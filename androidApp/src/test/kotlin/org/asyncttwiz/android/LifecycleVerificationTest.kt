package org.asyncttwiz.android

import java.util.UUID
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** Independent host assertions for the saved lifecycle candidate; no rendered/native claim. */
class LifecycleVerificationTest {
    private fun id() = UUID.randomUUID().toString()

    @Test fun terminalRecoveryNeverResendsEvenNotAdmittedAndLateMessagesCannotRestoreContent() {
        for (terminal in listOf(GameLifecycle.CANCELLED, GameLifecycle.DELETED)) {
            for (status in listOf("final", "pending", "notAdmitted")) {
                val wire = CancellationFixture.Wire()
                val owner = AccountIdentity(id(), "Owner")
                val peer = AccountIdentity(id(), "Peer")
                val active = terminal == GameLifecycle.DELETED
                val game = GameSummary(id(), "Original", "date", owner, peer, id(), id(), InvitationState.ACCEPTED,
                    if (active) GameLifecycle.ACTIVE else GameLifecycle.LOBBY, playId = if (active) id() else null)
                val settled = mutableListOf<GameResult>()
                GameSession(wire, { "token" }, { it() }, {}, {}, { _, result -> settled.add(result) }, {}).use { session ->
                    fun receive(message: GameServerMessage) = wire.listener.message(message)
                    session.watch(listOf(game)); wire.listener.opened(); receive(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
                    val stream = wire.frames.filterIsInstance<OpenGameStream>().single()
                    receive(GameStreamReady(game.id, stream.streamId, "1"))
                    receive(GameHead(game.id, "0", game,
                        preparation = if (active) null else PreparationRules.empty(game)))
                    assertTrue(if (active) session.sendChat(game, "original text") else session.submit(game, GameAction.CancelSetup))
                    val original = wire.frames.filterIsInstance<GameCommand>().single()
                    val subscriptions = wire.frames.filterIsInstance<SubscribeGame>().size
                    wire.listener.disconnected(); session.tick(); wire.listener.opened(); receive(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
                    assertEquals(listOf(original.requestId), wire.frames.filterIsInstance<ResumeGameStream>().single().requestIds)
                    val result = GameResult(original.requestId, original.scope, "accepted", ingressOrdinal = "1", commitRevision = "1")
                    val removed = game.copy(lifecycle = terminal, revision = "2")
                    receive(GameStreamReady(game.id, stream.streamId, "2",
                        listOf(GameRequestStatus(original.requestId, status, if (status == "final") result else null)), removed))
                    assertEquals(status == "pending", session.pending(game.id))
                    assertEquals(if (status == "final") listOf(result) else emptyList(), settled)
                    assertFalse(session.available(game.id)); assertFalse(session.ready(game.id))
                    // A stale snapshot and a delayed chat revision cannot overwrite terminal metadata.
                    val chat = ChatMessage(original.requestId, game.id, game.playId ?: id(), owner.accountId, "stale private text", "1", "date")
                    receive(GameHead(game.id, "0", game,
                        preparation = if (active) null else PreparationRules.empty(game), chat = if (active) listOf(chat) else emptyList()))
                    receive(GameRevision(game.id, "0", "1", original.requestId, owner.accountId, original.scope,
                        game.copy(revision = "1"), chatMessage = if (active) chat else null))
                    receive(result)
                    assertEquals(if (status == "notAdmitted") emptyList() else listOf(result), settled)
                    assertFalse(session.pending(game.id)); assertEquals(removed, session.summary(game))
                    assertNull(session.acceptedPreparation(game.id)); assertNull(session.preparation(game)); assertNull(session.play(game))
                    assertTrue(session.chat(game.id).isEmpty()); assertTrue(session.outgoingChat(game.id).isEmpty())
                    assertFalse(session.sendChat(game, "new intent")); assertFalse(session.submit(game, GameAction.CancelSetup))
                    assertFalse(session.deleteGame(game))
                    session.watch(listOf(game)); session.tick()
                    assertEquals(listOf(original), wire.frames.filterIsInstance<GameCommand>())
                    assertEquals(subscriptions, wire.frames.filterIsInstance<SubscribeGame>().size)
                    assertEquals(1, wire.frames.filterIsInstance<OpenGameStream>().size)
                }
            }
        }
    }

    @Test fun switchingLifecycleContextsAndBackNeverDispatchesAndConfirmationKeepsCapturedScope() = CancellationFixture().use { f ->
        f.lobby.requestCancelSetup(f.game)
        assertEquals(f.game, f.lobby.setupCancellation.confirmation)
        val play = f.play()
        val active = f.game.copy(lifecycle = GameLifecycle.ACTIVE, playId = play.id, revision = "1")
        f.head(active, play = play)
        assertNull(f.lobby.setupCancellation.confirmation)
        f.lobby.gameContext(active); f.lobby.back()
        assertNull(f.lobby.state.gameContext)
        f.lobby.requestGameDeletion(active); f.lobby.back()
        assertNull(f.lobby.state.deletion)
        f.lobby.openChat(); f.lobby.composeChat("unsent"); f.lobby.back()
        assertFalse(f.lobby.state.chatOpen); assertEquals(active.id, f.lobby.state.selected)
        assertEquals(play, f.lobby.state.play); assertTrue(f.commands().isEmpty())
        f.lobby.requestGameDeletion(active)
        // Advance the actual replica with a non-board record before its periodic head.
        val chat = ChatMessage(id(), active.id, play.id, f.owner.accountId, "same incarnation", "2", "date")
        f.message(GameRevision(active.id, "1", "2", chat.id, f.owner.accountId, GameScope(active.id, playId = play.id),
            active.copy(revision = "2"), play = play, chatMessage = chat))
        f.message(GameHead(active.id, "2", active.copy(revision = "2"), play = play, chat = listOf(chat)))
        assertTrue(f.lobby.confirmGameDeletion())
        assertEquals(GameScope(active.id, playId = play.id), f.commands().single().scope)
        assertEquals(GameAction.DeleteGame, f.commands().single().action)
        assertEquals(play, f.lobby.state.play) // Request is not predicted shared deletion.
    }

    @Test fun setupAndActiveRowsReplaceEachOthersPersonalContextWithoutRetargetingConfirmation() = CancellationFixture().use { f ->
        val other = f.game.copy(id = id(), lifecycle = GameLifecycle.ACTIVE, playId = id(), revision = "1")
        f.head(other)
        f.lobby.requestCancelSetup(f.game)
        f.lobby.gameContext(other)
        assertNull(f.lobby.setupCancellation.confirmation)
        assertEquals(other, f.lobby.state.gameContext)
        f.lobby.requestGameDeletion(other)
        assertNull(f.lobby.state.gameContext); assertEquals(other, f.lobby.state.deletion?.game)
        f.lobby.showGameContext(f.game)
        assertNull(f.lobby.state.deletion); assertEquals(f.game, f.lobby.setupCancellation.context)
        f.lobby.requestGameDeletion(other)
        assertNull(f.lobby.setupCancellation.context)
        f.lobby.requestCancelSetup(f.game)
        assertNull(f.lobby.state.gameContext); assertNull(f.lobby.state.deletion)
        assertTrue(f.commands().isEmpty())
        f.lobby.confirmCancelSetup()
        assertEquals(GameScope(f.game.id, f.game.preparationRound), f.commands().single().scope)
        assertEquals(GameAction.CancelSetup, f.commands().single().action)
        assertEquals(f.game.id, f.lobby.state.selected)
    }
}
