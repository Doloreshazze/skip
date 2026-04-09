package com.playeverywhere999.skip

import android.content.Context

object AutoClickPrefs {
    private const val PREFS_NAME = "auto_click_prefs"
    private const val KEY_TARGET_TEXT = "target_text"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_ACCESSIBILITY_GUIDE_REQUESTED = "accessibility_guide_requested"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun targetText(context: Context): String {
        return prefs(context)
            .getString(KEY_TARGET_TEXT, "")
            .orEmpty()
    }

    fun setTargetText(context: Context, value: String) {
        prefs(context)
            .edit()
            .putString(KEY_TARGET_TEXT, value)
            .apply()
    }

    fun isEnabled(context: Context): Boolean {
        return prefs(context)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
    }

    fun isSoundEnabled(context: Context): Boolean {
        return prefs(context)
            .getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(context: Context, value: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_SOUND_ENABLED, value)
            .apply()
    }

    fun isAccessibilityGuideRequested(context: Context): Boolean {
        return prefs(context)
            .getBoolean(KEY_ACCESSIBILITY_GUIDE_REQUESTED, false)
    }

    fun setAccessibilityGuideRequested(context: Context, value: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_ACCESSIBILITY_GUIDE_REQUESTED, value)
            .apply()
    }
}
