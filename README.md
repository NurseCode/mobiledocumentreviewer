# Quick PDF Composer

A native Android app for offline PDF creation, scanning, OCR, and manipulation. Built with Jetpack Compose and optimized for both phones and tablets.

## Features

### Core Functionality
- **Document Scanning**: Use your camera with ML Kit edge detection and auto-enhancement
- **Image to PDF**: Convert gallery photos to high-quality PDFs
- **OCR Text Extraction**: Extract text from scanned documents offline using ML Kit
- **PDF Manipulation**: 
  - Merge multiple PDFs
  - Split PDFs into pages
  - Compress file sizes
- **Annotations**: Draw and add text notes to PDFs
- **Bookmarks**: Navigate large documents easily (5 free bookmarks per PDF)
- **Quality Detection**: Automatically checks if scans are good enough for OCR

### Device Support
- **Phones**: Android 7.0+ (API 24+), optimized for 5-7" screens
- **Tablets**: Full support with responsive layouts for 8-12" screens and stylus input
- **Offline-First**: All features work without internet connection

## Tech Stack

- **Kotlin** - Modern Android development
- **Jetpack Compose** - Declarative UI framework
- **Material 3** - Dynamic color theming
- **CameraX** - Camera capture API
- **ML Kit** - Document scanning and OCR (offline)
- **Room Database** - Local storage for bookmarks
- **PdfRenderer & PdfDocument** - PDF viewing and manipulation

## Building the App

### Using GitHub Actions (Recommended)

This project uses GitHub Actions to build APK and AAB files automatically:

1. Push your code to GitHub
2. Go to the **Actions** tab in your repository
3. The workflow will automatically build:
   - Debug APK (for testing)
   - Release APK (for installation)
   - Release AAB (for Google Play Console)
4. Download artifacts from the completed workflow

### Local Build (Android Studio)

If you have Android Studio installed:

```bash
./gradlew assembleDebug    # Build debug APK
./gradlew assembleRelease  # Build release APK
./gradlew bundleRelease    # Build AAB for Play Store
```

## Uploading to Google Play Console

1. Build the Release AAB using GitHub Actions
2. Download the `app-bundle` artifact (app-release.aab)
3. Go to [Google Play Console](https://play.google.com/console)
4. Create a new app or select existing
5. Navigate to Release → Production → Create new release
6. Upload the AAB file
7. Complete the store listing and submit for review

## Project Structure

```
QuickPDFComposer/
├── .github/workflows/
│   └── build-apk.yml          # GitHub Actions build workflow
├── app/
│   ├── src/main/
│   │   ├── java/com/pdfcomposer/app/
│   │   │   └── MainActivity.kt # Main app code
│   │   ├── res/               # Resources (icons, strings, themes)
│   │   └── AndroidManifest.xml
│   ├── build.gradle           # App-level dependencies
│   └── proguard-rules.pro
├── build.gradle               # Project-level configuration
├── settings.gradle
└── gradle.properties
```

## Permissions

The app requests:
- **Camera**: For document scanning
- **Storage**: For saving/loading PDFs (scoped storage on Android 10+)

## Development Without Android Studio

This project is optimized for development on Replit and GitHub:

1. Edit code in Replit or any text editor
2. Push changes to GitHub
3. GitHub Actions builds the APK/AAB automatically
4. Download and sideload APK to your Android device for testing

No Android Studio required!

## Roadmap

- [x] Basic UI with Material 3
- [x] Document list and management
- [x] Room database for bookmarks
- [ ] Camera scanning with CameraX
- [ ] ML Kit document edge detection
- [ ] Image to PDF conversion
- [ ] OCR text extraction
- [ ] PDF merge/split/compress
- [ ] Annotation tools
- [ ] Quality detection
- [ ] Premium features with in-app billing

## License

MIT License - feel free to use and modify for your projects.

## Contributing

Contributions welcome! Please open an issue or submit a pull request.
