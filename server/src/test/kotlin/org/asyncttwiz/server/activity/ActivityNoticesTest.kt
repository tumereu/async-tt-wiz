package org.asyncttwiz.server.activity

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * LOB-242, LOB-243 (owner, 2026-11-19): a notification clears on every device of its player once nothing waits for them,
 * however they dealt with it. The server decides what still waits (the feed's openNotices), remembers what a device may show
 * and sends a clear to the account's devices: the FCM hint to an Android installation, a Web Push clear to a browser that
 * was sent the notice.
 */
class ActivityNoticesTest {
    private fun id() = ActivityFixture.id()
    private val browserKey = "B" + "A".repeat(86)
    private fun subscription() = WebPushSubscription("https://fcm.googleapis.com/fcm/send/${id()}", browserKey, "A".repeat(22))
    private fun ActivityFixture.open(owner: org.asyncttwiz.server.accounts.AccountService.Principal = b): Set<String> =
        runtime.transaction { ActivityNotices.open(DSL.using(it, SQLDialect.POSTGRES), UUID.fromString(owner.account.accountId)) }

    // LOB-242: a pending invitation waits until answered; a game's event waits until seen on any device and, while it is the
    // player's move, after it too; the feed lists what waits
    @Test fun `what still waits is the server's to say`() {
        ActivityFixture().use { f ->
            val clock = ActivityClock(Instant.now().truncatedTo(ChronoUnit.MICROS))
            val service = ActivityService(f.runtime, clock)
            val invited = f.create()
            assertEquals(setOf("invite:${invited.invitationId}"), f.open())
            assertEquals(listOf("invite:${invited.invitationId}"), service.page(f.b, null).openNotices)
            val game = f.accept(invited)
            assertEquals(emptySet<String>(), f.open(), "answered: nothing waits")
            f.sql("UPDATE game SET lifecycle = 'ACTIVE' WHERE id = '${game.id}'")
            fun event(yourMove: Boolean) = f.runtime.transaction { connection ->
                ActivityService.record(DSL.using(connection, SQLDialect.POSTGRES), UUID.fromString(f.b.account.accountId),
                    ActivityKind.GAME, UUID.fromString(game.id), UUID.randomUUID(), UUID.fromString(f.a.account.accountId),
                    OffsetDateTime.ofInstant(clock.time, ZoneOffset.UTC), yourMove = yourMove, move = true)
            }
            event(yourMove = false)
            assertEquals(setOf("game:${game.id}"), f.open(), "an event not seen yet")
            val presence = GamePresence(game.id, id())
            service.heartbeat(f.b, presence) // seen on some device of Bob's
            assertEquals(emptySet<String>(), f.open(), "seen, and not Bob's move")
            event(yourMove = true)
            service.heartbeat(f.b, presence)
            assertEquals(setOf("game:${game.id}"), f.open(), "seen, but still Bob's move")
            assertEquals(emptySet<String>(), f.open(f.a), "never the other player's")
        }
    }

    // LOB-242, LOB-243, WEB-099: a notice delivered to Bob's phone and browser; once he answers it elsewhere the server sends the
    // phone a clear hint (its feed then lists the key no more) and the browser a Web Push clear naming its tag, once each
    @Test fun `a notice dealt with elsewhere is cleared on the phone and in the browser`() {
        ActivityFixture().use { f ->
            val clock = ActivityClock(); val service = ActivityService(f.runtime, clock)
            val phone = service.register(f.b, RegisterInstallation(id(), id(), "synthetic-android-token"))
            service.register(f.b, RegisterInstallation(id(), id(), webPush = subscription()))
            val hints = mutableListOf<ActivityPushHint>()
            val sent = mutableListOf<WebPushNotice>()
            val game = f.create()
            val key = "invite:${game.invitationId}"
            ActivityDelivery(f.runtime, { _, hint -> hints += hint; PushResult(PushStatus.DELIVERED) }, clock,
                { _, notice -> sent += notice; PushResult(PushStatus.DELIVERED) }).use { delivery ->
                clock.time = Instant.now().plusSeconds(1); delivery.deliverDue()
                assertEquals(listOf(key), sent.map { it.tag }); assertFalse(sent.single().clear)
                assertEquals(1, hints.size)
                assertEquals(1L, f.number("SELECT count(*) FROM activity_notice WHERE notice_key = '$key'"))
                hints.clear(); sent.clear()
                clock.time = clock.time.plusSeconds(10); delivery.deliverDue()
                assertTrue(hints.isEmpty() && sent.isEmpty(), "nothing to clear while it waits")
                f.accept(game) // answered on another device
                clock.time = clock.time.plusSeconds(10); delivery.deliverDue()
                assertEquals(listOf(WebPushNotice("", tag = key, path = "/", clear = true)), sent)
                val hint = hints.single()
                assertEquals(phone.generation, hint.generation); assertEquals("0", hint.activityId)
                assertEquals(game.invitationId, hint.gameId)
                assertFalse(service.page(f.b, null).openNotices!!.contains(key), "the phone's refresh: it no longer waits")
                hints.clear(); sent.clear()
                clock.time = clock.time.plusSeconds(10); delivery.deliverDue()
                assertTrue(hints.isEmpty() && sent.isEmpty(), "cleared once")
                assertEquals(0L, f.number("SELECT count(*) FROM activity_notice"))
                assertEquals(0L, f.number("SELECT count(*) FROM activity_notice_browser"))
                assertEquals(0L, f.number("SELECT count(*) FROM activity_push"))
            }
        }
    }

    // LOB-243: without push, what a feed page or the waiting check let the phone show is remembered, and a browser that never
    // got the notice is sent no clear
    @Test fun `what the feed and the waiting check tell is remembered, and only browsers that showed it are cleared`() {
        ActivityFixture().use { f ->
            val clock = ActivityClock(); val service = ActivityService(f.runtime, clock)
            val first = service.page(f.b, null)
            service.register(f.b, RegisterInstallation(id(), id(), webPush = subscription()))
            val game = f.create()
            val page = service.page(f.b, first.nextCursor)
            assertEquals(listOf(ActivityKind.INVITATION), page.readyNotices!!.map { it.kind })
            assertEquals(1L, f.number("SELECT count(*) FROM activity_notice WHERE notice_key = 'invite:${game.invitationId}'"))
            f.sql("DELETE FROM activity_push") // the browser was never sent it
            val sent = mutableListOf<WebPushNotice>()
            ActivityDelivery(f.runtime, null, clock, { _, notice -> sent += notice; PushResult(PushStatus.DELIVERED) }).use { delivery ->
                f.accept(game)
                clock.time = clock.time.plusSeconds(10); delivery.deliverDue()
                assertTrue(sent.isEmpty(), "no clear for a browser that never showed it")
                assertEquals(0L, f.number("SELECT count(*) FROM activity_notice"), "forgotten once settled")
            }
        }
    }
}
