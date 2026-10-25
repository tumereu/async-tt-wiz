package org.asyncttwiz.server.game

import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.generated.tables.GameChat.GAME_CHAT
import org.asyncttwiz.server.generated.tables.records.GameRecord
import org.jooq.DSLContext

internal object ChatStore {
    private fun message(it: org.asyncttwiz.server.generated.tables.records.GameChatRecord) =
        ChatMessage(it.id.toString(), it.gameId.toString(), it.playId.toString(), it.authorId.toString(),
            it.text!!, it.revision.toString(), it.createdAt!!.toInstant().toString())

    fun sync(sql: DSLContext, game: UUID): ChatSync = sql.select(GAME_CHAT.ID, GAME_CHAT.REVISION).from(GAME_CHAT)
        .where(GAME_CHAT.GAME_ID.eq(game)).orderBy(GAME_CHAT.REVISION.desc()).limit(1).fetchOne()
        ?.let { ChatSync(it.value2().toString(), it.value1().toString()) } ?: ChatSync()

    fun page(sql: DSLContext, game: UUID, before: Long? = null, after: Long? = null): ChatPage {
        require(before == null || after == null)
        val condition = GAME_CHAT.GAME_ID.eq(game)
            .and(if (before != null) GAME_CHAT.REVISION.lt(before) else org.jooq.impl.DSL.trueCondition())
            .and(if (after != null) GAME_CHAT.REVISION.gt(after) else org.jooq.impl.DSL.trueCondition())
        val rows = sql.selectFrom(GAME_CHAT).where(condition)
            .orderBy(if (after != null) GAME_CHAT.REVISION.asc() else GAME_CHAT.REVISION.desc()).limit(ChatPaging.SIZE + 1).fetch()
        val messages = rows.take(ChatPaging.SIZE).map(::message).let { if (after == null) it.asReversed() else it }
        return ChatPage(game.toString(), messages, sync(sql, game),
            older = if (after == null && rows.size > ChatPaging.SIZE) messages.first().revision else null,
            next = if (after != null && rows.size > ChatPaging.SIZE) messages.last().revision else null)
    }

    data class Change(val reason: String? = null, val message: ChatMessage? = null)
    fun apply(sql: DSLContext, game: GameRecord, account: UUID, command: GameCommand): Change {
        if (game.lifecycle != "ACTIVE") return Change("lifecycleEnded")
        if (command.scope.playId != game.currentPlay.toString()) return Change("inapplicable")
        val text = (command.action as GameAction.SendChat).text
        if (!ChatValidation.text(text)) return Change("invalidInput")
        val inserted = sql.insertInto(GAME_CHAT).set(GAME_CHAT.ID, UUID.fromString(command.requestId))
            .set(GAME_CHAT.GAME_ID, game.id).set(GAME_CHAT.PLAY_ID, game.currentPlay)
            .set(GAME_CHAT.AUTHOR_ID, account).set(GAME_CHAT.TEXT, text)
            .set(GAME_CHAT.REVISION, Math.addExact(game.revision!!, 1)).returning().fetchSingle()
        return Change(message = message(inserted))
    }
}
