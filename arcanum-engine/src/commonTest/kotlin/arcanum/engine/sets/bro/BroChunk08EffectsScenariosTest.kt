package arcanum.engine.sets.bro

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The Brothers' War chunk 08 in play: damage to a chosen opponent and their creatures (three players), a hand discarded and drawn
 * again with one card of each mana value returned, a permanent handed to a chosen opponent, a graveyard card exiled with life loss
 * for its owner, cards exiled for the damage dealt, two face-down piles an opponent chooses between, an opponent's same-named tokens
 * exiled, a Powerstone unless a card was played from exile, life gained for the damage dealt, a dies trigger's optional life gain,
 * a land revealing a Soldier as it enters, a defender that loses defender and becomes blue, and a combat damage trigger for any
 * creature's controller with lands played and spells cast from an opponent's library.
 */
class BroChunk08EffectsScenariosTest {
    private val sack = Cards.invent("The Sack of Ostmere", "{4}{R}{R}", "Sorcery",
        "Choose target opponent. Destroy target land that player controls. The Sack of Ostmere deals 3 damage to that player and 1 damage to each creature they control.")
    private val duchess = Cards.invent("Duchess Varna of Brenmoor", "{1}{R}{W}", "Legendary Creature — Human Noble",
        "{4}, {T}: Discard all the cards in your hand, then draw that many cards. You may choose an artifact or creature card with mana value 1 you discarded this way, then do the same for artifact or creature cards with mana values 2 and 3. Return those cards to the battlefield. Activate only as a sorcery.",
        "2/3")
    private val bequest = Cards.invent("Grim Bequest", "{3}{B}", "Sorcery",
        "Draw cards equal to the mana value of target artifact or creature you control. An opponent gains control of that permanent.")
    private val locust = Cards.invent("Gravepick Locust", "{2}{B}", "Creature — Insect Horror", """
        Flying
        When this creature enters, exile target card from an opponent's graveyard. If it was a creature card, that player loses 1 life.
    """, "2/1")
    private val orrin = Cards.invent("Orrin, Quarry Delver", "{1}{R}", "Legendary Creature — Human Artificer", """
        Haste
        Orrin can't block.
        Whenever Orrin is dealt damage, exile that many cards from the top of your library. Choose one of them. Until the end of your next turn, you may play that card.
    """, "2/2")
    private val parley = Cards.invent("Bitter Parley", "{3}{B}", "Instant",
        "Exile the top three cards of your library in a face-down pile, then exile the top three cards of your library in another face-down pile. Look at the cards in each pile, then turn a pile of your choice face up. An opponent chooses one of those piles. Put that pile into your hand and the other into your graveyard. You lose 3 life.")
    private val ranks = Cards.invent("Ranks to Cinders", "{1}{W}{B}", "Sorcery",
        "Exile target nonland permanent an opponent controls and all tokens that player controls with the same name as that permanent.")
    private val glimpses = Cards.invent("Glimpses of Ruin", "{2}{R}{R}", "Enchantment", """
        At the beginning of your upkeep, exile the top card of your library. You may play that card this turn.
        At the beginning of your end step, if you didn't play a card from exile this turn, create a tapped Powerstone token. (It's an artifact with "{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.")
    """)
    private val siphon = Cards.invent("Bogsoul Siphon", "{5}{B}", "Sorcery",
        "Bogsoul Siphon deals damage to any target equal to the number of Swamps you control. You gain life equal to the damage dealt this way.")
    private val spider = Cards.invent("Cloudnet Spider", "{2}{B}{G}", "Creature — Spider", """
        Reach
        When this creature enters, you may sacrifice another creature. When you do, destroy target nonland permanent.
        When this creature dies, you may gain 1 life for each creature card in your graveyard. If you do, exile this card from your graveyard.
    """, "3/3")
    private val shoreline = Cards.invent("Garrisoned Shoreline", null, "Land", """
        As this land enters, you may reveal a Soldier card from your hand. This land enters tapped unless you revealed a Soldier card this way or you control a Soldier.
        {T}: Add {W} or {U}.
        {5}, {T}: Soldiers you control get +1/+1 until end of turn.
    """)
    private val wavecoil = Cards.invent("Wavecoil Engine", "{2}", "Artifact Creature — Construct", """
        Defender
        {U}: This creature loses defender and gains "This creature can't be blocked."
        {2}{U}: This creature becomes blue and has base power and toughness 5/4. Activate only if this creature doesn't have defender.
        {4}{U}{U}: Draw three cards. Activate only if this creature is blue and only once.
    """, "3/2")
    private val vashk = Cards.invent("Vashk, Grinning Usurper", "{1}{B}{B}", "Legendary Creature — Phyrexian Praetor", """
        Whenever a creature deals combat damage to one of your opponents, its controller may pay 1 life. If they do, they draw a card.
        {4}{B}{B}{B}, Discard X cards: Exile the top X cards of target opponent's library. You may play lands and cast spells from among cards exiled this way without paying their mana costs.
    """, "3/3")

    private val mite = Cards.invent("Copper Mite", "{1}", "Artifact Creature — Insect", "", "1/1")
    private val recruit = Cards.invent("Shoreline Recruit", "{W}", "Creature — Human Soldier", "", "1/1")
    private val rally = Cards.invent("Rally of Shields", "{W}", "Sorcery", "Each player creates a 1/1 white Soldier creature token.")
    private val kilnBurst = Cards.invent("Kiln Burst", "{1}{R}", "Instant", "Kiln Burst deals 3 damage to any target.")
    private val staticPrick = Cards.invent("Static Prick", "{R}", "Instant", "Static Prick deals 1 damage to any target.")
    private val emberShard = Cards.invent("Ember Shard", "{R}", "Instant", "Ember Shard deals 2 damage to any target.")
    private val totem = Cards.invent("Brass Totem", "{2}", "Artifact", "{T}: You gain 1 life.")
    private val grizzly = Cards.invent("Burrow Grizzly", "{1}{G}", "Creature — Beast", "", "2/2")
    private val ogre = Cards.invent("Hillside Ogre", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val hound = Cards.invent("Cinder Hound", "{2}{R}", "Creature — Dog", "", "3/3")

    // CR 115.1, 120.2: the chosen opponent loses a land and takes 3; only their creatures take 1, the third player's and yours don't
    @Test
    fun sackHitsOnlyTheTargetOpponentAndTheirCreatures() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", mite)
        alice.hand(sack)
        bob.battlefield("Forest", mite)
        carol.battlefield("Plains", mite)
        alice.casts(sack, targets = listOf("bob", "Forest"))
        allPass()
        expect {
            bob.life shouldBe 17
            carol.life shouldBe 20
            bob.graveyard has "Forest"
            bob.graveyard has mite
            carol.battlefield has mite
            alice.battlefield has mite
        }
    }

    // CR 701.9a: five cards discarded, five drawn; one artifact or creature card of each mana value 1, 2 and 3 returns, the instant and
    // the land stay in the graveyard
    @Test
    fun duchessReturnsOneDiscardedCardOfEachManaValue() = scenario {
        alice.battlefield(duchess, "Plains", "Plains", "Plains", "Plains")
        alice.hand(mite, totem, hound, staticPrick, "Mountain")
        alice.library("Island", "Island", "Island", "Island", "Island", "Island")
        alice.activates(duchess)
        allPass()
        playOut(mite, totem, hound)
        expect {
            alice.hand.size shouldBe 5
            alice.battlefield has mite
            alice.battlefield has totem
            alice.battlefield has hound
            alice.graveyard has staticPrick
            alice.graveyard has "Mountain"
        }
    }

    // CR 602.5d: only as a sorcery
    @Test
    fun duchessOnlyAsASorcery() = scenario {
        turn(number = 2, active = bob, priority = alice)
        alice.battlefield(duchess, "Plains", "Plains", "Plains", "Plains")
        alice.hand(mite)
        assertFailsWith<IllegalArgumentException> { alice.activates(duchess) }
    }

    // CR 608.2h: four cards for the mana value 4 creature; CR 613.1b: the opponent chosen as it resolves gains control of it
    @Test
    fun bequestDrawsAndTheChosenOpponentGainsControl() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", ogre)
        alice.hand(bequest)
        alice.library("Island", "Island", "Island", "Island", "Island")
        alice.casts(bequest, target = ogre)
        allPass()
        decision<Decision.ChoosePlayer>()
        answer(Answer.Player(carol.id))
        expect {
            alice.hand.size shouldBe 4
            ogre.controller shouldBe carol.id
        }
    }

    // CR 404.1: the creature card's owner loses 1 life, not the other opponent
    @Test
    fun locustMakesTheGraveyardsOwnerLoseLifeForACreatureCard() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(locust)
        bob.graveyard("Forest")
        carol.graveyard(grizzly)
        alice.casts(locust)
        playOut(grizzly)
        expect {
            carol.exile has grizzly
            carol.life shouldBe 19
            bob.life shouldBe 20
        }
    }

    // A land card exiled: no life lost
    @Test
    fun locustExilingALandCardCostsNoLife() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(locust)
        bob.graveyard("Forest")
        carol.graveyard(grizzly)
        alice.casts(locust)
        playOut("Forest")
        expect {
            bob.exile has "Forest"
            bob.life shouldBe 20
            carol.life shouldBe 20
        }
    }

    // CR 120.3: dealt 2 damage, two cards exiled; only the chosen one may be played
    @Test
    fun orrinExilesThatManyAndOnlyTheChosenOneMayBePlayed() = scenario {
        alice.battlefield(orrin, counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        alice.battlefield("Mountain", "Forest", "Forest")
        alice.hand(emberShard)
        alice.library(grizzly, "Island", "Plains")
        alice.casts(emberShard, target = orrin)
        playOut(grizzly)
        expect {
            alice.exile has grizzly
            alice.exile has "Island"
            alice.library.size shouldBe 1
        }
        assertFailsWith<IllegalArgumentException> { alice.plays("Island") }
        alice.casts(grizzly)
        allPass()
        expect { alice.battlefield has grizzly }
    }

    // CR 406.3: you turn the first pile face up; the opponent takes the second for you: it goes to your hand, the first to your graveyard
    @Test
    fun parleyOpponentChoosesWhichPileYouGet() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(parley)
        alice.library("Island", "Island", "Island", "Forest", "Forest", "Forest", "Plains")
        alice.casts(parley)
        allPass()
        assertEquals(alice.id, decision<Decision.ChooseOption>().player)
        answer(Answer.Option("first"))
        if (pending is Decision.ChoosePlayer) answer(Answer.Player(bob.id))
        assertEquals(bob.id, decision<Decision.ChooseOption>().player)
        answer(Answer.Option("second"))
        expect {
            alice.hand.size shouldBe 3
            alice.hand has "Forest"
            alice.hand lacks "Island"
            alice.graveyard.size shouldBe 4
            alice.life shouldBe 17
            alice.library.size shouldBe 1
        }
    }

    // CR 111.1, 201.2: the target and its controller's tokens of that name are exiled; the same tokens of the other players stay
    @Test
    fun ranksExilesOnlyThatPlayersTokensOfTheName() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Swamp")
        alice.hand(rally, rally, ranks)
        alice.casts(rally)
        allPass()
        alice.casts(rally)
        allPass()
        alice.casts(ranks)
        answer(Answer.Targets(listOf(TargetRef.Object(cards("Soldier Token", owner = bob).first().id))))
        playOut()
        expect {
            alice.graveyard has ranks
            cards("Soldier Token", owner = bob, zone = Zone.Battlefield).size shouldBe 0
            cards("Soldier Token", owner = alice, zone = Zone.Battlefield).size shouldBe 2
            cards("Soldier Token", owner = carol, zone = Zone.Battlefield).size shouldBe 2
        }
    }

    // CR 603.4: nothing played from exile this turn: a tapped Powerstone at the end step
    @Test
    fun glimpsesMakesAPowerstoneWhenNothingWasPlayedFromExile() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield(glimpses)
        alice.library("Island", "Island", "Island", "Island")
        bob.library("Island", "Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut()
        advanceTo(TurnStep.End)
        playOut()
        expect {
            alice.battlefield has "Powerstone Token"
            "Powerstone Token".tapped shouldBe true
        }
    }

    // CR 305.1: playing the exiled land is playing a card from exile: no Powerstone
    @Test
    fun glimpsesMakesNoPowerstoneAfterTheExiledLandIsPlayed() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield(glimpses)
        alice.library("Forest", "Island", "Island", "Island")
        bob.library("Island", "Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut()
        advanceTo(TurnStep.PrecombatMain)
        alice.plays("Forest")
        advanceTo(TurnStep.End)
        playOut()
        expect {
            alice.battlefield has "Forest"
            alice.exile lacks "Forest"
            alice.battlefield lacks "Powerstone Token"
        }
    }

    // CR 120.3: 3 damage dealt by three Swamps: 3 life gained
    @Test
    fun siphonGainsTheDamageDealt() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Plains", "Plains", "Plains")
        alice.hand(siphon)
        alice.casts(siphon, target = "bob")
        allPass()
        expect {
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    // CR 400.7: in the graveyard as the trigger resolves, it counts itself: three creature cards; then it is exiled
    @Test
    fun spiderGainsForEachCreatureCardThenExilesItself() = scenario {
        alice.battlefield(spider, "Mountain", "Mountain")
        alice.graveyard(grizzly, hound, "Forest")
        alice.hand(kilnBurst)
        alice.casts(kilnBurst, target = spider)
        playOut()
        expect {
            alice.life shouldBe 23
            alice.exile has spider
        }
    }

    // CR 118.12: declined, no life and it stays in the graveyard
    @Test
    fun spiderDeclinedStaysInTheGraveyard() = scenario {
        alice.battlefield(spider, "Mountain", "Mountain")
        alice.graveyard(grizzly)
        alice.hand(kilnBurst)
        alice.casts(kilnBurst, target = spider)
        playOut(may = false)
        expect {
            alice.life shouldBe 20
            alice.graveyard has spider
        }
    }

    // CR 614.12, 614.1c: a Soldier card revealed: it enters untapped
    @Test
    fun shorelineUntappedWhenASoldierIsRevealed() = scenario {
        alice.hand(shoreline, recruit)
        alice.plays(shoreline)
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(listOf(card(recruit))))
        expect { shoreline.tapped shouldBe false }
    }

    // CR 614.1c: nothing revealed and no Soldier: tapped
    @Test
    fun shorelineTappedWithoutASoldier() = scenario {
        alice.hand(shoreline, recruit)
        alice.plays(shoreline)
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(emptyList()))
        expect { shoreline.tapped shouldBe true }
    }

    // CR 614.1c: a Soldier you control is enough
    @Test
    fun shorelineUntappedWhileYouControlASoldier() = scenario {
        alice.battlefield(recruit)
        alice.hand(shoreline)
        alice.plays(shoreline)
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(emptyList()))
        expect { shoreline.tapped shouldBe false }
    }

    // CR 602.5b: not while it has defender; CR 613.1d, 613.4b: blue and 5/4 from then on; "only once" for as long as it exists
    @Test
    fun wavecoilLosesDefenderThenBecomesBlueThenDrawsOnce() = scenario {
        alice.battlefield(wavecoil, "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.library("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        assertFailsWith<IllegalArgumentException> { alice.activates(wavecoil, ability = 1) }
        alice.activates(wavecoil, ability = 0)
        allPass()
        expect { wavecoil lacksKeyword Keyword.Defender.kind }
        assertFailsWith<IllegalArgumentException> { alice.activates(wavecoil, ability = 2) }
        alice.activates(wavecoil, ability = 1)
        allPass()
        expect {
            wavecoil.colors shouldBe listOf(Color.Blue)
            wavecoil.power shouldBe 5
            wavecoil.toughness shouldBe 4
        }
        alice.activates(wavecoil, ability = 2)
        allPass()
        expect { alice.hand.size shouldBe 3 }
        assertFailsWith<IllegalArgumentException> { alice.activates(wavecoil, ability = 2) }
    }

    // CR 510.2: your creature hits an opponent: you may pay 1 life and draw
    @Test
    fun vashkYourAttackerPaysLifeToDraw() = scenario {
        alice.battlefield(vashk, grizzly)
        alice.library("Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(grizzly)
        advanceTo(TurnStep.CombatDamage)
        playOut()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 19
            alice.hand.size shouldBe 1
        }
    }

    // Three players: another player's creature hitting one of your opponents triggers it too; its controller pays and draws
    @Test
    fun vashkAnotherPlayersCreatureHittingYourOpponent() = scenario(players = 3) {
        turn(number = 2, active = bob)
        alice.battlefield(vashk)
        bob.battlefield(grizzly)
        bob.library("Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(grizzly, defender = "carol")
        advanceTo(TurnStep.CombatDamage)
        playOut()
        expect {
            carol.life shouldBe 18
            bob.life shouldBe 19
            bob.hand.size shouldBe 1
            alice.hand.size shouldBe 0
        }
    }

    // CR 107.3a: X = 2, two cards discarded; CR 608.2g, 305.2a: the exiled land is played and the creature cast free as it resolves
    @Test
    fun vashkPlaysTheExiledLandAndCastsTheSpellFree() = scenario {
        alice.battlefield(vashk, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(staticPrick, "Mountain")
        bob.library("Forest", grizzly, "Island")
        alice.activates(vashk, x = 2, target = "bob")
        playOut()
        expect {
            alice.graveyard has staticPrick
            alice.graveyard has "Mountain"
            alice.battlefield has "Forest"
            alice.battlefield has grizzly
            "Forest".controller shouldBe alice.id
            bob.library.size shouldBe 1
        }
    }

    // CR 305.2b: with the land play used already, the exiled land can't be played; the spell is still cast
    @Test
    fun vashkCantPlayTheExiledLandWithoutALandPlayLeft() = scenario {
        alice.battlefield(vashk, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(staticPrick, "Mountain", "Plains")
        bob.library("Forest", grizzly, "Island")
        alice.plays("Plains")
        alice.activates(vashk, x = 2, target = "bob")
        playOut()
        expect {
            alice.battlefield has grizzly
            alice.battlefield lacks "Forest"
            bob.exile has "Forest"
        }
    }
}
