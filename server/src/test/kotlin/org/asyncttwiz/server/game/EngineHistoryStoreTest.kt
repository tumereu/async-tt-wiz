package org.asyncttwiz.server.game

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.RandomKind
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.state.CardId
import arcanum.engine.state.PlayerId
import java.util.UUID
import kotlin.random.Random
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameHistory
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * I-D54 phase 2: a play's engine history in PostgreSQL (V56): definitions once per play, events, checkpoints every
 * [GameHistory.CHECKPOINT_EVERY] events of depth; a cold read of any event's state is one checkpoint plus the events
 * below it, read in one query, and equals the state reached directly.
 */
class EngineHistoryStoreTest {
    private val a = UUID.fromString("00000000-0000-4000-8000-00000000000a")
    private val b = UUID.fromString("00000000-0000-4000-8000-00000000000b")

    /** The rows a play needs (accounts, game, invitation, start request) and the play itself. */
    private fun seedPlay(sql: DSLContext): UUID {
        val game = UUID.randomUUID(); val play = UUID.randomUUID(); val round = UUID.randomUUID()
        val invitation = UUID.randomUUID(); val start = UUID.randomUUID()
        for ((account, name) in listOf(a to "EngineA", b to "EngineB")) sql.execute("""INSERT INTO account
            (id,username,username_key,email_delivery,email_key,password_phc) VALUES (?,?,?,?,?,?)""",
            account, name, name.lowercase(), "$name@example.invalid", "${name.lowercase()}@example.invalid", "fixture")
        sql.execute("""INSERT INTO game(id,game_name,inviter_id,recipient_id,preparation_round,current_invitation,revision,next_ingress)
            VALUES (?,'Engine game',?,?,?,?,1,2)""", game, a, b, round, invitation)
        sql.execute("INSERT INTO invitation(id,game_id,state) VALUES (?,?,'ACCEPTED')", invitation, game)
        sql.execute("INSERT INTO request_identity(id,account_id,kind,scope,intent_digest) VALUES (?,?,'gameCommand',?,?)",
            start, a, game.toString(), "0".repeat(64))
        sql.execute("""INSERT INTO game_inbox(request_id,game_id,account_id,ingress_ordinal,stream_id,generation,command,result)
            VALUES (?,?,?,1,?,1,'{}','{}')""", start, game, a, UUID.randomUUID())
        sql.execute("""INSERT INTO game_play(id,game_id,preparation_round,start_request_id,root_id,payload,initial_payload)
            VALUES (?,?,?,?,?,'{}','{}')""", play, game, round, start, UUID.randomUUID())
        return play
    }

    /** One acting point: the first listed input of anyone (a seeded pick among them), then the random results. */
    private fun next(model: GameModel, parent: String, rnd: Random): GameEvent {
        val builder = EventBuilder(model, UUID.randomUUID().toString(), parent, a.toString())
        val s = model.engine
        val input = if (s.turn.number == 0 && s.pending == null) Input.StartGame()
            else s.players.flatMap { Engine.options(s, it.id) }.filter { it !is Input.Concede }.let { it[rnd.nextInt(it.size)] }
        builder.perform(input)
        while (true) {
            val d = model.engine.pending?.decision as? Decision.Random ?: break
            builder.perform(Input.Answer(d.id, when (val k = d.what) {
                RandomKind.CoinFlip -> Answer.CoinFlip(rnd.nextBoolean())
                is RandomKind.Die -> Answer.DieRolls(List(k.count) { rnd.nextInt(1, k.sides + 1) })
                is RandomKind.Objects -> Answer.RandomObjects(k.among.shuffled(rnd).take(k.count))
            }))
        }
        return builder.build()
    }

    @Test
    fun aPlaysEngineHistoryIsStoredAndReadBackFromItsNearestCheckpoint() = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        db.connection().use { connection ->
            connection.autoCommit = false
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val play = seedPlay(sql)
            val alice = PlayerId(a.toString()); val bob = PlayerId(b.toString())
            val names = List(16) { if (it % 2 == 0) "Forest" else "Mountain" } + List(24) { listOf("Elvish Seer", "Storm Spark", "Thicketwood Elves")[it % 3] }
            val initial = Engine.newGame(listOf(alice, bob).map { p -> Seat(p, names.mapIndexed { i, n -> DeckCard(CardId("${p.value}.$i"), Cards.definition(n)) }) })
            val root = UUID.randomUUID()
            val store = EngineHistoryStore.create(sql, play, root, GameDefinitions.of(initial), GameModel(initial.deepCopy()))
            val history = GameHistory(store, store.codec)
            // A main line of 150 events and a branch of 40 from its 70th
            val rnd = Random(5)
            val direct = mutableMapOf<String, GameModel>()
            fun grow(from: String, model: GameModel, count: Int): List<String> {
                var parent = from
                return List(count) {
                    val event = next(model, parent, rnd)
                    store.append(history, event, a, model)
                    direct[event.id] = model.copy()
                    parent = event.id
                    event.id
                }
            }
            val main = grow(root.toString(), GameModel(initial.deepCopy()), 150)
            val branch = grow(main[69], direct.getValue(main[69]).copy(), 40)
            connection.commit()

            assertEquals(3, sql.fetchCount(DSL.table("engine_checkpoint")), "the root's and depths 64 and 128")
            assertEquals(190, sql.fetchCount(DSL.table("engine_event")))
            assertNull(EngineHistoryStore.open(sql, UUID.randomUUID()))
            // Cold: a new store reads each state from its nearest checkpoint, one query for the events in between
            for (id in listOf(main[0], main[62], main[63], main[64], main[149], branch[0], branch[39])) {
                val cold = EngineHistoryStore.open(sql, play)!!
                val line = cold.line(id, GameHistory.CHECKPOINT_EVERY)
                assertTrue(line.size < GameHistory.CHECKPOINT_EVERY, "$id: ${line.size} events below its checkpoint")
                val state = GameHistory(cold, cold.codec).stateAt(id)
                assertTrue(state.sameAs(direct.getValue(id)), "the state at $id")
            }
            assertEquals(listOf(main[0]), EngineHistoryStore.open(sql, play)!!.line(main[0], 64).map { it.event.id })
            assertEquals(71, history.depthOf(branch[0]))
        }
    }

    // HIS-111 (owner 2026-11-07: the timeline and the Undo dialog reach the game's first event): pages of the
    // main line from the head back to the game's first event, each read from a cold store with at most one checkpoint
    // interval of events decoded however long the game, the checkpoint's state stored with it; a discarded line's event
    // has no page (shown to nobody)
    @Test
    fun pagesOfTheMainLineReachTheFirstEventWithBoundedReads() = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        db.connection().use { connection ->
            connection.autoCommit = false
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val play = seedPlay(sql)
            val alice = PlayerId(a.toString()); val bob = PlayerId(b.toString())
            val names = List(16) { if (it % 2 == 0) "Forest" else "Mountain" } + List(24) { listOf("Elvish Seer", "Storm Spark", "Thicketwood Elves")[it % 3] }
            val initial = Engine.newGame(listOf(alice, bob).map { p -> Seat(p, names.mapIndexed { i, n -> DeckCard(CardId("${p.value}.$i"), Cards.definition(n)) }) })
            val root = UUID.randomUUID()
            val store = EngineHistoryStore.create(sql, play, root, GameDefinitions.of(initial), GameModel(initial.deepCopy()))
            val history = GameHistory(store, store.codec)
            val rnd = Random(9)
            val direct = mutableMapOf<String, GameModel>()
            fun grow(from: String, model: GameModel, count: Int): List<String> {
                var parent = from
                return List(count) {
                    val event = next(model, parent, rnd)
                    store.append(history, event, a, model)
                    direct[event.id] = model.copy()
                    parent = event.id
                    event.id
                }
            }
            // A main line of 300 events, and a discarded line of 20 from its 100th
            val first = grow(root.toString(), GameModel(initial.deepCopy()), 100)
            val gone = grow(first.last(), direct.getValue(first.last()).copy(), 20)
            val main = first + grow(first.last(), direct.getValue(first.last()).copy(), 200)
            val head = main.last()
            connection.commit()

            var through: String? = head
            val seen = ArrayDeque<String>()
            var pages = 0
            while (through != null) {
                val cold = EngineHistoryStore.open(sql, play)!!
                val page = GameHistory(cold, cold.codec).page(head, through!!)!!
                assertTrue(cold.decoded <= GameHistory.CHECKPOINT_EVERY + 1, "a page decodes at most a checkpoint interval: ${cold.decoded}")
                assertTrue(page.events.size in 1..GameHistory.CHECKPOINT_EVERY)
                val events = page.events.map(cold.codec::decodeEvent)
                assertEquals(through, events.last().id)
                assertEquals(page.baseId, events.first().parentId)
                // The state at each of its events: the checkpoint and the events before it, the same as reached directly
                val model = cold.codec.decodeModel(page.base)
                for (e in events) { model.apply(e); assertTrue(model.sameAs(direct.getValue(e.id)), "the state at ${e.id}") }
                events.asReversed().forEach { seen.addFirst(it.id) }
                pages++
                through = if (page.fromStart) null else page.baseId
                if (page.fromStart) assertEquals(root.toString(), page.baseId)
            }
            assertEquals(main, seen.toList(), "the whole main line, oldest first")
            assertEquals(5, pages, "300 events at 64 a checkpoint: 44, then 64 four times")
            val cold = EngineHistoryStore.open(sql, play)!!
            assertNull(GameHistory(cold, cold.codec).page(head, gone[5]), "a discarded line's event")
            assertNull(GameHistory(cold, cold.codec).page(head, root.toString()))
            assertNull(GameHistory(cold, cold.codec).page(head, UUID.randomUUID().toString()))
            assertEquals(main[199], cold.idAt(head, 200))
            assertEquals(root.toString(), cold.idAt(head, 0))
            assertNull(cold.idAt(main[10], 50))
        }
    }

    /** The wipe of the switch to engine events (V59, owner accepted), run again on the latest schema: every started game
     * goes with every row of any table that names it or its plays, both histories included; a game that never started
     * stays. */
    @Test
    fun theWipeForTheSwitchRemovesStartedGamesWithEverythingThatNamesThem() = wipeRemovesStartedGames("V59__engine_state_wipe_plays.sql")

    /** The wipe of the stable decision ids (V63, owner 2026-11-05), the same way. */
    @Test
    fun theWipeForStableDecisionIdsRemovesStartedGamesWithEverythingThatNamesThem() =
        wipeRemovesStartedGames("V63__engine_decision_ids_wipe_plays.sql")

    /** The wipe of the engine generalisation (V74, owner 2026-11-08), the same way; it also leaves each game's tombstone. */
    @Test
    fun theWipeForTheGeneralFormsRemovesStartedGamesWithEverythingThatNamesThem() =
        wipeRemovesStartedGames("V74__engine_general_forms_wipe_plays.sql", tombstones = 1)

    private fun wipeRemovesStartedGames(migration: String, tombstones: Int = 0) = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        db.connection().use { connection ->
            connection.autoCommit = false
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val play = seedPlay(sql)
            val game = sql.fetchValue("SELECT game_id FROM game_play WHERE id = ?", play) as UUID
            val initial = Engine.newGame(listOf(Seat(PlayerId(a.toString()), listOf(DeckCard(CardId("c1"), Cards.definition("Forest"))))))
            val store = EngineHistoryStore.create(sql, play, UUID.randomUUID(), GameDefinitions.of(initial), GameModel(initial.deepCopy()))
            store.append(GameHistory(store, store.codec), next(GameModel(initial.deepCopy()), store.rootId, Random(1)), a, GameModel(initial.deepCopy()))
            val waiting = UUID.randomUUID(); val invitation = UUID.randomUUID()
            sql.execute("""INSERT INTO game(id,game_name,inviter_id,recipient_id,preparation_round,current_invitation,revision,next_ingress)
                VALUES (?,'Not started',?,?,?,?,1,1)""", waiting, a, b, UUID.randomUUID(), invitation)
            sql.execute("INSERT INTO invitation(id,game_id,state) VALUES (?,?,'PENDING')", invitation, waiting)
            // The started game's finished match: its record is never deleted, also not with its game (V66)
            sql.execute("""INSERT INTO match_record(id,play_id,game_id,inviter_id,recipient_id,started_at,ended_at,finished,record)
                VALUES (?,?,?,?,?,now(),now(),true,'{}')""", UUID.randomUUID(), play, game, a, b)
            connection.commit()

            // V62 dropped the old model's tables V59 also emptied: its statements for them have nothing left to do
            val dropped = Regex("\\b(game_play_mutation|gameplay_endpoint_edge|gameplay_endpoint_record|gameplay_tree_control|gameplay_mutation|gameplay_group)\\b")
            val wipe = javaClass.getResource("/db/migration/$migration")!!.readText()
                .lines().filterNot { dropped.containsMatchIn(it) }.joinToString("\n")
            connection.createStatement().use { it.execute(wipe) }
            connection.commit()

            val columns = sql.fetch("""SELECT table_name, column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND column_name IN ('game_id', 'play_id')""")
            assertTrue(columns.size > 10)
            for (row in columns) {
                val table = row.get(0, String::class.java); val column = row.get(1, String::class.java)
                val named = if (column == "game_id") game else play
                val left = when (table) { "match_record" -> 1; "game_removed" -> tombstones; else -> 0 }
                assertEquals(left, sql.fetchCount(DSL.table(DSL.name(table)), DSL.field(DSL.name(column)).eq(named)), "$table.$column")
            }
            assertEquals(0, sql.fetchCount(DSL.table("game"), DSL.field("id").eq(game)))
            assertEquals(1, sql.fetchCount(DSL.table("game"), DSL.field("id").eq(waiting)))
            assertEquals(0, sql.fetchCount(DSL.table("engine_play")))
        }
    }
}
