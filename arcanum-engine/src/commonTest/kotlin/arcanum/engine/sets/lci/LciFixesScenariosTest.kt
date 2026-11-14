package arcanum.engine.sets.lci

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** LCI fixes played: Unstable Glyphbridge's choice for each player and Sandswirl Wanderglyph's attack and cast restrictions, with three players;
 * X read from a sacrificed or died creature as it last existed (Disciple of Bolas, Dinosaur Egg, Fruit of the First Tree).
 * Invented names carry the oracle text. */
class LciFixesScenariosTest {
    private val glyphgate = Cards.invent("Glyphgate Warden", "{3}{W}{W}", "Artifact",
        "When this artifact enters, if you cast it, for each player, choose a creature with power 2 or less that player controls. " +
            "Then destroy all creatures except creatures chosen this way.")
    private val glyphwalker = Cards.invent("Hollowstone Glyphwalker", "{3}{W}{W}", "Artifact Creature — Golem", """
        Flying
        Whenever an opponent casts a spell during their turn, they can't attack you or planeswalkers you control this turn.
        Each opponent who attacked you or a planeswalker you control this turn can't cast spells.
    """, "5/3")
    private val sprite = Cards.invent("Pebble Sprite", "{W}", "Creature — Faerie", "", "1/1")
    private val duskSprite = Cards.invent("Dusk Pebble Sprite", "{W}", "Creature — Faerie", "", "2/2")
    private val courser = Cards.invent("Ash Courser", "{2}{R}", "Creature — Horse", "", "4/4")
    private val squire = Cards.invent("Cinder Squire", "{R}", "Creature — Human", "", "2/1")
    private val jolt = Cards.invent("Quickflame Jolt", "{R}", "Instant", "Quickflame Jolt deals 1 damage to any target.")

    // CR 608.2c, 101.4: you choose one creature with power 2 or less for each player (none for a player who has none); every other creature
    // is destroyed at once (CR 608.2e, 701.8a)
    @Test
    fun glyphgateKeepsOneSmallCreatureOfEachPlayer() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", sprite, duskSprite, courser)
        alice.hand(glyphgate)
        bob.battlefield(squire, "Ash Courser")
        carol.battlefield("Ash Courser")
        alice.casts(glyphgate)
        resolveAll()
        alice.chooses(duskSprite)
        resolveAll()
        expect {
            alice.battlefield has duskSprite
            alice.graveyard has sprite
            alice.graveyard has courser
            bob.battlefield has squire
            bob.graveyard has courser
            carol.graveyard has courser
        }
    }

    // CR 603.2, 508.1c: an opponent's spell during their own turn: their creatures can't attack you this turn, but may attack another player
    @Test
    fun glyphwalkerStopsTheCastersAttacksOnYou() = scenario(players = 3) {
        alice.battlefield(glyphwalker)
        bob.battlefield(courser, "Mountain")
        bob.hand(jolt)
        turn(number = 2, active = bob)
        bob.casts(jolt, target = "carol")
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { bob.attacks(courser, defender = "alice") }
        bob.attacks(courser, defender = "carol")
        expect { courser.tapped shouldBe true }
    }

    // CR 603.2: "during their turn": a spell the opponent casts during your turn doesn't trigger it
    @Test
    fun glyphwalkerIgnoresSpellsCastDuringAnotherPlayersTurn() = scenario(players = 3) {
        alice.battlefield(glyphwalker)
        bob.battlefield(courser, "Mountain")
        bob.hand(jolt)
        bob.library("Mountain")
        turn(number = 1, active = alice, priority = bob)
        bob.casts(jolt, target = "carol")
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(courser, defender = "alice")
        expect { courser.tapped shouldBe true }
    }

    // CR 506.2, 508.1, 101.2: the opponent who attacked you this turn can't cast spells; another opponent still can
    @Test
    fun glyphwalkerSilencesTheOpponentWhoAttackedYou() = scenario(players = 3) {
        alice.battlefield(glyphwalker)
        bob.battlefield(courser, "Mountain")
        bob.hand(jolt)
        carol.battlefield("Mountain")
        carol.hand("Quickflame Jolt")
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(courser, defender = "alice")
        assertFailsWith<IllegalArgumentException> { bob.casts(jolt, target = "alice") }
        bob.passes()
        carol.casts(jolt, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    private val acolyte = Cards.invent("Ashen Acolyte", "{3}{B}", "Creature — Human Wizard",
        "When this creature enters, sacrifice another creature. You gain X life and draw X cards, where X is that creature's power.", "2/1")
    private val egg = Cards.invent("Mottled Saurian Egg", "{1}{G}", "Creature — Dinosaur Egg", "When this creature dies, you may discover X, where X is its toughness.", "0/3")
    private val fruit = Cards.invent("Elderbough Fruit", "{3}{G}", "Enchantment — Aura",
        "Enchant creature\nWhen enchanted creature dies, you gain X life and draw X cards, where X is its toughness.")
    private val edict = Cards.invent("Gloomfall Edict", "{1}{B}", "Instant", "Destroy target creature.")
    private val hauler = Cards.invent("Ridgeplate Hauler", "{3}{G}", "Creature — Beast", "", "4/4")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    // CR 608.2h: X is the sacrificed creature's power as it last existed, its two +1/+1 counters included (1 + 2)
    @Test
    fun acolyteReadsTheSacrificedCreaturesPower() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.battlefield(sprite, counters = listOf(p1p1 to 2))
        alice.hand(acolyte)
        alice.library("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.casts(acolyte)
        resolveAll()
        expect {
            alice.graveyard has sprite
            alice.life shouldBe 23
            alice.hand.size shouldBe 3
        }
    }

    // CR 603.10a: the dies trigger reads the toughness it had on the battlefield (3 + 1 counter), so discover 4 finds a mana value 4 card
    @Test
    fun eggDiscoversByItsLastToughness() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.battlefield(egg, counters = listOf(p1p1 to 1))
        alice.hand(edict)
        alice.library(hauler, "Swamp")
        alice.casts(edict, target = egg)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect { alice.hand has hauler }
    }

    // CR 603.10a: the enchanted creature's toughness as it last existed (1 + 2 counters)
    @Test
    fun fruitReadsTheDeadCreaturesToughness() = scenario {
        alice.battlefield("Swamp", "Swamp", fruit)
        alice.battlefield(sprite, counters = listOf(p1p1 to 2))
        attach(fruit, sprite)
        alice.hand(edict)
        alice.library("Swamp", "Swamp", "Swamp", "Swamp")
        alice.casts(edict, target = sprite)
        resolveAll()
        expect {
            alice.life shouldBe 23
            alice.hand.size shouldBe 3
        }
    }
}
