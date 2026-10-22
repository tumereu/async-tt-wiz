package org.asyncttwiz.storage

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.os.Looper
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.File

object AndroidCatalogStorage {
    fun directory(context: Context): File = File(context.applicationContext.noBackupFilesDir, "public-catalog")

    fun repository(context: Context): CatalogRepository = CatalogRepository(directory(context)) { file ->
        check(Looper.myLooper() != Looper.getMainLooper())
        val database = SQLiteDatabase.openDatabase(file.absolutePath, null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            DatabaseErrorHandler { db -> db.close(); throw SQLiteDatabaseCorruptException("Catalog unavailable") })
        AndroidSqliteDriver(RawAndroidDatabase(database))
    }
}
