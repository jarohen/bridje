package brj.reader

import brj.runtime.*
import brj.runtime.SymKind.ID
import brj.runtime.SymKind.VARIANT
import brj.types.*
import java.math.BigDecimal
import java.math.BigInteger

internal val FORM_NS = Symbol(ID, "brj.forms")

internal val UNQUOTE = QSymbol(FORM_NS, "unquote")
internal val UNQUOTE_SPLICING = QSymbol(FORM_NS, "unquote-splicing")

internal sealed class Form(formClass: Class<out Form>, arg: Any) : VariantObject(FORM_TYPES.getValue(formClass).variantKey, arrayOf(arg)) {
    var loc: Loc? = null

    fun withLoc(loc: Loc?): Form {
        this.loc = loc
        return this
    }
}

internal val FORM = QSymbol(FORM_NS, "Form")

internal val FORM_TYPE_ALIAS = TypeAlias_(FORM, type = null)
private val LIST_OF_FORMS = VectorType(TypeAliasType(FORM_TYPE_ALIAS, emptyList()))

internal class BooleanForm(val bool: Boolean) : Form(BooleanForm::class.java, bool)
internal class StringForm(val string: String) : Form(StringForm::class.java, string)
internal class IntForm(val int: Long) : Form(IntForm::class.java, int)
internal class BigIntForm(val bigInt: BigInteger) : Form(BigIntForm::class.java, bigInt)
internal class FloatForm(val float: Double) : Form(FloatForm::class.java, float)
internal class BigFloatForm(val bigFloat: BigDecimal) : Form(BigFloatForm::class.java, bigFloat)
internal class SymbolForm(val sym: Symbol) : Form(SymbolForm::class.java, sym)
internal class QSymbolForm(val sym: QSymbol) : Form(QSymbolForm::class.java, sym)
internal class ListForm(val forms: List<Form>) : Form(ListForm::class.java, forms)
internal class VectorForm(val forms: List<Form>) : Form(VectorForm::class.java, forms)
internal class SetForm(val forms: List<Form>) : Form(SetForm::class.java, forms)
internal class RecordForm(val forms: List<Form>) : Form(RecordForm::class.java, forms)
internal class QuotedSymbolForm(val sym: Symbol) : Form(QuotedSymbolForm::class.java, sym)
internal class QuotedQSymbolForm(val sym: QSymbol) : Form(QuotedQSymbolForm::class.java, sym)
internal class SyntaxQuotedSymbolForm(val sym: Symbol) : Form(SyntaxQuotedSymbolForm::class.java, sym)
internal class SyntaxQuotedQSymbolForm(val sym: QSymbol) : Form(SyntaxQuotedQSymbolForm::class.java, sym)

internal data class FormType<I>(val formClass: Class<*>, val constructor: (I) -> Form, val paramType: MonoType) {
    val variantKey = VariantKey(QSymbol(FORM_NS, Symbol(VARIANT, formClass.simpleName!!)), emptyList(), listOf(paramType))
}

internal val FORM_TYPES = listOf(
    FormType(BooleanForm::class.java, ::BooleanForm, BoolType),
    FormType(StringForm::class.java, ::StringForm, StringType),
    FormType(IntForm::class.java, ::IntForm, IntType),
    FormType(BigIntForm::class.java, ::BigIntForm, BigIntType),
    FormType(FloatForm::class.java, ::FloatForm, FloatType),
    FormType(BigFloatForm::class.java, ::BigFloatForm, BigFloatType),
    FormType(SymbolForm::class.java, ::SymbolForm, SymbolType),
    FormType(QSymbolForm::class.java, ::QSymbolForm, QSymbolType),
    FormType(ListForm::class.java, ::ListForm, LIST_OF_FORMS),
    FormType(VectorForm::class.java, ::VectorForm, LIST_OF_FORMS),
    FormType(SetForm::class.java, ::SetForm, LIST_OF_FORMS),
    FormType(RecordForm::class.java, ::RecordForm, LIST_OF_FORMS),
    FormType(QuotedSymbolForm::class.java, ::QuotedSymbolForm, SymbolType),
    FormType(SyntaxQuotedSymbolForm::class.java, ::SyntaxQuotedSymbolForm, SymbolType),
    FormType(SyntaxQuotedQSymbolForm::class.java, ::SyntaxQuotedQSymbolForm, QSymbolType))

    .associateBy { it.formClass }
    .also { formTypes ->
        FORM_TYPE_ALIAS.type = VariantType(
            formTypes.values.associate { it.variantKey to RowKey(emptyList()) },
            RowTypeVar(false))
    }

