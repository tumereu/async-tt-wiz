package arcanum.engine.sets.blb

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Bloomburrow's graveyard casting and spell wordings, played with invented cards of the parser's chunks 09 and 10. */
class BlbSpellsScenariosTest {
    private val feast = Cards.invent("Feast of Cinders", "{4}{R}", "Enchantment", """
        During your turn, you may cast instant and sorcery spells from your graveyard by paying 1 life in addition to their other costs.
        If a card or token would be put into your graveyard from anywhere, exile it instead.
    """)
    private val spark = Cards.invent("Twin Spark", "{1}{R}", "Instant", "Twin Spark deals 1 damage to any target.")
    private val downgust = Cards.invent("Grim Downgust", "{3}{U}", "Instant", """
        This spell costs {1} less to cast if it targets an attacking or tapped creature.
        Target creature's owner puts it on their choice of the top or bottom of their library.
    """)
    private val flee = Cards.invent("Flee Together", "{1}{U}", "Instant",
        "Choose two target creatures controlled by different players. Return those creatures to their owners' hands.")
    private val chomp = Cards.invent("Frenzied Chomp", "{1}{R}", "Instant",
        "Target creature you control gets +1/+0 until end of turn. Then it deals damage equal to its power to target creature you don't control.")
    private val vole = Cards.invent("Mossback Vole", "{2}{G}", "Creature — Mouse", "", "3/3")
    private val fledgling = Cards.invent("Skylark Fledgling", "{W}", "Creature — Bird", "Flying", "1/1")
    private val elder = Cards.invent("Burrow Elder", "{1}{W}", "Creature — Rabbit Cleric", "{3}{W}: Creatures you control get +1/+1 until end of turn.", "2/2")

    /** The spell is cast from the graveyard for 1 life more, and then exiled instead of going back. */
    // CR 601.2a, 601.2f: the permission and its added cost; CR 614.1a: the graveyard replacement
    @Test
    fun graveyardSpellCostsLifeAndIsExiled() = scenario {
        alice.battlefield(feast, "Mountain", "Mountain")
        alice.graveyard(spark)

        alice.casts(spark, target = "bob")
        expect { alice.life shouldBe 19 }
        allPass()
        expect {
            bob.life shouldBe 19
            card(spark).zone shouldBe Zone.Exile
        }
    }

    // CR 601.3a: "during your turn"
    @Test
    fun noGraveyardCastingOnAnotherPlayersTurn() = scenario {
        alice.battlefield(feast, "Mountain", "Mountain")
        alice.graveyard(spark)
        turn(1, active = bob, priority = alice)

        assertFailsWith<IllegalArgumentException> { alice.casts(spark, target = "bob") }
    }

    /** Targeting a tapped creature makes it cost {2}{U}; its owner chooses the bottom of their library. */
    // CR 601.2f: the total cost is determined after targets are chosen
    @Test
    fun cheaperForATappedTarget() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(downgust)
        bob.battlefield(vole, tapped = true)

        alice.casts(downgust, target = vole)
        expect { (0..3).count { card("Island", it).tapped } shouldBe 3 }
        allPass()
        bob.answers(Answer.Option("bottom"))
        expect { card(vole).zone shouldBe Zone.Library }
    }

    @Test
    fun fullCostForAnUntappedTarget() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(downgust)
        bob.battlefield(vole)

        alice.casts(downgust, target = vole)
        expect { (0..3).count { card("Island", it).tapped } shouldBe 4 }
    }

    /** Two creatures of one player can't both be chosen; one of each player's can. */
    // CR 601.2c
    @Test
    fun targetsControlledByDifferentPlayers() = scenario {
        alice.battlefield(elder, "Island", "Island")
        alice.hand(flee)
        bob.battlefield(vole, fledgling)

        assertFailsWith<IllegalArgumentException> { alice.casts(flee, targets = listOf(vole, fledgling)) }
    }

    @Test
    fun returnsOneCreatureOfEachPlayer() = scenario {
        alice.battlefield(elder, "Island", "Island")
        alice.hand(flee)
        bob.battlefield(vole, fledgling)

        alice.casts(flee, targets = listOf(elder, vole))
        allPass()
        expect {
            card(elder).zone shouldBe Zone.Hand
            card(vole).zone shouldBe Zone.Hand
            card(fledgling).zone shouldBe Zone.Battlefield
        }
    }

    /** The pumped creature deals damage equal to its new power. */
    // CR 608.2c: the instructions are followed in order
    @Test
    fun pumpedCreatureDealsItsPower() = scenario {
        alice.battlefield(elder, "Mountain", "Mountain")
        alice.hand(chomp)
        bob.battlefield(vole)

        alice.casts(chomp, targets = listOf(elder, vole))
        allPass()
        expect {
            elder.power shouldBe 3
            card(vole).zone shouldBe Zone.Graveyard
        }
    }
}
