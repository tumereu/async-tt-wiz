package org.asyncttwiz.android

import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtworkInitializationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun firstArtworkUseInitializesAndTrimsTheCacheOffMainBeforePublishing() = runBlocking {
        val root = temporary.newFolder()
        val directory = File(root, "public-artwork").apply { mkdirs() }
        val partial = File(directory, "${"a".repeat(64)}.art.part").apply { writeText("interrupted") }
        val oversized = File(directory, "${"b".repeat(64)}.art").apply {
            java.io.RandomAccessFile(this, "rw").use { it.setLength(256L * 1024 * 1024 + 1) }
        }
        var cacheThread: Thread? = null
        val cacheLookups = AtomicInteger()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File {
                cacheLookups.incrementAndGet(); cacheThread = Thread.currentThread(); return root
            }
        }
        assertSame(Looper.getMainLooper().thread, Thread.currentThread())
        // A fresh process owner, independent of services warmed by other Robolectric tests.
        val provider = ArtworkServiceProvider()
        val services = List(8) { async { provider.get(context) } }.awaitAll()
        val service = services.first()
        try {
            assertFalse(partial.exists(), "partial writes must be removed before publication")
            assertFalse(oversized.exists(), "the startup cache budget must be enforced before publication")
            assertNotSame(Looper.getMainLooper().thread, cacheThread, "cache startup must run on IO, not the caller's main thread")
            assertEquals(1, cacheLookups.get(), "simultaneous first consumers must share initialization")
            assertTrue(services.all { it === service })
            assertSame(service, provider.get(context))
        } finally { service.close() }
    }
}
