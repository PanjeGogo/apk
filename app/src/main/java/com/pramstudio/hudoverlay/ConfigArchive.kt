package com.pramstudio.hudoverlay

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Proprietary Overlay aja project container.
 *
 * Layout:
 *   8-byte magic | 4-byte version | 12-byte GCM nonce |
 *   4-byte encrypted payload length | AES-256-GCM payload
 *
 * The encrypted payload is a compact binary record stream, not ZIP.
 */
object ConfigArchive {
    private val MAGIC = byteArrayOf(0x50, 0x41, 0x4E, 0x4A, 0x45, 0x50, 0x4A, 0x01)
    private const val FORMAT_VERSION = 1
    private const val MAX_FILE_BYTES = 128 * 1024 * 1024
    private const val HEADER_BYTES = 8 + 4 + 12 + 4
    private const val GCM_TAG_BYTES = 16
    private const val MAX_PAYLOAD_BYTES = MAX_FILE_BYTES - HEADER_BYTES - GCM_TAG_BYTES
    private const val MAX_ASSETS = 512
    private const val MAX_CONFIG_BYTES = 8 * 1024 * 1024

    // A no-password portable format needs the same embedded key in each app installation.
    // This prevents casual extraction; it is not intended to be cryptographically unbreakable
    // against someone reverse-engineering the APK itself.
    private fun encryptionKey(): SecretKeySpec {
        val fragments = listOf(
            "Overlay", "aja", "PanjeGogo", "Project", "Format", "Key", "2026"
        )
        val material = fragments.joinToString("|") + "|com.PanjeGoGo.AppOverlay|"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    fun export(context: Context, uri: Uri) {
        val layout = HudStore.load(context)
        val root = JSONObject()
            .put("format", "panje")
            .put("version", FORMAT_VERSION)
            .put("portrait", HudStore.encode(layout.portrait))
            .put("landscape", HudStore.encode(layout.landscape))

        val allItems = layout.portrait + layout.landscape
        val assetPaths = linkedSetOf<String>()
        allItems.filter { it.type == ItemType.IMAGE }.forEach { item ->
            if (item.path.isNotBlank() && File(item.path).isFile) assetPaths.add(item.path)
        }
        require(assetPaths.size <= MAX_ASSETS) { "Terlalu banyak aset gambar/GIF" }

        val assetBytes = linkedMapOf<String, ByteArray>()
        var totalAssetBytes = 0L
        assetPaths.forEachIndexed { index, path ->
            val file = File(path)
            require(file.length() in 1..MAX_PAYLOAD_BYTES.toLong()) {
                "Ukuran aset gambar/GIF tidak valid"
            }
            totalAssetBytes += file.length()
            require(totalAssetBytes <= MAX_PAYLOAD_BYTES) { "Total aset terlalu besar" }

            val assetId = "asset_$index"
            val bytes = file.readBytes()
            assetBytes[assetId] = bytes
            listOf("portrait", "landscape").forEach { profile ->
                val array = root.getJSONArray(profile)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    if (obj.optString("path") == path) obj.put("path", assetId)
                }
            }
        }

        val configBytes = root.toString().toByteArray(Charsets.UTF_8)
        require(configBytes.size <= MAX_CONFIG_BYTES) { "Konfigurasi terlalu besar" }

        val payloadBuffer = ByteArrayOutputStream()
        DataOutputStream(payloadBuffer).use { data ->
            data.writeInt(configBytes.size)
            data.write(configBytes)
            data.writeInt(assetBytes.size)
            assetBytes.forEach { (id, bytes) ->
                val idBytes = id.toByteArray(Charsets.UTF_8)
                data.writeShort(idBytes.size)
                data.write(idBytes)
                data.writeInt(bytes.size)
                data.write(bytes)
            }
        }
        require(payloadBuffer.size() <= MAX_PAYLOAD_BYTES) { "File project melebihi batas ukuran" }

        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), GCMParameterSpec(128, nonce))
        cipher.updateAAD(MAGIC + intBytes(FORMAT_VERSION))
        val encrypted = cipher.doFinal(payloadBuffer.toByteArray())

        context.contentResolver.openOutputStream(uri)?.use { output ->
            DataOutputStream(output).use { data ->
                data.write(MAGIC)
                data.writeInt(FORMAT_VERSION)
                data.write(nonce)
                data.writeInt(encrypted.size)
                data.write(encrypted)
                data.flush()
            }
        } ?: error("Tidak bisa membuat file tujuan")
    }

    fun import(context: Context, uri: Uri) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_FILE_BYTES) { "File .panje terlalu besar" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Tidak bisa membaca file")

        require(bytes.size in (HEADER_BYTES + GCM_TAG_BYTES)..MAX_FILE_BYTES) {
            "Ukuran file .panje tidak valid"
        }

        val input = DataInputStream(ByteArrayInputStream(bytes))
        val magic = ByteArray(MAGIC.size)
        input.readFully(magic)
        require(magic.contentEquals(MAGIC)) { "Bukan file project Overlay aja (.panje)" }

        val version = input.readInt()
        require(version == FORMAT_VERSION) { "Versi file .panje tidak didukung" }
        val nonce = ByteArray(12)
        input.readFully(nonce)
        val encryptedLength = input.readInt()
        require(encryptedLength in GCM_TAG_BYTES..MAX_FILE_BYTES && encryptedLength == input.available()) {
            "Struktur file .panje rusak"
        }
        val encrypted = ByteArray(encryptedLength)
        input.readFully(encrypted)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, nonce))
        cipher.updateAAD(MAGIC + intBytes(version))
        val plain = try {
            cipher.doFinal(encrypted)
        } catch (_: Exception) {
            error("File .panje tidak valid atau bukan dibuat oleh Overlay aja")
        }
        require(plain.size <= MAX_PAYLOAD_BYTES) { "Data project terlalu besar" }

        val payload = DataInputStream(ByteArrayInputStream(plain))
        val configLength = payload.readInt()
        require(configLength in 2..MAX_CONFIG_BYTES && configLength <= payload.available()) {
            "Data konfigurasi tidak valid"
        }
        val configBytes = ByteArray(configLength)
        payload.readFully(configBytes)
        val root = JSONObject(configBytes.toString(Charsets.UTF_8))
        require(root.optString("format") == "panje") { "Format project tidak dikenal" }
        require(root.optInt("version", -1) == FORMAT_VERSION) { "Versi konfigurasi tidak didukung" }
        require(root.optJSONArray("portrait") != null && root.optJSONArray("landscape") != null) {
            "Layout Portrait atau Landscape tidak ditemukan"
        }

        val assetCount = payload.readInt()
        require(assetCount in 0..MAX_ASSETS) { "Jumlah aset tidak valid" }
        val assets = linkedMapOf<String, ByteArray>()
        var totalAssetBytes = 0L
        repeat(assetCount) {
            val idLength = payload.readUnsignedShort()
            require(idLength in 1..128 && idLength <= payload.available()) { "Data aset rusak" }
            val idBytes = ByteArray(idLength)
            payload.readFully(idBytes)
            val id = idBytes.toString(Charsets.UTF_8)
            require(id.matches(Regex("asset_[0-9]+")) && !assets.containsKey(id)) {
                "ID aset tidak valid atau duplikat"
            }
            val assetLength = payload.readInt()
            require(assetLength in 1..MAX_FILE_BYTES && assetLength <= payload.available()) {
                "Ukuran aset tidak valid"
            }
            totalAssetBytes += assetLength
            require(totalAssetBytes <= MAX_PAYLOAD_BYTES) { "Total aset terlalu besar" }
            val asset = ByteArray(assetLength)
            payload.readFully(asset)
            assets[id] = asset
        }
        require(payload.available() == 0) { "Ada data tambahan yang tidak dikenal" }

        // Decode and validate both layouts before touching stored media or preferences.
        val portraitItems = HudStore.decode(root.getJSONArray("portrait"))
        val landscapeItems = HudStore.decode(root.getJSONArray("landscape"))
        (portraitItems + landscapeItems)
            .filter { it.type == ItemType.IMAGE }
            .forEach { item ->
                require(assets.containsKey(item.path)) { "Aset gambar/GIF tidak ditemukan" }
            }

        val mediaDir = File(context.filesDir, "media").apply {
            require(exists() || mkdirs()) { "Tidak bisa membuat folder media" }
        }
        val stagedFiles = mutableListOf<File>()
        val committedFiles = mutableListOf<File>()
        val resolvedPaths = mutableMapOf<String, String>()

        try {
            assets.forEach { (assetId, asset) ->
                val temp = File.createTempFile("panje_stage_", ".tmp", mediaDir)
                stagedFiles.add(temp)
                FileOutputStream(temp).use { stream ->
                    stream.write(asset)
                    stream.fd.sync()
                }
                val destination = File(mediaDir, "panje_${UUID.randomUUID()}.img")
                require(temp.renameTo(destination)) { "Gagal menyimpan aset project" }
                stagedFiles.remove(temp)
                committedFiles.add(destination)
                resolvedPaths[assetId] = destination.absolutePath
            }

            fun resolveItems(items: List<HudItem>): List<HudItem> =
                items.map { item ->
                    if (item.type == ItemType.IMAGE) {
                        item.copy(path = resolvedPaths[item.path]
                            ?: error("Aset gambar/GIF tidak ditemukan"))
                    } else item
                }

            val layout = HudLayout(resolveItems(portraitItems), resolveItems(landscapeItems))
            HudStore.save(context, layout)
        } catch (error: Exception) {
            stagedFiles.forEach { it.delete() }
            committedFiles.forEach { it.delete() }
            throw error
        }
    }

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(),
        (value ushr 8).toByte(), value.toByte()
    )
}
