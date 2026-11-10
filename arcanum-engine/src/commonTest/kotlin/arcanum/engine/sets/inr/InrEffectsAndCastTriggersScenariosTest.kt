package arcanum.engine.sets.inr

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dsk.grantedWay
import arcanum.engine.sets.mh2.playOut
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** INR chunk 09: a {0} alternative cost for Zombie creature spells, cards exiled with an artifact returned when it goes to the graveyard, a
 * life payment for each creature by its controller, piles of permanents, opponents that can't make you sacrifice, a search bounded by the
 * sacrificed creature's mana value, madness given to cards in hand, a trigger on casting from the graveyard, eminence, and a copy of a spell
 * for each other creature its caster controls. Invented names carry the oracle text of the real cards. */
class InrEffectsAndCastTriggersScenariosTest {
    private val tempest = Cards.invent("Gravecall Tempest", "{5}{U}", "Enchantment",
        "You may pay {0} rather than pay the mana cost for Zombie creature spells you cast.", null)
    private val reliquary = Cards.invent("Sealed Reliquary", "{3}", "Legendary Artifact", """
        {1}, {T}: Exile target creature you control.
        {7}, {T}: Exile target creature you don't control.
        When Sealed Reliquary is put into a graveyard from the battlefield, return all cards exiled with it to the battlefield under their owners' control.
    """.trimIndent(), null)
    private val tide = Cards.invent("Tide of Culling", "{X}{B}", "Sorcery", "For each creature, its controller sacrifices it unless they pay X life.", null)
    private val edictmistress = Cards.invent("Veiled Edictmistress", "{1}{B}{B}", "Legendary Planeswalker — Edict", """
        [+1]: Each player discards a card.
        [−2]: Target player sacrifices a creature.
        [−6]: Separate all permanents target player controls into two piles. That player sacrifices all permanents in the pile of their choice.
    """.trimIndent(), null)
    private val selvala = Cards.invent("Selvala Skyward Mother", "{2}{G}{W}{W}", "Creature — Angel", """
        Flying, hexproof
        Spells and abilities your opponents control can't cause you to sacrifice permanents.
    """.trimIndent(), "5/5")
    private val ascension = Cards.invent("Primal Ascension", "{1}{G}{G}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice a creature.
        Search your library for a creature card with mana value X or less, where X is 2 plus the sacrificed creature's mana value. Put that card onto the battlefield, then shuffle. Exile Primal Ascension.
    """.trimIndent(), null)
    private val instigator = Cards.invent("Bloodline Instigator", "{R}", "Creature — Vampire Berserker",
        "Each Vampire creature card you own that isn't on the battlefield has madness. The madness cost is equal to its mana cost. (If you discard a card with madness, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)",
        "2/1")
    private val pyre = Cards.invent("Pyre of Reprisal", "{2}{R}", "Enchantment",
        "Whenever you cast a spell from your graveyard, this enchantment deals 2 damage to any target.", null)
    private val patriarch = Cards.invent("Sanguine Patriarch", "{3}{R}{W}{B}", "Legendary Creature — Vampire Knight", """
        Eminence — Whenever you cast another Vampire spell, if Sanguine Patriarch is in the command zone or on the battlefield, create a 1/1 black Vampire creature token.
        First strike, haste
        Whenever Sanguine Patriarch attacks, put a +1/+1 counter on each Vampire you control.
    """.trimIndent(), "4/4")
    private val wyrm = Cards.invent("Glasswing Wyrm", "{3}{R}{R}", "Creature — Dragon", """
        Flying
        Whenever a player casts an instant or sorcery spell that targets only this creature, that player copies that spell for each other creature they control that the spell could target. Each copy targets a different one of those creatures.
    """.trimIndent(), "4/5")

    private val ghoul = Cards.invent("Restless Ghoul", "{4}{B}", "Creature — Zombie", "", "4/4")
    private val elk = Cards.invent("Fen Elk", "{1}{G}", "Creature — Elk", "", "2/2")
    private val shambler = Cards.invent("Marsh Shambler", "{2}{B}", "Creature — Horror", "", "2/3")
    private val cub = Cards.invent("Thicket Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val stalker = Cards.invent("Grove Stalker", "{3}{G}", "Creature — Beast", "", "4/4")
    private val grovewarden = Cards.invent("Ancient Grovewarden", "{4}{G}", "Creature — Treefolk", "", "5/5")
    private val squire = Cards.invent("Dusk Squire", "{1}{R}", "Creature — Vampire Knight", "", "2/2")
    private val ripper = Cards.invent("Cellar Ripper", "{1}{R}", "Creature — Horror", "", "2/2")
    private val hound = Cards.invent("Bog Hound", "{R}", "Creature — Dog", "", "1/1")
    private val brute = Cards.invent("Hollow Brute", "{2}{G}", "Creature — Beast", "", "3/3")
    private val altar = Cards.invent("Scrap Altar", "{1}", "Artifact", "Sacrifice a permanent: You gain 1 life.", null)
    private val outlet = Cards.invent("Grim Decree", "{B}", "Sorcery", "Target player sacrifices a creature of their choice.", null)
    private val recall = Cards.invent("Ember Recall", "{R}", "Sorcery", """
        Ember Recall deals 1 damage to any target.
        Flashback {R}
    """.trimIndent(), null)
    private val jolt = Cards.invent("Spiteful Jolt", "{R}", "Instant", "Spiteful Jolt deals 2 damage to target creature.", null)
    private val purge = Cards.invent("Ashen Purge", "{1}", "Artifact", "Discard a card: You gain 1 life.", null)

    private fun Scenario.count(name: String) = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == name }

    // CR 118.9: the Zombie creature spell may be cast for {0}; the Elk, not a Zombie, has no such way
    @Test
    fun tempestCastsZombieCreatureSpellsForNothing() = scenario {
        alice.battlefield(tempest)
        alice.hand(ghoul, elk)
        assertNull(grantedWay(elk))
        val way = grantedWay(ghoul)
        assertNotNull(way)
        alice.casts(ghoul, way = way)
        resolveAll()
        expect { alice.battlefield has ghoul }
    }

    // CR 607.2a: the card its second ability exiled comes back when it is put into the graveyard, under its owner's control (CR 110.2a)
    @Test
    fun reliquaryReturnsTheExiledCardToItsOwner() = scenario {
        alice.battlefield(reliquary, altar, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        bob.battlefield(brute)
        alice.activates(reliquary, ability = 1, target = brute)
        resolveAll()
        expect { bob.exile has brute }
        alice.activates(altar, choosing = listOf(reliquary))
        resolveAll()
        expect {
            bob.battlefield has brute
            brute.controller shouldBe bob.id
        }
    }

    // CR 101.4: Alice, then Bob, then Carol decide for their creatures; CR 119.4: Bob and Carol pay 2 life, Alice doesn't and sacrifices hers
    @Test
    fun tideAsksEachControllerInTurnOrder() = scenario(players = 3) {
        val carol = players[2]
        alice.battlefield(shambler, "Swamp", "Swamp", "Swamp")
        alice.hand(tide)
        bob.battlefield(cub)
        carol.battlefield(stalker)
        alice.casts(tide, x = 2)
        resolveAll()
        assertEquals(alice.id, decision<Decision.May>().player)
        answer(Answer.YesNo(false))
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        assertEquals(carol.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.graveyard has shambler
            bob.battlefield has cub
            carol.battlefield has stalker
            alice.life shouldBe 20
            bob.life shouldBe 18
            carol.life shouldBe 18
        }
    }

    // CR 700.3: Alice puts the Bear in one pile; Bob chooses the other pile and sacrifices everything in it, his land too
    @Test
    fun edictmistressSacrificesThePileItsTargetChooses() = scenario {
        alice.battlefield(edictmistress, counters = listOf(CounterKind.Loyalty to 6))
        bob.battlefield(cub, grovewarden, "Forest")
        alice.activates(edictmistress, ability = 2, target = "bob")
        resolveAll()
        assertEquals(alice.id, decision<Decision.ChooseObjects>().player)
        alice.chooses(cub)
        assertEquals(bob.id, decision<Decision.ChooseOption>().player)
        answer(Answer.Option("second"))
        resolveAll()
        expect {
            bob.battlefield has cub
            bob.graveyard has grovewarden
            bob.graveyard has "Forest"
        }
    }

    // CR 701.21a: Bob's spell can't make Alice sacrifice; her own altar still can
    @Test
    fun selvalaStopsOpponentsFromMakingYouSacrifice() = scenario {
        alice.battlefield(selvala, cub, altar)
        bob.battlefield("Swamp")
        bob.hand(outlet)
        turn(number = 2, active = bob)
        bob.casts(outlet, target = "alice")
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(cub)
        resolveAll()
        expect {
            alice.battlefield has cub
            alice.battlefield has selvala
            bob.graveyard has outlet
        }
        bob.passes()
        alice.activates(altar, choosing = listOf(cub))
        resolveAll()
        expect { alice.graveyard has cub }
    }

    // CR 608.2h: the sacrificed Bear's mana value 2, plus 2: a 4 can be found, the 5 can't; then the spell exiles itself
    @Test
    fun ascensionFindsACreatureUpToTwoMoreThanTheSacrificed() = scenario {
        alice.battlefield(cub, "Forest", "Forest", "Forest")
        alice.hand(ascension)
        alice.library(stalker, grovewarden)
        alice.casts(ascension, choosing = listOf(cub))
        resolveAll()
        val options = decision<Decision.ChooseObjects>().options
        assertTrue(card(stalker) in options)
        assertFalse(card(grovewarden) in options)
        alice.chooses(stalker)
        resolveAll()
        expect {
            alice.battlefield has stalker
            alice.graveyard has cub
            alice.exile has ascension
        }
    }

    // CR 702.35a: the Vampire card discarded has madness and is cast for its mana cost; the Horror has none and stays in the graveyard
    @Test
    fun instigatorGivesVampireCardsInHandMadness() = scenario {
        alice.battlefield(instigator, purge, "Mountain", "Mountain")
        alice.hand(squire, ripper)
        alice.activates(purge, choosing = listOf(squire))
        playOut()
        expect { alice.battlefield has squire }
        alice.activates(purge, choosing = listOf(ripper))
        playOut()
        expect { alice.graveyard has ripper }
    }

    // CR 601.2a, 702.34a: cast from the hand, nothing; cast again by flashback from the graveyard, the enchantment deals 2 more
    @Test
    fun pyreTriggersOnASpellCastFromTheGraveyard() = scenario {
        alice.battlefield(pyre, "Mountain", "Mountain")
        alice.hand(recall)
        alice.casts(recall, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 19 }
        alice.casts(recall, target = "bob")
        playOut("bob")
        expect {
            bob.life shouldBe 16
            alice.exile has recall
        }
    }

    // CR 113.6b, 603.4: another Vampire spell makes a Vampire token; a non-Vampire doesn't
    @Test
    fun patriarchMakesATokenForAnotherVampireSpell() = scenario {
        alice.battlefield(patriarch, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(squire, ripper)
        alice.casts(squire)
        resolveAll()
        expect { count("Vampire Token") shouldBe 1 }
        alice.casts(ripper)
        resolveAll()
        expect { count("Vampire Token") shouldBe 1 }
    }

    // CR 707.10d: Bob's spell at the Wyrm is copied for each other creature Bob controls: his Hound dies, his Beast is dealt 2; Alice's creatures
    // other than the Wyrm aren't touched. Bob puts the copies on the Stack in the order he chooses (CR 707.10d, 405.3): the Beast's first, so the
    // Hound's is on top
    @Test
    fun wyrmCopiesTheSpellForEachOtherCreatureOfItsCaster() = scenario {
        alice.battlefield(wyrm, cub)
        bob.battlefield(hound, brute, "Mountain")
        bob.hand(jolt)
        turn(number = 2, active = bob)
        bob.casts(jolt, target = wyrm)
        resolveAll()
        val order = decision<Decision.ChooseObjects>()
        assertEquals(bob.id, order.player)
        assertEquals(setOf(card(hound), card(brute)), order.options.toSet())
        answer(Answer.Objects(listOf(card(brute), card(hound))))
        assertEquals(listOf(listOf(arcanum.engine.state.TargetRef.Object(card(brute))), listOf(arcanum.engine.state.TargetRef.Object(card(hound)))),
            state.stack.takeLast(2).map { item -> state.stackItems[item]!!.targets.values.single() })
        resolveAll()
        expect {
            bob.graveyard has hound
            bob.battlefield has brute
            brute.view.obj?.damage shouldBe 2
            wyrm.view.obj?.damage shouldBe 2
            alice.battlefield has cub
            cub.view.obj?.damage shouldBe 0
        }
    }
}
