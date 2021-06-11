package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.NodeChildren
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.source.SourceSection

@NodeChildren(
    NodeChild(value = "doExprs", type = ExecuteArrayNode::class),
    NodeChild(value = "doExpr", type = ExprNode::class)
)
abstract class DoNode protected constructor(lang: BridjeLanguage, loc: SourceSection?) : ExprNode(lang, loc) {
    @Specialization
    fun execute(doExprs: Array<Any>, doExpr: Any) = doExpr
}