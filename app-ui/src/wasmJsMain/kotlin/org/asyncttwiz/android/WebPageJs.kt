@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

// The browser APIs the web shell uses (web client step 3), as small JavaScript functions: the address and history, the
// close watcher, the page's own storage for small settings, the loading screen and the build it was served.

internal fun jsPathname(): String = js("window.location.pathname")
internal fun jsOrigin(): String = js("window.location.origin")

/** history.state's entry index (the web shell's), -1 when the entry has none. */
internal fun jsHistoryIndex(): Int = js("(history.state && typeof history.state.atw === 'number') ? history.state.atw : -1")
internal fun jsPushState(path: String, index: Int): Unit = js("history.pushState({ atw: index }, '', path)")
internal fun jsReplaceState(path: String, index: Int): Unit = js("history.replaceState({ atw: index }, '', path)")
internal fun jsHistoryBack(): Unit = js("history.back()")

/** Calls [popped] with the new address's path and its entry index (-1: none) on each browser Back or Forward. */
internal fun jsOnPopState(popped: (String, Int) -> Unit): Unit = js("""{
    window.addEventListener('popstate', (event) => {
        const index = (event.state && typeof event.state.atw === 'number') ? event.state.atw : -1;
        popped(window.location.pathname, index);
    });
}""")

/** A close watcher (Android Chrome's system Back closes it instead of going back), null where the browser has none. */
internal fun jsCloseWatcher(closed: () -> Unit): JsAny? = js("""(() => {
    if (typeof CloseWatcher !== 'function') return null;
    try { const watcher = new CloseWatcher(); watcher.onclose = () => closed(); return watcher; } catch (e) { return null; }
})()""")
internal fun jsDestroyCloseWatcher(watcher: JsAny): Unit = js("{ try { watcher.destroy(); } catch (e) {} }")

/** Whether a text field has the keyboard (Compose's text input is a hidden input or textarea while typing). */
internal fun jsTyping(): Boolean = js("""(() => {
    const element = document.activeElement;
    return !!element && (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA' || element.isContentEditable === true);
})()""")

internal fun jsLocalGet(key: String): String? = js("(() => { try { return localStorage.getItem(key); } catch (e) { return null; } })()")
internal fun jsLocalSet(key: String, value: String): Unit = js("{ try { localStorage.setItem(key, value); } catch (e) {} }")
internal fun jsSessionGet(key: String): String? = js("(() => { try { return sessionStorage.getItem(key); } catch (e) { return null; } })()")
internal fun jsSessionSet(key: String, value: String?): Unit =
    js("{ try { if (value === null) sessionStorage.removeItem(key); else sessionStorage.setItem(key, value); } catch (e) {} }")

/** The build this page was served (index.html sets it), "" when unknown (a development server). */
internal fun jsBuildId(): String = js("(typeof globalThis.atwBuild === 'string') ? globalThis.atwBuild : ''")

/** Removes the first load's loading screen (index.html's) once the app draws. */
internal fun jsHideLoading(): Unit = js("{ const element = document.getElementById('atw-loading'); if (element) element.remove(); }")

/** Shows [text] on the loading screen (a failure before the app could start). */
internal fun jsLoadingMessage(text: String): Unit =
    js("{ const element = document.getElementById('atw-loading-text'); if (element) element.textContent = text; }")

internal fun jsReload(): Unit = js("window.location.reload()")

/** Calls [visible] each time the page comes back to the foreground, [hidden] each time it goes to the background. */
internal fun jsOnVisibility(visible: () -> Unit, hidden: () -> Unit): Unit = js("""{
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') visible(); else hidden();
    });
}""")

/** Calls [input] on each tap, click or key anywhere on the page (the game heartbeat's "did something"). */
internal fun jsOnInput(input: () -> Unit): Unit = js("""{
    window.addEventListener('pointerdown', () => input(), true);
    window.addEventListener('keydown', () => input(), true);
}""")

/** Whether the page was opened for the first time in this browser (no earlier visit left its marker). */
internal fun jsFirstVisit(): Boolean = js("""(() => {
    try {
        if (localStorage.getItem('atw.visited') !== null) return false;
        localStorage.setItem('atw.visited', '1');
        return true;
    } catch (e) { return false; }
})()""")
