package org.asyncttwiz.android

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.asyncttwiz.storage.ArtworkReference

data class ArtworkPrefetchProgress(val done: Int, val total: Int)

/** Process-wide background artwork downloads. Each tagged batch (a saved deck, an open game)
 * replaces its previous version; the newest batch runs first. Bounded parallelism, one attempt per
 * reference: on-screen requests keep their own retry loop and network priority. */
internal class ArtworkPrefetchQueue(private val scope: CoroutineScope, private val parallelism: Int = 3,
    private val load: suspend (ArtworkReference) -> Boolean) {
    private class Batch(val tag: String, references: List<ArtworkReference>) {
        val queue = ArrayDeque(references)
        val total = references.size
        var done = 0
    }
    private val batches = ArrayList<Batch>() // newest first
    private var workers = 0
    private val state = MutableStateFlow<ArtworkPrefetchProgress?>(null)
    /** Null when idle. Finished batches still count until every batch is done, so it never runs backwards. */
    val progress: StateFlow<ArtworkPrefetchProgress?> = state

    fun submit(tag: String, references: List<ArtworkReference>) = synchronized(this) {
        batches.removeAll { it.tag == tag }
        val distinct = references.distinct()
        if (distinct.isNotEmpty()) batches.add(0, Batch(tag, distinct))
        publish()
        while (workers < parallelism && batches.any { it.queue.isNotEmpty() }) {
            workers++
            scope.launch { work() }
        }
    }

    fun cancel(tag: String) = synchronized(this) {
        if (batches.removeAll { it.tag == tag }) publish()
    }

    private suspend fun work() {
        try {
            while (true) {
                val (batch, reference) = synchronized(this) {
                    batches.firstOrNull { it.queue.isNotEmpty() }?.let { it to it.queue.removeFirst() }
                        ?: run { workers--; null }
                } ?: return
                try { load(reference) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
                synchronized(this) { batch.done++; publish() }
            }
        } catch (cancelled: CancellationException) {
            synchronized(this) { workers-- }
            throw cancelled
        }
    }

    private fun publish() {
        if (batches.all { it.done >= it.total }) batches.clear()
        state.value = if (batches.isEmpty()) null
            else ArtworkPrefetchProgress(batches.sumOf { it.done }, batches.sumOf { it.total })
    }
}

/** The inspector's text while its artwork is not shown. */
internal fun inspectorArtworkStatus(failed: Boolean, progress: ArtworkPrefetchProgress?): String = when {
    failed -> "Artwork unavailable"
    progress != null -> "Downloading artwork ${minOf(progress.done + 1, progress.total)} of ${progress.total}"
    else -> "Downloading artwork…"
}

/** Inspector placeholder: a card without any artwork source keeps its plain fallback text. */
@Composable
internal fun InspectorArtworkPlaceholder(reference: ArtworkReference?, fallback: String) {
    if (reference == null || (reference.printing == null && reference.customUrl == null)) { Text(fallback); return }
    val context = LocalContext.current
    val service by produceState(PublicArtwork.current(), context) { value = PublicArtwork.get(context) }
    val failed = service?.unavailable?.collectAsState()?.value?.contains(reference.key) == true
    val progress = service?.prefetch?.progress?.collectAsState()?.value
    Text(inspectorArtworkStatus(failed, progress))
}
