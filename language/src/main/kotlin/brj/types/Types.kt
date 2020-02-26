package brj.types

import brj.runtime.*
import brj.runtime.SymKind.ID
import brj.runtime.SymKind.TYPE
import brj.types.TypeException.UnificationError

internal val STR = Symbol(TYPE, "Str")
internal val BOOL = Symbol(TYPE, "Bool")
internal val INT = Symbol(TYPE, "Int")
internal val FLOAT = Symbol(TYPE, "Float")
internal val BIG_INT = Symbol(TYPE, "BigInt")
internal val BIG_FLOAT = Symbol(TYPE, "BigFloat")
internal val SYMBOL = Symbol(TYPE, "Symbol")
internal val QSYMBOL = Symbol(TYPE, "QSymbol")
internal val FN_TYPE = Symbol(TYPE, "Fn")
internal val VARIANT_TYPE = Symbol(ID, "+")

internal data class Type(val monoType: MonoType, val effects: Set<QSymbol> = emptySet()) {
    override fun toString(): String {
        return if (effects.isEmpty()) monoType.toString() else "(! $monoType #{${effects.joinToString(", ")}}"
    }
}

internal sealed class MonoType {
    internal open val javaType: Class<*>? = Object::class.java

    internal open fun unifyEq(other: MonoType): Unification =
        if (this.javaClass == other.javaClass) Unification() else throw UnificationError(this, other)

    protected inline fun <reified T : MonoType> ensure(t: MonoType): T =
        t as? T ?: throw UnificationError(this, t)

    open fun fmap(f: (MonoType) -> MonoType): MonoType = this

    fun ftvs(): List<TypeVarType> {
        fun ftvs(): List<TypeVarType> = when (this) {
            BoolType, StringType,
            IntType, BigIntType,
            FloatType, BigFloatType,
            SymbolType, QSymbolType -> emptyList()
            is TypeVarType -> listOf(this)
            is VectorType -> this.elType.ftvs()
            is SetType -> this.elType.ftvs()
            is FnType -> this.paramTypes.flatMap { it.ftvs() } + this.returnType.ftvs()
            is RecordType -> this.keyTypes.values.flatten().flatMap { it.ftvs() } + this.typeVar
            is VariantType -> this.possibleKeys.values.flatMap { it.typeParams }.flatMap { it.ftvs() }
            is TypeAliasType -> this.typeAlias.type!!.ftvs()
        }
        return ftvs().distinct()
    }

    internal open fun applyMapping(mapping: Mapping): MonoType = fmap { it.applyMapping(mapping) }
}

internal object BoolType : MonoType() {
    override val javaType: Class<*>? = Boolean::class.javaPrimitiveType

    override fun toString(): String = "Bool"
}

internal object StringType : MonoType() {
    override fun toString(): String = "Str"
}

internal object IntType : MonoType() {
    override val javaType: Class<*>? = Long::class.javaPrimitiveType
    override fun toString(): String = "Int"
}

internal object BigIntType : MonoType() {
    override fun toString(): String = "BigInt"
}

internal object FloatType : MonoType() {
    override val javaType: Class<*>? = Double::class.javaPrimitiveType
    override fun toString(): String = "Float"
}

internal object BigFloatType : MonoType() {
    override fun toString(): String = "BigFloat"
}

internal object SymbolType : MonoType() {
    override fun toString(): String = "Symbol"
}

internal object QSymbolType : MonoType() {
    override fun toString(): String = "QSymbol"
}

internal class TypeVarType : MonoType() {
    override fun applyMapping(mapping: Mapping): MonoType = mapping.typeMapping.getOrDefault(this, this)

    override fun toString(): String {
        return "tv${hashCode() % 10000}"
    }
}

internal data class VectorType(val elType: MonoType) : MonoType() {
    override fun unifyEq(other: MonoType) = Unification(listOf(TypeEq(elType, ensure<VectorType>(other).elType)))
    override fun fmap(f: (MonoType) -> MonoType): MonoType = VectorType(f(elType))

    override fun toString(): String = "[$elType]"
}

internal data class SetType(val elType: MonoType) : MonoType() {
    override fun unifyEq(other: MonoType) = Unification(listOf(TypeEq(elType, ensure<SetType>(other).elType)))
    override fun fmap(f: (MonoType) -> MonoType): MonoType = SetType(f(elType))

    override fun toString(): String = "#{$elType}"
}

internal data class FnType(val paramTypes: List<MonoType>, val returnType: MonoType) : MonoType() {
    override fun unifyEq(other: MonoType): Unification {
        val otherFnType = ensure<FnType>(other)
        if (paramTypes.size != otherFnType.paramTypes.size) throw UnificationError(this, other)

        return Unification(paramTypes.zip(otherFnType.paramTypes, ::TypeEq)
            .plus(TypeEq(returnType, otherFnType.returnType)))
    }

    override fun fmap(f: (MonoType) -> MonoType): MonoType = FnType(paramTypes.map(f), f(returnType))

    override fun toString(): String = "(Fn ${paramTypes.joinToString(separator = " ")} $returnType)"
}

class RowTypeVar(val open: Boolean) {
    override fun toString() = "r${hashCode()}${if (open) "*" else ""}"
}

internal data class RowKey(val typeParams: List<MonoType>) {
    internal fun fmap(f: (MonoType) -> MonoType) = RowKey(typeParams.map(f))
}

internal typealias RowKeyType = List<MonoType>

internal data class RecordType(val hasKeys: Set<RecordKey>? = null,
                               val needsKeys: Set<RecordKey> = emptySet(),
                               val keyTypes: Map<RecordKey, RowKeyType> = emptyMap(),
                               val typeVar: TypeVarType) : MonoType() {

    companion object {
        internal fun accessorType(recordKey: RecordKey): Type {
            val recordType = RecordType(hasKeys = setOf(recordKey), keyTypes = mapOf(recordKey to recordKey.typeVars), typeVar = TypeVarType())

            return Type(FnType(listOf(recordType), recordKey.type))
        }
    }

    override fun unifyEq(other: MonoType): Unification {
        val otherRecord: RecordType = ensure(other)

        val newTypeVar = TypeVarType()

        operator fun RecordType.minus(other: RecordType): RecordType {
            val keyDiff = this.needsKeys - other.hasKeys.orEmpty()
            if (other.hasKeys != null && keyDiff.isNotEmpty()) {
                TODO("missing keys: $keyDiff")
            }

            return RecordType(
                hasKeys = when {
                    other.hasKeys == null -> this.hasKeys
                    this.hasKeys == null -> null
                    else -> other.hasKeys - this.hasKeys
                },
                needsKeys = other.needsKeys + this.needsKeys,
                keyTypes = this.keyTypes - other.keyTypes.keys,
                typeVar = newTypeVar)
        }

        val recordEqs = listOf(
            TypeEq(this.typeVar, otherRecord - this),
            TypeEq(otherRecord.typeVar, this - otherRecord))

        val keyTypeEqs =
            this.keyTypes.keys.intersect(otherRecord.keyTypes.keys)
                .flatMap { this.keyTypes.getValue(it).zip(otherRecord.keyTypes.getValue(it)) }

        return Unification(recordEqs + keyTypeEqs)
    }

    override fun fmap(f: (MonoType) -> MonoType) = copy(keyTypes = keyTypes.mapValues { it.value.map(f) })

    override fun applyMapping(mapping: Mapping): RecordType {
        val recordType = when (val mappedType = mapping.typeMapping[typeVar]) {
            null -> this
            is RecordType -> RecordType(
                if (this.hasKeys == null) mappedType.hasKeys else this.hasKeys - mappedType.hasKeys.orEmpty(),
                this.needsKeys + mappedType.needsKeys,
                (this.keyTypes + mappedType.keyTypes),
                mappedType.typeVar
            )
            is TypeVarType -> this.copy(typeVar = mappedType)
            else -> TODO()
        }

        return recordType.copy(keyTypes = recordType.keyTypes.mapValues { it.value.map { it.applyMapping(mapping) } })
    }

//    override fun toString() = "{${hasKeys.keys.joinToString(" ")}}"
}

internal data class VariantType(val possibleKeys: Map<VariantKey, RowKey>, val typeVar: RowTypeVar) : MonoType() {

    companion object {
        internal fun constructorType(variantKey: VariantKey): Type {
            val variantType = VariantType(mapOf(variantKey to RowKey(variantKey.typeVars)), RowTypeVar(true))

            return Type(if (variantKey.paramTypes.isEmpty()) variantType else FnType(variantKey.paramTypes, variantType))
        }
    }

    override fun unifyEq(other: MonoType): Unification {
        val otherVariant = ensure<VariantType>(other)

        val newTypeVar = RowTypeVar(typeVar.open && otherVariant.typeVar.open)

        fun minus(left: VariantType, right: VariantType): Pair<RowTypeVar, Pair<Map<VariantKey, RowKey>, RowTypeVar>> {
            val keyDiff = left.possibleKeys - right.possibleKeys.keys
            if (!right.typeVar.open && keyDiff.isNotEmpty()) {
                TODO("too many keys: ${keyDiff.keys}")
            }

            return right.typeVar to Pair(keyDiff, newTypeVar)
        }

        val typeVarEqs: List<TypeEq> = (this.possibleKeys.keys + otherVariant.possibleKeys.keys).flatMap {
            (possibleKeys[it]?.typeParams ?: emptyList()) zip (otherVariant.possibleKeys[it]?.typeParams ?: emptyList())
        }

        return Unification(
            typeEqs = typeVarEqs,
            variantEqs = mapOf(
                minus(this, otherVariant),
                minus(otherVariant, this)
            ))
    }

    override fun fmap(f: (MonoType) -> MonoType) = VariantType(possibleKeys.mapValues { it.value.fmap(f) }, typeVar)

    override fun applyMapping(mapping: Mapping): MonoType =
        (mapping.variantMapping[typeVar]?.let { (newKeys, newTypeVar) ->
            VariantType(possibleKeys + newKeys, newTypeVar)
        } ?: this)
            .fmap { it.applyMapping(mapping) }

    override fun toString() =
        "(+ " +
            possibleKeys.map { if (it.value.typeParams.isNotEmpty()) "(${it.key} ${it.value.typeParams.joinToString(" ")})" else "${it.key}" }
                .joinToString(" ") +
            ")"
}

internal data class TypeAliasType(val typeAlias: TypeAlias, val typeParams: List<MonoType>) : MonoType() {
    override fun fmap(f: (MonoType) -> MonoType): MonoType = TypeAliasType(typeAlias, typeParams.map { it.fmap(f) })
    override fun unifyEq(other: MonoType): Unification = Unification(typeEqs = listOf(TypeEq(other, typeAlias.type!!)))

    override fun toString() = if (typeParams.isEmpty()) typeAlias.sym.toString() else "(${typeAlias.sym} ${typeParams.joinToString(" ")})"
}

