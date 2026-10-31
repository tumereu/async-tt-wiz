package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class HandMenuTest {
    private val f = TableStatesFixture
    private fun handPlay(vararg types: String): Pair<PlayState, List<String>> {
        val root = f.initial
        val hand = root.board.containers.single { it.zone == PlayZone.HAND && it.accountId == f.a }
        val template = root.board.cards.single { it.id == hand.cards.first() }
        val cards = types.mapIndexed { index, type -> template.copy(id = f.id("hand-$index"), definition = "hand-definition-$index") }
        val definitions = types.mapIndexed { index, type -> SubmittedDefinition("hand-definition-$index",
            CardDefinition(type, listOf(DefinitionFace(template.faceId, type, typeLine = type)))) }
        return root.copy(board = root.board.copy(cards = root.board.cards.filterNot { it.id in hand.cards } + cards,
            containers = root.board.containers.map { if (it.id == hand.id) it.copy(cards = cards.map { c -> c.id }) else it }),
            definitions = root.definitions + definitions) to cards.map { it.id }
    }
    // TBL-090
    @Test fun playUsesDoubleTapLoweringAndOneAtomicCallForMixedMultiselection() {
        val (play, ids) = handPlay("Land", "Creature", "Instant")
        val singles = mutableListOf<PlayAction>()
        val batches = mutableListOf<List<PlayAction>>()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> singles += action; true },
            playBatch = { actions, _ -> batches += actions; true })
        assertTrue(toolkit.playHand(ids))
        assertTrue(singles.isEmpty())
        val actions = batches.single()
        val land = assertIs<PlayAction.MoveCard>(actions.first())
        assertEquals(ids[0], land.cardId)
        assertEquals(0, land.destination.position?.y)
        assertEquals(2, actions.filterIsInstance<PlayAction.Stack>().count { it.action is StackAction.AddEffect })
        assertTrue(actions.filterIsInstance<PlayAction.MoveCard>().any { it.cardId == ids[1] })
        assertTrue(actions.filterIsInstance<PlayAction.MoveCard>().none { it.cardId == ids[2] })
    }
    // SYS-036, SYS-037, TBL-072, ZON-010, ZON-011
    @Test fun handMenusServeBothPlayersAndRevealCapturesWholeSelectionWhileRandomUsesActualSelection() {
        val (play, ids) = handPlay("Instant", "Instant", "Instant")
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> sent += action; true })
        toolkit.openCard(TableScope(ids[0], ids.take(2)))
        val bound = assertNotNull(toolkit.capture)
        val controls = handMenuTools(toolkit, play, bound, TableSelection(ids = setOf(ids[1])), emptyMap(), true)
        controls.single { it.name == "Reveal" }.click()
        assertEquals(PlayAction.RevealHand(play.board.cards.single { it.id == ids[0] }.containerId, ids.take(2)), sent.single())
        controls.single { it.name == "Exile random" }.click()
        val random = assertIs<PlayAction.RandomHand>(sent.last())
        assertEquals(listOf(ids[1]), random.selectedIds)
        assertEquals(ids[1], random.cardId)
        controls.single { it.name == "Exile random" }.click()
        assertNotEquals(random.seed, assertIs<PlayAction.RandomHand>(sent.last()).seed)
        // Rules v30: the other player gets the very same Hand menu and acts blind, face down.
        val other = mutableListOf<PlayAction>()
        val opponent = TableToolkitController(f.b, { play }, { true }, { action, _ -> other += action; true })
        opponent.openCard(TableScope(ids[0], listOf(ids[0])))
        val theirs = assertNotNull(opponent.capture)
        val menu = handMenuTools(opponent, play, theirs, TableSelection(), emptyMap(), true)
        assertEquals(controls.map { it.name }, menu.map { it.name })
        for (name in listOf("Play", "Face down", "Reveal", "Discard", "Exile", "Discard random", "Reveal random"))
            assertTrue(menu.single { it.name == name }.enabled, name)
        menu.single { it.name == "Discard random" }.click()
        val discard = assertIs<PlayAction.RandomHand>(other.single())
        assertEquals(HandRandomResult.DISCARD, discard.result)
        val hand = play.board.cards.single { it.id == ids[0] }.containerId
        val changed = GameplayRules.change(play, f.b, GameCommand(f.id("opponent-random"), GameScope(play.gameId, playId = play.id),
            GameAction.Play(discard), f.id("opponent-invocation"), "1"))
        assertEquals("accepted", changed.outcome, changed.reason)
        assertEquals(play.board.zoneContainer(f.a, PlayZone.GRAVEYARD)!!.id,
            changed.state!!.board.cards.single { it.id == discard.cardId }.containerId)
        assertTrue(play.board.cards.filter { it.containerId == hand }.none { CardVisibility.isVisibleFaceUp(play.board, it, f.b) })
    }
    // STK-005, SYS-010, TBL-090, ZON-008
    @Test fun faceDownCastsAndAutoResolvesTheWholeSelectionInOneBatchWithoutProjections() {
        val (play, ids) = handPlay("Creature", "Instant")
        val singles = mutableListOf<PlayAction>()
        val batches = mutableListOf<List<PlayAction>>()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> singles += action; true },
            playBatch = { actions, _ -> batches += actions; true })
        toolkit.openCard(TableScope(ids[0], ids))
        val bound = assertNotNull(toolkit.capture)
        handMenuTools(toolkit, play, bound, TableSelection(ids = ids.toSet()), emptyMap(), true).single { it.name == "Face down" }.click()
        assertTrue(singles.isEmpty())
        val moves = batches.single().map { assertIs<PlayAction.MoveCard>(it) }
        // Each card: Hand -> Stack face down, then the manual resolver Stack -> Battlefield, whatever its printed type.
        assertEquals(ids.flatMap { listOf(it, it) }, moves.map { it.cardId })
        val stack = CombatStackRules.stack(play.board).id
        val field = play.board.zoneContainer(f.a, PlayZone.BATTLEFIELD)!!.id
        for ((cast, resolve) in moves.chunked(2)) {
            assertTrue(cast.faceDown == true && cast.destination.containerId == stack && cast.removeStackEntryId == null)
            assertEquals(stack, resolve.source.containerId)
            assertEquals(field, resolve.destination.containerId)
            assertEquals(1, resolve.destination.position?.y)
        }
        // The batch applies in order and nothing is disclosed to the opponent.
        var state = play
        for ((index, move) in moves.withIndex()) {
            val change = GameplayRules.change(state, f.a, GameCommand(f.id("face-down-$index"),
                GameScope(play.gameId, playId = play.id), GameAction.Play(move), f.id("invocation"), "1"))
            assertNotNull(change.state, change.reason)
            assertTrue(change.mutation!!.transition.disclosures.isEmpty())
            state = change.state!!
        }
        assertTrue(ids.all { id -> state.board.cards.single { it.id == id }.let {
            it.isFaceDown && it.containerId == field && it.corner == "2/2" && !CardVisibility.isVisibleFaceUp(state.board, it, f.b) } })
        assertTrue(CombatStackRules.stack(state.board).cards.isEmpty())
        assertTrue(state.board.stackEffects.isEmpty())
    }
    // ZON-018
    @Test fun manifestTopSendsOneFaceDownMoveFromTheLibraryTopToTheBattlefield() {
        val play = f.initial
        val library = play.board.zoneContainer(f.a, PlayZone.LIBRARY)!!
        val field = play.board.zoneContainer(f.a, PlayZone.BATTLEFIELD)!!
        val sent = mutableListOf<PlayAction>()
        val controller = LibraryController(play.id, library.id, play.board.zoneContainer(f.a, PlayZone.HAND)!!.id,
            { play }, { true }, { sent += it; true })
        assertEquals(LibrarySubmission(1, 0), controller.manifestTop())
        val move = assertIs<PlayAction.MoveCard>(sent.single())
        assertEquals(library.cards.first(), move.cardId)
        assertEquals(MoveSourceEdge.TOP, move.source.edge)
        assertTrue(move.source.takeFromEdge)
        assertEquals(true, move.faceDown)
        assertEquals(field.id, move.destination.containerId)
        val change = GameplayRules.change(play, f.a, GameCommand(f.id("manifest"), GameScope(play.gameId, playId = play.id),
            GameAction.Play(move), f.id("invocation"), "1"))
        val card = change.state!!.board.cards.single { it.id == move.cardId }
        assertTrue(card.isFaceDown)
        assertEquals("2/2", card.corner)
        assertTrue(change.mutation!!.transition.disclosures.isEmpty())
    }
    @Test fun revealDoneSubmitsOneDismissNotALibraryRestOperation() {
        val (play, ids) = handPlay("Instant", "Instant")
        val hand = play.board.cards.single { it.id == ids[0] }.containerId
        val display = ContentsDisplay(f.id("reveal"), f.a, hand, ContentsSelection.ALL, 2, true, ids, panelVersion = 4)
        val revealed = play.copy(board = play.board.copy(contentsDisplays = listOf(display)))
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(f.a, { revealed }, { true }, { action, _ -> sent += action; true })
        toolkit.closeCardPanel(display)
        assertEquals(listOf<PlayAction>(PlayAction.DismissContents(display.id)), sent)
        assertNull(toolkit.panel)
    }
    // ZON-067
    @Test fun opponentMayPressDoneOnAForgottenHandReveal() {
        val (play, ids) = handPlay("Instant", "Instant")
        val hand = play.board.cards.single { it.id == ids[0] }.containerId
        val display = ContentsDisplay(f.id("reveal"), f.a, hand, ContentsSelection.ALL, 2, true, ids, panelVersion = 4)
        val revealed = play.copy(board = play.board.copy(contentsDisplays = listOf(display)))
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(f.b, { revealed }, { true }, { action, _ -> sent += action; true })
        toolkit.closeCardPanel(display)
        assertEquals(listOf<PlayAction>(PlayAction.DismissContents(display.id)), sent)
        val change = GameplayRules.change(revealed, f.b, GameCommand(f.id("done"), GameScope(revealed.gameId, playId = revealed.id),
            GameAction.Play(sent.single()), f.id("invocation"), "1"))
        assertEquals("accepted", change.outcome, change.reason)
        assertTrue(change.state!!.board.contentsDisplays.isEmpty())
    }
}
