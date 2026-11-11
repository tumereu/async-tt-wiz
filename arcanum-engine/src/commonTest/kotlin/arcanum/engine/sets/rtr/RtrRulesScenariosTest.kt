package arcanum.engine.sets.rtr

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Return to Ravnica's rules pieces in play: a cost to attack, a cost of cards sharing a name, a discard an opponent causes,
 * creatures that dealt damage, two chosen colours, a spell exiled from the Stack as a cost, permanents sharing a name,
 * two targets with one creature, creatures returned as black Zombies, redirected damage, a graveyard-hating enchantment.
 */
class RtrRulesScenariosTest {
    private val bulwark = Cards.invent("Bulwark of Statutes", "{4}{W}", "Enchantment",
        "Creatures can't attack you or planeswalkers you control unless their controller pays {X} for each of those creatures, where X is the number of enchantments you control.")
    private val sphinx = Cards.invent("Sphinx of the Bells", "{4}{U}{U}", "Creature — Sphinx", """
        Flying
        Discard two nonland cards with the same name: Draw four cards.
    """, "5/6")
    private val bruiser = Cards.invent("Tuskguard Bruiser", "{1}{G}{W}", "Creature — Elephant Soldier", """
        This spell can't be countered.
        If a spell or ability an opponent controls causes you to discard this card, put it onto the battlefield instead of putting it into your graveyard.
    """, "4/4")
    private val rot = Cards.invent("Thought Rot", "{2}{B}", "Sorcery", "Target player discards two cards.")
    private val combustion = Cards.invent("Idea Combustion", "{3}{U}{R}", "Instant", "Draw four cards, then discard two cards.")
    private val bolt = Cards.invent("Retribution Bolt", "{2}{W}", "Instant", "Destroy target creature that dealt damage this turn.")
    private val slate = Cards.invent("Slate of the Factions", "{2}", "Artifact", """
        As this artifact enters, choose two colors.
        Whenever you cast a spell, if it's at least one of the chosen colors, you gain 1 life for each of the chosen colors it is.
    """)
    private val gorger = Cards.invent("Spellgorger Elemental", "{U/R}", "Creature — Elemental",
        "Exile an instant or sorcery spell you control: Put two +1/+1 counters on this creature. (That spell won't resolve.)", "1/2")
    private val orb = Cards.invent("Containment Orb", "{1}{W}{U}", "Enchantment", """
        When this enchantment enters, you may exile target nonland permanent not named Containment Orb and all other permanents with the same name as that permanent.
        When this enchantment leaves the battlefield, return the exiled cards to the battlefield under their owner's control.
    """)
    private val vow = Cards.invent("Shared Vow", "{1}{G}{W}", "Instant", """
        Put a +1/+1 counter on target creature.
        Put a +1/+1 counter on target creature.
    """)
    private val treachery = Cards.invent("Tomb Treachery", "{5}{B}{B}", "Enchantment",
        "Whenever a creature you don't control dies, return it to the battlefield under your control with an additional +1/+1 counter on it at the beginning of the next end step. That creature is a black Zombie in addition to its other colors and types.")
    private val colossus = Cards.invent("Rampart Colossus", "{4}{W}{W}", "Creature — Giant Soldier",
        "All damage that would be dealt to you and other permanents you control is dealt to this creature instead.", "2/7")
    private val repose = Cards.invent("Eternal Repose", "{1}{W}", "Enchantment", """
        When this enchantment enters, exile all graveyards.
        If a card or token would be put into a graveyard from anywhere, exile it instead.
    """)

    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val wolf = Cards.invent("Alley Hound", "{1}{G}", "Creature — Dog", "", "2/2")
    private val shock = Cards.invent("Street Spark", "{R}", "Instant", "Street Spark deals 2 damage to any target.")
    private val insight = Cards.invent("Quick Insight", "{1}{U}", "Instant", "Draw a card.")
    private val azoriusSpell = Cards.invent("Senate Decree", "{W}{U}", "Sorcery", "You gain 1 life.")
    private val redSpell = Cards.invent("Hot Words", "{R}", "Sorcery", "You gain 1 life.")
    private val sigil = Cards.invent("Plain Sigil", "{1}", "Enchantment")
    private val disenchant = Cards.invent("Purge the Wards", "{1}{W}", "Instant", "Destroy target enchantment.")
    private val murder = Cards.invent("Quick Execution", "{1}{B}{B}", "Instant", "Destroy target creature.")

    // ---- A cost to attack (CR 508.1d, 508.1h)

    /** {X} for each creature attacking the player, X the number of their enchantments: paid as attackers are declared. */
    // CR 508.1h, 508.1j
    @Test
    fun attackingPaysTheCost() = scenario {
        turn(1, active = bob)
        alice.battlefield(bulwark, sigil)
        bob.battlefield(bear, "Forest", "Forest", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(bear)
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect {
            bear.tapped shouldBe true
            card("Forest", 0).tapped shouldBe true
            card("Forest", 1).tapped shouldBe true
            card("Forest", 2).tapped shouldBe false
        }
        assertTrue(state.combat!!.attackers.any { it.id == card(bear) })
    }

    /** Before the declaration a host reads the cost as it stands, X worked out from the board: {2} with two enchantments,
     * {3} once a third enters (Engine.combatCost). */
    // CR 508.1h
    @Test
    fun theCostToAttackReadsWithItsXWorkedOut() = scenario {
        turn(1, active = bob)
        alice.battlefield(bulwark, sigil)
        bob.battlefield(bear)
        fun cost(): String {
            val applied = Engine.view(state)[card(bear)]!!.mechanics.single { it.mechanic is arcanum.api.Mechanic.AttackCost }
            return Engine.combatCost(state, applied, card(bear))!!.parts.joinToString { (it as arcanum.api.CostPart.Mana).cost.toString() }
        }
        assertEquals("{2}", cost())
        alice.battlefield(sigil)
        assertEquals("{3}", cost())
    }

    /** A declaration whose cost can't be paid isn't made: refused, and not offered (CR 508.1d). */
    // CR 508.1d
    @Test
    fun unpayableAttackIsRefused() = scenario {
        turn(1, active = bob)
        alice.battlefield(bulwark, sigil)
        bob.battlefield(bear, wolf, "Forest", "Forest", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        val attackBoth = Input.DeclareAttackers(bob.id, listOf(Attack(card(bear), TargetRef.Player(alice.id)), Attack(card(wolf), TargetRef.Player(alice.id))))
        assertTrue(Engine.options(state, bob.id).none { it == attackBoth })
        assertFailsWith<IllegalArgumentException> { perform(attackBoth) }
    }

    // ---- Two cards with the same name (CR 201.2)

    @Test
    fun discardTwoWithTheSameName() = scenario {
        alice.battlefield(sphinx)
        alice.hand(insight, insight, shock)
        alice.library("Island", "Island", "Island", "Island")
        assertTrue(Engine.options(state, alice.id).any { it is Input.Activate && it.source == card(sphinx) })
        alice.activates(sphinx)
        val d = decision<Decision.ChooseObjects>()
        assertEquals(setOf(card(insight, 0), card(insight, 1)), d.options.toSet())
        alice.chooses(insight, insight)
        allPass()
        expect { alice.hand.size shouldBe 5 }
    }

    /** Without two cards sharing a name the ability can't be activated. */
    @Test
    fun noPairNoActivation() = scenario {
        alice.battlefield(sphinx)
        alice.hand(insight, shock)
        assertTrue(Engine.options(state, alice.id).none { it is Input.Activate && it.source == card(sphinx) })
    }

    // ---- A discard an opponent's spell causes (CR 701.9)

    @Test
    fun opponentsDiscardPutsItOntoTheBattlefield() = scenario {
        alice.hand(bruiser, insight)
        bob.hand(rot)
        bob.castsWithoutCosts(rot, target = "alice")
        resolve()
        expect {
            alice.battlefield has bruiser
            alice.graveyard.names shouldBe listOf(insight)
        }
    }

    /** Discarded by its owner's own spell, it goes to the graveyard. */
    @Test
    fun ownDiscardDoesNot() = scenario {
        alice.hand(combustion, bruiser, insight)
        alice.library("Island", "Island", "Island", "Island")
        alice.castsWithoutCosts(combustion)
        resolve()
        alice.chooses(bruiser, insight)
        expect { alice.graveyard has bruiser }
    }

    // ---- A creature that dealt damage this turn

    /** The source of damage this turn can be targeted; the creature dealt damage can't. */
    @Test
    fun creatureThatDealtDamage() = scenario {
        alice.battlefield(bear)
        bob.battlefield(wolf)
        alice.hand(bolt)
        // The wolf deals 2 damage to the bear (fight-like, by hand)
        drive { r ->
            val act = arcanum.engine.action.Act(r, arcanum.engine.action.Asker.NONE)
            arcanum.engine.action.Actions.damage(act, arcanum.engine.action.Cause(bob.id, card(wolf), key = "test"),
                listOf(arcanum.engine.change.DamageDealt(card(wolf), TargetRef.Object(card(bear)), 1)))
            arcanum.engine.Progress.Done
        }
        alice.casts(bolt)
        val d = decision<Decision.Targets>()
        assertEquals(listOf(target(wolf)), d.options)
    }

    // ---- Two chosen colours (CR 607.2d)

    @Test
    fun lifeForEachChosenColorTheSpellIs() = scenario {
        alice.hand(slate, azoriusSpell, redSpell)
        alice.castsWithoutCosts(slate)
        resolve()
        answer(Answer.ColorChoice(Color.White))
        answer(Answer.ColorChoice(Color.Blue))
        alice.castsWithoutCosts(azoriusSpell)
        settle()
        resolve()
        expect { alice.life shouldBe 22 }
        resolve()
        expect { alice.life shouldBe 23 }
        alice.castsWithoutCosts(redSpell)
        settle()
        expect { stack.size shouldBe 1 }
    }

    // ---- A spell exiled from the Stack as a cost (CR 405.1)

    @Test
    fun exileASpellYouControl() = scenario {
        alice.battlefield(gorger, "Island", "Island")
        alice.hand(insight)
        alice.library("Island")
        alice.casts(insight)
        alice.activates(gorger, choosing = listOf(insight))
        allPass()
        expect {
            gorger.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            alice.exile.names shouldBe listOf(insight)
            alice.hand.isEmpty()
        }
    }

    // ---- Permanents sharing a name, linked exile (CR 201.2, 607.2a)

    @Test
    fun exileAllWithTheSameNameUntilItLeaves() = scenario {
        alice.hand(orb)
        bob.hand(disenchant)
        bob.battlefield(bear, bear, wolf)
        alice.castsWithoutCosts(orb)
        resolve()
        answer(Answer.Targets(listOf(TargetRef.Object(card(bear, 0)))))
        allPassAnd()
        answer(Answer.YesNo(true))
        expect {
            bob.exile.names shouldBe listOf(bear, bear)
            bob.battlefield.names shouldBe listOf(wolf)
        }
        bob.castsWithoutCosts(disenchant, target = orb)
        resolve()
        resolve()
        expect { bob.battlefield.names shouldBe listOf(wolf, bear, bear) }
    }

    private fun arcanum.engine.scenario.Scenario.allPassAnd() {
        if (pending == null) resolve()
    }

    // ---- One creature as both targets (CR 115.3)

    @Test
    fun sameCreatureForBothTargets() = scenario {
        alice.battlefield(bear)
        alice.hand(vow)
        alice.castsWithoutCosts(vow, targets = listOf(bear, bear))
        resolve()
        expect { bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2 }
    }

    // ---- A delayed return as a black Zombie (CR 603.7, 611.2e)

    @Test
    fun opponentsCreatureReturnsAsYourZombie() = scenario {
        alice.battlefield(treachery)
        alice.hand(murder)
        bob.battlefield(wolf)
        alice.castsWithoutCosts(murder, target = wolf)
        resolve()
        resolve()
        expect { bob.graveyard has wolf }
        advanceTo(TurnStep.End)
        resolveStack()
        expect {
            alice.battlefield has wolf
            wolf.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            wolf.colors.toSet() shouldBe setOf(Color.Green, Color.Black)
            wolf.subtypes shouldBe listOf(Subtype("Dog"), Subtype("Zombie"))
        }
    }

    // ---- Redirection (CR 614.9)

    @Test
    fun damageToYouIsDealtToItInstead() = scenario {
        alice.battlefield(colossus, bear)
        bob.hand(shock, shock)
        bob.castsWithoutCosts(shock, target = "alice")
        resolve()
        bob.castsWithoutCosts(shock, target = bear)
        resolve()
        expect {
            alice.life shouldBe 20
            card(bear).zone shouldBe Zone.Battlefield
            card(colossus).view.obj!!.damage shouldBe 4
        }
    }

    // ---- Every graveyard (CR 614.1a)

    @Test
    fun graveyardsExiledAndCardsExiledInstead() = scenario {
        alice.graveyard(insight)
        bob.graveyard(shock)
        bob.battlefield(wolf)
        alice.hand(repose, murder)
        alice.castsWithoutCosts(repose)
        resolve()
        resolve()
        expect {
            alice.exile has insight
            bob.exile has shock
        }
        alice.castsWithoutCosts(murder, target = wolf)
        resolve()
        expect {
            bob.exile has wolf
            alice.exile has murder
            alice.graveyard.isEmpty()
        }
    }
}
