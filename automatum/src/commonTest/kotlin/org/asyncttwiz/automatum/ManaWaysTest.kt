package org.asyncttwiz.automatum

import arcanum.api.Color
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * STK-024 (owner playtest 2026-11-09): a tap on a source with several ways to pay takes the sensible one: the way after which the
 * rest of the cost can still be paid (the engine's own fill of the payment with that way kept), and where the spell cares about
 * the colours spent on it, the way that serves that first.
 */
class ManaWaysTest {
    private val table = EngineTable()
    private val alice get() = table.alice

    private val ford = Cards.invent("Wayfinder Ford", null, "Land", "{T}: Add {W} or {U}.")
    private val grove = Cards.invent("Wayfinder Grove", null, "Land", "{T}: Add {G} or {R}.")

    /** Alice begins casting [spell] from her hand: its open payment. */
    private fun payment(spell: String): Pair<CastFlow, Decision.ManaPayment> {
        val (card) = table.put(alice, Zone.Hand, spell)
        val state = table.state()
        val flow = CastFlow.start(table.source(state), alice, Input.Cast(arcanum.engine.state.PlayerId(alice), table.obj(card)), CastFlow.Seeds(table::seed))
        return flow to assertIs<Decision.ManaPayment>(flow.pending)
    }

    private fun waysOf(payment: Decision.ManaPayment, source: String) =
        payment.options.filterIsInstance<ManaUse.Ability>().filter { it.source == table.obj(source) }

    private fun CastFlow.care(payment: Decision.ManaPayment) = ManaWays.care(state, payment.source)

    // CR 601.2h: {W}{U} with the Ford and a Plains: the Ford's first listed way, {W}, leaves {U} unpaid; the tap takes {U}
    @Test fun aTapTakesTheWayThatKeepsTheRestPayable() {
        val (fordId) = table.put(alice, Zone.Battlefield, ford)
        table.put(alice, Zone.Battlefield, "Plains")
        val (flow, payment) = payment(Cards.invent("Wayfinder Bargain", "{W}{U}", "Sorcery", "Draw a card."))
        val ways = waysOf(payment, fordId)
        assertEquals(listOf(Color.White, Color.Blue), ways.map { it.color })
        val chosen = ManaWays.sensible(ways, flow.care(payment), emptySet()) { flow.fill(emptyList(), emptyList(), listOf(it)).isNotEmpty() }
        assertEquals(Color.Blue, (chosen as ManaUse.Ability).color)
    }

    // CR 702.44a (sunburst): with a Plains already paying {W}, the Ford pays a colour not spent yet ({U}), though {W} pays too
    @Test fun sunburstTakesAColourNotSpentYet() {
        val (fordId) = table.put(alice, Zone.Battlefield, ford)
        val (plains) = table.put(alice, Zone.Battlefield, "Plains")
        val (flow, payment) = payment(Cards.invent("Wayfinder Prism", "{2}", "Artifact Creature — Construct", "Sunburst", pt = "0/0"))
        assertEquals(ManaWays.ColorCare.Distinct, flow.care(payment))
        val kept = payment.options.filterIsInstance<ManaUse.Ability>().filter { it.source == table.obj(plains) }
        val spent = kept.flatMap(ManaWays::colorsOf).toSet()
        val chosen = ManaWays.sensible(waysOf(payment, fordId), flow.care(payment), spent) {
            flow.fill(kept, emptyList(), listOf(it)).isNotEmpty()
        }
        assertEquals(Color.Blue, (chosen as ManaUse.Ability).color)
    }

    // CR 603.4: "if {R}{R} was spent": the Grove pays {R} though {G} is listed first; without that, its first way
    @Test fun aSpellReadingRedSpentTakesRed() {
        val (groveId) = table.put(alice, Zone.Battlefield, grove)
        table.put(alice, Zone.Battlefield, "Mountain")
        val (flow, payment) = payment(Cards.invent("Wayfinder Ember", "{1}{R}", "Creature — Elemental",
            "When this creature enters, if {R}{R} was spent to cast it, this creature deals 3 damage to any target.", pt = "2/2"))
        assertEquals(ManaWays.ColorCare.Of(setOf(Color.Red)), flow.care(payment))
        val ways = waysOf(payment, groveId)
        assertEquals(Color.Green, ways.first().color)
        val pays: (ManaUse) -> Boolean = { flow.fill(emptyList(), emptyList(), listOf(it)).isNotEmpty() }
        assertEquals(Color.Red, (ManaWays.sensible(ways, flow.care(payment), emptySet(), pays) as ManaUse.Ability).color)
        assertEquals(Color.Green, (ManaWays.sensible(ways, ManaWays.ColorCare.None, emptySet(), pays) as ManaUse.Ability).color)
    }

    // When no way leaves the rest payable, the first preferred one: the payment then says what is missing
    @Test fun withNoWayPayingTheFirstIsTaken() {
        val (fordId) = table.put(alice, Zone.Battlefield, ford)
        val (flow, payment) = payment(Cards.invent("Wayfinder Toll", "{W}{U}{B}", "Sorcery", "Draw a card."))
        val chosen = ManaWays.sensible(waysOf(payment, fordId), flow.care(payment), emptySet()) {
            flow.fill(emptyList(), emptyList(), listOf(it)).isNotEmpty()
        }
        assertEquals(Color.White, (chosen as ManaUse.Ability).color)
    }

    @Test fun waysAreNamedByTheirMana() {
        val (fordId) = table.put(alice, Zone.Battlefield, ford)
        val (_, payment) = payment(Cards.invent("Wayfinder Errand", "{1}", "Sorcery", "Draw a card."))
        assertEquals(listOf("{W}", "{U}"), waysOf(payment, fordId).map(ManaWays::label))
        assertEquals("Convoke", ManaWays.label(ManaUse.Special(arcanum.api.ManaPaymentWay.TapCreatures, emptyList())))
    }
}
