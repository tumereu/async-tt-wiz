package org.asyncttwiz.android

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*

/** Server-side account preferences with a per-account offline copy. */
interface AccountSettingsSource {
    suspend fun cached(): AccountSettings
    suspend fun refresh(): AccountSettings
    suspend fun set(settings: AccountSettings)
}

/** Failed writes stay dirty locally and are sent again on the next refresh. */
internal class AccountSettingsStore(context: Context, account: String,
    private val token: () -> String?, private val origin: String = BuildConfig.ACCOUNT_ORIGIN) : AccountSettingsSource {
    private val preferences = context.applicationContext.getSharedPreferences("account-settings", Context.MODE_PRIVATE)
    private val key = "$origin/$account"
    private val mutex = Mutex()
    private fun read() = preferences.getString(key, null)?.let {
        runCatching { AccountJson.format.decodeFromString<AccountSettings>(it) }.getOrNull()
    } ?: AccountSettings()
    override suspend fun cached() = withContext(Dispatchers.IO) { mutex.withLock { read() } }
    override suspend fun refresh(): AccountSettings = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = token() ?: throw AccountFailure("authentication_required")
            AccountApi(origin).use { api ->
                val result = if (preferences.getBoolean("$key/dirty", false)) api.setAccountSettings(session, read())
                    else api.accountSettings(session)
                preferences.edit().putString(key, AccountJson.format.encodeToString(result)).putBoolean("$key/dirty", false).commit()
                result
            }
        }
    }
    override suspend fun set(settings: AccountSettings) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(preferences.edit().putString(key, AccountJson.format.encodeToString(settings)).putBoolean("$key/dirty", true).commit())
            val session = token() ?: throw AccountFailure("authentication_required")
            AccountApi(origin).use { it.setAccountSettings(session, settings) }
            preferences.edit().putBoolean("$key/dirty", false).commit()
            Unit
        }
    }
}

@Composable
internal fun rememberAccountSettingsSource(accountId: String, auth: AuthController?): AccountSettingsSource? {
    val context = LocalContext.current
    return remember(context, accountId, auth) { auth?.let { AccountSettingsStore(context, accountId, it::sessionToken) } }
}

internal val DeckVisibility.label get() = when (this) {
    DeckVisibility.VISIBLE -> "Visible"
    DeckVisibility.HIDDEN -> "Hidden"
    DeckVisibility.AFTER_FIRST_GAME -> "Show after first game"
}
internal val DeckVisibility.explanation get() = when (this) {
    DeckVisibility.VISIBLE -> "Opponents see your deck's name and art"
    DeckVisibility.HIDDEN -> "Opponents don't see your deck's name or art"
    DeckVisibility.AFTER_FIRST_GAME -> "Hidden until you've finished a game with that deck against that opponent"
}

/** UI state for the account settings: cached value first, then the server's; changes save at once. */
@Stable
internal class AccountSettingsState(private val source: AccountSettingsSource, private val scope: kotlinx.coroutines.CoroutineScope) {
    var settings by mutableStateOf<AccountSettings?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    fun load() = scope.launch {
        settings = source.cached()
        // Background read: a failure stays silent, the cached value remains shown.
        runCatching { source.refresh() }.onSuccess { settings = it }
    }
    fun setDeckVisibility(visibility: DeckVisibility) {
        val next = (settings ?: AccountSettings()).copy(deckVisibility = visibility)
        settings = next
        scope.launch {
            runCatching { source.set(next) }.onFailure {
                error = "Couldn't save deck visibility to the server. It is kept on this device and sent later."
            }
        }
    }
    fun dismissError() { error = null }
}
