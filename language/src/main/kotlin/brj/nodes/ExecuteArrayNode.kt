package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExecutableNode
import com.oracle.truffle.api.nodes.ExplodeLoop

class ExecuteArrayNode(lang: BridjeLanguage, @field:Children private val exprNodes: Array<ExecutableNode>) :
    ExecutableNode(lang) {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Array<Any?> {
        val res = arrayOfNulls<Any>(exprNodes.size)
        for (i in exprNodes.indices) {
            res[i] = exprNodes[i].execute(frame)
        }
        return res
    }
}