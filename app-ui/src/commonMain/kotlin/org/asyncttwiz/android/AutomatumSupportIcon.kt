package org.asyncttwiz.android

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.asyncttwiz.protocol.CardListSupport
import org.asyncttwiz.protocol.SubmittedDefinition
import arcanum.parser.OracleParser
import org.asyncttwiz.storage.ParsedCard
import org.asyncttwiz.appui.ioDispatcher
import org.asyncttwiz.appui.concurrentMapOf

/**
 * LOB-115 (section 3.8): which cards of a list Automatum can't play yet, as the card catalog marks them: a card is
 * supported when its printing is ([unsupported]: of the printing ids asked for, those the card records don't mark as
 * supported; null without card records). A card without a printing counts as unsupported. Blocking: a worker only.
 */
class CardListSupportCheck(private val parse: (Collection<SubmittedDefinition>) -> Map<String, ParsedCard>,
    private val unsupported: (Collection<String>) -> Set<String>?,
    val parserVersion: Int = OracleParser.VERSION, val engineVersion: Int = arcanum.engine.CardSupport.SUPPORT_VERSION) {

    /** The cards' Automatum definitions (the device's cache), for Stats' mana sources (DRF-115). Blocking. */
    fun parsed(definitions: Collection<SubmittedDefinition>): Map<String, ParsedCard> = parse(definitions.distinctBy { it.digest })

    /** The list's support; throws when the card records aren't installed, so nothing is kept. */
    fun support(definitions: Collection<SubmittedDefinition>): CardListSupport {
        val cards = definitions.distinctBy { it.digest }
        val marks = checkNotNull(unsupported(cards.mapNotNull { it.definition.printingId })) { "Card records not installed" }
        return CardListSupport(parserVersion, cards.filter { card -> card.definition.printingId.let { it == null || it in marks } }
            .map { it.definition.name }.distinct().sorted(), engineVersion)
    }
}

/** The app's support check, null where there is none (previews and most tests: no icon is shown). */
val LocalCardListSupport = staticCompositionLocalOf<CardListSupportCheck?> { null }

/** [definitions]' support, worked out off the main thread; null until known (or without a check). */
@Composable
fun rememberCardListSupport(definitions: List<SubmittedDefinition>?): CardListSupport? {
    val check = LocalCardListSupport.current ?: return null
    val key = definitions?.map { it.digest }?.sorted()
    val support by produceState<CardListSupport?>(null, check, key) {
        value = definitions?.takeIf { it.isNotEmpty() }?.let { cards ->
            withContext(Dispatchers.Default) { runCatching { check.support(cards) }.getOrNull() }
        }
    }
    return support
}

/** LOB-115 (owner gallery review, 2026-11-06): "Automatum can't play 3 cards yet: A, B, C" (at most [shown] names, then
 * "and N more"). */
fun automatumSupportTip(support: CardListSupport, shown: Int = 12): String {
    val names = support.byHand
    val count = if (names.size == 1) "1 card" else "${names.size} cards"
    val listed = names.take(shown).joinToString(", ") + if (names.size > shown) " and ${names.size - shown} more" else ""
    return "Automatum can't play $count yet: $listed"
}

val AutomatumWarning = Color(0xffc77700)

/** I-D52: why an unsupported list can't be used (hold the warning icon for its cards). */
const val AUTOMATUM_DECK_BLOCKED = "Automatum can't play every card of this deck yet. Hold the warning sign to see which."
const val AUTOMATUM_SOURCE_BLOCKED = "Automatum can't play every card of this source yet. Hold the warning sign to see which."

/**
 * I-D51 (LOB-115, DRF-161): an amber warning icon when [support] names cards Automatum plays by hand; nothing when it
 * plays every card or isn't known yet. Holding it names the cards (I-D48); a tap does [onTap] (the row's own action).
 */
@Composable
fun AutomatumSupportIcon(support: CardListSupport?, modifier: Modifier = Modifier, onTap: (() -> Unit)? = null) {
    if (support == null || support.supported) return
    HoldWarningIcon(automatumSupportTip(support), modifier, onTap)
}

/** An amber warning icon: holding it says [tip] (I-D48); a tap does [onTap] (the row's own action). */
@Composable
fun HoldWarningIcon(tip: String, modifier: Modifier = Modifier, onTap: (() -> Unit)? = null) {
    var tooltip by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    Box(modifier.sizeIn(minWidth = 32.dp, minHeight = 32.dp).semantics { contentDescription = tip }
        .pointerInput(Unit) {
            detectTapGestures(onPress = { tryAwaitRelease(); tooltip = false }, onTap = { tap?.invoke() },
                onLongPress = { tooltip = true })
        }, contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.WarningAmber, null, Modifier.size(20.dp), tint = AutomatumWarning)
        if (tooltip) HoldTooltip(tip)
    }
}

/** Section 3.9: what holding a card's warning sign says. */
const val AUTOMATUM_CARD_UNSUPPORTED = "Automatum can't play this card yet"

/**
 * Section 3.9: which cards Automatum can't play yet, by their pictures' printing (Scryfall id), as the card catalog marks
 * them ([unsupported]: of the ids asked for, those of no supported printing; null without card records). Answers are
 * kept for the app's run (the records change only with a new app). Blocking: a worker only.
 */
class CardMarks(private val unsupported: (Collection<String>) -> Set<String>?) {
    private val known = concurrentMapOf<String, Boolean>()
    fun unsupported(scryfallId: String): Boolean? = known[scryfallId]
        ?: unsupported(listOf(scryfallId))?.let { (scryfallId in it).also { value -> known[scryfallId] = value } }
}

/** The app's card marks, null where there are none (previews, most tests: no sign is shown). */
val LocalCardMarks = staticCompositionLocalOf<CardMarks?> { null }

/**
 * LOB-144 (owner gallery review 2026-11-10, gallery 188): a card Automatum can't play yet ([scryfallId]: its picture's printing)
 * carries a warning triangle stamped over its picture's art box: the triangle alone (no disc), as big as the art box's smaller
 * side and centred in it, a little translucent like a stamp, with a thin dark outline so it reads on light and dark art alike.
 * [modifier] gives it the card picture's bounds (the caller's matchParentSize). Holding the triangle says so; taps and holds
 * still reach the card under it. Nothing while unknown or for a supported card.
 */
@Composable
fun CardSupportMark(scryfallId: String?, modifier: Modifier = Modifier) {
    val marks = LocalCardMarks.current ?: return
    if (scryfallId.isNullOrEmpty()) return
    val unsupported by produceState(false, marks, scryfallId) {
        value = withContext(ioDispatcher) { runCatching { marks.unsupported(scryfallId) }.getOrNull() } == true
    }
    if (unsupported) BoxWithConstraints(modifier) {
        val art = CardArtBox.of(maxWidth.value, maxHeight.value)
        val side = minOf(art.width, art.height)
        UnsupportedStamp(Modifier.offset((art.x + (art.width - side) / 2).dp, (art.y + (art.height - side) / 2).dp).size(side.dp))
    }
}

/** LOB-144: where a printed card's art box lies in its picture (a modern frame's, as fractions of the card's size). */
object CardArtBox {
    const val LEFT = .08f
    const val TOP = .11f
    const val RIGHT = .92f
    const val BOTTOM = .555f
    /** The art box of a card picture [width] by [height] (dp). */
    fun of(width: Float, height: Float) = TableBounds(width * LEFT, height * TOP, width * (RIGHT - LEFT), height * (BOTTOM - TOP))
}

/** LOB-144: how see-through the stamp's amber fill is, and its ink. */
private const val STAMP_FILL_ALPHA = .5f
private val StampEdge = Color(0xff2b1d00)

/** LOB-144: the warning triangle stamp (see [CardSupportMark]); holding it says [AUTOMATUM_CARD_UNSUPPORTED]. */
@Composable
fun UnsupportedStamp(modifier: Modifier) {
    var tooltip by remember { mutableStateOf(false) }
    Box(modifier.semantics { contentDescription = AUTOMATUM_CARD_UNSUPPORTED }.then(SharedHoldElement { tooltip = it })) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            val edge = (minOf(w, h) * .03f).coerceAtLeast(1.5.dp.toPx())
            val triangle = Path().apply {
                moveTo(w * .5f, h * .07f); lineTo(w * .97f, h * .9f); lineTo(w * .03f, h * .9f); close()
            }
            drawPath(triangle, AutomatumWarning.copy(alpha = STAMP_FILL_ALPHA))
            drawPath(triangle, StampEdge.copy(alpha = .75f), style = Stroke(edge, join = StrokeJoin.Round))
            // The exclamation mark, in the edge's ink
            val bar = w * .075f
            drawRoundRect(StampEdge.copy(alpha = .8f), Offset(w * .5f - bar / 2, h * .36f), Size(bar, h * .3f), CornerRadius(bar / 2))
            drawCircle(StampEdge.copy(alpha = .8f), bar * .62f, Offset(w * .5f, h * .76f))
        }
        if (tooltip) HoldTooltip(AUTOMATUM_CARD_UNSUPPORTED)
    }
}

/**
 * LOB-144: a hold on a sign lying over a card ([onHold] true after the long-press timeout, false on release or cancel) that
 * shares every touch with the siblings under it, so the card's own taps and holds go on working there.
 */
private data class SharedHoldElement(val onHold: (Boolean) -> Unit) : ModifierNodeElement<SharedHoldNode>() {
    override fun create() = SharedHoldNode(onHold)
    override fun update(node: SharedHoldNode) { node.onHold = onHold }
}

private class SharedHoldNode(var onHold: (Boolean) -> Unit) : Modifier.Node(), PointerInputModifierNode,
    CompositionLocalConsumerModifierNode {
    private var waiting: Job? = null
    private var down: Offset? = null
    private var held = false

    override fun sharePointerInputWithSiblings() = true

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Main) return
        val change = pointerEvent.changes.firstOrNull() ?: return
        val configuration = currentValueOf(LocalViewConfiguration)
        when {
            change.changedToDown() -> {
                end()
                down = change.position
                waiting = coroutineScope.launch {
                    delay(configuration.longPressTimeoutMillis)
                    held = true
                    onHold(true)
                }
            }
            !change.pressed -> end()
            !held -> down?.let { if ((change.position - it).getDistance() > configuration.touchSlop) end() }
        }
    }

    override fun onCancelPointerInput() = end()

    override fun onDetach() { if (held) onHold(false); held = false }

    private fun end() {
        waiting?.cancel(); waiting = null; down = null
        if (held) { held = false; onHold(false) }
    }
}
