package org.asyncttwiz.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationSettingsTest {
    // LOB-276 (owner, 2026-11-26): every kind on by default (and for an account that never chose: nothing on the wire); a
    // kind switched off lets no notice of it through, the others still
    @Test fun everyKindIsOnUntilSwitchedOff() {
        assertTrue(NotificationKind.all.all { NotificationSettings.ALL_ON.allows(it) })
        assertEquals("{}", AccountJson.format.encodeToString(NotificationSettings.serializer(), NotificationSettings.ALL_ON))
        assertEquals(AccountSettings(), AccountJson.format.decodeFromString(AccountSettings.serializer(), "{}"))
        for (kind in NotificationKind.all) {
            val off = NotificationSettings.ALL_ON.with(kind, false)
            assertFalse(off.allows(kind), kind.id)
            assertEquals(NotificationKind.all - kind, NotificationKind.all.filter { off.allows(it) }, kind.id)
            assertEquals(listOf(kind), off.off)
            assertEquals(NotificationSettings.ALL_ON, off.with(kind, true))
        }
        val chat = ActivityHint("1", ActivityKind.CHAT, "g", "m")
        assertFalse(NotificationSettings(chat = false).allows(chat))
        assertTrue(NotificationSettings(yourMove = false).allows(chat))
        assertEquals(NotificationKind.all, NotificationKind.all.map { NotificationKind.of(it.id) })
    }

    // LOB-277: a chat's notice: a game's "<game> · vs <author>", a pod's "<pod>: <author>", over the newest message, after
    // the count of unread ones when more than one
    @Test fun chatNoticeWords() {
        val game = ActivityHint("1", ActivityKind.CHAT, "g", "m", resourceName = "Azure Spark", opponentName = "Alex",
            chat = ChatNotice("Good luck!"))
        assertEquals("Azure Spark · vs Alex" to "Good luck!", ActivityTexts.notification(game))
        assertEquals("Azure Spark · vs Alex" to "3 new messages · Good luck!", ActivityTexts.notification(game.copy(chat = ChatNotice("Good luck!", 3))))
        val pod = ActivityHint("2", ActivityKind.POD_CHAT, "p", "m", resourceName = "Cube night", opponentName = "Riley",
            chat = ChatNotice("Draft tonight?"))
        assertEquals("Cube night: Riley" to "Draft tonight?", ActivityTexts.notification(pod))
        assertEquals("Friday pod: Riley" to "Draft tonight?", ActivityTexts.notification(pod.copy(resourceName = null), podName = "Friday pod"))
        assertEquals(NotificationKind.Chat, NotificationKind.of(pod))
    }
}
