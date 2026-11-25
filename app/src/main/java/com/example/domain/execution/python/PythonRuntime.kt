package com.example.domain.execution.python

import com.example.data.local.LocalFileEntity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.PI
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

data class PythonExecutionOutcome(
    val isSuccess: Boolean,
    val stdout: String,
    val stderr: String
)

class PythonRuntime(
    private val timeoutMs: Long,
    stdinInput: String,
    private val workspaceFiles: List<LocalFileEntity> = emptyList(),
    private val coroutineContext: CoroutineContext = EmptyCoroutineContext
) {
    private val startMs = System.currentTimeMillis()
    private val deadlineMs = startMs + timeoutMs.coerceIn(500L, 30_000L)
    private val stdoutBuilder = StringBuilder()
    private val stdinLines = stdinInput.lines()
    private var stdinIdx = 0

    private var steps = 0L
    private val maxSteps = 2_000_000L
    private var recursionDepth = 0
    private val maxRecursionDepth = 180
    private val maxOutputChars = 250_000
    private val maxCollectionItems = 50_000
    private val frameStack = mutableListOf<String>()

    private val globalScope = PyScope()
    private val loadedModules = mutableMapOf<String, PyModule>()

    init {
        installBuiltins()
    }

    fun execute(source: String, fileName: String = "main.py"): PythonExecutionOutcome {
        return try {
            frameStack.add("File \"$fileName\", in <module>")
            val tokens = PythonLexer.tokenize(source)
            val stmts = PythonParser(tokens).parseModule()
            execBlock(stmts, globalScope)
            PythonExecutionOutcome(
                isSuccess = true,
                stdout = stdoutBuilder.toString().trimEnd('\n'),
                stderr = ""
            )
        } catch (e: PythonSyntaxException) {
            PythonExecutionOutcome(
                isSuccess = false,
                stdout = stdoutBuilder.toString().trimEnd('\n'),
                stderr = "  File \"$fileName\", line ${e.line}\n${e.message}"
            )
        } catch (e: ControlSignal.Raise) {
            val tb = formatTraceback(fileName, e.line, e.excType, e.message)
            PythonExecutionOutcome(
                isSuccess = false,
                stdout = stdoutBuilder.toString().trimEnd('\n'),
                stderr = tb
            )
        } catch (e: PythonRuntimeException) {
            val tb = formatTraceback(fileName, e.line, e.errorType, e.detail)
            PythonExecutionOutcome(
                isSuccess = false,
                stdout = stdoutBuilder.toString().trimEnd('\n'),
                stderr = tb
            )
        } catch (e: Exception) {
            PythonExecutionOutcome(
                isSuccess = false,
                stdout = stdoutBuilder.toString().trimEnd('\n'),
                stderr = "RuntimeError: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun formatTraceback(fileName: String, line: Int, excType: String, detail: String): String = buildString {
        appendLine("Traceback (most recent call last):")
        if (frameStack.isEmpty()) {
            appendLine("  File \"$fileName\", line $line, in <module>")
        } else {
            frameStack.forEachIndexed { idx, frame ->
                if (idx == frameStack.lastIndex) {
                    appendLine("  $frame, line $line")
                } else {
                    appendLine("  $frame")
                }
            }
        }
        append("$excType: $detail")
    }

    private fun checkLimits(line: Int) {
        coroutineContext.ensureActive()
        steps++
        if (steps > maxSteps) {
            throw PythonRuntimeException("TimeoutError", "Execution step limit ($maxSteps steps) exceeded", line)
        }
        if ((steps and 0xFFL) == 0L || steps < 10) {
            if (System.currentTimeMillis() > deadlineMs) {
                throw PythonRuntimeException("TimeoutError", "Execution timed out after ${timeoutMs}ms", line)
            }
        }
    }

    private fun appendOutput(text: String, line: Int) {
        if (stdoutBuilder.length + text.length > maxOutputChars) {
            throw PythonRuntimeException("MemoryError", "Stdout buffer limit ($maxOutputChars chars) exceeded", line)
        }
        stdoutBuilder.append(text)
    }

    private fun execBlock(stmts: List<PyStmt>, scope: PyScope) {
        for (stmt in stmts) {
            execStmt(stmt, scope)
        }
    }

    private fun execStmt(stmt: PyStmt, scope: PyScope) {
        checkLimits(stmt.line)
        when (stmt) {
            is PyPassStmt -> Unit
            is PyBreakStmt -> throw ControlSignal.Break
            is PyContinueStmt -> throw ControlSignal.Continue
            is PyReturnStmt -> {
                val value = stmt.value?.let { evalExpr(it, scope) } ?: PyNone
                throw ControlSignal.Return(value)
            }
            is PyExprStmt -> evalExpr(stmt.expr, scope)
            is PyGlobalStmt -> scope.globals.addAll(stmt.names)
            is PyNonlocalStmt -> scope.nonlocals.addAll(stmt.names)
            is PyAssignStmt -> {
                val rhs = evalExpr(stmt.value, scope)
                for (target in stmt.targets) {
                    assignTarget(target, rhs, scope, stmt.line)
                }
            }
            is PyAugAssignStmt -> {
                val current = readTarget(stmt.target, scope, stmt.line)
                val rhs = evalExpr(stmt.value, scope)
                val binOp = stmt.op.removeSuffix("=")
                val updated = applyBinaryOp(current, binOp, rhs, stmt.line)
                assignTarget(stmt.target, updated, scope, stmt.line)
            }
            is PyDelStmt -> {
                for (t in stmt.targets) {
                    when (t) {
                        is PyTarget.Name -> if (!scope.delete(t.id)) {
                            throw PythonRuntimeException("NameError", "name '${t.id}' is not defined", stmt.line)
                        }
                        is PyTarget.Subscript -> {
                            val obj = evalExpr(t.obj, scope)
                            val idx = evalExpr(t.index, scope)
                            when (obj) {
                                is PyList -> {
                                    val i = resolveIndex((idx as? PyInt)?.value?.toInt() ?: throw PythonRuntimeException("TypeError", "list indices must be integers", stmt.line), obj.items.size, stmt.line)
                                    obj.items.removeAt(i)
                                }
                                is PyDict -> {
                                    if (obj.entries.remove(idx) == null) throw PythonRuntimeException("KeyError", idx.pyRepr(), stmt.line)
                                }
                                else -> throw PythonRuntimeException("TypeError", "object does not support item deletion", stmt.line)
                            }
                        }
                        else -> throw PythonRuntimeException("SyntaxError", "cannot delete expression", stmt.line)
                    }
                }
            }
            is PyIfStmt -> {
                var matched = false
                for ((cond, body) in stmt.branches) {
                    if (evalExpr(cond, scope).asBool()) {
                        execBlock(body, scope)
                        matched = true
                        break
                    }
                }
                if (!matched && stmt.elseBody.isNotEmpty()) {
                    execBlock(stmt.elseBody, scope)
                }
            }
            is PyWhileStmt -> {
                var broke = false
                while (evalExpr(stmt.cond, scope).asBool()) {
                    checkLimits(stmt.line)
                    try {
                        execBlock(stmt.body, scope)
                    } catch (_: ControlSignal.Continue) {
                        continue
                    } catch (_: ControlSignal.Break) {
                        broke = true
                        break
                    }
                }
                if (!broke && stmt.elseBody.isNotEmpty()) {
                    execBlock(stmt.elseBody, scope)
                }
            }
            is PyForStmt -> {
                val iterVal = evalExpr(stmt.iter, scope)
                val items = toIterableSequence(iterVal, stmt.line)
                var broke = false
                for (item in items) {
                    checkLimits(stmt.line)
                    assignTarget(stmt.target, item, scope, stmt.line)
                    try {
                        execBlock(stmt.body, scope)
                    } catch (_: ControlSignal.Continue) {
                        continue
                    } catch (_: ControlSignal.Break) {
                        broke = true
                        break
                    }
                }
                if (!broke && stmt.elseBody.isNotEmpty()) {
                    execBlock(stmt.elseBody, scope)
                }
            }
            is PyDefStmt -> {
                val defaults = stmt.params.map { p -> p.defaultExpr?.let { evalExpr(it, scope) } }
                var fnVal: PyVal = PyUserFunc(stmt.name, stmt.params, stmt.body, scope, defaults)
                for (dec in stmt.decorators.asReversed()) {
                    val decVal = evalExpr(dec, scope)
                    if (decVal is PyCallable) {
                        fnVal = decVal.call(this, listOf(fnVal), emptyMap(), stmt.line)
                    }
                }
                scope.set(stmt.name, fnVal)
            }
            is PyClassStmt -> {
                val baseClasses = stmt.bases.map { b ->
                    evalExpr(b, scope) as? PyClass
                        ?: throw PythonRuntimeException("TypeError", "base must be a class", stmt.line)
                }
                val classScope = PyScope(scope)
                execBlock(stmt.body, classScope)
                var clsVal: PyVal = PyClass(stmt.name, baseClasses, classScope.vars)
                for (dec in stmt.decorators.asReversed()) {
                    val decVal = evalExpr(dec, scope)
                    if (decVal is PyCallable) {
                        clsVal = decVal.call(this, listOf(clsVal), emptyMap(), stmt.line)
                    }
                }
                scope.set(stmt.name, clsVal)
            }
            is PyRaiseStmt -> {
                if (stmt.expr == null) {
                    throw ControlSignal.Raise("RuntimeError", "No active exception to reraise", stmt.line)
                }
                val v = evalExpr(stmt.expr, scope)
                when (v) {
                    is PyInstance -> throw ControlSignal.Raise(v.klass.name, v.fields["message"]?.pyStr() ?: "", stmt.line)
                    is PyClass -> throw ControlSignal.Raise(v.name, "", stmt.line)
                    else -> throw ControlSignal.Raise("Exception", v.pyStr(), stmt.line)
                }
            }
            is PyAssertStmt -> {
                if (!evalExpr(stmt.test, scope).asBool()) {
                    val msg = stmt.msg?.let { evalExpr(it, scope).pyStr() } ?: "assertion failed"
                    throw ControlSignal.Raise("AssertionError", msg, stmt.line)
                }
            }
            is PyTryStmt -> execTry(stmt, scope)
            is PyImportStmt -> {
                for ((modName, alias) in stmt.names) {
                    val mod = resolveModule(modName, stmt.line)
                    scope.set(alias ?: modName.substringBefore('.'), mod)
                }
            }
            is PyFromImportStmt -> {
                val mod = resolveModule(stmt.module, stmt.line)
                for ((itemName, alias) in stmt.names) {
                    if (itemName == "*") {
                        mod.attrs.forEach { (k, v) -> if (!k.startsWith("_")) scope.set(k, v) }
                    } else {
                        val v = mod.attrs[itemName]
                            ?: throw PythonRuntimeException("ImportError", "cannot import name '$itemName' from '${stmt.module}'", stmt.line)
                        scope.set(alias ?: itemName, v)
                    }
                }
            }
        }
    }

    private fun execTry(stmt: PyTryStmt, scope: PyScope) {
        var raised: ControlSignal.Raise? = null
        try {
            execBlock(stmt.body, scope)
        } catch (r: ControlSignal.Raise) {
            raised = r
        } catch (re: PythonRuntimeException) {
            if (re.errorType == "TimeoutError" || re.errorType == "MemoryError") throw re
            raised = ControlSignal.Raise(re.errorType, re.detail, re.line)
        } finally {
            if (raised == null) {
                // Only run elseBody if no exception and no return/break/continue; handled below
            }
        }

        try {
            if (raised != null) {
                var handled = false
                for (h in stmt.handlers) {
                    val matches = if (h.excType == null) {
                        true
                    } else {
                        val excVal = evalExpr(h.excType, scope)
                        matchesException(raised.excType, excVal)
                    }
                    if (matches) {
                        handled = true
                        if (h.alias != null) {
                            scope.set(h.alias, PyStr("${raised.excType}: ${raised.message}"))
                        }
                        execBlock(h.body, scope)
                        break
                    }
                }
                if (!handled) throw raised
            } else if (stmt.elseBody.isNotEmpty()) {
                execBlock(stmt.elseBody, scope)
            }
        } finally {
            if (stmt.finallyBody.isNotEmpty()) {
                execBlock(stmt.finallyBody, scope)
            }
        }
    }

    private fun matchesException(actualType: String, handlerVal: PyVal): Boolean = when (handlerVal) {
        is PyClass -> handlerVal.name == "Exception" || handlerVal.name == "BaseException" || handlerVal.name == actualType
        is PyStr -> handlerVal.value == actualType
        is PyTuple -> handlerVal.items.any { matchesException(actualType, it) }
        else -> false
    }

    fun invokeUserFunction(
        fn: PyUserFunc,
        posArgs: List<PyVal>,
        kwArgs: Map<String, PyVal>,
        callLine: Int
    ): PyVal {
        recursionDepth++
        if (recursionDepth > maxRecursionDepth) {
            recursionDepth--
            throw PythonRuntimeException("RecursionError", "maximum recursion depth exceeded in comparison", callLine)
        }
        frameStack.add("File \"<module>\", in ${fn.name}()")
        val fnScope = PyScope(fn.closure)
        try {
            val regularParams = fn.params.filter { !it.isVarArg && !it.isKwVarArg }
            val varArgParam = fn.params.firstOrNull { it.isVarArg }
            val kwVarArgParam = fn.params.firstOrNull { it.isKwVarArg }

            val assigned = mutableSetOf<String>()
            val extraPos = mutableListOf<PyVal>()
            posArgs.forEachIndexed { idx, argVal ->
                if (idx < regularParams.size) {
                    val pname = regularParams[idx].name
                    fnScope.set(pname, argVal)
                    assigned.add(pname)
                } else {
                    extraPos.add(argVal)
                }
            }
            if (extraPos.isNotEmpty()) {
                if (varArgParam != null) {
                    fnScope.set(varArgParam.name, PyTuple(extraPos))
                } else {
                    throw PythonRuntimeException(
                        "TypeError",
                        "${fn.name}() takes ${regularParams.size} positional arguments but ${posArgs.size} were given",
                        callLine
                    )
                }
            } else if (varArgParam != null) {
                fnScope.set(varArgParam.name, PyTuple(emptyList()))
            }

            val extraKw = linkedMapOf<PyVal, PyVal>()
            for ((k, v) in kwArgs) {
                val paramMatch = regularParams.firstOrNull { it.name == k }
                if (paramMatch != null) {
                    if (k in assigned) {
                        throw PythonRuntimeException("TypeError", "${fn.name}() got multiple values for argument '$k'", callLine)
                    }
                    fnScope.set(k, v)
                    assigned.add(k)
                } else if (kwVarArgParam != null) {
                    extraKw[PyStr(k)] = v
                } else {
                    throw PythonRuntimeException("TypeError", "${fn.name}() got an unexpected keyword argument '$k'", callLine)
                }
            }
            if (kwVarArgParam != null) {
                fnScope.set(kwVarArgParam.name, PyDict(extraKw))
            }

            regularParams.forEachIndexed { idx, p ->
                if (p.name !in assigned) {
                    val originalIndex = fn.params.indexOf(p)
                    val defVal = fn.defaults.getOrNull(originalIndex)
                        ?: throw PythonRuntimeException("TypeError", "${fn.name}() missing 1 required positional argument: '${p.name}'", callLine)
                    fnScope.set(p.name, defVal)
                }
            }

            return try {
                execBlock(fn.body, fnScope)
                PyNone
            } catch (ret: ControlSignal.Return) {
                ret.value
            }
        } finally {
            frameStack.removeAt(frameStack.lastIndex)
            recursionDepth--
        }
    }

    private fun evalExpr(expr: PyExpr, scope: PyScope): PyVal {
        checkLimits(expr.line)
        return when (expr) {
            is PyIntLit -> PyInt(expr.value)
            is PyFloatLit -> PyFloat(expr.value)
            is PyStringLit -> PyStr(expr.value)
            is PyBoolLit -> PyBool(expr.value)
            is PyNoneLit -> PyNone
            is PyIdent -> scope.get(expr.name)
                ?: throw PythonRuntimeException("NameError", "name '${expr.name}' is not defined", expr.line)
            is PyFStringExpr -> evalFString(expr.rawTemplate, scope, expr.line)
            is PyListExpr -> PyList(expandStarredList(expr.items, scope))
            is PyTupleExpr -> PyTuple(expandStarredList(expr.items, scope))
            is PySetExpr -> {
                val s = linkedSetOf<PyVal>()
                expandStarredList(expr.items, scope).forEach { s.add(it) }
                PySet(s)
            }
            is PyDictExpr -> {
                val map = linkedMapOf<PyVal, PyVal>()
                for ((kExpr, vExpr) in expr.entries) {
                    if (kExpr == null) {
                        val d = evalExpr(vExpr, scope) as? PyDict
                            ?: throw PythonRuntimeException("TypeError", "argument after ** must be a mapping", expr.line)
                        map.putAll(d.entries)
                    } else {
                        map[evalExpr(kExpr, scope)] = evalExpr(vExpr, scope)
                    }
                }
                PyDict(map)
            }
            is PyListCompExpr -> {
                val out = mutableListOf<PyVal>()
                evalComprehension(expr.clauses, 0, scope, expr.line) { compScope ->
                    if (out.size >= maxCollectionItems) throw PythonRuntimeException("MemoryError", "Collection size cap reached", expr.line)
                    out.add(evalExpr(expr.elt, compScope))
                }
                PyList(out)
            }
            is PySetCompExpr -> {
                val out = linkedSetOf<PyVal>()
                evalComprehension(expr.clauses, 0, scope, expr.line) { compScope ->
                    if (out.size >= maxCollectionItems) throw PythonRuntimeException("MemoryError", "Collection size cap reached", expr.line)
                    out.add(evalExpr(expr.elt, compScope))
                }
                PySet(out)
            }
            is PyDictCompExpr -> {
                val out = linkedMapOf<PyVal, PyVal>()
                evalComprehension(expr.clauses, 0, scope, expr.line) { compScope ->
                    if (out.size >= maxCollectionItems) throw PythonRuntimeException("MemoryError", "Collection size cap reached", expr.line)
                    out[evalExpr(expr.key, compScope)] = evalExpr(expr.value, compScope)
                }
                PyDict(out)
            }
            is PyStarredExpr -> evalExpr(expr.inner, scope)
            is PyUnaryOp -> evalUnary(expr.op, evalExpr(expr.operand, scope), expr.line)
            is PyBinaryOp -> when (expr.op) {
                "and" -> {
                    val l = evalExpr(expr.left, scope)
                    if (!l.asBool()) l else evalExpr(expr.right, scope)
                }
                "or" -> {
                    val l = evalExpr(expr.left, scope)
                    if (l.asBool()) l else evalExpr(expr.right, scope)
                }
                else -> applyBinaryOp(evalExpr(expr.left, scope), expr.op, evalExpr(expr.right, scope), expr.line)
            }
            is PyCompareExpr -> {
                var left = evalExpr(expr.first, scope)
                var passed = true
                for ((op, rightExpr) in expr.opsAndRights) {
                    val right = evalExpr(rightExpr, scope)
                    if (!evalComparison(left, op, right, expr.line)) {
                        passed = false
                        break
                    }
                    left = right
                }
                PyBool(passed)
            }
            is PyTernaryExpr -> {
                if (evalExpr(expr.cond, scope).asBool()) evalExpr(expr.body, scope) else evalExpr(expr.orelse, scope)
            }
            is PyWalrusExpr -> {
                val v = evalExpr(expr.value, scope)
                scope.set(expr.name, v)
                v
            }
            is PyLambdaExpr -> {
                val defaults = expr.params.map { p -> p.defaultExpr?.let { evalExpr(it, scope) } }
                PyUserFunc("<lambda>", expr.params, listOf(PyReturnStmt(expr.body, expr.line)), scope, defaults)
            }
            is PyCallExpr -> {
                val callee = evalExpr(expr.callee, scope) as? PyCallable
                    ?: throw PythonRuntimeException("TypeError", "'${expr.callee}' object is not callable", expr.line)
                val posArgs = mutableListOf<PyVal>()
                val kwArgs = linkedMapOf<String, PyVal>()
                for (arg in expr.args) {
                    when {
                        arg.isStar -> posArgs.addAll(toIterableList(evalExpr(arg.value, scope), expr.line))
                        arg.isDoubleStar -> {
                            val d = evalExpr(arg.value, scope) as? PyDict
                                ?: throw PythonRuntimeException("TypeError", "argument after ** must be a mapping", expr.line)
                            d.entries.forEach { (k, v) -> kwArgs[k.pyStr()] = v }
                        }
                        arg.name != null -> kwArgs[arg.name] = evalExpr(arg.value, scope)
                        else -> posArgs.add(evalExpr(arg.value, scope))
                    }
                }
                callee.call(this, posArgs, kwArgs, expr.line)
            }
            is PyAttrExpr -> getAttribute(evalExpr(expr.obj, scope), expr.attr, expr.line)
            is PySubscriptExpr -> getSubscript(evalExpr(expr.obj, scope), expr.index, scope, expr.line)
            is PySliceExpr -> PySliceVal(
                start = expr.start?.let { (evalExpr(it, scope) as? PyInt)?.value?.toInt() },
                stop = expr.stop?.let { (evalExpr(it, scope) as? PyInt)?.value?.toInt() },
                step = expr.step?.let { (evalExpr(it, scope) as? PyInt)?.value?.toInt() }
            )
        }
    }

    private fun evalComprehension(
        clauses: List<PyCompClause>,
        idx: Int,
        parentScope: PyScope,
        line: Int,
        emit: (PyScope) -> Unit
    ) {
        if (idx >= clauses.size) {
            emit(parentScope)
            return
        }
        val clause = clauses[idx]
        val iterList = toIterableSequence(evalExpr(clause.iter, parentScope), line)
        val localScope = PyScope(parentScope)
        for (item in iterList) {
            checkLimits(line)
            assignTarget(clause.target, item, localScope, line)
            val condPass = clause.conditions.all { evalExpr(it, localScope).asBool() }
            if (condPass) {
                evalComprehension(clauses, idx + 1, localScope, line, emit)
            }
        }
    }

    private fun expandStarredList(items: List<PyExpr>, scope: PyScope): MutableList<PyVal> {
        val out = mutableListOf<PyVal>()
        for (item in items) {
            if (item is PyStarredExpr) {
                out.addAll(toIterableList(evalExpr(item.inner, scope), item.line))
            } else {
                out.add(evalExpr(item, scope))
            }
        }
        return out
    }

    private fun evalFString(template: String, scope: PyScope, line: Int): PyVal {
        val sb = StringBuilder()
        var i = 0
        while (i < template.length) {
            val ch = template[i]
            if (ch == '{') {
                if (i + 1 < template.length && template[i + 1] == '{') {
                    sb.append('{')
                    i += 2
                    continue
                }
                var depth = 1
                var j = i + 1
                while (j < template.length && depth > 0) {
                    if (template[j] == '{') depth++
                    else if (template[j] == '}') depth--
                    j++
                }
                if (depth != 0) throw PythonSyntaxException("SyntaxError", "f-string: expecting '}'", line)
                val inner = template.substring(i + 1, j - 1)
                val colonIdx = findFormatColon(inner)
                val exprCode = if (colonIdx >= 0) inner.substring(0, colonIdx) else inner
                val fmtSpec = if (colonIdx >= 0) inner.substring(colonIdx + 1) else null
                val tokens = PythonLexer.tokenize(exprCode.trim())
                val parsed = PythonParser(tokens).parseExpr()
                val valResult = evalExpr(parsed, scope)
                sb.append(formatWithSpec(valResult, fmtSpec, line))
                i = j
            } else if (ch == '}' && i + 1 < template.length && template[i + 1] == '}') {
                sb.append('}')
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return PyStr(sb.toString())
    }

    private fun findFormatColon(s: String): Int {
        var depth = 0
        var inQuote = '\u0000'
        for (i in s.indices) {
            val c = s[i]
            if (inQuote != '\u0000') {
                if (c == inQuote && (i == 0 || s[i - 1] != '\\')) inQuote = '\u0000'
                continue
            }
            if (c == '\'' || c == '"') inQuote = c
            else if (c == '(' || c == '[' || c == '{') depth++
            else if (c == ')' || c == ']' || c == '}') depth--
            else if (c == ':' && depth == 0) return i
        }
        return -1
    }

    private fun formatWithSpec(value: PyVal, spec: String?, line: Int): String {
        if (spec.isNullOrBlank()) return value.pyStr()
        val fMatch = Regex("""^\.?(\d+)f$""").find(spec.trim())
        if (fMatch != null) {
            val decimals = fMatch.groupValues[1].toInt()
            val num = toDouble(value, line)
            return String.format(java.util.Locale.US, "%.${decimals}f", num)
        }
        val dMatch = Regex("""^0?(\d+)d$""").find(spec.trim())
        if (dMatch != null && value is PyInt) {
            return value.value.toString().padStart(dMatch.groupValues[1].toInt(), if (spec.trim().startsWith("0")) '0' else ' ')
        }
        return value.pyStr()
    }

    private fun evalUnary(op: String, v: PyVal, line: Int): PyVal = when (op) {
        "not" -> PyBool(!v.asBool())
        "-" -> when (v) {
            is PyInt -> PyInt(-v.value)
            is PyBool -> PyInt(if (v.value) -1L else 0L)
            is PyFloat -> PyFloat(-v.value)
            else -> throw PythonRuntimeException("TypeError", "bad operand type for unary -: '${v.pyRepr()}'", line)
        }
        "+" -> when (v) {
            is PyInt -> v
            is PyFloat -> v
            is PyBool -> PyInt(if (v.value) 1L else 0L)
            else -> throw PythonRuntimeException("TypeError", "bad operand type for unary +", line)
        }
        "~" -> PyInt((v as? PyInt)?.value?.inv() ?: throw PythonRuntimeException("TypeError", "bad operand type for ~", line))
        else -> throw PythonRuntimeException("SyntaxError", "unknown unary op $op", line)
    }

    private fun applyBinaryOp(l: PyVal, op: String, r: PyVal, line: Int): PyVal {
        when (op) {
            "+" -> {
                if (l is PyStr && r is PyStr) return PyStr(l.value + r.value)
                if (l is PyList && r is PyList) {
                    if (l.items.size + r.items.size > maxCollectionItems) throw PythonRuntimeException("MemoryError", "List size cap exceeded", line)
                    return PyList((l.items + r.items).toMutableList())
                }
                if (l is PyTuple && r is PyTuple) return PyTuple(l.items + r.items)
            }
            "*" -> {
                if (l is PyStr && r is PyInt) {
                    val count = r.value.coerceAtLeast(0L)
                    if (count * l.value.length > maxOutputChars) throw PythonRuntimeException("MemoryError", "String repeat limit exceeded", line)
                    return PyStr(l.value.repeat(count.toInt()))
                }
                if (l is PyInt && r is PyStr) return applyBinaryOp(r, "*", l, line)
                if (l is PyList && r is PyInt) {
                    val count = r.value.coerceAtLeast(0L)
                    if (count * l.items.size > maxCollectionItems) throw PythonRuntimeException("MemoryError", "List repeat limit exceeded", line)
                    val res = mutableListOf<PyVal>()
                    repeat(count.toInt()) { res.addAll(l.items) }
                    return PyList(res)
                }
            }
            "%" -> {
                if (l is PyStr) {
                    val args = if (r is PyTuple) r.items else listOf(r)
                    var idx = 0
                    val formatted = Regex("%[sdfr]").replace(l.value) {
                        args.getOrElse(idx++) { PyStr("") }.pyStr()
                    }
                    return PyStr(formatted)
                }
            }
        }

        if ((l is PyInt || l is PyBool) && (r is PyInt || r is PyBool)) {
            val a = if (l is PyBool) (if (l.value) 1L else 0L) else (l as PyInt).value
            val b = if (r is PyBool) (if (r.value) 1L else 0L) else (r as PyInt).value
            return when (op) {
                "+" -> PyInt(a + b)
                "-" -> PyInt(a - b)
                "*" -> PyInt(a * b)
                "/" -> {
                    if (b == 0L) throw PythonRuntimeException("ZeroDivisionError", "division by zero", line)
                    PyFloat(a.toDouble() / b.toDouble())
                }
                "//" -> {
                    if (b == 0L) throw PythonRuntimeException("ZeroDivisionError", "integer division or modulo by zero", line)
                    PyInt(Math.floorDiv(a, b))
                }
                "%" -> {
                    if (b == 0L) throw PythonRuntimeException("ZeroDivisionError", "integer division or modulo by zero", line)
                    PyInt(Math.floorMod(a, b))
                }
                "**" -> if (b >= 0) PyInt(a.toDouble().pow(b.toDouble()).toLong()) else PyFloat(a.toDouble().pow(b.toDouble()))
                "&" -> PyInt(a and b)
                "|" -> PyInt(a or b)
                "^" -> PyInt(a xor b)
                "<<" -> PyInt(a shl b.toInt().coerceIn(0, 63))
                ">>" -> PyInt(a shr b.toInt().coerceIn(0, 63))
                else -> throw PythonRuntimeException("TypeError", "unsupported operand type(s) for $op", line)
            }
        }

        if (isNumeric(l) && isNumeric(r)) {
            val a = toDouble(l, line)
            val b = toDouble(r, line)
            return when (op) {
                "+" -> PyFloat(a + b)
                "-" -> PyFloat(a - b)
                "*" -> PyFloat(a * b)
                "/" -> {
                    if (b == 0.0) throw PythonRuntimeException("ZeroDivisionError", "float division by zero", line)
                    PyFloat(a / b)
                }
                "//" -> {
                    if (b == 0.0) throw PythonRuntimeException("ZeroDivisionError", "float floor division by zero", line)
                    PyFloat(floor(a / b))
                }
                "%" -> {
                    if (b == 0.0) throw PythonRuntimeException("ZeroDivisionError", "float modulo", line)
                    PyFloat(((a % b) + b) % b)
                }
                "**" -> PyFloat(a.pow(b))
                else -> throw PythonRuntimeException("TypeError", "unsupported operand type(s) for $op", line)
            }
        }

        throw PythonRuntimeException("TypeError", "unsupported operand type(s) for $op: '${l.pyRepr()}' and '${r.pyRepr()}'", line)
    }

    private fun evalComparison(l: PyVal, op: String, r: PyVal, line: Int): Boolean = when (op) {
        "==" -> l == r || (isNumeric(l) && isNumeric(r) && toDouble(l, line) == toDouble(r, line))
        "!=" -> !evalComparison(l, "==", r, line)
        "is" -> l === r || (l is PyNone && r is PyNone) || (l is PyBool && r is PyBool && l.value == r.value)
        "is not" -> !evalComparison(l, "is", r, line)
        "in" -> containsVal(r, l, line)
        "not in" -> !containsVal(r, l, line)
        "<", "<=", ">", ">=" -> {
            val cmp = compareValues(l, r, line)
            when (op) {
                "<" -> cmp < 0
                "<=" -> cmp <= 0
                ">" -> cmp > 0
                else -> cmp >= 0
            }
        }
        else -> false
    }

    private fun containsVal(container: PyVal, item: PyVal, line: Int): Boolean = when (container) {
        is PyStr -> (item as? PyStr)?.let { container.value.contains(it.value) }
            ?: throw PythonRuntimeException("TypeError", "'in <string>' requires string as left operand", line)
        is PyList -> container.items.any { evalComparison(it, "==", item, line) }
        is PyTuple -> container.items.any { evalComparison(it, "==", item, line) }
        is PySet -> container.items.contains(item)
        is PyDict -> container.entries.containsKey(item)
        is PyRange -> (item as? PyInt)?.let { item.value in container.sequence() } ?: false
        else -> throw PythonRuntimeException("TypeError", "argument is not iterable", line)
    }

    private fun compareValues(l: PyVal, r: PyVal, line: Int): Int {
        if (isNumeric(l) && isNumeric(r)) return toDouble(l, line).compareTo(toDouble(r, line))
        if (l is PyStr && r is PyStr) return l.value.compareTo(r.value)
        if (l is PyTuple && r is PyTuple) {
            for (i in 0 until minOf(l.items.size, r.items.size)) {
                val c = compareValues(l.items[i], r.items[i], line)
                if (c != 0) return c
            }
            return l.items.size.compareTo(r.items.size)
        }
        throw PythonRuntimeException("TypeError", "'<' not supported between '${l.pyRepr()}' and '${r.pyRepr()}'", line)
    }

    private fun getSubscript(obj: PyVal, indexExpr: PyExpr, scope: PyScope, line: Int): PyVal {
        val index = evalExpr(indexExpr, scope)
        if (index is PySliceVal) {
            return sliceObject(obj, index, line)
        }
        return when (obj) {
            is PyList -> {
                val idx = resolveIndex((index as? PyInt)?.value?.toInt() ?: throw PythonRuntimeException("TypeError", "list indices must be integers", line), obj.items.size, line)
                obj.items[idx]
            }
            is PyTuple -> {
                val idx = resolveIndex((index as? PyInt)?.value?.toInt() ?: throw PythonRuntimeException("TypeError", "tuple indices must be integers", line), obj.items.size, line)
                obj.items[idx]
            }
            is PyStr -> {
                val idx = resolveIndex((index as? PyInt)?.value?.toInt() ?: throw PythonRuntimeException("TypeError", "string indices must be integers", line), obj.value.length, line)
                PyStr(obj.value[idx].toString())
            }
            is PyDict -> obj.entries[index] ?: throw PythonRuntimeException("KeyError", index.pyRepr(), line)
            else -> throw PythonRuntimeException("TypeError", "'${obj.pyRepr()}' object is not subscriptable", line)
        }
    }

    private fun sliceObject(obj: PyVal, slice: PySliceVal, line: Int): PyVal {
        val size = when (obj) {
            is PyList -> obj.items.size
            is PyTuple -> obj.items.size
            is PyStr -> obj.value.length
            else -> throw PythonRuntimeException("TypeError", "object does not support slicing", line)
        }
        val step = slice.step ?: 1
        if (step == 0) throw PythonRuntimeException("ValueError", "slice step cannot be zero", line)
        val resultIndices = mutableListOf<Int>()
        if (step > 0) {
            val s = normalizeSliceBound(slice.start ?: 0, size, 0, size)
            val e = normalizeSliceBound(slice.stop ?: size, size, 0, size)
            var i = s
            while (i < e) {
                resultIndices.add(i)
                i += step
            }
        } else {
            val s = normalizeSliceBound(slice.start ?: (size - 1), size, -1, size - 1)
            val e = if (slice.stop == null) -1 else normalizeSliceBound(slice.stop, size, -1, size - 1)
            var i = s
            while (i > e) {
                resultIndices.add(i)
                i += step
            }
        }
        return when (obj) {
            is PyList -> PyList(resultIndices.map { obj.items[it] }.toMutableList())
            is PyTuple -> PyTuple(resultIndices.map { obj.items[it] })
            is PyStr -> PyStr(resultIndices.map { obj.value[it] }.joinToString(""))
        }
    }

    private fun normalizeSliceBound(v: Int, size: Int, min: Int, max: Int): Int {
        val norm = if (v < 0) v + size else v
        return norm.coerceIn(min, max)
    }

    private fun resolveIndex(raw: Int, size: Int, line: Int): Int {
        val idx = if (raw < 0) raw + size else raw
        if (idx !in 0 until size) throw PythonRuntimeException("IndexError", "index out of range", line)
        return idx
    }

    private fun readTarget(target: PyTarget, scope: PyScope, line: Int): PyVal = when (target) {
        is PyTarget.Name -> scope.get(target.id) ?: throw PythonRuntimeException("NameError", "name '${target.id}' is not defined", line)
        is PyTarget.Attr -> getAttribute(evalExpr(target.obj, scope), target.attr, line)
        is PyTarget.Subscript -> getSubscript(evalExpr(target.obj, scope), target.index, scope, line)
        else -> throw PythonRuntimeException("SyntaxError", "invalid target", line)
    }

    private fun assignTarget(target: PyTarget, value: PyVal, scope: PyScope, line: Int) {
        when (target) {
            is PyTarget.Name -> scope.set(target.id, value)
            is PyTarget.TupleOrList -> {
                val seq = toIterableList(value, line)
                val starIdx = target.items.indexOfFirst { it is PyTarget.Starred }
                if (starIdx == -1) {
                    if (seq.size != target.items.size) {
                        throw PythonRuntimeException("ValueError", "expected ${target.items.size} values to unpack, got ${seq.size}", line)
                    }
                    target.items.forEachIndexed { idx, sub -> assignTarget(sub, seq[idx], scope, line) }
                } else {
                    val before = starIdx
                    val after = target.items.size - 1 - starIdx
                    if (seq.size < before + after) {
                        throw PythonRuntimeException("ValueError", "not enough values to unpack", line)
                    }
                    for (i in 0 until before) assignTarget(target.items[i], seq[i], scope, line)
                    val starred = target.items[starIdx] as PyTarget.Starred
                    assignTarget(starred.target, PyList(seq.subList(before, seq.size - after).toMutableList()), scope, line)
                    for (j in 0 until after) {
                        assignTarget(target.items[starIdx + 1 + j], seq[seq.size - after + j], scope, line)
                    }
                }
            }
            is PyTarget.Starred -> assignTarget(target.target, value, scope, line)
            is PyTarget.Attr -> {
                val obj = evalExpr(target.obj, scope)
                when (obj) {
                    is PyInstance -> obj.fields[target.attr] = value
                    is PyClass -> obj.attrs[target.attr] = value
                    else -> throw PythonRuntimeException("AttributeError", "cannot set attribute '${target.attr}'", line)
                }
            }
            is PyTarget.Subscript -> {
                val obj = evalExpr(target.obj, scope)
                val idx = evalExpr(target.index, scope)
                when (obj) {
                    is PyList -> {
                        val i = resolveIndex((idx as? PyInt)?.value?.toInt() ?: throw PythonRuntimeException("TypeError", "list indices must be integers", line), obj.items.size, line)
                        obj.items[i] = value
                    }
                    is PyDict -> obj.entries[idx] = value
                    else -> throw PythonRuntimeException("TypeError", "object does not support item assignment", line)
                }
            }
        }
    }

    private fun getAttribute(obj: PyVal, attr: String, line: Int): PyVal {
        when (obj) {
            is PyModule -> return obj.attrs[attr]
                ?: throw PythonRuntimeException("AttributeError", "module '${obj.name}' has no attribute '$attr'", line)
            is PyInstance -> {
                obj.fields[attr]?.let { return it }
                val clsAttr = obj.klass.resolveAttr(attr)
                    ?: throw PythonRuntimeException("AttributeError", "'${obj.klass.name}' object has no attribute '$attr'", line)
                return if (clsAttr is PyCallable) PyBoundMethod(obj, clsAttr) else clsAttr
            }
            is PyClass -> return obj.resolveAttr(attr)
                ?: throw PythonRuntimeException("AttributeError", "type object '${obj.name}' has no attribute '$attr'", line)
            is PyStr -> return stringMethod(obj, attr, line)
            is PyList -> return listMethod(obj, attr, line)
            is PyDict -> return dictMethod(obj, attr, line)
            is PySet -> return setMethod(obj, attr, line)
            else -> throw PythonRuntimeException("AttributeError", "'${obj.pyRepr()}' has no attribute '$attr'", line)
        }
    }

    private fun stringMethod(s: PyStr, attr: String, line: Int): PyVal = when (attr) {
        "upper" -> PyBuiltinFunc("upper") { _, _, _, _ -> PyStr(s.value.uppercase()) }
        "lower" -> PyBuiltinFunc("lower") { _, _, _, _ -> PyStr(s.value.lowercase()) }
        "title" -> PyBuiltinFunc("title") { _, _, _, _ ->
            PyStr(s.value.split(" ").joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } })
        }
        "capitalize" -> PyBuiltinFunc("capitalize") { _, _, _, _ -> PyStr(s.value.lowercase().replaceFirstChar { it.uppercase() }) }
        "strip" -> PyBuiltinFunc("strip") { _, pos, _, _ ->
            if (pos.isEmpty()) PyStr(s.value.trim()) else PyStr(s.value.trim(*pos[0].pyStr().toCharArray()))
        }
        "lstrip" -> PyBuiltinFunc("lstrip") { _, _, _, _ -> PyStr(s.value.trimStart()) }
        "rstrip" -> PyBuiltinFunc("rstrip") { _, _, _, _ -> PyStr(s.value.trimEnd()) }
        "split" -> PyBuiltinFunc("split") { _, pos, _, _ ->
            val parts = if (pos.isEmpty() || pos[0] is PyNone) {
                s.value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            } else {
                s.value.split(pos[0].pyStr())
            }
            PyList(parts.map { PyStr(it) }.toMutableList())
        }
        "join" -> PyBuiltinFunc("join") { _, pos, _, ln ->
            val items = toIterableList(pos.firstOrNull() ?: PyList(mutableListOf()), ln)
            PyStr(items.joinToString(s.value) { it.pyStr() })
        }
        "replace" -> PyBuiltinFunc("replace") { _, pos, _, _ ->
            PyStr(s.value.replace(pos[0].pyStr(), pos[1].pyStr()))
        }
        "startswith" -> PyBuiltinFunc("startswith") { _, pos, _, _ -> PyBool(s.value.startsWith(pos[0].pyStr())) }
        "endswith" -> PyBuiltinFunc("endswith") { _, pos, _, _ -> PyBool(s.value.endsWith(pos[0].pyStr())) }
        "find" -> PyBuiltinFunc("find") { _, pos, _, _ -> PyInt(s.value.indexOf(pos[0].pyStr()).toLong()) }
        "count" -> PyBuiltinFunc("count") { _, pos, _, _ ->
            val sub = pos[0].pyStr()
            var count = 0L
            var idx = 0
            while (sub.isNotEmpty()) {
                val found = s.value.indexOf(sub, idx)
                if (found < 0) break
                count++
                idx = found + sub.length
            }
            PyInt(count)
        }
        "isdigit" -> PyBuiltinFunc("isdigit") { _, _, _, _ -> PyBool(s.value.isNotEmpty() && s.value.all { it.isDigit() }) }
        "isalpha" -> PyBuiltinFunc("isalpha") { _, _, _, _ -> PyBool(s.value.isNotEmpty() && s.value.all { it.isLetter() }) }
        "format" -> PyBuiltinFunc("format") { _, pos, _, _ ->
            var i = 0
            val out = Regex("\\{(\\d*)}").replace(s.value) { m ->
                val idx = m.groupValues[1].toIntOrNull() ?: i++
                pos.getOrNull(idx)?.pyStr() ?: ""
            }
            PyStr(out)
        }
        else -> throw PythonRuntimeException("AttributeError", "'str' object has no attribute '$attr'", line)
    }

    private fun listMethod(list: PyList, attr: String, line: Int): PyVal = when (attr) {
        "append" -> PyBuiltinFunc("append") { _, pos, _, ln ->
            if (list.items.size >= maxCollectionItems) throw PythonRuntimeException("MemoryError", "List limit reached", ln)
            list.items.add(pos.first())
            PyNone
        }
        "extend" -> PyBuiltinFunc("extend") { _, pos, _, ln ->
            list.items.addAll(toIterableList(pos.first(), ln))
            PyNone
        }
        "insert" -> PyBuiltinFunc("insert") { _, pos, _, _ ->
            val idx = (pos[0] as PyInt).value.toInt().coerceIn(0, list.items.size)
            list.items.add(idx, pos[1])
            PyNone
        }
        "pop" -> PyBuiltinFunc("pop") { _, pos, _, ln ->
            if (list.items.isEmpty()) throw PythonRuntimeException("IndexError", "pop from empty list", ln)
            val idx = if (pos.isEmpty()) list.items.lastIndex else resolveIndex((pos[0] as PyInt).value.toInt(), list.items.size, ln)
            list.items.removeAt(idx)
        }
        "remove" -> PyBuiltinFunc("remove") { _, pos, _, ln ->
            val target = pos.first()
            val idx = list.items.indexOfFirst { evalComparison(it, "==", target, ln) }
            if (idx < 0) throw PythonRuntimeException("ValueError", "list.remove(x): x not in list", ln)
            list.items.removeAt(idx)
            PyNone
        }
        "clear" -> PyBuiltinFunc("clear") { _, _, _, _ -> list.items.clear(); PyNone }
        "reverse" -> PyBuiltinFunc("reverse") { _, _, _, _ -> list.items.reverse(); PyNone }
        "copy" -> PyBuiltinFunc("copy") { _, _, _, _ -> PyList(list.items.toMutableList()) }
        "count" -> PyBuiltinFunc("count") { _, pos, _, ln ->
            PyInt(list.items.count { evalComparison(it, "==", pos.first(), ln) }.toLong())
        }
        "index" -> PyBuiltinFunc("index") { _, pos, _, ln ->
            val idx = list.items.indexOfFirst { evalComparison(it, "==", pos.first(), ln) }
            if (idx < 0) throw PythonRuntimeException("ValueError", "x is not in list", ln)
            PyInt(idx.toLong())
        }
        "sort" -> PyBuiltinFunc("sort") { rt, _, kw, ln ->
            val reverse = kw["reverse"]?.asBool() ?: false
            val keyFn = kw["key"] as? PyCallable
            val sorted = list.items.sortedWith { a, b ->
                val ka = keyFn?.call(rt, listOf(a), emptyMap(), ln) ?: a
                val kb = keyFn?.call(rt, listOf(b), emptyMap(), ln) ?: b
                compareValues(ka, kb, ln)
            }
            list.items.clear()
            list.items.addAll(if (reverse) sorted.asReversed() else sorted)
            PyNone
        }
        else -> throw PythonRuntimeException("AttributeError", "'list' object has no attribute '$attr'", line)
    }

    private fun dictMethod(dict: PyDict, attr: String, line: Int): PyVal = when (attr) {
        "get" -> PyBuiltinFunc("get") { _, pos, _, _ -> dict.entries[pos[0]] ?: pos.getOrNull(1) ?: PyNone }
        "keys" -> PyBuiltinFunc("keys") { _, _, _, _ -> PyList(dict.entries.keys.toMutableList()) }
        "values" -> PyBuiltinFunc("values") { _, _, _, _ -> PyList(dict.entries.values.toMutableList()) }
        "items" -> PyBuiltinFunc("items") { _, _, _, _ ->
            PyList(dict.entries.entries.map { PyTuple(listOf(it.key, it.value)) }.toMutableList())
        }
        "pop" -> PyBuiltinFunc("pop") { _, pos, _, ln ->
            dict.entries.remove(pos[0]) ?: pos.getOrNull(1) ?: throw PythonRuntimeException("KeyError", pos[0].pyRepr(), ln)
        }
        "update" -> PyBuiltinFunc("update") { _, pos, _, ln ->
            val other = pos.first() as? PyDict ?: throw PythonRuntimeException("TypeError", "update expects a dict", ln)
            dict.entries.putAll(other.entries)
            PyNone
        }
        "clear" -> PyBuiltinFunc("clear") { _, _, _, _ -> dict.entries.clear(); PyNone }
        "copy" -> PyBuiltinFunc("copy") { _, _, _, _ -> PyDict(LinkedHashMap(dict.entries)) }
        else -> throw PythonRuntimeException("AttributeError", "'dict' object has no attribute '$attr'", line)
    }

    private fun setMethod(set: PySet, attr: String, line: Int): PyVal = when (attr) {
        "add" -> PyBuiltinFunc("add") { _, pos, _, _ -> set.items.add(pos.first()); PyNone }
        "remove" -> PyBuiltinFunc("remove") { _, pos, _, ln ->
            if (!set.items.remove(pos.first())) throw PythonRuntimeException("KeyError", pos.first().pyRepr(), ln)
            PyNone
        }
        "discard" -> PyBuiltinFunc("discard") { _, pos, _, _ -> set.items.remove(pos.first()); PyNone }
        "union" -> PyBuiltinFunc("union") { _, pos, _, ln ->
            val res = LinkedHashSet(set.items)
            res.addAll(toIterableList(pos.first(), ln))
            PySet(res)
        }
        "intersection" -> PyBuiltinFunc("intersection") { _, pos, _, ln ->
            val other = toIterableList(pos.first(), ln).toSet()
            PySet(LinkedHashSet(set.items.filter { it in other }))
        }
        "clear" -> PyBuiltinFunc("clear") { _, _, _, _ -> set.items.clear(); PyNone }
        else -> throw PythonRuntimeException("AttributeError", "'set' object has no attribute '$attr'", line)
    }

    private fun toIterableSequence(v: PyVal, line: Int): Sequence<PyVal> = when (v) {
        is PyList -> v.items.asSequence()
        is PyTuple -> v.items.asSequence()
        is PySet -> v.items.asSequence()
        is PyDict -> v.entries.keys.asSequence()
        is PyStr -> v.value.asSequence().map { PyStr(it.toString()) }
        is PyRange -> v.sequence().map { PyInt(it) }
        else -> throw PythonRuntimeException("TypeError", "'${v.pyRepr()}' object is not iterable", line)
    }

    private fun toIterableList(v: PyVal, line: Int): List<PyVal> {
        val list = mutableListOf<PyVal>()
        for (item in toIterableSequence(v, line)) {
            if (list.size >= maxCollectionItems) throw PythonRuntimeException("MemoryError", "Collection size cap exceeded", line)
            list.add(item)
        }
        return list
    }

    private fun isNumeric(v: PyVal) = v is PyInt || v is PyFloat || v is PyBool
    private fun toDouble(v: PyVal, line: Int): Double = when (v) {
        is PyInt -> v.value.toDouble()
        is PyFloat -> v.value
        is PyBool -> if (v.value) 1.0 else 0.0
        else -> throw PythonRuntimeException("TypeError", "must be real number, not '${v.pyRepr()}'", line)
    }

    private fun resolveModule(name: String, line: Int): PyModule {
        loadedModules[name]?.let { return it }
        builtInStdlibModule(name)?.let {
            loadedModules[name] = it
            return it
        }
        val workspaceFile = workspaceFiles.firstOrNull {
            it.languageId == "python" && (it.name == "$name.py" || it.path == "$name.py")
        }
        if (workspaceFile != null) {
            val modScope = PyScope(globalScope)
            val tokens = PythonLexer.tokenize(workspaceFile.content)
            val stmts = PythonParser(tokens).parseModule()
            execBlock(stmts, modScope)
            val mod = PyModule(name, modScope.vars)
            loadedModules[name] = mod
            return mod
        }
        if (name in setOf("os", "subprocess", "socket", "ctypes")) {
            throw PythonRuntimeException("PermissionError", "Module '$name' is blocked in PoraStudio Sandboxed Python VM for security.", line)
        }
        throw PythonRuntimeException(
            "ModuleNotFoundError",
            "No module named '$name' in on-device sandbox stdlib. For external packages, configure a Remote Sandbox URL in Dev Tools > Settings.",
            line
        )
    }

    private fun builtInStdlibModule(name: String): PyModule? = when (name) {
        "math" -> PyModule("math", mutableMapOf(
            "pi" to PyFloat(PI),
            "e" to PyFloat(E),
            "sqrt" to PyBuiltinFunc("sqrt") { _, p, _, ln -> PyFloat(sqrt(toDouble(p[0], ln))) },
            "sin" to PyBuiltinFunc("sin") { _, p, _, ln -> PyFloat(sin(toDouble(p[0], ln))) },
            "cos" to PyBuiltinFunc("cos") { _, p, _, ln -> PyFloat(cos(toDouble(p[0], ln))) },
            "tan" to PyBuiltinFunc("tan") { _, p, _, ln -> PyFloat(tan(toDouble(p[0], ln))) },
            "floor" to PyBuiltinFunc("floor") { _, p, _, ln -> PyInt(floor(toDouble(p[0], ln)).toLong()) },
            "ceil" to PyBuiltinFunc("ceil") { _, p, _, ln -> PyInt(ceil(toDouble(p[0], ln)).toLong()) },
            "log" to PyBuiltinFunc("log") { _, p, _, ln ->
                val x = toDouble(p[0], ln)
                if (p.size > 1) PyFloat(ln(x) / ln(toDouble(p[1], ln))) else PyFloat(ln(x))
            },
            "log10" to PyBuiltinFunc("log10") { _, p, _, ln -> PyFloat(log10(toDouble(p[0], ln))) },
            "pow" to PyBuiltinFunc("pow") { _, p, _, ln -> PyFloat(toDouble(p[0], ln).pow(toDouble(p[1], ln))) },
            "fabs" to PyBuiltinFunc("fabs") { _, p, _, ln -> PyFloat(abs(toDouble(p[0], ln))) },
            "factorial" to PyBuiltinFunc("factorial") { _, p, _, ln ->
                val n = (p[0] as? PyInt)?.value ?: throw PythonRuntimeException("TypeError", "factorial() requires int", ln)
                if (n < 0 || n > 20) throw PythonRuntimeException("ValueError", "factorial() argument out of safe range (0..20)", ln)
                var acc = 1L
                for (k in 2..n) acc *= k
                PyInt(acc)
            },
            "gcd" to PyBuiltinFunc("gcd") { _, p, _, ln ->
                var a = abs((p[0] as PyInt).value)
                var b = abs((p[1] as PyInt).value)
                while (b != 0L) { val t = a % b; a = b; b = t }
                PyInt(a)
            }
        ))
        "random" -> PyModule("random", mutableMapOf(
            "random" to PyBuiltinFunc("random") { _, _, _, _ -> PyFloat(Random.nextDouble()) },
            "randint" to PyBuiltinFunc("randint") { _, p, _, _ ->
                PyInt(Random.nextLong((p[0] as PyInt).value, (p[1] as PyInt).value + 1))
            },
            "uniform" to PyBuiltinFunc("uniform") { _, p, _, ln ->
                val a = toDouble(p[0], ln); val b = toDouble(p[1], ln)
                PyFloat(a + Random.nextDouble() * (b - a))
            },
            "choice" to PyBuiltinFunc("choice") { _, p, _, ln ->
                val items = toIterableList(p[0], ln)
                if (items.isEmpty()) throw PythonRuntimeException("IndexError", "Cannot choose from an empty sequence", ln)
                items[Random.nextInt(items.size)]
            },
            "shuffle" to PyBuiltinFunc("shuffle") { _, p, _, ln ->
                val l = p[0] as? PyList ?: throw PythonRuntimeException("TypeError", "shuffle requires a list", ln)
                l.items.shuffle()
                PyNone
            }
        ))
        "json" -> PyModule("json", mutableMapOf(
            "dumps" to PyBuiltinFunc("dumps") { _, p, kw, ln ->
                val indent = (kw["indent"] as? PyInt)?.value?.toInt()
                PyStr(pyValToJsonString(p[0], indent, ln))
            },
            "loads" to PyBuiltinFunc("loads") { _, p, _, ln ->
                jsonStringToPyVal(p[0].pyStr(), ln)
            }
        ))
        "time" -> PyModule("time", mutableMapOf(
            "time" to PyBuiltinFunc("time") { _, _, _, _ -> PyFloat(System.currentTimeMillis() / 1000.0) },
            "monotonic" to PyBuiltinFunc("monotonic") { _, _, _, _ -> PyFloat(System.nanoTime() / 1_000_000_000.0) },
            "sleep" to PyBuiltinFunc("sleep") { rt, p, _, ln ->
                val secs = rt.toDouble(p[0], ln).coerceIn(0.0, 5.0)
                val targetEnd = System.currentTimeMillis() + (secs * 1000).toLong()
                while (System.currentTimeMillis() < targetEnd) {
                    rt.checkLimits(ln)
                    Thread.sleep(minOf(50L, (targetEnd - System.currentTimeMillis()).coerceAtLeast(1L)))
                }
                PyNone
            }
        ))
        "sys" -> PyModule("sys", mutableMapOf(
            "version" to PyStr("3.11 (PoraStudio Sandboxed VM)"),
            "platform" to PyStr("android-sandbox"),
            "maxsize" to PyInt(Long.MAX_VALUE)
        ))
        "statistics" -> PyModule("statistics", mutableMapOf(
            "mean" to PyBuiltinFunc("mean") { _, p, _, ln ->
                val nums = toIterableList(p[0], ln).map { toDouble(it, ln) }
                if (nums.isEmpty()) throw PythonRuntimeException("StatisticsError", "mean requires at least one data point", ln)
                PyFloat(nums.average())
            },
            "median" to PyBuiltinFunc("median") { _, p, _, ln ->
                val nums = toIterableList(p[0], ln).map { toDouble(it, ln) }.sorted()
                if (nums.isEmpty()) throw PythonRuntimeException("StatisticsError", "no median for empty data", ln)
                val mid = nums.size / 2
                PyFloat(if (nums.size % 2 == 1) nums[mid] else (nums[mid - 1] + nums[mid]) / 2.0)
            }
        ))
        "string" -> PyModule("string", mutableMapOf(
            "ascii_lowercase" to PyStr("abcdefghijklmnopqrstuvwxyz"),
            "ascii_uppercase" to PyStr("ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
            "ascii_letters" to PyStr("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"),
            "digits" to PyStr("0123456789")
        ))
        "re" -> PyModule("re", mutableMapOf(
            "findall" to PyBuiltinFunc("findall") { _, p, _, _ ->
                val rx = Regex(p[0].pyStr())
                val matches = rx.findAll(p[1].pyStr()).map<MatchResult, PyVal> { PyStr(it.value) }.toMutableList()
                PyList(matches)
            },
            "sub" to PyBuiltinFunc("sub") { _, p, _, _ ->
                PyStr(Regex(p[0].pyStr()).replace(p[2].pyStr(), p[1].pyStr()))
            },
            "search" to PyBuiltinFunc("search") { _, p, _, _ ->
                val m = Regex(p[0].pyStr()).find(p[1].pyStr())
                if (m == null) PyNone else PyStr(m.value)
            }
        ))
        else -> null
    }

    private fun pyValToJsonString(v: PyVal, indent: Int?, line: Int): String {
        val raw = toJavaObj(v, line)
        return when (raw) {
            is JSONObject -> if (indent != null) raw.toString(indent) else raw.toString()
            is JSONArray -> if (indent != null) raw.toString(indent) else raw.toString()
            is String -> JSONObject.quote(raw)
            null -> "null"
            else -> raw.toString()
        }
    }

    private fun toJavaObj(v: PyVal, line: Int): Any? = when (v) {
        is PyNone -> JSONObject.NULL
        is PyBool -> v.value
        is PyInt -> v.value
        is PyFloat -> v.value
        is PyStr -> v.value
        is PyList -> JSONArray().apply { v.items.forEach { put(toJavaObj(it, line)) } }
        is PyTuple -> JSONArray().apply { v.items.forEach { put(toJavaObj(it, line)) } }
        is PyDict -> JSONObject().apply { v.entries.forEach { (k, valItem) -> put(k.pyStr(), toJavaObj(valItem, line)) } }
        else -> throw PythonRuntimeException("TypeError", "Object is not JSON serializable", line)
    }

    private fun jsonStringToPyVal(raw: String, line: Int): PyVal = try {
        val t = raw.trim()
        when {
            t.startsWith("{") -> jsonObjToPyDict(JSONObject(t))
            t.startsWith("[") -> jsonArrToPyList(JSONArray(t))
            t == "true" -> PyBool(true)
            t == "false" -> PyBool(false)
            t == "null" -> PyNone
            t.startsWith("\"") && t.endsWith("\"") -> PyStr(t.substring(1, t.length - 1))
            t.toLongOrNull() != null -> PyInt(t.toLong())
            t.toDoubleOrNull() != null -> PyFloat(t.toDouble())
            else -> throw IllegalArgumentException("Invalid JSON")
        }
    } catch (e: Exception) {
        throw PythonRuntimeException("ValueError", "JSONDecodeError: ${e.message}", line)
    }

    private fun jsonObjToPyDict(obj: JSONObject): PyDict {
        val map = linkedMapOf<PyVal, PyVal>()
        obj.keys().forEach { k -> map[PyStr(k)] = wrapJsonAny(obj.get(k)) }
        return PyDict(map)
    }

    private fun jsonArrToPyList(arr: JSONArray): PyList {
        val list = (0 until arr.length()).map { wrapJsonAny(arr.get(it)) }.toMutableList()
        return PyList(list)
    }

    private fun wrapJsonAny(v: Any?): PyVal = when (v) {
        null, JSONObject.NULL -> PyNone
        is Boolean -> PyBool(v)
        is Int -> PyInt(v.toLong())
        is Long -> PyInt(v)
        is Double -> PyFloat(v)
        is String -> PyStr(v)
        is JSONObject -> jsonObjToPyDict(v)
        is JSONArray -> jsonArrToPyList(v)
        else -> PyStr(v.toString())
    }

    private fun installBuiltins() {
        fun reg(name: String, impl: (PythonRuntime, List<PyVal>, Map<String, PyVal>, Int) -> PyVal) {
            globalScope.set(name, PyBuiltinFunc(name, impl))
        }

        reg("print") { _, pos, kw, ln ->
            val sep = kw["sep"]?.pyStr() ?: " "
            val end = kw["end"]?.pyStr() ?: "\n"
            appendOutput(pos.joinToString(sep) { it.pyStr() } + end, ln)
            PyNone
        }

        reg("input") { _, pos, _, ln ->
            val prompt = pos.firstOrNull()?.pyStr().orEmpty()
            if (prompt.isNotEmpty()) appendOutput(prompt, ln)
            val lineVal = if (stdinIdx < stdinLines.size) stdinLines[stdinIdx++] else ""
            PyStr(lineVal)
        }

        reg("len") { _, pos, _, ln ->
            val v = pos.firstOrNull() ?: throw PythonRuntimeException("TypeError", "len() takes 1 argument", ln)
            PyInt(when (v) {
                is PyStr -> v.value.length.toLong()
                is PyList -> v.items.size.toLong()
                is PyTuple -> v.items.size.toLong()
                is PySet -> v.items.size.toLong()
                is PyDict -> v.entries.size.toLong()
                is PyRange -> v.sequence().count().toLong()
                else -> throw PythonRuntimeException("TypeError", "object has no len()", ln)
            })
        }

        reg("range") { _, pos, _, ln ->
            when (pos.size) {
                1 -> PyRange(0L, (pos[0] as PyInt).value, 1L)
                2 -> PyRange((pos[0] as PyInt).value, (pos[1] as PyInt).value, 1L)
                3 -> {
                    val step = (pos[2] as PyInt).value
                    if (step == 0L) throw PythonRuntimeException("ValueError", "range() arg 3 must not be zero", ln)
                    PyRange((pos[0] as PyInt).value, (pos[1] as PyInt).value, step)
                }
                else -> throw PythonRuntimeException("TypeError", "range expected 1 to 3 arguments", ln)
            }
        }

        reg("int") { _, pos, _, ln ->
            if (pos.isEmpty()) PyInt(0L) else when (val v = pos[0]) {
                is PyInt -> v
                is PyFloat -> PyInt(v.value.toLong())
                is PyBool -> PyInt(if (v.value) 1L else 0L)
                is PyStr -> {
                    val base = (pos.getOrNull(1) as? PyInt)?.value?.toInt() ?: 10
                    PyInt(v.value.trim().toLongOrNull(base) ?: throw PythonRuntimeException("ValueError", "invalid literal for int(): '${v.value}'", ln))
                }
                else -> throw PythonRuntimeException("TypeError", "int() argument must be a string or number", ln)
            }
        }

        reg("float") { _, pos, _, ln ->
            if (pos.isEmpty()) PyFloat(0.0) else when (val v = pos[0]) {
                is PyFloat -> v
                is PyInt -> PyFloat(v.value.toDouble())
                is PyBool -> PyFloat(if (v.value) 1.0 else 0.0)
                is PyStr -> PyFloat(v.value.trim().toDoubleOrNull() ?: throw PythonRuntimeException("ValueError", "could not convert string to float: '${v.value}'", ln))
                else -> throw PythonRuntimeException("TypeError", "float() argument invalid", ln)
            }
        }

        reg("str") { _, pos, _, _ -> PyStr(pos.firstOrNull()?.pyStr() ?: "") }
        reg("bool") { _, pos, _, _ -> PyBool(pos.firstOrNull()?.asBool() ?: false) }
        reg("repr") { _, pos, _, _ -> PyStr(pos.firstOrNull()?.pyRepr() ?: "None") }
        reg("list") { _, pos, _, ln -> if (pos.isEmpty()) PyList(mutableListOf()) else PyList(toIterableList(pos[0], ln).toMutableList()) }
        reg("tuple") { _, pos, _, ln -> if (pos.isEmpty()) PyTuple(emptyList()) else PyTuple(toIterableList(pos[0], ln)) }
        reg("set") { _, pos, _, ln -> if (pos.isEmpty()) PySet(linkedSetOf()) else PySet(LinkedHashSet(toIterableList(pos[0], ln))) }
        reg("dict") { _, pos, kw, _ ->
            val map = linkedMapOf<PyVal, PyVal>()
            if (pos.isNotEmpty() && pos[0] is PyDict) map.putAll((pos[0] as PyDict).entries)
            kw.forEach { (k, v) -> map[PyStr(k)] = v }
            PyDict(map)
        }

        reg("abs") { _, pos, _, ln ->
            when (val v = pos.first()) {
                is PyInt -> PyInt(abs(v.value))
                is PyFloat -> PyFloat(abs(v.value))
                else -> throw PythonRuntimeException("TypeError", "bad operand type for abs()", ln)
            }
        }

        reg("round") { _, pos, _, ln ->
            val d = toDouble(pos[0], ln)
            val ndigits = (pos.getOrNull(1) as? PyInt)?.value?.toInt()
            if (ndigits == null) PyInt(round(d).toLong())
            else {
                val factor = 10.0.pow(ndigits)
                PyFloat(round(d * factor) / factor)
            }
        }

        reg("pow") { _, pos, _, ln -> applyBinaryOp(pos[0], "**", pos[1], ln) }

        reg("sum") { _, pos, _, ln ->
            val items = toIterableList(pos[0], ln)
            var acc: PyVal = pos.getOrNull(1) ?: PyInt(0L)
            for (item in items) acc = applyBinaryOp(acc, "+", item, ln)
            acc
        }

        reg("min") { _, pos, _, ln ->
            val items = if (pos.size == 1) toIterableList(pos[0], ln) else pos
            if (items.isEmpty()) throw PythonRuntimeException("ValueError", "min() iterable argument is empty", ln)
            items.reduce { a, b -> if (compareValues(a, b, ln) <= 0) a else b }
        }

        reg("max") { _, pos, _, ln ->
            val items = if (pos.size == 1) toIterableList(pos[0], ln) else pos
            if (items.isEmpty()) throw PythonRuntimeException("ValueError", "max() iterable argument is empty", ln)
            items.reduce { a, b -> if (compareValues(a, b, ln) >= 0) a else b }
        }

        reg("sorted") { rt, pos, kw, ln ->
            val items = toIterableList(pos[0], ln)
            val reverse = kw["reverse"]?.asBool() ?: false
            val keyFn = kw["key"] as? PyCallable
            val out = items.sortedWith { a, b ->
                val ka = keyFn?.call(rt, listOf(a), emptyMap(), ln) ?: a
                val kb = keyFn?.call(rt, listOf(b), emptyMap(), ln) ?: b
                compareValues(ka, kb, ln)
            }
            PyList((if (reverse) out.asReversed() else out).toMutableList())
        }

        reg("reversed") { _, pos, _, ln -> PyList(toIterableList(pos[0], ln).asReversed().toMutableList()) }

        reg("enumerate") { _, pos, _, ln ->
            val start = (pos.getOrNull(1) as? PyInt)?.value ?: 0L
            val items: MutableList<PyVal> = toIterableList(pos[0], ln).mapIndexedTo(mutableListOf()) { idx, v -> PyTuple(listOf(PyInt(start + idx), v)) }
            PyList(items)
        }

        reg("zip") { _, pos, _, ln ->
            val lists = pos.map { toIterableList(it, ln) }
            val minLen = lists.minOfOrNull { it.size } ?: 0
            val zipped: MutableList<PyVal> = (0 until minLen).mapTo(mutableListOf()) { idx -> PyTuple(lists.map { it[idx] }) }
            PyList(zipped)
        }

        reg("map") { rt, pos, _, ln ->
            val fn = pos[0] as? PyCallable ?: throw PythonRuntimeException("TypeError", "map() arg 1 must be callable", ln)
            val items = toIterableList(pos[1], ln).map { fn.call(rt, listOf(it), emptyMap(), ln) }.toMutableList()
            PyList(items)
        }

        reg("filter") { rt, pos, _, ln ->
            val fn = pos[0] as? PyCallable
            val items = toIterableList(pos[1], ln).filter { item ->
                if (fn == null) item.asBool() else fn.call(rt, listOf(item), emptyMap(), ln).asBool()
            }.toMutableList()
            PyList(items)
        }

        reg("any") { _, pos, _, ln -> PyBool(toIterableSequence(pos[0], ln).any { it.asBool() }) }
        reg("all") { _, pos, _, ln -> PyBool(toIterableSequence(pos[0], ln).all { it.asBool() }) }
        reg("chr") { _, pos, _, _ -> PyStr((pos[0] as PyInt).value.toInt().toChar().toString()) }
        reg("ord") { _, pos, _, _ -> PyInt(pos[0].pyStr()[0].code.toLong()) }
        reg("hex") { _, pos, _, _ -> PyStr("0x" + (pos[0] as PyInt).value.toString(16)) }
        reg("bin") { _, pos, _, _ -> PyStr("0b" + (pos[0] as PyInt).value.toString(2)) }

        reg("type") { _, pos, _, _ ->
            val label = when (val v = pos[0]) {
                is PyNone -> "<class 'NoneType'>"
                is PyBool -> "<class 'bool'>"
                is PyInt -> "<class 'int'>"
                is PyFloat -> "<class 'float'>"
                is PyStr -> "<class 'str'>"
                is PyList -> "<class 'list'>"
                is PyTuple -> "<class 'tuple'>"
                is PyDict -> "<class 'dict'>"
                is PySet -> "<class 'set'>"
                is PyInstance -> "<class '${v.klass.name}'>"
                else -> "<class 'object'>"
            }
            PyStr(label)
        }

        reg("isinstance") { _, pos, _, _ ->
            val v = pos[0]
            val t = pos[1]
            val matches = when {
                t is PyClass && v is PyInstance -> v.klass == t || t in v.klass.bases
                t is PyBuiltinFunc -> when (t.name) {
                    "int" -> v is PyInt || v is PyBool
                    "float" -> v is PyFloat
                    "str" -> v is PyStr
                    "bool" -> v is PyBool
                    "list" -> v is PyList
                    "dict" -> v is PyDict
                    "tuple" -> v is PyTuple
                    "set" -> v is PySet
                    else -> false
                }
                else -> false
            }
            PyBool(matches)
        }

        reg("open") { _, _, _, ln ->
            throw PythonRuntimeException("PermissionError", "Direct host file I/O (open) is disabled in PoraStudio Sandboxed Python VM.", ln)
        }

        // Standard exception constructors
        listOf("Exception", "ValueError", "TypeError", "KeyError", "IndexError", "ZeroDivisionError", "RuntimeError", "AssertionError")
            .forEach { excName ->
                globalScope.set(excName, PyClass(excName, emptyList(), mutableMapOf(
                    "__init__" to PyBuiltinFunc("__init__") { _, args, _, _ ->
                        val inst = args[0] as PyInstance
                        inst.fields["message"] = args.getOrNull(1) ?: PyStr("")
                        PyNone
                    }
                )))
            }
    }
}
