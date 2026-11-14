package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * I-D54 phase 2, the owner's undo rule on events: an event can be undone when no later event changed or relied on what
 * it changed; an engine input relied on the whole engine state; refusing is the safe side.
 */
class UndoRuleTest {
    private val games = EngineGames(21)
    private val model = games.model()
    private var parent = "root"
    private var n = 0

    /** Plays on until the active player may play a land with nothing pending and two permanents out. */
    private fun untilALandCanBePlayed(): Input.PlayLand {
        while (true) {
            val s = model.engine
            if (s.pending == null && s.priority.holder == s.turn.active && s.battlefield.size >= 2)
                Engine.options(s, s.turn.active, org.asyncttwiz.protocol.game.EnginePreferences.of(s)).filterIsInstance<Input.PlayLand>().firstOrNull()?.let { return it }
            parent = games.next(model, parent).id
        }
    }

    private fun event(build: EventBuilder.() -> Unit): GameEvent =
        EventBuilder(model, "u${++n}", parent, "alice").apply(build).build().also { parent = it.id }

    /** A table-only change about [obj]: it staged as an attacker of Bob. */
    private fun stage(obj: ObjectId) = TableChange.AttackDraft(model.table.attackDraft,
        listOf(arcanum.engine.input.Attack(obj, arcanum.engine.state.TargetRef.Player(games.bob))))

    @Test
    fun aCardStagedLaterBlocksUndoingTheEventThatPutItThere() {
        val land = untilALandCanBePlayed()
        val play = event { perform(land) }
        val entered = model.engine.battlefield.last()
        val placing = event { table(stage(entered)) }
        assertSame(placing, UndoRule.blocking(play, listOf(placing)))
        val snapshot = model.copy()
        assertFailsWith<IllegalStateException> { UndoRule.undo(model, play, listOf(placing)) }
        assertTrue(model.sameAs(snapshot), "a refused undo changes nothing")
    }

    // SYS-058: the other player's table-only events on other things stay when an undo goes past them
    @Test
    fun tableEventsOnOtherThingsDoNotBlockAnEngineEvent() {
        val land = untilALandCanBePlayed()
        val before = model.copy()
        val play = event { perform(land) }
        val touched = Concerns.of(play).writes
        val elsewhere = model.engine.battlefield.filterIsInstance<ObjectId.Card>().first { StateKey.Presence(it.card) !in touched }
        val moving = event { table(stage(elsewhere)) }
        val setting = event { table(TableChange.Suggestion(null, ResultSuggestion(games.bob, arcanum.engine.setup.GameResult.Draw))) }
        assertNull(UndoRule.blocking(play, listOf(moving, setting)))
        UndoRule.undo(model, play, listOf(moving, setting))
        // The land is back in hand; the later table events keep their values
        val expected = before.also { it.apply(moving.copy(parentId = "x")); it.apply(setting) }
        assertTrue(model.sameAs(expected))
    }

    // CMB-043, SYS-058 (owner notes 2026-11-14 evening: after the other player's approved rewind the game came back to declare
    // attackers with the attacker's earlier staging still there): a rewind keeps the other player's combat staging only while the
    // game still awaits the declaration it stages at the new head, so a rewind back out of the declare attackers step drops it
    @Test
    fun aRewindOutOfTheDeclarationDropsItsStaging() {
        val points = mutableListOf<Pair<String, GameModel>>()
        val events = mutableListOf<GameEvent>()
        fun declaring() = model.engine.let { s -> s.pending == null && s.turn.step == arcanum.api.TurnStep.DeclareAttackers &&
            s.players.any { p -> Engine.options(s, p.id, EnginePreferences.of(s)).any { it is Input.DeclareAttackers } } &&
            s.battlefield.any { s.obj(it)?.controller == s.turn.active } }
        while (!declaring()) {
            points += parent to model.copy()
            events += games.next(model, parent).also { parent = it.id }
        }
        val active = model.engine.turn.active
        val other = model.engine.players.first { it.id != active }.id
        val attacker = model.engine.battlefield.first { model.engine.obj(it)?.controller == active }
        val staging = EventBuilder(model, "staging", parent, active.value).apply {
            table(TableChange.AttackDraft(null, listOf(arcanum.engine.input.Attack(attacker, arcanum.engine.state.TargetRef.Player(other)))))
        }.build()
        events += staging
        fun rewound(index: Int): Pair<List<GameEvent>, List<String>> {
            val (target, at) = points[index]
            val dropped = mutableListOf<String>()
            val added = Rewind.again(at.copy(), target, events.drop(index), other, 1, { "again${dropped.size}" }, { e, why -> dropped += "${e.id}: $why" }) {}
            return added to dropped
        }
        // Back to before the declare attackers step: the staging doesn't stand, and the logs say why
        val before = points.indexOfLast { it.second.engine.turn.step != arcanum.api.TurnStep.DeclareAttackers }
        val (added, dropped) = rewound(before)
        assertTrue(added.none { e -> e.table.any { it is TableChange.AttackDraft } }, "no staging re-added: $added")
        assertTrue(dropped.any { it.startsWith("staging: its combat staging") }, "said why: $dropped")
        // Back to a point in the same declare attackers step (the step's first board): it stands (SYS-058)
        val inStep = points.indexOfFirst { it.second.engine.turn.step == arcanum.api.TurnStep.DeclareAttackers && it.second.engine.turn.number == model.engine.turn.number }
        if (inStep >= 0 && points[inStep].second.engine.let { s -> s.players.any { p -> Engine.options(s, p.id, EnginePreferences.of(s)).any { it is Input.DeclareAttackers } } })
            assertTrue(rewound(inStep).first.any { e -> e.table.any { it is TableChange.AttackDraft } }, "kept within its step")
    }

    @Test
    fun aLaterEngineInputBlocksUndoingAnyEarlierEngineChange() {
        val land = untilALandCanBePlayed()
        val play = event { perform(land) }
        val pass = event { perform(Input.PassPriority(model.engine.priority.holder!!)) }
        assertSame(pass, UndoRule.blocking(play, listOf(pass)))
        // ... but a table-only event before it is free to go: no engine input reads the table
        val obj = model.engine.battlefield.last()
        val placing = event { table(stage(obj)) }
        val any = model.engine.players.flatMap { Engine.options(model.engine, it.id, org.asyncttwiz.protocol.game.EnginePreferences.of(model.engine)) }.first { it !is Input.Concede }
        val pass2 = event { perform(any) }
        assertNull(UndoRule.blocking(placing, listOf(pass2)))
    }

    @Test
    fun tableEventsBlockEachOtherOnlyOnTheSameThing() {
        untilALandCanBePlayed()
        val obj = model.engine.battlefield.first()
        val first = event { table(stage(obj)) }
        val unrelated = event { table(TableChange.EarlyAnswered("u", null, EarlyAnswer(games.bob, "u", arcanum.engine.decision.Answer.YesNo(true)))) }
        val again = event { table(TableChange.AttackDraft(model.table.attackDraft, null)) }
        assertNull(UndoRule.blocking(first, listOf(unrelated)))
        assertEquals(again, UndoRule.blocking(first, listOf(unrelated, again)))
        // The latest event can always be undone
        assertNull(UndoRule.blocking(again, emptyList()))
        val alice = PlayerId("alice")
        val early = EarlyAnswer(alice, "k", arcanum.engine.decision.Answer.YesNo(true))
        val settings = event { table(TableChange.EarlyAnswered("k", null, early)) }
        val result = event { table(TableChange.Suggestion(null, ResultSuggestion(alice, arcanum.engine.setup.GameResult.Draw))) }
        assertNull(UndoRule.blocking(settings, listOf(result)))
    }
}
