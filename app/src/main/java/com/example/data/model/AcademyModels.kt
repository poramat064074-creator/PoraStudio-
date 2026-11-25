package com.example.data.model

enum class SkillLevel(val labelTh: String, val labelEn: String) {
    BEGINNER("เริ่มต้น", "Beginner"),
    INTERMEDIATE("ระดับกลาง", "Intermediate"),
    ADVANCED("ระดับสูง", "Advanced")
}

data class Lesson(
    val id: String,
    val level: SkillLevel,
    val titleTh: String,
    val titleEn: String,
    val explanationTh: String,
    val explanationEn: String,
    val codeExample: String,
    val language: CodeLanguage
)

data class CodingChallenge(
    val id: String,
    val titleTh: String,
    val titleEn: String,
    val promptTh: String,
    val promptEn: String,
    val starterCode: String,
    val expectedSubstring: String,
    val expectedKeywords: List<String>,
    val language: CodeLanguage
)

data class QuizQuestion(
    val id: String,
    val questionTh: String,
    val questionEn: String,
    val optionsTh: List<String>,
    val optionsEn: List<String>,
    val correctIndex: Int,
    val explanationTh: String,
    val explanationEn: String
)

data class Course(
    val id: String,
    val titleTh: String,
    val titleEn: String,
    val summaryTh: String,
    val summaryEn: String,
    val primaryLanguage: CodeLanguage,
    val lessons: List<Lesson>,
    val challenges: List<CodingChallenge>,
    val quiz: List<QuizQuestion>,
    val finalProjectTitleTh: String,
    val finalProjectTitleEn: String,
    val finalProjectBriefTh: String,
    val finalProjectBriefEn: String,
    val finalProjectFiles: List<TemplateFileSpec>
)
