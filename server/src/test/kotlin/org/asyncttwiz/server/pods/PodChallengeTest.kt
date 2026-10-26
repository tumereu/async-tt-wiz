package org.asyncttwiz.server.pods

import java.util.UUID
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.cubes.CubeService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.game.GameService
import org.asyncttwiz.server.lobby.LobbyService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PodChallengeTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()
    private val basics = BasicLand.entries.map { land ->
        val card = CardDefinition(land.cardName, listOf(DefinitionFace(id(), land.cardName, typeLine = "Basic Land — ${land.cardName}")))
        SubmittedDefinition(DeckJson.digest(card), card)
    }
    private inner class Fixture(val runtime: ServiceRuntime) {
        private val accounts = AccountService(runtime)
        fun account(name: String): AccountService.Principal {
            accounts.register(name, "$name@example.invalid", "fixture password")
            return accounts.authenticate(accounts.login("$name@example.invalid", "fixture password")!!.token)!!
        }
        val a = account("ChallengeA"); val b = account("ChallengeB"); val c = account("ChallengeC")
        val pods = PodService(runtime); val games = GameService(runtime); val lobby = LobbyService(runtime)
        val decks = DeckService(runtime)
        val card = CardDefinition("Pool card", listOf(DefinitionFace(id(), "Pool card")))
        val digest = DeckJson.digest(card)
        val cube: SavedCube
        init {
            assertEquals(200, decks.submit(a, SubmitDefinitions(id(), listOf(SubmittedDefinition(digest, card)))).status)
            cube = json.decodeFromString(CubeService(runtime).create(a, CreateCube(id(), "Cube", listOf(SavedCubeEntry(digest, 90)))).body)
        }
        fun pod(three: Boolean = false): String {
            val pod = json.decodeFromString<PodDetail>(pods.create(a, CreatePod(id(), "Surviving pod name", cube.cube.id)).body).id
            for (member in if (three) listOf(b, c) else listOf(b)) {
                assertEquals(200, pods.invite(a, pod, InviteToPod(id(), member.account.accountId)).status)
                assertEquals(200, pods.invitation(member, pod, PodRequest(id()), true).status)
            }
            // Each member receives three automatic one-card packs.
            assertEquals(200, pods.update(a, pod, UpdatePod(id(), settings = DraftSettings(packsPerPlayer = 3, cardsPerPack = 1, sideboardLimit = 1))).status)
            assertEquals(200, pods.start(a, pod, PodRequest(id())).status)
            return pod
        }
        fun detail(who: AccountService.Principal, pod: String) = json.decodeFromString<PodDetail>(pods.read(who, pod).body)
        fun submit(who: AccountService.Principal, pod: String) {
            val detail = detail(who, pod); val attempt = detail.draftAttemptId!!
            val copies = detail.build!!.copies
            assertEquals(200, pods.move(who, pod, MovePodCopy(id(), attempt, copies[1].copyId, PodDeckCategory.SIDE)).status)
            assertEquals(200, pods.move(who, pod, MovePodCopy(id(), attempt, copies[2].copyId, PodDeckCategory.TRASH)).status)
            assertEquals(200, pods.basics(who, pod, SetPodBasics(id(), attempt, PodDeckBasics(8, 8, 8, 8, 8))).status)
            assertEquals(200, pods.submit(who, pod, PodDraftRequest(id(), attempt)).status)
        }
        fun challenge(pod: String, who: AccountService.Principal = a, opponent: AccountService.Principal = b): GameSummary {
            val result = pods.challenge(who, pod, ChallengeFromPod(id(), opponent.account.accountId, "Parallel game", basics))
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
            val scope = if (action is GameAction.Play || action is GameAction.AcceptResult) GameScope(game.id, playId = summary.playId)
                else GameScope(game.id, summary.preparationRound, if (action == GameAction.AcceptInvitation) summary.invitationId else null)
            val command = GameCommand(UUID.randomUUID().toString(), scope, action, stream.streamId, stream.generation)
            assertTrue(games.admit(who, hello, connection, command) is GameReceipt)
            assertTrue(games.executeOne())
            return games.results(who, game.id, listOf(command.requestId)).statuses.single().result!!
        }
    }
    private fun fixture(test: (Fixture) -> Unit) = OwnedPostgres().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        ServiceRuntime(db.settings).use { runtime ->
            runtime.start()
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
            test(Fixture(runtime))
        }
    }

    @Test fun `challenge authorization submitted decks name basics validation idempotency and parallel games`() = fixture { f ->
        val pod = f.pod()
        fun input(opponent: String = f.b.account.accountId) = ChallengeFromPod(id(), opponent, "Game", basics)
        assertEquals(404, f.pods.challenge(f.c, pod, input()).status)
        assertEquals(404, f.pods.challenge(f.a, pod, input(f.c.account.accountId)).status)
        assertEquals(400, f.pods.challenge(f.a, pod, input(f.a.account.accountId)).status)
        assertEquals(409, f.pods.challenge(f.a, pod, input()).status)
        f.submit(f.a, pod)
        assertEquals(409, f.pods.challenge(f.a, pod, input()).status)
        f.submit(f.b, pod)
        assertEquals(400, f.pods.challenge(f.a, pod, input().copy(gameName = " ")).status)
        assertEquals(400, f.pods.challenge(f.a, pod, input().copy(basicDefinitions = basics.drop(1))).status)
        assertEquals(400, f.pods.challenge(f.a, pod, input().copy(basicDefinitions = basics.map { it.copy(digest = "sha256:" + "0".repeat(64)) })).status)
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
        for (player in preparation.players) {
            val deck = player.deck!!
            assertEquals(41L, deck.count(SavedDeckSection.MAINBOARD)); assertEquals(1L, deck.count(SavedDeckSection.SIDEBOARD))
            assertEquals(2, deck.entries.count { it.suppliedBasic == null }) // Trash is absent.
            assertEquals(PodDeckBasics(8, 8, 8, 8, 8), PodGamePreparation.basics(deck))
            assertEquals(5, deck.basicDefinitions.size)
            assertTrue(deck.entries.all { entry -> preparation.definitions.any { it.digest == entry.definition } })
        }
        assertNotEquals(preparation.players[0].deck!!.id, preparation.players[1].deck!!.id)
        val stream = f.stream(f.a, game)
        assertEquals("podDeckFixed", stream.execute(GameAction.SelectDeck(id(), "1")).reason)
        val own = preparation.players.first { it.accountId == f.a.account.accountId }.deck!!
        val other = preparation.players.first { it.accountId == f.b.account.accountId }.deck!!
        assertEquals("preparedDeckChanged", stream.execute(GameAction.SetPodGameBasics(other.id, PodDeckBasics(plains = 40), basics)).reason)
        assertEquals(own, f.preparation(game).players.first { it.accountId == f.a.account.accountId }.deck)
    }

    @Test fun `pod readiness sideboarding basics and accepted games stay independent after destroy`() = fixture { f ->
        val pod = f.pod(); f.submit(f.a, pod); f.submit(f.b, pod)
        val game = f.challenge(pod); val owner = f.stream(f.a, game); val recipient = f.stream(f.b, game)
        recipient.execute(GameAction.AcceptInvitation)
        val deck = f.preparation(game).players.first { it.accountId == f.a.account.accountId }.deck!!
        owner.execute(GameAction.Ready)
        assertEquals("accepted", owner.execute(GameAction.SetPodGameBasics(deck.id, PodDeckBasics(plains = 38), basics)).outcome)
        assertFalse(f.preparation(game).players.first { it.accountId == f.a.account.accountId }.ready)
        assertEquals("mainTooSmall", owner.execute(GameAction.Ready).reason)
        owner.execute(GameAction.SetPodGameBasics(deck.id, PodDeckBasics(plains = 40), basics))
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
        assertEquals(started, f.games.head(f.b, game.id))
        assertEquals(GameLifecycle.LOBBY, f.games.head(f.b, acceptedLobby.id).summary.lifecycle)
        assertEquals("Surviving pod name", f.games.head(f.b, acceptedLobby.id).summary.podName)
        assertEquals(404, f.lobby.head(f.a, fresh.id).status)
        // The accepted but unstarted game's definitions still support editing and starting.
        val remaining = f.stream(f.a, acceptedLobby)
        assertEquals("accepted", remaining.execute(GameAction.Ready).outcome)
        assertEquals("accepted", f.stream(f.b, acceptedLobby).execute(GameAction.Ready).outcome)
        assertEquals(GameLifecycle.ACTIVE, f.games.head(f.a, acceptedLobby.id).summary.lifecycle)
        assertEquals("accepted", owner.execute(GameAction.Play(PlayAction.SuggestResult(SuggestedResult.DRAW))).outcome)
        val suggestion = f.games.head(f.b, game.id).play!!.board.resultSuggestion!!
        assertEquals("accepted", recipient.execute(GameAction.AcceptResult(suggestion.id)).outcome)
        val rematch = f.games.head(f.a, game.id)
        assertEquals(GameLifecycle.LOBBY, rematch.summary.lifecycle)
        assertEquals(PodGameRules(1), rematch.preparation!!.podRules)
        assertEquals(PodDeckBasics(plains = 40), PodGamePreparation.basics(rematch.preparation!!.players.first { it.accountId == f.a.account.accountId }.deck!!))
        assertTrue(rematch.preparation!!.players.all { !it.ready })
    }

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
}
