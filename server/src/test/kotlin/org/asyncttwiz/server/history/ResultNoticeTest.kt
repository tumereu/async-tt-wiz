package org.asyncttwiz.server.history

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.state.CardId
import arcanum.engine.state.PlayerId
import java.util.UUID
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.MatchEnding
import org.asyncttwiz.protocol.StarterChoice
import org.asyncttwiz.protocol.game.Attention
import org.asyncttwiz.protocol.game.TableChange
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.game.engineSeats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import arcanum.engine.setup.GameResult as EngineResult
import org.asyncttwiz.protocol.game.ResultSuggestion as TableSuggestion

/**
 * TRN-065, LOB-046 (owner 2026-11-07): the server keeps each player's OK of a match's result: the player whose own action
 * ended it has seen it; the other sees it until they tap OK, and the next game starts only once both have.
 */
class ResultNoticeTest {
    private fun id() = UUID.randomUUID().toString()

    // TRN-065, TRN-064, LOB-046 (owner, 2026-11-09: nobody goes to the lobby until both have pressed OK): Bob concedes: the
    // match is final and recorded at once, the result kept for Alice (Alice won, Bob conceded), not for Bob; neither can get
    // Ready before her OK (both are still at the table); after it the result is gone and both Readies start the next game
    @Test fun `a concession is kept for the other player until their OK and the next game waits for it`() = HistoryWorld.run { w ->
        val a = w.account("NoticeAlice"); val b = w.account("NoticeBob")
        val m = w.Match(a, b, "Notice Spark")
        m.start()
        m.concede()
        val notice = m.summary().resultNotice!!
        assertEquals(GameLifecycle.LOBBY, m.summary().lifecycle)
        assertEquals(1L, w.number("SELECT count(*) FROM match_record"), "recorded at once")
        assertEquals(a.account.accountId, notice.winner)
        assertEquals(MatchEnding.Conceded(b.account.accountId), notice.ending)
        assertEquals(listOf(a.account.accountId), notice.unseen, "the conceder has given theirs")
        m.seated {
            assertEquals("resultNotSeen", m.bob.execute(GameAction.Ready).reason)
            assertEquals("resultNotSeen", m.alice.execute(GameAction.Ready).reason)
            assertEquals(GameLifecycle.LOBBY, m.summary().lifecycle, "nothing starts before Alice's OK")
            m.alice.accepted(GameAction.AcknowledgeResult)
            assertNull(m.summary().resultNotice, "both have pressed OK")
            m.bob.accepted(GameAction.Ready)
            m.alice.accepted(GameAction.Ready)
        }
        assertEquals(GameLifecycle.ACTIVE, m.summary().lifecycle, "the next game started")
    }

    // TRN-065, LOB-046 (owner, 2026-11-09): while Alice's OK on Bob's concession is still to come, the lobby takes nothing
    // from either player: decks, the starting player, Show deck, Ready, cancelling the game; the
    // conceder's own OK (another device of his) changes nothing. Once hers comes, all of it works again
    @Test fun `the lobby takes nothing until both OKs stand`() = HistoryWorld.run { w ->
        val a = w.account("WaitLobbyAlice"); val b = w.account("WaitLobbyBob")
        val m = w.Match(a, b, "Wait Lobby")
        val other = w.deck(b, "Bob Other", "U")
        m.start()
        m.concede()
        val refused = listOf(
            m.bob to GameAction.SelectDeck(other.deck.id, other.deck.revision),
            m.alice to GameAction.SetStarter(StarterChoice(b.account.accountId)),
            m.alice to GameAction.SetAlternateStarter(false),
            m.bob to GameAction.ShowDeck(true),
            m.bob to GameAction.WithdrawReady,
            m.bob to GameAction.CancelSetup,
            m.alice to GameAction.CancelSetup)
        for ((stream, action) in refused) assertEquals("resultNotSeen", stream.execute(action).reason, "$action")
        m.bob.accepted(GameAction.AcknowledgeResult)
        assertEquals(listOf(a.account.accountId), m.summary().resultNotice!!.unseen, "the conceder's OK takes nothing from Alice's")
        assertEquals(GameLifecycle.LOBBY, m.summary().lifecycle)
        m.alice.accepted(GameAction.AcknowledgeResult)
        m.bob.accepted(GameAction.SelectDeck(other.deck.id, other.deck.revision))
        m.alice.accepted(GameAction.SetStarter(StarterChoice(b.account.accountId)))
        m.bob.accepted(GameAction.CancelSetup)
    }

    // TRN-065, TRN-064 (owner, 2026-11-09): Alice's move made on the same head is still on its way when Bob's concession
    // lands: the concession stands, her move is refused, and the match is recorded once
    @Test fun `a move in flight when a concession lands is refused`() = HistoryWorld.run { w ->
        val a = w.account("FlightAlice"); val b = w.account("FlightBob")
        val m = w.Match(a, b, "In Flight")
        m.start()
        m.bobKeeps()
        val device = m.device(a)
        val open = device.model.engine.pending!!.decision
        val keep = device.act(id(), Input.Answer(open.id, arcanum.engine.decision.Answer.Option("Keep")))
        // Sent as her device did, for the play it was made in
        val sent = org.asyncttwiz.protocol.GameCommand(id(), org.asyncttwiz.protocol.GameScope(m.game.id, playId = m.summary().playId),
            GameAction.Submit(keep), m.alice.stream.streamId, m.alice.stream.generation)
        m.concede()
        w.games.admit(a, m.alice.hello, m.alice.connection, sent)
        w.games.executeOne()
        assertEquals("lifecycleEnded", w.games.results(a, m.game.id, listOf(sent.requestId)).statuses.single().result!!.reason)
        assertEquals(GameLifecycle.LOBBY, m.summary().lifecycle)
        assertEquals(MatchEnding.Conceded(b.account.accountId), m.summary().resultNotice!!.ending)
        assertEquals(1L, w.number("SELECT count(*) FROM match_record"))
    }

    // TRN-065, TRN-047: Alice suggests her win, Bob accepts: the match ends at once, and the result (agreed, with its reason)
    // is Alice's to see; an OK from a second device of hers changes nothing more
    @Test fun `an accepted suggestion ends the match at once and is kept for the suggester`() = HistoryWorld.run { w ->
        val a = w.account("AgreeNoticeAlice"); val b = w.account("AgreeNoticeBob")
        val m = w.Match(a, b, "Agreed Notice")
        m.start()
        val alice = PlayerId(a.account.accountId)
        val suggestion = TableSuggestion(alice, EngineResult.Won(alice), "Infinite combo")
        m.alice.accepted(GameAction.Submit(m.device(a).act(id(), null, listOf(TableChange.Suggestion(null, suggestion)))))
        m.bob.accepted(GameAction.Submit(m.device(b).act(id(), Input.AgreedResult(EngineResult.Won(alice)),
            listOf(TableChange.Suggestion(suggestion, null)))))
        assertEquals(GameLifecycle.LOBBY, m.summary().lifecycle, "accepting ends the match")
        val notice = m.summary().resultNotice!!
        assertEquals(a.account.accountId, notice.winner)
        assertEquals(MatchEnding.Agreed("Infinite combo"), notice.ending)
        assertEquals(listOf(a.account.accountId), notice.unseen, "the player who accepted has seen it")
        m.alice.accepted(GameAction.AcknowledgeResult)
        m.alice.accepted(GameAction.AcknowledgeResult)
        assertNull(m.summary().resultNotice)
    }

    // TRN-065, LOB-126, LOB-046 (owner, 2026-11-09): a result the rules gave (Bob draws from his empty library, CR 704.5b): one
    // OK leaves the match in play, with no record and the OK taken back at will; both OKs end it and nobody owes another
    @Test fun `a result the rules gave ends the match only on both OKs`() = HistoryWorld.run { w ->
        val a = w.account("RulesOkAlice"); val b = w.account("RulesOkBob")
        val m = w.Match(a, b, "Rules OK")
        val alice = PlayerId(a.account.accountId); val bob = PlayerId(b.account.accountId)
        fun deck(p: PlayerId, size: Int) = Seat(p, List(size) { i -> DeckCard(CardId("${p.value}.$i"), Cards.definition("Forest")) })
        engineSeats = { _ -> listOf(deck(alice, 40), deck(bob, 7)) }
        try { m.alice.accepted(GameAction.Ready); m.bob.accepted(GameAction.Ready) } finally { engineSeats = null }
        m.aliceKeeps()
        m.bobKeeps()
        while (Engine.result(m.device(a).model.engine) == null) {
            val who = Attention.of(m.device(a).model.engine)!!
            val (actor, stream) = if (who == alice) a to m.alice else b to m.bob
            stream.accepted(GameAction.Submit(m.device(actor).act(id(), Input.PassPriority(who))))
        }
        fun ok(actor: AccountService.Principal, stream: HistoryWorld.Stream, player: PlayerId, on: Boolean) =
            stream.accepted(GameAction.Submit(m.device(actor).act(id(), null, listOf(TableChange.ResultOk(player, on)))))
        ok(a, m.alice, alice, true)
        assertEquals(GameLifecycle.ACTIVE, m.summary().lifecycle, "Bob's OK is still to come")
        assertEquals(0L, w.number("SELECT count(*) FROM match_record"), "stats wait for it")
        ok(a, m.alice, alice, false)
        ok(b, m.bob, bob, true)
        assertEquals(GameLifecycle.ACTIVE, m.summary().lifecycle, "Alice took hers back")
        ok(a, m.alice, alice, true)
        assertEquals(GameLifecycle.LOBBY, m.summary().lifecycle, "both OKs make it final")
        assertNull(m.summary().resultNotice, "nobody owes an OK any more")
        assertEquals(1L, w.number("SELECT count(*) FROM match_record"))
    }
}
