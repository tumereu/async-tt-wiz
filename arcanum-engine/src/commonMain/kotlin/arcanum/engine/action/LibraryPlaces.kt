package arcanum.engine.action

import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.change.Recorder
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId

/**
 * Which library cards have a place some player knows ([arcanum.engine.state.PlayerState.libraryPlaces], CR 401.2: a
 * library's order is otherwise unknown). Followed after every change the engine records ([follow]): a card put into a
 * library at a place (the top, the bottom, Nth from the top, CR 401.4) or looked at or revealed where it lies
 * ([Change.Shown]: a scry or a look before they are put back in a chosen order) gets a known place; cards put in together in a random
 * order get one group ([group]); a shuffle (CR 701.24) makes every card it shuffled unknown (they are new objects, D38).
 */
internal object LibraryPlaces {
    /** A known exact place. */
    const val KNOWN = 0

    /** After [change] was recorded: the places it made known, or the entries it made stale, as a [Change.LibraryPlaces]. */
    fun follow(recorder: Recorder, change: Change) {
        val state = recorder.state
        val known: Map<PlayerId, List<ObjectId>> = when (change) {
            is Change.ZoneChange -> change.moves.filter { it.after.zone == Zone.Library }.groupBy({ it.after.owner }, { it.after.id })
            is Change.Shuffle -> mapOf(change.player to emptyList())
            is Change.Shown -> state.players.associate { p -> p.id to change.ids.filter { it in p.library } }.filterValues { it.isNotEmpty() }
            else -> return
        }
        known.forEach { (player, ids) -> set(recorder, player) { places -> places + ids.associateWith { KNOWN } } }
    }

    /** [ids], just put into their owners' libraries in a random order, are one group in each library: everyone knows
     * where the group is, nobody which card is where. */
    fun group(recorder: Recorder, ids: List<ObjectId>) {
        ids.groupBy { recorder.state.obj(it)?.owner }.forEach { (owner, cards) ->
            if (owner == null || cards.size < 2) return@forEach
            set(recorder, owner) { places -> places + cards.associateWith { (places.values.maxOrNull() ?: KNOWN) + 1 } }
        }
    }

    private fun set(recorder: Recorder, player: PlayerId, update: (Map<ObjectId, Int>) -> Map<ObjectId, Int>) {
        val p = recorder.state.player(player)
        val library = p.library.toSet()
        val after = update(p.libraryPlaces).filterKeys { it in library }
        if (after != p.libraryPlaces) recorder.append(Change.LibraryPlaces(player, p.libraryPlaces, after))
    }

    /**
     * The parts of [player]'s library whose order no player knows, each as its cards in library order: the unknown part,
     * then each random-order group. Cards with a known place and cards a player may look at now (CR 401.5, [lookable]) are
     * in none.
     */
    fun unknownParts(state: GameState, player: PlayerId, lookable: Set<ObjectId>): List<List<ObjectId>> {
        val p = state.player(player)
        val parts = linkedMapOf<Int, MutableList<ObjectId>>()
        for (id in p.library) {
            if (id in lookable) continue
            val place = p.libraryPlaces[id]
            if (place == KNOWN) continue
            parts.getOrPut(place ?: -1) { mutableListOf() } += id
        }
        return parts.entries.sortedBy { it.key }.map { it.value }.filter { it.size > 1 }
    }
}
