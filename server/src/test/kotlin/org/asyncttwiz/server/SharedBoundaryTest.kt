package org.asyncttwiz.server

import org.asyncttwiz.core.EntityId
import org.asyncttwiz.protocol.ScalarCodecs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SharedBoundaryTest {
    @Test
    fun serverConsumesTheSharedJvmArtifactsOnJdk21() {
        assertEquals(21, Runtime.version().feature())
        val id = EntityId("01234567-89ab-4cde-8f01-23456789abcd")
        assertEquals(id, ScalarCodecs.decodeId(ScalarCodecs.encodeId(id)))
    }
}
