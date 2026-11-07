package org.asyncttwiz.android

import org.asyncttwiz.storage.ArtworkReference

// Web spike: split from TableArtwork.kt (androidApp) with the table drawing
enum class TableArtworkMode { TYPE_ICON, ART_CROP, CUSTOM_CARD }

/** A failed or pending custom image keeps the ordinary name/icon/corner fallback. */
fun tableArtworkMode(reference: ArtworkReference?, imageAvailable: Boolean): TableArtworkMode = when {
    reference == null || !imageAvailable -> TableArtworkMode.TYPE_ICON
    reference.customUrl != null -> TableArtworkMode.CUSTOM_CARD
    else -> TableArtworkMode.ART_CROP
}
