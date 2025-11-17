package com.pdfcomposer.app

import android.Manifest
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
data class PdfDocument(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val filePath: String,
    val pageCount: Int,
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
    fun getAllDocuments(): Flow<List<PdfDocument>>
    
    @Insert
    suspend fun insert(document: PdfDocument)
    
    @Delete
    suspend fun delete(document: PdfDocument)
    
    @Query("SELECT COUNT(*) FROM pdf_documents")
    suspend fun getCount(): Int
}

@Database(entities = [Bookmark::class, PdfDocument::class], version = 1, exportSchema = false)
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
                ).build()
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
    
    val allDocuments: Flow<List<PdfDocument>> = pdfDocumentDao.getAllDocuments()
    
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
    
    fun addDocument(fileName: String, filePath: String, pageCount: Int) {
        viewModelScope.launch {
            pdfDocumentDao.insert(PdfDocument(fileName = fileName, filePath = filePath, pageCount = pageCount))
        }
    }
    
    fun deleteDocument(document: PdfDocument) {
        viewModelScope.launch {
            pdfDocumentDao.delete(document)
            bookmarkDao.deleteAllForPdf(document.filePath)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        primary = Color(0xFF6750A4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF21005D),
        secondary = Color(0xFF625B71),
        onSecondary = Color.White,
        background = Color(0xFFFFFBFE),
        surface = Color(0xFFFFFBFE),
        onSurface = Color(0xFF1C1B1F)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: PdfViewModel) {
    var selectedScreen by remember { mutableStateOf(Screen.Home) }
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600
    
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
                        label = { Text("Home") },
                        selected = selectedScreen == Screen.Home,
                        onClick = { selectedScreen = Screen.Home }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.CameraAlt, "Scan") },
                        label = { Text("Scan") },
                        selected = selectedScreen == Screen.Scan,
                        onClick = { selectedScreen = Screen.Scan }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Edit, "Tools") },
                        label = { Text("Tools") },
                        selected = selectedScreen == Screen.Tools,
                        onClick = { selectedScreen = Screen.Tools }
                    )
                </NavigationBar>
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
                }
                Box(modifier = Modifier.weight(1f)) {
                    ScreenContent(selectedScreen, viewModel)
                }
            }
        } else {
            Box(modifier = Modifier.padding(padding)) {
                ScreenContent(selectedScreen, viewModel)
            }
        }
    }
}

@Composable
fun ScreenContent(screen: Screen, viewModel: PdfViewModel) {
    when (screen) {
        Screen.Home -> HomeScreen(viewModel)
        Screen.Scan -> ScanScreen(viewModel)
        Screen.Tools -> ToolsScreen(viewModel)
    }
}

@Composable
fun HomeScreen(viewModel: PdfViewModel) {
    val documents by viewModel.allDocuments.collectAsState(initial = emptyList())
    
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
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(documents) { doc ->
                    DocumentCard(doc, viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentCard(document: PdfDocument, viewModel: PdfViewModel) {
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
                Text(
                    text = "${document.pageCount} pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { viewModel.deleteDocument(document) }) {
                Icon(Icons.Default.Delete, "Delete")
            }
        }
    }
}

@Composable
fun ScanScreen(viewModel: PdfViewModel) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Handle image to PDF conversion
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
                    // Launch camera
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan New Document")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Existing PDF")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = { imagePickerLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
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

enum class Screen {
    Home, Scan, Tools
}
