package arcanum.engine.review

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 3: costs that trigger (mana abilities with sacrifice costs, CR 605.3, 603.2), tapping summoning-sick creatures
 * for costs without {T} (CR 302.6), damage redirection (CR 614.9), creatures put onto the battlefield attacking
 * (CR 508.4, 611.2c).
 */
class Review3CostsTest {
    /** "All damage that would be dealt to you is dealt to this creature instead." (CR 614.9), written by hand: the parser
     * has no redirection wording yet. */
    private val shieldBearer = Cards.define("Review3 Shield Bearer", CardDefinition(Layout.Normal, listOf(FaceDefinition(
        name = "Review3 Shield Bearer", manaCost = ManaCost.parse("{2}{W}"), types = listOf(CardType.Creature),
        subtypes = listOf(Subtype("Soldier")), power = PrintedNumber.Fixed(0), toughness = PrintedNumber.Fixed(4),
        abilities = listOf(Ability.Static(listOf(Mechanic.Replacement(
            event = EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You)),
            modify = listOf(Modification.ChangeRecipient(Recipients(objects = Objects.Self))),
        )), Affected.Objects(Objects.Self))),
    ))))

    // ---- Costs that trigger

    /** Compleat Altar's sacrifice is a mana ability's cost: the mana comes at once, Havoc Imp's trigger waits for the next
     * time a player would get priority and then goes on the Stack. */
    // CR 605.3a, 603.3, 605.5a
    @Test
    fun aManaAbilitysSacrificeTriggersAfterIt() = scenario {
        alice.battlefield("Compleat Altar", "Havoc Imp", "Tinkered Mite")
        alice.activates("Compleat Altar", choosing = listOf("Tinkered Mite"), color = Color.Red)
        expect { alice.pool.size shouldBe 1 }
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        expect { stack.size shouldBe 1 }
        allPass()
        expect {
            bob.life shouldBe 19
            alice.pool.size shouldBe 1
        }
    }

    /** Lotus Frond sacrificed to pay for Storm Spark: Velo's trigger goes on the Stack after the spell is cast, on top
     * of it. */
    // CR 601.2i, 603.3, 605.3a
    @Test
    fun aSacrificeWhilePayingTriggersAboveTheSpell() = scenario {
        alice.battlefield("Velo, Master of Ceremonies", "Lotus Frond")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob", paying = listOf("Lotus Frond:R"))
        expect {
            stack.size shouldBe 2
            stack.names.last() shouldBe "ability of Velo, Master of Ceremonies"
        }
        allPass()
        expect { "Velo, Master of Ceremonies".counters(arcanum.api.CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    /** Wasted Bounty sacrifices a land for mana: Baloth Sovereign's "whenever you sacrifice a land" triggers. */
    // CR 605.3a, 603.2
    @Test
    fun aLandSacrificedForManaTriggers() = scenario {
        alice.battlefield("Wasted Bounty", "Forest")
        alice.battlefield("Baloth Sovereign", tapped = true)
        alice.activates("Wasted Bounty", choosing = listOf("Forest"), color = Color.Green)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        expect { alice.pool.size shouldBe 1 }
        allPass()
        expect {
            alice.battlefield has "Beast Token"
            "Baloth Sovereign".tapped shouldBe false
        }
    }

    /** Umbral Elves' damage to its controller comes with its mana, at once. */
    // CR 605.3b, 106.12
    @Test
    fun umbralElvesDealsItsDamageAtOnce() = scenario {
        alice.battlefield("Umbral Elves")
        alice.activates("Umbral Elves")
        expect {
            alice.life shouldBe 19
            alice.pool.size shouldBe 1
            stack.isEmpty()
        }
    }

    // ---- Summoning sickness and costs without {T}

    /** Lineage Druid taps three Elves, itself among them, all summoning sick: no {T} in the cost, so it may. */
    // CR 302.6
    @Test
    fun sickElvesTapForLineageDruid() = scenario {
        alice.battlefield("Lineage Druid", "Elvish Seer", "Thicketwood Elves", sick = true)
        assertTrue(activateOptions(alice, "Lineage Druid").isNotEmpty(), "Lineage Druid's ability is offered")
        alice.activates("Lineage Druid", choosing = listOf("Lineage Druid", "Elvish Seer", "Thicketwood Elves"))
        expect { alice.pool.size shouldBe 3 }
    }

    /** A summoning-sick creature can crew a Vehicle (crewing taps it as a cost, not with {T}). */
    // CR 302.6, 702.122a
    @Test
    fun aSickCreatureCrews() = scenario {
        alice.battlefield("Runner's Skiff")
        alice.battlefield("Elvish Seer", sick = true)
        alice.activates("Runner's Skiff", choosing = listOf("Elvish Seer"))
        allPass()
        expect { "Runner's Skiff".types.contains(CardType.Creature) shouldBe true }
    }

    /** Leafbeat Drum: "{T}, Tap an untapped creature you control" — the creature may be summoning sick. */
    // CR 302.6
    @Test
    fun aSickCreatureTapsForLeafbeatDrum() = scenario {
        alice.battlefield("Leafbeat Drum")
        alice.battlefield("Elvish Seer", sick = true)
        alice.activates("Leafbeat Drum", choosing = listOf("Elvish Seer"), color = Color.Blue)
        expect { alice.pool.size shouldBe 1 }
    }

    /** Tombcracker taps three Zombies, itself among them, while summoning sick. */
    // CR 302.6
    @Test
    fun sickTombcrackerTapsItselfAsAZombie() = scenario {
        alice.battlefield("Tombcracker", "Corpse Gnawer", "Tombscuttler", sick = true)
        alice.library("Island")
        alice.activates("Tombcracker", ability = 1, choosing = listOf("Tombcracker", "Corpse Gnawer", "Tombscuttler"))
        allPass()
        expect {
            alice.hand has "Island"
            alice.life shouldBe 19
        }
    }

    /** A summoning-sick Elvish Seer's own {T} mana ability isn't offered (control for the ones above). */
    // CR 302.6
    @Test
    fun aSickCreaturesTapAbilityIsntOffered() = scenario {
        alice.battlefield("Elvish Seer", sick = true)
        assertTrue(activateOptions(alice, "Elvish Seer").isEmpty())
    }

    // ---- Redirection

    /** A redirection effect: Storm Spark at alice deals its damage to Shield Bearer instead. */
    // CR 614.9
    @Test
    fun damageIsRedirectedToTheCreature() = scenario {
        alice.battlefield(shieldBearer)
        bob.hand("Storm Spark")
        bob.castsWithoutCosts("Storm Spark", target = "alice")
        resolve()
        expect { alice.life shouldBe 20 }
        assertEquals(3, state.objOrThrow(card(shieldBearer)).damage)
    }

    /** A redirection to a permanent that is no longer a creature does nothing (CR 614.9): with Rapid Retrofit, Shield
     * Bearer is a noncreature Vehicle, so alice is dealt the damage. */
    // CR 614.9
    @Test
    fun redirectionToANoncreatureDoesNothing() = scenario {
        alice.battlefield(shieldBearer, "Rapid Retrofit")
        attach("Rapid Retrofit", shieldBearer)
        bob.hand("Storm Spark")
        expect { shieldBearer.types.contains(CardType.Creature) shouldBe false }
        bob.castsWithoutCosts("Storm Spark", target = "alice")
        resolve()
        expect { alice.life shouldBe 17 }
    }

    /** A redirection effect fixed on one creature (an effect from a resolved spell) does nothing once that creature has
     * left the battlefield (CR 614.9): alice is dealt the damage, it isn't lost. */
    // CR 614.9, 400.7
    @Test
    fun redirectionToAPermanentThatLeftDoesNothing() = scenario {
        alice.battlefield("Wall of Portents")
        bob.hand("Storm Spark", "Storm Spark")
        effect(alice, Mechanic.Replacement(
            event = EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You)),
            modify = listOf(Modification.ChangeRecipient(Recipients(objects = Objects.Bound("\$affected")))),
        ), on = listOf("Wall of Portents"))
        bob.castsWithoutCosts("Storm Spark", target = "alice")
        resolve()
        expect { alice.life shouldBe 20 }
        assertEquals(3, state.objOrThrow(card("Wall of Portents")).damage)
        move("Wall of Portents", Zone.Graveyard)
        bob.castsWithoutCosts("Storm Spark", target = "alice")
        resolve()
        expect { alice.life shouldBe 17 }
    }

    // ---- Put onto the battlefield attacking

    /** Battle cry pumps the attackers when it resolves; a Ninja put onto the battlefield attacking later isn't among
     * them (CR 611.2c). */
    // CR 611.2c, 702.91a, 508.4
    @Test
    fun battleCryDoesntPumpALaterAttacker() = scenario {
        alice.battlefield("Beacon Pest", "Tinkered Mite", "Island", "Island")
        alice.hand("Ninja of the Silent Hours")
        alice.library("Swamp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Beacon Pest", "Tinkered Mite")
        allPass()
        expect { "Tinkered Mite".power shouldBe 2 }
        advanceTo(TurnStep.DeclareBlockers)
        alice.activates("Ninja of the Silent Hours", choosing = listOf("Tinkered Mite"))
        allPass()
        expect { "Ninja of the Silent Hours".power shouldBe 2 }
    }

    /** A token put onto the battlefield attacking wasn't declared as an attacker: its "whenever this creature attacks"
     * doesn't trigger (Myriad-Faced Shade copying Gearwheel Courier). */
    // CR 508.4, 508.3a
    @Test
    fun aTokenEnteringAttackingDoesntTriggerAttacks() = scenario {
        alice.battlefield("Gearwheel Courier", "Tinkered Mite", "Island", "Island", "Island", "Island")
        alice.hand("Myriad-Faced Shade")
        alice.library("Swamp", "Swamp", "Swamp", "Swamp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Gearwheel Courier", "Tinkered Mite")
        allPass()
        expect { alice.exile.size shouldBe 1 }
        advanceTo(TurnStep.DeclareBlockers)
        alice.activates("Myriad-Faced Shade", choosing = listOf("Tinkered Mite"))
        allPass()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Gearwheel Courier"))))
        passUntilEmpty(mays = true)
        expect {
            alice.battlefield.names.count { it == "Gearwheel Courier" } shouldBe 2
            alice.exile.size shouldBe 1
        }
    }
}
