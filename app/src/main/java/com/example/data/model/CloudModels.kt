package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val preferredLanguage: String = "th",
    val xp: Long = 0L,
    val streakDays: Long = 1L,
    val totalExecutions: Long = 0L,
    val lastActiveDate: String? = null,
    val favoriteLanguages: List<String> = emptyList(),
    val achievements: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("displayName", displayName.take(100).ifBlank { "Developer" })
        email?.take(200)?.takeIf { it.isNotBlank() }?.let { put("email", it) }
        put("preferredLanguage", if (preferredLanguage == "en") "en" else "th")
        put("xp", xp.coerceIn(0L, 10_000_000L))
        put("streakDays", streakDays.coerceIn(0L, 10_000L))
        put("totalExecutions", totalExecutions.coerceIn(0L, 10_000_000L))
        lastActiveDate?.take(32)?.let { put("lastActiveDate", it) }
        put("favoriteLanguages", favoriteLanguages.take(16))
        put("achievements", achievements.take(32))
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class CloudProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val templateType: String = "web_app",
    val primaryLanguage: String = "html",
    val fileCount: Long = 0L,
    val gitBranch: String = "main",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.take(100).ifBlank { "Untitled Project" },
        "description" to description.take(500),
        "templateType" to templateType.take(64).ifBlank { "custom" },
        "primaryLanguage" to primaryLanguage.take(32).ifBlank { "javascript" },
        "fileCount" to fileCount.coerceIn(0L, 500L),
        "gitBranch" to gitBranch.take(64).ifBlank { "main" },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class CloudProjectFile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val path: String = "",
    val name: String = "",
    val language: String = "javascript",
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String, projId: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "projectId" to projId,
        "path" to path.take(256).ifBlank { name.take(128).ifBlank { "main.txt" } },
        "name" to name.take(128).ifBlank { "main.txt" },
        "language" to language.take(32).ifBlank { "javascript" },
        "content" to content.take(300_000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class CloudCourseProgress(
    @DocumentId val id: String = "",
    val userId: String = "",
    val courseId: String = "",
    val completedLessonIds: List<String> = emptyList(),
    val completedChallengeIds: List<String> = emptyList(),
    val bestQuizScore: Long = 0L,
    val finalProjectDone: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "courseId" to courseId.take(64),
        "completedLessonIds" to completedLessonIds.take(50),
        "completedChallengeIds" to completedChallengeIds.take(50),
        "bestQuizScore" to bestQuizScore.coerceIn(0L, 100L),
        "finalProjectDone" to finalProjectDone,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class CloudDailyActivity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val editsCount: Long = 0L,
    val runsCount: Long = 0L,
    val lessonsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "dateKey" to dateKey.take(32),
        "editsCount" to editsCount.coerceIn(0L, 100_000L),
        "runsCount" to runsCount.coerceIn(0L, 100_000L),
        "lessonsCount" to lessonsCount.coerceIn(0L, 1_000L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
