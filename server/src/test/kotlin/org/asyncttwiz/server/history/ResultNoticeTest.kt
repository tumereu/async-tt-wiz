package org.asyncttwiz.server.history

import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import java.util.UUID
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.MatchEnding
import org.asyncttwiz.protocol.game.TableChange
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
}
