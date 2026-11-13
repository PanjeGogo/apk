package com.pramstudio.hudoverlay

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
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
    private val MAGIC = byteArrayOf(0x50, 0x41, 0x4E, 0x4A, 0x45, 0x50, 0x4A, 0x01) // PANJEPJ + format marker
    private const val FORMAT_VERSION = 1
    private const val MAX_FILE_BYTES = 128 * 1024 * 1024
    private const val MAX_ASSETS = 512
    private const val MAX_CONFIG_BYTES = 8 * 1024 * 1024

    // App-specific key material. Obfuscation can deter casual inspection, but an APK
    // containing a no-password key cannot provide absolute protection from reverse engineering.
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

        val assetBytes = linkedMapOf<String, ByteArray>()
        assetPaths.forEachIndexed { index, path ->
            val assetId = "asset_$index"
            val bytes = File(path).readBytes()
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
            }
        } ?: error("Tidak bisa membuat file tujuan")
    }

    fun import(context: Context, uri: Uri) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Tidak bisa membaca file")
        require(bytes.size in (MAGIC.size + 4 + 12 + 4 + 16)..MAX_FILE_BYTES) {
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
        require(encryptedLength in 16..MAX_FILE_BYTES && encryptedLength == input.available()) {
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

        val payload = DataInputStream(ByteArrayInputStream(plain))
        val configLength = payload.readInt()
        require(configLength in 2..MAX_CONFIG_BYTES && configLength <= payload.available()) {
            "Data konfigurasi tidak valid"
        }
        val configBytes = ByteArray(configLength)
        payload.readFully(configBytes)
        val root = JSONObject(configBytes.toString(Charsets.UTF_8))
        require(root.optString("format") == "panje") { "Format project tidak dikenal" }

        val assetCount = payload.readInt()
        require(assetCount in 0..MAX_ASSETS) { "Jumlah aset tidak valid" }
        val assets = linkedMapOf<String, ByteArray>()
        repeat(assetCount) {
            val idLength = payload.readUnsignedShort()
            require(idLength in 1..128 && idLength <= payload.available()) { "Data aset rusak" }
            val idBytes = ByteArray(idLength)
            payload.readFully(idBytes)
            val id = idBytes.toString(Charsets.UTF_8)
            require(id.matches(Regex("asset_[0-9]+"))) { "ID aset tidak valid" }
            val assetLength = payload.readInt()
            require(assetLength in 1..MAX_FILE_BYTES && assetLength <= payload.available()) {
                "Ukuran aset tidak valid"
            }
            val asset = ByteArray(assetLength)
            payload.readFully(asset)
            assets[id] = asset
        }
        require(payload.available() == 0) { "Ada data tambahan yang tidak dikenal" }

        val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
        listOf("portrait", "landscape").forEach { profile ->
            val array = root.optJSONArray(profile) ?: return@forEach
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("type") == ItemType.IMAGE.name) {
                    val assetId = obj.optString("path")
                    val asset = assets[assetId] ?: error("Aset gambar/GIF tidak ditemukan")
                    val destination = File(mediaDir, "panje_${System.nanoTime()}_${i}.img")
                    destination.writeBytes(asset)
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

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(),
        (value ushr 8).toByte(), value.toByte()
    )
}
