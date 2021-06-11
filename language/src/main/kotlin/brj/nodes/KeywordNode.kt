package brj.nodes

import brj.BridjeLanguage
import brj.runtime.BridjeKey
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.source.SourceSection

class KeywordNode(lang: BridjeLanguage, loc: SourceSection?, private val keyword: BridjeKey) : ExprNode(lang, loc) {
    override fun execute(frame: VirtualFrame) = keyword
}