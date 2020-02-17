@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj.reader

import brj.analyser.ExprAnalyser
import brj.analyser.ParserState
import brj.analyser.Resolver
import brj.analyser.VarDeclExpr
import brj.runtime.QSymbol
import brj.runtime.SymKind.*
import brj.runtime.Symbol
import brj.types.MonoType

private val NS = Symbol(ID, "ns")
private val REFERS = Symbol(RECORD, "refers")
private val ALIASES = Symbol(RECORD, "aliases")
private val JAVA = Symbol(ID, "java")

internal sealed class Alias {
    abstract val ns: Symbol
}

internal data class BridjeAlias(override val ns: Symbol) : Alias()
internal data class JavaInteropDecl(val sym: Symbol, val type: MonoType)
internal data class JavaAlias(override val ns: Symbol, val clazz: Symbol, val typeForms: List<Form>) : Alias()

internal data class NSHeader(val ns: Symbol,
                             val refers: Map<Symbol, QSymbol> = emptyMap(),
                             val aliases: Map<Symbol, Alias> = emptyMap()) {

    val deps by lazy {
        refers.values.mapTo(mutableSetOf()) { it.ns } +
            aliases.values.mapNotNull {
                when (it) {
                    is BridjeAlias -> it.ns
                    is JavaAlias -> null
                }
            }
    }

    companion object {
        private fun refersAnalyser(it: ParserState) =
            it.varargs {
                val nsSym = it.expectForm<SymbolForm>().sym
                it.nested(SetForm::forms) {
                    it.varargs {
                        val sym = it.expectForm<SymbolForm>().sym
                        sym to QSymbol(nsSym, sym)
                    }
                }
            }.flatten().toMap()

        private fun aliasesAnalyser(it: ParserState, ns: Symbol) =
            it.varargs {
                it.or({
                    it.maybe { it.expectSym(ID) }?.let { sym -> sym to BridjeAlias(it.expectSym(ID)) }
                }, {
                    when (val sym = it.maybe { it.expectSym(TYPE) }) {
                        // this doesn't look like idiomatic Kotlin - you'd use '?.let' given the chance
                        // but for reasons I can't fathom, this breaks native-image
                        null -> null
                        else -> it.nested(ListForm::forms) {
                            it.expectSym(JAVA)
                            sym to JavaAlias(
                                Symbol(ID, "$ns\$${sym}"),
                                Symbol(ID, it.expectSym(ID).baseStr),
                                it.consume())
                        }
                    }
                }) ?: TODO()
            }.toMap()

        internal fun nsHeaderParser(it: ParserState): NSHeader? =
            it.maybe {
                it.nested(ListForm::forms) {
                    it.expectSym(NS)
                    it
                }
            }?.let {
                val ns = it.expectSym(ID)

                var nsHeader = NSHeader(ns)

                if (it.forms.isNotEmpty()) {
                    it.nested(RecordForm::forms) {
                        it.varargs {
                            val sym = it.expectForm<SymbolForm>().sym
                            nsHeader = when (sym) {
                                REFERS -> {
                                    if (nsHeader.refers.isNotEmpty()) TODO()
                                    nsHeader.copy(refers = it.nested(RecordForm::forms, Companion::refersAnalyser))
                                }

                                ALIASES -> {
                                    if (nsHeader.aliases.isNotEmpty()) TODO()
                                    nsHeader.copy(aliases = it.nested(RecordForm::forms) { aliasesAnalyser(it, ns) })
                                }

                                else -> TODO()
                            }
                        }
                    }
                }

                it.expectEnd()
                nsHeader
            }
    }
}

