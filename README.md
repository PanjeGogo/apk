# Overlay aja

Aplikasi Android native untuk menampilkan overlay teks, gambar/GIF, dan halaman WEB di atas game atau aplikasi lain. Termasuk playlist musik yang berjalan melalui foreground service.

**Developer:** PanjeGogo  
**Application ID:** `com.PanjeGoGo.AppOverlay`  
**Package namespace:** `com.pramstudio.hudoverlay`  
**Current app version:** `2.2.0` (`versionCode 7`)  
**Technology:** Kotlin, Jetpack Compose, Android View/WindowManager, MediaPlayer  
**Minimum Android:** API 28  
**Target Android:** API 34  
**Compile SDK:** 35

## Fitur saat ini

### Editor overlay
- Tiga tab utama: **Editor**, **Musik**, dan **Pengaturan**.
- Profil layout **Portrait** dan **Landscape** disimpan terpisah.
- Elemen teks, gambar/GIF, dan WEB dapat ditambah, dipilih, dipindah, diatur, serta diurutkan berdasarkan layer.
- Perubahan editor disimpan otomatis dan langsung diterapkan ke overlay aktif.
- Teks: font family, weight, italic, underline, alignment, warna HEX, outline, shadow, opacity, dan ukuran.
- Gambar/GIF: file dipilih dari perangkat dan disalin ke penyimpanan internal aplikasi. GIF/WebP animasi didukung; preview editor menggunakan frame pertama.
- Ukuran gambar/GIF menerima input angka desimal tanpa batas maksimum/minimum tetap. Nilai 0 menyembunyikan elemen; ukuran ekstrem tetap dibatasi oleh kemampuan layar dan perangkat saat dirender.
- WEB: URL, JavaScript aktif, pilihan rasio 16:9, 9:16, 1:1, 4:3, 3:4, dan 21:9, serta pengaturan sentuhan.
- Overlay dapat dibuat touch-through agar interaksi diteruskan ke aplikasi/game di bawahnya. WEB dapat mengaktifkan interaksi sentuhan sesuai pengaturannya.

### Musik
- Playlist multi-lagu, tambah beberapa lagu, pilih lagu, next/previous, play/pause.
- Volume dan auto-repeat playlist.
- Lagu menggunakan content URI dan izin akses permanen; file musik tidak disalin ke arsip project.
- Kontrol musik juga tersedia melalui notifikasi foreground service.

### Service dan izin Android
- Overlay ditampilkan melalui foreground service dan WindowManager.
- Notifikasi menyediakan aksi jeda/lanjut overlay, play/pause musik, dan stop.
- Aplikasi meminta izin **Display over other apps** dan izin notifikasi pada Android yang membutuhkannya.
- Patch Notes ditampilkan setiap kali aplikasi dibuka.

### Pengaturan aplikasi
- Identitas Overlay aja / PanjeGogo.
- Pilihan warna aksen aplikasi; Orange menjadi warna default.
- Import dan export seluruh konfigurasi overlay menggunakan format project khusus `.panje`.

## Format project `.panje`

- Ekstensi project: `.panje`.
- Format native binary khusus, bukan ZIP dan bukan JSON polos.
- Tidak meminta password.
- Menggunakan header/magic, versi format, nonce, dan payload AES-256-GCM untuk integritas serta perlindungan data.
- Menyimpan konfigurasi Portrait dan Landscape, properti elemen (termasuk teks dan WEB), serta aset gambar/GIF yang digunakan.
- Playlist musik sengaja tidak dimasukkan.
- Import memvalidasi struktur, versi, autentikasi payload, layout, dan aset sebelum menyimpan konfigurasi. Import mengganti konfigurasi overlay yang tersimpan.
- Implementasi membatasi ukuran file arsip sampai 128 MiB dan jumlah aset sampai 512 untuk menghindari konsumsi resource yang tidak terkendali.
- Import/export resmi dilakukan melalui aplikasi Overlay aja.

**Catatan keamanan:** format ini tidak memakai password dan kunci dekripsi ditanam di aplikasi agar file dapat dipindahkan antar-instalasi Overlay aja. Ini menghalangi pembacaan langsung dengan extractor arsip umum, tetapi bukan proteksi absolut terhadap reverse engineering APK.

## Struktur source code

Semua source Kotlin utama berada di `app/src/main/java/com/pramstudio/hudoverlay/`:

| File | Tanggung jawab |
|---|---|
| `MainActivity.kt` | Activity utama, top bar, tab, izin, dan Patch Notes |
| `EditorScreen.kt` | Editor Compose untuk teks, gambar/GIF, WEB, ukuran, opacity, dan layer |
| `HudView.kt` | Rendering elemen overlay pada window |
| `OverlayService.kt` | Foreground service, WindowManager, notifikasi, dan sinkronisasi perubahan |
| `Model.kt` | Model HUD, penyimpanan layout, playlist, dan serialisasi data |
| `Media.kt` | Pemilihan/pemrosesan media dan aset |
| `MusicPlayer.kt` | Playback playlist, volume, dan repeat |
| `MusicScreen.kt` | UI playlist musik |
| `SettingsScreen.kt` | Identitas aplikasi, warna aksen, import/export project |
| `ConfigArchive.kt` | Pembacaan dan penulisan arsip binary `.panje` |
| `Theme.kt` | Tema, warna, dan komponen UI bersama |

File build utama: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, dan `app/build.gradle.kts`.  
Workflow CI: `.github/workflows/android.yml`.

## Build APK

GitHub Actions workflow bernama **Android APK Build** berjalan saat push ke `main` atau `compose-editor`, dan dapat dijalankan manual.

1. Buka tab **Actions**.
2. Pilih workflow **Android APK Build**.
3. Buka run yang berhasil (centang hijau).
4. Unduh artifact `hud-overlay-debug-apk`.
5. Ekstrak ZIP dan install `app-debug.apk`.

Build lokal:

```bash
gradle assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Roadmap

Lihat [ROADMAP.md](ROADMAP.md) untuk status fitur, prioritas stabilisasi, dan rencana versi berikutnya.

## Catatan kompatibilitas

- Android 12+ membatasi touch-through overlay berdasarkan opacity window; service memakai nilai window yang sesuai batas platform.
- Perilaku overlay, cutout, izin, playback, dan visibilitas saat screen recording dapat berbeda menurut versi Android serta vendor perangkat.
- Audio playback capture dan visibilitas overlay dalam hasil capture bergantung pada kebijakan aplikasi perekam/streaming dan versi Android.
- Uji pada perangkat target sebelum dipakai untuk live streaming.
