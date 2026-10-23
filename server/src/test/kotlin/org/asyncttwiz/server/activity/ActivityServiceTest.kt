package org.asyncttwiz.server.activity

import java.time.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.asyncttwiz.server.lobby.LobbyService
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class ActivityFixture : AutoCloseable {
    val db = OwnedPostgres().start()
    val runtime: ServiceRuntime
    val accounts: AccountService
    val a: AccountService.Principal
    val b: AccountService.Principal
    val tokenA: String
    val tokenB: String
    val lobby: LobbyService
    init {
        FoundationMigrations.migrate(db.settings)
        runtime = ServiceRuntime(db.settings); runtime.start()
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!runtime.ready()) { check(System.nanoTime() < end); Thread.sleep(20) }
        accounts = AccountService(runtime)
        accounts.register("Inviter", "inviter@example.invalid", "test password")
        accounts.register("Recipient", "recipient@example.invalid", "test password")
        tokenA = accounts.login("inviter@example.invalid", "test password")!!.token
        tokenB = accounts.login("recipient@example.invalid", "test password")!!.token
        a = accounts.authenticate(tokenA)!!; b = accounts.authenticate(tokenB)!!
        lobby = LobbyService(runtime); lobby.add(a, AddContact(id(), b.account.accountId))
    }
    fun create(): GameSummary = GameWire.json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Private table")).body)
    fun accept(game: GameSummary): GameSummary {
        val games = org.asyncttwiz.server.game.GameService(runtime)
        val hello = GameHello(id(), id()); val connection = UUID.randomUUID()
        val stream = games.open(b, hello, connection, game.id, id())
        games.admit(b, hello, connection, GameCommand(id(), GameScope(game.id, game.preparationRound, game.invitationId),
            GameAction.AcceptInvitation, stream.streamId, stream.generation))
        check(games.executeOne())
        return games.head(b, game.id).summary
    }
    fun sql(text: String) { db.connection().use { it.createStatement().execute(text) } }
    fun number(text: String): Long = db.connection().use { c -> c.createStatement().executeQuery(text).use { it.next(); it.getLong(1) } }
    fun record(game: GameSummary, group: String, time: Instant, owner: AccountService.Principal = b, author: AccountService.Principal = a) {
        runtime.transaction { connection -> ActivityService.record(DSL.using(connection, SQLDialect.POSTGRES),
            UUID.fromString(owner.account.accountId), ActivityKind.GAME, UUID.fromString(game.id), UUID.fromString(group),
            UUID.fromString(author.account.accountId), OffsetDateTime.ofInstant(time, ZoneOffset.UTC)) }
    }
    override fun close() { runtime.close(); db.close() }
    companion object { fun id() = UUID.randomUUID().toString() }
}

internal class ActivityClock(var time: Instant = Instant.now()) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = time
}

class ActivityServiceTest {
    @TempDir lateinit var directory: java.nio.file.Path
    private fun id() = ActivityFixture.id()

    @Test fun `password reset removes all old session registrations only on successful confirmation`() {
        ActivityFixture().use { f ->
            val activity = ActivityService(f.runtime)
            val second = f.accounts.authenticate(f.accounts.login("recipient@example.invalid", "test password")!!.token)!!
            activity.register(f.b, RegisterInstallation(id(), id(), "synthetic-one"))
            activity.register(second, RegisterInstallation(id(), id(), "synthetic-two"))
            f.create()
            org.asyncttwiz.server.accounts.SyntheticMailbox(directory.resolve("mail")).use { mailbox ->
                val reset = org.asyncttwiz.server.accounts.PasswordResetService(f.runtime, mailbox.settings())
                var link: String? = null
                reset.request("recipient@example.invalid")
                reset.deliverDue(org.asyncttwiz.server.accounts.ResetMailer { _, value ->
                    link = value; org.asyncttwiz.server.accounts.MailResult.DELIVERED
                })
                assertEquals(2L, f.number("SELECT count(*) FROM activity_installation"))
                assertFalse(reset.confirm("invalid", "new password"))
                assertEquals(2L, f.number("SELECT count(*) FROM activity_installation"))
                val token = java.net.URI(link!!).query.substringAfter("token=")
                assertTrue(reset.confirm(token, "new password"))
                assertEquals(0L, f.number("SELECT count(*) FROM activity_installation"))
                assertEquals(0L, f.number("SELECT count(*) FROM activity_push"))
                assertNull(f.accounts.authenticate(f.tokenB)); assertNotNull(f.accounts.authenticate(f.tokenA))
                assertEquals(1L, f.number("SELECT count(*) FROM account_activity"))
            }
            println("PASS real successful password reset atomically removes all old registrations/queued hints while request+invalid reset preserve them, unrelated account survives and committed activity is not a read receipt")
        }
    }

    @Test fun `rollback cannot publish partial invitation hint and non FCM maintenance retains truthful empty log floor`() {
        ActivityFixture().use { f ->
            f.sql("""CREATE FUNCTION fail_activity() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                BEGIN RAISE EXCEPTION 'owned failure after activity writes'; END ${'$'}${'$'};
                CREATE TRIGGER fail_activity BEFORE INSERT ON account_request FOR EACH ROW EXECUTE FUNCTION fail_activity();""")
            assertThrows(Exception::class.java) { f.create() }
            assertEquals(0L, f.number("SELECT count(*) FROM account_activity"))
            assertEquals(0L, f.number("SELECT count(*) FROM activity_cursor"))
            assertEquals(0L, f.number("SELECT count(*) FROM game"))
            f.sql("DROP TRIGGER fail_activity ON account_request; DROP FUNCTION fail_activity()")
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!f.runtime.ready()) { check(System.nanoTime() < until); Thread.sleep(20) }
            f.create()
            f.sql("UPDATE account_activity SET created_at=now()-interval '31 days'")
            ActivityDelivery(f.runtime).use { it.deliverDue() }
            assertEquals(0L, f.number("SELECT count(*) FROM account_activity"))
            assertEquals(1L, f.number("SELECT retention_floor FROM activity_cursor"))
            assertEquals(1, ActivityService(f.runtime).page(f.b, "0").entries.size)
            println("PASS injected real SQL failure rolls invitation/game/cursor/hint back together; non-FCM maintenance deletes dormant-account expired hints, keeps actual floor/current summary")
        }
    }
    @Test fun `real invitation is atomic account scoped idempotent and every registered live installation receives opaque hint`() {
        ActivityFixture().use { f ->
            val activity = ActivityService(f.runtime)
            val b2 = f.accounts.authenticate(f.accounts.login("recipient@example.invalid", "test password")!!.token)!!
            val first = RegisterInstallation(id(), id(), "synthetic-push-one")
            val second = RegisterInstallation(id(), id(), "synthetic-push-two")
            val third = RegisterInstallation(id(), id()) // non-GMS installation remains independently pollable
            val reg1 = activity.register(f.b, first); val reg2 = activity.register(b2, second)
            activity.register(f.b, third)
            assertEquals(reg1, activity.register(f.b, first))
            val initial = activity.page(f.b, null)
            assertTrue(initial.summaryReset); assertTrue(initial.entries.isEmpty())
            val input = CreateInvitation(id(), f.b.account.accountId, "Private covered-card/chat name never copied")
            val created = f.lobby.create(f.a, input)
            assertEquals(created, f.lobby.create(f.a, input))
            val page = activity.page(f.b, "0")
            assertEquals(1, page.entries.size); assertFalse(page.summaryReset)
            assertEquals(ActivityKind.INVITATION, page.entries.single().kind)
            assertEquals(page, activity.page(b2, "0"))
            assertTrue(activity.page(f.a, "0").entries.isEmpty())
            assertEquals(1L, f.number("SELECT count(*) FROM account_activity"))
            val deliveries = mutableListOf<ActivityPushHint>()
            ActivityDelivery(f.runtime, ActivityPushAdapter { _, hint -> deliveries += hint; PushResult(PushStatus.DELIVERED) }).use { it.deliverDue() }
            assertEquals(setOf(reg1.generation, reg2.generation), deliveries.map { it.generation }.toSet())
            assertEquals(2, deliveries.size)
            val encoded = FcmActivityAdapter.payload("synthetic-token", deliveries.first())
            assertFalse(encoded.contains("notification")); assertFalse(encoded.contains("Private")); assertFalse(encoded.contains(f.tokenB))
            assertTrue(encoded.contains("86400s")); assertTrue(encoded.contains("collapse_key"))
            f.accounts.revoke(f.b)
            assertEquals(1L, f.number("SELECT count(*) FROM activity_installation"))
            activity.unregister(b2, first.installationId) // cannot unregister another session/installation
            assertEquals(1L, f.number("SELECT count(*) FROM activity_installation"))
            val transfer = activity.register(f.a, second.copy(binding = id(), pushToken = null))
            assertNotEquals(reg2, transfer)
            assertNull(f.accounts.authenticate(f.tokenB))
            assertEquals(0L, f.number("SELECT count(*) FROM game_inbox"))
            println("PASS real idempotent atomic invitation hint, account/session+installation binding, every live push install and independent non-GMS cursor; opaque data-only payload, revocation cleanup and installation switch generation; no game/read commands")
        }
    }

    @Test fun `bounded delta and summary paging truthful thirty day floor empty retained log and current outstanding visibility`() {
        ActivityFixture().use { f ->
            val clock = ActivityClock(); val service = ActivityService(f.runtime, clock)
            val games = (1..103).map { f.create() }
            val delta1 = service.page(f.b, "0"); val delta2 = service.page(f.b, delta1.nextCursor)
            assertEquals(100, delta1.entries.size); assertTrue(delta1.hasMore)
            assertEquals(3, delta2.entries.size); assertFalse(delta2.hasMore)
            assertEquals("103", delta2.nextCursor)
            f.sql("UPDATE account_activity SET created_at = now() - interval '31 days' WHERE activity_id <= 2")
            val reset1 = service.page(f.b, "0")
            assertTrue(reset1.summaryReset); assertEquals("2", reset1.retentionFloor)
            assertEquals(100, reset1.entries.size); assertTrue(reset1.nextCursor.startsWith("r:103:"))
            val reset2 = service.page(f.b, reset1.nextCursor)
            assertTrue(reset2.summaryReset); assertEquals(3, reset2.entries.size); assertFalse(reset2.hasMore)
            assertEquals(games.map { it.id }.toSet(), (reset1.entries + reset2.entries).map { it.gameId }.toSet())
            assertEquals(101L, f.number("SELECT count(*) FROM account_activity"))
            // An invitation no longer outstanding and a deleted table never reappear in a reset.
            f.sql("UPDATE invitation SET state='DECLINED' WHERE game_id='${games[0].id}'; UPDATE game SET lifecycle='DELETED' WHERE id='${games[1].id}'")
            clock.time = clock.time.plus(Duration.ofDays(32))
            val allPruned = service.page(f.b, "103")
            assertEquals("103", allPruned.retentionFloor); assertFalse(allPruned.summaryReset)
            assertTrue(allPruned.entries.isEmpty()); assertEquals(0L, f.number("SELECT count(*) FROM account_activity"))
            val current = service.page(f.b, "1")
            assertTrue(current.summaryReset); assertEquals("103", current.retentionFloor)
            assertTrue(current.entries.none { it.gameId in games.take(2).map { game -> game.id } })
            for (cursor in listOf("-1", "01", "104", "r:104:0", "r:1:2", "r:foo:bar"))
                assertThrows(ActivityFailure::class.java) { service.page(f.b, cursor) }
            println("PASS real PostgreSQL 100/3 delta+reset paging, retained current summaries, actual contiguous thirty-day floor including empty log, explicit reset without historical replay and current outstanding/access filtering")
        }
    }

    @Test fun `opponent group quiet window max ten seconds retry after ttl and invalid token fallback`() {
        ActivityFixture().use { f ->
            val clock = ActivityClock(); val service = ActivityService(f.runtime, clock)
            val install = RegisterInstallation(id(), id(), "synthetic-push")
            service.register(f.b, install)
            val game = f.accept(f.create())
            clock.time = Instant.now().plusSeconds(1)
            ActivityDelivery(f.runtime, ActivityPushAdapter { _, _ -> PushResult(PushStatus.DELIVERED) }, clock).use { it.deliverDue() }
            val group = id(); val start = clock.time
            f.record(game, group, start)
            val delivered = mutableListOf<ActivityPushHint>()
            var result = PushResult(PushStatus.DELIVERED)
            ActivityDelivery(f.runtime, ActivityPushAdapter { _, hint -> delivered += hint; result }, clock).use { delivery ->
                clock.time = start.plusSeconds(1); delivery.deliverDue(); assertTrue(delivered.isEmpty())
                for (second in 1..9) { clock.time = start.plusSeconds(second.toLong()); f.record(game, group, clock.time); delivery.deliverDue() }
                assertTrue(delivered.isEmpty())
                clock.time = start.plusSeconds(10); delivery.deliverDue(); assertEquals(1, delivered.size)
                assertEquals(group, delivered.single().identity)
                // Same group may extend after an alert; client updates its per-game identity silently.
                f.record(game, group, clock.time); clock.time = clock.time.plusSeconds(2)
                result = PushResult(PushStatus.TRANSIENT, 120); delivery.deliverDue(); assertEquals(2, delivered.size)
                clock.time = clock.time.plusSeconds(119); delivery.deliverDue(); assertEquals(2, delivered.size)
                clock.time = clock.time.plusSeconds(1); result = PushResult(PushStatus.INVALID_REGISTRATION)
                delivery.deliverDue(); assertEquals(3, delivered.size)
                assertEquals(0L, f.number("SELECT count(*) FROM activity_installation WHERE push_token IS NOT NULL"))
                f.record(game, id(), clock.time); clock.time = clock.time.plusSeconds(2); delivery.deliverDue()
                assertEquals(3, delivered.size)
                assertFalse(service.page(f.b, "1").entries.isEmpty()) // invalid push never removes polling/activity
                service.register(f.b, install)
                assertEquals(0L, f.number("SELECT count(*) FROM activity_installation WHERE push_token IS NOT NULL"))
                service.register(f.b, install.copy(pushToken = "synthetic-new-token"))
                f.record(game, id(), clock.time); clock.time = clock.time.plus(Duration.ofHours(25)); delivery.deliverDue()
                assertEquals(3, delivered.size); assertEquals(0L, f.number("SELECT count(*) FROM activity_push"))
            }
            val before = f.number("SELECT high_water FROM activity_cursor WHERE account_id='${f.b.account.accountId}'")
            f.record(game, id(), clock.time, author = f.b)
            assertEquals(before, f.number("SELECT high_water FROM activity_cursor WHERE account_id='${f.b.account.accountId}'"))
            assertEquals(listOf(60L, 300L, 900L, 3600L, 3600L), (0..4).map(ActivityDelivery::retrySeconds))
            println("PASS synthetic opponent-group adapter with real durable 2s quiet/max10s queue, post-alert extension, Retry-After, bounded TTL/permanent invalid registration fallback and own-activity exclusion; not final gameplay group integration")
        }
    }
}
