package org.asyncttwiz.automatum

import arcanum.engine.cards.Cards
import org.asyncttwiz.protocol.EngineRecord
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Invented creatures with one combat keyword each (our own names; keyword wordings as printed). */
internal object CombatCards {
    val ensured: Boolean by lazy {
        Cards.invent("Ironhide Brute", "{3}{G}", "Creature — Beast", "Trample", "5/5")
        Cards.invent("Viper Adept", "{1}{B}", "Creature — Snake", "Deathtouch", "1/1")
        Cards.invent("Twinblade Duelist", "{1}{R}{W}", "Creature — Human Warrior", "Double strike", "2/2")
        Cards.invent("Dawnlance Squire", "{1}{W}", "Creature — Human Knight", "First strike", "2/1")
        Cards.invent("Bloodbond Acolyte", "{1}{B}", "Creature — Vampire", "Lifelink", "2/2")
        Cards.invent("Grim Prowler", "{1}{B}", "Creature — Cat", "Menace", "2/2")
        Cards.invent("Stout Bear", "{1}{G}", "Creature — Bear", "", "2/2")
        Cards.invent("Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
        true
    }
}

/**
 * Combat through the engine (integration spec §3 I3, I-D29, CR 506–511): declarations from lane data, the engine's
 * legality with reasons, the damage preview, divisions with the suggestion, first and double strike, trample,
 * deathtouch, lifelink and removal from combat, all mapped back to ATW's lanes and events. Bob keeps an instant he could
 * cast, so the host never passes for him and each call stops where he holds priority (I-D18); every call is checked by
 * [RoundTrip].
 */
class CombatFlowTest {
    init { CombatCards.ensured }
    private val table = AtwTable("combat")
    private val alice get() = table.alice
    private val bob get() = table.bob

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
    private fun zone(rt: RoundTrip, card: String) =
        rt.state.board.containers.single { c -> c.id == rt.state.board.cards.single { it.id == card }.containerId }.zone
    private fun life(rt: RoundTrip, player: String) = rt.state.board.players.single { it.accountId == player }.life
    private fun damage(rt: RoundTrip, card: String) = rt.state.board.cards.single { it.id == card }.damage
    private fun ref(rt: RoundTrip, card: String) = MtgRef.Card(card, rt.state.board.cards.single { it.id == card }.zoneChanges)
    private fun passBob(rt: RoundTrip) = rt.run(bob) { StepFlow.pass(it, bob) }
    private fun combatRecord(rt: RoundTrip): EngineRecord? = rt.state.board.engineRecords.singleOrNull { it.id == "combat" }

    /** From Alice's main phase to her declaration of attackers. */
    private fun toAttackers(rt: RoundTrip) {
        rt.run(alice) { StepFlow.pass(it, alice) }
        while (position(rt) != TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        assertNull(rt.state.board.priority?.holder, "nobody has priority while attackers are awaited (CR 508.1)")
    }

    // CR 509.1a, 510.1c, 702.19b, 702.2c, 704.5g–h: a trampler blocked by two creatures, one with deathtouch; its
    // controller divides its damage (the suggestion first: lethal to each blocker, the rest to the player)
    @Test fun trampleOverTwoBlockersWithDeathtouch() {
        val (brute) = table.put(alice, PlayZone.BATTLEFIELD, "Ironhide Brute")
        val (bear, viper) = table.put(bob, PlayZone.BATTLEFIELD, "Stout Bear", "Viper Adept")
        val rt = game()
        toAttackers(rt)
        val attackChoices = CombatFlow.choices(rt.state, rt.definitions, emptyList())
        assertEquals(alice, attackChoices.declaring)
        assertEquals(mapOf(brute to listOf<String?>(null)), attackChoices.attacks)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(brute))) }
        assertEquals(listOf(listOf(brute)), rt.state.board.combat!!.pairs.map { p -> p.attackers.map { it.sourceId } })
        assertTrue(rt.state.board.cards.single { it.id == brute }.tapped, "attacking taps it (CR 508.1f)")
        passBob(rt)
        assertEquals(TurnPosition.DECLARE_BLOCKERS, position(rt))
        val blockChoices = CombatFlow.choices(rt.call())
        assertEquals(bob, blockChoices.declaring)
        assertEquals(mapOf(bear to listOf(brute), viper to listOf(brute)), blockChoices.blocks)
        val blocks = listOf(LaneBlock(bear, brute), LaneBlock(viper, brute))
        // I-D29: the preview of the blocks Bob is arranging
        val preview = assertNotNull(CombatFlow.preview(rt.call(), blocks))
        assertEquals(mapOf(bear to 2, viper to 1, bob to 2, brute to 3), preview.damage)
        assertEquals(setOf(bear, viper, brute), preview.dies.toSet())
        assertEquals(mapOf(bob to -2), preview.life)
        rt.run(bob) { CombatFlow.declareBlockers(it, blocks) }
        assertEquals(listOf(LaneAttack(brute)) to blocks, CombatFlow.lanes(rt.state.board.combat))
        assertEquals(preview.damage, CombatFlow.preview(rt.call())!!.damage, "the same preview once declared")
        passBob(rt)
        assertEquals(TurnPosition.COMBAT_DAMAGE, position(rt))
        val division = assertNotNull(CombatFlow.division(rt.call()))
        assertEquals(alice, division.player)
        assertEquals(brute, division.source)
        assertEquals(5, division.amount)
        assertEquals(mapOf<MtgRef, Int>(ref(rt, bear) to 2, ref(rt, viper) to 1), division.lethal)
        assertEquals(mapOf<MtgRef, Int>(ref(rt, bear) to 2, ref(rt, viper) to 1, MtgRef.Player(bob) to 2), division.suggested)
        assertEquals(MtgRef.Player(bob), division.trampleTo)
        // A division the engine refuses (damage to the player before lethal damage to each blocker) changes nothing
        assertFailsWith<IllegalArgumentException> {
            CombatFlow.divide(rt.call(), mapOf(ref(rt, bear) to 1, ref(rt, viper) to 1, MtgRef.Player(bob) to 3))
        }
        rt.run(bob) { CombatFlow.divide(it, mapOf(ref(rt, bear) to 3, ref(rt, viper) to 2)) }
        assertEquals("20", life(rt, bob))
        assertEquals(listOf(PlayZone.GRAVEYARD), listOf(brute, bear, viper).map { zone(rt, it) }.distinct())
        assertTrue(rt.state.board.combat!!.pairs.all { it.attackers.isEmpty() && it.blockers.isEmpty() }, "the dead leave the lanes")
        passBob(rt)
        assertEquals(TurnPosition.END_COMBAT, position(rt))
        passBob(rt)
        assertEquals(TurnPosition.POSTCOMBAT_MAIN, position(rt))
        assertNull(rt.state.board.combat, "combat ends with the end of combat step (CR 511.3)")
        assertNull(combatRecord(rt))
    }

    // CR 510.4, 702.4b, 702.4c, 702.15b, 506.4: first and double strike make two damage steps; a creature killed by first
    // strike damage deals none; lifelink gains its controller life; damage goes in the cleanup step (CR 514.2)
    @Test fun firstAndDoubleStrikeWithLifelink() {
        val (duelist, acolyte, aliceBear) = table.put(alice, PlayZone.BATTLEFIELD, "Twinblade Duelist", "Bloodbond Acolyte", "Stout Bear")
        val (squire, ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Dawnlance Squire", "Hill Ogre")
        val rt = game()
        toAttackers(rt)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(duelist, acolyte, aliceBear).map { LaneAttack(it) }) }
        passBob(rt)
        val blocks = listOf(LaneBlock(squire, aliceBear), LaneBlock(ogre, acolyte))
        val preview = assertNotNull(CombatFlow.preview(rt.call(), blocks))
        assertEquals(listOf(
            PreviewDamage(duelist, MtgRef.Player(bob), 2, firstStrike = true),
            PreviewDamage(squire, ref(rt, aliceBear), 2, firstStrike = true),
        ), preview.dealt.filter { it.firstStrike }.sortedBy { it.source != duelist })
        assertEquals(mapOf(bob to 4, ogre to 2, acolyte to 3, aliceBear to 2), preview.damage)
        assertEquals(mapOf(bob to -4, alice to 2), preview.life)
        assertEquals(setOf(aliceBear, acolyte), preview.dies.toSet())
        rt.run(bob) { CombatFlow.declareBlockers(it, blocks) }
        passBob(rt)
        // The first-strike damage step
        assertEquals(TurnPosition.COMBAT_DAMAGE, position(rt))
        assertEquals("18", life(rt, bob))
        assertEquals(PlayZone.GRAVEYARD, zone(rt, aliceBear))
        // CR 506.4, 510.1d, CMB-006: the dead attacker leaves the lanes; its blocker stays blocking
        assertTrue(rt.state.board.combat!!.pairs.none { p -> p.attackers.any { it.sourceId == aliceBear } })
        assertTrue(rt.state.board.combat!!.pairs.any { p -> p.blockers.any { it.sourceId == squire } })
        assertTrue(CombatFlow.firstStrikeStep(rt.call()), "the combat record knows the first-strike step")
        passBob(rt)
        assertTrue(!CombatFlow.firstStrikeStep(rt.call()))
        // The regular damage step: ATW shows the same step
        assertEquals(TurnPosition.COMBAT_DAMAGE, position(rt))
        assertEquals("16", life(rt, bob))
        assertEquals("22", life(rt, alice))
        assertEquals(PlayZone.GRAVEYARD, zone(rt, acolyte))
        assertEquals(PlayZone.BATTLEFIELD, zone(rt, squire))
        assertEquals("2", damage(rt, ogre))
        var guard = 20
        while (rt.state.board.turn.accountId == alice && guard-- > 0) passBob(rt)
        assertEquals(bob, rt.state.board.turn.accountId)
        assertEquals("", damage(rt, ogre), "damage is removed in the cleanup step")
    }

    // CR 508.1a, 302.6, 509.1b, 702.9b, 702.111b: who may attack and block, refusals with reasons, and a creature with
    // menace blocked by two
    @Test fun legalityAndRefusals() {
        val (glider, prowler, mite, sick) = table.put(alice, PlayZone.BATTLEFIELD, "Gearwing Glider", "Grim Prowler", "Tinkered Mite", "Stout Bear")
        table.put(alice, PlayZone.BATTLEFIELD, "Tinkered Mite", tapped = true)
        val (bear, ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Stout Bear", "Hill Ogre")
        val rt = game()
        // The Stout Bear came under Alice's control this turn (CR 302.6)
        rt.state = rt.state.copy(board = rt.state.board.copy(cards = rt.state.board.cards.map { if (it.id == sick) it.copy(enteredTurn = "3") else it }))
        toAttackers(rt)
        val choices = CombatFlow.choices(rt.state, rt.definitions, emptyList())
        assertEquals(setOf(glider, prowler, mite), choices.attacks.keys)
        val refusal = assertNotNull(CombatFlow.attackRefusal(rt.call(), listOf(LaneAttack(sick))))
        assertTrue("Stout Bear can't attack" in refusal, refusal)
        val call = rt.call()
        assertFailsWith<CombatRefused> { CombatFlow.declareAttackers(call, listOf(LaneAttack(glider), LaneAttack(sick))) }
        assertTrue(call.changes.isEmpty(), "a refused declaration changes nothing")
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(glider), LaneAttack(prowler))) }
        passBob(rt)
        val blocks = CombatFlow.choices(rt.call())
        assertEquals(mapOf(bear to listOf(prowler), ogre to listOf(prowler)), blocks.blocks, "nothing of Bob's can block a flyer")
        val flyer = assertNotNull(CombatFlow.blockRefusal(rt.call(), listOf(LaneBlock(bear, glider))))
        assertTrue("Stout Bear can't block Gearwing Glider" in flyer, flyer)
        val menace = assertNotNull(CombatFlow.blockRefusal(rt.call(), listOf(LaneBlock(bear, prowler))))
        assertTrue("Grim Prowler can't be blocked except by 2" in menace, menace)
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(bear, prowler), LaneBlock(ogre, prowler))) }
        passBob(rt)
        val division = assertNotNull(CombatFlow.division(rt.call()))
        assertEquals(mapOf<MtgRef, Int>(ref(rt, bear) to 2, ref(rt, ogre) to 0), division.suggested)
        // Bob accepts the suggestion for Alice (D11)
        rt.run(bob) { CombatFlow.divide(it) }
        assertEquals(PlayZone.GRAVEYARD, zone(rt, bear))
        assertEquals(PlayZone.GRAVEYARD, zone(rt, prowler))
        assertEquals("20", life(rt, bob), "the flyer has no power")
    }
}
