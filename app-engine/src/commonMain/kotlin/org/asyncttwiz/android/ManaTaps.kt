package org.asyncttwiz.android

import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import org.asyncttwiz.automatum.ManaPicks
import org.asyncttwiz.automatum.ManaWays

/**
 * STK-024 (owner playtest 2026-11-09): the player's own changes to a payment, a cast's or one made while something resolves
 * (STK-049): the sources they tapped on the table or as chips ([tapped]: each with the way the tap took, which Autotap may still
 * change while the rest of the cost needs it), the ways they chose with a source's corner chip ([chosen]: kept exactly), and the
 * sources they took out ([outs]: not used again while Autotap fills in the rest). Immutable: worked out off the main thread and
 * applied when still current.
 */
data class ManaTaps(
    val tapped: List<ManaUse> = emptyList(),
    val chosen: List<ManaUse> = emptyList(),
    val outs: List<ManaUse> = emptyList(),
) {
    /** Every pick, chosen ways first. */
    val picks: List<ManaUse> get() = chosen + tapped
    val empty: Boolean get() = tapped.isEmpty() && chosen.isEmpty() && outs.isEmpty()

    /** What a payment's engine checks are, for [ManaTaps]' rules: [fill] the open payment around picks ([arcanum.engine.CastScreen.fillPayment]:
     * pinned, removed, exact) and whether uses [pays] it. */
    class Engine(val fill: (List<ManaUse>, List<ManaUse>, List<ManaUse>) -> List<ManaUse>, val pays: (List<ManaUse>) -> Boolean)

    /** The outcome of a tap or a chosen way: the picks after it, the payment they come to, and what happened in words (the log). */
    data class Outcome(val taps: ManaTaps, val mana: List<ManaUse>, val said: String)

    companion object {
        /**
         * A tap on a source (or a chip) whose uses are [ways] ([belongs]: which uses of the payment are of it), with [current]
         * paying now: one paying is taken out; one not paying is added with its sensible way ([ManaWays.sensible]: the first, in
         * the order that serves what the spell cares about in colours ([care]), after which the rest can still be paid, the
         * engine's own check), then the payment is worked out again ([payment]).
         */
        fun tap(taps: ManaTaps, decision: Decision.ManaPayment, current: List<ManaUse>, ways: List<ManaUse>, belongs: (ManaUse) -> Boolean,
            autotap: Boolean, care: ManaWays.ColorCare, engine: Engine): Outcome {
            val paying = current.filter(belongs)
            val kept = taps.copy(tapped = taps.tapped.filterNot(belongs), chosen = taps.chosen.filterNot(belongs))
            if (paying.isNotEmpty()) {
                val next = kept.copy(outs = kept.outs + paying.first())
                return Outcome(next, payment(next, decision, autotap, engine), "taken out (${paying.joinToString { ManaWays.label(it) }})")
            }
            val outs = kept.outs.filterNot(belongs)
            val spent = current.filterNot(belongs).flatMap(ManaWays::colorsOf).toSet()
            val removed = if (autotap) outs else emptyList()
            var tries = 0
            val way = ManaWays.sensible(ways, care, spent) { use ->
                tries++
                engine.fill(kept.tapped, removed, kept.chosen + use).isNotEmpty()
            } ?: return Outcome(taps, current, "no way to pay with it")
            val next = kept.copy(tapped = kept.tapped + way, outs = outs)
            val why = if (ways.size == 1) "its one way" else "${ManaWays.label(way)} of ${ways.joinToString("/") { ManaWays.label(it) }} " +
                "(colours: $care, $tries tried)"
            return Outcome(next, payment(next, decision, autotap, engine), "added with $why")
        }

        /** A way chosen with a source's corner chip: [way] replaces whatever it paid with ([belongs]), kept exactly. */
        fun choose(taps: ManaTaps, decision: Decision.ManaPayment, way: ManaUse, belongs: (ManaUse) -> Boolean, autotap: Boolean,
            engine: Engine): Outcome {
            val next = taps.copy(tapped = taps.tapped.filterNot(belongs), chosen = taps.chosen.filterNot(belongs) + way,
                outs = taps.outs.filterNot(belongs))
            return Outcome(next, payment(next, decision, autotap, engine), "way chosen: ${ManaWays.label(way)}")
        }

        /**
         * The payment [taps] come to. With Autotap on: Autotap fills in the rest around them, the tapped sources keeping their ways
         * while that pays, else changing colour as the rest needs, else only the picks as with it off. With it off only the picks,
         * the tapped sources' colours worked out together so the set pays when it can ([ManaPicks.assign]; chosen ways stay).
         */
        fun payment(taps: ManaTaps, decision: Decision.ManaPayment, autotap: Boolean, engine: Engine): List<ManaUse> {
            if (autotap) {
                if (taps.empty) return engine.fill(emptyList(), emptyList(), emptyList())
                engine.fill(emptyList(), taps.outs, taps.picks).ifEmpty { engine.fill(taps.tapped, taps.outs, taps.chosen) }
                    .takeIf { it.isNotEmpty() }?.let { return it }
                // A source Autotap can't read (an amount it announces): the picks alone, their colours worked out together
            }
            val uses = taps.picks
            if (uses.isEmpty() || engine.pays(uses)) return uses
            val groups = ManaPicks.groups(decision)
            val tappedGroups = taps.tapped.map { use -> groups.firstOrNull { use in it } ?: listOf(use) }
            return ManaPicks.assign(taps.chosen.map { listOf(it) } + tappedGroups, uses, engine.pays) ?: uses
        }
    }
}
