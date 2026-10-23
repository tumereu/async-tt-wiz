package org.asyncttwiz.android

import kotlin.test.*
import org.junit.Test
import org.asyncttwiz.protocol.ActivityKind
import java.time.Instant

class ActivityNotificationsTest {
    @Test fun exactlyTwoDefaultChannelsAndBothPathsRespectAllOsControls() {
        assertEquals(setOf("Invitations", "Game activity"), ActivityChannels.names.values.toSet())
        assertEquals(2, ActivityChannels.names.size)
        assertEquals("invitations", ActivityChannels.channel(ActivityKind.INVITATION))
        assertEquals("game_activity", ActivityChannels.channel(ActivityKind.GAME))
        for (permission in listOf(false, true)) for (application in listOf(false, true)) for (channel in listOf(false, true))
            assertEquals(permission && application && channel, ActivityChannels.allowed(permission, application, channel))
    }
    @Test fun serverRetryAfterSecondsAndHttpDateAreRespected() {
        assertEquals(120L, ActivityApi.retryAfter("120"))
        assertEquals(120L, ActivityApi.retryAfter("Wed, 21 Oct 2015 07:30:00 GMT", Instant.parse("2015-10-21T07:28:00Z")))
        assertEquals(0L, ActivityApi.retryAfter("invalid"))
    }
}
