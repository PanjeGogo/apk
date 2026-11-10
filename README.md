# HUD Overlay

Aplikasi overlay Android (Kotlin + Jetpack Compose) untuk live streaming.

## Fitur saat ini

- Editor overlay: geser teks, atur ukuran, opacity, warna, dan urutan layer.
- Profil Portrait dan Landscape terpisah.
- Overlay tembus sentuhan (tap tetap sampai ke game).
- Perubahan di editor langsung terlihat di overlay yang sedang aktif.
- Notifikasi dengan aksi Jeda / Lanjut / Stop.
- Build APK debug lewat GitHub Actions.

## Build

Push ke `main` atau jalankan workflow **Android APK Build** manual (pilih branch yang diinginkan).
APK debug diunggah sebagai artifact `hud-overlay-debug-apk`.
Lokal / Codespaces: `gradle assembleDebug`.

## Izin

- **Tampil di atas aplikasi lain** (wajib).
- Notifikasi (Android 13+).

## Catatan

Di Android 12+, overlay tembus sentuhan dibatasi opacity jendela maksimal 0.8,
jadi tampilannya sedikit lebih redup. Visibilitas overlay di hasil capture beda-beda
per versi Android dan vendor. Tes di HP target sebelum live.

## Roadmap

- Overlay gambar/GIF
- Web overlay
- Preset layout
- Video dan MP3
- Uji kompatibilitas MediaProjection
