package org.asyncttwiz.server.accounts

import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime

class TokenFavoritesService(private val runtime: ServiceRuntime) {
    fun read(accountId: String): TokenFavorites = runtime.transaction { connection ->
        connection.prepareStatement("SELECT keys_json FROM account_token_favorites WHERE account_id = ?").use { statement ->
            statement.setObject(1, UUID.fromString(accountId))
            statement.executeQuery().use { rows ->
                if (rows.next()) AccountJson.format.decodeFromString<TokenFavorites>(rows.getString(1)) else TokenFavorites()
            }
        }
    }

    fun set(accountId: String, favorites: TokenFavorites): TokenFavorites {
        val keys = favorites.keys ?: throw InvalidAccountField("keys")
        if (keys.size > 100 || keys.any { !TokenIdentity.validKey(it) }) throw InvalidAccountField("keys")
        val stored = TokenFavorites(keys.distinct().sorted())
        runtime.transaction { connection ->
            connection.prepareStatement("""
                INSERT INTO account_token_favorites(account_id, keys_json) VALUES (?, ?)
                ON CONFLICT (account_id) DO UPDATE SET keys_json = EXCLUDED.keys_json
            """.trimIndent()).use { statement ->
                statement.setObject(1, UUID.fromString(accountId))
                statement.setString(2, AccountJson.format.encodeToString(stored))
                statement.executeUpdate()
            }
        }
        return stored
    }
}
