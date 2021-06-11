package brj.builtins

import brj.BridjeLanguage
import brj.runtime.BridjeContext
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.CachedContext
import com.oracle.truffle.api.dsl.Specialization
import java.io.PrintWriter

@BuiltIn("println!")
abstract class PrintlnNode(lang: BridjeLanguage) : BuiltInFn(lang) {

    @TruffleBoundary
    private fun print(env: TruffleLanguage.Env, str: String): String {
        PrintWriter(env.out()).run {
            println(str)
            flush()
        }
        return str
    }

    @Specialization
    fun doExecute(arg: String, @CachedContext(BridjeLanguage::class) ctx: BridjeContext) =
        print(ctx.truffleEnv, arg)
}