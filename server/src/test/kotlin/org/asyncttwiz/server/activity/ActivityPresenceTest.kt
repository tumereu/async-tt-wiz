package org.asyncttwiz.server.activity

import java.time.*
import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ActivityPresenceTest {
    private fun id() = UUID.randomUUID().toString()
    private val start = OffsetDateTime.parse("2026-10-27T12:00:00Z")

    @Test fun `recipient present suppresses actor left or expired is immediate and idle thresholds are exact`() {
        fun due(seconds: Long, yourMove: Boolean, actor: List<PresenceLease>, recipient: List<PresenceLease> = emptyList()) =
            ActivityTiming.due(actor, recipient, start, yourMove, start.plusSeconds(seconds))
        assertFalse(due(0, true, emptyList(), listOf(PresenceLease(start, start))))
        assertTrue(due(0, false, emptyList())) // explicit left
        assertTrue(due(15, false, listOf(PresenceLease(start, start)))) // expired, not 60s
        assertFalse(due(9, true, listOf(PresenceLease(start.plusSeconds(5), start))))
        assertTrue(due(10, true, listOf(PresenceLease(start.plusSeconds(10), start))))
        // PostgreSQL projections may carry the DB session's offset rather than UTC.
        val east = start.withOffsetSameInstant(ZoneOffset.ofHours(2))
        assertTrue(due(10, true, listOf(PresenceLease(east.plusSeconds(10), east))))
        assertFalse(ActivityTiming.present(listOf(PresenceLease(east, east)), start.plusSeconds(15)))
        assertFalse(due(59, false, listOf(PresenceLease(start.plusSeconds(55), start))))
        assertTrue(due(60, false, listOf(PresenceLease(start.plusSeconds(60), start))))
        assertFalse(due(10, true, listOf(PresenceLease(start.plusSeconds(10), start.plusSeconds(5)))))
        assertTrue(due(15, true, listOf(PresenceLease(start.plusSeconds(15), start.plusSeconds(5)))))
        // Another live device's input resets the account-wide idle window too.
        assertFalse(due(60, false, listOf(PresenceLease(start.plusSeconds(60), start), PresenceLease(start.plusSeconds(60), start.plusSeconds(59)))))
    }

    @Test fun `durable presence gates scheduler and polling without holding the sync cursor and opening clears unseen`() {
        ActivityFixture().use { f ->
            val game = f.accept(f.create()); val clock = ActivityClock(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
            val service = ActivityService(f.runtime, clock)
            service.register(f.b, RegisterInstallation(id(), id(), "push-one"))
            // Remove the earlier immediate invitation, isolating game notice delivery.
            f.sql("DELETE FROM activity_push")
            val actor = GamePresence(game.id, id(), true)
            service.heartbeat(f.a, actor)
            val first = clock.time
            fun record(yourMove: Boolean, chat: Boolean = false) = f.runtime.transaction { connection ->
                ActivityService.record(DSL.using(connection, SQLDialect.POSTGRES), UUID.fromString(f.b.account.accountId),
                    ActivityKind.GAME, UUID.fromString(game.id), UUID.randomUUID(), UUID.fromString(f.a.account.accountId),
                    OffsetDateTime.ofInstant(clock.time, ZoneOffset.UTC), yourMove, chat)
            }
            record(true, true)
            val delta = service.page(f.b, "0")
            assertTrue(delta.entries.any { it.kind == ActivityKind.GAME })
            assertTrue(delta.readyNotices!!.none { it.kind == ActivityKind.GAME })
            val sent = mutableListOf<ActivityPushHint>()
            ActivityDelivery(f.runtime, ActivityPushAdapter { _, hint -> sent += hint; PushResult(PushStatus.DELIVERED) }, clock).use { delivery ->
                clock.time = first.plusSeconds(5); service.heartbeat(f.a, actor.copy(didSomething = false))
                delivery.deliverDue(); assertTrue(sent.isEmpty())
                clock.time = first.plusSeconds(9); service.heartbeat(f.a, actor)
                clock.time = first.plusSeconds(10); delivery.deliverDue(); assertTrue(sent.isEmpty())
                clock.time = first.plusSeconds(18); service.heartbeat(f.a, actor.copy(didSomething = false))
                delivery.deliverDue(); assertTrue(sent.isEmpty())
                clock.time = first.plusSeconds(19)
                f.runtime.transaction { connection ->
                    val sql = DSL.using(connection, SQLDialect.POSTGRES)
                    val row = sql.selectFrom(org.asyncttwiz.server.generated.tables.ActivitySummary.ACTIVITY_SUMMARY)
                        .where(org.asyncttwiz.server.generated.tables.ActivitySummary.ACTIVITY_SUMMARY.KIND.eq("GAME")).fetchSingle()
                    assertTrue(ActivityTiming.due(ActivityTiming.leases(sql, UUID.fromString(game.id), UUID.fromString(f.a.account.accountId)),
                        emptyList(), row.lastEventAt!!, row.yourMove!!, OffsetDateTime.ofInstant(clock.time, ZoneOffset.UTC)),
                        "The persisted input must be idle for ten seconds regardless of the DB timezone")
                }
                delivery.deliverDue(); assertEquals(1, sent.size)
                val emptyDelta = service.page(f.b, delta.nextCursor)
                assertTrue(emptyDelta.entries.isEmpty())
                val notice = emptyDelta.readyNotices!!.single()
                assertTrue(notice.notice!!.yourMove); assertTrue(notice.notice!!.newMessage)
                assertEquals("Private table", notice.notice!!.gameName)
                assertEquals("Inviter", notice.notice!!.opponentName)
                assertEquals(first, OffsetDateTime.parse(notice.notice!!.lastEventAt).toInstant())
                val recipient = GamePresence(game.id, id())
                service.heartbeat(f.b, recipient)
                assertTrue(service.page(f.b, delta.nextCursor).readyNotices!!.isEmpty())
                record(false)
                delivery.deliverDue(); assertEquals(1, sent.size)
                service.heartbeat(f.b, recipient.copy(left = true))
                // Presence suppressed events stay seen after leaving; no stale alert.
                assertTrue(service.page(f.b, delta.nextCursor).readyNotices!!.isEmpty())
                record(false)
                service.heartbeat(f.a, actor.copy(left = true))
                delivery.deliverDue(); assertEquals(2, sent.size)
                assertFalse(service.page(f.b, delta.nextCursor).readyNotices!!.single().notice!!.newMessage)
            }
        }
    }

    @Test fun `sixty second idle and expiry are enforced by real queue scheduler`() {
        ActivityFixture().use { f ->
            val game = f.accept(f.create()); val clock = ActivityClock(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)); val first = clock.time
            val service = ActivityService(f.runtime, clock)
            service.register(f.b, RegisterInstallation(id(), id(), "push")); f.sql("DELETE FROM activity_push")
            val actor = GamePresence(game.id, id())
            service.heartbeat(f.a, actor); f.record(game, id(), first)
            val sent = mutableListOf<ActivityPushHint>()
            ActivityDelivery(f.runtime, ActivityPushAdapter { _, hint -> sent += hint; PushResult(PushStatus.DELIVERED) }, clock).use { delivery ->
                for (seconds in 5L..55L step 5) {
                    clock.time = first.plusSeconds(seconds); service.heartbeat(f.a, actor); delivery.deliverDue()
                }
                assertTrue(sent.isEmpty())
                clock.time = first.plusSeconds(59); delivery.deliverDue(); assertTrue(sent.isEmpty())
                clock.time = first.plusSeconds(60); service.heartbeat(f.a, actor); delivery.deliverDue(); assertEquals(1, sent.size)
                f.record(game, id(), clock.time)
                clock.time = first.plusSeconds(74); delivery.deliverDue(); assertEquals(1, sent.size)
                clock.time = first.plusSeconds(75); delivery.deliverDue(); assertEquals(2, sent.size)
            }
        }
    }

    @Test fun `presence suppression is recorded on insert and update even if recipient leaves before scheduler`() {
        ActivityFixture().use { f ->
            val game = f.accept(f.create()); val clock = ActivityClock()
            val service = ActivityService(f.runtime, clock)
            service.register(f.b, RegisterInstallation(id(), id(), "push"))
            f.sql("DELETE FROM activity_push")
            val recipient = GamePresence(game.id, id())
            service.heartbeat(f.b, recipient)
            repeat(2) {
                f.runtime.transaction { connection ->
                    ActivityService.record(DSL.using(connection, SQLDialect.POSTGRES), UUID.fromString(f.b.account.accountId),
                        ActivityKind.GAME, UUID.fromString(game.id), UUID.randomUUID(), UUID.fromString(f.a.account.accountId),
                        OffsetDateTime.ofInstant(clock.time, ZoneOffset.UTC), newMessage = true)
                }
                assertEquals(1L, f.number("SELECT count(*) FROM activity_summary WHERE kind='GAME' AND seen_through=activity_id AND NOT new_message"))
            }
            service.heartbeat(f.b, recipient.copy(left = true))
            assertTrue(service.page(f.b, "0").readyNotices!!.isEmpty())
            val sent = mutableListOf<ActivityPushHint>()
            ActivityDelivery(f.runtime, ActivityPushAdapter { _, hint -> sent += hint; PushResult(PushStatus.DELIVERED) }, clock).use {
                it.deliverDue()
                assertTrue(sent.isEmpty())
                f.record(game, id(), clock.time)
                it.deliverDue()
                assertEquals(1, sent.size)
            }
            assertFalse(service.page(f.b, "0").readyNotices!!.single().notice!!.newMessage)
        }
    }

    @Test fun `many unseen games return only newest fifty due notices while activity cursor progresses`() {
        ActivityFixture().use { f ->
            val clock = ActivityClock(); val service = ActivityService(f.runtime, clock)
            val games = (1..160).map { f.accept(f.create()).also { game -> f.record(game, id(), clock.time) } }
            // A newer, not-yet-due summary must not consume a slot in the bounded notice list.
            service.heartbeat(f.a, GamePresence(games.last().id, id(), true))
            val expected = games.dropLast(1).takeLast(50).asReversed().map { it.id }
            val first = service.page(f.b, "0")
            val second = service.page(f.b, first.nextCursor)
            val empty = service.page(f.b, second.nextCursor)
            assertEquals(100, first.entries.size); assertTrue(first.hasMore)
            assertEquals(60, second.entries.size); assertFalse(second.hasMore)
            assertTrue(second.nextCursor.toLong() > first.nextCursor.toLong())
            assertEquals(games.map { it.id }, (first.entries + second.entries).map { it.gameId })
            assertTrue(empty.entries.isEmpty()); assertEquals(second.nextCursor, empty.nextCursor)
            for (page in listOf(first, second, empty)) {
                assertEquals(expected, page.readyNotices!!.map { it.gameId })
                assertTrue(ActivityWire.json.encodeToString(page).toByteArray(Charsets.UTF_8).size <= 65536,
                    "Polling must fit the Android response limit so its cursor can be saved")
            }
        }
    }
}
