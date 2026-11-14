package org.asyncttwiz.server.activity

import java.time.Instant
import org.asyncttwiz.protocol.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** WEB-050, WEB-051, WEB-052: a browser's push subscription kept with its installation, and the same notifications sent to it as
 * Android's pushes announce, in the feed's words, beside Firebase. */
class WebPushDeliveryTest {
    private fun id() = ActivityFixture.id()
    private val browserKey = "B" + "A".repeat(86)
    private fun subscription(games: Boolean = true, invitations: Boolean = true) =
        WebPushSubscription("https://fcm.googleapis.com/fcm/send/${id()}", browserKey, "A".repeat(22), invitations, games, true)

    // WEB-052, LOB-072, LOB-077: an invitation and a game's move reach the browser as notifications with their words, its
    // tag and the address a click opens; Android installations still get their opaque hint beside it
    @Test fun `a browser gets the same notifications as Android, ready-made, beside Firebase`() {
        ActivityFixture().use { f ->
            val clock = ActivityClock(); val service = ActivityService(f.runtime, clock)
            val browser = RegisterInstallation(id(), id(), webPush = subscription())
            service.register(f.b, browser)
            service.register(f.b, RegisterInstallation(id(), id(), "synthetic-android-token"))
            val sent = mutableListOf<Pair<WebPushSubscription, WebPushNotice>>()
            val hints = mutableListOf<ActivityPushHint>()
            val game = f.create()
            ActivityDelivery(f.runtime, { _, hint -> hints += hint; PushResult(PushStatus.DELIVERED) }, clock,
                { target, notice -> sent += target to notice; PushResult(PushStatus.DELIVERED) }).use { delivery ->
                clock.time = Instant.now().plusSeconds(1); delivery.deliverDue()
                val invitation = sent.single()
                assertEquals(browser.webPush, invitation.first)
                assertEquals(WebPushNotice("Inviter invited you · Private table", null, "invite:${game.invitationId}", "/games/${game.id}"),
                    invitation.second)
                assertEquals(1, hints.size, "the Android installation's hint, as before")
                sent.clear()
                val accepted = f.accept(game)
                clock.time = Instant.now().plusSeconds(1); delivery.deliverDue(); sent.clear()
                f.record(accepted, id(), clock.time)
                clock.time = clock.time.plusSeconds(61); delivery.deliverDue()
                val move = sent.single().second
                assertEquals("Private table · vs Inviter", move.title)
                assertEquals("Inviter's move", move.body)
                assertEquals("game:${game.id}", move.tag); assertEquals("/games/${game.id}", move.path)
                assertNotNull(move.time)
                assertEquals(0L, f.number("SELECT count(*) FROM activity_push WHERE kind = 'GAME'"))
            }
        }
    }

    // WEB-051: a kind switched off in the browser is never sent to it (its pending push is dropped), the others still are
    @Test fun `a kind the browser switched off isn't sent, and a gone subscription is forgotten`() {
        ActivityFixture().use { f ->
            val clock = ActivityClock(); val service = ActivityService(f.runtime, clock)
            val browser = RegisterInstallation(id(), id(), webPush = subscription(games = false))
            service.register(f.b, browser)
            val sent = mutableListOf<WebPushNotice>()
            var answer = PushResult(PushStatus.DELIVERED)
            ActivityDelivery(f.runtime, null, clock, { _, notice -> sent += notice; answer }).use { delivery ->
                val invited = f.create()
                clock.time = Instant.now().plusSeconds(1); delivery.deliverDue()
                assertEquals(listOf("invite"), sent.map { it.tag.substringBefore(':') }, "Invitations are on")
                sent.clear()
                val game = f.accept(invited)
                clock.time = Instant.now().plusSeconds(1); delivery.deliverDue()
                f.record(game, id(), clock.time); clock.time = clock.time.plusSeconds(61); delivery.deliverDue()
                assertTrue(sent.isEmpty(), "Game activity is off in this browser")
                assertEquals(0L, f.number("SELECT count(*) FROM activity_push"))
                // Game activity on again: the next move is sent; the push service says the subscription is gone
                service.register(f.b, browser.copy(webPush = browser.webPush!!.copy(games = true)))
                answer = PushResult(PushStatus.INVALID_REGISTRATION)
                f.record(game, id(), clock.time); clock.time = clock.time.plusSeconds(61); delivery.deliverDue()
                assertEquals(1, sent.size)
                assertEquals(0L, f.number("SELECT count(*) FROM activity_installation WHERE web_push IS NOT NULL"))
                f.record(game, id(), clock.time); clock.time = clock.time.plusSeconds(61); delivery.deliverDue()
                assertEquals(1, sent.size, "nothing more until the browser subscribes again")
            }
        }
    }

    // WEB-050: only a push service's https address is kept, and a browser's subscription comes without an Android token
    @Test fun `registering a browser takes a push service's subscription only`() {
        ActivityFixture().use { f ->
            val service = ActivityService(f.runtime)
            for (endpoint in listOf("https://example.com/push", "https://127.0.0.1/push", "http://fcm.googleapis.com/fcm/send/x",
                    "https://fcm.googleapis.com/fcm/send/{x}"))
                assertThrows(ActivityFailure::class.java, { service.register(f.b, RegisterInstallation(id(), id(),
                    webPush = subscription().copy(endpoint = endpoint))) }, endpoint)
            assertThrows(ActivityFailure::class.java) { service.register(f.b, RegisterInstallation(id(), id(), "token", subscription())) }
            assertThrows(ActivityFailure::class.java) {
                service.register(f.b, RegisterInstallation(id(), id(), webPush = subscription().copy(p256dh = "short")))
            }
            val browser = RegisterInstallation(id(), id(), webPush = subscription())
            val first = service.register(f.b, browser)
            assertEquals(first, service.register(f.b, browser.copy(webPush = browser.webPush!!.copy(drafts = false))), "the same browser")
            assertEquals(1L, f.number("SELECT count(*) FROM activity_installation WHERE web_push LIKE '%\"drafts\":false%'"))
            service.unregister(f.b, browser.installationId)
            assertEquals(0L, f.number("SELECT count(*) FROM activity_installation"))
        }
    }
}
