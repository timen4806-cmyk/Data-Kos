# WargaKos — Aplikasi Manajemen Data Warga Kos (Android Native Kotlin)

Aplikasi Android native berbasis **Kotlin** dan **Jetpack Compose (Material 3)** yang diporting dari skrip desktop Python `Data Warga Kos`, dilengkapi autentikasi cloud, enkripsi end-to-end, penyimpanan SQLite offline, ekspor laporan resmi Excel & PDF, serta pipeline CI/CD GitHub Actions.

## Fitur Utama

1. **Autentikasi Berlapis (Firebase Auth + 2FA + Biometrik)**
   - **Langkah 1**: Login aman menggunakan **Google Sign-In (Jetpack Credential Manager)** & Firebase Auth.
   - **Langkah 2**: Kunci brankas menggunakan **Sidik Jari / Pengenal Wajah (`BiometricPrompt`)**, **PIN/Sandi Brankas**, dan opsional **2FA Authenticator (TOTP RFC 6238 6-digit)** yang kompatibel dengan Google Authenticator / Authy.
2. **Enkripsi End-to-End (E2EE AES-256-GCM)**
   - Kunci brankas diturunkan dari sandi pengguna menggunakan **PBKDF2WithHmacSHA256 (100.000 iterasi)** dan dibungkus secara lokal dengan **Android Keystore**.
   - Seluruh data penghuni (nama pemilik kos, nama warga, nomor kamar, jumlah orang, alamat, status nikah/belum nikah, keterangan) dienkripsi **client-side** sebelum disimpan di SQLite lokal maupun Cloud Firestore.
3. **Database Offline SQLite (Room) & Sinkronisasi Real-Time Firestore**
   - Data tetap dapat dibaca, ditambah, diubah, dan dihapus saat perangkat **offline**.
   - Perubahan offline ditandai sebagai tertunda (`PENDING_UPLOAD` / `PENDING_DELETE`) dan otomatis tersinkronisasi saat koneksi internet kembali.
4. **Ekspor Laporan Resmi Excel (`.xlsx`) & PDF (`.pdf`)**
   - Mengikuti tata letak skrip referensi: header gabungan dua tingkat, minimal 40 baris bernomor, orientasi A3 Landscape, dan blok tanda tangan **Ketua RW. 01**, **Ketua RT. 01**, dan **Ketua RT. 02**.
   - Tersimpan otomatis ke direktori internal aplikasi serta `Downloads/WargaKos/` (Android 10+ tanpa izin penyimpanan berlebih).
5. **Berbagi Dokumen & Mode Gelap**
   - Bagikan file PDF/Excel via `FileProvider` atau ringkasan teks langsung ke WhatsApp, Telegram, Email, dan aplikasi pesan lainnya.
   - Dukungan **Tema Gelap (Dark Mode)** dengan pilihan Sistem, Terang, atau Gelap.

---

## Panduan Setup CI/CD GitHub Actions (`android.yml`)

Alur kerja CI/CD sudah tersedia di `.github/workflows/android.yml` dan otomatis berjalan pada setiap `push` ke branch `main`/`master`, `pull_request`, atau dijalankan manual lewat tab **Actions → Run workflow**.

### 1. Cara Mengunduh File APK dari GitHub
1. Push repositori ini ke GitHub (`main` atau `master`).
2. Buka tab **Actions** pada repositori GitHub Anda.
3. Pilih workflow **Android CI/CD Build & APK** yang sudah selesai (status hijau).
4. Gulir ke bagian **Artifacts** di halaman ringkasan run, lalu klik **`WargaKos-debug-apk`** untuk mengunduh file `.apk`.

### 2. Konfigurasi Secrets di GitHub (Opsional / Untuk Release Build)
Buka **Settings → Secrets and variables → Actions → New repository secret**:

| Nama Secret | Wajib? | Keterangan |
|---|---|---|
| `GOOGLE_SERVICES_JSON_B64` | Opsional | Jika `app/google-services.json` tidak di-commit ke repo publik, encode file ke Base64 (`base64 -w 0 app/google-services.json`) dan simpan di sini. |
| `KEYSTORE_B64` | Opsional (Release) | File keystore `.jks` untuk rilis Play Store dalam format Base64 (`base64 -w 0 my-upload-key.jks`). |
| `STORE_PASSWORD` | Opsional (Release) | Password keystore rilis. |
| `KEY_PASSWORD` | Opsional (Release) | Password alias kunci `upload`. |

> **Catatan:** Untuk `assembleDebug`, jika `KEYSTORE_B64` tidak diisi, workflow otomatis membuat atau memakai `debug.keystore` sementara sehingga **APK Debug selalu bisa diunduh setelah push**.

### 3. Keamanan Firebase & Aturan Firestore
- Skema data terdokumentasi di `firebase-blueprint.json`.
- Aturan keamanan zero-trust di `firestore.rules` membatasi akses baca/tulis hanya ke pemilik UID terautentikasi (`/users/{userId}/warga_kos/{recordId}` dan `/users/{userId}/security/vault`).
- Uji aturan keamanan secara lokal dengan:
  ```bash
  FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
  ```
