package brj.nodes

import brj.BridjeLanguage
import brj.runtime.BridjeVar
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.NodeField
import com.oracle.truffle.api.dsl.NodeFields
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.source.SourceSection

abstract class GlobalVarNode(lang: BridjeLanguage, loc: SourceSection?, protected val bridjeVar: BridjeVar) : ExprNode(lang, loc) {
    @Specialization(assumptions = ["getBridjeVar().getAssumption().getAssumption()"])
    fun cachedExecute(@Cached("getBridjeVar().getValue()") value: Any) = value
}