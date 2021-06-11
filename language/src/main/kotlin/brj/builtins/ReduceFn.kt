package brj.builtins

import brj.BridjeLanguage
import brj.runtime.BridjeFunction
import brj.runtime.FxMap
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.nodes.DirectCallNode
import com.oracle.truffle.api.nodes.UnexpectedResultException

@BuiltIn("reduce")
abstract class ReduceFn(lang: BridjeLanguage) : BuiltInFn(lang) {
    @field:Child
    private var fLib = InteropLibrary.getFactory().createDispatched(3)

    @field:Child
    private var collLib = InteropLibrary.getFactory().createDispatched(3)

    @field:Child
    private var iteratorLib = InteropLibrary.getFactory().createDispatched(3)

    @Specialization
    fun doReduce(
        fx: FxMap, fn: BridjeFunction, init: Any, coll: Any,
        @Cached("create(fn.getCallTarget())") callNode: DirectCallNode
    ): Any {
        if (!collLib.hasIterator(coll)) throw UnexpectedResultException(coll)

        val iterator = collLib.getIterator(coll)
        var res = init

        while (iteratorLib.hasIteratorNextElement(iterator)) {
            res = callNode.call(fx, res, iteratorLib.getIteratorNextElement(iterator))
        }

        return res
    }

    @Specialization
    fun doReduce(fx: FxMap, f: Any, init: Any, coll: Any): Any {
        if (!fLib.isExecutable(f)) throw UnexpectedResultException(f)
        if (!collLib.hasIterator(coll)) throw UnexpectedResultException(coll)

        val iterator = collLib.getIterator(coll)
        var res = init

        while (iteratorLib.hasIteratorNextElement(iterator)) {
            res = fLib.execute(f, res, iteratorLib.getIteratorNextElement(iterator))
        }

        return res
    }
}