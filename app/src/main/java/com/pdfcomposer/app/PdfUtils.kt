package com.pdfcomposer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class StorageLocation {
    PRIVATE,    // App-private storage (/data/data/...)
    PUBLIC,     // Public Documents folder
    SHARE       // Share immediately after saving
}

object PdfUtils {
    
    fun initializePdfBox(context: Context) {
        PDFBoxResourceLoader.init(context)
    }
    
    suspend fun addBookmarkToPdf(
        pdfFile: File,
        pageNumber: Int,
        bookmarkTitle: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val document = PDDocument.load(pdfFile)
            
            var outline = document.documentCatalog.documentOutline
            if (outline == null) {
                outline = PDDocumentOutline()
                document.documentCatalog.documentOutline = outline
            }
            
            val bookmark = PDOutlineItem()
            bookmark.title = bookmarkTitle
            
            val destination = PDPageFitWidthDestination()
            destination.page = document.getPage(pageNumber - 1)
            bookmark.destination = destination
            
            outline.addLast(bookmark)
            
            val outputFile = File(pdfFile.parent, "${pdfFile.nameWithoutExtension}_bookmarked.pdf")
            document.save(outputFile)
            document.close()
            
            if (outputFile.exists()) {
                pdfFile.delete()
                outputFile.renameTo(pdfFile)
            }
            
            Result.success(pdfFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getBookmarksFromPdf(pdfFile: File): List<PdfBookmark> = withContext(Dispatchers.IO) {
        try {
            val document = PDDocument.load(pdfFile)
            val outline = document.documentCatalog.documentOutline
            val bookmarks = mutableListOf<PdfBookmark>()
            
            if (outline != null) {
                var current = outline.firstChild
                while (current != null) {
                    val dest = current.destination
                    if (dest is PDPageDestination) {
                        val pageNum = document.pages.indexOf(dest.page) + 1
                        bookmarks.add(PdfBookmark(current.title ?: "Bookmark", pageNum))
                    }
                    current = current.nextSibling
                }
            }
            
            document.close()
            bookmarks
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getPdfStorageDir(context: Context, location: StorageLocation): File {
        return when (location) {
            StorageLocation.PRIVATE -> {
                File(context.filesDir, "pdfs").apply { mkdirs() }
            }
            StorageLocation.PUBLIC -> {
                val documentsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS
                )
                File(documentsDir, "QuickPDFComposer").apply { mkdirs() }
            }
            StorageLocation.SHARE -> {
                File(context.cacheDir, "pdfs").apply { mkdirs() }
            }
        }
    }
    
    fun sharePdf(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, pdfFile.nameWithoutExtension)
            putExtra(
                Intent.EXTRA_TEXT,
                "Sharing PDF document: ${pdfFile.nameWithoutExtension}"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(
            Intent.createChooser(shareIntent, "Share PDF via")
        )
    }
    
    fun openPdfInViewer(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        context.startActivity(
            Intent.createChooser(intent, "Open PDF with")
        )
    }
    
    suspend fun mergePdfs(pdfFiles: List<File>, outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val mergedDocument = PDDocument()
            
            for (pdfFile in pdfFiles) {
                val document = PDDocument.load(pdfFile)
                for (i in 0 until document.numberOfPages) {
                    mergedDocument.addPage(document.getPage(i))
                }
                document.close()
            }
            
            mergedDocument.save(outputFile)
            mergedDocument.close()
            
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun splitPdf(
        pdfFile: File,
        outputDir: File,
        pageRanges: List<IntRange>
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        try {
            val document = PDDocument.load(pdfFile)
            val outputFiles = mutableListOf<File>()
            
            pageRanges.forEachIndexed { index, range ->
                val newDocument = PDDocument()
                for (pageNum in range) {
                    if (pageNum - 1 < document.numberOfPages) {
                        newDocument.addPage(document.getPage(pageNum - 1))
                    }
                }
                
                val outputFile = File(
                    outputDir,
                    "${pdfFile.nameWithoutExtension}_part${index + 1}.pdf"
                )
                newDocument.save(outputFile)
                newDocument.close()
                outputFiles.add(outputFile)
            }
            
            document.close()
            Result.success(outputFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class PdfBookmark(
    val title: String,
    val pageNumber: Int
)
