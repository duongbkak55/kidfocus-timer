package com.kidfocus.timer.domain.model

/**
 * Available color themes for the app.
 * Each theme maps to a unique [KidFocusColors] palette in the UI layer.
 */
enum class AppTheme(
    val displayName: String,
    val emoji: String,
) {
    /** Cool blue ocean palette – default theme. */
    OCEAN(displayName = "Đại dương", emoji = "🌊"),

    /** Calm green forest palette. */
    FOREST(displayName = "Rừng xanh", emoji = "🌿"),

    /** Warm orange sunset palette. */
    SUNSET(displayName = "Hoàng hôn", emoji = "🌅");

    companion object {
        /** Safely converts a stored String key back to an [AppTheme], falling back to [OCEAN]. */
        fun fromKey(key: String): AppTheme =
            entries.firstOrNull { it.name == key } ?: OCEAN
    }
}
