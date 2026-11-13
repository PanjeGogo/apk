# Overlay aja — Project Roadmap

Dokumen ini adalah acuan roadmap utama proyek Overlay aja. Status fitur menggambarkan implementasi yang ada di source branch saat dokumen ini diperbarui; status build CI tidak menggantikan pengujian langsung di perangkat Android.

## Identitas proyek

- Nama: Overlay aja
- Developer: PanjeGogo
- Application ID: `com.PanjeGoGo.AppOverlay`
- Namespace: `com.pramstudio.hudoverlay`
- Versi aplikasi saat ini: `2.2.0` (versionCode 7)
- Bahasa/platform: Kotlin / Android
- UI: Jetpack Compose
- Overlay: Android WindowManager + foreground service

## Status fitur

| Area | Fitur | Status |
|---|---|---|
| App shell | Tab Editor, Musik, Pengaturan | Selesai |
| App shell | Identitas Overlay aja / PanjeGogo | Selesai |
| App shell | Warna aksen aplikasi dengan Orange sebagai default | Selesai |
| App shell | Patch Notes tampil setiap aplikasi dibuka | Selesai |
| Editor | Profil Portrait dan Landscape terpisah | Selesai |
| Editor | Tambah, pilih, geser, dan hapus elemen | Selesai |
| Editor | Pengaturan urutan layer | Selesai |
| Editor teks | Font family, weight, italic, underline, alignment | Selesai |
| Editor teks | Warna HEX, opacity, outline, dan shadow | Selesai |
| Editor gambar | Import gambar dan GIF dari perangkat | Selesai |
| Editor gambar | GIF/WebP animasi pada overlay | Selesai |
| Editor gambar | Input ukuran desimal tanpa batas maksimum/minimum tetap | Selesai di source; uji ekstrem di perangkat |
| WEB overlay | URL dan JavaScript aktif | Selesai |
| WEB overlay | Rasio 16:9, 9:16, 1:1, 4:3, 3:4, 21:9 | Selesai |
| WEB overlay | Pengaturan interaksi/touch-through | Selesai di source; uji per perangkat |
| Musik | Playlist multi-lagu, play/pause, next/previous | Selesai |
| Musik | Volume dan auto-repeat | Selesai |
| Service | Foreground notification dan aksi kontrol | Selesai |
| Service | Update overlay langsung dari perubahan editor | Selesai |
| Project | Export/import seluruh konfigurasi Portrait dan Landscape sebagai `.panje` | Selesai di source |
| Project | Aset gambar/GIF ikut disimpan dalam `.panje` | Selesai di source |
| Project | Playlist musik tidak ikut disimpan dalam `.panje` | Sesuai keputusan desain |
| Project | Validasi format, versi, payload, dan aset | Selesai di source |
| CI | Build debug APK melalui GitHub Actions | Workflow tersedia; periksa status run terbaru |

## Spesifikasi arsip `.panje`

Keputusan desain yang harus dipertahankan:

1. Tidak meminta password.
2. File menggunakan ekstensi `.panje` dan format binary khusus, bukan ZIP/JSON polos.
3. Import resmi dilakukan dari aplikasi Overlay aja.
4. Layout Portrait dan Landscape, properti setiap elemen, URL WEB, dan aset gambar/GIF dikemas bersama.
5. Playlist musik tidak dimasukkan.
6. Import harus memvalidasi file sebelum mengganti konfigurasi yang sedang tersimpan.
7. Jika import gagal, konfigurasi lama harus tetap utuh dan file sementara hasil import harus dibersihkan.

Implementasi saat ini memakai AES-256-GCM dengan kunci yang ditanam di aplikasi. Karena tidak ada password dan kunci harus tersedia pada instalasi aplikasi lain, proteksi ini bukan jaminan anti-reverse-engineering absolut. Batas keselamatan implementasi saat ini: arsip maksimum 128 MiB dan maksimum 512 aset.

## Prioritas berikutnya

### P0 — Stabilitas dan verifikasi perangkat

- [ ] Install APK hasil workflow terbaru pada perangkat Android target.
- [ ] Uji izin overlay, mulai/jeda/lanjut/stop, dan notifikasi.
- [ ] Uji orientasi Portrait ↔ Landscape ketika overlay aktif.
- [ ] Uji tambah/edit/hapus teks dan gambar/GIF; pastikan perubahan langsung terlihat.
- [ ] Uji ukuran gambar/GIF: 0, angka desimal, nilai besar, dan nilai ekstrem; pastikan editor tidak crash dan perilaku rendering dapat dipahami.
- [ ] Uji WEB: setiap rasio, URL, JavaScript, transparansi, dan mode sentuhan.
- [ ] Uji playlist: izin URI, next/previous, repeat, volume, dan perilaku saat service dihentikan.
- [ ] Uji export/import `.panje` dengan layout Portrait + Landscape dan beberapa aset gambar/GIF.
- [ ] Uji file rusak, versi tidak didukung, aset hilang, dan arsip terlalu besar; konfigurasi lama harus tetap aman.
- [ ] Uji apakah WEB URL dan seluruh properti teks tetap sama setelah round-trip export → import.
- [ ] Catat bug beserta perangkat, versi Android, langkah reproduksi, dan log yang relevan.

### P1 — Preset layout

- [ ] Rancang penyimpanan beberapa preset layout.
- [ ] Tambah aksi simpan, ganti nama, pilih, dan hapus preset.
- [ ] Pastikan preset menyimpan profil Portrait dan Landscape beserta referensi aset.
- [ ] Uji kompatibilitas preset dengan format project `.panje`.

### P2 — Video overlay

- [ ] Tentukan format video yang didukung dan batas resource.
- [ ] Rancang kontrol play/pause, posisi, ukuran, opacity, dan layer.
- [ ] Evaluasi decoder dan konsumsi baterai/RAM pada perangkat target.
- [ ] Tambahkan video ke format project hanya setelah strategi aset dan kompatibilitas ditetapkan.

### P3 — Kompatibilitas MediaProjection / screen capture

- [ ] Uji visibilitas overlay pada MediaProjection dan aplikasi perekam/streaming populer.
- [ ] Dokumentasikan batasan Android dan vendor perangkat.
- [ ] Jangan mengasumsikan semua aplikasi perekam menangkap overlay atau audio dengan cara yang sama.

## Kriteria penerimaan rilis

- Build GitHub Actions berhasil.
- APK dapat di-install dan dibuka pada perangkat target.
- Tidak ada crash pada alur utama Editor, Musik, Pengaturan, dan service.
- Perubahan layout tersimpan setelah aplikasi ditutup dan dibuka kembali.
- Export/import `.panje` lolos pengujian round-trip dan file rusak tidak merusak konfigurasi lama.
- Batasan perangkat dan fitur yang belum selesai dijelaskan pada release notes.

## Aturan kontribusi dan kesinambungan

- `main` adalah branch utama dan sumber rilis.
- Implementasi fitur dilakukan pada branch kerja, lalu digabung ke `main` setelah build dan pemeriksaan.
- Jangan menghapus fitur yang sudah ada saat memperbaiki fitur lain.
- Perbarui README dan roadmap ketika perilaku atau status fitur berubah.
- Jangan menandai fitur sebagai selesai hanya karena source code sudah ditulis; bedakan status implementasi, build CI, dan pengujian perangkat.
