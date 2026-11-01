package arcanum.engine.action

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Replacement and prevention effects (CR 614–616) on cube cards and on statics written by hand (spec §4.4). */
class ReplacementTest {
    private fun permanent(name: String, type: CardType, vararg mechanics: Mechanic, affects: Objects = Objects.Self,
                          power: Int? = null, keywords: List<Ability> = emptyList()) =
        Cards.define(name, CardDefinition(Layout.Normal, listOf(FaceDefinition(name, ManaCost.parse("{2}"), types = listOf(type),
            power = power?.let { PrintedNumber.Fixed(it) }, toughness = power?.let { PrintedNumber.Fixed(it) },
            abilities = keywords + (if (mechanics.isEmpty()) emptyList() else listOf(Ability.Static(mechanics.toList(), Affected.Objects(affects))))))))

    private fun spell(name: String, vararg steps: Step, targets: List<TargetSlot> = emptyList()): String = Cards.define(name,
        CardDefinition(Layout.Normal, listOf(FaceDefinition(name, ManaCost.parse("{1}"), types = listOf(CardType.Sorcery),
            abilities = listOf(Ability.Spell(Instructions(targets = targets, steps = steps.toList())))))))

    private val creature = ObjectFilter.Type(CardType.Creature)
    private val mine = ObjectFilter.ControlledBy(PlayerFilter.You)
    private fun and(vararg f: ObjectFilter) = ObjectFilter.And(f.toList())
    private val creatureSlot = TargetSlot("creature", TargetKind.Objects(creature), Value.Fixed(1), Value.Fixed(1))

    /** "If a card would be put into a graveyard from anywhere, exile it instead." */
    private val restInPeace = permanent("Test Final Rest", CardType.Enchantment, Mechanic.Replacement(
        EventPattern.ZoneChange(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), to = Zone.Graveyard),
        modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile)))))

    /** "If a source you control would deal damage to a permanent or player, it deals double that damage instead." */
    private val doubler = permanent("Test Fire Within", CardType.Enchantment, Mechanic.Replacement(
        EventPattern.Damage(ObjectFilter.ControlledBy(PlayerFilter.You), RecipientFilter()),
        modify = listOf(Modification.MultiplyAmount(2))))

    // CR 614.1a: "instead" changes where the cards go: the creature and the resolved spell are exiled
    @Test
    fun exileInsteadOfTheGraveyard() = scenario {
        alice.battlefield(restInPeace)
        alice.hand("Storm Spark")
        bob.battlefield("Umbral Elves")

        alice.castsWithoutCosts("Storm Spark", target = "Umbral Elves")
        resolve()

        expect {
            bob.exile has "Umbral Elves"
            alice.exile has "Storm Spark"
            alice.graveyard.isEmpty()
        }
    }

    // CR 614.1a, 120.3: doubled damage
    @Test
    fun doubleDamage() = scenario {
        alice.battlefield(doubler)
        alice.hand("Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        resolve()

        expect { bob.life shouldBe 14 }
    }

    // CR 615.7, 616.1: doubled first (the affected player's order here: replacements, then shields), then the shield
    @Test
    fun doubledDamageThenAShield() = scenario {
        alice.battlefield(doubler)
        alice.hand("Storm Spark")
        /** "Prevent the next 2 damage that would be dealt to you this turn." */
        val shield = spell("Test Guard", Step.Do(Effect.PreventDamage(Recipients(players = Players.You), Value.Fixed(2), ObjectFilter.Any,
            Duration.EndOfTurn)))
        bob.hand(shield)

        bob.castsWithoutCosts(shield)
        resolve()
        alice.castsWithoutCosts("Storm Spark", target = "bob")
        resolve()

        expect { bob.life shouldBe 16 }
    }

    // CR 615.1: "Prevent all damage that would be dealt to you"
    @Test
    fun preventAllDamageToYou() = scenario {
        /** "Prevent all damage that would be dealt to you." */
        val ward = permanent("Test Sanctuary", CardType.Enchantment, Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any,
            RecipientFilter(players = PlayerFilter.You))))
        bob.battlefield(ward)
        alice.hand("Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        resolve()

        expect { bob.life shouldBe 20 }
    }

    // CR 615.1: preventing part of each damage event
    @Test
    fun preventOneOfEachDamage() = scenario {
        /** "If a source would deal damage to you, prevent 1 of that damage." */
        val ward = permanent("Test Buffer", CardType.Enchantment, Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any,
            RecipientFilter(players = PlayerFilter.You)), each = Value.Fixed(1)))
        bob.battlefield(ward)
        alice.hand("Storm Spark", "Magma Needle")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        resolve()
        alice.castsWithoutCosts("Magma Needle", target = "bob")
        resolve()

        expect { bob.life shouldBe 18 }
    }

    // CR 119.3, 614.1a: "you gain twice that much life instead"
    @Test
    fun doubleLifeGain() = scenario {
        /** "If you would gain life, you gain twice that much life instead." */
        val bounty = permanent("Test Overflowing Cup", CardType.Enchantment, Mechanic.Replacement(EventPattern.GainLife(PlayerFilter.You),
            modify = listOf(Modification.MultiplyAmount(2))))
        alice.battlefield(bounty)
        alice.hand("Tendrils of Torment")

        alice.castsWithoutCosts("Tendrils of Torment", target = "bob")
        resolveStack()

        expect {
            alice.life shouldBe 24
            bob.life shouldBe 18
        }
    }

    // CR 119.7: a player who can't gain life doesn't
    @Test
    fun cantGainLife() = scenario {
        /** "Your opponents can't gain life." */
        val ban = Cards.define("Test Sour Wells", CardDefinition(Layout.Normal, listOf(FaceDefinition("Test Sour Wells", ManaCost.parse("{1}"),
            types = listOf(CardType.Enchantment), abilities = listOf(Ability.Static(listOf(Mechanic.CantGainLife),
                Affected.Players(Players.Each(PlayerFilter.Opponent))))))))
        bob.battlefield(ban)
        alice.hand("Tendrils of Torment")

        alice.castsWithoutCosts("Tendrils of Torment", target = "bob")
        resolveStack()

        expect { alice.life shouldBe 20 }
    }

    // CR 111.1, 614.1a: "twice that many tokens"
    @Test
    fun doubleTokens() = scenario {
        /** "If one or more tokens would be created under your control, twice that many of those tokens are created instead." */
        val season = permanent("Test Fertile Season", CardType.Enchantment, Mechanic.Replacement(EventPattern.CreateToken(mine),
            modify = listOf(Modification.MultiplyAmount(2))))
        alice.battlefield(season)
        alice.hand("Lingering Shades")

        alice.castsWithoutCosts("Lingering Shades")
        resolve()

        expect { alice.battlefield.names.count { it == "Spirit Token" } shouldBe 4 }
    }

    /** "If one or more +1/+1 counters would be put on a creature you control, that many plus one are put on it instead." */
    private val scales = permanent("Test Hardened Hide", CardType.Enchantment, Mechanic.Replacement(
        EventPattern.CountersPut(and(creature, mine), CounterKind.PowerToughness(1, 1)), modify = listOf(Modification.AddAmount(Value.Fixed(1)))))

    /** "If one or more counters would be put on a permanent you control, twice that many are put on it instead." */
    private val doublingCounters = permanent("Test Doubling Sap", CardType.Enchantment, Mechanic.Replacement(
        EventPattern.CountersPut(mine), modify = listOf(Modification.MultiplyAmount(2))))

    /** "Put a +1/+1 counter on target creature." */
    private val oneCounter = spell("Test Boon", Step.Do(Effect.PutCounters(Objects.Target("creature"), CounterKind.PowerToughness(1, 1),
        Value.Fixed(1))), targets = listOf(creatureSlot))

    // CR 122.6, 614.1a: one more counter
    @Test
    fun oneMoreCounter() = scenario {
        alice.battlefield(scales, "Elvish Seer")
        alice.hand(oneCounter)

        alice.castsWithoutCosts(oneCounter, target = "Elvish Seer")
        resolve()

        expect { "Elvish Seer".counters(CounterKind.PowerToughness(1, 1)) shouldBe 2 }
    }

    // CR 616.1e: two replacements whose order matters: the affected player chooses (+1 first, then doubled: 4)
    @Test
    fun theAffectedPlayerOrdersReplacements() = scenario {
        alice.battlefield(scales, doublingCounters, "Elvish Seer")
        alice.hand(oneCounter)

        alice.castsWithoutCosts(oneCounter, target = "Elvish Seer")
        resolve()
        val choice = decision<Decision.ChooseReplacement>()
        assertEquals(alice.id, choice.player)
        // the first option is the +1 (from the earlier permanent)
        answer(Answer.Replacement(0))

        expect { "Elvish Seer".counters(CounterKind.PowerToughness(1, 1)) shouldBe 4 }
    }

    // CR 616.1e: the other order gives 3
    @Test
    fun theOtherOrderGivesAnotherResult() = scenario {
        alice.battlefield(scales, doublingCounters, "Elvish Seer")
        alice.hand(oneCounter)

        alice.castsWithoutCosts(oneCounter, target = "Elvish Seer")
        resolve()
        answer(Answer.Replacement(1))

        expect { "Elvish Seer".counters(CounterKind.PowerToughness(1, 1)) shouldBe 3 }
    }

    // CR 616.1a, 614.15: the countering spell's own replacement applies first; then the graveyard replacement no longer does
    @Test
    fun selfReplacementFirst() = scenario {
        alice.battlefield(restInPeace)
        alice.hand("Storm Spark")
        alice.library("Forest")
        bob.hand("Recall Lapse")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        bob.castsWithoutCosts("Recall Lapse", target = "Storm Spark")
        resolve()

        expect {
            alice.library.names shouldBe listOf("Storm Spark", "Forest")
            bob.exile has "Recall Lapse"
        }
    }

    // CR 614.12: "Creatures your opponents control enter tapped"
    @Test
    fun opponentsCreaturesEnterTapped() = scenario {
        /** "Creatures your opponents control enter tapped." */
        val gate = permanent("Test Frozen Gate", CardType.Enchantment, Mechanic.Replacement(
            EventPattern.ZoneChange(and(creature, ObjectFilter.OwnedBy(PlayerFilter.Opponent)), to = Zone.Battlefield),
            modify = listOf(Modification.EntersTapped)))
        alice.battlefield(gate)
        bob.hand("Umbral Elves")
        alice.hand("Elvish Seer")

        bob.castsWithoutCosts("Umbral Elves")
        resolve()
        alice.castsWithoutCosts("Elvish Seer")
        resolve()

        expect {
            "Umbral Elves".tapped shouldBe true
            "Elvish Seer".tapped shouldBe false
        }
    }

    /** "Put a land card from your hand onto the battlefield." */
    private val ramp = spell("Test Ramp", Step.Choose(arcanum.api.Choice.Objects(and(ObjectFilter.Type(CardType.Land),
        ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), Value.Fixed(1), Value.Fixed(1)), "land", Players.You),
        Step.Do(Effect.Move(Objects.Bound("land"), Destination(Zone.Battlefield))))

    // CR 614.12: "As this land enters, you may pay 2 life. If you don't, it enters tapped."
    @Test
    fun asEntersPayLife() = scenario {
        alice.hand(ramp, "Gore Vault")

        alice.castsWithoutCosts(ramp)
        resolve()
        assertTrue(decision<Decision.May>().cost != null)
        answer(Answer.YesNo(true))

        expect {
            alice.life shouldBe 18
            "Gore Vault".tapped shouldBe false
        }
    }

    // CR 614.12: declining enters tapped
    @Test
    fun asEntersDeclineEntersTapped() = scenario {
        alice.hand(ramp, "Gore Vault")

        alice.castsWithoutCosts(ramp)
        resolve()
        answer(Answer.YesNo(false))

        expect {
            alice.life shouldBe 20
            "Gore Vault".tapped shouldBe true
        }
    }

    // CR 614.12, 607.2d: "As this creature enters, choose a creature type"; later Elves of that type enter with a counter
    @Test
    fun asEntersChooseACreatureType() = scenario {
        alice.hand("Gleaming Copycat", "Elvish Seer")

        alice.castsWithoutCosts("Gleaming Copycat")
        resolve()
        answer(Answer.SubtypeChoice(Subtype("Elf")))
        expect { "Gleaming Copycat".subtypes.contains(Subtype("Elf")) shouldBe true }

        alice.castsWithoutCosts("Elvish Seer")
        resolve()
        expect { "Elvish Seer".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    // CR 121.6: "skip that draw"
    @Test
    fun skipADraw() = scenario {
        /** "If you would draw a card, skip that draw instead." */
        val stall = permanent("Test Stalled Mind", CardType.Enchantment, Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You),
            modify = listOf(Modification.Skip)))
        alice.battlefield(stall)
        alice.hand("Forebear's Insight")
        alice.library("Forest", "Island", "Swamp")

        alice.castsWithoutCosts("Forebear's Insight", target = "alice")
        resolve()

        expect { alice.hand.size shouldBe 0 }
    }

    // CR 702.52a, 614.6: dredge replaces a draw (optional): mill 3 and return the card
    @Test
    fun dredgeInsteadOfDrawing() = scenario {
        alice.graveyard("Shadowsting")
        alice.hand("Glance Ahead")
        alice.library("Forest", "Island", "Swamp", "Mountain")

        alice.castsWithoutCosts("Glance Ahead")
        resolve()
        // Scry 1 first: keep it on top
        answer(Answer.Objects(emptyList()))
        // Draw: apply dredge?
        answer(Answer.YesNo(true))

        expect {
            alice.hand.names shouldBe listOf("Shadowsting")
            alice.graveyard.names.containsAll(listOf("Forest", "Island", "Swamp")) shouldBe true
            alice.library.names shouldBe listOf("Mountain")
        }
    }

    // CR 702.35a, 603.11: madness: discarded into exile instead, and its linked trigger goes on the Stack
    @Test
    fun madnessExilesAndTriggers() = scenario {
        alice.hand("Crow's Larceny")
        bob.hand("Blast from Before")

        alice.castsWithoutCosts("Crow's Larceny", target = "bob")
        resolve()

        expect {
            bob.exile has "Blast from Before"
            stack.names shouldBe listOf("ability of Blast from Before")
        }
    }

    /** "Indestructible" on a 3/3. */
    private val stalwart = permanent("Test Stalwart", CardType.Creature, power = 3, keywords = listOf(Keyword.Indestructible))

    // CR 702.12b: lethal damage and "destroy" don't destroy an indestructible permanent
    @Test
    fun indestructibleSurvivesDamageAndDestroy() = scenario {
        bob.battlefield(stalwart)
        alice.hand("Storm Spark", "Sudden Rot")

        alice.castsWithoutCosts("Storm Spark", target = stalwart)
        resolve()
        alice.castsWithoutCosts("Sudden Rot", target = stalwart)
        resolve()

        expect { bob.battlefield has stalwart }
    }

    // CR 704.5f: 0 toughness still puts an indestructible creature into the graveyard
    @Test
    fun indestructibleDiesToZeroToughness() = scenario {
        bob.battlefield(stalwart)
        alice.hand("Sever Limb")

        alice.castsWithoutCosts("Sever Limb", target = stalwart)
        resolve()

        expect { bob.graveyard has stalwart }
    }

    // CR 702.16b: protection from blue: a blue spell's target is illegal, so it fizzles (CR 608.2b)
    @Test
    fun protectionMakesATargetIllegal() = scenario {
        bob.battlefield("Glade Sprite Ranger")
        alice.hand("Click Back")

        alice.castsWithoutCosts("Click Back", target = "Glade Sprite Ranger")
        resolve()

        expect {
            bob.battlefield has "Glade Sprite Ranger"
            log mentions "Fizzled"
        }
    }

    // CR 614.1, D26: a replacement shield used up after one use
    @Test
    fun aOneUseShield() = scenario {
        /** "The next time a creature would die this turn, exile it instead." */
        val once = spell("Test Soul Snare", Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
            EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.EndOfTurn, uses = 1)))
        alice.hand(once, "Storm Spark", "Storm Spark")
        bob.battlefield("Umbral Elves", "Elvish Seer")

        alice.castsWithoutCosts(once)
        resolve()
        alice.castsWithoutCosts("Storm Spark", target = "Umbral Elves")
        resolve()
        alice.castsWithoutCosts("Storm Spark", 1.let { "Elvish Seer" })
        resolve()

        expect {
            bob.exile has "Umbral Elves"
            bob.graveyard has "Elvish Seer"
        }
    }

    // CR 701.19a: a regeneration shield replaces destruction by lethal damage (a state-based action)
    @Test
    fun regenerationAgainstLethalDamage() = scenario {
        /** "Regenerate target creature." */
        val regen = spell("Test Mending Bark", Step.Do(Effect.Action(arcanum.api.KeywordAction.Regenerate(Objects.Target("creature")))),
            targets = listOf(creatureSlot))
        bob.battlefield("Elvish Hierophant")
        bob.hand(regen)
        alice.hand("Storm Spark")

        bob.castsWithoutCosts(regen, target = "Elvish Hierophant")
        resolve()
        alice.castsWithoutCosts("Storm Spark", target = "Elvish Hierophant")
        resolve()

        expect {
            bob.battlefield has "Elvish Hierophant"
            "Elvish Hierophant".tapped shouldBe true
            "Elvish Hierophant".view.obj!!.damage shouldBe 0
        }
    }

    // CR 614.9: damage redirected to another object
    @Test
    fun redirectDamageToACreature() = scenario {
        /** "All damage that would be dealt to you is dealt to this creature instead." (a 5/5) */
        val guard = permanent("Test Shieldbearer", CardType.Creature, Mechanic.Replacement(
            EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You)),
            modify = listOf(Modification.ChangeRecipient(Recipients(objects = Objects.Self)))), power = 5)
        bob.battlefield(guard)
        alice.hand("Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        resolve()

        expect {
            bob.life shouldBe 20
            guard.view.obj!!.damage shouldBe 3
        }
    }

    // CR 702.34a: a spell cast with flashback is exiled as it leaves the Stack
    @Test
    fun flashbackExiles() = scenario {
        alice.graveyard("Cinder Bolt")

        alice.castsWithoutCosts("Cinder Bolt", target = "bob")
        resolve()

        expect {
            bob.life shouldBe 18
            alice.exile has "Cinder Bolt"
        }
    }

    // CR 702.84a: unearth: the returned creature is exiled instead if it would leave the battlefield
    @Test
    fun unearthedCreatureIsExiledInsteadOfDying() = scenario {
        alice.graveyard("Junkyard Cur")
        alice.hand("Storm Spark")

        alice.activatesWithoutCosts("Junkyard Cur")
        resolve()
        // Its enters trigger: "you may discard a card. If you do, draw a card."
        resolve()
        answer(Answer.YesNo(false))
        expect { "Junkyard Cur" hasKeyword Keyword.Haste }

        alice.castsWithoutCosts("Storm Spark", target = "Junkyard Cur")
        resolve()
        expect { alice.exile has "Junkyard Cur" }
    }

    // CR 702.88a: rebound: cast from hand, exiled as it resolves; at your next upkeep you may cast it again
    @Test
    fun rebound() = scenario {
        alice.hand("Fleeting Recall")
        alice.battlefield("Canopy Visionary")
        alice.library("Forest", "Island")

        alice.castsWithoutCosts("Fleeting Recall", target = "Canopy Visionary")
        resolve()
        // Canopy Visionary came back: its enters trigger
        resolve()
        expect { alice.exile has "Fleeting Recall" }

        beginStep(arcanum.api.TurnStep.Upkeep, active = alice)
        expect { stack.names shouldBe listOf("ability of Fleeting Recall") }
        resolve()
        answer(Answer.YesNo(true))
        // Cast from exile without paying; its only legal target is chosen without asking (P5)
        expect {
            stack.names shouldBe listOf("Fleeting Recall")
            alice.exile lacks "Fleeting Recall"
        }
    }
}
