package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color

/** The Automatum violet (emblem chips use it too) and the cyan of the castable cards' glow (integration spec §5). */
val AutomatumViolet = EmblemChipColor
val AutomatumCyan = Color(0xff1fb8cf)
/** TBL-122 (owner 2026-11-05, proposed): the glow of a battlefield group a tap opens rather than acts on: white, set off
 * the light table by a thin grey edge; distinct from the cyan, violet and red-orange glows. */
val GroupExpandGlow = Color.White
val GroupExpandEdge = Color(0xff7d8a88)

/** TRN-059 (owner playtest 2026-11-06): the response mode and Assume resolves cells fill by how much control you keep:
 * deep blue = Control / Assume: Never, violet = Normal / Assume: Normal, amber = Quick / Assume: No mana, red-orange =
 * Assume: Always (owner 2026-11-07: each Assume setting its own colour); white text on each passes contrast (8.5:1,
 * 5.6:1, 4.8:1, 5.9:1). */
val ControlBlue = Color(0xff24489c)
val QuickAmber = Color(0xffa86200)
val AssumeAlwaysRed = Color(0xffb23c17)

/** ZON-079 (owner, 2026-11-12): the zone badge of a hand row card from another zone (the piles' count bubbles' dark teal) and
 * the thin divider before those cards. */
val HandRowBadgeColor = Color(0xff345451)
val HandRowDividerColor = Color(0xff8a9296)
