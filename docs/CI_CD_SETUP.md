# Dokumentasi Setup CI/CD & Rilis APK (GitHub Actions)

Dokumen ini menjelaskan cara kerja workflow `.github/workflows/android.yml` untuk build, pengujian otomatis, dan pengunduhan file APK secara langsung setelah `push` ke GitHub.

## 1. Alur Kerja Otomatis (`android.yml`)

Setiap kali ada `push` ke branch `main` atau `master`, atau `pull_request`:
1. **Checkout & Setup Lingkungan**:
   - Menjalankan runner `ubuntu-latest` dengan **Temurin JDK 21**.
   - Memasang **Gradle 9.3.1** melalui `gradle/actions/setup-gradle@v4` lengkap dengan cache dependensi.
2. **Konfigurasi Firebase & Keystore**:
   - Jika secret `GOOGLE_SERVICES_JSON_B64` tersedia di GitHub Secrets, workflow mendekodenya menjadi `app/google-services.json`. Jika tidak, menggunakan `app/google-services.json` di repositori.
   - Memastikan `debug.keystore` tersedia untuk menandatangani build Debug secara otomatis.
3. **Pengujian Otomatis (Unit & Robolectric Tests)**:
   - Menjalankan `gradle :app:testDebugUnitTest` untuk `ExampleUnitTest` (enkripsi AES-256-GCM, TOTP 2FA, format `.xlsx`) dan `ExampleRobolectricTest` (SQLite Room offline & ekspor PDF).
   - Mengunggah laporan hasil tes ke artifact `unit-test-reports`.
4. **Build & Upload Artifact APK**:
   - Menjalankan `gradle :app:assembleDebug --no-daemon` dan mengunggah APK ke artifact **`WargaKos-debug-apk`**.
   - Jika secret rilis (`KEYSTORE_B64`, `STORE_PASSWORD`, `KEY_PASSWORD`) diisi, workflow juga menjalankan `gradle :app:assembleRelease --no-daemon` dan mengunggah **`WargaKos-release-apk`**.

## 2. Cara Mengunduh APK dari GitHub

1. Push kode ke GitHub (`git push origin main`).
2. Buka repositori di browser lalu klik tab **Actions**.
3. Klik workflow run terbaru bernama **Android CI/CD Build & APK**.
4. Pada bagian **Artifacts** di bagian bawah halaman, klik **`WargaKos-debug-apk`** (atau `WargaKos-release-apk`) untuk mengunduh ZIP berisi file `.apk`.

## 3. Catatan Penting Google Sign-In di Build Baru

Jika Anda membuat keystore baru untuk rilis Play Store atau meng-clone proyek ke proyek Firebase lain:
1. Ambil SHA-1 & SHA-256 keystore Anda:
   ```bash
   keytool -list -v -keystore my-upload-key.jks -alias upload
   ```
2. Tambahkan kedua fingerprint tersebut ke **Firebase Console → Project Settings → Your apps → WargaKos (Android)** agar **Google Sign-In (Credential Manager)** berfungsi pada APK tersebut.
