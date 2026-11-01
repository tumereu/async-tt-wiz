package arcanum.engine.state

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** A player, named by the host ("alice"). Serialised as the plain string, so it can key JSON maps. */
@Serializable(with = PlayerIdSerializer::class)
data class PlayerId(val value: String) {
    override fun toString(): String = value
}

/**
 * A physical card or a token, the same in every zone: the host's own card id, or for a token the name-based UUID
 * the engine derives ([TokenIds], D35). Objects come and go as the card moves (CR 400.7); the card stays.
 */
@Serializable(with = CardIdSerializer::class)
data class CardId(val value: String) {
    override fun toString(): String = value
}

/**
 * An object (CR 109): its id changes whenever it changes zones (CR 400.7, D21), so a stale id reads as last known
 * information and can't be acted on (CR 608.2h).
 *
 * Serialised as one string (`c:<card>#<zoneChanges>`, `s:<id>`) so it can key JSON maps.
 */
@Serializable(with = ObjectIdSerializer::class)
sealed interface ObjectId {
    /** A card or token: [zoneChanges] counts the zone changes of [card] so far (D21). */
    @Serializable(with = CardObjectIdSerializer::class)
    data class Card(val card: CardId, val zoneChanges: Int) : ObjectId {
        /** The object [card] becomes when it next changes zones. */
        fun next(): Card = Card(card, zoneChanges + 1)
        override fun toString(): String = "$card#$zoneChanges"
    }

    /** An ability or a copy of a spell on the Stack (CR 113.7, 707.10, D35): not a card, never changes zones. */
    @Serializable(with = StackItemIdSerializer::class)
    data class StackItem(val id: String) : ObjectId {
        override fun toString(): String = "stack:$id"
    }
}

/** An effect object or delayed trigger the engine created (D19). */
@Serializable(with = EffectIdSerializer::class)
data class EffectId(val value: String) {
    override fun toString(): String = value
}

/** Something a target, a recipient or an attachment can be: an object or a player (CR 115.1). */
@Serializable
sealed interface TargetRef {
    @Serializable @SerialName("object") data class Object(val id: ObjectId) : TargetRef
    @Serializable @SerialName("player") data class Player(val id: PlayerId) : TargetRef
}

internal object PlayerIdSerializer : KSerializer<PlayerId> {
    override val descriptor = PrimitiveSerialDescriptor("arcanum.engine.PlayerId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: PlayerId) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder) = PlayerId(decoder.decodeString())
}

internal object CardIdSerializer : KSerializer<CardId> {
    override val descriptor = PrimitiveSerialDescriptor("arcanum.engine.CardId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: CardId) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder) = CardId(decoder.decodeString())
}

internal object EffectIdSerializer : KSerializer<EffectId> {
    override val descriptor = PrimitiveSerialDescriptor("arcanum.engine.EffectId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: EffectId) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder) = EffectId(decoder.decodeString())
}

internal object ObjectIdSerializer : KSerializer<ObjectId> {
    override val descriptor = PrimitiveSerialDescriptor("arcanum.engine.ObjectId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ObjectId) = encoder.encodeString(
        when (value) {
            is ObjectId.Card -> "c:${value.card.value}#${value.zoneChanges}"
            is ObjectId.StackItem -> "s:${value.id}"
        },
    )

    override fun deserialize(decoder: Decoder): ObjectId {
        val text = decoder.decodeString()
        return when {
            text.startsWith("c:") -> {
                val hash = text.lastIndexOf('#')
                require(hash > 2) { "Not an object id: $text" }
                ObjectId.Card(CardId(text.substring(2, hash)), text.substring(hash + 1).toInt())
            }
            text.startsWith("s:") -> ObjectId.StackItem(text.substring(2))
            else -> throw IllegalArgumentException("Not an object id: $text")
        }
    }
}

internal object CardObjectIdSerializer : KSerializer<ObjectId.Card> {
    override val descriptor = PrimitiveSerialDescriptor("arcanum.engine.ObjectId.Card", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ObjectId.Card) = ObjectIdSerializer.serialize(encoder, value)
    override fun deserialize(decoder: Decoder) = ObjectIdSerializer.deserialize(decoder) as ObjectId.Card
}

internal object StackItemIdSerializer : KSerializer<ObjectId.StackItem> {
    override val descriptor = PrimitiveSerialDescriptor("arcanum.engine.ObjectId.StackItem", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ObjectId.StackItem) = ObjectIdSerializer.serialize(encoder, value)
    override fun deserialize(decoder: Decoder) = ObjectIdSerializer.deserialize(decoder) as ObjectId.StackItem
}
