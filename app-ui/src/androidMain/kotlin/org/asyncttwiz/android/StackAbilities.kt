package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.automatum.StackAbilityText
import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.protocol.CardVisibility
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.MtgStackItem
import org.asyncttwiz.protocol.MtgStackKind

/**
 * STK-046 (owner 2026-11-04, MTG Arena as the reference): a triggered or activated ability on the Stack is drawn as an
 * ability, never as its source card: a smaller token with the source's art, a trigger or activation marker, the source's
 * name and the ability's text. [source] is the source's name, [text] the ability's text ("" when unknown); [kind] what
 * kind of ability it is (STK-050, owner playtest 2026-11-07: "Cycling trigger", "Offspring trigger", "Cycling ability",
 * else "Triggered ability" or "Activated ability").
 */
data class StackAbilityShown(val triggered: Boolean, val source: String, val text: String,
    val kind: String = if (triggered) "Triggered ability" else "Activated ability") {
    /** How the ability is named in lists and texts: "Ember Herald's ability". */
    val name: String get() = "$source's ability"
    /** The source's name and the ability's text: "Ember Herald — When this creature enters, …". */
    val line: String get() = if (text.isEmpty()) source else "$source — $text"
    /** The kind chip's letter (STK-046): "T" for a triggered ability, "A" for an activated one. */
    val letter: String get() = if (triggered) "T" else "A"
}

/** STK-018, STK-050: a Stack entry's whole text as shown ([pieces], target names in place) and the targets it doesn't
 * name ([unplaced]), which its "Target:" line lists. */
data class StackEntryText(val pieces: List<StackTextPiece>, val unplaced: List<MtgRef>)

object StackAbilities {
    private val texts = object : java.util.LinkedHashMap<String, String>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 64
    }

    /** The engine's text of [item]'s ability (a keyword's trigger as the keyword's line of [rulesText]), decoded once
     * per item. */
    private fun text(item: MtgStackItem, rulesText: String?): String? = synchronized(texts) {
        texts[item.data] ?: StackAbilityText.of(item, rulesText)?.also { texts[item.data] = it }
    }

    private val kinds = object : java.util.LinkedHashMap<String, String>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 64
    }

    /** What kind of ability [item] is ([StackAbilityText.kind]), decoded once per item. */
    private fun kind(item: MtgStackItem): String? = synchronized(kinds) {
        kinds[item.data] ?: StackAbilityText.kind(item)?.also { kinds[item.data] = it }
    }

    /**
     * The ability Stack entry [entryId] shows to [viewer], or null when it is a spell (or a copy of one), not an entry, or
     * its source is hidden from [viewer] (a face-down entry stays a card back, STK-005).
     */
    fun of(play: TableBoard, entryId: String, viewer: String): StackAbilityShown? {
        val effect = play.board.stackEffects.singleOrNull { it.id == entryId } ?: return null
        val item = effect.item ?: return null
        val triggered = when (item.kind) {
            MtgStackKind.Triggered -> true
            MtgStackKind.Activated -> false
            else -> return null
        }
        if (effect.faceDown) return null
        val card = CombatStackRules.inspection(play.board, entryId) ?: return null
        val face = play.definitions.firstOrNull { it.digest == card.definition }?.definition?.faces
            ?.let { faces -> faces.firstOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }
        // An entry of its own (STK-022) is its own card data, the source's name and the ability's text; a live source
        // shows only as far as the viewer may see it
        val frozen = effect.frozen != null
        if (!frozen && !CardVisibility.isVisibleFaceUp(play.board, card, viewer)) return null
        val source = face?.name?.takeIf { it.isNotBlank() } ?: "Ability"
        val text = text(item, face?.rulesText) ?: face?.rulesText?.takeIf { frozen }.orEmpty()
        return kind(item)?.let { StackAbilityShown(triggered, source, text.trim(), it) } ?: StackAbilityShown(triggered, source, text.trim())
    }

    /**
     * STK-018, STK-050 (owner 2026-11-07): the whole text of Stack entry [entryId] as [viewer] sees it, with each chosen
     * target named where the text names it ("Storm Spark deals 3 damage to Ember Cub"), coloured by its controller, and X
     * filled in: an ability's text ([of]) or a spell's own text; [unplaced] the targets it doesn't name. Null when no text
     * shows: a hidden entry, a permanent spell without text of its own.
     */
    fun text(play: TableBoard, entryId: String, viewer: String, names: Map<String, String>): StackEntryText? {
        val effect = play.board.stackEffects.singleOrNull { it.id == entryId } ?: return null
        val item = effect.item ?: return null
        val engine = StackAbilityText.decoded(item)
        val ability = of(play, entryId, viewer)
        val rulesText: String?
        when (item.kind) {
            MtgStackKind.Activated, MtgStackKind.Triggered -> {
                ability ?: return null
                rulesText = sourceFace(play, entryId)?.rulesText
            }
            MtgStackKind.Spell, MtgStackKind.SpellCopy -> {
                val card = CombatStackRules.inspection(play.board, entryId) ?: return null
                if (effect.faceDown || (effect.frozen == null && !CardVisibility.isVisibleFaceUp(play.board, card, viewer))) return null
                rulesText = null
            }
        }
        val targeted = engine?.let { StackAbilityText.targetedText(it, rulesText) }
            ?: return ability?.text?.takeIf { it.isNotEmpty() }?.let { StackEntryText(listOf(StackTextPiece(it)), item.targets.values.flatten().distinct()) }
        val pieces = StackTargetText.pieces(play, item, targeted, viewer, names)
        if (pieces.plain().isBlank()) return null
        return StackEntryText(pieces, StackTargetText.unplaced(item, targeted))
    }

    /** The face of the Stack entry [entryId]'s card (its source's, for an ability). */
    private fun sourceFace(play: TableBoard, entryId: String) = CombatStackRules.inspection(play.board, entryId)?.let { card ->
        play.definitions.firstOrNull { it.digest == card.definition }?.definition?.faces
            ?.let { faces -> faces.firstOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }
    }

    /**
     * STK-050: what was chosen for the Stack entry [entryId] (an ability, or a spell: owner 2026-11-06), one line each, for
     * Inspect: "Mode: …" ("Modes: …"), "X = 3" for each announced value, and "Target: …" ("Targets: …"), each target named
     * as far as [viewer] may see it ("You", the other player's name in [names], a card's name, "a hidden card"); empty
     * when nothing was chosen. With the entry's [shown] text, only the targets that text doesn't name are listed (owner
     * 2026-11-07).
     */
    fun details(play: TableBoard, entryId: String, viewer: String, names: Map<String, String>, shown: StackEntryText? = null): List<String> {
        val item = play.board.stackEffects.singleOrNull { it.id == entryId }?.item ?: return emptyList()
        val modes = StackAbilityText.modes(item)
        val targets = (shown?.unplaced ?: item.targets.values.flatten()).map { targetName(play, it, viewer, names) }
        return listOfNotNull(
            modes.takeIf { it.isNotEmpty() }?.joinToString("; ", prefix = if (modes.size == 1) "Mode: " else "Modes: ")) +
            item.values.map { (name, value) -> "$name = $value" } +
            listOfNotNull(targets.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = if (targets.size == 1) "Target: " else "Targets: "))
    }

    /**
     * A chosen [target] as [viewer] may see it (HIS-062): [you] for themselves, the other player's name in [names], a card's
     * or Stack entry's name, [hidden] for a card they can't see face up.
     */
    fun targetName(play: TableBoard, target: MtgRef, viewer: String, names: Map<String, String>, you: String = "You",
        hidden: String = "a hidden card"): String = when (target) {
        is MtgRef.Player -> if (target.accountId == viewer) you else names[target.accountId] ?: "Opponent"
        is MtgRef.Card -> visibleName(play, target.cardId, viewer, hidden)
        is MtgRef.StackItem -> of(play, target.id, viewer)?.name ?: visibleName(play, target.id, viewer, hidden)
    }

    /** The name of the card (or Stack entry) [id] as [viewer] sees it: [hidden] when they can't see it face up. */
    fun visibleName(play: TableBoard, id: String, viewer: String, hidden: String = "a hidden card"): String {
        val card = CombatStackRules.inspection(play.board, id) ?: return hidden
        if (!CardVisibility.isVisibleFaceUp(play.board, card, viewer)) return hidden
        return play.definitions.firstOrNull { it.digest == card.definition }?.definition?.faces
            ?.let { faces -> faces.firstOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }?.name ?: hidden
    }
}

/** STK-046: the kind chip of an ability: "T" for a triggered ability, "A" for an activated one, white on violet; it
 * reads as [StackAbilityShown.kind]. [height] sets its size. */
@Composable
fun AbilityKindChip(ability: StackAbilityShown, height: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(height * .28f)
    Box(modifier.size(height * 1.15f, height).background(AutomatumViolet, shape).border(1.dp, Color.White, shape)
        .semantics { contentDescription = ability.kind }, contentAlignment = Alignment.Center) {
        Text(ability.letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (height.value * .62f).sp,
            lineHeight = (height.value * .62f).sp)
    }
}

/**
 * STK-046: an ability on the Stack outside the table (the Stack list, the Stack target picker, a panel's source), drawn
 * as the table draws it: a dark token smaller than a card with the controller's outline ([own]), the source's [art]
 * filling it (its type glyph [typeIcon] while there is none) and the kind chip at its bottom; no name or text.
 */
@Composable
fun AbilityTokenFace(ability: StackAbilityShown, art: ArtworkReference?, own: Boolean, modifier: Modifier = Modifier,
    typeIcon: String = "") {
    val owner = if (own) OwnTableColor else OpponentTableColor
    val shape = RoundedCornerShape(5.dp)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val chipHeight = minOf(maxHeight * .2f, 16.dp)
        Box(Modifier.fillMaxWidth(.86f).fillMaxHeight(.8f)) {
            Box(Modifier.fillMaxSize().clip(shape).background(AbilityTokenArtColor).border(1.5.dp, owner, shape)) {
                CardArtwork(art, "", 192, 260, Modifier.fillMaxSize(), crop = true, placeholder = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(typeIcon, fontSize = 14.sp, color = Color.White)
                    }
                })
            }
            AbilityKindChip(ability, chipHeight, Modifier.align(Alignment.BottomCenter).offset(y = -chipHeight * .3f))
        }
    }
}
