package org.asyncttwiz.automatum

import arcanum.api.Door
import arcanum.api.ObjectMark
import arcanum.api.RulesGlossary
import arcanum.engine.Inspect
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.NamedValue

/**
 * TBL-133: the states the rules keep on a card that its picture doesn't show, in the same words everywhere: [words] for its
 * chip (TBL-113), [lines] for Inspect (TBL-104). A face-down permanent's manifested or cloaked mark isn't among them: the
 * table shows it as face down, nothing more.
 */
internal object CardStates {
    /** The Ring emblem's name, as the engine gives it: its "can't be blocked" is the Ring-bearer's line ([lines]). */
    const val RING = "The Ring"

    /** [o]'s states as the chip reads them, the most telling first. */
    fun words(state: GameState, o: GameObject): List<String> = buildList {
        if (ObjectMark.RingBearer in o.marks) add("Ring-bearer")
        if (o.goadedBy.isNotEmpty()) add("Goaded")
        if (o.phasedOut) add("Phased out")
        for (mark in o.marks) word(mark)?.let(::add)
        doors(o)?.let(::add)
        Inspect.classLevel(state, o.id)?.takeIf { it > 1 }?.let { add("Level $it") }
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
     * the other marks by their word; "Protector: Bob" for a battle; what was chosen for it ("Chosen: blue"). [names] names the
     * players by account; a face-down permanent's chosen values are its controller's to see.
     */
    fun lines(state: GameState, o: GameObject, viewer: String, names: Map<String, String>): List<EffectChange> = buildList {
        fun who(id: String) = if (id == viewer) "you" else names[id] ?: "the opponent"
        for (word in words(state, o)) when (word) {
            "Ring-bearer" -> {
                val level = Inspect.ringTemptations(state, o.controller ?: o.owner).coerceIn(1, RulesGlossary.ringLevels.size)
                add(EffectChange("Ring-bearer: " + RulesGlossary.ringLevels.take(level).joinToString("; "), RING))
            }
            "Goaded" -> {
                val goaders = o.goadedBy.joinToString(" and ") { who(it.value) }
                add(EffectChange("Goaded by $goaders: attacks each combat if able, and not $goaders if it can"))
            }
            else -> add(EffectChange(word))
        }
        o.protector?.let { add(EffectChange("Protector: ${who(it.value)}")) }
        if (!o.faceDown || o.controller?.value == viewer) chosen(o).forEach { add(EffectChange(it)) }
    }

    /** What was chosen for [o] and stays with it (CR 607.2d): a colour, a creature or card type, a card name. The engine's
     * own bookkeeping keys, starting "$", are left out. */
    private fun chosen(o: GameObject): List<String> = o.memory.values.filterKeys { !it.startsWith("$") }.values.mapNotNull { value ->
        when (value) {
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
