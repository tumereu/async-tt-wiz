package org.asyncttwiz.protocol

import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Same flat wire shape, but cold decoding prepares shared pages before the first normal edit. */
internal class SharedMapSerializer<K, V>(key: KSerializer<K>, value: KSerializer<V>) : KSerializer<Map<K, V>> {
    private val delegate = MapSerializer(key, value)
    override val descriptor get() = delegate.descriptor
    override fun serialize(encoder: Encoder, value: Map<K, V>) = delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): Map<K, V> = delegate.deserialize(decoder).toPersistentMap()
}

internal object RedoQueuesSerializer : KSerializer<Map<String, List<GameplayRedoEntry>>> {
    private val entries = object : KSerializer<List<GameplayRedoEntry>> {
        private val delegate = ListSerializer(GameplayRedoEntry.serializer())
        override val descriptor get() = delegate.descriptor
        override fun serialize(encoder: Encoder, value: List<GameplayRedoEntry>) = delegate.serialize(encoder, value)
        override fun deserialize(decoder: Decoder): List<GameplayRedoEntry> = delegate.deserialize(decoder).toPersistentList()
    }
    private val delegate = MapSerializer(String.serializer(), entries)
    override val descriptor get() = delegate.descriptor
    override fun serialize(encoder: Encoder, value: Map<String, List<GameplayRedoEntry>>) = delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): Map<String, List<GameplayRedoEntry>> = delegate.deserialize(decoder).toPersistentMap()
}
