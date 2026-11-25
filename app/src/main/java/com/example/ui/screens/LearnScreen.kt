package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalStrings
import com.example.data.model.CloudCourseProgress
import com.example.data.model.CodeLanguage
import com.example.data.model.Course
import com.example.data.model.SkillLevel
import com.example.data.model.UiState
import com.example.domain.editor.SyntaxHighlightTransformation
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.CodeVerseViewModel

@Composable
fun LearnScreen(
    viewModel: CodeVerseViewModel,
    initialCourseId: String?,
    onClearInitialCourse: () -> Unit,
    onNavigateToIde: () -> Unit
) {
    val strings = LocalStrings.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val progressState by viewModel.courseProgressState.collectAsStateWithLifecycle()
    val isThai = prefs.language == AppLanguage.THAI
    val courses = remember { viewModel.getCourses() }
    val progressMap = remember(progressState) {
        ((progressState as? UiState.Success)?.data ?: emptyList()).associateBy { it.courseId }
    }

    var selectedCourseId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<SkillLevel?>(null) }

    LaunchedEffect(initialCourseId) {
        if (!initialCourseId.isNullOrBlank() && courses.any { it.id == initialCourseId }) {
            selectedCourseId = initialCourseId
            onClearInitialCourse()
        }
    }

    val activeCourse = courses.firstOrNull { it.id == selectedCourseId }
    if (activeCourse != null) {
        BackHandler {
            selectedCourseId = null
        }
        CourseDetailView(
            course = activeCourse,
            progress = progressMap[activeCourse.id],
            isThai = isThai,
            selectedLevelFilter = selectedLevel,
            onBack = { selectedCourseId = null },
            onTryInIde = { title, lang, code ->
                viewModel.openCodeSnippetInIde(title, lang, code, onDone = onNavigateToIde)
            },
            onCompleteLesson = { lessonId ->
                viewModel.completeLesson(activeCourse.id, lessonId)
            },
            onVerifyChallenge = { challenge, userCode ->
                viewModel.verifyChallenge(activeCourse.id, challenge, userCode)
            },
            onSubmitQuiz = { score ->
                viewModel.submitQuizScore(activeCourse.id, score)
            },
            onStartFinalProject = {
                viewModel.startCourseFinalProject(activeCourse, onDone = onNavigateToIde)
            }
        )
        return
    }

    val filteredCourses = remember(courses, searchQuery, selectedLevel, isThai) {
        courses.filter { course ->
            val matchesQuery = searchQuery.isBlank() ||
                course.titleTh.contains(searchQuery, ignoreCase = true) ||
                course.titleEn.contains(searchQuery, ignoreCase = true) ||
                course.primaryLanguage.displayName.contains(searchQuery, ignoreCase = true)
            val matchesLevel = selectedLevel == null || course.lessons.any { it.level == selectedLevel }
            matchesQuery && matchesLevel
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("learn_catalog_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(148.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.img_academy_hero),
                        contentDescription = strings.academyTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = strings.academyTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.academySubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = {
                    Text(if (isThai) "ค้นหาหลักสูตรหรือภาษา..." else "Search 15 courses or languages...")
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("learn_search_input")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedLevel == null,
                        onClick = { selectedLevel = null },
                        label = { Text(strings.allLevels) },
                        modifier = Modifier.testTag("level_chip_all")
                    )
                }
                items(SkillLevel.entries) { level ->
                    FilterChip(
                        selected = selectedLevel == level,
                        onClick = { selectedLevel = if (selectedLevel == level) null else level },
                        label = { Text(if (isThai) level.labelTh else level.labelEn) },
                        modifier = Modifier.testTag("level_chip_${level.name.lowercase()}")
                    )
                }
            }
        }

        items(filteredCourses, key = { it.id }) { course ->
            val p = progressMap[course.id]
            val totalSteps = (course.lessons.size + course.challenges.size + 2).coerceAtLeast(1)
            val doneSteps = (p?.completedLessonIds?.size ?: 0) +
                (p?.completedChallengeIds?.size ?: 0) +
                (if ((p?.bestQuizScore ?: 0L) >= 60L) 1 else 0) +
                (if (p?.finalProjectDone == true) 1 else 0)
            val ratio = (doneSteps.toFloat() / totalSteps).coerceIn(0f, 1f)

            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedCourseId = course.id }
                    .testTag("course_card_${course.id}")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isThai) course.titleTh else course.titleEn,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = course.primaryLanguage.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isThai) course.summaryTh else course.summaryEn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isThai) {
                                "${course.lessons.size} บท • ${course.challenges.size} โจทย์ • ${course.quiz.size} ควิซ"
                            } else {
                                "${course.lessons.size} Lessons • ${course.challenges.size} Challenges • ${course.quiz.size} Quiz"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(ratio * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (ratio >= 1f) EmeraldTertiary else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseDetailView(
    course: Course,
    progress: CloudCourseProgress?,
    isThai: Boolean,
    selectedLevelFilter: SkillLevel?,
    onBack: () -> Unit,
    onTryInIde: (title: String, lang: CodeLanguage, code: String) -> Unit,
    onCompleteLesson: (String) -> Unit,
    onVerifyChallenge: (challenge: com.example.data.model.CodingChallenge, userCode: String) -> Boolean,
    onSubmitQuiz: (Int) -> Unit,
    onStartFinalProject: () -> Unit
) {
    val strings = LocalStrings.current
    var activeTab by remember { mutableIntStateOf(0) }
    var detailLevelFilter by remember(course.id) { mutableStateOf(selectedLevelFilter) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("course_detail_back")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isThai) course.titleTh else course.titleEn,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${course.primaryLanguage.displayName} • ${progress?.completedLessonIds?.size ?: 0}/${course.lessons.size} ${strings.lessonsTab}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val tabs = listOf(
            strings.lessonsTab to Icons.Filled.School,
            strings.exercisesTab to Icons.Filled.Code,
            strings.quizTab to Icons.Filled.Quiz,
            strings.finalProjectTab to Icons.Filled.RocketLaunch
        )
        TabRow(selectedTabIndex = activeTab) {
            tabs.forEachIndexed { idx, (label, _) ->
                Tab(
                    selected = activeTab == idx,
                    onClick = { activeTab = idx },
                    text = {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    modifier = Modifier.testTag("course_tab_$idx")
                )
            }
        }

        when (activeTab) {
            0 -> {
                val visibleLessons = remember(course, detailLevelFilter) {
                    if (detailLevelFilter == null) course.lessons else course.lessons.filter { it.level == detailLevelFilter }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = detailLevelFilter == null,
                                onClick = { detailLevelFilter = null },
                                label = { Text(strings.allLevels) }
                            )
                            SkillLevel.entries.forEach { lvl ->
                                FilterChip(
                                    selected = detailLevelFilter == lvl,
                                    onClick = { detailLevelFilter = if (detailLevelFilter == lvl) null else lvl },
                                    label = { Text(if (isThai) lvl.labelTh else lvl.labelEn) }
                                )
                            }
                        }
                    }

                    items(visibleLessons, key = { it.id }) { lesson ->
                        val isDone = progress?.completedLessonIds?.contains(lesson.id) == true
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(if (isThai) lesson.level.labelTh else lesson.level.labelEn)
                                        }
                                    )
                                    if (isDone) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = strings.completedBadge,
                                                tint = EmeraldTertiary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                strings.completedBadge,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = EmeraldTertiary
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = if (isThai) lesson.titleTh else lesson.titleEn,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isThai) lesson.explanationTh else lesson.explanationEn,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                CodeSnippetBlock(
                                    code = lesson.codeExample,
                                    language = lesson.language
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val title = if (isThai) lesson.titleEn else lesson.titleEn
                                            onTryInIde(title, lesson.language, lesson.codeExample)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("try_lesson_${lesson.id}")
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(strings.tryInIde, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Button(
                                        onClick = { onCompleteLesson(lesson.id) },
                                        enabled = !isDone,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("complete_lesson_${lesson.id}")
                                    ) {
                                        Text(
                                            if (isDone) strings.completedBadge else strings.markComplete,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(course.challenges, key = { it.id }) { challenge ->
                        var codeText by remember(challenge.id) { mutableStateOf(challenge.starterCode) }
                        var checkFeedback by remember(challenge.id) { mutableStateOf<Boolean?>(null) }
                        val alreadyDone = progress?.completedChallengeIds?.contains(challenge.id) == true

                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isThai) challenge.titleTh else challenge.titleEn,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (alreadyDone) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = strings.completedBadge,
                                            tint = EmeraldTertiary
                                        )
                                    }
                                }
                                Text(
                                    text = if (isThai) challenge.promptTh else challenge.promptEn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline,
                                            MaterialTheme.shapes.small
                                        )
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerLowest,
                                            MaterialTheme.shapes.small
                                        )
                                        .padding(12.dp)
                                ) {
                                    BasicTextField(
                                        value = codeText,
                                        onValueChange = {
                                            codeText = it
                                            checkFeedback = null
                                        },
                                        textStyle = TextStyle(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("challenge_editor_${challenge.id}")
                                    )
                                }

                                if (checkFeedback != null) {
                                    Text(
                                        text = if (checkFeedback == true) {
                                            if (isThai) "ยอดเยี่ยม! ผ่านแบบฝึกหัด (+30 XP)" else "Passed! Challenge complete (+30 XP)"
                                        } else {
                                            if (isThai) {
                                                "ยังไม่ครบเงื่อนไข ลองใช้: ${challenge.expectedKeywords.joinToString(", ")}"
                                            } else {
                                                "Not quite yet. Ensure you include: ${challenge.expectedKeywords.joinToString(", ")}"
                                            }
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (checkFeedback == true) EmeraldTertiary else CrimsonError
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onTryInIde(challenge.titleEn, challenge.language, codeText) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(strings.tryInIde, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Button(
                                        onClick = {
                                            checkFeedback = onVerifyChallenge(challenge, codeText)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("verify_challenge_${challenge.id}")
                                    ) {
                                        Text(strings.checkSolution, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                val selectedAnswers = remember(course.id) { mutableStateMapOf<String, Int>() }
                var quizSubmitted by remember(course.id) { mutableStateOf(false) }
                var computedScore by remember(course.id) { mutableIntStateOf(0) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if ((progress?.bestQuizScore ?: 0L) > 0L) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isThai) {
                                        "คะแนนสูงสุดของคุณ: ${progress?.bestQuizScore}%"
                                    } else {
                                        "Your Best Score: ${progress?.bestQuizScore}%"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    items(course.quiz, key = { it.id }) { q ->
                        val options = if (isThai) q.optionsTh else q.optionsEn
                        val chosen = selectedAnswers[q.id]

                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isThai) q.questionTh else q.questionEn,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                options.forEachIndexed { idx, opt ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable(enabled = !quizSubmitted) { selectedAnswers[q.id] = idx }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        RadioButton(
                                            selected = chosen == idx,
                                            onClick = { if (!quizSubmitted) selectedAnswers[q.id] = idx }
                                        )
                                        Text(
                                            text = opt,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = when {
                                                quizSubmitted && idx == q.correctIndex -> EmeraldTertiary
                                                quizSubmitted && chosen == idx && idx != q.correctIndex -> CrimsonError
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                                if (quizSubmitted) {
                                    Text(
                                        text = if (isThai) q.explanationTh else q.explanationEn,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    item {
                        if (quizSubmitted) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isThai) "ผลคะแนน: $computedScore%" else "Score: $computedScore%",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = {
                                val total = course.quiz.size.coerceAtLeast(1)
                                val correct = course.quiz.count { q -> selectedAnswers[q.id] == q.correctIndex }
                                computedScore = (correct * 100) / total
                                quizSubmitted = true
                                onSubmitQuiz(computedScore)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("submit_quiz_button")
                        ) {
                            Text(strings.submitQuiz)
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.EmojiEvents,
                                        contentDescription = null,
                                        tint = EmeraldTertiary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = if (isThai) course.finalProjectTitleTh else course.finalProjectTitleEn,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = if (isThai) course.finalProjectBriefTh else course.finalProjectBriefEn,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (isThai) {
                                        "ไฟล์เริ่มต้นในโปรเจกต์ (${course.finalProjectFiles.size}):"
                                    } else {
                                        "Included Starter Files (${course.finalProjectFiles.size}):"
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                                course.finalProjectFiles.forEach { f ->
                                    Text(
                                        text = "• ${f.path} (${f.language.displayName})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Button(
                                    onClick = onStartFinalProject,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("start_final_project_button")
                                ) {
                                    Icon(Icons.Filled.RocketLaunch, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.startFinalProject)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeSnippetBlock(
    code: String,
    language: CodeLanguage
) {
    val transformation = remember(language) { SyntaxHighlightTransformation(language) }
    val highlighted = remember(code, language) {
        transformation.filter(androidx.compose.ui.text.AnnotatedString(code)).text
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .horizontalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text(
            text = highlighted,
            style = TextStyle(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        )
    }
}
