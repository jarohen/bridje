package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.source.SourceSection

class LetNode(
    lang: BridjeLanguage,
    loc: SourceSection?,
    @field:Children private val bindingNodes: Array<WriteLocalNode>,
    @field:Child private var expr: ExprNode
) : ExprNode(lang, loc) {

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        for (bindingNode in bindingNodes) {
            bindingNode.execute(frame)
        }
        return expr.execute(frame)
    }
}