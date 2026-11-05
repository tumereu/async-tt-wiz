package org.asyncttwiz.android

import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-04: what each player is shown of assumptions (only the assumer reads them as assumed), how
 * playback paces events (§27: quiet passes go by at once) and pausing it (the next point where Full control stops).
 */
class EngineAsyncViewTest {
    private val f = EngineGameFixture()
    private val names = mapOf(f.a to "Alice", f.b to "Bob")

    /** Alice kept while Bob was away (his keep and passes assumed up to her main phase), and Bob's device catching up. */
    private fun aliceAndBob(): Pair<EngineGame, EngineGame> {
        val alice = EngineGame(f.game.id, f.a, f.base, f.snapshot())
        alice.tick(false, 1_000)
        alice.keep(emptyList())
        alice.tick(false, 2_000)
        val events: List<GameEvent> = alice.device.session.pending
        val snapshot = EngineSnapshot(f.codec.encodeDefinitions(), f.snapshot().rootId, events.last().id,
            f.codec.encodeModel(alice.device.model), localHeads = mapOf(f.a to events.last().id, f.b to f.start.id),
            baseId = f.start.id, base = f.snapshot().state, events = events.map(f.codec::encodeEvent))
        return alice to EngineGame(f.game.id, f.b, f.base, snapshot)
    }

    // HIS-091 (owner playtest 2026-11-04 #1, #2): only the assumer reads an assumed answer as assumed; the player it was
    // assumed about reads it as their own ("You kept"), never "(assumed)"; §29.2: an assumed pass reads as neither, only
    // as what it led to
    @Test fun onlyTheAssumerReadsAnAssumedAnswerAsAssumed() {
        val (alice, _) = aliceAndBob()
        val all = alice.device.session.pending.filter { it.engine?.steps?.any { s -> s.assumedBy == PlayerId(f.a) } == true }
        val (passes, assumed) = all.partition(EngineEventText::pass)
        assertTrue(assumed.isNotEmpty() && passes.isNotEmpty())
        val state = alice.device.model.engine
        for (event in passes) for (viewer in listOf(f.a, f.b)) {
            val text = EngineEventText.of(event, state, viewer, names)
            assertTrue("passed" !in text && "assumed" !in text, text)
        }
        for (event in assumed) {
            val hers = EngineEventText.of(event, state, f.a, names)
            val his = EngineEventText.of(event, state, f.b, names)
            assertTrue(hers.startsWith("Bob ") && "(assumed)" in hers, hers)
            assertTrue(his.startsWith("You ") && "assumed" !in his, his)
        }
    }

    // HIS-086 (owner 2026-11-04): an undo adds no event; a rewind's own line start that older games stored is shown nowhere
    // (strip, timeline, event chip)
    @Test fun anOldRewindLineStartIsShownNowhere() {
        val (alice, _) = aliceAndBob()
        val head = alice.device.session.tip
        val lineStart = org.asyncttwiz.protocol.game.EventBuilder(alice.device.model.copy(), "line1", head, f.a).also { it.startLine(1) }.build()
        assertTrue(EngineEventText.hidden(lineStart))
        // (§29.2: of the other events only passes that led to nothing are hidden)
        assertTrue(alice.device.session.pending.none { EngineEventText.hidden(it) && !EngineEventText.pass(it) })
    }

    // HIS-104 (§27, §29.3): playback lets passes that changed nothing visible go by at once (under Full control they keep
    // the delay); a land play, a cast, a new turn keep the playback delay after them
    @Test fun quietPassesGoByAtOnce() {
        val (alice, _) = aliceAndBob()
        val events = alice.device.session.pending
        val quiet = events.filter(EnginePlaybackPace::quiet)
        assertTrue(quiet.isNotEmpty(), "an upkeep pass changes nothing visible")
        assertTrue(quiet.all { e -> e.engine!!.steps.all { it.input == null || it.input is Input.PassPriority } })
        for (event in quiet) assertEquals(0L, playbackWait(event, 330, ResponseMode.AutoSkip))
        for (event in quiet) assertEquals(330L, playbackWait(event, 330, ResponseMode.FullControl))
        // The keeps (an answer) and the pass into the draw step (a card drawn) aren't quiet
        val keep = events.first { e -> e.engine?.steps?.any { it.input is Input.Answer || it.input is Input.SetupAnswer } == true }
        assertFalse(EnginePlaybackPace.quiet(keep))
        assertEquals(330L, playbackWait(keep, 330, ResponseMode.Normal))
        val draw = events.firstOrNull { e -> e.engine?.changes.orEmpty().any { it is arcanum.engine.change.Change.ZoneChange } &&
            e.engine?.steps?.all { it.input == null || it.input is Input.PassPriority } == true }
        if (draw != null) assertFalse(EnginePlaybackPace.quiet(draw))
    }

    // HIS-021 (§29.3): the playback delay is a third of a second at Card move speed 1×, scaled by it
    @Test fun thePlaybackDelayFollowsTheCardMoveSpeed() {
        assertEquals(330L, playbackDelay(1f))
        assertEquals(110L, playbackDelay(3f))
        assertEquals(1_320L, playbackDelay(.25f))
    }

    // HIS-095, HIS-103 (§27): pausing playback goes on to the next point where Full control would stop (here Alice's
    // assumed pass in her upkeep, which Bob's Auto-skip goes past), stops there for good until he plays on
    @Test fun pausingStopsAtTheNextFullControlPoint() {
        val (_, bob) = aliceAndBob()
        bob.startPlayback()
        var guard = 0
        while (bob.device.status is GameDevice.Status.Playing && guard++ < 100) bob.step()
        // The assumed keep always stops
        bob.act((bob.device.status as GameDevice.Status.Stopped).assumed)
        bob.pause(true)
        assertTrue(bob.shown!!.pausing)
        while (bob.device.status is GameDevice.Status.Playing && guard++ < 100) bob.step()
        val stop = assertIs<GameDevice.Status.Stopped>(bob.device.status)
        assertTrue(stop.paused, "Auto-skip alone wouldn't stop here")
        assertIs<Input.PassPriority>(stop.assumed)
        // Playing on answers the same as assumed and ends the pause
        assertEquals(GameDevice.Acted.Confirmed, bob.act(stop.assumed))
        assertFalse(bob.shown!!.pausing)
    }
}
