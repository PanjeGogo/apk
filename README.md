# HUD Overlay

Aplikasi overlay Android untuk live streaming. Tampilkan teks dan gambar/GIF di atas game atau app lain, plus putar playlist musik dengan auto repeat.

**Stack:** Kotlin, Jetpack Compose (UI), View + WindowManager (overlay), foreground service, MediaPlayer.
minSdk 28, targetSdk 34, compileSdk 35. AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9 (di CI).

## Fitur

- **Overlay teks**: geser, atur ukuran, opacity, warna (18 pilihan), dan urutan layer.
- **Overlay gambar / GIF**: pilih file dari HP, GIF dan WebP animasi ikut bergerak di overlay. Preview editor menampilkan frame pertama.
- **Playlist musik**: tambah banyak lagu sekaligus, putar/jeda, lagu berikutnya/sebelumnya, ketuk lagu untuk memutarnya, atur volume, dan **auto repeat** (ulangi playlist dari awal). Lagu yang gagal dibuka dilewati.
- Profil **Portrait** dan **Landscape** terpisah.
- Overlay tembus sentuhan (tap tetap sampai ke game).
- Perubahan di editor langsung terlihat di overlay yang sedang aktif.
- Notifikasi dengan aksi Jeda/Lanjut overlay, Putar/Jeda musik, dan Stop.
- Build APK debug otomatis lewat GitHub Actions.

## Cara pakai

1. Install APK lalu buka app.
2. Ketuk **Mulai**. Kalau izin belum aktif, app menjelaskan dulu untuk apa izinnya, lalu membuka pengaturan **Tampil di atas aplikasi lain**.
3. Tab **Editor**:
   - **+ Teks** atau **+ Gambar / GIF** untuk menambah elemen.
   - Seret elemen untuk memindah, ketuk untuk memilih.
   - Panel bawah: teks, warna, ukuran (tombol - dan +), opacity, dan layer (Naik/Turun/Hapus).
   - Ganti tab **Portrait** atau **Landscape** untuk mengatur masing-masing orientasi.
4. Tab **Musik**: **+ Tambah lagu**, lalu ketuk tombol play. Memutar musik juga menyalakan overlay.
5. Stop overlay dan musik dengan tombol **Stop** di atas atau dari notifikasi.

## Download APK

1. Buka tab **Actions** di repo ini.
2. Pilih run **Android APK Build** terbaru yang bercentang hijau.
3. Buka **Summary**, scroll ke bawah sampai bagian **Artifacts**.
4. Unduh **hud-overlay-debug-apk** (berupa zip), ekstrak, lalu install `app-debug.apk`.

## Build

Push ke `main` atau jalankan workflow **Android APK Build** manual (pilih branch yang diinginkan).
APK debug diunggah sebagai artifact `hud-overlay-debug-apk`.

Lokal atau Codespaces:

```
gradle assembleDebug
```

Hasilnya ada di `app/build/outputs/apk/debug/`.

## Struktur proyek

Semua kode ada di `app/src/main/java/com/pramstudio/hudoverlay/`:

- `Model.kt`: data elemen (`HudItem`, `HudLayout`), penyimpanan layout dan playlist (JSON di SharedPreferences).
- `Media.kt`: import gambar, decode GIF/gambar, nama file audio.
- `MusicPlayer.kt`: pemutar playlist dengan auto repeat.
- `HudView.kt`: menggambar elemen teks dan gambar/GIF di jendela overlay.
- `OverlayService.kt`: foreground service, jendela overlay, musik, dan notifikasi.
- `Theme.kt`: warna dan komponen UI bersama.
- `EditorScreen.kt`: editor Compose.
- `MusicScreen.kt`: layar playlist.
- `MainActivity.kt`: top bar, tab, izin, dan tombol mulai/stop.

## Catatan teknis

- Posisi elemen disimpan sebagai pecahan layar (0..1). Ukuran teks dan lebar gambar relatif terhadap sisi layar terpendek, jadi proporsi di editor dan overlay sama.
- Gambar yang dipilih disalin ke penyimpanan internal app. File dihapus saat elemennya dihapus.
- File musik tidak disalin. App menyimpan izin akses permanen ke file yang dipilih, jadi kalau file dipindah atau dihapus, lagunya dilewati.
- Android 12 ke atas membatasi overlay tembus sentuhan ke opacity jendela maksimal 0.8, jadi tampilan sedikit lebih redup.
- Musik diputar sebagai audio media dan app mengizinkan audio playback capture. Apakah aplikasi streaming bisa menangkapnya bergantung pada aplikasi tersebut dan versi Android.
- Visibilitas overlay di hasil capture berbeda-beda per versi Android dan vendor. Tes di HP target sebelum live.

## Roadmap

- Web overlay
- Preset layout
- Video overlay
- Uji kompatibilitas MediaProjection
