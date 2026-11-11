# HUD Overlay

Aplikasi overlay Android untuk live streaming. Tampilkan teks (HUD) di atas game atau app lain, dan atur posisinya lewat editor visual.

**Stack:** Kotlin, Jetpack Compose (editor), View + WindowManager (overlay), foreground service.
minSdk 28, targetSdk 34, compileSdk 35. AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9 (di CI).

## Fitur

- Editor overlay: geser teks, atur ukuran, opacity, warna, dan urutan layer.
- Tambah, edit, dan hapus elemen teks.
- Profil **Portrait** dan **Landscape** terpisah.
- Overlay tembus sentuhan (tap tetap sampai ke game).
- Perubahan di editor langsung terlihat di overlay yang sedang aktif.
- Notifikasi dengan aksi Jeda / Lanjut / Stop.
- Build APK debug otomatis lewat GitHub Actions.

## Cara pakai

1. Install APK lalu buka app.
2. Ketuk **Buka pengaturan izin**, lalu aktifkan **Tampil di atas aplikasi lain**.
3. Atur overlay di editor:
   - **+ Teks** untuk menambah elemen.
   - Seret elemen untuk memindah, ketuk untuk memilih.
   - Slider ukuran dan opacity, pilihan warna, dan tombol Edit / Naik / Turun / Hapus muncul saat elemen dipilih.
   - Ganti tab **Portrait** atau **Landscape** untuk mengatur masing-masing orientasi.
4. Ketuk **Mulai overlay**. Jeda, lanjut, dan stop bisa lewat notifikasi.

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

- `Model.kt`: data `HudItem` dan `HudLayout`, plus penyimpanan layout (JSON di SharedPreferences).
- `EditorScreen.kt`: editor Compose (kanvas, drag, slider, warna, layer).
- `HudView.kt`: menggambar elemen HUD di jendela overlay.
- `OverlayService.kt`: foreground service, jendela overlay, dan notifikasi.
- `MainActivity.kt`: layar utama, izin, dan tombol mulai/stop.

## Catatan teknis

- Posisi elemen disimpan sebagai pecahan layar (0..1), dan ukuran teks relatif terhadap sisi layar terpendek. Karena itu tampilan di editor dan di overlay sama proporsinya.
- Android 12 ke atas membatasi overlay tembus sentuhan ke opacity jendela maksimal 0.8, jadi tampilan sedikit lebih redup.
- Apakah overlay ikut ke-capture saat rekam atau live berbeda-beda per versi Android dan vendor. Tes di HP target sebelum live.

## Roadmap

- Overlay gambar/GIF
- Web overlay
- Preset layout
- Video dan MP3
- Uji kompatibilitas MediaProjection
