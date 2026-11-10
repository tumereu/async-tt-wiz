package org.asyncttwiz.server.pods

import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.activity.ActivityService
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.PodChat.POD_CHAT
import org.asyncttwiz.server.generated.tables.PodChatReadCursor.POD_CHAT_READ_CURSOR as CURSOR
import org.asyncttwiz.server.generated.tables.PodMember.POD_MEMBER
import org.asyncttwiz.server.generated.tables.records.PodChatRecord
import org.jooq.DSLContext
import org.jooq.impl.DSL

/**
 * Pod chat (owner, 2026-11-08): a pod's messages and each member's private read cursor, as a game's chat ([ChatValidation],
 * [ChatPaging]) but outside every game. Callers check membership under the pod's row lock; ordinals are given under the
 * exclusive one, so a page read "after" a cursor never misses a message committed later with a lower ordinal.
 */
internal object PodChatStore {
    private fun message(it: PodChatRecord) = PodChatMessage(it.id.toString(), it.podId.toString(), it.authorId.toString(),
        it.text!!, it.ordinal.toString(), it.createdAt!!.toInstant().toString())

    private fun cursor(sql: DSLContext, pod: UUID, account: UUID): Long =
        sql.select(CURSOR.ORDINAL).from(CURSOR).where(CURSOR.POD_ID.eq(pod)).and(CURSOR.ACCOUNT_ID.eq(account))
            .fetchOne(CURSOR.ORDINAL) ?: 0L

    fun readState(sql: DSLContext, pod: UUID, account: UUID): PodChatRead {
        val cursor = cursor(sql, pod, account)
        val unread = sql.fetchCount(POD_CHAT, POD_CHAT.POD_ID.eq(pod).and(POD_CHAT.ORDINAL.gt(cursor)).and(POD_CHAT.AUTHOR_ID.ne(account)))
        val newest = sql.select(DSL.max(POD_CHAT.ORDINAL)).from(POD_CHAT).where(POD_CHAT.POD_ID.eq(pod)).fetchOne(0, Long::class.java) ?: 0L
        return PodChatRead(pod.toString(), cursor.toString(), newest.toString(), unread)
    }

    /** DRF-012: the other members' messages in the chat of the pod [pod] names that [account] hasn't read, as a column of
     * the caller's own statement (no extra round trip for the pods list). */
    fun unreadField(pod: org.jooq.Field<UUID?>, account: UUID): org.jooq.Field<Int> {
        val read = DSL.coalesce(DSL.field(DSL.select(CURSOR.ORDINAL).from(CURSOR).where(CURSOR.POD_ID.eq(pod))
            .and(CURSOR.ACCOUNT_ID.eq(account))), DSL.inline(0L))
        return DSL.field(DSL.selectCount().from(POD_CHAT).where(POD_CHAT.POD_ID.eq(pod)).and(POD_CHAT.AUTHOR_ID.ne(account))
            .and(POD_CHAT.ORDINAL.gt(read))).`as`("chat_unread")
    }

    fun page(sql: DSLContext, pod: UUID, account: UUID, before: Long? = null, after: Long? = null): PodChatPage {
        require(before == null || after == null)
        val condition = POD_CHAT.POD_ID.eq(pod)
            .and(if (before != null) POD_CHAT.ORDINAL.lt(before) else DSL.trueCondition())
            .and(if (after != null) POD_CHAT.ORDINAL.gt(after) else DSL.trueCondition())
        val rows = sql.selectFrom(POD_CHAT).where(condition)
            .orderBy(if (after != null) POD_CHAT.ORDINAL.asc() else POD_CHAT.ORDINAL.desc()).limit(ChatPaging.SIZE + 1).fetch()
        val messages = rows.take(ChatPaging.SIZE).map(::message).let { if (after == null) it.asReversed() else it }
        val authors = messages.map { UUID.fromString(it.author) }.toSet().let { ids ->
            if (ids.isEmpty()) emptyList() else sql.selectFrom(ACCOUNT).where(ACCOUNT.ID.`in`(ids)).orderBy(ACCOUNT.ID).fetch()
                .map(AccountService::identity)
        }
        return PodChatPage(pod.toString(), messages, authors, readState(sql, pod, account),
            older = if (after == null && rows.size > ChatPaging.SIZE) messages.first().ordinal else null,
            next = if (after != null && rows.size > ChatPaging.SIZE) messages.last().ordinal else null)
    }

    /** Moves [account]'s cursor forward to [message] (never back). Null when [message] isn't one of [pod]'s. */
    fun read(sql: DSLContext, pod: UUID, account: UUID, message: UUID): PodChatRead? {
        val ordinal = sql.select(POD_CHAT.ORDINAL).from(POD_CHAT).where(POD_CHAT.POD_ID.eq(pod)).and(POD_CHAT.ID.eq(message))
            .fetchOne(POD_CHAT.ORDINAL) ?: return null
        sql.insertInto(CURSOR).set(CURSOR.POD_ID, pod).set(CURSOR.ACCOUNT_ID, account).set(CURSOR.ORDINAL, ordinal)
            .onConflict(CURSOR.POD_ID, CURSOR.ACCOUNT_ID).doUpdate()
            .set(CURSOR.ORDINAL, DSL.greatest(CURSOR.ORDINAL, DSL.`val`(ordinal))).execute()
        return readState(sql, pod, account)
    }

    /** What a send did: the stored [message], or why it was refused ([refusal]: "invalid_text", "request_conflict"). */
    class Sent(val message: PodChatMessage? = null, val refusal: String? = null, val repeated: Boolean = false)

    /**
     * Stores [text] by [author] in [pod] (under its exclusive row lock) as message [id]. A retried send of the same message
     * returns it again; the same id with other content is refused. Every other member gets a "New message" notice
     * ([ActivityKind.POD_MESSAGE]). The author has read their own message.
     */
    fun send(sql: DSLContext, pod: UUID, author: UUID, id: UUID, text: String): Sent {
        sql.selectFrom(POD_CHAT).where(POD_CHAT.ID.eq(id)).fetchOne()?.let { existing ->
            return if (existing.podId == pod && existing.authorId == author && existing.text == text) Sent(message(existing), repeated = true)
                else Sent(refusal = "request_conflict")
        }
        if (!ChatValidation.text(text)) return Sent(refusal = "invalid_text")
        val ordinal = (sql.select(DSL.max(POD_CHAT.ORDINAL)).from(POD_CHAT).where(POD_CHAT.POD_ID.eq(pod)).fetchOne(0, Long::class.java) ?: 0L) + 1
        val stored = sql.insertInto(POD_CHAT).set(POD_CHAT.ID, id).set(POD_CHAT.POD_ID, pod).set(POD_CHAT.AUTHOR_ID, author)
            .set(POD_CHAT.TEXT, text).set(POD_CHAT.ORDINAL, ordinal).returning().fetchSingle()
        read(sql, pod, author, id)
        val members = sql.select(POD_MEMBER.ACCOUNT_ID).from(POD_MEMBER).where(POD_MEMBER.POD_ID.eq(pod)).fetch(POD_MEMBER.ACCOUNT_ID)
        for (member in members) ActivityService.record(sql, member!!, ActivityKind.POD_MESSAGE, pod, id, author)
        return Sent(message(stored))
    }
}
