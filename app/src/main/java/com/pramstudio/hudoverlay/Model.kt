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
    val webTouchable: Boolean = false
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

    fun load(ctx: Context): HudLayout {
        val raw = prefs(ctx).getString(KEY_LAYOUT, null) ?: return defaultLayout()
        return try {
            val o = JSONObject(raw)
            HudLayout(decode(o.optJSONArray("portrait")), decode(o.optJSONArray("landscape")))
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

    private fun encode(items: List<HudItem>): JSONArray {
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
            )
        }
        return arr
    }

    private fun decode(arr: JSONArray?): List<HudItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val type = try {
                ItemType.valueOf(o.optString("type", "TEXT"))
            } catch (e: Exception) {
                ItemType.TEXT
            }
            HudItem(
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
                webTouchable = o.optBoolean("webTouchable", false)
            )
        }
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
