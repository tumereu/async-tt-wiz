package arcanum.engine.casting

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [Engine.options] (problem report KX8GYS): a cast or activation whose total cost is only known after a choice made while
 * casting it (CR 601.2f comes after 601.2b-e) is listed when some choice makes it payable, and the choice's answers are those
 * that do. Before, the cost before any choice decided: a spell costing {4}{W}, {2} less on a tapped creature, was never listed
 * with four lands.
 */
class CostFallsAfterChoiceOptionsTest {
    private val ox = Cards.invent("Falls Plain Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val bear = Cards.invent("Falls Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val banish = Cards.invent("Falls Banishing", "{4}{W}", "Sorcery",
        "This spell costs {2} less to cast if it targets a tapped creature.\nExile target creature.")
    private val aura = Cards.invent("Falls Wing Clip", "{3}{W}", "Enchantment — Aura",
        "Enchant creature\nThis spell costs {2} less to cast if it targets a creature with flying.\nEnchanted creature gets -1/-0.")
    private val flier = Cards.invent("Falls Plain Bird", "{1}{W}", "Creature — Bird", "Flying", "1/1")
    private val burst = Cards.invent("Falls Burst", "{X}{3}{R}", "Sorcery",
        "This spell costs {3} less to cast if it targets a tapped creature.\nThis spell deals X damage to target creature.")
    private val charm = Cards.invent("Falls Charm", "{4}{W}", "Sorcery",
        "This spell costs {2} less to cast if it targets a tapped creature.\nChoose one —\n• Exile target creature.\n• Tap target creature.")
    private val kicked = Cards.invent("Falls Kicked Banishing", "{4}{W}", "Sorcery",
        "Kicker {1}\nThis spell costs {2} less to cast if it targets a tapped creature.\nExile target creature.")
    private val tapper = Cards.invent("Falls Tapper", "{W}", "Creature — Human", "{3}, {T}: Tap target creature. This ability costs {2} less to activate if it targets a creature with flying.", "1/1")
    private val blade = Cards.invent("Falls Blade", "{1}", "Artifact — Equipment",
        "Equipped creature gets +1/+0.\nEquip {3}. This ability costs {1} less to activate for each +1/+1 counter on the creature it targets.")
    private val hero = Cards.invent("Falls Hero", "{1}{W}", "Legendary Creature — Human", "Equip abilities you activate that target Falls Hero cost {2} less to activate.", "2/2")
    private val plainBlade = Cards.invent("Falls Plain Blade", "{1}", "Artifact — Equipment", "Equipped creature gets +1/+0.\nEquip {3}")
    private val horror = Cards.invent("Falls Emerging Horror", "{6}{G}", "Creature — Eldrazi Horror",
        "Emerge {5}{G} (You may cast this spell by sacrificing a creature and paying the emerge cost reduced by that creature's mana value.)", "6/6")
    private val growth = Cards.invent("Falls Harmonic Growth", "{2}{G}", "Sorcery",
        "Draw two cards.\nHarmonize {4}{G} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)")
    private val troll = Cards.invent("Falls Bridge Troll", "{4}{G}", "Creature — Troll",
        "Assist (Another player can pay up to {4} of this spell's cost.)", "4/4")
    private val signet = Cards.invent("Falls Signet", "{2}", "Artifact", "{1}, {T}: Add {W}{U}.")

    private fun Scenario.casts(player: ScenarioPlayer) =
        Engine.options(state, player.id).filterIsInstance<Input.Cast>().map { nameOf(state.objOrThrow(it.card)) }

    private fun Scenario.activates(player: ScenarioPlayer, source: String) =
        Engine.options(state, player.id).filterIsInstance<Input.Activate>().any { it.source == card(source) }

    /** The objects Alice is offered to answer the open target decision with, by name. */
    private fun Scenario.offeredTargets(): List<String> =
        Engine.options(state, alice.id).filterIsInstance<Input.Answer>().mapNotNull { a ->
            ((a.answer as? arcanum.engine.decision.Answer.Targets)?.chosen?.singleOrNull() as? arcanum.engine.state.TargetRef.Object)?.id?.let { nameOf(state.objOrThrow(it)) }
        }

    // CR 601.2f: the report's board: four lands, a tapped creature among others
    @Test
    fun aSpellCheaperOnATappedTargetIsListedWithTheManaOfTheCheaperCost() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp")
        alice.hand(banish)
        bob.battlefield(ox, tapped = true)
        bob.battlefield(bear)
        assertTrue(banish in casts(alice))
        perform(Input.Cast(alice.id, card(banish)))
        assertEquals(listOf(ox), offeredTargets(), "only the tapped creature leaves it payable")
    }

    // CR 601.2f: the reduction doesn't apply without a tapped creature: {4}{W} isn't paid with four lands
    @Test
    fun theSameSpellIsNotListedWithoutATappedCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp")
        alice.hand(banish)
        bob.battlefield(ox)
        bob.battlefield(bear)
        assertFalse(banish in casts(alice))
    }

    // CR 601.2f: the cheaper cost itself still has to be paid: {2}{W} with two lands isn't
    @Test
    fun theSameSpellIsNotListedWithoutTheManaOfTheCheaperCost() = scenario {
        alice.battlefield("Plains", "Swamp")
        alice.hand(banish)
        bob.battlefield(ox, tapped = true)
        bob.battlefield(bear)
        assertFalse(banish in casts(alice))
    }

    // CR 601.2f, 303.4a: an Aura's target through its enchant keyword
    @Test
    fun anAuraCheaperOnItsTargetIsListed() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(aura)
        bob.battlefield(flier)
        bob.battlefield(bear)
        assertTrue(aura in casts(alice))
    }

    // CR 107.3, 601.2f: X announced before the targets, the reduction after them
    @Test
    fun anXSpellCheaperOnItsTargetIsListed() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(burst)
        bob.battlefield(ox, tapped = true)
        bob.battlefield(bear)
        assertTrue(burst in casts(alice))
    }

    // CR 700.2, 601.2f: modes before the targets, the reduction after them
    @Test
    fun aModalSpellCheaperOnItsTargetIsListed() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(charm)
        bob.battlefield(ox, tapped = true)
        bob.battlefield(bear)
        assertTrue(charm in casts(alice))
    }

    // CR 601.2b, 601.2f: an optional cost asked before the targets
    @Test
    fun aKickerSpellCheaperOnItsTargetIsListed() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(kicked)
        bob.battlefield(ox, tapped = true)
        bob.battlefield(bear)
        assertTrue(kicked in casts(alice))
    }

    // CR 602.2b, 601.2f: an activated ability, its own reduction on its target
    @Test
    fun anAbilityCheaperOnItsTargetIsListed() = scenario {
        alice.battlefield("Plains", tapper)
        bob.battlefield(flier)
        bob.battlefield(bear)
        assertTrue(activates(alice, tapper))
    }

    // CR 702.6a, 602.2b: equip, less by what its target has
    @Test
    fun equipCheaperOnItsTargetIsListed() = scenario {
        alice.battlefield("Plains", blade)
        alice.battlefield(bear)
        alice.battlefield(ox, counters = listOf(arcanum.api.CounterKind.PowerToughness(1, 1) to 2))
        assertTrue(activates(alice, blade))
    }

    // CR 702.6a, 602.2b: another permanent's reduction for abilities that target it
    @Test
    fun equipMadeCheaperByItsTargetIsListed() = scenario {
        alice.battlefield("Plains", plainBlade)
        alice.battlefield(hero)
        alice.battlefield(bear)
        assertTrue(activates(alice, plainBlade))
    }

    // CR 702.119a, c: emerge, with more than one creature to sacrifice
    @Test
    fun emergeWithSeveralCreaturesIsListed() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", ox, bear)
        alice.hand(horror)
        assertTrue(horror in casts(alice))
    }

    // CR 702.180a: harmonize, with more than one creature to tap
    @Test
    fun harmonizeWithSeveralCreaturesIsListed() = scenario {
        alice.battlefield("Forest", "Forest", ox, bear)
        alice.graveyard(growth)
        assertTrue(growth in casts(alice))
    }

    // CR 702.132a: another player may pay the generic mana the caster can't
    @Test
    fun anAssistSpellIsListedWithTheOtherPlayersMana() = scenario {
        alice.battlefield("Forest")
        alice.hand(troll)
        bob.battlefield("Island", "Island", "Island", "Island")
        assertTrue(troll in casts(alice))
    }

    // CR 605.3a, 106.4 (castability §1): a mana ability that costs mana counts by what it nets (a Plains into a Signet: {W}{U}), and so
    // does its mana once in the pool (TBL-122)
    @Test
    fun aManaAbilityThatCostsManaCountsByWhatItNets() = scenario {
        alice.battlefield("Plains", signet)
        alice.hand(flier)
        assertTrue(flier in casts(alice), "{1}{W} with the Plains through the Signet")
        alice.activates(signet)
        assertTrue(flier in casts(alice), "{1}{W} with the Signet's {W}{U} in the pool")
    }
}
