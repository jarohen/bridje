package brj.analyser

import brj.emitter.Ident
import brj.emitter.QSymbol
import brj.emitter.Symbol
import brj.emitter.SymbolKind
import brj.runtime.GlobalVar
import brj.runtime.NSEnv
import brj.runtime.RuntimeEnv
import brj.runtime.TypeAlias

internal interface Resolver {
    fun resolveVar(ident: Ident): GlobalVar?
    fun resolveTypeAlias(ident: Ident): TypeAlias?

    data class NSResolver(val env: RuntimeEnv? = null,
                          val referVars: Map<Symbol, GlobalVar> = emptyMap(),
                          val referTypeAliases: Map<Symbol, TypeAlias> = emptyMap(),
                          val aliases: Map<Symbol, NSEnv> = emptyMap(),
                          val nsEnv: NSEnv? = null) : Resolver {

        override fun resolveVar(ident: Ident) =
            nsEnv?.vars?.get(ident) ?: when (ident) {
                is Symbol -> referVars[ident]
                is QSymbol -> (aliases[ident.ns] ?: env?.nses?.get(ident.ns) ?: TODO("can't find NS")).vars[ident.base]
            }

        override fun resolveTypeAlias(ident: Ident) =
            nsEnv?.typeAliases?.get(ident)
                ?: when (ident) {
                    is Symbol -> nsEnv?.typeAliases?.get(ident) ?: referTypeAliases[ident]
                    is QSymbol -> (aliases[ident.ns] ?: env?.nses?.get(ident.ns) ?: TODO("can't find NS")).typeAliases[ident.base]
                }

        companion object {
            fun create(env: RuntimeEnv, nsHeader: NSHeader) =
                NSResolver(env,
                    referVars = nsHeader.refers.filterKeys { it.symbolKind != SymbolKind.TYPE_ALIAS_SYM }.entries.associate {
                        it.key to
                            ((env.nses[it.value.ns] ?: TODO("can't find ${it.value.ns} NS"))
                                .vars[it.value.base] ?: TODO("can't find refer ${it.value}"))
                    },
                    referTypeAliases = nsHeader.refers.filterKeys { it.symbolKind == SymbolKind.TYPE_ALIAS_SYM }.entries.associate {
                        it.key to
                            ((env.nses[it.value.ns] ?: TODO("can't find ${it.value.ns} NS"))
                                .typeAliases[it.value.base] ?: TODO("can't find refer ${it.value}"))
                    },
                    aliases = nsHeader.aliases.entries.map { (aliasSym, alias) ->
                        aliasSym to (env.nses[alias.ns] ?: TODO("can't find ${alias.ns} NS"))

                    }.toMap())
        }

    }
}