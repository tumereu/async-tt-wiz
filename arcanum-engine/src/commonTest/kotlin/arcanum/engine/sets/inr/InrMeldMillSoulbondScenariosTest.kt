package arcanum.engine.sets.inr

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.Engine
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** INR chunk 06: meld by an end-step trigger and by an activated ability, a sacrifice trigger that returns the creature transformed at the next
 * end step, a mill that transforms the creature, and a soulbond-granted flicker. Invented names carry the oracle text of the real cards. */
class InrMeldMillSoulbondScenariosTest {
    private val gilda = Cards.inventFaces("Meld:Brunhild, the Dimming Light",
        face("Gilda, the Shattered Edge", "{2}{W}{W}", "Legendary Creature — Angel Horror", """
            Flying, first strike, lifelink
            At the beginning of your end step, if you both own and control Gilda and a creature named Brunhild, the Dimming Light, exile them, then meld them into Briseis, Voice of Dread.
        """.trimIndent(), "4/3", null, null),
        face("Briseis, Voice of Dread", null, "Legendary Creature — Eldrazi Angel", """
            Flying, first strike, vigilance, lifelink
            Your opponents can't cast spells with mana value 3 or less.
        """.trimIndent(), "9/10", null, "W"))
    private val brunhild = Cards.inventFaces("Meld:Gilda, the Shattered Edge",
        face("Brunhild, the Dimming Light", "{3}{W}", "Legendary Creature — Human Cleric", "Flash\n(Melds with Gilda, the Shattered Edge.)", "4/4", null, null),
        face("Briseis, Voice of Dread", null, "Legendary Creature — Eldrazi Angel", """
            Flying, first strike, vigilance, lifelink
            Your opponents can't cast spells with mana value 3 or less.
        """.trimIndent(), "9/10", null, "W"))
    private val ramparts = Cards.inventFaces("Meld:Harrow Garrison",
        face("Harrow Ramparts", null, "Land", """
            {T}: Add {C}.
            {3}{R}{R}, {T}: If you both own and control this land and a creature named Harrow Garrison, exile them, then meld them into Harrow, the Squirming Hamlet.
        """.trimIndent(), null, null, null),
        face("Harrow, the Squirming Hamlet", null, "Legendary Creature — Eldrazi Ooze", """
            Trample, haste
            Whenever Harrow attacks, create two 3/2 colorless Eldrazi Horror creature tokens that are tapped and attacking.
        """.trimIndent(), "7/4", null, "R"))
    private val garrison = Cards.inventFaces("Meld:Harrow Ramparts",
        face("Harrow Garrison", "{2}{R}", "Creature — Human Soldier", "Haste\n(Melds with Harrow Ramparts.)", "3/3", null, null),
        face("Harrow, the Squirming Hamlet", null, "Legendary Creature — Eldrazi Ooze", """
            Trample, haste
            Whenever Harrow attacks, create two 3/2 colorless Eldrazi Horror creature tokens that are tapped and attacking.
        """.trimIndent(), "7/4", null, "R"))
    private val seizure = Cards.invent("Fleeting Seizure", "{1}{R}", "Instant", "Gain control of target permanent until end of turn.")
    private val cheapTrick = Cards.invent("Cheap Trick", "{1}", "Instant", "You gain 1 life.")
    private val dearTrick = Cards.invent("Dear Trick", "{3}", "Instant", "You gain 1 life.")
    private val bear = Cards.invent("Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val roe = Cards.inventFaces("Transform",
        face("Biolume Roe", "{2}{U}", "Creature — Serpent Egg", """
            Defender
            When you sacrifice this creature, return it to the battlefield transformed under its owner's control at the beginning of the next end step.
        """.trimIndent(), "0/4", null, null),
        face("Biolume Eel", null, "Creature — Serpent", "Sacrifice two Islands: This creature can't be blocked this turn.", "4/4", null, "U"))
    private val altar = Cards.invent("Altar of Offering", null, "Artifact", "Sacrifice a creature: You gain 1 life.")
    private val scholar = Cards.inventFaces("Transform",
        face("Aberrant Scholar", "{3}{U}", "Creature — Human Insect", """
            Flying
            At the beginning of your upkeep, mill a card. If an instant or sorcery card was milled this way, transform this creature.
        """.trimIndent(), "3/2", null, null),
        face("Perfected Shape", null, "Creature — Insect Horror", "Flying", "5/4", null, "U"))
    private val navigator = Cards.invent("Deadeye Pilot", "{4}{U}{U}", "Creature — Spirit", """
        Soulbond (You may pair this creature with another unpaired creature when either enters. They remain paired for as long as you control both of them.)
        As long as Deadeye Pilot is paired with another creature, each of those creatures has "{1}{U}: Exile this creature, then return it to the battlefield under your control."
    """.trimIndent(), "5/5")

    private fun Scenario.face(name: String): Int? = state.obj(card(name))?.face
    private fun Scenario.libraries() { players.forEach { it.library("Island", "Island", "Island", "Island") } }
    private fun Scenario.canCast(player: arcanum.engine.scenario.ScenarioPlayer) = Engine.options(state, player.id).any { it is Input.Cast }

    // CR 701.42a, 712.4a: at the beginning of your end step, no payment; CR 101.2 (the melded card's restriction applies to every opponent)
    @Test
    fun gildaMeldsAtTheEndStepAndOpponentsCantCastCheapSpells() = scenario(players = 3) {
        libraries()
        alice.battlefield(gilda, brunhild)
        bob.battlefield("Plains"); bob.hand(cheapTrick)
        players[2].battlefield("Plains"); players[2].hand(cheapTrick)
        turn(step = TurnStep.PrecombatMain)
        alice.passes()
        assertTrue(canCast(bob), "before the meld Bob may cast a mana value 1 instant")
        bob.passes()
        assertTrue(canCast(players[2]), "and so may Carol")
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield.size shouldBe 1
            card(gilda).view.characteristics?.name shouldBe "Briseis, Voice of Dread"
            gilda.power shouldBe 9
        }
        alice.passes()
        assertEquals(false, canCast(bob))
        bob.passes()
        assertEquals(false, canCast(players[2]))
    }

    // CR 701.42a: "If you both own and control" is checked as the ability resolves; with the partner under another player's control nothing happens
    @Test
    fun harrowRampartsMeldsOnlyWithYourOwnGarrison() = scenario {
        libraries()
        alice.battlefield(ramparts, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.battlefield(garrison)
        turn(step = TurnStep.PrecombatMain)
        alice.activates(ramparts, ability = 1)
        resolveAll()
        expect {
            card(ramparts).view.characteristics?.name shouldBe "Harrow Ramparts"
            bob.battlefield has garrison
        }
    }

    // CR 603.4, 701.42a: "you both own and control Gilda" is checked again as the trigger resolves; Bob took Gilda with the trigger on the Stack,
    // so nothing is exiled or melded
    @Test
    fun gildaStolenWithTheTriggerOnTheStackDoesNotMeld() = scenario {
        libraries()
        alice.battlefield(gilda, brunhild)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(seizure)
        advanceTo(TurnStep.End)
        alice.passes()
        bob.casts(seizure, target = gilda)
        resolveAll()
        expect {
            card(gilda).view.characteristics?.name shouldBe "Gilda, the Shattered Edge"
            gilda.controller shouldBe bob.id
            alice.battlefield has brunhild
        }
    }

    // CR 701.42a: the activated ability checks "you both own and control this land" as it resolves; Bob took the land in response
    @Test
    fun harrowRampartsStolenInResponseDoesNotMeld() = scenario {
        libraries()
        alice.battlefield(ramparts, garrison, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.battlefield("Mountain", "Mountain")
        bob.hand(seizure)
        turn(step = TurnStep.PrecombatMain)
        alice.activates(ramparts, ability = 1)
        alice.passes()
        bob.casts(seizure, target = ramparts)
        resolveAll()
        expect {
            card(ramparts).view.characteristics?.name shouldBe "Harrow Ramparts"
            ramparts.controller shouldBe bob.id
            alice.battlefield has garrison
        }
    }

    @Test
    fun harrowRampartsMeldsWithGarrison() = scenario {
        libraries()
        alice.battlefield(ramparts, garrison, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        turn(step = TurnStep.PrecombatMain)
        alice.activates(ramparts, ability = 1)
        resolveAll()
        expect {
            card(ramparts).view.characteristics?.name shouldBe "Harrow, the Squirming Hamlet"
            alice.battlefield.size shouldBe 6
            ramparts.power shouldBe 7
        }
    }

    // CR 603.3 (the sacrifice trigger), 603.7 (delayed trigger at the next end step), 400.7 (a new object), 701.27a (it returns transformed)
    @Test
    fun sacrificedEggReturnsTransformedAtTheNextEndStep() = scenario {
        libraries()
        alice.battlefield(roe, altar)
        turn(step = TurnStep.PrecombatMain)
        alice.activates(altar, ability = 0)
        resolveAll()
        expect { alice.graveyard has roe }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield has roe
            card(roe).view.characteristics?.name shouldBe "Biolume Eel"
            roe.power shouldBe 4
        }
    }

    // CR 701.17a, 701.27a: an instant milled transforms the creature; a creature milled does not
    @Test
    fun milledInstantTransformsTheScholar() = scenario {
        alice.battlefield(scholar)
        alice.library(cheapTrick, bear, bear)
        bob.library(bear, bear, bear)
        turn(step = TurnStep.PrecombatMain)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect { alice.graveyard has cheapTrick }
        assertEquals(1, face("Aberrant Scholar"))
    }

    @Test
    fun milledCreatureLeavesTheScholarAlone() = scenario {
        alice.battlefield(scholar)
        alice.library(bear, cheapTrick, bear)
        bob.library(bear, bear, bear)
        turn(step = TurnStep.PrecombatMain)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect { alice.graveyard has bear }
        assertEquals(0, face("Aberrant Scholar"))
    }

    // CR 702.95b, 400.7: the paired creature has the granted ability; flickering it returns a new object that is no longer paired
    @Test
    fun pairedCreatureCanFlickerItself() = scenario {
        libraries()
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", bear)
        alice.hand(navigator)
        alice.casts(navigator)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        assertTrue(arcanum.engine.soulbond.Soulbond.partner(state, card(bear)) != null, "paired")
        alice.activates(bear, ability = 0)
        resolveAll()
        expect { alice.battlefield has bear }
        assertEquals(null, arcanum.engine.soulbond.Soulbond.partner(state, card(bear)))
    }
}
