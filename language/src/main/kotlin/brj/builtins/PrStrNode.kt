package brj.builtins

import brj.BridjeLanguage
import brj.BridjeTypesGen
import brj.BridjeTypesGen.expectString
import brj.runtime.BridjeContext
import com.oracle.truffle.api.dsl.CachedContext
import com.oracle.truffle.api.dsl.CachedLanguage
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.nodes.UnexpectedResultException

@BuiltIn("pr-str")
abstract class PrStrNode(lang: BridjeLanguage) : BuiltInFn(lang) {
    @field:Child
    private var objLib = InteropLibrary.getFactory().createDispatched(3)

    @Specialization
    fun doExecute(
        obj: Any,
        @CachedLanguage lang: BridjeLanguage,
        @CachedContext(BridjeLanguage::class) ctx: BridjeContext
    ): String =
        try {
            val objView = if (!objLib.hasLanguage(obj) || objLib.getLanguage(obj) != BridjeLanguage::class.java) {
                lang.getLanguageView(ctx, obj)
            } else obj

            expectString(objLib.toDisplayString(objView))
        } catch (e: UnexpectedResultException) {
            throw RuntimeException(e)
        } catch (e: UnsupportedMessageException) {
            throw RuntimeException(e)
        }
}