package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*

/** Immutable request-local overlays over indexed SQL reads. Enumeration is reserved for explicit
 * exports/maintenance. A keyed edit never copies the underlying table or a high-fanout edge set. */
internal class EndpointMap<K, V : Any>(private val lookup: (K) -> V?, private val all: () -> Set<K>,
    private val edits: Map<K, V?> = emptyMap(), private val reads: MutableMap<K, V?> = mutableMapOf()) : AbstractMap<K, V>(), IndexedMap<K, V> {
    val changedKeys: Set<K> get() = edits.keys
    override fun get(key: K): V? = if (key == null) null else if (edits.containsKey(key)) edits[key] else
        if (reads.containsKey(key)) reads[key] else lookup(key).also { reads[key] = it }
    override fun containsKey(key: K) = get(key) != null
    override val entries: Set<Map.Entry<K, V>> get() = (all() + edits.keys).mapNotNull { key ->
        get(key)?.let { java.util.AbstractMap.SimpleImmutableEntry(key, it) } }.toSet()
    override fun putting(key: K, value: V): Map<K, V> = EndpointMap(lookup, all, edits + (key to value), reads)
    override fun removing(key: K): Map<K, V> = EndpointMap(lookup, all, edits + (key to null), reads)
}

/** Fixed-size decoded record cache; never captures a connection, lazy loader, or whole history. */
internal class EndpointReadCache {
    private val values = object : LinkedHashMap<String, kotlinx.serialization.json.JsonElement?>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, kotlinx.serialization.json.JsonElement?>?) = size > 512
    }
    fun read(key: String, load: () -> kotlinx.serialization.json.JsonElement?): kotlinx.serialization.json.JsonElement? =
        if (values.containsKey(key)) values[key] else load().also { values[key] = it }
    fun write(key: String, value: kotlinx.serialization.json.JsonElement?) { values[key] = value }
    fun copy() = EndpointReadCache().also { it.values.putAll(values) }
}

internal class EndpointSet<T> private constructor(private val base: Reads<T>,
    private val edits: Map<T, Boolean>) : AbstractSet<T>(), IndexedSet<T> {
    constructor(lookup: (T) -> Boolean, count: () -> Int, all: () -> Set<T>) : this(Reads(lookup, count, all), emptyMap())
    private class Reads<T>(val lookup: (T) -> Boolean, count: () -> Int, all: () -> Set<T>) {
        val size by lazy(count)
        val values by lazy(all)
        val membership = mutableMapOf<T, Boolean>()
        fun contains(value: T) = membership.getOrPut(value) { lookup(value) }
    }
    override fun contains(element: T): Boolean = edits[element] ?: base.contains(element)
    override val size: Int get() = base.size + edits.entries.sumOf { (key, present) ->
        (if (present) 1 else 0) - (if (base.contains(key)) 1 else 0) }
    override fun iterator(): Iterator<T> = if (edits.isEmpty()) base.values.iterator() else
        (base.values.filter { edits[it] != false } + edits.filterValues { it }.keys).toSet().iterator()
    override fun adding(value: T): Set<T> = EndpointSet(base, edits + (value to true))
    override fun removing(value: T): Set<T> = EndpointSet(base, edits + (value to false))
}

internal class EndpointList<T>(override val size: Int, private val lookup: (Int) -> T) : AbstractList<T>(), IndexedList<T> {
    override fun get(index: Int): T { require(index in 0 until size); return lookup(index) }
    override fun appending(value: T): List<T> = EndpointList(size + 1) { if (it == size) value else get(it) }
    override fun removingLast(): List<T> = EndpointList(size - 1) { get(it) }
}
