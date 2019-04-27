package brj

import brj.BrjLanguage.Companion.require
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

fun main() {
    val ctx = Context.newBuilder("brj").allowAllAccess(true).build()

    ctx.enter()

    val foo = Symbol.mkSym("foo")
    val bar = Symbol.mkSym("bar")

    val fooSource = Source.create("brj", BrjLanguage::class.java.getResource("main-foo.brj").readText())

    val barSource = Source.create("brj", BrjLanguage::class.java.getResource("main-bar.brj").readText())

    val env = require(setOf(foo), mapOf(foo to fooSource, bar to barSource))

    val value = ctx.eval("brj", """(str "Hello" " " "world!")""")

    println("value: $value")

    ctx.leave()
}
