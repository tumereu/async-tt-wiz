package org.asyncttwiz.server.pods

import java.util.UUID
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.TestPrintings
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.activity.ActivityService
import org.asyncttwiz.server.cubes.CubeService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.game.GameService
import org.asyncttwiz.server.lobby.LobbyService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * DRF-119, DRF-302, DRF-122 (owner, 2026-11-21, problem report MT74XQ): a member submits as soon as their own draft is done (no more
 * picks or cards can reach them), whatever the others still draft, and can then challenge any member, one still drafting
 * too. Every draft mode's "own draft done", the refusals before it, and the notices around it.
 */
class EarlySubmitTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()

    private inner class Fixture(val runtime: ServiceRuntime) {
        private val accounts = AccountService(runtime)
        fun account(name: String): AccountService.Principal {
            accounts.register(name, "$name@example.invalid", "fixture password")
            return accounts.authenticate(accounts.login("$name@example.invalid", "fixture password")!!.token)!!
        }
        val a = account("EarlyA"); val b = account("EarlyB")
        val pods = PodService(runtime); val games = GameService(runtime); val lobby = LobbyService(runtime)
        val activity = ActivityService(runtime)
        val cube: SavedCube = json.decodeFromString(CubeService(runtime).create(a, CreateCube(id(), "Cube",
            listOf(TestPrintings.entry("Ash Pup", 120)))).body)

        /** A started two-member pod of [settings]. */
        fun pod(settings: DraftSettings): String {
            val pod = json.decodeFromString<PodDetail>(pods.create(a, CreatePod(id(), "Early pod", cube.cube.id, settings.mode)).body).id
            assertEquals(200, pods.invite(a, pod, InviteToPod(id(), b.account.accountId)).status)
            assertEquals(200, pods.invitation(b, pod, PodRequest(id()), true).status)
            assertEquals(200, pods.update(a, pod, UpdatePod(id(), settings = settings)).status)
            assertEquals(200, pods.start(a, pod, PodRequest(id())).status)
            return pod
        }
        fun detail(who: AccountService.Principal, pod: String) = json.decodeFromString<PodDetail>(pods.read(who, pod).body)
        fun summary(who: AccountService.Principal, pod: String) =
            json.decodeFromString<PodList>(pods.list(who).body).pods.single { it.id == pod }
        fun mine(who: AccountService.Principal, pod: String) = detail(who, pod).members.single { it.account.accountId == who.account.accountId }
        /** [who]'s deck: their pool in Main and 40 Plains; returns the submit's status and body. */
        fun submit(who: AccountService.Principal, pod: String): Pair<Int, String> {
            val attempt = detail(who, pod).draftAttemptId!!
            assertEquals(200, pods.basics(who, pod, SetPodBasics(id(), attempt, PodDeckBasics(plains = 40))).status)
            return pods.submit(who, pod, SubmitPodDeck(id(), attempt)).let { it.status to it.body }
        }
        fun refused(who: AccountService.Principal, pod: String) {
            val (status, body) = submit(who, pod)
            assertEquals(409, status, body)
            assertEquals("draft_not_complete", json.decodeFromString<PodError>(body).code)
        }
        fun submitted(who: AccountService.Principal, pod: String) {
            val (status, body) = submit(who, pod)
            assertEquals(200, status, body)
            assertTrue(json.decodeFromString<PodDetail>(body).submitted)
        }
        /** [who]'s next move in any mode: the head pack's first cards, the first line of the head grid, the first untaken
         * Rotisserie card, the current Winston pile, or a Winchester pile. */
        fun move(who: AccountService.Principal, pod: String) {
            val detail = detail(who, pod); val attempt = detail.draftAttemptId!!
            val status = when (detail.settings.mode) {
                DraftMode.GRID -> {
                    val grid = detail.grid!!
                    val board = grid.publicView.grids.single { it.id == grid.mine.headGridId }
                    val line = GridLineType.entries.flatMap { type -> List(3) { GridLine(type, it) } }
                        .first { line -> line.positions().any { board.positions[it] != null } }
                    pods.selectGrid(who, pod, SelectPodGrid(id(), attempt, board.id, line.type, line.index)).status
                }
                DraftMode.ROTISSERIE -> {
                    val view = detail.limited!!
                    pods.turn(who, pod, PodTurn(id(), attempt, LimitedAction.TAKE_CARD, view.turn,
                        card = view.board.first { it.pickedBy == null }.copy.id)).status
                }
                DraftMode.WINSTON -> {
                    val view = detail.limited!!
                    pods.turn(who, pod, PodTurn(id(), attempt, LimitedAction.TAKE_PILE, view.turn, pile = view.pile)).status
                }
                DraftMode.WINCHESTER -> {
                    val view = detail.limited!!
                    pods.turn(who, pod, PodTurn(id(), attempt, LimitedAction.TAKE_PILE, view.turn,
                        pile = view.piles.indexOfFirst { it.isNotEmpty() })).status
                }
                else -> {
                    val head = detail.draft!!.head!!
                    val taken = head.pick - 1
                    val keep = detail.settings.keepAt(taken); val burn = detail.settings.burnAt(taken)
                    pods.pick(who, pod, PickPod(id(), attempt, head.id, head.cards.take(keep).map { it.id },
                        if (detail.settings.keepsOnly(head.cards.size, keep.toLong(), taken)) emptyList()
                        else head.cards.drop(keep).take(burn).map { it.id })).status
                }
            }
            assertEquals(200, status)
        }
        /** Whether [who] can move now. */
        fun canMove(who: AccountService.Principal, pod: String) = mine(who, pod).packsWaiting > 0
        fun complete(who: AccountService.Principal, pod: String) = activity.page(who, "0").entries
            .count { it.kind == ActivityKind.POD_DRAFT_COMPLETE && it.gameId == pod }
    }

    private fun fixture(test: (Fixture) -> Unit) = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        ServiceRuntime(db.settings).use { runtime ->
            runtime.start()
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
            test(Fixture(runtime))
        }
    }

    /**
     * Plays [pod] with [first] moving whenever they can and [second] only when [first] can't, until [first]'s own draft is
     * done; checks [first]'s submit is refused before it and the "still drafting" state the server reports.
     */
    private fun draftFirstToTheirEnd(f: Fixture, pod: String, first: AccountService.Principal, second: AccountService.Principal) {
        var guard = 0
        while (f.detail(first, pod).draftWait != null) {
            assertTrue(guard++ < 200, "the draft must progress")
            assertFalse(f.mine(first, pod).building)
            f.refused(first, pod)
            if (f.canMove(first, pod)) f.move(first, pod) else f.move(second, pod)
        }
    }

    // MT74XQ: a two-member Arena pod; Alice finishes her picks while Bob has made none, submits, challenges Bob while he still
    // drafts; Bob can accept only after his own last pick and submit
    @Test fun `an Arena member submits after their own last pick and challenges a member still drafting`() = fixture { f ->
        val pod = f.pod(DraftSettings.arena().copy(packsPerPlayer = 3, cardsPerPack = 2, keep = 1, ignoreRarity = true, reuseCards = true))
        repeat(3) { pick ->
            assertEquals(PodDraftWait.Picks, f.detail(f.a, pod).draftWait)
            f.refused(f.a, pod)
            assertEquals(0, f.complete(f.a, pod))
            f.move(f.a, pod)
            if (pick < 2) assertEquals(PodAttention.YOUR_PICK, f.summary(f.a, pod).attention)
        }
        // Alice's own draft is done; Bob has made no pick
        val done = f.detail(f.a, pod)
        assertEquals(PodPhase.DRAFTING, done.phase)
        assertNull(done.draftWait)
        assertTrue(done.members.single { it.account.accountId == f.a.account.accountId }.building)
        assertEquals(0, done.members.single { it.account.accountId == f.b.account.accountId }.picksMade)
        assertEquals(PodAttention.BUILD, f.summary(f.a, pod).attention)
        // DRF-137: "Draft complete: build your deck" comes with her own draft's end
        assertEquals(1, f.complete(f.a, pod))
        assertEquals(0, f.complete(f.b, pod))
        f.submitted(f.a, pod)
        assertEquals(PodPhase.DRAFTING, f.detail(f.a, pod).phase)
        f.summary(f.a, pod).let { assertEquals(PodAttention.NONE, it.attention); assertTrue(it.submitted) }
        assertEquals(PodDraftWait.Picks, f.detail(f.b, pod).draftWait)
        f.refused(f.b, pod)

        // DRF-122: Alice challenges Bob while he still drafts; he sees it at once and can't accept yet
        val result = f.pods.challenge(f.a, pod, ChallengeFromPod(id(), f.b.account.accountId, "Early game"))
        assertEquals(201, result.status, result.body)
        val game = json.decodeFromString<GameSummary>(result.body)
        assertTrue(game.invitationHeld)
        assertEquals(game.id, json.decodeFromString<GameList>(f.lobby.list(f.b, null, true).body).games.single().id)
        assertTrue(f.activity.page(f.b, "0").entries.single { it.kind == ActivityKind.INVITATION }.held)
        // Bob, still drafting, can't challenge
        assertEquals(409, f.pods.challenge(f.b, pod, ChallengeFromPod(id(), f.a.account.accountId, "Too early")).status)

        repeat(3) { f.move(f.b, pod) }
        assertEquals(PodPhase.DRAFTED, f.detail(f.b, pod).phase)
        assertEquals(1, f.complete(f.a, pod), "the whole draft's end adds no second Draft complete")
        assertEquals(1, f.complete(f.b, pod))
        f.submitted(f.b, pod)
        val released = json.decodeFromString<GameList>(f.lobby.list(f.b, null, true).body).games.single()
        assertEquals(game.id, released.id); assertFalse(released.invitationHeld)
    }

    // DRF-119: Booster with a discard threshold: the first member is done after their two picks while the other still picks
    @Test fun `a Booster member submits once no pack can reach them`() = fixture { f ->
        val pod = f.pod(DraftSettings(packsPerPlayer = 1, cardsPerPack = 3, discardRemainingAt = 1))
        draftFirstToTheirEnd(f, pod, f.a, f.b)
        assertEquals(PodPhase.DRAFTING, f.detail(f.a, pod).phase)
        assertEquals(PodDraftWait.Picks, f.detail(f.b, pod).draftWait)
        f.submitted(f.a, pod)
        f.refused(f.b, pod)
        f.move(f.b, pod)
        assertEquals(PodPhase.DRAFTED, f.detail(f.b, pod).phase)
        f.submitted(f.b, pod)
    }

    // DRF-119: a pack's last card comes without a pick: after their last pick the member waits for it ("cards")
    @Test fun `a Booster member whose last card is still coming waits for it`() = fixture { f ->
        val pod = f.pod(DraftSettings(packsPerPlayer = 1, cardsPerPack = 2))
        f.move(f.a, pod)
        assertEquals(PodDraftWait.Cards, f.detail(f.a, pod).draftWait)
        f.refused(f.a, pod)
        f.move(f.b, pod)
        assertEquals(PodPhase.DRAFTED, f.detail(f.a, pod).phase)
        assertNull(f.detail(f.a, pod).draftWait)
        f.submitted(f.a, pod)
    }

    // DRF-119: Rotisserie's snake (a b b a a b …): a member is done after their last turn while the other still has one
    @Test fun `a Rotisserie member submits after their last turn`() = fixture { f ->
        val pod = f.pod(DraftSettings.rotisserie().copy(cardsPerPlayer = 5))
        val seating = listOf(f.a, f.b).sortedBy { if (f.canMove(it, pod)) 0 else 1 }
        // Five picks each for two: the first seat's last turn is the ninth, the second's the tenth
        draftFirstToTheirEnd(f, pod, seating[0], seating[1])
        assertEquals(PodPhase.DRAFTING, f.detail(seating[0], pod).phase)
        f.submitted(seating[0], pod)
        f.refused(seating[1], pod)
    }

    // DRF-119: Grid: a member is done after their last turn on the last grid while the other still has one
    @Test fun `a Grid member submits after their last turn`() = fixture { f ->
        val pod = f.pod(DraftSettings.grid().copy(grids = 2))
        // Grid 2's turns start with the second seat: they are done first
        val seating = listOf(f.a, f.b).sortedBy { if (f.canMove(it, pod)) 0 else 1 }
        draftFirstToTheirEnd(f, pod, seating[1], seating[0])
        assertEquals(PodPhase.DRAFTING, f.detail(seating[1], pod).phase)
        f.submitted(seating[1], pod)
        f.refused(seating[0], pod)
    }

    // DRF-119: Winston and Winchester: a pile can come to anyone until the last one is taken, so Submit opens only then
    @Test fun `Winston and Winchester members submit only when the last pile is taken`() = fixture { f ->
        for (settings in listOf(DraftSettings.winston().copy(packsPerPlayer = 1, cardsPerPack = 10),
                DraftSettings.winchester().copy(packsPerPlayer = 1, cardsPerPack = 10))) {
            val pod = f.pod(settings)
            var guard = 0
            while (f.detail(f.a, pod).phase == PodPhase.DRAFTING) {
                assertTrue(guard++ < 200, "the draft must progress")
                for (who in listOf(f.a, f.b)) {
                    assertEquals(PodDraftWait.Piles, f.detail(who, pod).draftWait, "${settings.mode}")
                    assertFalse(f.mine(who, pod).building)
                    f.refused(who, pod)
                }
                f.move(listOf(f.a, f.b).first { f.canMove(it, pod) }, pod)
            }
            for (who in listOf(f.a, f.b)) {
                assertNull(f.detail(who, pod).draftWait)
                assertEquals(1, f.complete(who, pod))
                f.submitted(who, pod)
            }
        }
    }

    // DRF-119, DRF-122: the creator's Cancel draft after an early submit withdraws the open challenge and discards the deck
    @Test fun `cancelling the draft after an early submit withdraws its challenges`() = fixture { f ->
        val pod = f.pod(DraftSettings.arena().copy(packsPerPlayer = 1, cardsPerPack = 2, keep = 1, ignoreRarity = true, reuseCards = true))
        f.move(f.a, pod)
        f.submitted(f.a, pod)
        val game = json.decodeFromString<GameSummary>(f.pods.challenge(f.a, pod, ChallengeFromPod(id(), f.b.account.accountId, "Early")).body)
        assertEquals(200, f.pods.cancel(f.a, pod, PodDraftRequest(id(), f.detail(f.a, pod).draftAttemptId!!)).status)
        assertEquals(PodPhase.SETUP, f.detail(f.a, pod).phase)
        assertFalse(f.detail(f.a, pod).submitted)
        assertEquals(404, f.lobby.head(f.a, game.id).status)
        assertTrue(json.decodeFromString<GameList>(f.lobby.list(f.b, null, true).body).games.isEmpty())
    }
}
