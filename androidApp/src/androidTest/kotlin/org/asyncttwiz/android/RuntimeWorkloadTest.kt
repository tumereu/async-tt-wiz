package org.asyncttwiz.android

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPOutputStream
import kotlin.math.ceil
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Assert.*
import org.junit.Test

/** LOWER-LAYER component workloads in the normal APK. Synthetic inputs are NOT UI setup/E2E.
 * Real pointer delivery, production TableSurface/BasicTableController/GameplayReplica, real window
 * FrameMetrics and enabled animations. No Compose test clock or test-only dispatch API.
 * This intentionally does NOT measure GameSession socket write start or remote committed visibility.
 */
class RuntimeWorkloadTest {
    private val executionId = UUID.randomUUID().toString()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun setting(name: String, default: Int, range: IntRange) =
        (args.getString(name)?.toInt() ?: default).also { require(it in range) }
    private fun source(): String = args.getString("runtimeSourceCommit").orEmpty().also {
        require(Regex("[0-9a-f]{40}").matches(it)) { "Executor must supply runtimeSourceCommit" }
    }

    @Test fun animatedTable200Visible400LinksAndProjectionsWithEmpty60And250Libraries() {
        val source = source()
        assertEquals("Executor must supply the portrait component profile", Configuration.ORIENTATION_PORTRAIT,
            context.resources.configuration.orientation)
        for (key in listOf(Settings.Global.ANIMATOR_DURATION_SCALE, Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.WINDOW_ANIMATION_SCALE)) {
            assertTrue("System animations must be enabled", Settings.Global.getFloat(context.contentResolver, key, 1f) > 0f)
        }
        val samples = setting("runtimeSamples", 20, 20..100)
        val warmup = setting("runtimeWarmup", 3, 3..20)
        val frameThread = HandlerThread("I33-frame-observations").apply { start() }
        val frameHandler = Handler(frameThread.looper)
        val frames = java.util.Collections.synchronizedList(mutableListOf<FrameObservation>())
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            frames.add(FrameObservation(metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
                metrics.getMetric(FrameMetrics.TOTAL_DURATION), dropped))
        }
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        var refreshHz = 0f
        try {
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                scenario.onActivity {
                    refreshHz = it.window.decorView.display.refreshRate
                    it.window.addOnFrameMetricsAvailableListener(listener, frameHandler)
                }
                try {
                    for (library in listOf(0, 60, 250)) {
                        val initial = sceneFixture(library)
                        val game = game(initial)
                        val replica = GameplayReplica().apply {
                            assertTrue(root(GameHead(game.id, game.revision, game, play = initial)))
                        }
                        val shown = mutableStateOf(initial)
                        val geometry = AtomicReference<Geometry?>()
                        val observation = AtomicReference<PointerObservation?>()
                        val ready = CountDownLatch(1)
                        var predictionId: String? = null
                        val controller = BasicTableController({ replica.display }, { true }) { action, invocation ->
                            val command = GameCommand(UUID.randomUUID().toString(), GameScope(initial.gameId, playId = initial.id),
                                GameAction.Play(action), id(80), "1", invocationId = invocation)
                            val accepted = replica.predict(command, id(7))
                            if (accepted) { predictionId = command.requestId; shown.value = replica.display!! }
                            accepted
                        }
                        scenario.onActivity { activity ->
                            activity.setContent {
                                MaterialTheme {
                                    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                                        val offset = coordinates.positionInWindow()
                                        val location = IntArray(2)
                                        activity.window.decorView.getLocationOnScreen(location)
                                        geometry.get()?.let { geometry.set(it.copy(x = offset.x + location[0], y = offset.y + location[1])) }
                                    }.pointerInput(library) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                observation.get()?.let { sample ->
                                                    if (event.changes.any { it.pressed && !it.previousPressed } && sample.firstDown == 0L)
                                                        sample.firstDown = System.nanoTime()
                                                    if (event.changes.any { !it.pressed && it.previousPressed }) sample.lastUp = System.nanoTime()
                                                }
                                            }
                                        }
                                    }) {
                                        val width = maxWidth.value; val height = maxHeight.value
                                        val placements = remember(width, height, library) {
                                            densePlacements(game, initial, width, height)
                                        }
                                        // One setup geometry calculation, NOT extra per-frame work beside TableSurface.
                                        val timedScene = remember(width, height, library) {
                                            val start = System.nanoTime()
                                            val scene = TablePresentation.scene(game, initial, id(7), width, height, false, placements = placements)
                                            scene to (System.nanoTime() - start)
                                        }
                                        val scene = timedScene.first
                                        SideEffect {
                                            val old = geometry.get()
                                            val target = scene.items.single { it.cardId == id(1_099) }
                                            geometry.set(Geometry(old?.x ?: 0f, old?.y ?: 0f,
                                                target.bounds.x + 16f, target.bounds.y + 16f,
                                                scene.items.count { it.kind == TableItemKind.CARD && it.visible.isNotEmpty() },
                                                scene.items.count { it.projection && it.visible.isNotEmpty() }, timedScene.second))
                                            ready.countDown()
                                        }
                                        TableSurface(game, shown.value, id(7), false, Modifier.fillMaxSize(), connected = true,
                                            placements = placements, onIntent = { tableIntent ->
                                                val sample = observation.get()
                                                val start = System.nanoTime()
                                                val sent = controller.handle(tableIntent)
                                                if (sent && sample != null) {
                                                    sample.callback = start; sample.provisional = System.nanoTime(); sample.done.countDown()
                                                }
                                                sent
                                            })
                                    }
                                }
                            }
                        }
                        assertTrue("Scene did not mount", ready.await(10, TimeUnit.SECONDS))
                        instrumentation.waitForIdleSync()
                        val sceneGeometry = geometry.get()!!
                        assertTrue("Fixture must actually expose 200 cards, not merely retain them", sceneGeometry.visible >= 200)
                        assertTrue("Projection geometry must be visible", sceneGeometry.projections >= 4)
                        val measured = mutableListOf<PointerObservation>()
                        repeat(warmup + samples) { index ->
                            val sample = PointerObservation()
                            observation.set(sample)
                            val windowStart = System.nanoTime()
                            doubleTap(sceneGeometry)
                            assertTrue("Normal pointer double tap never reached the production callback", sample.done.await(10, TimeUnit.SECONDS))
                            Thread.sleep(250) // real-time production 180ms tween, never advance a fake clock
                            instrumentation.waitForIdleSync()
                            val windowEnd = System.nanoTime()
                            val drained = CountDownLatch(1)
                            frameHandler.post { drained.countDown() }
                            assertTrue(drained.await(10, TimeUnit.SECONDS))
                            scenario.onActivity {
                                assertTrue(replica.display!!.board.cards.single { it.id == id(1_099) }.tapped)
                                assertEquals(initial, replica.accepted) // no invented server commit
                            }
                            if (index >= warmup) {
                                sample.frames = synchronized(frames) { frames.filter { it.vsync in windowStart..windowEnd } }
                                sample.heapBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                                sample.pssKiB = Debug.getPss().toLong()
                                measured += sample
                            }
                            observation.set(null)
                            scenario.onActivity {
                                replica.retire(checkNotNull(predictionId)); predictionId = null
                                shown.value = replica.display!!
                            }
                            Thread.sleep(250) // reset outside the measured warm ordinary sample
                            synchronized(frames) { frames.clear() }
                        }
                        val durations = measured.flatMap { it.frames }.map { it.duration }
                        output("table-library-$library", source, buildJsonObject {
                            put("libraryMembersPerPlayer", library); put("visibleCards", sceneGeometry.visible)
                            put("visibleProjections", sceneGeometry.projections); put("links", initial.board.links.size)
                            put("projectionLinks", initial.board.projectionLinks.size); put("warmupExcluded", warmup)
                            put("animationsEnabled", true)
                            put("displayRefreshHz", refreshHz); put("orientation", "portrait")
                            series("setupProductionGeometryNanos", listOf(sceneGeometry.geometryNanos))
                            // Observer is before the arbiter, not its internal recognition seam.
                            put("recognitionDefinition", "first pointer down to second release; NOT internal recognition completion")
                            put("dispatchDefinition", "second release observed at Initial pass to TableSurface callback; upper bound, not socket enqueue")
                            put("provisionalDefinition", "callback entry through BasicTableController + GameplayReplica.predict return")
                            series("recognitionWindowNanos", measured.map { it.lastUp - it.firstDown })
                            series("releaseToCallbackUpperBoundNanos", measured.map { it.callback - it.lastUp })
                            series("callbackToProvisionalNanos", measured.map { it.provisional - it.callback })
                            series("releaseToProvisionalUpperBoundNanos", measured.map { it.provisional - it.lastUp })
                            series("actualWindowFrameNanos", durations)
                            putJsonArray("rawSamples") { measured.forEach { sample -> add(buildJsonObject {
                                put("firstDownNanos", sample.firstDown); put("secondReleaseNanos", sample.lastUp)
                                put("callbackNanos", sample.callback); put("provisionalNanos", sample.provisional)
                                put("heapUsedBytes", sample.heapBytes); put("pssKiB", sample.pssKiB)
                                putJsonArray("frames") { sample.frames.forEach { frame -> add(buildJsonObject {
                                    put("intendedVsyncNanos", frame.vsync); put("totalDurationNanos", frame.duration)
                                    put("listenerDroppedReports", frame.dropped)
                                }) } }
                            }) } }
                        })
                        assertTrue(measured.all { it.firstDown > 0 && it.lastUp >= it.firstDown && it.callback >= it.lastUp })
                        assertTrue("Incomplete FrameMetrics observations", measured.all { it.frames.isNotEmpty() && it.frames.all { f -> f.dropped == 0 } })
                        assertTrue("Component release-to-provisional p95 exceeds 50ms", percentile(measured.map { it.provisional - it.lastUp }, .95) <= 50_000_000L)
                        assertTrue("Frame p95 exceeds 16.7ms", percentile(durations, .95) <= 16_700_000L)
                        assertTrue("Frame p99 exceeds 33.3ms", percentile(durations, .99) <= 33_300_000L)
                    }
                } finally { scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) } }
            }
        } finally { frameThread.quitSafely(); frameThread.join(10_000); assertFalse(frameThread.isAlive) }
    }

    @Test fun complete10k100kLocalExportStagingPublicationAndColdOpen() {
        val source = source()
        val sizes = (args.getString("runtimeHistoryNodes") ?: "10000,100000").split(',').map(String::toInt)
        require(sizes.isNotEmpty() && sizes.all { it == 10_000 || it == 100_000 } && sizes.distinct() == sizes)
        for (count in sizes) for (branches in listOf(2, 100)) {
            assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
            val directory = File(context.noBackupFilesDir, "I33-fixture-${UUID.randomUUID()}").apply { check(mkdir()) }
            try {
                val tree = historyFixture(count, branches)
                val chunks = mutableListOf<TreeDownloadChunk>()
                var records = 0
                val exportStart = System.nanoTime()
                // Stream production records through a bounded on-disk transport fixture, never a full byte blob.
                val flat = File(directory, "complete.ndjson")
                flat.bufferedWriter().use { writer -> TreeSync.records(TreeSync.generation(tree)).forEach {
                    writer.append(GameWire.json.encodeToString(TreeSyncRecord.serializer(), it)).append('\n'); records++
                } }
                flat.inputStream().use { input ->
                    val buffer = ByteArray(GameplaySync.CHUNK_BYTES)
                    while (true) {
                        var size = 0
                        while (size < buffer.size) {
                            val read = input.read(buffer, size, buffer.size - size)
                            if (read < 0) break
                            size += read
                        }
                        if (size == 0) break
                        val bytes = buffer.copyOf(size)
                        val index = chunks.size
                        File(directory, "$index.gz").outputStream().use { raw -> GZIPOutputStream(raw).use { it.write(bytes) } }
                        chunks += TreeDownloadChunk(index, size, TreeSync.digest(bytes))
                    }
                }
                val exportNanos = System.nanoTime() - exportStart
                val plan = TreeDownloadPlan(id(800_000), tree.initial.gameId, tree.initial.id, 1, records, chunks,
                    StoredPayload(1, "lower-layer-streamed-fixture-v1"))
                (AndroidClientStorage.open(File(directory, "replica.db")) as StorageOpenResult.Opened).storage.use { storage ->
                    storage.savePersonal("installation", "account-owner", StoredPayload(1, id(7)))
                    val cache = GameplayTreeCache.bind(storage, id(7), tree.initial.gameId, tree.initial.id)
                    val download = TreeDownload(cache, Looper.getMainLooper().thread)
                    val beforeHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                    val start = System.nanoTime()
                    val staged = download.stage(plan) { File(directory, "$it.gz").inputStream() }
                    val stagedAt = System.nanoTime()
                    assertNull(cache.accepted())
                    val publication = download.publish(staged, emptyList(), 1)
                    val publishedAt = System.nanoTime()
                    assertTrue(publication is TreePublication.Published)
                    assertNull(cache.transfer())
                    val coldStart = System.nanoTime()
                    val restored = checkNotNull(download.accepted()).generation.tree
                    val state = checkNotNull(restored.stateAt(restored.currentNodeId))
                    val coldNanos = System.nanoTime() - coldStart
                    output("history-$count-$branches", source, buildJsonObject {
                        put("nodes", count); put("branches", branches); put("records", records); put("chunks", chunks.size)
                        put("decodedBytes", chunks.sumOf { it.bytes.toLong() }); put("maxChunkBytes", chunks.maxOf { it.bytes })
                        put("exportDefinition", "shared flat codec to bounded disk chunks, NOT server GameplayStore export")
                        series("fixtureExportNanos", listOf(exportNanos)); series("productionStageNanos", listOf(stagedAt - start))
                        series("productionPublishNanos", listOf(publishedAt - stagedAt)); series("coldLocalAcceptedOpenNanos", listOf(coldNanos))
                        put("heapUsedBeforeStageBytes", beforeHeap)
                        put("heapUsedAfterOpenBytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                        put("pssAfterOpenKiB", Debug.getPss()); put("nativeHeapAllocatedBytes", Debug.getNativeHeapAllocatedSize())
                    })
                    assertEquals(tree, restored)
                    assertEquals((20 + count / branches).toString(), state.board.players.first().life)
                    assertEquals(count, restored.nodes.size)
                    for (head in restored.branchHeads.values) assertEquals((20 + count / branches).toString(),
                        checkNotNull(restored.stateAt(head)).board.players.first().life)
                    if (count == 10_000) assertTrue("Cold 10k local accepted open exceeds 3s", coldNanos <= 3_000_000_000L)
                }
            } finally { directory.walkBottomUp().forEach { check(it.delete()) } } // only this test's unique files
        }
    }

    private data class Geometry(val x: Float, val y: Float, val targetX: Float, val targetY: Float,
        val visible: Int, val projections: Int, val geometryNanos: Long)
    private data class FrameObservation(val vsync: Long, val duration: Long, val dropped: Int)
    private class PointerObservation {
        @Volatile var firstDown = 0L
        @Volatile var lastUp = 0L
        @Volatile var callback = 0L
        @Volatile var provisional = 0L
        val done = CountDownLatch(1)
        var frames: List<FrameObservation> = emptyList()
        var heapBytes = 0L
        var pssKiB = 0L
    }

    private fun doubleTap(geometry: Geometry) {
        val density = context.resources.displayMetrics.density
        val x = geometry.x + geometry.targetX * density
        val y = geometry.y + geometry.targetY * density
        repeat(2) { tap ->
            val down = SystemClock.uptimeMillis()
            MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0).also {
                try { instrumentation.sendPointerSync(it) } finally { it.recycle() }
            }
            Thread.sleep(40)
            MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0).also {
                try { instrumentation.sendPointerSync(it) } finally { it.recycle() }
            }
            if (tap == 0) Thread.sleep(80)
        }
    }

    private fun game(play: PlayState) = GameSummary(play.gameId, "Workload", "2026-10-23T00:00:00Z",
        AccountIdentity(id(7), "Workload A"), AccountIdentity(id(8), "Workload B"), play.preparationRound,
        "1", InvitationState.ACCEPTED, lifecycle = GameLifecycle.ACTIVE, playId = play.id)

    private fun root() = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))), emptyList(), emptyList(), TurnState(id(7))),
        emptyList(), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))

    private fun sceneFixture(library: Int): PlayState {
        val definition = TokenCatalog.blank
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        for (player in 0..1) for (zone in PlayZone.entries.filter { it != PlayZone.STACK }) {
            val container = id(100 + player * 10 + zone.ordinal)
            val members = (0 until when (zone) { PlayZone.BATTLEFIELD -> 100; PlayZone.LIBRARY -> library; else -> 0 }).map { n ->
                id(if (zone == PlayZone.BATTLEFIELD) 1_000 + player * 100 + n else 10_000 + player * 1_000 + n)
            }
            cards += members.map { PlayCard(it, definition.digest, id(7 + player), container,
                definition.definition.faces.single().faceId, covered = zone != PlayZone.BATTLEFIELD) }
            containers += PlayContainer(container, zone, id(7 + player), members)
        }
        containers += PlayContainer(id(150), PlayZone.STACK)
        val field = cards.filter { c -> containers.single { it.id == c.containerId }.zone == PlayZone.BATTLEFIELD }
        val occurrences = (0..3).map { CombatOccurrence(id(30_000 + it), field[it * 50].id) }
        return root().copy(definitions = listOf(definition), board = root().board.copy(cards = cards, containers = containers,
            links = (0 until 400).map { n -> PlayLink(id(40_000 + n), LinkEndpoint(LinkEndpointKind.CARD, field[n % 200].id),
                LinkEndpoint(LinkEndpointKind.CARD, field[(n + if (n < 200) 1 else 17) % 200].id)) },
            combat = CombatArea(id(31_000), listOf(CombatPair(id(31_001), listOf(occurrences[0]), listOf(occurrences[1])),
                CombatPair(id(31_002), listOf(occurrences[2]), listOf(occurrences[3])))),
            projectionLinks = occurrences.indices.map { n -> ProjectionLink(id(32_000 + n), occurrences[n].id, occurrences[(n + 1) % 4].id) }))
    }

    private fun densePlacements(game: GameSummary, play: PlayState, width: Float, height: Float): Map<String, TablePlacement> {
        val scene = TablePresentation.scene(game, play, id(7), width, height, false)
        return scene.fields.flatMap { field ->
            play.board.containers.single { it.id == field.containerId }.cards.mapIndexed { index, card ->
                card to TablePlacement((index % 20) * (field.bounds.width - 68f).coerceAtLeast(0f) / 19 / 84,
                    (index / 20) * (field.bounds.height - 84f).coerceAtLeast(0f) / 4 / 84)
            }
        }.toMap()
    }

    private fun historyFixture(count: Int, branches: Int): GameplayTree {
        val root = root()
        val nodes = linkedMapOf<String, GameplayMutation>()
        val heads = linkedMapOf<String, String>()
        val lineage = linkedMapOf<String, GameplayBranch>()
        var request = 1_000
        var last = root
        repeat(branches) { branch ->
            val branchId = if (branch == 0) root.root.branchId else id(500_000 + branch)
            var state = root.copy(currentBranchId = branchId)
            var first: GameplayMutation? = null
            repeat(count / branches) {
                val command = GameCommand(id(request++), GameScope(root.gameId, playId = root.id),
                    GameAction.Play(PlayAction.AdjustLife(id(7), "1")), id(80), "1")
                val change = GameplayRules.change(state, id(7), command)
                state = checkNotNull(change.state)
                val node = checkNotNull(change.mutation)
                if (first == null) first = node
                nodes[node.id] = node
            }
            heads[branchId] = state.currentNodeId
            if (branch != 0) lineage[branchId] = GameplayBranch(branchId, root.root.id, first!!.id,
                first!!.requestId, id(7), heads.getValue(root.root.branchId), root.root.branchId)
            last = state
        }
        return GameplayTree(root, nodes, last.currentNodeId, branches = lineage, currentBranchId = last.currentBranchId, branchHeads = heads)
    }

    private fun percentile(values: List<Long>, p: Double): Long {
        require(values.isNotEmpty())
        return values.sorted()[ceil(values.size * p).toInt() - 1]
    }

    private fun JsonObjectBuilder.series(name: String, values: List<Long>) {
        putJsonObject(name) {
            put("samples", values.size)
            if (values.isNotEmpty()) { put("p95", percentile(values, .95)); put("p99", percentile(values, .99)) }
            putJsonArray("raw") { values.forEach { add(JsonPrimitive(it)) } }
        }
    }

    private fun output(metric: String, source: String, values: JsonObject) {
        val json = buildJsonObject {
            put("workload", "I33-v1"); put("layer", "LOWER-LAYER-NOT-E2E"); put("metric", metric)
            put("executionId", executionId)
            put("sourceCommit", source); put("profile", "runtime-and-verification-profile")
            put("deviceModel", Build.MODEL); put("buildFingerprint", Build.FINGERPRINT)
            put("sdk", Build.VERSION.SDK_INT); put("hardware", Build.HARDWARE)
            put("emulatorHeuristic", Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu") || Build.FINGERPRINT.contains("generic"))
            put("physicalDeviceQualification", "external lab identity required; emulator is not physical evidence")
            put("abi", Build.SUPPORTED_ABIS.joinToString(",")); put("heapLimitBytes", Runtime.getRuntime().maxMemory())
            put("screenPixels", "${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}")
            put("density", context.resources.displayMetrics.density); put("observations", values)
        }
        val directory = File(context.noBackupFilesDir, "managed-20261023-write-i33-01-observations").apply { check(isDirectory || mkdir()) }
        File(directory, "observations.jsonl").appendText("$json\n")
        println(json) // identifiers, times and counts only; never card/deck/chat/account payloads
    }
}
