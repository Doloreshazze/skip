package com.playeverywhere999.skip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

class AutoClickAccessibilityService : AccessibilityService() {
    private var lastClickAt = 0L
    private var overlayView: View? = null
    private var overlayLabel: TextView? = null
    private var toneGenerator: ToneGenerator? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
        attachOverlay()
        updateOverlayText()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!AutoClickPrefs.isEnabled(this)) {
            updateOverlayText()
            return
        }

        val targetText = AutoClickPrefs.targetText(this).trim()
        if (targetText.isEmpty()) {
            updateOverlayText()
            return
        }

        val rootNode = rootInActiveWindow ?: return
        val matchedNode = findNodeByText(rootNode, targetText) ?: return

        val now = SystemClock.elapsedRealtime()
        if (now - lastClickAt < CLICK_COOLDOWN_MS) {
            return
        }

        if (performClick(matchedNode)) {
            lastClickAt = now
            playClickSignalIfEnabled()
            updateOverlayText("Нажато: $targetText")
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        detachOverlay()
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, targetText: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()?.trim()
        val nodeContentDescription = node.contentDescription?.toString()?.trim()

        if (isIgnoredTargetInputNode(node)) {
            return null
        }

        val matched = nodeText.equals(targetText, ignoreCase = true) ||
            nodeContentDescription.equals(targetText, ignoreCase = true)

        if (matched) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByText(child, targetText)
            if (result != null) {
                return result
            }
        }
        return null
    }

    private fun isIgnoredTargetInputNode(node: AccessibilityNodeInfo): Boolean {
        val nodePackageName = node.packageName?.toString()
        val className = node.className?.toString()
        return nodePackageName == packageName &&
            node.isEditable &&
            className == "android.widget.EditText"
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }

        return false
    }

    private fun attachOverlay() {
        if (overlayView != null) return

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val label = TextView(this).apply {
            textSize = 12f
            setPadding(24, 12, 24, 12)
            setBackgroundColor(0xAA000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 40
        }

        wm.addView(label, params)
        overlayView = label
        overlayLabel = label
    }

    private fun detachOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView?.let { wm.removeView(it) }
        overlayView = null
        overlayLabel = null
    }

    private fun updateOverlayText(custom: String? = null) {
        val text = custom ?: run {
            val enabled = AutoClickPrefs.isEnabled(this)
            val target = AutoClickPrefs.targetText(this).ifBlank { "<не задан>" }
            if (enabled) "Автоклик: ВКЛ ($target)" else "Автоклик: ВЫКЛ"
        }
        overlayLabel?.text = text
    }

    private fun playClickSignalIfEnabled() {
        if (!AutoClickPrefs.isSoundEnabled(this)) return
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
    }

    companion object {
        private const val CLICK_COOLDOWN_MS = 1200L
        private const val BEEP_DURATION_MS = 120
        private const val TONE_VOLUME = 80
    }
}
