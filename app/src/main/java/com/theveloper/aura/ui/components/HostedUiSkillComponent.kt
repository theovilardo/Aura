package com.theveloper.aura.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.theveloper.aura.domain.model.HostedUiConfig
import com.theveloper.aura.domain.model.UiSkillRuntime

@Composable
fun HostedUiSkillComponent(
    config: HostedUiConfig,
    modifier: Modifier = Modifier
) {
    when (config.runtime) {
        UiSkillRuntime.HTML_JS -> HtmlHostedUi(config = config, modifier = modifier)
        UiSkillRuntime.COMPOSE_HOSTED -> HostedUiPlaceholder(
            title = config.displayLabel.ifBlank { "Compose UI-Skill" },
            body = "Compose-hosted skill registered and ready for a dedicated host.",
            modifier = modifier
        )
        UiSkillRuntime.NATIVE -> HostedUiPlaceholder(
            title = config.displayLabel.ifBlank { "Hosted UI-Skill" },
            body = "This skill is marked as hosted but currently points to the native runtime.",
            modifier = modifier
        )
    }
}

@Composable
private fun HostedUiPlaceholder(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun HtmlHostedUi(
    config: HostedUiConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val html = remember(config, context) {
        when {
            !config.htmlDocument.isNullOrBlank() -> config.htmlDocument
            !config.sourceAssetPath.isNullOrBlank() -> runCatching {
                context.assets.open(config.sourceAssetPath).bufferedReader().use { it.readText() }
            }.getOrNull()
            else -> null
        }
    }

    if (html.isNullOrBlank()) {
        HostedUiPlaceholder(
            title = config.displayLabel.ifBlank { "HTML/JS UI-Skill" },
            body = "No HTML document or asset entrypoint was provided for this hosted skill.",
            modifier = modifier
        )
        return
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp),
        factory = { viewContext ->
            WebView(viewContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
    )
}
