package org.asyncttwiz.server.game

import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.lobby.LobbyService
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameResultLifecycleTest {
    private fun id() = UUID.randomUUID().toString()
    private fun sql(db: OwnedPostgres, statement: String) { db.connection().use { it.createStatement().execute(statement) } }
    private fun count(db: OwnedPostgres, statement: String): Long = db.connection().use { connection ->
        connection.createStatement().executeQuery(statement).use { it.next(); it.getLong(1) } }
    private fun account(runtime: ServiceRuntime, name: String): AccountService.Principal {
        val accounts = AccountService(runtime)
        accounts.register(name, "$name@example.invalid", "synthetic password")
        return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
    }
    private fun deck(runtime: ServiceRuntime, actor: AccountService.Principal): SavedDeck {
        val definition = CardDefinition("Prepared original", listOf(DefinitionFace(id(), "Original", corner = "3/4")))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val decks = DeckService(runtime)
        assertEquals(200, decks.submit(actor, SubmitDefinitions(id(), listOf(submitted))).status)
        return GameWire.json.decodeFromString(decks.create(actor, CreateDeck(id(), "Last selected", listOf(
            SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 5),
            SavedDeckEntry(SavedDeckSection.SIDEBOARD, submitted.digest, 2)))).body)
    }
    private inner class Stream(val games: GameService, val actor: AccountService.Principal, val gameId: String) {
        val hello = GameHello(id(), id()); val connection = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, gameId, id())
        fun command(action: GameAction, scope: GameScope? = null): GameCommand {
            val summary = games.head(actor, gameId).summary
            return GameCommand(id(), scope ?: if (action is GameAction.Play || action is GameAction.AcceptResult)
                GameScope(gameId, playId = summary.playId) else GameScope(gameId, summary.preparationRound,
                    if (action == GameAction.AcceptInvitation) summary.invitationId else null), action, stream.streamId, stream.generation)
        }
        fun admit(command: GameCommand) = games.admit(actor, hello, connection, command)
        fun result(command: GameCommand) = games.results(actor, gameId, listOf(command.requestId)).statuses.single().result!!
        fun execute(command: GameCommand): GameResult { assertTrue(admit(command) is GameReceipt); assertTrue(games.executeOne()); return result(command) }
        fun execute(action: GameAction) = execute(command(action))
    }
    private inner class Fixture(val db: OwnedPostgres) : AutoCloseable {
        val runtime = ServiceRuntime(db.settings)
        val a: AccountService.Principal; val b: AccountService.Principal
        val game: GameSummary; val games: GameService; val owner: Stream; val other: Stream
        val source: SavedDeck; val preparation: LobbyPreparation; val initial: PlayState
        val firstReady: GameCommand
        init {
            runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
            a = account(runtime, "ResultA"); b = account(runtime, "ResultB")
            val lobby = LobbyService(runtime); lobby.add(a, AddContact(id(), b.account.accountId))
            game = GameWire.json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Result Spark")).body)
            games = GameService(runtime); owner = Stream(games, a, game.id); other = Stream(games, b, game.id)
            source = deck(runtime, a); val bSource = deck(runtime, b)
            owner.execute(GameAction.SelectDeck(source.deck.id, source.deck.revision))
            other.execute(GameAction.AcceptInvitation); other.execute(GameAction.SelectDeck(bSource.deck.id, bSource.deck.revision))
            // A real lobby partition edit is part of the last preparation, not just the source snapshot.
            val selected = games.head(a, game.id).preparation!!.players[0].deck!!
            val entry = selected.entries.first()
            owner.execute(GameAction.TransferPreparedCard(selected.id, selected.card(entry, 0).id, entry.firstIndex, SavedDeckSection.MAINBOARD))
            owner.execute(GameAction.SetStarter(StarterChoice()))
            firstReady = owner.command(GameAction.Ready); owner.execute(firstReady)
            preparation = games.head(a, game.id).preparation!!
            other.execute(GameAction.Ready); initial = games.head(a, game.id).play!!
        }
        fun suggestion(author: Stream = owner, result: SuggestedResult = SuggestedResult.WIN) = author.command(GameAction.Play(PlayAction.SuggestResult(result)))
        override fun close() { runtime.close() }
    }

    @Test fun `first applicable suggestion wins both arrival orders and return fences old round and play while fresh start records new inputs`() {
        for (reverse in listOf(false, true)) OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            Fixture(db).use { f ->
                val author = if (reverse) f.other else f.owner; val accepter = if (reverse) f.owner else f.other
                val first = f.suggestion(author); val discarded = f.suggestion(accepter, SuggestedResult.DRAW)
                author.admit(first); accepter.admit(discarded); assertTrue(f.games.executeOne()); assertTrue(f.games.executeOne())
                val original = author.result(first); val rejected = accepter.result(discarded)
                assertEquals("accepted", original.outcome); assertEquals("inapplicable", rejected.reason)
                val proposed = f.games.head(f.a, f.game.id).play!!; val suggestion = proposed.board.resultSuggestion!!
                assertEquals(author.actor.account.accountId, suggestion.author); assertEquals(SuggestedResult.WIN, suggestion.outcome)
                assertEquals("1", proposed.currentGroup!!.count); assertEquals(HistoryCategory.S.key, proposed.currentGroup!!.category)
                assertEquals(rejected, accepter.admit(discarded)); assertEquals(1, count(db, "SELECT count(*) FROM gameplay_mutation"))
                assertEquals("otherPlayerRequired", author.execute(GameAction.AcceptResult(suggestion.id)).reason)
                assertEquals("inapplicable", accepter.execute(GameAction.AcceptResult(id())).reason)
                // Mutate actual board state through the shared/server adapter; restoration must ignore it.
                f.owner.execute(GameAction.Play(PlayAction.SetLife(f.a.account.accountId, "1")))
                f.owner.execute(GameAction.Play(PlayAction.SetTapped(f.initial.board.cards.first().id, true)))
                val decks = DeckService(f.runtime)
                assertEquals(200, decks.update(f.a, f.source.deck.id, UpdateDeck(id(), "Changed saved source", contents = DeckContentsImport(
                    listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, f.source.entries[0].definition, 1))))).status)
                val oldReady = f.owner.command(GameAction.Ready, GameScope(f.game.id, f.game.preparationRound))
                val oldPlay = author.command(GameAction.Play(PlayAction.SuggestResult(SuggestedResult.LOSS)))
                val accept = accepter.command(GameAction.AcceptResult(suggestion.id)); val accepted = accepter.execute(accept)
                val returned = f.games.head(f.a, f.game.id); val prepared = returned.preparation!!
                assertEquals(GameLifecycle.LOBBY, returned.summary.lifecycle); assertNull(returned.play); assertNull(returned.summary.playId)
                assertNotEquals(f.game.preparationRound, prepared.roundId)
                assertTrue(prepared.players.all { !it.ready })
                assertEquals(f.preparation.players.map { it.deck }, prepared.players.map { it.deck })
                assertEquals(f.preparation.definitions, prepared.definitions)
                assertEquals(returned, f.games.head(f.b, f.game.id))
                assertEquals(accepted, accepter.admit(accept)); assertEquals(original, author.admit(first))
                assertEquals(rejected, accepter.admit(discarded)); assertEquals(returned, f.games.head(f.a, f.game.id))
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) {
                    f.games.results(author.actor, f.game.id, listOf(accept.requestId)) }.code)
                assertEquals("inapplicable", f.owner.execute(oldReady).reason)
                assertEquals("lifecycleEnded", author.execute(oldPlay).reason)
                assertEquals("lifecycleEnded", accepter.execute(accept.copy(requestId = id())).reason)
                // Explicitly select Random again: cross-game starter retention is not selected by this test.
                f.owner.execute(GameAction.SetStarter(StarterChoice())); f.owner.execute(GameAction.Ready)
                assertNull(f.games.head(f.a, f.game.id).play)
                val start = f.other.command(GameAction.Ready); val startResult = f.other.execute(start)
                val freshHead = f.games.head(f.a, f.game.id); val fresh = freshHead.play!!
                assertNotEquals(f.initial.id, fresh.id); assertNotEquals(f.initial.root, fresh.root)
                assertEquals(fresh.root.id, fresh.currentNodeId); assertNull(fresh.currentGroup); assertNull(fresh.board.resultSuggestion)
                assertTrue(fresh.board.players.all { it.life == "20" && it.counters.isEmpty() })
                assertTrue(fresh.board.cards.all { !it.tapped && it.covered && it.note.isEmpty() && !it.token })
                assertTrue(fresh.board.containers.filter { it.zone != PlayZone.LIBRARY }.all { it.cards.isEmpty() })
                assertEquals(TurnState(fresh.starter.accountId), fresh.board.turn)
                assertEquals(6, (f.initial.shuffles.map { it.seed } + f.initial.starter.seed!! + fresh.shuffles.map { it.seed } + fresh.starter.seed!!).distinct().size)
                for (player in prepared.players) {
                    val deck = player.deck!!
                    val members = deck.entries.filter { it.section == SavedDeckSection.MAINBOARD }.flatMap { entry -> List(entry.quantity) { deck.card(entry, it).id } }
                    val shuffle = fresh.shuffles.single { it.accountId == player.accountId }
                    assertEquals(members.toSet(), shuffle.order.toSet()); assertEquals(RandomV1.shuffle(members, shuffle.seed), shuffle.order)
                }
                assertEquals(startResult, f.other.admit(start)); assertEquals(freshHead, f.games.head(f.b, f.game.id))
                assertEquals("inapplicable", author.execute(oldPlay.copy(requestId = id())).reason)
                assertEquals("inapplicable", accepter.execute(accept.copy(requestId = id())).reason)
                assertEquals(freshHead, f.games.head(f.a, f.game.id))
                assertEquals(original, author.admit(first)); assertEquals(accepted, accepter.admit(accept))
                assertEquals(2, count(db, "SELECT count(*) FROM game_play"))
                assertEquals(1, count(db, "SELECT count(*) FROM game_play WHERE ended_request_id IS NOT NULL"))
                assertEquals("Changed saved source", GameWire.json.decodeFromString<SavedDeck>(decks.read(f.a, f.source.deck.id).body).deck.name)
                println("PASS arrival reverse=$reverse: exact shared singleton/first applicable/private discarded originals; other-player exact acceptance; immutable last partition/definitions and both Ready reset; stale preparation/play/dialog fences; two new Ready and independent recorded shuffles/Random; no old-result reset or duplicate return")
            }
        }
    }

    @Test fun `failed return rolls back atomically and admitted acceptance recovers after restart without duplicate effect`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var accept: GameCommand; lateinit var before: GameHead; lateinit var actor: AccountService.Principal
            lateinit var author: AccountService.Principal; lateinit var hello: GameHello; lateinit var gameId: String
            Fixture(db).use { f ->
                f.owner.execute(f.suggestion()); before = f.games.head(f.a, f.game.id)
                accept = f.other.command(GameAction.AcceptResult(before.play!!.board.resultSuggestion!!.id))
                actor = f.b; author = f.a; hello = f.other.hello; gameId = f.game.id
                f.other.admit(accept)
                sql(db, """CREATE FUNCTION fail_return() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN RAISE EXCEPTION 'owned return rollback after preparation play revision and result writes'; END ${'$'}${'$'};
                    CREATE TRIGGER fail_return BEFORE INSERT ON game_outbox FOR EACH ROW EXECUTE FUNCTION fail_return();""")
                assertThrows(Exception::class.java) { f.games.executeOne() }
                // An unexpected SQL failure correctly fences this runtime's writer. Inspect rollback
                // through the owned database, then recover using a fresh runtime below.
                assertEquals(1, count(db, "SELECT count(*) FROM game WHERE id='$gameId' AND lifecycle='ACTIVE' AND revision=${before.revision} AND preparation_round='${before.summary.preparationRound}' AND current_play='${before.play!!.id}'"))
                assertEquals(1, count(db, "SELECT count(*) FROM game_inbox WHERE request_id='${accept.requestId}' AND result IS NULL"))
                assertEquals(before.revision.toLong(), count(db, "SELECT count(*) FROM game_revision"))
                assertEquals(before.revision.toLong(), count(db, "SELECT count(*) FROM game_outbox"))
                assertEquals(2, count(db, "SELECT count(*) FROM player_preparation WHERE ready"))
                assertEquals(0, count(db, "SELECT count(*) FROM game_play WHERE ended_request_id IS NOT NULL"))
                sql(db, "DROP TRIGGER fail_return ON game_outbox; DROP FUNCTION fail_return()")
            }
            ServiceRuntime(db.settings).use { runtime ->
                val games = GameService(runtime); runtime.attachWorker(games::validatePending) { true }; runtime.start()
                val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
                assertTrue(games.executeOne()); assertFalse(games.executeOne())
                val returned = games.head(author, gameId); assertNull(returned.play); assertTrue(returned.preparation!!.players.none { it.ready })
                assertEquals(before.revision.toLong() + 1, returned.revision.toLong())
                val original = games.results(actor, gameId, listOf(accept.requestId)).statuses.single().result!!
                val connection = UUID.randomUUID(); val resumed = games.open(actor, hello, connection, gameId, accept.streamId, listOf(accept.requestId))
                assertEquals(original, resumed.statuses.single().result)
                assertEquals(original, games.admit(actor, hello, connection, accept.copy(generation = resumed.generation)))
                assertEquals(returned, games.head(actor, gameId)); assertEquals(1, count(db, "SELECT count(*) FROM game_play"))
            }
            assertEquals(0, count(db, "SELECT count(*) FROM pg_locks WHERE locktype='advisory'"))
            println("PASS PostgreSQL rollback and restart: preparation round/Ready/current play/end marker/revision/result/outbox atomic; admitted exact original survives runtime restart, return executes once, renewed stream recovers private original without reset")
        }
    }

    @Test fun `V14 migration preserves preexisting play preparation and request data`() {
        OwnedPostgres().start().use { db ->
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("9").load().migrate()
            val a = id(); val b = id(); val gameId = id(); val round = id(); val invitation = id(); val request = id(); val stream = id()
            val definition = CardDefinition("V9 retained", listOf(DefinitionFace(id(), "V9 face")))
            val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
            val summary = GameSummary(gameId, "Retained result", "2026-10-23T00:00:00Z", AccountIdentity(a, "A"), AccountIdentity(b, "B"),
                round, invitation, InvitationState.ACCEPTED)
            val players = listOf(a, b).map { actor -> PlayerPreparation(actor, true, PreparedDeck(id(), id(), "1", "V9 deck", null, id(),
                listOf(PreparedEntry(submitted.digest, SavedDeckSection.MAINBOARD, "0", 5), PreparedEntry(submitted.digest, SavedDeckSection.SIDEBOARD, "5", 2)))) }
            val prepared = LobbyPreparation(gameId, round, players, listOf(submitted), StarterChoice(a))
            val play = StartRules.initialize(summary, prepared, request, mapOf(a to "A".repeat(43), b to "_".repeat(42) + "8"))
            val command = GameCommand(request, GameScope(gameId, round), GameAction.Ready, stream, "1")
            val original = GameResult(request, command.scope, "accepted", ingressOrdinal = "1", commitRevision = "1", createdIds = listOf(play.id, play.root.id))
            sql(db, """INSERT INTO account(id,username,username_key,email_delivery,email_key,password_phc) VALUES
                ('$a','A','a','a@example.invalid','a@example.invalid','retained'),('$b','B','b','b@example.invalid','b@example.invalid','retained');
                INSERT INTO definition VALUES ('${submitted.digest}',1,'${DeckJson.canonical(definition)}');
                BEGIN;
                INSERT INTO game(id,game_name,inviter_id,recipient_id,preparation_round,current_invitation,next_ingress,revision,starter_choice)
                VALUES ('$gameId','Retained result','$a','$b','$round','$invitation',2,1,'${GameWire.json.encodeToString(prepared.starter)}');
                INSERT INTO invitation VALUES ('$invitation','$gameId','ACCEPTED'); COMMIT;
                INSERT INTO request_identity VALUES ('$request','$b','gameCommand','$gameId','${GameWire.digest(command)}');
                INSERT INTO game_inbox VALUES ('$request','$gameId','$b',1,'$stream',1,'${GameWire.json.encodeToString<GameClientMessage>(command)}','${GameWire.json.encodeToString(original)}');
                INSERT INTO game_play(id,game_id,preparation_round,start_request_id,root_id,payload)
                VALUES ('${play.id}','$gameId','$round','$request','${play.root.id}','${GameWire.json.encodeToString(play)}');
                UPDATE game SET lifecycle='ACTIVE', current_play='${play.id}' WHERE id='$gameId';
                INSERT INTO game_definition VALUES ('$gameId','${submitted.digest}');""")
            for (player in players) sql(db, "INSERT INTO player_preparation VALUES ('$gameId','${player.accountId}',true,'${GameWire.json.encodeToString(player.deck)}')")
            val forwards = FoundationMigrations.flyway(db.settings).info().pending().size
            assertTrue(forwards > 0)
            assertEquals(forwards, FoundationMigrations.migrate(db.settings)); assertEquals(0, FoundationMigrations.migrate(db.settings))
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
                val principal = AccountService.Principal(UUID.randomUUID(), AccountIdentity(a, "A"))
                val recipient = AccountService.Principal(UUID.randomUUID(), AccountIdentity(b, "B"))
                val games = GameService(runtime); assertEquals(play, games.head(principal, gameId).play)
                assertEquals(original, games.results(recipient, gameId, listOf(request)).statuses.single().result)
                val author = Stream(games, principal, gameId); val other = Stream(games, recipient, gameId)
                author.execute(GameAction.Play(PlayAction.SuggestResult(SuggestedResult.DRAW)))
                val pending = games.head(principal, gameId).play!!.board.resultSuggestion!!
                assertEquals("accepted", other.execute(GameAction.AcceptResult(pending.id)).outcome)
                assertEquals(players.map { it.deck }, games.head(principal, gameId).preparation!!.players.map { it.deck })
                assertEquals(listOf(submitted), games.head(principal, gameId).preparation!!.definitions)
            }
            println("PASS additive V9-to-V14 migration: seeded V9 play/orders/preparation/Ready/private original unchanged, real cooperative return works afterwards")
        }
    }
}
