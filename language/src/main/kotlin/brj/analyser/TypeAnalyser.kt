package brj.analyser

import brj.reader.*
import brj.runtime.Ident
import brj.runtime.SymKind.*
import brj.runtime.Symbol
import brj.runtime.VariantKeyVar
import brj.types.*

internal interface ITypeVarFactory {
    fun mkTypeVar(sym: Symbol): TypeVarType

    class TypeVarFactory : ITypeVarFactory {
        val tvMapping: MutableMap<Symbol, TypeVarType> = mutableMapOf()

        override fun mkTypeVar(sym: Symbol) = tvMapping.getOrPut(sym) { TypeVarType() }
    }
}

internal class TypeAnalyser(private val resolver: Resolver,
                            private val typeVarFactory: ITypeVarFactory = ITypeVarFactory.TypeVarFactory()) {

    fun typeVarAnalyser(it: ParserState) = typeVarFactory.mkTypeVar(it.expectSym(ID))

    fun monoTypeAnalyser(it: ParserState): MonoType =
        when (val form = it.expectForm<Form>()) {
            is SymbolForm -> {
                when (form.sym) {
                    STR -> StringType
                    BOOL -> BoolType
                    INT -> IntType
                    FLOAT -> FloatType
                    BIG_INT -> BigIntType
                    BIG_FLOAT -> BigFloatType
                    SYMBOL -> SymbolType
                    QSYMBOL -> QSymbolType

                    else -> when (form.sym.kind) {
                        ID -> typeVarFactory.mkTypeVar(form.sym)
                        TYPE -> {
                            // TODO kind check
                            resolver.resolveTypeAlias(form.sym)?.let { TypeAliasType(it, emptyList()) } ?: TODO()
                        }
                        else -> TODO()
                    }
                }
            }

            is QSymbolForm -> {
                when (form.sym.kind) {
                    TYPE -> {
                        // TODO kind check
                        resolver.resolveTypeAlias(form.sym)?.let { TypeAliasType(it, emptyList()) } ?: TODO()
                    }
                    else -> TODO()
                }
            }

            is VectorForm -> VectorType(it.nested(form.forms) { monoTypeAnalyser(it).also { _ -> it.expectEnd() } })

            is SetForm -> SetType(it.nested(form.forms) { monoTypeAnalyser(it).also { _ -> it.expectEnd() } })

            is ListForm -> it.nested(form.forms) {
                it.or({
                    it.maybe { it.expectSym(FN_TYPE) }?.let { _ ->
                        val types = it.varargs { monoTypeAnalyser(it) }
                        FnType(types.dropLast(1), types.last()) as MonoType
                    }
                }, {
                    when (it.maybe { it.expectSym(VARIANT_TYPE) })  {
                        null -> null
                        else -> VariantType(
                            possibleKeys = it.varargs {
                                data class Preamble(val variantSym: Ident, val typeParams: List<MonoType>)

                                val preamble = it.or({
                                    it.maybe { it.expectIdent(VARIANT) }?.let { Preamble(it, emptyList()) }
                                }, {
                                    it.maybe { it.expectForm<ListForm>() }?.let { lf -> it.nested(lf.forms) { Preamble(it.expectIdent(VARIANT), it.varargs { monoTypeAnalyser(it) }) } }
                                }) ?: TODO()

                                val variantKey = (resolver.resolveVar(preamble.variantSym) as? VariantKeyVar)?.variantKey
                                    ?: TODO()

                                // TODO check kind
                                variantKey to RowKey(preamble.typeParams)
                            }.toMap(),
                            typeVar = RowTypeVar(true))
                    }
                }, {
                    it.maybe { it.expectSym(TYPE) }?.let { sym ->
                        // TODO kind check
                        TypeAliasType(resolver.resolveTypeAlias(sym) ?: TODO(), it.varargs(this::monoTypeAnalyser))
                    }
                }) ?: TODO()
            }

            is RecordForm -> {
                RecordType(typeVar = TypeVarType())
            }

            else -> TODO()
        }
}