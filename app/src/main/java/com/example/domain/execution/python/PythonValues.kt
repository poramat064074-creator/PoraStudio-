package com.example.domain.execution.python

sealed interface PyVal {
    fun asBool(): Boolean = true
    fun pyRepr(): String
    fun pyStr(): String = pyRepr()
}

data object PyNone : PyVal {
    override fun asBool() = false
    override fun pyRepr() = "None"
}

data class PyBool(val value: Boolean) : PyVal {
    override fun asBool() = value
    override fun pyRepr() = if (value) "True" else "False"
}

data class PyInt(val value: Long) : PyVal {
    override fun asBool() = value != 0L
    override fun pyRepr() = value.toString()
}

data class PyFloat(val value: Double) : PyVal {
    override fun asBool() = value != 0.0
    override fun pyRepr() = if (value % 1.0 == 0.0 && value in -1e15..1e15) {
        String.format(java.util.Locale.US, "%.1f", value)
    } else {
        value.toString()
    }
}

data class PyStr(val value: String) : PyVal {
    override fun asBool() = value.isNotEmpty()
    override fun pyRepr() = "'${value.replace("\\", "\\\\").replace("'", "\\'")}'"
    override fun pyStr() = value
}

class PyList(val items: MutableList<PyVal>) : PyVal {
    override fun asBool() = items.isNotEmpty()
    override fun pyRepr() = "[${items.joinToString(", ") { it.pyRepr() }}]"
}

class PyTuple(val items: List<PyVal>) : PyVal {
    override fun asBool() = items.isNotEmpty()
    override fun pyRepr() = if (items.size == 1) "(${items[0].pyRepr()},)" else "(${items.joinToString(", ") { it.pyRepr() }})"
}

class PySet(val items: LinkedHashSet<PyVal>) : PyVal {
    override fun asBool() = items.isNotEmpty()
    override fun pyRepr() = if (items.isEmpty()) "set()" else "{${items.joinToString(", ") { it.pyRepr() }}}"
}

class PyDict(val entries: LinkedHashMap<PyVal, PyVal>) : PyVal {
    override fun asBool() = entries.isNotEmpty()
    override fun pyRepr() = "{${entries.entries.joinToString(", ") { "${it.key.pyRepr()}: ${it.value.pyRepr()}" }}}"
}

data class PyRange(val start: Long, val stop: Long, val step: Long) : PyVal {
    override fun asBool(): Boolean = sequence().any()
    override fun pyRepr() = if (step == 1L) "range($start, $stop)" else "range($start, $stop, $step)"
    fun sequence(): Sequence<Long> = sequence {
        var cur = start
        if (step > 0) {
            while (cur < stop) {
                yield(cur)
                cur += step
            }
        } else if (step < 0) {
            while (cur > stop) {
                yield(cur)
                cur += step
            }
        }
    }
}

data class PySliceVal(val start: Int?, val stop: Int?, val step: Int?) : PyVal {
    override fun pyRepr() = "slice($start, $stop, $step)"
}

interface PyCallable : PyVal {
    fun call(runtime: PythonRuntime, posArgs: List<PyVal>, kwArgs: Map<String, PyVal>, line: Int): PyVal
}

class PyBuiltinFunc(
    val name: String,
    private val impl: (runtime: PythonRuntime, posArgs: List<PyVal>, kwArgs: Map<String, PyVal>, line: Int) -> PyVal
) : PyCallable {
    override fun call(runtime: PythonRuntime, posArgs: List<PyVal>, kwArgs: Map<String, PyVal>, line: Int): PyVal =
        impl(runtime, posArgs, kwArgs, line)
    override fun pyRepr() = "<built-in function $name>"
}

class PyUserFunc(
    val name: String,
    val params: List<PyParam>,
    val body: List<PyStmt>,
    val closure: PyScope,
    val defaults: List<PyVal?>
) : PyCallable {
    override fun call(runtime: PythonRuntime, posArgs: List<PyVal>, kwArgs: Map<String, PyVal>, line: Int): PyVal =
        runtime.invokeUserFunction(this, posArgs, kwArgs, line)
    override fun pyRepr() = "<function $name>"
}

class PyBoundMethod(val instance: PyInstance, val func: PyCallable) : PyCallable {
    override fun call(runtime: PythonRuntime, posArgs: List<PyVal>, kwArgs: Map<String, PyVal>, line: Int): PyVal =
        func.call(runtime, listOf(instance) + posArgs, kwArgs, line)
    override fun pyRepr() = "<bound method of ${instance.klass.name}>"
}

class PyClass(
    val name: String,
    val bases: List<PyClass>,
    val attrs: MutableMap<String, PyVal>
) : PyCallable {
    fun resolveAttr(attr: String): PyVal? {
        attrs[attr]?.let { return it }
        for (base in bases) {
            base.resolveAttr(attr)?.let { return it }
        }
        return null
    }

    override fun call(runtime: PythonRuntime, posArgs: List<PyVal>, kwArgs: Map<String, PyVal>, line: Int): PyVal {
        val inst = PyInstance(this)
        val initFn = resolveAttr("__init__")
        if (initFn is PyCallable) {
            initFn.call(runtime, listOf(inst) + posArgs, kwArgs, line)
        } else if (posArgs.isNotEmpty() || kwArgs.isNotEmpty()) {
            throw PythonRuntimeException("TypeError", "$name() takes no arguments", line)
        }
        return inst
    }

    override fun pyRepr() = "<class '$name'>"
}

class PyInstance(val klass: PyClass) : PyVal {
    val fields = linkedMapOf<String, PyVal>()
    override fun pyRepr() = "<${klass.name} object>"
    override fun pyStr(): String = fields["__str_val__"]?.pyStr() ?: pyRepr()
}

class PyModule(val name: String, val attrs: MutableMap<String, PyVal> = linkedMapOf()) : PyVal {
    override fun pyRepr() = "<module '$name'>"
}

class PyScope(val parent: PyScope? = null) {
    val vars = linkedMapOf<String, PyVal>()
    val globals = mutableSetOf<String>()
    val nonlocals = mutableSetOf<String>()

    fun get(name: String): PyVal? {
        if (name in globals) {
            var root = this
            while (true) {
                root = root.parent ?: break
            }
            return root.vars[name]
        }
        vars[name]?.let { return it }
        return parent?.get(name)
    }

    fun set(name: String, value: PyVal) {
        if (name in globals) {
            var root = this
            while (true) {
                root = root.parent ?: break
            }
            root.vars[name] = value
            return
        }
        if (name in nonlocals) {
            var p = parent
            while (p != null) {
                if (name in p.vars) {
                    p.vars[name] = value
                    return
                }
                p = p.parent
            }
        }
        vars[name] = value
    }

    fun delete(name: String): Boolean {
        if (name in vars) {
            vars.remove(name)
            return true
        }
        return parent?.delete(name) ?: false
    }
}

class PythonRuntimeException(
    val errorType: String,
    val detail: String,
    val line: Int,
    val callFrames: List<String> = emptyList()
) : RuntimeException("$errorType: $detail")

sealed class ControlSignal : RuntimeException(null, null, false, false) {
    data class Return(val value: PyVal) : ControlSignal()
    data object Break : ControlSignal()
    data object Continue : ControlSignal()
    data class Raise(val excType: String, override val message: String, val line: Int) : ControlSignal()
}
