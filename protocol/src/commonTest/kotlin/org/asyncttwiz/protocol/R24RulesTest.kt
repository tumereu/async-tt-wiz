package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v24: face-down casting, Manifest top. From rules v35 a face-down cast is a face-down Stack entry pointing
 * at the hand card, which resolves onto the Battlefield face down: a real card is never in the Stack (STK-003). */
class R24RulesTest {
    private val a = gameplayId(7)
    private val b = gameplayId(8)
    private val library = gameplayId(20)
    private val hand = gameplayId(21)
    private val field = gameplayId(22)
    private val graveyard = gameplayId(23)
    private val stack = gameplayId(25)

    private fun command(action: GameAction, n: Int, state: PlayState) =
        GameCommand(gameplayId(n), GameScope(state.gameId, playId = state.id), action, gameplayId(80), "1")

    private fun accepted(state: PlayState, action: PlayAction, n: Int): GameplayChange {
        val change = gameplayChange(state, action, n)
        assertNotNull(change.state, change.reason)
        val event = assertNotNull(change.mutation)
        assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, event.transition.rulesVersion)
        assertEquals(change.state, GameplayHistory.apply(state, event), "recorded replay of $action")
        assertEquals(state, GameplayHistory.unapply(change.state, event), "exact inverse of $action")
        return change
    }

    private fun card(state: PlayState, n: Int) = state.board.cards.single { it.id == gameplayId(n) }

    /** Card 10 in a's Hand; it was face up for its owner only. */
    private fun inHand(): PlayState = accepted(gameplayFixture(), PlayAction.MoveCard(gameplayId(10),
        MoveSource(library, edge = MoveSourceEdge.TOP), MoveDestination(hand, MoveInsertion.UNORDERED)), 100).state!!

    /** A tree root (no recorded events) with card 10 already in a's Hand. */
    private fun handRoot(): PlayState = gameplayFixture().let { s -> s.copy(board = s.board.copy(
        cards = s.board.cards.map { if (it.id == gameplayId(10)) it.copy(containerId = hand) else it },
        containers = s.board.containers.map { when (it.id) {
            library -> it.copy(cards = it.cards - gameplayId(10))
            hand -> it.copy(cards = listOf(gameplayId(10)))
            else -> it
        } })) }

    /** The face-down cast and its resolve as one batch [command] (one Undo), each lowered against the predicted state. */
    private fun castBatch(state: PlayState, n: Int): List<PlayAction> {
        val batch = command(GameAction.PlayBatch(emptyList()), n, state)
        val cast = assertNotNull(HandActions.castFaceDown(state, gameplayId(10)))
        val stacked = assertNotNull(GameplayRules.change(state, a, PlayBatches.member(batch, 0, cast)).state)
        val entry = stacked.board.stackEffects.single()
        return listOf(cast) + StackQuickActions.actions(stacked, entry.id, PlayZone.BATTLEFIELD)
    }

    /** Card 10 cast face down from a's Hand: the entry, then its resolve, each exact and replayable. */
    private fun castFaceDown(): Pair<PlayState, PlayState> {
        val start = inHand()
        val change = accepted(start, assertNotNull(HandActions.castFaceDown(start, gameplayId(10))), 101)
        assertTrue(change.mutation!!.transition.disclosures.isEmpty())
        assertFalse(change.mutation.revealsInformation)
        val stacked = change.state!!
        val entry = stacked.board.stackEffects.single()
        val resolve = StackQuickActions.actions(stacked, entry.id, PlayZone.BATTLEFIELD).single()
        val resolved = accepted(stacked, resolve, 102)
        assertTrue(resolved.mutation!!.transition.disclosures.isEmpty())
        assertFalse(resolved.mutation.revealsInformation)
        return stacked to resolved.state!!
    }

    @Test fun rulesVersionIsAtLeast24() = assertTrue(CURRENT_GAMEPLAY_RULES_VERSION >= 24)

    // STK-005, STK-003, TBL-090, ZON-008
    @Test fun castingFaceDownPutsAFaceDownEntryOnTheStackThatResolvesIntoAFaceDown22WithoutRevealingIt() {
        val (stacked, cast) = castFaceDown()
        // On the Stack: a face-down entry pointing at the hand card, which stays in the Hand hidden from the opponent.
        val entry = stacked.board.stackEffects.single()
        assertTrue(entry.faceDown)
        assertEquals(gameplayId(10), entry.sourceId)
        assertEquals(listOf(entry.id), stacked.board.containers.single { it.id == stack }.cards)
        val held = card(stacked, 10)
        assertEquals(hand, held.containerId, "the real card stays in the Hand")
        assertFalse(CardVisibility.isVisibleFaceUp(stacked.board, held, b))
        assertTrue(CardVisibility.isVisibleFaceUp(stacked.board, held, a))
        assertEquals(listOf(a), CardVisibility.viewers(stacked.board, held))
        assertTrue(ManualDisclosure.permittedFaces(stacked, CombatStackRules.inspection(stacked.board, entry.id)!!, b, inspecting = true).isEmpty())
        // Resolved: face down in the main row, a public 2/2.
        val down = card(cast, 10)
        assertEquals(field, down.containerId)
        assertEquals(BoardPosition(0, 1), down.placement!!.position, "face down lands in the main row")
        assertTrue(cast.board.containers.single { it.id == stack }.cards.isEmpty())
        assertTrue(cast.board.containers.single { it.id == hand }.cards.isEmpty())
        assertTrue(cast.board.stackEffects.isEmpty())
        assertTrue(down.isFaceDown)
        assertEquals("2/2", down.corner)
        assertFalse(CardVisibility.isVisibleFaceUp(cast.board, down, b))
        // Its owner may still peek at it, like at any own face-down Battlefield card; the opponent may not.
        assertEquals(listOf(gameplayId(30)), ManualDisclosure.permittedFaces(cast, down, a, inspecting = true).map { it.faceId })
        assertTrue(ManualDisclosure.permittedFaces(cast, down, b, inspecting = true).isEmpty())
    }

    // STK-005
    @Test fun aFaceDownEntryComesOnlyFromAHandAndItsCopyStaysFaceDown() {
        val start = inHand()
        val (stacked, _) = castFaceDown()
        val entry = stacked.board.stackEffects.single()
        val copy = accepted(stacked, PlayAction.Stack(StackAction.CopyEffect(entry.id, GameplayRules.stackDigest(stacked))), 103).state!!
        assertTrue(copy.board.stackEffects.all { it.faceDown })
        assertFalse(CardVisibility.isVisibleFaceUp(copy.board, card(copy, 10), b))
        // Graveyard and other public zones never cast face down; an ordinary entry still shows a hand card to both.
        val discarded = accepted(start, PlayAction.MoveCard(gameplayId(10), MoveSource(hand),
            MoveDestination(graveyard, MoveInsertion.UNORDERED)), 104).state!!
        assertNull(HandActions.castFaceDown(discarded, gameplayId(10)))
        assertEquals("inapplicable", gameplayChange(discarded, PlayAction.Stack(StackAction.AddEffect(gameplayId(10),
            GameplayRules.stackDigest(discarded), faceDown = true)), 105).reason)
        val open = accepted(start, PlayAction.Stack(StackAction.AddEffect(gameplayId(10), GameplayRules.stackDigest(start))), 106).state!!
        assertFalse(open.board.stackEffects.single().faceDown)
        assertTrue(CardVisibility.isVisibleFaceUp(open.board, card(open, 10), b))
        // The wire never carries the flag for ordinary entries, and "false" is not a valid face-down cast.
        assertFalse("faceDown" in GameWire.json.encodeToString(StackAction.serializer(), StackAction.AddEffect(gameplayId(10), GameplayRules.stackDigest(start))))
        assertFalse(CombatStackRules.valid(StackAction.AddEffect(gameplayId(10), GameplayRules.stackDigest(start), faceDown = false)))
    }

    // STK-005: going to the graveyard instead, the card is put there face up like any other spell.
    @Test fun aFaceDownEntryPutIntoTheGraveyardLeavesTheHandFaceUp() {
        val (stacked, _) = castFaceDown()
        val entry = stacked.board.stackEffects.single()
        val gone = accepted(stacked, StackQuickActions.actions(stacked, entry.id, PlayZone.GRAVEYARD).single(), 107).state!!
        assertEquals(graveyard, card(gone, 10).containerId)
        assertFalse(card(gone, 10).isFaceDown)
    }

    /** A state recorded before rules v35: card 10 cast face down sits in the Stack itself. */
    private fun legacyCast(): PlayState = inHand().let { s -> s.copy(board = s.board.copy(
        cards = s.board.cards.map { if (it.id == gameplayId(10)) it.copy(containerId = stack, covered = true, explicitlyCovered = true) else it },
        containers = s.board.containers.map { when (it.id) {
            hand -> it.copy(cards = it.cards - gameplayId(10))
            stack -> it.copy(cards = listOf(gameplayId(10)))
            else -> it
        } })) }

    /** Seal the v23 acceptance path exactly as it was recorded then. */
    private fun retained(original: PlayState, change: GameplayChange): GameplayMutation {
        val next = assertNotNull(change.state, change.reason)
        val raw = assertNotNull(change.mutation)
        val delta = GameplayDeltas.capture(original, next, raw.id)
        val recorded = GameplayHistory.recontextualize(original, raw.copy(schemaVersion = 2,
            transition = raw.transition.copy(delta = delta, concerns = GameplayConcerns.capture(original, next, raw, delta)),
            bookkeeping = GameplayBookkeeping(original.currentGroup, original.currentBranchId, next.currentGroup)))
        val sealed = recorded.copy(transition = recorded.transition.copy(delta = delta.copy(seal = GameplayDeltas.seal(recorded))))
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(sealed))
        assertEquals(next, GameplayHistory.apply(original, decoded))
        assertEquals(original, GameplayHistory.unapply(next, decoded))
        assertEquals(decoded.transition, GameplayHistory.replay(original, decoded).mutation!!.transition)
        return decoded
    }

    // STK-003: retained events that moved a real card out of the Stack still replay exactly.
    @Test fun retainedV23ResolveStillTurnsTheCardFaceUpOnReplay() {
        val cast = legacyCast()
        val resolve = PlayAction.MoveCard(gameplayId(10), MoveSource(stack),
            MoveDestination(field, MoveInsertion.EMPTY, BoardPosition(0, 1)))
        val old = GameplayRules.move(cast, a, command(GameAction.Play(resolve), 102, cast), resolve, rulesVersion = 23)
        val event = retained(cast, old)
        assertEquals(23, event.transition.rulesVersion)
        val replayed = GameplayHistory.replay(cast, event).state!!
        assertFalse(card(replayed, 10).isFaceDown)
        assertEquals(card(cast, 10).corner, card(replayed, 10).corner)
    }

    // ZON-018
    @Test fun manifestTopEntersTheBattlefieldFaceDownWithoutRevealing() {
        val start = gameplayFixture()
        val top = start.board.containers.single { it.id == library }.cards.first()
        val manifest = assertNotNull(LibraryActions.manifestTop(start.board, library))
        assertEquals(top, manifest.cardId)
        assertEquals(MoveSourceEdge.TOP, manifest.source.edge)
        assertEquals(BoardPosition(0, 1), manifest.destination.position)
        val change = accepted(start, manifest, 100)
        val down = change.state!!.board.cards.single { it.id == top }
        assertEquals(field, down.containerId)
        assertTrue(down.isFaceDown)
        assertEquals("2/2", down.corner)
        assertTrue(change.mutation!!.transition.disclosures.isEmpty())
        assertFalse(CardVisibility.isVisibleFaceUp(change.state.board, down, a))
        assertFalse(CardVisibility.isVisibleFaceUp(change.state.board, down, b))
        // The next manifest takes the next free main-row cell.
        assertEquals(BoardPosition(1, 1), LibraryActions.manifestTop(change.state.board, library)!!.destination.position)
        val empty = start.copy(board = start.board.copy(containers = start.board.containers.map {
            if (it.id == library) it.copy(cards = emptyList()) else it }))
        assertNull(LibraryActions.manifestTop(empty.board, library))
    }

    private fun cast(root: PlayState, n: Int): GameplayTree {
        val change = HistoryContinuation.change(GameplayTree(root), a, command(GameAction.PlayBatch(castBatch(root, n)), n, root))
        return assertNotNull(change.tree, change.reason)
    }

    // STK-005: play and resolve are one Undo.
    @Test fun undoingTheCastIsBlockedByALaterEditOfThePublicCorner() {
        val root = handRoot()
        val edited = assertNotNull(cast(root, 200).play(b, command(GameAction.Play(
            PlayAction.AdjustCorner(gameplayId(10), CornerPart.BOTH, 1)), 202, root)).tree)
        assertEquals("3/3", card(edited.stateAt(edited.currentNodeId)!!, 10).corner)
        assertNull(SelectiveUndo.change(edited, a, command(GameAction.Undo, 900, root)).tree,
            "casting face down assigned the corner the later edit depends on")
        // Without the dependent edit one Undo goes exactly back to the Hand, the Stack empty.
        val undone = assertNotNull(SelectiveUndo.change(cast(root, 200), a, command(GameAction.Undo, 900, root)).tree)
        assertEquals(root.board, undone.stateAt(undone.currentNodeId)!!.board)
    }

    @Test fun undoingAFaceDownCastIsBlockedOnceTheCardWasTurnedFaceUp() {
        val root = handRoot()
        var tree = cast(root, 300)
        tree = assertNotNull(tree.play(b, command(GameAction.Play(PlayAction.SetCovered(gameplayId(10), false)), 301, root)).tree)
        assertNull(SelectiveUndo.change(tree, a, command(GameAction.Undo, 901, root)).tree)
    }
}
