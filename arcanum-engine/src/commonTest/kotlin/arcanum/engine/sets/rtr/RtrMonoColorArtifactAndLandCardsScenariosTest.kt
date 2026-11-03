package arcanum.engine.sets.rtr

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.Rtr.bear
import arcanum.engine.sets.rtr.Rtr.flyer
import arcanum.engine.sets.rtr.Rtr.giant
import arcanum.engine.sets.rtr.Rtr.human
import arcanum.engine.sets.rtr.Rtr.idol
import arcanum.engine.sets.rtr.Rtr.ogre
import arcanum.engine.sets.rtr.Rtr.p1p1
import arcanum.engine.sets.rtr.Rtr.ponder
import arcanum.engine.sets.rtr.Rtr.shock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Return to Ravnica's blue, black, red and green cards, Izzet creatures, artifacts and lands in play (parser chunks 07–12). */
class RtrMonoColorArtifactAndLandCardsScenariosTest {
    private val canalSteed = Cards.invent("Canal Steed", "{3}{U}", "Creature — Beast", "{2}{U}, {T}: Target creature gets -2/-0 until end of turn.", "3/1")
    private val seepingDeluge = Cards.invent("Seeping Deluge", "{1}{U}", "Enchantment — Aura", """
        Enchant land
        Whenever enchanted land becomes tapped, its controller mills three cards.
    """)
    private val phantomCoinage = Cards.invent("Phantom Coinage", "{5}{U}", "Enchantment", "At the beginning of your upkeep, you may exchange control of this enchantment and target permanent you neither own nor control.")
    private val alleywayMessenger = Cards.invent("Alleyway Messenger", "{1}{U}", "Creature — Vedalken", "Whenever this creature deals combat damage to a player, that player mills that many cards.", "2/1")
    private val thresholdWarden = Cards.invent("Threshold Warden", "{1}{U}", "Creature — Homunculus", """
        Defender
        {2}{U}, {T}: Target player mills X cards, where X is the number of creatures you control with defender.
    """, "0/4")
    private val flashOfInsightUnbound = Cards.invent("Flash of Insight Unbound", "{3}{U}", "Instant", "Target player draws two cards.")
    private val numbingClutch = Cards.invent("Numbing Clutch", "{2}{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature doesn't untap during its controller's untap step.
    """)
    private val glyphwing = Cards.invent("Glyphwing", "{3}{U}", "Creature — Bird", """
        Flying
        When this creature dies, draw a card.
    """, "2/2")
    private val pilfererOfWhispers = Cards.invent("Pilferer of Whispers", "{2}{U}", "Creature — Human Rogue", "Whenever this creature deals combat damage to a player, draw a card.", "2/2")
    private val spireDrake = Cards.invent("Spire Drake", "{2}{U}", "Creature — Drake", """
        Flying
        {W}: This creature gets +0/+1 until end of turn.
    """, "1/1")
    private val nullcaster = Cards.invent("Nullcaster", "{4}{U}", "Creature — Human Wizard", "When this creature enters, you may return target creature to its owner's hand.", "1/4")
    private val paidAssassination = Cards.invent("Paid Assassination", "{4}{B}{B}", "Sorcery", "Destroy target creature. Its controller discards a card.")
    private val pyreRitual = Cards.invent("Pyre Ritual", "{B}", "Instant", """
        Exile target card from a graveyard.
        Draw a card.
    """)
    private val wickedDelight = Cards.invent("Wicked Delight", "{B}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+1 and has "{R}: This creature gains trample until end of turn."
    """)
    private val hurlFestival = Cards.invent("Hurl Festival", "{3}{B}", "Instant", """
        As an additional cost to cast this spell, sacrifice a creature.
        Destroy target creature. Its controller loses 2 life.
    """)
    private val ogreEscapee = Cards.invent("Ogre Escapee", "{3}{B}", "Creature — Ogre Rogue", """
        Defender
        This creature can attack as though it didn't have defender as long as you control a Gate.
    """, "4/4")
    private val swarmingRat = Cards.invent("Swarming Rat", "{1}{B}", "Creature — Rat", """
        Swarming Rat's power and toughness are each equal to the number of Rats you control.
        {2}{B}, Discard a card: Create a token that's a copy of this creature.
    """, "*/*")
    private val menacingShade = Cards.invent("Menacing Shade", "{2}{B}{B}", "Creature — Insect Shade", "{1}{B}: This creature gets +2/+2 until end of turn.", "1/1")
    private val howlingTorment = Cards.invent("Howling Torment", "{B}", "Enchantment", "At the beginning of each opponent's upkeep, if that player has one or fewer cards in hand, they lose 3 life.")
    private val tenementReaper = Cards.invent("Tenement Reaper", "{3}{B}", "Creature — Horror", "When this creature enters, each player sacrifices a creature of their choice.", "4/2")
    private val festeringGash = Cards.invent("Festering Gash", "{2}{B}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets -2/-2.
        At the beginning of the upkeep of enchanted creature's controller, that player loses 2 life.
    """)
    private val alehouseGrifter = Cards.invent("Alehouse Grifter", "{1}{B}", "Creature — Human Rogue", "{T}, Pay 3 life: Flip a coin. If you win the flip, you gain 6 life.", "1/2")
    private val finalToll = Cards.invent("Final Toll", "{1}{B}", "Instant", "Destroy target monocolored creature.")
    private val subterraneanContacts = Cards.invent("Subterranean Contacts", "{1}{B}{B}", "Enchantment — Aura", """
        Enchant land
        Enchanted land has "{T}, Pay 1 life: Draw a card."
    """)
    private val obliteratingBlaze = Cards.invent("Obliterating Blaze", "{1}{R}{R}", "Instant", "Obliterating Blaze deals 3 damage to any target. If a creature dealt damage this way would die this turn, exile it instead.")
    private val cinderZealot = Cards.invent("Cinder Zealot", "{R}{R}", "Creature — Human Warrior", """
        First strike, haste
        Whenever a player casts a spell from a graveyard, this creature deals 3 damage to that player.
    """, "2/2")
    private val rambasher = Cards.invent("Rambasher", "{4}{R}", "Creature — Beast", "When this creature enters, you may destroy target artifact.", "3/3")
    private val forgebreathLizard = Cards.invent("Forgebreath Lizard", "{R}", "Creature — Lizard", "{1}{R}: This creature gets +1/+0 until end of turn.", "1/1")
    private val shatteringImpact = Cards.invent("Shattering Impact", "{5}{R}", "Instant", "Shattering Impact deals 5 damage to any target.")
    private val goblinMuster = Cards.invent("Goblin Muster", "{3}{R}{R}", "Sorcery", "Create four 1/1 red Goblin creature tokens.")
    private val gutterspark = Cards.invent("Gutterspark", "{2}{R}", "Creature — Goblin Shaman", "Whenever you cast an instant or sorcery spell, this creature deals 2 damage to each opponent.", "2/2")
    private val catapultCrew = Cards.invent("Catapult Crew", "{2}{R}", "Creature — Goblin Warrior", """
        Defender
        {T}: This creature deals 1 damage to each opponent.
        Whenever you cast a multicolored spell, untap this creature.
    """, "0/4")
    private val questForWings = Cards.invent("Quest for Wings", "{1}{R}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2 and has "{U}: This creature gains flying until end of turn."
    """)
    private val blazeconvergence = Cards.invent("Blazeconvergence", "{4}{R}", "Enchantment", "Whenever you cast a multicolored spell, this enchantment deals 2 damage to any target.")
    private val speedwayFrenzy = Cards.invent("Speedway Frenzy", "{R}", "Enchantment — Aura", """
        Enchant land
        Enchanted land has "{T}: Target creature gains haste until end of turn."
    """)
    private val siftTheRubble = Cards.invent("Sift the Rubble", "{4}{R}", "Sorcery", "Destroy target land. Create a 1/1 red Goblin creature token.")
    private val turncoatUrge = Cards.invent("Turncoat Urge", "{3}{R}", "Sorcery", "Gain control of target creature until end of turn. Untap that creature. Until end of turn, it gets +2/+0 and gains haste.")
    private val utmostHellkite = Cards.invent("Utmost Hellkite", "{6}{R}{R}", "Creature — Dragon", """
        Flying
        Whenever a Dragon you control attacks, create a 6/6 red Dragon creature token with flying.
    """, "6/6")
    private val viashinoHustler = Cards.invent("Viashino Hustler", "{2}{R}", "Creature — Lizard Rogue", "When this creature enters, you may discard a card. If you do, draw a card.", "2/1")
    private val skywardAmbush = Cards.invent("Skyward Ambush", "{2}{G}", "Instant", "Destroy target creature with flying. You gain 2 life.")
    private val axewoodWarden = Cards.invent("Axewood Warden", "{2}{G}", "Creature — Human Druid", """
        Defender
        {T}: Add X mana in any combination of colors, where X is the number of creatures you control with defender.
    """, "0/3")
    private val centaurHarbinger = Cards.invent("Centaur Harbinger", "{G}", "Creature — Elf Scout", "{2}{G}, Sacrifice this creature: Create a 3/3 green Centaur creature token.", "1/1")
    private val anthemOfStrength = Cards.invent("Anthem of Strength", "{3}{G}", "Instant", "Until end of turn, target creature gets +1/+1 for each creature you control and gains trample.")
    private val mortalInheritance = Cards.invent("Mortal Inheritance", "{5}{G}", "Enchantment", "Whenever a creature you control dies, put X +1/+1 counters on target creature you control, where X is the power of the creature that died.")
    private val gateclimberVine = Cards.invent("Gateclimber Vine", "{1}{G}", "Creature — Plant", """
        Defender
        When this creature enters, you may search your library for a basic land card or a Gate card, reveal it, put it into your hand, then shuffle.
    """, "0/3")
    private val devouringOoze = Cards.invent("Devouring Ooze", "{4}{G}", "Creature — Ooze", "{G}, Sacrifice another creature: Put a +1/+1 counter on this creature.", "3/3")
    private val manaBlossom = Cards.invent("Mana Blossom", "{X}{G}", "Enchantment", """
        This enchantment enters with X charge counters on it.
        Remove a charge counter from this enchantment: Add one mana of any color. Activate only once each turn.
        At the beginning of your upkeep, if this enchantment has no charge counters on it, return it to its owner's hand.
    """)
    private val feralSurge = Cards.invent("Feral Surge", "{1}{G}", "Instant", "Target creature gets +2/+2 until end of turn. Untap that creature.")
    private val scanTheSkyline = Cards.invent("Scan the Skyline", "{3}{G}", "Sorcery", "Search your library for up to three basic land cards, reveal them, put them into your hand, then shuffle.")
    private val oozeSculpting = Cards.invent("Ooze Sculpting", "{X}{G}", "Sorcery", "Create an X/X green Ooze creature token.")
    private val stonetrailCrocodile = Cards.invent("Stonetrail Crocodile", "{2}{G}", "Creature — Crocodile", "{2}{B}: This creature gains lifelink until end of turn. (Damage dealt by this creature also causes you to gain that much life.)", "2/3")
    private val feralPackmaster = Cards.invent("Feral Packmaster", "{2}{G}", "Creature — Human Shaman", "Whenever this creature attacks, each other creature you control gets +X/+X until end of turn, where X is this creature's power.", "1/1")
    private val sparkcoilOddity = Cards.invent("Sparkcoil Oddity", "{U/R}", "Creature — Weird", "Whenever you cast an instant or sorcery spell, this creature gets +1/+1 until end of turn. Untap it.", "1/1")
    private val rimeflareOddity = Cards.invent("Rimeflare Oddity", "{U/R}{U/R}", "Creature — Weird", "{U/R}: This creature gets +1/-1 until end of turn.", "1/4")
    private val goblinVoltsmith = Cards.invent("Goblin Voltsmith", "{U}{R}", "Creature — Goblin Wizard", "Instant and sorcery spells you cast cost {1} less to cast.", "2/2")
    private val thunderclapDragon = Cards.invent("Thunderclap Dragon", "{3}{U}{R}", "Creature — Dragon", """
        Flying, haste
        You may cast sorcery spells as though they had flash. (You may cast them any time you could cast an instant.)
    """, "4/4")
    private val staticSparkMage = Cards.invent("Static Spark-Mage", "{1}{U}{R}", "Creature — Human Wizard", """
        Flash (You may cast this spell any time you could cast an instant.)
        Haste
        {T}: This creature deals 1 damage to target creature and each other creature with the same name as that creature.
    """, "0/3")
    private val volatileAlchemist = Cards.invent("Volatile Alchemist", "{3}{U}{R}", "Creature — Human Wizard", """
        {U}, {T}: Draw two cards.
        {R}, {T}, Discard a card: This creature deals damage to target creature equal to the discarded card's mana value.
    """, "3/3")
    private val vazrekDragonSavant = Cards.invent("Vazrek, Dragon Savant", "{2}{U}{U}{R}{R}", "Legendary Creature — Dragon Wizard", """
        Flying
        Whenever Vazrek deals damage to a player, you may draw a card.
        {U}{R}: Vazrek deals 1 damage to any target.
    """, "5/5")
    private val laboratoryGuildmage = Cards.invent("Laboratory Guildmage", "{U}{R}", "Creature — Human Wizard", """
        {1}{U}{R}: Draw a card, then discard a card.
        {2}{U}{R}: Copy target instant or sorcery spell you control. You may choose new targets for the copy.
    """, "2/2")
    private val undercityRunestone = Cards.invent("Undercity Runestone", "{3}", "Artifact", """
        {T}: Add {B} or {G}.
        {B}{G}: This artifact becomes a 2/2 black and green Insect artifact creature with deathtouch until end of turn.
    """)
    private val foundryRunestone = Cards.invent("Foundry Runestone", "{3}", "Artifact", """
        {T}: Add {U} or {R}.
        {U}{R}: Until end of turn, this artifact becomes a 2/1 blue and red Elemental artifact creature.
        Whenever this artifact deals combat damage to a player, you may draw a card. If you do, discard a card.
    """)
    private val carnivalRunestone = Cards.invent("Carnival Runestone", "{3}", "Artifact", """
        {T}: Add {B} or {R}.
        {B}{R}: This artifact becomes a 3/1 black and red Devil artifact creature with first strike until end of turn.
    """)
    private val conclaveRunestone = Cards.invent("Conclave Runestone", "{3}", "Artifact", """
        {T}: Add {G} or {W}.
        {G}{W}: This artifact becomes a 3/3 green and white Wolf artifact creature until end of turn.
    """)
    private val prismaticLamp = Cards.invent("Prismatic Lamp", "{3}", "Artifact", """
        Lands you control have "{T}: Add one mana of any color."
        {T}: Add one mana of any color.
    """)
    private val citizensBlade = Cards.invent("Citizen's Blade", "{1}", "Artifact — Equipment", """
        Equipped creature gets +1/+0 for each of its colors.
        Equip {1}
    """)
    private val ledgerShredderEngine = Cards.invent("Ledger Shredder Engine", "{1}", "Artifact", """
        {T}: Target player mills a card. (They put the top card of their library into their graveyard.)
        {5}, {T}, Sacrifice this artifact: Return target card from your graveyard to your hand.
    """)
    private val gutterSweeper = Cards.invent("Gutter Sweeper", "{6}", "Artifact Creature — Construct", "Whenever this creature attacks, destroy all Auras attached to target land.", "4/6")
    private val unstableContraption = Cards.invent("Unstable Contraption", "{4}", "Artifact Creature — Construct", """
        Trample
        This creature attacks each combat if able.
        Whenever this creature is dealt damage, flip a coin. If you lose the flip, sacrifice this creature.
        When this creature dies, flip a coin. If you lose the flip, it deals 4 damage to each creature and each player.
    """, "4/4")
    private val senateGate = Cards.invent("Senate Gate", null, "Land — Gate", """
        This land enters tapped.
        {T}: Add {W} or {U}.
    """)
    private val undercityGate = Cards.invent("Undercity Gate", null, "Land — Gate", """
        This land enters tapped.
        {T}: Add {B} or {G}.
    """)
    private val foundryGate = Cards.invent("Foundry Gate", null, "Land — Gate", """
        This land enters tapped.
        {T}: Add {U} or {R}.
    """)
    private val carnivalGate = Cards.invent("Carnival Gate", null, "Land — Gate", """
        This land enters tapped.
        {T}: Add {B} or {R}.
    """)
    private val conclaveGate = Cards.invent("Conclave Gate", null, "Land — Gate", """
        This land enters tapped.
        {T}: Add {G} or {W}.
    """)
    private val mossyCrypt = Cards.invent("Mossy Crypt", null, "Land — Swamp Forest", """
        ({T}: Add {B} or {G}.)
        As this land enters, you may pay 2 life. If you don't, it enters tapped.
    """)
    private val geyserChasm = Cards.invent("Geyser Chasm", null, "Land — Island Mountain", """
        ({T}: Add {U} or {R}.)
        As this land enters, you may pay 2 life. If you don't, it enters tapped.
    """)
    private val gladeOfTheSentinel = Cards.invent("Glade of the Sentinel", null, "Land", """
        {T}: Add {C}.
        {3}{G}{W}, {T}, Tap two untapped creatures you control, Sacrifice this land: Create an 8/8 green and white Elemental creature token with vigilance.
    """)
    private val smugglersAlley = Cards.invent("Smuggler's Alley", null, "Land", """
        {T}: Add {C}.
        {4}, {T}: Target creature can't be blocked this turn.
    """)
    private val interguildConcourse = Cards.invent("Interguild Concourse", null, "Land", """
        This land enters tapped.
        When this land enters, sacrifice it unless you pay {1}.
        {T}: Add one mana of any color.
    """)
    private val flashbackSpell = Cards.invent("Tenth Street Recall", "{U}", "Sorcery", """
        Draw a card.
        Flashback {2}{U}
    """)
    private val landAura = Cards.invent("Tenth Street Overgrowth", "{G}", "Enchantment — Aura", "Enchant land")
    private val gate = Cards.invent("Tenth Street Gate", null, "Land — Gate", "{T}: Add {W}.")
    private val wall = Cards.invent("Tenth Street Wall", "{1}{U}", "Creature — Wall", "Defender", "0/4")
    private val redBlue = Cards.invent("Tenth Street Spellcrafter", "{U}{R}", "Creature — Human Wizard", "", "2/2")
    private val coinWin: (arcanum.engine.decision.RandomKind) -> Answer = { Answer.CoinFlip(true) }

    // ---- Chunk 07: blue

    @Test
    fun canalSteedWeakensACreature() = scenario {
        alice.battlefield(canalSteed, "Island", "Island", "Island")
        bob.battlefield(ogre)
        alice.activates(canalSteed, target = ogre)
        resolveAll()
        expect { ogre.power shouldBe 1 }
    }

    // CR 603.2: "becomes tapped"
    @Test
    fun seepingDelugeMillsWhenTheLandTaps() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(seepingDeluge)
        bob.battlefield("Forest")
        bob.library(bear, ogre, giant, human)
        alice.casts(seepingDeluge, target = "Forest")
        resolveAll()
        alice.passes()
        bob.activates("Forest")
        resolveAll()
        expect { bob.library.names shouldBe listOf(human) }
    }

    // CR 701.12b: exchanging control
    @Test
    fun phantomCoinageExchangesControl() = scenario {
        alice.battlefield(phantomCoinage)
        alice.library(bear)
        bob.battlefield(giant)
        bob.library(bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        targetsIfAsked(giant)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            giant.controller shouldBe alice.id
            phantomCoinage.controller shouldBe bob.id
        }
    }

    @Test
    fun alleywayMessengerMillsByDamage() = scenario {
        alice.battlefield(alleywayMessenger)
        bob.library(bear, ogre, giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(alleywayMessenger)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.library.names shouldBe listOf(giant) }
    }

    @Test
    fun thresholdWardenMillsPerDefender() = scenario {
        alice.battlefield(thresholdWarden, wall, "Island", "Island", "Island")
        bob.library(bear, ogre, giant)
        alice.activates(thresholdWarden, target = "bob")
        resolveAll()
        expect { bob.library.names shouldBe listOf(giant) }
    }

    @Test
    fun flashOfInsightUnboundDrawsTwo() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(flashOfInsightUnbound)
        alice.library(bear, ogre)
        alice.casts(flashOfInsightUnbound, target = "alice")
        resolveAll()
        expect { alice.hand.names shouldBe listOf(bear, ogre) }
    }

    @Test
    fun numbingClutchKeepsItTapped() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(numbingClutch)
        bob.battlefield(ogre, tapped = true)
        bob.library(bear)
        alice.casts(numbingClutch, target = ogre)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { ogre.tapped shouldBe true }
    }

    @Test
    fun glyphwingDrawsAsItDies() = scenario {
        alice.battlefield("Mountain", glyphwing)
        alice.hand(shock)
        alice.library(bear)
        alice.casts(shock, target = glyphwing)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun pilfererOfWhispersDrawsOnDamage() = scenario {
        alice.battlefield(pilfererOfWhispers)
        alice.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(pilfererOfWhispers)
        advanceTo(TurnStep.EndOfCombat)
        expect { alice.hand has bear }
    }

    @Test
    fun spireDrakeToughensUp() = scenario {
        alice.battlefield(spireDrake, "Plains")
        alice.activates(spireDrake)
        resolveAll()
        expect { spireDrake.toughness shouldBe 2 }
    }

    @Test
    fun nullcasterBounces() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(nullcaster)
        bob.battlefield(ogre)
        alice.casts(nullcaster)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        may(true)
        resolveAll()
        expect { bob.hand has ogre }
    }

    // ---- Chunk 08: black

    @Test
    fun paidAssassinationDestroysAndDiscards() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(paidAssassination)
        bob.battlefield(ogre)
        bob.hand(giant)
        alice.casts(paidAssassination, target = ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            bob.graveyard has giant
        }
    }

    @Test
    fun pyreRitualExilesAndDraws() = scenario {
        alice.battlefield("Swamp")
        alice.hand(pyreRitual)
        alice.library(bear)
        bob.graveyard(ogre)
        alice.casts(pyreRitual, target = ogre)
        resolveAll()
        expect {
            bob.exile has ogre
            alice.hand has bear
        }
    }

    @Test
    fun wickedDelightPumpsAndGrantsTrample() = scenario {
        alice.battlefield("Swamp", "Mountain", bear)
        alice.hand(wickedDelight)
        alice.casts(wickedDelight, target = bear, paying = listOf("Swamp"))
        resolveAll()
        alice.activates(bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun hurlFestivalSacrificesToDestroy() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", human)
        alice.hand(hurlFestival)
        bob.battlefield(giant)
        alice.casts(hurlFestival, target = giant, choosing = listOf(human))
        resolveAll()
        expect {
            bob.graveyard has giant
            bob.life shouldBe 18
            alice.graveyard has human
        }
    }

    // CR 702.3b: attacks as though it didn't have defender
    @Test
    fun ogreEscapeeAttacksWithAGate() = scenario {
        alice.battlefield(ogreEscapee, gate)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ogreEscapee)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun swarmingRatCopiesItself() = scenario {
        alice.battlefield(swarmingRat, "Swamp", "Swamp", "Swamp")
        alice.hand(bear)
        expect { swarmingRat.power shouldBe 1 }
        alice.activates(swarmingRat, choosing = listOf(bear))
        resolveAll()
        expect {
            alice.battlefield.names.count { it == swarmingRat } shouldBe 2
            swarmingRat.power shouldBe 2
        }
    }

    @Test
    fun menacingShadePumps() = scenario {
        alice.battlefield(menacingShade, "Swamp", "Swamp")
        alice.activates(menacingShade)
        resolveAll()
        expect { menacingShade.power shouldBe 3 }
    }

    @Test
    fun howlingTormentPunishesAnEmptyHand() = scenario {
        alice.battlefield(howlingTorment)
        bob.hand(bear)
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun tenementReaperMakesEachPlayerSacrifice() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", human)
        alice.hand(tenementReaper)
        bob.battlefield(ogre)
        alice.casts(tenementReaper)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(human)
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.graveyard has human
            alice.battlefield has tenementReaper
        }
    }

    @Test
    fun festeringGashShrinksAndDrains() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(festeringGash)
        bob.battlefield(giant)
        bob.library(bear)
        alice.casts(festeringGash, target = giant)
        resolveAll()
        expect { giant.power shouldBe 3 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    // CR 705.1: flipping a coin
    @Test
    fun alehouseGrifterGainsOnAWonFlip() = scenario {
        alice.battlefield(alehouseGrifter)
        random(coinWin)
        alice.activates(alehouseGrifter)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    @Test
    fun finalTollOnlyMonocolored() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(finalToll)
        bob.battlefield(redBlue, ogre)
        alice.casts(finalToll)
        assertEquals(listOf(target(ogre)), decision<Decision.Targets>().options)
        targets(ogre)
        payIfAsked()
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun subterraneanContactsMakesALandDraw() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Island")
        alice.hand(subterraneanContacts)
        alice.library(bear)
        alice.casts(subterraneanContacts, target = "Island", paying = listOf("Swamp", "Swamp#1", "Swamp#2"))
        resolveAll()
        alice.activates("Island", ability = 1)
        resolveAll()
        expect {
            alice.hand has bear
            alice.life shouldBe 19
        }
    }

    // ---- Chunk 09: red

    @Test
    fun obliteratingBlazeExiles() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(obliteratingBlaze)
        bob.battlefield(ogre)
        alice.casts(obliteratingBlaze, target = ogre)
        resolveAll()
        expect { bob.exile has ogre }
    }

    @Test
    fun cinderZealotPunishesGraveyardCasting() = scenario {
        alice.battlefield(cinderZealot)
        bob.battlefield("Island", "Island", "Island", "Island")
        bob.graveyard(flashbackSpell)
        bob.library(bear)
        turn(active = bob)
        bob.casts(flashbackSpell)
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun rambasherDestroysAnArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(rambasher)
        bob.battlefield(idol)
        alice.casts(rambasher)
        resolveAll()
        targetsIfAsked(idol)
        resolveAll()
        may(true)
        resolveAll()
        expect { bob.graveyard has idol }
    }

    @Test
    fun forgebreathLizardPumps() = scenario {
        alice.battlefield(forgebreathLizard, "Mountain", "Mountain")
        alice.activates(forgebreathLizard)
        resolveAll()
        expect { forgebreathLizard.power shouldBe 2 }
    }

    @Test
    fun shatteringImpactDealsFive() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(shatteringImpact)
        alice.casts(shatteringImpact, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 15 }
    }

    @Test
    fun goblinMusterMakesFour() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(goblinMuster)
        alice.casts(goblinMuster)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Goblin Token" } shouldBe 4 }
    }

    @Test
    fun guttersparkBurnsOnSpells() = scenario {
        alice.battlefield(gutterspark, "Island")
        alice.hand(ponder)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun catapultCrewUntapsForAMulticoloredSpell() = scenario {
        alice.battlefield(catapultCrew, "Island", "Mountain")
        alice.hand(redBlue)
        alice.activates(catapultCrew)
        resolveAll()
        alice.casts(redBlue)
        resolveAll()
        alice.activates(catapultCrew)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun questForWingsPumpsAndGrantsFlying() = scenario {
        alice.battlefield("Mountain", "Mountain", "Island", bear)
        alice.hand(questForWings)
        alice.casts(questForWings, target = bear, paying = listOf("Mountain", "Mountain#1"))
        resolveAll()
        alice.activates(bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Flying
        }
    }

    @Test
    fun blazeconvergenceBurnsOnAMulticoloredSpell() = scenario {
        alice.battlefield(blazeconvergence, "Island", "Mountain")
        alice.hand(redBlue)
        alice.casts(redBlue)
        targets("bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun speedwayFrenzyGivesHaste() = scenario {
        alice.battlefield("Mountain", "Island")
        alice.battlefield(bear, sick = true)
        alice.hand(speedwayFrenzy)
        alice.casts(speedwayFrenzy, target = "Island", paying = listOf("Mountain"))
        resolveAll()
        alice.activates("Island", ability = 1, target = bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Haste }
    }

    @Test
    fun siftTheRubbleDestroysALand() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(siftTheRubble)
        bob.battlefield("Forest")
        alice.casts(siftTheRubble, target = "Forest")
        resolveAll()
        expect {
            bob.graveyard has "Forest"
            alice.battlefield has "Goblin Token"
        }
    }

    @Test
    fun turncoatUrgeStealsWithAPump() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(turncoatUrge)
        bob.battlefield(ogre, tapped = true)
        alice.casts(turncoatUrge, target = ogre)
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            ogre.power shouldBe 5
            ogre hasKeyword Keyword.Haste
        }
    }

    @Test
    fun utmostHellkiteMakesDragonsOnAttack() = scenario {
        alice.battlefield(utmostHellkite)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(utmostHellkite)
        resolveAll()
        expect { "Dragon Token".power shouldBe 6 }
    }

    @Test
    fun viashinoHustlerRummages() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(viashinoHustler, giant)
        alice.library(bear)
        alice.casts(viashinoHustler)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(giant)
        resolveAll()
        expect { alice.hand has bear }
    }

    // ---- Chunk 10: green

    @Test
    fun skywardAmbushDowned() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(skywardAmbush)
        bob.battlefield(flyer)
        alice.casts(skywardAmbush, target = flyer)
        resolveAll()
        expect {
            bob.graveyard has flyer
            alice.life shouldBe 22
        }
    }

    @Test
    fun axewoodWardenMakesManaPerDefender() = scenario {
        alice.battlefield(axewoodWarden, wall)
        alice.activates(axewoodWarden, color = Color.Green)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun centaurHarbingerBecomesACentaur() = scenario {
        alice.battlefield(centaurHarbinger, "Forest", "Forest", "Forest")
        alice.activates(centaurHarbinger)
        resolveAll()
        expect { "Centaur Token".power shouldBe 3 }
    }

    @Test
    fun anthemOfStrengthPumpsPerCreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", bear, human)
        alice.hand(anthemOfStrength)
        alice.casts(anthemOfStrength, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun mortalInheritancePassesThePowerOn() = scenario {
        alice.battlefield("Mountain", mortalInheritance, ogre, bear)
        alice.hand(shock, shock)
        alice.battlefield("Mountain")
        alice.casts(shock, target = ogre)
        resolveAll()
        alice.casts(shock, target = ogre)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 3 }
    }

    @Test
    fun gateclimberVineFindsAGate() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(gateclimberVine)
        alice.library(bear, gate)
        alice.casts(gateclimberVine)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(gate)
        resolveAll()
        expect { alice.hand has gate }
    }

    @Test
    fun devouringOozeEatsACreature() = scenario {
        alice.battlefield(devouringOoze, bear, "Forest")
        alice.activates(devouringOoze, choosing = listOf(bear))
        resolveAll()
        expect { devouringOoze.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun manaBlossomStoresMana() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(manaBlossom)
        alice.casts(manaBlossom, x = 2)
        resolveAll()
        expect { manaBlossom.counters(CounterKind.Named("charge")) shouldBe 2 }
        alice.activates(manaBlossom, color = Color.Red)
        expect {
            alice.pool.size shouldBe 1
            manaBlossom.counters(CounterKind.Named("charge")) shouldBe 1
        }
    }

    @Test
    fun manaBlossomReturnsWhenEmpty() = scenario {
        alice.battlefield(manaBlossom)
        alice.library(bear)
        bob.library(bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect { alice.hand has manaBlossom }
    }

    @Test
    fun feralSurgeUntapsAndPumps() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.battlefield(bear, tapped = true)
        alice.hand(feralSurge)
        alice.casts(feralSurge, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.tapped shouldBe false
        }
    }

    @Test
    fun scanTheSkylineFindsThreeLands() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(scanTheSkyline)
        alice.library("Plains", bear, "Island", "Swamp")
        alice.casts(scanTheSkyline)
        resolveAll()
        alice.chooses("Plains", "Island", "Swamp")
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
    }

    @Test
    fun oozeSculptingMakesAnXByXOoze() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(oozeSculpting)
        alice.casts(oozeSculpting, x = 3)
        resolveAll()
        expect { "Ooze Token".power shouldBe 3 }
    }

    @Test
    fun stonetrailCrocodileGainsLifelink() = scenario {
        alice.battlefield(stonetrailCrocodile, "Swamp", "Swamp", "Swamp")
        alice.activates(stonetrailCrocodile)
        resolveAll()
        expect { stonetrailCrocodile hasKeyword Keyword.Lifelink }
    }

    @Test
    fun feralPackmasterPumpsTheOthers() = scenario {
        alice.battlefield(feralPackmaster, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(feralPackmaster)
        resolveAll()
        expect {
            bear.power shouldBe 3
            feralPackmaster.power shouldBe 1
        }
    }

    // ---- Chunk 11: Izzet and guild creatures

    @Test
    fun sparkcoilOddityGrowsAndUntaps() = scenario {
        alice.battlefield("Island")
        alice.battlefield(sparkcoilOddity, tapped = true)
        alice.hand(ponder)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        expect {
            sparkcoilOddity.power shouldBe 2
            sparkcoilOddity.tapped shouldBe false
        }
    }

    @Test
    fun rimeflareOddityTradesToughnessForPower() = scenario {
        alice.battlefield(rimeflareOddity, "Island")
        alice.activates(rimeflareOddity)
        resolveAll()
        expect {
            rimeflareOddity.power shouldBe 2
            rimeflareOddity.toughness shouldBe 3
        }
    }

    // CR 601.2f: {1} less: six mana paid with five Mountains
    @Test
    fun goblinVoltsmithReducesByOne() = scenario {
        alice.battlefield(goblinVoltsmith, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(shatteringImpact)
        alice.casts(shatteringImpact, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 15 }
    }

    @Test
    fun thunderclapDragonFlashesSorceries() = scenario {
        alice.battlefield(thunderclapDragon, "Island")
        alice.hand(ponder)
        alice.library(bear)
        turn(active = bob, priority = alice)
        alice.casts(ponder)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun staticSparkMageHitsEveryNamesake() = scenario {
        alice.battlefield(staticSparkMage)
        bob.battlefield(human, human, ogre)
        alice.activates(staticSparkMage, target = human)
        resolveAll()
        expect {
            bob.graveyard.size shouldBe 2
            bob.battlefield has ogre
        }
    }

    @Test
    fun volatileAlchemistFlingsAMagnitude() = scenario {
        alice.battlefield(volatileAlchemist, "Mountain")
        alice.hand(giant)
        bob.battlefield(ogre)
        alice.activates(volatileAlchemist, ability = 1, target = ogre, choosing = listOf(giant))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun volatileAlchemistDrawsTwo() = scenario {
        alice.battlefield(volatileAlchemist, "Island")
        alice.library(bear, ogre)
        alice.activates(volatileAlchemist, ability = 0)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun vazrekPingsAndDraws() = scenario {
        alice.battlefield(vazrekDragonSavant, "Island", "Mountain")
        alice.library(bear)
        alice.activates(vazrekDragonSavant, target = "bob")
        resolveAll()
        may(true)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.hand has bear
        }
    }

    @Test
    fun laboratoryGuildmageCopiesASpell() = scenario {
        alice.battlefield(laboratoryGuildmage, "Mountain", "Island", "Mountain", "Island", "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob", paying = listOf("Mountain"))
        alice.activates(laboratoryGuildmage, ability = 1, target = shock)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    // ---- Chunk 12: artifacts and lands

    // CR 613.1d: an artifact that becomes a creature until end of turn
    @Test
    fun undercityRunestoneBecomesAnInsect() = scenario {
        alice.battlefield(undercityRunestone, "Swamp", "Forest")
        alice.activates(undercityRunestone, ability = 1)
        resolveAll()
        expect {
            undercityRunestone.power shouldBe 2
            undercityRunestone hasKeyword Keyword.Deathtouch
        }
    }

    @Test
    fun foundryRunestoneLootsOnDamage() = scenario {
        alice.battlefield(foundryRunestone, "Island", "Mountain")
        alice.hand(giant)
        alice.library(bear)
        alice.activates(foundryRunestone, ability = 1)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(foundryRunestone)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(giant)
        resolveAll()
        expect {
            alice.hand has bear
            alice.graveyard has giant
        }
    }

    @Test
    fun carnivalRunestoneBecomesADevil() = scenario {
        alice.battlefield(carnivalRunestone, "Swamp", "Mountain")
        alice.activates(carnivalRunestone, ability = 1)
        resolveAll()
        expect {
            carnivalRunestone.power shouldBe 3
            carnivalRunestone hasKeyword Keyword.FirstStrike
        }
    }

    @Test
    fun conclaveRunestoneBecomesAWolf() = scenario {
        alice.battlefield(conclaveRunestone, "Forest", "Plains")
        alice.activates(conclaveRunestone, ability = 1)
        resolveAll()
        expect { conclaveRunestone.toughness shouldBe 3 }
    }

    @Test
    fun prismaticLampFixesLands() = scenario {
        alice.battlefield(prismaticLamp, "Forest")
        alice.activates("Forest", ability = 1, color = Color.Blue)
        expect { alice.pool.size shouldBe 1 }
    }

    @Test
    fun citizensBladeCountsColors() = scenario {
        alice.battlefield(citizensBlade, redBlue, "Plains")
        alice.activates(citizensBlade, target = redBlue)
        resolveAll()
        expect { redBlue.power shouldBe 4 }
    }

    @Test
    fun ledgerShredderEngineMillsAndRecovers() = scenario {
        alice.battlefield(ledgerShredderEngine, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.graveyard(bear)
        bob.library(ogre, giant)
        alice.activates(ledgerShredderEngine, ability = 0, target = "bob")
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun gutterSweeperDestroysAurasOnALand() = scenario {
        alice.battlefield(gutterSweeper)
        bob.battlefield("Forest")
        bob.battlefield(landAura)
        attach(landAura, "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(gutterSweeper)
        targetsIfAsked("Forest")
        resolveAll()
        expect { bob.graveyard has landAura }
    }

    // CR 705.1: coin flips
    @Test
    fun unstableContraptionSurvivesAWonFlip() = scenario {
        alice.battlefield(unstableContraption, "Mountain")
        alice.hand(shock)
        random(coinWin)
        alice.casts(shock, target = unstableContraption)
        resolveAll()
        expect { alice.battlefield has unstableContraption }
    }

    /** All five Gates: each enters tapped and makes either of its two colors. */
    // CR 614.1c
    @Test
    fun eachGateEntersTapped() {
        listOf(senateGate, undercityGate, foundryGate, carnivalGate, conclaveGate).forEach { gate ->
            scenario {
                alice.hand(gate)
                alice.plays(gate)
                expect {
                    gate.tapped shouldBe true
                    gate.subtypes.map { it.name } shouldBe listOf("Gate")
                }
            }
        }
    }

    // CR 614.12: "as this land enters, you may pay 2 life"
    @Test
    fun mossyCryptEntersUntappedForLife() = scenario {
        alice.hand(mossyCrypt)
        alice.plays(mossyCrypt)
        may(true)
        expect {
            mossyCrypt.tapped shouldBe false
            alice.life shouldBe 18
        }
    }

    @Test
    fun geyserChasmEntersTappedUnpaid() = scenario {
        alice.hand(geyserChasm)
        alice.plays(geyserChasm)
        may(false)
        expect {
            geyserChasm.tapped shouldBe true
            alice.life shouldBe 20
        }
    }

    @Test
    fun gladeOfTheSentinelMakesAnElemental() = scenario {
        alice.battlefield(gladeOfTheSentinel, "Forest", "Forest", "Forest", "Forest", "Plains", bear, human)
        alice.activates(gladeOfTheSentinel, ability = 1, choosing = listOf(bear, human))
        resolveAll()
        expect {
            "Elemental Token".power shouldBe 8
            alice.graveyard has gladeOfTheSentinel
        }
    }

    @Test
    fun smugglersAlleyMakesUnblockable() = scenario {
        alice.battlefield(smugglersAlley, "Plains", "Plains", "Plains", "Plains", bear)
        bob.battlefield(giant)
        alice.activates(smugglersAlley, ability = 1, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
    }

    // CR 118.12: sacrificed unless {1} is paid
    @Test
    fun interguildConcourseIsKeptForOne() = scenario {
        alice.battlefield("Plains")
        alice.hand(interguildConcourse)
        alice.plays(interguildConcourse)
        resolveAll()
        may(true)
        payIfAsked()
        resolveAll()
        expect { alice.battlefield has interguildConcourse }
    }
}
