package com.playeverywhere999.skip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
    private var closeDropView: ImageView? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var closeDropLayoutParams: WindowManager.LayoutParams? = null
    private var toneGenerator: ToneGenerator? = null
    private lateinit var prefs: SharedPreferences
    private var isAutoClickEnabled = false
    private var isPaused = false
    private var isSoundEnabled = true
    private var targetText = ""
    private var overlayButtonStyle = "classic"
    private var accessibilityGuideRequested = false
    private var guideLastScrollAt = 0L
    private var guidePulseStarted = false
    private var overlayDismissed = false
    private var moveModeActive = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val moveModeTrigger = Runnable {
        moveModeActive = true
        showCloseDropTarget()
    }
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
        if (key == KEY_ENABLED && isAutoClickEnabled) {
            overlayDismissed = false
            resetOverlayPositionToDefault()
        }
        updatePlayPauseIcon()
        updateOverlayVisibility()
        updateOverlayText()
    }
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateOverlayVisibility()
        }
    }
    private var isScreenStateReceiverRegistered = false

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
        registerScreenStateReceiverIfNeeded()
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
        attachOverlay()
        updatePlayPauseIcon()
        updateOverlayVisibility()
        updateOverlayText()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        handleSettingsGuide()

        if (!isAutoClickEnabled || isPaused) {
            updateOverlayText()
            return
        }

        if (targetText.isEmpty()) {
            updateOverlayText()
            return
        }

        val rootNode = rootInActiveWindow ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickAt < CLICK_COOLDOWN_MS) {
                return
            }

            if (clickFirstMatchingNode(rootNode, targetText)) {
                lastClickAt = now
                playClickSignalIfEnabled()
                updateOverlayText(getString(R.string.overlay_clicked, targetText))
            }
        } finally {
            rootNode.recycle()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenStateReceiverIfNeeded()
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsChangeListener)
        }
        stopGuidePulse()
        mainHandler.removeCallbacks(moveModeTrigger)
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
        try {
            val packageName = root.packageName?.toString().orEmpty()
            if (packageName !in SETTINGS_PACKAGES) {
                stopGuidePulse()
                return
            }

            val target = findNodeByTextContains(root, getString(R.string.app_name))
            if (target != null) {
                try {
                    startGuidePulse()
                    target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                    val bounds = Rect()
                    target.getBoundsInScreen(bounds)
                    moveOverlayNear(bounds)
                    updateOverlayText(
                        getString(
                            R.string.guide_hint_tap_service,
                            getString(R.string.app_name)
                        )
                    )
                    prefs.edit().putBoolean(KEY_GUIDE_REQUESTED, false).apply()
                    accessibilityGuideRequested = false
                    return
                } finally {
                    if (target !== root) {
                        target.recycle()
                    }
                }
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
        } finally {
            root.recycle()
        }
    }

    private fun clickFirstMatchingNode(rootNode: AccessibilityNodeInfo, targetText: String): Boolean {
        val stack = ArrayDeque<AccessibilityNodeInfo>()

        if (!isIgnoredTargetInputNode(rootNode)) {
            val rootText = rootNode.text?.toString()?.trim()
            val rootContentDescription = rootNode.contentDescription?.toString()?.trim()
            val rootMatched = rootText.equals(targetText, ignoreCase = true) ||
                rootContentDescription.equals(targetText, ignoreCase = true)
            if (rootMatched) {
                return clickNodeOrClickableParent(rootNode)
            }
        }

        for (i in 0 until rootNode.childCount) {
            rootNode.getChild(i)?.let(stack::addLast)
        }

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val shouldIgnore = isIgnoredTargetInputNode(node)

            if (!shouldIgnore) {
                val nodeText = node.text?.toString()?.trim()
                val nodeContentDescription = node.contentDescription?.toString()?.trim()
                val matched = nodeText.equals(targetText, ignoreCase = true) ||
                    nodeContentDescription.equals(targetText, ignoreCase = true)

                if (matched) {
                    return try {
                        clickNodeOrClickableParent(node)
                    } finally {
                        recycleNodes(stack)
                        node.recycle()
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::addLast)
            }
            node.recycle()
        }

        return false
    }

    private fun recycleNodes(nodes: ArrayDeque<AccessibilityNodeInfo>) {
        while (nodes.isNotEmpty()) {
            nodes.removeLast().recycle()
        }
    }

    private fun clickNodeOrClickableParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo = node
        while (true) {
            if (current.isClickable) {
                val didClick = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (current !== node) {
                    current.recycle()
                }
                return didClick
            }

            val parent = current.parent ?: return false
            if (current !== node) {
                current.recycle()
            }
            current = parent
        }
    }

    private fun isIgnoredTargetInputNode(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty()
        return node.isEditable ||
            className == "android.widget.EditText" ||
            isLauncherNode(node)
    }

    private fun isLauncherNode(node: AccessibilityNodeInfo): Boolean {
        val packageName = node.packageName?.toString().orEmpty()
        return packageName in LAUNCHER_PACKAGES
    }

    private fun attachOverlay() {
        if (overlayView != null) return

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val playPause = createActionButton(android.R.drawable.ic_media_pause)
        val overlayBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = overlayCornerRadiusPx()
            setColor(0xAA000000.toInt())
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 12, 12, 12)
            background = overlayBackground
            addView(playPause)
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

        val overlayTouchListener = View.OnTouchListener overlayTouchListener@{ _, event ->
            val currentParams = overlayLayoutParams ?: return@overlayTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = currentParams.x
                    dragStartY = currentParams.y
                    touchStartRawX = event.rawX
                    touchStartRawY = event.rawY
                    moveModeActive = false
                    mainHandler.postDelayed(moveModeTrigger, LONG_PRESS_TIMEOUT_MS)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!moveModeActive) {
                        val movedEnough = kotlin.math.abs(event.rawX - touchStartRawX) > MOVE_THRESHOLD_PX ||
                            kotlin.math.abs(event.rawY - touchStartRawY) > MOVE_THRESHOLD_PX
                        if (movedEnough) {
                            mainHandler.removeCallbacks(moveModeTrigger)
                        }
                        return@overlayTouchListener true
                    }

                    val deltaX = (event.rawX - touchStartRawX).toInt()
                    val deltaY = (event.rawY - touchStartRawY).toInt()
                    currentParams.x = dragStartX + deltaX
                    currentParams.y = (dragStartY + deltaY).coerceAtLeast(0)
                    wm.updateViewLayout(container, currentParams)
                    updateCloseDropTargetState(event.rawX, event.rawY)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(moveModeTrigger)
                    if (moveModeActive) {
                        if (isInsideCloseDropTarget(event.rawX, event.rawY)) {
                            dismissOverlayViews()
                        } else {
                            hideCloseDropTarget()
                        }
                    } else {
                        val isTap = kotlin.math.abs(event.rawX - touchStartRawX) <= MOVE_THRESHOLD_PX &&
                            kotlin.math.abs(event.rawY - touchStartRawY) <= MOVE_THRESHOLD_PX
                        if (isTap) {
                            isPaused = !isPaused
                            updatePlayPauseIcon()
                            updateOverlayVisibility()
                            updateOverlayText()
                        }
                    }
                    moveModeActive = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(moveModeTrigger)
                    if (moveModeActive) {
                        hideCloseDropTarget()
                    }
                    moveModeActive = false
                    true
                }

                else -> false
            }
        }
        container.setOnTouchListener(overlayTouchListener)
        playPause.setOnTouchListener(overlayTouchListener)

        wm.addView(container, params)
        overlayView = container
        overlayContainer = container
        playPauseButton = playPause
        overlayLayoutParams = params
        updateOverlayVisibility()
    }

    private fun createActionButton(iconResId: Int): ImageButton {
        return ImageButton(this).apply {
            setImageResource(iconResId)
            setBackgroundColor(0x00000000)
            setColorFilter(0xFFFFFFFF.toInt())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val sizePx = buttonSizePxForStyle(overlayButtonStyle)
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
        }
    }

    private fun showCloseDropTarget() {
        if (overlayDismissed) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dropView = closeDropView ?: ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCCB00020.toInt())
            setPadding(20, 20, 20, 20)
        }.also {
            closeDropView = it
        }

        val params = closeDropLayoutParams ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = closeDropBottomMarginPx()
        }.also {
            closeDropLayoutParams = it
        }

        if (dropView.parent == null) {
            wm.addView(dropView, params)
        } else {
            wm.updateViewLayout(dropView, params)
        }
        dropView.alpha = 0.75f
        dropView.visibility = View.VISIBLE
    }

    private fun hideCloseDropTarget() {
        closeDropView?.visibility = View.GONE
    }

    private fun updateCloseDropTargetState(rawX: Float, rawY: Float) {
        closeDropView?.alpha = if (isInsideCloseDropTarget(rawX, rawY)) 1f else 0.75f
    }

    private fun isInsideCloseDropTarget(rawX: Float, rawY: Float): Boolean {
        val dropView = closeDropView ?: return false
        if (dropView.visibility != View.VISIBLE) return false
        val location = IntArray(2)
        dropView.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]
        val right = left + dropView.width
        val bottom = top + dropView.height
        return rawX in left.toFloat()..right.toFloat() && rawY in top.toFloat()..bottom.toFloat()
    }

    private fun dismissOverlayViews() {
        overlayDismissed = true
        moveModeActive = false
        overlayContainer?.visibility = View.GONE
        hideCloseDropTarget()
    }

    private fun updateOverlayVisibility() {
        val visible = isAutoClickEnabled && !overlayDismissed && !isScreenLocked()
        overlayContainer?.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) {
            hideCloseDropTarget()
        }
    }

    private fun isScreenLocked(): Boolean {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        val powerManager = getSystemService(PowerManager::class.java)
        val screenOff = powerManager?.isInteractive == false
        val keyguardLocked = keyguardManager?.isKeyguardLocked == true
        val deviceLocked = keyguardManager?.isDeviceLocked == true
        return screenOff || keyguardLocked || deviceLocked
    }

    private fun registerScreenStateReceiverIfNeeded() {
        if (isScreenStateReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, filter)
        isScreenStateReceiverRegistered = true
    }

    private fun unregisterScreenStateReceiverIfNeeded() {
        if (!isScreenStateReceiverRegistered) return
        unregisterReceiver(screenStateReceiver)
        isScreenStateReceiverRegistered = false
    }

    private fun updatePlayPauseIcon() {
        val iconRes = resolveOverlayIcon(isPaused)
        playPauseButton?.setImageResource(iconRes)
        applyButtonStyle()
    }

    private fun resolveOverlayIcon(paused: Boolean): Int {
        return when (overlayButtonStyle) {
            "filled" -> if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
            "alt" -> android.R.drawable.presence_online
            "outlined" -> if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
            else -> if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        }
    }

    private fun applyButtonStyle() {
        val button = playPauseButton ?: return
        val sizePx = buttonSizePxForStyle(overlayButtonStyle)
        button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
            width = sizePx
            height = sizePx
        }
        button.background = null
        button.setBackgroundColor(0x00000000)
        button.imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())

        when (overlayButtonStyle) {
            "alt" -> {
                val fillColor = if (isPaused) 0xFFFFC107.toInt() else 0xFF2E7D32.toInt()
                val circle = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(fillColor)
                }
                button.setImageResource(android.R.drawable.presence_online)
                button.imageTintList = ColorStateList.valueOf(0x00FFFFFF)
                button.background = circle
            }
            "outlined" -> {
                val strokeColor = if (isPaused) 0xFFFFC107.toInt() else 0xFF2E7D32.toInt()
                val outline = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x00000000)
                    setStroke(outlinedStrokePx(), strokeColor)
                }
                button.background = outline
            }
            "filled" -> {
                button.background = null
            }
            else -> {
                button.background = null
            }
        }
        button.requestLayout()
    }

    private fun buttonSizePxForStyle(style: String): Int {
        val dp = if (style == "filled") FILLED_ACTION_BUTTON_SIZE_DP else ACTION_BUTTON_SIZE_DP
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }

    private fun outlinedStrokePx(): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        OUTLINED_STROKE_DP,
        resources.displayMetrics
    ).toInt()

    private fun detachOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView?.let { wm.removeView(it) }
        closeDropView?.let { if (it.parent != null) wm.removeView(it) }
        overlayView = null
        overlayContainer = null
        playPauseButton = null
        closeDropView = null
        overlayLayoutParams = null
        closeDropLayoutParams = null
    }

    private fun updateOverlayText(custom: String? = null) {
        val statusText = custom ?: run {
            val target = targetText.ifBlank { getString(R.string.overlay_target_not_set) }
            if (!isAutoClickEnabled) {
                getString(R.string.overlay_easy_off)
            } else if (isPaused) {
                getString(R.string.overlay_easy_off)
            } else {
                getString(R.string.overlay_easy_on, target)
            }
        }
        playPauseButton?.contentDescription = statusText
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

    private fun resetOverlayPositionToDefault() {
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

    private fun closeDropBottomMarginPx(): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        CLOSE_DROP_BOTTOM_MARGIN_DP,
        resources.displayMetrics
    ).toInt()

    private fun overlayCornerRadiusPx(): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        OVERLAY_CORNER_RADIUS_DP,
        resources.displayMetrics
    )

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
        if (!isAutoClickEnabled) {
            isPaused = false
        }
        isSoundEnabled = prefs.getBoolean("sound_enabled", true)
        targetText = prefs.getString("target_text", "").orEmpty().trim()
        accessibilityGuideRequested = prefs.getBoolean(KEY_GUIDE_REQUESTED, false)
        overlayButtonStyle = prefs.getString(KEY_OVERLAY_BUTTON_STYLE, "classic").orEmpty().ifBlank { "classic" }
    }

    private fun findNodeByTextContains(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let(stack::addLast)
        }

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val nodeText = current.text?.toString().orEmpty()
            val contentDescription = current.contentDescription?.toString().orEmpty()
            if (nodeText.contains(text, ignoreCase = true) ||
                contentDescription.contains(text, ignoreCase = true)
            ) {
                recycleNodes(stack)
                return current
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let(stack::addLast)
            }
            current.recycle()
        }
        return null
    }

    companion object {
        private const val CLICK_COOLDOWN_MS = 1200L
        private const val BEEP_DURATION_MS = 120
        private const val TONE_VOLUME = 80
        private const val GUIDE_SCROLL_COOLDOWN_MS = 700L
        private const val GUIDE_PULSE_PERIOD_MS = 900L
        private const val LONG_PRESS_TIMEOUT_MS = 450L
        private const val MOVE_THRESHOLD_PX = 12
        private const val KEY_GUIDE_REQUESTED = "accessibility_guide_requested"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_OVERLAY_BUTTON_STYLE = "overlay_button_style"
        private const val OVERLAY_TOP_MARGIN_DP = 16f
        private const val OVERLAY_CORNER_RADIUS_DP = 14f
        private const val ACTION_BUTTON_SIZE_DP = 48f
        private const val FILLED_ACTION_BUTTON_SIZE_DP = 36f
        private const val OUTLINED_STROKE_DP = 1.5f
        private const val CLOSE_DROP_BOTTOM_MARGIN_DP = 28f
        private val SETTINGS_PACKAGES = setOf("com.android.settings", "com.google.android.settings")
        private val LAUNCHER_PACKAGES = setOf(
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.miui.home",
            "com.sec.android.app.launcher",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher",
            "com.transsion.XOSLauncher",
            "com.transsion.hilauncher",
            "com.realme.launcher"
        )
    }
}
