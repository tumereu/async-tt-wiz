package org.asyncttwiz.storage

import app.cash.sqldelight.db.SqlCursor

// A NOT NULL column's value. The schema guarantees it, so a null here means a broken database file: refused at this
// boundary, where the data enters the app, as the database's own errors are.

internal fun SqlCursor.text(index: Int): String = getString(index) ?: throw IllegalStateException("Column $index is null")

internal fun SqlCursor.number(index: Int): Long = getLong(index) ?: throw IllegalStateException("Column $index is null")
