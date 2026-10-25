package com.dockeep.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dockeep.app.domain.DocumentItem
import com.dockeep.app.domain.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.max

private const val MinZoom = 1f
private const val MaxZoom = 6f

@Composable
fun FullScreenDocumentViewer(
    document: DocumentItem,
    file: File,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    var pageCount by remember(file.path) { mutableIntStateOf(if (document.fileType == FileType.PDF) 1 else 1) }
    val pagerState = rememberPagerState(pageCount = { pageCount })

    LaunchedEffect(file.path, document.fileType) {
        if (document.fileType == FileType.PDF) {
            pageCount = withContext(Dispatchers.IO) { pdfPageCount(file) }.coerceAtLeast(1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (document.fileType == FileType.PDF) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { page ->
                ZoomablePdfPage(file = file, pageIndex = page, documentName = document.name)
            }
        } else {
            ZoomableImage(file = file, contentDescription = document.name)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f)) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(document.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    text = if (document.fileType == FileType.PDF) {
                        "Pinch to zoom · Page ${pagerState.currentPage + 1} of $pageCount"
                    } else {
                        "Pinch to zoom · Double-tap to zoom"
                    },
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(file: File, contentDescription: String) {
    ZoomableContent {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ZoomablePdfPage(file: File, pageIndex: Int, documentName: String) {
    var bitmap by remember(file.path, pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.path, pageIndex) {
        bitmap = withContext(Dispatchers.IO) { renderPdfPage(file, pageIndex, scale = 2.5f) }
    }
    ZoomableContent {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "$documentName page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ZoomableContent(content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(MinZoom) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(MinZoom, MaxZoom)
        scale = newScale
        offset = if (newScale <= MinZoom) Offset.Zero else offset + panChange
    }

    fun setZoom(target: Float) {
        val next = target.coerceIn(MinZoom, MaxZoom)
        scale = next
        if (next <= MinZoom) offset = Offset.Zero
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val maxX = (constraints.maxWidth * (scale - 1f) / 2f)
        val maxY = (constraints.maxHeight * (scale - 1f) / 2f)
        val clampedOffset = Offset(
            x = offset.x.coerceIn(-max(maxX, 0f), max(maxX, 0f)),
            y = offset.y.coerceIn(-max(maxY, 0f), max(maxY, 0f))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.15f) {
                                scale = MinZoom
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
                .transformable(transformState)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = clampedOffset.x
                    translationY = clampedOffset.y
                },
            contentAlignment = Alignment.Center
        ) {
            content()
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZoomButton(Icons.Rounded.Remove, "Zoom out") {
                scope.launch { setZoom(scale - 0.5f) }
            }
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f)) {
                Text(
                    text = "${(scale * 100).toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            ZoomButton(Icons.Rounded.Add, "Zoom in") {
                scope.launch { setZoom(scale + 0.5f) }
            }
        }
    }
}

@Composable
private fun ZoomButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f)) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
    }
}

private fun pdfPageCount(file: File): Int = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { it.pageCount }
    }
}.getOrDefault(1)

private fun renderPdfPage(file: File, pageIndex: Int, scale: Float): Bitmap? = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            val index = pageIndex.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(index).use { page ->
                // Cap the longest edge so oversized pages (posters, plans) don't exhaust memory.
                val fitted = min(scale, MaxPdfRenderEdge / max(page.width, page.height).coerceAtLeast(1).toFloat())
                val width = (page.width * fitted).toInt().coerceAtLeast(1)
                val height = (page.height * fitted).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }
}.getOrNull()

private const val MaxPdfRenderEdge = 3000f
