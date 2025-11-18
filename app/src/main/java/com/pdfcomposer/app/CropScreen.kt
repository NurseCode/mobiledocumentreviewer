package com.pdfcomposer.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlin.math.sqrt

// Custom Saver for Rect to survive configuration changes (rotation)
val RectSaver = Saver<Rect?, List<Float>>(
    save = { rect ->
        rect?.let { listOf(it.left, it.top, it.right, it.bottom) }
    },
    restore = { list ->
        if (list.size == 4) Rect(list[0], list[1], list[2], list[3]) else null
    }
)

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
    var cropRect by rememberSaveable(stateSaver = RectSaver) { mutableStateOf<Rect?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    
    LaunchedEffect(imageFile) {
        val loadedBitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(imageFile.absolutePath)
        }
        
        // Update state on main thread
        imageBitmap = loadedBitmap?.asImageBitmap()
        
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
                    // Auto-detect button
                    IconButton(
                        onClick = {
                            scope.launch {
                                val detectedRect = withContext(Dispatchers.IO) {
                                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                                    autoDetectEdges(bitmap)
                                }
                                // Update state on main thread
                                if (detectedRect != null) {
                                    cropRect = detectedRect
                                }
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.AutoFixHigh, "Auto-detect edges")
                    }
                    
                    // Apply crop button
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

// Handle types for crop tool
enum class CropHandle {
    NONE,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,  // Corner handles
    TOP, BOTTOM, LEFT, RIGHT  // Edge handles
}

@Composable
fun CropView(
    image: ImageBitmap,
    cropRect: Rect,
    onCropRectChange: (Rect) -> Unit
) {
    var currentRect by remember { mutableStateOf(cropRect) }
    var activeHandle by remember { mutableStateOf(CropHandle.NONE) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    LaunchedEffect(cropRect) {
        currentRect = cropRect
    }
    
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val maxWidth = constraints.maxWidth.toFloat()
        val maxHeight = constraints.maxHeight.toFloat()
        
        // Calculate initial scale to fit image in view
        val scaleX = maxWidth / image.width
        val scaleY = maxHeight / image.height
        val initialScale = min(scaleX, scaleY)
        
        // Use remembered scale if user zoomed, otherwise use initial
        if (scale == 1f) {
            scale = initialScale
        }
        
        val displayWidth = image.width * scale
        val displayHeight = image.height * scale
        val offsetX = (maxWidth - displayWidth) / 2 + offset.x
        val offsetY = (maxHeight - displayHeight) / 2 + offset.y
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Handle drag gestures for crop handles
                    detectDragGestures(
                        onDragStart = { position ->
                            // Convert screen coordinates to image coordinates
                            val imgX = (position.x - offsetX) / scale
                            val imgY = (position.y - offsetY) / scale
                            
                            // Determine which handle is being grabbed
                            activeHandle = detectHandle(currentRect, imgX, imgY)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            
                            if (activeHandle != CropHandle.NONE) {
                                // Apply drag to the active handle
                                val dragX = dragAmount.x / scale
                                val dragY = dragAmount.y / scale
                                
                                val newRect = adjustCropRectWithHandle(
                                    currentRect, 
                                    activeHandle, 
                                    dragX, 
                                    dragY,
                                    image.width.toFloat(), 
                                    image.height.toFloat()
                                )
                                currentRect = newRect
                                onCropRectChange(newRect)
                            }
                        },
                        onDragEnd = {
                            activeHandle = CropHandle.NONE
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
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
            
            // Draw corner handles (larger circles)
            val cornerSize = 44f
            val edgeSize = 36f
            
            // Corner handles
            drawCircle(
                color = Color.White,
                radius = cornerSize / 2,
                center = Offset(cropLeft, cropTop)
            )
            drawCircle(
                color = Color.White,
                radius = cornerSize / 2,
                center = Offset(cropRight, cropTop)
            )
            drawCircle(
                color = Color.White,
                radius = cornerSize / 2,
                center = Offset(cropLeft, cropBottom)
            )
            drawCircle(
                color = Color.White,
                radius = cornerSize / 2,
                center = Offset(cropRight, cropBottom)
            )
            
            // Edge handles (middle of each side)
            val centerX = (cropLeft + cropRight) / 2
            val centerY = (cropTop + cropBottom) / 2
            
            drawCircle(
                color = Color(0xFF64B5F6),  // Light blue for edge handles
                radius = edgeSize / 2,
                center = Offset(centerX, cropTop)
            )
            drawCircle(
                color = Color(0xFF64B5F6),
                radius = edgeSize / 2,
                center = Offset(centerX, cropBottom)
            )
            drawCircle(
                color = Color(0xFF64B5F6),
                radius = edgeSize / 2,
                center = Offset(cropLeft, centerY)
            )
            drawCircle(
                color = Color(0xFF64B5F6),
                radius = edgeSize / 2,
                center = Offset(cropRight, centerY)
            )
            }
        }
    }
}

/**
 * Detect which handle (corner or edge) the user is grabbing
 */
fun detectHandle(rect: Rect, touchX: Float, touchY: Float): CropHandle {
    val cornerThreshold = 100f  // Very large hit area for corners (easier to grab on phone)
    val edgeThreshold = 80f     // Large hit area for edge handles
    
    // Calculate center points for edge handles
    val centerX = (rect.left + rect.right) / 2
    val centerY = (rect.top + rect.bottom) / 2
    
    // Check corners first (higher priority)
    if (distance(touchX, touchY, rect.left, rect.top) < cornerThreshold) {
        return CropHandle.TOP_LEFT
    }
    if (distance(touchX, touchY, rect.right, rect.top) < cornerThreshold) {
        return CropHandle.TOP_RIGHT
    }
    if (distance(touchX, touchY, rect.left, rect.bottom) < cornerThreshold) {
        return CropHandle.BOTTOM_LEFT
    }
    if (distance(touchX, touchY, rect.right, rect.bottom) < cornerThreshold) {
        return CropHandle.BOTTOM_RIGHT
    }
    
    // Check edge handles
    if (distance(touchX, touchY, centerX, rect.top) < edgeThreshold) {
        return CropHandle.TOP
    }
    if (distance(touchX, touchY, centerX, rect.bottom) < edgeThreshold) {
        return CropHandle.BOTTOM
    }
    if (distance(touchX, touchY, rect.left, centerY) < edgeThreshold) {
        return CropHandle.LEFT
    }
    if (distance(touchX, touchY, rect.right, centerY) < edgeThreshold) {
        return CropHandle.RIGHT
    }
    
    return CropHandle.NONE
}

/**
 * Calculate distance between two points
 */
fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return sqrt(dx * dx + dy * dy)
}

/**
 * Adjust crop rectangle based on which handle is being dragged
 * This allows proper diagonal corner movement and independent edge movement
 */
fun adjustCropRectWithHandle(
    rect: Rect,
    handle: CropHandle,
    dragX: Float,
    dragY: Float,
    imageWidth: Float,
    imageHeight: Float
): Rect {
    val minSize = 100f  // Minimum crop size
    
    return when (handle) {
        // Corner handles - move both edges together (true diagonal movement)
        CropHandle.TOP_LEFT -> {
            val newLeft = (rect.left + dragX).coerceIn(0f, rect.right - minSize)
            val newTop = (rect.top + dragY).coerceIn(0f, rect.bottom - minSize)
            Rect(newLeft, newTop, rect.right, rect.bottom)
        }
        CropHandle.TOP_RIGHT -> {
            val newRight = (rect.right + dragX).coerceIn(rect.left + minSize, imageWidth)
            val newTop = (rect.top + dragY).coerceIn(0f, rect.bottom - minSize)
            Rect(rect.left, newTop, newRight, rect.bottom)
        }
        CropHandle.BOTTOM_LEFT -> {
            val newLeft = (rect.left + dragX).coerceIn(0f, rect.right - minSize)
            val newBottom = (rect.bottom + dragY).coerceIn(rect.top + minSize, imageHeight)
            Rect(newLeft, rect.top, rect.right, newBottom)
        }
        CropHandle.BOTTOM_RIGHT -> {
            val newRight = (rect.right + dragX).coerceIn(rect.left + minSize, imageWidth)
            val newBottom = (rect.bottom + dragY).coerceIn(rect.top + minSize, imageHeight)
            Rect(rect.left, rect.top, newRight, newBottom)
        }
        
        // Edge handles - move only one edge
        CropHandle.TOP -> {
            val newTop = (rect.top + dragY).coerceIn(0f, rect.bottom - minSize)
            Rect(rect.left, newTop, rect.right, rect.bottom)
        }
        CropHandle.BOTTOM -> {
            val newBottom = (rect.bottom + dragY).coerceIn(rect.top + minSize, imageHeight)
            Rect(rect.left, rect.top, rect.right, newBottom)
        }
        CropHandle.LEFT -> {
            val newLeft = (rect.left + dragX).coerceIn(0f, rect.right - minSize)
            Rect(newLeft, rect.top, rect.right, rect.bottom)
        }
        CropHandle.RIGHT -> {
            val newRight = (rect.right + dragX).coerceIn(rect.left + minSize, imageWidth)
            Rect(rect.left, rect.top, newRight, rect.bottom)
        }
        
        CropHandle.NONE -> rect  // No change
    }
}

/**
 * Auto-detect document edges using simple edge detection
 * Finds the content bounding box by analyzing brightness
 */
fun autoDetectEdges(bitmap: Bitmap?): Rect? {
    if (bitmap == null) return null
    
    return try {
        val width = bitmap.width
        val height = bitmap.height
        val threshold = 0.15f  // Brightness threshold for edge detection
        
        // Sample points to find content boundaries
        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0
        
        // Scan image in a grid pattern to find content
        val step = max(1, max(width, height) / 50)  // Sample every N pixels (minimum 1)
        
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = getBrightness(pixel)
                
                // Check if pixel is significantly different from edges (likely content)
                val edgeBrightness = getEdgeAverageBrightness(bitmap, x, y, width, height)
                
                if (abs(brightness - edgeBrightness) > threshold) {
                    // Found content - update bounds
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                }
            }
        }
        
        // Add some padding (5%) to avoid cutting off content
        val paddingX = (maxX - minX) * 0.05f
        val paddingY = (maxY - minY) * 0.05f
        
        val finalLeft = (minX - paddingX).coerceIn(0f, width.toFloat())
        val finalTop = (minY - paddingY).coerceIn(0f, height.toFloat())
        val finalRight = (maxX + paddingX).coerceIn(0f, width.toFloat())
        val finalBottom = (maxY + paddingY).coerceIn(0f, height.toFloat())
        
        // Make sure we found something reasonable (at least 20% of image)
        if (finalRight - finalLeft > width * 0.2f && finalBottom - finalTop > height * 0.2f) {
            Rect(finalLeft, finalTop, finalRight, finalBottom)
        } else {
            // Detection failed, return default margin
            val margin = 0.05f
            Rect(
                left = width * margin,
                top = height * margin,
                right = width * (1 - margin),
                bottom = height * (1 - margin)
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Calculate brightness of a pixel (0.0 = black, 1.0 = white)
 */
fun getBrightness(pixel: Int): Float {
    val r = android.graphics.Color.red(pixel) / 255f
    val g = android.graphics.Color.green(pixel) / 255f
    val b = android.graphics.Color.blue(pixel) / 255f
    return (r + g + b) / 3f
}

/**
 * Get average brightness of pixels near the edges for comparison
 */
fun getEdgeAverageBrightness(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Float {
    val samples = mutableListOf<Float>()
    
    // Sample from top edge
    if (y < height / 10) {
        samples.add(getBrightness(bitmap.getPixel(x, 0)))
    }
    
    // Sample from bottom edge
    if (y > height * 9 / 10) {
        samples.add(getBrightness(bitmap.getPixel(x, height - 1)))
    }
    
    // Sample from left edge
    if (x < width / 10) {
        samples.add(getBrightness(bitmap.getPixel(0, y)))
    }
    
    // Sample from right edge
    if (x > width * 9 / 10) {
        samples.add(getBrightness(bitmap.getPixel(width - 1, y)))
    }
    
    return if (samples.isNotEmpty()) {
        samples.average().toFloat()
    } else {
        0.5f  // Default middle brightness
    }
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
