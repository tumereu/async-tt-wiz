package org.asyncttwiz.android

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.asyncttwiz.storage.DeckImportResolver
import java.util.concurrent.Executors

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val main = Handler(Looper.getMainLooper())
    private val catalogStore = ViewModelStore()
    val catalog = ViewModelProvider(catalogStore, ViewModelProvider.AndroidViewModelFactory(application))[CatalogViewModel::class.java]
    val deckImport = DeckImportController(DeckImportResolver(catalog.repository)::resolve,
        Executors.newSingleThreadExecutor { Thread(it, "deck-import") }, { action -> main.post { action() } }).apply {
        connectUrl({ DeckImportResolver(catalog.repository).resolve(it) })
        acceptedDefinitions = { deck, definitions -> prefetchDeckArtwork(application, deck, definitions) }
        search.source = { query, cursor -> this@AuthViewModel.catalog.repository.searchCards(query, cursor) }
        search.cardData = { this@AuthViewModel.catalog.repository.searchData(it) }
    }
    private val revocations = Executors.newSingleThreadExecutor { Thread(it, "best-effort-logout") }
    private val local = AndroidAccountLocalData(application).also { local ->
        AutomatumBoards.definitionSource = local::cardDefinitions
    }
    /** I-D51: which cards of a deck or cube Automatum plays by hand, from the device's parsed card definitions. */
    internal val cardListSupport = CardListSupportCheck(local::cardDefinitions, dungeons = catalog.dungeons::provided)
    val activity = AndroidActivityDelivery.get(application)
    fun openActivity(route: ActivityRoute) {
        val heldInPod = route.heldInPod
        if (heldInPod != null) {
            // DRF-137: a challenge that waits for your deck opens its pod's Games tab
            controller.navigate(ShellDestination.PODS)
            if (controller.state.account?.accountId != route.accountId || !activity.accepts(route.accountId, route.binding)) pods.showList()
            else pods.open(heldInPod, PodTab.GAMES)
            return
        }
        if (route.kind.isPodActivity()) {
            controller.navigate(ShellDestination.PODS)
            if (controller.state.account?.accountId != route.accountId || !activity.accepts(route.accountId, route.binding)) {
                pods.showList()
                return
            }
            if (route.kind == org.asyncttwiz.protocol.ActivityKind.POD_INVITATION) pods.showList() else pods.open(route.gameId)
            return
        }
        controller.navigate(ShellDestination.GAMES)
        if (controller.state.account?.accountId != route.accountId || !activity.accepts(route.accountId, route.binding)) {
            lobby.activityUnavailable("This notification belongs to an earlier sign-in. Showing your current Games.")
            return
        }
        if (route.kind == org.asyncttwiz.protocol.ActivityKind.GAME) lobby.openNotified(route.gameId)
        else lobby.openActivity(route) { activity.accepts(route.accountId, route.binding) }
    }
    private fun authenticationRequired(token: String) {
        activity.rejected(token)
        controller.authenticationRequired(token)
    }
    val controller = AuthController(AccountApi(BuildConfig.ACCOUNT_ORIGIN),
        ProtectedSessionStore(application, BuildConfig.ACCOUNT_ORIGIN),
        Executors.newSingleThreadExecutor { Thread(it, "account-entry") },
        local = local,
        accountSwitcher = BuildConfig.ACCOUNT_SWITCHER,
        revoke = { token -> revocations.execute {
            runCatching { kotlinx.coroutines.runBlocking {
                AccountApi(BuildConfig.ACCOUNT_ORIGIN).use { it.revoke(token) }
            } }
        } },
        post = { action -> main.post { action() } }, clearPrivateWork = ::clearDeckWork)

    val decks = DeckLibraryController(DeckApi(BuildConfig.ACCOUNT_ORIGIN,
        authenticationRequired = ::authenticationRequired), controller::sessionToken,
        Executors.newSingleThreadExecutor { Thread(it, "saved-decks") }, { action -> main.post { action() } })
    private val cubeResolver = org.asyncttwiz.storage.CubeImportResolver(catalog.repository)
    val cubes = CubeController(DeckApi(BuildConfig.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired),
        controller::sessionToken, cubeResolver::resolve, cubeResolver::resolve,
        Executors.newSingleThreadExecutor { Thread(it, "saved-cubes") }, { action -> main.post { action() } }).apply {
        search.source = { query, cursor -> this@AuthViewModel.catalog.repository.searchCards(query, cursor) }
        search.cardData = { this@AuthViewModel.catalog.repository.searchData(it) }
        supportCheck = cardListSupport
    }
    val pods = PodController(PodApi(BuildConfig.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired),
        DeckApi(BuildConfig.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired), controller::sessionToken,
        Executors.newSingleThreadExecutor { Thread(it, "pods") }, { action -> main.post { action() } },
        librarianDefinition = { podLibrarianDefinition(catalog.repository) },
        basicLandDefinitions = { podBasicDefinitions(catalog.repository) },
        marksScheduler = Executors.newSingleThreadScheduledExecutor { Thread(it, "pod-marks").apply { isDaemon = true } }.let { marks ->
            PodMarksScheduler { delay, task ->
                val scheduled = marks.schedule(Runnable(task), delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                val cancel: () -> Unit = { scheduled.cancel(false) }
                cancel
            }
        }, setRepository = StoredPodSets({
            org.asyncttwiz.storage.SetSourceRepository(java.io.File(application.noBackupFilesDir, "mtg-set-sources"),
                catalog.repository, if (BuildConfig.DEBUG && BuildConfig.CATALOG_TEST_ORIGIN.isNotEmpty())
                    org.asyncttwiz.storage.HttpCatalogSource(BuildConfig.CATALOG_BASE, BuildConfig.CATALOG_TEST_ORIGIN)
                else org.asyncttwiz.storage.HttpCatalogSource(), log = { AppLog.info("sets", it) })
        }, support = { catalog.setSupport.sets() }, request = catalog::requestSetSupport, now = catalog::setSupportNow))
    private val saveApi = DeckApi(BuildConfig.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired)
    val lobby = LobbyController(LobbyApi(BuildConfig.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired),
        controller::sessionToken, Executors.newSingleThreadExecutor { Thread(it, "contacts-lobby") },
        { action -> main.post { action() } }, openGame = { controller.push(ShellDestination.GAMES) },
        gameConnection = SocketGameConnection(BuildConfig.ACCOUNT_ORIGIN), accountId = { controller.state.account?.accountId },
        authenticationRequired = ::authenticationRequired,
        installationId = application.getSharedPreferences("installation", 0).let { preferences ->
            preferences.getString("id", null) ?: java.util.UUID.randomUUID().toString().also {
                preferences.edit().putString("id", it).apply()
            }
        }, deckApi = DeckApi(BuildConfig.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired),
        activityTarget = { token, route ->
            try { ActivityApi(BuildConfig.ACCOUNT_ORIGIN).use { it.target(token, route) } }
            catch (failure: ActivityFetchFailure) {
                if (failure.status == 401) authenticationRequired(token)
                throw failure
            }
        }, basicLandDefinitions = { podBasicDefinitions(catalog.repository) },
        dungeonDefinitions = catalog.dungeons::definitions)
    internal val heartbeat = GameHeartbeatController { token, presence ->
        controller.sessionToken() == token && lobby.heartbeat(presence)
    }
    private val gameMaintenance = GameConnectionMaintenance({ true }, {
        controller.foreground()
        if (controller.state.account != null) {
            lobby.sync()
            // Switch-review #11: while a game is open only the list's changes are read (badges); the rest waits
            if (lobby.state.selected == null) {
                refreshAutomatumSupport()
            }
        }
    }, main, lobby::tick)

    /** Owner playtest 2026-11-06: the background runs over every saved list read one list a second (three requests or so),
     * well inside the deck service's 600 requests a minute, so a deck chosen meanwhile is never refused as busy. */
    private suspend fun paceSavedLists() = kotlinx.coroutines.delay(1_000)

    private val cardDataWork by lazy { Executors.newSingleThreadExecutor { Thread(it, "card-data-refresh") } }
    // I-D51 (LOB-115, DRF-161): saved decks and cubes without Automatum support for this parser version get it in the
    // background, on the card data worker, at most every 15 minutes while the app returns.
    @Volatile private var supportRun: Pair<String, Long>? = null
    private fun refreshAutomatumSupport() {
        val account = controller.state.account?.accountId ?: return
        val token = controller.sessionToken() ?: return
        val now = System.currentTimeMillis()
        supportRun?.let { (last, at) -> if (last == account && now - at < 15 * 60_000L) return }
        supportRun = account to now
        cardDataWork.execute {
            try {
                val outcome = DeckApi(BuildConfig.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired).use { api ->
                    kotlinx.coroutines.runBlocking { AutomatumSupportRun(api, api, cardListSupport.parserVersion, cardListSupport.engineVersion, cardListSupport::support,
                        pace = ::paceSavedLists).run(token) }
                }
                if (outcome.decks + outcome.cubes > 0)
                    AppLog.info("Automatum", "Automatum support worked out: ${outcome.decks} decks, ${outcome.cubes} cubes")
            } catch (failure: Exception) {
                AppLog.warn("Automatum", "Automatum support of saved lists failed; trying again later", failure)
                if (supportRun?.first == account) supportRun = null
            }
        }
    }
    init {
        lobby.gameInput = heartbeat::input
        activity.authenticationRequired = ::authenticationRequired
        activity.activityHints = { hints ->
            val token = controller.sessionToken()
            main.post { if (token != null && token == controller.sessionToken()) hints.forEach(pods::hint) }
        }
        deckImport.supportCheck = cardListSupport
        deckImport.connectSave(saveApi, controller::sessionToken, saved = {
            // An import started from a lobby's Choose deck returns there with the new deck highlighted.
            if (!lobby.importedForPicker(it.deck)) {
                controller.push(ShellDestination.DECKS)
                decks.open(it.deck.id)
            }
        })
    }
    init {
        // LOB-057: a deck deleted from its editor leaves for the deck list.
        deckImport.connectDelete(saveApi, controller::sessionToken, deleted = { decks.refresh() })
    }
    private fun clearDeckWork() { activity.clear(); deckImport.exit(); decks.clear(); cubes.clear(); pods.clear(); lobby.clear() }

    val reset = ResetController(AccountApi(BuildConfig.ACCOUNT_ORIGIN), BuildConfig.RESET_ORIGIN,
        Executors.newSingleThreadExecutor { Thread(it, "password-reset") },
        succeeded = controller::passwordResetCompleted, post = { action -> main.post { action() } })

    override fun onCleared() {
        heartbeat.close()
        gameMaintenance.close()
        cardDataWork.shutdown()
        activity.authenticationRequired = null
        activity.activityHints = null
        controller.close()
        deckImport.close()
        decks.close()
        cubes.close()
        pods.close()
        saveApi.close()
        lobby.close()
        revocations.execute { local.close() }
        catalogStore.clear()
        reset.close()
        // Drain bounded best-effort requests without retaining credentials on disk.
        revocations.shutdown()
    }
}
