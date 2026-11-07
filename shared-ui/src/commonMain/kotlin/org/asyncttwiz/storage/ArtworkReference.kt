package org.asyncttwiz.storage

// Web spike: moved from client-storage (ArtworkRepository.kt) so the shared table model can name it
/** Retained definition consumers can construct this without consulting a newer local catalog.
 * Face is an explicit provider face index, not a name search or first-face guess. */
data class ArtworkReference(val printing: String?, val face: Int, val version: String,
    val size: String = "normal", val customUrl: String? = null, val faceName: String? = null) {
    init { require(face in 0..15 && version.length <= 256 && size in listOf("small", "normal", "large", "png", "art_crop")) }
    val key: String get() = "$printing|$face|$size|$version|$customUrl|$faceName"
    companion object
}
