package org.asyncttwiz.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.appui.postToMain
import org.asyncttwiz.appui.newSingleThreadExecutor
import org.asyncttwiz.appui.newSingleThreadScheduler
import kotlin.reflect.KClass
import org.asyncttwiz.appui.randomUuid
import org.asyncttwiz.appui.executeSuspending

/** The app's controllers for the signed-in shell, kept across configuration changes; [services]: the platform's. */
class AuthViewModel constructor(services: AppServices) : ViewModel() {
    private val catalogStore = ViewModelStore()
    val catalog = ViewModelProvider.create(catalogStore, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
            CatalogViewModel(services.catalogController()) as T
    })[CatalogViewModel::class]
    /** Saved decks' and cubes' cards come from the phone's card records by printing (owner 2026-11-10). */
    private val cardRecords = SavedCardRecords { ids -> SavedCardFaces(catalog.repository.cards(ids), catalog.repository.metadata()) }
    val deckImport = DeckImportController(catalog.controller.deckResolver()::resolve,
        newSingleThreadExecutor("deck-import"), { action -> postToMain(action) }).apply {
        connectUrl({ catalog.controller.deckResolver().resolve(it) })
        acceptedDefinitions = { deck, definitions -> prefetchDeckArtwork(services.artworkContext, deck, definitions) }
        search.source = { query, cursor -> this@AuthViewModel.catalog.repository.searchCards(query, cursor) }
        search.cardData = { this@AuthViewModel.catalog.repository.searchData(it) }
    }
    private val revocations = newSingleThreadExecutor("best-effort-logout")
    private val local = services.accountLocalData().also { local ->
        AutomatumBoards.definitionSource = local::cardDefinitions
    }
    /** LOB-115: which cards of a deck or cube Automatum can't play yet, from the card catalog's marks (section 3.8). */
    val cardListSupport = CardListSupportCheck(local::cardDefinitions, catalog.repository::unsupported)
    /** Section 3.9: the warning sign on cards Automatum can't play yet. */
    val cardMarks = CardMarks(catalog.repository::unsupportedPictures)
    val activity = services.activityDelivery
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
    val controller = AuthController(AccountApi(AppBuild.ACCOUNT_ORIGIN),
        services.sessionStore(),
        newSingleThreadExecutor("account-entry"),
        local = local,
        accountSwitcher = AppBuild.ACCOUNT_SWITCHER,
        revoke = { token -> revocations.executeSuspending {
            runCatching { run {
                AccountApi(AppBuild.ACCOUNT_ORIGIN).use { it.revoke(token) }
            } }
        } },
        post = { action -> postToMain(action) }, clearPrivateWork = ::clearDeckWork)

    val decks = DeckLibraryController(DeckApi(AppBuild.ACCOUNT_ORIGIN,
        authenticationRequired = ::authenticationRequired), controller::sessionToken,
        newSingleThreadExecutor("saved-decks"), { action -> postToMain(action) })
    private val cubeResolver = catalog.controller.cubeResolver()
    val cubes = CubeController(DeckApi(AppBuild.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired),
        controller::sessionToken, cubeResolver::resolve, cubeResolver::resolve,
        newSingleThreadExecutor("saved-cubes"), { action -> postToMain(action) }).apply {
        search.source = { query, cursor -> this@AuthViewModel.catalog.repository.searchCards(query, cursor) }
        search.cardData = { this@AuthViewModel.catalog.repository.searchData(it) }
    }
    val pods = PodController(PodApi(AppBuild.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired),
        DeckApi(AppBuild.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired), controller::sessionToken,
        newSingleThreadExecutor("pods"), { action -> postToMain(action) },
        marksScheduler = newSingleThreadScheduler("pod-marks", daemon = true).let { schedule ->
            PodMarksScheduler { delay, task -> schedule(delay, task) }
        }, setRepository = StoredPodSets { catalog.repository },
        chatWorker = newSingleThreadExecutor("pod-chat"))
    private val saveApi = DeckApi(AppBuild.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired)
    val lobby = LobbyController(LobbyApi(AppBuild.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired),
        controller::sessionToken, newSingleThreadExecutor("contacts-lobby"),
        { action -> postToMain(action) }, openGame = { controller.push(ShellDestination.GAMES) },
        gameConnection = platformGameConnection(AppBuild.ACCOUNT_ORIGIN), accountId = { controller.state.account?.accountId },
        authenticationRequired = ::authenticationRequired,
        installationId = AppPlatform.current.preferences("installation").let { preferences ->
            preferences.getString("id", null) ?: randomUuid().also {
                preferences.edit().putString("id", it).apply()
            }
        }, deckApi = DeckApi(AppBuild.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired),
        podApi = PodApi(AppBuild.ACCOUNT_ORIGIN, authenticationRequired = ::authenticationRequired),
        activityTarget = { token, route ->
            try { ActivityApi(AppBuild.ACCOUNT_ORIGIN).use { it.target(token, route) } }
            catch (failure: ActivityFetchFailure) {
                if (failure.status == 401) authenticationRequired(token)
                throw failure
            }
        }, namedCards = CatalogNamedCards { catalog.repository })
    val heartbeat = GameHeartbeatController { token, presence ->
        controller.sessionToken() == token && lobby.heartbeat(presence)
    }
    private val gameMaintenance = GameConnectionMaintenance({ true }, {
        controller.foreground()
        if (controller.state.account != null) {
            lobby.sync()
        }
    }, mainLooperDelayed, lobby::tick)

    init {
        deckImport.cardRecords = cardRecords; decks.cardRecords = cardRecords; cubes.cardRecords = cardRecords
        pods.cardRecords = cardRecords; lobby.cardRecords = cardRecords
        lobby.gameInput = heartbeat::input
        activity.authenticationRequired = ::authenticationRequired
        activity.activityHints = { hints ->
            val token = controller.sessionToken()
            postToMain { if (token != null && token == controller.sessionToken()) hints.forEach(pods::hint) }
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

    val reset = ResetController(AccountApi(AppBuild.ACCOUNT_ORIGIN), AppBuild.RESET_ORIGIN,
        newSingleThreadExecutor("password-reset"),
        succeeded = controller::passwordResetCompleted, post = { action -> postToMain(action) })

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
