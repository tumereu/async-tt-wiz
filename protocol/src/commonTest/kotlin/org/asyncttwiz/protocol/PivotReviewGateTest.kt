package org.asyncttwiz.protocol

import kotlin.test.*

/**
 * Adversarial review of the Automatum-always-on pivot (I-D52; report `arcanum-pivot-review.md`, outside the repo): the
 * server's gate ([AutomatumOnly]). Each ignored "pivotreview #N" test pins a problem found and fails while it isn't fixed.
 */
class PivotReviewGateTest {
    private val a = gameplayId(7)
    private val b = gameplayId(8)
    private val library = gameplayId(20)
    private val hand = gameplayId(21)
    private val graveyard = gameplayId(23)
    private var request = 970

    /** Two cards on Alice's Battlefield, one in her hand, the rest in her library; turn [turn]. */
    private fun state(opening: Boolean = false, turn: String = "3"): PlayState =
        gameplayFixture().withBattlefieldCards(gameplayId(10), gameplayId(11)).let { s ->
            val inHand = gameplayId(12)
            s.copy(board = s.board.copy(turn = s.board.turn.copy(number = turn),
                players = s.board.players.map { it.copy(status = if (opening) PlayerStatus.OPENING else "") },
                cards = s.board.cards.map { if (it.id == inHand) it.copy(containerId = hand) else it },
                containers = s.board.containers.map { c -> when (c.id) {
                    library -> c.copy(cards = c.cards - inHand, layers = LibraryLayers.initial(gameplayId(28), c.cards - inHand))
                    hand -> c.copy(cards = listOf(inHand))
                    else -> c
                } }))
        }
    private fun command(action: GameAction) = GameCommand(gameplayId(request++), GameScope(gameplayId(1), playId = gameplayId(2)),
        action, gameplayId(80), "1")
    private fun card(state: PlayState, n: Int) = state.board.cards.single { it.id == gameplayId(n) }
    private fun toGraveyard(state: PlayState, n: Int) = card(state, n).let {
        PlayAction.MoveCard(it.id, MoveSource(it.containerId, it.placement), MoveDestination(graveyard, MoveInsertion.UNORDERED)) }

    // pivotreview #4 — SYS-073 (I-D52, P8): the gate takes any command that carries a tag of the right form as
    // Automatum's. Automatum's own batches always carry the planned board's digest on their first member
    // (`EngineBatch.plan`, `MtgTag.board`) and the app never sends a tagged single event, yet the server takes a single
    // event or a batch tagged by hand: Bob moves Alice's creature to her graveyard and sets his life to 99 with a
    // made-up meaning. Expected: a tag without the board digest of the board it applies to (or a tagged single event)
    // isn't Automatum's and is refused like any change by hand. (This only stops a stray or old client path; a client
    // made to cheat can still compute the digest: the server can't check Automatum's rules without running the engine.)
    @Ignore("pivotreview #4: a hand-made tag makes any change Automatum's")
    @Test fun aHandMadeTagIsNotAutomatums() {
        val state = state()
        val tree = GameplayTree(state)
        val forged = MtgTag(listOf("died"))
        assertEquals(RuleRefusals.AUTOMATUM_ONLY, AutomatumOnly.refusal(tree, b,
            command(GameAction.Play(toGraveyard(state, 10), forged))), "a tagged single event")
        assertEquals(RuleRefusals.AUTOMATUM_ONLY, AutomatumOnly.refusal(tree, b,
            command(GameAction.PlayBatch(listOf(PlayAction.SetLife(b, "99"), toGraveyard(state, 10)), listOf(MtgTag(folded = true), null)))),
            "a batch whose first member carries a tag without the board digest")
        // Automatum's own form still goes on
        assertNull(AutomatumOnly.refusal(tree, b, command(GameAction.PlayBatch(listOf(toGraveyard(state, 10)),
            listOf(MtgTag(listOf("died"), board = MtgFacts.digest(state.board)))))))
    }

    // pivotreview #5 — TRN-062 (I-D49, CR 103.5): during the opening the gate takes any change of the player's own
    // mulligan count. Alice took two mulligans (she owes one card), then sets her count back to 0 on its own and keeps
    // all seven. Expected: the count only goes up by one, together with a Mulligan in the same batch.
    @Ignore("pivotreview #5: the opening's mulligan count can be set back by hand")
    @Test fun theMulliganCountOnlyGoesUpWithAMulligan() {
        val opening = state(opening = true, turn = "1")
        val twice = opening.copy(board = opening.board.copy(players = opening.board.players.map {
            if (it.accountId == a) it.copy(mulligans = 2) else it }))
        assertEquals(RuleRefusals.AUTOMATUM_ONLY, AutomatumOnly.refusal(GameplayTree(twice), a,
            command(GameAction.Play(PlayAction.Mtg(MtgAction.SetMulligans(2, 0))))))
    }

    // SYS-073, TRN-064 (I-D52, CR 104.3a): a concession is a result batch the gate takes from either player, also over
    // the other player's waiting suggestion, and their own suggested loss
    @Test fun aConcessionGoesThroughTheGate() {
        val state = state()
        val tree = GameplayTree(state)
        for (actor in listOf(a, b))
            assertNull(AutomatumOnly.refusal(tree, actor, command(GameAction.PlayBatch(GameResultRules.concession(state.board, actor)))))
        val suggested = HistoryContinuation.change(tree, a, command(GameAction.Play(PlayAction.SuggestResult(SuggestedResult.DRAW))))
        val next = assertNotNull(suggested.tree, suggested.reason)
        val board = next.stateAt(next.currentNodeId)!!.board
        assertNotNull(board.resultSuggestion)
        for (actor in listOf(a, b))
            assertNull(AutomatumOnly.refusal(next, actor, command(GameAction.PlayBatch(GameResultRules.concession(board, actor)))))
    }
}
