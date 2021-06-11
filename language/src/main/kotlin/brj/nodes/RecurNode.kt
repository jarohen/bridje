package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ControlFlowException
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.source.SourceSection

class RecurNode(
    lang: BridjeLanguage,
    loc: SourceSection?,
    @field:Children private val writeLocalNodes: Array<WriteLocalNode>
) : ExprNode(lang, loc) {
    class RecurException : ControlFlowException()

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        for (writeLocalNode in writeLocalNodes) {
            writeLocalNode.execute(frame)
        }
        throw RecurException()
    }
}