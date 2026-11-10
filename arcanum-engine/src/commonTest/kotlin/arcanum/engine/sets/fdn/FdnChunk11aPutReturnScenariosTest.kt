package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/**
 * Chunk 11a played: the invented names carry the oracle text of Felidar Savior, Genesis Wave, Ramos Dragon Engine, Rise of the Dark Realms,
 * Spinner of Souls, Fiendish Panda, River's Rebuke and Loot, Exuberant Explorer.
 */
class FdnChunk11aPutReturnScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val savior = Cards.invent("Lanternlit Savior", "{3}{W}", "Creature — Cat Beast", """
        Lifelink (Damage dealt by this creature also causes you to gain that much life.)
        When this creature enters, put a +1/+1 counter on each of up to two other target creatures you control.
    """, "2/3")
    private val wave = Cards.invent("Tidal Surge Wave", "{X}{G}{G}{G}", "Sorcery",
        "Reveal the top X cards of your library. You may put any number of permanent cards with mana value X or less from among them onto the battlefield. Then put all cards revealed this way that weren't put onto the battlefield into your graveyard.")
    private val engine = Cards.invent("Prismara, Dragon Engine", "{6}", "Legendary Artifact Creature — Dragon", """
        Flying
        Whenever you cast a spell, put a +1/+1 counter on Prismara for each of that spell's colors.
    """, "4/4")
    private val rite = Cards.invent("Grave Dominion Rite", "{7}{B}{B}", "Sorcery", "Put all creature cards from all graveyards onto the battlefield under your control.")
    private val spinner = Cards.invent("Soulweb Spinner", "{2}{G}", "Creature — Spider Spirit", """
        Reach
        Whenever another nontoken creature you control dies, you may reveal cards from the top of your library until you reveal a creature card. Put that card into your hand and the rest on the bottom of your library in a random order.
    """, "4/3")
    private val panda = Cards.invent("Spiteful Panda", "{2}{W}{B}", "Creature — Bear Demon", """
        Whenever you gain life, put a +1/+1 counter on this creature.
        When this creature dies, return another target non-Bear creature card with mana value less than or equal to this creature's power from your graveyard to the battlefield.
    """, "3/2")
    private val rebuke = Cards.invent("Floodtide Rebuke", "{4}{U}{U}", "Sorcery", "Return all nonland permanents target player controls to their owner's hand.")
    private val foxfire = Cards.invent("Foxfire, Eager Explorer", "{2}{G}", "Legendary Creature — Beast Noble",
        "{4}{G}{G}, {T}: Look at the top six cards of your library. You may reveal a creature card with mana value less than or equal to the number of lands you control from among them and put it onto the battlefield. Put the rest on the bottom in a random order.", "1/4")

    private val cub = Cards.invent("Marrowdeep Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val giant = Cards.invent("Marrowdeep Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    private val bear = Cards.invent("Marrowdeep Grizzly", "{2}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Marrowdeep Ogre", "{3}{B}", "Creature — Ogre", "", "3/3")
    private val imp = Cards.invent("Marrowdeep Imp", "{1}{B}", "Creature — Imp", "", "1/1")
    private val relic = Cards.invent("Marrowdeep Relic", "{2}", "Artifact", "")
    private val twinhue = Cards.invent("Marrowdeep Twinhue", "{W}{U}", "Creature — Spirit", "", "1/1")
    private val cull = Cards.invent("Marrowdeep Cull", "{B}", "Instant", "Destroy target creature.")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 115.4, 122.1: a counter on each chosen creature, none on the Savior itself
    @Test
    fun saviorPutsACounterOnEachTarget() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", cub, bear)
        alice.hand(savior)
        alice.casts(savior)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(cub), target(bear))))
        resolveAll()
        expect {
            cub.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 1
            savior.counters(p1p1) shouldBe 0
        }
    }

    // CR 115.4 ("up to"): one target is enough
    @Test
    fun saviorMayChooseOneTarget() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", cub, bear)
        alice.hand(savior)
        alice.casts(savior)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(cub))))
        resolveAll()
        expect {
            cub.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 0
        }
    }

    // CR 107.3, 701.20a, 400.7: X = 2 reveals two cards; only the permanent with mana value 2 or less may enter, the rest are put into the graveyard
    @Test
    fun waveRevealsXAndPutsTheRestInTheGraveyard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(wave)
        alice.library(cub, giant, "Island")
        alice.casts(wave, x = 2)
        resolveAll()
        alice.chooses(cub)
        resolveAll()
        expect {
            cub.zone shouldBe Zone.Battlefield
            giant.zone shouldBe Zone.Graveyard
            alice.library has "Island"
        }
    }

    // CR 608.2c: "any number" includes none: everything revealed goes to the graveyard
    @Test
    fun waveMayPutNothingOntoTheBattlefield() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(wave)
        alice.library(cub, bear, "Island")
        alice.casts(wave, x = 2)
        resolveAll()
        alice.chooses()
        resolveAll()
        expect {
            alice.graveyard has cub
            alice.graveyard has bear
            alice.library has "Island"
        }
    }

    // CR 105.1, 122.1: a two-colour spell gives two counters, a colourless one none
    @Test
    fun prismaraCountsTheColorsOfEachSpell() = scenario {
        alice.battlefield(engine, "Plains", "Island", "Swamp", "Swamp")
        alice.hand(twinhue, relic)
        alice.casts(twinhue)
        resolveAll()
        expect { engine.counters(p1p1) shouldBe 2 }
        alice.casts(relic)
        resolveAll()
        expect { engine.counters(p1p1) shouldBe 2 }
    }

    // CR 400.7, 108.3: every creature card of every graveyard enters under the caster's control
    @Test
    fun riteReturnsEveryCreatureCardFromEveryGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(rite)
        alice.graveyard(cub, relic)
        bob.graveyard(giant)
        alice.casts(rite)
        resolveAll()
        expect {
            cub.zone shouldBe Zone.Battlefield
            giant.zone shouldBe Zone.Battlefield
            giant.controller shouldBe alice.id
            relic.zone shouldBe Zone.Graveyard
        }
    }

    // CR 701.20a, 401.4: reveal until a creature, it goes to hand, the lands revealed go to the bottom
    @Test
    fun spinnerFindsTheNextCreatureWhenAnotherOneDies() = scenario {
        alice.battlefield(spinner, cub, "Swamp")
        alice.hand(cull)
        alice.library("Forest", "Forest", giant, "Island")
        alice.casts(cull, target = cub)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.hand has giant
            alice.hand.size shouldBe 1
            alice.library has "Island"
            alice.library has "Forest"
        }
    }

    // CR 603.2: "another": the Spinner's own death doesn't trigger it
    @Test
    fun spinnerDoesNotTriggerOnItsOwnDeath() = scenario {
        alice.battlefield(spinner, "Swamp")
        alice.hand(cull)
        alice.library(giant, "Island")
        alice.casts(cull, target = spinner)
        resolveAll()
        expect {
            alice.hand.size shouldBe 0
            alice.library has giant
        }
    }

    // CR 608.2h, 202.3: its power as it last existed bounds the mana value; a Bear and the Panda itself are not returnable
    @Test
    fun pandaReturnsANonBearWithinItsPower() = scenario {
        alice.battlefield(panda, "Swamp")
        alice.hand(cull)
        alice.graveyard(imp, ogre, bear)
        alice.casts(cull, target = panda)
        resolveAll()
        resolveAll()
        expect {
            imp.zone shouldBe Zone.Battlefield
            ogre.zone shouldBe Zone.Graveyard
            bear.zone shouldBe Zone.Graveyard
            panda.zone shouldBe Zone.Graveyard
        }
    }

    // CR 608.2h, 202.3: a creature card with mana value above its power (4 > 3) can't be chosen, a Bear (3) can't either
    @Test
    fun pandaCannotReturnTooBigACardOrABear() = scenario {
        alice.battlefield(panda, "Swamp")
        alice.hand(cull)
        alice.graveyard(ogre, bear)
        alice.casts(cull, target = panda)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            bear.zone shouldBe Zone.Graveyard
        }
    }

    // CR 115.1, 400.3: only the chosen player's nonland permanents return; lands and the other player's permanents stay
    @Test
    fun rebukeReturnsOnlyTheTargetPlayersNonlandPermanents() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", cub)
        alice.hand(rebuke)
        bob.battlefield("Forest", giant, relic)
        alice.casts(rebuke, target = "bob")
        resolveAll()
        expect {
            giant.zone shouldBe Zone.Hand
            relic.zone shouldBe Zone.Hand
            "Forest".zone shouldBe Zone.Battlefield
            cub.zone shouldBe Zone.Battlefield
        }
    }

    // CR 602.2, 701.20, 401.4: the creature with mana value at most the number of lands enters; too big a creature may not be chosen
    @Test
    fun foxfirePutsAnAffordableCreatureOntoTheBattlefield() = scenario {
        alice.battlefield(foxfire, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.library(giant, cub, "Island", "Island", "Island", "Island", "Swamp")
        alice.activates(foxfire)
        resolveAll()
        alice.chooses(cub)
        resolveAll()
        expect {
            cub.zone shouldBe Zone.Battlefield
            giant.zone shouldBe Zone.Library
            alice.library has "Swamp"
        }
    }
}
