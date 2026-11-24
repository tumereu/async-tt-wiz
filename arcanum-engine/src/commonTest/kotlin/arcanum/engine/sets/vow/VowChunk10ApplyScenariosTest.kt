package arcanum.engine.sets.vow

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** VOW chunk 10: a Vehicle crewed by tapping two of its controller's own creatures (the audit's "no controlled-by" was a false alarm), control
 * of a creature with mana value up to the number of Vampires you control, becoming a blue Serpent 5/5 until end of turn, "one or both" modes
 * resolved in printed order, a creature that exiles itself and returns at the next end step, and an opponent's second spell each turn
 * (with three players). */
class VowChunk10ApplyScenariosTest {
    private val hearse = Cards.invent("Bridal Hearse", "{2}{R}", "Artifact — Vehicle", """
        Trample
        Tap two untapped creatures you control: This Vehicle becomes an artifact creature until end of turn.
    """.trimIndent(), "5/5")
    private val overbearing = Cards.invent("Overbearing Vampire", "{1}{R}{R}", "Creature — Vampire",
        "When this creature enters, gain control of target creature with mana value less than or equal to the number of Vampires you control until end of turn. Untap that creature. It gains haste until end of turn.", "3/3")
    private val palePup = Cards.invent("Pale Fangling", "{1}{B}", "Creature — Vampire", "", "1/1")
    private val ambush = Cards.invent("Serpent's Ambush", "{1}{U}", "Instant",
        "Until end of turn, target creature becomes a blue Serpent with base power and toughness 5/5.")
    private val reprisal = Cards.invent("Markov Reprisal", "{2}{R}", "Sorcery", """
        Choose one or both —
        • Creatures you control get +1/+0 until end of turn.
        • Target Vampire you control deals damage equal to its power to another target creature.
    """.trimIndent())
    private val spirit = Cards.invent("Wavering Spirit", "{1}{W}", "Creature — Spirit", """
        {W}, Exile three cards from your graveyard: This creature gains first strike until end of turn.
        Discard a card: Exile this creature. Return it to the battlefield under its owner's control at the beginning of the next end step.
    """.trimIndent(), "3/1")
    private val moon = Cards.invent("Baying Moon", "{2}{G}", "Enchantment", """
        At the beginning of combat on your turn, target Wolf or Werewolf you control gets +2/+2 until end of turn.
        Whenever an opponent casts their second spell each turn, create a 2/2 green Wolf creature token.
    """.trimIndent())
    private val whisper = Cards.invent("Idle Whisper", "{U}", "Instant", "Draw a card.")
    private val murmur = Cards.invent("Idle Murmur", "{U}", "Instant", "Draw a card.")

    private val mouse = Cards.invent("Dusk Mouse", "{1}{G}", "Creature — Mouse", "", "1/1")
    private val boar = Cards.invent("Dusk Boar", "{2}{G}", "Creature — Boar", "", "3/3")
    private val gnat = Cards.invent("Dusk Gnat", "{G}", "Creature — Insect", "", "1/1")
    private val wolf = Cards.invent("Fenmoor Hound", "{1}{G}", "Creature — Wolf", "", "2/2")
    private val ogre = Cards.invent("Hollow Ogre", "{2}{R}", "Creature — Ogre", "", "2/3")
    private val imp = Cards.invent("Hollow Imp", "{1}{R}", "Creature — Imp", "", "1/1")

    private fun Scenario.targets(vararg names: String) = answer(Answer.Targets(names.map { target(it) }))

    // CR 118.3, 602.2: the cost's creatures come from the one paying: opponents' creatures can't be tapped for it; one own creature is not enough
    @Test
    fun hearseNeedsTwoOfYourOwnCreatures() = scenario(players = 3) {
        alice.battlefield(hearse, imp)
        bob.battlefield(mouse, boar)
        carol.battlefield(gnat)
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().none { it.source == card(hearse) })
    }

    // CR 301.7, 613.1d (it becomes an artifact creature), 118.3 (the tapped creatures are the controller's)
    @Test
    fun hearseBecomesACreatureByTappingTwoOwnCreatures() = scenario(players = 3) {
        alice.battlefield(hearse, imp, ogre)
        bob.battlefield(mouse, boar)
        alice.activates(hearse, choosing = listOf(imp, ogre))
        resolveAll()
        expect {
            hearse.types.toSet() shouldBe setOf(CardType.Artifact, CardType.Creature)
            imp.tapped shouldBe true
            ogre.tapped shouldBe true
            mouse.tapped shouldBe false
            boar.tapped shouldBe false
        }
    }

    // CR 701.12 (gain control), 302.6, 702.10 (haste), 202.3: the target's mana value is compared with the Vampires its new controller has,
    // this one included; with three players another opponent's creature may be chosen too; control ends at cleanup (CR 514.2)
    @Test
    fun takesAnOpponentsCreatureWithManaValueUpToYourVampires() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", palePup)
        alice.hand(overbearing)
        bob.battlefield(boar, tapped = true)
        carol.battlefield(mouse, tapped = true)
        alice.casts(overbearing)
        resolveAll()
        val options = decision<Decision.Targets>().options.toSet()
        assertTrue(target(mouse) in options)
        assertTrue(target(boar) !in options, "mana value 3 is more than two Vampires")
        targets(mouse)
        resolveAll()
        expect {
            mouse.controller shouldBe alice.id
            mouse.tapped shouldBe false
            boar.controller shouldBe bob.id
        }
        advanceTo(TurnStep.End)
        allPass()
        advanceTo(TurnStep.Upkeep, turn = state.turn.number + 1)
        expect { mouse.controller shouldBe carol.id }
    }

    // CR 613.1d (a creature type replaces the old creature types), 613.1e (colour), 613.4b (base power and toughness); ends at cleanup
    @Test
    fun becomesABlueSerpentFiveByFive() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(ambush)
        bob.battlefield(boar)
        alice.casts(ambush, target = boar)
        resolveAll()
        expect {
            boar.power shouldBe 5
            boar.toughness shouldBe 5
            boar.colors shouldBe listOf(Color.Blue)
            boar.subtypes shouldBe listOf(Subtype("Serpent"))
            boar.types shouldBe listOf(CardType.Creature)
        }
        advanceTo(TurnStep.Upkeep, turn = state.turn.number + 1)
        expect {
            boar.power shouldBe 3
            boar.colors shouldBe listOf(Color.Green)
            boar.subtypes shouldBe listOf(Subtype("Boar"))
        }
    }

    // CR 700.2d: "one or both": the modes happen in printed order, so the Vampire has the +1/+0 when its damage is dealt (CR 120.3)
    @Test
    fun bothModesResolveInPrintedOrder() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", palePup)
        alice.hand(reprisal)
        bob.battlefield(boar)
        alice.casts(reprisal, modes = listOf(0, 1), targets = listOf(palePup, boar))
        resolveAll()
        expect {
            palePup.power shouldBe 2
            boar.view.obj!!.damage shouldBe 2
        }
    }

    // CR 700.2d: the second mode alone
    @Test
    fun theDamageModeAlone() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", palePup)
        alice.hand(reprisal)
        bob.battlefield(mouse)
        alice.casts(reprisal, modes = listOf(1), targets = listOf(palePup, mouse))
        resolveAll()
        expect {
            palePup.power shouldBe 1
            bob.graveyard has mouse
        }
    }

    // CR 602.2, 118.3 (discarding as a cost), 406.3, 400.7, 603.7: it returns as a new object at the next end step, under its owner's control
    @Test
    fun exilesItselfAndReturnsAtTheNextEndStep() = scenario {
        alice.battlefield(spirit)
        alice.hand("Plains")
        alice.activates(spirit, ability = 1, choosing = listOf("Plains"))
        resolveAll()
        expect { alice.exile has spirit }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield has spirit }
    }

    // CR 702.7 (first strike), 118.3: exiling three cards from the graveyard pays for it
    @Test
    fun exilesThreeGraveyardCardsForFirstStrike() = scenario {
        alice.battlefield(spirit, "Plains")
        alice.graveyard("Island", "Island", "Island", "Forest")
        alice.activates(spirit, ability = 0, choosing = listOf("Island", "Island", "Island"))
        resolveAll()
        expect {
            spirit hasKeyword arcanum.api.Keyword.FirstStrike
            alice.graveyard.size shouldBe 1
        }
    }

    // CR 603.2, 603.4: only an opponent's second spell in a turn counts (the first doesn't); the Moon's controller gets the Wolf
    @Test
    fun anOpponentsSecondSpellEachTurnMakesAWolf() = scenario(players = 3) {
        alice.battlefield(moon)
        bob.battlefield("Island", "Island")
        bob.hand(whisper, murmur)
        bob.library("Island", "Island")
        alice.passes()
        bob.casts(whisper)
        allPass()
        expect { alice.battlefield.names.count { it == "Wolf Token" } shouldBe 0 }
        alice.passes()
        bob.casts(murmur)
        allPass()
        expect {
            alice.battlefield.names.count { it == "Wolf Token" } shouldBe 1
            bob.battlefield.names.count { it == "Wolf Token" } shouldBe 0
        }
    }

    // CR 507.1, 603.2: a Wolf you control gets +2/+2 at the beginning of combat on your turn
    @Test
    fun aWolfGetsPlusTwoAtTheBeginningOfCombat() = scenario {
        alice.battlefield(moon, wolf)
        turn(1, alice, TurnStep.PrecombatMain)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        if (pending is Decision.Targets) targets(wolf)
        resolveAll()
        expect { wolf.power shouldBe 4 }
    }
}
