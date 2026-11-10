package com.pramstudio.hudoverlay

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class Profile { PORTRAIT, LANDSCAPE }

/**
 * Satu elemen teks di overlay.
 * x, y   : posisi pojok kiri atas, pecahan 0..1 dari lebar/tinggi layar.
 * size   : ukuran teks sebagai pecahan dari sisi layar terpendek.
 * alpha  : 0.1..1
 * color  : ARGB
 */
data class HudItem(
    val id: Long,
    val text: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float,
    val color: Int
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

    private fun sample(id: Long) =
        HudItem(id, "Halo stream!", 0.08f, 0.08f, 0.07f, 1f, 0xFFFFFFFF.toInt())

    private fun encode(items: List<HudItem>): JSONArray {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("text", it.text)
                    .put("x", it.x.toDouble())
                    .put("y", it.y.toDouble())
                    .put("size", it.size.toDouble())
                    .put("alpha", it.alpha.toDouble())
                    .put("color", it.color)
            )
        }
        return arr
    }

    private fun decode(arr: JSONArray?): List<HudItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HudItem(
                id = o.getLong("id"),
                text = o.getString("text"),
                x = o.getDouble("x").toFloat(),
                y = o.getDouble("y").toFloat(),
                size = o.getDouble("size").toFloat(),
                alpha = o.getDouble("alpha").toFloat(),
                color = o.getInt("color")
            )
        }
    }
}
