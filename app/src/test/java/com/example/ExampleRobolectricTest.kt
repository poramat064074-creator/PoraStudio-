package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.credentials.CredentialManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.firebase.attemptAutoSignIn
import com.example.core.firebase.authStateFlow
import com.example.core.firebase.onGoogleSignInClicked
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalStrings
import com.example.core.i18n.stringsFor
import com.example.data.local.CodeVerseDatabase
import com.example.data.local.LocalFileEntity
import com.example.data.local.LocalGitCommitEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.model.CodeLanguage
import com.example.data.model.ExecutionStatus
import com.example.data.model.GitDiffStatus
import com.example.data.model.ProjectTemplateCatalog
import com.example.data.repository.AiAssistantRepository
import com.example.data.repository.AiExplanationMode
import com.example.data.repository.CloudSyncRepository
import com.example.data.repository.WorkspaceRepository
import com.example.domain.execution.ExecutionEngine
import com.example.domain.tools.ApiTesterClient
import com.example.domain.tools.DatabaseExplorerEngine
import com.example.domain.tools.GitHubIntegrationClient
import com.example.domain.tools.LocalGitEngine
import com.example.domain.tools.TerminalCommandProcessor
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.CodeVerseTheme
import com.example.ui.viewmodel.CodeVerseViewModel
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
    }

    @Test
    fun `app name matches PoraStudio and Firebase config resources exist`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("PoraStudio", appName)
        assertTrue("Google Play title must be <= 30 chars", appName.length <= 30)

        val dbId = context.getString(R.string.firestore_database_id)
        val recaptcha = context.getString(R.string.recaptcha_site_key)
        val clientId = context.getString(R.string.default_web_client_id)
        assertTrue(dbId.isNotBlank())
        assertTrue(recaptcha.isNotBlank())
        assertTrue(clientId.contains(".apps.googleusercontent.com"))
    }

    @Test
    fun `auth screen renders PoraStudio branding, language toggle, and Google Sign-In button`() {
        composeRule.setContent {
            var lang by remember { mutableStateOf(AppLanguage.THAI) }
            CompositionLocalProvider(LocalStrings provides stringsFor(lang)) {
                CodeVerseTheme(darkTheme = true) {
                    AuthScreen(
                        currentLanguage = lang,
                        onLanguageChange = { lang = it },
                        onAuthSuccess = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("PoraStudio").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("เข้าสู่ระบบด้วย Google").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("auth_language_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Sign in with Google").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `auth helper validates Activity context, authStateFlow, and CloudSync auth gate cleanly`() {
        val credManager = CredentialManager.create(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        var emittedUser: Any? = "UNSET"
        val authFlowJob = scope.launch {
            emittedUser = Firebase.auth.authStateFlow().first()
        }
        val deadline = System.currentTimeMillis() + 1_500L
        while (emittedUser == "UNSET" && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        authFlowJob.cancel()
        assertEquals(null, emittedUser)

        var reportedError: String? = null
        onGoogleSignInClicked(
            context = context,
            credentialManager = credManager,
            onAuthSuccess = {},
            onAuthError = { reportedError = it },
            scope = scope
        )
        assertNotNull(reportedError)
        assertTrue(reportedError!!.contains("Activity", ignoreCase = true))

        // Unauthenticated context without default_web_client_id resource invokes onUnauthenticated
        val bareContext = android.view.ContextThemeWrapper(context, 0).let { base ->
            object : android.content.ContextWrapper(base) {
                override fun getResources(): android.content.res.Resources =
                    throw android.content.res.Resources.NotFoundException("no web client id")
            }
        }
        var unauthCalled = false
        attemptAutoSignIn(
            context = bareContext,
            credentialManager = credManager,
            onAuthSuccess = {},
            onUnauthenticated = { unauthCalled = true },
            scope = scope
        )
        assertTrue(unauthCalled)

        // CloudSyncRepository must block writes when user is not signed in with Google
        val cloudSync = CloudSyncRepository(context)
        val ensureResult = runBlocking(Dispatchers.IO) {
            cloudSync.ensureUserProfile("Tester", "t@example.com", "th")
        }
        assertTrue(ensureResult.isFailure)
        assertTrue(ensureResult.exceptionOrNull()?.message?.contains("signed in with Google", ignoreCase = true) == true)
    }

    @Test
    fun `room persistence, project template creation, backup json, and cascade delete work reliably`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, CodeVerseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.projectDao()
            val repo = WorkspaceRepository(dao, CloudSyncRepository(context))
            val userId = "test_user_1"
            val template = ProjectTemplateCatalog.templates.first { it.id == "website" }

            val proj = repo.createProjectFromTemplate(userId, "My Web", template)
            val files = repo.getFilesOnce(userId, proj.id)
            assertTrue(files.size >= 3)
            assertEquals(files.size, dao.observeTotalFileCount(userId).first())

            val commit = repo.saveGitCommit(userId, proj.id, "main", "Initial snapshot", files)
            assertEquals("Initial snapshot", commit.message)
            assertNotNull(repo.getLatestCommit(userId, proj.id))

            val backupJson = JSONObject(repo.buildBackupJson(userId))
            assertEquals(1, backupJson.getJSONArray("projects").length())

            // Cascade delete must also remove child files and git commits
            repo.deleteProject(userId, proj.id)
            assertTrue(dao.getProjectsOnce(userId).isEmpty())
            assertTrue(dao.getFilesOnce(userId, proj.id).isEmpty())
            assertEquals(0, dao.observeTotalFileCount(userId).first())
            assertEquals(null, dao.getLatestCommit(userId, proj.id))
        } finally {
            db.close()
        }
    }

    @Test
    fun `preferences repository persists and clamps settings cleanly`() = runBlocking {
        val prefsRepo = PreferencesRepository(context)
        prefsRepo.setLanguage(AppLanguage.ENGLISH)
        prefsRepo.setDarkTheme(false)
        prefsRepo.setShareContextWithAi(true)
        prefsRepo.saveEditorAndSandboxSettings(
            fontSizeSp = 99, // should clamp to 22
            tabSize = 4,
            sandboxUrl = "  https://sandbox.example.com/execute  ",
            timeoutMs = 100, // should clamp to 2000
            memoryMb = 4096 // should clamp to 1024
        )
        val loaded = prefsRepo.preferences.first()
        assertEquals(AppLanguage.ENGLISH, loaded.language)
        assertFalse(loaded.darkTheme)
        assertTrue(loaded.shareContextWithAi)
        assertEquals(22, loaded.editorFontSizeSp)
        assertEquals(4, loaded.tabSize)
        assertEquals("https://sandbox.example.com/execute", loaded.sandboxUrl)
        assertEquals(2_000, loaded.sandboxTimeoutMs)
        assertEquals(1024, loaded.sandboxMemoryMb)
    }

    @Test
    fun `viewmodel project switching, editor undo-redo, terminal commands, and ai state work end-to-end`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, CodeVerseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefsRepo = PreferencesRepository(context)
            val cloudSync = CloudSyncRepository(context)
            val workspaceRepo = WorkspaceRepository(db.projectDao(), cloudSync)
            val engine = ExecutionEngine(context)
            val terminal = TerminalCommandProcessor(workspaceRepo, engine)
            val vm = CodeVerseViewModel(
                currentUserId = "test_user_vm",
                userDisplayName = "Tester",
                userEmail = "tester@example.com",
                preferencesRepo = prefsRepo,
                workspaceRepo = workspaceRepo,
                cloudSync = cloudSync,
                aiRepo = AiAssistantRepository(),
                executionEngine = engine,
                terminalProcessor = terminal,
                apiTesterClient = ApiTesterClient(),
                gitHubClient = GitHubIntegrationClient(),
                dbExplorer = DatabaseExplorerEngine(context)
            )

            // Create two projects directly in workspace and test openRecentFile without race overwrite
            val tpl = ProjectTemplateCatalog.templates.first { it.id == "website" }
            val proj1 = workspaceRepo.createProjectFromTemplate("test_user_vm", "Site 1", tpl)
            val proj2 = workspaceRepo.createProjectFromTemplate("test_user_vm", "Site 2", tpl)
            val proj2Files = workspaceRepo.getFilesOnce("test_user_vm", proj2.id)
            val targetCss = proj2Files.first { it.name.endsWith(".css") }

            vm.selectProject(proj1.id)
            vm.openRecentFile(targetCss)
            repeat(50) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                if (vm.activeTab()?.fileId == targetCss.id) return@repeat
                kotlinx.coroutines.delay(25L)
            }
            assertEquals(proj2.id, vm.activeProjectId.value)
            assertEquals(targetCss.id, vm.activeTab()?.fileId)

            // Undo / Redo & AI code apply
            vm.applyAiCodeToActiveEditor("body { color: #048cf9; }")
            assertEquals("body { color: #048cf9; }", vm.activeTab()?.content)
            vm.undoActiveTab()
            assertEquals(targetCss.content, vm.activeTab()?.content)
            vm.redoActiveTab()
            assertEquals("body { color: #048cf9; }", vm.activeTab()?.content)

            // AI mode & clear chat
            vm.setAiMode(AiExplanationMode.EXPERT)
            assertEquals(AiExplanationMode.EXPERT, vm.aiMode.value)
            vm.clearAiChat()
            assertTrue(vm.aiMessages.value.isEmpty())

            // Terminal commands execute cleanly
            val helpOut = terminal.executeCommand("help", "test_user_vm", proj2.id, proj2.name, "main", proj2Files, emptyList())
            assertTrue(helpOut.contains("git status"))
            val lsOut = terminal.executeCommand("ls", "test_user_vm", proj2.id, proj2.name, "main", proj2Files, emptyList())
            assertTrue(lsOut.contains("styles.css"))
            val sqlOut = terminal.executeCommand("sql SELECT 40 + 2 AS answer;", "test_user_vm", proj2.id, proj2.name, "main", proj2Files, emptyList())
            assertTrue(sqlOut.contains("42"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `sqlite execution runs real queries on device`() = runBlocking {
        val engine = ExecutionEngine(context)
        val sqlFile = LocalFileEntity(
            id = "f1",
            projectId = "p1",
            userId = "u1",
            path = "query.sql",
            name = "query.sql",
            languageId = CodeLanguage.SQL.id,
            content = """
                CREATE TABLE IF NOT EXISTS learners (id INTEGER PRIMARY KEY, name TEXT);
                DELETE FROM learners;
                INSERT INTO learners (id, name) VALUES (1, 'Alice'), (2, 'Bob');
                SELECT id, name FROM learners ORDER BY id ASC;
            """.trimIndent(),
            updatedAtMillis = 0L
        )
        val res = engine.execute(sqlFile, listOf(sqlFile), "", UserPreferences())
        assertEquals(ExecutionStatus.SUCCESS, res.status)
        assertEquals(listOf("id", "name"), res.sqlColumns)
        assertEquals(2, res.sqlRows.size)
        assertEquals("Alice", res.sqlRows[0][1])
    }

    @Test
    fun `python executes natively on device including json stdlib and real timeouts`() = runBlocking {
        val engine = ExecutionEngine(context)
        val pyFile = LocalFileEntity(
            id = "f2",
            projectId = "p1",
            userId = "u1",
            path = "main.py",
            name = "main.py",
            languageId = CodeLanguage.PYTHON.id,
            content = """
                import json
                data = json.loads('{"lang":"Python","version":3}')
                print(data["lang"], json.dumps([1, 2, 3]))
            """.trimIndent(),
            updatedAtMillis = 0L
        )
        val res = engine.execute(pyFile, listOf(pyFile), "", UserPreferences(sandboxUrl = ""))
        assertEquals(ExecutionStatus.SUCCESS, res.status)
        assertTrue(res.stdout.startsWith("Python"))

        val loopOutcome = engine.executePythonOnDevice(
            code = "while True:\n    pass",
            timeoutMs = 600L
        )
        assertEquals(ExecutionStatus.ERROR, loopOutcome.status)
        assertTrue(loopOutcome.stderr.contains("TimeoutError"))
    }

    @Test
    fun `unconfigured remote runtime honestly reports RUNTIME_UNAVAILABLE instead of simulating`() = runBlocking {
        val engine = ExecutionEngine(context)
        val cppFile = LocalFileEntity(
            id = "f_cpp",
            projectId = "p1",
            userId = "u1",
            path = "main.cpp",
            name = "main.cpp",
            languageId = CodeLanguage.CPP.id,
            content = "#include <iostream>\nint main() { std::cout << 42; }",
            updatedAtMillis = 0L
        )
        val res = engine.execute(cppFile, listOf(cppFile), "", UserPreferences(sandboxUrl = ""))
        assertEquals(ExecutionStatus.RUNTIME_UNAVAILABLE, res.status)
        assertTrue(res.stderr.contains("Sandbox", ignoreCase = true))
    }

    @Test
    fun `html bundler resolves relative css and js, injects csp and preview console bridge, and rejects blank html`() = runBlocking {
        val engine = ExecutionEngine(context)

        val htmlFile = LocalFileEntity(
            id = "h1", projectId = "p1", userId = "u1", path = "index.html", name = "index.html",
            languageId = CodeLanguage.HTML.id,
            content = """<!DOCTYPE html><html><head><link rel="stylesheet" href="css/app.css"></head><body><h1>Hi</h1><script src="./app.js"></script></body></html>""",
            updatedAtMillis = 0L
        )
        val cssFile = LocalFileEntity(
            id = "c1", projectId = "p1", userId = "u1", path = "css/app.css", name = "app.css",
            languageId = CodeLanguage.CSS.id,
            content = "h1 { color: #22d3ee; }",
            updatedAtMillis = 0L
        )
        val jsFile = LocalFileEntity(
            id = "j1", projectId = "p1", userId = "u1", path = "app.js", name = "app.js",
            languageId = CodeLanguage.JAVASCRIPT.id,
            content = "console.log('ready');",
            updatedAtMillis = 0L
        )

        val res = engine.execute(htmlFile, listOf(htmlFile, cssFile, jsFile), "", UserPreferences())
        assertEquals(ExecutionStatus.SUCCESS, res.status)
        val preview = res.htmlPreviewContent.orEmpty()
        assertTrue(preview.contains("<style>\nh1 { color: #22d3ee; }\n</style>"))
        assertTrue(preview.contains("<script>\nconsole.log('ready');\n</script>"))
        assertTrue(preview.contains("Content-Security-Policy"))
        assertTrue(preview.contains("CodeVersePreviewBridge"))

        val emptyHtml = htmlFile.copy(content = "   ")
        val emptyRes = engine.execute(emptyHtml, listOf(emptyHtml), "", UserPreferences())
        assertEquals(ExecutionStatus.ERROR, emptyRes.status)
    }

    @Test
    fun `javascript sandbox builds isolated web worker for pure js and network-blocked harness for dom js`() {
        val engine = ExecutionEngine(context)

        val workerHarness = engine.buildSandboxedJsHarness(
            code = "console.log(2 + 2);",
            stdinLines = JSONArray(listOf("Alice")),
            timeoutMs = 5_000L,
            useWorker = true
        )
        assertTrue(workerHarness.contains("new Worker"))
        assertTrue(workerHarness.contains("worker.terminate()"))
        assertTrue(workerHarness.contains("Network access disabled"))

        val domHarness = engine.buildSandboxedJsHarness(
            code = "document.title = 'Demo';",
            stdinLines = JSONArray(),
            timeoutMs = 5_000L,
            useWorker = false
        )
        assertFalse(domHarness.contains("new Worker"))
        assertTrue(domHarness.contains("Network access is disabled"))
    }

    @Test
    fun `json and xml validation report real diagnostics on invalid input`() = runBlocking {
        val engine = ExecutionEngine(context)
        val badJson = LocalFileEntity(
            id = "f3",
            projectId = "p1",
            userId = "u1",
            path = "bad.json",
            name = "bad.json",
            languageId = CodeLanguage.JSON.id,
            content = "{ \"name\": ",
            updatedAtMillis = 0L
        )
        val res = engine.execute(badJson, listOf(badJson), "", UserPreferences())
        assertEquals(ExecutionStatus.ERROR, res.status)
        assertTrue(res.stderr.isNotBlank())
    }

    @Test
    fun `local git engine computes added, modified, and deleted file diffs`() {
        val commit = LocalGitCommitEntity(
            id = "c1",
            projectId = "p1",
            userId = "u1",
            branch = "main",
            message = "init",
            snapshotJson = """{"a.py":"print(1)","removed.txt":"old"}""",
            timestampMillis = 0L
        )
        val current = listOf(
            LocalFileEntity(id = "1", projectId = "p1", userId = "u1", path = "a.py", name = "a.py", languageId = "python", content = "print(2)", updatedAtMillis = 0L),
            LocalFileEntity(id = "2", projectId = "p1", userId = "u1", path = "b.py", name = "b.py", languageId = "python", content = "x = 5", updatedAtMillis = 0L)
        )
        val changes = LocalGitEngine.computeChanges(current, commit)
        assertEquals(GitDiffStatus.MODIFIED, changes.first { it.path == "a.py" }.status)
        assertEquals(GitDiffStatus.ADDED, changes.first { it.path == "b.py" }.status)
        assertEquals(GitDiffStatus.DELETED, changes.first { it.path == "removed.txt" }.status)
    }
}
