package brj.emitter

import brj.BridjeContext
import brj.BridjeLanguage
import brj.analyser.DEFAULT_EFFECT_LOCAL
import brj.analyser.EvalAnalyser.EvalExpr
import brj.analyser.EvalAnalyser.EvalExpr.*
import brj.analyser.FnExpr
import brj.analyser.Resolver
import brj.analyser.ValueExprAnalyser
import brj.emitter.ValueExprEmitter.DoNode
import brj.reader.Form
import brj.reader.NSForms
import brj.runtime.BridjeFunction
import brj.runtime.Symbol
import brj.types.valueExprType
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.dsl.CachedContext
import com.oracle.truffle.api.dsl.Specialization

internal abstract class EvalValueNode(private val form: Form) : ValueNode() {
    @TruffleBoundary
    @Specialization
    fun doExecute(@CachedContext(BridjeLanguage::class) ctx: BridjeContext): Any? {
        val expr = ValueExprAnalyser(Resolver.NSResolver(ctx.env)).analyseValueExpr(form)

        @Suppress("UNUSED_VARIABLE") // for now
        val valueExprType = valueExprType(expr, null)

        val fnExpr = FnExpr(params = emptyList(), expr = expr, closedOverLocals = setOf(DEFAULT_EFFECT_LOCAL))
        val fn = (ValueExprEmitter(ctx).evalValueExpr(fnExpr)) as BridjeFunction

        return fn.execute(emptyArray<Any>())
    }
}

internal abstract class EvalRequireNode(private val nses: Set<Symbol>) : ValueNode() {
    @Specialization
    fun doExecute(@CachedContext(BridjeLanguage::class) ctx: BridjeContext) = ctx.require(nses)
}

internal abstract class EvalNSNode(private val nsForms: NSForms) : ValueNode() {
    @Specialization
    fun doExecute(@CachedContext(BridjeLanguage::class) ctx: BridjeContext) = ctx.eval(nsForms)
}

internal object EvalEmitter {
    fun emitEvalExpr(expr: EvalExpr): ValueNode =
        when (expr) {
            is EvalValueExpr -> EvalValueNodeGen.create(expr.form)
            is RequireExpr -> EvalRequireNodeGen.create(expr.nses)
            is AliasExpr -> TODO()
            is NSExpr -> EvalNSNodeGen.create(expr.nsForms)
        }

    fun emitEvalExprs(exprs: List<EvalExpr>) =
        DoNode(exprs.dropLast(1).map { emitEvalExpr(it) }.toTypedArray(), emitEvalExpr(exprs.last()), null)
}