package org.asyncttwiz.protocol

/** A permission calculation, not evidence of a logged inspection or a cache-based disclosure grant. */
object ManualDisclosure {
    fun ownRegion(state: PlayState, card: PlayCard, viewer: String): Boolean = ownRegion(state.board, card, viewer)

    /** [ownRegion] on [board] (a board drawn for a game on engine state). */
    fun ownRegion(board: BoardState, card: PlayCard, viewer: String): Boolean =
        board.containers.singleOrNull { it.id == card.containerId }?.accountId == viewer

    /** A face-down card outside Hand/Library (Battlefield, Graveyard, Exile, or a frozen snapshot). */
    fun faceDownOutsideHiddenZones(board: BoardState, card: PlayCard): Boolean = card.isFaceDown &&
        board.containers.singleOrNull { it.id == card.containerId }?.zone.let { it != PlayZone.HAND && it != PlayZone.LIBRARY }

    /** By default an opponent never sees a face-down card's front: ordinary inspection, menus and history keep it
     * hidden. Only the deliberate, logged InspectFace ("Inspect other side") shows it to the inspecting player.
     * "Own" is the D-252 region rule: the zone's holder. */
    fun faceDownFrontHidden(state: PlayState, card: PlayCard, viewer: String): Boolean = faceDownFrontHidden(state.board, card, viewer)

    /** [faceDownFrontHidden] on [board] (a board drawn for a game on engine state). */
    fun faceDownFrontHidden(board: BoardState, card: PlayCard, viewer: String): Boolean =
        faceDownOutsideHiddenZones(board, card) && !ownRegion(board, card, viewer)

    fun permittedFaces(state: PlayState, card: PlayCard, viewer: String, inspecting: Boolean): List<DefinitionFace> =
        permittedFaces(state.board, state.definitions, card, viewer, inspecting)

    /** [permittedFaces] on [board] with its card data [definitions] (a board drawn for a game on engine state).
     * Inspecting a card the viewer sees face up (or one in their own region) shows every face of it (TBL-106: inspect
     * shows every face); otherwise only the face it shows. */
    fun permittedFaces(board: BoardState, definitions: List<SubmittedDefinition>, card: PlayCard, viewer: String,
        inspecting: Boolean): List<DefinitionFace> {
        val faces = definitions.singleOrNull { it.digest == card.definition }?.definition?.faces.orEmpty()
        val visible = CardVisibility.isVisibleFaceUp(board, card, viewer)
        return if (inspecting && (visible || ownRegion(board, card, viewer))) faces
        else if (visible) faces.filter { it.faceId == card.faceId } else emptyList()
    }
}
