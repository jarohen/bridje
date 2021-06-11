package brj.nodes

import brj.BridjeLanguage
import brj.nodes.RecurNode.RecurException
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.nodes.LoopNode
import com.oracle.truffle.api.source.SourceSection

class LoopNode(
    lang: BridjeLanguage,
    private val loc: SourceSection?,
    @field:Children private val writeLocalNodes: Array<WriteLocalNode>,
    exprNode: ExprNode
) : ExprNode(lang, loc) {

    @Child
    private var loopBodyNode: LoopNode = Truffle.getRuntime().createLoopNode(LoopBodyNode(exprNode))

    class LoopExitException(val returnValue: Any) : ControlFlowException()

    class LoopBodyNode(@field:Child private var exprNode: ExprNode) : Node(), RepeatingNode {
        override fun executeRepeating(frame: VirtualFrame) =
            try {
                throw LoopExitException(exprNode.execute(frame))
            } catch (e: RecurException) {
                true
            }
    }

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        for (writeLocalNode in writeLocalNodes) {
            writeLocalNode.execute(frame)
        }
        return try {
            loopBodyNode.execute(frame)
            throw CompilerDirectives.shouldNotReachHere("loop didn't exit properly")
        } catch (e: LoopExitException) {
            e.returnValue
        }
    }

}