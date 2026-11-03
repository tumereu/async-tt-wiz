package arcanum.engine.sets.dsk

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dsk.Dsk.bear
import arcanum.engine.sets.dsk.Dsk.charm
import arcanum.engine.sets.dsk.Dsk.curse
import arcanum.engine.sets.dsk.Dsk.giant
import arcanum.engine.sets.dsk.Dsk.ogre
import arcanum.engine.sets.dsk.Dsk.p1p1
import arcanum.engine.sets.dsk.Dsk.shock
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Duskmourn's eerie and door cards in play (parser chunk 03). */
class DskEerieAndDoorCardsScenariosTest {
    private val cultMender = Cards.invent("Cult Mender", "{2}{W}", "Creature — Human Doctor", "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, this creature gains lifelink until end of turn.", "3/3")
    private val spectralWaltzers = Cards.invent("Spectral Waltzers", "{3}{W}{W}", "Creature — Spirit", """
        Flying
        When this creature enters, return an enchantment card from your graveyard to your hand or unlock a locked door of a Room you control.
        Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, create a 3/1 white Spirit creature token with flying.
    """, "2/5")
    private val hopefulForager = Cards.invent("Hopeful Forager", "{W}", "Creature — Human Scout", "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, put a +1/+1 counter on target creature.", "1/1")
    private val phantomChaser = Cards.invent("Phantom Chaser", "{2}{U}", "Creature — Human Scout", """
        Flash
        Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, draw a card.
    """, "2/3")
    private val flickeringHaunt = Cards.invent("Flickering Haunt", "{2}{U}", "Creature — Spirit", """
        Flying, vigilance
        Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, this creature gets +1/+1 until end of turn.
    """, "1/3")
    private val huntedScholar = Cards.invent("Hunted Scholar", "{1}{U}", "Creature — Human Wizard", """
        Defender
        Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, this creature can attack this turn as though it didn't have defender.
    """, "3/3")
    private val skitteringBonecrab = Cards.invent("Skittering Bonecrab", "{U}", "Creature — Crab Skeleton", "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, target player mills two cards. (They put the top two cards of their library into their graveyard.)", "0/3")
    private val reluctantHost = Cards.invent("Reluctant Host", "{2}{U}", "Creature — Human Wizard", """
        Vigilance
        Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, put a possession counter on this creature.
        When this creature dies, create an X/X blue Spirit creature token with flying, where X is the number of counters on this creature.
    """, "3/2")
    private val mirelurkLeech = Cards.invent("Mirelurk Leech", "{1}{B}", "Creature — Leech", "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, each opponent loses 1 life.", "2/2")
    private val dapperBloodsipper = Cards.invent("Dapper Bloodsipper", "{3}{B}", "Creature — Vampire Warrior", "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, this creature gets +2/+0 and gains lifelink until end of turn.", "2/5")
    private val hellishSpecter = Cards.invent("Hellish Specter", "{3}{R}", "Creature — Spirit", """
        Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, this creature gets +2/+0 until end of turn.
        When this creature dies, it deals damage equal to its power to any target.
    """, "2/3")
    private val dreadOfEternity = Cards.invent("Dread of Eternity", "{1}{U}{B}", "Enchantment Creature — Nightmare", """
        Flying, lifelink
        This creature can't block.
        Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, you may return this card from your graveyard to your hand.
    """, "2/2")
    private val gremlinWrangler = Cards.invent("Gremlin Wrangler", "{W}{U}", "Creature — Human Scout", "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, create a 1/1 red Gremlin creature token.", "2/2")
    private val skullclackPest = Cards.invent("Skullclack Pest", "{U}{B}", "Creature — Insect Skeleton", """
        Flying
        Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
    """, "1/4")
    private val varrowValgavothsSteward = Cards.invent("Varrow, Valgavoth's Steward", "{1}{W}{B}", "Legendary Creature — Human Warlock", "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, surveil 2 if this is the first time this ability has resolved this turn. If it's the second time, each opponent discards a card. If it's the third time, put a creature card from a graveyard onto the battlefield under your control.", "3/3")
    private val ghostlyDoorwarden = Cards.invent("Ghostly Doorwarden", "{3}{U}", "Creature — Spirit", """
        Flying
        Whenever this creature deals combat damage to a player, unlock a locked door of up to one target Room you control.
    """, "3/3")
    private val ragingSoulreaver = Cards.invent("Raging Soulreaver", "{2}{R}", "Creature — Spirit", "This creature gets +3/+0 as long as there are two or more unlocked doors among Rooms you control.", "1/4")
    private val curiousGlimmer = Cards.invent("Curious Glimmer", "{W}{U}", "Enchantment Creature — Fox Glimmer", """
        Enchantment spells you cast cost {1} less to cast.
        Unlock costs you pay cost {1} less.
    """, "2/3")
    private val lurkingWatcher = Cards.invent("Lurking Watcher", "{1}{U}", "Creature — Eye", "{T}: Add {U}. Spend this mana only to cast an enchantment spell, unlock a door, or turn a permanent face up.", "2/1")
    private val mirelaVendane = Cards.invent("Mirela Vendane", "{W}{U}{B}{R}{G}", "Legendary Creature — Human Warlock", """
        When Mirela Vendane enters, reveal the top seven cards of your library. Put all enchantment cards from among them into your hand and the rest on the bottom of your library in a random order.
        {T}: Lock or unlock a door of target Room you control. Activate only as a sorcery.
    """, "3/5")
    private val ringOfOldKeys = Cards.invent("Ring of Old Keys", "{1}", "Artifact", """
        {1}, {T}, Sacrifice this artifact: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
        {3}, {T}, Sacrifice this artifact: Lock or unlock a door of target Room you control. Activate only as a sorcery.
    """)
    private val trespassingSoulrager = Cards.invent("Trespassing Soulrager", "{U}{R}", "Creature — Spirit", """
        Vigilance
        {T}, Sacrifice a Room: This creature deals 2 damage to each opponent. Draw a card.
    """, "2/2")
    private val foyer = Cards.inventFaces("Split",
        face("Dim Foyer", "{W}", "Enchantment — Room", "When you unlock this door, you gain 1 life.", null, null, null),
        face("Dusty Pantry", "{1}{W}", "Enchantment — Room", "When you unlock this door, you gain 2 life.", null, null, null),
    )

    // ---- Chunk 03: eerie and doors

    // CR 207.2c: eerie is an ability word: "whenever an enchantment you control enters and whenever you fully unlock a Room"
    @Test
    fun cultMenderGainsLifelink() = scenario {
        alice.battlefield("Plains", cultMender)
        alice.hand(charm)
        alice.casts(charm)
        resolveAll()
        expect { cultMender hasKeyword Keyword.Lifelink }
    }

    @Test
    fun spectralWaltzersReturnsAnEnchantmentThenMakesSpirits() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(spectralWaltzers)
        alice.graveyard(charm)
        alice.casts(spectralWaltzers)
        resolveAll()
        if (pending is Decision.ChooseOption) option("return")
        if (pending is Decision.ChooseObjects) alice.chooses(charm)
        resolveAll()
        expect { alice.hand has charm }
        alice.casts(charm)
        resolveAll()
        expect {
            "Spirit Token".power shouldBe 3
            "Spirit Token" hasKeyword Keyword.Flying
        }
    }

    // CR 709.5f: unlocking a locked door as an effect
    @Test
    fun spectralWaltzersUnlocksADoor() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", foyer)
        alice.hand(spectralWaltzers)
        alice.casts(spectralWaltzers)
        resolveAll()
        if (pending is Decision.ChooseOption) option("unlock")
        if (pending is Decision.ChooseObjects) alice.chooses(foyer)
        if (pending is Decision.ChooseOption) option("Dim Foyer")
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    @Test
    fun hopefulForagerGrowsACreature() = scenario {
        alice.battlefield("Plains", hopefulForager, bear)
        alice.hand(charm)
        alice.casts(charm)
        resolveAll()
        targets(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    // CR 709.5i: fully unlocking a Room
    @Test
    fun phantomChaserDrawsOnAFullUnlock() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", phantomChaser)
        alice.battlefield(foyer)
        alice.library(bear, ogre)
        alice.takesSpecialAction(foyer, "unlock-left", paying = listOf("Plains"))
        resolveAll()
        expect { alice.hand.isEmpty() }
        alice.takesSpecialAction(foyer, "unlock-right", paying = listOf("Plains#1", "Plains#2"))
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun flickeringHauntGrows() = scenario {
        alice.battlefield("Plains", flickeringHaunt)
        alice.hand(charm)
        alice.casts(charm)
        resolveAll()
        expect { flickeringHaunt.power shouldBe 2 }
    }

    // CR 702.3b: may attack as though it didn't have defender
    @Test
    fun huntedScholarAttacksAfterEerie() = scenario {
        alice.battlefield("Plains", huntedScholar)
        alice.hand(charm)
        alice.casts(charm)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(huntedScholar)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun huntedScholarCantAttackOtherwise() = scenario {
        alice.battlefield(huntedScholar)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(huntedScholar) }
    }

    @Test
    fun skitteringBonecrabMills() = scenario {
        alice.battlefield("Plains", skitteringBonecrab)
        alice.hand(charm)
        bob.library(bear, ogre, giant)
        alice.casts(charm)
        resolveAll()
        targets("bob")
        resolveAll()
        expect { bob.library.names shouldBe listOf(giant) }
    }

    // CR 603.10a: the dies trigger counts its counters as it last existed
    @Test
    fun reluctantHostLeavesASpiritByItsCounters() = scenario {
        alice.battlefield("Plains", "Swamp", "Mountain", reluctantHost)
        alice.hand(charm, curse, shock)
        alice.casts(charm)
        resolveAll()
        alice.casts(curse)
        resolveAll()
        expect { reluctantHost.counters(CounterKind.Named("possession")) shouldBe 2 }
        alice.casts(shock, target = reluctantHost)
        resolveAll()
        expect {
            "Spirit Token".power shouldBe 2
            "Spirit Token" hasKeyword Keyword.Flying
        }
    }

    @Test
    fun mirelurkLeechDrains() = scenario {
        alice.battlefield("Plains", mirelurkLeech)
        alice.hand(charm)
        alice.casts(charm)
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun dapperBloodsipperGrowsWithLifelink() = scenario {
        alice.battlefield("Plains", dapperBloodsipper)
        alice.hand(charm)
        alice.casts(charm)
        resolveAll()
        expect {
            dapperBloodsipper.power shouldBe 4
            dapperBloodsipper hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun hellishSpecterBurnsAsItDies() = scenario {
        alice.battlefield("Plains", "Mountain", "Mountain", hellishSpecter)
        alice.hand(charm, shock, shock)
        alice.casts(charm)
        resolveAll()
        alice.casts(shock, target = hellishSpecter)
        resolveAll()
        alice.casts(shock, target = hellishSpecter)
        resolveAll()
        targets("bob")
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    // CR 603.6c-like: an ability that works from the graveyard
    @Test
    fun dreadOfEternityReturnsFromTheGraveyard() = scenario {
        alice.battlefield("Plains")
        alice.graveyard(dreadOfEternity)
        alice.hand(charm)
        alice.casts(charm)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.hand has dreadOfEternity }
    }

    @Test
    fun gremlinWranglerMakesAGremlin() = scenario {
        alice.battlefield("Plains", gremlinWrangler)
        alice.hand(charm)
        alice.casts(charm)
        resolveAll()
        expect { alice.battlefield has "Gremlin Token" }
    }

    @Test
    fun skullclackPestSurveils() = scenario {
        alice.battlefield("Plains", skullclackPest)
        alice.hand(charm)
        alice.library(ogre)
        alice.casts(charm)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { alice.graveyard has ogre }
    }

    // CR 603.2: "if this is the first time / second time this ability has resolved this turn"
    @Test
    fun varrowSurveilsThenMakesThemDiscard() = scenario {
        alice.battlefield("Plains", "Swamp", varrowValgavothsSteward)
        alice.hand(charm, curse)
        alice.library(ogre, bear)
        bob.hand(giant)
        alice.casts(charm)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { alice.graveyard has ogre }
        alice.casts(curse)
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses(giant)
        resolveAll()
        expect { bob.graveyard has giant }
    }

    @Test
    fun ghostlyDoorwardenUnlocksADoorOnDamage() = scenario {
        alice.battlefield(ghostlyDoorwarden, foyer)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ghostlyDoorwarden)
        advanceTo(TurnStep.CombatDamage)
        targetsIfAsked(foyer)
        resolveAll()
        if (pending is Decision.ChooseOption) option("Dim Foyer")
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    @Test
    fun ragingSoulreaverWithTwoUnlockedDoors() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", ragingSoulreaver)
        alice.hand(foyer)
        alice.castsDoor(foyer, 0, paying = listOf("Plains"))
        resolveAll()
        expect { ragingSoulreaver.power shouldBe 1 }
        alice.takesSpecialAction(foyer, "unlock-right", paying = listOf("Plains#1", "Plains#2"))
        resolveAll()
        expect { ragingSoulreaver.power shouldBe 4 }
    }

    // CR 601.2f: cost reductions; CR 709.5e: the unlock cost is reduced too
    @Test
    fun curiousGlimmerMakesEnchantmentsAndUnlocksCheaper() = scenario {
        alice.battlefield("Plains", "Plains", curiousGlimmer)
        alice.hand(foyer)
        alice.castsDoor(foyer, 0, paying = listOf("Plains"))
        resolveAll()
        expect { alice.life shouldBe 21 }
        alice.takesSpecialAction(foyer, "unlock-right", paying = listOf("Plains#1"))
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    // CR 106.6: mana spendable only on enchantment spells, unlocking or turning face up
    @Test
    fun lurkingWatcherPaysForAnUnlock() = scenario {
        alice.battlefield(lurkingWatcher, "Plains", foyer)
        alice.activates(lurkingWatcher)
        alice.takesSpecialAction(foyer, "unlock-right")
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    @Test
    fun mirelaVendaneFindsEnchantments() = scenario {
        alice.battlefield("Plains", "Island", "Swamp", "Mountain", "Forest")
        alice.hand(mirelaVendane)
        alice.library(charm, bear, curse, ogre)
        alice.casts(mirelaVendane)
        resolveAll()
        expect {
            alice.hand has charm
            alice.hand has curse
            alice.library.size shouldBe 2
        }
    }

    @Test
    fun mirelaVendaneUnlocksADoor() = scenario {
        alice.battlefield(mirelaVendane, foyer)
        alice.activates(mirelaVendane, target = foyer)
        resolveAll()
        if (pending is Decision.ChooseOption) option("unlock")
        if (pending is Decision.ChooseOption) option("Dusty Pantry")
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    @Test
    fun ringOfOldKeysFindsALand() = scenario {
        alice.battlefield(ringOfOldKeys, "Plains")
        alice.library(bear, "Island")
        alice.activates(ringOfOldKeys, ability = 0)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Island")
        resolveAll()
        expect { alice.hand has "Island" }
    }

    @Test
    fun ringOfOldKeysUnlocksADoor() = scenario {
        alice.battlefield(ringOfOldKeys, foyer, "Plains", "Plains", "Plains")
        alice.activates(ringOfOldKeys, ability = 1, target = foyer)
        resolveAll()
        if (pending is Decision.ChooseOption) option("unlock")
        if (pending is Decision.ChooseOption) option("Dim Foyer")
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    @Test
    fun trespassingSoulragerSacrificesARoom() = scenario {
        alice.battlefield(trespassingSoulrager, foyer)
        alice.library(bear)
        alice.activates(trespassingSoulrager, choosing = listOf(foyer))
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.hand has bear
            alice.graveyard has foyer
        }
    }
}
