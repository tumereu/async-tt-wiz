package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectStatus
import arcanum.engine.state.PlayerId
import arcanum.engine.view.View

/**
 * ZON-079 (owner, 2026-11-12): the cards outside a player's hand that their hand row shows: those a permission lets them cast or
 * play from where they are, castable now or not ([permitted]: the engine's [ObjectStatus.Playable]), and only those the player
 * may see (I-D27, [shown]).
 */
object OffZoneCards {
    /**
     * The cards [view] (of [state]) marks [ObjectStatus.Playable] for [viewer] that they may see: by ATW card id, graveyards first,
     * then exile, the libraries and the command zone, each in its order.
     */
    fun permitted(state: GameState, view: View, viewer: String): List<String> {
        val player = PlayerId(viewer)
        val held = view.objects.values.filter { o ->
            o.id is ObjectId.Card && o.statuses.any { status -> status is ObjectStatus.Playable && status.by.any { it.player == player } }
        }.map { it.id }
        if (held.isEmpty()) return emptyList()
        val order = ZONES.flatMap { zone ->
            when (zone) {
                Zone.Exile -> state.exile
                Zone.Command -> state.command
                Zone.Graveyard -> state.players.flatMap { it.graveyard }
                else -> state.players.flatMap { it.library }
            }
        }
        val rank = order.withIndex().associate { it.value to it.index }
        return shown(state, view, player, held.sortedBy { rank[it] ?: Int.MAX_VALUE }).map(::atw)
    }

    /**
     * Of [candidates], cards of [player]'s game outside their hand, those they may see (I-D27): a card face up in a graveyard or
     * exile at once, any other as [Host.visible] says ([view]: [state]'s, worked out here when null and needed); in the hand
     * row's order of zones, each zone's cards in [candidates]' order.
     */
    fun shown(state: GameState, view: View?, player: PlayerId, candidates: List<ObjectId>): List<ObjectId> {
        val seen by lazy { Host.visible(state, player, view ?: Engine.view(state)) }
        return candidates.distinct().filter { id ->
            val o = state.obj(id) ?: return@filter false
            o.zone != Zone.Hand && ((o.zone == Zone.Graveyard || o.zone == Zone.Exile) && !o.faceDown || id in seen)
        }.sortedBy { id -> (state.obj(id)?.zone?.let { ZONES.indexOf(it) } ?: -1).let { if (it < 0) ZONES.size else it } }
    }

    /** The ATW card id of the card [id]. */
    fun atw(id: ObjectId): String = (id as ObjectId.Card).card.value

    /** The zones off-zone cards come from, in the hand row's order. */
    private val ZONES = listOf(Zone.Graveyard, Zone.Exile, Zone.Library, Zone.Command)
}
