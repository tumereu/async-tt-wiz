package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.LibraryLayers
import org.asyncttwiz.protocol.PlayContainer

/** Test views over the library layer model: the known-order block at each edge and what lies between. */
internal val PlayContainer.knownTop: List<String> get() = LibraryLayers.knownTop(this)
internal val PlayContainer.knownBottom: List<String> get() =
    if (layers.size == 1) emptyList() else LibraryLayers.knownBottom(this)
internal val PlayContainer.unknownMiddle: List<String> get() = cards.subList(knownTop.size, cards.size - knownBottom.size)
