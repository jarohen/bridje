package brj.nodes

import brj.runtime.FxMap.Companion.DEFAULT_SHAPE
import brj.BridjeLanguage
import brj.BridjeTypesGen
import brj.runtime.*
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.`object`.DynamicObjectLibrary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExecutableNode
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.UnexpectedResultException
import com.oracle.truffle.api.source.SourceSection

class WithFxNode(
    lang: BridjeLanguage,
    loc: SourceSection?,
    @field:Child private var writeFxMap: WriteLocalNode,
    @field:Child private var bodyNode: ExprNode
) : ExprNode(lang, loc) {
    override fun execute(frame: VirtualFrame): Any {
        writeFxMap.execute(frame)
        return bodyNode.execute(frame)
    }

    class WithFxBindingNode(private val sym: Symbol, @field:Child private var exprNode: ExprNode) : Node() {
        @Child
        private var dynObjs = DynamicObjectLibrary.getUncached()

        fun execute(frame: VirtualFrame?, fxMap: FxMap?) {
            dynObjs.put(fxMap, sym, exprNode.execute(frame))
        }
    }

    class NewFxNode(
        lang: BridjeLanguage, loc: SourceSection?,
        @field:Child private var oldFxNode: ExecutableNode,
        @field:Children private val bindingNodes: Array<WithFxBindingNode>
    ) : ExprNode(lang, loc) {

        @ExplodeLoop
        override fun execute(frame: VirtualFrame): FxMap {
            val oldFx = try {
                BridjeTypesGen.expectFxMap(oldFxNode.execute(frame))
            } catch (e: UnexpectedResultException) {
                throw CompilerDirectives.shouldNotReachHere(e)
            }

            val newFx = FxMap(DEFAULT_SHAPE, oldFx)

            for (bindingNode in bindingNodes) {
                bindingNode.execute(frame, newFx)
            }

            return newFx
        }
    }
}