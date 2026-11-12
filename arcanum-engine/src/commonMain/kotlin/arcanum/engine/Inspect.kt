package arcanum.engine

import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.view.Layers
import arcanum.engine.view.View

/**
 * For a host's Inspect and the rules state it shows: where an object's characteristics come from, what continuous
 * effects tell it it can't or must do, what an object that no longer exists last was, and the tagged events the state keeps.
 * The values the engine keeps for a player, an object or the game (speed, the Ring, day and night, a Class's level, …) are
 * their statuses ([arcanum.engine.view.ObjectView.statuses], [arcanum.engine.view.PlayerView.statuses],
 * [arcanum.engine.view.GameView.statuses]).
 * Stateless, like [Engine]: it reads the [GameState] it is given and changes nothing.
 */
object Inspect {
    /**
     * Where object [id]'s characteristics come from (spec §7.1, a host's Inspect: "2/2 printed, +1/+1 from an anthem,
     * +3/+3 until end of turn"): its values after layer 1, then each continuous effect that changed them in the order
     * applied (CR 613), with what made it. Works the layers out afresh (one [Engine.view]'s cost). Null when [id] has no
     * characteristics (no such object).
     */
    fun explain(state: GameState, id: arcanum.engine.state.ObjectId): arcanum.engine.view.Explanation? = Layers.explain(state, id)

    /**
     * [explain] for several objects at once (a host inspecting a group of cards), from one run of the layers: the
     * explanation of each of [ids] that has characteristics, by id; the others are left out.
     */
    fun explain(state: GameState, ids: Collection<arcanum.engine.state.ObjectId>): Map<arcanum.engine.state.ObjectId, arcanum.engine.view.Explanation> =
        Layers.explain(state, ids)

    /**
     * What continuous effects tell object [id] it can't or must do beyond its characteristics ("can't block", "can't be
     * blocked by creatures with flying", "attacks each combat if able", "doesn't untap", "can't be countered"), a host's
     * Inspect beside [explain]: each with plain words, what made it and how long it lasts. Read from [view], which the host
     * already has, so it works nothing out again. What the object's own printed text says and what a keyword means (its
     * keyword is listed instead) are left out. Empty for an object nothing restricts or that isn't there.
     */
    fun restrictions(state: GameState, view: View, id: arcanum.engine.state.ObjectId): List<arcanum.engine.view.AppliedRestriction> =
        arcanum.engine.view.Restrictions.of(state, view, id)

    /** What [id] was as it last existed (CR 608.2h), for an object that no longer exists: a choice can still offer one (CR 701.44c, d). Null while
     * it exists or when nothing is known of it; its characteristics have no name if it was face down. */
    fun lastKnown(state: GameState, id: arcanum.engine.state.ObjectId): arcanum.engine.state.ObjectSnapshot? = arcanum.engine.query.Lki.find(state, id)

    /** The tagged events (D33) the state keeps, oldest first, each with its turn: this and last turn's, and any older ones the
     * host hasn't dropped ([GameState.forgetOlderTurns]). For a host's history and displays ("made by", mulligans). */
    fun events(state: GameState): List<arcanum.engine.state.LoggedChange> = state.log
}
