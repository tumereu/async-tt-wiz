package arcanum.engine.sets.otj

import arcanum.api.Color
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test

/** OTJ chunk 14: enters-tapped lands and creatures. */
class OtjChunk14ReplacementScenariosTest {
    private val desert = Cards.invent("Scorched Dunes", null, "Land — Desert",
        "This land enters tapped.\nWhen this land enters, it deals 1 damage to target opponent.\n{T}: Add {R} or {W}.")
    private val plateau = Cards.invent("Mirage Plateau", null, "Land — Desert",
        "This land enters tapped. As it enters, choose a color.\n{T}: Add one mana of the chosen color.")
    private val thief = Cards.invent("Dozing Thunder-Thief", "{3}{U}", "Creature — Turtle Rogue", "Flash\nThis creature enters tapped.", "4/4")

    // CR 614.1d, 603.2: the land arrives tapped, then its trigger pings the chosen opponent only (three players)
    @Test
    fun desertEntersTappedAndPingsTheChosenOpponent() = scenario(players = 3) {
        alice.hand(desert)
        alice.plays(desert)
        allPass()
        playOut("carol")
        expect {
            desert.tapped shouldBe true
            carol.life shouldBe 19
            bob.life shouldBe 20
            alice.life shouldBe 20
        }
    }

    // CR 614.12: the colour is chosen as it enters, while it also enters tapped
    @Test
    fun plateauEntersTappedWithAChosenColor() = scenario {
        alice.hand(plateau)
        alice.plays(plateau)
        answer(Answer.ColorChoice(Color.Green))
        allPass()
        expect { plateau.tapped shouldBe true }
    }

    // CR 614.1d: a creature cast with flash still enters tapped
    @Test
    fun creatureEntersTapped() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(thief)
        alice.casts(thief)
        allPass()
        expect { thief.tapped shouldBe true }
    }
}
