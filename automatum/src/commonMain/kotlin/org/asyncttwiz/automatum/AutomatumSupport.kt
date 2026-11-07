package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.engine.Engine
import org.asyncttwiz.protocol.game.OutsideCards

/**
 * Whether Automatum plays a card fully by the rules (I-D51): a card is supported when its definition has no part the
 * engine leaves to the players ([Engine.manualParts]: no text the parser couldn't read, no `Manual` step or cost part, no
 * keyword done only by hand, none of the few effects and keyword actions the engine always leaves to the players, and
 * none of the shapes it can't finish at run time, pivot review #3). Worked out from the parsed definition, so it follows
 * the parser and engine of this app version; a run-time step the engine still leaves to the players is skipped and
 * reported ([EngineRun.perform]). Venturing into the dungeon and the initiative are played by the rules: the player's
 * device brings its catalog's dungeon cards to the game ([org.asyncttwiz.protocol.game.OutsideCards]).
 */
object AutomatumSupport {
    private const val NO_DUNGEON = "by hand: venturing into the dungeon (no dungeon Automatum plays fully in the card records)"

    /** Every dungeon a card may need (OutsideCards.Need). */
    val ALL_DUNGEONS: Set<OutsideCards.Need> = setOf(OutsideCards.Need.Dungeon, OutsideCards.Need.Undercity)

    /** Whether [definition] has any part Automatum leaves to the players, with the dungeons the device can bring ([dungeons]). */
    fun byHand(definition: CardDefinition, dungeons: Set<OutsideCards.Need> = ALL_DUNGEONS): Boolean = handParts(definition, dungeons).isNotEmpty()

    /** The parts of [definition] Automatum leaves to the players, one short line each (empty: fully supported). A card that
     * ventures into the dungeon or takes the initiative needs a dungeon the device can bring that Automatum plays fully
     * ([dungeons]: what its catalog provides, OutsideCards.provided): without one the card is unsupported (I-D51, I-D52). */
    fun handParts(definition: CardDefinition, dungeons: Set<OutsideCards.Need> = ALL_DUNGEONS): List<String> {
        val found = mutableListOf<String>()
        if (!dungeons.containsAll(OutsideCards.needs(definition))) found += NO_DUNGEON
        found += Engine.manualParts(definition)
        return found.distinct()
    }
}
