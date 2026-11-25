package com.example.domain.editor

import com.example.data.model.CodeLanguage
import com.example.data.model.DiagnosticIssue
import com.example.data.model.DiagnosticSeverity
import com.example.data.model.FoldableBlock
import com.example.domain.execution.python.PythonLexer
import com.example.domain.execution.python.PythonParser
import com.example.domain.execution.python.PythonSyntaxException
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

object CodeAnalyzer {

    private val htmlVoidTags = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    fun analyze(code: String, language: CodeLanguage): List<DiagnosticIssue> {
        if (code.isBlank()) return emptyList()
        val issues = mutableListOf<DiagnosticIssue>()

        when (language) {
            CodeLanguage.JSON -> {
                try {
                    val trimmed = code.trim()
                    if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
                } catch (e: Exception) {
                    issues.add(
                        DiagnosticIssue(
                            line = extractLineNumber(e.message) ?: 1,
                            messageTh = "รูปแบบ JSON ไม่ถูกต้อง: ${e.message}",
                            messageEn = "Invalid JSON: ${e.message}",
                            severity = DiagnosticSeverity.ERROR
                        )
                    )
                }
            }
            CodeLanguage.XML -> {
                try {
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(StringReader(code))
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        event = parser.next()
                    }
                } catch (e: Exception) {
                    issues.add(
                        DiagnosticIssue(
                            line = extractLineNumber(e.message) ?: 1,
                            messageTh = "XML ผิดพลาด: ${e.message}",
                            messageEn = "XML parse error: ${e.message}",
                            severity = DiagnosticSeverity.ERROR
                        )
                    )
                }
            }
            CodeLanguage.PYTHON -> {
                analyzePython(code, issues)
            }
            CodeLanguage.HTML -> {
                analyzeHtml(code, issues)
                issues.addAll(checkBracketBalance(code))
            }
            CodeLanguage.CSS -> {
                analyzeCss(code, issues)
            }
            else -> {
                issues.addAll(checkBracketBalance(code))
            }
        }
        return issues.take(25)
    }

    private fun analyzePython(code: String, issues: MutableList<DiagnosticIssue>) {
        try {
            val tokens = PythonLexer.tokenize(code)
            PythonParser(tokens).parseModule()
        } catch (e: PythonSyntaxException) {
            issues.add(
                DiagnosticIssue(
                    line = e.line,
                    messageTh = "Python ${e.errorType} (บรรทัด ${e.line}): ${e.message?.substringAfter(": ") ?: ""}",
                    messageEn = e.message ?: "Python SyntaxError",
                    severity = DiagnosticSeverity.ERROR
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun analyzeHtml(code: String, issues: MutableList<DiagnosticIssue>) {
        val tagStack = ArrayDeque<Pair<String, Int>>()
        val tagRegex = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9\-]*)([^>]*)>""")
        val lineStarts = buildLineOffsets(code)
        val withoutComments = Regex("""<!--[\s\S]*?-->""").replace(code) { m ->
            m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
        }
        for (m in tagRegex.findAll(withoutComments)) {
            val isClose = m.groupValues[1] == "/"
            val tagName = m.groupValues[2].lowercase()
            val attrs = m.groupValues[3]
            val line = offsetToLine(lineStarts, m.range.first)
            if (tagName in htmlVoidTags || attrs.trimEnd().endsWith("/")) continue
            if (!isClose) {
                tagStack.addLast(tagName to line)
            } else {
                val matchIdx = tagStack.indexOfLast { it.first == tagName }
                if (matchIdx == -1) {
                    issues.add(
                        DiagnosticIssue(
                            line = line,
                            messageTh = "พบแท็กปิด </$tagName> ที่ไม่มีแท็กเปิด (บรรทัด $line)",
                            messageEn = "Unexpected closing tag </$tagName> on line $line",
                            severity = DiagnosticSeverity.WARNING
                        )
                    )
                } else {
                    while (tagStack.size > matchIdx + 1) {
                        val unclosed = tagStack.removeLast()
                        issues.add(
                            DiagnosticIssue(
                                line = unclosed.second,
                                messageTh = "แท็ก <${unclosed.first}> (บรรทัด ${unclosed.second}) ยังไม่ถูกปิด",
                                messageEn = "Unclosed tag <${unclosed.first}> opened on line ${unclosed.second}",
                                severity = DiagnosticSeverity.WARNING
                            )
                        )
                    }
                    tagStack.removeLast()
                }
            }
        }
        tagStack.forEach { (tagName, line) ->
            issues.add(
                DiagnosticIssue(
                    line = line,
                    messageTh = "แท็ก <$tagName> (บรรทัด $line) ยังไม่ถูกปิด",
                    messageEn = "Unclosed tag <$tagName> opened on line $line",
                    severity = DiagnosticSeverity.WARNING
                )
            )
        }
    }

    private fun analyzeCss(code: String, issues: MutableList<DiagnosticIssue>) {
        issues.addAll(checkBracketBalance(code))
        var inBlock = false
        code.lines().forEachIndexed { idx, raw ->
            val t = raw.trim()
            if (t.isEmpty() || t.startsWith("/*") || t.startsWith("*")) return@forEachIndexed
            if (t.contains("{")) inBlock = true
            if (inBlock && t.contains(":") && !t.contains("{") && !t.endsWith(";") && !t.endsWith("}") && !t.endsWith(",")) {
                issues.add(
                    DiagnosticIssue(
                        line = idx + 1,
                        messageTh = "CSS Property ควรลงท้ายด้วย ';' ที่บรรทัด ${idx + 1}",
                        messageEn = "CSS declaration should end with ';' on line ${idx + 1}",
                        severity = DiagnosticSeverity.WARNING
                    )
                )
            }
            if (t.contains("}")) inBlock = false
        }
    }

    private fun checkBracketBalance(code: String): List<DiagnosticIssue> {
        val issues = mutableListOf<DiagnosticIssue>()
        val stack = ArrayDeque<Pair<Char, Int>>()
        var line = 1
        var inSingleQuote = false
        var inDoubleQuote = false
        var inBacktick = false
        var inLineComment = false
        var inBlockComment = false
        var escaped = false

        var i = 0
        while (i < code.length) {
            val ch = code[i]
            val next = if (i + 1 < code.length) code[i + 1] else '\u0000'
            if (ch == '\n') {
                line++
                inLineComment = false
                escaped = false
                i++
                continue
            }
            if (inLineComment) {
                i++
                continue
            }
            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false
                    i += 2
                    continue
                }
                i++
                continue
            }
            if (escaped) {
                escaped = false
                i++
                continue
            }
            if (ch == '\\' && (inSingleQuote || inDoubleQuote || inBacktick)) {
                escaped = true
                i++
                continue
            }
            if (!inSingleQuote && !inDoubleQuote && !inBacktick) {
                if (ch == '/' && next == '/') {
                    inLineComment = true
                    i += 2
                    continue
                }
                if (ch == '/' && next == '*') {
                    inBlockComment = true
                    i += 2
                    continue
                }
            }
            if (ch == '"' && !inSingleQuote && !inBacktick) {
                inDoubleQuote = !inDoubleQuote
                i++
                continue
            }
            if (ch == '\'' && !inDoubleQuote && !inBacktick) {
                inSingleQuote = !inSingleQuote
                i++
                continue
            }
            if (ch == '`' && !inSingleQuote && !inDoubleQuote) {
                inBacktick = !inBacktick
                i++
                continue
            }
            if (inSingleQuote || inDoubleQuote || inBacktick) {
                i++
                continue
            }

            when (ch) {
                '(', '{', '[' -> stack.addLast(ch to line)
                ')', '}', ']' -> {
                    val expectedOpen = when (ch) {
                        ')' -> '('
                        '}' -> '{'
                        else -> '['
                    }
                    if (stack.isEmpty()) {
                        issues.add(
                            DiagnosticIssue(
                                line = line,
                                messageTh = "พบวงเล็บปิด '$ch' เกินมาที่บรรทัด $line",
                                messageEn = "Unexpected closing '$ch' on line $line",
                                severity = DiagnosticSeverity.ERROR
                            )
                        )
                    } else if (stack.last().first != expectedOpen) {
                        val top = stack.removeLast()
                        issues.add(
                            DiagnosticIssue(
                                line = line,
                                messageTh = "วงเล็บ '${top.first}' (บรรทัด ${top.second}) ไม่ตรงกับ '$ch'",
                                messageEn = "Mismatched '${top.first}' (line ${top.second}) with '$ch'",
                                severity = DiagnosticSeverity.ERROR
                            )
                        )
                    } else {
                        stack.removeLast()
                    }
                }
            }
            i++
        }

        if (inBlockComment) {
            issues.add(
                DiagnosticIssue(
                    line = line,
                    messageTh = "คอมเมนต์ /* ... */ ยังไม่ถูกปิด",
                    messageEn = "Unclosed block comment '/* ... */'",
                    severity = DiagnosticSeverity.ERROR
                )
            )
        }
        if (inDoubleQuote) {
            issues.add(
                DiagnosticIssue(
                    line = line,
                    messageTh = "เครื่องหมายคำพูด \" ยังไม่ถูกปิด",
                    messageEn = "Unclosed double quote string literal",
                    severity = DiagnosticSeverity.ERROR
                )
            )
        }
        if (inBacktick) {
            issues.add(
                DiagnosticIssue(
                    line = line,
                    messageTh = "Template literal ` ยังไม่ถูกปิด",
                    messageEn = "Unclosed template string literal",
                    severity = DiagnosticSeverity.ERROR
                )
            )
        }
        stack.forEach { (openCh, openLine) ->
            issues.add(
                DiagnosticIssue(
                    line = openLine,
                    messageTh = "วงเล็บ '$openCh' ที่บรรทัด $openLine ยังไม่ถูกปิด",
                    messageEn = "Unclosed '$openCh' opened on line $openLine",
                    severity = DiagnosticSeverity.ERROR
                )
            )
        }
        return issues
    }

    fun detectFoldableBlocks(code: String, language: CodeLanguage): List<FoldableBlock> {
        val lines = code.lines()
        val blocks = mutableListOf<FoldableBlock>()
        if (language == CodeLanguage.PYTHON) {
            lines.forEachIndexed { idx, raw ->
                val trimmed = raw.trim()
                if (trimmed.startsWith("def ") || trimmed.startsWith("class ") || trimmed.startsWith("if ") || trimmed.startsWith("for ") || trimmed.startsWith("while ")) {
                    val baseIndent = raw.takeWhile { it == ' ' }.length
                    var endIdx = idx
                    for (j in idx + 1 until lines.size) {
                        val next = lines[j]
                        if (next.isBlank()) continue
                        val nextIndent = next.takeWhile { it == ' ' }.length
                        if (nextIndent <= baseIndent) break
                        endIdx = j
                    }
                    if (endIdx > idx) {
                        blocks.add(FoldableBlock(idx + 1, endIdx + 1, trimmed.substringBefore(":")))
                    }
                }
            }
        } else {
            val stack = ArrayDeque<Pair<Int, String>>()
            lines.forEachIndexed { idx, raw ->
                val trimmed = raw.trim()
                if (trimmed.contains("{")) {
                    stack.addLast((idx + 1) to trimmed.take(48))
                }
                if (trimmed.contains("}") && stack.isNotEmpty()) {
                    val (startLine, label) = stack.removeLast()
                    if (idx + 1 > startLine) {
                        blocks.add(FoldableBlock(startLine, idx + 1, label))
                    }
                }
            }
        }
        return blocks.sortedBy { it.startLine }
    }

    fun formatCode(code: String, language: CodeLanguage, tabSize: Int = 2): String {
        if (language == CodeLanguage.JSON) {
            return try {
                val trimmed = code.trim()
                if (trimmed.startsWith("[")) JSONArray(trimmed).toString(tabSize)
                else JSONObject(trimmed).toString(tabSize)
            } catch (_: Exception) {
                code
            }
        }
        if (language == CodeLanguage.PYTHON) {
            // Preserve Python indentation safely while stripping trailing whitespace
            return code.lines().joinToString("\n") { it.trimEnd() }
        }
        val indentUnit = " ".repeat(tabSize)
        var depth = 0
        val out = StringBuilder()
        code.lines().forEachIndexed { index, raw ->
            val trimmed = raw.trim()
            if (trimmed.startsWith("}") || trimmed.startsWith("]") || trimmed.startsWith(")")) {
                depth = (depth - 1).coerceAtLeast(0)
            }
            if (trimmed.isNotEmpty()) {
                out.append(indentUnit.repeat(depth)).append(trimmed)
            }
            if (index < code.lines().lastIndex) out.append('\n')
            if (trimmed.endsWith("{") || trimmed.endsWith("[")) {
                depth++
            }
        }
        return out.toString()
    }

    fun autocompleteSuggestions(code: String, cursorIndex: Int, language: CodeLanguage): List<String> {
        val safeCursor = cursorIndex.coerceIn(0, code.length)
        val prefix = code.substring(0, safeCursor)
            .takeLastWhile { it.isLetterOrDigit() || it == '_' || it == '.' || it == '#' }
        if (prefix.isBlank()) return language.keywords.take(8)
        val identifierMatches = Regex("""\b[a-zA-Z_][a-zA-Z0-9_]{2,}\b""")
            .findAll(code)
            .map { it.value }
            .distinct()
            .toList()
        val combined = (language.keywords + identifierMatches).distinct()
        return combined.filter {
            it.startsWith(prefix, ignoreCase = true) && !it.equals(prefix, ignoreCase = false)
        }.take(8)
    }

    fun applyAutoIndentOnEnter(previousText: String, newText: String, cursorPos: Int, tabSize: Int): Pair<String, Int>? {
        if (newText.length != previousText.length + 1) return null
        val insertedAt = (cursorPos - 1).coerceIn(0, newText.lastIndex)
        if (newText[insertedAt] != '\n') return null
        val beforeNewline = newText.substring(0, insertedAt)
        val afterNewline = newText.substring(insertedAt + 1)
        val currentLine = beforeNewline.substringAfterLast('\n')
        val baseIndent = currentLine.takeWhile { it == ' ' || it == '\t' }
        val trimmedLine = currentLine.trimEnd()
        val indentStep = " ".repeat(tabSize)
        val opensBlock = trimmedLine.endsWith("{") || trimmedLine.endsWith(":") || trimmedLine.endsWith("(") || trimmedLine.endsWith("[")

        // Split `{|}` cleanly across 3 lines
        val lastChar = trimmedLine.lastOrNull()
        val nextNonEmptyChar = afterNewline.firstOrNull()
        val isEmptyBracePair = (lastChar == '{' && nextNonEmptyChar == '}') ||
            (lastChar == '(' && nextNonEmptyChar == ')') ||
            (lastChar == '[' && nextNonEmptyChar == ']')
        if (isEmptyBracePair) {
            val innerIndent = baseIndent + indentStep
            val updated = beforeNewline + "\n" + innerIndent + "\n" + baseIndent + afterNewline
            return updated to (insertedAt + 1 + innerIndent.length)
        }

        val insertion = if (opensBlock) baseIndent + indentStep else baseIndent
        if (insertion.isEmpty()) return null
        val updated = beforeNewline + "\n" + insertion + afterNewline
        return updated to (insertedAt + 1 + insertion.length)
    }

    private fun buildLineOffsets(text: String): List<Int> {
        val starts = mutableListOf(0)
        text.forEachIndexed { idx, ch -> if (ch == '\n') starts.add(idx + 1) }
        return starts
    }

    private fun offsetToLine(starts: List<Int>, offset: Int): Int {
        var low = 0
        var high = starts.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (starts[mid] <= offset) low = mid + 1 else high = mid - 1
        }
        return (high + 1).coerceAtLeast(1)
    }

    private fun extractLineNumber(msg: String?): Int? {
        if (msg == null) return null
        val match = Regex("""line\s+(\d+)""", RegexOption.IGNORE_CASE).find(msg)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
