package org.asyncttwiz.server.history

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.LossReason
import arcanum.engine.setup.Seat
import arcanum.engine.state.CardId
import arcanum.engine.state.PlayerId
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameSession
import org.asyncttwiz.protocol.game.TableChange
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.accounts.AccountSettingsService
import org.asyncttwiz.server.game.EngineGames
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import arcanum.engine.setup.GameResult as EngineResult
import org.asyncttwiz.protocol.game.ResultSuggestion as TableSuggestion

/** Game history §1–2: the record of every finished match (and of an Unfinished one), and who sees which of its decks. */
class MatchHistoryTest {
    private fun id() = UUID.randomUUID().toString()
    private fun HistoryWorld.records(viewer: AccountService.Principal) =
        sql { MatchRecords.forAccount(it, UUID.fromString(viewer.account.accountId)) }

    // Game history §1.1, 1.2, 2.1, 2.2, 2.4 (CR 104.3a): Bob concedes; both get the record (result, how it ended, turns,
    // starter, mulligans, life, decks); Bob hides his decks, so Alice's record reads no deck of his until he shows that deck
    // in another game, and then it stays shown, also after he hides it again; Alice's deck shows for Bob (Show after first
    // game revealed it at the end)
    @Test fun `a conceded match is recorded for both and the opponent deck shows once shown, for good`() = HistoryWorld.run { w ->
        val a = w.account("HistAlice"); val b = w.account("HistBob")
        AccountSettingsService(w.runtime).set(b.account.accountId, AccountSettings(DeckVisibility.HIDDEN))
        val bobDeck = w.deck(b, "Bob Green", "G")
        val m = w.Match(a, b, "History Spark", bDeck = bobDeck)
        m.start()
        m.concede()
        assertEquals(GameLifecycle.LOBBY, m.summary().lifecycle)

        val mine = w.records(a).single()
        assertEquals(m.game.id, mine.gameId); assertEquals("History Spark", mine.gameName)
        assertEquals(MatchEnding.Conceded(b.account.accountId), mine.ending)
        assertEquals(0, mine.turns, "conceded in the opening")
        val alice = mine.player(a.account.accountId)!!; val bob = mine.player(b.account.accountId)!!
        assertEquals(SuggestedResult.WIN, alice.result); assertEquals(SuggestedResult.LOSS, bob.result)
        assertTrue(alice.onThePlay); assertFalse(bob.onThePlay)
        assertEquals(0, alice.mulligans); assertEquals(20, alice.finalLife)
        assertEquals("Alice Red", alice.deck!!.name); assertEquals("R", alice.deck!!.colors)
        assertEquals("Alice Red Bolt", alice.deck!!.cover!!.name)
        assertTrue(alice.deck!!.openable, "her own saved deck: Open deck")
        assertNull(bob.deck, "Bob's deck was never shown to Alice")

        val his = w.records(b).single()
        assertEquals(mine.id, his.id)
        assertEquals("Alice Red", his.player(a.account.accountId)!!.deck!!.name, "revealed at the end (Show after first game)")
        assertFalse(his.player(a.account.accountId)!!.deck!!.openable)
        assertEquals("Bob Green", his.player(b.account.accountId)!!.deck!!.name)
        assertEquals(listOf(mine.id), w.sql { MatchRecords.forGame(it, UUID.fromString(a.account.accountId), m.id) }.map { it.id })

        // Bob shows the same deck to Alice in another game: her earlier record fills in, and stays so when he hides it again
        val other = w.Match(a, b, "Second Spark", bDeck = bobDeck)
        other.bob.accepted(GameAction.ShowDeck(true))
        assertEquals("Bob Green", w.records(a).single().player(b.account.accountId)!!.deck!!.name)
        other.bob.accepted(GameAction.ShowDeck(false))
        assertEquals("Bob Green", w.records(a).single().player(b.account.accountId)!!.deck!!.name)
        assertEquals("G", w.records(a).single().player(b.account.accountId)!!.deck!!.colors)

        // §2.4: her own deck links to it only while it is saved
        val aliceDeck = w.records(a).single().player(a.account.accountId)!!.deck!!
        w.execute("DELETE FROM saved_deck WHERE id = ?::uuid", aliceDeck.sourceId)
        assertFalse(w.records(a).single().player(a.account.accountId)!!.deck!!.openable)
        assertEquals("Alice Red", w.records(a).single().player(a.account.accountId)!!.deck!!.name)
    }

    // Game history §1.2 (TRN-046, TRN-047): Alice suggests her win with a reason, Bob accepts, Alice confirms once she saw
    // it: an agreed result, its reason kept
    @Test fun `an agreed match keeps the suggestion's reason`() = HistoryWorld.run { w ->
        val a = w.account("AgreeAlice"); val b = w.account("AgreeBob")
        val m = w.Match(a, b, "Agreed Spark")
        m.start()
        fun device(actor: AccountService.Principal): GameSession {
            val snapshot = w.games.head(actor, m.game.id).engine!!
            return GameSession(EngineJson(EngineJson.decodeDefinitions(snapshot.definitions)).decodeModel(snapshot.state),
                snapshot.headId, PlayerId(actor.account.accountId))
        }
        val alice = PlayerId(a.account.accountId)
        val suggestion = TableSuggestion(alice, EngineResult.Won(alice), "Infinite combo")
        m.alice.accepted(GameAction.Submit(device(a).act(id(), null, listOf(TableChange.Suggestion(null, suggestion)))))
        val accept = device(b).act(id(), Input.AgreedResult(EngineResult.Won(alice)), listOf(TableChange.Suggestion(suggestion, null)))
        m.bob.accepted(GameAction.Submit(accept))
        m.alice.accepted(GameAction.EngineSeen(accept.id))
        m.alice.accepted(GameAction.Submit(device(a).act(id(), null, listOf(TableChange.ResultConfirmed(null, alice)))))
        assertEquals(GameLifecycle.LOBBY, m.summary().lifecycle)
        val record = w.records(b).single()
        assertEquals(MatchEnding.Agreed("Infinite combo"), record.ending)
        assertEquals(SuggestedResult.WIN, record.player(a.account.accountId)!!.result)
        assertEquals(SuggestedResult.LOSS, record.player(b.account.accountId)!!.result)
    }

    // Game history §1.2: by the rules, each loser's reason (CR 704.5a, 704.5b, 704.5c, 104.3e), and the draws: everyone
    // losing at once (CR 104.4a) and a loop (CR 104.4b); each written as its match's record from the engine's final state
    @Test fun `each way the rules end a match is recorded`() = HistoryWorld.run { w ->
        val a = w.account("RulesAlice"); val b = w.account("RulesBob")
        val m = w.Match(a, b, "Rules Spark")
        val alice = PlayerId(a.account.accountId); val bob = PlayerId(b.account.accountId)
        fun ended(vararg losses: Pair<PlayerId, LossReason>): MatchRecord {
            val state = Engine.newGame(listOf(alice, bob).map { p -> Seat(p, List(7) { DeckCard(CardId("${p.value}.$it"), Cards.definition("Forest")) }) })
            for ((player, reason) in losses) state.apply(Change.Lost(player, false, true, reason))
            val facts = EngineGames.MatchFacts.of(state, Engine.result(state)!!, null, null, mapOf(alice.value to 1, bob.value to 0))
            val play = UUID.randomUUID()
            w.sql { MatchRecords.finished(it, m.summary(), play, facts, OffsetDateTime.now(ZoneOffset.UTC)) }
            return w.records(a).first()
        }
        for ((reason, cause) in listOf(LossReason.ZeroLife to LossCause.ZeroLife, LossReason.EmptyLibrary to LossCause.EmptyLibrary,
                LossReason.Poison to LossCause.Poison, LossReason.Effect("Test Doom") to LossCause.CardEffect("Test Doom"))) {
            val record = ended(bob to reason)
            assertEquals(MatchEnding.ByRules, record.ending, "$reason")
            assertEquals(cause, record.player(bob.value)!!.lossCause, "$reason")
            assertNull(record.player(alice.value)!!.lossCause)
            assertEquals(SuggestedResult.WIN, record.player(alice.value)!!.result)
            assertEquals(1, record.player(alice.value)!!.mulligans)
        }
        val atOnce = ended(alice to LossReason.ZeroLife, bob to LossReason.Poison)
        assertEquals(MatchEnding.Draw(loop = false), atOnce.ending)
        assertEquals(listOf(SuggestedResult.DRAW, SuggestedResult.DRAW), atOnce.players.map { it.result })
        val loop = ended(alice to LossReason.Loop, bob to LossReason.Loop)
        assertEquals(MatchEnding.Draw(loop = true), loop.ending)
        assertEquals(6, w.records(a).size)
    }

    // Game history §1.3 (LOB-026): deleting a game in play leaves an Unfinished record, with no result and the turn reached
    @Test fun `deleting a game in play leaves an Unfinished record`() = HistoryWorld.run { w ->
        val a = w.account("DelAlice"); val b = w.account("DelBob")
        val m = w.Match(a, b, "Deleted Spark")
        m.start()
        m.alice.accepted(GameAction.DeleteGame)
        val record = w.records(b).single()
        assertEquals(MatchEnding.Unfinished, record.ending)
        assertFalse(record.finished)
        assertTrue(record.players.all { it.result == null })
        assertEquals(0, record.turns)
        assertEquals("Bob Green", record.player(b.account.accountId)!!.deck!!.name)
        // A match that ends is recorded once: a game deleted after its match ended leaves nothing more
        assertEquals(1L, w.number("SELECT count(*) FROM match_record"))
    }

    // Game history §2.3, §3.2: every pod member sees every match of the pod, their own and the others', without any deck,
    // and the pod's matches in play with the turn reached; someone outside the pod sees none
    @Test fun `pod members see the pod's matches without decks`() = HistoryWorld.run { w ->
        val a = w.account("PodAlice"); val b = w.account("PodBob"); val c = w.account("PodCarol"); val d = w.account("PodDave")
        val pod = UUID.randomUUID()
        w.execute("INSERT INTO pod (id, name, creator_id, settings, source_name, source_snapshot) VALUES (?, 'History pod', ?, '{}'::jsonb, 'Cube', '[]'::jsonb)",
            pod, UUID.fromString(a.account.accountId))
        for (member in listOf(a, b, c)) w.execute("INSERT INTO pod_member (pod_id, account_id) VALUES (?, ?)", pod, UUID.fromString(member.account.accountId))
        val finished = w.Match(a, b, "Pod Spark")
        val playing = w.Match(b, c, "Pod Flame")
        for (game in listOf(finished, playing)) w.execute("UPDATE game SET pod_id = ?, pod_name = 'History pod' WHERE id = ?", pod, game.id)
        finished.start(); finished.concede()
        playing.start()
        val seen = w.sql { MatchRecords.forPod(it, UUID.fromString(c.account.accountId), pod) }!!
        val record = seen.records.single()
        assertEquals("History pod", record.podName)
        assertEquals(MatchEnding.Conceded(b.account.accountId), record.ending)
        assertTrue(record.players.all { it.deck == null }, "no deck in the pod's view")
        assertEquals(listOf(playing.game.id), seen.playing.map { it.gameId })
        assertEquals(listOf(b.account.accountId, c.account.accountId), seen.playing.single().players.map { it.accountId })
        assertEquals(0, seen.playing.single().turn)
        // Also a player's own match, in the pod's view
        assertTrue(w.sql { MatchRecords.forPod(it, UUID.fromString(a.account.accountId), pod) }!!.records.single().players.all { it.deck == null })
        assertNull(w.sql { MatchRecords.forPod(it, UUID.fromString(d.account.accountId), pod) })
        // Their own Stats still show their own deck
        assertEquals("Alice Red", w.records(a).single().player(a.account.accountId)!!.deck!!.name)
        assertEquals("History pod", w.records(a).single().podName)
    }

    // Game history §1.5: history starts empty; a match that ended is recorded once, and the record outlives its game
    @Test fun `records outlive their game`() = HistoryWorld.run { w ->
        val a = w.account("KeepAlice"); val b = w.account("KeepBob")
        assertTrue(w.records(a).isEmpty())
        val m = w.Match(a, b)
        m.start(); m.concede()
        w.sql { GameRemoval.delete(it, listOf(m.id)) }
        assertEquals(0L, w.number("SELECT count(*) FROM game WHERE id = ?", m.id))
        assertEquals(1, w.records(a).size)
        assertEquals(1, w.sql { MatchRecords.forGame(it, UUID.fromString(a.account.accountId), m.id) }.size)
    }
}
