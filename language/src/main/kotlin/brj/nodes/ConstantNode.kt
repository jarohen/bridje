package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.dsl.NodeField
import com.oracle.truffle.api.dsl.NodeFields
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.source.SourceSection

class ConstantNode(lang: BridjeLanguage, loc: SourceSection?, val value: Any) : ExprNode(lang, loc) {
    override fun execute(frame: VirtualFrame) = value
}