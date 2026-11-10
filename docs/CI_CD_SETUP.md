# Dokumentasi Setup CI/CD & Rilis APK (GitHub Actions)

Dokumen ini menjelaskan cara kerja workflow `.github/workflows/android.yml` untuk pengujian otomatis, build APK, dan pengunduhan file `.apk` setelah `push` ke GitHub.

## 1. Alur Kerja Otomatis (`.github/workflows/android.yml`)

Setiap kali ada `push` ke GitHub (branch apa pun atau tag `v*`), `pull_request` ke `main`/`master`, atau `workflow_dispatch`:
1. **Checkout & Setup Lingkungan**:
   - Menjalankan runner `ubuntu-24.04` dengan **Temurin JDK 21** (`actions/checkout@v7` dan `actions/setup-java@v6`, berjalan native di Node.js 24).
   - Menyiapkan Android SDK langsung menggunakan `sdkmanager` bawaan `ubuntu-24.04` (`$ANDROID_HOME`), menerima seluruh lisensi SDK tanpa error `SIGPIPE`/`tools`, dan memasang `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`.
   - Memasang **Gradle 9.9.0** melalui `gradle/actions/setup-gradle@v6` lengkap dengan cache dependensi (selaras dengan `gradle-wrapper.properties`).
2. **Konfigurasi Keystore & Firebase**:
   - Jika secret `GOOGLE_SERVICES_JSON_B64` tersedia di GitHub Secrets, workflow mendekodenya menjadi `app/google-services.json`. Jika tidak, menggunakan `app/google-services.json` yang ada di repositori.
   - Menggunakan `debug.keystore.base64` atau membuat `debug.keystore` sementara secara otomatis untuk build Debug.
3. **Pengujian Otomatis (Unit & Robolectric Tests)**:
   - Menjalankan `gradle :app:testDebugUnitTest` untuk `ExampleUnitTest` (enkripsi AES-256-GCM, derivasi kunci kredensial PBKDF2, TOTP 2FA, format `.xlsx`) dan `ExampleRobolectricTest` (SQLite Room offline, ekspor PDF, dan alur login/register E2EE).
   - Mengunggah laporan tes ke artifact `unit-test-reports`.
4. **Build & Upload Artifact APK**:
   - Menjalankan `gradle :app:assembleDebug --no-daemon`, menyalin APK ke `dist/WargaKos-debug.apk`, dan mengunggah artifact **`WargaKos-debug-apk`**.
   - Jika secret rilis (`KEYSTORE_B64`, `STORE_PASSWORD`, `KEY_PASSWORD`) diisi, workflow juga menjalankan `gradle :app:assembleRelease --no-daemon` dan mengunggah **`WargaKos-release-apk`**.
   - Jika Anda melakukan `push` tag versi (`v*`, misalnya `v1.0.0`), workflow otomatis menerbitkan **GitHub Release** berisi file `.apk` yang bisa langsung diunduh tanpa perlu mengekstrak ZIP artifact.

## 2. Cara Mengunduh APK dari GitHub

### Opsi A: Lewat Tab Actions (Setiap Kali Push)
1. Push kode ke GitHub (`git push`).
2. Buka repositori di browser lalu klik tab **Actions**.
3. Klik workflow run terbaru bernama **Android CI/CD Build & APK**.
4. Pada bagian **Artifacts** di bagian bawah halaman, klik **`WargaKos-debug-apk`** (atau **`WargaKos-release-apk`**) untuk mengunduh file `.apk`.

### Opsi B: Lewat GitHub Releases (Push Tag Versi)
1. Buat tag dan push ke GitHub:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
2. Setelah workflow selesai, buka tab **Releases** di repositori GitHub untuk mengunduh file `WargaKos-debug.apk` secara langsung.
