package brj

import brj.SymbolType.*

private class Interner<K, V>(val f: (K) -> V) {
    val interned = mutableMapOf<K, V>()

    fun intern(k: K): V {
        return interned.getOrPut(k) { f(k) }
    }
}

internal enum class SymbolType {
    VAR_SYM, KEY_SYM, VARIANT_SYM, TYPE_ALIAS_SYM, POLYVAR_SYM
}

private fun symbolType(sym: Symbol): SymbolType {
    val firstIsUpper = sym.baseStr.first().isUpperCase()
    val firstIsDot = sym.baseStr.first() == '.'

    return if (sym.isKeyword) {
        if (firstIsUpper) VARIANT_SYM else KEY_SYM
    } else {
        if (firstIsUpper) TYPE_ALIAS_SYM else if (firstIsDot) POLYVAR_SYM else VAR_SYM
    }
}

class Symbol private constructor(val isKeyword: Boolean, val baseStr: String) {
    private val stringRep = "${if (isKeyword) ":" else ""}$baseStr"
    internal val symbolType = brj.symbolType(this)

    companion object {
        private val INTERNER: Interner<String, Symbol> = Interner {
            val groups = Regex("(:)?(.+)").matchEntire(it)!!.groups
            Symbol(isKeyword = groups[1] != null, baseStr = groups[2]!!.value.intern())
        }

        fun mkSym(str: String) = INTERNER.intern(str)
    }

    override fun toString() = stringRep
}

class QSymbol private constructor(val isKeyword: Boolean, val ns: Symbol, val base: Symbol) {
    private val stringRep = "${if (isKeyword) ":" else ""}$ns/$base"

    companion object {
        private val INTERNER: Interner<String, QSymbol> = Interner {
            val groups = Regex("(:)?(.+?)/(.+)").matchEntire(it)!!.groups
            QSymbol(isKeyword = groups[1] != null, ns = Symbol.mkSym(groups[2]!!.value), base = Symbol.mkSym(groups[3]!!.value))
        }

        fun mkQSym(str: String) = INTERNER.intern(str)
    }

    override fun toString() = stringRep
}

