package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Layout
import arcanum.api.Step
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pivot review #3 (I-D52, STK-030, SYS-064): every game is played by the rules, so a step the engine still leaves to the
 * players is an engine gap: Automatum answers it Done at once, logs it for a report (I-D43) and says it in the history
 * with an event of its own ([MtgAction.Skipped]); nobody is ever asked to do something by hand.
 */
class EngineGapTest {
    private val table = AtwTable("enginegap")
    private val alice get() = table.alice
    private val bob get() = table.bob

    @Test fun aStepByHandIsSkippedAndSaid() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        val odd = table.put(alice, PlayZone.HAND, "Storm Spark").single()
        table.put(alice, PlayZone.LIBRARY, "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island")
        // The hand card is an instant whose whole text is a step the engine leaves to the players
        val state = table.state()
        val digest = state.board.cards.single { it.id == odd }.definition
        table.definitions[digest] = CardDefinition(Layout.Normal, listOf(FaceDefinition("Storm Spark", manaCost = arcanum.api.ManaCost.parse("{R}"),
            types = listOf(CardType.Instant), abilities = listOf(Ability.Spell(Instructions(steps = listOf(
                Step.Manual("Rearrange the permanents on the battlefield however you like."))))))))
        val rt = RoundTrip(state, table.definitions)
        rt.cast(alice, "Storm Spark"); rt.settle()
        rt.resolveAll()
        assertNull(rt.engine().pending, "nothing waits for the players")
        assertTrue(rt.engine().stack.isEmpty(), "the spell resolved")
        val skipped = rt.state.let { _ -> rt.lastPlan!!.actions.filterIsInstance<PlayAction.Mtg>().map { it.action }.filterIsInstance<MtgAction.Skipped>() }
        assertEquals(listOf("Rearrange the permanents on the battlefield however you like."), skipped.map { it.text })
        val at = rt.lastPlan!!.actions.indexOfFirst { (it as? PlayAction.Mtg)?.action is MtgAction.Skipped }
        assertTrue(rt.lastPlan!!.tags.getOrNull(at)?.folded != true, "an event of its own in the timeline")
    }
}
