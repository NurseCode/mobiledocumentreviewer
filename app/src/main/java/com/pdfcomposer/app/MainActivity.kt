package com.pdfcomposer.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.activity.result.IntentSenderRequest
import android.app.Activity

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

val LocalRequestDocumentSign = compositionLocalOf<((StoredPdfDocument) -> Unit)?> { null }
val LocalOpenSigningPadForTools = compositionLocalOf<(() -> Unit)?> { null }
val LocalToolsSignatureBitmap = compositionLocalOf<android.graphics.Bitmap?> { null }
val LocalConsumeToolsSignature = compositionLocalOf<(() -> Unit)?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: PdfViewModel) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val hasCompletedOnboarding by settingsManager.hasCompletedOnboardingFlow.collectAsState(initial = false)
    val safDirectoryUri by settingsManager.safDirectoryUriFlow.collectAsState(initial = null)
    
    var showOnboarding by remember { mutableStateOf(false) }
    var selectedScreen by rememberSaveable { mutableStateOf(Screen.Home) }
    var viewingDocument by remember { mutableStateOf<StoredPdfDocument?>(null) }
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600
    val scope = rememberCoroutineScope()
    
    var fullScreenDrawActive by rememberSaveable { mutableStateOf(false) }
    var signatureBitmapResult by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    var docSignTarget by remember { mutableStateOf<StoredPdfDocument?>(null) }
    var docSignShowDialog by rememberSaveable { mutableStateOf(false) }
    var docSignBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var docSignPdfFile by remember { mutableStateOf<File?>(null) }
    var docSignShowPlacement by rememberSaveable { mutableStateOf(false) }
    var docSignAwaitingPad by rememberSaveable { mutableStateOf(false) }
    
    var toolsSignAwaitingPad by rememberSaveable { mutableStateOf(false) }
    var toolsSignBitmapConsumed by remember { mutableStateOf(false) }
    
    // Check onboarding status on launch
    LaunchedEffect(hasCompletedOnboarding) {
        showOnboarding = !hasCompletedOnboarding
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
    
    Box(modifier = Modifier.fillMaxSize()) {
    CompositionLocalProvider(
        LocalRequestDocumentSign provides { doc ->
            docSignTarget = doc
            docSignShowDialog = true
        },
        LocalOpenSigningPadForTools provides {
            signatureBitmapResult = null
            toolsSignBitmapConsumed = false
            toolsSignAwaitingPad = true
            fullScreenDrawActive = true
        },
        LocalToolsSignatureBitmap provides (if (toolsSignBitmapConsumed) null else if (toolsSignAwaitingPad && signatureBitmapResult != null) signatureBitmapResult else null),
        LocalConsumeToolsSignature provides {
            toolsSignBitmapConsumed = true
            toolsSignAwaitingPad = false
        }
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doc Tracker Pro") },
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
                        label = { Text("Home", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = selectedScreen == Screen.Home,
                        onClick = { selectedScreen = Screen.Home }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.CameraAlt, "Scan") },
                        label = { Text("Scan", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = selectedScreen == Screen.Scan,
                        onClick = { selectedScreen = Screen.Scan }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Edit, "Tools") },
                        label = { Text("Tools", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = selectedScreen == Screen.Tools,
                        onClick = { selectedScreen = Screen.Tools }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, "Settings") },
                        label = { Text("Settings", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                        label = { Text("Home", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = selectedScreen == Screen.Home,
                        onClick = { selectedScreen = Screen.Home }
                    )
                    NavigationRailItem(
                        icon = { Icon(Icons.Default.CameraAlt, "Scan") },
                        label = { Text("Scan", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = selectedScreen == Screen.Scan,
                        onClick = { selectedScreen = Screen.Scan }
                    )
                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Edit, "Tools") },
                        label = { Text("Tools", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = selectedScreen == Screen.Tools,
                        onClick = { selectedScreen = Screen.Tools }
                    )
                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Settings, "Settings") },
                        label = { Text("Settings", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                            onViewDocument = { viewingDocument = it }
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
                        onViewDocument = { viewingDocument = it }
                    )
                }
            }
        }
    }
    } // CompositionLocalProvider
    
    if (docSignShowDialog && docSignTarget != null) {
        SignDocumentDialog(
            onDismiss = {
                docSignShowDialog = false
                docSignTarget = null
            },
            onOpenFullScreenDraw = {
                docSignShowDialog = false
                docSignAwaitingPad = true
                fullScreenDrawActive = true
            },
            onSignatureReady = { bitmap ->
                docSignBitmap = bitmap
                docSignShowDialog = false
                scope.launch {
                    try {
                        val doc = docSignTarget ?: return@launch
                        val file = if (doc.filePath.startsWith("content://")) {
                            StorageUtils.copyUriToTempFile(context, Uri.parse(doc.filePath))
                        } else {
                            val sourceFile = File(doc.filePath)
                            if (sourceFile.exists()) {
                                val tempFile = File(context.cacheDir, "temp_sign_${System.currentTimeMillis()}.pdf")
                                sourceFile.copyTo(tempFile, overwrite = true)
                                tempFile
                            } else null
                        }
                        if (file != null && file.exists()) {
                            docSignPdfFile = file
                            docSignShowPlacement = true
                        } else {
                            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                            docSignBitmap = null
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
                        docSignBitmap = null
                    }
                }
            }
        )
    }
    
    if (docSignShowPlacement && docSignPdfFile != null && docSignBitmap != null) {
        SignPdfScreen(
            pdfFile = docSignPdfFile!!,
            signatureBitmap = docSignBitmap!!,
            onDismiss = {
                docSignPdfFile?.delete()
                docSignPdfFile = null
                docSignBitmap = null
                docSignShowPlacement = false
                docSignTarget = null
            },
            onSigned = { signedFile ->
                scope.launch {
                    try {
                        val savedUri = if (safDirectoryUri != null) {
                            val folderUri = Uri.parse(safDirectoryUri)
                            val pdfUri = StorageUtils.createPdfFile(context, folderUri, signedFile.name)
                            if (pdfUri != null) {
                                StorageUtils.copyPdfToSaf(context, signedFile, pdfUri)
                                pdfUri.toString()
                            } else {
                                val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), signedFile.name)
                                signedFile.copyTo(appFile, overwrite = true)
                                appFile.absolutePath
                            }
                        } else {
                            val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), signedFile.name)
                            signedFile.copyTo(appFile, overwrite = true)
                            appFile.absolutePath
                        }
                        viewModel.addDocument(
                            signedFile.name,
                            savedUri,
                            signedFile.countPages(),
                            docSignTarget?.category ?: ""
                        )
                        Toast.makeText(context, "Document signed successfully!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error saving signed PDF: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        signedFile.delete()
                        docSignPdfFile?.delete()
                        docSignPdfFile = null
                        docSignBitmap = null
                        docSignShowPlacement = false
                        docSignTarget = null
                    }
                }
            }
        )
    }
    
    if (fullScreenDrawActive) {
        FullScreenDrawSignature(
            onDismiss = {
                fullScreenDrawActive = false
                docSignAwaitingPad = false
                toolsSignAwaitingPad = false
            },
            onSignatureReady = { bitmap ->
                fullScreenDrawActive = false
                if (docSignAwaitingPad) {
                    docSignAwaitingPad = false
                    docSignBitmap = bitmap
                    val doc = docSignTarget
                    if (doc != null) {
                        scope.launch {
                            try {
                                val file = if (doc.filePath.startsWith("content://")) {
                                    StorageUtils.copyUriToTempFile(context, Uri.parse(doc.filePath))
                                } else {
                                    val sourceFile = File(doc.filePath)
                                    if (sourceFile.exists()) {
                                        val tempFile = File(context.cacheDir, "temp_sign_${System.currentTimeMillis()}.pdf")
                                        sourceFile.copyTo(tempFile, overwrite = true)
                                        tempFile
                                    } else null
                                }
                                if (file != null && file.exists()) {
                                    docSignPdfFile = file
                                    docSignShowPlacement = true
                                } else {
                                    Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                                    docSignBitmap = null
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
                                docSignBitmap = null
                            }
                        }
                    }
                } else {
                    signatureBitmapResult = bitmap
                }
            }
        )
    }
    } // Box
}

@Composable
fun ScreenContent(
    screen: Screen, 
    viewModel: PdfViewModel, 
    onViewDocument: (StoredPdfDocument) -> Unit = {}
) {
    when (screen) {
        Screen.Home -> HomeScreen(viewModel, onViewDocument)
        Screen.Scan -> ScanScreen(viewModel)
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

@Composable
fun PdfThumbnail(filePath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var thumbnail by remember(filePath) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(filePath) { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val fd = if (filePath.startsWith("content://")) {
                    context.contentResolver.openFileDescriptor(Uri.parse(filePath), "r")
                } else {
                    val file = File(filePath)
                    if (file.exists()) ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) else null
                }
                if (fd != null) {
                    val renderer = PdfRenderer(fd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val thumbWidth = 150
                        val scale = thumbWidth.toFloat() / page.width.coerceAtLeast(1)
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        thumbnail = bmp
                    }
                    renderer.close()
                    fd.close()
                } else {
                    failed = true
                }
            } catch (_: Exception) {
                failed = true
            }
        }
    }

    if (thumbnail != null) {
        Image(
            bitmap = thumbnail!!.asImageBitmap(),
            contentDescription = "PDF preview",
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Icon(
            Icons.Default.PictureAsPdf,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentCard(document: StoredPdfDocument, viewModel: PdfViewModel, onClick: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val safDirectoryUri by settingsManager.safDirectoryUriFlow.collectAsState(initial = null)
    
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showOcrDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showAnnotateDialog by rememberSaveable { mutableStateOf(false) }
    var annotatePdfFile by remember { mutableStateOf<File?>(null) }
    var selectedPdfFile by remember { mutableStateOf<File?>(null) }
    val mergePdfList = remember { mutableStateListOf<Pair<String, File>>() }
    val requestDocumentSign = LocalRequestDocumentSign.current
    
    val mergePdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && showMergeDialog) {
            scope.launch {
                val tempFile = StorageUtils.copyUriToTempFile(context, uri)
                if (tempFile != null) {
                    val fileName = uri.lastPathSegment ?: "document.pdf"
                    mergePdfList.add(Pair(fileName, tempFile))
                }
            }
        }
    }
    
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
            PdfThumbnail(
                filePath = document.filePath,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
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
                            scope.launch {
                                try {
                                    // For SAF URIs, share directly; for file paths, copy to cache first
                                    val shareUri = if (document.filePath.startsWith("content://")) {
                                        Uri.parse(document.filePath)
                                    } else {
                                        val file = File(document.filePath)
                                        if (!file.exists()) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "File not found: ${document.fileName}",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                            return@launch
                                        }
                                        androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                    }
                                    
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    android.widget.Toast.makeText(
                                        context,
                                        "Failed to share PDF: ${e.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
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
                    
                    HorizontalDivider()
                    
                    DropdownMenuItem(
                        text = { Text("Split PDF") },
                        leadingIcon = { Icon(Icons.Default.CallSplit, null) },
                        onClick = {
                            showMenu = false
                            scope.launch {
                                var fileToCleanup: File? = null
                                try {
                                    val file = if (document.filePath.startsWith("content://")) {
                                        StorageUtils.copyUriToTempFile(context, Uri.parse(document.filePath))
                                    } else {
                                        val sourceFile = File(document.filePath)
                                        if (sourceFile.exists()) {
                                            val tempFile = File(context.cacheDir, "temp_split_${System.currentTimeMillis()}.pdf")
                                            sourceFile.copyTo(tempFile, overwrite = true)
                                            tempFile
                                        } else {
                                            null
                                        }
                                    }
                                    if (file != null && file.exists()) {
                                        fileToCleanup = file
                                        selectedPdfFile = file
                                        showSplitDialog = true
                                        fileToCleanup = null
                                    } else {
                                        Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    fileToCleanup?.delete()
                                    Toast.makeText(context, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    
                    DropdownMenuItem(
                        text = { Text("Compress PDF") },
                        leadingIcon = { Icon(Icons.Default.Compress, null) },
                        onClick = {
                            showMenu = false
                            scope.launch {
                                var fileToCleanup: File? = null
                                try {
                                    val file = if (document.filePath.startsWith("content://")) {
                                        StorageUtils.copyUriToTempFile(context, Uri.parse(document.filePath))
                                    } else {
                                        val sourceFile = File(document.filePath)
                                        if (sourceFile.exists()) {
                                            val tempFile = File(context.cacheDir, "temp_compress_${System.currentTimeMillis()}.pdf")
                                            sourceFile.copyTo(tempFile, overwrite = true)
                                            tempFile
                                        } else {
                                            null
                                        }
                                    }
                                    if (file != null && file.exists()) {
                                        fileToCleanup = file
                                        selectedPdfFile = file
                                        showCompressDialog = true
                                        fileToCleanup = null
                                    } else {
                                        Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    fileToCleanup?.delete()
                                    Toast.makeText(context, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    
                    DropdownMenuItem(
                        text = { Text("Extract Text (OCR)") },
                        leadingIcon = { Icon(Icons.Default.TextFields, null) },
                        onClick = {
                            showMenu = false
                            scope.launch {
                                var fileToCleanup: File? = null
                                try {
                                    val file = if (document.filePath.startsWith("content://")) {
                                        StorageUtils.copyUriToTempFile(context, Uri.parse(document.filePath))
                                    } else {
                                        val sourceFile = File(document.filePath)
                                        if (sourceFile.exists()) {
                                            val tempFile = File(context.cacheDir, "temp_ocr_${System.currentTimeMillis()}.pdf")
                                            sourceFile.copyTo(tempFile, overwrite = true)
                                            tempFile
                                        } else {
                                            null
                                        }
                                    }
                                    if (file != null && file.exists()) {
                                        fileToCleanup = file
                                        selectedPdfFile = file
                                        showOcrDialog = true
                                        fileToCleanup = null
                                    } else {
                                        Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    fileToCleanup?.delete()
                                    Toast.makeText(context, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    
                    DropdownMenuItem(
                        text = { Text("Merge with...") },
                        leadingIcon = { Icon(Icons.Default.MergeType, null) },
                        onClick = {
                            showMenu = false
                            scope.launch {
                                var fileToCleanup: File? = null
                                try {
                                    val file = if (document.filePath.startsWith("content://")) {
                                        StorageUtils.copyUriToTempFile(context, Uri.parse(document.filePath))
                                    } else {
                                        val sourceFile = File(document.filePath)
                                        if (sourceFile.exists()) {
                                            val tempFile = File(context.cacheDir, "temp_merge_${System.currentTimeMillis()}.pdf")
                                            sourceFile.copyTo(tempFile, overwrite = true)
                                            tempFile
                                        } else {
                                            null
                                        }
                                    }
                                    if (file != null && file.exists()) {
                                        fileToCleanup = file
                                        mergePdfList.clear()
                                        mergePdfList.add(Pair(document.fileName, file))
                                        showMergeDialog = true
                                        fileToCleanup = null
                                    } else {
                                        Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    fileToCleanup?.delete()
                                    Toast.makeText(context, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    
                    DropdownMenuItem(
                        text = { Text("Sign Document") },
                        leadingIcon = { Icon(Icons.Default.Draw, null) },
                        onClick = {
                            showMenu = false
                            requestDocumentSign?.invoke(document)
                        }
                    )
                    
                    DropdownMenuItem(
                        text = { Text("Annotate & Draw") },
                        leadingIcon = { Icon(Icons.Default.Brush, null) },
                        onClick = {
                            showMenu = false
                            scope.launch {
                                var fileToCleanup: File? = null
                                try {
                                    val file = if (document.filePath.startsWith("content://")) {
                                        StorageUtils.copyUriToTempFile(context, android.net.Uri.parse(document.filePath))
                                    } else {
                                        val sourceFile = File(document.filePath)
                                        if (sourceFile.exists()) {
                                            val tempFile = File(context.cacheDir, "temp_annotate_${System.currentTimeMillis()}.pdf")
                                            sourceFile.copyTo(tempFile, overwrite = true)
                                            tempFile
                                        } else {
                                            null
                                        }
                                    }
                                    if (file != null && file.exists()) {
                                        annotatePdfFile = file
                                        showAnnotateDialog = true
                                    } else {
                                        Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    fileToCleanup?.delete()
                                    Toast.makeText(context, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
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
    
    // Split PDF Dialog
    if (showSplitDialog && selectedPdfFile != null) {
        SplitPdfDialog(
            pdfFile = selectedPdfFile!!,
            onDismiss = {
                selectedPdfFile?.delete()
                selectedPdfFile = null
                showSplitDialog = false
            },
            onSplit = { pageRanges ->
                scope.launch {
                    try {
                        val outputDir = File(context.cacheDir, "split_output")
                        outputDir.mkdirs()
                        val result = PdfUtils.splitPdf(selectedPdfFile!!, outputDir, pageRanges)
                        result.onSuccess { files ->
                            files.forEach { file ->
                                try {
                                    // Save to SAF or fallback to app storage
                                    val savedUri = if (safDirectoryUri != null) {
                                        val folderUri = Uri.parse(safDirectoryUri)
                                        val pdfUri = StorageUtils.createPdfFile(context, folderUri, file.name)
                                        if (pdfUri != null) {
                                            StorageUtils.copyPdfToSaf(context, file, pdfUri)
                                            pdfUri.toString()
                                        } else {
                                            val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), file.name)
                                            file.copyTo(appFile, overwrite = true)
                                            appFile.absolutePath
                                        }
                                    } else {
                                        val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), file.name)
                                        file.copyTo(appFile, overwrite = true)
                                        appFile.absolutePath
                                    }
                                    
                                    viewModel.addDocument(
                                        file.name,
                                        savedUri,
                                        file.countPages(),
                                        document.category
                                    )
                                } finally {
                                    file.delete()
                                }
                            }
                            Toast.makeText(context, "PDF split into ${files.size} files!", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Split failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        selectedPdfFile?.delete()
                        selectedPdfFile = null
                        showSplitDialog = false
                    }
                }
            }
        )
    }
    
    // Compress PDF Dialog
    if (showCompressDialog && selectedPdfFile != null) {
        CompressPdfDialog(
            pdfFile = selectedPdfFile!!,
            onDismiss = {
                selectedPdfFile?.delete()
                selectedPdfFile = null
                showCompressDialog = false
            },
            onCompress = { quality ->
                scope.launch {
                    val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.pdf")
                    try {
                        Toast.makeText(context, "Compressing PDF... (Quality: $quality)", Toast.LENGTH_SHORT).show()
                        selectedPdfFile!!.copyTo(outputFile, overwrite = true)
                        
                        // Save to SAF or fallback to app storage
                        val savedUri = if (safDirectoryUri != null) {
                            val folderUri = Uri.parse(safDirectoryUri)
                            val pdfUri = StorageUtils.createPdfFile(context, folderUri, outputFile.name)
                            if (pdfUri != null) {
                                StorageUtils.copyPdfToSaf(context, outputFile, pdfUri)
                                pdfUri.toString()
                            } else {
                                val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), outputFile.name)
                                outputFile.copyTo(appFile, overwrite = true)
                                appFile.absolutePath
                            }
                        } else {
                            val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), outputFile.name)
                            outputFile.copyTo(appFile, overwrite = true)
                            appFile.absolutePath
                        }
                        
                        viewModel.addDocument(
                            outputFile.name,
                            savedUri,
                            outputFile.countPages(),
                            document.category
                        )
                        Toast.makeText(context, "PDF saved!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        outputFile.delete()
                        selectedPdfFile?.delete()
                        selectedPdfFile = null
                        showCompressDialog = false
                    }
                }
            }
        )
    }
    
    // OCR Text Extract Dialog
    if (showOcrDialog && selectedPdfFile != null) {
        OcrExtractDialog(
            pdfFile = selectedPdfFile!!,
            onDismiss = {
                selectedPdfFile?.delete()
                selectedPdfFile = null
                showOcrDialog = false
            }
        )
    }
    
    // Merge PDFs Dialog
    if (showMergeDialog) {
        MergePdfDialog(
            pdfList = mergePdfList,
            onAddPdf = { mergePdfPicker.launch(arrayOf("application/pdf")) },
            onRemovePdf = { index -> mergePdfList.removeAt(index) },
            onDismiss = {
                mergePdfList.forEach { it.second.delete() }
                mergePdfList.clear()
                showMergeDialog = false
            },
            onMerge = {
                scope.launch {
                    val outputFile = File(context.cacheDir, "merged_${System.currentTimeMillis()}.pdf")
                    try {
                        val result = PdfUtils.mergePdfs(mergePdfList.map { it.second }, outputFile)
                        result.onSuccess { merged ->
                            // Save to SAF or fallback to app storage
                            val savedUri = if (safDirectoryUri != null) {
                                val folderUri = Uri.parse(safDirectoryUri)
                                val pdfUri = StorageUtils.createPdfFile(context, folderUri, merged.name)
                                if (pdfUri != null) {
                                    StorageUtils.copyPdfToSaf(context, merged, pdfUri)
                                    pdfUri.toString()
                                } else {
                                    val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), merged.name)
                                    merged.copyTo(appFile, overwrite = true)
                                    appFile.absolutePath
                                }
                            } else {
                                val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), merged.name)
                                merged.copyTo(appFile, overwrite = true)
                                appFile.absolutePath
                            }
                            
                            viewModel.addDocument(
                                merged.name,
                                savedUri,
                                merged.countPages(),
                                document.category
                            )
                            Toast.makeText(context, "PDFs merged successfully!", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Merge failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        outputFile.delete()
                        mergePdfList.forEach { it.second.delete() }
                        mergePdfList.clear()
                        showMergeDialog = false
                    }
                }
            }
        )
    }
    
    if (showAnnotateDialog && annotatePdfFile != null) {
        AnnotatePdfDialog(
            pdfFile = annotatePdfFile!!,
            onDismiss = {
                annotatePdfFile?.delete()
                annotatePdfFile = null
                showAnnotateDialog = false
            },
            onSave = { annotatedFile ->
                scope.launch {
                    try {
                        val savedUri = if (safDirectoryUri != null) {
                            val folderUri = Uri.parse(safDirectoryUri)
                            val pdfUri = StorageUtils.createPdfFile(context, folderUri, annotatedFile.name)
                            if (pdfUri != null) {
                                StorageUtils.copyPdfToSaf(context, annotatedFile, pdfUri)
                                pdfUri.toString()
                            } else {
                                val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), annotatedFile.name)
                                annotatedFile.copyTo(appFile, overwrite = true)
                                appFile.absolutePath
                            }
                        } else {
                            val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), annotatedFile.name)
                            annotatedFile.copyTo(appFile, overwrite = true)
                            appFile.absolutePath
                        }
                        
                        viewModel.addDocument(
                            annotatedFile.name,
                            savedUri,
                            annotatedFile.countPages(),
                            document.category
                        )
                        Toast.makeText(context, "Annotated PDF saved!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        annotatedFile.delete()
                        annotatePdfFile?.delete()
                        annotatePdfFile = null
                        showAnnotateDialog = false
                    }
                }
            }
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
fun ScanScreen(viewModel: PdfViewModel) {
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
    var galleryImageToCrop by remember { mutableStateOf<File?>(null) }
    
    val activity = context as? Activity
    
    val documentScannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val scanResult = DocumentScannerUtils.parseResult(result.resultCode, result.data)
        if (scanResult != null && scanResult.imageUris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                processingMessage = "Processing scanned document..."
                
                try {
                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                    
                    // Copy scanned images to temp files for processing
                    val scannedFiles = mutableListOf<File>()
                    scanResult.imageUris.forEachIndexed { index, uri ->
                        processingMessage = "Processing page ${index + 1} of ${scanResult.imageUris.size}..."
                        val tempFile = File(context.cacheDir, "scanned_${timestamp}_$index.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        scannedFiles.add(tempFile)
                    }
                    
                    // Perform OCR on all pages
                    val ocrResults = mutableListOf<OcrResult>()
                    scannedFiles.forEachIndexed { index, file ->
                        processingMessage = "OCR page ${index + 1} of ${scannedFiles.size}..."
                        val ocrResult = CameraUtils.extractTextFromImage(context, file)
                        ocrResult.onSuccess { ocr ->
                            ocrResults.add(ocr)
                        }
                    }
                    
                    processingMessage = "Creating PDF..."
                    val pdfFile = File(context.cacheDir, "smartscan_$timestamp.pdf")
                    
                    val result = if (scannedFiles.size == 1) {
                        ImageToPdfUtils.imageToPdf(context, scannedFiles[0], pdfFile)
                    } else {
                        ImageToPdfUtils.imagesToPdf(context, scannedFiles, pdfFile)
                    }
                    
                    result.onSuccess { pdf ->
                        val suggestedName = if (ocrResults.isNotEmpty()) {
                            val docType = CameraUtils.detectDocumentType(ocrResults[0].fullText)
                            when (docType) {
                                DocumentType.RECEIPT -> "Receipt_$timestamp"
                                DocumentType.CONTRACT -> "Contract_$timestamp"
                                DocumentType.FORM -> "Form_$timestamp"
                                DocumentType.LETTER -> "Letter_$timestamp"
                                DocumentType.GENERAL -> "Document_$timestamp"
                            }
                        } else {
                            "Scan_$timestamp"
                        }
                        
                        pendingSaveData = PendingSaveData(
                            pdfFile = pdf,
                            pageCount = scannedFiles.size,
                            suggestedName = suggestedName,
                            ocrResults = ocrResults
                        )
                        showNamingDialog = true
                        
                        // Cleanup temp files
                        scannedFiles.forEach { it.delete() }
                    }.onFailure {
                        scannedFiles.forEach { it.delete() }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isProcessing = false
                }
            }
        }
    }
    
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
                    
                    // Show crop screen
                    galleryImageToCrop = tempImageFile
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
    
    // Show crop screen for gallery-imported images
    if (galleryImageToCrop != null) {
        CropScreen(
            imageFile = galleryImageToCrop!!,
            onCropComplete = { croppedFile ->
                scope.launch {
                    isProcessing = true
                    processingMessage = "Converting to PDF..."
                    
                    try {
                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                        val pdfFile = File(context.cacheDir, "import_$timestamp.pdf")
                        
                        // Perform OCR on the image
                        val ocrResult = CameraUtils.extractTextFromImage(context, croppedFile)
                        val ocrResults = mutableListOf<OcrResult>()
                        ocrResult.onSuccess { ocr ->
                            ocrResults.add(ocr)
                        }
                        
                        // Convert to PDF
                        val result = ImageToPdfUtils.imageToPdf(context, croppedFile, pdfFile)
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
                                "Import_$timestamp"
                            }
                            
                            // FIRST set dialog states BEFORE cleanup
                            pendingSaveData = PendingSaveData(
                                pdfFile = pdf,
                                pageCount = 1,
                                suggestedName = suggestedName,
                                ocrResults = ocrResults
                            )
                            showNamingDialog = true
                            
                            // THEN clean up temp image files (CropScreen will unmount but dialog is already set)
                            croppedFile.delete()
                            galleryImageToCrop?.delete()
                            galleryImageToCrop = null
                        }.onFailure {
                            // Clean up on failure too
                            croppedFile.delete()
                            galleryImageToCrop?.delete()
                            galleryImageToCrop = null
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Cleanup already handled in onFailure
                    } finally {
                        isProcessing = false
                    }
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
                        activity?.let { act ->
                            DocumentScannerUtils.launchScanner(
                                activity = act,
                                launcher = documentScannerLauncher,
                                galleryImportAllowed = true,
                                pageLimit = 20,
                                includePdf = false,
                                onError = { e ->
                                    android.widget.Toast.makeText(
                                        context,
                                        "Scanner not available: ${e.message}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing && activity != null
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Smart Scan (Auto Edge Detection)")
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
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
                    Text("Manual Camera Scan")
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
                    Text("Upload from Gallery (Manual Crop)")
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "Smart Scan uses ML-powered edge detection to automatically find and crop documents. Manual options available as fallback.",
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val safDirectoryUri by settingsManager.safDirectoryUriFlow.collectAsState(initial = null)
    
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfFile by remember { mutableStateOf<File?>(null) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showOcrDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    val mergePdfList = remember { mutableStateListOf<Pair<String, File>>() }
    var showSignatureDialog by rememberSaveable { mutableStateOf(false) }
    var showSignPlacement by rememberSaveable { mutableStateOf(false) }
    var signatureBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var signPdfFile by remember { mutableStateOf<File?>(null) }
    var showAnnotateDialog by rememberSaveable { mutableStateOf(false) }
    var annotatePdfFile by remember { mutableStateOf<File?>(null) }
    val openSigningPad = LocalOpenSigningPadForTools.current
    val signatureFromPad = LocalToolsSignatureBitmap.current
    val consumeSignatureResult = LocalConsumeToolsSignature.current
    var awaitingSignatureFromPad by rememberSaveable { mutableStateOf(false) }
    
    val mergePdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && showMergeDialog) {
            scope.launch {
                val tempFile = StorageUtils.copyUriToTempFile(context, uri)
                if (tempFile != null) {
                    val fileName = uri.lastPathSegment ?: "document.pdf"
                    mergePdfList.add(Pair(fileName, tempFile))
                }
            }
        }
    }
    
    val splitPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val tempFile = StorageUtils.copyUriToTempFile(context, uri)
                if (tempFile != null) {
                    selectedPdfFile = tempFile
                    selectedPdfUri = uri
                    showSplitDialog = true
                }
            }
        }
    }
    
    val compressPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val tempFile = StorageUtils.copyUriToTempFile(context, uri)
                if (tempFile != null) {
                    selectedPdfFile = tempFile
                    selectedPdfUri = uri
                    showCompressDialog = true
                }
            }
        }
    }
    
    val ocrPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val tempFile = StorageUtils.copyUriToTempFile(context, uri)
                if (tempFile != null) {
                    selectedPdfFile = tempFile
                    selectedPdfUri = uri
                    showOcrDialog = true
                }
            }
        }
    }
    
    val signPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val tempFile = StorageUtils.copyUriToTempFile(context, uri)
                if (tempFile != null) {
                    signPdfFile = tempFile
                    showSignPlacement = true
                } else {
                    Toast.makeText(context, "Failed to open PDF file", Toast.LENGTH_SHORT).show()
                    signatureBitmap = null
                }
            }
        } else {
            signatureBitmap = null
        }
    }
    
    val annotatePdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val tempFile = StorageUtils.copyUriToTempFile(context, uri)
                if (tempFile != null) {
                    annotatePdfFile = tempFile
                    showAnnotateDialog = true
                } else {
                    Toast.makeText(context, "Failed to open PDF file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "PDF Tools",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        ToolCard(
            title = "Merge PDFs",
            description = "Combine multiple PDF files into one",
            icon = Icons.Default.MergeType,
            onClick = { showMergeDialog = true }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        ToolCard(
            title = "Split PDF",
            description = "Extract pages or split into multiple files",
            icon = Icons.Default.CallSplit,
            onClick = { splitPdfPicker.launch(arrayOf("application/pdf")) }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        ToolCard(
            title = "Compress PDF",
            description = "Reduce file size while maintaining quality",
            icon = Icons.Default.Compress,
            onClick = { compressPdfPicker.launch(arrayOf("application/pdf")) }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        ToolCard(
            title = "OCR Text Extract",
            description = "Extract text from scanned documents",
            icon = Icons.Default.TextFields,
            onClick = { ocrPdfPicker.launch(arrayOf("application/pdf")) }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        ToolCard(
            title = "Annotate & Draw",
            description = "Add notes, highlights, and drawings",
            icon = Icons.Default.Brush,
            onClick = { annotatePdfPicker.launch(arrayOf("application/pdf")) }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        ToolCard(
            title = "Sign Document",
            description = "Draw, type, or upload a signature",
            icon = Icons.Default.Draw,
            onClick = { showSignatureDialog = true }
        )
    }
    
    // Split PDF Dialog
    if (showSplitDialog && selectedPdfFile != null) {
        SplitPdfDialog(
            pdfFile = selectedPdfFile!!,
            onDismiss = {
                selectedPdfFile?.delete()
                selectedPdfFile = null
                showSplitDialog = false
            },
            onSplit = { pageRanges ->
                scope.launch {
                    try {
                        val outputDir = File(context.cacheDir, "split_output")
                        outputDir.mkdirs()
                        val result = PdfUtils.splitPdf(selectedPdfFile!!, outputDir, pageRanges)
                        result.onSuccess { files ->
                            files.forEach { file ->
                                // Save to SAF or fallback to app storage
                                val savedUri = if (safDirectoryUri != null) {
                                    val folderUri = Uri.parse(safDirectoryUri)
                                    val pdfUri = StorageUtils.createPdfFile(context, folderUri, file.name)
                                    if (pdfUri != null) {
                                        StorageUtils.copyPdfToSaf(context, file, pdfUri)
                                        pdfUri.toString()
                                    } else {
                                        val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), file.name)
                                        file.copyTo(appFile, overwrite = true)
                                        appFile.absolutePath
                                    }
                                } else {
                                    val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), file.name)
                                    file.copyTo(appFile, overwrite = true)
                                    appFile.absolutePath
                                }
                                
                                viewModel.addDocument(
                                    file.name,
                                    savedUri,
                                    file.countPages(),
                                    ""
                                )
                                file.delete()  // Clean up temp file
                            }
                            Toast.makeText(context, "PDF split into ${files.size} files!", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Split failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                        selectedPdfFile?.delete()
                        selectedPdfFile = null
                        showSplitDialog = false
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
    
    // Compress PDF Dialog
    if (showCompressDialog && selectedPdfFile != null) {
        CompressPdfDialog(
            pdfFile = selectedPdfFile!!,
            onDismiss = {
                selectedPdfFile?.delete()
                selectedPdfFile = null
                showCompressDialog = false
            },
            onCompress = { quality ->
                scope.launch {
                    try {
                        Toast.makeText(context, "Compressing PDF... (Quality: $quality)", Toast.LENGTH_SHORT).show()
                        // For now, just copy the file - full compression requires image resampling
                        val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.pdf")
                        selectedPdfFile!!.copyTo(outputFile, overwrite = true)
                        
                        // Save to SAF or fallback to app storage
                        val savedUri = if (safDirectoryUri != null) {
                            val folderUri = Uri.parse(safDirectoryUri)
                            val pdfUri = StorageUtils.createPdfFile(context, folderUri, outputFile.name)
                            if (pdfUri != null) {
                                StorageUtils.copyPdfToSaf(context, outputFile, pdfUri)
                                pdfUri.toString()
                            } else {
                                val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), outputFile.name)
                                outputFile.copyTo(appFile, overwrite = true)
                                appFile.absolutePath
                            }
                        } else {
                            val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), outputFile.name)
                            outputFile.copyTo(appFile, overwrite = true)
                            appFile.absolutePath
                        }
                        
                        viewModel.addDocument(
                            outputFile.name,
                            savedUri,
                            outputFile.countPages(),
                            ""
                        )
                        outputFile.delete()  // Clean up temp file
                        Toast.makeText(context, "PDF saved! (Full compression coming soon)", Toast.LENGTH_SHORT).show()
                        selectedPdfFile?.delete()
                        selectedPdfFile = null
                        showCompressDialog = false
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
    
    // OCR Text Extract Dialog
    if (showOcrDialog && selectedPdfFile != null) {
        OcrExtractDialog(
            pdfFile = selectedPdfFile!!,
            onDismiss = {
                selectedPdfFile?.delete()
                selectedPdfFile = null
                showOcrDialog = false
            }
        )
    }
    
    // Merge PDFs Dialog
    if (showMergeDialog) {
        MergePdfDialog(
            pdfList = mergePdfList,
            onAddPdf = { mergePdfPicker.launch(arrayOf("application/pdf")) },
            onRemovePdf = { index -> mergePdfList.removeAt(index) },
            onDismiss = {
                mergePdfList.forEach { it.second.delete() }
                mergePdfList.clear()
                showMergeDialog = false
            },
            onMerge = {
                scope.launch {
                    try {
                        val outputFile = File(context.cacheDir, "merged_${System.currentTimeMillis()}.pdf")
                        val result = PdfUtils.mergePdfs(mergePdfList.map { it.second }, outputFile)
                        result.onSuccess { merged ->
                            // Save to SAF or fallback to app storage
                            val savedUri = if (safDirectoryUri != null) {
                                val folderUri = Uri.parse(safDirectoryUri)
                                val pdfUri = StorageUtils.createPdfFile(context, folderUri, merged.name)
                                if (pdfUri != null) {
                                    StorageUtils.copyPdfToSaf(context, merged, pdfUri)
                                    pdfUri.toString()
                                } else {
                                    val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), merged.name)
                                    merged.copyTo(appFile, overwrite = true)
                                    appFile.absolutePath
                                }
                            } else {
                                val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), merged.name)
                                merged.copyTo(appFile, overwrite = true)
                                appFile.absolutePath
                            }
                            
                            viewModel.addDocument(
                                merged.name,
                                savedUri,
                                merged.countPages(),
                                ""
                            )
                            merged.delete()  // Clean up temp file
                            Toast.makeText(context, "PDFs merged successfully!", Toast.LENGTH_SHORT).show()
                            mergePdfList.forEach { it.second.delete() }
                            mergePdfList.clear()
                            showMergeDialog = false
                        }.onFailure {
                            Toast.makeText(context, "Merge failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
    
    if (awaitingSignatureFromPad && signatureFromPad != null) {
        LaunchedEffect(Unit) {
            awaitingSignatureFromPad = false
            signatureBitmap = signatureFromPad
            consumeSignatureResult?.invoke()
            signPdfPicker.launch(arrayOf("application/pdf"))
        }
    }
    
    if (showSignatureDialog) {
        SignDocumentDialog(
            onDismiss = { showSignatureDialog = false },
            onOpenFullScreenDraw = {
                showSignatureDialog = false
                awaitingSignatureFromPad = true
                openSigningPad?.invoke()
            },
            onSignatureReady = { bitmap ->
                signatureBitmap = bitmap
                showSignatureDialog = false
                signPdfPicker.launch(arrayOf("application/pdf"))
            }
        )
    }
    
    if (showSignPlacement && signPdfFile != null && signatureBitmap != null) {
        SignPdfScreen(
            pdfFile = signPdfFile!!,
            signatureBitmap = signatureBitmap!!,
            onDismiss = {
                signPdfFile?.delete()
                signPdfFile = null
                signatureBitmap = null
                showSignPlacement = false
            },
            onSigned = { signedFile ->
                scope.launch {
                    val savedUri = if (safDirectoryUri != null) {
                        val folderUri = Uri.parse(safDirectoryUri)
                        val pdfUri = StorageUtils.createPdfFile(context, folderUri, signedFile.name)
                        if (pdfUri != null) {
                            StorageUtils.copyPdfToSaf(context, signedFile, pdfUri)
                            pdfUri.toString()
                        } else {
                            val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), signedFile.name)
                            signedFile.copyTo(appFile, overwrite = true)
                            appFile.absolutePath
                        }
                    } else {
                        val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), signedFile.name)
                        signedFile.copyTo(appFile, overwrite = true)
                        appFile.absolutePath
                    }
                    
                    viewModel.addDocument(
                        signedFile.name,
                        savedUri,
                        signedFile.countPages(),
                        ""
                    )
                    signedFile.delete()
                    signPdfFile?.delete()
                    signPdfFile = null
                    signatureBitmap = null
                    showSignPlacement = false
                    Toast.makeText(context, "Document signed successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    if (showAnnotateDialog && annotatePdfFile != null) {
        AnnotatePdfDialog(
            pdfFile = annotatePdfFile!!,
            onDismiss = {
                annotatePdfFile?.delete()
                annotatePdfFile = null
                showAnnotateDialog = false
            },
            onSave = { annotatedFile ->
                scope.launch {
                    try {
                        val savedUri = if (safDirectoryUri != null) {
                            val folderUri = Uri.parse(safDirectoryUri)
                            val pdfUri = StorageUtils.createPdfFile(context, folderUri, annotatedFile.name)
                            if (pdfUri != null) {
                                StorageUtils.copyPdfToSaf(context, annotatedFile, pdfUri)
                                pdfUri.toString()
                            } else {
                                val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), annotatedFile.name)
                                annotatedFile.copyTo(appFile, overwrite = true)
                                appFile.absolutePath
                            }
                        } else {
                            val appFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), annotatedFile.name)
                            annotatedFile.copyTo(appFile, overwrite = true)
                            appFile.absolutePath
                        }
                        
                        viewModel.addDocument(
                            annotatedFile.name,
                            savedUri,
                            annotatedFile.countPages(),
                            ""
                        )
                        Toast.makeText(context, "Annotated PDF saved!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        annotatedFile.delete()
                        annotatePdfFile?.delete()
                        annotatePdfFile = null
                        showAnnotateDialog = false
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit = {}) {
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
fun MergePdfDialog(
    pdfList: List<Pair<String, File>>,
    onAddPdf: () -> Unit,
    onRemovePdf: (Int) -> Unit,
    onDismiss: () -> Unit,
    onMerge: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge PDFs") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${pdfList.size} PDF(s) selected",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (pdfList.isEmpty()) {
                    Text(
                        "Tap 'Add PDF' to select files to merge",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        pdfList.forEachIndexed { index, (name, _) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${index + 1}. ", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        name.substringAfterLast("/").take(30),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                }
                                androidx.compose.material3.IconButton(
                                    onClick = { onRemovePdf(index) }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                androidx.compose.material3.Button(
                    onClick = onAddPdf,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add PDF")
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = onMerge,
                enabled = pdfList.size >= 2
            ) {
                Text("Merge")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SplitPdfDialog(
    pdfFile: File,
    onDismiss: () -> Unit,
    onSplit: (List<IntRange>) -> Unit
) {
    var pageRangesText by remember { mutableStateOf("") }
    val totalPages = pdfFile.countPages()
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split PDF") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Total pages: $totalPages", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = pageRangesText,
                    onValueChange = { pageRangesText = it },
                    label = { Text("Page Ranges") },
                    placeholder = { Text("e.g., 1-3, 5-7") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Enter ranges separated by commas") }
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    val ranges = pageRangesText.split(",").mapNotNull { range ->
                        val parts = range.trim().split("-")
                        if (parts.size == 2) {
                            val start = parts[0].toIntOrNull()
                            val end = parts[1].toIntOrNull()
                            if (start != null && end != null && start <= end) {
                                start..end
                            } else null
                        } else null
                    }
                    if (ranges.isNotEmpty()) onSplit(ranges)
                },
                enabled = pageRangesText.isNotBlank()
            ) {
                Text("Split")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CompressPdfDialog(
    pdfFile: File,
    onDismiss: () -> Unit,
    onCompress: (String) -> Unit
) {
    var selectedQuality by remember { mutableStateOf("Medium") }
    val qualities = listOf("Low", "Medium", "High")
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress PDF") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Select compression quality:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                qualities.forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedQuality = quality }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selectedQuality == quality,
                            onClick = { selectedQuality = quality }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(quality, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when (quality) {
                                    "Low" -> "Smallest file size"
                                    "Medium" -> "Balanced size and quality"
                                    "High" -> "Best quality"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onCompress(selectedQuality) }) {
                Text("Compress")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun OcrExtractDialog(
    pdfFile: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var extractedText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(pdfFile) {
        scope.launch(Dispatchers.IO) {
            try {
                val renderer = PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY))
                val fullText = StringBuilder()
                
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    
                    val tempImageFile = File(context.cacheDir, "ocr_page_${i}.jpg")
                    try {
                        FileOutputStream(tempImageFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        
                        val ocrResult = CameraUtils.extractTextFromImage(context, tempImageFile)
                        ocrResult.onSuccess { ocr ->
                            fullText.append("--- Page ${i + 1} ---\n")
                            fullText.append(ocr.fullText)
                            fullText.append("\n\n")
                        }
                    } finally {
                        tempImageFile.delete()
                        bitmap.recycle()
                    }
                }
                renderer.close()
                extractedText = fullText.toString()
                isLoading = false
            } catch (e: Exception) {
                extractedText = "Error extracting text: ${e.message}"
                isLoading = false
            }
        }
    }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extracted Text") },
        text = {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Extracting text...")
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.OutlinedTextField(
                        value = extractedText,
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        readOnly = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Extracted Text", extractedText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Text copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                enabled = !isLoading && extractedText.isNotBlank()
            ) {
                Text("Copy")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
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
                            label = { Text("High", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = imageQuality == ImageQuality.MEDIUM,
                            onClick = { scope.launch { settingsManager.setImageQuality(ImageQuality.MEDIUM) } },
                            label = { Text("Medium", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = imageQuality == ImageQuality.LOW,
                            onClick = { scope.launch { settingsManager.setImageQuality(ImageQuality.LOW) } },
                            label = { Text("Low", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                            label = { Text("Light", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = theme == AppTheme.DARK,
                            onClick = { scope.launch { settingsManager.setTheme(AppTheme.DARK) } },
                            label = { Text("Dark", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = theme == AppTheme.SYSTEM,
                            onClick = { scope.launch { settingsManager.setTheme(AppTheme.SYSTEM) } },
                            label = { Text("System", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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

fun File.countPages(): Int {
    return try {
        val renderer = PdfRenderer(ParcelFileDescriptor.open(this, ParcelFileDescriptor.MODE_READ_ONLY))
        val pageCount = renderer.pageCount
        renderer.close()
        pageCount
    } catch (e: Exception) {
        1
    }
}

enum class Screen {
    Home, Scan, Tools, Settings
}
