package arcanum.engine

import arcanum.engine.change.Change
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.setup.GameResult
import arcanum.engine.setup.Seat
import arcanum.engine.setup.Setup
import arcanum.engine.setup.result
import arcanum.engine.view.Layers
import arcanum.engine.view.View
import kotlinx.serialization.Serializable

/**
 * The rules engine's API (docs/specs/arcanum-automatum.md §7.0, D56–D61). Stateless: everything it needs is in the
 * [GameState] it is given, and everything it decides comes back as [Change]s (P2). It never writes the state it
 * reads; callers apply the changes with [GameState.apply].
 */
object Engine {
    /**
     * The version of what the engine carries out itself rather than leaving to the players: raised whenever an effect,
     * keyword or step it used to hand to the players (a [Decision.Manual], `KeywordMeanings.isManual`) becomes
     * automatic, or the other way round. A host that keeps per-card "fully supported" results worked out with this
     * engine keeps them per this version (and the parser's), so a new engine works them out again.
     */
    const val SUPPORT_VERSION: Int = 9

    /** The parts of [definition] this engine leaves to the players, one short line each (empty: it plays all of the card
     * by the rules): text the parser couldn't read, `Manual` steps and cost parts, keywords done by hand, and the shapes
     * it can't finish at run time (a cost paid on resolution it can't work out, a mana ability step that asks something,
     * a special action doing more than turning itself face up, unlocking its door or moving itself). Follows
     * [SUPPORT_VERSION]: a host keeping per-card results keeps them per that version. */
    fun manualParts(definition: arcanum.api.CardDefinition): List<String> = arcanum.engine.view.ManualParts.of(definition)

    /** The room of dungeon [id] its owner's venture marker is on (CR 309.4), by index into its rooms
     * ([arcanum.api.FaceDefinition.rooms]); null when it has no marker (not a dungeon in the command zone). */
    fun ventureRoom(state: GameState, id: arcanum.engine.state.ObjectId): Int? =
        state.objects[id]?.takeIf { it.zone == arcanum.api.Zone.Command }?.let(arcanum.engine.dungeon.Dungeons::room)

    /** The characteristics of every object after continuous effects (CR 613), the rules that apply to objects and
     * players, and the game rules in force (P3). A pure function of [state]. */
    fun view(state: GameState): View = Layers.view(state)

    /**
     * What continuous effects tell object [id] it can't or must do beyond its characteristics ("can't block", "can't be
     * blocked by creatures with flying", "attacks each combat if able", "doesn't untap", "can't be countered"), a host's
     * Inspect beside [explain]: each with plain words, what made it and how long it lasts. Read from [view], which the host
     * already has, so it works nothing out again. What the object's own printed text says and what a keyword means (its
     * keyword is listed instead) are left out. Empty for an object nothing restricts or that isn't there.
     */
    fun restrictions(state: GameState, view: View, id: arcanum.engine.state.ObjectId): List<arcanum.engine.view.AppliedRestriction> =
        arcanum.engine.view.Restrictions.of(state, view, id)

    /** [player]'s speed (CR 702.179): 1 to 4, 0 for a player with no speed. */
    fun speed(state: GameState, player: PlayerId): Int = arcanum.engine.turn.Speed.of(state, player)

    /** How many times the Ring has tempted [player] (CR 701.54c); 0: never. Its abilities stop growing at four. */
    fun ringTemptations(state: GameState, player: PlayerId): Int = arcanum.engine.ring.TheRing.temptations(state, player)

    /** How many dungeons [player] has completed this game (CR 309.6). */
    fun dungeonsCompleted(state: GameState, player: PlayerId): Int = arcanum.engine.dungeon.Dungeons.completed(state, player)

    /** Whether it is day or night (CR 731); null while it is neither, as a game starts. */
    fun dayOrNight(state: GameState): arcanum.engine.view.DayOrNight? = when (arcanum.engine.turn.DayNight.of(state)) {
        arcanum.engine.query.Evaluator.DAY -> arcanum.engine.view.DayOrNight.Day
        arcanum.engine.query.Evaluator.NIGHT -> arcanum.engine.view.DayOrNight.Night
        else -> null
    }

    /** The level a Class [id] has gained (CR 716.2, its level abilities): at least 1 for a Class on the battlefield; null for
     * any other object or none. */
    fun classLevel(state: GameState, id: arcanum.engine.state.ObjectId): Int? = state.obj(id)?.let { o ->
        if (arcanum.api.Subtype("Class") !in o.definition?.faces?.getOrNull(o.face)?.subtypes.orEmpty() || o.zone != arcanum.api.Zone.Battlefield) null
        else ((o.memory[arcanum.engine.query.Evaluator.CLASS_LEVEL] as? arcanum.engine.state.NamedValue.Number)?.value ?: 1).coerceAtLeast(1)
    }

    /**
     * Where object [id]'s characteristics come from (spec §7.1, a host's Inspect: "2/2 printed, +1/+1 from an anthem,
     * +3/+3 until end of turn"): its values after layer 1, then each continuous effect that changed them in the order
     * applied (CR 613), with what made it. Works the layers out afresh (one [view]'s cost). Null when [id] has no
     * characteristics (an unknown object, D60, or no such object).
     */
    fun explain(state: GameState, id: arcanum.engine.state.ObjectId): arcanum.engine.view.Explanation? = Layers.explain(state, id)

    /**
     * [explain] for several objects at once (a host inspecting a group of cards), from one run of the layers: the
     * explanation of each of [ids] that has characteristics, by id; the others are left out.
     */
    fun explain(state: GameState, ids: Collection<arcanum.engine.state.ObjectId>): Map<arcanum.engine.state.ObjectId, arcanum.engine.view.Explanation> =
        Layers.explain(state, ids)

    /**
     * Whether the open cast or activation can still be finished from here, as far as the search can tell (castability §1): false
     * only when it has shown there is no way; a search stopped by a bound, or a payment that may exist, says true. What
     * [options] lists a cast by, for a host working out a choice it made (a way with its extras).
     */
    fun finishable(state: GameState): Boolean = Options.finishable(state)

    /** [finishable] once [answer] is given to the open decision: false when it is refused or leaves no way. */
    fun finishableWith(state: GameState, answer: arcanum.engine.decision.Answer): Boolean = Options.finishableWith(state, answer)

    /**
     * What may be picked next in the open choice of targets or objects with [picked] picked so far (castability §3, CR 601.2c,
     * 601.2f): a candidate is listed when some finished selection including it and [picked] can be paid and is legal, not when the
     * selection is payable right now, since a cost can fall as more are picked. A search bound keeps a candidate listed;
     * [Selection.done] is for [picked] itself. Blocking: run it off the main thread. Picked candidates stay listed (they can be
     * taken back).
     */
    fun selection(state: GameState, picked: List<arcanum.engine.state.TargetRef>): Selection = Options.selection(state, picked)

    /**
     * What the open cast or activation can come to (castability §2): the smallest cost among the ways through its open choices,
     * whether it can have several, Autotap's suggestion for that smallest, and the answers leading to it. Blocking.
     */
    fun castCosts(state: GameState): CastCosts = Options.castCosts(state)

    /**
     * The open [arcanum.engine.decision.Decision.ManaPayment] paid around a player's own choices (STK-024): [pinned] uses stay,
     * [removed] uses aren't used, Autotap's order fills in the rest; empty when the rest can't be filled.
     */
    fun fillPayment(state: GameState, pinned: List<arcanum.engine.decision.ManaUse>, removed: List<arcanum.engine.decision.ManaUse>): List<arcanum.engine.decision.ManaUse> =
        Options.fillPayment(state, pinned, removed)

    /** What [player] may do now: play a land, cast, activate, pass, declare, or answer the open decision. */
    fun options(state: GameState, player: PlayerId): List<Input> = Options.of(state, player)

    /** [options] with [view], [state]'s view ([view] of [state] as it is now) when the caller already has it: the same
     * list without working the layers out again for the search. A view of another state gives wrong results. */
    fun options(state: GameState, player: PlayerId, view: View?): List<Input> = Options.of(state, player, view)

    /**
     * Why [perform] would refuse [input] at once, worked out without performing it (no copy of [state] is made; with
     * [view], [state]'s view when the caller has it, the layers aren't worked out either): for a land play, a cast or an
     * activation (special actions included) the same reason, word for word, as the [IllegalArgumentException] [perform]
     * throws before doing anything (the game is over, a decision is open, or it can't be begun: priority, timing, uses
     * left this turn, what the card or ability allows, CR 116, 117.1, 305, 601.2, 602.5, 606.3). Null when it may be
     * begun ([perform] may still refuse it later, as it is announced: a target or a cost it can't have), for a card
     * [state] doesn't know (a viewer's state, D60: [perform] first asks for it) and for any other input. A host greys
     * an action out with this reason instead of trying it.
     */
    fun refusal(state: GameState, input: Input, view: View? = null): String? = Perform.refusal(state, input, view)

    /**
     * Performs [input] on a copy of [state] and returns what changed, up to the next decision (D58): after every input
     * the engine applies state-based actions, puts triggered abilities on the Stack and gives priority (P5). While
     * [GameState.pending] is set, only its [Input.Answer] (or [Input.Cancel] for a cast) is accepted. A call that goes
     * round too often (an endless chain of mandatory triggers) stops with the "possible loop" choice
     * ([arcanum.engine.decision.LoopStop.Cap]) instead of hanging: continue, or end the game as a draw (CR 104.4b).
     *
     * [trace] gets one [TraceEntry] for the call (the input, what it did or why it was refused, how long it took) for a
     * host's logs and bug reports; the default records nothing and costs nothing.
     *
     * The [Outcome] carries the view of the state after its changes when the engine has it ([Outcome.view]).
     */
    fun perform(state: GameState, input: Input, trace: EngineTrace = EngineTrace.None): Outcome = Perform.run(state, input, trace = trace)

    /**
     * [perform] with [view], [state]'s view ([view] of [state] as it is now) when the caller already has it, so the engine
     * doesn't work it out again (null: worked out when needed, as [perform]). The same outcome; a view of another state
     * gives wrong results.
     */
    fun perform(state: GameState, input: Input, view: View?, trace: EngineTrace = EngineTrace.None): Outcome =
        Perform.run(state, input, view = view, trace = trace)

    /**
     * A game before it begins (CR 103): [seats] in seat order with their decks as libraries in the order given and their
     * sideboards outside the game, each at [startingLife] (CR 103.4). [skipFirstDraw]: the starting player skips the draw
     * of their first turn (CR 103.8a: two players; 103.8c: nobody in other multiplayer games). Nothing random happens
     * here: [Input.StartGame] then chooses the starting player, shuffles and deals opening hands (D25).
     */
    fun newGame(seats: List<Seat>, startingLife: Int = 20, skipFirstDraw: Boolean = seats.size == 2): GameState =
        Setup.newGame(seats, startingLife, skipFirstDraw)

    /**
     * The objects whose identity [player] may know now (D60, spec §7.0 "Hidden information"): public cards, their own
     * hand and cards outside the game, face-down cards they control, and cards an ability lets them look at any time
     * ("you may look at the top card of your library any time", CR 401.5). A host shows a player what [state] knows
     * of these and nothing of the rest: of the others' cards, of [GameState.pending] decisions of other players over
     * cards not among these, and of [arcanum.engine.change.Change.Filled]s that aren't public. While [player] controls
     * another player ([decider]), also what that player may see of objects in the game (CR 723.4).
     */
    fun visible(state: GameState, player: PlayerId): Set<arcanum.engine.state.ObjectId> =
        arcanum.engine.action.Knowledge.visible(state, view(state), player)

    /** [visible] with [view], [state]'s view ([view] of [state] as it is now) that the caller already has: the same
     * result without working the layers out again. */
    fun visible(state: GameState, player: PlayerId, view: View): Set<arcanum.engine.state.ObjectId> =
        arcanum.engine.action.Knowledge.visible(state, view, player)

    /**
     * For a host whose [state] waits on a [arcanum.engine.decision.Decision.OtherView] (D60, D31) and that doesn't keep
     * that player's own state: the decision the player faces next in their view, given the identities [filled] of the
     * decision's objects, with the cards only they may see (their options name them). Null when the answers so far are
     * enough, or more identities are needed (the engine then asks again). Shows nothing to anyone else; changes
     * nothing.
     */
    fun otherViewDecision(state: GameState, filled: List<arcanum.engine.decision.FilledObject>): arcanum.engine.decision.Decision? =
        arcanum.engine.resolution.OtherView.next(state, filled)

    /**
     * Whether a host passes priority for [player] now (I-D3, I-D18): they hold it, nothing is pending, and [options]
     * offers them nothing but passing (mana abilities aside). With their "Skip when I have no choice" setting
     * ([arcanum.engine.state.PlayerState.skipWhenNoChoice]) on, that is enough; off, only when it doesn't depend on cards
     * only they may see (an empty hand and no face-down permanent: an instant they can't afford is theirs to pass with).
     * False while such cards are unknown in [state] (another player's view, D60): their own view decides. The engine never
     * passes for anyone by itself; a host performs [Input.PassPriority] for each player this says, one at a time. While
     * another player controls [player] (CR 723), the controller's setting counts ([decider]).
     */
    fun shouldPassFor(state: GameState, player: PlayerId): Boolean = arcanum.engine.turn.AutoPass.shouldPass(state, player)

    /**
     * [shouldPassFor] two ways from one search of [options] (a host with its own "pass when I have no choice" asks both on
     * the same state): with the deciding player's setting as it is ([PassCheck.asSet], the same as [shouldPassFor]) and
     * as if [player]'s own setting were on ([PassCheck.ownSkipOn]).
     */
    fun passCheck(state: GameState, player: PlayerId): PassCheck = arcanum.engine.turn.AutoPass.check(state, player)

    /** [passCheck] with [view], [state]'s view ([view] of [state] as it is now) when the caller already has it: the same
     * result without working the layers out again for the search. A view of another state gives wrong results. */
    fun passCheck(state: GameState, player: PlayerId, view: View?): PassCheck = arcanum.engine.turn.AutoPass.check(state, player, view)

    /** [shouldPassFor] with [view], as [passCheck]'s. */
    fun shouldPassFor(state: GameState, player: PlayerId, view: View?): Boolean = arcanum.engine.turn.AutoPass.shouldPass(state, player, view)

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
     * between, it performs [Input.MandatoryLoop]. Serialises the state once (about a [view]'s cost on a large board).
     */
    fun loopKey(state: GameState): arcanum.engine.turn.LoopKey = arcanum.engine.turn.Loops.key(state)

    /**
     * The cards whose identity [changes] (made on [before] in order, such as an [Outcome]'s) show [viewer] at some moment
     * that [visible] didn't give them in [before]: put where they may see them (a hand they own, a public zone face up),
     * turned face up or revealed (CR 701.20, 708.9), looked at ([Change.Shown]: a search, scry, look, the cards of a choice
     * among hidden cards), or that they may look at once the changes are made (CR 401.5). Compared by card, so a card
     * changing zones (a new object, CR 400.7) isn't new; cards that didn't exist in [before] (tokens, copies) were hidden
     * from nobody and aren't counted. For a full-information state ([GameState.viewer] null; a viewer's state says this
     * with its fills). Works on a copy: [before] isn't changed. A host uses it to keep a player from learning hidden cards
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
     * or may look at now (CR 401.5, "play with the top card revealed") is in none. Parts of one card are left out.
     */
    fun unknownOrder(state: GameState, player: PlayerId): List<List<arcanum.engine.state.ObjectId>> =
        arcanum.engine.action.LibraryPlaces.unknownParts(state, player,
            arcanum.engine.action.Knowledge.lookable(state, view(state)).map { it.first }.toSet())

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

    /** Whether declaring [blocks] has costs to block, which the defending player determines and pays (CR 509.1d). */
    fun blockCosts(state: GameState, blocks: List<arcanum.engine.input.Block>): Boolean =
        arcanum.engine.combat.CombatRules.blockCosts(arcanum.engine.casting.Snapshot(state), blocks).isNotEmpty()

    /** How the game ended (CR 104): the winner or a draw; null while it goes on. */
    fun result(state: GameState): GameResult? = state.result

    /**
     * For a host's tests and measurements (changes no result): called on the calling thread each time the layers are
     * worked out in full, whether for [view], [explain] or inside another call ([perform], [options], [visible], …); a
     * view the engine kept or was given isn't counted. Null (the default): nothing is called. One for the whole process:
     * set it only in tests or while measuring.
     */
    @kotlin.concurrent.Volatile
    var layersWorkedOut: (() -> Unit)? = null
}

/**
 * The result of [Engine.perform]: [changes] to apply in order (one host batch, D58), and the decision the engine waits
 * for, if any. [pending] is also set in the state by the last change ([Change.PendingSet]).
 */
@Serializable
data class Outcome(val changes: List<Change>, val pending: Pending?) {
    /**
     * The view ([Engine.view]) of the state after [changes] when the engine had it at the end of the call (its working
     * copy's), so a host that applies [changes] needn't work it out again; null when it hadn't (then work it out). Not
     * part of equality, not serialised, and dropped by [copy].
     */
    @kotlinx.serialization.Transient
    var view: View? = null
        internal set
}

/** [Engine.passCheck]: whether a host passes priority for a player with the deciding player's "Skip when I have no
 * choice" as it is ([asSet]), and with the player's own setting on ([ownSkipOn]). */
data class PassCheck(val asSet: Boolean, val ownSkipOn: Boolean) {
    companion object {
        /** Neither: the player may do something, or doesn't hold priority. */
        val NONE = PassCheck(asSet = false, ownSkipOn = false)
    }
}

/** A [decision] and the [continuation] that goes on once it is answered (D58). The host stores both as they are. */
@Serializable
data class Pending(val decision: Decision, val continuation: Continuation)

/** What [Engine.selection] says of an open choice of targets or objects: the [candidates] that may be picked with what is picked
 * so far, and whether the picks so far are a selection that is [done]: legal and able to be paid. */
data class Selection(val candidates: List<arcanum.engine.state.TargetRef>, val done: Boolean)

/**
 * What [Engine.castCosts] says of an open cast or activation: [least] the lowest mana cost among the ways through its open choices
 * that can be paid now (the lowest of all when none can; null when it costs no mana), [several] whether it can have other costs or
 * the search couldn't finish, [asks] whether anything is asked before the payment, [castable] the same answer as
 * [Engine.finishable], [suggestion] Autotap's sources for [least], [answers] the answers leading to [least].
 */
data class CastCosts(
    val least: arcanum.api.ManaCost?, val several: Boolean, val asks: Boolean, val castable: Boolean,
    val suggestion: List<arcanum.engine.decision.ManaUse>, val answers: List<arcanum.engine.decision.Answer>,
)
