package arcanum.engine.sets.fdn

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/**
 * The FDN cards that draw cards, played: the invented names carry the oracle text of Arcanis the Omnipotent, Drogskol Reaver, Garruk's
 * Uprising, Gnarlback Rhino, Mentor of the Meek, Phyrexian Arena, Scrawling Crawler, Spectral Sailor, Strix Lookout and Vampire Gourmand.
 */
class FdnChunk21DrawScenariosTest {
    private val voltaris = Cards.invent("Voltaris the All-Knowing", "{3}{U}{U}{U}", "Legendary Creature — Wizard", """
        {T}: Draw three cards.
        {2}{U}{U}: Return Voltaris to its owner's hand.
    """, "3/4")
    private val reaver = Cards.invent("Gravewisp Reaver", "{5}{W}{U}", "Creature — Spirit", """
        Flying
        Double strike (This creature deals both first-strike and regular combat damage.)
        Lifelink (Damage dealt by this creature also causes you to gain that much life.)
        Whenever you gain life, draw a card.
    """, "3/5")
    private val uprising = Cards.invent("Thornwake Uprising", "{2}{G}", "Enchantment", """
        When this enchantment enters, if you control a creature with power 4 or greater, draw a card.
        Creatures you control have trample. (Each of those creatures can deal excess combat damage to the player or planeswalker it's attacking.)
        Whenever a creature you control with power 4 or greater enters, draw a card.
    """)
    private val rhino = Cards.invent("Thornback Rhino", "{2}{G}{G}", "Creature — Rhino", """
        Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)
        Whenever you cast a spell that targets this creature, draw a card.
    """, "4/4")
    private val mender = Cards.invent("Mender of the Meek", "{2}{W}", "Creature — Human Soldier",
        "Whenever another creature you control with power 2 or less enters, you may pay {1}. If you do, draw a card.", "2/2")
    private val arcade = Cards.invent("Phyrexian Arcade", "{1}{B}{B}", "Enchantment", "At the beginning of your upkeep, you draw a card and lose 1 life.")
    private val crawler = Cards.invent("Scribbling Crawler", "{3}", "Artifact Creature — Phyrexian Construct", """
        At the beginning of your upkeep, each player draws a card.
        Whenever an opponent draws a card, that player loses 1 life.
    """, "3/2")
    private val mariner = Cards.invent("Spectral Mariner", "{U}", "Creature — Spirit Pirate", """
        Flash (You may cast this spell any time you could cast an instant.)
        Flying
        {3}{U}: Draw a card.
    """, "1/1")
    private val watcher = Cards.invent("Stryx Watcher", "{1}{U}", "Creature — Bird", """
        Flying, vigilance (Attacking doesn't cause this creature to tap.)
        {1}{U}, {T}: Draw a card, then discard a card.
    """, "1/2")
    private val glutton = Cards.invent("Vampire Glutton", "{1}{B}", "Creature — Vampire",
        "Whenever this creature attacks, you may sacrifice another creature. If you do, draw a card and this creature can't be blocked this turn.", "2/2")

    private val cub = Cards.invent("Hollowtree Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val brute = Cards.invent("Hollowtree Brute", "{1}{G}{G}", "Creature — Ogre", "", "4/4")
    private val imp = Cards.invent("Hollowtree Imp", "{B}", "Creature — Imp", "", "1/1")
    private val aid = Cards.invent("Soothing Draught", "{W}", "Instant", "You gain 3 life.")
    private val boost = Cards.invent("Quiet Growth", "{G}", "Instant", "Target creature gets +1/+1 until end of turn.")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.stock() {
        alice.library("Island", "Forest", "Plains", "Swamp", "Mountain")
        bob.library("Island", "Forest", "Plains", "Swamp", "Mountain")
    }

    // CR 602.2, 121.1: three cards from the top of the library
    @Test
    fun voltarisDrawsThreeWhenTapped() = scenario {
        stock()
        alice.battlefield(voltaris)
        alice.activates(voltaris)
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            card(voltaris).tapped shouldBe true
        }
    }

    // CR 400.3: back to its owner's hand
    @Test
    fun voltarisReturnsToHand() = scenario {
        alice.battlefield(voltaris, "Island", "Island", "Island", "Island")
        alice.activates(voltaris, ability = 1)
        resolveAll()
        expect { alice.hand has voltaris }
    }

    // CR 603.2, 119.3: each life gain triggers once
    @Test
    fun reaverDrawsWhenYouGainLife() = scenario {
        stock()
        alice.battlefield(reaver, "Plains")
        alice.hand(aid)
        alice.casts(aid)
        resolveAll()
        expect {
            alice.life shouldBe 23
            alice.hand.size shouldBe 1
        }
    }

    // CR 603.4: the condition holds on entering; CR 208.1: power 4 or greater
    @Test
    fun uprisingDrawsOnEnteringWithABigCreature() = scenario {
        stock()
        alice.battlefield(brute, "Forest", "Forest", "Forest")
        alice.hand(uprising)
        alice.casts(uprising)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            card(brute) hasKeyword Keyword.Trample.kind
        }
    }

    // CR 603.4: no big creature, the trigger doesn't trigger
    @Test
    fun uprisingDoesNotDrawWithoutABigCreature() = scenario {
        stock()
        alice.battlefield(cub, "Forest", "Forest", "Forest")
        alice.hand(uprising)
        alice.casts(uprising)
        resolveAll()
        expect {
            alice.hand.size shouldBe 0
            card(cub) hasKeyword Keyword.Trample.kind
        }
    }

    // CR 603.6a: a creature with power 4 or greater entering; a smaller one does not
    @Test
    fun uprisingDrawsForBigCreaturesEnteringOnly() = scenario {
        stock()
        alice.battlefield(uprising, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(cub, brute)
        alice.casts(cub)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
        alice.casts(brute)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 601.2c, 603.2: a spell that targets this creature
    @Test
    fun rhinoDrawsWhenYouCastASpellTargetingIt() = scenario {
        stock()
        alice.battlefield(rhino, cub, "Forest")
        alice.hand(boost, boost)
        alice.casts(boost, target = rhino)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 601.2c: targeting another creature doesn't trigger
    @Test
    fun rhinoIgnoresSpellsTargetingOthers() = scenario {
        stock()
        alice.battlefield(rhino, cub, "Forest")
        alice.hand(boost)
        alice.casts(boost, target = cub)
        resolveAll()
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 117.12, 118.1: the optional payment, then the draw
    @Test
    fun menderDrawsWhenYouPayForASmallCreature() = scenario {
        stock()
        alice.battlefield(mender, "Forest", "Forest", "Forest")
        alice.hand(cub)
        alice.casts(cub)
        resolveAll()
        answer(Answer.YesNo(true))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.battlefield has cub
        }
    }

    // CR 117.12: declining the payment draws nothing
    @Test
    fun menderDrawsNothingWhenYouDecline() = scenario {
        stock()
        alice.battlefield(mender, "Forest", "Forest", "Forest")
        alice.hand(cub)
        alice.casts(cub)
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 208.1: power 3 or more doesn't trigger
    @Test
    fun menderIgnoresBigCreatures() = scenario {
        stock()
        alice.battlefield(mender, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(brute)
        alice.casts(brute)
        resolveAll()
        expect {
            pending shouldBe null
            alice.hand.size shouldBe 0
        }
    }

    // CR 503.1a, 121.1, 119.3: the draw and the life loss, in your upkeep only
    @Test
    fun arcadeDrawsAndCostsLifeInYourUpkeep() = scenario {
        stock()
        alice.battlefield(arcade)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.life shouldBe 19
        }
    }

    @Test
    fun arcadeDoesNothingInTheOpponentsUpkeep() = scenario {
        stock()
        alice.battlefield(arcade)
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect {
            alice.hand.size shouldBe 0
            alice.life shouldBe 20
        }
    }

    // CR 121.1, 603.2: each player draws; only the opponent's draws cost life. Bob also drew in his own turn 2 draw step, which the Crawler punished
    @Test
    fun crawlerEveryoneDrawsAndTheOpponentLosesLife() = scenario {
        stock()
        alice.battlefield(crawler)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            bob.hand.size shouldBe 2
            alice.life shouldBe 20
            bob.life shouldBe 18
        }
    }

    // CR 504.1: the draw step draw by the opponent is also an opponent drawing
    @Test
    fun crawlerPunishesTheOpponentsDrawStepToo() = scenario {
        stock()
        alice.battlefield(crawler)
        advanceTo(TurnStep.Draw, turn = 2)
        resolveAll()
        expect {
            bob.hand.size shouldBe 1
            bob.life shouldBe 19
            alice.life shouldBe 20
        }
    }

    // CR 602.1: a mana ability cost only
    @Test
    fun marinerDrawsForMana() = scenario {
        stock()
        alice.battlefield(mariner, "Island", "Island", "Island", "Island")
        alice.activates(mariner)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 702.20a, 508.1f: attacking doesn't tap a vigilant creature, so it can still pay {T} afterwards
    @Test
    fun watcherAttacksAndThenLoots() = scenario {
        stock()
        alice.battlefield(watcher, "Island", "Island")
        alice.hand(cub)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(watcher)
        expect { card(watcher).tapped shouldBe false }
        alice.activates(watcher)
        resolveAll()
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(decision<Decision.ChooseObjects>().options.take(1)))
        resolveAll()
        expect {
            card(watcher).tapped shouldBe true
            alice.hand.size shouldBe 1
            alice.graveyard.size shouldBe 1
        }
    }

    // CR 608.2c: draw first, then discard
    @Test
    fun watcherDrawsThenDiscards() = scenario {
        stock()
        alice.battlefield(watcher, "Island", "Island")
        alice.hand(cub)
        alice.activates(watcher)
        resolveAll()
        val d = decision<Decision.ChooseObjects>()
        answer(Answer.Objects(d.options.take(1)))
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.graveyard.size shouldBe 1
        }
    }

    // CR 117.12, 701.21a, 509.1b: sacrifice, then draw and become unblockable
    @Test
    fun gluttonSacrificesToDrawAndBecomeUnblockable() = scenario {
        stock()
        alice.battlefield(glutton, imp)
        bob.battlefield(cub)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(glutton)
        resolveAll()
        answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(decision<Decision.ChooseObjects>().options.take(1)))
        resolveAll()
        expect {
            alice.graveyard has imp
            alice.hand.size shouldBe 1
            glutton hasMechanic { it == arcanum.api.Mechanic.CantBeBlocked }
        }
    }

    // CR 117.12: declining keeps the creature and the cards
    @Test
    fun gluttonDecliningDrawsNothing() = scenario {
        stock()
        alice.battlefield(glutton, imp)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(glutton)
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.battlefield has imp
            alice.hand.size shouldBe 0
            glutton lacksMechanic { it == arcanum.api.Mechanic.CantBeBlocked }
        }
    }
}
