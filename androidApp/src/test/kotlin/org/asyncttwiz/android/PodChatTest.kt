package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pod chat (owner, 2026-11-08): what is unread, what goes to the server as read, and the chat tabs of a pod game. */
class PodChatTest {
    private val pod = "10000000-0000-4000-8000-000000000001"
    private fun message(n: Int, author: String = "b") = PodChatMessage("m$n", pod, author, "Message $n", n.toString(), "date")
    private fun page(vararg messages: PodChatMessage, read: PodChatRead) =
        PodChatPage(pod, messages.toList(), listOf(AccountIdentity("a", "Alice"), AccountIdentity("b", "Bob")), read)

    // DRF-170: unread counts the other members' messages after the cursor; seen messages count as read at once, before the
    // server knows; nothing is unread before the server's read state is known
    @Test fun unreadFollowsTheServerAndWhatTheViewerHasSeen() {
        assertEquals(0, PodChatView(pod, listOf(message(1))).unread("a"))
        var view = PodChatView(pod).install(page(message(1), message(2, "a"), message(3), message(4), read = PodChatRead(pod, "2", "4", 2)))
        assertEquals(2, view.unread("a"))
        view = view.presented(setOf("m3"))
        assertEquals(1, view.unread("a"))
        assertEquals("m3", view.pendingRead()?.id)
        // A newer page brings a message after the server's count
        view = view.install(page(message(5), read = PodChatRead(pod, "2", "4", 2)))
        assertEquals(2, view.unread("a"))
        view = view.presented(setOf("m4", "m5"))
        assertEquals(0, view.unread("a"))
        assertEquals("m5", view.pendingRead()?.id)
        // The server's answer: read through 5
        view = view.accept(PodChatRead(pod, "5", "5", 0))
        assertNull(view.pendingRead())
        assertEquals(0, view.unread("a"))
        // An older answer arriving late changes nothing
        assertEquals(view, view.accept(PodChatRead(pod, "3", "5", 1)))
        // Only the viewer's own messages after the cursor: nothing unread
        assertEquals(0, view.install(page(message(6, "a"), read = PodChatRead(pod, "6", "6", 0))).unread("a"))
    }

    // DRF-170: a page merges by id in order; the server's copy of a sent message replaces its "Checking delivery…" row
    @Test fun pagesMergeInOrderAndReplaceSendsOnTheirWay() {
        val sending = PodChatView(pod, outgoing = listOf(PodChatOutgoing("m2", "Message 2")))
        val view = sending.install(page(message(2, "a"), message(1), read = PodChatRead(pod, "2", "2", 1)))
            .install(page(message(2, "a"), message(3), read = PodChatRead(pod, "2", "3", 2)))
        assertEquals(listOf("m1", "m2", "m3"), view.messages.map { it.id })
        assertTrue(view.outgoing.isEmpty())
        assertEquals("Bob", view.authors.getValue("b").username)
        assertFalse(view.canSend(" \n "))
        assertTrue(view.canSend("Hello"))
    }

    // LOB-145, LOB-066: the chat opens on Pod only when the pod alone has unread messages; the other tab is highlighted while
    // it holds an unseen message; the table's chat button counts both
    @Test fun chatTabsOfAPodGame() {
        assertEquals(ChatTab.GAME, ChatTabs.opening(pod = true, gameUnread = 0, podUnread = 0))
        assertEquals(ChatTab.POD, ChatTabs.opening(pod = true, gameUnread = 0, podUnread = 2))
        assertEquals(ChatTab.GAME, ChatTabs.opening(pod = true, gameUnread = 1, podUnread = 2))
        assertEquals(ChatTab.GAME, ChatTabs.opening(pod = false, gameUnread = 0, podUnread = 2))
        assertTrue(ChatTabs.highlighted(ChatTab.POD, ChatTab.GAME, gameUnread = 0, podUnread = 1))
        assertFalse(ChatTabs.highlighted(ChatTab.POD, ChatTab.POD, gameUnread = 0, podUnread = 1))
        assertFalse(ChatTabs.highlighted(ChatTab.GAME, ChatTab.POD, gameUnread = 0, podUnread = 1))
        assertTrue(ChatTabs.highlighted(ChatTab.GAME, ChatTab.POD, gameUnread = 1, podUnread = 0))
        assertEquals(3, ChatTabs.buttonUnread(1, 2))
    }

    private val direct = object : AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private val unused = Proxy.newProxyInstance(Pods::class.java.classLoader, arrayOf(Pods::class.java)) { _, method, _ ->
        error("Unexpected call ${method.name}") } as Pods

    /** A server of one pod's chat for [viewer]: what it holds, the reads asked and the sends it took. */
    private inner class FakeChat(val viewer: String = "a") : Pods by unused {
        val messages = mutableListOf<PodChatMessage>()
        var cursor = 0L
        val afters = mutableListOf<String?>()
        var refuse: String? = null
        private fun read() = PodChatRead(pod, cursor.toString(), (messages.maxOfOrNull { it.ordinal.toLong() } ?: 0).toString(),
            messages.count { it.author != viewer && it.ordinal.toLong() > cursor })
        override suspend fun chat(token: String, id: String, after: String?): PodChatPage {
            refuse?.let { throw PodFailure(it) }
            afters += after
            return PodChatPage(pod, messages.filter { after == null || it.ordinal.toLong() > after.toLong() }, emptyList(), read())
        }
        override suspend fun sendChat(token: String, id: String, input: SendPodChat): PodChatMessage {
            refuse?.let { throw PodFailure(it) }
            return PodChatMessage(input.requestId, pod, viewer, input.text, (messages.size + 1).toString(), "date").also {
                messages += it; cursor = it.ordinal.toLong() }
        }
        override suspend fun readChat(token: String, id: String, input: ReadChat): PodChatRead {
            cursor = maxOf(cursor, messages.first { it.id == input.messageId }.ordinal.toLong()); return read()
        }
        override fun close() = Unit
    }

    // DRF-170, DRF-171: the controller reads the newest page, then only what is newer; seen messages move the server's
    // cursor; a send shows as being sent and then as the server's message; a refused chat is unavailable
    @Test fun controllerReadsSendsAndMovesTheCursor() {
        val server = FakeChat().apply { messages += listOf(message(1), message(2)) }
        val chat = PodChatController(server, { "token" }, direct, { it() })
        chat.refresh(pod)
        assertEquals(2, chat.view(pod).unread("a"))
        server.messages += message(3)
        chat.refresh(pod)
        assertEquals(listOf(null, "2"), server.afters)
        assertEquals(3, chat.view(pod).unread("a"))
        chat.presented(pod, setOf("m1", "m2", "m3"))
        assertEquals(3, server.cursor)
        assertEquals(0, chat.view(pod).unread("a"))
        chat.compose(pod, "My reply")
        chat.send(pod)
        assertEquals("", chat.view(pod).draft)
        assertEquals(listOf("Message 1", "Message 2", "Message 3", "My reply"), chat.view(pod).messages.map { it.text })
        assertTrue(chat.view(pod).outgoing.isEmpty())
        server.refuse = "network"
        chat.send(pod, "Lost", fromDraft = false)
        assertEquals(listOf(PodChatOutgoing(chat.view(pod).outgoing.single().requestId, "Lost", failed = true)), chat.view(pod).outgoing)
        server.refuse = "not_found"
        chat.refresh(pod)
        assertTrue(chat.view(pod).unavailable)
        chat.clear()
        assertEquals(PodChatView(pod), chat.view(pod))
    }
}
