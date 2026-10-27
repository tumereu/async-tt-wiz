package org.asyncttwiz.server.game

import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.generated.tables.Definition.DEFINITION
import org.asyncttwiz.server.generated.tables.GameDefinition.GAME_DEFINITION
import org.asyncttwiz.server.generated.tables.PlayerPreparation.PLAYER_PREPARATION
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.jooq.DSLContext

/** Adapter only: pure rules own selection; the existing game transaction owns all writes. */
internal object PreparationStore {
    private val json = GameWire.json
    fun ownPodDeckName(sql: DSLContext, game: UUID, account: UUID): String? =
        sql.select(PLAYER_PREPARATION.POD_DECK_NAME).from(PLAYER_PREPARATION)
            .where(PLAYER_PREPARATION.GAME_ID.eq(game)).and(PLAYER_PREPARATION.ACCOUNT_ID.eq(account))
            .fetchOne(PLAYER_PREPARATION.POD_DECK_NAME)
    fun definitions(sql: DSLContext, digests: Set<String>): List<SubmittedDefinition> =
        sql.select(DEFINITION.DIGEST, DEFINITION.PAYLOAD).from(DEFINITION).where(DEFINITION.DIGEST.`in`(digests))
            .orderBy(DEFINITION.DIGEST).fetch().map {
                SubmittedDefinition(it[DEFINITION.DIGEST]!!, json.decodeFromString<CardDefinition>(it[DEFINITION.PAYLOAD]!!))
            }

    fun read(sql: DSLContext, summary: GameSummary): LobbyPreparation {
        val game = UUID.fromString(summary.id)
        val rows = sql.selectFrom(PLAYER_PREPARATION).where(PLAYER_PREPARATION.GAME_ID.eq(game))
            .fetch().associateBy { it.accountId.toString() }
        val digests = sql.select(GAME_DEFINITION.DIGEST).from(GAME_DEFINITION)
            .where(GAME_DEFINITION.GAME_ID.eq(game)).fetchSet(GAME_DEFINITION.DIGEST)
        val starter = sql.select(GAME.STARTER_CHOICE).from(GAME).where(GAME.ID.eq(game)).fetchSingle(GAME.STARTER_CHOICE)
        return PreparationRules.empty(summary).let { empty -> empty.copy(players = empty.players.map { player ->
            val row = rows[player.accountId]
            player.copy(ready = row?.ready ?: false, deck = row?.deck?.let { json.decodeFromString<PreparedDeck>(it) })
        }, definitions = definitions(sql, digests), starter = starter?.let { json.decodeFromString<StarterChoice>(it) } ?: StarterChoice(),
            podRules = if (summary.podId == null) null else PodGameRules(sql.select(GAME.POD_SIDEBOARD_LIMIT).from(GAME)
                .where(GAME.ID.eq(game)).fetchSingle(GAME.POD_SIDEBOARD_LIMIT))) }
    }

    fun saveReadiness(sql: DSLContext, actor: UUID, state: LobbyPreparation) {
        val game = UUID.fromString(state.gameId)
        sql.update(GAME).set(GAME.STARTER_CHOICE, json.encodeToString(state.startingChoice)).where(GAME.ID.eq(game)).execute()
        val ready = state.players.single { it.accountId == actor.toString() }.ready
        sql.insertInto(PLAYER_PREPARATION).set(PLAYER_PREPARATION.GAME_ID, game).set(PLAYER_PREPARATION.ACCOUNT_ID, actor)
            .set(PLAYER_PREPARATION.READY, ready).onConflict(PLAYER_PREPARATION.GAME_ID, PLAYER_PREPARATION.ACCOUNT_ID)
            .doUpdate().set(PLAYER_PREPARATION.READY, ready).execute()
    }

    fun returnToLobby(sql: DSLContext, state: LobbyPreparation) {
        val game = UUID.fromString(state.gameId)
        check(state.players.all { !it.ready })
        // Deck JSON/definition membership are already retained from the last preparation.
        // Never read saved sources or derive this data from the final play board.
        sql.update(PLAYER_PREPARATION).set(PLAYER_PREPARATION.READY, false)
            .where(PLAYER_PREPARATION.GAME_ID.eq(game)).execute()
        sql.update(GAME).set(GAME.PREPARATION_ROUND, UUID.fromString(state.roundId))
            .set(GAME.CURRENT_PLAY, null as UUID?).set(GAME.LIFECYCLE, "LOBBY")
            .where(GAME.ID.eq(game)).and(GAME.LIFECYCLE.eq("ACTIVE")).execute().also { check(it == 1) }
    }

    fun saveSelection(sql: DSLContext, actor: UUID, state: LobbyPreparation) {
        val game = UUID.fromString(state.gameId)
        val player = state.players.single { it.accountId == actor.toString() }
        val encoded = json.encodeToString(player.deck!!)
        sql.insertInto(PLAYER_PREPARATION).set(PLAYER_PREPARATION.GAME_ID, game)
            .set(PLAYER_PREPARATION.ACCOUNT_ID, actor).set(PLAYER_PREPARATION.READY, player.ready)
            .set(PLAYER_PREPARATION.DECK, encoded)
            .onConflict(PLAYER_PREPARATION.GAME_ID, PLAYER_PREPARATION.ACCOUNT_ID).doUpdate()
            .set(PLAYER_PREPARATION.READY, player.ready).set(PLAYER_PREPARATION.DECK, encoded).execute()
        for (definition in state.definitions) {
            // Selection can normalize old catalog parts into a new game-local physical definition.
            sql.insertInto(DEFINITION).set(DEFINITION.DIGEST, definition.digest)
                .set(DEFINITION.SCHEMA_VERSION, definition.definition.schemaVersion)
                .set(DEFINITION.PAYLOAD, DeckJson.canonical(definition.definition)).onConflictDoNothing().execute()
            sql.insertInto(GAME_DEFINITION).set(GAME_DEFINITION.GAME_ID, game)
                .set(GAME_DEFINITION.DIGEST, definition.digest).onConflictDoNothing().execute()
        }
    }
}
