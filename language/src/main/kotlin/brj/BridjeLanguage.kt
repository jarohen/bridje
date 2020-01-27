package brj

import brj.analyser.EvalAnalyser.analyseForms
import brj.emitter.BridjeObject
import brj.emitter.EvalEmitter.emitEvalExprs
import brj.emitter.ValueNode
import brj.emitter.builtinsNSEnv
import brj.emitter.formsNSEnv
import brj.reader.FormReader.Companion.readSourceForms
import brj.runtime.SymKind.ID
import brj.runtime.Symbol
import com.oracle.truffle.api.*
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import org.graalvm.options.OptionCategory.USER
import org.graalvm.options.OptionDescriptors
import org.graalvm.options.OptionKey
import org.graalvm.options.OptionStability.STABLE

@TruffleLanguage.Registration(
    id = "brj",
    name = "Bridje",
    version = "0.0.2",
    defaultMimeType = "application/brj",
    characterMimeTypes = ["application/brj"],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
class BridjeLanguage : TruffleLanguage<BridjeContext>() {

    internal inner class BridjeRootNode(@Child var node: ValueNode, frameDescriptor: FrameDescriptor = FrameDescriptor()) : RootNode(this, frameDescriptor) {
        override fun execute(frame: VirtualFrame) = node.execute(frame)
    }

    override fun createContext(truffleEnv: Env) = BridjeContext(this, truffleEnv)

    override fun initializeContext(ctx: BridjeContext) {
        ctx.env += formsNSEnv(ctx)
        ctx.env += builtinsNSEnv(ctx)
        ctx.require(Symbol(ID, "brj.core"))
    }

    override fun isObjectOfLanguage(obj: Any) = obj is BridjeObject

    override fun parse(request: ParsingRequest): CallTarget =
        Truffle.getRuntime().createCallTarget(BridjeRootNode(emitEvalExprs(analyseForms(readSourceForms(request.source)))))

    override fun findTopScopes(ctx: BridjeContext) =
        listOf(Scope.newBuilder("global", ctx.env).build())

    override fun getOptionDescriptors(): OptionDescriptors = BridjeLanguageOptionDescriptors()

    companion object {
        @Option(name = "path", help = "Search path of directories and/or JAR files, separated by ':'",
            category = USER, stability = STABLE)
        @JvmField
        val PATH = OptionKey<String>(System.getProperty("java.class.path") ?: "")

        @Option(name = "stuff-dir", help = "Where Bridje can store temporary files, default ./.brj-stuff",
            category = USER, stability = STABLE)
        @JvmField
        val STUFF_DIR = OptionKey<String>(".brj-stuff")
    }
}
