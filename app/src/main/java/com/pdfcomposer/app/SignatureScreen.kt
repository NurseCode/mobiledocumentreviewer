package com.pdfcomposer.app

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument as AndroidPdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class SignatureMethod {
    DRAW, TYPE, IMAGE
}

enum class CursiveFont {
    ELEGANT, CLASSIC, FORMAL, CASUAL
}

data class DrawPoint(val x: Float, val y: Float, val isStart: Boolean)

@Composable
fun SignDocumentDialog(
    onDismiss: () -> Unit,
    onSignatureReady: (Bitmap) -> Unit
) {
    var selectedMethod by remember { mutableStateOf(SignatureMethod.DRAW) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Signature") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedMethod == SignatureMethod.DRAW,
                        onClick = { selectedMethod = SignatureMethod.DRAW },
                        label = { Text("Draw", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            if (selectedMethod == SignatureMethod.DRAW)
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMethod == SignatureMethod.TYPE,
                        onClick = { selectedMethod = SignatureMethod.TYPE },
                        label = { Text("Type", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            if (selectedMethod == SignatureMethod.TYPE)
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMethod == SignatureMethod.IMAGE,
                        onClick = { selectedMethod = SignatureMethod.IMAGE },
                        label = { Text("Upload", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            if (selectedMethod == SignatureMethod.IMAGE)
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedMethod) {
                    SignatureMethod.DRAW -> DrawSignaturePanel(onSignatureReady)
                    SignatureMethod.TYPE -> TypeSignaturePanel(onSignatureReady)
                    SignatureMethod.IMAGE -> UploadSignaturePanel(onSignatureReady)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DrawSignaturePanel(onSignatureReady: (Bitmap) -> Unit) {
    val points = remember { mutableStateListOf<DrawPoint>() }
    var canvasWidth by remember { mutableStateOf(0) }
    var canvasHeight by remember { mutableStateOf(0) }

    Column {
        Text(
            "Draw your signature below:",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(Color.White, RoundedCornerShape(8.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        canvasWidth = size.width
                        canvasHeight = size.height
                        detectDragGestures(
                            onDragStart = { offset ->
                                points.add(DrawPoint(offset.x, offset.y, true))
                            },
                            onDrag = { change, _ ->
                                points.add(
                                    DrawPoint(
                                        change.position.x,
                                        change.position.y,
                                        false
                                    )
                                )
                            }
                        )
                    }
            ) {
                canvasWidth = size.width.toInt()
                canvasHeight = size.height.toInt()

                for (i in 1 until points.size) {
                    if (!points[i].isStart) {
                        drawLine(
                            color = Color.Black,
                            start = Offset(points[i - 1].x, points[i - 1].y),
                            end = Offset(points[i].x, points[i].y),
                            strokeWidth = 4f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = { points.clear() }) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear")
            }
            Button(
                onClick = {
                    if (points.isNotEmpty() && canvasWidth > 0 && canvasHeight > 0) {
                        val bitmap = createBitmapFromDrawPoints(points, canvasWidth, canvasHeight)
                        onSignatureReady(bitmap)
                    }
                },
                enabled = points.isNotEmpty()
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Use Signature")
            }
        }
    }
}

@Composable
fun TypeSignaturePanel(onSignatureReady: (Bitmap) -> Unit) {
    var typedName by remember { mutableStateOf("") }
    var selectedFont by remember { mutableStateOf(CursiveFont.ELEGANT) }

    Column {
        Text(
            "Type your name:",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = typedName,
            onValueChange = { typedName = it },
            placeholder = { Text("Your full name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Choose a style:",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CursiveFont.entries.forEach { font ->
                val displayName = typedName.ifEmpty { "Your Name" }
                val fontStyle = getFontStyleForCursive(font)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFont = font },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedFont == font)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    border = if (selectedFont == font)
                        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else
                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = displayName,
                            style = fontStyle,
                            color = Color(0xFF1A237E)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (typedName.isNotBlank()) {
                    val bitmap = createBitmapFromTypedSignature(typedName, selectedFont)
                    onSignatureReady(bitmap)
                }
            },
            modifier = Modifier.align(Alignment.End),
            enabled = typedName.isNotBlank()
        ) {
            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Use Signature")
        }
    }
}

@Composable
fun UploadSignaturePanel(onSignatureReady: (Bitmap) -> Unit) {
    val context = LocalContext.current
    var uploadedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    uploadedBitmap = bitmap
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column {
        Text(
            "Upload a photo of your signature:",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (uploadedBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .background(Color.White, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = uploadedBitmap!!
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    val scale = minOf(
                        size.width / bmp.width.toFloat(),
                        size.height / bmp.height.toFloat()
                    )
                    val scaledWidth = bmp.width * scale
                    val scaledHeight = bmp.height * scale
                    val left = (size.width - scaledWidth) / 2f
                    val top = (size.height - scaledHeight) / 2f
                    drawImage(
                        image = bmp.asImageBitmap(),
                        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(scaledWidth.toInt(), scaledHeight.toInt())
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Change")
                }
                Button(onClick = {
                    uploadedBitmap?.let { onSignatureReady(it) }
                }) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Use Signature")
                }
            }
        } else {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clickable { imagePicker.launch("image/*") },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tap to select an image")
                        Text(
                            "PNG or JPG with transparent/white background works best",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SignPdfScreen(
    pdfFile: File,
    signatureBitmap: Bitmap,
    onDismiss: () -> Unit,
    onSigned: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageCount by remember { mutableStateOf(1) }
    var currentPage by remember { mutableStateOf(0) }
    var sigX by remember { mutableStateOf(0.5f) }
    var sigY by remember { mutableStateOf(0.8f) }
    var sigScale by remember { mutableStateOf(0.25f) }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(pdfFile, currentPage) {
        withContext(Dispatchers.IO) {
            try {
                val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                pageCount = renderer.pageCount
                val page = renderer.openPage(currentPage)
                val scale = 2
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Place Signature on Page ${currentPage + 1}") },
        text = {
            Column {
                if (pdfBitmap != null) {
                    Text(
                        "Drag to position. Use slider to resize.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        val pdfBmp = pdfBitmap ?: return@detectDragGestures
                                        val pScale = minOf(
                                            size.width.toFloat() / pdfBmp.width.toFloat(),
                                            size.height.toFloat() / pdfBmp.height.toFloat()
                                        )
                                        val pW = pdfBmp.width * pScale
                                        val pH = pdfBmp.height * pScale
                                        val pLeft = (size.width - pW) / 2f
                                        val pTop = (size.height - pH) / 2f
                                        sigX = ((change.position.x - pLeft) / pW).coerceIn(0f, 1f)
                                        sigY = ((change.position.y - pTop) / pH).coerceIn(0f, 1f)
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

                            val sigW = pageW * sigScale
                            val sigH = sigW * (signatureBitmap.height.toFloat() / signatureBitmap.width.toFloat())
                            val sigLeft = pageLeft + (sigX * pageW) - (sigW / 2f)
                            val sigTop = pageTop + (sigY * pageH) - (sigH / 2f)

                            drawImage(
                                image = signatureBitmap.asImageBitmap(),
                                dstOffset = androidx.compose.ui.unit.IntOffset(sigLeft.toInt(), sigTop.toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(sigW.toInt(), sigH.toInt())
                            )

                            drawRect(
                                color = Color(0x400000FF),
                                topLeft = Offset(sigLeft, sigTop),
                                size = androidx.compose.ui.geometry.Size(sigW, sigH),
                                style = Stroke(width = 2f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Signature Size:", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = sigScale,
                        onValueChange = { sigScale = it },
                        valueRange = 0.1f..0.5f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (pageCount > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
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
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                if (isProcessing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "Embedding signature...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isProcessing = true
                    scope.launch {
                        val result = embedSignatureInPdf(
                            context, pdfFile, signatureBitmap,
                            currentPage, sigX, sigY, sigScale
                        )
                        isProcessing = false
                        if (result != null) {
                            onSigned(result)
                        } else {
                            Toast.makeText(context, "Failed to sign PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = pdfBitmap != null && !isProcessing
            ) {
                Text("Sign PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) {
                Text("Cancel")
            }
        }
    )
}

fun getFontStyleForCursive(font: CursiveFont): TextStyle {
    return when (font) {
        CursiveFont.ELEGANT -> TextStyle(
            fontFamily = FontFamily.Cursive,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            fontStyle = FontStyle.Italic
        )
        CursiveFont.CLASSIC -> TextStyle(
            fontFamily = FontFamily.Cursive,
            fontSize = 26.sp,
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Normal
        )
        CursiveFont.FORMAL -> TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic
        )
        CursiveFont.CASUAL -> TextStyle(
            fontFamily = FontFamily.Cursive,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Normal
        )
    }
}

fun createBitmapFromDrawPoints(points: List<DrawPoint>, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    val path = android.graphics.Path()
    for (i in points.indices) {
        if (points[i].isStart) {
            path.moveTo(points[i].x, points[i].y)
        } else {
            path.lineTo(points[i].x, points[i].y)
        }
    }
    canvas.drawPath(path, paint)

    return trimBitmap(bitmap)
}

fun createBitmapFromTypedSignature(name: String, font: CursiveFont): Bitmap {
    val paint = Paint().apply {
        color = android.graphics.Color.rgb(26, 35, 126)
        isAntiAlias = true
        textSize = 80f
        typeface = when (font) {
            CursiveFont.ELEGANT -> Typeface.create("cursive", Typeface.ITALIC)
            CursiveFont.CLASSIC -> Typeface.create("cursive", Typeface.NORMAL)
            CursiveFont.FORMAL -> Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            CursiveFont.CASUAL -> Typeface.create("cursive", Typeface.BOLD)
        }
    }

    val textBounds = android.graphics.Rect()
    paint.getTextBounds(name, 0, name.length, textBounds)

    val padding = 20
    val width = textBounds.width() + padding * 2
    val height = textBounds.height() + padding * 2

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    canvas.drawText(name, padding.toFloat(), height - padding.toFloat(), paint)

    return bitmap
}

fun trimBitmap(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    var top = 0
    var bottom = height - 1
    var left = 0
    var right = width - 1

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    outer@ for (y in 0 until height) {
        for (x in 0 until width) {
            if (android.graphics.Color.alpha(pixels[y * width + x]) > 10) {
                top = y
                break@outer
            }
        }
    }

    outer@ for (y in height - 1 downTo 0) {
        for (x in 0 until width) {
            if (android.graphics.Color.alpha(pixels[y * width + x]) > 10) {
                bottom = y
                break@outer
            }
        }
    }

    outer@ for (x in 0 until width) {
        for (y in 0 until height) {
            if (android.graphics.Color.alpha(pixels[y * width + x]) > 10) {
                left = x
                break@outer
            }
        }
    }

    outer@ for (x in width - 1 downTo 0) {
        for (y in 0 until height) {
            if (android.graphics.Color.alpha(pixels[y * width + x]) > 10) {
                right = x
                break@outer
            }
        }
    }

    if (right <= left || bottom <= top) return bitmap

    val padding = 10
    val trimLeft = maxOf(0, left - padding)
    val trimTop = maxOf(0, top - padding)
    val trimRight = minOf(width - 1, right + padding)
    val trimBottom = minOf(height - 1, bottom + padding)

    return Bitmap.createBitmap(bitmap, trimLeft, trimTop, trimRight - trimLeft + 1, trimBottom - trimTop + 1)
}

suspend fun embedSignatureInPdf(
    context: Context,
    pdfFile: File,
    signatureBitmap: Bitmap,
    pageIndex: Int,
    relativeX: Float,
    relativeY: Float,
    relativeScale: Float
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

            if (i == pageIndex) {
                val sigW = (pageWidth * relativeScale).toInt()
                val sigH = (sigW * (signatureBitmap.height.toFloat() / signatureBitmap.width.toFloat())).toInt()
                val sigLeft = ((relativeX * pageWidth) - sigW / 2f).toInt()
                val sigTop = ((relativeY * pageHeight) - sigH / 2f).toInt()

                val sigRect = android.graphics.Rect(sigLeft, sigTop, sigLeft + sigW, sigTop + sigH)
                canvas.drawBitmap(signatureBitmap, null, sigRect, Paint().apply { isAntiAlias = true })
            }

            newPdfDocument.finishPage(newPage)
        }

        renderer.close()
        fd.close()

        val outputFile = File(context.cacheDir, "signed_${System.currentTimeMillis()}.pdf")
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
