package com.playeverywhere999.skip

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object TriggerNotification {
    private const val CHANNEL_ID = "trigger_control"
    private const val NOTIFICATION_ID = 1001

    fun show(context: Context) {
        if (!AccessibilityUtils.isServiceEnabled(context)) {
            cancel(context)
            return
        }
        if (!canPostNotifications(context)) return

        val enabled = AutoClickPrefs.isEnabled(context)
        val needsSetup = !enabled && !AutoClickPrefs.canResume(context)

        createChannel(context)

        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getBroadcast(
            context,
            if (enabled) 1 else 2,
            Intent(context, TriggerActionReceiver::class.java).apply {
                action = TriggerActionReceiver.ACTION_SET_TRIGGER_ENABLED
                putExtra(TriggerActionReceiver.EXTRA_ENABLED, !enabled)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(when {
            enabled -> R.string.trigger_notification_active_title
            needsSetup -> R.string.trigger_tile_setup
            else -> R.string.trigger_notification_paused_title
        })
        val actionLabel = context.getString(when {
            enabled -> R.string.trigger_notification_pause
            needsSetup -> R.string.trigger_open_app
            else -> R.string.trigger_notification_resume
        })
        val actionIcon = if (enabled) R.drawable.ic_trigger_pause else R.drawable.ic_trigger_play
        val action = NotificationCompat.Action.Builder(
            actionIcon, actionLabel, if (needsSetup) openAppIntent else toggleIntent
        ).setAuthenticationRequired(!enabled).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(actionIcon)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.trigger_notification_hint, actionLabel))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(action)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Android 13+ may deny notification permission; the app keeps working normally.
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.trigger_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.trigger_notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
