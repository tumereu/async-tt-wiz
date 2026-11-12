package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Which pick is which (owner, 2026-11-11): the text of what is cast or activated with the phrase chosen now, and each one
 * chosen as what was chosen ([PickText]). */
class PickTextTest {
    private val table = EngineTable()
    private val alice get() = table.alice
    private fun start(state: arcanum.engine.state.GameState, input: Input) =
        CastFlow.start(table.source(state), alice, input, CastFlow.Seeds(table::seed))

    private fun CastFlow.picked(costs: List<Pair<arcanum.engine.decision.CostPartAt, List<arcanum.engine.state.ObjectId>>> = emptyList(),
        rulesText: String? = null): PickText =
        assertNotNull(PickText.of(state, assertNotNull(PickText.announcing(call.pending?.continuation)), pending, costs, rulesText = rulesText))

    /** The parts as text: the phrase chosen now in [brackets], a chosen phrase as <names>, bracketed names as (names). */
    private fun PickText.shown(name: (TargetRef) -> String): String = parts.joinToString("") { part ->
        when (part) {
            is PickText.Part.Plain -> part.text
            is PickText.Part.Now -> "[${part.text}]"
            is PickText.Part.Chosen -> "<" + (part.chosen.joinToString(" and ") { name(it) }.ifEmpty { part.none }) + ">"
            is PickText.Part.After -> " (" + part.chosen.joinToString(" and ") { name(it) } + ")"
        }
    }

    // STK-047, CR 601.2c: a spell's targets in the order its text names them: the first while it is chosen, then in its place
    // what was chosen while the second is chosen
    @Test fun targetsChosenNowAndBefore() {
        val brawl = Cards.invent("Pick Brawl", "{0}", "Sorcery", "Target creature you control fights target creature you don't control.")
        val (seer) = table.put(alice, Zone.Battlefield, "Elvish Seer")
        val (elves) = table.put(table.bob, Zone.Battlefield, "Thicketwood Elves")
        val (card) = table.put(alice, Zone.Hand, brawl)
        val state = table.state()
        var flow = start(state, assertNotNull(table.plays(state).of(card).first().input))
        val names = mapOf<TargetRef, String>(TargetRef.Object(table.obj(seer)) to "Elvish Seer", TargetRef.Object(table.obj(elves)) to "Thicketwood Elves")
        assertEquals("[Target creature you control] fights target creature you don't control.", flow.picked().shown { names.getValue(it) })
        flow = flow.answer(Answer.Targets(listOf(TargetRef.Object(table.obj(seer)))))
        val second = flow.picked()
        assertEquals("<Elvish Seer> fights [target creature you don't control].", second.shown { names.getValue(it) })
        assertEquals(listOf<Pair<TargetRef, String>>(TargetRef.Object(table.obj(seer)) to "Target"), second.roles)
    }

    // STK-047, CR 702.6a, 601.2c, 601.2h: Equip—Sacrifice a creature: the equip target has no phrase of its own, so the keyword's
    // name is highlighted while it is chosen and the name follows it in brackets; then the sacrifice's phrase is chosen
    @Test fun anEquipTargetThenItsSacrifice() {
        val hauberkText = "Equipped creature gets +4/+2.\nEquip—Sacrifice a creature."
        val hauberk = Cards.invent("Pick Hauberk", "{0}", "Artifact — Equipment", hauberkText)
        val (equipment) = table.put(alice, Zone.Battlefield, hauberk)
        val (seer, elves) = table.put(alice, Zone.Battlefield, "Elvish Seer", "Thicketwood Elves")
        val state = table.state()
        val equip = table.plays(state).of(equipment).first { it.text.startsWith("Equip") }
        var flow = start(state, assertNotNull(equip.input))
        val names = mapOf<TargetRef, String>(TargetRef.Object(table.obj(seer)) to "Elvish Seer", TargetRef.Object(table.obj(elves)) to "Thicketwood Elves")
        assertIs<Decision.Targets>(flow.pending)
        assertEquals("[Equip]—Sacrifice a creature.", flow.picked(rulesText = hauberkText).shown { names.getValue(it) })
        flow = flow.answer(Answer.Targets(listOf(TargetRef.Object(table.obj(seer)))))
        val sacrifice = assertIs<Decision.ChooseObjects>(flow.pending)
        assertEquals(arcanum.engine.decision.CostPartAt(null, 0), sacrifice.cost)
        val text = flow.picked(rulesText = hauberkText)
        assertEquals("Equip (Elvish Seer)—Sacrifice [a creature].", text.shown { names.getValue(it) })
        assertEquals(listOf<Pair<TargetRef, String>>(TargetRef.Object(table.obj(seer)) to "Equip"), text.roles)
        val withPicked = PickText.of(flow.state, assertNotNull(PickText.announcing(flow.call.pending?.continuation)), sacrifice, emptyList(),
            picked = listOf(TargetRef.Object(table.obj(elves))), rulesText = hauberkText)
        assertEquals("Sacrificed", withPicked?.roles?.toMap()?.get(TargetRef.Object(table.obj(elves))))
    }

    // A phrase nothing was chosen for reads as none of it
    @Test fun noneChosen() {
        assertEquals("no creature", PickText.none("up to one target creature"))
        assertEquals("no creatures", PickText.none("any number of target creatures"))
        assertEquals("no creature you control", PickText.none("up to one other target creature you control"))
        assertEquals(PlayerId(alice).value, alice)
    }
}
