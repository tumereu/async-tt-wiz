package org.asyncttwiz.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * WEB-056 (owner report 2026-11-14: desktop Chrome ended the web page "out of memory" as a table opened, and nothing was left
 * to say so): what a web page is doing, kept in the browser's own storage ([store]) as it goes: its build, when it started
 * and was last heard of, whether it closed, and its last app log lines. A visit the browser ended without the page closing
 * (Chrome's "Aw, Snap!", a crash, out of memory, a discarded background tab) leaves its journal open; a later visit names
 * it, with those lines, in its own app log ([earlier]), and so in its problem reports.
 *
 * Each page keeps its own journal under [KEY] + [id]. The browser's tab remembers the id of its last page across a reload,
 * so a page reloaded after the browser ended it finds that journal at once; another tab's open journal counts as ended
 * only once it hasn't been heard of for [STALE] ms (an open page saves at least every [HEARTBEAT] ms).
 */
class VisitJournal(private val store: Store, val id: String, private val build: String, private val now: () -> Long) {
    /** The browser's storage for journals (localStorage). */
    interface Store {
        fun keys(): List<String>
        fun get(key: String): String?
        fun set(key: String, value: String)
        fun remove(key: String)
    }

    private val started = now()
    private val lines = ArrayDeque<String>()
    private var closed = false

    /**
     * The journals of earlier visits that ended without closing their page, as texts for the app log: this tab's last
     * page's ([previous], its [id]) at once, another's once it is [STALE]. Those and every closed one are removed; the
     * journals of pages still open elsewhere are left.
     */
    fun earlier(previous: String?): List<String> {
        val reports = mutableListOf<String>()
        for (key in store.keys().filter { it.startsWith(KEY) && it != KEY + id }.sorted()) {
            val text = store.get(key) ?: continue
            val visit = try { Json.parseToJsonElement(text).jsonObject } catch (_: Exception) { store.remove(key); continue }
            val closedThen = visit["closed"]?.jsonPrimitive?.booleanOrNull ?: false
            val startedThen = visit["started"]?.jsonPrimitive?.longOrNull ?: 0
            val updated = visit["updated"]?.jsonPrimitive?.longOrNull ?: 0
            val thisTab = key == KEY + previous
            if (!closedThen && !thisTab && now() - updated < STALE) continue
            store.remove(key)
            if (closedThen) continue
            val lines = visit["lines"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            reports += "The last visit ${if (thisTab) "in this tab" else "in another tab"} ended without closing its page: the " +
                "browser ended it (a crash, out of memory, or a background tab it discarded) ${(updated - startedThen) / 1000} s " +
                "after it started and ${(now() - updated) / 1000} s ago (build ${visit["build"]?.jsonPrimitive?.content.orEmpty()
                    .ifEmpty { "unknown" }}). Its last lines:\n" + lines.joinToString("\n")
        }
        return reports
    }

    /** One app log line of this page, kept among its last [LINES]. */
    fun line(text: String) {
        lines.addLast(text.take(LINE))
        while (lines.size > LINES) lines.removeFirst()
    }

    /** The page closed ([value]), or came back from the browser's page cache (false). */
    fun closed(value: Boolean) { closed = value; save() }

    /** Writes this page's journal. */
    fun save() {
        store.set(KEY + id, buildJsonObject {
            put("build", build); put("started", started); put("updated", now()); put("closed", closed)
            put("lines", JsonArray(lines.map(::JsonPrimitive)))
        }.toString())
    }

    companion object {
        const val KEY = "atw.visit."
        const val LINES = 40
        const val LINE = 300
        const val HEARTBEAT = 30_000L
        /** A hidden tab's timers may run only once a minute: another tab's journal ends after more than two of them. */
        const val STALE = 150_000L
    }
}
