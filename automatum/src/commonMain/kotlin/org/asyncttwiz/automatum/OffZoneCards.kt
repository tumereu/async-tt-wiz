package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectStatus
import arcanum.engine.state.PlayerId
import arcanum.engine.view.View

/** ZON-136: one card another player may use from outside their hand ([OffZoneCards.playableBy]): [cardId] when the viewer may
 * see it, else null; [zone] its zone's name in lower case ("graveyard", "exile", "library", "command"). */
@kotlinx.serialization.Serializable
data class OtherPlayable(val cardId: String?, val zone: String)

/**
 * ZON-079 (owner, 2026-11-12): the cards outside a player's hand that their hand row shows: those a permission lets them cast or
 * play from where they are, or with an activated ability or special action they may use there (unearth, embalm), usable now
 * or not ([permitted]: the engine's [ObjectStatus.Playable]), and only those the player
 * may see (I-D27, [shown]).
 */
object OffZoneCards {
    /**
     * The cards [view] (of [state]) marks [ObjectStatus.Playable] for [viewer] that they may see: by ATW card id, graveyards first,
     * then exile, the libraries and the command zone, each in its order.
     */
    fun permitted(state: GameState, view: View, viewer: String): List<String> =
        shown(state, view, PlayerId(viewer), permittedIds(state, view, PlayerId(viewer))).map(::atw)

    /** The cards [view] marks [ObjectStatus.Playable] for [player] (but prepare spells' copies), in the hand row's order. */
    private fun permittedIds(state: GameState, view: View, player: PlayerId): List<ObjectId> {
        val held = view.objects.values.filter { o ->
            o.id is ObjectId.Card && o.statuses.any { status -> status is ObjectStatus.Playable && status.by.any { it.player == player } } &&
                // SYS-207 (owner playtest 2026-11-19, N24): a prepared permanent's spell is offered on the permanent instead
                !preparedSpell(state, o.id)
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
        return held.sortedBy { rank[it] ?: Int.MAX_VALUE }.sortedBy { id ->
            (state.obj(id)?.zone?.let { ZONES.indexOf(it) } ?: -1).let { if (it < 0) ZONES.size else it } }
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

    /**
     * ZON-136 (owner playtest 2026-11-19, N17): what [viewer] may know of the cards another [player]'s hand row holds ([permitted]
     * for them, in its order): each card [viewer] may see by its ATW id, any other only by its zone ([OtherPlayable.cardId] null:
     * the top card of their library, a face-down card in exile), so nothing hidden is told.
     */
    fun playableBy(state: GameState, view: View, player: String, viewer: String): List<OtherPlayable> {
        val held = permittedIds(state, view, PlayerId(player))
        if (held.isEmpty()) return emptyList()
        val seen = Host.visible(state, PlayerId(viewer), view)
        return held.mapNotNull { id ->
            val o = state.obj(id)?.takeIf { it.zone != Zone.Hand } ?: return@mapNotNull null
            val public = (o.zone == Zone.Graveyard || o.zone == Zone.Exile) && !o.faceDown || id in seen
            OtherPlayable(if (public) atw(id) else null, when (o.zone) {
                Zone.Graveyard -> "graveyard"
                Zone.Exile -> "exile"
                Zone.Library -> "library"
                Zone.Command -> "command"
                else -> o.zone.toString().lowercase()
            })
        }
    }

    /** SYS-068, CR 723.3c: whether [id] is a prepare spell's copy in exile (cast from its prepared permanent, [CardPlay.Kind.Prepared]). */
    fun preparedSpell(state: GameState, id: ObjectId): Boolean =
        state.obj(id)?.statuses?.any { it is ObjectStatus.PrepareSpellOf } == true

    /** The ATW card id of the card [id]. */
    fun atw(id: ObjectId): String = (id as ObjectId.Card).card.value

    /** The zones off-zone cards come from, in the hand row's order. */
    private val ZONES = listOf(Zone.Graveyard, Zone.Exile, Zone.Library, Zone.Command)
}
