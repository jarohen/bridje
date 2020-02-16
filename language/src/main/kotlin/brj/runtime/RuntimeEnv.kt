@file:Suppress("NestedLambdaShadowedImplicitParameter")

package brj.runtime

import brj.BridjeContext
import brj.BridjeLanguage
import brj.emitter.BridjeObject
import brj.reader.Form
import brj.types.*
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.CachedContext
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

internal abstract class GlobalVar {
    abstract val sym: QSymbol
    abstract val type: Type
    abstract val value: Any?
}

internal data class DefVar(override val sym: QSymbol, override val type: Type, override var value: Any?) : GlobalVar()

internal data class EffectVar(override val sym: QSymbol, override val type: Type, var defaultImpl: BridjeFunction?, override var value: Any) : GlobalVar()

internal data class DefMacroVar(private val truffleEnv: TruffleLanguage.Env, override val sym: QSymbol, override val type: Type, override val value: BridjeFunction) : GlobalVar() {
    private val paramTypes = (type.monoType as FnType).paramTypes
    private val isVarargs = paramTypes.last() is VectorType
    private val fixedArgCount = if (isVarargs) paramTypes.size - 1 else paramTypes.size

    fun evalMacro(argForms: List<Form>): Form {
        val args = listOf(value.lexObj) + argForms.take(fixedArgCount) + listOfNotNull(if (isVarargs) argForms.drop(fixedArgCount) else null)

        return value.callTarget.call(*(args.toTypedArray())) as Form
    }
}

internal data class RecordKey(val sym: QSymbol, val typeVars: List<TypeVarType>, val type: MonoType) {
    override fun toString() = sym.toString()
}

internal data class RecordKeyVar(val recordKey: RecordKey, override val value: Any) : GlobalVar() {
    override val sym = recordKey.sym
    override val type: Type = RecordType.accessorType(recordKey)
}

internal data class VariantKey(val sym: QSymbol, val typeVars: List<TypeVarType>, val paramTypes: List<MonoType>) {
    override fun toString() = sym.toString()
}

internal data class VariantKeyVar(val variantKey: VariantKey, override val value: Any) : GlobalVar() {
    override val sym = variantKey.sym
    override val type: Type = VariantType.constructorType(variantKey)
}

internal data class JavaImport(val qsym: QSymbol, val clazz: Symbol, val name: String, val type: Type)

internal class JavaImportVar(javaImport: JavaImport, override val value: Any) : GlobalVar() {
    override val sym = javaImport.qsym
    override val type = javaImport.type
}

internal sealed class TypeAlias(open val sym: QSymbol, open val typeVars: List<TypeVarType>, open val type: MonoType?)
internal class TypeAlias_(override val sym: QSymbol, override val typeVars: List<TypeVarType> = emptyList(), override var type: MonoType?) : TypeAlias(sym, typeVars, type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeAlias) return false
        if (sym != other.sym) return false
        return true
    }

    override fun hashCode(): Int {
        return sym.hashCode()
    }
}

@ExportLibrary(InteropLibrary::class)
internal data class NSEnv(val ns: Symbol,
                          val typeAliases: Map<Symbol, TypeAlias> = emptyMap(),
                          val vars: Map<Symbol, GlobalVar> = emptyMap()) : BridjeObject {

    operator fun plus(globalVar: GlobalVar): NSEnv = copy(vars = vars + (globalVar.sym.local to globalVar))
    operator fun plus(alias: TypeAlias) = copy(typeAliases = typeAliases + (alias.sym.local to alias))

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    @TruffleBoundary
    fun getMembers(includeInternal: Boolean, @CachedContext(BridjeLanguage::class) ctx: BridjeContext): Any =
        ctx.truffleEnv.asGuestValue(vars.keys.toList().map { it.baseStr })

    @ExportMessage
    @TruffleBoundary
    fun isMemberReadable(k: String) = vars.containsKey(Symbol(k))

    @ExportMessage
    @TruffleBoundary
    fun readMember(k: String) = vars[Symbol(k)]?.value

    @ExportMessage
    @TruffleBoundary
    fun isMemberInvocable(k: String) = isMemberReadable(k) && readMember(k) is BridjeFunction

    @ExportMessage
    @TruffleBoundary
    fun invokeMember(k: String, args: Array<Any>) = (readMember(k) as BridjeFunction).execute(args)
}

@ExportLibrary(InteropLibrary::class)
internal class RuntimeEnv(val nses: Map<Symbol, NSEnv> = mapOf()) : BridjeObject {
    operator fun plus(newNsEnv: NSEnv) = RuntimeEnv(nses + (newNsEnv.ns to newNsEnv))
    operator fun plus(newNsEnvs: Iterable<NSEnv>) = RuntimeEnv(nses + newNsEnvs.map { it.ns to it })

    @ExportMessage
    fun hasMembers() = true

    @ExportMessage
    @TruffleBoundary
    fun getMembers(includeInternal: Boolean, @CachedContext(BridjeLanguage::class) ctx: BridjeContext): Any =
        ctx.truffleEnv.asGuestValue(nses.keys.toList().map { it.baseStr })

    @ExportMessage
    @TruffleBoundary
    fun isMemberReadable(k: String) = nses.containsKey(Symbol(k))

    @ExportMessage
    @TruffleBoundary
    fun readMember(k: String) = nses[Symbol(k)]
}
