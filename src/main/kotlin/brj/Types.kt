package brj

import brj.TypeException.ArityError
import brj.TypeException.UnificationError

internal data class TypeMapping(val mapping: Map<TypeVarType, MonoType> = emptyMap()) {
    fun applyMapping(mapping: TypeMapping) =
        TypeMapping(
            this.mapping.mapValues { e -> e.value.applyMapping(mapping) }
                .plus(mapping.mapping))

    fun map(tv: TypeVarType) = mapping.getOrDefault(tv, tv)
}

typealias MonoEnv = Map<LocalVar, MonoType>

internal class Instantiator {
    private val mapping: MutableMap<TypeVarType, TypeVarType> = mutableMapOf()

    operator fun invoke(type: MonoType): MonoType {
        return when (type) {
            is TypeVarType -> mapping.getOrPut(type) { TypeVarType() }
            else -> type.fmap(this::invoke)
        }
    }
}

data class Type(val monoType: MonoType) {
    override fun toString() = monoType.toString()

    // TODO check matching
    fun matches(expectedType: Type): Boolean = true
}

sealed class MonoType {
    internal open val javaType: Class<*>? = Object::class.java

    internal open fun unifyEq(other: MonoType): List<TypeEq> =
        if (this.javaClass == other.javaClass) emptyList() else throw UnificationError(this, other)

    protected inline fun <reified T : MonoType> ensure(t: MonoType): T =
        t as? T ?: throw UnificationError(this, t)

    open fun fmap(f: (MonoType) -> MonoType): MonoType = this

    internal open fun applyMapping(mapping: TypeMapping): MonoType = fmap { it.applyMapping(mapping) }
}

object BoolType : MonoType() {
    override val javaType: Class<*>? = Boolean::class.javaPrimitiveType

    override fun toString(): String = "Bool"
}

object StringType : MonoType() {
    override fun toString(): String = "Str"
}

object IntType : MonoType() {
    override val javaType: Class<*>? = Long::class.javaPrimitiveType
    override fun toString(): String = "Int"
}

object BigIntType : MonoType() {
    override fun toString(): String = "BigInt"
}

object FloatType : MonoType() {
    override val javaType: Class<*>? = Double::class.javaPrimitiveType
    override fun toString(): String = "Float"
}

object BigFloatType : MonoType() {
    override fun toString(): String = "BigFloat"
}

class TypeVarType : MonoType() {
    override fun applyMapping(mapping: TypeMapping): MonoType = mapping.mapping.getOrDefault(this, this)

    override fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }

    override fun toString(): String {
        return "TV(${hashCode()})"
    }
}

data class VectorType(val elType: MonoType) : MonoType() {
    override fun unifyEq(other: MonoType): List<TypeEq> = listOf(TypeEq(elType, ensure<VectorType>(other).elType))
    override fun fmap(f: (MonoType) -> MonoType): MonoType = VectorType(f(elType))

    override fun toString(): String = "[$elType]"
}

data class SetType(val elType: MonoType) : MonoType() {
    override fun unifyEq(other: MonoType): List<TypeEq> = listOf(TypeEq(elType, ensure<SetType>(other).elType))
    override fun fmap(f: (MonoType) -> MonoType): MonoType = VectorType(f(elType))

    override fun toString(): String = "#{$elType}"
}

data class FnType(val paramTypes: List<MonoType>, val returnType: MonoType) : MonoType() {
    override fun unifyEq(other: MonoType): List<TypeEq> {
        val otherFnType = ensure<FnType>(other)
        if (paramTypes.size != otherFnType.paramTypes.size) throw UnificationError(this, other)

        return paramTypes.zip(otherFnType.paramTypes, ::TypeEq)
            .plus(TypeEq(returnType, otherFnType.returnType))
    }

    override fun fmap(f: (MonoType) -> MonoType): MonoType = FnType(paramTypes.map(f), f(returnType))

    override fun toString(): String = "(Fn [${paramTypes.joinToString(separator = " ")}] $returnType)"
}

data class DataTypeType(val dataType: DataType) : MonoType() {
    override fun unifyEq(other: MonoType): List<TypeEq> =
        if (this == other) emptyList() else throw UnificationError(this, other)

    override fun toString(): String = dataType.toString()
}

data class AppliedType(val type: MonoType, val params: List<MonoType>) : MonoType() {
    override fun unifyEq(other: MonoType): List<TypeEq> {
        val otherAppliedType = ensure<AppliedType>(other)
        if (this.params.size != otherAppliedType.params.size) throw UnificationError(this, other)

        return params.zip(otherAppliedType.params, ::TypeEq)
            .plus(TypeEq(this.type, otherAppliedType.type))
    }

    override fun fmap(f: (MonoType) -> MonoType): MonoType =
        AppliedType(f(type), params.map(f))

    override fun toString(): String = "($type ${params.joinToString(" ")})"
}

sealed class TypeException : Exception() {
    data class UnificationError(val t1: MonoType, val t2: MonoType) : TypeException()
    data class ExpectedFunction(val expr: ValueExpr, val type: MonoType) : TypeException()
    data class ArityError(val fnType: FnType, val argExprs: List<ValueExpr>) : TypeException()
}

typealias TypeEq = Pair<MonoType, MonoType>

private fun unifyEqs(eqs_: List<TypeEq>): TypeMapping {
    var eqs = eqs_
    var mapping = TypeMapping()

    while (eqs.isNotEmpty()) {
        val eq = eqs.first()
        eqs = eqs.drop(1)

        val (t1, t2) = if (eq.second is TypeVarType) (eq.second to eq.first) else (eq.first to eq.second)

        if (t1 == t2) {
            continue
        }

        if (t1 is TypeVarType) {
            val newMapping = TypeMapping(mapOf(t1 to t2))
            mapping = mapping.applyMapping(newMapping)
            eqs = eqs.map { TypeEq(it.first.applyMapping(newMapping), it.second.applyMapping(newMapping)) }
            continue
        }

        eqs = t1.unifyEq(t2).plus(eqs)

    }

    return mapping
}

internal data class Typing(val monoType: MonoType, val monoEnv: MonoEnv = emptyMap())

internal class TypeChecker(val env: Env) {

    private fun combine(
        returnType: MonoType,
        typings: Iterable<Typing> = emptyList(),
        extraEqs: Iterable<TypeEq> = emptyList(),
        extraLVs: Iterable<Pair<LocalVar, MonoType>> = emptyList()
    ): Typing {

        val lvTvs: MutableMap<LocalVar, TypeVarType> = mutableMapOf()

        val mapping = unifyEqs(
            typings
                .flatMapTo(extraLVs.toMutableList()) { it.monoEnv.toList() }
                .mapTo(extraEqs.toMutableList()) { e -> TypeEq(lvTvs.getOrPut(e.first, ::TypeVarType), e.second) })

        return Typing(returnType.applyMapping(mapping), lvTvs.mapValuesTo(mutableMapOf()) { e -> mapping.map(e.value) })
    }

    private fun collExprTyping(mkCollType: (MonoType) -> MonoType, exprs: List<ValueExpr>): Typing {
        val typings = exprs.map(::valueExprTyping)
        val returnType = TypeVarType()

        return combine(mkCollType(returnType), extraEqs = typings.map { it.monoType to returnType })
    }

    private fun ifExprTyping(expr: IfExpr): Typing {
        val predExprTyping = valueExprTyping(expr.predExpr)
        val thenExprTyping = valueExprTyping(expr.thenExpr)
        val elseExprTyping = valueExprTyping(expr.elseExpr)

        val returnType = TypeVarType()

        return combine(returnType,
            extraEqs = listOf(
                BoolType to predExprTyping.monoType,
                returnType to thenExprTyping.monoType,
                returnType to elseExprTyping.monoType))
    }

    private fun letExprTyping(expr: LetExpr): Typing {
        val bindingTypings = expr.bindings.map { it.localVar to valueExprTyping(it.expr) }
        val exprTyping = valueExprTyping(expr.expr)

        return combine(exprTyping.monoType,
            typings = bindingTypings.map { it.second } + exprTyping,
            extraLVs = bindingTypings.map { it.first to it.second.monoType })
    }

    private fun doExprTyping(expr: DoExpr): Typing {
        val exprTypings = expr.exprs.map(::valueExprTyping)
        val exprTyping = valueExprTyping(expr.expr)

        return combine(exprTyping.monoType, exprTypings + exprTyping)
    }

    private fun loopExprTyping(expr: LoopExpr): Typing {
        val bindingTypings = expr.bindings.map { it to valueExprTyping(it.expr) }

        val bodyTyping = valueExprTyping(expr.expr)

        return combine(bodyTyping.monoType,
            typings = bindingTypings.map { it.second } + bodyTyping,
            extraLVs = bindingTypings.map { it.first.localVar to it.second.monoType })
    }

    private fun recurExprTyping(expr: RecurExpr): Typing {
        val exprTypings = expr.exprs.map { valueExprTyping(it.second) }

        return combine(TypeVarType(), exprTypings,
            extraLVs = expr.exprs.map { it.first }.zip(exprTypings.map(Typing::monoType)))
    }

    private fun fnExprTyping(expr: FnExpr): Typing {
        val params = expr.params.map { it to TypeVarType() }
        val exprTyping = valueExprTyping(expr.expr)

        return combine(FnType(params.map { it.second }, exprTyping.monoType), listOf(exprTyping), extraLVs = params)
    }

    private fun callExprTyping(expr: CallExpr): Typing {
        val fnExpr = expr.f
        val argExprs = expr.args

        val fnExprTyping = valueExprTyping(fnExpr)

        val fnExprType = fnExprTyping.monoType.let {
            it as? FnType ?: throw TypeException.ExpectedFunction(fnExpr, it)
        }

        if (fnExprType.paramTypes.size != argExprs.size) throw ArityError(fnExprType, argExprs)

        val argTypings = argExprs.map(this::valueExprTyping)

        return combine(fnExprType.returnType,
            typings = argTypings + fnExprTyping,
            extraEqs = fnExprType.paramTypes.zip(argTypings.map(Typing::monoType)))
    }

    private fun localVarTyping(lv: LocalVar): Typing {
        val typeVar = TypeVarType()
        return combine(typeVar, extraLVs = listOf(lv to typeVar))
    }

    private fun caseExprTyping(expr: CaseExpr): Typing {
        val returnType = TypeVarType()

        val exprTyping = valueExprTyping(expr.expr)

        val clauseTypings = expr.clauses.map { clause ->
            if (clause.constructor.paramTypes?.size != clause.bindings?.size) TODO()
            clause to valueExprTyping(clause.bodyExpr)
        }


        val defaultTyping = expr.defaultExpr?.let { valueExprTyping(it) }

        return combine(returnType,
            typings = (clauseTypings.map { it.second } + exprTyping + defaultTyping).filterNotNull(),
            extraEqs = (
                clauseTypings.map { exprTyping.monoType to it.first.constructor.dataType.monoType }
                    + clauseTypings.map { returnType to it.second.monoType }
                    + defaultTyping?.let { returnType to it.monoType }).filterNotNull(),
            extraLVs = clauseTypings.flatMap { (clause, _) ->
                if (clause.constructor.paramTypes != null && clause.bindings != null)
                    clause.bindings.zip(clause.constructor.paramTypes)
                else emptyList()
            }
        )
    }

    internal fun valueExprTyping(expr: ValueExpr): Typing =
        when (expr) {
            is BooleanExpr -> Typing(BoolType)
            is StringExpr -> Typing(StringType)
            is IntExpr -> Typing(IntType)
            is BigIntExpr -> Typing(BigIntType)
            is FloatExpr -> Typing(FloatType)
            is BigFloatExpr -> Typing(BigFloatType)

            is VectorExpr -> collExprTyping(::VectorType, expr.exprs)
            is SetExpr -> collExprTyping(::SetType, expr.exprs)

            is FnExpr -> fnExprTyping(expr)
            is CallExpr -> callExprTyping(expr)

            is IfExpr -> ifExprTyping(expr)
            is LetExpr -> letExprTyping(expr)
            is DoExpr -> doExprTyping(expr)

            is LoopExpr -> loopExprTyping(expr)
            is RecurExpr -> recurExprTyping(expr)

            is LocalVarExpr -> localVarTyping(expr.localVar)
            is GlobalVarExpr -> Typing(Instantiator()(expr.globalVar.type.monoType))

            is CaseExpr -> caseExprTyping(expr)
        }
}

fun valueExprType(env: Env, expr: ValueExpr) = Type(TypeChecker(env).valueExprTyping(expr).monoType)
