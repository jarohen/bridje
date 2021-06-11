package brj.builtins

import brj.BridjeLanguage
import brj.Typing
import brj.nodes.ExprNode
import brj.nodes.ReadArgNode
import com.oracle.truffle.api.dsl.GenerateNodeFactory
import com.oracle.truffle.api.dsl.NodeChild

@GenerateNodeFactory
@NodeChild(value = "args", type = Array<ReadArgNode>::class)
abstract class BuiltInFn(lang: BridjeLanguage) : ExprNode(lang, null)

@Retention
annotation class BuiltIn(val name: String)
