package arcanum.engine.sets.neo

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test

/** Chunk 14 of NEO in play: affinity with a look-and-reveal spell, an optional exile-cards additional cost that reduces the cost, a sacrifice cost
 * whose mana value is counted, and cost reductions for the controller's artifact and enchantment spells. */
class NeoChunk14AffinityCostsScenariosTest {
    private val heist = Cards.invent("Mirage Heist", "{5}{U}{U}", "Instant", """
        Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
        Look at the top seven cards of your library. You may reveal up to two artifact cards from among them and put them into your hand. Put the rest on the bottom of your library in a random order.
    """.trimIndent())
    private val march = Cards.invent("Procession of Dawn Light", "{X}{W}", "Instant", """
        As an additional cost to cast this spell, you may exile any number of white cards from your hand. This spell costs {2} less to cast for each card exiled this way.
        Exile target artifact, creature, or enchantment with mana value X or less.
    """.trimIndent())
    private val bargain = Cards.invent("Pawnbroker's Wager", "{1}{B}", "Instant", """
        As an additional cost to cast this spell, sacrifice an artifact or creature.
        You gain life equal to the sacrificed permanent's mana value. Draw two cards.
    """.trimIndent())
    private val tinkerer = Cards.invent("Eager Clockmaker", "{U}{R}", "Artifact Creature — Goblin Artificer", """
        Flying
        Artifact spells you cast cost {1} less to cast.
    """.trimIndent(), "2/2")
    private val scholar = Cards.invent("Grove Sage", "{G}{W}", "Enchantment Creature — Human Monk", """
        Lifelink
        Enchantment spells you cast cost {1} less to cast.
    """.trimIndent(), "2/2")
    private val jammer = Cards.invent("Signal Jam", "{U}{U}", "Instant", """
        As an additional cost to cast this spell, tap an untapped artifact you control or pay {1}.
        Counter target spell.
    """.trimIndent())
    private val switchboard = Cards.invent("Relay Board", "{3}", "Artifact", """
        {T}: Add one mana of any color.
        {1}, {T}, Tap another untapped artifact you control: Draw a card, then discard a card.
    """.trimIndent())
    private val gadget = Cards.invent("Tin Gadget", "{1}", "Artifact", "")
    private val gear = Cards.invent("Brass Gear", "{2}", "Artifact", "")
    private val cog = Cards.invent("Iron Cog", "{3}", "Artifact", "")
    private val charm = Cards.invent("Dull Charm", "{2}", "Enchantment", "")
    private val ward = Cards.invent("Quiet Ward", "{1}{W}", "Enchantment", "")
    private val ox = Cards.invent("Fenwick Steer", "{2}{G}", "Creature — Ox", "", "3/3")
    private val bear = Cards.invent("Hakone Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val pup = Cards.invent("Spirit Pup", "{R}", "Creature — Spirit", "", "2/2")
    private val whelp = Cards.invent("Dawn Whelp", "{W}", "Creature — Bird", "", "1/1")
    private val gull = Cards.invent("Dawn Gull", "{1}{W}", "Creature — Bird", "", "1/1")
    private val frog = Cards.invent("Pond Frog", "{U}", "Creature — Frog", "", "1/1")

    // CR 702.41a: two artifacts you control make it cost {3}{U}{U}; CR 701.20a-b (look, reveal), 401.4 (rest to the bottom)
    @Test
    fun heistCostsLessPerArtifactAndTakesUpToTwoArtifactsOfSeven() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", gadget, gear)
        alice.hand(heist)
        alice.library(cog, "Forest", "Swamp", frog, "Island", "Mountain", ox, bear)
        alice.casts(heist)
        resolveAll()
        alice.chooses(cog)
        resolveAll()
        expect {
            (0..5).count { card("Island", it).tapped } shouldBe 5
            alice.hand has cog
            alice.library.names.size shouldBe 7
            alice.library.names.first() shouldBe bear
        }
    }

    // CR 601.2b, 601.2f: the optional additional cost exiles white cards only; each reduces the cost by {2}; CR 118.7e: the colored {W} stays
    @Test
    fun marchExilesWhiteCardsToCostLessAndExilesTheTarget() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(march, whelp, gull, frog)
        bob.battlefield(bear)
        alice.casts(march, target = bear, x = 2, choosing = listOf(whelp))
        resolveAll()
        expect {
            (0..2).count { card("Plains", it).tapped } shouldBe 1
            whelp.zone shouldBe Zone.Exile
            gull.zone shouldBe Zone.Hand
            frog.zone shouldBe Zone.Hand
            bear.zone shouldBe Zone.Exile
        }
    }

    // CR 601.2b: exiling no cards leaves the full cost {2}{W}; the mana value of the target must be X or less (CR 107.3)
    @Test
    fun marchWithNoCardsExiledCostsFullAndSpareTargetsStay() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(march, whelp)
        bob.battlefield(bear, ox)
        alice.casts(march, target = bear, x = 2)
        alice.chooses()
        (pending as? Decision.ManaPayment)?.let { alice.answers(Answer.ManaPayment(it.suggestion)) }
        resolveAll()
        expect {
            (0..2).count { card("Plains", it).tapped } shouldBe 3
            bear.zone shouldBe Zone.Exile
            ox.zone shouldBe Zone.Battlefield
            whelp.zone shouldBe Zone.Hand
        }
    }

    // CR 601.2b, 608.2h: the sacrificed artifact's mana value (3) as it last existed is the life gained; two cards drawn
    @Test
    fun bargainGainsLifeEqualToTheSacrificedManaValue() = scenario {
        alice.battlefield("Swamp", "Swamp", cog, bear)
        alice.hand(bargain)
        alice.library(frog, whelp, "Island")
        alice.casts(bargain, choosing = listOf(cog))
        resolveAll()
        expect {
            alice.life shouldBe 23
            cog.zone shouldBe Zone.Graveyard
            bear.zone shouldBe Zone.Battlefield
            alice.hand has frog
            alice.hand has whelp
        }
    }

    // CR 601.2b: a creature pays as well; a token or land isn't needed
    @Test
    fun bargainSacrificingACreatureGainsItsManaValue() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand(bargain)
        alice.library(frog, whelp)
        alice.casts(bargain, choosing = listOf(bear))
        resolveAll()
        expect {
            alice.life shouldBe 22
            alice.graveyard has bear
        }
    }

    // CR 118.7, 601.2f: artifact spells cost {1} less, other spells don't; a creature spell that isn't an artifact is not
    @Test
    fun clockmakerMakesOnlyYourArtifactSpellsCheaper() = scenario {
        alice.battlefield("Island", "Mountain", tinkerer)
        alice.hand(gear, pup)
        alice.casts(gear, paying = listOf("Island"))
        resolveAll()
        expect {
            gear.zone shouldBe Zone.Battlefield
            card("Island").tapped shouldBe true
            card("Mountain").tapped shouldBe false
        }
        alice.casts(pup, paying = listOf("Mountain"))
        resolveAll()
        expect { pup.zone shouldBe Zone.Battlefield }
    }

    // CR 118.7, 601.2f: enchantment spells cost {1} less (the colored mana remains)
    @Test
    fun sageMakesYourEnchantmentSpellsCheaper() = scenario {
        alice.battlefield("Plains", "Forest", scholar)
        alice.hand(ward, gadget)
        alice.casts(ward, paying = listOf("Plains"))
        resolveAll()
        expect {
            ward.zone shouldBe Zone.Battlefield
            card("Forest").tapped shouldBe false
        }
        alice.casts(gadget, paying = listOf("Forest"))
        resolveAll()
        expect { gadget.zone shouldBe Zone.Battlefield }
    }

    // CR 118.12, 701.26a: the other artifact is tapped as part of the cost ({1} is not paid), so UU alone casts the counterspell
    @Test
    fun jammerTapsAnArtifactInsteadOfPayingOne() = scenario {
        bob.battlefield("Island", "Island", gadget)
        bob.hand(jammer)
        alice.battlefield("Forest", "Forest")
        alice.hand(bear)
        alice.casts(bear)
        alice.passes()
        bob.casts(jammer, target = bear, choosing = listOf(gadget), option = "tap")
        resolveAll()
        expect {
            gadget.tapped shouldBe true
            bear.zone shouldBe Zone.Graveyard
        }
    }

    // CR 118.12: "another untapped artifact": the board itself can't be tapped for its own cost, a second artifact is tapped
    @Test
    fun switchboardTapsAnotherArtifactToLoot() = scenario {
        alice.battlefield("Island", switchboard, gadget)
        alice.library(frog, whelp)
        alice.hand(pup)
        alice.activates(switchboard, ability = 1, choosing = listOf(gadget))
        resolveAll()
        if (pending != null) alice.chooses(pup)
        resolveAll()
        expect {
            gadget.tapped shouldBe true
            alice.hand has frog
            alice.graveyard has pup
        }
    }
}
