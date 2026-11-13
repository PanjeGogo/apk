package com.pramstudio.hudoverlay

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ConfigArchive {
    fun export(context: Context, uri: Uri) {
        val layout = HudStore.load(context)
        val root = JSONObject()
            .put("format", "panje")
            .put("version", 1)
            .put("portrait", HudStore.encode(layout.portrait))
            .put("landscape", HudStore.encode(layout.landscape))
        val assets = linkedSetOf<String>()
        (layout.portrait + layout.landscape)
            .filter { it.type == ItemType.IMAGE }
            .forEach { item -> if (item.path.isNotBlank() && File(item.path).isFile) assets.add(item.path) }
        context.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("config.json"))
                zip.write(root.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                assets.forEachIndexed { index, path ->
                    val file = File(path)
                    zip.putNextEntry(ZipEntry("media/$index.img"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    (layout.portrait + layout.landscape).forEach { item ->
                        if (item.type == ItemType.IMAGE && item.path == path) {
                            val arrays = listOf(root.getJSONArray("portrait"), root.getJSONArray("landscape"))
                            arrays.forEach { array ->
                                for (i in 0 until array.length()) {
                                    val obj = array.getJSONObject(i)
                                    if (obj.optString("path") == path) obj.put("path", "media/$index.img")
                                }
                            }
                        }
                    }
                }
            }
        } ?: error("Tidak bisa membuat file tujuan")
    }

    fun import(context: Context, uri: Uri) {
        val entries = linkedMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && (entry.name == "config.json" || entry.name.startsWith("media/"))) {
                        entries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Tidak bisa membaca file")
        val raw = entries["config.json"]?.toString(Charsets.UTF_8) ?: error("config.json tidak ditemukan")
        val root = JSONObject(raw)
        require(root.optString("format") == "panje") { "Format file tidak dikenal" }
        val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
        listOf("portrait", "landscape").forEach { profile ->
            val arr = root.optJSONArray(profile) ?: return@forEach
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("type") == ItemType.IMAGE.name) {
                    val asset = obj.optString("path")
                    val bytes = entries[asset] ?: error("Asset gambar tidak ditemukan: $asset")
                    val destination = File(mediaDir, "panje_${System.nanoTime()}_${i}.img")
                    destination.writeBytes(bytes)
                    obj.put("path", destination.absolutePath)
                }
            }
        }
        val layout = HudLayout(
            HudStore.decode(root.optJSONArray("portrait")),
            HudStore.decode(root.optJSONArray("landscape"))
        )
        HudStore.save(context, layout)
    }
}
