package org.asyncttwiz.server.game

import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.state.CardId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.LobbyPreparation
import org.asyncttwiz.protocol.SavedDeckSection
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.game.GameCards
import org.asyncttwiz.protocol.game.OutsideCards

/**
 * A starting game's decks as engine seats (I-D54 switch-over item 1): each prepared card, by its game-local id, with the
 * engine definition the parser makes of its card data on the server ([GameCards], the same parser as the device's).
 * Main decks become libraries; Side and Maybe are the cards outside the game (LOB-141: what wishes, learn and the like can
 * fetch), with the dungeon cards the server brought with the player's Ready ([OutsideCards], CR 309.2). Players in seat order by account
 * id, as the play sorts them. A card whose printed values can't be read plays as an unknown card (no definition, D60): the
 * readiness check (I-D52) keeps decks Automatum can't play out of games, Maybe cards included.
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
            val outside = deck.outside.mapIndexed { n, digest -> DeckCard(CardId(OutsideCards.cardId(deck, n)), parsed[digest]) }
            Seat(PlayerId(player.accountId), section(SavedDeckSection.MAINBOARD),
                section(SavedDeckSection.SIDEBOARD) + section(SavedDeckSection.MAYBEBOARD) + outside)
        }
    }

    /** [actor]'s prepared deck's cards whose text ventures or takes the initiative (a quick look at the text, no parse):
     * every card of the deck may be played, Maybe too, from outside the game (LOB-141). */
    private fun venturing(preparation: LobbyPreparation, actor: String): List<SubmittedDefinition> {
        val deck = preparation.players.single { it.accountId == actor }.deck ?: return emptyList()
        val digests = deck.entries.map { it.definition }.toSet()
        return preparation.definitions.filter { it.digest in digests && OutsideCards.mayVenture(it.definition) }
    }

    /** LOB-117: whether [actor]'s prepared deck may venture into the dungeon or take the initiative, so its Ready brings
     * dungeons (most decks never do: their text says so without parsing them). */
    fun ventures(preparation: LobbyPreparation, actor: String): Boolean = venturing(preparation, actor).isNotEmpty()

    /** Whether [outside] has the dungeons [actor]'s prepared deck needs (CR 309.2a, 701.49d: a card that ventures needs a dungeon
     * a plain venture may enter, one that takes the initiative an Undercity), by the server's own parse. */
    fun outsideProvided(preparation: LobbyPreparation, actor: String, outside: List<SubmittedDefinition>): Boolean {
        val venturing = venturing(preparation, actor)
        if (venturing.isEmpty()) return true
        val needs = venturing.flatMap { GameCards.parse(it.definition).definition?.let(OutsideCards::needs).orEmpty() }.toSet()
        return OutsideCards.provides(needs, outside.mapNotNull { GameCards.parse(it.definition).definition })
    }
}
