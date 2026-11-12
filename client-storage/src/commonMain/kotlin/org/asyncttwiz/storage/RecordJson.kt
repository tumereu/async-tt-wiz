package org.asyncttwiz.storage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Reading the card records' JSON (a printing's record, Cube Cobra's and Scryfall's answers) in common code, as client-storage
// read them with Gson before (web client step 3a): the same values, the same failures for what isn't there.

/** [text] as a JSON object; throws when it is no JSON or not an object. */
internal fun jsonObject(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

/** [text] as a JSON object, or null when it is no JSON or not an object. */
internal fun jsonObjectOrNull(text: String): JsonObject? = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()

/** [key]'s value as text when it is a string, number or boolean; null when it is missing, null, an object or an array. */
internal fun JsonObject.primitive(key: String): String? = this[key]?.primitiveText()

/** This value as text when it is a string, number or boolean, else null. */
internal fun JsonElement.primitiveText(): String? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

/** [key]'s value unless it is missing or null. */
internal fun JsonObject.present(key: String): JsonElement? = this[key]?.takeUnless { it is JsonNull }

/** [key]'s value as an array, or null when it is missing or not one. */
internal fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

/** This value as a boolean: a boolean, or text reading "true" in any case; throws on an object or array. */
internal fun JsonElement.asBoolean(): Boolean = jsonPrimitive.let { it.booleanOrNull ?: it.content.equals("true", ignoreCase = true) }

/** This value as text; throws on null, an object or an array. */
internal fun JsonElement.asText(): String = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
    ?: throw IllegalStateException("Not a JSON string: $this")
