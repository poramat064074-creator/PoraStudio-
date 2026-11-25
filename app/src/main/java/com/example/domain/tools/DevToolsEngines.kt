package com.example.domain.tools

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.data.local.LocalFileEntity
import com.example.data.local.LocalGitCommitEntity
import com.example.data.model.ApiRequestModel
import com.example.data.model.ApiResponseModel
import com.example.data.model.CodeLanguage
import com.example.data.model.GitDiffStatus
import com.example.data.model.GitFileChange
import com.example.data.model.SqliteTableSchema
import com.example.data.repository.WorkspaceRepository
import com.example.domain.editor.CodeAnalyzer
import com.example.domain.execution.ExecutionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object LocalGitEngine {
    fun computeChanges(
        currentFiles: List<LocalFileEntity>,
        latestCommit: LocalGitCommitEntity?
    ): List<GitFileChange> {
        val committedMap = mutableMapOf<String, String>()
        if (latestCommit != null) {
            try {
                val json = JSONObject(latestCommit.snapshotJson)
                json.keys().forEach { key -> committedMap[key] = json.optString(key, "") }
            } catch (_: Exception) {
            }
        }

        val changes = mutableListOf<GitFileChange>()
        val currentMap = currentFiles.associateBy { it.path }

        currentFiles.forEach { file ->
            val oldContent = committedMap[file.path]
            when {
                oldContent == null -> changes.add(
                    GitFileChange(
                        path = file.path,
                        status = GitDiffStatus.ADDED,
                        diffPreview = file.content.lines().take(8).joinToString("\n") { "+ $it" }
                    )
                )
                oldContent != file.content -> changes.add(
                    GitFileChange(
                        path = file.path,
                        status = GitDiffStatus.MODIFIED,
                        diffPreview = buildSimpleDiff(oldContent, file.content)
                    )
                )
            }
        }

        committedMap.keys.forEach { oldPath ->
            if (oldPath !in currentMap) {
                changes.add(
                    GitFileChange(
                        path = oldPath,
                        status = GitDiffStatus.DELETED,
                        diffPreview = "- (deleted file)"
                    )
                )
            }
        }
        return changes
    }

    private fun buildSimpleDiff(oldText: String, newText: String): String {
        val oldLines = oldText.lines()
        val newLines = newText.lines()
        val diff = mutableListOf<String>()
        val max = maxOf(oldLines.size, newLines.size)
        for (i in 0 until max) {
            val o = oldLines.getOrNull(i)
            val n = newLines.getOrNull(i)
            if (o != n) {
                if (o != null) diff.add("- ${i + 1}: $o")
                if (n != null) diff.add("+ ${i + 1}: $n")
            }
            if (diff.size >= 14) break
        }
        return diff.joinToString("\n")
    }
}

class GitHubIntegrationClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchPublicRepoContents(ownerRepo: String): Result<List<Pair<String, String>>> =
        withContext(Dispatchers.IO) {
            try {
                val cleaned = ownerRepo.trim().trim('/')
                if (!cleaned.contains('/')) {
                    return@withContext Result.failure(IllegalArgumentException("Use format owner/repo (e.g. octocat/Hello-World)"))
                }
                val req = Request.Builder()
                    .url("https://api.github.com/repos/$cleaned/contents")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("GitHub API ${resp.code}: ${body.take(200)}"))
                    }
                    val arr = JSONArray(body)
                    val importedFiles = mutableListOf<Pair<String, String>>()
                    for (i in 0 until minOf(arr.length(), 8)) {
                        val item = arr.getJSONObject(i)
                        if (item.optString("type") == "file") {
                            val name = item.optString("name")
                            val downloadUrl = item.optString("download_url")
                            if (downloadUrl.startsWith("https://")) {
                                val fileReq = Request.Builder().url(downloadUrl).build()
                                client.newCall(fileReq).execute().use { fResp ->
                                    if (fResp.isSuccessful) {
                                        importedFiles.add(name to fResp.body?.string().orEmpty().take(100_000))
                                    }
                                }
                            }
                        }
                    }
                    Result.success(importedFiles)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

class ApiTesterClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun executeRequest(requestModel: ApiRequestModel): ApiResponseModel =
        withContext(Dispatchers.IO) {
            val url = requestModel.url.trim()
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                return@withContext ApiResponseModel(
                    isSuccess = false,
                    errorMessage = "URL must start with https:// or http://"
                )
            }
            val start = System.currentTimeMillis()
            try {
                val builder = Request.Builder().url(url)
                var contentType = "application/json"
                requestModel.headersText.lines().forEach { line ->
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        val key = line.substring(0, idx).trim()
                        val value = line.substring(idx + 1).trim()
                        if (key.isNotEmpty()) {
                            builder.addHeader(key, value)
                            if (key.equals("Content-Type", ignoreCase = true)) contentType = value
                        }
                    }
                }
                val method = requestModel.method.uppercase()
                val reqBody = if (method in listOf("POST", "PUT", "PATCH")) {
                    requestModel.bodyText.toRequestBody(contentType.toMediaTypeOrNull())
                } else null
                builder.method(method, reqBody)

                client.newCall(builder.build()).execute().use { response ->
                    val latency = System.currentTimeMillis() - start
                    val rawBody = response.body?.string().orEmpty()
                    val prettyBody = try {
                        val trimmed = rawBody.trim()
                        when {
                            trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                            trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                            else -> rawBody
                        }
                    } catch (_: Exception) {
                        rawBody
                    }
                    val headersSummary = response.headers.joinToString("\n") { "${it.first}: ${it.second}" }
                    ApiResponseModel(
                        isSuccess = response.isSuccessful,
                        statusCode = response.code,
                        latencyMs = latency,
                        headersSummary = headersSummary,
                        body = prettyBody
                    )
                }
            } catch (e: Exception) {
                ApiResponseModel(
                    isSuccess = false,
                    latencyMs = System.currentTimeMillis() - start,
                    errorMessage = e.localizedMessage ?: "Network request failed"
                )
            }
        }
}

class DatabaseExplorerEngine(private val context: Context) {
    suspend fun listTables(): List<SqliteTableSchema> = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("codeverse_user_sandbox.db")
        if (!dbFile.exists()) return@withContext emptyList()
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
            val tableNames = mutableListOf<String>()
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' ORDER BY name",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    tableNames.add(cursor.getString(0))
                }
            }
            tableNames.map { table ->
                val cols = mutableListOf<String>()
                val quoted = "\"${table.replace("\"", "\"\"")}\""
                db.rawQuery("PRAGMA table_info($quoted)", null).use { c ->
                    while (c.moveToNext()) {
                        cols.add("${c.getString(1)} (${c.getString(2)})")
                    }
                }
                var count = 0L
                db.rawQuery("SELECT COUNT(*) FROM $quoted", null).use { c ->
                    if (c.moveToFirst()) count = c.getLong(0)
                }
                SqliteTableSchema(table, cols, count)
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            db?.close()
        }
    }
}

class TerminalCommandProcessor(
    private val workspaceRepo: WorkspaceRepository,
    private val executionEngine: ExecutionEngine
) {
    suspend fun executeCommand(
        rawCommand: String,
        userId: String,
        projectId: String?,
        projectName: String,
        branch: String,
        files: List<LocalFileEntity>,
        commits: List<LocalGitCommitEntity>
    ): String {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return ""
        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        val cmd = parts[0].lowercase()
        val arg = parts.getOrNull(1)?.trim().orEmpty()

        return when (cmd) {
            "help" -> """
                Available commands:
                  ls                  - List project files
                  cat <path>          - Print file contents
                  touch <path>        - Create a new file
                  rm <path>           - Delete a file
                  format <path>       - Format a file
                  git status          - Show working tree changes
                  git log             - Show commit history
                  git commit <msg>    - Commit current snapshot
                  sql <query>         - Execute SQLite query
                  help                - Show this help
                  clear               - Clear terminal output
            """.trimIndent()
            "ls" -> if (files.isEmpty()) "(empty project)" else files.joinToString("\n") { "${it.path}  [${it.languageId}] (${it.content.length} chars)" }
            "cat" -> {
                val target = files.firstOrNull { it.path == arg || it.name == arg }
                target?.content ?: "cat: '$arg': No such file in active project"
            }
            "touch" -> {
                if (projectId == null || arg.isBlank()) "Usage: touch <filename>"
                else {
                    val lang = CodeLanguage.fromFileName(arg)
                    workspaceRepo.addOrUpdateFile(userId, projectId, arg, lang, lang.starterSnippet)
                    "Created $arg (${lang.displayName})"
                }
            }
            "rm" -> {
                val target = files.firstOrNull { it.path == arg || it.name == arg }
                if (target == null) "rm: '$arg': No such file"
                else {
                    workspaceRepo.deleteFile(userId, target.id)
                    "Removed ${target.path}"
                }
            }
            "format" -> {
                val target = files.firstOrNull { it.path == arg || it.name == arg }
                if (target == null || projectId == null) "format: '$arg': No such file"
                else {
                    val formatted = CodeAnalyzer.formatCode(target.content, CodeLanguage.fromId(target.languageId))
                    workspaceRepo.addOrUpdateFile(userId, projectId, target.path, CodeLanguage.fromId(target.languageId), formatted, target.id)
                    "Formatted ${target.path}"
                }
            }
            "git" -> handleGitCommand(arg, userId, projectId, branch, files, commits)
            "sql" -> {
                if (arg.isBlank()) "Usage: sql SELECT * FROM ..."
                else {
                    val res = executionEngine.executeSqlOnDevice(arg, System.currentTimeMillis())
                    if (res.stderr.isNotBlank()) res.stderr
                    else buildString {
                        appendLine(res.stdout)
                        if (res.sqlColumns.isNotEmpty()) {
                            appendLine(res.sqlColumns.joinToString(" | "))
                            res.sqlRows.forEach { row -> appendLine(row.joinToString(" | ")) }
                        }
                    }.trimEnd()
                }
            }
            else -> "Command '$cmd' not recognized in workspace shell. Type 'help' for supported commands."
        }
    }

    private suspend fun handleGitCommand(
        arg: String,
        userId: String,
        projectId: String?,
        branch: String,
        files: List<LocalFileEntity>,
        commits: List<LocalGitCommitEntity>
    ): String {
        if (projectId == null) return "No active project selected."
        return when {
            arg == "status" -> {
                val changes = LocalGitEngine.computeChanges(files, commits.firstOrNull())
                if (changes.isEmpty()) "On branch $branch\nnothing to commit, working tree clean"
                else "On branch $branch\n" + changes.joinToString("\n") { "${it.status.name}: ${it.path}" }
            }
            arg == "log" -> {
                if (commits.isEmpty()) "No commits yet on branch $branch."
                else commits.joinToString("\n") { "${it.id} [${it.branch}] ${it.message}" }
            }
            arg.startsWith("commit") -> {
                val msg = arg.removePrefix("commit").trim().removeSurrounding("\"")
                val commit = workspaceRepo.saveGitCommit(userId, projectId, branch, msg.ifBlank { "Snapshot commit" }, files)
                "[${commit.branch} ${commit.id}] ${commit.message} (${files.size} files)"
            }
            else -> "Supported git commands: git status | git log | git commit <message>"
        }
    }
}
