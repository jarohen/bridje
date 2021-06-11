package brj.nodes

import brj.BridjeLanguage
import brj.DEFAULT_FX_LOCAL
import brj.nodes.ExprNode
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.NodeChildren
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.nodes.RootNode

@NodeChildren(
    NodeChild(value = "writeFxLocal", type = WriteLocalNode::class),
    NodeChild(value = "expr", type = ExprNode::class)
)
abstract class ValueExprRootNode protected constructor(lang: BridjeLanguage, frameDescriptor: FrameDescriptor) :
    RootNode(lang, frameDescriptor) {
    @Specialization
    fun execute(_writeFxLocal: Any, expr: Any) = expr

    companion object {
        fun create(lang: BridjeLanguage, frameDescriptor: FrameDescriptor, exprNode: ExprNode): ValueExprRootNode {
            val readFxMapNode = WriteLocalNodeGen.create(
                lang,
                ReadArgNode(lang, 0),
                frameDescriptor.findOrAddFrameSlot(DEFAULT_FX_LOCAL)
            )
            return ValueExprRootNodeGen.create(lang, frameDescriptor, readFxMapNode, exprNode)
        }
    }
}