package org.asyncttwiz.android

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** WEB-056, WEB-066: the web page's visit journal names the visits the browser ended (Chrome's "Aw, Snap! Out of memory"). */
class VisitJournalTest {
    private class Store : VisitJournal.Store {
        val values = LinkedHashMap<String, String>()
        override fun keys() = values.keys.toList()
        override fun get(key: String) = values[key]
        override fun set(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private var now = 1_000_000L
    private val store = Store()
    private fun journal(id: String) = VisitJournal(store, id, "build1") { now }

    // WEB-056, WEB-066: a page reloaded after the browser ended it names that visit at once, with its last lines and when it ended
    @Test fun aReloadedTabNamesItsEndedVisit() {
        val ended = journal("a")
        ended.line("I/app: Started")
        ended.line("I/automatum: Card definitions: parsing 36 new")
        now += 5_000
        ended.save()
        now += 10_000
        val reports = journal("b").earlier(previous = "a")
        assertEquals(1, reports.size)
        assertTrue(reports[0].startsWith("The last visit in this tab ended without closing its page"), reports[0])
        assertTrue("5 s after it started and 10 s ago (build build1)" in reports[0], reports[0])
        assertTrue(reports[0].endsWith("I/app: Started\nI/automatum: Card definitions: parsing 36 new"), reports[0])
        assertTrue(store.values.isEmpty(), "the ended journal is removed")
    }

    // WEB-056, WEB-066: a closed page leaves nothing to report; another tab's open journal waits until it hasn't been heard of for a while
    @Test fun closedAndLiveVisitsAreNotReported() {
        journal("closed").apply { line("I/app: Started"); closed(true) }
        journal("live").apply { line("I/app: Started"); save() }
        now += VisitJournal.STALE - 1
        assertEquals(emptyList(), journal("new").earlier(previous = null))
        assertEquals(setOf(VisitJournal.KEY + "live"), store.values.keys)
        now += 2
        val reports = journal("newer").earlier(previous = null)
        assertEquals(1, reports.size)
        assertTrue(reports[0].startsWith("The last visit in another tab ended without closing its page"), reports[0])
        assertTrue(store.values.isEmpty())
    }

    // WEB-056, WEB-066: a journal keeps the page's last lines only, each cut short
    @Test fun keepsTheLastLines() {
        val page = journal("a")
        repeat(VisitJournal.LINES + 5) { page.line("line $it " + "x".repeat(VisitJournal.LINE)) }
        page.save()
        val report = journal("b").earlier(previous = "a").single()
        val lines = report.substringAfter("Its last lines:\n").lines()
        assertEquals(VisitJournal.LINES, lines.size)
        assertTrue(lines.first().startsWith("line 5 ") && lines.all { it.length == VisitJournal.LINE }, lines.first())
    }
}
