package org.asyncttwiz.server.game

import java.security.SecureRandom
import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY
import org.jooq.DSLContext

/** Persistence/entropy adapter. Initialization semantics are pure shared Kotlin. */
internal object PlayStore {
    private val random = SecureRandom()
    private fun seed() = RandomV1.encodeSeed(ByteArray(32).also(random::nextBytes))
    fun read(sql: DSLContext, game: UUID): PlayState? = sql.select(GAME_PLAY.PAYLOAD).from(GAME)
        .join(GAME_PLAY).on(GAME_PLAY.ID.eq(GAME.CURRENT_PLAY).and(GAME_PLAY.GAME_ID.eq(GAME.ID)))
        .where(GAME.ID.eq(game)).fetchOne(GAME_PLAY.PAYLOAD)?.let { GameWire.json.decodeFromString(it) }

    fun accept(sql: DSLContext, game: GameSummary, actor: String, command: GameCommand): String? {
        val id = UUID.fromString(game.id)
        val change = GameResultRules.accept(game, read(sql, id), PreparationStore.read(sql, game), actor, command)
        change.preparation?.let { preparation ->
            sql.update(GAME_PLAY).set(GAME_PLAY.ENDED_REQUEST_ID, UUID.fromString(command.requestId))
                .where(GAME_PLAY.ID.eq(UUID.fromString(game.playId!!))).and(GAME_PLAY.ENDED_REQUEST_ID.isNull)
                .execute().also { check(it == 1) }
            PreparationStore.returnToLobby(sql, preparation)
        }
        return change.reason
    }

    fun start(sql: DSLContext, game: GameSummary, preparation: LobbyPreparation, requestId: String) {
        check(StartRules.canStart(game, preparation))
        val state = StartRules.initialize(game, preparation, requestId,
            preparation.players.associate { it.accountId to seed() },
            if (preparation.startingChoice.accountId == null) seed() else null)
        val id = UUID.fromString(state.id)
        sql.insertInto(GAME_PLAY).set(GAME_PLAY.ID, id).set(GAME_PLAY.GAME_ID, UUID.fromString(game.id))
            .set(GAME_PLAY.PREPARATION_ROUND, UUID.fromString(preparation.roundId))
            .set(GAME_PLAY.START_REQUEST_ID, UUID.fromString(requestId)).set(GAME_PLAY.ROOT_ID, UUID.fromString(state.root.id))
            .set(GAME_PLAY.PAYLOAD, GameWire.json.encodeToString(state))
            .set(GAME_PLAY.INITIAL_PAYLOAD, GameWire.json.encodeToString(state)).execute()
        GameplayStore().initialize(sql, state)
        sql.update(GAME).set(GAME.LIFECYCLE, "ACTIVE").set(GAME.CURRENT_PLAY, id)
            .where(GAME.ID.eq(UUID.fromString(game.id))).and(GAME.CURRENT_PLAY.isNull).execute().also { check(it == 1) }
    }
}
