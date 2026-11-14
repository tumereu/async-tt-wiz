package arcanum.engine.sets.bro

import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.resolveAll
import kotlin.test.Test

/**
 * The Brothers' War chunk 13 in play: a fight mode and a pump-then-fight spell, additional sacrifice costs, an uncounterable spell
 * with a mana value check, and a continuous "players can't gain life".
 */
class BroChunk13FightProwessSurveilScenariosTest {
    private val trampling = Cards.invent("Wild Trampling", "{G}", "Sorcery", """
        Choose one —
        • Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
        • Target creature you control fights target creature you don't control. (Each deals damage equal to its power to the other.)
    """)
    private val press = Cards.invent("Hard Press", "{1}{G}", "Sorcery",
        "Target creature you control gets +1/+2 until end of turn. It fights target creature you don't control. (Each deals damage equal to its power to the other.)")
    private val bargain = Cards.invent("Gloomhold Bargain", "{1}{B}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice a creature.
        Search your library for a card, put that card into your hand, then shuffle.
    """)
    private val fracture = Cards.invent("Sacrificial Fracture", "{1}{B}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice an artifact or creature.
        Destroy target creature or planeswalker.
    """)
    private val raze = Cards.invent("Raze Down", "{2}{R}", "Sorcery", """
        This spell can't be countered.
        Destroy target artifact. If its mana value was 1 or less, draw a card.
    """)
    private val denial = Cards.invent("Plain Denial", "{U}{U}", "Instant", "Counter target spell.")
    private val cindermaw = Cards.invent("Ember Cindermaw", "{2}{R}", "Creature — Dinosaur Beast", """
        Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)
        Players can't gain life.
    """, "4/3")
    private val healer = Cards.invent("Plain Healer", "{1}{W}", "Instant", "You gain 3 life.")
    private val bear = Cards.invent("Moor Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Moor Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val hare = Cards.invent("Plain Hare", "{G}", "Creature — Rabbit", "", "1/1")
    private val trinket = Cards.invent("Moor Trinket", "{1}", "Artifact", "")
    private val gizmo = Cards.invent("Plain Gizmo", "{3}", "Artifact", "")

    // CR 700.2, 701.14a: the fight mode; bear (2) kills the hare, the hare (1) does not kill the bear
    @Test
    fun tramplingFightMode() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(trampling)
        bob.battlefield(hare)
        alice.casts(trampling, modes = listOf(1), targets = listOf(bear, hare))
        resolveAll()
        expect {
            bob.graveyard has hare
            alice.battlefield has bear
        }
    }

    // CR 700.2, 701.23: the other mode fetches a basic land
    @Test
    fun tramplingSearchMode() = scenario {
        alice.battlefield("Forest")
        alice.hand(trampling)
        alice.library("Island", "Swamp")
        alice.casts(trampling, modes = listOf(0))
        resolveAll()
        alice.chooses("Island")
        resolveAll()
        expect { alice.hand has "Island" }
    }

    // CR 611.2a, 701.14a: +1/+2 applies first, so the 2/2 bear fights as a 3/4 and kills a 3/3 ox without dying
    @Test
    fun pressPumpsBeforeTheFight() = scenario {
        alice.battlefield("Forest", "Forest", bear)
        alice.hand(press)
        bob.battlefield(ox)
        alice.casts(press, targets = listOf(bear, ox))
        resolveAll()
        expect {
            bob.graveyard has ox
            alice.battlefield has bear
        }
    }

    // CR 118.8, 701.21a: the creature is sacrificed as the spell is cast, then the search happens
    @Test
    fun bargainSacrificesAndSearches() = scenario {
        alice.battlefield("Swamp", "Swamp", hare)
        alice.hand(bargain)
        alice.library(ox)
        alice.casts(bargain, choosing = listOf(hare))
        resolveAll()
        alice.chooses(ox)
        resolveAll()
        expect {
            alice.graveyard has hare
            alice.hand has ox
        }
    }

    // CR 118.8: an artifact pays the cost as well as a creature
    @Test
    fun fractureSacrificesAnArtifact() = scenario {
        alice.battlefield("Swamp", "Swamp", trinket)
        alice.hand(fracture)
        bob.battlefield(ox)
        alice.casts(fracture, target = ox, choosing = listOf(trinket))
        resolveAll()
        expect {
            alice.graveyard has trinket
            bob.graveyard has ox
        }
    }

    // CR 101.2: "can't be countered" beats the counterspell; CR 608.2h: a mana value 1 artifact draws
    @Test
    fun razeCannotBeCounteredAndDrawsForCheapArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(raze)
        alice.library(ox)
        bob.battlefield("Island", "Island", trinket)
        bob.hand(denial)
        alice.casts(raze, target = trinket)
        alice.passes()
        bob.casts(denial, target = raze)
        resolveAll()
        expect {
            bob.graveyard has trinket
            alice.hand has ox
        }
    }

    // CR 608.2h: a mana value 3 artifact does not draw
    @Test
    fun razeDoesNotDrawForExpensiveArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(raze)
        alice.library(ox)
        bob.battlefield(gizmo)
        alice.casts(raze, target = gizmo)
        resolveAll()
        expect {
            bob.graveyard has gizmo
            alice.hand.size shouldBe 0
        }
    }

    // CR 119.7, 101.2: no player gains life while it is out, in a 3-player game too
    @Test
    fun cindermawStopsEveryPlayerGainingLife() = scenario(players = 3) {
        alice.battlefield(cindermaw)
        players[2].battlefield("Plains", "Plains")
        players[2].hand(healer)
        turn(active = players[2])
        players[2].casts(healer)
        resolveAll()
        expect { players[2].life shouldBe 20 }
    }

    // CR 119.7: with it gone, life is gained again
    @Test
    fun lifeIsGainedWithoutCindermaw() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(healer)
        alice.casts(healer)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }
}
