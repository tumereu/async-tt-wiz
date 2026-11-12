package org.asyncttwiz.automatum

import arcanum.api.CounterKind
import arcanum.api.Designation
import arcanum.api.RulesGlossary
import arcanum.engine.Inspect
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.view.DayOrNight

/** One state of a player or the game as its chip reads it ("Monarch", "Ring 2/4"), with what it means ([explanation], null
 * for a counter the rules give no meaning of its own), for a hold, and the [glyph] its round chip shows (TRN-072). */
data class StateChip(val label: String, val explanation: RulesGlossary.Entry?, val glyph: StateGlyph = StateGlyph.Letters(label.take(1)))

/** TRN-072 (owner gallery review 2026-11-10, gallery 157): what a state's round chip shows in place of its words. */
sealed interface StateGlyph {
    /** A letter or two, with the state's number where it has one ("M", "S2", "P3"). */
    data class Letters(val text: String) : StateGlyph
    /** TRN-073: a sun. */
    data object Day : StateGlyph
    /** TRN-073: a moon. */
    data object Night : StateGlyph
    /** TRN-069: a door, for the room a player's dungeon marker is on. */
    data object Room : StateGlyph
}

/**
 * TRN-072: what the rules keep for a player and don't show elsewhere, in the order their chips show: designations (the
 * monarch, the initiative, the city's blessing, an enduring story), speed, the Ring's level, the player's counters (poison,
 * energy, ...), dungeons completed, and the turns they will take or skip. All of it is public (CR 400.2, 122.1). Reads only
 * values the state already holds, so a table can ask on every layout. TRN-073: day or night, the game's.
 */
object PlayerStates {
    /** The Ring's levels (CR 701.54c): shown as "Ring 2/4". */
    private const val RING_LEVELS = 4

    /**
     * The glyphs (TRN-072, owner gallery review 2026-11-10): each state's first letter, with its number where it has one; where
     * two would share a letter they are told apart: an enduring story "St" (energy keeps "E"), extra and skipped turns "+T" and
     * "−T" (their count before the T when more than one), and a counter whose letter an earlier chip shows its first two letters.
     */
    fun of(state: GameState, account: String): List<StateChip> {
        val id = PlayerId(account)
        val player = state.players.firstOrNull { it.id == id } ?: return emptyList()
        val chips = buildList {
            for (designation in player.designations) add(when (designation) {
                Designation.Monarch -> chip("Monarch", "monarch", "M")
                Designation.Initiative -> chip("Initiative", "initiative", "I")
                Designation.CitysBlessing -> chip("City's blessing", "citys-blessing", "C")
                Designation.EnduringStory -> chip("Enduring story", "enduring-story", "St")
            })
            Inspect.speed(state, id).takeIf { it > 0 }?.let { add(chip("Speed $it", "speed", "S$it")) }
            Inspect.ringTemptations(state, id).takeIf { it > 0 }?.coerceAtMost(RING_LEVELS)?.let { level ->
                add(chip("Ring $level/$RING_LEVELS", "the-ring", "R$level")) }
            val taken = mapNotNullTo(HashSet()) { (it.glyph as? StateGlyph.Letters)?.text?.takeWhile(Char::isLetter) }
            for (entry in player.counters) if (entry.count != 0) add(counter(entry.kind, entry.count, taken))
            Inspect.dungeonsCompleted(state, id).takeIf { it > 0 }?.let { add(chip("Dungeons $it", "dungeons-completed", "D$it")) }
            state.turn.extraTurns.count { it == id }.takeIf { it > 0 }?.let {
                add(chip(if (it == 1) "Extra turn" else "Extra turns $it", "extra-turn", if (it == 1) "+T" else "+${it}T")) }
            state.turn.skippedTurns.count { it == id }.takeIf { it > 0 }?.let {
                add(chip(if (it == 1) "Skips a turn" else "Skips $it turns", "skipped-turn", if (it == 1) "−T" else "−${it}T")) }
        }
        return chips
    }

    /** TRN-073: "Day" or "Night" (CR 731), null while it is neither. */
    fun dayOrNight(state: GameState): StateChip? = when (Inspect.dayOrNight(state)) {
        DayOrNight.Day -> StateChip("Day", RulesGlossary.state("day"), StateGlyph.Day)
        DayOrNight.Night -> StateChip("Night", RulesGlossary.state("night"), StateGlyph.Night)
        null -> null
    }

    private fun chip(label: String, id: String, letters: String) = StateChip(label, RulesGlossary.state(id), StateGlyph.Letters(letters))

    /** A counter's chip, its glyph its name's first letter and its count, or its first two letters when an earlier chip already
     * shows that letter ([taken], which it adds its own to; "D" is kept for dungeons completed, which come after). */
    private fun counter(kind: CounterKind, count: Int, taken: MutableSet<String>): StateChip {
        val name = when (kind) {
            CounterKind.Poison -> "poison"
            CounterKind.Energy -> "energy"
            is CounterKind.Named -> kind.name
            else -> ComputedBoard.counterName(EngineCodec.counterName(kind))
        }
        val title = name.replaceFirstChar { it.uppercase() }
        val letter = title.take(1)
        val letters = if (letter in taken || letter == "D") title.filter(Char::isLetter).take(2) else letter
        taken += letters
        return chip("$title $count", name.lowercase(), "$letters$count")
    }
}
