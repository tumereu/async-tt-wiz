package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayState

/** The render model ([TableBoard]) of a test board: what the table draws of [play]'s board (no engine state, no worked-out
 * values), for the table's drawing, layout and gesture tests. */
internal fun tableBoard(play: PlayState): TableBoard =
    TableBoard(play.gameId, play.id, play.currentNodeId, play.board, play.definitions)
