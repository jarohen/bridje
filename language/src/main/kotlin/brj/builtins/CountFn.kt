package brj.builtins

import brj.BridjeLanguage
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.nodes.NodeInfo

@NodeInfo(shortName = "count")
abstract class CountFn(lang: BridjeLanguage) : BuiltInFn(lang) {

    @Specialization(guards = ["interop.hasArrayElements(obj)"], limit = "3")
    fun doCount(obj: TruffleObject, @CachedLibrary("obj") interop: InteropLibrary) =
        interop.getArraySize(obj)

}