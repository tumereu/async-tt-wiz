package org.asyncttwiz.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

private fun count(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"

/** DRF-287: an Arena pod's format in brief, "Arena: 45 picks, 5 cards, take 1". */
fun podArenaFormat(format: ArenaFormat): String =
    "Arena: ${count(format.picks, "pick")}, ${count(format.cards, "card")}, take ${format.take}"

/** An Arena rarity's name: "Common", "Uncommon", "Rare", "Mythic rare". */
fun podRarityName(rarity: SetCardRarity): String = when (ArenaEngine.rarity(rarity)) {
    SetCardRarity.UNCOMMON -> "Uncommon"; SetCardRarity.RARE -> "Rare"; SetCardRarity.MYTHIC -> "Mythic rare"; else -> "Common"
}
private fun plural(rarity: SetCardRarity) = podRarityName(rarity).lowercase() + "s"

/** Whether an Arena pod's offers go by rarity: not a cube with Ignore rarity (DRF-283). */
fun podArenaByRarity(settings: DraftSettings, kind: PodSourceKind) = !(kind == PodSourceKind.CUBE && settings.ignoreRarity)

/** DRF-288: the Arena pick screen's first line, "Pick 12 of 45 · Uncommon · take 1" (no rarity with Ignore rarity). */
fun podArenaPickLine(settings: DraftSettings, pick: Int, kind: PodSourceKind, offer: Int): String =
    listOfNotNull("Pick $pick of ${settings.packsPerPlayer}",
        podRarityName(ArenaEngine.pickRarity(settings, pick)).takeIf { podArenaByRarity(settings, kind) },
        "take ${minOf(settings.keep, offer)}").joinToString(" · ")

/** DRF-289: an Arena pick's header in the draft history, "Pick 12 · Uncommon". */
fun podArenaHistoryHeader(settings: DraftSettings, pick: Int, kind: PodSourceKind): String =
    "Pick $pick" + if (podArenaByRarity(settings, kind)) " · ${podRarityName(ArenaEngine.pickRarity(settings, pick))}" else ""

/**
 * DRF-287, DRF-285: the setup summary under Arena's tiles: its format, how many picks are of each rarity and the cards
 * each player ends with, a cube's Reuse cards in words (DRF-284), then each rarity the source has too few cards of for an offer and where the offer is filled up
 * from (nearest below, then above), when the server has told the source's [ArenaCounts].
 */
fun podArenaSummary(settings: DraftSettings, source: PodSource): List<String> = buildList {
    add(podArenaFormat(ArenaFormat.of(settings)))
    val byRarity = podArenaByRarity(settings, source.kind)
    val schedule = ArenaEngine.schedule(settings)
    val order = listOf(SetCardRarity.RARE, SetCardRarity.UNCOMMON, SetCardRarity.COMMON)
    val picks = if (!byRarity) "Rarity ignored"
        else order.filter { (schedule[it] ?: 0) > 0 }.joinToString(", ") { "${schedule[it]} ${podRarityName(it).lowercase()}" } + " picks"
    add("$picks · each player ends with ${count(settings.packsPerPlayer * settings.keep, "card")}")
    // DRF-284 (owner, 2026-11-20): Reuse cards is each player's own
    if (source.kind == PodSourceKind.CUBE) add(if (settings.reuseCards) "Reuse cards on: a card you pick may be offered to you again"
        else "Reuse cards off: a card you pick is never offered to you again; other players' picks never change your offers")
    val counts = source.rarities ?: return@buildList
    if (!byRarity) return@buildList
    val ladder = listOf(SetCardRarity.COMMON, SetCardRarity.UNCOMMON, SetCardRarity.RARE, SetCardRarity.MYTHIC)
    val used = ladder.filter { (schedule[it] ?: 0) > 0 } +
        listOfNotNull(SetCardRarity.MYTHIC.takeIf { (schedule[SetCardRarity.RARE] ?: 0) > 0 && settings.mythicOneIn != null })
    for (rarity in used.reversed()) {
        val have = counts.of(rarity)
        if (have >= settings.cardsPerPack) continue
        val at = ladder.indexOf(rarity)
        val from = (ladder.subList(0, at).reversed() + ladder.subList(at + 1, ladder.size)).firstOrNull { counts.of(it) > 0 }
        val short = if (have == 0) "No ${plural(rarity)}" else "Only ${count(have, podRarityName(rarity).lowercase())} for offers of ${settings.cardsPerPack}"
        add(short + (from?.let { ": filled up from ${plural(it)}" } ?: ""))
    }
}

/** DRF-287: the summary's lines under Arena's tiles, read-only for members too. */
@Composable
fun PodArenaSummary(settings: DraftSettings, source: PodSource) {
    Column(Modifier.fillMaxWidth().testTag("pod-arena-summary"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        podArenaSummary(settings, source).forEachIndexed { index, line ->
            Text(line, style = if (index == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                color = if (index == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
