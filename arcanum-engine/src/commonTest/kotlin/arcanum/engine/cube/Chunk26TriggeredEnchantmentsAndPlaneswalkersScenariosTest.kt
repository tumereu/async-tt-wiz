package arcanum.engine.cube

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The cube's chunk 26 (triggered enchantments and planeswalkers) played in the engine. */
class Chunk26TriggeredEnchantmentsAndPlaneswalkersScenariosTest {
    /** On entering, you may discard a card to draw two; {1}, sacrifice it: your creatures gain haste. */
    @Test
    fun grimHomecoming() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.battlefield("Elvish Seer", sick = true)
        alice.hand("Grim Homecoming", "Forest", "Storm Spark")
        alice.library("Island", "Swamp", "Plains")

        alice.casts("Grim Homecoming")
        allPass()
        expect { stack.names shouldBe listOf("ability of Grim Homecoming") }
        allPass()
        answer(Answer.YesNo(true))
        alice.chooses("Forest")
        expect {
            alice.graveyard has "Forest"
            alice.hand.names shouldBe listOf("Storm Spark", "Island", "Swamp")
        }
        alice.activates("Grim Homecoming")
        expect { alice.graveyard has "Grim Homecoming" }
        allPass()

        expect { "Elvish Seer" hasKeyword Keyword.Haste }
    }

    /** Declining the discard draws nothing. */
    @Test
    fun grimHomecomingDeclined() = scenario {
        alice.battlefield("Grim Homecoming")
        alice.hand("Forest")
        alice.library("Island")
        alice.castsWithoutCosts("Grim Homecoming")
        resolveStack()
        answer(Answer.YesNo(false))
        expect { alice.hand.names shouldBe listOf("Forest") }
    }

    /** From your graveyard: a nontoken creature of yours dying makes a Zombie; an opponent's creature dying exiles it. */
    @Test
    fun stairwayFromTheDepths() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.battlefield("Elvish Seer")
        bob.battlefield("Umbral Elves")
        alice.graveyard("Stairway from the Depths")
        alice.hand("Storm Spark", "Storm Spark")

        alice.casts("Storm Spark", target = "Elvish Seer")
        allPass()
        expect { stack.names shouldBe listOf("ability of Stairway from the Depths") }
        allPass()
        expect { card("Zombie Token").power shouldBe 2 }
        // An opponent's creature dying exiles the card
        alice.castsWithoutCosts("Storm Spark", target = "Umbral Elves")
        resolve()
        resolve()

        expect {
            alice.exile has "Stairway from the Depths"
            alice.battlefield.names.count { it == "Zombie Token" } shouldBe 1
        }
    }

    /** On the battlefield it does nothing: its abilities work only from the graveyard (CR 113.6k). */
    @Test
    fun stairwayFromTheDepthsOnTheBattlefield() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Elvish Seer")
        alice.hand("Stairway from the Depths", "Storm Spark")

        alice.casts("Stairway from the Depths")
        allPass()
        alice.castsWithoutCosts("Storm Spark", target = "Elvish Seer")
        resolve()

        expect {
            alice.graveyard has "Elvish Seer"
            stack.isEmpty()
            alice.battlefield lacks "Zombie Token"
        }
    }

    /** Whenever a creature dies, its controller may draw a card. */
    @Test
    fun fertilePassing() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Mountain")
        bob.battlefield("Umbral Elves")
        alice.hand("Fertile Passing", "Storm Spark")
        bob.library("Swamp")

        alice.casts("Fertile Passing")
        allPass()
        alice.casts("Storm Spark", target = "Umbral Elves")
        allPass()
        allPass()
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))

        expect { bob.hand.names shouldBe listOf("Swamp") }
    }

    /** Creature tokens tap for any colour; creature cards leaving your graveyard make a Plant and grow every Plant. */
    @Test
    fun sinisterRootwork() = scenario {
        alice.battlefield("Swamp", "Forest", "Forest", "Forest", "Forest")
        alice.graveyard("Elvish Seer")
        alice.hand("Sinister Rootwork", "Undying Chronicler")
        alice.library("Island", "Island")
        bob.library("Island", "Island")

        alice.casts("Sinister Rootwork")
        allPass()
        alice.castsWithoutCosts("Undying Chronicler")
        resolveStack()
        answer(Answer.YesNo(true))
        resolveStack()
        expect {
            alice.hand has "Elvish Seer"
            card("Plant Token").power shouldBe 1
            card("Plant Token").toughness shouldBe 2
            card("Plant Token").colors shouldBe listOf(Color.Green)
        }
        // The Plant token taps for mana of any colour once it isn't summoning sick
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates("Plant Token", color = Color.Red)

        expect { card("Plant Token").tapped shouldBe true }
        assertEquals(listOf(Color.Red), alice.pool.map { it.color })
    }

    /** X: each creature gets -X/-X; your creatures dying drain opponents, theirs dying gains you life. */
    @Test
    fun theBonehookSlaughter() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Elvish Seer")
        bob.battlefield("Umbral Elves", "Wall of Portents")
        alice.hand("The Bonehook Slaughter")

        alice.casts("The Bonehook Slaughter", x = 1)
        allPass()
        allPass()
        expect {
            "Wall of Portents".power shouldBe -1
            "Wall of Portents".toughness shouldBe 3
            alice.graveyard has "Elvish Seer"
            bob.graveyard has "Umbral Elves"
        }
        // Both of Alice's triggers trigger at once (CR 603.3b)
        answer(Answer.Order(listOf(0, 1)))
        allPass()
        allPass()

        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
            stack.isEmpty()
        }
    }

    /** Revolt: a permanent of yours left this turn makes a Servo at your end step; {1}, sacrifice a creature: scry 1. */
    @Test
    fun concealedArmory() = scenario {
        alice.battlefield("Plains", "Swamp", "Swamp", "Elvish Seer")
        alice.hand("Concealed Armory")
        alice.library("Island", "Forest")

        alice.casts("Concealed Armory")
        allPass()
        alice.activates("Concealed Armory", choosing = listOf("Elvish Seer"))
        allPass()
        alice.chooses("Island")
        expect { alice.library.names shouldBe listOf("Forest", "Island") }
        advanceTo(TurnStep.End)
        expect { stack.names shouldBe listOf("ability of Concealed Armory") }
        allPass()

        expect {
            card("Servo Token").types shouldBe listOf(CardType.Artifact, CardType.Creature)
            card("Servo Token").power shouldBe 1
        }
    }

    /** Revolt: nothing left the battlefield: no Servo. */
    @Test
    fun concealedArmoryWithoutRevolt() = scenario {
        alice.battlefield("Concealed Armory")
        advanceTo(TurnStep.End)
        expect { stack.isEmpty() }
    }

    /** Landfall exiles the top card, which you may play; at your end step the cards still exiled go to the graveyard
     * and deal that much damage to each opponent. */
    @Test
    fun moltenPeakExpedition() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Molten Peak Expedition", "Island")
        alice.library("Storm Spark", "Island")

        alice.casts("Molten Peak Expedition")
        allPass()
        alice.plays("Island")
        allPass()
        expect { alice.exile has "Storm Spark" }
        // The exiled card may be played
        alice.casts("Storm Spark", target = "bob")
        allPass()
        expect { bob.life shouldBe 17 }
    }

    /** The exiled cards still there at the end step go to the graveyard and deal that much damage. */
    @Test
    fun moltenPeakExpeditionEndStep() = scenario {
        alice.battlefield("Molten Peak Expedition")
        alice.hand("Mountain")
        alice.library("Island", "Swamp")

        alice.plays("Mountain")
        allPass()
        expect { alice.exile has "Island" }
        advanceTo(TurnStep.End)
        allPass()

        expect {
            alice.graveyard has "Island"
            bob.life shouldBe 19
        }
    }

    /** Casting a noncreature spell: your creatures get +1/+1 and untap; you may draw, then discard. */
    @Test
    fun peakAscendancy() = scenario {
        alice.battlefield("Island", "Mountain", "Plains", "Mountain")
        alice.battlefield("Elvish Seer", tapped = true)
        alice.hand("Peak Ascendancy", "Storm Spark", "Forest")
        alice.library("Island")

        alice.casts("Peak Ascendancy")
        allPass()
        alice.casts("Storm Spark", target = "bob")
        answer(Answer.Order(listOf(1, 0)))
        allPass()
        expect {
            "Elvish Seer".power shouldBe 2
            "Elvish Seer".tapped shouldBe false
        }
        allPass()
        answer(Answer.YesNo(true))
        alice.chooses("Forest")
        allPass()

        expect {
            alice.hand.names shouldBe listOf("Island")
            alice.graveyard has "Forest"
            bob.life shouldBe 17
        }
    }

    /** [0]: a loyalty counter on each red planeswalker you control. */
    @Test
    fun igniaAddsLoyalty() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Ignia, Acolyte of Embers")

        alice.casts("Ignia, Acolyte of Embers")
        allPass()
        expect { "Ignia, Acolyte of Embers".counters(CounterKind.Loyalty) shouldBe 4 }
        alice.activates("Ignia, Acolyte of Embers", ability = 0)
        allPass()

        expect { "Ignia, Acolyte of Embers".counters(CounterKind.Loyalty) shouldBe 5 }
    }

    /** [0]: two hasty 1/1 red Elementals, sacrificed at the beginning of the next end step. */
    @Test
    fun igniaMakesElementals() = scenario {
        alice.battlefield("Ignia, Acolyte of Embers", counters = listOf(CounterKind.Loyalty to 4))

        alice.activates("Ignia, Acolyte of Embers", ability = 1)
        allPass()
        expect {
            alice.battlefield.names.count { it == "Elemental Token" } shouldBe 2
            card("Elemental Token") hasKeyword Keyword.Haste
            card("Elemental Token").colors shouldBe listOf(Color.Red)
            "Ignia, Acolyte of Embers".counters(CounterKind.Loyalty) shouldBe 4
        }
        advanceTo(TurnStep.End)
        allPass()

        expect { alice.battlefield.names.none { it == "Elemental Token" } shouldBe true }
    }

    /** [−2]: cast an instant or sorcery with mana value 3 or less from your graveyard; it's exiled afterwards. */
    @Test
    fun igniaRecastsFromTheGraveyard() = scenario {
        alice.battlefield("Mountain", "Ignia, Acolyte of Embers", counters = listOf(CounterKind.Loyalty to 4))
        alice.graveyard("Storm Spark")

        alice.activates("Ignia, Acolyte of Embers", ability = 2, target = "Storm Spark")
        expect { "Ignia, Acolyte of Embers".counters(CounterKind.Loyalty) shouldBe 2 }
        allPass()
        answer(Answer.YesNo(true))
        // CR 601.2: cast now, paying its costs
        answer(Answer.Targets(listOf(target("bob"))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect {
            stack.names shouldBe listOf("Storm Spark")
            "Mountain".tapped shouldBe true
        }
        allPass()

        expect {
            bob.life shouldBe 17
            alice.exile has "Storm Spark"
        }
    }

    /** [+1]: draw, then discard unless you attacked this turn; it phases out at your end step the turn it entered. */
    @Test
    fun renjiKagehara() = scenario {
        alice.battlefield("Island", "Swamp", "Swamp")
        alice.hand("Renji Kagehara", "Forest")
        alice.library("Island", "Island")

        alice.casts("Renji Kagehara")
        allPass()
        alice.activates("Renji Kagehara", ability = 0)
        allPass()
        alice.chooses("Forest")
        expect {
            "Renji Kagehara".counters(CounterKind.Loyalty) shouldBe 4
            alice.graveyard has "Forest"
            alice.hand.names shouldBe listOf("Island")
        }
        advanceTo(TurnStep.End)
        allPass()

        assertTrue(state.objOrThrow(card("Renji Kagehara")).phasedOut)
    }

    /** [+1] after attacking: no discard. */
    @Test
    fun renjiKageharaAfterAttacking() = scenario {
        alice.battlefield("Elvish Seer", "Renji Kagehara", counters = listOf(CounterKind.Loyalty to 3))
        alice.hand("Forest")
        alice.library("Island")

        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Elvish Seer")
        advanceTo(TurnStep.PostcombatMain)
        alice.activates("Renji Kagehara", ability = 0)
        allPass()

        expect { alice.hand.names.toSet() shouldBe setOf("Forest", "Island") }
    }

    /** [−2]: a 1/1 blue Ninja token that can't be blocked. */
    @Test
    fun renjiKageharaMakesANinja() = scenario {
        alice.battlefield("Renji Kagehara", counters = listOf(CounterKind.Loyalty to 3))
        alice.activates("Renji Kagehara", ability = 1)
        allPass()
        expect {
            "Renji Kagehara".counters(CounterKind.Loyalty) shouldBe 1
            card("Ninja Token").colors shouldBe listOf(Color.Blue)
            card("Ninja Token").view.has { it is arcanum.api.Mechanic.CantBeBlocked } shouldBe true
        }
    }

    /** [−7]: an emblem: your creatures dealing combat damage to a player fetch a blue or black creature card. */
    @Test
    fun renjiKageharaEmblem() = scenario {
        alice.battlefield("Elvish Seer", "Renji Kagehara", counters = listOf(CounterKind.Loyalty to 7))
        alice.library("Forest", "Venomous Owlet", "Island", hidden = true)
        alice.activates("Renji Kagehara", ability = 2)
        allPass()
        expect { alice.graveyard has "Renji Kagehara" }

        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Elvish Seer")
        advanceTo(TurnStep.CombatDamage)
        allPass()
        alice.chooses("Venomous Owlet")

        expect {
            bob.life shouldBe 19
            alice.battlefield has "Venomous Owlet"
            alice.library.size shouldBe 2
        }
    }

    /** Casting a noncreature spell makes a Servo; [−2]: an artifact becomes a copy of another artifact or creature of
     * yours until end of turn, and stays an artifact. */
    @Test
    fun orlaGildedArtificer() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain", "Mountain", "Elvish Seer")
        alice.hand("Orla, Gilded Artificer", "Storm Spark")

        alice.casts("Orla, Gilded Artificer")
        allPass()
        expect { "Orla, Gilded Artificer".counters(CounterKind.Loyalty) shouldBe 5 }
        alice.casts("Storm Spark", target = "bob")
        expect { stack.names shouldBe listOf("Storm Spark", "ability of Orla, Gilded Artificer") }
        allPass()
        allPass()
        expect { alice.battlefield has "Servo Token" }
        alice.activates("Orla, Gilded Artificer", ability = 0, targets = listOf("Servo Token", "Elvish Seer"))
        allPass()

        expect {
            "Orla, Gilded Artificer".counters(CounterKind.Loyalty) shouldBe 3
            alice.battlefield.names.count { it == "Elvish Seer" } shouldBe 1
            // CR 707.9b: the copy is an artifact in addition to its other types
            card("Servo Token").types.toSet() shouldBe setOf(CardType.Artifact, CardType.Creature)
            card("Servo Token").view.name shouldBe "Elvish Seer"
        }
    }
}
