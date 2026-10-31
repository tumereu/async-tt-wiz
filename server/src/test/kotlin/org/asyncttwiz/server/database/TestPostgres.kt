package org.asyncttwiz.server.database

import java.sql.Connection

/** What a test sees of its PostgreSQL database, whichever way it was provisioned. */
interface TestPostgres : AutoCloseable {
    val settings: DatabaseSettings
    fun connection(): Connection
}
