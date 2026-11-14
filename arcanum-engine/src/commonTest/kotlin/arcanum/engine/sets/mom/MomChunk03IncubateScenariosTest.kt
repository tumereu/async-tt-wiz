package arcanum.engine.sets.mom

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.backFaceUp
import arcanum.engine.sets.fin.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MOM chunk 03: incubate, part 1: the double-faced Incubator token and its counters, transforming it, X from a dying creature's last power
 * and from a cast spell, incubating twice, a draw once each turn on transforming or entering transformed, a Praetor's tax on an opponent's
 * damage with three players, and its Saga's five incubations and the board wipe that spares artifacts, lands and Phyrexians. */
class MomChunk03IncubateScenariosTest {
    private val brute = Cards.invent("Transmuter Brute", "{3}{G}", "Creature — Phyrexian Beast", "When this creature enters, incubate 5.", "0/1")
    private val imp = Cards.invent("Smelter Imp", "{1}{R}", "Creature — Phyrexian Gremlin", """
        {1}{R}: This creature gets +1/+0 until end of turn.
        When this creature dies, incubate X, where X is its power.
    """.trimIndent(), "1/2")
    private val reefshark = Cards.invent("Steel Brood Reefshark", "{2}{U}", "Creature — Phyrexian Shark", """
        Flying
        Whenever you cast a noncreature spell, incubate X, where X is that spell's mana value.
    """.trimIndent(), "2/4")
    private val blight = Cards.invent("Blight of Spirehold", "{4}{U}", "Enchantment", """
        When this enchantment enters, incubate 4.
        Whenever a permanent you control transforms or a permanent you control enters transformed, you may draw a card. Do this only once each turn.
    """.trimIndent())
    private val daybreak = Cards.invent("Gleaming Daybreak", "{2}{G}{G}", "Sorcery", "Incubate X twice, where X is the number of lands you control.")
    private val vessa = Cards.inventFaces("Transform",
        face("Vessa, Grand Matron", "{2}{W}{W}", "Legendary Creature — Phyrexian Praetor", """
            Vigilance
            Whenever a source an opponent controls deals damage to you or a permanent you control, that source's controller loses 2 life unless they pay {1}.
            {2}{W}, Sacrifice three other creatures: Exile Vessa, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
        """.trimIndent(), "3/5", null, null),
        face("The Gilded Engravings", null, "Enchantment — Saga", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I — Incubate 2 five times, then transform all Incubator tokens you control.
            II — Creatures you control get +1/+1 and gain double strike until end of turn.
            III — Destroy all other permanents except for artifacts, lands, and Phyrexians. Exile this Saga, then return it to the battlefield (front face up).
        """.trimIndent(), null, null, "W"))
    private val grizzly = Cards.invent("Momentide Grizzly", "{1}{G}", "Creature — Bear", "", "2/2")
    private val bolt = Cards.invent("Momentide Bolt", "{R}", "Instant", "Momentide Bolt deals 3 damage to any target.")
    private val spark = Cards.invent("Momentide Spark", "{R}", "Instant", "Draw a card.")
    private val ogre = Cards.invent("Momentide Brute", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val relic = Cards.invent("Momentide Relic", "{2}", "Artifact", "")

    private val incubator = "Incubator Token"
    private fun Scenario.libraries(count: Int = 20) = players.forEach { p -> p.library(*Array(count) { "Island" }) }
    private fun Scenario.faceName(name: String, index: Int = 0): String? =
        view()[cards(name, zone = Zone.Battlefield)[index].id]?.characteristics?.name
    private fun Scenario.incubators(): Int = state.battlefield.count { id -> cards(incubator).any { it.id == id } }

    // CR 701.53a: the token enters with N +1/+1 counters; CR 111.10i: its front face is a colorless Incubator artifact, not a creature;
    // CR 701.27a: "{2}: Transform this token." turns it into a 0/0 colorless Phyrexian artifact creature named Phyrexian Token, its
    // counters kept (CR 122.2, 711.8)
    @Test
    fun theIncubatorEntersWithCountersAndTransformsIntoAPhyrexian() = scenario {
        alice.battlefield(*Array(6) { "Forest" })
        alice.hand(brute)
        alice.casts(brute)
        resolveAll()
        expect {
            incubator.types shouldBe listOf(CardType.Artifact)
            incubator.subtypes shouldBe listOf(Subtype("Incubator"))
            incubator.colors shouldBe emptyList()
            incubator.power shouldBe null
            incubator.counters(CounterKind.PowerToughness(1, 1)) shouldBe 5
        }
        assertEquals("Incubator Token", faceName(incubator))
        alice.activates(incubator)
        resolveAll()
        assertEquals("Phyrexian Token", faceName(incubator))
        expect {
            incubator.types shouldBe listOf(CardType.Artifact, CardType.Creature)
            incubator.subtypes shouldBe listOf(Subtype("Phyrexian"))
            incubator.colors shouldBe emptyList()
            incubator.power shouldBe 5
            incubator.toughness shouldBe 5
        }
    }

    // CR 603.10a: X is the creature's power as it last existed on the battlefield, its pump included (CR 611.2c)
    @Test
    fun theImpIncubatesItsLastPower() = scenario {
        alice.battlefield(imp, "Mountain", "Mountain")
        bob.battlefield("Mountain")
        bob.hand(bolt)
        alice.activates(imp)
        resolveAll()
        expect { imp.power shouldBe 2 }
        alice.passes()
        bob.casts(bolt, target = imp)
        resolveAll()
        expect {
            alice.graveyard has imp
            incubator.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
        }
    }

    // CR 603.4, 608.2h: X is the cast spell's mana value; a creature spell doesn't trigger it
    @Test
    fun theReefsharkIncubatesTheSpellsManaValue() = scenario {
        alice.battlefield(reefshark, "Mountain", "Forest", "Forest")
        alice.hand(spark, grizzly)
        libraries()
        alice.casts(spark)
        resolveAll()
        expect { incubator.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
        alice.casts(grizzly)
        resolveAll()
        assertEquals(1, incubators())
    }

    // CR 107.3c, 608.2h: X is counted as it resolves; incubating twice makes two tokens with X counters each. CR 701.27a: each transforms;
    // CR 603.2h: declining the draw leaves it for a later trigger
    @Test
    fun daybreakIncubatesTwiceAndTheBlightDrawsOnceATurn() = scenario {
        alice.battlefield(blight, *Array(10) { "Forest" })
        alice.hand(daybreak)
        libraries()
        alice.casts(daybreak)
        resolveAll()
        assertEquals(2, incubators())
        expect {
            card(incubator, 0).counters(CounterKind.PowerToughness(1, 1)) shouldBe 10
            card(incubator, 1).counters(CounterKind.PowerToughness(1, 1)) shouldBe 10
        }
        alice.activates(incubator)
        resolveAll()
        assertTrue(pending is Decision.May, "may draw: $pending")
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect { alice.hand.size shouldBe 0 }
        // the second Incubator: the first is a Phyrexian now, without the ability
        val second = cards(incubator).first { o -> view()[o.id]!!.characteristics!!.abilities.any { it.ability is arcanum.api.Ability.Activated } }.id
        val ability = view()[second]!!.characteristics!!.abilities.first { it.ability is arcanum.api.Ability.Activated }
        perform(arcanum.engine.input.Input.Activate(alice.id, second, ability.origin))
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 712.14a: Vessa returns transformed, so the Blight's trigger sees a permanent entering transformed; CR 714.3a: its Saga enters with a
    // lore counter and chapter I's five transforms trigger it too; CR 603.2h: declined, each asks again
    @Test
    fun aPermanentEnteringTransformedDrawsForTheBlight() = scenario {
        alice.battlefield(blight, vessa, grizzly, ogre, reefshark, "Plains", "Plains", "Plains")
        libraries()
        alice.activates(vessa, choosing = listOf(grizzly, ogre, reefshark))
        resolveAll()
        // declining each: one for Vessa entering transformed, five for chapter I's Incubators transforming
        var asked = 0
        while (pending is Decision.May) { asked++; alice.answers(Answer.YesNo(false)); resolveAll() }
        assertEquals("The Gilded Engravings", faceName(vessa))
        assertEquals(6, asked)
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 120.3, 603.2: a source an opponent controls dealing damage to you; CR 118.12a: that source's controller may pay {1}; declining, they
    // lose 2 life; the other opponent is untouched (three players)
    @Test
    fun vessaTaxesTheOpponentWhoseSourceHitYou() = scenario(players = 3) {
        alice.battlefield(vessa)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(bolt)
        turn(number = 2, active = bob)
        bob.casts(bolt, target = "alice")
        resolveAll()
        assertEquals(bob.id, (pending as Decision.May).player)
        bob.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.life shouldBe 17
            bob.life shouldBe 18
            carol.life shouldBe 20
        }
    }

    // CR 118.12a: paying {1} spares the life; damage to a permanent you control triggers it too
    @Test
    fun vessaTaxIsPaidForDamageToYourCreature() = scenario(players = 3) {
        alice.battlefield(vessa, grizzly)
        carol.battlefield("Mountain", "Mountain")
        carol.hand(bolt)
        turn(number = 3, active = carol)
        carol.casts(bolt, target = grizzly)
        resolveAll()
        carol.answers(Answer.YesNo(true))
        (pending as Decision.ManaPayment).let { answer(Answer.ManaPayment(it.suggestion)) }
        resolveAll()
        assertEquals(null, pending)
        expect {
            alice.graveyard has grizzly
            carol.life shouldBe 20
            bob.life shouldBe 20
            carol.battlefield.size shouldBe 2
        }
        assertTrue(state.battlefield.filter { cards("Mountain", owner = carol).any { m -> m.id == it } }.all { state.objOrThrow(it).tapped })
    }

    // CR 714.2b: chapter I after the draw step; CR 701.53a five times, then CR 701.27a: every Incubator becomes a 2/2 Phyrexian
    @Test
    fun theEngravingsMakeFiveTwoTwoPhyrexians() = scenario {
        alice.battlefield(vessa, counters = listOf(CounterKind.Lore to 0))
        backFaceUp(vessa)
        libraries()
        turn(step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        assertEquals(5, incubators())
        repeat(5) { i ->
            assertEquals("Phyrexian Token", faceName(incubator, i))
            expect { card(incubator, i).power shouldBe 2 }
        }
    }

    // CR 701.8a: chapter III destroys every other permanent but artifacts, lands and Phyrexians, any player's; CR 712.14: the Saga returns front
    // face up as a new object (CR 400.7)
    @Test
    fun theEngravingsSpareArtifactsLandsAndPhyrexians() = scenario {
        alice.battlefield(vessa, counters = listOf(CounterKind.Lore to 2))
        backFaceUp(vessa)
        alice.battlefield(grizzly, "Plains")
        bob.battlefield(imp, relic, blight, "Mountain")
        libraries()
        turn(step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        expect {
            alice.graveyard has grizzly
            bob.graveyard has blight
            imp.zone shouldBe Zone.Battlefield
            relic.zone shouldBe Zone.Battlefield
            "Plains".zone shouldBe Zone.Battlefield
            "Mountain".zone shouldBe Zone.Battlefield
            vessa.zone shouldBe Zone.Battlefield
        }
        assertEquals("Vessa, Grand Matron", faceName(vessa))
    }
}
