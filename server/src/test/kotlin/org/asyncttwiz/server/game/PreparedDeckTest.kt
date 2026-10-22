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

class PreparedDeckTest {
    private val json = GameWire.json
    private fun id() = UUID.randomUUID().toString()
    private fun sql(db: OwnedPostgres, statement: String) { db.connection().use { it.createStatement().execute(statement) } }
    private fun number(db: OwnedPostgres, statement: String): Long = db.connection().use { c ->
        c.createStatement().executeQuery(statement).use { it.next(); it.getLong(1) } }
    private fun ready(runtime: ServiceRuntime) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
    }
    private fun account(runtime: ServiceRuntime, name: String): AccountService.Principal {
        val accounts = AccountService(runtime)
        accounts.register(name, "$name@example.invalid", "synthetic password")
        return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
    }
    private fun game(runtime: ServiceRuntime, a: AccountService.Principal, b: AccountService.Principal): GameSummary {
        val lobby = LobbyService(runtime); lobby.add(a, AddContact(id(), b.account.accountId))
        return json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Prepared Spark")).body)
    }
    private fun definitions(): List<SubmittedDefinition> {
        val front = id(); val back = id()
        val cards = listOf(CardDefinition("Retained Front // Back", listOf(
            DefinitionFace(front, "Front", listOf(back), corner = "2/4"),
            DefinitionFace(back, "Back", listOf(front), rulesText = "Original backside")), sourceVersion = "old-source"),
            CardDefinition("Side card", listOf(DefinitionFace(id(), "Side card", rulesText = "Original side text"))))
        return cards.map { SubmittedDefinition(DeckJson.digest(it), it) }
    }
    private fun deck(runtime: ServiceRuntime, actor: AccountService.Principal, defs: List<SubmittedDefinition>): SavedDeck {
        val decks = DeckService(runtime)
        assertEquals(200, decks.submit(actor, SubmitDefinitions(id(), defs)).status)
        val input = CreateDeck(id(), "Original deck 😀", listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, defs[0].digest, 3),
            SavedDeckEntry(SavedDeckSection.SIDEBOARD, defs[1].digest, 2)))
        val result = decks.create(actor, input); assertEquals(201, result.status)
        return json.decodeFromString(result.body)
    }
    private class Stream(val games: GameService, val actor: AccountService.Principal, val game: GameSummary) {
        val hello = GameHello(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val connection = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, game.id, UUID.randomUUID().toString())
        fun selection(source: SavedDeck) = GameCommand(UUID.randomUUID().toString(), GameScope(game.id, game.preparationRound),
            GameAction.SelectDeck(source.deck.id, source.deck.revision), stream.streamId, stream.generation)
        fun invitation(summary: GameSummary, action: GameAction) = GameCommand(UUID.randomUUID().toString(),
            GameScope(summary.id, summary.preparationRound, summary.invitationId), action, stream.streamId, stream.generation)
        fun admit(input: GameCommand) = games.admit(actor, hello, connection, input)
        fun execute(input: GameCommand): GameResult {
            assertTrue(admit(input) is GameReceipt); assertTrue(games.executeOne())
            return games.results(actor, game.id, listOf(input.requestId)).statuses.single().result!!
        }
    }

    @Test fun `exact snapshots pure equivalence own Ready clearing and invitation changes share one ordered path`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "PreparationA"); val b = account(runtime, "PreparationB")
                val summary = game(runtime, a, b); val defs = definitions(); val aSource = deck(runtime, a, defs)
                val bSource = deck(runtime, b, defs); val games = GameService(runtime)
                val aStream = Stream(games, a, summary); val bStream = Stream(games, b, summary)
                val input = aStream.selection(aSource)
                val predicted = PreparationRules.select(summary, PreparationRules.empty(summary), a.account.accountId,
                    input.requestId, input.scope, input.action as GameAction.SelectDeck, aSource, defs).state!!
                val result = aStream.execute(input); assertEquals("accepted", result.outcome)
                val selected = games.head(a, summary.id).preparation!!
                assertEquals(predicted, selected); assertNull(games.head(b, summary.id).preparation)
                assertEquals(listOf(selected.players[0].deck!!.id), result.createdIds)
                assertEquals("accessDenied", bStream.execute(bStream.selection(bSource)).reason)
                val initialDeck = selected.players[0].deck!!
                assertEquals(3L, initialDeck.count(SavedDeckSection.MAINBOARD)); assertEquals(2L, initialDeck.count(SavedDeckSection.SIDEBOARD))
                assertEquals(5, initialDeck.entries.flatMap { entry -> List(entry.quantity) { initialDeck.card(entry, it).id } }.distinct().size)
                assertEquals("accepted", bStream.execute(bStream.invitation(summary, GameAction.DeclineInvitation)).outcome)
                val declined = games.head(a, summary.id).summary
                assertEquals(initialDeck, games.head(a, summary.id).preparation!!.players[0].deck)
                assertEquals("accepted", aStream.execute(aStream.invitation(declined, GameAction.Reinvite)).outcome)
                val reinvited = games.head(a, summary.id).summary
                assertNotEquals(summary.invitationId, reinvited.invitationId)
                // Selection captured in this round before Reinvite remains applicable.
                assertEquals("accepted", aStream.execute(aStream.selection(aSource)).outcome)
                assertEquals("accepted", bStream.execute(bStream.invitation(reinvited, GameAction.AcceptInvitation)).outcome)
                assertEquals("accepted", bStream.execute(bStream.selection(bSource)).outcome)
                sql(db, "UPDATE player_preparation SET ready=true WHERE game_id='${summary.id}'")
                val other = games.head(a, summary.id).preparation!!.players[1]
                assertTrue(other.ready)
                assertEquals("accepted", aStream.execute(aStream.selection(aSource)).outcome)
                val after = games.head(b, summary.id).preparation!!
                assertFalse(after.players[0].ready); assertEquals(other, after.players[1])
                assertEquals(2, number(db, "SELECT count(*) FROM player_preparation"))
                assertEquals(2, number(db, "SELECT count(*) FROM game_definition"))
                assertEquals(summary.preparationRound, after.roundId)
                assertEquals(summary.gameName, games.head(a, summary.id).summary.gameName)
            }
            println("PASS shared pure/server exact equivalence, stable concrete UUIDs and Library/Sideboard counts, immutable multiface definitions, inviter prepares while pending, declined/Reinvite/Accept preserve round/deck; accepted recipient selects; only actor Ready clears with other selected deck/Ready retained")
        }
    }

    @Test fun `source mutation deletion privacy and stale lifecycle guards never substitute or rewrite chosen copies`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "IsolationA"); val b = account(runtime, "IsolationB"); val outsider = account(runtime, "IsolationOther")
                val summary = game(runtime, a, b); val defs = definitions(); val source = deck(runtime, a, defs)
                val foreign = deck(runtime, b, defs); val games = GameService(runtime); val stream = Stream(games, a, summary)
                val chosen = stream.selection(source); assertEquals("accepted", stream.execute(chosen).outcome)
                val original = games.head(a, summary.id).preparation!!
                val revision = games.head(a, summary.id).revision
                val decks = DeckService(runtime)
                val change = UpdateDeck(id(), "New name", defs[1].digest, DeckContentsImport(
                    listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, defs[1].digest, 7))))
                assertEquals(200, decks.update(a, source.deck.id, change).status)
                assertEquals(original, games.head(a, summary.id).preparation)
                val rejected = stream.execute(stream.selection(source)); assertEquals("sourceChanged", rejected.reason)
                assertNull(rejected.commitRevision)
                assertEquals("sourceUnavailable", stream.execute(stream.selection(foreign)).reason)
                assertEquals("sourceUnavailable", stream.execute(stream.selection(source.copy(deck = source.deck.copy(id = id())))).reason)
                val wrongRound = stream.selection(source).let { it.copy(scope = it.scope.copy(preparationId = id())) }
                assertEquals("inapplicable", stream.execute(wrongRound).reason)
                assertEquals(200, decks.delete(a, source.deck.id, DeleteDeck(id())).status)
                assertEquals("sourceUnavailable", stream.execute(stream.selection(source)).reason)
                assertEquals(original, games.head(a, summary.id).preparation)
                assertEquals(revision, games.head(a, summary.id).revision)
                // All accepted copy/definition data is still readable with the saved source absent.
                assertEquals(defs.sortedBy { it.digest }, games.head(a, summary.id).preparation!!.definitions)
                assertEquals(1, number(db, "SELECT count(*) FROM saved_deck")) // Only the foreign saved source remains.
                sql(db, "UPDATE game SET lifecycle='ACTIVE' WHERE id='${summary.id}'")
                assertEquals("lifecycleEnded", stream.execute(stream.selection(foreign)).reason)
                assertEquals(original, games.head(a, summary.id).preparation)
                assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) { games.head(outsider, summary.id) }.code)
                assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) { games.revisions(outsider, summary.id, "0") }.code)
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) {
                    stream.admit(chosen.copy(action = (chosen.action as GameAction.SelectDeck).copy(sourceRevision = "2"))) }.code)
                // Final dedup returns original success even after source deletion and a lifecycle transition.
                assertEquals("accepted", (stream.admit(chosen) as GameResult).outcome)
                assertEquals(1, number(db, "SELECT count(*) FROM game_revision"))
            }
            println("PASS source overwrite/rename/cover/delete leave chosen names, concrete IDs, sections and full definitions unchanged; changed/deleted/foreign/missing source, wrong round and late active edit reject without revision or substitution; private reads and original final dedup retained")
        }
    }

    @Test fun `pending selection rollback restart and renewed stream recover exact original without duplicate attachment`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var a: AccountService.Principal; lateinit var summary: GameSummary; lateinit var original: GameCommand
            lateinit var expected: LobbyPreparation; lateinit var hello: GameHello
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                a = account(runtime, "RecoverA"); val b = account(runtime, "RecoverB")
                summary = game(runtime, a, b); val defs = definitions(); val source = deck(runtime, a, defs)
                val games = GameService(runtime); val stream = Stream(games, a, summary); hello = stream.hello
                original = stream.selection(source)
                expected = PreparationRules.select(summary, PreparationRules.empty(summary), a.account.accountId,
                    original.requestId, original.scope, original.action as GameAction.SelectDeck, source, defs).state!!
                val receipt = stream.admit(original) as GameReceipt
                assertEquals(receipt, stream.admit(original))
                assertEquals("pending", games.results(a, summary.id, listOf(original.requestId)).statuses.single().status)
                assertNull(games.head(a, summary.id).preparation!!.players[0].deck)
                sql(db, """CREATE FUNCTION fail_preparation_commit() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN RAISE EXCEPTION 'owned failure after copy definition revision and result writes'; END ${'$'}${'$'};
                    CREATE TRIGGER fail_preparation_commit BEFORE INSERT ON game_outbox FOR EACH ROW EXECUTE FUNCTION fail_preparation_commit();""")
                assertThrows(Exception::class.java) { games.executeOne() }
                for (table in listOf("player_preparation", "game_definition", "game_revision", "game_outbox"))
                    assertEquals(0, number(db, "SELECT count(*) FROM $table"), table)
                assertEquals(0, number(db, "SELECT revision FROM game"))
                assertEquals(1, number(db, "SELECT count(*) FROM game_inbox WHERE result IS NULL"))
                sql(db, "DROP TRIGGER fail_preparation_commit ON game_outbox; DROP FUNCTION fail_preparation_commit()")
            }
            ServiceRuntime(db.settings).use { runtime ->
                val games = GameService(runtime); runtime.attachWorker({ games.validatePending() }, { true })
                runtime.start(); ready(runtime)
                val connection = UUID.randomUUID()
                val resumed = games.open(a, hello, connection, summary.id, original.streamId, listOf(original.requestId))
                assertEquals("pending", resumed.statuses.single().status)
                assertEquals("1", (games.admit(a, hello, connection, original.copy(generation = resumed.generation)) as GameReceipt).ingressOrdinal)
                assertTrue(games.executeOne()); assertFalse(games.executeOne())
                val result = games.results(a, summary.id, listOf(original.requestId)).statuses.single().result!!
                assertEquals("accepted", result.outcome); assertEquals("1", result.commitRevision)
                assertEquals(expected, games.head(a, summary.id).preparation)
                assertEquals(result, games.admit(a, hello, connection, original.copy(generation = resumed.generation)))
                assertEquals(expected, games.revisions(a, summary.id, "0").revisions.single().preparation)
                assertEquals(1, number(db, "SELECT count(*) FROM game_inbox"))
                assertEquals(1, number(db, "SELECT count(*) FROM game_revision"))
                assertEquals(1, number(db, "SELECT count(*) FROM game_outbox"))
                assertEquals(1, number(db, "SELECT count(*) FROM player_preparation"))
                assertEquals(2, number(db, "SELECT count(*) FROM game_definition"))
            }
            assertEquals(0, number(db, "SELECT count(*) FROM pg_locks WHERE locktype='advisory'"))
            println("PASS committed pending dedup is not accepted state, real SQL failure rolls selected copies/definitions/Ready/revision/result/outbox back atomically, restart validates pending then fenced transport renewal recovers same IDs once; one inbox/attachment/revision/outbox and exact advisory cleanup")
        }
    }

    @Test fun `explicit V7 forward migration retains source definitions game streams and pending original outcomes`() {
        OwnedPostgres().start().use { db ->
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("7").load().migrate()
            val a = id(); val b = id(); val game = id(); val invite = id(); val round = id(); val source = id(); val request = id()
            val def = definitions()[0]; val stream = id()
            val command = GameCommand(request, GameScope(game, round, invite), GameAction.DeclineInvitation, stream, "1")
            sql(db, """INSERT INTO account(id,username,username_key,email_delivery,email_key,password_phc) VALUES
                ('$a','A','a','a@example.invalid','a@example.invalid','retained'),('$b','B','b','b@example.invalid','b@example.invalid','retained');
                INSERT INTO definition VALUES ('${def.digest}',1,'${DeckJson.canonical(def.definition)}');
                INSERT INTO account_definition VALUES ('$a','${def.digest}');
                INSERT INTO saved_deck(id,account_id,name,revision) VALUES ('$source','$a','Retained source',9);
                INSERT INTO deck_entry VALUES ('$source','$a',0,'MAINBOARD','${def.digest}',3);
                BEGIN;
                INSERT INTO game(id,game_name,inviter_id,recipient_id,preparation_round,current_invitation,next_ingress)
                VALUES ('$game','Retained Spark','$a','$b','$round','$invite',2);
                INSERT INTO invitation VALUES ('$invite','$game','PENDING'); COMMIT;
                INSERT INTO request_identity VALUES ('$request','$b','gameCommand','$game','${GameWire.digest(command)}');
                INSERT INTO game_inbox VALUES ('$request','$game','$b',1,'$stream',1,'${json.encodeToString<GameClientMessage>(command)}',NULL);""")
            assertEquals(1, FoundationMigrations.migrate(db.settings)); assertEquals(0, FoundationMigrations.migrate(db.settings))
            assertEquals(1, number(db, "SELECT count(*) FROM saved_deck WHERE revision=9"))
            assertEquals(1, number(db, "SELECT count(*) FROM deck_entry WHERE quantity=3"))
            assertEquals(1, number(db, "SELECT count(*) FROM game WHERE id='$game' AND preparation_round='$round' AND current_invitation='$invite' AND revision=0 AND next_ingress=2"))
            assertEquals(0, number(db, "SELECT count(*) FROM player_preparation")); assertEquals(0, number(db, "SELECT count(*) FROM game_definition"))
            ServiceRuntime(db.settings).use { runtime ->
                val games = GameService(runtime); runtime.attachWorker({ games.validatePending() }, { true })
                runtime.start(); ready(runtime); assertTrue(games.executeOne()); assertFalse(games.executeOne())
            }
            assertEquals(1, number(db, "SELECT count(*) FROM invitation WHERE state='DECLINED'"))
            assertEquals(1, number(db, "SELECT count(*) FROM game_inbox WHERE result IS NOT NULL"))
            assertEquals(1, number(db, "SELECT count(*) FROM game_revision")); assertEquals(1, number(db, "SELECT count(*) FROM game_outbox"))
            println("PASS explicit V7-to-V8 retained source/definition/game/round/current attempt/next ordinal and original pending command survive; preparation tables start empty and existing durable invitation resumes once after migration")
        }
    }
}
