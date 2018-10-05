package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

fun main(args: Array<String>) {
    val context = Context.create()
    context.enter()

    try {
        println(context.eval(Source.create("brj", "10N")))
        println(context.eval(Source.create("brj", "(let [x 5] [3 x])")))
    } finally {
        context.leave()
    }
}
