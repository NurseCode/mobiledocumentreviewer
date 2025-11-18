package com.pdfcomposer.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    imageFile: File,
    onCropComplete: (File) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    
    LaunchedEffect(imageFile) {
        withContext(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            imageBitmap = bitmap?.asImageBitmap()
            
            // Initialize crop rect to cover most of the image (with 5% margin)
            imageBitmap?.let {
                val margin = 0.05f
                cropRect = Rect(
                    left = it.width * margin,
                    top = it.height * margin,
                    right = it.width * (1 - margin),
                    bottom = it.height * (1 - margin)
                )
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Crop Image")
                        Text(
                            "Drag corners to adjust",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val croppedFile = withContext(Dispatchers.IO) {
                                    cropImageFile(imageFile, cropRect)
                                }
                                isSaving = false
                                if (croppedFile != null) {
                                    onCropComplete(croppedFile)
                                }
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Check, "Apply")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            imageBitmap?.let { bitmap ->
                CropView(
                    image = bitmap,
                    cropRect = cropRect ?: Rect.Zero,
                    onCropRectChange = { cropRect = it }
                )
            }
            
            if (isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = 0.7f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun CropView(
    image: ImageBitmap,
    cropRect: Rect,
    onCropRectChange: (Rect) -> Unit
) {
    var currentRect by remember { mutableStateOf(cropRect) }
    
    LaunchedEffect(cropRect) {
        currentRect = cropRect
    }
    
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val maxWidth = constraints.maxWidth.toFloat()
        val maxHeight = constraints.maxHeight.toFloat()
        
        // Calculate scale to fit image in view
        val scaleX = maxWidth / image.width
        val scaleY = maxHeight / image.height
        val scale = min(scaleX, scaleY)
        
        val displayWidth = image.width * scale
        val displayHeight = image.height * scale
        val offsetX = (maxWidth - displayWidth) / 2
        val offsetY = (maxHeight - displayHeight) / 2
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        
                        // Convert screen coordinates to image coordinates
                        val x = (change.position.x - offsetX) / scale
                        val y = (change.position.y - offsetY) / scale
                        
                        // Determine which corner/edge is being dragged
                        val newRect = adjustCropRect(currentRect, x, y, dragAmount.x / scale, dragAmount.y / scale, image.width.toFloat(), image.height.toFloat())
                        currentRect = newRect
                        onCropRectChange(newRect)
                    }
                }
        ) {
            // Draw the image
            drawImage(
                image = image,
                dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                dstSize = IntSize(displayWidth.toInt(), displayHeight.toInt())
            )
            
            // Draw darkened overlay outside crop area
            val cropLeft = offsetX + currentRect.left * scale
            val cropTop = offsetY + currentRect.top * scale
            val cropRight = offsetX + currentRect.right * scale
            val cropBottom = offsetY + currentRect.bottom * scale
            
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, 0f),
                size = Size(maxWidth, cropTop)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, cropBottom),
                size = Size(maxWidth, maxHeight - cropBottom)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, cropTop),
                size = Size(cropLeft, cropBottom - cropTop)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(cropRight, cropTop),
                size = Size(maxWidth - cropRight, cropBottom - cropTop)
            )
            
            // Draw crop rectangle border
            drawRect(
                color = Color.White,
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropRight - cropLeft, cropBottom - cropTop),
                style = Stroke(width = 3f)
            )
            
            // Draw corner handles
            val handleSize = 40f
            drawCircle(
                color = Color.White,
                radius = handleSize / 2,
                center = Offset(cropLeft, cropTop)
            )
            drawCircle(
                color = Color.White,
                radius = handleSize / 2,
                center = Offset(cropRight, cropTop)
            )
            drawCircle(
                color = Color.White,
                radius = handleSize / 2,
                center = Offset(cropLeft, cropBottom)
            )
            drawCircle(
                color = Color.White,
                radius = handleSize / 2,
                center = Offset(cropRight, cropBottom)
            )
        }
    }
}

fun adjustCropRect(
    rect: Rect,
    touchX: Float,
    touchY: Float,
    dragX: Float,
    dragY: Float,
    imageWidth: Float,
    imageHeight: Float
): Rect {
    val threshold = 80f
    val minSize = 100f  // Minimum crop size
    
    var newLeft = rect.left
    var newTop = rect.top
    var newRight = rect.right
    var newBottom = rect.bottom
    
    // Check which corner/edge is closest
    val nearLeft = abs(touchX - rect.left) < threshold
    val nearRight = abs(touchX - rect.right) < threshold
    val nearTop = abs(touchY - rect.top) < threshold
    val nearBottom = abs(touchY - rect.bottom) < threshold
    
    // Apply drag with proper bounds and minimum size
    if (nearLeft) {
        newLeft = (rect.left + dragX).coerceIn(0f, (rect.right - minSize).coerceAtLeast(0f))
    }
    if (nearRight) {
        newRight = (rect.right + dragX).coerceIn((rect.left + minSize).coerceAtMost(imageWidth), imageWidth)
    }
    if (nearTop) {
        newTop = (rect.top + dragY).coerceIn(0f, (rect.bottom - minSize).coerceAtLeast(0f))
    }
    if (nearBottom) {
        newBottom = (rect.bottom + dragY).coerceIn((rect.top + minSize).coerceAtMost(imageHeight), imageHeight)
    }
    
    // Ensure rect stays within bounds and has minimum size
    val finalLeft = newLeft.coerceIn(0f, imageWidth - minSize)
    val finalTop = newTop.coerceIn(0f, imageHeight - minSize)
    val finalRight = newRight.coerceIn(minSize, imageWidth).coerceAtLeast(finalLeft + minSize)
    val finalBottom = newBottom.coerceIn(minSize, imageHeight).coerceAtLeast(finalTop + minSize)
    
    return Rect(finalLeft, finalTop, finalRight, finalBottom)
}

fun cropImageFile(originalFile: File, cropRect: Rect?): File? {
    if (cropRect == null) return originalFile
    
    return try {
        val originalBitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
        
        val left = cropRect.left.toInt().coerceIn(0, originalBitmap.width)
        val top = cropRect.top.toInt().coerceIn(0, originalBitmap.height)
        val width = (cropRect.width.toInt()).coerceIn(1, originalBitmap.width - left)
        val height = (cropRect.height.toInt()).coerceIn(1, originalBitmap.height - top)
        
        val croppedBitmap = Bitmap.createBitmap(originalBitmap, left, top, width, height)
        
        val croppedFile = File(originalFile.parent, "cropped_${originalFile.name}")
        FileOutputStream(croppedFile).use { out ->
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        
        originalBitmap.recycle()
        croppedBitmap.recycle()
        
        croppedFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
