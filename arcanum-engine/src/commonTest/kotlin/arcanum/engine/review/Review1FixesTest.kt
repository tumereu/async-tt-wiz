package arcanum.engine.review

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Objects
import arcanum.engine.cards.Cards
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.EventPattern
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.PlayerFilter
import arcanum.api.RecipientFilter
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** Review 1, around its fixes: events of costs and the cleanup step through the event pipeline, counters as they enter,
 * prevention among replacements, trigger counting and "until" returns. */
class Review1FixesTest {
    /** "If a creature would be put into a graveyard, its controller may exile it instead." */
    private val mayExile = Mechanic.Replacement(
        EventPattern.ZoneChange(ObjectFilter.Type(arcanum.api.CardTypeRef.Literal(CardType.Creature)), to = Zone.Graveyard),
        modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))), optional = true,
    )

    /** A replacement with a choice on the event of paying a cost: the payment waits for it, then finishes. */
    // CR 601.2h, 614.1a, 701.21a
    @Test
    fun paymentWaitsForAReplacementChoice() = scenario {
        alice.battlefield("Corpse Gnawer", "Thicketwood Elves")
        effect(alice, mayExile, on = listOf("Corpse Gnawer"))
        alice.activates("Corpse Gnawer", choosing = listOf("Thicketwood Elves"))
        assertEquals(alice.id, decision<Decision.May>().player)
        expect { stack.isEmpty() }
        answer(Answer.YesNo(true))
        expect {
            alice.exile has "Thicketwood Elves"
            stack.size shouldBe 1
        }
    }

    /** The payment waiting for that choice survives a JSON round trip and can still be cancelled whole. */
    // CR 601.2h; D58
    @Test
    fun paymentWaitingForAChoiceCanBeCancelled() = scenario {
        alice.battlefield("Corpse Gnawer", "Thicketwood Elves")
        effect(alice, mayExile, on = listOf("Corpse Gnawer"))
        alice.activates("Corpse Gnawer", choosing = listOf("Thicketwood Elves"))
        Review1.sameAfterJson(state, Input.Answer(decision<Decision.May>().id, Answer.YesNo(false)))
        perform(Input.Cancel(alice.id))
        expect {
            alice.battlefield has "Thicketwood Elves"
            stack.isEmpty()
        }
        assertEquals(null, pending)
    }

    /** Declining the replacement while paying: the creature goes to the graveyard as usual. */
    // CR 614.1a, 616.1
    @Test
    fun paymentWithTheReplacementDeclined() = scenario {
        alice.battlefield("Corpse Gnawer", "Thicketwood Elves")
        effect(alice, mayExile, on = listOf("Corpse Gnawer"))
        alice.activates("Corpse Gnawer", choosing = listOf("Thicketwood Elves"))
        answer(Answer.YesNo(false))
        expect { alice.graveyard has "Thicketwood Elves" }
    }

    /** The source of an "until" sacrificed as a cost: the exiled card is back at once, while the ability waits on the
     * Stack. */
    // CR 610.3, 610.3c
    @Test
    fun untilSourceSacrificedAsACost() = scenario {
        alice.battlefield("Swamp", "Swamp", "Corpse Gnawer")
        alice.hand("Deep-Grotto Bat")
        bob.hand("Cinder Bolt", "Forest")
        alice.casts("Deep-Grotto Bat")
        allPass()
        allPass()
        alice.chooses("Cinder Bolt")
        expect { bob.exile has "Cinder Bolt" }
        alice.activates("Corpse Gnawer", choosing = listOf("Deep-Grotto Bat"))
        expect {
            bob.hand has "Cinder Bolt"
            stack.names shouldBe listOf("ability of Corpse Gnawer")
        }
    }

    /** A madness discard in the cleanup step: its trigger gives priority there, then another cleanup step, then the
     * next turn. */
    // CR 514.3a, 702.35a
    @Test
    fun cleanupMadnessThenAnotherCleanupStep() = scenario {
        turn(number = 3, step = TurnStep.End)
        alice.hand("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Scorching Mudlizard")
        bob.library("Island", "Island")
        allPass()
        answer(Answer.Objects(listOf(card("Scorching Mudlizard"))))
        expect {
            alice.exile has "Scorching Mudlizard"
            stack.size shouldBe 1
        }
        assertEquals(TurnStep.Cleanup, state.turn.step)
        assertEquals(alice.id, state.priority.holder)
        allPass()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        expect { alice.graveyard has "Scorching Mudlizard" }
        assertEquals(3, state.turn.number)
        assertEquals(TurnStep.Cleanup, state.turn.step)
        allPass()
        assertEquals(4, state.turn.number)
    }

    /** Two "enters with" counters of one kind are one entry on the permanent (CR 122.1), adding up. */
    // CR 122.6, 614.1c
    @Test
    fun enteringCountersOfOneKindAreOneEntry() = scenario {
        alice.battlefield("Elenwyn, Spinner of Dawn", "Plains", "Plains", "Plains", "Plains")
        alice.hand("Hangar Strider")
        alice.casts("Hangar Strider", x = 2)
        allPass()
        assertEquals(1, state.objOrThrow(card("Hangar Strider")).counters.size)
        expect { "Hangar Strider".power shouldBe 3 }
    }

    /** Mender of Afflictions and a creature returned "with a -1/-1 counter": one fewer, none. */
    // CR 122.6, 614.1a
    @Test
    fun menderOnACreatureEnteringWithACounter() = scenario {
        alice.battlefield("Mender of Afflictions")
        alice.battlefield("Corpse Gnawer")
        alice.battlefield("Haven Warden")
        alice.activates("Corpse Gnawer", choosing = listOf("Haven Warden"))
        allPass()
        allPass()
        expect {
            alice.battlefield has "Haven Warden"
            "Haven Warden".counters(CounterKind.PowerToughness(-1, -1)) shouldBe 0
        }
    }

    /** "Whenever you discard one or more cards for the first time each turn": the second discard of the turn (a cost)
     * doesn't trigger. */
    // CR 603.2c
    @Test
    fun firstTimeEachTurnOnlyOnce() = scenario {
        alice.battlefield("Ysolde, the Unbound Scholar", "Rancid Imp")
        alice.hand("Island", "Forest")
        alice.library("Plains", "Plains", "Plains")
        alice.activates("Rancid Imp", choosing = listOf("Island"))
        expect { stack.size shouldBe 2 }
        allPass()
        allPass()
        expect { alice.hand.size shouldBe 2 }
        alice.activates("Rancid Imp", choosing = listOf("Forest"))
        expect { stack.size shouldBe 1 }
    }

    /** "Prevent all damage to you" and a doubling replacement: the order can't matter, nothing is asked. */
    // CR 616.1e, 615.1
    @Test
    fun preventAllWithADoublerAsksNothing() = scenario {
        alice.battlefield(doubler, "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield(sanctuary)
        alice.casts("Storm Spark", target = "bob")
        allPass()
        assertEquals(null, pending)
        expect { bob.life shouldBe 20 }
    }

    /** Prevent 1 of each damage, chosen first: 3 becomes 2, doubled to 4. */
    // CR 616.1, 616.1e, 616.1f
    @Test
    fun preventionChosenBeforeDoubling() = scenario {
        alice.battlefield(doubler, "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield(buffer)
        alice.casts("Storm Spark", target = "bob")
        allPass()
        val d = decision<Decision.ChooseReplacement>()
        answer(Answer.Replacement(d.options.single { it.source == card("Review Fix Buffer") }.index))
        expect { bob.life shouldBe 16 }
    }

    /** Doubling chosen first: 6, then 1 prevented: 5. */
    // CR 616.1, 616.1e, 616.1f
    @Test
    fun doublingChosenBeforePrevention() = scenario {
        alice.battlefield(doubler, "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield(buffer)
        alice.casts("Storm Spark", target = "bob")
        allPass()
        val d = decision<Decision.ChooseReplacement>()
        answer(Answer.Replacement(d.options.single { it.source == card("Review Fix Doubler") }.index))
        expect { bob.life shouldBe 15 }
    }

    private fun permanent(name: String, type: CardType, vararg mechanics: Mechanic) =
        Cards.define(name, CardDefinition(Layout.Normal, listOf(FaceDefinition(name, ManaCost.parse("{2}"), types = listOf(type),
            abilities = listOf(Ability.Static(mechanics.toList(), Affected.Objects(Objects.Self)))))))

    private val doubler = permanent("Review Fix Doubler", CardType.Enchantment, Mechanic.Replacement(
        EventPattern.Damage(ObjectFilter.ControlledBy(PlayerFilter.You), RecipientFilter()),
        modify = listOf(Modification.MultiplyAmount(2))))

    private val buffer = permanent("Review Fix Buffer", CardType.Enchantment, Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any,
        RecipientFilter(players = PlayerFilter.You)), each = arcanum.api.Value.Fixed(1)))

    private val sanctuary = permanent("Review Fix Sanctuary", CardType.Enchantment, Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any,
        RecipientFilter(players = PlayerFilter.You))))
}
