package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import org.asyncttwiz.automatum.StateChip
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.automatum.StateGlyph
import org.asyncttwiz.storage.ArtworkReference

/**
 * ZON-136 (owner playtest 2026-11-19, N17): the other player's cards they may use from outside their hand (cast or play from
 * their graveyard, exile or the top of their library, or an ability that works there: what their own hand row shows them,
 * ZON-105), at one glance: a round chip in their row of round chips (TRN-072), right after their dungeon's room, edged in the
 * playable cyan with "▶" and the count; a tap opens the list of those cards, another tap or a tap elsewhere closes it. Only
 * what you may know: a card you can't see is a card back, named by where it is ("The top card of their library").
 */
object OtherPlayableChip {
    /** One listed card: its [name] (or where a hidden one is), its [zone] word (read aloud), its picture ([artwork], none when
     * hidden), the ATW card ([cardId], null when hidden), for Inspect, and its [pile] (whose badge shows under it; none for a
     * zone without a pile, whose word shows instead). */
    data class Shown(val name: String, val zone: String, val artwork: ArtworkReference?, val cardId: String? = null,
        val pile: PlayZone? = null)

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

    /** The pile of [zone] whose badge shows under a listed card (owner 2026-11-22, F4: as the viewer's own hand row cards
     * from other zones carry, ZON-106); null for a zone without a pile on the table (the command zone). */
    fun pile(zone: String): PlayZone? = when (zone) {
        "graveyard" -> PlayZone.GRAVEYARD
        "exile" -> PlayZone.EXILE
        "library" -> PlayZone.LIBRARY
        else -> null
    }

    /** The zone's word a listed card is read aloud with ("…, Graveyard"), and shown under it without a [pile]. */
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

/** ZON-136: the other player's playable chip ([OtherPlayableChip]) where it lies, opening its list on a tap; [play] and
 * [viewer] for Inspect of a listed card. */
@Composable
fun OtherPlayableChipView(placed: PlacedStateChip, play: TableBoard?, viewer: String) {
    var open by remember { mutableStateOf(false) }
    val toggle = {
        open = !open
        AppLog.info("table", "Other player's playable chip ${if (open) "opened" else "closed"}: ${placed.label}: " +
            placed.playable.joinToString { "${it.name} (${it.zone})" })
    }
    val b = placed.bounds
    val scale = b.width / EmblemChips.SIZE
    Box(Modifier.offset { IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }.size(b.width.dp, b.height.dp).testTag(OtherPlayableChip.TAG)
        // WEB-058: a click target, so the mouse shows the hand over it
        .semantics { contentDescription = placed.label; onClick { toggle(); true } }
        .clip(CircleShape).background(Color.White).border(1.5.dp, AutomatumCyan, CircleShape)
        .hoverLook(HoverLook.Tint(PlayableInk))
        .pointerInput(placed.label) { detectTapGestures(onTap = { toggle() }) },
        contentAlignment = Alignment.Center) {
        val glyph = (placed.chip?.glyph as? StateGlyph.Letters)?.text ?: "▶"
        Text(glyph, color = PlayableInk, fontSize = ((if (glyph.length > 2) 9f else 10.5f) * scale).sp, fontWeight = FontWeight.Bold,
            lineHeight = (11 * scale).sp, maxLines = 1, softWrap = false)
        if (open) {
            val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
            Popup(popupPositionProvider = remember(gap) { HoldTooltipPosition(gap) }, onDismissRequest = { open = false }) {
                OtherPlayableList(placed.label, placed.playable, play, viewer)
            }
        }
    }
}

/** ZON-136 (owner 2026-11-21, E9: cards as cards): the list a tap on the chip opens: each card drawn as a small card, as a
 * choice panel's are ([ChoiceCardFace]), with its zone's pile badge under it (owner 2026-11-22, F4: [ZoneBadge], as the
 * viewer's own hand row cards from other zones carry); holding one the viewer may see inspects it ([play], [viewer]); one they
 * can't is a card back. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OtherPlayableList(title: String, cards: List<OtherPlayableChip.Shown>, play: TableBoard?, viewer: String) {
    val inspector = remember { ChoiceInspector() }
    Surface(shape = RoundedCornerShape(8.dp), color = Color.White, shadowElevation = 6.dp,
        modifier = Modifier.widthIn(max = 280.dp).border(1.dp, AutomatumCyan, RoundedCornerShape(8.dp)).testTag(OtherPlayableChip.LIST_TAG)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PlayableInk)
            val (cardWidth, _) = choiceCardSize()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.forEachIndexed { n, card ->
                    val data = ChoiceCardData("other-playable/$n", card.cardId, card.name, card.artwork, shown = card.cardId != null, own = false)
                    Column(Modifier.widthIn(min = cardWidth).semantics(mergeDescendants = true) { contentDescription = "${card.name}, ${card.zone}" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ChoiceCardFace(data, Modifier.choiceCardGestures(data.key, tap = {}, hold = { holding ->
                            if (holding) AppLog.info("table", "Other player's playable card held: ${card.name} (${card.zone})")
                            inspector.hold(data, holding)
                        }, moved = inspector::moved))
                        card.pile?.let { ZoneBadge(it, Modifier.testTag("$ZONE_BADGE_TAG/${it.name.lowercase()}")) }
                            ?: Text(card.zone, fontSize = 11.sp, lineHeight = 13.sp, color = Color(0xff5f6368), maxLines = 1)
                    }
                }
            }
        }
    }
    if (play != null) ChoiceInspection(inspector, play, viewer)
}

/** F4: the test tag of a listed card's zone badge, followed by "/" and its zone in lower case. */
const val ZONE_BADGE_TAG = "zone-badge"

/** ZON-136, ZON-106 (owner 2026-11-22, F4): a pile's zone badge on its own ([drawZoneBadge]), as a hand row card from another
 * zone carries in its corner. */
@Composable
fun ZoneBadge(zone: PlayZone, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size((2 * ZONE_BADGE_RADIUS).dp)) {
        drawZoneBadge(zone, ZONE_BADGE_RADIUS, ZONE_BADGE_RADIUS, ZONE_BADGE_RADIUS - .5f, density)
    }
}

/** F4: the zone badge's radius (dp) under a listed card, about as large as a hand row card's. */
private const val ZONE_BADGE_RADIUS = 10f

/** ZON-136: the chip's ink, a dark shade of the playable cyan. */
private val PlayableInk = Color(0xff0b5e6b)
