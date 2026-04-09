package com.playeverywhere999.skip

import android.content.Context

object AutoClickPrefs {
    private const val PREFS_NAME = "auto_click_prefs"
    private const val KEY_TARGET_TEXT = "target_text"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_ACCESSIBILITY_PROMPT_SHOWN = "accessibility_prompt_shown"

    fun targetText(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TARGET_TEXT, "")
            .orEmpty()
    }

    fun setTargetText(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TARGET_TEXT, value)
            .apply()
    }

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
    }

    fun isSoundEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SOUND_ENABLED, value)
            .apply()
    }

    fun wasAccessibilityPromptShown(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACCESSIBILITY_PROMPT_SHOWN, false)
    }

    fun setAccessibilityPromptShown(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACCESSIBILITY_PROMPT_SHOWN, value)
            .apply()
    }
}
