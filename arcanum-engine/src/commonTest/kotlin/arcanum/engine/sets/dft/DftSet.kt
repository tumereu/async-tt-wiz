package arcanum.engine.sets.dft

import arcanum.engine.cards.Cards

/**
 * The invented Aetherdrift (dft) cards of the parser tests (`arcanum.parser.sets.dft`), the same names and texts, for the scenarios: each
 * property registers its card and holds its name.
 */
internal object DftSet {
    // chunk 01
    val carrionHauler = Cards.invent("Carrion Hauler", "{2}{B}", "Artifact — Vehicle", """
        When this Vehicle enters, mill two cards. Then return a creature or Vehicle card from your graveyard to your hand. (To mill two cards, put the top two cards of your library into your graveyard.)
        Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "3/2")
    val cryptcallChariot = Cards.invent("Cryptcall Chariot", "{3}{B}", "Artifact — Vehicle", """
        Menace
        Whenever you discard one or more cards, create that many tapped 2/2 black Zombie creature tokens.
        Crew 2
    """.trimIndent(), "5/5")
    val holdingChariot = Cards.invent("Holding Chariot", "{4}{W}{W}", "Artifact — Vehicle", """
        When this Vehicle enters, exile target artifact or creature an opponent controls until this Vehicle leaves the battlefield.
        Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
        Cycling {W} ({W}, Discard this card: Draw a card.)
    """.trimIndent(), "6/6")
    val hullskimmer = Cards.invent("Hullskimmer", "{3}{U}{U}", "Artifact — Vehicle", """
        Flying
        When this Vehicle enters, draw two cards.
        Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "3/2")
    val infernalJunker = Cards.invent("Infernal Junker", "{6}{B}", "Artifact — Vehicle", """
        Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
        When this Vehicle enters, for each player, destroy up to one target creature that player controls. If a creature you controlled was destroyed this way, put two +1/+1 counters on this Vehicle.
        Crew 2
    """.trimIndent(), "4/3")
    val moonlitMangler = Cards.invent("Moonlit Mangler", "{1}{U}", "Artifact — Vehicle", """
        During turns other than yours, this Vehicle is an artifact creature.
        Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "3/3")
    val rescueEngine = Cards.invent("Rescue Engine", "{4}{W}", "Artifact — Vehicle", """
        Other artifact creatures you control get +2/+2.
        Whenever this Vehicle attacks, return up to one target artifact card from your graveyard to the battlefield.
        Crew 6
    """.trimIndent(), "6/10")
    val ripclawRustler = Cards.invent("Ripclaw Rustler", "{3}{B}", "Artifact — Vehicle", """
        When this Vehicle enters, each opponent discards a card.
        Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "4/3")
    val seerSkyChariot = Cards.invent("Seer-Sky Chariot", "{1}{W}", "Artifact — Vehicle", """
        Flying
        As this Vehicle enters, choose a nonland card name.
        Activated abilities of sources with the chosen name cost {2} more to activate.
        Crew 2
    """.trimIndent(), "3/3")
    val seizureEngine = Cards.invent("Seizure Engine", "{3}{U}{U}", "Artifact — Vehicle", """
        When this Vehicle enters, gain control of target creature an opponent controls for as long as you control this Vehicle. That creature can't attack or block for as long as you control this Vehicle.
        Crew 3
    """.trimIndent(), "5/5")
    val sidewalkRambler = Cards.invent("Sidewalk Rambler", "{4}{W}", "Artifact — Vehicle", """
        When this Vehicle enters, create a 1/1 colorless Thopter artifact creature token with flying.
        Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "5/4")
    val skywardPatrolUnit = Cards.invent("Skyward Patrol Unit", "{2}{W}", "Artifact — Vehicle", """
        Flying, vigilance
        Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "3/3")
    val spotlightScouter = Cards.invent("Spotlight Scouter", "{1}{W}", "Artifact — Vehicle", """
        When this Vehicle enters, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)
        Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "3/2")
    val theFinalLap = Cards.invent("The Final Lap", "{B}", "Legendary Artifact — Vehicle", """
        The Final Lap gets -X/-X, where X is your life total.
        {2}{B}, Pay 2 life: Draw a card.
        Crew 2
    """.trimIndent(), "13/13")
    val thopterAssembler = Cards.invent("Thopter Assembler", "{2}{U}", "Artifact — Vehicle", """
        Flying
        Whenever you draw your second card each turn, create a 1/1 colorless Thopter artifact creature token with flying.
        Crew 2
    """.trimIndent(), "4/4")
    val voyagerSkimcar = Cards.invent("Voyager Skimcar", "{W}", "Artifact — Vehicle", """
        When this Vehicle enters, scry 1.
        Tap three other untapped creatures you control: Until end of turn, this Vehicle becomes an artifact creature and gains flying. Put a +1/+1 counter on it.
        Crew 1
    """.trimIndent(), "2/3")
    // chunk 02
    val apocalypseRider = Cards.invent("Apocalypse Rider", "{2}{B}{R}", "Artifact — Vehicle", """
        {T}: Target creature you control with power 2 or less gains lifelink until end of turn and can't be blocked this turn.
        Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "6/5")
    val boomcruiser = Cards.invent("Boomcruiser", "{2}{R}{R}", "Artifact — Vehicle", """
        When this Vehicle enters, add four mana of any one color. Spend this mana only to activate abilities.
        Exhaust — {X}{2}{R}: This Vehicle deals X damage to any target. Put a +1/+1 counter on this Vehicle. (Activate each exhaust ability only once.)
        Crew 2
    """.trimIndent(), "5/5")
    val boostedSchooner = Cards.invent("Boosted Schooner", "{1}{U}{R}", "Artifact — Vehicle", """
        Menace
        Whenever you attack, draw a card, then discard a card.
        Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "3/3")
    val burnerMissile = Cards.invent("Burner Missile", "{1}{R}", "Artifact — Vehicle", """
        Flash
        When this Vehicle enters, target creature you control gets +2/+0 and gains trample until end of turn.
        Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "3/1")
    val clamorousIronhull = Cards.invent("Clamorous Ironhull", "{3}{R}", "Artifact — Vehicle", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
        Cycling {R} ({R}, Discard this card: Draw a card.)
    """.trimIndent(), "6/3")
    val cloudtrackSkycycle = Cards.invent("Cloudtrack Skycycle", "{2}{R}{W}", "Artifact — Vehicle", """
        Flying
        When this Vehicle enters, distribute two +1/+1 counters among one or two other target Vehicles and/or creatures you control.
        Crew 1
    """.trimIndent(), "2/3")
    val debrisScarab = Cards.invent("Debris Scarab", "{2}{B}{G}", "Artifact — Vehicle", """
        Trample
        When this Vehicle enters, each opponent loses 3 life and you gain 3 life.
        Crew 2
    """.trimIndent(), "6/6")
    val duneWanderer = Cards.invent("Dune Wanderer", "{X}{W}{B}", "Artifact — Vehicle", """
        When this Vehicle enters, return target artifact or creature card with mana value X or less from your graveyard to the battlefield.
        Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "3/3")
    val gastalThrillrider = Cards.invent("Gastal Thrillrider", "{2}{R}", "Artifact — Vehicle", """
        Trample, haste
        When this Vehicle enters, it becomes an artifact creature until end of turn.
        Crew 2
        {2}{R}, Discard a card: Return this card from your graveyard to the battlefield with a finality counter on it. Activate only as a sorcery.
    """.trimIndent(), "4/2")
    val grumblerumbler = Cards.invent("Grumblerumbler", "{4}{G}", "Artifact — Vehicle", """
        Vigilance, trample
        Exile an artifact or creature card from your graveyard: This Vehicle becomes an artifact creature until end of turn.
        Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "7/6")
    val hauntedHotrod = Cards.invent("Haunted Hotrod", "{1}{U}{B}", "Artifact — Vehicle", """
        Whenever you attack, target creature you control gets +1/+0 and gains deathtouch until end of turn. Untap it.
        Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "3/3")
    val lifeforgeEngine = Cards.invent("Lifeforge Engine", "{3}", "Artifact — Vehicle", """
        As this Vehicle enters, choose a creature type.
        Vehicle creatures you control are the chosen creature type in addition to their other types.
        Each creature you control of the chosen type other than this Vehicle gets +1/+1.
        Crew 3
    """.trimIndent(), "4/4")
    val marshalsPathrunner = Cards.invent("Marshals' Pathrunner", "{3}", "Artifact — Vehicle", """
        When this Vehicle enters, search your library for a basic land card, reveal it, put it into your hand, then shuffle.
        Exhaust — {W}{U}{B}{R}{G}: This Vehicle becomes an artifact creature. Put two +1/+1 counters on it. (Activate each exhaust ability only once.)
        Crew 5
    """.trimIndent(), "6/5")
    val pathlightPathmaker = Cards.invent("Pathlight Pathmaker", "{4}{W}{U}", "Artifact — Vehicle", """
        Vigilance
        When this Vehicle enters, you may search your library for an artifact card and reveal it. Put it onto the battlefield if its mana value is 2 or less. Otherwise, put it into your hand. If you search your library this way, shuffle.
        Crew 2
    """.trimIndent(), "6/5")
    val rangersAetherhub = Cards.invent("Rangers' Aetherhub", "{1}{G}{U}", "Artifact — Vehicle", """
        Vigilance
        Whenever you activate an exhaust ability, create a 1/1 colorless Thopter artifact creature token with flying.
        Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "3/5")
    val rangersRefitter = Cards.invent("Rangers' Refitter", "{1}{U}", "Artifact — Vehicle", """
        Whenever you activate an exhaust ability, draw a card.
        Exhaust — {4}: This Vehicle becomes an artifact creature. Put a +1/+1 counter on it. (Activate each exhaust ability only once.)
        Crew 2
    """.trimIndent(), "3/3")
    val rocketeerBoostcart = Cards.invent("Rocketeer Boostcart", "{R}{G}", "Artifact — Vehicle", """
        Whenever this Vehicle attacks, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        Exhaust — {3}: This Vehicle becomes an artifact creature. Put a +1/+1 counter on it. (Activate each exhaust ability only once.)
        Crew 1
    """.trimIndent(), "3/2")
    val roverEdges = Cards.invent("Rover Edges", "{3}", "Artifact — Equipment Vehicle", """
        Double strike
        Equipped creature has double strike.
        Equip {4}
        Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn. Creatures can't be attached to other permanents.)
    """.trimIndent(), "2/2")
    val skyboxBarge = Cards.invent("Skybox Barge", "{5}", "Artifact — Vehicle", """
        Flying
        Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent(), "4/4")
    val spireSpeedcycle = Cards.invent("Spire Speedcycle", "{4}{R}", "Artifact — Vehicle", """
        Haste
        Exhaust — Tap another untapped Mount or Vehicle you control: This Vehicle becomes an artifact creature. Put a +1/+1 counter on it for each Mount and/or Vehicle you control other than this Vehicle. (Activate each exhaust ability only once.)
        Crew 2
    """.trimIndent(), "5/4")
    val thunderingBroodcart = Cards.invent("Thundering Broodcart", "{2}{B}{B}{G}{G}", "Artifact — Vehicle", """
        Reach, menace
        When this Vehicle enters, destroy target nonland permanent an opponent controls with mana value 4 or less.
        Crew 3
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent(), "6/5")
    val thunderousVelocycle = Cards.invent("Thunderous Velocycle", "{1}{G}{G}", "Artifact — Vehicle", """
        Trample
        Each other Vehicle and creature you control enters with an additional +1/+1 counter on it if its mana value is 4 or less. Otherwise, it enters with three additional +1/+1 counters on it.
        Crew 3
    """.trimIndent(), "5/5")
    val trundlingWorldwagon = Cards.invent("Trundling Worldwagon", "{2}{G}", "Artifact — Vehicle", """
        This Vehicle's power is equal to the number of lands you control.
        Whenever this Vehicle enters or attacks, you may search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        Crew 4
    """.trimIndent(), "*/4")
    val veloheartCycle = Cards.invent("Veloheart Cycle", "{2}{G}", "Artifact — Vehicle", """
        When this Vehicle enters, you gain 2 life.
        {T}: Add one mana of any color.
        Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "4/2")
    // chunk 03
    val alacrianOutfitters = Cards.invent("Alacrian Outfitters", "{3}{W}", "Artifact", """
        Creatures you control get +0/+1 and have vigilance.
        At the beginning of combat on your turn, choose up to one target Mount or Vehicle you control. Until end of turn, that permanent becomes saddled if it's a Mount and becomes an artifact creature if it's a Vehicle.
    """.trimIndent())
    val chandraSparkRacer = Cards.invent("Chandra, Spark Racer", "{3}{R}", "Legendary Planeswalker — Chandra", """
        At the beginning of combat on your turn, choose up to one target Vehicle you control. Until end of turn, it becomes an artifact creature and gains haste.
        [+2]: You may sacrifice an artifact or discard a card. If you do, draw a card.
        [0]: Create a 3/2 colorless Vehicle artifact token with crew 1.
        [−7]: You get an emblem with "Whenever an artifact you control enters, this emblem deals 3 damage to any target."
    """.trimIndent(), null, loyalty = "4")
    val fearlessCorsair = Cards.invent("Fearless Corsair", "{1}{U}{R}", "Creature — Fish Pirate", """
        Haste
        Vehicles you control have haste.
        Whenever you attack, if a Pirate and a Vehicle attacked this combat, draw three cards, then discard two cards.
    """.trimIndent(), "3/3")
    val koladinTriumphRacer = Cards.invent("Koladin, Triumph Racer", "{R}{W}", "Legendary Creature — Human Pilot", """
        Mounts and Vehicles you control have haste.
        Whenever a Mount you control enters, it becomes saddled until end of turn.
        Whenever a Vehicle you control enters, it becomes an artifact creature until end of turn.
    """.trimIndent(), "2/3")
    val muLanyingGaleRacer = Cards.invent("Mu Lanying, Gale Racer", "{2}{U}{U}", "Legendary Creature — Human Wizard Pilot", """
        When Mu Lanying enters, create a 3/2 colorless Vehicle artifact token with crew 1.
        Vehicles you control have flying.
        Whenever one or more creatures you control with flying deal combat damage to a player, draw a card.
    """.trimIndent(), "2/4")
    val pathlightMatrix = Cards.invent("Pathlight Matrix", "{2}", "Artifact", """
        When this artifact enters, draw a card.
        {2}, {T}: Target Mount you control becomes saddled until end of turn. Activate only as a sorcery.
        {2}, {T}: Target Vehicle you control becomes an artifact creature until end of turn.
    """.trimIndent())
    val pushTheEnvelope = Cards.invent("Push the Envelope", "{5}{R}{R}", "Sorcery", """
        Return all Mount and Vehicle cards from your graveyard to the battlefield. Sacrifice them at the beginning of the next end step.
        Vehicles you control become artifact creatures until end of turn. Creatures you control gain haste until end of turn.
    """.trimIndent())
    val retune = Cards.invent("Retune", "{3}{W}", "Sorcery", "Return target artifact card from your graveyard to the battlefield. If it's a Vehicle, it becomes an artifact creature.")
    // chunk 04
    val alacrianPanther = Cards.invent("Alacrian Panther", "{4}{G}", "Creature — Cat Mount", """
        Vigilance
        Whenever this creature attacks while saddled, it gets +2/+2 until end of turn.
        Saddle 1 (Tap any number of other creatures you control with total power 1 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
    """.trimIndent(), "4/4")
    val autarchMastodon = Cards.invent("Autarch Mastodon", "{4}{G}{G}", "Creature — Elephant Mount", """
        When this creature enters and whenever it attacks while saddled, create a 3/3 green Elephant creature token.
        Saddle 5 (Tap any number of other creatures you control with total power 5 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
    """.trimIndent(), "5/5")
    val districtMascotPup = Cards.invent("District Mascot Pup", "{G}", "Creature — Dog Mount", """
        This creature enters with a +1/+1 counter on it.
        {1}{G}, Remove two +1/+1 counters from this creature: Destroy target artifact.
        Whenever this creature attacks while saddled, put a +1/+1 counter on it.
        Saddle 1
    """.trimIndent(), "0/0")
    val dracosaurOutrider = Cards.invent("Dracosaur Outrider", "{4}{R}{R}", "Creature — Dinosaur Dragon Mount", """
        Flying, haste
        Whenever this creature attacks while saddled, it deals 2 damage to any target.
        Saddle 3 (Tap any number of other creatures you control with total power 3 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
    """.trimIndent(), "4/4")
    val dunelightGlider = Cards.invent("Dunelight Glider", "{W}", "Creature — Possum Mount", """
        Vigilance
        Whenever this creature attacks while saddled, it gets +1/+2 and gains flying until end of turn.
        Saddle 3 (Tap any number of other creatures you control with total power 3 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
    """.trimIndent(), "1/1")
    val dunelightStallion = Cards.invent("Dunelight Stallion", "{3}{W}", "Creature — Horse Mount", """
        Whenever this creature attacks while saddled, untap it and put a +1/+1 counter on it.
        Saddle 1 (Tap any number of other creatures you control with total power 1 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
    """.trimIndent(), "3/3")
    val gildedGhodra = Cards.invent("Gilded Ghodra", "{1}{R}", "Creature — Horse Mount", """
        Whenever this creature attacks while saddled, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        Saddle 1 (Tap any number of other creatures you control with total power 1 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
    """.trimIndent(), "2/2")
    val glimmerheathOcelot = Cards.invent("Glimmerheath Ocelot", "{1}{W}", "Creature — Cat Mount", """
        Lifelink
        Whenever this creature attacks while saddled, search your library for a basic Plains card, reveal it, put it into your hand, then shuffle.
        Saddle 2 (Tap any number of other creatures you control with total power 2 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
    """.trimIndent(), "2/3")
    val lagorenSoulOfAlacria = Cards.invent("Lagoren, Soul of Alacria", "{G}{W}", "Legendary Creature — Beast Mount", """
        Flying
        Whenever Lagoren attacks while saddled, put a +1/+1 counter on each of up to two target Mounts and/or Vehicles.
        Saddle 1 (Tap any number of other creatures you control with total power 1 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
    """.trimIndent(), "1/1")
    val rampartYak = Cards.invent("Rampart Yak", "{1}{W}", "Creature — Ox Mount", """
        Whenever this creature attacks while saddled, put a +1/+1 counter on target creature.
        Sacrifice this creature: Creatures you control with counters on them gain hexproof and indestructible until end of turn.
        Saddle 1 (Tap any number of other creatures you control with total power 1 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
    """.trimIndent(), "2/2")
    val sentinelDawnmare = Cards.invent("Sentinel Dawnmare", "{3}{W}{W}", "Creature — Horse Mount", """
        Ward {2}
        Whenever this creature attacks while saddled, search your library for a nonland permanent card with mana value 3 or less, put it onto the battlefield, then shuffle.
        Saddle 4
    """.trimIndent(), "5/5")
    val unhurriedSloth = Cards.invent("Unhurried Sloth", "{3}{W}{W}", "Creature — Sloth Mount", """
        Whenever this creature attacks while saddled, it gains indestructible until end of turn. Untap all creatures you control.
        Saddle 4 (Tap any number of other creatures you control with total power 4 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
    """.trimIndent(), "5/5")
    val venomsacLizard = Cards.invent("Venomsac Lizard", "{1}{G}", "Creature — Lizard Mount", """
        Deathtouch
        Whenever this creature attacks while saddled, it gets +0/+3 until end of turn.
        Saddle 2 (Tap any number of other creatures you control with total power 2 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
    """.trimIndent(), "2/1")
    // chunk 05
    val backInTheRace = Cards.invent("Back in the Race", "{4}{B}", "Sorcery", """Return target creature or Vehicle card from your graveyard to the battlefield. Create a 1/1 colorless Pilot creature token with "This token saddles Mounts and crews Vehicles as though its power were 2 greater."""")
    val cloudtrackCommander = Cards.invent("Cloudtrack Commander", "{2}{W}", "Creature — Human Pilot", """
        Mounts and Vehicles you control get +1/+1.
        This creature saddles Mounts and crews Vehicles as though its power were 2 greater.
    """.trimIndent(), "2/3")
    val cloudtrackCoordinator = Cards.invent("Cloudtrack Coordinator", "{R}{W}", "Creature — Human Pilot", """
        When this creature enters, scry 2.
        {T}: Create X 1/1 colorless Pilot creature tokens, where X is the number of Mounts and/or Vehicles that entered the battlefield under your control this turn. The tokens have "This token saddles Mounts and crews Vehicles as though its power were 2 greater."
    """.trimIndent(), "3/1")
    val collisionVerdict = Cards.invent("Collision Verdict", "{1}{W}", "Sorcery", """
        Choose one —
        • Collision Verdict deals X damage to target creature, where X is the number of permanents you control that are creatures and/or Vehicles.
        • Destroy target artifact.
    """.trimIndent())
    val consoleAce = Cards.invent("Console Ace", "{1}{W}", "Artifact Creature — Robot Pilot", """
        This creature saddles Mounts and crews Vehicles using its toughness rather than its power.
        Whenever this creature becomes tapped during your turn, untap it. This ability triggers only once each turn.
    """.trimIndent(), "0/4")
    val countryLanes = Cards.invent("Country Lanes", null, "Land", """
        This land enters tapped unless you control a Mount or Vehicle.
        {T}: Add {W}.
        {1}{W}, {T}, Sacrifice this land: Create a 1/1 colorless Pilot creature token with "This token saddles Mounts and crews Vehicles as though its power were 2 greater." Activate only as a sorcery.
    """.trimIndent())
    val defendTheRacer = Cards.invent("Defend the Racer", "{G}", "Instant", """
        Choose one —
        • Target permanent you control gains hexproof and indestructible until end of turn.
        • Create a 1/1 colorless Pilot creature token with "This token saddles Mounts and crews Vehicles as though its power were 2 greater."
    """.trimIndent())
    val dynamitePlunger = Cards.invent("Dynamite Plunger", "{R}", "Creature — Goblin Pilot", """
        This creature saddles Mounts and crews Vehicles as though its power were 2 greater.
        When this creature dies, it deals 1 damage to any target.
    """.trimIndent(), "1/1")
    val foulLanes = Cards.invent("Foul Lanes", null, "Land", """
        This land enters tapped unless you control a Mount or Vehicle.
        {T}: Add {B}.
        {1}{B}, {T}, Sacrifice this land: Create a 1/1 colorless Pilot creature token with "This token saddles Mounts and crews Vehicles as though its power were 2 greater." Activate only as a sorcery.
    """.trimIndent())
    val gorgeLeaper = Cards.invent("Gorge Leaper", "{1}{W}", "Creature — Kor Pilot", "Whenever this creature saddles a Mount or crews a Vehicle during your main phase, that Mount or Vehicle gains flying until end of turn.", "3/1")
    val pitlaneMechanic = Cards.invent("Pitlane Mechanic", "{2}{W}", "Creature — Human Artificer", "{3}{W}: Put a +1/+1 counter on target Mount or Vehicle.", "3/3")
    val recklessVelocicorn = Cards.invent("Reckless Velocicorn", "{3}{R}", "Creature — Minotaur Pilot", "Whenever this creature saddles a Mount or crews a Vehicle during your main phase, that Mount or Vehicle gets +2/+0 and gains trample until end of turn.", "3/3")
    val reefLanes = Cards.invent("Reef Lanes", null, "Land", """
        This land enters tapped unless you control a Mount or Vehicle.
        {T}: Add {U}.
        {1}{U}, {T}, Sacrifice this land: Create a 1/1 colorless Pilot creature token with "This token saddles Mounts and crews Vehicles as though its power were 2 greater." Activate only as a sorcery.
    """.trimIndent())
    val roadFury = Cards.invent("Road Fury", "{R}", "Instant", "Road Fury deals X damage to target creature or planeswalker, where X is 2 plus the number of Mounts and Vehicles you control.")
    val rockyLanes = Cards.invent("Rocky Lanes", null, "Land", """
        This land enters tapped unless you control a Mount or Vehicle.
        {T}: Add {R}.
        {1}{R}, {T}, Sacrifice this land: Create a 1/1 colorless Pilot creature token with "This token saddles Mounts and crews Vehicles as though its power were 2 greater." Activate only as a sorcery.
    """.trimIndent())
    val undyingPilot = Cards.invent("Undying Pilot", "{1}{B}", "Creature — Zombie Pilot", """
        This creature saddles Mounts and crews Vehicles as though its power were 2 greater.
        {3}{B}: Return this card from your graveyard to your hand.
    """.trimIndent(), "2/2")
    val waysideAid = Cards.invent("Wayside Aid", "{2}{W}", "Enchantment — Aura", """
        Enchant creature or Vehicle
        When this Aura enters, create a 1/1 colorless Pilot creature token with "This token saddles Mounts and crews Vehicles as though its power were 2 greater."
        Enchanted permanent gets +1/+1 and has lifelink.
    """.trimIndent())
    val wildLanes = Cards.invent("Wild Lanes", null, "Land", """
        This land enters tapped unless you control a Mount or Vehicle.
        {T}: Add {G}.
        {1}{G}, {T}, Sacrifice this land: Create a 1/1 colorless Pilot creature token with "This token saddles Mounts and crews Vehicles as though its power were 2 greater." Activate only as a sorcery.
    """.trimIndent())
    // chunk 06
    val aetherSiphoner = Cards.invent("Aether Siphoner", "{1}{U}{U}", "Artifact", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        {2}, {T}: Draw a card.
        Max speed — Whenever you draw a card, each opponent mills two cards. (Each opponent puts the top two cards of their library into their graveyard.)
    """.trimIndent())
    val fuelGuzzler = Cards.invent("Fuel Guzzler", "{B}", "Creature — Vampire Rogue", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        This creature enters tapped.
        Max speed — {B}, Sacrifice another creature or Vehicle: Draw a card.
    """.trimIndent(), "2/1")
    val gastalPlunderer = Cards.invent("Gastal Plunderer", "{2}{B}", "Creature — Vampire Rogue", """
        Start your engines!
        When this creature enters, target opponent reveals their hand. You choose an instant or sorcery card from it. That player discards that card.
        Max speed — This creature gets +1/+1 and has menace.
    """.trimIndent(), "2/1")
    val gleamwheelUpgrades = Cards.invent("Gleamwheel Upgrades", "{W}", "Enchantment — Aura", """
        Enchant creature or Vehicle
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Enchanted permanent gets +1/+1 and has vigilance.
        Max speed — You may cast this card from your graveyard.
    """.trimIndent())
    val glitchwraithPathfinder = Cards.invent("Glitchwraith Pathfinder", "{2}{U}", "Creature — Spirit Scout", """
        Flying
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Max speed — {3}, Exile this card from your graveyard: Draw a card.
    """.trimIndent(), "2/2")
    val hazardousSnare = Cards.invent("Hazardous Snare", "{2}{W}", "Artifact", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        When this artifact enters, exile target nonland permanent an opponent controls until this artifact leaves the battlefield.
        Max speed — {T}: Put a +1/+1 counter on target creature or Vehicle you control. Activate only as a sorcery.
    """.trimIndent())
    val honorOfTheHighway = Cards.invent("Honor of the Highway", "{3}{W}", "Creature — Zombie Cat Warrior", """
        Vigilance
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Max speed — At the beginning of combat on your turn, target creature or Vehicle you control gains double strike until end of turn.
    """.trimIndent(), "2/5")
    val hourOfTriumph = Cards.invent("Hour of Triumph", "{2}{B}", "Enchantment", """
        Start your engines!
        When this enchantment enters, create a 2/2 black Zombie creature token.
        Max speed — {1}{B}, Sacrifice this enchantment: Search your library for a card, put it into your hand, then shuffle. Activate only as a sorcery.
    """.trimIndent())
    val leoninPathfinder = Cards.invent("Leonin Pathfinder", "{1}{W}", "Creature — Cat Scout", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        During your turn, this creature has first strike.
        Max speed — {3}, Exile this card from your graveyard: Draw a card.
    """.trimIndent(), "2/2")
    val momentumCrusher = Cards.invent("Momentum Crusher", "{1}{B}", "Enchantment", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        When this enchantment enters, each opponent sacrifices a creature or Vehicle of their choice. Each opponent who can't discards a card.
        {2}, Sacrifice this enchantment: You gain life equal to your speed.
    """.trimIndent())
    val mutantPathfinder = Cards.invent("Mutant Pathfinder", "{2}{B}", "Creature — Mutant Scout", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        {2}: This creature gets +1/+1 until end of turn.
        Max speed — {3}, Exile this card from your graveyard: Draw a card.
    """.trimIndent(), "2/3")
    val risenNecrolord = Cards.invent("Risen Necrolord", "{4}{B}", "Creature — Zombie Cat Knight", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Max speed — At the beginning of your end step, create a 2/2 black Zombie creature token.
    """.trimIndent(), "5/4")
    val roostingBot = Cards.invent("Roosting Bot", "{W}", "Artifact Creature — Robot", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        When this creature dies, create a 1/1 colorless Servo artifact creature token.
        Max speed — This creature gets +1/+0.
    """.trimIndent(), "1/1")
    val slickMimic = Cards.invent("Slick Mimic", "{1}{U}", "Creature — Ooze", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Max speed — {1}, Sacrifice this creature: Copy target spell you control. You may choose new targets for the copy. (A copy of a permanent spell becomes a token.)
    """.trimIndent(), "1/3")
    val spikebackHarrier = Cards.invent("Spikeback Harrier", "{4}{U}", "Artifact Creature — Robot Turtle", "When this creature enters, return target creature or Vehicle an opponent controls to its owner's hand. If that opponent's speed is greater than each other player's speed, reduce that opponent's speed by 1. This effect can't reduce their speed below 1.", "4/4")
    val streakingOilguzzler = Cards.invent("Streaking Oilguzzler", "{4}{B}", "Creature — Vampire", """
        Flying, haste
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Max speed — This creature has lifelink.
    """.trimIndent(), "3/3")
    val swiftfeatherAssailant = Cards.invent("Swiftfeather Assailant", "{3}{W}", "Creature — Bird Warrior", """
        Flying
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Max speed — This creature gets +0/+1 and has vigilance.
    """.trimIndent(), "3/3")
    val theVelocityFiend = Cards.invent("The Velocity Fiend", "{3}{B}{B}", "Legendary Creature — Demon", """
        Flying, trample
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        At the beginning of your end step, you draw X cards and lose X life, where X is your speed.
    """.trimIndent(), "5/5")
    val wxxnvWordyHost = Cards.invent("Wxxnv, Wordy Host", "{1}{U}", "Legendary Creature — Homunculus", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        You have no maximum hand size.
        Max speed — If you would draw a card, draw two cards instead.
    """.trimIndent(), "0/4")
    // chunk 07
    val amonkhetSpeedway = Cards.invent("Amonkhet Speedway", null, "Land", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        {T}: Add {C}.
        Max speed — {T}: Target creature gains haste until end of turn.
    """.trimIndent())
    val avishkarSpeedway = Cards.invent("Avishkar Speedway", null, "Land", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        {T}: Add {C}.
        Max speed — {3}, {T}, Discard a card: Draw a card.
    """.trimIndent())
    val burnoutBashrider = Cards.invent("Burnout Bashrider", "{R}", "Creature — Goblin Warrior", """
        Menace
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        {2}: This creature gets +1/+0 until end of turn.
        Max speed — This creature has double strike.
    """.trimIndent(), "1/1")
    val embalmedAscender = Cards.invent("Embalmed Ascender", "{1}{W}{B}", "Creature — Zombie", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        When this creature enters, create a 2/2 black Zombie creature token.
        Max speed — Whenever a creature you control dies, each opponent loses 1 life and you gain 1 life.
    """.trimIndent(), "1/2")
    val endriderCatalyst = Cards.invent("Endrider Catalyst", "{1}{R}", "Creature — Human Warrior", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Max speed — {T}: Add {R}{R}.
    """.trimIndent(), "3/1")
    val endriderSpikeshooter = Cards.invent("Endrider Spikeshooter", "{3}{R}", "Creature — Human Mercenary", """
        Reach
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Max speed — At the beginning of your upkeep, exile the top card of your library. You may play that card this turn.
    """.trimIndent(), "3/4")
    val farFortunaFinalBoss = Cards.invent("Far Fortuna, Final Boss", "{2}{B}{R}", "Legendary Creature — Human Mercenary", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Whenever you attack, Far Fortuna deals 1 damage to each opponent.
        Max speed — If a source you control would deal damage to an opponent or a permanent an opponent controls, it deals that much damage plus 1 instead.
    """.trimIndent(), "4/5")
    val gastalThrillchaser = Cards.invent("Gastal Thrillchaser", "{B}{R}", "Creature — Lizard Berserker", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        When this creature enters, it deals 1 damage to target opponent and you gain 1 life.
        Max speed — This creature has deathtouch and haste.
    """.trimIndent(), "2/3")
    val goblinPathfinder = Cards.invent("Goblin Pathfinder", "{2}{R}", "Creature — Goblin Scout", """
        Trample
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Max speed — {3}, Exile this card from your graveyard: Draw a card.
    """.trimIndent(), "3/2")
    val hazoraGodracer = Cards.invent("Hazora, Godracer", "{1}{R}", "Legendary Creature — God", """
        Indestructible, haste
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        {1}, {T}: Target creature with power 2 or less can't be blocked this turn.
        Hazora can't attack or block unless you have max speed.
    """.trimIndent(), "5/3")
    val howlpackHeavy = Cards.invent("Howlpack Heavy", "{2}{R}", "Creature — Goblin Mercenary", """
        Start your engines!
        Other Goblins you control have haste.
        At the beginning of combat on your turn, create a 1/1 red Goblin creature token. That token attacks this combat if able.
        Max speed — {T}: Add {R} for each Goblin you control.
    """.trimIndent(), "2/3")
    val kickoffFestivities = Cards.invent("Kickoff Festivities", "{1}{R}", "Enchantment", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        When this enchantment enters, you may discard a card. If you do, draw two cards.
        Max speed — Sacrifice this enchantment: Creatures and Vehicles you control gain haste until end of turn.
    """.trimIndent())
    val loxodonPathfinder = Cards.invent("Loxodon Pathfinder", "{2}{G}", "Creature — Elephant Scout", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Max speed — {3}, Exile this card from your graveyard: Draw a card.
    """.trimIndent(), "3/3")
    val markTheWay = Cards.invent("Mark the Way", "{G}", "Enchantment", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        {3}{G}, Sacrifice this enchantment: Search your library for up to X basic land cards, where X is your speed. Put those cards onto the battlefield tapped, then shuffle.
    """.trimIndent())
    val mendicantHeartPathlight = Cards.invent("Mendicant Heart, Pathlight", "{W}{U}", "Legendary Artifact Creature — Robot", """
        Mendicant Heart's power is equal to the number of artifacts you control.
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Max speed — Whenever you cast an artifact spell, you may pay {1}. If you do, copy it. (The copy becomes a token.)
    """.trimIndent(), "*/3")
    val muragandaSpeedway = Cards.invent("Muraganda Speedway", null, "Land", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        {T}: Add {C}.
        Max speed — {T}: Add {C}{C}.
    """.trimIndent())
    val outrunOblivion = Cards.invent("Outrun Oblivion", "{2}{R}", "Enchantment", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        When this enchantment enters, it deals 5 damage to up to one target creature or planeswalker.
        {2}, Sacrifice this enchantment: It deals 2 damage to each player who doesn't have max speed.
    """.trimIndent())
    val racersLeaderboard = Cards.invent("Racers' Leaderboard", "{4}", "Artifact", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        When this artifact enters, draw two cards, then discard a card.
        Max speed — Spells you cast cost {1} less to cast.
    """.trimIndent())
    val samraTheDrivingForce = Cards.invent("Samra, the Driving Force", "{3}{R}{G}{W}", "Legendary Creature — Human Warrior Cleric", """
        First strike, vigilance, haste
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Other creatures you control get +X/+0, where X is your speed.
        Noncreature spells you cast cost {X} less to cast, where X is your speed.
    """.trimIndent(), "4/5")
    val startingGrid = Cards.invent("Starting Grid", "{3}", "Artifact", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        {T}: Add one mana of any color.
        Max speed — {T}, Sacrifice this artifact: Draw two cards, then discard a card.
    """.trimIndent())
    val walkingSepulcher = Cards.invent("Walking Sepulcher", "{2}", "Artifact Creature — Zombie Cat", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Max speed — This creature gets +1/+2.
    """.trimIndent(), "2/1")
    val zahraGlorysPast = Cards.invent("Zahra, Glory's Past", "{W}{B}", "Legendary Creature — Zombie Cat Warrior", """
        Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
        Sacrifice another creature: Surveil 1. Activate only once each turn.
        Max speed — Whenever a nontoken creature you control dies, create a tapped 2/2 black Zombie creature token.
    """.trimIndent(), "3/2")
    // chunk 08
    val adrenalineRider = Cards.invent("Adrenaline Rider", "{2}{R}", "Creature — Minotaur Pilot", """
        Whenever a player casts a spell, if it's not their turn, this creature deals 4 damage to them.
        Whenever you activate an exhaust ability, put a +1/+1 counter on this creature.
    """.trimIndent(), "3/3")
    val afterburnerAdept = Cards.invent("Afterburner Adept", "{2}{G}", "Creature — Goblin Artificer", """
        Exhaust — {2}{G}{G}: Put two +1/+1 counters on this creature. (Activate each exhaust ability only once.)
        Whenever you activate an exhaust ability, return this card from your graveyard to the battlefield.
    """.trimIndent(), "4/2")
    val boomProfessor = Cards.invent("Boom Professor", "{1}{R}{G}", "Creature — Goblin Advisor", """
        Exhaust abilities of other permanents you control cost {2} less to activate.
        Exhaust — {4}{R}{G}: Creatures and Vehicles you control gain trample until end of turn. Put two +1/+1 counters on this creature. (Activate each exhaust ability only once.)
    """.trimIndent(), "3/3")
    val draconauticEngineer = Cards.invent("Draconautic Engineer", "{1}{R}", "Creature — Goblin Artificer", """
        Exhaust — {R}: Other creatures you control gain haste until end of turn. Put a +1/+1 counter on this creature. (Activate each exhaust ability only once.)
        Exhaust — {3}{R}: Create a 4/4 red Dinosaur Dragon creature token with flying.
    """.trimIndent(), "2/2")
    val elvishRefitter = Cards.invent("Elvish Refitter", "{2}{G}", "Creature — Elf Druid", """
        During your turn, as long as you haven't activated an exhaust ability this turn, you may activate exhaust abilities as though they haven't been activated.
        Exhaust — {1}{G}: Put a +1/+1 counter on this creature. (Activate each exhaust ability only once.)
    """.trimIndent(), "2/3")
    val greasegripGoblin = Cards.invent("Greasegrip Goblin", "{R}", "Creature — Goblin Artificer", "Exhaust — {2}{R}: Discard up to two cards, then draw that many cards. Put a +1/+1 counter on this creature. (Activate each exhaust ability only once.)", "2/1")
    val greenbeltWarden = Cards.invent("Greenbelt Warden", "{1}{G}", "Creature — Elf Ranger", """
        {G}: Target creature gains trample until end of turn.
        Exhaust — {3}{G}: Put three +1/+1 counters on this creature. (Activate each exhaust ability only once.)
    """.trimIndent(), "2/2")
    val journeysHalt = Cards.invent("Journey's Halt", "{4}{W}", "Instant", """
        This spell costs {3} less to cast if it targets a tapped permanent.
        Exile target creature or Vehicle.
    """.trimIndent())
    val lotuswingDisciple = Cards.invent("Lotuswing Disciple", "{2}{W}", "Creature — Bird Cleric", """
        Flying
        When this creature enters, target creature or Vehicle gains lifelink and indestructible until end of turn.
    """.trimIndent(), "2/2")
    val mindbrookMerfolk = Cards.invent("Mindbrook Merfolk", "{U}", "Creature — Merfolk Wizard", "Exhaust — {X}{U}{U}, {T}: Draw X cards. Put a +1/+1 counter on each Merfolk creature you control. (Activate each exhaust ability only once.)", "1/1")
    val pacesetterChampion = Cards.invent("Pacesetter Champion", "{2}{R}", "Creature — Human Pilot", "Exhaust — {2}{R}: Put a +1/+1 counter on this creature. It gains double strike until end of turn. (Activate each exhaust ability only once.)", "2/3")
    val pathlightSynergist = Cards.invent("Pathlight Synergist", "{3}{W}", "Artifact Creature — Robot Artificer", """
        Flying
        This creature gets +1/+0 for each artifact you control.
    """.trimIndent(), "0/4")
    val perilOfTheDunes = Cards.invent("Peril of the Dunes", "{3}{G}", "Creature — Wurm", """
        Reach, trample
        Exhaust — {6}{G}: Put three +1/+1 counters on this creature. (Activate each exhaust ability only once.)
    """.trimIndent(), "4/4")
    val pitAutomata = Cards.invent("Pit Automata", "{2}", "Artifact Creature — Construct", """
        Defender
        {T}: Add {C}{C}. Spend this mana only to activate abilities.
        {2}, {T}: When you next activate an exhaust ability that isn't a mana ability this turn, copy it. You may choose new targets for the copy.
    """.trimIndent(), "0/4")
    val prowcatcherExpert = Cards.invent("Prowcatcher Expert", "{1}{R}", "Creature — Goblin Warrior", """
        Haste
        Exhaust — {3}{R}: Put two +1/+1 counters on this creature. (Activate each exhaust ability only once.)
    """.trimIndent(), "2/1")
    val riverwashMonument = Cards.invent("Riverwash Monument", "{1}{U}", "Artifact", """
        {1}, {T}: Any number of target players each mill two cards. (Each of them puts the top two cards of their library into their graveyard.)
        Exhaust — {2}{U}{U}, {T}: Any number of target players each mill cards equal to the number of cards in their graveyard. (Activate each exhaust ability only once.)
    """.trimIndent())
    val sabotageTactician = Cards.invent("Sabotage Tactician", "{2}{U}{U}", "Creature — Vedalken Ranger", """
        Flying, vigilance
        Whenever one or more creatures attack you, those creatures get -1/-0 until end of turn.
        Exhaust — {5}{U}{U}: Put three +1/+1 counters on this creature. (Activate each exhaust ability only once.)
    """.trimIndent(), "2/2")
    val sharpEyedBuccaneer = Cards.invent("Sharp-Eyed Buccaneer", "{2}{U}", "Creature — Octopus Pirate", """
        Vigilance
        Exhaust — {1}{U}: Draw a card, then discard a card. Put a +1/+1 counter on this creature. (Activate each exhaust ability only once.)
    """.trimIndent(), "2/3")
    val shutterLauncher = Cards.invent("Shutter Launcher", "{3}", "Artifact Creature — Construct", "Exhaust — {3}: Put a +1/+1 counter on this creature. Create a 1/1 colorless Thopter artifact creature token with flying. (Activate each exhaust ability only once.)", "2/2")
    val skydashEngineer = Cards.invent("Skydash Engineer", "{1}{U}", "Creature — Human Pilot", """
        Flying
        Exhaust — {4}{U}: Put two +1/+1 counters on this creature. (Activate each exhaust ability only once.)
    """.trimIndent(), "1/3")
    val stampedingScurrier = Cards.invent("Stampeding Scurrier", "{G}", "Creature — Mouse", "Exhaust — {3}{G}: Put a +1/+1 counter on this creature. Create a 3/3 green Elephant creature token. (Activate each exhaust ability only once.)", "1/1")
    val voyagerSteelwelder = Cards.invent("Voyager Steelwelder", "{2}{W}", "Artifact Creature — Robot Artificer", "Artifact spells you cast cost {1} less to cast.", "2/4")
    // chunk 09
    val bounceAway = Cards.invent("Bounce Away", "{U}", "Instant", "Return target creature or Vehicle to its owner's hand.")
    val distractionUnit = Cards.invent("Distraction Unit", "{1}{U}", "Artifact Creature — Robot", """
        Flying
        {U}, Sacrifice this creature: Counter target instant or sorcery spell unless its controller pays {3}.
    """.trimIndent(), "2/1")
    val drownTheEngine = Cards.invent("Drown the Engine", "{2}{U}", "Enchantment — Aura", """
        Enchant creature or Vehicle
        When this Aura enters, tap enchanted permanent.
        Enchanted permanent loses all abilities and doesn't untap during its controller's untap step.
    """.trimIndent())
    val gearhunterSerpent = Cards.invent("Gearhunter Serpent", "{5}{U}{U}", "Creature — Serpent", """
        Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
        {5}{U}: This creature can't be blocked this turn.
    """.trimIndent(), "5/6")
    val luteTheWayfinder = Cards.invent("Lute, the Wayfinder", "{2}{G}{U}{R}", "Legendary Creature — Beast Noble", """
        Double strike, vigilance, haste
        Exhaust — {G}, {T}: Add three mana of any one color. (Activate each exhaust ability only once.)
        Exhaust — {U}, {T}: Draw three cards.
        Exhaust — {R}, {T}: Lute deals 3 damage to any target.
    """.trimIndent(), "2/4")
    val nimbleThoptersmith = Cards.invent("Nimble Thoptersmith", "{3}{U}", "Creature — Vedalken Artificer", "When this creature enters, create a 1/1 colorless Thopter artifact creature token with flying.", "3/2")
    val pathlightOptimizer = Cards.invent("Pathlight Optimizer", "{1}{U}", "Artifact Creature — Robot", "{T}: Add {U}. Spend this mana only to cast an artifact spell or activate an ability.", "2/1")
    val recallGuardian = Cards.invent("Recall Guardian", "{4}{U}", "Artifact Creature — Robot Artificer", """
        Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
        Flying
    """.trimIndent(), "3/4")
    val recyclingBay = Cards.invent("Recycling Bay", "{2}{U}", "Artifact", "{2}, {T}, Sacrifice another artifact: Search your library for an artifact card with mana value equal to 1 plus the sacrificed artifact's mana value, put that card onto the battlefield, then shuffle. Activate only as a sorcery.")
    val redstreakRocketeerBoss = Cards.invent("Redstreak, Rocketeer Boss", "{R}{G}", "Legendary Creature — Goblin Pilot", """
        Vigilance
        {T}: Add X mana of any one color, where X is Redstreak's power. Spend this mana only to activate abilities.
        Exhaust — {10}{R}{G}: Put any number of permanent cards from your hand onto the battlefield. (Activate each exhaust ability only once.)
    """.trimIndent(), "2/3")
    val sitaraVarmaMaskedRacer = Cards.invent("Sitara Varma, Masked Racer", "{G}{U}", "Legendary Creature — Human Rogue", "Exhaust — {X}{G}{G}{U}: Put X +1/+1 counters on Sitara Varma. Then you may have the base power and toughness of each other creature you control become equal to Sitara Varma's power until end of turn. (Activate each exhaust ability only once.)", "2/3")
    val skyserpentSearcher = Cards.invent("Skyserpent Searcher", "{G}{U}", "Creature — Snake", """
        Flying, deathtouch
        Exhaust — {4}: Reveal cards from the top of your library until you reveal two land cards. Put those land cards onto the battlefield tapped and the rest on the bottom of your library in a random order. Put a +1/+1 counter on this creature. (Activate each exhaust ability only once.)
    """.trimIndent(), "1/1")
    val spectralObstruction = Cards.invent("Spectral Obstruction", "{1}{U}", "Instant", "Counter target artifact or creature spell unless its controller pays {4}.")
    val spellPuncture = Cards.invent("Spell Puncture", "{U}", "Instant", "Counter target noncreature spell unless its controller pays {2}.")
    val stockPile = Cards.invent("Stock Pile", "{2}{U}", "Sorcery", "Look at the top five cards of your library. Put two of them into your hand and the rest on the bottom of your library in any order.")
    val transitAdept = Cards.invent("Transit Adept", "{2}{U}", "Creature — Human Wizard", "When this creature enters, you may search your library for an artifact card with mana value 4 or 5, reveal it, put it into your hand, then shuffle.", "2/2")
    val unrelentingPlan = Cards.invent("Unrelenting Plan", "{2}{U}", "Enchantment", "At the beginning of your end step, untap all nonland permanents you control.")
    val waysideBlowout = Cards.invent("Wayside Blowout", "{2}{U}", "Sorcery", """
        This spell costs {2} less to cast if it targets a permanent with mana value 1.
        Return target creature or Vehicle an opponent controls to its owner's hand.
        Draw a card.
    """.trimIndent())
    val wintraCursedRider = Cards.invent("Wintra, Cursed Rider", "{U}{B}", "Legendary Creature — Human Warlock", """
        Ward—Pay 2 life.
        Artifacts you control have "Ward—Pay 2 life."
        Exhaust — {2}{U}{B}, {T}, Exile X artifact cards from your graveyard: Each other nonartifact creature gets -X/-X until end of turn. (Activate each exhaust ability only once.)
    """.trimIndent(), "3/2")
    // chunk 10
    val ancestralVendetta = Cards.invent("Ancestral Vendetta", "{3}{B}", "Sorcery", "Choose a card name. Search target opponent's graveyard, hand, and library for up to four cards with that name and exile them. Then that player shuffles.")
    val basraRiderOfDawn = Cards.invent("Basra, Rider of Dawn", "{W}", "Legendary Creature — Human Knight", """
        {W}, {T}, Exert Basra: Create a 1/1 white Cat creature token with lifelink. (An exerted creature won't untap during your next untap step.)
        Cycling {2}{W} ({2}{W}, Discard this card: Draw a card.)
        When you cycle this card, Cats you control gain hexproof and indestructible until end of turn.
    """.trimIndent(), "2/1")
    val gleamshieldParry = Cards.invent("Gleamshield Parry", "{W}", "Instant", """
        Target creature gets +2/+2 until end of turn.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent())
    val howlersHauler = Cards.invent("Howler's Hauler", "{3}{U}", "Creature — Seal Pirate", """
        Cycling {1}{U} ({1}{U}, Discard this card: Draw a card.)
        When you cycle this card, target creature or Vehicle an opponent controls gets -3/-0 until end of turn.
    """.trimIndent(), "3/4")
    val nightBazaar = Cards.invent("Night Bazaar", null, "Land", """
        This land enters tapped. As it enters, choose a color.
        {T}: Add one mana of the chosen color.
        Cycling {3} ({3}, Discard this card: Draw a card.)
    """.trimIndent())
    val scavengingSkyray = Cards.invent("Scavenging Skyray", "{1}{U}", "Creature — Fish Pirate", """
        Flying
        Whenever you discard one or more cards, put that many +1/+1 counters on this creature.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent(), "1/2")
    val spectacularWreckage = Cards.invent("Spectacular Wreckage", "{3}{W}{W}", "Sorcery", """
        All creatures and Vehicles lose indestructible until end of turn, then destroy all creatures and Vehicles.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent())
    val stalledEngine = Cards.invent("Stalled Engine", "{1}{U}", "Sorcery", """
        Tap target creature or Vehicle, then put three stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent())
    val swapTheHelm = Cards.invent("Swap the Helm", "{4}{U}", "Sorcery", """
        Exchange control of target artifact or creature you control and target artifact or creature an opponent controls.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent())
    val tallowShapethief = Cards.invent("Tallow Shapethief", "{3}{U}", "Creature — Shapeshifter", """
        Flash
        You may have this creature enter as a copy of an artifact or creature you control.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent(), "0/0")
    val tumbleUp = Cards.invent("Tumble Up", "{3}{U}", "Instant", """
        Target nonland permanent's owner puts it on their choice of the top or bottom of their library.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent())
    val valiantTakedown = Cards.invent("Valiant Takedown", "{1}{W}", "Instant", """
        Destroy target creature with toughness 4 or greater.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent())
    val valorsDreadnought = Cards.invent("Valor's Dreadnought", "{4}{W}{W}{W}", "Legendary Artifact — Vehicle", """
        Flying, first strike, lifelink
        Crew 3
        Cycling {X}{2}{W} ({X}{2}{W}, Discard this card: Draw a card.)
        When you cycle this card, create X 1/1 colorless Pilot creature tokens with "This token saddles Mounts and crews Vehicles as though its power were 2 greater."
    """.trimIndent(), "7/7")
    // chunk 11
    val agonodonRex = Cards.invent("Agonodon Rex", "{3}{G}{G}", "Creature — Dinosaur", """
        Trample
        Cycling {2}{G} ({2}{G}, Discard this card: Draw a card.)
        When you cycle this card, put two +1/+1 counters on up to one target creature or Vehicle. It gains trample and indestructible until end of turn.
    """.trimIndent(), "8/8")
    val chitinTombstalker = Cards.invent("Chitin Tombstalker", "{5}{B}", "Creature — Insect Warrior", """
        This spell costs {1} less to cast for each artifact and/or creature card in your graveyard.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent(), "5/4")
    val intimidatingTactics = Cards.invent("Intimidating Tactics", "{B}", "Sorcery", """
        Target opponent reveals their hand. You choose an artifact or creature card from it. Exile that card.
        Cycling {3} ({3}, Discard this card: Draw a card.)
    """.trimIndent())
    val locustMist = Cards.invent("Locust Mist", "{B}", "Instant", """
        Target creature gets -1/-1 until end of turn.
        Cycling {B} ({B}, Discard this card: Draw a card.)
    """.trimIndent())
    val magmakinGunner = Cards.invent("Magmakin Gunner", "{2}{R}", "Creature — Elemental Pirate", """
        Whenever you discard one or more cards, this creature deals that much damage to each opponent.
        Cycling {1}{R} ({1}{R}, Discard this card: Draw a card.)
        When you cycle this card, it deals 1 damage to each opponent.
    """.trimIndent(), "1/4")
    val maraudingShark = Cards.invent("Marauding Shark", "{R}", "Creature — Shark Pirate", """
        Whenever you discard one or more cards, put that many +1/+1 counters on this creature.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent(), "1/1")
    val shefetHellfiend = Cards.invent("Shefet Hellfiend", "{5}{B}{B}", "Creature — Demon", """
        Flying
        When this creature enters, all other creatures get -2/-2 until end of turn.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent(), "5/5")
    val skyshatter = Cards.invent("Skyshatter", "{1}{R}", "Instant", """
        Destroy target artifact.
        Cycling {R} ({R}, Discard this card: Draw a card.)
    """.trimIndent())
    val stokeTheFlamesAnew = Cards.invent("Stoke the Flames Anew", "{2}{R}", "Instant", """
        Stoke the Flames Anew deals 2 damage to each creature.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent())
    val wanderingKetradon = Cards.invent("Wandering Ketradon", "{4}{G}{G}", "Creature — Dinosaur", """
        Reach
        When this creature enters, you gain 4 life.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent(), "6/6")
    val webshotElite = Cards.invent("Webshot Elite", "{G}{G}", "Creature — Insect Archer", """
        Reach
        Cycling {X}{G}{G} ({X}{G}{G}, Discard this card: Draw a card.)
        When you cycle this card, destroy up to one target artifact or enchantment with mana value X.
    """.trimIndent(), "3/3")
    val wreckCollector = Cards.invent("Wreck Collector", "{4}", "Artifact Creature — Construct", """
        Whenever this creature enters or attacks, exile up to one target card from a graveyard. You gain 1 life.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent(), "3/4")
    // chunk 12
    val bogFeast = Cards.invent("Bog Feast", "{1}{B}", "Sorcery", "Choose target creature, planeswalker, or Vehicle. Mill two cards, then destroy the chosen permanent if its mana value is less than or equal to the number of cards in your graveyard.")
    val gontraNightEnvoy = Cards.invent("Gontra, Night Envoy", "{2}{B}{B}", "Legendary Creature — Aetherborn Rogue", """
        Whenever a player casts a spell they don't own, that player creates a Treasure token.
        Whenever a creature deals combat damage to one of your opponents, its controller looks at the top card of that opponent's library and exiles it face down. They may play that card for as long as it remains exiled. Mana of any type can be spent to cast a spell this way.
    """.trimIndent(), "3/4")
    val gravehauntSpecter = Cards.invent("Gravehaunt Specter", "{B}{B}", "Creature — Vampire Spirit", """
        This creature can't block.
        This creature has haste as long as an opponent has 10 or less life.
        Landfall — Whenever a land you control enters, you may return this card from your graveyard to the battlefield.
    """.trimIndent(), "2/1")
    val grimSpearthrower = Cards.invent("Grim Spearthrower", "{2}{B}", "Creature — Human Warrior", "Whenever you attack, target attacking creature gets +1/+0 until end of turn. When that creature dies this turn, surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)", "3/2")
    val grimTrinket = Cards.invent("Grim Trinket", "{B}", "Artifact", """
        When this artifact enters, target creature an opponent controls gets -2/-2 until end of turn.
        {2}{B}, {T}, Sacrifice this artifact: Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """.trimIndent())
    val infernalSideswipe = Cards.invent("Infernal Sideswipe", "{B}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice an artifact or creature.
        Destroy target creature or Vehicle. If the sacrificed permanent was a Vehicle, draw a card.
    """.trimIndent())
    val maximumThrottle = Cards.invent("Maximum Throttle", "{1}{B}", "Instant", "Put a +1/+1 counter on target creature. It gains deathtouch and indestructible until end of turn.")
    val motorRat = Cards.invent("Motor Rat", "{B}", "Creature — Zombie Rat", """
        Deathtouch
        {5}{B}: Each opponent loses 2 life.
    """.trimIndent(), "1/1")
    val pactdollHorror = Cards.invent("Pactdoll Horror", "{3}{B}", "Artifact Creature — Toy", "Whenever this creature or another artifact you control enters, each opponent loses 1 life and you gain 1 life.", "3/4")
    val recklessShortcut = Cards.invent("Reckless Shortcut", "{2}{B}", "Sorcery", "Draw two cards. Each player loses 2 life.")
    val shroudclothWrappings = Cards.invent("Shroudcloth Wrappings", "{2}{B}{B}", "Artifact", """
        Zombies you control get +1/+1.
        {T}: Target creature card in your graveyard gains embalm until end of turn. The embalm cost is equal to its mana cost. (Exile that card and pay its embalm cost: Create a token that's a copy of it, except it's a white Zombie in addition to its other types and has no mana cost. Embalm only as a sorcery.)
    """.trimIndent())
    val siphonFuel = Cards.invent("Siphon Fuel", "{4}{B}", "Instant", "Target creature gets -6/-6 until end of turn. You gain 2 life.")
    val spinOff = Cards.invent("Spin Off", "{1}{B}{B}", "Instant", "Destroy target creature or Vehicle.")
    val wickerkinIndomitable = Cards.invent("Wickerkin Indomitable", "{3}{B}", "Artifact Creature — Scarecrow", "You may cast this card from your graveyard by paying 2 life and sacrificing an artifact or creature in addition to paying its other costs.", "4/3")
    val wreckageWickerkin = Cards.invent("Wreckage Wickerkin", "{1}{B}", "Artifact Creature — Scarecrow", """
        Flying
        When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """.trimIndent(), "1/3")
    val wretchedDolly = Cards.invent("Wretched Dolly", "{1}{B}", "Artifact Creature — Toy", "{B}, {T}: Surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)", "3/1")
    // chunk 13
    val countOnFortune = Cards.invent("Count on Fortune", "{R}{R}{R}", "Enchantment", "At the beginning of your upkeep, exile the top card of your library. You may play that card this turn.")
    val crashAndBlaze = Cards.invent("Crash and Blaze", "{3}{R}", "Instant", """
        Choose one —
        • Destroy target Vehicle.
        • Crash and Blaze deals 6 damage to target creature or planeswalker.
    """.trimIndent())
    val darettoRocketEngineer = Cards.invent("Daretto, Rocket Engineer", "{4}{R}", "Legendary Creature — Goblin Artificer", """
        Daretto's power is equal to the greatest mana value among artifacts you control.
        Whenever Daretto enters or attacks, choose target artifact card in your graveyard. You may sacrifice an artifact. If you do, return the chosen card to the battlefield.
    """.trimIndent(), "*/5")
    val gastalWrecker = Cards.invent("Gastal Wrecker", "{2}{R}", "Creature — Human Berserker", "When this creature enters, you may sacrifice a creature or Vehicle. When you do, destroy target artifact an opponent controls.", "3/2")
    val lightningJolt = Cards.invent("Lightning Jolt", "{1}{R}", "Instant", "Lightning Jolt deals 3 damage to any target.")
    val pedalToTheFloor = Cards.invent("Pedal to the Floor", "{X}{R}", "Instant", "Target creature gets +X/+0 and gains first strike until end of turn.")
    val thunderheadCannoneer = Cards.invent("Thunderhead Cannoneer", "{4}{R}", "Creature — Shark Pirate", """
        Reach
        Discard a card: Draw a card. Activate only as a sorcery and only once each turn.
    """.trimIndent(), "4/5")
    val wideOpenThrottle = Cards.invent("Wide Open Throttle", "{4}{R}{R}", "Sorcery", """
        After this main phase, there are two additional combat phases.
        At the beginning of each combat this turn, untap all creatures that attacked this turn.
    """.trimIndent())
    // chunk 14
    val beastriderOutrider = Cards.invent("Beastrider Outrider", "{1}{G}", "Creature — Human Knight", "{4}{G}: Look at the top three cards of your library. You may reveal a permanent card from among them and put it into your hand. Put the rest on the bottom of your library in any order.", "2/2")
    val bestowGrandeur = Cards.invent("Bestow Grandeur", "{2}{G}", "Instant", "Target creature gets +4/+4 and gains trample until end of turn.")
    val dredgersForesight = Cards.invent("Dredger's Foresight", "{1}{G}", "Enchantment", """
        Whenever one or more artifact and/or creature cards leave your graveyard, you gain 1 life.
        When this enchantment enters, mill four cards. You may put an artifact, creature, or land card from among the milled cards into your hand. (To mill four cards, put the top four cards of your library into your graveyard.)
    """.trimIndent())
    val fangProtector = Cards.invent("Fang Protector", "{3}{G}", "Creature — Ape Druid", """
        Flash
        When this creature enters, another target creature or Vehicle you control gets +2/+2 until end of turn.
    """.trimIndent(), "4/2")
    val fangDruidCaller = Cards.invent("Fang-Druid Caller", "{3}{G}", "Creature — Ape Druid", """
        Reach
        When this creature enters, you may search your library and/or graveyard for a creature card with no abilities, reveal it, and put it into your hand. If you search your library this way, shuffle.
    """.trimIndent(), "2/4")
    val marchOfTheEndlessOoze = Cards.invent("March of the Endless Ooze", "{3}{G}{G}{G}", "Enchantment", """
        Creatures you control have base power and toughness 6/6 and are Oozes in addition to their other types.
        Whenever an opponent casts a spell, if it's not their turn, you create a 3/3 green Elephant creature token.
    """.trimIndent())
    val moltKeeper = Cards.invent("Molt Keeper", "{G}", "Creature — Insect Druid", """
        {T}: Mill a card. (Put the top card of your library into your graveyard.)
        {T}, Exile a card from your graveyard: Add one mana of any color.
    """.trimIndent(), "1/1")
    val oozeSentry = Cards.invent("Ooze Sentry", "{3}{G}", "Creature — Ooze", "When this creature enters, mill two cards, then put a +1/+1 counter on this creature for each artifact and/or creature card in your graveyard. (To mill two cards, put the top two cards of your library into your graveyard.)", "2/2")
    val oviraAutomechArtisan = Cards.invent("Ovira, Automech Artisan", "{3}{G}", "Legendary Creature — Human Artificer", """
        Each creature that's attacking one of your opponents has trample.
        {G}, {T}: You may put a creature or Vehicle card from your hand onto the battlefield. If you put an artifact onto the battlefield this way, put two +1/+1 counters on it.
    """.trimIndent(), "1/2")
    val plowUnderFoe = Cards.invent("Plow Under Foe", "{G}", "Sorcery", """
        Choose one —
        • Target creature you control fights target creature an opponent controls. (Each deals damage equal to its power to the other.)
        • Destroy target Vehicle.
    """.trimIndent())
    val potholeVole = Cards.invent("Pothole Vole", "{2}{G}", "Creature — Mole", "When this creature enters, mill three cards, then you may return a land card from your graveyard to your hand. (To mill three cards, put the top three cards of your library into your graveyard.)", "2/3")
    val regalImperiodon = Cards.invent("Regal Imperiodon", "{1}{G}{G}", "Creature — Dinosaur", "Other Dinosaurs you control get +1/+1.", "5/4")
    val riseFromTheRubble = Cards.invent("Rise from the Rubble", "{2}{G}", "Sorcery", "Return up to one target creature card, up to one target Mount card, up to one target Vehicle card, and up to one target creature card with no abilities from your graveyard to your hand.")
    val runDown = Cards.invent("Run Down", "{1}{G}", "Instant", """
        This spell costs {1} less to cast if it targets a Mount or Vehicle you control.
        Target creature you control deals damage equal to its power to target creature an opponent controls.
    """.trimIndent())
    val silkenVigor = Cards.invent("Silken Vigor", "{1}{G}", "Enchantment — Aura", """
        Flash
        Enchant creature or Vehicle
        When this Aura enters, untap enchanted permanent.
        Enchanted permanent gets +1/+2 and has reach.
    """.trimIndent())
    val snappedPinions = Cards.invent("Snapped Pinions", "{2}{G}", "Instant", "Destroy target artifact, enchantment, or creature with flying.")
    // chunk 15
    val aatcheekEmeraldHerald = Cards.invent("Aatcheek, Emerald Herald", "{3}{B}{B}{G}", "Legendary Creature — Insect Druid", """
        When Aatcheek enters, create a 1/1 green Insect creature token for each artifact and/or creature card in your graveyard.
        Whenever another Insect you control dies, put a +1/+1 counter on Aatcheek. Each opponent loses 1 life.
    """.trimIndent(), "3/3")
    val brightglassColossus = Cards.invent("Brightglass Colossus", "{G}{G}{W}{W}", "Artifact Creature — Construct", """
        First strike, trample
        When this creature enters, you may search your library for up to two artifact, creature, and/or enchantment cards with mana value 1 or less, reveal them, put them into your hand, then shuffle.
    """.trimIndent(), "4/4")
    val broadsideVolley = Cards.invent("Broadside Volley", "{1}{U}{R}", "Instant", "Broadside Volley deals 5 damage to target creature or planeswalker. Draw a card, then discard a card.")
    val broodheartDevice = Cards.invent("Broodheart Device", "{B}{G}", "Artifact", """
        At the beginning of your upkeep, surveil 1.
        {2}{B}{G}, {T}, Sacrifice this artifact: Return target creature or Vehicle card from your graveyard to the battlefield. Activate only as a sorcery.
    """.trimIndent())
    val captainYowlerSeaTerror = Cards.invent("Captain Yowler, Sea Terror", "{2}{U}{R}", "Legendary Creature — Shark Pirate", """
        Ward—{2}, Pay 2 life.
        Whenever you discard one or more cards, target creature gets +2/+0 until end of turn for each card discarded this way. Whenever that creature deals combat damage to a player this turn, you draw a card.
    """.trimIndent(), "5/4")
    val caradoreHeartOfAlacria = Cards.invent("Caradore, Heart of Alacria", "{2}{G}{W}", "Legendary Creature — Human Knight", """
        When Caradore enters, you may search your library for a Mount or Vehicle card, reveal it, put it into your hand, then shuffle.
        If one or more +1/+1 counters would be put on a creature or Vehicle you control, that many plus one +1/+1 counters are put on it instead.
    """.trimIndent(), "4/2")
    val coalstokeColossus = Cards.invent("Coalstoke Colossus", "{1}{B}{B}{R}{R}", "Artifact Creature — Construct", """
        Menace, deathtouch
        When this creature enters, put target creature card with mana value 4 or less from a graveyard onto the battlefield under your control with a finality counter on it. That creature gains menace, deathtouch, and haste. At the beginning of your next end step, exile that creature.
    """.trimIndent(), "5/4")
    val explosiveEscape = Cards.invent("Explosive Escape", "{3}{R}{W}", "Sorcery", """
        Exile up to one target artifact or creature. Return it to the battlefield under its owner's control at the beginning of the next end step.
        Explosive Escape deals 4 damage to each creature.
    """.trimIndent())
    val hauntTheGrid = Cards.invent("Haunt the Grid", "{3}{U}{B}", "Sorcery", "Choose target opponent. Create two 1/1 colorless Thopter artifact creature tokens with flying. Then the chosen player loses X life and you gain X life, where X is the number of artifacts you control.")
    val ketramosTheBrightDawn = Cards.invent("Ketramos, the Bright Dawn", "{1}{W}{B}", "Legendary Creature — God", """
        Menace, lifelink, indestructible
        Ketramos can't attack or block unless there are seven or more cards in exile.
        Whenever one or more cards are put into exile from graveyards and/or the battlefield during your turn, you draw a card and lose 1 life.
    """.trimIndent(), "4/4")
    val mimeoplastExaltedOne = Cards.invent("Mimeoplast, Exalted One", "{X}{B}{G}{U}", "Legendary Creature — Ooze", """
        As Mimeoplast enters, exile up to X creature cards from your graveyard. It enters with three +1/+1 counters on it for each creature card exiled this way.
        {2}: Mimeoplast becomes a copy of target creature card exiled with it, except it's 0/0 and has this ability.
    """.trimIndent(), "0/0")
    val oildeepColossus = Cards.invent("Oildeep Colossus", "{U}{U}{B}{B}", "Artifact Creature — Construct", """
        Lifelink, ward {1}
        When this creature enters, look at target player's hand. You may choose a card from it. If you do, that player discards that card, then draws a card.
    """.trimIndent(), "4/4")
    val pyrewoodColossus = Cards.invent("Pyrewood Colossus", "{2}{R}{R}{G}{G}", "Artifact Creature — Construct", """
        Vigilance, menace
        When this creature enters, other creatures you control get +2/+2 and gain vigilance and menace until end of turn. Damage can't be prevented this turn.
    """.trimIndent(), "7/7")
    val riptideColossus = Cards.invent("Riptide Colossus", "{1}{W}{W}{U}{U}", "Artifact Creature — Construct", """
        Double strike
        Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        When this creature enters, for each opponent, put up to one target nonland permanent that player controls into its owner's library third from the top.
    """.trimIndent(), "2/5")
    val sabSunarLuxaIncarnate = Cards.invent("Sab-Sunar, Luxa Incarnate", "{3}{G}{U}", "Legendary Creature — God", """
        Reach, trample, indestructible
        Sab-Sunar can't attack or block unless it has an even number of counters on it. (Zero is even.)
        At the beginning of your first main phase, put a +1/+1 counter on Sab-Sunar. Then if it has an odd number of counters on it, draw two cards.
    """.trimIndent(), "6/6")
    val veteranBeastrunner = Cards.invent("Veteran Beastrunner", "{1}{G}{W}", "Creature — Human Knight", """
        At the beginning of your end step, untap each creature you control.
        {2}{G}{W}: Creatures you control get +1/+1 until end of turn.
    """.trimIndent(), "3/4")
    val voyageHomeward = Cards.invent("Voyage Homeward", "{5}{W}{U}", "Sorcery", """
        Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
        You draw three cards and gain 3 life.
    """.trimIndent())
    // chunk 16
    val aetherhullJacket = Cards.invent("Aetherhull Jacket", "{3}", "Artifact Creature — Thopter", """
        Flying, vigilance
        {2}, {T}, Sacrifice this creature: Destroy another target artifact. Activate only as a sorcery.
    """.trimIndent(), "2/1")
    val marketbackStrider = Cards.invent("Marketback Strider", "{X}{X}", "Artifact Creature — Construct", """
        This creature enters with X +1/+1 counters on it.
        {4}: Put a +1/+1 counter on this creature.
        When this creature dies, draw a card for each +1/+1 counter on it.
    """.trimIndent(), "0/0")
    val monumentToPersistence = Cards.invent("Monument to Persistence", "{3}", "Artifact", """
        Whenever you discard a card, choose one that hasn't been chosen this turn —
        • Draw a card.
        • Create a Treasure token.
        • Each opponent loses 3 life.
    """.trimIndent())
    val radiantLotusBloom = Cards.invent("Radiant Lotus Bloom", "{6}", "Artifact", "{T}, Sacrifice one or more artifacts: Choose a color. Target player adds three mana of the chosen color for each artifact sacrificed this way. (Activate only as an instant.)")
    val scrapCrusher = Cards.invent("Scrap Crusher", "{1}", "Artifact", """
        {3}, {T}, Sacrifice this artifact: It deals 3 damage to target creature.
        {6}, {T}, Sacrifice this artifact: Destroy target creature or Vehicle.
    """.trimIndent())
    val theAetherflare = Cards.invent("The Aetherflare", "{4}", "Legendary Artifact Planeswalker — Equipment", """
        As long as The Aetherflare is attached to a creature, The Aetherflare can't be attacked and has "Whenever equipped creature deals combat damage during your turn, put that many loyalty counters on The Aetherflare."
        [+1]: Attach The Aetherflare to up to one target creature you control. Put a +1/+1 counter on that creature.
        [−5]: Draw two cards.
        [−10]: Add ten mana of any one color.
    """.trimIndent(), null, loyalty = "4")
    val turnstileTortoise = Cards.invent("Turnstile Tortoise", "{2}", "Artifact Creature — Turtle", """
        Defender
        When this creature enters, if an opponent controls more lands than you, you create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """.trimIndent(), "3/1")
    // chunk 17
    val bleachboneBrink = Cards.invent("Bleachbone Brink", null, "Land", """
        {T}: Add {B}.
        {T}: Add {W}. Activate only if you control a Plains or a Swamp.
    """.trimIndent())
    val bloodfellGrotto = Cards.invent("Bloodfell Grotto", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {B} or {R}.
    """.trimIndent())
    val blossomingDunes = Cards.invent("Blossoming Dunes", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {G} or {W}.
    """.trimIndent())
    val dismalBayou = Cards.invent("Dismal Bayou", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {U} or {B}.
    """.trimIndent())
    val jungleHideaway = Cards.invent("Jungle Hideaway", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {B} or {G}.
    """.trimIndent())
    val riverpyreBrink = Cards.invent("Riverpyre Brink", null, "Land", """
        {T}: Add {R}.
        {T}: Add {U}. Activate only if you control an Island or a Mountain.
    """.trimIndent())
    val ruggedUplands = Cards.invent("Rugged Uplands", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {R} or {G}.
    """.trimIndent())
    val scouredWastes = Cards.invent("Scoured Wastes", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {W} or {B}.
    """.trimIndent())
    val sunbillowBrink = Cards.invent("Sunbillow Brink", null, "Land", """
        {T}: Add {W}.
        {T}: Add {R}. Activate only if you control a Mountain or a Plains.
    """.trimIndent())
    val swiftwaterBluffs = Cards.invent("Swiftwater Bluffs", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {U} or {R}.
    """.trimIndent())
    val thornwoodCascade = Cards.invent("Thornwood Cascade", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {G} or {U}.
    """.trimIndent())
    val tranquilInlet = Cards.invent("Tranquil Inlet", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {W} or {U}.
    """.trimIndent())
    val wastewoodBrink = Cards.invent("Wastewood Brink", null, "Land", """
        {T}: Add {G}.
        {T}: Add {B}. Activate only if you control a Swamp or a Forest.
    """.trimIndent())
    val willowrushBrink = Cards.invent("Willowrush Brink", null, "Land", """
        {T}: Add {U}.
        {T}: Add {G}. Activate only if you control a Forest or an Island.
    """.trimIndent())
    val windScarredBluff = Cards.invent("Wind-Scarred Bluff", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {R} or {W}.
    """.trimIndent())
}
