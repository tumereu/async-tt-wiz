package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Final adversarial review of combat (I-D29–I-D32, I-D38, I-D39) and pause points (I-D41) together with the steps
 * (report `arcanum-final-combat-review.md`, outside the repo), at Automatum's level: extra combat phases (Gatecrash,
 * Dragon's Maze), costs to attack (Return to Ravnica), first and double strike across the steps, and where the turn stops
 * for each player. Each ignored test pins a problem found and fails while it isn't fixed. Invented cards only.
 */
class FinalCombatReviewTest {
    init {
        CombatCards.ensured
        Cards.invent("Valoria, Dawnwing Marshal", "{2}{R}{R}{W}{W}", "Legendary Creature — Angel", """
            Flying, vigilance, haste
            Whenever Valoria attacks for the first time each turn, untap all creatures you control. After this phase, there is an additional combat phase.
        """, "3/4")
        Cards.invent("Edict of Tolls", "{4}{W}", "Enchantment",
            "Creatures can't attack you or planeswalkers you control unless their controller pays {X} for each of those creatures, where X is the number of enchantments you control.")
        Cards.invent("Ember Lance", "{R}", "Instant", "Ember Lance deals 1 damage to any target.")
    }
    private val table = AtwTable("final-combat-review")
    private val alice get() = table.alice
    private val bob get() = table.bob

    /** Alice's main phase of turn 3; Bob keeps an instant and a Mountain, so the host stops for him at his pauses. */
    private fun game(fullControl: Boolean = true): RoundTrip {
        table.turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
        table.priority = MtgPriority(alice)
        table.put(alice, PlayZone.LIBRARY, "Island", "Island", "Island")
        table.put(bob, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.HAND, "Ember Lance")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island", "Island")
        if (fullControl) table.fullControl += listOf(alice, bob)
        return RoundTrip(table.state(), table.definitions)
    }

    private fun position(rt: RoundTrip) = TurnPosition.of(rt.state.board.turn)
    private fun holder(rt: RoundTrip): String? = rt.call().working.let { HostPriority.wanted(it, HostPriority.kept(rt.state.board))?.holder ?: it.priority.holder }?.value
    /** Whoever holds priority as the host takes it passes. */
    private fun pass(rt: RoundTrip) = holder(rt)!!.let { h -> rt.run(h) { StepFlow.pass(it, h) } }
    /** Passes until [until] holds, at most [max] times. */
    private fun passUntil(rt: RoundTrip, max: Int = 20, until: () -> Boolean) {
        var guard = max
        while (!until() && guard-- > 0) {
            if (rt.engine().pending != null) rt.answer()
            else if (holder(rt) == null) return
            else pass(rt)
        }
    }

    // CR 500.8, 506.1 (gtc): "After this phase, there is an additional combat phase." Valoria attacks; her trigger untaps
    // Alice's creatures and adds a combat phase. Bob holds priority at his pause with the trigger on the Stack, so the
    // trigger resolves in a later call than the one that put it there: the added phase must survive ATW's round trip
    // (the engine's TurnState.extraPhases) and the turn must go to a second beginning of combat after the first combat.
    // finalreview-combat #1: TurnExtras doesn't keep extraPhases/resumeAt, so EngineState.from drops the added phase
    // (the round trip fails on "turn"; without the check the second combat never comes)
    @Ignore("finalreview-combat #1: an additional combat phase is lost between engine calls (TurnExtras has no extraPhases)")
    @Test fun anAdditionalCombatPhaseFromAnAttackTriggerIsTaken() {
        val (valoria) = table.put(alice, PlayZone.BATTLEFIELD, "Valoria, Dawnwing Marshal")
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        passUntil(rt) { position(rt) == TurnPosition.DECLARE_ATTACKERS && holder(rt) == null }
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(valoria))) }
        assertTrue(rt.engine().stack.isNotEmpty(), "Valoria's trigger is on the Stack")
        // Bob (pause on Alice's trigger) lets it resolve: a call of its own
        passUntil(rt) { rt.engine().stack.isEmpty() }
        assertEquals(listOf(arcanum.api.TurnStep.BeginningOfCombat), rt.engine().turn.extraPhases, "the added phase is kept")
        // Through the first combat to the added one
        var steps = mutableListOf<TurnPosition?>()
        passUntil(rt, max = 40) {
            steps += position(rt)
            position(rt) == TurnPosition.DECLARE_ATTACKERS && holder(rt) == null && steps.count { it == TurnPosition.COMBAT_DAMAGE } > 0
        }
        assertEquals(TurnPosition.DECLARE_ATTACKERS, position(rt), "a second declare attackers step: $steps")
        assertTrue(CombatFlow.choices(rt.call()).attacks.containsKey(valoria), "Valoria (vigilance) may attack again")
    }

    // finalreview-combat #1 (second half) — CR 500.8, TRN-014: once the added phase is kept, the step change into it goes
    // back in ATW's order (end of combat → beginning of combat). When nobody can act in that added beginning of combat
    // (no Full control), EngineBatch.step says it as a passed step (`ChooseStep(through = laterStep())`), which ATW's
    // reducer refuses for a step earlier than the board's ("A passed step only moves forward inside this turn"), so the
    // whole batch would be refused. Shown on the reducer: the batch's first two members as the planner makes them
    @Ignore("finalreview-combat #1: the step back to an added beginning of combat is refused as a passed step")
    @Test fun anAddedCombatPassedThroughIsTakenByTheReducer() {
        table.turn = TurnState(alice, "3", TurnPosition.END_COMBAT.phase, TurnPosition.END_COMBAT.step)
        table.priority = null
        val state = table.state()
        val command = org.asyncttwiz.protocol.GameCommand(org.asyncttwiz.protocol.CommandIds.uuidV5(AtwTable.NAMESPACE, "extra-combat"),
            org.asyncttwiz.protocol.GameScope(state.gameId, playId = state.id),
            org.asyncttwiz.protocol.GameAction.PlayBatch(listOf(
                org.asyncttwiz.protocol.PlayAction.ChooseStep(TurnPosition.BEGIN_COMBAT, through = true),
                org.asyncttwiz.protocol.PlayAction.ChooseStep(TurnPosition.DECLARE_ATTACKERS))),
            org.asyncttwiz.protocol.CommandIds.uuidV5(AtwTable.NAMESPACE, "stream"), "1")
        val first = org.asyncttwiz.protocol.GameplayRules.change(state, alice,
            org.asyncttwiz.protocol.PlayBatches.member(command, 0, (command.action as org.asyncttwiz.protocol.GameAction.PlayBatch).actions[0]))
        assertEquals("accepted", first.outcome, "the passed step into the added beginning of combat: ${first.reason}")
    }

    // CR 508.1g–j (rtr): a cost to attack is paid as attackers are declared; the attack waits for the payment, a shared
    // decision of the attacking player. Declared through the lanes, paid with the suggestion, the Bear attacks
    @Test fun aCostToAttackIsPaidAndTheAttackHappens() {
        val (bear) = table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear")
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.BATTLEFIELD, "Edict of Tolls")
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        passUntil(rt) { position(rt) == TurnPosition.DECLARE_ATTACKERS && holder(rt) == null }
        assertTrue(CombatFlow.choices(rt.call()).attacks.containsKey(bear))
        assertEquals(null, CombatFlow.attackRefusal(rt.call(), listOf(LaneAttack(bear))), "payable: the draft is taken")
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(bear))) }
        val payment = assertNotNull(rt.engine().pending?.decision as? Decision.ManaPayment, "the attack waits for the payment")
        assertEquals(alice, payment.player.value)
        // The lanes still show the draft while the payment is open; nothing is tapped yet but the payment
        rt.settle()
        assertTrue(rt.state.board.cards.single { it.id == bear }.tapped, "the Bear attacks (tapped)")
        assertTrue(rt.engine().combat?.attackers?.any { (it.id as? arcanum.engine.state.ObjectId.Card)?.card?.value != null } == true)
        assertEquals(TurnPosition.DECLARE_ATTACKERS, position(rt))
        passUntil(rt, max = 20) { position(rt) == TurnPosition.POSTCOMBAT_MAIN }
        assertEquals("18", rt.state.board.players.single { it.accountId == bob }.life)
    }

    // CR 508.1d, 508.1h: a cost to attack the player can't pay makes the attack illegal; the draft's refusal says so
    @Test fun aCostToAttackThatCantBePaidRefusesTheDraft() {
        val (bear) = table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear")
        table.put(bob, PlayZone.BATTLEFIELD, "Edict of Tolls")
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        passUntil(rt) { position(rt) == TurnPosition.DECLARE_ATTACKERS && holder(rt) == null }
        val refusal = CombatFlow.attackRefusal(rt.call(), listOf(LaneAttack(bear)))
        assertNotNull(refusal, "Alice has no mana to pay")
        assertTrue("cost" in refusal, refusal)
    }

    // CR 510.4, 702.4b–c, 702.4d: a double striker blocked by a first striker: both deal damage in the first-strike step,
    // only the double striker again in the regular one. With the pauses (both players' Full control) each step's priority
    // is its own call: the round trip holds through both damage steps and the preview of the first-strike step counts both
    @Test fun doubleStrikeAgainstFirstStrikeAcrossTheDamageSteps() {
        val (duelist) = table.put(alice, PlayZone.BATTLEFIELD, "Twinblade Duelist")
        val (squire) = table.put(bob, PlayZone.BATTLEFIELD, "Dawnlance Squire")
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        passUntil(rt) { position(rt) == TurnPosition.DECLARE_ATTACKERS && holder(rt) == null }
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(duelist))) }
        passUntil(rt) { position(rt) == TurnPosition.DECLARE_BLOCKERS && holder(rt) == null }
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(squire, duelist))) }
        val preview = assertNotNull(CombatFlow.preview(rt.call()))
        // Squire 2/1 deals 2 to the Duelist (2/2) in the first-strike step: both die there
        assertEquals(setOf(duelist, squire), preview.dies.toSet(), "${preview.dealt}")
        passUntil(rt, max = 20) { position(rt) == TurnPosition.POSTCOMBAT_MAIN }
        assertEquals(PlayZone.GRAVEYARD, rt.state.board.containers.single { c -> c.id == rt.state.board.cards.single { it.id == duelist }.containerId }.zone)
        assertEquals("20", rt.state.board.players.single { it.accountId == bob }.life)
    }

    // I-D41, STK-036: without Full control, a whole turn of Alice's with an attack stops for Bob only at his pauses: the
    // attack (declare attackers), the blocks (declare blockers) and Alice's end step; Alice herself only at her main phases
    // and declarations. Records each acting point
    @Test fun aTurnWithCombatStopsAtTheExpectedPoints() {
        val (bear) = table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        // Alice too could respond (so the host doesn't pass for her where she has nothing to do, I-D18)
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.HAND, "Ember Lance")
        val rt = game(fullControl = false)
        val points = mutableListOf<String>()
        fun note() { points += "${position(rt)}:${holder(rt)?.let { if (it == alice) "A" else "B" } ?: rt.engine().pending?.decision?.let { it::class.simpleName } ?: "-"}" }
        rt.run(alice) { StepFlow.pass(it, alice) }
        note()
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(bear))) }
        note()
        while (position(rt) == TurnPosition.DECLARE_ATTACKERS) { pass(rt); note() }
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, bear))) }
        note()
        var guard = 12
        while (rt.state.board.turn.accountId == alice && guard-- > 0) {
            if (rt.engine().pending != null) rt.answer() else if (holder(rt) == null) break else pass(rt)
            note()
        }
        // Alice declares attackers; Bob's pause on the attack; Bob declares blocks; both get the blocks' pause; then
        // straight to Alice's second main phase, Bob's pause in her end step, and Bob's own turn
        assertEquals(listOf("DECLARE_ATTACKERS:-", "DECLARE_ATTACKERS:B", "DECLARE_BLOCKERS:-", "DECLARE_BLOCKERS:A",
            "DECLARE_BLOCKERS:B", "POSTCOMBAT_MAIN:A", "END_STEP:B", "PRECOMBAT_MAIN:B"), points.take(8), "$points")
    }
}
