package arcanum.engine.prepare

import arcanum.api.CardDefinition
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.ObjectMark
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Zone
import arcanum.engine.action.Act
import arcanum.engine.action.Actions
import arcanum.engine.change.Change
import arcanum.engine.change.Placed
import arcanum.engine.change.Recorder
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.StatusFlag
import arcanum.engine.state.TokenIds
import arcanum.engine.view.AppliedMechanic

/**
 * Preparation cards (CR 722): a permanent with a prepare spell ([Layout.Prepare], its second face) becomes prepared
 * ([ObjectMark.Prepared]); as it does, its controller creates a copy of it in exile with only the prepare spell's
 * characteristics (722.3c), a copy of a card ([GameObject.copyOfCard]) linked to it by [GameObject.prepareSpellOf]. The
 * copy stays in exile while the permanent stays on the battlefield prepared (an exception to CR 704.5e), and its controller
 * may cast it then; the permanent loses the designation as the copy becomes cast (722.3c, 601.2i).
 */
internal object Prepare {
    /** Whether [o] has a prepare spell (CR 722.2a). */
    fun hasPrepareSpell(o: GameObject?): Boolean = o?.definition?.let { it.layout == Layout.Prepare && it.faces.size > 1 } == true

    /** [id] becomes prepared (CR 722.3a, c): only with a prepare spell and not when it already is. */
    fun becomePrepared(act: Act, id: ObjectId) {
        val o = act.obj(id) ?: return
        if (o.zone != Zone.Battlefield || !hasPrepareSpell(o) || ObjectMark.Prepared in o.marks) return
        // A copy left from an earlier designation goes first (the designation can't be held twice)
        removeCopies(act, id)
        Actions.tapStatus(act, id, StatusFlag.Marked(ObjectMark.Prepared), true)
        val controller = act.controllerOf(id) ?: o.owner
        val card = o.id as? ObjectId.Card ?: return
        val copy = GameObject(
            id = ObjectId.Card(TokenIds.token("prepare:${card.card.value}@${act.state.nextTimestamp()}", 0), 0),
            owner = controller, zone = Zone.Exile,
            definition = CardDefinition(Layout.Normal, listOf(o.definition!!.faces[1])),
            copyOfCard = true, timestamp = act.state.nextTimestamp(), prepareSpellOf = card,
        )
        act.record(Change.Created(listOf(Placed(copy, act.state.zone(Zone.Exile, controller).size)), listOf(arcanum.engine.change.Meaning.CardCopied)))
    }

    /** [id] becomes unprepared (CR 722.3b); its copy ceases to exist. */
    fun unprepare(act: Act, id: ObjectId) {
        val o = act.obj(id) ?: return
        if (ObjectMark.Prepared !in o.marks) return
        Actions.tapStatus(act, id, StatusFlag.Marked(ObjectMark.Prepared), false)
        removeCopies(act, id)
    }

    private fun removeCopies(act: Act, id: ObjectId) {
        act.state.objects.values.filter { it.prepareSpellOf == id && it.zone == Zone.Exile }.forEach { copy ->
            act.record(Change.Removed(listOf(Placed(copy, act.state.positionOf(copy.id)))))
        }
    }

    /** CR 601.2i, 722.3c: the permanent whose prepare spell [spell] is loses the designation as it becomes cast. */
    fun onCast(recorder: Recorder, spell: ObjectId) {
        val of = recorder.state.obj(spell)?.prepareSpellOf ?: return
        val permanent = recorder.state.obj(of) ?: return
        if (ObjectMark.Prepared in permanent.marks) recorder.record(Change.Status(of, StatusFlag.Marked(ObjectMark.Prepared), true, false))
    }

    /** Whether [o] is a prepared permanent's copy that stays in exile (CR 722.3c, an exception to 704.5e). */
    fun keptInExile(state: GameState, o: GameObject): Boolean {
        if (o.zone != Zone.Exile) return false
        val of = o.prepareSpellOf ?: return false
        val permanent = state.obj(of) ?: return false
        return permanent.zone == Zone.Battlefield && ObjectMark.Prepared in permanent.marks
    }

    /** The permission to cast each copy kept in exile, its permanent's controller's (CR 722.3c). */
    fun permissions(state: GameState, controllerOf: (ObjectId) -> arcanum.engine.state.PlayerId?): Map<ObjectId, AppliedMechanic> =
        state.exile.mapNotNull { id ->
            val o = state.obj(id) ?: return@mapNotNull null
            if (!keptInExile(state, o)) return@mapNotNull null
            val permanent = state.obj(o.prepareSpellOf!!)!!
            id to AppliedMechanic(CAST_COPY, controllerOf(permanent.id) ?: permanent.owner, permanent.timestamp, source = permanent.id)
        }.toMap()

    /** The permission the rules give a prepared permanent's controller (CR 722.3c). */
    val CAST_COPY: Mechanic = Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)
}
