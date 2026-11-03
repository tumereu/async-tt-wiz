package arcanum.engine.sets.hob

import arcanum.api.CardType
import arcanum.api.Supertype
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

/** The last of The Hobbit's rules pieces in play: a countered permanent spell exiled and cast free, casting from the graveyard
 * as an attack trigger resolves, an opponent's second draw, base power and toughness copied, token copies that aren't legendary,
 * an enchantment becoming a creature with a granted power-setting ability. */
class HobLastCardsScenariosTest {
    private val edict = Cards.invent("Elrian's Edict", "{4}{U}{U}", "Instant",
        "Counter target spell. If a permanent spell is countered this way, exile it instead of putting it into its owner's graveyard. You may cast that card without paying its mana cost for as long as it remains exiled.")
    private val tobbo = Cards.invent("Tobbo, Prowler in the Dark", "{1}{U}", "Legendary Creature — Halfling Rogue", """
        Spells you cast from anywhere other than your hand cost {1} less to cast.
        Whenever Tobbo attacks, you may cast an artifact, instant, or sorcery spell from your graveyard. If an instant or sorcery spell cast this way would be put into your graveyard, exile it instead.
    """, "2/2")
    private val riches = Cards.invent("Glittering Riches", "{1}{W}", "Enchantment", """
        Whenever an opponent draws their second card each turn, you create a Treasure token.
        {2}{W}: Two target players each draw a card.
    """)
    private val gelorn = Cards.invent("Gelorn, Elf-lord's Steward", "{2}{G}{G}", "Legendary Creature — Elf Advisor",
        "Whenever Gelorn attacks, choose up to one other target creature you control. Its base power and toughness become equal to Gelorn's power and toughness until end of turn.",
        "4/4")
    private val scriveners = Cards.invent("The Scrivener Halflings", "{3}{G}{G}", "Legendary Creature — Halfling Advisor", """
        When The Scrivener Halflings enter, if they're not a token, create two tokens that are copies of them, except the tokens aren't legendary.
        {T}: Add {C} for each Halfling you control.
    """, "1/1")
    private val welcome = Cards.invent("Barnulf's Welcome", "{1}{G}", "Enchantment", """
        Landfall — Whenever a land you control enters, put a +1/+1 counter on target creature you control.
        {5}{G}{G}: This enchantment becomes a Bear creature in addition to its other types and gains "This creature's power and toughness are each equal to the number of lands you control." (This effect doesn't end.)
    """)
    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val shock = Cards.invent("Quick Shock", "{R}", "Instant", "Quick Shock deals 2 damage to any target.")
    private val opt = Cards.invent("Peek of Fortune", "{U}", "Instant", "Draw a card.")
    private val p1p1 = arcanum.api.CounterKind.PowerToughness(1, 1)

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** The countered creature spell is exiled, and its card can be cast without paying its mana cost. */
    // CR 614.15, 118.9
    @Test
    fun counteredPermanentSpellCastFree() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(edict)
        alice.library(bear, bear)
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        bob.library(bear, bear)
        turn(1, active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(edict, target = ogre)
        resolveAll()
        expect { bob.exile has ogre }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        val free = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().first { state.obj(it.card)?.zone == Zone.Exile }
        perform(free)
        resolveAll()
        expect { ogre.controller shouldBe alice.id }
    }

    /** As the attack trigger resolves, an instant is cast from the graveyard ({1} less) and exiled after it resolves. */
    // CR 608.2g, 601.2f
    @Test
    fun castsFromTheGraveyardOnAttack() = scenario {
        alice.battlefield(tobbo, "Mountain")
        alice.graveyard(shock)
        alice.library(bear)
        bob.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(tobbo)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(shock)
        resolveAll()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.exile has shock
        }
    }

    /** An opponent's second card drawn in a turn: a Treasure for you; your own draws don't count. */
    // CR 603.2
    @Test
    fun opponentsSecondDraw() = scenario {
        alice.battlefield(riches, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        bob.library(bear, bear, bear)
        alice.library(bear, bear, bear)
        alice.activates(riches, targets = listOf("alice", "bob"))
        resolveAll()
        assertEquals(0, state.battlefield.count { state.obj(it)?.token == true })
        alice.activates(riches, targets = listOf("alice", "bob"))
        resolveAll()
        assertEquals(1, state.battlefield.count { state.obj(it)?.token == true })
    }

    /** The other creature's base power and toughness become this creature's. */
    // CR 613.4b, 608.2h
    @Test
    fun basePowerAndToughnessCopied() = scenario {
        alice.battlefield(gelorn, bear)
        alice.library(bear)
        bob.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(gelorn)
        resolveAll()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target(bear))))
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.toughness shouldBe 4
        }
    }

    /** The card makes two copies that aren't legendary; the copies make none. */
    // CR 707.9b, 603.4
    @Test
    fun tokenCopiesArentLegendary() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(scriveners)
        alice.casts(scriveners)
        resolveAll()
        val tokens = state.battlefield.filter { state.obj(it)?.token == true }
        assertEquals(2, tokens.size)
        assertTrue(tokens.all { Supertype.Legendary !in view()[it]!!.characteristics!!.supertypes })
        expect { scriveners.zone shouldBe Zone.Battlefield }
    }

    /** It becomes a Bear creature for good, its power and toughness the number of lands you control. */
    // CR 611.2a, 604.3a
    @Test
    fun enchantmentBecomesABear() = scenario {
        alice.battlefield(welcome, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(welcome)
        resolveAll()
        expect {
            assertTrue(CardType.Creature in welcome.types && CardType.Enchantment in welcome.types)
            welcome.power shouldBe 7
            welcome.toughness shouldBe 7
        }
    }
}
