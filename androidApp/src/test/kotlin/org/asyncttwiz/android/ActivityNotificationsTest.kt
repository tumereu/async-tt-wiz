package org.asyncttwiz.android

import kotlin.test.*
import org.junit.Test
import org.asyncttwiz.protocol.ActivityKind
import java.time.Instant

class ActivityNotificationsTest {
    @Test fun gameTextUsesAuthenticatedNamesMoveFlagChatAndOpponentEventTimestamp() {
        val content = org.asyncttwiz.protocol.GameNoticeContent("Friday", "Alex", "2026-10-27T12:34:56+02:00", true)
        val text = gameNotificationText(content)
        assertEquals("Friday · vs Alex", text.title)
        assertEquals("Your move", text.text)
        assertEquals(Instant.parse("2026-10-27T10:34:56Z").toEpochMilli(), text.whenMillis)
        assertEquals("Alex's move · new message", gameNotificationText(content.copy(yourMove = false, newMessage = true)).text)
        assertEquals("Alex is waiting for you", gameNotificationText(content.copy(reminder = true)).text)
        assertEquals("Friday · vs Alex", gameNotificationText(content.copy(reminder = true)).title)
    }
    @Test fun podsOnlyUsersCanEnableAlertsWithExistingPromptGuards() {
        for (destination in listOf(ShellDestination.PODS, ShellDestination.GAMES)) {
            fun prompt(sdk: Int = 33, signedIn: Boolean = true, asked: Boolean = false,
                catalogBlocked: Boolean = false, granted: Boolean = false, selected: String? = null) =
                ActivityPermissionPrompt.shouldPrompt(sdk, signedIn, destination, selected, asked, catalogBlocked, granted)
            assertTrue(prompt())
            assertFalse(prompt(sdk = 32))
            assertFalse(prompt(signedIn = false))
            assertFalse(prompt(asked = true))
            assertFalse(prompt(catalogBlocked = true))
            assertFalse(prompt(granted = true))
            assertEquals(destination == ShellDestination.PODS, prompt(selected = "previous-game"))
        }
        ShellDestination.entries.filter { it !in listOf(ShellDestination.PODS, ShellDestination.GAMES) }.forEach {
            assertFalse(ActivityPermissionPrompt.shouldPrompt(35, true, it, null, false, false, false))
        }
    }

    @Test fun threeDefaultChannelsAndAllPathsRespectAllOsControls() {
        assertEquals(setOf("Invitations", "Game activity", "Draft activity"), ActivityChannels.names.values.toSet())
        assertEquals(3, ActivityChannels.names.size)
        assertEquals("invitations", ActivityChannels.channel(ActivityKind.INVITATION))
        assertEquals("game_activity", ActivityChannels.channel(ActivityKind.GAME))
        assertEquals("invitations", ActivityChannels.channel(ActivityKind.POD_INVITATION))
        ActivityKind.entries.filter { it.isPodActivity() && it != ActivityKind.POD_INVITATION }.forEach {
            assertEquals("draft_activity", ActivityChannels.channel(it))
        }
        for (permission in listOf(false, true)) for (application in listOf(false, true)) for (channel in listOf(false, true))
            assertEquals(permission && application && channel, ActivityChannels.allowed(permission, application, channel))
    }
    @Test fun serverRetryAfterSecondsAndHttpDateAreRespected() {
        assertEquals(120L, ActivityApi.retryAfter("120"))
        assertEquals(120L, ActivityApi.retryAfter("Wed, 21 Oct 2015 07:30:00 GMT", Instant.parse("2015-10-21T07:28:00Z")))
        assertEquals(0L, ActivityApi.retryAfter("invalid"))
    }
}
