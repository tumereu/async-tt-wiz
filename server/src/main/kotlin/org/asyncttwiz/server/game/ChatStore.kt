package org.asyncttwiz.server.game

import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.generated.tables.ActivitySummary.ACTIVITY_SUMMARY
import org.asyncttwiz.server.generated.tables.GameChat.GAME_CHAT
import org.asyncttwiz.server.generated.tables.GameChatReadCursor.GAME_CHAT_READ_CURSOR
import org.asyncttwiz.server.generated.tables.records.GameRecord
import org.jooq.DSLContext

internal object ChatStore {
    fun readState(sql: DSLContext, game: UUID, account: UUID): ChatReadState {
        val cursor = sql.select(GAME_CHAT_READ_CURSOR.REVISION).from(GAME_CHAT_READ_CURSOR)
            .where(GAME_CHAT_READ_CURSOR.GAME_ID.eq(game)).and(GAME_CHAT_READ_CURSOR.ACCOUNT_ID.eq(account))
            .fetchOne(GAME_CHAT_READ_CURSOR.REVISION) ?: 0L
        val unread = sql.fetchCount(GAME_CHAT, GAME_CHAT.GAME_ID.eq(game)
            .and(GAME_CHAT.REVISION.gt(cursor)).and(GAME_CHAT.AUTHOR_ID.ne(account)))
        // LOB-138: the other player's cursor, so the author's chat marks their messages read
        val peer = sql.select(org.jooq.impl.DSL.max(GAME_CHAT_READ_CURSOR.REVISION)).from(GAME_CHAT_READ_CURSOR)
            .where(GAME_CHAT_READ_CURSOR.GAME_ID.eq(game)).and(GAME_CHAT_READ_CURSOR.ACCOUNT_ID.ne(account))
            .fetchOne(0, Long::class.java) ?: 0L
        return ChatReadState(game.toString(), cursor.toString(), sync(sql, game).revision, unread, peer.toString())
    }

    fun read(sql: DSLContext, game: UUID, account: UUID, message: UUID): ChatReadState {
        val revision = sql.select(GAME_CHAT.REVISION).from(GAME_CHAT)
            .where(GAME_CHAT.GAME_ID.eq(game)).and(GAME_CHAT.ID.eq(message))
            .fetchOne(GAME_CHAT.REVISION) ?: throw GameProtocolFailure("invalidInput")
        sql.insertInto(GAME_CHAT_READ_CURSOR).set(GAME_CHAT_READ_CURSOR.GAME_ID, game)
            .set(GAME_CHAT_READ_CURSOR.ACCOUNT_ID, account).set(GAME_CHAT_READ_CURSOR.REVISION, revision)
            .onConflict(GAME_CHAT_READ_CURSOR.GAME_ID, GAME_CHAT_READ_CURSOR.ACCOUNT_ID).doUpdate()
            .set(GAME_CHAT_READ_CURSOR.REVISION, org.jooq.impl.DSL.greatest(GAME_CHAT_READ_CURSOR.REVISION,
                org.jooq.impl.DSL.`val`(revision))).execute()
        return readState(sql, game, account).also { state ->
            // LOB-072 (owner, 2026-11-07): " · new message" follows the chat's read state: it goes once nothing is unread
            if (state.unread == 0) sql.update(ACTIVITY_SUMMARY).set(ACTIVITY_SUMMARY.NEW_MESSAGE, false)
                .where(ACTIVITY_SUMMARY.ACCOUNT_ID.eq(account)).and(ACTIVITY_SUMMARY.GAME_ID.eq(game))
                .and(ACTIVITY_SUMMARY.KIND.eq("GAME")).execute()
        }
    }

    private fun message(it: org.asyncttwiz.server.generated.tables.records.GameChatRecord) =
        ChatMessage(it.id.toString(), it.gameId.toString(), it.playId.toString(), it.authorId.toString(),
            it.text!!, it.revision.toString(), it.createdAt!!.toInstant().toString())

    fun sync(sql: DSLContext, game: UUID): ChatSync = sql.select(GAME_CHAT.ID, GAME_CHAT.REVISION).from(GAME_CHAT)
        .where(GAME_CHAT.GAME_ID.eq(game)).orderBy(GAME_CHAT.REVISION.desc()).limit(1).fetchOne()
        ?.let { ChatSync(it.value2().toString(), it.value1().toString()) } ?: ChatSync()

    /** [sync] as two columns of a caller's own statement (R6: read with other rows): the newest message's id and revision. */
    fun syncFields(game: UUID): Pair<org.jooq.Field<UUID?>, org.jooq.Field<Long?>> =
        org.jooq.impl.DSL.field(org.jooq.impl.DSL.select(GAME_CHAT.ID).from(GAME_CHAT).where(GAME_CHAT.GAME_ID.eq(game))
            .orderBy(GAME_CHAT.REVISION.desc()).limit(1)).`as`("chat_id") to
        org.jooq.impl.DSL.field(org.jooq.impl.DSL.select(GAME_CHAT.REVISION).from(GAME_CHAT).where(GAME_CHAT.GAME_ID.eq(game))
            .orderBy(GAME_CHAT.REVISION.desc()).limit(1)).`as`("chat_revision")

    /** The [sync] of the newest message's [id] and [revision] (none: null). */
    fun sync(id: UUID?, revision: Long?): ChatSync = if (id == null) ChatSync() else ChatSync(revision.toString(), id.toString())

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
