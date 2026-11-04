package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

enum class PlaybackAutoPause(val label: String, val defaultEnabled: Boolean = true) {
    STACK_ADDED("Added to Stack"),
    STACK_REMOVED("Removed from Stack", false),
    REVEAL("Reveal"),
    /** An opponent's change to your life or your player counters. */
    LIFE_HIT("Your life hit", false),
    /** An opponent's action on a card you own or control (also its combat copies and Stack entries) or one giving you a card. */
    CARDS_HIT("Your cards hit"),
    ATTENTION("Move passes to you"),
    ATTACKERS("Attackers declared", false),
    LAND_FROM_HAND("Opponent plays a land", false),
    NONLAND_FROM_HAND("Opponent puts a nonland card onto the battlefield from hand", false);

    companion object {
        val defaults = entries.filter { it.defaultEnabled }.toSet()
    }
}


