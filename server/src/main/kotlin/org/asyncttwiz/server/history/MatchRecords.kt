package org.asyncttwiz.server.history

import java.time.OffsetDateTime
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.game.EngineGames
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.DeckShownTo.DECK_SHOWN_TO
import org.asyncttwiz.server.generated.tables.EngineEvent.ENGINE_EVENT
import org.asyncttwiz.server.generated.tables.EngineSeat.ENGINE_SEAT
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY
import org.asyncttwiz.server.generated.tables.MatchRecord.MATCH_RECORD
import org.asyncttwiz.server.generated.tables.PlayerPreparation.PLAYER_PREPARATION
import org.asyncttwiz.server.generated.tables.PodMember.POD_MEMBER
import org.asyncttwiz.server.generated.tables.SavedDeck.SAVED_DECK
import org.jooq.DSLContext
import org.jooq.impl.DSL

/**
 * The game history's records (spec game-history §1–2): one per finished match, written in the transaction that makes its
 * result final, and an Unfinished one for a match in play whose game is deleted (by hand or by the cleanup). Records are
 * never deleted. Each is stored whole (both decks, the names and avatars as they were); a read gives each viewer the
 * names and avatars as they are now and only the deck fields they may see.
 */
internal object MatchRecords {
    private val json = GameWire.json

    /** [game]'s current match, [play], ended now ([at]) with [facts]: its record. Call before the play returns to its
     * lobby (its prepared decks are the decks it was played with). */
    fun finished(sql: DSLContext, summary: GameSummary, play: UUID, facts: EngineGames.MatchFacts, at: OffsetDateTime) {
        val starter = starter(sql, play)
        write(sql, summary, play, at, facts.turns, facts.ending) { account ->
            MatchPlayer(account, facts.results[account.accountId], facts.mulligans[account.accountId] ?: 0,
                facts.life[account.accountId], starter == account.accountId, lossCause = facts.causes[account.accountId])
        }
    }

    /** [summary]'s match in play is ended by its game's deletion ([at]): an Unfinished record with the turn it reached. */
    fun unfinished(sql: DSLContext, summary: GameSummary, at: OffsetDateTime) {
        val play = summary.playId?.let(UUID::fromString) ?: return
        val starter = starter(sql, play)
        val seats = sql.select(ENGINE_SEAT.ACCOUNT_ID, ENGINE_SEAT.MULLIGANS, ENGINE_SEAT.RESUME_AT).from(ENGINE_SEAT)
            .where(ENGINE_SEAT.PLAY_ID.eq(play)).fetch()
        // The turn reached: the furthest either player's local head got (their resume points)
        val turn = seats.mapNotNull { row -> row.value3()?.let { StoredData.orSkip("resume point", play) {
            json.decodeFromString(ResumePoint.serializer(), it).turn } } }.maxOrNull() ?: 0
        val mulligans = seats.associate { it.value1().toString() to it.value2()!! }
        write(sql, summary, play, at, turn, MatchEnding.Unfinished) { account ->
            MatchPlayer(account, mulligans = mulligans[account.accountId], onThePlay = starter == account.accountId)
        }
    }

    private fun starter(sql: DSLContext, play: UUID): String? =
        sql.select(GAME_PLAY.PAYLOAD).from(GAME_PLAY).where(GAME_PLAY.ID.eq(play)).fetchOne(GAME_PLAY.PAYLOAD)
            ?.let { StoredData.orSkip("game play", play) { json.decodeFromString(PlayState.serializer(), it).starter.accountId } }

    private fun write(sql: DSLContext, summary: GameSummary, play: UUID, at: OffsetDateTime, turns: Int, ending: MatchEnding,
        player: (AccountIdentity) -> MatchPlayer) {
        val game = UUID.fromString(summary.id)
        val preparation = org.asyncttwiz.server.game.PreparationStore.read(sql, summary)
        val definitions = preparation.definitions.associate { it.digest to it.definition }
        val podNames = sql.select(PLAYER_PREPARATION.ACCOUNT_ID, PLAYER_PREPARATION.POD_DECK_NAME).from(PLAYER_PREPARATION)
            .where(PLAYER_PREPARATION.GAME_ID.eq(game)).fetch().associate { it.value1().toString() to it.value2() }
        fun deck(account: String): MatchDeck? = preparation.players.firstOrNull { it.accountId == account }?.deck?.let { deck ->
            val main = deck.entries.filter { it.section == SavedDeckSection.MAINBOARD }.mapNotNull { definitions[it.definition] }
            val cover = deck.cover?.let { digest -> definitions[digest]?.let { card ->
                DeckCover(digest, card.name, card.faces.firstOrNull()?.scryfallId, card.faces.firstOrNull()?.imageUri) } }
            MatchDeck(podNames[account] ?: deck.name, cover, DeckColors.of(main), deck.sourceId)
        }
        val started = sql.select(ENGINE_EVENT.CREATED_AT).from(ENGINE_EVENT).where(ENGINE_EVENT.PLAY_ID.eq(play))
            .orderBy(ENGINE_EVENT.ORDINAL).limit(1).fetchOne(ENGINE_EVENT.CREATED_AT) ?: at
        val record = MatchRecord(UUID.randomUUID().toString(), summary.id, summary.gameName, summary.podId, summary.podName,
            started.toInstant().toString(), at.toInstant().toString(), turns,
            listOf(summary.inviter, summary.recipient).map { player(it).copy(deck = deck(it.accountId)) }, ending)
        sql.insertInto(MATCH_RECORD).set(MATCH_RECORD.ID, UUID.fromString(record.id)).set(MATCH_RECORD.PLAY_ID, play)
            .set(MATCH_RECORD.GAME_ID, game).set(MATCH_RECORD.POD_ID, summary.podId?.let(UUID::fromString))
            .set(MATCH_RECORD.INVITER_ID, UUID.fromString(summary.inviter.accountId))
            .set(MATCH_RECORD.RECIPIENT_ID, UUID.fromString(summary.recipient.accountId))
            .set(MATCH_RECORD.STARTED_AT, started).set(MATCH_RECORD.ENDED_AT, at).set(MATCH_RECORD.FINISHED, ending !is MatchEnding.Unfinished)
            .set(MATCH_RECORD.RECORD, json.encodeToString(MatchRecord.serializer(), record))
            .onConflict(MATCH_RECORD.PLAY_ID).doNothing().execute()
    }

    /** Every record of [viewer]'s, newest first (GET /v1/history). */
    fun forAccount(sql: DSLContext, viewer: UUID): List<MatchRecord> =
        visible(sql, viewer, read(sql, MATCH_RECORD.INVITER_ID.eq(viewer).or(MATCH_RECORD.RECIPIENT_ID.eq(viewer))))

    /** [game]'s records of [viewer]'s, newest first (GET /v1/games/{id}/history). */
    fun forGame(sql: DSLContext, viewer: UUID, game: UUID): List<MatchRecord> = visible(sql, viewer,
        read(sql, MATCH_RECORD.GAME_ID.eq(game).and(MATCH_RECORD.INVITER_ID.eq(viewer).or(MATCH_RECORD.RECIPIENT_ID.eq(viewer)))))

    /** [pod]'s matches for one of its members, without any deck (§2.3), and its games in play; null for anyone else. */
    fun forPod(sql: DSLContext, viewer: UUID, pod: UUID): PodMatches? {
        if (!sql.fetchExists(POD_MEMBER, POD_MEMBER.POD_ID.eq(pod).and(POD_MEMBER.ACCOUNT_ID.eq(viewer)))) return null
        val records = identities(sql, read(sql, MATCH_RECORD.POD_ID.eq(pod))).map { record ->
            record.copy(players = record.players.map { it.copy(deck = null) }) }
        val turn = DSL.field("(SELECT max(((s.resume_at)::jsonb ->> 'turn')::int) FROM engine_seat s WHERE s.play_id = {0})",
            Int::class.java, GAME.CURRENT_PLAY)
        val games = sql.select(GAME.ID, GAME.GAME_NAME, GAME.INVITER_ID, GAME.RECIPIENT_ID, turn).from(GAME)
            .where(GAME.POD_ID.eq(pod)).and(GAME.LIFECYCLE.eq("ACTIVE")).orderBy(GAME.ACTIVITY_AT.desc(), GAME.ID).fetch()
        val names = accounts(sql, games.flatMap { listOf(it.value3()!!, it.value4()!!) })
        val playing = games.map { row -> PodMatchInPlay(row.value1().toString(), row.value2()!!,
            listOf(row.value3()!!, row.value4()!!).map { names.getValue(it) }, row.value5() ?: 0) }
        return PodMatches(records, playing)
    }

    private fun read(sql: DSLContext, where: org.jooq.Condition): List<MatchRecord> =
        sql.select(MATCH_RECORD.ID, MATCH_RECORD.RECORD).from(MATCH_RECORD).where(where)
            .orderBy(MATCH_RECORD.ENDED_AT.desc(), MATCH_RECORD.ID).fetch()
            // A record the current code can't read is left out (SYS-052)
            .mapNotNull { row -> StoredData.orSkip("match record", row.value1()) { json.decodeFromString(MatchRecord.serializer(), row.value2()!!) } }

    private fun accounts(sql: DSLContext, ids: Collection<UUID>): Map<UUID, AccountIdentity> =
        if (ids.isEmpty()) emptyMap() else sql.selectFrom(ACCOUNT).where(ACCOUNT.ID.`in`(ids.toSet())).fetch()
            .associate { it.id!! to AccountService.identity(it) }

    /** The players' names and avatars as they are now. */
    private fun identities(sql: DSLContext, records: List<MatchRecord>): List<MatchRecord> {
        val names = accounts(sql, records.flatMap { r -> r.players.map { UUID.fromString(it.account.accountId) } })
        return records.map { r -> r.copy(players = r.players.map { p ->
            p.copy(account = names[UUID.fromString(p.account.accountId)] ?: p.account) }) }
    }

    /**
     * §2.2, 2.4: [viewer]'s own deck, openable while the saved deck with its id exists; the opponent's deck only once that
     * deck (its source) was shown to [viewer] in any game, else left out.
     */
    private fun visible(sql: DSLContext, viewer: UUID, records: List<MatchRecord>): List<MatchRecord> {
        if (records.isEmpty()) return records
        val shown = sql.select(DECK_SHOWN_TO.OWNER_ID, DECK_SHOWN_TO.SOURCE_ID).from(DECK_SHOWN_TO)
            .where(DECK_SHOWN_TO.VIEWER_ID.eq(viewer)).fetch().map { it.value1().toString() to it.value2()!! }.toSet()
        val own = records.mapNotNull { it.player(viewer.toString())?.deck?.sourceId }
            .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }.toSet()
        val saved = if (own.isEmpty()) emptySet() else sql.select(SAVED_DECK.ID).from(SAVED_DECK)
            .where(SAVED_DECK.ACCOUNT_ID.eq(viewer)).and(SAVED_DECK.ID.`in`(own)).fetch(SAVED_DECK.ID).map { it.toString() }.toSet()
        return identities(sql, records).map { record -> record.copy(players = record.players.map { p ->
            val deck = p.deck
            when {
                deck == null -> p
                p.account.accountId == viewer.toString() -> p.copy(deck = deck.copy(openable = deck.sourceId in saved))
                (p.account.accountId to deck.sourceId) in shown -> p.copy(deck = deck.copy(openable = false))
                else -> p.copy(deck = null)
            }
        }) }
    }
}
