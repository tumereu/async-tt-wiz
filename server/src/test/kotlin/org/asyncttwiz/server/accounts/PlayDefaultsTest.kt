package org.asyncttwiz.server.accounts

import java.util.UUID
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.asyncttwiz.protocol.GameWire
import org.asyncttwiz.server.history.HistoryWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** LOB-113, HIS-096 (owner, 2026-11-09): "No mana" is Assume's default in new games, for every account; games in play keep theirs. */
class PlayDefaultsTest {
    private fun json(settings: PlaySettings) = GameWire.json.encodeToString(PlaySettings.serializer(), settings)

    @Test fun `V73 moves every account's saved Assume to No mana and leaves the games in play theirs`() = HistoryWorld.run { w ->
        val saved = w.account("DefaultsSaved"); val none = w.account("DefaultsNone"); val other = w.account("DefaultsOther")
        val m = w.Match(saved, other, "Defaults Spark")
        // The game in play started on their old saved settings, Bob's in Control
        fun save(actor: AccountService.Principal, settings: PlaySettings?) = w.execute("UPDATE account SET play_defaults = ? WHERE id = ?",
            settings?.let(::json), UUID.fromString(actor.account.accountId))
        save(saved, PlaySettings(mode = ResponseMode.Normal, assume = AssumeResolves.Always, autotap = false))
        save(other, PlaySettings(assume = AssumeResolves.Never))
        save(none, null)
        m.start()
        fun seat(actor: AccountService.Principal) = w.sql { sql -> GameWire.json.decodeFromString(PlaySettings.serializer(),
            sql.fetchValue("SELECT settings FROM engine_seat WHERE account_id = ?", UUID.fromString(actor.account.accountId)) as String) }
        fun defaults(actor: AccountService.Principal) = w.sql { PlayDefaultsStore.read(it, UUID.fromString(actor.account.accountId)) }
        assertEquals(AssumeResolves.Always, seat(saved).assume)

        w.execute(checkNotNull(javaClass.getResourceAsStream("/db/migration/V73__assume_default_no_mana.sql")).reader().readText())

        assertEquals(PlaySettings(mode = ResponseMode.Normal, assume = AssumeResolves.WhenNoMana, autotap = false), defaults(saved), "only Assume moves")
        assertEquals(AssumeResolves.WhenNoMana, defaults(other).assume)
        assertEquals(PlaySettings.NEW_GAMES, defaults(none), "none saved: the new default")
        assertEquals(AssumeResolves.Always, seat(saved).assume, "the game in play keeps hers")
        assertEquals(AssumeResolves.Never, seat(other).assume)
        // A choice made afterwards stays what it is, Normal too
        w.sql { PlayDefaultsStore.save(it, UUID.fromString(none.account.accountId), PlaySettings(assume = AssumeResolves.Normal)) }
        assertEquals(AssumeResolves.Normal, defaults(none).assume)
    }
}
