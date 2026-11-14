package arcanum.engine.sets.lci

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.chooseNone
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.tokens
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** LCI chunk 10 played: a land entering tapped as a copy of a graveyard land (untapped when it doesn't copy), an ability countered whose
 * creature loses all abilities while the counterer stays, attacking Gnome tokens numbered by counters, Map tokens for each opponent with an
 * artifact, and a token for each mana from a Cave. Invented names carry the oracle text. */
class LciChunk10EffectsScenariosTest {
    private val hollows = Cards.invent("Resounding Hollows", null, "Land — Cave", """
        You may have this land enter tapped as a copy of any land card in a graveyard, except it's a Cave in addition to its other types.
        {T}: Add {C}.
    """)
    private val binder = Cards.invent("Hushwater Binder", "{2}{U}", "Creature — Merfolk Wizard", """
        Flash
        When this creature enters, counter up to one target activated or triggered ability. If an ability of an artifact, creature, or planeswalker is countered this way, that permanent loses all abilities for as long as this creature remains on the battlefield. (Mana abilities can't be targeted.)
    """, "3/2")
    private val ostra = Cards.invent("Ostra Kell, Waning Moon", "{1}{R}{W}", "Legendary Creature — Human Soldier",
        "Whenever you attack with one or more non-Gnome creatures, put a +1/+1 counter on Ostra Kell, then create X 1/1 colorless Gnome artifact creature tokens that are tapped and attacking, where X is the number of +1/+1 counters on Ostra Kell.",
        "1/2")
    private val tamsin = Cards.inventFaces("Adventure",
        face("Tamsin, Bold Wanderer", "{1}{W}", "Legendary Creature — Human Faerie Scout",
            "Whenever Tamsin attacks, reveal the top card of your library. If it's a creature card with mana value 3 or less, put it into your hand. Otherwise, you may put it into your graveyard.",
            "2/3", null, null),
        face("Onward Trek", "{G}", "Sorcery — Adventure",
            "Create X Map tokens, where X is one plus the number of opponents who control an artifact. (Then exile this card. You may cast the creature later from exile.)",
            null, null, null),
    )
    private val roost = Cards.invent("Roost of Gloomwings", "{2}{W}", "Enchantment", """
        When this enchantment enters, create a 1/1 black Bat creature token with flying for each mana from a Cave spent to cast it.
        Whenever a Cave you control enters, put a +1/+1 counter on target creature you control.
    """)

    private val grotto = Cards.invent("Mossglow Grotto", null, "Land", "{T}: Add {G}.")
    private val sentry = Cards.invent("Emberwatch Sentry", "{1}{R}", "Creature — Goblin", """
        Flying
        {T}: This creature deals 1 damage to any target.
    """, "1/1")
    private val cinder = Cards.invent("Quick Cinder", "{R}", "Instant", "Quick Cinder deals 3 damage to any target.")
    private val ox = Cards.invent("Gloamfield Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val trinket = Cards.invent("Tarnished Trinket", "{1}", "Artifact", "")
    private val cave = Cards.invent("Glimmer Cave", null, "Land — Cave", "{T}: Add {W}.")

    /** It enters tapped as a copy of an opponent's graveyard land, and it's a Cave as well. */
    // CR 707.9b, 614.1c
    @Test
    fun resoundingHollowsEntersTappedAsACopy() = scenario {
        alice.hand(hollows)
        bob.graveyard(grotto)
        alice.plays(hollows)
        alice.chooses(grotto)
        val id = card(hollows, zone = Zone.Battlefield)
        val c = view()[id]!!.characteristics!!
        assertEquals(grotto, c.name)
        assertTrue(Subtype("Cave") in c.subtypes)
        assertTrue(state.obj(id)!!.tapped)
        expect { bob.graveyard has grotto }
    }

    /** Copying nothing, it enters untapped as itself: "tapped" is part of entering as a copy. */
    // CR 614.1c
    @Test
    fun resoundingHollowsEntersUntappedWhenItCopiesNothing() = scenario {
        alice.hand(hollows)
        bob.graveyard(grotto)
        alice.plays(hollows)
        chooseNone()
        val id = card(hollows, zone = Zone.Battlefield)
        assertEquals(hollows, view()[id]!!.characteristics!!.name)
        assertFalse(state.obj(id)!!.tapped)
    }

    /** The countered ability's creature loses flying and its abilities until the counterer leaves the battlefield. */
    // CR 701.6b, 113.7, 611.2b
    @Test
    fun hushwaterBinderCountersAndSilencesTheSource() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain")
        alice.hand(binder, cinder)
        bob.battlefield(sentry)
        turn(number = 2, active = bob)
        bob.activates(sentry, target = "alice")
        bob.passes()
        alice.casts(binder)
        allPass()
        if (pending is Decision.Targets) {
            val ability = state.stack.first { state.stackItems[it]?.source == card(sentry) }
            alice.answers(Answer.Targets(listOf(TargetRef.Object(ability))))
        }
        resolveAll()
        expect {
            alice.life shouldBe 20
            stack.isEmpty()
            sentry lacksKeyword Keyword.Flying.kind
        }
        bob.passes()
        alice.casts(cinder, target = binder)
        resolveAll()
        expect {
            alice.graveyard has binder
            sentry hasKeyword Keyword.Flying.kind
        }
    }

    /** With one counter already, the attack trigger makes it two and creates two Gnomes tapped and attacking. */
    // CR 508.1m, 508.4, 107.3c
    @Test
    fun ostraKellCreatesAttackingGnomesForItsCounters() = scenario {
        alice.battlefield(ox)
        alice.battlefield(ostra, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        resolveAll()
        val gnomes = tokens("Gnome Token")
        assertEquals(2, gnomes.size)
        val attackers = state.combat!!.attackers.map { it.id }
        assertTrue(gnomes.all { it in attackers && state.obj(it)!!.tapped })
        expect { ostra.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2 }
    }

    /** With three players, the opponent with two artifacts counts once and the one with none not at all: two Maps. */
    // CR 102.2, 715.3a
    @Test
    fun onwardTrekCountsOpponentsWithAnArtifact() = scenario(players = 3) {
        alice.battlefield("Forest")
        alice.hand(tamsin)
        bob.battlefield(trinket, trinket)
        alice.casts("Onward Trek", way = CastWay(face = 1))
        resolveAll()
        assertEquals(2, tokens("Map Token").size)
    }

    /** Two of the three mana came from Caves: two Bats. */
    // CR 106.3, 400.7d
    @Test
    fun roostCreatesABatForEachManaFromACave() = scenario {
        alice.battlefield(cave, cave, "Plains")
        alice.hand(roost)
        alice.casts(roost, paying = listOf(cave, "$cave#1", "Plains"))
        resolveAll()
        assertEquals(2, tokens("Bat Token").size)
    }
}
