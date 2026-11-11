package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import org.asyncttwiz.storage.*

/**
 * The card records (docs/specs/web-client.md sections 3.5, 3.6): [installed] the records in use once they are the app's
 * catalog ([ready]); [progress] while it installs; [failed] when the download failed twice (Retry).
 */
data class CatalogUiState(val installed: CatalogMetadata? = null, val ready: Boolean = false,
    val progress: CatalogProgress? = null, val failed: Boolean = false) {
    /** What the screens show: controls reading card records are greyed until they are [CardRecords.Ready]. */
    val records: CardRecords get() = when {
        ready -> CardRecords.Ready
        failed -> CardRecords.Failed
        else -> CardRecords.Updating(progress?.percent ?: 0)
    }
}

/** The app's card records: installed from the app's server, read by the screens (Android: [CatalogController]). */
interface CardRecordsController {
    /** The card records on this device. */
    val repository: CardCatalog
    val state: CatalogUiState

    /** Starts the check (and an installation when needed) unless it is running or the records are ready. Main thread. */
    fun start()
    fun retry()
    /** Tests: shows [value] as the state. */
    fun seed(value: CatalogUiState)
    fun close()

    /** Deck lists resolved against [repository]. */
    fun deckResolver(): DeckResolver
    /** Cube lists resolved against [repository]. */
    fun cubeResolver(): CubeResolver
}

/** The app's [CardRecordsController] (MainActivity starts it on resume, [foreground]): the platform's
 * ([AppServices.catalogController]), or another one (end-to-end tests: a phone's own directory and the test server). */
class CatalogViewModel constructor(val controller: CardRecordsController) : ViewModel() {
    val repository = controller.repository
    val state: CatalogUiState get() = controller.state

    fun foreground() = controller.start()
    fun retry() = controller.retry()
    fun seed(value: CatalogUiState) = controller.seed(value)

    override fun onCleared() = controller.close()
}
