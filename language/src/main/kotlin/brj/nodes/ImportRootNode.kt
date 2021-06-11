package brj.nodes

import brj.BridjeLanguage
import brj.runtime.BridjeContext
import brj.runtime.Nil
import brj.runtime.Symbol
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.CachedContext
import com.oracle.truffle.api.dsl.NodeField
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.source.SourceSection

abstract class ImportRootNode(
    lang: TruffleLanguage<*>,
    private val loc: SourceSection?,
    @field:CompilationFinal(dimensions = 1) private val classes: Array<Symbol>
) : RootNode(lang) {

    @Specialization
    @ExplodeLoop
    fun doExecute(@CachedContext(BridjeLanguage::class) ctx: BridjeContext): Any {
        for (className in classes) {
            ctx.importClass(className)
        }
        return Nil
    }

    override fun getSourceSection() = loc
}