package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.parser.OracleCard
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.MtgAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Combat through the engine on a game's own engine state ([CombatFlow], [EngineProjection]'s lanes). */
class EngineCombatTest {
    init {
        Cards.invent("Ironhide Brute", "{3}{G}", "Creature — Beast", "Trample", "5/5")
        Cards.invent("Stout Bear", "{1}{G}", "Creature — Bear", "", "2/2")
        Cards.invent("Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    }
    private val table = EngineTable().apply { turn = 3; step = TurnStep.DeclareAttackers; priority = null }
    private val alice get() = table.alice
    private val bob get() = table.bob

    private fun run(state: GameState, board: org.asyncttwiz.protocol.BoardState = table.board(state)) =
        EngineRun.of(state, board, "game", table::seed)

    /** Every player passes in turn until [done] holds of the state. */
    private fun passUntil(start: GameState, done: (GameState) -> Boolean): GameState {
        var state = start
        repeat(40) {
            if (done(state)) return state
            val holder = assertNotNull(state.priority.holder, "nobody holds priority at ${state.turn.step}")
            state = run(state).also { it.perform(Input.PassPriority(holder)) }.working
        }
        error("never reached")
    }

    private fun declaredAttackers(attacks: List<LaneAttack>, state: GameState = table.state()): GameState =
        run(state).also { CombatFlow.declareAttackers(it, attacks) }.working

    private fun toBlockers(state: GameState) = passUntil(state) { it.turn.step == TurnStep.DeclareBlockers && it.priority.holder == null }

    /** [card]'s engine definition can block an additional creature (CR 509.1a). */
    private fun blocksTwo(state: GameState, card: String) {
        val obj = table.obj(card)
        val o = state.objects.getValue(obj)
        val face = o.definition!!.faces.single()
        state.objects[obj] = o.copy(definition = o.definition!!.copy(faces = listOf(face.copy(abilities = face.abilities +
            Ability.Static(listOf(Mechanic.CanBlockAdditional(1)), Affected.Objects(Objects.Self))))))
    }

    // CMB-029 (I-D29): in the declare blockers step the combat shows what damage would do if nobody does anything more,
    // with the blocks being arranged: an unblocked attacker's damage to the player, a blocked one dying
    @Test fun theDamagePreviewFollowsTheBlocksBeingArranged() {
        val (bear) = table.put(alice, Zone.Battlefield, "Stout Bear")
        val (ogre) = table.put(bob, Zone.Battlefield, "Hill Ogre")
        val blockers = toBlockers(declaredAttackers(listOf(LaneAttack(bear))))
        val shown = run(blockers)
        val unblocked = assertNotNull(CombatFlow.preview(shown, emptyList()))
        assertEquals(mapOf(bob to -2), unblocked.life)
        val blocked = assertNotNull(CombatFlow.preview(shown, listOf(LaneBlock(ogre, bear))))
        assertEquals(listOf(bear), blocked.dies, "the Ogre (3/3) kills the Bear (2/2)")
        assertTrue(blocked.life.isEmpty())
    }

    // CMB-031 (CR 509.1a): a blocker blocking two attackers is one copy in the pair of the first attacker it blocks, also
    // blocking the other's pair; STK-034 (I-D33): the defender's division of that blocker's damage given early is applied
    // when its decision opens
    @Test fun aBlockerOfTwoAttackersAndItsEarlyDivision() {
        val (brute, bear) = table.put(alice, Zone.Battlefield, "Ironhide Brute", "Stout Bear")
        val (ogre, second) = table.put(bob, Zone.Battlefield, "Hill Ogre", "Hill Ogre")
        val attacked = table.state().also { blocksTwo(it, ogre) }
        val blocking = toBlockers(declaredAttackers(listOf(LaneAttack(brute), LaneAttack(bear)), attacked))
        val declared = run(blocking).also {
            CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, brute), LaneBlock(ogre, bear), LaneBlock(second, brute)))
        }.working
        val area = assertNotNull(table.board(declared).combat)
        val ogreCopy = area.pairs.flatMap { it.blockers }.single { it.sourceId == ogre }
        assertEquals("pair:$brute", area.pairs.single { p -> p.blockers.any { it.sourceId == ogre } }.id)
        assertEquals(listOf("pair:$bear"), ogreCopy.alsoBlocks)
        assertEquals(setOf(LaneBlock(ogre, brute), LaneBlock(ogre, bear), LaneBlock(second, brute)),
            CombatFlow.lanes(area).second.toSet())
        // To the damage: Alice divides the Brute's damage first
        val damage = passUntil(declared) { it.pending != null }
        val open = assertNotNull(CombatFlow.division(run(damage)))
        assertEquals(alice to brute, open.player to open.source)
        // Bob's division of the Ogre's 3 damage, given early: all to the Bear
        val early = PreAnswer.Division(bob, MtgRef.Card(ogre, 0), listOf(PreShare(MtgRef.Card(bear, 0), 3), PreShare(MtgRef.Card(brute, 0), 0)))
        val record = assertNotNull((PreAnswers.set(table.board(damage), early) as PlayAction.Mtg).action as? MtgAction.SetRecord).after!!
        assertEquals(EngineRecordKind.PreAnswer, record.kind)
        val board = table.board(damage).let { it.copy(engineRecords = it.engineRecords + record) }
        val divided = run(damage, board).also { CombatFlow.divide(it) }
        assertNull(CombatFlow.division(divided), "Bob's decision was answered with his early division")
        assertTrue(divided.working.objects.keys.none { it is arcanum.engine.state.ObjectId.Card && it.card.value == bear &&
            divided.working.objects[it]?.zone == Zone.Battlefield }, "the Bear took the Ogre's 3 damage")
    }

    // CMB-037 (CR 508.1h): a cost to attack shows on the staged attacker ("{2}"); declared with Autotap its mana is paid
    // by itself, otherwise the payment is the attacker's open choice
    @Test fun aManaCostToAttackIsShownAndAutotapped() {
        val prison = Cards.invent("Mistward Prison", "{2}{W}", "Enchantment",
            "Creatures can't attack you unless their controller pays {2} for each creature they control that's attacking you.")
        val (bear) = table.put(alice, Zone.Battlefield, "Stout Bear")
        val plains = table.put(alice, Zone.Battlefield, "Plains", "Plains")
        table.put(bob, Zone.Battlefield, prison)
        val state = table.state()
        val staged = table.board(state).let { board ->
            board.copy(combat = org.asyncttwiz.protocol.CombatArea(EngineProjection.COMBAT_ID, listOf(
                org.asyncttwiz.protocol.CombatPair("pair:$bear", listOf(org.asyncttwiz.protocol.CombatOccurrence("attack:$bear", bear))))))
        }
        val shown = CombatFlow.shown(state, staged, "game")
        assertNull(shown.draftRefusal)
        assertEquals(listOf("{2}"), shown.costs.getValue(bear).map { it.label })
        assertIs<Decision.ManaPayment>(run(state, staged).also { CombatFlow.declareAttackers(it, listOf(LaneAttack(bear))) }.pending?.decision)
        val paid = run(state, staged).also { CombatFlow.declareAttackers(it, listOf(LaneAttack(bear)), autotap = true) }.working
        assertTrue(plains.all { paid.objects.getValue(table.obj(it)).tapped }, "both Plains pay")
    }

    // CMB-039 (CR 310.9b, 310.6): Alice attacks her own Siege, which Bob protects: Bob blocks for it, and unblocked damage
    // to it is shown against the Siege
    @Test fun aSiegeIsAttackedAndItsProtectorBlocks() {
        val siegeName = Cards.inventFaces("Transform",
            OracleCard("Siege of Emberfall", "{2}{R}", "Battle — Siege", "(As a Siege enters, choose an opponent to protect it. " +
                "You and others can attack it. When it's defeated, exile it, then cast it transformed.)", defense = "3"),
            OracleCard("Emberfall Warden", null, "Creature — Elemental", "", power = "4", toughness = "4", colorIndicator = "R"))
        val (siege, bear, ogre) = table.put(alice, Zone.Battlefield, siegeName, "Stout Bear", "Hill Ogre")
        val (bobBear) = table.put(bob, Zone.Battlefield, "Stout Bear")
        val state = table.state()
        val siegeObj = table.obj(siege)
        state.objects[siegeObj] = state.objects.getValue(siegeObj).copy(protector = PlayerId(bob),
            counters = listOf(CounterEntry(CounterKind.Defense, 3, 1)))
        val choices = CombatFlow.choices(run(state))
        assertEquals(listOf(null, siege), choices.attacks.getValue(bear), "the player and the Siege")
        val blocking = toBlockers(declaredAttackers(listOf(LaneAttack(bear, siege), LaneAttack(ogre, siege)), state))
        val blocks = CombatFlow.choices(run(blocking))
        assertEquals(bob, blocks.declaring, "the protector blocks for it")
        assertEquals(setOf(bear, ogre), blocks.blocks.getValue(bobBear).toSet())
        val declared = run(blocking).also { CombatFlow.declareBlockers(it, listOf(LaneBlock(bobBear, bear))) }.working
        assertEquals(3, assertNotNull(CombatFlow.preview(run(declared))).damage[siege])
        assertEquals(1, CombatStackRules.blocks(table.board(declared).combat).size)
    }
}
