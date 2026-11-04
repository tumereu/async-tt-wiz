package arcanum.engine.decision

import arcanum.api.CostPart
import arcanum.engine.casting.Question
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Names that go into ids, keys and the choices a player answers are written out, never taken from a class at run time
 * (problem report N7FU6Y: a shrunk Android build named a nested class "Question$Pay" where the JVM says "Pay", so the
 * server refused every payment a phone made). Each written name is the class's JVM name, so ids and keys already kept
 * in games stay the same; a new kind must be named, and named the same.
 */
class WrittenNamesTest {
    // Decision.kind names the early answers a table keeps (EarlyAnswer.keyOf)
    @Test
    fun everyDecisionKindIsItsJvmName() = assertNamed(Decision::class) { it.kind }

    // Question.kind names a cast's decision ids ("cast:Mana:Pay@…")
    @Test
    fun everyCastQuestionKindIsItsJvmName() = assertNamed(Question::class) { it.kind }

    // Texts.costName words a cost part in the options of a cost choice ("Pay energy or tap objects")
    @Test
    fun everyCostPartNameIsItsJvmName() = assertNamed(CostPart::class) { Texts.costName(it) }

    /** For every subclass of [base], [name] of an instance of it (made without a constructor) is its JVM simple name. */
    private fun <T : Any> assertNamed(base: KClass<T>, name: (T) -> String) {
        val leaves = generateSequence(listOf<KClass<out T>>(base)) { level -> level.flatMap { it.sealedSubclasses }.ifEmpty { null } }
            .flatten().filter { it.sealedSubclasses.isEmpty() && !it.java.isInterface }.toList()
        check(leaves.size > 2) { "No kinds found under ${base.simpleName}" }
        for (kind in leaves) {
            @Suppress("UNCHECKED_CAST")
            val instance = (kind.objectInstance ?: allocate(kind.java)) as T
            assertEquals(kind.simpleName, name(instance), "the written name of ${kind.qualifiedName}")
        }
    }

    private fun allocate(type: Class<*>): Any {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type)
    }
}
