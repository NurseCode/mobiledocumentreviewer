package com.pdfcomposer.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

data class DocumentScanResult(
    val imageUris: List<Uri>,
    val pdfUri: Uri?,
    val pageCount: Int
)

object DocumentScannerUtils {
    
    private var scanner: GmsDocumentScanner? = null
    
    fun getScanner(
        galleryImportAllowed: Boolean = true,
        pageLimit: Int = 20,
        includePdf: Boolean = true
    ): GmsDocumentScanner {
        val optionsBuilder = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(galleryImportAllowed)
            .setPageLimit(pageLimit)
            .setScannerMode(SCANNER_MODE_FULL)
        
        if (includePdf) {
            optionsBuilder.setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
        } else {
            optionsBuilder.setResultFormats(RESULT_FORMAT_JPEG)
        }
        
        val options = optionsBuilder.build()
        scanner = GmsDocumentScanning.getClient(options)
        return scanner!!
    }
    
    fun launchScanner(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        galleryImportAllowed: Boolean = true,
        pageLimit: Int = 20,
        includePdf: Boolean = true,
        onError: (Exception) -> Unit = {}
    ) {
        val documentScanner = getScanner(galleryImportAllowed, pageLimit, includePdf)
        
        documentScanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }
    
    fun parseResult(resultCode: Int, data: Intent?): DocumentScanResult? {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return null
        }
        
        val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(data)
        if (scanningResult == null) {
            return null
        }
        
        val imageUris = scanningResult.pages?.map { it.imageUri } ?: emptyList()
        val pdfUri = scanningResult.pdf?.uri
        val pageCount = scanningResult.pages?.size ?: 0
        
        return DocumentScanResult(
            imageUris = imageUris,
            pdfUri = pdfUri,
            pageCount = pageCount
        )
    }
}
