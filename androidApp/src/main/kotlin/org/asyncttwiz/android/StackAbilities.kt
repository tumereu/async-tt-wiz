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
 * name and the ability's text. [source] is the source's name, [text] the ability's text ("" when unknown).
 */
data class StackAbilityShown(val triggered: Boolean, val source: String, val text: String) {
    /** How the ability is named in lists and texts: "Ember Herald's ability". */
    val name: String get() = "$source's ability"
    /** The source's name and the ability's text: "Ember Herald — When this creature enters, …". */
    val line: String get() = if (text.isEmpty()) source else "$source — $text"
    /** The marker's words: "Triggered ability" or "Activated ability". */
    val kind: String get() = if (triggered) "Triggered ability" else "Activated ability"
    /** The kind chip's letter (STK-046): "T" for a triggered ability, "A" for an activated one. */
    val letter: String get() = if (triggered) "T" else "A"
}

internal object StackAbilities {
    private val texts = object : java.util.LinkedHashMap<String, String>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 64
    }

    /** The engine's text of [item]'s ability (a keyword's trigger as the keyword's line of [rulesText]), decoded once
     * per item. */
    private fun text(item: MtgStackItem, rulesText: String?): String? = synchronized(texts) {
        texts[item.data] ?: StackAbilityText.of(item, rulesText)?.also { texts[item.data] = it }
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
        return StackAbilityShown(triggered, source, text.trim())
    }

    /**
     * STK-050: what was chosen for the Stack entry [entryId] (an ability, or a spell: owner 2026-11-06), one line each, for
     * Inspect: "Mode: …" ("Modes: …"), "X = 3" for each announced value, and "Target: …" ("Targets: …"), each target named
     * as far as [viewer] may see it ("You", the other player's name in [names], a card's name, "a hidden card"); empty
     * when nothing was chosen.
     */
    fun details(play: TableBoard, entryId: String, viewer: String, names: Map<String, String>): List<String> {
        val item = play.board.stackEffects.singleOrNull { it.id == entryId }?.item ?: return emptyList()
        val modes = StackAbilityText.modes(item)
        val targets = item.targets.values.flatten().map { target -> when (target) {
            is MtgRef.Player -> if (target.accountId == viewer) "You" else names[target.accountId] ?: "Opponent"
            is MtgRef.Card -> visibleName(play, target.cardId, viewer)
            is MtgRef.StackItem -> of(play, target.id, viewer)?.name ?: visibleName(play, target.id, viewer)
        } }
        return listOfNotNull(
            modes.takeIf { it.isNotEmpty() }?.joinToString("; ", prefix = if (modes.size == 1) "Mode: " else "Modes: ")) +
            item.values.map { (name, value) -> "$name = $value" } +
            listOfNotNull(targets.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = if (targets.size == 1) "Target: " else "Targets: "))
    }

    /** The name of the card (or Stack entry) [id] as [viewer] sees it: "a hidden card" when they can't see it face up. */
    internal fun visibleName(play: TableBoard, id: String, viewer: String): String {
        val card = CombatStackRules.inspection(play.board, id) ?: return "a hidden card"
        if (!CardVisibility.isVisibleFaceUp(play.board, card, viewer)) return "a hidden card"
        return play.definitions.firstOrNull { it.digest == card.definition }?.definition?.faces
            ?.let { faces -> faces.firstOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }?.name ?: "a hidden card"
    }
}

/** STK-046: the kind chip of an ability: "T" for a triggered ability, "A" for an activated one, white on violet; it
 * reads as [StackAbilityShown.kind]. [height] sets its size. */
@Composable
internal fun AbilityKindChip(ability: StackAbilityShown, height: Dp, modifier: Modifier = Modifier) {
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
internal fun AbilityTokenFace(ability: StackAbilityShown, art: ArtworkReference?, own: Boolean, modifier: Modifier = Modifier,
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
