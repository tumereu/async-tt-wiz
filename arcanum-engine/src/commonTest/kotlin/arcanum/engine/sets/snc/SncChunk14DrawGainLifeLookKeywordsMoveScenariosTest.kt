package arcanum.engine.sets.snc

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.api.CounterKind
import kotlin.test.Test

/** Streets of New Capenna chunk 14 in play: a condition on entering, attacking with counters, and moving cards between zones. */
class SncChunk14DrawGainLifeLookKeywordsMoveScenariosTest {
    private val lieutenant = Cards.invent("Snc14 Crew Lieutenant", "{B}{R}{G}", "Creature — Human Warrior",
        "Haste\nThis creature has indestructible as long as it entered this turn.", "4/2")
    private val angel = Cards.invent("Snc14 City Angel", "{2}{W}{U}", "Creature — Angel Soldier",
        "Flying\nWhenever you attack with one or more creatures with counters on them, draw a card.", "3/1")
    private val bear = Cards.invent("Snc14 Idle Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val detour = Cards.invent("Snc14 Endless Detour", "{G}{W}{U}", "Instant",
        "The owner of target spell, nonland permanent, or card in a graveyard puts it on their choice of the top or bottom of their library.")
    private val charm = Cards.invent("Snc14 Obscura Charm", "{W}{U}{B}", "Instant", """
        Choose one —
        • Return target multicolored permanent card with mana value 3 or less from your graveyard to the battlefield tapped.
        • Counter target instant or sorcery spell.
        • Destroy target creature or planeswalker with mana value 3 or less.
    """.trimIndent())
    private val pair = Cards.invent("Snc14 Twin Hound", "{1}{B}{G}", "Creature — Dog", "", "2/2")
    private val mono = Cards.invent("Snc14 Plain Hound", "{1}{B}", "Creature — Dog", "", "2/2")
    private val big = Cards.invent("Snc14 Big Hound", "{3}{B}{G}", "Creature — Dog", "", "4/4")
    private val expert = Cards.invent("Snc14 Extraction Expert", "{2}{W}", "Creature — Human Rogue", """
        Lifelink
        When this creature enters, return target creature card with mana value 2 or less from your graveyard to the battlefield. That creature can't attack or block for as long as you control this creature.
    """.trimIndent(), "3/2")

    // CR 611.3a, 400.7: indestructible only on the turn it entered
    @Test
    fun lieutenantIsIndestructibleOnlyOnTheTurnItEntered() = scenario {
        alice.hand(lieutenant)
        alice.battlefield("Swamp", "Mountain", "Forest")
        alice.library("Plains", "Plains", "Plains")
        bob.library("Plains", "Plains", "Plains")
        alice.casts(lieutenant)
        resolveAll()
        expect { lieutenant hasKeyword Keyword.Indestructible.kind }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        expect { lieutenant lacksKeyword Keyword.Indestructible.kind }
    }

    // CR 508.1, 122.1: a counter on an attacker draws; no counters, no draw
    @Test
    fun angelDrawsOnlyWhenAnAttackerHasCounters() = scenario {
        alice.battlefield(angel, bear, counters = emptyList())
        alice.library("Plains", "Island")
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect { alice.hand.isEmpty() }
    }

    @Test
    fun angelDrawsWhenAnAttackerHasACounter() = scenario {
        alice.battlefield(angel)
        alice.battlefield(bear, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.library("Plains", "Island")
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 401.4, 400.3: the owner of a card in a graveyard chooses top or bottom; here Bob's card
    @Test
    fun detourPutsAnotherPlayersGraveyardCardOnItsOwnersLibrary() = scenario {
        alice.hand(detour)
        alice.battlefield("Forest", "Plains", "Island")
        bob.graveyard(bear)
        bob.library("Plains")
        alice.casts(detour, target = bear)
        resolveAll()
        answer(Answer.Option("top"))
        expect {
            bob.library.size shouldBe 2
            bob.graveyard.isEmpty()
        }
    }

    // CR 700.2, 110.5b (tapped): only a multicolored card with mana value 3 or less returns, tapped
    @Test
    fun charmReturnsAMulticoloredCardTapped() = scenario {
        alice.hand(charm)
        alice.battlefield("Plains", "Island", "Swamp")
        alice.graveyard(pair, mono)
        alice.casts(charm, modes = listOf(0), target = pair)
        resolveAll()
        expect {
            pair.zone shouldBe Zone.Battlefield
            pair.tapped shouldBe true
            mono.zone shouldBe Zone.Graveyard
        }
    }
}
