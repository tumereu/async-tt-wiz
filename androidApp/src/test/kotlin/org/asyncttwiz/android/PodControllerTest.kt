package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
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
    private val nextPack = head.copy(id = "next", cards = head.cards.map { it.copy(id = "next-${it.id}") })
    private fun detail(phase: PodPhase = PodPhase.DRAFTING) = PodDetail(id, "Pod", phase, self,
        DraftSettings(), PodSource("Cube", 540), DraftSupply(90, 540), listOf(PodMember(self, 2), PodMember(other)), emptyList(),
        if (phase == PodPhase.SETUP) null else attempt,
        if (phase == PodPhase.SETUP) null else DraftPrivateView(self.accountId, head, 1, listOf(copy), listOf(nextPack)),
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
        var failSourceWrites = false
        var updateFailure: String? = null
        var gridFailure: String? = null
        var pickFailure: String? = null
        var afterRead: () -> Unit = {}
        override suspend fun list(token: String) = listed ?: PodList(if (missing) emptyList() else listOf(summary(saved)), emptyList())
        override suspend fun detail(token: String, id: String): PodDetail {
            reads++; readIds += id; if (missing) throw PodFailure("not_found"); afterRead()
            return secondPod?.takeIf { it.id == id } ?: saved
        }
        override suspend fun create(token: String, input: CreatePod): PodDetail {
            calls += "create" to input
            if (failSourceWrites) throw PodFailure("network")
            saved = detail(PodPhase.SETUP).copy(name = input.name, settings = input.settings ?: podModeSettings(input.mode, DraftSettings()),
                source = input.setSource?.podSource() ?: PodSource("Cube", 540))
            return saved
        }
        override suspend fun update(token: String, id: String, input: UpdatePod): PodDetail {
            calls += "update" to input
            updateFailure?.let { throw PodFailure(it) }
            if (failSourceWrites) throw PodFailure("network")
            saved = saved.copy(name = input.name ?: saved.name, settings = input.settings ?: saved.settings,
                source = input.setSource?.podSource() ?: saved.source)
            return saved
        }
        override suspend fun invite(token: String, id: String, input: InviteToPod): PodDetail { calls += "invite" to input; return saved }
        override suspend fun accept(token: String, id: String, input: PodRequest): PodDetail { calls += "accept" to input; return saved }
        override suspend fun decline(token: String, id: String, input: PodRequest): PodAcknowledged { calls += "decline" to input; return PodAcknowledged(id) }
        override suspend fun start(token: String, id: String, input: PodRequest): PodDetail { calls += "start" to input; saved = detail(); return saved }
        override suspend fun pick(token: String, id: String, input: PickPod): PodDetail {
            calls += "pick" to input
            pickFailure?.let { throw PodFailure(it) }
            val draft = saved.draft!!
            check(input.packId == draft.head!!.id)
            val kept = draft.head!!.cards.filter { it.id in input.keep }
            saved = saved.copy(draft = draft.copy(head = draft.queuedPacks.firstOrNull(),
                queuedPacks = draft.queuedPacks.drop(1), packsBehind = (draft.packsBehind - 1).coerceAtLeast(0), pool = draft.pool + kept),
                build = PodDeckRules.withAwards(saved.build!!, kept.map { it.id }))
            return saved
        }
        override suspend fun gridSelect(token: String, id: String, input: SelectPodGrid): PodDetail {
            calls += "grid" to input
            gridFailure?.let { throw PodFailure(it) }
            saved = saved.copy(grid = saved.grid!!.copy(mine = saved.grid!!.mine.copy(headGridId = null)))
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
        val sets = Sets()
        val controller = PodController(api, cubes { sources }, { token }, worker, { posts.add(it) }, { "Fantasy name" }, {}, sets)
        fun deliver() {
            while (worker.tasks.isNotEmpty() || posts.isNotEmpty()) {
                worker.drain(); while (posts.isNotEmpty()) posts.removeFirst()()
            }
        }
        fun open() { controller.open(id); deliver() }
        override fun close() { controller.close(); deliver() }
    }

    private inner class Sets : PodSetRepository {
        val calls = mutableListOf<String>()
        var fail = false
        var upload = SetSourceUpload(SetSourceSnapshot("TST", "Test set",
            listOf(SetSourceCard(definition.digest, UUID.randomUUID().toString()))), SubmittedDefinitions(listOf(definition)), emptyList(), emptyList())
        override fun sets(): List<DraftableSet> { calls += "sets"; return listOf(DraftableSet("TST", "Test set", "2026-10-01", 1, "expansion")) }
        override fun isCached(code: String) = true
        override fun load(code: String): SetSourceUpload { calls += "load:$code"; if (fail) error("offline"); return upload }
        override fun redownload(code: String): SetSourceUpload { calls += "redownload:$code"; if (fail) error("offline"); return upload }
    }

    @Test fun setupFieldTransformsMergeIntoLatestPendingSettingsAndSaveTogether() = Fixture(PodPhase.SETUP).use { f ->
        f.open()
        f.controller.editSettings { it.copy(packsPerPlayer = 4) }
        f.controller.editSettings { it.copy(cardsPerPack = 12) }
        f.controller.editSettings { it.copy(sideboardLimit = 5) }
        f.controller.flushSetup(); f.deliver()
        val saved = (f.api.calls.single().second as UpdatePod).settings!!
        assertEquals(DraftSettings().copy(packsPerPlayer = 4, cardsPerPack = 12, sideboardLimit = 5), saved)
        assertEquals(saved, f.controller.state.detail!!.settings)
    }

    @Test fun setupPredictsImmediatelyAndAcknowledgementsDoNotReplaceNewerEdits() = Fixture(PodPhase.SETUP).use { f ->
        f.open()
        f.controller.editSetup(name = "Renamed")
        f.controller.editSettings { it.copy(keep = 2) }
        assertEquals("Renamed", f.controller.state.detail!!.name)
        assertEquals(2, f.controller.state.detail!!.settings.keep)
        assertTrue(f.api.calls.isEmpty())
        f.controller.flushSetup()
        f.controller.editSettings { it.copy(burn = 3) }
        assertEquals(3, f.controller.state.detail!!.settings.burn)
        f.deliver()
        assertEquals(3, f.controller.state.detail!!.settings.burn)
        f.controller.flushSetup(); f.deliver()
        assertEquals(f.api.saved.settings, f.controller.state.detail!!.settings)
        assertNull(f.controller.state.setup)
    }

    @Test fun rejectedSetupRevertsDependentEditsAndReleasesControlsForNextSave() = Fixture(PodPhase.SETUP).use { f ->
        f.open()
        val original = f.controller.state.detail!!
        f.api.updateFailure = "invalid_settings"
        f.controller.editSettings { it.copy(keep = 2) }; f.controller.flushSetup()
        f.controller.editSetup(name = "Later edit"); f.controller.flushSetup()
        assertEquals(2, f.controller.state.pending)
        assertEquals("Later edit", f.controller.state.detail!!.name)
        f.deliver()
        assertEquals(original, f.controller.state.detail)
        assertNull(f.controller.state.setup)
        assertEquals(0, f.controller.state.pending)
        assertEquals("Settings not saved.", f.controller.state.error)
        assertEquals(1, f.api.calls.size)
        f.api.updateFailure = null
        f.controller.editSettings { it.copy(sideboardLimit = 0) }; f.controller.flushSetup(); f.deliver()
        assertEquals(0, f.controller.state.detail!!.settings.sideboardLimit)
    }

    @Test fun ambiguousSettingsSaveStillAllowsLocalEditsAndRetriesOriginalRequest() = Fixture(PodPhase.SETUP).use { f ->
        f.open(); f.api.failSourceWrites = true
        f.controller.editSettings { it.copy(keep = 2) }; f.controller.flushSetup(); f.deliver()
        val original = f.api.calls.first().second
        assertTrue(f.controller.state.uncertain)
        f.controller.editSettings { it.copy(burn = 3) }
        assertEquals(3, f.controller.state.detail!!.settings.burn)
        f.api.failSourceWrites = false; f.controller.retry(); f.deliver()
        assertEquals(original, f.api.calls.last().second)
        assertEquals(3, f.controller.state.detail!!.settings.burn)
        f.controller.flushSetup(); f.deliver()
        assertEquals(3, f.api.saved.settings.burn)
    }

    @Test fun creationSendsEverySettingInOneRequest() = Fixture(PodPhase.SETUP).use { f ->
        f.controller.newPod(cube.id); f.deliver()
        val settings = DraftSettings.glimpse().copy(cardsPerPack = 12, keep = 2, burn = 3,
            discardRemainingAt = 1, packsOpenAtOnce = 3, colorBalance = false, sideboardLimit = 7)
        f.controller.newSettings { settings }
        assertEquals(settings, f.controller.state.creating!!.settings)
        f.controller.create(); f.deliver()
        assertEquals(settings, (f.api.calls.single().second as CreatePod).settings)
        assertEquals(settings, f.controller.state.detail!!.settings)
    }

    @Test fun creationPreservesEditsMadeWhileTheRequestIsPending() = Fixture(PodPhase.SETUP).use { f ->
        f.controller.newPod(cube.id); f.deliver()
        f.controller.create()
        f.controller.newName("Changed during creation")
        f.controller.newSettings { it.copy(keep = 2, sideboardLimit = 7) }
        f.deliver()
        assertEquals("Changed during creation", f.controller.state.detail!!.name)
        assertEquals(2, f.controller.state.detail!!.settings.keep)
        assertEquals(7, f.controller.state.detail!!.settings.sideboardLimit)
        assertNotNull(f.controller.state.setup)
        f.controller.flushSetup(); f.deliver()
        assertEquals(listOf("create", "update"), f.api.calls.map { it.first })
        assertEquals("Changed during creation", f.api.saved.name)
        assertEquals(2, f.api.saved.settings.keep)
    }

    @Test fun reopeningSourcePickerCancelsPreparationAndRestoresThePreviousSelection() = Fixture(PodPhase.SETUP).use { f ->
        f.controller.newPod(cube.id); f.deliver(); f.controller.chooseSet(); f.deliver()
        f.controller.setPicker.choose("TST")
        assertNotNull(f.controller.state.sourcePreview)
        f.controller.chooseSet()
        assertNull(f.controller.state.sourcePreview)
        f.controller.setPicker.dismiss(); f.deliver()
        assertNull(f.controller.state.creating!!.setSource)
        assertEquals(cube.id, f.controller.state.creating!!.cubeId)
    }

    @Test fun cachedSetSelectionDuringPendingSaveClosesImmediatelyAndSurvivesQueuedRefresh() = Fixture(PodPhase.SETUP).use { f ->
        f.open(); f.controller.chooseSet(); f.deliver(); f.controller.setPicker.dismiss()
        f.controller.editSetup(name = "Renamed"); f.controller.flushSetup()
        f.controller.chooseSet()
        assertTrue(f.controller.state.loading)
        assertEquals(1, f.controller.state.pending)
        assertTrue(f.controller.setPicker.state.busy)
        assertTrue(f.controller.setPicker.state.rows.single().cached)
        f.controller.setPicker.choose("TST")
        assertFalse(f.controller.setPicker.state.open)
        assertEquals("Test set", f.controller.state.sourcePreview?.setName)
        assertEquals(listOf("sets"), f.sets.calls) // Save, refresh and preparation are still queued.
        f.deliver()
        assertFalse(f.controller.setPicker.state.open)
        assertFalse(f.controller.setPicker.state.busy)
        assertNull(f.controller.state.sourcePreview)
        assertEquals("Renamed", f.controller.state.detail!!.name)
        assertEquals("Test set", f.controller.state.detail!!.source.setName)
        assertEquals(f.sets.upload.snapshot, f.controller.state.setup!!.setSource!!.snapshot)
        f.controller.flushSetup(); f.deliver()
        assertEquals("Test set", f.api.saved.source.setName)
        assertNull(f.controller.state.setup)
        assertEquals(listOf("sets", "sets", "load:TST"), f.sets.calls)
    }

    @Test fun cachedSetSelectionDuringRefreshRevertsPreviewOnPreparationFailure() = Fixture(PodPhase.SETUP).use { f ->
        f.open(); f.controller.chooseSet(); f.deliver(); f.controller.setPicker.dismiss()
        val original = f.controller.state.detail!!.source
        f.controller.editSettings { it.copy(keep = 2) }; f.controller.flushSetup()
        f.controller.chooseSet()
        f.sets.fail = true
        f.controller.setPicker.choose("TST")
        assertFalse(f.controller.setPicker.state.open)
        assertEquals("Test set", f.controller.state.sourcePreview?.setName)
        f.deliver()
        assertNull(f.controller.state.sourcePreview)
        assertEquals(original, f.controller.state.detail!!.source)
        assertEquals(2, f.controller.state.detail!!.settings.keep)
        assertEquals("Could not load source.", f.controller.state.error)
        assertFalse(f.controller.setPicker.state.open)
        assertFalse(f.controller.setPicker.state.busy)
    }

    @Test fun setCreatePreparesOnWorkerAndRetainsExactUploadAndRequestIdentityAcrossRetries() = Fixture(PodPhase.SETUP).use { f ->
        f.controller.newPod(cube.id); f.deliver()
        f.controller.chooseSet()
        assertTrue(f.sets.calls.isEmpty()) // No repository IO on the caller/main thread.
        assertTrue(f.controller.setPicker.state.busy)
        f.deliver()
        f.controller.setPicker.choose("TST")
        assertFalse(f.controller.setPicker.state.open)
        assertEquals("Test set", f.controller.state.sourcePreview?.setName)
        assertEquals(listOf("sets"), f.sets.calls)
        f.deliver()
        assertNull(f.controller.state.creating!!.cubeId)
        assertEquals(f.sets.upload.snapshot, f.controller.state.creating!!.setSource!!.snapshot)
        assertFalse(f.controller.setPicker.state.open)
        f.api.failSourceWrites = true
        f.controller.create()
        assertTrue(f.controller.state.loading)
        assertEquals(1, f.controller.state.pending)
        assertTrue(f.api.calls.isEmpty())
        f.deliver()
        assertTrue(f.controller.state.uncertain)
        val original = f.api.calls.first().second as CreatePod
        assertNull(original.cubeId)
        assertEquals(PodSetSource(f.sets.upload.snapshot, listOf(definition)), original.setSource)
        assertTrue(f.api.calls.all { it.second == original })
        f.sets.upload = f.sets.upload.copy(snapshot = f.sets.upload.snapshot.copy(setName = "Changed download"))
        f.api.failSourceWrites = false; f.controller.retry(); f.deliver()
        assertEquals(original, f.api.calls.last().second)
        assertEquals(listOf("sets", "load:TST"), f.sets.calls)
        assertEquals(PodSourceKind.SET, f.controller.state.detail!!.source.kind)
        assertFalse(f.controller.state.uncertain)
    }

    @Test fun setUpdateUsesDefinitionsAndStableRetryAndSettingsOnlyUpdateDoesNotReupload() = Fixture(PodPhase.SETUP).use { f ->
        f.open(); f.controller.chooseSet(); f.deliver()
        f.controller.setPicker.choose("TST"); f.deliver()
        assertNotNull(f.controller.state.setup!!.setSource)
        f.api.failSourceWrites = true; f.controller.flushSetup(); f.deliver()
        val original = f.api.calls.first().second as UpdatePod
        assertNull(original.cubeId)
        assertEquals(PodSetSource(f.sets.upload.snapshot, listOf(definition)), original.setSource)
        assertTrue(f.controller.state.uncertain)
        f.api.failSourceWrites = false; f.controller.retry(); f.deliver()
        assertEquals(original, f.api.calls.last().second)
        assertNull(f.controller.state.setup)
        f.controller.editSetup(name = "Set draft"); f.controller.flushSetup(); f.deliver()
        assertNull((f.api.calls.last().second as UpdatePod).setSource)
        assertEquals(PodSourceKind.SET, f.controller.state.detail!!.source.kind)
    }

    @Test fun setDownloadAndRedownloadErrorsCanRetryWithoutSelectingOrUploadingOnManualRefresh() = Fixture(PodPhase.SETUP).use { f ->
        f.controller.newPod(); f.deliver(); f.controller.chooseSet(); f.deliver()
        f.sets.fail = true; f.controller.setPicker.redownload("TST"); f.deliver()
        assertNotNull(f.controller.setPicker.state.error)
        assertFalse(f.controller.setPicker.state.busy)
        f.sets.fail = false; f.controller.setPicker.retry(); f.deliver()
        assertEquals(listOf("sets", "redownload:TST", "redownload:TST"), f.sets.calls)
        assertTrue(f.controller.setPicker.state.rows.single().cached)
        assertTrue(f.controller.setPicker.state.open)
        assertNull(f.controller.state.creating!!.setSource)
        f.sets.fail = true; f.controller.setPicker.choose("TST"); f.deliver()
        assertNotNull(f.controller.setPicker.state.error)
        assertNull(f.controller.state.sourcePreview)
        assertNull(f.controller.state.creating!!.setSource)
        assertNotNull(f.controller.state.error)
        f.sets.fail = false; f.controller.chooseSet(); f.deliver(); f.controller.setPicker.choose("TST"); f.deliver()
        assertNotNull(f.controller.state.creating!!.setSource)
        assertTrue(f.api.calls.isEmpty())
        f.controller.newCube(cube.id)
        assertNull(f.controller.state.creating!!.setSource)
        assertEquals(cube.id, f.controller.state.creating!!.cubeId)
    }

    @Test fun leavingPickerOrLoggingOutFencesLateSetPreparation() = Fixture(PodPhase.SETUP).use { f ->
        f.controller.newPod(); f.deliver(); f.controller.chooseSet(); f.deliver()
        f.controller.setPicker.choose("TST"); f.worker.drain()
        f.controller.back(); f.deliver()
        assertNull(f.controller.state.creating)
        f.controller.newPod(); f.deliver(); f.controller.chooseSet(); f.deliver(); f.controller.setPicker.choose("TST"); f.worker.drain()
        f.controller.clear(); f.deliver()
        assertNull(f.controller.state.creating)
        assertFalse(f.controller.setPicker.state.open)
        assertTrue(f.api.calls.isEmpty())
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

    @Test fun queuedPicksAdvanceImmediatelyAndSendInOrderWithoutResponseFlicker() = Fixture().use { f ->
        f.open()
        f.controller.pick(listOf("copy"), emptyList())
        assertEquals(nextPack, f.controller.state.detail!!.draft!!.head)
        assertEquals(0, f.controller.state.detail!!.draft!!.packsBehind)
        assertEquals(1, f.controller.state.detail!!.members.first().packsWaiting)
        f.controller.pick(listOf("next-copy"), emptyList())
        f.controller.pick(listOf("next-copy"), emptyList()) // No pack: no duplicate write.
        assertNull(f.controller.state.detail!!.draft!!.head)
        assertTrue(f.controller.state.detail!!.draft!!.queuedPacks.isEmpty())
        assertEquals(0, f.controller.state.detail!!.members.first().packsWaiting)
        assertTrue(f.controller.state.detail!!.build!!.copies.any { it.copyId == "next-copy" })
        assertEquals(2, f.controller.state.pending)
        assertTrue(f.api.calls.isEmpty())
        assertTrue(f.controller.state.picking)
        f.worker.one()
        assertNull(f.controller.state.detail!!.draft!!.head)
        f.posts.removeFirst()()
        assertNull(f.controller.state.detail!!.draft!!.head) // The second prediction survives the first ack.
        val request = f.api.calls.single().second as PickPod
        assertEquals(attempt, request.draftAttemptId); assertEquals("pack", request.packId)
        assertEquals(listOf("copy"), request.keep)
        f.deliver()
        assertEquals(listOf("pack", "next"), f.api.calls.map { (it.second as PickPod).packId })
        assertEquals(f.api.saved.draft, f.controller.state.detail!!.draft)
        assertFalse(f.controller.state.picking)
    }

    @Test fun rejectedPickRestoresItsPackAndDropsDependentUnsentPicks() = Fixture().use { f ->
        f.open(); f.api.pickFailure = "wrong_counts"
        val original = f.controller.state.detail
        f.controller.pick(listOf("copy"), emptyList())
        f.controller.pick(listOf("next-copy"), emptyList())
        assertNull(f.controller.state.detail!!.draft!!.head)
        f.deliver()
        assertEquals(original, f.controller.state.detail)
        assertEquals(1, f.api.calls.size)
        assertEquals("Pick rejected.", f.controller.state.error)
        assertFalse(f.controller.state.uncertain)
        assertFalse(f.controller.state.picking)
        assertEquals(0, f.controller.state.pending)
        f.api.pickFailure = null
        f.controller.pick(listOf("copy"), emptyList())
        assertEquals(nextPack, f.controller.state.detail!!.draft!!.head)
        f.deliver()
        assertNull(f.controller.state.error)
    }

    @Test fun laterPickRejectionRestoresOnlyTheRejectedPack() = Fixture().use { f ->
        f.open()
        f.controller.pick(listOf("copy"), emptyList())
        f.controller.pick(listOf("next-copy"), emptyList())
        f.worker.one(); f.posts.removeFirst()()
        val accepted = f.api.saved
        f.api.pickFailure = "not_head_of_queue"; f.deliver()
        assertEquals(accepted.draft, f.controller.state.detail!!.draft)
        assertEquals(accepted.build, f.controller.state.detail!!.build)
        assertEquals(nextPack, f.controller.state.detail!!.draft!!.head)
        assertEquals("Pick rejected.", f.controller.state.error)
    }

    @Test fun ambiguousQueuedPickKeepsPredictionsAndRetriesOriginalIdentityBeforeLaterPicks() = Fixture().use { f ->
        f.open(); f.api.pickFailure = "network"
        f.controller.pick(listOf("copy"), emptyList())
        f.controller.pick(listOf("next-copy"), emptyList())
        f.deliver()
        assertTrue(f.controller.state.uncertain)
        assertNull(f.controller.state.detail!!.draft!!.head)
        val original = f.api.calls.first().second
        assertTrue(f.api.calls.all { it.second == original })
        f.api.pickFailure = null; f.controller.retry(); f.deliver()
        assertEquals(original, f.api.calls[f.api.calls.lastIndex - 1].second)
        assertEquals("next", (f.api.calls.last().second as PickPod).packId)
        assertFalse(f.controller.state.uncertain)
        assertEquals(0, f.controller.state.pending)
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

    private fun gridDetail(): PodDetail {
        val boards = listOf(GridPublicBoard("grid-0", 1, List(9) { copy.copy(id = "copy-$it") }, self.accountId),
            GridPublicBoard("grid-1", 2, List(9) { copy.copy(id = "later-$it") }, self.accountId))
        return detail().copy(settings = DraftSettings.grid(), draft = null,
            grid = org.asyncttwiz.protocol.PodGridView(GridPublicView(1, 9, false, listOf(self.accountId, other.accountId), boards, emptyList()),
                GridPrivateView(self.accountId, listOf(copy), "grid-0")))
    }

    @Test fun gridSelectSendsAttemptAndLineOnlyOnceAndDoesNotPredict() = Fixture().use { f ->
        f.api.saved = gridDetail(); f.open()
        val before = f.controller.state.detail!!.grid
        f.controller.gridSelect("grid-1", GridLineType.ROW, 0) // Only the head can be selected.
        f.controller.gridSelect("grid-0", GridLineType.ROW, 3)
        assertEquals(0, f.controller.state.pending)
        f.controller.gridSelect("grid-0", GridLineType.COLUMN, 1)
        f.controller.gridSelect("grid-0", GridLineType.ROW, 0)
        assertTrue(f.controller.state.picking)
        assertEquals(before, f.controller.state.detail!!.grid)
        f.worker.one()
        assertEquals(before, f.controller.state.detail!!.grid)
        f.posts.removeFirst()()
        val input = f.api.calls.single().second as SelectPodGrid
        assertTrue(DeckValidation.uuid(input.requestId))
        assertEquals(attempt, input.draftAttemptId)
        assertEquals("grid-0", input.gridId)
        assertEquals(GridLineType.COLUMN, input.line)
        assertEquals(1, input.index)
        assertNull(f.controller.state.detail!!.grid!!.mine.headGridId)
        assertFalse(f.controller.state.picking)
        assertEquals(2, f.api.reads)
    }

    @Test fun rejectedGridSelectionKeepsBoardAndReleasesControls() = Fixture().use { f ->
        f.api.saved = gridDetail(); f.api.gridFailure = "empty_line"; f.open()
        val before = f.controller.state.detail!!.grid
        f.controller.gridSelect("grid-0", GridLineType.ROW, 0); f.deliver()
        assertEquals(before, f.controller.state.detail!!.grid)
        assertNotNull(f.controller.state.error)
        assertFalse(f.controller.state.uncertain)
        assertFalse(f.controller.state.picking)
        assertEquals(0, f.controller.state.pending)
    }

    @Test fun ambiguousGridSelectionRetriesOriginalRequestAndExpiredAttemptRefreshes() {
        Fixture().use { f ->
            f.api.saved = gridDetail(); f.api.gridFailure = "network"; f.open()
            f.controller.gridSelect("grid-0", GridLineType.ROW, 2); f.deliver()
            assertTrue(f.controller.state.uncertain)
            assertTrue(f.controller.state.picking)
            val original = f.api.calls.first().second as SelectPodGrid
            assertTrue(f.api.calls.all { it.second == original })
            f.api.gridFailure = null; f.controller.retry(); f.deliver()
            assertEquals(original, f.api.calls.last().second)
            assertFalse(f.controller.state.uncertain)
            assertFalse(f.controller.state.picking)
        }
        Fixture().use { f ->
            f.api.saved = gridDetail(); f.open()
            f.api.gridFailure = "draft_attempt_expired"
            f.api.saved = detail(PodPhase.SETUP)
            f.controller.gridSelect("grid-0", GridLineType.ROW, 0); f.deliver()
            assertEquals(PodPhase.SETUP, f.controller.state.detail!!.phase)
            assertNull(f.controller.state.detail!!.grid)
            assertEquals(0, f.controller.state.pending)
        }
    }

    @Test fun gridMemberStatusUsesPickingFlagAndStartRequiresTwoToFourMembers() {
        assertEquals("picking", podMemberStatus(PodMember(self, picking = true), PodPhase.DRAFTING, DraftMode.GRID))
        assertEquals("waiting", podMemberStatus(PodMember(self, packsWaiting = 3), PodPhase.DRAFTING, DraftMode.GRID))
        for (count in 1..8) {
            val pod = detail(PodPhase.SETUP).copy(settings = DraftSettings.grid(), members = List(count) { PodMember(self) })
            assertEquals(count in 2..4, podCanStart(pod))
        }
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
        assertEquals(CreatePod((f.api.calls.single().second as CreatePod).requestId, "Fantasy name", cube.id, DraftMode.GLIMPSE,
            settings = DraftSettings.glimpse()), f.api.calls.single().second)
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
