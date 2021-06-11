package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.dsl.NodeField
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.source.SourceSection

abstract class BoolNode(lang: BridjeLanguage,
                        loc: SourceSection?,
                        private val value: Boolean) : ExprNode(lang, loc) {
    @Specialization
    fun doExecute() = value
}