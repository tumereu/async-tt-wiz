package arcanum.engine

import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.view.Layers
import arcanum.engine.view.View

/**
 * For a host's Inspect and the rules state it shows: where an object's characteristics come from, what continuous
 * effects tell it it can't or must do, what an object that no longer exists last was, and the values the engine keeps
 * for a player, an object or the game (speed, the Ring, dungeons, day and night, a Class's level, a venture marker).
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
        if (arcanum.api.Subtype("Class") !in o.definition.faces.getOrNull(o.face)?.subtypes.orEmpty() || o.zone != arcanum.api.Zone.Battlefield) null
        else ((o.memory[arcanum.engine.query.Evaluator.CLASS_LEVEL] as? arcanum.engine.state.NamedValue.Number)?.value ?: 1).coerceAtLeast(1)
    }

    /** The room of dungeon [id] its owner's venture marker is on (CR 309.4), by index into its rooms
     * ([arcanum.api.FaceDefinition.rooms]); null when it has no marker (not a dungeon in the command zone). */
    fun ventureRoom(state: GameState, id: arcanum.engine.state.ObjectId): Int? =
        state.objects[id]?.takeIf { it.zone == arcanum.api.Zone.Command }?.let(arcanum.engine.dungeon.Dungeons::room)
}
