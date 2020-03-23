package brj.types

import brj.analyser.*
import brj.runtime.QSymbol
import brj.runtime.RecordKey
import brj.runtime.VariantKey
import java.util.*

internal typealias RowTypeMapping<K> = Map<RowTypeVar, Pair<Map<K, RowKey>, RowTypeVar>>

internal data class Mapping(val typeMapping: Map<TypeVarType, MonoType> = emptyMap(),
                            val variantMapping: RowTypeMapping<VariantKey> = emptyMap()) {
    fun applyMapping(mapping: Mapping) =
        Mapping(
            this.typeMapping
                .mapValues { e -> e.value.applyMapping(mapping) }
                .plus(mapping.typeMapping),

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
}

internal typealias TypeEq = Pair<MonoType, MonoType>

internal data class Unification(val typeEqs: List<TypeEq> = emptyList(),
                                val variantEqs: RowTypeMapping<VariantKey> = emptyMap())

internal fun unifyEqs(eqs_: List<TypeEq>): Mapping {
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
    }

    return mapping
}

internal data class Typing(val monoType: MonoType,
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

private fun vectorExprTyping(expr: VectorExpr): Typing {
    val typings = expr.exprs.map { valueExprTyping(it) }
    val returnType = TypeVarType()
    val vectorType = VectorType(returnType)

    return combine(vectorType, typings,
        extraEqs = typings.map { it.monoType to returnType })
}

private fun setExprTyping(expr: SetExpr): Typing {
    val typings = expr.exprs.map { valueExprTyping(it) }
    val returnType = TypeVarType()
    val setType = SetType(returnType)

    return combine(setType, typings,
        extraEqs = typings.map { it.monoType to returnType })
}

private fun recordExprTyping(expr: RecordExpr): Typing {
    val instantiator = Instantiator()

    data class InstantiatedEntry(val recordKey: RecordKey, val expr: ValueExpr, val typeVars: List<MonoType>, val type: MonoType)

    val entries = expr.entries.map { entry ->
        val recordKey = entry.recordKey

        val typeVars = recordKey.typeVars

        val tvMapping = Mapping(typeVars.associateWith(instantiator::instantiate))

        InstantiatedEntry(recordKey, entry.expr,
            recordKey.typeVars.map { it.applyMapping(tvMapping) },
            recordKey.type.applyMapping(tvMapping))
    }

    val typings = entries.map { Pair(it, valueExprTyping(it.expr)) }

    return combine(
        returnType = RecordType(
            hasKeys = entries.map { it.recordKey }.toSet(),
            keyTypes = entries.associate { it.recordKey to it.typeVars },
            typeVar = TypeVarType()),
        typings = typings.map { it.second },
        extraEqs = typings.map { it.first.type to it.second.monoType })
}

private fun ifExprTyping(expr: IfExpr): Typing {
    val predExprTyping = valueExprTyping(expr.predExpr)
    val thenExprTyping = valueExprTyping(expr.thenExpr)
    val elseExprTyping = valueExprTyping(expr.elseExpr)

    val returnType = TypeVarType()

    return combine(returnType,
        typings = listOf(predExprTyping, thenExprTyping, elseExprTyping),
        extraEqs = listOf(
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
    val exprTypings = expr.exprs.map { valueExprTyping(it) }
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

    val argTVs = generateSequence { TypeVarType() }.take(expr.args.size).toList()
    val returnType = TypeVarType()
    val fnType = FnType(argTVs, returnType)

    val fnExprTyping = valueExprTyping(fnExpr)

    val argTypings = argExprs.map { valueExprTyping(it) }

    return combine(fnType.returnType,
        typings = argTypings + fnExprTyping,
        extraEqs = listOf(TypeEq(fnType, fnExprTyping.monoType)) +
            argTypings.map { it.monoType }.zip(fnType.paramTypes)
    )
}

private fun localVarTyping(expr: LocalVarExpr): Typing {
    val typeVar = TypeVarType()
    return combine(typeVar, extraLVs = listOf(expr.localVar to typeVar))
}

private fun globalVarTyping(expr: GlobalVarExpr): Typing {
    val instantiator = Instantiator()
    val globalVarType = expr.globalVar.type
    return Typing(instantiator.instantiate(globalVarType.monoType), effects = globalVarType.effects)
}

private fun withFxTyping(expr: WithFxExpr): Typing {
    val fxTypings = expr.fx.map { it.effectVar to valueExprTyping(it.fnExpr) }

    val bodyExprTyping = valueExprTyping(expr.bodyExpr)

    val combinedTyping = combine(bodyExprTyping.monoType,
        fxTypings.map { it.second }.plus(bodyExprTyping))

    val effects = combinedTyping.effects
        .minus((fxTypings.map { it.first.sym }))
        .plus(fxTypings.flatMap { it.second.effects })

    return combinedTyping.copy(effects = effects)
}

private fun caseExprTyping(expr: CaseExpr): Typing {
    val returnType = TypeVarType()

    val exprTyping = valueExprTyping(expr.expr)

    val clauseTypings = expr.clauses.map { clause ->
        if (clause.variantKey.paramTypes.size != clause.bindings.size) TODO()
        clause to valueExprTyping(clause.bodyExpr)
    }

    val defaultTyping = expr.defaultExpr?.let { valueExprTyping(it) }

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

private fun primitiveExprTyping(actualType: MonoType) = Typing(actualType)

internal fun valueExprTyping(expr: ValueExpr): Typing =
    when (expr) {
        is BooleanExpr -> primitiveExprTyping(BoolType)
        is StringExpr -> primitiveExprTyping(StringType)
        is IntExpr -> primitiveExprTyping(IntType)
        is BigIntExpr -> primitiveExprTyping(BigIntType)
        is FloatExpr -> primitiveExprTyping(FloatType)
        is BigFloatExpr -> primitiveExprTyping(BigFloatType)

        is QuotedSymbolExpr -> primitiveExprTyping(SymbolType)
        is QuotedQSymbolExpr -> primitiveExprTyping(QSymbolType)

        is VectorExpr -> vectorExprTyping(expr)
        is SetExpr -> setExprTyping(expr)

        is RecordExpr -> recordExprTyping(expr)

        is FnExpr -> fnExprTyping(expr)
        is CallExpr -> callExprTyping(expr)

        is IfExpr -> ifExprTyping(expr)
        is LetExpr -> letExprTyping(expr)
        is DoExpr -> doExprTyping(expr)

        is LoopExpr -> loopExprTyping(expr)
        is RecurExpr -> recurExprTyping(expr)

        is LocalVarExpr -> localVarTyping(expr)
        is GlobalVarExpr -> globalVarTyping(expr)

        is WithFxExpr -> withFxTyping(expr)

        is CaseExpr -> caseExprTyping(expr)
    }

internal fun valueExprType(expr: ValueExpr): Type {
    val valueExprTyping = valueExprTyping(expr)
    return Type(valueExprTyping.monoType, valueExprTyping.effects)
}
