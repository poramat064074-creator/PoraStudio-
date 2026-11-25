package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalStrings
import com.example.data.model.CodeLanguage
import com.example.data.model.GitDiffStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.CodeVerseViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DevToolTab(val tag: String, val icon: ImageVector) {
    TERMINAL("terminal", Icons.Filled.Terminal),
    GIT("git", Icons.Filled.Source),
    API("api", Icons.Filled.Api),
    JSON("json", Icons.Filled.DataObject),
    DATABASE("database", Icons.Filled.Storage),
    DEBUGGER("debugger", Icons.Filled.BugReport),
    MARKDOWN("markdown", Icons.Filled.Description),
    PACKAGES("packages", Icons.Filled.FolderZip),
    SETTINGS("settings", Icons.Filled.Settings)
}

@Composable
fun ToolsScreen(
    viewModel: CodeVerseViewModel,
    onOpenIde: () -> Unit,
    onSignOut: () -> Unit
) {
    val strings = LocalStrings.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val isThai = prefs.language == AppLanguage.THAI
    var selectedTab by remember { mutableStateOf(DevToolTab.TERMINAL) }

    val tabLabels = mapOf(
        DevToolTab.TERMINAL to strings.tabTerminal,
        DevToolTab.GIT to strings.tabGit,
        DevToolTab.API to strings.tabApi,
        DevToolTab.JSON to strings.tabJson,
        DevToolTab.DATABASE to strings.tabDatabase,
        DevToolTab.DEBUGGER to strings.tabDebugger,
        DevToolTab.MARKDOWN to strings.tabMarkdown,
        DevToolTab.PACKAGES to strings.tabPackages,
        DevToolTab.SETTINGS to strings.tabSettings
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tools_screen")
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                Text(
                    text = strings.toolsTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DevToolTab.entries) { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            leadingIcon = {
                                Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(tabLabels[tab] ?: tab.name) },
                            modifier = Modifier.testTag("tool_tab_${tab.tag}")
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                DevToolTab.TERMINAL -> TerminalToolPane(viewModel, isThai)
                DevToolTab.GIT -> GitToolPane(viewModel, isThai)
                DevToolTab.API -> ApiTesterToolPane(viewModel, isThai)
                DevToolTab.JSON -> JsonViewerToolPane(viewModel, isThai)
                DevToolTab.DATABASE -> DatabaseExplorerToolPane(viewModel, isThai)
                DevToolTab.DEBUGGER -> DebuggerToolPane(viewModel, isThai)
                DevToolTab.MARKDOWN -> MarkdownPreviewToolPane(viewModel, isThai)
                DevToolTab.PACKAGES -> PackagesDocsToolPane(viewModel, isThai, onOpenIde)
                DevToolTab.SETTINGS -> SettingsCloudToolPane(viewModel, isThai, onSignOut)
            }
        }
    }
}

@Composable
private fun TerminalToolPane(viewModel: CodeVerseViewModel, isThai: Boolean) {
    val entries by viewModel.terminalEntries.collectAsStateWithLifecycle()
    var cmdInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(entries) { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "$ ${entry.command}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (entry.output.isNotBlank()) {
                            Text(
                                text = entry.output,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = cmdInput,
                onValueChange = { cmdInput = it },
                placeholder = {
                    Text(if (isThai) "คำสั่ง (เช่น help, ls, git status, sql SELECT 1)" else "Command (help, ls, git status, sql ...)")
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_command_input")
            )
            Button(
                onClick = {
                    if (cmdInput.isNotBlank()) {
                        viewModel.runTerminalCommand(cmdInput)
                        cmdInput = ""
                    }
                },
                modifier = Modifier.testTag("terminal_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run command")
            }
        }
    }
}

@Composable
private fun GitToolPane(viewModel: CodeVerseViewModel, isThai: Boolean) {
    val projects by viewModel.localProjects.collectAsStateWithLifecycle()
    val activeProjId by viewModel.activeProjectId.collectAsStateWithLifecycle()
    val changes by viewModel.gitChanges.collectAsStateWithLifecycle()
    val commits by viewModel.activeProjectCommits.collectAsStateWithLifecycle()
    val activeProject = projects.firstOrNull { it.id == activeProjId }

    var branchInput by remember(activeProject?.gitBranch) {
        mutableStateOf(activeProject?.gitBranch ?: "main")
    }
    var commitMessage by remember { mutableStateOf("") }
    var githubRepoInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isThai) "Git ในเครื่อง (${activeProject?.name ?: "-"})" else "Local Git (${activeProject?.name ?: "-"})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = branchInput,
                            onValueChange = { branchInput = it },
                            label = { Text("Branch") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = {
                            if (branchInput.isNotBlank()) viewModel.switchGitBranch(branchInput.trim())
                        }) {
                            Text(if (isThai) "สลับสาขา" else "Switch")
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = commitMessage,
                            onValueChange = { commitMessage = it },
                            label = { Text(if (isThai) "ข้อความ Commit" else "Commit message") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("git_commit_input")
                        )
                        Button(
                            onClick = {
                                viewModel.commitLocalGitChanges(
                                    commitMessage.ifBlank { "Update workspace snapshot" }
                                )
                                commitMessage = ""
                            },
                            enabled = activeProject != null,
                            modifier = Modifier.testTag("git_commit_button")
                        ) {
                            Text("Commit")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = if (isThai) "ไฟล์ที่เปลี่ยนแปลง (${changes.size})" else "Working Tree Changes (${changes.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (changes.isEmpty()) {
            item {
                Text(
                    text = if (isThai) "ไม่มีการแก้ไขค้างอยู่" else "Working tree clean",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(changes) { ch ->
                val color = when (ch.status) {
                    GitDiffStatus.ADDED -> EmeraldTertiary
                    GitDiffStatus.MODIFIED -> AmberWarning
                    GitDiffStatus.DELETED -> CrimsonError
                    GitDiffStatus.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "[${ch.status.name}] ${ch.path}",
                            style = MaterialTheme.typography.labelLarge,
                            color = color,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                        Text(
                            text = ch.diffPreview,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isThai) "นำเข้าจาก GitHub สาธารณะ" else "Import Public GitHub Repo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = githubRepoInput,
                            onValueChange = { githubRepoInput = it },
                            placeholder = { Text("owner/repo") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("github_repo_input")
                        )
                        Button(
                            onClick = {
                                if (githubRepoInput.isNotBlank()) {
                                    viewModel.importFromGitHubRepo(githubRepoInput)
                                }
                            },
                            modifier = Modifier.testTag("github_import_button")
                        ) {
                            Text(if (isThai) "นำเข้า" else "Import")
                        }
                    }
                }
            }
        }

        if (commits.isNotEmpty()) {
            item {
                Text(
                    text = if (isThai) "ประวัติ Commit (${commits.size})" else "Commit History (${commits.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(commits, key = { it.id }) { c ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${c.id} • [${c.branch}] ${c.message}",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiTesterToolPane(viewModel: CodeVerseViewModel, isThai: Boolean) {
    val req by viewModel.apiRequest.collectAsStateWithLifecycle()
    val resp by viewModel.apiResponse.collectAsStateWithLifecycle()
    val loading by viewModel.apiLoading.collectAsStateWithLifecycle()
    val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(methods) { m ->
                    FilterChip(
                        selected = req.method == m,
                        onClick = { viewModel.updateApiRequest(req.copy(method = m)) },
                        label = { Text(m) }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = req.url,
                onValueChange = { viewModel.updateApiRequest(req.copy(url = it)) },
                label = { Text("Endpoint URL") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_url_input")
            )
        }
        item {
            OutlinedTextField(
                value = req.headersText,
                onValueChange = { viewModel.updateApiRequest(req.copy(headersText = it)) },
                label = { Text("Headers (Key: Value)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (req.method in listOf("POST", "PUT", "PATCH")) {
            item {
                OutlinedTextField(
                    value = req.bodyText,
                    onValueChange = { viewModel.updateApiRequest(req.copy(bodyText = it)) },
                    label = { Text("JSON Body") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            Button(
                onClick = { viewModel.sendApiRequest() },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_send_button")
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isThai) "ส่งคำขอ API" else "Send Request")
                }
            }
        }

        val currentResp = resp
        if (currentResp != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (currentResp.errorMessage != null) {
                                "Error: ${currentResp.errorMessage}"
                            } else {
                                "HTTP ${currentResp.statusCode} • ${currentResp.latencyMs} ms"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (currentResp.isSuccess) EmeraldTertiary else CrimsonError,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentResp.body.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerLowest,
                                        MaterialTheme.shapes.small
                                    )
                                    .horizontalScroll(rememberScrollState())
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = currentResp.body,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonViewerToolPane(viewModel: CodeVerseViewModel, isThai: Boolean) {
    var rawJson by remember { mutableStateOf("{\n  \"app\": \"PoraStudio\",\n  \"languages\": 16\n}") }

    val parsedDisplay = remember(rawJson) {
        val trimmed = rawJson.trim()
        try {
            when {
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    val summary = "Object with ${obj.length()} keys: ${obj.keys().asSequence().toList().joinToString(", ")}"
                    Result.success(summary to obj.toString(2))
                }
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    val summary = "Array with ${arr.length()} items"
                    Result.success(summary to arr.toString(2))
                }
                else -> Result.failure(IllegalArgumentException("JSON must start with { or ["))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val tab = viewModel.activeTab()
                    if (tab != null) rawJson = tab.content
                }) {
                    Text(if (isThai) "โหลดจากแท็บปัจจุบัน" else "Load Active Tab")
                }
                Button(onClick = {
                    parsedDisplay.getOrNull()?.second?.let { rawJson = it }
                }) {
                    Text(if (isThai) "จัดรูปแบบ JSON" else "Prettify")
                }
            }
        }
        item {
            OutlinedTextField(
                value = rawJson,
                onValueChange = { rawJson = it },
                label = { Text("JSON Input") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    parsedDisplay.onSuccess { (summary, formatted) ->
                        Text(summary, style = MaterialTheme.typography.labelLarge, color = EmeraldTertiary)
                        Text(
                            text = formatted,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp
                        )
                    }.onFailure { err ->
                        Text(
                            text = err.localizedMessage ?: "Invalid JSON",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CrimsonError
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DatabaseExplorerToolPane(viewModel: CodeVerseViewModel, isThai: Boolean) {
    val tables by viewModel.sqliteTables.collectAsStateWithLifecycle()
    var sqlQuery by remember { mutableStateOf("SELECT name, sql FROM sqlite_master WHERE type='table';") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isThai) "ฐานข้อมูล SQLite บนเครื่อง" else "On-Device SQLite Sandbox",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.refreshSqliteTables() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh tables")
                }
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = sqlQuery,
                    onValueChange = { sqlQuery = it },
                    label = { Text("Quick SQL Query") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { viewModel.runTerminalCommand("sql $sqlQuery") }) {
                    Text(if (isThai) "รัน" else "Run")
                }
            }
        }

        if (tables.isEmpty()) {
            item {
                Text(
                    text = if (isThai) {
                        "ยังไม่มีตาราง รันไฟล์ .sql ในสตูดิโอเพื่อสร้างตารางและดูโครงสร้างที่นี่"
                    } else {
                        "No user tables yet. Run a .sql file in Studio to create tables and inspect them here."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(tables, key = { it.tableName }) { tbl ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${tbl.tableName} (${tbl.rowCount} rows)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        tbl.columns.forEach { col ->
                            Text(
                                text = "• $col",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebuggerToolPane(viewModel: CodeVerseViewModel, isThai: Boolean) {
    val traces by viewModel.debuggerTraces.collectAsStateWithLifecycle()
    val diagnostics = viewModel.editorState.collectAsStateWithLifecycle().value.diagnostics
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = if (isThai) {
                    "Diagnostics ปัจจุบัน (${diagnostics.size}) • บันทึกการรัน (${traces.size})"
                } else {
                    "Active Diagnostics (${diagnostics.size}) • Execution Traces (${traces.size})"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (traces.isEmpty()) {
            item {
                Text(
                    text = if (isThai) "ยังไม่มีบันทึกดีบัก ลองรันโค้ดในสตูดิโอ" else "No execution traces yet. Run code in Studio to inspect traces.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(traces) { t ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "[${formatter.format(Date(t.timestampMs))}] ${t.level} • ${t.source}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                        if (t.message.isNotBlank()) {
                            Text(
                                text = t.message,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownPreviewToolPane(viewModel: CodeVerseViewModel, isThai: Boolean) {
    val files by viewModel.activeProjectFiles.collectAsStateWithLifecycle()
    val mdFile = files.firstOrNull { it.name.endsWith(".md", ignoreCase = true) }
    var markdownText by remember(mdFile?.id) {
        mutableStateOf(
            mdFile?.content
                ?: "# PoraStudio\n\n- Native Android Mobile IDE\n- 16 Languages\n- Real Execution + Gemini AI"
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = markdownText,
                onValueChange = { markdownText = it },
                label = { Text(if (isThai) "ซอร์ส Markdown" else "Markdown Source") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    markdownText.lines().forEach { line ->
                        when {
                            line.startsWith("# ") -> Text(
                                line.removePrefix("# "),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            line.startsWith("## ") -> Text(
                                line.removePrefix("## "),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            line.startsWith("- ") || line.startsWith("* ") -> Text(
                                "• ${line.drop(2)}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            else -> if (line.isNotBlank()) {
                                Text(line, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackagesDocsToolPane(
    viewModel: CodeVerseViewModel,
    isThai: Boolean,
    onOpenIde: () -> Unit
) {
    val files by viewModel.activeProjectFiles.collectAsStateWithLifecycle()
    val manifestFiles = files.filter {
        it.name in listOf("package.json", "requirements.txt", "build.gradle.kts", "pubspec.yaml", "README.md")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = if (isThai) "ไฟล์แพ็กเกจและเอกสารโปรเจกต์" else "Project Manifests & Docs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val presets = listOf(
                    "README.md" to CodeLanguage.HTML,
                    "package.json" to CodeLanguage.JSON,
                    "requirements.txt" to CodeLanguage.PYTHON
                )
                items(presets) { (name, lang) ->
                    OutlinedButton(onClick = {
                        viewModel.createNewFile(name, lang)
                        onOpenIde()
                    }) {
                        Text("+ $name")
                    }
                }
            }
        }
        if (manifestFiles.isEmpty()) {
            item {
                Text(
                    text = if (isThai) "ยังไม่มีไฟล์ manifest หรือ README.md ในโปรเจกต์นี้" else "No package manifest or README.md in the active project yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(manifestFiles, key = { it.id }) { file ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(file.path, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Button(onClick = {
                                viewModel.openFileInTab(file)
                                onOpenIde()
                            }) {
                                Text(if (isThai) "แก้ไข" else "Edit")
                            }
                        }
                        Text(
                            text = file.content.take(300),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCloudToolPane(
    viewModel: CodeVerseViewModel,
    isThai: Boolean,
    onSignOut: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var fontSize by remember(prefs.editorFontSizeSp) { mutableIntStateOf(prefs.editorFontSizeSp) }
    var tabSize by remember(prefs.tabSize) { mutableIntStateOf(prefs.tabSize) }
    var sandboxUrl by remember(prefs.sandboxUrl) { mutableStateOf(prefs.sandboxUrl) }
    var timeoutMs by remember(prefs.sandboxTimeoutMs) { mutableStateOf(prefs.sandboxTimeoutMs.toString()) }
    var memoryMb by remember(prefs.sandboxMemoryMb) { mutableStateOf(prefs.sandboxMemoryMb.toString()) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackupToUri(context, uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = prefs.language == lang,
                                onClick = { viewModel.setLanguage(lang) },
                                label = { Text(lang.displayName) },
                                modifier = Modifier.testTag("settings_lang_${lang.code}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.darkThemeLabel, style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = prefs.darkTheme,
                            onCheckedChange = { viewModel.setDarkTheme(it) },
                            modifier = Modifier.testTag("settings_dark_theme_switch")
                        )
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${strings.editorFontSizeLabel}: ${fontSize}sp", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = { fontSize = it.toInt() },
                        valueRange = 11f..22f,
                        steps = 10
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 4).forEach { spaces ->
                            FilterChip(
                                selected = tabSize == spaces,
                                onClick = { tabSize = spaces },
                                label = { Text("Tab: $spaces spaces") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = sandboxUrl,
                        onValueChange = { sandboxUrl = it },
                        label = { Text(strings.sandboxUrlLabel) },
                        placeholder = { Text("https://emkc.org/api/v2/piston/execute") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_sandbox_url_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = timeoutMs,
                            onValueChange = { timeoutMs = it.filter(Char::isDigit) },
                            label = { Text(strings.timeoutMsLabel) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = memoryMb,
                            onValueChange = { memoryMb = it.filter(Char::isDigit) },
                            label = { Text(strings.memoryMbLabel) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.saveSettings(
                                fontSizeSp = fontSize,
                                tabSize = tabSize,
                                sandboxUrl = sandboxUrl,
                                timeoutMs = timeoutMs.toIntOrNull() ?: 10_000,
                                memoryMb = memoryMb.toIntOrNull() ?: 256
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_save_button")
                    ) {
                        Text(strings.saveSettings)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.syncCloudNow() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_sync_now_button")
                    ) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.syncNow)
                    }

                    OutlinedButton(
                        onClick = { backupLauncher.launch("porastudio_backup.json") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_export_backup_button")
                    ) {
                        Icon(Icons.Filled.Backup, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.exportBackup)
                    }

                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.signOut)
                    }
                }
            }
        }
    }
}
