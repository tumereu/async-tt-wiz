package org.asyncttwiz.server.game

import java.util.UUID
import org.asyncttwiz.protocol.DeckVisibility
import org.asyncttwiz.protocol.GameSummary
import org.asyncttwiz.protocol.LobbyPreparation
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.FinishedDeckMatchup.FINISHED_DECK_MATCHUP
import org.asyncttwiz.server.generated.tables.PlayerPreparation.PLAYER_PREPARATION
import org.jooq.DSLContext

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

    /** Sets the selecting player's per-game flag from their default. Call after the deck row exists. */
    fun apply(sql: DSLContext, summary: GameSummary, account: UUID, deckSource: String) {
        val opponent = UUID.fromString(if (summary.inviter.accountId == account.toString()) summary.recipient.accountId
            else summary.inviter.accountId)
        sql.update(PLAYER_PREPARATION).set(PLAYER_PREPARATION.DECK_SHOWN, initiallyShown(sql, account, opponent, deckSource))
            .where(PLAYER_PREPARATION.GAME_ID.eq(UUID.fromString(summary.id))).and(PLAYER_PREPARATION.ACCOUNT_ID.eq(account))
            .execute().also { check(it == 1) }
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

    private fun sourceId(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
