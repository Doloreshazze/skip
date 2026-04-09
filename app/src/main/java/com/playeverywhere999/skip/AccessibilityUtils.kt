package com.playeverywhere999.skip

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityUtils {
    fun isServiceEnabled(context: Context): Boolean {
        val componentName = ComponentName(context, AutoClickAccessibilityService::class.java)
        val expectedId = componentName.flattenToString()

        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!accessibilityEnabled) return false

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        return enabledServices
            .split(':')
            .any { it.equals(expectedId, ignoreCase = true) }
    }
}
