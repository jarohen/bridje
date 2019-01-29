package brj

internal val DO = Symbol.intern("do")
internal val DEF = Symbol.intern("def")
internal val TYPE_DEF = Symbol.intern("::")
internal val DEF_DATA = Symbol.intern("defdata")
internal val DEFX = Symbol.intern("defx")

data class DefExpr(val sym: Symbol, val expr: ValueExpr, val type: Type)
data class TypeDefExpr(val sym: Symbol, val type: Type)

data class DefDataConstructorExpr(val sym: Symbol, val params: List<MonoType>?)
data class DefDataExpr(val sym: Symbol?, val typeParams: List<TypeVarType>?, val attributes: List<Attribute>, val constructors: List<DefDataConstructorExpr> = emptyList())

data class EffectFnExpr(val sym: Symbol, val type: Type, val expr: ValueExpr?)
data class DefxExpr(val sym: QSymbol, val effectFns: List<EffectFnExpr>)

internal data class ActionExprAnalyser(val env: Env, val nsEnv: NSEnv, private val typeDefs: Map<Symbol, Type> = emptyMap()) {

    fun defAnalyser(it: AnalyserState): DefExpr {
        val form = it.expectForm<Form>()

        val (sym, paramSyms) = when (form) {
            is SymbolForm -> Pair(form.sym, null)
            is ListForm -> {
                it.nested(form.forms) {
                    Pair(
                        it.expectForm<SymbolForm>().sym,
                        it.varargs { it.expectForm<SymbolForm>().sym })
                }
            }

            else -> TODO()
        }

        val locals = paramSyms?.map { it to LocalVar(it) } ?: emptyList()

        val bodyExpr = ValueExprAnalyser(env, nsEnv, locals = locals.toMap()).analyseValueExpr(it.forms)

        val expr =
            if (paramSyms == null)
                bodyExpr
            else
                FnExpr(sym, locals.map(Pair<Symbol, LocalVar>::second), bodyExpr)


        val actualType = valueExprType(expr)

        val expectedType = nsEnv.vars[sym]?.type ?: typeDefs[sym]

        if (expectedType != null && !(actualType.matches(expectedType))) {
            TODO()
        }

        return DefExpr(sym, expr, (expectedType ?: actualType).copy(effects = actualType.effects))
    }

    fun typeDefAnalyser(it: AnalyserState): TypeDefExpr {
        val typeAnalyser = TypeAnalyser(env, nsEnv)

        val form = it.expectForm<Form>()
        val (sym, params) = when (form) {
            is ListForm -> {
                it.nested(form.forms) {
                    Pair(it.expectForm<SymbolForm>().sym, it.varargs(typeAnalyser::monoTypeAnalyser))
                }
            }

            is SymbolForm -> {
                Pair(form.sym, null)
            }

            else -> TODO()
        }

        val returnType = typeAnalyser.monoTypeAnalyser(it)

        it.expectEnd()

        return TypeDefExpr(sym, Type(if (params != null) FnType(params, returnType) else returnType, emptySet()))
    }

    fun defDataAnalyser(it: AnalyserState): DefDataExpr {
        val typeAnalyser = TypeAnalyser(env, nsEnv)

        val sig =
            it.maybe { it.expectForm<SymbolForm>().sym }?.let { sym -> Pair(sym, null) }
                ?: it.maybe { it.expectForm<ListForm>().forms }?.let { forms ->
                    it.nested(forms) {
                        Pair(it.expectForm<SymbolForm>().sym, it.varargs(typeAnalyser::tvAnalyser))
                    }
                }

        val sym = sig?.first
        val typeParams = sig?.second

        val attributes = mutableListOf<Attribute>()

        it.maybe { it.expectForm<RecordForm>() }?.let { recordForm ->
            it.nested(recordForm.forms) {
                it.varargs {
                    val attrSym = Symbol.intern(listOfNotNull(sym?.base, it.expectForm<SymbolForm>().sym.base).joinToString("."))

                    // TODO quite a lot of cyclic defs to deal with here...
                    attributes += Attribute(QSymbol.intern(nsEnv.ns, attrSym), typeAnalyser.monoTypeAnalyser(it))
                }
            }
        }

        val constructors = it.varargs {
            val form = it.expectForm<Form>()

            when (form) {
                is ListForm -> {
                    it.nested(form.forms) {
                        DefDataConstructorExpr(it.expectForm<SymbolForm>().sym, it.varargs(typeAnalyser::monoTypeAnalyser))
                    }
                }

                is SymbolForm -> {
                    DefDataConstructorExpr(form.sym, null)
                }

                else -> TODO()
            }
        }

        return DefDataExpr(sym, typeParams, attributes = attributes, constructors = constructors)
    }

    fun defxAnalyser(it: AnalyserState): DefxExpr {
        val sym = it.expectForm<SymbolForm>().sym
        val qSym = QSymbol.intern(nsEnv.ns, sym)

        val effectFns = mutableMapOf<Symbol, EffectFnExpr>()

        var ana = this

        it.varargs {
            it.nested(ListForm::forms) {
                it.maybe { it.expectSym(TYPE_DEF) }?.let { _ ->
                    val (fnSym, type) = ana.typeDefAnalyser(it)
                    ana = ana.copy(typeDefs = typeDefs + (fnSym to type))

                    effectFns[fnSym] = EffectFnExpr(fnSym, type, null)
                }

                    ?: it.maybe { it.expectSym(DEF) }?.let { _ ->
                        val defExpr = ana.defAnalyser(it)
                        val effectFn = effectFns[defExpr.sym]
                        if (effectFn != null) {
                            if (!defExpr.type.matches(effectFn.type)) TODO()

                            effectFns[defExpr.sym] = effectFn.copy(expr = defExpr.expr)
                        }
                    }

                    ?: TODO()
            }
        }

        return DefxExpr(qSym, effectFns.values.toList())
    }

}