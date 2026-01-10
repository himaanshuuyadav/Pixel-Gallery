# Pixel Gallery

A private, offline Android gallery app built with Jetpack Compose and Kotlin that provides a beautiful and responsive interface for viewing your photos and videos.

## Features

- 📸 **Photo & Video Gallery**: Browse all your device media in a responsive grid layout
- 📁 **Albums**: Automatically organized by folders/buckets
- 🔍 **Multiple Sort Options**: Sort by date, name, or size (ascending/descending)
- 🔎 **Full-Screen Viewer**: View images with pinch-to-zoom and swipe navigation
- 🔒 **Privacy-First**: Completely offline, no cloud, no analytics, no tracking
- 🎨 **Material Design 3**: Modern UI with dark theme by default
- 📤 **Share**: Share photos and videos with other apps
- 🗑️ **Delete**: Remove media with system confirmation (Android 11+)
- ⚡ **Fast Performance**: Efficient MediaStore queries and Coil image loading

## Technical Details

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material3
- **Architecture**: MVVM (Model-View-ViewModel)
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 35 (Android 15)
- **Package**: com.himanshu.pixelgallery

### Key Dependencies

- Compose BOM 2024.02.00
- Material3
- Navigation Compose
- Lifecycle ViewModel
- Coil Compose (image loading)
- Accompanist Permissions

### Permissions

The app uses scoped storage and requests appropriate permissions:
- Android 13+: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`
- Android 10-12: `READ_EXTERNAL_STORAGE`
- Android 9 and below: `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`

## Building and Running

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK with API 35

### Build Steps

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd "Pixel Gallery"
   ```

2. Open the project in Android Studio

3. Sync Gradle files (File → Sync Project with Gradle Files)

4. Build the project:
   ```bash
   ./gradlew build
   ```

5. Run on device/emulator:
   ```bash
   ./gradlew installDebug
   ```
   Or use the "Run" button in Android Studio

### Release Build

To create a release build:

```bash
./gradlew assembleRelease
```

The APK will be located at: `app/build/outputs/apk/release/app-release.apk`

**Note**: You'll need to sign the APK for distribution. Configure signing in `app/build.gradle.kts`.

## Project Structure

```
app/src/main/java/com/himanshu/pixelgallery/
├── MainActivity.kt                 # Main entry point
├── data/
│   └── MediaRepository.kt         # MediaStore queries and operations
├── model/
│   ├── MediaItem.kt              # Media data class
│   └── Album.kt                  # Album data class
├── viewmodel/
│   └── MediaViewModel.kt         # State management and business logic
├── navigation/
│   └── Navigation.kt             # Navigation graph
└── ui/
    ├── screens/
    │   ├── GalleryScreen.kt      # Main gallery with tabs
    │   ├── AlbumScreen.kt        # Album detail view
    │   └── ViewerScreen.kt       # Full-screen image viewer
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

## Usage

1. **Grant Permissions**: On first launch, grant storage permissions to view your media
2. **Browse Photos**: View all photos in the Photos tab
3. **View Albums**: Switch to Albums tab to see media organized by folders
4. **Watch Videos**: Videos tab shows all video files
5. **Sort**: Use the menu button (⋮) to change sort order
6. **View Full Screen**: Tap any image to open full-screen viewer
7. **Zoom**: Pinch to zoom in/out on images
8. **Navigate**: Swipe left/right to view next/previous images
9. **Share**: Use the share button to send photos to other apps
10. **Delete**: Use the delete button to remove photos (requires confirmation)

## Testing

Tested on:
- Android 13 (API 33)
- Android 14 (API 34)
- Android 15 (API 35)

## Privacy

- ✅ No internet permission
- ✅ No analytics or tracking
- ✅ No cloud storage
- ✅ All data stays on your device
- ✅ No advertisements

## License

This project is created for educational and personal use.

## Contributing

Feel free to submit issues and enhancement requests!

---

Built with ❤️ using Jetpack Compose
