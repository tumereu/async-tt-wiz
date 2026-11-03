package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.Mechanic
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import org.asyncttwiz.protocol.TypedCounter
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Night review 2026-11-04: the lane 2–4 mechanics through ATW (every call checked by [RoundTrip]: the engine state rebuilt from
 * the board equals the engine's own, undo restores the board) and the combat choices (CMB-036–CMB-039).
 */
class NightReview4AtwTest {
    init { CombatChoiceCards.ensured; ensured }
    private val table = AtwTable("night-review-4")
    private val alice get() = table.alice
    private val bob get() = table.bob

    private companion object {
        const val BRUTE = "Night Mutate Brute"
        const val WOLF = "Night Mutate Wolf"
        val ensured: Boolean by lazy {
            Cards.invent(BRUTE, "{3}{G}{G}", "Creature — Beast", "Mutate {2}{G}\nTrample", "6/6")
            Cards.invent(WOLF, "{1}{G}", "Creature — Wolf", "Vigilance", "2/2")
            true
        }
    }

    private fun game(): RoundTrip {
        table.turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
        table.priority = MtgPriority(alice)
        table.put(alice, PlayZone.LIBRARY, "Island", "Island", "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island", "Island")
        table.fullControl += listOf(alice, bob)
        return RoundTrip(table.state(), table.definitions)
    }

    private fun position(rt: RoundTrip) = TurnPosition.of(rt.state.board.turn)
    private fun card(rt: RoundTrip, id: String) = rt.state.board.cards.single { it.id == id }
    private fun zone(rt: RoundTrip, id: String) = rt.state.board.containers.single { it.id == card(rt, id).containerId }.zone

    private fun toAttackers(rt: RoundTrip): RoundTrip {
        rt.run(alice) { StepFlow.pass(it, alice) }
        while (position(rt) != TurnPosition.DECLARE_ATTACKERS) rt.run(bob) { StepFlow.pass(it, bob) }
        return rt
    }

    // CMB-037, CR 508.1f, 508.1i: the chosen attackers are tapped before the costs to attack are paid, so the staged Elvish Seer
    // can't tap for mana towards the Prison's {2}; with one other mana source the declaration can't be paid: the step arrow is
    // greyed with a reason, and the declaration with Autotap is refused
    @Ignore("nightreview #1: the attacking Seer taps for mana for its own tax")
    @Test fun anAttackingManaCreatureDoesntPayItsOwnTax() {
        val (seer) = table.put(alice, PlayZone.BATTLEFIELD, "Elvish Seer")
        table.put(alice, PlayZone.BATTLEFIELD, "Frosthorn Elves")
        table.put(bob, PlayZone.BATTLEFIELD, CombatChoiceCards.PRISON)
        val rt = toAttackers(game())
        val shown = CombatFlow.shown(rt.call())
        assertTrue(CombatFlow.attackRefusal(rt.call(), listOf(LaneAttack(seer))) != null,
            "The Seer, tapped as it attacks, can't help pay {2}: ${shown.costs}")
        val refused = runCatching { rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(seer)), autotap = true) } }
        assertTrue(refused.isFailure, "the declaration is refused")
    }

    // CMB-036–CMB-038 ("The other player sees the chosen costs' chips and the costs, without changing them"), I-D1: shared state is
    // server-authoritative; the defending player's command setting the attacking player's choices (exert, enlist, band) is refused
    @Ignore("nightreview #14: the rules accept any player's early answers, also another player's attack choices")
    @Test fun onlyTheAttackingPlayerSetsTheAttackChoices() {
        val (raider) = table.put(alice, PlayZone.BATTLEFIELD, CombatChoiceCards.RAIDER)
        val rt = toAttackers(game())
        val exert = CombatFlow.choices(rt.call()).optional.getValue(raider).single()
        val action = PreAnswers.set(rt.state.board, PreAnswer.AttackChoices(alice, 3, listOf(AttackChoice(raider, paying = listOf(exert.key)))))
        val command = org.asyncttwiz.protocol.GameCommand(org.asyncttwiz.protocol.CommandIds.uuidV5(AtwTable.NAMESPACE, "nr4/bob-sets"),
            org.asyncttwiz.protocol.GameScope(rt.state.gameId, playId = rt.state.id), org.asyncttwiz.protocol.GameAction.Play(action),
            org.asyncttwiz.protocol.CommandIds.uuidV5(AtwTable.NAMESPACE, "manual"), "1")
        val change = org.asyncttwiz.protocol.GameplayRules.change(rt.state, bob, command)
        assertTrue(change.outcome != "accepted", "bob set alice's attack choices: ${change.outcome}")
    }

    // CR 702.140c, 730.2: a creature mutated on top of another through ATW: the merged permanent keeps both cards (the board must
    // carry the merge, so the rebuilt engine state equals the engine's: RoundTrip checks every call)
    @Ignore("nightreview #12: a merge leaves the mutating card on the ATW Stack: the board is no longer representable")
    @Test fun aMutatedCreatureSurvivesTheRoundTrip() {
        table.put(alice, PlayZone.BATTLEFIELD, "Forest", "Forest", "Forest", WOLF)
        table.put(alice, PlayZone.HAND, BRUTE)
        val rt = game()
        val s = rt.engine()
        val brute = rt.obj(BRUTE, Zone.Hand)
        val way = CastWay(alternative = Engine.view(s)[brute]!!.characteristics!!.abilities.first { inst ->
            (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
        }.origin)
        rt.perform(Input.Cast(PlayerId(alice), brute, way), alice)
        val wolf = rt.obj(WOLF, Zone.Battlefield)
        rt.settle { it is Decision.Targets }
        if (rt.decision is Decision.Targets) rt.target(TargetRef.Object(wolf))
        rt.settle()
        rt.pass(alice)
        rt.pass(bob)
        val place = assertNotNull(rt.decision as? Decision.ChooseOption, "on top or under: ${rt.decision}")
        rt.perform(Input.Answer(place.id, Answer.Option("on top")), alice)
        val merged = rt.engine().objOrThrow(wolf)
        assertEquals(1, merged.mergedAbove.size, "the merged permanent keeps the Brute on top")
        assertEquals(6, Engine.view(rt.engine())[wolf]!!.characteristics!!.power)
    }

    // CR 309.2, 309.2a, 701.49a, I-D51: a card that ventures into the dungeon parses fully, so ATW lets it be played; venturing needs a
    // dungeon card its player owns outside the game, which an ATW game never has: the venture does nothing
    @Ignore("nightreview #13: an ATW game has no dungeon cards outside the game, so venturing does nothing")
    @Test fun venturingIntoTheDungeonThroughAtw() {
        Cards.invent("Night Torch Map", "{W}", "Sorcery", "Venture into the dungeon.")
        table.put(alice, PlayZone.BATTLEFIELD, "Plains")
        table.put(alice, PlayZone.HAND, "Night Torch Map")
        val rt = game()
        rt.cast(alice, "Night Torch Map")
        rt.settle()
        rt.pass(alice)
        rt.pass(bob)
        rt.settle()
        val s = rt.engine()
        assertTrue(s.command.any { id -> s.obj(id)?.definition?.faces?.firstOrNull()?.types?.contains(arcanum.api.CardType.Dungeon) == true },
            "alice is in a dungeon after venturing: command zone ${s.command}")
    }

    // CMB-039, CR 310.12b, 712: alice defeats her own Siege; its trigger exiles it and she casts it transformed: the back face
    // enters the battlefield on the ATW board
    @Test fun aDefeatedSiegeIsCastTransformedThroughAtw() {
        val (siege, ogre) = table.put(alice, PlayZone.BATTLEFIELD, CombatChoiceCards.SIEGE, "Hill Ogre")
        val rt = game()
        rt.state = rt.state.copy(board = rt.state.board.copy(cards = rt.state.board.cards.map {
            if (it.id == siege) it.copy(typedCounters = listOf(TypedCounter("defense", 3, 0)),
                ledger = CardLedger(protector = PlayerId(bob)).text()) else it }))
        toAttackers(rt)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(ogre, siege))) }
        var guard = 60
        while (guard-- > 0 && position(rt) != TurnPosition.POSTCOMBAT_MAIN) {
            val d = rt.decision
            when {
                d is Decision.May -> rt.perform(Input.Answer(d.id, Answer.YesNo(true)), d.player.value)
                d != null -> rt.settle { it is Decision.May }
                rt.engine().priority.holder == null -> rt.run(bob) { CombatFlow.declareBlockers(it, emptyList()) }
                else -> rt.engine().priority.holder!!.value.let { h -> rt.run(h) { StepFlow.pass(it, h) } }
            }
        }
        assertEquals(PlayZone.BATTLEFIELD, zone(rt, siege), "the Siege card is back on the battlefield, transformed")
        assertEquals("Emberfall Warden", rt.engine().let { s -> Engine.view(s)[rt.obj("Siege of Emberfall")]?.characteristics?.name })
    }
}
