package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.setup.StartOptions
import arcanum.engine.setup.cards
import arcanum.engine.setup.inOrder
import arcanum.engine.setup.keep
import arcanum.engine.setup.startGame
import arcanum.engine.setup.table
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The FDN Equipment, played: the invented names carry the oracle text of Adventuring Gear, Fishing Pole, Basilisk Collar, Celestial Armor,
 * Fireshrieker, Goldvein Pick, Leyline Axe, Pirate's Cutlass, Quick-Draw Katana and Swiftfoot Boots.
 */
class FdnChunk03EquipmentScenariosTest {
    private val satchel = Cards.invent("Trailworn Satchel", "{1}", "Artifact — Equipment", """
        Landfall — Whenever a land you control enters, equipped creature gets +2/+2 until end of turn.
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val reel = Cards.invent("Angler's Reel", "{1}", "Artifact — Equipment", """
        Equipped creature has "{1}, {T}, Tap Angler's Reel: Put a bait counter on Angler's Reel."
        Whenever equipped creature becomes untapped, remove a bait counter from this Equipment. If you do, create a 1/1 blue Fish creature token.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val choker = Cards.invent("Cockatrice Choker", "{1}", "Artifact — Equipment", """
        Equipped creature has deathtouch and lifelink. (Any amount of damage it deals to a creature is enough to destroy it. Damage dealt by this creature also causes you to gain that much life.)
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val cuirass = Cards.invent("Starlit Cuirass", "{2}{W}", "Artifact — Equipment", """
        Flash (You may cast this spell any time you could cast an instant.)
        When this Equipment enters, attach it to target creature you control. That creature gains hexproof and indestructible until end of turn.
        Equipped creature gets +2/+0 and has flying.
        Equip {3}{W} ({3}{W}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val screamblade = Cards.invent("Screamblade", "{3}", "Artifact — Equipment", """
        Equipped creature has double strike. (It deals both first-strike and regular combat damage.)
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val mattock = Cards.invent("Oreseam Mattock", "{2}", "Artifact — Equipment", """
        Equipped creature gets +1/+1.
        Whenever equipped creature deals combat damage to a player, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val hatchet = Cards.invent("Groundwell Hatchet", "{4}", "Artifact — Equipment", """
        If this card is in your opening hand, you may begin the game with it on the battlefield.
        Equipped creature gets +1/+1 and has double strike and trample.
        Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val hanger = Cards.invent("Buccaneer's Hanger", "{3}", "Artifact — Equipment", """
        When this Equipment enters, attach it to target Pirate you control.
        Equipped creature gets +2/+1.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val wakizashi = Cards.invent("Swiftdrawn Wakizashi", "{2}", "Artifact — Equipment", """
        During your turn, equipped creature gets +2/+0 and has first strike. (It deals combat damage before creatures without first strike.)
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val sandals = Cards.invent("Fleetstep Sandals", "{2}", "Artifact — Equipment", """
        Equipped creature has hexproof and haste. (It can't be the target of spells or abilities your opponents control. It can attack and {T} no matter when it came under your control.)
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val stevedore = Cards.invent("Tarry Stevedore", "{1}{G}", "Creature — Human", "", "2/2")
    private val pirate = Cards.invent("Plank Pirate", "{U}", "Creature — Human Pirate", "", "1/1")
    private val ettin = Cards.invent("Tall Ettin", "{3}{G}", "Creature — Giant", "", "4/4")
    private val ogre = Cards.invent("Wharf Ogre", "{1}{G}", "Creature — Ogre", "", "1/1")
    private val zap = Cards.invent("Spiteful Zap", "{R}", "Instant", "Spiteful Zap deals 2 damage to target creature.")
    private val bait = CounterKind.Named("bait")

    // CR 207.2c, 301.5: a land entering pumps the creature the Equipment is attached to, until end of turn
    @Test
    fun satchelPumpsTheEquippedCreatureOnLandfall() = scenario {
        alice.battlefield(satchel, stevedore)
        attach(satchel, stevedore)
        alice.hand("Forest")
        alice.plays("Forest")
        allPass()
        expect {
            stevedore.power shouldBe 4
            stevedore.toughness shouldBe 4
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { stevedore.power shouldBe 2 }
    }

    // CR 608.2b: not attached to anything, the ability has no creature to change
    @Test
    fun satchelDoesNothingUnattached() = scenario {
        alice.battlefield(satchel, stevedore)
        alice.hand("Forest")
        alice.plays("Forest")
        allPass()
        expect { stevedore.power shouldBe 2 }
    }

    // CR 113.10, 602.2b: the creature has the ability; its {T} taps the creature, "Tap [the Equipment]" taps the Equipment,
    // and the counter goes on the Equipment
    @Test
    fun reelPutsABaitCounterOnItself() = scenario {
        alice.battlefield(reel, stevedore, "Plains")
        attach(reel, stevedore)
        alice.activates(stevedore)
        expect {
            stevedore.tapped shouldBe true
            reel.tapped shouldBe true
        }
        allPass()
        expect {
            reel.counters(bait) shouldBe 1
            stevedore.counters(bait) shouldBe 0
        }
    }

    // CR 602.2b, 701.26a: an already tapped Equipment can't be tapped for the cost
    @Test
    fun reelMustBeUntappedToPay() = scenario {
        alice.battlefield(reel, tapped = true)
        alice.battlefield(stevedore, "Plains")
        attach(reel, stevedore)
        assertFailsWith<Throwable> { alice.activates(stevedore) }
    }

    // CR 302.6: the creature's own {T} needs it to have been yours since the turn began
    @Test
    fun reelNeedsAnUnsickCreature() = scenario {
        alice.battlefield(reel, "Plains")
        alice.battlefield(stevedore, sick = true)
        attach(reel, stevedore)
        assertFailsWith<Throwable> { alice.activates(stevedore) }
    }

    // CR 502.3, 701.26b, 603.2, 118.12: the untap step untaps the creature, the trigger takes the counter off, and only then a Fish
    @Test
    fun reelMakesAFishWhenTheCreatureUntaps() = scenario {
        bob.library("Island")
        alice.battlefield(reel, stevedore, "Plains")
        attach(reel, stevedore)
        alice.activates(stevedore)
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 3)
        allPass()
        expect {
            stevedore.tapped shouldBe false
            reel.tapped shouldBe false
            reel.counters(bait) shouldBe 0
        }
        expect { alice.battlefield has "Fish Token" }
    }

    // CR 118.12: no counter to remove, no Fish
    @Test
    fun reelMakesNoFishWithoutACounter() = scenario {
        bob.library("Island")
        alice.battlefield(reel)
        alice.battlefield(stevedore, tapped = true)
        attach(reel, stevedore)
        advanceTo(TurnStep.Upkeep, turn = 3)
        allPass()
        expect { stevedore.tapped shouldBe false }
        expect { alice.battlefield lacks "Fish Token" }
    }

    // CR 702.2c, 704.5h: any damage destroys; CR 702.15b: the controller gains that much life
    @Test
    fun chokerKillsTheBlockerAndGainsLife() = scenario {
        alice.battlefield(choker, stevedore)
        attach(choker, stevedore)
        bob.battlefield(ettin)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(stevedore)
        allPass()
        bob.blocks(ettin to stevedore)
        allPass()
        allPass()
        expect {
            ettin.zone shouldBe Zone.Graveyard
            stevedore.zone shouldBe Zone.Graveyard
            alice.life shouldBe 22
        }
    }

    // CR 702.8a: cast on the opponent's turn; CR 701.3a: attached by the trigger; "you control": the opponent's creature is no target, so
    // the lone legal target is taken; the gains end with the turn, the static bonus stays
    @Test
    fun cuirassFlashesInAndProtects() = scenario {
        turn(number = 1, active = bob, step = TurnStep.Upkeep)
        alice.battlefield("Plains", "Plains", "Plains", stevedore)
        alice.hand(cuirass)
        bob.battlefield(ettin)
        bob.passes()
        alice.casts(cuirass)
        allPass()
        allPass()
        expect {
            stevedore.power shouldBe 4
            stevedore.toughness shouldBe 2
            stevedore hasKeyword Keyword.Flying.kind
            stevedore hasKeyword Keyword.Hexproof.kind
            stevedore hasKeyword Keyword.Indestructible.kind
            ettin.power shouldBe 4
            ettin lacksKeyword Keyword.Flying.kind
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            stevedore.power shouldBe 4
            stevedore hasKeyword Keyword.Flying.kind
            stevedore lacksKeyword Keyword.Hexproof.kind
            stevedore lacksKeyword Keyword.Indestructible.kind
        }
    }

    // CR 702.4b: it deals first-strike and regular combat damage
    @Test
    fun screambladeHitsTwice() = scenario {
        alice.battlefield(screamblade, stevedore)
        attach(screamblade, stevedore)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(stevedore)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 16 }
    }

    // CR 510.3a: dealing combat damage to the player makes the Treasure
    @Test
    fun mattockMakesATreasureOnAHit() = scenario {
        alice.battlefield(mattock, stevedore, "Plains")
        alice.activates(mattock, target = stevedore)
        allPass()
        expect {
            stevedore.power shouldBe 3
            stevedore.toughness shouldBe 3
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(stevedore)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 17 }
        expect { alice.battlefield has "Treasure Token" }
    }

    // "to a player": damage to the blocker makes none
    @Test
    fun mattockMakesNoTreasureWhenBlocked() = scenario {
        alice.battlefield(mattock, stevedore)
        attach(mattock, stevedore)
        bob.battlefield(ettin)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(stevedore)
        allPass()
        bob.blocks(ettin to stevedore)
        allPass()
        allPass()
        expect { bob.life shouldBe 20 }
        expect { alice.battlefield lacks "Treasure Token" }
    }

    // CR 103.6a: after mulligans, the player may begin with it on the battlefield
    @Test
    fun hatchetMayBeginTheGameOnTheBattlefield() = scenario(start = table(cards(listOf(hatchet), "Forest", "Elvish Seer"), cards(emptyList(), "Mountain", "Elvish Seer"))) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        keep(bob)
        val offer = decision<Decision.ChooseObjects>()
        assertEquals(alice.id, offer.player)
        answer(Answer.Objects(offer.options))
        expect { card(hatchet).zone shouldBe Zone.Battlefield }
    }

    // CR 702.4b, 702.19b, 702.19e: first-strike damage kills the 1/1 blocker with 1, 2 go through; with no blocker left, all 3 regular go through
    @Test
    fun hatchetTramplesTwiceOverABlocker() = scenario {
        alice.battlefield(hatchet, stevedore)
        attach(hatchet, stevedore)
        bob.battlefield(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(stevedore)
        allPass()
        bob.blocks(ogre to stevedore)
        allPass()
        divideAsSuggested()
        allPass()
        allPass()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            bob.life shouldBe 15
        }
    }

    // CR 603.3d, 701.3a: the Pirate is the lone legal target: the opponent's Pirate and the non-Pirate aren't
    @Test
    fun hangerAttachesToYourPirate() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", stevedore, pirate)
        alice.hand(hanger)
        alice.casts(hanger)
        allPass()
        allPass()
        expect {
            pirate.power shouldBe 3
            pirate.toughness shouldBe 2
            stevedore.power shouldBe 2
        }
    }

    // CR 603.3d: no legal target, the ability leaves the Stack; equip takes any creature you control
    @Test
    fun hangerStaysUnattachedWithoutAPirateAndEquipsAnyCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", stevedore)
        alice.hand(hanger)
        alice.casts(hanger)
        allPass()
        expect {
            stack.isEmpty()
            stevedore.power shouldBe 2
        }
        alice.activates(hanger, target = stevedore)
        allPass()
        expect {
            stevedore.power shouldBe 4
            stevedore.toughness shouldBe 3
        }
    }

    // CR 611.3a: applies only during the controller's turn
    @Test
    fun wakizashiWorksOnlyOnYourTurn() = scenario {
        alice.battlefield(wakizashi, stevedore)
        attach(wakizashi, stevedore)
        expect {
            stevedore.power shouldBe 4
            stevedore hasKeyword Keyword.FirstStrike.kind
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            stevedore.power shouldBe 2
            stevedore lacksKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 702.7b: the 4-power first striker kills the blocker before it deals damage
    @Test
    fun wakizashiFirstStrikeWinsCombat() = scenario {
        alice.battlefield(wakizashi, stevedore)
        attach(wakizashi, stevedore)
        bob.battlefield(ettin)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(stevedore)
        allPass()
        bob.blocks(ettin to stevedore)
        allPass()
        allPass()
        allPass()
        expect {
            ettin.zone shouldBe Zone.Graveyard
            stevedore.zone shouldBe Zone.Battlefield
        }
    }

    // CR 702.10b: a sick creature can attack; CR 702.11b: the opponent can't target it
    @Test
    fun sandalsGiveHasteAndHexproof() = scenario {
        alice.battlefield(sandals)
        alice.battlefield(stevedore, sick = true)
        attach(sandals, stevedore)
        bob.battlefield("Mountain")
        bob.hand(zap)
        alice.passes()
        assertFailsWith<Throwable> { bob.casts(zap, target = stevedore) }
        bob.passes()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(stevedore)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 18 }
    }
}
