package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlin.test.*

class ActivityRuntimeTest {
    @Test fun navigationResultContainsOnlyOpaqueAuthorizedTargetAndNoCommandOrBoard() {
        val result = ActivityTarget("game", ActivityDestination.GROUP, playId = "play", groupId = "group", boundaryNodeId = "parent")
        val encoded = ActivityWire.json.encodeToString(result)
        assertEquals(result, ActivityWire.json.decodeFromString<ActivityTarget>(encoded))
        assertFalse("requestId" in encoded); assertFalse("board" in encoded); assertFalse("card" in encoded)
        assertFails { ActivityWire.json.decodeFromString<ActivityTarget>(encoded.dropLast(1) + ",\"cardName\":\"hidden\"}") }
    }
}
