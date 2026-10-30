package org.asyncttwiz.protocol

import kotlin.test.*

class ShowDeckTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = AccountIdentity(id(1), "A")
    private val b = AccountIdentity(id(2), "B")
    private val lobby = GameSummary(id(3), "Show", "2026-10-30T00:00:00Z", a, b, id(4), id(5), InvitationState.ACCEPTED)
    private val scope = GameScope(lobby.id, lobby.preparationRound)
    private val ready = PreparationRules.empty(lobby).copy(players = listOf(PlayerPreparation(a.accountId, true),
        PlayerPreparation(b.accountId, true, shownPodDeckName = null)))

    @Test fun showDeckTogglesOnlyTheActorsOwnFlagAndNeverWithdrawsReady() {
        val shown = StartRules.change(lobby, ready, b.accountId, scope, GameAction.ShowDeck(true)).state!!
        assertEquals(listOf(PlayerPreparation(a.accountId, true), PlayerPreparation(b.accountId, true, deckShown = true)), shown.players)
        val withName = shown.copy(players = shown.players.map { if (it.accountId == b.accountId) it.copy(shownPodDeckName = "Secret") else it })
        val hidden = StartRules.change(lobby, withName, b.accountId, scope, GameAction.ShowDeck(false)).state!!
        // Hiding again drops the pod name from the shared state at once.
        assertEquals(ready.players, hidden.players)
    }

    @Test fun showDeckFollowsLobbyAccessAndLifecycle() {
        val pending = lobby.copy(invitationState = InvitationState.PENDING)
        assertEquals("accessDenied", StartRules.change(pending, ready, b.accountId, scope, GameAction.ShowDeck(true)).reason)
        assertEquals("accessDenied", StartRules.change(lobby, ready, id(9), scope, GameAction.ShowDeck(true)).reason)
        assertEquals("lifecycleEnded", StartRules.change(lobby.copy(lifecycle = GameLifecycle.ACTIVE, playId = id(6)), ready,
            a.accountId, scope, GameAction.ShowDeck(true)).reason)
    }

    @Test fun commandIsAPreparationScopedLobbyActionAndHiddenDefaultsAreOmitted() {
        val command = GameCommand(id(16), scope, GameAction.ShowDeck(true), id(11), "1")
        assertTrue(GameWire.valid(command))
        assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
        assertFalse(GameWire.valid(command.copy(scope = GameScope(lobby.id, lobby.preparationRound, lobby.invitationId))))
        assertFalse(GameWire.valid(command.copy(scope = GameScope(lobby.id, playId = id(6)))))
        val encoded = GameWire.json.encodeToString(ready)
        assertFalse("deckShown" in encoded); assertFalse("shownPodDeckName" in encoded)
        val shown = ready.copy(players = ready.players.map { it.copy(deckShown = true, shownPodDeckName = "Secret") })
        assertEquals(shown, GameWire.json.decodeFromString<LobbyPreparation>(GameWire.json.encodeToString(shown)))
    }
}
