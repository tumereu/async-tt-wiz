package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.asyncttwiz.appui.executeSuspending
import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.ExecutorService
import kotlin.concurrent.Volatile

/** One list of records as read from the server: [records] (null until first read), [loading] while a read runs, [failed]
 * when the last read failed (the earlier list stays shown). */
data class HistoryList<T>(val value: T? = null, val loading: Boolean = false, val failed: Boolean = false)

/** The Stats screen's tabs (LOB-130). */
sealed class StatsTab(val title: String) {
    data object History : StatsTab("History")
    data object Graphs : StatsTab("Graphs")
    /** LOB-280: the kept drafts. */
    data object Drafts : StatsTab("Drafts")
    // A getter: the objects are made after their sealed class, so a stored list would hold nulls
    companion object { val all: List<StatsTab> get() = listOf(History, Graphs, Drafts) }
}

/** The Stats screen's personal choices: the opponent chip (null: All) and the tab. */
data class StatsChoice(val opponent: String? = null, val tab: StatsTab = StatsTab.History)

/**
 * Game history (spec game-history §3): the server's records as the app reads them: the account's (the Stats screen), a
 * game's (its lobby's History tab) and a pod's (its Games tab). Read on demand, kept until the next read; never written
 * here (the server writes records when matches end).
 */
class HistoryController(private val api: Lobbies, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit) {
    var stats by mutableStateOf(HistoryList<List<MatchRecord>>())
        public set // tests seed it
    var choice by mutableStateOf(StatsChoice())
        public set // tests seed it
    var games by mutableStateOf(mapOf<String, HistoryList<List<MatchRecord>>>())
        public set // tests seed it
    var pods by mutableStateOf(mapOf<String, HistoryList<PodMatches>>())
        public set // tests seed it
    /** LOB-280: the account's kept drafts (Stats' Drafts tab). */
    var drafts by mutableStateOf(HistoryList<List<DraftRecordSummary>>())
        public set // tests seed it
    /** LOB-281: the kept drafts opened, by id, each with its draft history. */
    var draftRecords by mutableStateOf(mapOf<String, HistoryList<DraftRecord>>())
        public set // tests seed it
    @Volatile private var epoch = 0L

    fun clear() {
        epoch++; stats = HistoryList(); games = emptyMap(); pods = emptyMap(); drafts = HistoryList(); draftRecords = emptyMap()
        choice = StatsChoice()
    }
    fun opponent(accountId: String?) { choice = choice.copy(opponent = accountId) }
    fun tab(tab: StatsTab) {
        if (tab != choice.tab) AppLog.info("history", "Stats: ${tab.title} tab")
        choice = choice.copy(tab = tab)
    }

    /** LOB-280: reads the account's kept drafts (on opening the Drafts tab). */
    fun loadDrafts() {
        AppLog.info("history", "Kept drafts read")
        drafts = drafts.copy(loading = true)
        read({ api.drafts(it).drafts }) { result ->
            if (result == null) AppLog.info("history", "Kept drafts read failed; the earlier list stays")
            else AppLog.info("history", "Kept drafts read: ${result.size} (${result.joinToString { "${it.id.take(8)} '${it.podName}' " +
                "${it.settings.mode.name.lowercase()} ${it.source.setCode} ${it.finishedAt}" }})")
            drafts = result?.let { HistoryList(it) } ?: drafts.copy(loading = false, failed = true)
        }
    }

    /** LOB-281: reads kept draft [id] with its draft history (on opening it from the Drafts tab, and on Retry). */
    fun loadDraft(id: String) {
        AppLog.info("history", "Kept draft ${id.take(8)} read")
        draftRecords = draftRecords + (id to (draftRecords[id] ?: HistoryList()).copy(loading = true, failed = false))
        read({ api.draft(it, id) }) { result ->
            if (result == null) AppLog.info("history", "Kept draft ${id.take(8)} read failed")
            else AppLog.info("history", "Kept draft ${id.take(8)} read: pod ${result.summary.podId.take(8)} '${result.summary.podName}', " +
                "${result.history.picks.size} picks, ${result.history.definitions.size} definitions, seating ${result.history.seating}")
            draftRecords = draftRecords + (id to (result?.let { HistoryList(it) }
                ?: (draftRecords[id] ?: HistoryList()).copy(loading = false, failed = true)))
        }
    }

    /** Reads the account's records (Stats). */
    fun loadStats() {
        stats = stats.copy(loading = true)
        read({ api.history(it).records }) { result -> stats = result?.let { HistoryList(it) } ?: stats.copy(loading = false, failed = true) }
    }

    /** Reads [gameId]'s records (its lobby's History tab). */
    fun loadGame(gameId: String) {
        games = games + (gameId to (games[gameId] ?: HistoryList()).copy(loading = true))
        read({ api.gameHistory(it, gameId).records }) { result ->
            games = games + (gameId to (result?.let { HistoryList(it) } ?: (games[gameId] ?: HistoryList()).copy(loading = false, failed = true)))
        }
    }

    /** Reads [podId]'s matches (its Games tab): on opening it, and again when one of its games starts or ends ([why]). */
    fun loadPod(podId: String, why: String = "opened") {
        AppLog.info("history", "Pod ${podId.take(8)} matches read ($why)")
        pods = pods + (podId to (pods[podId] ?: HistoryList()).copy(loading = true))
        read({ api.podMatches(it, podId) }) { result ->
            if (result == null) AppLog.info("history", "Pod ${podId.take(8)} matches read ($why) failed; the earlier list stays")
            else AppLog.info("history", "Pod ${podId.take(8)} matches read ($why): ${result.records.size} records " +
                "(${result.records.joinToString { "${it.gameId.take(8)} ${if (it.finished) "ended" else "unfinished"}" }}), ${result.playing.size} in play " +
                "(${result.playing.joinToString { "${it.gameId.take(8)} turn ${it.turn}" }})")
            pods = pods + (podId to (result?.let { HistoryList(it) } ?: (pods[podId] ?: HistoryList()).copy(loading = false, failed = true)))
        }
    }

    private fun <T> read(call: suspend (String) -> T, done: (T?) -> Unit) {
        val token = credential() ?: return post { done(null) }
        val generation = epoch
        worker.executeSuspending {
            val result = try { call(token) } catch (failure: Exception) {
                AppLog.warn("history", "read failed", failure); null
            }
            post { if (generation == epoch) done(result) }
        }
    }
}
