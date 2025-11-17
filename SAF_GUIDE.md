# Storage Access Framework (SAF) Implementation Guide

## Overview
The app now uses Android's **Storage Access Framework (SAF)** for flexible, persistent file storage that works seamlessly with cloud storage providers.

## Key Benefits

### ✅ **Files Persist After App Uninstall**
- User picks a folder (e.g., Documents/MyPDFs/)
- Files saved there survive even if app is deleted
- True ownership - files belong to the user, not the app

### ✅ **Open PDFs from Anywhere**
- Local storage
- Dropbox (if installed)
- Google Drive  
- OneDrive
- Any storage provider on the device

### ✅ **No Cloud API Integration Needed**
- Android handles all cloud providers automatically
- No API keys, OAuth, or SDK integration required
- User's existing cloud apps "just work"

---

## How It Works

### **Opening Existing PDFs**

User wants to add bookmarks to a PDF from Dropbox:

1. **User taps "Open PDF"**
2. **Android file picker appears** showing:
   - Recent files
   - Downloads folder
   - Dropbox (if app installed)
   - Google Drive
   - All storage providers
3. **User selects PDF from Dropbox**
4. **App copies to temp storage for processing**
5. **User adds bookmarks using PDFBox**
6. **User saves back or shares**

**Code Example:**
```kotlin
// Launch file picker
val intent = StorageUtils.createPdfPickerIntent()
startActivityForResult(intent, PICK_PDF_REQUEST)

// Handle result
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == PICK_PDF_REQUEST && resultCode == RESULT_OK) {
        val pdfUri = data?.data // Can be from Dropbox, Drive, local, anywhere!
        
        // Copy to temp file for PDFBox processing
        val tempFile = File(cacheDir, "temp.pdf")
        StorageUtils.copyPdfFromSaf(context, pdfUri, tempFile)
        
        // Now manipulate with PDFBox
        PdfUtils.addBookmarkToPdf(tempFile, 15, "Important Section")
    }
}
```

---

### **Saving PDFs Persistently**

User wants to save scanned documents to a folder that won't be deleted:

1. **First time: User picks storage folder**
   - Tap "Choose Save Location"
   - Android folder picker appears
   - User picks Documents/Contracts/ (or any folder, even in Dropbox!)
   - App saves this location

2. **Every subsequent save:**
   - App automatically saves to chosen folder
   - No picking needed again
   - Files persist after uninstall

**Code Example:**
```kotlin
// ONCE: User picks folder
val intent = StorageUtils.createFolderPickerIntent()
startActivityForResult(intent, PICK_FOLDER_REQUEST)

override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == PICK_FOLDER_REQUEST && resultCode == RESULT_OK) {
        val folderUri = data?.data!!
        StorageUtils.saveStorageFolderUri(context, folderUri)
        // Folder is now saved - user never has to pick again!
    }
}

// LATER: Save PDF to that folder
val folderUri = StorageUtils.getStorageFolderUri(context)
val pdfUri = StorageUtils.createPdfFile(context, folderUri, "scanned_receipt.pdf")

// Copy PDF to persistent location
StorageUtils.copyPdfToSaf(context, localPdfFile, pdfUri)
```

---

## Storage Options Comparison

| Option | Location | Persists? | User Access | Cloud Support |
|--------|----------|-----------|-------------|---------------|
| **PRIVATE** | /data/data/app/files/ | ❌ Deleted on uninstall | ❌ App only | ❌ No |
| **SAF** | User's choice (Documents, Dropbox, etc.) | ✅ Yes | ✅ Full access | ✅ Yes |
| **SHARE** | Temporary cache | ❌ Temporary | N/A | ✅ Via share sheet |

---

## User Workflows

### **Workflow 1: Scan → Save → Keep Forever**
```
1. User scans document with camera
2. App creates PDF in temp storage
3. User adds bookmarks
4. App saves to user-chosen persistent folder (e.g., Documents/Contracts/)
5. File stays there even if app is deleted
```

### **Workflow 2: Open from Dropbox → Edit → Save Back**
```
1. User taps "Open PDF"
2. Picks contract.pdf from Dropbox
3. App copies to temp for processing
4. User adds bookmarks using PDFBox
5. User saves:
   - Option A: Save back to Dropbox (via SAF)
   - Option B: Share to email/messaging
```

### **Workflow 3: Merge PDFs from Different Sources**
```
1. User taps "Merge PDFs"
2. Picks multiple files:
   - File 1 from Google Drive
   - File 2 from local Downloads
   - File 3 from OneDrive
3. App merges with bookmark preservation
4. User saves merged PDF to Documents/ folder
5. Share via email
```

---

## Technical Implementation

### **File Picker (Open PDFs)**
```kotlin
fun createPdfPickerIntent(): Intent {
    return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/pdf"
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
    }
}
```

### **Folder Picker (Save Location)**
```kotlin
fun createFolderPickerIntent(): Intent {
    return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    }
}
```

### **Persistent Permission**
```kotlin
// Take permission so it survives app restart
context.contentResolver.takePersistableUriPermission(
    folderUri,
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
)
```

---

## AndroidManifest Requirements

No special permissions needed! SAF handles everything:

```xml
<!-- No WRITE_EXTERNAL_STORAGE permission needed -->
<!-- No special cloud storage permissions needed -->
<!-- SAF handles all permissions dynamically -->
```

---

## UI/UX Flow

### **Settings Screen:**
```
Storage Location:
○ App Private (deleted on uninstall)
● Persistent Storage (choose folder)

[Choose Folder] button
Currently: Documents/MyPDFs/ ✓

Note: Files in persistent storage won't be deleted 
even if you uninstall the app.
```

### **Open PDF Flow:**
```
[Open PDF] button
  ↓
Android file picker shows:
  📱 Recent
  📁 Downloads
  ☁️ Dropbox
  ☁️ Google Drive
  ☁️ OneDrive
  📂 Internal Storage
```

---

## Advantages Over Direct Cloud APIs

| Approach | SAF | Dropbox SDK + Drive SDK + OneDrive SDK |
|----------|-----|----------------------------------------|
| **Setup** | None | 3 OAuth flows, API keys |
| **Code** | 50 lines | 500+ lines per service |
| **Maintenance** | Android handles it | Update SDKs regularly |
| **New Services** | Automatic | Integrate each manually |
| **User Experience** | Familiar Android picker | Custom UI per service |
| **Cost** | Free | API rate limits, quotas |

---

## Testing Checklist

- [ ] Open PDF from local storage
- [ ] Open PDF from Google Drive (if installed)
- [ ] Open PDF from Dropbox (if installed)
- [ ] Pick save folder in Documents/
- [ ] Save PDF to chosen folder
- [ ] Verify file persists in file manager
- [ ] Uninstall app
- [ ] Verify files still in Documents/
- [ ] Reinstall app
- [ ] Pick same folder again
- [ ] Open previously saved PDFs

---

## Migration Path

**Phase 1: Current (SAF Backend Ready)** ✅
- StorageUtils class created
- File/folder picker intents ready
- Copy operations implemented

**Phase 2: UI Integration** (Next)
- Add "Open PDF" button
- Add "Choose Save Location" in settings
- Handle activity results

**Phase 3: Full Workflow** (After)
- Camera scan → Save to SAF
- Open from cloud → Edit → Save back
- Merge PDFs from different sources

---

**This approach gives users complete flexibility while keeping implementation simple!**
