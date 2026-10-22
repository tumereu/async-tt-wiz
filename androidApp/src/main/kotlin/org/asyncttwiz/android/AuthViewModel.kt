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
        Executors.newSingleThreadExecutor { Thread(it, "deck-import") }, { action -> main.post { action() } })
    private val revocations = Executors.newSingleThreadExecutor { Thread(it, "best-effort-logout") }
    val controller = AuthController(AccountApi(BuildConfig.ACCOUNT_ORIGIN),
        ProtectedSessionStore(application, BuildConfig.ACCOUNT_ORIGIN),
        Executors.newSingleThreadExecutor { Thread(it, "account-entry") },
        local = AndroidAccountLocalData(application),
        revoke = { token -> revocations.execute {
            runCatching { kotlinx.coroutines.runBlocking {
                AccountApi(BuildConfig.ACCOUNT_ORIGIN).use { it.revoke(token) }
            } }
        } },
        post = { action -> main.post { action() } }, clearPrivateWork = ::clearDeckWork)

    val decks = DeckLibraryController(DeckApi(BuildConfig.ACCOUNT_ORIGIN), controller::sessionToken,
        Executors.newSingleThreadExecutor { Thread(it, "saved-decks") }, { action -> main.post { action() } })
    private val saveApi = DeckApi(BuildConfig.ACCOUNT_ORIGIN)
    init {
        deckImport.connectSave(action = {
            controller.sessionToken()?.let { token ->
                { plan: DeckSavePlan -> kotlinx.coroutines.runBlocking {
                    val accepted = saveApi.submit(token, plan.definitions)
                    if (accepted.digests.toSet() != plan.definitions.definitions.map { it.digest }.toSet())
                        throw DeckFailure("invalid_response")
                    if (plan.target == null) saveApi.create(token, plan.deck)
                    else {
                        saveApi.update(token, plan.target, plan.update())
                        // An uncertain retry may return an older original outcome. Reopen current
                        // server state, never reapply additive quantities or install that old result.
                        saveApi.read(token, plan.target)
                    }
                } }
            }
        }, saved = { decks.open(it.deck.id) })
    }
    private fun clearDeckWork() { deckImport.exit(); decks.clear() }

    val reset = ResetController(AccountApi(BuildConfig.ACCOUNT_ORIGIN), BuildConfig.RESET_ORIGIN,
        Executors.newSingleThreadExecutor { Thread(it, "password-reset") },
        succeeded = controller::passwordResetCompleted, post = { action -> main.post { action() } })

    override fun onCleared() {
        controller.close()
        deckImport.close()
        decks.close()
        saveApi.close()
        catalogStore.clear()
        reset.close()
        // Drain bounded best-effort requests without retaining credentials on disk.
        revocations.shutdown()
    }
}
