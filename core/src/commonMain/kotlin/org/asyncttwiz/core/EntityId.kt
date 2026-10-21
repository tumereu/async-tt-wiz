package org.asyncttwiz.core

/** Canonical identity only; acquisition of random IDs belongs to platform adapters. */
@JvmInline
value class EntityId(val value: String) {
    init {
        require(CANONICAL_UUID.matches(value)) { "Expected a lowercase hyphenated UUID" }
    }

    companion object {
        private val CANONICAL_UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
