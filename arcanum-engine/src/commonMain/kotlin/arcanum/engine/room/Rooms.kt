package arcanum.engine.room

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Cost
import arcanum.api.Door
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Layout
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Zone
import arcanum.engine.action.Act
import arcanum.engine.action.Actions
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.state.CopiableValues
import arcanum.engine.state.GameObject
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StatusFlag
import arcanum.engine.view.AbilityInstance
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.IndexedFace
import arcanum.engine.view.ObjectView

/**
 * Rooms and other permanents with a shared type line (CR 709.5): their doors (halves) are locked or unlocked, a locked
 * half's name, mana cost and rules text don't exist on the battlefield, a half cast is unlocked as it enters, and their
 * controller may pay a locked half's mana cost to unlock it as a sorcery (a special action).
 */
internal object Rooms {
    /** A split card whose halves are both permanent cards: a single shared type line (CR 709.5). What it is printed as:
     * what is cast and enters. */
    fun isRoom(definition: CardDefinition?): Boolean = definition != null && isRoom(definition.layout, definition.faces)

    /** Copiable values with a shared type line (CR 709.5, 709.5b): what a permanent works by, a copy of a Room too. */
    fun isRoom(values: CopiableValues?): Boolean = values != null && isRoom(values.layout, values.faces)

    private fun isRoom(layout: Layout, faces: List<FaceDefinition>) =
        layout == Layout.Split && faces.size == 2 && faces.all { isPermanentFace(it) }

    private fun isPermanentFace(face: FaceDefinition) =
        face.types.isNotEmpty() && face.types.none { it == CardType.Instant || it == CardType.Sorcery }

    fun door(face: Int): Door = if (face == 0) Door.Left else Door.Right

    fun face(door: Door): Int = if (door == Door.Left) 0 else 1

    fun other(door: Door): Door = if (door == Door.Left) Door.Right else Door.Left

    fun unlocked(o: GameObject, door: Door): Boolean = ObjectMark.Unlocked(door) in o.marks

    /** The locked doors of [o] as the view has it, a Room on the battlefield (a copy of a Room too, CR 709.5c). */
    fun locked(o: ObjectView?): List<Door> {
        val obj = o?.obj ?: return emptyList()
        return if (obj.zone != Zone.Battlefield || !isRoom(o.copiable)) emptyList() else listOf(Door.Left, Door.Right).filterNot { unlocked(obj, it) }
    }

    /**
     * CR 709.5: on the battlefield, a half without its unlocked designation doesn't have its name, mana cost or rules text
     * (its shared types stay, 709.5a). Applies to the faces of a Room after copy effects ([values]: the halves are copiable
     * values, 709.5b; the designations are the object's own, 709.5c).
     */
    fun lockedHalves(o: GameObject, values: CopiableValues, faces: List<IndexedFace>): List<IndexedFace> {
        if (o.zone != Zone.Battlefield || !isRoom(values)) return faces
        return faces.map { f ->
            if (f.abilitiesOnly) return@map f
            if (unlocked(o, door(f.index))) f else IndexedFace(f.index, f.face.copy(name = "", manaCost = null, abilities = emptyList()))
        }
    }

    /** Gives [id] the unlocked designation of [door] (CR 709.5f) and records the unlock (709.5h, i); [player] unlocked it. */
    fun unlock(act: Act, id: ObjectId, door: Door, player: PlayerId? = null) {
        val o = act.state.obj(id) ?: return
        if (unlocked(o, door)) return
        val fully = unlocked(o, other(door))
        Actions.tapStatus(act, id, StatusFlag.Marked(ObjectMark.Unlocked(door)), true)
        act.record(Change.Happened(GameEvent.Unlocked(player ?: act.controllerOf(id) ?: o.owner, id, face(door), fully)))
    }

    /** Takes the unlocked designation of [door] from [id] (CR 709.5g). */
    fun lock(act: Act, id: ObjectId, door: Door) {
        val o = act.state.obj(id) ?: return
        if (!unlocked(o, door)) return
        Actions.tapStatus(act, id, StatusFlag.Marked(ObjectMark.Unlocked(door)), false)
    }

    /** The unlock events of the Rooms that just entered from the Stack with the half cast unlocked (CR 709.5d, h). */
    fun enteredUnlocked(act: Act, entered: List<ObjectId>) {
        entered.forEach { id ->
            val o = act.state.obj(id) ?: return@forEach
            if (!isRoom(o.definition)) return@forEach
            o.marks.filterIsInstance<ObjectMark.Unlocked>().forEach { mark ->
                act.record(Change.Happened(GameEvent.Unlocked(o.controller ?: o.owner, id, face(mark.door), fully = false)))
            }
        }
    }

    /**
     * CR 709.5e: the special actions of paying a locked door's mana cost (as its copiable values have it on that half) to
     * unlock it, any time its controller could cast a sorcery.
     */
    fun unlockActions(o: ObjectView?): List<AbilityInstance> =
        locked(o).mapNotNull { door ->
            val half = o?.copiable?.faces?.getOrNull(face(door)) ?: return@mapNotNull null
            val cost = half.manaCost ?: return@mapNotNull null
            AbilityInstance(Ability.SpecialAction(Cost.mana(cost),
                Instructions(steps = listOf(Step.Do(Effect.UnlockDoor(Objects.Self, chooser = Players.You, door = door)))),
                timing = Timing.Sorcery, functionsIn = listOf(Zone.Battlefield),
                text = "Unlock the ${if (door == Door.Left) "left" else "right"} door (${half.name})"),
                AbilityOrigin.Rule(unlockRule(door)))
        }

    fun unlockRule(door: Door) = if (door == Door.Left) "unlock-left" else "unlock-right"

    fun isUnlockRule(origin: AbilityOrigin?): Boolean = origin is AbilityOrigin.Rule && origin.name.startsWith("unlock-")
}
