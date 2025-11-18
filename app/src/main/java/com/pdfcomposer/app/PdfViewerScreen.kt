package com.pdfcomposer.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.*
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ZoomablePdfPage(
    bitmap: Bitmap,
    pageNumber: Int,
    onCropClick: (() -> Unit)? = null
) {
    // Use rememberSaveable with unique keys to preserve zoom state per page
    var scale by rememberSaveable(key = "zoom_scale_$pageNumber") { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable(key = "zoom_offset_x_$pageNumber") { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable(key = "zoom_offset_y_$pageNumber") { mutableFloatStateOf(0f) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page $pageNumber",
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                        .pointerInput(Unit) {
                            // Custom gesture handling: only consume when 2+ fingers detected
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                
                                do {
                                    val event = awaitPointerEvent()
                                    val pointerCount = event.changes.count { it.pressed }
                                    
                                    // Only handle transform gestures with 2+ fingers
                                    if (pointerCount >= 2) {
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        
                                        // Calculate max offset based on scaled size
                                        val maxX = (size.width * (scale - 1f)) / 2f
                                        val maxY = (size.height * (scale - 1f)) / 2f
                                        
                                        offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                        offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                        
                                        // Reset offset if zoomed all the way out
                                        if (scale == 1f) {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                        
                                        // Consume the event to prevent parent scrolling while zooming
                                        event.changes.forEach { it.consume() }
                                    }
                                    // else: don't consume, let parent LazyColumn handle scrolling
                                } while (event.changes.any { it.pressed })
                            }
                        }
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Page $pageNumber",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (onCropClick != null) {
                    OutlinedButton(
                        onClick = onCropClick
                    ) {
                        Icon(
                            Icons.Default.Crop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Crop")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfPath: String,
    fileName: String,
    onBack: () -> Unit,
    viewModel: PdfViewModel? = null,
    pdfUri: String? = null,
    onCropPage: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pageBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showAddBookmark by remember { mutableStateOf(false) }
    var bookmarkLabel by remember { mutableStateOf("") }
    
    // Track current page based on scroll position
    val currentPage = remember {
        derivedStateOf {
            listState.firstVisibleItemIndex
        }
    }
    
    val bookmarks = if (viewModel != null && pdfUri != null) {
        viewModel.getBookmarksForPdf(pdfUri).collectAsState(initial = emptyList()).value
    } else {
        emptyList()
    }
    
    LaunchedEffect(pdfPath) {
        scope.launch {
            var fileDescriptor: ParcelFileDescriptor? = null
            var pdfRenderer: PdfRenderer? = null
            
            try {
                val bitmaps = withContext(Dispatchers.IO) {
                    // Handle both content:// URIs (from SAF) and file paths (from cache)
                    fileDescriptor = if (pdfPath.startsWith("content://")) {
                        // Open via ContentResolver for SAF URIs
                        val uri = Uri.parse(pdfPath)
                        context.contentResolver.openFileDescriptor(uri, "r")
                            ?: throw Exception("Could not open PDF from URI")
                    } else {
                        // Open as regular file for cache files
                        val pdfFile = File(pdfPath)
                        if (!pdfFile.exists()) {
                            throw Exception("PDF file not found")
                        }
                        ParcelFileDescriptor.open(
                            pdfFile,
                            ParcelFileDescriptor.MODE_READ_ONLY
                        )
                    }
                    
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
    
    // Add Bookmark Dialog
    if (showAddBookmark) {
        AlertDialog(
            onDismissRequest = { showAddBookmark = false },
            title = { Text("Add Bookmark") },
            text = {
                Column {
                    Text("Page ${currentPage.value + 1} of ${pageBitmaps.size}")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bookmarkLabel,
                        onValueChange = { bookmarkLabel = it },
                        label = { Text("Bookmark Name") },
                        placeholder = { Text("Enter bookmark name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (bookmarkLabel.isNotBlank() && viewModel != null && pdfUri != null) {
                            scope.launch {
                                val pageNum = currentPage.value + 1
                                
                                // Add to database
                                viewModel.addBookmark(pdfUri, pageNum, bookmarkLabel)
                                
                                // Embed in PDF file
                                val result = PdfUtils.addBookmarkToPdf(
                                    File(pdfPath),
                                    pageNum,
                                    bookmarkLabel
                                )
                                
                                if (result.isSuccess) {
                                    snackbarHostState.showSnackbar("Bookmark added to page $pageNum")
                                } else {
                                    snackbarHostState.showSnackbar("Failed to embed bookmark in PDF")
                                }
                                
                                showAddBookmark = false
                                bookmarkLabel = ""
                            }
                        }
                    },
                    enabled = bookmarkLabel.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddBookmark = false
                    bookmarkLabel = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // View Bookmarks Dialog
    if (showBookmarks) {
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = { Text("Bookmarks") },
            text = {
                if (bookmarks.isEmpty()) {
                    Text("No bookmarks yet")
                } else {
                    LazyColumn {
                        items(bookmarks) { bookmark ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        listState.animateScrollToItem(bookmark.pageNumber - 1)
                                        showBookmarks = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        bookmark.label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "Page ${bookmark.pageNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarks = false }) {
                    Text("Close")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (viewModel != null && pdfUri != null) {
                        // Show bookmarks
                        IconButton(onClick = { showBookmarks = true }) {
                            BadgedBox(
                                badge = {
                                    if (bookmarks.isNotEmpty()) {
                                        Badge { Text("${bookmarks.size}") }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Bookmark, "View Bookmarks")
                            }
                        }
                        
                        // Add bookmark
                        IconButton(onClick = { showAddBookmark = true }) {
                            Icon(Icons.Default.BookmarkAdd, "Add Bookmark")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(pageBitmaps.withIndex().toList()) { (index, bitmap) ->
                            ZoomablePdfPage(
                                bitmap = bitmap,
                                pageNumber = index + 1,
                                onCropClick = if (onCropPage != null) {
                                    { onCropPage(index) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}
