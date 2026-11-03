package org.asyncttwiz.protocol

import kotlinx.collections.immutable.toPersistentList

/** Storage-backed immutable collections. Adapters must keep reads in their transaction and must
 * never enumerate a collection to implement a keyed edit. Not a wire authority. */
interface IndexedMap<K, V> : Map<K, V> {
    fun putting(key: K, value: V): Map<K, V>
    fun removing(key: K): Map<K, V>
    /** Reads [keys] ahead in one go where the storage can (each is then read as [get] would have read it): a
     * 1000-member batch (I-D14) looks up each new event's id once per member otherwise. */
    fun prefetch(keys: Collection<K>) {}
}

interface IndexedSet<T> : Set<T> {
    fun adding(value: T): Set<T>
    fun removing(value: T): Set<T>
}

interface IndexedList<T> : List<T> {
    fun appending(value: T): List<T>
    fun removingLast(): List<T>
}

internal fun <T> List<T>.appendShared(value: T): List<T> =
    if (this is IndexedList<T>) appending(value) else toPersistentList().add(value)

internal fun <T> List<T>.dropLastShared(): List<T> =
    if (this is IndexedList<T>) removingLast() else toPersistentList().removeAt(lastIndex)
