package org.asyncttwiz.e2e

import arcanum.engine.Engine
import arcanum.engine.change.Changes
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.CardId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameCards
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LineRandom
import org.asyncttwiz.protocol.game.TableState

/**
 * A game's deal as the server starts it (EngineGames.start, EngineDecks.seats): players seated by account id, each
 * library in deck order (Side and Maybe outside the game), the game's random [secret] shuffling them and drawing the opening hands, worked out here with
 * the same engine and protocol code. [hand] and [library] (top first) by card name.
 */
class Deal private constructor(private val hands: Map<String, List<String>>, private val libraries: Map<String, List<String>>) {
    fun hand(account: TestAccount): List<String> = hands.getValue(account.id)
    fun library(account: TestAccount): List<String> = libraries.getValue(account.id)
    override fun toString() = "hands $hands"

    companion object {
        fun of(players: List<Pair<TestAccount, TestDeck>>, starter: TestAccount, secret: String): Deal {
            val seats = players.sortedBy { it.first.id }.map { (account, deck) ->
                fun cards(prefix: String, list: List<Pair<TestCard, Int>>) = list.flatMap { (card, n) ->
                    List(n) { i -> DeckCard(CardId("${account.id}/$prefix${card.digest}/$i"), checkNotNull(GameCards.parse(card.definition).definition) { "Unreadable ${card.digest}" }) }
                }
                Seat(PlayerId(account.id), cards("", deck.entries), cards("side/", deck.side) + cards("maybe/", deck.maybe))
            }
            val engine = Engine.newGame(seats)
            for (p in engine.players.toList()) engine.apply(Changes.skipWhenNoChoice(engine, p.id, false))
            val model = GameModel(engine, TableState(secret = LineRandom.secret(java.util.HexFormat.of().parseHex(secret))))
            EventBuilder(model, "deal", "root", starter.id).run {
                perform(Input.StartGame(StartOptions(startingPlayer = PlayerId(starter.id), simultaneousMulligans = true, freeFirstMulligan = true)))
                answerRandoms()
                build()
            }
            val s = model.engine
            fun names(ids: List<arcanum.engine.state.ObjectId>) = ids.map { s.obj(it)?.definition?.faces?.first()?.name ?: "?" }
            return Deal(players.associate { (a, _) -> a.id to names(s.player(PlayerId(a.id)).hand.toList()) },
                players.associate { (a, _) -> a.id to names(s.player(PlayerId(a.id)).library.toList()) })
        }
    }
}
