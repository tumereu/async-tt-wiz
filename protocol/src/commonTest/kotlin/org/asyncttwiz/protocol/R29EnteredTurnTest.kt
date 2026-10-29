package org.asyncttwiz.protocol

import kotlinx.serialization.json.*
import kotlin.test.*

/** Rules v29: cards store the turn number in which they entered the Battlefield. */
class R29EnteredTurnTest {
    private val a = gameplayId(7)
    private val b = gameplayId(8)
    private val library = gameplayId(20)
    private val hand = gameplayId(21)
    private val field = gameplayId(22)
    private val graveyard = gameplayId(23)
    private val exile = gameplayId(24)
    private val stack = gameplayId(25)
    private val otherField = gameplayId(26)

    private fun accepted(state: PlayState, action: PlayAction, n: Int, actor: Int = 7): GameplayChange {
        val change = gameplayChange(state, action, n, actor)
        assertNotNull(change.state, change.reason)
        val event = assertNotNull(change.mutation)
        assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, event.transition.rulesVersion)
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(event))
        assertEquals(change.state, GameplayHistory.apply(state, decoded), "recorded replay of $action")
        assertEquals(state, GameplayHistory.unapply(change.state, decoded), "exact inverse of $action")
        return change
    }

    private fun card(state: PlayState, n: Int) = state.board.cards.single { it.id == gameplayId(n) }
    private fun marked(state: PlayState, n: Int) = EnteredTurn.marked(state.board, card(state, n))

    /** Turn 3; cards 10..13 sit in Hand, Graveyard, Exile and Stack, 14 stays in the Library; B has a Battlefield. */
    private fun start(): PlayState {
        val fixture = gameplayFixture()
        val places = mapOf(10 to hand, 11 to graveyard, 12 to exile, 13 to stack).mapKeys { gameplayId(it.key) }
        val board = fixture.board
        return fixture.copy(board = board.copy(turn = board.turn.copy(number = "3"),
            cards = board.cards.map { places[it.id]?.let { target -> it.copy(containerId = target) } ?: it },
            containers = board.containers.map { container ->
                container.copy(cards = container.cards.filterNot { it in places } + places.filterValues { it == container.id }.keys)
            } + PlayContainer(otherField, PlayZone.BATTLEFIELD, b)))
    }

    private fun onField(state: PlayState, n: Int, enteredTurn: String, target: String = field): PlayState {
        val id = gameplayId(n)
        val board = state.board
        return state.copy(board = board.copy(cards = board.cards.map {
            if (it.id == id) it.copy(containerId = target, covered = false, placement = PlayPlacement(BoardPosition(n, 5), id),
                enteredTurn = enteredTurn) else it
        }, containers = board.containers.map { container ->
            container.copy(cards = container.cards.filterNot { it == id } + if (container.id == target) listOf(id) else emptyList())
        }))
    }

    private fun play(n: Int, from: String, x: Int, target: String = field, source: MoveSource = MoveSource(from)) =
        PlayAction.MoveCard(gameplayId(n), source, MoveDestination(target, MoveInsertion.EMPTY, BoardPosition(x, 0)))

    private fun fieldMove(state: PlayState, n: Int, destination: MoveDestination) =
        PlayAction.MoveCard(gameplayId(n), MoveSource(card(state, n).containerId, card(state, n).placement), destination)

    @Test fun enteringFromEveryOtherContainerStoresTheTurnNumber() {
        var state = start()
        listOf(10 to hand, 11 to graveyard, 12 to exile, 13 to stack).forEachIndexed { i, (n, from) ->
            assertEquals("", card(state, n).enteredTurn)
            val change = accepted(state, play(n, from, i), 100 + i)
            state = change.state!!
            assertEquals("3", card(state, n).enteredTurn, "from $from")
            assertTrue(marked(state, n))
            val concerns = change.mutation!!.transition.concerns!!.fields.map { it.field }
            assertTrue(ConcernField.Entity("card", gameplayId(n), listOf("enteredTurn")) in concerns)
            assertTrue(ConcernField.Entity("turn", "", listOf("number")) in concerns)
        }
        // Library top, and via a Temp pile.
        state = accepted(state, play(14, library, 7, source = MoveSource(library, edge = MoveSourceEdge.TOP)), 110).state!!
        assertTrue(marked(state, 14))
        val temp = TempPiles.containerId(state.id, a)
        state = accepted(state, fieldMove(state, 14, MoveDestination(temp, MoveInsertion.TOP)), 111).state!!
        assertEquals("", card(state, 14).enteredTurn)
        state = accepted(state, play(14, temp, 8, source = MoveSource(temp, edge = MoveSourceEdge.TOP)), 112).state!!
        assertTrue(marked(state, 14))
    }

    @Test fun tokensAndClonesAreCreatedEnteredThisTurn() {
        val state = onField(start(), 10, "2")
        val token = accepted(state, PlayAction.CreateToken(TokenCatalog.blank), 100)
        val created = token.state!!.board.cards.single { it.token }
        assertEquals("3", created.enteredTurn)
        assertEquals(created, token.mutation!!.transition.tokens.single().to!!.card)
        val clone = accepted(state, PlayAction.CloneToken(gameplayId(10)), 101).state!!.board.cards.single { it.token }
        assertEquals("3", clone.enteredTurn)
        assertEquals("2", card(state, 10).enteredTurn)
    }

    @Test fun movingToTheOtherPlayersBattlefieldEntersButRearrangingDoesNot() {
        val state = onField(start(), 10, "2")
        val moved = accepted(state, fieldMove(state, 10, MoveDestination(field, MoveInsertion.EMPTY, BoardPosition(9, 9))), 100)
        assertEquals("2", card(moved.state!!, 10).enteredTurn)
        assertFalse(marked(moved.state, 10))
        assertTrue(moved.mutation!!.transition.concerns!!.fields.none {
            it.field == ConcernField.Entity("turn", "", listOf("number")) || it.field == ConcernField.Entity("card", gameplayId(10), listOf("enteredTurn"))
        })
        val tapped = accepted(state, PlayAction.SetTapped(gameplayId(10), true), 101).state!!
        assertEquals("2", card(tapped, 10).enteredTurn)
        val flipped = accepted(state, PlayAction.SetCovered(gameplayId(10), true), 102).state!!
        assertEquals("2", card(flipped, 10).enteredTurn)
        val stolen = accepted(state, fieldMove(state, 10, MoveDestination(otherField, MoveInsertion.EMPTY, BoardPosition(0, 0))), 103).state!!
        assertEquals("3", card(stolen, 10).enteredTurn)
        assertTrue(marked(stolen, 10))
    }

    @Test fun leavingClearsAndTurnChangeLapses() {
        val state = onField(start(), 10, "3")
        assertTrue(marked(state, 10))
        for ((i, target) in listOf(graveyard, hand, exile).withIndex()) {
            val left = accepted(state, fieldMove(state, 10, MoveDestination(target, MoveInsertion.UNORDERED)), 100 + i).state!!
            assertEquals("", card(left, 10).enteredTurn, "to $target")
            assertFalse(marked(left, 10))
        }
        val next = accepted(state, PlayAction.EndTurn, 110).state!!
        assertEquals("4", next.board.turn.number)
        assertEquals("3", card(next, 10).enteredTurn)
        assertFalse(marked(next, 10))
        assertEquals(state.board.cards, next.board.cards)
    }

    @Test fun undoingAnEarlierTurnPassIsBlockedByALaterEntryButNotByOtherMoves() {
        val s0 = onField(start(), 11, "1")
        val s1 = accepted(s0, PlayAction.EndTurn, 100).state!!
        val entry = accepted(s1, play(10, hand, 0), 101).mutation!!
        assertEquals("replayBlocked", GameplayHistory.replay(s0, entry).reason)
        val discard = accepted(s1, PlayAction.MoveCard(gameplayId(10), MoveSource(hand),
            MoveDestination(graveyard, MoveInsertion.UNORDERED)), 102).mutation!!
        assertNotNull(GameplayHistory.replay(s0, discard).state)
        val rearrange = accepted(s1, fieldMove(s1, 11, MoveDestination(field, MoveInsertion.EMPTY, BoardPosition(9, 9))), 103).mutation!!
        assertNotNull(GameplayHistory.replay(s0, rearrange).state)
    }

    @Test fun rulesVersion28LeavesTheFieldUntouched() {
        val state = start()
        val action = play(10, hand, 0)
        val command = GameCommand(gameplayId(100), GameScope(state.gameId, playId = state.id), GameAction.Play(action), gameplayId(80), "1")
        val old = GameplayRules.move(state, a, command, action, rulesVersion = 28)
        assertEquals("", card(old.state!!, 10).enteredTurn)
        assertEquals(28, old.mutation!!.transition.rulesVersion)
        assertTrue(old.mutation.transition.concerns == null ||
            old.mutation.transition.concerns.fields.none { it.field == ConcernField.Entity("turn", "", listOf("number")) })
    }

    @Test fun defaultIsOmittedFromTheWire() {
        val encoded = GameWire.json.encodeToJsonElement(card(start(), 10)).jsonObject
        assertFalse("enteredTurn" in encoded)
        val stored = card(onField(start(), 10, "3"), 10)
        assertEquals("3", GameWire.json.encodeToJsonElement(stored).jsonObject["enteredTurn"]!!.jsonPrimitive.content)
        assertEquals(stored, GameWire.json.decodeFromJsonElement<PlayCard>(GameWire.json.encodeToJsonElement(stored)))
    }
}
