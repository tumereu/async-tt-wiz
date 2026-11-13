package org.asyncttwiz.protocol.game

import arcanum.api.CardDefinition
import arcanum.engine.change.Change
import arcanum.engine.state.GameState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.asyncttwiz.protocol.sha256

/**
 * The engine card definitions of one game, stored once per game (I-D54 §4), each by [key]: the digest of its JSON. The
 * cards of the game's decks; tokens' and copies' definitions made during play stay written out where they are.
 */
class GameDefinitions(private val byKey: Map<String, CardDefinition>) {
    /** [definitions] keyed by their digests ([key]). */
    constructor(definitions: Collection<CardDefinition>) : this(definitions.associateBy(::key))

    private val json: Map<String, JsonElement> by lazy { byKey.mapValues { EngineJson.json.encodeToJsonElement(CardDefinition.serializer(), it.value) } }
    private val keys: Map<JsonElement, String> by lazy { json.entries.associate { (k, v) -> v to k } }

    val size: Int get() = byKey.size
    val all: Map<String, CardDefinition> get() = byKey

    operator fun get(key: String): CardDefinition? = byKey[key]
    internal fun json(key: String): JsonElement? = json[key]
    internal fun keyOf(definition: JsonElement): String? = keys[definition]

    companion object {
        /** The digest key of [definition]. */
        fun key(definition: CardDefinition): String =
            "sha256:" + sha256(EngineJson.json.encodeToString(CardDefinition.serializer(), definition).encodeToByteArray())

        /** Every definition of [state]'s objects (a new game's: its decks and sideboards). */
        fun of(state: GameState): GameDefinitions = GameDefinitions(state.objects.values.mapNotNull { it.definition }.distinct())
    }
}

/**
 * Events and states in the engine's own serialized format (I-D54 §4: stored games depend on it; a change of the engine's
 * format may need a wipe migration, owner accepted), with every card object's definition written as a reference
 * (`"#sha256:…"`) to the game's [definitions] where it is one of them.
 */
class EngineJson(val definitions: GameDefinitions) {
    fun encodeEvent(event: GameEvent): String = encode(GameEvent.serializer(), event)
    fun decodeEvent(text: String): GameEvent = decode(GameEvent.serializer(), text)

    fun encodeModel(model: GameModel): String = encode(StoredModel.serializer(), StoredModel(model.engine, model.table))
    fun decodeModel(text: String): GameModel = decode(StoredModel.serializer(), text).let { GameModel(it.engine, it.table) }

    fun encodeChanges(changes: List<Change>): String = encode(ListSerializer(Change.serializer()), changes)

    fun encodeDefinitions(): String = json.encodeToJsonElement(DEFINITIONS, definitions.all).toString()

    fun <T> encode(serializer: KSerializer<T>, value: T): String = compact(json.encodeToJsonElement(serializer, value)).toString()
    fun <T> decode(serializer: KSerializer<T>, text: String): T = json.decodeFromJsonElement(serializer, expand(json.parseToJsonElement(text)))

    /** [encode] as a JSON element, to go inside a larger message (web client step 4c: the engine boundary's boards). */
    fun <T> encodeElement(serializer: KSerializer<T>, value: T): JsonElement = compact(json.encodeToJsonElement(serializer, value))
    fun <T> decodeElement(serializer: KSerializer<T>, element: JsonElement): T = json.decodeFromJsonElement(serializer, expand(element))

    /** [encodeModel] and [decodeModel] as JSON elements. */
    fun encodeModelElement(model: GameModel): JsonElement = encodeElement(StoredModel.serializer(), StoredModel(model.engine, model.table))
    fun decodeModelElement(element: JsonElement): GameModel = decodeElement(StoredModel.serializer(), element).let { GameModel(it.engine, it.table) }

    private fun compact(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (key == DEFINITION && value is JsonObject) definitions.keyOf(value)?.let { JsonPrimitive(REF + it) } ?: compact(value)
            else compact(value)
        })
        is JsonArray -> JsonArray(element.map(::compact))
        else -> element
    }

    private fun expand(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (key == DEFINITION && value is JsonPrimitive && value.isString && value.content.startsWith(REF)) {
                val ref = value.content.removePrefix(REF)
                definitions.json(ref) ?: throw IllegalArgumentException("No card definition $ref in this game")
            } else expand(value)
        })
        is JsonArray -> JsonArray(element.map(::expand))
        else -> element
    }

    @Serializable
    private class StoredModel(val engine: GameState, val table: TableState)

    companion object {
        /** The engine's format as stored: kotlinx JSON, defaults left out. */
        val json: Json = Json { encodeDefaults = false }
        private const val DEFINITION = "definition"
        private const val REF = "#"
        private val DEFINITIONS = MapSerializer(String.serializer(), CardDefinition.serializer())

        /** The definitions [encodeDefinitions] wrote, under the keys they were stored with. */
        fun decodeDefinitions(text: String): GameDefinitions = GameDefinitions(json.decodeFromString(DEFINITIONS, text))
    }
}
