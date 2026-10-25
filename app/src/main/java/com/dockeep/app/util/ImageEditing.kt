package com.dockeep.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ImageEditing {
    // Big enough to keep small print readable, small enough not to run low-end phones out of
    // memory (a 2400px ARGB bitmap is ~17 MB, and rotating briefly needs two of them).
    private const val MaxSide = 2400

    /** Returns null instead of throwing when the image can't be read or the phone is low on memory. */
    fun decode(context: Context, uri: Uri): Bitmap? = safely {
        val resolver = context.contentResolver
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees() }
        }.getOrNull() ?: 0
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return@safely null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds) }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@safely null
        uprighted(bitmap, orientation)
    }

    fun decode(file: File): Bitmap? = safely {
        if (!file.exists() || file.length() == 0L) return@safely null
        val orientation = runCatching { ExifInterface(file.absolutePath).rotationDegrees() }.getOrDefault(0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds) }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return@safely null
        uprighted(bitmap, orientation)
    }

    /** Rotates, or keeps the original if there isn't enough memory for the rotated copy. */
    fun rotateOrSame(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        return try {
            rotate(bitmap, degrees)
        } catch (_: OutOfMemoryError) {
            bitmap
        } catch (_: IllegalArgumentException) {
            bitmap
        }
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun crop(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap {
        val l = left.coerceIn(0, bitmap.width - 1)
        val t = top.coerceIn(0, bitmap.height - 1)
        val r = right.coerceIn(l + 1, bitmap.width)
        val b = bottom.coerceIn(t + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
    }

    fun autoTrimRect(bitmap: Bitmap): android.graphics.Rect {
        val width = bitmap.width
        val height = bitmap.height
        val corners = listOf(
            bitmap.getPixel(2.coerceAtMost(width - 1), 2.coerceAtMost(height - 1)),
            bitmap.getPixel((width - 3).coerceAtLeast(0), 2.coerceAtMost(height - 1)),
            bitmap.getPixel(2.coerceAtMost(width - 1), (height - 3).coerceAtLeast(0)),
            bitmap.getPixel((width - 3).coerceAtLeast(0), (height - 3).coerceAtLeast(0))
        )
        val row = IntArray(width)
        fun isEmpty(color: Int): Boolean {
            if (isNearWhiteOrBlack(color)) return true
            return corners.any { colorDistance(color, it) < 32 }
        }
        fun rowEmpty(y: Int): Boolean {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            return row.count(::isEmpty) >= (width * 0.9f).toInt()
        }
        fun colEmpty(x: Int): Boolean {
            var empty = 0
            val step = max(1, height / 180)
            var samples = 0
            var y = 0
            while (y < height) {
                if (isEmpty(bitmap.getPixel(x, y))) empty++
                samples++
                y += step
            }
            return empty >= samples * 0.9f
        }

        var top = 0
        while (top < height - 2 && rowEmpty(top)) top++
        var bottom = height - 1
        while (bottom > top + 1 && rowEmpty(bottom)) bottom--
        var left = 0
        while (left < width - 2 && colEmpty(left)) left++
        var right = width - 1
        while (right > left + 1 && colEmpty(right)) right--

        val padX = ((right - left) * 0.012f).toInt().coerceAtLeast(2)
        val padY = ((bottom - top) * 0.012f).toInt().coerceAtLeast(2)
        return android.graphics.Rect(
            (left - padX).coerceAtLeast(0),
            (top - padY).coerceAtLeast(0),
            (right + padX).coerceAtMost(width - 1),
            (bottom + padY).coerceAtMost(height - 1)
        )
    }

    fun saveJpeg(bitmap: Bitmap, dest: File, quality: Int = 92) {
        dest.parentFile?.mkdirs()
        dest.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
    }

    private fun uprighted(bitmap: Bitmap, orientation: Int): Bitmap {
        val rotated = rotateOrSame(bitmap, orientation)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun sampleFor(bounds: BitmapFactory.Options): Int {
        val longest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        return generateSequence(1) { it * 2 }.first { longest / it <= MaxSide }
    }

    private inline fun safely(block: () -> Bitmap?): Bitmap? = try {
        block()
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    private fun ExifInterface.rotationDegrees(): Int = when (
        getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private fun isNearWhiteOrBlack(color: Int): Boolean {
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        val brightness = (r + g + b) / 3
        val spread = max(r, max(g, b)) - min(r, min(g, b))
        return (brightness >= 238 && spread < 20) || (brightness <= 18 && spread < 20)
    }

    private fun colorDistance(a: Int, b: Int): Int {
        val dr = android.graphics.Color.red(a) - android.graphics.Color.red(b)
        val dg = android.graphics.Color.green(a) - android.graphics.Color.green(b)
        val db = android.graphics.Color.blue(a) - android.graphics.Color.blue(b)
        return abs(dr) + abs(dg) + abs(db)
    }
}

enum class CropAspect(
    val label: String,
    val ratio: Float?
) {
    FREE("Free", null),
    ID_CARD("ID card", 85.6f / 54f),
    PASSPORT("Passport", 35f / 45f),
    A4("A4", 210f / 297f),
    SQUARE("Square", 1f),
    PHOTO_4_3("4:3", 4f / 3f),
    PHOTO_3_2("3:2", 3f / 2f)
}
