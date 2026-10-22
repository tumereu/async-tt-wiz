package org.asyncttwiz.android

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.storage.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Uses the ordinary source/catalog/service/Coil/BitmapFactory in the target app process.
 * Synthetic account/game actions are neither seeded nor bypassed. */
class ArtworkProductionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val original get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun pendingFetchRebindAndDisposeNeverPublishesOldImage() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val origin = "http://127.0.0.1:${server.localPort}"
        val bitmap = Bitmap.createBitmap(16, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val responder = thread(name = "artwork-delayed-test") {
            try {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* read ordinary HTTP headers */ }
                    entered.countDown()
                    check(release.await(20, TimeUnit.SECONDS))
                    socket.getOutputStream().use { output ->
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(bytes)
                    }
                }
            } catch (_: java.io.IOException) { /* client disposal may cancel the socket */ }
            finally { finished.countDown() }
        }
        val directory = File(original.cacheDir, "artwork-pending-${UUID.randomUUID()}")
        val service = CardArtworkService(original, directory, ArtworkUrlPolicy(origin), "$origin/cards/")
        val reference = mutableStateOf<ArtworkReference?>(ArtworkReference(null, 0, "pending", customUrl = "$origin/delayed"))
        val visible = mutableStateOf(true)
        try {
            compose.activityRule.scenario.onActivity { activity -> activity.setContent {
                if (visible.value) CardArtwork(reference.value, "Current text fallback", 32, 48,
                    Modifier.size(120.dp, 180.dp), service)
            } }
            compose.waitForIdle() // Drive the test-clock's initial composition before waiting for IO.
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            compose.runOnIdle { visible.value = false }
            compose.runOnIdle { reference.value = null; visible.value = true }
            compose.onNodeWithText("Current text fallback").assertIsDisplayed()
            release.countDown()
            assertTrue(finished.await(10, TimeUnit.SECONDS))
            compose.waitForIdle()
            compose.onNodeWithText("Current text fallback").assertIsDisplayed()
            compose.onNodeWithContentDescription("Current text fallback").assertDoesNotExist()
            println("PASS pending actual HTTP/Coil request disposed and rebound; late old bitmap cannot replace current plain text")
        } finally {
            release.countDown(); server.close(); responder.join(10_000)
            compose.runOnIdle { visible.value = false }
            service.close()
            directory.walkBottomUp().forEach { check(it.delete()) }
        }
    }

    @Test fun actualCatalogFacesCacheDecoderAndPresentationLifecycle() {
        check(BuildConfig.DEBUG && BuildConfig.ART_TEST_ORIGIN.isNotEmpty())
        assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
        val owned = File(original.noBackupFilesDir, "artwork-test-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val context = object : ContextWrapper(original) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = owned
        }
        val source = HttpCatalogSource(BuildConfig.CATALOG_BASE, BuildConfig.CATALOG_TEST_ORIGIN)
        val catalog = AndroidCatalogStorage.repository(context)
        val metadata = CatalogInstaller(AndroidCatalogStorage.directory(context), catalog, source).install(source.metadata(), source.size())
        // Query ordinary name/printing identities from this synthetic source; no runtime fixture roles.
        val records = catalog.find("Dayglass Loom // Nightglass Weave")
        assertEquals(2, records.size)
        val references = records.sortedBy { it.side }.map { ArtworkReference.from(it, metadata.date + metadata.checksum)!! }
        val directory = File(owned, "art")
        var service = CardArtworkService(original, directory)
        val first = service.repository.load(references[0])!!
        val second = service.repository.load(references[1])!!
        assertFalse(first.contentEquals(second))
        assertNotNull(ArtworkDecoder.bounds(first))
        val request = CardArtworkRequest(references[0], 24, 32)
        fun load(request: CardArtworkRequest): SuccessResult = runBlocking {
            service.loader.execute(ImageRequest.Builder(context).data(request).size(request.width, request.height).build())
        }.let { assertTrue(it.toString(), it is SuccessResult); it as SuccessResult }
        val result = load(request)
        assertTrue(result.image.width <= 48)
        assertTrue(result.image.height <= 64)
        assertEquals(coil3.decode.DataSource.MEMORY_CACHE, load(request).dataSource)
        assertEquals(minOf(64L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8), service.memoryBudget)
        assertTrue(service.loader.memoryCache!!.size <= service.memoryBudget)
        assertEquals(256L * 1024 * 1024, service.disk.budget)
        service.close()
        service = CardArtworkService(original, directory)
        assertArrayEquals(first, service.repository.load(references[0]))
        assertArrayEquals(second, service.repository.load(references[1]))
        // Bounds are checked without allocating a huge bitmap, even with a plausible PNG signature.
        val oversized = first.copyOf()
        ByteBuffer.wrap(oversized, 16, 8).putInt(50_000).putInt(50_000)
        assertNull(ArtworkDecoder.bounds(oversized))
        assertNull(ArtworkDecoder.decode(oversized, 24, 32, service.memoryBudget))
        assertNull(ArtworkDecoder.bounds("<svg onload='x'/>".toByteArray()))
        assertNull(ArtworkDecoder.bounds(ByteArray(HttpArtworkRepository.MAX_IMAGE.toInt() + 1)))
        val large = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val largeBytes = ByteArrayOutputStream().also { large.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        large.recycle()
        val downsampled = ArtworkDecoder.decode(largeBytes, 32, 32, service.memoryBudget)!!
        assertEquals(32, downsampled.width); downsampled.recycle()

        val reference = mutableStateOf<ArtworkReference?>(references[0])
        val visible = mutableStateOf(true)
        val fallback = mutableStateOf("Front face")
        try {
            compose.activityRule.scenario.onActivity { activity ->
                activity.setContent {
                    Column {
                        if (visible.value) CardArtwork(reference.value, fallback.value, 64, 96,
                            Modifier.size(120.dp, 180.dp), service)
                    }
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Front face")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Front face").assertIsDisplayed()
            compose.runOnIdle { reference.value = references[1]; fallback.value = "Back face" }
            compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Back face")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Back face").assertIsDisplayed()
            compose.onNodeWithContentDescription("Front face").assertDoesNotExist()
            compose.runOnIdle { reference.value = ArtworkReference(null, 0, "missing"); fallback.value = "Missing artwork — plain text" }
            compose.onNodeWithText("Missing artwork — plain text").assertIsDisplayed()
            compose.runOnIdle { visible.value = false; reference.value = references[0] }
            compose.runOnIdle { visible.value = true; fallback.value = "Rebound face" }
            compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Rebound face")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Rebound face").assertIsDisplayed()
            println("PASS actual API29 catalog -> exact front/back hosted artwork, Coil/bitmap downsampling, bounds, memory/disk/restart cache, rebind/dispose and visible native text fallback")
        } finally {
            compose.runOnIdle { visible.value = false }
            service.close()
            owned.walkBottomUp().forEach { check(it.delete()) }
        }
    }
}
