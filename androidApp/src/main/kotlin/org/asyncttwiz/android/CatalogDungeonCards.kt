package org.asyncttwiz.android

import org.asyncttwiz.automatum.AutomatumSupport
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.game.GameCards
import org.asyncttwiz.protocol.game.OutsideCards

/**
 * LOB-117 (I-D52): the dungeon cards the device brings to a game: its catalog's dungeons ([load]) that Automatum plays fully, none
 * with a room left to the players; read and parsed once per catalog generation ([generation]), with what they [provided]: the set
 * support worker asks for that once per card it reads. Blocking: call on a worker.
 */
internal class CatalogDungeonCards(private val load: () -> List<SubmittedDefinition>, private val generation: () -> String?,
    private val parse: (CardDefinition) -> arcanum.api.CardDefinition? = { GameCards.parse(it).definition }) {
    private class Read(val generation: String, val definitions: List<SubmittedDefinition>, val provided: Set<OutsideCards.Need>)
    @Volatile private var cached: Read? = null

    private fun read(): Read {
        val current = generation()
        cached?.takeIf { it.generation == current }?.let { return it }
        val parsed = load().mapNotNull { d -> parse(d.definition)?.takeIf { !AutomatumSupport.byHand(it) }?.let { d to it } }
        val read = Read(current.orEmpty(), parsed.map { it.first }, OutsideCards.provided(parsed.map { it.second }))
        if (current != null) cached = read
        return read
    }

    fun definitions(): List<SubmittedDefinition> = read().definitions

    /** What they provide of the dungeons a card may need: a plain venture's, the initiative's Undercity. */
    fun provided(): Set<OutsideCards.Need> = read().provided
}
