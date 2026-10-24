package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class GameSessionTest {
    private fun id() = UUID.randomUUID().toString()
    private val owner = AccountIdentity(id(), "Inviter")
    private val recipient = AccountIdentity(id(), "Recipient")
    private fun game() = GameSummary(id(), "Azure Spark", "2026-10-22T00:00:00Z", owner, recipient, id(), id(), InvitationState.PENDING)
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        val tokens = mutableListOf<String>()
        var disconnected = 0
        override fun connect(token: String, listener: GameConnection.Listener) { tokens.add(token); this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() { disconnected++ }
        override fun close() = Unit
        fun message(message: GameServerMessage) = listener.message(message)
    }
    private inner class Fixture {
        val wire = Wire()
        var token: String? = "A".repeat(43)
        val changed = mutableListOf<GameSummary>()
        val results = mutableListOf<Pair<GameCommand, GameResult>>()
        val rejected = mutableListOf<String>()
        val posts = ArrayDeque<() -> Unit>()
        var now = 0L
        val reconnects = mutableListOf<ReconnectEpisode>()
        val session = GameSession(wire, { token }, { posts.add(it) }, rejected::add, changed::add,
            { command, result -> results.add(command to result) }, {}, now = { now }, reconnectTelemetry = reconnects::add)
        fun drain() { while (posts.isNotEmpty()) posts.removeFirst()() }
        fun message(message: GameServerMessage) { wire.message(message); drain() }
        fun connect(games: List<GameSummary>, statuses: Map<String, List<GameRequestStatus>> = emptyMap(), generation: String = "1") {
            session.watch(games); wire.listener.opened(); drain(); message(GameWelcome())
            for (game in games) {
                val streamId = wire.frames.mapNotNull { when (it) {
                    is ResumeGameStream -> if (it.gameId == game.id) it.streamId else null
                    is OpenGameStream -> if (it.gameId == game.id) it.streamId else null
                    else -> null
                } }.last()
                message(GameStreamReady(game.id, streamId, generation, statuses[game.id].orEmpty()))
                message(GameHead(game.id, game.revision, game))
            }
        }
    }
    @Test fun socketReasonAndSilentLifecycleRecoveryAreRecordedThroughTheAcceptedHead() {
        val f = Fixture(); val game = game()
        f.connect(listOf(game))
        f.session.background()
        f.wire.listener.disconnected(false, "socketClose:1001:Reconnect"); f.drain()
        assertEquals("lifecycle:socketClose:1001:Reconnect", f.reconnects.single().cause)
        f.now = 10_000
        assertFalse(f.session.reconnecting(game.id)) // Lifecycle interruption alone never demands UI.
        f.session.foreground()
        assertFalse(f.session.reconnecting(game.id))
        f.now += 10
        f.wire.listener.opened(); f.drain(); f.message(GameWelcome())
        val resume = f.wire.frames.filterIsInstance<ResumeGameStream>().single()
        f.now += 20; f.message(GameStreamReady(game.id, resume.streamId, "2"))
        assertFalse(f.reconnects.last().complete)
        f.now += 30; f.message(GameHead(game.id, game.revision, game))
        assertTrue(f.reconnects.last().complete)
        assertEquals(listOf("lifecycleForeground", "socketConnectStart", "socketConnectedHttpAuthenticated", "protocolWelcome",
            "streamResumeStart", "streamAuthorized", "ready"), f.reconnects.last().steps.map { it.stage })
        assertTrue(f.session.ready(game.id))
    }
    @Test fun credentialChangeRecordsARecoveryWithoutWaitingForASocketFailure() {
        val f = Fixture(); val game = game(); f.connect(listOf(game))
        val old = f.wire.listener
        f.token = "B".repeat(43)
        f.session.tick()
        assertEquals("credentialChanged", f.reconnects.last().cause)
        assertEquals("socketConnectStart", f.reconnects.last().steps.last().stage)
        val count = f.reconnects.size
        old.disconnected(false, "staleSocketFailure"); f.drain()
        assertEquals(count, f.reconnects.size)
        assertFalse(f.session.reconnecting(game.id))
    }
    @Test fun multipleInFlightCapturedIdentityNoOfflineActionsAndResultsNeverRewindRevision() {
        val f = Fixture(); val a = game(); val b = game()
        assertFalse(f.session.submit(a, GameAction.AcceptInvitation))
        f.connect(listOf(a, b))
        assertTrue(f.session.submit(a, GameAction.AcceptInvitation)); assertTrue(f.session.submit(b, GameAction.DeclineInvitation))
        assertFalse(f.session.submit(a, GameAction.DeclineInvitation))
        val commands = f.wire.frames.filterIsInstance<GameCommand>()
        assertEquals(listOf(a.id, b.id), commands.map { it.scope.gameId })
        assertEquals(a.invitationId, commands[0].scope.invitationId)
        f.message(GameReceipt(commands[0].requestId, commands[0].scope, "1")); assertTrue(f.session.pending(a.id))
        val accepted = a.copy(invitationState = InvitationState.ACCEPTED, revision = "9007199254740993")
        f.message(GameHead(a.id, accepted.revision, accepted))
        f.message(GameResult(commands[0].requestId, commands[0].scope, "accepted", ingressOrdinal = "1", commitRevision = "1"))
        f.message(GameHead(a.id, "0", a))
        assertEquals(accepted, f.session.summary(a)); assertEquals(accepted, f.changed.last())
        assertFalse(f.session.pending(a.id)); assertTrue(f.session.pending(b.id)); assertEquals(1, f.results.size)
        f.message(GameResult(commands[0].requestId, commands[0].scope, "accepted", ingressOrdinal = "1"))
        assertEquals(1, f.results.size)
    }
    @Test fun fencedResumeRetriesOnlyNotAdmittedInOriginalOrderAndKeepsPendingUntilFinal() {
        val f = Fixture(); val a = game(); val b = game(); val c = game()
        f.connect(listOf(a, b, c)); listOf(a, b, c).forEach { assertTrue(f.session.submit(it, GameAction.DeclineInvitation)) }
        val originals = f.wire.frames.filterIsInstance<GameCommand>()
        f.wire.listener.disconnected(); f.drain(); assertFalse(f.session.submit(game(), GameAction.DeclineInvitation))
        f.connect(listOf(a, b, c), mapOf(a.id to listOf(GameRequestStatus(originals[0].requestId, "notAdmitted")),
            b.id to listOf(GameRequestStatus(originals[1].requestId, "pending")),
            c.id to listOf(GameRequestStatus(originals[2].requestId, "final", GameResult(originals[2].requestId, originals[2].scope,
                "accepted", ingressOrdinal = "3", commitRevision = "1")))), "2")
        val retried = f.wire.frames.filterIsInstance<GameCommand>().drop(3)
        assertEquals(listOf(originals[0].copy(generation = "2")), retried)
        assertEquals(GameWire.digest(originals[0]), GameWire.digest(retried.single()))
        assertTrue(f.session.pending(a.id)); assertTrue(f.session.pending(b.id)); assertFalse(f.session.pending(c.id))
        assertEquals(listOf(a, b, c).map { it.id }, f.wire.frames.filterIsInstance<ResumeGameStream>().takeLast(3).map { it.gameId })
    }
    @Test fun sameAccountReauthKeepsOriginalButClearAndFreshProcessNeverReconstructQueueOrAcceptOldCallbacks() {
        val f = Fixture(); val a = game(); f.connect(listOf(a)); f.session.submit(a, GameAction.AcceptInvitation)
        val original = f.wire.frames.filterIsInstance<GameCommand>().single(); val old = f.wire.listener
        f.message(GameAdmissionError("unauthorized", false)); assertEquals(listOf("A".repeat(43)), f.rejected)
        f.token = null; f.session.tick(); assertFalse(f.session.ready)
        f.token = "B".repeat(43)
        f.connect(listOf(a), mapOf(a.id to listOf(GameRequestStatus(original.requestId, "notAdmitted"))), "2")
        assertEquals(original.copy(generation = "2"), f.wire.frames.filterIsInstance<GameCommand>().last())
        old.message(GameHead(a.id, "4", a.copy(revision = "4"))); f.drain(); assertEquals("0", f.session.summary(a).revision)
        val late = f.wire.listener; f.session.clear()
        late.message(GameHead(a.id, "5", a.copy(revision = "5"))); f.drain()
        assertFalse(f.session.pending(a.id)); assertFalse(f.session.ready(a.id)); assertEquals(a, f.session.summary(a))
        val openCount = f.wire.frames.filterIsInstance<OpenGameStream>().size
        f.connect(listOf(a)); assertEquals(openCount + 1, f.wire.frames.filterIsInstance<OpenGameStream>().size)
        val fresh = Fixture(); fresh.connect(listOf(a)); assertTrue(fresh.wire.frames.filterIsInstance<GameCommand>().isEmpty())
    }
    @Test fun terminalUnadmittedDoesNotBlockIndependentLaterWorkAndUnavailableIsNeverARejection() {
        val f = Fixture(); val a = game(); val b = game(); f.connect(listOf(a, b))
        f.session.submit(a, GameAction.AcceptInvitation)
        val command = f.wire.frames.filterIsInstance<GameCommand>().single()
        f.message(GameAdmissionError("requestIdentityMismatch", false, command.requestId))
        assertFalse(f.session.pending(a.id)); assertTrue(f.session.submit(b, GameAction.DeclineInvitation))
        f.message(GameAdmissionError("storageUnavailable", true))
        assertTrue(f.session.pending(b.id)); assertTrue(f.results.isEmpty())
    }
}
