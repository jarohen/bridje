package brj

internal class TypeAnalyser {
    companion object {
        private val STR = Symbol.intern("Str")
        private val BOOL = Symbol.intern("Bool")
        private val INT = Symbol.intern("Int")
        private val FLOAT = Symbol.intern("Float")
        private val BIG_INT = Symbol.intern("BigInt")
        private val BIG_FLOAT = Symbol.intern("BigFloat")
    }

    val tvMapping: MutableMap<Symbol, TypeVarType> = mutableMapOf()

    private fun tv(sym: Symbol): TypeVarType? =
        if (Character.isLowerCase(sym.nameStr.first())) {
            tvMapping.getOrPut(sym) { TypeVarType() }
        } else null

    fun monoTypeAnalyser(it: AnalyserState): MonoType {
        val form = it.expectForm<Form>()
        return when (form) {
            is SymbolForm -> {
                when (form.sym) {
                    STR -> StringType
                    BOOL -> BoolType
                    INT -> IntType
                    FLOAT -> FloatType
                    BIG_INT -> BigIntType
                    BIG_FLOAT -> BigFloatType

                    else -> {
                        tv(form.sym) ?: TODO()
                    }
                }
            }

            is VectorForm -> VectorType(it.nested(form.forms) { monoTypeAnalyser(it).also { _ -> it.expectEnd() } })

            is SetForm -> SetType(it.nested(form.forms) { monoTypeAnalyser(it).also { _ -> it.expectEnd() } })

            is ListForm -> it.nested(form.forms) { AppliedType(monoTypeAnalyser(it), it.varargs(::monoTypeAnalyser)) }

            else -> TODO()
        }
    }

    fun tvAnalyser(it: AnalyserState): TypeVarType =
        tv(it.expectForm<SymbolForm>().sym) ?: TODO()
}