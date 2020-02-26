package brj

import brj.runtime.QSymbol
import brj.runtime.RecordKey
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

    private fun assertUnified(expected: RecordType, left: RecordType, right: RecordType) {
        println(left)
        println(right)
        val mapping = unifyEqs(listOf(TypeEq(left, right))).also {println(it)}
        val mappedLeft = left.applyMapping(mapping)
        val mappedRight = right.applyMapping(mapping)

        assertEquals(mappedLeft.typeVar, mappedRight.typeVar)

        assertEquals(expected.copy(typeVar = mappedLeft.typeVar), mappedLeft)
        assertEquals(expected.copy(typeVar = mappedRight.typeVar), mappedRight)
    }

    @Test
    internal fun `test has-keys unifies against empty map`() {
        assertUnified(
            expected = RecordType(
                hasKeys = setOf(firstNameKey, lastNameKey),
                keyTypes = mapOf(firstNameKey to emptyList(), lastNameKey to emptyList()),
                typeVar = TypeVarType()),
            left = RecordType(
                hasKeys = setOf(firstNameKey, lastNameKey),
                keyTypes = mapOf(firstNameKey to emptyList(), lastNameKey to emptyList()),
                typeVar = TypeVarType()
            ),
            right = RecordType(typeVar = TypeVarType()))
    }

    @Test
    internal fun `test has-keys unifies`() {
        assertUnified(
            expected = RecordType(
                hasKeys = setOf(firstNameKey),
                keyTypes = mapOf(firstNameKey to emptyList(), middleNameKey to emptyList(), lastNameKey to emptyList()),
                typeVar = TypeVarType()),

            left = RecordType(
                hasKeys = setOf(firstNameKey, middleNameKey),
                keyTypes = mapOf(firstNameKey to emptyList(), middleNameKey to emptyList()),
                typeVar = TypeVarType()),

            right = RecordType(
                hasKeys = setOf(firstNameKey, lastNameKey),
                keyTypes = mapOf(firstNameKey to emptyList(), lastNameKey to emptyList()),
                typeVar = TypeVarType()))
    }

    @Test
    internal fun `test needs-keys unifies against empty map`() {
        assertUnified(
            expected = RecordType(
                needsKeys = setOf(firstNameKey, lastNameKey),
                keyTypes = mapOf(firstNameKey to emptyList(), lastNameKey to emptyList()),
                typeVar = TypeVarType()),
            left = RecordType(
                needsKeys = setOf(firstNameKey, lastNameKey),
                keyTypes = mapOf(firstNameKey to emptyList(), lastNameKey to emptyList()),
                typeVar = TypeVarType()
            ),
            right = RecordType(typeVar = TypeVarType()))
    }

    @Test
    internal fun `test needs-keys unifies`() {
        assertUnified(
            expected = RecordType(
                needsKeys = setOf(firstNameKey, middleNameKey, lastNameKey),
                keyTypes = mapOf(firstNameKey to emptyList(), middleNameKey to emptyList(), lastNameKey to emptyList()),
                typeVar = TypeVarType()),

            left = RecordType(
                needsKeys = setOf(firstNameKey, middleNameKey),
                keyTypes = mapOf(firstNameKey to emptyList(), middleNameKey to emptyList()),
                typeVar = TypeVarType()),

            right = RecordType(
                needsKeys = setOf(middleNameKey, lastNameKey),
                keyTypes = mapOf(middleNameKey to emptyList(), lastNameKey to emptyList()),
                typeVar = TypeVarType()))
    }
}
