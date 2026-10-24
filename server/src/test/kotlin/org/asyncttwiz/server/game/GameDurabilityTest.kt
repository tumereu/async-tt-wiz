package org.asyncttwiz.server.game

import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.asyncttwiz.server.lobby.LobbyService
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameDurabilityTest {
    private fun id() = UUID.randomUUID().toString()
    private fun sql(db: OwnedPostgres, sql: String) { db.connection().use { it.createStatement().execute(sql) } }
    private fun number(db: OwnedPostgres, sql: String): Long = db.connection().use { c -> c.createStatement().executeQuery(sql).use { it.next(); it.getLong(1) } }
    private fun await(check: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!check()) { assertTrue(System.nanoTime() < end, "Timed out"); Thread.sleep(20) }
    }
    private fun principal(runtime: ServiceRuntime, name: String): AccountService.Principal {
        val accounts = AccountService(runtime)
        accounts.register(name, "$name@example.invalid", "synthetic password")
        return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
    }
    private fun create(runtime: ServiceRuntime, a: AccountService.Principal, b: AccountService.Principal): GameSummary {
        val lobby = LobbyService(runtime)
        lobby.add(a, AddContact(id(), b.account.accountId))
        return GameWire.json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Durable Spark")).body)
    }
    private fun command(game: GameSummary, stream: GameStreamReady, action: GameAction = GameAction.DeclineInvitation) =
        GameCommand(id(), GameScope(game.id, game.preparationRound, game.invitationId), action, stream.streamId, stream.generation)

    @Test fun `committed pending dedup atomic execution rollback restart and original final recovery`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var b: AccountService.Principal; lateinit var game: GameSummary; lateinit var original: GameCommand
            val hello = GameHello(id(), id()); val connection = UUID.randomUUID(); val streamId = id()
            ServiceRuntime(db.settings).use { runtime ->
                val games = GameService(runtime); runtime.attachWorker({ games.validatePending() }, { true }); runtime.start(); await(runtime::ready)
                val a = principal(runtime, "RollbackOwner"); b = principal(runtime, "RollbackRecipient"); game = create(runtime, a, b)
                val stream = games.open(b, hello, connection, game.id, streamId)
                original = command(game, stream)
                val receipt = games.admit(b, hello, connection, original) as GameReceipt
                assertEquals("1", receipt.ingressOrdinal)
                // A separate connection sees the durable row by the time the receipt can be returned.
                assertEquals(1, number(db, "SELECT count(*) FROM game_inbox WHERE result IS NULL"))
                assertEquals(receipt, games.admit(b, hello, connection, original))
                assertEquals("pending", games.results(b, game.id, listOf(original.requestId)).statuses.single().status)
                sql(db, """CREATE FUNCTION fail_game_commit() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN RAISE EXCEPTION 'owned test failure after state revision and result writes'; END ${'$'}${'$'};
                    CREATE TRIGGER fail_game_commit BEFORE INSERT ON game_outbox FOR EACH ROW EXECUTE FUNCTION fail_game_commit();""")
                assertThrows(Exception::class.java) { games.executeOne() }
                assertEquals(0, number(db, "SELECT revision FROM game"))
                assertEquals(1, number(db, "SELECT count(*) FROM invitation WHERE state='PENDING'"))
                assertEquals(1, number(db, "SELECT count(*) FROM game_inbox WHERE result IS NULL"))
                assertEquals(0, number(db, "SELECT count(*) FROM game_revision")); assertEquals(0, number(db, "SELECT count(*) FROM game_outbox"))
                sql(db, "DROP TRIGGER fail_game_commit ON game_outbox; DROP FUNCTION fail_game_commit()")
            }
            ServiceRuntime(db.settings).use { runtime ->
                val games = GameService(runtime); runtime.attachWorker({ games.validatePending() }, { true }); runtime.start(); await(runtime::ready)
                // Restart validates and resumes the actual committed pending row, not a new request.
                assertTrue(games.executeOne()); assertFalse(games.executeOne())
                val resumed = games.open(b, hello, UUID.randomUUID(), game.id, streamId, listOf(original.requestId, id()))
                assertEquals("2", resumed.generation)
                assertEquals(listOf("final", "notAdmitted"), resumed.statuses.map { it.status })
                val result = resumed.statuses.first().result!!
                assertEquals("accepted", result.outcome); assertEquals("1", result.commitRevision)
                assertEquals(result, games.results(b, game.id, listOf(original.requestId)).statuses.single().result)
                assertEquals(1, number(db, "SELECT count(*) FROM game_inbox")); assertEquals(2, number(db, "SELECT next_ingress FROM game"))
                assertEquals(1, number(db, "SELECT count(*) FROM game_revision")); assertEquals(1, games.publications().size)
                games.published(games.publications().single().id); assertTrue(games.publications().isEmpty())
                assertEquals("DECLINED", games.head(b, game.id).summary.invitationState.name)
            }
            assertEquals(0, number(db, "SELECT count(*) FROM pg_locks WHERE locktype='advisory'"))
            println("PASS committed receipt/pending dedup; real SQL failure rolls state/result/revision/outbox back together; restart resumes lowest pending once, retained final/negative status only after cutover, independent publication recovery and exact advisory cleanup")
        }
    }

    @Test fun `FIFO enqueue precedes asynchronous database admission and unrelated games progress`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); await(runtime::ready)
                val a = principal(runtime, "FifoOwner"); val b = principal(runtime, "FifoRecipient")
                val first = create(runtime, a, b); val second = create(runtime, a, b)
                val games = GameService(runtime); val hello = GameHello(id(), id()); val connection = UUID.randomUUID()
                val stream = games.open(b, hello, connection, first.id, id())
                val independent = games.open(b, hello, connection, second.id, id())
                GameDispatcher().use { dispatcher ->
                    val entered = CountDownLatch(1); val release = CountDownLatch(1)
                    val gate = dispatcher.submit(first.id) { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    val commands = listOf(command(first, stream), command(first, stream, GameAction.AcceptInvitation))
                    val queued = commands.map { input -> dispatcher.submit(first.id) { games.admit(b, hello, connection, input) } }
                    val other = dispatcher.submit(second.id) { games.admit(b, hello, connection, command(second, independent)) }
                    assertEquals("1", (other.get(5, TimeUnit.SECONDS) as GameReceipt).ingressOrdinal)
                    assertTrue(queued.none { it.isDone })
                    release.countDown(); gate.get(5, TimeUnit.SECONDS)
                    assertEquals(listOf("1", "2"), queued.map { (it.get(5, TimeUnit.SECONDS) as GameReceipt).ingressOrdinal })
                    repeat(3) { assertTrue(games.executeOne()) }
                    val results = games.results(b, first.id, commands.map { it.requestId }).statuses.map { it.result!! }
                    assertEquals(listOf("accepted", "rejected"), results.map { it.outcome })
                    assertEquals("inapplicable", results.last().reason)
                }
            }
            println("PASS deliberately delayed earlier per-game dispatcher work cannot be overtaken by later asynchronous admissions; another game admits independently; lowest durable ordinal executes before newer commands")
        }
    }

    @Test fun `stream cutover is installation scoped global identities conflict and no sequence holes`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); await(runtime::ready)
                val a = principal(runtime, "FenceOwner"); val b = principal(runtime, "FenceRecipient")
                val game = create(runtime, a, b); val second = create(runtime, a, b)
                val games = GameService(runtime); val hello = GameHello(id(), id()); val connection = UUID.randomUUID()
                val stream = games.open(b, hello, connection, game.id, id())
                val original = command(game, stream); games.admit(b, hello, connection, original)
                val otherHello = GameHello(id(), id()); val otherConnection = UUID.randomUUID()
                val other = games.open(b, otherHello, otherConnection, game.id, id())
                val freshConnection = UUID.randomUUID(); val missing = id()
                val resumed = games.open(b, hello, freshConnection, game.id, stream.streamId, listOf(original.requestId, missing))
                assertEquals(listOf("pending", "notAdmitted"), resumed.statuses.map { it.status })
                val fenced = assertThrows(GameProtocolFailure::class.java) { games.admit(b, hello, connection, command(game, stream)) }
                assertEquals("streamFenced", fenced.code)
                assertEquals("1", (games.admit(b, hello, freshConnection, original.copy(generation = resumed.generation)) as GameReceipt).ingressOrdinal)
                val next = command(game, other, GameAction.AcceptInvitation)
                assertEquals("2", (games.admit(b, otherHello, otherConnection, next) as GameReceipt).ingressOrdinal)
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) {
                    games.admit(b, hello, freshConnection, original.copy(generation = resumed.generation, action = GameAction.AcceptInvitation)) }.code)
                val otherGame = games.open(b, hello, freshConnection, second.id, id())
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) {
                    games.admit(b, hello, freshConnection, command(second, otherGame).copy(requestId = original.requestId)) }.code)
                val aHello = GameHello(id(), id()); val aConnection = UUID.randomUUID()
                val aStream = games.open(a, aHello, aConnection, game.id, id())
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) {
                    games.admit(a, aHello, aConnection, original.copy(streamId = aStream.streamId, generation = aStream.generation)) }.code)
                val httpIdentity = id(); LobbyService(runtime).add(b, AddContact(httpIdentity, a.account.accountId))
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) {
                    games.admit(b, otherHello, otherConnection, next.copy(requestId = httpIdentity)) }.code)
                assertEquals("invalidInput", assertThrows(GameProtocolFailure::class.java) {
                    games.admit(b, otherHello, otherConnection, next.copy(requestId = id(), commandVersion = 2)) }.code)
                val tail = next.copy(requestId = missing)
                assertEquals("3", (games.admit(b, otherHello, otherConnection, tail) as GameReceipt).ingressOrdinal)
                repeat(3) { assertTrue(games.executeOne()) }; assertFalse(games.executeOne())
                assertEquals("accepted", games.results(b, game.id, listOf(original.requestId)).statuses.single().result!!.outcome)
                assertEquals("unknown", games.results(b, game.id, listOf(id())).statuses.single().status)
                assertEquals(3, number(db, "SELECT count(*) FROM game_inbox"))
            }
            println("PASS pending admission survives replacement, old connection fenced, independent installation remains writable, duplicate keeps original ordinal, account/kind/game/intent collisions private, invalid/unadmitted frames reserve no hole and tail continues")
        }
    }

    @Test fun `additive worker readiness recovery ownership epoch and bounded close`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val runtime = ServiceRuntime(db.settings); val hub = GameHub(runtime)
            val mailAvailable = AtomicBoolean(true); val mailReloads = AtomicInteger(); val gameReloads = AtomicInteger()
            runtime.attachWorker({ mailReloads.incrementAndGet() }, mailAvailable::get)
            runtime.attachWorker({ hub.validatePending(); gameReloads.incrementAndGet() }, hub::available)
            try {
                hub.start(); runtime.start(); await(runtime::ready)
                assertTrue(mailReloads.get() >= 1); assertEquals(mailReloads.get(), gameReloads.get())
                mailAvailable.set(false); assertFalse(runtime.ready()); mailAvailable.set(true); assertTrue(runtime.ready())
                val prior = gameReloads.get()
                sql(db, "SELECT pg_terminate_backend(pid) FROM pg_locks WHERE locktype='advisory' AND granted")
                await { gameReloads.get() > prior && runtime.ready() }
                assertEquals(mailReloads.get(), gameReloads.get())
                hub.close(); assertFalse(runtime.ready())
                assertTimeout(Duration.ofSeconds(15)) { runtime.close() }
                assertFalse(runtime.ready()); assertFalse(hub.available())
                assertEquals(0, number(db, "SELECT count(*) FROM pg_locks WHERE locktype='advisory'"))
            } finally { runtime.close(); hub.close() }
            println("PASS all worker hooks retained (including prior mail dependency), game ingress/execution/publication availability gates readiness; real ownership loss advances/revalidates both hooks before recovery; closed workers stop readiness and bounded close releases ownership")
        }
    }

    @Test fun `explicit V6 forward migration retains games attempts account deck and original creation`() {
        OwnedPostgres().start().use { db ->
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("6").load().migrate()
            val a = id(); val b = id(); val game = id(); val invite = id(); val preparation = id(); val request = id(); val deck = id()
            sql(db, """INSERT INTO account(id,username,username_key,email_delivery,email_key,password_phc) VALUES
                ('$a','A','a','a@example.invalid','a@example.invalid','retained'),('$b','B','b','b@example.invalid','b@example.invalid','retained');
                INSERT INTO saved_deck(id,account_id,name,revision) VALUES ('$deck','$a','Retained deck',9);
                INSERT INTO game(id,game_name,inviter_id,recipient_id,preparation_round) VALUES ('$game','Retained Spark','$a','$b','$preparation');
                INSERT INTO invitation VALUES ('$invite','$game','PENDING');
                INSERT INTO request_identity VALUES ('$request','$a','createInvitation','account','${"0".repeat(64)}');
                INSERT INTO account_request VALUES ('$request',201,'retained-original-create');""")
            assertEquals(10, FoundationMigrations.migrate(db.settings)); assertEquals(0, FoundationMigrations.migrate(db.settings))
            assertEquals(1, number(db, "SELECT count(*) FROM game WHERE current_invitation='$invite' AND preparation_round='$preparation' AND revision=0 AND next_ingress=1 AND game_name='Retained Spark'"))
            assertEquals(1, number(db, "SELECT count(*) FROM saved_deck WHERE revision=9"))
            assertEquals(1, number(db, "SELECT count(*) FROM account_request WHERE response='retained-original-create'"))
            assertEquals(0, number(db, "SELECT count(*) FROM game_inbox"))
            println("PASS explicit V6-to-current forward migration retains original game/preparation/attempt/name/date/accounts/deck revision/account outcome; new queues/preparation empty and original attempt pointer initialized")
        }
    }
}
