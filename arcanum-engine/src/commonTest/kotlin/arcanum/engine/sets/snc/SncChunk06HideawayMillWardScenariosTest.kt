package arcanum.engine.sets.snc

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dft.choose
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.sets.fin.yes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Streets of New Capenna chunk 06 in play: a hideaway card played from a later trigger, the first draw of your own draw step, a prevention
 * that mills twice the damage, distinct mana values in a graveyard, "any opponent may tap" with three players, counters of any kind among
 * creatures, and a token replacement whose controller chooses the token. */
class SncChunk06HideawayMillWardScenariosTest {
    private val rigging = Cards.invent("Snc06 Bout Fixing", "{2}{G}", "Enchantment", """
        Hideaway 5 (When this enchantment enters, look at the top five cards of your library, exile one face down, then put the rest on the bottom in a random order.)
        At the beginning of combat on your turn, put a +1/+1 counter on target creature you control. Then if you control a creature with power 7 or greater, you may play the exiled card without paying its mana cost.
    """)
    private val wiretap = Cards.invent("Snc06 Line Tapping", "{4}{U}", "Enchantment", """
        Hideaway 5 (When this enchantment enters, look at the top five cards of your library, exile one face down, then put the rest on the bottom in a random order.)
        Whenever you draw your first card during each of your draw steps, draw a card. Then if you have nine or more cards in hand, you may play the exiled card without paying its mana cost.
    """)
    private val rousing = Cards.invent("Snc06 Mob Stirring", "{4}{W}", "Enchantment", """
        Hideaway 5 (When this enchantment enters, look at the top five cards of your library, exile one face down, then put the rest on the bottom in a random order.)
        Whenever you attack with one or more creatures, create that many 1/1 green and white Citizen creature tokens. Then if you control ten or more creatures, you may play the exiled card without paying its mana cost.
    """)
    private val angel = Cards.invent("Snc06 Angel of Torment's Toll", "{3}{B}{B}", "Creature — Nightmare Angel", """
        Flying
        If damage would be dealt to you, prevent that damage and mill twice that many cards.
    """, "5/3")
    private val stabber = Cards.invent("Snc06 Aven Spinestabber", "{U}{B}", "Creature — Bird Assassin", """
        Flying
        As long as there are five or more mana values among cards in your graveyard, this creature gets +2/+2 and has deathtouch.
        When this creature dies, mill two cards, then draw a card.
    """, "1/1")
    private val kraken = Cards.invent("Snc06 Cistern Kraken", "{2}{U}{U}", "Creature — Kraken", """
        Trample, ward {2}
        At the beginning of each combat, if this creature is untapped, any opponent may tap an untapped creature they control. If they do, tap this creature and create a 1/1 blue Fish creature token with "This token can't be blocked."
    """, "6/6")
    private val bruiser = Cards.invent("Snc06 Alleyway Bruiser", "{1}{U}", "Creature — Octopus Rogue", """
        Defender
        As long as there are two or more counters among creatures you control, this creature can attack as though it didn't have defender.
    """, "3/3")
    private val jinnie = Cards.invent("Snc06 Jennet Fay, Jetmir's Aide", "{R/G}{G}{G/W}", "Legendary Creature — Elf Druid",
        "If you would create one or more tokens, you may instead create that many 2/2 green Cat creature tokens with haste or that many 3/1 green Dog creature tokens with vigilance.",
        "3/3")

    private val giant = Cards.invent("Snc06 Hulking Enforcer", "{4}{G}{G}", "Creature — Giant", "", "6/6")
    private val brute = Cards.invent("Snc06 Dockside Brute", "{3}{G}", "Creature — Ogre", "", "5/5")
    private val hidden = Cards.invent("Snc06 Stashed Rhino", "{5}{G}", "Creature — Rhino", "", "4/4")
    private val glimpse = Cards.invent("Snc06 Quick Tip", "{U}", "Instant", "Draw a card.")
    private val jolt = Cards.invent("Snc06 Alley Jolt", "{R}", "Instant", "Snc06 Alley Jolt deals 3 damage to any target.")
    private val two = Cards.invent("Snc06 Two-Bit Thug", "{1}{B}", "Creature — Human Rogue", "", "2/2")
    private val three = Cards.invent("Snc06 Three-Card Hustler", "{2}{B}", "Creature — Human Rogue", "", "3/3")
    private val four = Cards.invent("Snc06 Four-Corner Boss", "{3}{B}", "Creature — Human Rogue", "", "4/4")
    private val stash = Cards.invent("Snc06 Loot Stash", "{1}", "Sorcery", "Create two Treasure tokens.")

    /** Casts the hideaway enchantment, hides [card] from the top five cards of the library, then begins combat with [creature]. */
    private fun hideawayRigging(card: String, creature: String) = scenario {
        alice.battlefield(creature, "Forest", "Forest", "Forest")
        alice.hand(rigging)
        alice.library(card, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.casts(rigging)
        resolveAll()
        choose(card)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        if (pending is Decision.May) yes()
        expect {
            creature.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            hidden.zone shouldBe if (creature == giant) Zone.Battlefield else Zone.Exile
        }
    }

    // CR 702.75a (the hidden card played free from a later ability), 603.4 ("Then if" checked as the trigger resolves: 6/6 plus a counter is 7)
    @Test
    fun fightRiggingPlaysTheHiddenCardOnceACreatureHasPowerSeven() = hideawayRigging(hidden, giant)

    // CR 608.2b: a 5/5 with the counter is 6/6, under 7: the hidden card stays exiled
    @Test
    fun fightRiggingKeepsTheCardHiddenBelowPowerSeven() = hideawayRigging(hidden, brute)

    // CR 504.1, 603.2: the draw step's draw is your first card during your draw step: it triggers once
    @Test
    fun wiretappingDrawsAfterYourDrawStepDraw() = scenario {
        turn(number = 3, active = alice, step = TurnStep.Upkeep)
        alice.battlefield(wiretap)
        alice.library("Island", "Island", "Island")
        advanceTo(TurnStep.Draw)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 504.1: an opponent's draw step is not one of your draw steps: your first draw there doesn't trigger it (with three players)
    @Test
    fun wiretappingIgnoresAnOpponentsDrawStep() = scenario(players = 3) {
        turn(number = 2, active = bob, step = TurnStep.Upkeep)
        alice.battlefield(wiretap, "Island")
        alice.hand(glimpse)
        alice.library("Island", "Island", "Island")
        bob.library("Island", "Island")
        advanceTo(TurnStep.Draw)
        bob.passes()
        carol.passes()
        alice.casts(glimpse)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 615.5: the damage is prevented and its controller mills twice that many (3 damage: six cards)
    @Test
    fun angelPreventsDamageAndMillsTwiceThatMany() = scenario {
        alice.battlefield(angel, "Mountain")
        alice.hand(jolt)
        alice.library(*Array(10) { "Island" })
        alice.casts(jolt, target = "alice")
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.library.size shouldBe 4
        }
    }

    // CR 202.3: mana values 0 (a land), 1, 2, 3 and a second 1 are four values; a fifth value (4) turns it on
    @Test
    fun heartstabberCountsDistinctManaValues() = scenario {
        alice.battlefield(stabber)
        alice.graveyard("Island", glimpse, jolt, two, three)
        alice.hand(four)
        expect {
            stabber.power shouldBe 1
            stabber lacksKeyword Keyword.Deathtouch.kind
        }
        move(four, Zone.Graveyard)
        expect {
            stabber.power shouldBe 3
            stabber.toughness shouldBe 3
            stabber hasKeyword Keyword.Deathtouch.kind
        }
    }

    // CR 101.4 (each opponent in turn order may, until one does), 118.3 (the opponent taps their own creature), 603.4
    @Test
    fun krakenAnyOpponentMayTapACreature() = scenario(players = 3) {
        alice.battlefield(kraken)
        bob.battlefield(two)
        carol.battlefield(three)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        assertEquals(bob.id, (pending as Decision.May).player)
        answer(Answer.YesNo(false))
        assertEquals(carol.id, (pending as Decision.May).player)
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            two.tapped shouldBe false
            three.tapped shouldBe true
            kraken.tapped shouldBe true
            alice.battlefield has "Fish Token"
        }
    }

    // CR 603.4: a tapped Kraken doesn't trigger at all
    @Test
    fun tappedKrakenDoesNotTrigger() = scenario(players = 3) {
        alice.battlefield(kraken, tapped = true)
        bob.battlefield(two)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            pending shouldBe null
            two.tapped shouldBe false
            alice.battlefield lacks "Fish Token"
        }
    }

    // CR 122.1: a +1/+1 counter and an oil counter on two creatures are two counters among creatures you control
    @Test
    fun bruiserAttacksWithTwoCountersOfAnyKind() = scenario {
        alice.battlefield(bruiser)
        alice.battlefield(two, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.battlefield(three, counters = listOf(CounterKind.Named("oil") to 1))
        bob.library("Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bruiser)
        assertTrue(state.combat!!.attackers.any { a -> a.id == card(bruiser) })
    }

    // CR 702.3b: one counter is not enough: defender stays
    @Test
    fun bruiserCantAttackWithOneCounter() = scenario {
        alice.battlefield(bruiser)
        alice.battlefield(two, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(bruiser) }
    }

    // CR 508.1, 603.2c: one trigger for the attack, "that many" the creatures attacking
    @Test
    fun rabbleRousingCreatesOneCitizenPerAttacker() = scenario {
        alice.battlefield(rousing, two, three, four)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(two, three)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Citizen Token" } shouldBe 2 }
    }

    // owner 2026-11-23: one question, "don't replace" among the results
    private fun jinnieMakes(choice: String, expected: String) = scenario {
        alice.battlefield(jinnie, "Plains")
        alice.hand(stash)
        alice.casts(stash)
        resolveAll()
        assertEquals(listOf("Keep the original tokens", "Cat Token", "Dog Token"), (pending as Decision.ChooseOption).options)
        answer(Answer.Option(choice))
        resolveAll()
        expect {
            alice.battlefield.names.count { it == expected } shouldBe 2
            alice.battlefield.names.count { it?.endsWith("Token") == true } shouldBe 2
        }
    }

    // CR 614.1a: two Treasures become two 2/2 Cats with haste
    @Test
    fun jinnieTurnsTokensIntoCats() = jinnieMakes("Cat Token", "Cat Token")

    // CR 614.1a: or two 3/1 Dogs with vigilance
    @Test
    fun jinnieTurnsTokensIntoDogs() = jinnieMakes("Dog Token", "Dog Token")

    // CR 614.1a ("you may"): declined in the same question, the Treasures are created
    @Test
    fun jinnieMayLeaveTheTokens() = jinnieMakes("Keep the original tokens", "Treasure Token")
}
