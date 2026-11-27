package org.asyncttwiz.server.accounts

import java.util.UUID
import org.asyncttwiz.protocol.NotificationSettings
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** LOB-276 (owner, 2026-11-27): Chat notifications are off unless the player switches them on, for accounts that never did too. */
class ChatNotificationsDefaultTest {
    private fun sql(db: TestPostgres, statement: String) = db.connection().use { it.createStatement().execute(statement) }
    private fun stored(db: TestPostgres, account: String): String? = db.connection().use { c ->
        c.createStatement().executeQuery("SELECT notification_settings FROM account WHERE id = '$account'").use { it.next(); it.getString(1) } }

    // LOB-276 (owner, 2026-11-27): V101: stored switches, written whole when any kind was switched, held Chat on only as the old
    // default: off now, the others as they were; switches now equal to the default are stored as none; accounts without any follow
    // the new default (Chat off) by themselves
    @Test fun `stored switches turn Chat off and keep the rest`() {
        FreshDatabase(migrated = false).start().use { db ->
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("100").load().migrate()
            val (draftOff, invitationsOff, chatOff, untouched) = List(4) { UUID.randomUUID().toString() }
            fun account(id: String, settings: String?) = sql(db, "INSERT INTO account(id,username,username_key,email_delivery,email_key," +
                "password_phc,notification_settings) VALUES ('$id','$id','$id','$id@example.invalid','$id@example.invalid','retained'," +
                (settings?.let { "'$it'" } ?: "NULL") + ")")
            account(draftOff, """{"yourMove":true,"reminders":true,"chat":true,"invitations":true,"draft":false,"gameOver":true}""")
            account(invitationsOff, """{"yourMove":true,"reminders":true,"chat":true,"invitations":false,"draft":true,"gameOver":true}""")
            account(chatOff, """{"yourMove":true,"reminders":true,"chat":false,"invitations":true,"draft":true,"gameOver":true}""")
            account(untouched, null)
            FoundationMigrations.migrate(db.settings)
            assertEquals(NotificationSettings(draft = false), NotificationSettingsStore.decode(stored(db, draftOff)))
            assertEquals(NotificationSettings(invitations = false), NotificationSettingsStore.decode(stored(db, invitationsOff)))
            assertNull(stored(db, chatOff), "the new default: stored as none")
            assertNull(stored(db, untouched))
            assertFalse(NotificationSettingsStore.decode(stored(db, untouched)).chat)
        }
    }
}
