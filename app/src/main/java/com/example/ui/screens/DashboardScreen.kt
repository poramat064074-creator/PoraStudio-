package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalStrings
import com.example.data.academy.AcademyCatalog
import com.example.data.local.LocalFileEntity
import com.example.data.local.LocalProjectEntity
import com.example.data.model.CloudCourseProgress
import com.example.data.model.CloudDailyActivity
import com.example.data.model.CodeLanguage
import com.example.data.model.ProjectTemplate
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.viewmodel.CodeVerseViewModel

@Composable
fun DashboardScreen(
    viewModel: CodeVerseViewModel,
    onOpenIde: () -> Unit,
    onOpenLearnCourse: (String) -> Unit,
    onOpenNewProjectDialog: () -> Unit,
    onSignOut: () -> Unit
) {
    val strings = LocalStrings.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val projects by viewModel.localProjects.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val totalFiles by viewModel.totalFileCount.collectAsStateWithLifecycle()
    val progressState by viewModel.courseProgressState.collectAsStateWithLifecycle()
    val activityState by viewModel.dailyActivityState.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    val courseProgressList = (progressState as? UiState.Success)?.data.orEmpty()
    val activityList = (activityState as? UiState.Success)?.data.orEmpty()
    val templates = viewModel.getTemplates()

    val isThai = prefs.language == AppLanguage.THAI

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_academy_hero),
                    contentDescription = strings.appName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.65f)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_porastudio_logo),
                                    contentDescription = strings.appName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${strings.welcomeBack}, ${profile?.displayName ?: "Developer"}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = strings.tagline,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val next = if (isThai) AppLanguage.ENGLISH else AppLanguage.THAI
                                        viewModel.setLanguage(next)
                                    },
                                    modifier = Modifier.testTag("dashboard_language_toggle")
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = strings.languageLabel, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(prefs.language.displayName)
                                }
                                IconButton(
                                    onClick = onSignOut,
                                    modifier = Modifier.testTag("dashboard_sign_out")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = strings.signOut,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onOpenNewProjectDialog,
                                modifier = Modifier.testTag("dashboard_new_project_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = strings.newProject, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.newProject)
                            }
                            FilledTonalButton(
                                onClick = onOpenIde,
                                modifier = Modifier.testTag("dashboard_open_ide_btn")
                            ) {
                                Icon(Icons.Default.Code, contentDescription = strings.openInIde, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.openInIde)
                            }
                        }
                    }
                }
            }
        }

        // Coding Statistics Cards
        item {
            Text(
                text = strings.codingStats,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    StatCard(
                        icon = Icons.Default.FolderOpen,
                        label = strings.projectsLabel,
                        value = projects.size.toString(),
                        accent = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    StatCard(
                        icon = Icons.Default.Description,
                        label = strings.filesLabel,
                        value = totalFiles.toString(),
                        accent = MaterialTheme.colorScheme.secondary
                    )
                }
                item {
                    StatCard(
                        icon = Icons.Default.PlayArrow,
                        label = strings.executionsLabel,
                        value = (profile?.totalExecutions ?: 0L).toString(),
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                }
                item {
                    StatCard(
                        icon = Icons.Default.LocalFireDepartment,
                        label = strings.streakLabel,
                        value = (profile?.streakDays ?: 1L).toString(),
                        accent = Color(0xFFFBBF24)
                    )
                }
                item {
                    StatCard(
                        icon = Icons.Default.Star,
                        label = strings.xpLabel,
                        value = (profile?.xp ?: 20L).toString(),
                        accent = Color(0xFFF472B6)
                    )
                }
            }
        }

        // 7-Day Activity Chart
        item {
            DailyActivityChartCard(
                title = strings.dailyActivityTitle,
                activities = activityList,
                isThai = isThai
            )
        }

        // Continue Learning Card
        item {
            val nextCourse = AcademyCatalog.allCourses.firstOrNull { course ->
                val prog = courseProgressList.firstOrNull { it.courseId == course.id }
                val doneCount = prog?.completedLessonIds?.size ?: 0
                doneCount < course.lessons.size
            } ?: AcademyCatalog.allCourses.first()
            val prog = courseProgressList.firstOrNull { it.courseId == nextCourse.id }
            val completedLessons = prog?.completedLessonIds?.size ?: 0
            val totalLessons = nextCourse.lessons.size.coerceAtLeast(1)
            val fraction = (completedLessons.toFloat() / totalLessons.toFloat()).coerceIn(0f, 1f)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .clickable { onOpenLearnCourse(nextCourse.id) }
                    .testTag("dashboard_continue_learning_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = strings.continueLearning,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "$completedLessons/$totalLessons",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (isThai) nextCourse.titleTh else nextCourse.titleEn,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isThai) nextCourse.summaryTh else nextCourse.summaryEn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        // Quick Project Creation Templates
        item {
            Text(
                text = strings.quickCreateProject,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(templates, key = { it.id }) { template ->
                    QuickTemplateCard(
                        template = template,
                        isThai = isThai,
                        onClick = {
                            val title = if (isThai) template.titleTh else template.titleEn
                            viewModel.createProjectFromTemplate(title, template) {
                                onOpenIde()
                            }
                        }
                    )
                }
            }
        }

        // Recent Projects
        item {
            Text(
                text = strings.recentProjects,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (projects.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(strings.noProjectsYet, style = MaterialTheme.typography.titleMedium)
                        Text(
                            strings.createFirstProjectTip,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(projects.take(6), key = { it.id }) { project ->
                RecentProjectRow(
                    project = project,
                    onOpen = {
                        viewModel.selectProject(project.id)
                        onOpenIde()
                    },
                    onDelete = { viewModel.deleteProject(project.id) }
                )
            }
        }

        // Recent Files
        if (recentFiles.isNotEmpty()) {
            item {
                Text(
                    text = strings.recentFiles,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentFiles.take(8), key = { it.id }) { file ->
                        RecentFileChip(
                            file = file,
                            onClick = {
                                viewModel.openRecentFile(file)
                                onOpenIde()
                            }
                        )
                    }
                }
            }
        }

        // Favorite Languages & Achievements
        item {
            FavoriteLanguagesAndAchievements(
                profile = profile,
                favTitle = strings.favoriteLanguages,
                achTitle = strings.achievementsTitle,
                isThai = isThai
            )
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, accent: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .width(130.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DailyActivityChartCard(
    title: String,
    activities: List<CloudDailyActivity>,
    isThai: Boolean
) {
    val sorted = activities.sortedBy { it.dateKey }.takeLast(7)
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (sorted.isEmpty()) {
                Text(
                    text = if (isThai) "เริ่มเขียนหรือรันโค้ดวันนี้เพื่อบันทึกกราฟกิจกรรม" else "Edit or run code today to populate your activity chart.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxVal = sorted.maxOf { (it.editsCount + it.runsCount + it.lessonsCount).toFloat() }.coerceAtLeast(1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                ) {
                    val slotWidth = size.width / sorted.size.coerceAtLeast(1)
                    val barW = (slotWidth * 0.48f).coerceAtMost(36.dp.toPx())
                    sorted.forEachIndexed { index, item ->
                        val total = (item.editsCount + item.runsCount + item.lessonsCount).toFloat()
                        val left = index * slotWidth + (slotWidth - barW) / 2f
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(left, 0f),
                            size = Size(barW, size.height),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        val h = (total / maxVal) * size.height
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, size.height - h),
                            size = Size(barW, h.coerceAtLeast(8f)),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    sorted.forEach { act ->
                        Text(
                            text = act.dateKey.takeLast(5),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTemplateCard(
    template: ProjectTemplate,
    isThai: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .width(210.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("template_card_${template.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                color = Color(template.primaryLanguage.badgeHex).copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${template.category} • ${template.primaryLanguage.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(template.primaryLanguage.badgeHex),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(
                text = if (isThai) template.titleTh else template.titleEn,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isThai) template.descriptionTh else template.descriptionEn,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentProjectRow(
    project: LocalProjectEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val lang = CodeLanguage.fromId(project.primaryLanguageId)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .testTag("recent_project_${project.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(lang.badgeHex).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lang.primaryExtension.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(lang.badgeHex),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = "${project.templateType} • ${project.gitBranch}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun RecentFileChip(file: LocalFileEntity, onClick: () -> Unit) {
    val lang = CodeLanguage.fromId(file.languageId)
    AssistChip(
        onClick = onClick,
        label = { Text(file.name) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(lang.badgeHex))
            )
        }
    )
}

@Composable
private fun FavoriteLanguagesAndAchievements(
    profile: UserProfile?,
    favTitle: String,
    achTitle: String,
    isThai: Boolean
) {
    val favs = profile?.favoriteLanguages?.ifEmpty { listOf("HTML", "JavaScript", "SQL") }
        ?: listOf("HTML", "JavaScript", "SQL")
    val achievements = profile?.achievements?.ifEmpty { listOf("welcome_porastudio") }
        ?: listOf("welcome_porastudio")

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(favTitle, style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(favs) { langName ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = langName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Text(achTitle, style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(achievements) { ach ->
                    val label = when {
                        ach == "welcome_porastudio" || ach == "welcome_codeverse" ->
                            if (isThai) "นักสำรวจ PoraStudio" else "PoraStudio Explorer"
                        ach == "first_execution" -> if (isThai) "รันโค้ดแรกสำเร็จ" else "First Code Run"
                        ach.startsWith("final_project_") -> if (isThai) "พิชิตโปรเจกต์จบ" else "Capstone Builder"
                        else -> ach
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
