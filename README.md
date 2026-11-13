# Overlay aja

Aplikasi Android native untuk menampilkan overlay teks, gambar/GIF, dan halaman WEB di atas game atau aplikasi lain. Termasuk playlist musik yang berjalan melalui foreground service.

**Developer:** PanjeGogo  
**Application ID:** `com.PanjeGoGo.AppOverlay`  
**Package namespace:** `com.pramstudio.hudoverlay`  
**Current app version:** `2.2.1` (`versionCode 8`)  
**Technology:** Kotlin, Jetpack Compose, Android View/WindowManager, MediaPlayer  
**Minimum Android:** API 28  
**Target Android:** API 34  
**Compile SDK:** 35

## Fitur saat ini

### Editor overlay
- Tiga tab utama: **Editor**, **Musik**, dan **Pengaturan**. Tab, profil, dan Patch Notes tidak ter-reset saat layar diputar.
- Profil layout **Portrait** dan **Landscape** disimpan terpisah.
- Elemen teks, gambar/GIF, dan WEB dapat ditambah, dipilih, dipindah, diatur, serta diurutkan berdasarkan layer.
- Perubahan editor disimpan otomatis dan langsung diterapkan ke overlay aktif.
- Teks: font family (termasuk Sans Serif Bold dan Serif Bold), weight (bobot huruf asli Android), italic, underline, alignment, warna HEX, outline, shadow, opacity, dan ukuran. Teks dapat terdiri dari beberapa baris; alignment berlaku untuk teks multi-baris. Preview editor menampilkan warna, font, bobot, italic, underline, dan alignment; outline dan shadow hanya terlihat di overlay sebenarnya.
- Gambar/GIF: file dipilih dari perangkat dan disalin ke penyimpanan internal aplikasi. GIF/WebP animasi didukung; preview editor menggunakan frame pertama.
- Ukuran gambar/GIF menerima input angka desimal tanpa batas maksimum/minimum tetap. Nilai 0 menyembunyikan elemen; ukuran ekstrem dibatasi secara aman saat dirender.
- WEB: URL, JavaScript aktif, pilihan rasio 16:9, 9:16, 1:1, 4:3, 3:4, dan 21:9, serta pengaturan sentuhan.
- Overlay utama selalu touch-through agar interaksi diteruskan ke aplikasi/game di bawahnya. WEB yang diatur **Touchable** memakai jendela kecil sendiri seukuran areanya (selalu tampil di atas elemen lain): hanya area web yang menerima sentuhan, area di luarnya tetap tembus ke game.

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
- Pilihan warna aksen aplikasi; Orange menjadi warna default. Status bar mengikuti warna aksen.
- Import dan export seluruh konfigurasi overlay menggunakan format project khusus `.panje`.

## Format project `.panje`

- Ekstensi project: `.panje`.
- Format native binary khusus, bukan ZIP dan bukan JSON polos.
- Tidak meminta password.
- Menggunakan header/magic, versi format, nonce, dan payload AES-256-GCM untuk integritas serta perlindungan data.
- Menyimpan konfigurasi Portrait dan Landscape, properti elemen (termasuk teks dan WEB), serta aset gambar/GIF yang digunakan.
- Playlist musik sengaja tidak dimasukkan.
- Import memvalidasi struktur, versi, autentikasi payload, layout, dan aset sebelum menyimpan konfigurasi. Import mengganti konfigurasi overlay yang tersimpan; gambar dari konfigurasi lama yang tidak dipakai lagi dihapus.
- Implementasi membatasi ukuran file arsip sampai 128 MiB dan jumlah aset sampai 512 untuk menghindari konsumsi resource yang tidak terkendali. Jika memori perangkat tidak cukup, export/import menampilkan pesan gagal.
- Import/export resmi dilakukan melalui aplikasi Overlay aja.

**Catatan keamanan:** format ini tidak memakai password dan kunci dekripsi ditanam di aplikasi agar file dapat dipindahkan antar-instalasi Overlay aja. Ini menghalangi pembacaan langsung dengan extractor arsip umum, tetapi bukan proteksi absolut terhadap reverse engineering APK. URL WEB (yang sering berisi token alert donasi) ikut tersimpan di file `.panje`, jadi jangan membagikan file ini ke pihak yang tidak dipercaya.

## Struktur source code

Semua source Kotlin utama berada di `app/src/main/java/com/pramstudio/hudoverlay/`:

| File | Tanggung jawab |
|---|---|
| `MainActivity.kt` | Activity utama, top bar, tab, izin, dan Patch Notes |
| `EditorScreen.kt` | Editor Compose untuk teks, gambar/GIF, WEB, ukuran, opacity, dan layer |
| `HudView.kt` | Rendering elemen overlay pada window, serta helper WebView overlay |
| `OverlayService.kt` | Foreground service, WindowManager (jendela utama + jendela WEB touchable), notifikasi, dan sinkronisasi perubahan |
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

**Tanda tangan APK:** workflow memakai debug keystore tetap dari `.github/debug-keystore.b64` (keystore debug standar, bukan rahasia), sehingga setiap APK hasil CI memiliki tanda tangan yang sama dan bisa menimpa versi yang sudah terpasang tanpa uninstall. APK yang dibangun sebelum keystore ini ditambahkan (versi 2.2.0 dan sebelumnya) memakai kunci acak yang berbeda, jadi perpindahan pertama ke 2.2.1 memerlukan uninstall sekali (export konfigurasi `.panje` dulu). Untuk rilis publik, ganti dengan keystore rilis privat lewat GitHub Secrets.

Build lokal:

```bash
gradle assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Build lokal memakai debug keystore mesin Anda sendiri, jadi tanda tangannya berbeda dari APK hasil CI.

## Roadmap

Lihat [ROADMAP.md](ROADMAP.md) untuk status fitur, prioritas stabilisasi, dan rencana versi berikutnya.

## Catatan kompatibilitas

- Android 12+ membatasi touch-through overlay berdasarkan opacity window; jendela utama memakai opacity 0.8 sehingga seluruh overlay teks/gambar maksimal 80% pekat di Android 12+.
- Perilaku overlay, cutout, izin, playback, dan visibilitas saat screen recording dapat berbeda menurut versi Android serta vendor perangkat.
- Audio playback capture dan visibilitas overlay dalam hasil capture bergantung pada kebijakan aplikasi perekam/streaming dan versi Android.
- Uji pada perangkat target sebelum dipakai untuk live streaming.
