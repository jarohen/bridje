package brj

import brj.analyser.*
import brj.reader.*
import brj.runtime.*

internal interface Emitter {
    fun evalValueExpr(expr: ValueExpr): Any
    fun emitJavaImport(javaImport: JavaImport): Any
    fun emitRecordKey(recordKey: RecordKey): Any
    fun emitVariantKey(variantKey: VariantKey): Any
    fun emitEffectFn(sym: QSymbol, defaultImpl: BridjeFunction?): BridjeFunction
    fun emitDefMacroVar(expr: DefMacroExpr, ns: Symbol): DefMacroVar
}

internal class Evaluator(private val emitter: Emitter) {
    private inner class NSEvaluator(val env: RuntimeEnv, val nsHeader: NSHeader) {
        fun nsQSym(sym: Symbol) = QSymbol(nsHeader.ns, sym)

        private val resolver = Resolver.NSResolver.create(env, nsHeader)

        internal fun evalForm(nsEnv: NSEnv, form: Form): NSEnv =
            when (val result = ExprAnalyser(resolver.copy(nsEnv = nsEnv)).analyseExpr(form)) {
                is DoResult -> result.forms.fold(nsEnv, this::evalForm)

                is ExprResult -> {
                    when (val expr = result.expr) {
                        is DefExpr -> {
                            val qSym = nsQSym(expr.sym)

                            val eVar = (nsEnv.vars[expr.sym] as? EffectVar)

                            val valueExpr = if (eVar != null || expr.type.effects.isNotEmpty()) {
                                (expr.expr as FnExpr).copy(closedOverLocals = setOf(DEFAULT_EFFECT_LOCAL))
                            } else expr.expr

                            val value = emitter.evalValueExpr(valueExpr)

                            @Suppress("IfThenToElvis") // more readable this way?
                            nsEnv + if (eVar == null)
                                DefVar(qSym, expr.type, value)
                            else
                                eVar.also { it.defaultImpl = value as BridjeFunction; it.value = emitter.emitEffectFn(qSym, value) }
                        }

                        is DefMacroExpr -> {
                            nsEnv + emitter.emitDefMacroVar(expr, nsEnv.ns)
                        }

                        is VarDeclExpr -> {
                            val qsym = nsQSym(expr.sym)
                            nsEnv +
                                if (expr.isEffect)
                                    EffectVar(qsym, expr.type.copy(effects = setOf(qsym)), defaultImpl = null, value = emitter.emitEffectFn(qsym, null))
                                else
                                    DefVar(qsym, expr.type, null)
                        }

                        is TypeAliasDeclExpr -> nsEnv +
                            ((nsEnv.typeAliases[expr.sym] as? TypeAlias_)?.also { it.type = expr.type }
                                ?: TypeAlias_(QSymbol(nsEnv.ns, expr.sym), expr.typeVars, expr.type))

                        is RecordKeyDeclExpr -> {
                            val recordKey = RecordKey(nsQSym(expr.sym), expr.typeVars, expr.type)
                            nsEnv + RecordKeyVar(recordKey, emitter.emitRecordKey(recordKey))
                        }
                        is VariantKeyDeclExpr -> {
                            val variantKey = VariantKey(nsQSym(expr.sym), expr.typeVars, expr.paramTypes)
                            nsEnv + VariantKeyVar(variantKey, emitter.emitVariantKey(variantKey))
                        }
                    }
                }
            }
    }

    fun evalNS(env: RuntimeEnv, nsForms: NSForms): RuntimeEnv {
        val exprAnalyser = ExprAnalyser(Resolver.NSResolver(env))
        val javaImportNSEnvs = nsForms.nsHeader.aliases.values.mapNotNull { alias ->
            when (alias) {
                is JavaAlias -> {
                    NSEnv(alias.ns,
                        vars = ParserState(alias.typeForms).varargs(exprAnalyser.declAnalyser)
                            .associate { decl ->
                                decl as? VarDeclExpr ?: TODO()
                                val javaImport = JavaImport(QSymbol(alias.ns, decl.sym), alias.clazz, decl.sym.baseStr, decl.type)
                                decl.sym to JavaImportVar(javaImport, emitter.emitJavaImport(javaImport))
                            })
                }
                is BridjeAlias -> null
            }
        }

        val nsEvaluator = NSEvaluator(env + javaImportNSEnvs, nsForms.nsHeader)
        return nsEvaluator.env + nsForms.forms.fold(NSEnv(nsForms.nsHeader.ns), nsEvaluator::evalForm)
    }
}