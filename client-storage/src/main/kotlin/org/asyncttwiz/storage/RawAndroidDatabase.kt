package org.asyncttwiz.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteProgram
import android.database.sqlite.SQLiteStatement
import android.database.sqlite.SQLiteTransactionListener
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import java.util.Locale

/**
 * Public AndroidX interface bridge for SQLDelight's real AndroidSqliteDriver. AndroidX's own
 * framework wrapper is internal; using its open helper would create/version the file before
 * ClientStorage can validate it. This bridge owns no schema or corruption/retry policy.
 */
internal class RawAndroidDatabase(private val database: SQLiteDatabase) : SupportSQLiteDatabase {
    override fun compileStatement(sql: String): SupportSQLiteStatement = Statement(database.compileStatement(sql))
    override fun beginTransaction() = database.beginTransaction()
    override fun beginTransactionNonExclusive() = database.beginTransactionNonExclusive()
    override fun beginTransactionWithListener(transactionListener: SQLiteTransactionListener) =
        database.beginTransactionWithListener(transactionListener)
    override fun beginTransactionWithListenerNonExclusive(transactionListener: SQLiteTransactionListener) =
        database.beginTransactionWithListenerNonExclusive(transactionListener)
    override fun endTransaction() = database.endTransaction()
    override fun setTransactionSuccessful() = database.setTransactionSuccessful()
    override fun inTransaction(): Boolean = database.inTransaction()
    override val isDbLockedByCurrentThread: Boolean get() = database.isDbLockedByCurrentThread
    override fun yieldIfContendedSafely(): Boolean = database.yieldIfContendedSafely()
    override fun yieldIfContendedSafely(sleepAfterYieldDelayMillis: Long): Boolean =
        database.yieldIfContendedSafely(sleepAfterYieldDelayMillis)
    override var version: Int
        get() = database.version
        set(value) { database.version = value }
    override val maximumSize: Long get() = database.maximumSize
    override fun setMaximumSize(numBytes: Long): Long = database.setMaximumSize(numBytes)
    override var pageSize: Long
        get() = database.pageSize
        set(value) { database.pageSize = value }
    override fun query(query: String): Cursor = query(SimpleSQLiteQuery(query))
    override fun query(query: String, bindArgs: Array<out Any?>): Cursor = query(SimpleSQLiteQuery(query, bindArgs))
    override fun query(query: SupportSQLiteQuery): Cursor = query(query, null)
    override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor =
        database.rawQueryWithFactory({ _, cursorDriver, editTable, sqliteQuery ->
            query.bindTo(Program(sqliteQuery))
            SQLiteCursor(cursorDriver, editTable, sqliteQuery)
        }, query.sql, emptyArray(), "", cancellationSignal)
    override fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long =
        database.insertWithOnConflict(table, null, values, conflictAlgorithm)
    override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int =
        compileStatement("DELETE FROM $table" + whereClause?.let { " WHERE $it" }.orEmpty()).use {
            SimpleSQLiteQuery.bind(it, whereArgs)
            it.executeUpdateDelete()
        }
    override fun update(table: String, conflictAlgorithm: Int, values: ContentValues,
                        whereClause: String?, whereArgs: Array<out Any?>?): Int {
        require(values.size() > 0)
        val conflict = listOf("", " OR ROLLBACK", " OR ABORT", " OR FAIL", " OR IGNORE", " OR REPLACE")[conflictAlgorithm]
        val columns = values.keySet().toList()
        val sql = "UPDATE$conflict $table SET " + columns.joinToString(",") { "$it=?" } +
            whereClause?.let { " WHERE $it" }.orEmpty()
        return compileStatement(sql).use {
            SimpleSQLiteQuery.bind(it, (columns.map { key -> values[key] } + whereArgs.orEmpty()).toTypedArray())
            it.executeUpdateDelete()
        }
    }
    override fun execSQL(sql: String) = database.execSQL(sql)
    override fun execSQL(sql: String, bindArgs: Array<out Any?>) = database.execSQL(sql, bindArgs)
    override val isReadOnly: Boolean get() = database.isReadOnly
    override val isOpen: Boolean get() = database.isOpen
    override fun needUpgrade(newVersion: Int): Boolean = database.needUpgrade(newVersion)
    override val path: String get() = database.path
    override fun setLocale(locale: Locale) = database.setLocale(locale)
    override fun setMaxSqlCacheSize(cacheSize: Int) = database.setMaxSqlCacheSize(cacheSize)
    override fun setForeignKeyConstraintsEnabled(enabled: Boolean) = database.setForeignKeyConstraintsEnabled(enabled)
    override fun enableWriteAheadLogging(): Boolean = database.enableWriteAheadLogging()
    override fun disableWriteAheadLogging() = database.disableWriteAheadLogging()
    override val isWriteAheadLoggingEnabled: Boolean get() = database.isWriteAheadLoggingEnabled
    override val attachedDbs: List<android.util.Pair<String, String>>? get() = database.attachedDbs
    override val isDatabaseIntegrityOk: Boolean get() = database.isDatabaseIntegrityOk
    override fun close() = database.close()

    private open class Program(private val program: SQLiteProgram) : SupportSQLiteProgram {
        override fun bindNull(index: Int) = program.bindNull(index)
        override fun bindLong(index: Int, value: Long) = program.bindLong(index, value)
        override fun bindDouble(index: Int, value: Double) = program.bindDouble(index, value)
        override fun bindString(index: Int, value: String) = program.bindString(index, value)
        override fun bindBlob(index: Int, value: ByteArray) = program.bindBlob(index, value)
        override fun clearBindings() = program.clearBindings()
        override fun close() = program.close()
    }

    private class Statement(private val statement: SQLiteStatement) : Program(statement), SupportSQLiteStatement {
        override fun execute() = statement.execute()
        override fun executeUpdateDelete(): Int = statement.executeUpdateDelete()
        override fun executeInsert(): Long = statement.executeInsert()
        override fun simpleQueryForLong(): Long = statement.simpleQueryForLong()
        override fun simpleQueryForString(): String? = statement.simpleQueryForString()
    }
}
