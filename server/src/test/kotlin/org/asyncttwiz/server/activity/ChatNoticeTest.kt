package org.asyncttwiz.server.activity

import java.time.Instant
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.accounts.AccountSettingsService
import org.asyncttwiz.server.game.GameService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** LOB-277 (owner, 2026-11-26): a game's chat message notifies the other player in a notice of its own, one per chat; the
 * Chat switch (LOB-276) decides whether any device is told or pushed it. */
class ChatNoticeTest {
    private fun id() = ActivityFixture.id()
    private val browserKey = "B" + "A".repeat(86)
    private fun subscription() = WebPushSubscription("https://fcm.googleapis.com/fcm/send/${id()}", browserKey, "A".repeat(22))

    /** [by]'s chat message [text] in [game] (its lobby, both players in it, LOB-271); its id. */
    private fun ActivityFixture.say(game: GameSummary, text: String, by: AccountService.Principal = a): String {
        val games = GameService(runtime)
        val hello = GameHello(id(), id()); val connection = UUID.randomUUID()
        val stream = games.open(by, hello, connection, game.id, id())
        val command = GameCommand(id(), GameScope(game.id, playId = games.head(by, game.id).summary.playId), GameAction.SendChat(text),
            stream.streamId, stream.generation)
        games.admit(by, hello, connection, command)
        check(games.executeOne())
        check(games.results(by, game.id, listOf(command.requestId)).statuses.single().result!!.outcome == "accepted")
        return command.requestId
    }

    /** What [who]'s devices are told to show now (a delta page from [cursor]), and its next cursor. */
    private fun ActivityService.told(who: AccountService.Principal, cursor: String): Pair<List<ActivityHint>, String> =
        page(who, cursor).let { it.readyNotices!! to it.nextCursor }

    // LOB-277, LOB-184: Alice's message reaches Bob's devices as the chat's notice: "<game> · vs <author>" over the message (a
    // browser ready-made, quiet when it replaces one still showing); the game's own notice isn't sent again for it, though
    // " · new message" follows; a second message updates the one notice ("2 new messages · …"); reading the chat through
    // clears it on every device
    @Test fun `a chat message notifies the other player in the chat's one notice until read`() {
        ActivityFixture().use { f ->
            val clock = ActivityClock(); val service = ActivityService(f.runtime, clock)
            service.register(f.b, RegisterInstallation(id(), id(), webPush = subscription()))
            val game = f.accept(f.create())
            var cursor = service.page(f.b, null).nextCursor
            val sent = mutableListOf<WebPushNotice>()
            ActivityDelivery(f.runtime, null, clock, { _, notice -> sent += notice; PushResult(PushStatus.DELIVERED) }).use { delivery ->
                clock.time = Instant.now().plusSeconds(1); delivery.deliverDue(); sent.clear()
                val first = f.say(game, "Good luck!")
                val told = service.told(f.b, cursor).also { cursor = it.second }.first.single()
                assertEquals(ActivityKind.CHAT, told.kind); assertEquals(game.id, told.gameId); assertEquals(first, told.identity)
                assertEquals("Private table", told.resourceName); assertEquals("Inviter", told.opponentName)
                assertEquals(ChatNotice("Good luck!", 1), told.chat)
                assertEquals("Private table · vs Inviter" to "Good luck!", ActivityTexts.notification(told))
                assertTrue("chat:${game.id}" in service.page(f.b, cursor).openNotices!!)
                assertEquals(0L, f.number("SELECT count(*) FROM account_activity WHERE kind = 'GAME' AND account_id = '${f.b.account.accountId}'"),
                    "no game notice for it")
                assertEquals(1L, f.number("SELECT count(*) FROM activity_summary WHERE kind = 'GAME' AND new_message AND " +
                    "account_id = '${f.b.account.accountId}'"), "the game's later notices add · new message")
                clock.time = Instant.now().plusSeconds(2); delivery.deliverDue()
                val notice = sent.single()
                assertEquals(WebPushNotice("Private table · vs Inviter", "Good luck!", "chat:${game.id}", "/games/${game.id}", quiet = true), notice)
                sent.clear()
                f.say(game, "Ready when you are")
                assertEquals(ChatNotice("Ready when you are", 2), service.told(f.b, cursor).also { cursor = it.second }.first.single().chat)
                clock.time = Instant.now().plusSeconds(3); delivery.deliverDue()
                assertEquals("2 new messages · Ready when you are", sent.single().body)
                sent.clear()
                // Read through on one of Bob's devices: the notice clears on the others; reading only the first doesn't
                assertEquals(1, GameService(f.runtime).chatRead(f.b, game.id, ReadChat(first)).unread)
                assertTrue("chat:${game.id}" in service.page(f.b, cursor).openNotices!!)
                val newest = f.db.connection().use { c -> c.createStatement().executeQuery(
                    "SELECT id FROM game_chat WHERE game_id = '${game.id}' ORDER BY revision DESC LIMIT 1").use { it.next(); it.getString(1) } }
                assertEquals(0, GameService(f.runtime).chatRead(f.b, game.id, ReadChat(newest)).unread)
                assertFalse("chat:${game.id}" in service.page(f.b, cursor).openNotices!!)
                clock.time = Instant.now().plusSeconds(10); delivery.deliverDue()
                assertEquals(listOf(WebPushNotice("", tag = "chat:${game.id}", path = "/", clear = true)), sent)
            }
        }
    }

    // LOB-277: no chat notice while the game is open on any of the player's devices: one arriving then is never told nor
    // pushed, also after they leave (they saw the game with it)
    @Test fun `no chat notice comes while the game is open`() {
        ActivityFixture().use { f ->
            val clock = ActivityClock(); val service = ActivityService(f.runtime, clock)
            service.register(f.b, RegisterInstallation(id(), id(), webPush = subscription()))
            val game = f.accept(f.create())
            var cursor = service.page(f.b, null).nextCursor
            val sent = mutableListOf<WebPushNotice>()
            ActivityDelivery(f.runtime, null, clock, { _, notice -> sent += notice; PushResult(PushStatus.DELIVERED) }).use { delivery ->
                clock.time = Instant.now().plusSeconds(1); delivery.deliverDue(); sent.clear()
                val presence = GamePresence(game.id, id())
                clock.time = Instant.now(); service.heartbeat(f.b, presence)
                f.say(game, "Are you there?")
                assertTrue(service.told(f.b, cursor).also { cursor = it.second }.first.isEmpty(), "Bob has the game open")
                delivery.deliverDue()
                assertTrue(sent.isEmpty())
                // The open game's next heartbeat, then Bob leaves
                service.heartbeat(f.b, presence)
                service.heartbeat(f.b, presence.copy(left = true))
                assertTrue(service.told(f.b, cursor).also { cursor = it.second }.first.isEmpty(), "seen with the game")
                assertFalse("chat:${game.id}" in service.page(f.b, cursor).openNotices!!)
                delivery.deliverDue(); assertTrue(sent.isEmpty())
                // Away, the next one notifies
                f.say(game, "Later then")
                assertEquals("Later then", service.told(f.b, cursor).first.single().chat!!.text)
            }
        }
    }

    // LOB-276, LOB-277: with Chat off, a message is told to none of the account's devices and pushed to none (browser or
    // Android installation); the in-app unread marks still tell it
    @Test fun `with Chat off a message notifies no device`() {
        ActivityFixture().use { f ->
            val clock = ActivityClock(); val service = ActivityService(f.runtime, clock)
            AccountSettingsService(f.runtime).set(f.b.account.accountId, AccountSettings(notifications = NotificationSettings(chat = false)))
            service.register(f.b, RegisterInstallation(id(), id(), webPush = subscription()))
            service.register(f.b, RegisterInstallation(id(), id(), "synthetic-android-token"))
            val game = f.accept(f.create())
            var cursor = service.page(f.b, null).nextCursor
            val sent = mutableListOf<WebPushNotice>(); val hints = mutableListOf<ActivityPushHint>()
            ActivityDelivery(f.runtime, { _, hint -> hints += hint; PushResult(PushStatus.DELIVERED) }, clock,
                { _, notice -> sent += notice; PushResult(PushStatus.DELIVERED) }).use { delivery ->
                clock.time = Instant.now().plusSeconds(1); delivery.deliverDue(); sent.clear(); hints.clear()
                f.say(game, "Quiet one")
                assertTrue(service.told(f.b, cursor).also { cursor = it.second }.first.isEmpty())
                clock.time = Instant.now().plusSeconds(2); delivery.deliverDue()
                assertTrue(sent.isEmpty()); assertTrue(hints.none { it.gameId == game.id }, "$hints")
                assertEquals(0L, f.number("SELECT count(*) FROM activity_push WHERE kind = 'CHAT'"))
                assertEquals(1, GameService(f.runtime).chatRead(f.b, game.id).unread)
            }
        }
    }

    // LOB-076, LOB-276: with Reminders off, the app's own reminders list nothing waiting
    @Test fun `with Reminders off the app's own reminders list nothing`() {
        ActivityFixture().use { f ->
            val game = f.accept(f.create())
            f.sql("UPDATE game SET lifecycle = 'ACTIVE' WHERE id = '${game.id}'")
            val clock = ActivityClock(); val service = ActivityService(f.runtime, clock)
            f.runtime.transaction { connection ->
                ActivityService.record(org.jooq.impl.DSL.using(connection, org.jooq.SQLDialect.POSTGRES), UUID.fromString(f.b.account.accountId),
                    ActivityKind.GAME, UUID.fromString(game.id), UUID.randomUUID(), UUID.fromString(f.a.account.accountId),
                    java.time.OffsetDateTime.ofInstant(clock.time, java.time.ZoneOffset.UTC), yourMove = true, move = true)
            }
            clock.time = clock.time.plusSeconds(61)
            assertEquals(1, service.waiting(f.b).games.size)
            AccountSettingsService(f.runtime).set(f.b.account.accountId, AccountSettings(notifications = NotificationSettings(reminders = false)))
            assertTrue(service.waiting(f.b).games.isEmpty())
        }
    }
}
