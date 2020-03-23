package brj

import brj.analyser.CallExpr
import brj.analyser.GlobalVarExpr
import brj.analyser.LocalVar
import brj.analyser.LocalVarExpr
import brj.runtime.QSymbol
import brj.runtime.RecordKey
import brj.runtime.RecordKeyVar
import brj.runtime.Symbol
import brj.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import kotlin.test.assertEquals

@TestInstance(PER_CLASS)
class RecordTypeTest {

    private val firstNameKey = RecordKey(QSymbol("record-test/first-name"), emptyList(), StringType)
    private val middleNameKey = RecordKey(QSymbol("record-test/middle-name"), emptyList(), StringType)
    private val lastNameKey = RecordKey(QSymbol("record-test/last-name"), emptyList(), StringType)

    private var userTV = TypeVarType()
    private val userKey = RecordKey(QSymbol("record-test/user"), listOf(userTV), RecordType(typeVar = userTV))

    private fun assertUnified(expected: RecordType, left: RecordType, right: RecordType) {
        val mapping = unifyEqs(listOf(TypeEq(left, right))).also { println(it) }
        val mappedLeft = left.applyMapping(mapping)
        val mappedRight = right.applyMapping(mapping)

        assertEquals(mappedLeft.typeVar, mappedRight.typeVar)

        assertEquals(expected.copy(typeVar = mappedLeft.typeVar), mappedLeft)
        assertEquals(expected.copy(typeVar = mappedRight.typeVar), mappedRight)
    }

//    @Test
//    internal fun `test has-keys unifies against empty map`() {
//        assertUnified(
//            expected = RecordType(
//                hasKeys = setOf(firstNameKey, lastNameKey),
//                keyTypes = mapOf(firstNameKey to emptyList(), lastNameKey to emptyList()),
//                typeVar = TypeVarType()),
//            left = RecordType(
//                hasKeys = setOf(firstNameKey, lastNameKey),
//                keyTypes = mapOf(firstNameKey to emptyList(), lastNameKey to emptyList()),
//                typeVar = TypeVarType()
//            ),
//            right = RecordType(typeVar = TypeVarType()))
//    }
//
//    @Test
//    internal fun `test has-keys unifies`() {
//        assertUnified(
//            expected = RecordType(
//                hasKeys = setOf(firstNameKey),
//                keyTypes = mapOf(firstNameKey to emptyList(), middleNameKey to emptyList(), lastNameKey to emptyList()),
//                typeVar = TypeVarType()),
//
//            left = RecordType(
//                hasKeys = setOf(firstNameKey, middleNameKey),
//                keyTypes = mapOf(firstNameKey to emptyList(), middleNameKey to emptyList()),
//                typeVar = TypeVarType()),
//
//            right = RecordType(
//                hasKeys = setOf(firstNameKey, lastNameKey),
//                keyTypes = mapOf(firstNameKey to emptyList(), lastNameKey to emptyList()),
//                typeVar = TypeVarType()))
//    }
//
//    @Test
//    internal fun `test needs-keys unifies against empty map`() {
//        assertUnified(
//            expected = RecordType(
//                needsKeys = setOf(firstNameKey, lastNameKey),
//                keyTypes = mapOf(firstNameKey to emptyList(), lastNameKey to emptyList()),
//                typeVar = TypeVarType()),
//            left = RecordType(
//                needsKeys = setOf(firstNameKey, lastNameKey),
//                keyTypes = mapOf(firstNameKey to emptyList(), lastNameKey to emptyList()),
//                typeVar = TypeVarType()
//            ),
//            right = RecordType(typeVar = TypeVarType()))
//    }
//
//    @Test
//    internal fun `test needs-keys unifies`() {
//        assertUnified(
//            expected = RecordType(
//                needsKeys = setOf(firstNameKey, middleNameKey, lastNameKey),
//                keyTypes = mapOf(firstNameKey to emptyList(), middleNameKey to emptyList(), lastNameKey to emptyList()),
//                typeVar = TypeVarType()),
//
//            left = RecordType(
//                needsKeys = setOf(firstNameKey, middleNameKey),
//                keyTypes = mapOf(firstNameKey to emptyList(), middleNameKey to emptyList()),
//                typeVar = TypeVarType()),
//
//            right = RecordType(
//                needsKeys = setOf(middleNameKey, lastNameKey),
//                keyTypes = mapOf(middleNameKey to emptyList(), lastNameKey to emptyList()),
//                typeVar = TypeVarType()))
//    }

    @Test
    internal fun `test call`() {
        val firstNameVar = RecordKeyVar(firstNameKey, Any())
        val userVar = RecordKeyVar(userKey, Any())

        val fx = LocalVar(Symbol("_fx"))
        val lv = LocalVar(Symbol("m"))
        val userCall = CallExpr(
            GlobalVarExpr(userVar, fx),
            listOf(LocalVarExpr(lv)),
            LocalVar(Symbol("_lv")))

//        println(valueExprTyping(userCall, null).monoEnv[lv])

        println(
            valueExprTyping(
                CallExpr(GlobalVarExpr(firstNameVar, fx), listOf(userCall), fx)
            )
                .monoEnv[lv])
    }
}
