package org.asyncttwiz.android

import org.asyncttwiz.protocol.HistoryIcon
import org.asyncttwiz.protocol.game.GameEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HIS-239, HIS-246–HIS-248 (owner 2026-11-22, F6): how the game log's lines are put together from the events: the timeline's
 * events with what each caused under it, a log-only combat damage line, and the triggers of the events the log has no line
 * for under the next turn and step label.
 */
class GameLogAssemblyTest {
    private fun event(id: String) = GameEvent(id, "p", "alice")
    private fun shown(id: String, label: String? = null) =
        HistoryTimelineEvent(id, HistoryIcon.CHECK, "line $id", "alice", label?.let { TimelineSeparator(it, false, "alice") })
    private fun line(text: String) = listOf(StackTextPiece(text))

    /** What each event caused (all of it, or only its triggers), and which events dealt combat damage. */
    private val caused = mapOf("a" to "Ember Cub died", "c" to "Bob lost 2 life (18)", "e" to "Ember Sage died")
    private val triggers = mapOf("b" to "Ember Herald's ability triggered", "d" to "Ember Rider's ability triggered")
    private fun assemble(events: List<String>, timeline: List<HistoryTimelineEvent>, combat: Set<String> = emptySet()) =
        EngineTimeline.assemble(events.map(::event), timeline, { _, t -> line(t.description) },
            { e, part -> listOfNotNull(when (part) {
                EngineConsequences.Part.Triggers -> triggers[e.id]
                EngineConsequences.Part.BeforeCombatDamage -> "before the damage of ${e.id}"
                EngineConsequences.Part.CombatDamage -> "the damage of ${e.id}"
                EngineConsequences.Part.All -> caused[e.id] ?: triggers[e.id]
            }?.let(::line)) },
            { e -> if (e.id in combat) "Combat damage" else null })

    // HIS-246: each timeline event's line with what it caused under it
    @Test fun eachLineWithWhatItCaused() {
        val lines = assemble(listOf("a", "e"), listOf(shown("a"), shown("e")))
        assertEquals(listOf("line a", "line e"), lines.map { it.pieces.plain() })
        assertEquals(listOf("Ember Cub died"), lines[0].consequences.map { it.plain() })
        assertEquals(listOf("Ember Sage died"), lines[1].consequences.map { it.plain() })
        assertEquals(listOf("a", "e"), lines.map { it.opens })
    }

    // HIS-247: an event the timeline doesn't show that dealt combat damage gets a log-only line with the swords icon and
    // what it caused; a tap opens timeline mode at the next timeline event, at the one before it while none follows
    @Test fun aCombatDamageLine() {
        val waiting = assemble(listOf("a", "c"), listOf(shown("a")), combat = setOf("c"))
        assertEquals(listOf("line a", "Combat damage"), waiting.map { it.pieces.plain() })
        assertEquals(HistoryIcon.SWORDS, waiting[1].event.icon)
        assertEquals(listOf("Bob lost 2 life (18)"), waiting[1].consequences.map { it.plain() })
        assertEquals("a", waiting[1].opens)
        val followed = assemble(listOf("a", "c", "e"), listOf(shown("a"), shown("e", "T3 · Main 2")), combat = setOf("c"))
        assertEquals(listOf("line a", "Combat damage", "line e"), followed.map { it.pieces.plain() })
        assertEquals("e", followed[1].opens)
    }

    // HIS-247: an event the timeline shows that went on to deal combat damage ("Bob: no blocks") is followed by the
    // combat damage line: what came before the damage under its own line, the rest under the combat damage line, which
    // opens timeline mode at that event
    @Test fun aTimelineEventThatDealtCombatDamage() {
        val lines = assemble(listOf("a", "f"), listOf(shown("a"), shown("f")), combat = setOf("f"))
        assertEquals(listOf("line a", "line f", "Combat damage"), lines.map { it.pieces.plain() })
        assertEquals(listOf("before the damage of f"), lines[1].consequences.map { it.plain() })
        assertEquals(listOf("the damage of f"), lines[2].consequences.map { it.plain() })
        assertEquals("f", lines[2].opens)
        assertEquals(HistoryIcon.SWORDS, lines[2].event.icon)
    }

    // HIS-248: the triggers of events the log has no line for show under the next line's turn and step label; with no
    // label there, under the line before; until a next line is there, nowhere
    @Test fun triggersUnderTheNextLabel() {
        val labelled = assemble(listOf("a", "b", "e"), listOf(shown("a"), shown("e", "T4 · Upkeep")))
        assertEquals(listOf("Ember Herald's ability triggered"), labelled[1].labelConsequences.map { it.plain() })
        assertEquals(listOf("Ember Cub died"), labelled[0].consequences.map { it.plain() })
        val unlabelled = assemble(listOf("a", "d", "e"), listOf(shown("a"), shown("e")))
        assertEquals(listOf("Ember Cub died", "Ember Rider's ability triggered"), unlabelled[0].consequences.map { it.plain() })
        assertEquals(emptyList(), unlabelled[1].labelConsequences)
        val none = assemble(listOf("a", "b"), listOf(shown("a")))
        assertEquals(listOf("Ember Cub died"), none.single().consequences.map { it.plain() })
    }
}
