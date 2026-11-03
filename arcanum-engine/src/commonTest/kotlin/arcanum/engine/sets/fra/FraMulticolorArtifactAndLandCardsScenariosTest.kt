package arcanum.engine.sets.fra

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fra.Fra.bear
import arcanum.engine.sets.fra.Fra.doom
import arcanum.engine.sets.fra.Fra.elf
import arcanum.engine.sets.fra.Fra.flyer
import arcanum.engine.sets.fra.Fra.giant
import arcanum.engine.sets.fra.Fra.heal
import arcanum.engine.sets.fra.Fra.idol
import arcanum.engine.sets.fra.Fra.ogre
import arcanum.engine.sets.fra.Fra.p1p1
import arcanum.engine.sets.fra.Fra.shock
import arcanum.engine.sets.fra.Fra.walker
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Reality Fracture's multicolored cards, artifacts and lands in play (parser chunks 11–13). The other dual lands
 * that enter tapped unless their controller controls a planeswalker, or two or more other lands, repeat the two
 * templates tested here (Devoted Quadrangle, Abandoned Seashore). */
class FraMulticolorArtifactAndLandCardsScenariosTest {
    private val scholasticRise = Cards.invent("Scholastic Rise", "{1}{W}", "Instant", """
        Target creature gets +2/+2 and gains flying until end of turn.
        Empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
    """)
    private val ostrelVandrakar = Cards.invent("Ostrel Vandrakar", "{1}{R}{G}{G}", "Legendary Creature — Elder Sphinx", """
        Flying
        When Ostrel Vandrakar enters or dies, create a Heartwood token. (It's a red and green artifact with "{T}: Add {R} or {G}.")
        {6}: Create a Heartwood token. Then Ostrel Vandrakar gets +X/+0 until end of turn, where X is the number of artifacts you control.
    """, "5/4")
    private val hallowedGhast = Cards.invent("Hallowed Ghast", "{W/B}", "Creature — Zombie Cleric", """
        Lifelink
        {2}{W/B}: Return this card from your graveyard to your hand.
    """, "1/1")
    private val blossomhulk = Cards.invent("Blossomhulk", "{2}{G}{W}", "Creature — Plant Elemental", """
        Whenever you gain life, draw a card. This ability triggers only once each turn.
        {4}{G}{W}: Target creature gains trample and lifelink until end of turn.
    """, "4/4")
    private val stormTheSanctuary = Cards.invent("Storm the Sanctuary", "{2}{R/W}", "Instant", """
        Choose one —
        • Creatures you control get +2/+0 until end of turn.
        • Target creature gets +2/+0 and gains first strike until end of turn. Put a +1/+1 counter on it.
    """)
    private val handiworkSmasher = Cards.invent("Handiwork Smasher", "{3}{R}{R}{G}{G}", "Artifact Creature — Boar Construct", """
        Trample
        When this creature enters, choose two —
        • This creature deals 4 damage to target creature or planeswalker.
        • Create a 2/2 colorless Wizard Soldier creature token named Cadet.
        • Draw a card.
    """, "7/5")
    private val valdimereFatekeep = Cards.invent("Valdimere Fatekeep", "{1}{W}{U}{U}", "Legendary Creature — Elder Sphinx", """
        Flash
        Flying
        Whenever you scry or surveil, put a +1/+1 counter on each creature you control.
    """, "3/4")
    private val franticFuturist = Cards.invent("Frantic Futurist", "{2}{W}{U}", "Creature — Kor Scout", """
        Flying
        At the beginning of combat on your turn, another target creature you control gets +1/+1 until end of turn. If you've scried or surveilled this turn, put a +1/+1 counter on that creature instead.
    """, "3/4")
    private val savageryOfTheChase = Cards.invent("Savagery of the Chase", "{1}{B/G}", "Enchantment — Aura", """
        Flash
        Enchant creature
        Enchanted creature gets +1/+0 and has deathtouch.
        When enchanted creature dies, return that card to the battlefield tapped under its owner's control.
    """)
    private val rimeburnElemental = Cards.invent("Rimeburn Elemental", "{U}{R}{R}", "Creature — Elemental", """
        Trample, haste
        Whenever this creature deals combat damage to a player, draw two cards.
        At the beginning of the end step, sacrifice this creature.
    """, "4/4")
    private val direEncorer = Cards.invent("Dire Encorer", "{B}{R}", "Creature — Zombie Bard", """
        Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        {B}{R}: Return this card from your graveyard to the battlefield with a finality counter on it. Activate only if an opponent has been dealt noncombat damage this turn. (If a creature with a finality counter on it would die, exile it instead.)
    """, "2/2")
    private val varrusBarbquill = Cards.invent("Varrus Barbquill", "{B}{R}{R}", "Legendary Creature — Elder Sphinx", """
        Flying
        Whenever a creature you control attacks, that creature deals 1 damage to each opponent.
        {4}: Create a 2/2 colorless Wizard Soldier creature token named Cadet. Then creatures you control gain haste until end of turn.
    """, "1/4")
    private val tinkerforgeCharm = Cards.invent("Tinkerforge Charm", "{R}{G}", "Instant", """
        Choose one —
        • Tinkerforge Charm deals 6 damage to target creature with flying.
        • Put two +1/+1 counters on target creature. It gains trample until end of turn.
        • Add {C}{C}{C}.
    """)
    private val thessalyVigorleaf = Cards.invent("Thessaly Vigorleaf", "{3}{G}{W}{W}", "Legendary Creature — Elder Sphinx", """
        Flying, vigilance, lifelink, ward {2}
        Whenever you gain life, create a colorless artifact token named Lotus with "{T}, Sacrifice this token: Add three mana of any one color." This ability triggers only once each turn.
    """, "6/6")
    private val thoughtWanderer = Cards.invent("Thought Wanderer", "{3}{G}{U}{U}", "Creature — Bird Fish Illusion", """
        Flying
        This creature has vigilance as long as you control a Jace planeswalker.
        When this creature enters, it fights up to one target creature an opponent controls. (Each deals damage equal to its power to the other.)
    """, "4/4")
    private val voidSummoner = Cards.invent("Void Summoner", "{2}{U}{B}", "Creature — Human Warlock", """
        When this creature enters, if you cast it, target opponent reveals their hand. You choose a nonland card from it. Exile that card.
        Threshold — As long as there are seven or more cards in your graveyard, you may cast the exiled card, and mana of any type can be spent to cast that spell.
    """, "4/2")
    private val primevalHexstalker = Cards.invent("Primeval Hexstalker", "{1}{B}{G}", "Creature — Wolf", """
        Menace (This creature can't be blocked except by two or more creatures.)
        When this creature enters, mill four cards. When you do, return target land card from your graveyard to the battlefield tapped. (To mill four cards, put the top four cards of your library into your graveyard.)
    """, "2/1")
    private val examinerOfPromise = Cards.invent("Examiner of Promise", "{W}{U}", "Creature — Human Cleric", """
        Whenever this creature or another creature you control enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        {W}{U}: Return this card from your graveyard to the battlefield with a finality counter on it. Activate only if you've scried or surveilled this turn. (If a creature with a finality counter on it would die, exile it instead.)
    """, "3/1")
    private val recurringEnlistment = Cards.invent("Recurring Enlistment", "{2}{U}{B}", "Sorcery", """
        Create two 2/2 colorless Wizard Soldier creature tokens named Cadet. If this spell was cast from a graveyard, put a +1/+1 counter on each of them for every three cards in your graveyard.
        Flashback {6}{U}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val sunroomSentinel = Cards.invent("Sunroom Sentinel", "{G}{W}", "Creature — Cat Soldier", "Whenever an opponent casts a spell with mana value 2 or less, you gain 2 life.", "3/3")
    private val isolationChamber = Cards.invent("Isolation Chamber", "{R}{W}", "Artifact", """
        When this artifact enters, exile target nonland permanent an opponent controls with mana value 3 or less until this artifact leaves the battlefield.
        {1}, {T}, Discard a legendary card: Draw a card.
    """)
    private val barbquillCharm = Cards.invent("Barbquill Charm", "{B}{R}", "Instant", """
        Choose one —
        • Barbquill Charm deals 3 damage to any target.
        • Target creature gains first strike and deathtouch until end of turn.
        • Create a 2/2 colorless Wizard Soldier creature token named Cadet. It gains haste until end of turn.
    """)
    private val searingVenom = Cards.invent("Searing Venom", "{B}{R}", "Sorcery", "Searing Venom deals 2 damage to target opponent. That player reveals their hand. You choose a nonland card from it. They discard that card.")
    private val veteranTetherwright = Cards.invent("Veteran Tetherwright", "{1}{R}{G}", "Creature — Human Artificer", """
        When this creature enters, you may sacrifice a land. If you do, create two tapped Heartwood tokens. (They're red and green artifacts with "{T}: Add {R} or {G}.")
        Tap two untapped artifacts you control: Put two +1/+1 counters on this creature.
    """, "1/1")
    private val hypothesisCharm = Cards.invent("Hypothesis Charm", "{U}{B}", "Instant", """
        Choose one —
        • Counter target noncreature spell unless its controller pays {2}.
        • Target creature gets -2/-2 until end of turn.
        • Mill three cards, then draw a card. (To mill three cards, put the top three cards of your library into your graveyard.)
    """)
    private val doubledSight = Cards.invent("Doubled Sight", "{1}{U/R}", "Instant", """
        Draw a card. If this spell wasn't cast from your hand, draw two cards instead.
        Flashback—{1}{U/R}{U/R}, Discard a card. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val contortedDestinies = Cards.invent("Contorted Destinies", "{2}{W}{W}{B}", "Sorcery", "Destroy target nonland permanent. Put a +1/+1 counter on each creature target player controls.")
    private val vigorleafCharm = Cards.invent("Vigorleaf Charm", "{G}{W}", "Instant", """
        Choose one —
        • Target permanent you control gains hexproof and indestructible until end of turn.
        • You draw a card and gain 3 life.
        • Put a +1/+1 counter on target creature you control. Then it fights target creature an opponent controls. (Each deals damage equal to its power to the other.)
    """)
    private val hindsightSentinel = Cards.invent("Hindsight Sentinel", "{2}", "Artifact Creature — Gargoyle", """
        {2}: This creature gains flying until end of turn.
        Whenever this creature attacks, exile up to one target card from a graveyard.
    """, "2/2")
    private val libraryAdjudicator = Cards.invent("Library Adjudicator", "{6}", "Artifact Creature — Sphinx", """
        Flying
        When this creature enters, choose one —
        • Destroy target noncreature, nonland permanent.
        • You gain 4 life.
    """, "4/4")
    private val theResonancePivot = Cards.invent("The Resonance Pivot", "{2}", "Legendary Artifact", """
        When The Resonance Pivot enters, draw a card, then discard a card.
        {5}, {T}, Exile The Resonance Pivot: Destroy all creatures. Activate only as a sorcery.
    """)
    private val gazeOfTheTheorist = Cards.invent("Gaze of the Theorist", "{1}", "Artifact", "At the beginning of your upkeep, surveil 1. Then if there are seven or more cards in your graveyard, sacrifice this artifact, it deals 2 damage to each opponent, and you gain 2 life. (To surveil 1, look at the top card of your library. You may put it into your graveyard.)")
    private val breathingArchive = Cards.invent("Breathing Archive", "{2}", "Artifact Creature — Book Illusion", "{6}, Sacrifice this creature: Choose target creature or planeswalker an opponent controls. Its owner shuffles it into their library.", "0/4")
    private val healersGlidewing = Cards.invent("Healer's Glidewing", "{2}", "Artifact — Equipment", """
        Equipped creature gets +1/+0 and has flying and "Whenever this creature attacks, you gain 1 life."
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val whisperingTome = Cards.invent("Whispering Tome", "{3}", "Artifact — Book", """
        {T}: Add one mana of any color.
        {2}, {T}, Discard a card: Draw a card.
    """)
    private val devotedQuadrangle = Cards.invent("Devoted Quadrangle", null, "Land", """
        This land enters tapped unless you control a planeswalker.
        {T}: Add {R} or {W}.
    """)
    private val abandonedSeashore = Cards.invent("Abandoned Seashore", null, "Land", """
        This land enters tapped unless you control two or more other lands.
        {T}: Add {W} or {U}.
    """)
    private val churningBoughs = Cards.invent("Churning Boughs", null, "Land", """
        This land enters tapped.
        Whenever a Forest you control enters, if you control at least five other Forests, target creature you control gets +3/+3 until end of turn.
        {T}: Add {G}.
    """)
    private val chamberOfSanctuary = Cards.invent("Chamber of Sanctuary", null, "Land", """
        This land enters tapped. As it enters, choose a color.
        {T}: Add one mana of the chosen color.
        {5}, {T}, Sacrifice this land: Put two +1/+1 counters on target creature. Activate only as a sorcery.
    """)

    private val glimpse = Cards.invent("Campus Glimpse", "{U}", "Instant", "Surveil 1.")
    private val legend = Cards.invent("Campus Champion", "{1}{R}", "Legendary Creature — Human Warrior", "", "2/2")

    // ---- Chunk 11: multicolored

    @Test
    fun ostrelVandrakarMakesHeartwoods() = scenario {
        alice.battlefield("Mountain", "Forest", "Forest", "Forest", "Swamp")
        alice.hand(ostrelVandrakar, doom)
        alice.casts(ostrelVandrakar, paying = listOf("Mountain", "Forest", "Forest", "Forest"))
        resolveAll()
        alice.casts(doom, target = ostrelVandrakar)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Heartwood Token" } shouldBe 2 }
    }

    @Test
    fun ostrelVandrakarGrowsByArtifacts() = scenario {
        alice.battlefield(ostrelVandrakar, idol, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.activates(ostrelVandrakar)
        resolveAll()
        expect { ostrelVandrakar.power shouldBe 7 }
    }

    @Test
    fun hallowedGhastReturnsToTheHand() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.graveyard(hallowedGhast)
        alice.activates(hallowedGhast)
        resolveAll()
        expect { alice.hand has hallowedGhast }
    }

    @Test
    fun blossomhulkDrawsOnceOnLifeGain() = scenario {
        alice.battlefield(blossomhulk, "Plains", "Plains")
        alice.hand(heal, heal)
        alice.library(bear, bear)
        alice.casts(heal)
        resolveAll()
        alice.casts(heal)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun stormTheSanctuaryFirstStrikeAndACounter() = scenario {
        alice.battlefield("Mountain", "Mountain", "Plains", bear)
        alice.hand(stormTheSanctuary)
        alice.casts(stormTheSanctuary, modes = listOf(1), target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear hasKeyword Keyword.FirstStrike
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun handiworkSmasherChoosesTwo() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Forest", "Forest", "Forest", "Forest")
        alice.hand(handiworkSmasher)
        alice.library(bear)
        alice.casts(handiworkSmasher)
        resolveAll()
        modes(1, 2)
        resolveAll()
        expect {
            alice.battlefield has "Cadet"
            alice.hand has bear
        }
    }

    @Test
    fun valdimereFatekeepCountersOnSurveil() = scenario {
        alice.battlefield(valdimereFatekeep, bear, "Island")
        alice.hand(glimpse)
        alice.library(ogre)
        alice.casts(glimpse)
        resolveAll()
        chooseNone()
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            valdimereFatekeep.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun franticFuturistPumpsAtCombat() = scenario {
        alice.battlefield(franticFuturist, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun franticFuturistCountersAfterASurveil() = scenario {
        alice.battlefield(franticFuturist, bear, "Island")
        alice.hand(glimpse)
        alice.library(ogre)
        alice.casts(glimpse)
        resolveAll()
        chooseNone()
        resolveAll()
        advanceTo(TurnStep.BeginningOfCombat)
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    // CR 603.10a: the Aura's leaves-the-battlefield trigger looks back
    @Test
    fun savageryOfTheChaseBringsItBack() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.hand(savageryOfTheChase, doom)
        alice.casts(savageryOfTheChase, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Deathtouch
        }
        alice.casts(doom, target = bear)
        resolveAll()
        expect {
            alice.battlefield has bear
            bear.tapped shouldBe true
        }
    }

    @Test
    fun rimeburnElementalDrawsThenGoes() = scenario {
        alice.battlefield(rimeburnElemental)
        alice.library(bear, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rimeburnElemental)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.graveyard has rimeburnElemental }
    }

    // CR 122.1 (finality counter)
    @Test
    fun direEncorerReturnsAfterNoncombatDamage() = scenario {
        alice.battlefield("Mountain", "Swamp", "Mountain")
        alice.hand(shock)
        alice.graveyard(direEncorer)
        alice.casts(shock, target = "bob", paying = listOf("Mountain"))
        resolveAll()
        alice.activates(direEncorer)
        resolveAll()
        expect { direEncorer.counters(CounterKind.Finality) shouldBe 1 }
    }

    @Test
    fun direEncorerNeedsNoncombatDamage() = scenario {
        alice.battlefield("Mountain", "Swamp")
        alice.graveyard(direEncorer)
        assertFailsWith<IllegalArgumentException> { alice.activates(direEncorer) }
    }

    @Test
    fun varrusBarbquillPingsPerAttacker() = scenario {
        alice.battlefield(varrusBarbquill, bear, elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear, elf)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun varrusBarbquillMakesHastyCadets() = scenario {
        alice.battlefield(varrusBarbquill, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.activates(varrusBarbquill)
        resolveAll()
        expect { "Cadet" hasKeyword Keyword.Haste }
    }

    @Test
    fun tinkerforgeCharmShootsAFlyer() = scenario {
        alice.battlefield("Mountain", "Forest")
        alice.hand(tinkerforgeCharm)
        bob.battlefield(flyer)
        alice.casts(tinkerforgeCharm, modes = listOf(0), target = flyer)
        resolveAll()
        expect { bob.graveyard has flyer }
    }

    @Test
    fun tinkerforgeCharmGrowsATrampler() = scenario {
        alice.battlefield("Mountain", "Forest", bear)
        alice.hand(tinkerforgeCharm)
        alice.casts(tinkerforgeCharm, modes = listOf(1), target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 2
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun thessalyVigorleafMakesALotusOnce() = scenario {
        alice.battlefield(thessalyVigorleaf, "Plains", "Plains")
        alice.hand(heal, heal)
        alice.casts(heal)
        resolveAll()
        alice.casts(heal)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Lotus" } shouldBe 1 }
        alice.activates("Lotus", color = Color.Green)
        expect { alice.pool.size shouldBe 3 }
    }

    // CR 701.14a: fight
    @Test
    fun thoughtWandererFightsOnEntering() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Island", "Island", "Island")
        alice.hand(thoughtWanderer)
        bob.battlefield(ogre)
        alice.casts(thoughtWanderer)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            card(thoughtWanderer).view.obj!!.damage shouldBe 3
            thoughtWanderer lacksKeyword Keyword.Vigilance
        }
    }

    @Test
    fun thoughtWandererVigilantWithAJace() = scenario {
        alice.battlefield(thoughtWanderer, "Plains", "Plains")
        alice.hand(scholasticRise)
        alice.casts(scholasticRise, target = thoughtWanderer)
        resolveAll()
        expect { thoughtWanderer hasKeyword Keyword.Vigilance }
    }

    // CR 603.4 ("if you cast it"), 609.4b: mana of any type may be spent
    @Test
    fun voidSummonerStealsACardForThreshold() = scenario {
        alice.battlefield("Island", "Island", "Swamp", "Swamp", "Plains")
        alice.hand(voidSummoner)
        alice.graveyard(elf, elf, elf, elf, elf, elf, elf)
        bob.hand(doom, "Forest")
        bob.battlefield(bear)
        alice.casts(voidSummoner, paying = listOf("Island", "Island", "Swamp", "Swamp"))
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(doom)
        resolveAll()
        expect { bob.exile has doom }
        alice.casts(doom, target = bear, paying = listOf("Plains"))
        resolveAll()
        expect { bob.graveyard has bear }
    }

    // CR 603.12: a reflexive trigger after the mill
    @Test
    fun primevalHexstalkerReturnsAMilledLand() = scenario {
        alice.battlefield("Swamp", "Forest", "Forest")
        alice.hand(primevalHexstalker)
        alice.library("Island", bear, bear, bear)
        alice.casts(primevalHexstalker)
        resolveAll()
        targetsIfAsked("Island")
        resolveAll()
        expect {
            alice.battlefield has "Island"
            "Island".tapped shouldBe true
        }
    }

    @Test
    fun examinerOfPromiseSurveilsAndReturns() = scenario {
        alice.battlefield("Plains", "Island", "Forest", "Plains", "Island")
        alice.hand(elf)
        alice.graveyard(examinerOfPromise)
        alice.library(ogre)
        alice.hand(glimpse)
        alice.casts(glimpse, paying = listOf("Island"))
        resolveAll()
        chooseNone()
        resolveAll()
        alice.activates(examinerOfPromise, paying = listOf("Plains", "Island#1"))
        resolveAll()
        chooseNone()
        resolveAll()
        expect { examinerOfPromise.counters(CounterKind.Finality) shouldBe 1 }
    }

    @Test
    fun examinerOfPromiseSurveilsOnOtherArrivals() = scenario {
        alice.battlefield(examinerOfPromise, "Forest")
        alice.hand(elf)
        alice.library(ogre)
        alice.casts(elf)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { alice.graveyard has ogre }
    }

    // CR 702.34a: cast from the graveyard, the tokens get counters
    @Test
    fun recurringEnlistmentFlashbackCounters() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(recurringEnlistment, bear, bear, bear, bear, bear, bear)
        alice.casts(recurringEnlistment)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Cadet" } shouldBe 2 }
        expect { "Cadet".counters(p1p1) shouldBe 2 }
    }

    @Test
    fun sunroomSentinelGainsOnCheapSpells() = scenario {
        alice.battlefield(sunroomSentinel)
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(active = bob)
        bob.casts(shock, target = "alice")
        resolveAll()
        expect { alice.life shouldBe 20 }
    }

    @Test
    fun isolationChamberExilesACheapPermanent() = scenario {
        alice.battlefield("Mountain", "Plains")
        alice.hand(isolationChamber)
        bob.battlefield(bear, giant)
        alice.casts(isolationChamber)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bob.exile has bear }
    }

    @Test
    fun isolationChamberDrawsForALegend() = scenario {
        alice.battlefield(isolationChamber, "Mountain")
        alice.hand(legend)
        alice.library(bear)
        alice.activates(isolationChamber, choosing = listOf(legend))
        resolveAll()
        expect {
            alice.hand has bear
            alice.graveyard has legend
        }
    }

    @Test
    fun barbquillCharmMakesAHastyCadet() = scenario {
        alice.battlefield("Swamp", "Mountain")
        alice.hand(barbquillCharm)
        alice.casts(barbquillCharm, modes = listOf(2))
        resolveAll()
        expect { "Cadet" hasKeyword Keyword.Haste }
    }

    @Test
    fun searingVenomBurnsAndStrips() = scenario {
        alice.battlefield("Swamp", "Mountain")
        alice.hand(searingVenom)
        bob.hand(bear, "Forest")
        alice.casts(searingVenom, target = "bob")
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect {
            bob.life shouldBe 18
            bob.graveyard has bear
            bob.hand has "Forest"
        }
    }

    @Test
    fun veteranTetherwrightTradesALandForHeartwoods() = scenario {
        alice.battlefield("Mountain", "Forest", "Forest", "Plains")
        alice.hand(veteranTetherwright)
        alice.casts(veteranTetherwright, paying = listOf("Mountain", "Forest", "Forest"))
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses("Plains")
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Heartwood Token" } shouldBe 2
            alice.graveyard has "Plains"
        }
    }

    @Test
    fun veteranTetherwrightGrowsByTappingArtifacts() = scenario {
        alice.battlefield(veteranTetherwright, idol, idol)
        alice.activates(veteranTetherwright, choosing = listOf(idol, idol))
        resolveAll()
        expect { veteranTetherwright.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun hypothesisCharmMillsAndDraws() = scenario {
        alice.battlefield("Island", "Swamp")
        alice.hand(hypothesisCharm)
        alice.library(bear, bear, bear, ogre)
        alice.casts(hypothesisCharm, modes = listOf(2))
        resolveAll()
        expect {
            alice.hand has ogre
            alice.graveyard.size shouldBe 4
        }
    }

    @Test
    fun doubledSightDrawsTwoFromTheGraveyard() = scenario {
        alice.battlefield("Island", "Island", "Mountain")
        alice.graveyard(doubledSight)
        alice.hand(bear)
        alice.library(ogre, ogre)
        alice.casts(doubledSight, choosing = listOf(bear))
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.exile has doubledSight
        }
    }

    @Test
    fun contortedDestiniesDestroysAndCounters() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(contortedDestinies)
        bob.battlefield(idol)
        alice.casts(contortedDestinies, targets = listOf(idol, "alice"))
        resolveAll()
        expect {
            bob.graveyard has idol
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun vigorleafCharmCountersAndFights() = scenario {
        alice.battlefield("Plains", "Forest", ogre)
        alice.hand(vigorleafCharm)
        bob.battlefield(giant)
        alice.casts(vigorleafCharm, modes = listOf(2), targets = listOf(ogre, giant))
        resolveAll()
        expect {
            card(giant).view.obj!!.damage shouldBe 4
            alice.graveyard has ogre
        }
    }

    @Test
    fun vigorleafCharmDrawsAndGains() = scenario {
        alice.battlefield("Plains", "Forest")
        alice.hand(vigorleafCharm)
        alice.library(bear)
        alice.casts(vigorleafCharm, modes = listOf(1))
        resolveAll()
        expect {
            alice.hand has bear
            alice.life shouldBe 23
        }
    }

    // ---- Chunk 13: artifacts and lands

    @Test
    fun hindsightSentinelExilesFromAGraveyard() = scenario {
        alice.battlefield(hindsightSentinel)
        bob.graveyard(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hindsightSentinel)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bob.exile has bear }
    }

    @Test
    fun libraryAdjudicatorDestroysANoncreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(libraryAdjudicator)
        bob.battlefield(idol, bear)
        alice.casts(libraryAdjudicator)
        resolveAll()
        modes(0)
        targetsIfAsked(idol)
        resolveAll()
        expect { bob.graveyard has idol }
    }

    @Test
    fun theResonancePivotLootsThenWipes() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(theResonancePivot, ogre)
        alice.library(giant)
        bob.battlefield(ogre)
        alice.casts(theResonancePivot, paying = listOf("Plains", "Plains"))
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { alice.hand has giant }
        alice.activates(theResonancePivot)
        resolveAll()
        expect {
            alice.graveyard has bear
            bob.graveyard has ogre
            alice.exile has theResonancePivot
        }
    }

    @Test
    fun gazeOfTheTheoristPaysOffAtSevenCards() = scenario {
        alice.battlefield(gazeOfTheTheorist)
        alice.graveyard(elf, elf, elf, elf, elf, elf)
        alice.library(bear, bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        expect {
            alice.graveyard has gazeOfTheTheorist
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    @Test
    fun breathingArchiveShufflesAwayACreature() = scenario {
        alice.battlefield(breathingArchive, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        bob.battlefield(giant)
        alice.activates(breathingArchive, target = giant)
        resolveAll()
        expect { bob.library has giant }
    }

    @Test
    fun healersGlidewingGivesFlightAndLife() = scenario {
        alice.battlefield(healersGlidewing, bear, "Plains", "Plains")
        alice.activates(healersGlidewing, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect {
            bear hasKeyword Keyword.Flying
            bear.power shouldBe 3
            alice.life shouldBe 21
        }
    }

    @Test
    fun whisperingTomeRummages() = scenario {
        alice.battlefield(whisperingTome, "Plains", "Plains")
        alice.hand(bear)
        alice.library(ogre)
        alice.activates(whisperingTome, ability = 1, choosing = listOf(bear))
        resolveAll()
        expect { alice.hand has ogre }
    }

    // CR 614.1c: "enters tapped unless"
    @Test
    fun devotedQuadrangleUntappedWithAPlaneswalker() = scenario {
        alice.battlefield(walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.hand(devotedQuadrangle)
        alice.plays(devotedQuadrangle)
        expect { devotedQuadrangle.tapped shouldBe false }
    }

    @Test
    fun devotedQuadrangleTappedWithoutOne() = scenario {
        alice.hand(devotedQuadrangle)
        alice.plays(devotedQuadrangle)
        expect { devotedQuadrangle.tapped shouldBe true }
    }

    @Test
    fun abandonedSeashoreNeedsTwoOtherLands() = scenario {
        alice.battlefield("Plains")
        alice.hand(abandonedSeashore)
        alice.plays(abandonedSeashore)
        expect { abandonedSeashore.tapped shouldBe true }
    }

    @Test
    fun abandonedSeashoreUntappedWithTwoLands() = scenario {
        alice.battlefield("Plains", "Island")
        alice.hand(abandonedSeashore)
        alice.plays(abandonedSeashore)
        expect { abandonedSeashore.tapped shouldBe false }
    }

    @Test
    fun churningBoughsPumpsAtSixForests() = scenario {
        alice.battlefield(churningBoughs, "Forest", "Forest", "Forest", "Forest", "Forest", bear)
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.power shouldBe 5 }
    }

    @Test
    fun chamberOfSanctuaryChosenColorAndCounters() = scenario {
        alice.hand(chamberOfSanctuary)
        alice.plays(chamberOfSanctuary)
        answer(Answer.ColorChoice(Color.Green))
        expect { chamberOfSanctuary.tapped shouldBe true }
    }

    @Test
    fun chamberOfSanctuaryGivesTwoCounters() = scenario {
        alice.battlefield(chamberOfSanctuary, bear, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.activates(chamberOfSanctuary, ability = 1, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 2 }
    }
}
