package arcanum.engine.dungeon

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Designation
import arcanum.api.DungeonRoom
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.KeywordAction
import arcanum.api.ObjectFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.action.Act
import arcanum.engine.action.Actions
import arcanum.engine.action.Cause
import arcanum.engine.action.PlannedMove
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Meaning
import arcanum.engine.decision.WaitingTrigger
import arcanum.engine.query.Context
import arcanum.engine.query.ViewBoard
import arcanum.engine.state.Bindings
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TriggerEvent
import arcanum.engine.view.View

/**
 * Dungeons (CR 309), venturing into the dungeon (CR 701.49) and the initiative (CR 726).
 *
 * A dungeon card begins outside the game, among its owner's cards there ([arcanum.engine.state.PlayerState.outside]); venturing
 * brings one into the command zone with the venture marker on its topmost room, moves the marker along an arrow (its owner choosing
 * when there are several), or, from the bottommost room, completes the dungeon and starts a new one ([venture]). The marker is the
 * dungeon's memory under [ROOM]; each room's ability triggers as the marker moves into it, controlled by the dungeon's owner
 * (CR 309.4c). A dungeon whose marker is on its bottommost room and whose room ability has left the Stack is removed from the game,
 * completing it (a state-based action, CR 309.6, [check]). Completed dungeons are counted in the player's ledger ([COMPLETED]).
 *
 * A dungeon that may only be entered by "venture into [quality]" (Undercity, [arcanum.api.Mechanic.OnlyVentureInto], CR 701.49d) is
 * never chosen by a plain venture. The initiative's three inherent triggered abilities (CR 726.2) have no source and are found here
 * ([inherent]).
 */
internal object Dungeons {
    const val ROOM: String = "\$venture-room"
    const val COMPLETED: String = "dungeons-completed"

    /** "Undercity" (CR 205.3p, 701.49d): what the initiative ventures into. */
    val UNDERCITY: ObjectFilter = ObjectFilter.Sub(SubtypeRef.Literal(Subtype("Undercity")))

    fun completed(state: GameState, player: PlayerId): Int = state.player(player).ledger[COMPLETED] ?: 0

    private fun isDungeon(o: GameObject): Boolean = o.definition?.faces?.firstOrNull()?.types?.contains(CardType.Dungeon) == true

    private fun rooms(o: GameObject): List<DungeonRoom> = o.definition?.faces?.firstOrNull()?.rooms.orEmpty()

    /** The qualities a dungeon may only be entered by venturing into ("You can't enter this dungeon unless you "venture into
     * Undercity."", CR 701.49d): a plain venture can't choose it. */
    private fun only(o: GameObject): List<ObjectFilter> = o.definition?.faces?.firstOrNull()?.abilities.orEmpty()
        .filterIsInstance<Ability.Static>().flatMap { it.mechanics.filterIsInstance<arcanum.api.Mechanic.OnlyVentureInto>() }.map { it.quality }

    /** The dungeon [player] owns in the command zone (CR 309.3: at most one). */
    fun current(state: GameState, player: PlayerId): GameObject? =
        state.command.mapNotNull { state.obj(it) }.firstOrNull { it.owner == player && isDungeon(it) }

    /** The room [dungeon]'s venture marker is on. */
    fun room(dungeon: GameObject): Int? = (dungeon.memory[ROOM] as? NamedValue.Number)?.value

    /** CR 701.49a–d: [player] ventures into the dungeon; [into]: "venture into [quality]", the new dungeon's quality. */
    fun venture(act: Act, cause: Cause?, player: PlayerId, into: ObjectFilter?) {
        val dungeon = current(act.state, player) ?: return enter(act, cause, player, into)
        val rooms = rooms(dungeon)
        val at = room(dungeon) ?: 0
        val next = rooms.getOrNull(at)?.next.orEmpty().filter { it in rooms.indices }
        if (next.isEmpty()) {
            // CR 701.49c: from the bottommost room, the dungeon is completed and a new one begins
            complete(act, cause, player, dungeon)
            return enter(act, cause, player, into)
        }
        // CR 701.49b: an adjacent room following an arrow, chosen among several
        val names = next.map { rooms[it].name }
        val chosen = if (next.size == 1) next.single() else next[names.indexOf(act.chooseOption(player, dungeon.id, names)).coerceAtLeast(0)]
        moveMarker(act, player, dungeon.id, chosen)
    }

    /** CR 309.2a, 701.49a, d: a dungeon card [player] owns from outside the game into the command zone, the marker on its topmost room. */
    private fun enter(act: Act, cause: Cause?, player: PlayerId, into: ObjectFilter?) {
        val outside = act.state.player(player).outside
        // Only its owner may see their cards outside the game (D60): their choice is made in their view
        act.fill(player, cause?.source, outside, "venture into the dungeon")
        val e = act.evaluator(Context(player, cause?.source, Bindings.EMPTY))
        val options = outside.filter { id ->
            val o = act.state.obj(id)
            o != null && isDungeon(o) && (into == null || e.matches(into, id)) && only(o).all { it == into }
        }
        if (options.isEmpty()) return
        val chosen = act.chooseObjects(player, cause?.source, options, 1, 1, "Venture into").firstOrNull() ?: return
        val moved = Actions.move(act, cause, listOf(PlannedMove(chosen, Zone.Command, controller = player)))[chosen] ?: return
        if (act.state.obj(moved)?.zone != Zone.Command) return
        moveMarker(act, player, moved, 0)
    }

    /** The marker moves into [room], whose ability triggers (CR 309.4c). */
    private fun moveMarker(act: Act, player: PlayerId, dungeon: ObjectId, room: Int) {
        val o = act.state.objOrThrow(dungeon)
        act.record(Change.Memory(dungeon, ROOM, o.memory[ROOM], NamedValue.Number(room)))
        val r = rooms(o).getOrNull(room) ?: return
        arcanum.engine.trigger.Triggers.addWaiting(act.recorder, listOf(WaitingTrigger(o.owner,
            Ability.Triggered(arcanum.api.Trigger.On(emptyList()), r.instructions, text = r.name), null, dungeon)))
    }

    /** CR 309.7: [player] completes [dungeon] as it is removed from the game (back outside it, where it began). */
    private fun complete(act: Act, cause: Cause?, player: PlayerId, dungeon: GameObject) {
        val moved = Actions.move(act, cause, listOf(PlannedMove(dungeon.id, Zone.Outside, meanings = listOf(Meaning.LeftTheGame))))[dungeon.id]
        val before = completed(act.state, player)
        act.record(Change.Ledger(player, COMPLETED, before, before + 1))
        act.record(Change.Happened(GameEvent.CompletedDungeon(player, moved ?: dungeon.id)))
    }

    /** CR 309.6: a dungeon whose marker is on its bottommost room, and that isn't the source of a room ability that triggered and hasn't
     * left the Stack, is removed from the game by its owner (a state-based action). */
    fun check(act: Act) {
        act.state.command.mapNotNull { act.state.obj(it) }.filter(::isDungeon).forEach { d ->
            val at = room(d) ?: return@forEach
            if (rooms(d).getOrNull(at)?.next?.isNotEmpty() != false) return@forEach
            val waiting = act.state.triggered.any { it.source == d.id } || act.state.stackItems.values.any { it.source == d.id }
            if (!waiting) complete(act, null, d.owner, d)
        }
    }

    /**
     * CR 726.2: the initiative's inherent triggered abilities, without a source and controlled by the player who had the initiative:
     * at the beginning of their upkeep they venture into Undercity; when creatures a player controls deal combat damage to them, that
     * player takes the initiative; whenever a player takes the initiative (726.5: also when they had it), they venture into Undercity.
     */
    fun inherent(state: GameState, now: View, change: Change): List<WaitingTrigger> {
        val holder = state.players.firstOrNull { !it.lost && Designation.Initiative in it.designations }?.id
        val found = mutableListOf<WaitingTrigger>()
        when (change) {
            is Change.Step -> if (holder != null && Meaning.StepBegan in change.meanings && change.after.step == TurnStep.Upkeep &&
                change.after.active == holder) found += ventureUndercity(holder)
            is Change.Happened -> (change.event as? GameEvent.TookInitiative)?.let { found += ventureUndercity(it.player) }
            is Change.Damage -> if (holder != null) {
                val board = ViewBoard(state, now)
                change.dealt.filter { it.combat && it.amount > 0 && it.recipient == TargetRef.Player(holder) }
                    .mapNotNull { d -> board.controller(d.source) ?: d.sourceSeen?.controller }.distinct().filter { it != holder }
                    .forEach { p ->
                        found += WaitingTrigger(holder, Ability.Triggered(arcanum.api.Trigger.On(emptyList()), Instructions(steps = listOf(
                            Step.Do(Effect.GiveDesignation(Players.Bound(TAKER), Designation.Initiative)))), text = "takes the initiative"), null, null,
                            event = TriggerEvent(players = listOf(p)), names = Bindings.EMPTY.with(TAKER, NamedValue.Players(listOf(p))))
                    }
            }
            else -> {}
        }
        return found
    }

    private const val TAKER = "\$takes-initiative"

    private fun ventureUndercity(player: PlayerId) = WaitingTrigger(player, Ability.Triggered(arcanum.api.Trigger.On(emptyList()),
        Instructions(steps = listOf(Step.Do(Effect.Action(KeywordAction.VentureInto(UNDERCITY))))), text = "venture into Undercity"), null, null)
}
