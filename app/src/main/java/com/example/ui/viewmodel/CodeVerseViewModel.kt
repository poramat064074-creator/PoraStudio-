package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.core.i18n.AppLanguage
import com.example.data.academy.AcademyCatalog
import com.example.data.local.LocalExecutionHistoryEntity
import com.example.data.local.LocalFileEntity
import com.example.data.local.LocalGitCommitEntity
import com.example.data.local.LocalProjectEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.model.ApiRequestModel
import com.example.data.model.ApiResponseModel
import com.example.data.model.CloudCourseProgress
import com.example.data.model.CloudDailyActivity
import com.example.data.model.CloudProject
import com.example.data.model.CodeLanguage
import com.example.data.model.CodingChallenge
import com.example.data.model.Course
import com.example.data.model.DebuggerTraceEntry
import com.example.data.model.ExecutionResult
import com.example.data.model.ExecutionStatus
import com.example.data.model.GitFileChange
import com.example.data.model.ProjectTemplate
import com.example.data.model.ProjectTemplateCatalog
import com.example.data.model.SqliteTableSchema
import com.example.data.model.TemplateFileSpec
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.AiAssistantRepository
import com.example.data.repository.AiExplanationMode
import com.example.data.repository.CloudSyncRepository
import com.example.data.repository.GeneratedProjectPlan
import com.example.data.repository.WorkspaceRepository
import com.example.domain.editor.CodeAnalyzer
import com.example.domain.execution.ExecutionEngine
import com.example.domain.tools.ApiTesterClient
import com.example.domain.tools.DatabaseExplorerEngine
import com.example.domain.tools.GitHubIntegrationClient
import com.example.domain.tools.LocalGitEngine
import com.example.domain.tools.TerminalCommandProcessor
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CodeVerseViewModel(
    private val currentUserId: String,
    private val userDisplayName: String,
    private val userEmail: String?,
    private val preferencesRepo: PreferencesRepository,
    private val workspaceRepo: WorkspaceRepository,
    private val cloudSync: CloudSyncRepository,
    private val aiRepo: AiAssistantRepository,
    private val executionEngine: ExecutionEngine,
    private val terminalProcessor: TerminalCommandProcessor,
    private val apiTesterClient: ApiTesterClient,
    private val gitHubClient: GitHubIntegrationClient,
    private val dbExplorer: DatabaseExplorerEngine
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesRepo.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferences())

    val userProfileState: StateFlow<UiState<UserProfile?>> = cloudSync.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreErr = unwrapFirestoreError(error) ?: throw error
            emit(UiState.Error(firestoreErr.message ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val cloudProjectsState: StateFlow<UiState<List<CloudProject>>> = cloudSync.observeCloudProjects(currentUserId)
        .map<List<CloudProject>, UiState<List<CloudProject>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreErr = unwrapFirestoreError(error) ?: throw error
            emit(UiState.Error(firestoreErr.message ?: "Failed to observe cloud projects"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val courseProgressState: StateFlow<UiState<List<CloudCourseProgress>>> =
        cloudSync.observeCourseProgress(currentUserId)
            .map<List<CloudCourseProgress>, UiState<List<CloudCourseProgress>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreErr = unwrapFirestoreError(error) ?: throw error
                emit(UiState.Error(firestoreErr.message ?: "Failed to load progress"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val dailyActivityState: StateFlow<UiState<List<CloudDailyActivity>>> =
        cloudSync.observeDailyActivity(currentUserId)
            .map<List<CloudDailyActivity>, UiState<List<CloudDailyActivity>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreErr = unwrapFirestoreError(error) ?: throw error
                emit(UiState.Error(firestoreErr.message ?: "Failed to load activity"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val localProjects: StateFlow<List<LocalProjectEntity>> = workspaceRepo.observeProjects(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val recentFiles: StateFlow<List<LocalFileEntity>> = workspaceRepo.observeRecentFiles(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val totalFileCount: StateFlow<Int> = workspaceRepo.observeTotalFileCount(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val executionHistory: StateFlow<List<LocalExecutionHistoryEntity>> =
        workspaceRepo.observeExecutionHistory(currentUserId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _activeProjectId = MutableStateFlow<String?>(null)
    val activeProjectId: StateFlow<String?> = _activeProjectId.asStateFlow()

    val activeProjectFiles: StateFlow<List<LocalFileEntity>> = _activeProjectId
        .flatMapLatest { pid ->
            if (pid == null) flowOf(emptyList()) else workspaceRepo.observeFiles(currentUserId, pid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeProjectCommits: StateFlow<List<LocalGitCommitEntity>> = _activeProjectId
        .flatMapLatest { pid ->
            if (pid == null) flowOf(emptyList()) else workspaceRepo.observeCommits(currentUserId, pid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _editorState = MutableStateFlow(EditorRuntimeSnapshot())
    val editorState: StateFlow<EditorRuntimeSnapshot> = _editorState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    // AI Copilot State
    private val _aiMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val aiMessages: StateFlow<List<AiChatMessage>> = _aiMessages.asStateFlow()

    private val _aiGenState = MutableStateFlow<AiGenerationState>(AiGenerationState.Idle)
    val aiGenState: StateFlow<AiGenerationState> = _aiGenState.asStateFlow()

    private val _aiMode = MutableStateFlow(AiExplanationMode.BEGINNER)
    val aiMode: StateFlow<AiExplanationMode> = _aiMode.asStateFlow()

    private val _pendingAiProjectPlan = MutableStateFlow<GeneratedProjectPlan?>(null)
    val pendingAiProjectPlan: StateFlow<GeneratedProjectPlan?> = _pendingAiProjectPlan.asStateFlow()

    // Dev Tools State
    private val _terminalEntries = MutableStateFlow(
        listOf(
            TerminalEntry(
                command = "help",
                output = "PoraStudio Shell ready. Type 'help' for workspace, git, and sql commands."
            )
        )
    )
    val terminalEntries: StateFlow<List<TerminalEntry>> = _terminalEntries.asStateFlow()

    private val _gitChanges = MutableStateFlow<List<GitFileChange>>(emptyList())
    val gitChanges: StateFlow<List<GitFileChange>> = _gitChanges.asStateFlow()

    private val _apiRequest = MutableStateFlow(ApiRequestModel())
    val apiRequest: StateFlow<ApiRequestModel> = _apiRequest.asStateFlow()

    private val _apiResponse = MutableStateFlow<ApiResponseModel?>(null)
    val apiResponse: StateFlow<ApiResponseModel?> = _apiResponse.asStateFlow()

    private val _apiLoading = MutableStateFlow(false)
    val apiLoading: StateFlow<Boolean> = _apiLoading.asStateFlow()

    private val _sqliteTables = MutableStateFlow<List<SqliteTableSchema>>(emptyList())
    val sqliteTables: StateFlow<List<SqliteTableSchema>> = _sqliteTables.asStateFlow()

    private val _debuggerTraces = MutableStateFlow<List<DebuggerTraceEntry>>(emptyList())
    val debuggerTraces: StateFlow<List<DebuggerTraceEntry>> = _debuggerTraces.asStateFlow()

    private var selectProjectJob: Job? = null
    private var autoSaveJob: Job? = null
    private var executionJob: Job? = null
    private var aiJob: Job? = null

    init {
        viewModelScope.launch {
            cloudSync.ensureUserProfile(
                displayName = userDisplayName,
                email = userEmail,
                preferredLanguage = preferences.value.language.code
            )
            workspaceRepo.restoreMissingFromCloud(currentUserId)
            refreshSqliteTables()
        }

        // Auto-select first project when projects appear
        viewModelScope.launch {
            localProjects.collect { projects ->
                if (_activeProjectId.value == null && projects.isNotEmpty()) {
                    selectProject(projects.first().id)
                }
            }
        }

        // Keep Git diff status reactive
        viewModelScope.launch {
            activeProjectFiles.collect { files ->
                val pid = _activeProjectId.value
                val latest = if (pid != null) workspaceRepo.getLatestCommit(currentUserId, pid) else null
                _gitChanges.value = LocalGitEngine.computeChanges(files, latest)
            }
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        val firestoreErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreErr == null && error is CancellationException) return null
        return firestoreErr ?: (error as? FirebaseFirestoreException)
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferencesRepo.setLanguage(language)
            cloudSync.updatePreferredLanguage(language.code)
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setDarkTheme(enabled) }
    }

    fun setShareContextWithAi(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setShareContextWithAi(enabled) }
    }

    fun saveSettings(
        fontSizeSp: Int,
        tabSize: Int,
        sandboxUrl: String,
        timeoutMs: Int,
        memoryMb: Int
    ) {
        viewModelScope.launch {
            preferencesRepo.saveEditorAndSandboxSettings(fontSizeSp, tabSize, sandboxUrl, timeoutMs, memoryMb)
            _statusBanner.value = if (preferences.value.language == AppLanguage.THAI) {
                "บันทึกการตั้งค่าเรียบร้อย"
            } else {
                "Settings saved"
            }
        }
    }

    fun selectProject(projectId: String, targetFileId: String? = null) {
        val projectChanged = _activeProjectId.value != projectId
        _activeProjectId.value = projectId
        selectProjectJob?.cancel()
        selectProjectJob = viewModelScope.launch {
            val files = workspaceRepo.getFilesOnce(currentUserId, projectId)
            if (_activeProjectId.value != projectId) return@launch
            if (files.isNotEmpty()) {
                val chosen = targetFileId?.let { id -> files.firstOrNull { it.id == id } } ?: files.first()
                if (projectChanged) {
                    _editorState.update {
                        it.copy(openTabs = emptyList(), activeFileId = null, diagnostics = emptyList(), foldableBlocks = emptyList())
                    }
                }
                openFileInTab(chosen)
            } else {
                _editorState.update {
                    it.copy(openTabs = emptyList(), activeFileId = null, diagnostics = emptyList(), foldableBlocks = emptyList())
                }
            }
        }
    }

    fun openRecentFile(file: LocalFileEntity) {
        if (_activeProjectId.value == file.projectId) {
            openFileInTab(file)
        } else {
            selectProject(file.projectId, targetFileId = file.id)
        }
    }

    fun createProjectFromTemplate(name: String, template: ProjectTemplate, onCreated: () -> Unit = {}) {
        viewModelScope.launch {
            val proj = workspaceRepo.createProjectFromTemplate(currentUserId, name, template)
            selectProject(proj.id)
            onCreated()
        }
    }

    fun createCustomProject(
        name: String,
        description: String,
        category: String,
        language: CodeLanguage,
        files: List<TemplateFileSpec> = emptyList(),
        onCreated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val proj = workspaceRepo.createCustomProject(
                userId = currentUserId,
                name = name,
                description = description,
                category = category,
                primaryLanguage = language,
                initialFiles = files
            )
            selectProject(proj.id)
            onCreated()
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            workspaceRepo.deleteProject(currentUserId, projectId)
            if (_activeProjectId.value == projectId) {
                _activeProjectId.value = null
                _editorState.update { it.copy(openTabs = emptyList(), activeFileId = null) }
            }
        }
    }

    fun openFileInTab(file: LocalFileEntity) {
        _editorState.update { state ->
            val existing = state.openTabs.firstOrNull { it.fileId == file.id }
            val tabs = if (existing != null) state.openTabs else state.openTabs + EditorTabState(
                fileId = file.id,
                path = file.path,
                name = file.name,
                languageId = file.languageId,
                content = file.content,
                savedContent = file.content
            )
            val lang = CodeLanguage.fromId(file.languageId)
            state.copy(
                openTabs = tabs,
                activeFileId = file.id,
                diagnostics = CodeAnalyzer.analyze(existing?.content ?: file.content, lang),
                foldableBlocks = CodeAnalyzer.detectFoldableBlocks(existing?.content ?: file.content, lang)
            )
        }
    }

    fun switchTab(fileId: String) {
        _editorState.update { state ->
            val tab = state.openTabs.firstOrNull { it.fileId == fileId } ?: return@update state
            val lang = CodeLanguage.fromId(tab.languageId)
            state.copy(
                activeFileId = fileId,
                diagnostics = CodeAnalyzer.analyze(tab.content, lang),
                foldableBlocks = CodeAnalyzer.detectFoldableBlocks(tab.content, lang)
            )
        }
    }

    fun closeTab(fileId: String) {
        _editorState.update { state ->
            val remaining = state.openTabs.filterNot { it.fileId == fileId }
            val newActive = if (state.activeFileId == fileId) remaining.lastOrNull()?.fileId else state.activeFileId
            val activeTab = remaining.firstOrNull { it.fileId == newActive }
            val lang = activeTab?.let { CodeLanguage.fromId(it.languageId) }
            state.copy(
                openTabs = remaining,
                activeFileId = newActive,
                diagnostics = if (activeTab != null && lang != null) CodeAnalyzer.analyze(activeTab.content, lang) else emptyList(),
                foldableBlocks = if (activeTab != null && lang != null) CodeAnalyzer.detectFoldableBlocks(activeTab.content, lang) else emptyList()
            )
        }
    }

    fun updateActiveTabContent(newContent: String, pushUndo: Boolean = true) {
        val activeId = _editorState.value.activeFileId ?: return
        val projId = _activeProjectId.value ?: return
        var activeTabSnapshot: EditorTabState? = null

        _editorState.update { state ->
            val updatedTabs = state.openTabs.map { tab ->
                if (tab.fileId == activeId) {
                    val undo = if (pushUndo && tab.content != newContent) {
                        (tab.undoStack + tab.content).takeLast(40)
                    } else tab.undoStack
                    val updated = tab.copy(
                        content = newContent,
                        undoStack = undo,
                        redoStack = if (pushUndo) emptyList() else tab.redoStack
                    )
                    activeTabSnapshot = updated
                    updated
                } else tab
            }
            val lang = activeTabSnapshot?.let { CodeLanguage.fromId(it.languageId) }
            state.copy(
                openTabs = updatedTabs,
                autoSaveIndicator = false,
                diagnostics = if (activeTabSnapshot != null && lang != null) {
                    CodeAnalyzer.analyze(newContent, lang)
                } else state.diagnostics,
                foldableBlocks = if (activeTabSnapshot != null && lang != null) {
                    CodeAnalyzer.detectFoldableBlocks(newContent, lang)
                } else state.foldableBlocks
            )
        }

        val tabToSave = activeTabSnapshot ?: return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(650L)
            workspaceRepo.addOrUpdateFile(
                userId = currentUserId,
                projectId = projId,
                path = tabToSave.path,
                language = CodeLanguage.fromId(tabToSave.languageId),
                content = tabToSave.content,
                existingFileId = tabToSave.fileId
            )
            _editorState.update { state ->
                state.copy(
                    openTabs = state.openTabs.map { t ->
                        if (t.fileId == tabToSave.fileId) t.copy(savedContent = tabToSave.content) else t
                    },
                    autoSaveIndicator = true
                )
            }
            workspaceRepo.syncProjectNow(currentUserId, projId)
            cloudSync.recordDailyActivityAndStats(editsDelta = 1L)
        }
    }

    fun undoActiveTab() {
        val activeId = _editorState.value.activeFileId ?: return
        val tab = _editorState.value.openTabs.firstOrNull { it.fileId == activeId } ?: return
        val previous = tab.undoStack.lastOrNull() ?: return
        _editorState.update { state ->
            state.copy(
                openTabs = state.openTabs.map { t ->
                    if (t.fileId == activeId) {
                        t.copy(
                            content = previous,
                            undoStack = t.undoStack.dropLast(1),
                            redoStack = (t.redoStack + t.content).takeLast(40)
                        )
                    } else t
                }
            )
        }
        updateActiveTabContent(previous, pushUndo = false)
    }

    fun redoActiveTab() {
        val activeId = _editorState.value.activeFileId ?: return
        val tab = _editorState.value.openTabs.firstOrNull { it.fileId == activeId } ?: return
        val next = tab.redoStack.lastOrNull() ?: return
        _editorState.update { state ->
            state.copy(
                openTabs = state.openTabs.map { t ->
                    if (t.fileId == activeId) {
                        t.copy(
                            content = next,
                            undoStack = (t.undoStack + t.content).takeLast(40),
                            redoStack = t.redoStack.dropLast(1)
                        )
                    } else t
                }
            )
        }
        updateActiveTabContent(next, pushUndo = false)
    }

    fun formatActiveTab() {
        val tab = activeTab() ?: return
        val formatted = CodeAnalyzer.formatCode(
            tab.content,
            CodeLanguage.fromId(tab.languageId),
            preferences.value.tabSize
        )
        updateActiveTabContent(formatted)
    }

    fun replaceInActiveTab(findText: String, replaceText: String): Int {
        if (findText.isEmpty()) return 0
        val tab = activeTab() ?: return 0
        val count = Regex(Regex.escape(findText)).findAll(tab.content).count()
        if (count > 0) {
            updateActiveTabContent(tab.content.replace(findText, replaceText))
        }
        return count
    }

    fun createNewFile(path: String, language: CodeLanguage) {
        val projId = _activeProjectId.value ?: return
        viewModelScope.launch {
            val file = workspaceRepo.addOrUpdateFile(
                userId = currentUserId,
                projectId = projId,
                path = path,
                language = language,
                content = language.starterSnippet
            )
            openFileInTab(file)
            workspaceRepo.syncProjectNow(currentUserId, projId)
        }
    }

    fun deleteFileFromProject(fileId: String) {
        val projId = _activeProjectId.value ?: return
        viewModelScope.launch {
            closeTab(fileId)
            workspaceRepo.deleteFile(currentUserId, fileId)
            workspaceRepo.syncProjectNow(currentUserId, projId)
        }
    }

    fun importFileFromUri(context: Context, uri: Uri) {
        val projId = _activeProjectId.value ?: return
        viewModelScope.launch {
            val res = workspaceRepo.readTextFromUri(context, uri)
            res.onSuccess { (name, content) ->
                val lang = CodeLanguage.fromFileName(name)
                val file = workspaceRepo.addOrUpdateFile(currentUserId, projId, name, lang, content)
                openFileInTab(file)
                workspaceRepo.syncProjectNow(currentUserId, projId)
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Import failed"
            }
        }
    }

    fun exportActiveFileToUri(context: Context, uri: Uri) {
        val tab = activeTab() ?: return
        viewModelScope.launch {
            val res = workspaceRepo.writeTextToUri(context, uri, tab.content)
            _statusBanner.value = if (res.isSuccess) {
                "Exported ${tab.name}"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Export failed"
            }
        }
    }

    fun exportBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val backupJson = workspaceRepo.buildBackupJson(currentUserId)
            val res = workspaceRepo.writeTextToUri(context, uri, backupJson)
            _statusBanner.value = if (res.isSuccess) "Backup exported" else "Backup failed"
        }
    }

    fun setStdinInput(input: String) {
        _editorState.update { it.copy(stdinInput = input) }
    }

    fun runActiveFile() {
        val tab = activeTab() ?: return
        val projId = _activeProjectId.value ?: return
        val projName = localProjects.value.firstOrNull { it.id == projId }?.name ?: "Project"

        executionJob?.cancel()
        executionJob = viewModelScope.launch {
            _editorState.update {
                it.copy(executionResult = ExecutionResult(status = ExecutionStatus.RUNNING))
            }
            val savedFile = workspaceRepo.addOrUpdateFile(
                userId = currentUserId,
                projectId = projId,
                path = tab.path,
                language = CodeLanguage.fromId(tab.languageId),
                content = tab.content,
                existingFileId = tab.fileId
            )
            val files = workspaceRepo.getFilesOnce(currentUserId, projId)
            val result = executionEngine.execute(
                activeFile = savedFile,
                projectFiles = files,
                stdinInput = _editorState.value.stdinInput,
                preferences = preferences.value
            )
            _editorState.update { it.copy(executionResult = result) }

            val lang = CodeLanguage.fromId(tab.languageId)
            _debuggerTraces.update { traces ->
                (listOf(
                    DebuggerTraceEntry(
                        timestampMs = System.currentTimeMillis(),
                        source = "${tab.name} (${result.runtimeDescription})",
                        level = result.status.name,
                        message = (result.stdout.ifBlank { result.stderr }).take(240)
                    )
                ) + traces).take(40)
            }

            workspaceRepo.recordExecution(
                userId = currentUserId,
                projectName = projName,
                fileName = tab.name,
                language = lang,
                status = result.status.name,
                outputPreview = result.stdout.ifBlank { result.stderr },
                durationMs = result.durationMs
            )
            if (lang == CodeLanguage.SQL) refreshSqliteTables()
        }
    }

    fun stopExecution() {
        executionJob?.cancel()
        _editorState.update {
            it.copy(
                executionResult = ExecutionResult(
                    status = ExecutionStatus.STOPPED,
                    stdout = it.executionResult.stdout,
                    stderr = "Execution stopped by user."
                )
            )
        }
    }

    fun openCodeSnippetInIde(title: String, language: CodeLanguage, code: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val activeProjId = _activeProjectId.value
            if (activeProjId != null) {
                val fileName = "${title.lowercase().replace(Regex("[^a-z0-9]"), "_").take(20)}.${language.primaryExtension}"
                val file = workspaceRepo.addOrUpdateFile(currentUserId, activeProjId, fileName, language, code)
                openFileInTab(file)
            } else {
                createCustomProject(
                    name = title,
                    description = "Academy practice workspace",
                    category = "academy",
                    language = language,
                    files = listOf(TemplateFileSpec("main.${language.primaryExtension}", language, code))
                )
            }
            onDone()
        }
    }

    fun startCourseFinalProject(course: Course, onDone: () -> Unit) {
        viewModelScope.launch {
            createCustomProject(
                name = course.finalProjectTitleEn,
                description = course.finalProjectBriefEn,
                category = "final_project",
                language = course.primaryLanguage,
                files = course.finalProjectFiles
            )
            cloudSync.saveCourseProgress(courseId = course.id, finalProjectDone = true)
            cloudSync.recordDailyActivityAndStats(
                lessonsDelta = 1L,
                xpDelta = 50L,
                achievementUnlocked = "final_project_${course.id}"
            )
            onDone()
        }
    }

    fun completeLesson(courseId: String, lessonId: String) {
        viewModelScope.launch {
            cloudSync.saveCourseProgress(courseId = courseId, completedLessonId = lessonId)
            cloudSync.recordDailyActivityAndStats(lessonsDelta = 1L, xpDelta = 20L)
        }
    }

    fun verifyChallenge(courseId: String, challenge: CodingChallenge, userCode: String): Boolean {
        val passed = userCode.contains(challenge.expectedSubstring, ignoreCase = true) &&
            challenge.expectedKeywords.all { kw -> userCode.contains(kw, ignoreCase = true) }
        if (passed) {
            viewModelScope.launch {
                cloudSync.saveCourseProgress(courseId = courseId, completedChallengeId = challenge.id)
                cloudSync.recordDailyActivityAndStats(lessonsDelta = 1L, xpDelta = 30L)
            }
        }
        return passed
    }

    fun submitQuizScore(courseId: String, scorePercent: Int) {
        viewModelScope.launch {
            cloudSync.saveCourseProgress(courseId = courseId, quizScore = scorePercent)
            cloudSync.recordDailyActivityAndStats(lessonsDelta = 1L, xpDelta = (scorePercent / 4).toLong())
        }
    }

    // AI Copilot Actions
    fun setAiMode(mode: AiExplanationMode) {
        _aiMode.value = mode
    }

    fun clearAiChat() {
        aiJob?.cancel()
        _aiMessages.value = emptyList()
        _aiGenState.value = AiGenerationState.Idle
    }

    fun sendAiPrompt(promptText: String) {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isEmpty()) return
        aiJob?.cancel()

        val userMsg = AiChatMessage(UUID.randomUUID().toString(), isUser = true, text = cleanPrompt)
        val aiMsgId = UUID.randomUUID().toString()
        _aiMessages.update { it + userMsg + AiChatMessage(aiMsgId, isUser = false, text = "", isStreaming = true) }
        _aiGenState.value = AiGenerationState.Loading

        val contextString = if (preferences.value.shareContextWithAi) {
            buildActiveContextSummary()
        } else null

        aiJob = viewModelScope.launch {
            try {
                aiRepo.streamPrompt(
                    userPrompt = cleanPrompt,
                    mode = _aiMode.value,
                    appLanguage = preferences.value.language,
                    projectContext = contextString
                ).collect { partial ->
                    _aiGenState.value = AiGenerationState.Streaming
                    _aiMessages.update { list ->
                        list.map { m -> if (m.id == aiMsgId) m.copy(text = partial, isStreaming = true) else m }
                    }
                }
                _aiMessages.update { list ->
                    list.map { m -> if (m.id == aiMsgId) m.copy(isStreaming = false) else m }
                }
                _aiGenState.value = AiGenerationState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CodeVerseVM", "AI prompt error", e)
                val errMsg = e.localizedMessage ?: "AI request failed"
                _aiMessages.update { list ->
                    list.filterNot { it.id == aiMsgId && it.text.isBlank() }
                }
                _aiGenState.value = AiGenerationState.Error(errMsg)
            }
        }
    }

    fun requestAiProjectStructure(idea: String) {
        if (idea.isBlank()) return
        aiJob?.cancel()
        _aiGenState.value = AiGenerationState.Loading
        aiJob = viewModelScope.launch {
            val res = aiRepo.generateProjectStructure(idea, preferences.value.language)
            res.onSuccess { plan ->
                _pendingAiProjectPlan.value = plan
                val summary = "Generated project **${plan.projectName}** (${plan.primaryLanguage.displayName}) with ${plan.files.size} files:\n" +
                    plan.files.joinToString("\n") { "- `${it.path}`" }
                _aiMessages.update {
                    it + AiChatMessage(UUID.randomUUID().toString(), isUser = false, text = summary)
                }
                _aiGenState.value = AiGenerationState.Idle
            }.onFailure { err ->
                _aiGenState.value = AiGenerationState.Error(err.localizedMessage ?: "Project generation failed")
            }
        }
    }

    fun materializePendingAiProject(onCreated: () -> Unit) {
        val plan = _pendingAiProjectPlan.value ?: return
        createCustomProject(
            name = plan.projectName,
            description = plan.description,
            category = "ai_generated",
            language = plan.primaryLanguage,
            files = plan.files,
            onCreated = {
                _pendingAiProjectPlan.value = null
                onCreated()
            }
        )
    }

    fun applyAiCodeToActiveEditor(code: String) {
        if (_editorState.value.activeFileId == null) return
        updateActiveTabContent(code)
        _statusBanner.value = "Applied AI code to active editor tab"
    }

    // Dev Tools Actions
    fun runTerminalCommand(cmd: String) {
        val clean = cmd.trim()
        if (clean.isEmpty()) return
        if (clean.equals("clear", ignoreCase = true)) {
            _terminalEntries.value = emptyList()
            return
        }
        viewModelScope.launch {
            val pid = _activeProjectId.value
            val proj = localProjects.value.firstOrNull { it.id == pid }
            val files = if (pid != null) workspaceRepo.getFilesOnce(currentUserId, pid) else emptyList()
            val commits = activeProjectCommits.value
            val output = terminalProcessor.executeCommand(
                rawCommand = clean,
                userId = currentUserId,
                projectId = pid,
                projectName = proj?.name ?: "workspace",
                branch = proj?.gitBranch ?: "main",
                files = files,
                commits = commits
            )
            _terminalEntries.update { (it + TerminalEntry(clean, output)).takeLast(60) }
            if (clean.startsWith("sql", ignoreCase = true)) refreshSqliteTables()
        }
    }

    fun commitLocalGitChanges(message: String) {
        val pid = _activeProjectId.value ?: return
        val proj = localProjects.value.firstOrNull { it.id == pid } ?: return
        viewModelScope.launch {
            val files = workspaceRepo.getFilesOnce(currentUserId, pid)
            workspaceRepo.saveGitCommit(currentUserId, pid, proj.gitBranch, message, files)
            _gitChanges.value = emptyList()
            _statusBanner.value = "Committed to ${proj.gitBranch}"
        }
    }

    fun switchGitBranch(branch: String) {
        val pid = _activeProjectId.value ?: return
        viewModelScope.launch {
            workspaceRepo.updateBranch(currentUserId, pid, branch)
            _statusBanner.value = "Switched to branch $branch"
        }
    }

    fun importFromGitHubRepo(ownerRepo: String) {
        viewModelScope.launch {
            val res = gitHubClient.fetchPublicRepoContents(ownerRepo)
            res.onSuccess { fetched ->
                if (fetched.isEmpty()) {
                    _statusBanner.value = "No top-level files found in $ownerRepo"
                    return@onSuccess
                }
                val specs = fetched.map { (name, content) ->
                    TemplateFileSpec(name, CodeLanguage.fromFileName(name), content)
                }
                createCustomProject(
                    name = ownerRepo.substringAfterLast('/'),
                    description = "Imported from GitHub: $ownerRepo",
                    category = "github_import",
                    language = specs.first().language,
                    files = specs
                )
                _statusBanner.value = "Imported ${specs.size} files from $ownerRepo"
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "GitHub import failed"
            }
        }
    }

    fun updateApiRequest(updated: ApiRequestModel) {
        _apiRequest.value = updated
    }

    fun sendApiRequest() {
        viewModelScope.launch {
            _apiLoading.value = true
            _apiResponse.value = apiTesterClient.executeRequest(_apiRequest.value)
            _apiLoading.value = false
        }
    }

    fun refreshSqliteTables() {
        viewModelScope.launch {
            _sqliteTables.value = dbExplorer.listTables()
        }
    }

    fun syncCloudNow() {
        viewModelScope.launch {
            val res = workspaceRepo.syncAllProjectsNow(currentUserId)
            val restored = workspaceRepo.restoreMissingFromCloud(currentUserId)
            _statusBanner.value = if (res.isSuccess && restored.isSuccess) {
                if (preferences.value.language == AppLanguage.THAI) {
                    "ซิงค์คลาวด์สำเร็จ (${res.getOrDefault(0)} โปรเจกต์)"
                } else {
                    "Cloud sync complete (${res.getOrDefault(0)} projects)"
                }
            } else {
                "Sync completed with warnings"
            }
        }
    }

    fun activeTab(): EditorTabState? {
        val id = _editorState.value.activeFileId ?: return null
        return _editorState.value.openTabs.firstOrNull { it.fileId == id }
    }

    private fun buildActiveContextSummary(): String? {
        val pid = _activeProjectId.value ?: return null
        val proj = localProjects.value.firstOrNull { it.id == pid } ?: return null
        val tab = activeTab()
        val filesList = activeProjectFiles.value.joinToString(", ") { it.path }
        return buildString {
            appendLine("Project: ${proj.name} (${proj.templateType}, branch ${proj.gitBranch})")
            appendLine("Files: $filesList")
            if (tab != null) {
                appendLine("Active File: ${tab.path} (${tab.languageId})")
                appendLine("```${tab.languageId}\n${tab.content.take(4000)}\n```")
            }
        }
    }

    fun getCourses(): List<Course> = AcademyCatalog.allCourses
    fun getTemplates(): List<ProjectTemplate> = ProjectTemplateCatalog.templates
}
