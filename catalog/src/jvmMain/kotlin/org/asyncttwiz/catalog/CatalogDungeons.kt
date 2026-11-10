package org.asyncttwiz.catalog

import org.asyncttwiz.automatum.AutomatumSupport
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.game.GameCards
import org.asyncttwiz.protocol.game.OutsideCards

/**
 * LOB-117 (I-D52): the dungeons of a card catalog that Automatum plays fully (none with a room left to the players), each
 * once by name, as card data of their own ([OutsideCards.dungeon]): what the server brings outside the game with the Ready
 * of a player whose deck ventures into the dungeon or takes the initiative (CR 309.2), and what the catalog build counts as
 * provided when it marks a venturing card (section 3.8).
 */
object CatalogDungeons {
    /** The dungeons, sorted by name, at most [OutsideCards.MAX]; of a dungeon's printings one with a picture first. */
    fun definitions(catalog: CardCatalog): List<SubmittedDefinition> = catalog.printings.asSequence()
        .filter { it.token && catalog.faces[it.face].type.removePrefix("Token ").startsWith("Dungeon") }
        .sortedWith(compareBy({ it.scryfall == null }, { it.id }))
        .distinctBy { catalog.faces[it.face].name }
        .mapNotNull { runCatching { OutsideCards.dungeon(CatalogCards.definition(catalog, it)) }.getOrNull() }
        .filter { dungeon -> GameCards.parse(dungeon.definition).definition?.let { AutomatumSupport.handParts(it, emptySet()).isEmpty() } == true }
        .sortedBy { it.definition.name }.take(OutsideCards.MAX).toList()

    /** What [dungeons] provide of the dungeons a card may need: a plain venture's, the initiative's Undercity. */
    fun provided(dungeons: List<SubmittedDefinition>): Set<OutsideCards.Need> =
        OutsideCards.provided(dungeons.mapNotNull { GameCards.parse(it.definition).definition })
}
