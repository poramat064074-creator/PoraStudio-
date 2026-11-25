package com.example.data.model

enum class GitDiffStatus {
    ADDED,
    MODIFIED,
    DELETED,
    UNCHANGED
}

data class GitFileChange(
    val path: String,
    val status: GitDiffStatus,
    val diffPreview: String
)

data class ApiRequestModel(
    val method: String = "GET",
    val url: String = "https://jsonplaceholder.typicode.com/todos/1",
    val headersText: String = "Accept: application/json\nContent-Type: application/json",
    val bodyText: String = ""
)

data class ApiResponseModel(
    val isSuccess: Boolean = false,
    val statusCode: Int = 0,
    val latencyMs: Long = 0L,
    val headersSummary: String = "",
    val body: String = "",
    val errorMessage: String? = null
)

data class SqliteTableSchema(
    val tableName: String,
    val columns: List<String>,
    val rowCount: Long
)

data class DebuggerTraceEntry(
    val timestampMs: Long,
    val source: String,
    val level: String,
    val message: String
)
