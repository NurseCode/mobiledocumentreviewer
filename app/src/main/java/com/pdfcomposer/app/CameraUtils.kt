package com.pdfcomposer.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object CameraUtils {
    
    /**
     * Capture image with camera and save to file
     */
    suspend fun captureImage(
        context: Context,
        imageCapture: ImageCapture,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        suspendCoroutine { continuation ->
            val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
            
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        continuation.resume(Result.success(outputFile))
                    }
                    
                    override fun onError(exception: ImageCaptureException) {
                        continuation.resume(Result.failure(exception))
                    }
                }
            )
        }
    }
    
    /**
     * Normalize EXIF orientation by physically rotating the image
     * This ensures the image displays correctly in all apps/viewers
     * Call this immediately after capture before showing preview
     */
    suspend fun normalizeImageOrientation(file: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            // Only process if rotation is needed
            if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
                return@withContext Result.success(file)
            }
            
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            
            if (rotationDegrees == 0f) {
                return@withContext Result.success(file)
            }
            
            // Load and rotate bitmap
            var bitmap = BitmapFactory.decodeFile(file.absolutePath)
            val matrix = Matrix().apply {
                postRotate(rotationDegrees)
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            
            // Save back to same file
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            
            // Reset EXIF orientation to normal
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            exif.saveAttributes()
            
            bitmap.recycle()
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Optimize image for PDF conversion
     * - Resize to max 1920x1920 for file size
     * - Compress to balance quality and size
     * - Convert to grayscale for documents (optional)
     */
    suspend fun optimizeImageForPdf(
        sourceFile: File,
        outputFile: File,
        maxDimension: Int = 1920,
        quality: Int = 85,
        grayscale: Boolean = false
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            var bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
            
            // Fix orientation based on EXIF data
            val exif = ExifInterface(sourceFile.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            
            // Apply rotation if needed
            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply {
                    postRotate(rotationDegrees)
                }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
            
            // Resize if needed
            if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                val matrix = Matrix().apply {
                    postScale(scale, scale)
                }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
            
            // Convert to grayscale for document scans (optional)
            if (grayscale) {
                bitmap = toGrayscale(bitmap)
            }
            
            // Compress and save
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            
            bitmap.recycle()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Convert bitmap to grayscale for cleaner document scans
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val grayscaleBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(grayscaleBitmap)
        val paint = android.graphics.Paint()
        val colorMatrix = android.graphics.ColorMatrix()
        colorMatrix.setSaturation(0f)
        val colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = colorFilter
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayscaleBitmap
    }
    
    /**
     * Perform OCR on image using ML Kit
     */
    suspend fun extractTextFromImage(context: Context, imageFile: File): Result<OcrResult> = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            
            val result = suspendCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(visionText)
                    }
                    .addOnFailureListener { e ->
                        continuation.resumeWithException(e)
                    }
            }
            
            val extractedText = result.text
            val confidence = 0.95f // ML Kit doesn't provide confidence scores
            val blocks = result.textBlocks.map { block ->
                TextBlock(
                    text = block.text,
                    confidence = 0.95f, // ML Kit doesn't provide per-block confidence
                    boundingBox = block.boundingBox
                )
            }
            
            Result.success(OcrResult(extractedText, confidence, blocks))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    
    /**
     * Detect document type from OCR text
     */
    fun detectDocumentType(ocrText: String): DocumentType {
        val lowerText = ocrText.lowercase()
        
        return when {
            isReceipt(lowerText) -> DocumentType.RECEIPT
            isContract(lowerText) -> DocumentType.CONTRACT
            isForm(lowerText) -> DocumentType.FORM
            isLetter(lowerText) -> DocumentType.LETTER
            else -> DocumentType.GENERAL
        }
    }
    
    private fun isReceipt(text: String): Boolean {
        val receiptKeywords = listOf(
            "total", "subtotal", "tax", "receipt", "purchase",
            "qty", "quantity", "price", "amount", "paid",
            "cash", "credit", "debit", "$", "payment"
        )
        val matches = receiptKeywords.count { text.contains(it) }
        return matches >= 3
    }
    
    private fun isContract(text: String): Boolean {
        val contractKeywords = listOf(
            "agreement", "contract", "party", "parties",
            "hereby", "whereas", "terms", "conditions",
            "signature", "dated", "effective"
        )
        val matches = contractKeywords.count { text.contains(it) }
        return matches >= 3
    }
    
    private fun isForm(text: String): Boolean {
        val formKeywords = listOf(
            "name:", "address:", "date:", "phone:",
            "email:", "signature:", "please fill",
            "application", "form", "checkbox"
        )
        val matches = formKeywords.count { text.contains(it) }
        return matches >= 3
    }
    
    private fun isLetter(text: String): Boolean {
        val letterKeywords = listOf(
            "dear", "sincerely", "regards", "yours",
            "re:", "subject:", "attention:", "from:"
        )
        val matches = letterKeywords.count { text.contains(it) }
        return matches >= 2
    }
    
    /**
     * Assess scan quality based on image properties
     */
    fun assessScanQuality(bitmap: Bitmap): ScanQuality {
        val resolution = bitmap.width * bitmap.height
        val aspectRatio = bitmap.width.toFloat() / bitmap.height
        
        // Check resolution
        val hasGoodResolution = resolution >= 1_000_000 // At least 1MP
        
        // Check if aspect ratio is reasonable (not too skewed)
        val hasGoodAspectRatio = aspectRatio in 0.5f..2.0f
        
        // Check brightness (simple average of pixel values)
        val brightness = calculateBrightness(bitmap)
        val hasGoodBrightness = brightness in 80..200
        
        return when {
            hasGoodResolution && hasGoodAspectRatio && hasGoodBrightness -> ScanQuality.EXCELLENT
            hasGoodResolution && hasGoodAspectRatio -> ScanQuality.GOOD
            hasGoodResolution || hasGoodAspectRatio -> ScanQuality.FAIR
            else -> ScanQuality.POOR
        }
    }
    
    private fun calculateBrightness(bitmap: Bitmap): Int {
        // Sample 100 random pixels to estimate brightness
        var totalBrightness = 0
        val sampleSize = 100
        
        repeat(sampleSize) {
            val x = (Math.random() * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val y = (Math.random() * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            totalBrightness += (r + g + b) / 3
        }
        
        return totalBrightness / sampleSize
    }
}

/**
 * OCR result with extracted text and metadata
 */
data class OcrResult(
    val fullText: String,
    val confidence: Float,
    val textBlocks: List<TextBlock>
)

data class TextBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: android.graphics.Rect?
)

enum class DocumentType {
    RECEIPT,    // Has $ amounts, merchant, total
    CONTRACT,   // Legal language, parties, terms
    FORM,       // Field labels, checkboxes
    LETTER,     // Greeting, signature
    GENERAL     // Unknown/mixed
}

enum class ScanQuality {
    EXCELLENT,  // High resolution, good lighting
    GOOD,       // Acceptable quality for OCR
    FAIR,       // May have OCR issues
    POOR        // Needs to be rescanned
}
