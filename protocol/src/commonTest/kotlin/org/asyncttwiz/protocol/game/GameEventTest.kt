package org.asyncttwiz.protocol.game

import arcanum.engine.change.Change
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** I-D54 phase 2: events (engine part + table part) apply and revert exactly, and survive their stored format. */
class GameEventTest {
    // SYS-027: every event stores how to apply and revert it, checked against the current values
    @Test
    fun everyEventAppliesAndRevertsExactly() {
        for (seed in listOf(1L, 2L)) {
            val games = EngineGames(seed)
            val model = games.model()
            var before = model.copy()
            var parent = "root"
            var events = 0
            var engineEvents = 0; var tableEvents = 0
            while (events < 300 && !games.over(model)) {
                val event = games.next(model, parent)
                if (event.engine != null) engineEvents++
                if (event.table.isNotEmpty()) tableEvents++
                // Reverting gives back the state it was made on, applying again the state it made
                val reverted = model.copy().also { it.revert(event) }
                assertFalse(reverted.stale, "seed $seed ${event.id}: reverting one event never needs a checkpoint")
                assertTrue(reverted.sameAs(before), "seed $seed ${event.id}: revert didn't restore the state")
                val again = before.copy().also { it.apply(event) }
                assertTrue(again.sameAs(model), "seed $seed ${event.id}: apply didn't make the same state")
                before = model.copy()
                parent = event.id
                events++
            }
            assertTrue(engineEvents > 50 && tableEvents > 20, "seed $seed: $engineEvents engine and $tableEvents table events")
        }
    }

    @Test
    fun revertingBackAcrossTurnsStaysExactOrSaysItCant() {
        val games = EngineGames(3)
        val model = games.model()
        val played = games.play(model, "root", 400)
        assertTrue(played.last().second.engine.turn.number > GameModel.KEPT_DROPS + 2, "the game spans enough turns")
        // Walk back event by event: every state equals the one recorded forwards, until older turns' log is needed
        var reachedStale = false
        for (i in played.indices.reversed()) {
            model.revert(played[i].first)
            if (model.stale) { reachedStale = true; break }
            val expected = if (i == 0) games.model() else played[i - 1].second
            assertTrue(model.sameAs(expected), "back to before ${played[i].first.id}")
        }
        assertTrue(reachedStale, "more than ${GameModel.KEPT_DROPS} turns back the model needs a checkpoint")
    }

    @Test
    fun aChangeThatDoesNotFitChangesNothing() {
        val games = EngineGames(4)
        val model = games.model()
        var parent = games.play(model, "root", 40).last().first.id
        while (model.engine.pending != null || model.engine.priority.holder == null) parent = games.next(model, parent).id
        // Each change checks only its own before-values: an event of another game moving cards doesn't fit here
        val engineEvent = games.play(games.model(), "root", 40).first { it.first.engine?.changes.orEmpty().any { c -> c is Change.ZoneChange } }.first
        val snapshot = model.copy()
        // A pass that fits, then changes made for another state: the pass's changes are taken back too
        val pass = arcanum.engine.input.Input.PassPriority(model.engine.priority.holder!!)
        val fits = EngineStep(pass, arcanum.engine.Engine.perform(model.engine, pass).changes)
        val mixed = GameEvent("x", parent, "alice", EnginePart(model.engine.timestampCounter, listOf(fits) + engineEvent.engine!!.steps))
        assertFailsWith<RuntimeException> { model.apply(mixed) }
        assertTrue(model.sameAs(snapshot))
        // An event made on another timestamp counter is refused before anything is applied
        assertFailsWith<IllegalStateException> { model.apply(mixed.copy(engine = mixed.engine!!.copy(timestampBefore = -1))) }
        assertTrue(model.sameAs(snapshot))
        // A table part that doesn't fit takes the engine part back with it
        val wrongTable = GameEvent("x", parent, "alice", EnginePart(model.engine.timestampCounter, listOf(fits)),
            listOf(TableChange.EarlyAnswered("nobody", EarlyAnswer(PlayerId("alice"), "nobody", arcanum.engine.decision.Answer.YesNo(true)), null)))
        assertFailsWith<IllegalStateException> { model.apply(wrongTable) }
        assertTrue(model.sameAs(snapshot))
    }

    @Test
    fun tableChangesCheckTheirBeforeValues() {
        val alice = PlayerId("alice")
        val obj = ObjectId.Card(arcanum.engine.state.CardId("c1"), 2)
        var table = TableState()
        table = TableChange.LineMoved(Line(), Line(0, 2)).apply(table)
        table = TableChange.AttackDraft(null, listOf(arcanum.engine.input.Attack(obj, arcanum.engine.state.TargetRef.Player(alice)))).apply(table)
        assertEquals(Line(0, 2), table.line)
        assertFailsWith<IllegalStateException> { TableChange.AttackDraft(null, emptyList()).apply(table) }
        assertFailsWith<IllegalStateException> { TableChange.LineMoved(Line(), Line(1, 0)).apply(table) }
        table = TableChange.LineMoved(Line(0, 2), Line()).apply(table)
        assertEquals(TableState(attackDraft = table.attackDraft), table)
    }

    @Test
    fun eventsAndStatesSurviveTheirStoredForm() {
        val games = EngineGames(1)
        val model = games.model()
        val codec = games.codec
        val played = games.play(model, "root", 250)
        var compact = 0; var inline = 0
        for ((event, state) in played) {
            val text = codec.encodeEvent(event)
            val back = codec.decodeEvent(text)
            assertEquals(event, back, "${event.id} through JSON")
            assertEquals(text, codec.encodeEvent(back), "${event.id}: the same JSON again")
            compact += text.length
            inline += EngineJson.json.encodeToString(GameEvent.serializer(), event).length
            if (event.id.removePrefix("e").toInt() % 50 == 0) {
                val stored = codec.encodeModel(state)
                assertTrue(codec.decodeModel(stored).sameAs(state), "the state at ${event.id} through JSON")
            }
        }
        assertTrue(compact * 2 < inline, "definitions by reference: $compact vs $inline characters")
        // The definitions stored once per game come back under the same keys
        val definitions = EngineJson.decodeDefinitions(codec.encodeDefinitions())
        assertEquals(codec.definitions.all, definitions.all)
        val other = EngineJson(definitions)
        assertEquals(played.last().first, other.decodeEvent(codec.encodeEvent(played.last().first)))
        // A reference to a definition the game doesn't have is refused, not guessed
        val bare = EngineJson(GameDefinitions(emptyMap()))
        val withCard = played.first { (e, _) -> e.engine?.changes.orEmpty().any { it is Change.ZoneChange } }.first
        assertFailsWith<IllegalArgumentException> { bare.decodeEvent(codec.encodeEvent(withCard)) }
    }

    @Test
    fun anAbandonedEventLeavesNothing() {
        val games = EngineGames(6)
        val model = games.model()
        var parent = games.play(model, "root", 30).last().first.id
        while (model.engine.pending != null || model.engine.priority.holder == null) parent = games.next(model, parent).id
        val snapshot = model.copy()
        val builder = EventBuilder(model, "x", parent, "alice")
        builder.perform(arcanum.engine.input.Input.PassPriority(assertNotNull(model.engine.priority.holder)))
        builder.table(TableChange.Suggestion(null, ResultSuggestion(PlayerId("bob"), arcanum.engine.setup.GameResult.Draw)))
        builder.abandon()
        assertTrue(model.sameAs(snapshot))
    }
}
