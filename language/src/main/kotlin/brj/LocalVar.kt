package brj

import brj.runtime.Symbol

internal class LocalVar(val symbol: Symbol) {
    override fun toString() = "$symbol"
}