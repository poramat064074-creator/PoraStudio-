package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM local_projects WHERE userId = :userId ORDER BY updatedAtMillis DESC")
    fun observeProjects(userId: String): Flow<List<LocalProjectEntity>>

    @Query("SELECT * FROM local_projects WHERE userId = :userId ORDER BY updatedAtMillis DESC")
    suspend fun getProjectsOnce(userId: String): List<LocalProjectEntity>

    @Query("SELECT * FROM local_projects WHERE id = :projectId AND userId = :userId LIMIT 1")
    suspend fun getProject(userId: String, projectId: String): LocalProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: LocalProjectEntity)

    @Query("DELETE FROM local_projects WHERE id = :projectId AND userId = :userId")
    suspend fun deleteProject(userId: String, projectId: String)

    @Query("DELETE FROM local_files WHERE projectId = :projectId AND userId = :userId")
    suspend fun deleteFilesForProject(userId: String, projectId: String)

    @Query("DELETE FROM local_git_commits WHERE projectId = :projectId AND userId = :userId")
    suspend fun deleteCommitsForProject(userId: String, projectId: String)

    @androidx.room.Transaction
    suspend fun deleteProjectCascade(userId: String, projectId: String) {
        deleteCommitsForProject(userId, projectId)
        deleteFilesForProject(userId, projectId)
        deleteProject(userId, projectId)
    }

    @Query("SELECT * FROM local_files WHERE projectId = :projectId AND userId = :userId ORDER BY path ASC")
    fun observeFiles(userId: String, projectId: String): Flow<List<LocalFileEntity>>

    @Query("SELECT * FROM local_files WHERE projectId = :projectId AND userId = :userId ORDER BY path ASC")
    suspend fun getFilesOnce(userId: String, projectId: String): List<LocalFileEntity>

    @Query("SELECT * FROM local_files WHERE userId = :userId ORDER BY updatedAtMillis DESC LIMIT 12")
    fun observeRecentFiles(userId: String): Flow<List<LocalFileEntity>>

    @Query("SELECT COUNT(*) FROM local_files WHERE userId = :userId")
    fun observeTotalFileCount(userId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: LocalFileEntity)

    @Query("DELETE FROM local_files WHERE id = :fileId AND userId = :userId")
    suspend fun deleteFile(userId: String, fileId: String)

    @Query("SELECT * FROM local_git_commits WHERE projectId = :projectId AND userId = :userId ORDER BY timestampMillis DESC")
    fun observeCommits(userId: String, projectId: String): Flow<List<LocalGitCommitEntity>>

    @Query("SELECT * FROM local_git_commits WHERE projectId = :projectId AND userId = :userId ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLatestCommit(userId: String, projectId: String): LocalGitCommitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommit(commit: LocalGitCommitEntity)

    @Query("SELECT * FROM local_execution_history WHERE userId = :userId ORDER BY executedAtMillis DESC LIMIT 30")
    fun observeExecutionHistory(userId: String): Flow<List<LocalExecutionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecutionHistory(entry: LocalExecutionHistoryEntity)
}

@Database(
    entities = [
        LocalProjectEntity::class,
        LocalFileEntity::class,
        LocalGitCommitEntity::class,
        LocalExecutionHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CodeVerseDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
