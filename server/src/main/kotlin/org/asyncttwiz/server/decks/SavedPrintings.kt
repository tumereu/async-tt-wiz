package org.asyncttwiz.server.decks

import java.util.UUID
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.catalog.ServerCatalog
import org.asyncttwiz.server.generated.tables.CubeEntry.CUBE_ENTRY
import org.asyncttwiz.server.generated.tables.DeckEntry.DECK_ENTRY
import org.asyncttwiz.server.generated.tables.SavedCube.SAVED_CUBE
import org.asyncttwiz.server.generated.tables.SavedDeck.SAVED_DECK
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/**
 * Owner 2026-11-10 (card data from the server's catalog, step 4): the saved decks' and cubes' cards whose printing the
 * current card catalog doesn't have are found again by their name ([ServerCatalog.printingNamed]), else kept as cards
 * without a printing (LOB-115 keeps those out of games). That is the cards V80 found no printing for, and after a catalog
 * pin move any printing the new catalog lacks. Runs once the catalog has loaded at every start: idempotent, and it rewrites
 * only the lists that hold such a card. Logged with its counts; no card text.
 */
object SavedPrintings {
    /** [byId]: rows whose printing the catalog has; [byName]: rows found again by name now; [unresolved]: rows left
     * without a printing; [lists]: decks and cubes rewritten. */
    data class Outcome(val byId: Int, val byName: Int, val unresolved: Int, val lists: Int)

    private data class Row(val section: String, val printing: String?, val name: String, val quantity: Int)

    private fun log(message: String) = println("${java.time.Instant.now()} Saved printings: $message")

    fun resolve(runtime: ServiceRuntime, catalog: ServerCatalog): Outcome {
        val started = System.nanoTime()
        fun known(printing: String?) = printing != null && catalog.catalog.printing[printing] != null
        val (missing, byId) = runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val printings = sql.select(DECK_ENTRY.PRINTING_ID).from(DECK_ENTRY).where(DECK_ENTRY.PRINTING_ID.isNotNull)
                .union(sql.select(CUBE_ENTRY.PRINTING_ID).from(CUBE_ENTRY).where(CUBE_ENTRY.PRINTING_ID.isNotNull)).fetch().map { it.value1()!! }
            val missing = printings.filterNot(::known).toSet()
            val rows = sql.fetchCount(DECK_ENTRY, DECK_ENTRY.PRINTING_ID.isNotNull.and(DECK_ENTRY.PRINTING_ID.notIn(missing))) +
                sql.fetchCount(CUBE_ENTRY, CUBE_ENTRY.PRINTING_ID.isNotNull.and(CUBE_ENTRY.PRINTING_ID.notIn(missing)))
            missing to rows
        }
        val decks = runtime.transaction { connection -> DSL.using(connection, SQLDialect.POSTGRES)
            .selectDistinct(DECK_ENTRY.DECK_ID).from(DECK_ENTRY)
            .where(DECK_ENTRY.PRINTING_ID.isNull.or(DECK_ENTRY.PRINTING_ID.`in`(missing))).fetch(DECK_ENTRY.DECK_ID) }
        val cubes = runtime.transaction { connection -> DSL.using(connection, SQLDialect.POSTGRES)
            .selectDistinct(CUBE_ENTRY.CUBE_ID).from(CUBE_ENTRY)
            .where(CUBE_ENTRY.PRINTING_ID.isNull.or(CUBE_ENTRY.PRINTING_ID.`in`(missing))).fetch(CUBE_ENTRY.CUBE_ID) }
        var byName = 0; var unresolved = 0; var lists = 0
        /** [rows] with each card the catalog lacks found again by name or left without a printing, the same card in a
         * section made one (the first keeps the copies); and how the printings changed. */
        fun resolved(rows: List<Row>): Pair<List<Row>, Map<String, String?>> {
            val changed = HashMap<String, String?>()
            val merged = LinkedHashMap<Pair<String, String>, Row>()
            for (row in rows) {
                val next = if (known(row.printing)) row else {
                    val found = catalog.printingNamed(row.name)
                    if (found != null) byName++ else unresolved++
                    row.printing?.let { changed[it] = found }
                    row.copy(printing = found)
                }
                val key = next.section to (next.printing ?: "name:" + next.name)
                merged[key] = merged[key]?.let { it.copy(quantity = it.quantity + next.quantity) } ?: next
            }
            return merged.values.toList() to changed
        }
        for (deck in decks) runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val list = sql.selectFrom(SAVED_DECK).where(SAVED_DECK.ID.eq(deck)).forUpdate().fetchOne() ?: return@transaction
            val rows = sql.selectFrom(DECK_ENTRY).where(DECK_ENTRY.DECK_ID.eq(deck)).orderBy(DECK_ENTRY.POSITION).fetch()
                .map { Row(it.section!!, it.printingId, it.name!!, it.quantity!!) }
            val (next, changed) = resolved(rows)
            if (next == rows) return@transaction
            sql.deleteFrom(DECK_ENTRY).where(DECK_ENTRY.DECK_ID.eq(deck)).execute()
            var insert = sql.insertInto(DECK_ENTRY, DECK_ENTRY.DECK_ID, DECK_ENTRY.ACCOUNT_ID, DECK_ENTRY.POSITION, DECK_ENTRY.SECTION,
                DECK_ENTRY.PRINTING_ID, DECK_ENTRY.NAME, DECK_ENTRY.QUANTITY)
            next.forEachIndexed { index, row -> insert = insert.values(deck, list.accountId, index, row.section, row.printing, row.name, row.quantity) }
            insert.execute()
            val cover = list.coverPrinting?.let { if (it in changed) changed[it] else it }
            sql.update(SAVED_DECK).set(SAVED_DECK.COVER_PRINTING, cover).set(SAVED_DECK.REVISION, SAVED_DECK.REVISION.plus(1L))
                .where(SAVED_DECK.ID.eq(deck)).execute()
            lists++
        }
        for (cube in cubes) runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val list = sql.selectFrom(SAVED_CUBE).where(SAVED_CUBE.ID.eq(cube)).forUpdate().fetchOne() ?: return@transaction
            val rows = sql.selectFrom(CUBE_ENTRY).where(CUBE_ENTRY.CUBE_ID.eq(cube)).orderBy(CUBE_ENTRY.POSITION).fetch()
                .map { Row(it.section!!, it.printingId, it.name!!, it.quantity!!) }
            val (next) = resolved(rows)
            if (next == rows) return@transaction
            sql.deleteFrom(CUBE_ENTRY).where(CUBE_ENTRY.CUBE_ID.eq(cube)).execute()
            var insert = sql.insertInto(CUBE_ENTRY, CUBE_ENTRY.CUBE_ID, CUBE_ENTRY.ACCOUNT_ID, CUBE_ENTRY.POSITION, CUBE_ENTRY.SECTION,
                CUBE_ENTRY.PRINTING_ID, CUBE_ENTRY.NAME, CUBE_ENTRY.QUANTITY)
            next.forEachIndexed { index, row -> insert = insert.values(cube, list.accountId, index, row.section, row.printing, row.name, row.quantity) }
            insert.execute()
            sql.update(SAVED_CUBE).set(SAVED_CUBE.REVISION, SAVED_CUBE.REVISION.plus(1L)).set(SAVED_CUBE.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(SAVED_CUBE.ID.eq(cube)).execute()
            lists++
        }
        val outcome = Outcome(byId, byName, unresolved, lists)
        log("catalog ${catalog.version.checksum}: ${outcome.byId} saved cards by printing, ${outcome.byName} found again by name, " +
            "${outcome.unresolved} without a printing (${missing.size} printings the catalog lacks), ${outcome.lists} lists rewritten " +
            "(of ${decks.size} decks and ${cubes.size} cubes looked at), in ${(System.nanoTime() - started) / 1_000_000} ms")
        return outcome
    }

    /** Runs [resolve] once the catalog has loaded, logging (not throwing) a failure: the service runs on regardless. */
    fun afterLoad(runtime: ServiceRuntime, catalog: ServerCatalog) {
        try { resolve(runtime, catalog) } catch (failure: Exception) {
            log("failed: ${failure::class.java.simpleName}: ${failure.message}")
        }
    }
}
