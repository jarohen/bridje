package brj

import brj.analyser.*
import brj.runtime.*
import brj.runtime.SymKind.*
import brj.types.TypeVarType
import brj.types.valueExprType
import org.junit.jupiter.api.Test

internal class TypesTest {

    private val dummyVar = object : Any() {}

    @Test
    internal fun `test record types`() {
        val tv = TypeVarType()
        val fooKey = RecordKey(QSymbol(Symbol(ID, "user"), Symbol(RECORD, "foo")), listOf(tv), tv)

        println(valueExprType(RecordExpr(listOf(RecordEntry(fooKey, IntExpr(5))))))

        val localVar = LocalVar(Symbol(ID, "r"))
        val effectLocal = LocalVar(Symbol(ID, "fx"))

        val type = valueExprType(
            FnExpr(null, listOf(localVar),
                CallExpr(
                    GlobalVarExpr(RecordKeyVar(fooKey, dummyVar), effectLocal), listOf(LocalVarExpr(localVar)),
                    effectLocal))
        ).monoType

        println(type)
    }

    @Test
    internal fun `test variant types`() {
        val tv = TypeVarType()
        val fooKey = VariantKey(QSymbol(Symbol(ID, "user"), Symbol(VARIANT, "Foo")), listOf(tv), listOf(tv))
        val effectLocal = LocalVar(Symbol(ID, "fx"))

        println(valueExprType(CallExpr(GlobalVarExpr(VariantKeyVar(fooKey, dummyVar), effectLocal), listOf(IntExpr(5)), effectLocal)))
    }

    @Test
    internal fun `test case expr`() {
        val tv = TypeVarType()
        val fooKey = VariantKey(QSymbol(Symbol(ID, "user"), Symbol(VARIANT, "Foo")), listOf(tv), listOf(tv))
        val barKey = VariantKey(QSymbol(Symbol(ID, "user"), Symbol(VARIANT, "Bar")), emptyList(), emptyList())

        val paramVar = LocalVar(Symbol(ID, "foo"))
        val bindingVar = LocalVar(Symbol(ID, "a"))

        println(valueExprType(FnExpr(null, listOf(paramVar), CaseExpr(
            LocalVarExpr(paramVar),
            listOf(
                CaseClause(fooKey, listOf(bindingVar), LocalVarExpr(bindingVar)),
                CaseClause(barKey, emptyList(), FloatExpr(5.3))),
            null))))
    }
}
