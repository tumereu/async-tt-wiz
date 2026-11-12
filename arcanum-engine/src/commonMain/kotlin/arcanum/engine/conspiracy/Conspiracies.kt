package arcanum.engine.conspiracy

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.ObjectFilter
import arcanum.api.Zone
import arcanum.engine.action.Act
import arcanum.engine.action.Actions
import arcanum.engine.action.PlannedMove
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.state.GameObject
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId

/**
 * Conspiracies (Conspiracy Draft, CR 103.2e, 905.4) and hidden agenda (CR 702.106): as the game starts each player may put conspiracy
 * cards from their sideboard into the command zone ([putIntoCommand]); one with hidden agenda goes face down and its owner secretly
 * chooses a card name (double agenda: two different names, 702.106f), kept as its memory under [CHOSEN_NAME] (and [SECOND_NAME]),
 * the key "the chosen name" reads (linked abilities, CR 607.2d). Any time they have priority its owner may turn it face up, a special
 * action (CR 116.2j, the keyword's expansion), revealing the name ([revealed]).
 */
internal object Conspiracies {
    /** The memory key "the chosen name" reads (the parser's `RememberedName("name")`). */
    const val CHOSEN_NAME: String = "name"
    /** Double agenda's second name (CR 702.106f); "the chosen name" matches either. */
    const val SECOND_NAME: String = "name#2"

    private fun agendas(o: GameObject?): Int {
        val abilities = o?.definition?.faces?.firstOrNull()?.abilities.orEmpty()
        return when {
            Keyword.DoubleAgenda in abilities -> 2
            Keyword.HiddenAgenda in abilities -> 1
            else -> 0
        }
    }

    private fun isConspiracy(o: GameObject?): Boolean = o?.definition?.faces?.firstOrNull()?.types?.contains(CardType.Conspiracy) == true

    /**
     * CR 103.2e, 905.4, 905.4a: [player] puts any number of conspiracy cards they own outside the game into the command zone, those with
     * hidden agenda face down with their names chosen; the others become public.
     */
    fun putIntoCommand(act: Act, player: PlayerId) {
        val outside = act.state.player(player).outside
        val candidates = outside.filter { id -> isConspiracy(act.state.obj(id)) }
        if (candidates.isEmpty()) return
        val chosen = act.chooseObjects(player, null, candidates, 0, candidates.size, "Put conspiracies into the command zone (CR 103.2e)")
        if (chosen.isEmpty()) return
        val faceDown = chosen.filter { agendas(act.state.obj(it)) > 0 }
        act.show(player, chosen - faceDown.toSet(), public = true)
        val moved = Actions.move(act, null, chosen.map { PlannedMove(it, Zone.Command, faceDown = it in faceDown) })
        faceDown.mapNotNull { moved[it] }.forEach { id -> chooseNames(act, player, id) }
    }

    /** CR 702.106a, f: [player] secretly chooses the name(s) of [id]'s hidden agenda. */
    private fun chooseNames(act: Act, player: PlayerId, id: ObjectId) {
        val count = agendas(act.state.obj(id))
        val first = act.chooseCardName(player, id, ObjectFilter.Any)
        act.record(Change.Memory(id, CHOSEN_NAME, act.state.objOrThrow(id).linked[CHOSEN_NAME], NamedValue.Name(first)))
        if (count < 2) return
        var second = act.chooseCardName(player, id, ObjectFilter.Any)
        // CR 702.106f: two different names
        if (second == first) second = act.chooseCardName(player, id, ObjectFilter.Not(ObjectFilter.Named(first)))
        act.record(Change.Memory(id, SECOND_NAME, act.state.objOrThrow(id).linked[SECOND_NAME], NamedValue.Name(second)))
    }

    /** CR 702.106c: [id] was turned face up, revealing its chosen name(s): asked of its owner when none was chosen yet. */
    fun revealed(act: Act, id: ObjectId) {
        val o = act.state.obj(id) ?: return
        if (o.zone != Zone.Command || agendas(o) == 0) return
        if (o.linked[CHOSEN_NAME] == null) chooseNames(act, o.owner, id)
        act.record(Change.Happened(GameEvent.Revealed(o.owner, listOf(id))))
    }
}
