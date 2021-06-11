package brj.nodes

import brj.BridjeLanguage
import brj.Typing
import brj.runtime.*
import com.oracle.truffle.api.dsl.*
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.SourceSection

@NodeChild(value = "expr", type = ExprNode::class)
abstract class DefRootNode(lang: BridjeLanguage, frameDescriptor: FrameDescriptor,
                           private val sym: Symbol,
                           private val typing: Typing,
                           private val loc: SourceSection?) :
    RootNode(lang, frameDescriptor) {
    @Specialization
    fun doExecute(
        exprVal: Any,
        @CachedContext(BridjeLanguage::class) ctx: BridjeContext
    ): Any {
        ctx.def(sym, typing, exprVal)
        return exprVal
    }

    override fun getSourceSection() = loc
}