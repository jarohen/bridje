package brj.reader

import brj.analyser.ParserState
import brj.runtime.QSymbol
import brj.runtime.SymKind.*
import brj.runtime.Symbol
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NSHeaderTest {
    private fun parseNSHeader(s: String) = NSHeader.nsHeaderParser(ParserState(readForms(s)))

    @Test
    internal fun testNSHeader() {
        assertEquals(NSHeader(ns = Symbol(ID, "foo")),
            parseNSHeader("(ns foo)"))

        assertEquals(mapOf(
            Symbol(ID, "baz") to QSymbol(Symbol(ID, "bar"), Symbol(ID, "baz")),
            Symbol(TYPE, "Baz") to QSymbol(Symbol(ID, "bar"), Symbol(TYPE, "Baz")),
            Symbol(RECORD, "baz") to QSymbol(Symbol(ID, "bar"), Symbol(RECORD, "baz")),
            Symbol(VARIANT, "Baz") to QSymbol(Symbol(ID, "bar"), Symbol(VARIANT, "Baz"))),

            parseNSHeader("(ns foo {:refers {bar #{:baz :Baz baz Baz}}})")!!.refers)

        assertEquals(mapOf(
            Symbol(ID, "bar") to BridjeAlias(Symbol(ID, "foo.bar")),
            Symbol(TYPE, "Str") to
                JavaAlias(Symbol(ID, "foo\$Str"), Symbol(ID, String::class.java.name!!), readForms("(:: (bar Str) Int)"))),

            parseNSHeader("""
              (ns foo
                {:aliases {bar foo.bar
                           Str (java java.lang.String
                                      (:: (bar Str) Int))}})"""
                .trimIndent())!!.aliases)
    }
}