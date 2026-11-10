package org.asyncttwiz.server.decks

import org.asyncttwiz.protocol.CardListSupport
import org.asyncttwiz.server.catalog.ServerCatalogs
import org.asyncttwiz.server.generated.tables.Definition.DEFINITION
import org.jooq.DSLContext
import org.jooq.impl.DSL

/**
 * LOB-124 (I-D51): a deck's or cube's Automatum support, worked out by the server whenever it reads the list: its cards'
 * printings against the current card catalog's marks (SYS-082), the marks the app reads. Nothing is stored.
 */
internal object CardListSupports {
    /** The printing id (null when none) and name of each of [digests]' stored card data, in one query. */
    fun cards(sql: DSLContext, digests: Set<String>): Map<String, Pair<String?, String>> {
        if (digests.isEmpty()) return emptyMap()
        val printing = DSL.field("({0}::jsonb ->> 'printingId')", String::class.java, DEFINITION.PAYLOAD)
        val name = DSL.field("({0}::jsonb ->> 'name')", String::class.java, DEFINITION.PAYLOAD)
        return sql.select(DEFINITION.DIGEST, printing, name).from(DEFINITION).where(DEFINITION.DIGEST.`in`(digests))
            .fetch().associate { it.value1()!! to (it.value2() to it.value3().orEmpty()) }
    }

    /** The support of [cards] (printing id or none, and name) by the current catalog; null without one (not known). */
    fun support(cards: Collection<Pair<String?, String>>): CardListSupport? = ServerCatalogs.current()?.support(cards)

    /** The support of the cards [digests] name, by the current catalog; null without one. */
    fun ofDigests(sql: DSLContext, digests: Collection<String>): CardListSupport? =
        ServerCatalogs.current()?.let { catalog -> catalog.support(cards(sql, digests.toSet()).values) }
}
