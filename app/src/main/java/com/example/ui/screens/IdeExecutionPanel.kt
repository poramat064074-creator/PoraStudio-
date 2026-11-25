package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import androidx.annotation.Keep
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.i18n.LocalStrings
import com.example.data.local.LocalExecutionHistoryEntity
import com.example.data.model.ExecutionResult
import com.example.data.model.ExecutionStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IdeExecutionPanel(
    result: ExecutionResult,
    stdinInput: String,
    onStdinChange: (String) -> Unit,
    history: List<LocalExecutionHistoryEntity>,
    onConfigureSandbox: () -> Unit,
    onAskAiReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var selectedTab by remember(result.htmlPreviewContent, result.status) {
        mutableIntStateOf(
            when {
                result.htmlPreviewContent != null -> 2
                result.status == ExecutionStatus.ERROR || result.status == ExecutionStatus.RUNTIME_UNAVAILABLE -> 1
                else -> 0
            }
        )
    }
    val previewConsoleLogs = remember(result.htmlPreviewContent) { mutableStateListOf<String>() }
    val previewConsoleErrors = remember(result.htmlPreviewContent) { mutableStateListOf<String>() }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var mobileViewportMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        // Status & Runtime bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (statusLabel, statusColor) = when (result.status) {
                ExecutionStatus.IDLE -> strings.statusIdle to MaterialTheme.colorScheme.onSurfaceVariant
                ExecutionStatus.RUNNING -> strings.statusRunning to MaterialTheme.colorScheme.primary
                ExecutionStatus.SUCCESS -> strings.statusSuccess to EmeraldTertiary
                ExecutionStatus.ERROR -> strings.statusError to CrimsonError
                ExecutionStatus.STOPPED -> strings.statusStopped to AmberWarning
                ExecutionStatus.RUNTIME_UNAVAILABLE -> strings.statusRuntimeUnavailable to AmberWarning
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (result.durationMs > 0) {
                    Text(
                        text = "${result.durationMs} ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = result.runtimeDescription,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(strings.outputConsole) })
            val errCount = previewConsoleErrors.size + if (result.stderr.isNotBlank()) 1 else 0
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(if (errCount > 0) "${strings.errorConsole} ($errCount)" else strings.errorConsole)
                }
            )
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(strings.livePreview) })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text(strings.executionHistory) })
        }

        when (selectedTab) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stdinInput,
                        onValueChange = onStdinChange,
                        placeholder = { Text(strings.stdinInputHint) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ide_stdin_input")
                    )

                    if (result.sqlColumns.isNotEmpty()) {
                        SqlTableResultView(columns = result.sqlColumns, rows = result.sqlRows)
                    }

                    val outputText = buildString {
                        if (result.stdout.isNotBlank()) append(result.stdout)
                        if (previewConsoleLogs.isNotEmpty()) {
                            if (isNotEmpty()) append("\n")
                            append(previewConsoleLogs.joinToString("\n"))
                        }
                    }

                    Text(
                        text = outputText.ifBlank { "(No output yet — tap Run ▶)" },
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("ide_output_text")
                    )
                }
            }
            1 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (result.status == ExecutionStatus.RUNTIME_UNAVAILABLE) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AmberWarning, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = result.missingRuntimeHint ?: result.stderr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onConfigureSandbox) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.configureSandbox)
                                    }
                                    OutlinedButton(onClick = onAskAiReview) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.askAiToReview)
                                    }
                                }
                            }
                        }
                    } else {
                        val combinedErrors = buildString {
                            if (result.stderr.isNotBlank()) appendLine(result.stderr)
                            if (previewConsoleErrors.isNotEmpty()) {
                                append(previewConsoleErrors.joinToString("\n"))
                            }
                        }.trimEnd()

                        Text(
                            text = combinedErrors.ifBlank { "(No errors)" },
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (combinedErrors.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else CrimsonError,
                            modifier = Modifier.testTag("ide_error_text")
                        )
                        if (combinedErrors.isNotBlank()) {
                            OutlinedButton(onClick = onAskAiReview) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.askAiToReview)
                            }
                        }
                    }
                }
            }
            2 -> {
                val html = result.htmlPreviewContent
                if (html != null) {
                    var activeWebView by remember { mutableStateOf<WebView?>(null) }
                    DisposableEffect(Unit) {
                        onDispose {
                            activeWebView?.stopLoading()
                            activeWebView?.loadUrl("about:blank")
                            activeWebView?.destroy()
                            activeWebView = null
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sandboxed Preview (Network Isolated)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row {
                                IconButton(
                                    onClick = { mobileViewportMode = !mobileViewportMode },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (mobileViewportMode) Icons.Default.PhoneAndroid else Icons.Default.DesktopWindows,
                                        contentDescription = "Toggle viewport",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        previewConsoleLogs.clear()
                                        previewConsoleErrors.clear()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear preview logs", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { reloadNonce++ },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reload preview", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            val renderedKey = remember(html, reloadNonce) { "$reloadNonce:${html.hashCode()}" }
                            var lastRenderedKey by remember { mutableStateOf("") }

                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        activeWebView = this
                                        settings.javaScriptEnabled = true
                                        settings.allowFileAccess = false
                                        settings.allowContentAccess = false
                                        settings.blockNetworkLoads = true
                                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                        addJavascriptInterface(
                                            PreviewConsoleBridge(
                                                onLog = { level, message ->
                                                    post {
                                                        if (previewConsoleLogs.size < 120) previewConsoleLogs.add("[Preview $level] $message")
                                                    }
                                                },
                                                onError = { message ->
                                                    post {
                                                        if (previewConsoleErrors.size < 80) previewConsoleErrors.add("[Preview ERROR] $message")
                                                    }
                                                }
                                            ),
                                            "CodeVersePreviewBridge"
                                        )
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                                                cm?.let {
                                                    val text = "[WebView ${it.messageLevel()}] ${it.message()}"
                                                    if (it.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                                        if (previewConsoleErrors.size < 80 && text !in previewConsoleErrors) {
                                                            previewConsoleErrors.add(text)
                                                        }
                                                    }
                                                }
                                                return true
                                            }
                                        }
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

                                            override fun shouldInterceptRequest(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): WebResourceResponse? {
                                                val url = request?.url?.toString().orEmpty()
                                                return if (url.startsWith("https://localhost.sandbox/") || url.startsWith("data:") || url.startsWith("blob:")) {
                                                    super.shouldInterceptRequest(view, request)
                                                } else {
                                                    WebResourceResponse(
                                                        "text/plain",
                                                        "utf-8",
                                                        403,
                                                        "Blocked by Sandbox",
                                                        emptyMap(),
                                                        ByteArrayInputStream(ByteArray(0))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                update = { webView ->
                                    if (lastRenderedKey != renderedKey) {
                                        lastRenderedKey = renderedKey
                                        webView.loadDataWithBaseURL("https://localhost.sandbox/", html, "text/html", "UTF-8", null)
                                    }
                                },
                                modifier = if (mobileViewportMode) {
                                    Modifier.widthIn(max = 375.dp).fillMaxSize()
                                } else {
                                    Modifier.fillMaxSize()
                                }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Run an HTML or CSS file to launch the live interactive WebView preview.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            3 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (history.isEmpty()) {
                        Text(
                            text = "No execution history recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        history.forEach { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${item.fileName} (${item.languageId})", style = MaterialTheme.typography.labelLarge)
                                        Text("${item.status} • ${item.durationMs}ms", style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (item.outputPreview.isNotBlank()) {
                                        Text(
                                            text = item.outputPreview,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SqlTableResultView(columns: List<String>, rows: List<List<String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(8.dp)
        ) {
            columns.forEach { col ->
                Text(
                    text = col,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.width(120.dp)
                )
            }
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(8.dp)
            ) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(120.dp)
                    )
                }
            }
        }
    }
}

@Keep
private class PreviewConsoleBridge(
    private val onLog: (String, String) -> Unit,
    private val onError: (String) -> Unit
) {
    @JavascriptInterface
    fun onPreviewLog(level: String, message: String) {
        if (level == "ERROR") {
            onError(message)
        } else {
            onLog(level, message)
        }
    }
}
