package com.example.domain.execution.python

sealed interface PyExpr {
    val line: Int
}

sealed interface PyStmt {
    val line: Int
}

data class PyParam(
    val name: String,
    val defaultExpr: PyExpr? = null,
    val isVarArg: Boolean = false,
    val isKwVarArg: Boolean = false
)

data class PyArg(
    val name: String? = null,
    val value: PyExpr,
    val isStar: Boolean = false,
    val isDoubleStar: Boolean = false
)

data class PyCompClause(
    val target: PyTarget,
    val iter: PyExpr,
    val conditions: List<PyExpr> = emptyList()
)

sealed interface PyTarget {
    data class Name(val id: String) : PyTarget
    data class TupleOrList(val items: List<PyTarget>) : PyTarget
    data class Starred(val target: PyTarget) : PyTarget
    data class Attr(val obj: PyExpr, val attr: String) : PyTarget
    data class Subscript(val obj: PyExpr, val index: PyExpr) : PyTarget
}

// Expressions
data class PyIntLit(val value: Long, override val line: Int) : PyExpr
data class PyFloatLit(val value: Double, override val line: Int) : PyExpr
data class PyStringLit(val value: String, override val line: Int) : PyExpr
data class PyFStringExpr(val rawTemplate: String, override val line: Int) : PyExpr
data class PyBoolLit(val value: Boolean, override val line: Int) : PyExpr
data class PyNoneLit(override val line: Int) : PyExpr
data class PyIdent(val name: String, override val line: Int) : PyExpr
data class PyListExpr(val items: List<PyExpr>, override val line: Int) : PyExpr
data class PyTupleExpr(val items: List<PyExpr>, override val line: Int) : PyExpr
data class PySetExpr(val items: List<PyExpr>, override val line: Int) : PyExpr
data class PyDictExpr(val entries: List<Pair<PyExpr?, PyExpr>>, override val line: Int) : PyExpr
data class PyListCompExpr(val elt: PyExpr, val clauses: List<PyCompClause>, override val line: Int) : PyExpr
data class PySetCompExpr(val elt: PyExpr, val clauses: List<PyCompClause>, override val line: Int) : PyExpr
data class PyDictCompExpr(val key: PyExpr, val value: PyExpr, val clauses: List<PyCompClause>, override val line: Int) : PyExpr
data class PyStarredExpr(val inner: PyExpr, override val line: Int) : PyExpr
data class PyUnaryOp(val op: String, val operand: PyExpr, override val line: Int) : PyExpr
data class PyBinaryOp(val left: PyExpr, val op: String, val right: PyExpr, override val line: Int) : PyExpr
data class PyCompareExpr(val first: PyExpr, val opsAndRights: List<Pair<String, PyExpr>>, override val line: Int) : PyExpr
data class PyTernaryExpr(val body: PyExpr, val cond: PyExpr, val orelse: PyExpr, override val line: Int) : PyExpr
data class PyWalrusExpr(val name: String, val value: PyExpr, override val line: Int) : PyExpr
data class PyLambdaExpr(val params: List<PyParam>, val body: PyExpr, override val line: Int) : PyExpr
data class PyCallExpr(val callee: PyExpr, val args: List<PyArg>, override val line: Int) : PyExpr
data class PyAttrExpr(val obj: PyExpr, val attr: String, override val line: Int) : PyExpr
data class PySubscriptExpr(val obj: PyExpr, val index: PyExpr, override val line: Int) : PyExpr
data class PySliceExpr(val start: PyExpr?, val stop: PyExpr?, val step: PyExpr?, override val line: Int) : PyExpr

// Statements
data class PyExprStmt(val expr: PyExpr, override val line: Int) : PyStmt
data class PyAssignStmt(val targets: List<PyTarget>, val value: PyExpr, override val line: Int) : PyStmt
data class PyAugAssignStmt(val target: PyTarget, val op: String, val value: PyExpr, override val line: Int) : PyStmt
data class PyIfStmt(
    val branches: List<Pair<PyExpr, List<PyStmt>>>,
    val elseBody: List<PyStmt>,
    override val line: Int
) : PyStmt
data class PyWhileStmt(val cond: PyExpr, val body: List<PyStmt>, val elseBody: List<PyStmt>, override val line: Int) : PyStmt
data class PyForStmt(val target: PyTarget, val iter: PyExpr, val body: List<PyStmt>, val elseBody: List<PyStmt>, override val line: Int) : PyStmt
data class PyDefStmt(
    val name: String,
    val params: List<PyParam>,
    val body: List<PyStmt>,
    val decorators: List<PyExpr>,
    override val line: Int
) : PyStmt
data class PyClassStmt(
    val name: String,
    val bases: List<PyExpr>,
    val body: List<PyStmt>,
    val decorators: List<PyExpr>,
    override val line: Int
) : PyStmt
data class PyReturnStmt(val value: PyExpr?, override val line: Int) : PyStmt
data class PyPassStmt(override val line: Int) : PyStmt
data class PyBreakStmt(override val line: Int) : PyStmt
data class PyContinueStmt(override val line: Int) : PyStmt
data class PyRaiseStmt(val expr: PyExpr?, override val line: Int) : PyStmt
data class PyAssertStmt(val test: PyExpr, val msg: PyExpr?, override val line: Int) : PyStmt
data class PyDelStmt(val targets: List<PyTarget>, override val line: Int) : PyStmt
data class PyGlobalStmt(val names: List<String>, override val line: Int) : PyStmt
data class PyNonlocalStmt(val names: List<String>, override val line: Int) : PyStmt
data class PyExceptHandler(val excType: PyExpr?, val alias: String?, val body: List<PyStmt>)
data class PyTryStmt(
    val body: List<PyStmt>,
    val handlers: List<PyExceptHandler>,
    val elseBody: List<PyStmt>,
    val finallyBody: List<PyStmt>,
    override val line: Int
) : PyStmt
data class PyImportStmt(val names: List<Pair<String, String?>>, override val line: Int) : PyStmt
data class PyFromImportStmt(val module: String, val names: List<Pair<String, String?>>, override val line: Int) : PyStmt
