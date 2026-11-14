package arcanum.engine.sets.dmr

import arcanum.api.CardType
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.blb.targetsIfAsked
import arcanum.engine.sets.ltr.abilityIndex
import arcanum.engine.sets.ltr.aliceUpkeep
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * Dominaria Remastered chunk 01 in play: cycling (CR 702.29) with its triggers (CR 702.29c, from the graveyard), cycling for life,
 * typecycling, madness (CR 702.35), cycling lands, and the odd cards: a creature returned only in its owner's upkeep, an enchantment that
 * becomes a creature when an opponent casts a spell and then can't attack a player without an Island, and a creature that goes to the
 * player with the most life, both with three players.
 */
class DmrChunk01CyclingScenariosTest {
    private val bear = Cards.invent("Dmr Test Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val brute = Cards.invent("Dmr Test Brute", "{2}{G}", "Creature — Ogre", "", "3/3")
    private val imp = Cards.invent("Dmr Test Imp", "{B}", "Creature — Imp", "", "1/1")
    private val goblin = Cards.invent("Dmr Test Goblin", "{R}", "Creature — Goblin", "", "1/1")
    private val fodder = Cards.invent("Dmr Test Fodder", "{1}", "Sorcery", "You gain 1 life.")
    private val peek = Cards.invent("Dmr Test Tonic", "{U}", "Instant", "You gain 1 life.")

    private val vow = Cards.invent("Mending Vow", "{2}{W}", "Instant", """
        You gain 6 life.
        Cycling {1}{W} ({1}{W}, Discard this card: Draw a card.)
        When you cycle this card, you may gain 2 life.
    """)
    private val wraith = Cards.invent("Marsh Wraith", "{3}{B}{B}", "Creature — Wraith", """
        Swampwalk (This creature can't be blocked as long as defending player controls a Swamp.)
        Cycling—Pay 2 life. (Pay 2 life, Discard this card: Draw a card.)
    """, "3/4")
    private val barbarian = Cards.invent("Grave Barbarian", "{1}{B}{B}", "Creature — Zombie Barbarian", """
        {1}{B}, Discard a card: Return this card from your graveyard to your hand. Activate only during your upkeep.
        Cycling {1}{B} ({1}{B}, Discard this card: Draw a card.)
    """, "3/1")
    private val serpent = Cards.invent("Masked Serpent", "{2}{U}", "Enchantment", """
        When an opponent casts a spell, if this permanent is an enchantment, it becomes a 4/4 Serpent creature with "This creature can't attack unless defending player controls an Island."
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """)
    private val hounds = Cards.invent("Feral Hounds", "{G}", "Creature — Dog", """
        At the beginning of your upkeep, if a player has more life than each other player, the player with the most life gains control of this creature.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """, "2/1")
    private val wisps = Cards.invent("Mist Wisps", "{1}{U}", "Creature — Faerie", """
        Flying
        When this creature enters, untap up to two lands.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """, "1/1")
    private val meadow = Cards.invent("Sunlit Meadow", null, "Land", """
        This land enters tapped.
        {T}: Add {W}.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """)
    private val monstrosity = Cards.invent("Elvish Monstrosity", "{5}{G}", "Creature — Elf Mutant", """
        {T}: Add {G}{G}{G}.
        Forestcycling {2} ({2}, Discard this card: Search your library for a Forest card, reveal it, put it into your hand, then shuffle.)
    """, "4/5")
    private val palmGoblin = Cards.invent("Cinder Palm Goblin", "{2}{R}", "Creature — Goblin", """
        Cycling {1}{R} ({1}{R}, Discard this card: Draw a card.)
        When you cycle this card, you may have it deal X damage to target creature, where X is the number of Goblins on the battlefield.
    """, "2/1")
    private val slick = Cards.invent("Bitter Slick", "{2}{B}", "Sorcery", """
        Target creature gets -3/-3 until end of turn.
        Cycling {2} ({2}, Discard this card: Draw a card.)
        Madness {3}{B} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
    """)
    private val guard = Cards.invent("Plated Guard", "{3}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+5.
        Cycling {3} ({3}, Discard this card: Draw a card.)
    """)
    private val calm = Cards.invent("Muffled Calm", "{1}{G}", "Instant", """
        Prevent all combat damage that would be dealt this turn.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """)
    private val swell = Cards.invent("Primal Swell", "{2}{G}", "Instant", """
        Target creature gets +4/+4 until end of turn.
        Cycling {2}{G} ({2}{G}, Discard this card: Draw a card.)
        When you cycle this card, you may have target creature get +1/+1 until end of turn.
    """)
    private val carve = Cards.invent("Cleave and Carve", "{4}{R}{R}", "Sorcery", """
        Cleave and Carve deals 4 damage to each creature.
        Cycling {2}{R} ({2}{R}, Discard this card: Draw a card.)
        When you cycle this card, you may have it deal 1 damage to each creature.
    """)
    private val beam = Cards.invent("Scorching Beam", "{3}{R}", "Instant", """
        Scorching Beam deals 3 damage to any target.
        Cycling {1}{R}{R} ({1}{R}{R}, Discard this card: Draw a card.)
        When you cycle this card, you may have it deal 1 damage to any target.
    """)

    /** Resolves what is on the Stack; a "you may" it reaches is answered [yes]. */
    private fun Scenario.finish(yes: Boolean = true) {
        var guard = 30
        while (guard-- > 0) {
            if (pending is Decision.May) may(yes)
            if (pending != null || state.stack.isEmpty()) break
            allPass()
        }
    }

    // CR 702.29a, 702.29c, 608.2d: cycled for {1}{W}, the trigger from the graveyard is a choice
    @Test
    fun mendingVowCyclesAndMayGainTwo() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(vow)
        alice.library("Island")
        alice.activates(vow)
        finish()
        expect {
            alice.life shouldBe 22
            alice.hand has "Island"
            alice.graveyard has vow
        }
    }

    @Test
    fun mendingVowDeclinedGainsNothing() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(vow)
        alice.library("Island")
        alice.activates(vow)
        finish(yes = false)
        expect { alice.life shouldBe 20 }
    }

    // CR 702.29a: a cycling cost that is only life
    @Test
    fun marshWraithCyclesForLife() = scenario {
        alice.hand(wraith)
        alice.library("Island")
        alice.activates(wraith)
        finish()
        expect {
            alice.life shouldBe 18
            alice.hand has "Island"
            alice.graveyard has wraith
        }
    }

    // CR 602.5b: only during its owner's upkeep, from the graveyard, discarding a card as the cost
    @Test
    fun graveBarbarianReturnsInYourUpkeepOnly() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(barbarian)
        alice.hand(fodder)
        aliceUpkeep()
        resolveAll()
        val returning = abilityIndex(barbarian, "Return this card", Zone.Graveyard)
        alice.activates(barbarian, ability = returning, choosing = listOf(fodder))
        finish()
        expect {
            alice.hand has barbarian
            alice.graveyard has fodder
        }
    }

    @Test
    fun graveBarbarianNotInMainPhase() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(barbarian)
        alice.hand(fodder)
        val returning = abilityIndex(barbarian, "Return this card", Zone.Graveyard)
        assertFails { alice.activates(barbarian, ability = returning, choosing = listOf(fodder)) }
        expect { alice.graveyard has barbarian }
    }

    @Test
    fun graveBarbarianNotInAnOpponentsUpkeep() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(barbarian)
        alice.hand(fodder)
        turn(number = 2, active = bob, step = TurnStep.Upkeep, priority = alice)
        val returning = abilityIndex(barbarian, "Return this card", Zone.Graveyard)
        assertFails { alice.activates(barbarian, ability = returning, choosing = listOf(fodder)) }
        expect { alice.graveyard has barbarian }
    }

    // CR 205.1a, 603.4, 508.1c, 506.2: with three players; the first spell turns it into a creature, which is no longer an enchantment
    @Test
    fun maskedSerpentBecomesACreatureOnceAndNeedsAnIsland() = scenario(players = 3) {
        alice.battlefield(serpent)
        bob.battlefield("Island", bear, "Island")
        carol.battlefield(bear)
        bob.hand(peek, peek)
        turn(number = 1, active = bob)
        bob.casts(peek)
        resolveAll()
        expect {
            serpent.types shouldBe listOf(CardType.Creature)
            serpent.subtypes shouldBe listOf(Subtype("Serpent"))
            serpent.power shouldBe 4
            serpent.toughness shouldBe 4
        }
        // A second spell doesn't trigger it again (the permanent isn't an enchantment now, CR 603.4)
        bob.casts(peek)
        assertEquals(1, state.stack.size)
        resolveAll()
    }

    // CR 508.1c, 506.2: the granted restriction itself: it may attack a player who controls an Island, not one who doesn't
    private val awakeSerpent = Cards.invent("Masked Serpent Awake", "{2}{U}", "Creature — Serpent", """
        This creature can't attack unless defending player controls an Island.
    """, "4/4")

    @Test
    fun cantAttackAPlayerWithoutAnIsland() = scenario(players = 3) {
        alice.battlefield(awakeSerpent)
        bob.battlefield("Island")
        carol.battlefield("Forest")
        advanceTo(TurnStep.DeclareAttackers)
        assertFails { alice.attacks(awakeSerpent, defender = "carol") }
        alice.attacks(awakeSerpent, defender = "bob")
    }

    // CR 119.1: a unique greatest life total, with three players
    @Test
    fun feralHoundsGoToTheOnlyPlayerWithTheMostLife() = scenario(players = 3) {
        alice.battlefield(hounds)
        bob.life = 25
        carol.life = 21
        aliceUpkeep()
        resolveAll()
        expect { hounds.controller shouldBe bob.id }
    }

    @Test
    fun feralHoundsStayWhenTheMostLifeIsShared() = scenario(players = 3) {
        alice.battlefield(hounds)
        bob.life = 25
        carol.life = 25
        aliceUpkeep()
        resolveAll()
        expect { hounds.controller shouldBe alice.id }
    }

    @Test
    fun feralHoundsStayWithTheLeaderThemselves() = scenario(players = 3) {
        alice.battlefield(hounds)
        alice.life = 30
        aliceUpkeep()
        resolveAll()
        expect { hounds.controller shouldBe alice.id }
    }

    // CR 701.26b, 608.2d: "up to two lands" are chosen on resolution, not targeted
    @Test
    fun mistWispsUntapTwoLands() = scenario {
        alice.battlefield("Island", "Plains")
        alice.battlefield("Forest", "Forest", "Forest", tapped = true)
        alice.hand(wisps)
        alice.casts(wisps)
        resolveAll()
        alice.chooses("Forest", "Forest")
        resolveAll()
        expect {
            card("Forest", 0).tapped shouldBe false
            card("Forest", 1).tapped shouldBe false
            card("Forest", 2).tapped shouldBe true
            card("Plains").tapped shouldBe true
        }
    }

    // CR 614.1c
    @Test
    fun cyclingLandEntersTapped() = scenario {
        alice.hand(meadow)
        alice.plays(meadow)
        expect { meadow.tapped shouldBe true }
    }

    // CR 702.29a: a land cycled from the hand
    @Test
    fun cyclingLandCycles() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(meadow)
        alice.library("Forest")
        alice.activates(meadow, ability = 1)
        finish()
        expect {
            alice.hand has "Forest"
            alice.graveyard has meadow
        }
    }

    // CR 702.29e, 605.1a
    @Test
    fun elvishMonstrosityMakesThreeManaAndForestcycles() = scenario {
        alice.battlefield(monstrosity)
        alice.activates(monstrosity)
        expect { alice.pool.size shouldBe 3 }
    }

    @Test
    fun elvishMonstrosityForestcycles() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(monstrosity)
        alice.library("Island", "Forest", "Island")
        alice.activates(monstrosity, ability = 1)
        resolveAll()
        alice.chooses("Forest")
        resolveAll()
        expect {
            alice.hand has "Forest"
            alice.graveyard has monstrosity
        }
    }

    // CR 702.29c: X counts Goblins on the battlefield only: the cycled card and graveyard Goblins aren't counted
    @Test
    fun cinderPalmGoblinCountsOnlyTheBattlefield() = scenario {
        alice.battlefield("Mountain", "Mountain", goblin)
        alice.graveyard(goblin, goblin)
        alice.hand(palmGoblin)
        alice.library("Island")
        bob.battlefield(bear)
        alice.activates(palmGoblin)
        resolveAll()
        targets(bear)
        finish()
        // one Goblin on the battlefield: 1 damage to the 2/2
        expect { bear.zone shouldBe Zone.Battlefield }
    }

    // CR 702.35a, 702.29d: cycling discards it, so madness exiles it and it is cast for {3}{B}
    @Test
    fun bitterSlickCycledIsCastForMadness() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(slick)
        alice.library("Island")
        bob.battlefield(bear)
        alice.activates(slick)
        playOut(bear)
        expect {
            bear.zone shouldBe Zone.Graveyard
            alice.hand has "Island"
        }
    }

    // CR 303.4, 702.5a
    @Test
    fun platedGuardEnchantsACreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(guard)
        alice.casts(guard, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.toughness shouldBe 7
        }
    }

    // CR 615.1, 510.2: all combat damage this turn is prevented, to creatures and players
    @Test
    fun muffledCalmPreventsAllCombatDamage() = scenario {
        alice.battlefield(brute, bear, "Forest", "Forest")
        alice.hand(calm)
        bob.battlefield(imp)
        alice.casts(calm)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(brute, bear)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(imp to brute)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            imp.zone shouldBe Zone.Battlefield
            bob.life shouldBe 20
        }
    }

    // CR 702.29c: the +1/+1 from the cycle trigger on top of nothing else
    @Test
    fun primalSwellCycleTriggerPumps() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.hand(swell)
        alice.library("Island")
        alice.activates(swell)
        resolveAll()
        targetsIfAsked(bear)
        finish()
        expect {
            bear.power shouldBe 3
            bear.toughness shouldBe 3
        }
    }

    // CR 120.3: the cycled sorcery deals 1 damage to each creature, its controller's too
    @Test
    fun cleaveAndCarveCycleDamagesEachCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", imp, bear)
        alice.hand(carve)
        alice.library("Island")
        bob.battlefield(imp)
        alice.activates(carve)
        finish()
        expect {
            alice.graveyard has imp
            bob.graveyard has imp
            bear.zone shouldBe Zone.Battlefield
        }
    }

    @Test
    fun scorchingBeamCycleTriggerPingsAnyTarget() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(beam)
        alice.library("Island")
        alice.activates(beam)
        resolveAll()
        targets("bob")
        finish()
        expect { bob.life shouldBe 19 }
    }
}
