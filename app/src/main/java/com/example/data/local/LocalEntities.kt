package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "local_projects")
data class LocalProjectEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val description: String,
    val templateType: String,
    val primaryLanguageId: String,
    val gitBranch: String = "main",
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val syncedWithCloud: Boolean = false
)

@Entity(
    tableName = "local_files",
    foreignKeys = [
        ForeignKey(
            entity = LocalProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index("userId")]
)
data class LocalFileEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val projectId: String,
    val path: String,
    val name: String,
    val languageId: String,
    val content: String,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "local_git_commits",
    indices = [Index("projectId"), Index("userId")]
)
data class LocalGitCommitEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val projectId: String,
    val branch: String,
    val message: String,
    val snapshotJson: String,
    val timestampMillis: Long
)

@Entity(
    tableName = "local_execution_history",
    indices = [Index("userId")]
)
data class LocalExecutionHistoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val projectName: String,
    val fileName: String,
    val languageId: String,
    val status: String,
    val outputPreview: String,
    val durationMs: Long,
    val executedAtMillis: Long
)
