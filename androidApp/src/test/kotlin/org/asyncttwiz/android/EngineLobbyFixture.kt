package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The app's lobby and session ([LobbyController], [GameSession]) with an open game played on engine state, as the server
 * opens it: the game's stream is authorized and its engine head ([GameHead.engine], [EngineGameFixture]'s start: both
 * players at the simultaneous mulligans) loads the game on the session's engine game ([EngineWorker.Direct]). The table
 * screen ([TableScreen]) then shows [EngineTableScreen]. [me] is Alice by default.
 */
internal class EngineLobbyFixture(val me: String = TableStatesFixture.a) : AutoCloseable {
    val f = EngineGameFixture()
    val game: GameSummary = f.game.copy(lifecycle = GameLifecycle.ACTIVE, playId = f.base.id,
        preparationRound = f.base.preparationRound, revision = "1")
    val other: String get() = if (me == f.a) f.b else f.a
    val worker = BasicTableFixture.Worker()
    val wire = BasicTableFixture.Wire()
    var listReads = 0
    val api = object : Lobbies {
        override suspend fun contacts(token: String, after: String?) = ContactList(emptyList())
        override suspend fun games(token: String, invitations: Boolean, after: String?): GameList {
            listReads++
            return GameList(if (invitations) emptyList() else listOf(game))
        }
        override suspend fun head(token: String, id: String) = game
        override suspend fun search(token: String, query: String) = UserSearch(emptyList())
        override suspend fun add(token: String, input: AddContact): AccountIdentity = error("not used")
        override suspend fun create(token: String, input: CreateInvitation): GameSummary = error("not used")
        override fun close() = Unit
    }
    val lobby = LobbyController(api, { "A".repeat(43) }, worker, { it() }, gameConnection = wire, accountId = { me },
        engineWorker = EngineWorker.Direct)

    /** The engine head at the game's start, at [revision], with the given [chat]; [table] changes the shared table state
     * there (a pending result suggestion, say). With [otherKept] the other player's keep follows the start, shown to both
     * (the timeline then has an event to look at). */
    fun head(revision: String = "1", chat: List<ChatMessage> = emptyList(), otherKept: Boolean = false,
        table: (org.asyncttwiz.protocol.game.TableState) -> org.asyncttwiz.protocol.game.TableState = { it }): GameHead {
        if (!otherKept) {
            val model = f.model.copy().let { org.asyncttwiz.protocol.game.GameModel(it.engine, table(it.table)) }
            return GameHead(game.id, revision, game.copy(revision = revision), play = f.base,
                engine = f.snapshot().copy(state = f.codec.encodeModel(model)), chat = chat)
        }
        val keep = org.asyncttwiz.protocol.game.EventBuilder(f.model.copy(), DeviceRandom.uuid(), f.start.id, other).run {
            perform(arcanum.engine.input.Input.SetupAnswer(arcanum.engine.state.PlayerId(other), arcanum.engine.decision.Answer.Option("Keep")))
            answerRandoms(); build()
        }
        val after = f.model.copy().also { it.apply(keep) }.let { org.asyncttwiz.protocol.game.GameModel(it.engine, table(it.table)) }
        return GameHead(game.id, revision, game.copy(revision = revision), play = f.base,
            engine = EngineSnapshot(f.codec.encodeDefinitions(), f.snapshot().rootId, keep.id, f.codec.encodeModel(after),
                localHeads = mapOf(f.a to keep.id, f.b to keep.id), baseId = f.start.id,
                base = f.codec.encodeModel(f.model.copy().let { org.asyncttwiz.protocol.game.GameModel(it.engine, table(it.table)) }),
                events = listOf(f.codec.encodeEvent(keep))), chat = chat)
    }

    fun message(message: GameServerMessage) = wire.listener.message(message)
    fun commands() = wire.frames.filterIsInstance<GameCommand>()

    /** Opens the game: the stream is authorized and [head] loads its engine game. */
    fun connect(head: GameHead = head()) {
        lobby.open(game.id); worker.drain(); wire.listener.opened()
        message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
        message(GameStreamReady(game.id, wire.frames.filterIsInstance<OpenGameStream>().last().streamId, "1"))
        message(head)
        worker.drain()
        assertTrue(game.id in lobby.state.connectedGames)
        assertNotNull(lobby.engineGame(), "the engine head loads the game")
    }

    /** [me] keeps their opening hand (Bob's keep assumed while he is away): the timeline then has events to look at. */
    fun keep() {
        val game = assertNotNull(lobby.engineGame())
        game.tick(1_000); game.keep(emptyList()); game.tick(2_000)
        lobby.engineSent(); worker.drain()
    }

    override fun close() { lobby.close(); worker.drain() }
}
