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
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayBatches
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adversarial review of Automatum phases I2b and I3 (report `arcanum-i3-review.md`, outside the repo): each ignored test
 * pins a problem found and fails while it isn't fixed. Invented cube cards only ([CombatCards], the engine's test cards).
 */
class I3ReviewTest {
    init { CombatCards.ensured }
    private val table = AtwTable("i3-review")
    private val alice get() = table.alice
    private val bob get() = table.bob
    private var manualRequests = 0

    /** Alice's main phase of turn 3; Bob keeps Storm Spark and a Mountain, so the host never passes for him (I-D18). */
    private fun game(): RoundTrip {
        table.turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
        table.priority = MtgPriority(alice)
        table.put(alice, PlayZone.LIBRARY, "Island", "Island", "Island")
        table.put(bob, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.HAND, "Storm Spark")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island", "Island")
        return RoundTrip(table.state(), table.definitions)
    }

    private fun position(rt: RoundTrip) = TurnPosition.of(rt.state.board.turn)
    private fun ref(rt: RoundTrip, card: String) = MtgRef.Card(card, rt.state.board.cards.single { it.id == card }.zoneChanges)
    private fun passBob(rt: RoundTrip) = rt.run(bob) { StepFlow.pass(it, bob) }
    private fun shown(rt: RoundTrip) = CombatFlow.shown(rt.state, rt.definitions, emptyList())

    /** An ordinary (manual) event, as the server takes it. */
    private fun manual(rt: RoundTrip, actor: String, action: PlayAction) {
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "i3-manual/${manualRequests++}"),
            GameScope(rt.state.gameId, playId = rt.state.id), GameAction.Play(action), CommandIds.uuidV5(AtwTable.NAMESPACE, "manual"), "1")
        val change = GameplayRules.change(rt.state, actor, command)
        assertEquals("accepted", change.outcome, "$action: ${change.reason}")
        rt.state = change.state!!
        rt.tags += null
    }

    /** [card] can block an additional creature each combat (CR 509.1a). */
    private fun blocksTwo(card: String) {
        val digest = table.state().board.cards.single { it.id == card }.definition
        val definition = table.definitions.getValue(digest)
        val face = definition.faces.single()
        table.definitions[digest] = definition.copy(faces = listOf(face.copy(abilities = face.abilities +
            Ability.Static(listOf(Mechanic.CanBlockAdditional(1)), Affected.Objects(Objects.Self)))))
    }

    private fun toAttackers(rt: RoundTrip) {
        rt.run(alice) { StepFlow.pass(it, alice) }
        while (position(rt) != TurnPosition.DECLARE_ATTACKERS) passBob(rt)
    }

    // #1 (I-D24, I-D28, SYS-067; CR 511.3): a combat Automatum declared, then played out by hand with Automatum off
    // (closed, the turn ended), is still the engine's combat once Automatum is switched on again in a later turn: the
    // engine's combat record outlives its combat, so the engine sees Alice's creature attacking in Bob's turn and the
    // table refuses manual combat damage there ("Automatum deals the combat damage itself", CMB-022)
    @Ignore("i3review #1: the engine's combat record outlives a combat finished by hand while Automatum was off")
    @Test fun aCombatFinishedByHandWhileOffIsNotTheEnginesCombatLater() {
        val (bear) = table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear")
        val rt = game()
        toAttackers(rt)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(bear))) }
        assertTrue(CombatFlow.throughEngine(rt.state.board))
        // Switched off (no open choice: nothing to revert), combat finished and the turn ended by hand
        manual(rt, alice, PlayAction.Mtg(MtgAction.SetAutomatum(false)))
        manual(rt, alice, PlayAction.Combat(CombatAction.Close(rt.state.board.combat!!.id)))
        manual(rt, alice, PlayAction.EndTurn)
        assertEquals(bob, rt.state.board.turn.accountId)
        manual(rt, bob, PlayAction.Mtg(MtgAction.SetAutomatum(true)))
        assertNull(rt.state.board.combat, "no combat on the table")
        assertNull(rt.engine().combat, "the engine sees no combat in Bob's turn")
        assertTrue(!CombatFlow.throughEngine(rt.state.board), "a later combat isn't taken as Automatum's")
    }

    // #2 (CMB-028 "a pair per attacker", CR 509.1a): two attackers drafted into one pair (CMB-027: dropped beside each
    // other) keep sharing it once declared, so the defender can only block both or neither: a blocker in that pair is
    // read as blocking every attacker of the pair, and the lanes can't be rearranged once attackers are declared
    @Ignore("i3review #2: attackers drafted into one pair keep one pair; the defender can't block just one of them")
    @Test fun declaredAttackersGetAPairEach() {
        val (bear, brute) = table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear", "Ironhide Brute")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        val rt = game()
        toAttackers(rt)
        manual(rt, alice, PlayAction.Combat(CombatAction.Open))
        val area = rt.state.board.combat!!.id
        manual(rt, alice, PlayAction.Combat(CombatAction.Place(bear, CombatDestination(area, CombatRole.ATTACKER, pairGap = IdentityGap()))))
        val pair = rt.state.board.combat!!.pairs.single().id
        manual(rt, alice, PlayAction.Combat(CombatAction.Place(brute, CombatDestination(area, CombatRole.ATTACKER, pairId = pair))))
        assertEquals(1, rt.state.board.combat!!.pairs.size, "drafted beside each other: one pair")
        rt.run(alice) { CombatFlow.declareAttackers(it, CombatFlow.lanes(rt.state.board.combat).first) }
        while (position(rt) == TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        assertEquals(TurnPosition.DECLARE_BLOCKERS, position(rt))
        // Bob blocks the Bear only, by putting his Ogre into its pair
        val bearPair = rt.state.board.combat!!.pairs.single { p -> p.attackers.any { it.sourceId == bear } }.id
        manual(rt, bob, PlayAction.Combat(CombatAction.Place(ogre, CombatDestination(area, CombatRole.BLOCKER, pairId = bearPair))))
        assertEquals(listOf(LaneBlock(ogre, bear)), CombatFlow.lanes(rt.state.board.combat).second,
            "the Ogre blocks the Bear alone (a pair per attacker)")
        assertNull(CombatFlow.blockRefusal(rt.call(), CombatFlow.lanes(rt.state.board.combat).second))
    }

    // #3 (STK-034, I-D33; CR 510.4): early divisions are offered only for divisions still to come "in the same damage
    // step". In the first-strike damage step the defender is offered his blocker's division of the regular step; the
    // next batch (Alice's division) removes it unused, so his Ready is silently lost and he is asked again later
    @Ignore("i3review #3: early divisions of the regular damage step are offered in the first-strike step and dropped")
    @Test fun earlyDivisionsAreOfferedOnlyForTheSameDamageStep() {
        val (squire, brute) = table.put(alice, PlayZone.BATTLEFIELD, "Dawnlance Squire", "Ironhide Brute")
        val (ogre, bear) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre", "Stout Bear")
        blocksTwo(ogre)
        val rt = game()
        toAttackers(rt)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(squire), LaneAttack(brute))) }
        while (position(rt) == TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, squire), LaneBlock(bear, squire), LaneBlock(ogre, brute))) }
        while (CombatFlow.division(rt.call()) == null) passBob(rt)
        assertEquals(TurnPosition.FIRST_STRIKE_DAMAGE, position(rt))
        val open = assertNotNull(CombatFlow.division(rt.call()))
        assertEquals(squire, open.source, "the first striker's division is open")
        val offered = shown(rt).early
        assertTrue(offered.none { it.source == ogre },
            "the Ogre divides in the regular damage step, not this one: offered ${offered.map { it.source to it.player }}")
    }

    /** Alice's two tramplers both blocked by Bob's Hill Ogre (it blocks two): Alice divides twice, then Bob divides. */
    private fun twoDivisionsBeforeBobs(): Triple<RoundTrip, String, Pair<String, String>> {
        val (brute1, brute2) = table.put(alice, PlayZone.BATTLEFIELD, "Ironhide Brute", "Ironhide Brute")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        blocksTwo(ogre)
        val rt = game()
        toAttackers(rt)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(brute1), LaneAttack(brute2))) }
        while (position(rt) == TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, brute1), LaneBlock(ogre, brute2))) }
        while (CombatFlow.division(rt.call()) == null) passBob(rt)
        return Triple(rt, ogre, brute1 to brute2)
    }

    // #7 (STK-034, I-D33; CR 510.1c–d): Bob's division comes after both of Alice's. He gives it early while her first is
    // open (the panel offers it); her first answer opens her second, and that batch removes his early answer because
    // "its decision didn't open in that batch". The panel then offers it again, and the same happens, so an early
    // answer only ever works for the division right after the open one
    @Ignore("i3review #7: an early answer is dropped by any batch before its decision opens, not only by the game moving on")
    @Test fun anEarlyAnswerWaitsForItsOwnDecision() {
        val (rt, ogre, brutes) = twoDivisionsBeforeBobs()
        val (brute1, brute2) = brutes
        assertEquals(alice, CombatFlow.division(rt.call())!!.player)
        val early = shown(rt).early.first { it.player == bob }
        assertEquals(ogre, early.source, "Bob is offered his division early")
        manual(rt, bob, PreAnswers.set(rt.state.board, PreAnswer.Division(bob, ref(rt, ogre),
            listOf(PreShare(ref(rt, brute1), 3), PreShare(ref(rt, brute2), 0)))))
        rt.run(alice) { CombatFlow.divide(it) }
        assertEquals(alice, CombatFlow.division(rt.call())?.player, "Alice's second division is open")
        assertEquals(1, PreAnswers.of(rt.state.board).size, "Bob's early answer still waits for his division")
        rt.run(alice) { CombatFlow.divide(it) }
        assertNull(CombatFlow.division(rt.call()), "Bob's division was answered with his early answer")
    }

    // #8 (I-D33, PreAnswers KDoc "one given meanwhile never refuses the batch that answers the decision before it (that
    // batch then leaves it to the next one)"): Bob's early answer reaches the server while Alice's division is being
    // planned. Her batch (planned on the board without it) is accepted and opens Bob's division, but nothing applies the
    // early answer then: Bob is asked as if he had given none, and the stale record stays until some later batch
    @Ignore("i3review #8: an early answer that crosses the batch opening its decision is never applied")
    @Test fun anEarlyAnswerCrossingTheBatchBeforeItIsStillApplied() {
        val (brute, bear) = table.put(alice, PlayZone.BATTLEFIELD, "Ironhide Brute", "Stout Bear")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        blocksTwo(ogre)
        val rt = game()
        toAttackers(rt)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(brute), LaneAttack(bear))) }
        while (position(rt) == TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, brute), LaneBlock(ogre, bear))) }
        while (CombatFlow.division(rt.call()) == null) passBob(rt)
        // Alice's answer is worked out on the board as it is now ...
        val call = rt.call().also { CombatFlow.divide(it) }
        // ... while Bob's early answer reaches the server first
        manual(rt, bob, PreAnswers.set(rt.state.board, PreAnswer.Division(bob, ref(rt, ogre),
            listOf(PreShare(ref(rt, bear), 3), PreShare(ref(rt, brute), 0)))))
        // The server takes Alice's batch after it (its board digest leaves early answers out)
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "i3-race"), GameScope(rt.state.gameId, playId = rt.state.id),
            GameAction.PlayBatch(listOf(PlayAction.PassAttention)), CommandIds.uuidV5(AtwTable.NAMESPACE, "stream"), "1")
        val plan = EngineBatch.plan(call, alice, command)
        val batch = command.copy(action = plan.action)
        for ((i, action) in plan.actions.withIndex()) {
            val change = GameplayRules.change(rt.state, alice, PlayBatches.member(batch, i, action))
            assertEquals("accepted", change.outcome, "member $i $action: ${change.reason}")
            rt.state = change.state!!
            rt.tags += change.mutation!!.tag
        }
        val open = assertNotNull(CombatFlow.division(rt.call()), "Bob's division is open")
        assertEquals(bob, open.player)
        // Nothing applies his early answer to the open decision, and the record lingers until some later batch
        assertTrue(PreAnswers.of(rt.state.board).isEmpty(),
            "Bob's early answer is neither applied nor removed: he is asked again with it still on the board")
    }

    // #10 (CR 117.4, SYS-067): passes in a row survive a turn changed by hand while Automatum was off. Alice passed in her
    // main phase (Bob holds priority, 1 pass); off, the turn goes to Bob's main phase by hand; on again. Bob holds priority
    // there with the stale pass counted, so his own pass ends his main phase without Alice ever getting priority in it
    // (she holds Storm Spark and a Mountain, so the host wouldn't pass for her)
    @Ignore("i3review #10: stale passes in a row from before a turn changed by hand end the next step after one pass")
    @Test fun passesInARowDoNotSurviveATurnChangedByHand() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.HAND, "Storm Spark")
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        assertEquals(MtgPriority(bob, 1), rt.state.board.priority)
        manual(rt, alice, PlayAction.Mtg(MtgAction.SetAutomatum(false)))
        manual(rt, alice, PlayAction.EndTurn)
        manual(rt, bob, PlayAction.ChooseStep(TurnPosition.PRECOMBAT_MAIN))
        manual(rt, bob, PlayAction.Mtg(MtgAction.SetAutomatum(true)))
        assertEquals(bob, rt.state.board.turn.accountId)
        rt.run(bob) { StepFlow.pass(it, bob) }
        assertEquals(TurnPosition.PRECOMBAT_MAIN, position(rt), "Bob's pass alone doesn't end his main phase")
        assertEquals(alice, rt.state.board.priority?.holder, "Alice gets priority in Bob's main phase")
    }

    // #9 (I-D27): Bob drags his tapped Stout Bear into the pair of Alice's face-down attacker: the refusal reason (the
    // engine's, with objects named by the full-information view) may not name the face-down creature
    @Test fun aBlockRefusalDoesNotNameAFaceDownAttacker() {
        val (hidden) = table.put(alice, PlayZone.BATTLEFIELD, "Hill Ogre")
        val (bear) = table.put(bob, PlayZone.BATTLEFIELD, "Stout Bear", tapped = true)
        table.put(bob, PlayZone.BATTLEFIELD, "Stout Bear")
        val rt = game()
        rt.state = rt.state.copy(board = rt.state.board.copy(cards = rt.state.board.cards.map {
            if (it.id == hidden) it.copy(covered = true, explicitlyCovered = true) else it }))
        toAttackers(rt)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(hidden))) }
        while (position(rt) == TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        val reason = assertNotNull(CombatFlow.blockRefusal(rt.call(), listOf(LaneBlock(bear, hidden))))
        println("i3review #9 reason: $reason")
        assertTrue("Hill Ogre" !in reason, reason)
    }
}
