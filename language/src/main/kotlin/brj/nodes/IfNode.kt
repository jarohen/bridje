package brj.nodes

import brj.BridjeLanguage
import brj.BridjeTypesGen.expectBoolean
import com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.UnexpectedResultException
import com.oracle.truffle.api.profiles.ConditionProfile
import com.oracle.truffle.api.source.SourceSection

class IfNode(
    lang: BridjeLanguage,
    loc: SourceSection?,
    @field:Child private var predNode: ExprNode,
    @field:Child private var thenNode: ExprNode,
    @field:Child private var elseNode: ExprNode
) : ExprNode(lang, loc) {
    private val profile = ConditionProfile.createBinaryProfile()

    override fun execute(frame: VirtualFrame): Any {
        val branch = try {
            profile.profile(expectBoolean(predNode.execute(frame)))
        } catch (e: UnexpectedResultException) {
            throw shouldNotReachHere(e)
        }
        return if (branch) thenNode.execute(frame) else elseNode.execute(frame)
    }
}