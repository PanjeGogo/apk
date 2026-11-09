package com.pramstudio.hudoverlay

import android.content.Context
import android.content.res.Configuration

data class HudProfile(
    val label: String,
    val xPercent: Float,
    val yPercent: Float,
    val opacity: Float,
    val imageUri: String? = null,
    val videoUri: String? = null,
    val webUrl: String? = null,
    val audioUris: List<String> = emptyList(),
    val repeatAudio: Boolean = true
)

object HudProfiles {
    private const val PREFS = "hud_profiles"

    fun isLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun load(context: Context, landscape: Boolean = isLandscape(context)): HudProfile {
        val key = if (landscape) "landscape" else "portrait"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val audio = prefs.getString("${key}_audio", "").orEmpty().split("\n").filter { it.isNotBlank() }
        return HudProfile(
            label = prefs.getString("${key}_label", "HUD Overlay") ?: "HUD Overlay",
            xPercent = prefs.getFloat("${key}_x", 0.5f),
            yPercent = prefs.getFloat("${key}_y", 0.18f),
            opacity = prefs.getFloat("${key}_opacity", 0.85f),
            imageUri = prefs.getString("${key}_image", null),
            videoUri = prefs.getString("${key}_video", null),
            webUrl = prefs.getString("${key}_web", null),
            audioUris = audio,
            repeatAudio = prefs.getBoolean("${key}_repeat", true)
        )
    }

    fun save(context: Context, landscape: Boolean, profile: HudProfile) {
        val key = if (landscape) "landscape" else "portrait"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("${key}_label", profile.label)
            .putFloat("${key}_x", profile.xPercent.coerceIn(0f, 1f))
            .putFloat("${key}_y", profile.yPercent.coerceIn(0f, 1f))
            .putFloat("${key}_opacity", profile.opacity.coerceIn(0.1f, 1f))
            .putString("${key}_image", profile.imageUri)
            .putString("${key}_video", profile.videoUri)
            .putString("${key}_web", profile.webUrl)
            .putString("${key}_audio", profile.audioUris.joinToString("\n"))
            .putBoolean("${key}_repeat", profile.repeatAudio)
            .apply()
    }
}
