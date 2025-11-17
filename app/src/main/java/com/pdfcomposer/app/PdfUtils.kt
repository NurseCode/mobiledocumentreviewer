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
    PRIVATE,    // App-private storage (/data/data/...) - deleted on uninstall
    SAF,        // User-chosen folder via SAF - persists after uninstall
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
        var document: PDDocument? = null
        try {
            document = PDDocument.load(pdfFile)
            
            if (pageNumber < 1 || pageNumber > document.numberOfPages) {
                return@withContext Result.failure(
                    IllegalArgumentException("Page $pageNumber out of range (1-${document.numberOfPages})")
                )
            }
            
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
            document = null
            
            if (outputFile.exists()) {
                pdfFile.delete()
                outputFile.renameTo(pdfFile)
            }
            
            Result.success(pdfFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
    
    suspend fun getBookmarksFromPdf(pdfFile: File): List<PdfBookmark> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        try {
            document = PDDocument.load(pdfFile)
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
            
            bookmarks
        } catch (e: Exception) {
            emptyList()
        } finally {
            document?.close()
        }
    }
    
    fun getPdfStorageDir(context: Context, location: StorageLocation): File {
        return when (location) {
            StorageLocation.PRIVATE -> {
                // App-private storage - deleted when app is uninstalled
                File(context.filesDir, "pdfs").apply { mkdirs() }
            }
            StorageLocation.SAF -> {
                // For SAF, we use temp storage then copy to user-chosen location
                // The actual persistent location is managed via StorageUtils
                File(context.cacheDir, "saf_temp").apply { mkdirs() }
            }
            StorageLocation.SHARE -> {
                // Temporary storage for immediate sharing
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
    
    private fun cloneBookmarkRecursive(
        source: PDOutlineItem,
        sourceDoc: PDDocument,
        targetDoc: PDDocument,
        pageOffset: Int
    ): PDOutlineItem? {
        val cloned = PDOutlineItem()
        cloned.title = source.title ?: "Bookmark"
        
        // Handle destination
        val sourceDest = source.destination
        if (sourceDest is PDPageDestination) {
            val sourcePageIndex = sourceDoc.pages.indexOf(sourceDest.page)
            if (sourcePageIndex >= 0 && pageOffset + sourcePageIndex < targetDoc.numberOfPages) {
                val newDest = PDPageFitWidthDestination()
                newDest.page = targetDoc.getPage(pageOffset + sourcePageIndex)
                cloned.destination = newDest
            } else {
                // Invalid page reference, skip this bookmark
                return null
            }
        } else if (source.action != null) {
            // Copy action-based bookmarks as-is (may need adjustment for GoTo actions)
            cloned.action = source.action
        }
        
        // Recursively clone children
        var child = source.firstChild
        while (child != null) {
            val clonedChild = cloneBookmarkRecursive(child, sourceDoc, targetDoc, pageOffset)
            if (clonedChild != null) {
                cloned.addLast(clonedChild)
            }
            child = child.nextSibling
        }
        
        return cloned
    }
    
    suspend fun mergePdfs(pdfFiles: List<File>, outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        var mergedDocument: PDDocument? = null
        val openDocuments = mutableListOf<PDDocument>()
        
        try {
            mergedDocument = PDDocument()
            var mergedOutline: PDDocumentOutline? = null
            var pageOffset = 0
            
            for (pdfFile in pdfFiles) {
                val document = PDDocument.load(pdfFile)
                openDocuments.add(document)
                
                // Copy pages
                for (i in 0 until document.numberOfPages) {
                    mergedDocument.addPage(document.getPage(i))
                }
                
                // Copy bookmarks with updated page references (recursive)
                val sourceOutline = document.documentCatalog.documentOutline
                if (sourceOutline != null) {
                    if (mergedOutline == null) {
                        mergedOutline = PDDocumentOutline()
                        mergedDocument.documentCatalog.documentOutline = mergedOutline
                    }
                    
                    // Recursively clone bookmark tree
                    var currentBookmark = sourceOutline.firstChild
                    while (currentBookmark != null) {
                        val clonedBookmark = cloneBookmarkRecursive(
                            currentBookmark,
                            document,
                            mergedDocument,
                            pageOffset
                        )
                        if (clonedBookmark != null) {
                            mergedOutline.addLast(clonedBookmark)
                        }
                        currentBookmark = currentBookmark.nextSibling
                    }
                }
                
                pageOffset += document.numberOfPages
            }
            
            mergedDocument.save(outputFile)
            
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            openDocuments.forEach { it.close() }
            mergedDocument?.close()
        }
    }
    
    suspend fun splitPdf(
        pdfFile: File,
        outputDir: File,
        pageRanges: List<IntRange>
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        var sourceDocument: PDDocument? = null
        val outputDocuments = mutableListOf<PDDocument>()
        
        try {
            sourceDocument = PDDocument.load(pdfFile)
            val outputFiles = mutableListOf<File>()
            
            pageRanges.forEachIndexed { index, range ->
                val newDocument = PDDocument()
                outputDocuments.add(newDocument)
                
                for (pageNum in range) {
                    if (pageNum >= 1 && pageNum <= sourceDocument.numberOfPages) {
                        newDocument.addPage(sourceDocument.getPage(pageNum - 1))
                    }
                }
                
                val outputFile = File(
                    outputDir,
                    "${pdfFile.nameWithoutExtension}_part${index + 1}.pdf"
                )
                newDocument.save(outputFile)
                outputFiles.add(outputFile)
            }
            
            Result.success(outputFiles)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            outputDocuments.forEach { it.close() }
            sourceDocument?.close()
        }
    }
}

data class PdfBookmark(
    val title: String,
    val pageNumber: Int
)
