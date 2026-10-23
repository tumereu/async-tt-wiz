package org.asyncttwiz.server.game

import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.generated.tables.GameChat.GAME_CHAT
import org.asyncttwiz.server.generated.tables.records.GameRecord
import org.jooq.DSLContext

internal object ChatStore {
    fun read(sql: DSLContext, game: UUID): List<ChatMessage> = sql.selectFrom(GAME_CHAT)
        .where(GAME_CHAT.GAME_ID.eq(game)).orderBy(GAME_CHAT.REVISION).fetch().map {
            ChatMessage(it.id.toString(), it.gameId.toString(), it.playId.toString(), it.authorId.toString(),
                it.text!!, it.revision.toString(), it.createdAt!!.toInstant().toString())
        }

    fun apply(sql: DSLContext, game: GameRecord, account: UUID, command: GameCommand): String? {
        if (game.lifecycle != "ACTIVE") return "lifecycleEnded"
        if (command.scope.playId != game.currentPlay.toString()) return "inapplicable"
        val text = (command.action as GameAction.SendChat).text
        if (!ChatValidation.text(text)) return "invalidInput"
        sql.insertInto(GAME_CHAT).set(GAME_CHAT.ID, UUID.fromString(command.requestId))
            .set(GAME_CHAT.GAME_ID, game.id).set(GAME_CHAT.PLAY_ID, game.currentPlay)
            .set(GAME_CHAT.AUTHOR_ID, account).set(GAME_CHAT.TEXT, text)
            .set(GAME_CHAT.REVISION, Math.addExact(game.revision!!, 1)).execute()
        return null
    }
}
