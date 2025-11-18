package com.pdfcomposer.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfPath: String,
    fileName: String,
    onBack: () -> Unit,
    onCropPage: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(pdfPath) {
        scope.launch {
            var fileDescriptor: ParcelFileDescriptor? = null
            var pdfRenderer: PdfRenderer? = null
            
            try {
                val bitmaps = withContext(Dispatchers.IO) {
                    val pdfFile = File(pdfPath)
                    if (!pdfFile.exists()) {
                        throw Exception("PDF file not found")
                    }
                    
                    fileDescriptor = ParcelFileDescriptor.open(
                        pdfFile,
                        ParcelFileDescriptor.MODE_READ_ONLY
                    )
                    pdfRenderer = PdfRenderer(fileDescriptor!!)
                    
                    // Render pages one at a time to avoid memory issues
                    val bitmaps = mutableListOf<Bitmap>()
                    for (i in 0 until pdfRenderer!!.pageCount) {
                        val page = pdfRenderer!!.openPage(i)
                        // Render at actual size, not 2x
                        val bitmap = Bitmap.createBitmap(
                            page.width,
                            page.height,
                            Bitmap.Config.ARGB_8888
                        )
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bitmap)
                        page.close()
                    }
                    
                    bitmaps
                }
                
                pageBitmaps = bitmaps
                isLoading = false
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load PDF"
                isLoading = false
            } finally {
                // Ensure cleanup even on errors or cancellation
                withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                    try {
                        pdfRenderer?.close()
                        fileDescriptor?.close()
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                }
            }
        }
    }
    
    // Clean up bitmaps when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            pageBitmaps.forEach { it.recycle() }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error: $errorMessage",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) {
                            Text("Go Back")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(pageBitmaps.withIndex().toList()) { (index, bitmap) ->
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    
                                    if (onCropPage != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            OutlinedButton(
                                                onClick = { onCropPage(index) }
                                            ) {
                                                Icon(Icons.Default.Crop, "Crop")
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Crop Page ${index + 1}")
                                            }
                                        }
                                    }
                                    
                                    Text(
                                        text = "Page ${index + 1} of ${pageBitmaps.size}",
                                        modifier = Modifier.padding(8.dp),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
