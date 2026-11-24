package arcanum.engine.sets.neo

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Chunk 15 of NEO: a static that gives modified creatures trample and a combat-damage search by a modified creature, and a destroy spell that needs a
 * creature dealt damage this turn. */
class NeoChunk15ModifiedAndDamagedScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val kodama = Cards.invent("Kodama of the Far Tree", "{2}{G}", "Legendary Creature — Spirit", """
        Reach
        Modified creatures you control have trample. (Equipment, Auras you control, and counters are modifications.)
        Whenever a modified creature you control deals combat damage to a player, search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
    """.trimIndent(), "3/3")
    private val bear = Cards.invent("Hakkaido Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Fenwick Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val spark = Cards.invent("Ember Spark", "{R}", "Instant", "Ember Spark deals 1 damage to target creature.")
    private val broken = Cards.invent("You Are Already Broken", "{B}", "Instant", """
        Destroy target creature that was dealt damage this turn.
        Draw a card.
    """.trimIndent())

    // CR 700.9, 613.1f: only the creature with a counter (modified) has trample; the unmodified one doesn't
    @Test
    fun modifiedCreaturesHaveTrampleAndOthersDoNot() = scenario {
        alice.battlefield(kodama, ox)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        expect {
            bear hasKeyword Keyword.Trample.kind
            ox lacksKeyword Keyword.Trample.kind
        }
    }

    // CR 700.9, 510.2, 603.2: a modified creature's combat damage to a player searches a basic land onto the battlefield tapped
    @Test
    fun modifiedCreatureHittingAPlayerFetchesATappedBasicLand() = scenario {
        alice.battlefield(kodama)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.library("Forest")
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        if (pending != null) alice.chooses("Forest")
        resolveAll()
        expect {
            "Forest".zone shouldBe Zone.Battlefield
            "Forest".tapped shouldBe true
        }
    }

    // CR 700.9: an unmodified creature's combat damage doesn't search
    @Test
    fun unmodifiedCreatureHittingAPlayerFetchesNothing() = scenario {
        alice.battlefield(kodama, ox)
        alice.library("Forest")
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(ox)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { "Forest".zone shouldBe Zone.Library }
    }

    // CR 120.1, 115.1: the creature must have been dealt damage this turn to be targeted; then it is destroyed and its controller's opponent draws
    @Test
    fun destroyNeedsACreatureDealtDamageThisTurn() = scenario {
        alice.hand(spark, broken)
        alice.battlefield("Mountain", "Swamp")
        bob.battlefield(bear, ox)
        alice.library("Island")
        assertFailsWith<IllegalArgumentException> { alice.casts(broken, target = ox) }
        alice.casts(spark, target = bear)
        resolveAll()
        alice.casts(broken, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            ox.zone shouldBe Zone.Battlefield
            "Island".zone shouldBe Zone.Hand
        }
    }
}
