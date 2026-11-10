package arcanum.engine.sets.inr

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** INR chunk 07: a repeated "if you do", a draw replaced by a win, an attack restriction about any player's hand, a modal loss at exactly 13
 * life, counters for the creatures that died, countering every ability, copies with exceptions, a cost reduction by card types and an
 * extra turn after a controlled one, keyword sharing, a delayed trigger about chosen targets, and a clone with counters. Invented names carry
 * the oracle text of the real cards. */
class InrConditionsCopiesCountersScenariosTest {
    private val behemoth = Cards.invent("Tillage Behemoth", "{4}{G}{G}{G}", "Creature — Plant Beast", """
        Trample
        Tillage Behemoth's power and toughness are each equal to the number of lands you control.
        When this creature enters, you may put a land card from your hand onto the battlefield tapped. If you do, draw a card and repeat this process.
    """.trimIndent(), "*/*")
    private val lunatic = Cards.invent("Archive Lunatic", "{2}{U}", "Creature — Human Wizard",
        "If you would draw a card while your library has no cards in it, you win the game instead.", "2/2")
    private val hound = Cards.invent("Hound Automaton", "{2}", "Artifact Creature — Wolf Construct",
        "This creature can't attack or block unless a player has no cards in hand.", "5/5")
    private val dread = Cards.invent("Thirteenfold Dread", "{3}{B}", "Enchantment", """
        At the beginning of your upkeep, choose one —
        • Each player with exactly 13 life loses the game, then each player gains 1 life.
        • Each player with exactly 13 life loses the game, then each player loses 1 life.
    """.trimIndent(), null)
    private val ursine = Cards.invent("Carrion Ursine", "{2}{B}{G}", "Creature — Zombie Bear", """
        Trample
        This creature enters with a +1/+1 counter on it for each creature that died this turn.
    """.trimIndent(), "4/3")
    private val dismissal = Cards.invent("Abrupt Dismissal", "{2}{U}{U}", "Instant", "Exile all other spells and counter all abilities.", null)
    private val splitter = Cards.invent("Spirit Splitter", "{3}", "Artifact",
        "{5}, {T}, Sacrifice this artifact: Exile target creature card from your graveyard. Create a token that's a copy of that card, except it's 1/1, " +
            "it's a Spirit in addition to its other types, and it has flying. Create a black Zombie creature token with power equal to that card's power " +
            "and toughness equal to that card's toughness.", null)
    private val vorthak = Cards.invent("Vorthak, the Foretold End", "{13}", "Legendary Creature — Eldrazi", """
        This spell costs {1} less to cast for each card type among cards in your graveyard.
        When you cast this spell, you gain control of target opponent during that player's next turn. After that turn, that player takes an extra turn.
        Flying, trample, protection from instants
    """.trimIndent(), "13/13")
    private val odila = Cards.invent("Odila, Lunar Captain", "{3}{W}", "Legendary Creature — Human Soldier",
        "At the beginning of each combat, creatures you control gain first strike until end of turn if a creature you control has first strike. " +
            "The same is true for flying, deathtouch, double strike, haste, hexproof, indestructible, lifelink, menace, reach, skulk, trample, and vigilance.", "3/3")
    private val tamsin = Cards.invent("Tamsin, Field Scholar", "{1}{G}{W}{U}", "Legendary Planeswalker — Tamsin", """
        [+1]: Choose up to two target creatures. Until your next turn, whenever either of those creatures deals combat damage, you draw a card.
        [−2]: Tap up to two target nonland permanents. They don't untap during their controller's next untap step.
        [−7]: Draw three cards. You get an emblem with "You may cast spells from your hand without paying their mana costs."
    """.trimIndent(), null)
    private val mirrored = Cards.invent("Mirrored Self", "{X}{2}{G}{U}", "Creature — Shapeshifter", """
        This spell can't be countered.
        You may have this creature enter as a copy of any creature on the battlefield, except it enters with X additional +1/+1 counters on it.
    """.trimIndent(), "0/0")

    private val sentry = Cards.invent("Chapel Sentry", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val grunt = Cards.invent("Mossback Grunt", "{3}{G}", "Creature — Ogre", "", "4/3")
    private val courier = Cards.invent("Ghostly Courier", "{1}{U}", "Creature — Spirit", "Flying", "1/1")
    private val lurker = Cards.invent("Bramble Lurker", "{1}{G}", "Creature — Plant", "Deathtouch", "1/1")
    private val font = Cards.invent("Sacrificial Font", "{1}", "Artifact", "Sacrifice a creature: You gain 1 life.", null)
    private val ledger = Cards.invent("Dread Ledger", "{U}", "Instant", "Draw a card.", null)
    private val ledger2 = Cards.invent("Second Ledger", "{U}", "Instant", "Draw a card.", null)

    private fun Scenario.libraries() { players.forEach { it.library("Island", "Island", "Island", "Island", "Island", "Island") } }

    // CR 608.2c: the process repeats while a land is put; 305.4 (putting a land onto the battlefield isn't playing it)
    @Test
    fun behemothPutsLandsAndDrawsUntilYouStop() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(behemoth, "Mountain", "Mountain")
        alice.library("Plains", "Plains", "Plains")
        bob.library("Island")
        alice.casts(behemoth)
        resolveAll()
        alice.chooses("Mountain")
        alice.chooses("Mountain")
        alice.chooses()
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Mountain" } shouldBe 2
            "Mountain".tapped shouldBe true
            alice.hand.names shouldBe listOf("Plains", "Plains")
            behemoth.power shouldBe 9
        }
    }

    // CR 614.1a: the draw is replaced while the library is empty, and the player wins (CR 104.2b); with cards left it is an ordinary draw
    @Test
    fun lunaticWinsInsteadOfDrawingFromAnEmptyLibrary() = scenario {
        alice.battlefield(lunatic, "Island", "Island")
        alice.hand(ledger, ledger2)
        alice.library("Plains")
        bob.library("Island")
        alice.casts(ledger)
        resolveAll()
        expect { alice.hand has "Plains" }
        alice.casts(ledger2)
        resolveAll()
        assertTrue(state.player(bob.id).lost, "Bob loses when Alice wins")
        assertEquals(false, state.player(alice.id).lost)
    }

    // CR 508.1c: the restriction holds while no player's hand is empty; a third player's empty hand lifts it (CR 402)
    @Test
    fun houndAttacksOnlyWhenSomePlayerHasNoCards() = scenario(players = 3) {
        libraries()
        alice.battlefield(hound)
        alice.hand("Island"); bob.hand("Island"); players[2].hand("Island")
        turn(step = TurnStep.DeclareAttackers)
        assertFailsWith<Throwable> { alice.attacks(hound, defender = "bob") }
    }

    @Test
    fun houndAttacksWhenAThirdPlayerHasNoCards() = scenario(players = 3) {
        libraries()
        alice.battlefield(hound)
        alice.hand("Island"); bob.hand("Island")
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(hound, defender = "bob")
        expect { hound.tapped shouldBe true }
    }

    // CR 700.2 (the mode), 104.3e: each player at exactly 13 loses; then every remaining player gains 1 (CR 101.4, 800.4a)
    @Test
    fun dreadMakesThePlayerAtThirteenLose() = scenario(players = 3) {
        libraries()
        alice.battlefield(dread)
        bob.life = 13
        players[2].life = 14
        turn(step = TurnStep.PrecombatMain)
        advanceTo(TurnStep.Upkeep, turn = 4)
        answer(Answer.Modes(listOf(0)))
        resolveAll()
        assertTrue(state.player(bob.id).lost)
        expect {
            alice.life shouldBe 21
            players[2].life shouldBe 15
        }
    }

    // CR 614.1c, 700.4: two creatures died this turn before it entered
    @Test
    fun ursineEntersWithACounterForEachCreatureThatDied() = scenario {
        alice.battlefield(font, sentry, courier, "Swamp", "Swamp", "Forest", "Forest")
        alice.hand(ursine)
        alice.activates(font, choosing = listOf(sentry))
        resolveAll()
        alice.activates(font, choosing = listOf(courier))
        resolveAll()
        alice.casts(ursine)
        resolveAll()
        expect {
            ursine.power shouldBe 6
            ursine.toughness shouldBe 5
        }
    }

    // CR 701.6a, 113.1c: every ability on the stack is countered; the other spells are exiled (CR 406)
    @Test
    fun dismissalExilesSpellsAndCountersAbilities() = scenario {
        bob.battlefield(font, grunt, "Plains", "Plains")
        bob.hand(sentry)
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(dismissal)
        turn(active = bob)
        bob.casts(sentry)
        bob.activates(font, choosing = listOf(grunt))
        bob.passes()
        alice.casts(dismissal)
        resolveAll()
        expect {
            bob.exile has sentry
            bob.life shouldBe 20
            alice.graveyard has dismissal
            bob.graveyard has grunt
        }
    }

    // CR 707.9b: the copy is 1/1, also a Spirit, with flying; CR 111.4: the Zombie's power and toughness are the exiled card's
    @Test
    fun splitterMakesASpiritCopyAndAZombie() = scenario {
        alice.battlefield(splitter, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.graveyard(grunt)
        alice.activates(splitter, target = grunt)
        resolveAll()
        expect {
            alice.exile has grunt
            alice.graveyard has splitter
            grunt.power shouldBe 1
            grunt.toughness shouldBe 1
            grunt hasKeyword Keyword.Flying.kind
            grunt.subtypes shouldBe listOf(Subtype("Ogre"), Subtype("Spirit"))
            "Zombie Token".power shouldBe 4
            "Zombie Token".toughness shouldBe 3
        }
    }

    // CR 601.2f, 205.2a: three card types in the graveyard make it cost {10}
    @Test
    fun vorthakCostsOneLessForEachCardType() = scenario {
        libraries()
        alice.battlefield(*Array(10) { "Island" })
        alice.graveyard(grunt, "Forest", ledger)
        alice.hand(vorthak)
        alice.casts(vorthak, target = "bob")
        resolveAll()
        expect { alice.battlefield has vorthak }
    }

    @Test
    fun vorthakCantBeCastWithTooFewCardTypes() = scenario {
        libraries()
        alice.battlefield(*Array(10) { "Island" })
        alice.graveyard(grunt, ledger2, ledger)
        alice.hand(vorthak)
        alice.casts(vorthak, target = "bob")
        assertEquals(emptyList(), decision<Decision.ManaPayment>().suggestion)
    }

    // CR 723.1: Alice decides for Carol during Carol's next turn; CR 500.7: Carol's extra turn follows that turn, before Alice's (3 players)
    @Test
    fun vorthakControlsTheNextTurnThenGivesAnExtraTurn() = scenario(players = 3) {
        libraries()
        alice.battlefield(*Array(13) { "Island" })
        alice.hand(vorthak)
        turn(step = TurnStep.PrecombatMain)
        alice.casts(vorthak, target = "carol")
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        assertEquals(bob.id, state.turn.active)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        assertEquals(players[2].id, state.turn.active)
        assertEquals(alice.id, Engine.decider(state, players[2].id))
        advanceTo(TurnStep.PrecombatMain, turn = 4)
        assertEquals(players[2].id, state.turn.active)
        assertEquals(players[2].id, Engine.decider(state, players[2].id))
        advanceTo(TurnStep.PrecombatMain, turn = 5)
        assertEquals(alice.id, state.turn.active)
    }

    // CR 608.2h: a keyword one of your creatures has is shared at the beginning of combat; an opponent's creature's keyword isn't
    @Test
    fun odilaSharesKeywordsYourCreaturesHave() = scenario {
        libraries()
        alice.battlefield(odila, courier, sentry)
        bob.battlefield(lurker)
        turn(step = TurnStep.PrecombatMain)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            sentry hasKeyword Keyword.Flying.kind
            odila hasKeyword Keyword.Flying.kind
            sentry lacksKeyword Keyword.Deathtouch.kind
            sentry lacksKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 603.7c: the delayed trigger is about the chosen creatures; it triggers each time one of them deals combat damage
    @Test
    fun tamsinDrawsWhenAChosenCreatureDealsCombatDamage() = scenario {
        libraries()
        alice.battlefield(tamsin, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield(sentry, grunt)
        turn(step = TurnStep.PrecombatMain)
        alice.activates(tamsin, ability = 0, targets = listOf(sentry))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(sentry, grunt, defender = "bob")
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bob.life shouldBe 14
            alice.hand.size shouldBe 1
        }
    }

    // CR 614.1c, 707.9b: it enters as a copy with X +1/+1 counters; without a copy it is a 0/0 with them (CR 107.3m)
    @Test
    fun mirroredSelfCopiesWithXCounters() = scenario {
        alice.battlefield(grunt, "Forest", "Forest", "Island", "Island", "Island", "Island")
        alice.hand(mirrored)
        alice.casts(mirrored, x = 2)
        resolveAll()
        alice.chooses(grunt)
        resolveAll()
        // the copy is a Mossback Grunt (4/3) with two +1/+1 counters
        assertEquals(listOf(4, 6), powersOf("Mossback Grunt"))
    }

    @Test
    fun mirroredSelfWithoutACopyIsAZeroZeroWithXCounters() = scenario {
        alice.battlefield(grunt, "Forest", "Forest", "Island", "Island", "Island", "Island")
        alice.hand(mirrored)
        alice.casts(mirrored, x = 2)
        resolveAll()
        alice.chooses()
        resolveAll()
        expect {
            mirrored.power shouldBe 2
            mirrored.toughness shouldBe 2
        }
    }

    private fun Scenario.powersOf(name: String) = state.battlefield.mapNotNull { id -> view()[id]?.characteristics?.takeIf { it.name == name }?.power }.sorted()
}
