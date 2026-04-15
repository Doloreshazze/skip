package com.playeverywhere999.skip

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.playeverywhere999.skip.ui.theme.SkipTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val insetsController by lazy { WindowInsetsControllerCompat(window, window.decorView) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        installInsetsAutoHide()
        hideSystemBars()
        setContent {
            SkipTheme {
                AutoClickScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun installInsetsAutoHide() {
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            val barsVisible = insets.isVisible(WindowInsetsCompat.Type.navigationBars()) ||
                insets.isVisible(WindowInsetsCompat.Type.statusBars())
            if (barsVisible) {
                window.decorView.post { hideSystemBars() }
            }
            insets
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun AutoClickScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var targetText by rememberSaveable { mutableStateOf(AutoClickPrefs.targetText(context)) }
    var enabled by rememberSaveable { mutableStateOf(AutoClickPrefs.isEnabled(context)) }
    var soundEnabled by rememberSaveable { mutableStateOf(AutoClickPrefs.isSoundEnabled(context)) }
    var disclosureAccepted by rememberSaveable { mutableStateOf(AutoClickPrefs.isDisclosureAccepted(context)) }
    var accessibilityEnabled by rememberSaveable { mutableStateOf(AccessibilityUtils.isServiceEnabled(context)) }
    var guideRequested by rememberSaveable { mutableStateOf(AutoClickPrefs.isAccessibilityGuideRequested(context)) }
    var screenLocked by remember { mutableStateOf(isScreenLocked(context)) }
    var permissionAttentionTrigger by remember { mutableIntStateOf(0) }
    var privacyExpanded by rememberSaveable { mutableStateOf(false) }
    var showAllowOverlay by rememberSaveable { mutableStateOf(false) }
    val permissionCardOffset = remember { Animatable(0f) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = AccessibilityUtils.isServiceEnabled(context)
                guideRequested = AutoClickPrefs.isAccessibilityGuideRequested(context)
                screenLocked = isScreenLocked(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionAttentionTrigger) {
        if (permissionAttentionTrigger == 0) return@LaunchedEffect

        val shakeOffsets = listOf(-30f, 30f, -24f, 24f, -12f, 12f, 0f)
        for (offset in shakeOffsets) {
            permissionCardOffset.animateTo(
                targetValue = offset,
                animationSpec = tween(durationMillis = 45)
            )
        }
    }

    val gradientBackground = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
            MaterialTheme.colorScheme.background
        )
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        if (!accessibilityEnabled) {
            PermissionInstructionFirstPage(
                disclosureAccepted = disclosureAccepted,
                privacyExpanded = privacyExpanded,
                onPrivacyToggle = { privacyExpanded = !privacyExpanded },
                showAllowOverlay = showAllowOverlay,
                onDismissOverlay = { showAllowOverlay = false },
                onCancel = { (context as? Activity)?.finish() },
                onAllowClick = {
                    if (!disclosureAccepted) {
                        disclosureAccepted = true
                        AutoClickPrefs.setDisclosureAccepted(context, true)
                    }
                    showAllowOverlay = true
                },
                onConfirmOverlay = {
                    showAllowOverlay = false
                    AutoClickPrefs.setAccessibilityGuideRequested(context, true)
                    guideRequested = true
                    openAccessibilitySettings(context)
                }
            )
            return@Surface
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.hero_badge),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.hero_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.hero_steps),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.section_target_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = targetText,
                            onValueChange = {
                                targetText = it
                                AutoClickPrefs.setTargetText(context, it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.target_text_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingToggleRow(
                            title = stringResource(R.string.toggle_autoclick_title),
                            subtitle = if (enabled) stringResource(R.string.state_active) else stringResource(R.string.state_disabled),
                            checked = enabled,
                            enabled = disclosureAccepted,
                            onCheckedChange = {
                                if (!disclosureAccepted) {
                                    permissionAttentionTrigger++
                                    return@SettingToggleRow
                                }
                                val canEnable = !it || accessibilityEnabled
                                enabled = canEnable && it
                                AutoClickPrefs.setEnabled(context, enabled)
                                if (it && !accessibilityEnabled) {
                                    permissionAttentionTrigger++
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        SettingToggleRow(
                            title = stringResource(R.string.toggle_sound_title),
                            subtitle = if (soundEnabled) stringResource(R.string.state_sound_on) else stringResource(R.string.state_sound_off),
                            checked = soundEnabled,
                            enabled = disclosureAccepted,
                            onCheckedChange = {
                                if (!disclosureAccepted) return@SettingToggleRow
                                soundEnabled = it
                                AutoClickPrefs.setSoundEnabled(context, it)
                            }
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.5.dp,
                            color = if (disclosureAccepted) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            } else {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                            },
                            shape = RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (disclosureAccepted) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.disclosure_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (disclosureAccepted) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.disclosure_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.disclosure_confirm_title),
                            subtitle = if (disclosureAccepted) {
                                stringResource(R.string.disclosure_confirmed_state)
                            } else {
                                stringResource(R.string.disclosure_unconfirmed_state)
                            },
                            checked = disclosureAccepted,
                            onCheckedChange = {
                                disclosureAccepted = it
                                AutoClickPrefs.setDisclosureAccepted(context, it)
                                if (!it) {
                                    enabled = false
                                    AutoClickPrefs.setEnabled(context, false)
                                }
                            }
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(permissionCardOffset.value.toInt(), 0) }
                        .border(
                            width = 1.dp,
                            color = if (accessibilityEnabled) {
                                Color(0xFF2E7D32).copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            },
                            shape = RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (accessibilityEnabled) {
                            Color(0xFF2E7D32).copy(alpha = 0.09f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (accessibilityEnabled) {
                                stringResource(R.string.permission_ready_title)
                            } else {
                                stringResource(R.string.permission_needed_title)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (accessibilityEnabled) {
                                stringResource(R.string.permission_ready_message)
                            } else {
                                stringResource(R.string.permission_needed_message)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        GuideStatusRow(
                            active = !accessibilityEnabled && guideRequested
                        )
                        if (!screenLocked) {
                            Button(
                                onClick = {
                                    if (!disclosureAccepted) {
                                        permissionAttentionTrigger++
                                        return@Button
                                    }
                                    AutoClickPrefs.setAccessibilityGuideRequested(context, true)
                                    guideRequested = true
                                    openAccessibilitySettings(context)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = disclosureAccepted
                            ) {
                                Text(stringResource(R.string.open_accessibility_settings), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun PermissionInstructionFirstPage(
    disclosureAccepted: Boolean,
    privacyExpanded: Boolean,
    onPrivacyToggle: () -> Unit,
    showAllowOverlay: Boolean,
    onDismissOverlay: () -> Unit,
    onCancel: () -> Unit,
    onAllowClick: () -> Unit,
    onConfirmOverlay: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val policyUrl = stringResource(R.string.permission_intro_privacy_policy_url)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.permission_intro_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.permission_intro_subtitle),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            InstructionCard(
                title = stringResource(R.string.permission_intro_why_title),
                body = stringResource(R.string.permission_intro_why_text)
            )

        InstructionCard(
            title = stringResource(R.string.permission_intro_how_title),
            body = stringResource(R.string.permission_intro_how_text)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPrivacyToggle),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.permission_intro_privacy_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (privacyExpanded) "▲" else "▼",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (privacyExpanded) {
                    Text(
                        text = stringResource(R.string.permission_intro_privacy_body),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.permission_intro_privacy_policy_prefix),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.permission_intro_privacy_policy_link),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF2AA6FF),
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            uriHandler.openUri(policyUrl)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(text = stringResource(R.string.permission_intro_cancel))
                }
                Button(
                    onClick = onAllowClick,
                    modifier = Modifier.weight(1.7f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF41B129),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = stringResource(R.string.permission_intro_allow))
                }
            }
        }

        if (showAllowOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismissOverlay() }
            )
            AllowInstructionOverlay(
                onClose = onDismissOverlay,
                onConfirm = onConfirmOverlay,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun AllowInstructionOverlay(
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pageCount = 3
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pageCount - 1
    val transition = rememberInfiniteTransition(label = "overlayHighlight")
    val highlightAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "overlayHighlightAlpha"
    )

    LaunchedEffect(isDragged) {
        while (true) {
            delay(2800)
            if (isDragged) continue
            val nextPage = (pagerState.currentPage + 1) % pageCount
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF313B57))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.permission_overlay_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "✕",
                        color = Color(0xFFA9B0C3),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.clickable(onClick = onClose)
                    )
                }

                Text(
                    text = stringResource(R.string.permission_overlay_body),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFE6E9F2)
                )

                OverlayStepLine(
                    index = 0,
                    activeIndex = pagerState.currentPage,
                    text = stringResource(R.string.permission_overlay_step_1)
                )
                OverlayStepLine(
                    index = 1,
                    activeIndex = pagerState.currentPage,
                    text = stringResource(
                        R.string.permission_overlay_step_2,
                        stringResource(R.string.app_name)
                    )
                )
                OverlayStepLine(
                    index = 2,
                    activeIndex = pagerState.currentPage,
                    text = stringResource(R.string.permission_overlay_step_3)
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    pageSpacing = 12.dp
                ) { page ->
                    FakeSettingsSlide(
                        page = page,
                        highlightAlpha = highlightAlpha
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(pageCount) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(if (index == pagerState.currentPage) 20.dp else 8.dp)
                                .height(8.dp)
                                .background(
                                    color = if (index == pagerState.currentPage) Color(0xFFE9EDF7) else Color(0xFF7F879A),
                                    shape = RoundedCornerShape(50)
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    if (!isLastPage) {
                        val next = (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(next)
                        }
                    } else {
                        Toast.makeText(context, appName, Toast.LENGTH_SHORT).show()
                        onConfirm()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF41B129),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (isLastPage) {
                        stringResource(R.string.permission_intro_allow)
                    } else {
                        stringResource(R.string.permission_overlay_next)
                    },
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
private fun OverlayStepLine(
    index: Int,
    activeIndex: Int,
    text: String
) {
    val active = index == activeIndex
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (active) "▶" else " ",
            color = if (active) Color(0xFFFFD60A) else Color.Transparent,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = if (active) Color(0xFFF2F4FA) else Color(0xFF9EA6B9),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun FakeSettingsSlide(
    page: Int,
    highlightAlpha: Float
) {
    val appName = stringResource(R.string.app_name)
    val switchTransition = rememberInfiniteTransition(label = "fakeSwitch")
    val switchProgress by switchTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2200
                0f at 0
                0f at 750
                1f at 1250
                1f at 2000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "fakeSwitchProgress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFADADAE))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFFA9A9AA))
            ) {
                Text(
                    text = when (page) {
                        0 -> "Специальные возможности"
                        2 -> appName
                        else -> "Специальные"
                    },
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (page == 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .background(
                                Color(0xFFB8D9FF).copy(alpha = highlightAlpha),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (switchProgress < 0.55f) "Выключено" else "Включено",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF183A66),
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .width(54.dp)
                                    .height(30.dp)
                                    .background(
                                        lerp(Color(0xFF9EA5B3), Color(0xFF4F84F4), switchProgress),
                                        RoundedCornerShape(20.dp)
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .offset(x = (3 + 22 * switchProgress).dp)
                                        .width(24.dp)
                                        .height(24.dp)
                                        .background(Color.White, RoundedCornerShape(50))
                                )
                            }
                        }
                    }
                } else {
                    repeat(3) { row ->
                        val isHighlighted = (page == 0 && row == 2) || (page == 1 && row == 0)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(
                                    if (isHighlighted) Color(0xFFB8D9FF).copy(alpha = highlightAlpha) else Color(0xFFE4E4E4),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = when {
                                    page == 0 && row == 2 -> "Установленные службы"
                                    page == 1 && row == 0 -> appName
                                    page == 0 && row == 0 -> "Нарушение координации и взаимодействие"
                                    page == 0 && row == 1 -> "Дополнительные параметры"
                                    page == 1 && row == 1 -> "Другое ваше приложение"
                                    page == 1 && row == 2 -> "Ещё одно приложение"
                                    else -> "Раздел настроек"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isHighlighted) Color(0xFF183A66) else Color(0xFF434343)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFFFD60A),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private fun openAccessibilitySettings(context: android.content.Context) {
    val serviceComponent = android.content.ComponentName(
        context,
        AutoClickAccessibilityService::class.java
    )

    val detailIntent = Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        putExtra(Intent.EXTRA_COMPONENT_NAME, serviceComponent.flattenToString())
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val fallbackIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val intentToLaunch = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        detailIntent.resolveActivity(context.packageManager) != null
    ) {
        detailIntent
    } else {
        fallbackIntent
    }

    context.startActivity(intentToLaunch)
}

private fun isScreenLocked(context: android.content.Context): Boolean {
    val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    val powerManager = context.getSystemService(PowerManager::class.java)
    val screenOff = powerManager?.isInteractive == false
    val keyguardLocked = keyguardManager?.isKeyguardLocked == true
    val deviceLocked = keyguardManager?.isDeviceLocked == true
    return screenOff || keyguardLocked || deviceLocked
}

private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
    "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

@Composable
private fun GuideStatusRow(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "guideStatus")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "guideAlpha"
    )
    val tint = if (active) {
        MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Text(
        text = if (active) {
            stringResource(R.string.guide_status_active)
        } else {
            stringResource(R.string.guide_status_idle)
        },
        color = tint,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
