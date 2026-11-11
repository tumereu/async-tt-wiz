package org.asyncttwiz.server.accounts

import java.util.UUID
import org.asyncttwiz.protocol.AccountSettings
import org.asyncttwiz.protocol.GameWire
import org.asyncttwiz.protocol.MoveSound
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.game.DeckVisibilityStore
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/** Server-side account preferences, shared by all of the account's devices. */
class AccountSettingsService(private val runtime: ServiceRuntime) {
    fun read(accountId: String): AccountSettings = runtime.transaction { connection ->
        read(DSL.using(connection, SQLDialect.POSTGRES), UUID.fromString(accountId))
    }

    fun set(accountId: String, settings: AccountSettings): AccountSettings = runtime.transaction { connection ->
        val sql = DSL.using(connection, SQLDialect.POSTGRES)
        val account = UUID.fromString(accountId)
        DeckVisibilityStore.save(sql, account, settings.deckVisibility)
        PlayDefaultsStore.save(sql, account, settings.playDefaults)
        MoveSoundStore.save(sql, account, settings.moveSound)
        read(sql, account)
    }

    private fun read(sql: DSLContext, account: UUID) =
        AccountSettings(DeckVisibilityStore.setting(sql, account), PlayDefaultsStore.read(sql, account), MoveSoundStore.read(sql, account))
}

/** The account's "Your move" sound (TRN-075, LOB-146), by its id; null in the database: the default. */
internal object MoveSoundStore {
    fun read(sql: DSLContext, account: UUID): MoveSound =
        sql.select(ACCOUNT.MOVE_SOUND).from(ACCOUNT).where(ACCOUNT.ID.eq(account)).fetchOne(ACCOUNT.MOVE_SOUND)
            ?.let(MoveSound::of) ?: MoveSound.DEFAULT

    fun save(sql: DSLContext, account: UUID, sound: MoveSound) {
        sql.update(ACCOUNT).set(ACCOUNT.MOVE_SOUND, sound.id.takeIf { sound != MoveSound.DEFAULT }).where(ACCOUNT.ID.eq(account)).execute()
    }
}

/** The account's defaults for new games on engine state (LOB-113, I-D56 §7): given to their seat when a play starts. */
internal object PlayDefaultsStore {
    fun read(sql: DSLContext, account: UUID): PlaySettings =
        sql.select(ACCOUNT.PLAY_DEFAULTS).from(ACCOUNT).where(ACCOUNT.ID.eq(account)).fetchOne(ACCOUNT.PLAY_DEFAULTS)
            ?.let { runCatching { GameWire.json.decodeFromString(PlaySettings.serializer(), it) }.getOrNull() } ?: PlaySettings.NEW_GAMES

    fun save(sql: DSLContext, account: UUID, settings: PlaySettings) {
        sql.update(ACCOUNT).set(ACCOUNT.PLAY_DEFAULTS, if (settings == PlaySettings.NEW_GAMES) null
            else GameWire.json.encodeToString(PlaySettings.serializer(), settings)).where(ACCOUNT.ID.eq(account)).execute()
    }
}
