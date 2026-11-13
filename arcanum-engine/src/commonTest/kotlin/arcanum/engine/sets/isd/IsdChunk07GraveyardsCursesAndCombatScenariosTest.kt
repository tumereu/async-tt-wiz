package arcanum.engine.sets.isd

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.dft.payYes
import arcanum.engine.sets.rtr.resolveAll
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** ISD chunk 07: the card an additional cost exiled read as the spell resolves, Curses attached to a player (destroyed, or put onto the
 * battlefield attached to one), "that player" after a counter, an attacking token exiled at end of combat, a graveyard card of the target
 * player's choice, a library's bottom card, cards returned at random, and protection from non-Human creatures. Invented names carry the
 * oracle text of the real cards. */
class IsdChunk07GraveyardsCursesAndCombatScenariosTest {
    private val thrust = Cards.invent("Cadaver Thrust", "{2}{B}", "Instant", """
        As an additional cost to cast this spell, exile a creature card from your graveyard.
        Cadaver Thrust deals damage equal to the exiled card's power to target creature.
    """)
    private val sphere = Cards.invent("Hexward Sphere", "{4}", "Artifact", """
        When this artifact enters, destroy all Curses attached to you.
        You have hexproof. (You can't be the target of spells or abilities your opponents control, including Aura spells.)
    """)
    private val fancy = Cards.invent("Dreadful Fancy", "{2}{U}", "Instant", "Counter target spell unless its controller pays {1}. That player discards a card.")
    private val shade = Cards.invent("Shade of Saint Voren", "{1}{W}{U}", "Legendary Creature — Spirit Cleric", """
        Hexproof (This creature can't be the target of spells or abilities your opponents control.)
        Whenever Shade of Saint Voren attacks, create a 4/4 white Angel creature token with flying that's tapped and attacking. Exile that token at end of combat.
    """, "2/2")
    private val spade = Cards.invent("Tomb Spade", "{2}", "Artifact", "{2}, {T}: Target player exiles a card from their graveyard. If it's a creature card, you gain 2 life.")
    private val shield = Cards.invent("Shield from Wickedness", "{1}{W}", "Instant", "Creatures you control gain protection from non-Human creatures until end of turn.")
    private val hatch = Cards.invent("Crypt Hatch", "{2}", "Artifact",
        "{3}, {T}: Target player puts the bottom card of their library into their graveyard. If it's a creature card, you create a 2/2 black Zombie creature token.")
    private val askTheStars = Cards.invent("Ask the Stars", "{3}{G}", "Sorcery", "Return two cards at random from your graveyard to your hand.")
    private val behemoth = Cards.invent("Mossgrave Behemoth", "{4}{G}{G}{G}", "Creature — Insect", """
        Trample
        When this creature dies, exile it, then return two creature cards at random from your graveyard to the battlefield.
    """, "8/8")
    private val hexer = Cards.invent("Sourheart Hexer", "{4}{B}", "Creature — Human Shaman", """
        Deathtouch
        When this creature dies, you may search your library for a Curse card, put it onto the battlefield attached to target player, then shuffle.
    """, "1/2")
    private val wick = Cards.invent("Curse of the Guttering Wick", "{B}", "Enchantment — Aura Curse", """
        Enchant player
        At the beginning of enchanted player's upkeep, that player loses 1 life.
    """)
    private val sourMilk = Cards.invent("Curse of Sour Milk", "{B}", "Enchantment — Aura Curse", """
        Enchant player
        At the beginning of enchanted player's upkeep, that player loses 1 life.
    """)
    private val verdict = Cards.invent("Grave Verdict", "{1}{B}", "Instant", "Destroy target creature.")
    private val brute = Cards.invent("Barrow Brute", "{3}{G}", "Creature — Beast", "", "4/4")
    private val pup = Cards.invent("Ditch Pup", "{G}", "Creature — Dog", "", "1/1")
    private val ox = Cards.invent("Mire Ox", "{2}{G}", "Creature — Ox", "", "3/4")
    private val villager = Cards.invent("Lane Villager", "{W}", "Creature — Human Peasant", "", "1/1")

    // CR 601.2h, 608.2h: the card the cost exiled; its power (4) kills the 3/4, where the 1/1's wouldn't
    @Test
    fun cadaverThrustDealsTheExiledCardsPower() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(thrust)
        alice.graveyard(brute, pup)
        bob.battlefield(ox)
        alice.casts(thrust, target = ox, choosing = listOf(brute))
        allPass()
        expect {
            alice.exile has brute
            alice.graveyard has pup
            bob.graveyard has ox
        }
    }

    // CR 303.4e: only the Curses enchanting you are destroyed; one on another player stays, whoever controls them
    @Test
    fun hexwardSphereDestroysOnlyTheCursesAttachedToYou() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(sphere)
        bob.battlefield(wick, sourMilk)
        attach(wick, "alice")
        attach(sourMilk, "carol")
        alice.casts(sphere)
        allPass()
        resolveAll()
        expect {
            bob.graveyard has wick
            bob.battlefield has sourMilk
        }
    }

    // CR 118.12, 608.2c: the spell's controller can't pay, so it is countered; that player discards
    @Test
    fun dreadfulFancyCountersAndItsControllerDiscards() = scenario {
        turn(number = 2, active = bob)
        bob.battlefield("Forest", "Forest", "Forest")
        bob.hand(ox, "Island")
        alice.battlefield("Island", "Island", "Island")
        alice.hand(fancy)
        bob.casts(ox)
        bob.passes()
        alice.casts(fancy, target = ox)
        allPass()
        expect {
            bob.graveyard has ox
            bob.graveyard has "Island"
        }
    }

    // CR 118.12: paying keeps the spell; "That player discards a card" happens either way (CR 608.2c)
    @Test
    fun dreadfulFancyStillMakesThemDiscardWhenTheyPay() = scenario {
        turn(number = 2, active = bob)
        bob.battlefield("Forest", "Forest", "Forest", "Forest")
        bob.hand(ox, "Island")
        alice.battlefield("Island", "Island", "Island")
        alice.hand(fancy)
        bob.casts(ox)
        bob.passes()
        alice.casts(fancy, target = ox)
        allPass()
        assertEquals(bob.id, (pending as Decision.May).player)
        payYes()
        allPass()
        expect {
            bob.battlefield has ox
            bob.graveyard has "Island"
        }
    }

    // CR 508.4: the Angel attacks without being declared and deals its 4; CR 603.7: exiled at end of combat (a token then ceases to exist,
    // CR 704.5d)
    @Test
    fun shadeOfSaintVorensAngelIsExiledAtEndOfCombat() = scenario {
        alice.battlefield(shade)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(shade)
        resolveAll()
        expect { alice.battlefield has "Angel Token" }
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            bob.life shouldBe 14
            alice.battlefield lacks "Angel Token"
            alice.exile lacks "Angel Token"
        }
    }

    // CR 608.2d: the target player chooses the card; CR 400.7: a creature card exiled this way gains you 2 life
    @Test
    fun tombSpadeTheTargetPlayerChoosesACreatureCard() = scenario {
        alice.battlefield(spade, "Plains", "Plains")
        bob.graveyard(ox, "Island")
        alice.activates(spade, target = "bob")
        allPass()
        assertEquals(bob.id, (pending as Decision.ChooseObjects).player)
        bob.chooses(ox)
        expect {
            bob.exile has ox
            bob.graveyard has "Island"
            alice.life shouldBe 22
        }
    }

    // CR 608.2c: a land card exiled this way gains nothing
    @Test
    fun tombSpadeANoncreatureCardGainsNothing() = scenario {
        alice.battlefield(spade, "Plains", "Plains")
        bob.graveyard(ox, "Island")
        alice.activates(spade, target = "bob")
        allPass()
        bob.chooses("Island")
        expect {
            bob.exile has "Island"
            alice.life shouldBe 20
        }
    }

    // CR 702.16b: a non-Human creature can't block a creature with protection from non-Human creatures; a Human can
    @Test
    fun shieldFromWickednessStopsOnlyNonHumanBlockers() = scenario {
        alice.battlefield(pup, "Plains", "Plains")
        alice.hand(shield)
        bob.battlefield(ox, villager)
        alice.casts(shield)
        allPass()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(pup)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ox to pup) }
        bob.blocks(villager to pup)
        advanceTo(TurnStep.EndOfCombat)
        expect {
            alice.graveyard has pup
            bob.graveyard has villager
        }
    }

    // CR 701.17a: the bottom card, not the top (not a mill); a creature card makes a Zombie
    @Test
    fun cryptHatchPutsTheBottomCardIntoTheGraveyard() = scenario {
        alice.battlefield(hatch, "Plains", "Plains", "Plains")
        bob.library("Island", ox)
        alice.activates(hatch, target = "bob")
        allPass()
        expect {
            bob.graveyard has ox
            bob.library has "Island"
            alice.battlefield has "Zombie Token"
        }
    }

    // CR 608.2c: two cards picked at random as it resolves (no player chooses)
    @Test
    fun askTheStarsReturnsTwoCardsAtRandom() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(askTheStars)
        alice.graveyard(ox, pup, "Island")
        random({ _ -> Answer.RandomObjects(listOf(card(ox), card("Island"))) })
        alice.casts(askTheStars)
        allPass()
        expect {
            alice.hand has ox
            alice.hand has "Island"
            alice.graveyard has pup
        }
    }

    // CR 400.7: exiled first, so it isn't among the cards returned; two creature cards at random onto the battlefield
    @Test
    fun mossgraveBehemothExilesItselfAndReturnsTwoCreatures() = scenario {
        alice.battlefield(behemoth, "Swamp", "Swamp")
        alice.hand(verdict)
        alice.graveyard(ox, pup, "Forest")
        random({ _ -> Answer.RandomObjects(listOf(card(ox), card(pup))) })
        alice.casts(verdict, target = behemoth)
        allPass()
        resolveAll()
        expect {
            alice.exile has behemoth
            alice.battlefield has ox
            alice.battlefield has pup
            alice.graveyard has "Forest"
        }
    }

    // CR 303.4f: the Curse enters attached to the target player without being cast; CR 701.23a, 701.24a (search, then shuffle)
    @Test
    fun sourheartHexerPutsACurseOntoTheTargetPlayer() = scenario(players = 3) {
        alice.battlefield(hexer, "Swamp", "Swamp")
        alice.hand(verdict)
        alice.library(wick, "Swamp")
        alice.casts(verdict, target = hexer)
        allPass()
        resolveAll()
        targets("carol")
        allPass()
        answer(Answer.YesNo(true))
        alice.chooses(wick)
        resolveAll()
        expect { alice.battlefield has wick }
        assertEquals(TargetRef.Player(carol.id), state.obj(card(wick))!!.attachedTo)
    }
}
