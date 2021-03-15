package brj

import brj.nodes.*
import brj.runtime.BridjeFunction
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.FrameDescriptor

internal class Emitter(
    private val lang: BridjeLanguage,
    private val frameDescriptor: FrameDescriptor = FrameDescriptor()
) {
    private fun arrayNode(exprs: List<ValueExpr>) =
        ExecuteArrayNode(lang, exprs.map { emitValueExpr(it) }.toTypedArray())

    internal fun emitValueExpr(expr: ValueExpr): ExprNode = when (expr) {
        is IntExpr -> IntNodeGen.create(lang, expr.int, expr.loc)
        is BoolExpr -> BoolNodeGen.create(lang, expr.bool, expr.loc)
        is StringExpr -> StringNodeGen.create(lang, expr.string, expr.loc)
        is VectorExpr -> VectorNodeGen.create(lang, arrayNode(expr.exprs), expr.loc)
        is SetExpr -> SetNodeGen.create(lang, arrayNode(expr.exprs), expr.loc)
        is IfExpr -> IfNode(
            lang,
            emitValueExpr(expr.predExpr),
            emitValueExpr(expr.thenExpr),
            emitValueExpr(expr.elseExpr),
            expr.loc
        )
        is DoExpr -> DoNodeGen.create(lang, arrayNode(expr.exprs), emitValueExpr(expr.expr), expr.loc)

        is LetExpr -> LetNode(
            lang,
            expr.bindings.map {
                WriteLocalNodeGen.create(emitValueExpr(it.expr), frameDescriptor.findOrAddFrameSlot(it.binding))
            }.toTypedArray(),
            emitValueExpr(expr.expr),
            expr.loc
        )

        is FnExpr -> {
            val frameDescriptor = FrameDescriptor()

            val fnRootNode = FnRootNode(
                lang,
                frameDescriptor,
                expr.params.map(frameDescriptor::findOrAddFrameSlot).toTypedArray(),
                Emitter(lang, frameDescriptor).emitValueExpr(expr.expr)
            )

            FnNodeGen.create(lang, BridjeFunction(Truffle.getRuntime().createCallTarget(fnRootNode)), expr.loc)
        }

        is CallExpr -> CallNodeGen.create(
            lang,
            emitValueExpr(expr.fn),
            arrayNode(expr.args),
            expr.loc
        )

        is LocalVarExpr -> LocalVarNodeGen.create(lang, frameDescriptor.findOrAddFrameSlot(expr.localVar), expr.loc)
        is GlobalVarExpr -> GlobalVarNodeGen.create(lang, expr.globalVar, expr.loc)
    }

    internal fun emitDefExpr(expr: DefExpr) = DefNodeGen.create(lang, emitValueExpr(expr.expr), expr.sym, valueExprType(expr.expr), expr.loc)
}


