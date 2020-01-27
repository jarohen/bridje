package brj.reader

import brj.reader.FormReader.Companion.readSourceForms
import com.oracle.truffle.api.source.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal fun Form.stringRep(): String = when (this) {
    is BooleanForm -> bool.toString()
    is StringForm -> string.replace(Regex("""[\\"\n\t\r]""")) {
        when (it.value.first()) {
            '\\' -> "\\\\"
            '\n' -> "\\\n"
            '\t' -> "\\\t"
            '\r' -> "\\\r"
            '\"' -> "\\\""
            else -> it.value
        }
    }
    is IntForm -> int.toString()
    is BigIntForm -> "${bigInt}N"
    is FloatForm -> float.toString()
    is BigFloatForm -> "${bigFloat}M"
    is SymbolForm -> sym.toString()
    is QSymbolForm -> sym.toString()
    is ListForm -> forms.map(Form::stringRep).joinToString(prefix = "(", separator = " ", postfix = ")")
    is VectorForm -> forms.map(Form::stringRep).joinToString(prefix = "[", separator = " ", postfix = "]")
    is SetForm -> forms.map(Form::stringRep).joinToString(prefix = "#{", separator = " ", postfix = "}")
    is RecordForm -> forms.map(Form::stringRep).joinToString(prefix = "{", separator = " ", postfix = "}")
    is QuotedSymbolForm -> "'${sym}"
    is QuotedQSymbolForm -> "'${sym}"
    is SyntaxQuotedSymbolForm -> "`${sym}"
    is SyntaxQuotedQSymbolForm -> "`${sym}"
}

internal fun readForms(s: String) = readSourceForms(Source.newBuilder("brj", s, "<read-forms>").build())

internal class ReaderTest {

    @Test
    internal fun `test simple form`() {
        assertEquals(
            listOf("[true foo]"),
            readForms("[true foo]").map(Form::stringRep))
    }

    @Test
    internal fun `test quoting`() {
        assertEquals(
            listOf("(:brj.forms/VectorForm [(:brj.forms/BooleanForm true) (:brj.forms/SymbolForm 'foo)])"),
            readForms("'[true foo]").map(Form::stringRep))
    }
}

