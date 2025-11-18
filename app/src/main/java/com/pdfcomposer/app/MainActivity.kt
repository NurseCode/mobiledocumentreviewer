package com.pdfcomposer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pdfUri: String,
    val pageNumber: Int,
    val label: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "pdf_documents")
data class StoredPdfDocument(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val filePath: String,
    val pageCount: Int,
    val category: String = "",  // Folder/category name (e.g., "Client A", "Receipts")
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE pdfUri = :uri ORDER BY pageNumber ASC")
    fun getBookmarksForPdf(uri: String): Flow<List<Bookmark>>
    
    @Insert
    suspend fun insert(bookmark: Bookmark)
    
    @Delete
    suspend fun delete(bookmark: Bookmark)
    
    @Query("DELETE FROM bookmarks WHERE pdfUri = :uri")
    suspend fun deleteAllForPdf(uri: String)
}

@Dao
interface PdfDocumentDao {
    @Query("SELECT * FROM pdf_documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<StoredPdfDocument>>
    
    @Query("SELECT * FROM pdf_documents WHERE category = :category ORDER BY createdAt DESC")
    fun getDocumentsByCategory(category: String): Flow<List<StoredPdfDocument>>
    
    @Query("SELECT DISTINCT category FROM pdf_documents WHERE category != '' ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>
    
    @Insert
    suspend fun insert(document: StoredPdfDocument)
    
    @Update
    suspend fun update(document: StoredPdfDocument)
    
    @Delete
    suspend fun delete(document: StoredPdfDocument)
    
    @Query("SELECT COUNT(*) FROM pdf_documents")
    suspend fun getCount(): Int
}

@Database(entities = [Bookmark::class, StoredPdfDocument::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun pdfDocumentDao(): PdfDocumentDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pdf_composer_database"
                )
                .fallbackToDestructiveMigration()  // For development; proper migration needed for production
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class PdfViewModel(
    private val bookmarkDao: BookmarkDao,
    private val pdfDocumentDao: PdfDocumentDao
) : ViewModel() {
    
    val allDocuments: Flow<List<StoredPdfDocument>> = pdfDocumentDao.getAllDocuments()
    
    fun getBookmarksForPdf(uri: String): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksForPdf(uri)
    }
    
    fun addBookmark(pdfUri: String, pageNumber: Int, label: String) {
        viewModelScope.launch {
            bookmarkDao.insert(Bookmark(pdfUri = pdfUri, pageNumber = pageNumber, label = label))
        }
    }
    
    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkDao.delete(bookmark)
        }
    }
    
    fun addDocument(fileName: String, filePath: String, pageCount: Int, category: String = "") {
        viewModelScope.launch {
            pdfDocumentDao.insert(StoredPdfDocument(fileName = fileName, filePath = filePath, pageCount = pageCount, category = category))
        }
    }
    
    fun updateDocument(document: StoredPdfDocument) {
        viewModelScope.launch {
            pdfDocumentDao.update(document)
        }
    }
    
    fun renameDocument(document: StoredPdfDocument, newName: String) {
        viewModelScope.launch {
            val updated = document.copy(fileName = newName)
            pdfDocumentDao.update(updated)
        }
    }
    
    fun updateDocumentCategory(document: StoredPdfDocument, newCategory: String) {
        viewModelScope.launch {
            val updated = document.copy(category = newCategory)
            pdfDocumentDao.update(updated)
        }
    }
    
    fun deleteDocument(document: StoredPdfDocument) {
        viewModelScope.launch {
            pdfDocumentDao.delete(document)
            bookmarkDao.deleteAllForPdf(document.filePath)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize PDFBox for Android
        ImageToPdfUtils.initialize(this)
        
        setContent {
            PdfComposerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val database = AppDatabase.getDatabase(LocalContext.current)
                    val viewModel = remember {
                        PdfViewModel(database.bookmarkDao(), database.pdfDocumentDao())
                    }
                    MainScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun PdfComposerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = dynamicColorScheme(),
        content = content
    )
}

@Composable
fun dynamicColorScheme(): ColorScheme {
    return lightColorScheme(
        primary = Color(0xFF1565C0),           // Professional dark blue
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE3F2FD),  // Very light blue container (not lilac)
        onPrimaryContainer = Color(0xFF0D47A1), // Darker blue for text
        secondary = Color(0xFF455A64),         // Blue-grey
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFECEFF1), // Light blue-grey container (not lilac)
        onSecondaryContainer = Color(0xFF263238),
        surfaceVariant = Color(0xFFE0E0E0),    // Light grey variant
        onSurfaceVariant = Color(0xFF424242),
        background = Color(0xFFFAFAFA),        // Light grey background
        surface = Color.White,
        onSurface = Color(0xFF212121)          // Dark text
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: PdfViewModel) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val hasCompletedOnboarding by settingsManager.hasCompletedOnboardingFlow.collectAsState(initial = false)
    val safDirectoryUri by settingsManager.safDirectoryUriFlow.collectAsState(initial = null)
    
    var showOnboarding by remember { mutableStateOf(false) }
    var selectedScreen by remember { mutableStateOf(Screen.Home) }
    var viewingDocument by remember { mutableStateOf<StoredPdfDocument?>(null) }
    var galleryImageToCrop by remember { mutableStateOf<File?>(null) }  // Track gallery import crop flow
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600
    val scope = rememberCoroutineScope()
    
    // Check onboarding status on launch
    LaunchedEffect(hasCompletedOnboarding) {
        if (!hasCompletedOnboarding) {
            showOnboarding = true
        }
    }
    
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                // Take persistable permission
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                settingsManager.setSafDirectoryUri(it.toString())
                settingsManager.setHasCompletedOnboarding(true)
                showOnboarding = false
            }
        }
    }
    
    if (showOnboarding) {
        OnboardingScreen(
            onSelectFolder = { folderPickerLauncher.launch(null) },
            onSkip = {
                scope.launch {
                    settingsManager.setHasCompletedOnboarding(true)
                    showOnboarding = false
                }
            }
        )
        return
    }
    
    // Show crop screen for gallery-imported images
    if (galleryImageToCrop != null) {
        CropScreen(
            imageFile = galleryImageToCrop!!,
            onCropComplete = { croppedFile ->
                scope.launch {
                    // Convert cropped image to PDF
                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                    val pdfFile = File(context.cacheDir, "scanned_$timestamp.pdf")
                    
                    val result = ImageToPdfUtils.imageToPdf(context, croppedFile, pdfFile)
                    result.onSuccess { pdf ->
                        // Add to database
                        viewModel.addDocument("scanned_$timestamp.pdf", pdf.absolutePath, 1)
                    }
                    
                    // Clean up
                    croppedFile.delete()
                    galleryImageToCrop?.delete()
                    galleryImageToCrop = null
                }
            },
            onCancel = {
                // Clean up and go back
                galleryImageToCrop?.delete()
                galleryImageToCrop = null
            }
        )
        return
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick PDF Composer") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            if (!isTablet) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, "Home") },
                        label = { Text("Home", style = MaterialTheme.typography.labelSmall) },
                        selected = selectedScreen == Screen.Home,
                        onClick = { selectedScreen = Screen.Home }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.CameraAlt, "Scan") },
                        label = { Text("Scan", style = MaterialTheme.typography.labelSmall) },
                        selected = selectedScreen == Screen.Scan,
                        onClick = { selectedScreen = Screen.Scan }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Edit, "Tools") },
                        label = { Text("Tools", style = MaterialTheme.typography.labelSmall) },
                        selected = selectedScreen == Screen.Tools,
                        onClick = { selectedScreen = Screen.Tools }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, "Settings") },
                        label = { Text("Settings", style = MaterialTheme.typography.labelSmall) },
                        selected = selectedScreen == Screen.Settings,
                        onClick = { selectedScreen = Screen.Settings }
                    )
                }
            }
        }
    ) { padding ->
        if (isTablet) {
            Row(modifier = Modifier.padding(padding)) {
                NavigationRail(
                    modifier = Modifier.width(80.dp)
                ) {
                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Home, "Home") },
                        label = { Text("Home") },
                        selected = selectedScreen == Screen.Home,
                        onClick = { selectedScreen = Screen.Home }
                    )
                    NavigationRailItem(
                        icon = { Icon(Icons.Default.CameraAlt, "Scan") },
                        label = { Text("Scan") },
                        selected = selectedScreen == Screen.Scan,
                        onClick = { selectedScreen = Screen.Scan }
                    )
                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Edit, "Tools") },
                        label = { Text("Tools") },
                        selected = selectedScreen == Screen.Tools,
                        onClick = { selectedScreen = Screen.Tools }
                    )
                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Settings, "Settings") },
                        label = { Text("Settings") },
                        selected = selectedScreen == Screen.Settings,
                        onClick = { selectedScreen = Screen.Settings }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (viewingDocument != null) {
                        PdfViewerScreen(
                            pdfPath = viewingDocument!!.filePath,
                            fileName = viewingDocument!!.fileName,
                            onBack = { viewingDocument = null },
                            viewModel = viewModel,
                            pdfUri = viewingDocument!!.filePath
                        )
                    } else {
                        ScreenContent(
                            selectedScreen, 
                            viewModel, 
                            onViewDocument = { viewingDocument = it },
                            onGalleryImageSelected = { galleryImageToCrop = it }
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.padding(padding)) {
                if (viewingDocument != null) {
                    PdfViewerScreen(
                        pdfPath = viewingDocument!!.filePath,
                        fileName = viewingDocument!!.fileName,
                        onBack = { viewingDocument = null },
                        viewModel = viewModel,
                        pdfUri = viewingDocument!!.filePath
                    )
                } else {
                    ScreenContent(
                        selectedScreen, 
                        viewModel, 
                        onViewDocument = { viewingDocument = it },
                        onGalleryImageSelected = { galleryImageToCrop = it }
                    )
                }
            }
        }
    }
}

@Composable
fun ScreenContent(
    screen: Screen, 
    viewModel: PdfViewModel, 
    onViewDocument: (StoredPdfDocument) -> Unit = {},
    onGalleryImageSelected: (File) -> Unit = {}
) {
    when (screen) {
        Screen.Home -> HomeScreen(viewModel, onViewDocument)
        Screen.Scan -> ScanScreen(viewModel, onGalleryImageSelected)
        Screen.Tools -> ToolsScreen(viewModel)
        Screen.Settings -> SettingsScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: PdfViewModel, onViewDocument: (StoredPdfDocument) -> Unit = {}) {
    val documents by viewModel.allDocuments.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    
    val allCategories = remember(documents) {
        documents
            .mapNotNull { if (it.category.isNotBlank()) it.category else null }
            .distinct()
            .sorted()
    }
    
    val filteredDocuments = remember(documents, searchQuery, selectedCategory) {
        documents.filter { doc ->
            val matchesSearch = searchQuery.isBlank() || 
                doc.fileName.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == null || 
                doc.category == selectedCategory
            matchesSearch && matchesCategory
        }
    }
    
    val hasActiveFilters = searchQuery.isNotBlank() || selectedCategory != null
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Recent Documents",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search documents...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
        
        if (allCategories.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All") },
                        leadingIcon = if (selectedCategory == null) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
                items(allCategories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { 
                            selectedCategory = if (selectedCategory == category) null else category 
                        },
                        label = { Text(category) },
                        leadingIcon = if (selectedCategory == category) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }
        }
        
        if (hasActiveFilters) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredDocuments.size} of ${documents.size} documents",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { 
                        searchQuery = ""
                        selectedCategory = null
                    }
                ) {
                    Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear filters")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (documents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No documents yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Scan or upload a document to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (filteredDocuments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No documents found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Try adjusting your search or filters",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredDocuments) { doc ->
                    DocumentCard(doc, viewModel, onClick = { onViewDocument(doc) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentCard(document: StoredPdfDocument, viewModel: PdfViewModel, onClick: () -> Unit = {}) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.fileName,
                    style = MaterialTheme.typography.titleMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${document.pageCount} pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (document.category.isNotBlank()) {
                        Text(
                            text = " • ${document.category}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "More options")
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = {
                            showMenu = false
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                val uri = if (document.filePath.startsWith("content://")) {
                                    Uri.parse(document.filePath)
                                } else {
                                    androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        File(document.filePath)
                                    )
                                }
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = {
                            showMenu = false
                            viewModel.deleteDocument(document)
                        }
                    )
                }
            }
        }
    }
    
    if (showRenameDialog) {
        RenameDocumentDialog(
            currentName = document.fileName,
            currentCategory = document.category,
            onSave = { newName, newCategory ->
                viewModel.updateDocument(document.copy(fileName = newName, category = newCategory))
                showRenameDialog = false
            },
            onCancel = { showRenameDialog = false }
        )
    }
}

data class PendingSaveData(
    val pdfFile: File,
    val pageCount: Int,
    val suggestedName: String,
    val ocrResults: List<OcrResult>
)

@Composable
fun ScanScreen(
    viewModel: PdfViewModel,
    onGalleryImageSelected: (File) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val safDirectoryUri by settingsManager.safDirectoryUriFlow.collectAsState(initial = null)
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var showMultiPageScan by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }
    var showNamingDialog by remember { mutableStateOf(false) }
    var pendingSaveData by remember { mutableStateOf<PendingSaveData?>(null) }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            showMultiPageScan = true
        }
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isProcessing = true
                processingMessage = "Preparing image..."
                
                try {
                    // Create temp file from URI
                    val inputStream = context.contentResolver.openInputStream(it)
                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                    val tempImageFile = File(context.cacheDir, "gallery_${timestamp}.jpg")
                    inputStream?.use { input ->
                        tempImageFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // Show crop screen instead of directly converting to PDF
                    onGalleryImageSelected(tempImageFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isProcessing = false
                }
            }
        }
    }
    
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistent permission to access the file
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                           android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            
            // Get file info and add to database
            val fileName = StorageUtils.getFileName(context, it) ?: "Unknown.pdf"
            // TODO: Get page count from PDF
            viewModel.addDocument(fileName, it.toString(), 1)
        }
    }
    
    if (showMultiPageScan) {
        MultiPageScanScreen(
            onComplete = { imageFiles ->
                showMultiPageScan = false
                scope.launch {
                    isProcessing = true
                    processingMessage = "Processing ${imageFiles.size} page${if (imageFiles.size != 1) "s" else ""}..."
                    
                    // Declare temp file collections outside try for cleanup access
                    val optimizedFiles = mutableListOf<File>()
                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                    val pdfFile = File(context.cacheDir, "scan_$timestamp.pdf")
                    
                    try {
                        // Optimize all images
                        imageFiles.forEachIndexed { index, imageFile ->
                            processingMessage = "Optimizing page ${index + 1} of ${imageFiles.size}..."
                            val optimizedFile = File(context.cacheDir, "optimized_page_${index}.jpg")
                            CameraUtils.optimizeImageForPdf(imageFile, optimizedFile)
                            optimizedFiles.add(optimizedFile)
                        }
                        
                        processingMessage = "Performing OCR on ${imageFiles.size} page${if (imageFiles.size != 1) "s" else ""}..."
                        
                        // Perform OCR on all pages
                        val ocrResults = mutableListOf<OcrResult>()
                        optimizedFiles.forEachIndexed { index, file ->
                            processingMessage = "OCR page ${index + 1} of ${optimizedFiles.size}..."
                            val ocrResult = CameraUtils.extractTextFromImage(context, file)
                            ocrResult.onSuccess { ocr ->
                                ocrResults.add(ocr)
                            }
                        }
                        
                        processingMessage = "Creating ${imageFiles.size}-page PDF..."
                        
                        val result = ImageToPdfUtils.imagesToPdf(context, optimizedFiles, pdfFile)
                        result.onSuccess { pdf ->
                            // Generate smart filename from OCR
                            val suggestedName = if (ocrResults.isNotEmpty()) {
                                val docType = CameraUtils.detectDocumentType(ocrResults[0].fullText)
                                when (docType) {
                                    com.pdfcomposer.app.DocumentType.RECEIPT -> "Receipt_$timestamp"
                                    com.pdfcomposer.app.DocumentType.CONTRACT -> "Contract_$timestamp"
                                    com.pdfcomposer.app.DocumentType.FORM -> "Form_$timestamp"
                                    com.pdfcomposer.app.DocumentType.LETTER -> "Letter_$timestamp"
                                    com.pdfcomposer.app.DocumentType.GENERAL -> "Document_$timestamp"
                                }
                            } else {
                                "Scan_$timestamp"
                            }
                            
                            // Store data and show naming dialog
                            pendingSaveData = PendingSaveData(
                                pdfFile = pdf,
                                pageCount = imageFiles.size,
                                suggestedName = suggestedName,
                                ocrResults = ocrResults
                            )
                            showNamingDialog = true
                        }
                        
                        // Cleanup optimized and captured images (keep PDF for now)
                        optimizedFiles.forEach { file ->
                            if (!file.delete() && file.exists()) {
                                android.util.Log.w("ScanScreen", "Failed to delete optimized file: ${file.absolutePath}")
                            }
                        }
                        imageFiles.forEach { file ->
                            if (!file.delete() && file.exists()) {
                                android.util.Log.w("ScanScreen", "Failed to delete captured file: ${file.absolutePath}")
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Ensure cleanup even on exception
                        optimizedFiles.forEach { it.delete() }
                        imageFiles.forEach { it.delete() }
                        pdfFile.delete()
                    } finally {
                        isProcessing = false
                    }
                }
            },
            onCancel = { showMultiPageScan = false }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        if (hasCameraPermission) {
                            showMultiPageScan = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan New Document")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Existing PDF")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload from Gallery")
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "Scan documents with camera, or open existing PDFs from anywhere (Dropbox, Drive, local storage)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Processing overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = processingMessage,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Document naming dialog
    if (showNamingDialog && pendingSaveData != null) {
        val saveData = pendingSaveData!!
        DocumentNamingDialog(
            suggestedName = saveData.suggestedName,
            onSave = { fileName, category ->
                scope.launch {
                    isProcessing = true
                    processingMessage = "Saving document..."
                    
                    try {
                        val finalFileName = if (fileName.endsWith(".pdf")) fileName else "$fileName.pdf"
                        
                        // Save to SAF or fallback to app storage
                        val savedUri = if (safDirectoryUri != null) {
                            // Save to user-selected SAF location
                            val folderUri = Uri.parse(safDirectoryUri)
                            val pdfUri = StorageUtils.createPdfFile(context, folderUri, finalFileName)
                            
                            if (pdfUri != null) {
                                StorageUtils.copyPdfToSaf(context, saveData.pdfFile, pdfUri)
                                pdfUri.toString()
                            } else {
                                // Fallback: use app files directory
                                val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), finalFileName)
                                saveData.pdfFile.copyTo(appFile, overwrite = true)
                                appFile.absolutePath
                            }
                        } else {
                            // No SAF folder selected - use app files directory
                            val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), finalFileName)
                            saveData.pdfFile.copyTo(appFile, overwrite = true)
                            appFile.absolutePath
                        }
                        
                        // Add to database
                        viewModel.addDocument(finalFileName, savedUri, saveData.pageCount, category)
                        
                        // Clean up temp PDF
                        saveData.pdfFile.delete()
                        
                        showNamingDialog = false
                        pendingSaveData = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Still clean up
                        pendingSaveData?.pdfFile?.delete()
                        showNamingDialog = false
                        pendingSaveData = null
                    } finally {
                        isProcessing = false
                    }
                }
            },
            onCancel = {
                // Clean up temp PDF on cancel
                pendingSaveData?.pdfFile?.delete()
                showNamingDialog = false
                pendingSaveData = null
            }
        )
    }
}

@Composable
fun ToolsScreen(viewModel: PdfViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "PDF Tools",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        ToolCard(
            title = "Merge PDFs",
            description = "Combine multiple PDF files into one",
            icon = Icons.Default.MergeType
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        ToolCard(
            title = "Split PDF",
            description = "Extract pages or split into multiple files",
            icon = Icons.Default.CallSplit
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        ToolCard(
            title = "Compress PDF",
            description = "Reduce file size while maintaining quality",
            icon = Icons.Default.Compress
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        ToolCard(
            title = "OCR Text Extract",
            description = "Extract text from scanned documents",
            icon = Icons.Default.TextFields
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        ToolCard(
            title = "Annotate & Draw",
            description = "Add notes, highlights, and drawings",
            icon = Icons.Default.Draw
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        ToolCard(
            title = "Sign Document",
            description = "Draw signature and embed in PDF",
            icon = Icons.Default.Edit
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DocumentNamingDialog(
    suggestedName: String,
    onSave: (fileName: String, category: String) -> Unit,
    onCancel: () -> Unit
) {
    var fileName by remember { mutableStateOf(suggestedName) }
    var category by remember { mutableStateOf("") }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Save Document") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Folder/Category (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g., Client A, Receipts, Personal") }
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onSave(fileName, category) },
                enabled = fileName.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenameDocumentDialog(
    currentName: String,
    currentCategory: String,
    onSave: (fileName: String, category: String) -> Unit,
    onCancel: () -> Unit
) {
    var fileName by remember { mutableStateOf(currentName) }
    var category by remember { mutableStateOf(currentCategory) }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Edit Document") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Folder/Category") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g., Client A, Receipts, Personal") }
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onSave(fileName, category) },
                enabled = fileName.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    
    val safDirectoryUri by settingsManager.safDirectoryUriFlow.collectAsState(initial = null)
    val defaultCategory by settingsManager.defaultCategoryFlow.collectAsState(initial = "")
    val imageQuality by settingsManager.imageQualityFlow.collectAsState(initial = ImageQuality.HIGH)
    val ocrLanguage by settingsManager.ocrLanguageFlow.collectAsState(initial = "en")
    val autoNaming by settingsManager.autoNamingFlow.collectAsState(initial = true)
    val theme by settingsManager.themeFlow.collectAsState(initial = AppTheme.SYSTEM)
    val sortOrder by settingsManager.sortOrderFlow.collectAsState(initial = SortOrder.NEWEST_FIRST)
    
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var tempCategory by remember { mutableStateOf("") }
    
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                settingsManager.setSafDirectoryUri(it.toString())
            }
        }
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        item {
            Text(
                text = "STORAGE & FILES",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { folderPickerLauncher.launch(null) }
            ) {
                ListItem(
                    headlineContent = { Text("Storage Location") },
                    supportingContent = { 
                        Text(if (safDirectoryUri != null) "Configured" else "Not set - using temporary storage") 
                    },
                    leadingContent = { Icon(Icons.Default.Folder, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) }
                )
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showCategoryDialog = true }
            ) {
                ListItem(
                    headlineContent = { Text("Default Category") },
                    supportingContent = { Text(if (defaultCategory.isBlank()) "None" else defaultCategory) },
                    leadingContent = { Icon(Icons.Default.Label, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) }
                )
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        val cacheDir = context.cacheDir
                        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                    }
                }
            ) {
                ListItem(
                    headlineContent = { Text("Clear Cache") },
                    supportingContent = { Text("Remove temporary files") },
                    leadingContent = { Icon(Icons.Default.Delete, null) }
                )
            }
        }
        
        item {
            Text(
                text = "SCANNING & OCR",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        headlineContent = { Text("Image Quality") },
                        supportingContent = { Text("Compression level for scanned images") }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = imageQuality == ImageQuality.HIGH,
                            onClick = { scope.launch { settingsManager.setImageQuality(ImageQuality.HIGH) } },
                            label = { Text("High", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = imageQuality == ImageQuality.MEDIUM,
                            onClick = { scope.launch { settingsManager.setImageQuality(ImageQuality.MEDIUM) } },
                            label = { Text("Medium", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = imageQuality == ImageQuality.LOW,
                            onClick = { scope.launch { settingsManager.setImageQuality(ImageQuality.LOW) } },
                            label = { Text("Low", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showLanguageDialog = true }
            ) {
                ListItem(
                    headlineContent = { Text("OCR Language") },
                    supportingContent = { 
                        Text(when(ocrLanguage) {
                            "en" -> "English"
                            "es" -> "Spanish"
                            "fr" -> "French"
                            "de" -> "German"
                            "it" -> "Italian"
                            "pt" -> "Portuguese"
                            "zh" -> "Chinese"
                            "ja" -> "Japanese"
                            else -> "English"
                        })
                    },
                    leadingContent = { Icon(Icons.Default.Language, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) }
                )
            }
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Auto-Naming") },
                    supportingContent = { Text("Suggest names from OCR text") },
                    leadingContent = { Icon(Icons.Default.AutoAwesome, null) },
                    trailingContent = {
                        Switch(
                            checked = autoNaming,
                            onCheckedChange = { scope.launch { settingsManager.setAutoNaming(it) } }
                        )
                    }
                )
            }
        }
        
        item {
            Text(
                text = "APPEARANCE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        headlineContent = { Text("Theme") },
                        supportingContent = { Text("Choose light or dark mode") }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = theme == AppTheme.LIGHT,
                            onClick = { scope.launch { settingsManager.setTheme(AppTheme.LIGHT) } },
                            label = { Text("Light", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = theme == AppTheme.DARK,
                            onClick = { scope.launch { settingsManager.setTheme(AppTheme.DARK) } },
                            label = { Text("Dark", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = theme == AppTheme.SYSTEM,
                            onClick = { scope.launch { settingsManager.setTheme(AppTheme.SYSTEM) } },
                            label = { Text("System", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        headlineContent = { Text("Sort Order") },
                        supportingContent = { Text("Document list sorting") }
                    )
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = sortOrder == SortOrder.NEWEST_FIRST,
                            onClick = { scope.launch { settingsManager.setSortOrder(SortOrder.NEWEST_FIRST) } },
                            label = { Text("Newest First") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        FilterChip(
                            selected = sortOrder == SortOrder.OLDEST_FIRST,
                            onClick = { scope.launch { settingsManager.setSortOrder(SortOrder.OLDEST_FIRST) } },
                            label = { Text("Oldest First") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        FilterChip(
                            selected = sortOrder == SortOrder.NAME_A_Z,
                            onClick = { scope.launch { settingsManager.setSortOrder(SortOrder.NAME_A_Z) } },
                            label = { Text("Name (A-Z)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        FilterChip(
                            selected = sortOrder == SortOrder.NAME_Z_A,
                            onClick = { scope.launch { settingsManager.setSortOrder(SortOrder.NAME_Z_A) } },
                            label = { Text("Name (Z-A)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        
        item {
            Text(
                text = "ABOUT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("App Version") },
                    supportingContent = { Text("1.0.0") },
                    leadingContent = { Icon(Icons.Default.Info, null) }
                )
            }
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Storage Usage") },
                    supportingContent = { 
                        val database = AppDatabase.getDatabase(context)
                        val docCount by database.pdfDocumentDao().getAllDocuments().collectAsState(initial = emptyList())
                        Text("${docCount.size} documents")
                    },
                    leadingContent = { Icon(Icons.Default.Storage, null) }
                )
            }
        }
    }
    
    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Default Category") },
            text = {
                OutlinedTextField(
                    value = tempCategory,
                    onValueChange = { tempCategory = it },
                    label = { Text("Category name") },
                    placeholder = { Text("e.g., Work, Personal, Receipts") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        settingsManager.setDefaultCategory(tempCategory)
                        showCategoryDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
        LaunchedEffect(Unit) {
            tempCategory = defaultCategory
        }
    }
    
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("OCR Language") },
            text = {
                Column {
                    listOf(
                        "en" to "English",
                        "es" to "Spanish",
                        "fr" to "French",
                        "de" to "German",
                        "it" to "Italian",
                        "pt" to "Portuguese",
                        "zh" to "Chinese",
                        "ja" to "Japanese"
                    ).forEach { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        settingsManager.setOcrLanguage(code)
                                        showLanguageDialog = false
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = ocrLanguage == code,
                                onClick = {
                                    scope.launch {
                                        settingsManager.setOcrLanguage(code)
                                        showLanguageDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun OnboardingScreen(onSelectFolder: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            "Choose Storage Location",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            "Select a folder where your PDFs will be saved. This folder can be on your phone, Dropbox, Google Drive, or any other location.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Your documents will persist even if you uninstall the app!",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onSelectFolder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Folder")
        }
        TextButton(onClick = onSkip) {
            Text("Skip (use temporary storage)")
        }
    }
}

enum class Screen {
    Home, Scan, Tools, Settings
}
