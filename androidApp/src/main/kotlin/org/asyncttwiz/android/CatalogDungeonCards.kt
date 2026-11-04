package org.asyncttwiz.android

import org.asyncttwiz.automatum.AutomatumSupport
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.game.GameCards
import org.asyncttwiz.protocol.game.OutsideCards

/**
 * LOB-117 (I-D52): the dungeon cards the device brings to a game: its catalog's dungeons ([load]) that Automatum plays fully, none
 * with a room left to the players; read once per catalog generation ([generation]). Blocking: call on a worker.
 */
internal class CatalogDungeonCards(private val load: () -> List<SubmittedDefinition>, private val generation: () -> String?) {
    @Volatile private var cached: Pair<String, List<SubmittedDefinition>>? = null

    fun definitions(): List<SubmittedDefinition> {
        val current = generation()
        cached?.takeIf { it.first == current }?.let { return it.second }
        val playable = load().filter { d -> GameCards.parse(d.definition).definition?.let { !AutomatumSupport.byHand(it) } == true }
        if (current != null) cached = current to playable
        return playable
    }

    /** What they provide of the dungeons a card may need: a plain venture's, the initiative's Undercity. */
    fun provided(): Set<OutsideCards.Need> = OutsideCards.provided(definitions().mapNotNull { GameCards.parse(it.definition).definition })
}
