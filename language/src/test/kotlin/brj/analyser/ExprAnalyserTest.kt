package brj.analyser

import brj.reader.readForms
import brj.runtime.SymKind.*
import brj.runtime.Symbol
import brj.types.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ExprAnalyserTest {

    private val aSym = Symbol(ID, "a")
    private val aTypeVar = TypeVarType()

    private val resolver = Resolver.NSResolver()

    private val tvFactory = object : ITypeVarFactory {
        private val tvs: MutableMap<Symbol, TypeVarType> = mutableMapOf(aSym to aTypeVar)
        override fun mkTypeVar(sym: Symbol) = tvs.getOrPut(sym) { TypeVarType() }
    }

    private val exprAnalyser = ExprAnalyser(resolver, TypeAnalyser(resolver, tvFactory))

    private fun analyseDecl(s: String) = exprAnalyser.declAnalyser(ParserState(readForms(s)))

    @Test
    fun `analyses var declarations`() {
        val foo = Symbol(ID, "foo")

        assertEquals(
            VarDeclExpr(foo, false, Type(IntType)),
            analyseDecl("(:: foo Int)"))

        assertEquals(
            VarDeclExpr(foo, false, Type(FnType(listOf(IntType), StringType))),
            analyseDecl("(:: (foo Int) Str)"))

        assertEquals(
            VarDeclExpr(foo, true, Type(FnType(listOf(IntType), StringType))),
            analyseDecl("(:: (foo Int) (! Str))"))
    }

    private fun analyseDef(s: String) = exprAnalyser.defAnalyser(ParserState(readForms(s)))

    @Test
    internal fun `analyses var definitions`() {
        val foo = Symbol(ID, "foo")

        assertEquals(
            DefExpr(foo,
                FnExpr(foo, emptyList(), DoExpr(emptyList(), IntExpr(4))),
                Type(FnType(emptyList(), IntType))),

            analyseDef("(def (foo) 4)"))
    }

    @Test
    fun `analyses type aliases`() {
        val foo = Symbol(TYPE, "Foo")
        assertEquals(
            TypeAliasDeclExpr(foo, emptyList(), FnType(listOf(IntType), StringType)),
            analyseDecl("(:: Foo (Fn Int Str))"))
    }

    @Test
    fun `analyses key decl`() {
        val fooKey = Symbol(RECORD, "foo")
        assertEquals(
            RecordKeyDeclExpr(fooKey, emptyList(), IntType),
            analyseDecl("(:: :foo Int)"))

        val decl = analyseDecl("(:: (:foo a) a)")
        val typeVar = (decl as RecordKeyDeclExpr).typeVars.first()

        assertEquals(
            RecordKeyDeclExpr(fooKey, listOf(typeVar), typeVar),
            decl)


        val fooVariant = Symbol(VARIANT, "Foo")

        assertEquals(
            VariantKeyDeclExpr(fooVariant, emptyList(), emptyList()),
            analyseDecl("(:: :Foo)"))

        assertEquals(
            VariantKeyDeclExpr(fooVariant, emptyList(), listOf(IntType, StringType)),
            analyseDecl("(:: :Foo Int Str)"))

    }
}

