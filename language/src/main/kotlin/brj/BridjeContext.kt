package brj

import brj.emitter.TruffleEmitter
import brj.reader.ClasspathLoader
import brj.reader.FormLoader
import brj.reader.NSForms
import brj.reader.loadNSForms
import brj.runtime.RuntimeEnv
import brj.runtime.Symbol
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleFile
import com.oracle.truffle.api.TruffleLanguage

class BridjeContext internal constructor(internal val language: BridjeLanguage,
                                         internal val truffleEnv: TruffleLanguage.Env,
                                         internal var env: RuntimeEnv = RuntimeEnv()) {

    internal val brjHome: TruffleFile? =
        (truffleEnv.config["brj.home"] as? String ?: System.getProperty("brj.home"))
            ?.let(truffleEnv::getInternalTruffleFile)

    internal val formLoader: FormLoader = ClasspathLoader(this)

    private fun require(nsFormses: List<NSForms>): RuntimeEnv {
        val evaluator = Evaluator(TruffleEmitter(this))

        synchronized(this) {
            env = nsFormses.fold(env, evaluator::evalNS)
        }

        return env
    }

    @TruffleBoundary
    internal fun eval(nsForms: NSForms) =
        require(loadNSForms(nsForms.nsHeader.deps, formLoader) + nsForms).nses.getValue(nsForms.nsHeader.ns)

    @TruffleBoundary
    internal fun require(ns: Symbol) =
        require(loadNSForms(setOf(ns), formLoader)).nses.getValue(ns)
}