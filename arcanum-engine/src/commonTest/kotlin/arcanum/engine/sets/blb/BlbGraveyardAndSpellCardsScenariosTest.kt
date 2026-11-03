package arcanum.engine.sets.blb

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.Blb.bat
import arcanum.engine.sets.blb.Blb.bear
import arcanum.engine.sets.blb.Blb.bird
import arcanum.engine.sets.blb.Blb.charm
import arcanum.engine.sets.blb.Blb.frog
import arcanum.engine.sets.blb.Blb.giant
import arcanum.engine.sets.blb.Blb.idol
import arcanum.engine.sets.blb.Blb.lizard
import arcanum.engine.sets.blb.Blb.mouse
import arcanum.engine.sets.blb.Blb.ogre
import arcanum.engine.sets.blb.Blb.otter
import arcanum.engine.sets.blb.Blb.p1p1
import arcanum.engine.sets.blb.Blb.ponder
import arcanum.engine.sets.blb.Blb.rabbit
import arcanum.engine.sets.blb.Blb.rabbitCall
import arcanum.engine.sets.blb.Blb.raccoon
import arcanum.engine.sets.blb.Blb.rat
import arcanum.engine.sets.blb.Blb.shock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Bloomburrow's graveyard, threshold and spell cards in play (parser chunks 09–10). */
class BlbGraveyardAndSpellCardsScenariosTest {
    private val bogswirlCrab = Cards.invent("Bogswirl Crab", "{5}{U}{U}", "Creature — Elemental Crab", """
        Flash
        This spell costs {1} less to cast for each instant and sorcery card in your graveyard.
        This creature enters tapped if it's not your turn.
        When this creature enters, tap up to two target creatures.
    """, "5/5")
    private val thoughtwhisker = Cards.invent("Thoughtwhisker", "{2}{U}", "Creature — Rat Wizard", """
        At the beginning of your upkeep, surveil 1.
        Threshold — As long as there are seven or more cards in your graveyard, creatures your opponents control get -1/-0.
    """, "3/2")
    private val duskwhorlRecluse = Cards.invent("Duskwhorl Recluse", "{2}{U}", "Creature — Rat Rogue", """
        Vigilance
        Threshold — As long as there are seven or more cards in your graveyard, this creature gets +1/+0 and can't be blocked.
    """, "1/4")
    private val strandlinePilferer = Cards.invent("Strandline Pilferer", "{1}{U}", "Creature — Rat Rogue", """
        This creature can't be blocked.
        Threshold — Whenever this creature deals combat damage to a player, draw a card. Then discard a card unless there are seven or more cards in your graveyard.
    """, "1/1")
    private val thoughtdrillRaider = Cards.invent("Thoughtdrill Raider", "{2}{U/B}{U/B}", "Creature — Rat Warlock", """
        Threshold — As long as there are seven or more cards in your graveyard, this creature gets +3/+0.
        {2}{U/B}: Surveil 1.
    """, "2/5")
    private val ideaHusker = Cards.invent("Idea Husker", "{1}{U}", "Creature — Rat Rogue", "Threshold — {1}{U}: Put a +1/+1 counter on this creature and draw a card. Activate only if there are seven or more cards in your graveyard and only once.", "1/3")
    private val otterpupShenanigans = Cards.invent("Otterpup Shenanigans", "{1}{U}", "Sorcery", """
        Create a 1/1 blue and red Otter creature token with prowess. If this spell was cast from anywhere other than your hand, put a +1/+1 counter on that creature.
        Flashback {3}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val wishwaterFountain = Cards.invent("Wishwater Fountain", "{3}{U}", "Artifact", "{T}: Put a coin counter on this artifact. When you do, you may cast target instant or sorcery card with mana value equal to the number of coin counters on this artifact from your graveyard without paying its mana cost. If that spell would be put into your graveyard, exile it instead. Activate only as a sorcery.")
    private val boldWavecrester = Cards.invent("Bold Wavecrester", "{4}{U}{U}", "Creature — Otter Wizard", "When this creature enters, you may cast target instant or sorcery card with mana value 4 or less from your graveyard without paying its mana cost. If that spell would be put into your graveyard, exile it instead.", "4/4")
    private val husksplitterHorde = Cards.invent("Husksplitter Horde", "{7}{B}", "Creature — Elemental Insect", """
        This spell costs {1} less to cast for each creature card you own in exile and in your graveyard.
        Menace, deathtouch
    """, "6/6")
    private val relentlessBogstalker = Cards.invent("Relentless Bogstalker", "{1}{B}", "Creature — Rat Berserker", """
        This creature gets +1/+0 for each other Rat you control.
        Threshold — Whenever you attack with one or more Rats, if there are seven or more cards in your graveyard, you may pay {2}{B}. If you do, return this card from your graveyard to the battlefield tapped and attacking.
    """, "3/1")
    private val mindswirl = Cards.invent("Mindswirl", "{2}{B}", "Sorcery", """
        Target opponent discards two cards. Then if you control a Rat, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """)
    private val cutthroatBargaining = Cards.invent("Cutthroat Bargaining", "{B}", "Sorcery", """
        Target opponent exiles a card from their hand. If this spell was cast from a graveyard, draw a card.
        Flashback {4}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val skygaze = Cards.invent("Skygaze", "{X}{B}{B}", "Sorcery", "Look at twice X cards from the top of your library. Put X cards from among them into your hand and the rest into your graveyard. You lose X life.")
    private val fennicksSentries = Cards.invent("Fennick's Sentries", "{4}{B}{B}", "Creature — Rat Warlock", "When this creature enters, mill three cards. When you do, target creature an opponent controls gets -X/-X until end of turn, where X is the greatest mana value among cards in your graveyard.", "5/3")
    private val sharpEyedCollector = Cards.invent("Sharp-Eyed Collector", "{G}{G}", "Creature — Raccoon Scout", """
        As long as there are four or more card types among cards exiled with this creature, it gets +4/+4 and has trample.
        {1}: Exile target card from a graveyard.
    """, "3/3")
    private val ebbcallerElder = Cards.invent("Ebbcaller Elder", "{1}{U}{B}", "Creature — Rat Wizard", """
        Menace
        Threshold — When this creature enters, if there are seven or more cards in your graveyard, return up to one target nonland permanent to its owner's hand.
    """, "3/3")
    private val barkshapeGatherer = Cards.invent("Barkshape Gatherer", "{3}", "Artifact Creature — Shapeshifter", """
        Changeling (This card is every creature type.)
        Reach
        {2}: Put target card from your graveyard on the bottom of your library.
    """, "2/3")
    private val bonetieSpeaker = Cards.invent("Bonetie Speaker", "{1}{B}", "Creature — Squirrel Warlock Bard", "{3}{B}, Exile this card from your graveyard: Return another target creature card from your graveyard to your hand.", "2/2")
    private val knifetoothPair = Cards.invent("Knifetooth Pair", "{2}{B}", "Creature — Rat Squirrel", """
        Deathtouch
        When this creature enters, you may mill two cards. (You may put the top two cards of your library into your graveyard.)
    """, "3/2")
    private val grimsight = Cards.invent("Grimsight", "{2}{B}", "Sorcery", "Surveil 2, then draw two cards. You lose 2 life. (To surveil 2, look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)")
    private val boldkinPair = Cards.invent("Boldkin Pair", "{W}", "Creature — Rabbit Mouse", "{1}, {T}: Target creature gets +1/+1 until end of turn. Activate only as a sorcery.", "1/1")
    private val maribelsGrit = Cards.invent("Maribel's Grit", "{1}{W}", "Instant", "Target creature gets +2/+2 until end of turn. Up to one other target creature gets +1/+1 until end of turn.")
    private val bunnyRally = Cards.invent("Bunny Rally", "{2}{W}{W}", "Instant", "Creatures you control get +2/+1 until end of turn. If you control a Rabbit, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)")
    private val wardOffDisaster = Cards.invent("Ward Off Disaster", "{1}{W}", "Instant", "Destroy target creature with power or toughness 4 or greater.")
    private val echoStrike = Cards.invent("Echo Strike", "{1}{W}", "Instant", "Echo Strike deals 4 damage to target attacking, blocking, or tapped creature. You gain 3 life if you control a Bat.")
    private val disastrousSurge = Cards.invent("Disastrous Surge", "{4}{U}{U}", "Sorcery", "Return up to two target creatures to their owners' hands. Draw two cards, then discard a card.")
    private val glitteringRefusal = Cards.invent("Glittering Refusal", "{1}{U}", "Instant", "Counter target spell unless its controller pays {2}. If you control a Bird, counter that spell unless its controller pays {4} instead.")
    private val pearlOfInsight = Cards.invent("Pearl of Insight", "{2}{U}", "Sorcery", """
        This spell costs {1} less to cast if you control an Otter.
        Draw two cards.
    """)
    private val braceUp = Cards.invent("Brace Up", "{U}", "Instant", "Target creature you control gets +1/+1 and gains hexproof until end of turn. Untap it. (It can't be the target of spells or abilities your opponents control.)")
    private val slateScales = Cards.invent("Slate Scales", "{2}{B}", "Instant", """
        Affinity for Lizards (This spell costs {1} less to cast for each Lizard you control.)
        Target creature gets +2/+0 and gains lifelink and indestructible until end of turn.
    """)
    private val arcConduction = Cards.invent("Arc Conduction", "{4}{R}", "Instant", "Arc Conduction deals 6 damage to target creature and 2 damage to up to one target creature token.")
    private val strengthOfTheSmall = Cards.invent("Strength of the Small", "{R}", "Instant", """
        Target creature gains trample until end of turn. It also gets +1/+0 until end of turn if you control a Mouse.
        Draw a card.
    """)
    private val cheekyPush = Cards.invent("Cheeky Push", "{1}{R}", "Sorcery", """
        Cheeky Push deals 1 damage to any target.
        Draw a card.
    """)
    private val haulOffTheRubbish = Cards.invent("Haul Off the Rubbish", "{1}{R}", "Instant", "Haul Off the Rubbish deals 3 damage to target creature or planeswalker. If you control a Raccoon, you may discard a card. If you do, draw a card.")
    private val forTheSharedWeal = Cards.invent("For the Shared Weal", "{X}{X}{G}", "Sorcery", "Create X tokens that are copies of target token you control. Then tokens you control gain indestructible until your next turn. You gain 1 life for each token you control.")
    private val tallStride = Cards.invent("Tall Stride", "{G}", "Instant", "Target creature gets +1/+3 and gains reach until end of turn. Untap it.")
    private val overguard = Cards.invent("Overguard", "{1}{G}", "Instant", "Target creature you control gets +3/+3 and gains trample, hexproof, and indestructible until end of turn.")
    private val tadpoleSmack = Cards.invent("Tadpole Smack", "{3}{G}", "Instant", """
        Affinity for Frogs (This spell costs {1} less to cast for each Frog you control.)
        Target creature you control deals damage equal to twice its power to target creature you don't control.
    """)
    private val feralBite = Cards.invent("Feral Bite", "{1}{G}", "Sorcery", "Target creature you control deals damage equal to its power to target creature you don't control.")

    private val seven = arrayOf("Island", "Island", "Island", "Island", "Island", "Island", "Island")

    // ---- Chunk 09: graveyard and threshold

    // CR 601.2f: cheaper per instant and sorcery card; CR 614.1c: enters tapped on another player's turn
    @Test
    fun bogswirlCrabFlashesInTappedAndTapsTwo() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(bogswirlCrab)
        alice.graveyard(ponder, shock, Blb.pump)
        bob.battlefield(ogre, bear)
        turn(active = bob, priority = alice)
        alice.casts(bogswirlCrab)
        resolveAll()
        targets(ogre, bear)
        resolveAll()
        expect {
            bogswirlCrab.tapped shouldBe true
            ogre.tapped shouldBe true
            bear.tapped shouldBe true
        }
    }

    // CR 207.2c: threshold is an ability word; the static applies with seven cards in the graveyard
    @Test
    fun thoughtwhiskerWeakensOpponentsAtThreshold() = scenario {
        alice.battlefield(thoughtwhisker)
        bob.battlefield(ogre)
        expect { ogre.power shouldBe 3 }
        alice.graveyard(*seven)
        expect { ogre.power shouldBe 2 }
    }

    @Test
    fun duskwhorlRecluseCantBeBlockedAtThreshold() = scenario {
        alice.battlefield(duskwhorlRecluse)
        alice.graveyard(*seven)
        bob.battlefield(giant)
        expect { duskwhorlRecluse.power shouldBe 2 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(duskwhorlRecluse)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to duskwhorlRecluse) }
    }

    @Test
    fun strandlinePilfererDrawsWithoutDiscardingAtThreshold() = scenario {
        alice.battlefield(strandlinePilferer)
        alice.graveyard(*seven)
        alice.hand(ogre)
        alice.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(strandlinePilferer)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect { alice.hand.names shouldBe listOf(ogre, bear) }
    }

    @Test
    fun strandlinePilfererLootsBelowThreshold() = scenario {
        alice.battlefield(strandlinePilferer)
        alice.hand(ogre)
        alice.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(strandlinePilferer)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(bear)
            alice.graveyard has ogre
        }
    }

    @Test
    fun thoughtdrillRaiderHitsHarderAtThreshold() = scenario {
        alice.battlefield(thoughtdrillRaider, "Island", "Island", "Island")
        alice.library(ogre)
        alice.graveyard(*seven)
        expect { thoughtdrillRaider.power shouldBe 5 }
        alice.activates(thoughtdrillRaider)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { alice.graveyard has ogre }
    }

    @Test
    fun ideaHuskerNeedsThreshold() = scenario {
        alice.battlefield(ideaHusker, "Island", "Island")
        assertFailsWith<IllegalArgumentException> { alice.activates(ideaHusker) }
    }

    // CR 602.5b: "only once"
    @Test
    fun ideaHuskerOnceAtThreshold() = scenario {
        alice.battlefield(ideaHusker, "Island", "Island", "Island", "Island")
        alice.library(bear, ogre)
        alice.graveyard(*seven)
        alice.activates(ideaHusker)
        resolveAll()
        expect {
            ideaHusker.counters(p1p1) shouldBe 1
            alice.hand has bear
        }
        assertFailsWith<IllegalArgumentException> { alice.activates(ideaHusker) }
    }

    // CR 702.34a: flashback; cast from the graveyard, so the token gets a counter
    @Test
    fun otterpupShenanigansFlashedBackMakesABiggerOtter() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.graveyard(otterpupShenanigans)
        alice.casts(otterpupShenanigans)
        resolveAll()
        expect {
            "Otter Token".counters(p1p1) shouldBe 1
            alice.exile has otterpupShenanigans
        }
    }

    @Test
    fun otterpupShenanigansFromTheHand() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(otterpupShenanigans)
        alice.casts(otterpupShenanigans)
        resolveAll()
        expect {
            "Otter Token".counters(p1p1) shouldBe 0
            alice.graveyard has otterpupShenanigans
        }
    }

    // CR 603.12, 608.2g: cast while the reflexive trigger resolves, without paying its mana cost; exiled after
    @Test
    fun wishwaterFountainCastsAMatchingSpell() = scenario {
        alice.battlefield(wishwaterFountain)
        alice.graveyard(ponder)
        alice.library(bear)
        alice.activates(wishwaterFountain)
        resolveAll()
        targetsIfAsked(ponder)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            wishwaterFountain.counters(CounterKind.Named("coin")) shouldBe 1
            alice.hand has bear
            alice.exile has ponder
        }
    }

    @Test
    fun boldWavecresterRecastsASpell() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(boldWavecrester)
        alice.graveyard(shock)
        alice.casts(boldWavecrester)
        resolveAll()
        targetsIfAsked(shock)
        resolveAll()
        may(true)
        targets("bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.exile has shock
        }
    }

    @Test
    fun husksplitterHordeIsCheaperPerCreatureCard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(husksplitterHorde)
        alice.graveyard(bear, ogre, rabbit)
        alice.exile(giant)
        alice.casts(husksplitterHorde)
        resolveAll()
        expect {
            alice.battlefield has husksplitterHorde
            husksplitterHorde hasKeyword Keyword.Deathtouch
        }
    }

    @Test
    fun relentlessBogstalkerCountsRats() = scenario {
        alice.battlefield(relentlessBogstalker, rat, rat)
        expect { relentlessBogstalker.power shouldBe 5 }
    }

    // CR 506.3, 508.4: returned tapped and attacking
    @Test
    fun relentlessBogstalkerReturnsAttacking() = scenario {
        alice.battlefield(rat, "Swamp", "Swamp", "Swamp")
        alice.graveyard(relentlessBogstalker, "Island", "Island", "Island", "Island", "Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rat)
        resolveAll()
        may(true)
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect {
            alice.battlefield has relentlessBogstalker
            relentlessBogstalker.tapped shouldBe true
        }
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 15 }
    }

    @Test
    fun mindswirlDiscardsAndSurveilsWithARat() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", rat)
        alice.hand(mindswirl)
        alice.library(ogre, bear)
        bob.hand(giant, "Forest", rabbit)
        alice.casts(mindswirl, target = "bob")
        resolveAll()
        bob.chooses(giant, rabbit)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            bob.hand.names shouldBe listOf("Forest")
            alice.graveyard has ogre
            alice.library.names shouldBe listOf(bear)
        }
    }

    @Test
    fun cutthroatBargainingDrawsWhenFlashedBack() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(cutthroatBargaining)
        alice.library(bear)
        bob.hand(ogre)
        alice.casts(cutthroatBargaining, target = "bob")
        resolveAll()
        expect {
            bob.exile has ogre
            alice.hand has bear
            alice.exile has cutthroatBargaining
        }
    }

    @Test
    fun skygazeKeepsHalf() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(skygaze)
        alice.library(bear, ogre, rabbit, giant, mouse)
        alice.casts(skygaze, x = 2)
        resolveAll()
        alice.chooses(bear, giant)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(bear, giant)
            alice.graveyard.size shouldBe 3
            alice.library.names shouldBe listOf(mouse)
            alice.life shouldBe 18
        }
    }

    @Test
    fun fennicksSentriesShrinkByTheGreatestManaValue() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(fennicksSentries)
        alice.library(giant, "Island", bear)
        bob.battlefield(ogre)
        alice.casts(fennicksSentries)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            alice.graveyard has giant
            bob.graveyard has ogre
        }
    }

    // CR 205.2a: four card types among the exiled cards
    @Test
    fun sharpEyedCollectorGrowsWithFourTypes() = scenario {
        alice.battlefield(sharpEyedCollector, "Forest", "Forest", "Forest", "Forest")
        bob.graveyard(bear, "Plains", idol, charm)
        alice.activates(sharpEyedCollector, target = bear)
        resolveAll()
        alice.activates(sharpEyedCollector, target = "Plains")
        resolveAll()
        alice.activates(sharpEyedCollector, target = idol)
        resolveAll()
        expect { sharpEyedCollector.power shouldBe 3 }
        alice.activates(sharpEyedCollector, target = charm)
        resolveAll()
        expect {
            sharpEyedCollector.power shouldBe 7
            sharpEyedCollector hasKeyword Keyword.Trample
        }
    }

    @Test
    fun ebbcallerElderBouncesAtThreshold() = scenario {
        alice.battlefield("Island", "Swamp", "Swamp")
        alice.hand(ebbcallerElder)
        alice.graveyard(*seven)
        bob.battlefield(ogre)
        alice.casts(ebbcallerElder)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.hand has ogre }
    }

    // (changeling itself: the ECL changeling scenarios)
    @Test
    fun barkshapeGathererRecyclesACard() = scenario {
        alice.battlefield(barkshapeGatherer, "Forest", "Forest")
        alice.graveyard(bear)
        alice.library(ogre)
        alice.activates(barkshapeGatherer, target = bear)
        resolveAll()
        expect { alice.library.names shouldBe listOf(ogre, bear) }
    }

    // CR 602.1: an ability that works from the graveyard
    @Test
    fun bonetieSpeakerReturnsACreatureFromTheGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(bonetieSpeaker, bear)
        alice.activates(bonetieSpeaker, target = bear)
        resolveAll()
        expect {
            alice.hand has bear
            alice.exile has bonetieSpeaker
        }
    }

    @Test
    fun knifetoothPairMillsTwo() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(knifetoothPair)
        alice.library(bear, ogre, rabbit)
        alice.casts(knifetoothPair)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.library.names shouldBe listOf(rabbit) }
    }

    @Test
    fun grimsightSurveilsDrawsAndCostsLife() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(grimsight)
        alice.library(ogre, bear, rabbit, mouse)
        alice.casts(grimsight)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.hand.names shouldBe listOf(bear, rabbit)
            alice.life shouldBe 18
        }
    }

    // ---- Chunk 10: spells

    // CR 602.5d: activate only as a sorcery
    @Test
    fun boldkinPairPumpsAsASorcery() = scenario {
        alice.battlefield(boldkinPair, "Plains", bear)
        alice.activates(boldkinPair, target = bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun maribelsGritPumpsTwo() = scenario {
        alice.battlefield("Plains", "Plains", bear, rabbit)
        alice.hand(maribelsGrit)
        alice.casts(maribelsGrit, targets = listOf(bear, rabbit))
        resolveAll()
        expect {
            bear.power shouldBe 4
            rabbit.power shouldBe 2
        }
    }

    @Test
    fun bunnyRallyPumpsAndScries() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", rabbit, bear)
        alice.hand(bunnyRally)
        alice.library(ogre, giant, mouse)
        alice.casts(bunnyRally)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.toughness shouldBe 3
            alice.library.names.last() shouldBe ogre
        }
    }

    @Test
    fun wardOffDisasterOnlyBigCreatures() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(wardOffDisaster)
        bob.battlefield(bear, giant)
        alice.casts(wardOffDisaster)
        assertEquals(listOf(target(giant)), decision<Decision.Targets>().options)
        targets(giant)
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect { bob.graveyard has giant }
    }

    @Test
    fun echoStrikeHitsATappedCreatureAndGainsWithABat() = scenario {
        alice.battlefield("Plains", "Plains", bat)
        alice.hand(echoStrike)
        bob.battlefield(ogre, tapped = true)
        alice.casts(echoStrike, target = ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.life shouldBe 23
        }
    }

    @Test
    fun disastrousSurgeBouncesTwoAndLoots() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(disastrousSurge, giant)
        alice.library(bear, rabbit)
        bob.battlefield(ogre, mouse)
        alice.casts(disastrousSurge, targets = listOf(ogre, mouse))
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect {
            bob.hand.size shouldBe 2
            alice.hand.names shouldBe listOf(bear, rabbit)
        }
    }

    // CR 118.12: unless its controller pays {4} with a Bird
    @Test
    fun glitteringRefusalAsksForFourWithABird() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        alice.battlefield("Island", "Island", bird)
        alice.hand(glitteringRefusal)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(glitteringRefusal, target = ogre)
        resolveAll()
        if (pending is Decision.May) bob.answers(Answer.YesNo(false))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun pearlOfInsightIsCheaperWithAnOtter() = scenario {
        alice.battlefield("Island", "Island", otter)
        alice.hand(pearlOfInsight)
        alice.library(bear, ogre)
        alice.casts(pearlOfInsight)
        resolveAll()
        expect { alice.hand.names shouldBe listOf(bear, ogre) }
    }

    @Test
    fun braceUpUntapsAndShields() = scenario {
        alice.battlefield("Island")
        alice.battlefield(bear, tapped = true)
        alice.hand(braceUp)
        alice.casts(braceUp, target = bear)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear.power shouldBe 3
            bear hasKeyword Keyword.Hexproof
        }
    }

    // CR 702.41a: affinity for Lizards
    @Test
    fun slateScalesIsCheaperPerLizard() = scenario {
        alice.battlefield("Swamp", lizard, lizard)
        alice.hand(slateScales)
        alice.casts(slateScales, target = lizard)
        resolveAll()
        expect {
            card(lizard, 0).power shouldBe 3
            lizard hasKeyword Keyword.Lifelink
            lizard hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun arcConductionBurnsACreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(arcConduction)
        bob.battlefield(giant)
        alice.casts(arcConduction, target = giant)
        resolveAll()
        expect { bob.graveyard has giant }
    }

    @Test
    fun strengthOfTheSmallWithAMouse() = scenario {
        alice.battlefield("Mountain", mouse, bear)
        alice.hand(strengthOfTheSmall)
        alice.library(ogre)
        alice.casts(strengthOfTheSmall, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Trample
            alice.hand has ogre
        }
    }

    @Test
    fun cheekyPushPingsAndDraws() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(cheekyPush)
        alice.library(ogre)
        alice.casts(cheekyPush, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.hand has ogre
        }
    }

    @Test
    fun haulOffTheRubbishRummagesWithARaccoon() = scenario {
        alice.battlefield("Mountain", "Mountain", raccoon)
        alice.hand(haulOffTheRubbish, giant)
        alice.library(bear)
        bob.battlefield(ogre)
        alice.casts(haulOffTheRubbish, target = ogre)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(giant)
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.graveyard has giant
            alice.hand has bear
        }
    }

    // CR 107.3a: X twice in the cost
    @Test
    fun forTheSharedWealCopiesATokenAndGains() = scenario {
        alice.battlefield("Plains", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(rabbitCall, forTheSharedWeal)
        alice.casts(rabbitCall, paying = listOf("Plains"))
        resolveAll()
        alice.casts(forTheSharedWeal, x = 2, target = "Rabbit Token")
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Rabbit Token" } shouldBe 3
            "Rabbit Token" hasKeyword Keyword.Indestructible
            alice.life shouldBe 23
        }
    }

    @Test
    fun tallStrideUntapsWithReach() = scenario {
        alice.battlefield("Forest")
        alice.battlefield(bear, tapped = true)
        alice.hand(tallStride)
        alice.casts(tallStride, target = bear)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear.toughness shouldBe 5
            bear hasKeyword Keyword.Reach
        }
    }

    @Test
    fun overguardProtectsEverything() = scenario {
        alice.battlefield("Forest", "Forest", bear)
        alice.hand(overguard)
        alice.casts(overguard, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear hasKeyword Keyword.Trample
            bear hasKeyword Keyword.Hexproof
            bear hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun tadpoleSmackDealsTwiceThePower() = scenario {
        alice.battlefield("Forest", "Forest", frog, frog)
        alice.hand(tadpoleSmack)
        bob.battlefield(bear)
        alice.casts(tadpoleSmack, targets = listOf(frog, bear))
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun feralBiteBites() = scenario {
        alice.battlefield("Forest", "Forest", ogre)
        alice.hand(feralBite)
        bob.battlefield(bear)
        alice.casts(feralBite, targets = listOf(ogre, bear))
        resolveAll()
        expect {
            bob.graveyard has bear
            card(ogre).view.obj?.damage shouldBe 0
        }
    }
}
