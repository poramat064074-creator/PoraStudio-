package com.example.ui.viewmodel

import com.example.data.model.DiagnosticIssue
import com.example.data.model.ExecutionResult
import com.example.data.model.ExecutionStatus
import com.example.data.model.FoldableBlock

data class EditorTabState(
    val fileId: String,
    val path: String,
    val name: String,
    val languageId: String,
    val content: String,
    val savedContent: String,
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList()
) {
    val isDirty: Boolean get() = content != savedContent
}

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false
)

sealed interface AiGenerationState {
    data object Idle : AiGenerationState
    data object Loading : AiGenerationState
    data object Streaming : AiGenerationState
    data class Error(val message: String) : AiGenerationState
}

data class TerminalEntry(
    val command: String,
    val output: String
)

data class EditorRuntimeSnapshot(
    val openTabs: List<EditorTabState> = emptyList(),
    val activeFileId: String? = null,
    val diagnostics: List<DiagnosticIssue> = emptyList(),
    val foldableBlocks: List<FoldableBlock> = emptyList(),
    val executionResult: ExecutionResult = ExecutionResult(status = ExecutionStatus.IDLE),
    val stdinInput: String = "",
    val autoSaveIndicator: Boolean = false
)
