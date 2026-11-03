package arcanum.engine.sets.eoe

import arcanum.engine.cards.Cards

/**
 * The invented Edge of Eternities (eoe) cards of the parser tests (`arcanum.parser.sets.eoe`), the same names and texts, for the
 * scenarios: each property registers its card and holds its name.
 */
internal object EoeSet {
    // chunk 01
    val stratosphericArboretum = Cards.invent("Stratospheric Arboretum", "{4}{G}", "Artifact — Spacecraft", """
        When this Spacecraft enters, put a +1/+1 counter on each creature you control.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 8+.)
        8+ | Flying, trample
    """.trimIndent(), "5/4")
    val highsunStarwardDreadnought = Cards.invent("Highsun, Starward Dreadnought", "{5}", "Legendary Artifact — Spacecraft", """
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 20+.)
        10+ | Whenever you attack, Highsun deals 100 damage to up to one target creature or planeswalker.
        20+ | Flying
    """.trimIndent(), "20/20")
    val wreckageBeltGrinder = Cards.invent("Wreckage Belt Grinder", "{4}{R}", "Artifact — Spacecraft", """
        When this Spacecraft enters, it deals 3 damage to any target.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 8+.)
        8+ | Flying
        {1}{R}: This Spacecraft gets +2/+0 until end of turn.
    """.trimIndent(), "1/5")
    val decayingWarcruiser = Cards.invent("Decaying Warcruiser", "{3}{B}", "Artifact — Spacecraft", """
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 8+.)
        1+ | Whenever an opponent discards a card, they lose 3 life.
        8+ | Flying, deathtouch
        Whenever this Spacecraft attacks, each opponent discards a card. Each opponent who can't loses 3 life.
    """.trimIndent(), "3/10")
    val snufferClassWarship = Cards.invent("Snuffer-Class Warship", "{8}", "Artifact — Spacecraft", """
        When this Spacecraft enters, destroy target noncreature permanent. Then this Spacecraft deals 4 damage to each creature.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 5+.)
        5+ | Flying, trample
    """.trimIndent(), "10/10")
    val grimGravityBarge = Cards.invent("Grim Gravity Barge", "{2}{B}", "Artifact — Spacecraft", """
        When this Spacecraft enters, mill three cards, then return a creature or Spacecraft card from your graveyard to your hand.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 8+.)
        8+ | Flying, lifelink
    """.trimIndent(), "3/2")
    val arcChargedBladeship = Cards.invent("Arc-Charged Bladeship", "{5}{R}", "Artifact — Spacecraft", """
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 3+.)
        3+ | Flying, haste
    """.trimIndent(), "6/5")
    val radianceClassCutter = Cards.invent("Radiance-Class Cutter", "{1}{W}", "Artifact — Spacecraft", """
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 12+.)
        2+ | Other creatures you control get +1/+1.
        12+ | Flying, lifelink
    """.trimIndent(), "3/5")
    val apexHunterFrigate = Cards.invent("Apex Hunter-Frigate", "{7}", "Artifact — Spacecraft", """
        When this Spacecraft enters, it deals 10 damage to up to one target creature.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 7+.)
        7+ | Flying
    """.trimIndent(), "7/7")
    val salvageLifeboat = Cards.invent("Salvage Lifeboat", "{5}{W}", "Artifact — Spacecraft", """
        When this Spacecraft enters, return target creature or enchantment card from your graveyard to the battlefield.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 10+.)
        10+ | Flying
    """.trimIndent(), "5/6")
    val theHalcyonWing = Cards.invent("The Halcyon Wing", "{1}{W}{W}", "Legendary Artifact — Spacecraft", """
        When The Halcyon Wing enters, search your library for a legendary creature card, reveal it, put it into your hand, then shuffle.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 7+.)
        7+ | Flying
        Other tapped legendary creatures you control have indestructible.
    """.trimIndent(), "5/5")
    val surveyProbeCutter = Cards.invent("Survey Probe Cutter", "{3}{U}", "Artifact — Spacecraft", """
        When this Spacecraft enters, draw two cards, then discard a card.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 8+.)
        8+ | Flying
    """.trimIndent(), "4/4")
    val warforgerGunboat = Cards.invent("Warforger Gunboat", "{2}{R}", "Artifact — Spacecraft", """
        When this Spacecraft enters, it deals damage equal to the number of artifacts you control to target creature an opponent controls.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 6+.)
        6+ | Flying
    """.trimIndent(), "4/3")
    val prismwedgeRamship = Cards.invent("Prismwedge Ramship", "{3}{W}", "Artifact — Spacecraft", """
        When this Spacecraft enters, create a 2/2 colorless Robot artifact creature token.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 9+.)
        9+ | Flying, first strike
    """.trimIndent(), "3/4")
    // chunk 02
    val corvessaGaleboundBastion = Cards.invent("Corvessa, Galebound Bastion", null, "Land — Planet", """
        This land enters tapped.
        {T}: Add {W}.
        Station (Tap another creature you control: Put charge counters equal to its power on this Planet. Station only as a sorcery.)
        12+ | {3}{W}, {T}: Create a token that's a copy of target artifact or enchantment you control, except it's legendary. Activate only as a sorcery.
    """.trimIndent())
    val thessivarStirringHaven = Cards.invent("Thessivar, Stirring Haven", null, "Land — Planet", """
        This land enters tapped.
        {T}: Add {G}.
        Station (Tap another creature you control: Put charge counters equal to its power on this Planet. Station only as a sorcery.)
        12+ | {G}, {T}: Add {G} for each creature you control.
    """.trimIndent())
    val endlessWaymarkCitadel = Cards.invent("Endless Waymark Citadel", "{W}{U}{B}{R}{G}", "Legendary Artifact — Spacecraft", """
        When Endless Waymark Citadel enters, create a tapped 2/2 colorless Robot artifact creature token for each multicolored permanent you control.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 12+.)
        12+ | Flying
        Whenever Endless Waymark Citadel attacks, draw a card for each multicolored permanent you control.
    """.trimIndent(), "7/15")
    val pyrrhosRemembranceWorld = Cards.invent("Pyrrhos, Remembrance World", null, "Land — Planet", """
        This land enters tapped.
        {T}: Add {R}.
        Station (Tap another creature you control: Put charge counters equal to its power on this Planet. Station only as a sorcery.)
        12+ | {1}{R}, {T}, Sacrifice a land: Create a 2/2 colorless Robot artifact creature token, then creatures you control get +1/+0 and gain haste until end of turn.
    """.trimIndent())
    val grublingPathfinderCraft = Cards.invent("Grubling Pathfinder Craft", "{2}{G}", "Artifact — Spacecraft", """
        When this Spacecraft enters, you may sacrifice a land or Lander. If you do, search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 7+.)
        7+ | Flying
    """.trimIndent(), "3/3")
    val maulClassSowingArk = Cards.invent("Maul-Class Sowing Ark", "{2}{G}", "Artifact — Spacecraft", """
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 7+.)
        7+ | Flying
        Whenever this Spacecraft attacks, you may put a creature card from your hand onto the battlefield.
    """.trimIndent(), "4/5")
    val sampleHauler = Cards.invent("Sample Hauler", "{5}{U}", "Artifact — Spacecraft", """
        When this Spacecraft enters, return up to two target non-Spacecraft creatures to their owners' hands.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 9+.)
        9+ | Flying
        Whenever this Spacecraft attacks, defending player mills four cards.
    """.trimIndent(), "4/7")
    val nyxaraTertiaHollowAltar = Cards.invent("Nyxara Tertia, Hollow Altar", null, "Land — Planet", """
        This land enters tapped.
        {T}: Add {B}.
        Station (Tap another creature you control: Put charge counters equal to its power on this Planet. Station only as a sorcery.)
        12+ | {1}{B}, {T}, Pay 2 life, Sacrifice a creature: Draw cards equal to the sacrificed creature's power. Activate only as a sorcery.
    """.trimIndent())
    val murmuringRequiemBarge = Cards.invent("Murmuring Requiem Barge", "{4}{B}", "Artifact — Spacecraft", """
        When this Spacecraft enters, each opponent sacrifices a nontoken creature of their choice.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 7+.)
        7+ | Flying
    """.trimIndent(), "4/3")
    val fabricatorTestbed = Cards.invent("Fabricator Testbed", "{U}", "Artifact — Spacecraft", """
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 9+.)
        2+ | At the beginning of combat on your turn, up to one other target artifact you control becomes an artifact creature with base power and toughness 2/2 and gains flying until end of turn.
        9+ | Flying, vigilance
    """.trimIndent(), "4/4")
    val thePerpetualLift = Cards.invent("The Perpetual Lift", "{5}", "Legendary Artifact — Spacecraft", """
        {T}: Add {C}{C}{C}.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery.)
        20+ | {T}: Add X mana of any one color, where X is the number of charge counters on The Perpetual Lift.
    """.trimIndent())
    val maeltharColossalGodcore = Cards.invent("Maelthar, Colossal Godcore", null, "Land — Planet", """
        This land enters tapped.
        {T}: Add {U}.
        Station (Tap another creature you control: Put charge counters equal to its power on this Planet. Station only as a sorcery.)
        12+ | {U}, {T}: Add {U} for each artifact you control.
    """.trimIndent())
    val coilwallSweepcraft = Cards.invent("Coilwall Sweepcraft", "{2}", "Artifact — Spacecraft", """
        When this Spacecraft enters, surveil 2.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 4+.)
        4+ | Flying
    """.trimIndent(), "2/2")
    // chunk 03
    val everyPathProwler = Cards.invent("Every-Path Prowler", "{3}{W}", "Creature — Drix Assassin", """
        When this creature enters, exile up to one target non-Assassin creature until this creature leaves the battlefield.
        Warp {1}{W} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "2/3")
    val celestineRetriever = Cards.invent("Celestine Retriever", "{3}{W}{W}", "Creature — Angel Warrior", """
        Flying
        When this creature enters, return target noncreature, nonland permanent card with mana value X or less from your graveyard to the battlefield, where X is the amount of mana spent to cast this creature.
        Warp {2}{W}
    """.trimIndent(), "5/4")
    val cipherbreakerMutt = Cards.invent("Cipherbreaker Mutt", "{2}{U}", "Creature — Dog", """
        When this creature enters, look at the top two cards of your library. Put one into your hand and the other into your graveyard.
        Warp {2}{U} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "2/1")
    val glorifiedDawnchild = Cards.invent("Glorified Dawnchild", "{3}{W}{W}", "Creature — Angel Wizard", """
        Flying, lifelink
        If one or more tokens would be created under your control, twice that many of those tokens are created instead.
        Warp {1}{W} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "4/5")
    val orlenaLedByStarlight = Cards.invent("Orlena, Led by Starlight", "{2}{W}", "Legendary Creature — Human Soldier", """
        Whenever Orlena or another creature or artifact you control enters, you gain 1 life.
        At the beginning of your end step, draw a card if you've gained 3 or more life this turn.
        Warp {W} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "3/3")
    val radiantSquireCommander = Cards.invent("Radiant Squire-Commander", "{3}{W}", "Creature — Human Knight", """
        When this creature enters, create a 1/1 white Human Soldier creature token.
        Warp {1}{W} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "3/2")
    val clockworkMedusoid = Cards.invent("Clockwork Medusoid", "{4}{U}{U}", "Artifact Creature — Robot Jellyfish", """
        When this creature enters, tap target artifact or creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        Warp {2}{U} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "5/5")
    val probabilitySphinx = Cards.invent("Probability Sphinx", "{3}{U}{U}", "Creature — Sphinx", """
        Flying
        When this creature enters, draw a card.
        As long as you have one or fewer cards in hand, if you would draw one or more cards, you draw that many cards plus one instead.
        Warp {1}{U}
    """.trimIndent(), "4/6")
    val beamswordTrooper = Cards.invent("Beamsword Trooper", "{2}{W}", "Creature — Human Soldier", """
        When this creature enters, put a +1/+1 counter on target creature you control.
        Whenever a nontoken creature you control with a +1/+1 counter on it dies, create a 1/1 white Human Soldier creature token.
        Warp {1}{W} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "2/2")
    val malignFrostwright = Cards.invent("Malign Frostwright", "{2}{U}", "Creature — Jellyfish Wizard", """
        When this creature enters, target creature an opponent controls gets -3/-0 until end of turn.
        Warp {U} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "2/3")
    val riftsurgeLeviathan = Cards.invent("Riftsurge Leviathan", "{4}{U}", "Creature — Whale", """
        Flying
        When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        Warp {1}{U} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "3/5")
    val nebulaHerdwarden = Cards.invent("Nebula Herdwarden", "{3}{W}{W}", "Creature — Angel", """
        Flying
        When this creature enters, search your library for a basic Plains card or a creature card with mana value 1 or less, reveal it, put it into your hand, then shuffle.
        Warp {1}{W} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "3/2")
    val nebulaChorister = Cards.invent("Nebula Chorister", "{3}{U}", "Creature — Human Bard", """
        If a permanent entering the battlefield causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.
        Warp {1}{U} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "3/4")
    val cometcoiler = Cards.invent("Cometcoiler", "{5}{U}{U}", "Creature — Leviathan", """
        Whenever a creature you control deals combat damage to a player, you may draw that many cards.
        Warp {2}{U}{U} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "7/7")
    val loombladeAugmenter = Cards.invent("Loomblade Augmenter", "{5}{W}", "Creature — Drix Artificer", """
        When this creature enters, put a +1/+1 counter on each of up to two target creatures.
        Warp {2}{W} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "3/4")
    // chunk 04
    val reverseTimeRemnant = Cards.invent("Reverse-Time Remnant", "{6}", "Creature — Eldrazi", """
        When this creature leaves the battlefield, draw a card, then you may put a permanent card with mana value less than or equal to the number of lands you control from your hand onto the battlefield tapped.
        Warp {4} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "7/5")
    val edgeOfTheHive = Cards.invent("Edge of the Hive", "{3}{B}", "Creature — Insect Assassin", """
        When this creature enters, choose one —
        • Put two +1/+1 counters on this creature.
        • Put target exiled card with warp on the bottom of its owner's library.
    """.trimIndent(), "3/1")
    val hivewatchVanguard = Cards.invent("Hivewatch Vanguard", "{X}{G}{G}", "Creature — Insect Knight", """
        This creature enters with X +1/+1 counters on it.
        When this creature leaves the battlefield, put its counters on target creature you control.
        Warp {X}{G} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "0/0")
    val forgottenJuggernaut = Cards.invent("Forgotten Juggernaut", "{9}", "Artifact Creature — Robot Giant", "Warp {3} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)", "9/9")
    val nearCollision = Cards.invent("Near Collision", "{1}{G}", "Instant", """
        As an additional cost to cast this spell, choose a creature you control or a warped creature card you own in exile.
        Near Collision deals damage equal to the power of the chosen creature or card to target creature.
    """.trimIndent())
    val drixDestinyweaver = Cards.invent("Drix Destinyweaver", "{3}{G}", "Creature — Drix Wizard", """
        When this creature enters, put a +1/+1 counter on target creature.
        Each creature you control with a +1/+1 counter on it has trample.
        Warp {1}{G} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "3/2")
    val colonyArchitecture = Cards.invent("Colony Architecture", "{3}{G}{G}", "Enchantment", """
        Landfall — Whenever a land you control enters, create a 2/2 colorless Robot artifact creature token.
        Warp {1}{G} (You may cast this card from your hand for its warp cost. Exile this enchantment at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent())
    val maximumThrust = Cards.invent("Maximum Thrust", "{R}", "Instant", "Target creature you control gets +3/+2 until end of turn. If that creature was cast for its warp cost, it also gains trample and haste until end of turn.")
    val sproutingCrawler = Cards.invent("Sprouting Crawler", "{4}{G}", "Creature — Plant Wurm", """
        When this creature enters, you gain 2 life.
        Warp {1}{G} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "5/5")
    val cargoBayStaging = Cards.invent("Cargo Bay Staging", "{3}{G}", "Enchantment", """
        If one or more counters would be put on a creature, Spacecraft, or Planet you control, twice that many of each of those kinds of counters are put on it instead.
        Warp {G} (You may cast this card from your hand for its warp cost. Exile this enchantment at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent())
    val fallenSquadCaptain = Cards.invent("Fallen Squad Captain", "{3}{R}", "Creature — Kavu Soldier", """
        During your turn, other creatures you control get +1/+0.
        Warp {1}{R} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "4/3")
    val strengthshapeAttuner = Cards.invent("Strengthshape Attuner", "{2}{G}{G}", "Creature — Insect Druid", """
        Landfall — Whenever a land you control enters, double the power of target creature you control until end of turn.
        Warp {2}{G} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "4/4")
    val supernovaWyrm = Cards.invent("Supernova Wyrm", "{3}{R}{R}", "Creature — Dragon", """
        Flying, haste
        When this creature enters, it deals 1 damage to target creature an opponent controls.
        Warp {2}{R} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "4/5")
    val apogeeSummoner = Cards.invent("Apogee Summoner", "{4}{B}", "Creature — Horror", """
        When this creature enters, until end of turn, another target creature you control gets +2/+0 and gains "When this creature dies, return it to the battlefield tapped under its owner's control."
        Warp {1}{B} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "4/5")
    val summitEnvoy = Cards.invent("Summit Envoy", "{1}{U}{R}", "Artifact Creature — Robot", """
        Whenever you cast an artifact spell, create a 1/1 colorless Drone artifact creature token with flying and "This token can block only creatures with flying."
        Warp {U/R} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "3/3")
    val probabilityMechanic = Cards.invent("Probability Mechanic", "{2}{R}", "Creature — Kavu Artificer", """
        Whenever this creature or another Kavu you control enters, exile the top card of your library. For as long as that card remains exiled, you may play it if you control a Kavu.
        Warp {1}{R} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "3/3")
    val crimsonPantherAutomaton = Cards.invent("Crimson Panther Automaton", "{3}{R}", "Artifact Creature — Robot Cat", """
        Haste
        Warp {1}{R} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "3/3")
    val murmuringNullspawn = Cards.invent("Murmuring Nullspawn", "{2}{B}", "Creature — Vampire Soldier", """
        Whenever this creature or another creature or artifact you control dies, target opponent loses 1 life and you gain 1 life.
        Warp {B} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "2/2")
    val brakkosStalwartLieutenant = Cards.invent("Brakkos, Stalwart Lieutenant", "{2}{R}{R}", "Legendary Creature — Kavu Pilot", """
        Other creatures you control have haste.
        Artifact cards and red creature cards in your hand have warp {2}{R}. (You may cast a card from your hand for its warp cost. Exile that permanent at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "3/5")
    val chronolineReaper = Cards.invent("Chronoline Reaper", "{B}{B}", "Creature — Drix Warlock", """
        Haste
        You may cast this card from your graveyard using its warp ability.
        Warp—{B}, Pay 2 life. (You may cast this card from your hand or graveyard for its warp cost. If you do, exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "2/2")
    val loomstalkerZealot = Cards.invent("Loomstalker Zealot", "{2}{R}", "Creature — Drix Artificer", """
        Whenever another creature or artifact you control enters, this creature deals 1 damage to each opponent.
        Warp {R} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "2/3")
    // chunk 05
    val velunirRockchosen = Cards.invent("Velunir, Rockchosen", "{3}{B}{B}", "Legendary Creature — Human Cleric", """
        Ward—Discard a card at random.
        Void — Whenever Velunir attacks, if a nonland permanent left the battlefield this turn or a spell was warped this turn, defending player loses half their life, rounded up.
    """.trimIndent(), "3/3")
    val hymnalOfTheAbyss = Cards.invent("Hymnal of the Abyss", "{3}{B}", "Enchantment — Aura", """
        Enchant creature you control
        Whenever enchanted creature attacks, put target creature card from defending player's graveyard onto the battlefield under your control tapped and attacking.
        Void — At the beginning of your end step, sacrifice this Aura unless a nonland permanent left the battlefield this turn or a spell was warped this turn.
    """.trimIndent())
    val decipherSignals = Cards.invent("Decipher Signals", "{2}{B}", "Sorcery", """
        You draw two cards and lose 2 life.
        Void — If a nonland permanent left the battlefield this turn or a spell was warped this turn, instead you draw two cards and each opponent loses 2 life.
    """.trimIndent())
    val lamentNovice = Cards.invent("Lament Novice", "{2}{B}{B}", "Creature — Human Cleric", """
        Lifelink
        Whenever one or more creatures you control deal combat damage to a player, you draw a card and lose 1 life.
        Void — At the beginning of your end step, if a nonland permanent left the battlefield this turn or a spell was warped this turn, create a 2/2 colorless Robot artifact creature token.
    """.trimIndent(), "4/4")
    val nullsteelEdge = Cards.invent("Nullsteel Edge", "{B}", "Artifact — Equipment", """
        Equipped creature gets +3/+1.
        Void — At the beginning of your end step, if a nonland permanent left the battlefield this turn or a spell was warped this turn, attach this Equipment to target creature you control.
        Equip {4} ({4}: Attach to target creature you control. Equip only as a sorcery.)
    """.trimIndent())
    val psalmOfTheFallenStar = Cards.invent("Psalm of the Fallen Star", "{1}{B}", "Sorcery", """
        Surveil 1, then you draw a card and lose 1 life. (To surveil 1, look at the top card of your library. You may put it into your graveyard.)
        Void — If a nonland permanent left the battlefield this turn or a spell was warped this turn, draw another card.
    """.trimIndent())
    val ravenousScuttlejaw = Cards.invent("Ravenous Scuttlejaw", "{2}{B}", "Creature — Insect Horror", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Void — At the beginning of your end step, if a nonland permanent left the battlefield this turn or a spell was warped this turn, put a +1/+1 counter on this creature.
    """.trimIndent(), "2/2")
    val interdictionAutomaton = Cards.invent("Interdiction Automaton", "{2}{B}{R}", "Artifact Creature — Robot", """
        Flying
        When this creature enters, return target artifact or creature card from your graveyard to your hand.
        Void — At the beginning of your end step, if a nonland permanent left the battlefield this turn or a spell was warped this turn, put a +1/+1 counter on this creature.
    """.trimIndent(), "2/2")
    val pyrrhosCloudsentry = Cards.invent("Pyrrhos Cloudsentry", "{4}{R}", "Creature — Kavu Soldier", """
        Reach
        Void — At the beginning of your end step, if a nonland permanent left the battlefield this turn or a spell was warped this turn, put a +1/+1 counter on this creature.
    """.trimIndent(), "4/5")
    val ionLance = Cards.invent("Ion Lance", "{R}", "Sorcery", """
        Ion Lance deals 2 damage to any target.
        Void — Ion Lance deals 3 damage instead if a nonland permanent left the battlefield this turn or a spell was warped this turn.
    """.trimIndent())
    val wanderingServomotor = Cards.invent("Wandering Servomotor", "{3}{R}", "Artifact Creature — Robot", "Void — When this creature enters, if a nonland permanent left the battlefield this turn or a spell was warped this turn, exile up to one target instant or sorcery card with mana value 2 or less from your graveyard. Copy it. You may cast the copy without paying its mana cost.", "3/4")
    val chronalInterference = Cards.invent("Chronal Interference", "{2}{B}", "Sorcery", """
        Void — This spell costs {2} less to cast if a nonland permanent left the battlefield this turn or a spell was warped this turn.
        Target opponent reveals their hand. You choose a nonland card from it. That player discards that card.
    """.trimIndent())
    val doomedArc = Cards.invent("Doomed Arc", "{B}", "Sorcery", """
        Target creature gets -2/-2 until end of turn.
        Void — That creature gets -10/-10 until end of turn instead if a nonland permanent left the battlefield this turn or a spell was warped this turn.
    """.trimIndent())
    val nullwroughtColossus = Cards.invent("Nullwrought Colossus", "{4}{B}", "Artifact Creature — Robot Warrior", "Void — At the beginning of your end step, if a nonland permanent left the battlefield this turn or a spell was warped this turn, you draw a card and lose 1 life.", "5/4")
    // chunk 06
    val lasercutSurveyor = Cards.invent("Lasercut Surveyor", "{1}{B}", "Creature — Human Artificer", "When this creature dies, create a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")", "2/1")
    val geneTailoredTomorrow = Cards.invent("Gene-Tailored Tomorrow", "{1}{G}{G}", "Enchantment", """
        When this enchantment enters, create a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
        Each creature you control enters with an additional +1/+1 counter on it for each land that entered the battlefield under your control this turn.
    """.trimIndent())
    val bioforgeMachinist = Cards.invent("Bioforge Machinist", "{G}{U}", "Creature — Insect Artificer", """
        When this creature enters, create a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
        {8}: Draw two cards and create a 2/2 colorless Robot artifact creature token.
    """.trimIndent(), "2/2")
    val geneLabExpert = Cards.invent("Gene-Lab Expert", "{R}{G}", "Creature — Insect Scientist", """
        When this creature enters, create a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
        Whenever you sacrifice an artifact, this creature deals 2 damage to target opponent.
    """.trimIndent(), "1/3")
    val fearlessSalvagebot = Cards.invent("Fearless Salvagebot", "{3}", "Artifact Creature — Robot", "When this creature enters, exile each opponent's graveyard. Create a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")", "3/1")
    val avertCatastrophe = Cards.invent("Avert Catastrophe", "{1}{U}", "Instant", "Counter target spell unless its controller pays {2}. If they do, you create a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")")
    val frontierCrawler = Cards.invent("Frontier Crawler", "{G}", "Artifact Creature — Robot Scout", """
        Reach
        When this creature dies, each player creates a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
    """.trimIndent(), "2/2")
    val escapePodLaunch = Cards.invent("Escape Pod Launch", "{2}{W}", "Instant", "Destroy target nonland permanent. Its controller creates a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")")
    val starlaneVoyager = Cards.invent("Starlane Voyager", "{2}{G}", "Creature — Human Scout", "When this creature enters, create a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")", "3/3")
    val icefieldWorldmaw = Cards.invent("Icefield Worldmaw", "{5}{G}{G}", "Creature — Leviathan", """
        Trample
        When this creature enters, create a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
        Landfall — Whenever a land you control enters, creatures you control get +1/+1 and gain vigilance and haste until end of turn.
    """.trimIndent(), "6/6")
    val kavuGroundfinder = Cards.invent("Kavu Groundfinder", "{3}{R}", "Creature — Kavu Soldier", """
        Menace (This creature can't be blocked except by two or more creatures.)
        When this creature enters, create a Lander token. At the beginning of the end step on your next turn, sacrifice that token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
    """.trimIndent(), "4/3")
    val hardTouchdown = Cards.invent("Hard Touchdown", "{2}{R}", "Instant", "Create a Lander token. Then you may sacrifice an artifact. When you do, Hard Touchdown deals 2 damage to each creature. (A Lander token is an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")")
    val reentryDive = Cards.invent("Reentry Dive", "{3}{R}", "Sorcery", "Reentry Dive deals 6 damage to target creature. If excess damage was dealt this way, create a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")")
    val orlosWanderlust = Cards.invent("Orlo's Wanderlust", "{G}", "Sorcery", "You gain 2 life. Create a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")")
    val scavengeForForever = Cards.invent("Scavenge for Forever", "{2}{B}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice an artifact or creature.
        Return target creature or Spacecraft card with mana value 5 or less from your graveyard to the battlefield. Then create a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
    """.trimIndent())
    val sowingArkCultivator = Cards.invent("Sowing-Ark Cultivator", "{3}{G}", "Creature — Insect Scientist", """
        Whenever this creature becomes tapped, create a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
        Landfall — Whenever a land you control enters, put a +1/+1 counter on this creature.
    """.trimIndent(), "3/3")
    val sowingArkCollision = Cards.invent("Sowing-Ark Collision", "{1}{G}", "Instant", "Destroy target artifact or enchantment. If its mana value was 2 or less, create a Lander token. (It's an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")")
    val daystarFrontierist = Cards.invent("Daystar Frontierist", "{1}{W}", "Creature — Human Knight", """
        When this creature enters, if an opponent controls more lands than you, create a Lander token. (It's an artifact with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
        Landfall — Whenever a land you control enters, this creature gets +1/+0 until end of turn.
    """.trimIndent(), "2/3")
    val groundtreatyEnforcer = Cards.invent("Groundtreaty Enforcer", "{1}{R}", "Creature — Kavu Scout", "When this creature enters, target opponent may have you create two Lander tokens. If they don't, put two +1/+1 counters on this creature. (A Lander token is an artifact with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")", "2/1")
    // chunk 07
    val backupThrusters = Cards.invent("Backup Thrusters", "{4}{W}", "Artifact — Equipment", """
        When this Equipment enters, create a 2/2 colorless Robot artifact creature token and attach this Equipment to it.
        Equipped creature gets +1/+2 and has flying.
        Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
    """.trimIndent())
    val exilingRadiance = Cards.invent("Exiling Radiance", "{2}{W}", "Enchantment", "When this enchantment enters, exile target nonland permanent an opponent controls until this enchantment leaves the battlefield.")
    val pastTheSilence = Cards.invent("Past the Silence", "{3}{W}{W}", "Sorcery", "Exile all creatures and Spacecraft.")
    val glowlanceDevotee = Cards.invent("Glowlance Devotee", "{2}{W}", "Creature — Human Soldier", """
        Vigilance
        This creature gets +2/+0 as long as you've cast two or more spells this turn.
    """.trimIndent(), "2/4")
    val starhighApex = Cards.invent("Starhigh Apex", "{2}{W}", "Creature — Human Soldier", """
        Whenever you cast your second spell each turn, choose one —
        • Create two 1/1 white Human Soldier creature tokens.
        • Put a +1/+1 counter on each creature you control.
    """.trimIndent(), "2/4")
    val daybreakSpearhead = Cards.invent("Daybreak Spearhead", "{5}{W}", "Creature — Human Knight", """
        Lifelink
        At the beginning of your end step, if you control two or more tapped creatures, put a +1/+1 counter on each creature you control other than this creature.
    """.trimIndent(), "4/5")
    val hangarLoaderDrone = Cards.invent("Hangar Loader Drone", "{1}{W}", "Artifact Creature — Robot", """
        This creature enters with a +1/+1 counter on it.
        When this creature dies, put its counters on target creature you control.
    """.trimIndent(), "1/1")
    val twinStarDisciples = Cards.invent("Twin-Star Disciples", "{2}{W}", "Creature — Human Soldier", """
        Double strike
        {5}: Creatures you control get +1/+1 until end of turn.
    """.trimIndent(), "2/2")
    val twinStarForm = Cards.invent("Twin-Star Form", "{1}{W}", "Instant", "Target creature you control gains double strike until end of turn. If it has a +1/+1 counter on it, draw a card.")
    val powersuitRescuer = Cards.invent("Powersuit Rescuer", "{2}{W}", "Creature — Human Soldier", """
        Flying
        When this creature enters, return up to one other target permanent you control to its owner's hand.
    """.trimIndent(), "2/2")
    val launchBayMarshal = Cards.invent("Launch-Bay Marshal", "{2}{W}", "Creature — Human Soldier", "At the beginning of your end step, if you control two or more tapped creatures, you gain 2 life.", "3/3")
    val concentratedBarrage = Cards.invent("Concentrated Barrage", "{W}", "Instant", "Concentrated Barrage deals X damage to target attacking or blocking creature, where X is 2 plus the number of creatures and/or Spacecraft you control.")
    val solidLightPrison = Cards.invent("Solid-Light Prison", "{W}", "Enchantment — Aura", """
        Enchant artifact you control
        When this Aura enters, exile target creature an opponent controls until this Aura leaves the battlefield.
        Enchanted permanent has ward {1}.
    """.trimIndent())
    val laurelOfMerit = Cards.invent("Laurel of Merit", "{W}", "Sorcery", """
        Put a +1/+1 counter on target creature.
        Draw a card.
    """.trimIndent())
    val decoratedKnightMarshal = Cards.invent("Decorated Knight-Marshal", "{1}{W}", "Creature — Human Advisor Knight", """
        When this creature enters, create a 1/1 white Human Soldier creature token.
        {4}{W}{W}, Sacrifice this creature: Search your library for an Equipment card, put it onto the battlefield, then shuffle.
    """.trimIndent(), "1/1")
    // chunk 08
    val glowstallInterrogator = Cards.invent("Glowstall Interrogator", "{W}", "Creature — Angel Wizard", """
        Vigilance
        When this creature enters, each opponent exiles a card from their hand and may play that card for as long as it remains exiled. Each spell cast this way costs {1} more to cast. Each land played this way enters tapped.
    """.trimIndent(), "2/1")
    val lumenknightVanguard = Cards.invent("Lumenknight Vanguard", "{3}{W}", "Creature — Human Knight", "This creature enters with a +1/+1 counter on it for each other creature and/or artifact you control.", "2/2")
    val summitStarprison = Cards.invent("Summit Starprison", "{1}{W}{W}", "Artifact", """
        When this artifact enters, exile all artifacts and creatures with mana value 2 or less until this artifact leaves the battlefield.
        {6}{W}{W}: Put each card exiled with this artifact into its owner's graveyard, then create a 2/2 colorless Robot artifact creature token for each card put into a graveyard this way. Sacrifice this artifact.
    """.trimIndent())
    val quasarWingAce = Cards.invent("Quasar Wing Ace", "{1}{W}", "Creature — Human Pilot", "When this creature enters, look at the top five cards of your library. You may reveal a Spacecraft card from among them and put it into your hand. Put the rest on the bottom of your library in a random order. If you didn't put a card into your hand this way, put a +1/+1 counter on this creature.", "1/2")
    val gleamingBlow = Cards.invent("Gleaming Blow", "{3}{W}", "Instant", "Destroy target artifact or tapped creature. You gain 3 life.")
    val redirectCircuits = Cards.invent("Redirect Circuits", "{W}", "Instant", """
        Choose one —
        • Target artifact or creature gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
        • Redirect Circuits deals 2 damage to target tapped creature.
    """.trimIndent())
    val searchTheWreckage = Cards.invent("Search the Wreckage", "{2}{W}", "Sorcery", "Return up to three target creature cards with total mana value 3 or less from your graveyard to the battlefield. Put a +1/+1 counter on each of them.")
    val riftTear = Cards.invent("Rift Tear", "{W}", "Enchantment", "When this enchantment enters, exile target nonland permanent an opponent controls with mana value 2 or less until this enchantment leaves the battlefield.")
    val pagesGlowblade = Cards.invent("Page's Glowblade", "{W}", "Artifact — Equipment", """
        Flash
        When this Equipment enters, attach it to target creature you control. That creature gains first strike until end of turn.
        Equipped creature gets +1/+0.
        Equip {3}
    """.trimIndent())
    val dogfighterAce = Cards.invent("Dogfighter Ace", "{1}{W}", "Creature — Human Pilot", "Whenever this creature becomes tapped, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)", "2/2")
    val spacedockGuard = Cards.invent("Spacedock Guard", "{W}", "Artifact Creature — Robot Soldier", "{3}{W}, {T}: Tap another target creature. This ability costs {2} less to activate if you control a creature with a +1/+1 counter on it.", "1/1")
    val daystarCleric = Cards.invent("Daystar Cleric", "{1}{W}", "Creature — Human Cleric", """
        At the beginning of your end step, if you control two or more tapped creatures, put a +1/+1 counter on target creature you control.
        {2}, Remove a +1/+1 counter from a creature you control: Tap target artifact or creature.
    """.trimIndent(), "3/2")
    val daystarGlowsmith = Cards.invent("Daystar Glowsmith", "{3}{W}", "Creature — Human Artificer", "Whenever you cast your second spell each turn, put a +1/+1 counter on this creature and draw a card.", "3/3")
    val ferventShowing = Cards.invent("Fervent Showing", "{2}{W}", "Instant", "Creatures you control get +2/+0 until end of turn. If it's not your turn, untap those creatures.")
    // chunk 09
    val voidOut = Cards.invent("Void Out", "{U}", "Instant", "Counter target artifact or enchantment spell.")
    val subatomicShrinker = Cards.invent("Subatomic Shrinker", "{U}", "Artifact — Equipment", """
        Equipped creature gets +1/+0.
        Whenever equipped creature attacks, choose up to one target creature. That creature can't be blocked this turn and has base power and toughness 1/1 until end of turn.
        Equip {2}
    """.trimIndent())
    val neuralUpload = Cards.invent("Neural Upload", "{4}{U}", "Instant", "Surveil X, where X is the number of artifacts you control. Then draw three cards. (To surveil X, look at the top X cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)")
    val mistshaperMechanic = Cards.invent("Mistshaper Mechanic", "{2}{U}", "Creature — Jellyfish Artificer", """
        Flying
        As long as you control an artifact, this creature gets +1/+0.
    """.trimIndent(), "1/4")
    val readTheConstellations = Cards.invent("Read the Constellations", "{1}{U}", "Instant", """
        Kicker {1}{U} (You may pay an additional {1}{U} as you cast this spell.)
        Look at the top X cards of your library, where X is the number of lands you control. Put one of those cards into your hand. If this spell was kicked, put two of those cards into your hand instead. Put the rest on the bottom of your library in a random order.
    """.trimIndent())
    val stasisRelic = Cards.invent("Stasis Relic", "{1}{U}", "Artifact", """
        When this artifact enters or leaves the battlefield, draw a card.
        {1}{U}, Sacrifice this artifact: Put a stun counter on up to one target tapped creature. (If a permanent with a stun counter would become untapped, remove one from it instead.)
    """.trimIndent())
    val frostfracture = Cards.invent("Frostfracture", "{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets -5/-0.
        When enchanted creature becomes tapped or is dealt damage, destroy it.
    """.trimIndent())
    val unmakingBurst = Cards.invent("Unmaking Burst", "{1}{U}", "Instant", "Return target nonland permanent to its owner's hand. If it was attacking, create a 1/1 colorless Drone artifact creature token with flying and \"This token can block only creatures with flying.\"")
    val envoyBodyguard = Cards.invent("Envoy Bodyguard", "{1}{U}", "Artifact Creature — Robot Soldier", "This creature gets +X/+0, where X is the greatest mana value among other artifacts you control.", "0/4")
    val megatempestColossus = Cards.invent("Megatempest Colossus", "{4}{U}", "Creature — Elemental", "This spell costs {3} less to cast if you've cast another spell this turn.", "4/4")
    val drifterWindedge = Cards.invent("Drifter Windedge", "{U}", "Creature — Jellyfish Warrior", """
        Flash
        Flying
        {2}, Sacrifice this creature: Draw a card.
    """.trimIndent(), "1/1")
    val drifterSneak = Cards.invent("Drifter Sneak", "{2}{U}", "Creature — Jellyfish Rogue", """
        This creature can't be blocked if you've cast two or more spells this turn.
        Whenever this creature deals combat damage to a player, draw a card.
    """.trimIndent(), "1/3")
    val drifterGlareScrambler = Cards.invent("Drifter Glare Scrambler", "{1}{U}", "Artifact — Equipment", """
        Flash
        When this Equipment enters, attach it to target creature you control. That creature gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)
        Equipped creature gets +1/+2.
        Equip {3}
    """.trimIndent())
    val drifterAgent = Cards.invent("Drifter Agent", "{1}{U}", "Creature — Jellyfish Rogue", "Whenever you cast your second spell each turn, put a +1/+1 counter on this creature.", "2/1")
    val adriftInTheDark = Cards.invent("Adrift in the Dark", "{3}{U}", "Instant", "Target artifact or creature's owner puts it on their choice of the top or bottom of their library. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)")
    // chunk 10
    val automatonFabricator = Cards.invent("Automaton Fabricator", "{4}{U}", "Artifact Creature — Robot Artificer", "Whenever another artifact you control enters, create a 2/2 colorless Robot artifact creature token. This ability triggers only once each turn.", "4/4")
    val automatonHelmsman = Cards.invent("Automaton Helmsman", "{1}{U}", "Artifact Creature — Robot Pilot", "Whenever this creature becomes tapped, draw a card, then discard a card.", "2/1")
    val automatonBulwark = Cards.invent("Automaton Bulwark", "{1}{U}", "Artifact Creature — Robot Soldier", """
        Defender
        As long as an artifact entered the battlefield under your control this turn, this creature can attack as though it didn't have defender.
    """.trimIndent(), "3/2")
    val mindTuning = Cards.invent("Mind Tuning", "{1}{U}", "Instant", """
        This spell costs {1} less to cast during your turn.
        Tap target artifact or creature.
        Draw a card.
    """.trimIndent())
    val quellanTheTrustedHand = Cards.invent("Qu'ellan, the Trusted Hand", "{3}{U}{U}", "Legendary Creature — Jellyfish Advisor", """
        Flying
        You may look at the top card of your library any time.
        You may cast artifact spells from the top of your library.
        Artifacts you control have "{T}: Add {U}. Spend this mana only to cast a spell from anywhere other than your hand."
    """.trimIndent(), "3/4")
    val lunarContemplation = Cards.invent("Lunar Contemplation", "{2}{U}", "Enchantment — Aura", """
        Enchant artifact or creature you control
        The first time you would create one or more tokens each turn, you may instead create that many tokens that are copies of enchanted permanent.
    """.trimIndent())
    val mawOfTheTempest = Cards.invent("Maw of the Tempest", "{6}{U}", "Creature — Elemental", """
        Flying
        Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
        When this creature enters, creatures your opponents control get -3/-0 until your next turn.
    """.trimIndent(), "6/6")
    val microframeWatcher = Cards.invent("Microframe Watcher", "{2}{U}", "Artifact Creature — Robot", "Whenever this creature becomes tapped, untap another target permanent. This ability triggers only once each turn.", "3/2")
    val siftForSalvage = Cards.invent("Sift for Salvage", "{3}{U}", "Instant", """
        Choose one or both —
        • Search your library for an artifact card, reveal it, put it into your hand, then shuffle.
        • Return target artifact card from your graveyard to your hand.
    """.trimIndent())
    val selfbuiltAutomaton = Cards.invent("Selfbuilt Automaton", "{3}{U}", "Artifact Creature — Robot Artificer", "When this creature enters, you may sacrifice an artifact. When you do, put a +1/+1 counter on target creature and draw a card.", "3/4")
    val ironflockController = Cards.invent("Ironflock Controller", "{1}{U}", "Artifact Creature — Robot Soldier", """
        Flying
        {T}: Add {U}. Spend this mana only to cast an artifact spell.
        {T}: Add {U}{U}. Spend this mana only to activate abilities of artifact sources.
    """.trimIndent(), "1/1")
    val gravityTether = Cards.invent("Gravity Tether", "{2}{U}{U}", "Enchantment — Aura", """
        Enchant creature or Spacecraft
        When this Aura enters, tap enchanted permanent.
        You control enchanted permanent.
        Enchanted permanent doesn't untap during its controller's untap step.
    """.trimIndent())
    val unspool = Cards.invent("Unspool", "{1}{U}{U}", "Instant", "Counter target spell. If the amount of mana spent to cast that spell was less than its mana value, you draw a card.")
    val maeltharMindbender = Cards.invent("Maelthar Mindbender", "{2}{U}", "Creature — Jellyfish Scientist", "The second spell you cast each turn costs {2} less to cast.", "2/4")
    val loomstriding = Cards.invent("Loomstriding", "{4}{U}{U}", "Enchantment", """
        When this enchantment enters, if you cast it, shuffle your hand and graveyard into your library, then draw seven cards.
        The first spell each player casts during each of their turns may be cast without paying its mana cost.
    """.trimIndent())
    // chunk 11
    val nemesissCharm = Cards.invent("Nemesis's Charm", "{B}{B}{B}", "Instant", """
        Choose one —
        • Exile target creature or planeswalker.
        • Return one or two target creature and/or planeswalker cards from your graveyard to your hand.
        • Put two +1/+1 counters on target creature you control. It gains lifelink until end of turn.
    """.trimIndent())
    val meteorCreeper = Cards.invent("Meteor Creeper", "{2}{B}", "Creature — Insect Horror", """
        Lifelink
        Whenever this creature attacks, you may sacrifice another creature or artifact. If you do, this creature gets +2/+0 until end of turn.
    """.trimIndent(), "2/3")
    val shadowedFortitude = Cards.invent("Shadowed Fortitude", "{1}{B}", "Instant", """
        This spell costs {1} less to cast if it targets a blocking creature.
        Target creature gets +2/+0 and gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
    """.trimIndent())
    val ventTheAirlock = Cards.invent("Vent the Airlock", "{1}{B}", "Instant", "Target creature gets -3/-0 until end of turn. Then if that creature's power is 0 or less, destroy it.")
    val questionableMorsel = Cards.invent("Questionable Morsel", "{2}{B}", "Artifact — Food", """
        Flash
        When this artifact enters, up to one target creature gets -3/-3 until end of turn.
        {2}, {T}, Sacrifice this artifact: You gain 3 life.
        {2}, {T}, Sacrifice this artifact: Target opponent loses 3 life.
    """.trimIndent())
    val welcomeTheAbyss = Cards.invent("Welcome the Abyss", "{B}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice an artifact or creature.
        Destroy target creature or Spacecraft.
    """.trimIndent())
    val fallenStarsDevotee = Cards.invent("Fallen Star's Devotee", "{2}{B}", "Creature — Human Wizard", "When this creature enters, destroy up to one other target creature. If that creature wasn't dealt damage this turn, its controller draws two cards.", "3/1")
    val gravedgeBruiser = Cards.invent("Gravedge Bruiser", "{3}{B}", "Creature — Human Soldier", "As long as you control an artifact, this creature gets +1/+0 and has deathtouch.", "3/4")
    val gravcrush = Cards.invent("Gravcrush", "{3}{B}", "Instant", "Exile target creature or Spacecraft.")
    val gravjetZealot = Cards.invent("Gravjet Zealot", "{2}{B}", "Creature — Human Scout", """
        Flying
        When this creature dies, create a tapped 2/2 colorless Robot artifact creature token.
    """.trimIndent(), "2/1")
    val hullgouger = Cards.invent("Hullgouger", "{B}", "Artifact Creature — Robot Assassin", "Deathtouch", "1/1")
    val lamplessPreacher = Cards.invent("Lampless Preacher", "{1}{B}", "Creature — Vampire Cleric", "Whenever you sacrifice another creature or artifact, put a +1/+1 counter on this creature.", "2/2")
    val zealotCircuitLeech = Cards.invent("Zealot Circuit-Leech", "{4}{B}{B}", "Artifact Creature — Nautilus", """
        Flying
        When this creature enters, until end of turn, target creature you control gets +X/+0 and target creature an opponent controls gets -0/-X, where X is the number of artifacts you control.
    """.trimIndent(), "4/4")
    val zealotPicket = Cards.invent("Zealot Picket", "{B}", "Artifact Creature — Robot", "Defender", "4/1")
    val dirgeObelisk = Cards.invent("Dirge Obelisk", "{2}{B}", "Artifact", "{T}: Until end of turn, target creature gains \"Whenever this creature is dealt damage, you draw that many cards and lose that much life.\" That creature's controller may have this artifact deal 1 damage to it. Activate only as a sorcery.")
    val nerezzaTheGreatHollow = Cards.invent("Nerezza, the Great Hollow", "{2}{B}{B}", "Legendary Enchantment", """
        Whenever a creature you control dies, each opponent chooses a creature they control and exiles it.
        At the beginning of your end step, if a player controls no creatures, sacrifice Nerezza, then put a creature card exiled with it onto the battlefield under your control with two additional +1/+1 counters on it.
    """.trimIndent())
    val duskWrecker = Cards.invent("Dusk Wrecker", "{1}{B}", "Creature — Human Rogue", """
        Menace
        Ward—Discard a card.
        Whenever this creature attacks, put a +1/+1 counter on target creature an opponent controls.
    """.trimIndent(), "4/1")
    val hiveThinner = Cards.invent("Hive Thinner", "{3}{B}", "Creature — Insect Warrior", """
        Flying
        Whenever this creature becomes tapped, you may sacrifice another creature or artifact. If you do, draw a card.
    """.trimIndent(), "2/4")
    val shadowcollarFanatic = Cards.invent("Shadowcollar Fanatic", "{1}{B}", "Creature — Human Cleric", "Sacrifice another creature or artifact: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)", "3/2")
    val plagueScarab = Cards.invent("Plague Scarab", "{1}{B}", "Artifact Creature — Insect", "When this creature enters, each opponent discards a card.", "1/1")
    val castOutByBallot = Cards.invent("Cast Out by Ballot", "{3}{B}", "Sorcery", """
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        Destroy target creature.
    """.trimIndent())
    val zhuRavelBonechorister = Cards.invent("Zhu-Ravel, Bonechorister", "{1}{B}{B}", "Legendary Creature — Human Wizard", "{T}: Return target creature card from your graveyard to the battlefield. It's a Skeleton in addition to its other types and has no abilities. Activate only as a sorcery.", "2/3")
    val nullPointRequiem = Cards.invent("Null Point Requiem", "{X}{B}", "Sorcery", "Destroy all creatures with toughness X or less. You lose X life. If X is 6 or more, return a creature card put into a graveyard this way to the battlefield under your control.")
    // chunk 12
    val barrageShot = Cards.invent("Barrage Shot", "{2}{R}", "Instant", "Barrage Shot deals 4 damage to target creature.")
    val severThrusters = Cards.invent("Sever Thrusters", "{2}{R}", "Instant", "Target creature deals damage to itself equal to its power. If that creature has flying, it deals twice that much damage to itself instead.")
    val ruinousOffensive = Cards.invent("Ruinous Offensive", "{X}{X}{R}", "Sorcery", "Create X tokens that are copies of target artifact or creature you control. Those tokens gain haste until end of turn. Sacrifice them at the beginning of the next end step.")
    val borePastTheCore = Cards.invent("Bore Past the Core", "{1}{R}", "Instant", """
        Choose one —
        • Put five charge counters on target Spacecraft or Planet you control.
        • Destroy target artifact.
    """.trimIndent())
    val vanguardBattleBrute = Cards.invent("Vanguard Battle-Brute", "{2}{R}", "Creature — Kavu Soldier", "At the beginning of your end step, if you control two or more tapped creatures, put a +1/+1 counter on this creature.", "2/3")
    val incursionTactics = Cards.invent("Incursion Tactics", "{1}{R}", "Instant", "Incursion Tactics deals 3 damage to target creature. It deals 5 damage instead if you control a Spacecraft.")
    val pyrrhosRaider = Cards.invent("Pyrrhos Raider", "{R}", "Artifact Creature — Robot Soldier", "Whenever this creature attacks, you may pay {2}. If you do, create a 2/2 colorless Robot artifact creature token that's tapped and attacking. Sacrifice that token at end of combat.", "2/1")
    val pyrrhosBoostdrone = Cards.invent("Pyrrhos Boostdrone", "{2}{R}", "Artifact Creature — Robot Scout", "{T}: Target creature you control gets +1/+1 and gains haste until end of turn. Activate only as a sorcery.", "2/3")
    val fusedMoxstone = Cards.invent("Fused Moxstone", "{1}{R}", "Artifact", """
        When this artifact enters, you may discard a card. If you do, draw two cards.
        {3}, Sacrifice this artifact: Create a tapped 2/2 colorless Robot artifact creature token.
    """.trimIndent())
    val remembranceStrongroom = Cards.invent("Remembrance Strongroom", "{3}{R}", "Artifact", "{T}, Sacrifice another artifact: Exile the top X cards of your library, where X is one plus the mana value of the sacrificed artifact. You may play those cards this turn.")
    val atomicTinkerer = Cards.invent("Atomic Tinkerer", "{2}{R}", "Creature — Kavu Artificer", "At the beginning of combat on your turn, target creature you control gets +1/+0 and gains first strike until end of turn.", "2/2")
    val stardustWyrm = Cards.invent("Stardust Wyrm", "{6}{R}", "Creature — Dragon", """
        Flying
        When this creature enters, it deals 3 damage to any target.
    """.trimIndent(), "4/4")
    val orehideArmadillo = Cards.invent("Orehide Armadillo", "{1}{R}", "Artifact Creature — Robot Pangolin", "Whenever another artifact you control enters, you may pay {1}. If you do, put a +1/+1 counter on this creature.", "2/2")
    val sufferingShared = Cards.invent("Suffering Shared", "{2}{R}", "Enchantment — Aura", """
        Enchant creature you control
        When this Aura enters, enchanted creature deals damage equal to its power to any other target.
        Whenever enchanted creature is dealt damage, it deals that much damage to each opponent.
    """.trimIndent())
    val residueElemental = Cards.invent("Residue Elemental", "{1}{R}", "Creature — Elemental", """
        Reach
        Landfall — Whenever a land you control enters, this creature gets +2/+0 until end of turn.
    """.trimIndent(), "0/4")
    val outfitForBattle = Cards.invent("Outfit for Battle", "{1}{R}", "Instant", "Target creature gets +3/+0 and gains reach and first strike until end of turn.")
    val calamitousStampede = Cards.invent("Calamitous Stampede", "{1}{R}{R}", "Sorcery", """
        Choose one —
        • Calamitous Stampede deals 3 damage to each opponent.
        • Exile all artifacts with mana value 3 or less.
    """.trimIndent())
    val corrosionReaper = Cards.invent("Corrosion Reaper", "{R}", "Artifact Creature — Robot", """
        Menace
        {2}, {T}, Exile an artifact card from your graveyard: Put a +1/+1 counter on this creature, then it deals damage equal to its power to any target.
    """.trimIndent(), "1/1")
    val cinderboreSalvager = Cards.invent("Cinderbore Salvager", "{R}", "Artifact Creature — Robot Scout", "{2}, {T}, Sacrifice another artifact or land: Draw a card.", "1/2")
    val controlHijack = Cards.invent("Control Hijack", "{2}{R}", "Sorcery", "Gain control of target artifact or creature until end of turn. Untap that permanent. It gains haste until end of turn. If it's a Spacecraft, put ten charge counters on it. If you do, remove ten charge counters from it at the beginning of the next end step.")
    val finalPlummet = Cards.invent("Final Plummet", "{4}{R}{R}", "Sorcery", "You may put an artifact or creature card from your hand onto the battlefield. That permanent gains haste, \"When this permanent leaves the battlefield, it deals damage equal to its mana value to each creature,\" and \"At the beginning of your end step, sacrifice this permanent.\"")
    val turfGuardingHornback = Cards.invent("Turf-Guarding Hornback", "{4}{R}{R}", "Creature — Beast", """
        Reach
        Landfall — Whenever a land you control enters, exile cards from the top of your library until you exile a nonland card. You may cast that card this turn.
    """.trimIndent(), "6/6")
    val strongroomSentinel = Cards.invent("Strongroom Sentinel", "{4}{R}", "Creature — Kavu Soldier", "At the beginning of your end step, if you control two or more tapped creatures, you may discard your hand. If you do, draw two cards.", "5/5")
    val armamentAssembly = Cards.invent("Armament Assembly", "{1}{R}", "Enchantment", "Whenever a nontoken artifact you control enters, create a colorless artifact token named Munitions with \"When this token leaves the battlefield, it deals 2 damage to any target.\"")
    val menagerieAutomaton = Cards.invent("Menagerie Automaton", "{1}{R}", "Artifact Creature — Robot", """
        {T}: Add {R}.
        {6}{R}: Target creature you control gets +4/+0 until end of turn. Activate only as a sorcery.
    """.trimIndent(), "1/3")
    // chunk 13
    val lifeforgeSurge = Cards.invent("Lifeforge Surge", "{1}{G}", "Instant", "Put a +1/+1 counter on target creature you control. It gains reach, trample, and indestructible until end of turn. Untap it. (Damage and effects that say \"destroy\" don't destroy it.)")
    val floweringBarbtail = Cards.invent("Flowering Barbtail", "{1}{G}", "Creature — Plant Scorpion", """
        Deathtouch
        When this creature enters, another target creature you control gains deathtouch until end of turn.
    """.trimIndent(), "2/2")
    val treatyNegotiations = Cards.invent("Treaty Negotiations", "{2}{G}", "Instant", "Target creature you control gets +1/+0 and gains vigilance until end of turn. It deals damage equal to its power to target creature an opponent controls.")
    val verdaniGroundgardener = Cards.invent("Verdani Groundgardener", "{1}{G}", "Creature — Insect Druid", "Landfall — Whenever a land you control enters, you gain 1 life.", "2/3")
    val starvingPlanetmaw = Cards.invent("Starving Planetmaw", "{5}{G}{G}{G}", "Creature — Leviathan", """
        Ward {3}
        Devour land 3 (As this creature enters, you may sacrifice any number of lands. It enters with three times that many +1/+1 counters on it.)
        When this creature enters, look at the top X cards of your library, where X is this creature's power. Put any number of land cards from among them onto the battlefield tapped, then shuffle.
    """.trimIndent(), "0/0")
    val rampagingHornbeast = Cards.invent("Rampaging Hornbeast", "{G}{G}", "Creature — Beast", """
        This spell can't be countered.
        Trample, haste
        Creature spells you control can't be countered.
        Combat damage can't be prevented.
    """.trimIndent(), "3/2")
    val sporecapBehemoth = Cards.invent("Sporecap Behemoth", "{6}{G}", "Creature — Fungus Beast", "This spell costs {X} less to cast, where X is the number of differently named lands you control.", "5/5")
    val genomePollenDrone = Cards.invent("Genome Pollen-Drone", "{G}", "Artifact Creature — Robot Insect", "{T}, Tap an untapped permanent you control: Add one mana of any color.", "1/2")
    val concordantGladeWalker = Cards.invent("Concordant Glade-Walker", "{3}{G}{G}", "Creature — Beast", """
        Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
        Concordant Glade-Walker's power and toughness are each equal to the number of lands you control.
    """.trimIndent(), "*/*")
    val bloodbondTick = Cards.invent("Bloodbond Tick", "{G}", "Creature — Mite", "Whenever this creature becomes tapped, another target creature you control gets +X/+X until end of turn, where X is this creature's power.", "1/1")
    val frostgrottoSmasher = Cards.invent("Frostgrotto Smasher", "{3}{G}", "Creature — Beast", """
        Trample
        Landfall — Whenever a land you control enters, this creature gets +1/+0 until end of turn.
    """.trimIndent(), "4/4")
    val frostplowScout = Cards.invent("Frostplow Scout", "{2}{G}{G}", "Creature — Insect Scout", """
        You may play an additional land on each of your turns.
        You may play lands from your graveyard.
        Landfall — Whenever a land you control enters, mill a card.
    """.trimIndent(), "2/4")
    val pluckyGreenhorn = Cards.invent("Plucky Greenhorn", "{1}{G}", "Creature — Insect Citizen", "{3}: Put a +1/+1 counter on this creature. Activate only as a sorcery.", "2/2")
    val whiptailStalker = Cards.invent("Whiptail Stalker", "{4}{G}{G}", "Creature — Plant Beast", """
        This spell costs {2} less to cast if your opponents control three or more creatures.
        Reach
    """.trimIndent(), "5/7")
    val thawwalkerMourner = Cards.invent("Thawwalker Mourner", "{2}{G}", "Creature — Insect Soldier", "Whenever a creature you control with a +1/+1 counter on it dies, draw a card.", "3/3")
    val thawwalkersKit = Cards.invent("Thawwalker's Kit", "{G}", "Artifact — Equipment", """
        When this Equipment enters, attach it to target creature you control.
        Equipped creature gets +2/+1 and has reach.
        Equip {5} ({5}: Attach to target creature you control. Equip only as a sorcery.)
    """.trimIndent())
    val thawwalkersGrit = Cards.invent("Thawwalker's Grit", "{G}", "Enchantment — Aura", """
        Enchant creature you control
        When this Aura enters, enchanted creature fights up to one target creature an opponent controls. (Each deals damage equal to its power to the other.)
        Enchanted creature gets +0/+2 and can't be blocked by more than one creature.
    """.trimIndent())
    val coilselfDevourer = Cards.invent("Coilself Devourer", "{2}{G}{G}", "Creature — Plant Wurm", "At the beginning of combat on your turn, put X +1/+1 counters on each creature you control, where X is this creature's power.", "1/3")
    val drawBackThroughTheLoom = Cards.invent("Draw Back Through the Loom", "{3}{G}{G}", "Sorcery", "Return up to two target nonland permanent cards from your graveyard to your hand, then return up to two target land cards from your graveyard to the battlefield tapped.")
    val brokenPinions = Cards.invent("Broken Pinions", "{2}{G}", "Sorcery", "Destroy target artifact, enchantment, or creature with flying. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)")
    val cloudbarb = Cards.invent("Cloudbarb", "{2}{G}", "Creature — Insect Warrior", """
        Reach
        Whenever this creature blocks a creature with flying, this creature gets +5/+0 until end of turn.
    """.trimIndent(), "3/3")
    val loomworkSentinel = Cards.invent("Loomwork Sentinel", "{3}{G}", "Artifact Creature — Robot Soldier", """
        Vigilance
        Each creature you control with toughness greater than its power assigns combat damage equal to its toughness rather than its power.
        Each creature you control with toughness greater than its power stations permanents using its toughness rather than its power.
    """.trimIndent(), "3/4")
    val groundbonding = Cards.invent("Groundbonding", "{2}{G}", "Enchantment", "Whenever you put one or more +1/+1 counters on a creature you control, you may draw that many cards. Do this only once each turn.")
    val meltcaller = Cards.invent("Meltcaller", "{2}{G}", "Creature — Insect Scout", "When this creature enters or dies, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)", "4/2")
    // chunk 14
    val velunirSlumberingNovice = Cards.invent("Velunir, Slumbering Novice", "{1}{U}{B}", "Legendary Creature — Human Cleric", """
        When Velunir enters, draw two cards. Then discard two cards unless you discard an artifact card.
        During your turn, Velunir has deathtouch.
    """.trimIndent(), "2/3")
    val starwastegoyf = Cards.invent("Starwastegoyf", "{B}{G}", "Creature — Elemental Lhurgoyf", "Starwastegoyf's power is equal to the number of cards you own in exile and its toughness is equal to that number plus 1.", "*/1+*")
    val twinnoraFusionComposite = Cards.invent("Twinnora, Fusion Composite", "{X}{G}{W}", "Legendary Artifact Creature — Construct", """
        Trample
        Twinnora enters with a number of +1/+1 counters on it equal to the amount of mana spent to cast it.
        Whenever you attack, you may remove a +1/+1 counter from each of two creatures you control. If you do, draw a card and create a 2/2 colorless Robot artifact creature token.
    """.trimIndent(), "0/1")
    val geneshiftChrysalid = Cards.invent("Geneshift Chrysalid", "{G}{U}", "Creature — Insect Druid", """
        Flying
        Landfall — Whenever a land you control enters, target creature has base power and toughness 3/3 until end of turn. If you control six or more lands, that creature has base power and toughness 6/6 until end of turn instead.
    """.trimIndent(), "1/3")
    val orlenaRisingRecruit = Cards.invent("Orlena, Rising Recruit", "{2}{G}{W}{W}", "Legendary Creature — Human Soldier", """
        Whenever Orlena enters or attacks, put a +1/+1 counter on target creature you control.
        Whenever one or more creatures you control with +1/+1 counters on them deal combat damage to a player, draw a card.
    """.trimIndent(), "3/3")
    val quellanMaeltharExile = Cards.invent("Qu'ellan, Maelthar Exile", "{1}{U}{R}", "Legendary Creature — Jellyfish Advisor", """
        Flying
        Whenever an artifact you control enters, put a +1/+1 counter on target creature.
    """.trimIndent(), "1/3")
    val rebelliousSlaughter = Cards.invent("Rebellious Slaughter", "{3}{B}{B}{R}{R}", "Sorcery", "Choose odd or even. Destroy each creature with mana value of the chosen quality. Then gain control of all creatures until end of turn. Untap them. They gain haste until end of turn. (Zero is even.)")
    val brisquelNimbleGalleychef = Cards.invent("Brisquel, Nimble Galleychef", "{R}{W}", "Legendary Creature — Lobster Citizen", """
        Artifacts you control are Foods in addition to their other types and have "{2}, {T}, Sacrifice this artifact: You gain 3 life."
        {1}, {T}, Sacrifice a Food: Brisquel deals 3 damage to each opponent.
        At the beginning of each end step, if you gained life this turn, untap Brisquel.
    """.trimIndent(), "2/2")
    val orloVesselsMechanic = Cards.invent("Orlo, Vessel's Mechanic", "{2}{R}{W}", "Legendary Creature — Human Artificer", "At the beginning of your end step, if you control two or more tapped creatures, create a tapped 2/2 colorless Robot artifact creature token.", "2/4")
    val orloFreewheelingSkipper = Cards.invent("Orlo, Freewheeling Skipper", "{4}{R}{W}", "Legendary Creature — Human Artificer Rogue", """
        Double strike, vigilance
        Spells you cast have affinity for artifacts. (They cost {1} less to cast for each artifact you control.)
    """.trimIndent(), "4/4")
    val sowingArkNursemaid = Cards.invent("Sowing-Ark Nursemaid", "{B}{G}", "Creature — Insect Citizen", """
        When this creature enters, mill three cards. (Put the top three cards of your library into your graveyard.)
        {3}{B}{G}, Sacrifice this creature: Return target creature or Spacecraft card from your graveyard to the battlefield. Activate only as a sorcery.
    """.trimIndent(), "2/3")
    val collapseOfTheHorizon = Cards.invent("Collapse of the Horizon", "{3}{U}{B}{B}", "Sorcery", "Destroy all creatures, then any number of target players each mill half their library, rounded down.")
    val chronoSpatialRift = Cards.invent("Chrono-Spatial Rift", "{2}{W}{U}", "Sorcery", "Target player mills cards equal to your life total.")
    val outpostOverseer = Cards.invent("Outpost Overseer", "{W}{U}", "Creature — Lizard Artificer", "Whenever you cast your second spell each turn, create a 1/1 colorless Drone artifact creature token with flying and \"This token can block only creatures with flying.\"", "2/2")
    val sirHarvellDaystarParagon = Cards.invent("Sir Harvell, Daystar Paragon", "{W}{B}", "Legendary Creature — Human Knight", """
        Vigilance, menace
        Whenever another creature you control dies or is put into exile, put a +1/+1 counter on Sir Harvell and you gain 1 life.
        When Sir Harvell dies or is put into exile while its power is 4 or greater, destroy up to one target nonland permanent.
    """.trimIndent(), "2/2")
    val sirHarvellTheLuminous = Cards.invent("Sir Harvell, the Luminous", "{2}{W}{B}{B}", "Legendary Creature — Human Knight", """
        Deathtouch, lifelink
        Whenever Sir Harvell enters or attacks, other creatures you control get +1/+0 and gain deathtouch until end of turn.
    """.trimIndent(), "4/4")
    val brakkosRemembranceStandard = Cards.invent("Brakkos, Remembrance Standard", "{1}{R}{G}", "Legendary Creature — Kavu Pilot", "Landfall — Whenever a land you control enters, Brakkos deals 1 damage to each opponent. If this is the second time this ability has resolved this turn, draw a card.", "2/4")
    // chunk 15
    val everyPathCodex = Cards.invent("Every-Path Codex", "{3}", "Artifact", """
        {T}: Add one mana of any color.
        {7}, {T}, Sacrifice this artifact: Draw X cards, where X is the number of differently named lands you control.
    """.trimIndent())
    val spawningLagoon = Cards.invent("Spawning Lagoon", null, "Land — Forest Island", """
        ({T}: Add {G} or {U}.)
        As this land enters, you may pay 2 life. If you don't, it enters tapped.
    """.trimIndent())
    val silversteelPal = Cards.invent("Silversteel Pal", "{2}", "Artifact Creature — Dog", """
        Whenever this creature becomes tapped, you gain 1 life.
        {2}, {T}: Put target card from a graveyard on the bottom of its owner's library.
    """.trimIndent(), "2/1")
    val helmDeck = Cards.invent("Helm Deck", null, "Land", """
        This land enters tapped.
        When this land enters, sacrifice it unless you tap an untapped permanent you control.
        {T}: Add one mana of any color.
    """.trimIndent())
    val faithlessChapel = Cards.invent("Faithless Chapel", null, "Land — Plains Swamp", """
        ({T}: Add {W} or {B}.)
        As this land enters, you may pay 2 life. If you don't, it enters tapped.
    """.trimIndent())
    val rationBrick = Cards.invent("Ration Brick", "{1}", "Artifact — Food", """
        Indestructible (Effects that say "destroy" don't destroy this artifact.)
        {2}, {T}, Sacrifice this artifact: You gain 3 life.
        When this artifact is put into a graveyard from the battlefield, draw a card.
    """.trimIndent())
    val hallowedSmeltery = Cards.invent("Hallowed Smeltery", null, "Land — Mountain Plains", """
        ({T}: Add {R} or {W}.)
        As this land enters, you may pay 2 life. If you don't, it enters tapped.
    """.trimIndent())
    val remoteStarsmithy = Cards.invent("Remote Starsmithy", null, "Land", """
        {T}: Add {C}.
        {2}, {T}, Tap X untapped artifacts you control: Target creature gets +X/+0 until end of turn. Activate only as a sorcery.
        {5}, {T}: Create a 2/2 colorless Robot artifact creature token.
    """.trimIndent())
    val tramplingHollow = Cards.invent("Trampling Hollow", null, "Land — Mountain Forest", """
        ({T}: Add {R} or {G}.)
        As this land enters, you may pay 2 life. If you don't, it enters tapped.
    """.trimIndent())
    val scoutingAutomaton = Cards.invent("Scouting Automaton", "{4}", "Artifact Creature — Robot", """
        Flying
        Hexproof (This creature can't be the target of spells or abilities your opponents control.)
        {10}, Sacrifice this creature: It deals 3 damage to any target. Target player draws three cards and gains 3 life. This ability costs {X} less to activate, where X is the number of differently named lands you control.
    """.trimIndent(), "1/3")
    val vexmorRuthlessCommander = Cards.invent("Vexmor, Ruthless Commander", "{3}", "Legendary Planeswalker — Tezzeret", """
        Whenever an artifact you control enters, put a loyalty counter on Vexmor.
        [0]: Untap target artifact or creature. If it's an artifact creature, put a +1/+1 counter on it.
        [−3]: Search your library for an artifact card with mana value 1 or less, reveal it, put it into your hand, then shuffle.
        [−7]: You get an emblem with "At the beginning of combat on your turn, put three +1/+1 counters on target artifact you control. If it's not a creature, it becomes a 0/0 Robot artifact creature."
    """.trimIndent(), null, loyalty = "4")
    val wondersteelMissile = Cards.invent("Wondersteel Missile", "{1}", "Artifact", "{6}, {T}, Sacrifice this artifact: Destroy target nonland permanent. This ability costs {3} less to activate if you attacked with a Spacecraft this turn.")
    val theSovereignBangle = Cards.invent("The Sovereign Bangle", "{2}", "Legendary Artifact — Equipment", """
        Equipped creature gets +1/+1 and has "{15}, Exile The Sovereign Bangle: You control target opponent during their next turn. This ability costs {X} less to activate, where X is this creature's power. Activate only as a sorcery." (You see all cards that player could see and make all decisions for them.)
        Equip {1}
    """.trimIndent())
    val theFinalityStone = Cards.invent("The Finality Stone", "{7}", "Legendary Artifact", """
        Whenever you play a land or cast a spell, draw a card.
        At the beginning of your end step, your life total becomes half your starting life total, rounded up.
    """.trimIndent())
    val hummingBroodvat = Cards.invent("Humming Broodvat", "{6}", "Artifact", """
        Affinity for Slivers (This spell costs {1} less to cast for each Sliver you control.)
        Slivers you control have double strike and haste.
        At the beginning of your upkeep, create two 1/1 colorless Sliver creature tokens.
    """.trimIndent())
    val toxicHushBot = Cards.invent("Toxic Hush-Bot", "{3}", "Artifact Creature — Robot Assassin", "Whenever a nontoken artifact creature you control deals combat damage to a player, that player gets two poison counters. (A player with ten or more poison counters loses the game.)", "2/3")
    val sunkenOssuary = Cards.invent("Sunken Ossuary", null, "Land — Island Swamp", """
        ({T}: Add {U} or {B}.)
        As this land enters, you may pay 2 life. If you don't, it enters tapped.
    """.trimIndent())
}
