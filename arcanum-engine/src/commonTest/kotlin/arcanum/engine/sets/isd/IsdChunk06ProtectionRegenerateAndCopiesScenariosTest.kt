package arcanum.engine.sets.isd

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ecl.paySuggested
import arcanum.engine.sets.ltr.tokens
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** ISD chunk 06: protection from a creature type (CR 702.16), a static that holds while it has a keyword and an ability that removes the
 * keyword (CR 613.1f), shuffling itself in and revealing until a named card (CR 701.24c, 701.20a), copies as they enter with an added ability
 * or of this creature (CR 707.9), a cost of counters and a sacrifice whose effect makes the returned cards Zombies (CR 611.2e), a name no player
 * can cast (CR 101.2), counters counted from two places with a prevention rider (CR 615.5), half a count rounded down and tokens created
 * attacking (CR 508.4). Invented names carry the oracle text of the real cards. */
class IsdChunk06ProtectionRegenerateAndCopiesScenariosTest {
    private val inquisitor = Cards.invent("Vigil Inquisitor", "{W}{W}", "Creature — Human Soldier", """
        First strike, vigilance
        Protection from Vampires, from Werewolves, and from Zombies
    """, "2/2")
    private val wraith = Cards.invent("Glassmad Wraith", "{3}{U}{U}", "Creature — Spirit", """
        Flying
        {1}{U}: This creature's owner shuffles it into their library. If that player does, they reveal cards from the top of that library until a card named Glassmad Wraith is revealed. The player puts that card onto the battlefield and all other cards revealed this way into their graveyard.
    """, "5/1")
    private val gargoyle = Cards.invent("Belfry Gargoyle", "{5}", "Artifact Creature — Gargoyle", """
        Defender
        This creature has indestructible as long as it has defender.
        {1}: Until end of turn, this creature loses defender and gains flying.
    """, "4/4")
    private val doppel = Cards.invent("Sinister Doppel", "{2}{U}{B}", "Creature — Shapeshifter",
        "You may have this creature enter as a copy of any creature on the battlefield, except it has \"{U}{B}, {T}: Destroy target creature with the same name as this creature.\"",
        "0/0")
    private val tome = Cards.invent("Tome of the Restless", "{4}", "Legendary Artifact — Book", """
        {1}, {T}, Discard a card: Put a study counter on Tome of the Restless.
        {T}, Remove three study counters from Tome of the Restless and sacrifice it: Put all creature cards from all graveyards onto the battlefield under your control. They're black Zombies in addition to their other colors and types.
    """)
    private val neverwhen = Cards.invent("Neverwhen", "{1}{W}{W}", "Enchantment", """
        As this enchantment enters, choose a nonland card name.
        Spells with the chosen name can't be cast.
    """)
    private val thicket = Cards.invent("Spirit of the Thicket", "{3}{G}{G}{G}", "Creature — Avatar",
        "Creatures you control enter as a copy of this creature.", "6/6")
    private val throng = Cards.invent("Breathless Throng", "{2}{B}", "Creature — Zombie", """
        This creature enters with a +1/+1 counter on it for each other Zombie you control and each Zombie card in your graveyard.
        If this creature would be dealt damage, prevent that damage and remove a +1/+1 counter from it.
    """, "0/0")
    private val ranks = Cards.invent("Ceaseless Ranks", "{2}{B}{B}", "Enchantment",
        "At the beginning of your upkeep, create X 2/2 black Zombie creature tokens, where X is half the number of Zombies you control, rounded down.")
    private val breakers = Cards.invent("Kennel Breakers", "{4}{G}", "Creature — Human Rogue",
        "Whenever this creature attacks, create a 2/2 green Wolf creature token that's tapped and attacking for each creature card in your graveyard.", "3/4")

    private val zombie = Cards.invent("Hollowmoor Zombie", "{1}{B}", "Creature — Zombie", "", "2/2")
    private val corpse = Cards.invent("Hollowmoor Corpse", "{B}", "Creature — Zombie", "", "1/1")
    private val bear = Cards.invent("Hollowmoor Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Hollowmoor Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")

    // CR 702.16e: a Zombie can't block it; a creature of another type can
    @Test
    fun inquisitorCantBeBlockedByZombies() = scenario {
        alice.battlefield(inquisitor)
        bob.battlefield(zombie, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(inquisitor)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(zombie to inquisitor) }
        bob.blocks(bear to inquisitor)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.graveyard has bear }
    }

    // CR 611.3a: indestructible while it has defender; CR 613.1f: once it loses defender it loses indestructible too, and flies
    @Test
    fun gargoyleLosesIndestructibleWithDefender() = scenario {
        alice.battlefield(gargoyle, "Plains")
        expect { gargoyle hasKeyword Keyword.Indestructible.kind }
        alice.activates(gargoyle)
        allPass()
        expect {
            gargoyle lacksKeyword Keyword.Defender.kind
            gargoyle lacksKeyword Keyword.Indestructible.kind
            gargoyle hasKeyword Keyword.Flying.kind
        }
    }

    // CR 701.24a, 701.20a: shuffled in, then the cards above it go to the graveyard and it returns (a new object, CR 400.7)
    @Test
    fun wraithShufflesInAndReturns() = scenario {
        alice.battlefield(wraith, "Island", "Island")
        alice.library("Swamp", "Swamp", "Swamp")
        alice.activates(wraith)
        allPass()
        expect {
            alice.battlefield has wraith
            alice.graveyard.names.all { it == "Swamp" } shouldBe true
            (alice.graveyard.size + alice.library.size) shouldBe 3
        }
    }

    // CR 701.24c: gone before it resolves, the library is still shuffled, but nothing was shuffled in, so nothing is revealed
    @Test
    fun wraithKilledInResponseRevealsNothing() = scenario {
        alice.battlefield(wraith, "Island", "Island", "Mountain")
        alice.hand("Storm Spark")
        alice.library("Swamp", "Swamp", "Swamp")
        alice.activates(wraith, paying = listOf("Island", "Island#1"))
        alice.casts("Storm Spark", target = wraith)
        resolveAll()
        expect {
            alice.graveyard has wraith
            alice.library.size shouldBe 3
        }
    }

    // CR 707.9b: the copy has the added ability; it destroys a creature with its copied name (CR 201.2), not another
    @Test
    fun doppelDestroysTheCreatureItCopied() = scenario {
        alice.battlefield("Island", "Island", "Swamp", "Swamp")
        alice.hand(doppel)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        bob.battlefield(bear, ogre)
        alice.casts(doppel)
        allPass()
        alice.chooses(bear)
        expect {
            doppel.power shouldBe 2
            doppel.subtypes shouldBe listOf(Subtype("Bear"))
        }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates(doppel)
        // only the creatures named Hollowmoor Bear: Bob's and the copy itself
        assertEquals(setOf(target(bear), target(doppel)), (pending as Decision.Targets).options.toSet())
        answer(Answer.Targets(listOf(target(bear))))
        paySuggested()
        allPass()
        expect {
            bob.graveyard has bear
            bob.battlefield has ogre
        }
    }

    // CR 118.3: three counters removed and the Tome sacrificed; CR 611.2e: the cards from both graveyards enter as black Zombies too
    @Test
    fun tomeReturnsEveryCreatureAsZombies() = scenario {
        alice.battlefield(tome, counters = listOf(CounterKind.Named("study") to 3))
        alice.graveyard(bear)
        bob.graveyard(ogre)
        alice.activates(tome, ability = 1)
        allPass()
        expect {
            alice.graveyard has tome
            bear.controller shouldBe alice.id
            ogre.controller shouldBe alice.id
            ogre.colors.toSet() shouldBe setOf(Color.Red, Color.Black)
            ogre.subtypes.toSet() shouldBe setOf(Subtype("Ogre"), Subtype("Zombie"))
            bear.subtypes.toSet() shouldBe setOf(Subtype("Bear"), Subtype("Zombie"))
        }
    }

    // CR 602.2: with two study counters the cost can't be paid
    @Test
    fun tomeNeedsThreeCounters() = scenario {
        alice.battlefield(tome, counters = listOf(CounterKind.Named("study") to 2))
        alice.graveyard(bear)
        assertFailsWith<IllegalArgumentException> { alice.activates(tome, ability = 1) }
    }

    // CR 101.2: no player, its controller included, can cast a spell with the chosen name (three players)
    @Test
    fun neverwhenStopsEveryPlayer() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Mountain")
        alice.hand(neverwhen, "Storm Spark")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.casts(neverwhen, paying = listOf("Plains", "Plains#1", "Plains#2"))
        allPass()
        answer(Answer.CardName("Storm Spark"))
        assertFailsWith<IllegalArgumentException> { alice.casts("Storm Spark", target = "bob") }
        alice.passes()
        assertTrue(Engine.options(state, bob.id).none { it is Input.Cast })
    }

    // CR 707.9, 614.1c: a creature entering under your control enters as a copy of it
    @Test
    fun thicketMakesEntersCopies() = scenario {
        alice.battlefield(thicket, "Forest", "Forest")
        alice.hand(bear)
        alice.casts(bear)
        allPass()
        expect {
            bear.power shouldBe 6
            bear.toughness shouldBe 6
            bear.subtypes shouldBe listOf(Subtype("Avatar"))
        }
    }

    // CR 614.1c: one other Zombie and one Zombie card in the graveyard: two counters; CR 615.5: 3 damage prevented and one counter removed
    @Test
    fun throngCountsAndShrugsOffDamage() = scenario {
        alice.battlefield(zombie, "Swamp", "Swamp", "Swamp", "Mountain")
        alice.graveyard(corpse, bear)
        alice.hand(throng, "Storm Spark")
        alice.casts(throng, paying = listOf("Swamp", "Swamp#1", "Swamp#2"))
        allPass()
        expect { throng.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2 }
        alice.casts("Storm Spark", target = throng)
        allPass()
        expect {
            throng.zone shouldBe Zone.Battlefield
            throng.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            throng.view.obj?.damage shouldBe 0
        }
    }

    // CR 107.1a: three Zombies make one token (half of three, rounded down)
    @Test
    fun ranksMakesHalfAsManyZombies() = scenario {
        alice.battlefield(ranks, zombie, corpse, throng, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.library("Swamp", "Swamp")
        bob.library("Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        assertEquals(1, tokens("Zombie Token").size)
    }

    // CR 508.4: two creature cards in the graveyard, two Wolves enter attacking; all three deal damage
    @Test
    fun breakersBringWolvesAttacking() = scenario {
        alice.battlefield(breakers)
        alice.graveyard(bear, ogre, "Forest")
        alice.library("Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(breakers)
        resolveAll()
        assertEquals(2, tokens("Wolf Token").size)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 13 }
    }
}
