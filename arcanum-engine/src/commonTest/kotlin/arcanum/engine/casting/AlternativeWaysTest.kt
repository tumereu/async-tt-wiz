package arcanum.engine.casting

import arcanum.api.Color
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Ways to cast that used to be done by hand (D61): emerge, harmonize, prototype, aftermath and ripple. */
class AlternativeWaysTest {
    private val ox = Cards.invent("Auto4 Plain Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val horror = Cards.invent("Auto4 Emerging Horror", "{6}{G}", "Creature — Eldrazi Horror", "Emerge {5}{G} (You may cast this spell by sacrificing a creature and paying the emerge cost reduced by that creature's mana value.)", "6/6")
    private val growth = Cards.invent("Auto4 Harmonic Growth", "{2}{G}", "Sorcery", "Draw two cards.\nHarmonize {4}{G} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)")
    private val split = Cards.inventFaces("Split",
        face("Auto4 Start", "{1}{W}", "Sorcery", "You gain 2 life.", null, null, null),
        face("Auto4 Finish", "{1}{R}", "Sorcery", "Aftermath (Cast this spell only from your graveyard. Then exile it.)\nAuto4 Finish deals 2 damage to any target.", null, null, null))
    private val ripple = Cards.invent("Auto4 Ripple Bolt", "{R}", "Instant", "Ripple 4 (When you cast this spell, you may reveal the top four cards of your library. You may cast any revealed cards with the same name as this spell without paying their mana costs. Put the rest on the bottom of your library.)\nAuto4 Ripple Bolt deals 1 damage to any target.")

    private fun Scenario.alternativeWay(name: String): CastWay =
        Engine.options(state, alice.id).filterIsInstance<Input.Cast>().first { it.card == card(name) && it.way.alternative != null }.way

    // CR 702.119a, c: sacrificing a creature, the emerge cost is reduced by its mana value
    @Test
    fun emerge() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", ox)
        alice.hand(horror)
        alice.casts(horror, way = alternativeWay(horror), choosing = listOf(ox))
        allPass()
        expect {
            alice.battlefield has horror
            alice.graveyard has ox
        }
    }

    // CR 702.180a, b: from the graveyard, tapping a creature reduces the harmonize cost by its power; then exiled
    @Test
    fun harmonize() = scenario {
        alice.battlefield("Forest", "Forest", ox)
        alice.graveyard(growth)
        alice.library("Island", "Island")
        alice.casts(growth, choosing = listOf(ox))
        allPass()
        expect {
            alice.exile has growth
            alice.hand.size shouldBe 2
            ox.tapped shouldBe true
        }
    }

    // CR 702.180a: tapping no creature, the whole harmonize cost
    @Test
    fun harmonizeWithoutTapping() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.graveyard(growth)
        alice.library("Island", "Island")
        alice.casts(growth, choosing = emptyList())
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(emptyList()))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        expect { alice.exile has growth }
    }

    // CR 702.160a, 718.3: cast prototyped it is a red 2/2 with mana value 2
    @Test
    fun prototype() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Smelter Colossus")
        alice.casts("Smelter Colossus", way = alternativeWay("Smelter Colossus"))
        allPass()
        expect {
            "Smelter Colossus".power shouldBe 2
            "Smelter Colossus".colors shouldBe listOf(Color.Red)
            "Smelter Colossus".view.characteristics!!.manaValue() shouldBe 2
        }
    }

    // CR 702.127a: the first half from the hand; the aftermath half only from the graveyard, then exiled
    @Test
    fun aftermath() = scenario {
        alice.battlefield("Plains", "Plains", "Mountain", "Mountain")
        alice.hand(split)
        assertFailsWith<IllegalArgumentException> { alice.casts(split, way = CastWay(face = 1), target = "bob") }
        alice.casts(split)
        allPass()
        expect {
            alice.life shouldBe 22
            alice.graveyard has split
        }
        assertFailsWith<IllegalArgumentException> { alice.casts(split) }
        alice.casts(split, way = CastWay(face = 1), target = "bob")
        allPass()
        expect {
            bob.life shouldBe 18
            alice.exile has split
        }
    }

    // CR 702.60a: the revealed copies with its name are cast without paying their mana costs; the rest go to the bottom
    @Test
    fun ripple() = scenario {
        alice.battlefield("Mountain")
        alice.hand(ripple)
        alice.library("Island", ripple, "Island", "Forest", "Plains")
        alice.casts(ripple, target = "bob")
        allPass()
        var rippled = false
        var guard = 30
        while (guard-- > 0 && (state.stack.isNotEmpty() || pending != null)) {
            when (val d = pending) {
                // Ripple the first time; the copy cast this way ripples too, declined
                is Decision.May -> { alice.answers(Answer.YesNo(!rippled)); rippled = true }
                is Decision.ChooseObjects -> answer(Answer.Objects(d.options.filter { state.objOrThrow(it).zone == Zone.Library }))
                is Decision.Targets -> answer(Answer.Targets(listOf(arcanum.engine.state.TargetRef.Player(bob.id))))
                null -> passPriority()
                else -> error("Unexpected $d")
            }
        }
        expect { bob.life shouldBe 18 }
        assertEquals("Plains", state.player(alice.id).library.first().let { state.objOrThrow(it) }.definition!!.faces.first().name)
        assertTrue(state.player(alice.id).library.size == 4)
    }
}
