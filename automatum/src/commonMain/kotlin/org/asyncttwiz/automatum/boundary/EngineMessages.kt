package org.asyncttwiz.automatum.boundary

import arcanum.engine.input.Input
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.asyncttwiz.automatum.CardPlay
import org.asyncttwiz.automatum.CardPlays
import org.asyncttwiz.automatum.HandPlays

/*
 * The engine boundary (docs/specs/web-client.md §1 item 3): the page (Android's main thread, the browser's page) asks the
 * engine side (Android's engine thread, the browser's Web Worker) for planning work on a board of a game by request and
 * answer. One design on both platforms: across a Web Worker they travel as JSON strings ([EngineMessages]); on Android
 * the same objects are handed over in-process ([InProcessEngineChannel]), nothing serialized.
 *
 * A board is named by its game and the page's number for it ([EngineRequest.board], rising per game as the page moves
 * on); the engine side holds a copy of the boards it plans on, sent as a snapshot ([EngineRequest.Load]) and then as the
 * events that changed it ([EngineRequest.Advance]).
 */

/** A request to the engine side: [seq] numbers it on its page (rising), [game] and [board] name the board it is about. */
@Serializable
sealed interface EngineRequest {
    val seq: Long
    val game: String
    val board: Long

    /** The board's state as a snapshot: the game's card definitions and its model, in the engine's stored format
     * ([org.asyncttwiz.protocol.game.EngineJson.encodeDefinitions], [org.asyncttwiz.protocol.game.EngineJson.encodeModel]).
     * The engine side holds it in place of any board of the game it held. */
    @Serializable @SerialName("load")
    data class Load(override val seq: Long, override val game: String, override val board: Long,
        val definitions: String, val model: String) : EngineRequest

    /** What changed from the board [from] the engine side holds to [board]: the events [revert]ed (the latest first),
     * then the events [apply]ed (in order), each in the engine's stored format. */
    @Serializable @SerialName("advance")
    data class Advance(override val seq: Long, override val game: String, override val board: Long, val from: Long,
        val apply: List<String>, val revert: List<String> = emptyList()) : EngineRequest

    /** The page is done with [game]: the engine side forgets its board. */
    @Serializable @SerialName("close")
    data class Close(override val seq: Long, override val game: String, override val board: Long = 0) : EngineRequest

    /** What [viewer] may cast or play from their hand row on the board: the trial casts of [HandPlays]. */
    @Serializable @SerialName("hand-plays")
    data class HandPlaysOf(override val seq: Long, override val game: String, override val board: Long,
        val viewer: String) : EngineRequest

    /** What the engine offers [viewer] for each card on the board ([CardPlays]: the card menus). */
    @Serializable @SerialName("card-plays")
    data class CardPlaysOf(override val seq: Long, override val game: String, override val board: Long,
        val viewer: String) : EngineRequest
}

/** The engine side's answer to the request [seq] about [game]'s [board], with the time it worked on it ([micros]). */
@Serializable
sealed interface EngineAnswer {
    val seq: Long
    val game: String
    val board: Long
    val micros: Long

    /** The board sent ([EngineRequest.Load], [EngineRequest.Advance]) is held now. */
    @Serializable @SerialName("held")
    data class Held(override val seq: Long, override val game: String, override val board: Long,
        override val micros: Long) : EngineAnswer

    /** The engine side doesn't hold [board] (it holds [held] of the game, or nothing): the page sends it, then asks again. */
    @Serializable @SerialName("missing")
    data class Missing(override val seq: Long, override val game: String, override val board: Long,
        override val micros: Long, val held: Long? = null) : EngineAnswer

    /** The work failed with an exception [kind] (its simple class name) and [message]; the engine side forgets the game's
     * board when it was a board's transfer that failed. */
    @Serializable @SerialName("failed")
    data class Failed(override val seq: Long, override val game: String, override val board: Long,
        override val micros: Long, val kind: String, val message: String? = null) : EngineAnswer

    /** [EngineRequest.HandPlaysOf]'s answer. */
    @Serializable @SerialName("hand-plays")
    data class HandPlaysAnswer(override val seq: Long, override val game: String, override val board: Long,
        override val micros: Long, @Serializable(with = HandPlaysSerializer::class) val plays: HandPlays) : EngineAnswer

    /** [EngineRequest.CardPlaysOf]'s answer. */
    @Serializable @SerialName("card-plays")
    data class CardPlaysAnswer(override val seq: Long, override val game: String, override val board: Long,
        override val micros: Long, @Serializable(with = CardPlaysSerializer::class) val plays: CardPlays) : EngineAnswer
}

/** The boundary's messages as JSON strings, as they cross a Web Worker (the engine's own format for its inputs). */
object EngineMessages {
    val json: Json = Json { encodeDefaults = false }

    fun encode(request: EngineRequest): String = json.encodeToString(EngineRequest.serializer(), request)
    fun decodeRequest(text: String): EngineRequest = json.decodeFromString(EngineRequest.serializer(), text)
    fun encode(answer: EngineAnswer): String = json.encodeToString(EngineAnswer.serializer(), answer)
    fun decodeAnswer(text: String): EngineAnswer = json.decodeFromString(EngineAnswer.serializer(), text)
}

@Serializable
private class HandPlaysData(val byCard: Map<String, List<Input>>, val offZone: List<String> = emptyList())

/** [HandPlays] as its cards' inputs and the cards from other zones. */
object HandPlaysSerializer : KSerializer<HandPlays> {
    private val data = HandPlaysData.serializer()
    override val descriptor: SerialDescriptor = SerialDescriptor("org.asyncttwiz.automatum.HandPlays", data.descriptor)
    override fun serialize(encoder: Encoder, value: HandPlays) = encoder.encodeSerializableValue(data, HandPlaysData(value.byCard, value.offZone))
    override fun deserialize(decoder: Decoder): HandPlays =
        decoder.decodeSerializableValue(data).let { HandPlays.restored(it.byCard, it.offZone) }
}

/** [CardPlays] as its plays by card. */
object CardPlaysSerializer : KSerializer<CardPlays> {
    private val data = MapSerializer(String.serializer(), ListSerializer(CardPlay.serializer()))
    override val descriptor: SerialDescriptor = SerialDescriptor("org.asyncttwiz.automatum.CardPlays", data.descriptor)
    override fun serialize(encoder: Encoder, value: CardPlays) = encoder.encodeSerializableValue(data, value.byCard)
    override fun deserialize(decoder: Decoder): CardPlays = CardPlays.restored(decoder.decodeSerializableValue(data))
}
