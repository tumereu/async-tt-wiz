package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference

/** The two series of a record over time (the dataviz palette's first two categorical slots): you, and the opponent (or
 * your losses, with All). */
internal val SeriesYou = Color(0xff2a78d6)
internal val SeriesThem = Color(0xffeb6834)

/**
 * The Stats screen (game history spec §3.3): opponent chips (All, then each opponent by number of matches) filtering
 * everything below, and two tabs: History (every record, newest first; a tap opens its details) and Graphs (summary tiles
 * and graphs drawn in the app).
 */
@Composable
internal fun StatsContent(history: HistoryController, me: String, openDeck: ((String) -> Unit)?) {
    LaunchedEffect(Unit) { history.loadStats() }
    val list = history.stats
    val choice = history.choice
    val all = list.value
    var details by remember { mutableStateOf<MatchRecord?>(null) }
    Column(Modifier.fillMaxSize()) {
        if (all == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (list.failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Stats couldn't be loaded.")
                    TextButton(history::loadStats) { Text("Retry") }
                } else CircularProgressIndicator()
            }
            return@Column
        }
        val opponents = remember(all, me) { MatchStats.opponents(all, me) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(choice.opponent == null, { history.opponent(null) }, label = { Text("All") }, modifier = Modifier.testTag("stats-chip-all"))
            for (opponent in opponents) FilterChip(choice.opponent == opponent.account.accountId, { history.opponent(opponent.account.accountId) },
                label = { Text(opponent.account.username) }, leadingIcon = { Avatar(opponent.account, size = 20.dp) },
                modifier = Modifier.testTag("stats-chip-${opponent.account.accountId}"))
        }
        TabRow(if (choice.graphs) 1 else 0) {
            Tab(!choice.graphs, { history.graphs(false) }, text = { Text("History") })
            Tab(choice.graphs, { history.graphs(true) }, text = { Text("Graphs") }, modifier = Modifier.testTag("stats-graphs-tab"))
        }
        val records = remember(all, choice.opponent) { MatchStats.with(all, me, choice.opponent) }
        val opponent = opponents.firstOrNull { it.account.accountId == choice.opponent }?.account
        if (!choice.graphs) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (records.isEmpty()) Text("No matches yet. Finished matches show here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            records.forEach { record -> MatchRow(record, me) { details = record } }
        } else StatsGraphs(records, me, opponent, Modifier.weight(1f))
    }
    details?.let { MatchDetailsDialog(it, me, openDeck) { details = null } }
}

/** The Graphs tab: summary tiles, then each graph; an empty selection says so instead of drawing empty graphs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsGraphs(records: List<MatchRecord>, me: String, opponent: AccountIdentity?, modifier: Modifier) {
    val summary = remember(records) { MatchStats.summary(records, me) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (summary.record.matches == 0) {
            Text("No finished matches yet. Graphs appear once a match is finished.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Matches", summary.record.matches.toString())
            StatTile("W–L–D", summary.record.text)
            StatTile("Win %", summary.record.rate.text)
            StatTile("Current streak", summary.current?.let { (result, n) -> "$n ${resultLetter(result)}" } ?: "—")
            StatTile("Longest streak", if (summary.longestWins == 0) "—" else "${summary.longestWins} W")
            StatTile("Average turns", summary.averageTurns?.let { String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "—")
        }
        val running = remember(records) { MatchStats.running(records, me) }
        GraphCard("Record over time") {
            RunningChart(running, if (opponent == null) "Your wins" to "Your losses" else "You" to opponent.username)
        }
        val (play, draw) = remember(records) { MatchStats.playDraw(records, me) }
        GraphCard("Win % on the play vs on the draw") {
            PercentBars(listOf("On the play" to play, "On the draw" to draw))
        }
        GraphCard("How matches end") { CountBars(MatchStats.endings(records, me)) }
        GraphCard("Match length in turns") { CountBars(MatchStats.lengths(records, me), vertical = true) }
        GraphCard("Your decks") {
            val decks = remember(records) { MatchStats.decks(records, me) }
            decks.forEach { deck ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeckThumbnail(deck.cover?.let { ArtworkReference(it.scryfallId, 0, it.definition, "small", it.imageUri) }, deck.name, 24.dp, 34.dp)
                    Text(deck.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${deck.record.wins}–${deck.record.losses}", fontWeight = FontWeight.SemiBold)
                    Text(deck.record.rate.text, Modifier.width(48.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (opponent != null) GraphCard("Your decks vs theirs") { DeckGrid(remember(records) { MatchStats.grid(records, me) }) }
        GraphCard("Win % by your deck's colours") { PercentBars(MatchStats.colors(records, me)) }
        GraphCard("Extras") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Win % after a mulligan", MatchStats.afterMulligan(records, me).text)
                StatTile("Fastest win", MatchStats.fastestWin(records, me)?.let { "T$it" } ?: "—")
                StatTile("Longest match", MatchStats.longestMatch(records, me)?.let { "T$it" } ?: "—")
            }
            Text("Matches per week", style = MaterialTheme.typography.labelLarge)
            val clock = AppClock.clock()
            val weeks = remember(records) { MatchStats.perWeek(records, me, clock.instant(), clock.zone) }
            CountBars(weeks.map { (week, n) -> DateTimeFormatter.ofPattern("d MMM").format(week) to n }, vertical = true)
        }
    }
}

private fun resultLetter(result: SuggestedResult) = when (result) {
    SuggestedResult.WIN -> "W"; SuggestedResult.LOSS -> "L"; SuggestedResult.DRAW -> "D"
}

@Composable
private fun StatTile(label: String, value: String) {
    OutlinedCard(Modifier.widthIn(min = 96.dp).semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GraphCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("graph-$title")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

/** Horizontal bars of percentages with their "n of m" (no bar when there was no match). */
@Composable
private fun PercentBars(items: List<Pair<String, MatchStats.Rate>>) {
    for ((label, rate) in items) BarRow(label, (rate.percent ?: 0) / 100f, "${rate.text} (${rate.wins} of ${rate.matches})")
}

/** Bars of counts: horizontal rows, or a vertical histogram with its labels under the bars. */
@Composable
private fun CountBars(items: List<Pair<String, Int>>, vertical: Boolean = false) {
    val max = items.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    if (!vertical) { for ((label, n) in items) BarRow(label, n.toFloat() / max, n.toString()); return }
    Row(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = items.joinToString { "${it.first}: ${it.second}" } },
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        for ((label, n) in items) Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom) {
            Text(if (n > 0) n.toString() else "", style = MaterialTheme.typography.labelSmall)
            Box(Modifier.fillMaxWidth(.7f).fillMaxHeight((n.toFloat() / max * .7f).coerceAtLeast(if (n > 0) .02f else 0f))
                .background(SeriesYou, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun BarRow(label: String, fraction: Float, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(104.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f).height(14.dp)) {
            if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight()
                .background(SeriesYou, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
        }
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/** Record over time: two running lines over the matches, oldest to newest, with a legend. */
@Composable
private fun RunningChart(points: List<Pair<Int, Int>>, names: Pair<String, String>) {
    val max = (points.maxOfOrNull { maxOf(it.first, it.second) } ?: 0).coerceAtLeast(1)
    val grid = MaterialTheme.colorScheme.outlineVariant
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        for ((name, color) in listOf(names.first to SeriesYou, names.second to SeriesThem))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(12.dp, 3.dp).background(color))
                Text("$name ${if (color == SeriesYou) points.lastOrNull()?.first ?: 0 else points.lastOrNull()?.second ?: 0}",
                    style = MaterialTheme.typography.bodySmall)
            }
    }
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics {
        contentDescription = "${names.first} ${points.lastOrNull()?.first ?: 0}, ${names.second} ${points.lastOrNull()?.second ?: 0} after ${points.size} matches"
    }) {
        val step = if (points.size > 1) size.width / (points.size - 1) else 0f
        fun y(v: Int) = size.height - v.toFloat() / max * (size.height - 8.dp.toPx()) - 2.dp.toPx()
        drawLine(grid, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
        for ((index, color) in listOf(0 to SeriesYou, 1 to SeriesThem)) {
            val values = listOf(0) + points.map { if (index == 0) it.first else it.second }
            val dx = if (values.size > 1) size.width / (values.size - 1) else step
            for (i in 1 until values.size) drawLine(color, Offset((i - 1) * dx, y(values[i - 1])), Offset(i * dx, y(values[i])),
                2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** Your decks against theirs (one opponent): a W–L grid. */
@Composable
private fun DeckGrid(grid: MatchStats.Grid) {
    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Spacer(Modifier.width(112.dp))
            for (column in grid.columns) Text(column, Modifier.width(80.dp), style = MaterialTheme.typography.labelSmall, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
        for (row in grid.rows) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row, Modifier.width(112.dp), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            for (column in grid.columns) Text(grid.cells[row to column]?.let { "${it.wins}–${it.losses}" } ?: "·",
                Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}
