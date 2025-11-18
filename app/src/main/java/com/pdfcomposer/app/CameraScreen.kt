package com.pdfcomposer.app

import android.graphics.BitmapFactory
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onImageCaptured: (File) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var showCrop by remember { mutableStateOf(false) }
    var capturedImageFile by remember { mutableStateOf<File?>(null) }
    var finalImageFile by remember { mutableStateOf<File?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    
    LaunchedEffect(previewView) {
        if (previewView == null) return@LaunchedEffect
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()
        
        val preview = Preview.Builder().build()
        
        // Configure ImageCapture with rotation from PreviewView display
        val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
        val imageCaptureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetRotation(rotation)
        imageCapture = imageCaptureBuilder.build()
        
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            
            preview.setSurfaceProvider(previewView.surfaceProvider)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    if (showCrop && capturedImageFile != null) {
        CropScreen(
            imageFile = capturedImageFile!!,
            onCropComplete = { croppedFile ->
                // Return to preview with cropped image
                finalImageFile = croppedFile
                showCrop = false
                showPreview = true
            },
            onCancel = {
                showCrop = false
                showPreview = true
            }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!showPreview) {
                // Camera preview
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also {
                        previewView = it
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Camera controls overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Position document within frame",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White, shape = MaterialTheme.shapes.medium)
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = Color.Black)
                    }
                    
                    // Capture button
                    Button(
                        onClick = {
                            if (!isCapturing && imageCapture != null) {
                                isCapturing = true
                                scope.launch {
                                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    val photoFile = File(
                                        context.cacheDir,
                                        "IMG_$timestamp.jpg"
                                    )
                                    
                                    val result = CameraUtils.captureImage(context, imageCapture!!, photoFile)
                                    result.onSuccess {
                                        capturedImageFile = it
                                        finalImageFile = it
                                        showPreview = true
                                    }.onFailure {
                                        // Handle error
                                        it.printStackTrace()
                                    }
                                    isCapturing = false
                                }
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        enabled = !isCapturing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.Default.CameraAlt,
                                "Capture",
                                modifier = Modifier.size(32.dp),
                                tint = Color.White
                            )
                        }
                    }
                    
                    // Flash toggle (placeholder)
                    IconButton(
                        onClick = { /* Toggle flash */ },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White, shape = MaterialTheme.shapes.medium)
                    ) {
                        Icon(Icons.Default.FlashOff, "Flash", tint = Color.Black)
                    }
                }
            }
        } else {
            // Image preview - show the final (possibly cropped) image
            finalImageFile?.let { file ->
                val bitmap = remember(file) {
                    BitmapFactory.decodeFile(file.absolutePath)
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // Preview image
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    // Preview controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Retake
                        OutlinedButton(
                            onClick = {
                                showPreview = false
                                capturedImageFile?.delete()
                                finalImageFile?.delete()
                                capturedImageFile = null
                                finalImageFile = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retake")
                        }
                        
                        // Crop
                        OutlinedButton(
                            onClick = {
                                showPreview = false
                                showCrop = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Crop")
                        }
                        
                        // Use image
                        Button(
                            onClick = {
                                onImageCaptured(file)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use Image")
                        }
                    }
                }
            }
        }
        
            // Top bar
            if (!showPreview) {
                TopAppBar(
                    title = { Text("Scan Document", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, "Close", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}
