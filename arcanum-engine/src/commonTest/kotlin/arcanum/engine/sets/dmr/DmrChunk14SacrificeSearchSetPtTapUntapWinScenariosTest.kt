package arcanum.engine.sets.dmr

import arcanum.api.CardType
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import kotlin.test.assertFails
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.setup.GameResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Dominaria Remastered chunk 14 in play: bounce-or-sacrifice, graveyard counting, tap and untap effects, winning at upkeep. */
class DmrChunk14SacrificeSearchSetPtTapUntapWinScenariosTest {
    private val lair = Cards.invent("Ashen Lair Hollow", null, "Land — Lair", """
        When this land enters, sacrifice it unless you return a non-Lair land you control to its owner's hand.
        {T}: Add {U}, {B}, or {R}.
    """)
    private val otherLair = Cards.invent("Cinder Lair Den", null, "Land — Lair", """
        When this land enters, sacrifice it unless you return a non-Lair land you control to its owner's hand.
        {T}: Add {B}, {R}, or {G}.
    """)
    private val herder = Cards.invent("Flockherder Adept", "{2}{U}", "Creature — Human Wizard", """
        When this creature enters, sacrifice it unless you return three basic lands you control to their owner's hand.
        {T}, Return this creature to its owner's hand: Destroy target creature. It can't be regenerated. That creature's controller creates a 0/1 green Sheep creature token.
    """, "0/1")
    private val gorger = Cards.invent("Mire Gorger", "{1}{G}{G}", "Creature — Lhurgoyf", """
        Trample
        Mire Gorger's power and toughness are each equal to the number of land cards in all graveyards.
    """, "*/*")
    private val rod = Cards.invent("Winter Rod", "{4}", "Artifact", "{1}, {T}: Tap target artifact, creature, or land.")
    private val hex = Cards.invent("Standoff Hex", "{2}{U}{U}", "Enchantment", "Tap an untapped creature you control: Tap target artifact, creature, or land.")
    private val flip = Cards.invent("Flip Tide", "{2}{U}{U}", "Instant",
        "Choose artifact, creature, or land. Tap all untapped permanents of the chosen type target player controls, or untap all tapped permanents of that type that player controls.")
    private val hawk = Cards.invent("Gale Hawk of Dawn", "{4}{U}", "Creature — Drake", """
        Flying
        When this creature enters, untap up to five lands.
    """, "2/3")
    private val trial = Cards.invent("Stamina Trial", "{2}{W}{W}", "Enchantment", "At the beginning of your upkeep, if you have 50 or more life, you win the game.")

    private val bear = Cards.invent("Dmr Fourteen Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Dmr Fourteen Ox", "{2}{W}", "Creature — Ox", "", "2/3")
    private val relic = Cards.invent("Dmr Fourteen Relic", "{1}", "Artifact", "")

    private fun Scenario.yes() = answer(Answer.YesNo(true))

    // CR 603.2, 118.12: returning a land that isn't a Lair keeps the new Lair
    @Test
    fun lairStaysWhenANonLairLandIsReturned() = scenario {
        alice.battlefield("Forest")
        alice.hand(lair)
        alice.plays(lair)
        resolve()
        yes()
        if (pending != null) alice.chooses("Forest")
        expect {
            alice.battlefield has lair
            alice.hand has "Forest"
        }
    }

    // CR 118.12: declining the payment sacrifices the land
    @Test
    fun lairIsSacrificedWhenYouDecline() = scenario {
        alice.battlefield("Forest")
        alice.hand(lair)
        alice.plays(lair)
        resolve()
        answer(Answer.YesNo(false))
        expect {
            alice.graveyard has lair
            alice.battlefield has "Forest"
        }
    }

    // CR 118.12: another Lair isn't a non-Lair land, so with nothing else to return the new Lair is sacrificed
    @Test
    fun lairCannotReturnAnotherLair() = scenario {
        alice.battlefield(otherLair)
        alice.hand(lair)
        alice.plays(lair)
        resolve()
        if (pending is Decision.May) yes()
        expect {
            alice.graveyard has lair
            alice.battlefield has otherLair
        }
    }

    // CR 118.12: three basic lands must all be returned; a nonbasic land doesn't count
    @Test
    fun herderNeedsThreeBasicLands() = scenario {
        alice.battlefield("Island", "Island", otherLair)
        alice.hand(herder)
        alice.castsWithoutCosts(herder)
        resolve()
        resolve()
        if (pending is Decision.May) yes()
        expect {
            alice.graveyard has herder
            alice.battlefield has "Island"
        }
    }

    @Test
    fun herderStaysWithThreeBasicLands() = scenario {
        alice.battlefield("Island", "Island", "Island", otherLair)
        alice.hand(herder)
        alice.castsWithoutCosts(herder)
        resolve()
        resolve()
        yes()
        if (pending != null) alice.chooses("Island", "Island", "Island")
        expect {
            alice.battlefield has herder
            alice.hand.size shouldBe 3
            alice.battlefield has otherLair
        }
    }

    // CR 604.3, 208.2a, 400.1: lands in every player's graveyard count, in a game of three; nonland cards don't
    @Test
    fun gorgerCountsLandCardsInAllGraveyards() = scenario(players = 3) {
        alice.battlefield(gorger)
        alice.graveyard("Forest", bear)
        bob.graveyard("Island", "Swamp")
        carol.graveyard("Mountain", relic)
        expect {
            gorger.power shouldBe 4
            gorger.toughness shouldBe 4
        }
    }

    // CR 701.26a, 602.2b: the cost taps one of your untapped creatures; the target is tapped
    @Test
    fun hexTapsATargetByTappingYourCreature() = scenario {
        alice.battlefield(hex, bear)
        bob.battlefield(ox)
        alice.activates(hex, target = ox, choosing = listOf("Dmr Fourteen Bear"))
        resolve()
        expect {
            bear.tapped shouldBe true
            ox.tapped shouldBe true
        }
    }

    // CR 602.2b: a tapped creature can't pay it
    @Test
    fun hexNeedsAnUntappedCreature() = scenario {
        alice.battlefield(hex)
        bob.battlefield(ox)
        assertFails { alice.activates(hex, target = ox) }
    }

    // CR 701.26a, 602.2: tap the opponent's land with the rod
    @Test
    fun rodTapsALand() = scenario {
        alice.battlefield(rod, "Island", "Island")
        bob.battlefield("Forest")
        alice.activates(rod, target = "Forest")
        resolve()
        expect { "Forest".tapped shouldBe true }
    }

    // CR 700.2, 701.26, 701.26b: choose a type, then tap all untapped or untap all tapped permanents of it that player controls
    @Test
    fun flipTapsAllUntappedCreaturesOfTheTargetPlayer() = scenario {
        alice.hand(flip)
        alice.battlefield("Island", "Island", "Island", "Island")
        bob.battlefield(bear, ox, "Forest")
        alice.casts(flip, target = "bob")
        allPass()
        answer(Answer.CardTypeChoice(CardType.Creature))
        answer(Answer.Option("tap"))
        expect {
            bear.tapped shouldBe true
            ox.tapped shouldBe true
        }
        expect { "Forest".tapped shouldBe false }
    }

    @Test
    fun flipUntapsOnlyThatPlayersTappedPermanentsOfTheType() = scenario(players = 3) {
        alice.hand(flip)
        alice.battlefield("Island", "Island", "Island", "Island", bear)
        bob.battlefield(ox, relic)
        carol.battlefield(Cards.invent("Dmr Fourteen Hound", "{1}", "Creature — Dog", "", "1/1"))
        change(arcanum.engine.change.Changes.tap(state, card(ox)))
        change(arcanum.engine.change.Changes.tap(state, card(relic)))
        alice.casts(flip, target = "bob")
        allPass()
        answer(Answer.CardTypeChoice(CardType.Creature))
        answer(Answer.Option("untap"))
        expect {
            ox.tapped shouldBe false
            relic.tapped shouldBe true
        }
    }

    // CR 608.2d: "up to five" lands, chosen on resolution; only tapped lands change
    @Test
    fun hawkUntapsUpToFiveLands() = scenario {
        alice.hand(hawk)
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.casts(hawk)
        allPass()
        resolve()
        val options = (pending as Decision.ChooseObjects).options
        val tappedLands = options.filter { state.objects.getValue(it).tapped }
        assertEquals(5, tappedLands.size)
        answer(Answer.Objects(tappedLands.take(4)))
        assertEquals(1, state.objects.values.count { it.zone == Zone.Battlefield && it.tapped && it.definition.faces[0].name == "Island" })
    }

    // CR 104.2b, 603.4: at 50 life the upkeep trigger wins the game; at 49 it never triggers
    @Test
    fun trialWinsAtFiftyLife() = scenario {
        alice.battlefield(trial)
        alice.life = 50
        alice.library("Forest", "Forest")
        bob.library("Forest", "Forest")
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolve()
        assertEquals(GameResult.Won(alice.id), Engine.result(state))
    }

    @Test
    fun trialDoesNothingAtFortyNineLife() = scenario {
        alice.battlefield(trial)
        alice.life = 49
        alice.library("Forest", "Forest")
        bob.library("Forest", "Forest")
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { state.stack.size shouldBe 0 }
        assertNull(Engine.result(state))
    }
}
