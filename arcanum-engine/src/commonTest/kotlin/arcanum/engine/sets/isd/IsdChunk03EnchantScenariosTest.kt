package arcanum.engine.sets.isd

import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** ISD chunk 03: Auras the engine hadn't played: a bonus that depends on the enchanted creature's type, Curses that enchant a player
 * (their creatures, their upkeep, combat damage to them, a choice between them and their planeswalker), preventing combat damage to
 * and by the enchanted creature, a bonus counted from a graveyard and a trigger on damage to an opponent of the Aura's controller
 * (CR 303.4). Invented names carry the oracle text of the real cards. */
class IsdChunk03EnchantScenariosTest {
    private val vow = Cards.invent("Vow of Kinship", "{1}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2 as long as it's a Human. Otherwise, it can't attack or block.
    """)
    private val wiltingRanks = Cards.invent("Hex of Wilting Ranks", "{3}{B}{B}", "Enchantment — Aura Curse", """
        Enchant player
        Creatures enchanted player controls get -1/-1.
    """)
    private val forgottenGraves = Cards.invent("Hex of Forgotten Graves", "{3}{B}", "Enchantment — Aura Curse", """
        Enchant player
        At the beginning of enchanted player's upkeep, that player exiles two cards from their graveyard.
    """)
    private val hunted = Cards.invent("Hex of the Hunted", "{1}{R}", "Enchantment — Aura Curse", """
        Enchant player
        Whenever a creature deals combat damage to enchanted player, put a +1/+1 counter on that creature.
    """)
    private val moonlitChase = Cards.invent("Hex of the Moonlit Chase", "{2}{R}", "Enchantment — Aura Curse", """
        Enchant player
        Creatures enchanted player controls attack each combat if able.
    """)
    private val needledHeart = Cards.invent("Hex of the Needled Heart", "{1}{R}", "Enchantment — Aura Curse", """
        Enchant player
        At the beginning of enchanted player's upkeep, this Aura deals 1 damage to that player or a planeswalker that player controls.
    """)
    private val phantomEmbrace = Cards.invent("Phantom Embrace", "{2}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature has flying.
        Prevent all combat damage that would be dealt to and dealt by enchanted creature.
    """)
    private val garland = Cards.invent("Garland of Shades", "{G}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +X/+X, where X is the number of creature cards in your graveyard.
    """)
    private val spark = Cards.invent("Inquisitive Spark", "{U}", "Enchantment — Aura", """
        Enchant creature
        Whenever enchanted creature deals damage to an opponent, you may draw a card.
    """)

    private val villager = Cards.invent("Gravecrest Villager", "{1}{W}", "Creature — Human Peasant", "", "2/2")
    private val hound = Cards.invent("Bogfen Hound", "{1}{G}", "Creature — Dog", "", "2/2")
    private val pup = Cards.invent("Bogfen Pup", "{G}", "Creature — Dog", "", "1/1")
    private val elk = Cards.invent("Fenwatch Elk", "{1}{G}", "Creature — Elk", "", "2/2")
    private val owl = Cards.invent("Skyreach Owl", "{1}{W}", "Creature — Bird", "Flying", "2/2")
    private val seer = Cards.invent("Lantern Seer", "{2}{U}", "Legendary Planeswalker — Seer", "[+1]: You gain 1 life.", loyalty = "3")

    // CR 611.3a: the bonus applies while the enchanted creature is a Human
    @Test
    fun vowPumpsAHuman() = scenario {
        alice.battlefield(villager, vow)
        attach(vow, villager)
        expect {
            villager.power shouldBe 4
            villager.toughness shouldBe 4
        }
    }

    // CR 611.3a, 508.1c: a creature that isn't a Human gets no bonus and can't attack
    @Test
    fun vowStopsANonHuman() = scenario {
        alice.battlefield(hound, vow)
        attach(vow, hound)
        expect { hound.power shouldBe 2 }
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(hound) }
    }

    // CR 303.4e, 613.4c, 704.5f: only the enchanted player's creatures get -1/-1; a 1/1 of theirs dies
    @Test
    fun wiltingRanksShrinksOnlyTheEnchantedPlayersCreatures() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(wiltingRanks)
        bob.battlefield(hound, pup)
        carol.battlefield(elk)
        alice.casts(wiltingRanks, target = "bob")
        allPass()
        expect {
            hound.power shouldBe 1
            hound.toughness shouldBe 1
            bob.graveyard has pup
            elk.power shouldBe 2
        }
    }

    // CR 503.1a: the enchanted player's upkeep; "that player" chooses the two cards
    @Test
    fun forgottenGravesExilesTwoCardsOfThatPlayersChoice() = scenario {
        alice.battlefield(forgottenGraves)
        attach(forgottenGraves, "bob")
        alice.graveyard("Forest")
        bob.graveyard("Island", "Swamp", "Mountain")
        bob.library("Plains")
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        assertEquals(bob.id, (pending as Decision.ChooseObjects).player)
        bob.chooses("Island", "Swamp")
        resolveAll()
        expect {
            bob.exile has "Island"
            bob.exile has "Swamp"
            bob.graveyard has "Mountain"
            alice.graveyard has "Forest"
        }
    }

    // CR 609.3: with only one card in their graveyard, they exile that one
    @Test
    fun forgottenGravesExilesWhatThereIs() = scenario {
        alice.battlefield(forgottenGraves)
        attach(forgottenGraves, "bob")
        bob.graveyard("Island")
        bob.library("Plains")
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect {
            bob.exile has "Island"
            bob.graveyard.isEmpty()
        }
    }

    // CR 510.2, 603.2: only combat damage to the enchanted player triggers, whoever controls the Curse
    @Test
    fun huntedRewardsCreaturesThatHitTheEnchantedPlayer() = scenario(players = 3) {
        alice.battlefield(hound, elk)
        carol.battlefield(hunted)
        attach(hunted, "bob")
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(hound), TargetRef.Player(bob.id)), Attack(card(elk), TargetRef.Player(carol.id)))))
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        assertEquals(18, bob.life)
        assertEquals(18, carol.life)
        expect {
            hound.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            elk.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 508.1d: the enchanted player's creatures must attack; another player's don't
    @Test
    fun moonlitChaseMakesTheEnchantedPlayersCreaturesAttack() = scenario(players = 3) {
        alice.battlefield(moonlitChase)
        attach(moonlitChase, "bob")
        bob.battlefield(hound)
        carol.battlefield(elk)
        turn(number = 2, active = bob)
        expect {
            hound hasMechanic { it is Mechanic.MustAttack }
            elk lacksMechanic { it is Mechanic.MustAttack }
        }
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { bob.attacks() }
        bob.attacks(hound, defender = "carol")
    }

    // CR 608.2d: with no planeswalker to choose, the player is dealt the damage
    @Test
    fun needledHeartDamagesTheEnchantedPlayer() = scenario {
        alice.battlefield(needledHeart)
        attach(needledHeart, "bob")
        bob.library("Plains")
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        assertEquals(19, bob.life)
        assertEquals(20, alice.life)
    }

    // CR 608.2d, 120.3c: the Aura's controller may choose one of that player's planeswalkers instead; damage to it removes loyalty
    @Test
    fun needledHeartMayDamageThatPlayersPlaneswalker() = scenario {
        alice.battlefield(needledHeart)
        attach(needledHeart, "bob")
        bob.battlefield(seer, counters = listOf(CounterKind.Loyalty to 3))
        bob.library("Plains")
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        assertEquals(alice.id, (pending as Decision.ChooseObjects).player)
        alice.chooses(seer)
        resolveAll()
        assertEquals(20, bob.life)
        expect { seer.counters(CounterKind.Loyalty) shouldBe 2 }
    }

    // CR 615.1, 702.9b: the flying creature's combat damage and the combat damage dealt to it are both prevented
    @Test
    fun phantomEmbracePreventsCombatDamageBothWays() = scenario {
        alice.battlefield(hound, phantomEmbrace)
        attach(phantomEmbrace, hound)
        bob.battlefield(owl)
        expect { hound hasKeyword arcanum.api.Keyword.Flying.kind }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hound)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(owl to hound)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            hound.zone shouldBe Zone.Battlefield
            owl.zone shouldBe Zone.Battlefield
            hound.view.obj?.damage shouldBe 0
            owl.view.obj?.damage shouldBe 0
        }
    }

    // CR 208.1, 611.3a: X counts the creature cards in the Aura controller's graveyard, as it changes
    @Test
    fun garlandCountsCreatureCardsInYourGraveyard() = scenario {
        alice.battlefield(hound, elk, garland)
        attach(garland, hound)
        alice.graveyard(pup, villager, "Forest")
        bob.graveyard(owl)
        expect { hound.power shouldBe 4; hound.toughness shouldBe 4 }
        move(elk, Zone.Graveyard)
        expect { hound.power shouldBe 5; hound.toughness shouldBe 5 }
    }

    // CR 102.2, 603.2: an opponent of the Aura's controller, here a creature of another player's hitting a third player
    @Test
    fun sparkDrawsWhenTheEnchantedCreatureDamagesAnOpponent() = scenario(players = 3) {
        alice.battlefield(spark)
        bob.battlefield(hound)
        attach(spark, hound)
        alice.library("Island")
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(hound, defender = "carol")
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        assertEquals(alice.id, (pending as Decision.May).player)
        answer(Answer.YesNo(true))
        resolveAll()
        assertEquals(18, carol.life)
        expect { alice.hand has "Island" }
    }

    // CR 102.2: damage to the Aura's controller isn't damage to an opponent of theirs
    @Test
    fun sparkIgnoresDamageToItsController() = scenario(players = 3) {
        alice.battlefield(spark)
        bob.battlefield(hound)
        attach(spark, hound)
        alice.library("Island")
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(hound, defender = "alice")
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        assertEquals(18, alice.life)
        assertTrue(pending == null && state.stack.isEmpty())
        expect { alice.hand.isEmpty() }
    }
}
