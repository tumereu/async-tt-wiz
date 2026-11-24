package arcanum.engine.sets.vow

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.tdm.alternativeOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** VOW chunk 04: cleave (CR 702.148): what the engine hadn't played: a target chosen by whether the cleave cost was paid, an X the text
 * defines unless cleaved, a maximum hand size reduced for the rest of the game, and damage that can't be prevented during an extra turn. */
class VowChunk04CleaveScenariosTest {
    private val reminder = "(You may cast this spell for its cleave cost. If you do, remove the words in square brackets.)"
    private val reprisal = Cards.invent("Lantern Reprisal", "{1}{W}", "Instant", """
        Cleave {5}{W} $reminder
        Destroy target [attacking] creature.
    """.trimIndent())
    private val burst = Cards.invent("Lamplight Burst", "{1}{W}", "Instant", """
        Cleave {X}{R}{W} $reminder
        Lamplight Burst deals X damage to target creature or planeswalker and you gain X life. [X is the number of creatures you control.]
    """.trimIndent())
    private val notion = Cards.invent("Sudden Notion", "{2}{U}", "Sorcery", """
        Cleave {3}{U}{U} $reminder
        Draw three cards. [Your maximum hand size is reduced by three for the rest of the game.]
    """.trimIndent())
    private val flask = Cards.invent("Gambler's Last Flask", "{1}{R}{R}", "Sorcery", """
        Cleave {4}{U}{U}{R} $reminder
        Take an extra turn after this one. During that turn, damage can't be prevented. [At the beginning of that turn's end step, you lose the game.]
        Exile Gambler's Last Flask.
    """.trimIndent())
    private val rinse = Cards.invent("Rinse Aside", "{U}", "Instant", """
        Cleave {1}{U}{U} $reminder
        Counter target spell [that wasn't cast from its owner's hand].
    """.trimIndent())
    private val shielded = Cards.invent("Shielded Ox", "{2}{W}", "Creature — Ox", "Prevent all damage that would be dealt to this creature.", "1/1")
    private val jab = Cards.invent("Lamplit Jab", "{R}", "Instant", "Lamplit Jab deals 2 damage to target creature.")
    private val cinderJab = Cards.invent("Lamplit Flick", "{R}", "Instant", "Lamplit Flick deals 2 damage to target creature.")
    private val bear = Cards.invent("Lamplit Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val cub = Cards.invent("Lamplit Cub", "{G}", "Creature — Bear", "", "1/1")
    private val brute = Cards.invent("Lamplit Brute", "{2}{R}", "Creature — Ogre", "", "3/3")

    private fun Scenario.cleaving(name: String) = alternativeOf(name, Keyword.Cleave.Kind)
    private fun Scenario.pay(player: arcanum.engine.scenario.ScenarioPlayer) =
        player.answers(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))

    // CR 702.148a, 601.2c: not cleaved, only an attacking creature can be the target
    @Test
    fun reprisalTargetsOnlyAnAttackerUnlessCleaved() = scenario {
        alice.battlefield(bear, cub)
        bob.battlefield("Plains", "Plains")
        bob.hand(reprisal)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        alice.passes()
        bob.casts(reprisal)
        assertEquals(listOf(target(bear)), decision<Decision.Targets>().options)
        bob.answers(Answer.Targets(listOf(target(bear))))
        pay(bob)
        resolveAll()
        expect { alice.graveyard has bear }
    }

    // CR 702.148a, 601.2c, 608.2b: cleaved, any creature; the target stays legal as it resolves
    @Test
    fun cleavedReprisalDestroysACreatureThatIsntAttacking() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(reprisal)
        bob.battlefield(bear)
        alice.casts(reprisal, way = cleaving(reprisal), target = bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    // CR 702.148a, 107.3c: not cleaved, X is the number of creatures you control (three)
    @Test
    fun burstCountsYourCreaturesUnlessCleaved() = scenario {
        alice.battlefield("Plains", "Plains", bear, cub, "Lamplit Bear")
        alice.hand(burst)
        bob.battlefield(brute)
        alice.casts(burst, target = brute)
        resolveAll()
        expect {
            bob.graveyard has brute
            alice.life shouldBe 23
        }
    }

    // CR 702.148a, 107.3: cleaved, X is announced for the cleave cost {X}{R}{W}
    @Test
    fun cleavedBurstUsesTheAnnouncedX() = scenario {
        alice.battlefield("Plains", "Mountain", "Plains", bear, cub, "Lamplit Bear")
        alice.hand(burst)
        bob.battlefield(brute)
        alice.casts(burst, way = cleaving(burst), target = brute, x = 1)
        resolveAll()
        expect {
            bob.battlefield has brute
            card(brute).view.obj!!.damage shouldBe 1
            alice.life shouldBe 21
        }
    }

    // CR 402.2, 514.1: not cleaved, the maximum hand size is four from then on; seven cards in hand become four at cleanup
    @Test
    fun notionReducesTheMaximumHandSize() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(notion, "Forest", "Forest", "Forest", "Forest")
        alice.library("Swamp", "Swamp", "Swamp", "Swamp")
        bob.library("Swamp", "Swamp")
        alice.casts(notion)
        resolveAll()
        expect { alice.hand.size shouldBe 7 }
        advanceTo(TurnStep.Cleanup)
        alice.chooses("Forest", "Forest", "Forest")
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            alice.hand.size shouldBe 4
            alice.graveyard.size shouldBe 4
        }
    }

    // CR 702.148a, 402.2: cleaved, the hand size stays seven
    @Test
    fun cleavedNotionKeepsTheHandSize() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(notion, "Forest", "Forest", "Forest", "Forest")
        alice.library("Swamp", "Swamp", "Swamp", "Swamp")
        bob.library("Swamp", "Swamp")
        alice.casts(notion, way = cleaving(notion))
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { alice.hand.size shouldBe 7 }
    }

    // CR 500.7, 615.12, 611.2a: damage is still prevented in the turn the flask resolves, not during the extra turn; not cleaved, the
    // player loses at that turn's end step (CR 603.7, 104.3a)
    @Test
    fun flaskMakesDamageUnpreventableOnlyDuringTheExtraTurn() = scenario {
        alice.library("Island", "Island", "Island")
        bob.library("Island", "Island", "Island")
        alice.hand(flask, jab, cinderJab)
        bob.battlefield(shielded)
        alice.castsWithoutCosts(flask)
        resolveAll()
        alice.castsWithoutCosts(jab, target = shielded)
        resolveAll()
        expect { bob.battlefield has shielded }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        assertEquals(alice.id, state.turn.active)
        alice.castsWithoutCosts(cinderJab, target = shielded)
        resolveAll()
        expect { bob.graveyard has shielded }
        advanceTo(TurnStep.End, turn = 2)
        resolveAll()
        assertEquals(true, state.player(alice.id).lost)
    }

    // CR 702.148a, 500.7: cleaved, no loss; with three players the effect ends with the extra turn, and damage in the next player's turn is
    // prevented again
    @Test
    fun cleavedFlaskDoesntLoseAndTheEffectEndsWithTheTurn() = scenario(players = 3) {
        alice.library("Island", "Island", "Island")
        bob.library("Island", "Island", "Island")
        carol.library("Island", "Island", "Island")
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Mountain")
        alice.hand(flask)
        bob.hand(jab)
        carol.battlefield(shielded)
        alice.casts(flask, way = cleaving(flask))
        resolveAll()
        advanceTo(TurnStep.End, turn = 2)
        resolveAll()
        assertEquals(alice.id, state.turn.active)
        assertEquals(false, state.player(alice.id).lost)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        assertEquals(bob.id, state.turn.active)
        bob.castsWithoutCosts(jab, target = shielded)
        resolveAll()
        expect { carol.battlefield has shielded }
    }

    // CR 702.148a, 601.2a: not cleaved, a spell cast from the hand isn't a legal target; cleaved, it is
    @Test
    fun rinseCountersASpellFromTheHandOnlyWhenCleaved() = scenario {
        alice.battlefield(bear)
        bob.battlefield("Island", "Island", "Island")
        bob.hand(rinse)
        alice.hand(jab)
        alice.castsWithoutCosts(jab, target = bear)
        alice.passes()
        val ways = Engine.options(state, bob.id).filterIsInstance<Input.Cast>().filter { it.card == card(rinse) }.map { it.way }
        assertEquals(listOf(cleaving(rinse)), ways)
        bob.casts(rinse, way = cleaving(rinse), target = jab)
        resolveAll()
        expect {
            alice.graveyard has jab
            alice.battlefield has bear
        }
    }
}
