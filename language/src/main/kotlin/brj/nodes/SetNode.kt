package brj.nodes

import brj.BridjeLanguage
import brj.nodes.CollNode
import brj.runtime.BridjeSet
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.source.SourceSection

abstract class SetNode(lang: BridjeLanguage, loc: SourceSection?) : CollNode(lang, loc) {
    @Specialization
    override fun executeColl(res: Array<Any>) = BridjeSet(res)
}