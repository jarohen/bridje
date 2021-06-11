package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.NodeChildren
import com.oracle.truffle.api.dsl.NodeField
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.source.SourceSection

@NodeChildren(
    NodeChild(value = "metaObj", type = ExprNode::class),
    NodeChild(value = "params", type = ExecuteArrayNode::class)
)
abstract class NewNode(lang: BridjeLanguage, loc: SourceSection?) : ExprNode(lang, loc) {
    @Specialization(limit = "3")
    fun doExecute(metaObj: TruffleObject, params: Array<Any>, @CachedLibrary("metaObj") interop: InteropLibrary): Any =
        interop.instantiate(metaObj, *params)
}