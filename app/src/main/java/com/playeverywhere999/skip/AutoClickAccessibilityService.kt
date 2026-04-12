package com.playeverywhere999.skip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout

class AutoClickAccessibilityService : AccessibilityService() {
    private var lastClickAt = 0L
    private var overlayView: View? = null
    private var overlayContainer: LinearLayout? = null
    private var playPauseButton: ImageButton? = null
    private var offButton: ImageButton? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var toneGenerator: ToneGenerator? = null
    private lateinit var prefs: SharedPreferences
    private var isAutoClickEnabled = false
    private var isSoundEnabled = true
    private var targetText = ""
    private var accessibilityGuideRequested = false
    private var guideLastScrollAt = 0L
    private var guidePulseStarted = false
    private val guidePulseCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!guidePulseStarted) return
            val frameTimeMs = frameTimeNanos / 1_000_000L
            val wave = kotlin.math.sin(frameTimeMs / GUIDE_PULSE_PERIOD_MS.toDouble() * Math.PI * 2.0)
            overlayContainer?.alpha = (0.55f + (wave.toFloat() + 1f) * 0.18f).coerceIn(0.5f, 0.95f)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private val prefsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null) return@OnSharedPreferenceChangeListener
        reloadPrefs()
        updatePlayPauseIcon()
        updateOverlayVisibility()
        updateOverlayText()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("auto_click_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsChangeListener)
        reloadPrefs()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
        attachOverlay()
        updatePlayPauseIcon()
        updateOverlayVisibility()
        updateOverlayText()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        handleSettingsGuide()

        if (!isAutoClickEnabled) {
            updateOverlayText()
            return
        }

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
            updateOverlayText(getString(R.string.overlay_clicked, targetText))
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsChangeListener)
        }
        stopGuidePulse()
        detachOverlay()
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun handleSettingsGuide() {
        if (!accessibilityGuideRequested) {
            stopGuidePulse()
            return
        }

        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString().orEmpty()
        if (packageName !in SETTINGS_PACKAGES) {
            stopGuidePulse()
            return
        }

        val target = findNodeByTextContains(root, getString(R.string.accessibility_service_name))
        if (target != null) {
            startGuidePulse()
            target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            val bounds = Rect()
            target.getBoundsInScreen(bounds)
            moveOverlayNear(bounds)
            updateOverlayText(
                getString(
                    R.string.guide_hint_tap_service,
                    getString(R.string.accessibility_service_name)
                )
            )
            prefs.edit().putBoolean(KEY_GUIDE_REQUESTED, false).apply()
            accessibilityGuideRequested = false
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - guideLastScrollAt >= GUIDE_SCROLL_COOLDOWN_MS) {
            val didScroll = root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            if (didScroll) {
                guideLastScrollAt = now
            }
        }
        startGuidePulse()
        updateOverlayText(getString(R.string.guide_hint_searching))
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
        val className = node.className?.toString().orEmpty()
        return node.isEditable || className == "android.widget.EditText"
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
        val playPause = createActionButton(android.R.drawable.ic_media_pause)
        val off = createActionButton(android.R.drawable.ic_lock_power_off)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xAA000000.toInt())
            addView(playPause)
            addView(off)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = topOverlayOffsetPx()
        }

        playPause.setOnClickListener {
            isAutoClickEnabled = !isAutoClickEnabled
            prefs.edit().putBoolean(KEY_ENABLED, isAutoClickEnabled).apply()
            updatePlayPauseIcon()
            updateOverlayVisibility()
            updateOverlayText()
        }

        off.setOnClickListener {
            isAutoClickEnabled = false
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            updatePlayPauseIcon()
            updateOverlayVisibility()
            updateOverlayText()
        }

        container.setOnTouchListener { _, event ->
            val currentParams = overlayLayoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = currentParams.x
                    dragStartY = currentParams.y
                    touchStartRawX = event.rawX
                    touchStartRawY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - touchStartRawX).toInt()
                    val deltaY = (event.rawY - touchStartRawY).toInt()
                    currentParams.x = dragStartX + deltaX
                    currentParams.y = (dragStartY + deltaY).coerceAtLeast(0)
                    wm.updateViewLayout(container, currentParams)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> false

                else -> false
            }
        }

        wm.addView(container, params)
        overlayView = container
        overlayContainer = container
        playPauseButton = playPause
        offButton = off
        overlayLayoutParams = params
        updateOverlayVisibility()
    }

    private fun createActionButton(iconResId: Int): ImageButton {
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            ACTION_BUTTON_SIZE_DP,
            resources.displayMetrics
        ).toInt()
        return ImageButton(this).apply {
            setImageResource(iconResId)
            setBackgroundColor(0x00000000)
            setColorFilter(0xFFFFFFFF.toInt())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                marginStart = 4
                marginEnd = 4
            }
        }
    }

    private fun updateOverlayVisibility() {
        overlayContainer?.visibility = if (isAutoClickEnabled) View.VISIBLE else View.GONE
    }

    private fun updatePlayPauseIcon() {
        val iconRes = if (isAutoClickEnabled) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        playPauseButton?.setImageResource(iconRes)
    }

    private fun detachOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView?.let { wm.removeView(it) }
        overlayView = null
        overlayContainer = null
        playPauseButton = null
        offButton = null
        overlayLayoutParams = null
    }

    private fun updateOverlayText(custom: String? = null) {
        val statusText = custom ?: run {
            val target = targetText.ifBlank { getString(R.string.overlay_target_not_set) }
            if (isAutoClickEnabled) {
                getString(R.string.overlay_autoclick_on, target)
            } else {
                getString(R.string.overlay_autoclick_off)
            }
        }
        playPauseButton?.contentDescription = statusText
        offButton?.contentDescription = getString(R.string.overlay_autoclick_off)
    }

    private fun moveOverlayNear(@Suppress("UNUSED_PARAMETER") targetBounds: Rect) {
        val view = overlayView ?: return
        val params = overlayLayoutParams ?: return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = topOverlayOffsetPx()
        wm.updateViewLayout(view, params)
    }

    private fun topOverlayOffsetPx(): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        OVERLAY_TOP_MARGIN_DP,
        resources.displayMetrics
    ).toInt()

    private var dragStartX = 0
    private var dragStartY = 0
    private var touchStartRawX = 0f
    private var touchStartRawY = 0f

    private fun startGuidePulse() {
        if (guidePulseStarted) return
        guidePulseStarted = true
        Choreographer.getInstance().postFrameCallback(guidePulseCallback)
    }

    private fun stopGuidePulse() {
        if (!guidePulseStarted) return
        guidePulseStarted = false
        Choreographer.getInstance().removeFrameCallback(guidePulseCallback)
        overlayContainer?.alpha = 1f
    }

    private fun playClickSignalIfEnabled() {
        if (!isSoundEnabled) return
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
    }

    private fun reloadPrefs() {
        isAutoClickEnabled = prefs.getBoolean(KEY_ENABLED, false)
        isSoundEnabled = prefs.getBoolean("sound_enabled", true)
        targetText = prefs.getString("target_text", "").orEmpty().trim()
        accessibilityGuideRequested = prefs.getBoolean(KEY_GUIDE_REQUESTED, false)
    }

    private fun findNodeByTextContains(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString().orEmpty()
        val contentDescription = node.contentDescription?.toString().orEmpty()
        if (nodeText.contains(text, ignoreCase = true) ||
            contentDescription.contains(text, ignoreCase = true)
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByTextContains(child, text)
            if (result != null) return result
        }
        return null
    }

    companion object {
        private const val CLICK_COOLDOWN_MS = 1200L
        private const val BEEP_DURATION_MS = 120
        private const val TONE_VOLUME = 80
        private const val GUIDE_SCROLL_COOLDOWN_MS = 700L
        private const val GUIDE_PULSE_PERIOD_MS = 900L
        private const val KEY_GUIDE_REQUESTED = "accessibility_guide_requested"
        private const val KEY_ENABLED = "enabled"
        private const val OVERLAY_TOP_MARGIN_DP = 16f
        private const val ACTION_BUTTON_SIZE_DP = 36f
        private val SETTINGS_PACKAGES = setOf("com.android.settings", "com.google.android.settings")
    }
}
