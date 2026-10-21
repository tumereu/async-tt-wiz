package org.asyncttwiz.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EntityIdTest {
    @Test
    fun preservesCanonicalIdentity() {
        val text = "01234567-89ab-4cde-8f01-23456789abcd"
        assertEquals(text, EntityId(text).value)
        assertEquals(EntityId(text), EntityId(text))
    }

    @Test
    fun refusesNoncanonicalIdentityRatherThanNormalizingIt() {
        listOf("", "0123456789ab4cde8f0123456789abcd", "01234567-89AB-4cde-8f01-23456789abcd",
            " 01234567-89ab-4cde-8f01-23456789abcd").forEach {
            assertFailsWith<IllegalArgumentException> { EntityId(it) }
        }
    }
}
