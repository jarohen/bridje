package brj.nodes

import brj.BridjeLanguage
import brj.runtime.BridjeVector
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.source.SourceSection

abstract class VectorNode protected constructor(lang: BridjeLanguage, loc: SourceSection?) : CollNode(lang, loc) {
    @Specialization
    override fun executeColl(el: Array<Any>) = BridjeVector(el)
}