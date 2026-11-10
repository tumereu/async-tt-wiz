package arcanum.engine.sets.inr

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** INR chunk 04: Coven's "three or more creatures with different powers", a spell cast trigger that picks one card of each kind from a
 * revealed hand, and Auras: "can't transform", a granted triggered ability, "-X/-0", and an Aura returning from the graveyard attached.
 * Invented names carry the oracle text of the real cards. */
class InrCovenEmergeAurasScenariosTest {
    private val contest = Cards.invent("Contest of Fangs", "{1}{G}", "Instant", """
        Coven — Choose target creature you control and target creature you don't control. If you control three or more creatures with different powers, put a +1/+1 counter on the chosen creature you control. Then the chosen creatures fight each other. (Each deals damage equal to its power to the other.)
    """)
    private val mindbender = Cards.invent("Vessel Mindbender", "{8}", "Creature — Eldrazi Insect", """
        Emerge {5}{B}{B} (You may cast this spell by sacrificing a creature and paying the emerge cost reduced by that creature's mana value.)
        When you cast this spell, target opponent reveals their hand. You choose from it a nonland card with mana value 3 or less and a card with mana value 4 or greater. That player discards those cards.
    """, "5/5")
    private val moonlight = Cards.invent("Bound by Moonlight", "{2}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature can't attack, block, or transform.
        Sacrifice another permanent: Attach this Aura to target creature. Activate only as a sorcery and only once each turn.
    """)
    private val hearthCall = Cards.invent("Hearth Call", "{2}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature has vigilance and "At the beginning of your end step, create a 1/1 white Human creature token."
    """)
    private val decay = Cards.invent("Spontaneous Decay", "{U}", "Enchantment — Aura", """
        Flash
        Enchant creature
        Enchanted creature gets -X/-0, where X is the number of cards in your graveyard.
    """)
    private val blessing = Cards.invent("Gryff's Blessing", "{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+0 and has flying.
        {3}{W}: Return this card from your graveyard to the battlefield attached to target creature. Activate only as a sorcery.
    """)
    private val shifter = Cards.inventFaces("Transform",
        face("Idle Shifter", "{1}{W}", "Creature — Human Werewolf", "{1}: Transform this creature.", "2/2", null, null),
        face("Roused Shifter", null, "Creature — Werewolf", "", "4/4", null, "W"))
    private val watcher = Cards.inventFaces("Transform",
        face("Dusk Watcher", "{1}{G}", "Creature — Human Werewolf", "Daybound (If a player casts no spells during their own turn, it becomes night next turn.)", "2/2", null, null),
        face("Dusk Howler", null, "Creature — Werewolf", "Nightbound (If a player casts at least two spells during their own turn, it becomes day next turn.)", "4/4", null, null))
    private val small = Cards.invent("Small Fry", "{1}", "Creature — Fish", "", "1/1")
    private val mid = Cards.invent("Mid Fry", "{2}", "Creature — Fish", "", "2/2")
    private val big = Cards.invent("Big Fry", "{3}", "Creature — Fish", "", "3/3")
    private val foe = Cards.invent("Foe Fry", "{2}", "Creature — Fish", "", "2/4")
    private val cheapA = Cards.invent("Cheap Spell A", "{2}", "Sorcery", "")
    private val cheapB = Cards.invent("Cheap Spell B", "{3}", "Sorcery", "")
    private val dear = Cards.invent("Dear Spell", "{5}", "Sorcery", "")
    private val dearToo = Cards.invent("Dear Spell Too", "{4}", "Sorcery", "")

    private fun Scenario.face(name: String): Int? = state.obj(card(name))?.face
    private fun Scenario.count(name: String) = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == name }

    // CR 207.2c (Coven), 701.14a: three different powers (1, 2, 3) put the counter on before the fight, so Mid Fry (3/3) deals 3 to Foe Fry (2/4)
    @Test
    fun covenCounterIsPutBeforeTheFight() = scenario {
        alice.battlefield("Forest", "Forest", small, mid, big)
        bob.battlefield(foe)
        alice.hand(contest)
        alice.casts(contest, targets = listOf(mid, foe))
        resolveAll()
        expect {
            card(mid).counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            foe.view.obj?.damage shouldBe 3
            mid.view.obj?.damage shouldBe 2
        }
    }

    // CR 207.2c: two creatures with the same power are not three different powers, so no counter
    @Test
    fun covenNeedsThreeDifferentPowers() = scenario {
        alice.battlefield("Forest", "Forest", small, mid, mid)
        bob.battlefield(foe)
        alice.hand(contest)
        alice.casts(contest, targets = listOf(mid, foe))
        resolveAll()
        expect { card(mid).counters(CounterKind.PowerToughness(1, 1)) shouldBe 0 }
    }

    // CR 603.3, 701.9, 800.4: with three players only the targeted opponent shows a hand; one nonland card of mana value 3 or less and one
    // card of mana value 4 or greater are taken (a land has mana value 0, so it is neither), the third player keeps theirs
    @Test
    fun mindbenderTakesOneCheapAndOneDearCard() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(mindbender)
        bob.hiddenHand(cheapA, cheapB, dear, dearToo, "Forest")
        players[2].hiddenHand(cheapA)
        alice.casts(mindbender, target = "bob")
        allPass()
        assertEquals(setOf(card(cheapA, owner = bob), card(cheapB, owner = bob)), decision<Decision.ChooseObjects>().options.toSet())
        alice.chooses(cheapB)
        assertEquals(setOf(card(dear, owner = bob), card(dearToo, owner = bob)), decision<Decision.ChooseObjects>().options.toSet())
        alice.chooses(dearToo)
        expect {
            bob.graveyard has cheapB
            bob.graveyard has dearToo
            bob.hand.size shouldBe 3
            players[2].hand.size shouldBe 1
        }
    }

    // CR 701.27, 701.28f: the creature can't transform by its own ability (control: without the Aura it does)
    @Test
    fun withoutTheAuraTheShifterTransforms() = scenario {
        alice.battlefield("Plains", shifter)
        alice.activates(shifter, ability = 0)
        resolveAll()
        assertEquals(1, face("Idle Shifter"))
    }

    @Test
    fun enchantedCreatureCantTransformAttackOrBlock() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", shifter)
        alice.hand(moonlight)
        alice.casts(moonlight, target = shifter)
        allPass()
        alice.activates(shifter, ability = 0)
        resolveAll()
        assertEquals(0, face("Idle Shifter"))
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<Throwable> { alice.attacks(shifter) }
    }

    // CR 701.27, 702.145c: night would transform a daybound creature, but "can't transform" wins (CR 101.2)
    @Test
    fun nightDoesNotTransformTheEnchantedWatcher() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", watcher)
        alice.hand(moonlight)
        players.forEach { it.library("Forest", "Forest", "Forest", "Forest") }
        alice.casts(moonlight, target = watcher)
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(0, face("Dusk Watcher"))
    }

    // CR 701.3b (attach), 602.5d (sorcery speed), once each turn: sacrificing another permanent moves the Aura to another creature
    @Test
    fun moonsilverMovesToAnotherCreatureOnlyOnceEachTurn() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Swamp", small, mid)
        bob.battlefield(foe)
        alice.hand(moonlight)
        alice.casts(moonlight, target = small)
        allPass()
        alice.activates(moonlight, ability = 0, target = foe, choosing = listOf("Swamp"))
        resolveAll()
        assertEquals(arcanum.engine.state.TargetRef.Object(card(foe)), state.objOrThrow(card(moonlight)).attachedTo)
        assertFailsWith<Throwable> { alice.activates(moonlight, ability = 0, target = mid, choosing = listOf("Plains")) }
    }

    // CR 113.10, 603.1: the granted trigger is the creature's, at its controller's end step; vigilance is granted too
    @Test
    fun hearthCallGivesATokenAtTheEndStep() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", mid)
        alice.hand(hearthCall)
        alice.casts(hearthCall, target = mid)
        allPass()
        assertEquals(0, count("Human Token"))
        advanceTo(TurnStep.End)
        resolveAll()
        assertEquals(1, count("Human Token"))
        expect { mid hasKeyword Keyword.Vigilance.kind }
    }

    // CR 208.1, 604.3: the lost power follows the number of cards in its owner's graveyard (the Aura's controller's: "your")
    @Test
    fun decayTracksTheGraveyard() = scenario {
        alice.battlefield("Island", big)
        alice.hand(decay, dear)
        alice.graveyard(cheapA, cheapB)
        alice.casts(decay, target = big)
        allPass()
        expect { big.power shouldBe 1 }
        move(dear, Zone.Graveyard)
        expect { big.power shouldBe 0 }
    }

    // CR 400.7 (a new object), 303.4f (enters attached to the chosen creature), 602.5d: from the graveyard onto another creature
    @Test
    fun blessingReturnsFromTheGraveyardAttached() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", mid)
        alice.graveyard(blessing)
        alice.activates(blessing, ability = 0, target = mid)
        resolveAll()
        expect {
            card(blessing).zone shouldBe Zone.Battlefield
            mid.power shouldBe 3
        }
        expect { mid hasKeyword Keyword.Flying.kind }
    }
}
