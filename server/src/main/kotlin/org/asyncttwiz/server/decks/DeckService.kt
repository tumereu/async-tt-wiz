package org.asyncttwiz.server.decks

import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.generated.tables.AccountDefinition.ACCOUNT_DEFINITION
import org.asyncttwiz.server.generated.tables.AccountRequest.ACCOUNT_REQUEST
import org.asyncttwiz.server.generated.tables.DeckEntry.DECK_ENTRY
import org.asyncttwiz.server.generated.tables.Definition.DEFINITION
import org.asyncttwiz.server.generated.tables.RequestIdentity.REQUEST_IDENTITY
import org.asyncttwiz.server.generated.tables.SavedDeck.SAVED_DECK
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
            val summary = DeckSummary(id.toString(), row.name!!, row.revision.toString(), row.createdAt!!.toInstant().toString())
            Result(201, json.encodeToString(SavedDeck(summary, input.entries)))
        }
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

    fun list(principal: AccountService.Principal, after: String?): Result {
        if (after != null && !DeckValidation.uuid(after)) return error(400, "invalid_request", "after")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val rows = sql.selectFrom(SAVED_DECK).where(SAVED_DECK.ACCOUNT_ID.eq(UUID.fromString(principal.account.accountId)))
                .and(if (after == null) DSL.trueCondition() else SAVED_DECK.ID.gt(UUID.fromString(after)))
                .orderBy(SAVED_DECK.ID).limit(101).fetch()
            val decks = rows.take(100).map { DeckSummary(it.id.toString(), it.name!!, it.revision.toString(), it.createdAt!!.toInstant().toString()) }
            Result(200, json.encodeToString(DeckList(decks, if (rows.size > 100) decks.last().id else null)))
        }
    }

    fun read(principal: AccountService.Principal, id: String, digest: String? = null): Result {
        // Unknown and foreign selectors have the same safe response, including definition attachment reads.
        if (!DeckValidation.uuid(id) || (digest != null && !DeckValidation.digest(digest))) return error(404, "not_found")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val row = sql.selectFrom(SAVED_DECK).where(SAVED_DECK.ID.eq(UUID.fromString(id)))
                .and(SAVED_DECK.ACCOUNT_ID.eq(UUID.fromString(principal.account.accountId))).fetchOne()
                ?: return@transaction error(404, "not_found")
            if (digest != null) {
                val payload = sql.select(DEFINITION.PAYLOAD).from(DECK_ENTRY).join(DEFINITION)
                    .on(DEFINITION.DIGEST.eq(DECK_ENTRY.DEFINITION_DIGEST))
                    .where(DECK_ENTRY.DECK_ID.eq(row.id)).and(DEFINITION.DIGEST.eq(digest))
                    .limit(1).fetchOne(DEFINITION.PAYLOAD) ?: return@transaction error(404, "not_found")
                return@transaction Result(200, json.encodeToString(SubmittedDefinition(digest, json.decodeFromString<CardDefinition>(payload))))
            }
            val entries = sql.selectFrom(DECK_ENTRY).where(DECK_ENTRY.DECK_ID.eq(row.id)).orderBy(DECK_ENTRY.POSITION)
                .fetch().map { SavedDeckEntry(SavedDeckSection.valueOf(it.section!!), it.definitionDigest!!, it.quantity!!) }
            Result(200, json.encodeToString(SavedDeck(DeckSummary(row.id.toString(), row.name!!,
                row.revision.toString(), row.createdAt!!.toInstant().toString()), entries)))
        }
    }
}
