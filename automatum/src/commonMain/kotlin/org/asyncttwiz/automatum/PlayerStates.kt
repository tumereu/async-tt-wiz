package org.asyncttwiz.automatum

import arcanum.api.CounterKind
import arcanum.api.Designation
import arcanum.api.RulesGlossary
import arcanum.engine.state.GameState
import arcanum.engine.state.GameStatus
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PlayerStatus

/** One state of a player or the game as its chip reads it ("Monarch", "Ring 2/4"), with what it means ([explanation], null
 * for a counter the rules give no meaning of its own), for a hold, and the [glyph] its round chip shows (TRN-072). */
data class StateChip(val label: String, val explanation: RulesGlossary.Entry?, val glyph: StateGlyph = StateGlyph.Letters(label.take(1)))

/** TRN-072 (owner gallery review 2026-11-10, gallery 157): what a state's round chip shows in place of its words. */
sealed interface StateGlyph {
    /** A letter or two, with the state's number where it has one ("Sh1"): a player counter with no icon of its own. */
    data class Letters(val text: String) : StateGlyph
    /** TRN-072 (owner notes 2026-11-14): the state's own [icon], with its current [level] on it where it has one (speed 2, the
     * Ring's level, 3 poison). */
    data class Icon(val icon: StateIcon, val level: Int? = null) : StateGlyph
    /** TRN-073: a sun. */
    data object Day : StateGlyph
    /** TRN-073: a moon. */
    data object Night : StateGlyph
    /** TRN-069: a door, for the room a player's dungeon marker is on. */
    data object Room : StateGlyph
}

/** TRN-072 (owner notes 2026-11-14): the player states with an icon of their own; a table draws each as it likes. */
sealed interface StateIcon {
    data object Monarch : StateIcon
    data object Initiative : StateIcon
    data object CitysBlessing : StateIcon
    data object EnduringStory : StateIcon
    data object Speed : StateIcon
    data object Ring : StateIcon
    data object Poison : StateIcon
    data object Energy : StateIcon
    data object Experience : StateIcon
    data object Rad : StateIcon
    data object Dungeons : StateIcon
    data object ExtraTurn : StateIcon
    data object SkippedTurn : StateIcon
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
     * The glyphs (TRN-072, owner notes 2026-11-14): each state its own icon ([StateGlyph.Icon]) with its current level where it has
     * one (speed, the Ring's level, poison, energy, experience, rad, dungeons completed, extra and skipped turns); a player counter
     * with no icon of its own keeps letters: its first letter and count, or its first two letters where an earlier counter's chip
     * shows that letter.
     */
    fun of(state: GameState, account: String): List<StateChip> {
        val id = PlayerId(account)
        val player = state.players.firstOrNull { it.id == id } ?: return emptyList()
        // The player's statuses (the same values their view has, [arcanum.engine.view.PlayerView.statuses])
        // Designations in the order TRN-072 lists them: Monarch, Initiative, City's blessing, Enduring story
        val designations = arrayOfNulls<StateChip>(4)
        var speed: StateChip? = null
        var ring: StateChip? = null
        var dungeons: StateChip? = null
        for (status in player.statuses) when (status) {
            is PlayerStatus.Designated -> when (status.designation) {
                Designation.Monarch -> designations[0] = chip("Monarch", "monarch", StateIcon.Monarch)
                Designation.Initiative -> designations[1] = chip("Initiative", "initiative", StateIcon.Initiative)
                Designation.CitysBlessing -> designations[2] = chip("City's blessing", "citys-blessing", StateIcon.CitysBlessing)
                Designation.EnduringStory -> designations[3] = chip("Enduring story", "enduring-story", StateIcon.EnduringStory)
            }
            is PlayerStatus.Speed -> speed = status.value.takeIf { it > 0 }?.let { chip("Speed $it", "speed", StateIcon.Speed, it) }
            is PlayerStatus.RingTempted -> ring = status.times.takeIf { it > 0 }?.coerceAtMost(RING_LEVELS)?.let { level ->
                chip("Ring $level/$RING_LEVELS", "the-ring", StateIcon.Ring, level) }
            is PlayerStatus.DungeonsCompleted -> dungeons = status.count.takeIf { it > 0 }?.let {
                chip("Dungeons $it", "dungeons-completed", StateIcon.Dungeons, it) }
        }
        val chips = buildList {
            designations.forEach { it?.let(::add) }
            speed?.let(::add)
            ring?.let(::add)
            val taken = HashSet<String>()
            for (entry in player.counters) if (entry.count != 0) add(counter(entry.kind, entry.count, taken))
            dungeons?.let(::add)
            state.turn.extraTurns.count { it == id }.takeIf { it > 0 }?.let {
                add(chip(if (it == 1) "Extra turn" else "Extra turns $it", "extra-turn", StateIcon.ExtraTurn, it)) }
            state.turn.skippedTurns.count { it == id }.takeIf { it > 0 }?.let {
                add(chip(if (it == 1) "Skips a turn" else "Skips $it turns", "skipped-turn", StateIcon.SkippedTurn, it)) }
        }
        return chips
    }

    /** TRN-073: "Day" or "Night" (CR 731), null while it is neither. */
    fun dayOrNight(state: GameState): StateChip? {
        var chip: StateChip? = null
        for (status in state.statuses) when (status) {
            is GameStatus.DayNight -> chip = if (status.night) StateChip("Night", RulesGlossary.state("night"), StateGlyph.Night)
                else StateChip("Day", RulesGlossary.state("day"), StateGlyph.Day)
        }
        return chip
    }

    private fun chip(label: String, id: String, icon: StateIcon, level: Int? = null) =
        StateChip(label, RulesGlossary.state(id), StateGlyph.Icon(icon, level))

    /** A counter's chip: its own icon with its count (poison, energy, experience, rad), or else its name's first letter and its
     * count, or its first two letters when an earlier counter's chip already shows that letter ([taken], which it adds its own to). */
    private fun counter(kind: CounterKind, count: Int, taken: MutableSet<String>): StateChip {
        val name = when (kind) {
            CounterKind.Poison -> "poison"
            CounterKind.Energy -> "energy"
            is CounterKind.Named -> kind.name
            else -> ComputedBoard.counterName(EngineCodec.counterName(kind))
        }
        val title = name.replaceFirstChar { it.uppercase() }
        val icon = when {
            kind == CounterKind.Poison -> StateIcon.Poison
            kind == CounterKind.Energy -> StateIcon.Energy
            kind == CounterKind.Rad -> StateIcon.Rad
            name.equals("experience", ignoreCase = true) -> StateIcon.Experience
            else -> null
        }
        if (icon != null) return chip("$title $count", name.lowercase(), icon, count)
        val letter = title.take(1)
        val letters = if (letter in taken) title.filter(Char::isLetter).take(2) else letter
        taken += letters
        return StateChip("$title $count", RulesGlossary.state(name.lowercase()), StateGlyph.Letters("$letters$count"))
    }
}
