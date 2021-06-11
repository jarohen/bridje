package brj.nodes

import brj.BridjeLanguage
import brj.BridjeTypes
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode
import com.oracle.truffle.api.nodes.ExecutableNode
import com.oracle.truffle.api.source.SourceSection

@TypeSystemReference(BridjeTypes::class)
@GenerateWrapper
abstract class ExprNode(lang: BridjeLanguage, private val loc: SourceSection?) : ExecutableNode(lang),
    InstrumentableNode {
    protected constructor(copy: ExprNode) : this(copy.getLanguage(BridjeLanguage::class.java), copy.loc)

    override fun isInstrumentable() = true

    override fun createWrapper(probe: ProbeNode): WrapperNode = ExprNodeWrapper(this, this, probe)

    override fun hasTag(tag: Class<out Tag>) =
        tag == StandardTags.ExpressionTag::class.java || super.hasTag(tag)

    override fun getSourceSection() = loc
}