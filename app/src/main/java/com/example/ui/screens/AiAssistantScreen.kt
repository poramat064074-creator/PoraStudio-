package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalStrings
import com.example.data.repository.AiExplanationMode
import com.example.data.repository.AiQuickAction
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.AiChatMessage
import com.example.ui.viewmodel.AiGenerationState
import com.example.ui.viewmodel.CodeVerseViewModel

@Composable
fun AiAssistantScreen(
    viewModel: CodeVerseViewModel,
    onNavigateToIde: () -> Unit
) {
    val strings = LocalStrings.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val messages by viewModel.aiMessages.collectAsStateWithLifecycle()
    val genState by viewModel.aiGenState.collectAsStateWithLifecycle()
    val aiMode by viewModel.aiMode.collectAsStateWithLifecycle()
    val pendingPlan by viewModel.pendingAiProjectPlan.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()

    val isThai = prefs.language == AppLanguage.THAI
    val clipboard = LocalClipboardManager.current

    var promptInput by remember { mutableStateOf("") }
    var showProjectGenDialog by remember { mutableStateOf(false) }
    var projectIdeaInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_assistant_screen")
    ) {
        // Header card with artwork & controls
        Card(
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_ai_copilot),
                            contentDescription = strings.aiTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Column {
                            Text(
                                text = strings.aiTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.aiSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.clearAiChat() },
                        modifier = Modifier.testTag("ai_clear_button")
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = strings.aiClear)
                    }
                }

                // Explanation depth modes
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AiExplanationMode.entries) { mode ->
                        FilterChip(
                            selected = aiMode == mode,
                            onClick = { viewModel.setAiMode(mode) },
                            label = {
                                Text(if (isThai) mode.labelTh else mode.labelEn)
                            },
                            modifier = Modifier.testTag("ai_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                // Explicit opt-in project context toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = strings.shareProjectContext,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = prefs.shareContextWithAi,
                        onCheckedChange = { viewModel.setShareContextWithAi(it) },
                        modifier = Modifier.testTag("ai_context_switch")
                    )
                }

                // 11 Quick Action chips + Generate Project Structure
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AssistChip(
                            onClick = { showProjectGenDialog = true },
                            leadingIcon = {
                                Icon(Icons.Filled.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text(if (isThai) "สร้างโครงสร้างโปรเจกต์" else "Generate Project")
                            },
                            modifier = Modifier.testTag("ai_generate_project_chip")
                        )
                    }
                    items(AiQuickAction.entries) { action ->
                        AssistChip(
                            onClick = {
                                val activeTab = viewModel.activeTab()
                                val snippet = if (activeTab != null && prefs.shareContextWithAi) {
                                    "\n\n```${activeTab.languageId}\n${activeTab.content.take(2500)}\n```"
                                } else ""
                                val typed = promptInput.trim()
                                if (snippet.isNotEmpty() || typed.isNotEmpty()) {
                                    val extra = if (typed.isNotEmpty()) "\n\n$typed" else ""
                                    viewModel.sendAiPrompt("${action.promptTemplate}$extra$snippet")
                                    if (typed.isNotEmpty()) promptInput = ""
                                } else {
                                    promptInput = "${action.promptTemplate} "
                                }
                            },
                            label = { Text(if (isThai) action.labelTh else action.labelEn) },
                            modifier = Modifier.testTag("ai_action_${action.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Pending generated multi-file project plan card
        if (pendingPlan != null) {
            val plan = pendingPlan!!
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${plan.projectName} (${plan.primaryLanguage.displayName})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = plan.files.joinToString(", ") { it.path },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Button(
                        onClick = { viewModel.materializePendingAiProject(onCreated = onNavigateToIde) },
                        modifier = Modifier.testTag("materialize_ai_project_button")
                    ) {
                        Text(strings.createProjectFromAi)
                    }
                }
            }
        }

        // Error state banner if any
        val errorState = genState as? AiGenerationState.Error
        if (errorState != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = CrimsonError)
                    Text(
                        text = errorState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Chat Feed
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("ai_message_list"),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = if (isThai) {
                                    "พร้อมช่วยเขียน อธิบาย แก้บั๊ก และตรวจความปลอดภัยโค้ด"
                                } else {
                                    "Ready to explain, generate, debug, refactor & audit your code"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isThai) {
                                    "เลือกปุ่มลัดด้านบน หรือเปิดสวิตช์แชร์บริบทโปรเจกต์เพื่อให้ AI เข้าใจไฟล์ที่คุณกำลังเปิดอยู่"
                                } else {
                                    "Pick a quick action above, or opt in to share your active project context so Gemini understands your open files."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                AiMessageBubble(
                    message = msg,
                    hasActiveEditor = editorState.activeFileId != null,
                    applyLabel = strings.applyToEditor,
                    copyLabel = strings.copy,
                    onCopyCode = { code -> clipboard.setText(AnnotatedString(code)) },
                    onApplyToEditor = { code ->
                        viewModel.applyAiCodeToActiveEditor(code)
                        onNavigateToIde()
                    }
                )
            }
        }

        // Bottom prompt composer
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = { Text(strings.aiInputHint) },
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_prompt_input")
                )
                if (genState is AiGenerationState.Loading || genState is AiGenerationState.Streaming) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                } else {
                    Button(
                        onClick = {
                            val text = promptInput.trim()
                            if (text.isNotEmpty()) {
                                viewModel.sendAiPrompt(text)
                                promptInput = ""
                            }
                        },
                        modifier = Modifier.testTag("ai_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.aiSend)
                    }
                }
            }
        }
    }

    if (showProjectGenDialog) {
        AlertDialog(
            onDismissRequest = { showProjectGenDialog = false },
            title = {
                Text(if (isThai) "สร้างโครงสร้างโปรเจกต์ด้วย AI" else "Generate Project with AI")
            },
            text = {
                OutlinedTextField(
                    value = projectIdeaInput,
                    onValueChange = { projectIdeaInput = it },
                    label = {
                        Text(if (isThai) "อธิบายโปรเจกต์ที่ต้องการ" else "Describe the project to build")
                    },
                    placeholder = {
                        Text(if (isThai) "เช่น เว็บพอร์ตโฟลิโอพร้อมฟอร์มติดต่อ หรือ REST API ด้วย Python" else "e.g. Task manager web app or Python Flask API")
                    },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_project_idea_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idea = projectIdeaInput.trim()
                        if (idea.isNotEmpty()) {
                            viewModel.requestAiProjectStructure(idea)
                            projectIdeaInput = ""
                            showProjectGenDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_ai_project_gen")
                ) {
                    Text(if (isThai) "สร้างโครงสร้าง" else "Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProjectGenDialog = false }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }
}

private sealed interface MessageSegment {
    data class Plain(val text: String) : MessageSegment
    data class Code(val lang: String, val code: String) : MessageSegment
}

private fun parseMessageSegments(markdown: String): List<MessageSegment> {
    val regex = Regex("```([a-zA-Z0-9_+-]*)\\n([\\s\\S]*?)```")
    val segments = mutableListOf<MessageSegment>()
    var cursor = 0
    for (match in regex.findAll(markdown)) {
        if (match.range.first > cursor) {
            val plain = markdown.substring(cursor, match.range.first).trim()
            if (plain.isNotEmpty()) segments.add(MessageSegment.Plain(plain))
        }
        val lang = match.groupValues[1]
        val code = match.groupValues[2].trimEnd()
        segments.add(MessageSegment.Code(lang, code))
        cursor = match.range.last + 1
    }
    if (cursor < markdown.length) {
        val tail = markdown.substring(cursor).trim()
        if (tail.isNotEmpty()) segments.add(MessageSegment.Plain(tail))
    }
    return if (segments.isEmpty()) listOf(MessageSegment.Plain(markdown)) else segments
}

@Composable
private fun AiMessageBubble(
    message: AiChatMessage,
    hasActiveEditor: Boolean,
    applyLabel: String,
    copyLabel: String,
    onCopyCode: (String) -> Unit,
    onApplyToEditor: (String) -> Unit
) {
    val containerColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val textColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val segments = remember(message.text) { parseMessageSegments(message.text) }
            segments.forEach { seg ->
                when (seg) {
                    is MessageSegment.Plain -> {
                        Text(
                            text = seg.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }

                    is MessageSegment.Code -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (seg.lang.isNotBlank()) {
                                Text(
                                    text = seg.lang.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = seg.code,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onCopyCode(seg.code) }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(copyLabel)
                                }
                                if (hasActiveEditor) {
                                    Button(onClick = { onApplyToEditor(seg.code) }) {
                                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(applyLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (message.isStreaming) {
                Text(
                    text = "...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
