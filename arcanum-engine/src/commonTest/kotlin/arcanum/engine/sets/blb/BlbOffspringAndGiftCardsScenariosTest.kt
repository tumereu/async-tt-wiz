package arcanum.engine.sets.blb

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.Blb.bear
import arcanum.engine.sets.blb.Blb.charm
import arcanum.engine.sets.blb.Blb.flyer
import arcanum.engine.sets.blb.Blb.giant
import arcanum.engine.sets.blb.Blb.heal
import arcanum.engine.sets.blb.Blb.idol
import arcanum.engine.sets.blb.Blb.legend
import arcanum.engine.sets.blb.Blb.mouse
import arcanum.engine.sets.blb.Blb.ogre
import arcanum.engine.sets.blb.Blb.p1p1
import arcanum.engine.sets.blb.Blb.ponder
import arcanum.engine.sets.blb.Blb.rabbit
import arcanum.engine.sets.blb.Blb.shock
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Bloomburrow's offspring creatures and gift cards in play (parser chunks 01–02): what each card does beyond the offspring and gift already played in [BlbOffspringScenariosTest] and [BlbGiftAndForageScenariosTest]. */
class BlbOffspringAndGiftCardsScenariosTest {
    private val pluckyHareguard = Cards.invent("Plucky Hareguard", "{2}{W}", "Creature — Rabbit Soldier", """
        Offspring {1} (You may pay an additional {1} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        When this creature enters, target creature you control gets +1/+1 until end of turn.
    """, "3/2")
    private val burrowBattlelord = Cards.invent("Burrow Battlelord", "{2}{W}{W}", "Creature — Rabbit Knight", """
        Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        Whenever you attack, choose one —
        • Create a 1/1 white Rabbit creature token that's tapped and attacking.
        • Attacking creatures you control get +1/+1 until end of turn.
    """, "4/4")
    private val sparrowSquadron = Cards.invent("Sparrow Squadron", "{2}{U}", "Creature — Bird Scout", """
        Offspring {3} (You may pay an additional {3} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        Flying
        When this creature enters, target creature you control gains flying until end of turn.
    """, "2/2")
    private val puddleWhipper = Cards.invent("Puddle Whipper", "{3}{U}", "Creature — Frog Wizard", """
        Offspring {1}{U} (You may pay an additional {1}{U} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        When this creature enters, tap up to one target creature and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
    """, "3/3")
    private val stormnetTutor = Cards.invent("Stormnet Tutor", "{1}{U}", "Creature — Otter Wizard", """
        Offspring {4} (You may pay an additional {4} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        When this creature enters, look at the top four cards of your library. You may reveal a noncreature, nonland card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
    """, "1/2")
    private val nightskySeer = Cards.invent("Nightsky Seer", "{2}{B}", "Creature — Bat Warlock", """
        Offspring {B} (You may pay an additional {B} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        Flying
        At the beginning of your upkeep, reveal the top card of your library and put that card into your hand. You lose life equal to its mana value.
    """, "2/3")
    private val shimmerscaleStalker = Cards.invent("Shimmerscale Stalker", "{B}", "Creature — Lizard Assassin", """
        Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        Landfall — Whenever a land you control enters, this creature deals 1 damage to target opponent.
    """, "1/2")
    private val skyveilAcolyte = Cards.invent("Skyveil Acolyte", "{1}{B}", "Creature — Bat Cleric", """
        Offspring {2}{B} (You may pay an additional {2}{B} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        Flying
        This creature can't block.
        Whenever you gain life, each opponent loses 1 life.
    """, "2/1")
    private val spikehideBully = Cards.invent("Spikehide Bully", "{3}{B}", "Creature — Rat Rogue", """
        Offspring {3} (You may pay an additional {3} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        When this creature enters, target opponent loses 3 life unless they sacrifice a nonland permanent of their choice or discard a card.
    """, "4/3")
    private val sparkleConjurer = Cards.invent("Sparkle Conjurer", "{1}{R}", "Creature — Otter Wizard", """
        Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        Whenever you cast a noncreature spell, this creature deals 1 damage to each opponent.
    """, "2/2")
    private val manyTailedMouse = Cards.invent("Many-Tailed Mouse", "{1}{R}", "Creature — Mouse Soldier", """
        Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        At the beginning of combat on your turn, target Mouse you control gains your choice of double strike or trample until end of turn.
    """, "1/2")
    private val ashtrailRunner = Cards.invent("Ashtrail Runner", "{1}{R}", "Creature — Lizard Warlock", """
        Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        When this creature dies, it deals 1 damage to target player.
    """, "2/1")
    private val cloverfieldCadet = Cards.invent("Cloverfield Cadet", "{G}", "Creature — Rabbit Warrior", """
        Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        Trample
        Whenever a creature you control becomes the target of a spell or ability an opponent controls, put a +1/+1 counter on target creature you control other than that creature.
    """, "2/1")
    private val gentleThicketkeeper = Cards.invent("Gentle Thicketkeeper", "{1}{G}", "Creature — Possum Druid", """
        Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        {T}: Add one mana of any color.
        {T}: Put a +1/+1 counter on this creature.
    """, "2/2")
    private val crumbsForCourage = Cards.invent("Crumbs for Courage", "{W}", "Instant", """
        Gift a Food (You may promise an opponent a gift as you cast this spell. If you do, they create a Food token before its other effects. It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        Target creature you control gets +2/+2 until end of turn. If the gift was promised, that creature also gains indestructible until end of turn.
    """)
    private val daybreakAccord = Cards.invent("Daybreak Accord", "{1}{W}", "Instant", """
        Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
        You and permanents you control gain hexproof until end of turn. If the gift was promised, permanents you control also gain indestructible until end of turn.
    """)
    private val morningdewRemedy = Cards.invent("Morningdew Remedy", "{2}{W}", "Sorcery", """
        Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
        Return up to two target creature cards each with mana value 2 or less from your graveyard to the battlefield. If the gift was promised, instead return up to three target creature cards each with mana value 2 or less from your graveyard to the battlefield.
    """)
    private val farewellBreeze = Cards.invent("Farewell Breeze", "{W}{W}", "Instant", """
        Gift a tapped Fish (You may promise an opponent a gift as you cast this spell. If you do, they create a tapped 1/1 blue Fish creature token before its other effects.)
        Exile target nontoken creature. If the gift wasn't promised, return that card to the battlefield under its owner's control with a +1/+1 counter on it at the beginning of the next end step.
    """)
    private val meteorBenediction = Cards.invent("Meteor Benediction", "{3}{W}{W}", "Sorcery", """
        Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
        Destroy all creatures. If the gift was promised, return a creature card put into your graveyard this way to the battlefield under your control.
    """)
    private val pawsnatch = Cards.invent("Pawsnatch", "{2}{U}{U}", "Enchantment — Aura", """
        Gift a card (You may promise an opponent a gift as you cast this spell. If you do, when it enters, they draw a card.)
        Enchant creature
        When this Aura enters, tap enchanted creature. If the gift wasn't promised, put three stun counters on it.
        You control enchanted creature.
    """)
    private val undertowsClaim = Cards.invent("Undertow's Claim", "{U}{U}", "Instant", """
        Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
        Counter target creature spell. If the gift was promised, instead counter target spell.
    """)
    private val thoughtEddy = Cards.invent("Thought Eddy", "{4}{U}", "Sorcery", """
        Gift a tapped Fish (You may promise an opponent a gift as you cast this spell. If you do, they create a tapped 1/1 blue Fish creature token before its other effects.)
        Target player draws three cards. If the gift was promised, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
    """)
    private val twistingRenewal = Cards.invent("Twisting Renewal", "{3}{B}{B}", "Sorcery", """
        Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
        Return target creature card from your graveyard to the battlefield. Then if the gift was promised and that creature isn't legendary, create a token that's a copy of that creature, except it's 1/1.
    """)
    private val devouredByWant = Cards.invent("Devoured by Want", "{1}{B}{B}", "Instant", """
        Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
        Target opponent sacrifices a creature with the greatest power among creatures they control. If the gift was promised, return target creature card from your graveyard to your hand.
    """)
    private val sharpclawsRobbery = Cards.invent("Sharpclaw's Robbery", "{B}{B}", "Sorcery", """
        Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
        Target opponent reveals their hand. You choose a nonland card from it. Exile that card. If the gift was promised, you may cast that card for as long as it remains exiled, and mana of any type can be spent to cast it.
    """)
    private val blossomBurst = Cards.invent("Blossom Burst", "{1}{R}", "Instant", """
        Gift a Treasure (You may promise an opponent a gift as you cast this spell. If you do, they create a Treasure token before its other effects. It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        Blossom Burst deals 2 damage to target creature. If the gift was promised, Blossom Burst also deals 3 damage to that creature's controller.
    """)
    private val mushroomTonic = Cards.invent("Mushroom Tonic", "{1}{R}", "Instant", """
        Gift a tapped Fish (You may promise an opponent a gift as you cast this spell. If you do, they create a tapped 1/1 blue Fish creature token before its other effects.)
        As an additional cost to cast this spell, discard a card.
        Target player draws two cards. If the gift was promised, target creature you control gets +2/+0 until end of turn.
    """)
    private val meadowMuster = Cards.invent("Meadow Muster", "{2}{R}", "Instant", """
        Gift a Food (You may promise an opponent a gift as you cast this spell. If you do, they create a Food token before its other effects. It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        Creatures you control get +2/+0 until end of turn. If the gift was promised, target creature you control gains first strike until end of turn.
    """)
    private val brushfireWail = Cards.invent("Brushfire Wail", "{1}{R}{R}", "Sorcery", """
        Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
        Brushfire Wail deals 2 damage to each creature. If the gift was promised, instead Brushfire Wail deals 1 damage to any target and 2 damage to each creature.
    """)
    private val reedbedScuffle = Cards.invent("Reedbed Scuffle", "{G}", "Sorcery", """
        Gift a tapped Fish (You may promise an opponent a gift as you cast this spell. If you do, they create a tapped 1/1 blue Fish creature token before its other effects.)
        Choose target creature you control and target creature you don't control. Put a +1/+1 counter on the creature you control if the gift was promised. Then those creatures fight each other.
    """)
    private val thriftySalvage = Cards.invent("Thrifty Salvage", "{1}{G}", "Instant", """
        Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
        Return target permanent card from your graveyard to your hand. If the gift was promised, instead return two target permanent cards from your graveyard to your hand.
    """)
    private val grindAway = Cards.invent("Grind Away", "{1}{G}", "Sorcery", """
        Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
        Destroy target artifact or enchantment. If the gift was promised, instead destroy two target artifacts and/or enchantments.
    """)
    private val cometforgedBlade = Cards.invent("Cometforged Blade", "{4}", "Artifact — Equipment", """
        Gift a tapped Fish (You may promise an opponent a gift as you cast this spell. If you do, when it enters, they create a tapped 1/1 blue Fish creature token.)
        When this Equipment enters, if the gift was promised, attach this Equipment to target creature you control.
        Equipped creature gets +3/+3 and loses flying.
        Equip {3}
    """)

    // ---- Chunk 01: the offspring creatures' other abilities (offspring itself: BlbOffspringScenariosTest)

    @Test
    fun pluckyHareguardPumpsACreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(pluckyHareguard)
        alice.casts(pluckyHareguard, kicked = 0)
        resolveAll()
        targets(bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear.toughness shouldBe 3
        }
    }

    // CR 508.1, 603.3c: "whenever you attack" triggers once; its mode is chosen as it goes on the Stack;
    // CR 506.3, 508.4: the token enters tapped and attacking without being declared
    @Test
    fun burrowBattlelordMakesAnAttackingRabbit() = scenario {
        alice.battlefield(burrowBattlelord)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(burrowBattlelord)
        modes(0)
        resolveAll()
        expect { card("Rabbit Token").tapped shouldBe true }
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 15 }
    }

    @Test
    fun burrowBattlelordPumpsTheAttackers() = scenario {
        alice.battlefield(burrowBattlelord, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(burrowBattlelord, bear)
        modes(1)
        resolveAll()
        expect {
            burrowBattlelord.power shouldBe 5
            bear.power shouldBe 3
        }
    }

    @Test
    fun sparrowSquadronLendsFlying() = scenario {
        alice.battlefield("Island", "Island", "Island", bear)
        alice.hand(sparrowSquadron)
        alice.casts(sparrowSquadron, kicked = 0)
        resolveAll()
        targets(bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Flying }
    }

    // CR 122.1d: a stun counter
    @Test
    fun puddleWhipperTapsAndStuns() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(puddleWhipper)
        bob.battlefield(ogre)
        alice.casts(puddleWhipper, kicked = 0)
        resolveAll()
        targets(ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 1
        }
    }

    // CR 701.20a: a revealed card; the rest go to the bottom in a random order
    @Test
    fun stormnetTutorFindsANoncreatureNonlandCard() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(stormnetTutor)
        alice.library(bear, ponder, "Island", ogre, giant)
        alice.casts(stormnetTutor, kicked = 0)
        resolveAll()
        alice.chooses(ponder)
        resolveAll()
        expect {
            alice.hand has ponder
            alice.library.names.first() shouldBe giant
            alice.library.size shouldBe 4
        }
    }

    @Test
    fun nightskySeerDrawsInTheUpkeepForLife() = scenario {
        alice.battlefield(nightskySeer)
        alice.library(ogre, "Swamp")
        bob.library("Forest")
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect {
            alice.hand has ogre
            alice.life shouldBe 17
        }
    }

    // CR 207.2c: landfall is a land entering under your control
    @Test
    fun shimmerscaleStalkerPingsOnLandfall() = scenario {
        alice.battlefield(shimmerscaleStalker)
        alice.hand("Swamp")
        alice.plays("Swamp")
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun skyveilAcolyteDrainsOnLifeGain() = scenario {
        alice.battlefield("Plains", skyveilAcolyte)
        alice.hand(heal)
        alice.casts(heal)
        resolveAll()
        expect {
            alice.life shouldBe 22
            bob.life shouldBe 19
        }
    }

    // CR 118.12: "unless" — with nothing to sacrifice or discard, the opponent loses the life
    @Test
    fun spikehideBullyWithNothingToGiveUp() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(spikehideBully)
        alice.casts(spikehideBully, kicked = 0)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun spikehideBullySacrificeInstead() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(spikehideBully)
        bob.battlefield(idol)
        alice.casts(spikehideBully, kicked = 0)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        // CR 118.12: bob pays one of the costs instead
        may(true)
        if (pending is Decision.ChooseOption) option("Sacrifice")
        if (pending is Decision.ChooseObjects) bob.chooses(idol)
        resolveAll()
        expect {
            bob.life shouldBe 20
            bob.graveyard has idol
        }
    }

    @Test
    fun sparkleConjurerPingsOnANoncreatureSpell() = scenario {
        alice.battlefield("Island", sparkleConjurer)
        alice.hand(ponder)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun manyTailedMouseGivesDoubleStrike() = scenario {
        alice.battlefield(manyTailedMouse)
        advanceTo(TurnStep.BeginningOfCombat)
        targetsIfAsked(manyTailedMouse)
        resolveAll()
        option("double")
        resolveAll()
        expect {
            manyTailedMouse hasKeyword Keyword.DoubleStrike
            manyTailedMouse lacksKeyword Keyword.Trample
        }
    }

    // CR 603.10a: a dies trigger looks back; "it" deals the damage as it last existed
    @Test
    fun ashtrailRunnerPingsAsItDies() = scenario {
        alice.battlefield("Mountain", ashtrailRunner)
        alice.hand(shock)
        alice.casts(shock, target = ashtrailRunner)
        resolveAll()
        targets("bob")
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    // CR 115.10: becoming the target of an opponent's spell
    @Test
    fun cloverfieldCadetRewardsAnotherCreature() = scenario {
        alice.battlefield(cloverfieldCadet, bear)
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(active = bob)
        bob.casts(shock, target = bear)
        targetsIfAsked(cloverfieldCadet)
        resolveAll()
        expect {
            cloverfieldCadet.counters(p1p1) shouldBe 1
            alice.graveyard has bear
        }
    }

    @Test
    fun cloverfieldCadetIgnoresYourOwnSpells() = scenario {
        alice.battlefield("Forest", cloverfieldCadet, bear)
        alice.hand(Blb.pump)
        alice.casts(Blb.pump, target = bear)
        resolveAll()
        expect { cloverfieldCadet.counters(p1p1) shouldBe 0 }
    }

    @Test
    fun gentleThicketkeeperGrowsItself() = scenario {
        alice.battlefield(gentleThicketkeeper)
        alice.activates(gentleThicketkeeper, ability = 1)
        resolveAll()
        expect { gentleThicketkeeper.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun gentleThicketkeeperMakesAnyColor() = scenario {
        alice.battlefield(gentleThicketkeeper)
        alice.activates(gentleThicketkeeper, ability = 0, color = Color.Blue)
        expect { alice.pool.size shouldBe 1 }
    }

    // ---- Chunk 02: gift spells and permanents (gift itself: BlbGiftAndForageScenariosTest)

    // CR 702.174j: the gift happens first
    @Test
    fun crumbsForCourageMakesIndestructibleWhenPromised() = scenario {
        alice.battlefield("Plains", bear)
        alice.hand(crumbsForCourage)
        alice.casts(crumbsForCourage, target = bear, kicked = 1)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Indestructible
            bob.battlefield has "Food Token"
        }
    }

    @Test
    fun crumbsForCourageOnlyPumpsWhenNotPromised() = scenario {
        alice.battlefield("Plains", bear)
        alice.hand(crumbsForCourage)
        alice.casts(crumbsForCourage, target = bear, kicked = 0)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear lacksKeyword Keyword.Indestructible
        }
    }

    // CR 702.11d: a player with hexproof can't be targeted by opponents
    @Test
    fun daybreakAccordShieldsYouAndYourPermanents() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(daybreakAccord)
        bob.library(ogre)
        bob.battlefield("Mountain")
        bob.hand(shock)
        alice.casts(daybreakAccord, kicked = 1)
        resolveAll()
        expect {
            bob.hand has ogre
            bear hasKeyword Keyword.Hexproof
            bear hasKeyword Keyword.Indestructible
        }
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.casts(shock, target = "alice") }
    }

    @Test
    fun morningdewRemedyReturnsTwoSmallCreatures() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(morningdewRemedy)
        alice.graveyard(bear, rabbit, ogre)
        alice.casts(morningdewRemedy, targets = listOf(bear, rabbit), kicked = 0)
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.battlefield has rabbit
            alice.graveyard has ogre
        }
    }

    // CR 603.7: the delayed trigger returns it at the next end step with a counter
    @Test
    fun farewellBreezeFlickersUntilTheEndStepWhenNotPromised() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(farewellBreeze)
        bob.battlefield(ogre)
        alice.casts(farewellBreeze, target = ogre, kicked = 0)
        resolveAll()
        expect { bob.exile has ogre }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.battlefield has ogre
            ogre.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun farewellBreezeExilesForGoodWhenPromised() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(farewellBreeze)
        bob.battlefield(ogre)
        alice.casts(farewellBreeze, target = ogre, kicked = 1)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.exile has ogre
            bob.battlefield has "Fish Token"
        }
    }

    @Test
    fun meteorBenedictionReturnsOneOfYoursWhenPromised() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(meteorBenediction)
        bob.battlefield(ogre)
        bob.library(giant)
        alice.casts(meteorBenediction, kicked = 1)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect {
            alice.battlefield has bear
            bob.graveyard has ogre
            bob.hand has giant
        }
    }

    // CR 613.1b: control-changing Aura; CR 122.1d: three stun counters when not promised
    @Test
    fun pawsnatchStealsAndStuns() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(pawsnatch)
        bob.battlefield(ogre)
        alice.casts(pawsnatch, target = ogre, kicked = 0)
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 3
        }
    }

    // CR 702.174m: the promised version may target any spell
    @Test
    fun undertowsClaimCountersAnySpellWhenPromised() = scenario {
        bob.battlefield("Island")
        bob.hand(ponder)
        bob.library(ogre, giant)
        alice.battlefield("Island", "Island")
        alice.hand(undertowsClaim)
        turn(active = bob)
        bob.casts(ponder)
        bob.passes()
        alice.casts(undertowsClaim, target = ponder, kicked = 1)
        resolveAll()
        expect {
            bob.graveyard has ponder
            bob.hand.names shouldBe listOf(ogre)
        }
    }

    @Test
    fun undertowsClaimCantTargetANoncreatureSpellUnpromised() = scenario {
        bob.battlefield("Island")
        bob.hand(ponder)
        bob.library(ogre)
        alice.battlefield("Island", "Island")
        alice.hand(undertowsClaim)
        turn(active = bob)
        bob.casts(ponder)
        bob.passes()
        assertFailsWith<IllegalArgumentException> { alice.casts(undertowsClaim, target = ponder, kicked = 0) }
    }

    @Test
    fun thoughtEddyDrawsAndStunsWhenPromised() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(thoughtEddy)
        alice.library(bear, rabbit, mouse)
        bob.battlefield(ogre)
        alice.casts(thoughtEddy, targets = listOf("alice", ogre), kicked = 1)
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 1
            bob.battlefield has "Fish Token"
        }
    }

    // CR 707.9b: the copy is 1/1
    @Test
    fun twistingRenewalAlsoMakesA1By1CopyWhenPromised() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(twistingRenewal)
        alice.graveyard(ogre)
        bob.library(bear)
        alice.casts(twistingRenewal, target = ogre, kicked = 1)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == ogre } shouldBe 2
            card(ogre, 0).power shouldBe 3
            card(ogre, 1).power shouldBe 1
        }
    }

    @Test
    fun twistingRenewalMakesNoCopyOfALegend() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(twistingRenewal)
        alice.graveyard(legend)
        bob.library(bear)
        alice.casts(twistingRenewal, target = legend, kicked = 1)
        resolveAll()
        expect { alice.battlefield.names.count { it == legend } shouldBe 1 }
    }

    @Test
    fun devouredByWantTakesTheBiggestAndReturnsACard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(devouredByWant)
        alice.graveyard(giant)
        bob.battlefield(bear, ogre)
        bob.library(rabbit)
        alice.casts(devouredByWant, targets = listOf("bob", giant), kicked = 1)
        resolveAll()
        expect {
            bob.graveyard has ogre
            bob.battlefield has bear
            alice.hand has giant
        }
    }

    // CR 609.4b: mana of any type may be spent to cast the exiled card
    @Test
    fun sharpclawsRobberyLetsYouCastTheExiledCard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(sharpclawsRobbery)
        bob.hand(ogre, "Forest")
        bob.library(rabbit)
        alice.casts(sharpclawsRobbery, target = "bob", kicked = 1)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { bob.exile has ogre }
        alice.casts(ogre)
        resolveAll()
        expect { alice.battlefield has ogre }
    }

    @Test
    fun blossomBurstHitsTheControllerWhenPromised() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(blossomBurst)
        bob.battlefield(bear)
        alice.casts(blossomBurst, target = bear, kicked = 1)
        resolveAll()
        expect {
            bob.graveyard has bear
            bob.life shouldBe 17
            bob.battlefield has "Treasure Token"
        }
    }

    // CR 601.2b, 601.2h: the discard is an additional cost
    @Test
    fun mushroomTonicDiscardsDrawsAndPumps() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(mushroomTonic, giant)
        alice.library(rabbit, mouse)
        alice.casts(mushroomTonic, targets = listOf("alice", bear), kicked = 1, choosing = listOf(giant))
        resolveAll()
        expect {
            alice.graveyard has giant
            alice.hand.names shouldBe listOf(rabbit, mouse)
            bear.power shouldBe 4
        }
    }

    @Test
    fun meadowMusterPumpsTheTeamAndGivesFirstStrike() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear, ogre)
        alice.hand(meadowMuster)
        alice.casts(meadowMuster, target = bear, kicked = 1)
        resolveAll()
        expect {
            bear.power shouldBe 4
            ogre.power shouldBe 5
            bear hasKeyword Keyword.FirstStrike
            ogre lacksKeyword Keyword.FirstStrike
        }
    }

    @Test
    fun brushfireWailSweepsAndPingsWhenPromised() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(brushfireWail)
        bob.battlefield(bear, giant)
        bob.library(rabbit)
        alice.casts(brushfireWail, target = "bob", kicked = 1)
        resolveAll()
        expect {
            bob.life shouldBe 19
            bob.graveyard has bear
            card(giant).view.obj?.damage shouldBe 2
        }
    }

    @Test
    fun brushfireWailOnlySweepsWhenNotPromised() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(brushfireWail)
        bob.battlefield(bear)
        alice.casts(brushfireWail, kicked = 0)
        resolveAll()
        expect {
            bob.life shouldBe 20
            bob.graveyard has bear
        }
    }

    // CR 701.14a: fight
    @Test
    fun reedbedScuffleCountersThenFights() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(reedbedScuffle)
        bob.battlefield(ogre)
        alice.casts(reedbedScuffle, targets = listOf(bear, ogre), kicked = 1)
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.graveyard has bear
        }
    }

    @Test
    fun reedbedScuffleWithoutTheCounter() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(reedbedScuffle)
        bob.battlefield(ogre)
        alice.casts(reedbedScuffle, targets = listOf(bear, ogre), kicked = 0)
        resolveAll()
        expect {
            bob.battlefield has ogre
            alice.graveyard has bear
        }
    }

    @Test
    fun thriftySalvageReturnsTwoWhenPromised() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(thriftySalvage)
        alice.graveyard(bear, idol)
        bob.library(rabbit)
        alice.casts(thriftySalvage, targets = listOf(bear, idol), kicked = 1)
        resolveAll()
        expect {
            alice.hand has bear
            alice.hand has idol
        }
    }

    @Test
    fun grindAwayDestroysTwoWhenPromised() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(grindAway)
        bob.battlefield(idol, charm)
        bob.library(rabbit)
        alice.casts(grindAway, targets = listOf(idol, charm), kicked = 1)
        resolveAll()
        expect {
            bob.graveyard has idol
            bob.graveyard has charm
        }
    }

    // CR 603.4: the attach trigger checks the gift; CR 301.5: attached, equipped creature gets +3/+3
    @Test
    fun cometforgedBladeAttachesWhenPromised() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", flyer)
        alice.hand(cometforgedBlade)
        alice.casts(cometforgedBlade, kicked = 1)
        resolveAll()
        targetsIfAsked(flyer)
        resolveAll()
        expect {
            flyer.power shouldBe 5
            flyer lacksKeyword Keyword.Flying
            bob.battlefield has "Fish Token"
        }
    }
}
