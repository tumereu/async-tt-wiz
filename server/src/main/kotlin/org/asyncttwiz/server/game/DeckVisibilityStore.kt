package org.asyncttwiz.server.game

import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.FinishedDeckMatchup.FINISHED_DECK_MATCHUP
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.GameRevision.GAME_REVISION
import org.asyncttwiz.server.generated.tables.PlayerPreparation.PLAYER_PREPARATION
import org.jooq.DSLContext
import org.jooq.impl.DSL

/** The account's deck visibility default, applied when a deck is selected for a game.
 * A deck is identified by its stable source: the saved deck id, or the pod id for a pod deck. */
internal object DeckVisibilityStore {
    fun setting(sql: DSLContext, account: UUID): DeckVisibility =
        sql.select(ACCOUNT.DECK_VISIBILITY).from(ACCOUNT).where(ACCOUNT.ID.eq(account)).fetchOne(ACCOUNT.DECK_VISIBILITY)
            ?.let { stored -> DeckVisibility.entries.firstOrNull { it.name == stored } } ?: DeckVisibility.AFTER_FIRST_GAME

    fun save(sql: DSLContext, account: UUID, visibility: DeckVisibility) {
        sql.update(ACCOUNT).set(ACCOUNT.DECK_VISIBILITY, visibility.name).where(ACCOUNT.ID.eq(account)).execute()
    }

    /** Whether [account]'s deck starts shown in a game against [opponent]. */
    fun initiallyShown(sql: DSLContext, account: UUID, opponent: UUID, deckSource: String): Boolean =
        when (setting(sql, account)) {
            DeckVisibility.VISIBLE -> true
            DeckVisibility.HIDDEN -> false
            DeckVisibility.AFTER_FIRST_GAME -> sourceId(deckSource)?.let { source ->
                sql.fetchExists(sql.selectOne().from(FINISHED_DECK_MATCHUP)
                    .where(FINISHED_DECK_MATCHUP.ACCOUNT_ID.eq(account)).and(FINISHED_DECK_MATCHUP.OPPONENT_ID.eq(opponent))
                    .and(FINISHED_DECK_MATCHUP.DECK_SOURCE_ID.eq(source)))
            } ?: false
        }

    /** Sets the selecting player's per-game flag from their default; a new deck forgets the player's own toggle choice.
     * Call after the deck row exists. */
    fun apply(sql: DSLContext, summary: GameSummary, account: UUID, deckSource: String) {
        val opponent = UUID.fromString(if (summary.inviter.accountId == account.toString()) summary.recipient.accountId
            else summary.inviter.accountId)
        sql.update(PLAYER_PREPARATION).set(PLAYER_PREPARATION.DECK_SHOWN, initiallyShown(sql, account, opponent, deckSource))
            .set(PLAYER_PREPARATION.DECK_SHOWN_CHOSEN, false)
            .where(PLAYER_PREPARATION.GAME_ID.eq(UUID.fromString(summary.id))).and(PLAYER_PREPARATION.ACCOUNT_ID.eq(account))
            .execute().also { check(it == 1) }
    }

    /** The player switched this game's Show deck toggle themselves: [revealFinished] leaves the game alone. */
    fun chosen(sql: DSLContext, game: UUID, account: UUID) {
        sql.update(PLAYER_PREPARATION).set(PLAYER_PREPARATION.DECK_SHOWN_CHOSEN, true)
            .where(PLAYER_PREPARATION.GAME_ID.eq(game)).and(PLAYER_PREPARATION.ACCOUNT_ID.eq(account)).execute()
    }

    /** A play ended with an accepted result: each player has now finished a game with their deck against the other. */
    fun recordFinished(sql: DSLContext, preparation: LobbyPreparation) {
        for (player in preparation.players) {
            val source = player.deck?.sourceId?.let(::sourceId) ?: continue
            val opponent = preparation.players.singleOrNull { it.accountId != player.accountId } ?: continue
            sql.insertInto(FINISHED_DECK_MATCHUP).set(FINISHED_DECK_MATCHUP.ACCOUNT_ID, UUID.fromString(player.accountId))
                .set(FINISHED_DECK_MATCHUP.OPPONENT_ID, UUID.fromString(opponent.accountId))
                .set(FINISHED_DECK_MATCHUP.DECK_SOURCE_ID, source).onConflictDoNothing().execute()
        }
    }

    /** After [recordFinished] and the finished game's return to its lobby: with "Show after first game", each player's
     * finished deck is now shown to the opponent in every lobby game of this pair holding that deck, the finished game
     * included, unless the player switched that game's toggle themselves. Other games get a revision of their own; the
     * finished game's revision is written by its command. Started plays keep their flag until they return to the lobby. */
    fun revealFinished(sql: DSLContext, preparation: LobbyPreparation, summary: (UUID) -> GameSummary) {
        val finished = UUID.fromString(preparation.gameId)
        val (first, second) = preparation.players.map { UUID.fromString(it.accountId) }.takeIf { it.size == 2 } ?: return
        val pair = GAME.INVITER_ID.eq(first).and(GAME.RECIPIENT_ID.eq(second)).or(GAME.INVITER_ID.eq(second).and(GAME.RECIPIENT_ID.eq(first)))
        // The finished game's lock is held. Every other game is locked in identity order; a lobby game's own command
        // never waits for another game, and a game finishing concurrently is not in the lobby yet.
        val games = sql.selectFrom(GAME).where(pair).and(GAME.LIFECYCLE.eq("LOBBY")).orderBy(GAME.ID).forUpdate().fetch()
            .associateBy { it.id!! }
        val revealed = mutableMapOf<UUID, UUID>()
        for (player in preparation.players) {
            val account = UUID.fromString(player.accountId)
            val source = player.deck?.sourceId?.let(::sourceId) ?: continue
            if (setting(sql, account) != DeckVisibility.AFTER_FIRST_GAME) continue
            val rows = sql.select(PLAYER_PREPARATION.GAME_ID, PLAYER_PREPARATION.DECK).from(PLAYER_PREPARATION)
                .where(PLAYER_PREPARATION.GAME_ID.`in`(games.keys)).and(PLAYER_PREPARATION.ACCOUNT_ID.eq(account))
                .and(PLAYER_PREPARATION.DECK_SHOWN.isFalse).and(PLAYER_PREPARATION.DECK_SHOWN_CHOSEN.isFalse)
                .and(PLAYER_PREPARATION.DECK.isNotNull).fetch()
                .filter { sourceId(GameWire.json.decodeFromString<PreparedDeck>(it.value2()!!).sourceId) == source }
            for (row in rows) {
                sql.update(PLAYER_PREPARATION).set(PLAYER_PREPARATION.DECK_SHOWN, true)
                    .where(PLAYER_PREPARATION.GAME_ID.eq(row.value1())).and(PLAYER_PREPARATION.ACCOUNT_ID.eq(account)).execute()
                revealed[row.value1()!!] = account
            }
        }
        for ((id, author) in revealed) {
            if (id == finished) continue
            val game = games.getValue(id)
            sql.update(GAME).set(GAME.REVISION, game.revision!! + 1).set(GAME.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(GAME.ID.eq(id)).execute()
            val next = summary(id)
            val revision = GameRevision(next.id, game.revision.toString(), next.revision, UUID.randomUUID().toString(),
                author.toString(), GameScope(next.id, next.preparationRound), next, PreparationStore.read(sql, next))
            sql.insertInto(GAME_REVISION).set(GAME_REVISION.GAME_ID, id).set(GAME_REVISION.REVISION, game.revision!! + 1)
                .set(GAME_REVISION.PAYLOAD, GameWire.encode(revision)).execute()
        }
    }

    private fun sourceId(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
