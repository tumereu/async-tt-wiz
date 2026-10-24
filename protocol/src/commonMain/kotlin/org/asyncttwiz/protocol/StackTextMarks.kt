package org.asyncttwiz.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

typealias StackTextMarks = @Serializable(with = StackTextMarksSerializer::class) Set<Int>

/** Keep singleton encoding identical to old snapshots AND sealed event transitions. */
object StackTextMarksSerializer : KSerializer<Set<Int>> {
    override val descriptor = SetSerializer(Int.serializer()).descriptor
    override fun serialize(encoder: Encoder, value: Set<Int>) {
        val element = if (value.size == 1) JsonPrimitive(value.single()) else JsonArray(value.sorted().map(::JsonPrimitive))
        (encoder as JsonEncoder).encodeJsonElement(element)
    }
    override fun deserialize(decoder: Decoder): Set<Int> = when (val element = (decoder as JsonDecoder).decodeJsonElement()) {
        is JsonArray -> element.map { it.jsonPrimitive.int }.toSet()
        else -> setOf(element.jsonPrimitive.int)
    }
}
