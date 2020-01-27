package brj

import org.graalvm.polyglot.Context
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
        val res = ctx.eval("brj", """
            (ns variant-introduction-and-elimination)
            
            (:: :Foo [Int])
            (:: :Foo2 [Int])
            
            (def out 
              (case (:Foo2 [54])
                (:Foo a) 12
                (:Foo2 a) (first a)))
        """.trimIndent())

        assertEquals(54L, res.getMember("out").asLong())
    }

    @Test
    internal fun `record introduction and elimination`() {
        val res = ctx.eval("brj", """
            (ns records)
            
            (:: :count Int)
            (:: :message Str)
            
            (def record {:count 42, :message "Hello world!"})
        """.trimIndent())

        val record = res.getMember("record")

        assertEquals(setOf(":records/count", ":records/message"), record.memberKeys)
        assertEquals(42L, record.getMember(":records/count").asLong())
        assertEquals("Hello world!", record.getMember(":records/message").asString())

        assertEquals(42L, res.getMember(":count").execute(record).asLong())
    }

    @Test
    internal fun `java interop`() {
        val res = ctx.eval("brj", """
            (ns java-interop
              {:aliases {Foo (java brj.FooKt    
                                   (:: (plus Int Int) Int))}})
                                   
            (def plus Foo/plus)
        """.trimIndent())

        assertEquals(5, res.getMember("plus").execute(3, 2).asLong())
    }
}