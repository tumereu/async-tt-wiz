package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The FDN hexproof-from, kicker, mill and morbid cards, played: the invented names carry the oracle text of Elenda, Saint of Dusk,
 * Knight of Grace, Knight of Malice, Divine Resilience, Gnarlid Colony, Grow from the Ashes, Burst Lightning, Gatekeeper of Malakir,
 * Into the Roil, Nullpriest of Oblivion, Rite of Replication, Sun-Blessed Healer, Dread Summons, Billowing Shriekmass, Wardens of
 * the Cycle, Cackling Prowler, Needletooth Pack, Slumbering Cerberus and Tragic Banshee.
 */
class FdnChunk05HexproofKickerMillMorbidScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val bear = Cards.invent("Fenhollow Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Fenhollow Ox", "{2}{G}", "Creature — Ox", "", "2/4")
    private val cull = Cards.invent("Fenhollow Cull", "{B}", "Instant", "Destroy target creature.")
    private val zap = Cards.invent("Fenhollow Zap", "{R}", "Instant", "Fenhollow Zap deals 1 damage to any target.")
    private val hex = Cards.invent("Fenhollow Hex", "{B}", "Instant", "Target creature gets -1/-1 until end of turn.")
    private val blessing = Cards.invent("Fenhollow Blessing", "{W}", "Instant", "Target creature gets +1/+1 until end of turn.")
    private val sting = Cards.invent("Fenhollow Sting", "{R}", "Creature — Goblin", "{T}: This creature deals 4 damage to any target.", "1/1")
    private val vampire = Cards.invent("Fenhollow Vampire", "{B}", "Creature — Vampire", "{T}: Target creature gets -1/-1 until end of turn.", "1/1")
    private val icon = Cards.invent("Fenhollow Icon", "{W}", "Enchantment", "")

    private val verenna = Cards.invent("Verenna, Saint of Vespers", "{2}{W}{B}", "Legendary Creature — Vampire Knight", """
        Lifelink, hexproof from instants
        As long as your life total is greater than your starting life total, Verenna gets +1/+1 and has menace. Verenna gets an additional +5/+5 as long as your life total is at least 10 greater than your starting life total.
    """, "4/4")
    private val mercy = Cards.invent("Paladin of Mercy", "{1}{W}", "Creature — Human Knight", """
        First strike (This creature deals combat damage before creatures without first strike.)
        Hexproof from black (This creature can't be the target of black spells or abilities your opponents control.)
        This creature gets +1/+0 as long as any player controls a black permanent.
    """, "2/2")
    private val spite = Cards.invent("Paladin of Spite", "{1}{B}", "Creature — Human Knight", """
        First strike (This creature deals combat damage before creatures without first strike.)
        Hexproof from white (This creature can't be the target of white spells or abilities your opponents control.)
        This creature gets +1/+0 as long as any player controls a white permanent.
    """, "2/2")
    private val prayer = Cards.invent("Stalwart Prayer", "{W}", "Instant", """
        Kicker {2}{W} (You may pay an additional {2}{W} as you cast this spell.)
        Target creature you control gains indestructible until end of turn. If this spell was kicked, instead any number of target creatures you control gain indestructible until end of turn. (Damage and effects that say "destroy" don't destroy them.)
    """)
    private val herd = Cards.invent("Mossback Herd", "{1}{G}", "Creature — Beast", """
        Kicker {2}{G} (You may pay an additional {2}{G} as you cast this spell.)
        If this creature was kicked, it enters with two +1/+1 counters on it.
        Each creature you control with a +1/+1 counter on it has trample. (It can deal excess combat damage to the player or planeswalker it's attacking.)
    """, "2/2")
    private val seedfall = Cards.invent("Seedfall Rite", "{2}{G}", "Sorcery", """
        Kicker {2} (You may pay an additional {2} as you cast this spell.)
        Search your library for a basic land card, put it onto the battlefield, then shuffle. If this spell was kicked, instead search your library for two basic land cards, put them onto the battlefield, then shuffle.
    """)
    private val spark = Cards.invent("Flash Spark", "{R}", "Instant", """
        Kicker {4} (You may pay an additional {4} as you cast this spell.)
        Flash Spark deals 2 damage to any target. If this spell was kicked, it deals 4 damage instead.
    """)
    private val warden = Cards.invent("Warden of Ashfen", "{B}{B}", "Creature — Vampire Warrior", """
        Kicker {B} (You may pay an additional {B} as you cast this spell.)
        When this creature enters, if it was kicked, target player sacrifices a creature of their choice.
    """, "2/2")
    private val recoil = Cards.invent("Tidal Recoil", "{1}{U}", "Instant", """
        Kicker {1}{U} (You may pay an additional {1}{U} as you cast this spell.)
        Return target nonland permanent to its owner's hand. If this spell was kicked, draw a card.
    """)
    private val acolyte = Cards.invent("Graveborn Acolyte", "{1}{B}", "Creature — Vampire Cleric", """
        Kicker {3}{B} (You may pay an additional {3}{B} as you cast this spell.)
        Lifelink
        Menace (This creature can't be blocked except by two or more creatures.)
        When this creature enters, if it was kicked, return target creature card from your graveyard to the battlefield.
    """, "2/1")
    private val mirror = Cards.invent("Mirror Rite", "{2}{U}{U}", "Sorcery", """
        Kicker {5} (You may pay an additional {5} as you cast this spell.)
        Create a token that's a copy of target creature. If this spell was kicked, create five of those tokens instead.
    """)
    private val medic = Cards.invent("Dawnblessed Medic", "{1}{W}", "Creature — Human Cleric", """
        Kicker {1}{W} (You may pay an additional {1}{W} as you cast this spell.)
        Lifelink (Damage dealt by this creature also causes you to gain that much life.)
        When this creature enters, if it was kicked, return target nonland permanent card with mana value 2 or less from your graveyard to the battlefield.
    """, "3/1")
    private val muster = Cards.invent("Grave Muster", "{X}{B}{B}", "Sorcery",
        "Each player mills X cards. For each creature card put into a graveyard this way, you create a tapped 2/2 black Zombie creature token. (To mill a card, a player puts the top card of their library into their graveyard.)")
    private val wailmass = Cards.invent("Howling Wailmass", "{3}{B}", "Creature — Spirit", """
        Flying
        When this creature enters, mill three cards. (Put the top three cards of your library into your graveyard.)
        Threshold — This creature gets +2/+1 as long as there are seven or more cards in your graveyard.
    """, "2/3")
    private val keepers = Cards.invent("Keepers of the Hollow", "{1}{B}{G}{G}", "Creature — Elf Warlock", """
        Morbid — At the beginning of your end step, if a creature died this turn, choose one —
        • You gain 2 life.
        • You draw a card and you lose 1 life.
    """, "3/4")
    private val stalker = Cards.invent("Snickering Stalker", "{3}{G}", "Creature — Hyena Rogue", """
        Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
        Morbid — At the beginning of your end step, if a creature died this turn, put a +1/+1 counter on this creature.
    """, "4/3")
    private val razorjaw = Cards.invent("Razorjaw Herd", "{3}{G}{G}", "Creature — Dinosaur",
        "Morbid — At the beginning of your end step, if a creature died this turn, put two +1/+1 counters on target creature you control.", "4/5")
    private val hound = Cards.invent("Slumbering Hellhound", "{1}{R}", "Creature — Dog", """
        This creature doesn't untap during your untap step.
        Morbid — At the beginning of each end step, if a creature died this turn, untap this creature.
    """, "4/2")
    private val wraith = Cards.invent("Sorrowful Wraith", "{4}{B}", "Creature — Spirit",
        "Morbid — When this creature enters, target creature an opponent controls gets -1/-1 until end of turn. If a creature died this turn, that creature gets -13/-13 until end of turn instead.", "5/3")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** Alice kills her own creature, so a creature died this turn. */
    private fun Scenario.aCreatureDies() {
        alice.battlefield("Swamp", ox)
        alice.hand(cull)
        alice.casts(cull, target = ox)
        resolveAll()
        expect { alice.graveyard has ox }
    }

    // CR 702.11d: an opponent's instant can't target it
    @Test
    fun verennaCantBeTargetedByAnOpponentsInstant() = scenario {
        alice.battlefield(verenna)
        bob.battlefield("Mountain")
        bob.hand(zap)
        alice.passes()
        assertFailsWith<Throwable> { bob.casts(zap, target = verenna) }
    }

    // CR 702.11d: an ability isn't from an instant source
    @Test
    fun verennaCanBeTargetedByACreaturesAbility() = scenario {
        alice.battlefield(verenna)
        bob.battlefield(sting)
        alice.passes()
        bob.activates(sting, target = verenna)
        resolveAll()
        expect { alice.graveyard has verenna }
    }

    // CR 702.11d: only spells your opponents control
    @Test
    fun verennaCanBeTargetedByItsControllersInstant() = scenario {
        alice.battlefield(verenna, "Mountain")
        alice.hand(zap)
        alice.casts(zap, target = verenna)
        resolveAll()
        expect { alice.graveyard has zap }
    }

    // CR 103.4: your starting life total isn't greater than itself
    @Test
    fun verennaIsPlainAtHerStartingLife() = scenario {
        alice.battlefield(verenna)
        expect {
            verenna.power shouldBe 4
            verenna.toughness shouldBe 4
            verenna lacksKeyword Keyword.Menace
        }
    }

    // CR 103.4, CR 613.4c
    @Test
    fun verennaGrowsAboveHerStartingLife() = scenario {
        alice.battlefield(verenna)
        alice.life = 21
        expect {
            verenna.power shouldBe 5
            verenna.toughness shouldBe 5
            verenna hasKeyword Keyword.Menace
        }
    }

    // CR 613.4c: nine above isn't at least 10 greater
    @Test
    fun verennaHasNoAdditionalPumpAtNineAbove() = scenario {
        alice.battlefield(verenna)
        alice.life = 29
        expect { verenna.power shouldBe 5 }
    }

    // CR 613.4c: both pumps add at 10 above
    @Test
    fun verennaGetsBothPumpsAtTenAbove() = scenario {
        alice.battlefield(verenna)
        alice.life = 30
        expect {
            verenna.power shouldBe 10
            verenna.toughness shouldBe 10
            verenna hasKeyword Keyword.Menace
        }
    }

    // CR 702.11d: an opponent's black spell can't target it
    @Test
    fun paladinOfMercyCantBeTargetedByAnOpponentsBlackSpell() = scenario {
        alice.battlefield(mercy)
        bob.battlefield("Swamp")
        bob.hand(hex)
        alice.passes()
        assertFailsWith<Throwable> { bob.casts(hex, target = mercy) }
    }

    // CR 702.11d: nor an opponent's ability from a black source
    @Test
    fun paladinOfMercyCantBeTargetedByABlackSourcesAbility() = scenario {
        alice.battlefield(mercy)
        bob.battlefield(vampire)
        alice.passes()
        assertFailsWith<Throwable> { bob.activates(vampire, target = mercy) }
    }

    // CR 702.11d: another colour's spell can; so can its controller's black spell
    @Test
    fun paladinOfMercyCanBeTargetedOtherwise() = scenario {
        alice.battlefield(mercy, "Swamp")
        alice.hand(hex)
        alice.casts(hex, target = mercy)
        resolveAll()
        expect { mercy.toughness shouldBe 1 }
    }

    // any player: no black permanent, then the opponent's
    @Test
    fun paladinOfMercyGrowsWithAnOpponentsBlackPermanent() = scenario {
        alice.battlefield(mercy)
        expect { mercy.power shouldBe 2 }
        bob.battlefield(vampire)
        expect {
            mercy.power shouldBe 3
            mercy.toughness shouldBe 2
        }
    }

    // any player: its controller's own black permanent counts
    @Test
    fun paladinOfMercyGrowsWithItsControllersBlackPermanent() = scenario {
        alice.battlefield(mercy, vampire)
        expect { mercy.power shouldBe 3 }
    }

    // CR 702.11d: the white half
    @Test
    fun paladinOfSpiteCantBeTargetedByAnOpponentsWhiteSpellAndGrowsWithWhite() = scenario {
        alice.battlefield(spite)
        bob.battlefield("Plains", icon)
        bob.hand(blessing)
        alice.passes()
        assertFailsWith<Throwable> { bob.casts(blessing, target = spite) }
        expect { spite.power shouldBe 3 }
    }

    // CR 702.33d
    @Test
    fun prayerSavesOneCreatureUnkicked() = scenario {
        alice.battlefield("Plains", bear, ox)
        alice.hand(prayer)
        alice.casts(prayer, target = bear, kicked = 0)
        resolveAll()
        expect {
            bear hasKeyword Keyword.Indestructible
            ox lacksKeyword Keyword.Indestructible
        }
    }

    // CR 702.33d, CR 601.2c: an unkicked spell has one target
    @Test
    fun prayerCantTargetTwoCreaturesUnkicked() = scenario {
        alice.battlefield("Plains", bear, ox)
        alice.hand(prayer)
        alice.casts(prayer, kicked = 0)
        val choice = decision<Decision.Targets>()
        expect {
            choice.min shouldBe 1
            choice.max shouldBe 1
        }
    }

    // CR 702.33d, CR 601.2c: kicked, any number includes none
    @Test
    fun prayerMayTargetNothingKicked() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(prayer)
        alice.casts(prayer, kicked = 1)
        val choice = decision<Decision.Targets>()
        expect {
            choice.min shouldBe 0
            choice.max shouldBe null
        }
    }

    // CR 702.33d, CR 601.2c: kicked, any number
    @Test
    fun prayerSavesEveryChosenCreatureKicked() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear, ox, herd)
        alice.hand(prayer)
        alice.casts(prayer, targets = listOf(bear, ox), kicked = 1)
        resolveAll()
        expect {
            bear hasKeyword Keyword.Indestructible
            ox hasKeyword Keyword.Indestructible
            herd lacksKeyword Keyword.Indestructible
        }
    }

    // CR 702.33d
    @Test
    fun herdIsPlainUnkicked() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(herd)
        alice.casts(herd, kicked = 0)
        resolveAll()
        expect {
            herd.counters(p1p1) shouldBe 0
            herd.power shouldBe 2
            herd lacksKeyword Keyword.Trample
        }
    }

    // CR 702.33d, CR 614.1c: its counters give itself trample too
    @Test
    fun herdEntersWithCountersKicked() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(herd)
        alice.casts(herd, kicked = 1)
        resolveAll()
        expect {
            herd.counters(p1p1) shouldBe 2
            herd.power shouldBe 4
            herd hasKeyword Keyword.Trample
        }
    }

    // each creature you control with a counter, not those without, not an opponent's
    @Test
    fun herdGivesTrampleToYourCreaturesWithCounters() = scenario {
        alice.battlefield(herd, ox)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        bob.battlefield(sting, counters = listOf(p1p1 to 1))
        expect {
            bear hasKeyword Keyword.Trample
            ox lacksKeyword Keyword.Trample
            sting lacksKeyword Keyword.Trample
        }
    }

    // CR 701.23: one land, untapped
    @Test
    fun seedfallFindsOneLandUnkicked() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(seedfall)
        alice.library("Plains", "Island", bear)
        alice.casts(seedfall, kicked = 0)
        resolveAll()
        alice.chooses("Plains")
        resolveAll()
        expect {
            alice.battlefield has "Plains"
            card("Plains").tapped shouldBe false
            alice.library has "Island"
        }
    }

    // CR 702.33d, CR 701.23: two lands, untapped; only basics
    @Test
    fun seedfallFindsTwoLandsKicked() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(seedfall)
        alice.library("Plains", "Island", bear)
        alice.casts(seedfall, kicked = 1)
        resolveAll()
        alice.chooses("Plains", "Island")
        resolveAll()
        expect {
            alice.battlefield has "Plains"
            alice.battlefield has "Island"
            card("Plains").tapped shouldBe false
            card("Island").tapped shouldBe false
            alice.library has bear
        }
    }

    // CR 702.33d
    @Test
    fun sparkDealsTwoUnkicked() = scenario {
        alice.battlefield("Mountain")
        alice.hand(spark)
        alice.casts(spark, target = "bob", kicked = 0)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    // CR 702.33d
    @Test
    fun sparkDealsFourKicked() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(spark)
        alice.casts(spark, target = "bob", kicked = 1)
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    // CR 603.4: no kicker, no trigger
    @Test
    fun wardenAsksNothingUnkicked() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(warden)
        bob.battlefield(bear)
        alice.casts(warden, kicked = 0)
        resolveAll()
        expect {
            alice.battlefield has warden
            bob.battlefield has bear
        }
    }

    // CR 603.4, CR 701.21a: the target player chooses what is sacrificed
    @Test
    fun wardenMakesTheTargetPlayerSacrificeKicked() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(warden)
        bob.battlefield(bear, ox)
        alice.casts(warden, kicked = 1)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        bob.chooses(ox)
        resolveAll()
        expect {
            bob.battlefield has bear
            bob.graveyard has ox
        }
    }

    // CR 702.33d
    @Test
    fun recoilDrawsNothingUnkicked() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(recoil)
        alice.library("Plains")
        bob.battlefield(bear)
        alice.casts(recoil, target = bear, kicked = 0)
        resolveAll()
        expect {
            bob.hand has bear
            alice.hand lacks "Plains"
        }
    }

    // CR 702.33d
    @Test
    fun recoilDrawsKicked() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(recoil)
        alice.library("Plains")
        bob.battlefield(bear)
        alice.casts(recoil, target = bear, kicked = 1)
        resolveAll()
        expect {
            bob.hand has bear
            alice.hand has "Plains"
        }
    }

    // CR 603.4
    @Test
    fun acolyteReturnsNothingUnkicked() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(acolyte)
        alice.graveyard(bear)
        alice.casts(acolyte, kicked = 0)
        resolveAll()
        expect {
            alice.battlefield has acolyte
            alice.graveyard has bear
        }
    }

    // CR 603.4
    @Test
    fun acolyteReturnsACreatureKicked() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(acolyte)
        alice.graveyard(bear, cull)
        alice.casts(acolyte, kicked = 1)
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.graveyard has cull
        }
    }

    // CR 707.2
    @Test
    fun mirrorMakesOneCopyUnkicked() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(mirror)
        bob.battlefield(bear)
        alice.casts(mirror, target = bear, kicked = 0)
        resolveAll()
        expect { alice.battlefield.names.count { it == bear } shouldBe 1 }
    }

    // CR 702.33d: five of those tokens instead, not six
    @Test
    fun mirrorMakesFiveCopiesKicked() = scenario {
        alice.battlefield(*Array(9) { "Island" })
        alice.hand(mirror)
        bob.battlefield(bear)
        alice.casts(mirror, target = bear, kicked = 1)
        resolveAll()
        expect { alice.battlefield.names.count { it == bear } shouldBe 5 }
    }

    // CR 603.4
    @Test
    fun medicReturnsNothingUnkicked() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(medic)
        alice.graveyard(bear)
        alice.casts(medic, kicked = 0)
        resolveAll()
        expect {
            alice.battlefield has medic
            alice.graveyard has bear
        }
    }

    // mana value 2 or less, a permanent, kicked
    @Test
    fun medicReturnsACheapPermanentKicked() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(medic)
        alice.graveyard(bear, ox, "Forest", cull)
        alice.casts(medic, kicked = 1)
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.graveyard has ox
            alice.graveyard has "Forest"
            alice.graveyard has cull
        }
    }

    // CR 701.17, CR 107.3: both players mill X; only the caster's tokens, one for each creature card of either graveyard, tapped
    @Test
    fun musterCountsEveryMilledCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(muster)
        alice.library(bear, "Forest", ox)
        bob.library("Island", sting, "Plains")
        alice.casts(muster, x = 2)
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.graveyard has "Forest"
            alice.library has ox
            bob.graveyard has "Island"
            bob.graveyard has sting
            bob.library has "Plains"
            alice.battlefield.names.count { it == "Zombie Token" } shouldBe 2
            bob.battlefield.names.count { it == "Zombie Token" } shouldBe 0
            card("Zombie Token").tapped shouldBe true
            card("Zombie Token").power shouldBe 2
        }
    }

    // CR 701.17: no creature milled, no token
    @Test
    fun musterMakesNoTokenWithoutACreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(muster)
        alice.library("Forest", ox)
        bob.library("Island", sting)
        alice.casts(muster, x = 1)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Zombie Token" } shouldBe 0 }
    }

    // CR 701.17, threshold: six cards after the mill
    @Test
    fun wailmassIsPlainBelowThreshold() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(wailmass)
        alice.graveyard("Forest", "Forest", "Forest")
        alice.library("Island", "Island", "Island")
        alice.casts(wailmass)
        resolveAll()
        expect {
            alice.graveyard.size shouldBe 6
            wailmass.power shouldBe 2
            wailmass.toughness shouldBe 3
        }
    }

    // threshold: the mill itself reaches seven
    @Test
    fun wailmassReachesThresholdByMilling() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(wailmass)
        alice.graveyard("Forest", "Forest", "Forest", "Forest")
        alice.library("Island", "Island", "Island")
        alice.casts(wailmass)
        allPass()
        expect { wailmass.power shouldBe 2 }
        resolveAll()
        expect {
            alice.graveyard.size shouldBe 7
            wailmass.power shouldBe 4
            wailmass.toughness shouldBe 4
        }
    }

    // CR 603.4: nothing died, no trigger and no choice
    @Test
    fun keepersDoNothingWithoutADeath() = scenario {
        alice.battlefield(keepers)
        alice.library("Island")
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.hand.size shouldBe 0
        }
    }

    // CR 603.4, CR 700.2: first mode
    @Test
    fun keepersGainLifeAfterADeath() = scenario {
        alice.battlefield(keepers)
        aCreatureDies()
        advanceTo(TurnStep.End)
        alice.answers(Answer.Modes(listOf(0)))
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    // CR 700.2: second mode, both of its parts
    @Test
    fun keepersDrawAndLoseLifeAfterADeath() = scenario {
        alice.battlefield(keepers)
        alice.library("Island")
        aCreatureDies()
        advanceTo(TurnStep.End)
        alice.answers(Answer.Modes(listOf(1)))
        resolveAll()
        expect {
            alice.life shouldBe 19
            alice.hand has "Island"
        }
    }

    // CR 603.4: no death, no counter
    @Test
    fun stalkerGetsNoCounterWithoutADeath() = scenario {
        alice.battlefield(stalker)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { stalker.counters(p1p1) shouldBe 0 }
    }

    // CR 603.4
    @Test
    fun stalkerGetsACounterAfterADeath() = scenario {
        alice.battlefield(stalker)
        aCreatureDies()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            stalker.counters(p1p1) shouldBe 1
            stalker.power shouldBe 5
        }
    }

    // CR 603.4, CR 601.2c: two counters on the creature chosen as the trigger goes on the Stack
    @Test
    fun razorjawPutsTwoCountersAfterADeath() = scenario {
        alice.battlefield(razorjaw, bear)
        aCreatureDies()
        advanceTo(TurnStep.End)
        alice.answers(Answer.Targets(listOf(target(bear))))
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 2
            razorjaw.counters(p1p1) shouldBe 0
        }
    }

    // CR 603.4
    @Test
    fun razorjawAsksNothingWithoutADeath() = scenario {
        alice.battlefield(razorjaw, bear)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 0
            razorjaw.counters(p1p1) shouldBe 0
        }
    }

    // CR 502.3: it stays tapped through its controller's untap step when nothing died
    @Test
    fun houndStaysTappedThroughTheUntapStep() = scenario {
        alice.battlefield(hound, tapped = true)
        alice.battlefield("Swamp", tapped = true)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect {
            hound.tapped shouldBe true
            "Swamp".tapped shouldBe false
        }
    }

    // CR 603.4: your end step
    @Test
    fun houndUntapsAtYourEndStepAfterADeath() = scenario {
        alice.battlefield(hound, tapped = true)
        aCreatureDies()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { hound.tapped shouldBe false }
    }

    // "each end step": also the opponent's
    @Test
    fun houndUntapsAtAnOpponentsEndStepAfterADeath() = scenario {
        alice.battlefield(hound, tapped = true)
        bob.battlefield("Swamp", bear)
        bob.hand(cull)
        turn(2, bob, TurnStep.PrecombatMain)
        bob.casts(cull, target = bear)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { hound.tapped shouldBe false }
    }

    // the condition is read as the ability resolves; no death, -1/-1
    @Test
    fun wraithShrinksALittleWithoutADeath() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(wraith)
        bob.battlefield(bear)
        alice.casts(wraith)
        resolveAll()
        expect {
            bear.power shouldBe 1
            bear.toughness shouldBe 1
        }
    }

    // CR 608.2c, CR 704.5f: -13/-13 instead, not both
    @Test
    fun wraithKillsAfterADeath() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(wraith)
        bob.battlefield(sting)
        aCreatureDies()
        alice.casts(wraith)
        resolveAll()
        expect { bob.graveyard has sting }
    }
}
