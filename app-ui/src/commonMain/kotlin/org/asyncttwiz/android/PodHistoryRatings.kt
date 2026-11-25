package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DraftBotRatings
import org.asyncttwiz.protocol.DraftCopy
import org.asyncttwiz.protocol.PodDetail

/** DRF-317: the citation the draft history dialogs show at the top of their lists whenever card ratings show. */
const val POD_RATINGS_CITATION = "Ratings: 17Lands"

/**
 * DRF-315 (owner, 2026-11-25): the set whose card ratings pod [detail]'s draft history dialogs show: the set its bots pick by
 * ([podBotRatingsSet], DRF-312: one set that has ratings), once the caller's own draft is done (DRF-119, [podOwnDraftDone]);
 * null before that and in every other pod.
 */
fun podHistoryRatingsSet(detail: PodDetail): String? = podBotRatingsSet(detail.source)?.takeIf { podOwnDraftDone(detail) }

/** Why pod [detail]'s draft history shows no card ratings, for the app log; null when it shows them (DRF-315). */
fun podHistoryNoRatingsReason(detail: PodDetail): String? = when {
    podBotRatingsSet(detail.source) == null -> "the pod isn't of one set with card ratings"
    !podOwnDraftDone(detail) -> "your own draft isn't done yet"
    else -> null
}

/**
 * DRF-315: one of a card's five rating steps within its set, by how many of the set's rated cards rate higher: the top 10 %,
 * the next 15 %, the middle, the next 15 % and the bottom 10 %.
 */
sealed class PodRatingStep(val rank: Int) {
    data object Top : PodRatingStep(0)
    data object High : PodRatingStep(1)
    data object Middle : PodRatingStep(2)
    data object Low : PodRatingStep(3)
    data object Bottom : PodRatingStep(4)
}

/** DRF-315: the draft history's view of a set's card [ratings]: a card's rating, its text and its step within the set. */
class PodHistoryRatings(val ratings: DraftBotRatings) {
    private val descending = ratings.table.values.sortedDescending()

    /** [definition]'s rating in tenths of a percent, null for a card without one. */
    fun rating(definition: CardDefinition?): Int? = definition?.let(ratings::rating)

    /** [rating]'s step: the share of the set's rated cards rating strictly higher puts it in its step. */
    fun step(rating: Int): PodRatingStep {
        // The first place in the descending list holding [rating] or lower: how many rate higher
        val higher = descending.indexOfFirst { it <= rating }.let { if (it < 0) descending.size else it }
        val share = higher.toDouble() / descending.size
        return when {
            share < .10 -> PodRatingStep.Top
            share < .25 -> PodRatingStep.High
            share < .75 -> PodRatingStep.Middle
            share < .90 -> PodRatingStep.Low
            else -> PodRatingStep.Bottom
        }
    }

    /** [copies] best first by rating, the cards without one after them in their own order (DRF-316). */
    fun <T> byRating(copies: List<T>, definition: (T) -> CardDefinition?): List<T> =
        copies.withIndex().sortedWith(compareBy({ rating(definition(it.value))?.let { r -> -r } ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }

    override fun toString() = ratings.toString()
}

/** DRF-315: a rating in tenths of a percent as its chip reads it, "58.4%". */
fun podRatingText(rating: Int): String = "${rating / 10}.${rating % 10}%"

/** DRF-316: a draft's copies sorted best first by [ratings] (unrated after, in their own order); unchanged without ratings. */
fun podHistoryRatingOrder(cards: List<DraftCopy>, definitions: Map<String, CardDefinition>, ratings: PodHistoryRatings?): List<DraftCopy> =
    ratings?.byRating(cards) { definitions[it.definition] } ?: cards

/** DRF-316: an opponent's picks best first by [ratings]; card backs (null) and unrated cards after, in their own order. */
fun podPicksRatingOrder(cards: List<DraftCopy?>, definitions: Map<String, CardDefinition>, ratings: PodHistoryRatings?): List<DraftCopy?> =
    ratings?.byRating(cards) { copy -> copy?.let { definitions[it.definition] } } ?: cards

/**
 * DRF-315 (owner, 2026-11-25): the rating chips' colours, one per step from the top down: gold at the top down to grey,
 * never green or red (the keeps' and burns' colours). [GoldToGrey] is the one the app uses; the others are offered to the
 * owner in the gallery.
 */
sealed class PodRatingPalette(val name: String, private val fills: List<Long>, private val darkText: Int) {
    /** The chip's fill for [step]. */
    fun fill(step: PodRatingStep) = Color(fills[step.rank])
    /** The chip's text colour for [step]: dark on the light steps, white on the dark ones. */
    fun text(step: PodRatingStep) = if (step.rank < darkText) Color(0xFF2B2100) else Color.White

    data object GoldToGrey : PodRatingPalette("Gold to grey", listOf(0xFFE2B53E, 0xFFEBDCA6, 0xFFD0D3D8, 0xFF8E9297, 0xFF55595E), 3)
    data object Medals : PodRatingPalette("Medals", listOf(0xFFE2B53E, 0xFFC5CCD6, 0xFFC4996B, 0xFF8E9297, 0xFF55595E), 3)
    data object GoldVioletGrey : PodRatingPalette("Gold, violet, grey",
        listOf(0xFFE2B53E, 0xFFB79AE0, 0xFFD0D3D8, 0xFF8E9297, 0xFF55595E), 3)
}

/** DRF-315: the palette the rating chips use; the gallery's colour options provide another. */
val LocalPodRatingPalette = staticCompositionLocalOf<PodRatingPalette> { PodRatingPalette.GoldToGrey }

/**
 * DRF-315: pod [detail]'s card ratings for its draft history dialogs, read from card records through [filters] once they may
 * show ([podHistoryRatingsSet]); null while they may not or aren't read yet.
 */
@Composable
fun rememberPodHistoryRatings(detail: PodDetail, filters: PodCardFilters): PodHistoryRatings? {
    val set = podHistoryRatingsSet(detail)
    LaunchedEffect(set) { if (set != null) filters.loadRatings(set) }
    val ratings = set?.let { filters.ratings[it] }
    return remember(ratings) { ratings?.let(::PodHistoryRatings) }
}

/** DRF-316: the "By rating" sort of the draft history dialogs, one choice for every pod, kept on the device. */
@Composable
fun rememberPodByRating(): MutableState<Boolean> {
    val platform = AppPlatform.current
    val preferences = remember(platform) { platform.preferences("pod-pick") }
    return remember(preferences) {
        object : MutableState<Boolean> {
            private val state = mutableStateOf(preferences.getBoolean(POD_BY_RATING_KEY, false))
            override var value: Boolean
                get() = state.value
                set(on) {
                    state.value = on
                    preferences.edit().apply { if (on) putBoolean(POD_BY_RATING_KEY, true) else remove(POD_BY_RATING_KEY) }.apply()
                }
            override fun component1() = value
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}

private const val POD_BY_RATING_KEY = "history-by-rating"

/** DRF-316: the "By rating" chip of a draft history dialog's header; [where] names the dialog in the app log. */
@Composable
fun PodByRatingChip(byRating: MutableState<Boolean>, where: String) {
    FilterChip(byRating.value, {
        byRating.value = !byRating.value
        AppLog.info("pods", "$where: by rating ${if (byRating.value) "on" else "off"}")
    }, { Text("By rating") }, Modifier.testTag("pod-history-by-rating"),
        leadingIcon = if (byRating.value) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null)
}

/** DRF-317: the small dimmed citation line at the top of a dialog's list while card ratings show. */
@Composable
fun PodRatingsCitation(modifier: Modifier = Modifier) {
    Text(POD_RATINGS_CITATION, modifier.testTag("pod-ratings-citation"), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f))
}

/** DRF-315: a card's rating chip, centred on the bottom edge of the card it is drawn over ([BoxScope] of the card). */
@Composable
fun PodRatingChip(rating: Int, step: PodRatingStep, tag: String, modifier: Modifier = Modifier) {
    val palette = LocalPodRatingPalette.current
    Box(modifier.testTag(tag).padding(bottom = 2.dp).background(palette.fill(step), RoundedCornerShape(50))
        .padding(horizontal = 4.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
        Text(podRatingText(rating), color = palette.text(step), fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false)
    }
}
