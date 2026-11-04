package org.asyncttwiz.automatum

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.PlayerStatus
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LineRandom
import org.asyncttwiz.protocol.game.Placement
import org.asyncttwiz.protocol.game.TableChange
import org.asyncttwiz.protocol.game.TableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * I-D56 step C: the table's board made from the engine's state ([EngineProjection]): the play's own containers and card
 * ids, hidden cards covered for the viewer, the Stack's entries pointing at the spell's card (STK-001), places from the
 * table state, and the engine's state carried by the board for every engine reader ([EngineState.from]).
 */
class EngineProjectionTest {
    private val table = AtwTable("projection").apply {
        put(alice, PlayZone.LIBRARY, *Array(20) { if (it % 2 == 0) "Mountain" else "Storm Spark" })
        put(bob, PlayZone.LIBRARY, *Array(20) { "Forest" })
    }
    private val base: PlayState = table.state()
    private val alice = PlayerId(table.alice)
    private val bob = PlayerId(table.bob)

    private fun model(): GameModel {
        val seats = listOf(alice, bob).map { p ->
            Seat(p, base.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == p.value }.cards.map { id ->
                DeckCard(CardId(id), table.definitions[base.board.cards.single { it.id == id }.definition])
            })
        }
        return GameModel(Engine.newGame(seats), TableState(secret = LineRandom.secret(ByteArray(32) { 7 })))
    }

    private var events = 0
    private fun GameModel.act(input: Input, table: List<TableChange> = emptyList()) {
        val builder = EventBuilder(this, "e${++events}", "p", "x")
        builder.perform(input)
        builder.answerRandoms()
        table.forEach(builder::table)
        builder.build()
    }

    /** Started (Alice first), both keep, and passed on to Alice's first main phase. */
    private fun started(): GameModel = model().apply {
        act(Input.StartGame(StartOptions(startingPlayer = alice)))
        while (engine.turn.number == 0) {
            val d = engine.pending!!.decision
            val keep = Engine.options(engine, d.player).filterIsInstance<Input.Answer>()
                .first { it.decision == d.id && (it.answer == Answer.Option("Keep") || d !is Decision.ChooseOption) }
            act(keep)
        }
        while (!(engine.turn.step == TurnStep.PrecombatMain && engine.priority.holder == alice && engine.turn.active == alice))
            act(Input.PassPriority(engine.priority.holder!!))
    }

    private fun named(model: GameModel, player: PlayerId, name: String): ObjectId =
        model.engine.player(player).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    @Test
    fun theOpeningShowsBothPlayersMulliganing() {
        val m = model().apply { act(Input.StartGame(StartOptions(startingPlayer = alice))) }
        val board = EngineProjection.project(base, m, table.alice, "e1").board
        assertTrue(board.players.all { it.status == PlayerStatus.OPENING })
        // Each hand holds seven of the player's own cards, the other player's covered for the viewer
        val bobsHand = board.containers.single { it.zone == PlayZone.HAND && it.accountId == table.bob }
        assertEquals(7, bobsHand.cards.size)
        assertTrue(bobsHand.cards.all { id -> board.cards.single { it.id == id }.covered })
    }

    @Test
    fun cardsKeepTheirIdsAndContainersAndTheBoardIsPlainData() {
        val m = started()
        val play = EngineProjection.project(base, m, table.alice, "e9")
        val board = play.board
        val hand = board.containers.single { it.zone == PlayZone.HAND && it.accountId == table.alice }
        assertEquals(m.engine.player(alice).hand.map { (it as ObjectId.Card).card.value }, hand.cards)
        assertTrue(hand.cards.all { id -> base.board.cards.any { it.id == id } }, "the prepared cards' own ids")
        val library = board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == table.alice }
        assertEquals(m.engine.player(alice).library.map { (it as ObjectId.Card).card.value }, library.cards, "top first")
        assertTrue(board.players.none { it.status == PlayerStatus.OPENING })
        assertEquals(table.alice, board.turn.accountId)
        // Engine-responsiveness R4a: the board is the plain data the table draws; the engine state stays beside it
        assertNull(board.engine)
        assertTrue(play.definitions.map { it.digest }.containsAll(board.cards.map { it.definition }.toSet()), "every card's data")
    }

    @Test
    fun aLandTakesItsPlaceAndASpellPointsAtItsCard() {
        val m = started()
        val land = named(m, alice, "Mountain")
        m.act(Input.PlayLand(alice, land), listOf(TableChange.Placed(land.let { ObjectId.Card((it as ObjectId.Card).card, it.zoneChanges + 1) }, null, Placement(3, 0))))
        var board = EngineProjection.project(base, m, table.alice, "e20").board
        val landCard = board.cards.single { it.id == (land as ObjectId.Card).card.value }
        val battlefield = board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == table.alice }
        assertEquals(battlefield.id, landCard.containerId)
        assertEquals(3, landCard.placement?.position?.x)
        assertEquals(0, landCard.placement?.position?.y)
        assertFalse(landCard.covered)
        // Storm Spark at Bob: its card stays in the hand with an entry on the Stack (STK-001)
        val spark = named(m, alice, "Storm Spark")
        m.act(Input.Cast(alice, spark))
        while (m.engine.pending != null) {
            val d = m.engine.pending!!.decision
            val answers = Engine.options(m.engine, d.player).filterIsInstance<Input.Answer>().filter { it.decision == d.id }
            m.act(if (d is Decision.Targets) answers.first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(bob)) } else answers.first())
        }
        board = EngineProjection.project(base, m, table.alice, "e21").board
        val entry = board.stackEffects.single()
        assertEquals((spark as ObjectId.Card).card.value, entry.sourceId)
        assertTrue(entry.item?.id is MtgRef.Card)
        val stack = board.containers.single { it.zone == PlayZone.STACK }
        assertEquals(listOf(entry.id), stack.cards)
        val hand = board.containers.single { it.zone == PlayZone.HAND && it.accountId == table.alice }
        assertTrue(spark.card.value in hand.cards, "the spell's card stays in the hand underneath")
        assertTrue(board.players.single { it.accountId == table.alice }.manaPool.isEmpty())
        assertEquals(table.alice, board.priority?.holder)
    }
}
