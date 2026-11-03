package arcanum.engine.sets.spm

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Marvel's Spider-Man mechanics in play: web-slinging (and web-slinging given to legendary spells), mayhem (with a cost, without
 * one, given to graveyard cards), and modified creatures. */
class SpmMechanicsScenariosTest {
    private val dasher = Cards.invent("Rooftop Dasher, Silk Sailer", "{2}{W}", "Legendary Creature — Spider Human Hero",
        "Web-slinging {W} (You may cast this spell for {W} if you also return a tapped creature you control to its owner's hand.)", "3/3")
    private val legion = Cards.invent("Thousand-Leg Legion, Many as One", "{1}{G}", "Legendary Creature — Spider Hero", """
        Web-slinging {4}{G}{G} (You may cast this spell for {4}{G}{G} if you also return a tapped creature you control to its owner's hand.)
        When Thousand-Leg Legion enters, if they were cast using web-slinging, you gain 3 life and create two 2/1 green Spider creature tokens with reach.
    """, "2/3")
    private val weaver = Cards.invent("Crimson Weaver, Twin of Valor", "{1}{R}{G}", "Legendary Creature — Spider Human Hero", """
        Web-slinging {R}{G} (You may cast this spell for {R}{G} if you also return a tapped creature you control to its owner's hand.)
        Trample
        Sensational Save — If Crimson Weaver was cast using web-slinging, he enters with X +1/+1 counters on him, where X is the mana value of the returned creature.
    """, "4/3")
    private val webcrawler = Cards.invent("Astonishing Webcrawler", "{1}{G}{W}{U}", "Legendary Creature — Spider Human Hero", """
        Vigilance, reach
        Each legendary spell you cast that's one or more colors has web-slinging {G}{W}{U}. (You may cast a spell for its web-slinging cost if you also return a tapped creature you control to its owner's hand.)
    """, "4/4")
    private val sanctum = Cards.invent("Sprawl Sanctum", null, "Land", """
        This land enters tapped.
        {T}: Add {G}, {W}, or {U}.
        {2}, Return a tapped creature you control to its owner's hand: Put this card from your hand onto the battlefield. Activate only as a sorcery.
    """)
    private val sinkhole = Cards.invent("Dune Sinkhole", "{1}{B}{B}", "Sorcery", """
        Mayhem {3}{B} (You may cast this card from your graveyard for {3}{B} if you discarded it this turn. Timing rules still apply.)
        All creatures get -2/-2 until end of turn. If this spell's mayhem cost was paid, creatures your opponents control get -2/-2 until end of turn instead.
    """)
    private val mob = Cards.invent("Seething Goblin Mob", "{4}{R}", "Creature — Goblin Berserker Villain", """
        Haste
        Mayhem {2}{R} (You may cast this card from your graveyard for {2}{R} if you discarded it this turn. Timing rules still apply.)
    """, "5/4")
    private val foundry = Cards.invent("Grimsby Foundry", null, "Land", """
        This land enters tapped.
        When this land enters from a graveyard, you lose 2 life.
        {T}: Add {U}, {B}, or {R}.
        Mayhem (You may play this card from your graveyard if you discarded it this turn. Timing rules still apply.)
    """)
    private val fiend = Cards.invent("Verdant Fiend", "{1}{U}{B}{R}", "Legendary Creature — Goblin Human Villain", """
        Flying, menace
        Spells you cast from your graveyard cost {2} less to cast.
        Goblin Formula — Each nonland card in your graveyard has mayhem. The mayhem cost is equal to its mana cost. (You may cast a card from your graveyard for its mayhem cost if you discarded it this turn. Timing rules still apply.)
    """, "3/3")
    private val glidewing = Cards.invent("Jet-Propelled Glidewing", "{3}", "Artifact — Equipment", """
        When this Equipment enters, if it was cast from your graveyard, attach it to target creature you control.
        Equipped creature gets +2/+0 and has flying and haste.
        Equip {2}
        Mayhem {2}
    """)
    private val tryst = Cards.invent("Moonlit Tryst", "{1}{R}", "Sorcery", "Discard a card, then draw two cards.")
    private val wardrobe = Cards.invent("Disguise Wardrobe", "{1}{W}", "Artifact", """
        This artifact enters with two +1/+1 counters on it.
        {T}: Move a +1/+1 counter from this artifact onto target creature you control. Activate only as a sorcery.
        Whenever a modified creature you control leaves the battlefield, put a +1/+1 counter on this artifact. (Equipment, Auras you control, and counters are modifications.)
    """)
    private val updraft = Cards.invent("Updraft Webrunner", "{W/U}{W/U}", "Creature — Spider Human Hero", """
        Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
        This creature has flying as long as it's modified. (Equipment, Auras you control, and counters are modifications.)
    """, "2/2")
    private val walker = Cards.invent("Gearweb Walker, Child-Piloted", "{3}{W}{U}", "Legendary Artifact Creature — Spider Hero", """
        Vigilance
        When Gearweb Walker enters, put a +1/+1 counter on target creature.
        Whenever a modified creature you control deals combat damage to a player, draw a card. (Equipment, Auras you control, and counters are modifications.)
    """, "4/4")
    private val bodysuit = Cards.invent("Webbed Bodysuit", "{1}", "Artifact — Equipment", """
        Equipped creature gets +2/+2 and is a Spider Hero in addition to its other types.
        Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val barb = Cards.invent("Stinger's Barb", "{1}{B}", "Instant", "Target creature gets -3/-3 until end of turn.")
    private val bear = Cards.invent("Rooftop Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Rooftop Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val giant = Cards.invent("Rooftop Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    /** Discards [name] when the discard is a choice (with one card in hand, it isn't). */
    private fun Scenario.discards(name: String) { if (pending != null) alice.chooses(name) }

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** The web-slinging alternative cost of [name] (its own, or one another object gives it). */
    private fun Scenario.webSlinging(name: String): CastWay = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost && it.keyword == Keyword.WebSlinging.Kind } == true
    }.origin)

    /** Web-slinging: {W} and a tapped creature returned to its owner's hand instead of the mana cost. */
    // CR 702.188a
    @Test
    fun webSlingingReturnsATappedCreature() = scenario {
        alice.battlefield("Plains")
        alice.battlefield(bear, tapped = true)
        alice.hand(dasher)
        alice.casts(dasher, way = webSlinging(dasher), choosing = listOf(bear))
        resolveAll()
        expect {
            dasher.zone shouldBe Zone.Battlefield
            alice.hand has bear
        }
    }

    /** No tapped creature, no web-slinging (an untapped creature can't be returned for it). */
    // CR 702.188a, 601.2h
    @Test
    fun webSlingingNeedsATappedCreature() = scenario {
        alice.battlefield("Plains", bear)
        alice.hand(dasher)
        assertFailsWith<Throwable> { alice.casts(dasher, way = webSlinging(dasher), choosing = listOf(bear)) }
    }

    /** "If they were cast using web-slinging": the enters trigger only then. */
    // CR 702.188a, 603.4
    @Test
    fun castUsingWebSlingingTriggers() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.battlefield(bear, tapped = true)
        alice.hand(legion)
        alice.casts(legion, way = webSlinging(legion), choosing = listOf(bear))
        resolveAll()
        expect {
            alice.life shouldBe 23
            alice.battlefield has "Spider Token"
        }
    }

    @Test
    fun castNormallyDoesNotTrigger() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(legion)
        alice.casts(legion)
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.battlefield lacks "Spider Token"
        }
    }

    /** The returned creature's mana value, as it last existed, is the number of counters. */
    // CR 702.188a, 614.1c, 608.2h
    @Test
    fun webSlingingCountersFromTheReturnedCreature() = scenario {
        alice.battlefield("Mountain", "Forest")
        alice.battlefield(giant, tapped = true)
        alice.hand(weaver)
        alice.casts(weaver, way = webSlinging(weaver), choosing = listOf(giant))
        resolveAll()
        expect { weaver.counters(p1p1) shouldBe 5 }
    }

    /** A legendary coloured spell gets web-slinging {G}{W}{U} from another permanent; a nonlegendary one doesn't. */
    // CR 702.188a, 601.2b
    @Test
    fun webSlingingGivenToLegendarySpells() = scenario {
        alice.battlefield(webcrawler, "Forest", "Plains", "Island")
        alice.battlefield(bear, tapped = true)
        alice.hand(legion, ogre)
        alice.casts(legion, way = webSlinging(legion).let { own ->
            CastWay(alternative = view()[card(legion)]!!.characteristics!!.abilities.last { inst ->
                (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost && it.keyword == Keyword.WebSlinging.Kind } == true
            }.origin).takeIf { it.alternative != own.alternative } ?: own
        }, choosing = listOf(bear), paying = listOf("Forest", "Plains", "Island"))
        resolveAll()
        expect {
            legion.zone shouldBe Zone.Battlefield
            alice.hand has bear
            alice.life shouldBe 23
        }
        assertEquals(0, view()[card(ogre)]!!.characteristics!!.abilities.count { it.ability is Keyword.WebSlinging })
    }

    /** The land puts itself onto the battlefield from the hand by returning a tapped creature. */
    // CR 602.2, 113.6m
    @Test
    fun landFromHandByReturningATappedCreature() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.battlefield(bear, tapped = true)
        alice.hand(sanctum)
        alice.activates(sanctum, ability = 1, choosing = listOf(bear))
        resolveAll()
        expect {
            sanctum.zone shouldBe Zone.Battlefield
            sanctum.tapped shouldBe true
            alice.hand has bear
        }
    }

    /** A card discarded this turn may be cast from the graveyard for its mayhem cost. */
    // CR 702.187b
    @Test
    fun mayhemCastsADiscardedCard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(tryst, mob)
        alice.library(bear, bear)
        alice.casts(tryst, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        discards(mob)
        resolveAll()
        expect { alice.graveyard has mob }
        alice.casts(mob, paying = listOf("Mountain#2", "Mountain#3", "Mountain#4"))
        resolveAll()
        expect { mob.zone shouldBe Zone.Battlefield }
    }

    /** A card put into the graveyard otherwise, or discarded an earlier turn, can't be cast with mayhem. */
    // CR 702.187b
    @Test
    fun mayhemNeedsADiscardThisTurn() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.graveyard(mob)
        assertFailsWith<Throwable> { alice.casts(mob) }
    }

    /** "If this spell's mayhem cost was paid": only the opponents' creatures get -2/-2. */
    // CR 702.187b, 607.2i
    @Test
    fun mayhemPaidChangesTheSpell() = scenario {
        alice.battlefield("Mountain", "Mountain", "Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(tryst, sinkhole)
        alice.library(ogre, ogre)
        bob.battlefield(bear)
        alice.casts(tryst, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        discards(sinkhole)
        resolveAll()
        alice.casts(sinkhole, paying = listOf("Swamp", "Swamp#1", "Swamp#2", "Swamp#3"))
        resolveAll()
        expect {
            card(bear, 0).zone shouldBe Zone.Battlefield
            card(bear, 1).zone shouldBe Zone.Graveyard
        }
    }

    /** Cast from the hand, every creature gets -2/-2. */
    // CR 608.2c
    @Test
    fun withoutMayhemEveryCreatureShrinks() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.hand(sinkhole)
        bob.battlefield(bear)
        alice.casts(sinkhole)
        resolveAll()
        expect {
            card(bear, 0).zone shouldBe Zone.Graveyard
            card(bear, 1).zone shouldBe Zone.Graveyard
        }
    }

    /** A land with mayhem and no cost is played from the graveyard after a discard; it enters from a graveyard. */
    // CR 702.187c, 305.1
    @Test
    fun mayhemWithoutACostPlaysALand() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(tryst, foundry)
        alice.library(bear, bear)
        alice.casts(tryst)
        resolveAll()
        discards(foundry)
        resolveAll()
        alice.plays(foundry)
        resolveAll()
        expect {
            foundry.zone shouldBe Zone.Battlefield
            foundry.tapped shouldBe true
            alice.life shouldBe 18
        }
    }

    /** Mayhem given to the graveyard's nonland cards costs their mana cost, reduced by {2} for being cast from a graveyard. */
    // CR 702.187b, 601.2f
    @Test
    fun grantedMayhemCostsTheManaCostLess() = scenario {
        alice.battlefield(fiend, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(tryst, ogre)
        alice.library(bear, bear)
        alice.casts(tryst, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        discards(ogre)
        resolveAll()
        alice.casts(ogre, paying = listOf("Mountain#2"))
        resolveAll()
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    /** Cast from the graveyard, the Equipment attaches itself as it enters. */
    // CR 702.187b, 603.4
    @Test
    fun equipmentCastWithMayhemAttaches() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", bear)
        alice.hand(tryst, glidewing)
        alice.library(ogre, ogre)
        alice.casts(tryst, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        discards(glidewing)
        resolveAll()
        alice.casts(glidewing, paying = listOf("Mountain#2", "Mountain#3"))
        resolveAll()
        if (pending != null) alice.answers(arcanum.engine.decision.Answer.Targets(listOf(target(bear))))
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Flying
        }
    }

    /** Counters make a creature modified; it has flying then. Equipment does too. */
    // CR 700.9
    @Test
    fun modifiedByCountersAndEquipment() = scenario {
        alice.battlefield(updraft, bodysuit, "Plains", "Plains", "Plains")
        expect { updraft lacksKeyword Keyword.Flying }
        alice.activates(bodysuit, target = updraft)
        resolveAll()
        expect {
            updraft hasKeyword Keyword.Flying
            updraft.power shouldBe 4
        }
    }

    /** A modified creature leaving the battlefield puts a counter on the wardrobe; an unmodified one doesn't. */
    // CR 700.9, 603.10a
    @Test
    fun modifiedCreatureLeavingTriggers() = scenario {
        alice.battlefield(wardrobe, ogre)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.hand(barb, barb)
        alice.castsWithoutCosts(barb, target = bear)
        resolveAll()
        alice.castsWithoutCosts(barb, target = ogre)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Graveyard
            wardrobe.counters(p1p1) shouldBe 1
        }
    }

    /** Only a modified creature's combat damage draws. */
    // CR 700.9, 510.2
    @Test
    fun modifiedCreatureCombatDamageDraws() = scenario {
        alice.battlefield(walker)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.library(ogre, ogre, ogre)
        bob.library(ogre, ogre)
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(bear, walker)
        allPass()
        if (state.priority.holder == null) bob.blocks()
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        expect { alice.hand.size shouldBe 2 }
    }
}
