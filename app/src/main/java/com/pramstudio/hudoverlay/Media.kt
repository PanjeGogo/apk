package com.pramstudio.hudoverlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Helper file media: import gambar, decode GIF/gambar, nama file. */
object Media {
    private const val MAX_SIDE = 1280

    private fun mediaDir(ctx: Context): File =
        File(ctx.filesDir, "media").apply { mkdirs() }

    /** Salin gambar pilihan user ke penyimpanan internal supaya overlay selalu bisa membacanya. */
    fun importImage(ctx: Context, uri: Uri): String? {
        return try {
            val dest = File(mediaDir(ctx), System.nanoTime().toString() + ".img")
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { src ->
                dest.outputStream().use { out -> src.copyTo(out) }
            }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Drawable untuk overlay. GIF/WebP animasi menjadi AnimatedImageDrawable. */
    fun decodeDrawable(path: String): Drawable? {
        return try {
            val source = ImageDecoder.createSource(File(path))
            ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val big = max(w, h)
                if (big > MAX_SIDE) {
                    val f = MAX_SIDE.toFloat() / big
                    decoder.setTargetSize(
                        (w * f).roundToInt().coerceAtLeast(1),
                        (h * f).roundToInt().coerceAtLeast(1)
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Bitmap statis (frame pertama) untuk preview di editor. */
    fun decodePreview(path: String, maxSide: Int = 512): Bitmap? {
        return try {
            val source = ImageDecoder.createSource(File(path))
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val big = max(w, h)
                if (big > maxSide) {
                    val f = maxSide.toFloat() / big
                    decoder.setTargetSize(
                        (w * f).roundToInt().coerceAtLeast(1),
                        (h * f).roundToInt().coerceAtLeast(1)
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Hapus file gambar kalau sudah tidak dipakai elemen mana pun. */
    fun deleteIfUnused(path: String, layout: HudLayout) {
        if (path.isEmpty()) return
        val used = (layout.portrait + layout.landscape)
            .any { it.type == ItemType.IMAGE && it.path == path }
        if (!used) {
            try {
                File(path).delete()
            } catch (e: Exception) {
                // abaikan
            }
        }
    }

    fun displayName(ctx: Context, uri: Uri): String {
        try {
            val cursor = ctx.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        } catch (e: Exception) {
            // pakai nama cadangan
        }
        return uri.lastPathSegment ?: "Lagu"
    }
}
