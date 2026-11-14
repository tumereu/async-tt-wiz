package arcanum.engine.sets.dmr

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.tokens
import arcanum.engine.state.ObjectId
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Dominaria Remastered chunk 06 in play: extra draws with cards drawn this turn paid for or put back (CR 119.4, 400.7), life for the land
 * cards discarded (CR 701.9a), protection from a color chosen as the ability resolves (CR 702.16a), a creature returned from any graveyard
 * made a black Nightmare (CR 105.3, 205.1b), static color and type changes (CR 613.1d-e), lands made creatures that are still lands
 * (CR 205.1b), "each other player" (CR 102.3), an attack limit on one player (CR 508.1c), Saprolings for each permanent of a chosen color
 * (CR 111.1) and each player's upkeep payment (CR 603.2).
 */
class DmrChunk06EffectsScenariosTest {
    private val archive = Cards.invent("Grove Archive", "{1}{G}", "Enchantment",
        "At the beginning of your draw step, you may draw two additional cards. If you do, choose two cards in your hand drawn this turn. " +
            "For each of those cards, pay 4 life or put the card on top of your library.")
    private val judgment = Cards.invent("Captain's Judgment", "{W}{B}", "Sorcery",
        "Target player discards two cards. You gain 3 life for each land card discarded this way.")
    private val avatar = Cards.invent("Radiant Avatar", "{3}{W}{W}", "Creature — Incarnation", """
        Flying
        {2}{W}: Choose a color. Creatures you control gain protection from the chosen color until end of turn. Activate only if this card is in your graveyard.
    """, "3/3")
    private val vessar = Cards.invent("Vessar, Dread Keeper", "{3}{B}{B}", "Legendary Creature — Human Minion", """
        All Nightmares get +1/+1.
        {B}{B}{B}, Pay 3 life: Put target creature card from a graveyard onto the battlefield under your control. That creature is black and is a Nightmare in addition to its other creature types.
        When Vessar leaves the battlefield, exile all Nightmares.
    """, "3/3")
    private val pilgrimage = Cards.invent("Warlord's Pilgrimage", "{1}{B}{R}", "Enchantment", """
        All Goblins get +1/+1.
        All Goblins are black and are Zombies in addition to their other creature types.
    """)
    private val uprising = Cards.invent("Verdant Uprising", "{G}", "Sorcery",
        "All lands you control become 1/1 creatures until end of turn. They're still lands.")
    private val leechMage = Cards.invent("Bogland Leech-Mage", "{2}{B}", "Creature — Human Spellshaper",
        "{2}{B}, {T}, Discard a card: Each other player loses 2 life. You gain life equal to the life lost this way.", "2/2")
    private val tunnel = Cards.invent("Narrow Tunnel", "{3}", "Artifact", "No more than two creatures can attack you each combat.")
    private val thalos = Cards.invent("Thalos, the Verdant Wyrm", "{3}{R}{G}{W}", "Legendary Creature — Dragon", """
        Flying
        Whenever Thalos deals combat damage to a player, you may pay {2}{G}. If you do, choose a color, then create a 1/1 green Saproling creature token for each permanent of that color.
    """, "6/6")
    private val cord = Cards.invent("Tethering Cord", "{4}", "Artifact",
        "At the beginning of each player's upkeep, that player may pay 2 life. If they don't, they return a permanent they control to its owner's hand.")

    private val grazer = Cards.invent("Thornback Grazer", "{1}{G}", "Creature — Beast", "", "2/2")
    private val picket = Cards.invent("Watchful Picket", "{1}{W}", "Creature — Soldier", "Vigilance", "2/2")
    private val ox = Cards.invent("Ironhide Ox", "{2}{W}", "Creature — Ox", "", "2/3")
    private val goblin = Cards.invent("Scrapyard Goblin", "{R}", "Creature — Goblin", "", "1/1")
    private val jolt = Cards.invent("Scorchbolt Jolt", "{R}", "Instant", "Scorchbolt Jolt deals 3 damage to any target.")

    /** Alice's draw step of turn 3, her library topped by [top]: the Archive's trigger on the Stack. */
    private fun arcanum.engine.scenario.Scenario.aliceDrawStep(vararg top: String) {
        alice.library(*top)
        bob.library("Swamp", "Swamp")
        advanceTo(TurnStep.Draw, turn = 3)
    }

    /** The offered card [name] of alice's: the cards drawn by the resolving trigger are new objects in her hand (CR 400.7) while the
     * scenario's state still shows them in the library. */
    private fun arcanum.engine.scenario.Scenario.offered(name: String): ObjectId {
        val card = (card(name, owner = alice) as ObjectId.Card).card
        return decision<Decision.ChooseObjects>().options.first { (it as? ObjectId.Card)?.card == card }
    }

    // CR 504.1, 400.7: the card drawn in the draw step and the two extra ones were drawn this turn, the card already in hand wasn't;
    // CR 119.4: 4 life paid for each chosen card keeps both
    @Test
    fun archiveDrawsTwoAndPaysLifeToKeepThem() = scenario {
        alice.battlefield(archive)
        alice.hand(grazer)
        aliceDrawStep("Island", "Forest", "Mountain", "Plains")
        resolveAll()
        may(true)
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(setOf(offered("Island"), offered("Forest"), offered("Mountain")), choice.options.toSet())
        assertEquals(2, choice.min)
        answer(Answer.Objects(listOf(offered("Island"), offered("Mountain"))))
        may(true)
        may(true)
        resolveAll()
        expect {
            alice.life shouldBe 12
            alice.hand has "Island"
            alice.hand has "Forest"
            alice.hand has "Mountain"
            alice.library.names shouldBe listOf("Plains")
        }
    }

    // CR 401.1: a chosen card not paid for goes back on top of the library
    @Test
    fun archivePutsUnpaidCardsBackOnTop() = scenario {
        alice.battlefield(archive)
        aliceDrawStep("Island", "Forest", "Mountain", "Plains")
        resolveAll()
        may(true)
        answer(Answer.Objects(listOf(offered("Island"), offered("Mountain"))))
        may(false)
        may(false)
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.hand.names shouldBe listOf("Forest")
            alice.library.names.take(2).toSet() shouldBe setOf("Island", "Mountain")
            alice.library.names.last() shouldBe "Plains"
        }
    }

    // CR 701.9a: only the land card among the discarded ones counts
    @Test
    fun judgmentGainsLifeForEachLandDiscarded() = scenario {
        alice.hand(judgment)
        bob.hand("Island", grazer)
        alice.castsWithoutCosts(judgment, target = "bob")
        resolveAll()
        expect {
            bob.graveyard has "Island"
            bob.graveyard has grazer
            alice.life shouldBe 23
        }
    }

    // CR 702.16a, 602.5b: activated from the graveyard; alice's creatures can't be targeted by red, bob's still can
    @Test
    fun avatarGivesProtectionFromTheChosenColor() = scenario {
        alice.graveyard(avatar)
        alice.battlefield(grazer, "Plains", "Plains", "Plains")
        bob.battlefield(picket, "Mountain")
        bob.hand(jolt)
        alice.activates(avatar)
        resolveAll()
        answer(Answer.ColorChoice(Color.Red))
        resolveAll()
        alice.passes()
        bob.casts(jolt)
        val legal = decision<Decision.Targets>().options
        assertTrue(target(grazer) !in legal)
        assertTrue(target(picket) in legal)
        answer(Answer.Targets(listOf(target(picket))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect {
            bob.graveyard has picket
            alice.battlefield has grazer
        }
    }

    // CR 105.3, 205.1b: bob's creature card enters under alice's control as a black Beast Nightmare, so it gets +1/+1 (CR 613.4c);
    // CR 603.6c, 603.10a: when Vessar leaves, every Nightmare is exiled
    @Test
    fun vessarMakesANightmareAndExilesThemWhenItLeaves() = scenario {
        alice.battlefield(vessar, "Swamp", "Swamp", "Swamp")
        bob.graveyard(grazer)
        bob.hand(jolt)
        alice.activates(vessar, target = grazer)
        resolveAll()
        expect {
            alice.battlefield has grazer
            alice.life shouldBe 17
            grazer.colors shouldBe listOf(Color.Black)
            grazer.subtypes.toSet() shouldBe setOf(Subtype("Beast"), Subtype("Nightmare"))
            grazer.power shouldBe 3
            grazer.toughness shouldBe 3
        }
        alice.passes()
        bob.castsWithoutCosts(jolt, target = vessar)
        resolveAll()
        expect {
            alice.graveyard has vessar
            bob.exile has grazer
        }
    }

    // CR 613.1d, 613.1e: every Goblin, whoever controls it, is a black Goblin Zombie and gets +1/+1
    @Test
    fun pilgrimageMakesEveryGoblinABlackZombie() = scenario {
        alice.battlefield(pilgrimage, grazer)
        bob.battlefield(goblin)
        expect {
            goblin.power shouldBe 2
            goblin.toughness shouldBe 2
            goblin.colors shouldBe listOf(Color.Black)
            goblin.subtypes.toSet() shouldBe setOf(Subtype("Goblin"), Subtype("Zombie"))
            grazer.power shouldBe 2
            grazer.colors shouldBe listOf(Color.Green)
        }
    }

    // CR 205.1b, 613.1d, 613.4b: alice's lands are 1/1 land creatures until end of turn; bob's land isn't
    @Test
    fun uprisingMakesLandsCreaturesThatAreStillLands() = scenario {
        alice.battlefield("Forest", "Island")
        bob.battlefield("Plains")
        alice.hand(uprising)
        alice.castsWithoutCosts(uprising)
        resolveAll()
        expect {
            "Forest".power shouldBe 1
            "Forest".toughness shouldBe 1
            "Forest".types.toSet() shouldBe setOf(CardType.Land, CardType.Creature)
            "Forest".subtypes shouldBe listOf(Subtype("Forest"))
            "Island".power shouldBe 1
            "Plains".types shouldBe listOf(CardType.Land)
        }
    }

    // CR 102.3: each other player loses 2 life, opponent or not; alice gains the total lost (CR 119.3)
    @Test
    fun leechMageDrainsEveryOtherPlayer() = scenario(players = 3) {
        alice.battlefield(leechMage, "Swamp", "Swamp", "Swamp")
        alice.hand("Island")
        alice.activates(leechMage)
        resolveAll()
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 18
            alice.life shouldBe 24
            alice.graveyard has "Island"
        }
    }

    // CR 508.1c: no more than two creatures attack alice; a third may attack carol (the limit is only on attacking alice)
    @Test
    fun tunnelLetsOnlyTwoCreaturesAttackItsController() = scenario(players = 3) {
        turn(1, active = bob)
        alice.battlefield(tunnel)
        bob.battlefield(grazer, picket, ox)
        advanceTo(TurnStep.DeclareAttackers)
        val allAtAlice = Input.DeclareAttackers(bob.id, listOf(grazer, picket, ox).map { Attack(card(it), TargetRef.Player(alice.id)) })
        assertTrue(Engine.options(state, bob.id).none { it == allAtAlice })
        assertFailsWith<IllegalArgumentException> { perform(allAtAlice) }
        perform(Input.DeclareAttackers(bob.id, listOf(Attack(card(grazer), TargetRef.Player(alice.id)), Attack(card(picket), TargetRef.Player(alice.id)),
            Attack(card(ox), TargetRef.Player(carol.id)))))
        expect {
            grazer.tapped shouldBe true
            ox.tapped shouldBe true
        }
    }

    // CR 510.3a, 118.12: paid; green chosen: a Saproling for each green permanent then (Thalos itself and bob's Beast), lands are colorless
    @Test
    fun thalosCreatesSaprolingsForEachPermanentOfTheChosenColor() = scenario {
        alice.battlefield(thalos, "Forest", "Forest", "Forest")
        bob.battlefield(grazer, picket)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(thalos)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        may(true)
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        answer(Answer.ColorChoice(Color.Green))
        resolveAll()
        expect {
            bob.life shouldBe 14
            tokens("Saproling Token").size shouldBe 2
        }
    }

    // CR 603.2: at bob's upkeep bob decides; not paying, he returns a permanent he chooses (CR 608.2d)
    @Test
    fun cordMakesThePlayerWhoseUpkeepItIsPayOrReturn() = scenario(players = 3) {
        alice.battlefield(cord)
        bob.battlefield(grazer, "Forest")
        bob.library("Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(false))
        assertEquals(bob.id, decision<Decision.ChooseObjects>().player)
        bob.chooses(grazer)
        resolveAll()
        expect {
            bob.hand has grazer
            bob.battlefield has "Forest"
            bob.life shouldBe 20
            alice.battlefield has cord
        }
    }

    /** Paying 2 life keeps everything. */
    @Test
    fun cordPaidKeepsEverything() = scenario {
        alice.battlefield(cord)
        bob.battlefield(grazer)
        bob.library("Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.life shouldBe 18
            bob.battlefield has grazer
        }
    }
}
