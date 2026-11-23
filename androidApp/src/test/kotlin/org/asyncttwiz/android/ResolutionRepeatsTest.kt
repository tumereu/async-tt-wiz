package org.asyncttwiz.android

import androidx.compose.ui.test.junit4.v2.createComposeRule
import arcanum.engine.state.PlayerId
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-22 (trigger timing clips): a board wipe's "dies" triggers resolve faster each time (TBL-150, HIS-138), the
 * second too. The first changed only a life total: the table never settled on its resolution, and the chain of repeats began
 * again with the second. Alice's whole app ([LiveTableKit], [TableScreen]) on the test's clock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ResolutionRepeatsTest {
    @get:Rule val compose = createComposeRule()
    private var screen: LiveTableScreen? = null

    @After fun close() { screen?.close() }

    private fun failure(what: String) = "$what; the table's moments:\n" + screen!!.moments.takeLast(60).joinToString("\n")

    // TBL-150, HIS-138: Alice's Ember Unmaking destroys Bob's four Ash Mourners; his four "When this creature dies" triggers
    // resolve on her table one after another, each a repeat of the one before (its effect's animations shorter each time)
    @Test fun aBoardWipesDiesTriggersResolveFasterEachTime() {
        val s = LiveTableScreen(compose, TriggerTimingGames.unmakingAtMourners()).also { screen = it }
        s.open()
        s.tapInHand(Choices.UNMAKING)
        s.run(1_000)
        s.tapInHand(Choices.UNMAKING)
        val bob = PlayerId(s.kit.f.b)
        assertTrue(s.runUntil(30_000) { s.kit.alice.shown?.let { it.status == GameDevice.Status.YourMove && it.engine.player(bob).life == 24 } == true },
            failure("the four triggers resolved, Bob at 24"))
        // The table drawing the last resolution
        s.run(1_000)
        // The Stack animation's own log line for each resolution (step 5) says which repeat it is
        val repeats = s.moments.mapNotNull { Regex("""leaves the Stack \(step 5\).*repeat (\d+)""").find(it)?.groupValues?.get(1)?.toInt() }
        assertEquals(listOf(0, 1, 2, 3), repeats, failure("each resolution a repeat of the one before"))
    }
}
