package com.pdfcomposer.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument as AndroidPdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class AnnotationTool {
    PEN, HIGHLIGHTER, ERASER
}

data class AnnotationStroke(
    val points: List<Offset>,
    val color: androidx.compose.ui.graphics.Color,
    val strokeWidth: Float,
    val alpha: Float,
    val tool: AnnotationTool
)

val annotationColors = listOf(
    androidx.compose.ui.graphics.Color.Black,
    androidx.compose.ui.graphics.Color.Red,
    androidx.compose.ui.graphics.Color.Blue,
    androidx.compose.ui.graphics.Color(0xFF2E7D32),
    androidx.compose.ui.graphics.Color(0xFFFF6F00),
    androidx.compose.ui.graphics.Color(0xFF6A1B9A),
    androidx.compose.ui.graphics.Color(0xFFAD1457)
)

val highlightColors = listOf(
    androidx.compose.ui.graphics.Color.Yellow,
    androidx.compose.ui.graphics.Color(0xFF80FF80),
    androidx.compose.ui.graphics.Color(0xFF80D4FF),
    androidx.compose.ui.graphics.Color(0xFFFF80AB),
    androidx.compose.ui.graphics.Color(0xFFFFCC80)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotatePdfDialog(
    pdfFile: File,
    onDismiss: () -> Unit,
    onSave: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageCount by remember { mutableStateOf(1) }
    var currentPage by remember { mutableStateOf(0) }
    var selectedTool by remember { mutableStateOf(AnnotationTool.PEN) }
    var selectedColor by remember { mutableStateOf(Color.Black) }
    var strokeWidth by remember { mutableStateOf(4f) }
    var isProcessing by remember { mutableStateOf(false) }
    var showToolOptions by remember { mutableStateOf(false) }

    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val allAnnotations = remember { mutableStateMapOf<Int, MutableList<AnnotationStroke>>() }
    val currentPoints = remember { mutableStateListOf<Offset>() }

    LaunchedEffect(pdfFile, currentPage) {
        withContext(Dispatchers.IO) {
            try {
                val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                pageCount = renderer.pageCount
                val page = renderer.openPage(currentPage)
                val scale = 3
                val bitmap = Bitmap.createBitmap(
                    page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                fd.close()
                pdfBitmap = bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Annotate & Draw") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !isProcessing) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                allAnnotations[currentPage]?.let {
                                    if (it.isNotEmpty()) it.removeAt(it.lastIndex)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Undo, "Undo")
                        }
                        IconButton(onClick = { allAnnotations[currentPage]?.clear() }) {
                            Icon(Icons.Default.DeleteSweep, "Clear")
                        }
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(2.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Button(
                                onClick = {
                                    isProcessing = true
                                    scope.launch {
                                        val result = embedAnnotationsInPdf(
                                            context, pdfFile, allAnnotations
                                        )
                                        isProcessing = false
                                        if (result != null) {
                                            onSave(result)
                                        } else {
                                            Toast.makeText(context, "Failed to save annotations", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = allAnnotations.isNotEmpty(),
                                modifier = Modifier.padding(end = 4.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Text("Save")
                            }
                        }
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedTool == AnnotationTool.PEN,
                        onClick = {
                            selectedTool = AnnotationTool.PEN
                            showToolOptions = true
                            if (highlightColors.contains(selectedColor)) {
                                selectedColor = Color.Black
                            }
                        },
                        label = { Text("Pen") },
                        leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = selectedTool == AnnotationTool.HIGHLIGHTER,
                        onClick = {
                            selectedTool = AnnotationTool.HIGHLIGHTER
                            showToolOptions = true
                            selectedColor = Color.Yellow
                            strokeWidth = 20f
                        },
                        label = { Text("Highlight") },
                        leadingIcon = { Icon(Icons.Default.Highlight, null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = selectedTool == AnnotationTool.ERASER,
                        onClick = {
                            selectedTool = AnnotationTool.ERASER
                            showToolOptions = false
                        },
                        label = { Text("Eraser") },
                        leadingIcon = { Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(16.dp)) }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    if (selectedTool == AnnotationTool.PEN || selectedTool == AnnotationTool.HIGHLIGHTER) {
                        IconButton(
                            onClick = { showToolOptions = !showToolOptions },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (showToolOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                "Toggle options",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (zoomScale > 1.05f) {
                        Text(
                            "${(zoomScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ZoomOut, "Reset zoom", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                if (showToolOptions && selectedTool != AnnotationTool.ERASER) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val colors = if (selectedTool == AnnotationTool.PEN) annotationColors else highlightColors
                        colors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .padding(2.dp)
                                    .background(color, CircleShape)
                                    .then(
                                        if (selectedColor == color)
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else
                                            Modifier.border(1.dp, Color.Gray, CircleShape)
                                    )
                                    .clickable { selectedColor = color }
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${strokeWidth.toInt()}", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = strokeWidth,
                            onValueChange = { strokeWidth = it },
                            valueRange = if (selectedTool == AnnotationTool.PEN) 2f..12f else 10f..40f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (pdfBitmap != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFFE0E0E0))
                            .clipToBounds()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.none { it.pressed }) break
                                        if (event.changes.size >= 2) {
                                            val zoom = event.calculateZoom()
                                            val pan = event.calculatePan()
                                            zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                            panOffset = Offset(
                                                (panOffset.x + pan.x).coerceIn(
                                                    -size.width * (zoomScale - 1) / 2f,
                                                    size.width * (zoomScale - 1) / 2f
                                                ),
                                                (panOffset.y + pan.y).coerceIn(
                                                    -size.height * (zoomScale - 1) / 2f,
                                                    size.height * (zoomScale - 1) / 2f
                                                )
                                            )
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                }
                                .pointerInput(selectedTool, selectedColor, strokeWidth, zoomScale, panOffset) {
                                    if (selectedTool == AnnotationTool.ERASER) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val pageAnnotations = allAnnotations[currentPage]
                                                if (pageAnnotations != null && pageAnnotations.isNotEmpty()) {
                                                    val pdfBmp = pdfBitmap ?: return@detectDragGestures
                                                    val pScale = minOf(
                                                        size.width.toFloat() / pdfBmp.width.toFloat(),
                                                        size.height.toFloat() / pdfBmp.height.toFloat()
                                                    )
                                                    val pW = pdfBmp.width * pScale
                                                    val pH = pdfBmp.height * pScale
                                                    val pLeft = (size.width - pW) / 2f
                                                    val pTop = (size.height - pH) / 2f
                                                    val relX = (offset.x - pLeft) / pW
                                                    val relY = (offset.y - pTop) / pH

                                                    val iterator = pageAnnotations.iterator()
                                                    while (iterator.hasNext()) {
                                                        val stroke = iterator.next()
                                                        val hit = stroke.points.any { pt ->
                                                            val dx = pt.x - relX
                                                            val dy = pt.y - relY
                                                            (dx * dx + dy * dy) < 0.001f
                                                        }
                                                        if (hit) iterator.remove()
                                                    }
                                                }
                                            },
                                            onDrag = { _, _ -> }
                                        )
                                    } else {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                currentPoints.clear()
                                                val pdfBmp = pdfBitmap ?: return@detectDragGestures
                                                val pScale = minOf(
                                                    size.width.toFloat() / pdfBmp.width.toFloat(),
                                                    size.height.toFloat() / pdfBmp.height.toFloat()
                                                )
                                                val pW = pdfBmp.width * pScale
                                                val pH = pdfBmp.height * pScale
                                                val pLeft = (size.width - pW) / 2f
                                                val pTop = (size.height - pH) / 2f
                                                val relX = ((offset.x - pLeft) / pW).coerceIn(0f, 1f)
                                                val relY = ((offset.y - pTop) / pH).coerceIn(0f, 1f)
                                                currentPoints.add(Offset(relX, relY))
                                            },
                                            onDrag = { change, _ ->
                                                val pdfBmp = pdfBitmap ?: return@detectDragGestures
                                                val pScale = minOf(
                                                    size.width.toFloat() / pdfBmp.width.toFloat(),
                                                    size.height.toFloat() / pdfBmp.height.toFloat()
                                                )
                                                val pW = pdfBmp.width * pScale
                                                val pH = pdfBmp.height * pScale
                                                val pLeft = (size.width - pW) / 2f
                                                val pTop = (size.height - pH) / 2f
                                                val relX = ((change.position.x - pLeft) / pW).coerceIn(0f, 1f)
                                                val relY = ((change.position.y - pTop) / pH).coerceIn(0f, 1f)
                                                currentPoints.add(Offset(relX, relY))
                                            },
                                            onDragEnd = {
                                                if (currentPoints.size > 1) {
                                                    val stroke = AnnotationStroke(
                                                        points = currentPoints.toList(),
                                                        color = selectedColor,
                                                        strokeWidth = strokeWidth,
                                                        alpha = if (selectedTool == AnnotationTool.HIGHLIGHTER) 0.4f else 1f,
                                                        tool = selectedTool
                                                    )
                                                    allAnnotations.getOrPut(currentPage) { mutableListOf() }.add(stroke)
                                                }
                                                currentPoints.clear()
                                            }
                                        )
                                    }
                                }
                        ) {
                            val pdfBmp = pdfBitmap!!
                            val pageScale = minOf(
                                size.width / pdfBmp.width.toFloat(),
                                size.height / pdfBmp.height.toFloat()
                            )
                            val pageW = pdfBmp.width * pageScale
                            val pageH = pdfBmp.height * pageScale
                            val pageLeft = (size.width - pageW) / 2f
                            val pageTop = (size.height - pageH) / 2f

                            drawImage(
                                image = pdfBmp.asImageBitmap(),
                                dstOffset = androidx.compose.ui.unit.IntOffset(pageLeft.toInt(), pageTop.toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(pageW.toInt(), pageH.toInt())
                            )

                            val pageAnnotations = allAnnotations[currentPage]
                            if (pageAnnotations != null) {
                                for (stroke in pageAnnotations) {
                                    drawAnnotationStroke(stroke, pageLeft, pageTop, pageW, pageH)
                                }
                            }

                            if (currentPoints.size > 1) {
                                val tempStroke = AnnotationStroke(
                                    points = currentPoints.toList(),
                                    color = selectedColor,
                                    strokeWidth = strokeWidth,
                                    alpha = if (selectedTool == AnnotationTool.HIGHLIGHTER) 0.4f else 1f,
                                    tool = selectedTool
                                )
                                drawAnnotationStroke(tempStroke, pageLeft, pageTop, pageW, pageH)
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                if (pageCount > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0
                        ) {
                            Icon(Icons.Default.ArrowBack, "Previous page")
                        }
                        Text("Page ${currentPage + 1} of $pageCount")
                        IconButton(
                            onClick = { if (currentPage < pageCount - 1) currentPage++ },
                            enabled = currentPage < pageCount - 1
                        ) {
                            Icon(Icons.Default.ArrowForward, "Next page")
                        }
                    }
                }
            }
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnotationStroke(
    stroke: AnnotationStroke,
    pageLeft: Float,
    pageTop: Float,
    pageW: Float,
    pageH: Float
) {
    for (i in 1 until stroke.points.size) {
        val startX = pageLeft + stroke.points[i - 1].x * pageW
        val startY = pageTop + stroke.points[i - 1].y * pageH
        val endX = pageLeft + stroke.points[i].x * pageW
        val endY = pageTop + stroke.points[i].y * pageH

        drawLine(
            color = stroke.color.copy(alpha = stroke.alpha),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = stroke.strokeWidth,
            cap = if (stroke.tool == AnnotationTool.HIGHLIGHTER) StrokeCap.Butt else StrokeCap.Round
        )
    }
}

suspend fun embedAnnotationsInPdf(
    context: Context,
    pdfFile: File,
    annotations: Map<Int, List<AnnotationStroke>>
): File? = withContext(Dispatchers.IO) {
    try {
        val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        val pageCount = renderer.pageCount

        val newPdfDocument = AndroidPdfDocument()

        for (i in 0 until pageCount) {
            val page = renderer.openPage(i)
            val pageWidth = page.width
            val pageHeight = page.height

            val scaleFactor = 3
            val bitmapWidth = pageWidth * scaleFactor
            val bitmapHeight = pageHeight * scaleFactor

            val pageBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            pageBitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            val newPage = newPdfDocument.startPage(
                AndroidPdfDocument.PageInfo.Builder(pageWidth, pageHeight, i).create()
            )
            val canvas = newPage.canvas

            val srcRect = android.graphics.Rect(0, 0, bitmapWidth, bitmapHeight)
            val dstRect = android.graphics.Rect(0, 0, pageWidth, pageHeight)
            canvas.drawBitmap(pageBitmap, srcRect, dstRect, null)
            pageBitmap.recycle()

            val pageAnnotations = annotations[i]
            if (pageAnnotations != null) {
                for (stroke in pageAnnotations) {
                    val paint = android.graphics.Paint().apply {
                        color = androidColorFromComposeColor(stroke.color)
                        alpha = (stroke.alpha * 255).toInt()
                        strokeWidth = stroke.strokeWidth * (pageWidth.toFloat() / 400f)
                        style = android.graphics.Paint.Style.STROKE
                        strokeCap = if (stroke.tool == AnnotationTool.HIGHLIGHTER)
                            android.graphics.Paint.Cap.BUTT
                        else
                            android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                        isAntiAlias = true
                        if (stroke.tool == AnnotationTool.HIGHLIGHTER) {
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
                        }
                    }

                    val path = android.graphics.Path()
                    for (j in stroke.points.indices) {
                        val x = stroke.points[j].x * pageWidth
                        val y = stroke.points[j].y * pageHeight
                        if (j == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    canvas.drawPath(path, paint)
                }
            }

            newPdfDocument.finishPage(newPage)
        }

        renderer.close()
        fd.close()

        val outputFile = File(context.cacheDir, "annotated_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { fos ->
            newPdfDocument.writeTo(fos)
        }
        newPdfDocument.close()

        outputFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun androidColorFromComposeColor(color: androidx.compose.ui.graphics.Color): Int {
    return android.graphics.Color.argb(
        (color.alpha * 255).toInt(),
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt()
    )
}
