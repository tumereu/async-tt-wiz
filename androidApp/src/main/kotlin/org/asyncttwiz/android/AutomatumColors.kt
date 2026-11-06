package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color

/** The Automatum violet (emblem chips use it too) and the cyan of the castable cards' glow (integration spec §5). */
internal val AutomatumViolet = EmblemChipColor
internal val AutomatumCyan = Color(0xff1fb8cf)
/** TBL-122 (owner 2026-11-05, proposed): the glow of a battlefield group a tap opens rather than acts on: white, set off
 * the light table by a thin grey edge; distinct from the cyan, violet and red-orange glows. */
internal val GroupExpandGlow = Color.White
internal val GroupExpandEdge = Color(0xff7d8a88)

/** TRN-059 (owner playtest 2026-11-06): the response mode and Assume resolves cells fill by how much control you keep:
 * deep blue = Control / Assume: Never, violet = Normal / Assume: No mana, amber = Quick / Assume: Always; white text on
 * each passes contrast (8.5:1, 5.6:1, 4.8:1). */
internal val ControlBlue = Color(0xff24489c)
internal val QuickAmber = Color(0xffa86200)
