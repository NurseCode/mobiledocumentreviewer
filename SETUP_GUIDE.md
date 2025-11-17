# Quick PDF Composer - Setup Guide

## Project Overview
This is a native Android app built with Kotlin and Jetpack Compose for offline PDF creation, scanning, OCR, and manipulation. The app works on both Android phones and tablets with responsive layouts.

## Current Status ✅
**Initial Setup Complete!**
- ✅ Android project structure created
- ✅ MainActivity with Jetpack Compose UI
- ✅ Material 3 theming (purple/lavender color scheme)
- ✅ Responsive layouts (NavigationBar for phones, NavigationRail for tablets)
- ✅ Room database configured for bookmarks and documents
- ✅ GitHub Actions workflow for automated APK/AAB building
- ✅ All necessary configuration files

## How to Build Your App

### Step 1: Push to GitHub
```bash
git init
git add .
git commit -m "Initial Quick PDF Composer setup"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/QuickPDFComposer.git
git push -u origin main
```

### Step 2: GitHub Actions Builds Automatically
Once you push to GitHub:
1. Go to your repository on GitHub
2. Click the **Actions** tab
3. The "Build Android APK/AAB" workflow will run automatically
4. Wait 5-10 minutes for the build to complete

### Step 3: Download Your APK
After the workflow completes:
1. Click on the completed workflow run
2. Scroll to **Artifacts** at the bottom
3. Download:
   - **app-debug** - For testing (larger file, includes debug info)
   - **app-release** - For installation (optimized)
   - **app-bundle** - For Google Play Console upload (.aab file)

### Step 4: Install on Your Android Device
**Option A: Direct Install (Debug APK)**
1. Download `app-debug.apk` to your phone
2. Enable "Install from Unknown Sources" in Settings
3. Tap the APK file to install
4. Grant Camera and Storage permissions when prompted

**Option B: Google Play Console (Release AAB)**
1. Download `app-release.aab`
2. Go to [Google Play Console](https://play.google.com/console)
3. Create a new app or select existing
4. Navigate to Release → Production → Create new release
5. Upload the AAB file
6. Complete store listing and submit

## Project Structure
```
QuickPDFComposer/
├── .github/workflows/
│   └── build-apk.yml          # Automated build workflow
├── app/
│   ├── src/main/
│   │   ├── java/com/pdfcomposer/app/
│   │   │   └── MainActivity.kt  # Main app code (12KB)
│   │   ├── res/                 # Resources
│   │   └── AndroidManifest.xml  # Permissions & config
│   ├── build.gradle            # Dependencies
│   └── proguard-rules.pro      # Code optimization
├── build.gradle                # Project configuration
├── settings.gradle
└── gradle.properties
```

## Features Implemented

### UI Components ✅
- **Home Screen**: Document list with empty state
- **Scan Screen**: Camera/gallery upload interface
- **Tools Screen**: PDF manipulation options
- **Responsive Design**: Adapts to phones (375px) and tablets (768px+)
- **Material 3**: Purple theme with dynamic colors

### Database ✅
- **Bookmarks Table**: Store page bookmarks with labels
- **Documents Table**: Track PDF files and metadata
- **Room Database**: Offline-first SQLite storage

### Ready for Implementation 🚧
- Camera scanning with CameraX
- ML Kit document edge detection
- OCR text extraction
- PDF merge/split/compress
- Annotation tools
- Quality detection

## Next Steps

### To Continue Development:
1. **Edit code in Replit** - All code is in `app/src/main/java/com/pdfcomposer/app/MainActivity.kt`
2. **Push to GitHub** - Changes trigger automatic build
3. **Download new APK** - Test on your device
4. **Repeat** - Fast iteration cycle without Android Studio!

### To Add Camera Scanning:
The code structure is ready. Next implementation phase will add:
- CameraX for camera capture
- ML Kit DocumentScanner for edge detection
- Image processing and PDF generation
- Quality detection (blur, contrast, resolution checks)

### To Add PDF Manipulation:
- PdfRenderer for viewing PDFs
- PdfDocument for creating/editing
- Merge algorithm (combine multiple PDFs)
- Split algorithm (extract pages)
- Compression (reduce file size)

## Testing Without Android Studio

### Local Development:
1. Edit `MainActivity.kt` in any text editor (Replit, VS Code, etc.)
2. Push changes to GitHub
3. GitHub Actions builds APK automatically
4. Download and sideload to your Android device

### No Emulator Needed:
- Test directly on your physical Android device
- Faster than emulators
- Real camera testing
- Actual screen sizes (not simulated)

## File Sizes (Estimated)
- **Debug APK**: ~15-20 MB (includes debug symbols)
- **Release APK**: ~10-15 MB (optimized)
- **Release AAB**: ~8-12 MB (Google Play optimized)

Final app size will be ~12-18 MB depending on features.

## Compatibility
- **Minimum**: Android 7.0 (API 24) - Released 2016
- **Target**: Android 14 (API 34) - Latest version
- **Coverage**: 95%+ of active Android devices
- **Devices**: Phones (5-7" screens) and Tablets (8-12" screens)

## Permissions Required
- **CAMERA**: For document scanning
- **READ_MEDIA_IMAGES**: For gallery uploads (Android 13+)
- **READ_EXTERNAL_STORAGE**: For gallery uploads (Android 12 and below)

All permissions are requested at runtime with clear explanations.

## Support
- See `README.md` for feature documentation
- See `replit.md` for project architecture
- GitHub Actions logs show build errors if any

## Preview
A live preview of the UI is available at `preview.html` showing:
- Phone layout (375x812 - iPhone-like dimensions)
- Tablet layout (768x1024 - iPad-like dimensions)
- Material 3 design with purple theme
- Empty states and navigation

---

**Ready to build?** Push your code to GitHub and watch the magic happen! 🚀
