package brj.types

import brj.analyser.*
import brj.runtime.QSymbol
import brj.runtime.RecordKey
import brj.runtime.VariantKey
import java.util.*

internal typealias RowTypeMapping<K> = Map<RowTypeVar, Pair<Map<K, RowKey>, RowTypeVar>>

internal data class Mapping(val typeMapping: Map<TypeVarType, MonoType> = emptyMap(),
                            val recordMapping: RowTypeMapping<RecordKey> = emptyMap(),
                            val variantMapping: RowTypeMapping<VariantKey> = emptyMap()) {
    fun applyMapping(mapping: Mapping) =
        Mapping(
            this.typeMapping
                .mapValues { e -> e.value.applyMapping(mapping) }
                .plus(mapping.typeMapping),

            this.recordMapping
                .mapValues {
                    val (ks, tv) = it.value
                    mapping.recordMapping[tv]?.let { (moreKs, newTv) -> Pair(ks + moreKs, newTv) } ?: it.value
                }
                .plus(mapping.recordMapping),

            this.variantMapping
                .mapValues {
                    val (ks, tv) = it.value
                    mapping.variantMapping[tv]?.let { (moreKs, newTv) -> Pair(ks + moreKs, newTv) } ?: it.value
                }
                .plus(mapping.variantMapping))
}

internal typealias MonoEnv = Map<LocalVar, MonoType>

internal class Instantiator {
    private val tvMapping = mutableMapOf<TypeVarType, TypeVarType>()

    fun instantiate(type: TypeVarType): TypeVarType = tvMapping.getOrPut(type, ::TypeVarType)

    fun instantiate(type: MonoType): MonoType =
        when (type) {
            is TypeVarType -> instantiate(type)
            else -> type.fmap(this::instantiate)
        }
}

internal sealed class TypeException : Exception() {
    data class UnificationError(val t1: MonoType, val t2: MonoType) : TypeException()
    data class ExpectedFunction(val expr: ValueExpr, val type: MonoType) : TypeException()
    data class ArityError(val fnType: FnType, val argExprs: List<ValueExpr>) : TypeException()
}

internal typealias TypeEq = Pair<MonoType, MonoType>

internal data class Unification(val typeEqs: List<TypeEq> = emptyList(),
                                val recordEqs: RowTypeMapping<RecordKey> = emptyMap(),
                                val variantEqs: RowTypeMapping<VariantKey> = emptyMap())

private fun unifyEqs(eqs_: List<TypeEq>): Mapping {
    val eqs = LinkedList(eqs_)
    var mapping = Mapping()

    while (eqs.isNotEmpty()) {
        val eq = eqs.pop()

        val (t1, t2) = when (eq.second) {
            is TypeVarType -> eq.second to eq.first
            is TypeAliasType -> eq.second to eq.first
            else -> eq
        }

        if (t1 == t2) {
            continue
        }

        if (t1 is TypeVarType) {
            val newMapping = Mapping(mapOf(t1 to t2))
            mapping = mapping.applyMapping(newMapping)
            eqs.replaceAll { TypeEq(it.first.applyMapping(newMapping), it.second.applyMapping(newMapping)) }
            continue
        }

        val unification = t1.unifyEq(t2)

        eqs += unification.typeEqs
        mapping = mapping.applyMapping(
            Mapping(
                recordMapping = unification.recordEqs,
                variantMapping = unification.variantEqs))
    }

    return mapping
}

private data class Typing(val monoType: MonoType,
                          val monoEnv: MonoEnv = emptyMap(),
                          val effects: Set<QSymbol> = emptySet())

private fun combine(returnType: MonoType,
                    typings: Iterable<Typing> = emptyList(),
                    extraEqs: Iterable<TypeEq> = emptyList(),
                    extraLVs: Iterable<Pair<LocalVar, MonoType>> = emptyList()
): Typing {

    val lvTvs: MutableMap<LocalVar, TypeVarType> = mutableMapOf()

    val mapping = unifyEqs(
        typings
            .flatMapTo(extraLVs.toMutableList()) { it.monoEnv.toList() }
            .mapTo(extraEqs.toMutableList()) { e -> TypeEq(lvTvs.getOrPut(e.first, ::TypeVarType), e.second) })

    return Typing(
        returnType.applyMapping(mapping),
        lvTvs.mapValues { e -> mapping.typeMapping.getOrDefault(e.value, e.value) },
        typings.flatMapTo(mutableSetOf()) { it.effects }
    )
}

private fun vectorExprTyping(expr: VectorExpr, expectedType: MonoType?): Typing {
    val typings = expr.exprs.map { valueExprTyping(it, (expectedType as? VectorType)?.elType) }
    val returnType = TypeVarType()
    val vectorType = VectorType(returnType)

    return combine(vectorType, typings, extraEqs = typings.map { it.monoType to returnType } + listOfNotNull(expectedType?.let { it to vectorType }))
}

private fun setExprTyping(expr: SetExpr, expectedType: MonoType?): Typing {
    val typings = expr.exprs.map { valueExprTyping(it, (expectedType as? SetType)?.elType) }
    val returnType = TypeVarType()
    val setType = SetType(returnType)

    return combine(setType, typings, extraEqs = typings.map { it.monoType to returnType } + listOfNotNull(expectedType?.let { it to setType }))
}

private fun recordExprTyping(expr: RecordExpr, expectedType: MonoType?): Typing {
    val typings = expr.entries.map { valueExprTyping(it.expr, it.recordKey.type) }

    val recordType = RecordType(
        expr.entries.map { it.recordKey }.associateWith { RowKey(it.typeVars) },
        RowTypeVar(false))

    val extraEqs = when (expectedType) {
        null -> emptyList()
        is RecordType -> {
            val missingKeys = expectedType.hasKeys.keys - expr.entries.map { it.recordKey }.toSet()
            if (missingKeys.isNotEmpty()) {
                TODO("missing keys: $missingKeys")
            }
            emptyList()
        }
        else -> listOf(TypeEq(expectedType, recordType))
    }

    return combine(recordType, typings, extraEqs = extraEqs)
}

private fun ifExprTyping(expr: IfExpr, expectedType: MonoType?): Typing {
    val predExprTyping = valueExprTyping(expr.predExpr, BoolType)
    val thenExprTyping = valueExprTyping(expr.thenExpr, expectedType)
    val elseExprTyping = valueExprTyping(expr.elseExpr, expectedType)

    val returnType = TypeVarType()

    return combine(returnType,
        typings = listOf(predExprTyping, thenExprTyping, elseExprTyping),
        extraEqs = listOf(
            returnType to thenExprTyping.monoType,
            returnType to elseExprTyping.monoType))
}

private fun letExprTyping(expr: LetExpr, expectedType: MonoType?): Typing {
    val bindingTypings = expr.bindings.map { it.localVar to valueExprTyping(it.expr) }
    val exprTyping = valueExprTyping(expr.expr, expectedType)

    return combine(exprTyping.monoType,
        typings = bindingTypings.map { it.second } + exprTyping,
        extraLVs = bindingTypings.map { it.first to it.second.monoType })
}

private fun doExprTyping(expr: DoExpr, expectedType: MonoType?): Typing {
    val exprTypings = expr.exprs.map { valueExprTyping(it) }
    val exprTyping = valueExprTyping(expr.expr, expectedType)

    return combine(exprTyping.monoType, exprTypings + exprTyping)
}

private fun loopExprTyping(expr: LoopExpr, expectedType: MonoType?): Typing {
    val bindingTypings = expr.bindings.map { it to valueExprTyping(it.expr) }

    val bodyTyping = valueExprTyping(expr.expr, expectedType)

    return combine(bodyTyping.monoType,
        typings = bindingTypings.map { it.second } + bodyTyping,
        extraLVs = bindingTypings.map { it.first.localVar to it.second.monoType })
}

private fun recurExprTyping(expr: RecurExpr): Typing {
    val exprTypings = expr.exprs.map { valueExprTyping(it.second) }

    return combine(TypeVarType(), exprTypings,
        extraLVs = expr.exprs.map { it.first }.zip(exprTypings.map(Typing::monoType)))
}

private fun fnExprTyping(expr: FnExpr, expectedType: MonoType?): Typing {
    val params = expr.params.map { it to TypeVarType() }
    val exprTyping = valueExprTyping(expr.expr)

    val combinedTyping = combine(FnType(params.map { it.second }, exprTyping.monoType), listOf(exprTyping), extraLVs = params)

    // TODO needs to actually check the types
    return if (expectedType != null) combinedTyping.copy(monoType = expectedType) else combinedTyping
}

private fun callExprTyping(expr: CallExpr, expectedType: MonoType?): Typing {
    val fnExpr = expr.f
    val argExprs = expr.args

    val argTVs = generateSequence { TypeVarType() }.take(argExprs.size).toList()
    val returnType = expectedType ?: TypeVarType()

    val fnExprTyping = valueExprTyping(fnExpr, FnType(argTVs, returnType))
    val fnExprType = fnExprTyping.monoType.let { monoType ->
        monoType as? FnType ?: throw TypeException.ExpectedFunction(fnExpr, monoType)
    }

    val argTypings = argExprs.zip(fnExprType.paramTypes).map { valueExprTyping(it.first, it.second) }

    return combine(fnExprType.returnType, typings = argTypings + fnExprTyping)
}

private fun localVarTyping(lv: LocalVar, expectedType: MonoType?): Typing {
    val typeVar = expectedType ?: TypeVarType()
    return combine(typeVar, extraLVs = listOf(lv to typeVar))
}

private fun globalVarTyping(expr: GlobalVarExpr): Typing {
    val instantiator = Instantiator()
    return expr.globalVar.type.let { Typing(instantiator.instantiate(it.monoType), effects = it.effects) }
}

private fun withFxTyping(expr: WithFxExpr, expectedType: MonoType?): Typing {
    val fxTypings = expr.fx.map { it.effectVar to valueExprTyping(it.fnExpr, it.effectVar.type.monoType) }

    val bodyExprTyping = valueExprTyping(expr.bodyExpr, expectedType)

    val combinedTyping = combine(bodyExprTyping.monoType,
        fxTypings.map { it.second }.plus(bodyExprTyping))

    val effects = combinedTyping.effects
        .minus((fxTypings.map { it.first.sym }))
        .plus(fxTypings.flatMap { it.second.effects })

    return combinedTyping.copy(effects = effects)
}

private fun caseExprTyping(expr: CaseExpr, expectedType: MonoType?): Typing {
    val returnType = TypeVarType()

    val exprTyping = valueExprTyping(expr.expr)

    val clauseTypings = expr.clauses.map { clause ->
        if (clause.variantKey.paramTypes.size != clause.bindings.size) TODO()
        clause to valueExprTyping(clause.bodyExpr, expectedType)
    }

    val defaultTyping = expr.defaultExpr?.let { valueExprTyping(it, expectedType) }

    val instantiator = Instantiator()

    val variantKeys = clauseTypings.map { it.first.variantKey }.associateWith { RowKey(it.typeVars) }
    val variantType = instantiator.instantiate(VariantType(variantKeys, RowTypeVar(defaultTyping != null)))

    return combine(returnType,
        typings = (clauseTypings.map { it.second } + exprTyping + defaultTyping).filterNotNull(),
        extraEqs = (
            clauseTypings.map { returnType to it.second.monoType }
                + (exprTyping.monoType to variantType)
                + defaultTyping?.let { returnType to it.monoType }).filterNotNull(),
        extraLVs = clauseTypings.flatMap { (clause, _) -> clause.bindings.zip(clause.variantKey.paramTypes.map { instantiator.instantiate(it) }) }
    )
}

private fun primitiveExprTyping(actualType: MonoType, expectedType: MonoType?) =
    if (expectedType == null) Typing(actualType) else combine(expectedType, extraEqs = listOf(TypeEq(expectedType, actualType)))

private fun valueExprTyping(expr: ValueExpr, expectedType: MonoType? = null): Typing =
    when (expr) {
        is BooleanExpr -> primitiveExprTyping(BoolType, expectedType)
        is StringExpr -> primitiveExprTyping(StringType, expectedType)
        is IntExpr -> primitiveExprTyping(IntType, expectedType)
        is BigIntExpr -> primitiveExprTyping(BigIntType, expectedType)
        is FloatExpr -> primitiveExprTyping(FloatType, expectedType)
        is BigFloatExpr -> primitiveExprTyping(BigFloatType, expectedType)

        is QuotedSymbolExpr -> primitiveExprTyping(SymbolType, expectedType)
        is QuotedQSymbolExpr -> primitiveExprTyping(QSymbolType, expectedType)

        is VectorExpr -> vectorExprTyping(expr, expectedType)
        is SetExpr -> setExprTyping(expr, expectedType)

        is RecordExpr -> recordExprTyping(expr, expectedType)

        is FnExpr -> fnExprTyping(expr, expectedType)
        is CallExpr -> callExprTyping(expr, expectedType)

        is IfExpr -> ifExprTyping(expr, expectedType)
        is LetExpr -> letExprTyping(expr, expectedType)
        is DoExpr -> doExprTyping(expr, expectedType)

        is LoopExpr -> loopExprTyping(expr, expectedType)
        is RecurExpr -> recurExprTyping(expr)

        is LocalVarExpr -> localVarTyping(expr.localVar, expectedType)
        is GlobalVarExpr -> globalVarTyping(expr)

        is WithFxExpr -> withFxTyping(expr, expectedType)

        is CaseExpr -> caseExprTyping(expr, expectedType)
    }

internal fun valueExprType(expr: ValueExpr, expectedType: MonoType?): Type {
    val valueExprTyping = valueExprTyping(expr, expectedType)
    return Type(valueExprTyping.monoType, valueExprTyping.effects)
}
