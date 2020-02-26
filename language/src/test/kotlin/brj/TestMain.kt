package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

fun <R> withCtx(f: (Context) -> R): R {
    val ctx = Context.newBuilder("brj").allowAllAccess(true).build()

    try {
        ctx.enter()
        ctx.initialize("brj")
        return f(ctx)
    } finally {
        ctx.leave()
        ctx.close()
    }
}

fun main() {
    withCtx { ctx ->
        @Suppress("UNUSED_VARIABLE")
        val env = ctx.eval(Source.create("brj", "(require! brj.main-foo)"))

        println(ctx.eval("brj", "[1 2]"))

        val value = ctx.eval("brj", """(brj.main-foo/my-fn "Hello" "world!")""")
        println("value: $value")
    }
}
