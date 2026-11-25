package com.example.domain.execution

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import com.example.data.local.LocalFileEntity
import com.example.data.local.UserPreferences
import com.example.data.model.CodeLanguage
import com.example.data.model.ExecutionResult
import com.example.data.model.ExecutionStatus
import com.example.domain.execution.python.PythonRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

@Keep
private class JsSandboxBridge(
    private val stdoutBuf: StringBuilder,
    private val stderrBuf: StringBuilder,
    private val deferred: CompletableDeferred<Pair<String, String>>
) {
    @JavascriptInterface
    fun onLog(msg: String) {
        synchronized(stdoutBuf) {
            if (stdoutBuf.length < 200_000) stdoutBuf.appendLine(msg)
        }
    }

    @JavascriptInterface
    fun onError(msg: String) {
        synchronized(stderrBuf) {
            if (stderrBuf.length < 100_000) stderrBuf.appendLine(msg)
        }
    }

    @JavascriptInterface
    fun onDone() {
        if (!deferred.isCompleted) {
            val out = synchronized(stdoutBuf) { stdoutBuf.toString().trimEnd() }
            val err = synchronized(stderrBuf) { stderrBuf.toString().trimEnd() }
            deferred.complete(out to err)
        }
    }
}

class ExecutionEngine(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun execute(
        activeFile: LocalFileEntity,
        projectFiles: List<LocalFileEntity>,
        stdinInput: String,
        preferences: UserPreferences
    ): ExecutionResult {
        val startMs = System.currentTimeMillis()
        val language = CodeLanguage.fromId(activeFile.languageId)
        val timeoutMs = preferences.sandboxTimeoutMs.toLong().coerceIn(2_000L, 30_000L)

        return when (language) {
            CodeLanguage.HTML, CodeLanguage.CSS -> executeHtmlBundle(activeFile, projectFiles, startMs)
            CodeLanguage.JAVASCRIPT -> executeJavaScriptOnDevice(activeFile.content, stdinInput, timeoutMs, startMs)
            CodeLanguage.PYTHON -> executePython(
                activeFile = activeFile,
                projectFiles = projectFiles,
                stdinInput = stdinInput,
                preferences = preferences,
                timeoutMs = timeoutMs,
                startMs = startMs
            )
            CodeLanguage.SQL -> executeSqlOnDevice(activeFile.content, startMs)
            CodeLanguage.JSON -> validateJsonOnDevice(activeFile.content, startMs)
            CodeLanguage.XML -> validateXmlOnDevice(activeFile.content, startMs)
            else -> executeViaRemoteSandboxOrExplain(
                code = activeFile.content,
                fileName = activeFile.name,
                language = language,
                stdinInput = stdinInput,
                preferences = preferences,
                startMs = startMs
            )
        }
    }

    internal fun bundleHtmlDocument(
        activeFile: LocalFileEntity,
        projectFiles: List<LocalFileEntity>
    ): String {
        val htmlSource = if (activeFile.languageId == CodeLanguage.HTML.id) {
            activeFile.content
        } else {
            projectFiles.firstOrNull { it.languageId == CodeLanguage.HTML.id }?.content
                ?: """
                    <!DOCTYPE html>
                    <html>
                    <head>
                      <meta charset="UTF-8" />
                      <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    </head>
                    <body>
                      <div class="container">
                        <div class="card">
                          <h1>CSS Live Preview</h1>
                          <p>Styled elements rendered inside PoraStudio isolated WebView sandbox.</p>
                          <button type="button">Primary Action</button>
                        </div>
                      </div>
                    </body>
                    </html>
                """.trimIndent()
        }

        val cssFiles = projectFiles.filter { it.languageId == CodeLanguage.CSS.id }
        val jsFiles = projectFiles.filter { it.languageId == CodeLanguage.JAVASCRIPT.id }

        var bundled = htmlSource
        cssFiles.forEach { css ->
            val targetName = Regex.escape(css.name)
            val targetPath = Regex.escape(css.path)
            val linkRegex = Regex(
                """<link[^>]*href\s*=\s*["'](?:\./)?(?:$targetPath|$targetName)["'][^>]*>""",
                RegexOption.IGNORE_CASE
            )
            val styleTag = "<style>\n${css.content}\n</style>"
            bundled = when {
                linkRegex.containsMatchIn(bundled) -> linkRegex.replace(bundled, Regex.escapeReplacement(styleTag))
                bundled.contains("</head>", ignoreCase = true) ->
                    bundled.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), Regex.escapeReplacement("$styleTag\n</head>"))
                else -> "$styleTag\n$bundled"
            }
        }

        jsFiles.forEach { js ->
            val targetName = Regex.escape(js.name)
            val targetPath = Regex.escape(js.path)
            val scriptRegex = Regex(
                """<script[^>]*src\s*=\s*["'](?:\./)?(?:$targetPath|$targetName)["'][^>]*>\s*</script>""",
                RegexOption.IGNORE_CASE
            )
            if (scriptRegex.containsMatchIn(bundled)) {
                bundled = scriptRegex.replace(bundled, Regex.escapeReplacement("<script>\n${js.content}\n</script>"))
            }
        }

        // Inject CSP + runtime error/console forwarding hook without modifying user source files
        val sandboxPrologue = """
            <meta http-equiv="Content-Security-Policy" content="default-src 'unsafe-inline' 'unsafe-eval' data: blob: https://localhost.sandbox;">
            <script>
              (function() {
                if (window.__codeverseHooked) return;
                window.__codeverseHooked = true;
                function emit(level, msg) {
                  if (window.CodeVersePreviewBridge && window.CodeVersePreviewBridge.onPreviewLog) {
                    window.CodeVersePreviewBridge.onPreviewLog(level, String(msg));
                  }
                }
                const __log = console.log;
                const __warn = console.warn;
                const __err = console.error;
                console.log = function(...args) {
                  emit("LOG", args.map(a => typeof a === "object" ? JSON.stringify(a) : String(a)).join(" "));
                  __log && __log.apply(console, args);
                };
                console.warn = function(...args) {
                  emit("WARN", args.map(a => typeof a === "object" ? JSON.stringify(a) : String(a)).join(" "));
                  __warn && __warn.apply(console, args);
                };
                console.error = function(...args) {
                  emit("ERROR", args.map(a => typeof a === "object" ? JSON.stringify(a) : String(a)).join(" "));
                  __err && __err.apply(console, args);
                };
                window.addEventListener("error", function(e) {
                  emit("ERROR", (e.message || "Script Error") + (e.lineno ? " (line " + e.lineno + ")" : ""));
                });
                window.addEventListener("unhandledrejection", function(e) {
                  emit("ERROR", "Unhandled Promise Rejection: " + (e.reason && e.reason.message ? e.reason.message : String(e.reason)));
                });
              })();
            </script>
        """.trimIndent()

        bundled = when {
            bundled.contains("<head>", ignoreCase = true) ->
                bundled.replaceFirst(Regex("<head>", RegexOption.IGNORE_CASE), Regex.escapeReplacement("<head>\n$sandboxPrologue"))
            bundled.contains("<html>", ignoreCase = true) ->
                bundled.replaceFirst(Regex("<html>", RegexOption.IGNORE_CASE), Regex.escapeReplacement("<html>\n<head>\n$sandboxPrologue\n</head>"))
            else -> "<!DOCTYPE html><html><head>\n$sandboxPrologue\n</head><body>\n$bundled\n</body></html>"
        }

        return bundled
    }

    private fun executeHtmlBundle(
        activeFile: LocalFileEntity,
        projectFiles: List<LocalFileEntity>,
        startMs: Long
    ): ExecutionResult {
        if (activeFile.content.isBlank()) {
            return ExecutionResult(
                status = ExecutionStatus.ERROR,
                stderr = "${activeFile.name} is empty. Add HTML/CSS content to render preview.",
                durationMs = System.currentTimeMillis() - startMs,
                runtimeDescription = "Android Sandboxed WebView Engine"
            )
        }
        val bundled = bundleHtmlDocument(activeFile, projectFiles)
        return ExecutionResult(
            status = ExecutionStatus.SUCCESS,
            stdout = "Compiled isolated HTML/CSS/JS bundle (${bundled.length} bytes). Live sandboxed preview active.",
            htmlPreviewContent = bundled,
            durationMs = System.currentTimeMillis() - startMs,
            runtimeDescription = "Android Isolated WebView Engine (CSP + Network Blocked)"
        )
    }

    private suspend fun executePython(
        activeFile: LocalFileEntity,
        projectFiles: List<LocalFileEntity>,
        stdinInput: String,
        preferences: UserPreferences,
        timeoutMs: Long,
        startMs: Long
    ): ExecutionResult {
        if (preferences.sandboxUrl.isNotBlank()) {
            return executeViaRemoteSandboxOrExplain(
                code = activeFile.content,
                fileName = activeFile.name,
                language = CodeLanguage.PYTHON,
                stdinInput = stdinInput,
                preferences = preferences,
                startMs = startMs
            )
        }
        return executePythonOnDevice(
            code = activeFile.content,
            fileName = activeFile.name,
            projectFiles = projectFiles,
            stdinInput = stdinInput,
            timeoutMs = timeoutMs,
            startMs = startMs
        )
    }

    suspend fun executePythonOnDevice(
        code: String,
        fileName: String = "main.py",
        projectFiles: List<LocalFileEntity> = emptyList(),
        stdinInput: String = "",
        timeoutMs: Long = 10_000L,
        startMs: Long = System.currentTimeMillis()
    ): ExecutionResult {
        return try {
            withTimeout(timeoutMs) {
                val ctx = coroutineContext
                withContext(Dispatchers.Default) {
                    val runtime = PythonRuntime(
                        timeoutMs = timeoutMs,
                        stdinInput = stdinInput,
                        workspaceFiles = projectFiles,
                        coroutineContext = ctx
                    )
                    val outcome = runtime.execute(code, fileName)
                    ExecutionResult(
                        status = if (outcome.isSuccess) ExecutionStatus.SUCCESS else ExecutionStatus.ERROR,
                        stdout = outcome.stdout,
                        stderr = outcome.stderr,
                        durationMs = System.currentTimeMillis() - startMs,
                        runtimeDescription = "On-Device Sandboxed Python 3 VM (Timeout ${timeoutMs}ms)"
                    )
                }
            }
        } catch (_: TimeoutCancellationException) {
            ExecutionResult(
                status = ExecutionStatus.ERROR,
                stderr = "TimeoutError: Python execution timed out after ${timeoutMs}ms.",
                durationMs = System.currentTimeMillis() - startMs,
                runtimeDescription = "On-Device Sandboxed Python 3 VM"
            )
        } catch (e: Exception) {
            ExecutionResult(
                status = ExecutionStatus.ERROR,
                stderr = "Python VM Error: ${e.localizedMessage ?: e.javaClass.simpleName}",
                durationMs = System.currentTimeMillis() - startMs,
                runtimeDescription = "On-Device Sandboxed Python 3 VM"
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun executeJavaScriptOnDevice(
        code: String,
        stdinInput: String,
        timeoutMs: Long,
        startMs: Long
    ): ExecutionResult {
        val deferred = CompletableDeferred<Pair<String, String>>()
        val stdoutBuf = StringBuilder()
        val stderrBuf = StringBuilder()
        val mainHandler = Handler(Looper.getMainLooper())
        var webViewRef: WebView? = null
        val usesDomGlobals = Regex("""\b(document|window|alert|localStorage)\b""").containsMatchIn(code)

        return try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.Main) {
                    val webView = WebView(context.applicationContext)
                    webViewRef = webView
                    webView.settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        blockNetworkLoads = true
                    }

                    val stdinLines = JSONArray(stdinInput.lines())
                    val bridge = JsSandboxBridge(stdoutBuf, stderrBuf, deferred)

                    webView.addJavascriptInterface(bridge, "CodeVerseBridge")
                    webView.webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            consoleMessage?.let {
                                val line = "[${it.messageLevel()}] ${it.message()}"
                                if (it.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                    synchronized(stderrBuf) { stderrBuf.appendLine(line) }
                                } else {
                                    synchronized(stdoutBuf) { stdoutBuf.appendLine(line) }
                                }
                            }
                            return true
                        }
                    }
                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString().orEmpty()
                            return if (url.startsWith("https://localhost.sandbox/") || url.startsWith("blob:") || url.startsWith("data:")) {
                                super.shouldInterceptRequest(view, request)
                            } else {
                                WebResourceResponse(
                                    "text/plain",
                                    "utf-8",
                                    403,
                                    "Blocked by Offline Sandbox",
                                    emptyMap(),
                                    ByteArrayInputStream(ByteArray(0))
                                )
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            val harness = buildSandboxedJsHarness(code, stdinLines, timeoutMs, useWorker = !usesDomGlobals)
                            view?.evaluateJavascript(harness, null)
                        }
                    }
                    webView.loadDataWithBaseURL(
                        "https://localhost.sandbox/",
                        "<!DOCTYPE html><html><head><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'unsafe-inline' 'unsafe-eval' blob: data: https://localhost.sandbox;\"></head><body></body></html>",
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
                val (out, err) = deferred.await()
                ExecutionResult(
                    status = if (err.isBlank()) ExecutionStatus.SUCCESS else ExecutionStatus.ERROR,
                    stdout = out,
                    stderr = err,
                    durationMs = System.currentTimeMillis() - startMs,
                    runtimeDescription = if (usesDomGlobals) {
                        "Isolated WebView JS Engine (Network Blocked)"
                    } else {
                        "Isolated WebWorker V8 Engine (Network Blocked)"
                    }
                )
            }
        } catch (_: TimeoutCancellationException) {
            mainHandler.post {
                webViewRef?.evaluateJavascript("if (window.__activeWorker) { window.__activeWorker.terminate(); }", null)
            }
            ExecutionResult(
                status = ExecutionStatus.ERROR,
                stdout = synchronized(stdoutBuf) { stdoutBuf.toString().trimEnd() },
                stderr = "TimeoutError: JavaScript execution timed out after ${timeoutMs}ms.",
                durationMs = System.currentTimeMillis() - startMs,
                runtimeDescription = "Isolated WebWorker V8 Engine"
            )
        } catch (e: Exception) {
            ExecutionResult(
                status = ExecutionStatus.ERROR,
                stderr = e.localizedMessage ?: "JavaScript execution failed",
                durationMs = System.currentTimeMillis() - startMs,
                runtimeDescription = "Isolated JS Engine"
            )
        } finally {
            mainHandler.post {
                webViewRef?.stopLoading()
                webViewRef?.loadUrl("about:blank")
                webViewRef?.destroy()
            }
        }
    }

    internal fun buildSandboxedJsHarness(
        code: String,
        stdinLines: JSONArray,
        timeoutMs: Long,
        useWorker: Boolean
    ): String {
        val escapedCode = JSONObject.quote(code)
        if (!useWorker) {
            return """
                (async function() {
                  const __stdin = $stdinLines;
                  let __stdinIdx = 0;
                  window.input = function() { return __stdinIdx < __stdin.length ? __stdin[__stdinIdx++] : ""; };
                  window.prompt = window.input;
                  window.fetch = function() { return Promise.reject(new Error("Network access is disabled in PoraStudio Sandbox")); };
                  console.log = function(...args) {
                    CodeVerseBridge.onLog(args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' '));
                  };
                  console.warn = console.log;
                  console.info = console.log;
                  console.error = function(...args) {
                    CodeVerseBridge.onError(args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' '));
                  };
                  try {
                    const AsyncFunction = Object.getPrototypeOf(async function(){}).constructor;
                    const fn = new AsyncFunction($escapedCode);
                    const ret = await fn();
                    if (ret !== undefined) CodeVerseBridge.onLog(String(ret));
                  } catch (err) {
                    CodeVerseBridge.onError(err && err.stack ? err.stack : String(err));
                  } finally {
                    CodeVerseBridge.onDone();
                  }
                })();
            """.trimIndent()
        }

        // True WebWorker isolation: infinite loops cannot block the Android WebView UI thread and can be terminated cleanly
        return """
            (function() {
              const workerSource = `
                const __stdin = $stdinLines;
                let __stdinIdx = 0;
                self.input = function() { return __stdinIdx < __stdin.length ? __stdin[__stdinIdx++] : ""; };
                self.prompt = self.input;
                self.fetch = function() { throw new Error("Network access disabled in PoraStudio JS Sandbox"); };
                self.importScripts = function() { throw new Error("External script loading disabled in Sandbox"); };
                const fmt = (args) => args.map(a => {
                  try { return typeof a === 'object' ? JSON.stringify(a) : String(a); }
                  catch (_) { return String(a); }
                }).join(' ');
                const console = {
                  log: (...a) => self.postMessage({ type: 'log', text: fmt(a) }),
                  info: (...a) => self.postMessage({ type: 'log', text: fmt(a) }),
                  warn: (...a) => self.postMessage({ type: 'log', text: fmt(a) }),
                  error: (...a) => self.postMessage({ type: 'error', text: fmt(a) })
                };
                (async function() {
                  try {
                    const AsyncFunction = Object.getPrototypeOf(async function(){}).constructor;
                    const userFn = new AsyncFunction('console', 'input', 'prompt', ${escapedCode});
                    const res = await userFn(console, self.input, self.prompt);
                    if (res !== undefined) console.log(res);
                  } catch (err) {
                    self.postMessage({ type: 'error', text: err && err.stack ? err.stack : String(err) });
                  } finally {
                    self.postMessage({ type: 'done' });
                  }
                })();
              `;
              try {
                const blob = new Blob([workerSource], { type: 'application/javascript' });
                const workerUrl = URL.createObjectURL(blob);
                const worker = new Worker(workerUrl);
                window.__activeWorker = worker;
                const timer = setTimeout(function() {
                  worker.terminate();
                  CodeVerseBridge.onError("TimeoutError: Worker execution timed out after ${timeoutMs}ms.");
                  CodeVerseBridge.onDone();
                }, ${timeoutMs});
                worker.onmessage = function(ev) {
                  const d = ev.data || {};
                  if (d.type === 'log') CodeVerseBridge.onLog(String(d.text));
                  else if (d.type === 'error') CodeVerseBridge.onError(String(d.text));
                  else if (d.type === 'done') {
                    clearTimeout(timer);
                    URL.revokeObjectURL(workerUrl);
                    worker.terminate();
                    CodeVerseBridge.onDone();
                  }
                };
                worker.onerror = function(err) {
                  clearTimeout(timer);
                  CodeVerseBridge.onError(err && err.message ? err.message : "Worker execution error");
                  worker.terminate();
                  CodeVerseBridge.onDone();
                };
              } catch (e) {
                CodeVerseBridge.onError(String(e));
                CodeVerseBridge.onDone();
              }
            })();
        """.trimIndent()
    }

    suspend fun executeSqlOnDevice(sqlScript: String, startMs: Long): ExecutionResult =
        withContext(Dispatchers.IO) {
            val dbFile = context.getDatabasePath("codeverse_user_sandbox.db")
            var db: SQLiteDatabase? = null
            try {
                db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
                val statements = splitSqlStatements(sqlScript)
                val outLines = mutableListOf<String>()
                var lastColumns: List<String> = emptyList()
                var lastRows: List<List<String>> = emptyList()

                for (stmt in statements) {
                    val trimmed = stmt.trim()
                    if (trimmed.isEmpty()) continue
                    val upper = trimmed.uppercase()
                    if (upper.startsWith("SELECT") || upper.startsWith("PRAGMA") || upper.startsWith("WITH") || upper.startsWith("EXPLAIN")) {
                        db.rawQuery(trimmed, null).use { cursor ->
                            val cols = cursor.columnNames.toList()
                            val rows = mutableListOf<List<String>>()
                            while (cursor.moveToNext() && rows.size < 200) {
                                val row = (0 until cursor.columnCount).map { idx ->
                                    when (cursor.getType(idx)) {
                                        android.database.Cursor.FIELD_TYPE_NULL -> "NULL"
                                        android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(idx).toString()
                                        android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(idx).toString()
                                        android.database.Cursor.FIELD_TYPE_BLOB -> "<BLOB>"
                                        else -> cursor.getString(idx) ?: ""
                                    }
                                }
                                rows.add(row)
                            }
                            lastColumns = cols
                            lastRows = rows
                            outLines.add("Query returned ${rows.size} row(s): ${cols.joinToString(" | ")}")
                        }
                    } else {
                        db.execSQL(trimmed)
                        outLines.add("Executed: ${trimmed.take(72)}")
                    }
                }

                ExecutionResult(
                    status = ExecutionStatus.SUCCESS,
                    stdout = outLines.joinToString("\n").ifBlank { "SQLite script completed with 0 statements." },
                    sqlColumns = lastColumns,
                    sqlRows = lastRows,
                    durationMs = System.currentTimeMillis() - startMs,
                    runtimeDescription = "On-Device Isolated SQLite Engine"
                )
            } catch (e: Exception) {
                ExecutionResult(
                    status = ExecutionStatus.ERROR,
                    stderr = "SQLite Error: ${e.message}",
                    durationMs = System.currentTimeMillis() - startMs,
                    runtimeDescription = "On-Device Isolated SQLite Engine"
                )
            } finally {
                db?.close()
            }
        }

    private fun validateJsonOnDevice(content: String, startMs: Long): ExecutionResult {
        return try {
            val trimmed = content.trim()
            val formatted = if (trimmed.startsWith("[")) {
                JSONArray(trimmed).toString(2)
            } else {
                JSONObject(trimmed).toString(2)
            }
            ExecutionResult(
                status = ExecutionStatus.SUCCESS,
                stdout = "Valid JSON (${formatted.lines().size} lines):\n$formatted",
                durationMs = System.currentTimeMillis() - startMs,
                runtimeDescription = "On-Device JSON Validator"
            )
        } catch (e: Exception) {
            ExecutionResult(
                status = ExecutionStatus.ERROR,
                stderr = "JSON Syntax Error: ${e.message}",
                durationMs = System.currentTimeMillis() - startMs,
                runtimeDescription = "On-Device JSON Validator"
            )
        }
    }

    private fun validateXmlOnDevice(content: String, startMs: Long): ExecutionResult {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(content))
            var tags = 0
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) tags++
                event = parser.next()
            }
            ExecutionResult(
                status = ExecutionStatus.SUCCESS,
                stdout = "Valid XML document ($tags elements parsed).",
                durationMs = System.currentTimeMillis() - startMs,
                runtimeDescription = "On-Device XmlPullParser Validator"
            )
        } catch (e: Exception) {
            ExecutionResult(
                status = ExecutionStatus.ERROR,
                stderr = "XML Parse Error: ${e.message}",
                durationMs = System.currentTimeMillis() - startMs,
                runtimeDescription = "On-Device XmlPullParser Validator"
            )
        }
    }

    private suspend fun executeViaRemoteSandboxOrExplain(
        code: String,
        fileName: String,
        language: CodeLanguage,
        stdinInput: String,
        preferences: UserPreferences,
        startMs: Long
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val sandboxUrl = preferences.sandboxUrl.trim()
        if (sandboxUrl.isEmpty()) {
            return@withContext ExecutionResult(
                status = ExecutionStatus.RUNTIME_UNAVAILABLE,
                stderr = buildMissingRuntimeMessage(language),
                durationMs = System.currentTimeMillis() - startMs,
                runtimeDescription = "${language.displayName} Runtime Unavailable",
                missingRuntimeHint = buildMissingRuntimeMessage(language)
            )
        }

        if (!sandboxUrl.startsWith("https://") && !sandboxUrl.startsWith("http://")) {
            return@withContext ExecutionResult(
                status = ExecutionStatus.ERROR,
                stderr = "Invalid Sandbox URL. Must start with https:// or http://.",
                durationMs = System.currentTimeMillis() - startMs
            )
        }

        try {
            val payload = JSONObject().apply {
                put("language", mapToSandboxLanguage(language))
                put("version", "*")
                put(
                    "files",
                    JSONArray().put(
                        JSONObject().apply {
                            put("name", fileName)
                            put("content", code)
                        }
                    )
                )
                put("stdin", stdinInput)
                put("run_timeout", preferences.sandboxTimeoutMs)
                put("compile_timeout", preferences.sandboxTimeoutMs)
                put("run_memory_limit", preferences.sandboxMemoryMb.toLong() * 1024L * 1024L)
                put("network_disabled", true)
            }

            val req = Request.Builder()
                .url(sandboxUrl)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext ExecutionResult(
                        status = ExecutionStatus.ERROR,
                        stderr = "Sandbox HTTP ${resp.code}: ${rawBody.take(500)}",
                        durationMs = System.currentTimeMillis() - startMs,
                        runtimeDescription = "Remote Sandbox ($sandboxUrl)"
                    )
                }

                val json = JSONObject(rawBody)
                val runObj = json.optJSONObject("run") ?: json
                val compileObj = json.optJSONObject("compile")
                val compileErr = compileObj?.optString("stderr").orEmpty()
                val stdout = runObj.optString("stdout").ifBlank { json.optString("output") }
                val stderr = listOf(compileErr, runObj.optString("stderr"), json.optString("error"))
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                val exitCode = runObj.optInt("code", if (stderr.isBlank()) 0 else 1)

                ExecutionResult(
                    status = if (exitCode == 0 && stderr.isBlank()) ExecutionStatus.SUCCESS else ExecutionStatus.ERROR,
                    stdout = stdout,
                    stderr = stderr,
                    durationMs = System.currentTimeMillis() - startMs,
                    runtimeDescription = "Remote Isolated Sandbox (${language.displayName})"
                )
            }
        } catch (e: Exception) {
            ExecutionResult(
                status = ExecutionStatus.ERROR,
                stderr = "Sandbox connection failed: ${e.localizedMessage ?: e.javaClass.simpleName}",
                durationMs = System.currentTimeMillis() - startMs,
                runtimeDescription = "Remote Sandbox ($sandboxUrl)"
            )
        }
    }

    private fun mapToSandboxLanguage(language: CodeLanguage): String = when (language) {
        CodeLanguage.CPP -> "c++"
        CodeLanguage.CSHARP -> "csharp"
        else -> language.id
    }

    private fun buildMissingRuntimeMessage(language: CodeLanguage): String =
        "${language.displayName} compiler/runtime is not available locally on Android, and no Server Sandbox URL is configured.\n" +
            "HTML, CSS, JavaScript, Python 3, SQL, JSON, and XML run directly on-device.\n" +
            "For ${language.displayName}, configure a Piston/Judge0-compatible isolated sandbox endpoint in Dev Tools > Settings."

    private fun splitSqlStatements(script: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        for (ch in script) {
            if (ch == '\'') inSingle = !inSingle
            if (ch == ';' && !inSingle) {
                result.add(current.toString())
                current.clear()
            } else {
                current.append(ch)
            }
        }
        if (current.isNotBlank()) result.add(current.toString())
        return result
    }
}
