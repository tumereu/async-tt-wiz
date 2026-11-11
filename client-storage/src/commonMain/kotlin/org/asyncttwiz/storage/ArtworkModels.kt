package org.asyncttwiz.storage

/** Retained definition consumers can construct this without consulting a newer local catalog.
 * Face is an explicit provider face index, not a name search or first-face guess.
 * These fields are what tells two pictures apart: a card's definition or the catalog's edition does not (LOB-097).
 * [faceName] stays because it can pick another picture than [face] alone (meld results, misnumbered faces). */
data class ArtworkReference(val printing: String?, val face: Int,
    val size: String = "normal", val customUrl: String? = null, val faceName: String? = null) {
    init { require(face in 0..15 && size in listOf("small", "normal", "large", "png", "art_crop")) }
    val key: String get() = "$printing|$face|$size|$customUrl|$faceName"
    companion object
}

/** One artwork network request. [failure] is null when it produced usable bytes. */
data class ArtworkAttempt(val image: Boolean, val url: String, val failure: String?, val millis: Long,
    val bytes: Int, val failures: Int, val retryInMillis: Long)

/** Diagnostics for every artwork download; the default records nothing. */
interface ArtworkLog {
    fun attempt(attempt: ArtworkAttempt) {}
    /** A load that ended without art for a reason other than a failed request. */
    fun failed(reference: ArtworkReference, failure: Throwable) {}
    companion object { val NONE = object : ArtworkLog {} }
}

data class ArtworkResponse(val status: Int, val bytes: ByteArray = byteArrayOf(), val type: String = "", val retryAt: Long = 0)

fun interface ArtworkTransport {
    fun get(url: String, maximum: Long): ArtworkResponse
}

enum class ArtworkPriority { VISIBLE, PREFETCH }
