package arcanum.engine.action

import arcanum.api.CardDefinition
import arcanum.api.Mechanic
import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Recorder
import arcanum.engine.state.CardId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.view.View

/**
 * The viewer-knowledge rule (D60, spec §7.0 "Hidden information"): the state is one viewer's view
 * ([GameState.viewer]; null: full information). Whenever a hidden card's identity matters or becomes visible to the
 * viewer, the host fills exactly those cards in ([Act.fill]); whenever cards become hidden from the viewer again, the
 * engine forgets them ([forgets]; a shuffle or a random order also gives them new ids from the host, CR 701.24a, D38).
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

    /** Whether the state's viewer sees [o] where it is: a public card, or one the viewer may see. With full information
     * only public cards count: the host knows the rest and fills them only when their identity matters. */
    fun viewerSees(state: GameState, o: GameObject): Boolean = public(o) || state.viewer?.let { mayKnow(o, it) } == true

    /** Whether the viewer must forget the known [o] where it now is (D60): put into a hand the viewer can't see, or face
     * down where the viewer can't look. A card put into a library stays known until the library is shuffled. */
    fun forgets(state: GameState, o: GameObject): Boolean {
        val viewer = state.viewer ?: return false
        if (o.unknown) return false
        return (o.zone == Zone.Hand && o.owner != viewer) || (o.faceDown && !mayKnow(o, viewer))
    }

    /** [o] with its identity forgotten. */
    fun forgotten(o: GameObject): GameObject = o.copy(definition = null)

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

    /** [arcanum.engine.Engine.revealed]. */
    fun revealed(before: GameState, changes: List<Change>, viewer: PlayerId): Set<CardId> {
        val known = visible(before, arcanum.engine.Engine.view(before), viewer).mapNotNull { (it as? ObjectId.Card)?.card }.toSet()
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
                is Change.Filled -> see(plain.after.id)
                else -> {}
            }
        }
        visible(state, arcanum.engine.Engine.view(state), viewer).forEach { id -> state.obj(id)?.let { shown += it.card } }
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

    /**
     * Before priority (with the state-based actions, CR 117.5): the cards the viewer may look at any time ("you may look
     * at the top card of your library any time", CR 401.5) or that are revealed to everyone are filled in, so the
     * viewer's options see them; with full information, those any player may look at.
     */
    fun fillLookable(act: Act) {
        val state = act.state
        lookable(state, act.view()).filter { (id, looker) -> state.obj(id)?.unknown == true && (looker == null || state.viewer == null || looker == state.viewer) }
            .groupBy { it.second }.forEach { (looker, cards) ->
                val ids = cards.map { it.first }.distinct()
                act.fill(looker ?: state.objOrThrow(ids.first()).owner, null, ids, "may look at any time", public = looker == null, shown = false)
            }
    }

    /**
     * Cards about to be seen by everyone as they move (D60): put into a public zone face up, or a face-down permanent
     * leaving the battlefield or a face-down spell leaving the Stack other than onto the battlefield (revealed, CR
     * 708.9). Filled before replacements apply, so replacements and triggers see them (madness of a discarded card,
     * "whenever a land card is put into your graveyard").
     */
    fun fillRevealedByMoves(act: Act, moves: List<PlannedMove>) {
        val ids = moves.filter { m ->
            val o = act.obj(m.id) ?: return@filter false
            val revealed = o.faceDown && (o.zone == Zone.Battlefield || (o.zone == Zone.Stack && m.to != Zone.Battlefield))
            o.unknown && (revealed || (m.to in PUBLIC_ZONES && !m.faceDown))
        }.map { it.id }.distinct()
        if (ids.isNotEmpty()) act.fill(act.state.objOrThrow(ids.first()).owner, null, ids, "becomes public", public = true)
    }

    /**
     * D60, D31: the [hidden] cards were filled in only to work a resolution out as in another player's view
     * ([arcanum.engine.decision.Decision.OtherView]). The changes made since [mark] are made again without their
     * identities, in their characteristics and definitions everywhere in the changes, until the viewer sees one (it is
     * put where the viewer may see it, or revealed): it is filled in just before, as a host fill would have done.
     * Triggers were found on the changes as they were made, as with full information. Returns the cards still hidden.
     */
    fun forgetHidden(recorder: Recorder, mark: Int, hidden: Map<CardId, CardDefinition>): Set<CardId> {
        if (hidden.isEmpty()) return emptySet()
        val made = recorder.changes.subList(mark, recorder.changes.size).toList()
        recorder.rollback(mark)
        val full = recorder.state.deepCopy()
        val still = hidden.keys.toMutableSet()
        made.forEach { c ->
            full.apply(c)
            // The fill only the other player saw
            if (c is Change.Filled && c.before.unknown && c.after.card in still) return@forEach
            val revealed = ((c as? Change.Happened)?.event as? GameEvent.Revealed)?.ids.orEmpty().mapNotNull { (it as? ObjectId.Card)?.card }
            still.filter { card -> card in revealed || full.current(card)?.let { viewerSees(full, it) } == true }.forEach { card ->
                still -= card
                val o = recorder.state.current(card)
                if (o != null && o.unknown) recorder.append(Change.Filled(o, o.copy(definition = hidden.getValue(card))))
            }
            val seen = if (still.isEmpty()) c else withoutIdentities(c, still)
            if (seen is Change.Forgotten && seen.before == seen.after) return@forEach
            recorder.append(seen)
        }
        return still
    }

    /** [change] with the definitions and characteristics of [cards]' objects left out (unknown, D60). */
    private fun withoutIdentities(change: Change, cards: Set<CardId>): Change {
        val prefixes = cards.map { "c:${it.value}#" }
        fun hidden(id: JsonElement?): Boolean =
            (id as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { s -> prefixes.any { s.startsWith(it) } } == true
        fun walk(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> {
                val hide = hidden(e["id"])
                JsonObject(e.mapValues { (k, v) -> if (hide && (k == "definition" || k == "characteristics")) JsonNull else walk(v) })
            }
            is JsonArray -> JsonArray(e.map(::walk))
            else -> e
        }
        return Json.decodeFromJsonElement(Change.serializer(), walk(Json.encodeToJsonElement(Change.serializer(), change)))
    }

    /** After replacements: cards the viewer will see where they go (their own hand, face down under their control) or
     * still to be seen by everyone, filled in before the zone change (D60). */
    fun fillSeenAfterMoves(act: Act, moves: List<PlannedMove>) {
        fillRevealedByMoves(act, moves)
        val viewer = act.state.viewer ?: return
        val ids = moves.filter { m ->
            val o = act.obj(m.id) ?: return@filter false
            val after = o.copy(zone = m.to, faceDown = m.faceDown, controller = if (m.to == Zone.Battlefield || m.to == Zone.Stack) m.controller ?: o.owner else null)
            o.unknown && mayKnow(after, viewer)
        }.map { it.id }.distinct()
        if (ids.isNotEmpty()) act.fill(viewer, null, ids, "seen")
    }
}
