package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Реклама. Менять её можно БЕЗ новой сборки: правьте файл ad.json на сайте (AD_CONFIG_URL).
// Значения ниже показываются, пока файл не загрузился или нет интернета.
private const val AD_LABEL = "Реклама"
private const val AD_TITLE = "Нужен сайт или ИИ-видео для бизнеса?"
private const val AD_TEXT = "Загляните на сайт студии и посмотрите примеры работ"
private const val AD_URL = "https://veraverkina2-svg.github.io/andrei-studio/"
private const val AD_CONFIG_URL = "https://veraverkina2-svg.github.io/andrei-studio/ad.json"

private val BgColor = Color(0xFF0B1020)
private val TextColor = Color(0xFFE9EEF8)
private val DimColor = Color(0xFF9FB0C8)

private data class AdData(val title: String, val text: String, val url: String, val enabled: Boolean)

private suspend fun loadAd(): AdData = withContext(Dispatchers.IO) {
    val fallback = AdData(AD_TITLE, AD_TEXT, AD_URL, true)
    runCatching {
        val c = URL(AD_CONFIG_URL).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 5000
        val body = c.inputStream.bufferedReader().use { it.readText() }
        val j = JSONObject(body)
        AdData(
            title = j.optString("title", AD_TITLE).ifBlank { AD_TITLE },
            text = j.optString("text", AD_TEXT),
            url = j.optString("url", AD_URL).ifBlank { AD_URL },
            enabled = j.optBoolean("enabled", true),
        )
    }.getOrDefault(fallback)
}

/**
 * Экран «одна кнопка»: большая круглая кнопка ВКЛ/ВЫКЛ, строка состояния и рекламный баннер.
 * В публичной версии (без вшитого ключа) при первом запуске просит вставить ссылку доступа.
 */
@Composable
fun OneButtonScreen(
    mainViewModel: MainViewModel,
    onToggle: () -> Unit,
    onImportLink: (String) -> Unit,
) {
    val state by mainViewModel.uiState.collectAsStateWithLifecycle()
    val running = state.isRunning
    val hasProfile = !state.selectedGuid.isNullOrEmpty()
    val needsLink = !hasProfile && BundledProfile.LINK.isBlank()
    val ad by produceState(AdData(AD_TITLE, AD_TEXT, AD_URL, true)) { value = loadAd() }

    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        if (needsLink) {
            LinkEntry(
                modifier = Modifier.align(Alignment.Center),
                onImportLink = onImportLink,
            )
        } else {
            PowerButton(running = running, onToggle = onToggle, modifier = Modifier.align(Alignment.Center))
        }

        if (ad.enabled) {
            AdBanner(
                ad = ad,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun PowerButton(running: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val buttonColor = if (running) Color(0xFF2E9E5B) else Color(0xFF6B7280)
    val title = if (running) "ВКЛ" else "ВЫКЛ"
    val caption = if (running) {
        "VPN включён. Нажмите, чтобы выключить"
    } else {
        "VPN выключен. Нажмите, чтобы включить"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = caption,
            color = TextColor,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun LinkEntry(modifier: Modifier = Modifier, onImportLink: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    var text by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Вставьте ссылку доступа",
            color = TextColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Её присылает владелец сервиса. Она начинается с vless://",
            color = DimColor,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ссылка доступа") },
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                focusedLabelColor = DimColor,
                unfocusedLabelColor = DimColor,
                cursorColor = TextColor,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { onImportLink(text) }) {
            Text("Добавить")
        }
        TextButton(onClick = { runCatching { uriHandler.openUri(AD_URL) } }) {
            Text("Как получить доступ")
        }
    }
}

@Composable
private fun AdBanner(ad: AdData, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1F3A5F))
            .clickable { runCatching { uriHandler.openUri(ad.url) } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = AD_LABEL,
            color = Color(0xFF9FB4D0),
            fontSize = 11.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = ad.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        if (ad.text.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = ad.text,
                color = Color(0xFFD6E0EE),
                fontSize = 14.sp,
            )
        }
    }
}
