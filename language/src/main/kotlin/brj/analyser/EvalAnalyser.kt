package brj.analyser

import brj.reader.Form
import brj.reader.ListForm
import brj.reader.NSForms
import brj.reader.RecordForm
import brj.runtime.SymKind
import brj.runtime.Symbol

internal object EvalAnalyser {

    internal sealed class EvalExpr {
        data class RequireExpr(val ns: Symbol) : EvalExpr()
        data class AliasExpr(val aliases: Map<Symbol, Symbol>) : EvalExpr()
        data class EvalValueExpr(val form: Form) : EvalExpr()
        data class NSExpr(val nsForms: NSForms) : EvalExpr()
    }

    private val requireParser: FormsParser<Symbol?> = {
        it.maybe {
            it.nested(ListForm::forms) { it.expectSym(Symbol(SymKind.ID, "require!")); it }
        }?.expectSym(SymKind.ID)
    }

    private val aliasParser: FormsParser<Map<Symbol, Symbol>?> = {
        it.maybe {
            it.nested(ListForm::forms) { it.expectSym(Symbol(SymKind.ID, "alias!")); it }
        }?.let {
            it.nested(RecordForm::forms) {
                it.varargs { Pair(it.expectSym(SymKind.ID), it.expectSym(SymKind.ID)) }
            }.toMap()
        }
    }

    private val formsParser: FormsParser<List<EvalExpr>> = {
        it.varargs {
            it.or(
                { it.maybe(NSHeader.Companion::nsHeaderParser)?.let { header -> EvalExpr.NSExpr(NSForms(header, it.consume())) } },
                { it.maybe(requireParser)?.let { nses -> EvalExpr.RequireExpr(nses) } },
                { it.maybe(aliasParser)?.let { aliases -> EvalExpr.AliasExpr(aliases) } }
            ) ?: EvalExpr.EvalValueExpr(it.expectForm())
        }
    }

    fun analyseForms(forms: List<Form>) = formsParser(ParserState(forms))
}