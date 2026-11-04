package org.asyncttwiz.storage

import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.game.OutsideCards

/**
 * The dungeon cards of the installed catalog (CR 309: in card data they are token records), each once by name, as card data of their
 * own ([OutsideCards.dungeon]): what a player's device brings to a game whose deck ventures into the dungeon or takes the initiative.
 * Empty when the catalog isn't installed or its tokens aren't prepared yet. Blocking: call on a worker.
 */
fun CatalogRepository.dungeonDefinitions(): List<SubmittedDefinition> {
    val tokens = try { loadTokens() } catch (_: IllegalStateException) { return emptyList() }
    return tokens.choices.asSequence()
        .filter { choice -> choice.submitted.definition.faces.any { it.typeLine.removePrefix("Token ").startsWith("Dungeon") } }
        .mapNotNull { OutsideCards.dungeon(it.submitted.definition) }
        .distinctBy { it.definition.name }.sortedBy { it.definition.name }.take(OutsideCards.MAX).toList()
}
