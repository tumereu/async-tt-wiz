package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.encodeToString

class GameplaySessionTest {
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        data class Read(val game: String, val ids: List<String>, val complete: (GameplayBaseline?) -> Unit)
        val reads = mutableListOf<Read>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun baseline(gameId: String, requestIds: List<String>, complete: (GameplayBaseline?) -> Unit): Boolean {
            reads.add(Read(gameId, requestIds, complete)); return true
        }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private class Fixture {
        val data = GameplayFixture(); val wire = Wire(); val posts = ArrayDeque<() -> Unit>()
        var token: String? = "A".repeat(43); var account: String? = data.actor
        val targets = mutableListOf<BoardState?>(); val results = mutableListOf<GameResult>()
        val session: GameSession = GameSession(wire, { token }, { posts.add(it) }, {}, {}, { _, result -> results.add(result) },
            { targets.add(sessionDisplay()) }, accountId = { account })
        private fun sessionDisplay() = session.play(data.game)?.board
        fun drain() { while (posts.isNotEmpty()) posts.removeFirst()() }
        fun message(message: GameServerMessage) { wire.listener.message(message); drain() }
        fun connect(statuses: List<GameRequestStatus> = emptyList(), generation: String = "1", head: GameHead = data.head()) {
            session.watch(listOf(data.game)); wire.listener.opened(); drain(); message(GameWelcome())
            val stream = wire.frames.mapNotNull { when (it) {
                is OpenGameStream -> it.streamId; is ResumeGameStream -> it.streamId; else -> null } }.last()
            message(GameStreamReady(data.game.id, stream, generation, statuses)); message(head)
        }
        fun gameplay(action: PlayAction) = session.gameplay(data.game, data.actor, action)
        fun commands() = wire.frames.filterIsInstance<GameCommand>()
    }
    @Test fun actualSessionSendsMultipleInflightAndResultDeltaOrdersNeverAnimateAcknowledgement() {
        val f = Fixture(); val d = f.data
        assertFalse(f.gameplay(PlayAction.AdjustLife(d.actor, "1"))); f.connect()
        repeat(3) { assertTrue(f.gameplay(PlayAction.AdjustLife(d.actor, "1"))) }
        val commands = f.commands(); assertEquals(3, commands.size); assertTrue(commands.all(GameWire::valid))
        assertEquals("23", d.life(f.session.play(d.game))); assertEquals("20", d.life(f.session.acceptedPlay(d.game)))
        val target = f.session.play(d.game)!!.board; f.targets.clear()
        f.message(GameReceipt(commands[0].requestId, commands[0].scope, "1"))
        f.message(d.result(commands[0], "2")); assertTrue(f.session.pending(d.game.id))
        val first = d.update(d.initial, commands[0], "1", "2")
        f.message(first); val second = d.update(first.play!!, commands[1], "2", "3")
        f.message(second); f.message(d.result(commands[1], "3")); f.message(first); f.message(d.result(commands[1], "3"))
        assertTrue(f.targets.all { it == target }); assertEquals("22", d.life(f.session.acceptedPlay(d.game)))
        assertEquals(2, f.results.size)
        f.message(d.result(commands[2], outcome = "noOp")); assertFalse(f.session.pending(d.game.id))
        assertEquals("22", d.life(f.session.play(d.game)))
    }
    @Test fun httpSummaryCannotBlockActualReplicaAndGapBaselineSerializesInterleavedDeltas() {
        val f = Fixture(); val d = f.data; f.connect()
        val a = d.command(PlayAction.AdjustLife(d.actor, "1")); val first = d.update(d.initial, a, "1", "2")
        val second = d.update(first.play!!, d.command(PlayAction.SetTapped(d.id(9), true)), "2", "3", d.other)
        f.session.watch(listOf(d.game.copy(revision = "3")))
        f.message(second); assertFalse(f.session.ready(d.game.id)); assertEquals(1, f.wire.reads.size)
        f.message(first); assertEquals("21", d.life(f.session.acceptedPlay(d.game)))
        f.wire.reads.single().complete(GameplayBaseline(d.snapshot(), emptyList())); f.drain()
        assertTrue(f.session.ready(d.game.id)); assertEquals(second.play, f.session.acceptedPlay(d.game))
        f.message(d.head()); assertEquals(second.play, f.session.acceptedPlay(d.game))
        assertEquals("3", f.session.summary(d.game).revision)
    }
    @Test fun uncertainWithdrawnOriginalsResumeInOrderWithoutOfflineCreationAndOldCallbacksAreFenced() {
        val f = Fixture(); val d = f.data; f.connect()
        repeat(2) { assertTrue(f.gameplay(PlayAction.AdjustLife(d.actor, "1"))) }
        val originals = f.commands(); f.session.withdrawGameplayPrediction(d.game.id, originals[0].requestId)
        assertEquals("21", d.life(f.session.play(d.game))); val old = f.wire.listener
        old.disconnected(); f.drain(); assertFalse(f.gameplay(PlayAction.AdjustLife(d.actor, "1")))
        f.token = "B".repeat(43)
        f.connect(originals.map { GameRequestStatus(it.requestId, "notAdmitted") }, "2")
        assertEquals(originals.map { it.copy(generation = "2") }, f.commands().drop(2))
        assertEquals("21", d.life(f.session.play(d.game)))
        old.message(d.update(d.initial, originals[0], "1", "2")); f.drain()
        assertEquals("20", d.life(f.session.acceptedPlay(d.game)))
        f.message(d.update(d.initial, originals[0], "1", "2")); assertEquals("22", d.life(f.session.play(d.game)))
        f.account = d.other; f.session.tick()
        assertFalse(f.session.pending(d.game.id)); assertNull(f.session.acceptedPlay(d.game))
        val fresh = Fixture(); fresh.connect(); assertTrue(fresh.commands().isEmpty())
    }
    @Test fun fullHeadRecoversFinalOriginalBeforePublishingAndStaleTransferCannotCrossClear() {
        val f = Fixture(); val d = f.data; f.connect()
        f.gameplay(PlayAction.AdjustLife(d.actor, "5")); val own = f.commands().single()
        val first = d.update(d.initial, own, "1", "2")
        f.message(d.head(first.play!!, "2")); assertFalse(f.session.ready(d.game.id))
        val read = f.wire.reads.single(); assertEquals(listOf(own.requestId), read.ids)
        read.complete(GameplayBaseline(d.snapshot(first), listOf(GameRequestStatus(own.requestId, "final", d.result(own, "2")))))
        f.drain(); assertTrue(f.session.ready(d.game.id)); assertEquals("25", d.life(f.session.play(d.game)))
        assertFalse(f.session.pending(d.game.id)); assertEquals(1, f.commands().size)
        f.message(d.head(first.play!!, "3")); val late = f.wire.reads.last()
        f.session.clear(); late.complete(GameplayBaseline(d.snapshot(first), emptyList())); f.drain()
        assertNull(f.session.play(d.game)); assertFalse(f.session.ready(d.game.id))
    }
    @Test fun newerHeadDuringDownloadCannotMarkStaleBaselineReadyAndAccountSwitchFencesReadBeforeTick() {
        val f = Fixture(); val d = f.data; f.connect()
        val first = d.update(d.initial, d.command(PlayAction.AdjustLife(d.actor, "1")), "1", "2")
        f.message(d.head(first.play!!, "2")); val read = f.wire.reads.single()
        f.message(d.head()); assertFalse(f.session.ready(d.game.id))
        assertFalse(f.gameplay(PlayAction.AdjustLife(d.actor, "1")))
        read.complete(GameplayBaseline(d.snapshot(), emptyList())); f.drain()
        assertFalse(f.session.ready(d.game.id)); assertEquals(d.initial, f.session.acceptedPlay(d.game))
        f.connect(generation = "2"); f.message(d.head(first.play!!, "2")); val next = f.wire.reads.last()
        f.account = d.other
        next.complete(GameplayBaseline(d.snapshot(first), emptyList())); f.drain()
        assertEquals(d.initial, f.session.acceptedPlay(d.game))
        f.session.tick(); assertNull(f.session.acceptedPlay(d.game))
    }
    @Test fun productionHttpReaderStagesVerifiedGzipAndRejectsCorruptChunksWithoutPublishingPartialData() {
        val f = GameplayFixture(); val snapshot = f.snapshot()
        val bytes = GameWire.json.encodeToString(snapshot).toByteArray()
        val chunks = listOf(bytes.copyOfRange(0, bytes.size / 2), bytes.copyOfRange(bytes.size / 2, bytes.size))
        val manifest = GameplayManifest(f.id(500), f.game.id, f.initial.id, "1",
            chunks.mapIndexed { index, part -> GameplayChunk(index, part.size, GameplaySync.digest(part)) }, emptyList())
            .let { it.copy(digest = GameplaySync.manifestDigest(it)) }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var corrupt = false
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val response = when {
                path.endsWith("/baselines") -> GameWire.json.encodeToString(manifest).toByteArray()
                "/chunks/" in path -> {
                    val index = path.substringAfterLast('/').toInt()
                    val part = if (corrupt && index == 1) chunks[index] + byteArrayOf(0) else chunks[index]
                    ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(part) } }.toByteArray()
                }
                else -> byteArrayOf()
            }
            exchange.requestBody.close(); exchange.sendResponseHeaders(if (path == "/v1/socket") 503 else 200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            SocketGameConnection("http://127.0.0.1:${server.address.port}").use { connection ->
                connection.connect("A".repeat(43), object : GameConnection.Listener {
                    override fun opened() = Unit
                    override fun message(message: GameServerMessage) = Unit
                    override fun disconnected(unauthorized: Boolean) = Unit
                })
                fun download(): GameplayBaseline? {
                    val latch = CountDownLatch(1); var result: GameplayBaseline? = null
                    assertTrue(connection.baseline(f.game.id, emptyList()) { result = it; latch.countDown() })
                    assertTrue(latch.await(10, TimeUnit.SECONDS)); return result
                }
                assertEquals(snapshot, download()!!.snapshot)
                corrupt = true; assertNull(download())
            }
        } finally { server.stop(0) }
    }
    @Test fun chatAndPredictedBoardUseOneQueueAndChatRevisionDoesNotCreateOrResetBoardNodes() {
        val f = Fixture(); val d = f.data; f.connect()
        assertTrue(f.gameplay(PlayAction.AdjustLife(d.actor, "2")))
        assertTrue(f.session.sendChat(d.game, "immutable chat"))
        val (edit, chat) = f.commands(); assertTrue(GameWire.valid(chat))
        val first = d.update(d.initial, edit, "1", "2")
        val message = ChatMessage(chat.requestId, d.game.id, d.initial.id, d.actor, "immutable chat", "3", "date")
        f.message(first); f.message(GameRevision(d.game.id, "2", "3", chat.requestId, d.actor, chat.scope,
            d.game.copy(revision = "3"), play = first.play, chatMessage = message))
        assertEquals(first.play, f.session.acceptedPlay(d.game)); assertEquals("22", d.life(f.session.play(d.game)))
        assertEquals(listOf(message), f.session.chat(d.game.id)); assertTrue(f.session.outgoingChat(d.game.id).isEmpty())
        f.message(d.result(edit, "2")); f.message(d.result(chat, "3"))
        assertFalse(f.session.pending(d.game.id)); assertEquals(listOf(edit, chat), f.commands())
        f.session.close()
    }
    @Test fun fullBaselineAndInterleavedChatRestoreMessagesWithoutTerminalCallbackResurrection() {
        val f = Fixture(); val d = f.data; f.connect()
        val chat = ChatMessage(d.id(800), d.game.id, d.initial.id, d.other, "baseline chat", "2", "date")
        val record = GameRevision(d.game.id, "1", "2", chat.id, d.other, GameScope(d.game.id, playId = d.initial.id),
            d.game.copy(revision = "2"), play = d.initial)
        val edit = d.update(d.initial, d.command(PlayAction.AdjustLife(d.actor, "1")), "2", "3")
        val snapshot = d.snapshot(record, edit).let { it.copy(head = it.head.copy(chat = listOf(chat))) }
        f.message(snapshot.head); val read = f.wire.reads.single()
        val tail = chat.copy(id = d.id(801), text = "tail chat", revision = "4")
        f.message(GameRevision(d.game.id, "3", "4", tail.id, d.other, record.scope,
            d.game.copy(revision = "4"), play = edit.play, chatMessage = tail))
        read.complete(GameplayBaseline(snapshot, emptyList())); f.drain()
        assertTrue(f.session.ready(d.game.id)); assertEquals(edit.play, f.session.acceptedPlay(d.game))
        assertEquals(listOf(chat, tail), f.session.chat(d.game.id))
        f.message(snapshot.head.copy(revision = "5", summary = d.game.copy(revision = "5")))
        val late = f.wire.reads.last()
        val removed = d.game.copy(lifecycle = GameLifecycle.DELETED, revision = "6")
        f.message(GameHead(d.game.id, "6", removed))
        late.complete(GameplayBaseline(snapshot, emptyList())); f.drain()
        f.message(snapshot.head)
        assertNull(f.session.acceptedPlay(d.game)); assertTrue(f.session.chat(d.game.id).isEmpty())
        assertEquals(removed, f.session.summary(d.game)); assertFalse(f.session.ready(d.game.id))
        f.session.close()
    }
}
