package org.asyncttwiz.automatum

import arcanum.engine.decision.ReplacementOption

/**
 * CR 616.1, 616.1f (owner gallery review, 2026-11-06): which replacement effects apply first is the player's preference
 * list, applied in order and skipping those no longer applicable; asked again only when a new one appears. An effect is
 * the same in a later question when its source and text are (the engine numbers the options of each question anew).
 */
object ReplacementPreference {
    /** The first of [preference] among [options]; null when one of [options] isn't in the list (a new one: asked). */
    fun preferred(options: List<ReplacementOption>, preference: List<ReplacementOption>): ReplacementOption? {
        val keys = preference.map(::key)
        if (options.isEmpty() || options.any { key(it) !in keys }) return null
        return options.minBy { keys.indexOf(key(it)) }
    }

    private fun key(option: ReplacementOption) = option.source to option.text
}
