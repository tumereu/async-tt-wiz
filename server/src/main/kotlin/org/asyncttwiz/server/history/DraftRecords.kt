package org.asyncttwiz.server.history

import java.time.OffsetDateTime
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.decks.StoredDefinitions
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.DraftRecord.DRAFT_RECORD
import org.jooq.DSLContext

/**
 * LOB-279 (owner, 2026-11-26: "deleting a pod should not take away a player's opportunity to learn"): the drafts kept for each
 * player's Stats, their last [org.asyncttwiz.protocol.DraftRecords.KEPT] Booster, Glimpse, Custom booster or Arena drafts with
 * card ratings (DRF-315). Each is written in the transaction where the player's own draft becomes done (DRF-119), with what
 * their draft history shows (only what their own packs showed, DRF-211), and never changes after; it has no tie to its pod,
 * so it outlives the pod's deletion by hand or by the cleanup (LOB-182). A newer one past the limit drops the player's oldest.
 */
internal object DraftRecords {
    private val json = GameWire.json
    private fun log(message: String) = println("${java.time.Instant.now()} Draft records: $message")

    /**
     * [account]'s own draft of [pod]'s draft [attempt] is done now ([at]): keeps it with their [picks] and the [seating]
     * ([players] its identities), then drops their oldest past the limit. A draft already kept stays as it is.
     */
    fun write(sql: DSLContext, account: UUID, pod: UUID, attempt: UUID, podName: String, source: PodSource, settings: DraftSettings,
        seating: List<String>, picks: List<DraftHistoryPick>, players: List<AccountIdentity>, at: OffsetDateTime) {
        val id = UUID.randomUUID()
        // Stored whole, as a read gives it, but for the cards' definitions (read with it) and the names (as they are then)
        val stored = DraftRecord(DraftRecordSummary(id.toString(), pod.toString(), podName, source, settings, at.toInstant().toString(), players),
            PodDraftHistory(picks, seating = seating))
        val written = sql.insertInto(DRAFT_RECORD).set(DRAFT_RECORD.ID, id).set(DRAFT_RECORD.ACCOUNT_ID, account)
            .set(DRAFT_RECORD.POD_ID, pod).set(DRAFT_RECORD.DRAFT_ATTEMPT_ID, attempt).set(DRAFT_RECORD.FINISHED_AT, at)
            .set(DRAFT_RECORD.RECORD, json.encodeToString(DraftRecord.serializer(), stored))
            .onConflict(DRAFT_RECORD.ACCOUNT_ID, DRAFT_RECORD.DRAFT_ATTEMPT_ID).doNothing().execute()
        if (written == 0) { log("pod $pod draft $attempt of $account already kept"); return }
        val kept = sql.select(DRAFT_RECORD.ID).from(DRAFT_RECORD).where(DRAFT_RECORD.ACCOUNT_ID.eq(account))
            .orderBy(DRAFT_RECORD.FINISHED_AT.desc(), DRAFT_RECORD.ID.desc()).limit(org.asyncttwiz.protocol.DraftRecords.KEPT)
            .fetch(DRAFT_RECORD.ID)
        val dropped = sql.deleteFrom(DRAFT_RECORD).where(DRAFT_RECORD.ACCOUNT_ID.eq(account)).and(DRAFT_RECORD.ID.notIn(kept)).execute()
        log("pod $pod ('$podName', ${settings.mode.name.lowercase()}, ${source.setCode}) draft $attempt kept for $account as $id: " +
            "${picks.size} picks, seating $seating" + if (dropped > 0) "; their $dropped oldest dropped past ${kept.size}" else "")
    }

    /** [viewer]'s kept drafts, newest first (GET /v1/history/drafts). */
    fun list(sql: DSLContext, viewer: UUID): DraftRecordList {
        val stored = rows(sql, viewer, null)
        val names = accounts(sql, stored.flatMap { it.history.seating })
        return DraftRecordList(stored.map { summary(it, names) })
    }

    /** [viewer]'s kept draft [id] with its draft history and its cards' definitions; null when they have no such draft. */
    fun read(sql: DSLContext, viewer: UUID, id: UUID): DraftRecord? {
        val stored = rows(sql, viewer, id).singleOrNull() ?: return null
        val digests = stored.history.picks.flatMap { it.cards + it.takenSince }.map { it.definition }.toSet()
        return DraftRecord(summary(stored, accounts(sql, stored.history.seating)),
            stored.history.copy(definitions = StoredDefinitions.read(sql, digests)))
    }

    private fun rows(sql: DSLContext, viewer: UUID, id: UUID?): List<DraftRecord> =
        sql.select(DRAFT_RECORD.ID, DRAFT_RECORD.RECORD).from(DRAFT_RECORD)
            .where(DRAFT_RECORD.ACCOUNT_ID.eq(viewer)).and(id?.let(DRAFT_RECORD.ID::eq) ?: org.jooq.impl.DSL.trueCondition())
            .orderBy(DRAFT_RECORD.FINISHED_AT.desc(), DRAFT_RECORD.ID.desc()).fetch()
            // A record the current code can't read is left out (SYS-052)
            .mapNotNull { row -> StoredData.orSkip("draft record", row.value1()) { json.decodeFromString(DraftRecord.serializer(), row.value2()!!) } }

    /** [stored]'s summary with the seating's people as they are named now (bots by their seat; the stored name for an
     * account no longer found). */
    private fun summary(stored: DraftRecord, names: Map<String, AccountIdentity>): DraftRecordSummary {
        val then = stored.summary.players.associateBy { it.accountId }
        return stored.summary.copy(players = stored.history.seating.mapNotNull { id -> PodBots.identity(id) ?: names[id] ?: then[id] })
    }

    private fun accounts(sql: DSLContext, ids: Collection<String>): Map<String, AccountIdentity> {
        val accounts = ids.filterNot(PodBots::isBot).mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }.toSet()
        return if (accounts.isEmpty()) emptyMap() else sql.selectFrom(ACCOUNT).where(ACCOUNT.ID.`in`(accounts)).fetch()
            .associate { it.id.toString() to AccountService.identity(it) }
    }
}
