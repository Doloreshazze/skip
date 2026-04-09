package com.playeverywhere999.skip

import android.content.Context

object AutoClickPrefs {
    private const val PREFS_NAME = "auto_click_prefs"
    private const val KEY_TARGET_TEXT = "target_text"
    private const val KEY_ENABLED = "enabled"

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
}
