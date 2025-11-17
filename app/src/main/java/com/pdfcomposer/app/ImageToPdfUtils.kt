package com.pdfcomposer.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ImageToPdfUtils {
    
    /**
     * Initialize PDFBox for Android
     */
    fun initialize(context: Context) {
        PDFBoxResourceLoader.init(context)
    }
    
    /**
     * Convert a single image to PDF
     */
    suspend fun imageToPdf(
        context: Context,
        imageFile: File,
        outputPdfFile: File,
        pageSize: PDRectangle = PDRectangle.A4,
        compress: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        try {
            document = PDDocument()
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            
            // Create page with appropriate size
            val page = PDPage(calculatePageSize(bitmap, pageSize))
            document.addPage(page)
            
            // Add image to page
            val contentStream = PDPageContentStream(document, page)
            val pdImage = if (compress) {
                JPEGFactory.createFromImage(document, bitmap)
            } else {
                LosslessFactory.createFromImage(document, bitmap)
            }
            
            // Scale image to fit page
            val scale = calculateScale(bitmap, page.mediaBox)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            
            // Center image on page
            val x = (page.mediaBox.width - width) / 2
            val y = (page.mediaBox.height - height) / 2
            
            contentStream.drawImage(pdImage, x, y, width, height)
            contentStream.close()
            
            bitmap.recycle()
            
            // Save PDF
            document.save(outputPdfFile)
            Result.success(outputPdfFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
    
    /**
     * Convert multiple images to a single multi-page PDF
     */
    suspend fun imagesToPdf(
        context: Context,
        imageFiles: List<File>,
        outputPdfFile: File,
        pageSize: PDRectangle = PDRectangle.A4,
        compress: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        try {
            document = PDDocument()
            
            for (imageFile in imageFiles) {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                
                // Create page
                val page = PDPage(calculatePageSize(bitmap, pageSize))
                document.addPage(page)
                
                // Add image to page
                val contentStream = PDPageContentStream(document, page)
                val pdImage = if (compress) {
                    JPEGFactory.createFromImage(document, bitmap)
                } else {
                    LosslessFactory.createFromImage(document, bitmap)
                }
                
                // Scale and center image
                val scale = calculateScale(bitmap, page.mediaBox)
                val width = bitmap.width * scale
                val height = bitmap.height * scale
                val x = (page.mediaBox.width - width) / 2
                val y = (page.mediaBox.height - height) / 2
                
                contentStream.drawImage(pdImage, x, y, width, height)
                contentStream.close()
                
                bitmap.recycle()
            }
            
            // Save PDF
            document.save(outputPdfFile)
            Result.success(outputPdfFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
    
    /**
     * Calculate optimal page size based on image aspect ratio
     */
    private fun calculatePageSize(bitmap: Bitmap, defaultSize: PDRectangle): PDRectangle {
        val imageAspect = bitmap.width.toFloat() / bitmap.height
        val pageAspect = defaultSize.width / defaultSize.height
        
        // Use portrait or landscape based on image orientation
        return if (imageAspect > 1.0f && pageAspect < 1.0f) {
            // Image is landscape, rotate page to landscape
            PDRectangle(defaultSize.height, defaultSize.width)
        } else if (imageAspect < 1.0f && pageAspect > 1.0f) {
            // Image is portrait, rotate page to portrait
            PDRectangle(defaultSize.height, defaultSize.width)
        } else {
            defaultSize
        }
    }
    
    /**
     * Calculate scale to fit image on page while maintaining aspect ratio
     */
    private fun calculateScale(bitmap: Bitmap, pageBox: PDRectangle): Float {
        val widthScale = pageBox.width / bitmap.width
        val heightScale = pageBox.height / bitmap.height
        
        // Use smaller scale to ensure image fits on page
        return minOf(widthScale, heightScale) * 0.95f // 95% to add small margin
    }
    
    /**
     * Add OCR text layer to PDF (for searchable PDFs)
     * This makes the PDF searchable even though it's image-based
     */
    suspend fun addOcrTextLayer(
        context: Context,
        pdfFile: File,
        ocrResults: List<OcrResult>,
        imageFiles: List<File>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        try {
            document = PDDocument.load(pdfFile)
            
            // Add invisible text layer to each page
            for ((pageIndex, ocrResult) in ocrResults.withIndex()) {
                if (pageIndex >= document.numberOfPages) break
                
                val page = document.getPage(pageIndex)
                val contentStream = PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )
                
                // Set font for text (required before showText)
                contentStream.setFont(
                    com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA,
                    12f
                )
                
                // Get image dimensions for coordinate conversion
                val imageBitmap = if (pageIndex < imageFiles.size) {
                    BitmapFactory.decodeFile(imageFiles[pageIndex].absolutePath)
                } else null
                
                // Add text blocks at their detected positions
                for (textBlock in ocrResult.textBlocks) {
                    if (textBlock.boundingBox != null && imageBitmap != null) {
                        // Convert bitmap coordinates to PDF coordinates
                        val pageBox = page.mediaBox
                        val scale = calculateScale(imageBitmap, pageBox)
                        
                        // Calculate margins from centering the image (same as in imageToPdf)
                        val marginX = (pageBox.width - imageBitmap.width * scale) / 2
                        val marginY = (pageBox.height - imageBitmap.height * scale) / 2
                        
                        // PDF coordinates start at bottom-left, image at top-left
                        // Position text baseline at bottom of bounding box
                        val pdfX = (textBlock.boundingBox.left * scale) + marginX
                        val pdfY = marginY + (imageBitmap.height - textBlock.boundingBox.bottom) * scale
                        
                        // Make text invisible (render mode 3 = invisible)
                        // Note: PDFBox Android doesn't have setTextRenderingMode,
                        // so we set text color to transparent instead
                        contentStream.beginText()
                        contentStream.setNonStrokingColor(255, 255, 255) // White/transparent
                        contentStream.newLineAtOffset(pdfX, pdfY)
                        contentStream.showText(textBlock.text)
                        contentStream.endText()
                    }
                }
                
                imageBitmap?.recycle()
                contentStream.close()
            }
            
            document.save(outputFile)
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
}

/**
 * PDF creation options
 */
data class PdfCreationOptions(
    val pageSize: PDRectangle = PDRectangle.A4,
    val compress: Boolean = true,
    val addOcrTextLayer: Boolean = false,
    val grayscale: Boolean = false,
    val quality: Int = 85
)
