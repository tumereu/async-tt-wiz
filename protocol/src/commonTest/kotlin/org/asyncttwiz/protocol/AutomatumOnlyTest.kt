package org.asyncttwiz.protocol

import kotlin.test.*

/** I-D52 (SYS-069): the server takes board changes only as Automatum batches; outside them only what isn't a change
 * made by hand ([AutomatumOnly]). */
class AutomatumOnlyTest {
    private val a = gameplayId(7)
    private val b = gameplayId(8)
    private val library = gameplayId(20)
    private val hand = gameplayId(21)
    private val field = gameplayId(22)
    private val graveyard = gameplayId(23)
    private var request = 950

    /** Two cards on Alice's Battlefield, one in her hand, the rest in her library. */
    private fun state(opening: Boolean = false, turn: String = "1"): PlayState =
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
    private fun refusal(state: PlayState, action: PlayAction, actor: String = a) =
        AutomatumOnly.refusal(GameplayTree(state), actor, command(GameAction.Play(action)))
    private fun card(state: PlayState, n: Int) = state.board.cards.single { it.id == gameplayId(n) }
    private fun move(state: PlayState, n: Int, destination: MoveDestination) =
        card(state, n).let { PlayAction.MoveCard(it.id, MoveSource(it.containerId, it.placement), destination) }

    // SYS-069 (I-D52): a change made by hand is refused, alone or in a batch without tags
    @Test fun changesByHandAreRefused() {
        val state = state()
        for (action in listOf(PlayAction.SetTapped(gameplayId(10), true), PlayAction.SetLife(a, "17"), PlayAction.NextStep,
            PlayAction.EndTurn, PlayAction.RollDie(6, 3), PlayAction.SetStatus("Thinking"),
            move(state, 10, MoveDestination(graveyard, MoveInsertion.UNORDERED)),
            PlayAction.Combat(CombatAction.Close(gameplayId(70))),
            PlayAction.Mtg(MtgAction.SetPriority(null, MtgPriority(a)))))
            assertEquals(RuleRefusals.AUTOMATUM_ONLY, refusal(state, action), "$action")
        val batch = GameAction.PlayBatch(listOf(PlayAction.SuggestResult(SuggestedResult.DRAW), PlayAction.SetTapped(gameplayId(10), true)))
        assertEquals(RuleRefusals.AUTOMATUM_ONLY, AutomatumOnly.refusal(GameplayTree(state), a, command(batch)))
    }

    // SYS-057, SYS-069: an automated event or batch (one member tagged is enough) is the engine's: it goes on to the reducer
    @Test fun automatedChangesGoOn() {
        val state = state()
        val tap = PlayAction.SetTapped(gameplayId(10), true)
        assertNull(AutomatumOnly.refusal(GameplayTree(state), a, command(GameAction.Play(tap, MtgTag(listOf("tapped-for-mana"))))))
        assertNull(AutomatumOnly.refusal(GameplayTree(state), a, command(GameAction.PlayBatch(listOf(tap, PlayAction.NextStep),
            listOf(MtgTag(board = MtgFacts.digest(state.board)), null)))))
        // Other commands aren't play commands
        assertNull(AutomatumOnly.refusal(GameplayTree(state), a, command(GameAction.Undo)))
    }

    // SYS-069, TRN-046, TRN-063 (I-D52): what isn't a change made by hand still goes: result suggestions and answers,
    // the player's own Automatum settings, early answers, Inspect other side, a card's place in its own Battlefield and the
    // combat draft
    @Test fun whatIsNoChangeByHandStillGoes() {
        val state = state()
        val early = EngineRecord("pre:x", EngineRecordKind.PreAnswer, data = "{}", player = b)
        val battlefieldCard = card(state, 10)
        for (action in listOf(PlayAction.SuggestResult(SuggestedResult.DRAW), PlayAction.SuggestResult(SuggestedResult.LOSS),
            PlayAction.DeclineResult(gameplayId(60)), PlayAction.WithdrawResult(gameplayId(60)),
            PlayAction.Mtg(MtgAction.SetSkipWhenNoChoice(false)), PlayAction.Mtg(MtgAction.SetFullControl(true)),
            PlayAction.Mtg(MtgAction.SetRecord(early.id, null, early)), PlayAction.Mtg(MtgAction.SetRecord(early.id, early, null)),
            PlayAction.InspectFace(battlefieldCard.id, battlefieldCard.faceId, field), PlayAction.PassAttention,
            move(state, 10, MoveDestination(field, MoveInsertion.EMPTY, BoardPosition(4, 1))),
            PlayAction.Combat(CombatAction.Open), PlayAction.Combat(CombatAction.Remove(gameplayId(70), gameplayId(71)))))
            assertNull(refusal(state, action, b), "$action")
        // An engine record that isn't an early answer is the engine's
        val effect = EngineRecord("effect:1", EngineRecordKind.Effect, 3, "{}")
        assertEquals(RuleRefusals.AUTOMATUM_ONLY, refusal(state, PlayAction.Mtg(MtgAction.SetRecord(effect.id, null, effect))))
    }

    // TRN-062 (I-D49, CR 103.5): a player's own opening — mulligan, its count, own hand cards to the bottom of own
    // library, Ready — goes while they are mulliganing on turn 1, and only then
    @Test fun theOpeningGoesOnlyWhileMulliganing() {
        val opening = state(opening = true)
        val mulligan = PlayAction.Mulligan(library, hand, listOf(gameplayId(12)), 7, RandomV1.encodeSeed(ByteArray(32)))
        val bottom = move(opening, 12, MoveDestination(library, MoveInsertion.BOTTOM))
        val actions = listOf(mulligan, PlayAction.Mtg(MtgAction.SetMulligans(0, 1)), bottom, PlayAction.SetStatus(""))
        for (action in actions) assertNull(refusal(opening, action), "$action")
        // Bob's opening is his own: not Alice's hand and library
        assertEquals(RuleRefusals.AUTOMATUM_ONLY, refusal(opening, mulligan, b))
        assertEquals(RuleRefusals.AUTOMATUM_ONLY, refusal(opening, bottom, b))
        // Not to the top, and no other status
        assertEquals(RuleRefusals.AUTOMATUM_ONLY, refusal(opening, move(opening, 12, MoveDestination(library, MoveInsertion.TOP))))
        assertEquals(RuleRefusals.AUTOMATUM_ONLY, refusal(opening, PlayAction.SetStatus("Keeping")))
        // Once Ready, or after the first turn, none of it
        for (state in listOf(state(), state(opening = true, turn = "2")))
            for (action in actions) assertEquals(RuleRefusals.AUTOMATUM_ONLY, refusal(state, action), "$action")
    }
}
