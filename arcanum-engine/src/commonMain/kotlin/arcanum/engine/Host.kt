package arcanum.engine

import arcanum.engine.change.Change
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.view.View

/**
 * For a host running games: passing priority for players (I-D3, I-D18), who decides for a controlled player (CR 723),
 * finding loops (CR 104.4b), hidden information (what a player may know and what changes reveal to them, for display:
 * the state itself always has full information) and the host's randomness for the library's unknown order (D25). Stateless, like [Engine]: it reads the
 * [GameState] it is given and changes nothing.
 */
object Host {
    /**
     * Whether a host passes priority for [player] now (I-D3, I-D18): they hold it, nothing is pending, and [Engine.options]
     * offers them nothing but passing (mana abilities aside). With their "Skip when I have no choice" setting
     * ([Preferences.noSkipWhenNoChoice]) on, that is enough; off, only when it doesn't depend on cards
     * only they may see (an empty hand and no face-down permanent: an instant they can't afford is theirs to pass with).
     * The engine never
     * passes for anyone by itself; a host performs [Input.PassPriority] for each player this says, one at a time. While
     * another player controls [player] (CR 723), the controller's setting counts ([decider]).
     */
    fun shouldPassFor(state: GameState, player: PlayerId, prefs: Preferences = Preferences()): Boolean =
        arcanum.engine.turn.AutoPass.shouldPass(state, player, prefs = prefs)

    /** [shouldPassFor] with [view], as [passCheck]'s. */
    fun shouldPassFor(state: GameState, player: PlayerId, view: View?, prefs: Preferences = Preferences()): Boolean =
        arcanum.engine.turn.AutoPass.shouldPass(state, player, view, prefs)

    /**
     * [shouldPassFor] two ways from one search of [Engine.options] (a host with its own "pass when I have no choice" asks both on
     * the same state): with the deciding player's setting as it is ([PassCheck.asSet], the same as [shouldPassFor]) and
     * as if [player]'s own setting were on ([PassCheck.ownSkipOn]).
     */
    fun passCheck(state: GameState, player: PlayerId, prefs: Preferences = Preferences()): PassCheck =
        arcanum.engine.turn.AutoPass.check(state, player, prefs = prefs)

    /** [passCheck] with [view], [state]'s view ([Engine.view] of [state] as it is now) when the caller already has it: the same
     * result without working the layers out again for the search. A view of another state gives wrong results. */
    fun passCheck(state: GameState, player: PlayerId, view: View?, prefs: Preferences = Preferences()): PassCheck =
        arcanum.engine.turn.AutoPass.check(state, player, view, prefs)

    /**
     * Who makes [player]'s choices and decisions now (CR 723.5): the player controlling them during this turn (723.1) or
     * its combat phase (723.2), else [player]. Decisions, options and inputs stay [player]'s own (they use only that
     * player's resources, 723.5a, and stay the active player, 723.3); a host lets the decider answer [player]'s decisions
     * and make their inputs, shows the decider what [visible] gives (723.4), and still lets only [player] concede (723.6)
     * and make the choices no rule or object calls for (723.5b). The controller goes on making their own decisions
     * (723.8).
     */
    fun decider(state: GameState, player: PlayerId): PlayerId = arcanum.engine.turn.Control.decider(state, player)

    /**
     * A key for finding loops of mandatory actions (CR 104.4b, 732.4): equal for two states that differ only in object
     * ids, timestamps and what is recorded about the past (the event log, ledgers, "this turn" counts of uses), so the
     * same position reached again in a loop has the same key though its objects are new ones. A host that passes for
     * players ([shouldPassFor]) keeps the keys of the states it passed in; meeting one again with only such passes in
     * between, it performs [Input.MandatoryLoop]. Serialises the state once (about a [Engine.view]'s cost on a large board).
     */
    fun loopKey(state: GameState): arcanum.engine.turn.LoopKey = arcanum.engine.turn.Loops.key(state)

    /**
     * The objects whose identity [player] may know now (spec §7.0 "Hidden information"): public cards, their own
     * hand and cards outside the game, face-down cards they control, and cards an ability lets them look at any time
     * ("you may look at the top card of your library any time", CR 401.5). A host shows a player what [state] knows
     * of these and nothing of the rest: of the others' cards, of [GameState.pending] decisions of other players over
     * cards not among these, and of [arcanum.engine.change.Change.Shown]s to other players. While [player] controls
     * another player ([decider]), also what that player may see of objects in the game (CR 723.4).
     */
    fun visible(state: GameState, player: PlayerId): Set<arcanum.engine.state.ObjectId> =
        arcanum.engine.action.Knowledge.visible(state, Engine.view(state), player)

    /** [visible] with [view], [state]'s view ([Engine.view] of [state] as it is now) that the caller already has: the same
     * result without working the layers out again. */
    fun visible(state: GameState, player: PlayerId, view: View): Set<arcanum.engine.state.ObjectId> =
        arcanum.engine.action.Knowledge.visible(state, view, player)

    /**
     * The cards whose identity [changes] (made on [before] in order, such as an [Outcome]'s) show [viewer] at some moment
     * that [visible] didn't give them in [before]: put where they may see them (a hand they own, a public zone face up),
     * turned face up or revealed (CR 701.20, 708.9), looked at ([Change.Shown]: a search, scry, look, the cards of a choice
     * among hidden cards), or that they may look at once the changes are made (CR 401.5). Compared by card, so a card
     * changing zones (a new object, CR 400.7) isn't new; cards that didn't exist in [before] (tokens, copies) were hidden
     * from nobody and aren't counted. Works on a copy: [before] isn't changed. A host uses it to keep a player from learning hidden cards
     * by going on (ATW's async play, I-D56) or to tell whether taking an event back would forget something seen.
     */
    fun revealed(before: GameState, changes: List<Change>, viewer: PlayerId): Set<arcanum.engine.state.CardId> =
        arcanum.engine.action.Knowledge.revealed(before, changes, viewer)

    /** [revealed] with the views the caller already has, the same result without working them out again: [beforeView] is
     * [before]'s view and [afterView] the view of [before] with [changes] made; each null: worked out here. */
    fun revealed(before: GameState, changes: List<Change>, viewer: PlayerId, beforeView: View?, afterView: View?): Set<arcanum.engine.state.CardId> =
        arcanum.engine.action.Knowledge.revealed(before, changes, viewer, beforeView, afterView)

    /**
     * The parts of [player]'s library whose order no player knows (CR 401.2), each as its cards in library order: the
     * library's unknown part (every card since the last shuffle, CR 701.24, whose place nobody learned), then each group of
     * cards put in together in a random order ([arcanum.engine.state.PlayerState.libraryPlaces]). A card whose place a player
     * knows (put on the top, the bottom or Nth from the top, looked at or revealed where it lies, put back in a chosen order)
     * or may look at now (CR 401.5, "play with the top card revealed") is in none; nor is a card the step waiting on an open
     * decision has read already (it looked at the card or moved it, and is redone from its start once answered, D29). Parts
     * of one card are left out.
     */
    fun unknownOrder(state: GameState, player: PlayerId): List<List<arcanum.engine.state.ObjectId>> =
        arcanum.engine.action.LibraryPlaces.unknownParts(state, player,
            arcanum.engine.action.Knowledge.lookable(state, Engine.view(state)).map { it.first }.toSet() +
                arcanum.engine.action.LibraryPlaces.readByOpenStep(state))

    /**
     * A host's new random order of [player]'s library where nobody knows it (D25: the host's randomness): [parts] are
     * [unknownOrder]'s parts in the same order, each with its cards in a new order, put back into that part's own places;
     * every other card stays where it is, so no player's knowledge of the library changes (the order nobody knew is
     * still unknown). A [Change.Reorder] (the cards keep their ids), or null when nothing moves. Throws
     * [IllegalArgumentException] when a part doesn't hold exactly the cards of the part it replaces.
     */
    fun reorderUnknown(state: GameState, player: PlayerId, parts: List<List<arcanum.engine.state.ObjectId>>): Change.Reorder? {
        val unknown = unknownOrder(state, player)
        require(parts.size == unknown.size && parts.zip(unknown).all { (a, b) -> a.size == b.size && a.toSet() == b.toSet() }) {
            "Each part is a new order of the same part of the library"
        }
        val library = state.player(player).library
        val after = library.toMutableList()
        parts.zip(unknown).forEach { (order, part) ->
            val places = part.map { library.indexOf(it) }
            places.forEachIndexed { i, place -> after[place] = order[i] }
        }
        return if (after == library) null else Change.Reorder(arcanum.api.Zone.Library, player, library, after)
    }

    /**
     * Whether [a] and [b] are the same game position: equal in everything but the tagged events they keep, the timestamp counter
     * and the order of the libraries (the host's randomness, D25). Everything else the engine decides with (the objects, zones,
     * Stack, effects, turn, priority, statuses, the open decision, its bookkeeping, the input count) is compared: a host that
     * keeps a decision asked on one state for another asks this.
     */
    fun samePosition(a: GameState, b: GameState): Boolean = a.samePosition(b)
}
