package arcanum.engine.sets.dmr

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.aliceUpkeep
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs

/** DMR fix step: general engine fixes the set's chunks found, each with the rule it follows. */
class DmrFixesScenariosTest {
    private val eel = Cards.invent("Shrouded Eel", "{U}", "Creature — Serpent",
        "This creature can't attack unless defending player controls an Island.", "4/4")
    private val siege = Cards.inventFaces("Transform",
        OracleCard("Siege of the Salt Flats", "{2}{R}", "Battle — Siege", "", defense = "3"),
        OracleCard("Salt Flats Colossus", null, "Creature — Elemental", "", power = "4", toughness = "4", colorIndicator = "R"),
    )

    // CR 506.2, 508.1b: attacking a battle, the defending player is its protector: the Eel can't attack a battle protected by a
    // player without an Island (3 players: the protector is chosen among the opponents)
    @Test
    fun cantAttackABattleWhoseProtectorHasNoIsland() = scenario(players = 3) {
        alice.battlefield(eel, "Mountain", "Mountain", "Mountain")
        bob.battlefield("Island")
        carol.battlefield("Forest")
        alice.hand(siege)
        alice.casts(siege)
        allPass()
        assertIs<Decision.ChoosePlayer>(pending)
        answer(Answer.Player(carol.id))
        advanceTo(TurnStep.DeclareAttackers)
        assertFails { perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(eel), TargetRef.Object(card(siege)))))) }
        alice.attacks(eel, defender = "bob")
    }

    @Test
    fun attacksABattleWhoseProtectorHasAnIsland() = scenario(players = 3) {
        alice.battlefield(eel, "Mountain", "Mountain", "Mountain")
        bob.battlefield("Island")
        carol.battlefield("Forest")
        alice.hand(siege)
        alice.casts(siege)
        allPass()
        answer(Answer.Player(bob.id))
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(eel), TargetRef.Object(card(siege))))))
    }

    private val seizure = Cards.invent("Spine Graft Seizure", "{3}{U}{U}{B}", "Instant", """
        Cast this spell only during combat.
        Untap target creature you don't control and gain control of it. It gains haste until end of turn. At the beginning of the next end step, sacrifice it. If you do, you gain life equal to its toughness.
    """)
    private val grasp = Cards.invent("Reclaiming Grasp", "{2}{R}", "Instant",
        "Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn.")
    private val mule = Cards.invent("Harrier Mule", "{2}{G}", "Creature — Ox", "", "2/3")

    // CR 701.21a: a player can sacrifice only a permanent they control: once bob has taken his creature back, alice's delayed
    // "sacrifice it" does nothing, and "If you do" doesn't happen
    @Test
    fun delayedSacrificeOfAPermanentAnotherPlayerNowControls() = scenario {
        bob.battlefield(mule)
        alice.hand(seizure)
        bob.hand(grasp)
        turn(step = TurnStep.BeginningOfCombat)
        alice.castsWithoutCosts(seizure, target = mule)
        resolve()
        expect { mule.controller shouldBe alice.id }
        alice.passes()
        bob.castsWithoutCosts(grasp, target = mule)
        resolve()
        expect { mule.controller shouldBe bob.id }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.battlefield has mule
            alice.life shouldBe 20
        }
    }

    private val hounds = Cards.invent("Tussock Hounds", "{G}", "Creature — Dog", """
        At the beginning of your upkeep, if a player has more life than each other player, the player with the most life gains control of this creature.
    """, "2/1")
    private val windfall = Cards.invent("Verdant Windfall", "{G}", "Instant", "Target player gains 10 life.")

    // CR 608.2h, 611.2c: the player with the most life is found as the ability resolves; when another player later has the most life,
    // control doesn't move again without a new trigger (3 players)
    @Test
    fun controlGoesToThePlayerWithTheMostLifeOnResolutionOnly() = scenario(players = 3) {
        alice.battlefield(hounds)
        alice.hand(windfall)
        bob.life = 25
        carol.life = 21
        aliceUpkeep()
        resolveAll()
        expect { hounds.controller shouldBe bob.id }
        alice.castsWithoutCosts(windfall, target = "carol")
        resolve()
        expect {
            carol.life shouldBe 31
            hounds.controller shouldBe bob.id
        }
    }

    private val bulwark = Cards.invent("Ancestral Bulwark", "{7}", "Legendary Artifact", """
        If Ancestral Bulwark would be put into a graveyard from anywhere, reveal Ancestral Bulwark and shuffle it into its owner's library instead.
    """)
    private val grinder = Cards.invent("Silt Grinder", "{1}{U}", "Sorcery", "Target player mills two cards.")

    // CR 614.6, 701.17a: the replacement replaces only the Bulwark's part of the mill: it is shuffled into the library, the other
    // milled card is still put into the graveyard
    @Test
    fun millingAReplacedCardStillMillsTheOther() = scenario {
        alice.library(bulwark, "Island", "Forest")
        alice.hand(grinder)
        alice.castsWithoutCosts(grinder, target = "alice")
        resolve()
        expect {
            alice.graveyard has "Island"
            alice.graveyard lacks bulwark
            alice.library has bulwark
        }
        assertEquals(2, state.player(alice.id).library.size)
    }

    private val raider = Cards.invent("Flashstep Raider", "{1}{R}", "Creature — Human Warrior", """
        You may cast this spell as though it had flash. If you cast it any time a sorcery couldn't have been cast, the controller of the permanent it becomes sacrifices it at the beginning of the next cleanup step.
    """, "2/2")
    private val silence = Cards.invent("Stilling Silence", "{2}{U}", "Enchantment", "Creatures lose all abilities.")

    // CR 603.7a, 601.2i: the delayed trigger is created as the spell is cast, by the static ability that let it be cast: the permanent
    // it becomes is sacrificed at the next cleanup step even though it has no abilities on the battlefield
    @Test
    fun flashCastPermanentIsSacrificedEvenWithoutAbilities() = scenario {
        turn(number = 1, active = bob, step = TurnStep.Upkeep)
        alice.battlefield("Mountain", "Mountain")
        bob.battlefield(silence)
        alice.hand(raider)
        alice.library("Mountain")
        bob.library("Forest", "Forest")
        bob.passes()
        alice.casts(raider)
        allPass()
        expect { card(raider).zone shouldBe Zone.Battlefield }
        advanceTo(TurnStep.Cleanup)
        expect { stack.size shouldBe 1 }
        allPass()
        expect { card(raider).zone shouldBe Zone.Graveyard }
    }

    private val spun = Cards.invent("Bracer of Valor Spun", "{5}", "Artifact", """
        As this artifact enters, choose a color.
        Creatures of the chosen color get +1/+1.
    """)

    // CR 117.3b: after a resolution the active player receives priority, also when the resolution stopped for an "as enters" choice
    // (test harness: a spell put on the Stack without costs, then resolved by all players passing)
    @Test
    fun priorityAfterAnAsEntersChoiceOfASpellCastWithoutCosts() = scenario {
        alice.battlefield("Forest")
        alice.hand(spun)
        alice.castsWithoutCosts(spun)
        resolveAll()
        alice.answers(Answer.ColorChoice(arcanum.api.Color.Green))
        assertEquals(alice.id, state.priority.holder)
        alice.activates("Forest")
        expect { alice.pool.size shouldBe 1 }
    }
}
