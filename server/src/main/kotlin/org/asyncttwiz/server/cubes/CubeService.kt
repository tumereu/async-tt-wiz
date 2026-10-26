package org.asyncttwiz.server.cubes

import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.decks.DeckService.Result
import org.asyncttwiz.server.generated.tables.AccountDefinition.ACCOUNT_DEFINITION
import org.asyncttwiz.server.generated.tables.AccountRequest.ACCOUNT_REQUEST
import org.asyncttwiz.server.generated.tables.CubeEntry.CUBE_ENTRY
import org.asyncttwiz.server.generated.tables.Definition.DEFINITION
import org.asyncttwiz.server.generated.tables.RequestIdentity.REQUEST_IDENTITY
import org.asyncttwiz.server.generated.tables.SavedCube.SAVED_CUBE
import org.asyncttwiz.server.generated.tables.records.SavedCubeRecord
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/** Account-owned snapshots; definitions use the same submission and grants as saved decks. */
class CubeService(private val runtime: ServiceRuntime) {
    private val json = DeckJson.format
    private fun error(status: Int, code: String) = Result(status, json.encodeToString(DeckError(code)))

    fun create(principal: AccountService.Principal, input: CreateCube): Result =
        business(principal, input.requestId, "createCube", DeckJson.canonical(input)) { sql, owner ->
            if (!CubeValidation.name(input.name) || !CubeValidation.entries(input.entries) || !CubeValidation.provenance(input.cubeCobraId))
                return@business error(400, "invalid_cube")
            if (!available(sql, owner, input.entries)) return@business error(400, "definitions_unavailable")
            val row = sql.insertInto(SAVED_CUBE).set(SAVED_CUBE.ID, UUID.randomUUID())
                .set(SAVED_CUBE.ACCOUNT_ID, owner).set(SAVED_CUBE.NAME, input.name)
                .set(SAVED_CUBE.CUBE_COBRA_ID, input.cubeCobraId).returning().fetchSingle()
            replace(sql, owner, row.id!!, input.entries)
            Result(201, json.encodeToString(SavedCube(summary(row, input.entries), input.entries)))
        }

    fun update(principal: AccountService.Principal, id: String, input: UpdateCube): Result =
        business(principal, input.requestId, "updateCube", intent(id, json.encodeToJsonElement(input))) { sql, owner ->
            val row = owned(sql, owner, id) ?: return@business error(404, "not_found")
            if ((input.name == null && input.entries == null && input.cubeCobraId == null) ||
                input.name?.let { !CubeValidation.name(it) } == true || !CubeValidation.provenance(input.cubeCobraId) ||
                input.entries?.let { !CubeValidation.entries(it) } == true) return@business error(400, "invalid_cube")
            val previous = entries(sql, row.id!!)
            val next = input.entries ?: previous
            if (!available(sql, owner, next)) return@business error(400, "definitions_unavailable")
            val name = input.name ?: row.name!!
            val provenance = input.cubeCobraId ?: row.cubeCobraId
            if (next != previous || name != row.name || provenance != row.cubeCobraId) {
                if (next != previous) replace(sql, owner, row.id!!, next)
                row.name = name; row.cubeCobraId = provenance; row.revision = row.revision!! + 1
                row.updatedAt = sql.update(SAVED_CUBE).set(SAVED_CUBE.NAME, row.name).set(SAVED_CUBE.CUBE_COBRA_ID, provenance)
                    .set(SAVED_CUBE.REVISION, row.revision).set(SAVED_CUBE.UPDATED_AT, DSL.currentOffsetDateTime())
                    .where(SAVED_CUBE.ID.eq(row.id)).returning(SAVED_CUBE.UPDATED_AT).fetchSingle().updatedAt
            }
            Result(200, json.encodeToString(SavedCube(summary(row, next), next)))
        }

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
            val cubes = rows.take(100).map { summary(it, entries(sql, it.id!!)) }
            Result(200, json.encodeToString(CubeList(cubes, if (rows.size > 100) cubes.last().id else null)))
        }
    }

    fun read(principal: AccountService.Principal, id: String, digest: String? = null): Result {
        if (!DeckValidation.uuid(id) || (digest != null && !DeckValidation.digest(digest))) return error(404, "not_found")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val row = sql.selectFrom(SAVED_CUBE).where(SAVED_CUBE.ID.eq(UUID.fromString(id)))
                .and(SAVED_CUBE.ACCOUNT_ID.eq(UUID.fromString(principal.account.accountId))).forShare().fetchOne()
                ?: return@transaction error(404, "not_found")
            if (digest != null) {
                val payload = sql.select(DEFINITION.PAYLOAD).from(CUBE_ENTRY).join(DEFINITION)
                    .on(DEFINITION.DIGEST.eq(CUBE_ENTRY.DEFINITION_DIGEST))
                    .where(CUBE_ENTRY.CUBE_ID.eq(row.id)).and(DEFINITION.DIGEST.eq(digest))
                    .fetchOne(DEFINITION.PAYLOAD) ?: return@transaction error(404, "not_found")
                return@transaction Result(200, json.encodeToString(SubmittedDefinition(digest, json.decodeFromString<CardDefinition>(payload))))
            }
            val entries = entries(sql, row.id!!)
            Result(200, json.encodeToString(SavedCube(summary(row, entries), entries)))
        }
    }

    private fun owned(sql: DSLContext, owner: UUID, id: String): SavedCubeRecord? =
        if (!DeckValidation.uuid(id)) null else sql.selectFrom(SAVED_CUBE).where(SAVED_CUBE.ID.eq(UUID.fromString(id)))
            .and(SAVED_CUBE.ACCOUNT_ID.eq(owner)).forUpdate().fetchOne()
    private fun available(sql: DSLContext, owner: UUID, entries: List<SavedCubeEntry>): Boolean {
        val needed = entries.map { it.definition }.toSet()
        return sql.select(ACCOUNT_DEFINITION.DIGEST).from(ACCOUNT_DEFINITION)
            .where(ACCOUNT_DEFINITION.ACCOUNT_ID.eq(owner)).and(ACCOUNT_DEFINITION.DIGEST.`in`(needed))
            .fetchSet(ACCOUNT_DEFINITION.DIGEST) == needed
    }
    private fun entries(sql: DSLContext, id: UUID) = sql.selectFrom(CUBE_ENTRY).where(CUBE_ENTRY.CUBE_ID.eq(id))
        .orderBy(CUBE_ENTRY.POSITION).fetch().map { SavedCubeEntry(it.definitionDigest!!, it.quantity!!) }
    private fun replace(sql: DSLContext, owner: UUID, id: UUID, entries: List<SavedCubeEntry>) {
        sql.deleteFrom(CUBE_ENTRY).where(CUBE_ENTRY.CUBE_ID.eq(id)).execute()
        entries.forEachIndexed { index, entry ->
            sql.insertInto(CUBE_ENTRY).set(CUBE_ENTRY.CUBE_ID, id).set(CUBE_ENTRY.ACCOUNT_ID, owner)
                .set(CUBE_ENTRY.POSITION, index).set(CUBE_ENTRY.DEFINITION_DIGEST, entry.definition)
                .set(CUBE_ENTRY.QUANTITY, entry.quantity).execute()
        }
    }
    private fun summary(row: SavedCubeRecord, entries: List<SavedCubeEntry>) = CubeSummary(row.id.toString(), row.name!!,
        row.revision.toString(), row.createdAt!!.toInstant().toString(), row.updatedAt!!.toInstant().toString(),
        entries.sumOf { it.quantity.toLong() }, row.cubeCobraId)
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
