package org.asyncttwiz.automatum

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.asyncttwiz.protocol.MtgRef

/** An amount for an object or player ([RefAmounts]'s entries). */
@Serializable
private class RefAmount(val ref: MtgRef, val amount: Int)

/**
 * Web client step 4e (docs/specs/web-client.md §1 item 3): an amount for each object or player (a division's shares, lethal
 * damage) as it crosses the engine boundary: a list of entries in the map's order (JSON keys can't be objects).
 */
object RefAmounts : KSerializer<Map<MtgRef, Int>> {
    private val list = ListSerializer(RefAmount.serializer())
    override val descriptor: SerialDescriptor = SerialDescriptor("org.asyncttwiz.automatum.RefAmounts", list.descriptor)
    override fun serialize(encoder: Encoder, value: Map<MtgRef, Int>) =
        encoder.encodeSerializableValue(list, value.map { (ref, amount) -> RefAmount(ref, amount) })
    override fun deserialize(decoder: Decoder): Map<MtgRef, Int> =
        decoder.decodeSerializableValue(list).associateTo(LinkedHashMap()) { it.ref to it.amount }
}
