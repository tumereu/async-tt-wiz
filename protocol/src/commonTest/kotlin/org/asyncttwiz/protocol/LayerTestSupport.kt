package org.asyncttwiz.protocol

/** Test views over the layer model: the known-order block at each edge and what lies between. */
internal val PlayContainer.knownTop: List<String> get() = LibraryLayers.knownTop(this)
internal val PlayContainer.knownBottom: List<String> get() =
    if (layers.size == 1) emptyList() else LibraryLayers.knownBottom(this)
internal val PlayContainer.unknownMiddle: List<String> get() = cards.subList(knownTop.size, cards.size - knownBottom.size)

/** A library whose top [top] and bottom [bottom] blocks are known and whose middle is one hidden layer. */
internal fun PlayContainer.withKnown(top: List<String> = emptyList(), bottom: List<String> = emptyList()): PlayContainer {
    require(cards.take(top.size) == top && cards.takeLast(bottom.size) == bottom)
    val middle = cards.subList(top.size, cards.size - bottom.size)
    fun layerId(part: String) = CommandIds.uuidV5(id, "test-layer/$part")
    return copy(layers = LibraryLayers.normalize(listOf(LibraryLayer(layerId("top"), true, top),
        LibraryLayer(layerId("middle"), false, middle), LibraryLayer(layerId("bottom"), true, bottom))))
}

/** Every library container with its whole content as one hidden layer (fixtures built without layers). */
internal fun PlayState.withLibraryLayers(): PlayState = copy(board = board.copy(containers = board.containers.map {
    if (it.zone == PlayZone.LIBRARY && !LibraryLayers.valid(it)) it.withKnown() else it }))
