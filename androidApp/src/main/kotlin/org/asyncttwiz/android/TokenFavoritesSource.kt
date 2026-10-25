package org.asyncttwiz.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*

interface TokenFavoritesSource {
    suspend fun cached(): TokenFavorites
    suspend fun refresh(): TokenFavorites
    suspend fun set(favorites: TokenFavorites)
}

/** Per-account offline cache. Failed writes remain dirty and are retried on the next opening. */
internal class AccountTokenFavoritesSource(context: Context, account: String,
    private val token: () -> String?, private val origin: String = BuildConfig.ACCOUNT_ORIGIN) : TokenFavoritesSource {
    private val preferences = context.applicationContext.getSharedPreferences("token-favorites", Context.MODE_PRIVATE)
    private val key = "$origin/$account"
    private val mutex = Mutex()
    private fun read() = preferences.getString(key, null)?.let {
        runCatching { AccountJson.format.decodeFromString<TokenFavorites>(it) }.getOrNull()
    } ?: TokenFavorites()
    override suspend fun cached() = withContext(Dispatchers.IO) { mutex.withLock { read() } }
    override suspend fun refresh(): TokenFavorites = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = token() ?: throw AccountFailure("authentication_required")
            AccountApi(origin).use { api ->
                val result = if (preferences.getBoolean("$key/dirty", false)) api.setTokenFavorites(session, read())
                    else api.tokenFavorites(session)
                preferences.edit().putString(key, AccountJson.format.encodeToString(result)).putBoolean("$key/dirty", false).commit()
                result
            }
        }
    }
    override suspend fun set(favorites: TokenFavorites) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(preferences.edit().putString(key, AccountJson.format.encodeToString(favorites))
                .putBoolean("$key/dirty", true).commit())
            val session = token() ?: throw AccountFailure("authentication_required")
            AccountApi(origin).use { it.setTokenFavorites(session, favorites) }
            preferences.edit().putBoolean("$key/dirty", false).commit()
            Unit
        }
    }
}
