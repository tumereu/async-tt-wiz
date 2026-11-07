package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.concurrent.ExecutorService

/** One list of records as read from the server: [records] (null until first read), [loading] while a read runs, [failed]
 * when the last read failed (the earlier list stays shown). */
data class HistoryList<T>(val value: T? = null, val loading: Boolean = false, val failed: Boolean = false)

/** The Stats screen's personal choices: the opponent chip (null: All) and the tab. */
data class StatsChoice(val opponent: String? = null, val graphs: Boolean = false)

/**
 * Game history (spec game-history §3): the server's records as the app reads them: the account's (the Stats screen), a
 * game's (its lobby's History tab) and a pod's (its Games tab). Read on demand, kept until the next read; never written
 * here (the server writes records when matches end).
 */
class HistoryController(private val api: Lobbies, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit) {
    var stats by mutableStateOf(HistoryList<List<MatchRecord>>())
        internal set // tests seed it
    var choice by mutableStateOf(StatsChoice())
        internal set // tests seed it
    var games by mutableStateOf(mapOf<String, HistoryList<List<MatchRecord>>>())
        internal set // tests seed it
    var pods by mutableStateOf(mapOf<String, HistoryList<PodMatches>>())
        internal set // tests seed it
    @Volatile private var epoch = 0L

    fun clear() { epoch++; stats = HistoryList(); games = emptyMap(); pods = emptyMap(); choice = StatsChoice() }
    fun opponent(accountId: String?) { choice = choice.copy(opponent = accountId) }
    fun graphs(on: Boolean) { choice = choice.copy(graphs = on) }

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

    /** Reads [podId]'s matches (its Games tab). */
    fun loadPod(podId: String) {
        pods = pods + (podId to (pods[podId] ?: HistoryList()).copy(loading = true))
        read({ api.podMatches(it, podId) }) { result ->
            pods = pods + (podId to (result?.let { HistoryList(it) } ?: (pods[podId] ?: HistoryList()).copy(loading = false, failed = true)))
        }
    }

    private fun <T> read(call: suspend (String) -> T, done: (T?) -> Unit) {
        val token = credential() ?: return post { done(null) }
        val generation = epoch
        worker.execute {
            val result = try { runBlocking { call(token) } } catch (failure: Exception) {
                AppLog.warn("history", "read failed", failure); null
            }
            post { if (generation == epoch) done(result) }
        }
    }
}
