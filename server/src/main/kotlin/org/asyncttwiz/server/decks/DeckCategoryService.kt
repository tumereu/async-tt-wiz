package org.asyncttwiz.server.decks

import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.generated.tables.DeckCategory.DECK_CATEGORY
import org.asyncttwiz.server.generated.tables.SavedDeck.SAVED_DECK
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/**
 * LOB-244..LOB-246 (owner 2026-11-19): an account's deck categories, a tree: created, renamed, moved and deleted from Manage
 * decks. Every change answers the whole list. Deleting a category moves its decks and subcategories up one level, into its
 * parent (the top level, uncategorised, for a top-level one).
 */
class DeckCategoryService(private val runtime: ServiceRuntime) {
    private val decks = DeckService(runtime)
    private val json = DeckJson.format
    private val log = org.slf4j.LoggerFactory.getLogger(DeckCategoryService::class.java)
    private fun error(status: Int, code: String, field: String? = null) =
        DeckService.Result(status, json.encodeToString(DeckError(code, field)))
    private fun list(sql: DSLContext, owner: UUID) = DeckService.Result(200, json.encodeToString(DeckCategoryList(all(sql, owner))))

    fun read(principal: AccountService.Principal): DeckService.Result = runtime.transaction { connection ->
        list(DSL.using(connection, SQLDialect.POSTGRES), UUID.fromString(principal.account.accountId))
    }

    fun create(principal: AccountService.Principal, input: CreateDeckCategory): DeckService.Result {
        if (!DeckValidation.uuid(input.requestId)) return error(400, "invalid_request", "requestId")
        if (!DeckCategories.name(input.name)) return error(400, "invalid_category", "name")
        return decks.business(principal, input.requestId, "createDeckCategory", DeckJson.canonical(input)) { sql, owner ->
            val parent = input.parent?.let { owned(sql, owner, it) ?: return@business error(400, "invalid_category", "parent") }
            if (sql.fetchCount(DECK_CATEGORY, DECK_CATEGORY.ACCOUNT_ID.eq(owner)) >= MAX_CATEGORIES) return@business error(400, "too_many_categories")
            val id = UUID.randomUUID()
            sql.insertInto(DECK_CATEGORY).set(DECK_CATEGORY.ID, id).set(DECK_CATEGORY.ACCOUNT_ID, owner)
                .set(DECK_CATEGORY.PARENT_ID, parent).set(DECK_CATEGORY.NAME, input.name).execute()
            log.info("Deck category created account={} category={} parent={}", owner, id, parent)
            list(sql, owner)
        }
    }

    fun update(principal: AccountService.Principal, id: String, input: UpdateDeckCategory): DeckService.Result {
        if (!DeckValidation.uuid(input.requestId)) return error(400, "invalid_request", "requestId")
        val name = input.name
        if (name == null && input.parent == null) return error(400, "invalid_category")
        if (name != null && !DeckCategories.name(name)) return error(400, "invalid_category", "name")
        return decks.business(principal, input.requestId, "updateDeckCategory", intent(id, json.encodeToJsonElement(UpdateDeckCategory.serializer(), input))) { sql, owner ->
            val category = owned(sql, owner, id) ?: return@business error(404, "not_found")
            val parent = input.parent?.let { choice -> choice.id?.let { owned(sql, owner, it) ?: return@business error(400, "invalid_category", "parent") } }
            // A category never moves into itself or one of its subcategories
            if (input.parent != null && !DeckCategories.canMove(all(sql, owner), category.toString(), parent?.toString()))
                return@business error(400, "invalid_move", "parent")
            if (name != null) sql.update(DECK_CATEGORY).set(DECK_CATEGORY.NAME, name).where(DECK_CATEGORY.ID.eq(category)).execute()
            if (input.parent != null) sql.update(DECK_CATEGORY).set(DECK_CATEGORY.PARENT_ID, parent).where(DECK_CATEGORY.ID.eq(category)).execute()
            log.info("Deck category changed account={} category={} renamed={} movedTo={}", owner, category, input.name != null,
                if (input.parent != null) parent ?: "top level" else "unchanged")
            list(sql, owner)
        }
    }

    fun delete(principal: AccountService.Principal, id: String, input: DeleteDeckCategory): DeckService.Result {
        if (!DeckValidation.uuid(input.requestId)) return error(400, "invalid_request", "requestId")
        return decks.business(principal, input.requestId, "deleteDeckCategory", intent(id, json.encodeToJsonElement(DeleteDeckCategory.serializer(), input))) { sql, owner ->
            val category = owned(sql, owner, id) ?: return@business error(404, "not_found")
            val parent = sql.select(DECK_CATEGORY.PARENT_ID).from(DECK_CATEGORY).where(DECK_CATEGORY.ID.eq(category)).fetchOne(DECK_CATEGORY.PARENT_ID)
            // LOB-246: its decks and subcategories move up one level
            val movedDecks = sql.update(SAVED_DECK).set(SAVED_DECK.CATEGORY_ID, parent).where(SAVED_DECK.CATEGORY_ID.eq(category))
                .and(SAVED_DECK.ACCOUNT_ID.eq(owner)).execute()
            val movedCategories = sql.update(DECK_CATEGORY).set(DECK_CATEGORY.PARENT_ID, parent).where(DECK_CATEGORY.PARENT_ID.eq(category))
                .and(DECK_CATEGORY.ACCOUNT_ID.eq(owner)).execute()
            sql.deleteFrom(DECK_CATEGORY).where(DECK_CATEGORY.ID.eq(category)).execute()
            log.info("Deck category deleted account={} category={}: {} decks and {} subcategories moved up into {}", owner, category,
                movedDecks, movedCategories, parent ?: "the top level")
            list(sql, owner)
        }
    }

    private fun intent(id: String, input: kotlinx.serialization.json.JsonElement) = DeckJson.canonicalElement(
        kotlinx.serialization.json.buildJsonObject { put("categoryId", kotlinx.serialization.json.JsonPrimitive(id)); put("input", input) })

    internal companion object {
        /** An HTTP resource bound, far beyond any collection's need. */
        const val MAX_CATEGORIES = 1000

        /** [owner]'s category [id], locked for the change; null when it isn't theirs (or no category). */
        fun owned(sql: DSLContext, owner: UUID, id: String): UUID? {
            if (!DeckValidation.uuid(id)) return null
            return sql.select(DECK_CATEGORY.ID).from(DECK_CATEGORY).where(DECK_CATEGORY.ID.eq(UUID.fromString(id)))
                .and(DECK_CATEGORY.ACCOUNT_ID.eq(owner)).forUpdate().fetchOne(DECK_CATEGORY.ID)
        }

        /** Every category of [owner]'s. */
        fun all(sql: DSLContext, owner: UUID): List<DeckCategory> = sql.selectFrom(DECK_CATEGORY)
            .where(DECK_CATEGORY.ACCOUNT_ID.eq(owner)).orderBy(DECK_CATEGORY.NAME, DECK_CATEGORY.ID).fetch()
            .map { DeckCategory(it.id.toString(), it.name, it.parentId?.toString()) }
    }
}
