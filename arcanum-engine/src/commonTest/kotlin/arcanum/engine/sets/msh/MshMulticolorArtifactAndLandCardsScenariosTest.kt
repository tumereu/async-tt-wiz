package arcanum.engine.sets.msh

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.msh.Msh.bear
import arcanum.engine.sets.msh.Msh.charm
import arcanum.engine.sets.msh.Msh.doom
import arcanum.engine.sets.msh.Msh.elf
import arcanum.engine.sets.msh.Msh.giant
import arcanum.engine.sets.msh.Msh.heal
import arcanum.engine.sets.msh.Msh.idol
import arcanum.engine.sets.msh.Msh.ogre
import arcanum.engine.sets.msh.Msh.p1p1
import arcanum.engine.sets.msh.Msh.shatter
import arcanum.engine.sets.msh.Msh.shock
import arcanum.engine.sets.msh.Msh.sword
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Marvel Super Heroes' green, multicolored, artifact and land cards in play (parser chunks 15–20). Thundar
 * Allfatherson (double prowess) and Syndicate Synthoids (entering surveil 2) repeat tested templates; Scientist Supreme
 * of the Syndicate is in [MshAbilityCopyCardsScenariosTest]. */
class MshMulticolorArtifactAndLandCardsScenariosTest {
    private val tinySoldiersArmy = Cards.invent("Tiny Soldiers' Army", "{2}{G}", "Creature — Insect", "When this creature enters, create a Food token or a Treasure token. (A Food token is an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\" A Treasure token is an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")", "3/2")
    private val callTheCleanupCrew = Cards.invent("Call the Cleanup Crew", "{1}{G}", "Sorcery", """
        Choose up to two. Return those cards from your graveyard to your hand.
        • Target artifact card.
        • Target creature card.
        • Target enchantment card.
        • Target land card.
    """)
    private val doctorStallionGammaTherapist = Cards.invent("Doctor Stallion, Gamma Therapist", "{4}{G}", "Legendary Creature — Gamma Doctor Hero", """
        If you would put one or more counters on a permanent you control, put that many plus one of each of those kinds of counters on that permanent instead.
        {T}: Add X mana of any one color, where X is Doctor Stallion's power.
    """, "3/6")
    private val jungleCommandoApe = Cards.invent("Jungle Commando Ape", "{1}{G}", "Creature — Ape Soldier Hero", """
        Reach
        Sacrifice this creature: Destroy target noncreature artifact or noncreature enchantment. Activate only as a sorcery.
    """, "2/2")
    private val lynxclawUndyingVigilante = Cards.invent("Lynxclaw, Undying Vigilante", "{G}{G}", "Legendary Creature — Human Hero", """
        Haste
        When Lynxclaw dies, return her to the battlefield under her owner's control with a +1/+1 counter on her. She loses all abilities and gains haste.
    """, "2/2")
    private val championsBanquet = Cards.invent("Champions' Banquet", "{2}{G}", "Enchantment", """
        When this enchantment enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        Whenever you gain life, choose up to that many target creatures you control. Put a +1/+1 counter on each of them.
    """)
    private val shapelingBurgeoningBruiser = Cards.invent("Shapeling, Burgeoning Bruiser", "{2}{G}", "Legendary Creature — Kree Skrull Hero", """
        Vigilance
        Whenever another creature you control enters, if it has greater power or toughness than Shapeling, put a +1/+1 counter on Shapeling.
    """, "2/3")
    private val kazanOfTheWildLand = Cards.invent("Kazan of the Wild Land", "{4}{G}", "Legendary Creature — Human Barbarian Hero", """
        You may look at the top card of your library any time.
        You may play lands from the top of your library.
        When Kazan enters, create Sabre, a legendary 2/2 green Cat creature token with "Landfall — Whenever a land you control enters, put a +1/+1 counter on Sabre."
    """, "3/2")
    private val knightOfMountVargor = Cards.invent("Knight of Mount Vargor", "{1}{G}", "Creature — Cat Knight Villain", """
        Trample
        Whenever you put a +1/+1 counter on another creature, put a +1/+1 counter on this creature. This ability triggers only once each turn.
    """, "2/1")
    private val misterGrendelMonsterWithin = Cards.invent("Mister Grendel, Monster Within", "{2}{G}", "Legendary Creature — Human Villain", """
        At the beginning of your upkeep, choose one —
        • Put a +1/+1 counter on Mister Grendel.
        • Remove a counter from a creature you control. If you do, draw a card.
    """, "2/2")
    private val burrowLordUnderlingMaster = Cards.invent("Burrow Lord, Underling Master", "{2}{G}", "Legendary Creature — Human Villain", """
        You may play lands from your graveyard.
        Landfall — Whenever a land you control enters, create a 1/1 green Minion creature token named Underling with "Whenever this token attacks, you may mill a card."
    """, "1/1")
    private val pummelingPunch = Cards.invent("Pummeling Punch", "{2}{G}", "Instant", """
        This spell costs {2} less to cast if there are two or more creature cards in your graveyard.
        Target creature you control deals damage equal to twice its power to target creature an opponent controls.
    """)
    private val legendarySlugfest = Cards.invent("Legendary Slugfest", "{2}{G}", "Sorcery", """
        Choose one or both —
        • Double target creature's power and toughness until end of turn.
        • Target creature you control fights target creature an opponent controls.
    """)
    private val swiftSalvage = Cards.invent("Swift Salvage", "{G}", "Instant", "Mill two cards. You may put a permanent card from among the milled cards into your hand. You gain 2 life. (To mill two cards, put the top two cards of your library into your graveyard.)")
    private val saurianDinomorpher = Cards.invent("Saurian, Dinomorpher", "{G}", "Legendary Creature — Human Hero", """
        Brontosaurus — {3}: Until end of turn, Saurian becomes a Dinosaur Hero with base power and toughness 3/5 and gains reach and vigilance.
        Tyrannosaurus Rex — {6}: Until end of turn, Saurian becomes a Dinosaur Hero with base power and toughness 6/6 and gains trample.
    """, "1/2")
    private val mendingTechnique = Cards.invent("Mending Technique", "{2}{G}", "Sorcery", "Target player gains 2 life, then searches their library for a basic land card, puts it onto the battlefield tapped, then shuffles. Put a +1/+1 counter on up to one target creature.")
    private val roryJenningsDestinedSidekick = Cards.invent("Rory Jennings, Destined Sidekick", "{G}", "Legendary Creature — Human Advisor", "{3}, {T}: Mill four cards. You may put a Hero or enchantment card from among those cards into your hand. (To mill four cards, put the top four cards of your library into your graveyard.)", "0/3")
    private val mightyStrength = Cards.invent("Mighty Strength", "{4}{G}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +4/+4 and has trample and ward {1}. (Whenever enchanted creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
    """)
    private val theBoulderBennettStone = Cards.invent("The Boulder, Bennett Stone", "{5}{G}", "Legendary Creature — Human Hero", """
        Trample
        Whenever one or more Heroes you control deal damage to a player, put two +1/+1 counters on The Boulder.
    """, "7/7")
    private val pantheressFelineFury = Cards.invent("Pantheress, Feline Fury", "{1}{G}", "Legendary Creature — Cat Human Hero", """
        Flash
        Trample
        Whenever you gain life, put a +1/+1 counter on Pantheress.
    """, "2/1")
    private val trainingRoutine = Cards.invent("Training Routine", "{3}{G}", "Enchantment", """
        Creatures you control with +1/+1 counters on them have trample.
        At the beginning of combat on your turn, put a +1/+1 counter on target creature you control.
    """)
    private val theUnstoppableChipmunkGirl = Cards.invent("The Unstoppable Chipmunk Girl", "{1}{G}{G}{G}", "Legendary Creature — Squirrel Human Hero", """
        Do You Like Squirrels? — Whenever The Unstoppable Chipmunk Girl enters or attacks, create a 1/1 green Squirrel creature token.
        I LOVE Squirrels! — {1}{G}{G}{G}: Create X 1/1 green Squirrel creature tokens, where X is the number of Squirrels you control.
    """, "4/4")
    private val undercoverShapeshifter = Cards.invent("Undercover Shapeshifter", "{1}{G}", "Creature — Skrull Shapeshifter Villain", """
        As long as there are two or more creature cards in your graveyard, this creature gets +2/+2 and is all creature types.
        {T}: Add one mana of any color.
    """, "1/1")
    private val kingdomRoyalGuard = Cards.invent("Kingdom Royal Guard", "{4}{G}", "Creature — Human Soldier Hero", """
        Vigilance
        When this creature enters, put a +1/+1 counter on target creature. If that creature is another Hero, put two +1/+1 counters on it instead.
    """, "4/4")
    private val absorbentBrute = Cards.invent("Absorbent Brute", "{1}{G}{U}", "Legendary Creature — Human Villain", """
        Vigilance
        At the beginning of your first main phase, until your next turn, Absorbent Brute becomes a copy of up to one target artifact, non-Aura enchantment, or land, except his name is Absorbent Brute, he's a legendary 4/4 Human Villain creature in addition to his other types, and he has vigilance.
    """, "4/4")
    private val invadersFromBeyond = Cards.invent("Invaders from Beyond", "{1}{R}{R}{G}", "Enchantment", "At the beginning of combat on your turn, create a 1/1 red Alien creature token with haste and \"This token attacks each combat if able.\" Put a +1/+1 counter on it for each invasion counter on this enchantment, then put an invasion counter on this enchantment.")
    private val antKnightColonyCommander = Cards.invent("Ant Knight, Colony Commander", "{1}{G}{U}", "Legendary Creature — Human Rogue Hero", """
        Whenever Ant Knight attacks, you may pay {1}. When you do, put a +1/+1 counter on target creature.
        Whenever you put a +1/+1 counter on a creature, create a 1/1 green Insect creature token. This ability triggers only once each turn.
    """, "2/2")
    private val martiusGodOfWar = Cards.invent("Martius, God of War", "{1}{B}{R}", "Legendary Creature — God Warrior Villain", """
        Martius attacks each combat if able.
        Whenever an attacking creature you control dies, return that card to its owner's hand.
    """, "4/3")
    private val theAstonishingHivemaster = Cards.invent("The Astonishing Hivemaster", "{G}{U}", "Legendary Creature — Human Scientist Hero", """
        Whenever you draw a card, put a +1/+1 counter on The Astonishing Hivemaster.
        {2}{G}, {T}, Remove any number of +1/+1 counters from The Astonishing Hivemaster: Create that many 1/1 green Insect creature tokens.
    """, "1/1")
    private val bruinEruditeAerialist = Cards.invent("Bruin, Erudite Aerialist", "{3}{G/U}", "Legendary Creature — Mutant Scientist Hero", """
        As long as you've put one or more +1/+1 counters on Bruin this turn, he has flying.
        Whenever Bruin deals combat damage to a player, draw a card.
    """, "3/3")
    private val shadowPantherVanguard = Cards.invent("Shadow Panther, Vanguard", "{2}{G}{W}", "Legendary Creature — Human Warrior Hero", """
        Whenever another nontoken Hero you control enters, choose one —
        • Create a 1/1 white Soldier creature token.
        • Creatures you control get +1/+1 until end of turn.
    """, "4/4")
    private val pinpointDeathDealer = Cards.invent("Pinpoint, Death Dealer", "{2}{B/R}", "Legendary Creature — Human Assassin Villain", """
        When Pinpoint enters, you may sacrifice an artifact or discard a nonland card. When you do, Pinpoint deals 2 damage to any target.
        {3}, {T}, Sacrifice an artifact or discard a nonland card: Pinpoint deals 2 damage to any target.
    """, "2/3")
    private val commanderLibertyLivingLegend = Cards.invent("Commander Liberty, Living Legend", "{1}{W}{U}", "Legendary Creature — Human Soldier Hero", """
        Vigilance
        Whenever a creature you control becomes tapped during your turn, if it's the first time that creature has become tapped this turn, untap it.
    """, "3/4")
    private val veilAndBladeEntwined = Cards.invent("Veil and Blade, Entwined", "{1}{W}{B}", "Legendary Creature — Human Hero", """
        Deathtouch, lifelink
        When Veil and Blade enter, choose target opponent and up to one target creature they control. They reveal their hand. You may exile a nonland card from their hand or the chosen creature until Veil and Blade leave the battlefield.
    """, "2/2")
    private val nightseerManWithoutFear = Cards.invent("Nightseer, Man Without Fear", "{2}{R}{W}", "Legendary Creature — Human Hero", """
        Vigilance, haste
        Radar Sense — You may look at the top card of your library any time.
        Whenever you attack, you may exile the top card of your library. If that card is a Hero card, Nightseer gets +2/+1 until end of turn. You may play that card this turn.
    """, "3/4")
    private val driftshadeSpectralSaboteur = Cards.invent("Driftshade, Spectral Saboteur", "{2}{U/B}", "Legendary Creature — Human Rogue Villain", """
        Flash
        Intangibility — Driftshade can't be blocked.
    """, "2/2")
    private val steelSentinelMasterOfMachines = Cards.invent("Steel Sentinel, Master of Machines", "{2}{U}{R}", "Legendary Artifact Creature — Human Hero", """
        Flying, vigilance
        Steel Sentinel gets +1/+0 for each other artifact you control.
        Whenever Steel Sentinel attacks, if an artifact entered the battlefield under your control this turn, draw a card.
    """, "1/4")
    private val killRaiderScourgeOfTheKingdom = Cards.invent("Kill-Raider, Scourge of the Kingdom", "{2}{B}{G}", "Legendary Creature — Human Mercenary Villain", """
        When Kill-Raider enters, you may sacrifice another creature. When you do, destroy target nonland permanent an opponent controls.
        As long as there are two or more creature cards in your graveyard, Kill-Raider gets +2/+1.
    """, "3/3")
    private val theOverlordOfCrime = Cards.invent("The Overlord of Crime", "{1}{W}{B}", "Legendary Creature — Human Villain", """
        Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
        Whenever you attack, you may pay 2 life. If you do, until end of turn, creatures you control with toughness greater than their power assign combat damage equal to their toughness rather than their power.
    """, "1/5")
    private val theMightyThundererJanaForge = Cards.invent("The Mighty Thunderer, Jana Forge", "{1}{W}{U}", "Legendary Creature — Human God Hero", """
        Flying
        Whenever The Mighty Thunderer attacks, exile up to one target nontoken artifact or creature, then return that card to the battlefield tapped under its owner's control.
        Whenever an Equipment you control enters, draw a card.
    """, "3/3")
    private val skyGirlAndThunderLizard = Cards.invent("Sky Girl and Thunder Lizard", "{1}{G}{U}", "Legendary Creature — Human Dinosaur Hero", """
        Whenever you draw your second card each turn, until end of turn, Sky Girl and Thunder Lizard's base power and toughness become 6/6 and they gain trample.
        Whenever an artifact you control enters, draw a card. This ability triggers only once each turn.
    """, "2/2")
    private val arachnidKidToTheRescue = Cards.invent("Arachnid Kid, To the Rescue", "{2}{G/W}", "Legendary Creature — Spider Human Hero", """
        Flash
        Reach, vigilance
        No One Dies! — When Arachnid Kid enters, you may tap him. When you do, another target nonattacking creature you control gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
    """, "3/2")
    private val copyistMercenaryMimic = Cards.invent("Copyist, Mercenary Mimic", "{2}{U}{B}", "Legendary Creature — Human Mercenary Villain", "Photographic Reflexes — At the beginning of your first main phase, until your next turn, Copyist becomes a copy of up to one target creature on the battlefield or creature card in a graveyard, except his name is Copyist, Mercenary Mimic and he's a legendary Human Mercenary Villain creature.", "3/5")
    private val tetraRuggedRumbler = Cards.invent("Tetra, Rugged Rumbler", "{2}{B/G}", "Legendary Creature — Human Villain", """
        As an additional cost to cast this spell, discard a card or pay {2}.
        Ward—Discard a card or pay {2}. (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player discards a card or pays {2}.)
    """, "5/5")
    private val unagentJackWarden = Cards.invent("U.N.Agent, Jack Warden", "{3}{W/B}", "Legendary Creature — Human Soldier Hero", "When U.N.Agent enters, create a colorless Equipment artifact token named Sturdy Shield with \"Equipped creature gets +1/+2\" and equip {2}. Attach it to U.N.Agent.", "3/2")
    private val syntheticQuest = Cards.invent("Synthetic Quest", "{X}{U}{R}", "Sorcery", "Search your library and/or graveyard for an artifact creature card with mana value X or less and put it onto the battlefield with X additional +1/+1 counters on it. If X is 4 or greater, it gains haste until end of turn. If you search your library this way, shuffle.")
    private val battleEngineLegacyOfSteel = Cards.invent("Battle Engine, Legacy of Steel", "{2}{R/W}", "Legendary Artifact Creature — Human Hero", """
        Flying
        At the beginning of combat on your turn, another target creature you control gets +X/+0 until end of turn, where X is Battle Engine's power.
    """, "1/3")
    private val frostSoldierIcyAssassin = Cards.invent("Frost Soldier, Icy Assassin", "{W}{B}", "Legendary Creature — Human Assassin Villain", """
        Vigilance, menace
        Frost Soldier gets +2/+0 for each Equipment attached to him.
        {3}{W}{B}: Return this card from your graveyard to the battlefield with a finality counter on him. Then you may attach an Equipment you control to him. (If a creature with a finality counter on it would die, exile it instead.)
    """, "2/2")
    private val realmsWithinRealms = Cards.invent("Realms Within Realms", "{5}{G}{U}", "Sorcery", "Exile all creatures. Each player may put any number of creature cards from their hand onto the battlefield. Then put all cards exiled this way into their owners' hands. Exile Realms Within Realms.")
    private val coreReactor = Cards.invent("Core Reactor", "{5}", "Artifact", """
        Improvise (Your artifacts can help cast this spell. Each artifact you tap after you're done activating mana abilities pays for {1}.)
        This artifact enters tapped.
        {T}: Add {C}{C}{C}.
    """)
    private val commanderLibertysShield = Cards.invent("Commander Liberty's Shield", "{2}", "Legendary Artifact — Equipment", """
        Indestructible
        Equipped creature gets +0/+8 and has vigilance.
        Whenever equipped creature attacks, tap target creature defending player controls.
        Equip {2}
    """)
    private val infinitePrism = Cards.invent("Infinite Prism", "{5}", "Artifact", """
        Ward {2}
        Whenever you attack, look at the top six cards of your library. You may cast a spell from among them with mana value less than or equal to the greatest power among attacking creatures you control without paying its mana cost. Put the rest on the bottom of your library in a random order.
    """)
    private val dependableSkyjet = Cards.invent("Dependable Skyjet", "{3}", "Artifact — Vehicle", """
        Flying
        {T}: Add one mana of any color.
        Crew 4 (Tap any number of creatures you control with total power 4 or more: This Vehicle becomes an artifact creature until end of turn.)
    """, "3/3")
    private val roverScoutUnit = Cards.invent("R.O.V.E.R. Scout Unit", "{4}", "Artifact Creature — Robot Scout", """
        Flying
        When this creature enters, draw a card, then you may put a land card from your hand onto the battlefield tapped.
    """, "2/1")
    private val steelSentinelArmor = Cards.invent("Steel Sentinel Armor", "{3}", "Artifact — Equipment", """
        When this Equipment enters, attach it to target creature you control.
        Equipped creature gets +2/+1 and has flying.
        {2}: If this Equipment isn't a creature, it becomes a 0/0 Construct Hero artifact creature with flying and "This creature gets +1/+1 for each artifact you control" until end of turn.
        Equip {2}
    """)
    private val bureauHelicarrier = Cards.invent("Bureau Helicarrier", "{4}", "Artifact — Vehicle", """
        Flying
        When this Vehicle enters, create two 1/1 white Soldier creature tokens.
        Crew 6 (Tap any number of creatures you control with total power 6 or more: This Vehicle becomes an artifact creature until end of turn.)
    """, "4/5")
    private val superMimicoid = Cards.invent("Super-Mimicoid", "{2}", "Legendary Artifact Creature — Robot Villain", """
        Super-Mimicoid's power is equal to the number of legendary creatures you control.
        Whenever Super-Mimicoid enters or attacks, choose another target creature. If that creature has haste and Super-Mimicoid doesn't, put a haste counter on Super-Mimicoid. Do the same for flying, first strike, double strike, deathtouch, indestructible, lifelink, menace, reach, trample, and vigilance.
    """, "*/2")
    private val theTenBracers = Cards.invent("The Ten Bracers", "{8}", "Legendary Artifact", """
        Your maximum hand size is ten.
        At the beginning of your end step, if you have fewer than ten cards in hand, draw cards equal to the difference.
    """)
    private val omnitronArtificialMalevolence = Cards.invent("Omnitron, Artificial Malevolence", "{3}", "Legendary Artifact Creature — Robot Villain", "Whenever another nontoken artifact you control enters, you may pay {2}. If you do, create a token that's a copy of it. If the token isn't a creature, it becomes a 2/2 Robot Villain creature in addition to its other types.", "2/4")
    private val adamantEnergyDaggers = Cards.invent("Adamant Energy Daggers", "{1}", "Artifact — Equipment", """
        Indestructible (Effects that say "destroy" don't destroy this Equipment.)
        Equipped creature gets +2/+2.
        Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val theSimulacrum = Cards.invent("The Simulacrum", "{4}", "Legendary Artifact Creature — Robot Hero", """
        Flying, vigilance
        Whenever you cast a noncreature spell, choose one that hasn't been chosen this turn —
        • Solar Beam — The Simulacrum gains double strike until end of turn.
        • Density Control — The Simulacrum gains indestructible until end of turn.
        • Technopathy — Draw a card.
    """, "2/5")
    private val syndicateLabs = Cards.invent("Syndicate Labs", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {U} or {B}.
    """)
    private val grimStronghold = Cards.invent("Grim Stronghold", null, "Land", """
        {T}: Add {C}.
        {T}: Add {B} or {R}. Activate only if this land entered this turn or if you control a basic land.
    """)
    private val guardiansTower = Cards.invent("Guardians Tower", null, "Land", """
        {T}: Add {C}.
        {T}: Add one mana of any color. Spend this mana only to cast a Hero spell or to activate an ability of a Hero source.
        {4}, {T}: Look at the top three cards of your library. You may reveal a Hero card from among them and put it into your hand. Put the rest on the bottom of your library in any order.
    """)
    private val foursquareBuilding = Cards.invent("Foursquare Building", null, "Land", """
        {T}: Add {C}.
        {4}, {T}: Add four mana in any combination of colors.
        {4}, {T}: Draw a card. Activate only if you control a creature with toughness 4 or greater.
    """)
    private val castleDread = Cards.invent("Castle Dread", null, "Land", """
        {T}: Add {C}.
        {T}: Add one mana of any color. Spend this mana only to cast an artifact spell.
        {3}, {T}, Sacrifice an artifact: Create a 3/3 colorless Robot Villain artifact creature token named Dreadbot. Activate only as a sorcery.
    """)
    private val monitoringRoom = Cards.invent("Monitoring Room", null, "Land", """
        When this land enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        {T}: Add {C}.
        {1}, {T}: Add one mana of any color.
    """)
    private val nefariousHideout = Cards.invent("Nefarious Hideout", null, "Land", """
        {T}: Add {C}.
        {T}: Add one mana of any color. Spend this mana only to cast a Villain spell or to activate an ability of a Villain source.
        {3}, {T}: Target Villain you control connives. Activate only as a sorcery. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)
    """)

    private val hero = Cards.invent("Avenue Hero", "{1}{W}", "Creature — Human Hero", "", "2/2")
    private val villain = Cards.invent("Avenue Thug", "{1}{B}", "Creature — Human Villain", "", "2/2")
    private val growth = Cards.invent("Avenue Growth", "{G}", "Instant", "Put a +1/+1 counter on target creature.")
    private val letters = Cards.invent("Avenue Letters", "{U}", "Sorcery", "Draw two cards.")
    private val drone = Cards.invent("Avenue Drone", "{3}", "Artifact Creature — Robot", "", "2/2")
    private val paladin = Cards.invent("Avenue Paladin", "{2}{W}", "Creature — Human Knight", "Flying, lifelink", "2/2")
    private val gadget = Cards.invent("Avenue Gadget", "{1}", "Artifact", "")

    // ---- Chunks 15–16: green

    @Test
    fun tinySoldiersArmyBringsATreasure() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(tinySoldiersArmy)
        alice.casts(tinySoldiersArmy)
        resolveAll()
        option("Treasure")
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
    }

    @Test
    fun callTheCleanupCrewReturnsTwoKinds() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(callTheCleanupCrew)
        alice.graveyard(idol, bear)
        alice.casts(callTheCleanupCrew, modes = listOf(0, 1), targets = listOf(idol, bear))
        resolveAll()
        expect {
            alice.hand has idol
            alice.hand has bear
        }
    }

    // CR 614.1a: one more counter of each kind
    @Test
    fun doctorStallionAddsACounterAndMakesMana() = scenario {
        alice.battlefield(doctorStallionGammaTherapist, bear, "Forest")
        alice.hand(growth)
        alice.casts(growth, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 2 }
        alice.activates(doctorStallionGammaTherapist, color = Color.Green)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        expect { alice.pool.size shouldBe 3 }
    }

    @Test
    fun jungleCommandoApeDestroysAnEnchantment() = scenario {
        alice.battlefield(jungleCommandoApe)
        bob.battlefield(charm)
        alice.activates(jungleCommandoApe, target = charm)
        resolveAll()
        expect { bob.graveyard has charm }
    }

    @Test
    fun lynxclawComesBackPlainButHasty() = scenario {
        alice.battlefield(lynxclawUndyingVigilante, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = lynxclawUndyingVigilante)
        resolveAll()
        expect {
            lynxclawUndyingVigilante.counters(p1p1) shouldBe 1
            lynxclawUndyingVigilante hasKeyword Keyword.Haste
            alice.battlefield has lynxclawUndyingVigilante
        }
    }

    @Test
    fun championsBanquetFeedsAndCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Plains", bear, elf)
        alice.hand(championsBanquet, heal)
        alice.casts(championsBanquet, paying = listOf("Forest", "Forest", "Forest"))
        resolveAll()
        expect { alice.battlefield has "Food Token" }
        alice.casts(heal)
        resolveAll()
        pickTargets(bear, elf)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            elf.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun shapelingGrowsForBiggerArrivals() = scenario {
        alice.battlefield(shapelingBurgeoningBruiser, "Mountain", "Mountain", "Mountain")
        alice.hand(ogre)
        alice.casts(ogre)
        resolveAll()
        expect { shapelingBurgeoningBruiser.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun kazanPlaysLandsFromTheTopAndRaisesSabre() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(kazanOfTheWildLand)
        alice.library("Island", bear)
        alice.casts(kazanOfTheWildLand)
        resolveAll()
        alice.plays("Island")
        resolveAll()
        expect {
            alice.battlefield has "Island"
            "Sabre".counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun knightOfMountVargorGrowsAlongOnce() = scenario {
        alice.battlefield(knightOfMountVargor, bear, "Forest", "Forest")
        alice.hand(growth, growth)
        alice.casts(growth, target = bear)
        resolveAll()
        alice.casts(growth, target = bear)
        resolveAll()
        expect { knightOfMountVargor.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun misterGrendelGrowsEachUpkeep() = scenario {
        alice.battlefield(misterGrendelMonsterWithin)
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        modes(0)
        resolveAll()
        expect { misterGrendelMonsterWithin.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun misterGrendelTradesACounterForACard() = scenario {
        alice.battlefield(misterGrendelMonsterWithin, counters = listOf(p1p1 to 1))
        alice.library(bear, ogre)
        bob.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        modes(1)
        resolveAll()
        chooseIfOffered(misterGrendelMonsterWithin)
        resolveAll()
        if (pending is Decision.ChooseOption) option("+1/+1")
        resolveAll()
        expect {
            misterGrendelMonsterWithin.counters(p1p1) shouldBe 0
            alice.hand has bear
        }
    }

    @Test
    fun burrowLordReplaysLandsAndMakesUnderlings() = scenario {
        alice.battlefield(burrowLordUnderlingMaster)
        alice.graveyard("Forest")
        alice.plays("Forest")
        resolveAll()
        expect {
            alice.battlefield has "Forest"
            alice.battlefield has "Underling"
        }
    }

    @Test
    fun pummelingPunchCheapAndDouble() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(pummelingPunch)
        alice.graveyard(elf, elf)
        bob.battlefield(ogre)
        alice.casts(pummelingPunch, targets = listOf(bear, ogre))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun legendarySlugfestDoublesThenFights() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.hand(legendarySlugfest)
        bob.battlefield(ogre)
        alice.casts(legendarySlugfest, modes = listOf(0, 1), targets = listOf(bear, bear, ogre))
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.battlefield has bear
            bear.power shouldBe 4
        }
    }

    @Test
    fun swiftSalvageKeepsAPermanentCard() = scenario {
        alice.battlefield("Forest")
        alice.hand(swiftSalvage)
        alice.library(shock, ogre)
        alice.casts(swiftSalvage)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(ogre)
        resolveAll()
        expect {
            alice.hand has ogre
            alice.life shouldBe 22
        }
    }

    @Test
    fun saurianBecomesARex() = scenario {
        alice.battlefield(saurianDinomorpher, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(saurianDinomorpher, ability = 1)
        resolveAll()
        expect {
            saurianDinomorpher.power shouldBe 6
            saurianDinomorpher hasKeyword Keyword.Trample
            saurianDinomorpher.subtypes.map { it.name }.contains("Dinosaur") shouldBe true
        }
    }

    @Test
    fun mendingTechniqueRampsHealsAndCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.hand(mendingTechnique)
        alice.library(ogre, "Island")
        alice.casts(mendingTechnique, targets = listOf("alice", bear))
        resolveAll()
        chooseIfOffered("Island")
        resolveAll()
        expect {
            alice.life shouldBe 22
            "Island".tapped shouldBe true
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun roryJenningsFindsAHero() = scenario {
        alice.battlefield(roryJenningsDestinedSidekick, "Forest", "Forest", "Forest")
        alice.library(bear, hero, ogre, elf)
        alice.activates(roryJenningsDestinedSidekick)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(hero)
        resolveAll()
        expect { alice.hand has hero }
    }

    @Test
    fun mightyStrengthEmpowers() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", bear)
        alice.hand(mightyStrength)
        alice.casts(mightyStrength, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 6
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun theBoulderGrowsWhenHeroesHit() = scenario {
        alice.battlefield(theBoulderBennettStone, hero)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hero)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { theBoulderBennettStone.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun pantheressGrowsOnLifeGain() = scenario {
        alice.battlefield(pantheressFelineFury, "Plains")
        alice.hand(heal)
        alice.casts(heal)
        resolveAll()
        expect { pantheressFelineFury.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun trainingRoutineCountersAndTramples() = scenario {
        alice.battlefield(trainingRoutine, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun theUnstoppableChipmunkGirlMultipliesSquirrels() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(theUnstoppableChipmunkGirl)
        alice.casts(theUnstoppableChipmunkGirl)
        resolveAll()
        alice.activates(theUnstoppableChipmunkGirl)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 3 }
    }

    @Test
    fun undercoverShapeshifterGrowsAtTwoCreatureCards() = scenario {
        alice.battlefield(undercoverShapeshifter)
        alice.graveyard(bear, ogre)
        expect {
            undercoverShapeshifter.power shouldBe 3
            card(undercoverShapeshifter).view.characteristics!!.allCreatureTypes shouldBe true
        }
    }

    @Test
    fun kingdomRoyalGuardDoublesForHeroes() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", hero)
        alice.hand(kingdomRoyalGuard)
        alice.casts(kingdomRoyalGuard)
        resolveAll()
        targetsIfAsked(hero)
        resolveAll()
        expect { hero.counters(p1p1) shouldBe 2 }
    }

    // ---- Chunks 17–18: multicolored

    // CR 707.9b: a copy with exceptions
    @Test
    fun absorbentBruteCopiesAnArtifact() = scenario {
        alice.battlefield(absorbentBrute, idol)
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        targetsIfAsked(idol)
        resolveAll()
        expect {
            absorbentBrute.types.contains(CardType.Artifact) shouldBe true
            absorbentBrute.power shouldBe 4
        }
    }

    @Test
    fun invadersFromBeyondGrowingAliens() = scenario {
        alice.battlefield(invadersFromBeyond)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            "Alien Token" hasKeyword Keyword.Haste
            invadersFromBeyond.counters(CounterKind.Named("invasion")) shouldBe 1
        }
    }

    @Test
    fun antKnightCountersAndBreedsInsects() = scenario {
        alice.battlefield(antKnightColonyCommander, bear, "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(antKnightColonyCommander)
        resolveAll()
        may(true)
        payIfAsked()
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            alice.battlefield has "Insect Token"
        }
    }

    @Test
    fun martiusReturnsFallenAttackers() = scenario {
        alice.battlefield(martiusGodOfWar, bear)
        bob.battlefield(giant)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(bear) }
        alice.attacks(martiusGodOfWar, bear)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(giant to bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun theAstonishingHivemasterGrowsAndSwarms() = scenario {
        alice.battlefield(theAstonishingHivemaster, "Island", "Forest", "Forest", "Forest")
        alice.hand(letters)
        alice.library(bear, bear)
        alice.casts(letters, paying = listOf("Island"))
        resolveAll()
        expect { theAstonishingHivemaster.counters(p1p1) shouldBe 2 }
        alice.activates(theAstonishingHivemaster, x = 2)
        resolveAll()
        if (pending is Decision.ChooseNumber) answer(Answer.Number(2))
        resolveAll()
        expect { alice.battlefield.names.count { it == "Insect Token" } shouldBe 2 }
    }

    @Test
    fun bruinFliesAfterACounter() = scenario {
        alice.battlefield(bruinEruditeAerialist, "Forest")
        alice.hand(growth)
        expect { bruinEruditeAerialist lacksKeyword Keyword.Flying }
        alice.casts(growth, target = bruinEruditeAerialist)
        resolveAll()
        expect { bruinEruditeAerialist hasKeyword Keyword.Flying }
    }

    @Test
    fun shadowPantherVanguardRalliesForHeroes() = scenario {
        alice.battlefield(shadowPantherVanguard, bear, "Plains", "Plains")
        alice.hand(hero)
        alice.casts(hero)
        resolveAll()
        modes(1)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun pinpointShootsForADiscard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(pinpointDeathDealer, ogre)
        alice.casts(pinpointDeathDealer)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseOption) option("Discard")
        chooseIfOffered(ogre)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    // CR 603.2: the first time it became tapped this turn
    @Test
    fun commanderLibertyLivingLegendUntapsAttackers() = scenario {
        alice.battlefield(commanderLibertyLivingLegend, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect { bear.tapped shouldBe false }
    }

    @Test
    fun veilAndBladeExilesFromTheHand() = scenario {
        alice.battlefield("Plains", "Swamp", "Swamp")
        alice.hand(veilAndBladeEntwined)
        bob.hand(ogre, "Forest")
        alice.casts(veilAndBladeEntwined)
        resolveAll()
        pickTargets("bob")
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(ogre)
        resolveAll()
        expect { bob.exile has ogre }
    }

    @Test
    fun nightseerFindsAHero() = scenario {
        alice.battlefield(nightseerManWithoutFear, "Plains", "Plains")
        alice.library(hero)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(nightseerManWithoutFear)
        resolveAll()
        may(true)
        resolveAll()
        expect { nightseerManWithoutFear.power shouldBe 5 }
    }

    @Test
    fun driftshadeCantBeBlocked() = scenario {
        alice.battlefield(driftshadeSpectralSaboteur)
        bob.battlefield(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(driftshadeSpectralSaboteur)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to driftshadeSpectralSaboteur) }
    }

    @Test
    fun steelSentinelMasterCountsArtifactsAndDraws() = scenario {
        alice.battlefield(steelSentinelMasterOfMachines, idol, "Plains")
        alice.hand(gadget)
        alice.library(bear)
        alice.casts(gadget)
        resolveAll()
        expect { steelSentinelMasterOfMachines.power shouldBe 3 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(steelSentinelMasterOfMachines)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun killRaiderTradesForAPermanent() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Forest", elf)
        alice.hand(killRaiderScourgeOfTheKingdom)
        alice.graveyard(bear)
        bob.battlefield(idol)
        alice.casts(killRaiderScourgeOfTheKingdom)
        resolveAll()
        may(true)
        chooseIfOffered(elf)
        resolveAll()
        targetsIfAsked(idol)
        resolveAll()
        expect {
            bob.graveyard has idol
            killRaiderScourgeOfTheKingdom.power shouldBe 5
        }
    }

    @Test
    fun theOverlordOfCrimeHitsByToughness() = scenario {
        alice.battlefield(theOverlordOfCrime)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(theOverlordOfCrime)
        resolveAll()
        may(true)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            alice.life shouldBe 18
            bob.life shouldBe 15
        }
    }

    @Test
    fun theMightyThundererBlinksAndDrawsForEquipment() = scenario {
        alice.battlefield(theMightyThundererJanaForge, "Plains")
        alice.hand(sword)
        alice.library(bear)
        bob.battlefield(ogre)
        alice.casts(sword)
        resolveAll()
        expect { alice.hand has bear }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(theMightyThundererJanaForge)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { ogre.tapped shouldBe true }
    }

    @Test
    fun skyGirlGrowsOnTheSecondDraw() = scenario {
        alice.battlefield(skyGirlAndThunderLizard, "Island")
        alice.hand(letters)
        alice.library(bear, bear)
        alice.casts(letters)
        resolveAll()
        expect {
            skyGirlAndThunderLizard.power shouldBe 6
            skyGirlAndThunderLizard hasKeyword Keyword.Trample
        }
    }

    @Test
    fun arachnidKidProtectsAnother() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.hand(arachnidKidToTheRescue)
        alice.casts(arachnidKidToTheRescue)
        resolveAll()
        may(true)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear hasKeyword Keyword.Indestructible
            arachnidKidToTheRescue.tapped shouldBe true
        }
    }

    @Test
    fun copyistBecomesACreature() = scenario {
        alice.battlefield(copyistMercenaryMimic)
        alice.library(bear, bear)
        bob.battlefield(giant)
        bob.library(bear, bear)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        targetsIfAsked(giant)
        resolveAll()
        expect { copyistMercenaryMimic.power shouldBe 5 }
    }

    @Test
    fun tetraPaysTwoMore() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(tetraRuggedRumbler)
        alice.casts(tetraRuggedRumbler, option = "Pay {2}")
        resolveAll()
        expect { alice.battlefield has tetraRuggedRumbler }
    }

    @Test
    fun unagentArmsHimself() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(unagentJackWarden)
        alice.casts(unagentJackWarden)
        resolveAll()
        expect {
            alice.battlefield has "Sturdy Shield"
            unagentJackWarden.toughness shouldBe 4
        }
    }

    @Test
    fun syntheticQuestFindsAnArtifactCreature() = scenario {
        alice.battlefield("Island", "Island", "Mountain", "Mountain", "Mountain")
        alice.hand(syntheticQuest)
        alice.library(bear, drone)
        alice.casts(syntheticQuest, x = 3)
        resolveAll()
        // "and/or": the searcher chooses the zones (CR 701.23a)
        answer(Answer.Option("library and graveyard"))
        chooseIfOffered(drone)
        resolveAll()
        expect { drone.counters(p1p1) shouldBe 3 }
    }

    @Test
    fun battleEngineLendsItsPower() = scenario {
        alice.battlefield(battleEngineLegacyOfSteel, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun frostSoldierReturnsArmed() = scenario {
        alice.battlefield(sword, "Plains", "Plains", "Swamp", "Swamp", "Swamp")
        alice.graveyard(frostSoldierIcyAssassin)
        alice.activates(frostSoldierIcyAssassin)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(sword)
        resolveAll()
        expect {
            frostSoldierIcyAssassin.counters(CounterKind.Finality) shouldBe 1
            frostSoldierIcyAssassin.power shouldBe 5
        }
    }

    @Test
    fun realmsWithinRealmsResetsTheBoard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Island", "Island", bear)
        alice.hand(realmsWithinRealms, giant)
        bob.battlefield(ogre)
        alice.casts(realmsWithinRealms)
        resolveAll()
        repeat(2) {
            (pending as? Decision.ChooseObjects)?.let { d -> answer(Answer.Objects(if (d.player == alice.id) d.options.filter { state.obj(it)?.let { o -> nameOf(o) } == giant } else emptyList())) }
            resolveAll()
        }
        expect {
            alice.battlefield has giant
            alice.hand has bear
            bob.hand has ogre
            alice.exile has realmsWithinRealms
        }
    }

    // ---- Chunk 19: artifacts

    @Test
    fun coreReactorMakesThreeColorless() = scenario {
        alice.battlefield(coreReactor)
        alice.activates(coreReactor)
        expect { alice.pool.size shouldBe 3 }
    }

    @Test
    fun coreReactorEntersTapped() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(coreReactor)
        alice.casts(coreReactor)
        resolveAll()
        expect { coreReactor.tapped shouldBe true }
    }

    @Test
    fun commanderLibertysShieldTapsABlocker() = scenario {
        alice.battlefield(commanderLibertysShield, bear, "Plains", "Plains")
        bob.battlefield(ogre)
        alice.activates(commanderLibertysShield, target = bear)
        resolveAll()
        expect { bear.toughness shouldBe 10 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            bear.tapped shouldBe false
        }
    }

    @Test
    fun infinitePrismCastsAFreeSpell() = scenario {
        alice.battlefield(infinitePrism, ogre)
        alice.library(bear, shock, elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ogre)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(shock)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun dependableSkyjetTapsForMana() = scenario {
        alice.battlefield(dependableSkyjet)
        alice.activates(dependableSkyjet, color = Color.Blue)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Blue))
        expect { alice.pool.size shouldBe 1 }
    }

    @Test
    fun roverScoutUnitDrawsAndDropsALand() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(roverScoutUnit, "Island")
        alice.library(bear)
        alice.casts(roverScoutUnit)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered("Island")
        resolveAll()
        expect {
            alice.hand has bear
            "Island".tapped shouldBe true
        }
    }

    @Test
    fun steelSentinelArmorSuitsUpAndStandsAlone() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", bear, idol)
        alice.hand(steelSentinelArmor)
        alice.casts(steelSentinelArmor, paying = listOf("Plains", "Plains", "Plains"))
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Flying
        }
    }

    @Test
    fun steelSentinelArmorBecomesACreature() = scenario {
        alice.battlefield(steelSentinelArmor, idol, "Plains", "Plains")
        alice.activates(steelSentinelArmor)
        resolveAll()
        expect { steelSentinelArmor.power shouldBe 2 }
    }

    @Test
    fun bureauHelicarrierBringsTwoSoldiers() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(bureauHelicarrier)
        alice.casts(bureauHelicarrier)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Soldier Token" } shouldBe 2 }
    }

    @Test
    fun superMimicoidCopiesKeywords() = scenario {
        alice.battlefield("Plains", "Plains", paladin)
        alice.hand(superMimicoid)
        alice.casts(superMimicoid)
        resolveAll()
        targetsIfAsked(paladin)
        resolveAll()
        expect {
            superMimicoid hasKeyword Keyword.Flying
            superMimicoid hasKeyword Keyword.Lifelink
            superMimicoid.power shouldBe 1
        }
    }

    @Test
    fun theTenBracersFillTheHand() = scenario {
        alice.battlefield(theTenBracers)
        alice.hand(bear, bear, bear, bear, bear, bear, bear)
        alice.library(elf, elf, elf, elf)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.hand.size shouldBe 10 }
    }

    @Test
    fun omnitronCopiesArtifactsAsRobots() = scenario {
        alice.battlefield(omnitronArtificialMalevolence, "Plains", "Plains", "Plains")
        alice.hand(gadget)
        alice.casts(gadget, paying = listOf("Plains"))
        resolveAll()
        may(true)
        payIfAsked()
        resolveAll()
        expect { alice.battlefield.names.count { it == gadget } shouldBe 2 }
    }

    @Test
    fun adamantEnergyDaggersSurviveDestruction() = scenario {
        alice.battlefield(adamantEnergyDaggers, bear, "Plains", "Plains", "Plains")
        bob.battlefield("Mountain")
        bob.hand(shatter)
        alice.activates(adamantEnergyDaggers, target = bear)
        resolveAll()
        expect { bear.power shouldBe 4 }
        alice.passes()
        bob.casts(shatter, target = adamantEnergyDaggers)
        resolveAll()
        expect { alice.battlefield has adamantEnergyDaggers }
    }

    @Test
    fun theSimulacrumChoosesDifferentModes() = scenario {
        alice.battlefield(theSimulacrum, "Mountain", "Mountain")
        alice.hand(shock, shock)
        alice.library(bear)
        alice.casts(shock, target = "bob")
        resolveAll()
        modes(2)
        resolveAll()
        alice.casts(shock, target = "bob")
        resolveAll()
        modes(0)
        resolveAll()
        expect {
            alice.hand has bear
            theSimulacrum hasKeyword Keyword.DoubleStrike
        }
    }

    // ---- Chunk 20: lands

    @Test
    fun syndicateLabsGainsOnEntering() = scenario {
        alice.hand(syndicateLabs)
        alice.plays(syndicateLabs)
        resolveAll()
        expect {
            syndicateLabs.tapped shouldBe true
            alice.life shouldBe 21
        }
    }

    @Test
    fun grimStrongholdNeedsABasic() = scenario {
        alice.battlefield(grimStronghold)
        assertFailsWith<IllegalArgumentException> { alice.activates(grimStronghold, ability = 1, color = Color.Black) }
    }

    @Test
    fun grimStrongholdColoredWithABasic() = scenario {
        alice.battlefield(grimStronghold, "Plains")
        alice.activates(grimStronghold, ability = 1, color = Color.Black)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Black))
        expect { alice.pool.size shouldBe 1 }
    }

    @Test
    fun guardiansTowerFindsAHero() = scenario {
        alice.battlefield(guardiansTower, "Plains", "Plains", "Plains", "Plains")
        alice.library(bear, hero, ogre)
        alice.activates(guardiansTower, ability = 2)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(hero)
        resolveAll()
        expect { alice.hand has hero }
    }

    @Test
    fun foursquareBuildingDrawsWithAToughCreature() = scenario {
        alice.battlefield(foursquareBuilding, giant, "Plains", "Plains", "Plains", "Plains")
        alice.library(bear)
        alice.activates(foursquareBuilding, ability = 2)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun castleDreadBuildsADreadbot() = scenario {
        alice.battlefield(castleDread, idol, "Plains", "Plains", "Plains")
        alice.activates(castleDread, ability = 2, choosing = listOf(idol))
        resolveAll()
        expect { "Dreadbot".power shouldBe 3 }
    }

    @Test
    fun monitoringRoomSurveilsOnEntering() = scenario {
        alice.hand(monitoringRoom)
        alice.library(bear)
        alice.plays(monitoringRoom)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        expect { alice.graveyard has bear }
    }

    @Test
    fun nefariousHideoutMakesAVillainConnive() = scenario {
        alice.battlefield(nefariousHideout, villain, "Swamp", "Swamp", "Swamp")
        alice.hand(ogre)
        alice.library(elf)
        alice.activates(nefariousHideout, ability = 2, target = villain)
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect { villain.counters(p1p1) shouldBe 1 }
    }
}
