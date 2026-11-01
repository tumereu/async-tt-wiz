package arcanum.engine.change

import kotlin.test.Test
import kotlin.test.assertEquals

/** The reversal samples ([ChangeSamples]) cover every [Change] kind, so a new kind can't skip the D59 test. */
class ChangeKindsCoveredTest {
    @Test
    fun everyChangeKindHasASample() {
        val kinds = Change::class.sealedSubclasses.map { it.simpleName }.toSet()
        val sampled = ChangeSamples.all().second.map { it.second::class.simpleName }.toSet()
        assertEquals(kinds, sampled)
    }
}
