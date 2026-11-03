package org.asyncttwiz.automatum

import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.state.PlayerId
import arcanum.parser.OracleCard
import org.asyncttwiz.protocol.CombatAction
import org.asyncttwiz.protocol.CombatDestination
import org.asyncttwiz.protocol.CombatRole
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameScope
import org.asyncttwiz.protocol.GameplayRules
import org.asyncttwiz.protocol.IdentityGap
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import org.asyncttwiz.protocol.TypedCounter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Invented cards for the choices and costs of combat (our own names; wordings copied from printed templates). */
internal object CombatChoiceCards {
    const val RAIDER = "Sunscorch Raider"
    const val SERGEANT = "Drillmaster Vey"
    const val KNIGHT = "Shieldwall Knight"
    const val WYRM = "Hungry Cinderwyrm"
    const val PRISON = "Mistward Prison"
    const val TOLLGATE = "Thorned Tollgate"
    const val SIEGE = "Siege of Emberfall"

    val ensured: Boolean by lazy {
        CombatCards.ensured
        Cards.invent(RAIDER, "{2}{R}", "Creature — Human Warrior",
            "You may exert this creature as it attacks. When you do, it gets +2/+0 until end of turn. " +
                "(An exerted creature won't untap during your next untap step.)", "3/3")
        Cards.invent(SERGEANT, "{1}{W}", "Creature — Human Soldier",
            "Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. " +
                "When you do, add its power to this creature's until end of turn.)", "2/2")
        Cards.invent(KNIGHT, "{1}{W}", "Creature — Human Knight", "Banding", "2/2")
        Cards.invent(WYRM, "{4}{R}", "Creature — Dragon",
            "This creature can't attack unless you sacrifice a land. (This cost is paid as attackers are declared.)", "5/5")
        Cards.invent(PRISON, "{2}{W}", "Enchantment",
            "Creatures can't attack you unless their controller pays {2} for each creature they control that's attacking you.")
        Cards.invent(TOLLGATE, "{1}{B}", "Enchantment",
            "Creatures can't block unless their controller pays 2 life for each blocking creature they control.")
        Cards.inventFaces("Transform",
            OracleCard(SIEGE, "{2}{R}", "Battle — Siege", "(As a Siege enters, choose an opponent to protect it. You and others " +
                "can attack it. When it's defeated, exile it, then cast it transformed.)", defense = "3"),
            OracleCard("Emberfall Warden", null, "Creature — Elemental", "", power = "4", toughness = "4", colorIndicator = "R"))
        true
    }
}

/**
 * The choices made with a declaration of attackers and the costs of combat through the engine (CMB-036–CMB-039): exert
 * and other optional costs to attack (CR 508.1g), enlist (CR 702.154), banding (CR 702.22), mandatory costs to attack and
 * block paid as they are declared (CR 508.1h, 509.1d, with Autotap's suggestion for mana), and battles as attack targets
 * whose protector blocks (CR 310.9). Every call is checked by [RoundTrip].
 */
class CombatChoicesTest {
    init { CombatChoiceCards.ensured }
    private val table = AtwTable("combat-choices")
    private val alice get() = table.alice
    private val bob get() = table.bob
    private var manualRequests = 0

    /** Alice's main phase of turn 3; Bob keeps Storm Spark and a Mountain (I-D18), Full control for both (I-D41). */
    private fun game(): RoundTrip {
        table.turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
        table.priority = MtgPriority(alice)
        table.put(alice, PlayZone.LIBRARY, "Island", "Island", "Island")
        table.put(bob, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.HAND, "Storm Spark")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island", "Island")
        table.fullControl += listOf(alice, bob)
        return RoundTrip(table.state(), table.definitions)
    }

    private fun position(rt: RoundTrip) = TurnPosition.of(rt.state.board.turn)
    private fun card(rt: RoundTrip, id: String) = rt.state.board.cards.single { it.id == id }
    private fun zone(rt: RoundTrip, id: String) = rt.state.board.containers.single { it.id == card(rt, id).containerId }.zone
    private fun life(rt: RoundTrip, player: String) = rt.state.board.players.single { it.accountId == player }.life.toInt()
    private fun passBob(rt: RoundTrip) = rt.run(bob) { StepFlow.pass(it, bob) }
    /** Whoever holds priority as the host takes it (HostPriority) passes. */
    private fun pass(rt: RoundTrip) = rt.call().working.let { HostPriority.wanted(it, HostPriority.kept(rt.state.board))?.holder ?: it.priority.holder }!!
        .value.let { holder -> rt.run(holder) { StepFlow.pass(it, holder) } }

    private fun toAttackers(rt: RoundTrip): RoundTrip {
        rt.run(alice) { StepFlow.pass(it, alice) }
        while (position(rt) != TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        return rt
    }

    private fun toBlockers(rt: RoundTrip) {
        while (position(rt) == TurnPosition.DECLARE_ATTACKERS) passBob(rt)
        assertEquals(TurnPosition.DECLARE_BLOCKERS, position(rt))
    }

    /** A table event outside Automatum's batches (the combat draft in the lanes, an early answer). */
    private fun manual(rt: RoundTrip, actor: String, action: PlayAction) {
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "combat-choices/${manualRequests++}"),
            GameScope(rt.state.gameId, playId = rt.state.id), GameAction.Play(action), CommandIds.uuidV5(AtwTable.NAMESPACE, "manual"), "1")
        val change = GameplayRules.change(rt.state, actor, command)
        assertEquals("accepted", change.outcome, "$action: ${change.reason}")
        rt.state = change.state!!
        rt.tags += null
    }

    /** Stages [cards] as attackers of the defending player in the lanes (CMB-028). */
    private fun stage(rt: RoundTrip, vararg cards: String, target: String? = null) {
        if (rt.state.board.combat == null) manual(rt, alice, PlayAction.Combat(CombatAction.Open))
        for (c in cards) {
            val area = rt.state.board.combat!!
            manual(rt, alice, PlayAction.Combat(CombatAction.Place(c, CombatDestination(area.id, CombatRole.ATTACKER,
                pairGap = IdentityGap(area.pairs.lastOrNull()?.id), attackTarget = target))))
        }
    }

    private fun setChoices(rt: RoundTrip, vararg choices: AttackChoice) =
        manual(rt, alice, PreAnswers.set(rt.state.board, PreAnswer.AttackChoices(alice, 3, choices.toList())))

    // CMB-037, CR 508.1g, 701.43d, 607.2h: exerting is a choice of the staged attacker, paid as it is declared; its
    // "when you do" gives +2/+0, which the damage preview shows
    @Test fun exertIsChosenWithTheAttacker() {
        val (raider) = table.put(alice, PlayZone.BATTLEFIELD, CombatChoiceCards.RAIDER)
        val rt = toAttackers(game())
        val choices = CombatFlow.choices(rt.call())
        val exert = choices.optional.getValue(raider).single()
        assertEquals("Exert", exert.label)
        assertTrue(exert.text.orEmpty().contains("exert this creature as it attacks"), exert.text)
        stage(rt, raider)
        setChoices(rt, AttackChoice(raider, paying = listOf(exert.key)))
        val attacks = CombatFlow.withChoices(CombatFlow.lanes(rt.state.board.combat).first, rt.state.board)
        assertEquals(listOf(LaneAttack(raider, paying = listOf(exert.key))), attacks)
        rt.run(alice) { CombatFlow.declareAttackers(it, attacks) }
        assertNull(rt.state.board.engineRecords.firstOrNull { it.id == "pre:" + PreAnswer.ATTACK_CHOICES }, "declared: the choices are gone")
        toBlockers(rt)
        val preview = assertNotNull(CombatFlow.preview(rt.call()))
        assertEquals(mapOf(bob to -5), preview.life)
    }

    // CMB-036, CR 702.154a, 508.1g: an attacker with enlist enlists a nonattacking creature without summoning sickness:
    // it is tapped as the attacker is declared and its power is added
    @Test fun enlistTapsTheEnlistedCreature() {
        val (sergeant, bear) = table.put(alice, PlayZone.BATTLEFIELD, CombatChoiceCards.SERGEANT, "Stout Bear")
        val rt = toAttackers(game())
        val choices = CombatFlow.choices(rt.call())
        assertEquals(mapOf(sergeant to 1), choices.enlist)
        assertTrue(bear in choices.enlistable, "${choices.enlistable}")
        stage(rt, sergeant)
        setChoices(rt, AttackChoice(sergeant, enlisted = listOf(bear)))
        assertNull(CombatFlow.shown(rt.call()).draftRefusal)
        rt.run(alice) { CombatFlow.declareAttackers(it, CombatFlow.withChoices(CombatFlow.lanes(rt.state.board.combat).first, rt.state.board)) }
        assertTrue(card(rt, bear).tapped, "enlisting taps it")
        toBlockers(rt)
        assertEquals(mapOf(bob to -4), assertNotNull(CombatFlow.preview(rt.call())).life)
    }

    // CMB-036, CMB-038: a staged creature isn't enlisted; a band needs two staged members; choices of another turn count
    // for nothing
    @Test fun choicesApplyOnlyToStagedCreatures() {
        val (sergeant, bear, knight) = table.put(alice, PlayZone.BATTLEFIELD, CombatChoiceCards.SERGEANT, "Stout Bear", CombatChoiceCards.KNIGHT)
        val rt = toAttackers(game())
        stage(rt, sergeant, bear)
        setChoices(rt, AttackChoice(sergeant, enlisted = listOf(bear)), AttackChoice(knight, band = 1), AttackChoice(bear, band = 1))
        assertEquals(listOf(LaneAttack(sergeant), LaneAttack(bear)),
            CombatFlow.withChoices(CombatFlow.lanes(rt.state.board.combat).first, rt.state.board))
        manual(rt, alice, PreAnswers.set(rt.state.board, PreAnswer.AttackChoices(alice, 2, listOf(AttackChoice(sergeant, band = 1)))))
        assertEquals(emptyMap(), CombatFlow.attackChoices(rt.state.board))
    }

    // CMB-038, CR 702.22c, 702.22h: attackers with banding and one without form a band; a creature blocking one of them
    // blocks the whole band (its arrows reach each member)
    @Test fun aBandIsBlockedAsOne() {
        val (knight, bear) = table.put(alice, PlayZone.BATTLEFIELD, CombatChoiceCards.KNIGHT, "Stout Bear")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        val rt = toAttackers(game())
        assertEquals(setOf(knight), CombatFlow.choices(rt.call()).banding)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(knight, band = 1), LaneAttack(bear, band = 1))) }
        toBlockers(rt)
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, bear))) }
        assertEquals(setOf(LaneBlock(ogre, bear), LaneBlock(ogre, knight)), CombatFlow.lanes(rt.state.board.combat).second.toSet())
    }

    // CMB-037, CR 508.1h, 508.1d: a cost to attack shows on the staged creature; with Autotap its mana is paid with the
    // engine's suggestion as the attackers are declared
    @Test fun aManaCostToAttackIsShownAndAutotapped() {
        val (bear) = table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear")
        val plains = table.put(alice, PlayZone.BATTLEFIELD, "Plains", "Plains")
        table.put(bob, PlayZone.BATTLEFIELD, CombatChoiceCards.PRISON)
        val rt = toAttackers(game())
        stage(rt, bear)
        val shown = CombatFlow.shown(rt.call())
        assertNull(shown.draftRefusal)
        assertEquals(listOf("{2}"), shown.costs.getValue(bear).map { it.label })
        assertTrue(shown.costs.getValue(bear).single().text.orEmpty().startsWith("Creatures can't attack you"))
        // Without Autotap the payment is the open choice
        val open = rt.call().also { CombatFlow.declareAttackers(it, listOf(LaneAttack(bear))) }.pending
        assertIs<Decision.ManaPayment>(open?.decision)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(bear)), autotap = true) }
        assertTrue(plains.all { card(rt, it).tapped }, "both Plains pay")
        assertTrue(card(rt, bear).tapped)
        toBlockers(rt)
    }

    // CMB-037, CR 508.1h: a cost that isn't mana (sacrificing a land) stays the attacker's choice after the declaration
    @Test fun aSacrificeToAttackIsChosen() {
        val (wyrm) = table.put(alice, PlayZone.BATTLEFIELD, CombatChoiceCards.WYRM)
        val (plains) = table.put(alice, PlayZone.BATTLEFIELD, "Plains")
        val rt = toAttackers(game())
        stage(rt, wyrm)
        assertEquals(listOf("Sacrifice"), CombatFlow.shown(rt.call()).costs.getValue(wyrm).map { it.label })
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(wyrm)), autotap = true) }
        rt.settle()
        assertEquals(PlayZone.GRAVEYARD, zone(rt, plains))
        assertTrue(card(rt, wyrm).tapped)
    }

    // CMB-037, CR 509.1d: a cost to block shows on the drafted blocker and is paid as the blockers are declared
    @Test fun aCostToBlockIsShownAndPaid() {
        val (bear) = table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre", CombatChoiceCards.TOLLGATE)
        val rt = toAttackers(game())
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(bear))) }
        toBlockers(rt)
        val pair = rt.state.board.combat!!.pairs.single().id
        manual(rt, bob, PlayAction.Combat(CombatAction.SetBlocks(rt.state.board.combat!!.id, ogre, listOf(pair))))
        assertEquals(listOf("2 life"), CombatFlow.shown(rt.call()).costs.getValue(ogre).map { it.label })
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, bear)), autotap = true) }
        rt.settle()
        assertEquals(18, life(rt, bob))
    }

    // CMB-039, CR 310.9b–d, 310.12a, 310.6: Alice attacks her own Siege, which Bob protects: Bob is its defending player
    // and blocks for it; unblocked damage removes its defense counters
    @Test fun aSiegeIsAttackedAndItsProtectorBlocks() {
        val (siege, bear, ogre) = table.put(alice, PlayZone.BATTLEFIELD, CombatChoiceCards.SIEGE, "Stout Bear", "Hill Ogre")
        val (bobBear) = table.put(bob, PlayZone.BATTLEFIELD, "Stout Bear")
        val rt = game()
        rt.state = rt.state.copy(board = rt.state.board.copy(cards = rt.state.board.cards.map {
            if (it.id == siege) it.copy(typedCounters = listOf(TypedCounter("defense", 3, 0)),
                ledger = CardLedger(protector = PlayerId(bob)).text()) else it }))
        toAttackers(rt)
        val choices = CombatFlow.choices(rt.call())
        assertEquals(listOf(null, siege), choices.attacks.getValue(bear), "the player and the Siege")
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(bear, siege), LaneAttack(ogre, siege))) }
        toBlockers(rt)
        val blocks = CombatFlow.choices(rt.call())
        assertEquals(bob, blocks.declaring, "the protector blocks for it")
        assertEquals(setOf(bear, ogre), blocks.blocks.getValue(bobBear).toSet())
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(bobBear, bear))) }
        val preview = assertNotNull(CombatFlow.preview(rt.call()))
        assertEquals(3, preview.damage[siege])
        while (position(rt) != TurnPosition.END_COMBAT) {
            if (rt.call().working.pending != null) rt.settle() else pass(rt)
        }
        assertEquals(20, life(rt, bob), "the Siege took the damage, not Bob")
        assertTrue(card(rt, siege).typedCounters.none { it.kind == "defense" && it.count > 0 } || zone(rt, siege) != PlayZone.BATTLEFIELD,
            "3 damage removes its 3 defense counters: ${card(rt, siege).typedCounters}")
    }
}
