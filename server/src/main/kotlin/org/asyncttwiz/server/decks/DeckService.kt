package org.asyncttwiz.server.decks

import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.generated.tables.AccountDefinition.ACCOUNT_DEFINITION
import org.asyncttwiz.server.generated.tables.AccountRequest.ACCOUNT_REQUEST
import org.asyncttwiz.server.generated.tables.DeckEntry.DECK_ENTRY
import org.asyncttwiz.server.generated.tables.Definition.DEFINITION
import org.asyncttwiz.server.generated.tables.RequestIdentity.REQUEST_IDENTITY
import org.asyncttwiz.server.generated.tables.SavedDeck.SAVED_DECK
import org.asyncttwiz.server.generated.tables.records.SavedDeckRecord
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/** Immutable snapshots and account data, not a provider catalog or a local-authority deck store. */
class DeckService(private val runtime: ServiceRuntime) {
    data class Result(val status: Int, val body: String)
    private val json = DeckJson.format
    private fun error(status: Int, code: String, field: String? = null) =
        Result(status, json.encodeToString(DeckError(code, field)))

    fun submit(principal: AccountService.Principal, input: SubmitDefinitions): Result {
        if (!DeckValidation.uuid(input.requestId)) return error(400, "invalid_request", "requestId")
        // Definition set order is not semantic. Normalize link sets before hashing/comparing intent.
        val normalized = input.copy(definitions = input.definitions.map {
            it.copy(definition = DeckJson.normalized(it.definition))
        }.sortedBy { it.digest })
        val valid = normalized.definitions.isNotEmpty() &&
            normalized.definitions.map { it.digest }.distinct().size == normalized.definitions.size &&
            normalized.definitions.all { DeckValidation.digest(it.digest) && DeckValidation.definition(it.definition) &&
                DeckJson.digest(it.definition) == it.digest }
        return business(principal, input.requestId, "definitions", DeckJson.canonical(normalized)) { sql, owner ->
            if (!valid) return@business error(400, "invalid_definition")
            for (submitted in normalized.definitions) {
                sql.insertInto(DEFINITION).set(DEFINITION.DIGEST, submitted.digest)
                    .set(DEFINITION.SCHEMA_VERSION, submitted.definition.schemaVersion)
                    .set(DEFINITION.PAYLOAD, DeckJson.canonical(submitted.definition)).onConflictDoNothing().execute()
                sql.insertInto(ACCOUNT_DEFINITION).set(ACCOUNT_DEFINITION.ACCOUNT_ID, owner)
                    .set(ACCOUNT_DEFINITION.DIGEST, submitted.digest).onConflictDoNothing().execute()
            }
            Result(200, json.encodeToString(DefinitionsAccepted(normalized.definitions.map { it.digest })))
        }
    }

    fun create(principal: AccountService.Principal, input: CreateDeck): Result {
        if (!DeckValidation.uuid(input.requestId)) return error(400, "invalid_request", "requestId")
        val valid = DeckValidation.create(input)
        return business(principal, input.requestId, "createDeck", DeckJson.canonical(input)) { sql, owner ->
            if (!valid) return@business error(400, "invalid_deck")
            val needed = input.entries.map { it.definition }.toSet()
            val owned = sql.select(ACCOUNT_DEFINITION.DIGEST).from(ACCOUNT_DEFINITION)
                .where(ACCOUNT_DEFINITION.ACCOUNT_ID.eq(owner)).and(ACCOUNT_DEFINITION.DIGEST.`in`(needed))
                .fetchSet(ACCOUNT_DEFINITION.DIGEST)
            if (owned != needed) return@business error(400, "definitions_unavailable")
            val id = UUID.randomUUID()
            val row = sql.insertInto(SAVED_DECK).set(SAVED_DECK.ID, id).set(SAVED_DECK.ACCOUNT_ID, owner)
                .set(SAVED_DECK.NAME, input.name).returning().fetchSingle()
            input.entries.forEachIndexed { index, entry ->
                sql.insertInto(DECK_ENTRY).set(DECK_ENTRY.DECK_ID, id).set(DECK_ENTRY.ACCOUNT_ID, owner)
                    .set(DECK_ENTRY.POSITION, index).set(DECK_ENTRY.SECTION, entry.section.name)
                    .set(DECK_ENTRY.DEFINITION_DIGEST, entry.definition).set(DECK_ENTRY.QUANTITY, entry.quantity).execute()
            }
            Result(201, json.encodeToString(SavedDeck(summary(sql, row, input.entries), input.entries)))
        }
    }

    fun update(principal: AccountService.Principal, id: String, input: UpdateDeck): Result {
        if (!DeckValidation.uuid(input.requestId)) return error(400, "invalid_request", "requestId")
        // Preserve canonical intent for pre-I08 metadata retries: the newly optional null field
        // was absent from their typed schema, not a change to the original request.
        val encoded = json.encodeToJsonElement(UpdateDeck.serializer(), input).jsonObject
        val canonical = if (input.contents == null) JsonObject(encoded.filterKeys { it != "contents" }) else encoded
        return business(principal, input.requestId, "updateDeck", intent(id, canonical)) { sql, owner ->
            val row = owned(sql, owner, id) ?: return@business error(404, "not_found")
            // Draft decks follow their pod submission; "Save as deck" makes an editable copy.
            if (row.draftPodId != null) return@business error(409, "draft_deck")
            val requestedName = input.name
            val requestedCover = input.cover
            val contents = input.contents
            if ((requestedName == null && requestedCover == null && contents == null) ||
                (requestedName != null && (!DeckValidation.text(requestedName) || requestedName.isBlank())))
                return@business error(400, "invalid_deck", "name")
            val previous = entries(sql, row.id!!)
            val next = if (contents == null) previous else {
                if (!DeckValidation.entries(contents.entries)) return@business error(400, "invalid_deck")
                val needed = contents.entries.map { it.definition }.toSet()
                val owned = sql.select(ACCOUNT_DEFINITION.DIGEST).from(ACCOUNT_DEFINITION)
                    .where(ACCOUNT_DEFINITION.ACCOUNT_ID.eq(owner)).and(ACCOUNT_DEFINITION.DIGEST.`in`(needed))
                    .fetchSet(ACCOUNT_DEFINITION.DIGEST)
                if (owned != needed) return@business error(400, "definitions_unavailable")
                if (contents.mode == DeckImportMode.OVERWRITE) contents.entries else {
                    // Content identity, never a guessed name/external-ID match. Preserve existing order.
                    val merged = linkedMapOf<Pair<SavedDeckSection, String>, SavedDeckEntry>()
                    for (entry in previous + contents.entries) {
                        val key = entry.section to entry.definition
                        val quantity = (merged[key]?.quantity?.toLong() ?: 0L) + entry.quantity
                        if (quantity > Int.MAX_VALUE) return@business error(400, "quantity_overflow")
                        merged[key] = entry.copy(quantity = quantity.toInt())
                    }
                    merged.values.toList()
                }
            }
            if (contents != null && !DeckValidation.entries(next)) return@business error(400, "invalid_deck")
            if (requestedCover != null && (!DeckValidation.digest(requestedCover) || next.none { it.definition == requestedCover }))
                return@business error(400, "invalid_cover", "cover")
            val name = input.name ?: row.name!!
            val cover = (input.cover ?: row.coverDigest)?.takeIf { digest -> next.any { it.definition == digest } }
            if (next != previous) {
                sql.deleteFrom(DECK_ENTRY).where(DECK_ENTRY.DECK_ID.eq(row.id)).execute()
                next.forEachIndexed { index, entry ->
                    sql.insertInto(DECK_ENTRY).set(DECK_ENTRY.DECK_ID, row.id).set(DECK_ENTRY.ACCOUNT_ID, owner)
                        .set(DECK_ENTRY.POSITION, index).set(DECK_ENTRY.SECTION, entry.section.name)
                        .set(DECK_ENTRY.DEFINITION_DIGEST, entry.definition).set(DECK_ENTRY.QUANTITY, entry.quantity).execute()
                }
            }
            if (name != row.name || cover != row.coverDigest || next != previous) {
                sql.update(SAVED_DECK).set(SAVED_DECK.NAME, name).set(SAVED_DECK.COVER_DIGEST, cover)
                    .set(SAVED_DECK.REVISION, SAVED_DECK.REVISION.plus(1L)).where(SAVED_DECK.ID.eq(row.id)).execute()
                row.name = name; row.coverDigest = cover; row.revision = row.revision!! + 1
            }
            Result(200, json.encodeToString(SavedDeck(summary(sql, row, next), next)))
        }
    }

    fun delete(principal: AccountService.Principal, id: String, input: DeleteDeck): Result {
        if (!DeckValidation.uuid(input.requestId)) return error(400, "invalid_request", "requestId")
        return business(principal, input.requestId, "deleteDeck", intent(id, json.encodeToJsonElement(DeleteDeck.serializer(), input))) { sql, owner ->
            val row = owned(sql, owner, id) ?: return@business error(404, "not_found")
            sql.deleteFrom(SAVED_DECK).where(SAVED_DECK.ID.eq(row.id)).execute()
            Result(200, json.encodeToString(DeckDeleted(id)))
        }
    }

    /** "Save as deck": an independent ordinary saved deck with the same name, contents and cover. */
    fun copy(principal: AccountService.Principal, id: String, input: CopyDeck): Result {
        if (!DeckValidation.uuid(input.requestId)) return error(400, "invalid_request", "requestId")
        return business(principal, input.requestId, "copyDeck", intent(id, json.encodeToJsonElement(CopyDeck.serializer(), input))) { sql, owner ->
            val source = owned(sql, owner, id) ?: return@business error(404, "not_found")
            val entries = entries(sql, source.id!!)
            val copy = UUID.randomUUID()
            val row = sql.insertInto(SAVED_DECK).set(SAVED_DECK.ID, copy).set(SAVED_DECK.ACCOUNT_ID, owner)
                .set(SAVED_DECK.NAME, source.name).set(SAVED_DECK.COVER_DIGEST, source.coverDigest).returning().fetchSingle()
            entries.forEachIndexed { index, entry ->
                sql.insertInto(DECK_ENTRY).set(DECK_ENTRY.DECK_ID, copy).set(DECK_ENTRY.ACCOUNT_ID, owner)
                    .set(DECK_ENTRY.POSITION, index).set(DECK_ENTRY.SECTION, entry.section.name)
                    .set(DECK_ENTRY.DEFINITION_DIGEST, entry.definition).set(DECK_ENTRY.QUANTITY, entry.quantity).execute()
            }
            Result(201, json.encodeToString(SavedDeck(summary(sql, row, entries), entries)))
        }
    }

    private fun intent(id: String, input: kotlinx.serialization.json.JsonElement) = DeckJson.canonicalElement(
        kotlinx.serialization.json.buildJsonObject { put("deckId", kotlinx.serialization.json.JsonPrimitive(id)); put("input", input) })

    private fun owned(sql: DSLContext, owner: UUID, id: String): SavedDeckRecord? {
        if (!DeckValidation.uuid(id)) return null
        return sql.selectFrom(SAVED_DECK).where(SAVED_DECK.ID.eq(UUID.fromString(id)))
            .and(SAVED_DECK.ACCOUNT_ID.eq(owner)).forUpdate().fetchOne()
    }

    private fun entries(sql: DSLContext, id: UUID) = sql.selectFrom(DECK_ENTRY).where(DECK_ENTRY.DECK_ID.eq(id))
        .orderBy(DECK_ENTRY.POSITION).fetch().map {
            SavedDeckEntry(SavedDeckSection.valueOf(it.section!!), it.definitionDigest!!, it.quantity!!)
        }

    /** Called inside the game transaction; a concurrent edit/delete cannot change this snapshot. */
    internal fun selectedSource(sql: DSLContext, owner: UUID, id: String): SavedDeck? {
        val row = sql.selectFrom(SAVED_DECK).where(SAVED_DECK.ID.eq(UUID.fromString(id)))
            .and(SAVED_DECK.ACCOUNT_ID.eq(owner)).forShare().fetchOne() ?: return null
        val entries = entries(sql, row.id!!)
        return SavedDeck(summary(sql, row, entries), entries)
    }

    private fun summary(sql: DSLContext, row: SavedDeckRecord, entries: List<SavedDeckEntry> = entries(sql, row.id!!)): DeckSummary {
        val cover = (row.coverDigest?.takeIf { digest -> entries.any { it.definition == digest } } ?: entries.firstOrNull()?.definition)?.let { digest ->
            val payload = sql.select(DEFINITION.PAYLOAD).from(DEFINITION).where(DEFINITION.DIGEST.eq(digest)).fetchSingle(DEFINITION.PAYLOAD)!!
            // A cover card whose stored definition can't be read leaves the deck without a cover (SYS-052).
            StoredData.orSkip("card definition", digest) {
                val definition = json.decodeFromString<CardDefinition>(payload)
                val face = definition.faces.first()
                DeckCover(digest, definition.name, face.scryfallId, face.imageUri)
            }
        }
        return DeckSummary(row.id.toString(), row.name!!, row.revision.toString(), row.createdAt!!.toInstant().toString(),
            entries.filter { it.section == SavedDeckSection.MAINBOARD }.sumOf { it.quantity.toLong() },
            entries.filter { it.section == SavedDeckSection.SIDEBOARD }.sumOf { it.quantity.toLong() }, cover,
            row.draftPodId?.let { DraftDeckOrigin(it.toString(), row.draftPodName!!) })
    }

    /** INSERT uniqueness waits for concurrent identical IDs, then reads the committed original result.
     * No thrown business exceptions: the ownership boundary correctly treats SQL failures as unavailable.
     */
    private fun business(principal: AccountService.Principal, requestId: String, kind: String, canonical: String,
                         action: (DSLContext, UUID) -> Result): Result {
        val owner = UUID.fromString(principal.account.accountId)
        val id = UUID.fromString(requestId)
        val intent = DeckJson.intent(kind, canonical)
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val inserted = sql.insertInto(REQUEST_IDENTITY).set(REQUEST_IDENTITY.ID, id)
                .set(REQUEST_IDENTITY.ACCOUNT_ID, owner).set(REQUEST_IDENTITY.KIND, kind)
                .set(REQUEST_IDENTITY.SCOPE, "account").set(REQUEST_IDENTITY.INTENT_DIGEST, intent)
                .onConflictDoNothing().execute() == 1
            if (!inserted) {
                val original = sql.selectFrom(REQUEST_IDENTITY).where(REQUEST_IDENTITY.ID.eq(id)).fetchSingle()
                if (original.accountId != owner || original.kind != kind || original.scope != "account" || original.intentDigest != intent)
                    return@transaction error(409, "request_conflict")
                val outcome = sql.selectFrom(ACCOUNT_REQUEST).where(ACCOUNT_REQUEST.REQUEST_ID.eq(id)).fetchSingle()
                return@transaction Result(outcome.status!!, outcome.response!!)
            }
            val outcome = action(sql, owner)
            sql.insertInto(ACCOUNT_REQUEST).set(ACCOUNT_REQUEST.REQUEST_ID, id)
                .set(ACCOUNT_REQUEST.STATUS, outcome.status).set(ACCOUNT_REQUEST.RESPONSE, outcome.body).execute()
            outcome
        }
    }

    /** Ordinary decks, paged by id; or, with [drafts], the draft decks newest first (bounded by [DraftDecks.LIMIT]). */
    fun list(principal: AccountService.Principal, after: String?, drafts: Boolean = false): Result {
        if (after != null && (drafts || !DeckValidation.uuid(after))) return error(400, "invalid_request", "after")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val owner = UUID.fromString(principal.account.accountId)
            if (drafts) {
                val rows = sql.selectFrom(SAVED_DECK).where(SAVED_DECK.ACCOUNT_ID.eq(owner)).and(SAVED_DECK.DRAFT_POD_ID.isNotNull)
                    .orderBy(SAVED_DECK.CREATED_AT.desc(), SAVED_DECK.ID.desc()).limit(100).forShare().fetch()
                return@transaction Result(200, json.encodeToString(DeckList(rows.map { summary(sql, it) })))
            }
            val rows = sql.selectFrom(SAVED_DECK).where(SAVED_DECK.ACCOUNT_ID.eq(owner)).and(SAVED_DECK.DRAFT_POD_ID.isNull)
                .and(if (after == null) DSL.trueCondition() else SAVED_DECK.ID.gt(UUID.fromString(after)))
                .orderBy(SAVED_DECK.ID).limit(101).forShare().fetch()
            val decks = rows.take(100).map { summary(sql, it) }
            Result(200, json.encodeToString(DeckList(decks, if (rows.size > 100) decks.last().id else null)))
        }
    }

    fun read(principal: AccountService.Principal, id: String, digest: String? = null): Result {
        // Unknown and foreign selectors have the same safe response, including definition attachment reads.
        if (!DeckValidation.uuid(id) || (digest != null && !DeckValidation.digest(digest))) return error(404, "not_found")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val row = sql.selectFrom(SAVED_DECK).where(SAVED_DECK.ID.eq(UUID.fromString(id)))
                .and(SAVED_DECK.ACCOUNT_ID.eq(UUID.fromString(principal.account.accountId))).forShare().fetchOne()
                ?: return@transaction error(404, "not_found")
            if (digest != null) {
                val payload = sql.select(DEFINITION.PAYLOAD).from(DECK_ENTRY).join(DEFINITION)
                    .on(DEFINITION.DIGEST.eq(DECK_ENTRY.DEFINITION_DIGEST))
                    .where(DECK_ENTRY.DECK_ID.eq(row.id)).and(DEFINITION.DIGEST.eq(digest))
                    .limit(1).fetchOne(DEFINITION.PAYLOAD) ?: return@transaction error(404, "not_found")
                return@transaction Result(200, json.encodeToString(SubmittedDefinition(digest, json.decodeFromString<CardDefinition>(payload))))
            }
            val entries = entries(sql, row.id!!)
            Result(200, json.encodeToString(SavedDeck(summary(sql, row, entries), entries)))
        }
    }
}
