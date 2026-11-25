package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.LocalExecutionHistoryEntity
import com.example.data.local.LocalFileEntity
import com.example.data.local.LocalGitCommitEntity
import com.example.data.local.LocalProjectEntity
import com.example.data.local.ProjectDao
import com.example.data.model.CodeLanguage
import com.example.data.model.ProjectTemplate
import com.example.data.model.TemplateFileSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class WorkspaceRepository(
    private val projectDao: ProjectDao,
    private val cloudSync: CloudSyncRepository
) {
    fun observeProjects(userId: String): Flow<List<LocalProjectEntity>> =
        projectDao.observeProjects(userId)

    fun observeFiles(userId: String, projectId: String): Flow<List<LocalFileEntity>> =
        projectDao.observeFiles(userId, projectId)

    fun observeRecentFiles(userId: String): Flow<List<LocalFileEntity>> =
        projectDao.observeRecentFiles(userId)

    fun observeTotalFileCount(userId: String): Flow<Int> =
        projectDao.observeTotalFileCount(userId)

    fun observeCommits(userId: String, projectId: String): Flow<List<LocalGitCommitEntity>> =
        projectDao.observeCommits(userId, projectId)

    fun observeExecutionHistory(userId: String): Flow<List<LocalExecutionHistoryEntity>> =
        projectDao.observeExecutionHistory(userId)

    suspend fun getFilesOnce(userId: String, projectId: String): List<LocalFileEntity> =
        projectDao.getFilesOnce(userId, projectId)

    suspend fun getLatestCommit(userId: String, projectId: String): LocalGitCommitEntity? =
        projectDao.getLatestCommit(userId, projectId)

    suspend fun createProjectFromTemplate(
        userId: String,
        name: String,
        template: ProjectTemplate
    ): LocalProjectEntity {
        val now = System.currentTimeMillis()
        val projectId = "proj_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val project = LocalProjectEntity(
            id = projectId,
            userId = userId,
            name = name.trim().ifBlank { template.titleEn },
            description = template.descriptionEn,
            templateType = template.id,
            primaryLanguageId = template.primaryLanguage.id,
            gitBranch = "main",
            createdAtMillis = now,
            updatedAtMillis = now,
            syncedWithCloud = false
        )
        projectDao.upsertProject(project)
        val fileEntities = template.files.map { spec ->
            createFileEntity(userId, projectId, spec.path, spec.language, spec.content, now)
        }
        fileEntities.forEach { projectDao.upsertFile(it) }
        cloudSync.syncProjectToCloud(project, fileEntities)
        cloudSync.recordDailyActivityAndStats(editsDelta = 1L, xpDelta = 15L, languageUsed = template.primaryLanguage.displayName)
        return project
    }

    suspend fun createCustomProject(
        userId: String,
        name: String,
        description: String,
        category: String,
        primaryLanguage: CodeLanguage,
        initialFiles: List<TemplateFileSpec>
    ): LocalProjectEntity {
        val now = System.currentTimeMillis()
        val projectId = "proj_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val project = LocalProjectEntity(
            id = projectId,
            userId = userId,
            name = name.trim().ifBlank { "New ${primaryLanguage.displayName} Project" },
            description = description.take(500),
            templateType = category,
            primaryLanguageId = primaryLanguage.id,
            gitBranch = "main",
            createdAtMillis = now,
            updatedAtMillis = now
        )
        projectDao.upsertProject(project)
        val specs = initialFiles.ifEmpty {
            listOf(
                TemplateFileSpec(
                    path = "main.${primaryLanguage.primaryExtension}",
                    language = primaryLanguage,
                    content = primaryLanguage.starterSnippet
                )
            )
        }
        val entities = specs.map { spec ->
            createFileEntity(userId, projectId, spec.path, spec.language, spec.content, now)
        }
        entities.forEach { projectDao.upsertFile(it) }
        cloudSync.syncProjectToCloud(project, entities)
        cloudSync.recordDailyActivityAndStats(editsDelta = 1L, xpDelta = 15L, languageUsed = primaryLanguage.displayName)
        return project
    }

    suspend fun addOrUpdateFile(
        userId: String,
        projectId: String,
        path: String,
        language: CodeLanguage,
        content: String,
        existingFileId: String? = null
    ): LocalFileEntity {
        val now = System.currentTimeMillis()
        val cleanPath = path.trim().trimStart('/').ifBlank { "untitled.${language.primaryExtension}" }
        val fileName = cleanPath.substringAfterLast('/')
        val entity = LocalFileEntity(
            id = existingFileId ?: "file_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            userId = userId,
            projectId = projectId,
            path = cleanPath,
            name = fileName,
            languageId = language.id,
            content = content,
            updatedAtMillis = now
        )
        projectDao.upsertFile(entity)
        projectDao.getProject(userId, projectId)?.let { proj ->
            val updatedProj = proj.copy(updatedAtMillis = now)
            projectDao.upsertProject(updatedProj)
        }
        return entity
    }

    suspend fun syncProjectNow(userId: String, projectId: String): Result<Unit> {
        val project = projectDao.getProject(userId, projectId)
            ?: return Result.failure(IllegalArgumentException("Project not found"))
        val files = projectDao.getFilesOnce(userId, projectId)
        val res = cloudSync.syncProjectToCloud(project, files)
        if (res.isSuccess) {
            projectDao.upsertProject(project.copy(syncedWithCloud = true))
        }
        return res
    }

    suspend fun syncAllProjectsNow(userId: String): Result<Int> {
        val projects = projectDao.getProjectsOnce(userId)
        var syncedCount = 0
        for (proj in projects) {
            val files = projectDao.getFilesOnce(userId, proj.id)
            val res = cloudSync.syncProjectToCloud(proj, files)
            if (res.isSuccess) {
                projectDao.upsertProject(proj.copy(syncedWithCloud = true))
                syncedCount++
            }
        }
        return Result.success(syncedCount)
    }

    suspend fun restoreMissingFromCloud(userId: String): Result<Int> {
        val cloudResult = cloudSync.fetchCloudProjectsWithFiles(userId)
        return cloudResult.map { list ->
            var restored = 0
            for ((proj, files) in list) {
                val localExisting = projectDao.getProject(userId, proj.id)
                if (localExisting == null) {
                    val now = System.currentTimeMillis()
                    val entity = LocalProjectEntity(
                        id = proj.id,
                        userId = userId,
                        name = proj.name,
                        description = proj.description,
                        templateType = proj.templateType,
                        primaryLanguageId = proj.primaryLanguage,
                        gitBranch = proj.gitBranch,
                        createdAtMillis = proj.createdAt?.toDate()?.time ?: now,
                        updatedAtMillis = proj.updatedAt?.toDate()?.time ?: now,
                        syncedWithCloud = true
                    )
                    projectDao.upsertProject(entity)
                    files.forEach { cf ->
                        projectDao.upsertFile(
                            LocalFileEntity(
                                id = cf.id,
                                userId = userId,
                                projectId = proj.id,
                                path = cf.path,
                                name = cf.name,
                                languageId = cf.language,
                                content = cf.content,
                                updatedAtMillis = cf.updatedAt?.toDate()?.time ?: now
                            )
                        )
                    }
                    restored++
                }
            }
            restored
        }
    }

    suspend fun deleteFile(userId: String, fileId: String) {
        projectDao.deleteFile(userId, fileId)
    }

    suspend fun deleteProject(userId: String, projectId: String) {
        projectDao.deleteProjectCascade(userId, projectId)
        cloudSync.deleteCloudProject(projectId)
    }

    suspend fun updateBranch(userId: String, projectId: String, branchName: String) {
        projectDao.getProject(userId, projectId)?.let { proj ->
            val updated = proj.copy(
                gitBranch = branchName.trim().ifBlank { "main" },
                updatedAtMillis = System.currentTimeMillis()
            )
            projectDao.upsertProject(updated)
            cloudSync.syncProjectToCloud(updated, projectDao.getFilesOnce(userId, projectId))
        }
    }

    suspend fun saveGitCommit(
        userId: String,
        projectId: String,
        branch: String,
        message: String,
        files: List<LocalFileEntity>
    ): LocalGitCommitEntity {
        val snapshotObj = JSONObject()
        files.forEach { snapshotObj.put(it.path, it.content) }
        val commit = LocalGitCommitEntity(
            id = UUID.randomUUID().toString().replace("-", "").take(8),
            userId = userId,
            projectId = projectId,
            branch = branch,
            message = message.trim().ifBlank { "Update project files" },
            snapshotJson = snapshotObj.toString(),
            timestampMillis = System.currentTimeMillis()
        )
        projectDao.insertCommit(commit)
        return commit
    }

    suspend fun recordExecution(
        userId: String,
        projectName: String,
        fileName: String,
        language: CodeLanguage,
        status: String,
        outputPreview: String,
        durationMs: Long
    ) {
        projectDao.insertExecutionHistory(
            LocalExecutionHistoryEntity(
                id = "exec_${UUID.randomUUID().toString().replace("-", "").take(10)}",
                userId = userId,
                projectName = projectName,
                fileName = fileName,
                languageId = language.id,
                status = status,
                outputPreview = outputPreview.take(400),
                durationMs = durationMs,
                executedAtMillis = System.currentTimeMillis()
            )
        )
        cloudSync.recordDailyActivityAndStats(
            runsDelta = 1L,
            xpDelta = if (status == "SUCCESS") 10L else 2L,
            languageUsed = language.displayName,
            achievementUnlocked = if (status == "SUCCESS") "first_execution" else null
        )
    }

    suspend fun readTextFromUri(context: Context, uri: Uri): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "imported.txt"
                val content = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@withContext Result.failure(IllegalStateException("Cannot open input stream"))
                Result.success(name to content)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun writeTextToUri(context: Context, uri: Uri, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(content)
                } ?: return@withContext Result.failure(IllegalStateException("Cannot open output stream"))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun buildBackupJson(userId: String): String = withContext(Dispatchers.IO) {
        val projects = projectDao.getProjectsOnce(userId)
        val root = JSONObject()
        val arr = JSONArray()
        for (p in projects) {
            val files = projectDao.getFilesOnce(userId, p.id)
            val pObj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("description", p.description)
                put("templateType", p.templateType)
                put("primaryLanguage", p.primaryLanguageId)
                put("gitBranch", p.gitBranch)
                val fArr = JSONArray()
                files.forEach { f ->
                    fArr.put(
                        JSONObject().apply {
                            put("path", f.path)
                            put("language", f.languageId)
                            put("content", f.content)
                        }
                    )
                }
                put("files", fArr)
            }
            arr.put(pObj)
        }
        root.put("exportedAt", System.currentTimeMillis())
        root.put("projects", arr)
        root.toString(2)
    }

    private fun createFileEntity(
        userId: String,
        projectId: String,
        path: String,
        language: CodeLanguage,
        content: String,
        timestamp: Long
    ): LocalFileEntity {
        val cleanPath = path.trimStart('/')
        return LocalFileEntity(
            id = "file_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            userId = userId,
            projectId = projectId,
            path = cleanPath,
            name = cleanPath.substringAfterLast('/'),
            languageId = language.id,
            content = content,
            updatedAtMillis = timestamp
        )
    }
}
