package org.asyncttwiz.android

import kotlin.test.*
import org.junit.Test
import org.asyncttwiz.protocol.*
import java.time.Instant

class ActivityNotificationsTest {
    // LOB-072, LOB-183, LOB-184
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
    // LOB-072, LOB-183 (I-D57): where the recipient would resume, in the turn square's words, and their own deck in the header
    @Test fun gameTextNamesWhereYouWouldResumeAndYourDeck() {
        val content = org.asyncttwiz.protocol.GameNoticeContent("Friday", "Alex", "2026-10-27T12:34:56+02:00", true,
            resumeAt = org.asyncttwiz.protocol.ResumePoint(5, false, org.asyncttwiz.protocol.TurnPosition.END_STEP), deckName = "Elves")
        val text = gameNotificationText(content)
        assertEquals("Your move · Turn 5, Alex's turn, End step", text.text)
        assertEquals("Elves", text.subText)
        assertEquals("Alex is waiting for you · Turn 6, your turn, Upkeep · new message", gameNotificationText(content.copy(reminder = true,
            newMessage = true, resumeAt = org.asyncttwiz.protocol.ResumePoint(6, true, org.asyncttwiz.protocol.TurnPosition.UPKEEP))).text)
        assertEquals("Alex is ready · Mulliganing", gameNotificationText(content.copy(opponentReady = true,
            resumeAt = org.asyncttwiz.protocol.ResumePoint(0, false))).text)
        // The game is over: nothing to resume
        assertEquals("Game over · You won", gameNotificationText(content.copy(gameOver = org.asyncttwiz.protocol.SuggestedResult.WIN,
            byRules = true)).text)
        assertNull(gameNotificationText(content.copy(deckName = null)).subText)
    }

    // DRF-135 (I-D57): a pod notification names the set or cube the pod drafts
    @Test fun podTextNamesTheSetOrCube() {
        val hint = org.asyncttwiz.protocol.ActivityHint("1", ActivityKind.POD_YOUR_PICK, java.util.UUID.randomUUID().toString(),
            java.util.UUID.randomUUID().toString(), resourceName = "Friday pod", sourceName = "Vintage cube")
        assertEquals("Vintage cube", podNotificationDetail(hint))
        assertNull(podNotificationDetail(hint.copy(sourceName = null)))
        assertNull(podNotificationDetail(hint.copy(kind = ActivityKind.GAME)))
    }

    // LOB-111
    @Test fun gameOverTextNamesTheAcceptedResultFromTheSuggestionAuthorsSide() {
        val content = org.asyncttwiz.protocol.GameNoticeContent("Friday", "Alex", "2026-10-27T12:34:56+02:00", false)
        for ((outcome, text) in listOf(org.asyncttwiz.protocol.SuggestedResult.WIN to "Game over · Alex accepted your win",
                org.asyncttwiz.protocol.SuggestedResult.LOSS to "Game over · Alex accepted your loss",
                org.asyncttwiz.protocol.SuggestedResult.DRAW to "Game over · Alex accepted the draw")) {
            val shown = gameNotificationText(content.copy(gameOver = outcome))
            assertEquals("Friday · vs Alex", shown.title)
            assertEquals(text, shown.text)
        }
        // CR 104.3a (I-D52): the opponent accepted their own loss
        assertEquals("Game over · Alex conceded", gameNotificationText(content.copy(gameOver = org.asyncttwiz.protocol.SuggestedResult.LOSS,
            conceded = true)).text)
    }

    // LOB-114
    @Test fun opponentReadyTextReplacesTheMoveTextAndKeepsTheChatSuffix() {
        val content = org.asyncttwiz.protocol.GameNoticeContent("Friday", "Alex", "2026-10-27T12:34:56+02:00", false, opponentReady = true)
        assertEquals("Alex is ready", gameNotificationText(content).text)
        assertEquals("Alex is ready", gameNotificationText(content.copy(yourMove = true)).text)
        assertEquals("Alex is ready · new message", gameNotificationText(content.copy(newMessage = true)).text)
        assertEquals("Friday · vs Alex", gameNotificationText(content).title)
    }

    // LOB-078, LOB-188
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

    // DRF-135, LOB-072, LOB-076, LOB-111, LOB-114, LOB-116, LOB-134, LOB-277 (owner, 2026-11-26): six channels, one per kind:
    // each notification in its kind's (a game's by what it tells); the app's own reminders in Reminders
    @Test fun sixChannelsOnePerKindAndAllPathsRespectAllOsControls() {
        assertEquals(listOf("Your move", "Reminders", "Chat", "Invitations", "Draft", "Game over & warnings"),
            ActivityChannels.names.values.toList())
        assertTrue(ActivityChannels.names.keys.none { it in ActivityChannels.RETIRED }, "new channels keep none of the old ones' settings")
        fun hint(kind: ActivityKind, notice: GameNoticeContent? = null) = ActivityHint("1", kind, "g", "i", notice = notice)
        fun channel(hint: ActivityHint, reminder: Boolean = false) =
            ActivityChannels.names.getValue(ActivityChannels.channel(hint, reminder))
        val move = GameNoticeContent("Azure Spark", "Alex", "2026-11-26T10:00:00Z", yourMove = true)
        assertEquals("Your move", channel(hint(ActivityKind.GAME, move)))
        assertEquals("Your move", channel(hint(ActivityKind.GAME, move.copy(yourMove = false))))
        assertEquals("Your move", channel(hint(ActivityKind.GAME, move.copy(opponentReady = true))))
        assertEquals("Reminders", channel(hint(ActivityKind.GAME, move.copy(reminder = true))))
        assertEquals("Game over & warnings", channel(hint(ActivityKind.GAME, move.copy(gameOver = SuggestedResult.WIN))))
        assertEquals("Game over & warnings", channel(hint(ActivityKind.GAME, move.copy(branched = true))))
        assertEquals("Game over & warnings", channel(hint(ActivityKind.GAME_REMOVAL)))
        assertEquals("Chat", channel(hint(ActivityKind.CHAT))); assertEquals("Chat", channel(hint(ActivityKind.POD_CHAT)))
        assertEquals("Invitations", channel(hint(ActivityKind.INVITATION)))
        assertEquals("Invitations", channel(hint(ActivityKind.POD_INVITATION)))
        for (kind in listOf(ActivityKind.POD_YOUR_PICK, ActivityKind.POD_DRAFT_COMPLETE, ActivityKind.POD_DECKS_READY,
                ActivityKind.POD_DRAFT_CANCELLED, ActivityKind.POD_AUTO_START_OFF)) assertEquals("Draft", channel(hint(kind)), kind.name)
        for (kind in listOf(ActivityKind.POD_REMOVAL, ActivityKind.POD_DESTROYED, ActivityKind.POD_MEMBER_REMOVED))
            assertEquals("Game over & warnings", channel(hint(kind)), kind.name)
        // LOB-076: the app's own reminder of a pick or a game
        assertEquals("Reminders", channel(hint(ActivityKind.POD_YOUR_PICK), reminder = true))
        assertEquals("Reminders", channel(hint(ActivityKind.GAME, move), reminder = true))
        for (permission in listOf(false, true)) for (application in listOf(false, true)) for (channel in listOf(false, true))
            assertEquals(permission && application && channel, ActivityChannels.allowed(permission, application, channel))
    }
    @Test fun serverRetryAfterSecondsAndHttpDateAreRespected() {
        assertEquals(120L, ActivityApi.retryAfter("120"))
        assertEquals(120L, ActivityApi.retryAfter("Wed, 21 Oct 2015 07:30:00 GMT", Instant.parse("2015-10-21T07:28:00Z")))
        assertEquals(0L, ActivityApi.retryAfter("invalid"))
    }
}
