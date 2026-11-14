package arcanum.engine.sets.woe

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.eoe.pay
import kotlin.test.Test
import kotlin.test.assertTrue

/** WOE chunk 04, Bargain (CR 702.166): the invented names carry the oracle text of the cards that need something the engine tests haven't
 * played. A bargained cast is `kicked = 1` with the sacrificed permanent in `choosing`. */
class WoeChunk04BargainScenariosTest {
    private val bargain = "Bargain (You may sacrifice an artifact, enchantment, or token as you cast this spell.)"
    private val bauble = Cards.invent("Copper Bauble", "{1}", "Artifact")
    private val hedgehog = Cards.invent("Thistle Hedgehog", "{1}{G}", "Creature — Hedgehog", "", "2/2")
    private val colossus = Cards.invent("Cairn Colossus", "{4}{G}", "Creature — Giant", "", "5/5")
    private val brute = Cards.invent("Shale Brute", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val stoat = Cards.invent("Bramble Stoat", "{G}", "Creature — Weasel", "", "1/1")
    private val rustTheHinge = Cards.invent("Rust the Hinge", "{1}{R}", "Instant", "Destroy target artifact.")
    private val sprite = Cards.invent("Gravel Sprite", "{1}{U}", "Creature — Faerie", "", "1/4")

    private val secondsReclaimed = Cards.invent("Seconds Reclaimed", "{2}{B}", "Sorcery", """
        $bargain
        Return up to two target creature cards from your graveyard to your hand. If this spell was bargained, you may put one of those cards with mana value 4 or less onto the battlefield instead of putting it into your hand.
    """.trimIndent())
    private val pleadTheLookingGlass = Cards.invent("Plead the Looking Glass", "{1}{B}{B}{B}", "Sorcery", """
        $bargain
        Search your library for a card, exile it face down, then shuffle. If this spell was bargained, you may cast the exiled card without paying its mana cost if that spell's mana value is 4 or less. Put the exiled card into your hand if it wasn't cast this way.
    """.trimIndent())
    private val dareTheThicket = Cards.invent("Dare the Thicket", "{G}", "Sorcery", """
        $bargain
        If this spell was bargained, target land you control becomes a 3/3 Elemental creature with haste that's still a land.
        Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
    """.trimIndent())
    private val longsightRite = Cards.invent("Longsight Rite", "{2}{U}{U}", "Instant", """
        $bargain
        Look at the top four cards of your library. If this spell was bargained, look at the top eight cards of your library instead. Put two of them into your hand and the rest on the bottom of your library in a random order.
    """.trimIndent())
    private val villageGorger = Cards.invent("Village Gorger", "{5}{G}{G}", "Creature — Giant", """
        $bargain
        This spell costs {2} less to cast if it's bargained.
        Trample
        When this creature enters, you gain 3 life.
    """.trimIndent(), "6/6")
    private val wendelsGlowblades = Cards.invent("Wendel's Glowblades", "{1}{W}", "Instant", """
        $bargain
        Wendel's Glowblades deals 3 damage to target attacking or blocking creature. If this spell was bargained, destroy that creature instead.
    """.trimIndent())
    private val ardenasGrimProbe = Cards.invent("Ardena's Grim Probe", "{2}{B}", "Instant", """
        $bargain
        If this spell was bargained, look at the top four cards of your library, then put up to two of them back on top of your library in any order and the rest into your graveyard.
        You draw two cards and you lose 2 life.
    """.trimIndent())
    private val rockcleaverBolt = Cards.invent("Rockcleaver Bolt", "{X}{R}", "Instant", """
        $bargain
        Rockcleaver Bolt deals X damage to target creature or planeswalker. If this spell was bargained, it deals twice X damage to that permanent instead.
    """.trimIndent())
    private val rumblingPremiere = Cards.invent("Rumbling Premiere", "{6}{G}{G}", "Sorcery", """
        $bargain
        Look at the top twenty cards of your library. You may reveal up to two creature cards from among them. If this spell was bargained, put the revealed cards onto the battlefield. Otherwise, put the revealed cards into your hand. Then shuffle.
    """.trimIndent())
    private val burnTheBelfry = Cards.invent("Burn the Belfry", "{R}", "Instant", """
        $bargain
        Burn the Belfry deals 2 damage to target creature or planeswalker. If this spell was bargained, instead it deals 3 damage to that permanent and you scry 1.
        If a permanent dealt damage by Burn the Belfry would die this turn, exile it instead.
    """.trimIndent())
    private val faeCourtHaggler = Cards.invent("Fae Court Haggler", "{3}{B}{B}", "Creature — Faerie Warlock", """
        $bargain
        Flying
        When this creature enters, if it was bargained, each opponent loses 3 life and you gain 3 life.
    """.trimIndent(), "3/5")

    private fun swamps(n: Int) = Array(n) { "Swamp" }
    private fun forests(n: Int) = Array(n) { "Forest" }

    // CR 702.166b, 400.7: one returned card with mana value 4 or less goes to the battlefield instead, the other to the hand
    @Test
    fun secondsReclaimedPutsOneOntoTheBattlefieldWhenBargained() = scenario {
        alice.battlefield(*swamps(3), bauble)
        alice.graveyard(hedgehog, colossus)
        alice.hand(secondsReclaimed)
        alice.casts(secondsReclaimed, targets = listOf(hedgehog, colossus), kicked = 1, choosing = listOf(bauble))
        resolveAll()
        expect { decision<Decision.ChooseObjects>().options.toSet() shouldBe setOf(card(hedgehog).id) }
        alice.chooses(hedgehog)
        resolveAll()
        expect {
            bauble.zone shouldBe Zone.Graveyard
            hedgehog.zone shouldBe Zone.Battlefield
            colossus.zone shouldBe Zone.Hand
        }
    }

    // CR 702.166b: not bargained, both go to the hand
    @Test
    fun secondsReclaimedReturnsBothToHandUnbargained() = scenario {
        alice.battlefield(*swamps(3), bauble)
        alice.graveyard(hedgehog, colossus)
        alice.hand(secondsReclaimed)
        alice.casts(secondsReclaimed, targets = listOf(hedgehog, colossus), kicked = 0)
        resolveAll()
        expect {
            bauble.zone shouldBe Zone.Battlefield
            alice.hand has hedgehog
            alice.hand has colossus
        }
    }

    // CR 702.166b, 406.3, 601.2: the face-down exiled card is cast without paying its mana cost
    @Test
    fun pleadTheLookingGlassCastsTheExiledCardWhenBargained() = scenario {
        alice.battlefield(*swamps(4), bauble)
        alice.library("Island", hedgehog, "Island")
        alice.hand(pleadTheLookingGlass)
        alice.casts(pleadTheLookingGlass, kicked = 1, choosing = listOf(bauble))
        resolveAll()
        alice.chooses(hedgehog)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            hedgehog.zone shouldBe Zone.Battlefield
            alice.hand.size shouldBe 0
        }
        assertTrue(state.battlefield.filter { state.objOrThrow(it).tapped }.size == 4)
    }

    // CR 702.166b: not bargained, the exiled card goes to the hand
    @Test
    fun pleadTheLookingGlassPutsTheCardIntoHandUnbargained() = scenario {
        alice.battlefield(*swamps(4), bauble)
        alice.library("Island", hedgehog, "Island")
        alice.hand(pleadTheLookingGlass)
        alice.casts(pleadTheLookingGlass, kicked = 0)
        resolveAll()
        alice.chooses(hedgehog)
        resolveAll()
        expect {
            alice.hand has hedgehog
            alice.exile.size shouldBe 0
        }
    }

    // CR 118.9, 601.2: the spell's mana value is 5, more than 4: it can't be cast this way and goes to the hand
    @Test
    fun pleadTheLookingGlassCantCastAFiveDrop() = scenario {
        alice.battlefield(*swamps(4), bauble)
        alice.library("Island", colossus, "Island")
        alice.hand(pleadTheLookingGlass)
        alice.casts(pleadTheLookingGlass, kicked = 1, choosing = listOf(bauble))
        resolveAll()
        alice.chooses(colossus)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            alice.hand has colossus
            alice.exile.size shouldBe 0
        }
    }

    // CR 601.2c, 608.2c: a spell with no legal target can't be cast this way, so it isn't cast and goes to the hand
    @Test
    fun pleadTheLookingGlassPutsAnUncastableCardIntoHand() = scenario {
        alice.battlefield(*swamps(4), bauble)
        alice.library("Island", rustTheHinge, "Island")
        alice.hand(pleadTheLookingGlass)
        alice.casts(pleadTheLookingGlass, kicked = 1, choosing = listOf(bauble))
        resolveAll()
        alice.chooses(rustTheHinge)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            alice.hand has rustTheHinge
            alice.exile.size shouldBe 0
        }
    }

    // CR 702.166b, 611.2a, 205.1b: the land is a 3/3 Elemental creature land with haste for good
    @Test
    fun dareTheThicketAnimatesALandWhenBargained() = scenario {
        alice.battlefield("Forest", "Forest", bauble)
        alice.library("Plains", "Island")
        bob.library("Island")
        alice.hand(dareTheThicket)
        alice.casts(dareTheThicket, target = "Forest", kicked = 1, choosing = listOf(bauble), paying = listOf("Forest#1"))
        resolveAll()
        alice.chooses("Plains")
        resolveAll()
        expect {
            val land = card("Forest", 0)
            land.power shouldBe 3
            land.toughness shouldBe 3
            land.types.toSet() shouldBe setOf(CardType.Land, CardType.Creature)
            land.subtypes.toSet() shouldBe setOf(Subtype("Forest"), Subtype("Elemental"))
            land hasKeyword Keyword.Haste.kind
            alice.hand has "Plains"
        }
        advanceTo(TurnStep.End)
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        expect { card("Forest", 0).power shouldBe 3 }
    }

    // CR 601.2c: not bargained, the spell has no target (two lands, yet no target is asked)
    @Test
    fun dareTheThicketNeedsNoTargetUnbargained() = scenario {
        alice.battlefield("Forest", "Plains", bauble)
        alice.library("Plains", "Forest")
        alice.hand(dareTheThicket)
        alice.casts(dareTheThicket, kicked = 0)
        expect { assertTrue(pending !is Decision.Targets) }
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
        resolveAll()
        alice.chooses("Forest")
        resolveAll()
        expect { alice.hand has "Forest" }
    }

    // CR 702.166b: bargained, the top eight are looked at instead of four
    @Test
    fun longsightRiteLooksAtEightWhenBargained() = scenario {
        alice.battlefield(*Array(4) { "Island" }, bauble)
        alice.library("Plains", "Plains", "Plains", "Plains", "Swamp", "Forest", hedgehog, colossus, "Mountain", "Mountain")
        alice.hand(longsightRite)
        alice.casts(longsightRite, kicked = 1, choosing = listOf(bauble))
        resolveAll()
        expect { decision<Decision.ChooseObjects>().options.size shouldBe 8 }
        alice.chooses(hedgehog, colossus)
        resolveAll()
        expect {
            alice.hand.names.toSet() shouldBe setOf(hedgehog, colossus)
            alice.library.names.take(2) shouldBe listOf("Mountain", "Mountain")
            alice.library.size shouldBe 8
        }
    }

    // CR 702.166b, 401.4: unbargained only the top four
    @Test
    fun longsightRiteLooksAtFourUnbargained() = scenario {
        alice.battlefield(*Array(4) { "Island" })
        alice.library("Plains", "Swamp", "Forest", hedgehog, colossus, "Mountain")
        alice.hand(longsightRite)
        alice.casts(longsightRite, kicked = 0)
        resolveAll()
        expect { decision<Decision.ChooseObjects>().options.size shouldBe 4 }
        alice.chooses("Forest", hedgehog)
        resolveAll()
        expect {
            alice.hand.names.toSet() shouldBe setOf("Forest", hedgehog)
            alice.library.names.take(2) shouldBe listOf(colossus, "Mountain")
        }
    }

    // CR 601.2f: the additional cost is chosen before the total cost is determined; bargained it costs {3}{G}{G}
    @Test
    fun villageGorgerCostsTwoLessWhenBargained() = scenario {
        alice.battlefield(*forests(5), bauble)
        alice.hand(villageGorger)
        alice.casts(villageGorger, kicked = 1, choosing = listOf(bauble))
        pay()
        expect {
            villageGorger.zone shouldBe Zone.Battlefield
            alice.life shouldBe 23
        }
    }

    // CR 601.2f: without the bargain the full {5}{G}{G} is due, which five lands can't pay
    @Test
    fun villageGorgerCostsFullUnbargained() = scenario {
        alice.battlefield(*forests(5), bauble)
        alice.hand(villageGorger)
        alice.casts(villageGorger, kicked = 0)
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // CR 702.166b, 506.4: bargained, the attacking creature is destroyed instead of dealt 3 damage
    @Test
    fun wendelsGlowbladesDestroysWhenBargained() = scenario {
        bob.battlefield(colossus)
        alice.battlefield("Plains", "Plains", bauble)
        alice.hand(wendelsGlowblades)
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(colossus)
        bob.passes()
        alice.casts(wendelsGlowblades, target = colossus, kicked = 1, choosing = listOf(bauble))
        resolveAll()
        expect { colossus.zone shouldBe Zone.Graveyard }
    }

    // CR 702.166b: unbargained, 3 damage doesn't kill a 5/5
    @Test
    fun wendelsGlowbladesDealsThreeUnbargained() = scenario {
        bob.battlefield(colossus)
        alice.battlefield("Plains", "Plains")
        alice.hand(wendelsGlowblades)
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(colossus)
        bob.passes()
        alice.casts(wendelsGlowblades, target = colossus, kicked = 0)
        resolveAll()
        expect {
            colossus.zone shouldBe Zone.Battlefield
            colossus.view.obj?.damage shouldBe 3
        }
    }

    // CR 702.166b, 401.4: two kept on top in the chosen order, the rest milled, then those two drawn
    @Test
    fun ardenasGrimProbeKeepsTwoOnTopWhenBargained() = scenario {
        alice.battlefield(*swamps(3), bauble)
        alice.library("Plains", hedgehog, "Island", colossus, "Mountain")
        alice.hand(ardenasGrimProbe)
        alice.casts(ardenasGrimProbe, kicked = 1, choosing = listOf(bauble))
        resolveAll()
        alice.chooses(hedgehog, colossus)
        resolveAll()
        // the order, first on top: the Colossus is drawn first
        alice.chooses(colossus, hedgehog)
        resolveAll()
        expect {
            alice.graveyard has "Plains"
            alice.graveyard has "Island"
            alice.hand.names shouldBe listOf(colossus, hedgehog)
            alice.library.names shouldBe listOf("Mountain")
            alice.life shouldBe 18
        }
    }

    // CR 702.166b, 107.3: twice X
    @Test
    fun rockcleaverBoltDealsTwiceXWhenBargained() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bauble)
        bob.battlefield(brute)
        alice.hand(rockcleaverBolt)
        alice.casts(rockcleaverBolt, target = brute, x = 2, kicked = 1, choosing = listOf(bauble))
        resolveAll()
        expect { brute.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun rockcleaverBoltDealsXUnbargained() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        bob.battlefield(brute)
        alice.hand(rockcleaverBolt)
        alice.casts(rockcleaverBolt, target = brute, x = 2, kicked = 0)
        resolveAll()
        expect { brute.view.obj?.damage shouldBe 2 }
    }

    // CR 702.166b, 701.20: the revealed creature cards go onto the battlefield when bargained
    @Test
    fun rumblingPremierePutsRevealedCreaturesOntoTheBattlefieldWhenBargained() = scenario {
        alice.battlefield(*forests(8), bauble)
        alice.library("Plains", hedgehog, "Island", colossus, "Mountain")
        alice.hand(rumblingPremiere)
        alice.casts(rumblingPremiere, kicked = 1, choosing = listOf(bauble))
        resolveAll()
        expect { decision<Decision.ChooseObjects>().options.toSet() shouldBe setOf(card(hedgehog).id, card(colossus).id) }
        alice.chooses(hedgehog, colossus)
        resolveAll()
        expect {
            hedgehog.zone shouldBe Zone.Battlefield
            colossus.zone shouldBe Zone.Battlefield
            alice.library.size shouldBe 3
        }
    }

    // CR 702.166b: otherwise into the hand
    @Test
    fun rumblingPremierePutsRevealedCreaturesIntoHandUnbargained() = scenario {
        alice.battlefield(*forests(8))
        alice.library("Plains", hedgehog, "Island", colossus, "Mountain")
        alice.hand(rumblingPremiere)
        alice.casts(rumblingPremiere, kicked = 0)
        resolveAll()
        alice.chooses(colossus)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(colossus)
            alice.library.size shouldBe 4
        }
    }

    // CR 702.166b, 614.1a, 700.4: bargained, 3 damage kills the 3/3, which is exiled instead; then scry 1
    @Test
    fun burnTheBelfryExilesWhenBargained() = scenario {
        alice.battlefield("Mountain", bauble)
        alice.library("Plains", "Island")
        bob.battlefield(brute)
        alice.hand(burnTheBelfry)
        alice.casts(burnTheBelfry, target = brute, kicked = 1, choosing = listOf(bauble))
        resolveAll()
        expect { decision<Decision.ChooseObjects>() }
        alice.chooses("Plains")
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Exile
            alice.library.names shouldBe listOf("Island", "Plains")
        }
    }

    // CR 614.1a: unbargained, 2 damage kills the 1/1, which is exiled instead
    @Test
    fun burnTheBelfryExilesUnbargained() = scenario {
        alice.battlefield("Mountain")
        bob.battlefield(stoat, sprite)
        alice.hand(burnTheBelfry)
        alice.casts(burnTheBelfry, target = stoat, kicked = 0)
        resolveAll()
        expect { stoat.zone shouldBe Zone.Exile }
    }

    // CR 702.166b, 603.4: the enters trigger's "if it was bargained"; each opponent of three players (CR 102.1)
    @Test
    fun faeCourtHagglerDrainsEachOpponentWhenBargained() = scenario(players = 3) {
        alice.battlefield(*swamps(5), bauble)
        alice.hand(faeCourtHaggler)
        alice.casts(faeCourtHaggler, kicked = 1, choosing = listOf(bauble))
        resolveAll()
        expect {
            bob.life shouldBe 17
            carol.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    // CR 603.4: not bargained, the ability doesn't trigger
    @Test
    fun faeCourtHagglerDoesNothingUnbargained() = scenario(players = 3) {
        alice.battlefield(*swamps(5))
        alice.hand(faeCourtHaggler)
        alice.casts(faeCourtHaggler, kicked = 0)
        resolveAll()
        expect {
            faeCourtHaggler.zone shouldBe Zone.Battlefield
            bob.life shouldBe 20
            carol.life shouldBe 20
            alice.life shouldBe 20
        }
    }
}
