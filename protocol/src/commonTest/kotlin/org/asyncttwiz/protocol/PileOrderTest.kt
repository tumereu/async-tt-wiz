package org.asyncttwiz.protocol

import kotlin.test.*

/** Seals a move made under an older rules version exactly as that build recorded it (no v27 ordering). */
internal fun legacyPileMove(state: PlayState, action: PlayAction.MoveCard, request: Int, rulesVersion: Int = 26,
    actor: Int = 7): Pair<PlayState, GameplayMutation> {
    val command = GameCommand(gameplayId(request), GameScope(state.gameId, playId = state.id), GameAction.Play(action), gameplayId(80), "1")
    val change = GameplayRules.move(state, gameplayId(actor), command, action, rulesVersion = rulesVersion)
    val next = assertNotNull(change.state, change.reason)
    val raw = assertNotNull(change.mutation)
    assertEquals(rulesVersion, raw.transition.rulesVersion)
    val delta = GameplayDeltas.capture(state, next, raw.id)
    val recorded = GameplayHistory.recontextualize(state, raw.copy(schemaVersion = 2,
        transition = raw.transition.copy(delta = delta, concerns = GameplayConcerns.capture(state, next, raw, delta)),
        bookkeeping = GameplayBookkeeping(state.currentGroup, state.currentBranchId, next.currentGroup)))
    val sealed = recorded.copy(transition = recorded.transition.copy(delta = delta.copy(seal = GameplayDeltas.seal(recorded))))
    val decoded = GameWire.json.decodeFromString(GameplayMutation.serializer(), GameWire.json.encodeToString(GameplayMutation.serializer(), sealed))
    return next to decoded
}

class PileOrderTest {
    private val actor = gameplayId(7)
    private val other = gameplayId(8)
    private val library = gameplayId(20)
    private val hand = gameplayId(21)
    private val graveyard = gameplayId(23)
    private val exile = gameplayId(24)
    private fun cards(state: PlayState, id: String) = state.board.containers.single { it.id == id }.cards
    private fun toPile(card: String, pile: String = graveyard, from: String = library) =
        PlayAction.MoveCard(card, MoveSource(from), MoveDestination(pile, MoveInsertion.UNORDERED))
    private fun change(state: PlayState, action: PlayAction, request: Int): GameplayChange =
        gameplayChange(state, action, request).also { assertNotNull(it.state, it.reason) }
    private fun run(state: PlayState, actions: List<PlayAction>, first: Int): PlayState =
        actions.foldIndexed(state) { n, s, a -> change(s, a, first + n).state!! }

    @Test fun rulesVersionIs27() = assertEquals(27, CURRENT_GAMEPLAY_RULES_VERSION)

    @Test fun newestArrivalIsTheTopAndRecordsItsNeighbours() {
        val root = gameplayFixture(); val ids = cards(root, library)
        val first = change(root, toPile(ids[3]), 100)
        val second = change(first.state!!, toPile(ids[0]), 101)
        assertEquals(listOf(ids[0], ids[3]), cards(second.state!!, graveyard))
        assertEquals(CardLocation(graveyard, beforeId = ids[3]), second.mutation!!.transition.movement!!.to)
        assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, second.mutation.transition.rulesVersion)
        // The pile edit is ordered now: an exact sequence insertion, not an unordered membership edit.
        val edit = second.mutation.transition.delta!!.entities.single { it.id == graveyard }.values.single { it.path == listOf("cards") }
        assertTrue(edit.sequence && !edit.unordered)
        // Exact apply/unapply from the sealed delta.
        assertEquals(second.state.board, GameplayHistory.apply(first.state, second.mutation)!!.board)
        assertEquals(first.state.board, GameplayHistory.reverse(second.state, second.mutation))
        // Exile follows the same rule.
        val exiled = run(root, listOf(toPile(ids[4], exile), toPile(ids[1], exile)), 102)
        assertEquals(listOf(ids[1], ids[4]), cards(exiled, exile))
    }

    @Test fun millingSeveralPutsTheLastMilledOnTop() {
        val root = gameplayFixture(); val ids = cards(root, library)
        val mill = LibraryActions.moveSlice(root.board, library, ContentsSelection.TOP, 3, MoveDestination(graveyard, MoveInsertion.UNORDERED))
        assertEquals(ids.take(3), mill.map { it.cardId })
        val milled = run(root, mill, 100)
        assertEquals(listOf(ids[2], ids[1], ids[0]), cards(milled, graveyard))
        // Mass moves of captured cards land in the order they are moved, too.
        val back = run(milled, LibraryActions.moveCaptured(milled.board, listOf(ids[3], ids[4]),
            MoveDestination(exile, MoveInsertion.UNORDERED)), 110)
        assertEquals(listOf(ids[4], ids[3]), cards(back, exile))
    }

    @Test fun removingTheTopShowsTheNextCardAndSamePileMovesKeepTheirPlace() {
        val root = gameplayFixture(); val ids = cards(root, library)
        val pile = run(root, ids.take(3).map { toPile(it) }, 100)
        assertEquals(listOf(ids[2], ids[1], ids[0]), cards(pile, graveyard))
        val taken = change(pile, PlayAction.MoveCard(ids[2], MoveSource(graveyard), MoveDestination(hand, MoveInsertion.UNORDERED)), 110)
        assertEquals(listOf(ids[1], ids[0]), cards(taken.state!!, graveyard))
        assertEquals(CardLocation(graveyard, beforeId = ids[1]), taken.mutation!!.transition.movement!!.from)
        val middle = change(pile, PlayAction.MoveCard(ids[1], MoveSource(graveyard), MoveDestination(hand, MoveInsertion.UNORDERED)), 111)
        assertEquals(listOf(ids[2], ids[0]), cards(middle.state!!, graveyard))
        // Dropping a pile card back onto its own pile is not a reorder.
        val same = gameplayChange(pile, PlayAction.MoveCard(ids[0], MoveSource(graveyard), MoveDestination(graveyard, MoveInsertion.UNORDERED)), 112)
        assertNull(same.mutation); assertEquals(pile, same.state)
    }

    @Test fun cardsAlreadyThereKeepTheirOrderAndOnlyNewArrivalsGoOnTop() {
        val root = gameplayFixture(); val ids = cards(root, library)
        // An old game's id-sorted graveyard is not migrated.
        val old = root.copy(board = root.board.copy(cards = root.board.cards.map {
            if (it.id in ids.take(2)) it.copy(containerId = graveyard, covered = false) else it },
            containers = root.board.containers.map { when (it.id) {
                library -> it.copy(cards = ids.drop(2)); graveyard -> it.copy(cards = ids.take(2)); else -> it } }))
        val next = change(old, toPile(ids[4]), 100).state!!
        assertEquals(listOf(ids[4], ids[0], ids[1]), cards(next, graveyard))
    }

    @Test fun panelRestAndRandomDiscardArriveOnTop() {
        val root = gameplayFixture(); val ids = cards(root, library)
        val shown = change(root, PlayAction.ShowContents(library, ContentsSelection.TOP, 3), 100).state!!
        val display = shown.board.contentsDisplays.single()
        val rest = change(shown, PlayAction.DismissContents(display.id, ContentsRest.GRAVEYARD, RandomV1.encodeSeed(ByteArray(32))), 101).state!!
        // Put away one by one in panel order: the last panel card ends on top.
        assertEquals(display.cardIds.reversed(), cards(rest, graveyard))
        val drawn = run(rest, LibraryActions.draw(rest.board, library, hand, 2), 110)
        val random = HandActions.random(drawn.board, hand, emptyList(), RandomV1.encodeSeed(ByteArray(32) { 1 }), HandRandomResult.DISCARD)
        val discarded = change(drawn, random, 120).state!!
        assertEquals(listOf(random.cardId) + display.cardIds.reversed(), cards(discarded, graveyard))
        assertEquals(setOf(ids[3], ids[4]), (cards(drawn, hand)).toSet())
    }

    @Test fun olderRecordedMovesReplayUnchangedAsUnorderedBins() {
        val root = gameplayFixture(); val ids = cards(root, library)
        val (first, firstEvent) = legacyPileMove(root, toPile(ids[3]), 100)
        val (second, secondEvent) = legacyPileMove(first, toPile(ids[0]), 101)
        // v26: id-sorted membership, no neighbours, unordered delta.
        assertEquals(listOf(ids[0], ids[3]), cards(second, graveyard))
        assertEquals(CardLocation(graveyard), secondEvent.transition.movement!!.to)
        assertTrue(secondEvent.transition.delta!!.entities.single { it.id == graveyard }.values.single { it.path == listOf("cards") }.unordered)
        assertEquals(first.board, GameplayHistory.apply(root, firstEvent)!!.board)
        assertEquals(second.board, GameplayHistory.apply(GameplayHistory.apply(root, firstEvent)!!, secondEvent)!!.board)
        assertEquals(first.board, GameplayHistory.reverse(second, secondEvent))
        // Old arrivals stay independent of each other.
        val independent = GameplayHistory.replay(root, secondEvent)
        assertEquals(secondEvent.transition, independent.mutation!!.transition)
        assertEquals(listOf(ids[0]), cards(independent.state!!, graveyard))
        // A v27 arrival after old events goes on top of the old id-sorted pile.
        val upgraded = change(second, toPile(ids[4]), 102)
        assertEquals(listOf(ids[4], ids[0], ids[3]), cards(upgraded.state!!, graveyard))
        // An old event rebased onto an arrival-ordered pile adds on top instead of re-sorting it.
        val (_, oldArrival) = legacyPileMove(root, toPile(ids[0]), 103)
        val ordered = run(root, listOf(toPile(ids[3]), toPile(ids[4])), 104)
        assertEquals(listOf(ids[4], ids[3]), cards(ordered, graveyard))
        val rebased = GameplayHistory.replay(ordered, oldArrival)
        assertEquals(listOf(ids[0], ids[4], ids[3]), cards(assertNotNull(rebased.state, rebased.reason), graveyard))
    }

    // Undo: allowed iff later events' concerned tuples are unchanged.
    private var request = 2000
    private fun play(tree: GameplayTree, by: String, action: PlayAction): GameplayTree {
        val change = HistoryContinuation.change(tree, by, GameCommand(gameplayId(request++),
            GameScope(tree.initial.gameId, playId = tree.initial.id), GameAction.Play(action), gameplayId(80), "1"))
        return assertNotNull(change.tree, "$action: ${change.reason}")
    }
    private fun undo(tree: GameplayTree, by: String = actor) = HistoryContinuation.change(tree, by,
        GameCommand(gameplayId(request++), GameScope(tree.initial.gameId, playId = tree.initial.id), GameAction.Undo, gameplayId(80), "1"))
    private fun state(tree: GameplayTree) = assertNotNull(tree.stateAt(tree.currentNodeId))

    @Test fun undoIsBlockedOnlyWhenALaterMoveDependsOnTheChangedPileOrder() {
        val root = gameplayFixture(); val ids = cards(root, library)
        // Own latest arrival: undo takes it off the top again.
        var tree = play(play(GameplayTree(root), actor, toPile(ids[0])), actor, toPile(ids[1]))
        val undone = assertNotNull(undo(tree).tree)
        assertEquals(listOf(ids[0]), cards(state(undone), graveyard))
        // A later arrival elsewhere does not depend on this pile. (Nonadjacent Library cards, so the
        // source-side Library neighbours stay unchanged too.)
        tree = play(play(GameplayTree(root), actor, toPile(ids[0])), other, toPile(ids[3], exile))
        val allowed = assertNotNull(undo(tree).tree, undo(tree).reason)
        assertEquals(emptyList(), cards(state(allowed), graveyard))
        assertEquals(listOf(ids[3]), cards(state(allowed), exile))
        // A later arrival on top of this card recorded it as its neighbour: over-blocking, never a wrong pile.
        tree = play(play(GameplayTree(root), actor, toPile(ids[0])), other, toPile(ids[3]))
        assertEquals("replayBlocked", undo(tree).reason)
        // Taking a middle card out leaves the top alone: a later arrival on top still replays.
        val pile = ids.take(3).fold(GameplayTree(root)) { t, id -> play(t, actor, toPile(id)) }
        tree = play(pile, actor, PlayAction.MoveCard(ids[1], MoveSource(graveyard), MoveDestination(hand, MoveInsertion.UNORDERED)))
        tree = play(tree, other, toPile(ids[3]))
        val restored = assertNotNull(undo(tree).tree, undo(tree).reason)
        assertEquals(listOf(ids[3], ids[2], ids[1], ids[0]), cards(state(restored), graveyard))
        // A later move that took the card right above it depends on that neighbour.
        tree = play(pile, actor, PlayAction.MoveCard(ids[1], MoveSource(graveyard), MoveDestination(hand, MoveInsertion.UNORDERED)))
        tree = play(tree, other, PlayAction.MoveCard(ids[2], MoveSource(graveyard), MoveDestination(exile, MoveInsertion.UNORDERED)))
        assertEquals("replayBlocked", undo(tree).reason)
    }
}
