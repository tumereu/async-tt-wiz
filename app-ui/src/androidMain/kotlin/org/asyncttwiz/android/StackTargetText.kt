package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color
import org.asyncttwiz.automatum.TargetedText
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.MtgStackItem

/**
 * A piece of a Stack entry's text as shown (owner 2026-11-07): [text], and for a target's name the account id of the
 * target's controller ([controller]; a player is their own controller), which colours it.
 */
data class StackTextPiece(val text: String, val controller: String? = null)

/** The pieces' words, as one plain string (semantics, tests). */
fun List<StackTextPiece>.plain(): String = joinToString("") { it.text }

/** The pieces for [CardText], each target name in its controller's colour as [viewer] sees it: theirs green, the other
 * player's red (OwnTableColor, OpponentTableColor). */
fun List<StackTextPiece>.tinted(viewer: String): List<Pair<String, Color?>> =
    map { it.text to it.controller?.let { c -> if (c == viewer) OwnTableColor else OpponentTableColor } }

/** [tinted] on the dark wait band (STK-057): the same green and red, lightened to read on it. */
fun List<StackTextPiece>.tintedOnDark(viewer: String): List<Pair<String, Color?>> =
    map { it.text to it.controller?.let { c -> if (c == viewer) Color(0xff8ae6cf) else Color(0xffffa8b0) } }

/**
 * STK-057, STK-018, STK-050 (owner 2026-11-07: "Lightning Bolt deals 3 damage to Llanowar Elves"): a Stack entry's text
 * with its chosen targets named where the text names them, each coloured by its controller.
 */
object StackTargetText {
    /**
     * [text] of [item] as pieces for [viewer]: each target phrase as the names of the targets chosen for it ("Ember Cub",
     * "Bob", "Ember Cub and Ash Pup"), as far as [viewer] may see them ([StackAbilities.targetName]: [you] for themselves,
     * [hidden] for a card they can't see, HIS-062).
     */
    fun pieces(play: TableBoard, item: MtgStackItem, text: TargetedText, viewer: String, names: Map<String, String>,
        you: String = "you", hidden: String = "a card"): List<StackTextPiece> {
        var before = ""
        return text.parts.flatMap { part ->
            when (part) {
                is TargetedText.Part.Plain -> listOf(StackTextPiece(part.text)).also { before += part.text }
                is TargetedText.Part.Targets -> {
                    // A phrase that is its clause's subject ("Target player draws two cards") names the viewer by their
                    // own name, so the verb still agrees; elsewhere ("… deals 3 damage to you") they read as [you].
                    val subject = before.isBlank() || before.trimEnd().let { it.endsWith('.') || it.endsWith(':') || it.endsWith('\n') }
                    val self = if (subject) names[viewer] ?: you else you
                    named(play, part.keys.flatMap { item.targets[it].orEmpty() }.distinct(), viewer, names, self, hidden)
                        .also { pieces -> before += pieces.plain() }
                }
            }
        }
    }

    /** The targets of [item] [text] doesn't name, in the order they were chosen. */
    fun unplaced(item: MtgStackItem, text: TargetedText): List<MtgRef> {
        val placed = text.parts.filterIsInstance<TargetedText.Part.Targets>().flatMap { it.keys }.toSet()
        return item.targets.filterKeys { it !in placed }.values.flatten().distinct()
    }

    /** [refs] named and coloured, joined as a sentence lists them: "A", "A and B", "A, B, and C". */
    fun named(play: TableBoard, refs: List<MtgRef>, viewer: String, names: Map<String, String>, you: String = "you",
        hidden: String = "a card"): List<StackTextPiece> = refs.flatMapIndexed { index, ref ->
        val separator = when {
            index == 0 -> null
            refs.size == 2 -> " and "
            index == refs.size - 1 -> ", and "
            else -> ", "
        }
        listOfNotNull(separator?.let { StackTextPiece(it) },
            StackTextPiece(StackAbilities.targetName(play, ref, viewer, names, you = you, hidden = hidden), controller(play, ref)))
    }

    /** Who controls [ref]: a player themselves, a card the player whose zone holds it (its owner without one), a Stack entry
     * its controller. */
    fun controller(play: TableBoard, ref: MtgRef): String? = when (ref) {
        is MtgRef.Player -> ref.accountId
        is MtgRef.Card -> play.board.cards.firstOrNull { it.id == ref.cardId }?.let { card ->
            play.board.containers.firstOrNull { it.id == card.containerId }?.accountId ?: card.owner
        }
        is MtgRef.StackItem -> play.board.stackEffects.firstOrNull { it.id == ref.id || it.item?.id == ref }?.controller
            ?: CombatStackRules.controller(play.board, ref.id)
    }
}
