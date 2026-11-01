// Generated from arcanum-parser/src/commonTest/kotlin/arcanum/parser/cube/Chunk*Test.kt by CubeCardsInSyncTest (jvmTest).
// Do not edit: ./scripts/gradle.sh :arcanum-engine:jvmTest -Parcanum.regenerate=true --tests '*CubeCardsInSyncTest*'
package arcanum.engine.cards

/** The invented cube cards of the parser tests (D15, D46): printed fields as written there, by chunk. */
internal object CubeCardTexts {
    val all: List<CubeCardText> by lazy {
        listOf(
            chunk01KeywordCreatures(),
            chunk02ManaLands(),
            chunk03UtilityLands(),
            chunk04SimpleMana(),
            chunk05TrickyMana(),
            chunk06ManaCreatures(),
            chunk07Counterspells(),
            chunk08BurnAndRemoval(),
            chunk09ModesAndCardFlow(),
            chunk10DrawDiscardAndTutors(),
            chunk11LookScryAndMill(),
            chunk12HarderCardFlow(),
            chunk13HandDisruptionAndRemoval(),
            chunk14CopiesStormAndGraveyardCasting(),
            chunk15ReturnsTriggersAndTurns(),
            chunk20EquipmentAndSacrificeArtifacts(),
            chunk21CountersTriggersAndLinkedArtifacts(),
            chunk25EnchantmentEngines(),
            chunk26TriggeredEnchantmentsAndPlaneswalkers(),
            chunk30EntersDrawAndLife(),
            chunk31EntersLibraryAndGraveyard(),
            chunk32EntersTutorsTokensAndBounce(),
            chunk33EntersRemoval(),
            chunk35DeathTriggers(),
            chunk36AttackAndCombatDamage(),
            chunk40PumpsEvasionAndTypeChanges(),
            chunk41CountersDamageAndArtifactEngines(),
            chunk42SacrificeEnginesAndTutorCreatures(),
            chunk45GraveyardCastingAndCantBlock(),
            chunk46StaticCreatures(),
            chunk47TriggeredCreatures(),
            chunk50GraveyardCreatures(),
            chunk51SacrificeAndReturnOutlets(),
            chunk52StaticsAndCastTriggers(),
            chunk55LeftoverManaCreatures(),
            chunk56MultiFacedCards(),
            chunk57UnCards(),
        ).flatten()
    }

    private fun chunk01KeywordCreatures() = listOf(
        CubeCardText(
            "Normal",
            face("Tinkered Mite", "{0}", "Artifact Creature — Construct", "", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gearwing Glider", "{0}", "Artifact Creature — Thopter", "Flying", "0/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Enamel Phalanxer", "{2}{W/P}", "Artifact Creature — Phyrexian Knight", "({W/P} can be paid with either {W} or 2 life.)\nFirst strike", "3/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Festering Gremlin", "{1}{B}", "Creature — Zombie Gremlin", "Persist (When this creature dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Haven Warden", "{1}{G/W}", "Creature — Elf Soldier", "Persist (When this creature dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Splintered Envoy", "{1}{G}{U}", "Artifact Creature — Human Spy", "Cascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Crypt Flitter", "{1}{B/P}", "Artifact Creature — Phyrexian Bat", "({B/P} can be paid with either {B} or 2 life.)\nFlying\nLifelink (Damage dealt by this creature also causes you to gain that much life.)", "1/1", null, null),
        ),
    )

    private fun chunk02ManaLands() = listOf(
        CubeCardText(
            "Normal",
            face("Sunbaked Mesa", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for a Mountain or Plains card, put it onto the battlefield, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Cinder-Soaked Bog", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for a Swamp or Mountain card, put it onto the battlefield, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Tidewashed Shore", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for a Plains or Island card, put it onto the battlefield, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gloomlit Fen", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for a Plains or Swamp card, put it onto the battlefield, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mistveil Canopy", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for a Forest or Island card, put it onto the battlefield, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Brackish Estuary", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for an Island or Swamp card, put it onto the battlefield, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Steaming Caldera", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for an Island or Mountain card, put it onto the battlefield, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mossy Ossuary", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for a Swamp or Forest card, put it onto the battlefield, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gusting Moorland", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for a Forest or Plains card, put it onto the battlefield, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Timbered Ridge", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for a Mountain or Forest card, put it onto the battlefield, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Refracting Overlook", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ashen Wastes", null, "Land — Swamp Mountain", "({T}: Add {B} or {R}.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Blackwater Mire", null, "Land — Swamp Forest", "({T}: Add {B} or {G}.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Highland Mesa", null, "Land — Mountain Plains", "({T}: Add {R} or {W}.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Golden Grassland", null, "Land — Forest Plains", "({T}: Add {G} or {W}.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Thornbrush Flats", null, "Land — Plains Swamp", "({T}: Add {W} or {B}.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Frostpine Slopes", null, "Land — Mountain Forest", "({T}: Add {R} or {G}.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Palmfringe Atoll", null, "Land — Forest Island", "({T}: Add {G} or {U}.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Glacial Plain", null, "Land — Plains Island", "({T}: Add {W} or {U}.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Sunken Grotto", null, "Land — Island Swamp", "({T}: Add {U} or {B}.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Smoldering Isle", null, "Land — Island Mountain", "({T}: Add {U} or {R}.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gore Vault", null, "Land — Swamp Mountain", "({T}: Add {B} or {R}.)\nAs this land enters, you may pay 2 life. If you don't, it enters tapped.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Spawning Basin", null, "Land — Forest Island", "({T}: Add {G} or {U}.)\nAs this land enters, you may pay 2 life. If you don't, it enters tapped.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Heretic's Chapel", null, "Land — Plains Swamp", "({T}: Add {W} or {B}.)\nAs this land enters, you may pay 2 life. If you don't, it enters tapped.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Consecrated Spring", null, "Land — Plains Island", "({T}: Add {W} or {U}.)\nAs this land enters, you may pay 2 life. If you don't, it enters tapped.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ivy-Choked Crypt", null, "Land — Swamp Forest", "({T}: Add {B} or {G}.)\nAs this land enters, you may pay 2 life. If you don't, it enters tapped.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Holy Forge", null, "Land — Mountain Plains", "({T}: Add {R} or {W}.)\nAs this land enters, you may pay 2 life. If you don't, it enters tapped.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Geyser Fissure", null, "Land — Island Mountain", "({T}: Add {U} or {R}.)\nAs this land enters, you may pay 2 life. If you don't, it enters tapped.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Trampled Glade", null, "Land — Mountain Forest", "({T}: Add {R} or {G}.)\nAs this land enters, you may pay 2 life. If you don't, it enters tapped.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Cloister Grove", null, "Land — Forest Plains", "({T}: Add {G} or {W}.)\nAs this land enters, you may pay 2 life. If you don't, it enters tapped.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Drowned Barrow", null, "Land — Island Swamp", "({T}: Add {U} or {B}.)\nAs this land enters, you may pay 2 life. If you don't, it enters tapped.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Hearth of the Emberwillows", null, "Land", "{T}: Add {C}.\n{T}: Add {R} or {G}. Each opponent gains 1 life.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Oblation Spire", null, "Legendary Land", "{T}: Add {C}.\n{T}, Sacrifice a creature: Add {B}{B}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Quarry Gouge", null, "Land", "{T}: Add {C}.\n{T}, Sacrifice this land: Destroy target land.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Barren Expanse", null, "Land", "{T}: Add {C}.\n{T}, Sacrifice this land: Destroy target nonbasic land.", null, null, null),
        ),
    )

    private fun chunk03UtilityLands() = listOf(
        CubeCardText(
            "Normal",
            face("Hallowed Mire Triad", null, "Land — Plains Swamp Forest", "({T}: Add {W}, {B}, or {G}.)\nThis land enters tapped.\nCycling {3} ({3}, Discard this card: Draw a card.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Revelers' Arbor", null, "Land — Mountain Forest Plains", "({T}: Add {R}, {G}, or {W}.)\nThis land enters tapped.\nCycling {3} ({3}, Discard this card: Draw a card.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Stormroot Triad", null, "Land — Forest Island Mountain", "({T}: Add {G}, {U}, or {R}.)\nThis land enters tapped.\nCycling {3} ({3}, Discard this card: Draw a card.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Spymaster's Spire", null, "Land — Plains Island Swamp", "({T}: Add {W}, {U}, or {B}.)\nThis land enters tapped.\nCycling {3} ({3}, Discard this card: Draw a card.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Skyforge Triad", null, "Land — Island Mountain Plains", "({T}: Add {U}, {R}, or {W}.)\nThis land enters tapped.\nCycling {3} ({3}, Discard this card: Draw a card.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ashlight Triad", null, "Land — Mountain Plains Swamp", "({T}: Add {R}, {W}, or {B}.)\nThis land enters tapped.\nCycling {3} ({3}, Discard this card: Draw a card.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gardener's Loft", null, "Land — Forest Plains Island", "({T}: Add {G}, {W}, or {U}.)\nThis land enters tapped.\nCycling {3} ({3}, Discard this card: Draw a card.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Velvet Den", null, "Land — Island Swamp Mountain", "({T}: Add {U}, {B}, or {R}.)\nThis land enters tapped.\nCycling {3} ({3}, Discard this card: Draw a card.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Bogshade Triad", null, "Land — Swamp Forest Island", "({T}: Add {B}, {G}, or {U}.)\nThis land enters tapped.\nCycling {3} ({3}, Discard this card: Draw a card.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Scalelord's Arena", null, "Land — Swamp Mountain Forest", "({T}: Add {B}, {R}, or {G}.)\nThis land enters tapped.\nCycling {3} ({3}, Discard this card: Draw a card.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mother's Bower", null, "Legendary Land", "{T}: Add {G} for each creature you control.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Arcanist Collegium", null, "Legendary Land", "{T}: Add {U} for each artifact you control.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Tinker's Atelier", null, "Land", "{T}: Add {C}{C}{C}. Spend this mana only to cast artifact spells.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Souk of the Sand Kings", null, "Land", "{T}: Draw two cards, then discard three cards.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Barrow of the Restless", null, "Land", "This land enters tapped.\n{T}: Add {C}.\nWhenever this land or another land you control enters, if you control seven or more lands with different names, create a 2/2 black Zombie creature token.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Okurai, Who Abides", null, "Legendary Land", "{T}: Add {G}.\nChannel — {1}{G}, Discard this card: Destroy target artifact, enchantment, or nonbasic land an opponent controls. That player may search their library for a land card with a basic land type, put it onto the battlefield, then shuffle. This ability costs {1} less to activate for each legendary creature you control.", null, null, null),
        ),
    )

    private fun chunk04SimpleMana() = listOf(
        CubeCardText(
            "Normal",
            face("Grim Offering", "{B}", "Instant", "Add {B}{B}{B}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Roiling Chant", "{2}{R}", "Instant", "Add {R}{R}{R}{R}{R}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Verdant Bauble", "{0}", "Artifact", "{T}: Add {G}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Onyx Bauble", "{0}", "Artifact", "{T}: Add {B}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Nacre Bauble", "{0}", "Artifact", "{T}: Add {W}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Garnet Bauble", "{0}", "Artifact", "{T}: Add {R}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Azure Bauble", "{0}", "Artifact", "{T}: Add {U}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Sun Band", "{1}", "Artifact", "{T}: Add {C}{C}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Lotus Frond", "{0}", "Artifact", "{T}, Sacrifice this artifact: Add one mana of any color.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Midnight Lotus", "{0}", "Artifact", "{T}, Sacrifice this artifact: Add three mana of any one color.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Opaline Trinket", "{0}", "Legendary Artifact", "Metalcraft — {T}: Add one mana of any color. Activate only if you control three or more artifacts.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Renderer's Altar", "{3}", "Artifact", "Sacrifice a creature: Add {C}{C}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Scrapper-Clan Forge", "{4}", "Artifact", "Sacrifice an artifact: Add {C}{C}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Compleat Altar", "{3}", "Artifact", "Sacrifice a creature: Add one mana of any color.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Leafbeat Drum", "{1}", "Artifact", "{T}, Tap an untapped creature you control: Add one mana of any color.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Prismatic Mote", "{1}", "Artifact", "{1}, {T}, Sacrifice this artifact: Add one mana of any color.\nWhen this artifact is put into a graveyard from the battlefield, draw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Rimewatcher's Orrery", "{S}", "Snow Artifact", "({S} can be paid with one mana from a snow source.)\nWhen this artifact enters, draw a card.\n{1}, {T}: Add one mana of any color.", null, null, null),
        ),
    )

    private fun chunk05TrickyMana() = listOf(
        CubeCardText(
            "Normal",
            face("Coven's Rite", "{1}{B}", "Instant", "Add {B}{B}{B}.\nThreshold — Add {B}{B}{B}{B}{B} instead if there are seven or more cards in your graveyard.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Frantic Invocation", "{1}{R}", "Instant — Arcane", "Add {R}{R}{R}.\nSplice onto Arcane {1}{R} (As you cast an Arcane spell, you may reveal this card from your hand and pay its splice cost. If you do, add this card's effects to that spell.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Rite of Embers", "{R}", "Sorcery", "Add {R}{R}, then add {R} for each card named Rite of Embers in each graveyard.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Prismatic Orb", "{1}", "Artifact", "{1}, {T}, Sacrifice this artifact: Add one mana of any color. Draw a card. (Activate only as an instant.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Chromed Locket", "{0}", "Artifact", "Imprint — When this artifact enters, you may exile a nonartifact, nonland card from your hand.\n{T}: Add one mana of any of the exiled card's colors.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Jackal's Eye Gem", "{0}", "Artifact", "Discard your hand, Sacrifice this artifact: Add three mana of any one color. Activate only as an instant.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Lotus Bud", null, "Artifact", "Suspend 3—{0} (Rather than cast this card from your hand, pay {0} and exile it with three time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost.)\n{T}, Sacrifice this artifact: Add three mana of any one color.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mana Reliquary", "{0}", "Artifact", "At the beginning of your upkeep, flip a coin. If you lose the flip, this artifact deals 3 damage to you.\n{T}: Add {C}{C}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mana Strongbox", "{1}", "Artifact", "This artifact doesn't untap during your untap step.\nAt the beginning of your upkeep, you may pay {4}. If you do, untap this artifact.\nAt the beginning of your draw step, if this artifact is tapped, it deals 1 damage to you.\n{T}: Add {C}{C}{C}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gemstone Charm", "{0}", "Artifact", "If this artifact would enter, you may discard a land card instead. If you do, put this artifact onto the battlefield. If you don't, put it into its owner's graveyard.\n{T}: Add one mana of any color.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Eden Cloak", "{0}", "Artifact — Equipment", "Equipped creature has \"{T}: Add one mana of any color.\"\nEquip {1}", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Sorcerer's Flares", "{1}", "Artifact", "This artifact enters tapped.\n{X}, {T}, Sacrifice this artifact: Add X mana in any combination of colors.\nWhen this artifact is put into a graveyard from the battlefield, draw a card.", null, null, null),
        ),
    )

    private fun chunk06ManaCreatures() = listOf(
        CubeCardText(
            "Normal",
            face("Saint's Wayfarer", "{G}", "Creature — Human Monk", "{T}: Add {W}.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Rimefrost Druid", "{G}", "Snow Creature — Elf Druid", "{T}: Add {C}.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Elvish Seer", "{G}", "Creature — Elf Druid", "{T}: Add {G}.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Frosthorn Elves", "{G}", "Creature — Elf Druid", "{T}: Add {G}.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Thicketwood Elves", "{G}", "Creature — Elf Druid", "{T}: Add {G}.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Umbral Elves", "{G}", "Creature — Elf Druid", "{T}: Add {B}. This creature deals 1 damage to you.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Canopy Visionary", "{2}{G}", "Creature — Elf Druid", "When this creature enters, draw a card.\n{T}: Add {G}.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Zealous Druid", "{1}{G}", "Creature — Elf Druid", "{T}: Add {G}.\nPut a -1/-1 counter on this creature: Untap this creature.", "0/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Plumes of Eden", "{G}", "Creature — Bird", "Flying\n{T}: Add one mana of any color.", "0/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gearwing of Eden", "{2}", "Artifact Creature — Thopter", "Flying\n{T}: Add one mana of any color.", "0/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Base Exarch", "{G}", "Creature — Goblin Shaman", "Exalted (Whenever a creature you control attacks alone, that creature gets +1/+1 until end of turn.)\n{T}: Add {B}, {R}, or {G}.", "0/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Highborn Exarch", "{G}", "Creature — Human Druid", "Exalted (Whenever a creature you control attacks alone, that creature gets +1/+1 until end of turn.)\n{T}: Add {G}, {W}, or {U}.", "0/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gemhide Lion", "{2}", "Artifact Creature — Cat", "{T}, Discard your hand, Sacrifice this creature: Add three mana of any one color. Activate only as an instant.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Dreamcircle Elder", "{G}{G}{G}", "Creature — Elf Druid", "{T}: Add {G} for each creature you control.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Acolyte of the Grove Mother", "{1}{G}", "Creature — Elf Druid", "{T}: Add {G} for each Elf on the battlefield.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Tavros, Thicket Emissary", "{G}{G}", "Legendary Creature — Elf Druid", "{T}: Add {G} for each Forest you control.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Elvish Hierophant", "{1}{G}{G}", "Creature — Elf Druid", "Other Elf creatures you control get +1/+1.\n{T}: Add {G} for each Elf you control.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Barkwood Pathfinders", "{G}", "Creature — Elf Druid Ranger", "Tap two untapped Elves you control: Add one mana of any color.\nMorph {G} (You may cast this card face down as a 2/2 creature for {3}. Turn it face up any time for its morph cost.)", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Lineage Druid", "{G}", "Creature — Elf Druid", "Tap three untapped Elves you control: Add {G}{G}{G}.", "1/1", null, null),
        ),
    )

    private fun chunk07Counterspells() = listOf(
        CubeCardText(
            "Normal",
            face("Denial", "{U}{U}", "Instant", "Counter target spell.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Spell Noose", "{U}", "Instant", "Counter target spell with mana value 2.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Sharp Rebuke", "{U}", "Instant", "Counter target creature spell with power or toughness 2 or less.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mana Seep", "{1}{U}", "Instant", "Counter target spell unless its controller pays {3}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mana Levy", "{W}", "Instant", "Counter target spell unless its controller pays {1}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Spell Prick", "{U}", "Instant", "Counter target noncreature spell unless its controller pays {2}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Lapse of Focus", "{1}{U}", "Instant", "You may return an Island you control to its owner's hand rather than pay this spell's mana cost.\nCounter target spell unless its controller pays {1}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Blaze of Refusal", "{1}{U}{U}", "Instant", "You may sacrifice a nontoken blue creature rather than pay this spell's mana cost.\nCounter target spell.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Force of Resolve", "{3}{U}{U}", "Instant", "You may pay 1 life and exile a blue card from your hand rather than pay this spell's mana cost.\nCounter target spell.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Force of Rebuttal", "{1}{U}{U}", "Instant", "If it's not your turn, you may exile a blue card from your hand rather than pay this spell's mana cost.\nCounter target noncreature spell. If that spell is countered this way, exile it instead of putting it into its owner's graveyard.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Recall Lapse", "{1}{U}", "Instant", "Counter target spell. If that spell is countered this way, put it on top of its owner's library instead of into that player's graveyard.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Send Back", "{1}{U}", "Instant", "Counter target spell. If that spell is countered this way, put it into its owner's hand instead of into that player's graveyard.\nDraw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mana Siphon", "{U}{U}", "Instant", "Counter target spell. At the beginning of your next main phase, add an amount of {C} equal to that spell's mana value.", null, null, null),
        ),
    )

    private fun chunk08BurnAndRemoval() = listOf(
        CubeCardText(
            "Normal",
            face("Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Cinder Bolt", "{R}", "Sorcery", "Cinder Bolt deals 2 damage to any target.\nFlashback {4}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Magma Needle", "{R}", "Instant", "Magma Needle deals 1 damage to any target.\nFlashback—Sacrifice a Mountain. (You may cast this card from your graveyard for its flashback cost. Then exile it.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Flash Surge", "{R}", "Instant", "Kicker {4} (You may pay an additional {4} as you cast this spell.)\nFlash Surge deals 2 damage to any target. If this spell was kicked, it deals 4 damage instead.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Voltaic Jolt", "{R}", "Instant", "Voltaic Jolt deals 2 damage to any target.\nMetalcraft — Voltaic Jolt deals 4 damage instead if you control three or more artifacts.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Profane Scorch", "{R}", "Instant", "Profane Scorch deals 2 damage to target creature or planeswalker.\nDelirium — Profane Scorch deals 6 damage instead if there are four or more card types among cards in your graveyard.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Immolation Bolt", "{1}{R}", "Instant", "Immolation Bolt deals 3 damage to any target. A creature dealt damage this way can't be regenerated this turn.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Doomfire", "{X}{R}", "Sorcery", "Doomfire deals X damage to any target.\nIf X is 5 or more, this spell can't be countered and the damage can't be prevented.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Sudden Rot", "{B}{G}", "Instant", "This spell can't be countered.\nDestroy target nonland permanent with mana value 3 or less.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Old Enmity", "{1}{R}", "Instant", "Destroy target artifact.\nFlashback {G} (You may cast this card from your graveyard for its flashback cost. Then exile it.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Hollow Victory", "{1}{B}", "Instant", "As an additional cost to cast this spell, discard a card or pay 3 life.\nDestroy target creature or planeswalker.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Bone Splinters", "{B}", "Sorcery", "As an additional cost to cast this spell, sacrifice a creature or discard a card.\nDestroy target creature or planeswalker.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Wink Out", "{3}{B}", "Instant", "If you control a Swamp, you may pay 4 life rather than pay this spell's mana cost.\nDestroy target nonblack creature. It can't be regenerated.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Deadly Shove", "{B}", "Instant", "Destroy target creature if it has mana value 2 or less.\nRevolt — Destroy that creature if it has mana value 4 or less instead if a permanent left the battlefield under your control this turn.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Curse Word", "{B}{B}", "Sorcery", "Destroy target creature. A creature destroyed this way can't be regenerated.\nOverload {2}{W}{W} (You may cast this spell for its overload cost. If you do, change \"target\" in its text to \"each.\")", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Plowshare Decree", "{W}", "Instant", "Exile target creature. Its controller gains life equal to its power.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Road to Exile", "{W}", "Instant", "Exile target creature. Its controller may search their library for a basic land card, put that card onto the battlefield tapped, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Spectral Finale", "{X}{W}", "Sorcery", "Converge — Exile target nonland permanent if its mana value is less than or equal to the number of colors of mana spent to cast this spell.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Rescind", "{X}{U}", "Instant", "Return target nonland permanent with mana value X to its owner's hand.\nDraw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Click Back", "{1}{U}", "Instant", "Return target creature to its owner's hand. Untap up to two lands.", null, null, null),
        ),
    )

    private fun chunk09ModesAndCardFlow() = listOf(
        CubeCardText(
            "Normal",
            face("Grindstone Rasp", "{1}{R}", "Instant", "Choose one —\n• Grindstone Rasp deals 3 damage to target creature.\n• Destroy target artifact.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Stormforge Charm", "{U}{R}", "Instant", "Choose one —\n• Counter target noncreature spell unless its controller pays {2}.\n• Stormforge Charm deals 2 damage to target creature.\n• Draw two cards, then discard two cards.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Warlord's Decree", "{1}{B}{R}", "Instant", "Choose two —\n• Return target creature card from your graveyard to your hand.\n• Target player discards a card.\n• Destroy target artifact.\n• Warlord's Decree deals 2 damage to any target.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Spellforge Command", "{1}{U}{R}", "Instant", "Choose two —\n• Spellforge Command deals 2 damage to any target.\n• Target player draws two cards, then discards two cards.\n• Target player creates a Treasure token.\n• Destroy target artifact.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Blazing Confluence", "{2}{R}{R}", "Sorcery", "Choose three. You may choose the same mode more than once.\n• Blazing Confluence deals 1 damage to each creature.\n• Blazing Confluence deals 2 damage to each opponent.\n• Destroy target artifact.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Thorough Study", "{3}{U}", "Sorcery", "Target player draws two cards.\nFlashback—{1}{U}, Pay 3 life. (You may cast this card from your graveyard for its flashback cost. Then exile it.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Faithless Rummage", "{R}", "Sorcery", "Draw two cards, then discard two cards.\nFlashback {2}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Hoard Voyage", "{7}{U}", "Sorcery", "Delve (Each card you exile from your graveyard while casting this spell pays for {1}.)\nDraw three cards.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Delve Through Ages", "{6}{U}{U}", "Instant", "Delve (Each card you exile from your graveyard while casting this spell pays for {1}.)\nLook at the top seven cards of your library. Put two of them into your hand and the rest on the bottom of your library in any order.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Expressive Variation", "{U}{R}", "Sorcery", "Look at the top three cards of your library. Put one of them into your hand, put one of them on the bottom of your library, and exile one of them. You may play the exiled card this turn.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Echo of Ages", "{4}{U}{U}", "Sorcery", "Each player shuffles their hand and graveyard into their library, then draws seven cards.\nFlashback {2}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Time Whorl", "{4}{U}{U}", "Sorcery", "Exile Time Whorl. Each player shuffles their hand and graveyard into their library, then draws seven cards. You untap up to six lands.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Fleeting Recall", "{W}", "Instant", "Exile target creature you control, then return it to the battlefield under its owner's control.\nRebound (If you cast this spell from your hand, exile it as it resolves. At the beginning of your next upkeep, you may cast this card from exile without paying its mana cost.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Lingering Shades", "{2}{W}", "Sorcery", "Create two 1/1 white Spirit creature tokens with flying.\nFlashback {1}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)", null, null, null),
        ),
    )

    private fun chunk10DrawDiscardAndTutors() = listOf(
        CubeCardText(
            "Normal",
            face("Forebear's Insight", "{U}", "Instant", "Target player draws three cards.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Midnight Murmur", "{1}{B}", "Sorcery", "You draw two cards and lose 2 life.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Hasty Rummage", "{2}{U}", "Instant", "Draw two cards, then discard two cards. Untap up to three lands.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Plot the Voyage", "{1}{U}", "Sorcery", "Draw two cards. Then discard a card unless you attacked this turn.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Searing Question", "{R}", "Sorcery", "Each player draws three cards, then discards three cards at random.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Spinning Fate", "{2}{R}", "Sorcery", "Each player discards their hand, then draws seven cards.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Hourspinner", "{2}{U}", "Sorcery", "Each player shuffles their hand and graveyard into their library, then draws seven cards. (Then put Hourspinner into its owner's graveyard.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Joyful Release", "{1}{R}", "Sorcery", "As an additional cost to cast this spell, discard two cards.\nDraw three cards.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Molten Epiphany", "{R}", "Sorcery", "As an additional cost to cast this spell, discard a land card.\nDraw two cards.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Insist on Truth", "{1}{R}", "Instant", "As an additional cost to cast this spell, sacrifice an artifact or discard a card.\nDraw two cards.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Tidal Rush", "{4}{U}", "Instant", "You may return two Islands you control to their owner's hand rather than pay this spell's mana cost.\nDraw two cards.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Fiendish Mentor", "{1}{B}", "Sorcery", "Search your library for a card, put that card into your hand, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Sinister Purpose", "{1}{B}", "Sorcery", "As an additional cost to cast this spell, sacrifice a creature.\nSearch your library for a card, put that card into your hand, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Reckless Wager", "{R}", "Sorcery", "Search your library for a card, put that card into your hand, discard a card at random, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Bloodsucker's Lesson", "{B}", "Instant", "Search your library for a card, then shuffle and put that card on top. You lose 2 life.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Illumined Mentor", "{W}", "Instant", "Search your library for an artifact or enchantment card, reveal it, then shuffle and put that card on top.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Arcane Mentor", "{U}", "Instant", "Search your library for an instant or sorcery card, reveal it, then shuffle and put that card on top.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Field Exchange", "{G}", "Instant", "As an additional cost to cast this spell, sacrifice a land.\nSearch your library for a land card, put that card onto the battlefield, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Tinkerer's Swap", "{2}{U}", "Sorcery", "As an additional cost to cast this spell, sacrifice an artifact.\nSearch your library for an artifact card, put that card onto the battlefield, then shuffle.", null, null, null),
        ),
    )

    private fun chunk11LookScryAndMill() = listOf(
        CubeCardText(
            "Normal",
            face("Mull It Over", "{U}", "Instant", "Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)\nDraw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Glance Ahead", "{U}", "Instant", "Scry 1. (Look at the top card of your library. You may put that card on the bottom.)\nDraw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Foretell the Way", "{U}", "Sorcery", "Scry 2, then draw a card. (To scry 2, look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ichor Dreams", "{U}", "Sorcery", "Draw a card. Scry 2.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Muse Awhile", "{U}", "Sorcery", "Look at the top three cards of your library, then put them back in any order. You may shuffle.\nDraw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mind Squall", "{U}", "Instant", "Draw three cards, then put two cards from your hand on top of your library in any order.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Cerebral Scan", "{U/P}", "Sorcery", "({U/P} can be paid with either {U} or 2 life.)\nLook at target player's hand.\nDraw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Thought Siphon", "{U}", "Instant", "Target player mills two cards.\nDraw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Manashift", "{1}{R/G}", "Instant", "Add two mana in any combination of colors.\nDraw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Solid Proof", "{U}", "Sorcery", "Create a 0/3 blue Crab creature token.\nInvestigate. (Create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Shadowsting", "{B}", "Instant", "Target creature gets -1/-1 until end of turn.\nDredge 3 (If you would draw a card, you may mill three cards instead. If you do, return this card from your graveyard to your hand.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Rebirth from the Mire", "{1}{G}", "Sorcery", "Return up to three target land cards from your graveyard to your hand.\nDredge 3 (If you would draw a card, you may mill three cards instead. If you do, return this card from your graveyard to your hand.)", null, null, null),
        ),
    )

    private fun chunk12HarderCardFlow() = listOf(
        CubeCardText(
            "Normal",
            face("Coda of Ruin", "{X}{G}{G}", "Sorcery", "Search your library and/or graveyard for a creature card with mana value X or less and put it onto the battlefield. If you search your library this way, shuffle. If X is 10 or more, creatures you control get +X/+X and gain haste until end of turn.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Verdant Moon's Apex", "{X}{G}", "Sorcery", "Search your library for a green creature card with mana value X or less, put it onto the battlefield, then shuffle. Shuffle Verdant Moon's Apex into its owner's library.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Eldritch Realization", "{X}{U}", "Instant", "Draw X cards, then discard X cards. Create a 1/1 white Spirit creature token with flying for each card type among cards discarded this way.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Recursive Result", "{3}{U}", "Instant", "Return any number of target nonland, nontoken permanents you control to their owners' hands. Draw a card for each card returned to your hand this way.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Hint of the Wild", "{G}", "Sorcery", "Whenever you cast a creature spell this turn, draw a card.", null, null, null),
        ),
    )

    private fun chunk13HandDisruptionAndRemoval() = listOf(
        CubeCardText(
            "Normal",
            face("Whispered Therapy", "{B}", "Sorcery", "Choose a nonland card name. Target player reveals their hand and discards all cards with that name.\nFlashback—Sacrifice a creature. (You may cast this card from your graveyard for its flashback cost. Then exile it.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Petty Coercion", "{B}", "Sorcery", "Target opponent reveals their hand. You choose a noncreature, nonland card from it. That player discards that card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mind Pilfer", "{B}", "Sorcery", "Target player reveals their hand. You choose a nonland card from it. That player discards that card. You lose 2 life.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Inquest of the Deep", "{B}", "Sorcery", "Target player reveals their hand. You choose a nonland card from it with mana value 3 or less. That player discards that card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Grim Refrain", "{B}", "Sorcery", "Cleave {2}{B} (You may cast this spell for its cleave cost. If you do, remove the words in square brackets.)\nTarget player reveals their hand. You choose a nonland card from it [with mana value 2 or less]. That player discards that card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Visions of Iron and Bone", "{B}", "Sorcery", "Target opponent reveals their hand. You choose an artifact or creature card from it, then choose an artifact or creature card from their graveyard. Exile the chosen cards.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Communal Cruelty", "{1}{B}", "Sorcery", "Escalate—Discard a card. (Pay this cost for each mode chosen beyond the first.)\nChoose one or more —\n• Target opponent reveals their hand. You choose an instant or sorcery card from it. That player discards that card.\n• Target creature gets -2/-2 until end of turn.\n• Target opponent loses 2 life and you gain 2 life.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Crow's Larceny", "{B}", "Sorcery", "Target player discards a card.\nRetrace (You may cast this card from your graveyard by discarding a land card in addition to paying its other costs.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Sever Limb", "{1}{B/P}{B/P}", "Instant", "({B/P} can be paid with either {B} or 2 life.)\nTarget creature gets -5/-5 until end of turn.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Banish Below", "{W}", "Sorcery", "Put target creature into its owner's library second from the top. Its controller gains 3 life.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Flash of Spite", "{2}{B}{B}", "Instant", "You may sacrifice a nontoken black creature rather than pay this spell's mana cost.\nEach opponent sacrifices a creature or planeswalker with the greatest mana value among creatures and planeswalkers they control.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Deadly Plotting", "{2}{B}{B}", "Instant", "Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)\nDestroy target creature or planeswalker. Each creature that convoked this spell connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Procession of Pale Light", "{X}{W}", "Instant", "As an additional cost to cast this spell, you may exile any number of white cards from your hand. This spell costs {2} less to cast for each card exiled this way.\nExile target artifact, creature, or enchantment with mana value X or less.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Vermin Outbreak", "{X}{X}{G}", "Sorcery", "Destroy up to X target artifacts and/or enchantments. Create twice X 1/1 black and green Pest creature tokens with \"When this token dies, you gain 1 life.\"", null, null, null),
        ),
    )

    private fun chunk14CopiesStormAndGraveyardCasting() = listOf(
        CubeCardText(
            "Normal",
            face("Linked Lightning", "{R}", "Sorcery", "Linked Lightning deals 3 damage to any target. Then that player or that permanent's controller may pay {R}{R}. If the player does, they may copy this spell and may choose a new target for that copy.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mounting Retribution", "{R}{R}", "Instant", "Copy target instant or sorcery spell you control. If this spell was cast from a graveyard, copy that spell twice instead. You may choose new targets for the copies.\nFlashback {3}{R}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Flash of Mirroring", "{1}{R}{R}", "Instant", "You may sacrifice a nontoken red creature rather than pay this spell's mana cost.\nCopy target instant or sorcery spell. You may choose new targets for the copy.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Encore Round", "{1}{R}{R}", "Sorcery", "Until end of turn, whenever a player casts an instant or sorcery spell, that player copies it and may choose new targets for the copy.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Warden's Reclamation", "{2}{W}", "Sorcery", "Return target permanent card with mana value 3 or less from your graveyard to the battlefield. If this spell was cast from a graveyard, you may copy this spell and may choose a new target for the copy.\nFlashback {4}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mind Chill", "{1}{U}", "Instant", "Target player mills three cards.\nStorm (When you cast this spell, copy it for each spell cast before it this turn. You may choose new targets for the copies.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Chitterstorm", "{1}{G}", "Sorcery", "Create a 1/1 green Squirrel creature token.\nStorm (When you cast this spell, copy it for each spell cast before it this turn.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Flood the Burrows", "{3}{R}", "Sorcery", "Create two 1/1 red Goblin creature tokens.\nStorm (When you cast this spell, copy it for each spell cast before it this turn.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Tendrils of Torment", "{2}{B}{B}", "Sorcery", "Target player loses 2 life and you gain 2 life.\nStorm (When you cast this spell, copy it for each spell cast before it this turn. You may choose new targets for the copies.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mind's Longing", "{4}{U}{U}", "Sorcery", "Shuffle your library. Then exile the top card of your library. Until end of turn, you may play that card without paying its mana cost.\nStorm (When you cast this spell, copy it for each spell cast before it this turn.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ashes of Yesterday", "{3}{R}", "Sorcery", "Each instant and sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost.\nFlashback {4}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Necromancer's Will", "{2}{B}", "Sorcery", "Until end of turn, you may play lands and cast spells from your graveyard.\nIf a card would be put into your graveyard from anywhere this turn, exile that card instead.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Finale of Vows", "{X}{R}{R}", "Sorcery", "You may cast up to one target instant card and/or up to one target sorcery card from your graveyard each with mana value X or less without paying their mana costs. If a spell cast this way would be put into your graveyard, exile it instead. If X is 10 or more, copy each of those spells twice. You may choose new targets for the copies.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Wildfire Spread", "{X}{X}{R}", "Sorcery", "Wildfire Spread deals X damage divided as you choose among any number of targets.\nFlashback—{R}{R}, Discard X cards. (You may cast this card from your graveyard for its flashback cost. Then exile it.)", null, null, null),
        ),
    )

    private fun chunk15ReturnsTriggersAndTurns() = listOf(
        CubeCardText(
            "Normal",
            face("Muster the Forebears", "{X}{W}{W}", "Instant", "Return each creature card with mana value X or less from your graveyard to the battlefield. Exile those creatures at the beginning of your next upkeep. Exile Muster the Forebears.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Hauled Back", "{W}{W}", "Instant", "Choose up to two target permanent cards in your graveyard that were put there from the battlefield this turn. Return them to the battlefield tapped.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Devotion's Reward", "{3}{W}", "Instant", "Return to the battlefield all permanent cards in your graveyard that were put there from the battlefield this turn.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Second Dawning", "{1}{W}{W}", "Instant", "Each player returns to the battlefield all artifact, creature, enchantment, and land cards in their graveyard that were put there from the battlefield this turn.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Rite of Concord", "{G}{W}", "Instant", "Whenever a creature or enchantment you control enters this turn, draw a card.\nFlashback {2}{G}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Swelling Tide", "{U}", "Instant", "Until end of turn, whenever a player taps an Island for mana, that player adds an additional {U}.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Chastening Flame", "{1}{R}", "Instant", "Chastening Flame deals 2 damage to any target.\nWhenever an opponent gains life, you may pay {R}. If you do, return this card from your graveyard to your hand.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gathered Fellowship", "{3}{G}", "Instant", "Look at the top six cards of your library. Put up to two creature cards with mana value 3 or less from among them onto the battlefield. Put the rest on the bottom of your library in any order.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Fleeting Whim", "{1}{U}", "Instant", "Look at the top four cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Time Stride", "{1}{U}", "Sorcery", "Take an extra turn after this one.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Tidecall Reversal", "{2}{U}{U}", "Instant", "Choose artifact, creature, or land. Tap all untapped permanents of the chosen type target player controls, or untap all tapped permanents of that type that player controls.", null, null, null),
        ),
    )

    private fun chunk20EquipmentAndSacrificeArtifacts() = listOf(
        CubeCardText(
            "Normal",
            face("Skull Bracer", "{2}", "Artifact — Equipment", "Equipped creature gets +1/+0 for each artifact you control.\n{B}{B}: Attach this Equipment to target creature you control.\nEquip {1}", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Fused Battleplate", "{3}", "Artifact — Equipment", "Equipped creature gets +3/+2.\nWhenever this Equipment becomes unattached from a permanent, sacrifice that permanent.\nEquip {0} ({0}: Attach to target creature you control. Equip only as a sorcery.)", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Thornpod Husk", "{3}", "Artifact — Equipment", "Living weapon (When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it.)\nEquipped creature gets +1/+1 for each artifact and/or enchantment you control.\nEquip {2}", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Bonevise", "{1}", "Artifact — Equipment", "Equipped creature gets +1/-1.\nWhenever equipped creature dies, draw two cards.\nEquip {1}", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Blade of the Humble", "{2}", "Artifact — Equipment", "Equipped creature gets +1/+2.\nEquip {2}\nWhenever a 1/1 creature you control enters, you may return this card from your graveyard to the battlefield, then attach it to that creature.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Runner's Skiff", "{2}", "Artifact — Vehicle", "Flying\nWhenever this Vehicle attacks or blocks, you may draw a card. If you do, discard a card.\nCrew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)", "3/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Tidal Spellcharge", "{1}", "Artifact", "{U}, Sacrifice this artifact: Return target creature to its owner's hand.\n{1}, Sacrifice this artifact: Draw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Cinder Spellcharge", "{1}", "Artifact", "{R}, Sacrifice this artifact: It deals 2 damage to any target.\n{1}, Sacrifice this artifact: Draw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mender's Trinket", "{1}", "Artifact", "{T}, Sacrifice this artifact: Put up to one target card from your graveyard on the bottom of your library. Draw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Seer's Trinket", "{0}", "Artifact", "{T}, Sacrifice this artifact: Look at the top card of target player's library. Draw a card at the beginning of the next turn's upkeep.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Spy's Trinket", "{0}", "Artifact", "{T}, Sacrifice this artifact: Look at a card at random in target player's hand. You draw a card at the beginning of the next turn's upkeep.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Oracle's Spinning Top", "{1}", "Artifact", "{1}: Look at the top three cards of your library, then put them back in any order.\n{T}: Draw a card, then put this artifact on top of its owner's library.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Shrine of Oblivion", "{2}", "Artifact", "Sacrifice a creature: Target player mills cards equal to the sacrificed creature's power.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Detonation Rig", "{3}", "Artifact", "{T}, Sacrifice a creature: This artifact deals 1 damage to any target.\nWhenever a creature enters, you may untap this artifact.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Lifeseed Orb", "{0}", "Artifact", "Sacrifice a land: You gain 2 life.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Hag's Kiln", "{1}", "Artifact", "{T}, Sacrifice a creature: Create a Food token. If the sacrificed creature's toughness was 4 or greater, create two Food tokens instead. (They're artifacts with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Wingwright Forge", "{W/B}{U}", "Artifact", "{1}, Sacrifice a nontoken artifact: Create a 1/1 blue Thopter artifact creature token with flying. You gain 1 life.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("The Gravecook's Ledger", "{1}", "Artifact — Book", "{T}, Discard a card: Create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")\n{4}, {T}, Sacrifice this artifact: Return target creature card from your graveyard to your hand.", null, null, null),
        ),
    )

    private fun chunk21CountersTriggersAndLinkedArtifacts() = listOf(
        CubeCardText(
            "Normal",
            face("Ether Flask", "{1}", "Artifact", "At the beginning of your upkeep, you may put a charge counter on this artifact.\n{T}: You may put a creature card with mana value equal to the number of charge counters on this artifact from your hand onto the battlefield.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Clockwork Charge", "{2}", "Artifact", "{T}: Put a charge counter on this artifact.\n{T}, Sacrifice this artifact: Destroy each nonland permanent with mana value equal to the number of charge counters on this artifact.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Tuned Detonators", "{X}", "Artifact", "Sunburst (This artifact enters with a charge counter on it for each color of mana spent to cast it.)\n{2}, Sacrifice this artifact: Destroy each nonland permanent with mana value equal to the number of charge counters on this artifact.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Brood Vat", "{2}", "Artifact", "Sacrifice a creature: Put a charge counter on this artifact.\n{1}, Remove two charge counters from this artifact: Create a 2/2 colorless Spawn artifact creature token.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Rod of the Fabulist", "{1}{W}", "Artifact", "When this artifact enters, create a 1/1 white Spirit creature token with flying.\nWhenever you create one or more creature tokens, put a story counter on this artifact.\n{W}, {T}, Remove a story counter from this artifact: Draw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Wishfang Amulet", "{1}{B}", "Artifact", "This artifact enters with three wish counters on it.\n{1}, {T}, Remove a wish counter from this artifact: Search your library for a card, put it into your hand, then shuffle. An opponent gains control of this artifact. Activate only during your turn.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Refitting Workshop", "{1}", "Artifact", "{3}: Untap this artifact.\n{2}, {T}: Create a 1/1 colorless Servo artifact creature token.\n{1}, {T}, Sacrifice a Servo: Create a 1/1 colorless Thopter artifact creature token with flying.\n{T}, Sacrifice a Thopter: Create a 4/4 colorless Construct artifact creature token.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Prototype Replicator", "{R}", "Artifact", "When this artifact enters or leaves the battlefield, exile the top card of your library. Until end of turn, you may play that card.\n{2}{R}, Sacrifice this artifact: Create a 2/2 white Samurai creature token with vigilance. Activate only as a sorcery.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Origin Vault", "{2}", "Artifact", "Whenever a nontoken creature enters, if this artifact is untapped, that creature's controller creates a 1/1 colorless Myr artifact creature token.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Demon-Cult Forge", "{B}{R}", "Artifact", "Whenever one or more artifacts you control leave the battlefield during your turn, create a 1/1 colorless Construct artifact creature token. This ability triggers only once each turn.\n{T}, Sacrifice an artifact: This artifact deals 1 damage to each opponent. You gain 1 life.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Stormflux Cistern", "{4}", "Artifact", "Whenever you cast a spell, you gain 1 life for each spell you've cast this turn.\nPay 50 life: This artifact deals 50 damage to any target.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Bile Fountain", "{2}", "Artifact", "When this artifact enters or is put into a graveyard from the battlefield, draw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Memorial to Perseverance", "{3}", "Artifact", "Whenever you discard a card, choose one that hasn't been chosen this turn —\n• Draw a card.\n• Create a Treasure token.\n• Each opponent loses 3 life.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Pocket Rift", "{W}", "Artifact", "When this artifact enters, exile target nonland permanent an opponent controls with mana value 2 or less until this artifact leaves the battlefield.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Cauldron of Realms", "{3}", "Artifact", "You may play lands from your graveyard.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Morwen's Spirit Kettle", "{2}", "Legendary Artifact", "You may spend mana as though it were mana of any color to activate abilities of creatures you control.\nCreatures you control with +1/+1 counters on them have all activated abilities of all creature cards exiled with Morwen's Spirit Kettle.\n{T}: Exile target card from a graveyard. When a creature card is exiled this way, put a +1/+1 counter on target creature you control.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Coin Exchanger", "{1}", "Artifact", "Whenever you discard a card, you may exile that card from your graveyard.\n{2}, {T}: Draw a card, then discard a card.\n{T}: Put a card exiled with this artifact into its owner's graveyard. If it's a land card, create a Treasure token. If it's a nonland card, create a 2/2 black Rogue creature token.", null, null, null),
        ),
    )

    private fun chunk25EnchantmentEngines() = listOf(
        CubeCardText(
            "Normal",
            face("Goblin Volley Pit", "{1}{R}", "Enchantment", "Sacrifice a creature: This enchantment deals 1 damage to any target.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Scrapheap Ordnance", "{1}{R}", "Enchantment", "{1}, Sacrifice an artifact or creature: This enchantment deals 1 damage to any target.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Magma Whirlpool", "{R}", "Enchantment", "{R}, Discard a land card: This enchantment deals 2 damage to any target.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ghoul Outbreak", "{1}{B}", "Enchantment", "Discard two cards: Create a 2/2 black Zombie creature token.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Law of the Wilds", "{1}{G}", "Enchantment", "{G}, Discard a creature card: Search your library for a creature card, reveal that card, put it into your hand, then shuffle.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Wasted Bounty", "{B}{G}", "Enchantment", "Sacrifice a land: Add one mana of any type the sacrificed land could produce.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Soilbinding", "{1}{G}", "Enchantment", "Tap an untapped creature you control: Untap target basic land.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Frontier Wandering", "{G}", "Enchantment", "You may play an additional land on each of your turns.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Rootrush", "{G}", "Enchantment", "You may play any number of lands on each of your turns.\nWhenever you play a land, if it wasn't the first land you played this turn, this enchantment deals 1 damage to you.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Verdant Hollowmere", "{2}{G}{G}", "Enchantment", "Any player may cast creature spells with mana value 3 or less without paying their mana costs and as though they had flash.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Sentry Klaxon", "{2}{U}", "Enchantment", "Creatures don't untap during their controllers' untap steps.\nWhenever a creature enters, untap all creatures.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Endless Return", "{2}{W}{W}", "Enchantment", "Play with your hand revealed.\nIf you would draw a card, reveal the top card of your library instead. If it's a creature card, put it into your graveyard. Otherwise, draw a card.\nWhenever a creature is put into your graveyard from the battlefield, return it to your hand.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Rapid Retrofit", "{W}", "Enchantment — Aura", "Flash\nEnchant creature or Vehicle\nEnchanted permanent is a Vehicle artifact with crew 5 and it loses all other card types. (It's not a creature unless it's crewed.)", null, null, null),
        ),
    )

    private fun chunk26TriggeredEnchantmentsAndPlaneswalkers() = listOf(
        CubeCardText(
            "Normal",
            face("Grim Homecoming", "{1}{R}", "Enchantment", "When this enchantment enters, you may discard a card. If you do, draw two cards.\n{1}, Sacrifice this enchantment: Creatures you control gain haste until end of turn.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Stairway from the Depths", "{B}{B}{B}", "Enchantment", "Whenever a nontoken creature is put into your graveyard from the battlefield, if this card is in your graveyard, create a 2/2 black Zombie creature token.\nWhen a creature is put into an opponent's graveyard from the battlefield, if this card is in your graveyard, exile this card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Fertile Passing", "{2}{G}", "Enchantment", "Whenever a creature dies, that creature's controller may draw a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Sinister Rootwork", "{B}{G}", "Enchantment", "Creature tokens you control have \"{T}: Add one mana of any color.\"\nWhenever one or more creature cards leave your graveyard, create a 0/1 green Plant creature token, then put a +1/+1 counter on each Plant you control.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("The Bonehook Slaughter", "{X}{B}{B}", "Legendary Enchantment", "When The Bonehook Slaughter enters, each creature gets -X/-X until end of turn.\nWhenever a creature you control dies, each opponent loses 1 life.\nWhenever a creature an opponent controls dies, you gain 1 life.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Concealed Armory", "{W}{B}", "Enchantment", "Revolt — At the beginning of your end step, if a permanent left the battlefield under your control this turn, create a 1/1 colorless Servo artifact creature token.\n{1}, Sacrifice a creature: Scry 1.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Molten Peak Expedition", "{2}{R}", "Enchantment", "Landfall — Whenever a land you control enters, exile the top card of your library. You may play that card for as long as it remains exiled.\nAt the beginning of your end step, if there are cards exiled with this enchantment, put them into their owner's graveyard, then this enchantment deals that much damage to each opponent.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Peak Ascendancy", "{U}{R}{W}", "Enchantment", "Whenever you cast a noncreature spell, creatures you control get +1/+1 until end of turn. Untap those creatures.\nWhenever you cast a noncreature spell, you may draw a card. If you do, discard a card.", null, null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ignia, Acolyte of Embers", "{1}{R}{R}", "Legendary Planeswalker — Chandra", "[0]: Put a loyalty counter on each red planeswalker you control.\n[0]: Create two 1/1 red Elemental creature tokens. They gain haste. Sacrifice them at the beginning of the next end step.\n[−2]: You may cast target instant or sorcery card with mana value 3 or less from your graveyard. If that spell would be put into your graveyard, exile it instead.", null, "4", null),
        ),
        CubeCardText(
            "Normal",
            face("Renji Kagehara", "{1}{U}{B}", "Legendary Planeswalker — Kaito", "At the beginning of your end step, if Renji entered this turn, he phases out.\n[+1]: Draw a card. Then discard a card unless you attacked this turn.\n[−2]: Create a 1/1 blue Ninja creature token with \"This token can't be blocked.\"\n[−7]: You get an emblem with \"Whenever a creature you control deals combat damage to a player, search your library for a blue or black creature card, put it onto the battlefield, then shuffle.\"", null, "3", null),
        ),
        CubeCardText(
            "Normal",
            face("Orla, Gilded Artificer", "{1}{U/R}{U/R}", "Legendary Planeswalker — Saheeli", "Whenever you cast a noncreature spell, create a 1/1 colorless Servo artifact creature token.\n[−2]: Target artifact you control becomes a copy of another target artifact or creature you control until end of turn, except it's an artifact in addition to its other types.", null, "5", null),
        ),
    )

    private fun chunk30EntersDrawAndLife() = listOf(
        CubeCardText(
            "Normal",
            face("Venomous Owlet", "{U}{B}", "Artifact Creature — Bird", "Flying, deathtouch\nWhen this creature enters, draw a card.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Elvish Seerling", "{1}{G}", "Creature — Elf Shaman", "When this creature enters, draw a card.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Loyal Hearthhound", "{1}{W}", "Enchantment Creature — Dog", "When this creature enters, draw a card.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Wall of Portents", "{1}{W}", "Creature — Wall", "Defender\nWhen this creature enters, draw a card.", "0/4", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Frost-Scale Serpent", "{G}{U}", "Snow Creature — Snake", "Flash\nFlying\nWhen this creature enters, draw a card.\nThis creature has deathtouch as long as you control at least three other snow permanents.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Cogitating Sentry", "{6}{U}", "Artifact Creature — Construct", "Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)\nFlying\nWhen this creature enters, draw two cards.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gravecaller Banneret", "{2}{B}", "Creature — Zombie Knight", "Flash\nWhen this creature enters, draw X cards, where X is the number of creatures that died under your control this turn.", "3/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Faerie Augur", "{U}", "Creature — Faerie Wizard", "Flying\nWhen this creature enters, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Deathless Savant", "{1}{U}{B}", "Creature — Zombie Wizard", "When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)\nAt the beginning of your upkeep, if this card is in your graveyard, you may pay 1 life. If you do, return it to your hand.", "3/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Hollowell Investigator", "{W}", "Creature — Human Soldier", "When this creature enters, investigate. (Create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")", "1/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Junkyard Cur", "{2}", "Artifact Creature — Dog", "When this creature enters, you may discard a card. If you do, draw a card.\nUnearth {1}{R} ({1}{R}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Bull of the Pyre", "{3}{R}{R}", "Creature — Ox", "When this creature enters, discard your hand, then draw three cards.\nEscape—{R}{R}, Exile eight other cards from your graveyard. (You may cast this card from your graveyard for its escape cost.)\nThis creature escapes with a +1/+1 counter on it.", "4/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Rebuilt Ratling", "{3}{B}", "Artifact Creature — Zombie Rat", "Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)\nFlying\nWhen this creature enters, each opponent discards a card. For each opponent who can't, you draw a card.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Festering Vermin", "{1}{B}", "Creature — Zombie Rat", "When this creature enters, each player discards a card.\nUnearth {1}{B} ({1}{B}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Larder Ouphes", "{1}{G/W}{G/W}", "Creature — Ouphe", "When this creature enters, you gain 2 life.\nPersist (When this creature dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)", "3/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Kettle Kitten", "{B}", "Creature — Cat", "When this creature enters, each opponent loses 1 life and you gain 1 life.\nSacrifice a Food: Return this card from your graveyard to the battlefield.", "1/1", null, null),
        ),
    )

    private fun chunk31EntersLibraryAndGraveyard() = listOf(
        CubeCardText(
            "Normal",
            face("Wreckage Analyst", "{1}{G}", "Creature — Elf Detective", "When this creature enters, mill three cards. (Put the top three cards of your library into your graveyard.)\n{3}{G}, Sacrifice this creature: Return all land cards from your graveyard to the battlefield tapped.", "1/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Stitchwork Porter", "{B}", "Creature — Zombie", "When this creature enters or dies, mill three cards. (Put the top three cards of your library into your graveyard.)", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Undying Valet", "{1}{B}", "Creature — Zombie", "When this creature enters, mill three cards. (Put the top three cards of your library into your graveyard.)\nWhen this creature dies, you may exile it. When you do, return target creature card from your graveyard to your hand.", "1/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ondra, Lurker of the Mere", "{2}{U}", "Legendary Creature — Merfolk Wizard", "Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)\nWhen Ondra enters, mill four cards.\n{T}: Choose target artifact card in your graveyard. You may cast that card this turn. (You still pay its costs. Timing rules still apply.)", "1/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Twining Augur", "{G}{U}", "Creature — Snake Elf Druid", "When this creature enters, reveal the top card of your library. If it's a land card, put it onto the battlefield. Otherwise, put that card into your hand.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Clever Tinkersmith", "{1}{W}", "Creature — Human Artificer", "When this creature enters, look at the top four cards of your library. You may reveal an artifact card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.\nWhenever one or more artifacts you control enter, put a +1/+1 counter on this creature. This ability triggers only once each turn.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Satyr Pathseeker", "{1}{G}", "Creature — Satyr", "When this creature enters, reveal the top four cards of your library. You may put a land card from among them into your hand. Put the rest into your graveyard.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Oracle of the Deep Tide", "{U}{U}", "Creature — Merfolk Wizard", "When this creature enters, look at the top X cards of your library, where X is your devotion to blue. Put up to one of them on top of your library and the rest on the bottom of your library in a random order. If X is greater than or equal to the number of cards in your library, you win the game. (Each {U} in the mana costs of permanents you control counts toward your devotion to blue.)", "1/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Seer of Morrows", "{1}{U}", "Creature — Human Wizard", "Hideaway 4 (When this creature enters, look at the top four cards of your library, exile one face down, then put the rest on the bottom in a random order.)\nThis creature enters tapped.\nWhen this creature leaves the battlefield, put the exiled card into its owner's hand.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Undying Chronicler", "{1}{G}{G}", "Creature — Human Shaman", "When this creature enters, you may return target card from your graveyard to your hand.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Echoflash Adept", "{1}{U}", "Creature — Human Wizard", "Flash\nWhen this creature enters, target instant or sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost. (You may cast that card from your graveyard for its flashback cost. Then exile it.)", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Canopy Browser", "{G}", "Creature — Sloth Beast", "Reach\nWhen this creature enters, you may put a land card from your hand onto the battlefield tapped.", "0/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Persistence", "{1}{G}{G}", "Creature — Elemental Incarnation", "Flash\nReach\nWhen this creature enters, up to one target player puts all the cards from their graveyard on the bottom of their library in a random order.\nEvoke—Exile a green card from your hand.", "3/4", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Goblin Salvager", "{1}{R}", "Creature — Goblin Artificer", "When this creature enters, you may search your library for an artifact card, put it into your graveyard, then shuffle.\n{R}, {T}, Sacrifice an artifact: Return target artifact card with mana value 3 or less from your graveyard to the battlefield.", "1/2", null, null),
        ),
    )

    private fun chunk32EntersTutorsTokensAndBounce() = listOf(
        CubeCardText(
            "Normal",
            face("Royal Enlister", "{2}{R}", "Creature — Human Advisor", "When this creature enters, search your library for a creature card with power 2 or less, reveal it, put it into your hand, then shuffle.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Enlister of the Watch", "{2}{W}", "Creature — Human Soldier", "When this creature enters, you may search your library for a creature card with toughness 2 or less, reveal it, put it into your hand, then shuffle.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ranger-Marshal of Dawn", "{1}{W}{W}", "Creature — Human Soldier Ranger", "When this creature enters, you may search your library for a creature card with mana value 1 or less, reveal it, put it into your hand, then shuffle.\nSacrifice this creature: Your opponents can't cast noncreature spells this turn.", "3/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Arcane Seeker", "{2}{U}", "Creature — Human Wizard", "When this creature enters, you may search your library for an instant or sorcery card with mana value 2 or less, reveal it, put it into your hand, then shuffle.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Anvilborn Mystic", "{1}{W}", "Creature — Kor Artificer", "When this creature enters, you may search your library for an Equipment card, reveal it, put it into your hand, then shuffle.\n{1}{W}, {T}: You may put an Equipment card from your hand onto the battlefield.", "1/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Commanding Orator", "{2}{G}", "Creature — Elf Druid", "When this creature enters, you may discard a card. If you do, search your library for a creature card, reveal it, put it into your hand, then shuffle.\n{1}, {T}: Untap another target permanent.", "2/4", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Vivacity", "{3}{R/G}{R/G}", "Creature — Elemental Incarnation", "When this creature enters, if {R}{R} was spent to cast it, this creature deals 3 damage to any target.\nWhen this creature enters, if {G}{G} was spent to cast it, search your library for a land card, reveal it, put it into your hand, then shuffle. You gain 2 life.\nEvoke {R/G}{R/G}", "4/4", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Bartholomewtheophilustrindleberry", null, "Legendary Creature — Human Wizard", "As long as you've discarded a card this turn, you may pay {B/R} to cast this spell.\nWhen Bartholomewtheophilustrindleberry enters, you may search your library for a card named The Netherworld Recipe Book, reveal it, put it into your hand, then shuffle.\nSacrifice two Foods: Target creature deals 6 damage to itself.", "3/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Golem Grafter", "{2}{W}", "Creature — Phyrexian Human Artificer", "When this creature enters, create a 3/3 colorless Phyrexian Golem artifact creature token.\nGolems you control have first strike.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Thunderhoof Colossus", "{5}{G}{G}{G}", "Creature — Beast", "Haste\nWhen this creature enters, creatures you control gain trample and get +X/+X until end of turn, where X is the number of creatures you control.", "5/5", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Puppeteer's Apprentice", "{1}{B}", "Creature — Human Artificer", "Fabricate 1 (When this creature enters, put a +1/+1 counter on it or create a 1/1 colorless Servo artifact creature token.)\nWhenever another creature or artifact you control is put into a graveyard from the battlefield, each opponent loses 1 life.", "1/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Myriad-Faced Shade", "{U}", "Creature — Human Ninja", "Ninjutsu {2}{U}{U} ({2}{U}{U}, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand tapped and attacking.)\nFlying\nWhen this creature enters from your hand, if it's attacking, create a token that's a copy of another target attacking creature. The token enters tapped and attacking.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Twincast Adept", "{1}{R}{R}", "Creature — Human Wizard", "Flash\nWhen this creature enters, copy target instant or sorcery spell. You may choose new targets for the copy.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Kor Cloudangler", "{1}{W}", "Creature — Kor Soldier", "Flying\nWhen this creature enters, return a permanent you control to its owner's hand.", "2/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Screeching Drakelet", "{U}", "Creature — Drake", "Flying\nWhen this creature enters, return a creature you control to its owner's hand.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Silvermane Lion", "{1}{W}", "Creature — Cat", "Flash\nWhen this creature enters, return a creature you control to its owner's hand.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Stoneshroud Gargoyle", "{2}{W}", "Creature — Gargoyle", "Flash\nFlying\nWhen this creature enters, return a creature you control to its owner's hand.\nWhen this creature enters, exile target card from a graveyard.", "3/2", null, null),
        ),
    )

    private fun chunk33EntersRemoval() = listOf(
        CubeCardText(
            "Normal",
            face("Cursed Pillager", "{1}{B}", "Creature — Zombie Warrior", "When this creature enters, each player sacrifices a nontoken creature of their choice.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Deep-Grotto Bat", "{1}{B}", "Creature — Bat", "Flying, lifelink\nWhen this creature enters, look at target opponent's hand. You may exile a nonland card from it until this creature leaves the battlefield.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Hypnotic Fiend", "{1}{B}", "Creature — Nightmare Horror", "When this creature enters, target opponent reveals their hand and you choose a nonland card from it. Exile that card.\nWhen this creature leaves the battlefield, return the exiled card to its owner's hand.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ebbmarsh Pilferer", "{W}{B}", "Artifact Creature — Zombie", "When this creature enters, target opponent reveals their hand and you choose a nonland card from it. Exile that card.\nWhen this creature leaves the battlefield, return the exiled card to its owner's hand.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Velis, Mender of the Lost Way", "{2}{W}", "Legendary Creature — Human Artificer", "Vigilance\nWhen Velis enters, destroy up to one target artifact or enchantment.\n{T}: You and target opponent each draw a card.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Overgrowth Sage", "{2}{G}", "Creature — Elf Shaman", "When this creature enters, you may destroy target artifact or enchantment.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Veiled Wrecker", "{1}{G}", "Creature — Shapeshifter", "Changeling (This card is every creature type.)\nWhen this creature enters, you may exile a creature card from your graveyard. If you do, exile target artifact or enchantment an opponent controls.", "1/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Cloudhaven Phantasm", "{1}{W}{W}", "Creature — Kor Spirit", "When this creature enters, exile up to one target nonland, nontoken permanent you don't control with mana value 4 or less.\nWhen this creature leaves the battlefield, the exiled card's owner creates an X/X blue Illusion creature token, where X is the mana value of the exiled card.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Seclusion", "{3}{W}{W}", "Creature — Elemental Incarnation", "Flash\nLifelink\nWhen this creature enters, exile up to one other target creature. That creature's controller gains life equal to its power.\nEvoke—Exile a white card from your hand.", "3/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Pilfering Skydiver", "{1}{U}", "Creature — Merfolk Rogue", "Kicker {X}. X can't be 0. (You may pay an additional {X} as you cast this spell.)\nFlying\nWhen this creature enters, if it was kicked, gain control of target artifact with mana value X or less. If that artifact is an Equipment, attach it to this creature.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mirrorward Mage", "{1}{W}{U}", "Creature — Human Wizard", "When this creature enters, return target creature an opponent controls to its owner's hand. That creature's owner can't cast spells with the same name as that creature until your next turn.", "2/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Bloodthirsty Redcap", "{2}{B/R}{B/R}", "Creature — Goblin Assassin", "When this creature enters, it deals damage equal to its power to any target.\nPersist (When this creature dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Spell Stifler", "{1}{W}{U}", "Creature — Spirit", "Flash\nFlying\nWhen this creature enters, exile target spell with mana value 4 or less.\nWhen this creature leaves the battlefield, the exiled card's owner may cast that card without paying its mana cost.", "2/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Spellstammer Pixie", "{1}{U}", "Creature — Faerie Wizard", "Flash\nFlying\nWhen this creature enters, counter target spell with mana value X or less, where X is the number of Faeries you control.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Harbor Gate Tempestcaller", "{1}{U}", "Creature — Human Wizard", "Kicker {4}{U}\nWhen this creature enters, copy the next instant or sorcery spell with mana value 2 or less you cast this turn when you cast it. If this creature was kicked, copy that spell twice instead. You may choose new targets for the copies.", "2/1", null, null),
        ),
    )

    private fun chunk35DeathTriggers() = listOf(
        CubeCardText(
            "Normal",
            face("Crimson Mourner", "{1}{B}", "Creature — Vampire", "Whenever this creature or another creature dies, target player loses 1 life and you gain 1 life.", "0/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Harborside Knifer", "{1}{B}", "Creature — Human Rogue Ally", "Whenever this creature or another creature you control dies, each opponent loses 1 life and you gain 1 life.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ghoulish Reveler", "{W}{B}", "Creature — Vampire", "Whenever this creature or another creature or planeswalker you control dies, each opponent loses 1 life and you gain 1 life.", "1/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Undead Soothsayer", "{B}{B}", "Creature — Zombie Wizard", "Whenever this creature or another Zombie you control dies, you draw a card and lose 1 life.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Dusk Harvester", "{2}{B}", "Creature — Zombie Knight", "Whenever a nontoken creature you control dies, this creature deals 1 damage to you and you draw a card.", "3/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Neckless Horseman", "{2}{B}", "Creature — Zombie", "Whenever this creature or another nontoken Zombie you control dies, create a 2/2 black Zombie creature token.", "3/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Pyrrhus, Tempered in Ash", "{1}{R}{R}", "Legendary Enchantment Creature — Demigod", "Pyrrhus's power is equal to your devotion to red. (Each {R} in the mana costs of permanents you control counts toward your devotion to red.)\nWhenever Pyrrhus or another nontoken creature you control dies, create a 1/1 red Satyr creature token with \"This token can't block.\" If the creature had power 4 or greater, create two of those tokens instead.", "*/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Hangar Strider", "{X}{X}", "Artifact Creature — Construct", "This creature enters with X +1/+1 counters on it.\nWhen this creature dies, create a 1/1 colorless Thopter artifact creature token with flying for each +1/+1 counter on this creature.\n{1}, {T}: Put a +1/+1 counter on this creature.", "0/0", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Burrowed Shambler", "{B}", "Creature — Zombie", "When this creature dies, create X tapped 1/1 green Squirrel creature tokens, where X is this creature's power.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Scrapfed Devourer", "{2}", "Artifact Creature — Beast", "Sacrifice an artifact: Put a +1/+1 counter on this creature.\nModular 1 (This creature enters with a +1/+1 counter on it. When it dies, you may put its +1/+1 counters on target artifact creature.)", "0/0", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Bazaarback Strider", "{X}{X}", "Artifact Creature — Construct", "This creature enters with X +1/+1 counters on it.\n{4}: Put a +1/+1 counter on this creature.\nWhen this creature dies, draw a card for each +1/+1 counter on it.", "0/0", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Velo, Master of Ceremonies", "{B}{R}", "Legendary Creature — Human Shaman", "Whenever you sacrifice a permanent, put a +1/+1 counter on Velo.\nWhen Velo dies, it deals damage equal to its power to any target.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Volatile Myr", "{2}", "Artifact Creature — Phyrexian Myr", "When this creature dies, it deals 2 damage to any target.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Salvage Gull", "{3}", "Artifact Creature — Bird", "Flying\nWhen this creature dies, return another target artifact card from your graveyard to your hand.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Myr Gleaner", "{2}", "Artifact Creature — Myr", "When this creature dies, return another target artifact card from your graveyard to your hand.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Foundry Apprentice", "{3}", "Artifact Creature — Construct", "When this creature dies, return another target artifact card from your graveyard to your hand.", "1/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Scrap Dredger", "{3}", "Artifact Creature — Construct", "Whenever this creature dies or another artifact you control is put into a graveyard from the battlefield, return to your hand target artifact card in your graveyard with lesser mana value.", "3/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Unyielding Dead", "{B}{B}", "Creature — Zombie", "Menace (This creature can't be blocked except by two or more creatures.)\nWhen this creature dies, you may pay {B}. If you do, return it to its owner's hand.\nWhen this creature dies, you may pay {X}. If you do, return another target Zombie creature card with mana value X from your graveyard to the battlefield.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Junkyard Reassembler", "{3}", "Artifact Creature — Construct", "Modular 2 (This creature enters with two +1/+1 counters on it. When it dies, you may put its +1/+1 counters on target artifact creature.)\n{T}, Sacrifice an artifact: Search your library for a Construct card, reveal it, put it into your hand, then shuffle.", "0/0", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Seasoned Pathfinder", "{G}", "Creature — Human Soldier Scout", "When this creature dies, each player may search their library for up to two basic land cards, put them onto the battlefield, then shuffle.", "1/1", null, null),
        ),
    )

    private fun chunk36AttackAndCombatDamage() = listOf(
        CubeCardText(
            "Normal",
            face("Kael's Marauder", "{2}{R}", "Creature — Vampire Berserker", "This creature attacks each combat if able.\nWhenever this creature attacks, discard your hand, then draw three cards.\nMadness {1}{R} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)", "3/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gearwheel Courier", "{1}", "Artifact Creature — Construct", "Haste\nWhenever this creature attacks, exile the top card of your library face down. (You can't look at it.)\n{R}, Discard your hand, Sacrifice this creature: Put all cards exiled with this creature into their owners' hands.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Paranoid Ranter", "{1}{R}", "Creature — Human Shaman", "Whenever this creature attacks, you may pay {1} and discard a card. If you do, draw a card.\nWhenever you discard one or more nonland cards, you may exile one of them from your graveyard. If you do, you may cast it this turn.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gravehorde Spellcaller", "{1}{R}", "Creature — Zombie Wizard", "Trample\nWhenever this creature attacks, you may cast target instant or sorcery card with mana value less than or equal to this creature's power from your graveyard without paying its mana cost. If that spell would be put into your graveyard, exile it instead.", "1/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Oakheart", "{2}{G}", "Legendary Creature — Treefolk", "Reach\nWhenever Oakheart attacks, mill three cards. You may put a land card from among them into your hand.\nDuring your turn, nonland permanent cards in your graveyard have retrace. (You may cast permanent cards from your graveyard by discarding a land card in addition to paying their other costs.)", "2/4", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Brisa, Bounding Shepherd", "{1}{W}", "Legendary Creature — Dog", "Flash\nWhenever Brisa attacks, exile up to one other target nonland permanent. At the beginning of the next end step, return that card to the battlefield under its owner's control. If it entered under your control, put a +1/+1 counter on Brisa.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Vorgath, Fey-Hexed Tyrant", "{2}{B}{R}{G}", "Legendary Creature — Dragon Noble", "Flying\nWhenever Vorgath enters or attacks, sacrifice another permanent.\nWhenever you sacrifice a permanent, put a +1/+1 counter on Vorgath and draw a card.", "4/4", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Aldric, Fist of the Seraphs", "{1}{G}{W}", "Legendary Creature — Human Cleric", "Training (Whenever this creature attacks with another creature with greater power, put a +1/+1 counter on this creature.)\nWhenever you cast a creature spell, create a 1/1 green and white Human Soldier creature token with training.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Beacon Pest", "{1}", "Artifact Creature — Pest", "Battle cry (Whenever this creature attacks, each other attacking creature gets +1/+0 until end of turn.)\nThis creature can't be blocked except by creatures with flying or reach.", "0/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ninja of the Silent Hours", "{3}{U}", "Creature — Human Ninja", "Ninjutsu {1}{U} ({1}{U}, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand tapped and attacking.)\nWhenever this creature deals combat damage to a player, you may draw a card.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Cunning Infiltrator", "{2}{U}{B}", "Creature — Vedalken Ninja", "Ninjutsu {U}{B} ({U}{B}, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand tapped and attacking.)\nWhenever a Ninja you control deals combat damage to a player, draw a card.", "2/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Neon-Circuit Hacker", "{1}{U}", "Enchantment Creature — Human Ninja", "Ninjutsu {U} ({U}, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand tapped and attacking.)\nWhenever this creature deals combat damage to a player, you may draw a card. If you do, discard a card unless this creature entered this turn.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Akemi, the Fox's Shadow", "{1}{U}{B}", "Legendary Creature — Human Ninja", "Commander ninjutsu {U}{B} ({U}{B}, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand or the command zone tapped and attacking.)\nWhenever a Ninja you control deals combat damage to a player, reveal the top card of your library and put that card into your hand. Each opponent loses life equal to that card's mana value.", "1/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Brakka, Wild Whelp", "{1}{R}", "Legendary Creature — Human Berserker", "Rage — Whenever Brakka attacks, put a +1/+1 counter on it.\nAt the beginning of each end step, if a card left your graveyard this turn, Brakka deals damage equal to its power to each opponent.", "2/2", null, null),
        ),
    )

    private fun chunk40PumpsEvasionAndTypeChanges() = listOf(
        CubeCardText(
            "Normal",
            face("Sunning Mudlizard", "{G}", "Creature — Lizard", "{1}{G}: This creature gets +2/+2 until end of turn. Activate only once each turn.\nMadness {0} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Scorching Mudlizard", "{R}", "Creature — Lizard", "{R}: This creature gets +2/+0 until end of turn. Activate only once each turn.\nMadness {0} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Elvish Drillmaster", "{1}{G}", "Creature — Elf Warrior", "Whenever one or more other Elves you control enter, create a 1/1 green Elf Warrior creature token. This ability triggers only once each turn.\n{5}{G}{G}: Elves you control get +2/+2 and gain deathtouch until end of turn.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Nutcracker, Squirrel Marshal", "{2}{G}", "Legendary Creature — Squirrel Warrior", "Forestwalk (This creature can't be blocked as long as defending player controls a Forest.)\nIf one or more tokens would be created under your control, those tokens plus that many 1/1 green Squirrel creature tokens are created instead.\n{B}, Sacrifice X Squirrels: Target creature gets +X/-X until end of turn.", "3/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Saurian Herder", "{G}", "Creature — Elf Shaman", "This spell can't be countered.\nGreen spells you control can't be countered.\n{4}{G}{G}: Until end of turn, each Elf creature you control has base power and toughness 5/5 and becomes a Dinosaur in addition to its other creature types.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Glass Polymorph", "{2}{W}", "Creature — Shapeshifter", "Changeling (This card is every creature type.)\n{X}: Until end of turn, creatures you control have base power and toughness X/X and gain all creature types.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Cookie Brawler", "{1}", "Artifact Creature — Food Golem", "Haste (This creature can attack and {T} as soon as it comes under your control.)\n{1}: This creature can't be blocked this turn except by creatures with haste.\n{2}, {T}, Sacrifice this creature: You gain 3 life.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Bestower of Sigils", "{W}", "Creature — Kor Cleric", "{T}: Another target creature you control gains protection from colorless or from the color of your choice until end of turn.", "1/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Vizzik, the Glintwasp", "{1}", "Legendary Artifact Creature — Insect", "Modular 1\nIf a modular triggered ability would put one or more +1/+1 counters on a creature you control, that many plus one +1/+1 counters are put on it instead.\n{R}: Destroy target artifact you control.\n{W}: Vizzik gains flying until end of turn.", "0/0", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Canopy Tender", "{G}", "Creature — Elf Druid", "{T}: Untap target Forest.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Fateknitter", "{3}{U}", "Creature — Zombie Wizard", "{T}: You may tap or untap another target permanent.\nUnearth {U} ({U}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)", "1/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Curseweb Mite", "{2}", "Artifact Creature — Phyrexian Horror", "{U/P}: Change a target of target spell or ability to this creature. ({U/P} can be paid with either {U} or 2 life.)", "0/4", null, null),
        ),
    )

    private fun chunk41CountersDamageAndArtifactEngines() = listOf(
        CubeCardText(
            "Normal",
            face("Iron Foreman", "{2}", "Artifact Creature — Construct", "{T}: Put a +1/+1 counter on each artifact creature you control.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Striding Arbalest", "{X}{X}", "Artifact Creature — Construct", "This creature enters with X +1/+1 counters on it.\n{4}: Put a +1/+1 counter on this creature.\nRemove a +1/+1 counter from this creature: It deals 1 damage to any target.", "0/0", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Uniskelion", "{2}", "Artifact Creature — Construct", "This creature enters with a +1/+1 counter on it.\n{1}, Remove a +1/+1 counter from this creature: It deals 1 damage to any target.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Leopard Sash", "{1}{W}", "Artifact Creature — Equipment Cat", "{W}: Exile target card from a graveyard. If it was a permanent card, put a +1/+1 counter on this permanent.\nEquipped creature gets +1/+1 for each +1/+1 counter on this Equipment.\nReconfigure {2} ({2}: Attach to target creature you control; or unattach from a creature. Reconfigure only as a sorcery. While attached, this isn't a creature.)", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Bleak Magmacaller", "{R}", "Creature — Human Wizard", "{R}, {T}, Exile two cards from your graveyard: This creature deals 2 damage to any target.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Breathing Tornado", "{R}{R}{G}", "Creature — Elemental", "{1}{R}, Discard a land card: This creature deals 2 damage to any target.\n{G}: Return a tapped land you control to its owner's hand.", "2/5", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Glint-Tusk Corsair", "{1}{R}{R}", "Creature — Minotaur Pirate", "Haste\nWhenever you discard a card, this creature deals 1 damage to each opponent.\n{1}{R}, Discard a card: Draw a card. Activate only if this creature is attacking.", "2/4", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Goblin Solderer", "{R}", "Creature — Goblin Artificer", "{T}: Choose target artifact a player controls and target artifact card in that player's graveyard. If both targets are still legal as this ability resolves, that player simultaneously sacrifices the artifact and returns the artifact card to the battlefield.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Junkpile Scavenger", "{2}", "Artifact Creature — Construct", "This creature can't block.\n{1}{B}, Exile another creature card from your graveyard: Return this card from your graveyard to the battlefield.", "3/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Tavi, Thopter Savant", "{2}{U}", "Legendary Creature — Human Artificer", "Whenever you cast an artifact spell, create a 1/1 colorless Thopter artifact creature token with flying.\n{1}{U}, Sacrifice two artifacts: Draw a card.", "1/4", null, null),
        ),
    )

    private fun chunk42SacrificeEnginesAndTutorCreatures() = listOf(
        CubeCardText(
            "Normal",
            face("Wildlife Shaman", "{1}{G}", "Creature — Elf Shaman", "{G}, {T}, Discard a creature card: Search your library for a creature card, reveal it, put it into your hand, then shuffle.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Elvish Salvager", "{G}", "Creature — Elf Warrior", "This creature gets +2/+2 as long as there are three or more land cards in your graveyard.\n{2}, {T}, Sacrifice a land: Search your library for a land card, put it onto the battlefield tapped, then shuffle.", "1/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Knight of the Relic Vault", "{1}{G}{W}", "Creature — Human Knight", "This creature gets +1/+1 for each land card in your graveyard.\n{T}, Sacrifice a Forest or Plains: Search your library for a land card, put it onto the battlefield, then shuffle.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Wight of the Relic Vault", "{B}{G}", "Creature — Zombie Knight", "Vigilance\nThis creature gets +1/+1 for each creature card in your graveyard.\n{T}, Sacrifice another creature: Search your library for a land card, put it onto the battlefield tapped, then shuffle.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Fiend Sculptor", "{B/G}{B/G}", "Creature — Nightmare", "This creature gets +1/+1 for each creature card in your graveyard.\n{X}{B/G}, {T}, Sacrifice another creature: Search your library for a creature card with mana value X or less, put it onto the battlefield, then shuffle. Activate only as a sorcery.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Tombcracker", "{B}", "Creature — Zombie", "{1}{B}, {T}, Discard a card: Create a 2/2 black Zombie creature token.\nTap three untapped Zombies you control: You draw a card and lose 1 life.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Baloth Sovereign", "{3}{G}", "Creature — Beast", "This creature enters tapped with six stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)\nWhenever you sacrifice a land, create a tapped 4/4 green Beast creature token and untap this creature.\n{4}, Sacrifice a land: You gain 2 life.", "10/10", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Famished Squirrel", "{B/G}", "Creature — Squirrel", "Whenever you sacrifice an artifact or creature, put a +1/+1 counter on this creature.\n{1}{B}{G}, Sacrifice an artifact or creature: You gain 1 life and draw a card.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Priest of Nameless Gods", "{1}{B}", "Creature — Human Cleric", "{T}, Sacrifice two other creatures: Any number of target players each lose 2 life and sacrifice a creature of their choice. You add {B}{B} and draw a card. (Activate only as an instant.)", "1/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Thessa, Beastwatcher", "{G}{U}", "Legendary Creature — Snake Elf Scout", "Whenever another nontoken creature you control enters, investigate.\n{T}, Sacrifice X Clues: Target opponent reveals the top X cards of their library. You may put a nonland permanent card with mana value X or less from among them onto the battlefield under your control. That player puts the rest on the bottom of their library in a random order.", "1/2", null, null),
        ),
    )

    private fun chunk45GraveyardCastingAndCantBlock() = listOf(
        CubeCardText(
            "Normal",
            face("Corpse Gnawer", "{B}", "Creature — Zombie", "This creature can't block.\nSacrifice a creature: Put a +1/+1 counter on this creature.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Shapeless Exile", "{B}", "Creature — Shapeshifter", "Changeling (This card is every creature type.)\nThis creature can't block and can't be blocked.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gorehaunt", "{B}{B}", "Creature — Vampire Spirit", "This creature can't block.\nThis creature has haste as long as an opponent has 10 or less life.\nLandfall — Whenever a land you control enters, you may return this card from your graveyard to the battlefield.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Bloodsworn Duelist", "{B}", "Creature — Human Warrior", "This creature can't block.\nRaid — {1}{B}: Return this card from your graveyard to the battlefield. Activate only if you attacked this turn.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Duskflame Phoenix", "{3}{B}", "Creature — Phoenix", "Flying, haste\nAt the beginning of combat on your turn, if two or more creatures died this turn, return this card from your graveyard to the battlefield.", "3/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Smoldering Spite", "{3}{R}", "Creature — Incarnation", "Haste\nAs long as this card is in your graveyard and you control a Mountain, creatures you control have haste.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Tombscuttler", "{B}", "Creature — Zombie", "This creature can't block.\nYou may cast this card from your graveyard as long as you control a Zombie.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Grommak, Risen Charnelhost", "{5}{B/G}{B/G}", "Legendary Creature — Avatar", "You can't spend mana to cast this spell.\nConvoke, delve (Each creature you tap while casting this spell pays for {1} or one mana of that creature's color. Each card you exile from your graveyard pays for {1}.)\nYou may cast this card from your graveyard.\nTrample", "8/8", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Inspector's Firebird", "{2}{R}", "Enchantment Creature — Phoenix", "Bestow—{R}, Collect evidence 6. (To pay this bestow cost, pay {R} and exile cards with total mana value 6 or greater from your graveyard.)\nFlying, haste\nEnchanted creature gets +2/+2 and has flying and haste.\nYou may cast this card from your graveyard using its bestow ability.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Velmora of the Sleep-Den", "{1}{W/B}{W/B}", "Legendary Creature — Cat Nightmare", "Companion — Each permanent card in your starting deck has mana value 2 or less. (If this card is your chosen companion, you may put it into your hand from outside the game for {3} as a sorcery.)\nLifelink\nOnce during each of your turns, you may cast a permanent spell with mana value 2 or less from your graveyard.", "3/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Colossal Rumblebeast", "{9}{R}", "Creature — Beast", "Suspend 10—{R}\nSacrifice an artifact, creature, or land: Remove a time counter from this card. Activate only if this card is suspended.", "9/7", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Seer of Harvestide", "{1}{G}{G}", "Creature — Human Druid", "You may look at the top card of your library any time.\nYou may play lands from the top of your library.\nCoven — As long as you control three or more creatures with different powers, you may cast creature spells from the top of your library.", "2/3", null, null),
        ),
    )

    private fun chunk46StaticCreatures() = listOf(
        CubeCardText(
            "Normal",
            face("Bough-Crowned Oracle", "{G}{G}", "Creature — Elf Druid", "Other Elves you control get +1/+1.\nWhenever you cast an Elf spell, you may pay {G}. If you do, draw a card.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Lord of the Aetherforge", "{2}{U}", "Artifact Creature — Vedalken Wizard", "Lord of the Aetherforge's power and toughness are each equal to the number of artifacts you control.\nOther artifact creatures you control get +1/+1.", "*/*", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Wyrm's Fury Conduit", "{R}", "Creature — Human Shaman", "Whenever you cast a noncreature spell, surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)\nDelirium — As long as there are four or more card types among cards in your graveyard, this creature gets +2/+2, has flying, and attacks each combat if able.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Elenwyn, Spinner of Dawn", "{1}{G}{G}", "Legendary Creature — Elf Noble", "Each other creature you control enters with a number of additional +1/+1 counters on it equal to Elenwyn's toughness.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gleaming Copycat", "{2}", "Artifact Creature — Shapeshifter", "As this creature enters, choose a creature type.\nThis creature is the chosen type in addition to its other types.\nEach other creature you control of the chosen type enters with an additional +1/+1 counter on it.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Torvanna, Elder-Tree Spirit", "{W}{W}", "Legendary Creature — Spirit Soldier", "Whenever another nontoken creature you control enters, bolster 1. (Choose a creature with the least toughness among creatures you control and put a +1/+1 counter on it.)", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Radiant Acolyte", "{1}{W}", "Creature — Human Cleric", "At the beginning of combat on your turn, put a +1/+1 counter on target creature you control.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Magnetite Colossus", "{4}", "Artifact Creature — Golem", "Nonartifact spells cost {1} more to cast.", "5/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Aetherbound Lawkeeper", "{1}{W}", "Artifact Creature — Human Cleric", "Each player who has cast a nonartifact spell this turn can't cast additional nonartifact spells.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Brassel, Gearwork Sage", "{2}{W}", "Legendary Creature — Elephant Artificer", "Prevent all combat damage that would be dealt to attacking artifact creatures you control.\nWhenever one or more artifact creatures you control enter, draw a card. This ability triggers only once each turn.", "2/4", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Wish of Aethervale", "{1}", "Legendary Artifact Creature — Thopter", "Flying\nSacrifice Wish of Aethervale: Until your next turn, target player who was dealt combat damage by Wish of Aethervale this turn can't cast noncreature spells.", "1/1", null, null),
        ),
    )

    private fun chunk47TriggeredCreatures() = listOf(
        CubeCardText(
            "Normal",
            face("Gloomblossom Carrier", "{B}{B}", "Creature — Faerie Rogue", "Flash\nFlying\nAt the beginning of your upkeep, you lose 1 life and create a 1/1 blue and black Faerie creature token with flying.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Shadow Informant", "{1}{B}", "Creature — Human Wizard", "At the beginning of your upkeep, reveal the top card of your library and put that card into your hand. You lose life equal to its mana value.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Morvek, Ghoulherd of Stensia", "{1}{B}", "Legendary Creature — Human Wizard", "At the beginning of your end step, if you control no creatures with decayed, create a 2/2 black Zombie creature token with decayed. (It can't block. When it attacks, sacrifice it at end of combat.)", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Excavation Tinker", "{2}{W}", "Creature — Dwarf Artificer", "Whenever you cast an artifact spell, you may pay {2}. If you do, create a 0/0 colorless Construct artifact creature token with \"This token gets +1/+1 for each artifact you control.\"", "3/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Etherglass Watcher", "{W}", "Artifact Creature — Human Soldier", "Whenever an opponent casts their first noncreature spell each turn, draw a card unless that player pays {X}, where X is this creature's power.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Invoice Shredder", "{1}{U}", "Creature — Bird Advisor", "Flying\nWhenever a player casts their second spell each turn, this creature connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)", "1/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Grubb, the Fingerless", "{1}{R}", "Legendary Creature — Goblin Wizard", "Whenever you cast an instant or sorcery spell, flip a coin. If you lose the flip, return that spell to its owner's hand. If you win the flip, copy that spell, and you may choose new targets for the copy.\nPartner (You can have two commanders if both have partner.)", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Atoq, Steward of the Dawn", "{1}{R}", "Legendary Creature — Human Knight", "Whenever you attack, you may discard a card. When you do, put a +1/+1 counter on target attacking creature. It gains trample until end of turn.\nWhenever you discard one or more cards, exile the top card of your library. You may play that card until your next end step.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Havoc Imp", "{1}{B}{R}", "Creature — Devil", "Whenever a player sacrifices a permanent, this creature deals 1 damage to any target.", "3/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Quarantine Automaton", "{2}", "Artifact Creature — Construct", "Whenever you discard a card, you may exile that card from your graveyard. If you do, you may play that card this turn.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Rampaging Reefshark", "{R}", "Creature — Shark Pirate", "Whenever you discard one or more cards, put that many +1/+1 counters on this creature.\nCycling {2} ({2}, Discard this card: Draw a card.)", "1/1", null, null),
        ),
    )

    private fun chunk50GraveyardCreatures() = listOf(
        CubeCardText(
            "Normal",
            face("Netherbound Turncoat", "{B}{B}", "Creature — Spirit", "Haste\nShadow (This creature can block or be blocked by only creatures with shadow.)\nWhenever another creature is put into your graveyard from the battlefield, you may pay {B}. If you do, return this card from your graveyard to the battlefield.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Pilfering Nibbler", "{U}{B}", "Creature — Faerie Rogue", "Flying\nWhen you draw your third card in a turn, return this card from your graveyard to the battlefield tapped.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Plague Shamblers", "{2}{B}", "Creature — Zombie", "Deathtouch\nCurse of the Walking Pox — Whenever you cast a spell from anywhere other than your hand, return this card from your graveyard to the battlefield tapped.", "3/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Skrit, Goblin Dawdler", "{2}{R}", "Legendary Creature — Goblin", "At the beginning of your upkeep, you may return this card from your graveyard to your hand.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Vengeroot", "{2}{G}{G}", "Creature — Elemental", "Haste\nWhenever you cast a spell, if it's the second creature spell you cast this turn, you may return this card from your graveyard to the battlefield.", "4/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Wyrm of Ashen Graves", "{5}{B}{B}", "Creature — Zombie Dragon", "Flying\nYou may cast this creature from your graveyard by paying {B}{B} and sacrificing two creatures rather than paying its mana cost.", "6/6", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Dunescour Digger", "{2}{G}", "Creature — Snake Cleric", "You may play lands from your graveyard.", "2/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ossira, Ancestral Herald", "{3}{W}", "Legendary Creature — Bird Cleric", "Flying\nWhenever you cast a historic spell, return target creature card with mana value 3 or less from your graveyard to the battlefield. (Artifacts, legendaries, and Sagas are historic.)", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mirebloat, the Undermuck", "{1}{G}{U}", "Legendary Creature — Ooze", "Trample\nWhenever a land card is put into your graveyard from anywhere, put a +1/+1 counter on Mirebloat.\nRemove three +1/+1 counters from Mirebloat: Return it to its owner's hand.\nWhen Mirebloat leaves the battlefield, return up to three target land cards from your graveyard to your hand.", "3/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("The Bogmaw Horror", "{3}{B}{G}", "Legendary Creature — Frog Horror", "Deathtouch\nAt the beginning of your upkeep, sacrifice The Bogmaw Horror unless you sacrifice a land.\nYou may play an additional land on each of your turns.\nWhenever one or more land cards are put into your graveyard from anywhere, draw a card.", "6/6", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Cinder Gatewarden", "{2}{R}", "Artifact Creature — Golem", "Whenever another creature you control enters, this creature deals 1 damage to each opponent.\nUnearth {R} ({R}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)", "2/3", null, null),
        ),
    )

    private fun chunk51SacrificeAndReturnOutlets() = listOf(
        CubeCardText(
            "Normal",
            face("Thicket Ranger", "{G}", "Creature — Elf Ranger", "Return a Forest you control to its owner's hand: Untap target creature. Activate only once each turn.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Glade Sprite Ranger", "{1}{G}", "Creature — Faerie Ranger", "Flash\nFlying, protection from blue\nReturn a Forest you control to its owner's hand: Untap target creature. Activate only once each turn.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Barkweave Symbiote", "{G}", "Creature — Insect", "Return an Elf you control to its owner's hand: Untap target creature. Activate only once each turn.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Tibbet Gearwhistle", "{1}{W}", "Legendary Creature — Gnome Artificer", "Magical Tinkering — {W}, {T}, Sacrifice an artifact: Search your library for an artifact card with mana value equal to 1 plus the sacrificed artifact's mana value, put it onto the battlefield, then shuffle. Activate only as a sorcery.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gloomwing Skulker", "{1}{B}", "Creature — Faerie Rogue", "Flying\nDiscard a card: This creature gets -2/-0 until end of turn. Any player may activate this ability.", "3/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Rancid Imp", "{B}", "Creature — Zombie Imp", "Discard a card: This creature gains flying until end of turn.\nThreshold — As long as there are seven or more cards in your graveyard, this creature gets +1/+1 and can't block.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Entrail Augur", "{B}", "Creature — Vampire Wizard", "Sacrifice a creature: Scry 1. (Look at the top card of your library. You may put that card on the bottom.)", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Shadowcollar Devotee", "{1}{B}", "Creature — Human Cleric", "Sacrifice another creature or artifact: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)", "3/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mossbound Keeper", "{G}", "Creature — Human Wizard", "Sacrifice a land: Target creature you control gains shroud until end of turn. (It can't be the target of spells or abilities.)", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Podrick Burrowfoot", "{G}{W}", "Legendary Creature — Halfling Peasant", "Whenever another nontoken creature you control enters, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")\nSacrifice three Foods: Return target historic card from your graveyard to your hand. (Artifacts, legendaries, and Sagas are historic.)", "2/2", null, null),
        ),
    )

    private fun chunk52StaticsAndCastTriggers() = listOf(
        CubeCardText(
            "Normal",
            face("Briar Sentry", "{G}", "Creature — Elf Warrior", "This creature doesn't untap during your untap step.\nWhenever you cast a green spell, you may untap this creature.", "2/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Scrapquilt Automaton", "{2}", "Artifact Creature — Construct", "Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)\nWhenever you cast an artifact spell, put a +1/+1 counter on this creature.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Novice Flamecaller", "{1}{R}", "Creature — Human Shaman", "Whenever you cast an instant or sorcery spell, create a 1/1 red Elemental creature token.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Seraphine, Keeper of the Gate", "{1}{W}", "Legendary Creature — Human Soldier", "First strike\nNoncreature spells cost {1} more to cast.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Mender of Afflictions", "{1}{W}", "Creature — Human Cleric", "If one or more -1/-1 counters would be put on a creature you control, that many -1/-1 counters minus one are put on it instead.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Shalecoil Wyrm", "{X}", "Artifact Creature — Snake", "Reach, trample, protection from multicolored\nThis creature enters with X +1/+1 counters on it.", "0/0", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ysolde, the Unbound Scholar", "{1}{U}{R}", "Legendary Creature — Human Wizard", "Ysolde gets +1/+0 for each instant and sorcery card in your graveyard.\nWhenever you discard one or more cards for the first time each turn, draw that many cards.", "0/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Silencing Automaton", "{2}", "Artifact Creature — Phyrexian Horror", "As this creature enters, choose a nonland card name.\nActivated abilities of sources with the chosen name can't be activated.", "2/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Crafty Pawnbroker", "{W}{U}", "Creature — Human Citizen", "As this creature enters, you may choose a nonland permanent.\nActivated abilities of the chosen permanent can't be activated.\nThis creature has all activated abilities of the chosen permanent except for loyalty abilities. You may spend mana as though it were mana of any color to activate those abilities.", "2/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Spiritwrangler", "{1}{W}{U}", "Creature — Spirit", "Whenever a creature is exiled from the battlefield, put a +1/+1 counter on this creature.\nAt the beginning of your end step, you may exile another target creature you control, then return that card to the battlefield under its owner's control.", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gossamer Coterie", "{1}{U}{U}", "Legendary Creature — Faerie Wizard", "Flash\nFlying\nWhen Gossamer Coterie enters, look at target player's hand. You may choose a nonland card from it. If you do, that player reveals the chosen card, puts it on the bottom of their library, then draws a card.", "3/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Ilsa, Verdant Seeker", "{2}{G}", "Legendary Creature — Elf Scout", "Landfall — Whenever a land you control enters, add one mana of any color. Then if this is the second time this ability has resolved this turn, reveal cards from the top of your library until you reveal an Elf or Elemental card. Put that card into your hand and the rest on the bottom of your library in a random order.", "3/3", null, null),
        ),
    )

    private fun chunk55LeftoverManaCreatures() = listOf(
        CubeCardText(
            "Normal",
            face("Gravebloom Augur", "{B/G}", "Creature — Elf Shaman", "{T}: Exile target land card from a graveyard. Add one mana of any color. (Activate only as an instant.)\n{B}, {T}: Exile target instant or sorcery card from a graveyard. Each opponent loses 2 life.\n{G}, {T}: Exile target creature card from a graveyard. You gain 2 life.", "1/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Gilt-Feather Gander", "{G}", "Creature — Bird", "Flying\nWhen this creature enters, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")\n{1}{G}, {T}: Create a Food token.\n{T}, Sacrifice a Food: Add one mana of any color.", "0/2", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Canopy Whisperer", "{G}", "Creature — Elf Druid", "Level up {1}{G} ({1}{G}: Put a level counter on this. Level up only as a sorcery.)\nLEVEL 1-4\n1/2\n{T}: Add {G}{G}.\nLEVEL 5+\n1/4\nElves you control have \"{T}: Add {G}{G}.\"", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Remorseless Corsair", "{3}{B}", "Creature — Human Pirate", "Whenever another creature you control dies, create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")", "1/4", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Shuffling Wight", "{B}", "Creature — Zombie", "When this creature dies, choose one —\n• Target creature an opponent controls gets -1/-1 until end of turn.\n• Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")", "1/1", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Burrow Soulbroker", "{2}{B}", "Creature — Zombie Goblin Wizard", "Pay 1 life, Sacrifice another creature: Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")", "3/3", null, null),
        ),
        CubeCardText(
            "Normal",
            face("Tamsin, Grand Mechanist", "{2}{U}{U}", "Legendary Creature — Human Artificer", "When Tamsin enters, create a 0/0 colorless Construct artifact creature token with \"This token gets +1/+1 for each artifact you control.\"\nTap an untapped artifact you control: Add {U}.\n{5}: Shuffle your library, then exile the top card. Until end of turn, you may play that card without paying its mana cost.", "1/4", null, null),
        ),
    )

    private fun chunk56MultiFacedCards() = listOf(
        CubeCardText(
            "ModalDoubleFaced",
            face("Skalda, God of Sagas", "{2}{R}", "Legendary Creature — God", "Whenever you cast a spell, add {R}. Until end of turn, you don't lose this mana as steps and phases end.\nCreatures you control can boast twice during each of your turns rather than once.", "3/3", null, null),
            face("Brennor, Horn of Plenty", "{4}{R}", "Legendary Artifact", "Discard a card: Exile the top two cards of your library. You may play those cards this turn.", null, null, null),
        ),
        CubeCardText(
            "ModalDoubleFaced",
            face("Summit Ascetic", "{3}{R}{R}", "Creature — Djinn Monk", "Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)\nWhen this creature enters, return target instant or sorcery card from your graveyard to your hand.", "2/2", null, null),
            face("Hermit Peak", null, "Land", "As this land enters, you may pay 3 life. If you don't, it enters tapped.\n{T}: Add {R}.", null, null, null),
        ),
        CubeCardText(
            "Adventure",
            face("Brash Pilferer", "{1}{U}{U}", "Creature — Faerie Rogue", "Flash\nFlying\nThis creature can block only creatures with flying.", "3/1", null, null),
            face("Petty Pinch", "{1}{U}", "Instant — Adventure", "Return target nonland permanent an opponent controls to its owner's hand.", null, null, null),
        ),
        CubeCardText(
            "Split",
            face("Expanse", "{U/R}{U/R}", "Instant", "Copy target instant or sorcery spell with mana value 4 or less. You may choose new targets for the copy.", null, null, null),
            face("Eruption", "{X}{U}{U}{R}{R}", "Instant", "Eruption deals X damage to any target. Target player draws X cards.", null, null, null),
        ),
        CubeCardText(
            "Transform",
            face("Orrin, Wayward Prodigy", "{1}{U}", "Legendary Creature — Human Wizard", "{T}: Draw a card, then discard a card. If there are five or more cards in your graveyard, exile Orrin, then return him to the battlefield transformed under his owner's control.", "0/2", null, null),
            face("Orrin, Mind Unbound", null, "Legendary Planeswalker — Jace", "[+1]: Up to one target creature gets -2/-0 until your next turn.\n[−3]: You may cast target instant or sorcery card from your graveyard this turn. If that spell would be put into your graveyard, exile it instead.\n[−9]: You get an emblem with \"Whenever you cast a spell, target opponent mills five cards.\"", null, "5", "U"),
        ),
        CubeCardText(
            "Transform",
            face("Haruka's Age of Honor", "{1}{W}", "Enchantment — Saga", "(As this Saga enters and after your draw step, add a lore counter.)\nI, II — Target creature gets +1/+1 until end of turn for each artifact and/or enchantment you control.\nIII — Exile this Saga, then return it to the battlefield transformed under your control.", null, null, null),
            face("Likeness of Haruka", null, "Enchantment Creature — Human Noble", "This creature gets +1/+1 for each artifact and/or enchantment you control.", "0/0", null, "W"),
        ),
        CubeCardText(
            "Transform",
            face("Shape Beneath the Floe", "{1}{U}", "Creature — Horror", "Defender\nThis creature enters with four ice counters on it.\nWhenever you cast an instant or sorcery spell, remove an ice counter from this creature. Then if it has no ice counters on it, transform it.", "0/4", null, null),
            face("Risen Leviathan", null, "Creature — Kraken Horror", "When this creature transforms into Risen Leviathan, return all non-Horror creatures to their owners' hands.", "7/8", null, "U"),
        ),
        CubeCardText(
            "Transform",
            face("Crimson Hexcaller", "{1}{B}", "Creature — Vampire Wizard", "Flying\nWhenever this creature or another nontoken creature you control dies, create a Blood token. (It's an artifact with \"{1}, {T}, Discard a card, Sacrifice this token: Draw a card.\")\nWhenever you create a Blood token, if you control five or more Blood tokens, transform this creature.", "2/1", null, null),
            face("Duskwing Summoner", null, "Creature — Vampire Wizard", "Flying\nAt the beginning of combat on your turn, up to one target Blood token you control becomes a 2/2 black Bat creature with flying and haste in addition to its other types.", "3/3", null, "B"),
        ),
    )

    private fun chunk57UnCards() = listOf(
        CubeCardText(
            "Normal",
            face("Blast from Before", "{2}{R}", "Instant", "Madness {R}, cycling {1}{R}, kicker {2}{R}, flashback {3}{R}, buyback {4}{R}\nBlast from Before deals 2 damage to any target. If this spell was kicked, create a 1/1 red Goblin creature token.", null, null, null),
        ),
    )
}
