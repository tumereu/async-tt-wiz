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

internal fun gameplayId() = UUID.randomUUID().toString()
internal fun gameplayAwait(check: () -> Boolean) {
    val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
    while (!check()) { assertTrue(System.nanoTime() < end, "Timed out"); Thread.sleep(20) }
}
internal class GameplayFixture(val runtime: ServiceRuntime) {
    val games = GameService(runtime)
    private fun account(name: String): AccountService.Principal {
        val accounts = AccountService(runtime)
        accounts.register(name, "$name@example.invalid", "synthetic password")
        return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
    }
    val a = account("PlayA"); val b = account("PlayB")
    val game: GameSummary
    val owner: Stream
    val recipient: Stream
    val root: PlayState
    init {
        val lobby = LobbyService(runtime)
        lobby.add(a, AddContact(gameplayId(), b.account.accountId))
        game = GameWire.json.decodeFromString(lobby.create(a, CreateInvitation(gameplayId(), b.account.accountId, "Gameplay Spark")).body)
        owner = Stream(a); recipient = Stream(b)
        fun deck(actor: AccountService.Principal): SavedDeck {
            val definition = CardDefinition("Synthetic retained", listOf(DefinitionFace(gameplayId(), "Face", corner = "3/5")))
            val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
            val decks = DeckService(runtime)
            assertEquals(200, decks.submit(actor, SubmitDefinitions(gameplayId(), listOf(submitted))).status)
            return GameWire.json.decodeFromString(decks.create(actor, CreateDeck(gameplayId(), "Source", listOf(
                SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 5)))).body)
        }
        val ad = deck(a); val bd = deck(b)
        owner.execute(owner.lobby(GameAction.SelectDeck(ad.deck.id, ad.deck.revision)))
        owner.execute(owner.lobby(GameAction.SetStarter(StarterChoice(a.account.accountId))))
        recipient.execute(recipient.lobby(GameAction.AcceptInvitation))
        recipient.execute(recipient.lobby(GameAction.SelectDeck(bd.deck.id, bd.deck.revision)))
        owner.execute(owner.lobby(GameAction.Ready)); recipient.execute(recipient.lobby(GameAction.Ready))
        root = games.head(a, game.id).play!!
    }
    inner class Stream(val actor: AccountService.Principal) {
        val hello = GameHello(gameplayId(), gameplayId()); val connection = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, game.id, gameplayId())
        fun lobby(action: GameAction) = GameCommand(gameplayId(), GameScope(game.id, game.preparationRound,
            if (action == GameAction.AcceptInvitation) game.invitationId else null), action, stream.streamId, stream.generation)
        fun command(action: PlayAction) = GameCommand(gameplayId(), GameScope(game.id, playId = root.id),
            GameAction.Play(action), stream.streamId, stream.generation)
        fun admit(input: GameCommand) = games.admit(actor, hello, connection, input)
        fun result(input: GameCommand) = games.results(actor, game.id, listOf(input.requestId)).statuses.single().result!!
        fun execute(input: GameCommand): GameResult {
            assertTrue(admit(input) is GameReceipt); assertTrue(games.executeOne()); return result(input)
        }
    }
}

class GameplayDurabilityTest {
    private fun sql(db: OwnedPostgres, statement: String) { db.connection().use { it.createStatement().execute(statement) } }
    private fun number(db: OwnedPostgres, statement: String): Long = db.connection().use { c ->
        c.createStatement().executeQuery(statement).use { it.next(); it.getLong(1) } }

    @Test fun `ordered durable gameplay applies compatible edits actual tuples and individual guarded moves`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val fixture = GameplayFixture(runtime); val games = fixture.games
                val a = fixture.a; val root = fixture.root
                val library = root.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == a.account.accountId }
                val hand = root.board.containers.single { it.zone == PlayZone.HAND && it.accountId == a.account.accountId }
                val field = root.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == a.account.accountId }
                val first = library.cards.first()
                val draw = PlayAction.MoveCard(first, MoveSource(library.id, edge = MoveSourceEdge.TOP), MoveDestination(hand.id, MoveInsertion.UNORDERED))
                val inputs = listOf(fixture.owner.command(PlayAction.AdjustLife(a.account.accountId, "1")),
                    fixture.recipient.command(PlayAction.AdjustLife(a.account.accountId, "1")), fixture.owner.command(draw),
                    fixture.recipient.command(PlayAction.SetTapped(first, true)), fixture.recipient.command(draw),
                    fixture.owner.command(PlayAction.MoveCard(first, MoveSource(hand.id), MoveDestination(field.id, MoveInsertion.EMPTY, BoardPosition(1024, 0)))))
                val streams = listOf(fixture.owner, fixture.recipient, fixture.owner, fixture.recipient, fixture.recipient, fixture.owner)
                val receipts = inputs.mapIndexed { index, input -> streams[index].admit(input) as GameReceipt }
                assertEquals(receipts[0], fixture.owner.admit(inputs[0]))
                assertEquals((receipts.first().ingressOrdinal.toLong()..receipts.last().ingressOrdinal.toLong()).map { it.toString() }, receipts.map { it.ingressOrdinal })
                repeat(inputs.size) { assertTrue(games.executeOne()) }; assertFalse(games.executeOne())
                val results = inputs.mapIndexed { index, input -> streams[index].result(input) }
                assertEquals(listOf("accepted", "accepted", "accepted", "accepted", "rejected", "accepted"), results.map { it.outcome })
                assertEquals("movementConflict", results[4].reason); assertNull(results[4].mutation); assertNull(results[4].commitRevision)
                assertEquals(GameplayFieldChange(a.account.accountId, GameplayField.LIFE, "21", "22"), results[1].mutation!!.transition.fields.single())
                assertEquals("22", games.head(a, fixture.game.id).play!!.board.players.single { it.accountId == a.account.accountId }.life)
                assertTrue(games.head(a, fixture.game.id).play!!.board.cards.single { it.id == first }.tapped)
                assertEquals(2, results.last().createdIds.size)
                assertEquals(CommandIds.uuidV5(inputs.last().requestId, "board-node"), results.last().createdIds.first())
                val accepted = games.head(a, fixture.game.id)
                val noOp = fixture.owner.command(PlayAction.SetTapped(first, true))
                assertEquals("noOp", fixture.owner.execute(noOp).outcome)
                assertEquals(accepted, games.head(a, fixture.game.id))
                assertEquals(results.first(), fixture.owner.admit(inputs.first()))
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) {
                    fixture.owner.admit(inputs.first().copy(action = GameAction.Play(PlayAction.SetLife(a.account.accountId, "0")))) }.code)
                val stale = fixture.owner.command(PlayAction.SetLife(a.account.accountId, "0")).let { it.copy(scope = it.scope.copy(playId = gameplayId())) }
                assertEquals("inapplicable", fixture.owner.execute(stale).reason)
                val history = games.history(a, fixture.game.id, root.id, "0")
                assertEquals(5, history.mutations.size)
                var replay = root
                history.mutations.forEach { mutation -> replay = GameplayHistory.replay(replay, mutation).state!! }
                assertEquals(accepted.play, replay)
                assertEquals(5, number(db, "SELECT count(*) FROM gameplay_mutation"))
                assertEquals(accepted.revision.toLong(), number(db, "SELECT count(*) FROM game_revision"))
                assertEquals(inputs.size.toLong() + 6 + 2, number(db, "SELECT count(*) FROM game_outbox"))
                println("PASS durable FIFO/pending dedup, current-value life 20->21->22, tap after move preserves unrelated state, exact-source stale draw rejects without replacement, stable node/pile outputs, no-op/rejection no revision, original outcomes and real replayable mutation/group rows")
            }
        }
    }

    @Test fun `late transaction failure rolls mutations grouping materialization result revision and outbox back then worker resumes once`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var pending: GameCommand; lateinit var actor: AccountService.Principal; lateinit var before: GameHead
            lateinit var hello: GameHello
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val fixture = GameplayFixture(runtime); actor = fixture.a
                before = fixture.games.head(actor, fixture.game.id)
                pending = fixture.owner.command(PlayAction.AdjustLife(actor.account.accountId, "-5")); hello = fixture.owner.hello
                fixture.owner.admit(pending)
                sql(db, """CREATE FUNCTION fail_gameplay_commit() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN RAISE EXCEPTION 'owned fault after gameplay state and result writes'; END ${'$'}${'$'};
                    CREATE TRIGGER fail_gameplay_commit BEFORE INSERT ON game_outbox FOR EACH ROW EXECUTE FUNCTION fail_gameplay_commit();""")
                assertThrows(Exception::class.java) { fixture.games.executeOne() }
                gameplayAwait(runtime::ready) // SQL faults invalidate writer ownership until its ordinary recovery tick.
                assertEquals(before, fixture.games.head(actor, fixture.game.id))
                assertEquals(0, number(db, "SELECT count(*) FROM gameplay_mutation")); assertEquals(0, number(db, "SELECT count(*) FROM gameplay_group"))
                assertEquals(before.revision.toLong(), number(db, "SELECT count(*) FROM game_revision"))
                assertEquals(before.revision.toLong(), number(db, "SELECT count(*) FROM game_outbox"))
                assertEquals("pending", fixture.games.results(actor, fixture.game.id, listOf(pending.requestId)).statuses.single().status)
                sql(db, "DROP TRIGGER fail_gameplay_commit ON game_outbox; DROP FUNCTION fail_gameplay_commit()")
            }
            lateinit var accepted: GameHead; lateinit var result: GameResult
            ServiceRuntime(db.settings).use { runtime ->
                val hub = GameHub(runtime); runtime.attachWorker(hub::validatePending, hub::available)
                try {
                    hub.start(); runtime.start(); gameplayAwait(runtime::ready)
                    gameplayAwait { hub.games.results(actor, before.gameId, listOf(pending.requestId)).statuses.single().status == "final" }
                    accepted = hub.games.head(actor, before.gameId)
                    result = hub.games.results(actor, before.gameId, listOf(pending.requestId)).statuses.single().result!!
                    assertEquals("accepted", result.outcome); assertEquals("15", accepted.play!!.board.players.single { it.accountId == actor.account.accountId }.life)
                    val connection = UUID.randomUUID()
                    val resumed = hub.games.open(actor, hello, connection, before.gameId, pending.streamId, listOf(pending.requestId))
                    assertEquals(result, resumed.statuses.single().result)
                    assertEquals(result, hub.games.admit(actor, hello, connection, pending.copy(generation = resumed.generation)))
                    assertEquals(accepted, hub.games.head(actor, before.gameId))
                    assertEquals(1, number(db, "SELECT count(*) FROM gameplay_mutation")); assertEquals(1, number(db, "SELECT count(*) FROM gameplay_group"))
                } finally { hub.close() }
            }
            ServiceRuntime(db.settings).use { runtime ->
                val games = GameService(runtime); runtime.start(); gameplayAwait(runtime::ready)
                assertEquals(accepted, games.head(actor, before.gameId)); assertFalse(games.executeOne())
                assertEquals(result, games.results(actor, before.gameId, listOf(pending.requestId)).statuses.single().result)
            }
            assertEquals(0, number(db, "SELECT count(*) FROM pg_locks WHERE locktype='advisory'"))
            println("PASS actual PostgreSQL late fault atomic rollback of node/group/materialized board/revision/outcome/outbox; committed pending original survives runtime restart, production worker recovers once, stream/final retry and second restart retain concrete transition")
        }
    }

    @Test fun `V9 retained current play and original outcomes migrate forward without rewriting board or initialization`() {
        OwnedPostgres().start().use { db ->
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("9").load().migrate()
            // A current executable correctly refuses readiness on an unmigrated schema. Seed a retained V9
            // serialization fixture via JDBC instead of bypassing that validation to run a V11 service on V9.
            val a = gameplayId(); val b = gameplayId(); val game = gameplayId(); val invite = gameplayId()
            val round = gameplayId(); val start = gameplayId(); val play = gameplayId(); val rootId = gameplayId(); val branch = gameplayId()
            val library = gameplayId(); val hand = gameplayId(); val stack = gameplayId(); val card = gameplayId(); val face = gameplayId()
            val definition = CardDefinition("Retained V9", listOf(DefinitionFace(face, "Retained face", corner = "2/4")))
            val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
            val before = PlayState(game, play, round, start, PlayRoot(rootId, branch), rootId,
                BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), listOf(PlayCard(card, submitted.digest, a, library, face, corner = "2/4")),
                    listOf(PlayContainer(library, PlayZone.LIBRARY, a, listOf(card)), PlayContainer(hand, PlayZone.HAND, a),
                        PlayContainer(stack, PlayZone.STACK)), TurnState(a)), listOf(submitted),
                listOf(InitialShuffle(a, "00".repeat(32), listOf(card))), InitialStarter(StarterChoice(a), a))
            val command = GameCommand(start, GameScope(game, round), GameAction.Ready, gameplayId(), "1")
            val result = GameResult(start, command.scope, "accepted", ingressOrdinal = "1", commitRevision = "7", createdIds = listOf(play, rootId))
            val json = GameWire.json
            val legacy = json.encodeToString(before).replace(",\"placement\":null", "").replace(",\"currentGroup\":null", "")
            sql(db, """INSERT INTO account(id,username,username_key,email_delivery,email_key,password_phc) VALUES
                ('$a','OldA','olda','olda@example.invalid','olda@example.invalid','retained'),
                ('$b','OldB','oldb','oldb@example.invalid','oldb@example.invalid','retained');
                BEGIN;
                INSERT INTO game(id,game_name,inviter_id,recipient_id,preparation_round,current_invitation,revision,next_ingress)
                VALUES ('$game','Retained gameplay','$a','$b','$round','$invite',7,2);
                INSERT INTO invitation VALUES ('$invite','$game','ACCEPTED');
                INSERT INTO request_identity VALUES ('$start','$a','gameCommand','$game','${GameWire.digest(command)}');
                INSERT INTO game_inbox VALUES ('$start','$game','$a',1,'${command.streamId}',1,'${json.encodeToString<GameClientMessage>(command)}','${json.encodeToString(result)}');
                INSERT INTO game_play VALUES ('$play','$game','$round','$start','$rootId','$legacy');
                UPDATE game SET lifecycle='ACTIVE', current_play='$play' WHERE id='$game';
                COMMIT;""")
            val forwards = FoundationMigrations.flyway(db.settings).info().pending().size
            assertTrue(forwards > 0); assertEquals(forwards, FoundationMigrations.migrate(db.settings))
            FoundationMigrations.flyway(db.settings).validate()
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready); val games = GameService(runtime)
                val actor = AccountService.Principal(UUID.randomUUID(), AccountIdentity(a, "OldA"))
                assertEquals(before, games.head(actor, game).play)
                assertEquals(result, games.results(actor, game, listOf(start)).statuses.single().result)
                val connection = UUID.randomUUID(); val hello = GameHello(gameplayId(), gameplayId())
                val stream = games.open(actor, hello, connection, game, gameplayId())
                val input = GameCommand(gameplayId(), GameScope(game, playId = before.id),
                    GameAction.Play(PlayAction.AdjustLife(a, "1")), stream.streamId, stream.generation)
                games.admit(actor, hello, connection, input); games.executeOne()
                assertEquals(before.currentNodeId, games.history(actor, game, before.id, "0").mutations.single().parentId)
                assertEquals(1, number(db, "SELECT count(*) FROM game_play"))
                assertEquals(1, number(db, "SELECT count(*) FROM gameplay_mutation"))
            }
            println("PASS explicit V9->discovered forwards preserves current incarnation/root/shuffles/board/definitions/readiness/results, nullable placement/group defaults decode retained payload and first gameplay mutation links to retained root")
        }
    }

    @Test fun `history paging retains ordered records and references only this play and authorized game`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready); val f = GameplayFixture(runtime)
                val commands = (1..103).map { f.owner.command(PlayAction.AdjustLife(f.a.account.accountId, "1")) }
                commands.forEach { f.owner.admit(it) }; repeat(commands.size) { assertTrue(f.games.executeOne()) }
                val first = f.games.history(f.a, f.game.id, f.root.id, "0")
                assertEquals(100, first.mutations.size); assertNotNull(first.next)
                val second = f.games.history(f.a, f.game.id, f.root.id, first.next!!)
                assertEquals(3, second.mutations.size); assertNull(second.next)
                assertEquals(commands.map { it.requestId }, (first.mutations + second.mutations).map { it.requestId })
                assertEquals(first.groups, second.groups); assertEquals("103", first.groups.single().count)
                assertEquals("inapplicable", assertThrows(GameProtocolFailure::class.java) { f.games.history(f.a, f.game.id, gameplayId(), "0") }.code)
                val stranger = AccountService.Principal(UUID.randomUUID(), AccountIdentity(gameplayId(), "Other"))
                assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) { f.games.history(stranger, f.game.id, f.root.id, "0") }.code)
                println("PASS 103 actual separate committed edits with one server-derived group; bounded stable ordinal paging, no missing/duplicate records, same-play and participant access enforced")
            }
        }
    }
}
