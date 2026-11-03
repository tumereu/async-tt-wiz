package arcanum.engine.sets.tdm

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tarkir: Dragonstorm cards of parser chunks 01–02 in play: endure (CR 701.63), mobilize (CR 702.181), the clan choices of the Sieges (CR 614.12, 607.2a) and the monuments. Wyrmback Lancer (flying, mobilize 1), Jolt Brigade (menace, mobilize 1), Rootline Tender and Dunecrawl Outrider (endure as they enter) repeat templates played here.
 */
class TdmEndureMobilizeAndClanCardsScenariosTest {
    private val velashaEnduringLineage = Cards.invent("Velasha, Enduring Lineage", "{2}{W}", "Legendary Creature — Spirit Soldier", """
        Flash
        First strike
        Whenever another nontoken creature you control dies, Velasha endures 2. (Put two +1/+1 counters on it or create a 2/2 white Spirit creature token.)
    """, "2/2")
    private val avengerOfTheSlain = Cards.invent("Avenger of the Slain", "{2}{B}", "Creature — Human Warrior", """
        Deathtouch
        Mobilize X, where X is the number of creature cards in your graveyard. (Whenever this creature attacks, create X tapped and attacking 1/1 red Warrior creature tokens. Sacrifice them at the beginning of the next end step.)
    """, "2/4")
    private val ossuaryButcher = Cards.invent("Ossuary Butcher", "{1}{R}{W}{B}", "Creature — Demon", """
        Mobilize 2 (Whenever this creature attacks, create two tapped and attacking 1/1 red Warrior creature tokens. Sacrifice them at the beginning of the next end step.)
        Attacking tokens you control have deathtouch.
    """, "4/4")
    private val steppePackhaulers = Cards.invent("Steppe Packhaulers", "{2}{W}", "Creature — Ox", """
        Vigilance
        Mobilize 3 (Whenever this creature attacks, create three tapped and attacking 1/1 red Warrior creature tokens. Sacrifice them at the beginning of the next end step.)
    """, "0/4")
    private val heirOfTempests = Cards.invent("Heir of Tempests", "{W}", "Creature — Human Soldier", "Whenever this creature attacks, you may pay {1}{W}. If you do, it endures 1. (Put a +1/+1 counter on it or create a 1/1 white Spirit creature token.)", "2/1")
    private val wyrmbackLancer = Cards.invent("Wyrmback Lancer", "{3}{W}", "Creature — Human Soldier", """
        Flying
        Mobilize 1 (Whenever this creature attacks, create a tapped and attacking 1/1 red Warrior creature token. Sacrifice it at the beginning of the next end step.)
    """, "3/3")
    private val dustvaleEarthshaper = Cards.invent("Dustvale Earthshaper", "{5}{G}", "Creature — Elephant Druid", """
        Reach
        When this creature enters, it endures 3. (Put three +1/+1 counters on it or create a 3/3 white Spirit creature token.)
    """, "4/4")
    private val bastionKinWarden = Cards.invent("Bastion Kin-Warden", "{1}{W}", "Creature — Dog Soldier", "When this creature enters, it endures 1. (Put a +1/+1 counter on it or create a 1/1 white Spirit creature token.)", "1/2")
    private val spiritSwornVanguard = Cards.invent("Spirit-Sworn Vanguard", "{4}{G}", "Creature — Human Soldier", "Whenever this creature enters or attacks, it endures 2. (Put two +1/+1 counters on it or create a 2/2 white Spirit creature token.)", "3/2")
    private val rootlineTender = Cards.invent("Rootline Tender", "{2}{B}", "Creature — Human Druid", """
        Lifelink
        When this creature enters, it endures 1. (Put a +1/+1 counter on it or create a 1/1 white Spirit creature token.)
    """, "2/1")
    private val kreshakNovice = Cards.invent("Kreshak Novice", "{1}{B}", "Creature — Human Cleric", "{X}{B}, {T}, Pay X life: This creature endures X. Activate only as a sorcery. (Put X +1/+1 counters on it or create an X/X white Spirit creature token.)", "2/2")
    private val duskbladeCompany = Cards.invent("Duskblade Company", "{2}{B}", "Creature — Goblin Soldier", """
        Deathtouch
        Mobilize 1 (Whenever this creature attacks, create a tapped and attacking 1/1 red Warrior creature token. Sacrifice it at the beginning of the next end step.)
        When this creature enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
    """, "1/3")
    private val crownedVictor = Cards.invent("Crowned Victor", "{2/R}{2/W}{2/B}", "Creature — Orc Warrior", """
        Mobilize 1 (Whenever this creature attacks, create a tapped and attacking 1/1 red Warrior creature token. Sacrifice it at the beginning of the next end step.)
        When this creature enters, target creature gets +1/+0 and gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
    """, "3/3")
    private val dunecrawlOutrider = Cards.invent("Dunecrawl Outrider", "{3}{B}", "Creature — Goblin Soldier", """
        Menace (This creature can't be blocked except by two or more creatures.)
        When this creature enters, it endures 2. (Put two +1/+1 counters on it or create a 2/2 white Spirit creature token.)
    """, "2/1")
    private val joltBrigade = Cards.invent("Jolt Brigade", "{1}{R}", "Creature — Goblin Soldier", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Mobilize 1 (Whenever this creature attacks, create a tapped and attacking 1/1 red Warrior creature token. Sacrifice it at the beginning of the next end step.)
    """, "1/3")
    private val pitfallSurveyor = Cards.invent("Pitfall Surveyor", "{1}{B}", "Creature — Bird Scout", """
        Flying
        Whenever this creature attacks, you lose 1 life and this creature endures 1. (Put a +1/+1 counter on it or create a 1/1 white Spirit creature token.)
    """, "1/3")
    private val arenaHeadliner = Cards.invent("Arena Headliner", "{R}", "Creature — Goblin Warrior", """
        Mobilize 1 (Whenever this creature attacks, create a tapped and attacking 1/1 red Warrior creature token. Sacrifice it at the beginning of the next end step.)
        {1}{R}, Sacrifice this creature: It deals damage equal to the number of creatures you control to target creature.
    """, "1/1")
    private val reveredStormchanter = Cards.invent("Revered Stormchanter", "{3}{B}", "Creature — Orc Cleric", """
        Mobilize 1 (Whenever this creature attacks, create a tapped and attacking 1/1 red Warrior creature token. Sacrifice it at the beginning of the next end step.)
        Whenever this creature or another creature you control dies, each opponent loses 1 life and you gain 1 life.
    """, "3/3")
    private val heraldOfTriumph = Cards.invent("Herald of Triumph", "{1}{W}", "Creature — Human Bard", """
        Mobilize 2 (Whenever this creature attacks, create two tapped and attacking 1/1 red Warrior creature tokens. Sacrifice them at the beginning of the next end step.)
        Your opponents can't cast spells during your turn.
    """, "1/3")
    private val warDrive = Cards.invent("War Drive", "{3}{R}", "Enchantment", """
        Creatures you control get +1/+0.
        Whenever you attack, create a 1/1 red Warrior creature token that's tapped and attacking. Sacrifice it at the beginning of the next end step.
    """)
    private val keeperOfTheThicket = Cards.invent("Keeper of the Thicket", "{2}{G}", "Creature — Hydra", """
        At the beginning of your end step, put a +1/+1 counter on this creature.
        Whenever another nontoken creature you control enters, it endures X, where X is the number of counters on this creature. (Put X +1/+1 counters on the creature that entered or create an X/X white Spirit creature token.)
    """, "2/2")
    private val warchiefsVanguard = Cards.invent("Warchief's Vanguard", "{2}{R}", "Creature — Dog Soldier", """
        Mobilize 1 (Whenever this creature attacks, create a tapped and attacking 1/1 red Warrior creature token. Sacrifice it at the beginning of the next end step.)
        Warchief's Vanguard's power is equal to the number of creatures you control.
    """, "*/3")
    private val ghurakThundersEdict = Cards.invent("Ghurak, Thunder's Edict", "{R}{W}{B}", "Legendary Creature — Orc Warrior", """
        Mobilize 2 (Whenever this creature attacks, create two tapped and attacking 1/1 red Warrior creature tokens. Sacrifice them at the beginning of the next end step.)
        During your end step, Warrior tokens you control have "This token can't be sacrificed."
    """, "2/4")
    private val houselordMonument = Cards.invent("Houselord Monument", "{2}", "Artifact", """
        When this artifact enters, search your library for a basic Plains, Swamp, or Forest card, reveal it, put it into your hand, then shuffle.
        {1}{W}{B}{G}, {T}, Sacrifice this artifact: Create an X/X white Spirit creature token, where X is the greatest toughness among creatures you control. Activate only as a sorcery.
    """)
    private val saltplainSiege = Cards.invent("Saltplain Siege", "{2}{W}{B}", "Enchantment", """
        As this enchantment enters, choose Abzan or Mardu.
        • Abzan — At the beginning of your end step, put a +1/+1 counter on each creature you control.
        • Mardu — At the beginning of your end step, if a creature died under your control this turn, each opponent sacrifices a creature of their choice.
    """)
    private val steppeEncampment = Cards.invent("Steppe Encampment", null, "Land", """
        This land enters tapped unless you control a Swamp or a Mountain.
        {T}: Add {W}.
        {2}{W}, {T}: Whenever you attack this turn, create two 1/1 red Warrior creature tokens that are tapped and attacking. Sacrifice them at the beginning of the next end step.
    """)
    private val rimecliffSiege = Cards.invent("Rimecliff Siege", "{1}{U}{R}", "Enchantment", """
        As this enchantment enters, choose Jeskai or Temur.
        • Jeskai — Whenever one or more creatures you control deal combat damage to a player, draw a card.
        • Temur — Creatures you control get +1/+0 and have trample and haste.
    """)
    private val icebarkSiege = Cards.invent("Icebark Siege", "{1}{G}{U}", "Enchantment", """
        As this enchantment enters, choose Temur or Sultai.
        • Temur — Whenever you cast an instant or sorcery spell, target player mills four cards.
        • Sultai — You may play lands from your graveyard.
    """)
    private val fenhollowSiege = Cards.invent("Fenhollow Siege", "{B}{G}", "Enchantment", """
        As this enchantment enters, choose Sultai or Abzan.
        • Sultai — Whenever a counter is put on a creature you control, draw a card. This ability triggers only once each turn.
        • Abzan — Whenever you attack, put a +1/+1 counter on target attacking creature. It gains menace until end of turn.
    """)
    private val wayfarerMonument = Cards.invent("Wayfarer Monument", "{2}", "Artifact", """
        When this artifact enters, search your library for a basic Island, Mountain, or Plains card, reveal it, put it into your hand, then shuffle.
        {1}{U}{R}{W}, {T}, Sacrifice this artifact: Create two 1/1 white Bird creature tokens with flying. Activate only as a sorcery.
    """)
    private val hordeMonument = Cards.invent("Horde Monument", "{2}", "Artifact", """
        When this artifact enters, search your library for a basic Mountain, Plains, or Swamp card, reveal it, put it into your hand, then shuffle.
        {2}{R}{W}{B}, {T}, Sacrifice this artifact: Create three 1/1 red Warrior creature tokens. They gain menace and haste until end of turn. Activate only as a sorcery. (A creature with menace can't be blocked except by two or more creatures.)
    """)
    private val hordeWallbreaker = Cards.invent("Horde Wallbreaker", "{1}{R}{W}{B}", "Creature — Human Warrior", """
        Deathtouch, haste
        When this creature enters, exile up to one other target creature you control until this creature leaves the battlefield.
        Whenever this creature attacks, for each opponent, create a tapped token that's a copy of the exiled card attacking that opponent. At the beginning of your next end step, sacrifice those tokens.
    """, "4/4")
    private val serpentCourtMonument = Cards.invent("Serpent Court Monument", "{2}", "Artifact", """
        When this artifact enters, search your library for a basic Swamp, Forest, or Island card, reveal it, put it into your hand, then shuffle.
        {2}{B}{G}{U}, {T}, Sacrifice this artifact: Create two 2/2 black Zombie Druid creature tokens. Activate only as a sorcery.
    """)
    private val frontierMonument = Cards.invent("Frontier Monument", "{2}", "Artifact", """
        When this artifact enters, search your library for a basic Forest, Island, or Mountain card, reveal it, put it into your hand, then shuffle.
        {3}{G}{U}{R}, {T}, Sacrifice this artifact: Create a 5/5 green Elephant creature token. Activate only as a sorcery.
    """)
    private val galecragSiege = Cards.invent("Galecrag Siege", "{1}{R}{W}", "Enchantment", """
        As this enchantment enters, choose Mardu or Jeskai.
        • Mardu — If a creature attacking causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.
        • Jeskai — At the beginning of your upkeep, create a 1/1 red Goblin creature token. It gains lifelink and haste until end of turn.
    """)

    private val bear = Plain.bear
    private val ogre = Plain.ogre
    private val giant = Plain.giant
    private val p1p1 = Plain.p1p1

    // ---- Chunk 01: endure and mobilize

    /** Another nontoken creature dies: Velasha endures 2, as two +1/+1 counters. */
    // CR 701.63a
    @Test
    fun velashaEnduresWhenACreatureDies() = scenario {
        alice.battlefield(velashaEnduringLineage, bear, "Swamp")
        alice.hand(Plain.doom)
        alice.casts(Plain.doom, target = bear)
        playOut(options = listOf("+1/+1"))
        expect { velashaEnduringLineage.counters(p1p1) shouldBe 2 }
    }

    /** Enduring as a token instead: a 2/2 white Spirit. */
    // CR 701.63a
    @Test
    fun velashaEnduresAsASpirit() = scenario {
        alice.battlefield(velashaEnduringLineage, bear, "Swamp")
        alice.hand(Plain.doom)
        alice.casts(Plain.doom, target = bear)
        playOut(options = listOf("Spirit"))
        expect {
            "Spirit Token".power shouldBe 2
            velashaEnduringLineage.counters(p1p1) shouldBe 0
        }
    }

    /** Mobilize X: as many attacking Warriors as creature cards in the graveyard, sacrificed at the next end step. */
    // CR 702.181a
    @Test
    fun avengerOfTheSlainMobilizesX() = scenario {
        alice.battlefield(avengerOfTheSlain)
        alice.graveyard(bear, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(avengerOfTheSlain)
        playOut()
        expect {
            card("Warrior Token", 1).tapped shouldBe true
            state.combat!!.attackers.size shouldBe 3
        }
        advanceTo(TurnStep.End)
        playOut()
        expect { alice.battlefield lacks "Warrior Token" }
    }

    /** Mobilize 2, and the attacking tokens have deathtouch. */
    // CR 702.181a, 702.2b
    @Test
    fun ossuaryButcherTokensHaveDeathtouch() = scenario {
        alice.battlefield(ossuaryButcher)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ossuaryButcher)
        playOut()
        expect {
            card("Warrior Token", 1).hasKeyword(Keyword.Deathtouch)
            ossuaryButcher lacksKeyword Keyword.Deathtouch
        }
    }

    /** Mobilize 3 on a vigilant 0/4: three Warriors join the attack. */
    // CR 702.181a
    @Test
    fun steppePackhaulersMobilizeThree() = scenario {
        alice.battlefield(steppePackhaulers)
        bob.library(ogre)
        hits(steppePackhaulers)
        expect {
            bob.life shouldBe 17
            steppePackhaulers.tapped shouldBe false
        }
    }

    /** Attacking, {1}{W} paid: it endures 1. */
    // CR 701.63a, 118.12
    @Test
    fun heirOfTempestsPaysToEndure() = scenario {
        alice.battlefield(heirOfTempests, "Plains", "Plains")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(heirOfTempests)
        playOut(options = listOf("+1/+1"))
        expect { heirOfTempests.counters(p1p1) shouldBe 1 }
    }

    /** Endure 3 as a 3/3 Spirit token. */
    // CR 701.63a
    @Test
    fun dustvaleEarthshaperEnduresThree() = scenario {
        alice.battlefield(*Array(6) { "Forest" })
        alice.hand(dustvaleEarthshaper)
        alice.casts(dustvaleEarthshaper)
        playOut(options = listOf("Spirit"))
        expect {
            "Spirit Token".power shouldBe 3
            "Spirit Token".toughness shouldBe 3
        }
    }

    /** Endure 1 as it enters: a counter. */
    // CR 701.63a
    @Test
    fun bastionKinWardenEnduresOne() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(bastionKinWarden)
        alice.casts(bastionKinWarden)
        playOut(options = listOf("+1/+1"))
        expect { bastionKinWarden.counters(p1p1) shouldBe 1 }
    }

    /** It endures 2 as it enters and again as it attacks. */
    // CR 701.63a
    @Test
    fun spiritSwornVanguardEnduresTwice() = scenario {
        alice.battlefield(*Array(5) { "Forest" })
        alice.hand(spiritSwornVanguard)
        alice.casts(spiritSwornVanguard)
        playOut(options = listOf("+1/+1"))
        expect { spiritSwornVanguard.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun spiritSwornVanguardEnduresAttacking() = scenario {
        alice.battlefield(spiritSwornVanguard)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(spiritSwornVanguard)
        playOut(options = listOf("Spirit"))
        expect { "Spirit Token".power shouldBe 2 }
    }

    /** X life paid: it endures X. */
    // CR 107.3a, 701.63a
    @Test
    fun kreshakNoviceEnduresX() = scenario {
        alice.battlefield(kreshakNovice, "Swamp", "Swamp", "Swamp")
        alice.activates(kreshakNovice, x = 2)
        playOut(options = listOf("+1/+1"))
        expect {
            kreshakNovice.counters(p1p1) shouldBe 2
            alice.life shouldBe 18
        }
    }

    /** Surveil 1 as it enters; mobilize on attack. */
    // CR 701.25a
    @Test
    fun duskbladeCompanySurveils() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(duskbladeCompany)
        alice.library(ogre)
        alice.casts(duskbladeCompany)
        playOut(ogre)
        expect { alice.graveyard has ogre }
    }

    /** As it enters, a creature gets +1/+0 and indestructible. */
    // CR 702.12b
    @Test
    fun crownedVictorEmboldens() = scenario {
        alice.battlefield("Mountain", "Plains", "Swamp", bear)
        alice.hand(crownedVictor)
        alice.casts(crownedVictor)
        playOut(bear)
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Indestructible
        }
    }

    /** Attacking: 1 life lost and it endures 1. */
    // CR 701.63a
    @Test
    fun pitfallSurveyorPaysLifeToEndure() = scenario {
        alice.battlefield(pitfallSurveyor)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(pitfallSurveyor)
        playOut(options = listOf("+1/+1"))
        expect {
            alice.life shouldBe 19
            pitfallSurveyor.counters(p1p1) shouldBe 1
        }
    }

    /** Sacrificed: damage equal to the number of creatures you control (counted as it resolves, without it). */
    // CR 608.2h
    @Test
    fun arenaHeadlinerHitsForTheTeam() = scenario {
        alice.battlefield(arenaHeadliner, bear, ogre, "Mountain", "Mountain")
        bob.battlefield(giant)
        alice.activates(arenaHeadliner, target = giant)
        playOut()
        expect { giant.view.obj!!.damage shouldBe 2 }
    }

    /** A mobilized Warrior sacrificed at the end step drains each opponent. */
    // CR 702.181a, 603.6c
    @Test
    fun reveredStormchanterDrainsOnDeaths() = scenario {
        alice.battlefield(reveredStormchanter)
        alice.library(ogre)
        bob.library(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(reveredStormchanter)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == null) bob.blocks()
        advanceTo(TurnStep.End)
        playOut()
        expect {
            bob.life shouldBe 15
            alice.life shouldBe 21
        }
    }

    /** During its controller's turn, opponents can't cast spells. */
    // CR 101.2
    @Test
    fun heraldOfTriumphSilencesOpponents() = scenario {
        alice.battlefield(heraldOfTriumph)
        alice.hand(Plain.study)
        alice.battlefield("Island")
        bob.battlefield("Mountain")
        bob.hand(Plain.shock)
        alice.library(ogre)
        alice.casts(Plain.study)
        assertFailsWith<Throwable> { bob.casts(Plain.shock, target = "alice") }
    }

    /** +1/+0 for the team, and attacking brings a tapped and attacking Warrior. */
    // CR 508.4
    @Test
    fun warDriveAddsAWarrior() = scenario {
        alice.battlefield(warDrive, bear)
        bob.library(ogre)
        hits(bear)
        expect { bob.life shouldBe 15 }
    }

    /** With two counters on it, a creature entering endures 2; at the end step it grows. */
    // CR 701.63a, 701.63b
    @Test
    fun keeperOfTheThicketMakesArrivalsEndure() = scenario {
        alice.battlefield(keeperOfTheThicket, counters = listOf(p1p1 to 2))
        alice.battlefield("Forest", "Forest")
        alice.hand(bear)
        alice.casts(bear)
        playOut(options = listOf("+1/+1"))
        expect { bear.counters(p1p1) shouldBe 2 }
        advanceTo(TurnStep.End)
        playOut()
        expect { keeperOfTheThicket.counters(p1p1) shouldBe 3 }
    }

    /** With no counters on it, enduring 0 does nothing (no choice, no token). */
    // CR 701.63b
    @Test
    fun keeperOfTheThicketEnduresZero() = scenario {
        alice.battlefield(keeperOfTheThicket, "Forest", "Forest")
        alice.hand(bear)
        alice.casts(bear)
        playOut()
        expect {
            alice.battlefield lacks "Spirit Token"
            bear.counters(p1p1) shouldBe 0
        }
    }

    /** Its power is the number of creatures you control, Warriors included. */
    // CR 604.3, 208.2
    @Test
    fun warchiefsVanguardCountsCreatures() = scenario {
        alice.battlefield(warchiefsVanguard, bear)
        expect { warchiefsVanguard.power shouldBe 2 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(warchiefsVanguard)
        playOut()
        expect { warchiefsVanguard.power shouldBe 3 }
    }

    /** During its controller's end step its Warrior tokens can't be sacrificed: mobilized Warriors stay. */
    // CR 701.21a
    @Test
    fun ghurakKeepsTheWarriors() = scenario {
        alice.battlefield(ghurakThundersEdict)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ghurakThundersEdict)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == null) bob.blocks()
        advanceTo(TurnStep.End)
        playOut()
        expect { card("Warrior Token", 1).zone shouldBe Zone.Battlefield }
    }

    // ---- Chunk 02: clans and monuments

    /** A basic land of its types to the hand; later, an X/X Spirit with the greatest toughness. */
    // CR 701.23a
    @Test
    fun houselordMonumentFetchesAndMakesASpirit() = scenario {
        alice.battlefield("Plains", "Swamp", "Forest", "Forest", giant)
        alice.hand(houselordMonument)
        alice.library("Island", "Swamp")
        alice.casts(houselordMonument, paying = listOf("Forest", "Forest#1"))
        playOut("Swamp")
        expect { alice.hand has "Swamp" }
    }

    @Test
    fun houselordMonumentMakesAnXSpirit() = scenario {
        alice.battlefield(houselordMonument, giant, "Plains", "Swamp", "Forest", "Forest")
        alice.activates(houselordMonument)
        playOut()
        expect { "Spirit Token".toughness shouldBe 5 }
    }

    /** Abzan chosen: a counter on each creature at the end step; the Mardu ability does nothing then. */
    // CR 614.12, 607.2a
    @Test
    fun saltplainSiegeAbzanCounters() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", bear)
        alice.hand(saltplainSiege)
        alice.casts(saltplainSiege)
        playOut()
        option("Abzan")
        playOut()
        advanceTo(TurnStep.End)
        playOut()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    /** Mardu chosen: after a death, each opponent sacrifices a creature at the end step. */
    // CR 701.21a
    @Test
    fun saltplainSiegeMarduSacrifices() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(saltplainSiege, Plain.doom)
        bob.battlefield(ogre)
        alice.casts(saltplainSiege, paying = listOf("Plains", "Plains#1", "Swamp", "Swamp#1"))
        playOut()
        option("Mardu")
        playOut()
        alice.casts(Plain.doom, target = bear)
        playOut()
        advanceTo(TurnStep.End)
        playOut(ogre)
        expect {
            bob.graveyard has ogre
            alice.battlefield lacks "Steppe Bear"
        }
    }

    /** Enters tapped without a Swamp or Mountain; its ability makes two attacking Warriors when you attack. */
    // CR 614.1c, 603.7c
    @Test
    fun steppeEncampmentRaises() = scenario {
        alice.battlefield(steppeEncampment, "Plains", "Plains", "Plains", bear)
        bob.library(ogre)
        alice.activates(steppeEncampment, ability = 1)
        playOut()
        hits(bear)
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun steppeEncampmentEntersTapped() = scenario {
        alice.hand(steppeEncampment)
        alice.plays(steppeEncampment)
        expect { steppeEncampment.tapped shouldBe true }
    }

    /** Temur: +1/+0, trample and haste; Jeskai: a card when creatures hit a player. */
    // CR 613.4c
    @Test
    fun rimecliffSiegeTemurHastens() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain")
        alice.battlefield(bear, sick = true)
        alice.hand(rimecliffSiege)
        alice.casts(rimecliffSiege)
        playOut()
        option("Temur")
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Haste
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun rimecliffSiegeJeskaiDraws() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain", bear)
        alice.hand(rimecliffSiege)
        alice.library(ogre)
        alice.casts(rimecliffSiege)
        playOut()
        option("Jeskai")
        hits(bear)
        expect { alice.hand has ogre }
    }

    /** Temur: an instant or sorcery makes a player mill four; Sultai: lands played from the graveyard. */
    // CR 305.1
    @Test
    fun icebarkSiegeTemurMills() = scenario {
        alice.battlefield("Forest", "Island", "Island", "Island")
        alice.hand(icebarkSiege, Plain.study)
        alice.library(ogre)
        bob.library(ogre, ogre, ogre, ogre, ogre)
        alice.casts(icebarkSiege, paying = listOf("Forest", "Island", "Island#1"))
        playOut()
        option("Temur")
        alice.casts(Plain.study)
        playOut("bob")
        expect { bob.graveyard.size shouldBe 4 }
    }

    @Test
    fun icebarkSiegeSultaiPlaysLandsFromTheGraveyard() = scenario {
        alice.battlefield("Forest", "Island", "Island")
        alice.hand(icebarkSiege)
        alice.graveyard("Swamp")
        alice.casts(icebarkSiege)
        playOut()
        option("Sultai")
        alice.plays("Swamp")
        expect { card("Swamp").zone shouldBe Zone.Battlefield }
    }

    /** Sultai: the first counter on a creature each turn draws a card; Abzan: attacking gives a counter and menace. */
    // CR 603.2i
    @Test
    fun fenhollowSiegeSultaiDraws() = scenario {
        alice.battlefield("Swamp", "Forest", "Forest", "Forest", bear, ogre)
        alice.hand(fenhollowSiege, Plain.growth, Plain.growth)
        alice.library(giant, giant)
        alice.casts(fenhollowSiege, paying = listOf("Swamp", "Forest"))
        playOut()
        option("Sultai")
        alice.casts(Plain.growth, target = bear, paying = listOf("Forest#1"))
        playOut()
        alice.casts(Plain.growth, target = ogre, paying = listOf("Forest#2"))
        playOut()
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun fenhollowSiegeAbzanEmpowersAnAttacker() = scenario {
        alice.battlefield("Swamp", "Forest", bear)
        alice.hand(fenhollowSiege)
        alice.casts(fenhollowSiege)
        playOut()
        option("Abzan")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        playOut(bear)
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Menace
        }
    }

    /** Sacrificed: two flying Birds. */
    // CR 111.1
    @Test
    fun wayfarerMonumentMakesBirds() = scenario {
        alice.battlefield(wayfarerMonument, "Plains", "Island", "Mountain", "Mountain")
        alice.activates(wayfarerMonument)
        playOut()
        expect {
            card("Bird Token", 1).zone shouldBe Zone.Battlefield
            "Bird Token" hasKeyword Keyword.Flying
        }
    }

    /** Sacrificed: three Warriors with menace and haste. */
    // CR 111.1
    @Test
    fun hordeMonumentRaisesWarriors() = scenario {
        alice.battlefield(hordeMonument, "Plains", "Swamp", "Mountain", "Mountain", "Mountain")
        alice.activates(hordeMonument)
        playOut()
        expect {
            card("Warrior Token", 2).zone shouldBe Zone.Battlefield
            "Warrior Token" hasKeyword Keyword.Haste
            "Warrior Token" hasKeyword Keyword.Menace
        }
    }

    /** Sacrificed: two 2/2 Zombie Druids; the Frontier Monument a 5/5 Elephant. */
    // CR 111.1
    @Test
    fun serpentCourtMonumentMakesZombies() = scenario {
        alice.battlefield(serpentCourtMonument, "Swamp", "Forest", "Island", "Island", "Island")
        alice.activates(serpentCourtMonument)
        playOut()
        expect { card("Zombie Druid Token", 1).zone shouldBe Zone.Battlefield }
    }

    @Test
    fun frontierMonumentMakesAnElephant() = scenario {
        alice.battlefield(frontierMonument, "Forest", "Island", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.activates(frontierMonument)
        playOut()
        expect { "Elephant Token".power shouldBe 5 }
    }

    /** A creature exiled as it enters; attacking, a tapped token copy of it attacks the opponent and is sacrificed at the end step. */
    // CR 610.3, 707.2, 508.4
    @Test
    fun hordeWallbreakerCopiesTheExiledCreature() = scenario {
        alice.battlefield("Mountain", "Plains", "Swamp", "Swamp", giant)
        alice.hand(hordeWallbreaker)
        bob.library(ogre)
        alice.casts(hordeWallbreaker)
        playOut(giant)
        expect { alice.exile has giant }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hordeWallbreaker)
        playOut()
        expect {
            card(giant, 1).zone shouldBe Zone.Battlefield
            card(giant, 1).tapped shouldBe true
        }
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == null) bob.blocks()
        advanceTo(TurnStep.CombatDamage)
        playOut()
        expect { bob.life shouldBe 11 }
    }

    /** Jeskai: an upkeep Goblin with lifelink and haste. */
    // CR 503.1a
    @Test
    fun galecragSiegeJeskaiGoblins() = scenario {
        alice.battlefield("Mountain", "Plains", "Plains")
        alice.hand(galecragSiege)
        alice.library(ogre, ogre)
        bob.library(ogre, ogre)
        alice.casts(galecragSiege)
        playOut()
        option("Jeskai")
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut()
        expect {
            "Goblin Token" hasKeyword Keyword.Haste
            "Goblin Token" hasKeyword Keyword.Lifelink
        }
    }

    /** Mardu: an attack trigger triggers twice. */
    // CR 603.2d
    @Test
    fun galecragSiegeMarduDoublesAttackTriggers() = scenario {
        alice.battlefield("Mountain", "Plains", "Plains", steppePackhaulers)
        alice.hand(galecragSiege)
        alice.casts(galecragSiege)
        playOut()
        option("Mardu")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(steppePackhaulers)
        playOut()
        expect { card("Warrior Token", 5).zone shouldBe Zone.Battlefield }
    }
}
