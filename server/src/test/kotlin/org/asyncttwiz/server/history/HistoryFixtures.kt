package org.asyncttwiz.server.history

import arcanum.engine.cards.Cards
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.state.CardId
import arcanum.engine.state.PlayerId
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.game.GameService
import org.asyncttwiz.server.game.concession
import org.asyncttwiz.server.game.engineSeats
import org.asyncttwiz.server.lobby.LobbyService
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** A clock the test moves (game history cleanup: days pass without waiting). */
internal class TestClock(var instant: Instant = Instant.now()) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = instant
    fun advance(duration: java.time.Duration) { instant = instant.plus(duration) }
}

/** A database, a running service runtime, a game service and the cleanup on one [clock]. */
/** HistoryWorld.deck's spell of each colour, cards of the test cards' catalog. */
internal val SPELLS = mapOf("R" to "Storm Spark", "G" to "Test Bear", "U" to "Blue Note")

internal class HistoryWorld(val db: TestPostgres, val runtime: ServiceRuntime, val clock: TestClock) {
    val games = GameService(runtime, clock)
    val cleanup = GameCleanup(runtime, clock)
    val lobby = LobbyService(runtime)
    private val accounts = AccountService(runtime)
    private fun id() = UUID.randomUUID().toString()

    fun account(name: String): AccountService.Principal = session(name).first

    /** A new account [name] and its session token. */
    fun session(name: String): Pair<AccountService.Principal, String> {
        accounts.register(name, "$name@example.invalid", "synthetic password")
        val token = accounts.login("$name@example.invalid", "synthetic password")!!.token
        return accounts.authenticate(token)!! to token
    }

    fun <T> sql(work: (DSLContext) -> T): T = runtime.transaction { connection -> work(DSL.using(connection, SQLDialect.POSTGRES)) }
    fun execute(statement: String, vararg values: Any?) { sql { it.execute(statement, *values) } }
    fun number(statement: String, vararg values: Any?): Long = sql { (it.fetchValue(statement, *values) as Number).toLong() }

    /** A saved deck [name] of [actor]'s, of the test cards' catalog: Main has a spell of [color] ([SPELLS]) and a land, Side a
     * colourless card. */
    fun deck(actor: AccountService.Principal, name: String, color: String = "R"): SavedDeck {
        val spell = org.asyncttwiz.server.TestPrintings.id(SPELLS.getValue(color))
        val decks = DeckService(runtime)
        val created = GameWire.json.decodeFromString<SavedDeck>(decks.create(actor, CreateDeck(id(), name, listOf(
            org.asyncttwiz.server.TestPrintings.entry(SPELLS.getValue(color), 20), org.asyncttwiz.server.TestPrintings.entry("Mountain", 20),
            org.asyncttwiz.server.TestPrintings.entry("Idle Glimmer", 2, SavedDeckSection.SIDEBOARD)))).body)
        // Its cover is the spell
        val covered = decks.update(actor, created.deck.id, UpdateDeck(id(), cover = spell))
        assertEquals(200, covered.status, covered.body)
        return GameWire.json.decodeFromString(decks.read(actor, created.deck.id).body)
    }

    /** One player's connection to [game]'s requests. */
    inner class Stream(val actor: AccountService.Principal, val game: GameSummary) {
        val hello = GameHello(id(), id()); val connection: UUID = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, game.id, id())
        fun command(action: GameAction): GameCommand {
            val summary = games.head(actor, game.id).summary
            val scope = if (action == GameAction.AcknowledgeResult) GameScope(game.id)
                else if (action is GameAction.Submit || action.isEngine || action is GameAction.SendChat || action == GameAction.DeleteGame)
                GameScope(game.id, playId = summary.playId)
                else GameScope(game.id, summary.preparationRound,
                    if (action == GameAction.AcceptInvitation || action == GameAction.DeclineInvitation) summary.invitationId else null)
            return GameCommand(id(), scope, action, stream.streamId, stream.generation)
        }
        fun execute(action: GameAction): GameResult {
            val c = command(action)
            games.admit(actor, hello, connection, c); assertTrue(games.executeOne())
            return games.results(actor, game.id, listOf(c.requestId)).statuses.single().result!!
        }
        fun accepted(action: GameAction) = assertEquals("accepted", execute(action).outcome, "$action")
    }

    /** A game [name] of [a] (inviter) and [b], accepted, each with a deck chosen, [a] starting. */
    inner class Match(val a: AccountService.Principal, val b: AccountService.Principal, name: String = "History Spark",
        aDeck: SavedDeck = deck(a, "Alice Red", "R"), bDeck: SavedDeck = deck(b, "Bob Green", "G")) {
        val game: GameSummary
        val alice: Stream; val bob: Stream
        init {
            lobby.add(a, AddContact(id(), b.account.accountId))
            game = GameWire.json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, name)).body)
            alice = Stream(a, game); bob = Stream(b, game)
            bob.accepted(GameAction.AcceptInvitation)
            alice.accepted(GameAction.SelectDeck(aDeck.deck.id, aDeck.deck.revision))
            bob.accepted(GameAction.SelectDeck(bDeck.deck.id, bDeck.deck.revision))
            alice.accepted(GameAction.SetStarter(StarterChoice(a.account.accountId)))
        }
        val id: UUID get() = UUID.fromString(game.id)
        fun summary(): GameSummary = games.head(a, game.id).summary

        /** Both Ready: the match starts, played on simple engine cards (the test seam). */
        fun start() {
            seated { alice.accepted(GameAction.Ready); bob.accepted(GameAction.Ready) }
            assertEquals(GameLifecycle.ACTIVE, summary().lifecycle)
        }

        /** Runs [work] with a match started from it played on simple engine cards (the test seam). */
        fun <T> seated(work: () -> T): T {
            val seats = listOf(a, b).map { PlayerId(it.account.accountId) }.map { p ->
                Seat(p, List(40) { i -> DeckCard(CardId("${p.value}.$i"), Cards.definition(if (i % 2 == 0) "Forest" else "Mountain")) })
            }
            engineSeats = { _ -> seats }
            try { return work() } finally { engineSeats = null }
        }

        /** [by] concedes (CR 104.3a): the match ends at once. */
        fun concede(by: Stream = bob) = by.accepted(games.concession(by.actor, game.id))

        /** [actor]'s device on the match's head. */
        fun device(actor: AccountService.Principal): org.asyncttwiz.protocol.game.GameSession {
            val snapshot = games.head(actor, game.id).engine!!
            return org.asyncttwiz.protocol.game.GameSession(org.asyncttwiz.protocol.game.EngineJson(
                org.asyncttwiz.protocol.game.EngineJson.decodeDefinitions(snapshot.definitions)).decodeModel(snapshot.state),
                snapshot.headId, PlayerId(actor.account.accountId))
        }

        /** Alice, the starter, keeps her opening hand (CR 103.5): a move. Returns its event. */
        fun aliceKeeps(): String {
            val device = device(a)
            val open = device.model.engine.pending!!.decision
            val submission = device.act(id(), arcanum.engine.input.Input.Answer(open.id, arcanum.engine.decision.Answer.Option("Keep")))
            alice.accepted(GameAction.Submit(submission))
            return submission.id
        }

        /** Bob keeps his opening hand (CR 103.5): a move. */
        fun bobKeeps() {
            val device = device(b)
            bob.accepted(GameAction.Submit(device.act(id(), arcanum.engine.input.Input.SetupAnswer(device.me,
                arcanum.engine.decision.Answer.Option("Keep")))))
        }

        /** The game's stored activity time. */
        fun activityAt(): Instant = sql { it.select(org.asyncttwiz.server.generated.tables.Game.GAME.ACTIVITY_AT)
            .from(org.asyncttwiz.server.generated.tables.Game.GAME).where(org.asyncttwiz.server.generated.tables.Game.GAME.ID.eq(id))
            .fetchSingle().value1()!!.toInstant() }
        fun exists(): Boolean = number("SELECT count(*) FROM game WHERE id = ?", id) == 1L
    }

    companion object {
        fun <T> run(test: (HistoryWorld) -> T): T = FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
                test(HistoryWorld(db, runtime, TestClock()))
            }
        }
    }
}
