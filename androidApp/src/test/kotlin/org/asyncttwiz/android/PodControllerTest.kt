package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class PodControllerTest {
    private val self = AccountIdentity(UUID.randomUUID().toString(), "Alice")
    private val other = AccountIdentity(UUID.randomUUID().toString(), "Bob")
    private val id = UUID.randomUUID().toString()
    private val attempt = UUID.randomUUID().toString()
    private val cube = CubeSummary(UUID.randomUUID().toString(), "Cube", "1", "2026-10-26T00:00:00Z", "2026-10-26T00:00:00Z", 540)
    private val card = CardDefinition("Card", listOf(DefinitionFace(UUID.randomUUID().toString(), "Card")))
    private val definition = SubmittedDefinition(DeckJson.digest(card), card)
    private val copy = DraftCopy("copy", definition.digest)
    private val head = DraftPrivatePack("pack", 1, listOf(copy, copy.copy(id = "burn")))
    private fun detail(phase: PodPhase = PodPhase.DRAFTING) = PodDetail(id, "Pod", phase, self,
        DraftSettings(), PodSource("Cube", 540), DraftSupply(90, 540), listOf(PodMember(self, 2), PodMember(other)), emptyList(),
        if (phase == PodPhase.SETUP) null else attempt,
        if (phase == PodPhase.SETUP) null else DraftPrivateView(self.accountId, head, 1, listOf(copy)),
        if (phase == PodPhase.SETUP) null else PodDeckBuild(listOf(PodDeckCopy(copy.id))),
        definitions = listOf(definition), updatedAt = "2026-10-26T00:00:00Z")
    private fun summary(detail: PodDetail, attention: PodAttention = PodAttention.YOUR_PICK) = PodSummary(detail.id,
        detail.name, detail.phase, detail.creator, detail.members.map { it.account }, attention, 2, detail.updatedAt)

    private class Worker : AbstractExecutorService() {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun one() { tasks.removeFirst().run() }
        fun drain() { while (tasks.isNotEmpty()) one() }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
    }
    private inner class Api : Pods {
        var saved = detail()
        var listed: PodList? = null
        val calls = mutableListOf<Pair<String, Any>>()
        var reads = 0
        val readIds = mutableListOf<String>()
        var secondPod: PodDetail? = null
        var missing = false
        var failMoves = false
        var afterRead: () -> Unit = {}
        override suspend fun list(token: String) = listed ?: PodList(if (missing) emptyList() else listOf(summary(saved)), emptyList())
        override suspend fun detail(token: String, id: String): PodDetail {
            reads++; readIds += id; if (missing) throw PodFailure("not_found"); afterRead()
            return secondPod?.takeIf { it.id == id } ?: saved
        }
        override suspend fun create(token: String, input: CreatePod): PodDetail { calls += "create" to input; saved = detail(PodPhase.SETUP).copy(name = input.name); return saved }
        override suspend fun update(token: String, id: String, input: UpdatePod): PodDetail {
            calls += "update" to input; saved = saved.copy(name = input.name ?: saved.name, settings = input.settings ?: saved.settings); return saved
        }
        override suspend fun invite(token: String, id: String, input: InviteToPod): PodDetail { calls += "invite" to input; return saved }
        override suspend fun accept(token: String, id: String, input: PodRequest): PodDetail { calls += "accept" to input; return saved }
        override suspend fun decline(token: String, id: String, input: PodRequest): PodAcknowledged { calls += "decline" to input; return PodAcknowledged(id) }
        override suspend fun start(token: String, id: String, input: PodRequest): PodDetail { calls += "start" to input; saved = detail(); return saved }
        override suspend fun pick(token: String, id: String, input: PickPod): PodDetail {
            calls += "pick" to input
            saved = saved.copy(draft = saved.draft!!.copy(head = head.copy(id = "next"), packsBehind = 0))
            return saved
        }
        override suspend fun move(token: String, id: String, input: MovePodCopy): PodDetail {
            calls += "move" to input
            if (failMoves) throw PodFailure("network")
            saved = saved.copy(build = saved.build!!.copy(copies = saved.build!!.copies.map { if (it.copyId == input.copyId) it.copy(category = input.category) else it }))
            return saved
        }
        override suspend fun basics(token: String, id: String, input: SetPodBasics): PodDetail {
            calls += "basics" to input; saved = saved.copy(build = saved.build!!.copy(basics = input.basics)); return saved
        }
        override suspend fun submit(token: String, id: String, input: PodDraftRequest): PodDetail { calls += "submit" to input; saved = saved.copy(submitted = true); return saved }
        override suspend fun cancel(token: String, id: String, input: PodDraftRequest): PodDetail { calls += "cancel" to input; saved = detail(PodPhase.SETUP); return saved }
        override suspend fun leave(token: String, id: String, input: PodRequest): PodAcknowledged { calls += "leave" to input; missing = true; return PodAcknowledged(id) }
        override suspend fun destroy(token: String, id: String, input: PodRequest): PodAcknowledged { calls += "destroy" to input; missing = true; return PodAcknowledged(id) }
        override fun close() = Unit
    }
    private fun cubes(sources: () -> List<CubeSummary> = { listOf(cube) }) = object : Cubes {
        override suspend fun listCubes(token: String, after: String?) = CubeList(sources())
        override suspend fun submit(token: String, value: SubmitDefinitions): DefinitionsAccepted = error("unused")
        override suspend fun create(token: String, value: CreateCube): SavedCube = error("unused")
        override suspend fun readCube(token: String, id: String): SavedCube = error("unused")
        override suspend fun cubeDefinition(token: String, id: String, digest: String): SubmittedDefinition = error("unused")
        override suspend fun cubeDefinitions(token: String, id: String, value: ReadCubeDefinitions): SubmittedDefinitions = error("unused")
        override suspend fun update(token: String, id: String, value: UpdateCube): SavedCube = error("unused")
        override suspend fun delete(token: String, id: String, value: DeleteCube): CubeDeleted = error("unused")
        override fun close() = Unit
    }
    private inner class Fixture(phase: PodPhase = PodPhase.DRAFTING) : AutoCloseable {
        val api = Api().also { it.saved = detail(phase) }
        val worker = Worker()
        val posts = ArrayDeque<() -> Unit>()
        var token: String? = "token"
        var sources = listOf(cube)
        val controller = PodController(api, cubes { sources }, { token }, worker, { posts.add(it) }, { "Fantasy name" }, {})
        fun deliver() {
            while (worker.tasks.isNotEmpty() || posts.isNotEmpty()) {
                worker.drain(); while (posts.isNotEmpty()) posts.removeFirst()()
            }
        }
        fun open() { controller.open(id); deliver() }
        override fun close() { controller.close(); deliver() }
    }

    @Test fun listKeepsServerOrderingAndPrivateDetailMapsDefinitionsAndStatus() = Fixture().use { f ->
        val first = summary(f.api.saved).copy(id = "first")
        val second = summary(f.api.saved, PodAttention.NONE).copy(id = "second")
        f.api.listed = PodList(listOf(first, second), listOf(PodInvitation(id, "Invite", other, "now")))
        f.controller.refresh(); f.deliver()
        assertEquals(listOf(first, second), f.controller.state.pods)
        assertEquals("Invite", f.controller.state.invitations.single().name)
        assertEquals("Your pick · 2 packs", podStatus(first))
        f.open()
        assertEquals(definition, f.controller.state.detail!!.definitions.single())
        assertEquals(PodTab.DRAFT, f.controller.state.tab)
        assertEquals("picking · 2 packs", podMemberStatus(f.api.saved.members.first(), PodPhase.DRAFTING))
    }

    @Test fun rapidRelativeBasicTapsAllCountBeforeAnyResponse() = Fixture().use { f ->
        f.open()
        repeat(5) { f.controller.adjustBasic(0, 1) }
        f.controller.adjustBasic(0, -1)
        assertEquals(4, f.controller.state.detail!!.build!!.basics.plains)
        f.deliver()
        assertEquals(listOf(1, 2, 3, 4, 5, 4), f.api.calls.filter { it.first == "basics" }.map { (it.second as SetPodBasics).basics.plains })
    }

    @Test fun buildMovesAndBasicsPredictImmediatelyAndSendInOrderWithoutFlicker() = Fixture().use { f ->
        f.open()
        f.controller.move("copy", PodDeckCategory.SIDE)
        f.controller.move("copy", PodDeckCategory.TRASH)
        f.controller.basics(PodDeckBasics(plains = 20))
        assertEquals(PodDeckCategory.TRASH, f.controller.state.detail!!.build!!.copies.single().category)
        assertEquals(20, f.controller.state.detail!!.build!!.basics.plains)
        assertTrue(f.api.calls.isEmpty())
        f.worker.one(); f.posts.removeFirst()()
        assertEquals(PodDeckCategory.TRASH, f.controller.state.detail!!.build!!.copies.single().category)
        assertEquals(20, f.controller.state.detail!!.build!!.basics.plains)
        f.deliver()
        assertEquals(listOf("move", "move", "basics"), f.api.calls.map { it.first })
        assertEquals(f.api.saved.build, f.controller.state.detail!!.build)
        assertEquals(4, f.api.reads) // open and every mutation
        assertEquals(0, f.controller.state.pending)
    }

    @Test fun failedWriteBlocksLaterSendsAndRetriesTheOriginalIdentity() = Fixture().use { f ->
        f.open(); f.api.failMoves = true
        f.controller.move("copy", PodDeckCategory.SIDE)
        f.controller.basics(PodDeckBasics(island = 12))
        f.deliver()
        assertTrue(f.controller.state.uncertain)
        assertEquals(listOf("move", "move", "move"), f.api.calls.map { it.first })
        assertEquals(1, f.api.calls.map { (it.second as MovePodCopy).requestId }.distinct().size)
        assertEquals(12, f.controller.state.detail!!.build!!.basics.island)
        f.api.failMoves = false; f.controller.retry(); f.deliver()
        assertEquals(listOf("move", "move", "move", "move", "basics"), f.api.calls.map { it.first })
        assertFalse(f.controller.state.uncertain)
        assertEquals(f.api.saved.build, f.controller.state.detail!!.build)
    }

    @Test fun pickDoesNotPredictAndOnlyAdvancesAfterAcceptance() = Fixture().use { f ->
        f.open()
        f.controller.pick(listOf("copy"), emptyList()); f.controller.pick(listOf("copy"), emptyList())
        assertEquals("pack", f.controller.state.detail!!.draft!!.head!!.id)
        assertTrue(f.controller.state.picking)
        f.worker.one()
        assertEquals("pack", f.controller.state.detail!!.draft!!.head!!.id)
        f.posts.removeFirst()()
        assertEquals("next", f.controller.state.detail!!.draft!!.head!!.id)
        val request = f.api.calls.single().second as PickPod
        assertEquals(attempt, request.draftAttemptId); assertEquals("pack", request.packId)
        assertEquals(listOf("copy"), request.keep)
        assertFalse(f.controller.state.picking)
    }

    @Test fun navigationRefreshIsNotLostBehindQueuedWritesToPreviousPod() = Fixture().use { f ->
        f.open()
        val next = UUID.randomUUID().toString()
        f.api.secondPod = detail().copy(id = next)
        f.controller.refresh() // an active read is ahead of the edit
        f.controller.move("copy", PodDeckCategory.SIDE)
        f.controller.open(next)
        f.deliver()
        assertEquals(next, f.controller.state.selected)
        assertEquals(next, f.controller.state.detail!!.id)
        assertEquals(next, f.api.readIds.last())
        assertEquals(1, f.api.calls.size)
    }

    @Test fun freshAwardsJoinMainWithoutLosingQueuedBuildIntents() = Fixture().use { f ->
        f.open()
        f.controller.move("copy", PodDeckCategory.TRASH)
        f.api.saved = f.api.saved.copy(build = PodDeckRules.withAwards(f.api.saved.build!!, listOf("copy", "new-award")))
        f.deliver()
        val build = f.controller.state.detail!!.build!!
        assertEquals(PodDeckCategory.TRASH, build.copies.first { it.copyId == "copy" }.category)
        assertEquals(PodDeckCategory.MAIN, build.copies.first { it.copyId == "new-award" }.category)
    }

    @Test fun setupSendsLatestFullSettingsAndRetainsNewerUnsentEdits() = Fixture(PodPhase.SETUP).use { f ->
        f.open()
        f.controller.editSetup(name = "Changed", settings = DraftSettings.glimpse())
        assertTrue(f.api.calls.isEmpty())
        f.controller.flushSetup()
        f.controller.editSetup(name = "Newer")
        f.deliver()
        val request = f.api.calls.single().second as UpdatePod
        assertEquals("Changed", request.name); assertEquals(DraftSettings.glimpse(), request.settings)
        assertEquals("Newer", f.controller.state.setup!!.name)
        f.controller.flushSetup(); f.deliver()
        assertNull(f.controller.state.setup)
        assertEquals("Newer", f.controller.state.detail!!.name)
    }

    @Test fun createUsesSuggestedNameAndPreselectedCubeAndAcceptDeclineUseTheirRoutes() = Fixture(PodPhase.SETUP).use { f ->
        f.controller.newPod(cube.id); f.deliver()
        assertEquals(cube.id, f.controller.state.creating!!.cubeId)
        assertEquals(listOf(cube), f.controller.state.cubes)
        f.controller.newMode(DraftMode.GLIMPSE); f.controller.create(); f.deliver()
        assertEquals(CreatePod((f.api.calls.single().second as CreatePod).requestId, "Fantasy name", cube.id, DraftMode.GLIMPSE), f.api.calls.single().second)
        assertEquals(id, f.controller.state.selected)
        f.controller.back(); f.deliver()
        f.controller.respond(id, false); f.deliver()
        f.controller.respond(id, true); f.deliver()
        assertEquals(listOf("create", "decline", "accept"), f.api.calls.map { it.first })
        assertEquals(id, f.controller.state.selected)
    }

    @Test fun startInviteAndConfirmedLifecycleActionsHaveRequestsAndRefresh() {
        Fixture(PodPhase.SETUP).use { f ->
            f.open(); assertTrue(podCanStart(f.controller.state.detail!!))
            f.controller.invite(other); f.deliver(); f.controller.start(); f.deliver()
            assertEquals(listOf("invite", "start"), f.api.calls.map { it.first })
            assertEquals(PodPhase.DRAFTING, f.controller.state.detail!!.phase)
        }
        for (action in PodConfirmation.entries) Fixture(if (action == PodConfirmation.SUBMIT) PodPhase.DRAFTED else PodPhase.DRAFTING).use { f ->
            f.open(); f.controller.requestConfirmation(action)
            assertTrue(f.api.calls.isEmpty())
            val text = podConfirmationText(action, f.api.saved)
            if (action == PodConfirmation.LEAVE) assertEquals("This cancels the draft for all 2 players and discards all picks.", text)
            f.controller.dismissConfirmation(); assertNull(f.controller.state.confirmation)
            f.controller.confirm(); assertTrue(f.api.calls.isEmpty())
            f.controller.requestConfirmation(action); f.controller.confirm(); f.deliver()
            val expected = when (action) { PodConfirmation.DESTROY -> "destroy"; PodConfirmation.LEAVE -> "leave"; PodConfirmation.CANCEL -> "cancel"; PodConfirmation.SUBMIT -> "submit" }
            assertEquals(expected, f.api.calls.single().first)
            val input = f.api.calls.single().second
            if (input is PodDraftRequest) { assertEquals(attempt, input.draftAttemptId); assertTrue(DeckValidation.uuid(input.requestId)) }
            else assertTrue(DeckValidation.uuid((input as PodRequest).requestId))
            assertNull(f.controller.state.confirmation)
            if (action in listOf(PodConfirmation.DESTROY, PodConfirmation.LEAVE)) assertNull(f.controller.state.selected)
            if (action == PodConfirmation.SUBMIT) { assertTrue(f.controller.state.detail!!.submitted); f.controller.move("copy", PodDeckCategory.SIDE); assertEquals(1, f.api.calls.size) }
        }
    }

    @Test fun podHintsRefreshDetailAndListAndMissingPodFallsBackToList() = Fixture().use { f ->
        f.open(); f.api.saved = f.api.saved.copy(name = "Other device")
        f.controller.hint(ActivityHint("1", ActivityKind.GAME, id, attempt)); f.deliver()
        assertEquals(1, f.api.reads)
        f.controller.hint(ActivityHint("2", ActivityKind.POD_YOUR_PICK, id, attempt)); f.deliver()
        assertEquals("Other device", f.controller.state.detail!!.name)
        assertEquals("Other device", f.controller.state.pods.single().name)
        f.api.missing = true
        f.controller.hint(ActivityHint("3", ActivityKind.POD_DESTROYED, id, attempt)); f.deliver()
        assertNull(f.controller.state.selected); assertEquals("Pod destroyed", f.controller.state.notice)
    }

    @Test fun logoutFencesLatePrivateDetailAndStopsSubsequentCalls() = Fixture().use { f ->
        f.api.afterRead = { f.controller.clear(); f.token = null }
        f.controller.open(id); f.deliver()
        assertNull(f.controller.state.detail); assertTrue(f.controller.state.pods.isEmpty())
        assertFalse(f.controller.state.loading)
    }

    @Test fun rejectedCredentialBeforeFailureCompletionDoesNotLeavePodLoadingAfterSignIn() = Fixture().use { f ->
        f.api.afterRead = { f.token = null; throw PodFailure("authentication_required") }
        f.controller.open(id); f.deliver()
        assertFalse(f.controller.state.loading)
        assertNull(f.controller.state.detail)
        f.api.afterRead = {}; f.token = "new-token"
        f.controller.shown(); f.deliver()
        assertEquals(id, f.controller.state.detail?.id)
        assertFalse(f.controller.state.loading)
    }

    @Test fun credentialChangeBeforeSuccessfulCompletionAlsoReleasesFlight() = Fixture().use { f ->
        f.controller.open(id); f.worker.drain()
        f.token = "new-token"
        f.posts.removeFirst()()
        assertFalse(f.controller.state.loading)
        assertNull(f.controller.state.detail)
        f.controller.refresh(); f.deliver()
        assertEquals(id, f.controller.state.detail?.id)
    }

    @Test fun rejectedWriteRetainsItsIdentityAndCanBeRetriedAfterSigningBackIn() = Fixture().use { f ->
        f.open()
        f.api.afterRead = { f.token = null; throw PodFailure("authentication_required") }
        f.controller.move("copy", PodDeckCategory.SIDE); f.deliver()
        assertFalse(f.controller.state.loading)
        assertTrue(f.controller.state.uncertain)
        assertEquals(1, f.controller.state.pending)
        val original = f.api.calls.single().second as MovePodCopy
        f.token = "new-token"; f.api.afterRead = {}
        f.controller.retry(); f.deliver()
        assertEquals(listOf(original, original), f.api.calls.map { it.second })
        assertFalse(f.controller.state.loading)
        assertFalse(f.controller.state.uncertain)
        assertEquals(0, f.controller.state.pending)
        assertEquals(PodDeckCategory.SIDE, f.controller.state.detail?.build?.copies?.single()?.category)
    }

    @Test fun lateOldCompletionCannotResetOrPopulateNewFlightAfterCredentialChangeOrClear() {
        for (clear in listOf(false, true)) Fixture().use { f ->
            f.controller.open(id); f.worker.drain()
            f.token = "new-token"
            if (clear) f.controller.clear()
            f.api.saved = f.api.saved.copy(name = "Fresh")
            f.controller.open(id)
            f.posts.removeFirst()()
            assertTrue(f.controller.state.loading)
            assertNull(f.controller.state.detail)
            f.deliver()
            assertEquals("Fresh", f.controller.state.detail?.name)
            assertFalse(f.controller.state.loading)
        }
    }

    @Test fun returningToNewPodReloadsCubesWithoutDiscardingTheDraft() = Fixture().use { f ->
        f.sources = emptyList()
        f.controller.newPod(); f.deliver()
        assertTrue(f.controller.state.cubesLoaded)
        assertTrue(f.controller.state.cubes.isEmpty())
        f.controller.newName("My draft")
        f.sources = listOf(cube)
        f.controller.shown(); f.deliver()
        assertEquals(listOf(cube), f.controller.state.cubes)
        assertEquals("My draft", f.controller.state.creating?.name)
        f.controller.newCube(cube.id); f.controller.create(); f.deliver()
        assertEquals(cube.id, (f.api.calls.single().second as CreatePod).cubeId)
    }

    @Test fun packCountsUseSingularOnlyForOneInRowsAndMemberBadges() {
        for (count in listOf(0, 1, 2, 12)) {
            val text = "$count ${if (count == 1) "pack" else "packs"}"
            assertEquals(text, podPackCount(count))
            assertEquals("Your pick · $text", podStatus(summary(detail()).copy(packsWaiting = count)))
            if (count > 0) assertEquals("picking · $text", podMemberStatus(PodMember(self, count), PodPhase.DRAFTING))
        }
    }

    @Test fun completionOpensBuildAndCancellationNoticeSurvivesUntilNextOpen() = Fixture().use { f ->
        f.open(); f.api.saved = f.api.saved.copy(phase = PodPhase.DRAFTED)
        f.controller.hint(ActivityHint("1", ActivityKind.POD_DRAFT_COMPLETE, id, attempt)); f.deliver()
        assertEquals(PodTab.BUILD, f.controller.state.tab)
        f.controller.back(); f.deliver()
        f.api.saved = detail(PodPhase.SETUP)
        f.controller.hint(ActivityHint("2", ActivityKind.POD_DRAFT_CANCELLED, id, attempt)); f.deliver()
        f.open()
        assertEquals("Draft cancelled", f.controller.state.notice)
        assertNull(f.controller.state.detail!!.draft)
    }
}
