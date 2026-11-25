package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.CardSearchPrinting
import org.asyncttwiz.protocol.DraftBot
import org.asyncttwiz.protocol.DraftBotCard
import org.asyncttwiz.protocol.DraftBotChoice
import org.asyncttwiz.protocol.DraftBotRatings
import org.asyncttwiz.protocol.DraftBotScore
import org.asyncttwiz.protocol.DraftCopy
import org.asyncttwiz.protocol.PodSource
import org.asyncttwiz.protocol.SetCardRarity

/** LOB-263: whether this build offers "Show bot picks": test builds only (debug and the personal test build), never a
 * release, as the error reports' lock (LOB-232). */
val draftBotSettingOffered: Boolean get() = AppBuild.DIAGNOSTICS

/** LOB-263: "Show bot picks", kept on the device, off by default. */
class DraftBotPreferences(platform: AppPlatform) {
    private val preferences = platform.preferences("draft-bot")
    var enabled: Boolean
        get() = preferences.getBoolean(KEY, false)
        set(value) {
            AppLog.info("settings", "Show bot picks ${if (value) "on" else "off"}")
            preferences.edit().putBoolean(KEY, value).apply()
        }
    fun observe(onChange: () -> Unit): () -> Unit = preferences.observe { changed -> if (changed == null || changed == KEY) onChange() }
    private companion object { const val KEY = "show" }
}

@Composable
private fun rememberDraftBotPreference(): Pair<State<Boolean>, DraftBotPreferences> {
    val platform = AppPlatform.current
    val preferences = remember(platform) { DraftBotPreferences(platform) }
    val state = remember(preferences) { mutableStateOf(preferences.enabled) }
    DisposableEffect(preferences) {
        val stop = preferences.observe { state.value = preferences.enabled }
        state.value = preferences.enabled
        onDispose { stop() }
    }
    return state to preferences
}

/** DRF-292: whether the draft screens show the bot's picks: a test build with the setting on. */
@Composable
fun rememberShowBotPicks(): Boolean = draftBotSettingOffered && rememberDraftBotPreference().first.value

/** LOB-263: the Settings switch, in About & account under "Send error reports"; test builds only. */
@Composable
fun DraftBotSetting() {
    if (!draftBotSettingOffered) return
    val (state, preferences) = rememberDraftBotPreference()
    val enabled = state.value
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("draft-bot-setting")
        .toggleable(enabled, role = Role.Switch, onValueChange = { preferences.enabled = it }),
        verticalAlignment = Alignment.CenterVertically) {
        SettingsLabel("Show bot picks", "Draft screens show the draft bot's score on each card and its pick. Test builds only",
            Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
        Switch(checked = enabled, onCheckedChange = null)
    }
}

/** LOB-275: About & account's credit for the draft bots' card ratings (DRF-312), 17Lands' public data under CC BY 4.0. */
const val CARD_RATINGS_CREDIT = "Draft bots' card ratings: 17Lands public data (17lands.com), CC BY 4.0"

/** LOB-275: the card ratings' credit line, above the version line. */
@Composable
fun CardRatingsCredit() {
    Text(CARD_RATINGS_CREDIT, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("card-ratings-credit"),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** DRF-292: what the draft screens need to show the bot's picks: card records' rarities ([printings], by printing id), a
 * way to ask for more ([load]) and the pod's card ratings as its bots use them ([ratings], DRF-312; null for none). Present
 * only while "Show bot picks" is on. */
class DraftBotDebug(val printings: Map<String, List<CardSearchPrinting>>, val load: (Collection<String>) -> Unit,
    val ratings: DraftBotRatings? = null)

val LocalDraftBot = compositionLocalOf<DraftBotDebug?> { null }

/** DRF-292: [content] (a pod's Draft tab) with [LocalDraftBot] while "Show bot picks" is on; rarities come through the pod's
 * [filters] (DRF-252's reading of card records), and so do the card ratings of its set where its bots pick by them
 * ([PodSource.botRatings], DRF-312). */
@Composable
fun PodDraftBotScope(filters: PodCardFilters, source: PodSource, content: @Composable () -> Unit) {
    val show = rememberShowBotPicks()
    val printings = filters.printings
    val set = podBotRatingsSet(source)
    LaunchedEffect(show, set) { if (show && set != null) filters.loadRatings(set) }
    val ratings = set?.let { filters.ratings[it] }
    val debug = if (show) remember(filters, printings, ratings) { DraftBotDebug(printings, filters::load, ratings) } else null
    CompositionLocalProvider(LocalDraftBot provides debug, content = content)
}

/** DRF-312: the set whose card ratings a pod's bots pick by ([PodSource.botRatings]: one set the server has them for), or
 * null for none. */
fun podBotRatingsSet(source: PodSource): String? =
    source.packSets.ifEmpty { listOfNotNull(source.setCode) }.distinct().singleOrNull()?.takeIf { source.botRatings }

/** A card record's rarity word as a set rarity, null for an unknown one. */
private fun draftBotRarity(printings: List<CardSearchPrinting>?): SetCardRarity? = printings?.firstNotNullOfOrNull { printing ->
    SetCardRarity.entries.firstOrNull { it.name.equals(printing.rarity, ignoreCase = true) }
}

/**
 * DRF-292: the bot's choice for [offer], picking [keep] cards with [pool] its cards so far, at pick number pool size / keep + 1;
 * rarities from [printings] where card records know them; by the pod's card [ratings] where it has them (DRF-312).
 */
fun draftBotChoice(offer: List<DraftCopy>, pool: List<DraftCopy>, keep: Int, definitions: Map<String, CardDefinition>,
    printings: Map<String, List<CardSearchPrinting>>, ratings: DraftBotRatings? = null): DraftBotChoice {
    fun card(copy: DraftCopy) = definitions[copy.definition].let { definition ->
        DraftBotCard(copy.id, definition, draftBotRarity(definition?.printingId?.let(printings::get)))
    }
    val take = keep.coerceAtLeast(1)
    return DraftBot.choose(offer.map(::card), pool.map(::card), pool.size / take + 1, take, ratings)
}

/** DRF-292: the bot's choice for [offer] while "Show bot picks" is on ([LocalDraftBot]), else null. */
@Composable
fun rememberDraftBotChoice(offer: List<DraftCopy>, pool: List<DraftCopy>, keep: Int, definitions: Map<String, CardDefinition>): DraftBotChoice? =
    rememberDraftBotChoices(mapOf("" to offer), pool, keep, definitions)[""]

/** DRF-292: the bot's choice for each of [offers] (several picks at once: Rochester's open packs where it is your pick),
 * by the same keys, while "Show bot picks" is on; empty while it is off. Empty offers have none. */
@Composable
fun rememberDraftBotChoices(offers: Map<String, List<DraftCopy>>, pool: List<DraftCopy>, keep: Int,
    definitions: Map<String, CardDefinition>): Map<String, DraftBotChoice> {
    val debug = LocalDraftBot.current
    val ids = remember(offers, pool, definitions) {
        (offers.values.flatten() + pool).mapNotNullTo(HashSet()) { definitions[it.definition]?.printingId }
    }
    LaunchedEffect(debug != null, ids) { debug?.load?.invoke(ids) }
    val printings = debug?.printings
    val ratings = debug?.ratings
    return remember(debug != null, offers, pool, keep, definitions, printings, ratings) {
        if (debug == null) emptyMap() else offers.filterValues { it.isNotEmpty() }.mapValues { (key, offer) ->
            draftBotChoice(offer, pool, keep, definitions, printings.orEmpty(), ratings).also { choice ->
                AppLog.info("draft-bot", "Bot picks ${choice.picks} of ${offer.size}${if (key.isEmpty()) "" else " in $key"} " +
                    "(${ratings ?: "no card ratings"}; colours ${choice.colors.joinToString("")}, commitment ${choice.commitment}%" +
                    "${choice.splash?.let { ", splash ${it.name}" } ?: ""}): " +
                    choice.scores.joinToString { "${it.card} ${it.total}" })
            }
        }
    }
}

private val BotGrey = Color(0xE0303030)
private val BotPick = Color(0xFF6A1B9A)

/** "Bot 52", "Bot pick 67" on the bot's own pick. */
fun draftBotBadgeText(choice: DraftBotChoice, card: String): String? {
    val score = choice.score(card) ?: return null
    return if (card in choice.picks) "Bot pick ${score.total}" else "Bot ${score.total}"
}

/** The breakdown's lines: "Base 40", "Removal +12", …, "Total 52". */
fun draftBotBreakdown(score: DraftBotScore): List<String> = score.parts.mapIndexed { index, part ->
    if (index == 0) "${part.label} ${part.points}" else "${part.label} ${if (part.points > 0) "+" else ""}${part.points}"
} + "Total ${score.total}"

/**
 * DRF-292: the bot's score on a card, at its bottom left: grey, or purple with "Bot pick" and a purple frame
 * on the card the bot would take; a tap shows how the score is made up.
 */
@Composable
fun BoxScope.DraftBotBadge(choice: DraftBotChoice?, card: String) {
    val score = choice?.score(card) ?: return
    val text = draftBotBadgeText(choice, card) ?: return
    val pick = card in choice.picks
    var open by remember(card) { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    if (pick) Box(Modifier.matchParentSize().padding(3.dp).border(2.dp, BotPick, shape).testTag("draft-bot-frame-$card"))
    Box(Modifier.align(Alignment.BottomStart).padding(4.dp)) {
        Text(text, Modifier.background(if (pick) BotPick else BotGrey, shape)
            .clickable(onClickLabel = "Bot score", role = Role.Button) {
                AppLog.info("draft-bot", "Breakdown of $card: ${draftBotBreakdown(score)}"); open = true
            }
            .padding(horizontal = 4.dp, vertical = 1.dp).testTag("draft-bot-$card").semantics { contentDescription = text },
            color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        DropdownMenu(open, { open = false }, Modifier.testTag("draft-bot-breakdown")) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                draftBotBreakdown(score).forEachIndexed { index, line ->
                    Text(line, style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (index == score.parts.size) FontWeight.Bold else FontWeight.Normal)
                }
                if (choice.colors.isNotEmpty()) Text("Colours ${choice.colors.joinToString("") { it.name }} · commitment ${choice.commitment}%" +
                    (choice.splash?.let { " · splash ${it.name}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
