package arcanum.engine.sets.isd

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** ISD chunk 11: the {0} alternative cost for the Zombie creature spells only its controller casts (CR 118.9), a trigger on attacking or
 * blocking that pumps other Humans, and blocking restrictions (CR 509.1b). Invented names carry the oracle text of the real cards. */
class IsdChunk11CostsAndRestrictionsScenariosTest {
    private val gale = Cards.invent("Rooftop Gale", "{5}{U}", "Enchantment",
        "You may pay {0} rather than pay the mana cost for Zombie creature spells you cast.")
    private val ghoul = Cards.invent("Cellar Ghoul", "{4}{B}", "Creature — Zombie", "", "4/4")
    private val warden = Cards.invent("Hamlet Warden", "{1}{G}", "Creature — Human Warrior",
        "Whenever this creature attacks or blocks, other Humans you control get +1/+1 until end of turn.", "2/2")
    private val villager = Cards.invent("Hamlet Villager", "{1}{W}", "Creature — Human Peasant", "", "1/1")
    private val hound = Cards.invent("Hamlet Hound", "{1}{W}", "Creature — Dog", "", "1/1")
    private val wisp = Cards.invent("Orchard Wisp", "{2}{G}", "Creature — Spirit",
        "This creature can't be blocked except by creatures with flying or reach.", "2/2")
    private val bat = Cards.invent("Orchard Bat", "{1}{B}", "Creature — Bat", "Flying", "1/1")
    private val spider = Cards.invent("Orchard Spider", "{1}{G}", "Creature — Spider", "Reach", "1/3")
    private val bear = Cards.invent("Orchard Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val lurker = Cards.invent("Vampire Lurker", "{1}{B}", "Creature — Vampire Scout", """
        Flying
        This creature can't block.
    """, "2/1")
    private val raider = Cards.invent("Orchard Raider", "{1}{R}", "Creature — Goblin", "", "2/2")

    private fun Scenario.castsOf(player: ScenarioPlayer, name: String) = Engine.options(state, player.id)
        .filterIsInstance<Input.Cast>().filter { it.card == card(name) }

    // CR 118.9, 601.2f: "Zombie creature spells you cast": the enchantment's controller gets the {0} way, an opponent casting a Zombie does not
    @Test
    fun opponentsZombieSpellsHaveNoFreeWay() = scenario(players = 3) {
        alice.battlefield(gale)
        bob.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        bob.hand(ghoul)
        turn(number = 2, active = bob)
        val casts = castsOf(bob, "Cellar Ghoul")
        assertTrue(casts.isNotEmpty(), "Bob can cast the Zombie for its mana cost")
        assertTrue(casts.all { it.way.alternative == null }, "but not for {0}")
    }

    // CR 508.1m, 603.2: the trigger fires when it attacks, and only the other Humans get +1/+1, not the Dog or itself
    @Test
    fun wardenPumpsOtherHumansWhenItAttacks() = scenario {
        alice.battlefield(warden, villager, hound)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(warden)
        resolveAll()
        expect {
            villager.power shouldBe 2
            hound.power shouldBe 1
            warden.power shouldBe 2
        }
    }

    // CR 509.3b: it triggers on blocking too
    @Test
    fun wardenPumpsOtherHumansWhenItBlocks() = scenario {
        alice.battlefield(warden, villager)
        bob.battlefield(raider)
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(raider)
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(warden to raider)
        resolveAll()
        expect { villager.power shouldBe 2 }
    }

    // CR 509.1b: only a creature with flying or reach can block the Wisp
    @Test
    fun wispCanBeBlockedOnlyByFlyingOrReach() = scenario {
        alice.battlefield(wisp)
        bob.battlefield(bear, bat, spider)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wisp)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(bear to wisp) }
        bob.blocks(bat to wisp)
    }

    // CR 509.1b: reach is enough
    @Test
    fun wispCanBeBlockedByReach() = scenario {
        alice.battlefield(wisp)
        bob.battlefield(spider)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wisp)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(spider to wisp)
    }

    // CR 509.1a: a creature that can't block is not declared as a blocker, the other can
    @Test
    fun lurkerCantBlock() = scenario {
        alice.battlefield(raider)
        bob.battlefield(lurker, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(raider)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(lurker to raider) }
        bob.blocks(bear to raider)
    }
}
