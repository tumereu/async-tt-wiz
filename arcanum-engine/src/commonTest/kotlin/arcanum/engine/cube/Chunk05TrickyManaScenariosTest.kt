package arcanum.engine.cube

import arcanum.api.Ability
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Chunk 5 in play: rituals with a twist, imprint, suspend, upkeep costs, an entering replacement, a granted mana
 * ability and X mana in any combination (CR 106, 605, 607, 614). */
class Chunk05TrickyManaScenariosTest {
    // ---- Rituals

    /** "Add {B}{B}{B}." */
    @Test
    fun covensRite() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Coven's Rite")

        alice.casts("Coven's Rite")
        allPass()

        assertEquals(List(3) { Color.Black }, alice.pool.map { it.color })
    }

    /** Threshold — "Add {B}{B}{B}{B}{B} instead if there are seven or more cards in your graveyard." */
    @Test
    fun covensRiteWithThreshold() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand("Coven's Rite", pipSpell("{B}{B}{B}{B}{B}"))

        alice.casts("Coven's Rite")
        allPass()
        alice.casts(pipSpell("{B}{B}{B}{B}{B}"))

        expect { stack.names shouldBe listOf(pipSpell("{B}{B}{B}{B}{B}")) }
        assertTrue(alice.pool.isEmpty())
    }

    /** "Add {R}{R}{R}." */
    @Test
    fun franticInvocation() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Frantic Invocation")

        alice.casts("Frantic Invocation")
        allPass()

        assertEquals(List(3) { Color.Red }, alice.pool.map { it.color })
    }

    /** Splice onto Arcane {1}{R} (CR 702.47): a second one spliced onto the first adds its {R}{R}{R} too. */
    @Ignore("engine: splice")
    @Test
    fun franticInvocationSpliced() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Frantic Invocation", "Frantic Invocation")

        perform(Input.Cast(alice.id, card("Frantic Invocation", 0)))
        // CR 702.47b: the splice is revealed and paid as the spell is cast
        alice.answers(Answer.YesNo(true))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()

        assertEquals(List(6) { Color.Red }, alice.pool.map { it.color })
        expect { alice.hand.names shouldBe listOf("Frantic Invocation") }
    }

    /** "Add {R}{R}, then add {R} for each card named Rite of Embers in each graveyard." */
    @Test
    fun riteOfEmbers() = scenario {
        alice.hand("Rite of Embers")
        alice.battlefield("Mountain")
        alice.graveyard("Rite of Embers")
        bob.graveyard("Rite of Embers")

        alice.casts("Rite of Embers")
        allPass()

        // The one resolving is still on the Stack: two others count
        assertEquals(List(4) { Color.Red }, alice.pool.map { it.color })
        expect { alice.graveyard.size shouldBe 2 }
    }

    // ---- Artifacts

    /** "{1}, {T}, Sacrifice this artifact: Add one mana of any color. Draw a card." It draws, so it isn't a mana ability
     * (CR 605.1a): it uses the Stack. */
    @Test
    fun prismaticOrb() = scenario {
        alice.battlefield("Prismatic Orb", "Plains")
        alice.library("Island")

        alice.activates("Prismatic Orb")
        expect {
            "Plains".tapped shouldBe true
            alice.graveyard has "Prismatic Orb"
            stack.names shouldBe listOf("ability of Prismatic Orb")
        }
        allPass()
        alice.answers(Answer.ColorChoice(Color.Green))

        assertEquals(listOf<Color?>(Color.Green), alice.pool.map { it.color })
        expect { alice.hand has "Island" }
    }

    /** Imprint — "When this artifact enters, you may exile a nonartifact, nonland card from your hand." and "{T}: Add one
     * mana of any of the exiled card's colors." (CR 607.2a) */
    @Test
    fun chromedLocket() = scenario {
        alice.hand("Chromed Locket", "Storm Spark", "Forest", "Sun Band")

        alice.casts("Chromed Locket")
        allPass()
        expect { stack.names shouldBe listOf("ability of Chromed Locket") }
        allPass()
        // Only Storm Spark is neither an artifact nor a land
        assertEquals(listOf(card("Storm Spark")), decision<Decision.ChooseObjects>().options)
        alice.chooses("Storm Spark")
        expect { alice.exile has "Storm Spark" }

        alice.activates("Chromed Locket")

        assertEquals(listOf<Color?>(Color.Red), alice.pool.map { it.color })
    }

    @Test
    fun chromedLocketWithNothingExiled() = scenario {
        alice.battlefield("Chromed Locket")
        // CR 106.5: with no exiled card the colour is undefined: it adds no mana
        alice.activates("Chromed Locket")
        assertTrue(alice.pool.isEmpty())
        expect { "Chromed Locket".tapped shouldBe true }
    }

    /** "Discard your hand, Sacrifice this artifact: Add three mana of any one color. Activate only as an instant." */
    @Test
    fun jackalsEyeGem() = scenario {
        alice.battlefield("Jackal's Eye Gem")
        alice.hand("Forest", "Storm Spark")

        alice.activates("Jackal's Eye Gem", color = Color.Green)

        assertEquals(List(3) { Color.Green }, alice.pool.map { it.color })
        expect {
            alice.hand.isEmpty()
            alice.graveyard has "Forest"
            alice.graveyard has "Storm Spark"
            alice.graveyard has "Jackal's Eye Gem"
        }
    }

    /** Suspend 3—{0}: exiled with three time counters, one removed each upkeep; with the last it's cast without paying
     * (CR 702.62). "{T}, Sacrifice this artifact: Add three mana of any one color." */
    @Test
    fun lotusBud() = scenario {
        alice.hand("Lotus Bud")
        alice.library("Forest", "Forest", "Forest", "Forest")
        bob.library("Island", "Island", "Island", "Island")
        val suspend = view()[card("Lotus Bud")]!!.characteristics!!.abilities.first { it.ability is Ability.SpecialAction }

        perform(Input.Activate(alice.id, card("Lotus Bud"), suspend.origin))
        expect { "Lotus Bud".counters(CounterKind.Time) shouldBe 3 }
        upkeepOf(3)
        expect { "Lotus Bud".counters(CounterKind.Time) shouldBe 2 }
        upkeepOf(5)
        upkeepOf(7)
        // CR 702.62a: the last counter is removed; "when the last is removed" triggers and it may be cast
        expect { stack.names shouldBe listOf("ability of Lotus Bud") }
        allPass()
        alice.answers(Answer.YesNo(true))
        expect { stack.names shouldBe listOf("Lotus Bud") }
        allPass()

        expect {
            alice.battlefield has "Lotus Bud"
        }
        alice.activates("Lotus Bud", color = Color.Blue)
        assertEquals(List(3) { Color.Blue }, alice.pool.map { it.color })
        expect { alice.graveyard has "Lotus Bud" }
    }

    /** Advances to alice's upkeep of turn [turn] and resolves what triggers there. */
    private fun Scenario.upkeepOf(turn: Int) {
        advanceTo(TurnStep.Upkeep, turn)
        allPass()
    }

    /** "At the beginning of your upkeep, flip a coin. If you lose the flip, this artifact deals 3 damage to you." */
    @Test
    fun manaReliquaryLosesTheFlip() = scenario {
        alice.battlefield("Mana Reliquary")
        alice.library("Forest")
        turn(1, active = bob, step = TurnStep.End)
        random(Answer.CoinFlip(false))

        advanceTo(TurnStep.Upkeep, 2)
        expect { stack.names shouldBe listOf("ability of Mana Reliquary") }
        allPass()

        expect { alice.life shouldBe 17 }
    }

    @Test
    fun manaReliquaryWinsTheFlip() = scenario {
        alice.battlefield("Mana Reliquary")
        alice.library("Forest")
        alice.hand(pipSpell("{2}"))
        turn(1, active = bob, step = TurnStep.End)
        random(Answer.CoinFlip(true))

        advanceTo(TurnStep.Upkeep, 2)
        allPass()
        expect { alice.life shouldBe 20 }

        // "{T}: Add {C}{C}."
        advanceTo(TurnStep.PrecombatMain)
        alice.casts(pipSpell("{2}"))
        expect { stack.names shouldBe listOf(pipSpell("{2}")) }
    }

    /**
     * "This artifact doesn't untap during your untap step. At the beginning of your upkeep, you may pay {4}. If you do,
     * untap this artifact. At the beginning of your draw step, if this artifact is tapped, it deals 1 damage to you."
     * "{T}: Add {C}{C}{C}."
     */
    @Test
    fun manaStrongboxPaidToUntap() = scenario {
        alice.battlefield("Mana Strongbox", tapped = true)
        alice.battlefield("Forest", "Forest", "Forest", "Forest", tapped = true)
        alice.library("Forest")
        turn(1, active = bob, step = TurnStep.End)

        advanceTo(TurnStep.Upkeep, 2)
        expect {
            // CR 502.3: the Forests untap, the Strongbox doesn't
            "Mana Strongbox".tapped shouldBe true
            "Forest".tapped shouldBe false
            stack.names shouldBe listOf("ability of Mana Strongbox")
        }
        allPass()
        alice.answers(Answer.YesNo(true))
        // CR 608.2g: the {4} is paid with the Forests
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect {
            "Mana Strongbox".tapped shouldBe false
            card("Forest", 3).tapped shouldBe true
        }

        advanceTo(TurnStep.Draw)
        expect {
            stack.isEmpty()
            alice.life shouldBe 20
        }
        alice.activates("Mana Strongbox")
        assertEquals(List<Color?>(3) { null }, alice.pool.map { it.color })
    }

    @Test
    fun manaStrongboxLeftTapped() = scenario {
        alice.battlefield("Mana Strongbox", tapped = true)
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.library("Forest")
        turn(1, active = bob, step = TurnStep.End)

        advanceTo(TurnStep.Upkeep, 2)
        allPass()
        alice.answers(Answer.YesNo(false))
        advanceTo(TurnStep.Draw)
        expect { stack.names shouldBe listOf("ability of Mana Strongbox") }
        allPass()

        expect { alice.life shouldBe 19 }
    }

    /** "If this artifact would enter, you may discard a land card instead. If you do, put this artifact onto the
     * battlefield. If you don't, put it into its owner's graveyard." (CR 614.1c) and "{T}: Add one mana of any color." */
    @Test
    fun gemstoneCharm() = scenario {
        alice.hand("Gemstone Charm", "Forest", "Storm Spark")

        alice.casts("Gemstone Charm")
        allPass()
        // CR 614.1a: the replacement asks as it would enter; the only land card is discarded without a choice
        alice.answers(Answer.YesNo(true))

        expect {
            alice.battlefield has "Gemstone Charm"
            alice.graveyard has "Forest"
            alice.hand has "Storm Spark"
        }
        alice.activates("Gemstone Charm", color = Color.White)
        assertEquals(listOf<Color?>(Color.White), alice.pool.map { it.color })
    }

    @Test
    fun gemstoneCharmWithoutALand() = scenario {
        alice.hand("Gemstone Charm", "Storm Spark")

        alice.casts("Gemstone Charm")
        allPass()

        expect {
            alice.graveyard has "Gemstone Charm"
            alice.hand has "Storm Spark"
        }
    }

    /** Equipped creature has "{T}: Add one mana of any color." Equip {1} (CR 702.6). */
    @Test
    fun edenCloak() = scenario {
        alice.battlefield("Eden Cloak", "Tinkered Mite", "Plains")
        alice.hand(pipSpell("{B}"))

        alice.activates("Eden Cloak", target = "Tinkered Mite")
        allPass()
        alice.casts(pipSpell("{B}"))

        expect {
            "Tinkered Mite".tapped shouldBe true
            stack.names shouldBe listOf(pipSpell("{B}"))
        }
    }

    /** "This artifact enters tapped." */
    @Test
    fun sorcerersFlaresEntersTapped() = scenario {
        alice.battlefield("Plains")
        alice.hand("Sorcerer's Flares")

        alice.casts("Sorcerer's Flares")
        allPass()

        expect { "Sorcerer's Flares".tapped shouldBe true }
    }

    /** "{X}, {T}, Sacrifice this artifact: Add X mana in any combination of colors." and "When this artifact is put into
     * a graveyard from the battlefield, draw a card." */
    @Test
    fun sorcerersFlares() = scenario {
        alice.battlefield("Sorcerer's Flares", "Plains", "Plains")
        alice.library("Island")

        alice.activates("Sorcerer's Flares", x = 2)
        alice.answers(Answer.ColorChoice(Color.Red))
        alice.answers(Answer.ColorChoice(Color.Green))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))

        assertEquals(listOf<Color?>(Color.Red, Color.Green), alice.pool.map { it.color })
        expect {
            alice.graveyard has "Sorcerer's Flares"
            stack.names shouldBe listOf("ability of Sorcerer's Flares")
        }
        allPass()
        expect { alice.hand has "Island" }
    }
}
