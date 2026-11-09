package com.dockeep.app.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wraps ML Kit text recognition so that nothing about OCR can take the app down.
 *
 * The recognizer is created only when a scan is requested, never when the Add screen opens,
 * and every failure (missing ML Kit classes after shrinking, native library problems, low
 * memory, unreadable images) is reported as a recoverable message so the user can type the
 * details by hand.
 */
class DocumentOcrScanner(context: Context) {
    private val appContext = context.applicationContext
    private val preprocessor = ImagePreprocessor(appContext)
    private val recognizerLock = Any()

    @Volatile
    private var recognizer: TextRecognizer? = null

    @Volatile
    private var recognizerUnavailable = false

    suspend fun scan(file: File?, uri: Uri?, categoryId: String): OcrScanResult = withContext(Dispatchers.IO) {
        val client = recognizerOrNull()
            ?: return@withContext OcrScanResult.Recoverable(UNAVAILABLE_MESSAGE)
        val prepared = try {
            preprocessor.prepare(file, uri)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return@withContext OcrScanResult.Recoverable(
                "Could not read this image. You can fill the details manually."
            )
        }
        try {
            if (prepared.tooPoor) {
                return@withContext OcrScanResult.Recoverable(
                    prepared.qualityMessage
                        ?: "Image is unclear. Please take another photo with better lighting."
                )
            }
            val image = InputImage.fromBitmap(prepared.bitmap, 0)
            val visionText = client.process(image).await()
            val lines = visionText.textBlocks
                .flatMap { block -> block.lines }
                .map { it.text.trim() }
                .filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                return@withContext OcrScanResult.Recoverable(
                    prepared.qualityMessage
                        ?: "No text was found. You can fill the details manually."
                )
            }
            val fields = DocumentFieldExtractor.extract(lines, visionText.text, categoryId)
            val usable = fields.filter { it.usable }
            if (usable.isEmpty()) {
                return@withContext OcrScanResult.Recoverable(
                    "No structured details were found. You can fill the form manually."
                )
            }
            OcrScanResult.Success(
                StructuredOcrResult(
                    fields = usable,
                    qualityWarning = prepared.qualityMessage
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            OcrScanResult.Recoverable("Could not scan this document. You can enter the details manually.")
        } finally {
            if (!prepared.bitmap.isRecycled) prepared.bitmap.recycle()
        }
    }

    fun close() {
        synchronized(recognizerLock) {
            runCatching { recognizer?.close() }
            recognizer = null
        }
    }

    private fun recognizerOrNull(): TextRecognizer? {
        recognizer?.let { return it }
        if (recognizerUnavailable) return null
        synchronized(recognizerLock) {
            recognizer?.let { return it }
            if (recognizerUnavailable) return null
            return try {
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer = it }
            } catch (_: Throwable) {
                // ML Kit can throw NPE / IllegalState / LinkageError when its components were
                // stripped, its init provider didn't run, or the native library failed to load.
                recognizerUnavailable = true
                null
            }
        }
    }

    private companion object {
        const val UNAVAILABLE_MESSAGE =
            "Text recognition isn’t available on this phone right now. You can enter the details manually."
    }
}
