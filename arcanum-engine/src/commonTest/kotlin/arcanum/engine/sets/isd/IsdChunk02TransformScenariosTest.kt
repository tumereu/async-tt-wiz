package arcanum.engine.sets.isd

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** ISD chunk 02: transforming double-faced cards the engine hadn't played: a loot that transforms by what was discarded, a counter
 * threshold that clears its counters, "Transform all Humans" with a prevention effect, an activation limited by a count, a lord on the
 * back face, a payment and a dying creature (CR 701.27, 712). Invented names carry the oracle text of the real cards. */
class IsdChunk02TransformScenariosTest {
    private val scribe = Cards.inventFaces("Transform",
        face("Mooncrest Scribe", "{2}{U}", "Creature — Human Advisor",
            "{T}: Draw a card, then discard a card. If a creature card is discarded this way, untap this creature, then transform it.", "0/1", null, null),
        face("Mooncrest Brute", null, "Creature — Human Mutant",
            "At the beginning of your end step, if this creature didn't attack this turn, tap this creature, then transform it.", "5/1", null, "U"))
    private val vessel = Cards.inventFaces("Transform",
        face("Dusk Vessel", "{1}{U}", "Creature — Lizard Egg", """
            Defender
            {1}{U}: Put a hatchling counter on this creature. Then if there are five or more hatchling counters on it, remove all of them and transform it.
        """.trimIndent(), "0/3", null, null),
        face("Dusk Horror", null, "Creature — Lizard Horror", "Trample", "13/13", null, "U"))
    private val villager = Cards.inventFaces("Transform",
        face("Mooncalled Villager", "{1}{G}", "Creature — Human Werewolf", "", "2/2", null, null),
        face("Mooncalled Beast", null, "Creature — Werewolf", "", "4/4", null, "G"))
    private val haze = Cards.invent("Moonmist Haze", "{1}{G}", "Instant",
        "Transform all Humans. Prevent all combat damage that would be dealt this turn by creatures other than Werewolves and Wolves. (Only double-faced cards can be transformed.)")
    private val matriarch = Cards.inventFaces("Transform",
        face("Dire Matron", "{2}{B}{B}", "Creature — Vampire", """
            Flying
            {T}: Create a 2/2 black Vampire creature token with flying.
            {B}: Transform this creature. Activate only if you control five or more Vampires.
        """.trimIndent(), "3/3", null, null),
        face("Dire Sovereign", null, "Creature — Vampire", """
            Flying
            Other Vampire creatures you control get +2/+2.
            {T}: Create a 2/2 black Vampire creature token with flying.
        """.trimIndent(), "5/5", null, "B"))
    private val flitter = Cards.inventFaces("Transform",
        face("Gloam Flitter", "{2}{B}", "Creature — Bat", """
            Flying
            At the beginning of your upkeep, you may pay {2}{B}{B}. If you do, transform this creature.
        """.trimIndent(), "2/2", null, null),
        face("Gloam Fiend", null, "Creature — Vampire",
            "At the beginning of your upkeep, you may pay {2}{B}{B}. If you do, transform this creature.", "5/5", null, "B"))
    private val sentry = Cards.inventFaces("Transform",
        face("Door Sentry", "{3}{W}", "Creature — Human Soldier", """
            Vigilance
            Whenever another creature you control dies, you may transform this creature.
        """.trimIndent(), "2/2", null, null),
        face("Door Veteran", null, "Creature — Human Soldier", "Trample", "5/4", null, "W"))
    private val leech = Cards.invent("Dunmoor Leech", "{B}", "Creature — Vampire", "", "1/1")
    private val bear = Cards.invent("Dunmoor Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val human = Cards.invent("Dunmoor Peasant", "{1}{W}", "Creature — Human Peasant", "", "2/2")
    private val idea = Cards.invent("Dunmoor Notion", "{U}", "Instant", "Draw a card.")
    private val zap = Cards.invent("Dunmoor Zap", "{R}", "Instant", "Dunmoor Zap deals 5 damage to any target.")

    private fun Scenario.face(name: String): Int? = state.obj(card(name))?.face

    // CR 701.9a, 701.27a: a creature card discarded to the loot untaps it and transforms it
    @Test
    fun lootingACreatureCardUntapsAndTransforms() = scenario {
        alice.battlefield(scribe)
        alice.library(bear)
        bob.library(bear)
        alice.activates(scribe)
        resolveAll()
        assertEquals(1, face(scribe))
        expect { scribe.tapped shouldBe false }
    }

    /** A noncreature card discarded leaves it tapped and unchanged. */
    // CR 701.9a
    @Test
    fun lootingANoncreatureCardChangesNothing() = scenario {
        alice.battlefield(scribe)
        alice.library(idea)
        bob.library(bear)
        alice.activates(scribe)
        resolveAll()
        assertEquals(0, face(scribe))
        expect { scribe.tapped shouldBe true }
    }

    // CR 603.4: the end-step trigger needs "didn't attack this turn" both when it triggers and on resolution; it taps the creature
    @Test
    fun theBruteTransformsBackAtTheEndStepWhenItDidntAttack() = scenario {
        alice.battlefield(scribe)
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.activates(scribe)
        resolveAll()
        assertEquals(1, face(scribe))
        advanceTo(TurnStep.End)
        resolveAll()
        assertEquals(0, face(scribe))
        expect { scribe.tapped shouldBe true }
    }

    /** Having attacked this turn, it stays as it is. */
    // CR 603.4
    @Test
    fun theBruteStaysWhenItAttacked() = scenario {
        alice.battlefield(scribe)
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.activates(scribe)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(scribe)
        advanceTo(TurnStep.End)
        resolveAll()
        assertEquals(1, face(scribe))
    }

    // CR 122.1, 608.2c: four counters change nothing; the fifth removes them all and transforms
    @Test
    fun theFifthHatchlingCounterTransformsAndClearsThem() = scenario {
        alice.battlefield(vessel, "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.library(bear)
        bob.library(bear)
        repeat(4) {
            alice.activates(vessel)
            resolveAll()
        }
        assertEquals(0, face(vessel))
        expect { vessel.counters(CounterKind.Named("hatchling")) shouldBe 4 }
        alice.activates(vessel)
        resolveAll()
        assertEquals(1, face(vessel))
        expect {
            vessel.counters(CounterKind.Named("hatchling")) shouldBe 0
            vessel.power shouldBe 13
        }
    }

    // CR 701.27c: only double-faced permanents transform; CR 615.1: the prevention covers creatures other than Werewolves and Wolves
    @Test
    fun transformAllHumansOnlyTransformsDoubleFacedHumansAndPreventsTheRestsDamage() = scenario {
        alice.battlefield(villager, human, "Forest", "Forest")
        alice.hand(haze)
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts(haze)
        resolveAll()
        assertEquals(1, face(villager))
        assertEquals(0, face(human))
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(villager, human)
        advanceTo(TurnStep.EndOfCombat, turn = 3)
        expect { bob.life shouldBe 16 }
    }

    // CR 602.5b: with only four Vampires the transform ability can't be activated
    @Test
    fun theMatronCantTransformWithFourVampires() = scenario {
        alice.battlefield(matriarch, leech, leech, leech, "Swamp")
        alice.library(bear)
        bob.library(bear)
        assertFailsWith<IllegalArgumentException> { alice.activates(matriarch, ability = 1) }
    }

    /** With five it can, and the back face pumps the other Vampires. */
    // CR 602.5b, 613.4c
    @Test
    fun theMatronTransformsWithFiveVampiresAndThenPumpsTheOthers() = scenario {
        alice.battlefield(matriarch, leech, leech, leech, leech, "Swamp")
        alice.library(bear)
        bob.library(bear)
        alice.activates(matriarch, ability = 1)
        resolveAll()
        assertEquals(1, face(matriarch))
        expect {
            matriarch.power shouldBe 5
            leech.power shouldBe 3
        }
    }

    // CR 603.4, 118.12: pay at resolution; "if you do" holds only when the payment was made
    @Test
    fun theFlitterTransformsOnlyWhenYouPay() = scenario {
        alice.battlefield(flitter, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.library(bear, bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        assertEquals(0, face(flitter))
        advanceTo(TurnStep.Upkeep, turn = 5)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        alice.answers(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        assertEquals(1, face(flitter))
    }

    // CR 700.4, 603.10a: another creature of yours dying lets you transform it; the "may" can be taken
    @Test
    fun theSentryTransformsWhenAnotherCreatureOfYoursDies() = scenario(players = 3) {
        alice.battlefield(sentry, leech, "Mountain")
        alice.hand(zap)
        alice.library(bear)
        bob.library(bear)
        carol.library(bear)
        alice.casts(zap, target = leech)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        assertEquals(1, face(sentry))
    }

    /** A creature of an opponent's dying doesn't trigger it. */
    // CR 603.2: the trigger is for creatures you control
    @Test
    fun theSentryIgnoresAnOpponentsCreatureDying() = scenario(players = 3) {
        alice.battlefield(sentry, "Mountain")
        bob.battlefield(leech)
        alice.hand(zap)
        alice.library(bear)
        bob.library(bear)
        carol.library(bear)
        alice.casts(zap, target = leech)
        resolveAll()
        assertEquals(0, face(sentry))
    }
}
