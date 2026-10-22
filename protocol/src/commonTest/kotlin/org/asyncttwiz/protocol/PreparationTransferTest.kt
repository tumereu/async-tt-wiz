package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlin.test.*

class PreparationTransferTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val game = GameSummary(id(1), "Transfer", "2026-10-22T00:00:00Z", AccountIdentity(id(2), "A"),
        AccountIdentity(id(3), "B"), id(4), id(5), InvitationState.PENDING)
    private val deck = PreparedDeck(id(6), id(7), "1", "Retained", null, id(8), listOf(
        PreparedEntry("definition", SavedDeckSection.MAINBOARD, "0", 4),
        PreparedEntry("definition", SavedDeckSection.SIDEBOARD, "4", 2)))
    private val initial = PreparationRules.empty(game).copy(players = listOf(
        PlayerPreparation(game.inviter.accountId, true, deck), PlayerPreparation(game.recipient.accountId, true)))
    private val actor = game.inviter.accountId
    private val scope = GameScope(game.id, game.preparationRound)
    private fun capture(state: LobbyPreparation = initial, source: SavedDeckSection = SavedDeckSection.MAINBOARD, all: Boolean = true) =
        PreparationRules.capture(state, actor, "definition", source, all)!!
    private fun move(state: LobbyPreparation, action: GameAction.TransferPreparedCard, summary: GameSummary = game,
        who: String = actor, bound: GameScope = scope) = PreparationRules.transfer(summary, state, who, bound, action)
    private fun cards(deck: PreparedDeck) = deck.entries.flatMap { entry -> List(entry.quantity) { deck.card(entry, it) } }.associateBy { it.id }

    @Test fun individualMovesSplitAndRecompactRangesWithoutChangingAnyIdentityOrSavedMetadata() {
        val captured = capture(); var state = initial
        val originalCards = cards(deck)
        for (offset in 0..3) {
            val action = captured.action(captured.entries.single(), offset)
            state = move(state, action).state!!
            val changed = state.players[0].deck!!
            assertEquals(3L - offset, changed.count(SavedDeckSection.MAINBOARD))
            assertEquals(3L + offset, changed.count(SavedDeckSection.SIDEBOARD))
            assertEquals(originalCards.keys, cards(changed).keys)
            assertEquals(deck.copy(entries = changed.entries), changed)
            assertFalse(state.players[0].ready); assertEquals(initial.players[1], state.players[1])
            assertEquals("sourceSectionChanged", move(state, action).reason)
        }
        assertEquals(listOf(PreparedEntry("definition", SavedDeckSection.SIDEBOARD, "0", 6)), state.players[0].deck!!.entries)
        val reverse = capture(state, SavedDeckSection.SIDEBOARD)
        val preview = PreparationRules.previewTransfer(state, actor, reverse)
        for (offset in 0..5) state = move(state, reverse.action(reverse.entries.single(), offset)).state!!
        assertEquals(preview, state)
        assertEquals(6L, state.players[0].deck!!.count(SavedDeckSection.MAINBOARD)) // No size/exchange rules.
        assertEquals(4L, initial.players[0].deck!!.count(SavedDeckSection.MAINBOARD))
    }

    @Test fun capturedAllExcludesLaterArrivalsAndConflictDoesNotSubstituteOrCancelIndependentCopies() {
        val captured = capture()
        val side = capture(source = SavedDeckSection.SIDEBOARD, all = false)
        val arrived = move(initial, side.action(side.entries.single(), 0)).state!!
        var state = move(arrived, captured.action(captured.entries.single(), 1)).state!! // Other device moved one original.
        for (offset in 0..3) {
            val change = move(state, captured.action(captured.entries.single(), offset))
            if (offset == 1) assertEquals("sourceSectionChanged", change.reason) else state = change.state!!
        }
        val remaining = state.players[0].deck!!.entries.single { it.section == SavedDeckSection.MAINBOARD }
        assertEquals("4", remaining.firstIndex); assertEquals(1, remaining.quantity)
        assertEquals(PreparationRules.previewTransfer(arrived, actor, captured), state)
        assertEquals(1L, capture(all = false).count)
    }

    @Test fun guardsBindPreparedCopySectionRoundActorAndLobbyButNotInvitationOrUnrelatedRevision() {
        val action = capture().let { it.action(it.entries.single(), 0) }
        assertEquals("preparedDeckChanged", move(initial, action.copy(deckId = id(90))).reason)
        assertEquals("copyUnavailable", move(initial, action.copy(cardId = id(91))).reason)
        assertEquals("copyUnavailable", move(initial, action.copy(cardIndex = "99")).reason)
        assertEquals("sourceSectionChanged", move(initial, action.copy(source = SavedDeckSection.SIDEBOARD)).reason)
        assertEquals("inapplicable", move(initial, action, bound = scope.copy(preparationId = id(92))).reason)
        assertEquals("lifecycleEnded", move(initial, action, summary = game.copy(lifecycle = GameLifecycle.ACTIVE)).reason)
        assertEquals("accessDenied", move(initial, action, who = game.recipient.accountId).reason)
        assertEquals("accessDenied", move(initial, action, who = id(93)).reason)
        for (invitation in InvitationState.entries) assertNotNull(move(initial, action,
            summary = game.copy(invitationId = id(94), invitationState = invitation, revision = "9007199254740993")).state)
        assertEquals(initial, PreparationRules.previewTransfer(initial, actor, capture().copy(roundId = id(95))))
    }

    @Test fun ordinaryIndividualCodecAndRetainedV8EncodingNeedNoNewBulkOrPersistenceFormat() {
        val action = capture().let { it.action(it.entries.single(), 0) }
        val command = GameCommand(id(20), scope, action, id(21), "1")
        assertTrue(GameWire.valid(command))
        assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
        assertEquals(GameWire.digest(command), GameWire.digest(command.copy(streamId = id(22), generation = "7")))
        assertNotEquals(GameWire.digest(command), GameWire.digest(command.copy(action = action.copy(source = SavedDeckSection.SIDEBOARD))))
        assertFalse(GameWire.valid(command.copy(scope = scope.copy(invitationId = game.invitationId))))
        for (index in listOf("-1", "01", "1.0", "9223372036854775808"))
            assertFalse(GameWire.valid(command.copy(action = action.copy(cardIndex = index))))
        val retained = GameWire.json.encodeToString(deck)
        val decoded = GameWire.json.decodeFromString<PreparedDeck>(retained)
        assertEquals(deck, decoded)
        val moved = move(initial.copy(players = initial.players.map { if (it.accountId == actor) it.copy(deck = decoded) else it }), action).state!!
        assertEquals(moved, GameWire.json.decodeFromString<LobbyPreparation>(GameWire.json.encodeToString(moved)))
    }

    @Test fun hugeCapturedRowsStayCompactAndKeep64BitCopyIndices() {
        val huge = deck.copy(entries = listOf(PreparedEntry("definition", SavedDeckSection.MAINBOARD, "2147483647", Int.MAX_VALUE)))
        val base = initial.copy(players = initial.players.map { if (it.accountId == actor) it.copy(deck = huge) else it })
        val captured = capture(base)
        assertEquals(Int.MAX_VALUE.toLong(), captured.count); assertEquals(1, captured.entries.size)
        val action = captured.action(captured.entries.single(), Int.MAX_VALUE - 1)
        assertEquals("4294967293", action.cardIndex)
        val changed = move(base, action).state!!
        assertEquals(2, changed.players[0].deck!!.entries.size)
        assertEquals(Int.MAX_VALUE.toLong(), PreparationRules.previewTransfer(base, actor, captured).players[0].deck!!.count(SavedDeckSection.SIDEBOARD))
    }
}
