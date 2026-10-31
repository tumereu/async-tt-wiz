package org.asyncttwiz.server.pods

import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.decks.DeckService.Result
import org.asyncttwiz.server.generated.tables.PodPreset.POD_PRESET
import org.asyncttwiz.server.generated.tables.SavedCube.SAVED_CUBE
import org.asyncttwiz.server.generated.tables.records.PodPresetRecord
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/** Personal New pod form presets. Latest write wins; names are unique per account ignoring case. */
class PodPresetService(private val runtime: ServiceRuntime) {
    private val json = DeckJson.format
    private fun error(status: Int, code: String) = Result(status, json.encodeToString(PodError(code)))

    fun list(principal: AccountService.Principal): Result = transaction(principal) { sql, owner ->
        Result(200, json.encodeToString(PodPresetList(presets(sql, owner))))
    }

    fun save(principal: AccountService.Principal, input: SavePodPreset): Result {
        if (!PodPresetValidation.name(input.name)) return error(400, "invalid_name")
        val settings = input.settings.copy(librarianDefinition = null)
        if (settings.validate(2).isNotEmpty()) return error(400, "invalid_settings")
        if (input.cubeId != null && input.setCode != null) return error(400, "invalid_source")
        val setCode = input.setCode
        if (setCode != null && (!PodPresetValidation.setCode(setCode) || input.setName?.let(PodPresetValidation::setName) != true) ||
            setCode == null && input.setName != null) return error(400, "invalid_source")
        if (input.cubeId?.let(DeckValidation::uuid) == false) return error(404, "cube_not_found")
        return transaction(principal) { sql, owner ->
            val cube = input.cubeId?.let { UUID.fromString(it) }
            if (cube != null && !sql.fetchExists(SAVED_CUBE, SAVED_CUBE.ID.eq(cube).and(SAVED_CUBE.ACCOUNT_ID.eq(owner))))
                return@transaction error(404, "cube_not_found")
            val key = PodPresetValidation.key(input.name)
            val existing = sql.selectFrom(POD_PRESET).where(POD_PRESET.ACCOUNT_ID.eq(owner)).and(POD_PRESET.NAME_KEY.eq(key))
                .forUpdate().fetchOne()
            if (existing != null && !input.overwrite) return@transaction error(409, "preset_exists")
            val encoded = json.encodeToString(settings)
            val id = if (existing != null) {
                sql.update(POD_PRESET).set(POD_PRESET.NAME, input.name).set(POD_PRESET.SETTINGS, encoded)
                    .set(POD_PRESET.CUBE_ID, cube).set(POD_PRESET.SET_CODE, input.setCode).set(POD_PRESET.SET_NAME, input.setName)
                    .set(POD_PRESET.UPDATED_AT, DSL.currentOffsetDateTime()).where(POD_PRESET.ID.eq(existing.id)).execute()
                existing.id!!
            } else {
                if (sql.fetchCount(POD_PRESET, POD_PRESET.ACCOUNT_ID.eq(owner)) >= PodPresetValidation.MAX_PRESETS)
                    return@transaction error(409, "too_many_presets")
                val id = UUID.randomUUID()
                // A concurrent save of the same name wins the unique key; this one then reports the name as taken.
                val inserted = sql.insertInto(POD_PRESET).set(POD_PRESET.ID, id).set(POD_PRESET.ACCOUNT_ID, owner)
                    .set(POD_PRESET.NAME, input.name).set(POD_PRESET.NAME_KEY, key).set(POD_PRESET.SETTINGS, encoded)
                    .set(POD_PRESET.CUBE_ID, cube).set(POD_PRESET.SET_CODE, input.setCode).set(POD_PRESET.SET_NAME, input.setName)
                    .onConflictDoNothing().execute()
                if (inserted == 0) return@transaction error(409, "preset_exists")
                id
            }
            Result(if (existing == null) 201 else 200, json.encodeToString(presets(sql, owner).single { it.id == id.toString() }))
        }
    }

    fun rename(principal: AccountService.Principal, id: String, input: RenamePodPreset): Result {
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        if (!PodPresetValidation.name(input.name)) return error(400, "invalid_name")
        return transaction(principal) { sql, owner ->
            val row = owned(sql, owner, id) ?: return@transaction error(404, "not_found")
            val key = PodPresetValidation.key(input.name)
            if (sql.fetchExists(POD_PRESET, POD_PRESET.ACCOUNT_ID.eq(owner).and(POD_PRESET.NAME_KEY.eq(key)).and(POD_PRESET.ID.ne(row.id))))
                return@transaction error(409, "preset_exists")
            sql.update(POD_PRESET).set(POD_PRESET.NAME, input.name).set(POD_PRESET.NAME_KEY, key)
                .set(POD_PRESET.UPDATED_AT, DSL.currentOffsetDateTime()).where(POD_PRESET.ID.eq(row.id)).execute()
            Result(200, json.encodeToString(presets(sql, owner).single { it.id == id }))
        }
    }

    fun delete(principal: AccountService.Principal, id: String): Result {
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        return transaction(principal) { sql, owner ->
            val row = owned(sql, owner, id) ?: return@transaction error(404, "not_found")
            sql.deleteFrom(POD_PRESET).where(POD_PRESET.ID.eq(row.id)).execute()
            Result(200, json.encodeToString(PodPresetDeleted(id)))
        }
    }

    private fun owned(sql: DSLContext, owner: UUID, id: String): PodPresetRecord? =
        sql.selectFrom(POD_PRESET).where(POD_PRESET.ID.eq(UUID.fromString(id))).and(POD_PRESET.ACCOUNT_ID.eq(owner)).forUpdate().fetchOne()

    /** A cube counts only while the caller still owns it; otherwise the preset reports it missing. */
    private fun presets(sql: DSLContext, owner: UUID): List<PodPreset> {
        val cubeName = SAVED_CUBE.NAME.`as`("cube_name")
        return sql.select(POD_PRESET.ID, POD_PRESET.NAME, POD_PRESET.SETTINGS, POD_PRESET.CUBE_ID, POD_PRESET.SET_CODE,
            POD_PRESET.SET_NAME, POD_PRESET.UPDATED_AT, cubeName).from(POD_PRESET)
            .leftJoin(SAVED_CUBE).on(SAVED_CUBE.ID.eq(POD_PRESET.CUBE_ID)).and(SAVED_CUBE.ACCOUNT_ID.eq(owner))
            .where(POD_PRESET.ACCOUNT_ID.eq(owner)).orderBy(POD_PRESET.NAME_KEY, POD_PRESET.ID).fetch().mapNotNull { row ->
                // A preset whose stored settings can't be read is left out (SYS-052).
                val settings = StoredData.orSkip("pod preset", row.get(POD_PRESET.ID)) {
                    json.decodeFromString<DraftSettings>(row.get(POD_PRESET.SETTINGS)!!) } ?: return@mapNotNull null
                val cube = row.get(POD_PRESET.CUBE_ID)
                val name = row.get(cubeName)
                PodPreset(row.get(POD_PRESET.ID).toString(), row.get(POD_PRESET.NAME)!!, settings,
                    cube?.toString(), name, cube != null && name == null,
                    row.get(POD_PRESET.SET_CODE), row.get(POD_PRESET.SET_NAME), row.get(POD_PRESET.UPDATED_AT)!!.toInstant().toString())
            }
    }

    private fun transaction(principal: AccountService.Principal, action: (DSLContext, UUID) -> Result): Result =
        runtime.transaction { connection -> action(DSL.using(connection, SQLDialect.POSTGRES), UUID.fromString(principal.account.accountId)) }
}
