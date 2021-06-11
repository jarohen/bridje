package brj.builtins

import brj.BridjeLanguage
import brj.runtime.BridjeContext
import com.oracle.truffle.api.dsl.CachedContext
import com.oracle.truffle.api.dsl.Specialization

@BuiltIn("poly")
abstract class PolyNode(lang: BridjeLanguage) : BuiltInFn(lang) {
    @Specialization
    fun doPoly(lang: String, code: String, @CachedContext(BridjeLanguage::class) ctx: BridjeContext) =
        ctx.poly(lang, code)
}