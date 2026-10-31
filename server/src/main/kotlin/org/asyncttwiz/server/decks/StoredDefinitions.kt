package org.asyncttwiz.server.decks

import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.generated.tables.Definition.DEFINITION
import org.jooq.DSLContext

/**
 * Stored card definitions are named by the digest of their content and are never changed or deleted, so a
 * decoded one stays valid for the life of the process. Requests that show the same cards again (a pod during
 * its draft) read only the ones not seen recently. Bounded, so the Pi's memory stays small (SYS-025).
 */
object StoredDefinitions {
    private const val LIMIT = 4096
    private val json = DeckJson.format
    private val decoded = object : LinkedHashMap<String, CardDefinition>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CardDefinition>) = size > LIMIT
    }

    /** The stored definitions among [digests], ordered by digest; digests with no stored definition are left out. */
    fun read(sql: DSLContext, digests: Set<String>): List<SubmittedDefinition> {
        if (digests.isEmpty()) return emptyList()
        val known = synchronized(decoded) { digests.mapNotNull { digest -> decoded[digest]?.let { digest to it } }.toMap() }
        val missing = digests - known.keys
        val loaded = if (missing.isEmpty()) emptyMap() else sql.select(DEFINITION.DIGEST, DEFINITION.PAYLOAD).from(DEFINITION)
            .where(DEFINITION.DIGEST.`in`(missing)).fetch().associate { row ->
                val digest = row[DEFINITION.DIGEST]!!
                digest to StoredData.decode("card definition", digest) { json.decodeFromString<CardDefinition>(row[DEFINITION.PAYLOAD]!!) }
            }
        if (loaded.isNotEmpty()) synchronized(decoded) { decoded.putAll(loaded) }
        // Digests are "sha256:" and 64 lowercase hex digits (a table constraint): the database's
        // order by digest and plain string order agree.
        return (known + loaded).toSortedMap().map { (digest, definition) -> SubmittedDefinition(digest, definition) }
    }
}
