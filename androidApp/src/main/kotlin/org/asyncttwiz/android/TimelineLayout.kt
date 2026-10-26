package org.asyncttwiz.android

/** Shared, fixed allocations in play and review, including on short landscape tables. */
internal const val TableTimelineHeight = 48f
internal const val TableControlsHeight = 56f
internal fun tableBottomHeight(landscape: Boolean) = if (landscape) TableControlsHeight else TableTimelineHeight + TableControlsHeight
