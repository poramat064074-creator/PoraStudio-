package com.example.domain.execution.python

enum class PyTokenType {
    NUMBER_INT,
    NUMBER_FLOAT,
    STRING,
    FSTRING,
    IDENT,
    KEYWORD,
    OP,
    LPAREN,
    RPAREN,
    LBRACKET,
    RBRACKET,
    LBRACE,
    RBRACE,
    COMMA,
    COLON,
    DOT,
    SEMICOLON,
    AT,
    ASSIGN,
    AUG_ASSIGN,
    ARROW,
    WALRUS,
    NEWLINE,
    INDENT,
    DEDENT,
    EOF
}

data class PyToken(
    val type: PyTokenType,
    val lexeme: String,
    val line: Int,
    val intVal: Long = 0L,
    val floatVal: Double = 0.0
)

class PythonSyntaxException(
    val errorType: String,
    message: String,
    val line: Int
) : RuntimeException("$errorType: $message (line $line)")

object PythonLexer {
    private val keywords = setOf(
        "False", "None", "True", "and", "as", "assert", "break", "class",
        "continue", "def", "del", "elif", "else", "except", "finally",
        "for", "from", "global", "if", "import", "in", "is", "lambda",
        "nonlocal", "not", "or", "pass", "raise", "return", "try",
        "while", "with", "yield"
    )

    fun tokenize(source: String): List<PyToken> {
        val normalized = source.replace("\r\n", "\n").replace("\r", "\n")
        val tokens = mutableListOf<PyToken>()
        val indentStack = ArrayDeque<Int>().apply { addLast(0) }

        var i = 0
        var line = 1
        var atLineStart = true
        var parenDepth = 0

        fun peek(offset: Int = 0): Char =
            if (i + offset < normalized.length) normalized[i + offset] else '\u0000'

        while (i < normalized.length) {
            if (atLineStart && parenDepth == 0) {
                var spaces = 0
                var sawSpace = false
                var sawTab = false
                while (i < normalized.length) {
                    when (normalized[i]) {
                        ' ' -> {
                            sawSpace = true
                            spaces++
                            i++
                        }
                        '\t' -> {
                            sawTab = true
                            spaces += 4
                            i++
                        }
                        else -> break
                    }
                }
                val next = peek()
                if (next == '\n') {
                    i++
                    line++
                    continue
                }
                if (next == '#') {
                    while (i < normalized.length && normalized[i] != '\n') i++
                    if (i < normalized.length && normalized[i] == '\n') {
                        i++
                        line++
                    }
                    continue
                }
                if (next == '\u0000') break
                if (sawSpace && sawTab) {
                    throw PythonSyntaxException(
                        "TabError",
                        "inconsistent use of tabs and spaces in indentation",
                        line
                    )
                }

                val currentIndent = indentStack.last()
                if (spaces > currentIndent) {
                    indentStack.addLast(spaces)
                    tokens.add(PyToken(PyTokenType.INDENT, "", line))
                } else if (spaces < currentIndent) {
                    while (indentStack.size > 1 && indentStack.last() > spaces) {
                        indentStack.removeLast()
                        tokens.add(PyToken(PyTokenType.DEDENT, "", line))
                    }
                    if (indentStack.last() != spaces) {
                        throw PythonSyntaxException(
                            "IndentationError",
                            "unindent does not match any outer indentation level",
                            line
                        )
                    }
                }
                atLineStart = false
                continue
            }

            val ch = normalized[i]
            when {
                ch == ' ' || ch == '\t' -> {
                    i++
                }
                ch == '\\' && peek(1) == '\n' -> {
                    i += 2
                    line++
                }
                ch == '\n' -> {
                    i++
                    if (parenDepth == 0) {
                        if (tokens.isNotEmpty() && tokens.last().type != PyTokenType.NEWLINE &&
                            tokens.last().type != PyTokenType.INDENT && tokens.last().type != PyTokenType.DEDENT
                        ) {
                            tokens.add(PyToken(PyTokenType.NEWLINE, "\\n", line))
                        }
                        atLineStart = true
                    }
                    line++
                }
                ch == '#' -> {
                    while (i < normalized.length && normalized[i] != '\n') i++
                }
                ch == '(' -> {
                    parenDepth++
                    tokens.add(PyToken(PyTokenType.LPAREN, "(", line))
                    i++
                }
                ch == ')' -> {
                    if (parenDepth > 0) parenDepth--
                    tokens.add(PyToken(PyTokenType.RPAREN, ")", line))
                    i++
                }
                ch == '[' -> {
                    parenDepth++
                    tokens.add(PyToken(PyTokenType.LBRACKET, "[", line))
                    i++
                }
                ch == ']' -> {
                    if (parenDepth > 0) parenDepth--
                    tokens.add(PyToken(PyTokenType.RBRACKET, "]", line))
                    i++
                }
                ch == '{' -> {
                    parenDepth++
                    tokens.add(PyToken(PyTokenType.LBRACE, "{", line))
                    i++
                }
                ch == '}' -> {
                    if (parenDepth > 0) parenDepth--
                    tokens.add(PyToken(PyTokenType.RBRACE, "}", line))
                    i++
                }
                ch == ',' -> {
                    tokens.add(PyToken(PyTokenType.COMMA, ",", line))
                    i++
                }
                ch == ':' && peek(1) == '=' -> {
                    tokens.add(PyToken(PyTokenType.WALRUS, ":=", line))
                    i += 2
                }
                ch == ':' -> {
                    tokens.add(PyToken(PyTokenType.COLON, ":", line))
                    i++
                }
                ch == '.' -> {
                    if (peek(1).isDigit()) {
                        val (numToken, nextIdx) = readNumber(normalized, i, line)
                        tokens.add(numToken)
                        i = nextIdx
                    } else {
                        tokens.add(PyToken(PyTokenType.DOT, ".", line))
                        i++
                    }
                }
                ch == ';' -> {
                    tokens.add(PyToken(PyTokenType.SEMICOLON, ";", line))
                    i++
                }
                ch == '@' -> {
                    if (peek(1) == '=') {
                        tokens.add(PyToken(PyTokenType.AUG_ASSIGN, "@=", line))
                        i += 2
                    } else {
                        tokens.add(PyToken(PyTokenType.AT, "@", line))
                        i++
                    }
                }
                (ch == 'f' || ch == 'F' || ch == 'r' || ch == 'R') && (peek(1) == '"' || peek(1) == '\'') -> {
                    val isF = ch == 'f' || ch == 'F'
                    val isRaw = ch == 'r' || ch == 'R'
                    val (strVal, nextIdx, linesConsumed) = readString(normalized, i + 1, line, isRaw)
                    tokens.add(PyToken(if (isF) PyTokenType.FSTRING else PyTokenType.STRING, strVal, line))
                    i = nextIdx
                    line += linesConsumed
                }
                ((ch == 'f' || ch == 'F') && (peek(1) == 'r' || peek(1) == 'R') && (peek(2) == '"' || peek(2) == '\'')) ||
                    ((ch == 'r' || ch == 'R') && (peek(1) == 'f' || peek(1) == 'F') && (peek(2) == '"' || peek(2) == '\'')) -> {
                    val (strVal, nextIdx, linesConsumed) = readString(normalized, i + 2, line, isRaw = true)
                    tokens.add(PyToken(PyTokenType.FSTRING, strVal, line))
                    i = nextIdx
                    line += linesConsumed
                }
                ch == '"' || ch == '\'' -> {
                    val (strVal, nextIdx, linesConsumed) = readString(normalized, i, line, isRaw = false)
                    tokens.add(PyToken(PyTokenType.STRING, strVal, line))
                    i = nextIdx
                    line += linesConsumed
                }
                ch.isDigit() -> {
                    val (numToken, nextIdx) = readNumber(normalized, i, line)
                    tokens.add(numToken)
                    i = nextIdx
                }
                ch.isLetter() || ch == '_' -> {
                    val start = i
                    while (i < normalized.length && (normalized[i].isLetterOrDigit() || normalized[i] == '_')) {
                        i++
                    }
                    val word = normalized.substring(start, i)
                    val type = if (word in keywords) PyTokenType.KEYWORD else PyTokenType.IDENT
                    tokens.add(PyToken(type, word, line))
                }
                else -> {
                    val (opToken, nextIdx) = readOperator(normalized, i, line)
                    tokens.add(opToken)
                    i = nextIdx
                }
            }
        }

        if (tokens.isNotEmpty() && tokens.last().type != PyTokenType.NEWLINE && tokens.last().type != PyTokenType.DEDENT) {
            tokens.add(PyToken(PyTokenType.NEWLINE, "\\n", line))
        }
        while (indentStack.size > 1) {
            indentStack.removeLast()
            tokens.add(PyToken(PyTokenType.DEDENT, "", line))
        }
        tokens.add(PyToken(PyTokenType.EOF, "", line))
        return tokens
    }

    private fun readString(src: String, startQuoteIdx: Int, startLine: Int, isRaw: Boolean): Triple<String, Int, Int> {
        val quote = src[startQuoteIdx]
        val isTriple = startQuoteIdx + 2 < src.length && src[startQuoteIdx + 1] == quote && src[startQuoteIdx + 2] == quote
        var idx = startQuoteIdx + if (isTriple) 3 else 1
        var linesConsumed = 0
        val sb = StringBuilder()

        while (idx < src.length) {
            val c = src[idx]
            if (isTriple) {
                if (c == quote && idx + 2 < src.length && src[idx + 1] == quote && src[idx + 2] == quote) {
                    return Triple(sb.toString(), idx + 3, linesConsumed)
                }
            } else {
                if (c == quote) {
                    return Triple(sb.toString(), idx + 1, linesConsumed)
                }
                if (c == '\n') {
                    throw PythonSyntaxException("SyntaxError", "unterminated string literal", startLine)
                }
            }

            if (c == '\n') linesConsumed++
            if (c == '\\' && !isRaw && idx + 1 < src.length) {
                val esc = src[idx + 1]
                when (esc) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    '\'' -> sb.append('\'')
                    '\n' -> linesConsumed++
                    else -> {
                        sb.append('\\').append(esc)
                    }
                }
                idx += 2
            } else {
                sb.append(c)
                idx++
            }
        }
        throw PythonSyntaxException("SyntaxError", "unterminated string literal", startLine)
    }

    private fun readNumber(src: String, startIdx: Int, line: Int): Pair<PyToken, Int> {
        var idx = startIdx
        if (src[idx] == '0' && idx + 1 < src.length && (src[idx + 1] == 'x' || src[idx + 1] == 'X')) {
            idx += 2
            val hexStart = idx
            while (idx < src.length && (src[idx].isDigit() || src[idx] in 'a'..'f' || src[idx] in 'A'..'F' || src[idx] == '_')) idx++
            val hex = src.substring(hexStart, idx).replace("_", "")
            val value = hex.toLongOrNull(16)
                ?: throw PythonSyntaxException("SyntaxError", "invalid hexadecimal literal", line)
            return PyToken(PyTokenType.NUMBER_INT, src.substring(startIdx, idx), line, intVal = value) to idx
        }
        if (src[idx] == '0' && idx + 1 < src.length && (src[idx + 1] == 'b' || src[idx + 1] == 'B')) {
            idx += 2
            val binStart = idx
            while (idx < src.length && (src[idx] == '0' || src[idx] == '1' || src[idx] == '_')) idx++
            val bin = src.substring(binStart, idx).replace("_", "")
            val value = bin.toLongOrNull(2)
                ?: throw PythonSyntaxException("SyntaxError", "invalid binary literal", line)
            return PyToken(PyTokenType.NUMBER_INT, src.substring(startIdx, idx), line, intVal = value) to idx
        }

        var isFloat = false
        while (idx < src.length && (src[idx].isDigit() || src[idx] == '_')) idx++
        if (idx < src.length && src[idx] == '.' && !(idx + 1 < src.length && src[idx + 1] == '.')) {
            isFloat = true
            idx++
            while (idx < src.length && (src[idx].isDigit() || src[idx] == '_')) idx++
        }
        if (idx < src.length && (src[idx] == 'e' || src[idx] == 'E')) {
            isFloat = true
            idx++
            if (idx < src.length && (src[idx] == '+' || src[idx] == '-')) idx++
            while (idx < src.length && (src[idx].isDigit() || src[idx] == '_')) idx++
        }

        val raw = src.substring(startIdx, idx)
        val clean = raw.replace("_", "")
        return if (isFloat) {
            val f = clean.toDoubleOrNull()
                ?: throw PythonSyntaxException("SyntaxError", "invalid float literal '$raw'", line)
            PyToken(PyTokenType.NUMBER_FLOAT, raw, line, floatVal = f) to idx
        } else {
            val l = clean.toLongOrNull()
            if (l != null) {
                PyToken(PyTokenType.NUMBER_INT, raw, line, intVal = l) to idx
            } else {
                val f = clean.toDoubleOrNull()
                    ?: throw PythonSyntaxException("SyntaxError", "invalid integer literal '$raw'", line)
                PyToken(PyTokenType.NUMBER_FLOAT, raw, line, floatVal = f) to idx
            }
        }
    }

    private fun readOperator(src: String, i: Int, line: Int): Pair<PyToken, Int> {
        val three = if (i + 2 < src.length) src.substring(i, i + 3) else ""
        if (three in setOf("**=", "//=", "<<=", ">>=")) {
            return PyToken(PyTokenType.AUG_ASSIGN, three, line) to (i + 3)
        }
        val two = if (i + 1 < src.length) src.substring(i, i + 2) else ""
        if (two in setOf("+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=")) {
            return PyToken(PyTokenType.AUG_ASSIGN, two, line) to (i + 2)
        }
        if (two == "->") {
            return PyToken(PyTokenType.ARROW, two, line) to (i + 2)
        }
        if (two in setOf("==", "!=", "<=", ">=", "**", "//", "<<", ">>")) {
            return PyToken(PyTokenType.OP, two, line) to (i + 2)
        }
        val one = src[i].toString()
        return when (one) {
            "=" -> PyToken(PyTokenType.ASSIGN, "=", line) to (i + 1)
            "+", "-", "*", "/", "%", "<", ">", "&", "|", "^", "~" -> PyToken(PyTokenType.OP, one, line) to (i + 1)
            else -> throw PythonSyntaxException("SyntaxError", "unexpected character '$one'", line)
        }
    }
}
