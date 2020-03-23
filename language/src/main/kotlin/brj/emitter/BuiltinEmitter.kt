package brj.emitter

import brj.BridjeContext
import brj.BridjeLanguage
import brj.runtime.*
import brj.types.*
import com.oracle.truffle.api.dsl.CachedContext
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.VirtualFrame

internal val BUILTINS_NS = Symbol("brj.built-ins")

@Suppress("UNCHECKED_CAST")
private abstract class ConcatNode : ValueNode() {
    @Specialization
    fun doExecute(frame: VirtualFrame, @CachedContext(BridjeLanguage::class) ctx: BridjeContext) =
        ctx.truffleEnv.asGuestValue((ctx.truffleEnv.asHostObject(frame.arguments[1]) as List<List<*>>).flatten())
}

@Suppress("UNCHECKED_CAST")
private abstract class IsEmptyNode : ValueNode() {
    @Specialization
    fun doExecute(frame: VirtualFrame, @CachedContext(BridjeLanguage::class) ctx: BridjeContext) =
        ctx.truffleEnv.asGuestValue((ctx.truffleEnv.asHostObject(frame.arguments[1]) as List<*>).isEmpty())
}

@Suppress("UNCHECKED_CAST")
private abstract class FirstNode : ValueNode() {
    @Specialization
    fun doExecute(frame: VirtualFrame, @CachedContext(BridjeLanguage::class) ctx: BridjeContext) =
        ctx.truffleEnv.asGuestValue((ctx.truffleEnv.asHostObject(frame.arguments[1]) as List<*>).first())
}

@Suppress("UNCHECKED_CAST")
private abstract class RestNode : ValueNode() {
    @Specialization
    fun doExecute(frame: VirtualFrame, @CachedContext(BridjeLanguage::class) ctx: BridjeContext) =
        ctx.truffleEnv.asGuestValue((ctx.truffleEnv.asHostObject(frame.arguments[1]) as List<*>).drop(1))
}

@Suppress("UNCHECKED_CAST")
private abstract class StrNode : ValueNode() {
    @Specialization
    fun doExecute(frame: VirtualFrame, @CachedContext(BridjeLanguage::class) ctx: BridjeContext) =
        ctx.truffleEnv.asGuestValue((ctx.truffleEnv.asHostObject(frame.arguments[1]) as List<*>).joinToString(""))
}


internal fun builtinsNSEnv(ctx: BridjeContext) = NSEnv(BUILTINS_NS,
    vars = mapOf(
        Symbol("concat") to DefVar(QSymbol(BUILTINS_NS, "concat"),
            TypeVarType().let { tv -> Type(FnType(listOf(VectorType(VectorType(tv))), VectorType(tv))) },
            BridjeFunction(ctx.language.BridjeRootNode(ConcatNodeGen.create()))),

        Symbol("empty?") to DefVar(QSymbol(BUILTINS_NS, "empty?"),
            Type(FnType(listOf(VectorType(TypeVarType())), BoolType)),
            BridjeFunction(ctx.language.BridjeRootNode(IsEmptyNodeGen.create()))),

        Symbol("first") to DefVar(QSymbol(BUILTINS_NS, "first"),
            TypeVarType().let { tv -> Type(FnType(listOf(VectorType(tv)), tv)) },
            BridjeFunction(ctx.language.BridjeRootNode(FirstNodeGen.create()))),

        Symbol("rest") to DefVar(QSymbol(BUILTINS_NS, "rest"),
            TypeVarType().let { tv -> Type(FnType(listOf(VectorType(tv)), VectorType(tv))) },
            BridjeFunction(ctx.language.BridjeRootNode(RestNodeGen.create()))),

        Symbol("str") to DefVar(QSymbol(BUILTINS_NS, "str"),
            Type(FnType(listOf(VectorType(StringType)), StringType)),
            BridjeFunction(ctx.language.BridjeRootNode(StrNodeGen.create())))))