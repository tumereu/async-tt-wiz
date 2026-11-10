package org.asyncttwiz.server.cubes

import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.decks.DeckService.Result
import org.asyncttwiz.server.decks.CardListInput
import org.asyncttwiz.server.generated.tables.AccountRequest.ACCOUNT_REQUEST
import org.asyncttwiz.server.generated.tables.CubeEntry.CUBE_ENTRY
import org.asyncttwiz.server.generated.tables.Definition.DEFINITION
import org.asyncttwiz.server.generated.tables.RequestIdentity.REQUEST_IDENTITY
import org.asyncttwiz.server.generated.tables.SavedCube.SAVED_CUBE
import org.asyncttwiz.server.generated.tables.records.SavedCubeRecord
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/** Account-owned snapshots; their cards' card data comes from the server's catalog as saved decks' does. */
class CubeService(private val runtime: ServiceRuntime) {
    private val json = DeckJson.format
    private fun error(status: Int, code: String) = Result(status, json.encodeToString(DeckError(code)))

    /** A new cube of [CreateCube.entries], its cards' card data built from the server's catalog ([CardListInput]). */
    fun create(principal: AccountService.Principal, input: CreateCube): Result {
        val valid = CubeValidation.name(input.name) && CubeValidation.input(input.entries) && CubeValidation.provenance(input.cubeCobraId)
        val (built, refused) = if (valid) CardListInput.build(input.entries) else null to null
        if (refused != null) return error(refused.status, refused.code)
        return business(principal, input.requestId, "createCube", DeckJson.canonical(input)) { sql, owner ->
            if (built == null) return@business error(400, "invalid_cube")
            // A new cube holds no card from before: every card comes from the catalog.
            if (built.kept.isNotEmpty()) return@business error(400, "definitions_unavailable")
            val entries = cubeEntries(built)
            built.store(sql)
            val row = sql.insertInto(SAVED_CUBE).set(SAVED_CUBE.ID, UUID.randomUUID())
                .set(SAVED_CUBE.ACCOUNT_ID, owner).set(SAVED_CUBE.NAME, input.name)
                .set(SAVED_CUBE.CUBE_COBRA_ID, input.cubeCobraId).returning().fetchSingle()
            replace(sql, owner, row.id!!, entries)
            Result(201, json.encodeToString(SavedCube(summary(sql, row, entries), entries)))
        }
    }

    fun update(principal: AccountService.Principal, id: String, input: UpdateCube): Result {
        val validEntries = input.entries?.let(CubeValidation::input) != false
        val (built, refused) = if (input.entries != null && validEntries) CardListInput.build(input.entries!!) else null to null
        if (refused != null) return error(refused.status, refused.code)
        return business(principal, input.requestId, "updateCube", intent(id, json.encodeToJsonElement(input))) { sql, owner ->
            val row = owned(sql, owner, id) ?: return@business error(404, "not_found")
            if ((input.name == null && input.entries == null && input.cubeCobraId == null) ||
                input.name?.let { !CubeValidation.name(it) } == true || !CubeValidation.provenance(input.cubeCobraId) ||
                !validEntries) return@business error(400, "invalid_cube")
            val previous = entries(sql, row.id!!)
            val next = if (built == null) previous else {
                // A card from before is kept only while the cube holds it.
                if (!previous.map { it.definition }.containsAll(built.kept)) return@business error(400, "definitions_unavailable")
                cubeEntries(built)
            }
            val name = input.name ?: row.name!!
            val provenance = input.cubeCobraId ?: row.cubeCobraId
            if (next != previous || name != row.name || provenance != row.cubeCobraId) {
                if (next != previous) { built?.store(sql); replace(sql, owner, row.id!!, next) }
                row.name = name; row.cubeCobraId = provenance; row.revision = row.revision!! + 1
                row.updatedAt = sql.update(SAVED_CUBE).set(SAVED_CUBE.NAME, row.name).set(SAVED_CUBE.CUBE_COBRA_ID, provenance)
                    .set(SAVED_CUBE.REVISION, row.revision).set(SAVED_CUBE.UPDATED_AT, DSL.currentOffsetDateTime())
                    .where(SAVED_CUBE.ID.eq(row.id)).returning(SAVED_CUBE.UPDATED_AT).fetchSingle().updatedAt
            }
            Result(200, json.encodeToString(SavedCube(summary(sql, row, next), next)))
        }
    }

    private fun cubeEntries(built: CardListInput) = built.entries.map { SavedCubeEntry(it.digest, it.quantity, it.section) }

    fun delete(principal: AccountService.Principal, id: String, input: DeleteCube): Result =
        business(principal, input.requestId, "deleteCube", intent(id, json.encodeToJsonElement(input))) { sql, owner ->
            val row = owned(sql, owner, id) ?: return@business error(404, "not_found")
            sql.deleteFrom(SAVED_CUBE).where(SAVED_CUBE.ID.eq(row.id)).execute()
            Result(200, json.encodeToString(CubeDeleted(id)))
        }

    fun list(principal: AccountService.Principal, after: String?): Result {
        if (after != null && !DeckValidation.uuid(after)) return error(400, "invalid_request")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val rows = sql.selectFrom(SAVED_CUBE).where(SAVED_CUBE.ACCOUNT_ID.eq(UUID.fromString(principal.account.accountId)))
                .and(if (after == null) DSL.trueCondition() else SAVED_CUBE.ID.gt(UUID.fromString(after)))
                .orderBy(SAVED_CUBE.ID).limit(101).forShare().fetch()
            val cubes = rows.take(100).map { summary(sql, it, entries(sql, it.id!!)) }
            Result(200, json.encodeToString(CubeList(cubes, if (rows.size > 100) cubes.last().id else null)))
        }
    }

    fun read(principal: AccountService.Principal, id: String): Result {
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val row = sql.selectFrom(SAVED_CUBE).where(SAVED_CUBE.ID.eq(UUID.fromString(id)))
                .and(SAVED_CUBE.ACCOUNT_ID.eq(UUID.fromString(principal.account.accountId))).forShare().fetchOne()
                ?: return@transaction error(404, "not_found")
            val entries = entries(sql, row.id!!)
            Result(200, json.encodeToString(SavedCube(summary(sql, row, entries), entries)))
        }
    }

    fun definitions(principal: AccountService.Principal, id: String, input: ReadCubeDefinitions): Result {
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        if (!CubeValidation.definitionRead(input.digests)) return error(400, "invalid_request")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val row = sql.selectFrom(SAVED_CUBE).where(SAVED_CUBE.ID.eq(UUID.fromString(id)))
                .and(SAVED_CUBE.ACCOUNT_ID.eq(UUID.fromString(principal.account.accountId))).forShare().fetchOne()
                ?: return@transaction error(404, "not_found")
            val definitions = sql.select(DEFINITION.DIGEST, DEFINITION.PAYLOAD).from(CUBE_ENTRY).join(DEFINITION)
                .on(DEFINITION.DIGEST.eq(CUBE_ENTRY.DEFINITION_DIGEST))
                .where(CUBE_ENTRY.CUBE_ID.eq(row.id)).and(DEFINITION.DIGEST.`in`(input.digests))
                .fetch().associate { it.get(DEFINITION.DIGEST)!! to it.get(DEFINITION.PAYLOAD)!! }
            if (definitions.keys != input.digests.toSet()) return@transaction error(404, "not_found")
            Result(200, json.encodeToString(SubmittedDefinitions(input.digests.distinct().map { digest ->
                SubmittedDefinition(digest, json.decodeFromString<CardDefinition>(definitions.getValue(digest)))
            })))
        }
    }

    private fun owned(sql: DSLContext, owner: UUID, id: String): SavedCubeRecord? =
        if (!DeckValidation.uuid(id)) null else sql.selectFrom(SAVED_CUBE).where(SAVED_CUBE.ID.eq(UUID.fromString(id)))
            .and(SAVED_CUBE.ACCOUNT_ID.eq(owner)).forUpdate().fetchOne()
    private fun entries(sql: DSLContext, id: UUID) = sql.selectFrom(CUBE_ENTRY).where(CUBE_ENTRY.CUBE_ID.eq(id))
        .orderBy(CUBE_ENTRY.POSITION).fetch().map { SavedCubeEntry(it.definitionDigest!!, it.quantity!!, SavedDeckSection.valueOf(it.section!!)) }
    /** [entries] as cube [id]'s rows, in order, replacing its old ones; the new rows in one statement (SYS-025). */
    private fun replace(sql: DSLContext, owner: UUID, id: UUID, entries: List<SavedCubeEntry>) {
        sql.deleteFrom(CUBE_ENTRY).where(CUBE_ENTRY.CUBE_ID.eq(id)).execute()
        if (entries.isEmpty()) return
        var insert = sql.insertInto(CUBE_ENTRY, CUBE_ENTRY.CUBE_ID, CUBE_ENTRY.ACCOUNT_ID, CUBE_ENTRY.POSITION, CUBE_ENTRY.SECTION,
            CUBE_ENTRY.DEFINITION_DIGEST, CUBE_ENTRY.QUANTITY)
        entries.forEachIndexed { index, entry -> insert = insert.values(id, owner, index, entry.section.name, entry.definition, entry.quantity) }
        insert.execute()
    }
    /** LOB-124, DRF-161: its Automatum support worked out from its cards' printings by the catalog's marks. */
    private fun summary(sql: DSLContext, row: SavedCubeRecord, entries: List<SavedCubeEntry>) = CubeSummary(row.id.toString(), row.name!!,
        row.revision.toString(), row.createdAt!!.toInstant().toString(), row.updatedAt!!.toInstant().toString(),
        entries.filter { it.section == SavedDeckSection.MAINBOARD }.sumOf { it.quantity.toLong() }, row.cubeCobraId,
        entries.filter { it.section == SavedDeckSection.MAYBEBOARD }.sumOf { it.quantity.toLong() },
        org.asyncttwiz.server.decks.CardListSupports.ofDigests(sql, entries.map { it.definition }))
    private fun intent(id: String, input: JsonElement) = DeckJson.canonicalElement(buildJsonObject { put("cubeId", id); put("input", input) })

    private fun business(principal: AccountService.Principal, requestId: String, kind: String, canonical: String,
        action: (DSLContext, UUID) -> Result): Result {
        if (!DeckValidation.uuid(requestId)) return error(400, "invalid_request")
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
}
