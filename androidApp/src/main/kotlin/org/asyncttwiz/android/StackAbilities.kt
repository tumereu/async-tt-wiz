package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.automatum.StackAbilityText
import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.protocol.CardVisibility
import org.asyncttwiz.protocol.CombatStackRules
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
}

internal object StackAbilities {
    private val texts = object : java.util.LinkedHashMap<String, String>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 64
    }

    /** The engine's text of [item]'s ability, decoded once per item. */
    private fun text(item: MtgStackItem): String? = synchronized(texts) {
        texts[item.data] ?: StackAbilityText.of(item)?.also { texts[item.data] = it }
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
        val text = text(item) ?: face?.rulesText?.takeIf { frozen }.orEmpty()
        return StackAbilityShown(triggered, source, text.trim())
    }
}

/** STK-046: the round marker of an ability: a "!" for a triggered ability, a bolt (as the Activate chips) for an activated one. */
@Composable
internal fun AbilityMarker(ability: StackAbilityShown, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(AutomatumViolet, CircleShape).border(1.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center) {
        Icon(if (ability.triggered) Icons.Outlined.PriorityHigh else Icons.Outlined.Bolt, contentDescription = ability.kind,
            tint = Color.White, modifier = Modifier.size(size * .72f))
    }
}

/**
 * STK-046: an ability on the Stack outside the table (the Stack list, the Stack target picker), drawn as the table draws
 * it: a dark token smaller than a card with the controller's outline ([own]), the source's [art] in its top part (its
 * type glyph [typeIcon] while there is none), the marker, and with [text] the source's name and the ability's text.
 */
@Composable
internal fun AbilityTokenFace(ability: StackAbilityShown, art: ArtworkReference?, own: Boolean, modifier: Modifier = Modifier,
    typeIcon: String = "", text: Boolean = true) {
    val owner = if (own) OwnTableColor else OpponentTableColor
    val shape = RoundedCornerShape(5.dp)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val markerSize = minOf(maxWidth * .3f, 14.dp)
        Box(Modifier.fillMaxWidth(.86f).fillMaxHeight(.8f)) {
            Column(Modifier.fillMaxSize().clip(shape).background(AbilityTokenColor).border(1.5.dp, owner, shape)) {
                Box(Modifier.fillMaxWidth().weight(if (text) .45f else 1f).background(AbilityTokenArtColor)) {
                    CardArtwork(art, "", 192, 140, Modifier.fillMaxSize(), crop = true, placeholder = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(typeIcon, fontSize = 14.sp, color = Color.White)
                        }
                    })
                }
                if (text) Column(Modifier.fillMaxWidth().weight(.55f).padding(start = 3.dp, end = 3.dp, top = markerSize / 2)) {
                    Text(ability.source, fontSize = 7.sp, lineHeight = 8.sp, maxLines = 1, fontWeight = FontWeight.Bold,
                        color = Color.White, overflow = TextOverflow.Ellipsis)
                    Text(ability.text, fontSize = 6.sp, lineHeight = 7.sp, color = AbilityTokenTextColor, overflow = TextOverflow.Ellipsis)
                }
            }
            AbilityMarker(ability, markerSize, Modifier
                .align(if (text) BiasAlignment(-1f, -.12f) else Alignment.BottomStart).offset(x = (-2).dp))
        }
    }
}
