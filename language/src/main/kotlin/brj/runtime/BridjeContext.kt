package brj.runtime

import brj.BridjeLanguage
import brj.Typing
import brj.nodes.DefxRootNodeGen
import brj.runtime.Symbol.Companion.symbol
import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.source.Source

@ExportLibrary(InteropLibrary::class)
class BridjeContext(internal val lang: BridjeLanguage, internal val truffleEnv: TruffleLanguage.Env) : TruffleObject {

    internal val globalVars = mutableMapOf<Symbol, GlobalVar>()
    internal val imports = mutableMapOf<Symbol, TruffleObject>()

    internal val interop = InteropLibrary.getUncached()

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    @TruffleBoundary
    fun getMembers(includeInternal: Boolean) =
        BridjeVector(globalVars.keys.map { it.local }.toList().toTypedArray())

    @ExportMessage
    fun isMemberReadable(key: String) = globalVars.containsKey(symbol(key))

    @ExportMessage
    fun readMember(key: String) = globalVars[symbol(key)]!!.bridjeVar.value

    @ExportMessage
    fun isScope() = true

    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage() = BridjeLanguage::class.java

    @ExportMessage
    fun toDisplayString(@Suppress("UNUSED_PARAMETER") allowSideEffects: Boolean) = "BridjeEnv"

    @TruffleBoundary
    fun def(sym: Symbol, typing: Typing, value: Any) {
        CompilerAsserts.neverPartOfCompilation()
        globalVars.compute(sym) { _, globalVar ->
            if (globalVar != null) {
//                if (typing.res != globalVar.typing.res) TODO()
                globalVar.also {
                    when (it) {
                        is DefVar -> it.bridjeVar.set(value)
                        is DefxVar -> it.defaultImpl.set(value)
                    }
                }
            } else DefVar(sym, typing, BridjeVar(value))
        }
    }

    @TruffleBoundary
    fun defx(sym: Symbol, typing: Typing) {
        CompilerAsserts.neverPartOfCompilation()

        val defaultImplVar = BridjeVar(null)

        val value = BridjeFunction(
            Truffle.getRuntime().createCallTarget(
                DefxRootNodeGen.DefxValueRootNodeGen.create(lang, FrameDescriptor(), sym, defaultImplVar)
            )
        )

        globalVars.compute(sym) { _, globalVar ->
            if (globalVar != null) TODO("global var already exists in `defx`, '$sym'")
            else DefxVar(sym, typing, BridjeVar(value), defaultImplVar)
        }
    }

    @TruffleBoundary
    fun importClass(className: Symbol) {
        val clazz = truffleEnv.lookupHostSymbol(className.local) as TruffleObject
        val simpleClassName = symbol(interop.asString(interop.getMetaSimpleName(clazz)))
        imports[simpleClassName] = clazz
    }

    @TruffleBoundary
    fun poly(lang: String, code: String): Any =
        truffleEnv.parsePublic(Source.newBuilder(lang, code, "<brj-inline>").build()).call()
}