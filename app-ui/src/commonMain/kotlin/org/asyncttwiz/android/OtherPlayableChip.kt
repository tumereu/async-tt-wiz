package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import org.asyncttwiz.automatum.StateChip
import org.asyncttwiz.automatum.StateGlyph
import org.asyncttwiz.storage.ArtworkReference

/**
 * ZON-136 (owner playtest 2026-11-19, N17): the other player's cards they may use from outside their hand (cast or play from
 * their graveyard, exile or the top of their library, or an ability that works there: what their own hand row shows them,
 * ZON-105), at one glance: a round chip in their row of round chips (TRN-072), right after their dungeon's room, edged in the
 * playable cyan with "▶" and the count; a tap opens the list of those cards, another tap or a tap elsewhere closes it. Only
 * what you may know: a card you can't see is named by where it is ("The top card of their library").
 */
object OtherPlayableChip {
    /** One listed card: its [name] (or where a hidden one is), its [zone] word and its picture ([artwork], none when hidden). */
    data class Shown(val name: String, val zone: String, val artwork: ArtworkReference?)

    /** The chip for [player]'s [count] cards. */
    fun chip(player: String, count: Int) = StateChip(label(player, count), null, StateGlyph.Letters("▶$count"))

    /** What the chip says aloud and the list's title. */
    fun label(player: String, count: Int) = "$player may play ${if (count == 1) "1 card" else "$count cards"} from other zones"

    /** A card the viewer can't see, by where it is (I-D27: nothing about it is told). */
    fun hiddenName(zone: String): String = when (zone) {
        "library" -> "The top card of their library"
        "exile" -> "A face-down card in their exile"
        else -> "A hidden card"
    }

    /** The zone's word under a listed card's name. */
    fun zoneWord(zone: String): String = when (zone) {
        "graveyard" -> "Graveyard"
        "exile" -> "Exile"
        "library" -> "Library"
        "command" -> "Command zone"
        else -> zone.replaceFirstChar { it.uppercase() }
    }

    const val TAG = "other-playable-chip"
    const val LIST_TAG = "other-playable-list"
}

/** ZON-136: the other player's playable chip ([OtherPlayableChip]) where it lies, opening its list on a tap. */
@Composable
fun OtherPlayableChipView(placed: PlacedStateChip) {
    var open by remember { mutableStateOf(false) }
    val b = placed.bounds
    val scale = b.width / EmblemChips.SIZE
    Box(Modifier.offset { IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }.size(b.width.dp, b.height.dp).testTag(OtherPlayableChip.TAG)
        .semantics { contentDescription = placed.label }
        .clip(CircleShape).background(Color.White).border(1.5.dp, AutomatumCyan, CircleShape)
        .pointerInput(placed.label) {
            detectTapGestures(onTap = {
                open = !open
                AppLog.info("table", "Other player's playable chip ${if (open) "opened" else "closed"}: ${placed.label}: " +
                    placed.playable.joinToString { "${it.name} (${it.zone})" })
            })
        },
        contentAlignment = Alignment.Center) {
        val glyph = (placed.chip?.glyph as? StateGlyph.Letters)?.text ?: "▶"
        Text(glyph, color = PlayableInk, fontSize = ((if (glyph.length > 2) 9f else 10.5f) * scale).sp, fontWeight = FontWeight.Bold,
            lineHeight = (11 * scale).sp, maxLines = 1, softWrap = false)
        if (open) {
            val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
            Popup(popupPositionProvider = remember(gap) { HoldTooltipPosition(gap) }, onDismissRequest = { open = false }) {
                OtherPlayableList(placed.label, placed.playable)
            }
        }
    }
}

/** ZON-136: the list a tap on the chip opens: each card's picture, name and zone. */
@Composable
fun OtherPlayableList(title: String, cards: List<OtherPlayableChip.Shown>) {
    Surface(shape = RoundedCornerShape(8.dp), color = Color.White, shadowElevation = 6.dp,
        modifier = Modifier.widthIn(max = 280.dp).border(1.dp, AutomatumCyan, RoundedCornerShape(8.dp)).testTag(OtherPlayableChip.LIST_TAG)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PlayableInk)
            val density = LocalDensity.current
            for (card in cards) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${card.name}, ${card.zone}" }) {
                val thumb = Modifier.size(40.dp, 30.dp).clip(RoundedCornerShape(3.dp))
                if (card.artwork != null) CardArtwork(card.artwork, card.name, with(density) { 40.dp.roundToPx() },
                    with(density) { 30.dp.roundToPx() }, thumb, crop = true,
                    placeholder = { Box(Modifier.size(40.dp, 30.dp).background(HiddenCard)) })
                else Box(thumb.background(HiddenCard), contentAlignment = Alignment.Center) {
                    Text("?", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(card.name, fontSize = 13.sp, lineHeight = 16.sp, color = Color(0xff202124))
                    Text(card.zone, fontSize = 11.sp, lineHeight = 13.sp, color = Color(0xff5f6368))
                }
            }
        }
    }
}

/** ZON-136: the chip's ink, a dark shade of the playable cyan. */
private val PlayableInk = Color(0xff0b5e6b)
/** A card back's colour, for a card the viewer can't see. */
private val HiddenCard = Color(0xff3c4043)
