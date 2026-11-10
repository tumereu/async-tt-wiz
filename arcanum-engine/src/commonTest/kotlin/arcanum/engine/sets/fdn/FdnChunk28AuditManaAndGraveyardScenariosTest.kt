package arcanum.engine.sets.fdn

import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Chunk 28 played: the invented names carry the oracle text of Gilded Lotus and Reassembling Skeleton. */
class FdnChunk28AuditManaAndGraveyardScenariosTest {
    private val blossom = Cards.invent("Gilded Blossom", "{5}", "Artifact", "{T}: Add three mana of any one color.")
    private val skeleton = Cards.invent("Rebuilding Skeleton", "{1}{B}", "Creature — Skeleton Warrior", "{1}{B}: Return this card from your graveyard to the battlefield tapped.", "1/1")

    // CR 605.1a, 106.1b: three mana, all of the one chosen colour
    @Test
    fun blossomAddsThreeManaOfOneColor() = scenario {
        alice.battlefield(blossom)
        alice.activates(blossom)
        pending?.let { answer(Answer.ColorChoice(arcanum.api.Color.Green)) }
        expect { alice.pool.size shouldBe 3 }
    }

    // CR 602.2, 400.7: activated from the graveyard it returns tapped
    @Test
    fun skeletonReturnsFromGraveyardTapped() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(skeleton)
        alice.activates(skeleton)
        allPass()
        expect {
            alice.battlefield has skeleton
            skeleton.tapped shouldBe true
        }
    }
}
