package org.asyncttwiz.server.pods

import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.TestPrintings
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.cubes.CubeService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.game.GameService
import org.asyncttwiz.server.game.concession
import org.asyncttwiz.server.lobby.LobbyService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PodChallengeTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()
    /** LOB-052: the basic lands the server supplies, from its catalog (the test cards' M21). */
    private val basics get() = TestPrintings.catalog.basics!!.mapValues { it.value.digest }
    private inner class Fixture(val runtime: ServiceRuntime, uniqueCards: Boolean) {
        private val accounts = AccountService(runtime)
        fun account(name: String): AccountService.Principal {
            accounts.register(name, "$name@example.invalid", "fixture password")
            return accounts.authenticate(accounts.login("$name@example.invalid", "fixture password")!!.token)!!
        }
        val a = account("ChallengeA"); val b = account("ChallengeB"); val c = account("ChallengeC")
        /** DRF-181: the games each committed change outside a game request told the game tables of ([PodGamesListener]). */
        val told = mutableListOf<List<String>>()
        val pods = PodService(runtime) { told += it }; val games = GameService(runtime); val lobby = LobbyService(runtime)
        val decks = DeckService(runtime)
        val cube: SavedCube
        init {
            // Test cards with nothing to do; unique ones whose names appear in no other card's data
            val cards = if (uniqueCards) listOf("Ash Pup", "Ember Cub", "Cinder Hound", "Lone Goat", "Drowsy Pup", "Warded Pup",
                "Ember Sprite", "Cinder Brute", "Ember Dasher") else listOf("Ash Pup")
            cube = json.decodeFromString(CubeService(runtime).create(a, CreateCube(id(), "Cube",
                cards.map { TestPrintings.entry(it, if (uniqueCards) 1 else 90) })).body)
        }
        fun pod(three: Boolean = false, sideboardLimit: Int? = 1, gameSettings: AssumeReveals = AssumeReveals()): String {
            val pod = json.decodeFromString<PodDetail>(pods.create(a, CreatePod(id(), "Surviving pod name", cube.cube.id)).body).id
            for (member in if (three) listOf(b, c) else listOf(b)) {
                assertEquals(200, pods.invite(a, pod, InviteToPod(id(), member.account.accountId)).status)
                assertEquals(200, pods.invitation(member, pod, PodRequest(id()), true).status)
            }
            // Each member receives three automatic one-card packs.
            assertEquals(200, pods.update(a, pod, UpdatePod(id(), settings = DraftSettings(packsPerPlayer = 3, cardsPerPack = 1, sideboardLimit = sideboardLimit,
                gameSettings = gameSettings))).status)
            assertEquals(200, pods.start(a, pod, PodRequest(id())).status)
            return pod
        }
        fun detail(who: AccountService.Principal, pod: String) = json.decodeFromString<PodDetail>(pods.read(who, pod).body)
        fun submit(who: AccountService.Principal, pod: String, counts: PodDeckBasics = PodDeckBasics(8, 8, 8, 8, 8)) {
            val detail = detail(who, pod); val attempt = detail.draftAttemptId!!
            val copies = detail.build!!.copies
            assertEquals(200, pods.move(who, pod, MovePodCopy(id(), attempt, copies[1].copyId, PodDeckCategory.SIDE)).status)
            assertEquals(200, pods.move(who, pod, MovePodCopy(id(), attempt, copies[2].copyId, PodDeckCategory.MAYBE)).status)
            assertEquals(200, pods.basics(who, pod, SetPodBasics(id(), attempt, counts)).status)
            assertEquals(200, pods.submit(who, pod, SubmitPodDeck(id(), attempt)).status)
        }
        fun challenge(pod: String, who: AccountService.Principal = a, opponent: AccountService.Principal = b): GameSummary {
            val result = pods.challenge(who, pod, ChallengeFromPod(id(), opponent.account.accountId, "Parallel game"))
            assertEquals(201, result.status, result.body)
            return json.decodeFromString(result.body)
        }
        fun preparation(game: GameSummary) = games.head(a, game.id).preparation!!
        fun stream(who: AccountService.Principal, game: GameSummary) = Stream(games, who, game)
    }
    private class Stream(val games: GameService, val who: AccountService.Principal, val game: GameSummary) {
        private val hello = GameHello(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        private val connection = UUID.randomUUID()
        private val stream = games.open(who, hello, connection, game.id, UUID.randomUUID().toString())
        fun execute(action: GameAction): GameResult {
            val summary = games.head(who, game.id).summary
            val scope = if (action is GameAction.Submit) GameScope(game.id, playId = summary.playId)
                else if (action is GameAction.SetAssumeReveals) GameScope(game.id)
                else GameScope(game.id, summary.preparationRound,
                    if (action in listOf(GameAction.AcceptInvitation, GameAction.DeclineInvitation, GameAction.Reinvite)) summary.invitationId else null)
            val command = GameCommand(UUID.randomUUID().toString(), scope, action, stream.streamId, stream.generation)
            assertTrue(games.admit(who, hello, connection, command) is GameReceipt)
            assertTrue(games.executeOne())
            return games.results(who, game.id, listOf(command.requestId)).statuses.single().result!!
        }
    }
    // TRN-027, TRN-058, LOB-022, LOB-024 (I-D57, async-play §28): while both openings are open the game waits for both
    // players, so each viewer's head names them as the move; the heads are otherwise the same
    private fun assertSameStart(first: GameHead, firstViewer: String, second: GameHead, secondViewer: String) {
        assertEquals(firstViewer, first.summary.attentionHolder); assertEquals(secondViewer, second.summary.attentionHolder)
        assertEquals(first, second.copy(summary = second.summary.copy(attentionHolder = firstViewer)))
    }

    private fun fixture(uniqueCards: Boolean = false, test: (Fixture) -> Unit) = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        ServiceRuntime(db.settings).use { runtime ->
            runtime.start()
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
            test(Fixture(runtime, uniqueCards))
        }
    }

    // LOB-135, HIS-042 (owner 2026-11-26, playtest F): the pod's Game settings, set by its creator in setup, are what every game
    // started from the pod begins with, whoever challenges; each game's lobby can still change them as usual (its creator, the
    // challenger), the pod's staying as they are
    @Test fun `games started from a pod begin with its game settings`() = fixture { f ->
        val settings = AssumeReveals(library = false, hands = true, undoPastPasses = true, reshuffleOnUndo = false, reshuffleOnBranch = false)
        val pod = f.pod(gameSettings = settings)
        assertEquals(settings, f.detail(f.b, pod).settings.gameSettings, "members see them")
        f.submit(f.a, pod); f.submit(f.b, pod)
        val game = f.challenge(pod)
        assertEquals(settings, game.assumeReveals)
        val other = f.challenge(pod, who = f.b, opponent = f.a)
        assertEquals(settings, f.games.head(f.a, other.id).summary.assumeReveals)
        assertEquals("accepted", f.stream(f.a, game).execute(GameAction.SetAssumeReveals(AssumeReveals())).outcome)
        assertEquals(AssumeReveals(), f.games.head(f.b, game.id).summary.assumeReveals)
        assertEquals(settings, f.detail(f.a, pod).settings.gameSettings)
        assertEquals(settings, f.challenge(pod).assumeReveals)
    }

    // The wipe of the engine generalisation (V74): no pod row names a game or a play, so a pod whose games went still opens
    // for its members, keeps its finished match's record and its challenge not yet started, and can challenge again
    @Test fun `a pod whose started games V74 wiped still opens`() = fixture { f ->
        val pod = f.pod()
        f.submit(f.a, pod); f.submit(f.b, pod)
        fun started(game: GameSummary): Stream {
            val recipient = f.stream(f.b, game)
            recipient.execute(GameAction.AcceptInvitation)
            f.stream(f.a, game).execute(GameAction.Ready); recipient.execute(GameAction.Ready)
            assertEquals(GameLifecycle.ACTIVE, f.games.head(f.a, game.id).summary.lifecycle)
            return recipient
        }
        val finished = f.challenge(pod)
        assertEquals("accepted", started(finished).execute(f.games.concession(f.b, finished.id)).outcome)
        val inPlay = f.challenge(pod); started(inPlay)
        val queued = f.challenge(pod)
        val before = f.detail(f.a, pod)
        f.runtime.transaction { connection -> connection.createStatement().use { it.execute(checkNotNull(
            javaClass.getResourceAsStream("/db/migration/V74__engine_general_forms_wipe_plays.sql")).reader().readText()) } }

        for (member in listOf(f.a, f.b)) {
            val after = f.detail(member, pod)
            assertEquals(before.phase, after.phase)
            assertEquals(before.draftAttemptId, after.draftAttemptId)
            assertEquals(pod, json.decodeFromString<PodList>(f.pods.list(member).body).pods.single().id)
        }
        assertEquals(404, f.lobby.head(f.a, finished.id).status)
        assertEquals(404, f.lobby.head(f.a, inPlay.id).status)
        assertEquals(200, f.lobby.head(f.a, queued.id).status)
        val matches = f.runtime.transaction { connection -> org.asyncttwiz.server.history.MatchRecords.forPod(
            org.jooq.impl.DSL.using(connection, org.jooq.SQLDialect.POSTGRES), UUID.fromString(f.a.account.accountId), UUID.fromString(pod))!! }
        assertEquals(1, matches.records.size, "the finished match's record stays")
        assertTrue(matches.playing.isEmpty())
        assertEquals(1L, f.runtime.transaction { connection -> org.jooq.impl.DSL.using(connection, org.jooq.SQLDialect.POSTGRES)
            .fetchCount(org.jooq.impl.DSL.table("game"), org.jooq.impl.DSL.field("pod_id").eq(UUID.fromString(pod))).toLong() })
        started(f.challenge(pod))
    }

    // DRF-104
    @Test fun `side basics start at seventeen when unlimited and become sideboard lands in challenge decks`() = fixture { f ->
        assertEquals(PodDeckBuild().sideBasics, f.detail(f.a, f.pod()).build!!.sideBasics)
        val pod = f.pod(sideboardLimit = null)
        val seventeen = PodDeckBasics(17, 17, 17, 17, 17)
        assertEquals(seventeen, f.detail(f.a, pod).build!!.sideBasics)
        val attempt = f.detail(f.a, pod).draftAttemptId!!
        assertEquals(400, f.pods.basics(f.a, pod, SetPodBasics(id(), attempt, PodDeckBasics(island = -1), side = true)).status)
        val side = json.decodeFromString<PodDetail>(f.pods.basics(f.a, pod, SetPodBasics(id(), attempt, PodDeckBasics(island = 2), side = true)).body)
        assertEquals(PodDeckBasics(island = 2), side.build!!.sideBasics); assertEquals(PodDeckBasics(), side.build!!.basics)
        f.submit(f.a, pod); f.submit(f.b, pod)
        val game = f.challenge(pod)
        assertEquals("accepted", f.stream(f.b, game).execute(GameAction.AcceptInvitation).outcome)
        val decks = f.preparation(game).players.associate { it.accountId to it.deck!! }
        for ((player, expected) in listOf(f.a to PodDeckBasics(island = 2), f.b to seventeen)) {
            val deck = decks.getValue(player.account.accountId)
            assertEquals(PodDeckBasics(8, 8, 8, 8, 8), PodGamePreparation.basics(deck))
            assertEquals(expected, PodGamePreparation.basics(deck, SavedDeckSection.SIDEBOARD))
            assertEquals(1 + expected.total, deck.count(SavedDeckSection.SIDEBOARD))
        }
    }

    // DRF-123, DRF-140
    @Test fun `challenge authorization submitted decks name basics validation idempotency and parallel games`() = fixture { f ->
        val pod = f.pod()
        fun input(opponent: String = f.b.account.accountId) = ChallengeFromPod(id(), opponent, "Game")
        assertEquals(404, f.pods.challenge(f.c, pod, input()).status)
        assertEquals(404, f.pods.challenge(f.a, pod, input(f.c.account.accountId)).status)
        assertEquals(400, f.pods.challenge(f.a, pod, input(f.a.account.accountId)).status)
        assertEquals(409, f.pods.challenge(f.a, pod, input()).status)
        f.submit(f.a, pod)
        // The challenger must have submitted; the opponent may still be building.
        assertEquals(409, f.pods.challenge(f.b, pod, input(f.a.account.accountId)).status)
        f.submit(f.b, pod)
        assertEquals(400, f.pods.challenge(f.a, pod, input().copy(gameName = " ")).status)
        val request = input(); val first = f.pods.challenge(f.a, pod, request)
        assertEquals(201, first.status, first.body); assertEquals(first, f.pods.challenge(f.a, pod, request))
        assertEquals(409, f.pods.challenge(f.a, pod, request.copy(gameName = "Changed")).status)
        val game = json.decodeFromString<GameSummary>(first.body)
        assertNotEquals(game.id, f.challenge(pod).id)
        assertNotEquals(game.id, f.challenge(pod, f.b, f.a).id)
        assertEquals(pod, game.podId); assertEquals("Surviving pod name", game.podName)
        assertEquals(2, json.decodeFromString<GameList>(f.lobby.list(f.a, null, false).body).games.size)
        assertEquals(1, json.decodeFromString<GameList>(f.lobby.list(f.a, null, true).body).games.size)
        val preparation = f.preparation(game)
        assertEquals(PodGameRules(1), preparation.podRules)
        assertNull(preparation.players.single { it.accountId == f.b.account.accountId }.deck)
        assertEquals("accepted", f.stream(f.b, game).execute(GameAction.AcceptInvitation).outcome)
        val accepted = f.preparation(game)
        for (player in accepted.players) {
            val deck = player.deck!!
            assertEquals(41L, deck.count(SavedDeckSection.MAINBOARD)); assertEquals(1L, deck.count(SavedDeckSection.SIDEBOARD))
            // LOB-141: Maybe comes along (outside the game with Side)
            assertEquals(1L, deck.count(SavedDeckSection.MAYBEBOARD))
            assertEquals(3, deck.entries.count { it.suppliedBasic == null })
            assertEquals(PodDeckBasics(8, 8, 8, 8, 8), PodGamePreparation.basics(deck))
            // LOB-052: the server's basics, no device's
            assertEquals(basics, deck.basicDefinitions)
            assertTrue(deck.entries.all { entry -> accepted.definitions.any { it.digest == entry.definition } })
        }
        assertNotEquals(accepted.players[0].deck!!.id, accepted.players[1].deck!!.id)
        val stream = f.stream(f.a, game)
        assertEquals("podDeckFixed", stream.execute(GameAction.SelectDeck(id(), "1")).reason)
    }

    // DRF-304: a challenge without a name is named "<pod name> #N", N counting the pod's games; a declined one keeps its
    // number, both players read the same name, and a retried request doesn't take another number
    @Test fun `pod challenges are named after the pod and numbered per pod`() = fixture { f ->
        val pod = f.pod()
        f.submit(f.a, pod); f.submit(f.b, pod)
        fun challenge(who: AccountService.Principal, opponent: AccountService.Principal, request: ChallengeFromPod =
            ChallengeFromPod(id(), opponent.account.accountId)): GameSummary {
            val result = f.pods.challenge(who, pod, request)
            assertEquals(201, result.status, result.body)
            return json.decodeFromString(result.body)
        }
        val request = ChallengeFromPod(id(), f.b.account.accountId)
        val first = challenge(f.a, f.b, request)
        assertEquals("Surviving pod name #1", first.gameName)
        assertEquals(first, challenge(f.a, f.b, request), "a retry is the same game")
        assertEquals("accepted", f.stream(f.b, first).execute(GameAction.DeclineInvitation).outcome)
        val second = challenge(f.b, f.a)
        assertEquals("Surviving pod name #2", second.gameName)
        assertEquals("Surviving pod name #2", f.games.head(f.a, second.id).summary.gameName, "the other player reads the same name")
        // A name given with the request is the game's
        assertEquals("Our rematch", challenge(f.a, f.b, ChallengeFromPod(id(), f.b.account.accountId, "Our rematch")).gameName)
        assertEquals("Surviving pod name #4", challenge(f.a, f.b).gameName)
    }

    // DRF-137
    @Test fun `decks ready goes immediately to every member except last submitter once`() = fixture { f ->
        val pod = f.pod(three = true)
        val activity = org.asyncttwiz.server.activity.ActivityService(f.runtime)
        fun ready(who: AccountService.Principal) = activity.page(who, "0").entries.filter { it.kind == ActivityKind.POD_DECKS_READY }
        f.submit(f.a, pod); f.submit(f.b, pod)
        assertTrue(ready(f.a).isEmpty()); assertTrue(ready(f.b).isEmpty()); assertTrue(ready(f.c).isEmpty())
        f.submit(f.c, pod)
        assertEquals(pod, ready(f.a).single().gameId)
        assertEquals("Surviving pod name", ready(f.a).single().resourceName)
        assertEquals(f.c.account.accountId, ready(f.b).single().authorId)
        assertTrue(ready(f.c).isEmpty())
        val detail = f.detail(f.c, pod)
        assertEquals(409, f.pods.submit(f.c, pod, SubmitPodDeck(id(), detail.draftAttemptId!!)).status)
        assertEquals(1, ready(f.a).size); assertEquals(1, ready(f.b).size)
    }

    // DRF-137, I-D57: one notice per thing that needs you: members with a queued challenge of the pod hear of it from the
    // challenge, not from "Decks ready" too; pod notices name the cube the pod drafts
    @Test fun `decks ready skips members with a queued challenge`() = fixture { f ->
        val pod = f.pod(three = true)
        val activity = org.asyncttwiz.server.activity.ActivityService(f.runtime)
        fun ready(who: AccountService.Principal) = activity.page(who, "0").entries.filter { it.kind == ActivityKind.POD_DECKS_READY }
        f.submit(f.a, pod)
        f.challenge(pod) // held until Bob submits
        f.submit(f.b, pod)
        assertEquals(1, activity.page(f.b, "0").entries.count { it.kind == ActivityKind.INVITATION }, "Bob is invited once")
        f.submit(f.c, pod)
        assertTrue(ready(f.a).isEmpty(), "Alice's challenge is queued"); assertTrue(ready(f.b).isEmpty(), "so is the one Bob got")
        assertTrue(ready(f.c).isEmpty(), "Carol submitted last")
        assertEquals("Cube", activity.page(f.c, "0").entries.first { it.kind.name.startsWith("POD_") }.sourceName)
    }

    // DRF-125, LOB-042
    @Test fun `pod deck names are private head metadata never shared preparation or replay`() = fixture { f ->
        val pod = f.pod()
        val attempt = f.detail(f.a, pod).draftAttemptId!!
        // DRF-166, DRF-229: a pod game's deck shows the member's chosen cover
        val aCover = f.detail(f.a, pod).let { detail -> detail.draft!!.pool.first { it.id == detail.build!!.copies.last().copyId }.definition }
        assertEquals(200, f.pods.deckCover(f.a, pod, SetPodDeckCover(id(), attempt, aCover)).status)
        val aName = "Private white strategy"
        val bName = "Private green strategy"
        assertEquals(200, f.pods.deckName(f.a, pod, SetPodDeckName(id(), attempt, aName)).status)
        assertEquals(200, f.pods.deckName(f.b, pod, SetPodDeckName(id(), attempt, bName)).status)
        f.submit(f.a, pod); f.submit(f.b, pod)
        val game = f.challenge(pod)
        assertEquals(aCover, f.preparation(game).players.single { it.accountId == f.a.account.accountId }.deck!!.cover)
        assertEquals(aName, f.games.head(f.a, game.id).ownPodDeckName)
        assertNull(f.games.subscriptionHead(f.b, game.id).ownPodDeckName)
        val recipient = f.stream(f.b, game)
        recipient.execute(GameAction.AcceptInvitation)
        val owner = f.stream(f.a, game)
        fun checkHeads() {
            for ((player, mine, other) in listOf(Triple(f.a, aName, bName), Triple(f.b, bName, aName))) {
                val head = f.games.head(player, game.id)
                assertEquals(mine, head.ownPodDeckName)
                assertFalse(GameWire.encode(head).contains(other))
                val revisions = f.games.revisions(player, game.id, "0").revisions.map(GameWire::encode)
                assertTrue(revisions.none { aName in it || bName in it })
            }
        }
        checkHeads()
        owner.execute(GameAction.Ready); recipient.execute(GameAction.Ready)
        checkHeads()
        assertEquals(200, f.pods.destroy(f.a, pod, PodRequest(id())).status)
        checkHeads()
    }

    // DRF-105, DRF-166, DRF-229, LOB-047, LOB-226 (owner, 2026-11-11): after submitting, the deck renamed or given another cover (from Prepare
    // deck in a pod game's lobby) changes the pod deck and every one of the member's games of the pod still in its lobby, the
    // cover as a revision of each game; the private name stays out of the revisions
    @Test fun `a submitted pod deck renamed or given another cover changes its games in the lobby`() = fixture(uniqueCards = true) { f ->
        val pod = f.pod()
        f.submit(f.a, pod); f.submit(f.b, pod)
        val game = f.challenge(pod)
        // Bob's challenge, which Alice accepted (her deck is prepared on accepting)
        val other = f.challenge(pod, f.b, f.a)
        f.stream(f.a, other).execute(GameAction.AcceptInvitation)
        val attempt = f.detail(f.a, pod).draftAttemptId!!
        fun aDeck(of: GameSummary) = f.games.head(f.a, of.id).preparation!!.players.single { it.accountId == f.a.account.accountId }.deck!!
        val cover = f.detail(f.a, pod).let { detail -> detail.build!!.copies.map { copy -> detail.draft!!.pool.first { it.id == copy.copyId }.definition } }
            .first { it != aDeck(game).cover }
        val revisions = f.games.revisions(f.a, game.id, "0").revisions.size
        val name = "Renamed after submitting"
        assertEquals(200, f.pods.deckName(f.a, pod, SetPodDeckName(id(), attempt, name)).status)
        assertEquals(200, f.pods.deckCover(f.a, pod, SetPodDeckCover(id(), attempt, cover)).status)
        for (each in listOf(game, other)) {
            assertEquals(name, f.games.head(f.a, each.id).ownPodDeckName)
            assertEquals(cover, aDeck(each).cover)
        }
        assertEquals(name, f.detail(f.a, pod).deckName)
        val after = f.games.revisions(f.a, game.id, "0").revisions
        assertEquals(revisions + 2, after.size, "one revision for the name, one for the cover")
        assertEquals(cover, after.last().preparation!!.players.single { it.accountId == f.a.account.accountId }.deck!!.cover)
        assertTrue(after.map(GameWire::encode).none { name in it }, "the private name stays out of revisions")
        assertNotEquals(name, f.games.head(f.b, game.id).ownPodDeckName)
    }

    // DRF-125
    @Test fun `a shown pod deck sends its private name to the opponent only while shown`() = fixture { f ->
        val pod = f.pod()
        val attempt = f.detail(f.a, pod).draftAttemptId!!
        val aName = "Private white strategy"; val bName = "Private green strategy"
        assertEquals(200, f.pods.deckName(f.a, pod, SetPodDeckName(id(), attempt, aName)).status)
        assertEquals(200, f.pods.deckName(f.b, pod, SetPodDeckName(id(), attempt, bName)).status)
        f.submit(f.a, pod); f.submit(f.b, pod)
        val game = f.challenge(pod)
        val owner = f.stream(f.a, game); val recipient = f.stream(f.b, game)
        // Only a player in the game may set a flag, and only their own: the recipient after accepting.
        assertEquals("accessDenied", recipient.execute(GameAction.ShowDeck(true)).reason)
        recipient.execute(GameAction.AcceptInvitation)
        fun player(viewer: AccountService.Principal, of: AccountService.Principal) =
            f.games.head(viewer, game.id).preparation!!.players.single { it.accountId == of.account.accountId }
        assertFalse(GameWire.encode(f.games.head(f.b, game.id)).contains(aName))
        assertEquals("accepted", owner.execute(GameAction.ShowDeck(true)).outcome)
        for (viewer in listOf(f.a, f.b)) {
            assertTrue(player(viewer, f.a).deckShown); assertEquals(aName, player(viewer, f.a).shownPodDeckName)
            assertFalse(player(viewer, f.b).deckShown); assertNull(player(viewer, f.b).shownPodDeckName)
        }
        assertFalse(GameWire.encode(f.games.head(f.a, game.id)).contains(bName))
        assertTrue(f.games.revisions(f.b, game.id, "0").revisions.last().preparation!!.players
            .single { it.accountId == f.a.account.accountId }.shownPodDeckName == aName)
        assertEquals("accepted", owner.execute(GameAction.ShowDeck(false)).outcome)
        assertFalse(GameWire.encode(f.games.head(f.b, game.id)).contains(aName))
        assertFalse(player(f.b, f.a).deckShown)
        assertEquals(aName, f.games.head(f.a, game.id).ownPodDeckName)
    }

    @Test fun `pod decks follow the deck visibility default per opponent`() = fixture { f ->
        val pod = f.pod(three = true)
        f.submit(f.a, pod); f.submit(f.b, pod); f.submit(f.c, pod)
        fun shown(game: GameSummary, of: AccountService.Principal) =
            f.games.head(of, game.id).preparation!!.players.single { it.accountId == of.account.accountId }.deckShown
        val first = f.challenge(pod); val owner = f.stream(f.a, first); val recipient = f.stream(f.b, first)
        recipient.execute(GameAction.AcceptInvitation)
        assertFalse(shown(first, f.a)); assertFalse(shown(first, f.b))
        // A challenge sent before the first game ends already holds the challenger's pod deck.
        val early = f.challenge(pod, who = f.b, opponent = f.a)
        assertFalse(shown(early, f.b))
        owner.execute(GameAction.Ready); recipient.execute(GameAction.Ready)
        assertEquals("accepted", recipient.execute(f.games.concession(f.b, first.id)).outcome)
        // Back in the lobby for the next play, both pod decks are shown with their names.
        assertTrue(shown(first, f.a)); assertTrue(shown(first, f.b))
        val aDeck = f.games.head(f.a, first.id).ownPodDeckName!!
        assertEquals(aDeck, f.games.head(f.b, first.id).preparation!!.players.single { it.accountId == f.a.account.accountId }.shownPodDeckName)
        // So is the challenger's deck in the challenge already sent, and the recipient's once they accept.
        assertTrue(shown(early, f.b))
        f.stream(f.a, early).execute(GameAction.AcceptInvitation)
        assertTrue(shown(early, f.a))
        // The same pod decks against the same opponent: both start shown, the inviter's already in the challenge.
        val second = f.challenge(pod)
        assertTrue(shown(second, f.a))
        f.stream(f.b, second).execute(GameAction.AcceptInvitation)
        assertTrue(shown(second, f.b))
        assertTrue(f.games.head(f.b, second.id).preparation!!.players.single { it.accountId == f.a.account.accountId }.shownPodDeckName != null)
        // Against another pod member it is a first game again, unless the account always shows its decks.
        val third = f.challenge(pod, opponent = f.c)
        assertFalse(shown(third, f.a))
        org.asyncttwiz.server.accounts.AccountSettingsService(f.runtime).set(f.c.account.accountId, AccountSettings(DeckVisibility.VISIBLE))
        f.stream(f.c, third).execute(GameAction.AcceptInvitation)
        assertTrue(shown(third, f.c))
    }

    @Test fun `recipient deck lists basics and definitions stay private until acceptance including revisions and declined or withdrawn invitations`() = fixture(uniqueCards = true) { f ->
        val pod = f.pod()
        val counts = PodDeckBasics(plains = 37, island = 4)
        f.submit(f.a, pod); f.submit(f.b, pod, counts)
        val detail = f.detail(f.b, pod)
        val pool = detail.draft!!.pool.associateBy { it.id }
        // LOB-141: the submitted deck keeps the Build's Maybe
        val submitted = detail.deck!!.copies
        assertEquals(detail.build!!.copies, submitted)
        assertEquals(1, submitted.count { it.category == PodDeckCategory.MAYBE })
        val privateDigests = submitted.map { pool.getValue(it.copyId).definition }.toSet()
        fun privatePreparation(preparation: LobbyPreparation) {
            assertNull(preparation.players.single { it.accountId == f.b.account.accountId }.deck)
            assertTrue(preparation.definitions.none { it.digest in privateDigests })
        }
        fun privatePayloads(game: GameSummary) {
            val head = f.games.head(f.a, game.id)
            privatePreparation(head.preparation!!)
            privatePreparation(f.games.subscriptionHead(f.a, game.id).preparation!!)
            val revisions = f.games.revisions(f.a, game.id, "0").revisions
            revisions.mapNotNull { it.preparation }.forEach(::privatePreparation)
            val payloads = listOf(GameWire.encode(head), f.lobby.head(f.a, game.id).body) + revisions.map(GameWire::encode)
            for (definition in detail.definitions.filter { it.digest in privateDigests }) {
                assertTrue(payloads.none { definition.digest in it || definition.definition.name in it })
            }
        }
        val game = f.challenge(pod)
        val owner = f.stream(f.a, game)
        privatePayloads(game)
        assertEquals("accepted", owner.execute(GameAction.Ready).outcome)
        assertTrue(f.games.revisions(f.a, game.id, "0").revisions.isNotEmpty())
        privatePayloads(game)
        assertEquals("accepted", f.stream(f.b, game).execute(GameAction.AcceptInvitation).outcome)
        val preparation = f.preparation(game)
        val deck = preparation.players.single { it.accountId == f.b.account.accountId }.deck!!
        assertEquals(counts, PodGamePreparation.basics(deck))
        for (copy in submitted) {
            val entry = deck.entries.single { it.definition == pool.getValue(copy.copyId).definition }
            assertEquals(when (copy.category) {
                PodDeckCategory.MAIN -> SavedDeckSection.MAINBOARD
                PodDeckCategory.SIDE -> SavedDeckSection.SIDEBOARD
                PodDeckCategory.MAYBE -> SavedDeckSection.MAYBEBOARD
            }, entry.section)
        }
        assertTrue(privateDigests.all { digest -> preparation.definitions.any { it.digest == digest } })
        assertEquals(preparation, f.games.subscriptionHead(f.a, game.id).preparation)
        assertEquals(preparation, f.games.revisions(f.a, game.id, "0").revisions.last().preparation)

        val declined = f.challenge(pod)
        assertEquals("accepted", f.stream(f.b, declined).execute(GameAction.DeclineInvitation).outcome)
        privatePayloads(declined)
        val withdrawn = f.challenge(pod)
        privatePayloads(withdrawn)
        assertEquals(200, f.pods.destroy(f.a, pod, PodRequest(id())).status)
        assertNull(f.games.subscriptionHead(f.a, declined.id).preparation)
        assertNull(f.games.subscriptionHead(f.a, withdrawn.id).preparation)
    }

    // DRF-042
    @Test fun `pod readiness sideboarding basics and accepted games stay independent after destroy`() = fixture { f ->
        val pod = f.pod(); f.submit(f.a, pod); f.submit(f.b, pod)
        val game = f.challenge(pod); val owner = f.stream(f.a, game); val recipient = f.stream(f.b, game)
        recipient.execute(GameAction.AcceptInvitation)
        val deck = f.preparation(game).players.first { it.accountId == f.a.account.accountId }.deck!!
        owner.execute(GameAction.Ready)
        // LOB-052: basics move to Side like any card; Main under 40 blocks Ready
        val plains = deck.entries.first { it.suppliedBasic == BasicLand.PLAINS && it.section == SavedDeckSection.MAINBOARD }
        for (copy in 0..1) assertEquals("accepted", owner.execute(GameAction.TransferPreparedCard(deck.id, deck.card(plains, copy).id,
            (GameWire.ordinal(plains.firstIndex) + copy).toString(), plains.section)).outcome)
        assertFalse(f.preparation(game).players.first { it.accountId == f.a.account.accountId }.ready)
        assertEquals("mainTooSmall", owner.execute(GameAction.Ready).reason)
        for (copy in 0..1) assertEquals("accepted", owner.execute(GameAction.TransferPreparedCard(deck.id, deck.card(plains, copy).id,
            (GameWire.ordinal(plains.firstIndex) + copy).toString(), SavedDeckSection.SIDEBOARD)).outcome)
        val card = deck.entries.first { it.section == SavedDeckSection.MAINBOARD && it.suppliedBasic == null }
        assertEquals("accepted", owner.execute(GameAction.TransferPreparedCard(deck.id, deck.card(card, 0).id, card.firstIndex, card.section)).outcome)
        assertEquals("sideboardLimit", owner.execute(GameAction.Ready).reason)
        owner.execute(GameAction.TransferPreparedCard(deck.id, deck.card(card, 0).id, card.firstIndex, SavedDeckSection.SIDEBOARD))
        val fresh = f.challenge(pod)
        assertEquals(PodDeckBasics(8, 8, 8, 8, 8), PodGamePreparation.basics(f.preparation(fresh).players.first { it.accountId == f.a.account.accountId }.deck!!))
        val acceptedLobby = f.challenge(pod)
        f.stream(f.b, acceptedLobby).execute(GameAction.AcceptInvitation)
        owner.execute(GameAction.Ready); assertEquals("accepted", recipient.execute(GameAction.Ready).outcome)
        val started = f.games.head(f.a, game.id)
        assertEquals(GameLifecycle.ACTIVE, started.summary.lifecycle)
        assertEquals(82, started.play!!.board.cards.size)
        assertEquals(200, f.pods.destroy(f.a, pod, PodRequest(id())).status)
        assertSameStart(started, f.a.account.accountId, f.games.head(f.b, game.id), f.b.account.accountId)
        assertEquals(GameLifecycle.LOBBY, f.games.head(f.b, acceptedLobby.id).summary.lifecycle)
        assertEquals("Surviving pod name", f.games.head(f.b, acceptedLobby.id).summary.podName)
        assertEquals(404, f.lobby.head(f.a, fresh.id).status)
        // The accepted but unstarted game's definitions still support editing and starting.
        val remaining = f.stream(f.a, acceptedLobby)
        assertEquals("accepted", remaining.execute(GameAction.Ready).outcome)
        assertEquals("accepted", f.stream(f.b, acceptedLobby).execute(GameAction.Ready).outcome)
        assertEquals(GameLifecycle.ACTIVE, f.games.head(f.a, acceptedLobby.id).summary.lifecycle)
        assertEquals("accepted", recipient.execute(f.games.concession(f.b, game.id)).outcome)
        val rematch = f.games.head(f.a, game.id)
        assertEquals(GameLifecycle.LOBBY, rematch.summary.lifecycle)
        assertEquals(PodGameRules(1), rematch.preparation!!.podRules)
        // The game's own deck as last prepared (its basics moved back to Main above)
        assertEquals(PodDeckBasics(8, 8, 8, 8, 8), PodGamePreparation.basics(rematch.preparation!!.players.first { it.accountId == f.a.account.accountId }.deck!!))
        assertTrue(rematch.preparation!!.players.all { !it.ready })
    }

    // DRF-039, DRF-042, DRF-235, DRF-236 (owner, 2026-11-22): a three-member Arena pod; Alice and Bob finish their own picks
    // early, submit and play while Carol still drafts: one match finished, one in play, one game accepted in its lobby. Alice
    // cancels the draft: those games carry on, orphaned from the pod: its Matches and Standings no longer count them (nor the
    // match the one in play finishes later), and the next draft's deck name doesn't reach the one in its lobby
    @Test fun `games carrying on past a cancelled draft are orphaned from the pod's standings and matches`() = fixture { f ->
        val pod = json.decodeFromString<PodDetail>(f.pods.create(f.a, CreatePod(id(), "Cancelled pod", f.cube.cube.id, DraftMode.ARENA)).body).id
        for (member in listOf(f.b, f.c)) {
            assertEquals(200, f.pods.invite(f.a, pod, InviteToPod(id(), member.account.accountId)).status)
            assertEquals(200, f.pods.invitation(member, pod, PodRequest(id()), true).status)
        }
        assertEquals(200, f.pods.update(f.a, pod, UpdatePod(id(), settings = DraftSettings.arena().copy(packsPerPlayer = 3, cardsPerPack = 2,
            keep = 1, ignoreRarity = true, reuseCards = true, sideboardLimit = null))).status)
        assertEquals(200, f.pods.start(f.a, pod, PodRequest(id())).status)
        fun picks(who: AccountService.Principal) = repeat(3) {
            val detail = f.detail(who, pod); val head = detail.draft!!.head!!
            assertEquals(200, f.pods.pick(who, pod, PickPod(id(), detail.draftAttemptId!!, head.id,
                head.cards.take(detail.settings.keepAt(head.pick - 1)).map { it.id })).status)
        }
        fun submit(who: AccountService.Principal) {
            val attempt = f.detail(who, pod).draftAttemptId!!
            assertEquals(200, f.pods.basics(who, pod, SetPodBasics(id(), attempt, PodDeckBasics(plains = 40))).status)
            assertEquals(200, f.pods.submit(who, pod, SubmitPodDeck(id(), attempt)).status)
        }
        picks(f.a); picks(f.b); submit(f.a); submit(f.b)
        assertEquals(PodPhase.DRAFTING, f.detail(f.a, pod).phase, "Carol still drafts")
        fun started(): Pair<GameSummary, Stream> {
            val game = f.challenge(pod); val bob = f.stream(f.b, game)
            bob.execute(GameAction.AcceptInvitation)
            f.stream(f.a, game).execute(GameAction.Ready); assertEquals("accepted", bob.execute(GameAction.Ready).outcome)
            assertEquals(GameLifecycle.ACTIVE, f.games.head(f.a, game.id).summary.lifecycle)
            return game to bob
        }
        val (finished, finishedBob) = started()
        assertEquals("accepted", finishedBob.execute(f.games.concession(f.b, finished.id)).outcome)
        val (playing, playingBob) = started()
        val lobbyGame = f.challenge(pod); f.stream(f.b, lobbyGame).execute(GameAction.AcceptInvitation)
        val podId = UUID.fromString(pod)
        fun matches() = f.sql { org.asyncttwiz.server.history.MatchRecords.forPod(it, UUID.fromString(f.c.account.accountId), podId) }!!
        assertEquals(listOf(finished.id), matches().records.map { it.gameId })
        assertEquals(listOf(playing.id), matches().playing.map { it.gameId })

        assertEquals(200, f.pods.cancel(f.a, pod, PodDraftRequest(id(), f.detail(f.a, pod).draftAttemptId!!)).status)
        assertEquals(PodPhase.SETUP, f.detail(f.a, pod).phase)
        assertEquals(PodMatches(emptyList(), emptyList()), matches(), "no standings or matches from the cancelled draft")
        // They carry on as ordinary games, still named after the pod they came from
        assertEquals(GameLifecycle.ACTIVE, f.games.head(f.a, playing.id).summary.lifecycle)
        assertEquals(GameLifecycle.LOBBY, f.games.head(f.b, lobbyGame.id).summary.lifecycle)
        assertEquals("Cancelled pod", f.games.head(f.b, lobbyGame.id).summary.podName)
        // (owner, 2026-11-23) marked orphaned for both players' apps (out of the pod's Games tab, no Pod chat); the game in its
        // lobby is told at once with a revision
        for (game in listOf(finished, playing, lobbyGame)) assertTrue(f.games.head(f.b, game.id).summary.podOrphaned)
        assertTrue(f.games.revisions(f.a, lobbyGame.id, "0").revisions.last().summary.podOrphaned)
        assertTrue(json.decodeFromString<GameList>(f.lobby.list(f.b, null, false).body).games.filter { it.podId == pod }.all { it.podOrphaned })
        assertTrue(json.decodeFromString<GameList>(f.lobby.list(f.a, null, false).body).games.map { it.id }
            .containsAll(listOf(finished.id, playing.id, lobbyGame.id)), "still in the Games list")
        // DRF-039: the deck of an orphaned game in play stays as it is (Prepare deck is the lobby's)
        assertEquals(409, f.pods.gameDeck(f.a, pod, playing.id, SetPodGameDeck(id(), name = "In play")).status)
        // The match in play ends after the cancel: its record is the players' own, not the pod's
        assertEquals("accepted", playingBob.execute(f.games.concession(f.b, playing.id)).outcome)
        assertEquals(PodMatches(emptyList(), emptyList()), matches())
        val history = f.sql { org.asyncttwiz.server.history.MatchRecords.forAccount(it, UUID.fromString(f.a.account.accountId)) }
        assertEquals(setOf(finished.id, playing.id), history.map { it.gameId }.toSet())
        assertTrue(history.all { it.podName == "Cancelled pod" })

        // DRF-105, DRF-166 (owner, 2026-11-23): Prepare deck in the orphaned game's lobby renames and covers that game's deck
        // alone; not a game in play, nor someone else's
        fun gameDeck(who: AccountService.Principal, game: GameSummary, input: SetPodGameDeck) = f.pods.gameDeck(who, pod, game.id, input).status
        assertEquals(200, gameDeck(f.a, lobbyGame, SetPodGameDeck(id(), name = "  The cancelled draft's deck ")))
        assertEquals("The cancelled draft's deck", f.games.head(f.a, lobbyGame.id).ownPodDeckName)
        assertNotEquals("The cancelled draft's deck", f.games.head(f.b, lobbyGame.id).ownPodDeckName)
        fun aDeck() = f.games.head(f.a, lobbyGame.id).preparation!!.players.single { it.accountId == f.a.account.accountId }.deck!!
        val plains = aDeck().entries.first { it.suppliedBasic == BasicLand.PLAINS }.definition
        assertEquals(400, gameDeck(f.a, lobbyGame, SetPodGameDeck(id(), cover = plains)), "a supplied basic is no cover")
        val card = aDeck().entries.first { it.suppliedBasic == null && it.section == SavedDeckSection.MAINBOARD }.definition
        assertEquals(200, gameDeck(f.a, lobbyGame, SetPodGameDeck(id(), cover = card)))
        assertEquals(card, aDeck().cover)
        assertEquals(404, gameDeck(f.c, lobbyGame, SetPodGameDeck(id(), name = "Not mine")))
        // The next draft: Alice's new deck name stays out of the orphaned game's lobby
        val before = f.games.head(f.a, lobbyGame.id).ownPodDeckName
        assertEquals(200, f.pods.start(f.a, pod, PodRequest(id())).status)
        picks(f.a)
        assertEquals(200, f.pods.deckName(f.a, pod, SetPodDeckName(id(), f.detail(f.a, pod).draftAttemptId!!, "The next draft's deck")).status)
        assertEquals(before, f.games.head(f.a, lobbyGame.id).ownPodDeckName)
        // Its games number on from the cancelled draft's
        submit(f.a); picks(f.b); submit(f.b)
        val next = json.decodeFromString<GameSummary>(f.pods.challenge(f.a, pod, ChallengeFromPod(id(), f.b.account.accountId)).body)
        assertEquals("Cancelled pod #4", next.gameName)
        // A game of the pod's own draft changes the pod deck instead (its own requests)
        assertEquals(409, gameDeck(f.a, next, SetPodGameDeck(id(), name = "Not orphaned")))
    }

    // DRF-041
    @Test fun `leave withdraws only departed members unaccepted challenges and final leave destroys pod`() = fixture { f ->
        val pod = f.pod(three = true)
        listOf(f.a, f.b, f.c).forEach { f.submit(it, pod) }
        val ab = f.challenge(pod); val ac = f.challenge(pod, opponent = f.c); val ca = f.challenge(pod, f.c, f.a)
        val accepted = f.challenge(pod, f.b, f.c)
        f.stream(f.c, accepted).execute(GameAction.AcceptInvitation)
        assertEquals(200, f.pods.leave(f.c, pod, PodRequest(id())).status)
        assertEquals(404, f.lobby.head(f.a, ac.id).status); assertEquals(404, f.lobby.head(f.a, ca.id).status)
        assertEquals(200, f.lobby.head(f.a, ab.id).status)
        assertEquals(200, f.lobby.head(f.c, accepted.id).status)
        assertEquals(200, f.pods.leave(f.b, pod, PodRequest(id())).status)
        assertEquals(404, f.pods.read(f.a, pod).status); assertEquals(404, f.lobby.head(f.a, ab.id).status)
        assertEquals(200, f.lobby.head(f.c, accepted.id).status)
    }

    // DRF-122, DRF-137: notified at once ("<pod>: <name> challenged you", held), and submitting the deck adds no notice
    @Test fun `challenge before the opponent submits is held until they submit then plays through to a started game`() = fixture { f ->
        val pod = f.pod(sideboardLimit = 3)
        val activity = org.asyncttwiz.server.activity.ActivityService(f.runtime)
        fun invitations(who: AccountService.Principal) = activity.page(who, "0").entries.filter { it.kind == ActivityKind.INVITATION }
        f.submit(f.a, pod)
        val game = f.challenge(pod)
        assertTrue(game.invitationHeld)
        assertFalse(json.encodeToString(game.copy(invitationHeld = false)).contains("invitationHeld")) // Older clients never see it.
        // The recipient sees the held challenge among their invitations at once and is notified at once, but cannot accept
        // it yet.
        val incoming = json.decodeFromString<GameList>(f.lobby.list(f.b, null, true).body).games.single()
        assertEquals(game.id, incoming.id); assertTrue(incoming.invitationHeld)
        assertTrue(json.decodeFromString<GameList>(f.lobby.list(f.b, null, false).body).games.isEmpty())
        assertEquals(404, f.lobby.head(f.b, game.id).status)
        val early = f.stream(f.b, game)
        assertTrue(f.games.head(f.b, game.id).summary.invitationHeld)
        assertNull(f.games.head(f.b, game.id).preparation)
        assertEquals("inapplicable", early.execute(GameAction.AcceptInvitation).reason)
        val notice = invitations(f.b).single()
        assertEquals(game.id, notice.gameId); assertEquals(f.a.account.accountId, notice.authorId)
        assertEquals(f.a.account.username, notice.opponentName); assertEquals(game.podName, notice.resourceName)
        assertEquals(pod, notice.podId); assertTrue(notice.held)
        // The challenger sees it, prepares and gets ready meanwhile.
        assertTrue(json.decodeFromString<GameList>(f.lobby.list(f.a, null, false).body).games.single().invitationHeld)
        val owner = f.stream(f.a, game)
        val deck = f.preparation(game).players.single { it.accountId == f.a.account.accountId }.deck!!
        val card = deck.entries.first { it.section == SavedDeckSection.MAINBOARD && it.suppliedBasic == null }
        assertEquals("accepted", owner.execute(GameAction.TransferPreparedCard(deck.id, deck.card(card, 0).id, card.firstIndex, card.section)).outcome)
        assertEquals("accepted", owner.execute(GameAction.Ready).outcome)
        assertEquals(GameLifecycle.LOBBY, f.games.head(f.a, game.id).summary.lifecycle)

        assertTrue(f.told.isEmpty(), "Alice's submit released nothing")
        f.submit(f.b, pod)
        val released = json.decodeFromString<GameList>(f.lobby.list(f.b, null, true).body).games.single()
        assertEquals(game.id, released.id); assertFalse(released.invitationHeld)
        // DRF-181 (owner 2026-11-22): the release is told to the game's subscribed tables once it has committed, not left to
        // their next heartbeat or list read
        assertEquals(listOf(listOf(game.id)), f.told)
        assertFalse(json.decodeFromString<GameSummary>(f.lobby.head(f.a, game.id).body).invitationHeld)
        // No second notice: the one sent with the challenge now reads as an acceptable one
        assertEquals(notice.copy(held = false), invitations(f.b).single())
        val recipient = f.stream(f.b, game)
        assertEquals("accepted", recipient.execute(GameAction.AcceptInvitation).outcome)
        assertTrue(f.preparation(game).players.single { it.accountId == f.a.account.accountId }.ready)
        val theirs = f.preparation(game).players.single { it.accountId == f.b.account.accountId }.deck!!
        val theirCard = theirs.entries.first { it.section == SavedDeckSection.MAINBOARD && it.suppliedBasic == null }
        assertEquals("accepted", recipient.execute(GameAction.TransferPreparedCard(theirs.id, theirs.card(theirCard, 0).id,
            theirCard.firstIndex, theirCard.section)).outcome)
        assertEquals("accepted", recipient.execute(GameAction.Ready).outcome)
        val started = f.games.head(f.a, game.id)
        assertEquals(GameLifecycle.ACTIVE, started.summary.lifecycle)
        assertEquals(80, started.play!!.board.cards.size) // Each main lost one card to the side.
    }

    // DRF-122, DRF-137
    @Test fun `a held challenge can be declined and reinvited before submitting and notifies at once`() = fixture { f ->
        val pod = f.pod()
        val activity = org.asyncttwiz.server.activity.ActivityService(f.runtime)
        fun invitations(who: AccountService.Principal) = activity.page(who, "0").entries.filter { it.kind == ActivityKind.INVITATION }
        f.submit(f.a, pod)
        val game = f.challenge(pod)
        assertEquals("accepted", f.stream(f.b, game).execute(GameAction.DeclineInvitation).outcome)
        assertEquals(InvitationState.DECLINED, f.games.head(f.a, game.id).summary.invitationState)
        assertTrue(json.decodeFromString<GameList>(f.lobby.list(f.b, null, true).body).games.isEmpty())
        assertTrue(invitations(f.b).isEmpty(), "a declined invitation's notice is gone")
        // Reinviting while still held notifies at once too; submitting adds nothing.
        assertEquals("accepted", f.stream(f.a, game).execute(GameAction.Reinvite).outcome)
        assertTrue(json.decodeFromString<GameList>(f.lobby.list(f.b, null, true).body).games.single().invitationHeld)
        assertTrue(invitations(f.b).single().held)
        f.submit(f.b, pod)
        assertFalse(json.decodeFromString<GameList>(f.lobby.list(f.b, null, true).body).games.single().invitationHeld)
        assertEquals(game.id, invitations(f.b).single().gameId)
        assertFalse(invitations(f.b).single().held)
        assertEquals("accepted", f.stream(f.b, game).execute(GameAction.AcceptInvitation).outcome)
    }

    @Test fun `a challenge declined while held is released silently`() = fixture { f ->
        val pod = f.pod()
        val activity = org.asyncttwiz.server.activity.ActivityService(f.runtime)
        f.submit(f.a, pod)
        val game = f.challenge(pod)
        assertEquals("accepted", f.stream(f.b, game).execute(GameAction.DeclineInvitation).outcome)
        f.submit(f.b, pod)
        assertFalse(f.games.head(f.a, game.id).summary.invitationHeld)
        assertTrue(activity.page(f.b, "0").entries.none { it.kind == ActivityKind.INVITATION })
    }

    @Test fun `held challenges are withdrawn when either player leaves and released per recipient`() = fixture { f ->
        val pod = f.pod(three = true)
        f.submit(f.a, pod)
        val ab = f.challenge(pod); val ac = f.challenge(pod, opponent = f.c)
        assertEquals(200, f.pods.leave(f.b, pod, PodRequest(id())).status)
        assertEquals(404, f.lobby.head(f.a, ab.id).status)
        assertEquals(200, f.lobby.head(f.a, ac.id).status)
        f.submit(f.c, pod)
        assertEquals(ac.id, json.decodeFromString<GameList>(f.lobby.list(f.c, null, true).body).games.single().id)
        assertEquals("accepted", f.stream(f.c, ac).execute(GameAction.AcceptInvitation).outcome)
    }

    // LOB-141, DRF-120: a pod game's cards outside the game are the pod deck's Side and Maybe (the Build's Maybe), never in
    // the library; the sideboard limit counted Side only (DRF-025)
    @Test fun `a pod game's cards outside the game are the pod deck's Side and Maybe`() = fixture(uniqueCards = true) { f ->
        val pod = f.pod()
        f.submit(f.a, pod); f.submit(f.b, pod)
        val detail = f.detail(f.a, pod)
        val names = detail.definitions.associate { it.digest to it.definition.name }
        val pool = detail.draft!!.pool.associate { it.id to names.getValue(it.definition) }
        fun named(category: PodDeckCategory) = detail.build!!.copies.filter { it.category == category }.map { pool.getValue(it.copyId) }
        val game = f.challenge(pod); val owner = f.stream(f.a, game); val recipient = f.stream(f.b, game)
        recipient.execute(GameAction.AcceptInvitation)
        owner.execute(GameAction.Ready); assertEquals("accepted", recipient.execute(GameAction.Ready).outcome)
        val head = f.games.head(f.a, game.id)
        assertEquals(GameLifecycle.ACTIVE, head.summary.lifecycle)
        val snapshot = head.engine!!
        val model = org.asyncttwiz.protocol.game.EngineJson(org.asyncttwiz.protocol.game.EngineJson.decodeDefinitions(snapshot.definitions))
            .decodeModel(snapshot.state)
        val alice = model.engine.player(arcanum.engine.state.PlayerId(f.a.account.accountId))
        fun name(id: arcanum.engine.state.ObjectId) = model.engine.obj(id)!!.definition!!.faces.first().name
        assertEquals(named(PodDeckCategory.SIDE) + named(PodDeckCategory.MAYBE), alice.outside.map(::name))
        assertEquals(1, named(PodDeckCategory.MAYBE).size)
        assertEquals(41, alice.library.size + alice.hand.size)
        assertTrue((alice.library + alice.hand).map(::name).none { it in named(PodDeckCategory.MAYBE) })
    }

    private fun <T> Fixture.sql(work: (org.jooq.DSLContext) -> T): T =
        runtime.transaction { work(org.jooq.impl.DSL.using(it, org.jooq.SQLDialect.POSTGRES)) }
    private fun Fixture.open(who: AccountService.Principal): Set<String> =
        sql { org.asyncttwiz.server.activity.ActivityNotices.open(it, UUID.fromString(who.account.accountId)) }

    // LOB-243, DRF-138 (owner, 2026-11-20): "Decks ready" waits for each member until they have opened the pod on any device;
    // then the server clears it on every device of theirs (a clear push to their phone), and another member's still waits
    @Test fun `pod news clears on every device once the pod was opened on any one`() = fixture { f ->
        val pod = f.pod(three = true)
        val activity = org.asyncttwiz.server.activity.ActivityService(f.runtime)
        val phone = activity.register(f.a, RegisterInstallation(id(), id(), "synthetic-android-token"))
        val cursor = activity.page(f.a, null).nextCursor
        f.submit(f.a, pod); f.submit(f.b, pod); f.submit(f.c, pod)
        val key = "pod:$pod"
        // Her phone's poll may show it (remembered until it waits no more)
        assertEquals(ActivityKind.POD_DECKS_READY, activity.page(f.a, cursor).readyNotices!!.last { it.gameId == pod }.kind)
        assertTrue(key in f.open(f.a)); assertTrue(key in f.open(f.b))
        f.sql { it.execute("DELETE FROM activity_push") }
        assertEquals(1, activity.podSeen(f.a, PodSeen(pod)), "Alice opened the pod on one of her devices")
        assertFalse(key in f.open(f.a), "seen: it waits no more")
        assertTrue(key in f.open(f.b), "Bob hasn't opened it")
        assertEquals(0, activity.podSeen(f.a, PodSeen(pod)), "seen once")
        f.sql { org.asyncttwiz.server.activity.ActivityNotices.sweep(it, java.time.OffsetDateTime.now()) }
        assertEquals(1, f.sql { it.fetch("SELECT 1 FROM activity_push WHERE generation = '${phone.generation}' " +
            "AND kind = 'CLEAR_POD' AND game_id = '$pod'").size }, "a clear for her phone")
        assertFalse(activity.page(f.a, null).openNotices!!.contains(key), "the phone's refresh clears it")
    }

    // LOB-243: a destroyed pod can't be opened; its notice is seen on the Pods list, where it leads
    @Test fun `a destroyed pod's notice is seen on the Pods list`() = fixture { f ->
        val pod = f.pod()
        val activity = org.asyncttwiz.server.activity.ActivityService(f.runtime)
        assertEquals(200, f.pods.destroy(f.a, pod, PodRequest(id())).status)
        assertEquals(ActivityKind.POD_DESTROYED, activity.page(f.b, "0").entries.last { it.gameId == pod }.kind)
        assertTrue("pod:$pod" in f.open(f.b))
        assertEquals(1, activity.podSeen(f.b, PodSeen()))
        assertFalse("pod:$pod" in f.open(f.b))
    }

    // DRF-135, DRF-122, DRF-137 (owner, 2026-11-20): one notification per pod: a challenge to a member still building replaces
    // their "Draft complete: build your deck" (the same key on the phone, the same tag in a browser), and waits until answered
    @Test fun `a pod challenge is its pod's one notification`() = fixture { f ->
        val pod = f.pod()
        val activity = org.asyncttwiz.server.activity.ActivityService(f.runtime)
        f.submit(f.a, pod)
        val complete = activity.page(f.b, "0").entries.last { it.kind == ActivityKind.POD_DRAFT_COMPLETE }
        val game = f.challenge(pod)
        val challenge = activity.page(f.b, "0").entries.single { it.kind == ActivityKind.INVITATION }
        assertEquals(pod, challenge.podId)
        val key = "pod:$pod"
        assertEquals(key, org.asyncttwiz.server.activity.ActivityNotices.key(complete))
        assertEquals(key, org.asyncttwiz.server.activity.ActivityNotices.key(challenge))
        assertEquals(key, org.asyncttwiz.server.activity.WebPushNotices.of(challenge).tag)
        assertTrue(ActivityWire.ordinal(challenge.activityId)!! > ActivityWire.ordinal(complete.activityId)!!, "the newer shows")
        assertEquals(setOf(key), f.open(f.b), "no invitation notification of its own")
        // Opening the pod doesn't answer the challenge (nor build the deck): it still waits
        activity.podSeen(f.b, PodSeen(pod))
        assertEquals(setOf(key), f.open(f.b))
        assertEquals("accepted", f.stream(f.b, game).execute(GameAction.DeclineInvitation).outcome)
        f.submit(f.b, pod)
        assertEquals(emptySet<String>(), f.open(f.b), "answered and built: nothing waits")
    }

    // DRF-135, DRF-138, LOB-242, LOB-243 (owner, 2026-11-22): Bob still builds when Alice's challenge replaces his "Draft
    // complete: build your deck"; he declines it on one device: the pod's notification no longer waits and the server clears it
    // on his other devices at once (a clear push to his phone), though his deck is still unsubmitted. A newer pod notice waits
    // on its own again
    @Test fun `a pod challenge declined on one device clears the pod's notification on every device`() = fixture { f ->
        val pod = f.pod(three = true)
        val activity = org.asyncttwiz.server.activity.ActivityService(f.runtime)
        val phone = activity.register(f.b, RegisterInstallation(id(), id(), "synthetic-android-token"))
        val cursor = activity.page(f.b, null).nextCursor
        f.submit(f.a, pod)
        val game = f.challenge(pod)
        val key = "pod:$pod"
        // His phone's poll shows the challenge (remembered until it waits no more)
        assertEquals(ActivityKind.INVITATION, activity.page(f.b, cursor).readyNotices!!.last { it.podId == pod }.kind)
        assertEquals(setOf(key), f.open(f.b))
        f.sql { it.execute("DELETE FROM activity_push") }
        assertEquals("accepted", f.stream(f.b, game).execute(GameAction.DeclineInvitation).outcome)
        assertEquals(emptySet<String>(), f.open(f.b), "declined: nothing waits, his build included")
        f.sql { org.asyncttwiz.server.activity.ActivityNotices.sweep(it, java.time.OffsetDateTime.now()) }
        assertEquals(1, f.sql { it.fetch("SELECT 1 FROM activity_push WHERE generation = '${phone.generation}' " +
            "AND kind = 'CLEAR_POD' AND game_id = '$pod'").size }, "a clear for his phone")
        assertFalse(activity.page(f.b, null).openNotices!!.contains(key), "the phone's refresh clears it")
        // Alice destroys the pod: its notice is newer than the declined challenge and waits for Bob until he sees it
        assertEquals(200, f.pods.destroy(f.a, pod, PodRequest(id())).status)
        assertEquals(setOf(key), f.open(f.b))
    }
}
