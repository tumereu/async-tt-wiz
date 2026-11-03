package arcanum.engine.sets.msh

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.msh.Msh.bear
import arcanum.engine.sets.msh.Msh.doom
import arcanum.engine.sets.msh.Msh.elf
import arcanum.engine.sets.msh.Msh.giant
import arcanum.engine.sets.msh.Msh.idol
import arcanum.engine.sets.msh.Msh.ogre
import arcanum.engine.sets.msh.Msh.p1p1
import arcanum.engine.sets.msh.Msh.shock
import arcanum.engine.sets.msh.Msh.sphinx
import arcanum.engine.sets.msh.Msh.sword
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Marvel Super Heroes' white, blue, black and red cards in play (parser chunks 08–14). Royal Drone Flock (entering
 * scry 2), Webbed In (exile until this leaves), Tidal Lancers (a counter on the second draw), Hour of Downfall, Project
 * Revenant Soldier, Fiery Crescendo and Scarlet Operative repeat templates tested in this set or the earlier ones. */
class MshColorCardsScenariosTest {
    private val commanderLibertyWingsOfValor = Cards.invent("Commander Liberty, Wings of Valor", "{2}{W}", "Legendary Creature — Human Soldier Hero", """
        Flying, first strike, ward {1}
        Whenever Commander Liberty attacks, each other Hero you control gets +X/+X until end of turn, where X is Commander Liberty's toughness.
    """, "3/1")
    private val starwardenSpaceBorn = Cards.invent("Starwarden, Space-Born", "{4}{W}", "Legendary Creature — Kree Soldier Hero", """
        Flying, vigilance
        Cosmic Awareness — As long as an opponent has cast a spell this turn, you may cast spells as though they had flash.
    """, "4/4")
    private val heroInTheMaking = Cards.invent("Hero in the Making", "{2}{W}", "Creature — Human Hero", "When this creature enters, draw a card. If you control another Hero, you gain 2 life.", "2/2")
    private val unseenMatriarch = Cards.invent("Unseen Matriarch", "{4}{W}", "Legendary Creature — Human Hero", """
        Lifelink
        Whenever you put one or more +1/+1 counters on one or more other Heroes you control, you may create a 0/4 colorless Wall creature token with defender.
    """, "2/5")
    private val nightMedicHealerOfHeroes = Cards.invent("Night Medic, Healer of Heroes", "{1}{W}", "Legendary Creature — Human Doctor Hero", """
        Flash
        Lifelink
        When Night Medic enters, choose target permanent card in your graveyard that was put there from anywhere this turn. Return it to your hand.
    """, "2/1")
    private val ayokaRoyalGuardCaptain = Cards.invent("Ayoka, Royal Guard Captain", "{3}{W}", "Legendary Creature — Human Warrior Hero", """
        When Ayoka enters, create two 1/1 white Soldier creature tokens.
        Attacking creature tokens you control have first strike.
    """, "3/2")
    private val pantherLeap = Cards.invent("Panther Leap", "{W}", "Instant", "Target player investigates. Target creature gets +1/+0 and gains flying until end of turn. Untap it. (To investigate, create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")")
    private val youngPatriotShieldBearer = Cards.invent("Young Patriot, Shield Bearer", "{1}{W}", "Legendary Creature — Human Hero", "{2}, {T}: Another target creature you control gets +2/+0 and gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)", "2/2")
    private val tremorAgentOfTheBureau = Cards.invent("Tremor, Agent of the Bureau", "{2}{W}", "Legendary Creature — Inhuman Spy Hero", "Seismic Takedown — Whenever you cast a noncreature spell, tap target creature or land.", "3/3")
    private val vaultSecurityOfficer = Cards.invent("Vault Security Officer", "{1}{W}", "Creature — Human Soldier", "{2}, {T}: Tap target creature. This ability costs {1} less to activate if it targets a creature with power 3 or less.", "1/3")
    private val crimsonSentinelSuperSoldier = Cards.invent("Crimson Sentinel, Super-Soldier", "{2}{W}", "Legendary Creature — Human Soldier Villain", """
        Flash
        When Crimson Sentinel enters, destroy target creature an opponent controls that dealt damage this turn.
    """, "2/2")
    private val theAurumGoldenGuardian = Cards.invent("The Aurum, Golden Guardian", "{3}{W}", "Legendary Creature — Human Hero", """
        Flying, vigilance, indestructible
        When The Aurum enters, target opponent creates The Abyss, a legendary 5/5 black Horror Villain creature token with flying, indestructible, and "The Abyss attacks each combat if able."
    """, "5/5")
    private val superVillainHoldingCell = Cards.invent("Super Villain Holding Cell", "{1}{W}", "Enchantment", """
        Flash
        When this enchantment enters, exile target tapped creature an opponent controls until this enchantment leaves the battlefield.
    """)
    private val superSoldierTonic = Cards.invent("Super-Soldier Tonic", "{1}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2, has first strike and vigilance, and is a legendary Soldier in addition to its other types.
        Whenever enchanted creature attacks or blocks, attach any number of target Equipment you control to it.
    """)
    private val raiseTheShield = Cards.invent("Raise the Shield", "{1}{W}", "Instant", "Put a +1/+1 counter on target creature. It gains lifelink and indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)")
    private val paleWidowFreeAgent = Cards.invent("Pale Widow, Free Agent", "{3}{W}", "Legendary Creature — Human Hero Villain", """
        When Pale Widow enters, choose one —
        • Put a +1/+1 counter on each of up to two target creatures.
        • Return target artifact or enchantment card from your graveyard to your hand.
    """, "2/3")
    private val krakosTidalWarlord = Cards.invent("Krakos, Tidal Warlord", "{2}{U}{U}", "Legendary Creature — Merfolk Warrior Villain", """
        Other Merfolk you control get +1/+1.
        Whenever one or more Merfolk you control attack a player, draw a card.
    """, "3/4")
    private val sapTheSurge = Cards.invent("Sap the Surge", "{2}{U}", "Instant", """
        This spell costs {2} less to cast if it targets an attacking creature.
        Target creature gets -4/-0 until end of turn.
        Draw a card.
    """)
    private val kestrelWingedWonder = Cards.invent("Kestrel, Winged Wonder", "{4}{U}", "Legendary Creature — Human Hero", """
        Flying
        Avian Telepathy — When Kestrel enters, create Scarlet Wing, a legendary 1/1 blue Bird Scout creature token with flying and "Whenever Scarlet Wing attacks, surveil 1." (Look at the top card of your library. You may put it into your graveyard.)
    """, "3/4")
    private val kestrelsWingHarness = Cards.invent("Kestrel's Wing Harness", "{1}{U}", "Artifact — Equipment", """
        When this Equipment enters, attach it to target creature you control.
        Equipped creature gets +1/+1 and has flying and ward {1}. (Whenever equipped creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
        Equip {2}{U} ({2}{U}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val encasedInFrost = Cards.invent("Encased in Frost", "{2}{U}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, tap enchanted creature.
        Enchanted creature loses all abilities and can't become untapped.
    """)
    private val futuristFoundry = Cards.invent("Futurist Foundry", "{1}{U}", "Artifact", """
        When this artifact enters, draw a card.
        {3}{U}, Sacrifice this artifact: Draw two cards.
    """)
    private val giantSizedFlyingBeetle = Cards.invent("Giant-Sized Flying Beetle", "{3}{U}", "Creature — Insect", """
        Flash
        Flying
        When this creature enters, choose one —
        • Tap target nonland permanent.
        • Untap target nonland permanent.
    """, "3/2")
    private val hydraulicAssistant = Cards.invent("Hydraulic Assistant", "{1}{U}", "Artifact Creature — Robot", """
        Defender
        {T}: Add {U}. This mana can't be spent to cast a nonartifact spell.
    """, "2/3")
    private val iAmTheArmor = Cards.invent("I Am the Armor", "{2}{U}", "Instant", """
        Until end of turn, target artifact or creature becomes an artifact creature with base power and toughness 4/4 and gains flying.
        Draw a card.
    """)
    private val steelLadDivergingDestiny = Cards.invent("Steel Lad, Diverging Destiny", "{2}{U}", "Legendary Artifact Creature — Human Hero", """
        Flying, vigilance
        You may look at the top card of your library any time.
        {T}: Reveal the top card of your library. If it's an artifact card, draw a card.
    """, "2/2")
    private val forgeheartCleverChampion = Cards.invent("Forgeheart, Clever Champion", "{4}{U}", "Legendary Artifact Creature — Human Hero", """
        Improvise (Your artifacts can help cast this spell. Each artifact you tap after you're done activating mana abilities pays for {1}.)
        Flying
        Noncreature spells you cast have improvise.
    """, "3/4")
    private val verdictTelekineticKnight = Cards.invent("Verdict, Telekinetic Knight", "{2}{U}", "Legendary Creature — Mutant Hero", """
        Flying
        When Verdict enters, return up to one target nonland, nontoken permanent to its owner's hand.
        Whenever another nonland permanent you control is returned to its owner's hand, put a +1/+1 counter on Verdict.
    """, "2/2")
    private val youngTrickster = Cards.invent("Young Trickster", "{U}", "Legendary Creature — God Hero Villain", """
        Each creature you control that you've put one or more +1/+1 counters on this turn has hexproof.
        Whenever you draw your second card each turn, put a +1/+1 counter on Young Trickster.
    """, "1/1")
    private val doctorElasticRubberGenius = Cards.invent("Doctor Elastic, Rubber Genius", "{3}{U}", "Legendary Creature — Human Scientist Hero", """
        Reach
        Whenever one or more tokens you control enter, you may draw a card.
    """, "2/4")
    private val multiversalBreach = Cards.invent("Multiversal Breach", "{5}{U}{U}", "Sorcery", "For each nontoken creature you control, create a token that's a copy of that creature, except it isn't legendary.")
    private val shrinkingParticles = Cards.invent("Shrinking Particles", "{U}", "Sorcery", """
        Target creature gains vigilance until end of turn and can't be blocked this turn.
        Draw a card.
    """)
    private val covertInvasion = Cards.invent("Covert Invasion", "{1}{U}{U}", "Enchantment — Aura", """
        Enchant creature you control
        When this Aura enters, exile up to one target creature other than enchanted creature until this Aura leaves the battlefield. Enchanted creature becomes a copy of that creature until this Aura leaves the battlefield.
        Enchanted creature has ward {2}.
    """)
    private val bureauDeploymentDrone = Cards.invent("Bureau Deployment Drone", "{2}{U}", "Artifact Creature — Robot", """
        Flying
        When this creature enters, create a 1/1 white Soldier creature token.
    """, "2/2")
    private val bureauFlyingCar = Cards.invent("Bureau Flying Car", "{2}{U}", "Artifact — Vehicle", """
        Flash
        Flying
        When this Vehicle enters, exile up to one target creature you control. Return that card to the battlefield under its owner's control at the beginning of the next end step.
        Crew 1
    """, "3/3")
    private val princessImaniRoyalInventor = Cards.invent("Princess Imani, Royal Inventor", "{1}{U}", "Legendary Creature — Human Artificer Hero", """
        Artifact spells you cast cost {1} less to cast.
        {1}, {T}: Target artifact you control becomes a copy of a second target artifact you control until end of turn, except it isn't legendary. Activate only as a sorcery.
    """, "2/1")
    private val superIntellect = Cards.invent("Super Intellect", "{U}", "Enchantment — Aura", """
        Enchant creature
        At the beginning of the upkeep of enchanted creature's controller, that player draws a card.
    """)
    private val prototypeSuperSuit = Cards.invent("Prototype Super Suit", "{1}{U}", "Artifact — Equipment", """
        Flash
        When this Equipment enters, attach it to target creature you control. Untap that creature.
        Equipped creature gets +1/+2.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val hungerForLearning = Cards.invent("Hunger for Learning", "{2}{U}", "Instant", "Draw three cards. Then discard two cards unless you discard an artifact card.")
    private val hexenRisingMagician = Cards.invent("Hexen, Rising Magician", "{4}{U}", "Legendary Creature — Mutant Warlock Hero", """
        Flying
        Whenever you cast a noncreature spell, exile another target nonland, nontoken permanent. Return that card to the battlefield under its owner's control at the beginning of the next end step.
    """, "4/4")
    private val theWondrousHornet = Cards.invent("The Wondrous Hornet", "{1}{U}", "Legendary Creature — Human Hero", """
        Flash
        Flying
        Wasp's Sting — When The Wondrous Hornet enters, tap up to one target creature. It loses all abilities for as long as The Wondrous Hornet remains on the battlefield.
    """, "2/1")
    private val agentsOfTheSerpentCult = Cards.invent("Agents of the Serpent Cult", "{1}{B}", "Creature — Human Spy Villain", "When this creature dies, create a 2/1 black Villain creature token with menace. (It can't be blocked except by two or more creatures.)", "1/1")
    private val doctorZolanBioFanatic = Cards.invent("Doctor Zolan, Bio-Fanatic", "{2}{B}", "Legendary Artifact Creature — Scientist Villain", "{3}, {T}: Create a tapped 2/1 black Villain creature token with menace. Activate only if there are two or more creature cards in your graveyard. (It can't be blocked except by two or more creatures.)", "2/3")
    private val crimsonSpiderSuperSpy = Cards.invent("Crimson Spider, Super Spy", "{1}{B}", "Legendary Creature — Human Spy Hero", """
        Menace
        Whenever Crimson Spider deals combat damage to a player, that player exiles cards from the top of their library until they exile a nonland card. You may put a +1/+1 counter on Crimson Spider. If you don't, you may cast the exiled nonland card until end of turn and mana of any type can be spent to cast that spell.
    """, "2/1")
    private val decoyFeint = Cards.invent("Decoy Feint", "{1}{B}", "Instant", """
        Choose one or both —
        • Return target Villain card from your graveyard to your hand.
        • Return target Hero card from your graveyard to your hand.
    """)
    private val doctorDread = Cards.invent("Doctor Dread", "{4}{B}{B}", "Legendary Creature — Human Scientist Villain", """
        When Doctor Dread enters, create two 3/3 colorless Robot Villain artifact creature tokens named Dreadbot.
        As long as you control an artifact creature or a Plan, Doctor Dread has indestructible.
        At the beginning of your end step, you draw a card and lose 1 life.
    """, "3/3")
    private val scytheLethalLegionnaire = Cards.invent("Scythe, Lethal Legionnaire", "{3}{B}", "Legendary Creature — Human Villain", "Whenever Scythe attacks, you may pay {3}{B}. When you do, return target creature card from your graveyard to the battlefield tapped and attacking with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)", "3/4")
    private val serpentCultTroopers = Cards.invent("Serpent Cult Troopers", "{2}{B}", "Creature — Human Soldier Villain", "When this creature enters, create a tapped 2/1 black Villain creature token with menace if there are two or more creature cards in your graveyard. Otherwise, mill two cards. (Put the top two cards of your library into your graveyard.)", "3/2")
    private val overlordsEnforcers = Cards.invent("Overlord's Enforcers", "{2}{B}", "Creature — Human Villain", """
        Lifelink
        {2}{B}, Sacrifice an artifact or creature: Draw a card.
    """, "2/3")
    private val resonatorSonicSubjugator = Cards.invent("Resonator, Sonic Subjugator", "{2}{B}", "Legendary Creature — Human Rogue Villain", "Sonic Attack — When Resonator enters, target player reveals a number of cards from their hand equal to one plus the number of creature cards in your graveyard. You choose one of them. That player discards that card.", "2/2")
    private val lunarStoneHarshMistress = Cards.invent("Lunar Stone, Harsh Mistress", "{3}{B}", "Legendary Creature — Human Doctor Villain", """
        Flying
        Whenever you discard a card, you may exile that card from your graveyard. If you do, until the end of your next turn, you may play that card.
    """, "2/4")
    private val wanderingBladeShadowStalker = Cards.invent("Wandering Blade, Shadow Stalker", "{2}{B}", "Legendary Creature — Human Rogue Hero", """
        Pay 2 life: Add two mana of any one color. Spend this mana only to cast Equipment spells or activate equip abilities. Activate only once each turn.
        {T}, Sacrifice an Equipment attached to Wandering Blade: Target creature gets -4/-4 until end of turn. Activate only as a sorcery.
    """, "3/3")
    private val petrocorpBrutes = Cards.invent("Petrocorp Brutes", "{4}{B}", "Creature — Human Berserker Villain", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Whenever you draw your second card each turn, put a +1/+1 counter on target creature.
        Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
    """, "4/4")
    private val stolenSteeleTech = Cards.invent("Stolen Steele Tech", "{1}{B}", "Artifact — Equipment", """
        Flash
        When this Equipment enters, attach it to target creature you control. That creature gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
        Equipped creature gets +1/+0.
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val superShifter = Cards.invent("Super-Shifter", "{1}{B}{B}{B}", "Legendary Creature — Skrull Shapeshifter Villain", """
        Flying
        {2}{W}: Create a 0/4 colorless Wall creature token with defender.
        {3}{G}: Super-Shifter gets +4/+4 until end of turn.
        {4}{R}: Super-Shifter deals 4 damage to target creature.
        {5}{U}: Target player draws four cards.
    """, "4/5")
    private val lightningSquadConspiracy = Cards.invent("Lightning Squad Conspiracy", "{3}{B}", "Enchantment", """
        Flash
        Whenever a Villain you control dies, return it to the battlefield under its owner's control with a finality counter on it. That creature is a Hero in addition to its other types. (If a creature with a finality counter on it would die, exile it instead.)
    """)
    private val visionsOfInfamy = Cards.invent("Visions of Infamy", "{2}{B}", "Instant", """
        This spell costs {1} less to cast if you control a Villain.
        You draw two cards and lose 2 life.
    """)
    private val lashVengefulEngineer = Cards.invent("Lash, Vengeful Engineer", "{B}", "Legendary Creature — Human Artificer Villain", """
        Lash enters tapped.
        Whenever Lash attacks, if he's equipped, each opponent loses X life and you gain X life, where X is the number of Equipment attached to him.
    """, "2/2")
    private val guardiansDisbanded = Cards.invent("Guardians Disbanded", "{1}{R}{R}", "Sorcery", """
        Choose one or both —
        • Guardians Disbanded deals 3 damage to each creature.
        • Destroy target land. Its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle.
    """)
    private val inEvilsGrip = Cards.invent("In Evil's Grip", "{2}{R}", "Sorcery", "Gain control of target creature until end of turn. If you control a Villain with greater mana value than that creature, gain control of that creature until the end of your next turn instead. Untap that creature. It gains haste until end of turn.")
    private val fletcherYoungGuardian = Cards.invent("Fletcher, Young Guardian", "{3}{R}", "Legendary Creature — Human Archer Hero", """
        Reach
        If a source you control would deal noncombat damage to an opponent or a permanent an opponent controls, instead it deals that much damage plus X, where X is Fletcher's power.
    """, "2/4")
    private val fletchersBow = Cards.invent("Fletcher's Bow", "{R}", "Artifact — Equipment", """
        Equipped creature gets +1/+0 and has reach.
        Whenever equipped creature becomes tapped, it deals 1 damage to each opponent.
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val chaosHex = Cards.invent("Chaos Hex", "{2}{R}", "Sorcery — Arcane", "Exile all the cards from your hand, then draw that many cards. Until the end of your next turn, you may play cards exiled this way.")
    private val hireSomeMuscle = Cards.invent("Hire Some Muscle", "{2}{R}", "Instant", "Create a 2/1 black Villain creature token with menace, then creatures you control get +1/+0 until end of turn. (A creature with menace can't be blocked except by two or more creatures.)")
    private val serpentCultAssaultRobot = Cards.invent("Serpent Cult Assault Robot", "{1}{R}", "Artifact Creature — Robot Villain", "Whenever another Villain and/or artifact you control enters, this creature deals 1 damage to target opponent.", "1/3")
    private val rheaKanePrivateEye = Cards.invent("Rhea Kane, Private Eye", "{2}{R}", "Legendary Creature — Human Detective Hero", "{T}, Put a stun counter on Rhea Kane: Exile the top X cards of your library, where X is Rhea Kane's power. You may play those cards this turn. (If a permanent with a stun counter would become untapped, remove one from it instead.)", "2/3")
    private val hiddenCityWarrior = Cards.invent("Hidden City Warrior", "{1}{R}", "Creature — Human Warrior Hero", "When this creature enters, you may sacrifice an artifact or discard a card. If you do, draw a card.", "2/2")
    private val toolmakerAutomaton = Cards.invent("Toolmaker Automaton", "{2}{R}", "Artifact Creature — Robot Villain", """
        Trample
        Whenever another artifact you control enters, put a +1/+1 counter on this creature.
    """, "2/2")
    private val veraSteeleHeroForHire = Cards.invent("Vera Steele, Hero for Hire", "{1}{R}", "Legendary Creature — Human Detective Hero", "{2}, {T}, Discard a card: Draw a card for each card you've discarded this turn.", "3/1")
    private val skyforgedHammerOfTheStorm = Cards.invent("Skyforged, Hammer of the Storm", "{3}{R}", "Legendary Artifact — Equipment", """
        When Skyforged enters, it deals 4 damage to up to one target creature.
        Double all damage equipped creature would deal.
        Equip worthy {1} (A creature is worthy if it's a legendary non-Villain that's red and/or white.)
        {2}{R}, Discard this card: It deals 2 damage to each creature.
    """)
    private val plasmaBoltBarrage = Cards.invent("Plasma Bolt Barrage", "{X}{R}{R}", "Sorcery", """
        When you cast this spell, copy it X times. You may choose new targets for the copies.
        Plasma Bolt Barrage deals 1 damage to target creature.
    """)
    private val crimsonBrute = Cards.invent("Crimson Brute", "{4}{R}{R}", "Legendary Creature — Gamma Berserker Villain", """
        Reach, trample
        Enrage — Whenever Crimson Brute is dealt damage, put a +1/+1 counter on him. When you do, he deals damage equal to the number of +1/+1 counters on him to any other target.
    """, "6/7")
    private val theChaosWitch = Cards.invent("The Chaos Witch", "{2}{R}", "Legendary Creature — Mutant Warlock Hero", "Instant and sorcery spells you cast with mana value 4 or greater cost {X} less to cast, where X is The Chaos Witch's power.", "2/3")
    private val quickstepYoungGuardian = Cards.invent("Quickstep, Young Guardian", "{1}{R}", "Legendary Creature — Mutant Hero", """
        Haste
        Whenever you cast a noncreature spell, you may pay {1}. When you do, target creature with haste can't be blocked this turn except by creatures with haste.
    """, "2/2")
    private val steeleIndustriesExecutive = Cards.invent("Steele Industries Executive", "{R}", "Creature — Human Advisor", "{2}, {T}: Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")", "1/2")
    private val superSwiftness = Cards.invent("Super Swiftness", "{R}", "Enchantment — Aura", """
        Flash
        Enchant creature
        When this Aura enters, enchanted creature gains first strike until end of turn.
        Enchanted creature gets +1/+0 and has haste.
    """)
    private val thundarGodOfThunder = Cards.invent("Thundar, God of Thunder", "{3}{R}{R}", "Legendary Creature — God Warrior Hero", """
        Flying
        When Thundar enters, exile target Equipment, instant, or sorcery card from your graveyard. Until the end of your next turn, you may play that card.
        Whenever you cast a noncreature spell, Thundar deals damage equal to that spell's mana value to any target.
    """, "5/5")
    private val lorryHurl = Cards.invent("Lorry Hurl", "{2}{R}{R}", "Instant", """
        This spell costs {2} less to cast if you control a Vehicle.
        Lorry Hurl deals 4 damage to any target.
    """)
    private val glimpseOfLove = Cards.invent("Glimpse of Love", "{1}{R}", "Instant", "You may sacrifice an artifact or discard a card. If you do, draw two cards.")

    private val hero = Cards.invent("Avenue Hero", "{1}{W}", "Creature — Human Hero", "", "2/2")
    private val villain = Cards.invent("Avenue Thug", "{1}{B}", "Creature — Human Villain", "", "2/2")
    private val merfolk = Cards.invent("Avenue Merfolk", "{U}", "Creature — Merfolk", "", "1/1")
    private val growth = Cards.invent("Avenue Growth", "{G}", "Instant", "Put a +1/+1 counter on target creature.")
    private val letters = Cards.invent("Avenue Letters", "{U}", "Sorcery", "Draw two cards.")
    private val bigSpell = Cards.invent("Avenue Barrage", "{4}{R}", "Sorcery", "Avenue Barrage deals 5 damage to any target.")
    private val muster = Cards.invent("Avenue Muster", "{W}", "Sorcery", "Create a 1/1 white Soldier creature token.")
    private val rummage = Cards.invent("Avenue Rummage", "{R}", "Sorcery", "Discard a card, then draw a card.")
    private val vehicle = Cards.invent("Avenue Cruiser", "{2}", "Artifact — Vehicle", "Crew 1", "3/3")

    // ---- Chunks 08–09: white

    @Test
    fun commanderLibertyWingsPumpsHeroesByToughness() = scenario {
        alice.battlefield(commanderLibertyWingsOfValor, hero)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(commanderLibertyWingsOfValor)
        resolveAll()
        expect { hero.power shouldBe 3 }
    }

    // CR 702.8a: as though it had flash
    @Test
    fun starwardenFlashesAfterAnOpponentsSpell() = scenario {
        alice.battlefield(starwardenSpaceBorn, "Forest", "Forest")
        alice.hand(bear)
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(active = bob)
        bob.casts(shock, target = "alice")
        resolveAll()
        bob.passes()
        alice.casts(bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun heroInTheMakingDrawsAndHealsWithAHero() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", hero)
        alice.hand(heroInTheMaking)
        alice.library(bear)
        alice.casts(heroInTheMaking)
        resolveAll()
        expect {
            alice.hand has bear
            alice.life shouldBe 22
        }
    }

    @Test
    fun unseenMatriarchBuildsWalls() = scenario {
        alice.battlefield(unseenMatriarch, hero, "Forest")
        alice.hand(growth)
        alice.casts(growth, target = hero)
        resolveAll()
        may(true)
        resolveAll()
        expect { "Wall Token".toughness shouldBe 4 }
    }

    @Test
    fun nightMedicSavesWhatDiedThisTurn() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", bear)
        alice.hand(doom, nightMedicHealerOfHeroes)
        alice.casts(doom, target = bear)
        resolveAll()
        alice.casts(nightMedicHealerOfHeroes)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun ayokaBringsFirstStrikingSoldiers() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(ayokaRoyalGuardCaptain)
        alice.casts(ayokaRoyalGuardCaptain)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Soldier Token" } shouldBe 2 }
    }

    @Test
    fun pantherLeapInvestigatesAndLifts() = scenario {
        alice.battlefield("Plains")
        alice.battlefield(bear, tapped = true)
        alice.hand(pantherLeap)
        alice.casts(pantherLeap, targets = listOf("alice", bear))
        resolveAll()
        expect {
            alice.battlefield has "Clue Token"
            bear hasKeyword Keyword.Flying
            bear.tapped shouldBe false
        }
    }

    @Test
    fun youngPatriotShieldsAnother() = scenario {
        alice.battlefield(youngPatriotShieldBearer, bear, "Plains", "Plains")
        alice.activates(youngPatriotShieldBearer, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Hexproof
        }
    }

    @Test
    fun tremorTapsOnNoncreatureSpells() = scenario {
        alice.battlefield(tremorAgentOfTheBureau, "Mountain")
        alice.hand(shock)
        bob.battlefield(ogre)
        alice.casts(shock, target = "bob")
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { ogre.tapped shouldBe true }
    }

    @Test
    fun vaultSecurityOfficerCheaperOnSmallCreatures() = scenario {
        alice.battlefield(vaultSecurityOfficer, "Plains")
        bob.battlefield(bear)
        alice.activates(vaultSecurityOfficer, target = bear)
        resolveAll()
        expect { bear.tapped shouldBe true }
    }

    @Test
    fun crimsonSentinelPunishesADamageDealer() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(crimsonSentinelSuperSoldier)
        alice.library(elf)
        bob.battlefield(ogre)
        bob.library(elf, elf)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(ogre)
        advanceTo(TurnStep.EndOfCombat)
        bob.passes()
        alice.casts(crimsonSentinelSuperSoldier)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun theAurumGivesThemTheAbyss() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(theAurumGoldenGuardian)
        alice.casts(theAurumGoldenGuardian)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect {
            bob.battlefield has "The Abyss"
            "The Abyss" hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun superVillainHoldingCellExilesATappedCreature() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(superVillainHoldingCell)
        bob.battlefield(ogre, tapped = true)
        alice.casts(superVillainHoldingCell)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.exile has ogre }
    }

    @Test
    fun superSoldierTonicMakesALegendaryArmedSoldier() = scenario {
        alice.battlefield("Plains", "Plains", bear, sword)
        alice.hand(superSoldierTonic)
        alice.casts(superSoldierTonic, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.FirstStrike
            bear.subtypes.map { it.name }.contains("Soldier") shouldBe true
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        pickTargets(sword)
        resolveAll()
        expect { bear.power shouldBe 5 }
    }

    @Test
    fun raiseTheShieldProtects() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(raiseTheShield)
        alice.casts(raiseTheShield, target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Indestructible
            bear hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun paleWidowCountersTwo() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear, elf)
        alice.hand(paleWidowFreeAgent)
        alice.casts(paleWidowFreeAgent)
        resolveAll()
        modes(0)
        pickTargets(bear, elf)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            elf.counters(p1p1) shouldBe 1
        }
    }

    // ---- Chunks 10–11: blue

    @Test
    fun krakosLeadsMerfolkAndDraws() = scenario {
        alice.battlefield(krakosTidalWarlord, merfolk)
        alice.library(bear)
        expect { merfolk.power shouldBe 2 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(merfolk)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun sapTheSurgeCheapOnAnAttacker() = scenario {
        alice.battlefield("Island")
        alice.hand(sapTheSurge)
        alice.library(elf, elf)
        bob.battlefield(giant)
        bob.library(elf, elf)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(giant)
        bob.passes()
        alice.casts(sapTheSurge, target = giant)
        resolveAll()
        expect {
            giant.power shouldBe 1
            alice.hand.size shouldBe 1
        }
    }

    @Test
    fun kestrelBringsScarletWing() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(kestrelWingedWonder)
        alice.casts(kestrelWingedWonder)
        resolveAll()
        expect { "Scarlet Wing" hasKeyword Keyword.Flying }
    }

    @Test
    fun kestrelsWingHarnessAttachesAndLifts() = scenario {
        alice.battlefield("Island", "Island", bear)
        alice.hand(kestrelsWingHarness)
        alice.casts(kestrelsWingHarness)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Flying
        }
    }

    @Test
    fun encasedInFrostLocksACreature() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(encasedInFrost)
        bob.battlefield(sphinx)
        alice.casts(encasedInFrost, target = sphinx)
        resolveAll()
        expect {
            sphinx.tapped shouldBe true
            sphinx lacksKeyword Keyword.Flying
        }
    }

    @Test
    fun futuristFoundryDrawsTwiceOver() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(futuristFoundry)
        alice.library(bear, bear, bear)
        alice.casts(futuristFoundry)
        resolveAll()
        alice.activates(futuristFoundry)
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
    }

    @Test
    fun giantSizedFlyingBeetleTaps() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(giantSizedFlyingBeetle)
        bob.battlefield(idol)
        alice.casts(giantSizedFlyingBeetle)
        resolveAll()
        modes(0)
        targetsIfAsked(idol)
        resolveAll()
        expect { idol.tapped shouldBe true }
    }

    @Test
    fun hydraulicAssistantPaysForArtifacts() = scenario {
        alice.battlefield(hydraulicAssistant, "Plains")
        alice.hand(idol)
        alice.casts(idol, paying = listOf(hydraulicAssistant, "Plains"))
        resolveAll()
        expect { alice.battlefield has idol }
    }

    @Test
    fun iAmTheArmorAnimatesAndDraws() = scenario {
        alice.battlefield("Island", "Island", "Island", idol)
        alice.hand(iAmTheArmor)
        alice.library(bear)
        alice.casts(iAmTheArmor, target = idol)
        resolveAll()
        expect {
            idol.power shouldBe 4
            idol hasKeyword Keyword.Flying
            alice.hand has bear
        }
    }

    @Test
    fun steelLadDrawsAnArtifact() = scenario {
        alice.battlefield(steelLadDivergingDestiny)
        alice.library(idol, bear)
        alice.activates(steelLadDivergingDestiny)
        resolveAll()
        expect { alice.hand has idol }
    }

    // CR 702.126a: improvise
    @Test
    fun forgeheartImprovises() = scenario {
        alice.battlefield("Island", idol, idol, idol, idol)
        alice.hand(forgeheartCleverChampion)
        alice.casts(forgeheartCleverChampion)
        resolveAll()
        expect { alice.battlefield has forgeheartCleverChampion }
    }

    @Test
    fun verdictBouncesAndGrows() = scenario {
        alice.battlefield("Island", "Island", "Island", bear)
        alice.hand(verdictTelekineticKnight)
        alice.casts(verdictTelekineticKnight)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            alice.hand has bear
            verdictTelekineticKnight.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun youngTricksterGrowsAndHides() = scenario {
        alice.battlefield(youngTrickster, "Island")
        alice.hand(letters)
        alice.library(bear, bear)
        alice.casts(letters)
        resolveAll()
        expect {
            youngTrickster.counters(p1p1) shouldBe 1
            youngTrickster hasKeyword Keyword.Hexproof
        }
    }

    @Test
    fun doctorElasticDrawsForTokens() = scenario {
        alice.battlefield(doctorElasticRubberGenius, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(muster)
        alice.library(bear)
        alice.casts(muster)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.hand has bear }
    }

    // CR 707.9b: a copy except it isn't legendary
    @Test
    fun multiversalBreachCopiesEachCreature() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", bear, commanderLibertyWingsOfValor)
        alice.hand(multiversalBreach)
        alice.casts(multiversalBreach)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == bear } shouldBe 2
            alice.battlefield.names.count { it == commanderLibertyWingsOfValor } shouldBe 2
        }
    }

    @Test
    fun shrinkingParticlesSneaksAndDraws() = scenario {
        alice.battlefield("Island", bear)
        alice.hand(shrinkingParticles)
        alice.library(elf)
        bob.battlefield(ogre)
        alice.casts(shrinkingParticles, target = bear)
        resolveAll()
        expect { alice.hand has elf }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to bear) }
    }

    // CR 707.2, 610.3
    @Test
    fun covertInvasionStealsAShape() = scenario {
        alice.battlefield("Island", "Island", "Island", bear)
        alice.hand(covertInvasion)
        bob.battlefield(giant)
        alice.casts(covertInvasion, target = bear)
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        expect {
            bob.exile has giant
            card(bear).power shouldBe 5
        }
    }

    @Test
    fun bureauDeploymentDroneBringsASoldier() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(bureauDeploymentDrone)
        alice.casts(bureauDeploymentDrone)
        resolveAll()
        expect { alice.battlefield has "Soldier Token" }
    }

    @Test
    fun bureauFlyingCarBlinksACreature() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.hand(bureauFlyingCar)
        alice.casts(bureauFlyingCar)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { alice.exile has bear }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 0 }
    }

    @Test
    fun princessImaniDiscountsArtifacts() = scenario {
        alice.battlefield(princessImaniRoyalInventor, "Island")
        alice.hand(idol)
        alice.casts(idol)
        resolveAll()
        expect { alice.battlefield has idol }
    }

    @Test
    fun princessImaniCopiesAnArtifact() = scenario {
        alice.battlefield(princessImaniRoyalInventor, idol, sword, "Island")
        alice.activates(princessImaniRoyalInventor, targets = listOf(idol, sword))
        resolveAll()
        expect { idol.subtypes.map { it.name } shouldBe listOf("Equipment") }
    }

    @Test
    fun superIntellectDrawsEachUpkeep() = scenario {
        alice.battlefield("Island", bear)
        alice.hand(superIntellect)
        alice.library(elf, elf, elf)
        bob.library(elf, elf)
        alice.casts(superIntellect, target = bear)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun prototypeSuperSuitUntapsAndArmors() = scenario {
        alice.battlefield("Island", "Island")
        alice.battlefield(bear, tapped = true)
        alice.hand(prototypeSuperSuit)
        alice.casts(prototypeSuperSuit)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear.toughness shouldBe 4
        }
    }

    @Test
    fun hungerForLearningKeepsMoreWithAnArtifact() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(hungerForLearning)
        alice.library(idol, bear, elf)
        alice.casts(hungerForLearning)
        resolveAll()
        // CR 118.12: "unless you discard an artifact card"
        may(true)
        chooseIfOffered(idol)
        resolveAll()
        expect {
            alice.graveyard has idol
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun hexenBlinksOnNoncreatureSpells() = scenario {
        alice.battlefield(hexenRisingMagician, "Mountain")
        alice.hand(shock)
        bob.battlefield(giant)
        alice.casts(shock, target = "bob")
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        expect { bob.exile has giant }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { bob.battlefield has giant }
    }

    @Test
    fun theWondrousHornetStings() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(theWondrousHornet)
        bob.battlefield(sphinx)
        alice.casts(theWondrousHornet)
        resolveAll()
        targetsIfAsked(sphinx)
        resolveAll()
        expect {
            sphinx.tapped shouldBe true
            sphinx lacksKeyword Keyword.Flying
        }
    }

    // ---- Chunks 12–13: black

    @Test
    fun agentsOfTheSerpentCultLeaveAVillain() = scenario {
        alice.battlefield(agentsOfTheSerpentCult, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = agentsOfTheSerpentCult)
        resolveAll()
        expect { alice.battlefield has "Villain Token" }
    }

    @Test
    fun doctorZolanBreedsVillains() = scenario {
        alice.battlefield(doctorZolanBioFanatic, "Swamp", "Swamp", "Swamp")
        alice.graveyard(bear, ogre)
        alice.activates(doctorZolanBioFanatic)
        resolveAll()
        expect { "Villain Token".tapped shouldBe true }
    }

    @Test
    fun crimsonSpiderSuperSpyGrows() = scenario {
        alice.battlefield(crimsonSpiderSuperSpy)
        bob.library("Island", ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(crimsonSpiderSuperSpy)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            bob.exile has ogre
            crimsonSpiderSuperSpy.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun decoyFeintReturnsBoth() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(decoyFeint)
        alice.graveyard(villain, hero)
        alice.casts(decoyFeint, modes = listOf(0, 1), targets = listOf(villain, hero))
        resolveAll()
        expect {
            alice.hand has villain
            alice.hand has hero
        }
    }

    @Test
    fun doctorDreadBuildsDreadbotsAndDraws() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(doctorDread)
        alice.library(bear)
        alice.casts(doctorDread)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Dreadbot" } shouldBe 2
            doctorDread hasKeyword Keyword.Indestructible
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.hand has bear
            alice.life shouldBe 19
        }
    }

    @Test
    fun scytheRaisesAnAttacker() = scenario {
        alice.battlefield(scytheLethalLegionnaire, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(scytheLethalLegionnaire)
        resolveAll()
        may(true)
        payIfAsked()
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            ogre.counters(CounterKind.Finality) shouldBe 1
            ogre.tapped shouldBe true
        }
    }

    @Test
    fun serpentCultTroopersMillWithoutCreatures() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(serpentCultTroopers)
        alice.library(elf, elf, elf)
        alice.casts(serpentCultTroopers)
        resolveAll()
        expect { alice.graveyard.size shouldBe 2 }
    }

    @Test
    fun serpentCultTroopersBringAVillainWithCreatures() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(serpentCultTroopers)
        alice.graveyard(bear, ogre)
        alice.casts(serpentCultTroopers)
        resolveAll()
        expect { "Villain Token".tapped shouldBe true }
    }

    @Test
    fun overlordsEnforcersDrawForASacrifice() = scenario {
        alice.battlefield(overlordsEnforcers, idol, "Swamp", "Swamp", "Swamp")
        alice.library(bear)
        alice.activates(overlordsEnforcers, choosing = listOf(idol))
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun resonatorRevealsAndStrips() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(resonatorSonicSubjugator)
        alice.graveyard(bear)
        bob.hand(ogre, elf, giant)
        alice.casts(resonatorSonicSubjugator)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        if (pending is Decision.ChooseObjects && (pending as Decision.ChooseObjects).player == bob.id) bob.chooses(ogre, elf)
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect { bob.hand.size shouldBe 2 }
    }

    @Test
    fun lunarStoneReplaysDiscards() = scenario {
        alice.battlefield(lunarStoneHarshMistress, "Mountain")
        alice.hand(rummage, bear)
        alice.library(elf)
        alice.casts(rummage)
        resolveAll()
        chooseIfOffered(bear)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.exile has bear }
    }

    @Test
    fun wanderingBladeSacrificesItsEquipment() = scenario {
        alice.battlefield(wanderingBladeShadowStalker, sword, "Plains")
        bob.battlefield(giant)
        alice.activates(sword, target = wanderingBladeShadowStalker)
        resolveAll()
        alice.activates(wanderingBladeShadowStalker, ability = 1, target = giant, choosing = listOf(sword))
        resolveAll()
        expect { giant.power shouldBe 1 }
    }

    @Test
    fun petrocorpBrutesCounterOnTheSecondDraw() = scenario {
        alice.battlefield(petrocorpBrutes, "Island")
        alice.hand(letters)
        alice.library(bear, bear)
        alice.casts(letters)
        resolveAll()
        targetsIfAsked(petrocorpBrutes)
        resolveAll()
        expect { petrocorpBrutes.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun stolenSteeleTechAttachesAndProtects() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand(stolenSteeleTech)
        alice.casts(stolenSteeleTech)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun superShifterShapes() = scenario {
        alice.battlefield(superShifter, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Plains", "Plains", "Plains")
        bob.battlefield(ogre)
        alice.activates(superShifter, ability = 2, target = ogre, paying = listOf("Mountain", "Mountain", "Mountain", "Mountain", "Mountain"))
        resolveAll()
        alice.activates(superShifter, ability = 0)
        resolveAll()
        expect {
            bob.graveyard has ogre
            "Wall Token".toughness shouldBe 4
        }
    }

    @Test
    fun lightningSquadConspiracyRedeemsVillains() = scenario {
        alice.battlefield(lightningSquadConspiracy, villain, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = villain)
        resolveAll()
        expect {
            villain.counters(CounterKind.Finality) shouldBe 1
            villain.subtypes.map { it.name }.contains("Hero") shouldBe true
        }
    }

    @Test
    fun visionsOfInfamyCheaperWithAVillain() = scenario {
        alice.battlefield(villain, "Swamp", "Swamp")
        alice.hand(visionsOfInfamy)
        alice.library(bear, bear)
        alice.casts(visionsOfInfamy)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.life shouldBe 18
        }
    }

    @Test
    fun lashDrainsWhenEquipped() = scenario {
        alice.battlefield(lashVengefulEngineer, sword, "Plains")
        alice.activates(sword, target = lashVengefulEngineer)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(lashVengefulEngineer)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    @Test
    fun lashEntersTapped() = scenario {
        alice.battlefield("Swamp")
        alice.hand(lashVengefulEngineer)
        alice.casts(lashVengefulEngineer)
        resolveAll()
        expect { lashVengefulEngineer.tapped shouldBe true }
    }

    // ---- Chunk 14: red

    @Test
    fun guardiansDisbandedBurnsEveryCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear)
        alice.hand(guardiansDisbanded)
        bob.battlefield(ogre, giant)
        alice.casts(guardiansDisbanded, modes = listOf(0))
        resolveAll()
        expect {
            alice.graveyard has bear
            bob.graveyard has ogre
            bob.battlefield has giant
        }
    }

    @Test
    fun inEvilsGripBorrowsACreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(inEvilsGrip)
        bob.battlefield(ogre, tapped = true)
        alice.casts(inEvilsGrip, target = ogre)
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            ogre.tapped shouldBe false
            ogre hasKeyword Keyword.Haste
        }
    }

    @Test
    fun fletcherAddsHisPower() = scenario {
        alice.battlefield(fletcherYoungGuardian, "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun fletchersBowPingsWhenTapped() = scenario {
        alice.battlefield(fletchersBow, bear, "Mountain")
        alice.activates(fletchersBow, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect {
            bob.life shouldBe 19
            bear hasKeyword Keyword.Reach
        }
    }

    @Test
    fun chaosHexSwapsTheHand() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Forest", "Forest")
        alice.hand(chaosHex, bear, ogre)
        alice.library(elf, elf)
        alice.casts(chaosHex, paying = listOf("Mountain", "Mountain", "Mountain"))
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.exile has bear
        }
        alice.casts(bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun hireSomeMuscleThenPumps() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear)
        alice.hand(hireSomeMuscle)
        alice.casts(hireSomeMuscle)
        resolveAll()
        expect {
            "Villain Token".power shouldBe 3
            bear.power shouldBe 3
        }
    }

    @Test
    fun serpentCultAssaultRobotPingsForVillains() = scenario {
        alice.battlefield(serpentCultAssaultRobot, "Swamp", "Swamp")
        alice.hand(villain)
        alice.casts(villain)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun rheaKaneDigsByPower() = scenario {
        alice.battlefield(rheaKanePrivateEye, "Forest", "Forest")
        alice.library(bear, "Island", ogre)
        alice.activates(rheaKanePrivateEye)
        resolveAll()
        expect {
            rheaKanePrivateEye.counters(CounterKind.Stun) shouldBe 1
            alice.exile has bear
        }
        alice.casts(bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun hiddenCityWarriorRummages() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(hiddenCityWarrior, ogre)
        alice.library(bear)
        alice.casts(hiddenCityWarrior)
        resolveAll()
        if (pending is Decision.May) may(true)
        if (pending is Decision.ChooseOption) option("Discard")
        chooseIfOffered(ogre)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun toolmakerAutomatonGrowsOnArtifacts() = scenario {
        alice.battlefield(toolmakerAutomaton, "Mountain", "Mountain")
        alice.hand(idol)
        alice.casts(idol)
        resolveAll()
        expect { toolmakerAutomaton.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun veraSteeleDrawsPerDiscard() = scenario {
        alice.battlefield(veraSteeleHeroForHire, "Mountain", "Mountain")
        alice.hand(bear)
        alice.library(elf, elf)
        alice.activates(veraSteeleHeroForHire, choosing = listOf(bear))
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun skyforgedStrikesOnEntering() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(skyforgedHammerOfTheStorm)
        bob.battlefield(ogre)
        alice.casts(skyforgedHammerOfTheStorm)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun skyforgedDiscardedSweeps() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear)
        alice.hand(skyforgedHammerOfTheStorm)
        bob.battlefield(elf, ogre)
        alice.activates(skyforgedHammerOfTheStorm, ability = 1)
        resolveAll()
        expect {
            alice.graveyard has bear
            bob.graveyard has elf
            bob.battlefield has ogre
        }
    }

    // CR 707.10: copies on cast
    @Test
    fun plasmaBoltBarrageCopiesX() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(plasmaBoltBarrage)
        bob.battlefield(ogre)
        alice.casts(plasmaBoltBarrage, x = 2, target = ogre)
        // the two copies keep the Ogre as their target (CR 707.10c)
        repeat(4) {
            resolveAll()
            if (pending is Decision.May) may(false)
        }
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun crimsonBruteStrikesBackWhenDamaged() = scenario {
        alice.battlefield(crimsonBrute)
        bob.battlefield("Mountain")
        bob.hand(shock)
        alice.passes()
        bob.casts(shock, target = crimsonBrute)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect {
            crimsonBrute.counters(p1p1) shouldBe 1
            bob.life shouldBe 19
        }
    }

    @Test
    fun theChaosWitchDiscountsBigSpells() = scenario {
        alice.battlefield(theChaosWitch, "Mountain", "Mountain", "Mountain")
        alice.hand(bigSpell)
        alice.casts(bigSpell, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 15 }
    }

    @Test
    fun quickstepSneaksAHastyCreature() = scenario {
        alice.battlefield(quickstepYoungGuardian, "Mountain", "Mountain")
        alice.hand(shock)
        bob.battlefield(ogre)
        alice.casts(shock, target = "bob", paying = listOf("Mountain"))
        resolveAll()
        may(true)
        payIfAsked()
        resolveAll()
        targetsIfAsked(quickstepYoungGuardian)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(quickstepYoungGuardian)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to quickstepYoungGuardian) }
    }

    @Test
    fun steeleIndustriesExecutiveMakesTreasure() = scenario {
        alice.battlefield(steeleIndustriesExecutive, "Mountain", "Mountain")
        alice.activates(steeleIndustriesExecutive)
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
    }

    @Test
    fun superSwiftnessHastesAndStrikesFirst() = scenario {
        alice.battlefield("Mountain")
        alice.battlefield(bear, sick = true)
        alice.hand(superSwiftness)
        alice.casts(superSwiftness, target = bear)
        resolveAll()
        expect {
            bear hasKeyword Keyword.Haste
            bear hasKeyword Keyword.FirstStrike
            bear.power shouldBe 3
        }
    }

    @Test
    fun thundarReplaysASpellAndBurns() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(thundarGodOfThunder)
        alice.graveyard(shock)
        alice.casts(thundarGodOfThunder, paying = listOf("Mountain", "Mountain", "Mountain", "Mountain", "Mountain"))
        resolveAll()
        targetsIfAsked(shock)
        resolveAll()
        alice.casts(shock, target = "bob")
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun lorryHurlCheaperWithAVehicle() = scenario {
        alice.battlefield(vehicle, "Mountain", "Mountain")
        alice.hand(lorryHurl)
        alice.casts(lorryHurl, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun glimpseOfLoveDrawsTwoForAnArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain", idol)
        alice.hand(glimpseOfLove)
        alice.library(bear, bear)
        alice.casts(glimpseOfLove)
        resolveAll()
        if (pending is Decision.May) may(true)
        if (pending is Decision.ChooseOption) option("Sacrifice")
        chooseIfOffered(idol)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.graveyard has idol
        }
    }
}
