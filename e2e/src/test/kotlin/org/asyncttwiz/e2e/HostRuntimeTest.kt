package org.asyncttwiz.e2e

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Host toolchain qualification only; not a device/backend acceptance scenario. */
class HostRuntimeTest {
    @Test
    fun hostRunnerUsesJdk21() {
        assertEquals(21, Runtime.version().feature())
    }
}
