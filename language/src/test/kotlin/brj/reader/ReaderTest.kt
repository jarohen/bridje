package brj.reader

import brj.reader.FormReader.Companion.readSourceForms
import com.oracle.truffle.api.source.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal fun Form.stringRep(): String = when (this) {
    is BooleanForm -> "(Bool $bool)"
    is StringForm -> {
        val s = string.replace(Regex("""[\\"\n\t\r]""")) {
            when (it.value.first()) {
                '\\' -> "\\\\"
                '\n' -> "\\\n"
                '\t' -> "\\\t"
                '\r' -> "\\\r"
                '\"' -> "\\\""
                else -> it.value
            }
        }
        "\"$s\""
    }
    is IntForm -> "(Int $int)"
    is BigIntForm -> "(BigInt $bigInt)"
    is FloatForm -> "(Float $float)"
    is BigFloatForm -> "(BigFloat $bigFloat)"
    is SymbolForm -> "(Sym $sym)"
    is QSymbolForm -> "(QSym $sym)"
    is ListForm -> forms.joinToString(prefix = "(List ", transform = Form::stringRep, separator = " ", postfix = ")")
    is VectorForm -> forms.joinToString(prefix = "(Vec ", transform = Form::stringRep, separator = " ", postfix = ")")
    is SetForm -> forms.joinToString(prefix = "(Set ", transform = Form::stringRep, separator = " ", postfix = ")")
    is RecordForm -> forms.joinToString(prefix = "(Rec ", transform = Form::stringRep, separator = " ", postfix = "}")
    is QuotedSymbolForm -> "('Sym $sym)"
    is QuotedQSymbolForm -> "('QSym $sym)"
    is SyntaxQuotedSymbolForm -> "(`Sym $sym)"
    is SyntaxQuotedQSymbolForm -> "(`QSym $sym)"
}

internal fun readForms(s: String) = readSourceForms(Source.newBuilder("brj", s, "<read-forms>").build())
internal fun readFormsString(s: String) = readForms(s).joinToString(transform = Form::stringRep, separator = " ")

internal class ReaderTest {

    @Test
    internal fun `test simple form`() {
        assertEquals(
            "(Vec (Bool true) (Sym foo))",
            readFormsString("[true foo]"))
    }

    @Test
    internal fun `test quoting`() {
        assertEquals(
            "(List (QSym :brj.forms/VectorForm) (Vec (List (QSym :brj.forms/BooleanForm) (Bool true)) (List (QSym :brj.forms/SymbolForm) ('Sym foo))))",
            readFormsString("'[true foo]"))
    }
}

