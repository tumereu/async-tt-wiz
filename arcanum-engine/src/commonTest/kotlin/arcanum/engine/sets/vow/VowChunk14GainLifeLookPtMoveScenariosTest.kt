package arcanum.engine.sets.vow

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertTrue

/** VOW chunk 14: first strike only while attacking, a graveyard ability that gains life, a dies trigger that looks at the top card
 * (reveal it for the hand, else maybe the graveyard), and a power boost for another Wolf or Werewolf. */
class VowChunk14GainLifeLookPtMoveScenariosTest {
    private val petal = Cards.invent("Dusk Petal Dancer", "{1}{R}", "Creature — Vampire", """
        This creature has first strike as long as it's attacking.
        When this creature dies, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
    """.trimIndent(), "2/1")
    private val wurm = Cards.invent("Briarthorn Wurm", "{6}{G}", "Creature — Wurm", """
        Reach, trample
        When this creature enters, you gain 5 life.
        {2}{G}, Exile this card from your graveyard: You gain 5 life.
    """.trimIndent(), "7/6")
    private val ghoul = Cards.invent("Graveyard Ghoul of Thorn", "{2}{B}", "Creature — Zombie Cleric",
        "Whenever this creature or another Zombie you control dies, look at the top card of your library. If it's a Zombie card, you may reveal it and put it into your hand. If you don't put the card into your hand, you may put it into your graveyard.",
        "3/2")
    private val shambler = Cards.invent("Mire Shambler", "{1}{B}", "Creature — Zombie", "", "2/2")
    private val ogre = Cards.invent("Reedbank Brute", "{1}{R}", "Creature — Ogre", "", "2/2")
    private val wolfCub = Cards.invent("Thicket Wolfling", "{1}{R}", "Creature — Wolf",
        "As long as you control another Wolf or Werewolf, this creature gets +1/+0 and has trample.", "2/2")
    private val pup = Cards.invent("Glade Pup", "{1}{G}", "Creature — Wolf", "", "2/2")
    private val howler = Cards.invent("Moonlit Howler", "{2}{G}", "Creature — Human Werewolf", "", "3/3")
    private val doom = Cards.invent("Final Doom", "{B}", "Instant", "Destroy target creature.")

    // CR 702.7b (first strike), 611.3a: the static ability grants first strike only while the creature is attacking
    @Test
    fun petalHasFirstStrikeOnlyWhileAttacking() = scenario {
        alice.battlefield(petal)
        expect { petal lacksKeyword Keyword.FirstStrike.kind }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(petal)
        expect { petal hasKeyword Keyword.FirstStrike.kind }
        advanceTo(TurnStep.End)
        expect { petal lacksKeyword Keyword.FirstStrike.kind }
    }

    // CR 113.6m, 602.2: from the graveyard, paying {2}{G}, the card exiles itself and its controller gains 5 life
    @Test
    fun wurmExilesItselfFromTheGraveyardToGainLife() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.graveyard(wurm)
        alice.activates(wurm)
        resolveAll()
        expect {
            wurm.zone shouldBe Zone.Exile
            alice.life shouldBe 25
        }
    }

    // CR 603.10a (dies triggers look back), 701.20 (reveal): a Zombie on top is revealed and put into the hand when the player agrees
    @Test
    fun ghoulDyingTakesAZombieFromTheTopIntoTheHand() = scenario {
        alice.battlefield(ghoul, "Swamp")
        alice.hand(doom)
        alice.library(shambler, "Swamp", "Swamp")
        alice.casts(doom, target = ghoul)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.hand has shambler
            alice.graveyard lacks shambler
        }
    }

    // CR 603.10a: another Zombie dying triggers it; a card that isn't a Zombie can only go to the graveyard (never the hand)
    @Test
    fun anotherZombieDyingLooksAndANonZombieMayGoToTheGraveyard() = scenario {
        alice.battlefield(ghoul, shambler, "Swamp")
        alice.hand(doom)
        alice.library("Mountain", "Swamp", "Swamp")
        alice.casts(doom, target = shambler)
        resolveAll()
        assertTrue(pending is Decision.May, "the may put it into the graveyard is offered")
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.graveyard has "Mountain"
            alice.hand lacks "Mountain"
        }
    }

    // CR 603.10a: refusing the Zombie, then the graveyard, are two separate choices; declining both leaves the card on top
    @Test
    fun decliningTheZombieOffersTheGraveyard() = scenario {
        alice.battlefield(ghoul, "Swamp")
        alice.hand(doom)
        alice.library(shambler, "Swamp", "Swamp")
        alice.casts(doom, target = ghoul)
        resolveAll()
        answer(Answer.YesNo(false))
        assertTrue(pending is Decision.May, "the graveyard choice follows")
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.graveyard has shambler
            alice.hand lacks shambler
        }
    }

    // CR 603.10a: a Zombie you control is needed; an opponent's creature or a non-Zombie dying doesn't trigger it
    @Test
    fun aNonZombieDyingDoesNotTrigger() = scenario {
        alice.battlefield(ghoul, ogre, "Swamp")
        alice.hand(doom)
        alice.library(shambler, "Swamp", "Swamp")
        alice.casts(doom, target = ogre)
        resolveAll()
        assertTrue(pending == null, "no trigger")
        expect {
            alice.library.size shouldBe 3
            alice.hand lacks shambler
        }
    }

    // CR 611.3a, 613.4c, 613.1f: +1/+0 and trample only with another Wolf or Werewolf under your control (not counting itself)
    @Test
    fun wolflingNeedsAnotherWolfOrWerewolf() = scenario {
        alice.battlefield(wolfCub)
        expect {
            wolfCub.power shouldBe 2
            wolfCub lacksKeyword Keyword.Trample.kind
        }
        alice.battlefield(pup)
        expect {
            wolfCub.power shouldBe 3
            wolfCub hasKeyword Keyword.Trample.kind
            pup.power shouldBe 2
        }
    }

    @Test
    fun wolflingCountsAWerewolfButNotAnOpponentsWolf() = scenario {
        alice.battlefield(wolfCub)
        bob.battlefield(pup)
        expect { wolfCub.power shouldBe 2 }
        alice.battlefield(howler)
        expect { wolfCub.power shouldBe 3 }
    }
}
