# KemSken — Android Document Scanner & PDF Manager

> **"Scan Smarter. Save Faster."**  
> Native, lightweight, offline-first document scanner, OCR engine, and PDF manager for Android.

---

## 1. Project Overview

**KemSken** is an original native Android application built using modern Jetpack Compose, Kotlin Coroutines & Flow, Clean Architecture MVVM, CameraX, Room Database, and on-device ML Kit OCR.

It delivers a comprehensive scanning workflow comparable to premium scanner utilities while remaining lightweight, fast, battery-efficient, and 100% offline-first.

### Core Philosophy
1. **Privacy-First & Local-First**: No mandatory account registration, no remote server uploads by default, no tracking SDKs or ads.
2. **Ultra-Lightweight & Responsive**: Zero heavy native bloat, optimized hardware-accelerated image pipeline using Android Canvas/Skia for high frame rates and low memory consumption on budget devices.
3. **Professional Quality**: Perspective correction, 10 document enhancement filters (Auto Enhance, Magic Color, Black & White, Whiteboard, Receipt, etc.), multi-page PDF generation, searchable PDF layers, electronic signatures, and watermark tools.

---

## 2. Architecture & Tech Stack

```text
KemSken/
├── core/
│   ├── database/        # Room Database (Documents, Pages, OCR, Folders, Annotations)
│   ├── image/           # DocumentDetector, PerspectiveTransformer, ImageEnhancer
│   ├── ocr/             # Google ML Kit on-device Text Recognition (Indonesian & Latin)
│   ├── pdf/             # Android native PdfDocument, PdfRenderer, Print Framework
│   ├── security/        # AndroidKeyStore AES-256-GCM CryptoManager & AppLockManager
│   └── storage/         # Bounded memory FileManager & safe FileProvider sharing
├── data/
│   └── repository/      # DocumentRepository (Single Source of Truth)
├── domain/
│   └── model/           # ScanMode, FilterType, DocumentCorners, PdfPageSize, PdfQuality
├── feature/
│   ├── home/            # Dashboard, Search, Folder Chips, Sort, Document Cards
│   ├── camera/          # CameraX Live Preview, Real-Time Edge Overlay, Shutter
│   ├── editor/          # 4-Point Interactive Crop, 10 Enhancement Filters, Multi-Page
│   ├── viewer/          # Swipeable High-Resolution Pager, Quick Actions
│   ├── ocr/             # Side-by-Side OCR Result, In-Place Editing, TXT Export
│   ├── export/          # PDF Config (A4, Letter, Auto), Quality Presets, Searchable PDF
│   ├── idcard/          # Specialized 2-Sided ID / KTP / SIM composite onto A4 sheet
│   ├── qr/              # On-device QR & Barcode Scanner with URL confirmation
│   ├── trash/           # Recycle Bin with Document Restore & Permanent Delete
│   └── settings/        # App Lock PIN, Storage Usage, Cache Cleanup, About
└── ui/
    ├── components/      # DocumentCard, CropHandlesCanvas, SignaturePadView, EmptyState
    ├── navigation/      # Navigation Compose Type-Safe Routes
    └── theme/           # KemSken Brand Identity (Deep Teal #087F8C & Soft Cyan #3CCBC5)
```

---

## 3. Implemented Feature Completion Matrix

| Feature | Status | Verification & Implementation Detail |
|---|---|---|
| **Camera Scanning** | **Implemented** | CameraX preview, flash control, orientation handling, pinch-to-zoom, tap-to-focus |
| **Edge Detection** | **Implemented** | Sobel gradient edge analysis, quadrilateral extraction, smoothed corner tracking |
| **Perspective Crop** | **Implemented** | 4-point projective homography transformation (`Matrix.setPolyToPoly`), interactive touch handles |
| **Image Enhancement** | **Implemented** | 10 filter presets (Original, Auto Enhance, Magic Color, B&W, Grayscale, High Contrast, Brighten, Sharpen, Whiteboard, Receipt) + manual sliders |
| **Multi-Page Scanning** | **Implemented** | Page reordering, thumbnail strip, add from camera/gallery, delete, individual filter adjustment |
| **OCR Text Recognition** | **Implemented** | Google ML Kit on-device Latin & Indonesian OCR, in-place editing, clipboard copy, TXT export |
| **PDF Creation & Export** | **Implemented** | Single & multi-page PDF (`PdfDocument`), configurable paper sizes (A4, Letter, Legal, A5, Auto), quality presets, searchable PDF text layer |
| **Document Management** | **Implemented** | Room Database persistence, folders, favorites, full-text search (title, tags, OCR), sorting (6 modes), grid/list toggle |
| **Recycle Bin** | **Implemented** | Soft deletion (`deletedAt`), restore functionality, permanent delete, empty trash |
| **ID Card Scanner** | **Implemented** | Guided Front & Back capture composed onto a single standardized A4 sheet |
| **QR Code Scanner** | **Implemented** | On-device ML Kit barcode scanner, clipboard copy, browser confirmation dialog |
| **Electronic Signature** | **Implemented** | Smooth touch signature drawing pad, save locally, stamp signature onto scanned pages |
| **Watermark Tool** | **Implemented** | Customizable watermark text, angle, and opacity |
| **Security & App Lock** | **Implemented** | SHA-256 salted PIN lock, AES-256-GCM Keystore crypto engine |
| **Storage Management** | **Implemented** | Real storage usage calculator, export cache cleanup |

---

## 4. Build & Installation Instructions

### Prerequisites
- Android Studio Ladybug / Meerkat or Gradle 8.13+
- JDK 17 or JDK 21
- Android SDK 36 (minSdk 24, targetSdk 36)

### Build Commands
```bash
# Compile debug APK
gradle assembleDebug

# Run Unit & Robolectric Tests
gradle :app:testDebugUnitTest

# Assemble Release Bundle / APK
gradle assembleRelease
```

---

## 5. GitHub Actions CI/CD Workflow

File workflow otomatis telah disediakan di `.github/workflows/build.yml`.

### Fitur Workflow:
1. **Otomatis Dijalankan**: Setiap kali ada `push` atau `pull_request` ke branch `main` atau `master`.
2. **Manual Dispatch**: Dapat dipicu secara manual dari tab **Actions** di GitHub dengan opsi memilih tipe build (`debug`, `release`, atau `all`).
3. **Pengujian Otomatis**: Menjalankan seluruh pengujian unit & Robolectric (`./gradlew testDebugUnitTest`).
4. **Artifact Otomatis**:
   - `KemSken-debug-apk`: File APK debug siap diinstal langsung di perangkat pengujian.
   - `KemSken-release-apk`: File APK rilis (jika opsi release dipilih).
   - `test-reports`: Arsip laporan html hasil eksekusi unit test.

### Konfigurasi GitHub Secrets (Opsional untuk Penandatanganan Release APK):
Jika ingin menandatangani APK rilis dengan keystore pribadi Anda secara otomatis di GitHub Actions, tambahkan Secrets berikut di **Settings > Secrets and variables > Actions**:
- `KEYSTORE_BASE64`: Isi file keystore `.jks` yang telah di-encode ke format base64 (`base64 -w 0 your-keystore.jks`).
- `STORE_PASSWORD`: Kata sandi keystore Anda.
- `KEY_ALIAS`: Alias kunci di dalam keystore.
- `KEY_PASSWORD`: Kata sandi alias kunci.
