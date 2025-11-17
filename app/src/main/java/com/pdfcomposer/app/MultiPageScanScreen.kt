package com.pdfcomposer.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MultiPageScanScreen(
    onComplete: (List<File>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var capturedPages by remember { mutableStateOf<List<File>>(emptyList()) }
    var showCamera by remember { mutableStateOf(true) }
    var currentPageNumber by remember { mutableStateOf(1) }
    
    if (showCamera) {
        CameraScreen(
            onImageCaptured = { imageFile ->
                // Add page to list
                capturedPages = capturedPages + imageFile
                currentPageNumber++
                showCamera = false
            },
            onClose = {
                if (capturedPages.isEmpty()) {
                    onCancel()
                } else {
                    showCamera = false
                }
            }
        )
    } else {
        PageReviewScreen(
            capturedPages = capturedPages,
            currentPageNumber = currentPageNumber,
            onAddPage = {
                showCamera = true
            },
            onDeletePage = { index ->
                capturedPages = capturedPages.filterIndexed { i, _ -> i != index }
                if (capturedPages.isEmpty()) {
                    onCancel()
                }
            },
            onDone = {
                if (capturedPages.isNotEmpty()) {
                    onComplete(capturedPages)
                }
            },
            onCancel = onCancel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageReviewScreen(
    capturedPages: List<File>,
    currentPageNumber: Int,
    onAddPage: () -> Unit,
    onDeletePage: (Int) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    var selectedPageIndex by remember { mutableStateOf(capturedPages.size - 1) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${capturedPages.size} page${if (capturedPages.size != 1) "s" else ""} captured") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                // Page thumbnails
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(capturedPages) { index, file ->
                        PageThumbnail(
                            file = file,
                            pageNumber = index + 1,
                            isSelected = index == selectedPageIndex,
                            onClick = { selectedPageIndex = index },
                            onDelete = { onDeletePage(index) }
                        )
                    }
                }
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddPage,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Page")
                    }
                    
                    Button(
                        onClick = onDone,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Done (${capturedPages.size})")
                    }
                }
            }
        }
    ) { padding ->
        // Large preview of selected page
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (selectedPageIndex in capturedPages.indices) {
                val bitmap = remember(capturedPages[selectedPageIndex]) {
                    BitmapFactory.decodeFile(capturedPages[selectedPageIndex].absolutePath)
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Page ${selectedPageIndex + 1} of ${capturedPages.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Page ${selectedPageIndex + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PageThumbnail(
    file: File,
    pageNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .width(100.dp)
            .height(140.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                    shape = MaterialTheme.shapes.small
                )
                .clickable(onClick = onClick)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val bitmap = remember(file) {
                BitmapFactory.decodeFile(file.absolutePath)
            }
            
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page $pageNumber",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            
            Text(
                text = "Page $pageNumber",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        // Delete button
        IconButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(Color.Red, shape = MaterialTheme.shapes.small)
        ) {
            Icon(
                Icons.Default.Delete,
                "Delete",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Page $pageNumber?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
