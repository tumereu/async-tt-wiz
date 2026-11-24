package arcanum.engine.sets.vow

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.fin.backFaceUp
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** VOW chunk 02: Daybound cards whose abilities need the engine: an attack trigger with three optional targets, and "can't block" for
 * the creature a damage ability hit. */
class VowChunk02DayboundScenariosTest {
    private val arsonist = Cards.inventFaces("Transform",
        face("Embershower Arsonist", "{3}{R}{R}", "Creature — Human Werewolf", """
            Menace, haste
            Whenever this creature attacks, it deals 1 damage to each of up to one target creature, up to one target player, and/or up to one target planeswalker.
            Daybound (If a player casts no spells during their own turn, it becomes night next turn.)
        """.trimIndent(), "4/4", null, null),
        face("Embershower Anarchist", null, "Creature — Werewolf", """
            Menace, haste
            Whenever this creature attacks, it deals 2 damage to each of up to one target creature, up to one target player, and/or up to one target planeswalker.
            Nightbound (If a player casts at least two spells during their own turn, it becomes day next turn.)
        """.trimIndent(), "5/5", null, "R"))
    private val marksman = Cards.inventFaces("Transform",
        face("Bolt Lookout", "{2}{R}{R}", "Creature — Human Soldier Werewolf", """
            {2}{R}, {T}: This creature deals 1 damage to any target.
            Daybound (If a player casts no spells during their own turn, it becomes night next turn.)
        """.trimIndent(), "4/3", null, null),
        face("Bolt Marksman", null, "Creature — Werewolf", """
            {2}{R}: This creature deals 1 damage to any target. A creature dealt damage this way can't block this turn.
            Nightbound (If a player casts at least two spells during their own turn, it becomes day next turn.)
        """.trimIndent(), "5/5", null, "R"))
    private val pup = Cards.invent("Vowfield Pup", "{G}", "Creature — Dog", "", "1/1")
    private val ox = Cards.invent("Vowfield Ox", "{3}{G}", "Creature — Ox", "", "2/4")
    private val wall = Cards.invent("Vowfield Wall", "{1}{G}", "Creature — Wall", "", "1/4")
    private val scout = Cards.invent("Vowfield Scout", "{1}{G}", "Creature — Elf", "", "2/2")

    /** No planeswalker is in play, so that slot has no legal target and isn't asked (CR 115.1); if it is asked, none is chosen. */
    private fun arcanum.engine.scenario.Scenario.noPlaneswalkerToChoose() {
        if (pending is Decision.Targets) answer(Answer.Targets(emptyList()))
    }

    // CR 115.1, 120.2: each "up to one" slot is chosen on its own; the creature and the player are both dealt damage, no planeswalker chosen
    @Test
    fun attackDamagesTheChosenCreatureAndPlayer() = scenario {
        alice.battlefield(arsonist)
        bob.battlefield(pup, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(arsonist)
        decision<Decision.Targets>()
        alice.answers(Answer.Targets(listOf(target(pup))))
        decision<Decision.Targets>()
        alice.answers(Answer.Targets(listOf(target("bob"))))
        noPlaneswalkerToChoose()
        resolveAll()
        expect {
            pup.zone shouldBe Zone.Graveyard
            ox.zone shouldBe Zone.Battlefield
            bob.life shouldBe 19
        }
    }

    // CR 115.1: choosing no target at all is allowed for "up to one": the attack trigger deals no damage
    @Test
    fun attackWithNoTargetsDealsNothing() = scenario {
        alice.battlefield(arsonist)
        bob.battlefield(pup)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(arsonist)
        repeat(2) {
            decision<Decision.Targets>()
            alice.answers(Answer.Targets(emptyList()))
        }
        noPlaneswalkerToChoose()
        resolveAll()
        expect {
            pup.zone shouldBe Zone.Battlefield
            bob.life shouldBe 20
        }
    }

    // CR 702.145: the nightbound face deals 2; a 3-player game picks its player among the opponents
    @Test
    fun nightFaceDealsTwoToAnOpponentInAThreePlayerGame() = scenario(players = 3) {
        alice.battlefield(arsonist)
        backFaceUp(arsonist)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(arsonist)
        decision<Decision.Targets>()
        alice.answers(Answer.Targets(emptyList()))
        decision<Decision.Targets>()
        alice.answers(Answer.Targets(listOf(target("carol"))))
        noPlaneswalkerToChoose()
        resolveAll()
        expect {
            carol.life shouldBe 18
            bob.life shouldBe 20
        }
    }

    // CR 509.1b: only the creature this ability dealt damage to can't block; the others still can
    @Test
    fun creatureDealtDamageByTheNightFaceCantBlock() = scenario {
        alice.battlefield(marksman, scout, "Mountain", "Mountain", "Mountain")
        backFaceUp(marksman)
        bob.battlefield(wall, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(scout)
        alice.activates(marksman, target = wall)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(wall to scout) }
        bob.blocks(ox to scout)
        expect { ox.zone shouldBe Zone.Battlefield }
    }
}
