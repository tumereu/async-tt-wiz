package org.asyncttwiz.core

import kotlin.test.Test
import kotlin.test.assertEquals

class JvmRuntimeTest {
    @Test
    fun testsActuallyRunOnJdk21() {
        assertEquals(21, Runtime.version().feature())
    }
}
