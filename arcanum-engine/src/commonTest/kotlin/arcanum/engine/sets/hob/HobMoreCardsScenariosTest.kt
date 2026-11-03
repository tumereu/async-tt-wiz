package arcanum.engine.sets.hob

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** More of The Hobbit's cards in play: exchanging control of permanents sharing a card type, a kicked choice of targets, an
 * activation trigger on mana abilities, an Equipment doubling triggers, exiling until it leaves, dying creatures exiled, a
 * gift stopping all spells, an opponent searching for lands, beholding to untap, quest counters, life loss milling, a
 * graveyard card returning itself, cards played for life. */
class HobMoreCardsScenariosTest {
    private val scheme = Cards.inventFaces("Adventure",
        arcanum.parser.OracleCard("Tobbo, Luckholder", "{1}{U}", "Legendary Creature — Halfling Rogue", """
            Tobbo can't be blocked.
            Whenever Tobbo deals combat damage to a player, draw a card, then discard a card.
        """.trimIndent(), "1", "1"),
        arcanum.parser.OracleCard("Pilferer's Scheme", "{4}{U}", "Sorcery — Adventure",
            "Exchange control of two target nonland permanents that share a card type. (Then exile this card. You may cast the creature later from exile.)"))
    private val hawks = Cards.invent("The Hawks Are Here!", "{1}{W}", "Instant", """
        Kicker {2}{W}{W} (You may pay an additional {2}{W}{W} as you cast this spell.)
        Choose target creature you own. If this spell was kicked, instead choose any number of target creatures you own. Return each chosen creature to your hand. At the beginning of the next upkeep, create a 4/4 white Bird Soldier creature token with flying for each creature returned to your hand this way.
    """)
    private val elwin = Cards.invent("Elwin, Moon-Scholar", "{2}{U}", "Legendary Creature — Elf Noble", """
        Whenever you activate an ability of a creature, draw a card. This ability triggers only once each turn.
        {5}{U}{U}: Exile up to two other target nonland permanents you control. Return those cards to the battlefield under their owner's control at the beginning of the next end step.
    """, "3/3")
    private val manaElf = Cards.invent("Grove Mana Elf", "{G}", "Creature — Elf Druid", "{T}: Add {G}.", "1/1")
    private val cudgel = Cards.invent("Mage's Cudgel", "{1}{U}", "Artifact — Equipment", """
        Equipped creature has prowess. (Whenever its controller casts a noncreature spell, that creature gets +1/+1 until end of turn.)
        If a triggered ability of equipped creature triggers, that ability triggers an additional time.
        Equip Wizard {1}
        Equip {3}
    """)
    private val toast = Cards.invent("Toast the Hill-King", "{3}{W}", "Enchantment", """
        When this enchantment enters, for each opponent, exile up to one target nonland permanent that player controls until this enchantment leaves the battlefield.
        When this enchantment enters, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)
    """)
    private val leader = Cards.invent("Leader of the Chase", "{2}{B}{B}", "Creature — Wolf", """
        Flash
        If a creature an opponent controls would die, exile it instead. When you do, create a 2/2 green Wolf creature token.
    """, "4/3")
    private val ploy = Cards.invent("Tobbo's Ploy", "{1}{W}", "Instant", """
        Gift a Treasure (You may promise an opponent a gift as you cast this spell. If you do, they create a Treasure token before its other effects. It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        Return target spell to its owner's hand. If the gift was promised, players can't cast spells this turn.
    """)
    private val calm = Cards.invent("Calm the Chaos", "{2}{W}{W}", "Instant",
        "Exile all attacking creatures target player controls. That player may search their library for that many basic land cards, put those cards onto the battlefield tapped, then shuffle.")
    private val byway = Cards.invent("Elven Byway", null, "Land",
        "{T}, Pay 1 life, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle. You may behold an Elf. If you do, untap that land. (To behold an Elf, choose an Elf you control or reveal an Elf card from your hand.)")
    private val mayor = Cards.invent("The Mayor of Mere-town", "{1}{B}{B}", "Legendary Creature — Human Advisor", """
        Deathtouch
        Whenever a player loses life, that player mills that many cards. (Damage causes loss of life.)
        When The Mayor of Mere-town dies, draw a card for each graveyard with seven or more cards in it.
    """, "3/2")
    private val forsaken = Cards.invent("Smeggle the Forsaken", "{1}{B}", "Legendary Creature — Halfling Horror", """
        Smeggle can't block.
        When Smeggle enters, exile up to one target card from an opponent's graveyard. Each opponent loses 2 life.
        {2}, Sacrifice an artifact or creature: Return this card from your graveyard to your hand. Activate only as a sorcery.
    """, "2/2")
    private val secret = Cards.invent("Secret Knowledge", "{X}{B}{B}", "Sorcery",
        "Exile the top X cards of target opponent's library. You may play those cards this turn. If you cast a spell this way, pay life equal to its mana value rather than pay its mana cost.")
    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val wizard = Cards.invent("Hedge Wizard", "{1}{U}", "Creature — Human Wizard", "", "1/1")
    private val greeter = Cards.invent("Cheerful Greeter", "{1}{W}", "Creature — Human Wizard", "Whenever this creature attacks, you gain 1 life.", "1/1")
    private val shock = Cards.invent("Quick Shock", "{R}", "Instant", "Quick Shock deals 2 damage to any target.")
    private val opt = Cards.invent("Peek of Fortune", "{U}", "Instant", "Draw a card.")
    private val charm = Cards.invent("Shatter Charm", "{W}", "Instant", "Destroy target enchantment.")
    private val idol = Cards.invent("Clay Idol", "{2}", "Artifact")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** The second target must share a card type with the first; control is exchanged. */
    // CR 701.12a, 601.2c
    @Test
    fun exchangesPermanentsSharingACardType() = scenario {
        alice.battlefield(bear, "Island", "Island", "Island", "Island", "Island")
        alice.hand(scheme)
        bob.battlefield(ogre, idol)
        alice.casts("Pilferer's Scheme", way = arcanum.engine.input.CastWay(face = 1), targets = listOf(bear, ogre))
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            bear.controller shouldBe bob.id
        }
    }

    /** Kicked, any number of your creatures return; a 4/4 Bird for each at the next upkeep. */
    // CR 702.33d, 603.7
    @Test
    fun kickedReturnsSeveralAndMakesBirds() = scenario {
        alice.battlefield(bear, ogre, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(hawks)
        alice.library(opt, opt)
        bob.library(opt, opt)
        alice.casts(hawks, kicked = 1, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            alice.hand has bear
            alice.hand has ogre
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        assertEquals(2, state.battlefield.count { state.obj(it)?.token == true })
    }

    /** Not kicked, exactly one target. */
    // CR 601.2c
    @Test
    fun unkickedReturnsOne() = scenario {
        alice.battlefield(bear, "Plains", "Plains")
        alice.hand(hawks)
        alice.casts(hawks, kicked = 0, target = bear)
        resolveAll()
        expect { alice.hand has bear }
    }

    /** Activating a creature's mana ability draws a card, once each turn. */
    // CR 605.1a, 603.2h
    @Test
    fun drawsWhenAnAbilityOfACreatureIsActivated() = scenario {
        alice.battlefield(elwin, manaElf, manaElf)
        alice.library(bear, bear)
        val elves = state.battlefield.filter { nameOf(state.obj(it)!!) == manaElf }
        elves.forEach { elf ->
            perform(Input.Activate(alice.id, elf, view()[elf]!!.characteristics!!.abilities.first { it.ability is arcanum.api.Ability.Activated }.origin))
            resolveAll()
        }
        expect { alice.hand.size shouldBe 1 }
    }

    /** The equipped creature's attack trigger triggers an additional time. */
    // CR 603.2d
    @Test
    fun equippedCreaturesTriggersTwice() = scenario {
        alice.battlefield(cudgel, greeter)
        attach(cudgel, greeter)
        alice.library(bear)
        bob.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(greeter)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    /** One target for each opponent, exiled until the enchantment leaves; it returns at once then. */
    // CR 610.3
    @Test
    fun exiledUntilItLeaves() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(toast)
        alice.library("Forest")
        bob.battlefield(ogre, "Plains")
        bob.hand(charm)
        alice.casts(toast)
        resolveAll()
        alice.answers(Answer.Order(listOf(0, 1)))
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target(ogre))))
        resolveAll()
        expect { bob.exile has ogre }
        passPriority()
        bob.casts(charm, target = toast)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    /** An opponent's creature that would die is exiled instead, and a Wolf is made. */
    // CR 614.1a, 603.11
    @Test
    fun dyingCreaturesExiledForAWolf() = scenario {
        alice.battlefield(leader, "Mountain")
        alice.hand(shock)
        bob.battlefield(bear)
        alice.casts(shock, target = bear)
        resolveAll()
        expect { bob.exile has bear }
        assertEquals(1, state.battlefield.count { state.obj(it)?.token == true })
    }

    /** With the gift promised, the spell returns and nobody can cast spells this turn. */
    // CR 702.174, 101.2
    @Test
    fun aPromisedGiftStopsSpells() = scenario {
        alice.battlefield("Plains", "Plains", "Mountain")
        alice.hand(ploy, shock)
        bob.battlefield("Mountain")
        bob.hand(shock)
        passPriority()
        bob.casts(shock, target = "alice")
        bob.passes()
        alice.casts(ploy, target = shock, kicked = 1)
        resolveAll()
        expect { bob.hand has shock }
        assertTrue(Engine.options(state, alice.id).none { it is Input.Cast })
    }

    /** The attackers are exiled; their controller may search for that many basic lands. */
    // CR 701.23, 508.1
    @Test
    fun exiledAttackersBecomeLands() = scenario {
        bob.battlefield(bear, ogre)
        bob.library("Forest", "Forest", "Forest")
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(calm)
        alice.library(opt)
        turn(2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(bear, ogre)
        passPriority()
        alice.casts(calm, target = "bob")
        resolveAll()
        if (pending is Decision.May) bob.answers(Answer.YesNo(true))
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses("Forest", "Forest")
        resolveAll()
        expect {
            bob.exile has bear
            bob.exile has ogre
        }
        assertEquals(2, state.battlefield.count { nameOf(state.obj(it)!!) == "Forest" && state.obj(it)!!.controller == bob.id })
    }

    /** Beholding an Elf untaps the land just found. */
    // CR 701.4a, 400.7
    @Test
    fun beholdUntapsTheFoundLand() = scenario {
        alice.battlefield(byway, manaElf)
        alice.library("Forest")
        alice.activates(byway)
        resolveAll()
        if (pending is Decision.ChooseObjects && (pending as Decision.ChooseObjects).options.size == 1) alice.chooses("Forest")
        resolveAll()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(manaElf)
        resolveAll()
        expect {
            "Forest".zone shouldBe Zone.Battlefield
            "Forest".tapped shouldBe false
            alice.life shouldBe 19
        }
    }

    /** A player who loses life mills that many cards. */
    // CR 120.3a
    @Test
    fun lifeLossMills() = scenario {
        alice.battlefield(mayor, "Mountain")
        alice.hand(shock)
        bob.library(bear, bear, bear, bear)
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { bob.graveyard.size shouldBe 2 }
    }

    /** The card returns itself from the graveyard to the hand, as a sorcery. */
    // CR 113.6m
    @Test
    fun returnsFromTheGraveyard() = scenario {
        alice.graveyard(forsaken)
        alice.battlefield(idol, "Swamp", "Swamp")
        alice.activates(forsaken, choosing = listOf(idol))
        resolveAll()
        expect { alice.hand has forsaken }
    }

    /** An exiled spell card is cast for life equal to its mana value. */
    // CR 118.9
    @Test
    fun castsTheExiledCardsForLife() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(secret)
        bob.library(ogre, bear)
        alice.casts(secret, target = "bob", x = 1)
        resolveAll()
        expect { bob.exile has ogre }
        val cast = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().first { state.obj(it.card)?.zone == Zone.Exile }
        perform(cast)
        resolveAll()
        expect {
            alice.life shouldBe 17
            ogre.controller shouldBe alice.id
        }
    }
}
