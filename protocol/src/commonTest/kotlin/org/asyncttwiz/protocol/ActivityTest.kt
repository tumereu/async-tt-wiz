package org.asyncttwiz.protocol

import kotlin.test.*
import kotlinx.serialization.encodeToString

class ActivityTest {
    private val id = "00000000-0000-4000-8000-000000000001"
    @Test fun hintsAreOpaqueAndStrict() {
        val push = ActivityPushHint(id, id, "13", id)
        assertTrue(ActivityWire.valid(push))
        assertEquals(push, ActivityWire.json.decodeFromString<ActivityPushHint>(ActivityWire.json.encodeToString(push)))
        assertFalse(ActivityWire.valid(push.copy(activityId = "-1")))
        assertFalse(ActivityWire.valid(push.copy(generation = "session-token")))
        assertFails { ActivityWire.json.decodeFromString<ActivityPushHint>("""{"generation":"$id","gameId":"$id","activityId":"1","identity":"$id","notification":{"title":"covered name"}}""") }
        assertTrue(ActivityWire.valid(RegisterInstallation(id, id)))
        assertFalse(ActivityWire.valid(RegisterInstallation(id, id, "token\nsecret")))
        assertNull(ActivityWire.ordinal("01")); assertNull(ActivityWire.ordinal("9223372036854775808"))
    }
    @Test fun resetAndPagingAreExplicitNotReadProgress() {
        val page = ActivityPage(listOf(ActivityHint("1", ActivityKind.INVITATION, id, id)), "r:101:1", "0", true, true)
        assertEquals(page, ActivityWire.json.decodeFromString<ActivityPage>(ActivityWire.json.encodeToString(page)))
    }
    @Test fun presenceRidesExistingPingAndOldPingStillDecodes() {
        val ping = GamePing(id, GamePresence(id, id, true))
        assertEquals(ping, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(ping)))
        assertEquals(GamePing(id), GameWire.decodeClient("""{"type":"ping","correlationId":"$id"}"""))
    }
}
