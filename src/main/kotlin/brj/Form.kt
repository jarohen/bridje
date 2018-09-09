package brj

import brj.grammar.FormBaseVisitor
import brj.grammar.FormLexer
import brj.grammar.FormParser
import com.oracle.truffle.api.source.Source
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.math.BigDecimal
import java.math.BigInteger

sealed class Form {

    data class BooleanForm(val bool: Boolean) : Form()
    data class StringForm(val string: String) : Form()
    data class IntForm(val int: Long) : Form()
    data class BigIntForm(val bigInt: BigInteger) : Form()
    data class FloatForm(val float: Double) : Form()
    data class BigFloatForm(val bigFloat: BigDecimal) : Form()
    data class SymbolForm(val ns: String?, val kw: String) : Form()
    data class KeywordForm(val ns: String?, val kw: String) : Form()
    data class ListForm(val forms: List<Form>) : Form()
    data class VectorForm(val forms: List<Form>) : Form()
    data class SetForm(val forms: List<Form>) : Form()
    data class RecordForm(val forms: List<Form>) : Form()
    data class QuoteForm(val form: Form) : Form()
    data class UnquoteForm(val form: Form) : Form()
    data class UnquoteSplicingForm(val form: Form) : Form()

    companion object {
        private fun transformForm(formContext: FormParser.FormContext): Form = formContext.accept(object : FormBaseVisitor<Form>() {

            override fun visitBoolean(ctx: FormParser.BooleanContext): Form = BooleanForm(ctx.text == "true")

            override fun visitString(ctx: FormParser.StringContext): Form = StringForm(ctx.STRING().text.removeSurrounding("\""))

            override fun visitInt(ctx: FormParser.IntContext) = IntForm(ctx.INT().text.toLong())

            override fun visitBigInt(ctx: FormParser.BigIntContext): BigIntForm =
                BigIntForm(ctx.BIG_INT().text.removeSuffix("N").toBigInteger())

            override fun visitFloat(ctx: FormParser.FloatContext) = FloatForm(ctx.FLOAT().text.toDouble())

            override fun visitBigFloat(ctx: FormParser.BigFloatContext) =
                BigFloatForm(ctx.BIG_FLOAT().text.removeSuffix("M").toBigDecimal())

            override fun visitSymbol(ctx: FormParser.SymbolContext) =
                Regex("((.+)/)?(.+)").matchEntire(ctx.text)!!
                    .groups
                    .let { groups -> SymbolForm(groups[2]?.value, groups[3]!!.value) }

            override fun visitKeyword(ctx: FormParser.KeywordContext) =
                Regex(":((.+)/)?(.+)").matchEntire(ctx.text)!!
                    .groups
                    .let { groups -> KeywordForm(groups[2]?.value, groups[3]!!.value) }

            override fun visitList(ctx: FormParser.ListContext) = ListForm(ctx.form().map(Companion::transformForm))
            override fun visitVector(ctx: FormParser.VectorContext) = VectorForm(ctx.form().map(Companion::transformForm))
            override fun visitSet(ctx: FormParser.SetContext) = SetForm(ctx.form().map(Companion::transformForm))
            override fun visitRecord(ctx: FormParser.RecordContext) = RecordForm(ctx.form().map(Companion::transformForm))

            override fun visitQuote(ctx: FormParser.QuoteContext) = QuoteForm(transformForm(ctx.form()))
            override fun visitUnquoteSplicing(ctx: FormParser.UnquoteSplicingContext) = UnquoteSplicingForm(transformForm(ctx.form()))
            override fun visitUnquote(ctx: FormParser.UnquoteContext): Form = UnquoteForm(transformForm(ctx.form()))
        })

        fun readForms(source: Source): List<Form> =
            FormParser(CommonTokenStream(FormLexer(CharStreams.fromReader(source.reader))))
                .file().form()
                .toList()
                .map(Companion::transformForm)

    }
}