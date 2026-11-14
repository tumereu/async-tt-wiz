package arcanum.engine.sets.woe

import arcanum.api.CardType
import arcanum.api.Supertype
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.hob.pickTargets
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * WOE chunk 09r: the invented names carry the oracle text of Faerie Fencing, Extraordinary Journey, Feral Encounter and The Irencrag.
 */
class WoeChunk09rLeftoversScenariosTest {
    private val parry = Cards.invent("Pixie Parry", "{X}{B}", "Instant",
        "Target creature gets -X/-X until end of turn. That creature gets an additional -3/-3 until end of turn if you controlled a Faerie as you cast this spell.")
    private val pixie = Cards.invent("Thicket Pixie", "{U}", "Creature — Faerie", "", "1/1")
    private val giant = Cards.invent("Barrow Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    private val boar = Cards.invent("Bristle Boar", "{2}{G}", "Creature — Boar", "", "3/3")
    private val voyage = Cards.invent("Wondrous Voyage", "{X}{X}{U}{U}", "Enchantment", """
        When this enchantment enters, exile up to X target creatures. For each of those cards, its owner may play it for as long as it remains exiled.
        Whenever one or more nontoken creatures enter, if one or more of them entered from exile or was cast from exile, you draw a card. This ability triggers only once each turn.
    """)
    private val wanderer = Cards.invent("Voyage Wanderer", "{1}{G}", "Creature — Elf Scout", "", "2/2")
    private val lurker = Cards.invent("Mistfen Lurker", "{1}{U}", "Creature — Merfolk", "Flash", "2/1")
    private val hunt = Cards.invent("Wild Ambush Hunt", "{G}{G}", "Sorcery",
        "Look at the top five cards of your library. You may exile a creature card from among them. Put the rest on the bottom of your library " +
            "in a random order. You may cast the exiled card this turn. At the beginning of the next combat phase this turn, target creature you control " +
            "deals damage equal to its power to up to one target creature you don't control.")
    private val copperspire = Cards.invent("The Copperspire", "{2}", "Legendary Artifact", """
        {T}: Add {C}.
        Whenever a legendary creature you control enters, you may have The Copperspire become a legendary Equipment artifact named Emberbrand, Hero's Heirloom. If you do, it gains equip {3} and "Equipped creature gets +3/+3" and loses all other abilities.
    """)
    private val knight = Cards.invent("Copperspire Knight", "{1}{W}", "Legendary Creature — Human Knight", "", "2/2")

    // CR 601.2i: Alice controlled a Faerie as she cast it; the Faerie leaving before it resolves doesn't matter: -1/-1 and -3/-3
    @Test
    fun pixieParryKeepsTheFaerieBonusFixedAsItWasCast() = scenario {
        alice.battlefield("Swamp", "Swamp", pixie)
        alice.hand(parry)
        bob.battlefield(giant)
        alice.casts(parry, target = giant, x = 1)
        move(pixie, Zone.Graveyard)
        resolveAll()
        expect {
            giant.power shouldBe 1
            giant.toughness shouldBe 1
        }
    }

    // CR 601.2i: no Faerie as it was cast: only -X/-X
    @Test
    fun pixieParryWithoutAFaerieGivesOnlyMinusX() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(parry)
        bob.battlefield(giant)
        alice.casts(parry, target = giant, x = 1)
        resolveAll()
        expect {
            giant.power shouldBe 4
            giant.toughness shouldBe 4
        }
    }

    // CR 601.2i, 704.5f: X = 0 with a Faerie: the creature gets -3/-3 and dies
    @Test
    fun pixieParryWithAFaerieAndXZeroKillsAThreeToughnessCreature() = scenario {
        alice.battlefield("Swamp", pixie)
        alice.hand(parry)
        bob.battlefield(boar)
        alice.casts(parry, target = boar, x = 0)
        resolveAll()
        expect { boar.zone shouldBe Zone.Graveyard }
    }

    // CR 107.3m, 400.7, 603.4, 603.2h: three players; Bob may cast his exiled creature (Alice may not), it was cast from exile so Alice draws;
    // her own exiled creature cast later the same turn draws nothing more
    @Test
    fun wondrousVoyageLetsEachOwnerPlayTheirCardAndDrawsOnceATurn() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Forest", "Forest", wanderer)
        alice.hand(voyage)
        alice.library("Plains", "Plains")
        bob.battlefield("Island", "Island", lurker)
        carol.battlefield(boar)
        alice.casts(voyage, x = 2)
        resolveAll()
        pickTargets(wanderer, lurker)
        resolveAll()
        expect {
            wanderer.zone shouldBe Zone.Exile
            lurker.zone shouldBe Zone.Exile
            boar.zone shouldBe Zone.Battlefield
        }
        assertFailsWith<IllegalArgumentException> { alice.casts(lurker) }
        alice.passes()
        bob.casts(lurker)
        resolveAll()
        expect {
            lurker.zone shouldBe Zone.Battlefield
            alice.hand.size shouldBe 1
        }
        alice.casts(wanderer)
        resolveAll()
        expect {
            wanderer.zone shouldBe Zone.Battlefield
            alice.hand.size shouldBe 1
        }
    }

    // CR 603.4: a creature that enters from a hand doesn't trigger it
    @Test
    fun wondrousVoyageIgnoresACreatureCastFromHand() = scenario {
        alice.battlefield("Island", "Island", "Forest", "Forest", voyage)
        alice.hand(wanderer)
        alice.library("Plains")
        alice.casts(wanderer)
        resolveAll()
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 401.4, 603.7, 603.3d: the exiled creature may be cast this turn; at the next beginning of combat Alice chooses the Boar and Bob's
    // Lurker as the delayed trigger's targets, and the Boar deals 3 damage to it
    @Test
    fun wildAmbushHuntExilesACreatureAndFightsOneWayAtTheNextCombat() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", boar)
        alice.hand(hunt)
        alice.library(wanderer, "Plains", "Island", "Swamp", "Mountain", "Forest")
        bob.battlefield(lurker)
        alice.casts(hunt)
        resolveAll()
        alice.chooses(wanderer)
        resolveAll()
        expect {
            wanderer.zone shouldBe Zone.Exile
            alice.library.size shouldBe 5
        }
        alice.casts(wanderer)
        resolveAll()
        advanceTo(TurnStep.BeginningOfCombat)
        pickTargets(boar, lurker)
        resolveAll()
        expect {
            wanderer.zone shouldBe Zone.Battlefield
            lurker.zone shouldBe Zone.Graveyard
        }
    }

    // CR 612.8, 205.1a, 613.1f, 702.6a: it becomes Emberbrand, a legendary Equipment artifact with only equip {3} and the granted bonus
    @Test
    fun theCopperspireBecomesANamedEquipment() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", copperspire)
        alice.hand(knight)
        alice.casts(knight, paying = listOf("Plains", "Plains#1"))
        resolveAll()
        may(true)
        resolveAll()
        expect {
            copperspire.view.characteristics!!.name shouldBe "Emberbrand, Hero's Heirloom"
            copperspire.view.characteristics!!.supertypes shouldBe listOf(Supertype.Legendary)
            copperspire.types shouldBe listOf(CardType.Artifact)
            copperspire.subtypes shouldBe listOf(Subtype("Equipment"))
        }
        alice.activates(copperspire, target = knight)
        resolveAll()
        expect {
            knight.power shouldBe 5
            knight.toughness shouldBe 5
        }
        // the mana ability is gone: equip is its only activated ability
        assertFailsWith<AssertionError> { alice.activates(copperspire, ability = 1) }
    }

    // CR 603.5: declining the "you may" leaves it as it was
    @Test
    fun theCopperspireStaysWhenDeclined() = scenario {
        alice.battlefield("Plains", "Plains", copperspire)
        alice.hand(knight)
        alice.casts(knight, paying = listOf("Plains", "Plains#1"))
        resolveAll()
        may(false)
        resolveAll()
        expect { copperspire.view.characteristics!!.name shouldBe "The Copperspire" }
    }
}
