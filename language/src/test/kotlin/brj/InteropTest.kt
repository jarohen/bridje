package brj

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@Suppress("UNUSED")
fun reverse(str: String) = str.reversed()

class InteropTest {
    @Test
    fun `e2e interop test`() {
        withCtx { ctx ->
            assertEquals("hello world", ctx.eval("brj", """(require! brj.interop-test) (brj.interop-test/str-reverse "dlrow olleh")""").asString())
        }
    }
}