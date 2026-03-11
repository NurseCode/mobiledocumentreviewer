# Doc Tracker Pro - Project Documentation

## Overview
Doc Tracker Pro is a native Android application designed for offline PDF creation, scanning, OCR, and manipulation. Developed using Kotlin and Jetpack Compose, it targets Android phones and tablets, offering a responsive UI and an offline-first approach. The app aims to be a comprehensive productivity tool for PDF document management, supporting features like multi-page scanning, searchable PDF creation, PDF merging/splitting, and robust file management. Its ambition is to provide a seamless user experience for managing documents entirely on-device, with easy deployment to the Google Play Console facilitated by GitHub Actions for automated builds.

## User Preferences
- **Development Environment**: Replit (no Android Studio due to storage limitations)
- **Build Method**: GitHub Actions for APK/AAB generation
- **Target Platform**: Android only (not iOS)
- **Distribution**: Google Play Console for Android
- **Device**: Personal Android device for testing
- **Previous Success**: Built a native Android app successfully using this workflow

## System Architecture

### Technology Stack
The application is built with Kotlin and utilizes Jetpack Compose with Material 3 for the UI. It incorporates Room for local database management, CameraX for camera operations, Google ML Kit for Document Scanning and Text Recognition (OCR), and Android's native PdfRenderer and PdfDocument APIs, supplemented by Apache PDFBox Android for advanced PDF manipulation. Gradle manages the build system, and GitHub Actions handles CI/CD.

### UI/UX Decisions
- **Jetpack Compose**: Modern declarative UI for responsive layouts across phones and tablets.
- **Material 3 Theming**: Consistent and modern visual design.
- **Advanced Crop Tool**: Uses ImageCropView library (v3.1.1) with rule-of-thirds grid, rotation support, aspect ratio toggle (free-style/square), and dark theme UI.
- **PDF Viewer**: Includes pinch-to-zoom and pan functionality for improved readability.
- **PDF Thumbnails**: Home screen document cards show a rendered preview of the first page.
- **Annotation Zoom**: Pinch-to-zoom and two-finger pan in the annotate screen for precise drawing/highlighting.
- **Navigation**: Uses NavigationBar and NavigationRail with proper text wrapping for usability.
- **Settings Screen**: Comprehensive settings for storage, quality, OCR language, theme, and sorting.
- **Folder/Category Organization**: Supports document categorization and real-time search with filter chips.

### Technical Implementations & Feature Specifications
- **Offline-First**: All core functionalities operate without internet connectivity.
- **SAF Persistent Storage**: Ensures documents survive app uninstallation by using user-selected directories.
- **Embedded Bookmarks**: Utilizes Apache PDFBox to embed bookmarks directly into PDF files, ensuring compatibility across all PDF readers.
- **Android Share Intent**: For universal sharing to cloud services without requiring individual API integrations.
- **Dual-Purpose OCR**: Employs ML Kit for general document OCR and specialized parsing for receipt data extraction.
- **Smart Scan with Edge Detection**: Uses ML Kit Document Scanner for automatic document edge detection, perspective correction, and cropping. Supports both camera capture and gallery import with ML-powered document boundary detection.
- **Multi-page Camera Scanning**: Supports unlimited pages with CameraX for image capture and rotation fixes.
- **Searchable PDF Creation**: Generates PDFs with an invisible text layer from OCR results.
- **PDF Manipulation Tools Tab**: Complete suite of PDF manipulation tools:
  - **Merge PDFs**: Select multiple PDFs and automatically combine into single file
  - **Split PDF**: Extract specific page ranges (e.g., "1-3, 5-7") into separate PDF files
  - **OCR Text Extract**: Render PDF pages, run ML Kit OCR, display extracted text with clipboard copy
  - **Compress PDF**: Quality selection dialog (placeholder - copies file without compression, real implementation deferred)
  - **Sign Document**: Three signature methods - finger draw (full-screen pad via top-level overlay), typed cursive font (4 styles), and photo upload. Full-screen draw pad rendered at MainScreen level as a `Surface` overlay inside a top-level `Box`. **DocumentCard signing flow** is fully lifted to MainScreen: `LocalRequestDocumentSign` CompositionLocal lets DocumentCard request signing, and MainScreen owns the entire workflow (SignDocumentDialog → FullScreenDrawSignature → file loading → SignPdfScreen → save). **ToolsScreen signing flow** uses separate CompositionLocals (`LocalOpenSigningPadForTools`/`LocalToolsSignatureBitmap`/`LocalConsumeToolsSignature`). The `FullScreenDrawSignature.onSignatureReady` callback routes the bitmap based on `docSignAwaitingPad` flag. Includes drag-to-position placement with resize slider and multi-page navigation.
  - **Annotate & Draw**: Pen tool with 7 colors and adjustable size, highlighter with 5 colors and adjustable width, eraser tool. Supports undo and clear per page. Multi-page navigation.
- **Form Templates System**: Upload a blank form PDF, mark fillable field zones (text, date, signature, checkbox) with color-coded overlays, save as reusable template. Fill mode presents a clean form UI with appropriate input types per field. Signature fields use existing FullScreenDrawSignature. Generates filled PDF by rendering source pages + overlaying field values via Canvas. Preview before saving. Save to SAF directory + document list, share via Android Share Intent.
  - **Field Placement**: Pinch-to-zoom (1x-5x) for precision, drag-to-move fields, 8-handle crop-style resize (4 corners + 4 edges), circular handles that scale with zoom.
  - **Auto-Detect Fields**: ML Kit OCR scans the form for labels (name, date, signature, address, etc.) and auto-places input fields next to them.
  - **Precise Dimensions**: Field size via slider or exact measurements in inches, cm, or mm with real-time conversion.
  - **Field Reorder**: Reorder fields for fill mode via up/down arrows in a dedicated reorder screen (list icon in fill screen top bar). Custom sort order persisted in database (`sortOrder` column on `TemplateField`). Useful for multi-column forms where default top-to-bottom order interleaves columns.
- **Document Management**: Features include naming dialogs with OCR suggestions, rename, delete, and category management via overflow menus. All tools (split, compress, OCR, merge, sign, annotate) accessible from document overflow menu on Home screen.
- **Database**: Room v4 includes `PdfDocument` entity with category support, `Bookmark` entity, `FormTemplate` entity, and `TemplateField` entity (FK with CASCADE delete, `sortOrder` column for fill order).
- **Device Compatibility**: Targets Android 7.0 (API 24) to Android 14 (API 34), supporting various screen sizes.

## External Dependencies
- **Jetpack Compose**: UI framework
- **CameraX**: Camera capture API
- **Google ML Kit**: Document Scanner (edge detection) and Text Recognition (OCR)
- **Room Database**: Local data persistence
- **Kotlin Coroutines**: Asynchronous operations
- **Material 3 Components**: UI components
- **Apache PDFBox Android**: PDF manipulation (e.g., merging, bookmark embedding)
- **Android PdfRenderer and PdfDocument**: Native PDF rendering and creation
- **ImageCropView**: Jetpack Compose image cropping library (v3.1.1)
- **GitHub Actions**: CI/CD for automated builds