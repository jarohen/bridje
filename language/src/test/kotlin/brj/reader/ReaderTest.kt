package brj.reader

import brj.reader.FormReader.Companion.readSourceForms
import com.oracle.truffle.api.source.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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

