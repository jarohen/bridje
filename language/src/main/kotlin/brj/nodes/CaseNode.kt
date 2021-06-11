package brj.nodes

import brj.BridjeLanguage
import kotlin.Throws
import brj.runtime.BridjeKey
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.nodes.ControlFlowException
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.profiles.ConditionProfile
import com.oracle.truffle.api.source.SourceSection

class CaseNode(
    lang: BridjeLanguage,
    loc: SourceSection?,
    @field:Child private var exprNode: ExprNode,
    @field:Children private val caseClauseNodes: Array<CaseClauseNode>,
    @field:Child private var defaultNode: ExprNode,
) : ExprNode(lang, loc) {
    class CaseMatched(val res: Any) : ControlFlowException()
    abstract class CaseClauseNode : Node() {
        @Throws(CaseMatched::class)
        abstract fun execute(frame: VirtualFrame, obj: Any)
    }

    class NilClauseNode(@field:Child private var exprNode: ExprNode) : CaseClauseNode() {
        private val interop = InteropLibrary.getUncached()
        private val profile = ConditionProfile.createCountingProfile()
        @Throws(CaseMatched::class)
        override fun execute(frame: VirtualFrame, obj: Any) {
            if (profile.profile(interop.isNull(obj))) {
                throw CaseMatched(exprNode.execute(frame))
            }
        }
    }

    class KeyClauseNode(
        private val key: BridjeKey,
        private val frameSlot: FrameSlot,
        @field:Child private var exprNode: ExprNode
    ) : CaseClauseNode() {
        private val interop = InteropLibrary.getUncached()
        private val profile = ConditionProfile.createCountingProfile()
        @Throws(CaseMatched::class)
        override fun execute(frame: VirtualFrame, obj: Any) {
            try {
                if (profile.profile(interop.isMetaInstance(key, obj))) {
                    frame.setObject(frameSlot, obj)
                    throw CaseMatched(exprNode.execute(frame))
                }
            } catch (e: UnsupportedMessageException) {
                throw CompilerDirectives.shouldNotReachHere(e)
            }
        }
    }

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        val obj = exprNode.execute(frame)
        for (caseClauseNode in caseClauseNodes) {
            try {
                caseClauseNode.execute(frame, obj)
            } catch (caseMatched: CaseMatched) {
                return caseMatched.res
            }
        }
        return defaultNode.execute(frame)
    }
}