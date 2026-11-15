package arcanum.engine.sets.trk

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.spm.loreChapter
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Star Trek chunk 09 in play: creatures entering tapped and attacking by a replacement, "Permanents can't phase in", a life gain trigger
 * triggering an additional time, a cost putting this card from the graveyard on the bottom of the library, casting this card from exile. */
class TrkChunk09ReplacementsAndConditionsScenariosTest {
    private val kiri = Cards.invent("Kiri Narro, Firebrand", "{1}{R}{W}", "Legendary Creature — Bajoran Soldier", """
        Trample
        If a creature you control would enter while you control an attacking creature, instead it enters tapped and attacking.
        Whenever another creature you control enters, it gets +1/+1 until end of turn.
    """.trimIndent(), "4/3")
    private val cub = Cards.invent("Grizzled Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val wolf = Cards.invent("Flashfang Wolf", "{1}{G}", "Creature — Wolf", "Flash", "2/2")
    private val muster = Cards.invent("Muster the Watch", "{W}", "Instant", "Create a 1/1 white Soldier creature token.")
    private val town = Cards.invent("The Town at the Rim of Always", "{3}{W}{B}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — All artifacts and creatures phase out. Create a 0/1 white Human creature token with "Permanents can't phase in." (Treat phased-out permanents and anything attached to them as though they don't exist.)
        II — You draw two cards and each player loses 2 life.
        III — Sacrifice a creature token. If you do, you gain 5 life.
    """.trimIndent())
    private val crane = Cards.invent("Dr. Bernice Crane", "{2}{W}", "Legendary Creature — Human Doctor", """
        If you gaining life causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.
        Whenever you gain life, put a +1/+1 counter on Dr. Crane.
    """.trimIndent(), "2/2")
    private val tonic = Cards.invent("Calming Tonic", "{W}", "Instant", "You gain 3 life.")
    private val danvers = Cards.invent("Danvers, Temporal Agent", "{2}{B/G}{B/G}", "Legendary Creature — Human Advisor", """
        When Danvers enters, return target creature card from your graveyard to your hand.
        {2}, Put this card from your graveyard on the bottom of your library: You gain 2 life.
    """.trimIndent(), "3/1")
    private val kharn = Cards.invent("Kharn, Vengeful Heart", "{2}{B}{B}", "Legendary Creature — Human Warrior", """
        When Kharn enters, you may sacrifice another creature or artifact. If you do, draw two cards.
        You may cast this card from exile.
    """.trimIndent(), "5/3")

    private fun attacksWhom(scenario: arcanum.engine.scenario.Scenario, name: String): TargetRef? = with(scenario) {
        state.combat?.attackers?.firstOrNull { it.id == card(name, zone = Zone.Battlefield) }?.target
    }

    // CR 614.1c, 508.4: with 3 players the creature's controller chooses which player it attacks; it isn't declared (no attack triggers)
    @Test
    fun kiriHasACreatureCastDuringCombatEnterTappedAndAttacking() = scenario(players = 3) {
        alice.battlefield(kiri, cub, "Forest", "Forest")
        alice.hand(wolf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cub, defender = "bob")
        alice.casts(wolf)
        allPass()
        val attacking = decision<Decision.Targets>()
        assertEquals(setOf(TargetRef.Player(bob.id), TargetRef.Player(carol.id)), attacking.options.toSet())
        answer(Answer.Targets(listOf(TargetRef.Player(carol.id))))
        resolveAll()
        assertEquals(TargetRef.Player(carol.id), attacksWhom(this, wolf))
        expect {
            wolf.tapped shouldBe true
            // its own "Whenever another creature you control enters" trigger saw it enter
            wolf.power shouldBe 3
        }
    }

    // CR 614.1c, 111.4, 508.4: a token created during combat enters tapped and attacking too
    @Test
    fun kiriHasATokenEnterTappedAndAttacking() = scenario {
        alice.battlefield(kiri, cub, "Plains")
        alice.hand(muster)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cub)
        alice.casts(muster)
        allPass()
        resolveAll()
        assertEquals(TargetRef.Player(bob.id), attacksWhom(this, "Soldier Token"))
        expect { "Soldier Token".tapped shouldBe true }
    }

    // CR 614.1c, 506.3: without an attacking creature (outside combat) the creature enters as usual
    @Test
    fun kiriWithoutAnAttackingCreatureChangesNothing() = scenario {
        alice.battlefield(kiri, "Forest", "Forest")
        alice.hand(wolf)
        alice.casts(wolf)
        allPass()
        resolveAll()
        assertTrue(state.combat?.attackers.orEmpty().isEmpty())
        expect { wolf.tapped shouldBe false }
    }

    // CR 702.26, 101.2: the phased-out permanents don't phase in during their controllers' untap steps while the token is there; once it is
    // gone they phase in as usual (CR 502.1)
    @Test
    fun theTownKeepsEverythingPhasedOutWhileTheTokenRemains() = scenario {
        alice.battlefield(cub)
        bob.battlefield(wolf)
        loreChapter(town, 1, "Island", "Island")
        resolveAll()
        assertTrue(state.obj(card(cub))!!.phasedOut)
        assertTrue(state.obj(card(wolf))!!.phasedOut)
        expect { alice.battlefield has "Human Token" }
        advanceTo(TurnStep.Upkeep, turn = 4)
        assertTrue(state.obj(card(wolf))!!.phasedOut)
        move("Human Token", Zone.Graveyard)
        advanceTo(TurnStep.Upkeep, turn = 5)
        assertTrue(!state.obj(card(cub))!!.phasedOut)
        assertTrue(state.obj(card(wolf))!!.phasedOut) // Bob's untap step comes on his turn
    }

    // CR 603.2d: one life gain, Dr. Crane's own trigger twice
    @Test
    fun craneTriggersTwiceOnALifeGain() = scenario {
        alice.battlefield(crane, "Plains")
        alice.hand(tonic)
        alice.casts(tonic)
        allPass()
        resolveAll()
        expect {
            alice.life shouldBe 23
            crane.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
        }
    }

    // CR 113.6m, 401.1: the cost works from the graveyard and puts the card on the bottom of the library
    @Test
    fun danversGoesFromTheGraveyardToTheBottomOfTheLibrary() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(danvers)
        alice.library("Island")
        alice.activates(danvers)
        allPass()
        expect {
            alice.life shouldBe 22
            alice.graveyard.size shouldBe 0
        }
        assertEquals(card(danvers, zone = Zone.Library), state.player(alice.id).library.last())
    }

    // CR 113.6f, 601.2a: cast from exile by its own permission
    @Test
    fun kharnIsCastFromExile() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.exile(kharn)
        alice.casts(kharn)
        allPass()
        resolveAll()
        expect { alice.battlefield has kharn }
    }
}
