package org.asyncttwiz.server.database

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock

/**
 * Server test classes run in parallel JVM forks. Container creation, stop/start, pause and
 * removal stay one at a time across all forks, as they were when the suite ran in one JVM:
 * the WSL/Docker port publication those tests exercise must not race another fork's container.
 * Re-entrant within a JVM; the OS releases the file lock if a fork dies.
 */
internal object ContainerLifecycleLock {
    private val file = File(System.getProperty("asyncttwiz.containerLock")
        ?: File(System.getProperty("java.io.tmpdir"), "asyncttwiz-owned-postgres.lock").path)
    private var holders = 0
    private var channel: RandomAccessFile? = null
    private var lock: FileLock? = null

    @Synchronized fun acquire() {
        if (holders++ > 0) return
        try {
            file.parentFile?.mkdirs()
            val opened = RandomAccessFile(file, "rw")
            channel = opened
            lock = opened.channel.lock()
        } catch (failure: Throwable) {
            holders--
            channel?.close(); channel = null
            throw failure
        }
    }

    @Synchronized fun release() {
        check(holders > 0)
        if (--holders > 0) return
        lock?.release(); lock = null
        channel?.close(); channel = null
    }

    fun <T> holding(work: () -> T): T { acquire(); try { return work() } finally { release() } }
}
