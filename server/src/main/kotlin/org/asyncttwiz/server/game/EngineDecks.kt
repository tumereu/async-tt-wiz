package org.asyncttwiz.server.game

import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.state.CardId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.LobbyPreparation
import org.asyncttwiz.protocol.SavedDeckSection
import org.asyncttwiz.protocol.game.GameCards

/**
 * A starting game's decks as engine seats (I-D54 switch-over item 1): each prepared card, by its game-local id, with the
 * engine definition the parser makes of its card data on the server ([GameCards], the same parser as the device's).
 * Main decks become libraries, sideboards the cards outside the game. Players in seat order by account id, as the play
 * sorts them. A card whose printed values can't be read plays as an unknown card (no definition, D60): the readiness
 * check (I-D52) keeps decks Automatum can't play out of games.
 */
internal object EngineDecks {
    fun seats(preparation: LobbyPreparation): List<Seat> {
        val definitions = preparation.definitions.associateBy { it.digest }
        val parsed = definitions.mapValues { (_, card) -> GameCards.parse(card.definition).definition }
        return preparation.players.sortedBy { it.accountId }.map { player ->
            val deck = requireNotNull(player.deck) { "${player.accountId} has no deck" }
            fun section(section: SavedDeckSection) = deck.entries.filter { it.section == section }.flatMap { entry ->
                List(entry.quantity) { offset -> DeckCard(CardId(deck.card(entry, offset).id), parsed[entry.definition]) }
            }
            Seat(PlayerId(player.accountId), section(SavedDeckSection.MAINBOARD), section(SavedDeckSection.SIDEBOARD))
        }
    }
}
