package org.asyncttwiz.android

import androidx.compose.foundation.gestures.detectTapGestures
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
import org.asyncttwiz.storage.CardDefinitions
import org.asyncttwiz.storage.ParsedCard

/**
 * LOB-115 (section 3.8): which cards of a list Automatum can't play yet, as the card catalog marks them: a card is
 * supported when its printing is ([unsupported]: of the printing ids asked for, those the card records don't mark as
 * supported; null without card records). A card without a printing counts as unsupported. Blocking: a worker only.
 */
internal class CardListSupportCheck(private val parse: (Collection<SubmittedDefinition>) -> Map<String, ParsedCard>,
    private val unsupported: (Collection<String>) -> Set<String>?,
    val parserVersion: Int = CardDefinitions.parserVersion, val engineVersion: Int = arcanum.engine.Engine.SUPPORT_VERSION) {

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
internal val LocalCardListSupport = staticCompositionLocalOf<CardListSupportCheck?> { null }

/** [definitions]' support, worked out off the main thread; null until known (or without a check). */
@Composable
internal fun rememberCardListSupport(definitions: List<SubmittedDefinition>?): CardListSupport? {
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
internal fun automatumSupportTip(support: CardListSupport, shown: Int = 12): String {
    val names = support.byHand
    val count = if (names.size == 1) "1 card" else "${names.size} cards"
    val listed = names.take(shown).joinToString(", ") + if (names.size > shown) " and ${names.size - shown} more" else ""
    return "Automatum can't play $count yet: $listed"
}

internal val AutomatumWarning = Color(0xffc77700)

/** I-D52: why an unsupported list can't be used (hold the warning icon for its cards). */
internal const val AUTOMATUM_DECK_BLOCKED = "Automatum can't play every card of this deck yet. Hold the warning sign to see which."
internal const val AUTOMATUM_SOURCE_BLOCKED = "Automatum can't play every card of this source yet. Hold the warning sign to see which."

/**
 * I-D51 (LOB-115, DRF-161): an amber warning icon when [support] names cards Automatum plays by hand; nothing when it
 * plays every card or isn't known yet. Holding it names the cards (I-D48); a tap does [onTap] (the row's own action).
 */
@Composable
internal fun AutomatumSupportIcon(support: CardListSupport?, modifier: Modifier = Modifier, onTap: (() -> Unit)? = null) {
    if (support == null || support.supported) return
    HoldWarningIcon(automatumSupportTip(support), modifier, onTap)
}

/** An amber warning icon: holding it says [tip] (I-D48); a tap does [onTap] (the row's own action). */
@Composable
internal fun HoldWarningIcon(tip: String, modifier: Modifier = Modifier, onTap: (() -> Unit)? = null) {
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
internal const val AUTOMATUM_CARD_UNSUPPORTED = "Automatum can't play this card yet"

/**
 * Section 3.9: which cards Automatum can't play yet, by their pictures' printing (Scryfall id), as the card catalog marks
 * them ([unsupported]: of the ids asked for, those of no supported printing; null without card records). Answers are
 * kept for the app's run (the records change only with a new app). Blocking: a worker only.
 */
internal class CardMarks(private val unsupported: (Collection<String>) -> Set<String>?) {
    private val known = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    fun unsupported(scryfallId: String): Boolean? = known[scryfallId]
        ?: unsupported(listOf(scryfallId))?.let { (scryfallId in it).also { value -> known[scryfallId] = value } }
}

/** The app's card marks, null where there are none (previews, most tests: no sign is shown). */
internal val LocalCardMarks = staticCompositionLocalOf<CardMarks?> { null }

/**
 * Section 3.9 (deck building, card search, the visual deck editor, Inspect): LOB-115's amber warning sign over the art's
 * corner of a card Automatum can't play yet ([scryfallId]: its picture's printing); holding it says so. Nothing while
 * unknown or for a supported card.
 */
@Composable
internal fun CardSupportMark(scryfallId: String?, modifier: Modifier = Modifier) {
    val marks = LocalCardMarks.current ?: return
    if (scryfallId.isNullOrEmpty()) return
    val unsupported by produceState(false, marks, scryfallId) {
        value = withContext(Dispatchers.IO) { runCatching { marks.unsupported(scryfallId) }.getOrNull() } == true
    }
    if (unsupported) androidx.compose.material3.Surface(modifier.padding(4.dp), shape = androidx.compose.foundation.shape.CircleShape,
        color = Color.White.copy(alpha = .9f)) {
        HoldWarningIcon(AUTOMATUM_CARD_UNSUPPORTED, Modifier.size(32.dp))
    }
}
