package org.asyncttwiz.android

import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.android.ReorderingConnection.Arrangement
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Hardening 2026-11-17 C: the open game at the table gets the server's messages out of order, twice or late
 * ([ReorderingConnection]) and its state still moves strictly by its revisions ([RevisionOrder]): a head or list read
 * ahead waits for them, a revision ahead is held until the ones before it come, a repeat is ignored. The known cases
 * (a concession's head first, e2e GameEndDialogTest; a head past the revisions, e2e HeadCrossesRevisionsTest; coming
 * back, e2e RewindOnReturnTest) and the general ones, on Alice's device.
 */
class ServerMessageOrderTest {
    private val f = EngineGameFixture()
    private val lobby = f.game.copy(preparationRound = f.base.preparationRound, lifecycle = GameLifecycle.LOBBY, playId = null, revision = "1")
    private fun active(revision: String) = lobby.copy(lifecycle = GameLifecycle.ACTIVE, playId = f.base.id, revision = revision)
    private val heads = mapOf(f.a to f.start.id, f.b to f.start.id)

    /** Bob keeps: the play's first event after the start. */
    private val keep: GameEvent = EventBuilder(f.model.copy(), DeviceRandom.uuid(), f.start.id, f.b).run {
        perform(Input.SetupAnswer(PlayerId(f.b), Answer.Option("Keep"))); answerRandoms(); build()
    }
    private val kept = GameRevision(lobby.id, "2", "3", DeviceRandom.uuid(), f.b, GameScope(lobby.id, playId = f.base.id),
        active("3"), engineEvent = f.codec.encodeEvent(keep), engine = EngineUpdate(localHeads = mapOf(f.a to f.start.id, f.b to keep.id)))
    /** Alice's device reports where it is: a revision without events. */
    private val seen = GameRevision(lobby.id, "3", "4", DeviceRandom.uuid(), f.a, GameScope(lobby.id, playId = f.base.id),
        active("4"), engine = EngineUpdate(localHeads = mapOf(f.a to keep.id, f.b to keep.id)))
    /** The engine head at the start (revision 2), as the open game's subscription gets it. */
    private val started = GameHead(lobby.id, "2", active("2"), play = f.base, engine = f.snapshot())
    /** The engine head after Bob's keep, at [revision]. */
    private fun keptHead(revision: String) = GameHead(lobby.id, revision, active(revision), play = f.base,
        engine = EngineSnapshot(f.codec.encodeDefinitions(), f.snapshot().rootId, keep.id,
            f.codec.encodeModel(f.model.copy().also { it.apply(keep) }), localHeads = mapOf(f.a to f.start.id, f.b to keep.id),
            baseId = f.start.id, base = f.codec.encodeModel(f.model), events = listOf(f.codec.encodeEvent(keep))))
    /** The head every heartbeat brings (switch-review #11): the summary only. */
    private fun summaryHead(summary: GameSummary) = GameHead(lobby.id, summary.revision, summary)

    private class Session(me: String) : AutoCloseable {
        val posts = ArrayDeque<() -> Unit>()
        fun drain() { while (posts.isNotEmpty()) posts.removeFirst()() }
        val wire = ReorderingConnection(::drain)
        val session = GameSession(wire, { "A".repeat(43) }, { posts.add(it) }, {}, {}, { _, _ -> }, {}, accountId = { me })
        override fun close() = session.close()
    }

    /** Alice's device with the game open at the table at the start (revision 2). */
    private fun opened(): Pair<Session, EngineGame> {
        val s = Session(f.a)
        s.session.watch(listOf(active("2")))
        s.wire.connect(s.session, lobby.id)
        s.wire.message(started)
        return s to assertNotNull(s.session.engineGame(lobby.id))
    }

    private fun device(game: EngineGame): GameDevice = game.read { it }

    // SYS-015, HIS-006 (e2e HeadCrossesRevisionsTest): a heartbeat's head read before the revisions up to it were published
    // reaches the table first. The table waits for them (they are asked for) instead of taking the head's word; they play
    // live as they come, and the asked-for head finds nothing to load
    @Test fun aHeadBeforeItsRevisionsWaitsForThem() { val (s, game) = opened(); s.use {
        val before = device(game)
        s.wire.serve(kept, seen, summaryHead(active("4")))
        s.wire.deliver(Arrangement.Holding { it is GameRevision })
        assertEquals("2", s.session.summary(active("2")).revision, "the head ahead isn't taken as the table's state")
        assertEquals(listOf("0", "2"), s.wire.subscribes().map { it.revision }, "the revisions after the table's asked for")
        assertFalse(s.session.caughtUp(lobby.id), "what the server had isn't in yet")
        s.wire.deliver()
        s.wire.message(keptHead("4"))
        assertSame(game, s.session.engineGame(lobby.id))
        assertSame(before, device(game), "never loaded again")
        assertTrue(game.read { d -> d.session.events.any { it.id == keep.id } }, "Bob's keep came by its revision")
        assertEquals("4", game.revision)
        assertEquals("4", s.session.summary(active("2")).revision)
        assertTrue(s.session.caughtUp(lobby.id))
    } }

    // SYS-015 (a live publication overtaking a resubscription's replay): the open play's revision that came before the one it
    // rests on is held, the missing one asked for; once it comes both apply in order, with no load from a head
    @Test fun aRevisionBeforeTheOneItRestsOnIsHeld() { val (s, game) = opened(); s.use {
        val before = device(game)
        s.wire.serve(kept, seen)
        s.wire.deliver(Arrangement.Reversed)
        assertEquals(listOf("0", "2"), s.wire.subscribes().map { it.revision }, "the missing revision asked for")
        assertTrue(game.read { d -> d.session.events.any { it.id == keep.id } }, "Bob's keep applied once it came")
        assertEquals("4", game.revision, "then the one held after it")
        assertFalse(device(game).needsReload)
        s.wire.serve(kept, seen, keptHead("4"))
        s.wire.deliver()
        assertSame(before, device(game), "the replay and its head change nothing")
    } }

    // SYS-014, SYS-015: each revision twice (a replay crossing the publications) is applied once
    @Test fun aRepeatedRevisionIsAppliedOnce() { val (s, game) = opened(); s.use {
        val before = device(game)
        s.wire.serve(kept, seen)
        s.wire.deliver(Arrangement.Twice)
        assertEquals(1, game.read { d -> d.session.events.count { it.id == keep.id } })
        assertEquals("4", game.revision)
        assertSame(before, device(game))
        assertEquals(1, s.wire.subscribes().size, "nothing to ask for")
    } }

    // SYS-014, SYS-019: after a reconnect the device resubscribes from the revision it received; a revision published live
    // before the replay is held until the replay brings the one before it
    @Test fun aRevisionAfterAReconnectWaitsForTheReplay() { val (s, game) = opened(); s.use {
        val before = device(game)
        s.wire.drop()
        s.wire.connect(s.session, lobby.id)
        assertEquals("2", s.wire.subscribes().last().revision, "from the revision it received")
        s.wire.serve(seen, kept, seen, keptHead("4"))
        s.wire.deliver()
        assertTrue(game.read { d -> d.session.events.any { it.id == keep.id } })
        assertEquals("4", game.revision)
        assertSame(before, device(game), "followed, not loaded again")
        assertEquals(2, s.wire.subscribes().size, "the stream's own subscription was enough")
    } }

    // HIS-006 (e2e full run 2026-11-13, PassesAndUndoTest; merge gate 2026-11-24, CoreLoopTest): the asked-for engine head beat
    // the revisions; ahead of what the device could follow, the game doesn't load from it but asks for the revisions after its
    // own again: they come and play, the live table stays live
    @Test fun anEngineHeadBeforeItsRevisionsIsAskedPastAndStaysLive() { val (s, game) = opened(); s.use {
        val before = device(game)
        s.wire.serve(kept, seen, keptHead("4"))
        s.wire.deliver(Arrangement.HeadsFirst)
        assertEquals("4", game.revision)
        assertEquals(1, game.read { d -> d.session.events.count { it.id == keep.id } })
        assertNotEquals(GameDevice.Status.CatchUp, device(game).status, "no catch-up pause on a live table")
        assertSame(before, device(game), "followed by its revisions, not loaded again")
        assertEquals(listOf("0", "2"), s.wire.subscribes().map { it.revision }, "the revisions after the table's asked for again")
    } }

    // SYS-135, SYS-138, HIS-006 (merge gate 2026-11-24, CoreLoopTest.theStepArrowGoesToCombatAndDeclares: her own To combat
    // refused as "That wasn't taken" at the start of her own turn): Bob's keep is committed after the open game's subscription
    // replayed its revisions, so the head read after the replay reaches Alice's table before the keep's own publication. The
    // table isn't loaded again from that head (loading took back the board she had just been shown, so her press on it was
    // judged stale, and an open cast was dropped with "The table changed: cast it again."): the revisions after its own are
    // asked for again, and the keep comes by its publication
    @Test fun aHeadReadBeforeItsRevisionsPublicationTakesNothingBack() { val (s, game) = opened(); s.use {
        val before = device(game)
        s.wire.message(keptHead("3"))
        assertSame(before, device(game), "not loaded again from the head")
        assertEquals(0L, game.takeBacks, "no board taken back: ${game.lastTakeBack}")
        assertEquals(listOf("0", "2"), s.wire.subscribes().map { it.revision }, "the revisions after the table's asked for again")
        // The publication, then the replay's repeat of it and the head asked for
        s.wire.serve(kept, kept, keptHead("3"))
        s.wire.deliver()
        assertSame(before, device(game), "still the same device")
        assertEquals("3", game.revision)
        assertEquals(1, game.read { d -> d.session.events.count { it.id == keep.id } }, "Bob's keep by its revision")
        assertEquals(0L, game.takeBacks, "nothing taken back: ${game.lastTakeBack}")
        assertEquals(2, s.wire.subscribes().size, "nothing more asked for")
    } }

    // HIS-006, SYS-015: a head past the revisions whose revisions, asked for again, don't come (more than a replay carries):
    // the game loads from the next such head
    @Test fun aHeadStillAheadOnceItsRevisionsWereAskedForLoads() { val (s, game) = opened(); s.use {
        val before = device(game)
        s.wire.message(keptHead("3"))
        assertSame(before, device(game), "asked past first")
        s.wire.message(keptHead("3"))
        assertNotSame(before, device(game), "the revisions asked for didn't come: loaded from the head")
        assertEquals("3", game.revision)
        assertTrue(game.read { d -> d.session.events.any { it.id == keep.id } })
    } }

    /** Bob concedes at the start: the revision that ends the play (the lobby, the result owed by both). */
    private fun conceded(): GameRevision {
        val concede = EventBuilder(f.codec.decodeModel(f.snapshot().state), DeviceRandom.uuid(), f.start.id, f.b)
            .run { perform(Input.Concede(PlayerId(f.b))); build() }
        val ended = lobby.copy(revision = "3", resultNotice = ResultNotice(f.a, MatchEnding.Conceded(f.b), unseen = listOf(f.a, f.b)))
        return GameRevision(lobby.id, "2", "3", DeviceRandom.uuid(), f.b, GameScope(lobby.id, playId = f.base.id), ended,
            engineEvent = f.codec.encodeEvent(concede), engine = EngineUpdate(localHeads = mapOf(f.a to f.start.id, f.b to concede.id)))
    }

    // TRN-065, LOB-046 (owner, prod 0.2.0; e2e GameEndDialogTest): a head saying the play ended, read before the revision that
    // ended it, doesn't take the player to the lobby: the table waits for that revision, which plays the end there and holds it
    @Test fun aHeadAheadOfTheConcessionLeavesTheTableToItsRevision() { val (s, _) = opened(); s.use {
        val ending = conceded()
        s.wire.serve(ending, summaryHead(ending.summary))
        s.wire.deliver(Arrangement.Holding { it is GameRevision })
        assertNotNull(s.session.engineGame(lobby.id), "still at the table")
        assertEquals(GameLifecycle.ACTIVE, s.session.summary(active("2")).lifecycle)
        assertEquals("2", s.wire.subscribes().last().revision, "the revisions after the table's asked for")
        s.wire.deliver()
        assertTrue(s.session.tableHeldAtEnd(lobby.id), "the end held at the table until both OKs")
        assertNotNull(s.session.engineGame(lobby.id))
    } }

    // TRN-065, LOB-046 (e2e GameEndDialogTest's list read): the same for a list read
    @Test fun aListReadAheadOfTheConcessionLeavesTheTableToItsRevision() { val (s, _) = opened(); s.use {
        val ending = conceded()
        s.session.watch(listOf(ending.summary))
        assertNotNull(s.session.engineGame(lobby.id), "still at the table")
        assertEquals(GameLifecycle.ACTIVE, s.session.summary(active("2")).lifecycle)
        s.wire.message(ending)
        assertTrue(s.session.tableHeldAtEnd(lobby.id))
    } }

    // HIS-035, HIS-205 (owner, prod 0.2.1: the other player's undo while away played at once on return): back in the app on
    // the open socket, a heartbeat's head past the revisions keeps the table away until the revisions it asked for are in
    @Test fun comingBackWaitsForTheRevisionsAHeadAheadAskedFor() { val (s, game) = opened(); s.use {
        s.session.background()
        assertTrue(game.awayPaused)
        s.session.foreground()
        val ping = s.wire.frames.filterIsInstance<GamePing>().last()
        s.wire.serve(kept, seen, summaryHead(active("4")))
        s.wire.deliver(Arrangement.Holding { it is GameRevision })
        s.wire.message(GamePong(ping.correlationId))
        s.session.engineTick(lobby.id)
        assertTrue(game.awayPaused, "still away: the revisions the head said were there aren't in")
        s.wire.deliver()
        s.wire.message(keptHead("4"))
        s.session.engineTick(lobby.id)
        assertFalse(game.awayPaused, "back, with everything")
    } }
}
