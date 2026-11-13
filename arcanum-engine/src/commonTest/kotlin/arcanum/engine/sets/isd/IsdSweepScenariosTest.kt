package arcanum.engine.sets.isd

import arcanum.api.Mechanic
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** ISD sweep: siblings of the bugs ISD set work found, played with invented cards in the wordings each fix's root cause could equally
 * affect. Invented names carry real oracle wordings. */
class IsdSweepScenariosTest {
    private val tithe = Cards.invent("Bitter Tithe of Vesk", "{1}{B}{B}", "Enchantment",
        "Whenever a creature an opponent controls enters, that player loses 2 life and you gain 2 life.")
    private val lending = Cards.invent("Unquiet Lending", "{2}{B}", "Sorcery",
        "Put target creature card from a graveyard onto the battlefield under your control. You lose life equal to that card's mana value.")
    private val ox = Cards.invent("Loamback Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val levy = Cards.invent("Graveward Levy", "{2}{B}", "Enchantment", "Whenever a creature an opponent controls dies, that player loses 1 life.")
    private val idol = Cards.invent("Marrowtithe Idol", "{3}", "Artifact",
        "Whenever a creature card is put into an opponent's graveyard from anywhere, that player mills a card.")
    private val hound = Cards.invent("Mudlark Hound", "{1}{G}", "Creature — Dog", "", "2/2")
    private val verdict = Cards.invent("Sweep Verdict", "{1}{B}", "Instant", "Destroy target creature.")
    private val sentry = Cards.invent("Kilnward Sentry", "{2}{W}", "Creature — Human Soldier",
        "Whenever a creature deals combat damage to this creature, exile that creature.", "1/4")
    private val brute = Cards.invent("Hollowfen Brute", "{2}{G}", "Creature — Beast", "", "2/2")
    private val culvert = Cards.invent("Slimewell Culvert", "{4}{G}", "Enchantment",
        "Whenever a nontoken creature you control dies, put a slime counter on this enchantment, then create a green Ooze creature token with \"This token's power and toughness are each equal to the number of slime counters on Slimewell Culvert.\"")
    private val lattice = Cards.invent("Echo Lattice", "{3}", "Artifact",
        "{2}, {T}: Copy target triggered ability you control. You may choose new targets for the copy.")
    private val rat = Cards.invent("Culvert Rat", "{B}", "Creature — Rat", "", "1/1")
    private val jolt = Cards.invent("Culvert Jolt", "{R}", "Instant", "Culvert Jolt deals 2 damage to target creature.")

    // CR 110.2, 603.2: "that player" of a creature entering is the player it enters under the control of (Bob, who put Carol's card
    // onto the battlefield), not its owner
    @Test
    fun aCreatureEnteringUnderAnotherPlayersControlTaxesItsController() = scenario(players = 3) {
        alice.battlefield(tithe)
        carol.graveyard(ox)
        bob.battlefield("Swamp", "Swamp", "Swamp")
        bob.hand(lending)
        turn(number = 2, active = bob)
        bob.casts(lending, target = "Loamback Ox")
        resolveAll()
        expect { ox.zone shouldBe Zone.Battlefield }
        assertEquals(15, bob.life, "3 for the card's mana value, 2 for the tithe")
        assertEquals(20, carol.life, "the card's owner doesn't control it")
        assertEquals(22, alice.life)
    }

    // CR 110.2, 603.10a: Carol controlled Bob's Hound as it died: she is "that player", not its owner Bob
    @Test
    fun aStolenCreatureDyingTaxesTheOpponentWhoControlledIt() = scenario(players = 3) {
        alice.battlefield(levy, "Swamp", "Swamp")
        alice.hand(verdict)
        bob.battlefield(hound)
        effect(carol, Mechanic.ControlledBy(Players.You), on = listOf("Mudlark Hound"))
        alice.casts(verdict, target = "Mudlark Hound")
        resolveAll()
        expect { hound.zone shouldBe Zone.Graveyard }
        assertEquals(19, carol.life)
        assertEquals(20, bob.life)
    }

    // CR 404.1: the card goes to its owner Bob's graveyard; Bob is "that player" and mills, though Carol controlled it
    @Test
    fun aStolenCreatureCardMillsTheGraveyardsOwner() = scenario(players = 3) {
        alice.battlefield(idol, "Swamp", "Swamp")
        alice.hand(verdict)
        bob.battlefield(hound)
        bob.library("Forest", "Forest")
        carol.library("Island", "Island")
        effect(carol, Mechanic.ControlledBy(Players.You), on = listOf("Mudlark Hound"))
        alice.casts(verdict, target = "Mudlark Hound")
        resolveAll()
        expect { hound.zone shouldBe Zone.Graveyard }
        assertEquals(1, bob.library.names.size, "Bob milled one")
        assertEquals(2, carol.library.names.size)
    }

    // CR 120.2, 603.2: the creature that dealt combat damage to the Sentry is exiled; the Sentry stays
    @Test
    fun kilnwardSentryExilesTheCreatureThatDamagedIt() = scenario {
        alice.battlefield(brute)
        bob.battlefield(sentry)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(brute)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(sentry to brute)
        advanceTo(TurnStep.EndOfCombat)
        expect {
            brute.zone shouldBe Zone.Exile
            sentry.zone shouldBe Zone.Battlefield
        }
    }

    // CR 707.10, 113.7: a copy of a triggered ability has the same source, so the token it creates names the same enchantment
    // (CR 201.5a); each ability put a slime counter on it, so both Oozes are 2/2
    @Test
    fun aCopiedTriggerMakesAnOozeCountingTheSameEnchantment() = scenario {
        alice.battlefield(culvert, lattice, rat, "Mountain", "Island", "Island")
        alice.hand(jolt)
        alice.casts(jolt, target = "Culvert Rat")
        allPass()
        expect { stack.names shouldBe listOf("ability of Slimewell Culvert") }
        alice.activates(lattice)
        if (pending is arcanum.engine.decision.Decision.Targets)
            alice.answers(arcanum.engine.decision.Answer.Targets(listOf(arcanum.engine.state.TargetRef.Object(state.stack.first()))))
        (pending as? arcanum.engine.decision.Decision.ManaPayment)?.let { alice.answers(arcanum.engine.decision.Answer.ManaPayment(it.suggestion)) }
        resolveAll()
        val oozes = state.battlefield.filter { state.obj(it)?.token == true }
        assertEquals(2, oozes.size, "battlefield ${state.battlefield.map { state.obj(it)?.let { o -> nameOf(o) } }}, stack ${state.stack}, " +
            "pending $pending, culvert ${state.obj(card("Slimewell Culvert"))?.counters}")
        expect {
            oozes.forEach { arcanum.engine.scenario.CardRef(it, "Ooze").power shouldBe 2 }
            oozes.forEach { arcanum.engine.scenario.CardRef(it, "Ooze").toughness shouldBe 2 }
        }
    }
}
