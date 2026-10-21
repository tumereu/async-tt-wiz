package org.asyncttwiz.android

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.util.concurrent.Executors
import org.asyncttwiz.storage.AndroidClientStorage
import org.asyncttwiz.storage.ClientStorage
import org.asyncttwiz.storage.StorageOpenFailure
import org.asyncttwiz.storage.StorageOpenResult

sealed interface StorageState {
    data object Opening : StorageState
    data object Ready : StorageState
    data class Failed(val reason: StorageOpenFailure) : StorageState
}

/** Activity ViewModel survives rotation. All database work/close stays on one owned worker. */
class StorageViewModel(application: Application) : AndroidViewModel(application) {
    var state: StorageState by mutableStateOf(StorageState.Opening)
        private set
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "client-storage") }
    private var storage: ClientStorage? = null // Worker only; never handed to a Composable.
    private var cleared = false // Main thread only; fences queued UI callbacks after finishing.

    init { open() }

    fun retry() {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!cleared && state is StorageState.Failed) {
            state = StorageState.Opening
            open()
        }
    }

    private fun open() {
        worker.execute {
            val result = try {
                AndroidClientStorage.open(AndroidClientStorage.databaseFile(getApplication()))
            } catch (_: Exception) {
                StorageOpenResult.Failed(StorageOpenFailure.OPEN_FAILED)
            }
            val next = when (result) {
                is StorageOpenResult.Opened -> {
                    storage = result.storage
                    StorageState.Ready
                }
                is StorageOpenResult.Failed -> StorageState.Failed(result.reason)
            }
            main.post { if (!cleared) state = next }
        }
    }

    override fun onCleared() {
        cleared = true
        // Queue behind any in-progress open; no wait/join or database close on the UI thread.
        worker.execute {
            try { storage?.close() } catch (_: Exception) {
                // The owner is gone. Do not crash the process or log private database inputs.
            } finally { storage = null }
        }
        worker.shutdown()
    }
}
