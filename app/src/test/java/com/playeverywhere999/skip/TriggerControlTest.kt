package com.playeverywhere999.skip

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 31])
class TriggerControlTest {
    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("auto_click_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Settings.Secure.putString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ComponentName(context, AutoClickAccessibilityService::class.java).flattenToString()
        )
        AutoClickPrefs.setDisclosureAccepted(context, true)
        AutoClickPrefs.setTargetText(context, "skip")
        AutoClickPrefs.setEnabled(context, true)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun action(enabled: Boolean) = Intent(context, TriggerActionReceiver::class.java)
        .setAction(TriggerActionReceiver.ACTION_SET_TRIGGER_ENABLED)
        .putExtra(TriggerActionReceiver.EXTRA_ENABLED, enabled)

    @Test
    fun notificationPausesWithoutRevokingServiceOrLosingTarget() {
        TriggerActionReceiver().onReceive(context, action(false))
        assertFalse(AutoClickPrefs.isEnabled(context))
        assertTrue(AccessibilityUtils.isServiceEnabled(context))
        assertEquals("skip", AutoClickPrefs.targetText(context))
        // A newly created consumer reads the same paused state.
        val recreatedContext = context.createPackageContext(context.packageName, 0)
        assertFalse(AutoClickPrefs.isEnabled(recreatedContext))
        TriggerActionReceiver().onReceive(context, action(true))
        assertTrue(AutoClickPrefs.isEnabled(context))
    }

    @Test
    fun repeatedPauseAndResumeActionsAreIdempotent() {
        repeat(2) { TriggerActionReceiver().onReceive(context, action(false)) }
        assertFalse(AutoClickPrefs.isEnabled(context))
        repeat(2) { TriggerActionReceiver().onReceive(context, action(true)) }
        assertTrue(AutoClickPrefs.isEnabled(context))
    }

    @Test
    fun staleResumeCannotReenableAfterConsentIsWithdrawn() {
        val staleResume = action(true)
        AutoClickPrefs.setDisclosureAccepted(context, false)
        TriggerActionReceiver().onReceive(context, staleResume)
        assertFalse(AutoClickPrefs.isEnabled(context))
        AutoClickPrefs.setDisclosureAccepted(context, true)
        assertFalse(AutoClickPrefs.isEnabled(context))
    }

    @Test
    fun resumeCannotReenableAfterServiceIsDisabled() {
        AutoClickPrefs.setEnabled(context, false)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        TriggerActionReceiver().onReceive(context, action(true))
        assertFalse(AutoClickPrefs.isEnabled(context))
        val notifications = context.getSystemService(NotificationManager::class.java)
        assertTrue(notifications.activeNotifications.isEmpty())
    }

    @Test
    fun emptyTargetCannotResume() {
        AutoClickPrefs.setEnabled(context, false)
        AutoClickPrefs.setTargetText(context, "   ")
        TriggerActionReceiver().onReceive(context, action(true))
        assertFalse(AutoClickPrefs.isEnabled(context))
    }

    @Test
    fun malformedAndUnrelatedIntentsDoNotChangeState() {
        val receiver = TriggerActionReceiver()
        receiver.onReceive(context, Intent().setAction("unrelated").putExtra("enabled", false))
        receiver.onReceive(context, Intent().setAction(TriggerActionReceiver.ACTION_SET_TRIGGER_ENABLED))
        assertTrue(AutoClickPrefs.isEnabled(context))
    }

    @Test
    fun notificationActionChangesFromPauseToResume() {
        val notifications = context.getSystemService(NotificationManager::class.java)
        TriggerNotification.show(context)
        assertEquals(context.getString(R.string.trigger_notification_pause),
            notifications.activeNotifications.single().notification.actions.single().title.toString())
        TriggerActionReceiver().onReceive(context, action(false))
        assertEquals(context.getString(R.string.trigger_notification_resume),
            notifications.activeNotifications.single().notification.actions.single().title.toString())
    }
}
