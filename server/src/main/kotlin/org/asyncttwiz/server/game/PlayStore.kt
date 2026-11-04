package org.asyncttwiz.server.game

import java.security.SecureRandom
import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY
import org.jooq.DSLContext

/** Persistence/entropy adapter. Initialization semantics are pure shared Kotlin. */
internal object PlayStore {
    private val random = SecureRandom()
    private fun seed() = RandomV1.encodeSeed(ByteArray(32).also(random::nextBytes))

    /** The current play as it started: its ids, containers, players and card data (the game's state is the engine's,
     * [EngineGames]). An unreadable one is contained like any stored data (SYS-052). */
    fun read(sql: DSLContext, game: UUID): PlayState? = decode(game, sql.select(GAME_PLAY.PAYLOAD).from(GAME)
        .join(GAME_PLAY).on(GAME_PLAY.ID.eq(GAME.CURRENT_PLAY).and(GAME_PLAY.GAME_ID.eq(GAME.ID)))
        .where(GAME.ID.eq(game)).fetchOne()?.value1())

    /** [read]'s stored payload as a column of a caller's own statement (R6: read with other rows). */
    fun payloadField(game: UUID): org.jooq.Field<String?> = org.jooq.impl.DSL.field(org.jooq.impl.DSL.select(GAME_PLAY.PAYLOAD).from(GAME)
        .join(GAME_PLAY).on(GAME_PLAY.ID.eq(GAME.CURRENT_PLAY).and(GAME_PLAY.GAME_ID.eq(GAME.ID)))
        .where(GAME.ID.eq(game))).`as`("play_payload")

    /** The play of [game] from its stored [payload] (none: null), contained like any stored data (SYS-052). */
    fun decode(game: UUID, payload: String?): PlayState? =
        payload?.let { StoredData.decode("game play", game) { GameWire.stored(PlayState.serializer(), it) } }

    /**
     * I-D56 §11: the play of [game] whose state is the engine's ends once its result is final (a concession, or the
     * engine's result confirmed after both players saw it), as an accepted result ends a play: back to the lobby, the
     * finished decks recorded and revealed.
     */
    fun endEngine(sql: DSLContext, game: GameSummary, requestId: String, result: ResultSuggestion?, summary: (UUID) -> GameSummary) {
        val play = read(sql, UUID.fromString(game.id)) ?: return
        val preparation = GameResultRules.next(PreparationStore.read(sql, game), play, requestId)
        // LOB-111: the game-over notice reads how the play ended from the ended play's result
        sql.update(GAME_PLAY).set(GAME_PLAY.ENDED_REQUEST_ID, UUID.fromString(requestId))
            .set(GAME_PLAY.PAYLOAD, GameWire.json.encodeToString(play.copy(board = play.board.copy(resultSuggestion = result))))
            .where(GAME_PLAY.ID.eq(UUID.fromString(game.playId!!))).and(GAME_PLAY.ENDED_REQUEST_ID.isNull)
            .execute().also { check(it == 1) }
        DeckVisibilityStore.recordFinished(sql, preparation)
        PreparationStore.returnToLobby(sql, preparation)
        DeckVisibilityStore.revealFinished(sql, preparation, summary)
    }

    fun start(sql: DSLContext, game: GameSummary, preparation: LobbyPreparation, requestId: String): PlayState {
        check(StartRules.canStart(game, preparation))
        val state = StartRules.initialize(game, preparation, requestId,
            preparation.players.associate { it.accountId to seed() },
            if (preparation.startingChoice.accountId == null) seed() else null, librarySecret = seed())
        val id = UUID.fromString(state.id)
        sql.insertInto(GAME_PLAY).set(GAME_PLAY.ID, id).set(GAME_PLAY.GAME_ID, UUID.fromString(game.id))
            .set(GAME_PLAY.PREPARATION_ROUND, UUID.fromString(preparation.roundId))
            .set(GAME_PLAY.START_REQUEST_ID, UUID.fromString(requestId)).set(GAME_PLAY.ROOT_ID, UUID.fromString(state.root.id))
            .set(GAME_PLAY.PAYLOAD, GameWire.json.encodeToString(state))
            .set(GAME_PLAY.INITIAL_PAYLOAD, GameWire.json.encodeToString(state)).execute()
        sql.update(GAME).set(GAME.LIFECYCLE, "ACTIVE").set(GAME.CURRENT_PLAY, id)
            .where(GAME.ID.eq(UUID.fromString(game.id))).and(GAME.CURRENT_PLAY.isNull).execute().also { check(it == 1) }
        return state
    }
}
