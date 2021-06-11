package brj.runtime

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class BridjeSet(val els: Array<Any>) : TruffleObject {
    @ExportMessage
    fun hasArrayElements() = true

    @ExportMessage
    fun getArraySize() = els.size

    @ExportMessage
    fun isArrayElementReadable(idx: Long) = idx >= 0 && idx < els.size

    @ExportMessage
    fun readArrayElement(idx: Long) = els[idx.toInt()]

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    fun getMembers(includeInternal: Boolean) = Keys(arrayOf("conj"))

    @ExportMessage
    fun isMemberInvocable(member: String) = member == "conj"

    @ExportMessage
    class InvokeMember {
        companion object {
            @Specialization
            @JvmStatic
            fun doExecute(set: BridjeSet, member: String, args: Array<Any>): Any {
                return when (member) {
                    "conj" -> {
                        if (args.size != 1) throw ArityException.create(1, args.size)
                        BridjeSet(set.els + args[0])
                    }
                    else -> throw UnsupportedMessageException.create()
                }
            }
        }
    }


    companion object {
        private val interopLibrary = InteropLibrary.getUncached()
    }

    @ExportMessage
    @TruffleBoundary
    fun toDisplayString(allowSideEffects: Boolean) =
        "#{${els.map(interopLibrary::toDisplayString).joinToString(", ")}}"
}