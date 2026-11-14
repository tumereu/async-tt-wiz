package arcanum.engine.sets.dmu

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.eoe.choose
import arcanum.engine.sets.blb.targetsIfAsked
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The DMU cards of chunk 11, played: an Aura and Equipment that grant while the host is legendary, additional costs, cost reductions,
 * "can't gain life" for every opponent, an attack trigger that pumps, trains and untaps the other attackers, and a creature that
 * makes a token each combat or when it attacks.
 */
class DmuChunk11EnchantEquipCostsScenariosTest {
    private val study = Cards.invent("Hollin Study", "{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature has "Whenever this creature deals combat damage to a player, draw a card."
        As long as enchanted creature is legendary, it gets +1/+1 and has ward {1}. (Whenever enchanted creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
    """)
    private val boon = Cards.invent("Hollin Boon", "{R}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, target creature can't block this turn.
        Enchanted creature gets +1/+1 and has haste.
    """)
    private val keepsake = Cards.invent("Hollin Keepsake", "{2}", "Artifact — Equipment", """
        Equipped creature gets +2/+1.
        As long as equipped creature is legendary, it has trample and haste.
        Equip {2}
    """)
    private val cleaver = Cards.invent("Hollin Cleaver", "{1}", "Artifact — Equipment", """
        Equipped creature gets +2/+0.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val splinters = Cards.invent("Hollin Splinters", "{B}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice a creature.
        Destroy target creature.
    """)
    private val gamble = Cards.invent("Hollin Gamble", "{1}{R}", "Instant", """
        As an additional cost to cast this spell, discard a card.
        Draw two cards.
    """)
    private val tarin = Cards.invent("Tarin Hollowbanner, Rider", "{1}{R}{R}{W}", "Legendary Creature — Human Knight", """
        Vigilance, trample
        Whenever Tarin Hollowbanner attacks, all other attacking creatures you control get +1/+1 until end of turn. Other red attacking creatures you control gain trample until end of turn. Untap each other white attacking creature you control.
    """, "3/3")
    private val figment = Cards.invent("Hollin Figment", "{1}{U}", "Creature — Illusion", """
        Vigilance
        This creature can't be blocked as long as you've cast an instant or sorcery spell this turn.
    """, "2/1")
    private val knave = Cards.invent("Hollin Knave", "{1}{B}", "Creature — Human Knight", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Your opponents can't gain life.
        {1}{B}: This creature gets +1/+1 until end of turn.
    """, "2/2")
    private val colossus = Cards.invent("Hollin Colossus", "{7}{R}", "Creature — Hellion", """
        This spell costs {X} less to cast, where X is the greatest power among creatures you control.
        Trample
    """, "5/5")
    private val carrion = Cards.invent("Hollin Carrion", "{6}{B}", "Creature — Zombie Giant", """
        This spell costs {1} less to cast for each creature card in your graveyard.
        Deathtouch
    """, "5/5")
    private val lagan = Cards.invent("Lagan Hollowhand, Malice", "{1}{B}{R}", "Legendary Creature — Human Shaman", """
        At the beginning of combat on your turn, create a 2/1 red Elemental creature token with trample and haste. Sacrifice it at the beginning of the next end step.
        {T}: Search your library for a card, put it into your hand, then shuffle. Activate only if five or more creatures died this turn.
    """, "1/3")
    private val squib = Cards.invent("Squib Hollowreeve, Doubtful", "{2}{R}", "Legendary Creature — Goblin Noble", """
        Haste
        Whenever Squib Hollowreeve attacks, create a 1/1 red Goblin creature token that's tapped and attacking.
        You may cast this card from your graveyard by paying {3}{R} and exiling four other cards from your graveyard rather than paying its mana cost.
    """, "2/2")
    private val hero = Cards.invent("Hollin Hero", "{1}{G}", "Legendary Creature — Elf", "", "2/2")
    private val bear = Cards.invent("Hollin Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Hollin Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val pup = Cards.invent("Hollin Pup", "{G}", "Creature — Dog", "", "1/1")
    private val giant = Cards.invent("Hollin Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    private val behemoth = Cards.invent("Hollin Behemoth", "{7}{G}", "Creature — Beast", "", "8/8")
    private val redGuy = Cards.invent("Hollin Redcap", "{R}", "Creature — Goblin", "", "2/2")
    private val whiteGuy = Cards.invent("Hollin Whitecap", "{W}", "Creature — Human", "", "2/2")
    private val brute = Cards.invent("Hollin Brute", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val lout = Cards.invent("Hollin Lout", "{1}{R}", "Creature — Ogre", "", "2/2")
    private val titan = Cards.invent("Hollin Titan", "{4}{G}", "Creature — Giant", "", "4/4")
    private val idler = Cards.invent("Hollin Idler", "{G}", "Creature — Dog", "", "2/2")
    private val cantrip = Cards.invent("Hollin Cantrip", "{U}", "Instant", "Draw a card.")
    private val mendA = Cards.invent("Hollin Mend", "{0}", "Instant", "You gain 3 life.")
    private val mendB = Cards.invent("Hollin Soothe", "{0}", "Instant", "You gain 3 life.")
    private val mendC = Cards.invent("Hollin Salve", "{0}", "Instant", "You gain 3 life.")
    private val purge = Cards.invent("Hollin Purge", "{0}", "Sorcery", "Destroy all creatures with power 2 or greater.")
    private val trinketA = Cards.invent("Hollin Trinket", "{1}", "Artifact", "")
    private val trinketB = Cards.invent("Hollin Gizmo", "{1}", "Artifact", "")
    private val trinketC = Cards.invent("Hollin Charm", "{1}", "Artifact", "")
    private val trinketD = Cards.invent("Hollin Bauble", "{1}", "Artifact", "")
    private val trinketE = Cards.invent("Hollin Token", "{1}", "Artifact", "")
    private val tool = Cards.invent("Hollin Tool", "{1}", "Artifact", "")

    private fun Scenario.tokenCount() = state.battlefield.count { state.obj(it)?.token == true }

    // CR 303.4, 613.4c: the Aura grants the trigger to any creature, and the pump only while the creature is legendary
    @Test
    fun studyDrawsOnCombatDamageAndPumpsOnlyALegend() = scenario {
        alice.battlefield("Island", "Island", bear, hero)
        alice.hand(study, study)
        alice.library(tool)
        alice.casts(study, target = bear)
        resolveAll()
        expect { bear.power shouldBe 2 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.EndOfCombat)
        expect { alice.hand has tool }
    }

    @Test
    fun studyOnALegendGivesPlusOnePlusOne() = scenario {
        alice.battlefield("Island", hero)
        alice.hand(study)
        alice.casts(study, target = hero)
        resolveAll()
        expect {
            hero.power shouldBe 3
            hero.toughness shouldBe 3
        }
    }

    // CR 509.1b: only the target creature can't block this turn
    @Test
    fun boonStopsOneBlockerAndHastesTheHost() = scenario {
        alice.battlefield("Mountain")
        alice.battlefield(bear, sick = true)
        alice.hand(boon)
        bob.battlefield(ogre, pup)
        alice.casts(boon, target = bear)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Haste
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to bear) }
        bob.blocks(pup to bear)
    }

    // CR 301.5, 613.4c: the legendary bonus only for a legendary creature, equip moves it
    @Test
    fun keepsakeGrantsTrampleAndHasteOnlyToALegend() = scenario {
        alice.battlefield("Plains", "Plains", keepsake, hero, bear)
        alice.activates(keepsake, ability = 0, target = hero)
        resolveAll()
        expect {
            hero.power shouldBe 4
            hero.toughness shouldBe 3
            hero hasKeyword Keyword.Trample
            hero hasKeyword Keyword.Haste
        }
    }

    @Test
    fun keepsakeOnANonLegendGivesOnlyThePump() = scenario {
        alice.battlefield(keepsake, hero, bear)
        attach(keepsake, bear)
        expect {
            bear.power shouldBe 4
            bear.toughness shouldBe 3
            hero.power shouldBe 2
        }
    }

    // CR 702.6a: equip only as a sorcery
    @Test
    fun cleaverEquipsAtSorcerySpeedOnly() = scenario {
        alice.battlefield("Plains", "Plains", cleaver, bear)
        alice.activates(cleaver, ability = 0, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.toughness shouldBe 2
        }
    }

    @Test
    fun cleaverCannotEquipOnTheOpponentsTurn() = scenario {
        alice.battlefield("Plains", "Plains", cleaver, bear)
        turn(active = bob, step = TurnStep.PrecombatMain, priority = alice)
        assertFailsWith<IllegalArgumentException> { alice.activates(cleaver, ability = 0, target = bear) }
    }

    // CR 118.8, 601.2b: a creature is sacrificed as the spell is cast; with none the spell can't be cast
    @Test
    fun splintersSacrificeBeforeDestroying() = scenario {
        alice.battlefield("Swamp", bear)
        alice.hand(splinters)
        bob.battlefield(giant)
        alice.casts(splinters, target = giant, choosing = listOf(bear))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            giant.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun splintersNeedsACreatureToSacrifice() = scenario {
        alice.battlefield("Swamp")
        alice.hand(splinters)
        bob.battlefield(giant)
        assertFailsWith<IllegalArgumentException> { alice.casts(splinters, target = giant) }
    }

    // CR 118.8: the discard is paid on casting, so a lone spell in hand can't be cast
    @Test
    fun gambleNeedsAnotherCardToDiscard() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(gamble, tool)
        alice.library(trinketA, trinketB, trinketC)
        alice.casts(gamble, choosing = listOf(tool))
        resolveAll()
        expect {
            tool.zone shouldBe Zone.Graveyard
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun gambleAloneCannotBeCast() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(gamble)
        assertFailsWith<IllegalArgumentException> { alice.casts(gamble) }
    }

    // CR 508.1, 700.1: the attack trigger pumps the other attackers, gives red ones trample and untaps white ones; Tarin keeps his own size
    @Test
    fun tarinBoostsTrainsAndUntapsTheOtherAttackers() = scenario {
        alice.battlefield(tarin, redGuy, whiteGuy, idler)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(tarin, redGuy, whiteGuy)
        resolveAll()
        expect {
            tarin.power shouldBe 3
            redGuy.power shouldBe 3
            whiteGuy.power shouldBe 3
            idler.power shouldBe 2
            redGuy hasKeyword Keyword.Trample
            whiteGuy.tapped shouldBe false
            redGuy.tapped shouldBe true
        }
    }

    // CR 509.1b: unblockable only after an instant or sorcery was cast this turn
    @Test
    fun figmentIsBlockableUntilYouCastAnInstant() = scenario {
        alice.battlefield("Island", figment)
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(figment)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bear to figment)
    }

    @Test
    fun figmentIsUnblockableAfterAnInstant() = scenario {
        alice.battlefield("Island", figment)
        alice.hand(cantrip)
        alice.library(tool)
        bob.battlefield(bear)
        alice.casts(cantrip)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(figment)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(bear to figment) }
    }

    // CR 119.7: every opponent's life gain does nothing, the controller's still works
    @Test
    fun knaveStopsEveryOpponentGainingLife() = scenario(players = 3) {
        alice.battlefield(knave)
        alice.hand(mendA)
        bob.hand(mendB)
        carol.hand(mendC)
        alice.casts(mendA)
        resolveAll()
        expect { alice.life shouldBe 23 }
        bob.castsWithoutCosts(mendB)
        resolveAll()
        carol.castsWithoutCosts(mendC)
        resolveAll()
        expect {
            bob.life shouldBe 20
            carol.life shouldBe 20
        }
    }

    // CR 601.2f: the greatest power among your creatures (not the opponents'); the coloured part is never reduced
    @Test
    fun colossusCostsLessByYourGreatestPower() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", giant)
        alice.hand(colossus)
        alice.casts(colossus)
        resolveAll()
        expect { colossus.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun colossusIgnoresTheOpponentsCreatures() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear)
        bob.battlefield(giant)
        carol.battlefield(giant)
        alice.hand(colossus)
        alice.casts(colossus)
        resolveAll()
        expect { colossus.zone shouldBe Zone.Stack }
    }

    @Test
    fun colossusStillNeedsItsColouredMana() = scenario {
        alice.battlefield("Mountain", behemoth)
        alice.hand(colossus)
        alice.casts(colossus)
        resolveAll()
        expect { colossus.zone shouldBe Zone.Battlefield }
    }

    // CR 601.2f: one less for each creature card in your graveyard, nothing for other cards or other graveyards
    @Test
    fun carrionCostsLessPerCreatureCardInYourGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(bear, ogre, pup)
        alice.hand(carrion)
        alice.casts(carrion)
        resolveAll()
        expect { carrion.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun carrionIgnoresOtherCardsAndOtherGraveyards() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(bear, cantrip)
        bob.graveyard(ogre, pup, giant)
        alice.hand(carrion)
        alice.casts(carrion)
        resolveAll()
        expect { carrion.zone shouldBe Zone.Stack }
    }

    // CR 603.7a, 111.1: a token each combat that is sacrificed at the next end step; the search needs five deaths this turn, any player's
    @Test
    fun laganTokenEachCombatThenSacrificed() = scenario {
        alice.battlefield(lagan)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { assertEquals(1, tokenCount()) }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { assertEquals(0, tokenCount()) }
    }

    @Test
    fun laganSearchesAfterFiveCreaturesDiedThisTurn() = scenario(players = 3) {
        alice.battlefield(lagan, ogre, giant)
        bob.battlefield(brute, lout)
        carol.battlefield(titan)
        alice.hand(purge)
        alice.library(trinketA, trinketB)
        assertFailsWith<IllegalArgumentException> { alice.activates(lagan, ability = 0) }
        alice.casts(purge)
        resolveAll()
        expect { titan.zone shouldBe Zone.Graveyard }
        alice.activates(lagan, ability = 0)
        resolveAll()
        choose(trinketB)
        expect { alice.hand has trinketB }
    }

    @Test
    fun laganNeedsFiveDeathsNotFour() = scenario(players = 3) {
        alice.battlefield(lagan, ogre, giant)
        bob.battlefield(brute)
        carol.battlefield(titan)
        alice.hand(purge)
        alice.library(trinketA, trinketB)
        alice.casts(purge)
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.activates(lagan, ability = 0) }
    }

    // CR 508.4, 111.1: the token enters tapped and attacking
    @Test
    fun squibMakesATappedAttackingGoblin() = scenario {
        alice.battlefield(squib)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(squib)
        resolveAll()
        expect { assertEquals(1, tokenCount()) }
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 17 }
    }

    // CR 118.9, 601.2b: cast from the graveyard for {3}{R} and four other cards exiled from your own graveyard
    @Test
    fun squibCastFromGraveyardExilingFourOthers() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(squib, bear, ogre, pup, giant)
        alice.casts(squib, choosing = listOf(bear, ogre, pup, giant))
        resolveAll()
        expect {
            squib.zone shouldBe Zone.Battlefield
            bear.zone shouldBe Zone.Exile
            giant.zone shouldBe Zone.Exile
        }
    }

    @Test
    fun squibCannotExileFromTheOpponentsGraveyard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(squib, bear, ogre)
        bob.graveyard(pup, giant, redGuy)
        assertFailsWith<IllegalArgumentException> { alice.casts(squib) }
    }
}
