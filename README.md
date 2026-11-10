# WargaKos — Aplikasi Manajemen Data Warga Kos (Android Native Kotlin)

Aplikasi Android native berbasis **Kotlin** dan **Jetpack Compose (Material 3)** yang diporting dari skrip desktop Python `Data Warga Kos`, dilengkapi autentikasi Username & Password terenkripsi End-to-End, login sidik jari, 2FA, penyimpanan SQLite offline, sinkronisasi Cloud Firestore opsional, ekspor laporan resmi Excel & PDF, tema gelap, serta pipeline CI/CD GitHub Actions.

## Fitur Utama

1. **Daftar & Login Username/Password (E2EE) + Sidik Jari + 2FA**
   - **Form Daftar & Masuk Standar**: Menggunakan **Username** dan **Password** biasa tanpa wajib login Google.
   - **Sidik Jari / Biometrik (`BiometricPrompt`)**: Kunci enkripsi dibungkus menggunakan hardware **Android Keystore** untuk login instan via sidik jari/wajah.
   - **2-Factor Authentication (2FA TOTP RFC 6238)**: Kode OTP 6 digit yang kompatibel dengan Google Authenticator / Authy.
2. **Enkripsi End-to-End (E2EE AES-256-GCM)**
   - Kunci brankas diturunkan dari `username + password` menggunakan **PBKDF2WithHmacSHA256 (100.000 iterasi)**.
   - Seluruh kredensial akun dan data penghuni (nama pemilik kos, nama warga, nomor kamar, jumlah orang, alamat, status nikah/belum nikah, keterangan) dienkripsi **client-side** sebelum disimpan di SQLite lokal maupun Cloud Firestore.
3. **Database Offline SQLite (Room) & Sinkronisasi Cloud Firestore Opsional**
   - Seluruh operasi baca, tambah, ubah, dan hapus bekerja penuh secara **offline** di database SQLite lokal terenkripsi.
   - Sinkronisasi cloud real-time via **Cloud Firestore** dapat dihubungkan kapan saja dari tab **Keamanan**.
4. **Ekspor Laporan Resmi Excel (`.xlsx`) & PDF (`.pdf`)**
   - Mengikuti tata letak skrip referensi: header gabungan dua tingkat, minimal 40 baris bernomor, orientasi A3 Landscape, dan blok tanda tangan **Ketua RW. 01**, **Ketua RT. 01**, dan **Ketua RT. 02**.
   - Tersimpan otomatis ke direktori internal aplikasi serta `Downloads/WargaKos/` (Android 10+ tanpa izin penyimpanan berlebih).
5. **Berbagi Dokumen & Mode Gelap**
   - Bagikan file PDF/Excel via `FileProvider` atau ringkasan teks langsung ke WhatsApp, Telegram, Email, dan aplikasi pesan lainnya.
   - Dukungan **Tema Gelap (Dark Mode)** dengan pilihan Sistem, Terang, atau Gelap.

---

## Panduan Setup CI/CD GitHub Actions (`android.yml`)

Alur kerja CI/CD tersedia di `.github/workflows/android.yml` dan otomatis berjalan pada setiap `push` ke GitHub (branch mana pun atau tag `v*`), `pull_request`, maupun manual lewat tab **Actions → Run workflow**.

### 1. Cara Mengunduh File APK Setelah Push ke GitHub
1. Push repositori ini ke GitHub (`git push`).
2. Buka repositori di GitHub lalu klik tab **Actions**.
3. Pilih workflow run terbaru **Android CI/CD Build & APK**.
4. Gulir ke bagian **Artifacts** di bagian bawah halaman, lalu klik **`WargaKos-debug-apk`** untuk mengunduh file **`WargaKos-debug.apk`** (siap langsung diinstal ke HP Android).
5. *(Opsional)* Jika Anda push tag versi seperti `git tag v1.0.0 && git push origin v1.0.0`, file `.apk` juga otomatis tersedia di halaman **Releases** GitHub untuk diunduh langsung.

### 2. Konfigurasi Secrets di GitHub (Opsional / Untuk Release Build)
Buka **Settings → Secrets and variables → Actions → New repository secret**:

| Nama Secret | Wajib? | Keterangan |
|---|---|---|
| `GOOGLE_SERVICES_JSON_B64` | Opsional | Jika `app/google-services.json` tidak di-commit ke repo publik, encode file ke Base64 (`base64 -w 0 app/google-services.json`) dan simpan di sini. |
| `KEYSTORE_B64` | Opsional (Release) | File keystore `.jks` untuk build release dalam format Base64 (`base64 -w 0 my-upload-key.jks`). |
| `STORE_PASSWORD` | Opsional (Release) | Password keystore rilis. |
| `KEY_PASSWORD` | Opsional (Release) | Password alias kunci `upload`. |

> **Catatan:** Tanpa mengisi secret apa pun, workflow tetap otomatis menjalankan pengujian dan membangun **`WargaKos-debug.apk`** di setiap `push` ke GitHub.

### 3. Keamanan Firebase & Aturan Firestore
- Skema data terdokumentasi di `firebase-blueprint.json`.
- Aturan keamanan zero-trust di `firestore.rules` membatasi akses baca/tulis hanya ke pemilik UID terautentikasi (`/users/{userId}/warga_kos/{recordId}` dan `/users/{userId}/security/vault`).
- Uji aturan keamanan secara lokal dengan:
  ```bash
  FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
  ```
