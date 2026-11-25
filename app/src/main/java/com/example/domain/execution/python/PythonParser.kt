package com.example.domain.execution.python

class PythonParser(private val tokens: List<PyToken>) {
    private var pos = 0

    private fun peek(offset: Int = 0): PyToken =
        tokens.getOrElse(pos + offset) { tokens.last() }

    private fun check(type: PyTokenType, lexeme: String? = null): Boolean {
        val t = peek()
        return t.type == type && (lexeme == null || t.lexeme == lexeme)
    }

    private fun advance(): PyToken {
        val current = peek()
        if (pos < tokens.lastIndex) pos++
        return current
    }

    private fun expect(type: PyTokenType, lexeme: String? = null, msg: String = "unexpected syntax"): PyToken {
        if (!check(type, lexeme)) {
            val got = peek()
            throw PythonSyntaxException("SyntaxError", "$msg (got '${got.lexeme.ifEmpty { got.type.name }}')", got.line)
        }
        return advance()
    }

    private fun skipNewlines() {
        while (check(PyTokenType.NEWLINE) || check(PyTokenType.SEMICOLON)) advance()
    }

    fun parseModule(): List<PyStmt> {
        val stmts = mutableListOf<PyStmt>()
        skipNewlines()
        while (!check(PyTokenType.EOF)) {
            stmts.addAll(parseStatement())
            skipNewlines()
        }
        return stmts
    }

    private fun parseStatement(): List<PyStmt> {
        if (check(PyTokenType.AT)) {
            val decorators = mutableListOf<PyExpr>()
            while (check(PyTokenType.AT)) {
                advance()
                decorators.add(parseNamedOrCallExpr())
                expect(PyTokenType.NEWLINE, msg = "expected newline after decorator")
                skipNewlines()
            }
            return when {
                check(PyTokenType.KEYWORD, "def") -> listOf(parseDef(decorators))
                check(PyTokenType.KEYWORD, "class") -> listOf(parseClass(decorators))
                else -> throw PythonSyntaxException("SyntaxError", "expected 'def' or 'class' after decorator", peek().line)
            }
        }

        return when {
            check(PyTokenType.KEYWORD, "if") -> listOf(parseIf())
            check(PyTokenType.KEYWORD, "while") -> listOf(parseWhile())
            check(PyTokenType.KEYWORD, "for") -> listOf(parseFor())
            check(PyTokenType.KEYWORD, "def") -> listOf(parseDef(emptyList()))
            check(PyTokenType.KEYWORD, "class") -> listOf(parseClass(emptyList()))
            check(PyTokenType.KEYWORD, "try") -> listOf(parseTry())
            else -> parseSmallStatements()
        }
    }

    private fun parseSmallStatements(): List<PyStmt> {
        val result = mutableListOf<PyStmt>()
        result.add(parseSimpleStmt())
        while (check(PyTokenType.SEMICOLON)) {
            advance()
            if (check(PyTokenType.NEWLINE) || check(PyTokenType.EOF) || check(PyTokenType.DEDENT)) break
            result.add(parseSimpleStmt())
        }
        if (check(PyTokenType.NEWLINE)) advance()
        return result
    }

    private fun parseSimpleStmt(): PyStmt {
        val line = peek().line
        return when {
            check(PyTokenType.KEYWORD, "pass") -> {
                advance()
                PyPassStmt(line)
            }
            check(PyTokenType.KEYWORD, "break") -> {
                advance()
                PyBreakStmt(line)
            }
            check(PyTokenType.KEYWORD, "continue") -> {
                advance()
                PyContinueStmt(line)
            }
            check(PyTokenType.KEYWORD, "return") -> {
                advance()
                val value = if (isEndOfSmallStmt()) null else parseTupleOrExpr()
                PyReturnStmt(value, line)
            }
            check(PyTokenType.KEYWORD, "raise") -> {
                advance()
                val expr = if (isEndOfSmallStmt()) null else parseExpr()
                PyRaiseStmt(expr, line)
            }
            check(PyTokenType.KEYWORD, "assert") -> {
                advance()
                val test = parseExpr()
                val msg = if (check(PyTokenType.COMMA)) {
                    advance()
                    parseExpr()
                } else null
                PyAssertStmt(test, msg, line)
            }
            check(PyTokenType.KEYWORD, "del") -> {
                advance()
                val targets = parseTargetList()
                PyDelStmt(targets, line)
            }
            check(PyTokenType.KEYWORD, "global") -> {
                advance()
                val names = mutableListOf<String>()
                do {
                    if (names.isNotEmpty()) expect(PyTokenType.COMMA)
                    names.add(expect(PyTokenType.IDENT, msg = "expected identifier").lexeme)
                } while (check(PyTokenType.COMMA))
                PyGlobalStmt(names, line)
            }
            check(PyTokenType.KEYWORD, "nonlocal") -> {
                advance()
                val names = mutableListOf<String>()
                do {
                    if (names.isNotEmpty()) expect(PyTokenType.COMMA)
                    names.add(expect(PyTokenType.IDENT, msg = "expected identifier").lexeme)
                } while (check(PyTokenType.COMMA))
                PyNonlocalStmt(names, line)
            }
            check(PyTokenType.KEYWORD, "import") -> parseImport(line)
            check(PyTokenType.KEYWORD, "from") -> parseFromImport(line)
            else -> {
                val lhs = parseTupleOrExpr(allowStarred = true)
                // Handle optional Python type annotation: x: int = 5
                if (check(PyTokenType.COLON) && lhs is PyIdent) {
                    advance()
                    skipAnnotationUntilAssignOrEnd()
                    if (check(PyTokenType.ASSIGN)) {
                        advance()
                        val rhs = parseTupleOrExpr()
                        return PyAssignStmt(listOf(PyTarget.Name(lhs.name)), rhs, line)
                    }
                    return PyPassStmt(line)
                }
                if (check(PyTokenType.AUG_ASSIGN)) {
                    val op = advance().lexeme
                    val rhs = parseTupleOrExpr()
                    return PyAugAssignStmt(exprToTarget(lhs), op, rhs, line)
                }
                if (check(PyTokenType.ASSIGN)) {
                    val targets = mutableListOf(exprToTarget(lhs))
                    var rhs: PyExpr? = null
                    while (check(PyTokenType.ASSIGN)) {
                        advance()
                        val next = parseTupleOrExpr(allowStarred = true)
                        if (check(PyTokenType.ASSIGN)) {
                            targets.add(exprToTarget(next))
                        } else {
                            rhs = next
                        }
                    }
                    return PyAssignStmt(targets, rhs ?: PyNoneLit(line), line)
                }
                PyExprStmt(lhs, line)
            }
        }
    }

    private fun skipAnnotationUntilAssignOrEnd() {
        var depth = 0
        while (!check(PyTokenType.EOF)) {
            val t = peek()
            if (depth == 0 && (t.type == PyTokenType.ASSIGN || t.type == PyTokenType.NEWLINE || t.type == PyTokenType.SEMICOLON)) {
                break
            }
            if (t.type in setOf(PyTokenType.LPAREN, PyTokenType.LBRACKET, PyTokenType.LBRACE)) depth++
            if (t.type in setOf(PyTokenType.RPAREN, PyTokenType.RBRACKET, PyTokenType.RBRACE) && depth > 0) depth--
            advance()
        }
    }

    private fun isEndOfSmallStmt(): Boolean =
        check(PyTokenType.NEWLINE) || check(PyTokenType.SEMICOLON) || check(PyTokenType.EOF) || check(PyTokenType.DEDENT)

    private fun parseImport(line: Int): PyImportStmt {
        advance() // import
        val names = mutableListOf<Pair<String, String?>>()
        do {
            if (names.isNotEmpty()) expect(PyTokenType.COMMA)
            val modName = buildString {
                append(expect(PyTokenType.IDENT, msg = "expected module name").lexeme)
                while (check(PyTokenType.DOT)) {
                    advance()
                    append('.').append(expect(PyTokenType.IDENT, msg = "expected submodule name").lexeme)
                }
            }
            val alias = if (check(PyTokenType.KEYWORD, "as")) {
                advance()
                expect(PyTokenType.IDENT, msg = "expected alias").lexeme
            } else null
            names.add(modName to alias)
        } while (check(PyTokenType.COMMA))
        return PyImportStmt(names, line)
    }

    private fun parseFromImport(line: Int): PyFromImportStmt {
        advance() // from
        val modName = buildString {
            append(expect(PyTokenType.IDENT, msg = "expected module name").lexeme)
            while (check(PyTokenType.DOT)) {
                advance()
                append('.').append(expect(PyTokenType.IDENT, msg = "expected submodule name").lexeme)
            }
        }
        expect(PyTokenType.KEYWORD, "import", "expected 'import'")
        val names = mutableListOf<Pair<String, String?>>()
        if (check(PyTokenType.OP, "*")) {
            advance()
            names.add("*" to null)
            return PyFromImportStmt(modName, names, line)
        }
        val hasParens = check(PyTokenType.LPAREN)
        if (hasParens) advance()
        while (true) {
            val item = expect(PyTokenType.IDENT, msg = "expected imported name").lexeme
            val alias = if (check(PyTokenType.KEYWORD, "as")) {
                advance()
                expect(PyTokenType.IDENT, msg = "expected alias").lexeme
            } else null
            names.add(item to alias)
            if (!check(PyTokenType.COMMA)) break
            advance()
            if (hasParens && check(PyTokenType.RPAREN)) break
        }
        if (hasParens) expect(PyTokenType.RPAREN, msg = "expected ')'")
        return PyFromImportStmt(modName, names, line)
    }

    private fun parseSuite(): List<PyStmt> {
        expect(PyTokenType.COLON, msg = "expected ':'")
        if (!check(PyTokenType.NEWLINE)) {
            return parseSmallStatements()
        }
        skipNewlines()
        if (!check(PyTokenType.INDENT)) {
            val bad = peek()
            throw PythonSyntaxException("IndentationError", "expected an indented block", bad.line)
        }
        advance()
        val stmts = mutableListOf<PyStmt>()
        skipNewlines()
        while (!check(PyTokenType.DEDENT) && !check(PyTokenType.EOF)) {
            stmts.addAll(parseStatement())
            skipNewlines()
        }
        if (check(PyTokenType.DEDENT)) advance()
        return stmts
    }

    private fun parseIf(): PyIfStmt {
        val line = expect(PyTokenType.KEYWORD, "if").line
        val branches = mutableListOf<Pair<PyExpr, List<PyStmt>>>()
        val firstCond = parseExpr()
        val firstBody = parseSuite()
        branches.add(firstCond to firstBody)
        while (check(PyTokenType.KEYWORD, "elif")) {
            advance()
            val c = parseExpr()
            val b = parseSuite()
            branches.add(c to b)
        }
        val elseBody = if (check(PyTokenType.KEYWORD, "else")) {
            advance()
            parseSuite()
        } else emptyList()
        return PyIfStmt(branches, elseBody, line)
    }

    private fun parseWhile(): PyWhileStmt {
        val line = expect(PyTokenType.KEYWORD, "while").line
        val cond = parseExpr()
        val body = parseSuite()
        val elseBody = if (check(PyTokenType.KEYWORD, "else")) {
            advance()
            parseSuite()
        } else emptyList()
        return PyWhileStmt(cond, body, elseBody, line)
    }

    private fun parseFor(): PyForStmt {
        val line = expect(PyTokenType.KEYWORD, "for").line
        val target = parseForTarget()
        expect(PyTokenType.KEYWORD, "in", "expected 'in'")
        val iter = parseTupleOrExpr()
        val body = parseSuite()
        val elseBody = if (check(PyTokenType.KEYWORD, "else")) {
            advance()
            parseSuite()
        } else emptyList()
        return PyForStmt(target, iter, body, elseBody, line)
    }

    private fun parseForTarget(): PyTarget {
        val items = mutableListOf<PyTarget>()
        items.add(parseSingleTarget())
        while (check(PyTokenType.COMMA)) {
            advance()
            if (check(PyTokenType.KEYWORD, "in")) break
            items.add(parseSingleTarget())
        }
        return if (items.size == 1) items.first() else PyTarget.TupleOrList(items)
    }

    private fun parseTargetList(): List<PyTarget> {
        val items = mutableListOf<PyTarget>()
        do {
            if (items.isNotEmpty()) expect(PyTokenType.COMMA)
            items.add(parseSingleTarget())
        } while (check(PyTokenType.COMMA))
        return items
    }

    private fun parseSingleTarget(): PyTarget {
        if (check(PyTokenType.OP, "*")) {
            advance()
            return PyTarget.Starred(parseSingleTarget())
        }
        if (check(PyTokenType.LPAREN)) {
            advance()
            val inner = parseTargetList()
            expect(PyTokenType.RPAREN, msg = "expected ')'")
            return PyTarget.TupleOrList(inner)
        }
        val expr = parsePostfix()
        return exprToTarget(expr)
    }

    private fun parseDef(decorators: List<PyExpr>): PyDefStmt {
        val line = expect(PyTokenType.KEYWORD, "def").line
        val name = expect(PyTokenType.IDENT, msg = "expected function name").lexeme
        expect(PyTokenType.LPAREN, msg = "expected '('")
        val params = parseParamList(PyTokenType.RPAREN)
        expect(PyTokenType.RPAREN, msg = "expected ')'")
        if (check(PyTokenType.ARROW)) {
            advance()
            skipAnnotationUntilColon()
        }
        val body = parseSuite()
        return PyDefStmt(name, params, body, decorators, line)
    }

    private fun skipAnnotationUntilColon() {
        var depth = 0
        while (!check(PyTokenType.EOF)) {
            val t = peek()
            if (depth == 0 && t.type == PyTokenType.COLON) break
            if (t.type in setOf(PyTokenType.LPAREN, PyTokenType.LBRACKET, PyTokenType.LBRACE)) depth++
            if (t.type in setOf(PyTokenType.RPAREN, PyTokenType.RBRACKET, PyTokenType.RBRACE) && depth > 0) depth--
            advance()
        }
    }

    private fun parseParamList(endToken: PyTokenType): List<PyParam> {
        val allowAnnotations = endToken != PyTokenType.COLON
        val params = mutableListOf<PyParam>()
        while (!check(endToken) && !check(PyTokenType.EOF)) {
            if (check(PyTokenType.OP, "**")) {
                advance()
                val pname = expect(PyTokenType.IDENT, msg = "expected parameter name").lexeme
                if (allowAnnotations && check(PyTokenType.COLON)) skipParamAnnotation()
                params.add(PyParam(pname, isKwVarArg = true))
            } else if (check(PyTokenType.OP, "*")) {
                advance()
                if (check(PyTokenType.IDENT)) {
                    val pname = advance().lexeme
                    if (allowAnnotations && check(PyTokenType.COLON)) skipParamAnnotation()
                    params.add(PyParam(pname, isVarArg = true))
                }
            } else if (check(PyTokenType.OP, "/")) {
                advance()
            } else {
                val pname = expect(PyTokenType.IDENT, msg = "expected parameter name").lexeme
                if (allowAnnotations && check(PyTokenType.COLON)) skipParamAnnotation()
                val defExpr = if (check(PyTokenType.ASSIGN)) {
                    advance()
                    parseExpr()
                } else null
                params.add(PyParam(pname, defaultExpr = defExpr))
            }
            if (check(PyTokenType.COMMA)) advance() else break
        }
        return params
    }

    private fun skipParamAnnotation() {
        advance() // ':'
        var depth = 0
        while (!check(PyTokenType.EOF)) {
            val t = peek()
            if (depth == 0 && t.type in setOf(PyTokenType.COMMA, PyTokenType.ASSIGN, PyTokenType.RPAREN, PyTokenType.COLON)) break
            if (t.type in setOf(PyTokenType.LPAREN, PyTokenType.LBRACKET, PyTokenType.LBRACE)) depth++
            if (t.type in setOf(PyTokenType.RPAREN, PyTokenType.RBRACKET, PyTokenType.RBRACE) && depth > 0) depth--
            advance()
        }
    }

    private fun parseClass(decorators: List<PyExpr>): PyClassStmt {
        val line = expect(PyTokenType.KEYWORD, "class").line
        val name = expect(PyTokenType.IDENT, msg = "expected class name").lexeme
        val bases = mutableListOf<PyExpr>()
        if (check(PyTokenType.LPAREN)) {
            advance()
            while (!check(PyTokenType.RPAREN) && !check(PyTokenType.EOF)) {
                bases.add(parseExpr())
                if (check(PyTokenType.COMMA)) advance() else break
            }
            expect(PyTokenType.RPAREN, msg = "expected ')'")
        }
        val body = parseSuite()
        return PyClassStmt(name, bases, body, decorators, line)
    }

    private fun parseTry(): PyTryStmt {
        val line = expect(PyTokenType.KEYWORD, "try").line
        val body = parseSuite()
        val handlers = mutableListOf<PyExceptHandler>()
        while (check(PyTokenType.KEYWORD, "except")) {
            advance()
            val excType = if (!check(PyTokenType.COLON)) parseExpr() else null
            val alias = if (check(PyTokenType.KEYWORD, "as")) {
                advance()
                expect(PyTokenType.IDENT, msg = "expected exception variable").lexeme
            } else null
            val handlerBody = parseSuite()
            handlers.add(PyExceptHandler(excType, alias, handlerBody))
        }
        val elseBody = if (check(PyTokenType.KEYWORD, "else")) {
            advance()
            parseSuite()
        } else emptyList()
        val finallyBody = if (check(PyTokenType.KEYWORD, "finally")) {
            advance()
            parseSuite()
        } else emptyList()
        return PyTryStmt(body, handlers, elseBody, finallyBody, line)
    }

    private fun parseNamedOrCallExpr(): PyExpr = parsePostfix()

    private fun parseTupleOrExpr(allowStarred: Boolean = false): PyExpr {
        val first = parseExprOrStarred(allowStarred)
        if (!check(PyTokenType.COMMA)) return first
        val items = mutableListOf(first)
        while (check(PyTokenType.COMMA)) {
            advance()
            if (isEndOfSmallStmt() || check(PyTokenType.ASSIGN) || check(PyTokenType.AUG_ASSIGN) ||
                check(PyTokenType.RPAREN) || check(PyTokenType.RBRACKET) || check(PyTokenType.KEYWORD, "in")
            ) break
            items.add(parseExprOrStarred(allowStarred))
        }
        return PyTupleExpr(items, first.line)
    }

    private fun parseExprOrStarred(allowStarred: Boolean): PyExpr {
        if (allowStarred && check(PyTokenType.OP, "*")) {
            val line = advance().line
            return PyStarredExpr(parseExpr(), line)
        }
        return parseExpr()
    }

    fun parseExpr(): PyExpr {
        if (check(PyTokenType.KEYWORD, "lambda")) {
            val line = advance().line
            val params = parseParamList(PyTokenType.COLON)
            expect(PyTokenType.COLON, msg = "expected ':' in lambda")
            val body = parseExpr()
            return PyLambdaExpr(params, body, line)
        }
        val expr = parseOr()
        if (check(PyTokenType.KEYWORD, "if")) {
            val line = advance().line
            val cond = parseOr()
            expect(PyTokenType.KEYWORD, "else", "expected 'else' in conditional expression")
            val orelse = parseExpr()
            return PyTernaryExpr(expr, cond, orelse, line)
        }
        if (expr is PyIdent && check(PyTokenType.WALRUS)) {
            val line = advance().line
            val value = parseExpr()
            return PyWalrusExpr(expr.name, value, line)
        }
        return expr
    }

    private fun parseOr(): PyExpr {
        var left = parseAnd()
        while (check(PyTokenType.KEYWORD, "or")) {
            val line = advance().line
            val right = parseAnd()
            left = PyBinaryOp(left, "or", right, line)
        }
        return left
    }

    private fun parseAnd(): PyExpr {
        var left = parseNot()
        while (check(PyTokenType.KEYWORD, "and")) {
            val line = advance().line
            val right = parseNot()
            left = PyBinaryOp(left, "and", right, line)
        }
        return left
    }

    private fun parseNot(): PyExpr {
        if (check(PyTokenType.KEYWORD, "not")) {
            val line = advance().line
            return PyUnaryOp("not", parseNot(), line)
        }
        return parseComparison()
    }

    private fun parseComparison(): PyExpr {
        val first = parseBitOr()
        val comparisons = mutableListOf<Pair<String, PyExpr>>()
        while (true) {
            val op = when {
                check(PyTokenType.OP, "==") || check(PyTokenType.OP, "!=") ||
                    check(PyTokenType.OP, "<") || check(PyTokenType.OP, "<=") ||
                    check(PyTokenType.OP, ">") || check(PyTokenType.OP, ">=") -> advance().lexeme
                check(PyTokenType.KEYWORD, "in") -> {
                    advance()
                    "in"
                }
                check(PyTokenType.KEYWORD, "not") && tokens.getOrNull(pos + 1)?.let { it.type == PyTokenType.KEYWORD && it.lexeme == "in" } == true -> {
                    advance(); advance()
                    "not in"
                }
                check(PyTokenType.KEYWORD, "is") -> {
                    advance()
                    if (check(PyTokenType.KEYWORD, "not")) {
                        advance()
                        "is not"
                    } else "is"
                }
                else -> break
            }
            comparisons.add(op to parseBitOr())
        }
        return if (comparisons.isEmpty()) first else PyCompareExpr(first, comparisons, first.line)
    }

    private fun parseBitOr(): PyExpr {
        var left = parseBitXor()
        while (check(PyTokenType.OP, "|")) {
            val line = advance().line
            left = PyBinaryOp(left, "|", parseBitXor(), line)
        }
        return left
    }

    private fun parseBitXor(): PyExpr {
        var left = parseBitAnd()
        while (check(PyTokenType.OP, "^")) {
            val line = advance().line
            left = PyBinaryOp(left, "^", parseBitAnd(), line)
        }
        return left
    }

    private fun parseBitAnd(): PyExpr {
        var left = parseShift()
        while (check(PyTokenType.OP, "&")) {
            val line = advance().line
            left = PyBinaryOp(left, "&", parseShift(), line)
        }
        return left
    }

    private fun parseShift(): PyExpr {
        var left = parseAddSub()
        while (check(PyTokenType.OP, "<<") || check(PyTokenType.OP, ">>")) {
            val t = advance()
            left = PyBinaryOp(left, t.lexeme, parseAddSub(), t.line)
        }
        return left
    }

    private fun parseAddSub(): PyExpr {
        var left = parseMulDiv()
        while (check(PyTokenType.OP, "+") || check(PyTokenType.OP, "-")) {
            val t = advance()
            left = PyBinaryOp(left, t.lexeme, parseMulDiv(), t.line)
        }
        return left
    }

    private fun parseMulDiv(): PyExpr {
        var left = parseUnary()
        while (check(PyTokenType.OP, "*") || check(PyTokenType.OP, "/") || check(PyTokenType.OP, "//") || check(PyTokenType.OP, "%")) {
            val t = advance()
            left = PyBinaryOp(left, t.lexeme, parseUnary(), t.line)
        }
        return left
    }

    private fun parseUnary(): PyExpr {
        if (check(PyTokenType.OP, "+") || check(PyTokenType.OP, "-") || check(PyTokenType.OP, "~")) {
            val t = advance()
            return PyUnaryOp(t.lexeme, parseUnary(), t.line)
        }
        return parsePower()
    }

    private fun parsePower(): PyExpr {
        val base = parsePostfix()
        if (check(PyTokenType.OP, "**")) {
            val line = advance().line
            return PyBinaryOp(base, "**", parseUnary(), line)
        }
        return base
    }

    private fun parsePostfix(): PyExpr {
        var expr = parsePrimary()
        while (true) {
            expr = when {
                check(PyTokenType.LPAREN) -> {
                    val line = advance().line
                    val args = parseCallArgs()
                    expect(PyTokenType.RPAREN, msg = "expected ')'")
                    PyCallExpr(expr, args, line)
                }
                check(PyTokenType.DOT) -> {
                    val line = advance().line
                    val attr = expect(PyTokenType.IDENT, msg = "expected attribute name").lexeme
                    PyAttrExpr(expr, attr, line)
                }
                check(PyTokenType.LBRACKET) -> {
                    val line = advance().line
                    val indexOrSlice = parseSubscriptOrSlice(line)
                    expect(PyTokenType.RBRACKET, msg = "expected ']'")
                    PySubscriptExpr(expr, indexOrSlice, line)
                }
                else -> break
            }
        }
        return expr
    }

    private fun parseSubscriptOrSlice(line: Int): PyExpr {
        var start: PyExpr? = null
        if (!check(PyTokenType.COLON)) {
            start = parseTupleOrExpr()
        }
        if (!check(PyTokenType.COLON)) {
            return start ?: PyNoneLit(line)
        }
        advance() // first ':'
        var stop: PyExpr? = null
        if (!check(PyTokenType.COLON) && !check(PyTokenType.RBRACKET)) {
            stop = parseExpr()
        }
        var step: PyExpr? = null
        if (check(PyTokenType.COLON)) {
            advance()
            if (!check(PyTokenType.RBRACKET)) {
                step = parseExpr()
            }
        }
        return PySliceExpr(start, stop, step, line)
    }

    private fun parseCallArgs(): List<PyArg> {
        val args = mutableListOf<PyArg>()
        while (!check(PyTokenType.RPAREN) && !check(PyTokenType.EOF)) {
            when {
                check(PyTokenType.OP, "**") -> {
                    advance()
                    args.add(PyArg(value = parseExpr(), isDoubleStar = true))
                }
                check(PyTokenType.OP, "*") -> {
                    advance()
                    args.add(PyArg(value = parseExpr(), isStar = true))
                }
                check(PyTokenType.IDENT) && peek(1).type == PyTokenType.ASSIGN -> {
                    val kname = advance().lexeme
                    advance()
                    args.add(PyArg(name = kname, value = parseExpr()))
                }
                else -> {
                    val argExpr = parseExpr()
                    if (check(PyTokenType.KEYWORD, "for")) {
                        val clauses = parseCompClauses()
                        args.add(PyArg(value = PyListCompExpr(argExpr, clauses, argExpr.line)))
                    } else {
                        args.add(PyArg(value = argExpr))
                    }
                }
            }
            if (check(PyTokenType.COMMA)) advance() else break
        }
        return args
    }

    private fun parsePrimary(): PyExpr {
        val t = peek()
        return when (t.type) {
            PyTokenType.NUMBER_INT -> {
                advance(); PyIntLit(t.intVal, t.line)
            }
            PyTokenType.NUMBER_FLOAT -> {
                advance(); PyFloatLit(t.floatVal, t.line)
            }
            PyTokenType.STRING -> {
                val sb = StringBuilder(advance().lexeme)
                while (check(PyTokenType.STRING)) {
                    sb.append(advance().lexeme)
                }
                PyStringLit(sb.toString(), t.line)
            }
            PyTokenType.FSTRING -> {
                advance(); PyFStringExpr(t.lexeme, t.line)
            }
            PyTokenType.KEYWORD -> when (t.lexeme) {
                "True" -> { advance(); PyBoolLit(true, t.line) }
                "False" -> { advance(); PyBoolLit(false, t.line) }
                "None" -> { advance(); PyNoneLit(t.line) }
                else -> throw PythonSyntaxException("SyntaxError", "unexpected keyword '${t.lexeme}'", t.line)
            }
            PyTokenType.IDENT -> {
                advance(); PyIdent(t.lexeme, t.line)
            }
            PyTokenType.LPAREN -> {
                advance()
                if (check(PyTokenType.RPAREN)) {
                    advance()
                    return PyTupleExpr(emptyList(), t.line)
                }
                val first = parseExprOrStarred(allowStarred = true)
                if (check(PyTokenType.KEYWORD, "for")) {
                    val clauses = parseCompClauses()
                    expect(PyTokenType.RPAREN, msg = "expected ')'")
                    return PyListCompExpr(first, clauses, t.line)
                }
                if (check(PyTokenType.COMMA)) {
                    val items = mutableListOf(first)
                    while (check(PyTokenType.COMMA)) {
                        advance()
                        if (check(PyTokenType.RPAREN)) break
                        items.add(parseExprOrStarred(allowStarred = true))
                    }
                    expect(PyTokenType.RPAREN, msg = "expected ')'")
                    return PyTupleExpr(items, t.line)
                }
                expect(PyTokenType.RPAREN, msg = "expected ')'")
                first
            }
            PyTokenType.LBRACKET -> {
                advance()
                if (check(PyTokenType.RBRACKET)) {
                    advance()
                    return PyListExpr(emptyList(), t.line)
                }
                val first = parseExprOrStarred(allowStarred = true)
                if (check(PyTokenType.KEYWORD, "for")) {
                    val clauses = parseCompClauses()
                    expect(PyTokenType.RBRACKET, msg = "expected ']'")
                    return PyListCompExpr(first, clauses, t.line)
                }
                val items = mutableListOf(first)
                while (check(PyTokenType.COMMA)) {
                    advance()
                    if (check(PyTokenType.RBRACKET)) break
                    items.add(parseExprOrStarred(allowStarred = true))
                }
                expect(PyTokenType.RBRACKET, msg = "expected ']'")
                PyListExpr(items, t.line)
            }
            PyTokenType.LBRACE -> {
                advance()
                if (check(PyTokenType.RBRACE)) {
                    advance()
                    return PyDictExpr(emptyList(), t.line)
                }
                if (check(PyTokenType.OP, "**")) {
                    val entries = mutableListOf<Pair<PyExpr?, PyExpr>>()
                    while (!check(PyTokenType.RBRACE) && !check(PyTokenType.EOF)) {
                        if (check(PyTokenType.OP, "**")) {
                            advance()
                            entries.add(null to parseExpr())
                        } else {
                            val k = parseExpr()
                            expect(PyTokenType.COLON, msg = "expected ':' in dict")
                            entries.add(k to parseExpr())
                        }
                        if (check(PyTokenType.COMMA)) advance() else break
                    }
                    expect(PyTokenType.RBRACE, msg = "expected '}'")
                    return PyDictExpr(entries, t.line)
                }
                val first = parseExpr()
                if (check(PyTokenType.COLON)) {
                    advance()
                    val firstVal = parseExpr()
                    if (check(PyTokenType.KEYWORD, "for")) {
                        val clauses = parseCompClauses()
                        expect(PyTokenType.RBRACE, msg = "expected '}'")
                        return PyDictCompExpr(first, firstVal, clauses, t.line)
                    }
                    val entries = mutableListOf<Pair<PyExpr?, PyExpr>>(first to firstVal)
                    while (check(PyTokenType.COMMA)) {
                        advance()
                        if (check(PyTokenType.RBRACE)) break
                        if (check(PyTokenType.OP, "**")) {
                            advance()
                            entries.add(null to parseExpr())
                        } else {
                            val k = parseExpr()
                            expect(PyTokenType.COLON, msg = "expected ':' in dict")
                            entries.add(k to parseExpr())
                        }
                    }
                    expect(PyTokenType.RBRACE, msg = "expected '}'")
                    PyDictExpr(entries, t.line)
                } else if (check(PyTokenType.KEYWORD, "for")) {
                    val clauses = parseCompClauses()
                    expect(PyTokenType.RBRACE, msg = "expected '}'")
                    PySetCompExpr(first, clauses, t.line)
                } else {
                    val items = mutableListOf(first)
                    while (check(PyTokenType.COMMA)) {
                        advance()
                        if (check(PyTokenType.RBRACE)) break
                        items.add(parseExpr())
                    }
                    expect(PyTokenType.RBRACE, msg = "expected '}'")
                    PySetExpr(items, t.line)
                }
            }
            else -> throw PythonSyntaxException("SyntaxError", "invalid syntax '${t.lexeme.ifEmpty { t.type.name }}'", t.line)
        }
    }

    private fun parseCompClauses(): List<PyCompClause> {
        val clauses = mutableListOf<PyCompClause>()
        while (check(PyTokenType.KEYWORD, "for")) {
            advance()
            val target = parseForTarget()
            expect(PyTokenType.KEYWORD, "in", "expected 'in'")
            val iter = parseOr()
            val conds = mutableListOf<PyExpr>()
            while (check(PyTokenType.KEYWORD, "if")) {
                advance()
                conds.add(parseOr())
            }
            clauses.add(PyCompClause(target, iter, conds))
        }
        return clauses
    }

    private fun exprToTarget(expr: PyExpr): PyTarget = when (expr) {
        is PyIdent -> PyTarget.Name(expr.name)
        is PyTupleExpr -> PyTarget.TupleOrList(expr.items.map { exprToTarget(it) })
        is PyListExpr -> PyTarget.TupleOrList(expr.items.map { exprToTarget(it) })
        is PyStarredExpr -> PyTarget.Starred(exprToTarget(expr.inner))
        is PyAttrExpr -> PyTarget.Attr(expr.obj, expr.attr)
        is PySubscriptExpr -> PyTarget.Subscript(expr.obj, expr.index)
        else -> throw PythonSyntaxException("SyntaxError", "cannot assign to expression", expr.line)
    }
}
