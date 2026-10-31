package org.asyncttwiz.server.game

import java.util.UUID
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.lobby.LobbyService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameCancellationTest {
    private val json = GameWire.json
    private fun id() = UUID.randomUUID().toString()
    private fun ready(runtime: ServiceRuntime) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
    }
    private fun sql(db: TestPostgres, statement: String) { db.connection().use { it.createStatement().execute(statement) } }
    private fun number(db: TestPostgres, statement: String): Long = db.connection().use { c ->
        c.createStatement().executeQuery(statement).use { it.next(); it.getLong(1) } }
    private fun account(runtime: ServiceRuntime, name: String): AccountService.Principal {
        val accounts = AccountService(runtime); accounts.register(name, "$name@example.invalid", "synthetic password")
        return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
    }
    private fun create(runtime: ServiceRuntime, a: AccountService.Principal, b: AccountService.Principal): Pair<CreateInvitation, GameSummary> {
        val lobby = LobbyService(runtime); lobby.add(a, AddContact(id(), b.account.accountId))
        val input = CreateInvitation(id(), b.account.accountId, "Cancelled Spark")
        return input to json.decodeFromString(lobby.create(a, input).body)
    }
    private fun deck(runtime: ServiceRuntime, actor: AccountService.Principal): SavedDeck {
        val definition = CardDefinition("Retained", listOf(DefinitionFace(id(), "Face")))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition); val decks = DeckService(runtime)
        assertEquals(200, decks.submit(actor, SubmitDefinitions(id(), listOf(submitted))).status)
        return json.decodeFromString(decks.create(actor, CreateDeck(id(), "Source", listOf(
            SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 3)))).body)
    }
    private class Stream(val games: GameService, val actor: AccountService.Principal, val game: GameSummary) {
        val hello = GameHello(UUID.randomUUID().toString(), UUID.randomUUID().toString()); val connection = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, game.id, UUID.randomUUID().toString())
        fun command(action: GameAction, summary: GameSummary = game) = GameCommand(UUID.randomUUID().toString(),
            GameScope(summary.id, summary.preparationRound, if (action in listOf(GameAction.AcceptInvitation, GameAction.DeclineInvitation, GameAction.Reinvite)) summary.invitationId else null),
            action, stream.streamId, stream.generation)
        fun admit(command: GameCommand) = games.admit(actor, hello, connection, command)
        fun result(command: GameCommand) = games.results(actor, game.id, listOf(command.requestId)).statuses.single().result!!
        fun execute(command: GameCommand): GameResult { admit(command); assertTrue(games.executeOne()); return result(command) }
        fun execute(action: GameAction) = execute(command(action))
    }

    @Test fun `both orders of cancellation acceptance reinvitation preparation and start preserve accepted lifecycle`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "CancelA"); val b = account(runtime, "CancelB"); val games = GameService(runtime)
                val aDeck = deck(runtime, a); val bDeck = deck(runtime, b)
                for (cancelFirst in listOf(true, false)) {
                    for (overlap in listOf("accept", "reinvite", "select", "transfer", "start")) {
                        val (create, game) = create(runtime, a, b); val owner = Stream(games, a, game); val recipient = Stream(games, b, game)
                        owner.execute(GameAction.SelectDeck(aDeck.deck.id, aDeck.deck.revision))
                        owner.execute(GameAction.SetStarter(StarterChoice()))
                        owner.execute(GameAction.Ready)
                        val pending = games.head(a, game.id)
                        assertTrue(pending.preparation!!.players[0].ready)
                        if (overlap == "reinvite") recipient.execute(GameAction.DeclineInvitation)
                        if (overlap in listOf("select", "transfer", "start")) recipient.execute(GameAction.AcceptInvitation)
                        if (overlap == "start") recipient.execute(GameAction.SelectDeck(bDeck.deck.id, bDeck.deck.revision))
                        val before = games.head(a, game.id)
                        if (overlap in listOf("accept", "reinvite")) assertEquals(pending.preparation, before.preparation)
                        val other = when (overlap) {
                            "accept" -> recipient.command(GameAction.AcceptInvitation)
                            "reinvite" -> owner.command(GameAction.Reinvite)
                            "select" -> recipient.command(GameAction.SelectDeck(bDeck.deck.id, bDeck.deck.revision))
                            "transfer" -> before.preparation!!.players[0].deck!!.let { deck -> deck.entries[0].let { entry ->
                                owner.command(GameAction.TransferPreparedCard(deck.id, deck.card(entry, 0).id, entry.firstIndex, SavedDeckSection.MAINBOARD)) } }
                            else -> recipient.command(GameAction.Ready)
                        }
                        val otherStream = if (overlap in listOf("reinvite", "transfer")) owner else recipient
                        val cancelling = if (overlap in listOf("select", "transfer", "start")) recipient else owner
                        val cancel = cancelling.command(GameAction.CancelSetup)
                        val ordered = if (cancelFirst) listOf(cancelling to cancel, otherStream to other) else listOf(otherStream to other, cancelling to cancel)
                        ordered.forEach { (stream, command) -> assertTrue(stream.admit(command) is GameReceipt) }
                        assertTrue(games.executeOne())
                        val firstHead = if (!cancelFirst) games.head(a, game.id) else null
                        if (!cancelFirst && overlap in listOf("accept", "reinvite")) assertEquals(pending.preparation, firstHead!!.preparation)
                        assertTrue(games.executeOne()); assertFalse(games.executeOne())
                        if (overlap == "start" && !cancelFirst) {
                            assertEquals("lifecycleEnded", cancelling.result(cancel).reason)
                            assertEquals(firstHead, games.head(a, game.id)); assertNotNull(firstHead!!.play)
                        } else {
                            assertEquals("accepted", cancelling.result(cancel).outcome)
                            assertEquals(if (cancelFirst) "lifecycleEnded" else null, otherStream.result(other).reason)
                            for (actor in listOf(a, b)) {
                                assertEquals(404, LobbyService(runtime).head(actor, game.id).status)
                                assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) { games.head(actor, game.id) }.code)
                                assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) { games.revisions(actor, game.id, "0") }.code)
                                for (invites in listOf(true, false)) assertTrue(json.decodeFromString<GameList>(LobbyService(runtime).list(actor, null, invites).body).games.none { it.id == game.id })
                            }
                            assertEquals(0, number(db, "SELECT count(*) FROM game_play WHERE game_id='${game.id}'"))
                            assertEquals(game, json.decodeFromString<GameSummary>(LobbyService(runtime).create(a, create).body))
                            assertEquals(cancelling.result(cancel), cancelling.admit(cancel))
                            assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) { cancelling.admit(cancel.copy(requestId = id())) }.code)
                            val publications = games.publications().filter { it.gameId == game.id }
                            val terminal = publications.mapNotNull { it.revision }.map { json.decodeFromString<GameServerMessage>(it) as GameRevision }.last()
                            assertEquals(GameLifecycle.CANCELLED, terminal.summary.lifecycle); assertNull(terminal.preparation); assertNull(terminal.play)
                            if (overlap == "reinvite" && !cancelFirst) {
                                assertEquals(1, otherStream.result(other).createdIds.size)
                                assertEquals(otherStream.result(other), otherStream.admit(other))
                            }
                        }
                        println("PASS $overlap cancelFirst=$cancelFirst: original ordered results, no replacement/revival/reset")
                    }
                }
            }
        }
    }

    @Test fun `pending recipient and stale round cannot cancel and cancelled originals remain account intent and stream scoped`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "PrivateCancelA"); val b = account(runtime, "PrivateCancelB"); val stranger = account(runtime, "PrivateOther")
                val game = create(runtime, a, b).second; val games = GameService(runtime)
                val owner = Stream(games, a, game); val recipient = Stream(games, b, game)
                assertEquals("accessDenied", recipient.execute(GameAction.CancelSetup).reason)
                assertEquals("inapplicable", owner.execute(owner.command(GameAction.CancelSetup).copy(scope = GameScope(game.id, id()))).reason)
                assertEquals(GameLifecycle.LOBBY, games.head(a, game.id).summary.lifecycle)
                val cancel = owner.command(GameAction.CancelSetup); owner.execute(cancel)
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) { owner.admit(cancel.copy(action = GameAction.Ready)) }.code)
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) { recipient.admit(cancel.copy(streamId = recipient.stream.streamId)) }.code)
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) { games.results(b, game.id, listOf(cancel.requestId)) }.code)
                assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) { games.results(stranger, game.id, listOf(cancel.requestId)) }.code)
                assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) { games.open(a, owner.hello, UUID.randomUUID(), game.id, id()) }.code)
                val connection = UUID.randomUUID(); val missing = id()
                val resumed = games.open(a, owner.hello, connection, game.id, cancel.streamId, listOf(cancel.requestId, missing))
                assertEquals(listOf("final", "notAdmitted"), resumed.statuses.map { it.status }); assertEquals(GameLifecycle.CANCELLED, resumed.ended!!.lifecycle)
                assertEquals(owner.result(cancel), games.admit(a, owner.hello, connection, cancel.copy(generation = resumed.generation)))
                assertEquals("streamFenced", assertThrows(GameProtocolFailure::class.java) { owner.admit(cancel) }.code)
                assertEquals("streamFenced", assertThrows(GameProtocolFailure::class.java) { games.open(a, GameHello(id(), id()), UUID.randomUUID(), game.id, cancel.streamId, listOf(cancel.requestId)) }.code)
                println("PASS pending recipient/stale-round refusal; private original-only recovery after cancellation, account/intent/stream fencing and no ordinary access")
            }
        }
    }

    @Test fun `SQL rollback and production worker restart recover admitted cancellation and following original once`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var a: AccountService.Principal; lateinit var b: AccountService.Principal; lateinit var game: GameSummary
            lateinit var cancel: GameCommand; lateinit var after: GameCommand; lateinit var hello: GameHello
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime); a = account(runtime, "AtomicCancelA"); b = account(runtime, "AtomicCancelB")
                game = create(runtime, a, b).second; val games = GameService(runtime); val owner = Stream(games, a, game)
                cancel = owner.command(GameAction.CancelSetup); after = owner.command(GameAction.SetStarter(StarterChoice())); hello = owner.hello
                owner.admit(cancel); owner.admit(after)
                assertEquals("pending", games.results(a, game.id, listOf(cancel.requestId)).statuses.single().status)
                sql(db, """CREATE FUNCTION fail_cancel_commit() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN RAISE EXCEPTION 'owned cancellation fault after lifecycle result revision writes'; END ${'$'}${'$'};
                    CREATE TRIGGER fail_cancel_commit BEFORE INSERT ON game_outbox FOR EACH ROW EXECUTE FUNCTION fail_cancel_commit();""")
                assertThrows(Exception::class.java) { games.executeOne() }
                assertEquals(1, number(db, "SELECT count(*) FROM game WHERE lifecycle='LOBBY'"))
                assertEquals(0, number(db, "SELECT revision FROM game")); assertEquals(2, number(db, "SELECT count(*) FROM game_inbox WHERE result IS NULL"))
                assertEquals(0, number(db, "SELECT count(*) FROM game_revision")); assertEquals(0, number(db, "SELECT count(*) FROM game_outbox"))
                sql(db, "DROP TRIGGER fail_cancel_commit ON game_outbox; DROP FUNCTION fail_cancel_commit()")
            }
            ServiceRuntime(db.settings).use { runtime ->
                val hub = GameHub(runtime); runtime.attachWorker(hub::validatePending, hub::available)
                try {
                    hub.start(); runtime.start(); ready(runtime)
                    val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                    while (hub.games.results(a, game.id, listOf(after.requestId)).statuses.single().status != "final") {
                        assertTrue(System.nanoTime() < end); Thread.sleep(20)
                    }
                    val results = hub.games.results(a, game.id, listOf(cancel.requestId, after.requestId)).statuses.map { it.result!! }
                    assertEquals(listOf("accepted", "rejected"), results.map { it.outcome }); assertEquals("lifecycleEnded", results.last().reason)
                    val resumed = hub.games.open(a, hello, UUID.randomUUID(), game.id, cancel.streamId, listOf(cancel.requestId, after.requestId))
                    assertEquals(results, resumed.statuses.map { it.result }); assertEquals("1", resumed.ended!!.revision)
                    assertFalse(hub.games.executeOne())
                    assertEquals(1, number(db, "SELECT count(*) FROM game_revision")); assertEquals(2, number(db, "SELECT count(*) FROM game_outbox"))
                    assertEquals(2, number(db, "SELECT count(*) FROM game_inbox")); assertEquals(0, number(db, "SELECT count(*) FROM game_play"))
                } finally { hub.close() }
            }
            assertEquals(0, number(db, "SELECT count(*) FROM pg_locks WHERE locktype='advisory'"))
            println("PASS actual SQL cancellation rollback retains admitted pending originals; production worker restart commits lifecycle/revision/result/outbox once and rejects queued late preparation")
        }
    }
}
