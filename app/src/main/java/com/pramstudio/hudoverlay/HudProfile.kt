package com.pramstudio.hudoverlay

import android.content.Context
import android.content.res.Configuration

data class HudProfile(
    val label: String,
    val xPercent: Float,
    val yPercent: Float,
    val opacity: Float
)

object HudProfiles {
    private const val PREFS = "hud_profiles"

    fun isLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun load(context: Context, landscape: Boolean = isLandscape(context)): HudProfile {
        val key = if (landscape) "landscape" else "portrait"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return HudProfile(
            label = prefs.getString("${key}_label", "HUD Overlay") ?: "HUD Overlay",
            xPercent = prefs.getFloat("${key}_x", 0.5f),
            yPercent = prefs.getFloat("${key}_y", 0.18f),
            opacity = prefs.getFloat("${key}_opacity", 0.85f)
        )
    }

    fun save(context: Context, landscape: Boolean, profile: HudProfile) {
        val key = if (landscape) "landscape" else "portrait"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("${key}_label", profile.label)
            .putFloat("${key}_x", profile.xPercent.coerceIn(0f, 1f))
            .putFloat("${key}_y", profile.yPercent.coerceIn(0f, 1f))
            .putFloat("${key}_opacity", profile.opacity.coerceIn(0.1f, 1f))
            .apply()
    }
}
