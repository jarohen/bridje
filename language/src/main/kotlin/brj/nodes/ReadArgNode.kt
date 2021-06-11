package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.frame.VirtualFrame

class ReadArgNode(lang: BridjeLanguage, private val idx: Int) : ExprNode(lang, null) {
    override fun execute(frame: VirtualFrame): Any = frame.arguments[idx]
}