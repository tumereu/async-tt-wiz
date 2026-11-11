package org.asyncttwiz.android

/** Fixed allocations of the bottom bar, including on short landscape tables. */
internal const val TableTimelineHeight = 48f
internal const val TableControlsHeight = 56f
/** The bottom bar's height: the controls row; in portrait timeline mode ([timeline]) its strip above them too (HIS-001:
 * play mode has no event strip, its room goes to the board). In landscape the strip sits beside the controls. */
internal fun tableBottomHeight(landscape: Boolean, timeline: Boolean = false) =
    if (landscape || !timeline) TableControlsHeight else TableTimelineHeight + TableControlsHeight
