package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Mechanic
import arcanum.api.Objects
import org.asyncttwiz.protocol.CombatAction
import org.asyncttwiz.protocol.CombatDestination
import org.asyncttwiz.protocol.CombatRole
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameScope
import org.asyncttwiz.protocol.GameplayRules
import org.asyncttwiz.protocol.IdentityGap
import org.asyncttwiz.protocol.MtgFacts
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the table shows of combat through the engine (I3 interim on today's lanes, I-D29, I-D32 deferred) and pre-events
 * (I-D33): the lanes as a draft of the declaration, the damage preview of blocks being arranged, a blocker blocking two
 * attackers, and the defender's division given early while the attacker still divides. Every call is checked by
 * [RoundTrip].
 */
class CombatShownTest {
    init { CombatCards.ensured }
    private val table = AtwTable("combat-shown")
    private val alice get() = table.alice
    private val bob get() = table.bob
    private var manualRequests = 0

    private fun game(): RoundTrip {
        table.turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
        table.priority = MtgPriority(alice)
        table.put(alice, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.HAND, "Storm Spark")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        // I-D41: these walk combat step by step, so Automatum stops in every step in which a player could act
        table.fullControl += listOf(alice, bob)
        return RoundTrip(table.state(), table.definitions)
    }

    private fun position(rt: RoundTrip) = TurnPosition.of(rt.state.board.turn)
    private fun ref(rt: RoundTrip, card: String) = MtgRef.Card(card, rt.state.board.cards.single { it.id == card }.zoneChanges)
    private fun zone(rt: RoundTrip, card: String) =
        rt.state.board.containers.single { c -> c.id == rt.state.board.cards.single { it.id == card }.containerId }.zone
    private fun passBob(rt: RoundTrip) = rt.run(bob) { StepFlow.pass(it, bob) }
    private fun shown(rt: RoundTrip) = CombatFlow.shown(rt.state, rt.definitions, emptyList())

    /** An ordinary (manual) event, as the server takes it. */
    private fun manual(rt: RoundTrip, actor: String, action: PlayAction) {
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "manual/${manualRequests++}"),
            GameScope(rt.state.gameId, playId = rt.state.id), GameAction.Play(action), CommandIds.uuidV5(AtwTable.NAMESPACE, "manual"), "1")
        val change = GameplayRules.change(rt.state, actor, command)
        assertEquals("accepted", change.outcome, change.reason)
        rt.state = change.state!!
        rt.tags += null
    }

    /** [card] can block an additional creature each combat (CR 509.1a), as an ability of its own. */
    private fun blocksTwo(card: String) {
        val digest = table.state().board.cards.single { it.id == card }.definition
        val definition = table.definitions.getValue(digest)
        val face = definition.faces.single()
        table.definitions[digest] = definition.copy(faces = listOf(face.copy(abilities = face.abilities +
            Ability.Static(listOf(Mechanic.CanBlockAdditional(1)), Affected.Objects(Objects.Self)))))
    }

    // CR 508.1, 509.1, I-D29: lanes arranged in the declare attackers step are a draft the engine doesn't take as declared;
    // in the declare blockers step the preview shows the damage of the blocks being arranged, without declaring them
    @Test fun lanesAreADraftAndThePreviewFollowsTheBlocksBeingArranged() {
        val (bear) = table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        while (position(rt) != TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        // Alice drafts her attack in the lanes by hand
        manual(rt, alice, PlayAction.Combat(CombatAction.Open))
        val area = rt.state.board.combat!!.id
        manual(rt, alice, PlayAction.Combat(CombatAction.Place(bear, CombatDestination(area, CombatRole.ATTACKER, pairGap = IdentityGap()))))
        val drafted = shown(rt)
        assertEquals(alice, drafted.choices.declaring, "the draft isn't a declaration: attackers are still awaited")
        assertNull(drafted.preview)
        rt.run(alice) { CombatFlow.declareAttackers(it, CombatFlow.lanes(rt.state.board.combat).first) }
        while (position(rt) == TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        assertEquals(TurnPosition.DECLARE_BLOCKERS, position(rt))
        assertEquals(bob, shown(rt).choices.declaring)
        assertEquals(mapOf(bob to 2), shown(rt).preview!!.damage, "unblocked: 2 to Bob")
        // Bob drafts a block: the preview follows it, still undeclared
        val pair = rt.state.board.combat!!.pairs.single().id
        manual(rt, bob, PlayAction.Combat(CombatAction.Place(ogre, CombatDestination(area, CombatRole.BLOCKER, pairId = pair))))
        val blocking = shown(rt)
        assertEquals(bob, blocking.choices.declaring)
        assertEquals(mapOf(ogre to 2, bear to 3), blocking.preview!!.damage)
        assertEquals(listOf(bear), blocking.preview!!.dies)
        rt.run(bob) { CombatFlow.declareBlockers(it, CombatFlow.lanes(rt.state.board.combat).second) }
        assertEquals(listOf(LaneAttack(bear)) to listOf(LaneBlock(ogre, bear)), CombatFlow.lanes(rt.state.board.combat))
    }

    // CMB-031, CR 509.1a, 510.1c–d, I-D29, I-D33: a blocker blocking two attackers is in the lanes with both blocks; while
    // the attacker divides the trampler's damage (two blockers, not enough for lethal to both), the defender divides the
    // blocker's early; it is applied when its decision opens, and the batch that applies it removes it
    @Test fun theDefendersDivisionGivenEarlyIsAppliedWhenItOpens() {
        val (brute, bear) = table.put(alice, PlayZone.BATTLEFIELD, "Ironhide Brute", "Stout Bear")
        val (ogre, second) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre", "Hill Ogre")
        blocksTwo(ogre)
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        while (position(rt) != TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(brute), LaneAttack(bear))) }
        while (position(rt) == TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, brute), LaneBlock(ogre, bear), LaneBlock(second, brute))) }
        assertEquals(setOf(LaneBlock(ogre, brute), LaneBlock(ogre, bear), LaneBlock(second, brute)),
            CombatFlow.lanes(rt.state.board.combat).second.toSet(), "the lanes hold both of the Ogre's blocks (CMB-031)")
        while (CombatFlow.division(rt.call()) == null) passBob(rt)
        val open = assertNotNull(CombatFlow.division(rt.call()))
        assertEquals(alice to brute, open.player to open.source)
        val early = shown(rt).early.single()
        assertEquals(bob to ogre, early.player to early.source)
        assertEquals(setOf<MtgRef>(ref(rt, brute), ref(rt, bear)), early.among.toSet())
        // Bob gives his division early: all 3 to the Stout Bear
        val answer = PreAnswer.Division(bob, ref(rt, ogre), listOf(PreShare(ref(rt, bear), 3), PreShare(ref(rt, brute), 0)))
        val digest = MtgFacts.digest(rt.state.board)
        manual(rt, bob, PreAnswers.set(rt.state.board, answer))
        assertEquals(digest, MtgFacts.digest(rt.state.board), "an early answer isn't part of the board a batch is planned on")
        assertEquals(listOf<PreAnswer>(answer), PreAnswers.of(rt.state.board))
        // Alice takes her suggestion: Bob's decision opens and is answered at once in the same batch
        rt.run(alice) { CombatFlow.divide(it) }
        assertNull(CombatFlow.division(rt.call()), "nothing is left to divide")
        assertEquals(PlayZone.GRAVEYARD, zone(rt, bear))
        assertEquals("3", rt.state.board.cards.single { it.id == brute }.damage, "the Brute got only the second Ogre's damage")
        assertTrue(rt.state.board.engineRecords.none { it.kind == EngineRecordKind.PreAnswer }, "used up")
    }

    // I-D33: an early answer that no longer fits when its decision opens is skipped: the decision is asked as usual
    @Test fun anEarlyAnswerTheEngineRefusesLeavesTheDecisionOpen() {
        val (brute, bear) = table.put(alice, PlayZone.BATTLEFIELD, "Ironhide Brute", "Stout Bear")
        val (ogre, second) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre", "Hill Ogre")
        blocksTwo(ogre)
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        while (position(rt) != TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(brute), LaneAttack(bear))) }
        while (position(rt) == TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, brute), LaneBlock(ogre, bear), LaneBlock(second, brute))) }
        while (CombatFlow.division(rt.call()) == null) passBob(rt)
        // 5 is more than the Ogre's 3
        manual(rt, bob, PreAnswers.set(rt.state.board, PreAnswer.Division(bob, ref(rt, ogre), listOf(PreShare(ref(rt, bear), 5)))))
        rt.run(alice) { CombatFlow.divide(it) }
        val asked = assertNotNull(CombatFlow.division(rt.call()))
        assertEquals(bob to ogre, asked.player to asked.source)
        assertTrue(PreAnswers.of(rt.state.board).isEmpty(), "the batch removed it")
    }
}
