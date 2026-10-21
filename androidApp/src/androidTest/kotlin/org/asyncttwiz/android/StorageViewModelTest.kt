package org.asyncttwiz.android

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.asyncttwiz.storage.AndroidClientStorage
import org.asyncttwiz.storage.StorageOpenFailure
import org.asyncttwiz.storage.StorageOpenResult
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Controller/component checks supplement normal-launcher tests, never replace a device journey. */
class StorageViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val previousWorkers = Thread.getAllStackTraces().keys
    private val directory = File(instrumentation.targetContext.noBackupFilesDir,
        "storage-owner-test-${UUID.randomUUID()}").apply { check(mkdirs()) }
    // Only this test's private path differs; the ViewModel/opener/driver are production code.
    private val application = object : Application() {
        override fun getApplicationContext(): Context = this
        override fun getNoBackupFilesDir(): File = directory
    }
    private val file = AndroidClientStorage.databaseFile(application)
    private val store = ViewModelStore()

    private fun owner(): StorageViewModel {
        lateinit var model: StorageViewModel
        instrumentation.runOnMainSync {
            model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(application))[StorageViewModel::class.java]
        }
        return model
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(10)
        assertTrue("Bounded storage owner condition", condition())
    }

    private fun state(owner: StorageViewModel): StorageState {
        lateinit var result: StorageState
        instrumentation.runOnMainSync { result = owner.state }
        return result
    }

    private fun clearAndAwaitWorker(previous: Set<Thread>) {
        val workers = Thread.getAllStackTraces().keys.filter { it.name == "client-storage" && it !in previous }
        assertEquals(1, workers.size)
        instrumentation.runOnMainSync { store.clear() }
        await { workers.none { it.isAlive } }
    }

    @After fun cleanup() {
        instrumentation.runOnMainSync { store.clear() }
        await { Thread.getAllStackTraces().keys.none { it.name == "client-storage" && it !in previousWorkers && it.isAlive } }
        // Only this UUID directory is owned; never delete the launcher's actual database.
        directory.walkBottomUp().forEach { assertTrue(it.delete()) }
    }

    @Test fun actualOpenFailureIsExplicitAndRetryPreservesFileThenReopens() {
        file.parentFile!!.mkdirs()
        val bytes = "synthetic private corrupt data".toByteArray()
        file.writeBytes(bytes)
        val previous = Thread.getAllStackTraces().keys
        val owner = owner()
        await { state(owner) is StorageState.Failed }
        assertArrayEquals(bytes, file.readBytes())
        assertFalse(state(owner).toString().contains("private"))
        instrumentation.runOnMainSync { owner.retry() }
        await { state(owner) is StorageState.Failed }
        assertArrayEquals(bytes, file.readBytes())
        clearAndAwaitWorker(previous)
    }

    @Test fun unsupportedVersionRemainsFailedUntilSameFileIsCompatibleThenRetryWorks() {
        file.parentFile!!.mkdirs()
        instrumentation.context.assets.open("1.db").use { file.writeBytes(it.readBytes()) }
        SQLiteDatabase.openDatabase(file.absolutePath, null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { it.version = 900 }
        val before = file.readBytes()
        val previous = Thread.getAllStackTraces().keys
        val owner = owner()
        await { state(owner) is StorageState.Failed }
        assertEquals(StorageState.Failed(StorageOpenFailure.UNSUPPORTED_VERSION), state(owner))
        assertArrayEquals(before, file.readBytes())
        // Simulate a repaired compatibility input on the test-owned fixture, not app auto-repair.
        SQLiteDatabase.openDatabase(file.absolutePath, null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { it.version = 1 }
        instrumentation.runOnMainSync { owner.retry() }
        await { state(owner) == StorageState.Ready }
        clearAndAwaitWorker(previous)
        (AndroidClientStorage.open(file) as StorageOpenResult.Opened).storage.use {
            assertEquals(9007199254740993, it.accepted("replica-a")!!.revision)
        }
    }

    @Test fun clearingDuringOpenDrainsAndClosesOnWorkerWithoutLateUiPublication() {
        val previous = Thread.getAllStackTraces().keys
        val owner = owner()
        clearAndAwaitWorker(previous)
        val afterClear = state(owner)
        instrumentation.waitForIdleSync()
        assertEquals(afterClear, state(owner))
        // Real reopen after queue-drained close, not an in-memory fake or a second launcher.
        (AndroidClientStorage.open(file) as StorageOpenResult.Opened).storage.close()
    }
}
