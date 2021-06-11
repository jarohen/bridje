package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.GenerateWrapper
import com.oracle.truffle.api.instrumentation.InstrumentableNode
import com.oracle.truffle.api.instrumentation.InstrumentableNode.*
import com.oracle.truffle.api.instrumentation.ProbeNode
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode

@GenerateWrapper
open class FnRootNode(
    lang: BridjeLanguage, frameDescriptor: FrameDescriptor,
    @field:Children private val writeArgNodes: Array<WriteLocalNode>,
    @field:Child private var exprNode: ExprNode
) : RootNode(lang, frameDescriptor), InstrumentableNode {

    protected constructor(copy: FnRootNode) : this(
        copy.getLanguage(BridjeLanguage::class.java), copy.frameDescriptor,
        copy.writeArgNodes, copy.exprNode
    )

    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        for (writeArgNode in writeArgNodes) {
            writeArgNode.execute(frame)
        }
        return exprNode.execute(frame)
    }

    override fun isInstrumentable() = true

    override fun createWrapper(probe: ProbeNode): WrapperNode = FnRootNodeWrapper(this, this, probe)
}