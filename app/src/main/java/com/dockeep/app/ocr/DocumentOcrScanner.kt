package com.dockeep.app.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File

class DocumentOcrScanner(context: Context) {
    private val appContext = context.applicationContext
    private val preprocessor = ImagePreprocessor(appContext)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun scan(file: File?, uri: Uri?, categoryId: String): OcrScanResult = withContext(Dispatchers.IO) {
        val prepared = runCatching { preprocessor.prepare(file, uri) }.getOrElse {
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
            val visionText = recognizer.process(image).await()
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
        } catch (_: Exception) {
            OcrScanResult.Recoverable("Could not scan this document. You can enter the details manually.")
        } finally {
            if (!prepared.bitmap.isRecycled) prepared.bitmap.recycle()
        }
    }

    fun close() {
        runCatching { recognizer.close() }
    }
}
