package org.asyncttwiz.server.game

import java.security.SecureRandom
import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY
import org.asyncttwiz.server.generated.tables.GameplayTreeControl.GAMEPLAY_TREE_CONTROL
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL

/** Persistence/entropy adapter. Initialization semantics are pure shared Kotlin. */
internal object PlayStore {
    private val random = SecureRandom()
    private fun seed() = RandomV1.encodeSeed(ByteArray(32).also(random::nextBytes))
    private fun control(kind: String) = DSL.field(DSL.select(GAMEPLAY_TREE_CONTROL.PAYLOAD).from(GAMEPLAY_TREE_CONTROL)
        .where(GAMEPLAY_TREE_CONTROL.PLAY_ID.eq(GAME_PLAY.ID)).and(GAMEPLAY_TREE_CONTROL.KIND.eq(kind))
        .and(GAMEPLAY_TREE_CONTROL.ID.eq("current")))

    /** The current play, refused unless it passes the same integrity check as startup recovery: a damaged game is
     * contained like an unreadable one (SYS-052). Only explicit maintenance reads it [unverified] to rebuild it. */
    fun read(sql: DSLContext, game: UUID, unverified: Boolean = false): PlayState? {
        val current = control("current"); val branch = control("selected-branch")
        val row = sql.select(GAME_PLAY.PAYLOAD, GAME_PLAY.CHECKPOINT_VERSION, GAME_PLAY.CHECKPOINT_REVISION,
            GAME_PLAY.CHECKPOINT_DIGEST, current, branch).from(GAME)
            .join(GAME_PLAY).on(GAME_PLAY.ID.eq(GAME.CURRENT_PLAY).and(GAME_PLAY.GAME_ID.eq(GAME.ID)))
            .where(GAME.ID.eq(game)).fetchOne() ?: return null
        val payload = row.value1()!!
        val state = StoredData.decode("game play", game) { GameWire.json.decodeFromString<PlayState>(payload) }
        if (unverified) return state
        GameplayEndpoint.intactCheckpoint(game, row.value2(), row.value3(), row.value4(), payload)
        fun pointer(value: JSONB?) = value?.let { StoredData.decode("game", game) { GameWire.json.decodeFromString<String>(it.data()) } }
        GameplayEndpoint.intactPointers(state, pointer(row.value5()), pointer(row.value6()))
        return state
    }

    fun accept(sql: DSLContext, game: GameSummary, actor: String, command: GameCommand, summary: (UUID) -> GameSummary): String? {
        val id = UUID.fromString(game.id)
        val change = GameResultRules.accept(game, read(sql, id), PreparationStore.read(sql, game), actor, command)
        change.preparation?.let { preparation ->
            sql.update(GAME_PLAY).set(GAME_PLAY.ENDED_REQUEST_ID, UUID.fromString(command.requestId))
                .where(GAME_PLAY.ID.eq(UUID.fromString(game.playId!!))).and(GAME_PLAY.ENDED_REQUEST_ID.isNull)
                .execute().also { check(it == 1) }
            DeckVisibilityStore.recordFinished(sql, preparation)
            PreparationStore.returnToLobby(sql, preparation)
            DeckVisibilityStore.revealFinished(sql, preparation, summary)
        }
        return change.reason
    }

    fun start(sql: DSLContext, game: GameSummary, preparation: LobbyPreparation, requestId: String) {
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
        GameplayStore().initialize(sql, state)
        sql.update(GAME).set(GAME.LIFECYCLE, "ACTIVE").set(GAME.CURRENT_PLAY, id)
            .where(GAME.ID.eq(UUID.fromString(game.id))).and(GAME.CURRENT_PLAY.isNull).execute().also { check(it == 1) }
    }
}
