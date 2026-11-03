package arcanum.engine.sets.blb

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.Blb.bear
import arcanum.engine.sets.blb.Blb.bigSpell
import arcanum.engine.sets.blb.Blb.boar
import arcanum.engine.sets.blb.Blb.frog
import arcanum.engine.sets.blb.Blb.giant
import arcanum.engine.sets.blb.Blb.idol
import arcanum.engine.sets.blb.Blb.ogre
import arcanum.engine.sets.blb.Blb.p1p1
import arcanum.engine.sets.blb.Blb.ponder
import arcanum.engine.sets.blb.Blb.rabbit
import arcanum.engine.sets.blb.Blb.rabbitCall
import arcanum.engine.sets.blb.Blb.shock
import arcanum.engine.sets.blb.Blb.unsummon
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Bloomburrow's other creatures, legends, lands and commons in play (parser chunks 11–13). */
class BlbCreatureLegendAndCommonCardsScenariosTest {
    private val mistgloamCoyote = Cards.invent("Mistgloam Coyote", "{3}{W}{W}", "Creature — Elemental Coyote", "When this creature enters, exile target creature an opponent controls until this creature leaves the battlefield. If that creature had power 2 or less, put a +1/+1 counter on this creature.", "3/4")
    private val twinOakScribe = Cards.invent("Twin Oak Scribe", "{1}{G}", "Creature — Frog Druid", "Whenever this creature or another creature you control leaves the battlefield without dying, put a +1/+1 counter on target creature you control.", "2/3")
    private val rainspoutSentry = Cards.invent("Rainspout Sentry", "{2}{U}", "Creature — Frog Soldier", "Whenever this creature attacks, if another creature entered the battlefield under your control this turn, this creature gains flying until end of turn.", "3/2")
    private val stormcleaverOtter = Cards.invent("Stormcleaver Otter", "{3}{R}", "Creature — Otter Wizard", """
        Haste
        Whenever you cast an instant or sorcery spell, create a token that's a copy of this creature. Exile that token at the beginning of the next end step.
    """, "1/4")
    private val battleSqueal = Cards.invent("Battle Squeal", "{R}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, target creature an opponent controls can't block this turn.
        Enchanted creature gets +1/+1 and has haste.
    """)
    private val ridgetopWatcher = Cards.invent("Ridgetop Watcher", "{2}{G}", "Creature — Frog Scout", """
        Reach
        When this creature enters, reveal cards from the top of your library until you reveal a land card. Put that card onto the battlefield tapped and the rest on the bottom of your library in a random order.
    """, "1/2")
    private val fogbreathElder = Cards.invent("Fogbreath Elder", "{G}", "Creature — Frog Warrior", "At the beginning of your upkeep, return another creature you control to its owner's hand. If you do, put a +1/+1 counter on this creature. Otherwise, you may return this creature to its owner's hand.", "2/2")
    private val gluetongueSentry = Cards.invent("Gluetongue Sentry", "{2}{G}", "Creature — Frog Warrior", """
        Reach
        When this creature enters, return up to one other target permanent you control to its owner's hand.
    """, "3/3")
    private val fillingTheLarder = Cards.invent("Filling the Larder", "{G}", "Enchantment", """
        Whenever you put one or more +1/+1 counters on a creature you control, put a supply counter on this enchantment.
        {2}, Remove a supply counter from this enchantment: Draw a card.
    """)
    private val drizzlelightDruid = Cards.invent("Drizzlelight Druid", "{G}", "Creature — Frog Druid", "When this creature enters, put a +1/+1 counter on target creature and you gain 1 life.", "0/2")
    private val grovekeeperPair = Cards.invent("Grovekeeper Pair", "{3}{G}", "Creature — Frog Rabbit", "When this creature enters, until end of turn, target creature you control gains vigilance and gets +X/+X, where X is the number of creatures you control.", "3/4")
    private val haloedRedeemer = Cards.invent("Haloed Redeemer", "{3}{W}{W}", "Creature — Angel Soldier", """
        Flying
        Whenever another creature you control with power 2 or less enters, put two +1/+1 counters on that creature.
    """, "2/4")
    private val enchantedSlumber = Cards.invent("Enchanted Slumber", "{1}{U}{U}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, tap enchanted creature.
        Enchanted creature doesn't untap during its controller's untap step.
    """)
    private val pilferingOtter = Cards.invent("Pilfering Otter", "{2}{U}", "Creature — Otter", "Whenever this creature deals damage to an opponent, draw a card.", "2/2")
    private val dewdreamMesmerist = Cards.invent("Dewdream Mesmerist", "{2}{G}{U}", "Creature — Frog Wizard", """
        Reach
        When this creature enters, tap up to one target creature and put three stun counters on it. If you control that creature, draw two cards.
    """, "3/4")
    private val lilypadTutor = Cards.invent("Lilypad Tutor", "{2}{G}{U}", "Creature — Frog Druid", """
        Reach
        {1}{G}{U}: Exile another target creature you control, then return it to the battlefield under its owner's control with a +1/+1 counter on it. Activate only as a sorcery.
    """, "4/4")
    private val noxaItsWingsDusk = Cards.invent("Noxa, Its Wings Dusk", "{3}{B}{B}", "Legendary Creature — Elemental Bird", """
        Flying, trample
        Ward—Discard a card.
        Creatures your opponents control have base toughness 1.
    """, "6/5")
    private val pelleTheFretful = Cards.invent("Pelle, the Fretful", "{1}{G}{U}", "Legendary Creature — Frog Druid", """
        Vigilance
        Whenever Pelle or another creature you control enters, return up to one target creature you control with lesser mana value to its owner's hand.
        Frogs you control have "{T}: Add {G} or {U}. Spend this mana only to cast a creature spell."
    """, "3/3")
    private val fertileGreenback = Cards.invent("Fertile Greenback", "{3}{G}{G}", "Creature — Elemental Turtle", """
        Reach
        As long as you control ten or more lands, creatures you control get +2/+2.
        Whenever this creature or another creature you control with toughness greater than its power enters, look at the top card of your library. If it's a land card, you may put it onto the battlefield tapped. Otherwise, put it into your hand.
    """, "4/6")
    private val unmappedRefuge = Cards.invent("Unmapped Refuge", null, "Land", """
        This land enters tapped. As it enters, choose a color.
        {T}: Add one mana of the chosen color.
    """)
    private val twinOakHamlet = Cards.invent("Twin Oak Hamlet", null, "Legendary Land", """
        As Twin Oak Hamlet enters, choose a creature type.
        {T}: Add {C}.
        {2}, {T}: Choose a color. Add an amount of mana of that color equal to the number of creatures you control of the chosen type.
    """)
    private val quiltedPennant = Cards.invent("Quilted Pennant", "{3}", "Artifact", """
        As this artifact enters, choose a creature type.
        Creatures you control of the chosen type get +1/+1.
        {T}: Add one mana of any color.
    """)
    private val snarlRoller = Cards.invent("Snarl Roller", "{3}", "Artifact — Vehicle", """
        Vigilance
        {3}, {T}: Put a +1/+1 counter on target creature.
        Tap two untapped tokens you control: This Vehicle becomes an artifact creature until end of turn.
    """, "6/6")
    private val torvinTheHayfork = Cards.invent("Torvin, the Hayfork", "{R}{G}{W}", "Legendary Creature — Rabbit Warrior", """
        Tap two untapped tokens you control: Add one mana of any color.
        Tap three untapped tokens you control: Draw a card.
        Tap four untapped tokens you control: Put three +1/+1 counters on Torvin. It gains trample until end of turn.
    """, "4/3")
    private val pippaOtterballAce = Cards.invent("Pippa, Otterball Ace", "{1}{U}", "Legendary Creature — Otter Wizard", """
        Vigilance
        Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        {T}: Draw a card, then discard a card.
        {2}, {T}: Copy target instant or sorcery spell you control. You may choose new targets for the copy. Activate only if Pippa's power is 3 or greater.
    """, "1/3")
    private val grubbCalamitysSeer = Cards.invent("Grubb, Calamity's Seer", "{B}{G}{U}", "Legendary Creature — Frog Wizard Noble", """
        Deathtouch
        You may look at the top card of your library any time.
        You may play lands and cast spells with mana value 4 or greater from the top of your library.
        {T}: Surveil 2.
    """, "2/4")
    private val arloSharpArcher = Cards.invent("Arlo, Sharp Archer", "{G}{W}", "Legendary Creature — Rabbit Archer", """
        Reach, vigilance
        Whenever Arlo attacks, put a +1/+1 counter on each other creature you control that's a token or a Rabbit. Then if creatures you control have total power 10 or greater, draw a card.
    """, "2/2")
    private val plumeOfSoaring = Cards.invent("Plume of Soaring", "{1}{W}", "Enchantment — Aura", """
        Flash
        Enchant creature
        When this Aura enters, draw a card.
        Enchanted creature gets +1/+0 and has flying.
    """)
    private val lifesongPair = Cards.invent("Lifesong Pair", "{1}{W}", "Creature — Bat Bird", """
        Flying
        Whenever another creature you control enters, you gain 1 life.
    """, "1/2")
    private val burrdownTroupe = Cards.invent("Burrdown Troupe", "{2}{W}", "Creature — Mouse Bard", "Whenever this creature attacks, untap target nonland permanent.", "3/3")
    private val croakingHerald = Cards.invent("Croaking Herald", "{1}{U}", "Creature — Frog Advisor", "When this creature enters, draw a card, then discard a card.", "2/1")
    private val glowshellPair = Cards.invent("Glowshell Pair", "{3}{U}", "Creature — Rat Otter", """
        Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """, "3/4")
    private val featherbondGuard = Cards.invent("Featherbond Guard", "{1}{U}", "Creature — Bird Scout", """
        Flash
        Flying
        When this creature enters, target creature you control gains hexproof until end of turn.
    """, "2/1")
    private val gullyProwler = Cards.invent("Gully Prowler", "{B}", "Creature — Lizard Rogue", """
        Menace (This creature can't be blocked except by two or more creatures.)
        {1}{B}: This creature gets +1/+1 until end of turn.
    """, "1/1")
    private val brisasTrailblazer = Cards.invent("Brisa's Trailblazer", "{3}{R}", "Creature — Otter Wizard", "When this creature enters, exile the top card of your library. Until the end of your next turn, you may play that card.", "4/2")
    private val boldHoarder = Cards.invent("Bold Hoarder", "{1}{R}", "Creature — Raccoon Rogue", """
        First strike
        Whenever this creature attacks, add {R}. Until end of turn, you don't lose this mana as steps and phases end.
    """, "2/1")
    private val tamerOfSqualls = Cards.invent("Tamer of Squalls", "{2}{R}", "Creature — Otter Wizard", "Whenever you cast a noncreature or Otter spell, you may exile the top card of your library. Until end of turn, you may play that card. This ability triggers only once each turn.", "1/4")
    private val emberbornBrawler = Cards.invent("Emberborn Brawler", "{2}{R}", "Creature — Lizard Warlock", """
        Haste
        Whenever a player casts their second spell each turn, this creature deals 2 damage to target opponent.
    """, "2/3")
    private val cindersparkPair = Cards.invent("Cinderspark Pair", "{2}{R}", "Creature — Lizard Otter", """
        {T}: This creature deals 1 damage to target opponent.
        Whenever you cast a noncreature spell, untap this creature.
    """, "1/3")
    private val raccoonDrummer = Cards.invent("Raccoon Drummer", "{1}{R}", "Creature — Raccoon Bard", "{T}: Target creature you control gains haste until end of turn. Activate only as a sorcery.", "2/2")
    private val druidOfTheTrowel = Cards.invent("Druid of the Trowel", "{2}{G}", "Creature — Rabbit Druid", "As long as you control a token, this creature gets +2/+0 and has trample.", "2/3")
    private val perilrootHerbalist = Cards.invent("Perilroot Herbalist", "{2}{G}", "Creature — Rabbit Druid", "Whenever you attack, target creature you control gets +1/+0 until end of turn. If that creature is a token, it also gains deathtouch until end of turn.", "1/4")
    private val grumlaRoarOfTheGlade = Cards.invent("Grumla, Roar of the Glade", "{4}{G}{G}", "Legendary Creature — Elemental Bear", """
        Reach, vigilance
        Grumla's power and toughness are each equal to the number of lands you control.
        When Grumla enters, mill four cards. Then return all land cards from your graveyard to the battlefield tapped.
    """, "*/*")
    private val warrenguardTutor = Cards.invent("Warrenguard Tutor", "{G}{W}", "Creature — Rabbit Soldier", """
        Trample
        Warrenguard Tutor's power and toughness are each equal to the number of creatures you control.
    """, "*/*")
    private val elderOfTheFarmstead = Cards.invent("Elder of the Farmstead", "{3}{G/W}{G/W}", "Creature — Rabbit Citizen", "When this creature enters, create two 1/1 white Rabbit creature tokens.", "3/2")
    private val marshOracle = Cards.invent("Marsh Oracle", "{G/U}{G/U}", "Creature — Frog Advisor", "When this creature enters, draw a card.", "1/1")
    private val squallcatchTutor = Cards.invent("Squallcatch Tutor", "{U}{R}", "Creature — Otter Wizard", """
        Haste
        Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        Instant and sorcery spells you cast cost {1} less to cast.
    """, "1/1")
    private val galeFisher = Cards.invent("Gale Fisher", "{1}{U/R}{U/R}", "Creature — Otter Wizard", "Whenever you cast a noncreature spell, put a +1/+1 counter on this creature.", "2/2")
    private val harbortownChime = Cards.invent("Harbortown Chime", "{1}", "Artifact", """
        When this artifact enters, you may search your library for a basic land card, reveal it, then shuffle and put that card on top.
        {1}, Sacrifice this artifact: Draw a card.
    """)
    private val twinOakMascot = Cards.invent("Twin Oak Mascot", "{2}", "Artifact Creature — Shapeshifter", """
        Changeling (This card is every creature type.)
        {1}: Add one mana of any color. Activate only once each turn.
    """, "2/1")
    private val harbortown = Cards.invent("Harbortown", null, "Land", """
        {T}: Add {C}.
        {2}, {T}, Sacrifice a token: Draw a card.
        {3}, {T}, Pay 1 life: Create a 1/1 blue Fish creature token.
        {4}, {T}: Create a Treasure token.
    """)
    private val secretHollow = Cards.invent("Secret Hollow", null, "Land", """
        When this land enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        {T}: Add {C}.
        {1}, {T}: Add one mana of any color.
    """)
    private val wrenTidalRogue = Cards.invent("Wren, Tidal Rogue", "{2}{U}{R}", "Legendary Creature — Otter Rogue", """
        Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        Other creatures you control have prowess. (If a creature has multiple instances of prowess, each triggers separately.)
        Whenever you cast a noncreature spell, target creature you control can't be blocked this turn.
    """, "3/3")

    // ---- Chunk 11: creatures

    // CR 610.3: exiled until this creature leaves the battlefield
    @Test
    fun mistgloamCoyoteExilesASmallCreatureAndGrows() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(mistgloamCoyote)
        bob.battlefield(rabbit)
        alice.casts(mistgloamCoyote)
        resolveAll()
        targetsIfAsked(rabbit)
        resolveAll()
        expect {
            bob.exile has rabbit
            mistgloamCoyote.counters(p1p1) shouldBe 1
        }
    }

    // CR 700.4: "dies" is put into a graveyard from the battlefield; returning to the hand isn't dying
    @Test
    fun twinOakScribeRewardsALeaveWithoutDying() = scenario {
        alice.battlefield("Island", twinOakScribe, bear)
        alice.hand(unsummon)
        alice.casts(unsummon, target = bear)
        resolveAll()
        targetsIfAsked(twinOakScribe)
        resolveAll()
        expect {
            alice.hand has bear
            twinOakScribe.counters(p1p1) shouldBe 1
        }
    }

    // CR 603.4: the intervening "if" at attack
    @Test
    fun rainspoutSentryFliesAfterAnArrival() = scenario {
        alice.battlefield("Forest", "Forest", rainspoutSentry)
        alice.hand(bear)
        alice.casts(bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rainspoutSentry)
        resolveAll()
        expect { rainspoutSentry hasKeyword Keyword.Flying }
    }

    @Test
    fun rainspoutSentryStaysGroundedOtherwise() = scenario {
        alice.battlefield(rainspoutSentry)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rainspoutSentry)
        resolveAll()
        expect { rainspoutSentry lacksKeyword Keyword.Flying }
    }

    // CR 707.2: a token copy; CR 603.7: exiled at the next end step
    @Test
    fun stormcleaverOtterCopiesItselfForATurn() = scenario {
        alice.battlefield("Island", stormcleaverOtter)
        alice.hand(ponder)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        expect { alice.battlefield.names.count { it == stormcleaverOtter } shouldBe 2 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield.names.count { it == stormcleaverOtter } shouldBe 1 }
    }

    // CR 509.1b: can't block this turn
    @Test
    fun battleSquealHastesAndStopsABlocker() = scenario {
        alice.battlefield("Mountain")
        alice.battlefield(bear, sick = true)
        alice.hand(battleSqueal)
        bob.battlefield(ogre)
        alice.casts(battleSqueal, target = bear)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Haste
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to bear) }
    }

    @Test
    fun ridgetopWatcherFindsALand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(ridgetopWatcher)
        alice.library(bear, ogre, "Plains", rabbit)
        alice.casts(ridgetopWatcher)
        resolveAll()
        expect {
            alice.battlefield has "Plains"
            "Plains".tapped shouldBe true
            alice.library.names.first() shouldBe rabbit
            alice.library.size shouldBe 3
        }
    }

    @Test
    fun fogbreathElderBouncesAnotherCreatureForACounter() = scenario {
        alice.battlefield(fogbreathElder, bear)
        alice.library(ogre)
        bob.library(ogre)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect {
            alice.hand has bear
            fogbreathElder.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun fogbreathElderAloneMayReturnItself() = scenario {
        alice.battlefield(fogbreathElder)
        alice.library(ogre)
        bob.library(ogre)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.hand has fogbreathElder }
    }

    @Test
    fun gluetongueSentryReturnsAPermanent() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", idol)
        alice.hand(gluetongueSentry)
        alice.casts(gluetongueSentry)
        resolveAll()
        targetsIfAsked(idol)
        resolveAll()
        expect { alice.hand has idol }
    }

    // CR 122.6: putting counters; the druid also gains life
    @Test
    fun fillingTheLarderStoresSupplyAndDraws() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", fillingTheLarder, bear)
        alice.hand(drizzlelightDruid)
        alice.library(ogre)
        alice.casts(drizzlelightDruid)
        resolveAll()
        targets(bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            alice.life shouldBe 21
            fillingTheLarder.counters(CounterKind.Named("supply")) shouldBe 1
        }
        alice.activates(fillingTheLarder)
        resolveAll()
        expect {
            alice.hand has ogre
            fillingTheLarder.counters(CounterKind.Named("supply")) shouldBe 0
        }
    }

    @Test
    fun grovekeeperPairPumpsByTheCreatureCount() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", bear, rabbit)
        alice.hand(grovekeeperPair)
        alice.casts(grovekeeperPair)
        resolveAll()
        targets(bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear hasKeyword Keyword.Vigilance
        }
    }

    @Test
    fun haloedRedeemerGrowsSmallArrivals() = scenario {
        alice.battlefield("Forest", "Forest", "Mountain", "Mountain", "Mountain", haloedRedeemer)
        alice.hand(bear, ogre)
        alice.casts(bear)
        resolveAll()
        alice.casts(ogre)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 2
            ogre.counters(p1p1) shouldBe 0
        }
    }

    // CR 502.3: the untap step skips it
    @Test
    fun enchantedSlumberKeepsItTapped() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(enchantedSlumber)
        bob.battlefield(ogre)
        bob.library(bear)
        alice.casts(enchantedSlumber, target = ogre)
        resolveAll()
        expect { ogre.tapped shouldBe true }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { ogre.tapped shouldBe true }
    }

    @Test
    fun pilferingOtterDrawsOnDamage() = scenario {
        alice.battlefield(pilferingOtter)
        alice.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(pilferingOtter)
        advanceTo(TurnStep.EndOfCombat)
        expect { alice.hand has bear }
    }

    // CR 603.4-like check at resolution: "if you control that creature"
    @Test
    fun dewdreamMesmeristStunsYourOwnForTwoCards() = scenario {
        alice.battlefield("Forest", "Forest", "Island", "Island", bear)
        alice.hand(dewdreamMesmerist)
        alice.library(ogre, rabbit)
        alice.casts(dewdreamMesmerist)
        resolveAll()
        targets(bear)
        resolveAll()
        expect {
            bear.counters(CounterKind.Stun) shouldBe 3
            alice.hand.names shouldBe listOf(ogre, rabbit)
        }
    }

    @Test
    fun lilypadTutorFlickersWithACounter() = scenario {
        alice.battlefield(lilypadTutor, "Forest", "Island", "Island", bear)
        alice.activates(lilypadTutor, target = bear)
        resolveAll()
        expect {
            alice.battlefield has bear
            bear.counters(p1p1) shouldBe 1
        }
    }

    // CR 613.4b: setting base toughness
    @Test
    fun noxaSetsOpponentsToughnessToOne() = scenario {
        alice.battlefield(noxaItsWingsDusk, bear)
        bob.battlefield(giant)
        expect {
            giant.toughness shouldBe 1
            giant.power shouldBe 5
            bear.toughness shouldBe 2
        }
    }

    @Test
    fun pelleReturnsACheaperCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", pelleTheFretful, frog)
        alice.hand(ogre)
        alice.casts(ogre)
        resolveAll()
        targetsIfAsked(frog)
        resolveAll()
        expect { alice.hand has frog }
    }

    // CR 106.6: the Frogs' granted mana casts only creature spells
    @Test
    fun pelleGivesFrogsCreatureMana() = scenario {
        alice.battlefield(pelleTheFretful, frog)
        alice.hand(rabbit)
        alice.activates(frog, color = Color.Green)
        expect { alice.pool.size shouldBe 1 }
    }

    @Test
    fun fertileGreenbackPutsALandIn() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(fertileGreenback)
        alice.library("Plains", bear)
        alice.casts(fertileGreenback)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            alice.battlefield has "Plains"
            "Plains".tapped shouldBe true
        }
    }

    @Test
    fun fertileGreenbackPumpsWithTenLands() = scenario {
        alice.battlefield(fertileGreenback, bear, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        expect { bear.power shouldBe 2 }
        alice.hand("Plains")
        alice.plays("Plains")
        expect { bear.power shouldBe 4 }
    }

    // ---- Chunk 12: legends and lands

    // CR 614.1c: enters tapped; CR 614.12: the color is chosen as it enters
    @Test
    fun unmappedRefugeEntersTappedWithAColor() = scenario {
        alice.hand(unmappedRefuge)
        alice.plays(unmappedRefuge)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Blue))
        expect { unmappedRefuge.tapped shouldBe true }
    }

    @Test
    fun twinOakHamletMakesManaPerCreatureOfTheType() = scenario {
        alice.battlefield("Plains", "Plains", rabbit, rabbit, bear)
        alice.hand(twinOakHamlet)
        alice.plays(twinOakHamlet)
        answer(Answer.SubtypeChoice(Subtype("Rabbit")))
        alice.activates(twinOakHamlet, ability = 1, color = Color.White)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.White))
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun quiltedPennantPumpsTheChosenType() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear, ogre)
        alice.hand(quiltedPennant)
        alice.casts(quiltedPennant)
        resolveAll()
        answer(Answer.SubtypeChoice(Subtype("Bear")))
        expect {
            bear.power shouldBe 3
            ogre.power shouldBe 3
        }
    }

    // CR 301.7: a Vehicle that becomes a creature; tapping tokens as a cost; the elder makes the tokens
    @Test
    fun snarlRollerAnimatesByTappingTokens() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", snarlRoller)
        alice.hand(elderOfTheFarmstead)
        alice.casts(elderOfTheFarmstead)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Rabbit Token" } shouldBe 2 }
        alice.activates(snarlRoller, ability = 1, choosing = listOf("Rabbit Token", "Rabbit Token"))
        resolveAll()
        expect {
            snarlRoller.types.toSet() shouldBe setOf(CardType.Artifact, CardType.Creature)
            card("Rabbit Token").tapped shouldBe true
        }
    }

    @Test
    fun snarlRollerGivesACounter() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", snarlRoller, bear)
        alice.activates(snarlRoller, ability = 0, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun torvinTapsTokensForMana() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", torvinTheHayfork)
        alice.hand(elderOfTheFarmstead)
        alice.casts(elderOfTheFarmstead)
        resolveAll()
        alice.activates(torvinTheHayfork, ability = 0, choosing = listOf("Rabbit Token", "Rabbit Token"), color = Color.Red)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Red))
        expect { alice.pool.size shouldBe 1 }
    }

    @Test
    fun pippaLoots() = scenario {
        alice.battlefield(pippaOtterballAce)
        alice.hand(ogre)
        alice.library(bear)
        alice.activates(pippaOtterballAce, ability = 0)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(bear)
            alice.graveyard has ogre
        }
    }

    // CR 707.10: a copy of a spell; CR 602.5b: only with power 3 or greater
    @Test
    fun pippaCopiesASpellWhenStrong() = scenario {
        alice.battlefield("Mountain", "Island", "Island")
        alice.battlefield(pippaOtterballAce, counters = listOf(p1p1 to 2))
        alice.hand(shock)
        alice.casts(shock, target = "bob", paying = listOf("Mountain"))
        alice.activates(pippaOtterballAce, ability = 1, target = shock)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    // CR 401.2, 601.3: casting from the top of the library
    @Test
    fun grubbCastsBigSpellsFromTheLibrary() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", grubbCalamitysSeer)
        alice.library(boar, bear)
        alice.casts(boar)
        resolveAll()
        expect { alice.battlefield has boar }
    }

    @Test
    fun grubbSurveils() = scenario {
        alice.battlefield(grubbCalamitysSeer)
        alice.library(ogre, bear, rabbit)
        alice.activates(grubbCalamitysSeer)
        resolveAll()
        alice.chooses(ogre, bear)
        resolveAll()
        expect { alice.library.names shouldBe listOf(rabbit) }
    }

    @Test
    fun arloGrowsTokensAndRabbits() = scenario {
        alice.battlefield(arloSharpArcher, rabbit, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(arloSharpArcher)
        resolveAll()
        expect {
            rabbit.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 0
            arloSharpArcher.counters(p1p1) shouldBe 0
        }
    }

    // ---- Chunk 13: commons

    @Test
    fun plumeOfSoaringDrawsAndGivesFlying() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(plumeOfSoaring)
        alice.library(ogre)
        alice.casts(plumeOfSoaring, target = bear)
        resolveAll()
        expect {
            alice.hand has ogre
            bear.power shouldBe 3
            bear hasKeyword Keyword.Flying
        }
    }

    @Test
    fun lifesongPairGainsOnArrivals() = scenario {
        alice.battlefield("Forest", "Forest", lifesongPair)
        alice.hand(bear)
        alice.casts(bear)
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    @Test
    fun burrdownTroupeUntapsAPermanent() = scenario {
        alice.battlefield(burrdownTroupe)
        alice.battlefield(idol, tapped = true)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(burrdownTroupe)
        targetsIfAsked(idol)
        resolveAll()
        expect { idol.tapped shouldBe false }
    }

    @Test
    fun croakingHeraldLoots() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(croakingHerald, ogre)
        alice.library(bear)
        alice.casts(croakingHerald)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(bear)
            alice.graveyard has ogre
        }
    }

    @Test
    fun glowshellPairSurveilsTwo() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(glowshellPair)
        alice.library(ogre, bear, rabbit)
        alice.casts(glowshellPair)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.library.names.first() shouldBe bear
        }
    }

    @Test
    fun featherbondGuardShieldsACreature() = scenario {
        alice.battlefield("Island", "Island", bear)
        alice.hand(featherbondGuard)
        alice.casts(featherbondGuard)
        resolveAll()
        targets(bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Hexproof }
    }

    @Test
    fun gullyProwlerPumps() = scenario {
        alice.battlefield(gullyProwler, "Swamp", "Swamp")
        alice.activates(gullyProwler)
        resolveAll()
        expect { gullyProwler.power shouldBe 2 }
    }

    @Test
    fun brisasTrailblazerExilesAPlayableCard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(brisasTrailblazer)
        alice.library("Forest")
        alice.casts(brisasTrailblazer)
        resolveAll()
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    // CR 106.4a: mana that isn't lost as steps and phases end
    @Test
    fun boldHoarderKeepsItsManaThroughCombat() = scenario {
        alice.battlefield(boldHoarder)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(boldHoarder)
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        expect { alice.pool.size shouldBe 1 }
    }

    // CR 603.2h-like limit: "triggers only once each turn"
    @Test
    fun tamerOfSquallsExilesOnceATurn() = scenario {
        alice.battlefield("Island", "Island", tamerOfSqualls)
        alice.hand(ponder, ponder)
        alice.library("Forest", bear, ogre)
        alice.casts(ponder)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.exile has "Forest" }
        alice.plays("Forest")
        alice.casts(ponder)
        resolveAll()
        expect {
            stack.isEmpty()
            alice.exile.isEmpty()
        }
    }

    @Test
    fun emberbornBrawlerPunishesTheSecondSpell() = scenario {
        alice.battlefield("Island", "Island", emberbornBrawler)
        alice.hand(ponder, ponder)
        alice.library(bear, ogre)
        alice.casts(ponder)
        resolveAll()
        expect { bob.life shouldBe 20 }
        alice.casts(ponder)
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun cindersparkPairUntapsOnNoncreatureSpells() = scenario {
        alice.battlefield("Island", cindersparkPair)
        alice.hand(ponder)
        alice.library(bear)
        alice.activates(cindersparkPair, target = "bob")
        resolveAll()
        alice.casts(ponder)
        resolveAll()
        alice.activates(cindersparkPair, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun raccoonDrummerGivesHaste() = scenario {
        alice.battlefield(raccoonDrummer)
        alice.battlefield(bear, sick = true)
        alice.activates(raccoonDrummer, target = bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Haste }
    }

    @Test
    fun druidOfTheTrowelWithAToken() = scenario {
        alice.battlefield("Plains", druidOfTheTrowel)
        alice.hand(rabbitCall)
        expect { druidOfTheTrowel.power shouldBe 2 }
        alice.casts(rabbitCall)
        resolveAll()
        expect {
            druidOfTheTrowel.power shouldBe 4
            druidOfTheTrowel hasKeyword Keyword.Trample
        }
    }

    @Test
    fun perilrootHerbalistGivesATokenDeathtouch() = scenario {
        alice.battlefield("Plains", perilrootHerbalist)
        alice.hand(rabbitCall)
        alice.casts(rabbitCall)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(perilrootHerbalist)
        targets("Rabbit Token")
        resolveAll()
        expect {
            "Rabbit Token".power shouldBe 2
            "Rabbit Token" hasKeyword Keyword.Deathtouch
        }
    }

    // CR 604.3: a characteristic-defining ability
    @Test
    fun grumlaReturnsMilledLandsAndCountsThem() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(grumlaRoarOfTheGlade)
        alice.library("Plains", bear, "Island", ogre, rabbit)
        alice.casts(grumlaRoarOfTheGlade)
        resolveAll()
        expect {
            alice.battlefield has "Plains"
            alice.battlefield has "Island"
            alice.graveyard has bear
            grumlaRoarOfTheGlade.power shouldBe 8
        }
    }

    @Test
    fun warrenguardTutorCountsCreatures() = scenario {
        alice.battlefield(warrenguardTutor, bear, rabbit)
        expect {
            warrenguardTutor.power shouldBe 3
            warrenguardTutor.toughness shouldBe 3
        }
    }

    @Test
    fun marshOracleDraws() = scenario {
        alice.battlefield("Forest", "Island")
        alice.hand(marshOracle)
        alice.library(bear)
        alice.casts(marshOracle)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun squallcatchTutorMakesSpellsCheaper() = scenario {
        alice.battlefield("Island", "Island", "Island", squallcatchTutor)
        alice.hand(bigSpell)
        alice.library(bear, ogre)
        alice.casts(bigSpell)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(bear, ogre)
            squallcatchTutor.power shouldBe 2
        }
    }

    @Test
    fun galeFisherGrowsOnNoncreatureSpells() = scenario {
        alice.battlefield("Island", galeFisher)
        alice.hand(ponder)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        expect { galeFisher.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun harbortownChimeTopsALandAndCashesIn() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(harbortownChime)
        alice.library(bear, "Forest")
        alice.casts(harbortownChime)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses("Forest")
        resolveAll()
        expect { alice.library.names.first() shouldBe "Forest" }
        alice.activates(harbortownChime)
        resolveAll()
        expect { alice.hand has "Forest" }
    }

    @Test
    fun twinOakMascotFixesOnceATurn() = scenario {
        alice.battlefield(twinOakMascot, "Plains", "Plains")
        alice.activates(twinOakMascot, color = Color.Green, paying = listOf("Plains"))
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        expect { alice.pool.size shouldBe 1 }
        assertFailsWith<IllegalArgumentException> { alice.activates(twinOakMascot, color = Color.Green, paying = listOf("Plains#1")) }
    }

    @Test
    fun harbortownMakesAFishForLife() = scenario {
        alice.battlefield(harbortown, "Plains", "Plains", "Plains")
        alice.library(bear)
        alice.activates(harbortown, ability = 2)
        resolveAll()
        expect {
            alice.battlefield has "Fish Token"
            alice.life shouldBe 19
        }
    }

    @Test
    fun harbortownSacrificesATokenToDraw() = scenario {
        alice.battlefield(harbortown, "Plains", "Plains", "Plains")
        alice.hand(rabbitCall)
        alice.library(bear)
        alice.casts(rabbitCall, paying = listOf("Plains"))
        resolveAll()
        alice.activates(harbortown, ability = 1, choosing = listOf("Rabbit Token"))
        resolveAll()
        expect {
            alice.hand has bear
            alice.battlefield lacks "Rabbit Token"
        }
    }

    @Test
    fun secretHollowSurveilsOnEntering() = scenario {
        alice.hand(secretHollow)
        alice.library(ogre, bear)
        alice.plays(secretHollow)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { alice.graveyard has ogre }
    }

    @Test
    fun wrenGivesProwessAndEvasion() = scenario {
        alice.battlefield("Island", wrenTidalRogue, bear)
        alice.hand(ponder)
        alice.library(ogre)
        bob.battlefield(giant)
        alice.casts(ponder)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear hasKeyword Keyword.Prowess
            bear.power shouldBe 3
            wrenTidalRogue.power shouldBe 4
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
    }
}
