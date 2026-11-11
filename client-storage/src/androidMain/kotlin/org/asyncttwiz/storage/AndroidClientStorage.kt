package org.asyncttwiz.storage

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.os.Looper
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.File

/** Raw Android open: SQLiteOpenHelper's automatic versions/destructive recovery are not used. */
object AndroidClientStorage {
    fun databaseFile(context: Context): File =
        File(context.applicationContext.noBackupFilesDir, "client-storage/replica.db")

    /** Blocking. Only the explicit repository boundary may create/migrate/publish user_version. */
    fun open(file: File): StorageOpenResult {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Storage must open off the UI thread" }
        return ClientStorage.open {
            val parent = requireNotNull(file.parentFile)
            check(parent.isDirectory || parent.mkdirs())
            val database = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY or
                    SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                preserveCorruption,
            )
            try {
                // This configures Android's connection pool, not just one connection's PRAGMA.
                database.setForeignKeyConstraintsEnabled(true)
                AndroidSqliteDriver(RawAndroidDatabase(database))
            } catch (error: Exception) {
                database.close()
                throw error
            }
        }
    }

    private val preserveCorruption = DatabaseErrorHandler { database ->
        // Android's default handler deletes corrupt files (and SQLiteOpenHelper may retry).
        // Stop instead. No private path, SQL, payload or original exception leaves our API.
        database.close()
        throw SQLiteDatabaseCorruptException("Storage cannot be opened")
    }
}
