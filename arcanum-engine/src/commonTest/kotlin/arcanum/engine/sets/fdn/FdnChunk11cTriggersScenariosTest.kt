package arcanum.engine.sets.fdn

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/**
 * Chunk 11c played: the invented names carry the oracle text of Diamond Mare, Pyromancer's Goggles, Wildwood Scourge, Predator Ooze,
 * Dictate of Kruphix and Exemplar of Light.
 */
class FdnChunk11cTriggersScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val mare = Cards.invent("Jadeglint Mare", "{2}", "Artifact Creature — Horse", """
        As this creature enters, choose a color.
        Whenever you cast a spell of the chosen color, you gain 1 life.
    """, "1/3")
    private val goggles = Cards.invent("Cinderlens Spectacles", "{5}", "Legendary Artifact",
        "{T}: Add {R}. When that mana is spent to cast a red instant or sorcery spell, copy that spell and you may choose new targets for the copy.")
    private val scourge = Cards.invent("Thornbrake Hydra", "{X}{G}", "Creature — Hydra", """
        This creature enters with X +1/+1 counters on it.
        Whenever one or more +1/+1 counters are put on another non-Hydra creature you control, put a +1/+1 counter on this creature.
    """, "0/0")
    private val ooze = Cards.invent("Gullet Ooze", "{G}{G}{G}", "Creature — Ooze", """
        Indestructible
        Whenever this creature attacks, put a +1/+1 counter on it.
        Whenever a creature dealt damage by this creature this turn dies, put a +1/+1 counter on this creature.
    """, "1/1")
    private val decree = Cards.invent("Decree of Plenty", "{1}{U}{U}", "Enchantment", """
        Flash
        At the beginning of each player's draw step, that player draws an additional card.
    """)
    private val paragon = Cards.invent("Paragon of Dawn", "{2}{W}{W}", "Creature — Angel", """
        Flying
        Whenever you gain life, put a +1/+1 counter on this creature.
        Whenever you put one or more +1/+1 counters on this creature, draw a card. This ability triggers only once each turn.
    """, "3/3")

    private val redGain = Cards.invent("Cinderlens Balm", "{R}", "Instant", "You gain 2 life.")
    private val greenGain = Cards.invent("Cinderlens Salve", "{G}", "Instant", "You gain 2 life.")
    private val redSorcery = Cards.invent("Cinderlens Bonfire", "{R}", "Sorcery", "You gain 3 life.")
    private val plainRelic = Cards.invent("Cinderlens Trinket", "{1}", "Artifact", "")
    private val boost = Cards.invent("Cinderlens Boon", "{G}", "Instant", "Put a +1/+1 counter on target creature.")
    private val bear = Cards.invent("Cinderlens Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val cub = Cards.invent("Cinderlens Cub", "{1}{G}", "Creature — Hydra", "", "2/2")
    private val imp = Cards.invent("Cinderlens Imp", "{B}", "Creature — Imp", "", "1/1")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.stock() {
        alice.library("Island", "Forest", "Plains", "Swamp", "Mountain", "Island")
        bob.library("Island", "Forest", "Plains", "Swamp", "Mountain", "Island")
    }

    // CR 614.12, 607.2d, 603.2: only a spell of the chosen colour gains life
    @Test
    fun mareGainsLifeForTheChosenColorOnly() = scenario {
        alice.hand(mare, greenGain, redGain)
        alice.battlefield("Plains", "Plains", "Forest", "Forest", "Mountain", "Mountain")
        alice.casts(mare)
        resolveAll()
        answer(Answer.ColorChoice(Color.Green))
        resolveAll()
        alice.casts(greenGain)
        resolveAll()
        expect { alice.life shouldBe 23 }
        alice.casts(redGain)
        resolveAll()
        expect { alice.life shouldBe 25 }
    }

    // CR 106.6, 603.7a, 707.10: the red instant paid with the Goggles' mana is copied; the copy gains life too
    @Test
    fun gogglesCopyARedInstantPaidWithTheirMana() = scenario {
        alice.battlefield(goggles)
        alice.hand(redGain)
        alice.casts(redGain)
        resolveAll()
        expect { alice.life shouldBe 24 }
    }

    // CR 106.6: a red sorcery too
    @Test
    fun gogglesCopyARedSorcery() = scenario {
        alice.battlefield(goggles)
        alice.hand(redSorcery)
        alice.casts(redSorcery)
        resolveAll()
        expect { alice.life shouldBe 26 }
    }

    // CR 106.6: the mana wasn't spent on a red spell of that kind (not red): no copy
    @Test
    fun gogglesDoNotCopyAColorlessSpell() = scenario {
        alice.battlefield(goggles, imp)
        alice.hand(plainRelic)
        alice.casts(plainRelic)
        resolveAll()
        expect {
            plainRelic.zone shouldBe Zone.Battlefield
            state.stack.size shouldBe 0
            pending shouldBe null
        }
    }

    // CR 106.6: mana from a Mountain isn't the Goggles' mana: no copy
    @Test
    fun gogglesDoNotCopyWhenOtherManaPaid() = scenario {
        alice.battlefield(goggles, "Mountain")
        alice.hand(redGain)
        alice.casts(redGain, paying = listOf("Mountain"))
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    // CR 122.6, 603.2c, 205.3: a counter on another non-Hydra creature of yours grows the Scourge; on a Hydra, or the opponent's, it doesn't
    @Test
    fun scourgeGrowsWhenAnotherNonHydraGetsCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.battlefield(scourge, counters = listOf(p1p1 to 1))
        alice.hand(boost, boost)
        alice.casts(boost, target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            scourge.counters(p1p1) shouldBe 2
        }
    }

    @Test
    fun scourgeIgnoresHydrasAndOpposingCreatures() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", cub)
        alice.battlefield(scourge, counters = listOf(p1p1 to 1))
        bob.battlefield(imp)
        alice.hand(boost, boost)
        alice.casts(boost, target = cub)
        resolveAll()
        alice.casts(boost, target = imp)
        resolveAll()
        expect {
            cub.counters(p1p1) shouldBe 1
            imp.counters(p1p1) shouldBe 1
            scourge.counters(p1p1) shouldBe 1
        }
    }

    // CR 603.10a, 120.3, 702.12: the Ooze, indestructible, kills a blocker it damaged and grows (attack +1, death +1)
    @Test
    fun oozeGrowsWhenACreatureItDamagedDies() = scenario {
        alice.battlefield(ooze)
        bob.battlefield(imp)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ooze)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(imp to ooze)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            imp.zone shouldBe Zone.Graveyard
            ooze.counters(p1p1) shouldBe 2
        }
    }

    // CR 120.1: a creature the Ooze didn't damage dying gives nothing (only the attack counter)
    @Test
    fun oozeIgnoresOtherDeaths() = scenario {
        alice.battlefield(ooze, "Mountain", "Mountain")
        bob.battlefield(imp)
        alice.hand(Cards.invent("Cinderlens Zap", "{R}", "Instant", "Destroy target creature."))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ooze)
        resolveAll()
        alice.casts("Cinderlens Zap", target = imp)
        resolveAll()
        expect {
            imp.zone shouldBe Zone.Graveyard
            ooze.counters(p1p1) shouldBe 1
        }
    }

    // CR 504.1, 603.2, 109.5: each player draws an extra card in their own draw step
    @Test
    fun decreeGivesTheActivePlayerAnExtraCard() = scenario {
        stock()
        alice.battlefield(decree)
        advanceTo(TurnStep.Draw, turn = 2)
        resolveAll()
        expect { bob.hand.size shouldBe 2 }
        advanceTo(TurnStep.Draw, turn = 3)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            bob.hand.size shouldBe 2
        }
    }

    // CR 603.2, 122.6: gaining life puts a counter (a counter you put) and that draws; the limit is once each turn
    @Test
    fun exemplarDrawsOnlyOncePerTurn() = scenario {
        stock()
        alice.battlefield(paragon, "Forest", "Forest")
        alice.hand(greenGain, greenGain)
        alice.casts(greenGain)
        resolveAll()
        expect {
            paragon.counters(p1p1) shouldBe 1
            alice.hand.size shouldBe 2
        }
        alice.casts(greenGain)
        resolveAll()
        expect {
            paragon.counters(p1p1) shouldBe 2
            alice.hand.size shouldBe 1
        }
    }

    // CR 122.6: counters the opponent puts on it aren't counters you put
    @Test
    fun exemplarIgnoresCountersTheOpponentPuts() = scenario {
        stock()
        alice.battlefield(paragon)
        bob.battlefield("Forest")
        bob.hand(boost)
        advanceTo(TurnStep.Upkeep, turn = 2)
        bob.casts(boost, target = paragon)
        resolveAll()
        expect {
            paragon.counters(p1p1) shouldBe 1
            alice.hand.size shouldBe 0
        }
    }
}
