package org.asyncttwiz.automatum

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.PlayZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the table draws of a game's own engine state as the game goes on ([EngineProjection]): cards leaving the
 * Battlefield, tokens, damage, the graveyard's order, spells on the Stack, emblems and combat copies. */
class EngineBoardTest {
    init {
        Cards.invent("Stout Bear", "{1}{G}", "Creature — Bear", "", "2/2")
        Cards.invent("Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    }
    private val table = EngineTable()
    private val alice get() = table.alice
    private val bob get() = table.bob

    private fun run(state: GameState) = EngineRun.of(state, table.board(state), "game", table::seed)

    /** Every player passes in turn until [done] holds of the state. */
    private fun passUntil(start: GameState, done: (GameState) -> Boolean): GameState {
        var state = start
        repeat(80) {
            if (done(state)) return state
            val holder = assertNotNull(state.priority.holder, "nobody holds priority at ${state.turn.step}")
            state = run(state).also { it.perform(Input.PassPriority(holder)) }.working
        }
        error("never reached")
    }

    /** [player] casts the targeted spell [card] at [target], paid with the suggestion: the state after the cast alone. */
    private fun cast(state: GameState, player: String, card: String, target: TargetRef): GameState {
        var flow = CastFlow.start(table.source(state), player, HandPlays.of(state, player, null).of(card).first(), CastFlow.Seeds(table::seed))
        assertIs<Decision.Targets>(flow.pending)
        flow = flow.answer(Answer.Targets(listOf(target)))
        return flow.sending(assertIs<Decision.ManaPayment>(flow.pending).suggestion).cast.working
    }

    private fun resolved(state: GameState) = passUntil(state) { it.stack.isEmpty() && it.pending == null }
    private fun at(card: String) = TargetRef.Object(table.obj(card))
    private fun zoneOf(board: BoardState, card: String) =
        board.cards.singleOrNull { it.id == card }?.let { c -> board.containers.single { it.id == c.containerId }.zone }

    // TBL-093, ZON-071, ZON-030: a creature leaving the Battlefield untaps and loses its counters and damage; the
    // graveyard shows its newest card on top; ZON-074: a hand card cast is shown to both players while on the Stack
    @Test fun aCreatureThatDiesLeavesItsStateBehind() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (spark, second) = table.put(alice, Zone.Hand, "Storm Spark", "Storm Spark")
        val (bear, ogre) = table.put(bob, Zone.Battlefield, "Stout Bear", "Hill Ogre", tapped = true)
        table.put(alice, Zone.Library, "Island", "Island")
        table.put(bob, Zone.Library, "Island", "Island")
        val start = table.state()
        start.objects[table.obj(bear)] = start.objects.getValue(table.obj(bear)).copy(
            counters = listOf(CounterEntry(CounterKind.PowerToughness(1, 1), 1, 1)), damage = 1)
        val casting = cast(start, alice, spark, at(bear))
        val shownToBob = table.board(casting, viewer = bob).cards.single { it.id == spark }
        assertFalse(shownToBob.covered, "the spell's card on the Stack is shown to the other player")
        val first = resolved(casting)
        val board = table.board(first)
        assertEquals(PlayZone.GRAVEYARD, zoneOf(board, bear))
        val dead = board.cards.single { it.id == bear }
        assertFalse(dead.tapped)
        assertTrue(dead.typedCounters.isEmpty())
        assertEquals("", dead.damage)
        val both = table.board(resolved(cast(first, alice, second, at(ogre))))
        assertEquals(listOf(ogre, bear), both.containers.single { it.zone == PlayZone.GRAVEYARD && it.accountId == bob }.cards
            .filter { it == ogre || it == bear }, "the newest card on top")
    }

    // TBL-103: a token that leaves the Battlefield stops existing; TBL-102: tokens appear in their normal row (land
    // tokens in the land row, others in the main row)
    @Test fun tokensStartInTheirRowAndStopExistingWhenTheyLeave() {
        table.put(alice, Zone.Battlefield, "Mountain")
        val (spark) = table.put(alice, Zone.Hand, "Storm Spark")
        val (soldier, land) = table.put(bob, Zone.Battlefield, "Stout Bear", "Mountain")
        val start = table.state()
        for (token in listOf(soldier, land)) start.objects[table.obj(token)] = start.objects.getValue(table.obj(token)).copy(token = true)
        val board = table.board(start)
        assertEquals(1, board.cards.single { it.id == soldier }.placement!!.position.y)
        assertEquals(0, board.cards.single { it.id == land }.placement!!.position.y)
        val after = table.board(resolved(cast(start, alice, spark, at(soldier))))
        assertNull(zoneOf(after, soldier), "the token is gone")
    }

    // TBL-095, TRN-003: damage on a creature clears when the turn passes
    @Test fun damageClearsWhenTheTurnPasses() {
        val (ogre) = table.put(bob, Zone.Battlefield, "Hill Ogre")
        table.put(alice, Zone.Library, "Island", "Island")
        table.put(bob, Zone.Library, "Island", "Island")
        val start = table.state()
        start.objects[table.obj(ogre)] = start.objects.getValue(table.obj(ogre)).copy(damage = 2)
        assertEquals("2", table.board(start).cards.single { it.id == ogre }.damage)
        val next = passUntil(start) { it.turn.number == start.turn.number + 1 && it.priority.holder != null }
        assertEquals("", table.board(next).cards.single { it.id == ogre }.damage)
    }

    // SYS-066: an emblem in a player's command zone is kept with whose it is and its text, for its chip (TRN-053)
    @Test fun anEmblemIsShownWithItsPlayerAndText() {
        val anthem = Cards.invent("Banner of the Pack", "{2}{G}", "Enchantment", "Creatures you control get +1/+1.")
        val (emblem) = table.put(alice, Zone.Command, anthem)
        val record = table.board().engineRecords.single { it.kind == EngineRecordKind.Emblem }
        assertEquals(alice, record.player)
        assertTrue("+1/+1" in record.text, record.text)
        assertTrue(emblem in record.id)
    }

    // CMB-002, CMB-006: attackers are combat copies of the real cards, which stay on the Battlefield; when the real card
    // leaves the Battlefield its copy disappears and the other attacker's pair stays
    @Test fun combatCopiesFollowTheirRealCards() {
        table.put(alice, Zone.Battlefield, "Mountain")
        val (spark) = table.put(alice, Zone.Hand, "Storm Spark")
        val (bear, ogre) = table.put(alice, Zone.Battlefield, "Stout Bear", "Hill Ogre")
        table.step = TurnStep.DeclareAttackers
        table.priority = null
        val declared = run(table.state()).also {
            it.perform(Input.DeclareAttackers(PlayerId(alice), listOf(Attack(table.obj(bear), TargetRef.Player(PlayerId(bob))),
                Attack(table.obj(ogre), TargetRef.Player(PlayerId(bob))))))
        }.working
        val board = table.board(declared)
        assertEquals(setOf(bear, ogre), board.combat!!.pairs.flatMap { p -> p.attackers.map { it.sourceId } }.toSet())
        assertEquals(PlayZone.BATTLEFIELD, zoneOf(board, bear), "the real card stays where it is")
        val after = table.board(resolved(cast(declared, alice, spark, at(bear))))
        assertEquals(listOf(ogre), after.combat!!.pairs.flatMap { p -> p.attackers.map { it.sourceId } })
    }
}
