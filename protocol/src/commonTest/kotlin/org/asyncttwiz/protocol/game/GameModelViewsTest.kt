package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.EngineProbes
import arcanum.engine.Host
import arcanum.engine.input.Input
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Engine-responsiveness R2: a [GameModel] works the layers out once per state. Its [GameModel.view] and
 * [GameModel.visible] sets are kept until the engine state next changes through it ([GameModel.apply],
 * [GameModel.revert], an [EventBuilder] on it, an event's drop of older log entries) and never outlive that change; a
 * [GameModel.copy] keeps nothing. Counted with [EngineProbes.layersWorkedOut] (no timing); [GameModel.checkKept] checks every
 * kept read against working it out afresh.
 */
class GameModelViewsTest {
    /** How many times the layers were worked out in full by [block]. */
    private fun layers(block: () -> Unit): Int {
        var count = 0
        EngineProbes.layersWorkedOut = { count++ }
        try { block() } finally { EngineProbes.layersWorkedOut = null }
        return count
    }

    private fun <T> checking(block: () -> T): T {
        GameModel.checkKept = true
        try { return block() } finally { GameModel.checkKept = false }
    }

    /** A model a few events into a game, and the events. */
    private fun started(seed: Long = 1, events: Int = 8): Pair<GameModel, List<GameEvent>> {
        val games = EngineGames(seed)
        val model = games.model()
        return model to games.play(model, "root", events).map { it.first }
    }

    @Test
    fun theViewAndVisibleSetsAreWorkedOutOncePerState() {
        // A copy, which keeps nothing (the model played on keeps its last outcome's view)
        val model = started().first.copy()
        val alice = model.engine.players[0].id
        val bob = model.engine.players[1].id
        assertEquals(1, layers { model.view(); model.view(); model.visible(alice); model.visible(bob); model.visible(alice) })
        assertEquals(Engine.view(model.engine), model.view())
        assertEquals(Host.visible(model.engine, alice), model.visible(alice))
        assertEquals(Host.visible(model.engine, bob), model.visible(bob))
    }

    @Test
    fun applyAndRevertDropWhatWasKept() {
        val games = EngineGames(2)
        val model = games.model()
        var parent = "root"
        var engineEvents = 0
        repeat(40) {
            if (games.over(model)) return@repeat
            val alice = model.engine.players[0].id
            model.view(); model.visible(alice)
            val changes = model.changes
            val event = games.next(model, parent).also { parent = it.id }
            if (event.engine == null) {
                // A table-only event doesn't touch the engine state: what was kept of it stays
                assertEquals(changes, model.changes)
                assertEquals(0, layers { model.view() })
            } else {
                engineEvents++
                assertTrue(model.changes > changes, "an event's engine changes count")
                assertEquals(Engine.view(model.engine), model.view(), "the view after ${event.id}")
            }
            assertEquals(Host.visible(model.engine, alice), model.visible(alice))
            // Reverting drops the view of the state after it; applying again too
            if (event.engine != null) {
                model.view()
                model.revert(event)
                assertNull(model.keptView(), "reverting ${event.id} drops the view")
                assertEquals(Engine.view(model.engine), model.view())
                assertEquals(Host.visible(model.engine, alice), model.visible(alice))
                model.apply(event)
                assertNull(model.keptView(), "applying ${event.id} drops the view")
                assertEquals(Engine.view(model.engine), model.view())
            }
        }
        assertTrue(engineEvents > 10, "$engineEvents engine events")
    }

    @Test
    fun aCopyKeepsNothingAndChangesOnItsOwn() {
        val (model, events) = started()
        model.view()
        val copy = model.copy()
        assertNull(copy.keptView(), "a copy works its view out itself")
        assertEquals(1, layers { copy.view() })
        val games = EngineGames(1)
        var next = games.next(copy, events.last().id)
        while (next.engine == null) next = games.next(copy, next.id)
        assertNotNull(model.keptView(), "changing the copy leaves the original's view")
        assertEquals(Engine.view(model.engine), model.view())
        assertEquals(Engine.view(copy.engine), copy.view())
    }

    @Test
    fun anEventBuilderKeepsTheOutcomesViewAndDropsItWhenItTakesBack() {
        val (model, events) = started()
        val builder = EventBuilder(model, "x", events.last().id, model.engine.players[0].id.value)
        val mark = builder.mark()
        // The model's kept view goes to the engine; the outcome's view of the state after it is kept
        model.view()
        val input = model.engine.players.flatMap { Engine.options(model.engine, it.id, org.asyncttwiz.protocol.game.EnginePreferences.of(model.engine)) }.first()
        builder.perform(input)
        model.keptView()?.let { assertEquals(Engine.view(model.engine), it, "the outcome's view is the state's") }
        builder.takeBack(mark)
        assertNull(model.keptView(), "taking back drops the view")
        assertEquals(Engine.view(model.engine), model.view())
        builder.perform(input)
        // End turn engine speed: abandoning puts back what was kept of the state the builder began on (it is that state
        // again), never the view of the state it made
        builder.abandon()
        model.keptView()?.let { assertEquals(Engine.view(model.engine), it, "abandoning keeps only the view of the state it began on") }
        assertEquals(Engine.view(model.engine), model.view())
        val line = EventBuilder(model, "y", events.last().id, model.engine.players[0].id.value)
        model.view()
        val before = model.changes
        line.startLine(model.table.line.number + 1)
        if (line.engineSteps.isNotEmpty()) {
            assertTrue(model.changes > before)
            assertNull(model.keptView(), "a new line's reorder drops the view")
        }
        line.abandon()
        assertEquals(Engine.view(model.engine), model.view())
    }

    // End turn engine speed: a pass check is kept with the state; a builder taken back puts back what was kept of the
    // state it began on, its first input's outcome included, so the same input made again there is applied without the
    // engine (the outcome equal to the engine's, the state after it the same); a change drops all of it
    @Test
    fun passChecksAndAProbesOutcomeAreKeptForTheStateTheyAreOf() {
        val games = EngineGames(4)
        val model = games.model()
        var parent = "root"
        var probed = 0
        repeat(120) {
            if (games.over(model)) return@repeat
            val holder = model.engine.priority.holder
            if (holder != null && model.engine.pending == null) {
                assertEquals(Host.passCheck(model.engine, holder, org.asyncttwiz.protocol.game.EnginePreferences.of(model.engine)), model.passCheck(holder))
                assertEquals(0, layers { model.passCheck(holder) }, "kept")
                val input = Input.PassPriority(holder)
                val probe = EventBuilder(model, "probe$it", parent, holder.value)
                val outcome = try { probe.perform(input) } catch (_: RuntimeException) { null }
                val after = model.copy()
                probe.abandon()
                assertEquals(0, layers { model.passCheck(holder) }, "kept again once the probe is taken back")
                if (outcome != null) {
                    probed++
                    val again = EventBuilder(model, "again$it", parent, holder.value)
                    assertEquals(0, layers { assertEquals(outcome, again.perform(input)) }, "the probe's outcome applied again")
                    assertTrue(after.sameAs(model), "the same state as the probe made")
                    again.abandon()
                }
            }
            parent = games.next(model, parent).id
        }
        assertTrue(probed > 5, "$probed probes")
    }

    @Test
    fun walkingBackAndForthAcrossTurnsNeverServesAStaleView() = checking {
        val games = EngineGames(3)
        val model = games.model()
        val played = games.play(model, "root", 300)
        val players = model.engine.players.map { it.id }
        // Back event by event (reverting a turn's start puts the dropped log entries back), reading at each state
        for (i in played.indices.reversed()) {
            model.revert(played[i].first)
            if (model.stale) break
            model.view(); players.forEach { model.visible(it) }
        }
        // And forwards again on a fresh model, the same
        val again = games.model()
        for ((event, _) in played) {
            again.view(); players.forEach { again.visible(it) }
            again.apply(event)
        }
        again.view(); players.forEach { again.visible(it) }
    }

    // The information rule given the kept views says what it says without them (I-D56 §5)
    @Test
    fun theInformationRuleWithKeptViewsSaysTheSame() {
        val games = EngineGames(4)
        val model = games.model()
        var parent = "root"
        var checked = 0
        while (checked < 60 && !games.over(model)) {
            val before = InformationRule.Before.of(model.also { it.view() })
            val event = games.next(model, parent).also { parent = it.id }
            val changes = event.engine?.changes ?: continue
            for (p in model.engine.players.map { it.id }) assertEquals(InformationRule.revealing(before.state, changes, p),
                InformationRule.revealing(before, changes, p, model.view()), "event ${event.id}")
            checked++
        }
    }
}
