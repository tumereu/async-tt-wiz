package arcanum.engine.action

import arcanum.api.Mechanic
import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.state.CardId
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.view.View

/**
 * What each player may know (spec §7.0 "Hidden information"). The state always has full information; this says what a
 * host may show each player of it ([arcanum.engine.Host.visible], [arcanum.engine.Host.revealed]), and a player looking
 * at hidden cards is said by a [Change.Shown] ([Act.show]).
 */
internal object Knowledge {
    private val PUBLIC_ZONES = setOf(Zone.Battlefield, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command)

    /** Everyone may see [o] where it is (CR 400.2): face up in a public zone. */
    fun public(o: GameObject): Boolean = o.zone in PUBLIC_ZONES && !o.faceDown

    /** Whether [player] may see what [o] is where it is: a public card, a card in their own hand or outside the game
     * (CR 402.3), a face-down permanent or spell they control (CR 708.5), a card they foretold (CR 702.143a); never a card in a
     * library or otherwise exiled face down (CR 406.3: only where an ability lets them look, [lookable]). */
    fun mayKnow(o: GameObject, player: PlayerId): Boolean = when {
        public(o) -> true
        o.zone == Zone.Hand || o.zone == Zone.Outside -> o.owner == player
        o.faceDown && (o.zone == Zone.Battlefield || o.zone == Zone.Stack) -> (o.controller ?: o.owner) == player
        // CR 702.106b: a face-down conspiracy in the command zone, its owner's
        o.faceDown && o.zone == Zone.Command -> o.owner == player
        // CR 702.143a: its owner may look at a foretold card
        o.faceDown && arcanum.engine.foretell.Foretell.mayLook(o, player) -> true
        else -> false
    }

    /** Objects [player] may see the identity of now: public ones, their own hand and cards outside the game, face-down
     * cards they may look at, and cards a "you may look at" ability lets them look at (CR 401.5 cards such as "look at
     * the top card of your library any time"); revealed ones ("play with the top card revealed") for everyone. While
     * [player] controls another player, also what that player may see of objects in the game (CR 723.4: not their cards
     * outside the game). */
    fun visible(state: GameState, view: View, player: PlayerId): Set<ObjectId> {
        val own = ownVisible(state, view, player)
        val controlled = arcanum.engine.turn.Control.controlledBy(state, player) ?: return own
        return own + ownVisible(state, view, controlled).filter { state.obj(it)?.zone != Zone.Outside }
    }

    private fun ownVisible(state: GameState, view: View, player: PlayerId): Set<ObjectId> =
        state.objects.values.filter { mayKnow(it, player) }.map { it.id }.toSet() +
            lookable(state, view).filter { (_, looker) -> looker == null || looker == player }.map { it.first }

    /** Objects only [player] may see (I-D18): visible to them and not to some other player still in the game (their
     * hand and cards outside the game, face-down cards they control, cards only they may look at). Library cards nobody
     * may look at aren't among them. What a controlling player sees of them (CR 723.4) doesn't count. */
    fun privateTo(state: GameState, view: View, player: PlayerId): Set<ObjectId> {
        val others = state.players.filter { it.id != player && !it.lost }.map { ownVisible(state, view, it.id) }
        return ownVisible(state, view, player).filter { id -> others.any { id !in it } }.toSet()
    }

    /** [arcanum.engine.Host.revealed]. */
    fun revealed(before: GameState, changes: List<Change>, viewer: PlayerId, beforeView: View? = null, afterView: View? = null): Set<CardId> {
        val known = visible(before, beforeView ?: arcanum.engine.Engine.view(before), viewer).mapNotNull { (it as? ObjectId.Card)?.card }.toSet()
        val existing = before.objects.values.map { it.card }.toSet()
        val state = before.deepCopy()
        val shown = mutableSetOf<CardId>()
        fun see(id: ObjectId) { state.obj(id)?.let { o -> if (mayKnow(o, viewer)) shown += o.card } }
        for (change in changes) {
            state.apply(change)
            val plain = (change as? Change.Reverted)?.change?.inverse() ?: change
            when (plain) {
                is Change.Shown -> if (plain.to == null || plain.to == viewer) plain.ids.forEach { id -> state.obj(id)?.let { shown += it.card } }
                is Change.Happened -> (plain.event as? GameEvent.Revealed)?.ids?.forEach { id -> state.obj(id)?.let { shown += it.card } }
                is Change.ZoneChange -> plain.moves.forEach { see(it.after.id) }
                is Change.Status -> see(plain.id)
                is Change.Face -> see(plain.id)
                is Change.ControlStart -> see(plain.id)
                is Change.ObjectUpdated -> see(plain.after.id)
                else -> {}
            }
        }
        visible(state, afterView ?: arcanum.engine.Engine.view(state), viewer).forEach { id -> state.obj(id)?.let { shown += it.card } }
        return shown.filterTo(mutableSetOf()) { it in existing && it !in known }
    }

    /** Cards a "you may look at … any time" ability lets a player look at (CR 401.5), with who may look (null: revealed
     * to everyone): applied to a player ("the top card of your library") or to the cards themselves. */
    fun lookable(state: GameState, view: View): List<Pair<ObjectId, PlayerId?>> {
        val board = arcanum.engine.query.ViewBoard(state, view)
        val toPlayers = view.players.values.flatMap { p ->
            p.mechanics.flatMap { applied ->
                val m = applied.mechanic as? Mechanic.MayLookAt ?: return@flatMap emptyList()
                val e = arcanum.engine.query.Evaluator(board, arcanum.engine.query.Context(applied.controller, applied.source, applied.names, affectedPlayer = p.id))
                e.objects(m.objects).map { it to (if (m.reveal) null else p.id) }
            }
        }
        val toObjects = view.objects.values.flatMap { o ->
            o.mechanics.mapNotNull { applied ->
                val m = applied.mechanic as? Mechanic.MayLookAt ?: return@mapNotNull null
                o.id to (if (m.reveal) null else applied.controller)
            }
        }
        return (toPlayers + toObjects).filter { state.obj(it.first) != null }.distinct()
    }
}
