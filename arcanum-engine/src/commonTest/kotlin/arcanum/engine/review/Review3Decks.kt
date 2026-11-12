package arcanum.engine.review

import arcanum.engine.state.PlayerId

/**
 * Review 3: four themed 40-card decks of invented cube cards (16 basics of two colours and 24 cards), for long games
 * between each pair ([LongGame]).
 */
internal object Review3Decks {
    private fun lands(a: String, b: String) = List(8) { a } + List(8) { b }

    /** Artifacts (blue-red and colourless): equipment, thopters, counters, artifact payoffs. */
    val artifacts: List<String> = lands("Island", "Mountain") + listOf(
        "Tinkered Mite", "Gearwing Glider", "Sun Band", "Lotus Frond", "Prismatic Mote", "Skull Bracer", "Bonevise",
        "Runner's Skiff", "Tidal Spellcharge", "Cinder Spellcharge", "Iron Foreman", "Uniskelion", "Striding Arbalest",
        "Lord of the Aetherforge", "Tavi, Thopter Savant", "Cogitating Sentry", "Scrapquilt Automaton", "Volatile Myr",
        "Myr Gleaner", "Scrap Dredger", "Hangar Strider", "Goblin Solderer", "Refitting Workshop", "Ondra, Lurker of the Mere",
    )

    /** Graveyard (black-green): mill, recursion, dies triggers, cards that work from the graveyard. */
    val graveyard: List<String> = lands("Swamp", "Forest") + listOf(
        "Festering Gremlin", "Stitchwork Porter", "Undying Valet", "Wreckage Analyst", "Satyr Pathseeker", "Gorehaunt",
        "Bloodsworn Duelist", "Tombscuttler", "Netherbound Turncoat", "Kettle Kitten", "Stairway from the Depths",
        "Fiend Sculptor", "Wight of the Relic Vault", "Corpse Gnawer", "Plague Shamblers", "Unyielding Dead",
        "Rebirth from the Mire", "Shadowsting", "Neckless Horseman", "Undead Soothsayer", "Burrowed Shambler",
        "Shuffling Wight", "Sinister Rootwork", "Undying Chronicler",
    )

    /** Spells (blue-red): burn, cantrips, counters, copies, storm, cast triggers. */
    val spells: List<String> = lands("Island", "Mountain") + listOf(
        "Storm Spark", "Cinder Bolt", "Magma Needle", "Flash Surge", "Grindstone Rasp", "Stormforge Charm",
        "Faithless Rummage", "Mull It Over", "Glance Ahead", "Foretell the Way", "Thought Siphon", "Mind Chill",
        "Linked Lightning", "Flash of Mirroring", "Mounting Retribution", "Novice Flamecaller", "Wyrm's Fury Conduit",
        "Ysolde, the Unbound Scholar", "Grubb, the Fingerless", "Expanse // Eruption", "Denial", "Spell Prick", "Click Back",
        "Twincast Adept",
    )

    /** Creatures (green-white): elves, counters, tokens, combat. */
    val creatures: List<String> = lands("Forest", "Plains") + listOf(
        "Elvish Seer", "Thicketwood Elves", "Canopy Visionary", "Elvish Hierophant", "Bough-Crowned Oracle",
        "Elvish Drillmaster", "Haven Warden", "Larder Ouphes", "Loyal Hearthhound", "Hollowell Investigator",
        "Wall of Portents", "Golem Grafter", "Torvanna, Elder-Tree Spirit", "Radiant Acolyte", "Brisa, Bounding Shepherd",
        "Aldric, Fist of the Seraphs", "Seasoned Pathfinder", "Sunning Mudlizard", "Bestower of Sigils", "Commanding Orator",
        "Ranger-Marshal of Dawn", "Elenwyn, Spinner of Dawn", "Knight of the Relic Vault", "Podrick Burrowfoot",
    )

    val all: Map<String, List<String>> = mapOf("artifacts" to artifacts, "graveyard" to graveyard, "spells" to spells, "creatures" to creatures)

    /** Every pair of decks, alice's first. */
    val pairs: List<Pair<String, String>> = all.keys.toList().let { k -> k.flatMapIndexed { i, a -> k.drop(i + 1).map { a to it } } }

    fun match(a: String, b: String): Map<PlayerId, List<String>> =
        mapOf(PlayerId("alice") to all.getValue(a), PlayerId("bob") to all.getValue(b))
}
