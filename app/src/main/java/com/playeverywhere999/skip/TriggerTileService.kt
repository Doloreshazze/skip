package com.playeverywhere999.skip

import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/** Uses the same persisted switch as the notification and the app. */
class TriggerTileService : TileService() {
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        AutoClickPrefs.registerListener(this, prefsListener)
        refreshTile()
    }

    override fun onStopListening() {
        AutoClickPrefs.unregisterListener(this, prefsListener)
        super.onStopListening()
    }

    override fun onDestroy() {
        AutoClickPrefs.unregisterListener(this, prefsListener)
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        // Pause is immediate, even on the lock screen. Resume requires an unlock.
        val resume = !AutoClickPrefs.isEnabled(this)
        if (resume && isLocked) {
            unlockAndRun { setTriggerEnabled(true) }
        } else {
            setTriggerEnabled(resume)
        }
    }

    private fun setTriggerEnabled(enabled: Boolean) {
        val actualEnabled = AutoClickPrefs.setEnabled(this, enabled)
        if (enabled && !actualEnabled) {
            Toast.makeText(this, R.string.trigger_setup_required, Toast.LENGTH_LONG).show()
        }
        TriggerNotification.show(this)
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val enabled = AutoClickPrefs.isEnabled(this)
        val ready = AutoClickPrefs.canResume(this)
        val label = getString(when {
            enabled -> R.string.trigger_tile_active
            !ready -> R.string.trigger_tile_setup
            else -> R.string.trigger_tile_paused
        })
        tile.state = when {
            enabled -> Tile.STATE_ACTIVE
            ready -> Tile.STATE_INACTIVE
            else -> Tile.STATE_UNAVAILABLE
        }
        tile.label = label
        tile.contentDescription = label
        tile.icon = Icon.createWithResource(this, if (enabled) R.drawable.ic_trigger_pause else R.drawable.ic_trigger_play)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (enabled) R.string.trigger_notification_pause else R.string.trigger_notification_resume)
        }
        tile.updateTile()
    }
}
