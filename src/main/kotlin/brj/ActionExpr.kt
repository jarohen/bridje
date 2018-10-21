package brj

import brj.Form.ListForm
import brj.Form.SymbolForm

sealed class ActionExpr {
    data class DefExpr(val sym: Symbol, val params: List<LocalVar>?, val expr: ValueExpr) : ActionExpr()

    @Suppress("NestedLambdaShadowedImplicitParameter")
    data class ActionExprAnalyser(val brjEnv: BrjEnv, val ns: Symbol) {
        private val defAnalyser: FormsAnalyser<ActionExpr> = {
            val (sym, paramSyms) = it.or({
                Pair(it.expectForm<Form.SymbolForm>().sym, null)
            }, {
                it.nested(ListForm::forms) {
                    Pair(
                        it.expectForm<Form.SymbolForm>().sym,
                        it.varargs { it.expectForm<Form.SymbolForm>().sym })
                }
            }) ?: throw Analyser.AnalyserError.InvalidDefDefinition

            val localMapping = paramSyms?.associate { it to LocalVar(it) } ?: emptyMap()

            val params = paramSyms?.map { localMapping[it]!! }

            val expr = ValueExpr.ValueExprAnalyser(brjEnv, ns, locals = localMapping).analyseValueExpr(it.forms)

            DefExpr(sym, params, expr)
        }

        val actionExprAnalyser: FormsAnalyser<ActionExpr> = {
            val firstSym = it.expectForm<SymbolForm>().sym

            when (firstSym) {
                DEF -> defAnalyser(it)
                else -> TODO()
            }
        }

        companion object {
            private val DEF = Symbol.create("def")
        }
    }
}