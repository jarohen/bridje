package brj

import brj.BridjeLanguage.Companion.currentBridjeContext
import brj.analyser.*
import brj.emitter.RecordEmitter
import brj.emitter.TruffleEmitter
import brj.emitter.ValueExprEmitter
import brj.emitter.VariantEmitter
import brj.runtime.*
import brj.runtime.SymKind.*
import brj.types.*
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import kotlin.test.assertEquals

@TestInstance(PER_CLASS)
internal class TruffleEmitterTest {
    private val ctx = Context.newBuilder("brj")
        .allowAllAccess(true)
        .build()

    @BeforeAll
    internal fun setUp() {
        ctx.enter()
        ctx.initialize("brj")
    }

    @AfterAll
    internal fun tearDown() {
        ctx.leave()
    }

    @Test
    internal fun `variant introduction and elimination`() {
        val brjCtx = currentBridjeContext()

        val variantKey = VariantKey(QSymbol(Symbol(ID, "user"), Symbol(VARIANT, "Foo")), emptyList(), listOf(IntType))
        val variantKey2 = VariantKey(QSymbol(Symbol(ID, "user"), Symbol(VARIANT, "Foo2")), emptyList(), listOf(IntType))
        val constructor = VariantEmitter.emitVariantKey(variantKey2)
        val localVar = LocalVar(Symbol(ID, "a"))
        val effectLocal = LocalVar(Symbol(ID, "fx"))

        assertEquals(54L,
            ValueExprEmitter(brjCtx).evalValueExpr(
                CaseExpr(
                    CallExpr(GlobalVarExpr(VariantKeyVar(variantKey2, constructor), effectLocal), listOf(IntExpr(54)), effectLocal),
                    listOf(
                        CaseClause(variantKey, listOf(localVar), IntExpr(12)),
                        CaseClause(variantKey2, listOf(localVar), LocalVarExpr(localVar))),
                    IntExpr(43))))
    }

    @Test
    internal fun `record introduction and elimination`() {
        val brjCtx = currentBridjeContext()

    val count = QSymbol(Symbol(ID, "user"), Symbol(RECORD, "count"))
        val message = QSymbol(Symbol(ID, "user"), Symbol(RECORD, "message"))

        val countKey = RecordKey(count, emptyList(), IntType)
        val messageKey = RecordKey(message, emptyList(), StringType)

        val record = Value.asValue(ValueExprEmitter(brjCtx).evalValueExpr(
            RecordExpr(listOf(
                RecordEntry(countKey, IntExpr(42)),
                RecordEntry(messageKey, StringExpr("Hello world!"))))))

        assertEquals(setOf(count.toString(), message.toString()), record.memberKeys)
        assertEquals(42L, record.getMember(count.toString()).asLong())
        assertEquals("Hello world!", record.getMember(message.toString()).asString())

        assertEquals(42L, Value.asValue(RecordEmitter(brjCtx).emitRecordKey(countKey)).execute(record).asLong())
    }

    @Test
    internal fun `java interop`() {
        val javaImport = JavaImport(QSymbol(Symbol(TYPE, "Foo"), Symbol(ID, "plus")), Class.forName("brj.FooKt").kotlin, "plus", Type(FnType(listOf(IntType, IntType), BoolType)))

        val fn = Value.asValue(TruffleEmitter(currentBridjeContext()).emitJavaImport(javaImport))

        assertEquals(5, fn.execute(3, 2).asLong())
    }
}