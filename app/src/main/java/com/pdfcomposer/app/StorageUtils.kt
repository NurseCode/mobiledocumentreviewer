package com.pdfcomposer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object StorageUtils {
    
    /**
     * Creates an intent to pick a folder where PDFs will be saved persistently.
     * User only needs to do this once - the URI is saved and reused.
     */
    fun createFolderPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
    }
    
    /**
     * Creates an intent to open a PDF file from anywhere
     * (local storage, Dropbox, Drive, OneDrive, etc.)
     */
    fun createPdfPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }
    }
    
    /**
     * Creates an intent to pick multiple PDF files for merging
     */
    fun createMultiplePdfPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
    }
    
    /**
     * DEPRECATED: Use SettingsManager instead
     * This function is kept for backwards compatibility but does nothing
     */
    @Deprecated("Use SettingsManager.setSafDirectoryUri instead")
    fun saveStorageFolderUri(context: Context, uri: Uri) {
        // No-op - use SettingsManager instead
    }
    
    /**
     * DEPRECATED: Use SettingsManager instead
     * This function is kept for backwards compatibility
     */
    @Deprecated("Use SettingsManager.safDirectoryUriFlow instead")
    fun getStorageFolderUri(context: Context): Uri? {
        return null  // Always return null to force using SettingsManager
    }
    
    /**
     * Create a new PDF file in the chosen storage location
     */
    suspend fun createPdfFile(
        context: Context,
        folderUri: Uri,
        fileName: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext null
            
            val file = folder.createFile("application/pdf", fileName)
            file?.uri
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Copy a PDF file from local storage to SAF location
     */
    suspend fun copyPdfToSaf(
        context: Context,
        sourceFile: File,
        destinationUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Copy a PDF from SAF URI to local temporary file for processing
     */
    suspend fun copyPdfFromSaf(
        context: Context,
        sourceUri: Uri,
        destinationFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Copy URI (content:// or file://) to a temporary file
     * Used for tools that need to process selected PDFs
     */
    suspend fun copyUriToTempFile(
        context: Context,
        uri: Uri
    ): File? = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileName(context, uri)
            val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}_$fileName")
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get file name from content URI
     */
    fun getFileName(context: Context, uri: Uri): String {
        var fileName = "document.pdf"
        
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        
        return fileName
    }
    
    /**
     * Check if we have persistent access to the storage folder
     * Note: This is a synchronous function - for Flow-based access, use SettingsManager.safDirectoryUriFlow
     */
    suspend fun hasStorageAccess(context: Context): Boolean = withContext(Dispatchers.IO) {
        val settingsManager = SettingsManager(context)
        val uriString = settingsManager.getSafDirectoryUri() ?: return@withContext false
        val uri = Uri.parse(uriString)
        
        return@withContext try {
            val folder = DocumentFile.fromTreeUri(context, uri)
            folder != null && folder.exists() && folder.canWrite()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Delete all cached/temp PDF files
     */
    suspend fun clearTempFiles(context: Context) = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "pdfs")
            if (tempDir.exists()) {
                tempDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}
