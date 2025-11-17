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
**Status**: Core PDF features implemented
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
- ⏳ Settings UI (pending)
- ⏳ Camera scanning (pending implementation)
- ⏳ OCR functionality (pending implementation)

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
│   │   ├── java/com/pdfcomposer/app/MainActivity.kt  # Main app code
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
- [ ] Document scanning with camera
- [ ] Image to PDF conversion
- [ ] OCR text extraction
- [ ] PDF merge/split/compress
- [ ] Basic annotations
- [ ] Bookmark navigation
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
