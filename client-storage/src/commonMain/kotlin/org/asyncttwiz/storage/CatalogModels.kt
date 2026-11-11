package org.asyncttwiz.storage

import org.asyncttwiz.protocol.Avatar
import org.asyncttwiz.protocol.CardSearchError

/** A draft set of the card records (DRF-088): [keyrune] its Keyrune set symbol (MTGJSON's keyruneCode, empty without
 * one), [automatum] whether Automatum can play every card its packs can hold, and
 * the names of those it can't ([unsupported], DRF-161), as the card catalog marks them (section 3.8). */
data class DraftableSet(val code: String, val name: String, val releaseDate: String, val cardCount: Long,
    val automatum: Boolean = false, val unsupported: List<String> = emptyList(), val keyrune: String = "")

class CatalogNotInstalledException : IllegalStateException("Card records are not installed. Download card records, then retry.")
class CatalogGenerationChangedException : IllegalStateException("Card records changed. Search again.")

enum class CatalogPhase { DOWNLOADING, INDEXING, FINISHING }

/** Downloading and indexing count the compressed file's bytes; finishing counts printings (its card search goes through
 * every one). */
data class CatalogProgress(val phase: CatalogPhase, val done: Long, val total: Long?) {
    /** How much of the whole installation is done, 0 to 99 ("Card records updating · 43%", section 3.6): the download is
     * the first [DOWNLOAD_SHARE] percent, indexing up to [FINISHING_FROM], finishing (the card search) the rest to 99. */
    val percent: Int get() {
        val fraction = total?.takeIf { it > 0 }?.let { (done.toDouble() / it).coerceIn(0.0, 1.0) } ?: 0.0
        return when (phase) {
            CatalogPhase.DOWNLOADING -> (fraction * DOWNLOAD_SHARE).toInt()
            CatalogPhase.INDEXING -> (DOWNLOAD_SHARE + fraction * (FINISHING_FROM - DOWNLOAD_SHARE)).toInt()
            CatalogPhase.FINISHING -> (FINISHING_FROM + fraction * (99 - FINISHING_FROM)).toInt()
        }
    }

    // After the download, the records and finishing took about as long each on an API 29 emulator (2026-11-10)
    private companion object { const val DOWNLOAD_SHARE = 20; const val FINISHING_FROM = 60 }
}

/** One search result: a card (oracle identity) shown with its preferred matching printing; faces front first. */
data class CatalogCard(val key: String, val name: String, val faces: List<CatalogPrinting>) {
    val printing: CatalogPrinting get() = faces.first()
}

/** A name of a card ([CatalogCard.namedParts]) with the type line of the part that has it. */
data class CatalogNamedPart(val name: String, val typeLine: String)
data class CatalogCardCursor(val generation: String, val query: String, val nameKey: String, val key: String)

sealed interface CatalogCardSearch {
    /** Cards sorted by name. [examined] counts card rows the matcher evaluated for this page (the work done).
     * A page may hold fewer than the limit and still have [next] when the work budget ran out first. */
    data class Page(val generation: String, val metadata: CatalogMetadata, val cards: List<CatalogCard>,
        val next: CatalogCardCursor?, val examined: Int) : CatalogCardSearch
    /** The query text cannot be searched; [error] says why and where. */
    data class Invalid(val error: CardSearchError) : CatalogCardSearch
}

/** Installed card records predate search; the regular catalog update replaces them. */
class CatalogSearchNotReadyException : IllegalStateException("Card search needs updated card records. Update card records, then retry.")

data class CatalogAvatar(val name: String, val avatar: Avatar)
