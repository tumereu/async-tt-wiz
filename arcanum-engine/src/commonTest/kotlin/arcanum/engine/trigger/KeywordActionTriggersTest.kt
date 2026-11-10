package arcanum.engine.trigger

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.KeywordAction
import arcanum.api.KeywordActionKind
import arcanum.api.Layout
import arcanum.api.Measure
import arcanum.api.ObjectMark
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.casting.Snapshot
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.decision.Answer
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AbilityOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Whenever" triggers on keyword actions and marks: one [EventPattern.KeywordActionDone] and one [EventPattern.GotMark], read from their
 * wordings on invented cards. Each watcher gains 1 life, so the life total counts the triggers.
 */
class KeywordActionTriggersTest {
    private fun watcher(name: String, event: String) = Cards.invent(name, "{0}", "Enchantment", "Whenever $event, you gain 1 life.")
    private val elf = Cards.invent("Wayfinding Elf", "{0}", "Creature — Elf", "", "1/1")
    private val scout = Cards.invent("Pathcutter Scout", "{0}", "Creature — Elf Scout", "When this creature enters, it explores.", "1/1")

    private fun Scenario.settleAll() { repeat(4) { if (state.stack.isNotEmpty()) allPass() } }

    // ---- Explore

    // CR 701.44a, b: a creature explores once it is done, and the land revealed doesn't matter
    @Test
    fun whenACreatureYouControlExplores() = scenario {
        val w = watcher("Trail Keeper", "a creature you control explores")
        alice.battlefield(w)
        alice.hand(scout)
        alice.library("Forest")
        alice.casts(scout)
        settleAll()
        expect {
            alice.life shouldBe 21
            alice.hand has "Forest"
        }
    }

    // CR 701.44b: exploring with an empty library is still exploring
    @Test
    fun exploringWithNoLibraryStillTriggers() = scenario {
        alice.battlefield(watcher("Empty Trail Keeper", "a creature you control explores"))
        alice.hand(scout)
        alice.casts(scout)
        settleAll()
        expect { alice.life shouldBe 21 }
    }

    // CR 701.44c: no longer on the battlefield as it is told to explore, it explores as it last existed: its controller reveals the card, a
    // nonland may go to the graveyard, there is no permanent for the counter, and it counts as the creature that explored
    @Test
    fun aCreatureThatLeftStillExploresAsItLastExisted() = scenario {
        val altar = Cards.invent("Quiet Altar", "{0}", "Artifact", "Sacrifice a creature: You gain 5 life.")
        alice.battlefield(watcher("Far Trail Keeper", "a creature you control explores"), altar)
        alice.hand(scout)
        alice.library("Storm Spark")
        alice.casts(scout)
        allPass() // the creature resolves; its trigger waits on the Stack
        alice.activates(altar, choosing = listOf(scout))
        allPass() // the altar's ability
        allPass() // the explore trigger, its creature gone
        answer(Answer.YesNo(true))
        settleAll()
        expect {
            alice.graveyard has "Storm Spark"
            alice.life shouldBe 26
        }
    }

    // CR 701.44a: the watcher names "a creature you control": another player's creature exploring isn't one
    @Test
    fun anOpponentsCreatureExploringDoesntTriggerIt() = scenario {
        val shared = Cards.invent("Shared Trek", "{0}", "Artifact", "{0}: Target creature explores.")
        alice.battlefield(watcher("Own Trail Keeper", "a creature you control explores"), shared)
        bob.battlefield(elf)
        bob.library("Forest")
        alice.activates(shared, target = elf)
        settleAll()
        expect { alice.life shouldBe 20 }
    }

    private val eachCreatureExplores = spellWith("Grand Trek", Effect.Action(KeywordAction.Explore(arcanum.api.Objects.Each(arcanum.api.ObjectFilter.Type(CardType.Creature)))))

    /** The explorers in the order they explored. */
    private fun Scenario.explored() = state.eventsOf(state.turn.number).mapNotNull { (it as? Change.Happened)?.event as? GameEvent.KeywordActionDone }
        .filter { it.action == KeywordActionKind.Explore }.flatMap { it.objects }

    // CR 701.44d: their controller chooses which explores next, which decides what each reveals: the one chosen first gets the nonland on top and
    // its counter, the other then finds the land
    @Test
    fun theControllerChoosesWhichExploresNext() = scenario {
        val other = Cards.invent("Second Scout", "{0}", "Creature — Elf Scout", "", "1/1")
        alice.battlefield(elf, other)
        alice.hand(eachCreatureExplores)
        alice.library("Storm Spark", "Forest")
        alice.castsWithoutCosts(eachCreatureExplores)
        val first = resolve().let { decision<arcanum.engine.decision.Decision.ChooseObjects>() }
        assertEquals(setOf(card(elf), card(other)), first.options.toSet())
        alice.chooses(other)
        answer(Answer.YesNo(true))
        settleAll()
        expect {
            other.counters(arcanum.api.CounterKind.PowerToughness(1, 1)) shouldBe 1
            elf.counters(arcanum.api.CounterKind.PowerToughness(1, 1)) shouldBe 0
            alice.graveyard has "Storm Spark"
            alice.hand has "Forest"
        }
        assertEquals(listOf(card(other), card(elf)), explored())
    }

    // CR 701.44d, 101.4: the first player in APNAP order goes first, whoever's spell it is
    @Test
    fun theActivePlayersPermanentsExploreFirst() = scenario {
        val other = Cards.invent("Far Scout", "{0}", "Creature — Elf Scout", "", "1/1")
        turn(active = bob)
        alice.battlefield(elf)
        bob.battlefield(other)
        alice.library("Forest")
        bob.library("Island")
        bob.hand(eachCreatureExplores)
        bob.castsWithoutCosts(eachCreatureExplores)
        resolve()
        settleAll()
        assertEquals(listOf(card(other), card(elf)), explored())
    }

    // CR 701.44c, d: one that has left counts for the player who last controlled it: the active player's, though gone, explores before the other's
    @Test
    fun oneThatLeftExploresInItsLastControllersTurnToChoose() = scenario {
        val other = Cards.invent("Late Scout", "{0}", "Creature — Elf Scout", "", "1/1")
        val everyone = arcanum.api.ObjectFilter.Type(CardType.Creature)
        val spell = Cards.define("Parting Trek", CardDefinition(Layout.Normal, listOf(FaceDefinition("Parting Trek", types = listOf(CardType.Sorcery),
            abilities = listOf(Ability.Spell(Instructions(steps = listOf(
                Step.Bind(arcanum.api.Objects.Each(everyone), "them"),
                Step.Do(Effect.Destroy(arcanum.api.Objects.Each(arcanum.api.ObjectFilter.And(listOf(everyone, arcanum.api.ObjectFilter.ControlledBy(PlayerFilter.You)))))),
                Step.Do(Effect.Action(KeywordAction.Explore(arcanum.api.Objects.Bound("them")))),
            ))))))))
        alice.battlefield(elf)
        bob.battlefield(other)
        alice.library("Forest")
        bob.library("Island")
        alice.hand(spell)
        val theirs = card(other)
        alice.castsWithoutCosts(spell)
        resolve()
        settleAll()
        val order = explored()
        assertEquals(2, order.size)
        assertEquals(theirs, order[1])
        expect {
            alice.graveyard has elf
            alice.hand has "Forest"
            bob.hand has "Island"
        }
    }

    /** A sorcery: each creature is to explore, but the Goners are destroyed first. */
    private val partingTrek by lazy {
        val everyone = arcanum.api.ObjectFilter.Type(CardType.Creature)
        Cards.define("Last Trek", CardDefinition(Layout.Normal, listOf(FaceDefinition("Last Trek", types = listOf(CardType.Sorcery),
            abilities = listOf(Ability.Spell(Instructions(steps = listOf(
                Step.Bind(arcanum.api.Objects.Each(everyone), "them"),
                Step.Do(Effect.Destroy(arcanum.api.Objects.Each(arcanum.api.ObjectFilter.And(listOf(everyone, arcanum.api.ObjectFilter.Sub(arcanum.api.Subtype("Goner"))))))),
                Step.Do(Effect.Action(KeywordAction.Explore(arcanum.api.Objects.Bound("them")))),
            ))))))))
    }

    // CR 701.44a, c, d: an explorer that left gets no counter, so among several of them the order changes nothing: no question, each still explores
    @Test
    fun onlyExplorersThatLeftArentAskedAbout() = scenario {
        val first = Cards.invent("First Goner", "{0}", "Creature — Goner", "", "1/1")
        val second = Cards.invent("Second Goner", "{0}", "Creature — Goner", "", "1/1")
        alice.battlefield(first, second)
        alice.library("Forest", "Plains")
        alice.hand(partingTrek)
        alice.castsWithoutCosts(partingTrek)
        val outcome = resolve()
        assertTrue(outcome.pending?.decision !is arcanum.engine.decision.Decision.ChooseObjects, "${outcome.pending?.decision}")
        settleAll()
        assertEquals(2, explored().size)
        expect {
            alice.graveyard has first
            alice.graveyard has second
            alice.hand has "Forest"
            alice.hand has "Plains"
        }
    }

    // CR 701.44d: with a permanent still there the order matters (it gets the counter or the land), so the one that left is offered beside it
    @Test
    fun oneThatLeftIsOfferedBesideOneStillThere() = scenario {
        val goner = Cards.invent("Lost Goner", "{0}", "Creature — Goner", "", "1/1")
        val other = Cards.invent("Staying Scout", "{0}", "Creature — Elf Scout", "", "1/1")
        alice.battlefield(goner, other)
        alice.library("Storm Spark", "Forest")
        alice.hand(partingTrek)
        val left = card(goner)
        alice.castsWithoutCosts(partingTrek)
        val asked = resolve().let { decision<arcanum.engine.decision.Decision.ChooseObjects>() }
        assertEquals(setOf(left, card(other)), asked.options.toSet())
        // As it last existed: its name for the panel
        assertEquals("Lost Goner", Engine.lastKnown(state, left)?.characteristics?.name)
        answer(Answer.Objects(listOf(left)))
        answer(Answer.YesNo(true))
        settleAll()
        expect {
            other.counters(arcanum.api.CounterKind.PowerToughness(1, 1)) shouldBe 0
            alice.graveyard has "Storm Spark"
            alice.hand has "Forest"
        }
        assertEquals(listOf(left, card(other)), explored())
    }

    // ---- Proliferate

    // CR 701.34a: "any number" may be none: proliferating with nothing to choose is still proliferating
    @Test
    fun whenYouProliferate() = scenario {
        val press = Cards.invent("Spreading Press", "{0}", "Artifact", "{0}: Proliferate.")
        alice.battlefield(watcher("Bloom Keeper", "you proliferate"), press)
        alice.activates(press)
        settleAll()
        expect { alice.life shouldBe 21 }
    }

    // CR 701.34a: only the player who proliferates
    @Test
    fun anOpponentProliferatingDoesntTriggerYours() = scenario {
        val press = Cards.invent("Foreign Press", "{0}", "Artifact", "{0}: Proliferate.")
        alice.battlefield(watcher("Own Bloom Keeper", "you proliferate"))
        bob.battlefield(press)
        turn(active = bob)
        bob.activates(press)
        settleAll()
        expect { alice.life shouldBe 20 }
    }

    // ---- Investigate

    // CR 701.16a
    @Test
    fun whenYouInvestigate() = scenario {
        val lens = Cards.invent("Curious Lens", "{0}", "Artifact", "{0}: Investigate.")
        alice.battlefield(watcher("Case Keeper", "you investigate"), lens)
        alice.activates(lens)
        settleAll()
        expect { alice.life shouldBe 21 }
    }

    // CR 701.16a: investigating twice is two investigations, each creating its Clue
    @Test
    fun investigatingTwiceTriggersTwice() = scenario {
        val lens = Cards.invent("Twin Lens", "{0}", "Artifact", "{0}: Investigate twice.")
        alice.battlefield(watcher("Twin Case Keeper", "you investigate"), lens)
        alice.activates(lens)
        settleAll()
        expect { alice.life shouldBe 22 }
    }

    // ---- Discover

    // CR 701.57a, b: discovering, and putting the card into the hand rather than casting it
    @Test
    fun whenYouDiscover() = scenario {
        val map = Cards.invent("Worn Map", "{0}", "Artifact", "{0}: Discover 3.")
        alice.battlefield(watcher("Vault Keeper", "you discover"), map)
        alice.library("Forest", "Storm Spark")
        alice.activates(map)
        allPass()
        answer(Answer.YesNo(false))
        settleAll()
        expect {
            alice.hand has "Storm Spark"
            alice.life shouldBe 21
        }
    }

    // CR 701.57b: discovered even if all of it was impossible
    @Test
    fun discoveringWithNoLibraryStillTriggers() = scenario {
        val map = Cards.invent("Blank Map", "{0}", "Artifact", "{0}: Discover 3.")
        alice.battlefield(watcher("Empty Vault Keeper", "you discover"), map)
        alice.activates(map)
        allPass()
        answer(Answer.YesNo(false)) // the question of casting comes even with nothing to cast
        settleAll()
        expect { alice.life shouldBe 21 }
    }

    // ---- Collect evidence

    // CR 701.59a: paid as a cost, by exiling the cards: it is collecting evidence
    @Test
    fun whenYouCollectEvidenceAsACost() = scenario {
        val desk = Cards.invent("Evidence Desk", "{0}", "Artifact", "Collect evidence 1: You gain 3 life.")
        alice.battlefield(watcher("Docket Keeper", "you collect evidence"), desk)
        alice.graveyard("Storm Spark")
        alice.activates(desk, choosing = listOf("Storm Spark"))
        settleAll()
        expect {
            alice.exile has "Storm Spark"
            alice.life shouldBe 24
        }
    }

    // ---- Exert

    // CR 701.43a, d: exerting as it attacks is exerting
    @Test
    fun whenYouExertACreature() = scenario {
        val striker = Cards.invent("Straining Striker", "{0}", "Creature — Soldier", "You may exert this creature as it attacks.", "2/2")
        alice.battlefield(watcher("Strain Keeper", "you exert a creature"), striker)
        beginStep(arcanum.api.TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(striker), TargetRef.Player(bob.id), paying = listOf(AbilityOrigin.Printed(0, 0))))))
        settleAll()
        expect { alice.life shouldBe 21 }
    }

    // CR 701.43d: an optional cost not paid: no exert
    @Test
    fun notExertingDoesntTrigger() = scenario {
        val striker = Cards.invent("Resting Striker", "{0}", "Creature — Soldier", "You may exert this creature as it attacks.", "2/2")
        alice.battlefield(watcher("Rest Keeper", "you exert a creature"), striker)
        beginStep(arcanum.api.TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(striker), TargetRef.Player(bob.id)))))
        settleAll()
        expect { alice.life shouldBe 20 }
    }

    // ---- Foretell

    // CR 702.143b, c: foretelling a card is the special action; it doesn't use the Stack, the trigger does
    @Test
    fun whenYouForetellACard() = scenario {
        val omen = Cards.invent("Far Omen", "{3}{U}{U}", "Instant", """
            Draw a card.
            Foretell {1} (During your turn, you may pay {2} and exile this card from your hand face down. Cast it on a later turn for its foretell cost.)
        """)
        alice.battlefield(watcher("Omen Keeper", "you foretell a card"), "Island", "Island")
        alice.hand(omen)
        alice.takesSpecialAction(omen, "foretell", paying = listOf("Island", "Island"))
        settleAll()
        expect { alice.life shouldBe 21 }
    }

    // CR 702.143c, a: a foretold card put in exile got the mark as it moved, which [EventPattern.GotMark] sees too
    @Test
    fun aForetoldCardGetsItsMarkAsItIsExiled() = scenario {
        val omen = Cards.invent("Near Omen", "{3}{U}{U}", "Instant", """
            Draw a card.
            Foretell {1} (During your turn, you may pay {2} and exile this card from your hand face down. Cast it on a later turn for its foretell cost.)
        """)
        val keeper = Cards.define("Marked Omen Keeper", gains(EventPattern.GotMark(ObjectMark.Foretold, arcanum.api.ObjectFilter.Any)))
        alice.battlefield(keeper, "Island", "Island")
        alice.hand(omen)
        alice.takesSpecialAction(omen, "foretell", paying = listOf("Island", "Island"))
        settleAll()
        expect { alice.life shouldBe 21 }
    }

    // ---- Marks

    // CR 701.37a, b: it becomes monstrous once; activating again does nothing and can't trigger again
    @Test
    fun whenACreatureBecomesMonstrous() = scenario {
        val beast = Cards.invent("Rising Beast", "{0}", "Creature — Beast", "{0}: Monstrosity 1.", "2/2")
        alice.battlefield(watcher("Giant Keeper", "a creature you control becomes monstrous"), beast)
        alice.activates(beast)
        settleAll()
        expect { alice.life shouldBe 21 }
        alice.activates(beast)
        settleAll()
        expect { alice.life shouldBe 21 }
    }

    // CR 701.37c: X in another ability of it is the X as it became monstrous: an amount
    @Test
    fun xIsTheXAsItBecameMonstrous() = scenario {
        val leech = Cards.invent("Rising Leech", "{0}", "Creature — Leech", """
            {X}{X}: Monstrosity X.
            When this creature becomes monstrous, you gain X life.
        """.trimIndent(), "1/1")
        alice.battlefield(leech, "Forest", "Forest", "Forest", "Forest")
        alice.activates(leech, x = 2, paying = listOf("Forest", "Forest#1", "Forest#2", "Forest#3"))
        settleAll()
        expect {
            leech.counters(arcanum.api.CounterKind.PowerToughness(1, 1)) shouldBe 2
            alice.life shouldBe 22
        }
    }

    // CR 701.37c: tokens, with X in their number and their power and toughness
    @Test
    fun monstrousXInTokens() = scenario {
        val brood = Cards.invent("Rising Brood", "{0}", "Creature — Hydra", """
            {X}{X}: Monstrosity X.
            When this creature becomes monstrous, create X X/X green Hydra creature tokens.
        """.trimIndent(), "1/1")
        alice.battlefield(brood, "Forest", "Forest", "Forest", "Forest")
        alice.activates(brood, x = 2, paying = listOf("Forest", "Forest#1", "Forest#2", "Forest#3"))
        settleAll()
        val tokens = state.battlefield.filter { state.objOrThrow(it).token }
        assertEquals(2, tokens.size)
        tokens.forEach { assertEquals(2, view()[it]!!.characteristics!!.power) }
    }

    // CR 701.37c, 115.1: the number of targets: X of 1 lets one be chosen, as the trigger goes on the Stack
    @Test
    fun monstrousXInTargets() = scenario {
        val warden = Cards.invent("Rising Warden", "{0}", "Creature — Giant", """
            {X}{X}: Monstrosity X.
            When this creature becomes monstrous, put a +1/+1 counter on each of up to X target creatures.
        """.trimIndent(), "1/1")
        alice.battlefield(warden, elf, "Forest", "Forest")
        alice.activates(warden, x = 1, paying = listOf("Forest", "Forest#1"))
        allPass()
        assertEquals(1, decision<arcanum.engine.decision.Decision.Targets>().max)
        answer(Answer.Targets(listOf(target(elf))))
        settleAll()
        expect { elf.counters(arcanum.api.CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    // CR 702.112a, b: renown gives the mark as its trigger resolves, on combat damage dealt to a player
    @Test
    fun whenACreatureBecomesRenowned() = scenario {
        val hero = Cards.invent("Rising Hero", "{0}", "Creature — Human", "Renown 1", "2/2")
        alice.battlefield(watcher("Fame Keeper", "a creature you control becomes renowned"), hero)
        advanceTo(arcanum.api.TurnStep.DeclareAttackers)
        alice.attacks(hero)
        advanceTo(arcanum.api.TurnStep.DeclareBlockers)
        if (state.priority.holder == null) bob.blocks()
        advanceTo(arcanum.api.TurnStep.CombatDamage)
        settleAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 21
        }
    }

    // CR 701.60a, d: a suspected permanent can't become suspected again, so a second suspicion doesn't trigger
    @Test
    fun whenACreatureBecomesSuspected() = scenario {
        val ledger = Cards.invent("Suspicious Ledger", "{0}", "Artifact", "{0}: Suspect target creature.")
        alice.battlefield(watcher("Accusation Keeper", "a creature becomes suspected"), ledger)
        bob.battlefield(elf)
        alice.activates(ledger, target = elf)
        settleAll()
        expect { alice.life shouldBe 21 }
        alice.activates(ledger, target = elf)
        settleAll()
        expect { alice.life shouldBe 21 }
    }

    // CR 722.3a: gaining the designation triggers; one without a prepare spell can't gain it, and one that has it can't gain it again
    @Test
    fun whenACreatureBecomesPrepared() = scenario {
        val seraph = Cards.inventFaces("Prepare",
            face("Ready Seraph", "{0}", "Creature — Angel", "{0}: This creature becomes prepared.", "2/4", null, null),
            face("Ready Mending", "{G}", "Sorcery", "You gain 1 life.", null, null, null))
        val plain = Cards.invent("Unready Seraph", "{0}", "Creature — Angel", "{0}: This creature becomes prepared.", "2/4")
        alice.battlefield(watcher("Ready Keeper", "a creature becomes prepared"), seraph, plain)
        alice.activates(plain)
        settleAll()
        expect { alice.life shouldBe 20 }
        alice.activates(seraph)
        settleAll()
        expect { alice.life shouldBe 21 }
        alice.activates(seraph)
        settleAll()
        expect { alice.life shouldBe 21 }
        assertTrue(ObjectMark.Prepared in state.objOrThrow(card(seraph)).marks)
    }

    // CR 702.171b: the same one pattern for a mark a keyword gives
    @Test
    fun whenACreatureBecomesSaddled() = scenario {
        val mount = Cards.invent("Rising Mount", "{0}", "Creature — Horse Mount", "Saddle 1", "3/2")
        alice.battlefield(watcher("Saddle Keeper", "a creature you control becomes saddled"), mount, elf)
        alice.activates(mount, choosing = listOf(elf))
        settleAll()
        expect { alice.life shouldBe 21 }
    }

    // ---- Other verbs, read by the same rule

    // CR 701.50a, f: a permanent connives once done; CR 701.50e: conniving 0 isn't an event
    @Test
    fun whenACreatureConnives() = scenario {
        val plotter = Cards.invent("Quiet Plotter", "{0}", "Creature — Rogue", "{0}: This creature connives.", "1/1")
        alice.battlefield(watcher("Whisper Keeper", "a creature you control connives"), plotter)
        alice.hand("Forest")
        alice.library("Forest")
        alice.activates(plotter)
        allPass()
        alice.chooses("Forest")
        settleAll()
        expect { alice.life shouldBe 21 }
    }

    private val eachCreatureConnives = spellWith("Grand Scheme", Effect.Action(KeywordAction.Connive(
        arcanum.api.Objects.Each(arcanum.api.ObjectFilter.Type(CardType.Creature)), Value.Fixed(1))))

    /** The connivers in the order they connived. */
    private fun Scenario.connived() = state.eventsOf(state.turn.number).mapNotNull { (it as? Change.Happened)?.event as? GameEvent.KeywordActionDone }
        .filter { it.action == KeywordActionKind.Connive }.flatMap { it.objects }

    /** Answers the discards that come, each with the card just drawn (the only one in hand). */
    private fun Scenario.discardEach() {
        repeat(4) {
            val pending = state.pending?.decision as? arcanum.engine.decision.Decision.ChooseObjects ?: return
            answer(Answer.Objects(listOf(pending.options.first())))
        }
    }

    // CR 701.50c: their controller chooses which connives next, which decides what each draws: the one chosen first draws the nonland on top,
    // discards it and gets its counter; the other then draws the land
    @Test
    fun theControllerChoosesWhichConnivesNext() = scenario {
        val first = Cards.invent("First Schemer", "{0}", "Creature — Rogue", "", "1/1")
        val other = Cards.invent("Second Schemer", "{0}", "Creature — Rogue", "", "1/1")
        alice.battlefield(first, other)
        alice.hand(eachCreatureConnives)
        alice.library("Storm Spark", "Forest")
        alice.castsWithoutCosts(eachCreatureConnives)
        val asked = resolve().let { decision<arcanum.engine.decision.Decision.ChooseObjects>() }
        assertEquals(setOf(card(first), card(other)), asked.options.toSet())
        alice.chooses(other)
        discardEach()
        settleAll()
        expect {
            other.counters(arcanum.api.CounterKind.PowerToughness(1, 1)) shouldBe 1
            first.counters(arcanum.api.CounterKind.PowerToughness(1, 1)) shouldBe 0
            alice.graveyard has "Storm Spark"
            alice.graveyard has "Forest"
        }
        assertEquals(listOf(card(other), card(first)), connived())
    }

    // CR 701.50c, 101.4: the first player in APNAP order goes first, whoever's spell it is
    @Test
    fun theActivePlayersPermanentsConniveFirst() = scenario {
        val mine = Cards.invent("Near Schemer", "{0}", "Creature — Rogue", "", "1/1")
        val theirs = Cards.invent("Far Schemer", "{0}", "Creature — Rogue", "", "1/1")
        turn(active = bob)
        alice.battlefield(mine)
        bob.battlefield(theirs)
        alice.library("Forest")
        bob.library("Island")
        bob.hand(eachCreatureConnives)
        bob.castsWithoutCosts(eachCreatureConnives)
        resolve()
        discardEach()
        settleAll()
        assertEquals(listOf(card(theirs), card(mine)), connived())
    }

    // CR 701.22b, d: scrying N with no library is scrying, the actions being impossible; scrying 0 isn't an event
    @Test
    fun scryingWithNoLibraryStillTriggersButScryingZeroDoesnt() = scenario {
        val orb = spellWith("Blank Orb", Effect.Scry(Players.You, Value.Fixed(1)))
        val none = spellWith("Closed Orb", Effect.Scry(Players.You, Value.Fixed(0)))
        alice.battlefield(watcher("Orb Keeper", "you scry"))
        alice.hand(orb, none)
        alice.castsWithoutCosts(none)
        settleAll()
        expect { alice.life shouldBe 20 }
        alice.castsWithoutCosts(orb)
        settleAll()
        expect { alice.life shouldBe 21 }
    }

    // CR 701.25c, d: the same for surveilling
    @Test
    fun surveilingWithNoLibraryStillTriggersButSurveilingZeroDoesnt() = scenario {
        val glass = spellWith("Blank Glass", Effect.Surveil(Players.You, Value.Fixed(1)))
        val none = spellWith("Closed Glass", Effect.Surveil(Players.You, Value.Fixed(0)))
        alice.battlefield(watcher("Glass Keeper", "you surveil"))
        alice.hand(glass, none)
        alice.castsWithoutCosts(none)
        settleAll()
        expect { alice.life shouldBe 20 }
        alice.castsWithoutCosts(glass)
        settleAll()
        expect { alice.life shouldBe 21 }
    }

    // ---- "This turn"

    // CR 701.34a: what happened this turn counts the same events as the triggers do
    @Test
    fun thisTurnCountsKeywordActions() = scenario {
        val press = Cards.invent("Counted Press", "{0}", "Artifact", "{0}: Proliferate.")
        alice.battlefield(press)
        val did = Condition.HappenedThisTurn(EventPattern.KeywordActionDone(KeywordActionKind.Proliferate, PlayerFilter.You), atLeast = 1, measure = Measure.Events)
        assertEquals(false, Snapshot(state).evaluator(alice.id).holds(did))
        alice.activates(press)
        settleAll()
        assertEquals(true, Snapshot(state).evaluator(alice.id).holds(did))
    }

    /**
     * For each verb, the general pattern meets the general event: one instance, the player who did it, the amount kept, and none for the
     * other player (CR 701.22d, 701.25d, 701.61a, 701.65b, 701.66b, 701.67c, 702.174c, 702.189b).
     */
    @Test
    fun theGeneralPatternMeetsTheGeneralEventForEachVerb() = scenario {
        val p = alice.id
        val verbs = listOf<Pair<KeywordActionKind, Int?>>(
            KeywordActionKind.Scry to 2,
            KeywordActionKind.Surveil to 2,
            KeywordActionKind.Forage to null,
            KeywordActionKind.GiveGift to null,
            KeywordActionKind.Airbend to null,
            KeywordActionKind.Earthbend to null,
            KeywordActionKind.Firebend to null,
            KeywordActionKind.Waterbend to null,
        )
        val yours = Snapshot(state).evaluator(alice.id)
        val theirs = Snapshot(state).evaluator(bob.id)
        verbs.forEach { (kind, amount) ->
            val pattern = EventPattern.KeywordActionDone(kind, PlayerFilter.You)
            val event = Change.Happened(GameEvent.KeywordActionDone(kind, p, amount = amount))
            val found = EventMatcher.instances(yours, pattern, event)
            assertEquals(1, found.size, "$pattern on $event")
            assertEquals(listOf(p), found.single().players)
            assertEquals(amount, found.single().amount)
            assertEquals(0, EventMatcher.instances(theirs, pattern, event).size, "$pattern on $event, seen by the other player")
        }
    }

    private fun gains(event: EventPattern) = CardDefinition(Layout.Normal, listOf(FaceDefinition(
        "A Keeper", types = listOf(CardType.Enchantment), abilities = listOf(Ability.Triggered(Trigger.On(event),
            Instructions(steps = listOf(Step.Do(Effect.GainLife(Players.You, Value.Fixed(1))))))))))

    private fun spellWith(name: String, effect: Effect): String = Cards.define(name, CardDefinition(Layout.Normal, listOf(FaceDefinition(
        name, types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(steps = listOf(Step.Do(effect)))))))))
}
