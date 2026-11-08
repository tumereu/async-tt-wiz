package org.asyncttwiz.server.game

import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.lobby.LobbyService
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameResultLifecycleTest {
    private fun id() = UUID.randomUUID().toString()
    private fun sql(db: TestPostgres, statement: String) { db.connection().use { it.createStatement().execute(statement) } }
    private fun count(db: TestPostgres, statement: String): Long = db.connection().use { connection ->
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
            return GameCommand(id(), scope ?: if (action is GameAction.Submit || action.isEngine)
                GameScope(gameId, playId = summary.playId) else GameScope(gameId, summary.preparationRound,
                    if (action == GameAction.AcceptInvitation) summary.invitationId else null), action, stream.streamId, stream.generation)
        }
        fun admit(command: GameCommand) = games.admit(actor, hello, connection, command)
        fun result(command: GameCommand) = games.results(actor, gameId, listOf(command.requestId)).statuses.single().result!!
        fun execute(command: GameCommand): GameResult { assertTrue(admit(command) is GameReceipt); assertTrue(games.executeOne()); return result(command) }
        fun execute(action: GameAction) = execute(command(action))
    }
    private inner class Fixture(val db: TestPostgres) : AutoCloseable {
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
        /** [by]'s concession (CR 104.3a): it ends the play at once (I-D56 §11). */
        fun concession(by: Stream = other) = by.command(games.concession(by.actor, game.id))
        override fun close() { runtime.close() }
    }

    // LOB-038
    @Test fun `alternate starter hands the next play to whoever did not start the last one and can be switched off`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            Fixture(db).use { f ->
                fun end(): LobbyPreparation {
                    assertEquals("accepted", f.other.execute(f.concession()).outcome)
                    // TRN-065: the next play starts only once the other player tapped OK on the result
                    assertEquals("accepted", f.owner.execute(f.owner.command(GameAction.AcknowledgeResult, GameScope(f.game.id))).outcome)
                    return f.games.head(f.b, f.game.id).preparation!!
                }
                val accounts = listOf(f.a.account.accountId, f.b.account.accountId)
                val first = f.initial.starter.accountId // Random resolved this one.
                assertNull(f.initial.starter.choice.accountId)
                val returned = end()
                val next = accounts.single { it != first }
                assertTrue(returned.alternateStarter, "On by default")
                assertEquals(StarterChoice(next), returned.starter)
                assertEquals(1, count(db, "SELECT count(*) FROM game WHERE alternate_starter AND starter_choice LIKE '%$next%'"))
                assertEquals("accepted", f.other.execute(GameAction.SetAlternateStarter(false)).outcome)
                assertFalse(f.games.head(f.a, f.game.id).preparation!!.alternateStarter)
                f.owner.execute(GameAction.Ready); f.other.execute(GameAction.Ready)
                val second = f.games.head(f.a, f.game.id).play!!
                assertEquals(next, second.starter.accountId)
                val kept = end()
                assertFalse(kept.alternateStarter)
                assertEquals(StarterChoice(next), kept.starter, "Switched off: the choice stays")
            }
        }
    }

    // LOB-046: after a game result (here a concession, taken at once) both players are back in the same game's lobby with
    // their last prepared decks restored, the owner's lobby partition edit included, and both Ready cleared
    @Test fun `after a result both players return to the same lobby with their last prepared decks and Ready cleared`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            Fixture(db).use { f ->
                val prepared = f.preparation.players.associate { it.accountId to it.deck }
                assertEquals(2, prepared.values.filterNotNull().size)
                assertEquals(GameLifecycle.ACTIVE, f.games.head(f.a, f.game.id).summary.lifecycle)
                assertEquals("accepted", f.other.execute(f.concession()).outcome)
                val returned = f.games.head(f.a, f.game.id)
                assertEquals(f.game.id, returned.summary.id)
                assertEquals(GameLifecycle.LOBBY, returned.summary.lifecycle)
                assertNull(returned.play)
                val lobby = returned.preparation!!
                assertEquals(prepared, lobby.players.associate { it.accountId to it.deck }, "the last prepared decks")
                assertTrue(lobby.players.none { it.ready }, "both Ready cleared")
                val own = f.games.head(f.b, f.game.id).preparation!!.players.single { it.accountId == f.b.account.accountId }
                assertEquals(prepared[f.b.account.accountId], own.deck)
                assertFalse(own.ready)
            }
        }
    }

    @Test fun `show deck is a per game per player lobby flag kept across plays and hidden again in a new game`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            Fixture(db).use { f ->
                val bId = f.b.account.accountId
                fun shown(viewer: AccountService.Principal = f.a) =
                    f.games.head(viewer, f.game.id).preparation!!.players.associate { it.accountId to it.deckShown }
                // The toggle itself, without "Show after first game" revealing the decks when the play ends.
                for (player in listOf(f.a, f.b)) org.asyncttwiz.server.accounts.AccountSettingsService(f.runtime)
                    .set(player.account.accountId, AccountSettings(DeckVisibility.HIDDEN))
                // The lobby toggle is not a gameplay action: an active game refuses it.
                assertEquals("lifecycleEnded", f.other.execute(GameAction.ShowDeck(true)).reason)
                assertEquals("accepted", f.other.execute(f.concession()).outcome)
                // TRN-065: the owner taps OK on the result before getting Ready
                assertEquals("accepted", f.owner.execute(f.owner.command(GameAction.AcknowledgeResult, GameScope(f.game.id))).outcome)
                assertEquals(setOf(false), shown().values.toSet(), "Hidden by default")
                assertFalse("deckShown" in GameWire.encode(f.games.head(f.a, f.game.id)))
                f.owner.execute(GameAction.Ready)
                assertEquals("accepted", f.other.execute(GameAction.ShowDeck(true)).outcome)
                for (viewer in listOf(f.a, f.b)) assertEquals(mapOf(f.a.account.accountId to false, bId to true), shown(viewer))
                assertTrue(f.games.head(f.a, f.game.id).preparation!!.players.single { it.accountId == f.a.account.accountId }.ready,
                    "Showing a deck never withdraws Ready")
                assertEquals(1, count(db, "SELECT count(*) FROM player_preparation WHERE deck_shown AND account_id = '$bId'"))
                val revision = f.games.revisions(f.a, f.game.id, "0").revisions.last()
                assertTrue(revision.preparation!!.players.single { it.accountId == bId }.deckShown)
                // Kept for the next play of the same game, through the start and the return to the lobby.
                f.other.execute(GameAction.Ready)
                assertEquals(GameLifecycle.ACTIVE, f.games.head(f.a, f.game.id).summary.lifecycle)
                assertEquals("accepted", f.other.execute(f.concession()).outcome)
                assertTrue(shown().getValue(bId))
                // It can be switched off again.
                assertEquals("accepted", f.other.execute(GameAction.ShowDeck(false)).outcome)
                assertFalse(shown().getValue(bId))
                assertEquals("accepted", f.other.execute(GameAction.ShowDeck(true)).outcome)
                // A new game follows the account default; with Hidden it starts hidden although A finished a game with this deck against B.
                val fresh = GameWire.json.decodeFromString<GameSummary>(LobbyService(f.runtime).create(f.a,
                    CreateInvitation(id(), bId, "Fresh game")).body)
                val freshOwner = Stream(f.games, f.a, fresh.id)
                org.asyncttwiz.server.accounts.AccountSettingsService(f.runtime).set(f.a.account.accountId, AccountSettings(DeckVisibility.HIDDEN))
                freshOwner.execute(GameAction.SelectDeck(f.source.deck.id, f.source.deck.revision))
                assertTrue(f.games.head(f.a, fresh.id).preparation!!.players.none { it.deckShown })
            }
        }
    }

    // LOB-111, LOB-075, LOB-076: a concession ends the game; the other player hears the outcome from their side, and no
    // reminder follows
    @Test fun `a concession notifies the other player with the outcome from their side and no reminder follows`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            Fixture(db).use { f ->
                val activity = org.asyncttwiz.server.activity.ActivityService(f.runtime)
                val concession = f.concession()
                assertEquals("accepted", f.other.execute(concession).outcome)
                assertEquals(GameLifecycle.LOBBY, f.games.head(f.a, f.game.id).summary.lifecycle)
                val notice = activity.page(f.a, null).readyNotices!!.single { it.kind == ActivityKind.GAME && it.gameId == f.game.id }
                assertEquals(concession.requestId, notice.identity)
                assertEquals(f.b.account.accountId, notice.authorId)
                assertNotNull(notice.notice!!.gameOver, "the game is over")
                assertTrue(notice.notice!!.conceded)
                assertEquals("Result Spark", notice.notice!!.gameName)
                assertTrue(activity.page(f.b, null).readyNotices!!.none { it.notice?.gameOver != null }, "Only the other player hears it")
                // Tapping it opens the game.
                assertEquals(ActivityDestination.CURRENT, activity.target(f.a, ActivityKind.GAME, f.game.id, notice.identity).destination)
                f.runtime.transaction { connection ->
                    org.asyncttwiz.server.activity.ActivityService.remindDue(org.jooq.impl.DSL.using(connection, org.jooq.SQLDialect.POSTGRES),
                        java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusHours(1))
                }
                assertEquals(0L, count(db, "SELECT count(*) FROM activity_summary WHERE reminder_activity_id IS NOT NULL"))
                assertTrue(activity.page(f.a, null).readyNotices!!.none { it.notice?.reminder == true })
                assertTrue(activity.waiting(f.a).games.isEmpty())
            }
        }
    }

    @Test fun `failed return rolls back atomically and an admitted concession recovers after restart without duplicate effect`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var accept: GameCommand; lateinit var before: GameHead; lateinit var actor: AccountService.Principal
            lateinit var author: AccountService.Principal; lateinit var hello: GameHello; lateinit var gameId: String
            Fixture(db).use { f ->
                before = f.games.head(f.a, f.game.id)
                accept = f.concession()
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

    @Test fun `V14 to V40 migrations preserve a preexisting play and V41 wipes its started game`() {
        FreshDatabase(migrated = false).start().use { db ->
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
            // The play row as the old model wrote it, at its root (V16 reads that)
            val stored = (GameWire.json.encodeToJsonElement(PlayState.serializer(), play) as kotlinx.serialization.json.JsonObject).let {
                kotlinx.serialization.json.JsonObject(it + ("currentNodeId" to kotlinx.serialization.json.JsonPrimitive(play.root.id))) }.toString()
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
                VALUES ('${play.id}','$gameId','$round','$request','${play.root.id}','$stored');
                UPDATE game SET lifecycle='ACTIVE', current_play='${play.id}' WHERE id='$gameId';
                INSERT INTO game_definition VALUES ('$gameId','${submitted.digest}');""")
            for (player in players) sql(db, "INSERT INTO player_preparation VALUES ('$gameId','${player.accountId}',true,'${GameWire.json.encodeToString(player.deck)}')")
            // V14..V40 are additive: the retained play, its preparation and its request survive unchanged.
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("40").load().migrate()
            fun count(statement: String): Long = db.connection().use { c -> c.createStatement().executeQuery(statement).use { it.next(); it.getLong(1) } }
            assertEquals(1, count("SELECT count(*) FROM game_play WHERE id='${play.id}' AND payload='$stored'"))
            assertEquals(2, count("SELECT count(*) FROM player_preparation WHERE game_id='$gameId'"))
            assertEquals(1, count("SELECT count(*) FROM game_inbox WHERE request_id='$request' AND result='${GameWire.json.encodeToString(original)}'"))
            // V41 (rules v29 library layers) then deletes the started game with everything that belongs to it.
            assertEquals(1, Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("41").load()
                .migrate().migrationsExecuted)
            FoundationMigrations.migrate(db.settings); assertEquals(0, FoundationMigrations.migrate(db.settings))
            for (table in listOf("game", "game_play", "player_preparation", "game_inbox", "game_definition", "invitation"))
                assertEquals(0, count("SELECT count(*) FROM $table"), table)
            assertEquals(2, count("SELECT count(*) FROM account"))
            println("PASS additive V9-to-V40 migration keeps the seeded play/preparation/request; V41 wipes the started game")
        }
    }
}
