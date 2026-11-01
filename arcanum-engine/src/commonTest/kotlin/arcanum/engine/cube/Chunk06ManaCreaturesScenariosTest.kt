package arcanum.engine.cube

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Chunk 6 in play: creatures that tap for mana (CR 302.6, 605), counting ones, Elf lords and Elf tappers. */
class Chunk06ManaCreaturesScenariosTest {
    /**
     * [creature] is cast with a Forest; it can't tap for mana the turn it comes (CR 302.6); one there since the turn
     * began pays a spell costing [color].
     */
    private fun manaDork(creature: String, color: Color?) = scenario {
        val cost = color?.let { "{${it.symbol}}" } ?: "{1}"
        alice.battlefield("Forest")
        alice.hand(creature)
        alice.battlefield(creature)
        alice.hand(pipSpell(cost))

        perform(Input.Cast(alice.id, card(creature, 0)))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        expect { card(creature, 0).zone shouldBe arcanum.api.Zone.Battlefield }
        // CR 302.6: the new one is summoning sick
        assertFailsWith<IllegalArgumentException> { perform(Input.Activate(alice.id, card(creature, 0), manaOrigin(creature, 0))) }

        alice.casts(pipSpell(cost))
        expect {
            card(creature, 1).tapped shouldBe true
            stack.names shouldBe listOf(pipSpell(cost))
        }
    }

    private fun arcanum.engine.scenario.Scenario.manaOrigin(name: String, index: Int) =
        view()[card(name, index)]!!.characteristics!!.abilities.first { it.ability is arcanum.api.Ability.Activated }.origin

    /** "{T}: Add {W}." */
    @Test
    fun saintsWayfarer() = manaDork("Saint's Wayfarer", Color.White)

    /** Snow; "{T}: Add {C}." Its mana is snow mana (CR 106.3, 107.4h). */
    @Test
    fun rimefrostDruid() = manaDork("Rimefrost Druid", null)

    /** "{T}: Add {G}." */
    @Test
    fun elvishSeer() = manaDork("Elvish Seer", Color.Green)

    @Test
    fun frosthornElves() = manaDork("Frosthorn Elves", Color.Green)

    @Test
    fun thicketwoodElves() = manaDork("Thicketwood Elves", Color.Green)

    /** "{T}: Add {B}. This creature deals 1 damage to you." */
    @Test
    fun umbralElves() = scenario {
        alice.battlefield("Umbral Elves")
        alice.hand(pipSpell("{B}"))

        alice.activates("Umbral Elves")
        alice.casts(pipSpell("{B}"))

        expect {
            alice.life shouldBe 19
            stack.names shouldBe listOf(pipSpell("{B}"))
        }
        assertTrue(applied.any { it is arcanum.engine.change.Change.Damage })
    }

    /** "When this creature enters, draw a card." and "{T}: Add {G}." */
    @Test
    fun canopyVisionary() = scenario {
        alice.hand("Canopy Visionary")
        alice.battlefield("Forest", "Forest", "Forest", "Canopy Visionary")
        alice.library("Island")
        alice.hand(pipSpell("{G}"))

        perform(Input.Cast(alice.id, card("Canopy Visionary", 0)))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        expect { stack.names shouldBe listOf("ability of Canopy Visionary") }
        allPass()
        expect { alice.hand has "Island" }

        alice.casts(pipSpell("{G}"))
        expect {
            card("Canopy Visionary", 1).tapped shouldBe true
            card("Canopy Visionary", 0).tapped shouldBe false
            stack.names shouldBe listOf(pipSpell("{G}"))
        }
    }

    /** "{T}: Add {G}." and "Put a -1/-1 counter on this creature: Untap this creature." It taps twice, shrinking. */
    @Test
    fun zealousDruid() = scenario {
        alice.battlefield("Zealous Druid")
        alice.hand(pipSpell("{G}{G}"))

        alice.activates("Zealous Druid")
        alice.activates("Zealous Druid", ability = 1)
        expect { stack.names shouldBe listOf("ability of Zealous Druid") }
        allPass()
        expect {
            "Zealous Druid".tapped shouldBe false
            "Zealous Druid".toughness shouldBe 1
            "Zealous Druid".counters(CounterKind.PowerToughness(-1, -1)) shouldBe 1
        }
        alice.activates("Zealous Druid")
        alice.casts(pipSpell("{G}{G}"))

        expect { stack.names shouldBe listOf(pipSpell("{G}{G}")) }
    }

    /** Flying; "{T}: Add one mana of any color." */
    @Test
    fun plumesOfEden() = scenario {
        alice.battlefield("Plumes of Eden")
        alice.hand(pipSpell("{U}"))

        alice.casts(pipSpell("{U}"))

        expect {
            "Plumes of Eden" hasKeyword Keyword.Flying
            "Plumes of Eden".tapped shouldBe true
            stack.names shouldBe listOf(pipSpell("{U}"))
        }
    }

    /** An artifact creature with flying and "{T}: Add one mana of any color." */
    @Test
    fun gearwingOfEden() = scenario {
        alice.battlefield("Gearwing of Eden")
        alice.hand(pipSpell("{R}"))

        alice.casts(pipSpell("{R}"), paying = listOf("Gearwing of Eden:R"))

        expect {
            "Gearwing of Eden" hasKeyword Keyword.Flying
            stack.names shouldBe listOf(pipSpell("{R}"))
        }
    }

    /**
     * Exalted (CR 702.83a): a creature attacking alone gets +1/+1 for each instance. [exarch] taps for each of
     * [colors].
     */
    private fun exarch(exarch: String, colors: List<Color>) = scenario {
        alice.battlefield(exarch, "Tinkered Mite")
        turn(1, step = TurnStep.BeginningOfCombat)

        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Tinkered Mite")
        expect { stack.names shouldBe listOf("ability of $exarch") }
        allPass()
        expect { "Tinkered Mite".power shouldBe 2 }
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 18 }

        // "{T}: Add {a}, {b}, or {c}."
        colors.forEach { c ->
            val inst = view()[card(exarch)]!!.characteristics!!.abilities.first { it.ability is arcanum.api.Ability.Activated }
            perform(Input.Activate(alice.id, card(exarch), inst.origin))
            assertEquals(colors, decision<Decision.ChooseColor>().among)
            perform(Input.Cancel)
        }
        alice.activates(exarch, color = colors.last())
        assertEquals(listOf<Color?>(colors.last()), alice.pool.map { it.color })
    }

    /** Exalted; "{T}: Add {B}, {R}, or {G}." */
    @Test
    fun baseExarch() = exarch("Base Exarch", listOf(Color.Black, Color.Red, Color.Green))

    /** Exalted; "{T}: Add {G}, {W}, or {U}." */
    @Test
    fun highbornExarch() = exarch("Highborn Exarch", listOf(Color.Green, Color.White, Color.Blue))

    /** "{T}, Discard your hand, Sacrifice this creature: Add three mana of any one color. Activate only as an instant." */
    @Test
    fun gemhideLion() = scenario {
        alice.battlefield("Gemhide Lion")
        alice.hand("Storm Spark", "Forest")

        alice.activates("Gemhide Lion", color = Color.Red)

        assertEquals(List(3) { Color.Red }, alice.pool.map { it.color })
        expect {
            alice.hand.isEmpty()
            alice.graveyard has "Gemhide Lion"
            alice.graveyard has "Storm Spark"
        }
    }

    /** "{T}: Add {G} for each creature you control." */
    @Test
    fun dreamcircleElder() = scenario {
        alice.battlefield("Dreamcircle Elder", "Tinkered Mite", "Elvish Seer")
        bob.battlefield("Thicketwood Elves")

        alice.activates("Dreamcircle Elder")

        assertEquals(List(3) { Color.Green }, alice.pool.map { it.color })
    }

    /** "{T}: Add {G} for each Elf on the battlefield." Opponents' Elves count too. */
    @Test
    fun acolyteOfTheGroveMother() = scenario {
        alice.battlefield("Acolyte of the Grove Mother", "Tinkered Mite")
        bob.battlefield("Thicketwood Elves", "Umbral Elves")

        alice.activates("Acolyte of the Grove Mother")

        assertEquals(List(3) { Color.Green }, alice.pool.map { it.color })
    }

    /** "{T}: Add {G} for each Forest you control." A dual land with the Forest type counts (CR 305.6). */
    @Test
    fun tavrosThicketEmissary() = scenario {
        alice.battlefield("Tavros, Thicket Emissary", "Forest", "Frostpine Slopes", "Island")
        bob.battlefield("Forest")
        alice.hand(pipSpell("{G}{G}"))

        alice.activates("Tavros, Thicket Emissary")
        assertEquals(List(2) { Color.Green }, alice.pool.map { it.color })
        alice.casts(pipSpell("{G}{G}"))

        expect {
            stack.names shouldBe listOf(pipSpell("{G}{G}"))
            "Forest".tapped shouldBe false
        }
    }

    /** "Other Elf creatures you control get +1/+1." and "{T}: Add {G} for each Elf you control." */
    @Test
    fun elvishHierophant() = scenario {
        alice.battlefield("Elvish Hierophant", "Elvish Seer", "Tinkered Mite")
        bob.battlefield("Thicketwood Elves")

        expect {
            // CR 613.4c
            "Elvish Seer".power shouldBe 2
            "Elvish Hierophant".power shouldBe 2
            "Tinkered Mite".power shouldBe 1
            "Thicketwood Elves".power shouldBe 1
        }
        alice.activates("Elvish Hierophant")
        assertEquals(List(2) { Color.Green }, alice.pool.map { it.color })
    }

    /** "Tap two untapped Elves you control: Add one mana of any color." Summoning-sick Elves may be tapped (CR 302.6). */
    @Test
    fun barkwoodPathfinders() = scenario {
        alice.battlefield("Barkwood Pathfinders", sick = true)
        alice.battlefield("Elvish Seer", sick = true)
        alice.hand(pipSpell("{U}"))

        alice.activates("Barkwood Pathfinders", choosing = listOf("Barkwood Pathfinders", "Elvish Seer"), color = Color.Blue)
        alice.casts(pipSpell("{U}"))

        expect {
            "Barkwood Pathfinders".tapped shouldBe true
            "Elvish Seer".tapped shouldBe true
            stack.names shouldBe listOf(pipSpell("{U}"))
        }
    }

    /** Morph {G} (CR 702.37): cast face down for {3} as a 2/2, turned face up for {G}. */
    @Test
    fun barkwoodPathfindersMorph() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand("Barkwood Pathfinders")
        val morph = view()[card("Barkwood Pathfinders")]!!.characteristics!!.abilities
            .first { (it.ability as? arcanum.api.Ability.Static)?.mechanics?.any { m -> m is arcanum.api.Mechanic.AlternativeCost } == true }

        alice.casts("Barkwood Pathfinders", way = arcanum.engine.input.CastWay(alternative = morph.origin))
        allPass()
        expect {
            "Barkwood Pathfinders".power shouldBe 2
            "Barkwood Pathfinders".subtypes shouldBe emptyList()
        }
        // CR 702.37e: turning it face up is a special action
        val turnUp = arcanum.engine.Engine.options(state, alice.id).filterIsInstance<Input.Activate>()
            .single { it.source == card("Barkwood Pathfinders") }
        perform(turnUp)
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))

        expect {
            "Barkwood Pathfinders".power shouldBe 1
            "Forest".tapped shouldBe true
            card("Forest", 3).tapped shouldBe true
        }
    }

    /** "Tap three untapped Elves you control: Add {G}{G}{G}." */
    @Test
    fun lineageDruid() = scenario {
        alice.battlefield("Lineage Druid", "Elvish Seer", "Umbral Elves", "Tinkered Mite")
        alice.hand(pipSpell("{G}{G}{G}"))

        alice.activates("Lineage Druid", choosing = listOf("Lineage Druid", "Elvish Seer", "Umbral Elves"))
        alice.casts(pipSpell("{G}{G}{G}"))

        expect {
            "Umbral Elves".tapped shouldBe true
            "Tinkered Mite".tapped shouldBe false
            stack.names shouldBe listOf(pipSpell("{G}{G}{G}"))
        }
    }
}
