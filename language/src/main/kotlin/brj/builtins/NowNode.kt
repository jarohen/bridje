package brj.builtins

import brj.BridjeLanguage
import brj.runtime.BridjeInstant
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.dsl.Specialization
import java.time.Instant

@BuiltIn("now!")
abstract class NowNode(lang: BridjeLanguage) : BuiltInFn(lang) {

    @TruffleBoundary
    private fun now() = BridjeInstant(Instant.now())

    @Specialization
    fun doExecute(): BridjeInstant = now()
}