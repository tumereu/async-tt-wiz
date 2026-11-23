package arcanum.engine.sets.snc

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.choose
import arcanum.engine.sets.dgm.payIfAsked
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Streets of New Capenna chunk 08 in play: sacrifices sharing a card type with the sacrificed permanent, one of two targets of one
 * controller, one target per colour, copying a spell that wasn't cast, a clone's shield counter, a search by colour count, a cast trigger's
 * intervening if on counters, counter kinds put on new tokens, tokens for each destroyed permanent's controller, draws for players who
 * control a multicoloured creature, a defending player's choice, and an opponent's next draw replaced. */
class SncChunk08EffectsScenariosTest {
    private val grudge = Cards.invent("Snc08 Festering Feud", "{B}{R}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice a nonland permanent.
        Each opponent chooses a permanent they control that shares a card type with the sacrificed permanent and sacrifices it.
        Draw a card.
    """)
    private val incriminate = Cards.invent("Snc08 Frame the Accused", "{1}{B}", "Sorcery",
        "Choose two target creatures controlled by the same player. That player sacrifices one of them of their choice.")
    private val gallery = Cards.invent("Snc08 Lineup of Crooks", "{2}{B}", "Sorcery",
        "For each color, return up to one target creature card of that color from your graveyard to your hand.")
    private val errant = Cards.invent("Snc08 Vagrant, Alley Painter", "{U}", "Legendary Creature — Human Rogue", """
        Flash
        Defender, haste
        {1}{U}, {T}: Copy target spell you control that wasn't cast. You may choose new targets for the copy.
    """, "0/3")
    private val echoMage = Cards.invent("Snc08 Echo Adept", "{U}", "Creature — Human Wizard",
        "{1}: Copy target instant or sorcery spell you control.", "1/1")
    private val operative = Cards.invent("Snc08 Deep Cover Agent", "{2}{U}{U}", "Creature — Shapeshifter Rogue",
        "You may have this creature enter as a copy of any creature on the battlefield, except it enters with a shield counter on it if you control that creature. (If it would be dealt damage or destroyed, remove a shield counter from it instead.)",
        "0/0")
    private val door = Cards.invent("Snc08 Shifting Portal", "{2}{G}", "Artifact",
        "{1}, {T}, Sacrifice a creature: Count the colors of the sacrificed creature, then search your library for a creature card that's exactly that many colors plus one. Exile that card, then shuffle. You may cast the exiled card. Activate only as a sorcery.")
    private val ascendancy = Cards.invent("Snc08 Veiled Dominion", "{W}{U}{B}", "Enchantment", """
        Whenever you cast a spell, if its mana value is equal to 1 plus the number of soul counters on Snc08 Veiled Dominion, put a soul counter on Snc08 Veiled Dominion, then create a 2/2 white Spirit creature token with flying.
        As long as there are five or more soul counters on Snc08 Veiled Dominion, Spirits you control get +3/+3.
    """)
    private val pets = Cards.invent("Snc08 Curious Critters", "{1}{W}{U}", "Instant",
        "Create two 1/1 blue Fish creature tokens with \"This token can't be blocked.\" Then for each kind of counter among creatures you control, put a counter of that kind on either of those tokens.")
    private val soul = Cards.invent("Snc08 Spirit of Liberation", "{4}{G}{W}{U}", "Creature — Avatar",
        "When this creature enters, destroy up to three other target nonland permanents. For each of those permanents, its controller creates a 3/3 white Angel creature token with flying.", "5/7")
    private val depopulate = Cards.invent("Snc08 Thin the Streets", "{2}{W}{W}", "Sorcery",
        "Each player who controls a multicolored creature draws a card. Then destroy all creatures.")
    private val heavy = Cards.invent("Snc08 Extortion Brute", "{2}{B}", "Creature — Ogre Warrior", """
        Menace
        Whenever this creature attacks, defending player may have you draw a card. If they do, untap this creature and remove it from combat.
    """, "6/4")
    private val urabrask = Cards.invent("Snc08 Vexrask, Apostate Praetor", "{3}{R}{R}", "Legendary Creature — Phyrexian Praetor", """
        Haste
        At the beginning of your upkeep, exile the top card of your library. You may play it this turn.
        At the beginning of each opponent's upkeep, the next time they would draw a card this turn, instead they exile the top card of their library. They may play it this turn.
    """, "4/4")

    private val sentinel = Cards.invent("Snc08 Clockwork Sentinel", "{3}", "Artifact Creature — Construct", "", "2/2")
    private val trinket = Cards.invent("Snc08 Brass Trinket", "{1}", "Artifact", "")
    private val lantern = Cards.invent("Snc08 Hex Lantern", "{1}{B}", "Enchantment", "")
    private val cat = Cards.invent("Snc08 Alley Cat", "{1}{G}", "Creature — Cat", "", "2/2")
    private val tough = Cards.invent("Snc08 Street Tough", "{2}{R}", "Creature — Human Warrior", "", "3/3")
    private val recruit = Cards.invent("Snc08 Penny Recruit", "{W}", "Creature — Human Citizen", "", "1/1")
    private val envoy = Cards.invent("Snc08 Gilded Envoy", "{G}{W}", "Creature — Human Citizen", "", "2/2")
    private val smuggler = Cards.invent("Snc08 Dusk Smuggler", "{U}{B}", "Creature — Human Rogue", "", "2/2")
    private val thug = Cards.invent("Snc08 Back-Alley Thug", "{1}{B}", "Creature — Human Rogue", "", "2/1")
    private val boss = Cards.invent("Snc08 Tricolor Boss", "{G}{W}{U}", "Creature — Human Citizen", "", "4/4")
    private val grunt = Cards.invent("Snc08 Mono Grunt", "{G}", "Creature — Human Warrior", "", "1/1")
    private val spark = Cards.invent("Snc08 Alley Spark", "{R}", "Instant", "Snc08 Alley Spark deals 3 damage to any target.")

    // CR 701.21a, 101.4: each opponent sacrifices a permanent sharing a card type with the sacrificed artifact creature: an artifact, a creature
    @Test
    fun grudgeEachOpponentSacrificesASharedType() = scenario(players = 3) {
        alice.battlefield(sentinel, "Swamp", "Mountain")
        alice.hand(grudge)
        alice.library("Plains")
        bob.battlefield(trinket, "Island")
        carol.battlefield(lantern, cat)
        alice.casts(grudge, choosing = listOf(sentinel))
        resolveAll()
        expect {
            alice.graveyard has sentinel
            bob.graveyard has trinket
            bob.battlefield has "Island"
            carol.graveyard has cat
            carol.battlefield has lantern
            alice.hand has "Plains"
        }
    }

    // CR 115.3, 701.21a: that player picks which of the two targets to sacrifice
    @Test
    fun incriminateTheControllerChoosesOne() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(incriminate)
        bob.battlefield(cat, tough)
        alice.casts(incriminate, targets = listOf(cat, tough))
        resolveAll()
        assertEquals(bob.id, decision<Decision.ChooseObjects>().player)
        bob.chooses(tough)
        expect {
            bob.graveyard has tough
            bob.battlefield has cat
        }
    }

    // CR 115.3: a multicoloured card is the target of only one of its colours' slots
    @Test
    fun galleryReturnsOneCardPerColor() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(gallery)
        alice.graveyard(recruit, smuggler, thug)
        alice.casts(gallery)
        answer(Answer.Targets(listOf(target(recruit))))
        answer(Answer.Targets(listOf(target(smuggler))))
        assertFalse(target(smuggler) in decision<Decision.Targets>().options)
        answer(Answer.Targets(listOf(target(thug))))
        while (pending is Decision.Targets) answer(Answer.Targets(emptyList()))
        playOut()
        expect {
            alice.hand has recruit
            alice.hand has smuggler
            alice.hand has thug
        }
    }

    // CR 601.2a: a spell that was cast isn't a legal target
    @Test
    fun errantCantCopyACastSpell() = scenario {
        alice.battlefield(errant, "Mountain", "Island", "Island")
        alice.hand(spark)
        alice.casts(spark, target = "bob", paying = listOf("Mountain"))
        assertFailsWith<IllegalArgumentException> { alice.activates(errant) }
    }

    // CR 707.10: a copy wasn't cast, so it can be copied again
    @Test
    fun errantCopiesACopy() = scenario {
        alice.battlefield(errant, echoMage, "Mountain", "Island", "Island", "Island")
        alice.hand(spark)
        alice.casts(spark, target = "bob", paying = listOf("Mountain"))
        alice.activates(echoMage, target = spark, paying = listOf("Island"))
        resolve()
        alice.activates(errant)
        // only the copy is offered, not the spell that was cast
        val options = decision<Decision.Targets>().options
        assertEquals(1, options.size)
        answer(Answer.Targets(options))
        payIfAsked()
        resolveAll()
        while (pending is Decision.May) { answer(Answer.YesNo(false)); resolveAll() }
        expect { bob.life shouldBe 11 }
    }

    // CR 707.9b, 614.1c: a copy of a creature you control enters with a shield counter
    @Test
    fun operativeCopyingYourCreatureGetsAShield() = scenario {
        alice.battlefield(tough, "Island", "Island", "Island", "Island")
        alice.hand(operative)
        bob.battlefield(cat)
        alice.casts(operative)
        resolveAll()
        alice.chooses(tough)
        resolveAll()
        expect {
            card(operative).power shouldBe 3
            card(operative).counters(CounterKind.Shield) shouldBe 1
        }
    }

    // CR 614.1c: a copy of an opponent's creature doesn't
    @Test
    fun operativeCopyingAnOpponentsCreatureGetsNoShield() = scenario {
        alice.battlefield(tough, "Island", "Island", "Island", "Island")
        alice.hand(operative)
        bob.battlefield(cat)
        alice.casts(operative)
        resolveAll()
        alice.chooses(cat)
        resolveAll()
        expect {
            card(operative).power shouldBe 2
            card(operative).counters(CounterKind.Shield) shouldBe 0
        }
    }

    // CR 105.2, 608.2h: a two-colour creature sacrificed finds a three-colour card, then it's cast from exile paying its costs (CR 608.2g)
    @Test
    fun doorFindsACardOfOneMoreColorAndCastsIt() = scenario {
        alice.battlefield(door, envoy, "Forest", "Forest", "Plains", "Island")
        alice.library(grunt, boss, "Forest")
        alice.activates(door, choosing = listOf(envoy), paying = listOf("Forest"))
        resolveAll()
        val options = decision<Decision.ChooseObjects>().options
        assertTrue(card(boss) in options)
        assertFalse(card(grunt) in options)
        alice.chooses(boss)
        assertTrue(pending is Decision.May)
        answer(Answer.YesNo(true))
        payIfAsked()
        resolveAll()
        expect {
            alice.battlefield has boss
            alice.graveyard has envoy
        }
    }

    // CR 603.4: a mana value 1 spell with no soul counters triggers it; the next mana value 1 spell doesn't (it now needs 2)
    @Test
    fun ascendancyCountsUp() = scenario {
        alice.battlefield(ascendancy, "Plains", "Forest")
        alice.hand(recruit, grunt)
        alice.casts(recruit, paying = listOf("Plains"))
        resolveAll()
        expect {
            card(ascendancy).counters(CounterKind.Named("soul")) shouldBe 1
            alice.battlefield has "Spirit Token"
        }
        alice.casts(grunt, paying = listOf("Forest"))
        assertEquals(1, state.stack.size)
    }

    // CR 122.1: one counter of each kind among creatures you control, each on a token of your choice
    @Test
    fun petsCopyEachCounterKind() = scenario {
        alice.battlefield(tough, counters = listOf(CounterKind.PowerToughness(1, 1) to 2, CounterKind.Keyword(Keyword.Flying.kind) to 1))
        alice.battlefield(cat, counters = listOf(CounterKind.Shield to 1))
        alice.battlefield("Plains", "Island", "Island")
        alice.hand(pets)
        alice.casts(pets)
        resolveAll()
        var asked = 0
        val first = tokens("Fish Token").first()
        while (pending is Decision.ChooseObjects) {
            asked++
            answer(Answer.Objects(listOf(first)))
        }
        resolveAll()
        assertEquals(3, asked)
        val counters = state.obj(first)!!.counters.associate { it.kind to it.count }
        assertEquals(mapOf(CounterKind.PowerToughness(1, 1) to 1, CounterKind.Keyword(Keyword.Flying.kind) to 1, CounterKind.Shield to 1), counters)
        val other = tokens("Fish Token").single { it != first }
        assertTrue(state.obj(other)!!.counters.none { it.count > 0 })
    }

    // CR 608.2h: each destroyed permanent's controller gets an Angel, the caster too
    @Test
    fun soulGivesEachControllerAnAngel() = scenario(players = 3) {
        alice.battlefield(recruit)
        alice.hand(soul)
        bob.battlefield(trinket)
        carol.battlefield(lantern)
        alice.castsWithoutCosts(soul)
        playOut(recruit, trinket, lantern)
        expect {
            alice.graveyard has recruit
            bob.graveyard has trinket
            carol.graveyard has lantern
        }
        assertEquals(1, tokens("Angel Token").count { state.obj(it)!!.controller == alice.id })
        assertEquals(1, tokens("Angel Token").count { state.obj(it)!!.controller == bob.id })
        assertEquals(1, tokens("Angel Token").count { state.obj(it)!!.controller == carol.id })
    }

    // CR 608.2c: only the player controlling a multicoloured creature draws, then every creature is destroyed
    @Test
    fun depopulateDrawsForMulticoloredControllers() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(depopulate)
        alice.library("Island")
        bob.battlefield(envoy)
        bob.library("Swamp")
        carol.battlefield(cat)
        carol.library("Forest")
        alice.casts(depopulate)
        resolveAll()
        expect {
            bob.hand has "Swamp"
            carol.hand lacks "Forest"
            alice.hand lacks "Island"
            bob.graveyard has envoy
            carol.graveyard has cat
        }
    }

    // CR 506.2, 608.2d: the defending player decides; if they have you draw, the attacker untaps and leaves combat (CR 506.4)
    @Test
    fun heavyDefenderPaysWithACard() = scenario(players = 3) {
        alice.battlefield(heavy)
        alice.library("Island")
        turn(1, alice, TurnStep.DeclareAttackers)
        alice.attacks(heavy, defender = "carol")
        resolveAll()
        assertEquals(carol.id, pending!!.player)
        answer(Answer.YesNo(true))
        resolveAll()
        advanceTo(TurnStep.EndOfCombat)
        expect {
            alice.hand has "Island"
            card(heavy).tapped shouldBe false
            carol.life shouldBe 20
        }
    }

    // CR 608.2d: declined: no draw, the attack goes on
    @Test
    fun heavyDefenderDeclines() = scenario(players = 3) {
        alice.battlefield(heavy)
        alice.library("Island")
        turn(1, alice, TurnStep.DeclareAttackers)
        alice.attacks(heavy, defender = "carol")
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        advanceTo(TurnStep.EndOfCombat)
        expect {
            alice.hand lacks "Island"
            carol.life shouldBe 14
            bob.life shouldBe 20
        }
    }

    // CR 614.1a, 614.11: the opponent's next draw becomes an exile of the top card, which they may play this turn
    @Test
    fun urabraskReplacesTheOpponentsNextDraw() = scenario {
        alice.battlefield(urabrask)
        bob.library("Mountain", "Swamp", "Swamp")
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        expect {
            bob.exile has "Mountain"
            bob.hand lacks "Mountain"
            bob.hand lacks "Swamp"
        }
        bob.plays("Mountain")
        expect { bob.battlefield has "Mountain" }
    }
}
