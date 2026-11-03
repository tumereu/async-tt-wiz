package arcanum.engine.sets.gtc

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.gtc.Gtc.bear
import arcanum.engine.sets.gtc.Gtc.charm
import arcanum.engine.sets.gtc.Gtc.doom
import arcanum.engine.sets.gtc.Gtc.flyer
import arcanum.engine.sets.gtc.Gtc.giant
import arcanum.engine.sets.gtc.Gtc.heal
import arcanum.engine.sets.gtc.Gtc.human
import arcanum.engine.sets.gtc.Gtc.idol
import arcanum.engine.sets.gtc.Gtc.ogre
import arcanum.engine.sets.gtc.Gtc.p1p1
import arcanum.engine.sets.gtc.Gtc.ponder
import arcanum.engine.sets.gtc.Gtc.shock
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Gatecrash's white, blue, black, red and green cards in play (parser chunks 06–10). */
class GtcMonoColorCardsScenariosTest {
    private val skywardManeuver = Cards.invent("Skyward Maneuver", "{1}{W}", "Instant", "Target creature gets +1/+1 and gains flying and first strike until end of turn.")
    private val seraphicEdict = Cards.invent("Seraphic Edict", "{4}{W}", "Sorcery", "Exile target creature or enchantment.")
    private val combatSeraph = Cards.invent("Combat Seraph", "{4}{W}{W}", "Creature — Angel", """
        Flying
        At the beginning of each combat, choose first strike, vigilance, or lifelink. Creatures you control gain that ability until end of turn.
    """, "4/4")
    private val plazaDenizen = Cards.invent("Plaza Denizen", "{2}{W}", "Creature — Human Soldier", "Whenever another white creature you control enters, tap target creature an opponent controls.", "2/2")
    private val debtcallersPulpit = Cards.invent("Debtcaller's Pulpit", "{4}{W}", "Enchantment — Aura", """
        Enchant land
        Enchanted land has "{T}: Tap target creature."
    """)
    private val obedientThrull = Cards.invent("Obedient Thrull", "{W}", "Creature — Thrull", "{B}: Regenerate this creature.", "1/1")
    private val gideonParagonOfTheCourts = Cards.invent("Gideon, Paragon of the Courts", "{2}{W}{W}", "Legendary Planeswalker — Gideon", """
        [+1]: Put a loyalty counter on Gideon for each creature target opponent controls.
        [0]: Until end of turn, Gideon becomes a Human Soldier creature with power and toughness each equal to the number of loyalty counters on him and gains indestructible. He's still a planeswalker. Prevent all damage that would be dealt to him this turn.
        [−15]: Exile all other permanents.
    """, loyalty = "4")
    private val guardianOfTheOpenGate = Cards.invent("Guardian of the Open Gate", "{4}{W}", "Creature — Angel", """
        Flying
        This creature can block any number of creatures.
        Whenever this creature blocks, it gets +1/+1 until end of turn for each creature it's blocking.
    """, "3/3")
    private val unityWard = Cards.invent("Unity Ward", "{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature has protection from multicolored.
    """)
    private val gatekeepersResolve = Cards.invent("Gatekeeper's Resolve", "{2}{W}", "Enchantment", "Creatures you control get +0/+1 for each Gate you control and have vigilance.")
    private val sanctifiedMantle = Cards.invent("Sanctified Mantle", "{2}{W}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2 and has protection from creatures.
    """)
    private val sentryWatch = Cards.invent("Sentry Watch", "{4}{W}", "Sorcery", "Create two 2/2 white Knight creature tokens with vigilance.")
    private val radiantPrimordial = Cards.invent("Radiant Primordial", "{5}{W}{W}", "Creature — Avatar", """
        Vigilance
        When this creature enters, for each opponent, exile up to one target creature that player controls and that player gains life equal to its power.
    """, "4/7")
    private val homicideInquest = Cards.invent("Homicide Inquest", "{1}{W}", "Enchantment — Aura", """
        Enchant creature you control
        When enchanted creature dies, create X 1/1 white Soldier creature tokens, where X is its power.
    """)
    private val vanguardCharge = Cards.invent("Vanguard Charge", "{1}{W}{W}", "Sorcery", "Creatures you control get +2/+2 until end of turn.")
    private val shieldingPassage = Cards.invent("Shielding Passage", "{W}", "Instant", "Prevent all damage that would be dealt to target creature this turn.")
    private val smiteTheBlocked = Cards.invent("Smite the Blocked", "{W}", "Instant", "Destroy target blocked creature.")
    private val urbisWarden = Cards.invent("Urbis Warden", "{4}{W}{W}", "Creature — Human Cleric", "When this creature enters, create a 4/4 white Angel creature token with flying.", "1/1")
    private val zarichiPanther = Cards.invent("Zarichi Panther", "{3}{W}", "Creature — Cat", "{1}{W}, {T}: You gain 2 life.", "2/3")
    private val aetherwindReversal = Cards.invent("Aetherwind Reversal", "{3}{U}", "Instant", "Return all attacking creatures to their owner's hand.")
    private val crowdphobia = Cards.invent("Crowdphobia", "{1}{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets -5/-0.
        {2}{U}: Return this Aura to its owner's hand.
    """)
    private val tidalPrimordial = Cards.invent("Tidal Primordial", "{5}{U}{U}", "Creature — Avatar", """
        Flying
        When this creature enters, for each opponent, you may cast up to one target instant or sorcery card from that player's graveyard without paying its mana cost. If a spell cast this way would be put into a graveyard, exile it instead.
    """, "5/5")
    private val embraceTheInfinite = Cards.invent("Embrace the Infinite", "{8}{U}{U}{U}{U}", "Sorcery", "Draw cards equal to the number of cards in your library, then put a card from your hand on top of your library. You have no maximum hand size until your next turn.")
    private val frilledWatcher = Cards.invent("Frilled Watcher", "{1}{U}", "Creature — Homunculus", "{1}{G}: This creature gets +2/+2 until end of turn. Activate only once each turn.", "1/3")
    private val gridlockSnare = Cards.invent("Gridlock Snare", "{X}{U}", "Instant", "Tap X target nonland permanents.")
    private val breachSpecialist = Cards.invent("Breach Specialist", "{1}{U}", "Creature — Human Wizard", "Whenever you cast your second spell each turn, this creature gets +2/+0 until end of turn and can't be blocked this turn.", "1/3")
    private val vaultkeyRogue = Cards.invent("Vaultkey Rogue", "{3}{U}", "Creature — Human Rogue", """
        This creature can't be blocked.
        When this creature enters, return a creature you control to its owner's hand.
    """, "3/2")
    private val leylineSpecter = Cards.invent("Leyline Specter", "{4}{U}", "Creature — Illusion", "When this creature deals combat damage, return it to its owner's hand. (Return it only if it survived combat.)", "5/5")
    private val skylineSprite = Cards.invent("Skyline Sprite", "{1}{U}", "Creature — Faerie Rogue", """
        Flying
        {U}: This creature gets +1/-1 until end of turn.
    """, "1/2")
    private val mindglassDrake = Cards.invent("Mindglass Drake", "{4}{U}", "Creature — Drake", """
        Flying
        When this creature dies, target player mills five cards.
    """, "2/5")
    private val hybridConversion = Cards.invent("Hybrid Conversion", "{U}", "Instant", "Destroy target creature. It can't be regenerated. That creature's controller creates a 3/3 green Frog Lizard creature token.")
    private val realmshaper = Cards.invent("Realmshaper", "{U}", "Creature — Vedalken Wizard", """
        As this creature enters, choose a basic land type.
        Lands you control are the chosen type in addition to their other types.
    """, "1/1")
    private val scholarsRowDenizen = Cards.invent("Scholar's Row Denizen", "{2}{U}", "Creature — Vedalken Wizard", "Whenever another blue creature you control enters, target player mills two cards.", "2/3")
    private val scatterWard = Cards.invent("Scatter Ward", "{3}{U}", "Instant", """
        Counter target noncreature spell.
        Draw a card.
    """)
    private val skywardGames = Cards.invent("Skyward Games", "{1}{U}", "Enchantment — Aura", """
        Enchant land
        Enchanted land has "{T}: Target creature gains flying until end of turn. Activate only as a sorcery."
    """)
    private val spellFracture = Cards.invent("Spell Fracture", "{1}{U}", "Instant", "Counter target spell unless its controller pays {X}, where X is the greatest power among creatures you control.")
    private val utterlyLost = Cards.invent("Utterly Lost", "{4}{U}", "Instant", "Put target nonland permanent on top of its owner's library.")
    private val pathOfTheThief = Cards.invent("Path of the Thief", "{3}{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2.
        Enchanted creature can't be blocked as long as you control a Gate.
    """)
    private val balconySpy = Cards.invent("Balcony Spy", "{3}{B}", "Creature — Vampire Rogue", """
        Flying
        When this creature enters, target player reveals cards from the top of their library until they reveal a land card, then puts those cards into their graveyard.
    """, "2/3")
    private val taintedGround = Cards.invent("Tainted Ground", "{1}{B}", "Enchantment — Aura", """
        Enchant land
        Enchanted land is a Swamp.
        Whenever enchanted land becomes tapped, its controller loses 2 life.
    """)
    private val corpseBarricade = Cards.invent("Corpse Barricade", "{2}{B}", "Creature — Zombie", """
        Defender
        Sacrifice another creature: This creature gains deathtouch until end of turn.
    """, "1/4")
    private val approachingDeath = Cards.invent("Approaching Death", "{B}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets -X/-X, where X is the number of creature cards in its controller's graveyard.
    """)
    private val consumeFlesh = Cards.invent("Consume Flesh", "{1}{B}", "Instant", "Target player sacrifices a creature of their choice, then gains life equal to that creature's toughness.")
    private val finalWish = Cards.invent("Final Wish", "{1}{B}", "Enchantment — Aura", """
        Enchant creature you control
        When enchanted creature dies, target player loses X life and you gain X life, where X is its power.
    """)
    private val gatewalkShade = Cards.invent("Gatewalk Shade", "{2}{B}", "Creature — Shade", """
        {B}: This creature gets +1/+1 until end of turn.
        Tap an untapped Gate you control: This creature gets +2/+2 until end of turn.
    """, "1/1")
    private val gruesomeSpectacle = Cards.invent("Gruesome Spectacle", "{2}{B}{B}", "Instant", "Destroy target nonartifact creature. Its controller mills cards equal to that creature's power.")
    private val dreadOfTheDim = Cards.invent("Dread of the Dim", "{4}{B}", "Creature — Horror", "{U}: This creature gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)", "3/4")
    private val rotInTheRanks = Cards.invent("Rot in the Ranks", "{B}", "Enchantment", "Creature tokens get -1/-1.")
    private val lethalGlare = Cards.invent("Lethal Glare", "{X}{B}", "Instant", "Destroy target creature with power X or less.")
    private val masterOfTheVoid = Cards.invent("Master of the Void", "{4}{B}{B}{B}", "Creature — Demon", """
        Flying
        Whenever this creature deals combat damage to a player, exile the top seven cards of that player's library, then put a creature card from among them onto the battlefield under your control.
    """, "7/7")
    private val ratLordSlumlord = Cards.invent("Rat Lord Slumlord", "{3}{B}{B}", "Creature — Ogre Rogue", """
        Whenever another nontoken creature dies, you may create a 1/1 black Rat creature token.
        Rats you control have deathtouch.
    """, "3/3")
    private val cryptPrimordial = Cards.invent("Crypt Primordial", "{5}{B}{B}", "Creature — Avatar", """
        Intimidate (This creature can't be blocked except by artifact creatures and/or creatures that share a color with it.)
        When this creature enters, for each opponent, you may put up to one target creature card from that player's graveyard onto the battlefield under your control.
    """, "5/4")
    private val gloomAlleyDenizen = Cards.invent("Gloom Alley Denizen", "{B}", "Creature — Vampire Rogue", "Whenever another black creature you control enters, target creature gains intimidate until end of turn. (It can't be blocked except by artifact creatures and/or creatures that share a color with it.)", "1/1")
    private val cobbleStreetThug = Cards.invent("Cobble Street Thug", "{2}{B}", "Creature — Human Warrior", "Whenever this creature becomes blocked, defending player discards a card.", "2/2")
    private val chokingSmog = Cards.invent("Choking Smog", "{4}{B}{B}", "Creature — Elemental", """
        Flying
        Creatures with flying your opponents control get -1/-1.
    """, "3/3")
    private val undercitySnitch = Cards.invent("Undercity Snitch", "{2}{B}", "Creature — Human Rogue", "{1}, Sacrifice a creature: Target player reveals cards from the top of their library until they reveal a land card, then puts those cards into their graveyard.", "2/3")
    private val wightOfPrecinctNine = Cards.invent("Wight of Precinct Nine", "{1}{B}", "Creature — Zombie", "This creature gets +1/+1 for each creature card in your opponents' graveyards.", "1/1")
    private val actOfBetrayal = Cards.invent("Act of Betrayal", "{2}{R}", "Sorcery", "Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn. (It can attack and {T} this turn.)")
    private val emberElemental = Cards.invent("Ember Elemental", "{3}{R}", "Creature — Elemental", "{X}{R}, {T}, Sacrifice this creature: It deals X damage to any target.", "2/2")
    private val sparkingPerimeter = Cards.invent("Sparking Perimeter", "{1}{R}", "Enchantment", "Tap an untapped Gate you control: This enchantment deals 1 damage to each opponent.")
    private val cinderBeast = Cards.invent("Cinder Beast", "{2}{R}", "Creature — Beast", "This creature can't attack or block alone.", "3/4")
    private val sixAlarmBlaze = Cards.invent("Six-Alarm Blaze", "{1}{R}{R}", "Enchantment", """
        Whenever a creature you control deals combat damage, put a blaze counter on this enchantment.
        Remove five blaze counters from this enchantment: It deals 5 damage to any target.
    """)
    private val furnaceStreetDenizen = Cards.invent("Furnace Street Denizen", "{R}", "Creature — Goblin Warrior", "Whenever another red creature you control enters, this creature gets +1/+0 until end of turn.", "1/1")
    private val defiantResistance = Cards.invent("Defiant Resistance", "{R}", "Instant", "Target blocking creature gets +3/+0 and gains first strike until end of turn.")
    private val hellwingTyrant = Cards.invent("Hellwing Tyrant", "{4}{R}{R}", "Creature — Dragon", """
        Flying, trample
        Whenever this creature deals combat damage to a player, gain control of all artifacts that player controls.
        At the beginning of your upkeep, if you control twenty or more artifacts, you win the game.
    """, "6/5")
    private val hellrouserGoblin = Cards.invent("Hellrouser Goblin", "{2}{R}", "Creature — Goblin Berserker", "Creatures you control have haste and attack each combat if able.", "2/2")
    private val seekingLightning = Cards.invent("Seeking Lightning", "{2}{R}{R}", "Instant", "Seeking Lightning deals 4 damage to target creature and each other creature with the same name as that creature.")
    private val wildSkills = Cards.invent("Wild Skills", "{1}{R}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +3/+0 and has menace. (It can't be blocked except by two or more creatures.)
    """)
    private val markedForRuin = Cards.invent("Marked for Ruin", "{3}{R}", "Sorcery", "Target creature an opponent controls blocks this turn if able. Untap that creature. Other creatures that player controls can't block this turn.")
    private val massRaid = Cards.invent("Mass Raid", "{1}{R}{R}", "Instant", "Mass Raid deals damage to any target equal to the number of creatures you control.")
    private val blazingPrimordial = Cards.invent("Blazing Primordial", "{5}{R}{R}", "Creature — Avatar", """
        Haste
        When this creature enters, for each opponent, gain control of up to one target creature that player controls until end of turn. Untap those creatures. They gain haste until end of turn.
    """, "6/4")
    private val streetMugging = Cards.invent("Street Mugging", "{R}", "Sorcery", "Street Mugging deals 2 damage to target creature. That creature can't block this turn.")
    private val skullCrusherStrike = Cards.invent("Skull Crusher Strike", "{1}{R}", "Instant", "Players can't gain life this turn. Damage can't be prevented this turn. Skull Crusher Strike deals 3 damage to target player or planeswalker.")
    private val structuralRuin = Cards.invent("Structural Ruin", "{5}{R}", "Sorcery", "Target player sacrifices an artifact and a land of their choice. Structural Ruin deals 2 damage to that player.")
    private val tinStreetBazaar = Cards.invent("Tin Street Bazaar", "{4}{R}", "Enchantment — Aura", """
        Enchant land
        Enchanted land has "{T}, Discard a card: Draw a card."
    """)
    private val thunderfistColossus = Cards.invent("Thunderfist Colossus", "{4}{R}", "Creature — Giant Soldier", "{W}: This creature gains vigilance until end of turn.", "4/4")
    private val packAuthority = Cards.invent("Pack Authority", "{1}{G}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature has hexproof and can't be blocked by more than one creature.
    """)
    private val discipleOfTheFirstbornWays = Cards.invent("Disciple of the Firstborn Ways", "{1}{G}", "Creature — Human Warrior", "{R}: This creature gains first strike until end of turn.", "2/2")
    private val colossalAdephage = Cards.invent("Colossal Adephage", "{5}{G}{G}", "Creature — Insect", """
        Trample
        Whenever this creature deals combat damage to a player, create a token that's a copy of this creature.
    """, "7/7")
    private val gatesideWatcher = Cards.invent("Gateside Watcher", "{1}{G}", "Creature — Elf Druid", "{T}: Untap target Gate.", "2/1")
    private val ivyRowDenizen = Cards.invent("Ivy Row Denizen", "{3}{G}", "Creature — Elf Warrior", "Whenever another green creature you control enters, put a +1/+1 counter on target creature.", "2/3")
    private val mimicSlime = Cards.invent("Mimic Slime", "{2}{G}", "Sorcery", "Create an X/X green Ooze creature token, where X is the greatest power among creatures you control.")
    private val returnToNaturesWay = Cards.invent("Return to Nature's Way", "{1}{G}", "Instant", "Destroy target artifact or enchantment.")
    private val predatorsBond = Cards.invent("Predator's Bond", "{2}{G}", "Instant", "Choose target creature you control. You gain life equal to that creature's power plus its toughness.")
    private val corrosionScarab = Cards.invent("Corrosion Scarab", "{4}{G}", "Creature — Insect", "Whenever this creature becomes blocked, you may destroy target artifact or enchantment defending player controls.", "4/5")
    private val spireStalker = Cards.invent("Spire Stalker", "{G}", "Creature — Elf Scout", "This creature can't be blocked except by creatures with flying or reach.", "1/1")
    private val verdantPrimordial = Cards.invent("Verdant Primordial", "{5}{G}{G}", "Creature — Avatar", """
        Reach
        When this creature enters, for each opponent, destroy target noncreature permanent that player controls. Search your library for up to X Forest cards, where X is the number of permanents destroyed this way, put those cards onto the battlefield tapped, then shuffle.
    """, "6/8")
    private val towerBulwark = Cards.invent("Tower Bulwark", "{1}{G}", "Instant", "Creatures you control get +0/+5 and gain reach until end of turn.")
    private val lushHaven = Cards.invent("Lush Haven", "{2}{G}", "Enchantment — Aura", """
        Enchant land
        When this Aura enters, you gain 2 life.
        Whenever enchanted land is tapped for mana, its controller adds an additional one mana of any color.
    """)
    private val wildwoodRenewal = Cards.invent("Wildwood Renewal", "{1}{G}", "Instant", "Return target creature card from your graveyard to your hand.")
    private val gate = Cards.invent("Ninth District Gate", null, "Land — Gate", "{T}: Add {W}.")
    private val multicolored = Cards.invent("Ninth District Envoy", "{W}{U}", "Creature — Human Advisor", "", "2/2")
    private val whiteBear = Cards.invent("Ninth District Lancer", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val blueBear = Cards.invent("Ninth District Scholar", "{1}{U}", "Creature — Human Wizard", "", "2/2")
    private val blackBear = Cards.invent("Ninth District Cutpurse", "{1}{B}", "Creature — Human Rogue", "", "2/2")
    private val redBear = Cards.invent("Ninth District Brawler", "{1}{R}", "Creature — Human Warrior", "", "2/2")
    private val tokenCall = Cards.invent("Ninth District Muster", "{W}", "Sorcery", "Create two 1/1 white Soldier creature tokens.")

    // ---- Chunk 06: white

    @Test
    fun skywardManeuverGivesFlyingAndFirstStrike() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(skywardManeuver)
        alice.casts(skywardManeuver, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Flying
            bear hasKeyword Keyword.FirstStrike
        }
    }

    @Test
    fun seraphicEdictExilesAnEnchantment() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(seraphicEdict)
        bob.battlefield(charm)
        alice.casts(seraphicEdict, target = charm)
        resolveAll()
        expect { bob.exile has charm }
    }

    @Test
    fun combatSeraphGrantsAChosenAbility() = scenario {
        alice.battlefield(combatSeraph, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        option("lifelink")
        resolveAll()
        expect { bear hasKeyword Keyword.Lifelink }
    }

    @Test
    fun plazaDenizenTapsForWhiteArrivals() = scenario {
        alice.battlefield("Plains", "Plains", plazaDenizen)
        alice.hand(whiteBear)
        bob.battlefield(ogre)
        alice.casts(whiteBear)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { ogre.tapped shouldBe true }
    }

    @Test
    fun debtcallersPulpitTapsACreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Island")
        alice.hand(debtcallersPulpit)
        bob.battlefield(ogre)
        alice.casts(debtcallersPulpit, target = "Island", paying = listOf("Plains", "Plains#1", "Plains#2", "Plains#3", "Plains#4"))
        resolveAll()
        alice.activates("Island", ability = 1, target = ogre)
        resolveAll()
        expect { ogre.tapped shouldBe true }
    }

    @Test
    fun obedientThrullRegenerates() = scenario {
        alice.battlefield(obedientThrull, "Swamp", "Swamp")
        alice.hand(doom)
        alice.activates(obedientThrull, paying = listOf("Swamp"))
        resolveAll()
        alice.casts(doom, target = obedientThrull, paying = listOf("Swamp#1"))
        resolveAll()
        expect { alice.battlefield has obedientThrull }
    }

    // CR 606.3, 306.5d: a planeswalker that becomes a creature
    @Test
    fun gideonParagonBecomesACreature() = scenario {
        alice.battlefield(gideonParagonOfTheCourts, counters = listOf(CounterKind.Loyalty to 4))
        alice.activates(gideonParagonOfTheCourts, ability = 1)
        resolveAll()
        expect {
            gideonParagonOfTheCourts.power shouldBe 4
            gideonParagonOfTheCourts hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun gideonParagonCountsTheirCreatures() = scenario {
        alice.battlefield(gideonParagonOfTheCourts, counters = listOf(CounterKind.Loyalty to 4))
        bob.battlefield(bear, ogre)
        alice.activates(gideonParagonOfTheCourts, ability = 0, target = "bob")
        resolveAll()
        expect { gideonParagonOfTheCourts.counters(CounterKind.Loyalty) shouldBe 7 }
    }

    // CR 509.1a: blocking any number of creatures
    @Test
    fun guardianOfTheOpenGateBlocksMany() = scenario {
        alice.battlefield(guardianOfTheOpenGate)
        bob.battlefield(bear, human)
        bob.library(ogre)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(bear, human)
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(guardianOfTheOpenGate to bear, guardianOfTheOpenGate to human)
        resolveAll()
        expect { guardianOfTheOpenGate.power shouldBe 5 }
    }

    // CR 702.16: protection from multicolored
    @Test
    fun unityWardProtectsFromMulticolored() = scenario {
        alice.battlefield("Plains", bear)
        alice.hand(unityWard)
        bob.battlefield(multicolored)
        alice.casts(unityWard, target = bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Protection }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(multicolored to bear) }
    }

    @Test
    fun gatekeepersResolveCountsGates() = scenario {
        alice.battlefield(gatekeepersResolve, gate, gate, bear)
        expect {
            bear.toughness shouldBe 4
            bear hasKeyword Keyword.Vigilance
        }
    }

    @Test
    fun sanctifiedMantlePumpsAndProtects() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(sanctifiedMantle)
        bob.battlefield(ogre)
        alice.casts(sanctifiedMantle, target = bear)
        resolveAll()
        expect { bear.power shouldBe 4 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to bear) }
    }

    @Test
    fun sentryWatchMakesTwoKnights() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(sentryWatch)
        alice.casts(sentryWatch)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Knight Token" } shouldBe 2 }
    }

    @Test
    fun radiantPrimordialExilesAndTheyGain() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(radiantPrimordial)
        bob.battlefield(giant)
        alice.casts(radiantPrimordial)
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        expect {
            bob.exile has giant
            bob.life shouldBe 25
        }
    }

    @Test
    fun homicideInquestLeavesSoldiers() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", ogre)
        alice.hand(homicideInquest, doom)
        alice.casts(homicideInquest, target = ogre, paying = listOf("Plains", "Plains#1"))
        resolveAll()
        alice.casts(doom, target = ogre)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Soldier Token" } shouldBe 3 }
    }

    @Test
    fun vanguardChargePumpsTheTeam() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(vanguardCharge)
        alice.casts(vanguardCharge)
        resolveAll()
        expect { bear.power shouldBe 4 }
    }

    // CR 615.1
    @Test
    fun shieldingPassagePreventsDamage() = scenario {
        alice.battlefield("Plains", "Mountain", bear)
        alice.hand(shieldingPassage, shock)
        alice.casts(shieldingPassage, target = bear)
        resolveAll()
        alice.casts(shock, target = bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun smiteTheBlockedDestroysABlockedAttacker() = scenario {
        alice.battlefield("Plains", giant)
        alice.hand(smiteTheBlocked)
        bob.battlefield(ogre)
        bob.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(giant)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(ogre to giant)
        alice.casts(smiteTheBlocked, target = giant)
        resolveAll()
        expect { alice.graveyard has giant }
    }

    @Test
    fun urbisWardenMakesAnAngel() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(urbisWarden)
        alice.casts(urbisWarden)
        resolveAll()
        expect { "Angel Token" hasKeyword Keyword.Flying }
    }

    @Test
    fun zarichiPantherGainsLife() = scenario {
        alice.battlefield(zarichiPanther, "Plains", "Plains")
        alice.activates(zarichiPanther)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    // ---- Chunk 07: blue

    @Test
    fun aetherwindReversalBouncesAttackers() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", bear)
        alice.hand(aetherwindReversal)
        bob.battlefield(ogre, giant)
        bob.library(human)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(ogre)
        bob.passes()
        alice.casts(aetherwindReversal)
        resolveAll()
        expect {
            bob.hand has ogre
            bob.battlefield has giant
        }
    }

    @Test
    fun crowdphobiaShrinksAndReturns() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(crowdphobia)
        bob.battlefield(giant)
        alice.casts(crowdphobia, target = giant)
        resolveAll()
        expect { giant.power shouldBe 0 }
        alice.activates(crowdphobia)
        resolveAll()
        expect { alice.hand has crowdphobia }
    }

    @Test
    fun tidalPrimordialCastsTheirSpell() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(tidalPrimordial)
        bob.graveyard(shock)
        alice.casts(tidalPrimordial)
        resolveAll()
        targetsIfAsked(shock)
        resolveAll()
        if (pending is Decision.May) may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(shock)
        targets("bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            bob.exile has shock
        }
    }

    @Test
    fun embraceTheInfiniteDrawsTheLibrary() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(embraceTheInfinite)
        alice.library(bear, ogre, giant)
        alice.casts(embraceTheInfinite)
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(bear, ogre)
            alice.library.names shouldBe listOf(giant)
        }
    }

    @Test
    fun frilledWatcherPumpsOnce() = scenario {
        alice.battlefield(frilledWatcher, "Forest", "Forest", "Forest", "Forest")
        alice.activates(frilledWatcher)
        resolveAll()
        expect { frilledWatcher.power shouldBe 3 }
        assertFailsWith<IllegalArgumentException> { alice.activates(frilledWatcher) }
    }

    @Test
    fun gridlockSnareTapsX() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(gridlockSnare)
        bob.battlefield(ogre, giant)
        alice.casts(gridlockSnare, x = 2, targets = listOf(ogre, giant))
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            giant.tapped shouldBe true
        }
    }

    @Test
    fun breachSpecialistSlipsThroughOnTheSecondSpell() = scenario {
        alice.battlefield(breachSpecialist, "Island", "Island")
        alice.hand(ponder, ponder)
        alice.library(bear, ogre)
        alice.casts(ponder)
        resolveAll()
        alice.casts(ponder)
        resolveAll()
        expect { breachSpecialist.power shouldBe 3 }
    }

    @Test
    fun vaultkeyRogueReturnsACreature() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", bear)
        alice.hand(vaultkeyRogue)
        alice.casts(vaultkeyRogue)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun leylineSpecterReturnsAfterDamage() = scenario {
        alice.battlefield(leylineSpecter)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(leylineSpecter)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            bob.life shouldBe 15
            alice.hand has leylineSpecter
        }
    }

    @Test
    fun skylineSpritePumps() = scenario {
        alice.battlefield(skylineSprite, "Island")
        alice.activates(skylineSprite)
        resolveAll()
        expect {
            skylineSprite.power shouldBe 2
            skylineSprite.toughness shouldBe 1
        }
    }

    @Test
    fun mindglassDrakeMillsAsItDies() = scenario {
        alice.battlefield("Swamp", mindglassDrake)
        alice.hand(doom)
        bob.library(bear, ogre, giant, human, idol, charm)
        alice.casts(doom, target = mindglassDrake)
        resolveAll()
        targets("bob")
        resolveAll()
        expect { bob.library.names shouldBe listOf(charm) }
    }

    // CR 701.19c: can't be regenerated
    @Test
    fun hybridConversionDestroysAndLeavesAFrogLizard() = scenario {
        alice.battlefield("Island")
        alice.hand(hybridConversion)
        bob.battlefield(giant)
        alice.casts(hybridConversion, target = giant)
        resolveAll()
        expect {
            bob.graveyard has giant
            "Frog Lizard Token".power shouldBe 3
        }
    }

    // CR 305.7-like: lands gain a basic land type and its mana ability
    @Test
    fun realmshaperAddsALandType() = scenario {
        alice.battlefield("Island", "Plains")
        alice.hand(realmshaper)
        alice.casts(realmshaper, paying = listOf("Island"))
        resolveAll()
        answer(Answer.SubtypeChoice(Subtype("Swamp")))
        expect { "Plains".subtypes.map { it.name }.contains("Swamp") shouldBe true }
    }

    @Test
    fun scholarsRowDenizenMillsForBlueArrivals() = scenario {
        alice.battlefield("Island", "Island", scholarsRowDenizen)
        alice.hand(blueBear)
        bob.library(bear, ogre, giant)
        alice.casts(blueBear)
        resolveAll()
        targets("bob")
        resolveAll()
        expect { bob.library.names shouldBe listOf(giant) }
    }

    @Test
    fun scatterWardCountersANoncreatureSpell() = scenario {
        bob.battlefield("Island")
        bob.hand(ponder)
        bob.library(bear)
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(scatterWard)
        alice.library(ogre)
        turn(active = bob)
        bob.casts(ponder)
        bob.passes()
        alice.casts(scatterWard, target = ponder)
        resolveAll()
        expect {
            bob.graveyard has ponder
            alice.hand has ogre
        }
    }

    @Test
    fun skywardGamesGivesFlying() = scenario {
        alice.battlefield("Island", "Island", "Plains", bear)
        alice.hand(skywardGames)
        alice.casts(skywardGames, target = "Plains", paying = listOf("Island", "Island#1"))
        resolveAll()
        alice.activates("Plains", ability = 1, target = bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Flying }
    }

    @Test
    fun spellFractureAsksForTheGreatestPower() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        alice.battlefield("Island", "Island", giant)
        alice.hand(spellFracture)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(spellFracture, target = ogre)
        resolveAll()
        if (pending is Decision.May) bob.answers(Answer.YesNo(false))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun utterlyLostTopsAPermanent() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(utterlyLost)
        bob.battlefield(idol)
        bob.library(bear)
        alice.casts(utterlyLost, target = idol)
        resolveAll()
        expect { bob.library.names.first() shouldBe idol }
    }

    @Test
    fun pathOfTheThiefIsUnblockableWithAGate() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", gate, bear)
        alice.hand(pathOfTheThief)
        bob.battlefield(giant)
        alice.casts(pathOfTheThief, target = bear, paying = listOf("Island", "Island#1", "Island#2", "Island#3"))
        resolveAll()
        expect { bear.power shouldBe 4 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
    }

    // ---- Chunk 08: black

    @Test
    fun balconySpyMillsToALand() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(balconySpy)
        bob.library(bear, ogre, "Forest", giant)
        alice.casts(balconySpy)
        resolveAll()
        targets("bob")
        resolveAll()
        expect { bob.library.names shouldBe listOf(giant) }
    }

    @Test
    fun taintedGroundHurtsWhenTapped() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(taintedGround)
        bob.battlefield("Forest")
        alice.casts(taintedGround, target = "Forest")
        resolveAll()
        expect { "Forest".subtypes.map { it.name } shouldBe listOf("Swamp") }
        alice.passes()
        bob.activates("Forest")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun corpseBarricadeGainsDeathtouch() = scenario {
        alice.battlefield(corpseBarricade, bear)
        alice.activates(corpseBarricade, choosing = listOf(bear))
        resolveAll()
        expect { corpseBarricade hasKeyword Keyword.Deathtouch }
    }

    @Test
    fun approachingDeathCountsCreatureCards() = scenario {
        alice.battlefield("Swamp")
        alice.hand(approachingDeath)
        bob.battlefield(ogre)
        bob.graveyard(bear, giant)
        alice.casts(approachingDeath, target = ogre)
        resolveAll()
        expect { ogre.power shouldBe 1 }
    }

    @Test
    fun consumeFleshSacrificesForLife() = scenario {
        alice.battlefield("Swamp", "Swamp", giant)
        alice.hand(consumeFlesh)
        alice.casts(consumeFlesh, target = "alice")
        resolveAll()
        expect {
            alice.graveyard has giant
            alice.life shouldBe 25
        }
    }

    @Test
    fun finalWishDrainsByPower() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", giant)
        alice.hand(finalWish, doom)
        alice.casts(finalWish, target = giant, paying = listOf("Swamp", "Swamp#1"))
        resolveAll()
        alice.casts(doom, target = giant)
        resolveAll()
        targets("bob")
        resolveAll()
        expect {
            bob.life shouldBe 15
            alice.life shouldBe 25
        }
    }

    @Test
    fun gatewalkShadeTapsAGate() = scenario {
        alice.battlefield(gatewalkShade, gate)
        alice.activates(gatewalkShade, ability = 1, choosing = listOf(gate))
        resolveAll()
        expect { gatewalkShade.power shouldBe 3 }
    }

    @Test
    fun gruesomeSpectacleDestroysAndMills() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(gruesomeSpectacle)
        bob.battlefield(ogre)
        bob.library(bear, human, giant, idol)
        alice.casts(gruesomeSpectacle, target = ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            bob.library.names shouldBe listOf(idol)
        }
    }

    @Test
    fun dreadOfTheDimGainsHexproof() = scenario {
        alice.battlefield(dreadOfTheDim, "Island")
        alice.activates(dreadOfTheDim)
        resolveAll()
        expect { dreadOfTheDim hasKeyword Keyword.Hexproof }
    }

    @Test
    fun rotInTheRanksShrinksTokens() = scenario {
        alice.battlefield("Plains", rotInTheRanks)
        alice.hand(tokenCall)
        alice.casts(tokenCall)
        resolveAll()
        expect { alice.battlefield lacks "Soldier Token" }
    }

    @Test
    fun lethalGlareByPower() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(lethalGlare)
        bob.battlefield(ogre)
        alice.casts(lethalGlare, x = 3, target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun masterOfTheVoidStealsACreatureCard() = scenario {
        alice.battlefield(masterOfTheVoid)
        bob.library(bear, "Forest", ogre, idol, charm, human, giant, flyer)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(masterOfTheVoid)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect {
            giant.controller shouldBe alice.id
            bob.exile.size shouldBe 6
        }
    }

    @Test
    fun ratLordSlumlordMakesDeadlyRats() = scenario {
        alice.battlefield("Swamp", ratLordSlumlord)
        alice.hand(doom)
        bob.battlefield(ogre)
        alice.casts(doom, target = ogre)
        resolveAll()
        may(true)
        resolveAll()
        expect { "Rat Token" hasKeyword Keyword.Deathtouch }
    }

    @Test
    fun cryptPrimordialReanimatesTheirs() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(cryptPrimordial)
        bob.graveyard(giant)
        alice.casts(cryptPrimordial)
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        if (pending is Decision.May) may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(giant)
        resolveAll()
        expect { giant.controller shouldBe alice.id }
    }

    @Test
    fun gloomAlleyDenizenGivesIntimidate() = scenario {
        alice.battlefield("Swamp", "Swamp", gloomAlleyDenizen)
        alice.hand(blackBear)
        alice.casts(blackBear)
        resolveAll()
        targets(blackBear)
        resolveAll()
        expect { blackBear hasKeyword Keyword.Intimidate }
    }

    @Test
    fun cobbleStreetThugMakesBlockersDiscard() = scenario {
        alice.battlefield(cobbleStreetThug)
        bob.battlefield(ogre)
        bob.hand(giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cobbleStreetThug)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(ogre to cobbleStreetThug)
        resolveAll()
        expect { bob.graveyard has giant }
    }

    @Test
    fun chokingSmogShrinksTheirFlyers() = scenario {
        alice.battlefield(chokingSmog)
        bob.battlefield(flyer)
        expect { flyer.power shouldBe 1 }
    }

    @Test
    fun undercitySnitchSacrificesToMill() = scenario {
        alice.battlefield(undercitySnitch, bear, "Swamp")
        bob.library(ogre, "Forest", giant)
        alice.activates(undercitySnitch, target = "bob", choosing = listOf(bear))
        resolveAll()
        expect { bob.library.names shouldBe listOf(giant) }
    }

    @Test
    fun wightOfPrecinctNineCountsTheirDead() = scenario {
        alice.battlefield(wightOfPrecinctNine)
        bob.graveyard(bear, ogre, idol)
        expect { wightOfPrecinctNine.power shouldBe 3 }
    }

    // ---- Chunk 09: red

    @Test
    fun actOfBetrayalSteals() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(actOfBetrayal)
        bob.battlefield(giant, tapped = true)
        alice.casts(actOfBetrayal, target = giant)
        resolveAll()
        expect {
            giant.controller shouldBe alice.id
            giant.tapped shouldBe false
        }
    }

    @Test
    fun emberElementalSacrificesForX() = scenario {
        alice.battlefield(emberElemental, "Mountain", "Mountain", "Mountain")
        alice.activates(emberElemental, x = 2, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun sparkingPerimeterTapsAGate() = scenario {
        alice.battlefield(sparkingPerimeter, gate)
        alice.activates(sparkingPerimeter, choosing = listOf(gate))
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    // CR 508.1c: can't attack alone
    @Test
    fun cinderBeastCantAttackAlone() = scenario {
        alice.battlefield(cinderBeast, bear)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(cinderBeast) }
        alice.attacks(cinderBeast, bear)
    }

    @Test
    fun sixAlarmBlazeChargesAndFires() = scenario {
        alice.battlefield(sixAlarmBlaze, counters = listOf(CounterKind.Named("blaze") to 5))
        alice.activates(sixAlarmBlaze, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 15 }
    }

    @Test
    fun sixAlarmBlazeCountsCombatDamage() = scenario {
        alice.battlefield(sixAlarmBlaze, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect { sixAlarmBlaze.counters(CounterKind.Named("blaze")) shouldBe 1 }
    }

    @Test
    fun furnaceStreetDenizenGrowsForRedArrivals() = scenario {
        alice.battlefield("Mountain", "Mountain", furnaceStreetDenizen)
        alice.hand(redBear)
        alice.casts(redBear)
        resolveAll()
        expect { furnaceStreetDenizen.power shouldBe 2 }
    }

    @Test
    fun defiantResistanceArmsABlocker() = scenario {
        alice.battlefield("Mountain", bear)
        alice.hand(defiantResistance)
        bob.battlefield(ogre)
        bob.library(human)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(ogre)
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(bear to ogre)
        bob.passes()
        alice.casts(defiantResistance, target = bear)
        resolveAll()
        advanceTo(TurnStep.EndOfCombat)
        expect {
            bob.graveyard has ogre
            alice.battlefield has bear
        }
    }

    @Test
    fun hellwingTyrantTakesTheirArtifacts() = scenario {
        alice.battlefield(hellwingTyrant)
        bob.battlefield(idol)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hellwingTyrant)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect { idol.controller shouldBe alice.id }
    }

    // CR 508.1d: attacks each combat if able
    @Test
    fun hellrouserGoblinHastesAndForcesAttacks() = scenario {
        alice.battlefield(hellrouserGoblin)
        alice.battlefield(bear, sick = true)
        expect { bear hasKeyword Keyword.Haste }
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(hellrouserGoblin) }
    }

    @Test
    fun seekingLightningHitsTheNamesakes() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(seekingLightning)
        bob.battlefield(ogre, ogre, giant)
        alice.casts(seekingLightning, target = ogre)
        resolveAll()
        expect {
            bob.graveyard.size shouldBe 2
            bob.battlefield has giant
        }
    }

    @Test
    fun wildSkillsPumpsWithMenace() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(wildSkills)
        alice.casts(wildSkills, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear hasKeyword Keyword.Menace
        }
    }

    // CR 509.1c: blocks this turn if able
    @Test
    fun markedForRuinForcesOneBlocker() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", bear)
        alice.hand(markedForRuin)
        bob.battlefield(ogre, giant)
        alice.casts(markedForRuin, target = ogre)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
        bob.blocks(ogre to bear)
    }

    @Test
    fun massRaidCountsCreatures() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear, human, ogre)
        alice.hand(massRaid)
        alice.casts(massRaid, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun blazingPrimordialStealsForTheTurn() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(blazingPrimordial)
        bob.battlefield(giant, tapped = true)
        alice.casts(blazingPrimordial)
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        expect {
            giant.controller shouldBe alice.id
            giant hasKeyword Keyword.Haste
        }
    }

    @Test
    fun streetMuggingBurnsAndStopsABlocker() = scenario {
        alice.battlefield("Mountain", bear)
        alice.hand(streetMugging)
        bob.battlefield(giant)
        alice.casts(streetMugging, target = giant)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
    }

    @Test
    fun skullCrusherStrikeStopsLifeGain() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(skullCrusherStrike)
        bob.battlefield("Plains")
        bob.hand(heal)
        alice.casts(skullCrusherStrike, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
        alice.passes()
        bob.casts(heal)
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun structuralRuinTakesAnArtifactAndALand() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(structuralRuin)
        bob.battlefield(idol, "Forest")
        alice.casts(structuralRuin, target = "bob")
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses(idol)
        if (pending is Decision.ChooseObjects) bob.chooses("Forest")
        resolveAll()
        expect {
            bob.graveyard has idol
            bob.graveyard has "Forest"
            bob.life shouldBe 18
        }
    }

    @Test
    fun tinStreetBazaarLoots() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Island")
        alice.hand(tinStreetBazaar, giant)
        alice.library(bear)
        alice.casts(tinStreetBazaar, target = "Island", paying = listOf("Mountain", "Mountain#1", "Mountain#2", "Mountain#3", "Mountain#4"))
        resolveAll()
        alice.activates("Island", ability = 1, choosing = listOf(giant))
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun thunderfistColossusGainsVigilance() = scenario {
        alice.battlefield(thunderfistColossus, "Plains")
        alice.activates(thunderfistColossus)
        resolveAll()
        expect { thunderfistColossus hasKeyword Keyword.Vigilance }
    }

    // ---- Chunk 10: green

    @Test
    fun packAuthorityShieldsAndLimitsBlocks() = scenario {
        alice.battlefield("Forest", "Forest", ogre)
        alice.hand(packAuthority)
        bob.battlefield(bear, human)
        alice.casts(packAuthority, target = ogre)
        resolveAll()
        expect { ogre hasKeyword Keyword.Hexproof }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ogre)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(bear to ogre, human to ogre) }
    }

    @Test
    fun discipleOfTheFirstbornWaysGainsFirstStrike() = scenario {
        alice.battlefield(discipleOfTheFirstbornWays, "Mountain")
        alice.activates(discipleOfTheFirstbornWays)
        resolveAll()
        expect { discipleOfTheFirstbornWays hasKeyword Keyword.FirstStrike }
    }

    @Test
    fun colossalAdephageCopiesOnDamage() = scenario {
        alice.battlefield(colossalAdephage)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(colossalAdephage)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect { alice.battlefield.names.count { it == colossalAdephage } shouldBe 2 }
    }

    @Test
    fun gatesideWatcherUntapsAGate() = scenario {
        alice.battlefield(gatesideWatcher)
        alice.battlefield(gate, tapped = true)
        alice.activates(gatesideWatcher, target = gate)
        resolveAll()
        expect { gate.tapped shouldBe false }
    }

    @Test
    fun ivyRowDenizenCountersForGreenArrivals() = scenario {
        alice.battlefield("Forest", "Forest", ivyRowDenizen)
        alice.hand(bear)
        alice.casts(bear)
        resolveAll()
        targets(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun mimicSlimeMakesTheBiggestOoze() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", giant)
        alice.hand(mimicSlime)
        alice.casts(mimicSlime)
        resolveAll()
        expect { "Ooze Token".power shouldBe 5 }
    }

    @Test
    fun returnToNaturesWayDestroysAnArtifact() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(returnToNaturesWay)
        bob.battlefield(idol)
        alice.casts(returnToNaturesWay, target = idol)
        resolveAll()
        expect { bob.graveyard has idol }
    }

    @Test
    fun predatorsBondGainsPowerPlusToughness() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", giant)
        alice.hand(predatorsBond)
        alice.casts(predatorsBond, target = giant)
        resolveAll()
        expect { alice.life shouldBe 30 }
    }

    @Test
    fun corrosionScarabDestroysWhenBlocked() = scenario {
        alice.battlefield(corrosionScarab)
        bob.battlefield(ogre, idol)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(corrosionScarab)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(ogre to corrosionScarab)
        targetsIfAsked(idol)
        resolveAll()
        may(true)
        resolveAll()
        expect { bob.graveyard has idol }
    }

    @Test
    fun spireStalkerOnlyBlockedByFlyers() = scenario {
        alice.battlefield(spireStalker)
        bob.battlefield(ogre, flyer)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(spireStalker)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to spireStalker) }
        bob.blocks(flyer to spireStalker)
    }

    @Test
    fun verdantPrimordialDestroysAndRamps() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(verdantPrimordial)
        alice.library(bear, "Forest")
        bob.battlefield(idol)
        alice.casts(verdantPrimordial)
        resolveAll()
        targetsIfAsked(idol)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Forest")
        resolveAll()
        expect {
            bob.graveyard has idol
            alice.battlefield.names.count { it == "Forest" } shouldBe 8
        }
    }

    @Test
    fun towerBulwarkToughensTheTeam() = scenario {
        alice.battlefield("Forest", "Forest", bear)
        alice.hand(towerBulwark)
        alice.casts(towerBulwark)
        resolveAll()
        expect {
            bear.toughness shouldBe 7
            bear hasKeyword Keyword.Reach
        }
    }

    @Test
    fun lushHavenGainsAndAddsMana() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Island")
        alice.hand(lushHaven)
        alice.casts(lushHaven, target = "Island", paying = listOf("Forest", "Forest#1", "Forest#2"))
        resolveAll()
        expect { alice.life shouldBe 22 }
        alice.activates("Island")
        resolveAll()
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Red))
        resolveAll()
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun wildwoodRenewalReturnsACreature() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(wildwoodRenewal)
        alice.graveyard(giant)
        alice.casts(wildwoodRenewal, target = giant)
        resolveAll()
        expect { alice.hand has giant }
    }
}
