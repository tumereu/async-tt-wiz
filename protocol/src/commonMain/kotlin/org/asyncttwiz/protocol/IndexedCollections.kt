package org.asyncttwiz.protocol

import kotlinx.collections.immutable.toPersistentList

/** Storage-backed immutable collections. Adapters must keep reads in their transaction and must
 * never enumerate a collection to implement a keyed edit. Not a wire authority. */
interface IndexedMap<K, V> : Map<K, V> {
    fun putting(key: K, value: V): Map<K, V>
    fun removing(key: K): Map<K, V>
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
