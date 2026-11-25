package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.data.local.LocalFileEntity
import com.example.data.local.LocalProjectEntity
import com.example.data.model.CloudCourseProgress
import com.example.data.model.CloudDailyActivity
import com.example.data.model.CloudProject
import com.example.data.model.CloudProjectFile
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CloudSyncRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun sanitizeDocId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "doc_${System.currentTimeMillis()}" }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank())
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudProjects(userId: String): Flow<List<CloudProject>> = flow {
        require(userId.isNotBlank())
        val path = "users/$userId/projects"
        emitAll(
            db.collection("users").document(userId).collection("projects")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCourseProgress(userId: String): Flow<List<CloudCourseProgress>> = flow {
        require(userId.isNotBlank())
        val path = "users/$userId/learningProgress"
        emitAll(
            db.collection("users").document(userId).collection("learningProgress")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudCourseProgress::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeDailyActivity(userId: String): Flow<List<CloudDailyActivity>> = flow {
        require(userId.isNotBlank())
        val path = "users/$userId/dailyActivity"
        emitAll(
            db.collection("users").document(userId).collection("dailyActivity")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudDailyActivity::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String?,
        preferredLanguage: String
    ): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid"
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val profile = UserProfile(
                    userId = uid,
                    displayName = displayName.ifBlank { "Developer" },
                    email = email,
                    preferredLanguage = preferredLanguage,
                    xp = 20L,
                    streakDays = 1L,
                    totalExecutions = 0L,
                    lastActiveDate = todayKey(),
                    favoriteLanguages = listOf("HTML", "Python"),
                    achievements = listOf("welcome_porastudio")
                )
                docRef.set(profile.toCreateMap()).await()
            } else {
                val updates = buildMap<String, Any> {
                    if (displayName.isNotBlank()) put("displayName", displayName.take(100))
                    put("lastActiveDate", todayKey())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePreferredLanguage(langCode: String): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid"
            val safeLang = if (langCode == "en") "en" else "th"
            db.collection("users").document(uid).update(
                mapOf(
                    "preferredLanguage" to safeLang,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncProjectToCloud(
        project: LocalProjectEntity,
        files: List<LocalFileEntity>
    ): Result<Unit> {
        var docPath = "users/unknown/projects"
        return try {
            val uid = requireUserId()
            val safeProjectId = sanitizeDocId(project.id)
            docPath = "users/$uid/projects/$safeProjectId"
            val projRef = db.collection("users").document(uid)
                .collection("projects").document(safeProjectId)
            val existing = projRef.get().await()

            if (!existing.exists()) {
                val model = CloudProject(
                    userId = uid,
                    name = project.name,
                    description = project.description,
                    templateType = project.templateType,
                    primaryLanguage = project.primaryLanguageId,
                    fileCount = files.size.toLong(),
                    gitBranch = project.gitBranch
                )
                projRef.set(model.toCreateMap(uid)).await()
            } else {
                projRef.update(
                    mapOf(
                        "name" to project.name.take(100).ifBlank { "Untitled" },
                        "description" to project.description.take(500),
                        "templateType" to project.templateType.take(64).ifBlank { "custom" },
                        "primaryLanguage" to project.primaryLanguageId.take(32).ifBlank { "javascript" },
                        "fileCount" to files.size.toLong().coerceIn(0L, 500L),
                        "gitBranch" to project.gitBranch.take(64).ifBlank { "main" },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            for (file in files.take(100)) {
                val safeFileId = sanitizeDocId(file.id)
                val fileRef = projRef.collection("files").document(safeFileId)
                val existingFile = fileRef.get().await()
                if (!existingFile.exists()) {
                    val fileModel = CloudProjectFile(
                        userId = uid,
                        projectId = safeProjectId,
                        path = file.path,
                        name = file.name,
                        language = file.languageId,
                        content = file.content
                    )
                    fileRef.set(fileModel.toCreateMap(uid, safeProjectId)).await()
                } else {
                    fileRef.update(
                        mapOf(
                            "path" to file.path.take(256).ifBlank { file.name },
                            "name" to file.name.take(128).ifBlank { "file.txt" },
                            "language" to file.languageId.take(32).ifBlank { "javascript" },
                            "content" to file.content.take(300_000),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudProject(projectId: String): Result<Unit> {
        var docPath = "users/unknown/projects/$projectId"
        return try {
            val uid = requireUserId()
            val safeProjectId = sanitizeDocId(projectId)
            docPath = "users/$uid/projects/$safeProjectId"
            val projRef = db.collection("users").document(uid)
                .collection("projects").document(safeProjectId)
            val filesSnapshot = projRef.collection("files").get().await()
            for (doc in filesSnapshot.documents) {
                doc.reference.delete().await()
            }
            projRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun fetchCloudProjectsWithFiles(userId: String): Result<List<Pair<CloudProject, List<CloudProjectFile>>>> {
        val path = "users/$userId/projects"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val projectsSnap = db.collection("users").document(uid)
                .collection("projects").get().await()
            val projects = projectsSnap.toObjects(
                CloudProject::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val result = projects.map { project ->
                val filesSnap = db.collection("users").document(uid)
                    .collection("projects").document(project.id)
                    .collection("files").get().await()
                val files = filesSnap.toObjects(
                    CloudProjectFile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                project to files
            }
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun recordDailyActivityAndStats(
        editsDelta: Long = 0L,
        runsDelta: Long = 0L,
        lessonsDelta: Long = 0L,
        xpDelta: Long = 0L,
        languageUsed: String? = null,
        achievementUnlocked: String? = null
    ): Result<Unit> {
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            val dateKey = todayKey()
            path = "users/$uid/dailyActivity/$dateKey"

            val actRef = db.collection("users").document(uid)
                .collection("dailyActivity").document(dateKey)
            val actSnap = actRef.get().await()
            if (!actSnap.exists()) {
                val activity = CloudDailyActivity(
                    userId = uid,
                    dateKey = dateKey,
                    editsCount = editsDelta.coerceAtLeast(0L),
                    runsCount = runsDelta.coerceAtLeast(0L),
                    lessonsCount = lessonsDelta.coerceAtLeast(0L)
                )
                actRef.set(activity.toCreateMap(uid)).await()
            } else {
                actRef.update(
                    mapOf(
                        "editsCount" to FieldValue.increment(editsDelta.coerceAtLeast(0L)),
                        "runsCount" to FieldValue.increment(runsDelta.coerceAtLeast(0L)),
                        "lessonsCount" to FieldValue.increment(lessonsDelta.coerceAtLeast(0L)),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            if (userSnap.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "lastActiveDate" to dateKey,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (xpDelta > 0) updates["xp"] = FieldValue.increment(xpDelta)
                if (runsDelta > 0) updates["totalExecutions"] = FieldValue.increment(runsDelta)
                if (!languageUsed.isNullOrBlank()) {
                    val existingFavs = (userSnap.get("favoriteLanguages") as? List<*>)
                        ?.filterIsInstance<String>()
                        .orEmpty()
                    val merged = (listOf(languageUsed) + existingFavs).distinct().take(8)
                    updates["favoriteLanguages"] = merged
                }
                if (!achievementUnlocked.isNullOrBlank()) {
                    val existingAch = (userSnap.get("achievements") as? List<*>)
                        ?.filterIsInstance<String>()
                        .orEmpty()
                    if (achievementUnlocked !in existingAch) {
                        updates["achievements"] = (existingAch + achievementUnlocked).take(32)
                    }
                }
                userRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("CloudSync", "Stats update warning: ${e.message}")
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveCourseProgress(
        courseId: String,
        completedLessonId: String? = null,
        completedChallengeId: String? = null,
        quizScore: Int? = null,
        finalProjectDone: Boolean? = null
    ): Result<Unit> {
        var path = "users/unknown/learningProgress/$courseId"
        return try {
            val uid = requireUserId()
            val safeCourseId = sanitizeDocId(courseId)
            path = "users/$uid/learningProgress/$safeCourseId"
            val docRef = db.collection("users").document(uid)
                .collection("learningProgress").document(safeCourseId)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val model = CloudCourseProgress(
                    userId = uid,
                    courseId = safeCourseId,
                    completedLessonIds = listOfNotNull(completedLessonId),
                    completedChallengeIds = listOfNotNull(completedChallengeId),
                    bestQuizScore = (quizScore ?: 0).toLong().coerceIn(0L, 100L),
                    finalProjectDone = finalProjectDone ?: false
                )
                docRef.set(model.toCreateMap(uid)).await()
            } else {
                val existingModel = snap.toObject(
                    CloudCourseProgress::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: CloudCourseProgress(userId = uid, courseId = safeCourseId)

                val lessons = if (completedLessonId != null) {
                    (existingModel.completedLessonIds + completedLessonId).distinct().take(50)
                } else existingModel.completedLessonIds

                val challenges = if (completedChallengeId != null) {
                    (existingModel.completedChallengeIds + completedChallengeId).distinct().take(50)
                } else existingModel.completedChallengeIds

                val bestScore = maxOf(
                    existingModel.bestQuizScore,
                    (quizScore ?: 0).toLong()
                ).coerceIn(0L, 100L)

                val finalDone = existingModel.finalProjectDone || (finalProjectDone == true)

                docRef.update(
                    mapOf(
                        "completedLessonIds" to lessons,
                        "completedChallengeIds" to challenges,
                        "bestQuizScore" to bestScore,
                        "finalProjectDone" to finalDone,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
