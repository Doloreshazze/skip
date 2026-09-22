package com.playeverywhere999.skip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
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
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView

class AutoClickAccessibilityService : AccessibilityService() {
    private var lastClickAt = 0L
    private var clickIndicatorView: ImageView? = null
    private var pendingShowClickIndicator: Runnable? = null
    private var toneGenerator: ToneGenerator? = null
    private lateinit var prefs: SharedPreferences
    private var isAutoClickEnabled = false
    private var isSoundEnabled = true
    private var targetText = ""
    private var accessibilityGuideRequested = false
    private var isServiceConnected = false
    private var guideLastScrollAt = 0L
    @Volatile private var isServiceDestroyed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null) return@OnSharedPreferenceChangeListener
        reloadPrefs()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("auto_click_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsChangeListener)
        reloadPrefs()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
        TriggerNotification.show(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        handleSettingsGuide()

        if (!AutoClickPrefs.isEnabled(this) || isScreenLocked()) {
            return
        }

        if (targetText.isEmpty()) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastClickAt < CLICK_COOLDOWN_MS) {
            return
        }

        val clickedBounds = clickFirstMatchingVisibleNode(targetText) ?: return
        lastClickAt = now
        playClickSignalIfEnabled()
        showClickIndicator(clickedBounds)
    }

    private fun clickFirstMatchingVisibleNode(targetText: String): Rect? {
        val visibleWindows = windows
        var clickedBounds: Rect? = null
        var inspectedWindowRoot = false

        for (index in visibleWindows.indices) {
            val window = visibleWindows[index]
            try {
                // PiP windows (including YouTube) are not guaranteed to be
                // reported as TYPE_APPLICATION. Do not filter by window type:
                // instead, inspect the root package and exclude only our own UI
                // and System UI. This keeps PiP accessible while preventing the
                // trigger from acting on notifications/Quick Settings.
                val root = window.root
                if (root != null) {
                    try {
                        val rootPackage = root.packageName?.toString()
                        if (rootPackage != packageName && rootPackage != SYSTEM_UI_PACKAGE) {
                            inspectedWindowRoot = true
                            clickedBounds = clickFirstMatchingNode(root, targetText)
                        }
                    } finally {
                        root.recycle()
                    }
                }
            } finally {
                window.recycle()
            }

            if (clickedBounds != null) {
                for (remainingIndex in index + 1 until visibleWindows.size) {
                    visibleWindows[remainingIndex].recycle()
                }
                return clickedBounds
            }
        }

        if (!inspectedWindowRoot) {
            val activeRoot = rootInActiveWindow ?: return null
            return try {
                clickFirstMatchingNode(activeRoot, targetText)
            } finally {
                activeRoot.recycle()
            }
        }

        return null
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        isServiceDestroyed = true
        isServiceConnected = false
        super.onDestroy()
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsChangeListener)
        }
        detachOverlay()
        toneGenerator?.release()
        toneGenerator = null
        TriggerNotification.cancel(this)
    }

    private fun handleSettingsGuide() {
        if (!accessibilityGuideRequested) {
            return
        }

        val root = rootInActiveWindow ?: return
        try {
            val packageName = root.packageName?.toString().orEmpty()
            if (packageName !in SETTINGS_PACKAGES) {
                return
            }

            val target = findNodeByTextContains(root, getString(R.string.app_name))
            if (target != null) {
                try {
                    target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
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
        } finally {
            root.recycle()
        }
    }

    private fun clickFirstMatchingNode(rootNode: AccessibilityNodeInfo, targetText: String): Rect? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()

        if (isMatchingVisibleNode(rootNode, targetText)) {
            clickNodeOrClickableParent(rootNode)?.let { return it }
        }

        for (i in 0 until rootNode.childCount) {
            rootNode.getChild(i)?.let(stack::addLast)
        }

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (isMatchingVisibleNode(node, targetText)) {
                val clickedBounds = clickNodeOrClickableParent(node)
                if (clickedBounds != null) {
                    recycleNodes(stack)
                    node.recycle()
                    return clickedBounds
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::addLast)
            }
            node.recycle()
        }

        return null
    }

    private fun recycleNodes(nodes: ArrayDeque<AccessibilityNodeInfo>) {
        while (nodes.isNotEmpty()) {
            nodes.removeLast().recycle()
        }
    }

    private fun clickNodeOrClickableParent(node: AccessibilityNodeInfo): Rect? {
        var current: AccessibilityNodeInfo = node
        while (true) {
            if (current.isClickable) {
                val bounds = Rect()
                current.getBoundsInScreen(bounds)
                val didClick = AutoClickPrefs.isEnabled(this) &&
                    !isScreenLocked() &&
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (current !== node) {
                    current.recycle()
                }
                return if (didClick) bounds else null
            }

            val parent = current.parent ?: run {
                if (current !== node) {
                    current.recycle()
                }
                return null
            }
            if (current !== node) {
                current.recycle()
            }
            current = parent
        }
    }

    private fun isMatchingVisibleNode(node: AccessibilityNodeInfo, targetText: String): Boolean {
        if (!node.isVisibleToUser || isIgnoredTargetInputNode(node)) {
            return false
        }

        return matchesTargetInShortText(node.text, targetText) ||
            matchesTargetInShortText(node.contentDescription, targetText)
    }

    private fun matchesTargetInShortText(text: CharSequence?, targetText: String): Boolean {
        val normalizedText = text?.toString()?.trim().orEmpty()
        if (normalizedText.isEmpty()) {
            return false
        }

        val wordCount = normalizedText.split(WORD_SEPARATOR).size
        return wordCount <= MAX_TRIGGER_TEXT_WORDS &&
            normalizedText.contains(targetText, ignoreCase = true)
    }

    private fun isIgnoredTargetInputNode(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty()
        val nodePackage = node.packageName?.toString()
        return nodePackage == packageName || nodePackage == SYSTEM_UI_PACKAGE ||
            node.isEditable ||
            className == "android.widget.EditText" ||
            isLauncherNode(node)
    }

    private fun isLauncherNode(node: AccessibilityNodeInfo): Boolean {
        val packageName = node.packageName?.toString().orEmpty()
        return packageName in LAUNCHER_PACKAGES
    }

    private fun showClickIndicator(bounds: Rect) {
        if (isScreenLocked()) return

        val indicatorBounds = Rect(bounds)
        val showIndicator = Runnable {
            pendingShowClickIndicator = null
            if (isServiceDestroyed || !isAutoClickEnabled || isScreenLocked()) {
                return@Runnable
            }

            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val sizePx = indicatorSizePx()
            removeClickIndicator()
            val view = ImageView(this).apply {
                setImageResource(R.drawable.ic_touch_hand)
                setPadding(indicatorPaddingPx(), indicatorPaddingPx(), indicatorPaddingPx(), indicatorPaddingPx())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xCC202124.toInt())
                }
                contentDescription = getString(R.string.click_indicator_description)
            }

            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            params.x = indicatorBounds.centerX() - sizePx / 2
            params.y = indicatorBounds.centerY() - sizePx / 2
            try {
                wm.addView(view, params)
            } catch (_: WindowManager.BadTokenException) {
                return@Runnable
            }
            clickIndicatorView = view
            mainHandler.removeCallbacks(hideClickIndicator)
            mainHandler.postDelayed(hideClickIndicator, CLICK_INDICATOR_DURATION_MS)
        }
        pendingShowClickIndicator?.let(mainHandler::removeCallbacks)
        pendingShowClickIndicator = showIndicator
        mainHandler.post(showIndicator)
    }

    private val hideClickIndicator = Runnable {
        removeClickIndicator()
    }

    private fun indicatorSizePx(): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        INDICATOR_SIZE_DP,
        resources.displayMetrics
    ).toInt()

    private fun indicatorPaddingPx(): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        INDICATOR_PADDING_DP,
        resources.displayMetrics
    ).toInt()

    private fun isScreenLocked(): Boolean {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        val powerManager = getSystemService(PowerManager::class.java)
        val screenOff = powerManager?.isInteractive == false
        val keyguardLocked = keyguardManager?.isKeyguardLocked == true
        val deviceLocked = keyguardManager?.isDeviceLocked == true
        return screenOff || keyguardLocked || deviceLocked
    }

    private fun detachOverlay() {
        pendingShowClickIndicator?.let(mainHandler::removeCallbacks)
        pendingShowClickIndicator = null
        mainHandler.removeCallbacks(hideClickIndicator)
        if (Looper.myLooper() == mainHandler.looper) {
            removeClickIndicator()
        } else {
            mainHandler.post(::removeClickIndicator)
        }
    }

    private fun removeClickIndicator() {
        val view = clickIndicatorView
        clickIndicatorView = null
        if (view?.parent != null) {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            try {
                wm.removeViewImmediate(view)
            } catch (_: IllegalArgumentException) {
                // The system may already have detached an accessibility overlay.
            }
        }
    }

    private fun playClickSignalIfEnabled() {
        if (!isSoundEnabled) return
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
    }

    private fun reloadPrefs() {
        isAutoClickEnabled = AutoClickPrefs.isEnabled(this)
        if (!isAutoClickEnabled) {
            detachOverlay()
        }
        isSoundEnabled = prefs.getBoolean("sound_enabled", true)
        targetText = prefs.getString("target_text", "").orEmpty().trim()
        accessibilityGuideRequested = prefs.getBoolean(KEY_GUIDE_REQUESTED, false)
        if (isServiceConnected) {
            TriggerNotification.show(this)
        }
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
        private const val CLICK_INDICATOR_DURATION_MS = 1000L
        private const val BEEP_DURATION_MS = 120
        private const val TONE_VOLUME = 80
        private const val GUIDE_SCROLL_COOLDOWN_MS = 700L
        private const val KEY_GUIDE_REQUESTED = "accessibility_guide_requested"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val INDICATOR_SIZE_DP = 64f
        private const val INDICATOR_PADDING_DP = 12f
        private const val MAX_TRIGGER_TEXT_WORDS = 2
        private val WORD_SEPARATOR = Regex("\\s+")
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
