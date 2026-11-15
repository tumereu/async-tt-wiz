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

    fun decodeRevision(encoded: String): Long = revision(stringScalar(encoded))

    /**
     * The revision [text] names (a JSON string's content: "0" or digits without a leading zero). Checked without a regex: the
     * web client's table compares revisions many times a frame (GameSession.summary), and a Regex compiled for each call there
     * took a quarter of a frame's recomposition (owner, 2026-11-15: "Web FPS is still stuttery").
     */
    fun revision(text: String): Long {
        require(canonicalDecimal(text)) { "Expected a canonical decimal revision" }
        return requireNotNull(text.toLongOrNull()) { "Revision exceeds signed 64-bit range" }
    }

    /** Whether [text] is "0" or ASCII digits without a leading zero, at most [maxDigits] of them. */
    fun canonicalDecimal(text: String, maxDigits: Int = Int.MAX_VALUE): Boolean =
        text.isNotEmpty() && text.length <= maxDigits && (text.length == 1 || text[0] != '0') && text.all { it in '0'..'9' }

    private fun stringScalar(encoded: String): String {
        val value = json.parseToJsonElement(encoded)
        require(value is JsonPrimitive && value.isString) { "Expected a JSON string" }
        return value.content
    }
}
