package org.asyncttwiz.server.decks

import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.accounts.AccountService
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

    /** A new deck of [CreateDeck.entries], its cards' card data built from the server's catalog ([CardListInput]), in one
     * request with its [CreateDeck.cover] (a card of the deck by digest). */
    fun create(principal: AccountService.Principal, input: CreateDeck): Result {
        if (!DeckValidation.uuid(input.requestId)) return error(400, "invalid_request", "requestId")
        val valid = DeckValidation.create(input)
        val (built, refused) = if (valid) CardListInput.build(input.entries) else null to null
        if (refused != null) return error(refused.status, refused.code)
        return business(principal, input.requestId, "createDeck", DeckJson.canonical(input)) { sql, owner ->
            if (built == null) return@business error(400, "invalid_deck")
            // A new deck holds no card from before: every card comes from the catalog.
            if (built.kept.isNotEmpty()) return@business error(400, "definitions_unavailable")
            val entries = built.entries.map { SavedDeckEntry(it.section, it.digest, it.quantity) }
            if (input.cover != null && entries.none { it.definition == input.cover }) return@business error(400, "invalid_cover", "cover")
            built.store(sql)
            val id = UUID.randomUUID()
            val row = sql.insertInto(SAVED_DECK).set(SAVED_DECK.ID, id).set(SAVED_DECK.ACCOUNT_ID, owner)
                .set(SAVED_DECK.NAME, input.name).set(SAVED_DECK.COVER_DIGEST, input.cover).returning().fetchSingle()
            insertEntries(sql, id, owner, entries)
            Result(201, json.encodeToString(SavedDeck(summary(sql, row, entries), entries)))
        }
    }

    fun update(principal: AccountService.Principal, id: String, input: UpdateDeck): Result {
        if (!DeckValidation.uuid(input.requestId)) return error(400, "invalid_request", "requestId")
        val contents = input.contents
        val validContents = contents == null || DeckValidation.input(contents.entries)
        val (built, refused) = if (contents != null && validContents) CardListInput.build(contents.entries) else null to null
        if (refused != null) return error(refused.status, refused.code)
        return business(principal, input.requestId, "updateDeck", intent(id, json.encodeToJsonElement(UpdateDeck.serializer(), input))) { sql, owner ->
            val row = owned(sql, owner, id) ?: return@business error(404, "not_found")
            // Draft decks follow their pod submission; "Save as deck" makes an editable copy.
            if (row.draftPodId != null) return@business error(409, "draft_deck")
            val requestedName = input.name
            val requestedCover = input.cover
            if ((requestedName == null && requestedCover == null && contents == null) ||
                (requestedName != null && (!DeckValidation.text(requestedName) || requestedName.isBlank())))
                return@business error(400, "invalid_deck", "name")
            val previous = entries(sql, row.id!!)
            val next = if (contents == null) previous else {
                if (built == null) return@business error(400, "invalid_deck")
                // A card from before is kept only while the deck holds it.
                if (!previous.map { it.definition }.containsAll(built.kept)) return@business error(400, "definitions_unavailable")
                // An import replaces the whole list (no merging).
                built.entries.map { SavedDeckEntry(it.section, it.digest, it.quantity) }
            }
            if (requestedCover != null && (!DeckValidation.digest(requestedCover) || next.none { it.definition == requestedCover }))
                return@business error(400, "invalid_cover", "cover")
            val name = input.name ?: row.name!!
            val cover = (input.cover ?: row.coverDigest)?.takeIf { digest -> next.any { it.definition == digest } }
            if (next != previous) {
                built?.store(sql)
                sql.deleteFrom(DECK_ENTRY).where(DECK_ENTRY.DECK_ID.eq(row.id)).execute()
                insertEntries(sql, row.id!!, owner, next)
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
            insertEntries(sql, copy, owner, entries)
            Result(201, json.encodeToString(SavedDeck(summary(sql, row, entries), entries)))
        }
    }

    /** [entries] as deck [deck]'s rows, in order, in one statement (SYS-025). */
    private fun insertEntries(sql: DSLContext, deck: UUID, owner: UUID, entries: List<SavedDeckEntry>) {
        if (entries.isEmpty()) return
        var insert = sql.insertInto(DECK_ENTRY, DECK_ENTRY.DECK_ID, DECK_ENTRY.ACCOUNT_ID, DECK_ENTRY.POSITION, DECK_ENTRY.SECTION,
            DECK_ENTRY.DEFINITION_DIGEST, DECK_ENTRY.QUANTITY)
        entries.forEachIndexed { index, entry -> insert = insert.values(deck, owner, index, entry.section.name, entry.definition, entry.quantity) }
        insert.execute()
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

    private fun summary(sql: DSLContext, row: SavedDeckRecord, entries: List<SavedDeckEntry> = entries(sql, row.id!!)): DeckSummary =
        summary(row, entries, CardListSupports.ofDigests(sql, entries.map { it.definition })) { digest ->
            sql.select(DEFINITION.PAYLOAD).from(DEFINITION).where(DEFINITION.DIGEST.eq(digest)).fetchSingle(DEFINITION.PAYLOAD)!! }

    private fun coverDigest(row: SavedDeckRecord, entries: List<SavedDeckEntry>) =
        row.coverDigest?.takeIf { digest -> entries.any { it.definition == digest } } ?: entries.firstOrNull()?.definition

    /** A list's summaries with three queries in all (entries, cover cards, the cards' printings) instead of three per deck. */
    private fun summaries(sql: DSLContext, rows: List<SavedDeckRecord>): List<DeckSummary> {
        if (rows.isEmpty()) return emptyList()
        val entries = sql.selectFrom(DECK_ENTRY).where(DECK_ENTRY.DECK_ID.`in`(rows.map { it.id }))
            .orderBy(DECK_ENTRY.DECK_ID, DECK_ENTRY.POSITION).fetch().groupBy({ it.deckId!! }) {
                SavedDeckEntry(SavedDeckSection.valueOf(it.section!!), it.definitionDigest!!, it.quantity!!)
            }
        val covers = rows.mapNotNull { coverDigest(it, entries[it.id].orEmpty()) }.toSet()
        val payloads = if (covers.isEmpty()) emptyMap() else sql.select(DEFINITION.DIGEST, DEFINITION.PAYLOAD).from(DEFINITION)
            .where(DEFINITION.DIGEST.`in`(covers)).fetch().associate { it[DEFINITION.DIGEST]!! to it[DEFINITION.PAYLOAD]!! }
        val cards = CardListSupports.cards(sql, entries.values.flatten().map { it.definition }.toSet())
        return rows.map { row ->
            val listed = entries[row.id].orEmpty()
            summary(row, listed, CardListSupports.support(listed.mapNotNull { cards[it.definition] }), payloads::getValue)
        }
    }

    private fun summary(row: SavedDeckRecord, entries: List<SavedDeckEntry>, support: CardListSupport?, payloadOf: (String) -> String): DeckSummary {
        val cover = coverDigest(row, entries)?.let { digest ->
            val payload = payloadOf(digest)
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
            row.draftPodId?.let { DraftDeckOrigin(it.toString(), row.draftPodName!!) },
            entries.filter { it.section == SavedDeckSection.MAYBEBOARD }.sumOf { it.quantity.toLong() }, support)
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
                return@transaction Result(200, json.encodeToString(DeckList(summaries(sql, rows))))
            }
            val rows = sql.selectFrom(SAVED_DECK).where(SAVED_DECK.ACCOUNT_ID.eq(owner)).and(SAVED_DECK.DRAFT_POD_ID.isNull)
                .and(if (after == null) DSL.trueCondition() else SAVED_DECK.ID.gt(UUID.fromString(after)))
                .orderBy(SAVED_DECK.ID).limit(101).forShare().fetch()
            val decks = summaries(sql, rows.take(100))
            Result(200, json.encodeToString(DeckList(decks, if (rows.size > 100) decks.last().id else null)))
        }
    }

    /**
     * The card data of [input]'s digests in deck [id] (owned by the caller), in one request: up to
     * [CubeValidation.MAX_DEFINITIONS_PER_READ] at a time, as a cube's read (owner playtest 2026-11-06: one request per
     * card ran into the deck service's per-account limit). Not found when any digest isn't in the deck.
     */
    fun definitions(principal: AccountService.Principal, id: String, input: ReadCubeDefinitions): Result {
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        if (!CubeValidation.definitionRead(input.digests)) return error(400, "invalid_request")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val row = sql.selectFrom(SAVED_DECK).where(SAVED_DECK.ID.eq(UUID.fromString(id)))
                .and(SAVED_DECK.ACCOUNT_ID.eq(UUID.fromString(principal.account.accountId))).forShare().fetchOne()
                ?: return@transaction error(404, "not_found")
            val definitions = sql.select(DEFINITION.DIGEST, DEFINITION.PAYLOAD).from(DECK_ENTRY).join(DEFINITION)
                .on(DEFINITION.DIGEST.eq(DECK_ENTRY.DEFINITION_DIGEST))
                .where(DECK_ENTRY.DECK_ID.eq(row.id)).and(DEFINITION.DIGEST.`in`(input.digests))
                .fetch().associate { it.get(DEFINITION.DIGEST)!! to it.get(DEFINITION.PAYLOAD)!! }
            if (definitions.keys != input.digests.toSet()) return@transaction error(404, "not_found")
            Result(200, json.encodeToString(SubmittedDefinitions(input.digests.distinct().map { digest ->
                SubmittedDefinition(digest, json.decodeFromString<CardDefinition>(definitions.getValue(digest)))
            })))
        }
    }

    fun read(principal: AccountService.Principal, id: String): Result {
        // Unknown and foreign selectors have the same safe response.
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val row = sql.selectFrom(SAVED_DECK).where(SAVED_DECK.ID.eq(UUID.fromString(id)))
                .and(SAVED_DECK.ACCOUNT_ID.eq(UUID.fromString(principal.account.accountId))).forShare().fetchOne()
                ?: return@transaction error(404, "not_found")
            val entries = entries(sql, row.id!!)
            Result(200, json.encodeToString(SavedDeck(summary(sql, row, entries), entries)))
        }
    }
}
