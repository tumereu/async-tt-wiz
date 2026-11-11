package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference

/** Game history's result colours (spec §3.3): Win green, Loss red, Draw grey; Unfinished is outlined. */
val WinGreen = Color(0xff2e7d32)
val LossRed = Color(0xffc62828)
val DrawGrey = Color(0xff6b7280)

/** A record's date, "7 Nov 2026" (the device's zone). */
fun matchDate(record: MatchRecord): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM uuuu").withZone(AppClock.clock().zone).format(Instant.parse(record.endedAt))
}.getOrDefault("")

/** How a match ended, short (a row): the loser's reason by the rules, Conceded, Agreed, the draw's kind, or Unfinished. */
fun matchEndText(record: MatchRecord): String = when (val ending = record.ending) {
    MatchEnding.ByRules -> record.players.firstNotNullOfOrNull { it.lossCause }?.let(::lossCauseText) ?: "By the rules"
    is MatchEnding.Conceded -> "Conceded"
    is MatchEnding.Agreed -> "Agreed"
    is MatchEnding.Draw -> if (ending.loop) "Loop" else "Both lost"
    MatchEnding.Unfinished -> "Unfinished"
}

/** Why a player lost by the rules. */
fun lossCauseText(cause: LossCause): String = when (cause) {
    LossCause.ZeroLife -> "Life 0"
    LossCause.EmptyLibrary -> "Empty library"
    LossCause.Poison -> "10 poison"
    is LossCause.CardEffect -> cause.card ?: "A card's effect"
}

/** How a match ended, in full (its details): who, why, and an agreement's reason. */
fun matchEndDetail(record: MatchRecord, me: String): String {
    fun name(accountId: String) = if (accountId == me) "You" else record.player(accountId)?.account?.username ?: "Opponent"
    return when (val ending = record.ending) {
        MatchEnding.ByRules -> record.players.filter { it.lossCause != null }.joinToString("; ") { loser ->
            val who = name(loser.account.accountId)
            when (val cause = loser.lossCause!!) {
                LossCause.ZeroLife -> "$who reached 0 life"
                LossCause.EmptyLibrary -> "$who drew from an empty library"
                LossCause.Poison -> "$who got 10 poison counters"
                is LossCause.CardEffect -> "$who lost to ${cause.card ?: "a card's effect"}"
            }
        }.ifEmpty { "By the rules" }
        is MatchEnding.Conceded -> "${name(ending.by)} conceded"
        is MatchEnding.Agreed -> "Agreed" + (ending.reason.takeIf { it.isNotBlank() }?.let { " · “$it”" } ?: "")
        is MatchEnding.Draw -> if (ending.loop) "Draw · a loop nobody could stop" else "Draw · everyone lost at once"
        MatchEnding.Unfinished -> "Unfinished · the game was deleted while in play"
    }
}

/** A result chip: Win green, Loss red, Draw grey, Unfinished outlined (§3.3). */
@Composable
fun ResultChip(result: SuggestedResult?, finished: Boolean, modifier: Modifier = Modifier) {
    val (text, color) = when {
        !finished -> "Unfinished" to null
        result == SuggestedResult.WIN -> "Win" to WinGreen
        result == SuggestedResult.LOSS -> "Loss" to LossRed
        else -> "Draw" to DrawGrey
    }
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = color ?: Color.Transparent,
        border = if (color == null) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium,
            color = if (color == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.White, fontWeight = FontWeight.SemiBold)
    }
}

/** A deck in a record: its cover and name, or "Deck" with the placeholder picture while hidden (LOB-042). */
@Composable
fun MatchDeckLine(deck: MatchDeck?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DeckThumbnail(deck?.cover?.let { ArtworkReference(it.scryfallId, 0, "small", it.imageUri) }, deck?.name ?: "Deck", 20.dp, 28.dp)
        Text(deck?.name ?: "Deck", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One record as a row (§3.3): date, the opponent's avatar and name, the result chip, turns, how it ended, both decks,
 * and the pod's name for pod matches. */
@Composable
fun MatchRow(record: MatchRecord, me: String, open: () -> Unit) {
    val opponent = record.opponent(me)
    OutlinedCard(onClick = open, modifier = Modifier.fillMaxWidth().testTag("match-${record.id}")) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                opponent?.let { Avatar(it.account, size = 28.dp) }
                Column(Modifier.weight(1f)) {
                    Text(opponent?.account?.username ?: "", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${matchDate(record)} · T${record.turns} · ${matchEndText(record)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ResultChip(record.player(me)?.result, record.finished)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MatchDeckLine(record.player(me)?.deck, Modifier.weight(1f))
                Text("vs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MatchDeckLine(opponent?.deck, Modifier.weight(1f))
            }
            record.podName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

/** How long a match took, "2 d 3 h", "3 h 5 min" or "12 min". */
fun matchDuration(record: MatchRecord): String = runCatching {
    val minutes = Duration.between(Instant.parse(record.startedAt), Instant.parse(record.endedAt)).toMinutes().coerceAtLeast(0)
    when {
        minutes >= 24 * 60 -> "${minutes / (24 * 60)} d ${minutes % (24 * 60) / 60} h"
        minutes >= 60 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "$minutes min"
    }
}.getOrDefault("")

/** A record's details (§3.3): everything it holds, including mulligans, final life, duration, play/draw, the reason, and
 * Open deck for the viewer's own deck while it is saved ([openDeck]). */
@Composable
fun MatchDetailsDialog(record: MatchRecord, me: String, openDeck: ((String) -> Unit)?, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, modifier = Modifier.testTag("match-details"),
        confirmButton = { TextButton(dismiss) { Text("Close") } },
        title = { Text(record.gameName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultChip(record.player(me)?.result, record.finished)
                    Text("${matchDate(record)} · ${record.turns} turns · ${matchDuration(record)}", style = MaterialTheme.typography.bodySmall)
                }
                Text(matchEndDetail(record, me), style = MaterialTheme.typography.bodyMedium)
                record.podName?.let { Text("Pod: $it", style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider()
                for (player in listOf(record.player(me), record.opponent(me)).filterNotNull()) {
                    val own = player.account.accountId == me
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Avatar(player.account, size = 24.dp)
                            Text(if (own) "You" else player.account.username, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(if (player.onThePlay) "On the play" else "On the draw", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(listOfNotNull(player.mulligans?.let { "Mulligans: $it" }, player.finalLife?.let { "Final life: $it" })
                            .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MatchDeckLine(player.deck, Modifier.weight(1f))
                            val deck = player.deck
                            if (own && deck != null && deck.openable && openDeck != null)
                                TextButton({ dismiss(); openDeck(deck.sourceId) }) { Text("Open deck") }
                        }
                    }
                }
            }
        })
}

/** A game lobby's History tab (§3.1): this game's matches newest first, or "No finished matches yet." */
@Composable
fun GameHistoryList(list: HistoryList<List<MatchRecord>>?, me: String, open: (MatchRecord) -> Unit, modifier: Modifier = Modifier) {
    val records = list?.value
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            records == null && list?.failed == true -> Text("History couldn't be loaded.", style = MaterialTheme.typography.bodyMedium)
            records == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            records.isEmpty() -> Text("No finished matches yet.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> records.forEach { record -> MatchRow(record, me) { open(record) } }
        }
    }
}

/** A pod's standings (§3.2): each member's W–L–D in this pod's matches, best record first. */
@Composable
fun PodStandings(standings: List<MatchStats.Standing>) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("pod-standings")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row { Text("Player", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("W–L–D", style = MaterialTheme.typography.labelMedium) }
            standings.forEachIndexed { index, standing ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}.", Modifier.width(24.dp), style = MaterialTheme.typography.bodySmall)
                    Avatar(standing.account, size = 24.dp)
                    Text(standing.account.username, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(standing.record.text, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** A pod's match (§3.2): "<name> vs <name>", the result or its status, turns and how it ended; no deck information. */
@Composable
fun PodMatchRow(players: List<AccountIdentity>, status: String, detail: String, tag: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(players.joinToString(" vs ") { it.username }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(status, style = MaterialTheme.typography.labelLarge)
    }
}

/** A finished pod match's status: "<name> won", "Draw" or "Unfinished". */
fun podMatchStatus(record: MatchRecord): String = when {
    !record.finished -> "Unfinished"
    else -> record.players.firstOrNull { it.result == SuggestedResult.WIN }?.let { "${it.account.username} won" } ?: "Draw"
}

/** The pod's standings and matches under its members (DRF-121; spec §3.2). */
@Composable
fun PodMatchesSection(detail: PodDetail, matches: HistoryList<PodMatches>?) {
    val value = matches?.value ?: return
    Text("Standings", style = MaterialTheme.typography.titleMedium)
    PodStandings(MatchStats.standings(value.records, detail.members.map { it.account }))
    Text("Matches", style = MaterialTheme.typography.titleMedium)
    if (value.records.isEmpty() && value.playing.isEmpty())
        Text("No matches yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    value.playing.forEach { game ->
        PodMatchRow(game.players, "In play", "Turn ${game.turn}", "pod-match-playing-${game.gameId}")
    }
    value.records.forEach { record ->
        PodMatchRow(record.players.map { it.account }, podMatchStatus(record), "${matchDate(record)} · T${record.turns} · ${matchEndText(record)}",
            "pod-match-${record.id}")
    }
}
