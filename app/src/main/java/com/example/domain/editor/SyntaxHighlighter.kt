package com.example.domain.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.data.model.CodeLanguage
import com.example.ui.theme.SyntaxColors

class SyntaxHighlightTransformation(
    private val language: CodeLanguage
) : VisualTransformation {
    private var lastRaw: String? = null
    private var lastTransformed: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw == lastRaw && lastTransformed != null) {
            return lastTransformed!!
        }
        val highlighted = SyntaxHighlighter.highlight(raw, language)
        val result = TransformedText(highlighted, OffsetMapping.Identity)
        lastRaw = raw
        lastTransformed = result
        return result
    }
}

object SyntaxHighlighter {

    private val pythonKeywords = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break",
        "class", "continue", "def", "del", "elif", "else", "except", "finally",
        "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
        "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
    )

    private val jsTsKeywords = setOf(
        "async", "await", "break", "case", "catch", "class", "const", "continue",
        "debugger", "default", "delete", "do", "else", "enum", "export", "extends",
        "false", "finally", "for", "from", "function", "if", "implements", "import",
        "in", "instanceof", "interface", "let", "new", "null", "of", "package",
        "private", "protected", "public", "return", "static", "super", "switch",
        "this", "throw", "true", "try", "type", "typeof", "undefined", "var",
        "void", "while", "with", "yield"
    )

    private val cLikeKeywords = setOf(
        "abstract", "as", "break", "by", "case", "catch", "class", "companion",
        "const", "continue", "data", "default", "do", "else", "enum", "extends",
        "false", "final", "finally", "for", "fun", "if", "implements", "import",
        "in", "init", "interface", "internal", "is", "lateinit", "namespace",
        "new", "null", "object", "open", "operator", "override", "package",
        "private", "protected", "public", "return", "sealed", "static", "struct",
        "super", "suspend", "switch", "this", "throw", "throws", "true", "try",
        "using", "val", "var", "void", "when", "while"
    )

    private val sqlKeywords = setOf(
        "select", "from", "where", "insert", "into", "values", "create", "table",
        "update", "set", "delete", "drop", "alter", "join", "inner", "left",
        "right", "on", "order", "by", "group", "having", "limit", "offset",
        "primary", "key", "not", "null", "and", "or", "as", "distinct", "exists",
        "if", "autoincrement", "default", "unique", "index", "pragma", "with"
    )

    private val bashKeywords = setOf(
        "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while",
        "case", "esac", "function", "return", "export", "local", "echo", "exit"
    )

    private val typeAndBuiltinTokens = setOf(
        "int", "float", "str", "bool", "list", "dict", "set", "tuple", "range", "len",
        "print", "input", "self", "super", "Exception", "ValueError", "TypeError",
        "Int", "Long", "Double", "Float", "Boolean", "String", "Unit", "Any", "List", "Map",
        "Promise", "Array", "JSON", "Math", "Date", "RegExp", "console", "document", "window",
        "INTEGER", "TEXT", "REAL", "BLOB", "VARCHAR", "System", "Console"
    )

    private val commentRegex = Regex("""(//[^\n]*|#[^\n]*|--[^\n]*|/\*[\s\S]*?\*/|<!--[\s\S]*?-->)""")
    private val tripleStringRegex = Regex("""([fFrR]{0,2}""" + "\"\"\"" + """[\s\S]*?""" + "\"\"\"" + """|[fFrR]{0,2}'''[\s\S]*?''')""")
    private val stringRegex = Regex("""([fFrR]{0,2}"([^"\\]|\\.)*"|[fFrR]{0,2}'([^'\\]|\\.)*'|`([^`\\]|\\.)*`)""")
    private val tagRegex = Regex("""(</?[a-zA-Z0-9:\-]+|/?\s*>)""")
    private val htmlAttrRegex = Regex("""\b[a-zA-Z\-]+(?=\s*=)""")
    private val cssPropertyRegex = Regex("""[a-zA-Z\-]+(?=\s*:)""")
    private val cssHexRegex = Regex("""#[0-9a-fA-F]{3,8}\b""")
    private val annotationRegex = Regex("""@[a-zA-Z_][a-zA-Z0-9_]*""")
    private val numberRegex = Regex("""\b\d+(\.\d+)?\b""")
    private val wordRegex = Regex("""\b[a-zA-Z_][a-zA-Z0-9_]*\b""")

    fun highlight(code: String, language: CodeLanguage): AnnotatedString {
        if (code.isEmpty()) return AnnotatedString("")
        if (code.length > 35_000) {
            return buildAnnotatedString {
                append(code)
                addStyle(SpanStyle(color = SyntaxColors.PlainText), 0, code.length)
            }
        }

        val activeKeywords = when (language) {
            CodeLanguage.PYTHON -> pythonKeywords
            CodeLanguage.JAVASCRIPT, CodeLanguage.TYPESCRIPT -> jsTsKeywords
            CodeLanguage.SQL -> sqlKeywords
            CodeLanguage.BASH -> bashKeywords
            CodeLanguage.HTML, CodeLanguage.CSS, CodeLanguage.JSON, CodeLanguage.XML -> emptySet()
            else -> cLikeKeywords
        }

        return buildAnnotatedString {
            append(code)
            addStyle(SpanStyle(color = SyntaxColors.PlainText), 0, code.length)

            val occupied = BooleanArray(code.length)

            fun applyIfFree(range: IntRange, style: SpanStyle) {
                val start = range.first.coerceAtLeast(0)
                val end = (range.last + 1).coerceAtMost(code.length)
                if (start >= end) return
                for (i in start until end) {
                    if (occupied[i]) return
                }
                addStyle(style, start, end)
                for (i in start until end) occupied[i] = true
            }

            // 1. Comments
            commentRegex.findAll(code).forEach { match ->
                val text = match.value
                val isApplicable = when {
                    text.startsWith("#") -> language in listOf(
                        CodeLanguage.PYTHON, CodeLanguage.BASH, CodeLanguage.C, CodeLanguage.CPP, CodeLanguage.PHP
                    )
                    text.startsWith("--") -> language == CodeLanguage.SQL
                    text.startsWith("<!--") -> language in listOf(CodeLanguage.HTML, CodeLanguage.XML)
                    else -> language != CodeLanguage.PYTHON && language != CodeLanguage.SQL
                }
                if (isApplicable) {
                    applyIfFree(match.range, SpanStyle(color = SyntaxColors.Comment, fontStyle = FontStyle.Italic))
                }
            }

            // 2. Triple-quoted strings (Python)
            if (language == CodeLanguage.PYTHON) {
                tripleStringRegex.findAll(code).forEach { match ->
                    applyIfFree(match.range, SpanStyle(color = SyntaxColors.StringLiteral))
                }
            }

            // 3. String literals
            stringRegex.findAll(code).forEach { match ->
                applyIfFree(match.range, SpanStyle(color = SyntaxColors.StringLiteral))
            }

            // 4. HTML / XML tags & attributes
            if (language == CodeLanguage.HTML || language == CodeLanguage.XML) {
                tagRegex.findAll(code).forEach { match ->
                    applyIfFree(match.range, SpanStyle(color = SyntaxColors.TagOrAttr, fontWeight = FontWeight.SemiBold))
                }
                htmlAttrRegex.findAll(code).forEach { match ->
                    applyIfFree(match.range, SpanStyle(color = SyntaxColors.Annotation))
                }
            }

            // 5. CSS properties & hex colors
            if (language == CodeLanguage.CSS) {
                cssHexRegex.findAll(code).forEach { match ->
                    applyIfFree(match.range, SpanStyle(color = SyntaxColors.NumberLiteral))
                }
                cssPropertyRegex.findAll(code).forEach { match ->
                    applyIfFree(match.range, SpanStyle(color = SyntaxColors.TypeOrClass))
                }
            }

            // 6. Decorators / Annotations
            annotationRegex.findAll(code).forEach { match ->
                applyIfFree(match.range, SpanStyle(color = SyntaxColors.Annotation))
            }

            // 7. Keywords, Built-in Types, Function calls
            wordRegex.findAll(code).forEach { match ->
                val word = match.value
                val lower = word.lowercase()
                when {
                    word in activeKeywords || (language == CodeLanguage.SQL && lower in sqlKeywords) -> {
                        applyIfFree(match.range, SpanStyle(color = SyntaxColors.Keyword, fontWeight = FontWeight.SemiBold))
                    }
                    word in typeAndBuiltinTokens -> {
                        applyIfFree(match.range, SpanStyle(color = SyntaxColors.TypeOrClass))
                    }
                    match.range.last + 1 < code.length && code[match.range.last + 1] == '(' -> {
                        applyIfFree(match.range, SpanStyle(color = SyntaxColors.FunctionCall))
                    }
                }
            }

            // 8. Numbers
            numberRegex.findAll(code).forEach { match ->
                applyIfFree(match.range, SpanStyle(color = SyntaxColors.NumberLiteral))
            }
        }
    }
}
