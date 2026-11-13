package arcanum.engine.sets.woe

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.msh.targetsIfAsked
import kotlin.test.Test
import kotlin.test.assertFails

/** Wilds of Eldraine fix step: general engine-visible fixes found while working the set. */
class WoeFixesScenariosTest {
    private val scorchTheSteeple = Cards.invent("Scorch the Steeple", "{R}", "Instant", """
        Bargain (You may sacrifice an artifact, enchantment, or token as you cast this spell.)
        Scorch the Steeple deals 2 damage to target creature or planeswalker. If this spell was bargained, instead it deals 3 damage to that permanent and you scry 1.
        If a permanent dealt damage by Scorch the Steeple would die this turn, exile it instead.
    """.trimIndent())
    private val wardlightPrayer = Cards.invent("Wardlight Prayer", "{W}", "Instant", "Prevent all damage that would be dealt to target creature this turn.")
    private val finalUnmaking = Cards.invent("Final Unmaking", "{B}", "Instant", "Destroy target creature.")
    private val bronzeStrider = Cards.invent("Bronze Strider", "{3}", "Artifact Creature — Golem", "", "3/3")
    private val groveWarden = Cards.invent("Grove Warden of Oaks", null, "Land Creature — Forest Dryad", "", "1/1")
    private val tidecallSurge = Cards.invent("Tidecall Surge", "{U}", "Sorcery",
        "Target land becomes a 3/3 Elemental creature with flying until end of turn. It's still a land.")
    private val wakenedFurrow = Cards.invent("Wakened Furrow", "{G}", "Instant", "Until end of turn, target land becomes a 4/4 Elemental creature.")
    private val goblinIdol = Cards.invent("Hearthbound Goblin Idol", "{2}", "Kindred Artifact — Goblin", "")
    private val gearsoulInfusion = Cards.invent("Gearsoul Infusion", "{U}", "Sorcery",
        "Put three +1/+1 counters on target artifact you control. If it's not a creature, it becomes a 0/0 Robot artifact creature.")
    private val mirrorhandMimic = Cards.invent("Mirrorhand Mimic", "{2}{U}", "Legendary Creature — Human Mercenary Villain",
        "At the beginning of your first main phase, until your next turn, Mirrorhand Mimic becomes a copy of up to one target creature on the battlefield or creature card in a graveyard, except its name is Mirrorhand Mimic and it's a legendary Human Mercenary Villain creature.",
        "3/5")

    private val vashka = Cards.invent("Vashka, Wicked Schemer", "{3}{B}{B}", "Legendary Planeswalker — Ashiok", """
        If you would pay life while your library has at least that many cards in it, exile that many cards from the top of your library instead.
        [+1]: Look at the top two cards of your library. Exile one of them and put the other into your hand.
        [−2]: Create two 1/1 black Nightmare creature tokens with "At the beginning of combat on your turn, if a card was put into exile this turn, put a +1/+1 counter on this token."
        [−7]: Target player exiles the top X cards of their library, where X is the total mana value of cards you own in exile.
    """.trimIndent())
    private val bloodtitheCharm = Cards.invent("Bloodtithe Charm", "{1}", "Artifact", "{T}, Pay 2 life: Draw a card.")
    private val thornbackOx = Cards.invent("Thornback Ox", "{3}{G}", "Creature — Ox", "", "3/3")
    private val glimmerSprite = Cards.invent("Glimmer Sprite", "{1}{U}", "Creature — Faerie", "", "1/1")
    private fun loyal(n: Int) = listOf(CounterKind.Loyalty to n)

    // CR 614.1a, 119.4: the 2 life paid is replaced by exiling the top two cards; the cost is still paid, so a card is drawn
    @Test
    fun vashkaExilesCardsInsteadOfPayingLife() = scenario {
        alice.battlefield(vashka, counters = loyal(5))
        alice.battlefield(bloodtitheCharm)
        alice.library("Plains", "Island", "Swamp", "Forest")
        alice.activates(bloodtitheCharm)
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.exile.names shouldBe listOf("Plains", "Island")
            alice.hand.names shouldBe listOf("Swamp")
            alice.library.names shouldBe listOf("Forest")
        }
    }

    // CR 614.1a: with fewer cards in the library than the life to pay, the replacement doesn't apply and the life is paid
    @Test
    fun vashkaPaysLifeWithASmallLibrary() = scenario {
        alice.battlefield(vashka, counters = loyal(5))
        alice.battlefield(bloodtitheCharm)
        alice.library("Plains")
        alice.activates(bloodtitheCharm)
        resolveAll()
        expect {
            alice.life shouldBe 18
            alice.exile.names shouldBe emptyList()
            alice.hand.names shouldBe listOf("Plains")
        }
    }

    // CR 119.4: life can be paid only with a life total at least that high; the replacement doesn't change what can be paid
    @Test
    fun vashkaDoesNotLetYouPayLifeYouDoNotHave() = scenario {
        alice.battlefield(vashka, counters = loyal(5))
        alice.battlefield(bloodtitheCharm)
        alice.library("Plains", "Island", "Swamp")
        alice.life = 1
        assertFails { alice.activates(bloodtitheCharm) }
    }

    // the +1: one of the top two exiled, the other into the hand (CR 606.4)
    @Test
    fun vashkaLooksAtTwoAndExilesOne() = scenario {
        alice.battlefield(vashka, counters = loyal(5))
        alice.library("Plains", "Island", "Swamp")
        alice.activates(vashka, ability = 0)
        resolveAll()
        alice.chooses("Island")
        resolveAll()
        expect {
            alice.exile.names shouldBe listOf("Island")
            alice.hand.names shouldBe listOf("Plains")
            alice.library.names shouldBe listOf("Swamp")
            vashka.counters(CounterKind.Loyalty) shouldBe 6
        }
    }

    // CR 603.4: the Nightmares get a counter at the beginning of combat only if a card was put into exile this turn (here by paying life
    // through the replacement, CR 614.1a)
    @Test
    fun vashkaNightmaresGrowAfterACardIsExiled() = scenario {
        alice.battlefield(vashka, counters = loyal(5))
        alice.battlefield(bloodtitheCharm)
        alice.library("Plains", "Island", "Swamp", "Forest")
        alice.activates(vashka, ability = 1)
        resolveAll()
        expect { card("Nightmare Token", 0).power shouldBe 1 }
        alice.activates(bloodtitheCharm)
        resolveAll()
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            card("Nightmare Token", 0).power shouldBe 2
            card("Nightmare Token", 1).power shouldBe 2
        }
    }

    // CR 603.4: no card was put into exile this turn, so no counter
    @Test
    fun vashkaNightmaresStaySmallWithoutExile() = scenario {
        alice.battlefield(vashka, counters = loyal(5))
        alice.activates(vashka, ability = 1)
        resolveAll()
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { card("Nightmare Token", 0).power shouldBe 1 }
    }

    // CR 107.3c: X is the total mana value of the cards Alice owns in exile (4 + 2); the target is any of three players (CR 115.1)
    @Test
    fun vashkaUltimateExilesTotalManaValue() = scenario(players = 3) {
        alice.battlefield(vashka, counters = loyal(7))
        alice.exile(thornbackOx, glimmerSprite)
        bob.exile("Plains")
        carol.library("Plains", "Island", "Swamp", "Forest", "Plains", "Island", "Swamp", "Forest")
        alice.activates(vashka, ability = 2, target = "carol")
        resolveAll()
        expect { carol.library.names shouldBe listOf("Swamp", "Forest") }
    }

    // CR 614.1a, 615.1a: the damage was prevented, so the creature was never dealt damage by the spell; dying later this turn, it goes to the
    // graveyard
    @Test
    fun scorchTheSteepleDoesNotExileWhenItsDamageWasPrevented() = scenario {
        alice.battlefield("Mountain", "Plains", "Swamp")
        bob.battlefield(bronzeStrider)
        alice.hand(wardlightPrayer, scorchTheSteeple, finalUnmaking)
        alice.casts(wardlightPrayer, target = bronzeStrider)
        resolveAll()
        alice.casts(scorchTheSteeple, target = bronzeStrider, kicked = 0)
        resolveAll()
        expect { bronzeStrider.view.obj?.damage shouldBe 0 }
        alice.casts(finalUnmaking, target = bronzeStrider)
        resolveAll()
        expect { bronzeStrider.zone shouldBe Zone.Graveyard }
    }

    // CR 614.1a: dealt 2 damage by the spell, the 3/3 survives; destroyed later this turn, it is exiled instead
    @Test
    fun scorchTheSteepleExilesACreatureItDamagedThatDiesLater() = scenario {
        alice.battlefield("Mountain", "Swamp")
        bob.battlefield(bronzeStrider)
        alice.hand(scorchTheSteeple, finalUnmaking)
        alice.casts(scorchTheSteeple, target = bronzeStrider, kicked = 0)
        resolveAll()
        expect { bronzeStrider.zone shouldBe Zone.Battlefield }
        alice.casts(finalUnmaking, target = bronzeStrider)
        resolveAll()
        expect { bronzeStrider.zone shouldBe Zone.Exile }
    }

    // CR 205.1b: "It's still a land." keeps all its prior types and subtypes, its creature type Dryad too
    @Test
    fun stillALandKeepsItsCreatureTypes() = scenario {
        alice.battlefield("Island", groveWarden)
        alice.hand(tidecallSurge)
        alice.casts(tidecallSurge, target = groveWarden)
        resolveAll()
        expect {
            groveWarden.power shouldBe 3
            groveWarden.types.toSet() shouldBe setOf(CardType.Land, CardType.Creature)
            groveWarden.subtypes.toSet() shouldBe setOf(Subtype("Forest"), Subtype("Dryad"), Subtype("Elemental"))
        }
    }

    // CR 205.1a: becoming an "Elemental creature" (no "still a land") sets its card type and creature types: it isn't a land any more, and its
    // land type goes with the land type (CR 205.1a, subtypes correlated with a removed card type)
    @Test
    fun becomingACreatureReplacesItsCardTypes() = scenario {
        alice.battlefield("Forest", "Plains")
        alice.hand(wakenedFurrow)
        alice.casts(wakenedFurrow, target = "Plains", paying = listOf("Forest"))
        resolveAll()
        expect {
            val plains = card("Plains")
            plains.power shouldBe 4
            plains.types.toSet() shouldBe setOf(CardType.Creature)
            plains.subtypes.toSet() shouldBe setOf(Subtype("Elemental"))
        }
    }

    // CR 205.1b: a "Robot artifact creature" keeps its other card types (Kindred, artifact) but its creature types are replaced
    @Test
    fun creatureTypeArtifactCreatureReplacesCreatureTypes() = scenario {
        alice.battlefield("Island", goblinIdol)
        alice.hand(gearsoulInfusion)
        alice.casts(gearsoulInfusion, target = goblinIdol)
        resolveAll()
        expect {
            goblinIdol.power shouldBe 3
            goblinIdol.types.toSet() shouldBe setOf(CardType.Kindred, CardType.Artifact, CardType.Creature)
            goblinIdol.subtypes.toSet() shouldBe setOf(Subtype("Robot"))
        }
    }

    // CR 707.9b, 205.1a: "except … it's a legendary Human Mercenary Villain creature" sets the copy's card types: a copy of an artifact
    // creature isn't an artifact
    @Test
    fun copyExceptItsACreatureSetsItsCardTypes() = scenario {
        alice.battlefield(mirrorhandMimic)
        alice.library("Island", "Island")
        bob.battlefield(bronzeStrider)
        bob.library("Island", "Island")
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        targetsIfAsked(bronzeStrider)
        resolveAll()
        expect {
            mirrorhandMimic.power shouldBe 3
            mirrorhandMimic.types.toSet() shouldBe setOf(CardType.Creature)
            mirrorhandMimic.subtypes.toSet() shouldBe setOf(Subtype("Human"), Subtype("Mercenary"), Subtype("Villain"))
        }
    }
}
