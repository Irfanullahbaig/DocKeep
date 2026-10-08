package com.dockeep.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PreparedOcrImage(
    val bitmap: Bitmap,
    val tooBlurry: Boolean,
    val tooDark: Boolean,
    val tooPoor: Boolean,
    val qualityMessage: String?
)

internal class ImagePreprocessor(private val context: Context) {

    suspend fun prepare(file: File?, uri: Uri?): PreparedOcrImage = withContext(Dispatchers.IO) {
        val decoded = decode(file, uri) ?: error("empty")
        val rotated = rotate(decoded.bitmap, decoded.rotation)
        if (rotated != decoded.bitmap && !decoded.bitmap.isRecycled) decoded.bitmap.recycle()
        val cropped = cropQuietBorders(rotated)
        if (cropped != rotated && !rotated.isRecycled) rotated.recycle()
        val scaled = scaleForOcr(cropped)
        if (scaled != cropped && !cropped.isRecycled) cropped.recycle()
        val stats = analyze(scaled)
        PreparedOcrImage(
            bitmap = scaled,
            tooBlurry = stats.blurry,
            tooDark = stats.dark,
            tooPoor = stats.unreadable,
            qualityMessage = when {
                stats.unreadable && stats.dark ->
                    "Image is unclear. Please take another photo with better lighting."
                stats.unreadable && stats.blurry ->
                    "Image is unclear. Please take another photo with better lighting."
                stats.dark -> "The photo looks dark. You can still scan, or take another with more light."
                stats.blurry -> "The photo looks blurry. You can still scan, or take a sharper picture."
                else -> null
            }
        )
    }

    fun writeTemp(bitmap: Bitmap, dir: File): File {
        val file = File(dir, "ocr_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file
    }

    private data class Decoded(val bitmap: Bitmap, val rotation: Int)

    private fun decode(file: File?, uri: Uri?): Decoded? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (file != null) {
            BitmapFactory.decodeFile(file.absolutePath, bounds)
        } else if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return null
        } else {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = sampleSize(max(bounds.outWidth, bounds.outHeight), 2000)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = if (file != null) {
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } else {
            context.contentResolver.openInputStream(uri!!)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } ?: return null
        val rotation = readRotation(file, uri)
        return Decoded(bitmap, rotation)
    }

    private fun readRotation(file: File?, uri: Uri?): Int {
        return runCatching {
            val exif = when {
                file != null -> ExifInterface(file)
                uri != null -> context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                else -> null
            } ?: return 0
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleForOcr(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= 1600) return enhanceContrast(bitmap)
        val scale = 1600f / longest
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        return enhanceContrast(scaled).also {
            if (it != scaled && scaled != bitmap && !scaled.isRecycled) scaled.recycle()
        }
    }

    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val matrix = ColorMatrix().apply { setSaturation(0.15f) }
        val contrast = ColorMatrix(
            floatArrayOf(
                1.25f, 0f, 0f, 0f, -16f,
                0f, 1.25f, 0f, 0f, -16f,
                0f, 0f, 1.25f, 0f, -16f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(contrast)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    private fun cropQuietBorders(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 40 || h < 40) return bitmap
        val step = max(1, min(w, h) / 120)
        fun luminance(x: Int, y: Int): Int {
            val c = bitmap.getPixel(x, y)
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            return (r * 30 + g * 59 + b * 11) / 100
        }
        val corner = listOf(
            luminance(step, step),
            luminance(w - step - 1, step),
            luminance(step, h - step - 1),
            luminance(w - step - 1, h - step - 1)
        ).average()
        fun isContent(x: Int, y: Int) = abs(luminance(x, y) - corner) > 28
        var left = 0
        var right = w - 1
        var top = 0
        var bottom = h - 1
        loop@ for (x in 0 until w step step) {
            for (y in 0 until h step step) {
                if (isContent(x, y)) {
                    left = x
                    break@loop
                }
            }
        }
        loop@ for (x in w - 1 downTo 0 step step) {
            for (y in 0 until h step step) {
                if (isContent(x, y)) {
                    right = x
                    break@loop
                }
            }
        }
        loop@ for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                if (isContent(x, y)) {
                    top = y
                    break@loop
                }
            }
        }
        loop@ for (y in h - 1 downTo 0 step step) {
            for (x in 0 until w step step) {
                if (isContent(x, y)) {
                    bottom = y
                    break@loop
                }
            }
        }
        val pad = min(w, h) / 40
        left = (left - pad).coerceAtLeast(0)
        top = (top - pad).coerceAtLeast(0)
        right = (right + pad).coerceAtMost(w - 1)
        bottom = (bottom + pad).coerceAtMost(h - 1)
        val cropW = right - left + 1
        val cropH = bottom - top + 1
        if (cropW < w * 0.55 || cropH < h * 0.55) return bitmap
        if (cropW >= w - 4 && cropH >= h - 4) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
    }

    private data class Stats(val dark: Boolean, val blurry: Boolean, val unreadable: Boolean)

    private fun analyze(bitmap: Bitmap): Stats {
        val stepX = max(1, bitmap.width / 64)
        val stepY = max(1, bitmap.height / 64)
        var sum = 0L
        var count = 0
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                val c = bitmap.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                sum += (r * 30 + g * 59 + b * 11) / 100
                count++
                y += stepY
            }
            x += stepX
        }
        val mean = if (count == 0) 0.0 else sum.toDouble() / count
        val blur = laplacianVariance(bitmap)
        val dark = mean < 48
        val blurry = blur < 55
        val unreadable = mean < 22 || blur < 12
        return Stats(dark = dark, blurry = blurry, unreadable = unreadable)
    }

    private fun laplacianVariance(bitmap: Bitmap): Double {
        val w = min(160, bitmap.width)
        val h = min(160, bitmap.height)
        val scaled = if (bitmap.width == w && bitmap.height == h) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        }
        val gray = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = scaled.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                gray[y * w + x] = (r * 30 + g * 59 + b * 11) / 100
            }
        }
        if (scaled != bitmap && !scaled.isRecycled) scaled.recycle()
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val lap =
                    gray[(y - 1) * w + x] +
                        gray[(y + 1) * w + x] +
                        gray[y * w + (x - 1)] +
                        gray[y * w + (x + 1)] -
                        4 * gray[y * w + x]
                val v = abs(lap).toDouble()
                sum += v
                sumSq += v * v
                n++
            }
        }
        if (n == 0) return 0.0
        val mean = sum / n
        return (sumSq / n) - mean * mean
    }

    private fun sampleSize(longest: Int, target: Int): Int {
        var sample = 1
        while (longest / sample > target * 2) sample *= 2
        return sample
    }
}
