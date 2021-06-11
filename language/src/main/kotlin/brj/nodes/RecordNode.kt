package brj.nodes

import brj.BridjeLanguage
import brj.runtime.BridjeRecord
import com.oracle.truffle.api.`object`.DynamicObjectLibrary
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

    abstract class PutMemberNode(
        private val key: String,
        @Child var valueNode: ExprNode
    ) : Node() {
        abstract fun executePut(frame: VirtualFrame, obj: BridjeRecord): Any

        @Specialization(limit = "3")
        fun doPut(
            frame: VirtualFrame,
            obj: BridjeRecord,
            @CachedLibrary("obj") dynObj: DynamicObjectLibrary
        ): Any {
            val value = valueNode.execute(frame)
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