package brj

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.TypeLiteral
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlin.test.assertEquals

@TestInstance(PER_CLASS)
class BridjeLanguageTest {
    lateinit var os: ByteArrayOutputStream
    lateinit var ctx: Context

    @BeforeEach
    fun setUp() {
        os = ByteArrayOutputStream()
        ctx = Context.newBuilder().allowAllAccess(true).out(os).build()
        ctx.enter()
    }

    @AfterEach
    fun tearDown() {
        ctx.leave()
        ctx.close()
    }

    private fun eval(src: String) = ctx.eval("brj", src)

    @Test
    fun `test nil`() {
        assertTrue(eval("nil").isNull)
    }

    @Test
    fun `just a number`() {
        assertEquals(10, eval("10").asInt())
    }

    private val listOfInt = object : TypeLiteral<List<Int>>() {}
    private val setOfInt = object : TypeLiteral<Set<Int>>() {}

    @Test
    fun `vector test`() {
        val value = eval("[4 10]")
        assertEquals(listOf(4, 10), value.`as`(listOfInt))
    }

    @Test
    fun `let test`() {
        val value = eval("(let [x 4, y 10] [x y])")
        assertEquals(listOf(4, 10), value.`as`(listOfInt))
    }

    @Test
    fun `exposes value in scope`() {
        eval("(def x 10)")
        eval("""(def y "Hello")""")
        val bindings = ctx.getBindings("brj")
        assertTrue(bindings.memberKeys.containsAll(setOf("x", "y")))
        assertEquals(10, bindings.getMember("x").asInt())
        assertEquals("Hello", bindings.getMember("y").asString())
        assertEquals(10, eval("x").asInt())
    }

    @Test
    fun `basic fn expr`() {
        eval("(def (simple-vec x) [x])")
        val bindings = ctx.getBindings("brj")
        assertEquals(
            listOf(10),
            bindings.getMember("simple-vec")
                .execute(10)
                .`as`(listOfInt)
        )
    }

    @Test
    fun `call expr`() {
        assertEquals(
            listOf(10, 5),
            eval("((fn [x y] [y x]) 5 10)")
                .`as`(listOfInt)
        )
    }

    @Test
    fun `test multiple exprs`() {
        assertEquals(10, eval("(def x 10) x").asInt())
    }

    @Test
    fun `test higher order fn`() {
        assertEquals(
            listOf(3),
            eval("((fn [f] (f 3)) (fn [x] [x]))")
                .`as`(listOfInt)
        )
    }

    @Test
    fun `rebound def`() {
        assertEquals(10, eval("(def x 10) x").asInt())
        assertEquals(10, eval("(def (y) x) (y)").asInt())
        assertEquals(15, eval("(def x 15) (y)").asInt())
    }

    @Test
    fun `test defx builtin`() {
        eval("""(println! "hello world!")""")
        assertEquals("hello world!\n", os.toString(StandardCharsets.UTF_8))
    }

    @Test
    fun `test top-level do`() {
        assertEquals(42, eval("(do (def x 42) x)").asInt())
    }

    @Test
    fun `test now-ms`() {
        val beforeMs = System.currentTimeMillis()
        val duringMs = eval("(now!)").asInstant().toEpochMilli()
        val afterMs = System.currentTimeMillis()

        assertTrue(duringMs in beforeMs..afterMs)
    }

    @Test
    fun `test with-fx`() {
        eval("(import java.time.Instant)")
        assertEquals(Instant.EPOCH, eval("(with-fx [(def (now!) Instant/EPOCH)] (now!))").asInstant())

        assertEquals(Instant.EPOCH, eval("(with-fx [(def (now!) Instant/EPOCH)] (with-fx [] (now!)))").asInstant())

        val beforeMs = System.currentTimeMillis()
        val duringMs = eval("(with-fx [] (now!))").asInstant()
        val afterMs = System.currentTimeMillis()

        assertTrue(duringMs.toEpochMilli() in beforeMs..afterMs)

        eval("(def (now-caller) (now!))")

        assertEquals(0, eval("(with-fx [(def (now!) 0)] (now-caller))").asLong())
    }

    @Test
    fun `test loop-recur`() {
        assertEquals(
            (4 downTo 0).toList(),
            eval("(loop [x 5, res []] (if (zero? x) res (recur (dec x) (.conj res x))))").`as`(listOfInt)
        )
    }

    @Test
    @Disabled("bug to be fixed")
    fun `test recur binds in parallel`() {
        assertEquals(1, eval("(loop [x 1, y 1] (if (zero? x) y (recur (dec x) x)))").asInt())
    }

    @Test
    fun `test poly`() {
        assertEquals(0, eval("""(poly "python" "import math; math")""").invokeMember("acos", 1).asInt())
    }

    @Test
    fun `test record`() {
        assertEquals(setOf("foo"), eval("""{:foo 42}""").memberKeys)
        assertEquals(42, eval("""{:foo 42}""").getMember("foo").asInt())

        assertEquals(42, eval("""(:foo {:foo 42})""").asInt())

        assertEquals(42, eval(":foo").execute(eval("{:foo 42}")).asInt())

        assertEquals(42, eval("""(:max java.lang.Math)""").execute(42, 10).asInt())
        assertEquals(42, eval("""((:max java.lang.Math) 42 10)""").asInt())
    }

    @Test
    fun `test new`() {
        val inst = Instant.ofEpochMilli(1000)
        assertEquals(inst, eval("(new java.util.Date 1000)").asInstant())
        assertEquals(inst, eval("(java.util.Date. 1000)").asInstant())
        assertEquals(inst, eval("(java.util.Date. 1000)").asInstant())

        eval("(import java.util.Date)")
        assertEquals(inst, eval("""(Date. 1000)""").asInstant())
    }

    @Test
    fun `test pr-str`() {
        assertEquals("nil", eval("pr-str").execute(null).asString())
    }

    @Test
    fun `test import`() {
        assertEquals(
            Instant.ofEpochMilli(1000),
            eval("(import java.util.Date java.time.Instant) (new Date 1000)").asInstant()
        )
        assertEquals(Instant.EPOCH, eval("Instant/EPOCH").asInstant())
        assertTrue(eval("(Instant/now)").isInstant)
    }

    @Test
    fun `test invoke`() {
        eval("(import java.time.Instant java.time.Duration)")
        assertEquals(Instant.ofEpochSecond(1), eval("(.plus Instant/EPOCH (Duration/ofSeconds 1))").asInstant())
    }

    @Test
    fun `can instantiate keywords`() {
        val fooKey = eval(":Foo")
        assertTrue(fooKey.canInstantiate())

        fun testFoo(foo: Value) {
            assertEquals("Foo", foo.metaObject.metaSimpleName)
            assertEquals(12, foo.getMember("foo").asInt())
            assertEquals("(:Foo {:foo 12})", foo.toString())
        }

        testFoo(eval("(new :Foo {:foo 12})"))
        testFoo(eval("(:Foo. {:foo 12})"))
        testFoo(eval(":Foo.").execute(eval("{:foo 12}")))
    }

    @Test
    fun `test case`() {
        assertEquals(42, eval("(case nil, nil 42, 12)").asInt())
        assertEquals(12, eval("(case :foo, nil 42, 12)").asInt())
        assertEquals(12, eval("(case (:Foo. {:foo 12}), nil 42, (:Foo e) (:foo e), 25)").asInt())
    }

    @Test
    fun `test conj`() {
        assertEquals(listOf(1, 2, 3), eval("(.conj [1 2] 3)").`as`(listOfInt))

        // TODO as setOfInt doesn't seem to return something that implements Set properly
//        assertEquals(setOf(1, 2, 3), eval("(.conj #{1 2} 3)").`as`(setOfInt))

        assertEquals(setOf(1, 2, 3), eval("(.conj [1 2] 3)").`as`(listOfInt).toSet())
    }

    @Test
    fun `test reducing iterables`() {
        assertEquals(15, eval("(reduce + 0 [1 2 3 4 5])").asInt())
        assertEquals(15, eval("(fn [coll] (reduce + 0 coll))").execute(listOf(1, 2, 3, 4, 5)).asInt())
    }
}
