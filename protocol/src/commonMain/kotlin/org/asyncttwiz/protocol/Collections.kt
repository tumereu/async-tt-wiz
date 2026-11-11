package org.asyncttwiz.protocol

// The common standard library has no public `replaceAll` (`java.util.List/Map.replaceAll` are JVM-only), so common code
// that must also build for Kotlin/Wasm uses these.

/** Replaces each element of this list by [transform] of it, in place. */
internal inline fun <T> MutableList<T>.replaceEach(transform: (T) -> T) {
    for (i in indices) this[i] = transform(this[i])
}

/** Replaces each value of this map by [transform] of its key and value, in place. */
internal inline fun <K, V> MutableMap<K, V>.replaceEach(transform: (K, V) -> V) {
    for (entry in entries) entry.setValue(transform(entry.key, entry.value))
}
