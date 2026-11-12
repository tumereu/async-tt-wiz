package org.asyncttwiz.server.pods

import java.security.SecureRandom
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.catalog.ServerCatalogs
import org.asyncttwiz.server.activity.ActivityService
import org.asyncttwiz.server.decks.DeckService.Result
import org.asyncttwiz.server.decks.DraftDeckStore
import org.asyncttwiz.server.decks.StoredDefinitions
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.AccountRequest.ACCOUNT_REQUEST
import org.asyncttwiz.server.generated.tables.CubeEntry.CUBE_ENTRY
import org.asyncttwiz.server.generated.tables.Definition.DEFINITION
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.GameRevision.GAME_REVISION
import org.asyncttwiz.server.generated.tables.Invitation.INVITATION
import org.asyncttwiz.server.generated.tables.Pod.POD
import org.asyncttwiz.server.generated.tables.PlayerPreparation.PLAYER_PREPARATION
import org.asyncttwiz.server.generated.tables.PodInvitation.POD_INVITATION as INVITE
import org.asyncttwiz.server.generated.tables.PodDeckNotes.POD_DECK_NOTES as NOTES
import org.asyncttwiz.server.generated.tables.PodDraftMarks.POD_DRAFT_MARKS as MARKS
import org.asyncttwiz.server.generated.tables.PodMember.POD_MEMBER as MEMBER
import org.asyncttwiz.server.generated.tables.RequestIdentity.REQUEST_IDENTITY
import org.asyncttwiz.server.generated.tables.SavedCube.SAVED_CUBE
import org.asyncttwiz.server.generated.tables.records.PodRecord
import org.asyncttwiz.server.game.PreparationStore
import org.asyncttwiz.server.lobby.LobbyService
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/** Every transition and its activity records commit under the same pod row lock. */
class PodService(private val runtime: ServiceRuntime) {
    private val json = DeckJson.format
    private val random = SecureRandom()
    private fun error(status: Int, code: String) = Result(status, json.encodeToString(PodError(code)))
    private inline fun <reified T> encoded(value: T) = JSONB.valueOf(json.encodeToString(value))
    /** A pod's stored JSON that the current code can't read fails only that pod's requests (SYS-052). */
    private fun <T> stored(row: PodRecord, read: () -> T) = StoredData.decode("pod", row.id, read)
    private fun build(row: PodRecord, data: String) = stored(row) { json.decodeFromString<PodDeckBuild>(data) }
    private fun settings(row: PodRecord) = stored(row) { json.decodeFromString<DraftSettings>(row.settings!!.data()) }
    // Cubes are arrays. A set source (its pack grid and the snapshot of each of its sets, DRF-177) is stored as a JSON
    // string inside JSONB: PostgreSQL's object-key reordering must not change the significant card-map order of fixed
    // recipe sheets.
    private fun isSetSource(row: PodRecord) = row.sourceSnapshot!!.data().trimStart().startsWith("\"")
    private fun setSource(row: PodRecord): SetPackSources? = row.sourceSnapshot!!.data().let {
        if (isSetSource(row)) stored(row) { json.decodeFromString<SetPackSources>(json.decodeFromString<String>(it)) } else null
    }
    private fun encodedSet(source: SetPackSources) = encoded(json.encodeToString(source))
    private fun source(row: PodRecord) = if (!isSetSource(row))
        stored(row) { json.decodeFromString<List<DraftCubeEntry>>(row.sourceSnapshot!!.data()) } else emptyList()
    /** The prepared cube's Maybe: cards of the cube not drafted in this pod. */
    private fun maybe(row: PodRecord) = row.sourceMaybe?.let { stored(row) { json.decodeFromString<List<DraftCubeEntry>>(it.data()) } }.orEmpty()
    private fun supply(row: PodRecord, players: Int): DraftSupply {
        val finite = settings(row).supply(players, source(row))
        return if (!isSetSource(row)) finite else finite.copy(available = Long.MAX_VALUE)
    }
    private fun state(row: PodRecord) = row.draftState?.takeIf { settings(row).mode.packPassing }
        ?.let { stored(row) { json.decodeFromString<DraftState>(it.data()) } }
    private fun gridState(row: PodRecord) = row.draftState?.takeIf { settings(row).mode == DraftMode.GRID }
        ?.let { stored(row) { json.decodeFromString<GridState>(it.data()) } }
    private fun limitedState(row: PodRecord) = row.draftState?.takeIf { settings(row).mode.limited }
        ?.let { stored(row) { json.decodeFromString<LimitedDraftState>(it.data()) } }
    /** A pod's stored draft, decoded once per request. Settings are read only when there is a draft. */
    private class Draft(val booster: DraftState?, val grid: GridState?, val limited: LimitedDraftState? = null)
    private fun draft(row: PodRecord, settings: DraftSettings? = row.draftState?.let { settings(row) }): Draft {
        val data = row.draftState ?: return Draft(null, null)
        return if (settings!!.mode.packPassing) Draft(stored(row) { json.decodeFromString<DraftState>(data.data()) }, null)
            else if (settings.mode.limited) Draft(null, null, stored(row) { json.decodeFromString<LimitedDraftState>(data.data()) })
            else Draft(null, stored(row) { json.decodeFromString<GridState>(data.data()) })
    }
    private fun pools(row: PodRecord) = gridState(row)?.pools ?: limitedState(row)?.pools ?: state(row)!!.pools
    private fun copies(row: PodRecord) = gridState(row)?.copies ?: limitedState(row)?.copies ?: state(row)!!.copies
    /** Grids the member can select from in a row if nobody else acts (see [GridEngine.consecutivePicks]). */
    private fun gridWaiting(state: GridState?, player: String) = state?.let { GridEngine.consecutivePicks(it, player) } ?: 0
    private fun members(sql: DSLContext, pod: UUID) = sql.selectFrom(MEMBER).where(MEMBER.POD_ID.eq(pod)).orderBy(MEMBER.ACCOUNT_ID).fetch()
    private fun pending(sql: DSLContext, pod: UUID) = sql.selectFrom(INVITE).where(INVITE.POD_ID.eq(pod)).and(INVITE.STATE.eq("PENDING")).orderBy(INVITE.ACCOUNT_ID).fetch()
    private fun identity(sql: DSLContext, id: UUID): AccountIdentity {
        return AccountService.identity(sql.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(id)).fetchSingle())
    }
    /** Every identity a response names, in one query instead of one per account. */
    private fun identities(sql: DSLContext, ids: Collection<UUID>): Map<UUID, AccountIdentity> =
        if (ids.isEmpty()) emptyMap() else sql.selectFrom(ACCOUNT).where(ACCOUNT.ID.`in`(ids.toSet())).fetch()
            .associate { it.id!! to AccountService.identity(it) }
    private fun validName(name: String) = DeckValidation.text(name) && name.isNotBlank() && name.length <= 120 && name.none { it.isISOControl() }

    /** DRF-051: [settings] with the server's Cogwork Librarian when the pod has Librarians (none otherwise; whatever a
     * device sent is replaced), or null when the catalog has none to give. */
    private fun withLibrarian(settings: DraftSettings): DraftSettings? =
        if (settings.librarians == 0) settings.copy(librarianDefinition = null)
        else ServerCatalogs.current()?.librarian?.let { settings.copy(librarianDefinition = it) }

    private fun storeLibrarian(sql: DSLContext, settings: DraftSettings) {
        settings.librarianDefinition?.let { card ->
            sql.insertInto(DEFINITION).set(DEFINITION.DIGEST, card.digest).set(DEFINITION.SCHEMA_VERSION, card.definition.schemaVersion)
                .set(DEFINITION.PAYLOAD, DeckJson.canonical(card.definition)).onConflictDoNothing().execute()
        }
    }

    private fun librarianDigests(sql: DSLContext, digests: Set<String>) =
        PreparationStore.definitions(sql, digests).filter { it.definition.name == "Cogwork Librarian" }.map { it.digest }.toSet()

    /** A set as a pod's source: what is stored ([source], and its Automatum support as JSON), or why it can't be one. */
    private class ChosenSet(val source: org.asyncttwiz.catalog.CatalogSetSource? = null, val support: String? = null, val refusal: Result? = null)

    /**
     * A draft set chosen as a pod's source, by its code: its source and support from the server's card catalog
     * (docs/specs/web-client.md sections 3.7, 3.8), or why it can't be one. DRF-162: a set whose packs can hold a card
     * Automatum can't play (the catalog's marks) is refused.
     */
    private fun chooseSet(code: String): ChosenSet {
        val catalog = ServerCatalogs.current()
        val set = catalog?.draftSet(code)
        if (catalog == null || set == null) {
            log("set $code refused: " + if (catalog == null) "no card catalog" else "not a draft set of catalog ${catalog.version.checksum}")
            return ChosenSet(refusal = error(404, "set_not_found"))
        }
        if (!set.automatum) {
            log("set $code refused: Automatum can't play ${set.unsupported.size} cards its packs can hold: ${set.unsupported.take(20)}")
            return ChosenSet(refusal = error(409, "automatum_unsupported"))
        }
        val support = json.encodeToString(catalog.support(set))
        val source = try { catalog.source(code) } catch (failure: IllegalStateException) {
            log("set $code refused: its source can't be built: ${failure.message}")
            null
        } ?: return ChosenSet(refusal = error(409, "set_unavailable"))
        log("set $code from catalog ${catalog.version.checksum}: ${source.snapshot.cards.size} cards, ${source.definitions.size} " +
            "definitions, recipe ${source.snapshot.recipe?.name ?: "none"}, ${source.missing} recipe printings not in the catalog")
        return ChosenSet(source, support)
    }

    /** The sets of a pod's pack grid, chosen as its source (DRF-177): what is stored, or why it can't be. */
    private class ChosenSets(val sources: SetPackSources? = null, val definitions: List<SubmittedDefinition> = emptyList(),
        val support: String? = null, val refusal: Result? = null)

    /**
     * The pod's set source from [packSets], the pack grid read row by row (or one set for every pack), for [settings]:
     * every set must be one [chooseSet] takes (DRF-162: draftable, every card supported), or the whole grid is refused.
     */
    private fun chooseSets(packSets: List<String>, settings: DraftSettings): ChosenSets {
        if (!PodPackGrid.valid(packSets)) return ChosenSets(refusal = error(400, "invalid_source"))
        val packs = PodPackGrid.normalize(packSets, settings)
        val chosen = packs.distinct().map { code ->
            chooseSet(code).also { set -> set.refusal?.let { log("pack grid $packs refused at set $code"); return ChosenSets(refusal = it) } }
        }
        val support = chosen.map { support(it.support) }
        val combined = CardListSupport(support.firstNotNullOfOrNull { it }?.parserVersion ?: 0,
            support.flatMap { it?.byHand.orEmpty() }.distinct().sorted(), support.firstNotNullOfOrNull { it }?.engineVersion ?: 0)
        log("pack grid for ${settings.mode}: $packs (asked ${packSets.size} cells)")
        return ChosenSets(SetPackSources(packs, chosen.map { it.source!!.snapshot }),
            chosen.flatMap { it.source!!.definitions }.distinctBy { it.digest }, json.encodeToString(combined))
    }

    /** [sources] resized to [settings] (DRF-179): the grid cut or carried on, sets no pack names any more dropped. */
    private fun resized(sources: SetPackSources, settings: DraftSettings): SetPackSources {
        val packs = PodPackGrid.normalize(sources.packs, settings)
        return SetPackSources(packs, sources.sets.filter { it.setCode in packs })
    }

    /** The pod's set source [sources] stored on [row], with its name and Automatum [support] (null: unchanged). A pod
     * of one set is named by it; a mixed grid by its sets' names in the order its packs first name them. */
    private fun storeSetSource(row: PodRecord, sources: SetPackSources, support: String?) {
        row.sourceName = sources.packs.distinct().joinToString(", ") { sources.set(it).setName }
        row.sourceSnapshot = encodedSet(sources); row.sourceMaybe = null
        support?.let { row.automatumSupport = it }
    }

    private fun log(message: String) = println("${java.time.Instant.now()} Pods: $message")

    /** I-D52 (pivot review #2): the pod's Automatum support of its source, kept with it when the source was chosen (the
     * catalog's marks then, LOB-124, DRF-161); null when not known or unreadable (another version's). */
    private fun support(row: PodRecord): CardListSupport? = support(row.automatumSupport)
    private fun support(value: String?): CardListSupport? = value?.let { runCatching { json.decodeFromString<CardListSupport>(it) }.getOrNull() }

    /** Whether pod [id]'s source has cards Automatum can't play fully, as stored with it (I-D52). */
    internal fun unsupported(sql: DSLContext, id: UUID): Boolean =
        sql.selectFrom(POD).where(POD.ID.eq(id)).fetchOne()?.let { unsupported(it) != null } == true

    /** Why the pod's draft or its games can't start: its source has cards Automatum can't play fully (I-D52). */
    internal fun unsupported(row: PodRecord): Result? =
        if (support(row)?.supported == false) error(409, "automatum_unsupported") else null

    /** Stores [definitions] the `definition` table doesn't have yet, in one statement (SYS-025). */
    private fun storeDefinitions(sql: DSLContext, definitions: List<SubmittedDefinition>) {
        if (definitions.isEmpty()) return
        var insert = sql.insertInto(DEFINITION, DEFINITION.DIGEST, DEFINITION.SCHEMA_VERSION, DEFINITION.PAYLOAD)
        for (submitted in definitions) insert = insert.values(submitted.digest, submitted.definition.schemaVersion, DeckJson.canonical(submitted.definition))
        insert.onConflictDoNothing().execute()
    }

    /** A saved cube as a pod's prepared cube, with its Automatum [support] (JSON [CardListSupport]): its cards, Maybe
     * included, by the card catalog's marks when it was read (LOB-124, DRF-161); null without a catalog. */
    private inner class CubeSnapshot(val name: String, val main: List<DraftCubeEntry>, val maybe: List<DraftCubeEntry>,
        val support: String? = null) {
        val unsupported: Boolean get() = support(support)?.supported == false
    }

    /** The saved cube as this pod's prepared cube: its Main is drafted, its Maybe kept aside for Prepare cube. Its cards
     * are built from the server's card catalog by their printings (owner 2026-11-10) and kept with the pod by digest,
     * their colours the catalog's; a card without one counts against its support (LOB-124) and isn't kept. */
    private fun snapshot(sql: DSLContext, owner: UUID, cubeId: String, catalog: org.asyncttwiz.server.catalog.ServerCatalog): CubeSnapshot? {
        if (!DeckValidation.uuid(cubeId)) return null
        val cube = sql.selectFrom(SAVED_CUBE).where(SAVED_CUBE.ID.eq(UUID.fromString(cubeId)))
            .and(SAVED_CUBE.ACCOUNT_ID.eq(owner)).forShare().fetchOne() ?: return null
        val rows = sql.selectFrom(CUBE_ENTRY).where(CUBE_ENTRY.CUBE_ID.eq(cube.id)).orderBy(CUBE_ENTRY.POSITION).fetch()
        val support = catalog.support(rows.map { it.printingId to it.name!! })
        log("cube $cubeId as a pod source: ${rows.size} entries, ${support.byHand.size} cards Automatum can't play by the catalog's " +
            "marks${if (support.supported) "" else ": " + support.byHand.take(20)}")
        val cards = rows.mapNotNull { row -> row.printingId?.let(catalog::card)?.let { row to SubmittedDefinition(DeckJson.digest(it), it) } }
        storeDefinitions(sql, cards.map { it.second }.distinctBy { it.digest })
        val entries = cards.map { (row, card) ->
            val colors = card.definition.printingId?.let { catalog.catalog.printing[it] }?.let { catalog.catalog.faces[it.face].colors }.orEmpty()
                .mapNotNull { color -> DraftColor.entries.firstOrNull { it.name == color } }.toSet()
            row.section to DraftCubeEntry(card.digest, row.quantity!!, colors)
        }
        // Only Main is drafted; Maybe reaches the draft only when Prepare cube moves it to Main (DRF-050).
        return CubeSnapshot(cube.name!!, entries.filter { it.first == SavedDeckSection.MAINBOARD.name }.map { it.second },
            entries.filter { it.first != SavedDeckSection.MAINBOARD.name }.map { it.second }, json.encodeToString(support))
    }

    private fun encodedMaybe(maybe: List<DraftCubeEntry>) = if (maybe.isEmpty()) null else encoded(maybe)

    fun create(principal: AccountService.Principal, input: CreatePod): Result =
        business(principal, input.requestId, "createPod", DeckJson.canonicalElement(json.encodeToJsonElement(input))) { sql, owner, _ ->
            if (!validName(input.name)) return@business error(400, "invalid_name")
            if (input.settings?.validate(2)?.isNotEmpty() == true) return@business error(400, "invalid_settings")
            if (listOfNotNull(input.cubeId, input.setCode, input.packSets).size != 1) return@business error(400, "invalid_source")
            val snapshot = input.cubeId?.let { cube ->
                val catalog = ServerCatalogs.current() ?: return@business error(503, "catalog_unavailable")
                snapshot(sql, owner, cube, catalog) ?: return@business error(404, "cube_not_found") }
            // I-D51/I-D52: a cube Automatum can't play fully (as the owner's device worked it out) can't start a pod.
            if (snapshot?.unsupported == true) return@business error(409, "automatum_unsupported")
            val requested = input.settings ?: DraftSettings.defaults(input.mode)
            // DRF-162, DRF-177: every set of the pack grid comes from the card catalog, refused by its marks
            val sets = (input.packSets ?: input.setCode?.let(::listOf))?.let { packs ->
                chooseSets(packs, requested).also { chosen -> chosen.refusal?.let { return@business it } } }
            sets?.let { storeDefinitions(sql, it.definitions) }
            val settings = withLibrarian(requested) ?: return@business error(400, "librarian_definition_unavailable")
            storeLibrarian(sql, settings)
            val record = sql.newRecord(POD)
            sets?.let { storeSetSource(record, it.sources!!, it.support) }
            val row = sql.insertInto(POD).set(POD.ID, UUID.randomUUID()).set(POD.CREATOR_ID, owner)
                .set(POD.NAME, input.name).set(POD.SETTINGS, encoded(settings))
                .set(POD.SOURCE_NAME, snapshot?.name ?: record.sourceName)
                .set(POD.SOURCE_SNAPSHOT, snapshot?.let { encoded(it.main) } ?: record.sourceSnapshot)
                .set(POD.SOURCE_MAYBE, snapshot?.let { encodedMaybe(it.maybe) })
                .set(POD.AUTOMATUM_SUPPORT, snapshot?.support ?: record.automatumSupport)
                .returning().fetchSingle()
            sql.insertInto(MEMBER).set(MEMBER.POD_ID, row.id).set(MEMBER.ACCOUNT_ID, owner).execute()
            detail(sql, row, owner, 201)
        }

    fun update(principal: AccountService.Principal, id: String, input: UpdatePod): Result =
        mutate(principal, id, input.requestId, "updatePod", json.encodeToJsonElement(input)) { sql, owner, row ->
            creator(row, owner)?.let { return@mutate it }
            if (row.phase != "SETUP") return@mutate error(409, "not_setup")
            if (input.name?.let { !validName(it) } == true) return@mutate error(400, "invalid_name")
            // Setup may have only its creator; validate settings against the minimum startable membership. Too many
            // members for the engine is allowed here: Start is refused and an armed auto-start switches off instead.
            val members = members(sql, row.id!!).size
            if (input.settings?.validate(members.coerceAtLeast(2))?.any { it != DraftSettingsIssue.PLAYERS } == true)
                return@mutate error(400, "invalid_settings")
            if (listOfNotNull(input.cubeId, input.setCode, input.packSets).size > 1) return@mutate error(400, "invalid_source")
            val snapshot = input.cubeId?.let { cube ->
                val catalog = ServerCatalogs.current() ?: return@mutate error(503, "catalog_unavailable")
                snapshot(sql, owner, cube, catalog) ?: return@mutate error(404, "cube_not_found") }
            if (snapshot?.unsupported == true) return@mutate error(409, "automatum_unsupported")
            val next = input.settings ?: settings(row)
            val sets = (input.packSets ?: input.setCode?.let(::listOf))?.let { packs ->
                chooseSets(packs, next).also { chosen -> chosen.refusal?.let { return@mutate it } } }
            input.name?.let { row.name = it }
            input.settings?.let { requested ->
                val settings = withLibrarian(requested) ?: return@mutate error(400, "librarian_definition_unavailable")
                storeLibrarian(sql, settings)
                row.settings = encoded(settings)
            }
            // A newly chosen source starts unprepared: the cube's own Main and Maybe.
            snapshot?.let { row.sourceName = it.name; row.sourceSnapshot = encoded(it.main); row.sourceMaybe = encodedMaybe(it.maybe)
                row.automatumSupport = it.support }
            sets?.let { storeDefinitions(sql, it.definitions); storeSetSource(row, it.sources!!, it.support) }
            // DRF-179: new settings resize the pod's pack grid
            if (snapshot == null && sets == null && input.settings != null) setSource(row)?.let { current ->
                val resized = resized(current, next)
                if (resized != current) {
                    log("pod ${row.id} pack grid resized for ${next.mode}: ${current.packs} -> ${resized.packs}")
                    storeSetSource(row, resized, null)
                }
            }
            overLimit(sql, row)
            save(sql, row)
            detail(sql, row, owner)
        }

    /** The prepared cube's cards by digest, each with its printing and name for the phone to show it (owner 2026-11-10). */
    private fun cubeView(sql: DSLContext, row: PodRecord): PodCube {
        val entries = source(row).map { it to SavedDeckSection.MAINBOARD } + maybe(row).map { it to SavedDeckSection.MAYBEBOARD }
        val cards = StoredDefinitions.read(sql, entries.map { it.first.definition }.toSet()).associate { it.digest to it.definition }
        return PodCube(row.sourceName!!, entries.map { (entry, section) -> cards[entry.definition].let { card ->
            PodCubeEntry(entry.definition, card?.printingId, card?.name ?: "Card", entry.quantity, section) } })
    }

    /** The creator's view of the pod's prepared cube (Prepare cube); a set source has none. */
    fun cube(principal: AccountService.Principal, id: String): Result = creatorRead(principal, id) { sql, row ->
        if (isSetSource(row)) error(409, "not_cube") else Result(200, json.encodeToString(cubeView(sql, row)))
    }

    private fun creatorRead(principal: AccountService.Principal, id: String, read: (DSLContext, PodRecord) -> Result): Result {
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val row = sql.selectFrom(POD).where(POD.ID.eq(UUID.fromString(id))).forShare().fetchOne()
                ?: return@transaction error(404, "not_found")
            val owner = UUID.fromString(principal.account.accountId)
            if (members(sql, row.id!!).none { it.accountId == owner }) return@transaction error(404, "not_found")
            creator(row, owner) ?: read(sql, row)
        }
    }

    /** Prepare cube: moves copies between the pod's Main and Maybe. Only this pod changes, never the saved cube. */
    fun moveCube(principal: AccountService.Principal, id: String, input: MovePodCubeCards): Result =
        mutate(principal, id, input.requestId, "movePodCube", json.encodeToJsonElement(input)) { sql, owner, row ->
            creator(row, owner)?.let { return@mutate it }
            if (row.phase != "SETUP") return@mutate error(409, "not_setup")
            if (isSetSource(row)) return@mutate error(409, "not_cube")
            if (input.from !in SavedDeckSection.CUBE || input.quantity !in 1..CubeValidation.MAX_CARDS ||
                !DeckValidation.digest(input.definition)) return@mutate error(400, "invalid_request")
            val main = source(row).toMutableList(); val maybe = maybe(row).toMutableList()
            val (from, to) = if (input.from == SavedDeckSection.MAINBOARD) main to maybe else maybe to main
            val index = from.indexOfFirst { it.definition == input.definition }
            if (index < 0 || from[index].quantity < input.quantity) return@mutate error(409, "not_enough_copies")
            val moved = from[index]
            if (moved.quantity == input.quantity) from.removeAt(index) else from[index] = moved.copy(quantity = moved.quantity - input.quantity)
            val existing = to.indexOfFirst { it.definition == input.definition }
            if (existing < 0) to += moved.copy(quantity = input.quantity)
            else to[existing] = to[existing].copy(quantity = to[existing].quantity + input.quantity)
            row.sourceSnapshot = encoded(main); row.sourceMaybe = encodedMaybe(maybe)
            save(sql, row)
            Result(200, json.encodeToString(cubeView(sql, row)))
        }

    fun invite(principal: AccountService.Principal, id: String, input: InviteToPod): Result =
        mutate(principal, id, input.requestId, "inviteToPod", json.encodeToJsonElement(input)) { sql, owner, row ->
            creator(row, owner)?.let { return@mutate it }
            if (row.phase != "SETUP") return@mutate error(409, "not_setup")
            if (!DeckValidation.uuid(input.accountId)) return@mutate error(400, "invalid_account")
            val target = UUID.fromString(input.accountId)
            if (!sql.fetchExists(ACCOUNT, ACCOUNT.ID.eq(target))) return@mutate error(404, "account_not_found")
            val members = members(sql, row.id!!)
            val invitations = pending(sql, row.id!!)
            if (members.any { it.accountId == target } || invitations.any { it.accountId == target })
                return@mutate error(409, "already_invited_or_member")
            if (members.size + invitations.size >= 8) return@mutate error(409, "pod_full")
            sql.insertInto(INVITE).set(INVITE.POD_ID, row.id).set(INVITE.ACCOUNT_ID, target).set(INVITE.STATE, "PENDING")
                .onConflict(INVITE.POD_ID, INVITE.ACCOUNT_ID).doUpdate().set(INVITE.STATE, "PENDING")
                .set(INVITE.INVITED_AT, DSL.currentOffsetDateTime()).execute()
            ActivityService.record(sql, target, ActivityKind.POD_INVITATION, row.id!!, UUID.randomUUID(), owner)
            overLimit(sql, row)
            save(sql, row); detail(sql, row, owner)
        }

    fun invitation(principal: AccountService.Principal, id: String, input: PodRequest, accept: Boolean): Result =
        mutate(principal, id, input.requestId, if (accept) "acceptPod" else "declinePod", json.encodeToJsonElement(input)) { sql, owner, row ->
            if (row.phase != "SETUP") return@mutate error(409, "invitation_unavailable")
            if (pending(sql, row.id!!).none { it.accountId == owner }) return@mutate error(404, "invitation_not_found")
            if (accept && members(sql, row.id!!).size >= 8) return@mutate error(409, "pod_full")
            sql.update(INVITE).set(INVITE.STATE, if (accept) "ACCEPTED" else "DECLINED")
                .where(INVITE.POD_ID.eq(row.id)).and(INVITE.ACCOUNT_ID.eq(owner)).execute()
            if (accept) {
                sql.insertInto(MEMBER).set(MEMBER.POD_ID, row.id).set(MEMBER.ACCOUNT_ID, owner).execute()
                // The last acceptance starts an armed pod exactly as the creator's Start would.
                if (row.autoStart == true && pending(sql, row.id!!).isEmpty())
                    begin(sql, row)?.let { failure -> autoStartOff(sql, row, code(failure)) }
            } else if (row.autoStart == true) autoStartOff(sql, row, "declined", owner)
            save(sql, row)
            if (accept) detail(sql, row, owner) else acknowledged(row)
        }

    fun autoStart(principal: AccountService.Principal, id: String, input: SetPodAutoStart): Result =
        mutate(principal, id, input.requestId, "setPodAutoStart", json.encodeToJsonElement(input)) { sql, owner, row ->
            creator(row, owner)?.let { return@mutate it }
            if (row.phase != "SETUP") return@mutate error(409, "not_setup")
            if (input.enabled && pending(sql, row.id!!).isEmpty()) return@mutate error(409, "no_pending_invitations")
            if (input.enabled && members(sql, row.id!!).size + pending(sql, row.id!!).size > settings(row).mode.players.last)
                return@mutate error(409, "too_many_players")
            row.autoStart = input.enabled; row.autoStartOffReason = null; row.autoStartOffAccount = null
            save(sql, row); detail(sql, row, owner)
        }

    private fun code(failure: Result) = json.decodeFromString<PodError>(failure.body).code

    /** An armed auto-start switches off, telling the creator, once members plus invitations exceed the engine's limit. */
    private fun overLimit(sql: DSLContext, row: PodRecord) {
        if (row.autoStart != true) return
        if (members(sql, row.id!!).size + pending(sql, row.id!!).size > settings(row).mode.players.last)
            autoStartOff(sql, row, "too_many_players")
    }

    /** Switches auto-start off and tells the creator why; [author] is the member who declined, if any. */
    private fun autoStartOff(sql: DSLContext, row: PodRecord, reason: String, author: UUID? = null) {
        row.autoStart = false; row.autoStartOffReason = reason; row.autoStartOffAccount = author
        ActivityService.record(sql, row.creatorId!!, ActivityKind.POD_AUTO_START_OFF, row.id!!, UUID.randomUUID(), author)
    }

    fun start(principal: AccountService.Principal, id: String, input: PodRequest): Result =
        mutate(principal, id, input.requestId, "startPod", json.encodeToJsonElement(input)) { sql, owner, row ->
            creator(row, owner)?.let { return@mutate it }
            if (row.phase != "SETUP") return@mutate error(409, "not_setup")
            begin(sql, row)?.let { return@mutate it }
            detail(sql, row, owner)
        }

    /** Starts the draft from setup, or returns why it cannot start. Pending invitations lapse. */
    private fun begin(sql: DSLContext, row: PodRecord): Result? {
        val players = members(sql, row.id!!).map { it.accountId.toString() }
        if (players.size < 2) return error(409, "not_enough_members")
        val settings = settings(row); val cube = source(row)
        if (players.size > settings.mode.players.last) return error(409, "too_many_members")
        if (settings.validate(players.size).isNotEmpty()) return error(400, "invalid_settings")
        if (settings.librarians > 0 && settings.librarianDefinition == null) return error(400, "librarian_definition_unavailable")
        unsupported(row)?.let { return it }
        if (!supply(row, players.size).sufficient) return error(409, "insufficient_supply")
        row.autoStart = false; row.autoStartOffReason = null; row.autoStartOffAccount = null
        val seed = RandomV1.encodeSeed(ByteArray(32).also(random::nextBytes))
        row.draftAttemptId = UUID.randomUUID()
        sql.update(INVITE).set(INVITE.STATE, "LAPSED").where(INVITE.POD_ID.eq(row.id)).and(INVITE.STATE.eq("PENDING")).execute()
        sql.update(MEMBER).set(MEMBER.BUILD, encoded(PodDeckRules.initialBuild(settings.sideboardLimit))).where(MEMBER.POD_ID.eq(row.id)).execute()
        // DRF-179: each pack from its cell's set (the stored grid is normalized; cells past its end take its last set)
        val set = setSource(row)?.let { resized(it, settings) }
        set?.let { log("pod ${row.id} starts ${settings.mode} for ${players.size} players from pack grid ${PodPackGrid.cells(it.packs, settings)}") }
        if (settings.mode.limited) {
            val next = if (set == null) LimitedEngine.startFromCube(players, cube, settings, seed)
                else LimitedEngine.startFromSet(players, set, settings, seed)
            advanceLimited(sql, row, null, next)
        } else if (settings.mode == DraftMode.GRID) {
            val next = if (set == null) GridEngine.start(players, cube, settings, seed)
            else if (settings.freshBoosters) {
                // One fresh booster per grid; its nine cards and refill cards are drawn at random from it.
                GridEngine.startWithCards(players, GridEngine.freshBoosterCards(set, settings, players.size, seed), settings, seed)
            } else {
                // Set grids consume generated packs in card order (including refills), not cube-shuffled cards.
                GridEngine.startWithCards(players, GridEngine.setCards(set, settings, players.size, seed), settings, seed)
            }
            advanceGrid(sql, row, null, next)
        } else {
            val digests = librarianDigests(sql, (cube.map { it.definition } + set?.sets.orEmpty().flatMap { it.cards }.map { it.definition }).toSet())
            val next = if (set == null) DraftEngine.start(players, cube.map { it.copy(librarian = it.definition in digests) }, settings, seed) else
                DraftEngine.startWithPacks(players, SetBoosters.generate(set, PodPackGrid.packSets(set.packs, settings, players.size), seed,
                    settings.cardsPerPack).map { pack -> pack.cards.map { it.copy(librarian = it.definition in digests) } }, settings, seed)
            advance(sql, row, null, next)
        }
        return null
    }

    fun pick(principal: AccountService.Principal, id: String, input: PickPod): Result =
        mutate(principal, id, input.requestId, "pickPod", json.encodeToJsonElement(input), input.draftAttemptId) { sql, owner, row ->
            member(sql, row, owner)?.let { return@mutate it }
            if (row.phase != "DRAFTING") return@mutate error(409, "not_drafting")
            if (!settings(row).mode.packPassing) return@mutate error(409, "wrong_draft_mode")
            val previous = state(row)!!
            val result = DraftEngine.pick(previous, DraftPick(owner.toString(), input.packId, input.keep, input.burn, input.librarians))
            if (result.reason != null) return@mutate error(409, result.reason!!.name.lowercase())
            // The marks belonged to the pack just picked.
            clearMarks(sql, row.id!!, owner)
            advance(sql, row, previous, result.state!!)
            detail(sql, row, owner)
        }

    /**
     * Stores the caller's marks for their current head pack (latest wins). Not request-identified: replays and reordering
     * only ever overwrite private intent, and a mark for any other pack is refused, so a late write cannot outlive a pick.
     */
    fun saveMarks(principal: AccountService.Principal, id: String, input: SetPodDraftMarks): Result {
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        if (!DeckValidation.uuid(input.draftAttemptId)) return error(400, "invalid_request")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            // A share lock orders this after (or before) a pick of the same pack, which takes the pod row for update.
            val row = sql.selectFrom(POD).where(POD.ID.eq(UUID.fromString(id))).forShare().fetchOne()
                ?: return@transaction error(404, "not_found")
            val owner = UUID.fromString(principal.account.accountId)
            member(sql, row, owner)?.let { return@transaction it }
            if (row.draftAttemptId?.toString() != input.draftAttemptId) return@transaction error(409, "draft_attempt_expired")
            if (row.phase != "DRAFTING") return@transaction error(409, "not_drafting")
            val draft = state(row) ?: return@transaction error(409, "wrong_draft_mode")
            val player = owner.toString()
            if (draft.queues[player]?.firstOrNull() != input.packId) return@transaction error(409, "not_head_of_queue")
            val owned = draft.pools.getValue(player).toSet()
            val librarians = draft.copies.filter { it.librarian && it.id in owned }.map { it.id }.toSet()
            // Spent Librarians are in the pack for this pick, so they can be marked like its cards.
            val cards = draft.packs.first { it.id == input.packId }.cards.toSet() + input.librarians
            val selected = input.keeps + input.burns
            if (selected.distinct().size != selected.size || selected.any { it !in cards } ||
                input.librarians.distinct().size != input.librarians.size || input.librarians.any { it !in librarians })
                return@transaction error(400, "invalid_marks")
            sql.insertInto(MARKS).set(MARKS.POD_ID, row.id).set(MARKS.DRAFT_ATTEMPT_ID, row.draftAttemptId)
                .set(MARKS.ACCOUNT_ID, owner).set(MARKS.PACK_ID, input.packId).set(MARKS.KEEPS, encoded(input.keeps))
                .set(MARKS.BURNS, encoded(input.burns)).set(MARKS.LIBRARIANS, encoded(input.librarians))
                .set(MARKS.UPDATED_AT, DSL.currentOffsetDateTime())
                .onConflict(MARKS.POD_ID, MARKS.DRAFT_ATTEMPT_ID, MARKS.ACCOUNT_ID).doUpdate()
                .set(MARKS.PACK_ID, input.packId).set(MARKS.KEEPS, encoded(input.keeps))
                .set(MARKS.BURNS, encoded(input.burns)).set(MARKS.LIBRARIANS, encoded(input.librarians))
                .set(MARKS.UPDATED_AT, DSL.currentOffsetDateTime()).execute()
            Result(200, json.encodeToString(PodAcknowledged(row.id.toString())))
        }
    }

    private fun marks(sql: DSLContext, row: PodRecord, owner: UUID, head: DraftPrivatePack?): DraftMarks? {
        val attempt = row.draftAttemptId ?: return null
        head ?: return null
        val saved = sql.selectFrom(MARKS).where(MARKS.POD_ID.eq(row.id)).and(MARKS.DRAFT_ATTEMPT_ID.eq(attempt))
            .and(MARKS.ACCOUNT_ID.eq(owner)).fetchOne()?.takeIf { it.packId == head.id } ?: return null
        return stored(row) { DraftMarks(head.id, json.decodeFromString(saved.keeps!!.data()), json.decodeFromString(saved.burns!!.data()),
            json.decodeFromString(saved.librarians!!.data())) }
    }

    private fun clearMarks(sql: DSLContext, pod: UUID, owner: UUID? = null) {
        sql.deleteFrom(MARKS).where(MARKS.POD_ID.eq(pod))
            .and(if (owner == null) DSL.trueCondition() else MARKS.ACCOUNT_ID.eq(owner)).execute()
    }

    /** Stores the caller's private notes for the current attempt (latest wins), during the draft and after it, submitted or not. */
    fun saveNotes(principal: AccountService.Principal, id: String, input: SetPodDeckNotes): Result {
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        if (!DeckValidation.uuid(input.draftAttemptId)) return error(400, "invalid_request")
        if (!input.notes.valid) return error(400, "invalid_notes")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val row = sql.selectFrom(POD).where(POD.ID.eq(UUID.fromString(id))).forShare().fetchOne()
                ?: return@transaction error(404, "not_found")
            val owner = UUID.fromString(principal.account.accountId)
            member(sql, row, owner)?.let { return@transaction it }
            if (row.draftAttemptId?.toString() != input.draftAttemptId) return@transaction error(409, "draft_attempt_expired")
            sql.insertInto(NOTES).set(NOTES.POD_ID, row.id).set(NOTES.DRAFT_ATTEMPT_ID, row.draftAttemptId)
                .set(NOTES.ACCOUNT_ID, owner).set(NOTES.NOTE, input.notes.text).set(NOTES.COUNTERS, encoded(input.notes.counters))
                .set(NOTES.UPDATED_AT, DSL.currentOffsetDateTime())
                .onConflict(NOTES.POD_ID, NOTES.DRAFT_ATTEMPT_ID, NOTES.ACCOUNT_ID).doUpdate()
                .set(NOTES.NOTE, input.notes.text).set(NOTES.COUNTERS, encoded(input.notes.counters))
                .set(NOTES.UPDATED_AT, DSL.currentOffsetDateTime()).execute()
            Result(200, json.encodeToString(PodAcknowledged(row.id.toString())))
        }
    }

    private fun notes(sql: DSLContext, row: PodRecord, owner: UUID): PodDeckNotes? {
        val attempt = row.draftAttemptId ?: return null
        val saved = sql.selectFrom(NOTES).where(NOTES.POD_ID.eq(row.id)).and(NOTES.DRAFT_ATTEMPT_ID.eq(attempt))
            .and(NOTES.ACCOUNT_ID.eq(owner)).fetchOne() ?: return null
        return stored(row) { PodDeckNotes(saved.note!!, json.decodeFromString(saved.counters!!.data())) }
    }

    fun selectGrid(principal: AccountService.Principal, id: String, input: SelectPodGrid): Result =
        mutate(principal, id, input.requestId, "selectPodGrid", json.encodeToJsonElement(input), input.draftAttemptId) { sql, owner, row ->
            member(sql, row, owner)?.let { return@mutate it }
            if (row.phase != "DRAFTING") return@mutate error(409, "not_drafting")
            if (settings(row).mode != DraftMode.GRID) return@mutate error(409, "wrong_draft_mode")
            val previous = gridState(row)!!
            val result = GridEngine.pick(previous, GridPick(owner.toString(), input.gridId, GridLine(input.line, input.index)))
            if (result.reason != null) return@mutate error(409, result.reason!!.name.lowercase())
            advanceGrid(sql, row, previous, result.state!!)
            detail(sql, row, owner)
        }

    fun turn(principal: AccountService.Principal, id: String, input: PodTurn): Result =
        mutate(principal, id, input.requestId, "podTurn", json.encodeToJsonElement(input), input.draftAttemptId) { sql, owner, row ->
            member(sql, row, owner)?.let { return@mutate it }
            if (row.phase != "DRAFTING") return@mutate error(409, "not_drafting")
            if (!settings(row).mode.limited) return@mutate error(409, "wrong_draft_mode")
            val previous = limitedState(row)!!
            val result = LimitedEngine.act(previous, LimitedMove(owner.toString(), input.action, input.turn, input.card, input.packId, input.pile, input.table))
            if (result.reason != null) return@mutate error(409, result.reason!!.name.lowercase())
            advanceLimited(sql, row, previous, result.state!!)
            detail(sql, row, owner)
        }

    /** The caller's Rotisserie pick queue (DRF-164): private, kept with the draft; nothing is taken when it is set. */
    fun queue(principal: AccountService.Principal, id: String, input: PodPickQueue): Result =
        mutate(principal, id, input.requestId, "podPickQueue", json.encodeToJsonElement(input), input.draftAttemptId) { sql, owner, row ->
            member(sql, row, owner)?.let { return@mutate it }
            if (row.phase != "DRAFTING") return@mutate error(409, "not_drafting")
            if (settings(row).mode != DraftMode.ROTISSERIE) return@mutate error(409, "wrong_draft_mode")
            if (input.cards.size > 500) return@mutate error(400, "invalid_request")
            val result = LimitedEngine.queue(limitedState(row)!!, owner.toString(), input.cards)
            if (result.reason != null) return@mutate error(409, result.reason!!.name.lowercase())
            row.draftState = encoded(result.state!!)
            save(sql, row)
            detail(sql, row, owner)
        }

    fun move(principal: AccountService.Principal, id: String, input: MovePodCopy): Result =
        editBuild(principal, id, input.requestId, "movePodCopy", json.encodeToJsonElement(input), input.draftAttemptId) { build ->
            if (build.copies.none { it.copyId == input.copyId }) null
            else build.copy(copies = build.copies.map { if (it.copyId == input.copyId) it.copy(category = input.category) else it })
        }

    fun basics(principal: AccountService.Principal, id: String, input: SetPodBasics): Result =
        editBuild(principal, id, input.requestId, "setPodBasics", json.encodeToJsonElement(input), input.draftAttemptId) { build ->
            if (!input.basics.valid) null else if (input.side) build.copy(sideBasics = input.basics) else build.copy(basics = input.basics)
        }

    fun deckName(principal: AccountService.Principal, id: String, input: SetPodDeckName): Result =
        mutate(principal, id, input.requestId, "setPodDeckName", json.encodeToJsonElement(input), input.draftAttemptId) { sql, owner, row ->
            member(sql, row, owner)?.let { return@mutate it }
            if (row.phase !in listOf("DRAFTING", "DRAFTED")) return@mutate error(409, "not_drafting")
            if (!validName(input.name)) return@mutate error(400, "invalid_name")
            sql.update(MEMBER).set(MEMBER.DECK_NAME, input.name).where(MEMBER.POD_ID.eq(row.id))
                .and(MEMBER.ACCOUNT_ID.eq(owner)).execute()
            // The draft deck kept from this membership's submitted deck follows the private name.
            if (members(sql, row.id!!).first { it.accountId == owner }.submittedDeck != null)
                DraftDeckStore.rename(sql, owner, row.id!!, input.name)
            followInLobbies(sql, row.id!!, owner, name = input.name)
            save(sql, row); detail(sql, row, owner)
        }

    /** DRF-166: the private cover, chosen from the member's Main and Side cards, also after submitting. */
    fun deckCover(principal: AccountService.Principal, id: String, input: SetPodDeckCover): Result =
        mutate(principal, id, input.requestId, "setPodDeckCover", json.encodeToJsonElement(input), input.draftAttemptId) { sql, owner, row ->
            member(sql, row, owner)?.let { return@mutate it }
            if (row.phase !in listOf("DRAFTING", "DRAFTED")) return@mutate error(409, "not_drafting")
            val member = members(sql, row.id!!).first { it.accountId == owner }
            if (input.definition !in coverChoices(row, build(row, member.build!!.data()))) return@mutate error(400, "invalid_cover")
            sql.update(MEMBER).set(MEMBER.DECK_COVER, input.definition).where(MEMBER.POD_ID.eq(row.id))
                .and(MEMBER.ACCOUNT_ID.eq(owner)).execute()
            // The draft deck kept from this membership's submitted deck follows the cover, as it does the name.
            if (member.submittedDeck != null) DraftDeckStore.cover(sql, owner, row.id!!, input.definition)
            followInLobbies(sql, row.id!!, owner, cover = input.definition)
            save(sql, row); detail(sql, row, owner)
        }

    /**
     * DRF-105, DRF-166 (owner, 2026-11-11): the member's games of [pod] still in their lobby follow the private deck [name]
     * or [cover] (changed in Build or from Prepare deck in such a game's lobby, also after submitting): their
     * preparation's pod deck name and the prepared deck's cover (a card of its own), each game's change published as a
     * revision of it. Games in play or ended keep theirs. The pod lock is held; games are locked in identity order.
     */
    private fun followInLobbies(sql: DSLContext, pod: UUID, owner: UUID, name: String? = null, cover: String? = null) {
        val games = sql.selectFrom(GAME).where(GAME.POD_ID.eq(pod)).and(GAME.LIFECYCLE.eq("LOBBY"))
            .and(GAME.INVITER_ID.eq(owner).or(GAME.RECIPIENT_ID.eq(owner))).orderBy(GAME.ID).forUpdate().fetch()
        for (game in games) {
            val preparation = sql.selectFrom(PLAYER_PREPARATION).where(PLAYER_PREPARATION.GAME_ID.eq(game.id))
                .and(PLAYER_PREPARATION.ACCOUNT_ID.eq(owner)).fetchOne() ?: continue
            val deck = preparation.deck?.let { StoredData.decode("game preparation", game.id) { GameWire.json.decodeFromString<PreparedDeck>(it) } } ?: continue
            var changed = false
            if (name != null && preparation.podDeckName != name) {
                sql.update(PLAYER_PREPARATION).set(PLAYER_PREPARATION.POD_DECK_NAME, name).where(PLAYER_PREPARATION.GAME_ID.eq(game.id))
                    .and(PLAYER_PREPARATION.ACCOUNT_ID.eq(owner)).execute()
                changed = true
            }
            if (cover != null && deck.cover != cover && deck.entries.any { it.definition == cover }) {
                sql.update(PLAYER_PREPARATION).set(PLAYER_PREPARATION.DECK, GameWire.json.encodeToString(deck.copy(cover = cover)))
                    .where(PLAYER_PREPARATION.GAME_ID.eq(game.id)).and(PLAYER_PREPARATION.ACCOUNT_ID.eq(owner)).execute()
                changed = true
            }
            if (!changed) continue
            sql.update(GAME).set(GAME.REVISION, game.revision!! + 1).set(GAME.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(GAME.ID.eq(game.id)).execute()
            val summary = LobbyService(runtime).summary(sql, game.id!!)
            val revision = GameRevision(summary.id, game.revision.toString(), summary.revision, UUID.randomUUID().toString(),
                owner.toString(), GameScope(summary.id, summary.preparationRound), summary, PreparationStore.read(sql, summary))
            sql.insertInto(GAME_REVISION).set(GAME_REVISION.GAME_ID, game.id).set(GAME_REVISION.REVISION, game.revision!! + 1)
                .set(GAME_REVISION.PAYLOAD, GameWire.encode(revision)).execute()
        }
    }

    private fun coverChoices(row: PodRecord, build: PodDeckBuild): List<String> {
        val definitions = copies(row).associate { it.id to it.definition }
        return PodDeckRules.coverChoices(build, definitions::get)
    }

    private fun editBuild(principal: AccountService.Principal, id: String, request: String, kind: String, input: JsonElement,
        attempt: String, edit: (PodDeckBuild) -> PodDeckBuild?): Result = mutate(principal, id, request, kind, input, attempt) { sql, owner, row ->
        member(sql, row, owner)?.let { return@mutate it }
        if (row.phase !in listOf("DRAFTING", "DRAFTED")) return@mutate error(409, "not_drafting")
        val member = members(sql, row.id!!).first { it.accountId == owner }
        if (member.submittedDeck != null) return@mutate error(409, "deck_submitted")
        val build = build(row, member.build!!.data())
        val next = edit(build) ?: return@mutate error(400, "invalid_build")
        sql.update(MEMBER).set(MEMBER.BUILD, encoded(next)).where(MEMBER.POD_ID.eq(row.id)).and(MEMBER.ACCOUNT_ID.eq(owner)).execute()
        save(sql, row); detail(sql, row, owner)
    }

    fun submit(principal: AccountService.Principal, id: String, input: SubmitPodDeck): Result =
        mutate(principal, id, input.requestId, "submitPod", json.encodeToJsonElement(input), input.draftAttemptId) { sql, owner, row ->
            member(sql, row, owner)?.let { return@mutate it }
            if (row.phase != "DRAFTED") return@mutate error(409, "draft_not_complete")
            val member = members(sql, row.id!!).first { it.accountId == owner }
            if (member.submittedDeck != null) return@mutate error(409, "deck_submitted")
            val build = build(row, member.build!!.data())
            val pool = pools(row).getValue(owner.toString())
            val issues = PodDeckRules.validate(build, pool, settings(row).sideboardLimit)
            if (issues.isNotEmpty()) return@mutate error(400, issues.first().name.lowercase())
            // LOB-141: Maybe goes to the pod's games too, outside the game with Side
            sql.update(MEMBER).set(MEMBER.SUBMITTED_DECK, encoded(build)).where(MEMBER.POD_ID.eq(row.id)).and(MEMBER.ACCOUNT_ID.eq(owner)).execute()
            // LOB-052: the server's basics (none when its catalog lacks them: the draft deck is kept without them)
            val basics = ServerCatalogs.current()?.basics?.also { storeDefinitions(sql, it.values.toList()) }
            DraftDeckStore.entries(build, pool, copies(row).associate { it.id to it.definition }, basics?.mapValues { it.value.digest }).let { entries ->
                DraftDeckStore.save(sql, owner, row.id!!, row.name!!, member.deckName ?: "${row.name} draft", entries,
                    member.deckCover?.takeIf { it in coverChoices(row, build) })
            }
            releaseChallenges(sql, row.id!!, owner)
            if (members(sql, row.id!!).all { it.submittedDeck != null }) {
                // I-D57: one notice per thing that needs you: a member with a challenge of this pod queued (sent or received,
                // not yet played) hears of it from the challenge, not from "Decks ready" too
                val challenged = queuedChallengers(sql, row.id!!)
                for (recipient in members(sql, row.id!!)) if (recipient.accountId !in challenged) ActivityService.record(sql,
                    recipient.accountId!!, ActivityKind.POD_DECKS_READY, row.id!!, row.draftAttemptId!!, owner)
            }
            save(sql, row); detail(sql, row, owner)
        }

    fun cancel(principal: AccountService.Principal, id: String, input: PodDraftRequest): Result =
        mutate(principal, id, input.requestId, "cancelPodDraft", json.encodeToJsonElement(input), input.draftAttemptId) { sql, owner, row ->
            creator(row, owner)?.let { return@mutate it }
            if (row.phase != "DRAFTING") return@mutate error(409, "not_drafting")
            reset(sql, row, owner)
            detail(sql, row, owner)
        }

    fun challenge(principal: AccountService.Principal, id: String, input: ChallengeFromPod): Result {
        // LOB-052: the basic lands come from the server's catalog; without them no pod game starts (not kept as the
        // request's outcome: a retry may find them)
        val basics = ServerCatalogs.current()?.basics ?: return error(503, "basic_definitions_unavailable")
        return mutate(principal, id, input.requestId, "challengeFromPod", json.encodeToJsonElement(input)) { sql, owner, row ->
            member(sql, row, owner)?.let { return@mutate it }
            if (!LobbyValidation.name(input.gameName)) return@mutate error(400, "invalid_name")
            if (!DeckValidation.uuid(input.opponentId) || input.opponentId == owner.toString())
                return@mutate error(400, "invalid_opponent")
            val players = members(sql, row.id!!).associateBy { it.accountId.toString() }
            if (input.opponentId !in players) return@mutate error(404, "opponent_not_member")
            // Only the challenger's deck must be submitted; an opponent still building receives the invitation on submit.
            if (row.phase != "DRAFTED" || players.getValue(owner.toString()).submittedDeck == null)
                return@mutate error(409, "deck_not_submitted")
            // I-D52 (pivot review #2): its decks come from the source; a source Automatum can't play fully starts no game
            unsupported(row)?.let { return@mutate it }
            val held = players.getValue(input.opponentId).submittedDeck == null
            val build = build(row, players.getValue(owner.toString()).submittedDeck!!.data())
            // Match the existing tabletop's bounded physical deck representation (submission enforces it too).
            if (build.copies.size + build.basics.total + build.sideBasics.total > DeckValidation.MAX_CARDS)
                return@mutate error(400, "invalid_deck")
            val game = UUID.randomUUID(); val invitation = UUID.randomUUID()
            sql.insertInto(GAME).set(GAME.ID, game).set(GAME.GAME_NAME, input.gameName).set(GAME.INVITER_ID, owner)
                .set(GAME.RECIPIENT_ID, UUID.fromString(input.opponentId)).set(GAME.PREPARATION_ROUND, UUID.randomUUID())
                .set(GAME.CURRENT_INVITATION, invitation).set(GAME.POD_ID, row.id).set(GAME.POD_NAME, row.name)
                .set(GAME.POD_SIDEBOARD_LIMIT, settings(row).sideboardLimit).set(GAME.POD_INVITATION_HELD, held).execute()
            sql.insertInto(INVITATION).set(INVITATION.ID, invitation).set(INVITATION.GAME_ID, game).execute()
            val lobby = LobbyService(runtime)
            val summary = lobby.summary(sql, game)
            // The recipient's locked deck remains pod-private until they accept the invitation.
            storeDefinitions(sql, basics.values.toList())
            saveChallengeDeck(sql, row, summary, owner.toString(), build, basics.mapValues { it.value.digest }, basics.values.toList(), input.requestId)
            // DRF-137: the recipient hears of it at once, also while still building (they accept once they submit)
            ActivityService.record(sql, UUID.fromString(input.opponentId), ActivityKind.INVITATION, game, invitation, owner)
            Result(201, json.encodeToString(summary))
        }
    }

    /** The players of [pod]'s queued challenges (I-D57): games of the pod still in their lobby, never played, whose
     * invitation is pending or accepted. */
    private fun queuedChallengers(sql: DSLContext, pod: UUID): Set<UUID> {
        val plays = org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY
        return sql.select(GAME.INVITER_ID, GAME.RECIPIENT_ID).from(GAME).join(INVITATION).on(INVITATION.ID.eq(GAME.CURRENT_INVITATION))
            .where(GAME.POD_ID.eq(pod)).and(GAME.LIFECYCLE.eq("LOBBY")).and(INVITATION.STATE.`in`("PENDING", "ACCEPTED"))
            .andNotExists(DSL.selectOne().from(plays).where(plays.GAME_ID.eq(GAME.ID))).fetch()
            .flatMap { listOfNotNull(it.value1(), it.value2()) }.toSet()
    }

    /** Challenges sent while [recipient] was still building become ordinary pending invitations once they submit; their
     * notice went at once when they were sent (DRF-137), so this one adds none. */
    private fun releaseChallenges(sql: DSLContext, pod: UUID, recipient: UUID) {
        // Pod lock is held; lock games in identity order like withdrawal.
        val games = sql.selectFrom(GAME).where(GAME.POD_ID.eq(pod)).and(GAME.RECIPIENT_ID.eq(recipient))
            .and(GAME.LIFECYCLE.eq("LOBBY")).and(GAME.POD_INVITATION_HELD.isTrue).orderBy(GAME.ID).forUpdate().fetch()
        for (row in games) {
            sql.update(GAME).set(GAME.POD_INVITATION_HELD, false).set(GAME.REVISION, row.revision!! + 1)
                .set(GAME.UPDATED_AT, DSL.currentOffsetDateTime()).where(GAME.ID.eq(row.id)).execute()
            val summary = LobbyService(runtime).summary(sql, row.id!!)
            val revision = GameRevision(summary.id, row.revision.toString(), summary.revision, UUID.randomUUID().toString(),
                recipient.toString(), GameScope(summary.id, summary.preparationRound), summary, PreparationStore.read(sql, summary))
            sql.insertInto(GAME_REVISION).set(GAME_REVISION.GAME_ID, row.id).set(GAME_REVISION.REVISION, row.revision!! + 1)
                .set(GAME_REVISION.PAYLOAD, GameWire.encode(revision)).execute()
        }
    }

    /** Called under the game lock, in the same transaction as invitation acceptance. */
    internal fun acceptChallenge(sql: DSLContext, summary: GameSummary, requestId: String) {
        val pod = summary.podId?.let(UUID::fromString) ?: return
        // Withdrawal also locks this game before removing the pod/member. Do not take the pod
        // lock here (pod mutations lock pod then game); submitted decks are immutable.
        val row = sql.selectFrom(POD).where(POD.ID.eq(pod)).fetchSingle()
        val player = summary.recipient.accountId
        val submitted = sql.select(MEMBER.SUBMITTED_DECK).from(MEMBER).where(MEMBER.POD_ID.eq(pod))
            .and(MEMBER.ACCOUNT_ID.eq(UUID.fromString(player))).fetchSingle(MEMBER.SUBMITTED_DECK)!!
        val preparation = PreparationStore.read(sql, summary)
        val basics = preparation.players.single { it.accountId == summary.inviter.accountId }.deck!!.basicDefinitions
        saveChallengeDeck(sql, row, summary, player, build(row, submitted.data()), basics,
            preparation.definitions.filter { it.digest in basics.values }, requestId)
    }

    private fun saveChallengeDeck(sql: DSLContext, row: PodRecord, summary: GameSummary, player: String,
        build: PodDeckBuild, basics: Map<BasicLand, String>, basicDefinitions: List<SubmittedDefinition>, requestId: String) {
        val copies = copies(row).associateBy { it.id }
        val selection = CommandIds.uuidV5(requestId, "pod-deck/$player")
        var index = 0L
        // LOB-141: Maybe comes along as the deck's Maybe (outside the game with Side)
        val entries = build.copies.map { copy ->
            PreparedEntry(copies.getValue(copy.copyId).definition, when (copy.category) {
                PodDeckCategory.MAIN -> SavedDeckSection.MAINBOARD
                PodDeckCategory.SIDE -> SavedDeckSection.SIDEBOARD
                PodDeckCategory.MAYBE -> SavedDeckSection.MAYBEBOARD
            }, (index++).toString(), 1)
        } + listOf(SavedDeckSection.MAINBOARD to build.basics, SavedDeckSection.SIDEBOARD to build.sideBasics).flatMap { (section, counts) ->
            BasicLand.entries.mapNotNull { land -> land.count(counts).takeIf { it > 0 }?.let { count ->
                PreparedEntry(basics.getValue(land), section, index.toString(), count, land).also { index += count }
            } }
        }
        // DRF-166: the member's chosen cover while it is still in Main or Side, else the first card as before
        val chosenCover = sql.select(MEMBER.DECK_COVER).from(MEMBER).where(MEMBER.POD_ID.eq(row.id))
            .and(MEMBER.ACCOUNT_ID.eq(UUID.fromString(player))).fetchSingle(MEMBER.DECK_COVER)
            ?.takeIf { it in coverChoices(row, build) }
        val deck = PreparedDeck(CommandIds.output(selection, "prepared-deck", 0), row.id.toString(), "1",
            row.name!!, chosenCover ?: entries.firstOrNull()?.definition, selection, entries, basics, index.toString())
        val definitions = PreparationStore.definitions(sql, entries.map { it.definition }.toSet()) + basicDefinitions
        PreparationStore.saveSelection(sql, UUID.fromString(player), LobbyPreparation(summary.id, summary.preparationRound,
            listOf(PlayerPreparation(player, deck = deck)), definitions.distinctBy { it.digest }))
        val name = sql.select(MEMBER.DECK_NAME).from(MEMBER).where(MEMBER.POD_ID.eq(row.id))
            .and(MEMBER.ACCOUNT_ID.eq(UUID.fromString(player))).fetchSingle(MEMBER.DECK_NAME) ?: "${row.name} deck"
        sql.update(PLAYER_PREPARATION).set(PLAYER_PREPARATION.POD_DECK_NAME, name)
            .where(PLAYER_PREPARATION.GAME_ID.eq(UUID.fromString(summary.id)))
            .and(PLAYER_PREPARATION.ACCOUNT_ID.eq(UUID.fromString(player))).execute()
        // A pod deck is the player's deck from this pod: the pod id is its stable identity.
        org.asyncttwiz.server.game.DeckVisibilityStore.apply(sql, summary, UUID.fromString(player), row.id.toString())
    }

    fun leave(principal: AccountService.Principal, id: String, input: PodRequest): Result =
        mutate(principal, id, input.requestId, "leavePod", json.encodeToJsonElement(input)) { sql, owner, row ->
            member(sql, row, owner)?.let { return@mutate it }
            if (row.creatorId == owner) return@mutate error(403, "creator_cannot_leave")
            // Leave concerns membership, not an attempt: cancel whichever draft is current.
            // Record cancellation for the original membership, including the departing member.
            if (row.phase == "DRAFTING") reset(sql, row, owner)
            withdrawChallenges(sql, row.id!!, owner)
            sql.deleteFrom(MEMBER).where(MEMBER.POD_ID.eq(row.id)).and(MEMBER.ACCOUNT_ID.eq(owner)).execute()
            if (row.phase == "DRAFTED" && members(sql, row.id!!).size == 1) destroy(sql, row, owner)
            else save(sql, row)
            acknowledged(row)
        }

    fun destroy(principal: AccountService.Principal, id: String, input: PodRequest): Result =
        mutate(principal, id, input.requestId, "destroyPod", json.encodeToJsonElement(input)) { sql, owner, row ->
            creator(row, owner)?.let { return@mutate it }
            destroy(sql, row, owner); acknowledged(row)
        }

    private fun destroy(sql: DSLContext, row: PodRecord, author: UUID) {
        withdrawChallenges(sql, row.id!!)
        clearDraftHints(sql, row.id!!)
        notify(sql, row, ActivityKind.POD_DESTROYED, author)
        sql.deleteFrom(POD).where(POD.ID.eq(row.id)).execute()
    }

    private fun withdrawChallenges(sql: DSLContext, pod: UUID, departed: UUID? = null) {
        // Lock games in identity order; an acceptance holding the game lock wins and is never undone.
        val games = sql.selectFrom(GAME).where(GAME.POD_ID.eq(pod)).and(GAME.LIFECYCLE.eq("LOBBY"))
            .and(if (departed == null) DSL.trueCondition() else GAME.INVITER_ID.eq(departed).or(GAME.RECIPIENT_ID.eq(departed)))
            .orderBy(GAME.ID).forUpdate().fetch()
        for (row in games) {
            if (sql.select(INVITATION.STATE).from(INVITATION).where(INVITATION.ID.eq(row.currentInvitation)).fetchSingle(INVITATION.STATE) == "ACCEPTED") continue
            sql.update(GAME).set(GAME.LIFECYCLE, "CANCELLED").set(GAME.REVISION, row.revision!! + 1)
                .set(GAME.UPDATED_AT, DSL.currentOffsetDateTime()).where(GAME.ID.eq(row.id)).execute()
            val summary = LobbyService(runtime).summary(sql, row.id!!)
            val revision = GameRevision(summary.id, row.revision.toString(), summary.revision, UUID.randomUUID().toString(),
                (departed ?: row.inviterId).toString(), GameScope(summary.id, summary.preparationRound), summary)
            sql.insertInto(GAME_REVISION).set(GAME_REVISION.GAME_ID, row.id).set(GAME_REVISION.REVISION, row.revision!! + 1)
                .set(GAME_REVISION.PAYLOAD, GameWire.encode(revision)).execute()
        }
    }

    private fun reset(sql: DSLContext, row: PodRecord, author: UUID) {
        clearDraftHints(sql, row.id!!)
        clearMarks(sql, row.id!!)
        sql.deleteFrom(NOTES).where(NOTES.POD_ID.eq(row.id)).execute()
        notify(sql, row, ActivityKind.POD_DRAFT_CANCELLED, author)
        row.phase = "SETUP"; row.draftAttemptId = null; row.draftState = null
        sql.update(MEMBER).set(MEMBER.BUILD, encoded(PodDeckBuild())).set(MEMBER.SUBMITTED_DECK, null as JSONB?)
            .set(MEMBER.DECK_NAME, null as String?).set(MEMBER.DECK_COVER, null as String?)
            .where(MEMBER.POD_ID.eq(row.id)).execute()
        save(sql, row)
    }

    private fun advance(sql: DSLContext, row: PodRecord, previous: DraftState?, next: DraftState) {
        row.draftState = encoded(next); row.phase = if (next.finished) "DRAFTED" else "DRAFTING"
        for (member in members(sql, row.id!!)) {
            val owner = member.accountId!!; val player = owner.toString()
            val build = build(row, member.build!!.data())
            val initialSide = if (previous == null) next.copies.filter { it.librarian && it.id.startsWith("librarian:") }.map { it.id }.toSet() else emptySet()
            sql.update(MEMBER).set(MEMBER.BUILD, encoded(PodDeckRules.withDraftPool(build, next.pools.getValue(player), initialSide)))
                .where(MEMBER.POD_ID.eq(row.id)).and(MEMBER.ACCOUNT_ID.eq(owner)).execute()
            // Notify when a pack arrives at an empty queue. The picker's own just-picked pack does not count as waiting,
            // so their next-round packs opening (possibly early) right after their pick notify them too.
            val queue = next.queues.getValue(player)
            val hadPacks = previous?.queues?.get(player).orEmpty().any { it in queue }
            if (!hadPacks && queue.isNotEmpty())
                ActivityService.record(sql, owner, ActivityKind.POD_YOUR_PICK, row.id!!, row.draftAttemptId!!)
        }
        if (next.finished && previous?.finished != true) {
            clearDraftHints(sql, row.id!!)
            clearMarks(sql, row.id!!)
            notify(sql, row, ActivityKind.POD_DRAFT_COMPLETE)
        }
        save(sql, row)
    }

    private fun advanceGrid(sql: DSLContext, row: PodRecord, previous: GridState?, next: GridState) {
        row.draftState = encoded(next); row.phase = if (next.finished) "DRAFTED" else "DRAFTING"
        for (member in members(sql, row.id!!)) {
            val owner = member.accountId!!; val player = owner.toString()
            val build = build(row, member.build!!.data())
            sql.update(MEMBER).set(MEMBER.BUILD, encoded(PodDeckRules.withAwards(build, next.pools.getValue(player))))
                .where(MEMBER.POD_ID.eq(row.id)).and(MEMBER.ACCOUNT_ID.eq(owner)).execute()
            // More simultaneous turns do not notify a member who already has an actionable grid. As with packs, the
            // picker's just-picked grid does not count, so their next round's grids opening early notify them too.
            val queue = next.queues[player].orEmpty()
            val hadGrids = previous?.queues?.get(player).orEmpty().any { it in queue }
            if (!hadGrids && queue.isNotEmpty())
                ActivityService.record(sql, owner, ActivityKind.POD_YOUR_PICK, row.id!!, row.draftAttemptId!!)
        }
        if (next.finished && previous?.finished != true) {
            clearDraftHints(sql, row.id!!)
            notify(sql, row, ActivityKind.POD_DRAFT_COMPLETE)
        }
        save(sql, row)
    }

    private fun advanceLimited(sql: DSLContext, row: PodRecord, previous: LimitedDraftState?, next: LimitedDraftState) {
        row.draftState = encoded(next); row.phase = if (next.finished) "DRAFTED" else "DRAFTING"
        for (member in members(sql, row.id!!)) {
            val owner = member.accountId!!; val player = owner.toString()
            val build = build(row, member.build!!.data())
            sql.update(MEMBER).set(MEMBER.BUILD, encoded(PodDeckRules.withAwards(build, next.pools.getValue(player))))
                .where(MEMBER.POD_ID.eq(row.id)).and(MEMBER.ACCOUNT_ID.eq(owner)).execute()
            // "Your pick" when it becomes the member's turn (Rochester: a pack's pick) while none was waiting.
            val queue = next.queues[player].orEmpty()
            // Rotisserie: a turn that comes back after others' picks (queued picks taken at once, DRF-164) is a new turn.
            val othersPicked = next.settings.mode == DraftMode.ROTISSERIE && previous != null &&
                next.log.drop(previous.log.size).any { it.player != player }
            val hadTurn = !othersPicked && previous?.queues?.get(player).orEmpty().any { it in queue }
            if (!hadTurn && queue.isNotEmpty())
                ActivityService.record(sql, owner, ActivityKind.POD_YOUR_PICK, row.id!!, row.draftAttemptId!!)
        }
        if (next.finished && previous?.finished != true) {
            clearDraftHints(sql, row.id!!)
            notify(sql, row, ActivityKind.POD_DRAFT_COMPLETE)
        }
        save(sql, row)
    }

    private fun clearDraftHints(sql: DSLContext, id: UUID) {
        val summary = org.asyncttwiz.server.generated.tables.ActivitySummary.ACTIVITY_SUMMARY
        val push = org.asyncttwiz.server.generated.tables.ActivityPush.ACTIVITY_PUSH
        val kinds = listOf("POD_YOUR_PICK", "POD_DRAFT_COMPLETE", "POD_DECKS_READY", "POD_INVITATION")
        sql.deleteFrom(summary).where(summary.GAME_ID.eq(id)).and(summary.KIND.`in`(kinds)).execute()
        sql.deleteFrom(push).where(push.GAME_ID.eq(id)).and(push.KIND.`in`(kinds)).execute()
    }

    private fun notify(sql: DSLContext, row: PodRecord, kind: ActivityKind, author: UUID? = null) {
        for (member in members(sql, row.id!!)) ActivityService.record(sql, member.accountId!!, kind, row.id!!,
            row.draftAttemptId ?: UUID.randomUUID(), author)
    }
    private fun save(sql: DSLContext, row: PodRecord) {
        row.updatedAt = sql.update(POD).set(POD.NAME, row.name).set(POD.PHASE, row.phase).set(POD.SETTINGS, row.settings)
            .set(POD.SOURCE_NAME, row.sourceName).set(POD.SOURCE_SNAPSHOT, row.sourceSnapshot).set(POD.SOURCE_MAYBE, row.sourceMaybe)
            .set(POD.AUTOMATUM_SUPPORT, row.automatumSupport)
            .set(POD.DRAFT_ATTEMPT_ID, row.draftAttemptId).set(POD.DRAFT_STATE, row.draftState)
            .set(POD.AUTO_START, row.autoStart).set(POD.AUTO_START_OFF_REASON, row.autoStartOffReason)
            .set(POD.AUTO_START_OFF_ACCOUNT, row.autoStartOffAccount)
            .set(POD.UPDATED_AT, DSL.currentOffsetDateTime()).where(POD.ID.eq(row.id)).returning(POD.UPDATED_AT).fetchSingle().updatedAt
    }
    private fun creator(row: PodRecord, owner: UUID): Result? = if (row.creatorId != owner) error(403, "creator_only") else null
    private fun member(sql: DSLContext, row: PodRecord, owner: UUID): Result? =
        if (members(sql, row.id!!).none { it.accountId == owner }) error(404, "not_found") else null
    private fun acknowledged(row: PodRecord) = Result(200, json.encodeToString(PodAcknowledged(row.id.toString())))

    fun read(principal: AccountService.Principal, id: String): Result {
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val row = sql.selectFrom(POD).where(POD.ID.eq(UUID.fromString(id))).forShare().fetchOne()
                ?: return@transaction error(404, "not_found")
            val owner = UUID.fromString(principal.account.accountId)
            val members = members(sql, row.id!!)
            if (members.none { it.accountId == owner }) error(404, "not_found") else detail(sql, row, owner, members = members)
        }
    }

    /**
     * Pod chat (owner, 2026-11-08): only the pod's members read it, write in it and move their read cursor in it; anyone
     * else, a member who left included, gets not_found as for the pod itself. [lock]: the pod row's lock, exclusive for a
     * send (ordinals follow commits).
     */
    private fun chatMember(principal: AccountService.Principal, id: String, exclusive: Boolean = false,
        action: (DSLContext, UUID, UUID) -> Result): Result {
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val select = sql.selectFrom(POD).where(POD.ID.eq(UUID.fromString(id)))
            val row = (if (exclusive) select.forUpdate() else select.forShare()).fetchOne() ?: return@transaction error(404, "not_found")
            val owner = UUID.fromString(principal.account.accountId)
            if (members(sql, row.id!!).none { it.accountId == owner }) error(404, "not_found") else action(sql, row.id!!, owner)
        }
    }

    /** A page of the pod's chat: the newest, older than [before] or newer than [after] (ordinals), with the caller's read state. */
    fun chat(principal: AccountService.Principal, id: String, before: String?, after: String?): Result {
        val older = before?.let { it.toLongOrNull()?.takeIf { n -> n > 0 } ?: return error(400, "invalid_request") }
        val newer = after?.let { it.toLongOrNull()?.takeIf { n -> n >= 0 } ?: return error(400, "invalid_request") }
        if (older != null && newer != null) return error(400, "invalid_request")
        return chatMember(principal, id) { sql, pod, owner ->
            Result(200, json.encodeToString(PodChatStore.page(sql, pod, owner, older, newer)))
        }
    }

    /** Sends a message to the pod's chat; the reply is the stored message. Never logs its text (as a game's chat). */
    fun sendChat(principal: AccountService.Principal, id: String, input: SendPodChat): Result {
        if (!DeckValidation.uuid(input.requestId)) return error(400, "invalid_request")
        return chatMember(principal, id, exclusive = true) { sql, pod, owner ->
            val sent = PodChatStore.send(sql, pod, owner, UUID.fromString(input.requestId), input.text)
            val message = sent.message
            if (message == null) {
                log("chat $pod: message ${input.requestId} by $owner refused: ${sent.refusal} (${input.text.length} chars)")
                error(if (sent.refusal == "request_conflict") 409 else 400, sent.refusal!!)
            } else {
                log("chat $pod: message ${message.id} #${message.ordinal} by $owner, ${message.text.length} chars" +
                    if (sent.repeated) " (retried, already stored)" else "")
                Result(200, json.encodeToString(message))
            }
        }
    }

    /** The caller's read state of the pod's chat; with [input], their cursor moved forward to that message first. */
    fun chatRead(principal: AccountService.Principal, id: String, input: ReadChat?): Result {
        if (input != null && !DeckValidation.uuid(input.messageId)) return error(400, "invalid_request")
        return chatMember(principal, id) { sql, pod, owner ->
            if (input == null) Result(200, json.encodeToString(PodChatStore.readState(sql, pod, owner)))
            else {
                val state = PodChatStore.read(sql, pod, owner, UUID.fromString(input.messageId)) ?: return@chatMember error(404, "message_not_found")
                log("chat $pod: $owner read through #${state.readThrough} (message ${input.messageId}), ${state.unread} unread")
                Result(200, json.encodeToString(state))
            }
        }
    }

    /** The caller's Booster or Glimpse draft history (DRF-165) with its cards' definitions; only what their packs showed. */
    fun history(principal: AccountService.Principal, id: String): Result {
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val row = sql.selectFrom(POD).where(POD.ID.eq(UUID.fromString(id))).forShare().fetchOne()
                ?: return@transaction error(404, "not_found")
            val owner = UUID.fromString(principal.account.accountId)
            if (members(sql, row.id!!).none { it.accountId == owner }) return@transaction error(404, "not_found")
            val state = draft(row, settings(row)).booster ?: return@transaction error(409, "wrong_draft_mode")
            val picks = DraftEngine.history(state, owner.toString())
            val digests = picks.flatMap { it.cards + it.takenSince }.map { it.definition }.toSet()
            // DRF-165, DRF-043: the seating the history's round headers and seat ring show
            log("history $id for $owner: ${picks.size} picks, seating ${state.seating}")
            Result(200, json.encodeToString(PodDraftHistory(picks, StoredDefinitions.read(sql, digests), state.seating)))
        }
    }

    /** The app's shared pod order (DRF-013): your pick or build longest waiting first, then the rest newest first. */
    private val podListOrder = Comparator<PodSummary> { a, b ->
        val waiting = (a.attention == PodAttention.NONE).compareTo(b.attention == PodAttention.NONE)
        val age = java.time.Instant.parse(a.updatedAt).compareTo(java.time.Instant.parse(b.updatedAt))
        when {
            waiting != 0 -> waiting
            age != 0 -> if (a.attention == PodAttention.NONE) -age else age
            else -> a.id.compareTo(b.id)
        }
    }

    fun list(principal: AccountService.Principal): Result = runtime.transaction { connection ->
        val sql = DSL.using(connection, SQLDialect.POSTGRES)
        val owner = UUID.fromString(principal.account.accountId)
        // DRF-012: each pod's unread chat messages, read with its row
        val unread = PodChatStore.unreadField(POD.ID, owner)
        val found = sql.select(POD.asterisk(), unread).from(POD)
            .where(POD.ID.`in`(sql.select(MEMBER.POD_ID).from(MEMBER).where(MEMBER.ACCOUNT_ID.eq(owner))))
            .orderBy(POD.ID).forShare().of(POD).fetch()
        val rows = found.map { it.into(POD) }
        val chatUnread = found.associate { it[POD.ID]!! to it[unread] }
        // Membership is rechecked after the pod locks: a concurrent leave may have removed the caller.
        // A pod whose stored draft can't be read is left out; opening it fails on its own (SYS-052).
        val memberships = if (rows.isEmpty()) emptyMap() else sql.selectFrom(MEMBER).where(MEMBER.POD_ID.`in`(rows.map { it.id }))
            .orderBy(MEMBER.ACCOUNT_ID).fetch().groupBy { it.podId!! }
        val invited = sql.select(INVITE.POD_ID, INVITE.INVITED_AT, POD.NAME, POD.CREATOR_ID).from(INVITE).join(POD).on(POD.ID.eq(INVITE.POD_ID))
            .where(INVITE.ACCOUNT_ID.eq(owner)).and(INVITE.STATE.eq("PENDING")).and(POD.PHASE.eq("SETUP"))
            .orderBy(INVITE.INVITED_AT.desc(), INVITE.POD_ID).fetch()
        val names = identities(sql, rows.map { it.creatorId!! } + memberships.values.flatten().map { it.accountId!! } +
            invited.map { it[POD.CREATOR_ID]!! })
        val pods = rows.mapNotNull { row -> StoredData.orSkip("pod", row.id) {
            val members = memberships[row.id].orEmpty()
            val mine = members.firstOrNull { it.accountId == owner } ?: return@orSkip null
            val draft = draft(row)
            val waiting = draft.booster?.let { DraftEngine.consecutivePicks(it, owner.toString()) }
                ?: draft.limited?.let { LimitedEngine.consecutivePicks(it, owner.toString()) } ?: gridWaiting(draft.grid, owner.toString())
            val attention = when {
                waiting > 0 -> PodAttention.YOUR_PICK
                row.phase == "DRAFTED" && mine.submittedDeck == null -> PodAttention.BUILD
                else -> PodAttention.NONE
            }
            PodSummary(row.id.toString(), row.name!!, PodPhase.valueOf(row.phase!!), names.getValue(row.creatorId!!),
                members.map { names.getValue(it.accountId!!) }, attention, waiting, row.updatedAt!!.toInstant().toString(),
                settings(row).mode, chatUnread[row.id] ?: 0)
        } }.sortedWith(podListOrder)
        val invitations = invited.map {
            PodInvitation(it[INVITE.POD_ID].toString(), it[POD.NAME]!!, names.getValue(it[POD.CREATOR_ID]!!), it[INVITE.INVITED_AT]!!.toInstant().toString())
        }
        Result(200, json.encodeToString(PodList(pods, invitations)))
    }

    /** Booster cards are private; grid boards/log are public, but pools/builds remain caller-only.
     * Each stored part is decoded once, and every named account is looked up in one query. */
    private fun detail(sql: DSLContext, row: PodRecord, owner: UUID, status: Int = 200,
        members: List<org.asyncttwiz.server.generated.tables.records.PodMemberRecord> = members(sql, row.id!!)): Result {
        val settings = settings(row)
        val stored = draft(row, settings)
        val draft = stored.booster
        val view = draft?.let { DraftEngine.privateView(it, owner.toString()) }?.let { it.copy(marks = marks(sql, row, owner, it.head)) }
        val public = draft?.let(DraftEngine::publicStatus)
        val grid = stored.grid
        val gridView = grid?.let { PodGridView(GridEngine.publicView(it, owner.toString()), GridEngine.privateView(it, owner.toString())) }
        val limited = stored.limited
        val limitedView = limited?.let { LimitedEngine.view(it, owner.toString()) }
        val mine = members.first { it.accountId == owner }
        val visible = view?.head?.cards.orEmpty() + view?.queuedPacks.orEmpty().flatMap { it.cards } +
            view?.pool.orEmpty() + gridView?.mine?.pool.orEmpty() +
            (gridView?.publicView?.grids.orEmpty() + gridView?.publicView?.finishedGrids.orEmpty())
                .flatMap { it.positions.filterNotNull() + it.slots.mapNotNull { slot -> slot.copy } } +
            gridView?.publicView?.log.orEmpty().flatMap { it.kept + it.discarded } + limitedView.cards()
        val cards = StoredDefinitions.read(sql, visible.map { it.definition }.toSet())
        val set = setSource(row)
        val cube = source(row)
        val sourceView = if (set == null) PodSource(row.sourceName!!, cube.sumOf { it.quantity.toLong() },
            maybeCopies = maybe(row).sumOf { it.quantity.toLong() }) else
            PodSource(kind = PodSourceKind.SET, setCode = set.first.setCode, setName = set.first.setName,
                boosterKind = if (set.sets.any { it.recipe == null }) SetBoosterKind.APPROXIMATE else SetBoosterKind.RECIPE_BASED,
                boosterName = set.first.recipe?.name, packSets = set.packs)
        val finite = settings.supply(members.size, cube)
        val supply = if (!isSetSource(row)) finite else finite.copy(available = Long.MAX_VALUE)
        val pending = pending(sql, row.id!!)
        val offAccount = row.autoStartOffAccount?.takeIf { row.autoStartOffReason != null && row.phase == "SETUP" && owner == row.creatorId }
        val names = identities(sql, listOf(row.creatorId!!) + members.map { it.accountId!! } + pending.map { it.accountId!! } + listOfNotNull(offAccount))
        val result = PodDetail(row.id.toString(), row.name!!, PodPhase.valueOf(row.phase!!), names.getValue(row.creatorId!!),
            settings, sourceView, supply,
            members.map { member ->
                val player = member.accountId.toString()
                val progress = public?.members?.firstOrNull { it.player == player }
                val waiting = progress?.packsWaiting ?: limited?.let { LimitedEngine.consecutivePicks(it, player) } ?: gridWaiting(grid, player)
                PodMember(names.getValue(member.accountId!!), waiting, progress?.picksMade ?: grid?.log?.count { it.player == player }
                    ?: limited?.let { LimitedEngine.picksMade(it, player) } ?: 0,
                    row.phase == "DRAFTED" && member.submittedDeck == null, member.submittedDeck != null, waiting > 0)
            }, pending.map { names.getValue(it.accountId!!) }, row.draftAttemptId?.toString(), view,
            if (row.draftState != null) build(row, mine.build!!.data()) else null,
            mine.submittedDeck != null, mine.submittedDeck?.let { build(row, it.data()) }, cards,
            row.updatedAt!!.toInstant().toString(), gridView, mine.deckName ?: "${row.name} deck",
            row.phase == "SETUP" && row.autoStart == true,
            row.autoStartOffReason?.takeIf { row.phase == "SETUP" && owner == row.creatorId }
                ?.let { PodAutoStartOff(it, offAccount?.let(names::getValue)) },
            notes(sql, row, owner), limitedView, mine.deckCover)
        return Result(status, json.encodeToString(result))
    }

    /** Every card a limited view shows, for its definitions. */
    private fun LimitedView?.cards(): List<DraftCopy> = this?.let { view ->
        view.pool + view.board.map { it.copy } + view.packs.flatMap { pack -> pack.slots.map { it.copy } } +
            view.piles.flatten().filterNotNull() + view.tables.flatMap { it.piles.flatten().filterNotNull() } + view.picks.flatMap { it.cards.filterNotNull() }
    }.orEmpty()

    private fun mutate(principal: AccountService.Principal, id: String, request: String, kind: String, input: JsonElement,
        attempt: String? = null, action: (DSLContext, UUID, PodRecord) -> Result): Result {
        if (!DeckValidation.uuid(id)) return error(404, "not_found")
        if (attempt != null && !DeckValidation.uuid(attempt)) return error(400, "invalid_request")
        val intent = DeckJson.canonicalElement(buildJsonObject { put("podId", id); put("input", input) })
        return business(principal, request, kind, intent, id, attempt) { sql, owner, row ->
            if (row == null) error(404, "not_found") else action(sql, owner, row)
        }
    }

    private fun business(principal: AccountService.Principal, request: String, kind: String, canonical: String,
        pod: String? = null, attempt: String? = null, action: (DSLContext, UUID, PodRecord?) -> Result): Result {
        if (!DeckValidation.uuid(request)) return error(400, "invalid_request")
        val owner = UUID.fromString(principal.account.accountId); val id = UUID.fromString(request)
        val intent = DeckJson.intent(kind, canonical)
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val inserted = sql.insertInto(REQUEST_IDENTITY).set(REQUEST_IDENTITY.ID, id).set(REQUEST_IDENTITY.ACCOUNT_ID, owner)
                .set(REQUEST_IDENTITY.KIND, kind).set(REQUEST_IDENTITY.SCOPE, "account").set(REQUEST_IDENTITY.INTENT_DIGEST, intent)
                .onConflictDoNothing().execute() == 1
            if (!inserted) {
                val original = sql.selectFrom(REQUEST_IDENTITY).where(REQUEST_IDENTITY.ID.eq(id)).fetchSingle()
                if (original.accountId != owner || original.kind != kind || original.scope != "account" || original.intentDigest != intent)
                    return@transaction error(409, "request_conflict")
            }
            val row = pod?.let { sql.selectFrom(POD).where(POD.ID.eq(UUID.fromString(it))).forUpdate().fetchOne() }
            // Validate both explicit attempts and retained response attempts under the pod lock.
            val stale = attempt != null && row?.draftAttemptId?.toString() != attempt
            if (!inserted && !stale) {
                val outcome = sql.selectFrom(ACCOUNT_REQUEST).where(ACCOUNT_REQUEST.REQUEST_ID.eq(id)).fetchSingle()
                val responseAttempt = json.parseToJsonElement(outcome.response!!).jsonObject["draftAttemptId"]
                    ?.jsonPrimitive?.contentOrNull
                if (responseAttempt != null && row?.draftAttemptId?.toString() != responseAttempt)
                    return@transaction error(409, "draft_attempt_expired")
                return@transaction Result(outcome.status!!, outcome.response!!)
            }
            val result = if (stale) error(409, "draft_attempt_expired") else action(sql, owner, row)
            if (inserted) sql.insertInto(ACCOUNT_REQUEST).set(ACCOUNT_REQUEST.REQUEST_ID, id)
                .set(ACCOUNT_REQUEST.STATUS, result.status).set(ACCOUNT_REQUEST.RESPONSE, result.body).execute()
            result
        }
    }
}
