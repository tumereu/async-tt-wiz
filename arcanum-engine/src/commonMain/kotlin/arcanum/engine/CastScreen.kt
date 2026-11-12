package arcanum.engine

import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.view.View

/**
 * For a host's cast and activation screens (castability, CR 601.2): what the open cast or activation can still come to,
 * what may be picked next, its payment around a player's own choices, and why [Engine.perform] would refuse an action
 * at once. Stateless, like [Engine]: it reads the [GameState] it is given and changes nothing.
 */
object CastScreen {
    /**
     * What the open cast or activation can come to (castability §2): the smallest cost among the ways through its open choices,
     * whether it can have several, Autotap's suggestion for that smallest, and the answers leading to it. Blocking.
     */
    fun castCosts(state: GameState): CastCosts = Options.castCosts(state)

    /**
     * What may be picked next in the open choice of targets or objects with [picked] picked so far (castability §3, CR 601.2c,
     * 601.2f): a candidate is listed when some finished selection including it and [picked] can be paid and is legal, not when the
     * selection is payable right now, since a cost can fall as more are picked. A search bound keeps a candidate listed;
     * [Selection.done] is for [picked] itself, false until a total the choice asks is reached (at any later point too, CR 701.59a:
     * [arcanum.engine.decision.Decision.ChooseObjects.total]). Blocking: run it off the main thread. Picked candidates stay listed
     * (they can be taken back).
     */
    fun selection(state: GameState, picked: List<arcanum.engine.state.TargetRef>): Selection = Options.selection(state, picked)

    /**
     * Whether the open cast or activation can still be finished from here, as far as the search can tell (castability §1): false
     * only when it has shown there is no way; a search stopped by a bound, or a payment that may exist, says true. What
     * [Engine.options] lists a cast by, for a host working out a choice it made (a way with its extras).
     */
    fun finishable(state: GameState): Boolean = Options.finishable(state)

    /** [finishable] once [answer] is given to the open decision: false when it is refused or leaves no way. */
    fun finishableWith(state: GameState, answer: arcanum.engine.decision.Answer): Boolean = Options.finishableWith(state, answer)

    /**
     * The open [arcanum.engine.decision.Decision.ManaPayment] paid around a player's own choices (STK-024): [pinned] uses stay,
     * [removed] uses aren't used, Autotap's order fills in the rest; empty when the rest can't be filled. A pinned mana
     * ability's source stays, its colour (and what else it chooses) free; each of [exact] stays exactly as given (a player who
     * chose which way a source pays: its colour, its ability).
     */
    fun fillPayment(state: GameState, pinned: List<arcanum.engine.decision.ManaUse>, removed: List<arcanum.engine.decision.ManaUse>,
        exact: List<arcanum.engine.decision.ManaUse> = emptyList()): List<arcanum.engine.decision.ManaUse> =
        Options.fillPayment(state, pinned, removed, exact)

    /**
     * Whether [value], announced for the open announcement of a cost (an X, CR 107.3, 601.2b), might still be paid: its mana
     * found, or paid at the most every cost reduction could take (CR 601.2f). False is proof that neither it nor any larger
     * value can be paid; true is no promise ([finishableWith] tells exactly).
     */
    fun announcedMayPay(state: GameState, value: Int): Boolean = Options.announcedMayPay(state, value)

    /** Whether a reduction may come to apply to the open cast or activation as its choices are made: then what it costs needn't rise with an X (CR 202.3e, 601.2f). */
    fun anyReduction(state: GameState): Boolean = Options.anyReduction(state)

    /**
     * Why [Engine.perform] would refuse [input] at once, worked out without performing it (no copy of [state] is made; with
     * [view], [state]'s view when the caller has it, the layers aren't worked out either): for a land play, a cast or an
     * activation (special actions included) the same reason, word for word, as the [IllegalArgumentException] [Engine.perform]
     * throws before doing anything (the game is over, a decision is open, or it can't be begun: priority, timing, uses
     * left this turn, what the card or ability allows, CR 116, 117.1, 305, 601.2, 602.5, 606.3). Null when it may be
     * begun ([Engine.perform] may still refuse it later, as it is announced: a target or a cost it can't have) and for any
     * other input. A host greys
     * an action out with this reason instead of trying it.
     */
    fun refusal(state: GameState, input: Input, view: View? = null): String? = Perform.refusal(state, input, view)
}
