package arcanum.engine.sets.fra

import arcanum.api.CounterKind
import arcanum.api.ObjectMark
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reality Fracture's preparation cards (CR 722): a prepared permanent's controller gets a copy of its prepare spell in
 * exile and may cast it, which unprepares the permanent; the copy goes when the permanent stops being prepared. */
class FraPrepareScenariosTest {
    private val seraph = Cards.inventFaces("Prepare",
        face("Bloomwing Seraph", "{3}{W}", "Creature — Angel Cleric", "Flying, vigilance\nThis creature enters prepared.", "2/4", null, null),
        face("Graft Mending", "{G/W}", "Sorcery", "Put a +1/+1 counter on target creature. You gain 1 life.", null, null, null))
    private val shaper = Cards.inventFaces("Prepare",
        face("Riddle Shaper", "{1}{U/B}", "Creature — Octopus Wizard",
            "At the beginning of your upkeep, if this creature isn't prepared, it becomes prepared.\n{2}: Put target card from your graveyard on the bottom of your library.",
            "1/3", null, null),
        face("Drop the Terms", "{U/B}", "Sorcery", "Mill three cards.", null, null, null))
    private val rhymer = Cards.inventFaces("Prepare",
        face("Cinder Rhymester", "{1}{R}{R}", "Creature — Elemental Sorcerer", "Prowess\nThis creature enters prepared.", "3/3", null, null),
        face("Lava Swell", "{R}", "Instant", "Until end of turn, whenever you tap a Mountain for mana, add an additional {R}.", null, null, null))
    private val codie = Cards.invent("Pagey, Hungering Tome", "{3}", "Legendary Artifact Creature — Book Construct", """
        Whenever you cast a prepared spell, copy it. You may choose new targets for the copy.
        {W}{U}{B}{R}{G}, {T}: Each creature you control becomes prepared. (Only creatures with prepare spells can become prepared.)
    """, "1/4")
    private val coursework = Cards.invent("Endless Syllabus", "{2}{U}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, tap enchanted creature. It becomes unprepared.
        Enchanted creature loses all abilities and doesn't untap during its controller's untap step.
    """)
    private val arena = Cards.invent("Spellclash Proving Grounds", null, "Land", """
        {T}: Add {C}.
        {2}, {T}: Target creature that attacked this turn becomes prepared. Activate only as a sorcery. (Only creatures with prepare spells can become prepared.)
        {4}, {T}: Target creature becomes prepared.
    """)
    private val murder = Cards.invent("Quick Execution", "{1}{B}{B}", "Instant", "Destroy target creature.")
    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    private fun Scenario.prepared(name: String): Boolean = state.obj(card(name, zone = Zone.Battlefield))!!.marks.contains(ObjectMark.Prepared)

    /** Entering prepared gives its controller the prepare spell's copy in exile; casting it unprepares the creature. */
    // CR 722.3a, 722.3c, 601.2i
    @Test
    fun entersPreparedAndCastsTheCopy() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Forest")
        alice.hand(seraph)
        alice.casts("Bloomwing Seraph", paying = listOf("Plains", "Plains", "Plains", "Plains"))
        allPass()
        assertTrue(prepared("Bloomwing Seraph"))
        expect { alice.exile has "Graft Mending" }
        assertTrue(Engine.options(state, alice.id).any { it is Input.Cast && state.obj(it.card)?.zone == Zone.Exile })
        alice.casts("Graft Mending", target = "Bloomwing Seraph", paying = listOf("Forest"))
        assertFalse(prepared("Bloomwing Seraph"))
        allPass()
        expect {
            "Bloomwing Seraph".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            alice.life shouldBe 21
            alice.exile lacks "Graft Mending"
            alice.graveyard lacks "Graft Mending"
        }
    }

    /** The copy is the controller's only while the permanent stays prepared on the battlefield. */
    // CR 722.3c, 704.5e
    @Test
    fun copyGoesWhenThePermanentLeaves() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        bob.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(seraph)
        bob.hand(murder)
        alice.casts("Bloomwing Seraph")
        allPass()
        expect { alice.exile has "Graft Mending" }
        alice.passes()
        bob.casts(murder, target = "Bloomwing Seraph")
        allPass()
        expect {
            card("Bloomwing Seraph").zone shouldBe Zone.Graveyard
            alice.exile lacks "Graft Mending"
        }
    }

    /** "If this creature isn't prepared, it becomes prepared": not again while it is (one copy). */
    // CR 603.4, 722.3a
    @Test
    fun becomesPreparedEachUpkeepOnlyWhenNot() = scenario {
        alice.battlefield(shaper)
        alice.library(bear, bear, bear)
        bob.library(bear, bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        allPass()
        assertTrue(prepared("Riddle Shaper"))
        expect { alice.exile.names.count { it == "Drop the Terms" } shouldBe 1 }
        advanceTo(TurnStep.Upkeep, turn = 5)
        expect {
            stack.isEmpty()
            alice.exile.names.count { it == "Drop the Terms" } shouldBe 1
        }
    }

    /** The copy is a spell cast: prowess triggers; an instant copy can be cast any time its controller has priority. */
    // CR 702.108a, 722.3c
    @Test
    fun castingTheCopyIsCastingASpell() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        bob.library(bear)
        alice.hand(rhymer)
        alice.casts("Cinder Rhymester", paying = listOf("Mountain", "Mountain", "Mountain"))
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(bob.id, state.turn.active)
        passPriority()
        alice.casts("Lava Swell")
        expect { "Cinder Rhymester".power shouldBe 3 }
        allPass()
        allPass()
        expect { "Cinder Rhymester".power shouldBe 4 }
    }

    /** A sorcery copy only as a sorcery. */
    // CR 307.1, 722.3c
    @Test
    fun aSorceryCopyAsASorcery() = scenario {
        alice.battlefield(shaper, "Island")
        alice.library(bear, bear, bear, bear)
        bob.library(bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        allPass()
        assertTrue(prepared("Riddle Shaper"))
        assertFailsWith<IllegalArgumentException> { alice.casts("Drop the Terms", paying = listOf("Island")) }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts("Drop the Terms", paying = listOf("Island"))
        allPass()
        expect { alice.graveyard.names.count { it == "Market Bear" } shouldBe 3 }
    }

    /** "Whenever you cast a prepared spell, copy it": the copy of the prepare spell is a prepare spell too. */
    // CR 722.3d, 707.10
    @Test
    fun codieCopiesThePreparedSpell() = scenario {
        alice.battlefield(codie, "Plains", "Plains", "Plains", "Plains", "Forest")
        alice.hand(seraph)
        alice.casts("Bloomwing Seraph", paying = listOf("Plains", "Plains", "Plains", "Plains"))
        allPass()
        alice.casts("Graft Mending", target = "Bloomwing Seraph", paying = listOf("Forest"))
        resolveAll()
        // the copy's new target (CR 707.10c): the same creature
        answer(arcanum.engine.decision.Answer.Targets(listOf(target("Bloomwing Seraph"))))
        resolveAll()
        expect {
            "Bloomwing Seraph".counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            alice.life shouldBe 22
        }
    }

    /** Only creatures with prepare spells can become prepared; an Aura can unprepare one (the copy goes). */
    // CR 722.3a, 722.3b
    @Test
    fun onlyPrepareCreaturesBecomePreparedAndAurasUnprepare() = scenario {
        alice.battlefield(arena, bear, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Island", "Island", "Island")
        alice.hand(coursework, seraph)
        alice.activates(arena, ability = 2, target = "Market Bear", paying = listOf("Plains", "Plains", "Plains", "Plains"))
        allPass()
        assertFalse(prepared("Market Bear"))
        expect { alice.exile.isEmpty() }
        alice.casts("Bloomwing Seraph", paying = listOf("Plains#4", "Plains#5", "Plains#6", "Plains#7"))
        allPass()
        assertTrue(prepared("Bloomwing Seraph"))
        expect { alice.exile has "Graft Mending" }
        alice.casts(coursework, target = "Bloomwing Seraph", paying = listOf("Island", "Island", "Island"))
        allPass()
        allPass()
        expect {
            "Bloomwing Seraph".tapped shouldBe true
            alice.exile lacks "Graft Mending"
        }
        assertFalse(prepared("Bloomwing Seraph"))
    }

    private fun Scenario.resolveAll() {
        var guard = 20
        while (guard-- > 0) {
            when {
                pending is arcanum.engine.decision.Decision.May -> answer(arcanum.engine.decision.Answer.YesNo(true))
                pending != null -> return
                state.stack.isNotEmpty() -> resolve()
                else -> return
            }
        }
    }
}
