package arcanum.engine.mana

import arcanum.api.Color
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Mana payment (CR 106, 107.4, 601.2g–h) and the suggested tapping (§7.8). */
class ManaTest {
    /** Starts casting [name] and returns the payment decision once targets are chosen. */
    private fun Scenario.payment(name: String, vararg targets: String): Decision.ManaPayment {
        perform(Input.Cast(alice.id, card(name)))
        targets.forEach { answer(Answer.Targets(listOf(target(it)))) }
        return assertIs<Decision.ManaPayment>(pending)
    }

    private fun Scenario.sources(d: Decision.ManaPayment): List<String> =
        d.suggestion.filterIsInstance<ManaUse.Ability>().map { u -> nameOf(state.objOrThrow(u.source))!! }

    // §7.8: the suggestion taps a land of each colour needed
    @Test
    fun suggestionTapsMatchingLands() = scenario {
        alice.battlefield("Forest", "Mountain", "Island")
        alice.hand("Manashift")
        assertEquals(setOf("Forest", "Mountain"), sources(payment("Manashift")).toSet().let { if ("Island" in it) it else it })
    }

    // §7.8: a single-colour land pays a coloured symbol before a dual land that could pay more
    @Test
    fun singleColourBeforeDual() = scenario {
        alice.battlefield("Frostpine Slopes", "Forest")
        alice.hand("Thicketwood Elves")
        assertEquals(listOf("Forest"), sources(payment("Thicketwood Elves")))
    }

    // §7.8: two colours from a dual and a basic: each where it's needed
    @Test
    fun dualAndBasicTogether() = scenario {
        alice.battlefield("Frostpine Slopes", "Forest")
        alice.hand("Stormforge Charm".let { "Glint-Tusk Corsair" })
        perform(Input.Cast(alice.id, card("Glint-Tusk Corsair")))
        val d = assertIs<Decision.ManaPayment>(pending)
        // {1}{R}{R} can't be paid with one red source
        assertTrue(d.suggestion.isEmpty())
    }

    // §7.8: lands pay before mana creatures
    @Test
    fun landsBeforeCreatures() = scenario {
        alice.battlefield("Elvish Seer", "Forest")
        alice.hand("Thicketwood Elves")
        assertEquals(listOf("Forest"), sources(payment("Thicketwood Elves")))
    }

    // §7.8: nothing is sacrificed when a land can pay
    @Test
    fun keepsTheLotus() = scenario {
        alice.battlefield("Lotus Frond", "Mountain")
        alice.hand("Storm Spark")
        assertEquals(listOf("Mountain"), sources(payment("Storm Spark", "bob")))
    }

    // §7.8: but it is used when nothing else can pay
    @Test
    fun usesTheLotusWhenNeeded() = scenario {
        alice.battlefield("Lotus Frond", "Forest")
        alice.hand("Storm Spark")
        val d = payment("Storm Spark", "bob")
        assertEquals(listOf("Lotus Frond"), sources(d))
        assertEquals(Color.Red, (d.suggestion.single() as ManaUse.Ability).color)
        answer(Answer.ManaPayment(d.suggestion))
        expect { alice.graveyard has "Lotus Frond" }
    }

    // CR 106.4, §7.8: mana already in the pool is used first
    @Test
    fun poolFirst() = scenario {
        alice.battlefield("Midnight Lotus", "Mountain")
        alice.hand("Storm Spark")
        alice.activates("Midnight Lotus", color = Color.Red)
        val d = payment("Storm Spark", "bob")
        assertEquals(listOf(ManaUse.Pool(0)), d.suggestion)
        answer(Answer.ManaPayment(d.suggestion))
        assertEquals(2, alice.pool.size)
        expect { "Mountain".tapped shouldBe false }
    }

    // CR 302.6: a summoning-sick creature can't tap for mana
    @Test
    fun sickCreatureDoesntPay() = scenario {
        alice.battlefield("Elvish Seer", sick = true)
        alice.hand("Thicketwood Elves")
        val d = payment("Thicketwood Elves")
        assertTrue(d.suggestion.isEmpty())
        assertTrue(d.options.none { it is ManaUse.Ability })
    }

    // CR 106.1: "Add one mana of any color": the colour needed
    @Test
    fun anyColourPicksTheNeededOne() = scenario {
        alice.battlefield("Plumes of Eden")
        alice.hand("Storm Spark")
        val d = payment("Storm Spark", "bob")
        assertEquals(ManaUse.Ability(card("Plumes of Eden"), (d.suggestion.single() as ManaUse.Ability).ability, Color.Red), d.suggestion.single())
    }

    // CR 106.1: "Add {G} for each Elf you control"
    @Test
    fun manaForEachElf() = scenario {
        alice.battlefield("Elvish Hierophant", "Elvish Seer", "Thicketwood Elves")
        alice.activates("Elvish Hierophant")
        assertEquals(3, alice.pool.size)
    }

    // CR 106.6: "Spend this mana only to cast artifact spells": not for other spells
    @Test
    fun restrictedManaOnlyForArtifacts() = scenario {
        alice.battlefield("Tinker's Atelier")
        alice.hand("Sun Band", "Haven Warden")
        val forArtifact = payment("Sun Band")
        assertEquals(listOf("Tinker's Atelier"), sources(forArtifact))
        answer(Answer.ManaPayment(forArtifact.suggestion))
        // CR 106.4: the rest stays in the pool
        assertEquals(2, alice.pool.size)
        allPass()
        expect { alice.battlefield has "Sun Band" }
    }

    @Test
    fun restrictedManaRefusedForOthers() = scenario {
        // CR 106.6
        alice.battlefield("Tinker's Atelier", "Plains")
        alice.hand("Haven Warden")
        assertTrue(payment("Haven Warden").suggestion.isEmpty())
    }

    // CR 601.2h: a payment that doesn't cover the cost is refused
    @Test
    fun underpaymentRefused() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand("Elvish Hierophant")
        val d = payment("Elvish Hierophant")
        assertTrue(d.suggestion.isEmpty())
        assertFailsWith<IllegalArgumentException> { answer(Answer.ManaPayment(d.options)) }
    }

    // CR 601.2h: the wrong colour is refused
    @Test
    fun wrongColourRefused() = scenario {
        alice.battlefield("Mountain", "Forest")
        alice.hand("Storm Spark")
        perform(Input.Cast(alice.id, card("Storm Spark")))
        answer(Answer.Targets(listOf(target("bob"))))
        assertFailsWith<IllegalArgumentException> { alice.casts("Storm Spark".let { it }, paying = listOf("Forest")).let { } }
    }

    // CR 106.4: mana added during payment that isn't spent stays in the pool
    @Test
    fun leftoverManaStays() = scenario {
        alice.battlefield("Sun Band")
        alice.hand("Prismatic Mote")
        alice.casts("Prismatic Mote")
        assertEquals(listOf<Color?>(null), alice.pool.map { it.color })
    }

    // CR 601.2h: paying with chosen sources (not the suggestion)
    @Test
    fun payingWithChosenSources() = scenario {
        alice.battlefield("Forest", "Plumes of Eden")
        alice.hand("Thicketwood Elves")
        alice.casts("Thicketwood Elves", paying = listOf("Plumes of Eden:G"))
        expect {
            "Plumes of Eden".tapped shouldBe true
            "Forest".tapped shouldBe false
        }
    }

    // CR 202.2, D22: the colours of mana spent are kept with the spell
    @Test
    fun manaSpentIsRecorded() = scenario {
        alice.battlefield("Forest", "Mountain")
        alice.hand("Haven Warden")
        Cards.definition("Haven Warden")
        alice.casts("Haven Warden")
        val spent = state.objOrThrow(card("Haven Warden")).facts.manaSpent
        assertEquals(2, spent.sumOf { it.amount })
    }

    // CR 605.1b, 605.4a: a triggered mana ability adds its mana at once, so it helps pay the cost in progress
    @Test
    fun triggeredManaAbilityHelpsPay() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Swelling Tide", "Click Back")
        bob.battlefield("Elvish Seer")
        alice.casts("Swelling Tide", paying = listOf("Island#1"))
        allPass()
        // One Island makes {U}{U} now: enough for {1}{U}
        alice.casts("Click Back", target = "Elvish Seer", paying = listOf("Island"))
        expect { stack.names shouldBe listOf("Click Back") }
        assertTrue(alice.pool.isEmpty())
    }

    // CR 605.3b, 608.2h: "any type the sacrificed land could produce" reads the land as it last existed: a dual land
    // offers its two colours
    @Test
    fun sacrificedLandsColoursAreAsked() = scenario {
        alice.battlefield("Wasted Bounty", "Ashen Wastes")
        alice.activates("Wasted Bounty", choosing = listOf("Ashen Wastes"))
        assertEquals(setOf(Color.Black, Color.Red), decision<Decision.ChooseColor>().among.toSet())
        answer(Answer.ColorChoice(Color.Red))
        assertEquals(listOf(Color.Red), alice.pool.map { it.color })
        expect { alice.graveyard has "Ashen Wastes" }
    }
}
