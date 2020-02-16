package brj.analyser

import brj.reader.readForms
import brj.runtime.*
import brj.runtime.SymKind.ID
import brj.runtime.SymKind.VARIANT
import brj.types.MonoType
import brj.types.RowKey
import brj.types.VariantType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TypeAnalyserTest {
    private val boolKey = VariantKey(QSymbol(Symbol(ID, "user"), Symbol(VARIANT, "BooleanForm")), emptyList(), emptyList())
    private val strKey = VariantKey(QSymbol(Symbol(ID, "user"), Symbol(VARIANT, "StringForm")), emptyList(), emptyList())

    private val dummyResolver = Resolver.NSResolver(
        nsEnv = NSEnv(
            ns = Symbol(ID, "user"),
            vars = mapOf(
                boolKey.sym.local to VariantKeyVar(boolKey, 42),
                strKey.sym.local to VariantKeyVar(strKey, 52))))

    private fun analyseMonoType(s: String): MonoType =
        TypeAnalyser(dummyResolver).monoTypeAnalyser(ParserState(readForms(s)))

    @Test
    internal fun `analyses variant type declaration`() {
        val s = "(+ :BooleanForm :StringForm)"

        assertEquals(
            mapOf(boolKey to RowKey(emptyList()), strKey to RowKey(emptyList())),
            (analyseMonoType(s) as VariantType).possibleKeys)
    }
}