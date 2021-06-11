package brj.runtime

import brj.BridjeLanguage
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.DirectCallNode

@ExportLibrary(InteropLibrary::class)
open class BridjeFunction(val callTarget: CallTarget) : TruffleObject {

    @ExportMessage
    fun hasLanguage() = true

    @ExportMessage
    fun getLanguage() = BridjeLanguage::class.java

    @ExportMessage
    fun toDisplayString(allowSideEffects: Boolean) = "<BridjeFunction>"

    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    class Execute {
        companion object {
            @Specialization
            @JvmStatic
            fun doExecute(
                fn: BridjeFunction,
                args: Array<Any>,
                @Cached("create(fn.getCallTarget())", allowUncached = true) callNode: DirectCallNode
            ): Any {
                val passedArgs = arrayOfNulls<Any>(args.size + 1)
                passedArgs[0] = FxMap(FxMap.DEFAULT_SHAPE)
                args.indices.forEach { i ->
                    passedArgs[i + 1] = args[i]
                }
                return callNode.call(*passedArgs)
            }
        }
    }
}