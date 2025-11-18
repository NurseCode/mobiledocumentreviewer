# Quick PDF Composer - Project Documentation

## Overview
A native Android app for offline PDF creation, scanning, OCR, and manipulation. Built specifically for Android devices (phones and tablets) using Kotlin and Jetpack Compose. The app works completely offline and uses GitHub Actions for building APK/AAB files since Android Studio is not available locally.

## Project Goals
- Create a productivity app for PDF document management on Android
- Support both phones and tablets with responsive UI
- Provide offline-first functionality (no cloud dependencies)
- Enable easy deployment to Google Play Console
- Build without requiring Android Studio installation

## Current State
**Status**: Core features complete; implementing document management system
- ✅ Project structure created
- ✅ MainActivity with Jetpack Compose UI
- ✅ Room database for documents
- ✅ Material 3 theming with responsive layouts
- ✅ GitHub Actions workflow for APK/AAB building
- ✅ **Apache PDFBox Android integrated**
- ✅ **Embedded bookmark functionality (works in Adobe Acrobat!)**
- ✅ **Android Share Intent for cloud storage**
- ✅ **PDF merge and split operations**
- ✅ **Flexible storage locations**
- ✅ **Multi-page camera scanning with unlimited pages**
- ✅ **Dual-purpose OCR (general documents + receipt detection)**
- ✅ **Searchable PDF creation with invisible text layers**
- ✅ **PDF viewer with bookmark navigation**
- ✅ **EXIF orientation fix added**
- ✅ **Content URI support for SAF documents**
- 🚧 **SAF persistent storage architecture (in progress)**
- 🚧 **Document naming on creation (in progress)**
- 🚧 **Folder/category organization (in progress)**
- ⏳ Rename functionality (pending)
- ⏳ Search and sorting (pending)
- ⏳ Settings UI (pending)
- ⏳ Receipt JSON extraction (pending)
- ⏳ Digital signatures (pending)

## Recent Changes
**Date**: November 17, 2025
- ✅ Integrated Apache PDFBox Android for bookmark embedding
- ✅ Implemented portable bookmarks that work across all PDF readers
- ✅ Added Android Share Intent for cloud storage (Dropbox, Drive, Email, etc.)
- ✅ Created flexible storage system (Private/Public/Share)
- ✅ Implemented PDF merge and split functionality
- ✅ Added FileProvider for secure file sharing
- ✅ Created PdfUtils utility class with all PDF operations
- ✅ Added SettingsManager for user preferences with DataStore
- ✅ Updated build configuration with PDFBox and ProGuard rules
- ✅ **Implemented multi-page scanning with CameraX**
  - Unlimited pages per document
  - Page preview gallery with thumbnails
  - Delete and reorder pages before saving
  - Robust file cleanup on all exit paths
- ✅ **Built dual-purpose OCR system with ML Kit**
  - General text extraction from any document
  - Automatic receipt detection for future JSON parsing
  - Searchable PDFs with invisible text layers
- ✅ **Integrated SAF file picker for opening existing PDFs**
  - Access PDFs from Dropbox, Google Drive, local storage
  - Persistent permissions for repeated access

## User Preferences
- **Development Environment**: Replit (no Android Studio due to storage limitations)
- **Build Method**: GitHub Actions for APK/AAB generation
- **Target Platform**: Android only (not iOS)
- **Distribution**: Google Play Console for Android
- **Device**: Personal Android device for testing
- **Previous Success**: Built a native Android app successfully using this workflow

## Project Architecture

### Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Database**: Room (SQLite)
- **Camera**: CameraX API
- **ML/AI**: Google ML Kit (Document Scanner, Text Recognition)
- **PDF APIs**: Android PdfRenderer and PdfDocument
- **Build System**: Gradle
- **CI/CD**: GitHub Actions

### Key Dependencies
```gradle
- Jetpack Compose (UI)
- CameraX (Camera capture)
- ML Kit Document Scanner (Edge detection)
- ML Kit Text Recognition (OCR)
- Room Database (Local storage)
- Kotlin Coroutines (Async operations)
- Material 3 Components
- Apache PDFBox Android (PDF manipulation)
```

### File Structure
```
QuickPDFComposer/
├── .github/workflows/build-apk.yml  # Automated build pipeline
├── app/
│   ├── src/main/
│   │   ├── java/com/pdfcomposer/app/
│   │   │   ├── MainActivity.kt           # Main app with scan workflow
│   │   │   ├── MultiPageScanScreen.kt    # Multi-page scan coordinator
│   │   │   ├── CameraScreen.kt           # Camera capture UI
│   │   │   ├── PageReviewScreen.kt       # Page preview gallery
│   │   │   ├── CameraUtils.kt            # Camera, OCR, optimization
│   │   │   ├── ImageToPdfUtils.kt        # PDF creation with text layers
│   │   │   ├── PdfUtils.kt               # PDF operations (merge/split)
│   │   │   ├── StorageUtils.kt           # SAF file handling
│   │   │   ├── SettingsManager.kt        # User preferences
│   │   │   └── [Database entities/DAOs]
│   │   ├── AndroidManifest.xml  # App configuration
│   │   └── res/  # Resources (icons, themes, strings)
│   └── build.gradle  # App dependencies
├── build.gradle  # Project configuration
└── settings.gradle  # Project settings
```

### Database Schema
**Bookmark Entity**:
- id (PrimaryKey)
- pdfUri (String)
- pageNumber (Int)
- label (String)
- timestamp (Long)

**PdfDocument Entity**:
- id (PrimaryKey)
- fileName (String)
- filePath (String)
- pageCount (Int)
- createdAt (Long)

**SavedSignature Entity** (Future):
- id (PrimaryKey)
- name (String) - e.g., "Personal", "Business", "Initials"
- imagePath (String) - Path to signature PNG
- createdAt (Long)

**Receipt Entity** (Future):
- id (PrimaryKey)
- pdfUri (String) - Link to PDF file
- merchantName (String)
- date (LocalDate)
- total (Double)
- tax (Double)
- category (String)
- jsonData (String) - Full structured data
- createdAt (Long)

### Device Compatibility
- **Minimum SDK**: API 24 (Android 7.0) - covers 95%+ devices
- **Target SDK**: API 34 (Android 14)
- **Screen Support**: 5-7" phones and 8-12" tablets
- **Camera**: Works with 5MP+ cameras (standard on modern devices)
- **Storage**: Uses scoped storage (Android 10+ compliant)

## Build Process
1. Code development in Replit or any editor
2. Push to GitHub repository
3. GitHub Actions workflow triggers automatically
4. Workflow builds:
   - Debug APK (for testing)
   - Release APK (for direct installation)
   - Release AAB (for Google Play upload)
5. Download artifacts from GitHub Actions
6. Sideload APK to Android device for testing
7. Upload AAB to Google Play Console for distribution

## Features Roadmap

### MVP Features (In Progress)
- [x] Project structure and UI framework
- [x] Database setup for bookmarks
- [x] Multi-page document scanning with camera
- [x] Image to PDF conversion with searchable text
- [x] OCR text extraction (general + receipt detection)
- [x] PDF merge/split operations
- [x] SAF integration for file access
- [ ] Receipt JSON data extraction
- [ ] Digital signatures
- [ ] Basic annotations
- [ ] Bookmark navigation UI
- [ ] Quality detection for scans

### Future Enhancements
- Premium tier with unlimited bookmarks
- Advanced OCR features
- Batch processing
- Cloud backup (optional)
- Advanced editing (rotate, watermark)
- Dark mode
- Accessibility improvements

## Design Decisions
1. **Native Android vs Cross-Platform**: Chose native Android due to:
   - Prior success with this approach
   - Better performance for camera/OCR operations
   - Simpler ML Kit integration
   - Optimized for Google Play Console deployment
   
2. **Jetpack Compose**: Modern declarative UI that handles phone/tablet responsiveness automatically

3. **Offline-First**: All core features work without internet for privacy and reliability

4. **GitHub Actions**: Enables building without Android Studio installation

5. **Storage Access Framework (SAF)**: For persistent file storage that survives app uninstall
   - Files stored in user-chosen locations (Documents, Downloads, external storage)
   - Can open PDFs from any source (Dropbox, Google Drive, local storage)
   - Full documentation in SAF_GUIDE.md

6. **Embedded Bookmarks**: Using Apache PDFBox to embed bookmarks directly in PDF files
   - Bookmarks work in any PDF reader (Adobe Acrobat, browsers, etc.)
   - Portable across devices without app database dependency

7. **Android Share Intent for Cloud**: Instead of individual cloud service integrations
   - Universal sharing to Dropbox, Drive, Email, Slack, etc.
   - No API keys or OAuth flows needed
   - User controls via native Android sharing

8. **Dual-Purpose OCR Architecture**:
   - **General Document OCR**: Extract text from any document (contracts, forms, letters)
   - **Receipt-Specific Parsing**: Auto-detect receipts and extract structured JSON data
   - ML Kit Text Recognition for universal text extraction
   - Pattern matching for receipt fields (merchant, total, items, tax, date)

9. **Digital Signatures**: Draw and embed signatures directly in PDF files
   - Signature canvas for drawing with finger/stylus
   - Save signatures for reuse (personal, business, initials)
   - Embed as images in PDF at user-specified positions
   - Basic visual signatures (not cryptographic certificates)

## Notes
- This is an Android-only app (not cross-platform)
- User has successfully built Android apps before using similar workflow
- Focus is on productivity and document management
- App will be published to Google Play Console
- Testing is done on physical Android device (no emulator needed)
