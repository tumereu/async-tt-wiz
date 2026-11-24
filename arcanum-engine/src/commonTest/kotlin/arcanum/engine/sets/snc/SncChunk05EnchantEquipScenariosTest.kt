package arcanum.engine.sets.snc

import arcanum.api.CounterKind
import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Streets of New Capenna chunk 05 in play: Auras and Equipment, including a granted ability that names the Aura, "attack that player"
 * requirements with three players, and "Equip planeswalker". */
class SncChunk05EnchantEquipScenariosTest {
    private val leash = Cards.invent("Snc05 Kidnapper's Leash", "{1}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature can't attack or block and has "{7}: Snc05 Kidnapper's Leash's controller sacrifices it and draws a card. Activate only as a sorcery."
    """)
    private val fugitive = Cards.invent("Snc05 Most Hunted Fugitive", "{2}{U}", "Enchantment — Aura", """
        Enchant creature
        All creatures attack enchanted creature's controller each combat if able.
        When enchanted creature dies, draw a card.
    """)
    private val poster = Cards.invent("Snc05 Bounty Poster", "{2}{G}", "Enchantment — Aura", """
        Flash
        Enchant creature
        Enchanted creature gets +2/+1.
        When enchanted creature dies, create two Treasure tokens.
    """)
    private val ward = Cards.invent("Snc05 Revenant Ward", "{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+1.
        You may cast this card from your graveyard by paying {2}{W} rather than paying its mana cost.
    """)
    private val slumber = Cards.invent("Snc05 Concrete Slumber", "{2}{U}{U}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, tap enchanted creature and you create a 1/1 blue Fish creature token with "This token can't be blocked."
        Enchanted creature doesn't untap during its controller's untap step.
    """)
    private val fingers = Cards.invent("Snc05 Light Fingers Charm", "{R}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature has menace and "Whenever this creature deals combat damage to a player, create a Treasure token." (It can't be blocked except by two or more creatures. The token is an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        When enchanted creature dies, draw a card.
    """)
    private val spitter = Cards.invent("Snc05 Spark Lobber", "{1}", "Artifact — Equipment", """
        Equipped creature has "{1}: This creature deals 1 damage to target creature that's blocking it."
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val knuckles = Cards.invent("Snc05 Iron Fist Duo", "{4}", "Artifact — Equipment", """
        When you cast this spell, copy it. (The copy becomes a token.)
        Equipped creature has double strike as long as two or more Equipment are attached to it.
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val bangle = Cards.invent("Snc05 Tin Bangle", "{1}", "Artifact — Equipment", """
        Equipped creature gets +1/+0.
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val blade = Cards.invent("Snc05 Wandering Blade of Kings", "{1}", "Legendary Artifact — Equipment", """
        Equipped creature gets +1/+1 for each counter on it.
        Equipped permanent isn't a planeswalker and is a creature in addition to its other types. (Loyalty abilities can still be activated.)
        Equip planeswalker {1}
        Equip {3}
    """)
    private val boots = Cards.invent("Snc05 Lead Boots", "{1}", "Artifact — Equipment", """
        Equipped creature gets +3/+3 and has "At the beginning of your end step, tap this creature."
        Equipped creature doesn't untap during its controller's untap step.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val shovel = Cards.invent("Snc05 Ribbon Shovel", "{1}{G}{W}", "Artifact — Equipment", """
        Equipped creature gets +2/+1 and has trample.
        Equip Citizen {1}
        Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val pryBar = Cards.invent("Snc05 Neighbor's Pry Bar", "{1}{W}", "Artifact — Equipment", """
        When this Equipment enters, create a 1/1 green and white Citizen creature token, then attach this Equipment to it.
        Equipped creature gets +1/+1 and has "{W}, {T}, Sacrifice Snc05 Neighbor's Pry Bar: Destroy target artifact or enchantment."
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)

    private val bear = Cards.invent("Snc05 Alley Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Snc05 Dock Ogre", "{1}{G}", "Creature — Ogre", "", "2/2")
    private val rat = Cards.invent("Snc05 Sewer Rat", "{B}", "Creature — Rat", "", "1/1")
    private val captain = Cards.invent("Snc05 Block Captain", "{1}{W}", "Creature — Human Citizen", "", "2/2")
    private val seer = Cards.invent("Snc05 Alley Broker", "{2}{U}", "Legendary Planeswalker — Broker", "[+1]: You gain 1 life.", loyalty = "3")
    private val jolt = Cards.invent("Snc05 Back Alley Jolt", "{R}", "Instant", "Snc05 Back Alley Jolt deals 5 damage to target creature.")
    private val trinket = Cards.invent("Snc05 Brass Trinket", "{1}", "Artifact", "")

    private fun treasures(player: arcanum.engine.scenario.ScenarioPlayer): Int = player.battlefield.names.count { it == "Treasure Token" }

    // CR 508.1c, 509.1a, 113.10, 602.5d, 701.21a: the creature's controller activates the granted ability at sorcery speed; the Aura's
    // controller sacrifices the Aura ("it") and draws, freeing the creature
    @Test
    fun leashOnAnOpponentsCreatureLetsItsControllerPayToBeFreed() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield(leash)
        bob.battlefield(ogre, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        attach(leash, ogre)
        alice.library("Island", "Island")
        bob.activates(ogre)
        resolveAll()
        expect {
            leash.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Battlefield
            alice.hand.size shouldBe 1
            bob.hand.size shouldBe 0
        }
    }

    // CR 701.21a: "it" is the Aura: on your own creature the Aura is sacrificed, the creature stays, and you draw
    @Test
    fun leashOnYourOwnCreatureSacrificesTheAuraAndDraws() = scenario {
        alice.battlefield(leash, ogre, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        attach(leash, ogre)
        alice.library("Island", "Island")
        alice.activates(ogre)
        resolveAll()
        expect {
            leash.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Battlefield
            alice.hand.size shouldBe 1
        }
    }

    // CR 508.1c, 509.1a, 602.5d
    @Test
    fun leashStopsAttackingBlockingAndActivatingOutsideTheMainPhase() = scenario {
        alice.battlefield(leash, ogre, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        bob.battlefield(bear)
        attach(leash, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(ogre) }
        assertFailsWith<Throwable> { alice.activates(ogre) }
    }

    @Test
    fun leashedCreatureCannotBlock() = scenario {
        alice.battlefield(bear)
        bob.battlefield(leash, ogre)
        attach(leash, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        allPass()
        assertFailsWith<Throwable> { bob.blocks(ogre to bear) }
    }

    // CR 508.1d, 506.2: with three players each creature must attack the enchanted creature's controller (Bob), not another opponent
    @Test
    fun fugitiveForcesEveryCreatureToAttackItsControllerWithThreePlayers() = scenario(players = 3) {
        alice.battlefield(bear, fugitive)
        bob.battlefield(ogre)
        carol.battlefield(rat)
        attach(fugitive, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks() }
        assertFailsWith<IllegalArgumentException> { alice.attacks(bear, defender = "carol") }
        alice.attacks(bear, defender = "bob")
    }

    // CR 508.1d: Carol's creature too, on Carol's own turn
    @Test
    fun fugitiveAlsoForcesTheOtherOpponentsCreatures() = scenario(players = 3) {
        turn(number = 3, active = carol)
        alice.battlefield(bear, fugitive)
        bob.battlefield(ogre)
        carol.battlefield(rat)
        attach(fugitive, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { carol.attacks() }
        assertFailsWith<IllegalArgumentException> { carol.attacks(rat, defender = "alice") }
        carol.attacks(rat, defender = "bob")
    }

    // CR 508.1d: a creature can't attack its own controller, so on Bob's turn nothing is required of his creature
    @Test
    fun fugitiveRequiresNothingOfItsOwnControllersCreatures() = scenario(players = 3) {
        turn(number = 2, active = bob)
        alice.battlefield(bear, fugitive)
        bob.battlefield(ogre)
        attach(fugitive, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks()
    }

    // CR 603.10a, 702.8a, 111.10a: cast with flash, +2/+1; when the creature dies, two Treasures for the Aura's controller
    @Test
    fun posterPumpsAndPaysTwoTreasuresWhenTheCreatureDies() = scenario {
        alice.battlefield(bear, "Forest", "Forest", "Forest", "Mountain")
        alice.hand(poster, jolt)
        alice.casts(poster, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.toughness shouldBe 3
        }
        alice.casts(jolt, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            poster.zone shouldBe Zone.Graveyard
        }
        assertTrue(treasures(alice) == 2, "two Treasure tokens: ${alice.battlefield.names}")
    }

    // CR 118.9, 601.3e, 303.4a: cast from the graveyard for {2}{W}, enchanting; not for its mana cost
    @Test
    fun wardIsCastFromTheGraveyardForItsAlternativeCost() = scenario {
        alice.battlefield(bear, "Plains", "Plains", "Plains")
        alice.graveyard(ward)
        alice.casts(ward, target = bear)
        resolveAll()
        expect {
            ward.zone shouldBe Zone.Battlefield
            bear.power shouldBe 3
        }
    }

    @Test
    fun wardFromTheGraveyardNeedsAllThree() = scenario {
        alice.battlefield(bear, "Plains", "Plains")
        alice.graveyard(ward)
        alice.casts(ward, target = bear)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 701.26a, 502.3, 111.2: the Aura's controller gets the Fish; the enchanted creature is tapped and stays tapped through its untap step
    @Test
    fun slumberTapsTheCreatureHoldsItAndMakesAFish() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(slumber)
        bob.battlefield(ogre)
        bob.library("Forest", "Forest")
        alice.library("Island", "Island")
        alice.casts(slumber, target = ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            alice.battlefield has "Fish Token"
        }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        expect { ogre.tapped shouldBe true }
    }

    // CR 113.10, 702.110a, 510.2, 603.2: menace and a granted combat damage trigger; the Treasure goes to the creature's controller
    @Test
    fun fingersGivesMenaceAndATreasureOnCombatDamage() = scenario {
        alice.battlefield(bear, fingers)
        attach(fingers, bear)
        expect { bear hasKeyword Keyword.Menace.kind }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        assertTrue(treasures(alice) == 1, "a Treasure: ${alice.battlefield.names}")
        expect { bob.life shouldBe 18 }
    }

    // CR 113.10, 509.1h: the equipped creature's controller pays {1} to ping a creature blocking it, only once it is blocked
    @Test
    fun spitterDamagesACreatureBlockingTheEquippedCreature() = scenario {
        alice.battlefield(ogre, spitter, "Plains", "Plains")
        bob.battlefield(rat, bear)
        attach(spitter, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ogre)
        allPass()
        bob.blocks(rat to ogre)
        alice.activates(ogre, target = rat)
        resolveAll()
        expect { rat.zone shouldBe Zone.Graveyard }
    }

    // CR 115.1, 602.2b: nothing is blocking it before blockers are declared, so there is no legal target
    @Test
    fun spitterHasNoTargetBeforeBlockers() = scenario {
        alice.battlefield(ogre, spitter, "Plains", "Plains")
        bob.battlefield(rat)
        attach(spitter, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ogre)
        assertFailsWith<IllegalArgumentException> { alice.activates(ogre) }
    }

    // CR 301.5, 603.4 ("as long as"): one Equipment is not enough, two give double strike, and it ends with one of them
    @Test
    fun knucklesNeedsTwoEquipmentAttached() = scenario {
        alice.battlefield(bear, knuckles, bangle, "Plains")
        attach(knuckles, bear)
        expect { bear lacksKeyword Keyword.DoubleStrike.kind }
        alice.activates(bangle, target = bear)
        resolveAll()
        expect { bear hasKeyword Keyword.DoubleStrike.kind }
    }

    // CR 702.6e, 301.5c, 205.1b, 306.5b, 122.1: Equip planeswalker attaches to a planeswalker you control; it stops being a planeswalker,
    // is a creature and gets +1/+1 for each counter (its loyalty counters); its loyalty ability can still be activated
    @Test
    fun bladeEquipsAPlaneswalkerWhichBecomesACreatureAndKeepsItsLoyaltyAbility() = scenario {
        alice.battlefield(blade, "Plains", "Plains")
        alice.battlefield(seer, counters = listOf(CounterKind.Loyalty to 3))
        alice.activates(blade, ability = 0, target = seer)
        resolveAll()
        expect {
            seer.types.contains(CardType.Creature) shouldBe true
            seer.types.contains(CardType.Planeswalker) shouldBe false
            seer.power shouldBe 3
            seer.toughness shouldBe 3
        }
        alice.activates(seer)
        resolveAll()
        expect {
            alice.life shouldBe 21
            seer.counters(CounterKind.Loyalty) shouldBe 4
            seer.power shouldBe 4
        }
    }

    // CR 702.6e, 702.6a: Equip planeswalker targets only planeswalkers, Equip {3} only creatures
    @Test
    fun bladesTwoEquipAbilitiesTargetTheirOwnKinds() = scenario {
        alice.battlefield(blade, bear, "Plains", "Plains", "Plains", "Plains")
        alice.battlefield(seer, counters = listOf(CounterKind.Loyalty to 3))
        assertFailsWith<IllegalArgumentException> { alice.activates(blade, ability = 0, target = bear) }
        assertFailsWith<IllegalArgumentException> { alice.activates(blade, ability = 1, target = seer) }
    }

    // CR 122.1: a creature's +1/+1 counter counts too
    @Test
    fun bladeOnACreatureCountsItsCounters() = scenario {
        alice.battlefield(blade, "Plains", "Plains", "Plains")
        alice.battlefield(bear, counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        alice.activates(blade, ability = 1, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 6
            bear.toughness shouldBe 6
        }
    }

    // CR 113.10, 603.2, 502.3: the granted trigger taps the creature at its controller's end step, and it doesn't untap
    @Test
    fun bootsTapTheCreatureAtTheEndStepAndKeepItTapped() = scenario {
        alice.battlefield(boots, bear)
        attach(boots, bear)
        alice.library("Island", "Island")
        bob.library("Forest", "Forest")
        expect { bear.power shouldBe 5 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { bear.tapped shouldBe true }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        expect { bear.tapped shouldBe true }
    }

    // CR 702.6c: Equip Citizen restricts only that ability's targets; Equip {3} still takes any creature
    @Test
    fun shovelsCheapEquipTakesOnlyCitizens() = scenario {
        alice.battlefield(shovel, bear, captain, "Plains", "Plains", "Plains", "Plains")
        alice.activates(shovel, ability = 0, target = captain)
        resolveAll()
        expect { captain.power shouldBe 4 }
        expect { bear.power shouldBe 2 }
        alice.activates(shovel, ability = 1, target = bear)
        resolveAll()
        expect { bear.power shouldBe 4 }
    }

    // CR 702.6c: with no Citizen, the cheap equip has no legal target
    @Test
    fun shovelsCheapEquipNeedsACitizen() = scenario {
        alice.battlefield(shovel, bear, "Plains")
        assertFailsWith<IllegalArgumentException> { alice.activates(shovel, ability = 0) }
    }

    // CR 701.3a, 111.2: the Citizen token enters and the Equipment is attached to it
    @Test
    fun pryBarMakesACitizenAndAttachesToIt() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(pryBar)
        alice.casts(pryBar)
        resolveAll()
        expect {
            alice.battlefield has "Citizen Token"
            "Citizen Token".power shouldBe 2
        }
    }

    // CR 301.5c, 113.10: the granted ability sacrifices the Equipment that grants it
    @Test
    fun pryBarGrantsDestroyByTappingAndSacrificingIt() = scenario {
        alice.battlefield(pryBar, bear, "Plains")
        bob.battlefield(trinket)
        attach(pryBar, bear)
        alice.activates(bear, target = trinket)
        resolveAll()
        expect {
            trinket.zone shouldBe Zone.Graveyard
            pryBar.zone shouldBe Zone.Graveyard
        }
    }
}
