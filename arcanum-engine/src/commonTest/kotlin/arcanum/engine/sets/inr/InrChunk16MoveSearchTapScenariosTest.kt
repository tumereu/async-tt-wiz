package arcanum.engine.sets.inr

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.TargetRef
import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test

/** INR chunk 16: a flicker with a type check, a discard trigger with a reflexive return, and exiling a spell until the exiler leaves.
 * Invented names carry the oracle text. */
class InrChunk16MoveSearchTapScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val flux = Cards.invent("Phase Flicker", "{U}", "Instant",
        "Exile target creature you control, then return that card to the battlefield under its owner's control. If it's a Spirit, put a +1/+1 counter on it.")
    private val spirit = Cards.invent("Pale Wisp", "{1}{W}", "Creature — Spirit", "", "1/1")
    private val ox = Cards.invent("Plodding Steer", "{2}", "Creature — Ox", "", "2/2")
    private val awakening = Cards.invent("Grave Stirring", "{3}{B}{B}", "Sorcery", """
        Return target creature card from your graveyard to the battlefield.
        When you discard this card, you may pay {B}. When you do, return target creature card from your graveyard to your hand.
    """)
    private val rummage = Cards.invent("Rummage Through", "{1}{R}", "Sorcery", "Discard a card, then draw two cards.")
    private val queller = Cards.invent("Spell Warden", "{1}{W}{U}", "Creature — Spirit", """
        Flash
        Flying
        When this creature enters, exile target spell with mana value 4 or less.
        When this creature leaves the battlefield, the exiled card's owner may cast that card without paying its mana cost.
    """, "2/3")
    private val altar = Cards.invent("Bone Altar", "{1}", "Artifact", "Sacrifice a creature: You gain 1 life.")
    private val bolt = Cards.invent("Jolt Spark", "{R}", "Instant", "Jolt Spark deals 2 damage to target player.")

    // CR 400.7: the returned card is a new object, checked as a Spirit after it returns; CR 122.1: the counter
    @Test
    fun essenceFlux_spiritGetsACounterAndOtherCreaturesDoNot() = scenario {
        alice.battlefield("Island", "Island", spirit, ox)
        alice.hand(flux, flux)
        alice.casts(flux, target = spirit)
        resolveAll()
        alice.casts(flux, target = ox)
        resolveAll()
        expect {
            spirit.zone shouldBe Zone.Battlefield
            spirit.counters(p1p1) shouldBe 1
            ox.zone shouldBe Zone.Battlefield
            ox.counters(p1p1) shouldBe 0
        }
    }

    // CR 701.9a, 603.12: discarding the card triggers it from the graveyard; paying {B} makes the reflexive trigger return a creature
    @Test
    fun edgarsAwakening_discardedPayToReturnACreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Swamp")
        alice.graveyard(ox)
        alice.hand(awakening, rummage)
        alice.library(spirit, spirit)
        alice.casts(rummage)
        resolveAll()
        answer(Answer.YesNo(true))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(decision<Decision.Targets>().options.first())))
        resolveAll()
        expect {
            alice.graveyard has awakening
            alice.hand has ox
        }
    }

    // CR 603.10a: leaves-the-battlefield looks back; the exiled spell's owner (not its controller of the exile) may cast it free
    @Test
    fun spellQueller_ownerCastsTheExiledSpellWhenItLeaves() = scenario(players = 3) {
        alice.battlefield("Plains", "Island", "Island", altar)
        alice.hand(queller)
        players[2].battlefield("Mountain")
        players[2].hand(bolt)
        turn(number = 3, active = players[2])
        players[2].casts(bolt, target = "bob")
        players[2].passes()
        alice.casts(queller)
        resolveAll()
        expect {
            bolt.zone shouldBe Zone.Exile
            bob.life shouldBe 20
        }
        players[2].passes()
        alice.activates(altar, choosing = listOf(queller))
        resolveAll()
        answer(Answer.YesNo(true))
        answer(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolveAll()
        expect {
            bolt.zone shouldBe Zone.Graveyard
            bob.life shouldBe 18
            queller.zone shouldBe Zone.Graveyard
        }
    }
}
