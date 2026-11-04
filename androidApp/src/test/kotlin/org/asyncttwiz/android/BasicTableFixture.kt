package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/** A small play with four cards in the actor's library and every zone of both players, for the table's layout tests;
 * [Worker] and [Wire] are a drained executor and a recording game connection for the session tests. */
internal class BasicTableFixture {
    val data = GameplayFixture()
    fun id(n: Int) = data.id(n)
    val actor = data.actor
    val other = data.other
    val game = data.game
    val definition = data.definition.copy(faces = data.definition.faces.map { it.copy(corner = "2/3", scryfallId = placeholderPrinting("Retained")) })
    fun container(zone: PlayZone, owner: String = actor): String = id(200 + zone.ordinal + if (owner == other) 10 else 0)
    val root = data.initial.copy(definitions = listOf(SubmittedDefinition(DeckJson.digest(definition), definition)),
        board = data.initial.board.copy(cards = (40..43).map { n -> data.initial.board.cards.single().copy(
            id = id(n), definition = DeckJson.digest(definition), containerId = container(PlayZone.LIBRARY),
            corner = "7/8", placement = null, counters = if (n == 40) mapOf(CounterColor.BLUE to "3") else emptyMap(), note = "retained") },
            containers = listOf(actor, other).flatMap { owner -> PlayZone.entries.filter { it != PlayZone.STACK }.map { zone ->
                PlayContainer(container(zone, owner), zone, owner,
                    if (owner == actor && zone == PlayZone.LIBRARY) (40..43).map(::id) else emptyList(),
                    layers = if (owner == actor && zone == PlayZone.LIBRARY) LibraryLayers.initial(id(90), (40..43).map(::id)) else emptyList())
            } } + PlayContainer(container(PlayZone.STACK), PlayZone.STACK)))
    class Worker : AbstractExecutorService() {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.add(command) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
    }
    class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    val worker = Worker()
    val wire = Wire()
}
