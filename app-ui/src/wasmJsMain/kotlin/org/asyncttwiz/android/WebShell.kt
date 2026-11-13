@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalComposeUiApi::class, InternalComposeUiApi::class)

package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.LocalCompatNavigationEventDispatcherOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import androidx.navigationevent.NavigationEventInput
import kotlinx.browser.document
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.appui.BuildInfo
import org.asyncttwiz.appui.WebBack
import org.asyncttwiz.appui.WebNotes
import org.asyncttwiz.appui.installBuildInfo
import org.asyncttwiz.appui.pageScope
import org.asyncttwiz.appui.postToMainDelayed
import org.asyncttwiz.appui.unlockSound
import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.PodPhase

// The web client's shell (docs/specs/web-client.md §11 step 3, docs/features/web.md): the page's entry point that opens
// the browser's storage and installs the app's platform and services, then the shared app (sign-in, lobby, decks, cubes,
// settings) in the phone layout, with addresses and browser history (WEB-001, WEB-002), the on-screen Back (WEB-003),
// Android Chrome's system Back for layers (WEB-004), the stale-tab reload (WEB-006), and a game's table (step 4, WEB-011:
// the engine's Web Worker for its plays, the browser's own gestures kept off it, haptics).

/** Starts the web app in this page with the build's values [build] (the webApp module's entry point). */
fun startWebApp(build: BuildInfo, assetBase: String) {
    installBuildInfo(build)
    pageScope.launch {
        val assets = try { WebAppPlatform.fetchAssets(assetBase) } catch (failure: Throwable) {
            jsLoadingMessage("ATW couldn't load. Check your connection and reload the page.")
            throw failure
        }
        val platform = WebAppPlatform(assets, jsFirstVisit())
        AppPlatform.install(platform)
        // Symbols Roboto lacks fall back to the bundled Noto fonts, as on Android to its system fonts
        preloadFallbackFonts(platform)
        // The card records and the session need the page's own storage before anything reads them
        val storage = BrowserStorage.open(pageScope)
        val catalog = BrowserCatalogController(storage, build.accountOrigin, build.catalogChecksum.ifEmpty { null }, pageScope)
        AppServices.install(BrowserAppServices(storage, catalog))
        startBrowserDiagnostics()
        // The table's plays are worked out in the engine's Web Worker; requests wait until it has loaded
        val worker = startEngineWorkers(assetBase)
        EngineBoundary.useRemote(worker.planning)
        // Web client step 4c part 3: the games' devices in the worker too, when the page asks for it
        if (jsDevicesInWorker()) EngineBoundary.useDevices(worker.devices)
        jsGuardTablePage()
        unlockSound()
        val auth = AuthViewModel(AppServices.current)
        val shell = WebShell(auth)
        jsOnInput { auth.heartbeat.input() }
        jsOnVisibility(visible = shell::foreground, hidden = shell::background)
        shell.foreground()
        val body = document.body ?: run {
            org.asyncttwiz.protocol.Faults.report("WebShell", "the page has no body to draw in", emptyMap())
            return@launch
        }
        // The screens' semantics mirrored into the page (screen readers; the browser smoke finds controls by them)
        ComposeViewport(body, configure = { isA11YEnabled = true }) {
            CompositionLocalProvider(LocalFontFamilyResolver provides org.asyncttwiz.appui.webFontResolver) { shell.Content() }
        }
        jsHideLoading()
    }
}

/** The page's app: the shared controllers ([auth]) and what the browser adds around them. */
internal class WebShell(private val auth: AuthViewModel) {
    /** Manage decks' Cubes tab, kept here so addresses can read and set it (WEB-001: /cubes/<id>). */
    private val collectionTab: MutableState<Boolean> = mutableStateOf(false)
    private val router = WebRouter(auth, collectionTab)
    private val stale = StaleBuild()

    fun foreground() {
        auth.catalog.foreground()
        auth.controller.foreground()
        auth.lobby.foreground()
        auth.activity.foreground()
        stale.check()
    }

    fun background() {
        auth.lobby.background()
        auth.heartbeat.display(null, null)
        DebugLatency.appBackgrounded()
    }

    @Composable fun Content() {
        MaterialTheme {
            val state = auth.controller.state
            val token = auth.controller.sessionToken()
            val activityScreen by remember { derivedStateOf {
                ActivityScreen.of(auth.controller.state.destination, auth.lobby.state.selected, auth.pods.state)
            } }
            SideEffect {
                if (!state.restoring && !state.busy) {
                    DebugCrashes.authenticated(if (state.account != null) token else null)
                    if (auth.activity.session(state.account?.accountId, token)) auth.activity.foreground()
                }
                auth.activity.display(activityScreen, true)
                auth.heartbeat.display(activityScreen.game, token)
            }
            router.Sync()
            stale.Watch(router)
            val catalogBlocked = !auth.catalog.state.ready && !auth.catalog.state.failed
            val releaseNotes = remember { ReleaseNotePreferences(AppPlatform.current) }
            var whatsNew by remember { mutableStateOf(releaseNotes.unseen()) }
            if (ReleaseNotes.showDialog(whatsNew, false, activityScreen))
                WhatsNewDialog(whatsNew) { releaseNotes.markSeen(whatsNew); whatsNew = emptyList() }
            val reportChoices = remember { ReportChoiceStore(AppPlatform.current) }
            var reportChoice by remember { mutableStateOf(reportChoices.choice) }
            if (ReportPrompt.shouldPrompt(Diagnostics.alwaysOn, reportChoice, state.account != null, state.destination,
                    auth.lobby.state.selected, whatsNew.isNotEmpty() || catalogBlocked)) {
                ReportsPromptDialog { on -> Diagnostics.choose(on); reportChoice = reportChoices.choice }
            }
            WebCloseWatcher()
            // WEB-011: the browser's own scrolling, zooming, selection and menus stay off the table and the draft
            val tableChrome = router.tableOpen() || router.draftOpen()
            DisposableEffect(tableChrome) {
                AppLog.info("web-page", if (tableChrome) "Table page: browser gestures off" else "Screen page: browser gestures back")
                jsTablePage(tableChrome)
                onDispose { }
            }
            Surface(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalCardListSupport provides auth.cardListSupport, LocalCardMarks provides auth.cardMarks,
                    LocalCollectionTab provides collectionTab, LocalScreenBack provides router.screenBack(),
                    LocalHapticFeedback provides BrowserHaptics) {
                    AccountEntry(auth.controller, auth.reset, auth.catalog, auth.deckImport, auth.decks, auth.lobby, auth.cubes,
                        pods = auth.pods)
                }
            }
            WebNotes.text?.let { text ->
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
                    Surface(color = Color(0xEE323232), shape = MaterialTheme.shapes.medium) {
                        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp).testTag("web-note"), color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Android Chrome's system Back while a layer is open (WEB-004): a close watcher stands while Compose's own Back has an
 * enabled handler (a dialog, popup, panel or the side menu: Esc closes the same ones), and its close is Compose's Back.
 * Without a layer the system Back is the browser's Back.
 */
@Composable private fun WebCloseWatcher() {
    val owner = LocalCompatNavigationEventDispatcherOwner.current ?: return
    DisposableEffect(owner) {
        val input = CloseWatcherInput()
        owner.navigationEventDispatcher.addInput(input)
        onDispose { owner.navigationEventDispatcher.removeInput(input); input.stop() }
    }
}

private class CloseWatcherInput : NavigationEventInput() {
    private var watcher: JsAny? = null
    private var layers = false

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        layers = hasEnabledHandlers
        if (hasEnabledHandlers) start() else stop()
    }

    private fun start() {
        if (watcher != null) return
        watcher = jsCloseWatcher {
            watcher = null
            AppLog.info("web-back", "System Back closes a layer")
            dispatchOnBackCompleted()
            // Still a layer open (one closed of several): stand again once the closed one has left
            postToMainDelayed(150) { if (layers) start() }
        }
    }

    fun stop() { watcher?.let(::jsDestroyCloseWatcher); watcher = null }
}

/**
 * The page's address and browser history kept in step with the app's screen (WEB-001, WEB-002). The app's own moves
 * (taps, the on-screen Back) add or go back entries; the browser's Back and Forward open the entry's screen; a refreshed
 * page opens its address's screen once signed in.
 */
internal class WebRouter(private val auth: AuthViewModel, private val collectionTab: MutableState<Boolean>) {
    private val history = WebHistory(null, jsHistoryIndex().takeIf { it >= 0 } ?: 0)
    /** The address the page was opened at, opened once the account is known. */
    private var initial: WebRoute? = WebRoute.parse(jsPathname()) ?: WebRoute.Menu
    /** A deck whose editor its address opens once the deck has loaded (/decks/<id>/edit). */
    private var editorFor by mutableStateOf<String?>(null)
    /** The screen shown now, null while signed out. */
    var shown by mutableStateOf<WebRoute?>(null)
        private set
    /** Whether [shown] was just opened (the stale-tab reload may run as a game opens). */
    var opened = false
        private set

    init {
        jsOnPopState { path, index ->
            val route = WebRoute.parse(path) ?: WebRoute.Menu
            AppLog.info("web-route", "Browser history: ${shown?.path} -> ${route.path} (entry $index)")
            history.popped(route, index.takeIf { it >= 0 })
            if (derive() != route) open(route)
        }
    }

    @Composable fun Sync() {
        LaunchedEffect(Unit) {
            snapshotFlow { derive() }.collect { route ->
                val before = shown
                shown = route
                opened = route != before
                if (route == null) return@collect
                initial?.let { start ->
                    initial = null
                    AppLog.info("web-route", "Opening ${start.path} from the address")
                    history.open(start)
                    jsReplaceState(start.path, jsHistoryIndex().takeIf { it >= 0 } ?: 0)
                    if (start != route) { open(start); return@collect }
                }
                when (val step = history.shown(route)) {
                    HistoryStep.None -> Unit
                    is HistoryStep.Push -> { jsPushState(step.route.path, step.index); AppLog.info("web-route", "Screen ${step.route.path}") }
                    is HistoryStep.Replace -> jsReplaceState(step.route.path, step.index)
                    HistoryStep.Back -> { AppLog.info("web-route", "Back to ${route.path}"); jsHistoryBack() }
                }
            }
        }
        // /decks/<id>/edit: the editor opens once its deck has loaded
        LaunchedEffect(Unit) {
            snapshotFlow { editorFor to auth.decks.state.detail }.collect { (id, detail) ->
                if (id != null && detail?.saved?.deck?.id == id) {
                    editorFor = null
                    auth.decks.dismiss()
                    auth.deckImport.openEditor(detail)
                }
            }
        }
    }

    /** The screen the app shows now, by its controllers; null while signed out (sign-in pages keep the address). */
    fun derive(): WebRoute? {
        val state = auth.controller.state
        if (state.account == null || state.restoring) return null
        val pods = auth.pods.state
        val decks = auth.decks.state
        val cubes = auth.cubes.state
        val importer = auth.deckImport.state
        return when (state.destination) {
            ShellDestination.MENU -> WebRoute.Menu
            ShellDestination.GAMES -> auth.lobby.state.selected?.let(WebRoute::Game) ?: WebRoute.Games
            ShellDestination.PODS -> pods.selected?.let { WebRoute.Pod(it, pods.tab.takeIf { _ -> pods.detail?.id == it }) } ?: WebRoute.Pods
            ShellDestination.DECKS -> when {
                collectionTab.value && cubes.selected != null -> WebRoute.Cube(cubes.selected)
                collectionTab.value && cubes.editing -> WebRoute.CubeNew
                collectionTab.value -> WebRoute.Decks
                importer.active -> importer.target?.takeIf { importer.editing }?.let(WebRoute::DeckEdit) ?: WebRoute.DeckImport
                decks.selected != null -> WebRoute.Deck(decks.selected)
                else -> WebRoute.Decks
            }
            ShellDestination.CONTACTS -> WebRoute.Contacts
            ShellDestination.SETTINGS -> WebRoute.Settings
            ShellDestination.STATS -> WebRoute.Stats
        }
    }

    /** Opens [route]'s screen, as the side menu and the screens' own taps open them. */
    fun open(route: WebRoute) {
        val controller = auth.controller
        val lobby = auth.lobby
        val pods = auth.pods
        fun leaveCollectionDetail() {
            if (auth.deckImport.state.active) auth.deckImport.back()
            if (auth.decks.state.selected != null) auth.decks.back()
            if (auth.cubes.state.selected != null || auth.cubes.state.editing) auth.cubes.back()
        }
        editorFor = null
        when (route) {
            WebRoute.Menu -> controller.navigate(ShellDestination.MENU)
            WebRoute.Games -> { if (lobby.state.selected != null) lobby.back(); controller.navigate(ShellDestination.GAMES) }
            is WebRoute.Game -> { controller.navigate(ShellDestination.GAMES); if (lobby.state.selected != route.id) lobby.open(route.id) }
            WebRoute.Pods -> { pods.showList(); controller.navigate(ShellDestination.PODS) }
            is WebRoute.Pod -> {
                controller.navigate(ShellDestination.PODS)
                if (pods.state.selected != route.id) pods.open(route.id, route.tab) else route.tab?.let(pods::tab)
            }
            WebRoute.Decks -> { leaveCollectionDetail(); controller.navigate(ShellDestination.DECKS) }
            WebRoute.DeckImport -> {
                collectionTab.value = false
                controller.navigate(ShellDestination.DECKS)
                if (!auth.deckImport.state.active) auth.deckImport.open()
            }
            is WebRoute.Deck -> {
                collectionTab.value = false
                controller.navigate(ShellDestination.DECKS)
                if (auth.deckImport.state.active) auth.deckImport.back()
                if (auth.decks.state.selected != route.id) auth.decks.open(route.id)
            }
            is WebRoute.DeckEdit -> {
                collectionTab.value = false
                controller.navigate(ShellDestination.DECKS)
                if (!(auth.deckImport.state.active && auth.deckImport.state.target == route.id)) {
                    if (auth.decks.state.selected != route.id) auth.decks.open(route.id)
                    editorFor = route.id
                }
            }
            WebRoute.CubeNew -> {
                collectionTab.value = true
                controller.navigate(ShellDestination.DECKS)
                if (!auth.cubes.state.editing) auth.cubes.newCube()
            }
            is WebRoute.Cube -> {
                collectionTab.value = true
                controller.navigate(ShellDestination.DECKS)
                if (auth.cubes.state.selected != route.id) auth.cubes.open(route.id)
            }
            WebRoute.Contacts -> { lobby.contacts(false); controller.navigate(ShellDestination.CONTACTS) }
            WebRoute.Settings -> controller.navigate(ShellDestination.SETTINGS)
            WebRoute.Stats -> controller.navigate(ShellDestination.STATS)
        }
    }

    /** The on-screen Back (WEB-003): what Android's system Back would do here, else the screen this one is part of. */
    fun back() {
        if (WebBack.dispatch()) return
        shown?.parent?.let { AppLog.info("web-back", "On-screen Back to ${it.path}"); open(it) }
    }

    /** The headers' on-screen Back, on every screen but the menu. */
    fun screenBack(): (() -> Unit)? = if (shown == null || shown == WebRoute.Menu) null else ::back

    /** Whether the open game shows its table (WEB-007). */
    fun tableOpen(): Boolean = auth.controller.state.let { it.account != null && it.destination == ShellDestination.GAMES } &&
        auth.lobby.state.selected != null && auth.lobby.state.detail?.lifecycle == GameLifecycle.ACTIVE

    /** Whether an open pod shows its draft (WEB-011). */
    fun draftOpen(): Boolean = auth.controller.state.let { it.account != null && it.destination == ShellDestination.PODS } &&
        auth.pods.state.let { it.selected != null && it.detail?.phase == PodPhase.DRAFTING && it.tab == PodTab.DRAFT }
}

/**
 * Whether this tab runs an older web build than the server serves now (WEB-006): asked when the page starts, comes back
 * to the foreground and every 10 minutes; the reload waits for a safe moment ([StaleReload]) and leaves a note for the
 * next page ("ATW was updated").
 */
internal class StaleBuild {
    private val own = jsBuildId()
    var stale by mutableStateOf(false)
        private set
    private var asking = false

    fun check() {
        if (own.isEmpty() || stale || asking) return
        asking = true
        pageScope.launch {
            try {
                val current = fetchText("/web/build", 1024.0).await<JsString>().toString().trim()
                if (current.isNotEmpty() && current != own) {
                    AppLog.info("web-update", "Build $own is older than the server's $current: reloading at the next safe moment")
                    stale = true
                }
            } catch (failure: Throwable) {
                AppLog.info("web-update", "Build check failed: ${failure.message}")
            } finally { asking = false }
        }
    }

    @Composable fun Watch(router: WebRouter) {
        LaunchedEffect(Unit) {
            jsSessionGet(UPDATED)?.let {
                jsSessionSet(UPDATED, null)
                WebNotes.show("ATW was updated")
            }
            while (true) { delay(10 * 60_000L); check() }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { Triple(stale, router.shown, WebBack.layerOpen) }.collect { (isStale, route, layerOpen) ->
                // Typing has no snapshot to watch: asked again shortly while it waits
                while (isStale && !StaleReload.now(true, route, router.opened, layerOpen, jsTyping())) {
                    if (layerOpen || !(route == null || route == WebRoute.Menu || route == WebRoute.Games)) return@collect
                    delay(2_000)
                }
                if (isStale) {
                    AppLog.info("web-update", "Reloading for the new build at ${route?.path ?: "sign-in"}")
                    jsSessionSet(UPDATED, "1")
                    jsReload()
                }
            }
        }
    }

    private companion object { const val UPDATED = "atw.updated" }
}

/** The page's origin, the API's too (the server serves the web app beside it). */
fun pageOrigin(): String = jsOrigin()
