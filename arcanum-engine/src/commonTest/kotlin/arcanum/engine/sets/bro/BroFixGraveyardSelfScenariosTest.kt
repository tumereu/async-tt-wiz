package arcanum.engine.sets.bro

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/**
 * BRO fixes: abilities naming "this card from your graveyard" outside a dies trigger (CR 113.6m). Such an ability functions only in the
 * graveyard unless its trigger condition or an earlier part of its cost or effect puts the object there: "put into your graveyard from
 * anywhere", "when you sacrifice this creature", "when you discard this card" (CR 701.9a), a sacrifice cost (CR 701.21a).
 */
class BroFixGraveyardSelfScenariosTest {
    private val husk = Cards.invent("Gravewake Husk", "{1}{B}", "Creature — Zombie",
        "When this card is put into your graveyard from anywhere, you may exile this card from your graveyard. If you do, create a 2/2 black Zombie creature token.",
        "2/2")
    private val edict = Cards.invent("Rotfall Edict", "{1}{B}", "Sorcery", "Destroy target creature.")
    private val revenant = Cards.invent("Ashwake Revenant", "{2}{B}", "Creature — Spirit",
        "Sacrifice this creature: Return this card from your graveyard to your hand at the beginning of the next end step.", "2/1")
    private val martyr = Cards.invent("Pyrewake Martyr", "{1}{R}", "Creature — Human Cleric", """
        Sacrifice this creature: You gain 2 life.
        When you sacrifice this creature, return this card from your graveyard to your hand at the beginning of the next end step.
    """, "1/1")
    private val thrall = Cards.invent("Cinderback Thrall", "{1}{R}", "Creature — Zombie",
        "When you discard this card, return this card from your graveyard to your hand at the beginning of the next end step.", "2/2")
    private val relic = Cards.invent("Kilnbound Relic", "{2}", "Artifact",
        "{1}, Sacrifice this artifact: Draw a card. Return this card from your graveyard to the battlefield tapped.")
    private val ponder = Cards.invent("Siltmind Ponder", "{U}", "Sorcery", "Draw a card, then discard a card.")

    // CR 113.6m: the trigger condition puts it into the graveyard, so the ability isn't limited to the graveyard: it triggers as the
    // creature dies (CR 700.4), and the card is exiled from there
    @Test
    fun putIntoYourGraveyardFromAnywhereTriggersAsItDies() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.battlefield(husk)
        alice.hand(edict)
        alice.casts(edict, target = husk)
        allPass()
        allPass()
        answer(Answer.YesNo(true))
        allPass()
        expect {
            alice.exile has husk
            alice.battlefield has "Zombie Token"
        }
    }

    // CR 113.6m, 701.21a: the sacrifice cost puts it into the graveyard first, so the ability works on the battlefield, where the cost
    // can be paid; the delayed trigger returns the card (CR 603.7)
    @Test
    fun aSacrificeCostLetsTheAbilityWorkOnTheBattlefield() = scenario {
        alice.battlefield(revenant)
        alice.activates(revenant)
        allPass()
        alice.graveyard has revenant
        advanceTo(TurnStep.End)
        allPass()
        expect { alice.hand has revenant }
    }

    // CR 113.6m, 701.21a: "when you sacrifice this creature" puts it into the graveyard: the trigger works where it is sacrificed from
    @Test
    fun whenYouSacrificeThisCreatureTriggersFromTheBattlefield() = scenario {
        alice.battlefield(martyr)
        alice.activates(martyr)
        allPass()
        advanceTo(TurnStep.End)
        allPass()
        expect {
            alice.life shouldBe 22
            alice.hand has martyr
        }
    }

    // CR 113.6m, 701.9a: discarding puts the card into the graveyard: the trigger works as it is discarded from the hand
    @Test
    fun whenYouDiscardThisCardTriggersFromTheHand() = scenario {
        alice.battlefield("Island")
        alice.library("Island")
        alice.hand(ponder, thrall)
        alice.casts(ponder)
        allPass()
        answer(Answer.Objects(listOf(card(thrall))))
        allPass()
        alice.graveyard has thrall
        advanceTo(TurnStep.End)
        allPass()
        expect { alice.hand has thrall }
    }

    // CR 113.6m, 701.21a: an ability returning this card from the graveyard to the battlefield works on the battlefield when its cost
    // sacrifices it first; the card returns as a new object (CR 400.7)
    @Test
    fun aSacrificeCostBeforeAReturnToTheBattlefield() = scenario {
        alice.battlefield("Island")
        alice.library("Island")
        alice.battlefield(relic)
        alice.activates(relic)
        allPass()
        expect {
            alice.hand has "Island"
            alice.battlefield has relic
        }
    }
}
