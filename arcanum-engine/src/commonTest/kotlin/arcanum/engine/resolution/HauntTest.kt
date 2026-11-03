package arcanum.engine.resolution

import arcanum.api.Keyword
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.state.NamedValue
import kotlin.test.Test
import kotlin.test.assertEquals

/** Haunt (CR 702.55): exiled haunting a creature as the permanent dies or the spell resolves, and "the creature it haunts" dying. */
class HauntTest {
    private val specter = Cards.invent("Haunt Gloom Specter", "{1}{B}", "Creature — Spirit", """
        Haunt (When this creature dies, exile it haunting target creature.)
        When this creature enters or the creature it haunts dies, target player loses 2 life and you gain 2 life.
    """.trimIndent(), "1/1")
    private val grim = Cards.invent("Haunt Grim Visit", "{B}", "Sorcery", """
        Target player loses 1 life.
        Haunt (When this spell card is put into a graveyard after resolving, exile it haunting target creature.)
        When the creature this card haunts dies, target player loses 3 life.
    """.trimIndent())
    private val bear = Cards.invent("Haunt Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    private fun arcanum.engine.scenario.Scenario.targetAnswer(name: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(name))))
    }

    // CR 702.55a, b, c: the creature dies, is exiled haunting a creature; when that creature dies, the trigger works from exile
    @Test
    fun permanentHauntsAndTriggersFromExile() = scenario {
        alice.battlefield("Mountain", "Mountain", specter)
        alice.hand("Storm Spark", "Storm Spark")
        bob.battlefield(bear)
        alice.casts("Storm Spark", target = specter)
        allPass()
        targetAnswer(bear)
        allPass()
        expect { alice.exile has specter }
        val exiled = state.objOrThrow(card(specter))
        assertEquals(listOf(card(bear)), (exiled.memory[Keyword.Haunt.id] as NamedValue.Objects).ids)
        alice.casts("Storm Spark", target = bear)
        allPass()
        targetAnswer("bob")
        allPass()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    // CR 702.55a: a spell is exiled haunting a creature as it is put into the graveyard resolving
    @Test
    fun spellHaunts() = scenario {
        alice.battlefield("Swamp", "Mountain")
        alice.hand(grim, "Storm Spark")
        bob.battlefield(bear)
        alice.casts(grim, target = "bob")
        allPass()
        targetAnswer(bear)
        allPass()
        expect {
            bob.life shouldBe 19
            alice.exile has grim
        }
        alice.casts("Storm Spark", target = bear)
        allPass()
        targetAnswer("bob")
        allPass()
        expect { bob.life shouldBe 16 }
    }
}
