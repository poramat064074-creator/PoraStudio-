package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.core.firebase.authStateFlow
import com.example.core.firebase.configureAppCheck
import com.example.core.firebase.signOutUser
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalStrings
import com.example.core.i18n.stringsFor
import com.example.data.local.CodeVerseDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.repository.AiAssistantRepository
import com.example.data.repository.CloudSyncRepository
import com.example.data.repository.WorkspaceRepository
import com.example.domain.execution.ExecutionEngine
import com.example.domain.tools.ApiTesterClient
import com.example.domain.tools.DatabaseExplorerEngine
import com.example.domain.tools.GitHubIntegrationClient
import com.example.domain.tools.TerminalCommandProcessor
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.IdeScreen
import com.example.ui.screens.LearnScreen
import com.example.ui.screens.NewProjectDialog
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.CodeVerseTheme
import com.example.ui.viewmodel.CodeVerseViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private enum class StudioDestination(val route: String, val icon: ImageVector) {
    DASHBOARD("dashboard", Icons.Filled.Dashboard),
    IDE("ide", Icons.Filled.Code),
    LEARN("learn", Icons.Filled.School),
    AI("ai", Icons.Filled.AutoAwesome),
    TOOLS("tools", Icons.Filled.Build)
}

private class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: CodeVerseDatabase = Room.databaseBuilder(
        appContext,
        CodeVerseDatabase::class.java,
        "codeverse_studio.db"
    ).build()
    val preferencesRepo = PreferencesRepository(appContext)
    val cloudSync = CloudSyncRepository(appContext)
    val workspaceRepo = WorkspaceRepository(database.projectDao(), cloudSync)
    val aiRepo = AiAssistantRepository()
    val executionEngine = ExecutionEngine(appContext)
    val terminalProcessor = TerminalCommandProcessor(workspaceRepo, executionEngine)
    val apiTesterClient = ApiTesterClient()
    val gitHubClient = GitHubIntegrationClient()
    val dbExplorer = DatabaseExplorerEngine(appContext)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureAppCheck(this, intent)

        setContent {
            val container = remember(applicationContext) { AppContainer(applicationContext) }
            val prefs by container.preferencesRepo.preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())
            val authUser by remember { Firebase.auth.authStateFlow() }
                .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
            val scope = rememberCoroutineScope()

            CompositionLocalProvider(LocalStrings provides stringsFor(prefs.language)) {
                CodeVerseTheme(darkTheme = prefs.darkTheme) {
                    val user = authUser
                    if (user == null) {
                        AuthScreen(
                            currentLanguage = prefs.language,
                            onLanguageChange = { lang ->
                                scope.launch { container.preferencesRepo.setLanguage(lang) }
                            },
                            onAuthSuccess = { }
                        )
                    } else {
                        key(user.uid) {
                            val factory = remember(user.uid, container) {
                                object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return CodeVerseViewModel(
                                            currentUserId = user.uid,
                                            userDisplayName = user.displayName ?: "Developer",
                                            userEmail = user.email,
                                            preferencesRepo = container.preferencesRepo,
                                            workspaceRepo = container.workspaceRepo,
                                            cloudSync = container.cloudSync,
                                            aiRepo = container.aiRepo,
                                            executionEngine = container.executionEngine,
                                            terminalProcessor = container.terminalProcessor,
                                            apiTesterClient = container.apiTesterClient,
                                            gitHubClient = container.gitHubClient,
                                            dbExplorer = container.dbExplorer
                                        ) as T
                                    }
                                }
                            }
                            val studioViewModel: CodeVerseViewModel = viewModel(
                                key = user.uid,
                                factory = factory
                            )
                            val credentialManager = remember { CredentialManager.create(this@MainActivity) }
                            CodeVerseMainShell(
                                viewModel = studioViewModel,
                                onSignOut = {
                                    signOutUser(credentialManager, scope)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeVerseMainShell(
    viewModel: CodeVerseViewModel,
    onSignOut: () -> Unit
) {
    val strings = LocalStrings.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val isThai = prefs.language == AppLanguage.THAI

    var currentDestination by rememberSaveable { mutableStateOf(StudioDestination.DASHBOARD) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var pendingLearnCourseId by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val message = statusBanner
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearStatusBanner()
        }
    }

    val navLabels = mapOf(
        StudioDestination.DASHBOARD to strings.navDashboard,
        StudioDestination.IDE to strings.navIde,
        StudioDestination.LEARN to strings.navLearn,
        StudioDestination.AI to strings.navAi,
        StudioDestination.TOOLS to strings.navTools
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        StudioDestination.entries.forEach { dest ->
                            val label = navLabels[dest] ?: dest.name
                            NavigationBarItem(
                                selected = currentDestination == dest,
                                onClick = { currentDestination = dest },
                                icon = { Icon(dest.icon, contentDescription = label) },
                                label = {
                                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        StudioDestination.entries.forEach { dest ->
                            val label = navLabels[dest] ?: dest.name
                            NavigationRailItem(
                                selected = currentDestination == dest,
                                onClick = { currentDestination = dest },
                                icon = { Icon(dest.icon, contentDescription = label) },
                                label = {
                                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentDestination) {
                        StudioDestination.DASHBOARD -> DashboardScreen(
                            viewModel = viewModel,
                            onOpenIde = { currentDestination = StudioDestination.IDE },
                            onOpenLearnCourse = { courseId ->
                                pendingLearnCourseId = courseId
                                currentDestination = StudioDestination.LEARN
                            },
                            onOpenNewProjectDialog = { showNewProjectDialog = true },
                            onSignOut = onSignOut
                        )

                        StudioDestination.IDE -> IdeScreen(
                            viewModel = viewModel,
                            onOpenNewProjectDialog = { showNewProjectDialog = true },
                            onNavigateToSandboxSettings = { currentDestination = StudioDestination.TOOLS },
                            onAskAiWithActiveCode = { prompt ->
                                viewModel.sendAiPrompt(prompt)
                                currentDestination = StudioDestination.AI
                            }
                        )

                        StudioDestination.LEARN -> LearnScreen(
                            viewModel = viewModel,
                            initialCourseId = pendingLearnCourseId,
                            onClearInitialCourse = { pendingLearnCourseId = null },
                            onNavigateToIde = { currentDestination = StudioDestination.IDE }
                        )

                        StudioDestination.AI -> AiAssistantScreen(
                            viewModel = viewModel,
                            onNavigateToIde = { currentDestination = StudioDestination.IDE }
                        )

                        StudioDestination.TOOLS -> ToolsScreen(
                            viewModel = viewModel,
                            onOpenIde = { currentDestination = StudioDestination.IDE },
                            onSignOut = onSignOut
                        )
                    }
                }
            }
        }
    }

    if (showNewProjectDialog) {
        NewProjectDialog(
            isThai = isThai,
            onDismiss = { showNewProjectDialog = false },
            onCreateFromTemplate = { name, template ->
                viewModel.createProjectFromTemplate(name, template) {
                    showNewProjectDialog = false
                    currentDestination = StudioDestination.IDE
                }
            },
            onCreateCustom = { name, desc, language ->
                viewModel.createCustomProject(
                    name = name,
                    description = desc,
                    category = "custom",
                    language = language
                ) {
                    showNewProjectDialog = false
                    currentDestination = StudioDestination.IDE
                }
            }
        )
    }
}
