package org.asyncttwiz.automatum

import arcanum.engine.Engine
import arcanum.engine.TraceEntry
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.PlayZone
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** I-D43: what Automatum logs, and a report's bundle rebuilding the same engine situation offline. */
class ReproBundleTest {
    private val table = AtwTable("repro")
    private val alice get() = table.alice
    private val bob get() = table.bob
    private val random = Random(5)
    private fun seed() = org.asyncttwiz.protocol.RandomV1.encodeSeed(random.nextBytes(32))

    @BeforeTest fun clear() = AutomatumLog.clear()
    @AfterTest fun clearAfter() = AutomatumLog.clear()

    private fun log(rt: RoundTrip) = EngineLog.from(rt.tags, rt.state, table.definitions)

    @Test
    fun aReportedBundleReplaysToTheSameStatesAndOptions() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.BATTLEFIELD, "Mountain")
        val (spark) = table.put(alice, PlayZone.HAND, "Storm Spark")
        table.put(bob, PlayZone.HAND, "Storm Spark")
        val rt = RoundTrip(table.state(), table.definitions)
        // Alice casts at Bob (a private flow), Bob may respond and lets it resolve (a shared flow)
        val plays = HandPlays.of(rt.state, table.definitions, alice, log(rt))
        var cast = CastFlow.start(rt.state, table.definitions, alice, log(rt), plays.of(spark).first(), CastFlow.Seeds(::seed))
        cast = cast.answer(Answer.Targets(listOf(TargetRef.Player(PlayerId(bob)))))
        cast = cast.answer(Answer.ManaPayment((cast.pending as Decision.ManaPayment).suggestion))
        val sent = cast.sent().also { rt.apply(it, alice) }
        val pass = SharedFlow(rt.state, table.definitions, log(rt), ::seed).pass(bob).also { rt.apply(it, bob) }
        assertEquals("17", rt.state.board.players.single { it.accountId == bob }.life)

        // The log has the engine calls with their shape, and the batches planned from them
        val entries = AutomatumLog.entries(rt.state.gameId)
        val engine = entries.filterIsInstance<AutomatumEntry.Engine>()
        assertTrue(engine.any { it.call == sent.logNumber && it.trace is TraceEntry.Performed })
        assertTrue(entries.filterIsInstance<AutomatumEntry.Batch>().any { it.call == pass.logNumber && it.actions > 0 })
        assertTrue(entries.all { it.line().isNotBlank() })

        // The latest board, for the report's engine state
        rt.call()
        val bundle = assertNotNull(AutomatumLog.bundle(rt.state.gameId))
        assertNull(bundle.engineError)
        val loaded = ReproBundles.load(ReproBundles.reportText(bundle))
        assertEquals(bundle, loaded, "the bundle comes back the same through the report's text")

        // Each recorded call replays from its start state to the state the device had
        for (call in listOf(sent, pass)) {
            val recorded = assertNotNull(loaded.calls.singleOrNull { it.call == call.logNumber }, "call ${call.logNumber} kept")
            val replay = ReproBundles.replay(recorded)
            assertEquals(call.working, replay.state, "call ${call.logNumber} replays to the same state")
            for (player in call.working.players) assertEquals(Engine.options(call.working, player.id), Engine.options(replay.state, player.id))
        }

        // The latest board rebuilds to the same engine state, with the same options
        val rebuilt = ReproBundles.rebuild(loaded)
        assertEquals(loaded.engine, rebuilt)
        assertEquals(EngineState.from(rt.state, table.definitions, null, log(rt)), rebuilt)
        for (player in rebuilt.players) assertEquals(Engine.options(rebuilt, player.id), Engine.options(loaded.engine!!, player.id))
    }

    @Test
    fun theLogStaysBounded() {
        val rt = RoundTrip(table.state(), table.definitions)
        repeat(AutomatumLog.ENTRIES + 50) { AutomatumLog.refusal(rt.state.gameId, "test", "refusal $it") }
        val entries = AutomatumLog.entries(rt.state.gameId)
        assertEquals(AutomatumLog.ENTRIES, entries.size)
        assertEquals("refusal ${AutomatumLog.ENTRIES + 49}", (entries.last() as AutomatumEntry.Refusal).reason)
        repeat(AutomatumLog.CALLS + 2) { runCatching { rt.call().perform(arcanum.engine.input.Input.PassPriority(PlayerId(alice))) } }
        assertEquals(AutomatumLog.CALLS, AutomatumLog.bundle(rt.state.gameId)!!.calls.size)
    }
}
