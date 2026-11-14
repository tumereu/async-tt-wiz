package arcanum.engine.sets.woe

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ecl.paySuggested
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * WOE chunk 14: the invented names carry the oracle text of Rankle's Prank, Likeness Looter and Heartflame Duelist; the other
 * cards of the chunk are mechanically the same as cards tested in earlier sets or chunks.
 */
class WoeChunk14DestroyDiscardDrawScenariosTest {
    private val prank = Cards.invent("Pall of Jests", "{2}{B}{B}", "Sorcery", """
        Choose one or more —
        • Each player discards two cards.
        • Each player loses 4 life.
        • Each player sacrifices two creatures of their choice.
    """)
    private val looter = Cards.invent("Mimic Looter", "{U}{B}", "Creature — Faerie Shapeshifter", """
        Flying
        {T}: Draw a card, then discard a card.
        {X}: This creature becomes a copy of target creature card in your graveyard with mana value X, except it has flying and this ability. Activate only as a sorcery.
    """, "1/1")
    private val duelist = Cards.inventFaces("Adventure",
        face("Cinder Duelist", "{1}{W}", "Creature — Human Knight", "Instant and sorcery spells you control have lifelink.", "3/1", null, null),
        face("Cinder Slash", "{2}{R}", "Instant — Adventure",
            "Cinder Slash deals 3 damage to any target. (Then exile this card. You may cast the creature later from exile.)", null, null, null))
    private val spark = Cards.invent("Ember Dart", "{R}", "Instant", "Ember Dart deals 3 damage to target creature.")
    private val bear = Cards.invent("Hollowbrook Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val bear2 = Cards.invent("Hollowbrook Cub", "{1}{G}", "Creature — Bear", "", "1/1")
    private val bear3 = Cards.invent("Hollowbrook Elder", "{1}{G}", "Creature — Bear", "", "3/3")
    private val bear4 = Cards.invent("Hollowbrook Kin", "{1}{G}", "Creature — Bear", "", "2/1")
    private val trampler = Cards.invent("Fenmire Charger", "{1}{R}", "Creature — Beast", "Trample", "3/2")
    private val bigger = Cards.invent("Fenmire Titan", "{2}{G}", "Creature — Giant", "Reach", "4/4")

    // CR 700.2 (several modes, in printed order), 101.4 (each player chooses their own sacrifices), 701.9, 119: three players;
    // a player with fewer than two creatures sacrifices the one she has, and a short hand is discarded whole
    @Test
    fun prankRunsEveryChosenModeForEachOfThreePlayers() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", bear, bear2)
        alice.hand(prank, "Island", "Plains")
        bob.battlefield(bear3, bear4, trampler)
        bob.hand("Mountain")
        carol.battlefield(bigger)
        carol.hand("Forest", "Forest")
        alice.casts(prank, modes = listOf(0, 1, 2))
        resolveAll()
        bob.chooses(bear3, trampler)
        resolveAll()
        expect {
            alice.life shouldBe 16
            bob.life shouldBe 16
            carol.life shouldBe 16
            alice.hand.names shouldBe emptyList<String>()
            bob.hand.names shouldBe emptyList<String>()
            bear.zone shouldBe Zone.Graveyard
            bear2.zone shouldBe Zone.Graveyard
            bear3.zone shouldBe Zone.Graveyard
            trampler.zone shouldBe Zone.Graveyard
            bear4.zone shouldBe Zone.Battlefield
            bigger.zone shouldBe Zone.Graveyard
            carol.hand.names shouldBe emptyList<String>()
        }
    }

    // CR 700.2: a single chosen mode runs alone
    @Test
    fun prankWithOnlyTheLifeLossMode() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(prank, "Island")
        bob.battlefield(bear3)
        alice.casts(prank, modes = listOf(1))
        resolveAll()
        expect {
            alice.life shouldBe 16
            bob.life shouldBe 16
            carol.life shouldBe 16
            bear.zone shouldBe Zone.Battlefield
            bear3.zone shouldBe Zone.Battlefield
        }
    }

    // CR 707.2 and 707.9a/b: it becomes a copy of the card with flying added and keeps this ability (CR 707.9b: the exception is
    // part of the copiable values), so it can copy again; it loses its tap ability, which the copied card doesn't have
    @Test
    fun looterBecomesACopyWithFlyingAndKeepsTheAbility() = scenario {
        alice.battlefield(looter, "Island", "Island", "Island", "Island", "Island", "Island")
        alice.graveyard(trampler, bigger)
        alice.activates(looter, ability = 1, x = 2, target = trampler)
        if (pending is arcanum.engine.decision.Decision.ManaPayment) paySuggested()
        resolveAll()
        expect {
            looter.power shouldBe 3
            looter.toughness shouldBe 2
            looter hasKeyword Keyword.Trample.kind
            looter hasKeyword Keyword.Flying.kind
        }
        // the copy ability is still its only activated ability (the tap ability is gone), and it works again
        alice.activates(looter, ability = 0, x = 3, target = bigger)
        if (pending is arcanum.engine.decision.Decision.ManaPayment) paySuggested()
        resolveAll()
        expect {
            looter.power shouldBe 4
            looter.toughness shouldBe 4
            looter hasKeyword Keyword.Reach.kind
            looter hasKeyword Keyword.Flying.kind
            looter lacksKeyword Keyword.Trample.kind
        }
    }

    // CR 107.3 and 601.2c: the chosen card's mana value must equal X
    @Test
    fun looterNeedsAGraveyardCreatureWithManaValueX() = scenario {
        alice.battlefield(looter, "Island", "Island", "Island", "Island")
        alice.graveyard(trampler)
        assertFailsWith<IllegalArgumentException> { alice.activates(looter, ability = 1, x = 3, target = trampler) }
    }

    // CR 602.5d: only as a sorcery, so not in the opponent's turn
    @Test
    fun looterCopiesOnlyAtSorcerySpeed() = scenario {
        alice.battlefield(looter, "Island", "Island", "Island")
        alice.graveyard(trampler)
        turn(number = 2, active = bob, priority = alice)
        assertFailsWith<IllegalArgumentException> { alice.activates(looter, ability = 1, x = 2, target = trampler) }
    }

    // CR 702.15b (lifelink on the damage of a spell), 613.1f: Alice's own instant gains life for her, Bob's doesn't
    @Test
    fun duelistGivesLifelinkToYourInstantsOnly() = scenario {
        alice.battlefield(duelist, "Mountain", bear)
        alice.hand(spark)
        bob.battlefield("Mountain", bear3)
        alice.casts(spark, target = bear3)
        resolveAll()
        expect {
            bear3.zone shouldBe Zone.Graveyard
            alice.life shouldBe 23
        }
    }

    @Test
    fun duelistGivesNothingToTheOpponentsInstants() = scenario {
        alice.battlefield(duelist, "Mountain", bear)
        bob.battlefield("Mountain", bear3)
        bob.hand(spark)
        turn(number = 2, active = bob)
        bob.casts(spark, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            alice.life shouldBe 20
            bob.life shouldBe 20
        }
    }
}
