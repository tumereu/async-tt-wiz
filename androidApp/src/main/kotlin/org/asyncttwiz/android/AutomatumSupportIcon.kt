package org.asyncttwiz.android

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import org.asyncttwiz.automatum.AutomatumSupport
import org.asyncttwiz.protocol.CardListSupport
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.storage.CardDefinitions
import org.asyncttwiz.storage.ParsedCard
import java.util.concurrent.ConcurrentHashMap

/**
 * I-D51: works out which cards of a list Automatum plays by hand, from each card's rules text as a game parses it
 * ([parse]: the device's card definitions, cached per card data digest and parser version) and Automatum's rule
 * ([AutomatumSupport]), which follows the engine ([engineVersion]). A card whose data can't be read counts as by hand.
 * Blocking: call on a worker only.
 */
internal class CardListSupportCheck(private val parse: (Collection<SubmittedDefinition>) -> Map<String, ParsedCard>,
    val parserVersion: Int = CardDefinitions.parserVersion, val engineVersion: Int = arcanum.engine.Engine.SUPPORT_VERSION,
    /** The dungeons the device can bring (LOB-117): a venturing card without one is played by hand. */
    private val dungeons: () -> Set<org.asyncttwiz.protocol.game.OutsideCards.Need> = { AutomatumSupport.ALL_DUNGEONS }) {
    private val byHand = ConcurrentHashMap<String, Boolean>()

    /** The cards' Automatum definitions as the support is worked out from (the device's cache), e.g. for Stats' mana
     * sources (DRF-115). Blocking. */
    fun parsed(definitions: Collection<SubmittedDefinition>): Map<String, ParsedCard> = parse(definitions.distinctBy { it.digest })

    /** The list's support; throws when the device couldn't read its cards at all (storage closed), so nothing is kept. */
    fun support(definitions: Collection<SubmittedDefinition>): CardListSupport {
        val cards = definitions.distinctBy { it.digest }
        val missing = cards.filterNot { byHand.containsKey(it.digest) }
        if (missing.isNotEmpty()) {
            val parsed = parse(missing)
            val provided by lazy { dungeons() }
            check(missing.all { it.digest in parsed }) { "Card definitions unavailable" }
            parsed.forEach { (digest, card) ->
                byHand[digest] = when (card) {
                    is ParsedCard.Read -> AutomatumSupport.byHand(card.definition, provided)
                    is ParsedCard.Unreadable -> true
                }
            }
        }
        return CardListSupport(parserVersion, cards.filter { byHand[it.digest] == true }.map { it.definition.name }.distinct().sorted(),
            engineVersion)
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
/** Pivot review #2: Ready waits while the device works out which cards of the deck Automatum plays. */
internal const val AUTOMATUM_CHECKING = "Checking which cards Automatum can play…"
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
