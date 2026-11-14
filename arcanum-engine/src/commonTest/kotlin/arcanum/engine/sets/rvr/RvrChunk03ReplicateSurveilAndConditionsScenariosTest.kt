package arcanum.engine.sets.rvr

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Supertype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.option
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ravnica Remastered chunk 03 in play: a replicated land animation that lasts, surveil then a creature back from the graveyard, a
 * graveyard copy with exceptions, an upkeep sacrifice that falls back on its source with 3 players, a draw that depends on where the
 * creature came from and a shuffle when a spell targets it, damage replaced by counters, a creature put onto the battlefield
 * blocking, a planeswalker that is a creature on its controller's turn, planeswalker life gain and damage from exile, an artifact
 * animated until its controller's next turn and a card from outside the game, and a copy with an X base power and toughness. */
class RvrChunk03ReplicateSurveilAndConditionsScenariosTest {
    private val awakening = Cards.invent("Rampart Awakening", "{1}{R}", "Sorcery", """
        Replicate {1}{R} (When you cast this spell, copy it for each time you paid its replicate cost. You may choose new targets for the copies.)
        Target Mountain becomes a 3/1 creature. It's still a land.
    """)
    private val peak = Cards.invent("Craggy Peak", null, "Land — Mountain", "({T}: Add {R}.)")
    private val crag = Cards.invent("Ember Crag", null, "Land — Mountain", "({T}: Add {R}.)")
    private val decoct = Cards.inventFaces("Split",
        face("Contrive", "{2}{U/B}{U/B}", "Sorcery", "Gain control of target creature with power 2 or less.", null, null, null),
        face("Decoct", "{3}{U}{B}", "Sorcery", "Surveil 3, then return a creature card from your graveyard to the battlefield.", null, null, null))
    private val vexin = Cards.invent("Vexin, the Manifold", "{U}{B}", "Legendary Creature — Shapeshifter", """
        When Vexin enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        {X}: Vexin becomes a copy of target creature card in your graveyard with mana value X, except its name is Vexin, the Manifold, it's legendary in addition to its other types, and it has this ability.
    """, "1/3")
    private val fiend = Cards.invent("Dirgecaller Fiend", "{3}{B}{B}", "Creature — Demon", """
        Flying
        At the beginning of each player's upkeep, that player sacrifices a creature of their choice. If the player can't, sacrifice this creature.
    """, "4/4")
    private val wobblin = Cards.invent("Wobblin, the Strayed", "{1}{U}", "Legendary Creature — Homunculus", """
        When Wobblin enters, draw a card. If it entered from your library or was cast from your library, draw two cards instead.
        When Wobblin becomes the target of a spell, shuffle Wobblin into its owner's library.
    """, "1/1")
    private val hydra = Cards.invent("Mossgorger Hydra", "{2}{G}{W}{W}", "Creature — Plant Hydra",
        "If damage would be dealt to this creature, put that many +1/+1 counters on it instead.", "1/1")
    private val phantasm = Cards.invent("Driftveil Phantasm", "{2}{U}{U}", "Creature — Illusion",
        "Whenever this creature blocks a creature, you may return this creature to its owner's hand. If you do, you may put a creature card from your hand onto the battlefield blocking that creature.",
        "1/1")
    private val tarrik = Cards.invent("Tarrik Duskblade", "{1}{W}{W}", "Legendary Planeswalker — Gideon", """
        During your turn, Tarrik Duskblade is a 4/4 Human Soldier creature with indestructible that's still a planeswalker.
        Prevent all damage that would be dealt to Tarrik Duskblade during your turn.
        [+1]: Up to one other target creature you control gains your choice of vigilance, lifelink, or indestructible until end of turn.
        [−6]: Exile target nonland permanent.
    """, loyalty = "4")
    private val velka = Cards.invent("Velka, Orzhov Pretender", "{1}{W}{B}", "Legendary Planeswalker — Kaya", """
        [+1]: Exile up to two target cards from a single graveyard. You gain 2 life if at least one creature card was exiled this way.
        [−1]: Exile target nonland permanent with mana value 1 or less.
        [−5]: Velka deals damage to target player equal to the number of cards that player owns in exile and you gain that much life.
    """, loyalty = "3")
    private val vorn = Cards.invent("Vorn, the Grand Artificer", "{4}", "Legendary Planeswalker — Karn", """
        Activated abilities of artifacts your opponents control can't be activated.
        [+1]: Until your next turn, up to one target noncreature artifact becomes an artifact creature with power and toughness each equal to its mana value.
        [−2]: You may reveal an artifact card you own from outside the game or choose a face-up artifact card you own in exile. Put that card into your hand.
    """, loyalty = "5")
    private val morphomass = Cards.invent("Morphomass", "{3}{U}", "Creature — Shapeshifter",
        "You may have this creature enter as a copy of any creature on the battlefield, except it has \"{X}: This creature has base power and toughness X/X.\"",
        "0/0")
    private val sentinel = Cards.invent("Skyward Sentinel", "{2}{W}", "Creature — Bird Soldier", "Flying", "2/4")
    private val brute = Cards.invent("Ashen Brute", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val hexer = Cards.invent("Hollowing Brute", "{2}{B}", "Creature — Ogre",
        "Whenever this creature blocks or becomes blocked by a creature, that creature loses all abilities until end of turn.", "3/3")
    private val warden = Cards.invent("Manyhand Warden", "{2}{W}", "Creature — Human Soldier", """
        This creature can block any number of creatures.
        Whenever this creature blocks a creature, you gain 1 life.
    """, "1/5")
    private val pup = Cards.invent("Mire Pup", "{1}{G}", "Creature — Dog", "", "2/2")
    private val golem = Cards.invent("Bramble Golem", "{3}", "Artifact Creature — Golem", "", "3/3")
    private val idol = Cards.invent("Copper Effigy", "{3}", "Artifact", "{T}: You gain 1 life.")
    private val spark = Cards.invent("Cinder Flick", "{R}", "Instant", "Cinder Flick deals 2 damage to any target.")
    private val bolt = Cards.invent("Searing Rebuke", "{1}{R}", "Instant", "Damage can't be prevented this turn. Searing Rebuke deals 3 damage to any target.")
    private val nudge = Cards.invent("Gentle Nudge", "{W}", "Instant", "Target creature gets +1/+1 until end of turn.")
    private val summons = Cards.invent("Deepwell Summons", "{2}{G}", "Sorcery", "Search your library for a creature card, put it onto the battlefield, then shuffle.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val loyalty = CounterKind.Loyalty

    // ---- Replicate

    /** Replicated once, two Mountains become 3/1 creatures, and they stay creatures after the turn ends. */
    // CR 702.56a; CR 611.2a: no duration, so for good; CR 205.1b: still a Mountain
    @Test
    fun replicatedAwakeningAnimatesTwoMountainsForGood() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", peak, crag)
        alice.library("Plains", "Plains")
        bob.library("Plains", "Plains")
        alice.hand(awakening)
        alice.casts(awakening, target = peak, kicked = 1, paying = listOf("Mountain", "Mountain#1", "Mountain#2", "Mountain#3"))
        playOut(crag)
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            peak.types shouldBe listOf(CardType.Land, CardType.Creature)
            peak.power shouldBe 3
            peak.toughness shouldBe 1
            crag.power shouldBe 3
            assertTrue(peak.subtypes.any { it.name == "Mountain" })
            card("Mountain").types shouldBe listOf(CardType.Land)
        }
    }

    // ---- Surveil

    /** The creature card surveil puts into the graveyard comes back to the battlefield. */
    // CR 701.25a; CR 608.2c: chosen as the spell resolves, not targeted
    @Test
    fun decoctReturnsTheCreatureItSurveiled() = scenario {
        alice.battlefield("Island", "Island", "Island", "Swamp", "Swamp")
        alice.hand(decoct)
        alice.library(brute, "Plains", "Plains")
        alice.casts(decoct, way = CastWay(face = 1))
        playOut(brute)
        expect {
            alice.battlefield has brute
            alice.library.size shouldBe 2
        }
    }

    /** For X = 3 it becomes a copy of the 3/3 in the graveyard, named Vexin, legendary, and it keeps the ability. */
    // CR 707.9b: the exceptions are part of the copy; CR 707.2
    @Test
    fun vexinCopiesACreatureCardInTheGraveyard() = scenario {
        alice.battlefield(vexin, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(brute, pup)
        alice.activates(vexin, target = brute, x = 3)
        resolveAll()
        expect {
            card(vexin).power shouldBe 3
            card(vexin).toughness shouldBe 3
            assertTrue(card(vexin).view.characteristics!!.supertypes.contains(Supertype.Legendary))
            assertEquals(vexin, card(vexin).view.characteristics!!.name)
        }
        // CR 707.9b: it still has this ability, so it can become a copy of another card
        alice.activates(vexin, target = pup, x = 2)
        resolveAll()
        expect { card(vexin).power shouldBe 2 }
    }

    // ---- Conditions

    /** Carol has no creature in her upkeep, so the Fiend is sacrificed; in Bob's upkeep Bob sacrificed his and the Fiend stayed. */
    // CR 101.4, 701.21a; CR 603.2: each player's upkeep
    @Test
    fun fiendIsSacrificedWhenThePlayerHasNoCreature() = scenario(players = 3) {
        alice.battlefield(fiend)
        bob.battlefield(pup)
        alice.library("Plains", "Plains")
        bob.library("Plains", "Plains")
        carol.library("Plains", "Plains")
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect {
            bob.graveyard has pup
            alice.battlefield has fiend
        }
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect { alice.graveyard has fiend }
    }

    /** Cast from the hand, it draws one card. */
    // CR 603.2
    @Test
    fun wobblinFromTheHandDrawsOne() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(wobblin)
        alice.library("Plains", "Plains", "Plains")
        alice.casts(wobblin)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    /** Put onto the battlefield from the library, it draws two. */
    // CR 614.1a ("instead"), CR 400.7
    @Test
    fun wobblinFromTheLibraryDrawsTwo() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(summons)
        alice.library(wobblin, "Plains", "Plains", "Plains")
        alice.casts(summons)
        resolveAll()
        alice.chooses(wobblin)
        resolveAll()
        expect {
            alice.battlefield has wobblin
            alice.hand.size shouldBe 2
        }
    }

    /** Targeted by a spell, it is shuffled into its owner's library; the spell then has no legal target. */
    // CR 115.10, 701.24a
    @Test
    fun wobblinTargetedByASpellIsShuffledAway() = scenario {
        alice.battlefield(wobblin, "Plains")
        alice.hand(nudge)
        alice.library("Island", "Island")
        alice.casts(nudge, target = wobblin)
        resolveAll()
        expect {
            alice.library has wobblin
            alice.graveyard has nudge
        }
    }

    /** 2 damage becomes two +1/+1 counters; no damage is marked. */
    // CR 614.1a
    @Test
    fun hydraTurnsDamageIntoCounters() = scenario {
        alice.battlefield(hydra)
        bob.battlefield("Mountain")
        bob.hand(spark)
        turn(active = bob)
        bob.casts(spark, target = hydra)
        resolveAll()
        expect {
            hydra.counters(p1p1) shouldBe 2
            hydra.power shouldBe 3
            card(hydra).view.obj?.damage shouldBe 0
        }
    }

    /** Damage that can't be prevented is still replaced: replacing isn't preventing. */
    // CR 614.1a, 615.12
    @Test
    fun hydraReplacesDamageThatCantBePrevented() = scenario {
        alice.battlefield(hydra)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(bolt)
        turn(active = bob)
        bob.casts(bolt, target = hydra)
        resolveAll()
        expect {
            alice.battlefield has hydra
            hydra.counters(p1p1) shouldBe 3
        }
    }

    /** The Phantasm blocks, goes back to the hand and a flyer enters blocking the attacker: the attacker stays blocked and the two
     * deal combat damage to each other. */
    // CR 509.4: put onto the battlefield blocking; CR 506.4: the attacker remains blocked
    @Test
    fun phantasmSwapsInABlocker() = scenario {
        alice.battlefield(phantasm)
        alice.hand(sentinel)
        bob.battlefield(brute)
        turn(active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(brute)
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(phantasm to brute)
        playOut(sentinel)
        expect {
            alice.hand has phantasm
            alice.battlefield has sentinel
        }
        advanceTo(TurnStep.EndOfCombat)
        expect {
            alice.life shouldBe 20
            card(sentinel).view.obj?.damage shouldBe 3
            card(brute).view.obj?.damage shouldBe 2
        }
    }

    /** A creature put onto the battlefield blocking makes "becomes blocked by a creature" trigger for it, though the attacker
     * was blocked already: the flyer loses flying. */
    // CR 509.3d
    @Test
    fun aCreatureEnteringBlockingTriggersBecomesBlockedBy() = scenario {
        alice.battlefield(phantasm)
        alice.hand(sentinel)
        bob.battlefield(hexer)
        turn(active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(hexer)
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(phantasm to hexer)
        playOut(sentinel)
        expect {
            alice.battlefield has sentinel
            sentinel lacksKeyword Keyword.Flying.kind
        }
    }

    /** Blocking two attackers, "whenever this creature blocks a creature" triggers twice. */
    // CR 509.3b
    @Test
    fun blocksACreatureTriggersForEachAttacker() = scenario {
        alice.battlefield(warden)
        bob.battlefield(brute, pup)
        turn(active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(brute, pup)
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(warden to brute, warden to pup)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    /** On its controller's turn it is a 4/4 indestructible Human Soldier creature and damage to it is prevented; on the opponent's
     * turn it is only a planeswalker. */
    // CR 205.1b, 611.3a; CR 615.1
    @Test
    fun tarrikIsACreatureOnlyDuringItsControllersTurn() = scenario {
        alice.battlefield(tarrik, counters = listOf(loyalty to 4))
        alice.battlefield("Mountain")
        alice.hand(spark)
        bob.library("Plains", "Plains")
        expect {
            tarrik.types shouldBe listOf(CardType.Planeswalker, CardType.Creature)
            tarrik.power shouldBe 4
            tarrik hasKeyword Keyword.Indestructible.kind
        }
        alice.casts(spark, target = tarrik)
        resolveAll()
        expect { tarrik.loyalty shouldBe 4 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { tarrik.types shouldBe listOf(CardType.Planeswalker) }
    }

    /** +1: the other creature gains the chosen keyword. */
    // CR 606.3; CR 115.3: "other"
    @Test
    fun tarrikGivesTheChosenKeyword() = scenario {
        alice.battlefield(tarrik, counters = listOf(loyalty to 4))
        alice.battlefield(pup)
        alice.activates(tarrik, ability = 0, target = pup)
        resolveAll()
        option("lifelink")
        expect {
            pup hasKeyword Keyword.Lifelink.kind
            pup lacksKeyword Keyword.Vigilance.kind
        }
    }

    /** +1 exiling a creature card gains 2 life; −5 deals damage equal to the cards Bob owns in exile and gains that much. */
    // CR 608.2c; CR 120.3
    @Test
    fun velkaGainsAndDrains() = scenario {
        alice.battlefield(velka, counters = listOf(loyalty to 6))
        bob.graveyard(brute, "Island", "Island")
        bob.exile("Plains")
        alice.library("Plains", "Plains")
        bob.library("Plains", "Plains")
        alice.activates(velka, ability = 0, targets = listOf(brute, "Island"))
        resolveAll()
        expect { alice.life shouldBe 22 }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates(velka, ability = 2, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.life shouldBe 25
        }
    }

    /** +1: the Idol (mana value 3) is a 3/3 artifact creature through Bob's turn and stops being one when Alice's next turn begins. */
    // CR 611.2b; CR 613.4b
    @Test
    fun vornAnimatesAnArtifactUntilItsControllersNextTurn() = scenario {
        alice.battlefield(vorn, counters = listOf(loyalty to 5))
        alice.battlefield(idol)
        alice.library("Plains", "Plains")
        bob.library("Plains", "Plains")
        alice.activates(vorn, ability = 0, target = idol)
        resolveAll()
        expect {
            idol.types shouldBe listOf(CardType.Artifact, CardType.Creature)
            idol.power shouldBe 3
            idol.toughness shouldBe 3
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { idol.power shouldBe 3 }
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { idol.types shouldBe listOf(CardType.Artifact) }
    }

    /** −2: an artifact card from outside the game is put into the hand. */
    // CR 400.11b
    @Test
    fun vornFetchesAnArtifactFromOutsideTheGame() = scenario {
        alice.battlefield(vorn, counters = listOf(loyalty to 5))
        alice.outside(golem)
        alice.activates(vorn, ability = 1)
        resolveAll()
        alice.chooses(golem)
        resolveAll()
        expect { alice.hand has golem }
    }

    /** It enters as a copy of the 3/3 with the X ability; X = 5 makes it 5/5. */
    // CR 707.9a; CR 613.4b
    @Test
    fun morphomassCopiesAndGrows() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(morphomass)
        bob.battlefield(brute)
        alice.casts(morphomass)
        playOut(brute)
        expect { card(morphomass).power shouldBe 3 }
        alice.activates(morphomass, x = 5)
        resolveAll()
        expect {
            card(morphomass).power shouldBe 5
            card(morphomass).toughness shouldBe 5
        }
    }
}
