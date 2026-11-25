package com.example.data.model

enum class ExecutionStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    ERROR,
    STOPPED,
    RUNTIME_UNAVAILABLE
}

data class ExecutionResult(
    val status: ExecutionStatus,
    val stdout: String = "",
    val stderr: String = "",
    val htmlPreviewContent: String? = null,
    val sqlColumns: List<String> = emptyList(),
    val sqlRows: List<List<String>> = emptyList(),
    val durationMs: Long = 0L,
    val runtimeDescription: String = "",
    val missingRuntimeHint: String? = null
)

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

data class DiagnosticIssue(
    val line: Int,
    val messageTh: String,
    val messageEn: String,
    val severity: DiagnosticSeverity
)

data class FoldableBlock(
    val startLine: Int,
    val endLine: Int,
    val label: String
)
