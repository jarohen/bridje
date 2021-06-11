package brj.builtins

import brj.BridjeLanguage
import com.oracle.truffle.api.dsl.Specialization

@BuiltIn("inc")
abstract class IncFn(lang: BridjeLanguage) : BuiltInFn(lang) {
    @Specialization
    fun doInc(param: Int) = param + 1
}

@BuiltIn("dec")
abstract class DecFn(lang: BridjeLanguage) : BuiltInFn(lang) {
    @Specialization
    fun doDec(param: Int) = param - 1
}

@BuiltIn("+")
abstract class PlusFn(lang: BridjeLanguage) : BuiltInFn(lang) {
    @Specialization
    fun doPlus(left: Int, right: Int) = left + right
}

@BuiltIn("-")
abstract class MinusFn(lang: BridjeLanguage) : BuiltInFn(lang) {
    @Specialization
    fun doMinus(left: Int, right: Int) = left - right
}

@BuiltIn("*")
abstract class MultiplyFn(lang: BridjeLanguage) : BuiltInFn(lang) {
    @Specialization
    fun doMultiply(left: Int, right: Int) = left * right
}

@BuiltIn("/")
abstract class DivideFn(lang: BridjeLanguage) : BuiltInFn(lang) {
    @Specialization
    fun doDivide(left: Int, right: Int) = left / right
}
