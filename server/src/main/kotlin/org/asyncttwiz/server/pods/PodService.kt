package org.asyncttwiz.server.pods

import java.security.SecureRandom
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.activity.ActivityService
import org.asyncttwiz.server.decks.DeckService.Result
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
    private fun settings(row: PodRecord) = json.decodeFromString<DraftSettings>(row.settings!!.data())
    // Legacy cubes remain arrays. A set is stored as a JSON string inside JSONB: PostgreSQL's
    // object-key reordering must not change the significant card-map order of fixed recipe sheets.
    private fun isSetSource(row: PodRecord) = row.sourceSnapshot!!.data().trimStart().startsWith("\"")
    private fun setSource(row: PodRecord): SetSourceSnapshot? = row.sourceSnapshot!!.data().let {
        if (isSetSource(row)) json.decodeFromString<SetSourceSnapshot>(json.decodeFromString<String>(it)) else null
    }
    private fun encodedSet(source: SetSourceSnapshot) = encoded(json.encodeToString(source))
    private fun sourceIntent(input: JsonElement, source: PodSetSource?): JsonElement {
        val fixed = source?.snapshot?.recipe?.sheets.orEmpty().filterValues { it.fixed }
        if (fixed.isEmpty()) return input
        // Canonical JSON sorts object keys; include significant fixed-sheet order as ordered arrays.
        return buildJsonObject {
            put("input", input)
            putJsonObject("fixedSheetOrder") {
                for ((name, sheet) in fixed) putJsonArray(name) { sheet.cards.keys.forEach { add(it) } }
            }
        }
    }
    private fun source(row: PodRecord) = if (!isSetSource(row))
        json.decodeFromString<List<DraftCubeEntry>>(row.sourceSnapshot!!.data()) else emptyList()
    private fun supply(row: PodRecord, players: Int): DraftSupply {
        val finite = settings(row).supply(players, source(row))
        return if (!isSetSource(row)) finite else finite.copy(available = Long.MAX_VALUE)
    }
    private fun state(row: PodRecord) = row.draftState?.takeIf { settings(row).mode != DraftMode.GRID }
        ?.let { json.decodeFromString<DraftState>(it.data()) }
    private fun gridState(row: PodRecord) = row.draftState?.takeIf { settings(row).mode == DraftMode.GRID }
        ?.let { json.decodeFromString<GridState>(it.data()) }
    private fun pools(row: PodRecord) = gridState(row)?.pools ?: state(row)!!.pools
    private fun copies(row: PodRecord) = gridState(row)?.copies ?: state(row)!!.copies
    private fun gridWaiting(state: GridState?, player: String) = state?.grids?.count { it.currentPlayer == player } ?: 0
    private fun members(sql: DSLContext, pod: UUID) = sql.selectFrom(MEMBER).where(MEMBER.POD_ID.eq(pod)).orderBy(MEMBER.ACCOUNT_ID).fetch()
    private fun pending(sql: DSLContext, pod: UUID) = sql.selectFrom(INVITE).where(INVITE.POD_ID.eq(pod)).and(INVITE.STATE.eq("PENDING")).orderBy(INVITE.ACCOUNT_ID).fetch()
    private fun identity(sql: DSLContext, id: UUID): AccountIdentity {
        return AccountService.identity(sql.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(id)).fetchSingle())
    }
    private fun validName(name: String) = DeckValidation.text(name) && name.isNotBlank() && name.length <= 120 && name.none { it.isISOControl() }

    private fun validateLibrarian(settings: DraftSettings): Result? {
        val card = settings.librarianDefinition
        if (settings.librarians > 0 && card == null) return error(400, "librarian_definition_unavailable")
        if (card != null && (card.definition.name != "Cogwork Librarian" || !DeckValidation.digest(card.digest) ||
                !DeckValidation.definition(card.definition) || DeckJson.digest(card.definition) != card.digest))
            return error(400, "invalid_definition")
        return null
    }

    private fun storeLibrarian(sql: DSLContext, settings: DraftSettings) {
        settings.librarianDefinition?.let { card ->
            sql.insertInto(DEFINITION).set(DEFINITION.DIGEST, card.digest).set(DEFINITION.SCHEMA_VERSION, card.definition.schemaVersion)
                .set(DEFINITION.PAYLOAD, DeckJson.canonical(card.definition)).onConflictDoNothing().execute()
        }
    }

    private fun librarianDigests(sql: DSLContext, digests: Set<String>) =
        PreparationStore.definitions(sql, digests).filter { it.definition.name == "Cogwork Librarian" }.map { it.digest }.toSet()

    private fun validateSet(input: PodSetSource): Result? {
        if (input.snapshot.validate().isNotEmpty()) return error(400, "invalid_set_snapshot")
        val definitions = input.definitions
        if (definitions.size !in 1..SetSourceValidation.MAX_CARDS || definitions.map { it.digest }.distinct().size != definitions.size ||
            definitions.any { !DeckValidation.digest(it.digest) || !DeckValidation.definition(it.definition) || DeckJson.digest(it.definition) != it.digest })
            return error(400, "invalid_definition")
        if (!definitions.map { it.digest }.toSet().containsAll(input.snapshot.cards.map { it.definition }))
            return error(400, "definitions_unavailable")
        return null
    }

    private fun storeSet(sql: DSLContext, input: PodSetSource) {
        for (submitted in input.definitions) sql.insertInto(DEFINITION).set(DEFINITION.DIGEST, submitted.digest)
            .set(DEFINITION.SCHEMA_VERSION, submitted.definition.schemaVersion)
            .set(DEFINITION.PAYLOAD, DeckJson.canonical(submitted.definition)).onConflictDoNothing().execute()
    }

    private fun snapshot(sql: DSLContext, owner: UUID, cubeId: String): Pair<String, List<DraftCubeEntry>>? {
        if (!DeckValidation.uuid(cubeId)) return null
        val cube = sql.selectFrom(SAVED_CUBE).where(SAVED_CUBE.ID.eq(UUID.fromString(cubeId)))
            .and(SAVED_CUBE.ACCOUNT_ID.eq(owner)).forShare().fetchOne() ?: return null
        val entries = sql.select(CUBE_ENTRY.DEFINITION_DIGEST, CUBE_ENTRY.QUANTITY, DEFINITION.PAYLOAD)
            .from(CUBE_ENTRY).join(DEFINITION).on(DEFINITION.DIGEST.eq(CUBE_ENTRY.DEFINITION_DIGEST))
            .where(CUBE_ENTRY.CUBE_ID.eq(cube.id)).orderBy(CUBE_ENTRY.POSITION).fetch().map {
                val definition = json.decodeFromString<CardDefinition>(it[DEFINITION.PAYLOAD]!!)
                val explicit = definition.faces.firstOrNull()?.sourceData?.let { data -> runCatching {
                    json.parseToJsonElement(data).jsonObject["colors"]?.jsonArray?.mapNotNull { color ->
                        DraftColor.entries.firstOrNull { it.name == color.jsonPrimitive.content }
                    }?.toSet()
                }.getOrNull() }
                DraftCubeEntry(it[CUBE_ENTRY.DEFINITION_DIGEST]!!, it[CUBE_ENTRY.QUANTITY]!!,
                    explicit ?: PodDeckManaCost.front(definition).colors)
            }
        return cube.name!! to entries
    }

    fun create(principal: AccountService.Principal, input: CreatePod): Result =
        business(principal, input.requestId, "createPod", DeckJson.canonicalElement(sourceIntent(json.encodeToJsonElement(input), input.setSource))) { sql, owner, _ ->
            if (!validName(input.name)) return@business error(400, "invalid_name")
            if (input.settings?.validate(2)?.isNotEmpty() == true) return@business error(400, "invalid_settings")
            if ((input.cubeId == null) == (input.setSource == null)) return@business error(400, "invalid_source")
            input.setSource?.let { validateSet(it)?.let { failure -> return@business failure } }
            val snapshot = input.cubeId?.let { snapshot(sql, owner, it) ?: return@business error(404, "cube_not_found") }
            input.setSource?.let { storeSet(sql, it) }
            val settings = input.settings ?: when (input.mode) {
                DraftMode.GLIMPSE -> DraftSettings.glimpse()
                DraftMode.GRID -> DraftSettings.grid()
                DraftMode.BOOSTER -> DraftSettings.booster()
            }
            validateLibrarian(settings)?.let { return@business it }
            storeLibrarian(sql, settings)
            val row = sql.insertInto(POD).set(POD.ID, UUID.randomUUID()).set(POD.CREATOR_ID, owner)
                .set(POD.NAME, input.name).set(POD.SETTINGS, encoded(settings))
                .set(POD.SOURCE_NAME, snapshot?.first ?: input.setSource!!.snapshot.setName)
                .set(POD.SOURCE_SNAPSHOT, snapshot?.let { encoded(it.second) } ?: encodedSet(input.setSource!!.snapshot)).returning().fetchSingle()
            sql.insertInto(MEMBER).set(MEMBER.POD_ID, row.id).set(MEMBER.ACCOUNT_ID, owner).execute()
            detail(sql, row, owner, 201)
        }

    fun update(principal: AccountService.Principal, id: String, input: UpdatePod): Result =
        mutate(principal, id, input.requestId, "updatePod", sourceIntent(json.encodeToJsonElement(input), input.setSource)) { sql, owner, row ->
            creator(row, owner)?.let { return@mutate it }
            if (row.phase != "SETUP") return@mutate error(409, "not_setup")
            if (input.name?.let { !validName(it) } == true) return@mutate error(400, "invalid_name")
            // Setup may have only its creator; validate settings against the minimum startable membership.
            if (input.settings?.validate(members(sql, row.id!!).size.coerceAtLeast(2))?.isNotEmpty() == true)
                return@mutate error(400, "invalid_settings")
            if (input.cubeId != null && input.setSource != null) return@mutate error(400, "invalid_source")
            input.setSource?.let { validateSet(it)?.let { failure -> return@mutate failure } }
            val snapshot = input.cubeId?.let { snapshot(sql, owner, it) ?: return@mutate error(404, "cube_not_found") }
            input.name?.let { row.name = it }
            input.settings?.let { row.settings = encoded(it) }
            validateLibrarian(settings(row))?.let { return@mutate it }
            storeLibrarian(sql, settings(row))
            snapshot?.let { row.sourceName = it.first; row.sourceSnapshot = encoded(it.second) }
            input.setSource?.let { storeSet(sql, it); row.sourceName = it.snapshot.setName; row.sourceSnapshot = encodedSet(it.snapshot) }
            save(sql, row)
            detail(sql, row, owner)
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
            save(sql, row); detail(sql, row, owner)
        }

    fun invitation(principal: AccountService.Principal, id: String, input: PodRequest, accept: Boolean): Result =
        mutate(principal, id, input.requestId, if (accept) "acceptPod" else "declinePod", json.encodeToJsonElement(input)) { sql, owner, row ->
            if (row.phase != "SETUP") return@mutate error(409, "invitation_unavailable")
            if (pending(sql, row.id!!).none { it.accountId == owner }) return@mutate error(404, "invitation_not_found")
            if (accept && members(sql, row.id!!).size >= 8) return@mutate error(409, "pod_full")
            sql.update(INVITE).set(INVITE.STATE, if (accept) "ACCEPTED" else "DECLINED")
                .where(INVITE.POD_ID.eq(row.id)).and(INVITE.ACCOUNT_ID.eq(owner)).execute()
            if (accept) sql.insertInto(MEMBER).set(MEMBER.POD_ID, row.id).set(MEMBER.ACCOUNT_ID, owner).execute()
            save(sql, row)
            if (accept) detail(sql, row, owner) else acknowledged(row)
        }

    fun start(principal: AccountService.Principal, id: String, input: PodRequest): Result =
        mutate(principal, id, input.requestId, "startPod", json.encodeToJsonElement(input)) { sql, owner, row ->
            creator(row, owner)?.let { return@mutate it }
            if (row.phase != "SETUP") return@mutate error(409, "not_setup")
            val players = members(sql, row.id!!).map { it.accountId.toString() }
            if (players.size < 2) return@mutate error(409, "not_enough_members")
            val settings = settings(row); val cube = source(row)
            if (settings.validate(players.size).isNotEmpty()) return@mutate error(400, "invalid_settings")
            validateLibrarian(settings)?.let { return@mutate it }
            if (!supply(row, players.size).sufficient) return@mutate error(409, "insufficient_supply")
            val seed = RandomV1.encodeSeed(ByteArray(32).also(random::nextBytes))
            row.draftAttemptId = UUID.randomUUID()
            sql.update(INVITE).set(INVITE.STATE, "LAPSED").where(INVITE.POD_ID.eq(row.id)).and(INVITE.STATE.eq("PENDING")).execute()
            val set = setSource(row)
            if (settings.mode == DraftMode.GRID) {
                val next = if (set == null) GridEngine.start(players, cube, settings, seed) else {
                    // Set grids consume generated packs in card order (including refills), not cube-shuffled cards.
                    val needed = settings.supply(players.size, emptyList()).needed.toInt()
                    val minPack = set.recipe?.boosters?.minOf { it.contents.values.sum() } ?: 15
                    val cards = SetBoosters.generate(set, (needed + minPack - 1) / minPack, seed).flatMap { it.cards }.take(needed)
                    GridEngine.startWithCards(players, cards, settings, seed)
                }
                advanceGrid(sql, row, null, next)
            } else {
                val digests = librarianDigests(sql, (cube.map { it.definition } + set?.cards.orEmpty().map { it.definition }).toSet())
                val next = if (set == null) DraftEngine.start(players, cube.map { it.copy(librarian = it.definition in digests) }, settings, seed) else
                    DraftEngine.startWithPacks(players, SetBoosters.generate(set, settings.packsPerPlayer * players.size, seed, settings.cardsPerPack)
                        .map { pack -> pack.cards.map { it.copy(librarian = it.definition in digests) } }, settings, seed)
                advance(sql, row, null, next)
            }
            detail(sql, row, owner)
        }

    fun pick(principal: AccountService.Principal, id: String, input: PickPod): Result =
        mutate(principal, id, input.requestId, "pickPod", json.encodeToJsonElement(input), input.draftAttemptId) { sql, owner, row ->
            member(sql, row, owner)?.let { return@mutate it }
            if (row.phase != "DRAFTING") return@mutate error(409, "not_drafting")
            if (settings(row).mode == DraftMode.GRID) return@mutate error(409, "wrong_draft_mode")
            val previous = state(row)!!
            val result = DraftEngine.pick(previous, DraftPick(owner.toString(), input.packId, input.keep, input.burn, input.librarians))
            if (result.reason != null) return@mutate error(409, result.reason!!.name.lowercase())
            advance(sql, row, previous, result.state!!)
            detail(sql, row, owner)
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

    fun move(principal: AccountService.Principal, id: String, input: MovePodCopy): Result =
        editBuild(principal, id, input.requestId, "movePodCopy", json.encodeToJsonElement(input), input.draftAttemptId) { build ->
            if (build.copies.none { it.copyId == input.copyId }) null
            else build.copy(copies = build.copies.map { if (it.copyId == input.copyId) it.copy(category = input.category) else it })
        }

    fun basics(principal: AccountService.Principal, id: String, input: SetPodBasics): Result =
        editBuild(principal, id, input.requestId, "setPodBasics", json.encodeToJsonElement(input), input.draftAttemptId) { build ->
            if (!input.basics.valid) null else build.copy(basics = input.basics)
        }

    fun deckName(principal: AccountService.Principal, id: String, input: SetPodDeckName): Result =
        mutate(principal, id, input.requestId, "setPodDeckName", json.encodeToJsonElement(input), input.draftAttemptId) { sql, owner, row ->
            member(sql, row, owner)?.let { return@mutate it }
            if (row.phase !in listOf("DRAFTING", "DRAFTED")) return@mutate error(409, "not_drafting")
            if (!validName(input.name)) return@mutate error(400, "invalid_name")
            sql.update(MEMBER).set(MEMBER.DECK_NAME, input.name).where(MEMBER.POD_ID.eq(row.id))
                .and(MEMBER.ACCOUNT_ID.eq(owner)).execute()
            save(sql, row); detail(sql, row, owner)
        }

    private fun editBuild(principal: AccountService.Principal, id: String, request: String, kind: String, input: JsonElement,
        attempt: String, edit: (PodDeckBuild) -> PodDeckBuild?): Result = mutate(principal, id, request, kind, input, attempt) { sql, owner, row ->
        member(sql, row, owner)?.let { return@mutate it }
        if (row.phase !in listOf("DRAFTING", "DRAFTED")) return@mutate error(409, "not_drafting")
        val member = members(sql, row.id!!).first { it.accountId == owner }
        if (member.submittedDeck != null) return@mutate error(409, "deck_submitted")
        val build = json.decodeFromString<PodDeckBuild>(member.build!!.data())
        val next = edit(build) ?: return@mutate error(400, "invalid_build")
        sql.update(MEMBER).set(MEMBER.BUILD, encoded(next)).where(MEMBER.POD_ID.eq(row.id)).and(MEMBER.ACCOUNT_ID.eq(owner)).execute()
        save(sql, row); detail(sql, row, owner)
    }

    fun submit(principal: AccountService.Principal, id: String, input: PodDraftRequest): Result =
        mutate(principal, id, input.requestId, "submitPod", json.encodeToJsonElement(input), input.draftAttemptId) { sql, owner, row ->
            member(sql, row, owner)?.let { return@mutate it }
            if (row.phase != "DRAFTED") return@mutate error(409, "draft_not_complete")
            val member = members(sql, row.id!!).first { it.accountId == owner }
            if (member.submittedDeck != null) return@mutate error(409, "deck_submitted")
            val build = json.decodeFromString<PodDeckBuild>(member.build!!.data())
            val issues = PodDeckRules.validate(build, pools(row).getValue(owner.toString()), settings(row).sideboardLimit)
            if (issues.isNotEmpty()) return@mutate error(400, issues.first().name.lowercase())
            val deck = build.copy(copies = build.copies.filter { it.category != PodDeckCategory.TRASH })
            sql.update(MEMBER).set(MEMBER.SUBMITTED_DECK, encoded(deck)).where(MEMBER.POD_ID.eq(row.id)).and(MEMBER.ACCOUNT_ID.eq(owner)).execute()
            save(sql, row); detail(sql, row, owner)
        }

    fun cancel(principal: AccountService.Principal, id: String, input: PodDraftRequest): Result =
        mutate(principal, id, input.requestId, "cancelPodDraft", json.encodeToJsonElement(input), input.draftAttemptId) { sql, owner, row ->
            creator(row, owner)?.let { return@mutate it }
            if (row.phase != "DRAFTING") return@mutate error(409, "not_drafting")
            reset(sql, row, owner)
            detail(sql, row, owner)
        }

    fun challenge(principal: AccountService.Principal, id: String, input: ChallengeFromPod): Result =
        mutate(principal, id, input.requestId, "challengeFromPod", json.encodeToJsonElement(input)) { sql, owner, row ->
            member(sql, row, owner)?.let { return@mutate it }
            if (!LobbyValidation.name(input.gameName)) return@mutate error(400, "invalid_name")
            if (!DeckValidation.uuid(input.opponentId) || input.opponentId == owner.toString())
                return@mutate error(400, "invalid_opponent")
            val players = members(sql, row.id!!).associateBy { it.accountId.toString() }
            if (input.opponentId !in players) return@mutate error(404, "opponent_not_member")
            if (row.phase != "DRAFTED" || listOf(owner.toString(), input.opponentId).any { players.getValue(it).submittedDeck == null })
                return@mutate error(409, "deck_not_submitted")
            val basics = PodGamePreparation.basicDefinitions(input.basicDefinitions)
                ?: return@mutate error(400, "invalid_basic_definitions")
            val builds = listOf(owner.toString(), input.opponentId).associateWith {
                json.decodeFromString<PodDeckBuild>(players.getValue(it).submittedDeck!!.data())
            }
            // Match the existing tabletop's bounded physical deck representation.
            if (builds.values.any { it.copies.count { copy -> copy.category != PodDeckCategory.TRASH } + it.basics.total > DeckValidation.MAX_CARDS })
                return@mutate error(400, "invalid_deck")
            val game = UUID.randomUUID(); val invitation = UUID.randomUUID()
            sql.insertInto(GAME).set(GAME.ID, game).set(GAME.GAME_NAME, input.gameName).set(GAME.INVITER_ID, owner)
                .set(GAME.RECIPIENT_ID, UUID.fromString(input.opponentId)).set(GAME.PREPARATION_ROUND, UUID.randomUUID())
                .set(GAME.CURRENT_INVITATION, invitation).set(GAME.POD_ID, row.id).set(GAME.POD_NAME, row.name)
                .set(GAME.POD_SIDEBOARD_LIMIT, settings(row).sideboardLimit).execute()
            sql.insertInto(INVITATION).set(INVITATION.ID, invitation).set(INVITATION.GAME_ID, game).execute()
            val lobby = LobbyService(runtime)
            val summary = lobby.summary(sql, game)
            // The recipient's locked deck remains pod-private until they accept the invitation.
            saveChallengeDeck(sql, row, summary, owner.toString(), builds.getValue(owner.toString()), basics,
                input.basicDefinitions, input.requestId)
            ActivityService.record(sql, UUID.fromString(input.opponentId), ActivityKind.INVITATION, game, invitation, owner)
            Result(201, json.encodeToString(summary))
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
        saveChallengeDeck(sql, row, summary, player, json.decodeFromString(submitted.data()), basics,
            preparation.definitions.filter { it.digest in basics.values }, requestId)
    }

    private fun saveChallengeDeck(sql: DSLContext, row: PodRecord, summary: GameSummary, player: String,
        build: PodDeckBuild, basics: Map<BasicLand, String>, basicDefinitions: List<SubmittedDefinition>, requestId: String) {
        val copies = copies(row).associateBy { it.id }
        val selection = CommandIds.uuidV5(requestId, "pod-deck/$player")
        var index = 0L
        val entries = build.copies.filter { it.category != PodDeckCategory.TRASH }.map { copy ->
            PreparedEntry(copies.getValue(copy.copyId).definition,
                if (copy.category == PodDeckCategory.MAIN) SavedDeckSection.MAINBOARD else SavedDeckSection.SIDEBOARD,
                (index++).toString(), 1)
        } + BasicLand.entries.mapNotNull { land -> land.count(build.basics).takeIf { it > 0 }?.let { count ->
            PreparedEntry(basics.getValue(land), SavedDeckSection.MAINBOARD, index.toString(), count, land).also { index += count }
        } }
        val deck = PreparedDeck(CommandIds.output(selection, "prepared-deck", 0), row.id.toString(), "1",
            row.name!!, entries.firstOrNull()?.definition, selection, entries, basics, index.toString())
        val definitions = PreparationStore.definitions(sql, entries.map { it.definition }.toSet()) + basicDefinitions
        PreparationStore.saveSelection(sql, UUID.fromString(player), LobbyPreparation(summary.id, summary.preparationRound,
            listOf(PlayerPreparation(player, deck = deck)), definitions.distinctBy { it.digest }))
        val name = sql.select(MEMBER.DECK_NAME).from(MEMBER).where(MEMBER.POD_ID.eq(row.id))
            .and(MEMBER.ACCOUNT_ID.eq(UUID.fromString(player))).fetchSingle(MEMBER.DECK_NAME) ?: "${row.name} deck"
        sql.update(PLAYER_PREPARATION).set(PLAYER_PREPARATION.POD_DECK_NAME, name)
            .where(PLAYER_PREPARATION.GAME_ID.eq(UUID.fromString(summary.id)))
            .and(PLAYER_PREPARATION.ACCOUNT_ID.eq(UUID.fromString(player))).execute()
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
        notify(sql, row, ActivityKind.POD_DRAFT_CANCELLED, author)
        row.phase = "SETUP"; row.draftAttemptId = null; row.draftState = null
        sql.update(MEMBER).set(MEMBER.BUILD, encoded(PodDeckBuild())).set(MEMBER.SUBMITTED_DECK, null as JSONB?)
            .set(MEMBER.DECK_NAME, null as String?)
            .where(MEMBER.POD_ID.eq(row.id)).execute()
        save(sql, row)
    }

    private fun advance(sql: DSLContext, row: PodRecord, previous: DraftState?, next: DraftState) {
        row.draftState = encoded(next); row.phase = if (next.finished) "DRAFTED" else "DRAFTING"
        for (member in members(sql, row.id!!)) {
            val owner = member.accountId!!; val player = owner.toString()
            val build = json.decodeFromString<PodDeckBuild>(member.build!!.data())
            val initialSide = if (previous == null) next.copies.filter { it.librarian && it.id.startsWith("librarian:") }.map { it.id }.toSet() else emptySet()
            sql.update(MEMBER).set(MEMBER.BUILD, encoded(PodDeckRules.withDraftPool(build, next.pools.getValue(player), initialSide)))
                .where(MEMBER.POD_ID.eq(row.id)).and(MEMBER.ACCOUNT_ID.eq(owner)).execute()
            val hadPacks = previous?.queues?.get(player).orEmpty().isNotEmpty()
            if ((!hadPacks || previous?.round != next.round) && next.queues.getValue(player).isNotEmpty())
                ActivityService.record(sql, owner, ActivityKind.POD_YOUR_PICK, row.id!!, row.draftAttemptId!!)
        }
        if (next.finished && previous?.finished != true) {
            clearDraftHints(sql, row.id!!)
            notify(sql, row, ActivityKind.POD_DRAFT_COMPLETE)
        }
        save(sql, row)
    }

    private fun advanceGrid(sql: DSLContext, row: PodRecord, previous: GridState?, next: GridState) {
        row.draftState = encoded(next); row.phase = if (next.finished) "DRAFTED" else "DRAFTING"
        for (member in members(sql, row.id!!)) {
            val owner = member.accountId!!; val player = owner.toString()
            val build = json.decodeFromString<PodDeckBuild>(member.build!!.data())
            sql.update(MEMBER).set(MEMBER.BUILD, encoded(PodDeckRules.withAwards(build, next.pools.getValue(player))))
                .where(MEMBER.POD_ID.eq(row.id)).and(MEMBER.ACCOUNT_ID.eq(owner)).execute()
            // More simultaneous turns do not notify a member who already has an actionable grid.
            if (gridWaiting(previous, player) == 0 && gridWaiting(next, player) > 0)
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
        val kinds = listOf("POD_YOUR_PICK", "POD_DRAFT_COMPLETE", "POD_INVITATION")
        sql.deleteFrom(summary).where(summary.GAME_ID.eq(id)).and(summary.KIND.`in`(kinds)).execute()
        sql.deleteFrom(push).where(push.GAME_ID.eq(id)).and(push.KIND.`in`(kinds)).execute()
    }

    private fun notify(sql: DSLContext, row: PodRecord, kind: ActivityKind, author: UUID? = null) {
        for (member in members(sql, row.id!!)) ActivityService.record(sql, member.accountId!!, kind, row.id!!,
            row.draftAttemptId ?: UUID.randomUUID(), author)
    }
    private fun save(sql: DSLContext, row: PodRecord) {
        row.updatedAt = sql.update(POD).set(POD.NAME, row.name).set(POD.PHASE, row.phase).set(POD.SETTINGS, row.settings)
            .set(POD.SOURCE_NAME, row.sourceName).set(POD.SOURCE_SNAPSHOT, row.sourceSnapshot)
            .set(POD.DRAFT_ATTEMPT_ID, row.draftAttemptId).set(POD.DRAFT_STATE, row.draftState)
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
            member(sql, row, owner) ?: detail(sql, row, owner)
        }
    }

    fun list(principal: AccountService.Principal): Result = runtime.transaction { connection ->
        val sql = DSL.using(connection, SQLDialect.POSTGRES)
        val owner = UUID.fromString(principal.account.accountId)
        val rows = sql.selectFrom(POD).where(POD.ID.`in`(sql.select(MEMBER.POD_ID).from(MEMBER).where(MEMBER.ACCOUNT_ID.eq(owner))))
            .orderBy(POD.ID).forShare().fetch()
        // Membership is rechecked after each pod lock: a concurrent leave may have removed the caller.
        val pods = rows.mapNotNull { row ->
            val members = members(sql, row.id!!)
            val mine = members.firstOrNull { it.accountId == owner } ?: return@mapNotNull null
            val waiting = state(row)?.queues?.get(owner.toString())?.size ?: gridWaiting(gridState(row), owner.toString())
            val attention = when {
                waiting > 0 -> PodAttention.YOUR_PICK
                row.phase == "DRAFTED" && mine.submittedDeck == null -> PodAttention.BUILD
                else -> PodAttention.NONE
            }
            PodSummary(row.id.toString(), row.name!!, PodPhase.valueOf(row.phase!!), identity(sql, row.creatorId!!),
                members.map { identity(sql, it.accountId!!) }, attention, waiting, row.updatedAt!!.toInstant().toString())
        }.sortedWith(compareBy<PodSummary> { it.attention.ordinal }.thenByDescending { it.updatedAt }.thenBy { it.id })
        val invitations = sql.select(INVITE.POD_ID, INVITE.INVITED_AT, POD.NAME, POD.CREATOR_ID).from(INVITE).join(POD).on(POD.ID.eq(INVITE.POD_ID))
            .where(INVITE.ACCOUNT_ID.eq(owner)).and(INVITE.STATE.eq("PENDING")).and(POD.PHASE.eq("SETUP"))
            .orderBy(INVITE.INVITED_AT.desc(), INVITE.POD_ID).fetch().map {
                PodInvitation(it[INVITE.POD_ID].toString(), it[POD.NAME]!!, identity(sql, it[POD.CREATOR_ID]!!), it[INVITE.INVITED_AT]!!.toInstant().toString())
            }
        Result(200, json.encodeToString(PodList(pods, invitations)))
    }

    /** Booster cards are private; grid boards/log are public, but pools/builds remain caller-only. */
    private fun detail(sql: DSLContext, row: PodRecord, owner: UUID, status: Int = 200): Result {
        val draft = state(row)
        val view = draft?.let { DraftEngine.privateView(it, owner.toString()) }
        val public = draft?.let(DraftEngine::publicStatus)
        val grid = gridState(row)
        val gridView = grid?.let { PodGridView(GridEngine.publicView(it), GridEngine.privateView(it, owner.toString())) }
        val members = members(sql, row.id!!)
        val mine = members.first { it.accountId == owner }
        val visible = view?.head?.cards.orEmpty() + view?.queuedPacks.orEmpty().flatMap { it.cards } +
            view?.pool.orEmpty() + gridView?.mine?.pool.orEmpty() +
            gridView?.publicView?.grids.orEmpty().flatMap { it.positions.filterNotNull() } +
            gridView?.publicView?.log.orEmpty().flatMap { it.kept + it.discarded }
        val definitions = visible.map { it.definition }.toSet()
        val cards = if (definitions.isEmpty()) emptyList() else sql.selectFrom(DEFINITION).where(DEFINITION.DIGEST.`in`(definitions))
            .orderBy(DEFINITION.DIGEST).fetch().map { SubmittedDefinition(it.digest!!, json.decodeFromString<CardDefinition>(it.payload!!)) }
        val set = setSource(row)
        val sourceView = if (set == null) PodSource(row.sourceName!!, source(row).sumOf { it.quantity.toLong() }) else
            PodSource(kind = PodSourceKind.SET, setCode = set.setCode, setName = set.setName,
                boosterKind = if (set.recipe == null) SetBoosterKind.APPROXIMATE else SetBoosterKind.RECIPE_BASED,
                boosterName = set.recipe?.name)
        val result = PodDetail(row.id.toString(), row.name!!, PodPhase.valueOf(row.phase!!), identity(sql, row.creatorId!!),
            settings(row), sourceView, supply(row, members.size),
            members.map { member ->
                val player = member.accountId.toString()
                val progress = public?.members?.firstOrNull { it.player == player }
                val waiting = progress?.packsWaiting ?: gridWaiting(grid, player)
                PodMember(identity(sql, member.accountId!!), waiting, progress?.picksMade ?: grid?.log?.count { it.player == player } ?: 0,
                    row.phase == "DRAFTED" && member.submittedDeck == null, member.submittedDeck != null, waiting > 0)
            }, pending(sql, row.id!!).map { identity(sql, it.accountId!!) }, row.draftAttemptId?.toString(), view,
            if (row.draftState != null) json.decodeFromString<PodDeckBuild>(mine.build!!.data()) else null,
            mine.submittedDeck != null, mine.submittedDeck?.let { json.decodeFromString<PodDeckBuild>(it.data()) }, cards,
            row.updatedAt!!.toInstant().toString(), gridView, mine.deckName ?: "${row.name} deck")
        return Result(status, json.encodeToString(result))
    }

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
