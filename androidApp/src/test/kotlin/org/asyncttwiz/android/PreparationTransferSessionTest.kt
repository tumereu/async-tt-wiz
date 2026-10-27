package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class PreparationTransferSessionTest {
    private fun id() = UUID.randomUUID().toString()
    private val owner = AccountIdentity(id(), "Owner")
    private val other = AccountIdentity(id(), "Other")
    private val game = GameSummary(id(), "Transfers", "2026-10-22T00:00:00Z", owner, other, id(), id(), InvitationState.PENDING)
    private val deck = PreparedDeck(id(), id(), "1", "Prepared copy", null, id(), listOf(
        PreparedEntry("definition", SavedDeckSection.MAINBOARD, "0", 4), PreparedEntry("definition", SavedDeckSection.SIDEBOARD, "4", 2)))
    private val initial = PreparationRules.empty(game).copy(players = listOf(PlayerPreparation(owner.accountId, true, deck), PlayerPreparation(other.accountId, true)))
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private inner class Fixture(val base: LobbyPreparation = initial) : AutoCloseable {
        val wire = Wire(); val posts = ArrayDeque<() -> Unit>()
        var token: String? = "A".repeat(43)
        val results = mutableListOf<GameResult>(); val rejected = mutableListOf<String>()
        val session = GameSession(wire, { token }, { posts.add(it) }, rejected::add, {}, { _, result -> results.add(result) }, {})
        init { connect() }
        fun drain() { while (posts.isNotEmpty()) posts.removeFirst()() }
        fun message(message: GameServerMessage) { wire.listener.message(message); drain() }
        fun connect(statuses: List<GameRequestStatus> = emptyList(), generation: String = "1", state: LobbyPreparation = base, revision: String = "0") {
            session.watch(listOf(game)); wire.listener.opened(); drain(); message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.frames.mapNotNull { when (it) { is OpenGameStream -> it.streamId; is ResumeGameStream -> it.streamId; else -> null } }.last()
            message(GameStreamReady(game.id, stream, generation, statuses)); head(revision, state)
        }
        fun head(revision: String, state: LobbyPreparation, summary: GameSummary = game.copy(revision = revision)) =
            message(GameHead(game.id, revision, summary, preparation = state))
        fun capture(source: SavedDeckSection = SavedDeckSection.MAINBOARD, all: Boolean = true) =
            PreparationRules.capture(session.preparation(game)!!, owner.accountId, "definition", source, all)!!
        fun submit(intent: PreparedTransfer = capture()): List<GameCommand> {
            val before = commands.size
            assertTrue(session.transfer(game, owner.accountId, intent)); return commands.drop(before)
        }
        val commands get() = wire.frames.filterIsInstance<GameCommand>()
        val display get() = session.preparation(game)!!
        fun apply(base: LobbyPreparation, command: GameCommand) = PreparationRules.transfer(game, base, owner.accountId,
            command.scope, command.action as GameAction.TransferPreparedCard).state!!
        fun result(command: GameCommand, revision: String? = null, reason: String? = null) = message(GameResult(command.requestId,
            command.scope, if (reason == null) "accepted" else "rejected", reason, "1", revision))
        fun revision(command: GameCommand, number: Int, state: LobbyPreparation) = message(GameRevision(game.id,
            (number - 1).toString(), number.toString(), command.requestId, owner.accountId, command.scope,
            game.copy(revision = number.toString()), state))
        override fun close() = session.close()
    }

    @Test fun rapidIndividualRequestsSendImmediatelyAndReceiptOrResultCannotChangeAcceptedState() = Fixture().use { f ->
        val commands = f.submit()
        assertEquals(4, commands.size); assertEquals(4, commands.map { it.requestId }.distinct().size)
        assertTrue(commands.all { GameWire.valid(it) && it.scope == GameScope(game.id, game.preparationRound) })
        assertEquals(0L, f.display.players[0].deck!!.count(SavedDeckSection.MAINBOARD))
        assertEquals(initial, f.session.acceptedPreparation(game.id))
        commands.forEach { f.message(GameReceipt(it.requestId, it.scope, "1")) }
        f.result(commands[0], "1")
        assertEquals(initial, f.session.acceptedPreparation(game.id)); assertEquals(0L, f.display.players[0].deck!!.count(SavedDeckSection.MAINBOARD))
        var accepted = initial
        for ((index, command) in commands.withIndex()) {
            accepted = f.apply(accepted, command); f.revision(command, index + 1, accepted)
            if (index > 0) f.result(command, (index + 1).toString())
            assertEquals(0L, f.display.players[0].deck!!.count(SavedDeckSection.MAINBOARD))
        }
        assertEquals(accepted, f.display); assertFalse(f.session.pending(game.id)); assertEquals(initial.players[1], accepted.players[1])
        f.head("0", initial); assertEquals(accepted, f.display) // Old reads/results never rewind.
        f.result(commands[0], "1"); assertEquals(4, f.results.size)
    }

    @Test fun capturedAllExcludesArrivalsAndIndependentOrConflictingDeviceChangesRebaseWithoutSubstitution() = Fixture().use { f ->
        val captured = f.capture()
        val reverse = f.capture(SavedDeckSection.SIDEBOARD, false)
        val arrived = PreparationRules.transfer(game, initial, owner.accountId, GameScope(game.id, game.preparationRound),
            reverse.action(reverse.entries.single(), 0)).state!!
        f.head("1", arrived)
        val commands = f.submit(captured)
        assertEquals(listOf("0", "1", "2", "3"), commands.map { (it.action as GameAction.TransferPreparedCard).cardIndex })
        assertEquals(1L, f.display.players[0].deck!!.count(SavedDeckSection.MAINBOARD))
        val conflicted = f.apply(arrived, commands[1]); f.head("2", conflicted)
        assertEquals(1L, f.display.players[0].deck!!.count(SavedDeckSection.MAINBOARD)) // Only difference, no double application.
        f.result(commands[1], reason = "sourceSectionChanged")
        var accepted = conflicted
        for ((index, command) in commands.filterIndexed { index, _ -> index != 1 }.withIndex()) {
            accepted = f.apply(accepted, command); f.head((index + 3).toString(), accepted); f.result(command, (index + 3).toString())
        }
        assertEquals(accepted, f.display); assertEquals("4", f.display.players[0].deck!!.entries.single { it.section == SavedDeckSection.MAINBOARD }.firstIndex)
        assertFalse(f.session.pending(game.id))
    }

    @Test fun reversePredictionsAndRevisionBeforeResultDoNotReplayOldForwardMoveOverLaterAcceptedWork() = Fixture().use { f ->
        val forward = f.submit(f.capture(all = false)).single()
        val reverse = f.submit(f.capture(SavedDeckSection.SIDEBOARD, false)).single()
        assertEquals("0", (reverse.action as GameAction.TransferPreparedCard).cardIndex)
        val first = f.apply(initial, forward); val second = f.apply(first, reverse)
        f.revision(forward, 1, first); assertEquals(second, f.display)
        f.revision(reverse, 2, second); assertEquals(second, f.display)
        val newer = second.copy(players = second.players.map { it.copy(ready = true) })
        f.head("3", newer); f.result(reverse, "2"); f.result(forward, "1")
        assertEquals(newer, f.display); assertFalse(f.session.pending(game.id))
    }

    @Test fun rejectedConstituentWithdrawsOnlyItAndDependentInvalidPredictionButNeverRecallsOriginals() = Fixture().use { f ->
        val forward = f.submit(f.capture(all = false)).single()
        val reverse = f.submit(f.capture(SavedDeckSection.SIDEBOARD, false)).single()
        f.result(forward, reason = "preparedDeckChanged")
        assertEquals(initial, f.display)
        assertTrue(f.session.pending(game.id)); assertEquals(2, f.commands.size)
        f.result(reverse, reason = "sourceSectionChanged")
        assertFalse(f.session.pending(game.id)); assertEquals(initial, f.display)
    }

    @Test fun withdrawnPredictionStillRecoversLateAcceptanceNormally() = Fixture().use { f ->
        val command = f.submit(f.capture(all = false)).single()
        f.session.withdrawTransferPrediction(command.requestId)
        assertEquals(initial, f.display); assertTrue(f.session.pending(game.id))
        f.result(command, "1"); assertEquals(initial, f.display); assertEquals(initial, f.session.acceptedPreparation(game.id))
        val accepted = f.apply(initial, command); f.head("1", accepted)
        assertEquals(accepted, f.display); assertFalse(f.session.pending(game.id))
    }

    @Test fun reconnectAndSameAccountReauthenticationRetainOriginalOrderIdsAndPredictionWithStaleCallbacksFenced() = Fixture().use { f ->
        val commands = f.submit(); val predicted = f.display; val old = f.wire.listener
        f.message(GameAdmissionError("unauthorized", false)); assertEquals(listOf("A".repeat(43)), f.rejected)
        assertFalse(f.session.transfer(game, owner.accountId, f.capture(SavedDeckSection.SIDEBOARD)))
        f.token = "B".repeat(43)
        f.connect(commands.mapIndexed { index, command -> GameRequestStatus(command.requestId, if (index == 1) "pending" else "notAdmitted") }, "2")
        assertEquals(commands.filterIndexed { index, _ -> index != 1 }.map { it.copy(generation = "2") }, f.commands.drop(4))
        assertEquals(predicted, f.display)
        old.message(GameHead(game.id, "99", game.copy(revision = "99"), preparation = initial)); f.drain()
        assertEquals(predicted, f.display); assertEquals("0", f.session.summary(game).revision)
        val late = f.wire.listener; f.session.clear()
        late.message(GameHead(game.id, "100", game.copy(revision = "100"), preparation = initial)); f.drain()
        assertNull(f.session.preparation(game)); assertFalse(f.session.pending(game.id))
    }

    @Test fun recoveryFinalBeforeHeadKeepsDisplayUntilAcceptedRevisionAndFreshProcessHasNoOutgoingJournal() {
        lateinit var accepted: LobbyPreparation
        Fixture().use { f ->
            val commands = f.submit(); accepted = commands.fold(initial, f::apply)
            f.wire.listener.disconnected(); f.drain()
            f.connect(commands.map { GameRequestStatus(it.requestId, "final", GameResult(it.requestId, it.scope,
                "accepted", ingressOrdinal = "1", commitRevision = "4")) }, "2", accepted, "4")
            assertEquals(accepted, f.display); assertFalse(f.session.pending(game.id)); assertEquals(4, f.commands.size)
        }
        Fixture(accepted).use { fresh ->
            assertEquals(accepted, fresh.display); assertTrue(fresh.commands.isEmpty()); assertFalse(fresh.session.pending(game.id))
        }
    }

    @Test fun protocolBackpressureKeepsEntireLargeCapturedTailCompactPredictsImmediatelyAndDrainsWithoutTruncation() {
        val huge = initial.copy(players = initial.players.map { if (it.accountId == owner.accountId) it.copy(deck = deck.copy(entries =
            listOf(PreparedEntry("definition", SavedDeckSection.MAINBOARD, "0", 300)))) else it })
        Fixture(huge).use { f ->
            val firstWindow = f.submit()
            assertEquals(256, firstWindow.size); assertEquals(44L, f.session.queuedTransfers(game.id))
            assertEquals(300L, f.display.players[0].deck!!.count(SavedDeckSection.SIDEBOARD))
            f.wire.listener.disconnected(); f.drain()
            f.connect(firstWindow.map { GameRequestStatus(it.requestId, "pending") }, "2")
            assertEquals(256, f.wire.frames.filterIsInstance<ResumeGameStream>().last().requestIds.size)
            var accepted = huge
            for ((index, command) in firstWindow.withIndex()) {
                accepted = f.apply(accepted, command); f.revision(command, index + 1, accepted); f.result(command, (index + 1).toString())
            }
            assertEquals(300, f.commands.size); assertEquals(0L, f.session.queuedTransfers(game.id))
            for ((index, command) in f.commands.drop(256).withIndex()) {
                accepted = f.apply(accepted, command); f.revision(command, index + 257, accepted); f.result(command, (index + 257).toString())
            }
            assertEquals(accepted, f.display); assertFalse(f.session.pending(game.id))
            assertEquals((0..299).map(Int::toString), f.commands.map { (it.action as GameAction.TransferPreparedCard).cardIndex })
        }
        val enormous = huge.copy(players = huge.players.map { if (it.accountId == owner.accountId)
            it.copy(deck = it.deck!!.copy(entries = listOf(PreparedEntry("definition", SavedDeckSection.MAINBOARD, "0", Int.MAX_VALUE)))) else it })
        Fixture(enormous).use { f ->
            f.submit(); assertEquals(256, f.commands.size)
            assertEquals(Int.MAX_VALUE.toLong() - 256, f.session.queuedTransfers(game.id))
            assertEquals(Int.MAX_VALUE.toLong(), f.display.players[0].deck!!.count(SavedDeckSection.SIDEBOARD))
            f.session.clear(); assertEquals(0L, f.session.queuedTransfers(game.id))
        }
    }
}
