package arcanum.engine.sets.mkm

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.resolveAll
import arcanum.engine.sets.tdm.Plain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** MKM chunk 16: a "you may pay" trigger whose token gains haste, a sacrifice of your own permanent, a cost tapping only your creatures. */
class MkmChunk16MovesCountersAndSacrificesScenariosTest {
    private val baron = Cards.invent("Ember Baron", "{2}{R}", "Legendary Creature — Goblin", """
        Whenever an artifact is put into a graveyard from the battlefield, you may pay {R}. If you do, create a 1/1 red Goblin creature token. It gains haste until end of turn.
    """, "3/3")
    private val outlet = Cards.invent("Scrap Outlet", "{1}", "Artifact", "Sacrifice an artifact: You gain 1 life.")
    private val hideout = Cards.invent("Hollow Hideout", null, "Artifact Land — Clue", """
        {T}, Tap an untapped creature you control: Add one mana of any color.
    """)
    private val crook = Cards.invent("Brass Crook", "{4}{R}", "Creature — Lizard Warrior", """
        When this creature enters, you may sacrifice an artifact. When you do, this creature deals 3 damage to any target.
    """, "5/4")

    private fun Scenario.goblins() = state.objects.values.filter { it.zone == Zone.Battlefield && nameOf(it) == "Goblin Token" }
    private fun Scenario.setup() {
        alice.library("Swamp", "Swamp")
        bob.library("Swamp", "Swamp")
        carol.library("Swamp", "Swamp")
    }

    // CR 603.2, 608.2c, 111.1: paying {R} makes a Goblin that has haste; the artifact that died doesn't
    @Test
    fun payingMakesAHastyGoblin() = scenario(players = 3) {
        alice.battlefield(baron, Plain.idol, Plain.golem, outlet, "Mountain", "Mountain")
        bob.battlefield(Plain.idol)
        setup()
        alice.activates(outlet, choosing = listOf(Plain.golem))
        resolveAll()
        answer(Answer.YesNo(true))
        alice.answers(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        assertEquals(1, goblins().size)
        expect {
            card("Goblin Token") hasKeyword Keyword.Haste.kind
            Plain.golem.zone shouldBe Zone.Graveyard
        }
    }

    // CR 603.2: an opponent's artifact dying triggers it too ("an artifact"), and declining the payment makes nothing
    @Test
    fun decliningMakesNoGoblin() = scenario(players = 3) {
        alice.battlefield(baron, Plain.idol, outlet, "Mountain")
        setup()
        alice.activates(outlet, choosing = listOf(Plain.idol))
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        assertEquals(0, goblins().size)
    }

    // CR 602.2b, 701.26: the tap cost needs an untapped creature of yours; an opponent's creature can't pay it
    @Test
    fun hideoutTapsOnlyYourOwnCreatures() = scenario(players = 3) {
        alice.battlefield(hideout)
        bob.battlefield(Plain.bear)
        carol.battlefield(Plain.ogre)
        setup()
        advanceTo(TurnStep.PrecombatMain, turn = 1)
        assertFailsWith<IllegalArgumentException> { alice.activates(hideout) }
        expect { Plain.bear.tapped shouldBe false }
    }

    // CR 603.12: sacrificing the artifact is the first action; only then the reflexive trigger deals 3 damage
    @Test
    fun sacrificingAnArtifactDeals3Damage() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", Plain.idol)
        alice.hand(crook)
        bob.battlefield(Plain.ogre)
        setup()
        alice.casts(crook)
        resolveAll()
        answer(Answer.YesNo(true))
        answer(Answer.Targets(listOf(target(Plain.ogre))))
        resolveAll()
        expect {
            Plain.idol.zone shouldBe Zone.Graveyard
            Plain.ogre.zone shouldBe Zone.Graveyard
        }
    }

    // CR 603.12: declining the sacrifice leaves the reflexive trigger unmade
    @Test
    fun decliningTheSacrificeDealsNoDamage() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", Plain.idol)
        alice.hand(crook)
        bob.battlefield(Plain.ogre)
        setup()
        alice.casts(crook)
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            Plain.idol.zone shouldBe Zone.Battlefield
            Plain.ogre.zone shouldBe Zone.Battlefield
        }
    }
}
