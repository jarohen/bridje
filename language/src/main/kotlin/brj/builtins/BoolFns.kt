package brj.builtins

import brj.BridjeLanguage
import com.oracle.truffle.api.dsl.Specialization

@BuiltIn("==")
abstract class EqualsFn(lang: BridjeLanguage) : BuiltInFn(lang) {
    @Specialization
    fun doEquals(left: Int, right: Int) = left == right
}

@BuiltIn(">=")
abstract class GreaterThanEqualsFn(lang: BridjeLanguage) : BuiltInFn(lang) {
    @Specialization
    fun doGTE(left: Int, right: Int) = left >= right
}

@BuiltIn(">")
abstract class GreaterThanFn(lang: BridjeLanguage) : BuiltInFn(lang) {
    @Specialization
    fun doGT(left: Int, right: Int) = left > right
}

@BuiltIn("<")
abstract class LessThanFn(lang: BridjeLanguage) : BuiltInFn(lang) {
    @Specialization
    fun doLT(left: Int, right: Int) = left < right
}

@BuiltIn("<=")
abstract class LessThanEqualsFn(lang: BridjeLanguage) : BuiltInFn(lang) {
    @Specialization
    fun doLTE(left: Int, right: Int) = left <= right
}

@BuiltIn("zero?")
abstract class IsZeroFn(lang: BridjeLanguage) : BuiltInFn(lang) {
    @Specialization
    fun doIsZero(x: Int) = x == 0
}
