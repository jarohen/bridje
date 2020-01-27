package brj.analyser

import brj.reader.readForms
import brj.runtime.*
import brj.runtime.SymKind.ID
import brj.runtime.SymKind.RECORD
import brj.types.FnType
import brj.types.IntType
import brj.types.StringType
import brj.types.Type
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal fun ValueExpr.stringRep(): String = when (this) {
    is BooleanExpr -> boolean.toString()
    is StringExpr -> "\"$string\""
    is IntExpr -> int.toString()
    is BigIntExpr -> "${bigInt}N"
    is FloatExpr -> float.toString()
    is BigFloatExpr -> "${bigFloat}M"
    is QuotedSymbolExpr -> "'$sym"
    is QuotedQSymbolExpr -> "'$sym"

    is VectorExpr -> exprs.joinToString(prefix = "[", transform = ValueExpr::stringRep, separator = " ", postfix = "]")
    is SetExpr -> exprs.joinToString(prefix = "#{", transform = ValueExpr::stringRep, separator = " ", postfix = "}")
    is RecordExpr -> entries.joinToString(prefix = "{", transform = { "${it.recordKey.sym} ${it.expr.stringRep()}" }, separator = ", ", postfix = "}")

    is CallExpr -> "(call ${this.f.stringRep()} ${effectLocal.sym} ${args.joinToString(separator = " ", transform = ValueExpr::stringRep)})"

    is FnExpr -> "(fn ${fnName ?: "_"}" +
        params.joinToString(prefix = "[", separator = " ", transform = { "${it.sym}" }, postfix = "]") + " " +
        params.joinToString(prefix = "[", separator = " ", transform = { "${it.sym}" }, postfix = "]") + " " +
        expr.stringRep() + ")"

    is IfExpr -> "(if ${predExpr.stringRep()} ${thenExpr.stringRep()} ${elseExpr.stringRep()})"
    is DoExpr -> "(do ${(exprs + expr).joinToString(separator = " ", transform = ValueExpr::stringRep)})"
    is LetExpr -> "(let " +
        bindings.joinToString(prefix = "[", separator = ", ", transform = { "${it.localVar.sym} ${it.expr.stringRep()}" }, postfix = "]") + " " +
        expr.stringRep() + ")"

    is LoopExpr -> "(loop " +
        bindings.joinToString(prefix = "[", separator = ", ", transform = { "${it.localVar.sym} ${it.expr.stringRep()}" }, postfix = "]") + " " +
        expr.stringRep() + ")"
    is RecurExpr -> "(recur ${exprs.joinToString(prefix = "[", separator = ", ", transform = { "${it.first.sym} ${it.second.stringRep()}" }, postfix = "]")})"

    is CaseExpr -> "(case ${expr.stringRep()} " +
        clauses.joinToString(prefix = "[", separator = ", ", postfix = "]", transform = {
            "(${it.variantKey.sym} ${it.bindings.joinToString(separator = " ", transform = {"${it.sym}"})}) ${it.bodyExpr.stringRep()}"
        }) + " " +
        defaultExpr?.stringRep() + ")"

    is LocalVarExpr -> "(lv ${localVar.sym})"
    is GlobalVarExpr -> "(gv ${globalVar.sym})"

    is WithFxExpr -> "(with-fx " +
        oldFxLocal.sym.toString() + " " +
        newFxLocal.sym.toString() + " " +
        fx.joinToString(prefix = "{", separator = ", ", postfix = "}", transform = { "${it.effectVar.sym} ${it.fnExpr.stringRep()}" }) + " " +
        bodyExpr.stringRep() + ")"
}

internal class ValueExprAnalyserTest {
    val dummyVar = object : Any() {}

    @Test
    fun `analyses record`() {
        val user = Symbol(ID, "user")
        val count = QSymbol(user, Symbol(RECORD, "count"))
        val message = QSymbol(user, Symbol(RECORD, "message"))

        val countKey = RecordKey(count, emptyList(), IntType)
        val messageKey = RecordKey(message, emptyList(), StringType)

        val nsEnv = NSEnv(user, vars = mapOf(
            count.local to RecordKeyVar(countKey, dummyVar),
            message.local to RecordKeyVar(messageKey, dummyVar)))

        assertEquals(
            DoExpr(emptyList(), RecordExpr(listOf(
                RecordEntry(countKey, IntExpr(42)),
                RecordEntry(messageKey, StringExpr("Hello world!"))))),

            ValueExprAnalyser(Resolver.NSResolver(nsEnv = nsEnv)).analyseValueExpr(readForms("""{:count 42, :message "Hello world!"}""").first()))
    }

    @Test
    internal fun `analyses with-fx`() {
        val user = Symbol(ID, "user")
        val println = QSymbol(user, Symbol(ID, "println!"))

        val effectVar = EffectVar(println, Type(FnType(listOf(StringType), StringType)), defaultImpl = null, value = "ohno")

        val nsEnv = NSEnv(user, vars = mapOf(println.local to effectVar))

        val expr = ValueExprAnalyser(Resolver.NSResolver(nsEnv = nsEnv))
            .analyseValueExpr(readForms("""(with-fx [(def (println! s) "Hello!")] (println! "foo!"))""").first())

        println(expr.stringRep())

        val withFxExpr = (expr as DoExpr).expr as WithFxExpr

        assertEquals(1, withFxExpr.fx.size)
        val effect = withFxExpr.fx.first()

        assertEquals(println, effect.effectVar.sym)

        assertEquals(
            """(do "Hello!")""",
            effect.fnExpr.expr.stringRep())

        assertEquals(
            """(do (call (gv user/println!) _fx "foo!"))""",
            withFxExpr.bodyExpr.stringRep())
    }
}