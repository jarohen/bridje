package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.dsl.NodeChild
import com.oracle.truffle.api.dsl.NodeField
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.StandardTags
import com.oracle.truffle.api.instrumentation.Tag

@NodeChild(value = "expr", type = ExprNode::class)
@NodeField(name = "frameSlot", type = FrameSlot::class)
abstract class WriteLocalNode(lang: BridjeLanguage) : ExprNode(lang, null) {
    abstract val frameSlot: FrameSlot?
    @Specialization
    fun doExecute(frame: VirtualFrame, expr: Any): Any {
        frame.setObject(frameSlot, expr)
        return expr
    }

    abstract override fun execute(frame: VirtualFrame): Any

    override fun hasTag(tag: Class<out Tag?>): Boolean {
        return tag == StandardTags.WriteVariableTag::class.java || super.hasTag(tag)
    }
}