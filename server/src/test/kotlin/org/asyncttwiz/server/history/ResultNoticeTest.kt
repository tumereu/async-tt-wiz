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

    // TRN-065, TRN-064, LOB-046: Bob concedes: back in the lobby, the result kept for Alice (Alice won, Bob conceded), not for
    // Bob; Bob may get Ready but nothing starts, Alice can't get Ready until her OK; after it the result is gone and her
    // Ready starts the next game
    @Test fun `a concession is kept for the other player until their OK and the next game waits for it`() = HistoryWorld.run { w ->
        val a = w.account("NoticeAlice"); val b = w.account("NoticeBob")
        val m = w.Match(a, b, "Notice Spark")
        m.start()
        m.concede()
        val notice = m.summary().resultNotice!!
        assertEquals(GameLifecycle.LOBBY, m.summary().lifecycle)
        assertEquals(a.account.accountId, notice.winner)
        assertEquals(MatchEnding.Conceded(b.account.accountId), notice.ending)
        assertEquals(listOf(a.account.accountId), notice.unseen, "the conceder has seen it")
        m.seated {
            m.bob.accepted(GameAction.Ready)
            assertEquals("resultNotSeen", m.alice.execute(GameAction.Ready).reason)
            assertEquals(GameLifecycle.LOBBY, m.summary().lifecycle, "nothing starts before Alice's OK")
            m.alice.accepted(GameAction.AcknowledgeResult)
            assertNull(m.summary().resultNotice, "both have seen it")
            m.alice.accepted(GameAction.Ready)
        }
        assertEquals(GameLifecycle.ACTIVE, m.summary().lifecycle, "the next game started")
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
