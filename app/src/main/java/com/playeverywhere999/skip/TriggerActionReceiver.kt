package com.playeverywhere999.skip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TriggerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_TRIGGER_ENABLED || !intent.hasExtra(EXTRA_ENABLED)) {
            return
        }

        val enabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
        AutoClickPrefs.setEnabled(context, enabled)
        TriggerNotification.show(context, enabled)
    }

    companion object {
        const val ACTION_SET_TRIGGER_ENABLED =
            "com.playeverywhere999.skip.action.SET_TRIGGER_ENABLED"
        const val EXTRA_ENABLED = "enabled"
    }
}
