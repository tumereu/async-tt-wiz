package org.asyncttwiz.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.asyncttwiz.core.EntityId

/** Protocol-v1 scalar foundation, not a command processor or negotiation endpoint. */
object ScalarCodecs {
    private val json = Json

    fun encodeId(id: EntityId): String = JsonPrimitive(id.value).toString()

    fun decodeId(encoded: String): EntityId = EntityId(stringScalar(encoded))

    fun encodeRevision(revision: Long): String {
        require(revision >= 0) { "Revision must be nonnegative" }
        return JsonPrimitive(revision.toString()).toString()
    }

    fun decodeRevision(encoded: String): Long {
        val text = stringScalar(encoded)
        require(Regex("0|[1-9][0-9]*").matches(text)) { "Expected a canonical decimal revision" }
        return requireNotNull(text.toLongOrNull()) { "Revision exceeds signed 64-bit range" }
    }

    private fun stringScalar(encoded: String): String {
        val value = json.parseToJsonElement(encoded)
        require(value is JsonPrimitive && value.isString) { "Expected a JSON string" }
        return value.content
    }
}
