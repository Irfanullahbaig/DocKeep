package com.dockeep.app.ui.edit

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Rotate90DegreesCcw
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dockeep.app.util.CropAspect
import com.dockeep.app.util.ImageEditing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.layout.width

sealed class ImageEditSource {
    data class FromFile(val file: File) : ImageEditSource()
    data class FromUri(val uri: Uri) : ImageEditSource()
}

private enum class CropHandle { None, Move, TL, TR, BL, BR, L, T, R, B }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageAdjustScreen(
    source: ImageEditSource,
    createOutputFile: () -> File,
    onCancel: () -> Unit,
    onConfirm: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(CropAspect.FREE) }
    var crop by remember { mutableStateOf(Rect.Zero) }
    var imageRect by remember { mutableStateOf(Rect.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var handle by remember { mutableStateOf(CropHandle.None) }
    val handleHit = with(LocalDensity.current) { 28.dp.toPx() }
    val minCrop = with(LocalDensity.current) { 56.dp.toPx() }

    BackHandler(onBack = onCancel)

    LaunchedEffect(source) {
        loading = true
        bitmap = withContext(Dispatchers.IO) {
            when (source) {
                is ImageEditSource.FromFile -> ImageEditing.decode(source.file)
                is ImageEditSource.FromUri -> ImageEditing.decode(context, source.uri)
            }
        }
        loading = false
    }

    fun fittedImageRect(size: IntSize, image: Bitmap): Rect {
        if (size.width == 0 || size.height == 0) return Rect.Zero
        val scale = min(size.width / image.width.toFloat(), size.height / image.height.toFloat())
        val drawnW = image.width * scale
        val drawnH = image.height * scale
        val left = (size.width - drawnW) / 2f
        val top = (size.height - drawnH) / 2f
        return Rect(left, top, left + drawnW, top + drawnH)
    }

    fun resetCrop(image: Bitmap, size: IntSize, locked: CropAspect) {
        val bounds = fittedImageRect(size, image)
        imageRect = bounds
        crop = aspectRect(bounds.deflate(8f), locked.ratio)
    }

    LaunchedEffect(bitmap, canvasSize) {
        val image = bitmap ?: return@LaunchedEffect
        if (canvasSize.width == 0) return@LaunchedEffect
        resetCrop(image, canvasSize, aspect)
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Crop & adjust") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        enabled = bitmap != null && !saving,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        onClick = {
                            val image = bitmap ?: return@TextButton
                            saving = true
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    val mapped = mapCropToBitmap(crop, imageRect, image.width, image.height)
                                    val cropped = ImageEditing.crop(
                                        image,
                                        mapped.left,
                                        mapped.top,
                                        mapped.right,
                                        mapped.bottom
                                    )
                                    val dest = createOutputFile()
                                    ImageEditing.saveJpeg(cropped, dest)
                                    dest
                                }
                                saving = false
                                onConfirm(file)
                            }
                        }
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Use photo")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { canvasSize = it }
            ) {
                val image = bitmap
                if (loading || image == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    val dst = imageRect
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(imageRect, aspect, minCrop) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        handle = hitHandle(offset, crop, handleHit)
                                    },
                                    onDragEnd = { handle = CropHandle.None },
                                    onDragCancel = { handle = CropHandle.None },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        crop = resizeCrop(crop, handle, drag, imageRect, minCrop, aspect.ratio)
                                    }
                                )
                            }
                    ) {
                        drawImage(
                            image = image.asImageBitmap(),
                            dstOffset = IntOffset(dst.left.toInt(), dst.top.toInt()),
                            dstSize = IntSize(dst.width.toInt().coerceAtLeast(1), dst.height.toInt().coerceAtLeast(1))
                        )
                        drawCropOverlay(crop)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Standard sizes", color = Color.White, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CropAspect.entries.forEach { option ->
                        FilterChip(
                            selected = aspect == option,
                            onClick = {
                                aspect = option
                                crop = aspectRect(imageRect, option.ratio)
                            },
                            label = { Text(option.label) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            bitmap = bitmap?.let { ImageEditing.rotate(it, -90) }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Rotate90DegreesCcw, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Left")
                    }
                    Button(
                        onClick = {
                            bitmap = bitmap?.let { ImageEditing.rotate(it, 90) }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Rotate90DegreesCw, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Right")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val image = bitmap ?: return@Button
                        val trimmed = ImageEditing.autoTrimRect(image)
                        crop = bitmapRectToView(trimmed, imageRect, image.width, image.height)
                        aspect = CropAspect.FREE
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Remove empty space")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Drag the corners to crop. Choose a size, rotate, or trim extra background.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun DrawScope.drawCropOverlay(crop: Rect) {
    val dim = Color.Black.copy(alpha = 0.55f)
    drawRect(dim, topLeft = Offset.Zero, size = Size(size.width, crop.top.coerceAtLeast(0f)))
    drawRect(dim, topLeft = Offset(0f, crop.bottom), size = Size(size.width, (size.height - crop.bottom).coerceAtLeast(0f)))
    drawRect(dim, topLeft = Offset(0f, crop.top), size = Size(crop.left.coerceAtLeast(0f), crop.height))
    drawRect(dim, topLeft = Offset(crop.right, crop.top), size = Size((size.width - crop.right).coerceAtLeast(0f), crop.height))
    drawRect(Color.White, topLeft = crop.topLeft, size = crop.size, style = Stroke(width = 3f))
    val thirdW = crop.width / 3f
    val thirdH = crop.height / 3f
    drawLine(Color.White.copy(0.35f), Offset(crop.left + thirdW, crop.top), Offset(crop.left + thirdW, crop.bottom), strokeWidth = 1.5f)
    drawLine(Color.White.copy(0.35f), Offset(crop.left + thirdW * 2, crop.top), Offset(crop.left + thirdW * 2, crop.bottom), strokeWidth = 1.5f)
    drawLine(Color.White.copy(0.35f), Offset(crop.left, crop.top + thirdH), Offset(crop.right, crop.top + thirdH), strokeWidth = 1.5f)
    drawLine(Color.White.copy(0.35f), Offset(crop.left, crop.top + thirdH * 2), Offset(crop.right, crop.top + thirdH * 2), strokeWidth = 1.5f)
    listOf(crop.topLeft, crop.topRight, crop.bottomLeft, crop.bottomRight).forEach { corner ->
        drawCircle(Color.White, radius = 14f, center = corner)
    }
}

private fun hitHandle(point: Offset, crop: Rect, hit: Float): CropHandle {
    fun near(target: Offset) = (point - target).getDistance() <= hit
    return when {
        near(crop.topLeft) -> CropHandle.TL
        near(crop.topRight) -> CropHandle.TR
        near(crop.bottomLeft) -> CropHandle.BL
        near(crop.bottomRight) -> CropHandle.BR
        abs(point.x - crop.left) <= hit && point.y in crop.top..crop.bottom -> CropHandle.L
        abs(point.x - crop.right) <= hit && point.y in crop.top..crop.bottom -> CropHandle.R
        abs(point.y - crop.top) <= hit && point.x in crop.left..crop.right -> CropHandle.T
        abs(point.y - crop.bottom) <= hit && point.x in crop.left..crop.right -> CropHandle.B
        crop.contains(point) -> CropHandle.Move
        else -> CropHandle.None
    }
}

private fun resizeCrop(
    current: Rect,
    handle: CropHandle,
    drag: Offset,
    bounds: Rect,
    minSize: Float,
    ratio: Float?
): Rect {
    var left = current.left
    var top = current.top
    var right = current.right
    var bottom = current.bottom
    when (handle) {
        CropHandle.None -> return current
        CropHandle.Move -> {
            val width = current.width
            val height = current.height
            left = (current.left + drag.x).coerceIn(bounds.left, bounds.right - width)
            top = (current.top + drag.y).coerceIn(bounds.top, bounds.bottom - height)
            return Rect(left, top, left + width, top + height)
        }
        CropHandle.L -> left += drag.x
        CropHandle.R -> right += drag.x
        CropHandle.T -> top += drag.y
        CropHandle.B -> bottom += drag.y
        CropHandle.TL -> { left += drag.x; top += drag.y }
        CropHandle.TR -> { right += drag.x; top += drag.y }
        CropHandle.BL -> { left += drag.x; bottom += drag.y }
        CropHandle.BR -> { right += drag.x; bottom += drag.y }
    }
    if (right - left < minSize) {
        if (handle == CropHandle.L || handle == CropHandle.TL || handle == CropHandle.BL) left = right - minSize
        else right = left + minSize
    }
    if (bottom - top < minSize) {
        if (handle == CropHandle.T || handle == CropHandle.TL || handle == CropHandle.TR) top = bottom - minSize
        else bottom = top + minSize
    }
        var rect = Rect(left, top, right, bottom).clampTo(bounds)
        if (ratio != null && rect.height > 0) {
            rect = aspectRect(rect, ratio).clampTo(bounds)
            if (rect.width < minSize || rect.height < minSize) return current
        }
        return rect
    }

private fun aspectRect(bounds: Rect, ratio: Float?): Rect {
    if (ratio == null || bounds.width <= 0f || bounds.height <= 0f) return bounds
    val boundsRatio = bounds.width / bounds.height
    val width: Float
    val height: Float
    if (boundsRatio > ratio) {
        height = bounds.height
        width = height * ratio
    } else {
        width = bounds.width
        height = width / ratio
    }
    val left = bounds.left + (bounds.width - width) / 2f
    val top = bounds.top + (bounds.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun Rect.clampTo(other: Rect): Rect = Rect(
    left = max(left, other.left),
    top = max(top, other.top),
    right = min(right, other.right),
    bottom = min(bottom, other.bottom)
)

private fun Rect.deflate(amount: Float): Rect = Rect(
    left + amount,
    top + amount,
    right - amount,
    bottom - amount
)

private fun mapCropToBitmap(crop: Rect, imageRect: Rect, bitmapWidth: Int, bitmapHeight: Int): android.graphics.Rect {
    val scaleX = bitmapWidth / imageRect.width.coerceAtLeast(1f)
    val scaleY = bitmapHeight / imageRect.height.coerceAtLeast(1f)
    return android.graphics.Rect(
        ((crop.left - imageRect.left) * scaleX).toInt().coerceIn(0, bitmapWidth - 1),
        ((crop.top - imageRect.top) * scaleY).toInt().coerceIn(0, bitmapHeight - 1),
        ((crop.right - imageRect.left) * scaleX).toInt().coerceIn(1, bitmapWidth),
        ((crop.bottom - imageRect.top) * scaleY).toInt().coerceIn(1, bitmapHeight)
    )
}

private fun bitmapRectToView(
    rect: android.graphics.Rect,
    imageRect: Rect,
    bitmapWidth: Int,
    bitmapHeight: Int
): Rect {
    val scaleX = imageRect.width / bitmapWidth.toFloat()
    val scaleY = imageRect.height / bitmapHeight.toFloat()
    return Rect(
        imageRect.left + rect.left * scaleX,
        imageRect.top + rect.top * scaleY,
        imageRect.left + rect.right * scaleX,
        imageRect.top + rect.bottom * scaleY
    )
}
