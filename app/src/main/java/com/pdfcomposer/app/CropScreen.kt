package com.pdfcomposer.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rroohit.imagecropview.ImageCrop
import com.rroohit.imagecropview.util.CropType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    imageFile: File,
    onCropComplete: (File) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var cropType by remember { mutableStateOf(CropType.FREE_STYLE) }
    var isSaving by remember { mutableStateOf(false) }
    var imageCrop by remember { mutableStateOf<ImageCrop?>(null) }
    
    LaunchedEffect(imageFile) {
        val loadedBitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(imageFile.absolutePath)
        }
        originalBitmap = loadedBitmap
        currentBitmap = loadedBitmap
        if (loadedBitmap != null) {
            imageCrop = ImageCrop(loadedBitmap)
        }
    }
    
    fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CROP") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            originalBitmap?.let { original ->
                                rotationDegrees = (rotationDegrees + 90) % 360
                                val rotated = rotateBitmap(original, rotationDegrees)
                                currentBitmap = rotated
                                imageCrop = ImageCrop(rotated)
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Rotate90DegreesCw, "Rotate")
                    }
                    
                    IconButton(
                        onClick = {
                            cropType = when (cropType) {
                                CropType.FREE_STYLE -> CropType.SQUARE
                                CropType.SQUARE -> CropType.FREE_STYLE
                                else -> CropType.FREE_STYLE
                            }
                            currentBitmap?.let { bitmap ->
                                imageCrop = ImageCrop(bitmap)
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.AspectRatio, "Aspect Ratio")
                    }
                    
                    IconButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val croppedFile = withContext(Dispatchers.IO) {
                                    try {
                                        val croppedBitmap = imageCrop?.onCrop()
                                        if (croppedBitmap != null) {
                                            val outputFile = File(imageFile.parent, "cropped_${System.currentTimeMillis()}.jpg")
                                            FileOutputStream(outputFile).use { out ->
                                                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                            }
                                            outputFile
                                        } else {
                                            null
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        null
                                    }
                                }
                                isSaving = false
                                if (croppedFile != null) {
                                    onCropComplete(croppedFile)
                                }
                            }
                        },
                        enabled = !isSaving && imageCrop != null
                    ) {
                        Icon(Icons.Default.Crop, "Crop")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            imageCrop?.let { crop ->
                crop.ImageCropView(
                    modifier = Modifier.fillMaxSize(),
                    guideLineColor = Color.White.copy(alpha = 0.5f),
                    guideLineWidth = 1.dp,
                    edgeCircleSize = 10.dp,
                    showGuideLines = true,
                    cropType = cropType
                )
            }
            
            if (currentBitmap == null) {
                CircularProgressIndicator(color = Color.White)
            }
            
            if (isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Cropping...", color = Color.White)
                    }
                }
            }
        }
    }
}
