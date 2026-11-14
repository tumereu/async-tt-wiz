package arcanum.engine.sets.bro

import arcanum.api.CounterKind
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * BRO sweep: siblings of the engine and parser fixes the set's chunks made, played. A name a later sentence counts ("the life lost this
 * way", "the discarded card") bound by every path that makes it; a permission to cast spells from a library matching cards there; a
 * creature put onto the battlefield attacking a player or their planeswalker its controller chooses; a land played by an effect under the
 * same "can't" as any land play; "it" and "that player" after an effect on a target meaning the target and its owner; a filter comparing an
 * object's power and toughness in a trigger.
 */
class BroSweepScenariosTest {
    private val chorus = Cards.invent("Bloodtithe Chorus", "{3}{B}", "Sorcery",
        "Each opponent loses 3 life. You gain life equal to the life lost this way.")
    private val veinTithe = Cards.invent("Vein Tithe of Kroog", "{1}{B}", "Sorcery", """
        Kicker {2}
        Target opponent loses 2 life. If this spell was kicked, you gain life equal to the life lost this way.
    """)
    private val ledger = Cards.invent("Bonepile Ledger", "{1}{B}", "Enchantment",
        "{1}{B}, Discard a creature card: Draw a card. If the discarded card was a Zombie card, create a tapped 2/2 black Zombie creature token.")
    private val buriedZombie = Cards.invent("Burrowed Zombie", "{1}{B}", "Creature — Zombie", "", "2/2")
    private val gamble = Cards.invent("Cinderlot Gamble", "{2}{R}", "Enchantment",
        "{3}, Discard a card at random: This enchantment deals damage to any target equal to the mana value of the discarded card.")
    private val stalwart = Cards.invent("Ostmere Stalwart", "{3}{R}", "Creature — Human Soldier", "", "4/4")
    private val wanderer = Cards.invent("Kinshift Wanderer", "{2}{G}", "Creature — Shapeshifter", """
        Changeling
        As this creature enters, choose a creature type.
        You may look at the top card of your library any time.
        You may cast creature spells of the chosen type from the top of your library.
    """, "2/3")
    private val elf = Cards.invent("Thornvine Elf", "{G}", "Creature — Elf", "", "1/1")
    private val hoplite = Cards.invent("Myriadine Hoplite", "{3}{W}", "Creature — Human Soldier", "Myriad", "2/2")
    private val tessa = Cards.invent("Tessa of the Banners", "{2}{W}", "Legendary Planeswalker — Tessa", "[+1]: You gain 1 life.", loyalty = "3")
    private val vashk = Cards.invent("Vashk, Grinning Usurper", "{1}{B}{B}", "Legendary Creature — Phyrexian Praetor", """
        Whenever a creature deals combat damage to one of your opponents, its controller may pay 1 life. If they do, they draw a card.
        {4}{B}{B}{B}, Discard X cards: Exile the top X cards of target opponent's library. You may play lands and cast spells from among cards exiled this way without paying their mana costs.
    """, "3/3")
    private val warden = Cards.invent("Exile Warden of Kroog", "{1}{W}", "Enchantment", "Each player can't play land cards from exile.")
    private val grizzly = Cards.invent("Burrow Grizzly", "{1}{G}", "Creature — Beast", "", "2/2")
    private val staticPrick = Cards.invent("Static Prick", "{R}", "Instant", "Static Prick deals 1 damage to any target.")
    private val sentinel = Cards.invent("Grimtusk Sentinel", "{2}{B}", "Creature — Boar", """
        When this creature enters, exile up to one target creature an opponent controls. If it was a Soldier, draw a card.
    """, "2/2")
    private val soldier = Cards.invent("Ashmark Soldier", "{W}", "Creature — Human Soldier", "", "1/1")
    private val kite = Cards.invent("Gravegleaner Kite", "{2}", "Artifact",
        "{T}: Exile target card from an opponent's graveyard. If it was a creature card, that player loses 2 life.")
    private val grazer = Cards.invent("Thickhide Grazer", "{1}{G}", "Creature — Beast", "", "1/4")
    private val raider = Cards.invent("Ironrot Raider", "{1}{B}", "Creature — Zombie", "", "3/1")
    private val shalefield = Cards.invent("Shalefield Warden", "{1}{G}", "Enchantment",
        "Whenever a creature you control with toughness greater than its power enters, draw a card.")

    private fun Scenario.libraries() { players.forEach { it.library("Island", "Island", "Island", "Island", "Island") } }

    // CR 119.3, 608.2c: "the life lost this way" is what the loss took, every opponent's (three players: 3 from each of two)
    @Test
    fun lifeLostThisWayCountsEveryOpponentsLoss() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(chorus)
        alice.casts(chorus)
        allPass()
        expect {
            bob.life shouldBe 17
            carol.life shouldBe 17
            alice.life shouldBe 26
        }
    }

    // CR 119.3, 702.33d: "the life lost this way" behind "If this spell was kicked" is the loss before it
    @Test
    fun lifeLostThisWayAfterAKickerCondition() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(veinTithe)
        alice.casts(veinTithe, target = "bob", kicked = 1)
        allPass()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    // CR 602.2b, 608.2h: "the discarded card" is the creature card discarded to pay the cost, as it last existed in the hand
    @Test
    fun theDiscardedCardOfACostIsTheCardPaid() = scenario {
        alice.battlefield(ledger, "Swamp", "Swamp")
        alice.hand(buriedZombie)
        alice.library("Island")
        alice.activates(ledger, choosing = listOf(buriedZombie))
        allPass()
        expect {
            alice.graveyard has buriedZombie
            alice.hand has "Island"
            alice.battlefield.names.count { it == "Zombie Token" } shouldBe 1
        }
    }

    // CR 602.2b, 701.9b: the card discarded at random for the cost is "the discarded card"; its mana value is the damage
    @Test
    fun theCardDiscardedAtRandomForACostSetsTheDamage() = scenario {
        alice.battlefield(gamble, "Mountain", "Mountain", "Mountain")
        alice.hand(stalwart)
        alice.activates(gamble, target = "bob")
        allPass()
        expect {
            alice.graveyard has stalwart
            bob.life shouldBe 16
        }
    }

    // CR 602.2b, 601.2h: with no card in hand the random discard can't be paid, so the ability can't be activated
    @Test
    fun aRandomDiscardCostNeedsACardInHand() = scenario {
        alice.battlefield(gamble, "Mountain", "Mountain", "Mountain")
        assertFailsWith<IllegalArgumentException> { alice.activates(gamble, target = "bob") }
        expect { bob.life shouldBe 20 }
    }

    // CR 601.2a, 601.3: a card in the library is cast from there; "creature spells of the chosen type" are the cards that become them
    @Test
    fun creatureSpellsOfTheChosenTypeAreCastFromTheTopOfTheLibrary() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(wanderer)
        alice.library(elf, "Island")
        alice.casts(wanderer)
        allPass()
        decision<Decision.ChooseSubtype>()
        answer(Answer.SubtypeChoice(Subtype("Elf")))
        playOut()
        alice.casts(elf)
        allPass()
        expect {
            alice.battlefield has elf
        }
    }

    // CR 702.116a, 508.4: three players; the myriad token for the opponent who isn't defending attacks that player or a planeswalker they
    // control, as its controller chooses
    @Test
    fun myriadTokenAttacksThatPlayerOrTheirPlaneswalker() = scenario(players = 3) {
        libraries()
        alice.battlefield(hoplite)
        carol.battlefield(tessa, counters = listOf(CounterKind.Loyalty to 3))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hoplite, defender = "bob")
        allPass()
        decision<Decision.May>()
        answer(Answer.YesNo(true))
        val choice = decision<Decision.Targets>()
        assertEquals(setOf(TargetRef.Player(carol.id), TargetRef.Object(card(tessa))), choice.options.toSet())
        answer(Answer.Targets(listOf(TargetRef.Object(card(tessa)))))
        playOut()
        val token = state.combat!!.attackers.single { it.id != card(hoplite) }
        assertEquals(TargetRef.Object(card(tessa)), token.target)
    }

    // CR 305.2b, 101.2: a land played as an effect resolves is still a land play, so "can't play land cards from exile" stops it; the spell is
    // still cast
    @Test
    fun aLandPlayedByAnEffectObeysCantPlayLands() = scenario {
        alice.battlefield(vashk, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(staticPrick, "Mountain")
        bob.battlefield(warden)
        bob.library("Forest", grizzly, "Island")
        alice.activates(vashk, x = 2, target = "bob")
        playOut("Forest", "Burrow Grizzly")
        expect {
            alice.battlefield has grizzly
            alice.battlefield lacks "Forest"
            bob.exile has "Forest"
        }
    }

    // CR 608.2h: "it" is the exiled target as it last existed on the battlefield (a Soldier), not this creature (a Boar)
    @Test
    fun ifItWasASoldierReadsTheExiledTarget() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(sentinel)
        alice.library("Island")
        bob.battlefield(soldier)
        alice.casts(sentinel)
        allPass()
        playOut(soldier)
        expect {
            bob.exile has soldier
            alice.hand has "Island"
        }
    }

    // CR 608.2h: three players; "that player" is the owner of the exiled card (Carol), not another opponent
    @Test
    fun thatPlayerIsTheOwnerOfTheCardExiledFromAGraveyard() = scenario(players = 3) {
        alice.battlefield(kite)
        bob.graveyard(raider)
        carol.graveyard(grazer)
        alice.activates(kite, target = grazer)
        allPass()
        expect {
            carol.exile has grazer
            carol.life shouldBe 18
            bob.life shouldBe 20
        }
    }

    // CR 603.2, 603.6a: a filter comparing the entering creature's own toughness and power is read on that creature (1/4 draws, 3/1 doesn't)
    @Test
    fun toughnessGreaterThanItsPowerIsReadOnTheEnteringCreature() = scenario {
        alice.battlefield(shalefield, "Forest", "Forest", "Swamp", "Swamp")
        alice.hand(grazer, raider)
        alice.library("Island", "Island")
        alice.casts(grazer)
        allPass()
        playOut()
        alice.casts(raider)
        allPass()
        playOut()
        expect {
            alice.hand.size shouldBe 1
            alice.library.size shouldBe 1
        }
    }
}
