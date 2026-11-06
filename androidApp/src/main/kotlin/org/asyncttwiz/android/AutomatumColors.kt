package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color

/** The Automatum violet (emblem chips use it too) and the cyan of the castable cards' glow (integration spec §5). */
internal val AutomatumViolet = EmblemChipColor
internal val AutomatumCyan = Color(0xff1fb8cf)
/** TBL-122 (owner 2026-11-05, proposed): the glow of a battlefield group a tap opens rather than acts on: white, set off
 * the light table by a thin grey edge; distinct from the cyan, violet and red-orange glows. */
internal val GroupExpandGlow = Color.White
internal val GroupExpandEdge = Color(0xff7d8a88)
