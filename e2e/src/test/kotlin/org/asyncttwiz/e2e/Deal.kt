package org.asyncttwiz.e2e

import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
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
 * the same engine and protocol code. [hand] and [library] (top first) by card name; [mulliganHand] the hand a player
 * draws after their mulligans (HIS-042, HIS-192: from their own random stream, whatever the other player does meanwhile).
 */
class Deal private constructor(private val hands: Map<String, List<String>>, private val libraries: Map<String, List<String>>,
    private val started: GameModel, private val starter: PlayerId) {
    fun hand(account: TestAccount): List<String> = hands.getValue(account.id)
    fun library(account: TestAccount): List<String> = libraries.getValue(account.id)
    override fun toString() = "hands $hands"

    private val mulligans = HashMap<Pair<String, Int>, List<String>>()

    /**
     * The hand [account] holds after [times] mulligans (TRN-062: the first free), by card name: each player's mulligans
     * are their own (TRN-027) and their shuffles come from their own stream, so the other player's moves don't change it.
     * Worked out with the starting player keeping first (the other player's then), a bottom owed put back as the last
     * cards of the hand (it changes nothing the next mulligan draws: the hand goes back and is shuffled in).
     */
    fun mulliganHand(account: TestAccount, times: Int): List<String> = mulligans.getOrPut(account.id to times) {
        val model = started.copy()
        val me = PlayerId(account.id)
        var n = 0
        fun make(author: PlayerId, input: Input) = EventBuilder(model, "deal-${n++}", "deal", author.value).run { perform(input); answerRandoms(); build() }
        if (me != starter) make(starter, Input.Answer(model.engine.pending!!.decision.id, Answer.Option("Keep")))
        repeat(times) {
            val d = checkNotNull(model.engine.pending?.decision) { "no decision for ${account.id}'s mulligan" }
            check(d.player == me) { "${d.player}'s decision open, not ${account.id}'s" }
            if (d is Decision.ChooseObjects) {
                make(me, Input.Answer(d.id, Answer.Objects(model.engine.player(me).hand.takeLast(d.min))))
                make(me, Input.Answer(model.engine.pending!!.decision.id, Answer.Option("Mulligan")))
            } else make(me, Input.Answer(d.id, Answer.Option("Mulligan")))
        }
        val s = model.engine
        s.player(me).hand.map { s.obj(it)?.definition?.faces?.first()?.name ?: "?" }
    }

    companion object {
        fun of(players: List<Pair<TestAccount, TestDeck>>, starter: TestAccount, secret: String): Deal {
            val seats = players.sortedBy { it.first.id }.map { (account, deck) ->
                fun cards(prefix: String, list: List<Pair<TestCard, Int>>) = list.flatMap { (card, n) ->
                    List(n) { i -> DeckCard(CardId("${account.id}/$prefix${card.digest}/$i"), checkNotNull(GameCards.parse(card.definition).definition) { "Unreadable ${card.digest}" }) }
                }
                Seat(PlayerId(account.id), cards("", deck.entries), cards("side/", deck.side) + cards("maybe/", deck.maybe))
            }
            val engine = Engine.newGame(seats)
            val model = GameModel(engine, TableState(secret = LineRandom.secret(java.util.HexFormat.of().parseHex(secret))))
            EventBuilder(model, "deal", "root", starter.id).run {
                perform(Input.StartGame(StartOptions(startingPlayer = PlayerId(starter.id), simultaneousMulligans = true, freeFirstMulligan = true)))
                answerRandoms()
                build()
            }
            val s = model.engine
            fun names(ids: List<arcanum.engine.state.ObjectId>) = ids.map { s.obj(it)?.definition?.faces?.first()?.name ?: "?" }
            return Deal(players.associate { (a, _) -> a.id to names(s.player(PlayerId(a.id)).hand.toList()) },
                players.associate { (a, _) -> a.id to names(s.player(PlayerId(a.id)).library.toList()) }, model, PlayerId(starter.id))
        }
    }
}
