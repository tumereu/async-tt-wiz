package arcanum.engine.sets.woe

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ecl.paySuggested
import arcanum.engine.sets.hob.pickTargets
import kotlin.test.Test

/**
 * WOE chunk 16: the invented names carry the oracle text of Elvish Archivist, Rotisserie Elemental, Virtue of Loyalty,
 * Restless Bivouac, Restless Fortress and Agatha's Soul Cauldron; the other cards of the chunk are mechanically the same
 * as cards tested in earlier sets (parser tests only).
 */
class WoeChunk16CountersTapUntapScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val skewer = CounterKind.Named("skewer")
    private val archivist = Cards.invent("Thornlit Archivist", "{1}{G}", "Creature — Elf Artificer", """
        Whenever one or more artifacts you control enter, put two +1/+1 counters on this creature. This ability triggers only once each turn.
        Whenever one or more enchantments you control enter, draw a card. This ability triggers only once each turn.
    """, "0/1")
    private val trinket = Cards.invent("Pewter Fetish", "{0}", "Artifact", "")
    private val trinket2 = Cards.invent("Bronze Fetish", "{0}", "Artifact", "")
    private val charm = Cards.invent("Dewdrop Charm", "{0}", "Enchantment", "")
    private val charm2 = Cards.invent("Moonbeam Charm", "{0}", "Enchantment", "")
    private val skewerElemental = Cards.invent("Skewerfire Elemental", "{R}", "Creature — Elemental", """
        Menace
        Whenever this creature deals combat damage to a player, put a skewer counter on this creature. Then you may sacrifice it. If you do, exile the top X cards of your library, where X is the number of skewer counters on this creature. You may play those cards this turn.
    """, "1/1")
    private val virtue = Cards.inventFaces("Adventure",
        face("Virtue of Pledges", "{3}{W}{W}", "Enchantment", "At the beginning of your end step, put a +1/+1 counter on each creature you control. Untap those creatures.", null, null, null),
        face("Pledge Ardent", "{1}{W}", "Instant — Adventure",
            "Create a 2/2 white Knight creature token with vigilance. (Then exile this card. You may cast the enchantment later from exile.)", null, null, null))
    private val bear = Cards.invent("Pilgrim Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Pilgrim Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val camp = Cards.invent("Wandering Camp", null, "Land", """
        This land enters tapped.
        {T}: Add {R} or {W}.
        {1}{R}{W}: This land becomes a 2/2 red and white Ox creature until end of turn. It's still a land.
        Whenever this land attacks, put a +1/+1 counter on target creature you control.
    """)
    private val keep = Cards.invent("Wandering Keep", null, "Land", """
        This land enters tapped.
        {T}: Add {W} or {B}.
        {2}{W}{B}: This land becomes a 1/4 white and black Nightmare creature until end of turn. It's still a land.
        Whenever this land attacks, defending player loses 2 life and you gain 2 life.
    """)
    private val kettle = Cards.invent("Marrow's Soul Kettle", "{2}", "Legendary Artifact", """
        You may spend mana as though it were mana of any color to activate abilities of creatures you control.
        Creatures you control with +1/+1 counters on them have all activated abilities of all creature cards exiled with Marrow's Soul Kettle.
        {T}: Exile target card from a graveyard. When a creature card is exiled this way, put a +1/+1 counter on target creature you control.
    """)
    private val imp = Cards.invent("Ember Imp", "{R}", "Creature — Imp", "{R}{R}: You gain 3 life.", "1/1")
    private val sage = Cards.invent("Hearthfont Sage", "{1}{W}", "Creature — Human Cleric", "{T}: You gain 1 life.", "1/1")

    // CR 603.4: "only once each turn": the second artifact of the turn doesn't trigger it again; the enchantment ability is separate
    @Test
    fun archivistArtifactsTriggerOnlyOnceEachTurn() = scenario {
        alice.battlefield(archivist)
        alice.hand(trinket, trinket2)
        alice.library("Forest", "Forest", "Forest")
        alice.casts(trinket)
        resolveAll()
        alice.casts(trinket2)
        resolveAll()
        expect { archivist.counters(p1p1) shouldBe 2 }
    }

    // CR 603.4: the enchantment ability draws once for two enchantments in a turn, independent of the artifact one
    @Test
    fun archivistEnchantmentsDrawOnlyOnce() = scenario {
        alice.battlefield(archivist)
        alice.hand(charm, charm2)
        alice.library("Forest", "Forest", "Forest")
        alice.casts(charm)
        resolveAll()
        alice.casts(charm2)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            archivist.counters(p1p1) shouldBe 0
        }
    }

    private fun skewerHit(sacrifice: Boolean, block: arcanum.engine.scenario.Scenario.() -> Unit) = scenario {
        alice.battlefield(skewerElemental)
        alice.library("Forest", "Mountain", "Plains")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(skewerElemental)
        allPass()
        allPass()
        resolveAll()
        may(sacrifice)
        resolveAll()
        block()
    }

    // CR 608.2h, 113.7a: after the sacrifice, X is the skewer counters it had (one): one card exiled, playable this turn (CR 305.1)
    @Test
    fun elementalSacrificedExilesAsManyCardsAsItHadCounters() = skewerHit(sacrifice = true) {
        expect {
            skewerElemental.zone shouldBe Zone.Graveyard
            alice.exile.names shouldBe listOf("Forest")
            alice.library.size shouldBe 2
        }
    }

    // CR 608.2c: declining the sacrifice keeps the creature with its counter and exiles nothing
    @Test
    fun elementalKeptKeepsItsCounter() = skewerHit(sacrifice = false) {
        expect {
            skewerElemental.counters(skewer) shouldBe 1
            alice.library.size shouldBe 3
        }
    }

    // CR 511.1: at the beginning of your end step the +1/+1 counters go on each creature and each is untapped, not the enchantment
    @Test
    fun virtueCountersAndUntapsEachCreature() = scenario {
        alice.battlefield(virtue, bear, ox, tapped = true)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            ox.counters(p1p1) shouldBe 1
        }
        expect { bear.tapped shouldBe false }
        expect { ox.tapped shouldBe false }
    }

    // CR 715.3: the Adventure face makes a 2/2 Knight with vigilance
    @Test
    fun virtueAdventureMakesAKnight() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(virtue)
        alice.casts("Pledge Ardent", way = CastWay(face = 1))
        resolveAll()
        expect {
            card("Knight Token").power shouldBe 2
            card("Knight Token") hasKeyword Keyword.Vigilance.kind
        }
    }

    // CR 614.1d: the land enters tapped
    @Test
    fun campEntersTapped() = scenario {
        alice.hand(camp)
        alice.plays(camp)
        expect { camp.tapped shouldBe true }
    }

    // CR 205.1b, 508.1: animated into a 2/2 Ox, it attacks and its trigger puts a counter on target creature you control
    @Test
    fun campAnimatesAndAttacksWithACounter() = scenario {
        alice.battlefield(camp, bear, "Mountain", "Plains", "Forest")
        alice.activates(camp, ability = 1)
        paySuggested()
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(camp)
        pickTargets(bear)
        resolveAll()
        expect {
            camp.power shouldBe 2
            bear.counters(p1p1) shouldBe 1
        }
    }

    // CR 508.1, 119.3, 119.1, 102.2: in a three-player game only the defending player loses 2 life
    @Test
    fun keepDrainsOnlyTheDefendingPlayer() = scenario(players = 3) {
        alice.battlefield(keep, "Plains", "Swamp", "Forest", "Plains")
        alice.activates(keep, ability = 1)
        paySuggested()
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(keep, defender = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 20
            alice.life shouldBe 22
        }
    }

    // CR 609.4b: any color of mana pays for a creature's ability: {R}{R} paid with two Islands
    @Test
    fun kettleLetsAnyManaPayForCreatureAbilities() = scenario {
        alice.battlefield(kettle, imp, "Island", "Island")
        alice.activates(imp)
        paySuggested()
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    // CR 406.6, 613.1f: a creature card exiled with the kettle puts a counter on a creature, which then has its activated ability
    @Test
    fun kettleExilesACreatureCardAndGrantsItsAbility() = scenario {
        alice.battlefield(kettle, bear)
        bob.graveyard(sage)
        alice.activates(kettle, target = sage)
        resolveAll()
        expect {
            sage.zone shouldBe Zone.Exile
            bear.counters(p1p1) shouldBe 1
        }
        alice.activates(bear)
        resolveAll()
        expect { alice.life shouldBe 21 }
    }
}
