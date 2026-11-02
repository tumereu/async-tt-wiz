package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameScope
import org.asyncttwiz.protocol.GameplayRules
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import org.asyncttwiz.protocol.TypedCounter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Adversarial review of the combat area redesign (I-D32, I-D38), I-D39, I-D40 and I-D42 (report
 * `arcanum-combat-review.md`, outside the repo), at Automatum's level: the rules of combat damage the table's preview and
 * division dialog rely on (CR 506–511, 702.2, 702.19), and switching Automatum off and on in the middle of a combat.
 * Each ignored test pins a problem found and fails while it isn't fixed; the others are checks that hold. Invented cards
 * only.
 */
class CombatReviewTest {
    init {
        CombatCards.ensured
        Cards.invent("Venomtusk Boar", "{2}{B}{G}", "Creature — Boar", "Deathtouch, trample", "3/3")
        Cards.invent("Oath Seer", "{2}{U}{U}", "Legendary Planeswalker — Seer", loyalty = "4")
    }
    private val table = AtwTable("combat-review")
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
    private fun zone(rt: RoundTrip, card: String) =
        rt.state.board.containers.single { c -> c.id == rt.state.board.cards.single { it.id == card }.containerId }.zone
    private fun passBob(rt: RoundTrip) = rt.run(bob) { StepFlow.pass(it, bob) }
    /** Whoever holds priority as the host takes it (HostPriority) passes. */
    private fun pass(rt: RoundTrip) = rt.call().working.let { HostPriority.wanted(it)?.holder ?: it.priority.holder }!!.value.let { holder -> rt.run(holder) { StepFlow.pass(it, holder) } }

    private fun manual(rt: RoundTrip, actor: String, action: PlayAction) {
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "combat-review/${manualRequests++}"),
            GameScope(rt.state.gameId, playId = rt.state.id), GameAction.Play(action), CommandIds.uuidV5(AtwTable.NAMESPACE, "manual"), "1")
        val change = GameplayRules.change(rt.state, actor, command)
        assertEquals("accepted", change.outcome, "$action: ${change.reason}")
        rt.state = change.state!!
        rt.tags += null
    }

    /** Automatum switched off and on again, nothing done by hand in between (I-D42 drops the turn's engine data). */
    private fun offAndOn(rt: RoundTrip) {
        manual(rt, alice, PlayAction.Mtg(MtgAction.SetAutomatum(false)))
        manual(rt, alice, PlayAction.Mtg(MtgAction.SetAutomatum(true)))
    }

    private fun blocksTwo(card: String) {
        val digest = table.state().board.cards.single { it.id == card }.definition
        val definition = table.definitions.getValue(digest)
        val face = definition.faces.single()
        table.definitions[digest] = definition.copy(faces = listOf(face.copy(abilities = face.abilities +
            Ability.Static(listOf(Mechanic.CanBlockAdditional(1)), Affected.Objects(Objects.Self)))))
    }

    /** Alice attacks with [attacks] from her main phase; returns the round trip in Bob's declare blockers step. */
    private fun attack(rt: RoundTrip, vararg attacks: LaneAttack): RoundTrip {
        rt.run(alice) { StepFlow.pass(it, alice) }
        while (position(rt) != TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        rt.run(alice) { CombatFlow.declareAttackers(it, attacks.toList()) }
        while (position(rt) == TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        assertEquals(TurnPosition.DECLARE_BLOCKERS, position(rt))
        return rt
    }

    /** Bob casts Storm Spark at [card] and it resolves (his Mountain pays). */
    private fun sparkAt(rt: RoundTrip, card: String) {
        val target = rt.obj(table.names.getValue(card), Zone.Battlefield)
        rt.cast(bob, "Storm Spark")
        rt.target(TargetRef.Object(target))
        rt.settle()
        rt.resolveAll()
        assertTrue(rt.engine().stack.isEmpty(), "Storm Spark resolved")
    }

    // CR 702.2c, 702.19b: with deathtouch any nonzero damage is lethal, so a deathtouch trampler needs 1 per blocker and
    // the rest tramples over; the division can't depend on the order (CMB-033)
    @Test fun aDeathtouchTramplerNeedsOneDamagePerBlocker() {
        val (boar) = table.put(alice, PlayZone.BATTLEFIELD, "Venomtusk Boar")
        val (ogre, bear) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre", "Stout Bear")
        val rt = attack(game(), LaneAttack(boar))
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, boar), LaneBlock(bear, boar))) }
        assertEquals(mapOf(bob to -1), assertNotNull(CombatFlow.preview(rt.call())).life)
        while (CombatFlow.division(rt.call()) == null) passBob(rt)
        val division = CombatFlow.division(rt.call())!!
        assertEquals(mapOf<MtgRef, Int>(ref(rt, ogre) to 1, ref(rt, bear) to 1), division.lethal)
        assertTrue(Divisions.orderFree(division))
    }

    // CR 702.19b, 509.1a: lethal damage counts the damage other creatures are assigned in the same step. Bob's Hill Ogre
    // (can block an additional creature) blocks both of Alice's tramplers: the first assigns 3 (lethal) to it and 2 to
    // Bob; for the second the Ogre already has lethal damage assigned, so all 5 may trample over. With the suggestions
    // Bob takes 7; the preview the table shows says the same
    @Test fun twoTramplersOnOneBlockerCountEachOthersDamage() {
        val (first, second) = table.put(alice, PlayZone.BATTLEFIELD, "Ironhide Brute", "Ironhide Brute")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        blocksTwo(ogre)
        val rt = attack(game(), LaneAttack(first), LaneAttack(second))
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, first), LaneBlock(ogre, second))) }
        val preview = assertNotNull(CombatFlow.preview(rt.call()))
        assertEquals(-7, preview.life[bob], "preview: ${preview.dealt}")
        while (CombatFlow.division(rt.call()) == null) passBob(rt)
        // Each division with its suggestion (the attacker's two, then Bob's of the Ogre's damage, CR 510.1)
        var guard = 4
        while (guard-- > 0) {
            val open = CombatFlow.division(rt.call()) ?: break
            rt.run(open.player) { CombatFlow.divide(it) }
        }
        assertEquals("13", rt.state.board.players.single { it.accountId == bob }.life, "the suggestions deal 2 + 5 to Bob")
    }

    // CR 702.19b, 506.4c: a trampler attacking a planeswalker and blocked tramples over onto the planeswalker, not its
    // controller (CR 702.19f): the division's last row is the planeswalker, and the preview's damage goes to it
    @Test fun aTramplerAttackingAPlaneswalkerTramplesOntoIt() {
        val (brute) = table.put(alice, PlayZone.BATTLEFIELD, "Ironhide Brute")
        val (seer, bear) = table.put(bob, PlayZone.BATTLEFIELD, "Oath Seer", "Stout Bear")
        val rt = game()
        rt.state = rt.state.copy(board = rt.state.board.copy(cards = rt.state.board.cards.map {
            if (it.id == seer) it.copy(typedCounters = listOf(TypedCounter("loyalty", 4, 0))) else it }))
        attack(rt, LaneAttack(brute, seer))
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(bear, brute))) }
        val preview = assertNotNull(CombatFlow.preview(rt.call()))
        assertEquals(mapOf(bear to 2, seer to 3, brute to 2), preview.damage)
        assertEquals(emptyMap(), preview.life.filterValues { it != 0 }, "Bob's life doesn't change")
        while (CombatFlow.division(rt.call()) == null) passBob(rt)
        assertEquals(ref(rt, seer), CombatFlow.division(rt.call())!!.trampleTo)
    }

    /** Alice's Ironhide Brute (trample) and Stout Bear attack; Bob's Hill Ogre (can block an additional creature) blocks
     * both; Bob then kills his own Ogre with Storm Spark before damage. */
    private fun blockerGone(): Triple<RoundTrip, String, String> {
        val (brute, aliceBear) = table.put(alice, PlayZone.BATTLEFIELD, "Ironhide Brute", "Stout Bear")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        blocksTwo(ogre)
        val rt = attack(game(), LaneAttack(brute), LaneAttack(aliceBear))
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, brute), LaneBlock(ogre, aliceBear))) }
        assertEquals(bob, rt.state.board.priority?.holder, "Bob holds priority with his instant")
        sparkAt(rt, ogre)
        assertEquals(PlayZone.GRAVEYARD, zone(rt, ogre))
        assertEquals(TurnPosition.DECLARE_BLOCKERS, position(rt))
        return Triple(rt, brute, aliceBear)
    }

    // CR 702.19d, 509.1h, 510.1c: once its only blocker is gone, a blocked trampler assigns all its damage to the player,
    // and a blocked creature without trample stays blocked and assigns none
    @Test fun aBlockedAttackerWhoseBlockerIsGone() {
        val (rt, brute, aliceBear) = blockerGone()
        val preview = assertNotNull(CombatFlow.preview(rt.call()))
        assertEquals(mapOf(bob to -5), preview.life)
        assertTrue(preview.dealt.none { it.source == aliceBear }, "the Bear stays blocked: ${preview.dealt}")
        assertTrue(preview.dealt.any { it.source == brute && it.target == MtgRef.Player(bob) && it.amount == 5 })
    }

    // CR 509.1h, I-D42, SYS-070: switching Automatum off and on (nothing done by hand) drops the combat record; read
    // from the lanes, the Bear's pair keeps "blocked", so it still deals no damage
    @Test fun switchingOffAndOnKeepsABlockedAttackerBlocked() {
        val (rt, _, aliceBear) = blockerGone()
        offAndOn(rt)
        val preview = assertNotNull(CombatFlow.preview(rt.call()))
        assertTrue(preview.dealt.none { it.source == aliceBear }, "the Bear stays blocked (CR 509.1h): ${preview.dealt}")
        assertEquals(mapOf(bob to -5), preview.life)
    }

    // combatreview #4 (fixed) — CR 510.4, I-D31, I-D42, SYS-070: Alice's Dawnlance Squire (first strike 2/1) is blocked by Bob's
    // Hill Ogre (3/3); in the first-strike damage step it dealt its 2. Switched off and on there (nothing done by hand),
    // the combat record (which knows this is the first-strike step and who dealt damage in it) is dropped: rebuilt from
    // the lanes the engine takes the step as the regular combat damage step, so the next pass's batch counts passes at
    // COMBAT_DAMAGE while ATW is still at FIRST_STRIKE_DAMAGE and the rebuilt state no longer matches the engine's
    // (the round trip fails); the Squire would also be free to strike again. Expected: the regular step follows with
    // only the Ogre dealing damage
    @Test fun switchingOffAndOnInTheFirstStrikeStepDoesNotStrikeTwice() {
        val (squire) = table.put(alice, PlayZone.BATTLEFIELD, "Dawnlance Squire")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        val rt = attack(game(), LaneAttack(squire))
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, squire))) }
        while (position(rt) != TurnPosition.FIRST_STRIKE_DAMAGE) passBob(rt)
        assertEquals("2", rt.state.board.cards.single { it.id == ogre }.damage)
        offAndOn(rt)
        var guard = 6
        while (position(rt) == TurnPosition.FIRST_STRIKE_DAMAGE && guard-- > 0) pass(rt)
        assertEquals(PlayZone.GRAVEYARD, zone(rt, squire), "the Ogre's 3 kill the Squire")
        assertEquals(PlayZone.BATTLEFIELD, zone(rt, ogre), "the Squire doesn't strike again (CR 510.4)")
        assertEquals("2", rt.state.board.cards.single { it.id == ogre }.damage)
    }

    // CR 506.4c, 510.1b: a planeswalker that leaves the battlefield is removed from combat; the creature attacking it stays
    // attacking but, unblocked, deals no combat damage (to nobody, not to its controller)
    @Test fun anAttackerOfAPlaneswalkerThatLeftDealsNoDamage() {
        val (aliceBear) = table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear")
        val (seer) = table.put(bob, PlayZone.BATTLEFIELD, "Oath Seer")
        val rt = game()
        rt.state = rt.state.copy(board = rt.state.board.copy(cards = rt.state.board.cards.map {
            if (it.id == seer) it.copy(typedCounters = listOf(TypedCounter("loyalty", 3, 0))) else it }))
        // Bob has no creature: no blockers are declared (nothing to declare)
        attack(rt, LaneAttack(aliceBear, seer))
        sparkAt(rt, seer)
        assertEquals(PlayZone.GRAVEYARD, zone(rt, seer))
        val preview = assertNotNull(CombatFlow.preview(rt.call()))
        assertTrue(preview.dealt.isEmpty(), "${preview.dealt}")
        assertTrue(preview.life.values.all { it == 0 }, "${preview.life}")
    }

    // combatreview #3 (fixed) — CR 506.4c, 510.1b, 702.19f, I-D42: as above, then Automatum is switched off and on. When the
    // planeswalker left, the planner moved the Bear's pair into Bob's own lane (attackTarget null), so the table shows the
    // Bear attacking Bob, and read back from the lanes it is attacking Bob: it deals 2 to Bob. Expected: no damage
    @Test fun switchingOffAndOnAfterTheAttackedPlaneswalkerLeft() {
        val (aliceBear) = table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear")
        val (seer) = table.put(bob, PlayZone.BATTLEFIELD, "Oath Seer")
        val rt = game()
        rt.state = rt.state.copy(board = rt.state.board.copy(cards = rt.state.board.cards.map {
            if (it.id == seer) it.copy(typedCounters = listOf(TypedCounter("loyalty", 3, 0))) else it }))
        // Bob has no creature: no blockers are declared (nothing to declare)
        attack(rt, LaneAttack(aliceBear, seer))
        sparkAt(rt, seer)
        assertTrue(rt.state.board.combat!!.pairs.none { p -> p.attackTarget == null && p.attackers.any { it.sourceId == aliceBear } },
            "the lanes don't show the Bear attacking Bob")
        offAndOn(rt)
        val preview = assertNotNull(CombatFlow.preview(rt.call()), "the combat can be taken over")
        assertTrue(preview.dealt.isEmpty(), "${preview.dealt}")
        assertTrue(preview.life.values.all { it == 0 }, "${preview.life}")
    }

    // combatreview #11 (fixed) — SYS-070, I-D42, CR 117.3d: Alice passed in her main phase, so Bob holds priority; switching
    // Automatum off and on (nothing done by hand) keeps him holding it, with Alice's pass counted
    @Test fun switchingOffAndOnKeepsThePriorityHolder() {
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        assertEquals(MtgPriority(bob, 1), rt.state.board.priority)
        offAndOn(rt)
        assertEquals(MtgPriority(bob, 1), rt.state.board.priority)
        assertEquals(null, HostPriority.wanted(rt.call().working), "the host leaves priority with Bob")
    }
}
