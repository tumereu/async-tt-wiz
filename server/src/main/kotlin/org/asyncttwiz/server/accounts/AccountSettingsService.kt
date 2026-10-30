package org.asyncttwiz.server.accounts

import java.util.UUID
import org.asyncttwiz.protocol.AccountSettings
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.game.DeckVisibilityStore
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/** Server-side account preferences, shared by all of the account's devices. */
class AccountSettingsService(private val runtime: ServiceRuntime) {
    fun read(accountId: String): AccountSettings = runtime.transaction { connection ->
        AccountSettings(DeckVisibilityStore.setting(DSL.using(connection, SQLDialect.POSTGRES), UUID.fromString(accountId)))
    }

    fun set(accountId: String, settings: AccountSettings): AccountSettings = runtime.transaction { connection ->
        val sql = DSL.using(connection, SQLDialect.POSTGRES)
        DeckVisibilityStore.save(sql, UUID.fromString(accountId), settings.deckVisibility)
        AccountSettings(DeckVisibilityStore.setting(sql, UUID.fromString(accountId)))
    }
}
