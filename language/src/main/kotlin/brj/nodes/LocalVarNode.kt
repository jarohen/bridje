package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.FrameSlotTypeException
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.StandardTags
import com.oracle.truffle.api.instrumentation.Tag
import com.oracle.truffle.api.source.SourceSection

abstract class LocalVarNode(lang: BridjeLanguage, loc: SourceSection?, private val frameSlot: FrameSlot) : ExprNode(lang, loc) {
    @Specialization
    fun doExecute(frame: VirtualFrame): Any {
        return try {
            frame.getObject(frameSlot)
        } catch (e: FrameSlotTypeException) {
            throw CompilerDirectives.shouldNotReachHere(e)
        }
    }

    override fun hasTag(tag: Class<out Tag>): Boolean {
        return tag == StandardTags.ReadVariableTag::class.java || super.hasTag(tag)
    }
}