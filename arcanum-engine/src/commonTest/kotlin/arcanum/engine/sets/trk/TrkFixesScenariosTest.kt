package arcanum.engine.sets.trk

import arcanum.api.CardType
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Star Trek fix step: general rules fixes found while working the set, each played here. */
class TrkFixesScenariosTest {
    private val refit = Cards.invent("Trk Fix Hasty Refit", "{W}", "Enchantment — Aura", """
        Flash
        Enchant creature or Vehicle
        Enchanted permanent is a Vehicle artifact with crew 5 and it loses all other card types. (It's not a creature unless it's crewed.)
    """)
    private val mindLink = Cards.invent("Psionic Mind-Link", "{U}", "Sorcery",
        "Target opponent looks at the top card of your library, then exiles it face down. You look at the top two cards of their library, then exile them face down. For each card exiled this way, the player who exiled it may play it for as long as it remains exiled. Mana of any type can be spent to cast spells this way.")
    private val hollowRelic = Cards.invent("Trk Fix Hollow Relic", "{C}", "Artifact", "")
    private val scotty = Cards.invent("Montgomery, Miracle Mechanic", "{1}{U}{U}", "Legendary Creature — Human Artificer", """
        During your main phase, each card in your graveyard has beam me up. The beam me up cost is equal to that spell's mana cost. Montgomery can't be beamed up to cast a spell this way. (Only instant and sorcery spells can be cast this way. You may cast a card from your graveyard for its beam me up cost if you also return another creature you control to its owner's hand. Then exile that spell.)
    """, "2/3")
    private val insight = Cards.invent("Trk Fix Recalled Insight", "{U}", "Instant", "Draw a card.")
    private val bear = Cards.invent("Trk Fix Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val sashGolem = Cards.invent("Trk Fix Sash Golem", "{2}", "Artifact Creature — Equipment Golem", "", "2/2")

    // CR 205.1a: setting its card type to artifact and its artifact subtype to Vehicle replaces its other artifact subtypes (Equipment) and
    // removes the creature types with the creature type
    @Test
    fun anEquipmentCreatureBecomesAVehicleArtifactOnly() = scenario {
        alice.battlefield(sashGolem, refit)
        attach(refit, sashGolem)
        expect {
            sashGolem.types shouldBe listOf(CardType.Artifact)
            sashGolem.subtypes shouldBe listOf(Subtype("Vehicle"))
        }
    }

    // CR 106.1b, 609.4b: colorless is a type of mana, so "mana of any type can be spent" lets a Mountain's red mana pay {C}
    @Test
    fun manaOfAnyTypePaysAColorlessCost() = scenario {
        alice.battlefield("Mountain")
        alice.hand(mindLink)
        alice.library("Island")
        bob.library(hollowRelic, "Swamp", "Swamp")
        alice.castsWithoutCosts(mindLink, target = "bob")
        resolveAll()
        expect { bob.exile has hollowRelic }
        alice.casts(hollowRelic, paying = listOf("Mountain"))
        resolveAll()
        expect { alice.battlefield has hollowRelic }
    }

    private fun Scenario.castable(name: String) =
        Engine.options(state, alice.id).filterIsInstance<Input.Cast>().any { it.card == card(name) }

    // CR 702.196a: the given beam me up costs the card's mana cost and another creature back to hand; then the spell is exiled
    @Test
    fun scottyGivesBeamMeUpReturningAnotherCreature() = scenario {
        alice.battlefield(scotty, bear, "Island")
        alice.graveyard(insight)
        alice.library("Forest")
        alice.casts(insight, choosing = listOf(bear), paying = listOf("Island"))
        resolveAll()
        expect {
            alice.hand has bear
            alice.hand has "Forest"
            alice.exile has insight
            scotty.zone shouldBe Zone.Battlefield
        }
    }

    // CR 702.196b: the giver can't be beamed up to cast a spell this way, so with no other creature nothing can be cast
    @Test
    fun scottyItselfCantBeReturnedForIt() = scenario {
        alice.battlefield(scotty, "Island")
        alice.graveyard(insight)
        assertFalse(castable(insight))
    }

    // CR 505.1: only during its controller's main phase
    @Test
    fun scottyGivesItOnlyDuringYourMainPhase() = scenario {
        alice.battlefield(scotty, bear, "Island")
        alice.graveyard(insight)
        alice.library("Forest", "Forest")
        assertTrue(castable(insight))
        turn(number = 1, active = alice, step = TurnStep.Upkeep)
        assertFalse(castable(insight))
    }
}
