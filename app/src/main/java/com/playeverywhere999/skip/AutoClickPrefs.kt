package com.playeverywhere999.skip

import android.content.Context

object AutoClickPrefs {
    private const val PREFS_NAME = "auto_click_prefs"
    private const val KEY_TARGET_TEXT = "target_text"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_ACCESSIBILITY_GUIDE_REQUESTED = "accessibility_guide_requested"
    private const val KEY_DISCLOSURE_ACCEPTED = "disclosure_accepted"
    private const val KEY_POWER_PERMISSION_PROMPT_HANDLED = "power_permission_prompt_handled"
    private const val KEY_POWER_PERMISSION_DONT_ASK_AGAIN = "power_permission_dont_ask_again"
    private const val KEY_POWER_PERMISSION_ALLOWED = "power_permission_allowed"
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

    fun isDisclosureAccepted(context: Context): Boolean {
        return prefs(context)
            .getBoolean(KEY_DISCLOSURE_ACCEPTED, false)
    }

    fun setDisclosureAccepted(context: Context, value: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_DISCLOSURE_ACCEPTED, value)
            .apply()
    }

    fun isPowerPermissionPromptHandled(context: Context): Boolean {
        return prefs(context)
            .getBoolean(KEY_POWER_PERMISSION_PROMPT_HANDLED, false)
    }

    fun setPowerPermissionPromptHandled(context: Context, value: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_POWER_PERMISSION_PROMPT_HANDLED, value)
            .apply()
    }

    fun isPowerPermissionDontAskAgain(context: Context): Boolean {
        return prefs(context)
            .getBoolean(KEY_POWER_PERMISSION_DONT_ASK_AGAIN, false)
    }

    fun setPowerPermissionDontAskAgain(context: Context, value: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_POWER_PERMISSION_DONT_ASK_AGAIN, value)
            .apply()
    }

    fun isPowerPermissionAllowed(context: Context): Boolean {
        return prefs(context)
            .getBoolean(KEY_POWER_PERMISSION_ALLOWED, false)
    }

    fun setPowerPermissionAllowed(context: Context, value: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_POWER_PERMISSION_ALLOWED, value)
            .apply()
    }
}
