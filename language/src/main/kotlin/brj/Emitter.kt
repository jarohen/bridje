package brj

import brj.nodes.*
import brj.nodes.CallNodeGen.CallArgsNodeGen
import brj.runtime.BridjeFunction
import brj.runtime.Nil
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.FrameDescriptor

internal class ValueExprEmitter(
    private val lang: BridjeLanguage,
    private val frameDescriptor: FrameDescriptor
) {
    private fun arrayNode(exprs: List<ValueExpr>) =
        ExecuteArrayNode(lang, exprs.map { emitValueExpr(it) }.toTypedArray())

    fun emitValueExpr(expr: ValueExpr): ExprNode = when (expr) {
        is NilExpr -> ConstantNode(lang, expr.loc, Nil)
        is IntExpr -> IntNodeGen.create(lang, expr.loc, expr.int)
        is BoolExpr -> BoolNodeGen.create(lang, expr.loc, expr.bool)
        is StringExpr -> ConstantNode(lang, expr.loc, expr.string)
        is VectorExpr -> VectorNodeGen.create(lang, expr.loc, arrayNode(expr.exprs))
        is SetExpr -> SetNodeGen.create(lang, expr.loc, arrayNode(expr.exprs))
        is RecordExpr -> RecordNodeGen.create(
            lang,
            expr.loc,
            expr.entries
                .map { RecordNodeGen.PutMemberNodeGen.create(it.key.toString(), emitValueExpr(it.value)) }
                .toTypedArray(),
        )

        is KeywordExpr -> KeywordNode(lang, expr.loc, expr.key)

        is IfExpr -> IfNode(
            lang,
            expr.loc,
            emitValueExpr(expr.predExpr),
            emitValueExpr(expr.thenExpr),
            emitValueExpr(expr.elseExpr)
        )
        is DoExpr -> DoNodeGen.create(lang, expr.loc, arrayNode(expr.exprs), emitValueExpr(expr.expr))

        is LetExpr -> LetNode(
            lang,
            expr.loc,
            expr.bindings.map {
                WriteLocalNodeGen.create(lang, emitValueExpr(it.expr), frameDescriptor.findOrAddFrameSlot(it.binding))
            }.toTypedArray(),
            emitValueExpr(expr.expr)
        )

        is FnExpr -> {
            val frameDescriptor = FrameDescriptor()

            val fnRootNode = FnRootNode(
                lang,
                frameDescriptor,
                (listOf(expr.fxLocal) + expr.params).mapIndexed { idx, localVar ->
                    WriteLocalNodeGen.create(lang, ReadArgNode(lang, idx), frameDescriptor.findOrAddFrameSlot(localVar))
                }.toTypedArray(),
                ValueExprEmitter(lang, frameDescriptor).emitValueExpr(expr.expr)
            )

            ConstantNode(lang, expr.loc, BridjeFunction(Truffle.getRuntime().createCallTarget(fnRootNode)))
        }

        is CallExpr -> CallNodeGen.create(
            lang,
            expr.loc,
            emitValueExpr(expr.fn),
            CallArgsNodeGen.create(
                emitValueExpr(expr.fxExpr),
                expr.args.map { emitValueExpr(it) }.toTypedArray()
            )
        )

        is LocalVarExpr -> LocalVarNodeGen.create(lang, expr.loc, frameDescriptor.findOrAddFrameSlot(expr.localVar))
        is GlobalVarExpr -> GlobalVarNodeGen.create(lang, expr.loc, expr.globalVar.bridjeVar)
        is TruffleObjectExpr -> ConstantNode(lang, expr.loc, expr.clazz)

        is WithFxExpr -> {
            WithFxNode(
                lang,
                expr.loc,
                WriteLocalNodeGen.create(
                    lang,
                    WithFxNode.NewFxNode(
                        lang, null,
                        LocalVarNodeGen.create(lang, null, frameDescriptor.findOrAddFrameSlot(expr.oldFx)),
                        expr.bindings.map { WithFxNode.WithFxBindingNode(it.defxVar.sym, emitValueExpr(it.expr)) }
                            .toTypedArray()
                    ),
                    frameDescriptor.findOrAddFrameSlot(expr.newFx)
                ),
                emitValueExpr(expr.expr)
            )
        }

        is LoopExpr -> LoopNode(
            lang,
            expr.loc,
            expr.bindings.map {
                WriteLocalNodeGen.create(
                    lang,
                    emitValueExpr(it.expr),
                    frameDescriptor.findOrAddFrameSlot(it.binding)
                )
            }.toTypedArray(),
            emitValueExpr(expr.expr)
        )

        is RecurExpr -> RecurNode(
            lang,
            expr.loc,
            expr.exprs.map {
                WriteLocalNodeGen.create(
                    lang,
                    emitValueExpr(it.expr),
                    frameDescriptor.findOrAddFrameSlot(it.binding)
                )
            }.toTypedArray()
        )

        is CaseExpr -> {
            val nilClauseNode = expr.nilExpr?.let { CaseNode.NilClauseNode(emitValueExpr(it)) }

            val clauseNodes = expr.clauses.map {
                CaseNode.KeyClauseNode(it.key, frameDescriptor.findOrAddFrameSlot(it.localVar), emitValueExpr(it.expr))
            }

            val defaultClauseNode =
                if (expr.defaultExpr != null) emitValueExpr(expr.defaultExpr)
                else ConstantNode(lang, null, Nil)

            CaseNode(
                lang, expr.loc,
                emitValueExpr(expr.expr),
                (listOfNotNull(nilClauseNode) + clauseNodes).toTypedArray(),
                defaultClauseNode
            )
        }

        is NewExpr -> {
            NewNodeGen.create(
                lang,
                expr.loc,
                emitValueExpr(expr.metaObj),
                arrayNode(expr.params)
            )
        }
    }
}