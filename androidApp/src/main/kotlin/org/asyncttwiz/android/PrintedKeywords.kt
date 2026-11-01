package org.asyncttwiz.android

/** Keywords printed in a face's rules text (CMB-025). Only a line made of keyword abilities alone counts, such as
 * "Flying, first strike" or "Lifelink (reminder text)"; "Equipped creature has first strike" does not. Parts are
 * comma- or semicolon-separated and reminder text in parentheses is ignored. Each part must be a keyword ability,
 * optionally followed by its parameter ("Ward {2}", "Protection from red"), so lines mixing in such abilities still
 * count; a keyword is reported only when it is a whole part. Results are lower case. */
internal object PrintedKeywords {
    private val names = listOf("deathtouch", "defender", "double strike", "enchant", "equip", "first strike", "flash",
        "flying", "haste", "hexproof", "indestructible", "intimidate", "lifelink", "protection", "reach", "shroud",
        "trample", "vigilance", "ward", "banding", "rampage", "cumulative upkeep", "flanking", "phasing", "buyback",
        "shadow", "cycling", "echo", "horsemanship", "fading", "kicker", "multikicker", "flashback", "madness", "fear",
        "morph", "megamorph", "amplify", "provoke", "storm", "affinity", "entwine", "modular", "sunburst", "bushido",
        "soulshift", "splice", "offering", "ninjutsu", "commander ninjutsu", "epic", "convoke", "dredge", "transmute",
        "bloodthirst", "haunt", "replicate", "forecast", "graft", "recover", "ripple", "split second", "suspend",
        "vanishing", "absorb", "aura swap", "delve", "fortify", "frenzy", "gravestorm", "poisonous", "transfigure",
        "champion", "changeling", "evoke", "hideaway", "prowl", "reinforce", "conspire", "persist", "wither", "retrace",
        "devour", "exalted", "unearth", "cascade", "annihilator", "level up", "rebound", "totem armor", "umbra armor",
        "infect", "battle cry", "living weapon", "undying", "miracle", "soulbond", "overload", "scavenge", "unleash",
        "cipher", "evolve", "extort", "fuse", "bestow", "tribute", "dethrone", "hidden agenda", "double agenda",
        "outlast", "prowess", "dash", "exploit", "menace", "renown", "awaken", "devoid", "ingest", "myriad", "surge",
        "skulk", "emerge", "escalate", "melee", "crew", "fabricate", "partner", "undaunted", "improvise", "aftermath",
        "embalm", "eternalize", "afflict", "ascend", "assist", "jump-start", "mentor", "afterlife", "riot",
        "spectacle", "escape", "companion", "mutate", "encore", "boast", "foretell", "demonstrate", "daybound",
        "nightbound", "disturb", "decayed", "cleave", "training", "compleated", "reconfigure", "blitz", "casualty",
        "enlist", "read ahead", "ravenous", "squad", "space sculptor", "visit", "prototype", "living metal",
        "more than meets the eye", "for mirrodin!", "toxic", "backup", "bargain", "craft", "disguise", "solved",
        "plot", "saddle", "spree", "freerunning", "gift", "offspring", "impending", "exhaust", "max speed",
        "start your engines!", "harmonize", "mobilize", "job select", "tiered", "station", "warp")
    /** Keywords that never take a parameter: "Flying creatures you control have trample" is a sentence, not Flying. */
    private val bare = setOf("deathtouch", "defender", "double strike", "first strike", "flash", "flying", "haste",
        "indestructible", "intimidate", "lifelink", "menace", "reach", "shroud", "trample", "trample over planeswalkers",
        "vigilance", "infect", "wither", "prowess", "changeling", "devoid", "fear", "shadow", "horsemanship", "skulk",
        "persist", "undying", "exalted", "split second", "battle cry", "living weapon", "cascade", "convoke", "delve")
    private val reminder = Regex("\\([^()]*\\)")
    private val spaces = Regex("\\s+")
    /** Forestwalk, nonbasic landwalk and the like. */
    private val landwalk = Regex("(\\p{L}+ )?\\p{L}+walk")

    private fun keywordAbility(part: String) = landwalk.matches(part) || part in bare || names.any { name ->
        name !in bare && part.startsWith(name) && (part.length == name.length ||
            part[name.length].let { it == ' ' || it == '—' || it == '-' || it == ':' })
    }

    fun of(rulesText: String): Set<String> = rulesText.lines().flatMap { line ->
        val parts = line.replace(reminder, " ").replace('’', '\'').lowercase().split(',', ';')
            .map { it.replace(spaces, " ").trim() }.filter { it.isNotEmpty() }
        if (parts.isNotEmpty() && parts.all(::keywordAbility)) parts else emptyList()
    }.toSet()
}
