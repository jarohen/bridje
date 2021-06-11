package brj.nodes

import brj.BridjeLanguage
import brj.nodes.ExprNode
import brj.runtime.BridjeFunction
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.NodeChildren
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.StandardTags
import com.oracle.truffle.api.instrumentation.Tag
import com.oracle.truffle.api.interop.*
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.nodes.DirectCallNode
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.source.SourceSection

@NodeChildren(
    NodeChild(value = "fn", type = ExprNode::class),
    NodeChild(value = "args", type = CallNode.CallArgsNode::class, executeWith = ["fn"])
)
abstract class CallNode(lang: BridjeLanguage, loc: SourceSection?) : ExprNode(lang, loc) {

    abstract class CallArgsNode(
        @field:Child private var fxLocalArgNode: ExprNode,
        @field:Children private val argNodes: Array<ExprNode>
    ) : Node() {
        @Specialization
        @ExplodeLoop
        fun doExecute(frame: VirtualFrame?, fn: BridjeFunction?): Array<Any?> {
            val args = arrayOfNulls<Any>(argNodes.size + 1)
            args[0] = fxLocalArgNode.execute(frame)
            for (i in argNodes.indices) {
                args[i + 1] = argNodes[i].execute(frame)
            }
            return args
        }

        @Specialization
        @ExplodeLoop
        fun doExecute(frame: VirtualFrame?, fn: TruffleObject?): Array<Any?> {
            val args = arrayOfNulls<Any>(argNodes.size)
            for (i in argNodes.indices) {
                args[i] = argNodes[i].execute(frame)
            }
            return args
        }

        abstract fun execute(frame: VirtualFrame?, fn: TruffleObject?): Array<Any?>?
    }

    @Specialization(guards = ["fn == cachedFn"])
    fun doExecute(
        fn: BridjeFunction?,
        args: Array<Any?>,
        @Cached("fn") cachedFn: BridjeFunction?,
        @Cached("create(cachedFn.getCallTarget())") callNode: DirectCallNode
    ): Any {
        return callNode.call(*args)
    }

    @Specialization
    fun doExecute(
        fn: Any?, args: Array<Any?>,
        @CachedLibrary(limit = "3") interop: InteropLibrary
    ): Any {
        return try {
            interop.execute(fn, *args)
        } catch (e: UnsupportedTypeException) {
            throw RuntimeException(e)
        } catch (e: ArityException) {
            throw RuntimeException(e)
        } catch (e: UnsupportedMessageException) {
            throw RuntimeException(e)
        }
    }

    override fun hasTag(tag: Class<out Tag?>): Boolean {
        return tag == StandardTags.CallTag::class.java || super.hasTag(tag)
    }
}