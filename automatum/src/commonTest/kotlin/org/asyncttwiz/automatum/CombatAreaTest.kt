package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Mechanic
import arcanum.api.Objects
import org.asyncttwiz.protocol.CombatAction
import org.asyncttwiz.protocol.CombatDestination
import org.asyncttwiz.protocol.CombatRole
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameScope
import org.asyncttwiz.protocol.GameplayRules
import org.asyncttwiz.protocol.IdentityGap
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The combat area redesign (I-D32) as Automatum sees it: blocks drafted in the lanes with one blocker blocking several
 * attackers (CMB-031), the refusals the quick actions show while a declaration can't be made (CMB-028), how many attackers
 * a blocker can block, the division dialog's "In order" and "Free" rules and divisions answered by themselves when their
 * order can't change anything (CMB-033). Every call is checked by [RoundTrip].
 */
class CombatAreaTest {
    init { CombatCards.ensured }
    private val table = AtwTable("combat-area")
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
        return RoundTrip(table.state(), table.definitions)
    }

    private fun position(rt: RoundTrip) = TurnPosition.of(rt.state.board.turn)
    private fun ref(rt: RoundTrip, card: String) = MtgRef.Card(card, rt.state.board.cards.single { it.id == card }.zoneChanges)
    private fun passBob(rt: RoundTrip) = rt.run(bob) { StepFlow.pass(it, bob) }
    private fun shown(rt: RoundTrip) = CombatFlow.shown(rt.state, rt.definitions, emptyList())

    private fun manual(rt: RoundTrip, actor: String, action: PlayAction) {
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "area/${manualRequests++}"),
            GameScope(rt.state.gameId, playId = rt.state.id), GameAction.Play(action), CommandIds.uuidV5(AtwTable.NAMESPACE, "manual"), "1")
        val change = GameplayRules.change(rt.state, actor, command)
        assertEquals("accepted", change.outcome, change.reason)
        rt.state = change.state!!
        rt.tags += null
    }

    private fun blocksTwo(card: String) {
        val digest = table.state().board.cards.single { it.id == card }.definition
        val definition = table.definitions.getValue(digest)
        val face = definition.faces.single()
        table.definitions[digest] = definition.copy(faces = listOf(face.copy(abilities = face.abilities +
            Ability.Static(listOf(Mechanic.CanBlockAdditional(1)), Affected.Objects(Objects.Self)))))
    }

    /** Alice attacks with [attackers]; returns the round trip in Bob's declare blockers step. */
    private fun attack(vararg attackers: String): RoundTrip {
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        while (position(rt) != TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        rt.run(alice) { CombatFlow.declareAttackers(it, attackers.map { LaneAttack(it) }) }
        while (position(rt) == TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        assertEquals(TurnPosition.DECLARE_BLOCKERS, position(rt))
        return rt
    }

    private fun pair(rt: RoundTrip, attacker: String) =
        rt.state.board.combat!!.pairs.single { p -> p.attackers.any { it.sourceId == attacker } }.id

    // CMB-031, CR 509.1a: a blocker that can block an additional creature blocks two attackers as one card with two
    // blocks in the lanes; the draft is declared as both blocks, and the preview counts its damage once
    @Test fun aBlockerOfTwoIsDraftedWithBothBlocksAndDeclared() {
        val (brute, bear) = table.put(alice, PlayZone.BATTLEFIELD, "Ironhide Brute", "Stout Bear")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        blocksTwo(ogre)
        val rt = attack(brute, bear)
        val choices = shown(rt).choices
        assertEquals(mapOf<String, Int?>(ogre to 2), choices.capacity, "it can block two attackers at once")
        val area = rt.state.board.combat!!.id
        manual(rt, bob, PlayAction.Combat(CombatAction.SetBlocks(area, ogre, listOf(pair(rt, brute)))))
        manual(rt, bob, PlayAction.Combat(CombatAction.SetBlocks(area, ogre, listOf(pair(rt, brute), pair(rt, bear)), listOf(pair(rt, brute)))))
        assertEquals(1, CombatStackRules.occurrences(rt.state.board.combat).count { it.sourceId == ogre }, "one copy, two blocks")
        assertEquals(listOf(LaneBlock(ogre, brute), LaneBlock(ogre, bear)), CombatFlow.lanes(rt.state.board.combat).second)
        val drafted = shown(rt)
        assertNull(drafted.draftRefusal, "Automatum takes the drafted blocks")
        rt.run(bob) { CombatFlow.declareBlockers(it, CombatFlow.lanes(rt.state.board.combat).second) }
        assertEquals(listOf(LaneBlock(ogre, brute), LaneBlock(ogre, bear)), CombatFlow.lanes(rt.state.board.combat).second,
            "the declared combat keeps both blocks in the lanes")
    }

    // CMB-031, CR 509.1a: the engine's blocks of a blocker of two reach the lanes as both blocks when declared without a
    // draft; a blocker that can block only one is refused a second
    @Test fun declaredBlocksOfTwoReachTheLanes() {
        val (brute, bear) = table.put(alice, PlayZone.BATTLEFIELD, "Ironhide Brute", "Stout Bear")
        val (ogre, bear2) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre", "Stout Bear")
        blocksTwo(ogre)
        val rt = attack(brute, bear)
        assertNotNull(rt.call().let { CombatFlow.blockRefusal(it, listOf(LaneBlock(bear2, brute), LaneBlock(bear2, bear))) },
            "a creature without the ability blocks one attacker")
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, brute), LaneBlock(ogre, bear))) }
        val occurrence = CombatStackRules.occurrences(rt.state.board.combat).single { it.sourceId == ogre }
        assertEquals(listOf(pair(rt, brute), pair(rt, bear)), CombatStackRules.blockedPairs(rt.state.board.combat, occurrence.id))
    }

    // CMB-028, CR 702.111b (menace), 509.1c: while blocks can't be declared the quick actions read the engine's reason; a
    // creature that must block makes declaring no blocks refused
    @Test fun refusalsOfTheDraftAndOfNoBlocks() {
        val (prowler) = table.put(alice, PlayZone.BATTLEFIELD, "Grim Prowler")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        val rt = attack(prowler)
        assertNull(shown(rt).draftRefusal, "nothing drafted is declaring no blocks, which is legal")
        assertNull(shown(rt).noneRefusal)
        manual(rt, bob, PlayAction.Combat(CombatAction.SetBlocks(rt.state.board.combat!!.id, ogre, listOf(pair(rt, prowler)))))
        val refused = assertNotNull(shown(rt).draftRefusal, "menace: one blocker isn't enough")
        assertTrue(refused.isNotBlank())
    }

    private fun division(amount: Int, lethal: List<Int>, trample: Boolean): DamageDivision {
        val creatures = lethal.indices.map { MtgRef.Card("c$it", 0) }
        val player = MtgRef.Player("bob")
        return DamageDivision("alice", "attacker", amount, creatures + listOfNotNull(player.takeIf { trample }),
            creatures.zip(lethal).toMap(), emptyMap(), player.takeIf { trample })
    }

    // CMB-033, CR 510.1c: "In order" gives each creature lethal damage in turn, short of that the rest to the next; with
    // more than enough the first gets the excess, as the engine's suggestion does
    @Test fun inOrderGivesLethalInTurn() {
        val d = division(5, listOf(2, 4), trample = false)
        val (a, b) = Divisions.creatures(d)
        assertEquals(mapOf<MtgRef, Int>(a to 2, b to 3), Divisions.inOrder(d, listOf(a, b)))
        assertEquals(mapOf<MtgRef, Int>(a to 1, b to 4), Divisions.inOrder(d, listOf(b, a)))
        val more = division(8, listOf(2, 4), trample = false)
        assertEquals(mapOf<MtgRef, Int>(a to 4, b to 4), Divisions.inOrder(more, listOf(a, b)))
    }

    // CMB-033, CR 702.19b: with trample the attacked player gets what is left once each blocker has lethal damage
    @Test fun inOrderWithTrampleGivesTheRestToThePlayer() {
        val d = division(7, listOf(2, 3), trample = true)
        val (a, b) = Divisions.creatures(d)
        assertEquals(mapOf(a to 2, b to 3, d.trampleTo!! to 2), Divisions.inOrder(d, listOf(a, b)))
        val short = division(4, listOf(2, 3), trample = true)
        assertEquals(mapOf(a to 1, b to 3, d.trampleTo!! to 0), Divisions.inOrder(short, listOf(b, a)), "not enough to trample over")
    }

    // CMB-033, CR 510.1c, 702.19b: "Free" takes any division of all the damage, but with trample nothing goes past the
    // blockers before each has lethal damage
    @Test fun freeDivisionRules() {
        val d = division(5, listOf(2, 3), trample = true)
        val (a, b) = Divisions.creatures(d)
        val p = d.trampleTo!!
        assertNull(Divisions.refusal(d, mapOf(a to 5, b to 0, p to 0)), "all to one blocker is legal (CR 510.1c)")
        assertNotNull(Divisions.refusal(d, mapOf(a to 2, b to 2, p to 0)), "all of it must be divided")
        assertNotNull(Divisions.refusal(d, mapOf(a to 2, b to 2, p to 1)), "the Bear needs lethal before the player gets any")
        assertNull(Divisions.refusal(d, mapOf(a to 2, b to 3, p to 0)))
    }

    // CMB-033: the order can't change the result with one creature, or with damage enough for lethal damage to all
    @Test fun orderFreeDivisions() {
        assertTrue(Divisions.orderFree(division(5, listOf(3), trample = true)))
        assertTrue(Divisions.orderFree(division(5, listOf(2, 3), trample = false)))
        assertFalse(Divisions.orderFree(division(4, listOf(2, 3), trample = true)))
    }

    // CMB-033, CR 510.1c, 702.19b: a trampler blocked by one creature is a division the table makes by itself with the
    // suggestion (lethal damage to the blocker, the rest to the player); one blocked by two it can't kill both of is asked
    @Test fun divisionsWhoseOrderCantMatterAreMadeByThemselves() {
        val (brute) = table.put(alice, PlayZone.BATTLEFIELD, "Ironhide Brute")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        val rt = attack(brute)
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, brute))) }
        while (CombatFlow.division(rt.call()) == null) passBob(rt)
        assertTrue(CombatFlow.answeredByItself(rt.call()))
        assertEquals(mapOf(ref(rt, ogre) to 3, MtgRef.Player(bob) to 2), CombatFlow.division(rt.call())!!.suggested)
    }

    // CMB-033, CR 510.1c
    @Test fun aDivisionWithAChoiceIsAsked() {
        val (brute) = table.put(alice, PlayZone.BATTLEFIELD, "Ironhide Brute")
        val (ogre, second) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre", "Hill Ogre")
        val rt = attack(brute)
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, brute), LaneBlock(second, brute))) }
        while (CombatFlow.division(rt.call()) == null) passBob(rt)
        assertFalse(CombatFlow.answeredByItself(rt.call()), "5 damage can't kill both 3/3s: the order matters")
    }

    // CMB-031: a blocker's further block names a pair that holds attackers; a blocker dragged into an attacker's place
    // keeps none; removing the attacker of a further block drops it
    @Test fun furtherBlocksFollowThePairs() {
        val (brute, bear) = table.put(alice, PlayZone.BATTLEFIELD, "Ironhide Brute", "Stout Bear")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        blocksTwo(ogre)
        val rt = attack(brute, bear)
        val area = rt.state.board.combat!!.id
        manual(rt, bob, PlayAction.Combat(CombatAction.SetBlocks(area, ogre, listOf(pair(rt, brute), pair(rt, bear)))))
        val bearCopy = CombatStackRules.occurrences(rt.state.board.combat).single { it.sourceId == bear }
        manual(rt, alice, PlayAction.Combat(CombatAction.Remove(area, bearCopy.id)))
        val ogreCopy = CombatStackRules.occurrences(rt.state.board.combat).single { it.sourceId == ogre }
        assertEquals(emptyList(), ogreCopy.alsoBlocks, "the Bear's pair is gone")
        // Removing every block takes the copy out
        manual(rt, bob, PlayAction.Combat(CombatAction.SetBlocks(area, ogre, emptyList(), listOf(pair(rt, brute)))))
        assertTrue(CombatStackRules.occurrences(rt.state.board.combat).none { it.sourceId == ogre })
        // A stale expectation is refused
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "stale"), GameScope(rt.state.gameId, playId = rt.state.id),
            GameAction.Play(PlayAction.Combat(CombatAction.SetBlocks(area, ogre, listOf(pair(rt, brute)), listOf(pair(rt, brute))))),
            CommandIds.uuidV5(AtwTable.NAMESPACE, "manual"), "1")
        assertEquals("sourceChanged", GameplayRules.change(rt.state, bob, command).reason)
        // An attacker's copy can't block
        val attack = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "attacker"), GameScope(rt.state.gameId, playId = rt.state.id),
            GameAction.Play(PlayAction.Combat(CombatAction.SetBlocks(area, brute, listOf(pair(rt, brute))))),
            CommandIds.uuidV5(AtwTable.NAMESPACE, "manual"), "1")
        assertEquals("inapplicable", GameplayRules.change(rt.state, bob, attack).reason)
        // Placing it by hand in a pair starts with no further blocks
        manual(rt, bob, PlayAction.Combat(CombatAction.Place(ogre, CombatDestination(area, CombatRole.BLOCKER, pairId = pair(rt, brute)))))
        assertEquals(emptyList(), CombatStackRules.occurrences(rt.state.board.combat).single { it.sourceId == ogre }.alsoBlocks)
    }
}
