package org.asyncttwiz.server.decks

import java.util.UUID
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.cubes.CubeService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.asyncttwiz.server.game.GameService
import org.asyncttwiz.server.lobby.LobbyService
import org.asyncttwiz.server.pods.PodService
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Draft decks: autosaved pod submissions, the per-account limit, Save as deck, play, privacy and the V44 backfill. */
class DraftDeckTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()
    private val basics = BasicLand.entries.zip(listOf("W", "U", "B", "R", "G")).map { (land, mana) ->
        val card = CardDefinition(land.cardName, listOf(DefinitionFace(id(), land.cardName,
            typeLine = "Basic Land — ${land.cardName}", rulesText = "({T}: Add {$mana}.)")), provider = "mtgjson")
        SubmittedDefinition(DeckJson.digest(card), card)
    }
    private val lands = basics.associate { submitted -> BasicLand.entries.single { it.cardName == submitted.definition.name } to submitted.digest }

    private inner class Fixture(val db: TestPostgres, val runtime: ServiceRuntime) {
        private val accounts = AccountService(runtime)
        fun account(name: String): AccountService.Principal {
            accounts.register(name, "$name@example.invalid", "fixture password")
            return accounts.authenticate(accounts.login("$name@example.invalid", "fixture password")!!.token)!!
        }
        val a = account("DraftA"); val b = account("DraftB")
        val pods = PodService(runtime); val decks = DeckService(runtime); val games = GameService(runtime)
        val cards = (1..9).map { CardDefinition("Drafted card $it", listOf(DefinitionFace(id(), "Drafted card $it"))) }
            .map { SubmittedDefinition(DeckJson.digest(it), it) }
        val cube: SavedCube
        init {
            assertEquals(200, decks.submit(a, SubmitDefinitions(id(), cards)).status)
            cube = json.decodeFromString(CubeService(runtime).create(a, CreateCube(id(), "Cube", cards.map { SavedCubeEntry(it.digest, 1) })).body)
        }
        /** Two members, three automatic one-card packs each. */
        fun pod(name: String = "Friday pod", sideboardLimit: Int? = null): String {
            val pod = json.decodeFromString<PodDetail>(pods.create(a, CreatePod(id(), name, cube.cube.id)).body).id
            assertEquals(200, pods.invite(a, pod, InviteToPod(id(), b.account.accountId)).status)
            assertEquals(200, pods.invitation(b, pod, PodRequest(id()), true).status)
            assertEquals(200, pods.update(a, pod, UpdatePod(id(), settings = DraftSettings(packsPerPlayer = 3, cardsPerPack = 1, sideboardLimit = sideboardLimit))).status)
            assertEquals(200, pods.start(a, pod, PodRequest(id())).status)
            return pod
        }
        fun detail(who: AccountService.Principal, pod: String) = json.decodeFromString<PodDetail>(pods.read(who, pod).body)
        /** Main: the first card and 8 of each basic; Side: the second card; Maybe: the third (else Main too). */
        fun build(who: AccountService.Principal, pod: String, maybe: Boolean = true): PodDetail {
            val detail = detail(who, pod); val attempt = detail.draftAttemptId!!; val copies = detail.build!!.copies
            assertEquals(200, pods.move(who, pod, MovePodCopy(id(), attempt, copies[1].copyId, PodDeckCategory.SIDE)).status)
            if (maybe) assertEquals(200, pods.move(who, pod, MovePodCopy(id(), attempt, copies[2].copyId, PodDeckCategory.MAYBE)).status)
            assertEquals(200, pods.basics(who, pod, SetPodBasics(id(), attempt, PodDeckBasics(8, 8, 8, 8, 8))).status)
            return detail(who, pod)
        }
        fun submit(who: AccountService.Principal, pod: String, definitions: List<SubmittedDefinition> = basics): Int =
            pods.submit(who, pod, SubmitPodDeck(id(), detail(who, pod).draftAttemptId!!, definitions)).status
        fun drafts(who: AccountService.Principal) = json.decodeFromString<DeckList>(decks.list(who, null, drafts = true).body).decks
        fun ordinary(who: AccountService.Principal) = json.decodeFromString<DeckList>(decks.list(who, null).body).decks
        fun read(who: AccountService.Principal, deck: String) = decks.read(who, deck).let {
            assertEquals(200, it.status, it.body); json.decodeFromString<SavedDeck>(it.body) }
        fun execute(statement: String) = db.connection().use { it.createStatement().execute(statement) }
    }

    private fun fixture(test: (Fixture) -> Unit) = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        ServiceRuntime(db.settings).use { runtime ->
            runtime.start()
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
            test(Fixture(db, runtime))
        }
    }

    // DRF-166, LOB-058: chosen from Main and Side only, private to the member; the draft deck takes it and follows later choices
    @Test fun `the private cover is chosen from Main and Side and the draft deck follows it`() = fixture { f ->
        val pod = f.pod()
        val built = f.build(f.a, pod)
        val attempt = built.draftAttemptId!!
        val definition = built.draft!!.pool.associate { it.id to it.definition }
        val copies = built.build!!.copies.map { definition.getValue(it.copyId) }
        fun cover(digest: String) = f.pods.deckCover(f.a, pod, SetPodDeckCover(id(), attempt, digest))
        assertEquals("invalid_cover", json.decodeFromString<PodError>(cover(copies[2]).body).code) // Maybe
        assertEquals(400, cover("sha256:not-a-card").status)
        assertEquals(409, f.pods.deckCover(f.a, pod, SetPodDeckCover(id(), id(), copies[1])).status)
        assertNull(f.detail(f.a, pod).deckCover)
        assertEquals(200, cover(copies[1]).status)
        assertEquals(copies[1], f.detail(f.a, pod).deckCover)
        assertNull(f.detail(f.b, pod).deckCover)
        assertEquals(200, f.submit(f.a, pod))
        val summary = f.drafts(f.a).single()
        assertEquals(copies[1], summary.cover!!.definition)
        assertEquals(200, cover(copies[0]).status)
        val after = f.read(f.a, summary.id).deck
        assertEquals(copies[0], after.cover!!.definition)
        assertEquals("2", after.revision)
        assertEquals(after.revision, run { assertEquals(200, cover(copies[0]).status); f.read(f.a, summary.id).deck.revision })
    }

    // DRF-166: without a choice a draft deck keeps showing its first card, and a chosen card moved to Maybe doesn't count
    @Test fun `a cover that went to Maybe leaves the draft deck on its first card`() = fixture { f ->
        val pod = f.pod()
        val before = f.detail(f.a, pod)
        val attempt = before.draftAttemptId!!
        val definition = before.draft!!.pool.associate { it.id to it.definition }
        val third = before.build!!.copies[2]
        assertEquals(200, f.pods.deckCover(f.a, pod, SetPodDeckCover(id(), attempt, definition.getValue(third.copyId))).status)
        val built = f.build(f.a, pod) // moves that third card to Maybe
        assertEquals(definition.getValue(third.copyId), built.deckCover)
        assertEquals(200, f.submit(f.a, pod))
        assertEquals(definition.getValue(built.build!!.copies[0].copyId), f.drafts(f.a).single().cover!!.definition)
    }

    // LOB-055: Main, Side (with the Side basics) and the Build's Maybe as its own section
    @Test fun `submitting saves a private read-only draft deck of the whole pool that follows the private name`() = fixture { f ->
        val pod = f.pod()
        val built = f.build(f.a, pod)
        val definition = built.draft!!.pool.associate { it.id to it.definition }
        val copies = built.build!!.copies.map { definition.getValue(it.copyId) }
        assertEquals(400, f.submit(f.a, pod, basics.drop(1)))
        assertEquals(200, f.submit(f.a, pod))
        val summary = f.drafts(f.a).single()
        assertEquals("Friday pod draft", summary.name)
        assertEquals(DraftDeckOrigin(pod, "Friday pod"), summary.draft)
        assertTrue(f.ordinary(f.a).isEmpty())
        assertTrue(f.drafts(f.b).isEmpty())
        val deck = f.read(f.a, summary.id)
        val seventeen = PodDeckBasics(17, 17, 17, 17, 17)
        assertEquals(listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, copies[0], 1)) +
            BasicLand.entries.map { SavedDeckEntry(SavedDeckSection.MAINBOARD, lands.getValue(it), 8) } +
            listOf(SavedDeckEntry(SavedDeckSection.SIDEBOARD, copies[1], 1)) +
            BasicLand.entries.map { SavedDeckEntry(SavedDeckSection.SIDEBOARD, lands.getValue(it), it.count(seventeen)) } +
            listOf(SavedDeckEntry(SavedDeckSection.MAYBEBOARD, copies[2], 1)),
            deck.entries.sortedWith(compareBy({ it.section }, { it.definition in lands.values })))
        assertEquals(41L, summary.mainboard); assertEquals(1L + 85, summary.sideboard); assertEquals(1L, summary.maybeboard)

        // The private pod deck name, also when changed after submitting.
        assertEquals(200, f.pods.deckName(f.a, pod, SetPodDeckName(id(), built.draftAttemptId!!, "Azorius fliers")).status)
        assertEquals("Azorius fliers", f.read(f.a, summary.id).deck.name)
        assertEquals("2", f.read(f.a, summary.id).deck.revision)

        // Read-only and private; a copy is an ordinary, independent, editable deck.
        assertEquals("draft_deck", json.decodeFromString<DeckError>(f.decks.update(f.a, summary.id, UpdateDeck(id(), name = "x")).body).code)
        assertEquals(404, f.decks.read(f.b, summary.id).status)
        assertEquals(404, f.decks.copy(f.b, summary.id, CopyDeck(id())).status)
        assertEquals(404, f.decks.delete(f.b, summary.id, DeleteDeck(id())).status)
        val input = CopyDeck(id())
        val result = f.decks.copy(f.a, summary.id, input)
        assertEquals(201, result.status, result.body)
        assertEquals(result.body, f.decks.copy(f.a, summary.id, input).body)
        val copy = json.decodeFromString<SavedDeck>(result.body)
        assertNull(copy.deck.draft); assertEquals("Azorius fliers", copy.deck.name); assertEquals(deck.entries, copy.entries)
        assertEquals(listOf(copy.deck.id), f.ordinary(f.a).map { it.id })
        assertEquals(1, f.drafts(f.a).size)
        assertEquals(200, f.decks.update(f.a, copy.deck.id, UpdateDeck(id(), name = "Kept")).status)
        assertEquals(200, f.decks.delete(f.a, summary.id, DeleteDeck(id())).status)
        assertTrue(f.drafts(f.a).isEmpty())
        assertEquals(deck.entries, f.read(f.a, copy.deck.id).entries)

        // Without basic definitions (an app without the card catalog) the draft deck has no basics.
        f.build(f.b, pod)
        assertEquals(200, f.submit(f.b, pod, emptyList()))
        val other = f.read(f.b, f.drafts(f.b).single().id)
        assertEquals(1L, other.deck.mainboard); assertEquals(1L, other.deck.sideboard); assertEquals(1L, other.deck.maybeboard)
    }

    @Test fun `a resubmission replaces the same draft deck`() = fixture { f ->
        val pod = f.pod(sideboardLimit = 1)
        f.build(f.a, pod)
        assertEquals(200, f.submit(f.a, pod))
        val first = f.drafts(f.a).single()
        assertEquals(41L, first.mainboard); assertEquals(1L, first.sideboard); assertEquals(1L, first.maybeboard)
        // Reopen the build as an edit-after-submit would, then move the Side card to Main.
        f.execute("UPDATE pod_member SET submitted_deck = NULL WHERE pod_id = '$pod' AND account_id = '${f.a.account.accountId}'")
        val detail = f.detail(f.a, pod)
        val side = detail.build!!.copies.single { it.category == PodDeckCategory.SIDE }
        assertEquals(200, f.pods.move(f.a, pod, MovePodCopy(id(), detail.draftAttemptId!!, side.copyId, PodDeckCategory.MAIN)).status)
        assertEquals(200, f.submit(f.a, pod))
        val second = f.drafts(f.a).single()
        assertEquals(first.id, second.id); assertEquals(first.createdAt, second.createdAt); assertEquals("2", second.revision)
        assertEquals(42L, second.mainboard); assertEquals(0L, second.sideboard); assertEquals(1L, second.maybeboard)
    }

    // LOB-055
    @Test fun `the twenty-first draft deck drops the oldest without disturbing a game that selected it`() = fixture { f ->
        val first = f.pod()
        f.build(f.a, first); assertEquals(200, f.submit(f.a, first))
        val oldest = f.drafts(f.a).single()
        // Playable directly: select the draft deck in an ordinary game still in setup.
        val lobby = LobbyService(f.runtime)
        lobby.add(f.a, AddContact(id(), f.b.account.accountId))
        val game = GameWire.json.decodeFromString<GameSummary>(lobby.create(f.a, CreateInvitation(id(), f.b.account.accountId, "Draft deck game")).body)
        val hello = GameHello(id(), id()); val connection = UUID.randomUUID()
        val stream = f.games.open(f.a, hello, connection, game.id, id())
        fun execute(action: GameAction): GameResult {
            val command = GameCommand(id(), GameScope(game.id, game.preparationRound), action, stream.streamId, stream.generation)
            assertTrue(f.games.admit(f.a, hello, connection, command) is GameReceipt); assertTrue(f.games.executeOne())
            return f.games.results(f.a, game.id, listOf(command.requestId)).statuses.single().result!!
        }
        assertEquals("accepted", execute(GameAction.SelectDeck(oldest.id, oldest.revision)).outcome)

        val entries = f.read(f.a, oldest.id).entries
        repeat(DraftDecks.LIMIT - 1) { n -> f.runtime.transaction { connection ->
            DraftDeckStore.save(DSL.using(connection, SQLDialect.POSTGRES), UUID.fromString(f.a.account.accountId),
                UUID.randomUUID(), "Old pod $n", "Old draft $n", entries)
        } }
        assertEquals(DraftDecks.LIMIT, f.drafts(f.a).size)
        assertEquals(oldest.id, f.drafts(f.a).last().id)
        val latest = f.pod("Newest pod")
        f.build(f.a, latest); assertEquals(200, f.submit(f.a, latest))
        val kept = f.drafts(f.a)
        assertEquals(DraftDecks.LIMIT, kept.size)
        assertEquals("Newest pod draft", kept.first().name)
        assertTrue(kept.none { it.id == oldest.id })
        assertEquals(404, f.decks.read(f.a, oldest.id).status)
        // Re-submitting an existing membership never evicts.
        f.execute("UPDATE pod_member SET submitted_deck = NULL WHERE pod_id = '$latest' AND account_id = '${f.a.account.accountId}'")
        assertEquals(200, f.submit(f.a, latest))
        assertEquals(kept.map { it.id }, f.drafts(f.a).map { it.id })

        // The game kept its own copy of the evicted deck and can still get ready with it.
        val prepared = f.games.head(f.a, game.id).preparation!!.players.single { it.accountId == f.a.account.accountId }.deck!!
        assertEquals(oldest.id, prepared.sourceId); assertEquals(41L, prepared.count(SavedDeckSection.MAINBOARD))
        assertEquals("accepted", execute(GameAction.Ready).outcome)
    }

    @Test fun `the V44 backfill rebuilds draft decks from submitted pod decks`() = fixture { f ->
        val pod = f.pod()
        // V44 predates Maybe in draft decks (it folded the then Trash into Side): builds without one
        f.build(f.a, pod, maybe = false); f.build(f.b, pod, maybe = false)
        assertEquals(200, f.submit(f.a, pod)); assertEquals(200, f.submit(f.b, pod))
        val name = f.detail(f.a, pod).draftAttemptId!!
        assertEquals(200, f.pods.deckName(f.b, pod, SetPodDeckName(id(), name, "B's pile")).status)
        // A pod game records the basic printings the backfill reuses.
        assertEquals(201, f.pods.challenge(f.a, pod, ChallengeFromPod(id(), f.b.account.accountId, "Pod game", basics)).status)
        fun snapshot() = listOf(f.a, f.b).map { who -> f.drafts(who).map { summary ->
            val deck = f.read(who, summary.id)
            Triple(summary.name, summary.draft, deck.entries)
        } }
        val autosaved = snapshot()
        assertTrue(autosaved.all { it.size == 1 })
        f.execute("DELETE FROM saved_deck WHERE draft_pod_id IS NOT NULL")
        val migration = javaClass.getResource("/db/migration/V44__draft_decks.sql")!!.readText()
        val backfill = migration.substring(migration.indexOf("-- Backfill"))
        f.db.connection().use { connection ->
            connection.autoCommit = false
            connection.createStatement().execute(backfill)
            connection.commit()
        }
        assertEquals(autosaved, snapshot())
        assertEquals("B's pile", autosaved[1].single().first)
    }

    // DRF-092, LOB-141: V70 turns a stored Trash into Maybe with nothing lost: the build keeps every copy, a submitted deck
    // (which left Trash out) takes its Maybe back, and stored request responses say Maybe too
    @Test fun `V70 renames a stored Trash to Maybe and gives submitted decks their Maybe`() = fixture { f ->
        val pod = f.pod()
        f.build(f.a, pod)
        assertEquals(200, f.submit(f.a, pod))
        val before = f.detail(f.a, pod)
        assertEquals(before.build, before.deck)
        val member = "pod_id = '$pod' AND account_id = '${f.a.account.accountId}'"
        // As stored before V70
        f.execute("UPDATE pod_member SET build = replace(build::text, '\"MAYBE\"', '\"TRASH\"')::jsonb, " +
            "submitted_deck = jsonb_set(submitted_deck, '{copies}', (SELECT jsonb_agg(c ORDER BY n) " +
            "FROM jsonb_array_elements(submitted_deck -> 'copies') WITH ORDINALITY AS t(c, n) WHERE c ->> 'category' IS DISTINCT FROM 'MAYBE')) " +
            "WHERE $member")
        f.execute("UPDATE account_request SET response = replace(response, '\"category\":\"MAYBE\"', '\"category\":\"TRASH\"')")
        fun count(where: String) = f.db.connection().use { c ->
            c.createStatement().executeQuery("SELECT count(*) FROM $where").use { it.next(); it.getInt(1) } }
        assertEquals(1, count("pod_member WHERE $member AND build::text LIKE '%TRASH%' AND submitted_deck::text NOT LIKE '%MAYBE%' " +
            "AND submitted_deck::text NOT LIKE '%TRASH%'"))
        assertTrue(count("account_request WHERE response LIKE '%\"category\":\"TRASH\"%'") > 0)

        val migration = javaClass.getResource("/db/migration/V70__build_maybe.sql")!!.readText()
        f.db.connection().use { connection ->
            connection.autoCommit = false
            connection.createStatement().execute(migration)
            connection.commit()
        }
        val after = f.detail(f.a, pod)
        assertEquals(before.build, after.build)
        assertEquals(before.deck, after.deck)
        assertEquals(0, count("account_request WHERE response LIKE '%TRASH%'"))
        assertEquals(0, count("pod_member WHERE build::text LIKE '%TRASH%'"))
    }
}
