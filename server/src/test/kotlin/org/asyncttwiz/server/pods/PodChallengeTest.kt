package org.asyncttwiz.server.pods

import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.cubes.CubeService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.game.GameService
import org.asyncttwiz.server.lobby.LobbyService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PodChallengeTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()
    private val basics = BasicLand.entries.zip(listOf("W", "U", "B", "R", "G")).map { (land, mana) ->
        val card = CardDefinition(land.cardName, listOf(DefinitionFace(id(), land.cardName,
            typeLine = "Basic Land — ${land.cardName}", rulesText = "({T}: Add {$mana}.)")), provider = "mtgjson")
        SubmittedDefinition(DeckJson.digest(card), card)
    }
    private inner class Fixture(val runtime: ServiceRuntime, uniqueCards: Boolean) {
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
            val cards = if (uniqueCards) (1..9).map { CardDefinition("Unique pool card $it", listOf(DefinitionFace(id(), "Unique pool card $it"))) }
                else listOf(card)
            val definitions = cards.map { SubmittedDefinition(DeckJson.digest(it), it) }
            assertEquals(200, decks.submit(a, SubmitDefinitions(id(), definitions)).status)
            cube = json.decodeFromString(CubeService(runtime).create(a, CreateCube(id(), "Cube",
                definitions.map { SavedCubeEntry(it.digest, if (uniqueCards) 1 else 90) })).body)
        }
        fun pod(three: Boolean = false, sideboardLimit: Int? = 1): String {
            val pod = json.decodeFromString<PodDetail>(pods.create(a, CreatePod(id(), "Surviving pod name", cube.cube.id)).body).id
            for (member in if (three) listOf(b, c) else listOf(b)) {
                assertEquals(200, pods.invite(a, pod, InviteToPod(id(), member.account.accountId)).status)
                assertEquals(200, pods.invitation(member, pod, PodRequest(id()), true).status)
            }
            // Each member receives three automatic one-card packs.
            assertEquals(200, pods.update(a, pod, UpdatePod(id(), settings = DraftSettings(packsPerPlayer = 3, cardsPerPack = 1, sideboardLimit = sideboardLimit))).status)
            assertEquals(200, pods.start(a, pod, PodRequest(id())).status)
            return pod
        }
        fun detail(who: AccountService.Principal, pod: String) = json.decodeFromString<PodDetail>(pods.read(who, pod).body)
        fun submit(who: AccountService.Principal, pod: String, counts: PodDeckBasics = PodDeckBasics(8, 8, 8, 8, 8)) {
            val detail = detail(who, pod); val attempt = detail.draftAttemptId!!
            val copies = detail.build!!.copies
            assertEquals(200, pods.move(who, pod, MovePodCopy(id(), attempt, copies[1].copyId, PodDeckCategory.SIDE)).status)
            assertEquals(200, pods.move(who, pod, MovePodCopy(id(), attempt, copies[2].copyId, PodDeckCategory.TRASH)).status)
            assertEquals(200, pods.basics(who, pod, SetPodBasics(id(), attempt, counts)).status)
            assertEquals(200, pods.submit(who, pod, SubmitPodDeck(id(), attempt)).status)
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
                else GameScope(game.id, summary.preparationRound,
                    if (action in listOf(GameAction.AcceptInvitation, GameAction.DeclineInvitation, GameAction.Reinvite)) summary.invitationId else null)
            val command = GameCommand(UUID.randomUUID().toString(), scope, action, stream.streamId, stream.generation)
            assertTrue(games.admit(who, hello, connection, command) is GameReceipt)
            assertTrue(games.executeOne())
            return games.results(who, game.id, listOf(command.requestId)).statuses.single().result!!
        }
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
        val deck = decks.getValue(f.a.account.accountId)
        val stream = f.stream(f.a, game)
        assertEquals("accepted", stream.execute(GameAction.SetPodGameBasics(deck.id, PodDeckBasics(forest = 3), basics, SavedDeckSection.SIDEBOARD)).outcome)
        val edited = f.preparation(game).players.single { it.accountId == f.a.account.accountId }.deck!!
        assertEquals(PodDeckBasics(forest = 3), PodGamePreparation.basics(edited, SavedDeckSection.SIDEBOARD))
        assertEquals(PodDeckBasics(8, 8, 8, 8, 8), PodGamePreparation.basics(edited))
    }

    // DRF-123, DRF-140
    @Test fun `challenge authorization submitted decks name basics validation idempotency and parallel games`() = fixture { f ->
        val pod = f.pod()
        fun input(opponent: String = f.b.account.accountId) = ChallengeFromPod(id(), opponent, "Game", basics)
        assertEquals(404, f.pods.challenge(f.c, pod, input()).status)
        assertEquals(404, f.pods.challenge(f.a, pod, input(f.c.account.accountId)).status)
        assertEquals(400, f.pods.challenge(f.a, pod, input(f.a.account.accountId)).status)
        assertEquals(409, f.pods.challenge(f.a, pod, input()).status)
        f.submit(f.a, pod)
        // The challenger must have submitted; the opponent may still be building.
        assertEquals(409, f.pods.challenge(f.b, pod, input(f.a.account.accountId)).status)
        f.submit(f.b, pod)
        assertEquals(400, f.pods.challenge(f.a, pod, input().copy(gameName = " ")).status)
        assertEquals(400, f.pods.challenge(f.a, pod, input().copy(basicDefinitions = basics.drop(1))).status)
        assertEquals(400, f.pods.challenge(f.a, pod, input().copy(basicDefinitions = basics.map { it.copy(digest = "sha256:" + "0".repeat(64)) })).status)
        val plains = basics.first().definition
        val forged = plains.copy(faces = listOf(plains.faces.single().copy(typeLine = "Basic Land Creature — Swamp Horror",
            power = "20", toughness = "20", rulesText = "Draw twenty cards.")))
        val rejected = f.pods.challenge(f.a, pod, input().copy(basicDefinitions =
            listOf(SubmittedDefinition(DeckJson.digest(forged), forged)) + basics.drop(1)))
        assertEquals(400, rejected.status)
        assertEquals("invalid_basic_definitions", json.decodeFromString<PodError>(rejected.body).code)
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
            assertEquals(2, deck.entries.count { it.suppliedBasic == null }) // Trash is absent.
            assertEquals(PodDeckBasics(8, 8, 8, 8, 8), PodGamePreparation.basics(deck))
            assertEquals(5, deck.basicDefinitions.size)
            assertTrue(deck.entries.all { entry -> accepted.definitions.any { it.digest == entry.definition } })
        }
        assertNotEquals(accepted.players[0].deck!!.id, accepted.players[1].deck!!.id)
        val stream = f.stream(f.a, game)
        assertEquals("podDeckFixed", stream.execute(GameAction.SelectDeck(id(), "1")).reason)
        val own = preparation.players.first { it.accountId == f.a.account.accountId }.deck!!
        val other = accepted.players.first { it.accountId == f.b.account.accountId }.deck!!
        assertEquals("preparedDeckChanged", stream.execute(GameAction.SetPodGameBasics(other.id, PodDeckBasics(plains = 40), basics)).reason)
        assertEquals(own, f.preparation(game).players.first { it.accountId == f.a.account.accountId }.deck)
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

    // DRF-125, LOB-042
    @Test fun `pod deck names are private head metadata never shared preparation or replay`() = fixture { f ->
        val pod = f.pod()
        val attempt = f.detail(f.a, pod).draftAttemptId!!
        val aName = "Private white strategy"
        val bName = "Private green strategy"
        assertEquals(200, f.pods.deckName(f.a, pod, SetPodDeckName(id(), attempt, aName)).status)
        assertEquals(200, f.pods.deckName(f.b, pod, SetPodDeckName(id(), attempt, bName)).status)
        f.submit(f.a, pod); f.submit(f.b, pod)
        val game = f.challenge(pod)
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
        owner.execute(GameAction.Play(PlayAction.SuggestResult(SuggestedResult.WIN)))
        val suggestion = f.games.head(f.a, first.id).play!!.board.resultSuggestion!!
        assertEquals("accepted", recipient.execute(GameAction.AcceptResult(suggestion.id)).outcome)
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
        val submitted = detail.deck!!.copies.filter { it.category != PodDeckCategory.TRASH }
        val privateDigests = submitted.map { pool.getValue(it.copyId).definition }.toSet()
        val trash = pool.getValue(detail.build!!.copies.single { it.category == PodDeckCategory.TRASH }.copyId).definition
        fun privatePreparation(preparation: LobbyPreparation) {
            assertNull(preparation.players.single { it.accountId == f.b.account.accountId }.deck)
            assertTrue(preparation.definitions.none { it.digest in privateDigests || it.digest == trash })
        }
        fun privatePayloads(game: GameSummary) {
            val head = f.games.head(f.a, game.id)
            privatePreparation(head.preparation!!)
            privatePreparation(f.games.subscriptionHead(f.a, game.id).preparation!!)
            val revisions = f.games.revisions(f.a, game.id, "0").revisions
            revisions.mapNotNull { it.preparation }.forEach(::privatePreparation)
            val payloads = listOf(GameWire.encode(head), f.lobby.head(f.a, game.id).body) + revisions.map(GameWire::encode)
            for (definition in detail.definitions.filter { it.digest in privateDigests || it.digest == trash }) {
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
            assertEquals(if (copy.category == PodDeckCategory.MAIN) SavedDeckSection.MAINBOARD else SavedDeckSection.SIDEBOARD, entry.section)
        }
        assertTrue(privateDigests.all { digest -> preparation.definitions.any { it.digest == digest } })
        assertTrue(preparation.definitions.none { it.digest == trash })
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

    // DRF-122
    @Test fun `challenge before the opponent submits is held until they submit then plays through to a started game`() = fixture { f ->
        val pod = f.pod(sideboardLimit = 3)
        val activity = org.asyncttwiz.server.activity.ActivityService(f.runtime)
        fun invitations(who: AccountService.Principal) = activity.page(who, "0").entries.filter { it.kind == ActivityKind.INVITATION }
        f.submit(f.a, pod)
        val game = f.challenge(pod)
        assertTrue(game.invitationHeld)
        assertFalse(json.encodeToString(game.copy(invitationHeld = false)).contains("invitationHeld")) // Older clients never see it.
        // The recipient sees the held challenge among their invitations at once but cannot accept it yet,
        // and is notified only when their deck releases it.
        val incoming = json.decodeFromString<GameList>(f.lobby.list(f.b, null, true).body).games.single()
        assertEquals(game.id, incoming.id); assertTrue(incoming.invitationHeld)
        assertTrue(json.decodeFromString<GameList>(f.lobby.list(f.b, null, false).body).games.isEmpty())
        assertEquals(404, f.lobby.head(f.b, game.id).status)
        val early = f.stream(f.b, game)
        assertTrue(f.games.head(f.b, game.id).summary.invitationHeld)
        assertNull(f.games.head(f.b, game.id).preparation)
        assertEquals("inapplicable", early.execute(GameAction.AcceptInvitation).reason)
        assertTrue(invitations(f.b).isEmpty())
        // The challenger sees it, prepares and gets ready meanwhile.
        assertTrue(json.decodeFromString<GameList>(f.lobby.list(f.a, null, false).body).games.single().invitationHeld)
        val owner = f.stream(f.a, game)
        val deck = f.preparation(game).players.single { it.accountId == f.a.account.accountId }.deck!!
        val card = deck.entries.first { it.section == SavedDeckSection.MAINBOARD && it.suppliedBasic == null }
        assertEquals("accepted", owner.execute(GameAction.TransferPreparedCard(deck.id, deck.card(card, 0).id, card.firstIndex, card.section)).outcome)
        assertEquals("accepted", owner.execute(GameAction.Ready).outcome)
        assertEquals(GameLifecycle.LOBBY, f.games.head(f.a, game.id).summary.lifecycle)

        f.submit(f.b, pod)
        val released = json.decodeFromString<GameList>(f.lobby.list(f.b, null, true).body).games.single()
        assertEquals(game.id, released.id); assertFalse(released.invitationHeld)
        assertFalse(json.decodeFromString<GameSummary>(f.lobby.head(f.a, game.id).body).invitationHeld)
        assertEquals(game.id, invitations(f.b).single().gameId)
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

    // DRF-122
    @Test fun `a held challenge can be declined and reinvited before submitting and notifies only on release`() = fixture { f ->
        val pod = f.pod()
        val activity = org.asyncttwiz.server.activity.ActivityService(f.runtime)
        fun invitations(who: AccountService.Principal) = activity.page(who, "0").entries.filter { it.kind == ActivityKind.INVITATION }
        f.submit(f.a, pod)
        val game = f.challenge(pod)
        assertEquals("accepted", f.stream(f.b, game).execute(GameAction.DeclineInvitation).outcome)
        assertEquals(InvitationState.DECLINED, f.games.head(f.a, game.id).summary.invitationState)
        assertTrue(json.decodeFromString<GameList>(f.lobby.list(f.b, null, true).body).games.isEmpty())
        // Reinviting while still held stays silent until the deck is in.
        assertEquals("accepted", f.stream(f.a, game).execute(GameAction.Reinvite).outcome)
        assertTrue(json.decodeFromString<GameList>(f.lobby.list(f.b, null, true).body).games.single().invitationHeld)
        assertTrue(invitations(f.b).isEmpty())
        f.submit(f.b, pod)
        assertFalse(json.decodeFromString<GameList>(f.lobby.list(f.b, null, true).body).games.single().invitationHeld)
        assertEquals(game.id, invitations(f.b).single().gameId)
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
}
