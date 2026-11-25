package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalStrings
import com.example.data.local.LocalFileEntity
import com.example.data.local.LocalProjectEntity
import com.example.data.model.CodeLanguage
import com.example.data.model.DiagnosticSeverity
import com.example.data.model.ExecutionStatus
import com.example.domain.editor.CodeAnalyzer
import com.example.domain.editor.SyntaxHighlightTransformation
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.CodeVerseViewModel
import kotlinx.coroutines.launch

@Composable
fun IdeScreen(
    viewModel: CodeVerseViewModel,
    onOpenNewProjectDialog: () -> Unit,
    onNavigateToSandboxSettings: () -> Unit,
    onAskAiWithActiveCode: (String) -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val uiScope = rememberCoroutineScope()

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val projects by viewModel.localProjects.collectAsStateWithLifecycle()
    val activeProjectId by viewModel.activeProjectId.collectAsStateWithLifecycle()
    val projectFiles by viewModel.activeProjectFiles.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val history by viewModel.executionHistory.collectAsStateWithLifecycle()

    val isThai = prefs.language == AppLanguage.THAI
    val activeProject = projects.firstOrNull { it.id == activeProjectId }
    val activeTab = editorState.openTabs.firstOrNull { it.fileId == editorState.activeFileId }
    val activeLang = activeTab?.let { CodeLanguage.fromId(it.languageId) } ?: CodeLanguage.JAVASCRIPT

    var showExplorer by remember { mutableStateOf(false) }
    var showProjectDropdown by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showFoldingSheet by remember { mutableStateOf(false) }
    var showDiagnosticsSheet by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var showOutputPane by remember { mutableStateOf(true) }

    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var cursorPosition by remember { mutableIntStateOf(0) }

    val vertScroll = rememberScrollState()
    val horizScroll = rememberScrollState()

    var tfValue by remember(activeTab?.fileId) {
        val initial = activeTab?.content.orEmpty()
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }

    LaunchedEffect(activeTab?.fileId, activeTab?.content) {
        val latest = activeTab?.content ?: return@LaunchedEffect
        if (tfValue.text != latest) {
            val safeSel = tfValue.selection.start.coerceIn(0, latest.length)
            tfValue = tfValue.copy(text = latest, selection = TextRange(safeSel))
            cursorPosition = safeSel
        }
    }

    val lineHeightPx = with(density) { (prefs.editorFontSizeSp + 6).sp.toPx() }
    fun jumpToLine(targetLine: Int) {
        val text = tfValue.text
        val clampedLine = targetLine.coerceAtLeast(1)
        var offset = 0
        var current = 1
        while (current < clampedLine && offset < text.length) {
            if (text[offset] == '\n') current++
            offset++
        }
        tfValue = tfValue.copy(selection = TextRange(offset))
        cursorPosition = offset
        uiScope.launch {
            val targetPx = ((current - 2).coerceAtLeast(0) * lineHeightPx).toInt()
            vertScroll.animateScrollTo(targetPx)
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importFileFromUri(context, uri)
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) viewModel.exportActiveFileToUri(context, uri)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideScreen = maxWidth >= 720.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Top IDE Action Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (!isWideScreen) {
                        IconButton(
                            onClick = { showExplorer = !showExplorer },
                            modifier = Modifier.testTag("ide_toggle_explorer_btn")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = strings.explorer)
                        }
                    }

                    Box {
                        TextButton(
                            onClick = { showProjectDropdown = true },
                            modifier = Modifier.testTag("ide_project_selector")
                        ) {
                            Text(
                                text = activeProject?.name ?: strings.noProjectsYet,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        DropdownMenu(
                            expanded = showProjectDropdown,
                            onDismissRequest = { showProjectDropdown = false }
                        ) {
                            projects.forEach { proj ->
                                DropdownMenuItem(
                                    text = { Text(proj.name) },
                                    onClick = {
                                        viewModel.selectProject(proj.id)
                                        showProjectDropdown = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("+ ${strings.newProject}") },
                                onClick = {
                                    showProjectDropdown = false
                                    onOpenNewProjectDialog()
                                }
                            )
                        }
                    }

                    if (editorState.autoSaveIndicator) {
                        Text(
                            text = "✓",
                            color = EmeraldTertiary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showDiagnosticsSheet = true }) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = strings.diagnostics,
                            tint = if (editorState.diagnostics.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else AmberWarning
                        )
                    }
                    IconButton(onClick = { showFoldingSheet = true }) {
                        Icon(Icons.Default.UnfoldLess, contentDescription = strings.codeFolding)
                    }
                    IconButton(onClick = { showFindReplace = !showFindReplace }) {
                        Icon(Icons.Default.FindReplace, contentDescription = strings.findReplace)
                    }
                    IconButton(onClick = { viewModel.formatActiveTab() }) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = strings.formatCode)
                    }

                    val isRunning = editorState.executionResult.status == ExecutionStatus.RUNNING
                    if (isRunning) {
                        Button(
                            onClick = { viewModel.stopExecution() },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                            modifier = Modifier.testTag("ide_stop_btn")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = strings.stopExecution, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                showOutputPane = true
                                viewModel.runActiveFile()
                            },
                            enabled = activeTab != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldTertiary,
                                contentColor = Color(0xFF04261A)
                            ),
                            modifier = Modifier.testTag("ide_run_btn")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = strings.runCode, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.runCode)
                        }
                    }
                }
            }

            // Find & Replace Bar
            AnimatedVisibility(visible = showFindReplace) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        label = { Text(strings.findLabel) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text(strings.replaceLabel) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { viewModel.replaceInActiveTab(findText, replaceText) }) {
                        Text(strings.replaceAll)
                    }
                }
            }

            // Main Split Area: Explorer + Editor + Output
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isWideScreen || showExplorer) {
                    FileExplorerPane(
                        files = projectFiles,
                        activeFileId = editorState.activeFileId,
                        hasActiveProject = activeProject != null,
                        onSelectFile = {
                            viewModel.openFileInTab(it)
                            if (!isWideScreen) showExplorer = false
                        },
                        onNewFile = { showNewFileDialog = true },
                        onDeleteFile = { viewModel.deleteFileFromProject(it.id) },
                        onImportFile = { importFileLauncher.launch(arrayOf("*/*")) },
                        onExportFile = {
                            if (activeTab != null) exportFileLauncher.launch(activeTab.name)
                        },
                        modifier = Modifier
                            .width(if (isWideScreen) 230.dp else 210.dp)
                            .fillMaxHeight()
                    )
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Open File Tabs
                    if (editorState.openTabs.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(editorState.openTabs, key = { it.fileId }) { tab ->
                                val selected = tab.fileId == editorState.activeFileId
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .clickable { viewModel.switchTab(tab.fileId) }
                                        .border(
                                            width = if (selected) 1.dp else 0.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (tab.isDirty) "• ${tab.name}" else tab.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = strings.closeTab,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { viewModel.closeTab(tab.fileId) },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Code Editor Area
                    if (activeTab == null) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = if (activeProject == null) strings.noProjectsYet else "Select or create a file to start coding",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Button(
                                    onClick = {
                                        if (activeProject == null) onOpenNewProjectDialog() else showNewFileDialog = true
                                    }
                                ) {
                                    Text(if (activeProject == null) strings.newProject else strings.newFile)
                                }
                            }
                        }
                    } else {
                        val content = activeTab.content
                        val lineCount = remember(content) { content.count { it == '\n' } + 1 }
                        val errorLines = remember(editorState.diagnostics) {
                            editorState.diagnostics.associateBy { it.line }
                        }
                        val suggestions = remember(content, cursorPosition, activeLang) {
                            CodeAnalyzer.autocompleteSuggestions(content, cursorPosition, activeLang)
                        }
                        val (currentLine, currentCol) = remember(tfValue.text, tfValue.selection.start) {
                            val pos = tfValue.selection.start.coerceIn(0, tfValue.text.length)
                            val before = tfValue.text.substring(0, pos)
                            val line = before.count { it == '\n' } + 1
                            val col = pos - (before.lastIndexOf('\n') + 1) + 1
                            line to col
                        }
                        val runtimeLabel = remember(activeLang, prefs.sandboxUrl) {
                            when (activeLang) {
                                CodeLanguage.HTML, CodeLanguage.CSS -> "Isolated WebView Preview"
                                CodeLanguage.JAVASCRIPT -> "Isolated WebWorker V8"
                                CodeLanguage.PYTHON -> if (prefs.sandboxUrl.isBlank()) "Sandboxed Python 3 VM" else "Remote Python Sandbox"
                                CodeLanguage.SQL -> "On-Device SQLite"
                                CodeLanguage.JSON, CodeLanguage.XML -> "On-Device Parser"
                                else -> if (prefs.sandboxUrl.isBlank()) "Remote Sandbox Required" else "Remote Sandbox"
                            }
                        }

                        // Compact Editor Status Strip (Runtime + Cursor Position / Go to Line)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${activeLang.displayName} • $runtimeLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "L$currentLine:C$currentCol ($lineCount lines)",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showGoToLineDialog = true }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Synchronized 1:1 Non-Wrapping Editor + Line Gutter
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            val editorMinWidth = (maxWidth - 48.dp).coerceAtLeast(160.dp)

                            Row(modifier = Modifier.fillMaxSize()) {
                                // Line numbers gutter (synced vertically 1:1 with BasicTextField)
                                Column(
                                    modifier = Modifier
                                        .width(48.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .verticalScroll(vertScroll)
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    for (ln in 1..lineCount) {
                                        val issue = errorLines[ln]
                                        val isCurrent = ln == currentLine
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    if (issue != null) showDiagnosticsSheet = true else jumpToLine(ln)
                                                }
                                                .padding(horizontal = 2.dp),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            if (issue != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (issue.severity == DiagnosticSeverity.ERROR) CrimsonError else AmberWarning
                                                        )
                                                )
                                                Spacer(Modifier.width(2.dp))
                                            }
                                            Text(
                                                text = ln.toString(),
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = prefs.editorFontSizeSp.sp,
                                                lineHeight = (prefs.editorFontSizeSp + 6).sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    issue?.severity == DiagnosticSeverity.ERROR -> CrimsonError
                                                    issue != null -> AmberWarning
                                                    isCurrent -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }

                                // Syntax-highlighted non-wrapping horizontal + vertical scroll editor
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .verticalScroll(vertScroll)
                                        .horizontalScroll(horizScroll)
                                        .padding(10.dp)
                                ) {
                                    BasicTextField(
                                        value = tfValue,
                                        onValueChange = { incoming ->
                                            val prev = tfValue.text
                                            val prevCursor = tfValue.selection.start
                                            var nextText = incoming.text
                                            var nextCursor = incoming.selection.start

                                            // 1. Auto-indent on Enter (including 3-line `{|}` expansion)
                                            val indented = CodeAnalyzer.applyAutoIndentOnEnter(
                                                prev,
                                                nextText,
                                                nextCursor,
                                                prefs.tabSize
                                            )
                                            if (indented != null) {
                                                nextText = indented.first
                                                nextCursor = indented.second
                                            } else if (nextText.length == prev.length + 1 && nextCursor > 0) {
                                                val typed = nextText[nextCursor - 1]
                                                val charAlreadyAfterCaret = prev.getOrNull(prevCursor)
                                                if (charAlreadyAfterCaret == typed && typed in listOf(')', '}', ']', '"', '\'')) {
                                                    // Overtype closing delimiter cleanly instead of duplicating
                                                    nextText = prev
                                                    nextCursor = (prevCursor + 1).coerceAtMost(prev.length)
                                                } else {
                                                    // Smart pair auto-completion
                                                    val prevChar = nextText.getOrNull(nextCursor - 2)
                                                    val closing = when (typed) {
                                                        '{' -> '}'
                                                        '(' -> ')'
                                                        '[' -> ']'
                                                        '"' -> '"'
                                                        '\'' -> if (prevChar == null || prevChar in " ([{:=,+-*/\n\t") '\'' else null
                                                        else -> null
                                                    }
                                                    if (closing != null) {
                                                        nextText = nextText.substring(0, nextCursor) + closing + nextText.substring(nextCursor)
                                                    }
                                                }
                                            } else if (nextText.length == prev.length - 1 && nextCursor >= 0) {
                                                // Pair backspace: deleting `(|)` removes matching `)`
                                                val deleted = prev.getOrNull(nextCursor)
                                                val expectedClose = when (deleted) {
                                                    '{' -> '}'
                                                    '(' -> ')'
                                                    '[' -> ']'
                                                    '"' -> '"'
                                                    '\'' -> '\''
                                                    else -> null
                                                }
                                                if (expectedClose != null && nextText.getOrNull(nextCursor) == expectedClose) {
                                                    nextText = nextText.removeRange(nextCursor, nextCursor + 1)
                                                }
                                            }

                                            tfValue = TextFieldValue(nextText, TextRange(nextCursor.coerceIn(0, nextText.length)))
                                            cursorPosition = nextCursor
                                            if (nextText != prev) {
                                                viewModel.updateActiveTabContent(nextText)
                                            }
                                        },
                                        textStyle = TextStyle(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = prefs.editorFontSizeSp.sp,
                                            lineHeight = (prefs.editorFontSizeSp + 6).sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        visualTransformation = remember(activeLang) {
                                            SyntaxHighlightTransformation(activeLang)
                                        },
                                        modifier = Modifier
                                            .widthIn(min = editorMinWidth)
                                            .testTag("ide_code_editor")
                                    )
                                }
                            }
                        }

                        // Clickable live diagnostic banner
                        if (editorState.diagnostics.isNotEmpty()) {
                            val topIssue = editorState.diagnostics.first()
                            val hasError = editorState.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (hasError) CrimsonError.copy(alpha = 0.14f) else AmberWarning.copy(alpha = 0.14f))
                                    .clickable { jumpToLine(topIssue.line) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "L${topIssue.line}: ${if (isThai) topIssue.messageTh else topIssue.messageEn}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasError) CrimsonError else AmberWarning,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (editorState.diagnostics.size > 1) {
                                    Text(
                                        text = "+${editorState.diagnostics.size - 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clickable { showDiagnosticsSheet = true }
                                            .padding(start = 6.dp)
                                    )
                                }
                            }
                        }

                        // Autocomplete suggestion strip
                        if (suggestions.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(suggestions) { word ->
                                    AssistChip(
                                        onClick = {
                                            val pos = tfValue.selection.start.coerceIn(0, tfValue.text.length)
                                            val prefixLen = tfValue.text.substring(0, pos)
                                                .takeLastWhile { it.isLetterOrDigit() || it == '_' || it == '.' || it == '#' }
                                                .length
                                            val replaced = tfValue.text.substring(0, pos - prefixLen) + word + tfValue.text.substring(pos)
                                            val newPos = pos - prefixLen + word.length
                                            tfValue = TextFieldValue(replaced, TextRange(newPos))
                                            cursorPosition = newPos
                                            viewModel.updateActiveTabContent(replaced)
                                        },
                                        label = { Text(word, fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }

                        // Mobile Quick-Insert Accessory Toolbar + Undo/Redo/Copy/Paste + Unindent + Toggle Comment
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { viewModel.undoActiveTab() }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = strings.undo, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.redoActiveTab() }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = strings.redo, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(tfValue.text)) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = strings.copy, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    val pasted = clipboard.getText()?.text ?: return@IconButton
                                    val pos = tfValue.selection.start.coerceIn(0, tfValue.text.length)
                                    val merged = tfValue.text.substring(0, pos) + pasted + tfValue.text.substring(pos)
                                    val nextPos = pos + pasted.length
                                    tfValue = TextFieldValue(merged, TextRange(nextPos))
                                    cursorPosition = nextPos
                                    viewModel.updateActiveTabContent(merged)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = strings.paste, modifier = Modifier.size(18.dp))
                            }

                            val tabSpaces = " ".repeat(prefs.tabSize.coerceIn(2, 8))
                            // TAB & Unindent & Comment Toggle
                            AccessoryKeyChip(label = "TAB") {
                                val pos = tfValue.selection.start.coerceIn(0, tfValue.text.length)
                                val updated = tfValue.text.substring(0, pos) + tabSpaces + tfValue.text.substring(pos)
                                val nextPos = pos + tabSpaces.length
                                tfValue = TextFieldValue(updated, TextRange(nextPos))
                                cursorPosition = nextPos
                                viewModel.updateActiveTabContent(updated)
                            }
                            AccessoryKeyChip(label = "<-TAB") {
                                val text = tfValue.text
                                val pos = tfValue.selection.start.coerceIn(0, text.length)
                                val lineStart = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
                                val removable = text.substring(lineStart).takeWhile { it == ' ' }.take(tabSpaces.length).length
                                if (removable > 0) {
                                    val updated = text.removeRange(lineStart, lineStart + removable)
                                    val nextPos = (pos - removable).coerceAtLeast(lineStart)
                                    tfValue = TextFieldValue(updated, TextRange(nextPos))
                                    cursorPosition = nextPos
                                    viewModel.updateActiveTabContent(updated)
                                }
                            }
                            val commentToken = if (activeLang == CodeLanguage.PYTHON || activeLang == CodeLanguage.BASH) "# " else "// "
                            AccessoryKeyChip(label = commentToken.trim()) {
                                val text = tfValue.text
                                val pos = tfValue.selection.start.coerceIn(0, text.length)
                                val lineStart = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
                                val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
                                val lineText = text.substring(lineStart, lineEnd)
                                val trimmedLeading = lineText.dropWhile { it == ' ' }
                                val leadLen = lineText.length - trimmedLeading.length
                                val updatedLine = if (trimmedLeading.startsWith(commentToken.trim())) {
                                    lineText.substring(0, leadLen) + trimmedLeading.removePrefix(commentToken).removePrefix(commentToken.trim())
                                } else {
                                    lineText.substring(0, leadLen) + commentToken + trimmedLeading
                                }
                                val updated = text.substring(0, lineStart) + updatedLine + text.substring(lineEnd)
                                val delta = updatedLine.length - lineText.length
                                val nextPos = (pos + delta).coerceIn(lineStart, updated.length)
                                tfValue = TextFieldValue(updated, TextRange(nextPos))
                                cursorPosition = nextPos
                                viewModel.updateActiveTabContent(updated)
                            }

                            val symbols = listOf(
                                "{}" to "{}",
                                "()" to "()",
                                "[]" to "[]",
                                "<>" to "<>",
                                ":" to ":",
                                ";" to ";",
                                "=" to "=",
                                "=>" to "=>",
                                "\"\"" to "\"\"",
                                "''" to "''"
                            )
                            symbols.forEach { (insert, label) ->
                                AccessoryKeyChip(label = label) {
                                    val pos = tfValue.selection.start.coerceIn(0, tfValue.text.length)
                                    val updated = tfValue.text.substring(0, pos) + insert + tfValue.text.substring(pos)
                                    val caretOffset = if (insert.length == 2 && insert in listOf("{}", "()", "[]", "<>", "\"\"", "''")) 1 else insert.length
                                    val nextPos = pos + caretOffset
                                    tfValue = TextFieldValue(updated, TextRange(nextPos))
                                    cursorPosition = nextPos
                                    viewModel.updateActiveTabContent(updated)
                                }
                            }
                            TextButton(onClick = { showOutputPane = !showOutputPane }) {
                                Text(if (showOutputPane) "Hide Console" else "Show Console")
                            }
                        }
                    }

                    // Bottom Output / Live Preview / Console Pane
                    AnimatedVisibility(visible = showOutputPane) {
                        IdeExecutionPanel(
                            result = editorState.executionResult,
                            stdinInput = editorState.stdinInput,
                            onStdinChange = { viewModel.setStdinInput(it) },
                            history = history,
                            onConfigureSandbox = onNavigateToSandboxSettings,
                            onAskAiReview = {
                                val err = editorState.executionResult.stderr
                                val code = activeTab?.content.orEmpty()
                                val prompt = if (err.isNotBlank()) {
                                    "Fix this error in ${activeTab?.name}:\nError:\n$err\n\nCode:\n$code"
                                } else {
                                    "Review and improve this ${activeLang.displayName} code:\n$code"
                                }
                                onAskAiWithActiveCode(prompt)
                            },
                            modifier = Modifier.height(235.dp)
                        )
                    }
                }
            }
        }
    }

    if (showNewFileDialog) {
        NewFileDialog(
            isThai = isThai,
            defaultLang = activeLang,
            onDismiss = { showNewFileDialog = false },
            onCreate = { path, lang ->
                viewModel.createNewFile(path, lang)
                showNewFileDialog = false
            }
        )
    }

    if (showGoToLineDialog && activeTab != null) {
        val maxLines = activeTab.content.count { it == '\n' } + 1
        var lineInput by remember { mutableStateOf("1") }
        AlertDialog(
            onDismissRequest = { showGoToLineDialog = false },
            title = { Text(if (isThai) "ไปที่บรรทัด (1 - $maxLines)" else "Go to Line (1 - $maxLines)") },
            text = {
                OutlinedTextField(
                    value = lineInput,
                    onValueChange = { input -> lineInput = input.filter { it.isDigit() } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ln = (lineInput.toIntOrNull() ?: 1).coerceIn(1, maxLines)
                        jumpToLine(ln)
                        showGoToLineDialog = false
                    }
                ) { Text(if (isThai) "ไป" else "Go") }
            },
            dismissButton = {
                TextButton(onClick = { showGoToLineDialog = false }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }

    if (showDiagnosticsSheet) {
        AlertDialog(
            onDismissRequest = { showDiagnosticsSheet = false },
            title = { Text(strings.diagnostics) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editorState.diagnostics.isEmpty()) {
                        Text(strings.noDiagnostics, color = EmeraldTertiary)
                    } else {
                        editorState.diagnostics.forEach { issue ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        jumpToLine(issue.line)
                                        showDiagnosticsSheet = false
                                    }
                            ) {
                                Text(
                                    text = "L${issue.line}: ${if (isThai) issue.messageTh else issue.messageEn}",
                                    color = if (issue.severity == DiagnosticSeverity.ERROR) CrimsonError else AmberWarning,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnosticsSheet = false }) { Text("OK") }
            }
        )
    }

    if (showFoldingSheet) {
        AlertDialog(
            onDismissRequest = { showFoldingSheet = false },
            title = { Text(strings.codeFolding) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editorState.foldableBlocks.isEmpty()) {
                        Text("No nested blocks detected in current file.")
                    } else {
                        editorState.foldableBlocks.forEach { block ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        jumpToLine(block.startLine)
                                        showFoldingSheet = false
                                    }
                            ) {
                                Text(
                                    text = "L${block.startLine}..L${block.endLine}  ${block.label}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFoldingSheet = false }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun AccessoryKeyChip(label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun FileExplorerPane(
    files: List<LocalFileEntity>,
    activeFileId: String?,
    hasActiveProject: Boolean,
    onSelectFile: (LocalFileEntity) -> Unit,
    onNewFile: () -> Unit,
    onDeleteFile: (LocalFileEntity) -> Unit,
    onImportFile: () -> Unit,
    onExportFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.explorer, style = MaterialTheme.typography.labelLarge)
            Row {
                IconButton(onClick = onNewFile, enabled = hasActiveProject, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = strings.newFile, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onImportFile, enabled = hasActiveProject, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.UploadFile, contentDescription = strings.importFile, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onExportFile, enabled = activeFileId != null, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Download, contentDescription = strings.exportFile, modifier = Modifier.size(18.dp))
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(files, key = { it.id }) { file ->
                val selected = file.id == activeFileId
                val lang = CodeLanguage.fromId(file.languageId)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onSelectFile(file) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (file.path.contains('/')) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(lang.badgeHex))
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = file.path,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onDeleteFile(file) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NewFileDialog(
    isThai: Boolean,
    defaultLang: CodeLanguage,
    onDismiss: () -> Unit,
    onCreate: (path: String, language: CodeLanguage) -> Unit
) {
    var path by remember { mutableStateOf("src/script.${defaultLang.primaryExtension}") }
    var selectedLang by remember { mutableStateOf(defaultLang) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isThai) "สร้างไฟล์หรือโฟลเดอร์ใหม่" else "New File / Folder Path") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(if (isThai) "พาธไฟล์ (เช่น src/utils.py)" else "File path (e.g. src/utils.py)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CodeLanguage.entries) { lang ->
                        FilterChip(
                            selected = lang == selectedLang,
                            onClick = {
                                selectedLang = lang
                                val base = path.substringBeforeLast('.')
                                path = "$base.${lang.primaryExtension}"
                            },
                            label = { Text(lang.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(path, selectedLang) }) {
                Text(if (isThai) "สร้าง" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isThai) "ยกเลิก" else "Cancel")
            }
        }
    )
}
