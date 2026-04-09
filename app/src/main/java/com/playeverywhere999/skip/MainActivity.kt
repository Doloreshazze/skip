package com.playeverywhere999.skip

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.playeverywhere999.skip.ui.theme.SkipTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkipTheme {
                AutoClickScreen()
            }
        }
    }
}

@Composable
private fun AutoClickScreen() {
    val context = LocalContext.current
    var targetText by rememberSaveable { mutableStateOf(AutoClickPrefs.targetText(context)) }
    var enabled by rememberSaveable { mutableStateOf(AutoClickPrefs.isEnabled(context)) }
    var soundEnabled by rememberSaveable { mutableStateOf(AutoClickPrefs.isSoundEnabled(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Автоклик по тексту",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "1) Введите точный текст кнопки.\n" +
                "2) Включите переключатель.\n" +
                "3) Выдайте Accessibility-доступ (кнопка ниже).\n" +
                "После этого сервис будет сканировать UI-дерево поверх любых приложений и нажимать найденную кнопку.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = targetText,
            onValueChange = {
                targetText = it
                AutoClickPrefs.setTargetText(context, it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Текст кнопки") },
            singleLine = true
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = if (enabled) "Автоклик включен" else "Автоклик выключен")
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    AutoClickPrefs.setEnabled(context, it)
                }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = if (soundEnabled) "Звуковой сигнал: включен" else "Звуковой сигнал: выключен")
            Switch(
                checked = soundEnabled,
                onCheckedChange = {
                    soundEnabled = it
                    AutoClickPrefs.setSoundEnabled(context, it)
                }
            )
        }

        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        ) {
            Text("Открыть Accessibility настройки")
        }
    }
}
