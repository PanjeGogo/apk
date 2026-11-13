package com.pramstudio.hudoverlay

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class Profile { PORTRAIT, LANDSCAPE }

enum class ItemType { TEXT, IMAGE, WEB }

/**
 * Satu elemen overlay (teks atau gambar/GIF).
 * x, y  : pojok kiri atas, pecahan 0..1 dari lebar/tinggi layar.
 * size  : teks = ukuran font, gambar = lebar. Keduanya pecahan sisi layar terpendek.
 * path  : lokasi file gambar di penyimpanan internal app (hanya untuk IMAGE).
 * alpha : 0.1..1
 * color : ARGB (hanya untuk TEXT)
 */
data class HudItem(
    val id: Long,
    val type: ItemType = ItemType.TEXT,
    val text: String = "",
    val path: String = "",
    val url: String = "",
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float,
    val color: Int,
    val fontFamily: String = "DEFAULT",
    val webTouchable: Boolean = false,
    val webAspectRatio: String = "16:9",
    val fontWeight: String = "REGULAR",
    val italic: Boolean = false,
    val underline: Boolean = false,
    val outlineColor: Int = 0xFF000000.toInt(),
    val outlineWidth: Float = 0f,
    val shadowColor: Int = 0x99000000.toInt(),
    val shadowBlur: Float = 0f,
    val shadowDx: Float = 0f,
    val shadowDy: Float = 0f,
    val textAlignment: String = "LEFT"
)

/** Urutan list = urutan layer (elemen terakhir ada di paling atas). */
data class HudLayout(val portrait: List<HudItem>, val landscape: List<HudItem>) {
    fun items(p: Profile): List<HudItem> = if (p == Profile.PORTRAIT) portrait else landscape

    fun withItems(p: Profile, items: List<HudItem>): HudLayout =
        if (p == Profile.PORTRAIT) copy(portrait = items) else copy(landscape = items)
}

object HudStore {
    const val KEY_LAYOUT = "layout"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("hud_overlay", Context.MODE_PRIVATE)

    /**
     * Membaca layout tersimpan. Elemen yang rusak dilewati satu per satu,
     * jadi satu elemen bermasalah tidak lagi membuat seluruh layout kembali ke default.
     */
    fun load(ctx: Context): HudLayout {
        val raw = prefs(ctx).getString(KEY_LAYOUT, null) ?: return defaultLayout()
        return try {
            val o = JSONObject(raw)
            HudLayout(
                decodeLenient(o.optJSONArray("portrait")),
                decodeLenient(o.optJSONArray("landscape"))
            )
        } catch (e: Exception) {
            defaultLayout()
        }
    }

    fun save(ctx: Context, layout: HudLayout) {
        val o = JSONObject()
            .put("portrait", encode(layout.portrait))
            .put("landscape", encode(layout.landscape))
        prefs(ctx).edit().putString(KEY_LAYOUT, o.toString()).apply()
    }

    fun exportJson(ctx: Context): String {
        val layout = load(ctx)
        return JSONObject()
            .put("format", "panje")
            .put("version", 1)
            .put("portrait", encode(layout.portrait))
            .put("landscape", encode(layout.landscape))
            .toString()
    }

    fun importJson(ctx: Context, raw: String) {
        val obj = JSONObject(raw)
        require(obj.optString("format") == "panje") { "File bukan konfigurasi Panje" }
        val layout = HudLayout(decode(obj.optJSONArray("portrait")), decode(obj.optJSONArray("landscape")))
        save(ctx, layout)
    }

    private fun defaultLayout() = HudLayout(
        portrait = listOf(sample(1L)),
        landscape = listOf(sample(2L))
    )

    private fun sample(id: Long) = HudItem(
        id = id,
        text = "Halo stream!",
        x = 0.08f,
        y = 0.08f,
        size = 0.07f,
        alpha = 1f,
        color = 0xFFFFFFFF.toInt()
    )

    fun encode(items: List<HudItem>): JSONArray {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("type", it.type.name)
                    .put("text", it.text)
                    .put("path", it.path)
                    .put("url", it.url)
                    .put("x", it.x.toDouble())
                    .put("y", it.y.toDouble())
                    .put("size", it.size.toDouble())
                    .put("alpha", it.alpha.toDouble())
                    .put("color", it.color)
                    .put("fontFamily", it.fontFamily)
                    .put("webTouchable", it.webTouchable)
                    .put("webAspectRatio", it.webAspectRatio)
                    .put("fontWeight", it.fontWeight)
                    .put("italic", it.italic)
                    .put("underline", it.underline)
                    .put("outlineColor", it.outlineColor)
                    .put("outlineWidth", it.outlineWidth.toDouble())
                    .put("shadowColor", it.shadowColor)
                    .put("shadowBlur", it.shadowBlur.toDouble())
                    .put("shadowDx", it.shadowDx.toDouble())
                    .put("shadowDy", it.shadowDy.toDouble())
                    .put("textAlignment", it.textAlignment)
            )
        }
        return arr
    }

    /** Ketat: satu elemen rusak melempar exception (dipakai untuk validasi import). */
    fun decode(arr: JSONArray?): List<HudItem> = decodeInternal(arr, strict = true)

    /** Toleran: elemen rusak dilewati (dipakai saat membaca layout tersimpan). */
    fun decodeLenient(arr: JSONArray?): List<HudItem> = decodeInternal(arr, strict = false)

    private fun decodeInternal(arr: JSONArray?, strict: Boolean): List<HudItem> {
        if (arr == null) return emptyList()
        val out = ArrayList<HudItem>(arr.length())
        for (i in 0 until arr.length()) {
            try {
                out.add(decodeItem(arr.getJSONObject(i)))
            } catch (e: Exception) {
                if (strict) throw e
            }
        }
        return out
    }

    private fun decodeItem(o: JSONObject): HudItem {
        val type = try {
            ItemType.valueOf(o.optString("type", "TEXT"))
        } catch (e: Exception) {
            ItemType.TEXT
        }
        return HudItem(
            id = o.getLong("id"),
            type = type,
            text = o.optString("text", ""),
            path = o.optString("path", ""),
            url = o.optString("url", ""),
            x = o.getDouble("x").toFloat(),
            y = o.getDouble("y").toFloat(),
            size = o.getDouble("size").toFloat(),
            alpha = o.getDouble("alpha").toFloat(),
            color = o.getInt("color"),
            fontFamily = o.optString("fontFamily", "DEFAULT"),
            webTouchable = o.optBoolean("webTouchable", false),
            webAspectRatio = o.optString("webAspectRatio", "16:9"),
            fontWeight = o.optString("fontWeight", "REGULAR"),
            italic = o.optBoolean("italic", false),
            underline = o.optBoolean("underline", false),
            outlineColor = o.optInt("outlineColor", 0xFF000000.toInt()),
            outlineWidth = o.optDouble("outlineWidth", 0.0).toFloat(),
            shadowColor = o.optInt("shadowColor", 0x99000000.toInt()),
            shadowBlur = o.optDouble("shadowBlur", 0.0).toFloat(),
            shadowDx = o.optDouble("shadowDx", 0.0).toFloat(),
            shadowDy = o.optDouble("shadowDy", 0.0).toFloat(),
            textAlignment = o.optString("textAlignment", "LEFT")
        )
    }
}

/** Satu lagu di playlist. uri = content URI dari file picker (izin disimpan permanen). */
data class Track(val uri: String, val name: String)

data class MusicState(
    val playing: Boolean = false,
    val index: Int = 0,
    val title: String = ""
)

/** Status pemutar musik yang dibaca UI. Ditulis oleh OverlayService. */
object MusicBus {
    val state = MutableStateFlow(MusicState())
}

object MusicStore {
    const val KEY_TRACKS = "music_tracks"
    const val KEY_REPEAT = "music_repeat"
    const val KEY_VOLUME = "music_volume"

    fun tracks(ctx: Context): List<Track> {
        val raw = HudStore.prefs(ctx).getString(KEY_TRACKS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Track(o.getString("uri"), o.optString("name", "Lagu"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTracks(ctx: Context, list: List<Track>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("uri", it.uri).put("name", it.name)) }
        HudStore.prefs(ctx).edit().putString(KEY_TRACKS, arr.toString()).apply()
    }

    /** Auto repeat: default aktif. */
    fun repeat(ctx: Context): Boolean = HudStore.prefs(ctx).getBoolean(KEY_REPEAT, true)

    fun setRepeat(ctx: Context, value: Boolean) {
        HudStore.prefs(ctx).edit().putBoolean(KEY_REPEAT, value).apply()
    }

    fun volume(ctx: Context): Float = HudStore.prefs(ctx).getFloat(KEY_VOLUME, 0.8f)

    fun setVolume(ctx: Context, value: Float) {
        HudStore.prefs(ctx).edit().putFloat(KEY_VOLUME, value).apply()
    }
}
