package brj.analyser

import brj.*
import brj.QSymbol.Companion.mkQSym
import brj.Symbol.Companion.mkSym
import brj.SymbolKind.RECORD_KEY_SYM
import brj.SymbolKind.VAR_SYM
import com.oracle.truffle.api.source.SourceSection
import java.math.BigDecimal
import java.math.BigInteger

class LocalVar(val sym: Symbol) {
    override fun toString() = "LV($sym)"
}

sealed class ValueExpr {
    abstract val loc: SourceSection?
}

data class BooleanExpr(val boolean: Boolean, override val loc: SourceSection? = null) : ValueExpr()
data class StringExpr(val string: String, override val loc: SourceSection? = null) : ValueExpr()
data class IntExpr(val int: Long, override val loc: SourceSection? = null) : ValueExpr()
data class BigIntExpr(val bigInt: BigInteger, override val loc: SourceSection? = null) : ValueExpr()
data class FloatExpr(val float: Double, override val loc: SourceSection? = null) : ValueExpr()
data class BigFloatExpr(val bigFloat: BigDecimal, override val loc: SourceSection? = null) : ValueExpr()

data class QuotedSymbolExpr(val sym: Symbol, override val loc: SourceSection? = null) : ValueExpr()
data class QuotedQSymbolExpr(val sym: QSymbol, override val loc: SourceSection? = null) : ValueExpr()

data class VectorExpr(val exprs: List<ValueExpr>, override val loc: SourceSection? = null) : ValueExpr()
data class SetExpr(val exprs: List<ValueExpr>, override val loc: SourceSection? = null) : ValueExpr()
data class RecordEntry(val recordKey: RecordKey, val expr: ValueExpr)
data class RecordExpr(val entries: List<RecordEntry>, override val loc: SourceSection? = null) : ValueExpr()

data class CallExpr(val f: ValueExpr, val effectArg: LocalVarExpr?, val args: List<ValueExpr>, override val loc: SourceSection? = null) : ValueExpr()
data class FnExpr(val fnName: Symbol? = null, val params: List<LocalVar>, val expr: ValueExpr, override val loc: SourceSection? = null) : ValueExpr()

data class IfExpr(val predExpr: ValueExpr, val thenExpr: ValueExpr, val elseExpr: ValueExpr, override val loc: SourceSection? = null) : ValueExpr()
data class DoExpr(val exprs: List<ValueExpr>, val expr: ValueExpr, override val loc: SourceSection? = null) : ValueExpr()

data class LetBinding(val localVar: LocalVar, val expr: ValueExpr)
data class LetExpr(val bindings: List<LetBinding>, val expr: ValueExpr, override val loc: SourceSection? = null) : ValueExpr()

data class LoopExpr(val bindings: List<LetBinding>, val expr: ValueExpr, override val loc: SourceSection? = null) : ValueExpr()
data class RecurExpr(val exprs: List<Pair<LocalVar, ValueExpr>>, override val loc: SourceSection? = null) : ValueExpr()

data class CaseClause(val variantKey: VariantKey, val bindings: List<LocalVar>, val bodyExpr: ValueExpr)
data class CaseExpr(val expr: ValueExpr, val clauses: List<CaseClause>, val defaultExpr: ValueExpr?, override val loc: SourceSection? = null) : ValueExpr()

data class LocalVarExpr(val localVar: LocalVar, override val loc: SourceSection? = null) : ValueExpr()
data class GlobalVarExpr(val globalVar: GlobalVar, override val loc: SourceSection? = null) : ValueExpr()

data class EffectDef(val effectVar: EffectVar, val fnExpr: FnExpr)
data class WithFxExpr(val oldFxLocal: LocalVar,
                      val fx: Set<EffectDef>,
                      val newFxLocal: LocalVar,
                      val bodyExpr: ValueExpr,
                      override val loc: SourceSection? = null) : ValueExpr()

internal val IF = mkSym("if")
internal val FN = mkSym("fn")
internal val LET = mkSym("let")
internal val CASE = mkSym("case")
internal val LOOP = mkSym("loop")
internal val RECUR = mkSym("recur")
internal val WITH_FX = mkSym("with-fx")

internal val DEFAULT_EFFECT_LOCAL = LocalVar(mkSym("_fx"))

private val QSYMBOL_FORM = mkQSym(":brj.forms/QSymbolForm")

@Suppress("NestedLambdaShadowedImplicitParameter")
internal data class ValueExprAnalyser(val env: RuntimeEnv, val nsEnv: NSEnv,
                                      val locals: Map<Symbol, LocalVar> = emptyMap(),
                                      val loopLocals: List<LocalVar>? = null,
                                      val effectLocal: LocalVar = DEFAULT_EFFECT_LOCAL) {
    private fun resolve(ident: Ident) = resolve(env, nsEnv, ident)

    private fun symAnalyser(form: SymbolForm): ValueExpr =
        (locals[form.sym]?.let { LocalVarExpr(it, form.loc) })
            ?: resolve(form.sym)?.let { GlobalVarExpr(it, form.loc) }
            ?: TODO("sym not found: ${form.sym}")

    private fun qsymAnalyser(form: QSymbolForm): ValueExpr =
        resolve(form.sym)?.let { GlobalVarExpr(it, form.loc) }
            ?: TODO("sym not found: ${form.sym}")

    private fun ifAnalyser(it: ParserState): ValueExpr {
        val predExpr = this.copy(loopLocals = null).exprAnalyser(it)
        val thenExpr = exprAnalyser(it)
        val elseExpr = exprAnalyser(it)
        it.expectEnd()
        return IfExpr(predExpr, thenExpr, elseExpr, it.outerLoc)
    }

    private fun letAnalyser(it: ParserState): ValueExpr {
        var ana = this
        return LetExpr(it.nested(VectorForm::forms) { bindingState ->
            bindingState.varargs {
                val localVar = LocalVar(it.expectSym(VAR_SYM))
                val expr = ana.copy(loopLocals = null).exprAnalyser(it)

                ana = ana.copy(locals = ana.locals.plus(localVar.sym to localVar))
                LetBinding(localVar, expr)
            }
        },
            ana.exprAnalyser(it),

            it.outerLoc)
    }

    private fun loopAnalyser(it: ParserState): ValueExpr {
        val bindings = it.nested(VectorForm::forms) { bindingState ->
            val bindingCtx = this.copy(loopLocals = null)

            bindingState.varargs {
                LetBinding(LocalVar(it.expectForm<SymbolForm>().sym), bindingCtx.exprAnalyser(it))
            }
        }

        val ana = this.copy(locals = locals.plus(bindings.map { it.localVar.sym to it.localVar }), loopLocals = bindings.map { it.localVar })
        return LoopExpr(bindings, ana.exprAnalyser(it), it.outerLoc)
    }

    private fun recurAnalyser(it: ParserState): ValueExpr {
        if (loopLocals == null) TODO()

        val recurExprs = it.varargs(this::exprAnalyser)

        if (loopLocals.size != recurExprs.size) TODO()

        return RecurExpr(loopLocals.zip(recurExprs), it.outerLoc)
    }

    private fun fnAnalyser(it: ParserState): ValueExpr {
        val fnName: Symbol? = it.maybe { it.expectForm<SymbolForm>().sym }

        val paramNames = it.nested(VectorForm::forms) {
            it.varargs {
                it.expectForm<SymbolForm>().sym
            }
        }

        val newLocals = paramNames.map { it to LocalVar(it) }

        val ana = this.copy(locals = locals.plus(newLocals), loopLocals = newLocals.map { it.second })

        val bodyExpr = ana.doAnalyser(it)

        return FnExpr(fnName, newLocals.map(Pair<Symbol, LocalVar>::second), bodyExpr, it.outerLoc)
    }

    private fun callAnalyser(it: ParserState): ValueExpr {
        val fn = exprAnalyser(it)

        return if (fn is GlobalVarExpr && fn.globalVar is DefMacroVar) {
            exprAnalyser(ParserState(listOf(fn.globalVar.evalMacro(env, it.varargs { it.expectForm<Form>() })), outerLoc = it.outerLoc))
        } else {
            CallExpr(fn, (if (fn is GlobalVarExpr && fn.globalVar.type.effects.isNotEmpty()) LocalVarExpr(effectLocal, fn.loc) else null),
                it.varargs(::exprAnalyser),
                it.outerLoc)
        }
    }

    internal fun doAnalyser(it: ParserState): ValueExpr {
        val exprs = listOf(exprAnalyser(it)).plus(it.varargs(::exprAnalyser))
        return DoExpr(exprs.dropLast(1), exprs.last(), it.outerLoc)
    }

    internal fun withFxAnalyser(it: ParserState): ValueExpr {
        data class Preamble(val sym: Symbol, val paramSyms: List<Symbol>)

        val fx = it.nested(VectorForm::forms) {
            it.varargs {
                it.nested(ListForm::forms) {
                    it.expectSym(DEF)

                    val preamble = it.nested(ListForm::forms) {
                        Preamble(it.expectSym(VAR_SYM), it.varargs { it.expectSym(VAR_SYM) })
                    }

                    val effectVar = resolve(preamble.sym) as? EffectVar ?: TODO()

                    val locals = preamble.paramSyms.map { it to LocalVar(it) }

                    val bodyExpr = this.copy(locals = locals.toMap(), loopLocals = locals.map { it.second }).doAnalyser(it)

                    val expr = FnExpr(preamble.sym, locals.map { it.second }, bodyExpr, it.outerLoc)

                    it.expectEnd()

                    EffectDef(effectVar, expr)
                }
            }
        }

        val newEffectLocal = LocalVar(DEFAULT_EFFECT_LOCAL.sym)

        return WithFxExpr(effectLocal, fx.toSet(), newEffectLocal, this.copy(effectLocal = newEffectLocal).doAnalyser(it), it.outerLoc)
    }

    private fun caseAnalyser(it: ParserState): ValueExpr {
        val expr = exprAnalyser(it)

        val clauses = mutableListOf<CaseClause>()

        while (it.forms.size > 1) {
            val clauseForm = it.expectForm<Form>()

            fun resolveVariantKey(form: Form): VariantKeyVar {
                return when (form) {
                    is SymbolForm -> resolve(form.sym)
                    is QSymbolForm -> resolve(form.sym)
                    else -> TODO()
                } as? VariantKeyVar ?: TODO()
            }

            val (variantKey, paramSyms) = when (clauseForm) {
                is SymbolForm -> Pair(resolveVariantKey(clauseForm).variantKey, emptyList())
                is ListForm -> {
                    it.nested(clauseForm.forms) {
                        Pair(resolveVariantKey(it.expectForm()).variantKey, it.varargs { it.expectForm<SymbolForm>().sym })
                    }
                }
                else -> TODO()
            }

            val localVars = paramSyms.map { it to LocalVar(it) }

            clauses += CaseClause(variantKey, localVars.map { it.second }, copy(locals = locals + localVars).exprAnalyser(it))
        }

        val defaultExpr = if (it.forms.isNotEmpty()) exprAnalyser(it) else null

        it.expectEnd()

        return CaseExpr(expr, clauses.toList(), defaultExpr, it.outerLoc)
    }

    private fun listAnalyser(it: ParserState): ValueExpr {
        if (it.forms.isEmpty()) throw ParseError.ExpectedForm

        val firstForm = it.forms[0]

        return if (firstForm is SymbolForm) {
            when (firstForm.sym) {
                IF, FN, LET, DO, CASE, LOOP, RECUR, WITH_FX -> it.forms = it.forms.drop(1)
            }

            when (firstForm.sym) {
                IF -> ifAnalyser(it)
                FN -> fnAnalyser(it)
                LET -> letAnalyser(it)
                DO -> doAnalyser(it)
                CASE -> caseAnalyser(it)
                LOOP -> loopAnalyser(it)
                RECUR -> recurAnalyser(it)
                WITH_FX -> withFxAnalyser(it)
                else -> callAnalyser(it)
            }
        } else {
            callAnalyser(it)
        }
    }

    private fun collAnalyser(transform: (List<ValueExpr>, SourceSection?) -> ValueExpr): FormsParser<ValueExpr> = {
        transform(it.varargs(::exprAnalyser), it.outerLoc)
    }

    private fun recordAnalyser(form: RecordForm): ValueExpr {
        val entries = mutableListOf<RecordEntry>()

        val state = ParserState(form.forms)
        state.varargs {
            val attr = (resolve(it.expectIdent(RECORD_KEY_SYM)) as? RecordKeyVar)?.recordKey ?: TODO()

            entries += RecordEntry(attr, exprAnalyser(it))
        }

        return RecordExpr(entries, form.loc)
    }

    private fun syntaxQuoteAnalyser(loc: SourceSection?, ident: Ident): ValueExpr =
        CallExpr(GlobalVarExpr(resolve(QSYMBOL_FORM)!!),
            null,
            listOf(QuotedQSymbolExpr((resolve(ident) ?: TODO("sym not found: $ident")).sym)),
            loc)

    private fun exprAnalyser(it: ParserState): ValueExpr {
        val form = it.expectForm<Form>()

        return when (form) {
            is BooleanForm -> BooleanExpr(form.bool, form.loc)
            is StringForm -> StringExpr(form.string, form.loc)
            is IntForm -> IntExpr(form.int, form.loc)
            is BigIntForm -> BigIntExpr(form.bigInt, form.loc)
            is FloatForm -> FloatExpr(form.float, form.loc)
            is BigFloatForm -> BigFloatExpr(form.bigFloat, form.loc)

            is SymbolForm -> symAnalyser(form)
            is QSymbolForm -> qsymAnalyser(form)

            is QuotedSymbolForm -> QuotedSymbolExpr(form.sym, form.loc)
            is QuotedQSymbolForm -> QuotedQSymbolExpr(form.sym, form.loc)

            is ListForm -> listAnalyser(ParserState(form.forms, outerLoc = form.loc))
            is VectorForm -> collAnalyser(::VectorExpr)(ParserState(form.forms, outerLoc = form.loc))
            is SetForm -> collAnalyser(::SetExpr)(ParserState(form.forms))
            is RecordForm -> recordAnalyser(form)
            is SyntaxQuotedSymbolForm -> syntaxQuoteAnalyser(form.loc, form.sym)
            is SyntaxQuotedQSymbolForm -> syntaxQuoteAnalyser(form.loc, form.sym)
        }
    }

    fun analyseValueExpr(form: Form): ValueExpr = doAnalyser(ParserState(listOf(form)))
}
