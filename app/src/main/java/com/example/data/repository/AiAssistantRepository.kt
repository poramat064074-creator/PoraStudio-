package com.example.data.repository

import android.util.Log
import com.example.core.i18n.AppLanguage
import com.example.data.model.CodeLanguage
import com.example.data.model.TemplateFileSpec
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

enum class AiExplanationMode(val labelTh: String, val labelEn: String, val systemInstruction: String) {
    BEGINNER(
        "เริ่มต้น",
        "Beginner",
        "Explain clearly for an absolute beginner using friendly analogies, simple step-by-step breakdowns, and avoid heavy jargon."
    ),
    INTERMEDIATE(
        "ระดับกลาง",
        "Intermediate",
        "Explain clearly for an intermediate developer with practical patterns, best practices, and trade-offs."
    ),
    EXPERT(
        "ผู้เชี่ยวชาญ",
        "Expert",
        "Respond at a senior/staff software engineer depth focusing on complexity, memory, concurrency, edge cases, and security hardening."
    )
}

enum class AiQuickAction(val labelTh: String, val labelEn: String, val promptTemplate: String) {
    EXPLAIN_LINES("อธิบายทีละบรรทัด", "Explain Line by Line", "Explain the following code line by line clearly:"),
    GENERATE_CODE("สร้างโค้ด", "Generate Code", "Write clean, production-ready code for:"),
    FIND_FIX_BUGS("หาและแก้บั๊ก", "Find & Fix Bugs", "Find bugs, edge cases, and provide corrected code for:"),
    REFACTOR("รีแฟกเตอร์", "Refactor", "Refactor this code for readability, modularity, and maintainability:"),
    OPTIMIZE("เพิ่มประสิทธิภาพ", "Optimize", "Analyze time/space complexity and optimize performance for:"),
    CONVERT_LANG("แปลงภาษา", "Convert Language", "Convert this code cleanly into idiomatic target language (ask or infer best target if unspecified):"),
    GENERATE_DOCS("สร้างเอกสาร", "Generate Docs", "Write concise documentation, docstrings, and usage examples for:"),
    SUGGEST_IMPROVE("แนะนำการปรับปรุง", "Improvements", "Review this code and suggest concrete architectural and quality improvements:"),
    EXPLAIN_ERROR("อธิบาย Error", "Explain Error", "Explain why this error occurs and exact steps to fix it:"),
    TEACH_CONCEPT("สอนคอนเซปต์", "Teach Concept", "Teach this programming concept with real examples and common pitfalls:"),
    SECURITY_REVIEW("ตรวจความปลอดภัย", "Security Audit", "Audit this code for security vulnerabilities, injection risks, and data validation issues:")
}

data class GeneratedProjectPlan(
    val projectName: String,
    val description: String,
    val primaryLanguage: CodeLanguage,
    val files: List<TemplateFileSpec>
)

class AiAssistantRepository {

    private val chatModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val projectStructureSchema = Schema.obj(
        properties = mapOf(
            "projectName" to Schema.string(description = "Short descriptive project name"),
            "description" to Schema.string(description = "Brief project description"),
            "primaryLanguage" to Schema.string(description = "Primary language id such as html, javascript, python, kotlin, java, sql, typescript"),
            "files" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "path" to Schema.string(description = "Relative file path e.g. src/main.py or index.html"),
                        "language" to Schema.string(description = "Language id"),
                        "content" to Schema.string(description = "Complete working starter file content")
                    )
                ),
                description = "List of starter project files"
            )
        )
    )

    private val structuredProjectModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = projectStructureSchema
            }
        )
    }

    fun streamPrompt(
        userPrompt: String,
        mode: AiExplanationMode,
        appLanguage: AppLanguage,
        projectContext: String?
    ): Flow<String> = flow {
        val langDirective = if (appLanguage == AppLanguage.THAI) {
            "Respond primarily in clear, natural Thai (with English technical terms and code blocks)."
        } else {
            "Respond in clear, concise English."
        }
        val contextSection = if (!projectContext.isNullOrBlank()) {
            "\n\n[Active Project Context Explicitly Shared by User]\n$projectContext"
        } else {
            ""
        }
        val fullPrompt = buildString {
            appendLine("You are PoraStudio AI, a senior software engineer & programming mentor.")
            appendLine("Depth Mode: ${mode.labelEn}. ${mode.systemInstruction}")
            appendLine(langDirective)
            appendLine("When providing code, always fence it with ```language and ```.")
            append(contextSection)
            appendLine("\n\nUser Request:\n$userPrompt")
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                chatModel.generateContentStream(fullPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Gemini returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI stream timed out", e)
            throw IllegalStateException("AI request timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI stream failed", e)
            throw e
        }
    }

    suspend fun generateProjectStructure(
        ideaPrompt: String,
        appLanguage: AppLanguage
    ): Result<GeneratedProjectPlan> {
        return try {
            val langHint = if (appLanguage == AppLanguage.THAI) {
                "Use Thai or bilingual comments/copy where appropriate inside starter files."
            } else {
                "Use English comments and copy inside starter files."
            }
            val prompt = """
                Design a clean, realistic multi-file starter software project for: "$ideaPrompt".
                $langHint
                Include 2 to 5 complete, well-structured files with real working code (no placeholders or TODO-only stubs).
            """.trimIndent()

            val response = withTimeout(60_000L) {
                structuredProjectModel.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("Empty structured response from Gemini.")
            val root = JSONObject(jsonText)
            val name = root.optString("projectName", "AI Generated Project")
            val desc = root.optString("description", ideaPrompt.take(160))
            val primaryLang = CodeLanguage.fromId(root.optString("primaryLanguage", "javascript"))
            val filesJson = root.getJSONArray("files")
            val specs = (0 until filesJson.length()).map { idx ->
                val obj = filesJson.getJSONObject(idx)
                val path = obj.optString("path", "file_$idx.txt").trimStart('/')
                val lang = CodeLanguage.fromId(obj.optString("language", primaryLang.id))
                val content = obj.optString("content", "")
                TemplateFileSpec(path = path, language = lang, content = content)
            }
            Result.success(
                GeneratedProjectPlan(
                    projectName = name,
                    description = desc,
                    primaryLanguage = primaryLang,
                    files = specs
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Structured project generation timed out", e)
            Result.failure(IllegalStateException("Project generation timed out after 60s.", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Structured project generation failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "AiAssistantRepo"
    }
}
