package brj

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.Reader
import java.io.StringReader
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

sealed class Form

data class BooleanForm(val bool: Boolean) : Form()
data class StringForm(val string: String) : Form()
data class IntForm(val int: Long) : Form()
data class BigIntForm(val bigInt: BigInteger) : Form()
data class FloatForm(val float: Double) : Form()
data class BigFloatForm(val bigFloat: BigDecimal) : Form()
data class SymbolForm(val sym: Symbol) : Form()
data class QSymbolForm(val sym: QSymbol) : Form()
data class ListForm(val forms: List<Form>) : Form()
data class VectorForm(val forms: List<Form>) : Form()
data class SetForm(val forms: List<Form>) : Form()
data class RecordForm(val forms: List<Form>) : Form()
data class QuoteForm(val form: Form) : Form()
data class UnquoteForm(val form: Form) : Form()
data class UnquoteSplicingForm(val form: Form) : Form()

internal fun transformForm(formContext: FormParser.FormContext): Form = formContext.accept(object : FormBaseVisitor<Form>() {

    override fun visitBoolean(ctx: FormParser.BooleanContext): Form = BooleanForm(ctx.text == "true")

    override fun visitString(ctx: FormParser.StringContext): Form = StringForm(ctx.STRING().text.removeSurrounding("\""))

    override fun visitInt(ctx: FormParser.IntContext) = IntForm(ctx.INT().text.toLong())

    override fun visitBigInt(ctx: FormParser.BigIntContext): BigIntForm =
        BigIntForm(ctx.BIG_INT().text.removeSuffix("N").toBigInteger())

    override fun visitFloat(ctx: FormParser.FloatContext) = FloatForm(ctx.FLOAT().text.toDouble())

    override fun visitBigFloat(ctx: FormParser.BigFloatContext) =
        BigFloatForm(ctx.BIG_FLOAT().text.removeSuffix("M").toBigDecimal())

    override fun visitSymbol(ctx: FormParser.SymbolContext): Form = SymbolForm(Symbol.mkSym(ctx.text))
    override fun visitQSymbol(ctx: FormParser.QSymbolContext): Form = QSymbolForm(QSymbol.mkQSym(ctx.text))

    override fun visitList(ctx: FormParser.ListContext) = ListForm(ctx.form().map(::transformForm))
    override fun visitVector(ctx: FormParser.VectorContext) = VectorForm(ctx.form().map(::transformForm))
    override fun visitSet(ctx: FormParser.SetContext) = SetForm(ctx.form().map(::transformForm))
    override fun visitRecord(ctx: FormParser.RecordContext) = RecordForm(ctx.form().map(::transformForm))

    override fun visitQuote(ctx: FormParser.QuoteContext) = QuoteForm(transformForm(ctx.form()))
    override fun visitUnquoteSplicing(ctx: FormParser.UnquoteSplicingContext) = UnquoteSplicingForm(transformForm(ctx.form()))
    override fun visitUnquote(ctx: FormParser.UnquoteContext): Form = UnquoteForm(transformForm(ctx.form()))
})

fun readForms(reader: Reader): List<Form> =
    FormParser(CommonTokenStream(FormLexer(CharStreams.fromReader(reader))))
        .file().form()
        .toList()
        .map(::transformForm)

fun readForms(s: String): List<Form> = readForms(StringReader(s))

internal data class NSFile(val nsEnv: NSEnv, val forms: List<Form>)

internal fun loadNSes(rootNSes: Set<Symbol>, nsForms: (Symbol) -> List<Form>): List<NSFile> {
    val stack = LinkedHashSet<Symbol>()

    val res = LinkedList<NSFile>()
    val seen = mutableSetOf<Symbol>()

    fun loadNS(ns: Symbol) {
        if (seen.contains(ns)) return
        if (stack.contains(ns)) throw TODO("Cyclic NS")

        stack += ns

        val state = AnalyserState(nsForms(ns))
        val nsEnv = NSAnalyser(ns).analyseNS(state)

        (nsEnv.deps - seen).forEach(::loadNS)

        res.add(NSFile(nsEnv, state.forms))

        stack -= ns
    }

    rootNSes.forEach(::loadNS)

    return res
}
