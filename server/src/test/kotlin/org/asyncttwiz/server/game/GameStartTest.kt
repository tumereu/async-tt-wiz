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

class GameStartTest {
    @Test fun `oversized create and merged contents are terminal validation errors`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "BudgetA"); val source = deck(runtime, a)
                val decks = DeckService(runtime)
                val input = CreateDeck(id(), "Oversized", listOf(source.entries.first().copy(quantity = Int.MAX_VALUE)))
                val rejected = decks.create(a, input)
                assertEquals(400, rejected.status); assertEquals("invalid_deck", json.decodeFromString<DeckError>(rejected.body).code)
                assertEquals(rejected, decks.create(a, input))
                val merged = UpdateDeck(id(), contents = DeckContentsImport(listOf(source.entries.first().copy(quantity = 1000)), DeckImportMode.MERGE))
                assertEquals(400, decks.update(a, source.deck.id, merged).status)
                assertEquals(source, json.decodeFromString<SavedDeck>(decks.read(a, source.deck.id).body))
            }
        }
    }

    @Test fun `legacy oversized prepared deck rejects Ready without allocating a play`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "LegacyBudgetA"); val b = account(runtime, "LegacyBudgetB")
                val game = game(runtime, a, b); val games = GameService(runtime)
                val owner = Stream(games, a, game); val recipient = Stream(games, b, game)
                recipient.execute(GameAction.AcceptInvitation)
                owner.execute(owner.select(deck(runtime, a))); recipient.execute(recipient.select(deck(runtime, b)))
                owner.execute(GameAction.Ready)
                val prepared = games.head(a, game.id).preparation!!.players.single { it.accountId == a.account.accountId }.deck!!
                val oversized = prepared.copy(entries = listOf(prepared.entries.first().copy(quantity = 1001)))
                sql(db, "UPDATE player_preparation SET deck='${json.encodeToString(oversized)}' WHERE game_id='${game.id}' AND account_id='${a.account.accountId}'")
                val command = recipient.command(GameAction.Ready)
                val result = recipient.execute(command)
                assertEquals("rejected", result.outcome); assertEquals("invalidDeck", result.reason)
                assertEquals(result, recipient.admit(command))
                assertNull(games.head(a, game.id).play)
                assertFalse(games.head(a, game.id).preparation!!.players.single { it.accountId == b.account.accountId }.ready)
                assertFalse(games.executeOne())
            }
        }
    }

    @Test fun `request Error rolls back partial work records a terminal rejection and allows the next request`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "ErrorA"); val b = account(runtime, "ErrorB")
                val game = game(runtime, a, b); val games = GameService(runtime)
                val owner = Stream(games, a, game)
                owner.execute(owner.select(deck(runtime, a)))
                val before = games.head(a, game.id)
                val command = owner.command(GameAction.Ready)
                assertTrue(owner.admit(command) is GameReceipt)
                val listener = object : org.jooq.impl.DefaultExecuteListener() {
                    override fun executeStart(ctx: org.jooq.ExecuteContext) {
                        if (ctx.sql().orEmpty().startsWith("update \"public\".\"game\""))
                            throw AssertionError("synthetic request failure")
                    }
                }
                assertTrue(games.executeOne(listener))
                val result = owner.result(command)
                assertEquals("rejected", result.outcome); assertEquals("internalError", result.reason)
                assertEquals(before, games.head(a, game.id))
                assertEquals(result, owner.admit(command))
                assertEquals("accepted", owner.execute(GameAction.Ready).outcome)
                assertFalse(games.executeOne())
            }
        }
    }

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
        return json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Starting Spark")).body)
    }
    private fun deck(runtime: ServiceRuntime, actor: AccountService.Principal, sideOnly: Boolean = false): SavedDeck {
        val defs = listOf(CardDefinition("Retained first", listOf(DefinitionFace(id(), "First", corner = "3/5")), sourceVersion = "synthetic-v1"),
            CardDefinition("Side only", listOf(DefinitionFace(id(), "Side", rulesText = "Not playable"))))
            .map { SubmittedDefinition(DeckJson.digest(it), it) }
        val decks = DeckService(runtime); assertEquals(200, decks.submit(actor, SubmitDefinitions(id(), defs)).status)
        val entries = if (sideOnly) listOf(SavedDeckEntry(SavedDeckSection.SIDEBOARD, defs[1].digest, 1)) else
            listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, defs[0].digest, 7), SavedDeckEntry(SavedDeckSection.SIDEBOARD, defs[1].digest, 2))
        val result = decks.create(actor, CreateDeck(id(), "Saved source", entries)); assertEquals(201, result.status)
        return json.decodeFromString(result.body)
    }
    private class Stream(val games: GameService, val actor: AccountService.Principal, val game: GameSummary) {
        val hello = GameHello(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val connection = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, game.id, UUID.randomUUID().toString())
        fun command(action: GameAction) = GameCommand(UUID.randomUUID().toString(), GameScope(game.id, game.preparationRound,
            if (action == GameAction.AcceptInvitation || action == GameAction.DeclineInvitation || action == GameAction.Reinvite) game.invitationId else null),
            action, stream.streamId, stream.generation)
        fun select(source: SavedDeck) = command(GameAction.SelectDeck(source.deck.id, source.deck.revision))
        fun admit(input: GameCommand) = games.admit(actor, hello, connection, input)
        fun result(input: GameCommand) = games.results(actor, game.id, listOf(input.requestId)).statuses.single().result!!
        fun execute(input: GameCommand): GameResult {
            assertTrue(admit(input) is GameReceipt); assertTrue(games.executeOne()); return result(input)
        }
        fun execute(action: GameAction) = execute(command(action))
    }
    private fun assertInitial(game: GameSummary, preparation: LobbyPreparation, state: PlayState) {
        assertEquals(game.id, state.gameId); assertEquals(game.preparationRound, state.preparationRound)
        assertNotEquals(game.preparationRound, state.id); assertNotEquals(state.id, state.root.id)
        assertEquals(state.root.id, state.currentNodeId); assertNull(state.root.parentId)
        assertEquals(TurnState(state.starter.accountId), state.board.turn)
        assertEquals(listOf("20", "20"), state.board.players.map { it.life })
        assertTrue(state.board.players.all { it.counters.isEmpty() })
        assertTrue(state.board.cards.all { !it.tapped && it.covered && it.counters.isEmpty() && it.note.isEmpty() })
        assertTrue(state.board.containers.filter { it.zone != PlayZone.LIBRARY }.all { it.cards.isEmpty() })
        for (player in preparation.players) {
            val deck = player.deck!!
            val members = deck.entries.filter { it.section == SavedDeckSection.MAINBOARD }.flatMap { entry ->
                List(entry.quantity) { deck.card(entry, it).id } }.toSet()
            val library = state.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == player.accountId }
            val shuffled = state.shuffles.single { it.accountId == player.accountId }
            assertEquals(members, library.cards.toSet()); assertEquals(library.cards, shuffled.order)
            assertEquals(shuffled.order, RandomV1.shuffle(members.toList(), shuffled.seed))
            assertEquals(members, state.board.cards.filter { it.owner == player.accountId }.map { it.id }.toSet())
        }
        assertEquals(state.board.cards.map { it.definition }.toSet(), state.definitions.map { it.digest }.toSet())
        assertEquals(2, state.shuffles.map { it.seed }.distinct().size)
    }

    @Test fun `inviter readies before acceptance edits clear only own and second accepted Ready starts exactly once`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "StartA"); val b = account(runtime, "StartB"); val other = account(runtime, "StartOther")
                val game = game(runtime, a, b); val games = GameService(runtime)
                val owner = Stream(games, a, game); val recipient = Stream(games, b, game)
                val source = deck(runtime, a); val bSource = deck(runtime, b, sideOnly = true)
                assertEquals("deckRequired", owner.execute(GameAction.Ready).reason)
                assertEquals("accepted", owner.execute(GameAction.SetStarter(StarterChoice(b.account.accountId))).outcome)
                assertEquals("accepted", owner.execute(owner.select(source)).outcome)
                assertEquals("accepted", owner.execute(GameAction.Ready).outcome)
                val first = games.head(a, game.id)
                assertTrue(first.preparation!!.players[0].ready); assertNull(first.play)
                owner.execute(GameAction.WithdrawReady)
                assertFalse(games.head(a, game.id).preparation!!.players[0].ready)
                owner.execute(GameAction.Ready)
                owner.execute(GameAction.SetStarter(StarterChoice()))
                assertFalse(games.head(a, game.id).preparation!!.players[0].ready)
                owner.execute(GameAction.Ready)
                assertNull(games.head(b, game.id).preparation)
                assertEquals("accessDenied", recipient.execute(GameAction.Ready).reason)
                assertEquals("accepted", recipient.execute(GameAction.AcceptInvitation).outcome)
                assertNull(games.head(b, game.id).play); assertTrue(games.head(b, game.id).preparation!!.players[0].ready)
                assertEquals("accepted", recipient.execute(recipient.select(bSource)).outcome)
                assertTrue(games.head(b, game.id).preparation!!.players[0].ready)
                // Own starter edit and withdrawal never clear the other player's accepted Ready.
                assertEquals("accepted", recipient.execute(GameAction.SetStarter(StarterChoice(a.account.accountId))).outcome)
                assertTrue(games.head(b, game.id).preparation!!.players[0].ready)
                assertEquals("accepted", recipient.execute(GameAction.WithdrawReady).outcome)
                assertTrue(games.head(b, game.id).preparation!!.players[0].ready)
                val prepared = games.head(a, game.id).preparation!!
                val decks = DeckService(runtime)
                assertEquals(200, decks.update(a, source.deck.id, UpdateDeck(id(), "Renamed source", contents = DeckContentsImport(
                    listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, source.entries[0].definition, 1))))).status)
                assertEquals(200, decks.delete(a, source.deck.id, DeleteDeck(id())).status)
                assertEquals(prepared, games.head(a, game.id).preparation)
                val last = recipient.command(GameAction.Ready)
                val receipt = recipient.admit(last); assertEquals(receipt, recipient.admit(last))
                assertNull(games.head(a, game.id).play); assertEquals("pending", games.results(b, game.id, listOf(last.requestId)).statuses.single().status)
                assertTrue(games.executeOne()); assertFalse(games.executeOne())
                val result = recipient.result(last); assertEquals("accepted", result.outcome)
                val head = games.head(a, game.id); val state = head.play!!
                assertInitial(game, prepared, state)
                assertEquals(GameLifecycle.ACTIVE, head.summary.lifecycle); assertEquals(state.id, head.summary.playId)
                assertNull(head.preparation) // Sideboard is retained for return, not an in-play contents path.
                assertEquals(a.account.accountId, state.starter.accountId); assertNull(state.starter.seed)
                assertEquals(listOf(state.id, state.root.id), result.createdIds)
                assertEquals(head, games.head(b, game.id)); assertEquals(game.gameName, head.summary.gameName); assertEquals(game.createdAt, head.summary.createdAt)
                val revision = games.revisions(b, game.id, (result.commitRevision!!.toLong() - 1).toString()).revisions.single()
                assertEquals(state, revision.play); assertNull(revision.preparation)
                assertEquals(result, recipient.admit(last))
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) {
                    recipient.admit(last.copy(action = GameAction.WithdrawReady)) }.code)
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) { games.results(a, game.id, listOf(last.requestId)) }.code)
                assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) { games.head(other, game.id) }.code)
                for (action in listOf(GameAction.Ready, GameAction.WithdrawReady, GameAction.SetStarter(StarterChoice()),
                    GameAction.SelectDeck(bSource.deck.id, bSource.deck.revision)))
                    assertEquals("lifecycleEnded", recipient.execute(action).reason)
                assertEquals(head, games.head(a, game.id))
                assertEquals(1, number(db, "SELECT count(*) FROM game_play"))
                assertEquals(2, number(db, "SELECT count(*) FROM player_preparation WHERE deck IS NOT NULL AND ready"))
                assertEquals(prepared.definitions.size.toLong(), number(db, "SELECT count(*) FROM game_definition"))
                assertEquals(0, number(db, "SELECT count(*) FROM saved_deck WHERE id='${source.deck.id}'"))
            }
            assertEquals(0, number(db, "SELECT count(*) FROM pg_locks WHERE locktype='advisory'"))
            println("PASS own selected deck only/no size or presence gate, inviter persistent Ready before acceptance; ordinary acceptance not start; explicit participant/starter change/withdraw own-only; source overwrite/delete isolation; second accepted Ready commits one root/play, 20 life/turn1 Untap/empty Hand/no Sideboard; duplicate/private original recovery and late lobby refusal")
        }
    }

    @Test fun `arrival order applies preparation change before second Ready and fresh starts use independent random inputs`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "OrderingA"); val b = account(runtime, "OrderingB")
                val aSource = deck(runtime, a); val bSource = deck(runtime, b); val games = GameService(runtime)
                val states = mutableListOf<PlayState>()
                repeat(2) {
                    val game = game(runtime, a, b); val owner = Stream(games, a, game); val recipient = Stream(games, b, game)
                    assertEquals(StarterChoice(), games.head(a, game.id).preparation!!.starter)
                    assertEquals(1, number(db, """SELECT count(*) FROM game WHERE id='${game.id}' AND starter_choice='{"accountId":null}'"""))
                    owner.execute(owner.select(aSource)) // Random is already selected, no SetStarter required.
                    assertEquals(0, number(db, "SELECT count(*) FROM game_play WHERE game_id='${game.id}'")) // No roll on selecting Random.
                    recipient.execute(GameAction.AcceptInvitation); recipient.execute(recipient.select(bSource)); owner.execute(GameAction.Ready)
                    var preparation = games.head(a, game.id).preparation!!
                    val deck = preparation.players[0].deck!!; val entry = deck.entries.first()
                    val change = owner.command(GameAction.TransferPreparedCard(deck.id, deck.card(entry, 0).id, entry.firstIndex, SavedDeckSection.MAINBOARD))
                    val second = recipient.command(GameAction.Ready)
                    owner.admit(change); recipient.admit(second)
                    games.executeOne(); games.executeOne()
                    assertFalse(games.head(a, game.id).preparation!!.players[0].ready)
                    assertTrue(games.head(a, game.id).preparation!!.players[1].ready); assertNull(games.head(a, game.id).play)
                    assertEquals("accepted", owner.result(change).outcome); assertEquals("accepted", recipient.result(second).outcome)
                    // A selection/reselection while still in lobby clears actor Ready, not the recipient's.
                    owner.execute(owner.select(aSource)); preparation = games.head(a, game.id).preparation!!
                    assertFalse(preparation.players[0].ready); assertTrue(preparation.players[1].ready)
                    assertEquals("inapplicable", owner.execute(owner.command(GameAction.Ready).let { it.copy(scope = it.scope.copy(preparationId = id())) }).reason)
                    owner.execute(GameAction.Ready)
                    val state = games.head(a, game.id).play!!; assertInitial(game, preparation, state)
                    assertNotNull(state.starter.seed); assertEquals("starter-v1", state.starter.algorithm)
                    assertEquals(state.starter.accountId, RandomV1.starter(listOf(a.account.accountId, b.account.accountId), state.starter.seed!!))
                    assertEquals(3, (state.shuffles.map { it.seed } + state.starter.seed!!).distinct().size)
                    assertEquals("lifecycleEnded", owner.execute(change.copy(requestId = id())).reason)
                    states.add(state)
                }
                assertNotEquals(states[0].id, states[1].id); assertNotEquals(states[0].root.id, states[1].root.id)
                assertEquals(6, states.flatMap { it.shuffles.map { shuffle -> shuffle.seed } + it.starter.seed!! }.distinct().size)
                assertEquals(2, number(db, "SELECT count(*) FROM game_play"))
            }
            println("PASS durable admission order: exact per-copy change precedes second Ready, own-only clearing prevents stale start; selection preserves other Ready; stale round refused; actual Random start retains independently acquired two Library and starter seeds with deterministic concrete outcomes, distinct starts have fresh inputs; no pre-start sampling")
        }
    }

    @Test fun `failed start transaction rolls back all initialization and admitted request survives worker restart once`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var a: AccountService.Principal; lateinit var b: AccountService.Principal; lateinit var game: GameSummary
            lateinit var pending: GameCommand; lateinit var hello: GameHello; lateinit var before: GameHead
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                a = account(runtime, "AtomicStartA"); b = account(runtime, "AtomicStartB"); game = game(runtime, a, b)
                val games = GameService(runtime); val owner = Stream(games, a, game); val recipient = Stream(games, b, game)
                owner.execute(owner.select(deck(runtime, a))); owner.execute(GameAction.SetStarter(StarterChoice()))
                recipient.execute(GameAction.AcceptInvitation); recipient.execute(recipient.select(deck(runtime, b))); owner.execute(GameAction.Ready)
                before = games.head(a, game.id); pending = recipient.command(GameAction.Ready); hello = recipient.hello
                assertTrue(recipient.admit(pending) is GameReceipt)
                sql(db, """CREATE FUNCTION fail_initial_commit() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN RAISE EXCEPTION 'owned start rollback after root board revision and result writes'; END ${'$'}${'$'};
                    CREATE TRIGGER fail_initial_commit BEFORE INSERT ON game_outbox FOR EACH ROW EXECUTE FUNCTION fail_initial_commit();""")
                assertThrows(Exception::class.java) { games.executeOne() }
                assertEquals(0, number(db, "SELECT count(*) FROM game_play"))
                assertEquals(1, number(db, "SELECT count(*) FROM game WHERE lifecycle='LOBBY' AND current_play IS NULL AND revision=${before.revision}"))
                assertEquals(1, number(db, "SELECT count(*) FROM player_preparation WHERE ready"))
                assertEquals(before.revision.toLong(), number(db, "SELECT count(*) FROM game_revision"))
                assertEquals(before.revision.toLong(), number(db, "SELECT count(*) FROM game_outbox"))
                assertEquals(1, number(db, "SELECT count(*) FROM game_inbox WHERE result IS NULL"))
                sql(db, "DROP TRIGGER fail_initial_commit ON game_outbox; DROP FUNCTION fail_initial_commit()")
            }
            lateinit var accepted: GameHead; lateinit var original: GameResult
            ServiceRuntime(db.settings).use { runtime ->
                val hub = GameHub(runtime); runtime.attachWorker(hub::validatePending, hub::available)
                try {
                    hub.start(); runtime.start(); ready(runtime)
                    val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                    while (hub.games.results(b, game.id, listOf(pending.requestId)).statuses.single().status != "final") {
                        assertTrue(System.nanoTime() < end); Thread.sleep(20)
                    }
                    accepted = hub.games.head(a, game.id); assertInitial(game, before.preparation!!, accepted.play!!)
                    original = hub.games.results(b, game.id, listOf(pending.requestId)).statuses.single().result!!
                    val connection = UUID.randomUUID()
                    val resumed = hub.games.open(b, hello, connection, game.id, pending.streamId, listOf(pending.requestId))
                    assertEquals(original, resumed.statuses.single().result)
                    assertEquals(original, hub.games.admit(b, hello, connection, pending.copy(generation = resumed.generation)))
                    assertEquals(accepted, hub.games.head(b, game.id))
                } finally { hub.close() }
            }
            ServiceRuntime(db.settings).use { runtime ->
                val games = GameService(runtime); runtime.attachWorker(games::validatePending) { true }; runtime.start(); ready(runtime)
                assertEquals(accepted, games.head(a, game.id)); assertFalse(games.executeOne())
                assertEquals(original, games.results(b, game.id, listOf(pending.requestId)).statuses.single().result)
                assertEquals(1, number(db, "SELECT count(*) FROM game_play"))
                assertEquals(before.revision.toLong() + 1, number(db, "SELECT count(*) FROM game_revision"))
                assertEquals(before.revision.toLong() + 1, number(db, "SELECT count(*) FROM game_outbox"))
                assertEquals(0, number(db, "SELECT count(*) FROM game_inbox WHERE result IS NULL"))
            }
            assertEquals(0, number(db, "SELECT count(*) FROM pg_locks WHERE locktype='advisory'"))
            println("PASS real SQL fault rolls back Ready/play/root/orders/lifecycle/revision/result/outbox atomically; committed pending original survives shutdown; production GameHub worker recovers and initializes once; renewed stream/final duplicate and second runtime restart return byte-equivalent retained state without resampling/reset/draw; exact advisory cleanup")
        }
    }

    @Test fun `explicit V8 migration retains original prepared snapshots ready flags definitions streams and outcomes`() {
        OwnedPostgres().start().use { db ->
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("8").load().migrate()
            val a = id(); val b = id(); val game = id(); val invite = id(); val round = id(); val select = id(); val stream = id(); val request = id()
            val def = CardDefinition("Old retained", listOf(DefinitionFace(id(), "Old face", corner = "1/2")))
            val submitted = SubmittedDefinition(DeckJson.digest(def), def)
            val deck = PreparedDeck(CommandIds.output(select, "prepared-deck", 0), id(), "7", "Old selected", submitted.digest, select,
                listOf(PreparedEntry(submitted.digest, SavedDeckSection.MAINBOARD, "0", 60), PreparedEntry(submitted.digest, SavedDeckSection.SIDEBOARD, "60", 15)))
            val command = GameCommand(request, GameScope(game, round, invite), GameAction.DeclineInvitation, stream, "1")
            sql(db, """INSERT INTO account(id,username,username_key,email_delivery,email_key,password_phc) VALUES
                ('$a','A','a','a@example.invalid','a@example.invalid','retained'),('$b','B','b','b@example.invalid','b@example.invalid','retained');
                INSERT INTO definition VALUES ('${submitted.digest}',1,'${DeckJson.canonical(def)}');
                BEGIN;
                INSERT INTO game(id,game_name,inviter_id,recipient_id,preparation_round,current_invitation,next_ingress,revision)
                VALUES ('$game','Retained start','$a','$b','$round','$invite',2,9);
                INSERT INTO invitation VALUES ('$invite','$game','PENDING'); COMMIT;
                INSERT INTO player_preparation VALUES ('$game','$a',true,'${json.encodeToString(deck)}');
                INSERT INTO game_definition VALUES ('$game','${submitted.digest}');
                INSERT INTO request_identity VALUES ('$request','$b','gameCommand','$game','${GameWire.digest(command)}');
                INSERT INTO game_inbox VALUES ('$request','$game','$b',1,'$stream',1,'${json.encodeToString<GameClientMessage>(command)}',NULL);""")
            val migrations = FoundationMigrations.flyway(db.settings)
            val pendingForwards = migrations.info().pending().count { it.version != null }
            assertTrue(pendingForwards > 0)
            assertEquals(pendingForwards, FoundationMigrations.migrate(db.settings))
            migrations.validate()
            assertEquals(0, migrations.info().pending().size)
            assertEquals(0, FoundationMigrations.migrate(db.settings))
            ServiceRuntime(db.settings).use { runtime ->
                val games = GameService(runtime); runtime.attachWorker(games::validatePending) { true }; runtime.start(); ready(runtime)
                val principal = AccountService.Principal(UUID.randomUUID(), AccountIdentity(a, "A"))
                val head = games.head(principal, game)
                assertNull(head.play); assertEquals(StarterChoice(), head.preparation!!.starter)
                assertEquals(deck, head.preparation!!.players[0].deck); assertTrue(head.preparation!!.players[0].ready)
                assertEquals(listOf(submitted), head.preparation!!.definitions)
                assertTrue(games.executeOne()); assertFalse(games.executeOne())
                assertEquals(deck, games.head(principal, game).preparation!!.players[0].deck)
                assertEquals("10", games.head(principal, game).revision)
            }
            assertEquals(0, number(db, "SELECT count(*) FROM game_play"))
            assertEquals(1, number(db, "SELECT count(*) FROM game_inbox WHERE result IS NOT NULL"))
            println("PASS non-destructive migration retains prepared snapshots and pending requests, defaults legacy unset starter to Random without rolling or starting")
        }
    }
}
