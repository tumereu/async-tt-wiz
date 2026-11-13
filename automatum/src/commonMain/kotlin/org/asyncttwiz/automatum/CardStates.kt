package org.asyncttwiz.automatum

import arcanum.api.Door
import arcanum.api.ObjectMark
import arcanum.api.RulesGlossary
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectStatus
import arcanum.engine.state.PlayerStatus
import arcanum.engine.state.playerStatus

/**
 * TBL-133: the states the rules keep on a card that its picture doesn't show, in the same words everywhere: [words] for its
 * chip (TBL-113), [lines] for Inspect (TBL-104). A face-down permanent's manifested or cloaked mark isn't among them: the
 * table shows it as face down, nothing more.
 */
internal object CardStates {
    /** The Ring emblem's name, as the engine gives it: its "can't be blocked" is the Ring-bearer's line ([lines]). */
    const val RING = "The Ring"

    /** What [statuses] (an object's, as its view has them: [arcanum.engine.view.ObjectView.statuses]) show of it: who goaded it, a
     * battle's protector, a Class's level. */
    private class Shown(statuses: List<ObjectStatus>) {
        var goadedBy: List<arcanum.engine.state.PlayerId> = emptyList()
        var protector: arcanum.engine.state.PlayerId? = null
        var level: Int? = null

        init {
            for (status in statuses) when (status) {
                is ObjectStatus.Goaded -> goadedBy = status.by
                is ObjectStatus.Protector -> protector = status.player
                is ObjectStatus.ClassLevel -> level = status.level
                // Shown otherwise (a Ring-bearer by its mark, a dungeon's room by its chip) or not a state a player sees on the card
                is ObjectStatus.RingBearer, is ObjectStatus.VentureRoom, ObjectStatus.CopyOfCard, is ObjectStatus.PrepareSpellOf,
                ObjectStatus.DeathtouchDamage, is ObjectStatus.Merged, ObjectStatus.Flipped, is ObjectStatus.GainedInCopying,
                is ObjectStatus.FixedCopies, ObjectStatus.DealtCombatDamage, ObjectStatus.DealtDamage, ObjectStatus.HeldPhasedOut,
                is ObjectStatus.MarksGained, ObjectStatus.TurnedFaceUp, is ObjectStatus.PairedWith, ObjectStatus.RevealedCompanion,
                // ZON-079: shown by the card's place in its player's hand row
                is ObjectStatus.Playable, is ObjectStatus.CreatedBy -> Unit
            }
        }
    }

    /** [o]'s states as the chip reads them, the most telling first; [statuses] its statuses as its view has them. */
    fun words(o: GameObject, statuses: List<ObjectStatus>): List<String> = words(o, Shown(statuses))

    private fun words(o: GameObject, shown: Shown): List<String> = buildList {
        if (ObjectMark.RingBearer in o.marks) add("Ring-bearer")
        if (shown.goadedBy.isNotEmpty()) add("Goaded")
        if (o.phasedOut) add("Phased out")
        for (mark in o.marks) word(mark)?.let(::add)
        doors(o)?.let(::add)
        shown.level?.takeIf { it > 1 }?.let { add("Level $it") }
    }

    private fun word(mark: ObjectMark): String? = when (mark) {
        ObjectMark.Monstrous -> "Monstrous"
        ObjectMark.Renowned -> "Renowned"
        ObjectMark.Suspected -> "Suspected"
        ObjectMark.Saddled -> "Saddled"
        ObjectMark.Solved -> "Solved"
        ObjectMark.Prepared -> "Prepared"
        ObjectMark.Harnessed -> "Harnessed"
        ObjectMark.Warped -> "Warped"
        ObjectMark.Foretold -> "Foretold"
        ObjectMark.Plotted -> "Plotted"
        is ObjectMark.Named -> mark.name.replaceFirstChar { it.uppercase() }
        // Shown otherwise: as its own word first, by [doors], or as a face-down permanent
        ObjectMark.RingBearer, is ObjectMark.Unlocked, ObjectMark.Manifested, ObjectMark.Cloaked -> null
    }

    /** A Room's doors (CR 709.5): nothing while both are locked. */
    private fun doors(o: GameObject): String? {
        val unlocked = o.marks.filterIsInstance<ObjectMark.Unlocked>().map { it.door }
        return when {
            unlocked.isEmpty() -> null
            unlocked.size > 1 -> "Unlocked"
            unlocked.single() == Door.Left -> "Left unlocked"
            else -> "Right unlocked"
        }
    }

    /**
     * [o]'s states as Inspect lists them, one line each, as [viewer] may see them: "Ring-bearer: legendary, can't be blocked by
     * creatures with greater power" and what the Ring's level adds; "Goaded by Bob: …"; a Room's doors, a Class's level and
     * the other marks by their word; "Protector: Bob" for a battle; what was chosen for it ("Chosen: blue", "Chosen: Ember Cub"
     * for a chosen permanent, named by [objectName] as the viewer may see it). [names] names the players by account; a face-down
     * permanent's chosen values are its controller's to see. [statuses]: its statuses as its view has them.
     */
    fun lines(state: GameState, o: GameObject, statuses: List<ObjectStatus>, viewer: String, names: Map<String, String>,
        objectName: (arcanum.engine.state.ObjectId) -> String? = { id -> state.obj(id)?.let { it.definition.faces.getOrNull(it.face)?.name } }): List<EffectChange> = buildList {
        fun who(id: String) = if (id == viewer) "you" else names[id] ?: "the opponent"
        val shown = Shown(statuses)
        for (word in words(o, shown)) when (word) {
            "Ring-bearer" -> {
                val tempted = state.player(o.controller ?: o.owner).statuses.playerStatus<PlayerStatus.RingTempted>()?.times ?: 0
                val level = tempted.coerceIn(1, RulesGlossary.ringLevels.size)
                add(EffectChange("Ring-bearer: " + RulesGlossary.ringLevels.take(level).joinToString("; "), RING))
            }
            "Goaded" -> {
                val goaders = shown.goadedBy.joinToString(" and ") { who(it.value) }
                add(EffectChange("Goaded by $goaders: attacks each combat if able, and not $goaders if it can"))
            }
            else -> add(EffectChange(word))
        }
        shown.protector?.let { add(EffectChange("Protector: ${who(it.value)}")) }
        if (!o.faceDown || o.controller?.value == viewer) chosen(o, objectName).forEach { add(EffectChange(it)) }
    }

    /** What was chosen for [o] and stays with it (CR 607.2d): a colour, a creature or card type, a card name, a permanent ("As
     * this enters, choose a nonland permanent", owner 2026-11-12: Scheming Fence; the parser's "chosen" memory). */
    private fun chosen(o: GameObject, objectName: (arcanum.engine.state.ObjectId) -> String?): List<String> =
        o.linked.values.filterKeys { !it.startsWith("$") }.mapNotNull { (key, value) ->
        when (value) {
            is NamedValue.Objects -> if (!key.startsWith("chosen")) null
                else value.ids.mapNotNull(objectName).takeIf { it.isNotEmpty() }?.let { "Chosen: " + it.joinToString(" and ") }
            is NamedValue.Colors -> value.colors.joinToString(" and ") { color ->
                when (color.symbol) { 'W' -> "white"; 'U' -> "blue"; 'B' -> "black"; 'R' -> "red"; else -> "green" }
            }.takeIf { it.isNotEmpty() }?.let { "Chosen: $it" }
            is NamedValue.SubtypeValue -> "Chosen: ${value.subtype.name}"
            is NamedValue.CardTypeValue -> "Chosen: ${value.cardType.toString().lowercase()}"
            is NamedValue.Name -> "Named: ${value.name}"
            else -> null
        }
    }
}
