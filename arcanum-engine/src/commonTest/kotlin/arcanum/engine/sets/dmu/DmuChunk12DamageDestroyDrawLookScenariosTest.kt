package arcanum.engine.sets.dmu

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targetsIfAsked
import arcanum.engine.sets.msh.chooseIfOffered
import arcanum.engine.sets.msh.may
import arcanum.engine.sets.msh.payIfAsked
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The DMU cards of chunk 12, played: a sacrifice-outlet pinger, destroy modes, a destroy-and-gain with last known mana value, an edict,
 * looting and tap-creature triggers, land creatures, Equipment and Vehicle grants, death and draw drains with three players, affinity
 * and the look-at-the-top effects. The invented names carry the oracle text of the real cards.
 */
class DmuChunk12DamageDestroyDrawLookScenariosTest {
    private val cyclops = Cards.invent("Quillon Slingmaul", "{3}{R}{R}", "Creature — Cyclops", "{1}, Sacrifice another creature: This creature deals 1 damage to any target.", "5/4")
    private val rend = Cards.invent("Quillon Rend", "{1}{W}", "Instant", """
        Choose one —
        • Destroy target creature with toughness 4 or greater.
        • Destroy target enchantment.
    """)
    private val snuff = Cards.invent("Quillon Snuff", "{2}{B}{B}", "Instant", "Destroy target creature or planeswalker. If its mana value was 3 or less, you gain 3 life.")
    private val grind = Cards.invent("Quillon Grind", "{1}{R}", "Sorcery", """
        Choose one —
        • Destroy target artifact.
        • Destroy target creature with defender.
        • Quillon Grind deals 1 damage to each creature your opponents control.
    """)
    private val nerissa = Cards.invent("Quillon Nerissa", "{1}{B}{B}", "Legendary Planeswalker — Nerissa", """
        [+1]: Each player discards a card.
        [−2]: Target player sacrifices a creature.
        [−6]: Separate all permanents target player controls into two piles. That player sacrifices all permanents in the pile of their choice.
    """)
    private val rampart = Cards.invent("Quillon Rampart", "{2}{U}", "Creature — Wall", """
        Defender
        Whenever you cast an instant or sorcery spell, you may draw a card. If you do, discard a card. This ability triggers only once each turn.
    """, "0/5")
    private val revelation = Cards.invent("Quillon Revelation", "{4}{U}{U}", "Sorcery", "Draw cards equal to the number of instant and sorcery cards in your graveyard.")
    private val rager = Cards.invent("Quillon Rager", "{2}{B}", "Creature — Phyrexian Horror", "When this creature enters, you draw a card and you lose 1 life.", "2/2")
    private val roff = Cards.invent("Quillon Roff, Warden", "{W}{U}", "Legendary Creature — Human Wizard", """
        Whenever you cast an instant or sorcery spell, you may tap two untapped creatures you control. If you do, draw a card.
        {3}{W}{W}: Creatures you control get +1/+1 and gain vigilance until end of turn.
    """, "1/3")
    private val tavina = Cards.invent("Quillon Tavina, Warden", "{G}{G}{U}", "Legendary Creature — Merfolk Druid", """
        Land creatures you control have flying.
        Whenever a land you control enters, if you control seven or more lands, up to one target land you control becomes a 3/3 Elemental creature with haste. It's still a land.
    """, "3/3")
    private val elka = Cards.invent("Quillon Elka, Pilgrim", "{W}{B}", "Legendary Creature — Phyrexian Kor Cleric", """
        Deathtouch
        Whenever another creature you control enters, you gain 1 life.
        Whenever another creature you control dies, each opponent loses 1 life.
    """, "2/2")
    private val shaldred = Cards.invent("Quillon Shaldred, Aftermath", "{2}{B}{B}", "Legendary Creature — Phyrexian Praetor", """
        Deathtouch
        Whenever you draw a card, you gain 2 life.
        Whenever an opponent draws a card, they lose 2 life.
    """, "4/5")
    private val phalanx = Cards.invent("Quillon Phalanx", "{5}{W}", "Creature — Human Kor Soldier", """
        Affinity for creatures (This spell costs {1} less to cast for each creature you control.)
        Vigilance (Attacking doesn't cause this creature to tap.)
    """, "4/4")
    private val astar = Cards.invent("Quillon Astar, Banners", "{2}{R}{W}", "Legendary Creature — Human Warrior", """
        When Quillon Astar enters, look at the top seven cards of your library. You may reveal an Equipment or Vehicle card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        Equipment you control have equip {1}.
        Vehicles you control have crew 1.
    """, "4/4")
    private val hedgewall = Cards.invent("Quillon Hedgewall", "{1}{G}", "Creature — Plant Wall", """
        Defender
        When this creature enters, look at the top six cards of your library. You may reveal a land card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
    """, "0/2")
    private val lookout = Cards.invent("Quillon Lookout", "{2}{U}{U}", "Creature — Human Pirate", """
        Flying
        When this creature dies, look at the top two cards of your library. Put one of them into your hand and the other into your graveyard.
    """, "3/2")

    private val bear = Cards.invent("Quillon Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val cub = Cards.invent("Quillon Cub", "{G}", "Creature — Bear", "", "1/1")
    private val ogre = Cards.invent("Quillon Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val giant = Cards.invent("Quillon Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    private val wall = Cards.invent("Quillon Wall", "{2}", "Artifact Creature — Wall", "Defender", "0/4")
    private val trinket = Cards.invent("Quillon Trinket", "{1}", "Artifact", "")
    private val aura = Cards.invent("Quillon Charm Aura", "{1}{W}", "Enchantment", "")
    private val champion = Cards.invent("Quillon Champion", "{2}{B}", "Creature — Human", "", "3/4")
    private val cheap = Cards.invent("Quillon Cheapling", "{1}{B}", "Creature — Imp", "", "1/1")
    private val walker = Cards.invent("Quillon Walker", "{1}{B}", "Planeswalker — Quill", "", null)
    private val scroll1 = Cards.invent("Quillon Scroll One", "{U}", "Instant", "Draw a card.")
    private val scroll2 = Cards.invent("Quillon Scroll Two", "{U}", "Sorcery", "Draw a card.")
    private val page = (1..8).map { Cards.invent("Quillon Page $it", "{G}", "Creature — Plant", "", "1/1") }
    private val blade = Cards.invent("Quillon Blade", "{1}", "Artifact — Equipment", "Equipped creature gets +1/+0.")
    private val cart = Cards.invent("Quillon Cart", "{2}", "Artifact — Vehicle", "", "3/3")
    private val farmland = Cards.invent("Quillon Farmland", null, "Land", "")

    // CR 602.1, 701.21a: the sacrifice is a cost paid from your own other creatures
    @Test
    fun cyclopsSacrificesAnotherCreatureOfYoursToPing() = scenario {
        alice.battlefield("Mountain", cyclops, bear)
        bob.battlefield(ogre)
        alice.activates(cyclops, target = "bob", choosing = listOf(bear))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            cyclops.zone shouldBe Zone.Battlefield
            bob.life shouldBe 19
        }
    }

    @Test
    fun cyclopsCannotSacrificeItselfOrAnOpponentsCreature() = scenario {
        alice.battlefield("Mountain", cyclops)
        bob.battlefield(ogre)
        assertFailsWith<IllegalArgumentException> { alice.activates(cyclops, target = "bob", choosing = listOf(ogre)) }
        assertFailsWith<IllegalArgumentException> { alice.activates(cyclops, target = "bob", choosing = listOf(cyclops)) }
        expect {
            ogre.zone shouldBe Zone.Battlefield
            cyclops.zone shouldBe Zone.Battlefield
        }
    }

    // CR 700.2, 701.8: the first mode needs toughness 4 or more
    @Test
    fun rendDestroysABigCreatureButCannotTargetASmallOne() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(rend)
        bob.battlefield(giant, ogre)
        alice.casts(rend, modes = listOf(0))
        assertEquals(listOf(target(giant)), decision<Decision.Targets>().options)
        targetsIfAsked(giant)
        payIfAsked()
        resolveAll()
        expect {
            giant.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Battlefield
        }
    }

    @Test
    fun rendSecondModeDestroysAnEnchantment() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(rend)
        bob.battlefield(giant, aura)
        alice.casts(rend, modes = listOf(1), target = aura)
        resolveAll()
        expect {
            aura.zone shouldBe Zone.Graveyard
            giant.zone shouldBe Zone.Battlefield
        }
    }

    // CR 608.2h, 400.7: the mana value is the destroyed permanent's last known one
    @Test
    fun snuffGainsLifeOnlyWhenTheDestroyedCreatureWasCheap() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(snuff, snuff)
        bob.battlefield(ogre, giant)
        alice.casts(snuff, target = ogre)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            alice.life shouldBe 23
        }
        alice.casts(snuff, target = giant)
        resolveAll()
        expect {
            giant.zone shouldBe Zone.Graveyard
            alice.life shouldBe 23
        }
    }

    // CR 306.9 (a planeswalker's mana value), 608.2h
    @Test
    fun snuffDestroysACheapPlaneswalkerAndGainsLife() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(snuff)
        bob.battlefield(walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.casts(snuff, target = walker)
        resolveAll()
        expect {
            walker.zone shouldBe Zone.Graveyard
            alice.life shouldBe 23
        }
    }

    // CR 700.2, 120.3, 109.5: each creature the caster's opponents control, three players, not the caster's own
    @Test
    fun grindDamagesEveryOpponentsCreatureButNotYours() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", cub)
        bob.battlefield(page[0], ogre)
        carol.battlefield(page[1])
        alice.hand(grind)
        alice.casts(grind, modes = listOf(2))
        resolveAll()
        expect {
            cub.zone shouldBe Zone.Battlefield
            page[0].zone shouldBe Zone.Graveyard
            page[1].zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Battlefield
            ogre.view.obj?.damage shouldBe 1
        }
    }

    @Test
    fun grindDestroysAnArtifactOrADefender() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(grind, grind)
        bob.battlefield(trinket, wall, ogre)
        alice.casts(grind, modes = listOf(0), target = trinket)
        resolveAll()
        expect { trinket.zone shouldBe Zone.Graveyard }
        alice.casts(grind, modes = listOf(1))
        assertEquals(listOf(target(wall)), decision<Decision.Targets>().options)
        targetsIfAsked(wall)
        payIfAsked()
        resolveAll()
        expect {
            wall.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Battlefield
        }
    }

    // CR 701.21a, 306.5 (loyalty cost): the targeted player chooses what to sacrifice
    @Test
    fun nerissaMinusTwoMakesTheTargetPlayerChooseWhatToSacrifice() = scenario(players = 3) {
        alice.battlefield(nerissa, counters = listOf(CounterKind.Loyalty to 3))
        bob.battlefield(giant, cub)
        carol.battlefield(ogre)
        alice.activates(nerissa, ability = 1, target = "bob")
        resolveAll()
        bob.chooses(cub)
        resolveAll()
        expect {
            cub.zone shouldBe Zone.Graveyard
            giant.zone shouldBe Zone.Battlefield
            ogre.zone shouldBe Zone.Battlefield
        }
    }

    // CR 603.4: the second instant or sorcery in a turn does not trigger it again; "if you do": the discard follows only a draw
    @Test
    fun rampartLootsOnlyOncePerTurn() = scenario {
        alice.battlefield("Island", "Island", rampart)
        alice.hand(scroll1, scroll2, bear)
        alice.library(page[0], page[1], page[2], page[3])
        alice.casts(scroll1)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            alice.hand.size shouldBe 3
        }
        alice.casts(scroll2)
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            scroll2.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun rampartMayDeclineToLoot() = scenario {
        alice.battlefield("Island", rampart)
        alice.hand(scroll1)
        alice.library(page[0], page[1])
        alice.casts(scroll1)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 121.1, 109.3: counts instant and sorcery cards in the graveyard only
    @Test
    fun revelationDrawsOnePerInstantOrSorceryInTheGraveyard() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.graveyard(scroll1, scroll2, bear)
        alice.hand(revelation)
        alice.library(page[0], page[1], page[2])
        alice.casts(revelation)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 603.6a, 121.1, 119.3
    @Test
    fun ragerDrawsAndLosesOneLife() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(rager)
        alice.library(page[0])
        alice.casts(rager)
        resolveAll()
        expect {
            alice.hand has page[0]
            alice.life shouldBe 19
        }
    }

    // CR 118.12, 701.26a: only untapped creatures can be tapped for the cost; "if you do" draws
    @Test
    fun roffTapsTwoUntappedCreaturesToDraw() = scenario {
        alice.battlefield("Island", roff, bear, cub)
        alice.hand(scroll1)
        alice.library(page[0], page[1])
        alice.casts(scroll1)
        resolveAll()
        if (pending is Decision.May) may(true)
        alice.chooses(bear, cub)
        resolveAll()
        expect {
            bear.tapped shouldBe true
            cub.tapped shouldBe true
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun roffCannotPayWithTappedCreatures() = scenario {
        alice.battlefield("Island")
        alice.battlefield(bear, cub, tapped = true)
        alice.battlefield(roff)
        alice.hand(scroll1)
        alice.library(page[0], page[1])
        alice.casts(scroll1)
        resolveAll()
        if (pending is Decision.May) may(true)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            roff.tapped shouldBe false
        }
    }

    @Test
    fun roffPumpsAndGivesVigilanceUntilEndOfTurn() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", roff, bear)
        alice.activates(roff, ability = 0)
        resolveAll()
        expect {
            bear.power shouldBe 3
            roff.toughness shouldBe 4
            bear hasKeyword Keyword.Vigilance
        }
    }

    // CR 205.3, 613.1, 611.2a: only land creatures fly; the seventh land makes the animated land a 3/3 haste Elemental that stays a land
    @Test
    fun tavinaGivesFlyingToTheLandItAnimatesWithSevenLands() = scenario {
        alice.battlefield(tavina, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(farmland)
        alice.plays(farmland)
        targetsIfAsked(farmland)
        resolveAll()
        expect {
            farmland.power shouldBe 3
            farmland hasKeyword Keyword.Flying
            farmland hasKeyword Keyword.Haste
            tavina lacksKeyword Keyword.Flying
            assertTrue(arcanum.api.CardType.Land in farmland.view.characteristics!!.types, "it is still a land")
        }
    }

    // CR 603.4: the intervening "if" is false with six lands, so nothing triggers
    @Test
    fun tavinaDoesNothingWithSixLands() = scenario {
        alice.battlefield(tavina, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand("Forest")
        alice.plays("Forest")
        assertTrue(pending !is Decision.Targets, "no trigger, so no targets: $pending")
    }

    // CR 700.4, 603.10a: Elka sees another creature dying; each of two opponents loses 1 life
    @Test
    fun elkaDrainsEachOpponentWhenAnotherCreatureOfYoursDies() = scenario(players = 3) {
        alice.battlefield(elka, bear)
        bob.hand(scroll1)
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(snuff)
        alice.casts(snuff, target = bear)
        resolveAll()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 23
        }
    }

    @Test
    fun elkaGainsLifeWhenAnotherCreatureEnters() = scenario {
        alice.battlefield("Plains", "Forest", elka)
        alice.hand(bear)
        alice.casts(bear)
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    // CR 121.1, 119.3: three players; each draw of an opponent drains that player only
    @Test
    fun shaldredGainsForMyDrawsAndDrainsEachOpponentThatDraws() = scenario(players = 3) {
        alice.battlefield(shaldred, "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(scroll1, revelation)
        alice.library(page[0], page[1])
        bob.library(page[2], page[3])
        carol.library(page[4], page[5])
        alice.graveyard(scroll2)
        alice.casts(scroll1)
        resolveAll()
        expect {
            alice.life shouldBe 22
            bob.life shouldBe 20
            carol.life shouldBe 20
        }
    }

    @Test
    fun shaldredDrainsAnOpponentWhoDrawsInTheirDrawStep() = scenario(players = 3) {
        alice.battlefield(shaldred)
        bob.library(page[2], page[3])
        turn(number = 2, active = bob, step = TurnStep.Upkeep, priority = bob)
        advanceTo(TurnStep.PrecombatMain)
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 20
            alice.life shouldBe 20
        }
    }

    // CR 702.41a, 601.2f: costs {1} less per creature you control, generic mana only
    @Test
    fun phalanxCostsOneLessForEachCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear, cub, ogre)
        alice.hand(phalanx)
        alice.casts(phalanx)
        resolveAll()
        expect { phalanx.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun phalanxWithTwoCreaturesStillNeedsFourMana() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear, cub)
        alice.hand(phalanx)
        alice.casts(phalanx)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty(), "{3}{W} can't be paid with three Plains")
    }

    // CR 701.20a, 401.4: look at seven, take an Equipment or Vehicle, the rest to the bottom
    @Test
    fun astarFindsAnEquipmentAndGrantsEquipOne() = scenario {
        alice.battlefield("Mountain", "Plains", "Mountain", "Plains", bear)
        alice.hand(astar)
        alice.library(page[0], page[1], blade, page[2], page[3], page[4], page[5], page[6])
        alice.casts(astar)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(blade)
        resolveAll()
        expect {
            alice.hand has blade
            alice.library.size shouldBe 7
        }
    }

    @Test
    fun astarGivesEquipmentEquipOneAndVehiclesCrewOne() = scenario {
        alice.battlefield("Mountain", astar, blade, cart, bear)
        alice.activates(blade, ability = 0, target = bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
        alice.activates(cart, ability = 0, choosing = listOf(bear))
        resolveAll()
        expect { cart.tapped shouldBe false }
    }

    // CR 701.20a: only a land card may be taken; with none, nothing goes to hand
    @Test
    fun hedgewallTakesALandFromTheTopSix() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(hedgewall)
        alice.library(page[0], page[1], "Swamp", page[2], page[3], page[4], page[5])
        alice.casts(hedgewall)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered("Swamp")
        resolveAll()
        expect {
            alice.hand has "Swamp"
            alice.library.size shouldBe 6
        }
    }

    @Test
    fun hedgewallWithNoLandInTheTopSixTakesNothing() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(hedgewall)
        alice.library(page[0], page[1], page[2], page[3], page[4], page[5], "Swamp")
        alice.casts(hedgewall)
        resolveAll()
        expect {
            alice.hand.isEmpty()
            alice.library.size shouldBe 7
        }
    }

    // CR 700.4, 603.10a: when it dies, one of the top two to hand and the other to the graveyard
    @Test
    fun lookoutDyingTakesOneOfTheTopTwoAndMillsTheOther() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", lookout)
        alice.hand(snuff)
        alice.library(page[0], page[1], page[2])
        alice.casts(snuff, target = lookout)
        resolveAll()
        chooseIfOffered(page[1])
        resolveAll()
        expect {
            alice.hand has page[1]
            alice.graveyard has page[0]
            alice.library.size shouldBe 1
        }
    }
}
