package brj.analyser

import brj.reader.*
import brj.runtime.*
import brj.runtime.SymKind.*
import java.math.BigDecimal
import java.math.BigInteger

internal class LocalVar(val sym: Symbol) {
    override fun toString() = "LV($sym)"
}

internal sealed class ValueExpr {
    var loc: Loc? = null
}

internal fun <T : ValueExpr> T.withLoc(loc: Loc?): T {
    this.loc = loc
    return this
}

internal data class BooleanExpr(val boolean: Boolean) : ValueExpr()
internal data class StringExpr(val string: String) : ValueExpr()
internal data class IntExpr(val int: Long) : ValueExpr()
internal data class BigIntExpr(val bigInt: BigInteger) : ValueExpr()
internal data class FloatExpr(val float: Double) : ValueExpr()
internal data class BigFloatExpr(val bigFloat: BigDecimal) : ValueExpr()

internal data class QuotedSymbolExpr(val sym: Symbol) : ValueExpr()
internal data class QuotedQSymbolExpr(val sym: QSymbol) : ValueExpr()

internal data class VectorExpr(val exprs: List<ValueExpr>) : ValueExpr()

internal data class SetExpr(val exprs: List<ValueExpr>) : ValueExpr()

internal data class RecordEntry(val recordKey: RecordKey, val expr: ValueExpr)

internal data class RecordExpr(val entries: List<RecordEntry>) : ValueExpr()

internal data class CallExpr(val f: ValueExpr, val args: List<ValueExpr>, val effectLocal: LocalVar) : ValueExpr()

internal data class FnExpr(val fnName: Symbol? = null,
                           val params: List<LocalVar>,
                           val expr: ValueExpr,
                           val effectLocal: LocalVar? = null,
                           val closedOverLocals: Set<LocalVar> = emptySet()) : ValueExpr()

internal data class IfExpr(val predExpr: ValueExpr, val thenExpr: ValueExpr, val elseExpr: ValueExpr) : ValueExpr()

internal data class DoExpr(val exprs: List<ValueExpr>, val expr: ValueExpr) : ValueExpr()

internal data class LetBinding(val localVar: LocalVar, val expr: ValueExpr)

internal data class LetExpr(val bindings: List<LetBinding>, val expr: ValueExpr) : ValueExpr()

internal data class LoopExpr(val bindings: List<LetBinding>, val expr: ValueExpr) : ValueExpr()

internal data class RecurExpr(val exprs: List<Pair<LocalVar, ValueExpr>>) : ValueExpr()

internal data class CaseClause(val variantKey: VariantKey, val bindings: List<LocalVar>, val bodyExpr: ValueExpr)

internal data class CaseExpr(val expr: ValueExpr, val clauses: List<CaseClause>, val defaultExpr: ValueExpr?) : ValueExpr()

internal data class LocalVarExpr(val localVar: LocalVar) : ValueExpr()
internal data class GlobalVarExpr(val globalVar: GlobalVar, val effectLocal: LocalVar) : ValueExpr()

internal data class EffectDef(val effectVar: EffectVar, val fnExpr: FnExpr)

internal data class WithFxExpr(val oldFxLocal: LocalVar,
                               val fx: Set<EffectDef>,
                               val newFxLocal: LocalVar,
                               val bodyExpr: ValueExpr) : ValueExpr()

internal val IF = Symbol(ID, "if")
internal val FN = Symbol(ID, "fn")
internal val LET = Symbol(ID, "let")
internal val CASE = Symbol(ID, "case")
internal val LOOP = Symbol(ID, "loop")
internal val RECUR = Symbol(ID, "recur")
internal val WITH_FX = Symbol(ID, "with-fx")

internal val DEFAULT_EFFECT_LOCAL = LocalVar(Symbol(ID, "_fx"))

private val QSYMBOL_FORM = QSymbol(Symbol(ID, "brj.forms"), Symbol(VARIANT, "QSymbolForm"))

@Suppress("NestedLambdaShadowedImplicitParameter")
internal data class ValueExprAnalyser(val resolver: Resolver,
                                      val locals: Map<Symbol, LocalVar> = emptyMap(),
                                      val loopLocals: List<LocalVar>? = null,
                                      val effectLocal: LocalVar = DEFAULT_EFFECT_LOCAL) {

    private fun resolve(ident: Ident) = resolver.resolveVar(ident)

    private fun symAnalyser(form: SymbolForm): ValueExpr =
        (locals[form.sym]?.let { LocalVarExpr(it).withLoc(form.loc) })
            ?: resolve(form.sym)?.let { GlobalVarExpr(it, effectLocal).withLoc(form.loc) }
            ?: TODO("sym not found: ${form.sym}, ${form.loc}")

    private fun qsymAnalyser(form: QSymbolForm): ValueExpr =
        resolve(form.sym)?.let { GlobalVarExpr(it, effectLocal).withLoc(form.loc) }
            ?: TODO("sym not found: ${form.sym}, ${form.loc}")

    private fun ifAnalyser(it: ParserState): ValueExpr {
        val predExpr = this.copy(loopLocals = null).exprAnalyser(it)
        val thenExpr = exprAnalyser(it)
        val elseExpr = exprAnalyser(it)
        it.expectEnd()
        return IfExpr(predExpr, thenExpr, elseExpr)
    }

    private fun letAnalyser(it: ParserState): ValueExpr {
        var ana = this
        return LetExpr(it.nested(VectorForm::forms) { bindingState ->
            bindingState.varargs {
                val localVar = LocalVar(it.expectSym(ID))
                val expr = ana.copy(loopLocals = null).exprAnalyser(it)

                ana = ana.copy(locals = ana.locals.plus(localVar.sym to localVar))
                LetBinding(localVar, expr)
            }
        },
            ana.doAnalyser(it))
    }

    private fun loopAnalyser(it: ParserState): ValueExpr {
        val bindings = it.nested(VectorForm::forms) { bindingState ->
            val bindingCtx = this.copy(loopLocals = null)

            bindingState.varargs {
                LetBinding(LocalVar(it.expectForm<SymbolForm>().sym), bindingCtx.exprAnalyser(it))
            }
        }

        val ana = this.copy(locals = locals.plus(bindings.map { it.localVar.sym to it.localVar }), loopLocals = bindings.map { it.localVar })
        return LoopExpr(bindings, ana.exprAnalyser(it))
    }

    private fun recurAnalyser(it: ParserState): ValueExpr {
        if (loopLocals == null) TODO()

        val recurExprs = it.varargs(this::exprAnalyser)

        if (loopLocals.size != recurExprs.size) TODO()

        return RecurExpr(loopLocals.zip(recurExprs))
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

        return FnExpr(fnName, newLocals.map(Pair<Symbol, LocalVar>::second), bodyExpr, effectLocal)
    }

    private fun callAnalyser(it: ParserState): ValueExpr {
        val fn = exprAnalyser(it)

        return if (fn is GlobalVarExpr && fn.globalVar is DefMacroVar) {
            exprAnalyser(ParserState(listOf(fn.globalVar.evalMacro(it.varargs { it.expectForm<Form>() }))))
        } else {
            CallExpr(fn, it.varargs(::exprAnalyser), effectLocal)
        }
    }

    internal fun doAnalyser(it: ParserState): ValueExpr {
        val exprs = listOf(exprAnalyser(it)).plus(it.varargs(::exprAnalyser))
        return DoExpr(exprs.dropLast(1), exprs.last())
    }

    internal fun withFxAnalyser(it: ParserState): ValueExpr {
        data class Preamble(val sym: Symbol, val paramSyms: List<Symbol>)

        val fx = it.nested(VectorForm::forms) {
            it.varargs {
                it.nested(ListForm::forms) {
                    it.expectSym(DEF)

                    val preamble = it.nested(ListForm::forms) {
                        Preamble(it.expectSym(ID), it.varargs { it.expectSym(ID) })
                    }

                    val effectVar = resolve(preamble.sym) as? EffectVar ?: TODO()

                    val locals = preamble.paramSyms.map { it to LocalVar(it) }

                    val bodyExpr = this.copy(locals = locals.toMap(), loopLocals = locals.map { it.second }).doAnalyser(it)

                    // TODO needs a few more closedOverLocals in here, most likely
                    val expr = FnExpr(preamble.sym, locals.map { it.second }, bodyExpr, effectLocal, closedOverLocals = setOf(effectLocal))

                    it.expectEnd()

                    EffectDef(effectVar, expr)
                }
            }
        }

        val newEffectLocal = LocalVar(DEFAULT_EFFECT_LOCAL.sym)

        return WithFxExpr(effectLocal, fx.toSet(), newEffectLocal, this.copy(effectLocal = newEffectLocal).doAnalyser(it))
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

        return CaseExpr(expr, clauses.toList(), defaultExpr)
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

    private fun collAnalyser(transform: (List<ValueExpr>) -> ValueExpr): FormsParser<ValueExpr> = {
        transform(it.varargs(::exprAnalyser))
    }

    private fun recordAnalyser(form: RecordForm): ValueExpr {
        val entries = mutableListOf<RecordEntry>()

        val state = ParserState(form.forms)
        state.varargs {
            val attr = (resolve(it.expectIdent(RECORD)) as? RecordKeyVar)?.recordKey ?: TODO()

            entries += RecordEntry(attr, exprAnalyser(it))
        }

        return RecordExpr(entries).withLoc(form.loc)
    }

    private fun syntaxQuoteAnalyser(ident: Ident): ValueExpr =
        CallExpr(GlobalVarExpr(resolve(QSYMBOL_FORM)!!, effectLocal), listOf(QuotedQSymbolExpr((resolve(ident)
            ?: TODO("sym not found: $ident")).sym)), effectLocal)

    private fun exprAnalyser(it: ParserState): ValueExpr {
        val form = it.expectForm<Form>()

        return when (form) {
            is BooleanForm -> BooleanExpr(form.bool)
            is StringForm -> StringExpr(form.string)
            is IntForm -> IntExpr(form.int)
            is BigIntForm -> BigIntExpr(form.bigInt)
            is FloatForm -> FloatExpr(form.float)
            is BigFloatForm -> BigFloatExpr(form.bigFloat)

            is SymbolForm -> symAnalyser(form)
            is QSymbolForm -> qsymAnalyser(form)

            is QuotedSymbolForm -> QuotedSymbolExpr(form.sym)
            is QuotedQSymbolForm -> QuotedQSymbolExpr(form.sym)

            is ListForm -> listAnalyser(ParserState(form.forms))
            is VectorForm -> collAnalyser(::VectorExpr)(ParserState(form.forms))
            is SetForm -> collAnalyser(::SetExpr)(ParserState(form.forms))
            is RecordForm -> recordAnalyser(form)
            is SyntaxQuotedSymbolForm -> syntaxQuoteAnalyser(form.sym)
            is SyntaxQuotedQSymbolForm -> syntaxQuoteAnalyser(form.sym)
        }.withLoc(form.loc)
    }

    fun analyseValueExpr(form: Form): ValueExpr = doAnalyser(ParserState(listOf(form)))
}
