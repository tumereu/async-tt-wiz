package org.asyncttwiz.automatum

import arcanum.api.CounterKind
import arcanum.api.Designation
import arcanum.api.RulesGlossary
import arcanum.engine.Engine
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.view.DayOrNight

/** One state of a player or the game as its chip reads it ("Monarch", "Ring 2/4"), with what it means ([explanation], null
 * for a counter the rules give no meaning of its own), for a hold. */
data class StateChip(val label: String, val explanation: RulesGlossary.Entry?)

/**
 * TRN-072: what the rules keep for a player and don't show elsewhere, in the order their chips show: designations (the
 * monarch, the initiative, the city's blessing, an enduring story), speed, the Ring's level, the player's counters (poison,
 * energy, ...), dungeons completed, and the turns they will take or skip. All of it is public (CR 400.2, 122.1). Reads only
 * values the state already holds, so a table can ask on every layout. TRN-073: day or night, the game's.
 */
object PlayerStates {
    /** The Ring's levels (CR 701.54c): shown as "Ring 2/4". */
    private const val RING_LEVELS = 4

    fun of(state: GameState, account: String): List<StateChip> {
        val id = PlayerId(account)
        val player = state.players.firstOrNull { it.id == id } ?: return emptyList()
        return buildList {
            for (designation in player.designations) add(when (designation) {
                Designation.Monarch -> chip("Monarch", "monarch")
                Designation.Initiative -> chip("Initiative", "initiative")
                Designation.CitysBlessing -> chip("City's blessing", "citys-blessing")
                Designation.EnduringStory -> chip("Enduring story", "enduring-story")
            })
            Engine.speed(state, id).takeIf { it > 0 }?.let { add(chip("Speed $it", "speed")) }
            Engine.ringTemptations(state, id).takeIf { it > 0 }?.let { add(chip("Ring ${it.coerceAtMost(RING_LEVELS)}/$RING_LEVELS", "the-ring")) }
            for (entry in player.counters) if (entry.count != 0) add(counter(entry.kind, entry.count))
            Engine.dungeonsCompleted(state, id).takeIf { it > 0 }?.let { add(chip("Dungeons $it", "dungeons-completed")) }
            state.turn.extraTurns.count { it == id }.takeIf { it > 0 }?.let { add(chip(if (it == 1) "Extra turn" else "Extra turns $it", "extra-turn")) }
            state.turn.skippedTurns.count { it == id }.takeIf { it > 0 }?.let { add(chip(if (it == 1) "Skips a turn" else "Skips $it turns", "skipped-turn")) }
        }
    }

    /** TRN-073: "Day" or "Night" (CR 731), null while it is neither. */
    fun dayOrNight(state: GameState): StateChip? = when (Engine.dayOrNight(state)) {
        DayOrNight.Day -> chip("Day", "day")
        DayOrNight.Night -> chip("Night", "night")
        null -> null
    }

    private fun chip(label: String, id: String) = StateChip(label, RulesGlossary.state(id))

    private fun counter(kind: CounterKind, count: Int): StateChip {
        val name = when (kind) {
            CounterKind.Poison -> "poison"
            CounterKind.Energy -> "energy"
            is CounterKind.Named -> kind.name
            else -> ComputedBoard.counterName(EngineCodec.counterName(kind))
        }
        return chip("${name.replaceFirstChar { it.uppercase() }} $count", name.lowercase())
    }
}
