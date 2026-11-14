package arcanum.engine.sets.mom

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** MOM chunk 04: incubate, part 2: a counter for a permanent transforming into a Phyrexian, incubating X times for an X creature, a burn
 * spell that stops a creature target blocking, an Incubator chosen and transformed as a mode resolves, incubating once per creature exiled,
 * and a revealed-hand choice whose refusal incubates (three players). */
class MomChunk04IncubateScenariosTest {
    private val interrogator = Cards.invent("Compleat Interrogator", "{1}{W}", "Creature — Phyrexian Knight", """
        When this creature enters, incubate 2.
        Whenever a permanent you control transforms into a Phyrexian, put a +1/+1 counter on it.
    """.trimIndent(), "1/1")
    private val quickening = Cards.invent("Phyrexian Quickening", "{2}{W}", "Enchantment", """
        When this enchantment enters, incubate 4.
        Phyrexians you control have vigilance.
    """.trimIndent())
    private val exarch = Cards.invent("Brood Exarch", "{X}{X}{W}", "Creature — Phyrexian Cat Cleric", """
        When this creature enters, incubate 3 X times.
        {T}: Transform target Incubator token you control.
    """.trimIndent(), "1/2")
    private val barb = Cards.invent("Scorching Barb", "{2}{R}", "Sorcery",
        "Scorching Barb deals 2 damage to any target. If it's a creature, it can't block this turn. Incubate 1.")
    private val shatter = Cards.invent("Shatter the Portal", "{1}{W}", "Sorcery", """
        Choose one —
        • Destroy target nontoken artifact or enchantment an opponent controls. Incubate 2.
        • Incubate 2, then transform an Incubator token you control.
    """.trimIndent())
    private val dawnfall = Cards.invent("Dawnfall", "{3}{W}{W}", "Sorcery",
        "Exile all creatures. Incubate X, where X is the number of creatures exiled this way.")
    private val revelation = Cards.invent("Harrowing Revelation", "{1}{B}", "Sorcery",
        "Target opponent reveals their hand. You may choose a creature or battle card from it. If you do, that player discards that card. If you don't, incubate 3.")
    private val wolf = Cards.invent("Momentide Wolf", "{1}{G}", "Creature — Wolf", "", "2/2")
    private val wolfling = Cards.invent("Momentide Wolfling", "{G}", "Creature — Wolf", "", "1/1")
    private val wall = Cards.invent("Momentide Bulwark", "{1}{W}", "Creature — Wall", "", "0/4")
    private val beastform = "Momentide Beastform"
    private val wolflingFaces = Cards.inventFaces("Transform",
        arcanum.engine.cards.face("Momentide Pup", "{G}", "Creature — Human Werewolf", "{1}: Transform this creature.", "1/1", null, null),
        arcanum.engine.cards.face(beastform, null, "Creature — Werewolf", "", "3/3", null, "G"))

    private val incubator = "Incubator Token"
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private fun Scenario.faceName(name: String, index: Int = 0): String? =
        view()[cards(name, zone = Zone.Battlefield)[index].id]?.characteristics?.name
    private fun Scenario.incubators(): Int = state.battlefield.count { id -> cards(incubator).any { it.id == id } }

    // CR 701.27a: the Incubator transforms into a Phyrexian, so it gets a +1/+1 counter (CR 603.2: checked after the event, CR 603.10); before
    // transforming it is no Phyrexian and lacks the vigilance Phyrexians have (CR 613.1f); a permanent transforming into a non-Phyrexian
    // doesn't trigger it
    @Test
    fun aPermanentTransformingIntoAPhyrexianGetsACounter() = scenario {
        alice.battlefield(interrogator, wolflingFaces, *Array(7) { "Plains" })
        alice.hand(quickening)
        alice.casts(quickening)
        resolveAll()
        expect {
            incubator.counters(p1p1) shouldBe 4
            incubator lacksKeyword Keyword.Vigilance.kind
        }
        alice.activates(incubator)
        resolveAll()
        assertEquals("Phyrexian Token", faceName(incubator))
        expect {
            incubator.counters(p1p1) shouldBe 5
            incubator.power shouldBe 5
            incubator hasKeyword Keyword.Vigilance.kind
        }
        alice.activates(wolflingFaces)
        resolveAll()
        assertEquals(beastform, faceName(wolflingFaces))
        expect { wolflingFaces.counters(p1p1) shouldBe 0 }
    }

    // CR 107.3m: X in its enters trigger is the X it was cast with; CR 701.53a two times, each Incubator with three counters
    @Test
    fun theExarchIncubatesXTimes() = scenario {
        alice.battlefield(*Array(5) { "Plains" })
        alice.hand(exarch)
        alice.casts(exarch, x = 2)
        resolveAll()
        assertEquals(2, incubators())
        expect {
            card(incubator, 0).counters(p1p1) shouldBe 3
            card(incubator, 1).counters(p1p1) shouldBe 3
        }
    }

    // CR 608.2h: the target is a creature as the spell resolves, so it can't block this turn (CR 509.1a); CR 701.53a: one counter
    @Test
    fun theBarbStopsACreatureTargetBlocking() = scenario {
        alice.battlefield(wolf, "Mountain", "Mountain", "Mountain")
        alice.hand(barb)
        bob.battlefield(wolfling)
        bob.battlefield(wall)
        alice.casts(barb, target = wall)
        resolveAll()
        expect { incubator.counters(p1p1) shouldBe 1 }
        assertEquals(2, state.objOrThrow(cards(wall).single().id).damage)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wolf)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(wall to wolf) }
        bob.blocks(wolfling to wolf)
    }

    // CR 608.2h: a player target isn't a creature, so nothing is stopped; the damage and the Incubator still happen
    @Test
    fun theBarbAtAPlayerStillIncubates() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(barb)
        alice.casts(barb, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            incubator.counters(p1p1) shouldBe 1
        }
    }

    // CR 700.2, 608.2c: the second mode incubates, then transforms an Incubator chosen as it resolves: the one just created is a 2/2 Phyrexian
    @Test
    fun theSecondModeTransformsTheNewIncubator() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(shatter)
        alice.casts(shatter, modes = listOf(1))
        resolveAll()
        assertEquals("Phyrexian Token", faceName(incubator))
        expect {
            incubator.power shouldBe 2
            incubator.toughness shouldBe 2
        }
    }

    // CR 701.53a: X counts every creature this exile moved, tokens included (a token in exile ceases to exist only afterwards, CR 111.7,
    // 704.5d), each player's; the Incubator is made after the exile and stays
    @Test
    fun dawnfallIncubatesOncePerCreatureExiled() = scenario(players = 3) {
        alice.battlefield(wolf, *Array(7) { "Plains" })
        alice.hand(shatter, dawnfall)
        bob.battlefield(wolfling)
        carol.battlefield(wolflingFaces)
        alice.casts(shatter, modes = listOf(1))
        resolveAll()
        alice.casts(dawnfall)
        resolveAll()
        expect {
            alice.exile has wolf
            bob.exile has wolfling
            carol.exile has wolflingFaces
            incubator.counters(p1p1) shouldBe 4
        }
        assertEquals(incubator, faceName(incubator))
    }

    // CR 701.53a: with no creature exiled, X is 0 and the Incubator is still created, without counters
    @Test
    fun dawnfallOnAnEmptyBoardMakesABareIncubator() = scenario {
        alice.battlefield(*Array(5) { "Plains" })
        alice.hand(dawnfall)
        alice.casts(dawnfall)
        resolveAll()
        assertEquals(1, incubators())
        expect { incubator.counters(p1p1) shouldBe 0 }
    }

    // CR 701.20a: the target opponent reveals; choosing a creature card makes them discard it, and no Incubator is made (CR 608.2c); the
    // third player is untouched
    @Test
    fun theRevelationTakesAChosenCreatureCard() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(revelation)
        bob.hand(wolf, "Island")
        carol.hand(wolfling)
        alice.casts(revelation, target = "bob")
        resolveAll()
        assertTrue(pending is Decision.May, "may choose: $pending")
        alice.answers(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(wolf)
        resolveAll()
        expect {
            bob.graveyard has wolf
            bob.hand.size shouldBe 1
            carol.hand.size shouldBe 1
        }
        assertEquals(0, incubators())
    }

    // CR 608.2c: declining to choose, the player keeps their hand and you incubate 3
    @Test
    fun theRevelationIncubatesWhenNothingIsChosen() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(revelation)
        carol.hand(wolf, "Island")
        alice.casts(revelation, target = "carol")
        resolveAll()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            carol.hand.size shouldBe 2
            incubator.counters(p1p1) shouldBe 3
        }
    }

    // CR 608.2c: with no creature or battle card in the revealed hand none can be chosen, so you incubate 3
    @Test
    fun theRevelationIncubatesWhenTheHandHasNoCreature() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(revelation)
        bob.hand("Island")
        alice.casts(revelation, target = "bob")
        resolveAll()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.hand.size shouldBe 1
            incubator.counters(p1p1) shouldBe 3
        }
    }
}
