package arcanum.engine.resolution

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.CounterKind
import arcanum.api.Designation
import arcanum.api.Doubled
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.KeywordAction
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.scenario.scenario
import arcanum.engine.state.Attacker
import arcanum.engine.state.CombatState
import arcanum.engine.state.NamedValue
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Effects no cube card's text has yet, on spells written by hand in the model (spec §4.4); each test names the oracle
 * text its spell stands for.
 */
class HandWrittenEffectsTest {
    private fun spell(name: String, vararg steps: Step, targets: List<TargetSlot> = emptyList()): String = Cards.define(name,
        CardDefinition(Layout.Normal, listOf(FaceDefinition(name, ManaCost.parse("{1}"), types = listOf(CardType.Sorcery),
            abilities = listOf(Ability.Spell(Instructions(targets = targets, steps = steps.toList())))))))

    private fun slot(name: String, filter: ObjectFilter = creature) =
        TargetSlot(name, TargetKind.Objects(filter), Value.Fixed(1), Value.Fixed(1))

    private val playerSlot = TargetSlot("player", TargetKind.Players(PlayerFilter.Any), Value.Fixed(1), Value.Fixed(1))
    private val creature = ObjectFilter.Type(CardType.Creature)
    private fun targeted(slot: String) = Objects.Target(slot)
    private fun player() = Players.Target("player")
    private fun Effect.step() = Step.Do(this)

    // CR 701.14a: each deals damage equal to its power to the other
    @Test
    fun fight() = scenario {
        /** "Target creature you control fights target creature you don't control." */
        val card = spell("Test Brawl", Effect.Fight(targeted("mine"), targeted("theirs")).step(),
            targets = listOf(slot("mine"), slot("theirs")))
        alice.hand(card)
        alice.battlefield("Elvish Hierophant")
        bob.battlefield("Umbral Elves")

        alice.castsWithoutCosts(card, targets = listOf("Elvish Hierophant", "Umbral Elves"))
        resolve()

        expect {
            bob.graveyard has "Umbral Elves"
            "Elvish Hierophant".view.obj!!.damage shouldBe 1
        }
    }

    // CR 119.5: a life total becoming a number is gaining or losing the difference
    @Test
    fun lifeTotalBecomes() = scenario {
        /** "Target player's life total becomes 10." */
        val card = spell("Test Reckoning", Effect.SetLife(player(), Value.Fixed(10)).step(), targets = listOf(playerSlot))
        alice.hand(card)

        alice.castsWithoutCosts(card, target = "bob")
        resolve()

        expect {
            bob.life shouldBe 10
            log mentions "LifeLost"
        }
    }

    // CR 122.1, 704.5c: poison counters on a player; ten or more lose the game
    @Test
    fun poisonCountersMakeAPlayerLose() = scenario {
        /** "Target player gets two poison counters." */
        val card = spell("Test Venom", Effect.GivePlayerCounters(player(), CounterKind.Poison, Value.Fixed(2)).step(), targets = listOf(playerSlot))
        alice.hand(card)
        bob.counters(CounterKind.Poison, 9)

        alice.castsWithoutCosts(card, target = "bob")
        resolve()

        assertEquals(11, bob.counters(CounterKind.Poison))
        assertTrue(state.player(bob.id).lost)
    }

    // CR 701.26a: tap
    @Test
    fun tapTargetCreature() = scenario {
        /** "Tap target creature." */
        val card = spell("Test Freeze", Effect.Tap(targeted("creature")).step(), targets = listOf(slot("creature")))
        alice.hand(card)
        bob.battlefield("Umbral Elves")

        alice.castsWithoutCosts(card, target = "Umbral Elves")
        resolve()

        expect { "Umbral Elves".tapped shouldBe true }
    }

    // CR 701.3a, 701.3d: attach an Equipment, then unattach it
    @Test
    fun attachThenUnattach() = scenario {
        /** "Attach target Equipment you control to target creature you control." */
        val attach = spell("Test Fitting", Effect.Attach(targeted("equipment"), Recipients(objects = targeted("creature"))).step(),
            targets = listOf(slot("equipment", ObjectFilter.Sub(Subtype("Equipment"))), slot("creature")))
        /** "Unattach target Equipment." */
        val unattach = spell("Test Unfitting", Effect.Unattach(targeted("equipment")).step(),
            targets = listOf(slot("equipment", ObjectFilter.Sub(Subtype("Equipment")))))
        alice.hand(attach, unattach)
        alice.battlefield("Skull Bracer", "Elvish Seer")

        alice.castsWithoutCosts(attach, targets = listOf("Skull Bracer", "Elvish Seer"))
        resolve()
        expect { "Elvish Seer".power shouldBe 2 }

        alice.castsWithoutCosts(unattach, target = "Skull Bracer")
        resolve()
        expect { "Elvish Seer".power shouldBe 1 }
    }

    // CR 702.26b: a phased-out permanent is treated as though it doesn't exist
    @Test
    fun phaseOut() = scenario {
        /** "Target creature phases out." */
        val card = spell("Test Blink", Effect.PhaseOut(targeted("creature")).step(), targets = listOf(slot("creature")))
        alice.hand(card)
        alice.battlefield("Elvish Hierophant", "Elvish Seer")

        alice.castsWithoutCosts(card, target = "Elvish Hierophant")
        resolve()

        expect { "Elvish Seer".power shouldBe 1 }
    }

    // CR 701.37b: monstrous is a mark only effects set
    @Test
    fun markMonstrous() = scenario {
        /** "Target creature becomes monstrous." */
        val card = spell("Test Swell", Effect.Mark(targeted("creature"), ObjectMark.Monstrous).step(), targets = listOf(slot("creature")))
        alice.hand(card)
        alice.battlefield("Elvish Seer")

        alice.castsWithoutCosts(card, target = "Elvish Seer")
        resolve()

        assertTrue(ObjectMark.Monstrous in state.objOrThrow(card("Elvish Seer")).marks)
    }

    // CR 725.1: there is one monarch; becoming it takes it from the other player
    @Test
    fun becomeTheMonarch() = scenario {
        /** "You become the monarch." */
        val card = spell("Test Coronation", Effect.GiveDesignation(Players.You, Designation.Monarch).step())
        alice.hand(card)

        alice.castsWithoutCosts(card)
        resolve()
        assertEquals(listOf(Designation.Monarch), state.player(alice.id).designations)
    }

    // CR 615.7, D26: a prevention shield prevents what is left of its amount and then ends
    @Test
    fun preventTheNextDamage() = scenario {
        /** "Prevent the next 2 damage that would be dealt to target creature this turn." */
        val card = spell("Test Ward", Effect.PreventDamage(Recipients(objects = targeted("creature")), Value.Fixed(2), ObjectFilter.Any,
            Duration.EndOfTurn).step(), targets = listOf(slot("creature")))
        alice.hand(card, "Storm Spark")
        alice.battlefield("Elvish Hierophant")

        alice.castsWithoutCosts(card, target = "Elvish Hierophant")
        resolve()
        assertEquals(1, state.effects.size)
        alice.castsWithoutCosts("Storm Spark", target = "Elvish Hierophant")
        resolve()

        expect {
            alice.battlefield has "Elvish Hierophant"
            "Elvish Hierophant".view.obj!!.damage shouldBe 1
            log mentions "ShieldUsed"
        }
        assertTrue(state.effects.isEmpty())
    }

    // CR 615.12: "can't be prevented" ignores prevention shields
    @Test
    fun damageThatCantBePrevented() = scenario {
        /** "Prevent the next 2 damage that would be dealt to target creature this turn." */
        val shield = spell("Test Ward", Effect.PreventDamage(Recipients(objects = targeted("creature")), Value.Fixed(2), ObjectFilter.Any,
            Duration.EndOfTurn).step(), targets = listOf(slot("creature")))
        alice.hand(shield, "Doomfire")
        alice.battlefield("Elvish Hierophant")

        alice.castsWithoutCosts(shield, target = "Elvish Hierophant")
        resolve()
        alice.castsWithoutCosts("Doomfire", target = "Elvish Hierophant", x = 5)
        resolve()

        expect { alice.graveyard has "Elvish Hierophant" }
        assertEquals(1, state.effects.size, "The shield wasn't used")
    }

    // CR 120.6: removing marked damage
    @Test
    fun removeAllDamage() = scenario {
        /** "Remove all damage marked on target creature." */
        val card = spell("Test Mend", Effect.RemoveAllDamage(targeted("creature")).step(), targets = listOf(slot("creature")))
        alice.hand("Storm Spark", card)
        bob.battlefield("Elvish Hierophant")
        bob.battlefield("Elvish Seer")

        alice.castsWithoutCosts(card, target = "Elvish Hierophant")
        alice.castsWithoutCosts("Storm Spark", target = "Elvish Hierophant")
        resolve()
        expect { bob.graveyard has "Elvish Hierophant" }
    }

    // CR 706, D25: the host rolls the die; the result is bound
    @Test
    fun rollADie() = scenario {
        /** "Roll a six-sided die. You gain life equal to the result." */
        val card = spell("Test Gamble", Effect.RollDie(6, 1, Players.You, bind = "roll").step(),
            Effect.GainLife(Players.You, Value.Bound("roll")).step())
        alice.hand(card)
        random(Answer.DieRolls(listOf(4)))

        alice.castsWithoutCosts(card)
        resolve()

        expect {
            alice.life shouldBe 24
            log mentions "DieRolled"
        }
    }

    // CR 705, D25: the host flips the coin
    @Test
    fun flipACoin() = scenario {
        /** "Flip a coin. If you win the flip, draw a card." */
        val card = spell("Test Toss", Effect.FlipCoin(Players.You, bind = "flip").step(),
            Step.If(arcanum.api.Condition.WonFlip("flip"), listOf(Effect.Draw(Players.You, Value.Fixed(1)).step())))
        alice.hand(card)
        alice.library("Forest")
        random(Answer.CoinFlip(true))

        alice.castsWithoutCosts(card)
        resolve()

        expect { alice.hand has "Forest" }
    }

    // CR 701.12a: exchanging control of two permanents
    @Test
    fun exchangeControl() = scenario {
        /** "Exchange control of target creature you control and target creature an opponent controls." */
        val card = spell("Test Swap", Effect.ExchangeControl(targeted("mine"), targeted("theirs")).step(),
            targets = listOf(slot("mine"), slot("theirs")))
        alice.hand(card)
        alice.battlefield("Elvish Seer")
        bob.battlefield("Umbral Elves")

        alice.castsWithoutCosts(card, targets = listOf("Elvish Seer", "Umbral Elves"))
        resolve()

        expect {
            "Elvish Seer".controller shouldBe bob.id
            "Umbral Elves".controller shouldBe alice.id
        }
    }

    // CR 122.5: moving counters from one object to another
    @Test
    fun moveCounters() = scenario {
        /** "Move all +1/+1 counters from target creature onto another target creature." */
        val plus = CounterKind.PowerToughness(1, 1)
        val card = spell("Test Transfer", Effect.MoveCounters(targeted("from"), targeted("to"), plus, Value.Fixed(99)).step(),
            targets = listOf(slot("from"), slot("to")))
        alice.hand(card)
        alice.battlefield("Elvish Seer", counters = listOf(plus to 2))
        alice.battlefield("Umbral Elves")

        alice.castsWithoutCosts(card, targets = listOf("Elvish Seer", "Umbral Elves"))
        resolve()

        expect {
            "Elvish Seer".counters(plus) shouldBe 0
            "Umbral Elves".counters(plus) shouldBe 2
        }
    }

    // CR 115.7: changing the target of a spell to another legal target
    @Test
    fun changeTheTarget() = scenario {
        /** "Change the target of target spell with a single target." */
        val card = spell("Test Redirect", Effect.ChangeTargets(targeted("spell"), Players.You, optional = false).step(),
            targets = listOf(slot("spell", ObjectFilter.Category(ObjectCategory.Spell))))
        alice.hand("Storm Spark")
        bob.hand(card)
        bob.battlefield("Umbral Elves")

        alice.castsWithoutCosts("Storm Spark", target = "Umbral Elves")
        bob.castsWithoutCosts(card, target = "Storm Spark")
        resolve()
        answer(Answer.Targets(listOf(target("alice"))))
        resolve()

        expect {
            alice.life shouldBe 17
            bob.battlefield has "Umbral Elves"
        }
    }

    // Two piles: the separator splits, the chooser picks
    @Test
    fun separateIntoPiles() = scenario {
        /** "Reveal the top three cards of your library. Target opponent separates them into two piles. Put the pile of
         * your choice into your hand and the other into your graveyard." */
        val card = spell("Test Fortune",
            Effect.Reveal(Objects.LibraryCards(Players.You, Value.Fixed(3), fromTop = true), bind = "revealed").step(),
            Effect.SeparatePiles(Objects.Bound("revealed"), separator = Players.Target("opponent"), chooser = Players.You,
                chosenBind = "kept", otherBind = "rest").step(),
            Effect.Move(Objects.Bound("kept"), arcanum.api.Destination(Zone.Hand)).step(),
            Effect.Move(Objects.Bound("rest"), arcanum.api.Destination(Zone.Graveyard)).step(),
            targets = listOf(TargetSlot("opponent", TargetKind.Players(PlayerFilter.Opponent), Value.Fixed(1), Value.Fixed(1))))
        alice.hand(card)
        alice.library("Forest", "Island", "Swamp")

        alice.castsWithoutCosts(card, target = "bob")
        resolve()
        assertEquals(bob.id, decision<Decision.ChooseObjects>().player)
        bob.chooses("Forest")
        answer(Answer.Option("second"))

        expect {
            alice.hand.names shouldBe listOf("Island", "Swamp")
            alice.graveyard.names shouldBe listOf("Forest", card)
        }
    }

    // CR 607.2a: card memory adds up; forgetting clears it
    @Test
    fun rememberAndForget() = scenario {
        val rememberIt = Cards.define("Test Keeper", CardDefinition(Layout.Normal, listOf(FaceDefinition("Test Keeper", ManaCost.parse("{1}"),
            types = listOf(CardType.Artifact), abilities = listOf(
                /** "{1}: Remember target creature." "{2}: Forget it." */
                Ability.Activated(arcanum.api.Cost(emptyList()), Instructions(listOf(slot("creature")),
                    steps = listOf(Effect.Remember("kept", targeted("creature")).step())),
                    arcanum.api.ActivationRules(arcanum.api.Timing.Any, PlayerFilter.You)),
                Ability.Activated(arcanum.api.Cost(emptyList()), Instructions(steps = listOf(Effect.Forget("kept").step())),
                    arcanum.api.ActivationRules(arcanum.api.Timing.Any, PlayerFilter.You)),
            )))))
        alice.battlefield(rememberIt, "Elvish Seer")

        alice.activatesWithoutCosts(rememberIt, target = "Elvish Seer")
        resolve()
        assertEquals(NamedValue.Objects(listOf(card("Elvish Seer"))), state.objOrThrow(card(rememberIt)).memory["kept"])
        alice.activatesWithoutCosts(rememberIt, ability = 1)
        resolve()
        assertEquals(null, state.objOrThrow(card(rememberIt)).memory["kept"])
    }

    // D61: what the engine can't do yet stops with a manual decision
    @Test
    fun extraCombatIsManual() = scenario {
        /** "After this main phase, there is an additional combat phase followed by an additional main phase." */
        val card = spell("Test Onslaught", Effect.ExtraCombat(followedByMain = true).step())
        alice.hand(card)

        alice.castsWithoutCosts(card)
        resolve()
        assertTrue(decision<Decision.Manual>().text.contains("additional combat"))
        answer(Answer.Done)

        expect { alice.graveyard has card }
    }

    // CR 119.5: exchanging life totals
    @Test
    fun exchangeLifeTotals() = scenario {
        /** "Exchange life totals with target player." */
        val card = spell("Test Mirror", Effect.ExchangeLife(Players.You, player()).step(), targets = listOf(playerSlot))
        alice.hand(card)
        alice.life = 5

        alice.castsWithoutCosts(card, target = "bob")
        resolve()

        expect {
            alice.life shouldBe 20
            bob.life shouldBe 5
        }
    }

    // CR 400.7d: an effect on a spell follows it onto the battlefield
    @Test
    fun spellGetsCarriesToThePermanent() = scenario {
        /** "Target creature spell gets +2/+2 as it enters" ("that creature enters with … "). */
        val card = spell("Test Blessing", Effect.SpellGets(targeted("spell"),
            listOf(Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(2)))).step(),
            targets = listOf(slot("spell", ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Spell), creature)))))
        alice.hand("Elvish Seer", card)

        alice.castsWithoutCosts("Elvish Seer")
        alice.castsWithoutCosts(card, target = "Elvish Seer")
        resolveStack()

        expect { "Elvish Seer".power shouldBe 3 }
    }

    // CR 104.3: an effect that says a player wins makes every other player lose
    @Test
    fun winTheGame() = scenario(players = 3) {
        /** "You win the game." */
        val card = spell("Test Triumph", Effect.WinGame(Players.You).step())
        alice.hand(card)

        alice.castsWithoutCosts(card)
        resolve()

        assertTrue(state.player(bob.id).lost && state.player(carol.id).lost && !state.player(alice.id).lost)
    }

    // CR 104.3a: an effect that says a player loses
    @Test
    fun loseTheGame() = scenario {
        /** "Target player loses the game." */
        val card = spell("Test Doom", Effect.LoseGame(player()).step(), targets = listOf(playerSlot))
        alice.hand(card)

        alice.castsWithoutCosts(card, target = "bob")
        resolve()

        assertTrue(state.player(bob.id).lost)
    }

    // CR 114.1: an emblem is created in the command zone; its abilities work from there (CR 114.4)
    @Test
    fun emblem() = scenario {
        /** "You get an emblem with 'Creatures you control get +1/+1.'" */
        val card = spell("Test Ascension", Effect.CreateEmblem(listOf(Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1))),
            Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.You))))),
            functionsIn = listOf(Zone.Command))), Players.You).step())
        alice.hand(card)
        alice.battlefield("Elvish Seer")

        alice.castsWithoutCosts(card)
        resolve()

        expect { "Elvish Seer".power shouldBe 2 }
        assertEquals(1, state.command.size)
    }

    // CR 701.27a: proliferate gives each chosen permanent and player another counter of each kind
    @Test
    fun proliferate() = scenario {
        /** "Proliferate." */
        val plus = CounterKind.PowerToughness(1, 1)
        val card = spell("Test Growth", Effect.Action(KeywordAction.Proliferate).step())
        alice.hand(card)
        alice.battlefield("Elvish Seer", counters = listOf(plus to 1))
        bob.counters(CounterKind.Poison, 3)

        alice.castsWithoutCosts(card)
        resolve()
        answer(Answer.Targets(listOf(target("Elvish Seer"), target("bob"))))

        expect { "Elvish Seer".counters(plus) shouldBe 2 }
        assertEquals(4, bob.counters(CounterKind.Poison))
    }

    // CR 701.36a: populate copies a creature token you control
    @Test
    fun populate() = scenario {
        /** "Create a 1/1 white Spirit creature token with flying. Populate." */
        alice.hand("Lingering Shades")
        val card = spell("Test Echo", Effect.Action(KeywordAction.Populate).step())
        alice.hand(card)

        alice.castsWithoutCosts("Lingering Shades")
        resolve()
        alice.castsWithoutCosts(card)
        resolve()
        answer(Answer.Objects(listOf(card("Spirit Token", 0))))

        expect { alice.battlefield.size shouldBe 3 }
    }

    // CR 701.44a: explore reveals the top card; a nonland means a +1/+1 counter and may go to the graveyard
    @Test
    fun explore() = scenario {
        /** "Target creature you control explores." */
        val card = spell("Test Trek", Effect.Action(KeywordAction.Explore(targeted("creature"))).step(), targets = listOf(slot("creature")))
        alice.hand(card)
        alice.battlefield("Elvish Seer")
        alice.library("Storm Spark")

        alice.castsWithoutCosts(card, target = "Elvish Seer")
        resolve()
        answer(Answer.YesNo(true))

        expect {
            "Elvish Seer".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            alice.graveyard has "Storm Spark"
        }
    }

    // CR 701.47a: amass creates a 0/0 Army first if you have none
    @Test
    fun amass() = scenario {
        /** "Amass Zombies 2." */
        val card = spell("Test Horde", Effect.Action(KeywordAction.Amass(Subtype("Zombie"), Value.Fixed(2))).step())
        alice.hand(card)

        alice.castsWithoutCosts(card)
        resolve()

        expect {
            card("Zombie Army Token").power shouldBe 2
            card("Zombie Army Token").toughness shouldBe 2
        }
    }

    // CR 701.15a: goaded by the effect's controller
    @Test
    fun goad() = scenario {
        /** "Goad target creature." */
        val card = spell("Test Taunt", Effect.Action(KeywordAction.Goad(targeted("creature"))).step(), targets = listOf(slot("creature")))
        alice.hand(card)
        bob.battlefield("Umbral Elves")

        alice.castsWithoutCosts(card, target = "Umbral Elves")
        resolve()

        assertEquals(listOf(alice.id), state.objOrThrow(card("Umbral Elves")).goadedBy)
    }

    // CR 701.10: doubling counters puts that many more
    @Test
    fun doubleCounters() = scenario {
        /** "Double the number of +1/+1 counters on target creature." */
        val plus = CounterKind.PowerToughness(1, 1)
        val card = spell("Test Bloom", Effect.Action(KeywordAction.Double(Doubled.Counters(targeted("creature"), plus))).step(),
            targets = listOf(slot("creature")))
        alice.hand(card)
        alice.battlefield("Elvish Seer", counters = listOf(plus to 3))

        alice.castsWithoutCosts(card, target = "Elvish Seer")
        resolve()

        expect { "Elvish Seer".counters(plus) shouldBe 6 }
    }

    // CR 701.19a, D26: a regeneration shield replaces destruction: the creature is tapped and its damage removed
    @Test
    fun regenerationShield() = scenario {
        /** "Regenerate target creature." */
        val card = spell("Test Renewal", Effect.Action(KeywordAction.Regenerate(targeted("creature"))).step(), targets = listOf(slot("creature")))
        alice.hand(card)
        bob.hand("Sudden Rot")
        alice.battlefield("Elvish Hierophant")

        alice.castsWithoutCosts(card, target = "Elvish Hierophant")
        resolve()
        bob.castsWithoutCosts("Sudden Rot", target = "Elvish Hierophant")
        resolve()

        expect {
            alice.battlefield has "Elvish Hierophant"
            "Elvish Hierophant".tapped shouldBe true
        }
        assertTrue(state.effects.isEmpty(), "The shield was used up")
    }

    // CR 701.19c: "can't be regenerated" ignores the shield
    @Test
    fun cantBeRegenerated() = scenario {
        /** "Regenerate target creature." */
        val card = spell("Test Renewal", Effect.Action(KeywordAction.Regenerate(targeted("creature"))).step(), targets = listOf(slot("creature")))
        alice.hand(card)
        bob.hand("Curse Word")
        alice.battlefield("Elvish Hierophant")

        alice.castsWithoutCosts(card, target = "Elvish Hierophant")
        resolve()
        bob.castsWithoutCosts("Curse Word", target = "Elvish Hierophant")
        resolve()

        expect { alice.graveyard has "Elvish Hierophant" }
    }

    // CR 701.43a: an exerted creature doesn't untap during its controller's next untap step
    @Test
    fun exert() = scenario {
        /** "Exert target creature." */
        val card = spell("Test Strain", Effect.Action(KeywordAction.Exert(targeted("creature"))).step(), targets = listOf(slot("creature")))
        alice.hand(card)
        alice.battlefield("Elvish Seer")

        alice.castsWithoutCosts(card, target = "Elvish Seer")
        resolve()

        expect { "Elvish Seer" hasMechanic { it == Mechanic.DoesntUntap } }
    }

    // CR 701.35a: detained until your next turn
    @Test
    fun detain() = scenario {
        /** "Detain target creature an opponent controls." */
        val card = spell("Test Arrest", Effect.Action(KeywordAction.Detain(targeted("creature"))).step(), targets = listOf(slot("creature")))
        alice.hand(card)
        bob.battlefield("Umbral Elves")

        alice.castsWithoutCosts(card, target = "Umbral Elves")
        resolve()

        expect {
            "Umbral Elves" hasMechanic { it is Mechanic.CantBlock }
            "Umbral Elves" hasMechanic { it is Mechanic.CantAttack }
        }
    }

    // CR 701.39a: bolster puts counters on a creature with the least toughness (a choice only when tied)
    @Test
    fun bolster() = scenario {
        /** "Bolster 2." */
        val card = spell("Test Rally", Effect.Action(KeywordAction.Bolster(Value.Fixed(2))).step())
        alice.hand(card)
        alice.battlefield("Elvish Hierophant", "Elvish Seer", "Tinkered Mite")

        alice.castsWithoutCosts(card)
        resolve()

        // The Seer is 2/2 with the Hierophant's anthem; the Mite has the least toughness
        expect { "Tinkered Mite".counters(CounterKind.PowerToughness(1, 1)) shouldBe 2 }
    }

    // CR 701.50a: connive draws, discards, and counts nonland discards
    @Test
    fun connive() = scenario {
        /** "Target creature you control connives." */
        val card = spell("Test Scheme", Effect.Action(KeywordAction.Connive(targeted("creature"), Value.Fixed(1))).step(),
            targets = listOf(slot("creature")))
        alice.hand(card, "Storm Spark")
        alice.battlefield("Elvish Seer")
        alice.library("Forest")

        alice.castsWithoutCosts(card, target = "Elvish Seer")
        resolve()
        alice.chooses("Storm Spark")

        expect {
            alice.graveyard has "Storm Spark"
            "Elvish Seer".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 608.2d: "distribute three +1/+1 counters among one or two target creatures"
    @Test
    fun distributeCounters() = scenario {
        /** "Distribute three +1/+1 counters among any number of creatures you control." */
        val plus = CounterKind.PowerToughness(1, 1)
        val card = spell("Test Blessing Rain", Effect.PutCounters(Objects.Each(ObjectFilter.And(listOf(creature,
            ObjectFilter.ControlledBy(PlayerFilter.You)))), plus, Value.Fixed(3), distributed = true).step())
        alice.hand(card)
        alice.battlefield("Elvish Seer", "Umbral Elves")

        alice.castsWithoutCosts(card)
        resolve()
        answer(Answer.Division(listOf(Share(target("Elvish Seer"), 2), Share(target("Umbral Elves"), 1))))

        expect {
            "Elvish Seer".counters(plus) shouldBe 2
            "Umbral Elves".counters(plus) shouldBe 1
        }
    }

    // CR 122.8: removing counters
    @Test
    fun removeCounters() = scenario {
        /** "Remove two +1/+1 counters from target creature." */
        val plus = CounterKind.PowerToughness(1, 1)
        val card = spell("Test Wither", Effect.RemoveCounters(targeted("creature"), plus, Value.Fixed(2)).step(), targets = listOf(slot("creature")))
        alice.hand(card)
        bob.battlefield("Umbral Elves", counters = listOf(plus to 3))

        alice.castsWithoutCosts(card, target = "Umbral Elves")
        resolve()

        expect { "Umbral Elves".counters(plus) shouldBe 1 }
    }

    // CR 506.4: removed from combat
    @Test
    fun removeFromCombat() = scenario {
        /** "Remove target attacking creature from combat." */
        val card = spell("Test Retreat", Effect.RemoveFromCombat(targeted("creature")).step(), targets = listOf(slot("creature")))
        bob.hand(card)
        alice.battlefield("Elvish Seer")
        change(Change.Combat(null, CombatState(listOf(Attacker(card("Elvish Seer"), TargetRef.Player(bob.id))))))

        bob.castsWithoutCosts(card, target = "Elvish Seer")
        resolve()

        assertEquals(emptyList(), state.combat!!.attackers)
    }

    // CR 708.2, 613.7f: turning face down and face up; each gives a new timestamp
    @Test
    fun turnFaceDownAndUp() = scenario {
        /** "Turn target creature face down." / "Turn target creature face up." */
        val down = spell("Test Veil", Effect.TurnFaceDown(targeted("creature")).step(), targets = listOf(slot("creature")))
        val up = spell("Test Unveil", Effect.TurnFaceUp(targeted("creature")).step(), targets = listOf(slot("creature")))
        alice.hand(down, up)
        alice.battlefield("Elvish Hierophant")

        alice.castsWithoutCosts(down, target = "Elvish Hierophant")
        resolve()
        expect { "Elvish Hierophant".power shouldBe 2 }
        assertTrue(state.objOrThrow(card("Elvish Hierophant")).faceDown)

        alice.castsWithoutCosts(up, target = "Elvish Hierophant")
        resolve()
        assertTrue(!state.objOrThrow(card("Elvish Hierophant")).faceDown)
        expect { log mentions "TurnedFaceUp" }
    }

    // CR 701.28a: transforming turns the other face up
    @Test
    fun transform() = scenario {
        /** "Transform target permanent." */
        val card = spell("Test Shift", Effect.Transform(targeted("permanent")).step(),
            targets = listOf(slot("permanent", ObjectFilter.Category(ObjectCategory.Permanent))))
        alice.hand(card)
        alice.battlefield("Shape Beneath the Floe")

        alice.castsWithoutCosts(card, target = "Shape Beneath the Floe")
        resolve()

        expect {
            card("Shape Beneath the Floe").view.name shouldBe "Risen Leviathan"
            log mentions "Transformed"
        }
    }

    // CR 603.7: a delayed trigger triggers at the next end step
    @Test
    fun delayedTriggerAtTheNextEndStep() = scenario {
        /** "At the beginning of the next end step, draw a card." */
        val card = spell("Test Patience", Effect.CreateDelayedTrigger(
            Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
            Instructions(steps = listOf(Effect.Draw(Players.You, Value.Fixed(1)).step()))).step())
        alice.hand(card)
        alice.library("Forest")

        alice.castsWithoutCosts(card)
        resolve()
        beginStep(TurnStep.End)

        expect { stack.names shouldBe listOf("ability of Test Patience") }
        resolve()
        expect { alice.hand has "Forest" }
        assertTrue(state.delayedTriggers.isEmpty(), "It triggered once and ended (CR 603.7c)")
    }

    // CR 603.12: "When you do" is a reflexive trigger, put on the Stack after the resolution
    @Test
    fun reflexiveTrigger() = scenario {
        /** "You may sacrifice a creature. When you do, draw two cards." */
        val card = spell("Test Offering",
            Step.Pay(arcanum.api.Cost(listOf(arcanum.api.CostPart.Sacrifice(arcanum.api.CostObjects.Choose(creature, Value.Fixed(1))))),
                payer = Players.You, bind = "sac"),
            Step.If(arcanum.api.Condition.Did("sac"), listOf(Effect.ReflexiveTrigger(Instructions(steps = listOf(
                Effect.Draw(Players.You, Value.Fixed(2)).step()))).step())))
        alice.hand(card)
        alice.battlefield("Elvish Seer")
        alice.library("Forest", "Island")

        alice.castsWithoutCosts(card)
        resolve()
        answer(Answer.YesNo(true))

        expect {
            alice.graveyard has "Elvish Seer"
            stack.size shouldBe 1
        }
        resolve()
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 608.2c: "choose a creature type" binds the choice for later steps
    @Test
    fun chooseACreatureType() = scenario {
        /** "Choose a creature type. Creatures of the chosen type get +2/+0 until end of turn." */
        val card = spell("Test Rally Cry", Step.Choose(Choice.CreatureType, "type", Players.You),
            Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(0))),
                Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.Sub(arcanum.api.SubtypeRef.Chosen("type")))))),
                Duration.EndOfTurn).step())
        alice.hand(card)
        alice.battlefield("Elvish Seer", "Tinkered Mite")

        alice.castsWithoutCosts(card)
        resolve()
        answer(Answer.SubtypeChoice(Subtype("Elf")))

        expect {
            "Elvish Seer".power shouldBe 3
            "Tinkered Mite".power shouldBe 1
        }
    }
}
