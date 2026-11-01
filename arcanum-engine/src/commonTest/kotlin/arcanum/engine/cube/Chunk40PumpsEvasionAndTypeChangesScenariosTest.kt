package arcanum.engine.cube

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The cube's chunk 40 (pumps, evasion and type changes), played as a player would. */
class Chunk40PumpsEvasionAndTypeChangesScenariosTest {
    private val bear = Cards.invent("Mossback Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Hulking Mudogre", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    /** Chooses targets when asked; a slot with only as many legal targets as it needs is filled without asking (P5). */
    private fun Scenario.targets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    private fun Scenario.yes() = answer(Answer.YesNo(true))

    /** Bob makes Alice discard [card] with Crow's Larceny; madness exiles it and its trigger casts it for [cost] (CR
     * 702.35a): free here. */
    private fun Scenario.discardForMadness(card: String, cost: String) {
        bob.castsWithoutCosts("Crow's Larceny", target = "alice")
        resolve()
        // CR 702.35a: discarded into exile instead; the madness trigger
        expect {
            alice.exile has card
            stack.names shouldBe listOf("ability of $card")
        }
        resolve()
        yes()
        expect { stack.names shouldBe listOf(card) }
        resolve()
        expect { alice.battlefield has card }
    }

    /** {1}{G}: +2/+2 until end of turn, once each turn; madness {0}. */
    @Test
    fun sunningMudlizard() = scenario {
        alice.battlefield("Sunning Mudlizard", "Forest", "Forest", "Forest", "Forest")

        alice.activates("Sunning Mudlizard")
        allPass()
        expect {
            "Sunning Mudlizard".power shouldBe 3
            "Sunning Mudlizard".toughness shouldBe 3
        }
        // CR 602.5b: once each turn
        assertFailsWith<IllegalArgumentException> { alice.activates("Sunning Mudlizard") }
    }

    @Test
    fun sunningMudlizardMadness() = scenario {
        alice.hand("Sunning Mudlizard")
        bob.hand("Crow's Larceny")
        discardForMadness("Sunning Mudlizard", "{0}")
    }

    /** {R}: +2/+0 until end of turn, once each turn; madness {0}. */
    @Test
    fun scorchingMudlizard() = scenario {
        alice.battlefield("Scorching Mudlizard", "Mountain", "Mountain")

        alice.activates("Scorching Mudlizard")
        allPass()
        expect {
            "Scorching Mudlizard".power shouldBe 3
            "Scorching Mudlizard".toughness shouldBe 1
        }
        assertFailsWith<IllegalArgumentException> { alice.activates("Scorching Mudlizard") }
    }

    @Test
    fun scorchingMudlizardMadness() = scenario {
        alice.hand("Scorching Mudlizard")
        bob.hand("Crow's Larceny")
        discardForMadness("Scorching Mudlizard", "{0}")
    }

    /** Other Elves entering make a 1/1 Elf Warrior, once each turn; {5}{G}{G}: Elves get +2/+2 and deathtouch. */
    @Test
    fun elvishDrillmaster() = scenario {
        alice.battlefield("Elvish Drillmaster", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand("Elvish Seer", "Thicketwood Elves")

        alice.casts("Elvish Seer")
        allPass()
        allPass()
        expect { alice.battlefield has "Elf Warrior Token" }
        // CR 603.2h: only once each turn
        alice.casts("Thicketwood Elves")
        allPass()
        expect {
            stack.isEmpty()
            alice.battlefield.names.count { it == "Elf Warrior Token" } shouldBe 1
        }

        alice.activates("Elvish Drillmaster")
        allPass()
        expect {
            "Elvish Seer".power shouldBe 3
            "Elvish Drillmaster".power shouldBe 4
            "Elf Warrior Token".toughness shouldBe 3
            "Elvish Seer" hasKeyword Keyword.Deathtouch
        }
    }

    /** Forestwalk; tokens you'd create come with as many 1/1 Squirrels; {B}, sacrifice X Squirrels: +X/-X. */
    @Test
    fun nutcrackerSquirrelMarshal() = scenario {
        alice.battlefield("Nutcracker, Squirrel Marshal", "Tombcracker", "Swamp", "Swamp", "Swamp")
        alice.hand("Forest")
        bob.battlefield(ogre)

        // CR 614.1a: creating a Zombie creates a Squirrel too
        alice.activates("Tombcracker", choosing = listOf("Forest"))
        allPass()
        expect {
            alice.battlefield has "Zombie Token"
            alice.battlefield has "Squirrel Token"
        }

        alice.activates("Nutcracker, Squirrel Marshal", x = 1, target = ogre, choosing = listOf("Squirrel Token"))
        allPass()
        expect {
            alice.battlefield lacks "Squirrel Token"
            ogre.power shouldBe 5
            ogre.toughness shouldBe 3
        }
    }

    /** Forestwalk: unblockable while the defending player controls a Forest. */
    @Test
    fun nutcrackerForestwalk() = scenario {
        alice.battlefield("Nutcracker, Squirrel Marshal")
        bob.battlefield(bear, "Forest")

        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Nutcracker, Squirrel Marshal")
        allPass()
        // CR 702.14c: no legal block, so no declaration is awaited and damage follows
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 17 }
    }

    /** Can't be countered; your green spells can't be countered; {4}{G}{G}: your Elves become 5/5 Dinosaurs. */
    @Test
    fun saurianHerder() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Saurian Herder", bear)
        bob.battlefield("Island", "Island", "Island", "Island")
        bob.hand("Denial", "Recall Lapse")

        alice.casts("Saurian Herder")
        alice.passes()
        // CR 101.2: Denial resolves, but the Herder isn't countered
        bob.casts("Denial", target = "Saurian Herder")
        allPass()
        expect { stack.names shouldBe listOf("Saurian Herder") }
        allPass()
        expect { alice.battlefield has "Saurian Herder" }

        alice.casts(bear)
        alice.passes()
        bob.casts("Recall Lapse", target = bear)
        allPass()
        allPass()
        expect { alice.battlefield has bear }
    }

    @Test
    fun saurianHerderMakesDinosaurs() = scenario {
        alice.battlefield("Saurian Herder", "Elvish Seer", bear, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")

        alice.activates("Saurian Herder")
        allPass()
        expect {
            "Elvish Seer".power shouldBe 5
            "Elvish Seer".toughness shouldBe 5
            "Elvish Seer".subtypes shouldBe listOf(Subtype("Elf"), Subtype("Druid"), Subtype("Dinosaur"))
            "Saurian Herder".power shouldBe 5
            bear.power shouldBe 2
        }
    }

    /** Changeling; {X}: your creatures have base power and toughness X/X and every creature type until end of turn. */
    @Test
    fun glassPolymorph() = scenario {
        alice.battlefield("Glass Polymorph", bear, "Plains", "Plains", "Plains")
        bob.battlefield(ogre)

        alice.activates("Glass Polymorph", x = 3)
        allPass()
        expect {
            bear.power shouldBe 3
            "Glass Polymorph".toughness shouldBe 3
            ogre.power shouldBe 4
            bear.view.characteristics!!.hasSubtype(Subtype("Elf")) shouldBe true
            ogre.view.characteristics!!.hasSubtype(Subtype("Elf")) shouldBe false
        }
    }

    /** Haste; {1}: can't be blocked this turn except by creatures with haste; {2}, {T}, sacrifice it: gain 3 life. */
    @Test
    fun cookieBrawler() = scenario {
        alice.battlefield("Cookie Brawler", sick = true)
        alice.battlefield("Plains")
        bob.battlefield(bear, "Gearwheel Courier")

        alice.activates("Cookie Brawler")
        allPass()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Cookie Brawler")
        allPass()
        assertFailsWith<IllegalArgumentException> { bob.blocks(bear to "Cookie Brawler") }
        bob.blocks("Gearwheel Courier" to "Cookie Brawler")
    }

    @Test
    fun cookieBrawlerSacrifice() = scenario {
        alice.battlefield("Cookie Brawler", "Plains", "Plains")

        alice.activates("Cookie Brawler", ability = 1)
        allPass()
        expect {
            alice.life shouldBe 23
            alice.graveyard has "Cookie Brawler"
        }
    }

    /** {T}: another creature you control gains protection from colorless or a colour of your choice. */
    @Test
    fun bestowerOfSigils() = scenario {
        alice.battlefield("Bestower of Sigils", bear)
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")

        alice.activates("Bestower of Sigils", target = bear)
        allPass()
        answer(Answer.Option("a color"))
        answer(Answer.ColorChoice(Color.Red))
        alice.passes()
        // CR 702.16b: it can't be the target of red spells
        assertFailsWith<IllegalArgumentException> { bob.casts("Storm Spark", target = bear) }
    }

    /** Modular 1; your modular abilities put one more counter; {R}: destroy your artifact; {W}: flying. */
    @Test
    fun vizzikTheGlintwasp() = scenario {
        alice.battlefield("Vizzik, the Glintwasp", "Scrapfed Devourer", counters = listOf(p1p1 to 1))
        alice.battlefield("Mountain", "Plains")

        alice.activates("Vizzik, the Glintwasp", target = "Scrapfed Devourer")
        allPass()
        expect { alice.graveyard has "Scrapfed Devourer" }
        allPass()
        yes()
        // CR 702.43a, 614.1a: one counter from modular, one more from Vizzik
        expect { "Vizzik, the Glintwasp".counters(p1p1) shouldBe 3 }

        alice.activates("Vizzik, the Glintwasp", ability = 1)
        allPass()
        expect { "Vizzik, the Glintwasp" hasKeyword Keyword.Flying }
    }

    /** {T}: untap target Forest. */
    @Test
    fun canopyTender() = scenario {
        alice.battlefield("Canopy Tender")
        alice.battlefield("Forest", tapped = true)

        alice.activates("Canopy Tender", target = "Forest")
        allPass()
        expect {
            "Forest".tapped shouldBe false
            "Canopy Tender".tapped shouldBe true
        }
    }

    /** {T}: you may tap or untap another permanent. */
    @Test
    fun fateknitter() = scenario {
        alice.battlefield("Fateknitter")
        bob.battlefield(ogre)

        alice.activates("Fateknitter", target = ogre)
        allPass()
        yes()
        answer(Answer.Option("tap"))
        expect { ogre.tapped shouldBe true }
    }

    /** Unearth {U}: back from the graveyard with haste, exiled at the next end step. */
    @Test
    fun fateknitterUnearth() = scenario {
        alice.battlefield("Island")
        alice.graveyard("Fateknitter")
        bob.battlefield(ogre)

        alice.activates("Fateknitter", ability = 1)
        allPass()
        expect {
            alice.battlefield has "Fateknitter"
            "Fateknitter" hasKeyword Keyword.Haste
        }
        // CR 702.84a: haste lets it use {T} at once
        alice.activates("Fateknitter", target = ogre)
        allPass()
        yes()
        answer(Answer.Option("tap"))
        expect { ogre.tapped shouldBe true }

        advanceTo(TurnStep.End)
        allPass()
        expect { alice.exile has "Fateknitter" }
    }

    /** {U/P}: change a target of target spell or ability to this creature. */
    @Test
    fun cursewebMite() = scenario {
        alice.battlefield("Curseweb Mite", bear)
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        turn(2, active = bob)

        bob.casts("Storm Spark", target = bear)
        bob.passes()
        // CR 107.4f: without blue mana, the suggested payment is 2 life for the Phyrexian blue
        alice.activates("Curseweb Mite", target = "Storm Spark")
        allPass()
        allPass()
        expect {
            alice.life shouldBe 18
            alice.battlefield has bear
            "Curseweb Mite".view.obj!!.damage shouldBe 3
        }
    }
}
