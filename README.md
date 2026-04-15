# Android Gallery

A production-ready, feature-rich gallery application for Android, inspired by the OnePlus OxygenOS Photos app and Apple Photos app.

## Screenshots

The app features a clean, minimal Material You design with smooth animations across all screens.

## Features

### 📸 Gallery View
- Displays all images and videos from device storage using MediaStore API
- Fast scrolling grid with thumbnail caching via Coil
- Media grouped by date with sticky headers
- Adjustable grid size (2, 3, or 4 columns)
- Multi-select support for batch operations

### 📁 Albums
- Auto-generated albums based on device buckets (Camera, Screenshots, Downloads, WhatsApp, etc.)
- User-created custom albums
- Album cover images with media count

### 🖼️ Media Viewer
- Full-screen viewer with swipe navigation between media
- Pinch-to-zoom and double-tap zoom for images
- Video playback with ExoPlayer / Media3
- Metadata display (date, size, resolution, duration)
- Share, favourite, delete, and set-as-wallpaper actions

### 🔒 Private Safe (Secure Vault)
- AES-256-GCM encryption via Android Keystore — files never stored in plain form
- PIN / Password authentication
- Biometric authentication (fingerprint / face unlock)
- FLAG\_SECURE prevents screenshots inside the Safe
- Move media into the Safe and restore it at any time

### 🔍 Search
- Real-time debounced search by file name
- Instant results from the local Room database

### ⭐ Favorites
- Mark/unmark any photo or video as a favourite
- Dedicated Favourites screen

### 🗑️ Trash / Recently Deleted
- Soft-delete media to Trash
- Items are automatically and permanently deleted after 30 days (WorkManager)
- Restore individual items before they expire

### ⚙️ Settings
- Light / Dark theme toggle
- Dynamic Material You colours (Android 12+)
- Grid column count picker (2 / 3 / 4)

### 🎨 UI / UX
- Jetpack Compose with Material 3
- Edge-to-edge layout with smooth enter/exit transitions
- Rounded corners, soft shadows, gesture-driven navigation

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Image Loading | Coil |
| Database | Room |
| Video | ExoPlayer (Media3) |
| Background Work | WorkManager |
| Media Access | MediaStore API (Scoped Storage) |
| Preferences | DataStore |
| Security | Android Keystore (AES-256-GCM) + Biometric API |
| Navigation | Navigation Compose |
| Async | Kotlin Coroutines + Flow |
| Paging | Paging 3 |

---

## Project Structure

```
app/src/main/java/com/gallery/android/
├── GalleryApplication.kt       # Hilt + WorkManager bootstrap
├── MainActivity.kt             # Entry point, permission handling
│
├── data/
│   ├── local/
│   │   ├── database/
│   │   │   ├── GalleryDatabase.kt
│   │   │   └── dao/            # MediaDao, AlbumDao, FavoriteDao
│   │   └── entity/             # MediaEntity, AlbumEntity, FavoriteEntity
│   └── repository/             # MediaRepositoryImpl, AlbumRepositoryImpl
│
├── domain/
│   ├── model/                  # MediaItem, Album
│   ├── repository/             # Interfaces
│   └── usecase/                # GetMedia, GetAlbums, Favorite, Trash, Search
│
├── ui/
│   ├── theme/                  # Color, Type, Theme
│   ├── navigation/             # GalleryNavigation (NavHost + BottomBar)
│   ├── gallery/                # GalleryScreen + GalleryViewModel
│   ├── albums/                 # AlbumsScreen + AlbumsViewModel
│   ├── viewer/                 # MediaViewerScreen + MediaViewerViewModel
│   ├── search/                 # SearchScreen + SearchViewModel
│   ├── favorites/              # FavoritesScreen (+ inline VM)
│   ├── trash/                  # TrashScreen (+ inline VM)
│   ├── safe/                   # PrivateSafeScreen + PrivateSafeViewModel
│   └── settings/               # SettingsScreen + SettingsViewModel
│
├── di/                         # AppModule, DatabaseModule, RepositoryModule
├── worker/                     # TrashCleanupWorker (HiltWorker)
└── utils/                      # CryptoUtils, DateUtils, MediaUtils, PermissionUtils
```

---

## Setup Instructions

### Prerequisites
- **Android Studio Ladybug** or newer (Arctic Fox is the minimum for Compose support)
- **Android SDK**: compile SDK 35, min SDK 26
- **JDK 17** (bundled with recent Android Studio versions)
- A physical or virtual Android device running Android 8.0 (API 26) or higher

### 1. Clone the repository
```bash
git clone https://github.com/zaki-vemp/android-gallery.git
cd android-gallery
```

### 2. Open in Android Studio
Open the cloned folder in Android Studio. Gradle will sync automatically and download all dependencies.

### 3. Create `local.properties`
If the file does not exist, create it in the root of the project:
```
sdk.dir=/path/to/your/Android/sdk
```
On macOS/Linux this is usually `~/Library/Android/sdk` or `~/Android/Sdk`.  
On Windows: `C:\Users\<user>\AppData\Local\Android\Sdk`.

### 4. Build and run
Click **Run ▶** in Android Studio, or build from the terminal:
```bash
./gradlew assembleDebug
```
Install on a connected device:
```bash
./gradlew installDebug
```

### 5. Runtime permissions
The app will request storage permissions on first launch:
- **Android 14+**: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`
- **Android 13**: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`
- **Android 12 and below**: `READ_EXTERNAL_STORAGE`

---

## Security Notes

- The **Private Safe** encrypts file references using AES-256-GCM backed by the Android Keystore. Keys never leave the secure hardware.
- PIN hashes are stored in DataStore using SHA-256 (no plain-text PIN is ever persisted).
- `FLAG_SECURE` is applied to the Safe screen to prevent screenshots and app-switcher previews.

---

## Permissions Used

| Permission | Reason |
|---|---|
| `READ_MEDIA_IMAGES` | Load images (Android 13+) |
| `READ_MEDIA_VIDEO` | Load videos (Android 13+) |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Partial media access (Android 14+) |
| `READ_EXTERNAL_STORAGE` | Load media (Android ≤ 12) |
| `WRITE_EXTERNAL_STORAGE` | Delete/move files (Android ≤ 9) |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | Biometric unlock for Private Safe |
| `SET_WALLPAPER` | Set photo as wallpaper |
| `WAKE_LOCK` | WorkManager background tasks |

---

## License

```
MIT License — see LICENSE file for details.
```