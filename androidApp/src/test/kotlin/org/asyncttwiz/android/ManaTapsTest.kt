package org.asyncttwiz.android

import arcanum.api.Color
import arcanum.api.ManaCost
import arcanum.engine.decision.Decision
import arcanum.engine.decision.DecisionId
import arcanum.engine.decision.ManaUse
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.view.AbilityOrigin
import org.asyncttwiz.automatum.ManaWays
import org.junit.Test
import kotlin.test.assertEquals

/**
 * STK-024 (owner playtest 2026-11-09): a tap on a source adds it with its sensible way (the first, in the order serving what the
 * spell cares about in colours, after which the rest can still be paid), or takes it out; a way chosen with its corner chip is
 * kept exactly. Here the engine is a stand-in: a cost of {W}{U} paid by a Ford ({W} or {U}) and a Plains.
 */
class ManaTapsTest {
    private fun card(n: Int) = ObjectId.Card(CardId("c$n"), 0)
    private val ford = card(1)
    private val plains = card(2)
    private fun ford(color: Color) = ManaUse.Ability(ford, AbilityOrigin.Printed(0, 0), color)
    private val plainsW = ManaUse.Ability(plains, AbilityOrigin.Printed(0, 0), Color.White)
    private val decision = Decision.ManaPayment(DecisionId("pay"), PlayerId("alice"), null, ManaCost.parse("{W}{U}")!!,
        listOf(ford(Color.White), ford(Color.Blue), plainsW), emptyList())

    /** The stand-in engine: a payment pays when it has {W} and {U} from two different sources; a fill keeps the exact uses and adds
     * the Plains when it is needed and not taken out. */
    private val engine = ManaTaps.Engine(fill = { pinned, removed, exact ->
        val uses = exact + pinned.filter { p -> exact.none { (it as ManaUse.Ability).source == (p as ManaUse.Ability).source } }
        val colors = uses.flatMap(ManaWays::colorsOf)
        val withPlains = if (Color.White !in colors && plainsW !in removed && uses.none { (it as ManaUse.Ability).source == plains }) uses + plainsW else uses
        if (withPlains.flatMap(ManaWays::colorsOf).toSet() == setOf(Color.White, Color.Blue) && withPlains.size == 2) withPlains else emptyList()
    }, pays = { uses -> uses.size == 2 && uses.flatMap(ManaWays::colorsOf).toSet() == setOf(Color.White, Color.Blue) })

    private val fordWays = listOf(ford(Color.White), ford(Color.Blue))
    private val isFord: (ManaUse) -> Boolean = { (it as? ManaUse.Ability)?.source == ford }

    // CR 601.2h: {W}, listed first, would leave {U} unpaid beside the Plains: the tap takes {U}, and Autotap adds the Plains
    @Test fun aTapAddsTheWayThatKeepsTheRestPayable() {
        val outcome = ManaTaps.tap(ManaTaps(), decision, emptyList(), fordWays, isFord, autotap = true, ManaWays.ColorCare.None, engine)
        assertEquals(listOf(ford(Color.Blue)), outcome.taps.tapped)
        assertEquals(setOf(ford(Color.Blue), plainsW), outcome.mana.toSet())
    }

    @Test fun aTapOnASourcePayingTakesItOut() {
        val paying = listOf(ford(Color.Blue), plainsW)
        val outcome = ManaTaps.tap(ManaTaps(tapped = listOf(ford(Color.Blue))), decision, paying, fordWays, isFord, autotap = true,
            ManaWays.ColorCare.None, engine)
        assertEquals(emptyList(), outcome.taps.tapped)
        assertEquals(listOf(ford(Color.Blue)), outcome.taps.outs)
    }

    // A way chosen with the corner chip stays as chosen, even when the rest can't be paid with it then (Cast greyed)
    @Test fun aChosenWayIsKeptExactly() {
        val outcome = ManaTaps.choose(ManaTaps(tapped = listOf(ford(Color.Blue))), decision, ford(Color.White), isFord, autotap = true, engine)
        assertEquals(listOf(ford(Color.White)), outcome.taps.chosen)
        assertEquals(emptyList(), outcome.taps.tapped)
        assertEquals(listOf(ford(Color.White)), outcome.mana)
    }

    // With Autotap off only the picks pay
    @Test fun withAutotapOffOnlyThePicksPay() {
        val outcome = ManaTaps.tap(ManaTaps(), decision, emptyList(), fordWays, isFord, autotap = false, ManaWays.ColorCare.None, engine)
        assertEquals(listOf(ford(Color.Blue)), outcome.mana)
    }
}
