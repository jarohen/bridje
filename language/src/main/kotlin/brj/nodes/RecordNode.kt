package brj.nodes

import brj.BridjeLanguage
import brj.runtime.BridjeRecord
import com.oracle.truffle.api.`object`.DynamicObjectLibrary
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.NodeChildren
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.source.SourceSection

abstract class RecordNode(
    lang: BridjeLanguage,
    loc: SourceSection?,
    @field:Children private val putMemberNodes: Array<PutMemberNode>
) : ExprNode(lang, loc) {

    @NodeChildren(
        NodeChild(value = "obj", type = ExprNode::class),
        NodeChild(value = "value", type = ExprNode::class)
    )
    abstract class PutMemberNode(
        private val key: String,
    ) : Node() {
        abstract fun executePut(frame: VirtualFrame, obj: BridjeRecord): Any

        @Specialization(limit = "3")
        fun doPut(
            obj: BridjeRecord,
            value: Any,
            @CachedLibrary("obj") dynObj: DynamicObjectLibrary
        ): Any {
            dynObj.put(obj, key, value)
            return value
        }
    }

    @Specialization
    @ExplodeLoop
    fun doExecute(frame: VirtualFrame): Any {
        val obj = BridjeRecord()
        for (putMemberNode in putMemberNodes) {
            putMemberNode.executePut(frame, obj)
        }
        return obj
    }
}