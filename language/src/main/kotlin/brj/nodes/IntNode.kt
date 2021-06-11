package brj.nodes

import brj.BridjeLanguage
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.source.SourceSection

abstract class IntNode(lang: BridjeLanguage, loc: SourceSection, private val value: Int) : ExprNode(lang, loc) {
    @Specialization
    fun doExecute() = value
}