package org.asyncttwiz.server.decks

import org.asyncttwiz.protocol.CardListSupport
import org.asyncttwiz.server.catalog.ServerCatalogs

/**
 * LOB-124 (I-D51): a deck's or cube's Automatum support, worked out by the server whenever it reads the list: its cards'
 * printings against the current card catalog's marks (SYS-082), the marks the app reads. Nothing is stored.
 */
internal object CardListSupports {
    /** The support of [cards] (printing id or none, and name) by the current catalog; null without one (not known). */
    fun support(cards: Collection<Pair<String?, String>>): CardListSupport? = ServerCatalogs.current()?.support(cards)
}
