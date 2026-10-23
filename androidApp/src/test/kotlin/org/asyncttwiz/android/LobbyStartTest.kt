package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class LobbyStartTest {
    private fun id() = UUID.randomUUID().toString()
    private val owner = AccountIdentity(id(), "Inviter")
    private val other = AccountIdentity(id(), "Recipient")
    private val game = GameSummary(id(), "Initial Spark", "2026-10-23T00:00:00Z", owner, other, id(), id(), InvitationState.ACCEPTED)
    private val definition = CardDefinition("Retained without catalog", listOf(DefinitionFace(id(), "Retained without catalog")))
    private val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
    private val source = SavedDeck(DeckSummary(id(), "Prepared source", "1", game.createdAt, 4, 2), listOf(
        SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 4), SavedDeckEntry(SavedDeckSection.SIDEBOARD, submitted.digest, 2)))
    private fun prepared(): LobbyPreparation {
        var state = PreparationRules.empty(game)
        for (player in listOf(owner, other)) state = PreparationRules.select(game, state, player.accountId, id(),
            GameScope(game.id, game.preparationRound), GameAction.SelectDeck(source.deck.id, source.deck.revision), source, listOf(submitted)).state!!
        return state.copy(starter = StarterChoice(other.accountId))
    }
    private class Worker : AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
    }
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private inner class Fixture(val base: LobbyPreparation = prepared()) : AutoCloseable {
        val wire = Wire(); var token: String? = "original"; val rejected = mutableListOf<String>()
        var httpGame = game
        val lobby = LobbyController(object : Lobbies {
            override suspend fun search(token: String, query: String) = UserSearch(emptyList())
            override suspend fun contacts(token: String, after: String?) = ContactList(emptyList())
            override suspend fun add(token: String, input: AddContact) = other
            override suspend fun create(token: String, input: CreateInvitation) = game
            override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(if (invitations) emptyList() else listOf(httpGame))
            override suspend fun head(token: String, id: String) = httpGame // Stale HTTP must not reopen the lobby.
            override fun close() = Unit
        }, { token }, Worker(), { it() }, gameConnection = wire, accountId = { owner.accountId }, authenticationRequired = { rejected.add(it); token = null })
        init { lobby.open(game.id); connect(); head(game, base) }
        fun message(value: GameServerMessage) = wire.listener.message(value)
        fun connect(statuses: List<GameRequestStatus> = emptyList(), generation: String = "1") {
            wire.listener.opened(); message(GameWelcome())
            val stream = wire.frames.mapNotNull { when (it) { is OpenGameStream -> it.streamId; is ResumeGameStream -> it.streamId; else -> null } }.last()
            message(GameStreamReady(game.id, stream, generation, statuses))
        }
        fun head(summary: GameSummary, preparation: LobbyPreparation? = null, play: PlayState? = null) =
            message(GameHead(game.id, summary.revision, summary, preparation = preparation, play = play))
        fun commands() = wire.frames.filterIsInstance<GameCommand>()
        fun result(command: GameCommand, revision: String = "1", outcome: String = "accepted") =
            message(GameResult(command.requestId, command.scope, outcome, ingressOrdinal = "1", commitRevision = if (outcome == "accepted") revision else null))
        fun initial(preparation: LobbyPreparation = base) = StartRules.initialize(game,
            preparation.copy(players = preparation.players.map { it.copy(ready = true) }), id(),
            mapOf(owner.accountId to "A".repeat(43), other.accountId to "_".repeat(42) + "8"),
            if (preparation.starter?.accountId == null) "A".repeat(43) else null)
        override fun close() { lobby.close() }
    }

    @Test fun capturedRoundAndSharedPredictionClearOnlyActorWithoutPrestartRoll() = Fixture().use { f ->
        val both = f.base.copy(players = f.base.players.map { it.copy(ready = true) })
        f.head(game.copy(revision = "1"), both)
        f.lobby.starter(StarterChoice())
        val command = f.commands().single()
        assertEquals(GameScope(game.id, game.preparationRound), command.scope); assertTrue(GameWire.valid(command))
        assertEquals(StarterChoice(), f.lobby.state.preparation!!.starter)
        assertFalse(f.lobby.state.preparation!!.players.single { it.accountId == owner.accountId }.ready)
        assertTrue(f.lobby.state.preparation!!.players.single { it.accountId == other.accountId }.ready)
        assertEquals(both, f.lobby.state.acceptedPreparation); assertNull(f.lobby.state.play)
        f.message(GameReceipt(command.requestId, command.scope, "1")); f.result(command, "2")
        assertEquals(both, f.lobby.state.acceptedPreparation); assertTrue(game.id in f.lobby.state.pendingGames)
        val changed = StartRules.change(game, both, owner.accountId, command.scope, command.action).state!!
        f.head(game.copy(revision = "2"), changed)
        assertEquals(changed, f.lobby.state.acceptedPreparation); assertTrue(f.lobby.state.pendingGames.isEmpty())
    }

    @Test fun noDeckOrUnsetStarterCannotReadyAndDisconnectedCannotSubmit() {
        for (base in listOf(PreparationRules.empty(game), prepared().copy(starter = null))) Fixture(base).use { f ->
            assertNotNull(readyReason(base, owner.accountId)); f.lobby.ready(); assertTrue(f.commands().isEmpty())
            f.wire.listener.disconnected(false); f.lobby.starter(StarterChoice(owner.accountId)); f.lobby.ready()
            assertTrue(f.commands().isEmpty())
        }
    }

    @Test fun readyAndWithdrawalPredictButOnlyRevisionCanInstallPlayAndLateReadCannotReopenLobby() = Fixture().use { f ->
        f.lobby.ready(); val ready = f.commands().single(); assertTrue(GameWire.valid(ready))
        assertTrue(f.lobby.state.preparation!!.players[0].ready); assertFalse(f.base.players[0].ready)
        f.result(ready); assertNull(f.lobby.state.play); assertEquals(GameLifecycle.LOBBY, f.lobby.state.detail!!.lifecycle)
        val accepted = StartRules.change(game, f.base, owner.accountId, ready.scope, ready.action).state!!
        f.head(game.copy(revision = "1"), accepted)
        f.lobby.withdrawReady(); val withdrawal = f.commands().last()
        assertEquals(GameAction.WithdrawReady, withdrawal.action); assertFalse(f.lobby.state.preparation!!.players[0].ready)
        f.result(withdrawal, outcome = "rejected"); assertTrue(f.lobby.state.preparation!!.players[0].ready)
        val play = f.initial()
        val active = game.copy(lifecycle = GameLifecycle.ACTIVE, playId = play.id, revision = "2")
        f.head(active, play = play)
        assertEquals(play, f.lobby.state.play); assertNull(f.lobby.state.preparation); assertNull(f.lobby.state.acceptedPreparation)
        f.head(game, f.base); f.lobby.sync()
        assertEquals(active, f.lobby.state.detail); assertEquals(play, f.lobby.state.play)
        f.lobby.ready(); f.lobby.withdrawReady(); f.lobby.starter(StarterChoice())
        assertEquals(2, f.commands().size)
    }

    @Test fun startRevisionBeforeResultDoesNotResetOrRepeatAndClosesPreparationUI() = Fixture().use { f ->
        f.lobby.prepareDeck(); assertTrue(f.lobby.state.preparing)
        f.lobby.ready(); val command = f.commands().single(); val play = f.initial()
        val active = game.copy(lifecycle = GameLifecycle.ACTIVE, playId = play.id, revision = "1")
        f.message(GameRevision(game.id, "0", "1", command.requestId, owner.accountId, command.scope, active, play = play))
        assertFalse(f.lobby.state.preparing); assertEquals(play, f.lobby.state.play)
        f.result(command); f.lobby.sync(); assertEquals(play, f.lobby.state.play)
        assertTrue(f.lobby.state.pendingGames.isEmpty()); assertEquals(1, f.commands().size)
        f.lobby.back(); assertNull(f.lobby.state.play); f.lobby.open(game.id)
        assertEquals(play, f.lobby.state.play); assertEquals(active, f.lobby.state.detail)
    }

    @Test fun uncertainReadySurvivesReauthenticationWithSameIdAndOldCallbackIsFenced() = Fixture().use { f ->
        f.lobby.ready(); val original = f.commands().single(); val stale = f.wire.listener
        f.message(GameAdmissionError("unauthorized", false)); assertEquals(listOf("original"), f.rejected)
        f.token = "renewed"; f.lobby.sync(); f.connect(listOf(GameRequestStatus(original.requestId, "notAdmitted")), "2")
        f.head(game, f.base)
        assertEquals(original.copy(generation = "2"), f.commands().last())
        stale.message(GameHead(game.id, "99", game.copy(revision = "99"), preparation = f.base))
        assertEquals("0", f.lobby.state.detail!!.revision)
        f.lobby.clear(); f.wire.listener.message(GameHead(game.id, "100", game.copy(revision = "100"), preparation = f.base))
        assertNull(f.lobby.state.play); assertNull(f.lobby.state.preparation); assertTrue(f.lobby.state.pendingGames.isEmpty())
    }

    @Test fun httpSummaryHighWaterCannotRetireReadinessBeforeItsAcceptedReplicaArrives() = Fixture().use { f ->
        f.lobby.ready(); val command = f.commands().single()
        val accepted = StartRules.change(game, f.base, owner.accountId, command.scope, command.action).state!!
        f.httpGame = game.copy(revision = "2"); f.lobby.sync()
        f.result(command)
        // Neither the leading summary nor the final result supplies the accepted preparation.
        assertEquals(f.base, f.lobby.state.acceptedPreparation)
        assertTrue(f.lobby.state.preparation!!.players[0].ready)
        assertTrue(game.id in f.lobby.state.pendingGames)
        f.message(GameRevision(game.id, "0", "1", command.requestId, owner.accountId, command.scope,
            game.copy(revision = "1"), preparation = accepted))
        // Revision 1 advances the replica from 0 without rewinding the summary at 2.
        assertEquals(accepted, f.lobby.state.acceptedPreparation)
        assertEquals(accepted, f.lobby.state.preparation); assertTrue(f.lobby.state.pendingGames.isEmpty())
        assertEquals(f.httpGame, f.lobby.state.detail)
        f.head(f.httpGame, accepted)
        assertEquals(accepted, f.lobby.state.preparation); assertTrue(f.lobby.state.pendingGames.isEmpty())
    }

    @Test fun newProcessUsesExactAcceptedInitialStateWithoutRecreatingUncertainIntent() {
        val play = Fixture().use { old -> old.lobby.ready(); old.initial() }
        Fixture().use { fresh ->
            fresh.head(game.copy(lifecycle = GameLifecycle.ACTIVE, playId = play.id, revision = "2"), play = play)
            assertEquals(play, fresh.lobby.state.play); assertTrue(fresh.commands().isEmpty())
            assertEquals(4, play.board.containers.single { it.accountId == owner.accountId && it.zone == PlayZone.LIBRARY }.cards.size)
            assertTrue(play.board.containers.filter { it.zone != PlayZone.LIBRARY }.all { it.cards.isEmpty() })
            assertTrue(play.board.players.all { it.life == "20" }); assertEquals("UNTAP", play.board.turn.step)
        }
    }

    @Test fun sameSceneBindsOwnersConcreteLibrariesAndEmptyAreasBothOrientationsWithoutCatalog() = Fixture().use { f ->
        val play = f.initial(); val active = game.copy(lifecycle = GameLifecycle.ACTIVE, playId = play.id)
        for ((width, height, landscape) in listOf(Triple(360f, 730f, false), Triple(780f, 310f, true))) {
            for (viewer in listOf(owner, other)) {
                val scene = TablePresentation.scene(active, play, viewer.accountId, width, height, landscape)
                assertEquals(2, scene.fields.size)
                assertEquals(11, scene.items.count { it.kind != TableItemKind.COUNTER })
                val counters = scene.items.filter { it.kind == TableItemKind.COUNTER }
                assertEquals(if (landscape) 12 else 0, counters.size)
                if (landscape) for (player in listOf(owner, other)) {
                    assertEquals(CounterColor.entries.toSet(), counters.filter { it.owner == player.accountId }.map { it.counter }.toSet())
                }
                for (item in scene.items) {
                    // Scrollable docks may extend offscreen; only clipped drawings are exposed.
                    for (visible in item.visible) {
                        assertTrue(visible.x >= 0 && visible.y >= 0)
                        assertTrue(visible.right <= width && visible.bottom <= height)
                        assertEquals(visible, visible.intersection(item.bounds))
                    }
                    if (item.zone == PlayZone.LIBRARY) {
                        assertEquals(4, item.cards.size); assertEquals(item.owner, play.board.cards.single { it.id == item.cards[0] }.owner)
                        assertFalse(item.label.contains(definition.name)) // Covered library never exposes its top face/name.
                    }
                }
                val piles = scene.items.filter { it.zone in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.STACK) }
                // Filled piles draw diagonal peeks; allocation, not the ink bounds, stays equal.
                assertEquals(1, piles.map { it.footprint.width to it.footprint.height }.distinct().size)
                for (pile in piles) {
                    assertEquals(scene.footprint, pile.footprint.width)
                    assertEquals(scene.footprint, pile.footprint.height)
                    assertEquals(pile.bounds, pile.bounds.intersection(pile.footprint))
                    val peek = if (pile.zone == PlayZone.LIBRARY) 8f else 0f
                    assertEquals(scene.cardWidth + peek, pile.bounds.width)
                    assertEquals(scene.cardHeight + peek, pile.bounds.height)
                }
                assertEquals(viewer.accountId, scene.fields.last().owner)
                assertEquals(play.board.containers.map { it.id }.filterNot { id -> play.board.containers.single { it.id == id }.zone == PlayZone.BATTLEFIELD }.toSet(),
                    scene.items.filter { it.zone != null }.map { it.id }.toSet())
            }
        }
    }
}
