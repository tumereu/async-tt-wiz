package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v30: cards put on a searched Library's top/bottom wait and are placed after the close shuffle. */
class R30SearchQueueTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private val libraryId = id(20)
    private val handId = id(21)
    private val graveyardId = id(22)
    private val secret = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private fun seed(n: Int) = RandomV1.encodeSeed(ByteArray(32) { (n + it).toByte() })
    private var request = 30000

    /** a: Library 1000..1009, Graveyard 1100..1104. b: Library 2000..2004. */
    private fun root(): PlayState {
        val definition = CardDefinition("Queue fixture", listOf(DefinitionFace(id(40), "Front", corner = "1/1")))
        val digest = DeckJson.digest(definition)
        val libraryA = (1000..1009).map(::id); val graveyard = (1100..1104).map(::id); val libraryB = (2000..2004).map(::id)
        val cards = libraryA.map { PlayCard(it, digest, a, libraryId, id(40)) } +
            graveyard.map { PlayCard(it, digest, a, graveyardId, id(40), covered = false) } +
            libraryB.map { PlayCard(it, digest, b, id(30), id(40)) }
        val containers = listOf(
            PlayContainer(libraryId, PlayZone.LIBRARY, a, libraryA, layers = LibraryLayers.initial(id(60), libraryA)),
            PlayContainer(handId, PlayZone.HAND, a), PlayContainer(graveyardId, PlayZone.GRAVEYARD, a, graveyard.reversed()),
            PlayContainer(id(23), PlayZone.BATTLEFIELD, a), PlayContainer(id(24), PlayZone.EXILE, a),
            PlayContainer(id(30), PlayZone.LIBRARY, b, libraryB, layers = LibraryLayers.initial(id(61), libraryB)),
            PlayContainer(id(31), PlayZone.HAND, b), PlayContainer(id(32), PlayZone.GRAVEYARD, b),
            PlayContainer(id(33), PlayZone.BATTLEFIELD, b), PlayContainer(id(34), PlayZone.EXILE, b),
            PlayContainer(id(35), PlayZone.STACK))
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), cards.sortedBy { it.id }, containers, TurnState(a)),
            listOf(SubmittedDefinition(digest, definition)), emptyList(), InitialStarter(StarterChoice(a), a), librarySecret = secret)
    }

    private fun command(state: PlayState, action: PlayAction) = GameCommand(id(request++),
        GameScope(state.gameId, playId = state.id), GameAction.Play(action), id(9), "1")
    /** Accepted, exactly applicable, reversible and replayable from its recorded event. */
    private fun exact(state: PlayState, action: PlayAction, actor: String = a): PlayState {
        val change = GameplayRules.change(state, actor, command(state, action))
        val mutation = assertNotNull(change.mutation, "$action: ${change.reason ?: change.outcome}")
        val next = assertNotNull(change.state)
        assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, mutation.transition.rulesVersion)
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(mutation))
        assertEquals(next, GameplayHistory.apply(state, decoded))
        assertEquals(state, GameplayHistory.unapply(next, decoded))
        assertEquals(next, GameplayHistory.replay(state, decoded).state)
        next.board.containers.forEach { assertTrue(LibraryLayers.valid(it), "$it") }
        return next
    }
    private fun library(state: PlayState) = state.board.containers.single { it.id == libraryId }
    private fun shape(state: PlayState) = library(state).layers.map { (if (it.ordered) "O" else "U") + it.size }
    private fun display(state: PlayState) = state.board.contentsDisplays.single()
    private fun move(state: PlayState, card: Int, insertion: MoveInsertion, to: String = libraryId): PlayAction.MoveCard {
        val c = state.board.cards.single { it.id == id(card) }
        return PlayAction.MoveCard(c.id, MoveSource(c.containerId, c.placement), MoveDestination(to, insertion))
    }
    private fun search(state: PlayState = root(), actor: String = a) =
        exact(state, PlayAction.ShowContents(libraryId, ContentsSelection.ALL), actor)
    private fun close(state: PlayState, seed: Int = 3, actor: String = a) =
        exact(state, PlayAction.DismissContents(display(state).id, ContentsRest.SHUFFLE, seed(seed)), actor)

    @Test fun topAndBottomPutsFromThePanelAndFromElsewhereWaitAndArePlacedAfterTheShuffle() {
        var state = search()
        // From the panel itself (a), from the Graveyard (b, the other player), then onto the bottom.
        state = exact(state, move(state, 1003, MoveInsertion.TOP))
        state = exact(state, move(state, 1100, MoveInsertion.TOP), actor = b)
        state = exact(state, move(state, 1007, MoveInsertion.BOTTOM))
        state = exact(state, move(state, 1101, MoveInsertion.BOTTOM))
        val waiting = display(state)
        assertEquals(listOf(listOf(id(1003)), listOf(id(1100))), waiting.queuedTop)
        assertEquals(listOf(listOf(id(1007)), listOf(id(1101))), waiting.queuedBottom)
        assertEquals(2, SearchQueue.topCount(waiting)); assertEquals(2, SearchQueue.bottomCount(waiting))
        // Waiting cards leave the panel list; ones from elsewhere never join it.
        assertEquals((1000..1009).map(::id) - id(1003) - id(1007), waiting.cardIds)
        val closed = close(state)
        assertTrue(closed.board.contentsDisplays.isEmpty())
        val rest = (library(state).cards - setOf(id(1003), id(1100), id(1007), id(1101)))
        // The last one put on top ends on top; the last one put on the bottom ends at the bottom.
        assertEquals(listOf(id(1100), id(1003)) + RandomV1.shuffle(rest, seed(3)) + listOf(id(1007), id(1101)), library(closed).cards)
        assertEquals(listOf("O2", "U8", "O2"), shape(closed))
    }

    @Test fun aBlockOfCardsPutOnTopKeepsItsOrderAsItWouldAfterTheShuffle() {
        var state = search()
        for (move in LibraryActions.moveCaptured(state.board, listOf(1102, 1101, 1100).map(::id), MoveDestination(libraryId, MoveInsertion.TOP)))
            state = exact(state, move)
        assertEquals(listOf(1102, 1101, 1100).map(::id), library(state).cards.take(3))
        assertEquals(3, SearchQueue.topCount(display(state)))
        val closed = close(state)
        assertEquals(listOf(1102, 1101, 1100).map(::id), library(closed).cards.take(3))
        assertEquals(listOf("O3", "U10"), shape(closed))
    }

    @Test fun aRandomOrderPutWaitsAsOneHiddenGroup() {
        var state = search()
        state = exact(state, PlayAction.MoveToLibraryRandom(libraryId, listOf(1100, 1101, 1102).map(::id), false, seed(5)))
        state = exact(state, move(state, 1103, MoveInsertion.TOP))
        state = exact(state, PlayAction.MoveToLibraryRandom(libraryId, listOf(1104, 1000).map(::id), true, seed(6)))
        val waiting = display(state)
        assertEquals(listOf(listOf(1100, 1101, 1102).map(::id), listOf(id(1103))), waiting.queuedTop)
        assertEquals(listOf(listOf(1000, 1104).map(::id)), waiting.queuedBottom)
        assertFalse(id(1000) in waiting.cardIds)
        val group = library(state).cards.subList(1, 4)
        val bottom = library(state).cards.takeLast(2)
        assertEquals(RandomV1.shuffle(listOf(1100, 1101, 1102).map(::id), seed(5)), group)
        val closed = close(state)
        val rest = library(state).cards - (group + id(1103) + bottom).toSet()
        assertEquals(listOf(id(1103)) + group + RandomV1.shuffle(rest, seed(3)) + bottom, library(closed).cards)
        // The single card's place is known; each random group stays a hidden layer of its own.
        assertEquals(listOf("O1", "U3", "U9", "U2"), shape(closed))
    }

    @Test fun cardsThatLeaveStopWaitingAndAShuffleOrAPlacedPositionJoinsTheShuffle() {
        var state = search()
        state = exact(state, move(state, 1100, MoveInsertion.TOP))
        state = exact(state, move(state, 1101, MoveInsertion.TOP))
        // b draws the top card: it no longer waits.
        state = exact(state, LibraryActions.draw(state.board, libraryId, handId, 1).single(), actor = b)
        assertEquals(listOf(listOf(id(1100))), display(state).queuedTop)
        // A waiting top card put on the bottom now waits there.
        state = exact(state, move(state, 1100, MoveInsertion.BOTTOM))
        assertEquals(emptyList(), display(state).queuedTop)
        assertEquals(listOf(listOf(id(1100))), display(state).queuedBottom)
        // A shuffle of the searched Library takes every waiting card into it.
        state = exact(state, move(state, 1102, MoveInsertion.TOP))
        state = exact(state, PlayAction.ShuffleLibrary(libraryId, seed(7)))
        assertTrue(display(state).queuedTop.isEmpty() && display(state).queuedBottom.isEmpty())
        val before = library(state).cards
        val closed = close(state)
        assertEquals(RandomV1.shuffle(before, seed(3)), library(closed).cards)
        assertEquals(listOf("U12"), shape(closed))
    }

    @Test fun otherLibrariesAndOtherPanelsKeepTheirOrdinaryBehaviour() {
        // A put on the other player's Library does not wait for this search.
        var state = search()
        state = exact(state, PlayAction.MoveCard(id(1100), MoveSource(graveyardId), MoveDestination(id(30), MoveInsertion.TOP)))
        assertTrue(display(state).queuedTop.isEmpty())
        // A Look panel never queues; its close keeps the chosen rest.
        var look = exact(root(), PlayAction.ShowContents(libraryId, ContentsSelection.TOP, 2))
        look = exact(look, move(look, 1100, MoveInsertion.TOP))
        assertTrue(display(look).queuedTop.isEmpty())
        // The wire form of a display with nothing waiting is unchanged.
        assertFalse("queued" in GameWire.json.encodeToString(ContentsDisplay.serializer(), display(search())))
    }

    @Test fun rulesV29KeepsThePlainCloseShuffle() {
        val state = search()
        val command = command(state, move(state, 1100, MoveInsertion.TOP))
        val moved = assertNotNull(GameplayRules.move(state, a, command, (command.action as GameAction.Play).action as PlayAction.MoveCard,
            rulesVersion = 29).state)
        assertTrue(display(moved).queuedTop.isEmpty())
        // Even with something waiting, a v29 close shuffles the whole Library.
        val waiting = assertNotNull(GameplayRules.change(state, a, command).state)
        val dismiss = PlayAction.DismissContents(display(waiting).id, ContentsRest.SHUFFLE, seed(3))
        val old = assertNotNull(LibraryRules.change(waiting, a, command(waiting, dismiss), dismiss, rulesVersion = 29).state)
        assertEquals(RandomV1.shuffle(library(waiting).cards, seed(3)), library(old).cards)
    }

    private fun play(t: GameplayTree, actor: String, action: PlayAction): GameplayTree {
        val state = assertNotNull(t.stateAt(t.currentNodeId))
        val change = HistoryContinuation.change(t, actor, command(state, action))
        return assertNotNull(change.tree, change.reason)
    }
    private fun board(t: GameplayTree) = assertNotNull(t.stateAt(t.currentNodeId)).board
    private fun undo(t: GameplayTree, actor: String = a) = HistoryContinuation.change(t, actor,
        command(assertNotNull(t.stateAt(t.currentNodeId)), PlayAction.AdjustLife(a, "0")).copy(action = GameAction.Undo))

    @Test fun aWaitingMoveAndTheCloseUndoExactly() {
        var t = play(GameplayTree(root()), a, PlayAction.ShowContents(libraryId, ContentsSelection.ALL))
        val opened = board(t)
        t = play(t, a, PlayAction.MoveCard(id(1100), MoveSource(graveyardId), MoveDestination(libraryId, MoveInsertion.TOP)))
        // The other player's unrelated later event does not block the undo.
        t = play(t, b, PlayAction.AdjustLife(b, "-1"))
        val undone = assertNotNull(undo(t).tree, undo(t).reason)
        assertEquals(opened.copy(players = board(t).players), board(undone))
        // Waiting, then closing; undoing the close restores the open search with its waiting card.
        t = play(t, a, move(assertNotNull(t.stateAt(t.currentNodeId)), 1005, MoveInsertion.BOTTOM))
        val waiting = board(t)
        assertEquals(listOf(listOf(id(1005))), waiting.contentsDisplays.single().queuedBottom)
        t = play(t, b, PlayAction.DismissContents(waiting.contentsDisplays.single().id, ContentsRest.SHUFFLE, seed(4)))
        assertEquals(id(1005), board(t).containers.single { it.id == libraryId }.cards.last())
        val reopened = assertNotNull(undo(t, b).tree, undo(t, b).reason)
        assertEquals(waiting, board(reopened))
    }
}
