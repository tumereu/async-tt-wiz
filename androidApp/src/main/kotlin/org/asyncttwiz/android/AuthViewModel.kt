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
    /** Saved decks' and cubes' cards come from the phone's card records by printing (owner 2026-11-10). */
    private val cardRecords = SavedCardRecords { ids -> SavedCardFaces(catalog.repository.cards(ids), catalog.repository.metadata()) }
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
    /** LOB-115: which cards of a deck or cube Automatum can't play yet, from the card catalog's marks (section 3.8). */
    internal val cardListSupport = CardListSupportCheck(local::cardDefinitions, catalog.repository::unsupported)
    /** Section 3.9: the warning sign on cards Automatum can't play yet. */
    internal val cardMarks = CardMarks(catalog.repository::unsupportedPictures)
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
            // DRF-172: a chat message's notice opens the pod with its chat open
            if (route.kind == org.asyncttwiz.protocol.ActivityKind.POD_MESSAGE) pods.openChat()
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
    }
    val pods = PodController(PodApi(BuildConfig.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired),
        DeckApi(BuildConfig.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired), controller::sessionToken,
        Executors.newSingleThreadExecutor { Thread(it, "pods") }, { action -> main.post { action() } },
        marksScheduler = Executors.newSingleThreadScheduledExecutor { Thread(it, "pod-marks").apply { isDaemon = true } }.let { marks ->
            PodMarksScheduler { delay, task ->
                val scheduled = marks.schedule(Runnable(task), delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                val cancel: () -> Unit = { scheduled.cancel(false) }
                cancel
            }
        }, setRepository = StoredPodSets { catalog.repository },
        chatWorker = Executors.newSingleThreadExecutor { Thread(it, "pod-chat") })
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
        })
    internal val heartbeat = GameHeartbeatController { token, presence ->
        controller.sessionToken() == token && lobby.heartbeat(presence)
    }
    private val gameMaintenance = GameConnectionMaintenance({ true }, {
        controller.foreground()
        if (controller.state.account != null) {
            lobby.sync()
        }
    }, main, lobby::tick)

    init {
        deckImport.cardRecords = cardRecords; decks.cardRecords = cardRecords; cubes.cardRecords = cardRecords
        pods.cardRecords = cardRecords; lobby.cardRecords = cardRecords
        lobby.gameInput = heartbeat::input
        activity.authenticationRequired = ::authenticationRequired
        activity.activityHints = { hints ->
            val token = controller.sessionToken()
            main.post { if (token != null && token == controller.sessionToken()) hints.forEach(pods::hint) }
        }
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
