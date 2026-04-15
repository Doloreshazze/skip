package com.playeverywhere999.skip

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
                onCancel = { (context as? Activity)?.finish() },
                onAllowClick = {
                    if (!disclosureAccepted) {
                        permissionAttentionTrigger++
                        return@PermissionInstructionFirstPage
                    }
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
    onCancel: () -> Unit,
    onAllowClick: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val policyUrl = stringResource(R.string.permission_intro_privacy_policy_url)

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
                enabled = disclosureAccepted,
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
