package org.asyncttwiz.android

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.GameState
import arcanum.engine.state.TargetRef

/**
 * An open decision picked on the table (STK-047): targets, or (D1, owner playtest 2026-11-07) a choice of cards whose every
 * candidate is a permanent on the battlefield (a sacrifice, creatures to tap, a permanent to return or exile as a cost, an
 * edict, "choose a creature"), so the cards are picked where their auras, counters and tapped state show. Its candidates
 * are [options] as targets are, so glowing, picking, groups and Done work the same for both.
 */
sealed interface TablePick {
    val decision: Decision
    /** The candidates, as targets (a card as its object). */
    val options: List<TargetRef>
    val min: Int
    val max: Int?
    /** The bar's title ("Choose any target", "Sacrifice another creature"). */
    val title: String
    /** The bar's description for accessibility and tests. */
    val description: String
    /** What is chosen on the table, after "Then " in a panel still choosing its way ("choose any target"). */
    val prompt: String
    /** The answer picking [picked] gives. */
    fun answer(picked: List<TargetRef>): Answer

    data class Targets(override val decision: Decision.Targets, val payable: List<TargetRef>? = null) : TablePick {
        override val options: List<TargetRef> get() = payable ?: decision.options
        override val min: Int get() = decision.min
        override val max: Int? get() = decision.max
        override val title: String get() = targetTitle(decision)
        override val description: String get() = "Choose targets"
        override val prompt: String get() = targetPrompt(decision)
        override fun answer(picked: List<TargetRef>): Answer = Answer.Targets(picked)
    }

    data class Objects(override val decision: Decision.ChooseObjects, val payable: List<TargetRef>? = null) : TablePick {
        override val options: List<TargetRef> = payable ?: decision.options.map { TargetRef.Object(it) }
        override val min: Int get() = decision.min
        override val max: Int? get() = decision.max
        /** What the engine's sentence says it is ("Sacrifice another creature"); a sentence too long for a title ("Each
         * opponent sacrifices a creature of their choice.", shown under it) says how many to pick instead. */
        override val title: String = ObjectsShape.of(decision).title.takeUnless { it == "Choose cards" || it == "Choose" } ?: when {
            decision.max == 1 -> if (decision.min == 0) "Choose up to one" else "Choose one"
            decision.max == null -> "Choose any number"
            decision.min == decision.max -> "Choose ${decision.max}"
            else -> "Choose up to ${decision.max}"
        }
        override val description: String get() = "Choose on the table"
        override val prompt: String get() = title.removeSuffix(".").replaceFirstChar { it.lowercase() }
        override fun answer(picked: List<TargetRef>): Answer = Answer.Objects(picked.mapNotNull { (it as? TargetRef.Object)?.id })
    }

    companion object {
        /** [decision] as picked on the table on [state], or null when it is answered in a panel. [payable]: only these
         * candidates can be picked now given what is picked so far (castability §3, CR 601.2c, 601.2f); null: all can. */
        fun of(decision: Decision?, state: GameState?, payable: List<TargetRef>? = null): TablePick? = when (decision) {
            is Decision.Targets -> Targets(decision, payable)
            is Decision.ChooseObjects -> if (state != null && onBattlefield(decision, state)) Objects(decision, payable) else null
            else -> null
        }

        /** D1: a choice of cards picked from the battlefield: a plain pick (not two places or an order, not a search) whose
         * every candidate is a permanent there. Choices among cards in a hand, library, graveyard or mixed zones keep their
         * panel. */
        fun onBattlefield(decision: Decision.ChooseObjects, state: GameState): Boolean {
            if (decision.options.isEmpty()) return false
            val shape = ObjectsShape.of(decision)
            return shape.shape == ObjectsShape.Shape.Pick && !shape.search &&
                decision.options.all { state.obj(it)?.zone == arcanum.api.Zone.Battlefield }
        }
    }
}
