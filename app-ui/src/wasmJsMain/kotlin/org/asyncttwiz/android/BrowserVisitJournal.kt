@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.asyncttwiz.appui.LogPriority
import org.asyncttwiz.appui.pageScope
import org.asyncttwiz.appui.randomUuid

/**
 * WEB-056: the page's [VisitJournal] in the browser's localStorage, started first thing (web shell): every app log line
 * goes into it, saved within a quarter second (at once for warnings and errors, and as the page closes or the browser
 * freezes it); this tab's id for it is kept in sessionStorage, which a reload keeps. Starting, it names in the app log what
 * earlier visits left behind: a start that failed before the app ran (index.html keeps it) and visits the browser ended.
 */
internal object BrowserVisitJournal {
    private const val TAB_KEY = "atw.visit"
    private var journal: VisitJournal? = null
    private var saving = false

    fun start(build: String) {
        if (journal != null) return
        val previous = jsSessionGet(TAB_KEY)
        val journal = VisitJournal(LocalStore, randomUuid(), build) { jsNow().toLong() }
        this.journal = journal
        jsSessionSet(TAB_KEY, journal.id)
        AppLog.observer = { priority, line ->
            journal.line(line)
            if (priority >= LogPriority.WARN) journal.save() else saveSoon()
        }
        jsTakeStartFailure()?.let { AppLog.warn("app", "The last visit couldn't start: $it") }
        for (report in journal.earlier(previous)) AppLog.warn("app", report)
        AppLog.info("app", "Browser: ${jsBrowserFacts()}")
        journal.save()
        jsOnPageLifecycle(closed = { journal.closed(true) }, shown = { journal.closed(false) },
            frozen = { AppLog.info("web-page", "Page frozen by the browser (in the background)"); journal.save() },
            resumed = { AppLog.info("web-page", "Page resumed by the browser") })
        pageScope.launch { while (true) { delay(VisitJournal.HEARTBEAT); journal.save() } }
    }

    private fun saveSoon() {
        if (saving) return
        saving = true
        pageScope.launch { delay(250); saving = false; journal?.save() }
    }

    private object LocalStore : VisitJournal.Store {
        override fun keys(): List<String> = jsLocalKeys().split('\n').filter { it.isNotEmpty() }
        override fun get(key: String): String? = jsLocalGet(key)
        override fun set(key: String, value: String) = jsLocalSet(key, value)
        override fun remove(key: String) = jsLocalRemove(key)
    }
}

/** The JavaScript heap this page uses and its limit (Chrome only), as ", JS heap 214 MB of 4096 MB"; "" elsewhere. */
internal fun jsHeapText(): String = js("""(() => {
    const memory = (typeof performance !== 'undefined') ? performance.memory : undefined;
    if (!memory) return '';
    const mb = (bytes) => Math.round(bytes / 1048576);
    return ', JS heap ' + mb(memory.usedJSHeapSize) + ' MB of ' + mb(memory.jsHeapSizeLimit) + ' MB';
})()""")

private fun jsNow(): Double = js("Date.now()")
private fun jsLocalKeys(): String = js("(() => { try { return Object.keys(localStorage).join('\\n'); } catch (e) { return ''; } })()")
private fun jsLocalRemove(key: String): Unit = js("{ try { localStorage.removeItem(key); } catch (e) {} }")

/** What index.html kept of a failure before the app ran, removed as it is read; null when there was none. */
private fun jsTakeStartFailure(): String? = js("""(() => {
    globalThis.atwStarted = true;
    try {
        const text = localStorage.getItem('atw.start-failure');
        localStorage.removeItem('atw.start-failure');
        return text;
    } catch (e) { return null; }
})()""")

/** The window, screen and machine as the browser tells them. */
private fun jsBrowserFacts(): String = js("""(() => {
    const parts = [innerWidth + 'x' + innerHeight + ' at ' + devicePixelRatio + 'x', 'screen ' + screen.width + 'x' + screen.height];
    if (navigator.hardwareConcurrency) parts.push(navigator.hardwareConcurrency + ' cores');
    if (navigator.deviceMemory) parts.push('device memory ' + navigator.deviceMemory + ' GB');
    const memory = (typeof performance !== 'undefined') ? performance.memory : undefined;
    if (memory) parts.push('JS heap limit ' + Math.round(memory.jsHeapSizeLimit / 1048576) + ' MB');
    return parts.join(', ');
})()""")

/** The page closes ([closed]: pagehide), comes back from the browser's page cache ([shown]), or is frozen or resumed. */
private fun jsOnPageLifecycle(closed: () -> Unit, shown: () -> Unit, frozen: () -> Unit, resumed: () -> Unit): Unit = js("""{
    window.addEventListener('pagehide', () => closed());
    window.addEventListener('pageshow', (event) => { if (event.persisted) shown(); });
    document.addEventListener('freeze', () => frozen());
    document.addEventListener('resume', () => resumed());
}""")
