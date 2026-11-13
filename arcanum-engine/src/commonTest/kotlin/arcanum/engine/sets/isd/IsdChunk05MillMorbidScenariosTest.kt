package arcanum.engine.sets.isd

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** ISD chunk 05: mill effects that read the milled cards (CR 701.17c, d), a replacement that turns combat damage into milling, a trigger
 * on cards going from an opponent's library to their graveyard, and morbid ("if a creature died this turn", CR 700.4) on a search, an
 * enters trigger that picks at random, a damage spell, enters-with-counters and an activation condition. Invented names carry the oracle
 * text of the real cards. */
class IsdChunk05MillMorbidScenariosTest {
    private val chime = Cards.invent("Gravecaller's Chime", "{1}", "Artifact", "{T}: Each player mills a card.")
    private val penance = Cards.invent("Heathen's Penance", "{4}{R}", "Enchantment",
        "{3}{R}: Choose any target, then mill three cards. This enchantment deals damage to that permanent or player equal to the greatest mana value among the milled cards.")
    private val mindwailer = Cards.invent("Mindwailer", "{1}{U}", "Creature — Spirit Bird", """
        Flying
        {2}: Target player mills a card. This creature gets +X/+X until end of turn, where X is the milled card's mana value.
    """, "1/1")
    private val distiller = Cards.invent("Unliving Distiller", "{3}{U}", "Creature — Zombie", """
        If a Zombie you control would deal combat damage to a player, instead that player mills that many cards.
        Whenever a creature card is put into an opponent's graveyard from their library, exile that card and create a 2/2 black Zombie creature token.
    """, "4/2")
    private val shoals = Cards.invent("Drownmarsh Shoals", null, "Land", """
        {T}: Add {C}.
        {1}{U}{B}, {T}: Target player mills three cards.
    """)
    private val shardfright = Cards.invent("Shardfright", "{2}{G}", "Creature — Elemental", """
        Trample
        Shardfright's power and toughness are each equal to the number of creature cards in your graveyard.
        At the beginning of your upkeep, mill two cards. (Put the top two cards of your library into your graveyard.)
    """, "*/*")
    private val vigil = Cards.invent("Wagon Vigil", "{G}", "Sorcery", """
        Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
        Morbid — You may put that card onto the battlefield instead of putting it into your hand if a creature died this turn.
    """)
    private val sleuth = Cards.invent("Thicket Sleuth", "{3}{G}", "Creature — Human Scout",
        "Morbid — When this creature enters, if a creature died this turn, return a creature card at random from your graveyard to your hand.", "2/3")
    private val barrage = Cards.invent("Sulfur Barrage", "{2}{R}", "Instant", """
        Sulfur Barrage deals 3 damage to any target.
        Morbid — Sulfur Barrage deals 5 damage instead if a creature died this turn.
    """)
    private val boar = Cards.invent("Rotmane Boar", "{3}{G}", "Creature — Boar", """
        Trample
        Morbid — This creature enters with two +1/+1 counters on it if a creature died this turn.
    """, "3/3")
    private val banshee = Cards.invent("Mirerut Banshee", "{3}{B}{B}", "Creature — Spirit",
        "Morbid — When this creature enters, if a creature died this turn, target creature gets -4/-4 until end of turn.", "4/4")
    private val priest = Cards.invent("Skirmoor High Priest", "{1}{B}", "Creature — Human Cleric",
        "Morbid — {T}, Tap two untapped creatures you control: Create a 5/5 black Demon creature token with flying. Activate only if a creature died this turn.", "1/2")

    private val ogre = Cards.invent("Mirefen Ogre", "{3}{R}", "Creature — Ogre", "", "3/3")
    private val sprite = Cards.invent("Mirefen Sprite", "{U}", "Creature — Faerie", "", "1/1")
    private val bear = Cards.invent("Mirefen Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val rat = Cards.invent("Gutterfen Rat", "{B}", "Creature — Rat", "", "1/1")

    /** Kills Bob's 1/1 rat with Storm Spark: a creature died this turn (CR 700.4). */
    private fun arcanum.engine.scenario.Scenario.killRat() {
        alice.casts("Storm Spark", target = rat)
        allPass()
    }

    // CR 701.17a: each player, three of them, mills their top card
    @Test
    fun chimeMillsEachPlayer() = scenario(players = 3) {
        alice.battlefield(chime)
        alice.library("Forest", "Island")
        bob.library("Swamp", "Plains")
        carol.library("Mountain", "Island")
        alice.activates(chime)
        allPass()
        expect {
            alice.graveyard has "Forest"
            bob.graveyard has "Swamp"
            carol.graveyard has "Mountain"
            alice.library has "Island"
            bob.library has "Plains"
        }
    }

    // CR 701.17c, 202.3: the milled cards' greatest mana value is 4 (the Ogre); a land counts 0
    @Test
    fun penanceDealsTheGreatestMilledManaValue() = scenario {
        alice.battlefield(penance, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.library(sprite, ogre, "Forest", "Island")
        alice.activates(penance, target = "bob")
        allPass()
        expect {
            bob.life shouldBe 16
            alice.graveyard has ogre
            alice.graveyard has sprite
            alice.graveyard has "Forest"
            alice.library has "Island"
        }
    }

    // CR 701.17b, 120.3: with only lands milled, the damage is 0
    @Test
    fun penanceMillingOnlyLandsDealsNothing() = scenario {
        alice.battlefield(penance, "Mountain", "Mountain", "Mountain", "Mountain")
        bob.battlefield(bear)
        alice.library("Forest", "Island")
        alice.activates(penance, target = bear)
        allPass()
        expect {
            bear.zone shouldBe Zone.Battlefield
            alice.graveyard has "Island"
        }
    }

    // CR 701.17c, 608.2h: X is the Ogre's mana value, 4, fixed as the ability resolves; CR 613.4c
    @Test
    fun mindwailerGrowsByTheMilledCardsManaValue() = scenario {
        alice.battlefield(mindwailer, "Island", "Island")
        bob.library(ogre, "Swamp")
        alice.activates(mindwailer, target = "bob")
        allPass()
        expect {
            bob.graveyard has ogre
            mindwailer.power shouldBe 5
            mindwailer.toughness shouldBe 5
        }
    }

    // CR 614.1a: the Zombie's 4 combat damage becomes milling four; a non-Zombie's damage is dealt as usual. CR 603.2: each creature card put
    // into Bob's graveyard from his library is exiled and makes a Zombie token
    @Test
    fun distillerTurnsZombieDamageIntoMillAndMilledCreaturesIntoZombies() = scenario {
        alice.battlefield(distiller, bear)
        bob.library(ogre, "Island", sprite, "Plains", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(distiller, bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bob.life shouldBe 18
            bob.exile has ogre
            bob.exile has sprite
            bob.graveyard has "Island"
            bob.graveyard has "Plains"
            bob.library has "Forest"
            alice.battlefield.names.count { it == "Zombie Token" } shouldBe 2
        }
    }

    // CR 603.2: a creature card Alice mills herself goes to her own graveyard: no trigger
    @Test
    fun distillerIgnoresItsControllersOwnLibrary() = scenario {
        alice.battlefield(distiller, chime)
        alice.library(ogre, "Forest")
        bob.library(sprite, "Swamp")
        alice.activates(chime)
        allPass()
        resolveAll()
        expect {
            alice.graveyard has ogre
            bob.exile has sprite
            alice.battlefield.names.count { it == "Zombie Token" } shouldBe 1
        }
    }

    // CR 605.1a, 701.17a: Bob mills his top three cards
    @Test
    fun shoalsMillsThree() = scenario {
        alice.battlefield(shoals, "Island", "Swamp", "Plains")
        bob.library("Forest", "Island", "Mountain", "Plains")
        alice.activates(shoals, ability = 1, target = "bob")
        allPass()
        expect {
            bob.graveyard has "Forest"
            bob.graveyard has "Mountain"
            bob.library has "Plains"
        }
    }

    // CR 604.3, 208.2a: two creature cards in Alice's graveyard (not the land, not Bob's creature card): 2/2; milled at her upkeep, 4/4
    @Test
    fun shardfrightCountsCreatureCardsInItsControllersGraveyard() = scenario {
        alice.battlefield(shardfright)
        alice.graveyard(ogre, sprite, "Forest")
        bob.graveyard(bear)
        alice.library(rat, "Mountain", "Island")
        bob.library("Swamp")
        expect {
            shardfright.power shouldBe 2
            shardfright.toughness shouldBe 2
        }
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect {
            alice.graveyard has rat
            shardfright.power shouldBe 3
            shardfright.toughness shouldBe 3
        }
    }

    // CR 608.2c: no creature died this turn: the land goes to the hand, nothing is asked
    @Test
    fun vigilWithoutMorbidPutsTheLandIntoTheHand() = scenario {
        alice.battlefield("Forest")
        alice.hand(vigil)
        alice.library("Plains", "Island")
        alice.casts(vigil)
        allPass()
        alice.chooses("Plains")
        resolveAll()
        expect {
            alice.hand has "Plains"
            vigil.zone shouldBe Zone.Graveyard
        }
    }

    // CR 700.4, 608.2c: after a creature died, Alice may put the land onto the battlefield instead
    @Test
    fun vigilWithMorbidMayPutTheLandOntoTheBattlefield() = scenario {
        alice.battlefield("Forest", "Mountain")
        alice.hand(vigil, "Storm Spark")
        bob.battlefield(rat)
        alice.library("Plains", "Island")
        killRat()
        alice.casts(vigil)
        allPass()
        alice.chooses("Plains")
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.battlefield has "Plains"
            alice.hand lacks "Plains"
        }
    }

    // CR 608.2c, 118.12: declining puts it into the hand
    @Test
    fun vigilWithMorbidDeclinedPutsTheLandIntoTheHand() = scenario {
        alice.battlefield("Forest", "Mountain")
        alice.hand(vigil, "Storm Spark")
        bob.battlefield(rat)
        alice.library("Plains", "Island")
        killRat()
        alice.casts(vigil)
        allPass()
        alice.chooses("Plains")
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.hand has "Plains"
            alice.battlefield lacks "Plains"
        }
    }

    // CR 603.4, 700.4: after a creature died the trigger returns a creature card chosen at random (scripted: the Sprite); the Ogre stays
    @Test
    fun sleuthReturnsACreatureCardAtRandom() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Mountain", "Mountain")
        alice.hand(sleuth, "Storm Spark")
        alice.graveyard(ogre, sprite, "Island")
        bob.battlefield(rat)
        killRat()
        random({ _ -> Answer.RandomObjects(listOf(card(sprite))) })
        alice.casts(sleuth)
        allPass()
        resolveAll()
        expect {
            alice.hand has sprite
            alice.graveyard has ogre
            alice.graveyard has "Island"
        }
    }

    // CR 603.4: no creature died this turn: the enters ability doesn't trigger
    @Test
    fun sleuthWithoutMorbidDoesNothing() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(sleuth)
        alice.graveyard(ogre)
        alice.casts(sleuth)
        allPass()
        resolveAll()
        expect {
            alice.graveyard has ogre
            stack.names shouldBe emptyList()
        }
    }

    // CR 608.2c: no creature died this turn: 3 damage
    @Test
    fun barrageDealsThreeWithoutMorbid() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(barrage)
        alice.casts(barrage, target = "bob")
        allPass()
        expect { bob.life shouldBe 17 }
    }

    // CR 700.4, 608.2c: a creature died earlier this turn: 5 damage
    @Test
    fun barrageAfterADeath() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(barrage, "Storm Spark")
        bob.battlefield(rat)
        killRat()
        alice.casts(barrage, target = "bob")
        allPass()
        expect { bob.life shouldBe 15 }
    }

    // CR 614.1c, 700.4: enters with two +1/+1 counters after a creature died; without, a 3/3
    @Test
    fun boarEntersWithCountersAfterADeath() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Mountain")
        alice.hand(boar, "Storm Spark")
        bob.battlefield(rat)
        killRat()
        alice.casts(boar)
        allPass()
        expect {
            boar.power shouldBe 5
            boar.toughness shouldBe 5
        }
    }

    @Test
    fun boarWithoutMorbidIsAThreeThree() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(boar)
        alice.casts(boar)
        allPass()
        expect { boar.power shouldBe 3 }
    }

    // CR 603.4, 613.4c, 704.5f: after a death the Banshee's trigger gives Bob's 2/2 -4/-4 and it dies
    @Test
    fun bansheeShrinksATargetAfterADeath() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(banshee, "Storm Spark")
        bob.battlefield(rat, bear)
        killRat()
        alice.casts(banshee)
        allPass()
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(bear))))
        resolveAll()
        expect { bob.graveyard has bear }
    }

    // CR 602.5b: no creature died this turn: the ability can't be activated
    @Test
    fun priestNeedsADeath() = scenario {
        alice.battlefield(priest, bear, sprite)
        assertFailsWith<IllegalArgumentException> { alice.activates(priest, choosing = listOf(bear, sprite)) }
    }

    // CR 602.5b, 118.3, 302.6: after a death, the Priest and two other creatures Alice controls tap (summoning sickness doesn't stop a creature
    // being tapped for another permanent's cost) for a 5/5 flying Demon
    @Test
    fun priestMakesADemonAfterADeath() = scenario {
        alice.battlefield(priest, "Mountain")
        alice.battlefield(bear, sprite, sick = true)
        alice.hand("Storm Spark")
        bob.battlefield(rat)
        killRat()
        alice.activates(priest, choosing = listOf(bear, sprite))
        allPass()
        expect {
            "Demon Token".power shouldBe 5
            "Demon Token" hasKeyword Keyword.Flying.kind
            bear.tapped shouldBe true
            sprite.tapped shouldBe true
        }
    }

    // Audit: the cost taps two untapped creatures Alice controls: a tapped one or Bob's can't pay it (CR 118.3)
    @Test
    fun priestCantTapATappedOrAnOpponentsCreature() = scenario {
        alice.battlefield(priest, bear, "Mountain")
        alice.battlefield(sprite, tapped = true)
        alice.hand("Storm Spark")
        bob.battlefield(rat, ogre)
        killRat()
        assertFailsWith<IllegalArgumentException> { alice.activates(priest, choosing = listOf(bear, sprite)) }
        assertFailsWith<IllegalArgumentException> { alice.activates(priest, choosing = listOf(bear, ogre)) }
    }
}
