package org.asyncttwiz.android




/** Compose effect keys must never invoke a retained tree's data-class equality. */
class IdentityKey(private val value: Any?) {
    override fun equals(other: Any?) = other is IdentityKey && value === other.value
    override fun hashCode() = System.identityHashCode(value)
}
