package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v24: face-down casting, Manifest top. From rules v35 a face-down cast goes straight from the Hand onto
 * the Battlefield face down: a real card is never in the Stack (STK-003). */
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

    private fun castFaceDown(): PlayState {
        val start = inHand()
        val change = accepted(start, HandActions.castFaceDown(start, gameplayId(10))!!, 101)
        assertTrue(change.mutation!!.transition.disclosures.isEmpty())
        assertFalse(change.mutation.revealsInformation)
        return change.state!!
    }

    @Test fun rulesVersionIsAtLeast24() = assertTrue(CURRENT_GAMEPLAY_RULES_VERSION >= 24)

    // STK-005, STK-003, TBL-090, ZON-008
    @Test fun castingFaceDownPutsTheHandCardStraightOntoTheBattlefieldAsAFaceDown22WithoutRevealingIt() {
        val cast = castFaceDown()
        val down = card(cast, 10)
        assertEquals(field, down.containerId)
        assertEquals(BoardPosition(0, 1), down.placement!!.position, "face down lands in the main row")
        assertTrue(cast.board.containers.single { it.id == stack }.cards.isEmpty(), "the real card never enters the Stack")
        assertTrue(cast.board.containers.single { it.id == hand }.cards.isEmpty())
        assertTrue(cast.board.stackEffects.isEmpty())
        assertTrue(down.isFaceDown)
        assertEquals("2/2", down.corner)
        assertFalse(CardVisibility.isVisibleFaceUp(cast.board, down, b))
        // Its owner may still peek at it, like at any own face-down Battlefield card; the opponent may not.
        assertEquals(listOf(gameplayId(30)), ManualDisclosure.permittedFaces(cast, down, a, inspecting = true).map { it.faceId })
        assertTrue(ManualDisclosure.permittedFaces(cast, down, b, inspecting = true).isEmpty())
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

    @Test fun undoingTheCastIsBlockedByALaterEditOfThePublicCorner() {
        val root = handRoot()
        fun cast(): GameplayTree = assertNotNull(GameplayTree(root).play(a, command(GameAction.Play(
            HandActions.castFaceDown(root, gameplayId(10))!!), 200, root)).tree)
        val edited = assertNotNull(cast().play(b, command(GameAction.Play(
            PlayAction.AdjustCorner(gameplayId(10), CornerPart.BOTH, 1)), 202, root)).tree)
        assertEquals("3/3", card(edited.stateAt(edited.currentNodeId)!!, 10).corner)
        assertNull(SelectiveUndo.change(edited, a, command(GameAction.Undo, 900, root)).tree,
            "casting face down assigned the corner the later edit depends on")
        // Without the dependent edit one Undo goes exactly back to the Hand.
        val undone = assertNotNull(SelectiveUndo.change(cast(), a, command(GameAction.Undo, 900, root)).tree)
        assertEquals(root.board, undone.stateAt(undone.currentNodeId)!!.board)
    }

    @Test fun undoingAFaceDownCastIsBlockedOnceTheCardWasTurnedFaceUp() {
        val root = handRoot()
        var tree = GameplayTree(root)
        tree = assertNotNull(tree.play(a, command(GameAction.Play(HandActions.castFaceDown(root, gameplayId(10))!!), 300, root)).tree)
        tree = assertNotNull(tree.play(b, command(GameAction.Play(PlayAction.SetCovered(gameplayId(10), false)), 301, root)).tree)
        assertNull(SelectiveUndo.change(tree, a, command(GameAction.Undo, 901, root)).tree)
    }
}
