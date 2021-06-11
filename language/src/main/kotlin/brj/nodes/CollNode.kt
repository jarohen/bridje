package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.NodeField
import com.oracle.truffle.api.source.SourceSection

@NodeChild(value = "els", type = ExecuteArrayNode::class)
abstract class CollNode(lang: BridjeLanguage, loc: SourceSection?) : ExprNode(lang, loc) {
    abstract fun executeColl(el: Array<Any>): Any
}