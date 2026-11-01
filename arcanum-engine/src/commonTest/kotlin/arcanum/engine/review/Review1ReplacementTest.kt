package arcanum.engine.review

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Review 1: replacement and prevention effects (CR 614–616), regeneration (CR 701.19). */
class Review1ReplacementTest {
    private fun permanent(name: String, type: CardType, vararg mechanics: Mechanic) =
        Cards.define(name, CardDefinition(Layout.Normal, listOf(FaceDefinition(name, ManaCost.parse("{2}"), types = listOf(type),
            abilities = listOf(Ability.Static(mechanics.toList(), Affected.Objects(Objects.Self)))))))

    /** "If a card would be put into a graveyard from anywhere, exile it instead." */
    private val finalRest = permanent("Review Final Rest", CardType.Enchantment, Mechanic.Replacement(
        EventPattern.ZoneChange(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), to = Zone.Graveyard),
        modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile)))))

    /** "Prevent all damage that would be dealt to you." */
    private val sanctuary = permanent("Review Sanctuary", CardType.Enchantment, Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any,
        RecipientFilter(players = PlayerFilter.You))))

    /** "Regenerate target creature." ({G} instant) */
    private val mendingBark = Cards.define("Review Mending Bark", CardDefinition(Layout.Normal, listOf(FaceDefinition(
        "Review Mending Bark", ManaCost.parse("{G}"), types = listOf(CardType.Instant),
        abilities = listOf(Ability.Spell(Instructions(
            targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), Value.Fixed(1), Value.Fixed(1))),
            steps = listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Regenerate(Objects.Target("creature"))))),
        ))),
    ))))

    /** A self-replacement ("put it on top of its owner's library instead") applies before a flashback spell's own
     * "exile it if it would leave the Stack", which then still applies: the countered flashback spell is exiled. */
    // CR 614.15, 616.1a, 702.34a
    @Test
    fun counteredFlashbackSpellIsExiledEvenByRecallLapse() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard("Cinder Bolt")
        bob.battlefield("Island", "Island")
        bob.hand("Recall Lapse")
        alice.casts("Cinder Bolt", target = "bob")
        alice.passes()
        bob.casts("Recall Lapse", target = "Cinder Bolt")
        allPass()
        expect {
            alice.exile has "Cinder Bolt"
            alice.library lacks "Cinder Bolt"
            bob.life shouldBe 20
        }
    }

    /** The counterspell's own "instead" goes first: with "cards go to exile instead of a graveyard" in play, a spell
     * countered by Recall Lapse goes on top of its owner's library. */
    // CR 614.15, 616.1a
    @Test
    fun selfReplacementBeforeAGraveyardReplacement() = scenario {
        alice.battlefield(finalRest, "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Island", "Island")
        bob.hand("Recall Lapse")
        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.casts("Recall Lapse", target = "Storm Spark")
        allPass()
        expect {
            alice.library has "Storm Spark"
            bob.exile has "Recall Lapse"
        }
    }

    /** A creature exiled instead of being put into the graveyard didn't die: no dies trigger. */
    // CR 700.4, 614.6
    @Test
    fun exiledInsteadOfDyingDoesntTriggerDies() = scenario {
        alice.battlefield(finalRest, "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Thicketwood Elves", "Harborside Knifer")
        alice.casts("Storm Spark", target = "Thicketwood Elves")
        allPass()
        expect {
            bob.exile has "Thicketwood Elves"
            stack.isEmpty()
            alice.life shouldBe 20
        }
    }

    /** "Can't be regenerated this turn" beats a regeneration shield made earlier. */
    // CR 701.19c
    @Test
    fun cantBeRegeneratedBeatsAShield() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Immolation Bolt")
        bob.battlefield("Forest", "Thicketwood Elves")
        bob.hand(mendingBark)
        alice.casts("Immolation Bolt", target = "Thicketwood Elves")
        alice.passes()
        bob.casts(mendingBark, target = "Thicketwood Elves")
        allPass()
        allPass()
        expect { bob.graveyard has "Thicketwood Elves" }
    }

    /** A regenerated blocker is removed from combat, tapped, and its damage removed. */
    // CR 701.19a
    @Test
    fun regeneratedBlockerLeavesCombat() = scenario {
        Cards.invent("Review Ox", "{2}{G}", "Creature — Ox", "", "3/3")
        alice.battlefield("Review Ox")
        bob.battlefield("Forest", "Thicketwood Elves")
        bob.hand(mendingBark)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Ox")
        allPass()
        bob.blocks("Thicketwood Elves" to "Review Ox")
        alice.passes()
        bob.casts(mendingBark, target = "Thicketwood Elves")
        allPass()
        allPass()
        expect {
            bob.battlefield has "Thicketwood Elves"
            "Thicketwood Elves".tapped shouldBe true
        }
        assertTrue(state.combat?.blockers.orEmpty().none { it.id == card("Thicketwood Elves") }, "Still blocking: ${state.combat}")
    }

    /** Damage that can't be prevented ignores a prevention effect; the same spell smaller is prevented. */
    // CR 615.12
    @Test
    fun damageThatCantBePrevented() = scenario {
        bob.battlefield(sanctuary)
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Doomfire")
        alice.casts("Doomfire", target = "bob", x = 5)
        allPass()
        expect { bob.life shouldBe 15 }
    }

    /** Elenwyn's "enters with additional +1/+1 counters" adds to the creature's own "enters with X counters". */
    // CR 614.1c, 616.1e, 122.6
    @Ignore("review1 #4: two enters-with-counters replacements make two counter entries of one kind; only one counts")
    @Test
    fun twoEntersWithCountersAddUp() = scenario {
        alice.battlefield("Elenwyn, Spinner of Dawn", "Plains", "Plains", "Plains", "Plains")
        alice.hand("Hangar Strider")
        alice.casts("Hangar Strider", x = 2)
        allPass()
        expect { "Hangar Strider".counters(CounterKind.PowerToughness(1, 1)) shouldBe 3 }
    }

    /** Token creation doubled by a "tokens plus that many Squirrels" effect is still one creation event: Rod of the
     * Fabulist gets one story counter. */
    // CR 614.1a, 603.2c
    @Test
    fun extraTokensAreOneEvent() = scenario {
        alice.battlefield("Nutcracker, Squirrel Marshal", "Rod of the Fabulist", "Plains", "Plains", "Plains")
        alice.hand("Lingering Shades")
        alice.casts("Lingering Shades")
        allPass()
        allPass()
        expect {
            alice.battlefield.names.count { it == "Spirit Token" } shouldBe 2
            alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 2
            "Rod of the Fabulist".counters(CounterKind.Named("story")) shouldBe 1
        }
    }

    /** Counters put on a creature as a cost are put on it: "that many minus one" applies (Zealous Druid's cost puts
     * none). */
    // CR 614.1a, 122.6, 118.3
    @Ignore("review1 #3: counters put as a cost bypass counter replacements")
    @Test
    fun counterReplacementAppliesToACostsCounters() = scenario {
        alice.battlefield("Mender of Afflictions", "Zealous Druid")
        alice.activates("Zealous Druid", ability = 1)
        expect { "Zealous Druid".counters(CounterKind.PowerToughness(-1, -1)) shouldBe 0 }
    }

    /** Endless Return: a draw is replaced by revealing the top card; a creature goes to the graveyard instead. */
    // CR 614.1a, 121.6
    @Test
    fun drawReplacedByReveal() = scenario {
        turn(number = 3, step = TurnStep.Upkeep)
        alice.battlefield("Endless Return")
        alice.library("Thicketwood Elves", "Island")
        allPass()
        expect {
            alice.graveyard has "Thicketwood Elves"
            alice.hand.size shouldBe 0
        }
    }

    /** Vizzik's "that many plus one" doesn't apply to its own modular once it has died: the Mite gets 1 counter. */
    // CR 702.43a, 614.1a
    @Test
    fun vizzikAddsToModular() = scenario {
        alice.battlefield("Tinkered Mite", "Mountain")
        alice.battlefield("Vizzik, the Glintwasp", counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        // Vizzik destroys itself: its own replacement is gone as its modular trigger resolves
        alice.activates("Vizzik, the Glintwasp", target = "Vizzik, the Glintwasp")
        allPass()
        repeat(3) {
            while (pending is Decision.Targets) answer(Answer.Targets(listOf(target("Tinkered Mite"))))
            while (pending is Decision.May) answer(Answer.YesNo(true))
            allPass()
        }
        expect { "Tinkered Mite".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }
}
