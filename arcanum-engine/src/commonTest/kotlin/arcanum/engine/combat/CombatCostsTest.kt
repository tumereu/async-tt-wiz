package arcanum.engine.combat

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.KeywordAction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AbilityOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Costs to attack and block other than mana (CR 508.1h, 509.1d), optional "as it attacks" costs (CR 508.1g) with exert
 * (CR 701.43) and its "when you do" trigger, and exert as an activated ability's cost. The cards are written by hand from
 * parsed parts: the parser doesn't read these wordings yet.
 */
class CombatCostsTest {
    private fun ability(text: String, base: String): Ability =
        Cards.definition(Cards.invent(base, "{1}", "Creature — Construct", text, "1/1")).faces.single().abilities.single()

    private fun define(name: String, cost: String, type: String, pt: String?, vararg abilities: Ability): String {
        val base = Cards.definition(Cards.invent("$name Base", cost, type, "", pt)).faces.single()
        return Cards.define(name, CardDefinition(arcanum.api.Layout.Normal, listOf(base.copy(name = name, abilities = abilities.toList()))))
    }

    private val exertSelf = Cost(listOf(CostPart.Action(KeywordAction.Exert(Objects.Self))))

    /** "You may exert this creature as it attacks. When you do, it gets +2/+0 until end of turn." */
    private val striker = define("Dune Exert Striker", "{2}{R}", "Creature — Human Warrior", "3/3",
        Ability.Static(listOf(Mechanic.AsAttacks(exertSelf,
            (ability("Whenever this creature attacks, it gets +2/+0 until end of turn.", "Dune Striker Text") as Ability.Triggered).instructions)),
            Affected.Objects(Objects.Self)))

    /** "{T}, Exert this creature: Draw a card." */
    private val scholar = define("Exerting Dune Scholar", "{1}{U}", "Creature — Human Wizard", "1/1",
        (ability("{T}: Draw a card.", "Dune Scholar Text") as Ability.Activated).let { it.copy(cost = Cost(it.cost.parts + exertSelf.parts)) })

    /** "Creatures can't attack you unless their controller sacrifices a land for each creature they control that's attacking you." */
    private val moat = define("Sinking Moat", "{2}{W}", "Enchantment", null,
        Ability.Static(listOf(Mechanic.AttackCost((ability("Sacrifice a land: Draw a card.", "Sinking Moat Text") as Ability.Activated).cost,
            target = Recipients(players = Players.You))), Affected.Objects(Objects.Each(ObjectFilter.Type(arcanum.api.CardType.Creature)))))

    /** "Creatures can't block unless their controller pays 2 life for each creature they control that's blocking." */
    private val tollGate = define("Toll of Blood Gate", "{1}{B}", "Enchantment", null,
        Ability.Static(listOf(Mechanic.BlockCost(Cost(listOf(CostPart.PayLife(arcanum.api.Value.Fixed(2)))))),
            Affected.Objects(Objects.Each(ObjectFilter.Type(arcanum.api.CardType.Creature)))))

    /** "This creature attacks each combat if able." */
    private val eager = Cards.invent("Eager Moat Raider", "{1}{R}", "Creature — Goblin", "This creature attacks each combat if able.", "2/2")
    private val bear = Cards.invent("Plain Moat Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    private fun exertOrigin(): AbilityOrigin = AbilityOrigin.Printed(0, 0)

    private fun Scenario.libraries() {
        alice.library("Island", "Island", "Island", "Island")
        bob.library("Island", "Island", "Island", "Island")
    }

    // CR 508.1g, 701.43d: exerting is an optional cost to attack; CR 607.2h: its "when you do" triggers once it is paid
    @Test
    fun exertingAsItAttacks() = scenario {
        alice.battlefield(striker)
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(striker), TargetRef.Player(bob.id), paying = listOf(exertOrigin())))))
        allPass()
        expect { striker.power shouldBe 5 }
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 15 }
    }

    // CR 701.43a: an exerted creature doesn't untap during its controller's next untap step, and untaps in the one after
    @Test
    fun exertedDoesntUntapNextTime() = scenario {
        alice.battlefield(striker)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(striker), TargetRef.Player(bob.id), paying = listOf(exertOrigin())))))
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { striker.tapped shouldBe true }
        advanceTo(TurnStep.Upkeep, turn = 5)
        expect { striker.tapped shouldBe false }
    }

    // CR 508.1g: an optional cost needn't be paid: the attack is an ordinary one
    @Test
    fun attackingWithoutExerting() = scenario {
        alice.battlefield(striker)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(striker)
        allPass()
        expect { striker.power shouldBe 3 }
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { striker.tapped shouldBe false }
    }

    // CR 508.1g: only the attacker's own optional costs can be chosen; the options offer the declaration with them paid
    @Test
    fun optionalCostsInOptions() = scenario {
        alice.battlefield(striker, bear)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> {
            perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(bear), TargetRef.Player(bob.id), paying = listOf(exertOrigin())))))
        }
        val declarations = Engine.options(state, alice.id).filterIsInstance<Input.DeclareAttackers>()
        assertTrue(declarations.any { d -> d.attacks.any { it.attacker == card(striker) && it.paying == listOf(exertOrigin()) } })
        assertTrue(declarations.any { d -> d.attacks.any { it.attacker == card(striker) && it.paying.isEmpty() } })
    }

    // CR 701.43a, b: exert as an activated ability's cost: the source doesn't untap during its controller's next untap step
    @Test
    fun exertAsActivationCost() = scenario {
        alice.battlefield(scholar)
        libraries()
        alice.activates(scholar)
        allPass()
        expect { alice.hand.size shouldBe 1 }
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { scholar.tapped shouldBe true }
    }

    // CR 508.1h, j: a cost to attack other than mana (sacrificing a land) is paid as attackers are declared
    @Test
    fun sacrificeToAttack() = scenario {
        alice.battlefield(bear, "Forest", "Forest")
        bob.battlefield(moat)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        alice.chooses("Forest")
        expect {
            alice.graveyard.names shouldBe listOf("Forest")
        }
        assertEquals(listOf(card(bear)), state.combat?.attackers?.map { it.id })
    }

    // CR 508.1d: a declaration whose cost can't be paid isn't made, and isn't offered
    @Test
    fun cantPayToAttack() = scenario {
        alice.battlefield(bear)
        bob.battlefield(moat)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(bear) }
        val declarations = Engine.options(state, alice.id).filterIsInstance<Input.DeclareAttackers>()
        assertEquals(listOf(emptyList()), declarations.map { it.attacks })
    }

    // CR 508.1d: a creature that attacks each combat if able needn't attack when attacking would ask a cost
    @Test
    fun requirementDoesntForceACost() = scenario {
        alice.battlefield(eager, "Forest")
        bob.battlefield(moat)
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, emptyList()))
        expect { alice.graveyard.size shouldBe 0 }
    }

    // CR 509.1d, f: a cost to block (life) is paid as blockers are declared, once for each blocker
    @Test
    fun payLifeToBlock() = scenario {
        alice.battlefield(bear)
        bob.battlefield(tollGate, "Umbral Elves")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks("Umbral Elves" to bear)
        expect {
            bob.life shouldBe 18
        }
        assertEquals(listOf(card("Umbral Elves")), state.combat?.blockers?.map { it.id })
    }

    // CR 509.1d: a block that can't be paid for isn't made; blocking nothing is free
    @Test
    fun cantPayToBlock() = scenario {
        alice.battlefield(bear)
        bob.battlefield(tollGate, "Umbral Elves")
        bob.life = 1
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { perform(Input.DeclareBlockers(bob.id, listOf(Block(card("Umbral Elves"), card(bear))))) }
        val declarations = Engine.options(state, bob.id).filterIsInstance<Input.DeclareBlockers>()
        assertEquals(listOf(emptyList()), declarations.map { it.blocks })
    }
}
