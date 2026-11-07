package arcanum.engine.sets.rtr

import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.Rtr.bear
import arcanum.engine.sets.rtr.Rtr.flyer
import arcanum.engine.sets.rtr.Rtr.giant
import arcanum.engine.sets.rtr.Rtr.human
import arcanum.engine.sets.rtr.Rtr.idol
import arcanum.engine.sets.rtr.Rtr.ogre
import arcanum.engine.sets.rtr.Rtr.ponder
import arcanum.engine.sets.rtr.Rtr.shock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Return to Ravnica's detain, populate and overload cards and other Azorius, Selesnya and Izzet cards in play (parser chunks 01–03), beyond the mechanics played in [RtrMechanicsScenariosTest]. */
class RtrAzoriusSelesnyaIzzetCardsScenariosTest {
    private val precinctWarden = Cards.invent("Precinct Warden", "{1}{W}", "Creature — Human Soldier", "When this creature enters, detain target creature an opponent controls. (Until your next turn, that creature can't attack or block and its activated abilities can't be activated.)", "2/1")
    private val tribunalAdjudicator = Cards.invent("Tribunal Adjudicator", "{2}{W}{W}", "Creature — Human Wizard", "When this creature enters, detain up to two target creatures your opponents control. (Until your next turn, those creatures can't attack or block and their activated abilities can't be activated.)", "2/3")
    private val curfewDecree = Cards.invent("Curfew Decree", "{2}{W}{W}", "Enchantment", "At the beginning of your upkeep, detain target creature an opponent controls. (Until your next turn, that creature can't attack or block and its activated abilities can't be activated.)")
    private val skyCourtSentinel = Cards.invent("Sky Court Sentinel", "{5}{U}", "Creature — Vedalken Knight", """
        Flying
        When this creature enters, detain target creature an opponent controls. (Until your next turn, that creature can't attack or block and its activated abilities can't be activated.)
    """, "3/3")
    private val oathboundShade = Cards.invent("Oathbound Shade", "{3}{U}", "Creature — Spirit", """
        This creature can't be blocked.
        When this creature enters, detain target creature an opponent controls. (Until your next turn, that creature can't attack or block and its activated abilities can't be activated.)
    """, "2/2")
    private val lawwingKnight = Cards.invent("Lawwing Knight", "{1}{W}{U}", "Creature — Human Knight", """
        Flying
        When this creature enters, detain target nonland permanent an opponent controls. (Until your next turn, that permanent can't attack or block and its activated abilities can't be activated.)
    """, "3/1")
    private val senateClerkMage = Cards.invent("Senate Clerk-Mage", "{W}{U}", "Creature — Human Wizard", """
        {W}{U}: Target creature gains flying until end of turn.
        {3}{W}{U}: Detain target nonland permanent an opponent controls. (Until your next turn, that permanent can't attack or block and its activated abilities can't be activated.)
    """, "2/2")
    private val archonOfTheNinefoldSeal = Cards.invent("Archon of the Ninefold Seal", "{5}{W}{U}", "Creature — Archon", """
        Flying
        Whenever this creature attacks, detain up to two target nonland permanents your opponents control. (Until your next turn, those permanents can't attack or block and their activated abilities can't be activated.)
    """, "4/5")
    private val lawfulShackles = Cards.invent("Lawful Shackles", "{2}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature can't attack or block, and its activated abilities can't be activated.
    """)
    private val senateSigil = Cards.invent("Senate Sigil", "{W}{U}", "Instant", """
        Choose one —
        • Creatures you control gain lifelink until end of turn.
        • Draw a card.
        • Put target attacking or blocking creature on top of its owner's library.
    """)
    private val writOfInspection = Cards.invent("Writ of Inspection", "{W}{U}", "Sorcery", "Target player reveals their hand. You gain life equal to the number of cards in that player's hand.")
    private val mandateOfTheBench = Cards.invent("Mandate of the Bench", "{3}{W}{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+1 for each card in its controller's hand.
        At the beginning of the draw step of enchanted creature's controller, that player draws an additional card.
    """)
    private val valeriaArbiterOfSky = Cards.invent("Valeria, Arbiter of Sky", "{2}{W}{W}{U}{U}", "Legendary Creature — Sphinx", """
        Flying
        Whenever a creature attacks you or a planeswalker you control, you may draw a card.
    """, "6/6")
    private val abruptPardon = Cards.invent("Abrupt Pardon", "{W}{U}", "Instant", "Return target creature to its owner's hand. You gain 2 life.")
    private val gavelsVerdict = Cards.invent("Gavel's Verdict", "{3}{W}{U}", "Instant", "Counter target spell. You gain 5 life.")
    private val docketOwl = Cards.invent("Docket Owl", "{W/U}", "Creature — Bird", """
        Flying
        Sacrifice this creature: Counter target instant or sorcery spell unless its controller pays {1}.
    """, "1/1")
    private val cloudmarkCondor = Cards.invent("Cloudmark Condor", "{2}{W}{U}", "Creature — Bird", """
        Flying
        Whenever this creature attacks, you may return target creature defending player controls with toughness 2 or less to its owner's hand.
    """, "2/2")
    private val sphinxsDisclosure = Cards.invent("Sphinx's Disclosure", "{X}{W}{U}{U}", "Instant", "You gain X life and draw X cards.")
    private val unanimousSentence = Cards.invent("Unanimous Sentence", "{1}{W}{W}{U}", "Sorcery", """
        This spell can't be countered.
        Destroy all creatures.
    """)
    private val matriarchsRuling = Cards.invent("Matriarch's Ruling", "{5}{W}", "Instant", "Exile target creature, then populate. (Create a token that's a copy of a creature token you control.)")
    private val grovekeepersShelter = Cards.invent("Grovekeeper's Shelter", "{1}{G}", "Instant", "Prevent all combat damage that would be dealt to you this turn. Populate. (Create a token that's a copy of a creature token you control.)")
    private val hornsingersCall = Cards.invent("Hornsinger's Call", "{7}{G}", "Sorcery", "Create a 4/4 green Rhino creature token with trample, then populate. (Create a token that's a copy of a creature token you control.)")
    private val gallopingPact = Cards.invent("Galloping Pact", "{4}{G}{W}", "Sorcery", "Create a 3/3 green Centaur creature token, then populate. (Create a token that's a copy of a creature token you control.)")
    private val swellingConclave = Cards.invent("Swelling Conclave", "{2}{G/W}{G/W}", "Enchantment", "At the beginning of your upkeep, populate. (Create a token that's a copy of a creature token you control.)")
    private val splitrootBloom = Cards.invent("Splitroot Bloom", "{G/W}{G/W}", "Instant", "Destroy target artifact or enchantment, then populate. (Create a token that's a copy of a creature token you control.)")
    private val ysoldeConclaveChorus = Cards.invent("Ysolde, Conclave Chorus", "{G}{G}{W}{W}", "Legendary Creature — Dryad", """
        Whenever another creature you control enters, you gain life equal to that creature's toughness.
        {1}{G}{W}, {T}: Populate. (Create a token that's a copy of a creature token you control.)
    """, "2/5")
    private val treehallSpeaker = Cards.invent("Treehall Speaker", "{G}{W}", "Creature — Dryad Shaman", """
        {4}{G}{W}: Create a 3/3 green Centaur creature token.
        {2}{G}{W}: Populate. (Create a token that's a copy of a creature token you control.)
    """, "2/2")
    private val wanderingSanctum = Cards.invent("Wandering Sanctum", "{1}{G}{W}", "Creature — Elemental", """
        Wandering Sanctum's power and toughness are each equal to the number of creatures you control.
        Whenever this creature deals combat damage to a player, populate. (Create a token that's a copy of a creature token you control.)
    """, "*/*")
    private val kinsfolkGathering = Cards.invent("Kinsfolk Gathering", "{G}{W}", "Instant", "Target player gains 7 life.")
    private val conclaveSigil = Cards.invent("Conclave Sigil", "{G}{W}", "Instant", """
        Choose one —
        • Target creature gets +2/+2 and gains trample until end of turn.
        • Exile target creature with power 5 or greater.
        • Create a 2/2 white Knight creature token with vigilance.
    """)
    private val groveMilitia = Cards.invent("Grove Militia", "{G/W}", "Creature — Dryad Soldier", """
        ({G/W} can be paid with either {G} or {W}.)
        If an instant or sorcery card would be put into a graveyard from anywhere, exile it instead.
    """, "2/1")
    private val communalGrace = Cards.invent("Communal Grace", "{3}{G}{G}{W}", "Enchantment", "Creatures you control get +3/+3.")
    private val flotillaWurm = Cards.invent("Flotilla Wurm", "{2}{G}{G}{W}{W}", "Creature — Wurm", """
        Trample
        When this creature enters, create a 5/5 green Wurm creature token with trample.
    """, "5/5")
    private val summonsOfTheGrove = Cards.invent("Summons of the Grove", "{G}{W}", "Sorcery", "Create a 3/3 green Centaur creature token.")
    private val centaurMender = Cards.invent("Centaur Mender", "{1}{G}{W}", "Creature — Centaur Cleric", "When this creature enters, you gain 3 life.", "3/3")
    private val galeburst = Cards.invent("Galeburst", "{U}", "Instant", """
        Tap target creature you don't control.
        Overload {3}{U} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val shrinkingSpell = Cards.invent("Shrinking Spell", "{U}", "Instant", """
        Target creature you don't control gets -4/-0 until end of turn.
        Overload {2}{U} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val alloyhideWard = Cards.invent("Alloyhide Ward", "{U}", "Instant", """
        Target creature you control gets +0/+1 and gains hexproof until end of turn.
        Overload {1}{U} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val overchargePulse = Cards.invent("Overcharge Pulse", "{R}", "Instant", """
        Target creature you control gets +2/+0 until end of turn.
        Overload {2}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val arcLattice = Cards.invent("Arc Lattice", "{R}", "Instant", """
        Arc Lattice deals 1 damage to target creature you don't control.
        Overload {1}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val cobblestoneQuake = Cards.invent("Cobblestone Quake", "{X}{R}", "Instant", """
        Cobblestone Quake deals X damage to target creature without flying you don't control.
        Overload {X}{X}{R}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val scrapwreck = Cards.invent("Scrapwreck", "{R}", "Sorcery", """
        Destroy target artifact you don't control.
        Overload {4}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val alchemistsFeint = Cards.invent("Alchemist's Feint", "{U}{R}", "Instant", """
        Target creature you don't control gets -2/-0 until end of turn and attacks this turn if able.
        Overload {3}{U}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val reversalCurrent = Cards.invent("Reversal Current", "{U}{U}{R}", "Instant", """
        This spell can't be countered.
        Counter target spell you don't control.
        Overload {1}{U}{U}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val blinkgate = Cards.invent("Blinkgate", "{U}{R}", "Sorcery", """
        Target creature you control gets +1/+0 until end of turn and can't be blocked this turn.
        Overload {3}{U}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val lifeforceRecoil = Cards.invent("Lifeforce Recoil", "{2}{U}{R}", "Instant", "Counter target creature spell. Lifeforce Recoil deals damage equal to that spell's power to its controller.")
    private val laboratorySigil = Cards.invent("Laboratory Sigil", "{U}{R}", "Instant", """
        Choose one —
        • Counter target noncreature spell unless its controller pays {2}.
        • Laboratory Sigil deals 2 damage to target creature.
        • Draw two cards, then discard two cards.
    """)
    private val offbeatDenial = Cards.invent("Offbeat Denial", "{X}{U}", "Instant", "Counter target spell unless its controller pays {X}. If that spell is countered this way, exile it instead of putting it into its owner's graveyard.")
    private val rats = Cards.invent("Tenth Street Rats", "{1}{G}", "Sorcery", "Create two 1/1 black Rat creature tokens.")
    private val pinger = Cards.invent("Tenth Street Pinger", "{1}{R}", "Creature — Human Wizard", "{T}: This creature deals 1 damage to any target.", "1/1")

    /** [name] can't attack or block and its activated abilities can't be activated (CR 701.35a). */
    private fun Scenario.detained(name: String) = expect {
        name hasMechanic { it is Mechanic.CantAttack }
        name hasMechanic { it is Mechanic.CantBlock }
    }

    // ---- Chunk 01: detain and Azorius (detain itself: RtrMechanicsScenariosTest)

    @Test
    fun precinctWardenDetains() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(precinctWarden)
        bob.battlefield(ogre)
        alice.casts(precinctWarden)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        detained(ogre)
    }

    @Test
    fun tribunalAdjudicatorDetainsTwo() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(tribunalAdjudicator)
        bob.battlefield(ogre, giant)
        alice.casts(tribunalAdjudicator)
        resolveAll()
        targets(ogre, giant)
        resolveAll()
        detained(ogre)
        detained(giant)
    }

    @Test
    fun curfewDecreeDetainsEachUpkeep() = scenario {
        alice.battlefield(curfewDecree)
        alice.library(bear)
        bob.battlefield(ogre)
        bob.library(bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        targetsIfAsked(ogre)
        resolveAll()
        detained(ogre)
    }

    @Test
    fun skyCourtSentinelDetains() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(skyCourtSentinel)
        bob.battlefield(ogre)
        alice.casts(skyCourtSentinel)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        detained(ogre)
    }

    @Test
    fun oathboundShadeDetainsAndCantBeBlocked() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(oathboundShade)
        bob.battlefield(ogre)
        alice.casts(oathboundShade)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        detained(ogre)
    }

    // CR 701.35a: a nonland permanent's activated abilities can't be activated
    @Test
    fun lawwingKnightDetainsAPermanent() = scenario {
        alice.battlefield("Plains", "Island", "Island")
        alice.hand(lawwingKnight)
        bob.battlefield(pinger)
        alice.casts(lawwingKnight)
        resolveAll()
        targetsIfAsked(pinger)
        resolveAll()
        expect { pinger hasMechanic { it is Mechanic.CantBeActivated } }
    }

    @Test
    fun senateClerkMageGivesFlyingAndDetains() = scenario {
        alice.battlefield(senateClerkMage, bear, "Plains", "Island", "Plains", "Island", "Plains", "Island", "Plains")
        bob.battlefield(ogre)
        alice.activates(senateClerkMage, ability = 0, target = bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Flying }
        alice.activates(senateClerkMage, ability = 1, target = ogre)
        resolveAll()
        detained(ogre)
    }

    @Test
    fun archonOfTheNinefoldSealDetainsWhenAttacking() = scenario {
        alice.battlefield(archonOfTheNinefoldSeal)
        bob.battlefield(ogre, giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(archonOfTheNinefoldSeal)
        targets(ogre, giant)
        resolveAll()
        detained(ogre)
        detained(giant)
    }

    @Test
    fun lawfulShacklesLocksACreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(lawfulShackles)
        bob.battlefield(pinger)
        alice.casts(lawfulShackles, target = pinger)
        resolveAll()
        detained(pinger)
    }

    @Test
    fun senateSigilTopsABlocker() = scenario {
        alice.battlefield("Plains", "Island", bear)
        alice.hand(senateSigil)
        bob.battlefield(ogre)
        bob.library(giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(ogre to bear)
        alice.casts(senateSigil, modes = listOf(2), target = ogre)
        resolveAll()
        expect { bob.library.names.first() shouldBe ogre }
    }

    @Test
    fun writOfInspectionGainsByTheHand() = scenario {
        alice.battlefield("Plains", "Island")
        alice.hand(writOfInspection)
        bob.hand(ogre, giant, bear)
        alice.casts(writOfInspection, target = "bob")
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    @Test
    fun mandateOfTheBenchCountsTheHandAndDrawsMore() = scenario {
        alice.battlefield("Plains", "Plains", "Island", "Island", "Island", bear)
        alice.hand(mandateOfTheBench, ogre, giant)
        alice.library(human, ponder, pinger)
        bob.library(ogre)
        alice.casts(mandateOfTheBench, target = bear)
        resolveAll()
        expect { bear.power shouldBe 4 }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        expect { alice.hand.size shouldBe 4 }
    }

    @Test
    fun valeriaDrawsWhenAttacked() = scenario {
        alice.battlefield(valeriaArbiterOfSky)
        alice.library(bear)
        bob.battlefield(ogre)
        bob.library(giant)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(ogre)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun abruptPardonBouncesAndGains() = scenario {
        alice.battlefield("Plains", "Island")
        alice.hand(abruptPardon)
        bob.battlefield(ogre)
        alice.casts(abruptPardon, target = ogre)
        resolveAll()
        expect {
            bob.hand has ogre
            alice.life shouldBe 22
        }
    }

    @Test
    fun gavelsVerdictCountersAndGains() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        alice.battlefield("Plains", "Plains", "Plains", "Island", "Island")
        alice.hand(gavelsVerdict)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(gavelsVerdict, target = ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.life shouldBe 25
        }
    }

    @Test
    fun docketOwlCountersAnUnpaidSpell() = scenario {
        bob.battlefield("Island")
        bob.hand(ponder)
        bob.library(ogre)
        alice.battlefield(docketOwl)
        turn(active = bob)
        bob.casts(ponder)
        bob.passes()
        alice.activates(docketOwl, target = ponder)
        resolveAll()
        if (pending is Decision.May) bob.answers(Answer.YesNo(false))
        resolveAll()
        expect { bob.graveyard has ponder }
    }

    @Test
    fun cloudmarkCondorBouncesASmallDefender() = scenario {
        alice.battlefield(cloudmarkCondor)
        bob.battlefield(human)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cloudmarkCondor)
        targetsIfAsked(human)
        resolveAll()
        may(true)
        resolveAll()
        expect { bob.hand has human }
    }

    @Test
    fun sphinxsDisclosureGainsAndDraws() = scenario {
        alice.battlefield("Plains", "Island", "Island", "Island", "Island")
        alice.hand(sphinxsDisclosure)
        alice.library(bear, ogre)
        alice.casts(sphinxsDisclosure, x = 2)
        resolveAll()
        expect {
            alice.life shouldBe 22
            alice.hand.names shouldBe listOf(bear, ogre)
        }
    }

    // CR 101.2, 701.6: can't be countered
    @Test
    fun unanimousSentenceWipesAndCantBeCountered() = scenario {
        alice.battlefield("Plains", "Plains", "Island", "Island", bear)
        alice.hand(unanimousSentence)
        bob.battlefield(ogre)
        alice.casts(unanimousSentence)
        resolveAll()
        expect {
            alice.graveyard has bear
            bob.graveyard has ogre
        }
    }

    // ---- Chunk 02: populate and Selesnya (populate itself: RtrMechanicsScenariosTest)

    // CR 701.36a: populate copies a creature token you control
    @Test
    fun matriarchsRulingExilesAndPopulates() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Forest", "Forest")
        alice.hand(rats, matriarchsRuling)
        bob.battlefield(giant)
        alice.casts(rats, paying = listOf("Forest", "Forest#1"))
        resolveAll()
        alice.casts(matriarchsRuling, target = giant)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Rat Token")
        resolveAll()
        expect {
            bob.exile has giant
            alice.battlefield.names.count { it == "Rat Token" } shouldBe 3
        }
    }

    // CR 615.1: preventing combat damage
    @Test
    fun grovekeepersShelterPreventsCombatDamage() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(grovekeepersShelter)
        bob.battlefield(ogre)
        bob.library(giant)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(ogre)
        bob.passes()
        alice.casts(grovekeepersShelter)
        resolveAll()
        advanceTo(TurnStep.EndOfCombat)
        expect { alice.life shouldBe 20 }
    }

    @Test
    fun hornsingersCallMakesTwoRhinos() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(hornsingersCall)
        alice.casts(hornsingersCall)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Rhino Token" } shouldBe 2
            "Rhino Token" hasKeyword Keyword.Trample
        }
    }

    @Test
    fun gallopingPactMakesTwoCentaurs() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Plains", "Plains")
        alice.hand(gallopingPact)
        alice.casts(gallopingPact)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Centaur Token" } shouldBe 2 }
    }

    @Test
    fun swellingConclavePopulatesEachUpkeep() = scenario {
        alice.battlefield("Forest", "Forest", swellingConclave)
        alice.hand(rats)
        alice.library(bear)
        bob.library(bear)
        alice.casts(rats)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Rat Token")
        resolveAll()
        expect { alice.battlefield.names.count { it == "Rat Token" } shouldBe 3 }
    }

    @Test
    fun splitrootBloomDestroysAndPopulates() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(rats, splitrootBloom)
        bob.battlefield(idol)
        alice.casts(rats, paying = listOf("Forest", "Forest#1"))
        resolveAll()
        alice.casts(splitrootBloom, target = idol)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Rat Token")
        resolveAll()
        expect {
            bob.graveyard has idol
            alice.battlefield.names.count { it == "Rat Token" } shouldBe 3
        }
    }

    @Test
    fun ysoldeGainsByToughnessAndPopulates() = scenario {
        alice.battlefield(ysoldeConclaveChorus, "Forest", "Forest", "Forest", "Forest", "Plains")
        alice.hand(rats)
        alice.casts(rats, paying = listOf("Forest", "Forest#1"))
        resolveAll()
        expect { alice.life shouldBe 22 }
        alice.activates(ysoldeConclaveChorus, paying = listOf("Forest#2", "Forest#3", "Plains"))
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Rat Token")
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Rat Token" } shouldBe 3
            alice.life shouldBe 23
        }
    }

    @Test
    fun treehallSpeakerMakesAndCopiesCentaurs() = scenario {
        alice.battlefield(treehallSpeaker, "Forest", "Forest", "Forest", "Forest", "Forest", "Plains", "Forest", "Forest", "Forest", "Plains")
        alice.activates(treehallSpeaker, ability = 0)
        resolveAll()
        alice.activates(treehallSpeaker, ability = 1)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Centaur Token" } shouldBe 2 }
    }

    @Test
    fun wanderingSanctumCountsAndPopulates() = scenario {
        alice.battlefield("Forest", "Forest", wanderingSanctum, bear)
        alice.hand(rats)
        alice.casts(rats)
        resolveAll()
        expect { wanderingSanctum.power shouldBe 4 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wanderingSanctum)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Rat Token")
        resolveAll()
        expect { alice.battlefield.names.count { it == "Rat Token" } shouldBe 3 }
    }

    @Test
    fun kinsfolkGatheringGainsSeven() = scenario {
        alice.battlefield("Forest", "Plains")
        alice.hand(kinsfolkGathering)
        alice.casts(kinsfolkGathering, target = "alice")
        resolveAll()
        expect { alice.life shouldBe 27 }
    }

    @Test
    fun conclaveSigilExilesABigCreature() = scenario {
        alice.battlefield("Forest", "Plains")
        alice.hand(conclaveSigil)
        bob.battlefield(giant, ogre)
        alice.casts(conclaveSigil, modes = listOf(1), target = giant)
        resolveAll()
        expect { bob.exile has giant }
    }

    @Test
    fun conclaveSigilMakesAKnight() = scenario {
        alice.battlefield("Forest", "Plains")
        alice.hand(conclaveSigil)
        alice.casts(conclaveSigil, modes = listOf(2))
        resolveAll()
        expect { "Knight Token" hasKeyword Keyword.Vigilance }
    }

    // CR 614.1a: instants and sorceries are exiled instead
    @Test
    fun groveMilitiaExilesSpells() = scenario {
        alice.battlefield("Mountain", groveMilitia)
        alice.hand(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { alice.exile has shock }
    }

    @Test
    fun communalGracePumpsTheTeam() = scenario {
        alice.battlefield(communalGrace, bear)
        expect { bear.power shouldBe 5 }
    }

    @Test
    fun flotillaWurmBringsAWurm() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Plains", "Plains", "Plains")
        alice.hand(flotillaWurm)
        alice.casts(flotillaWurm)
        resolveAll()
        expect { "Wurm Token".power shouldBe 5 }
    }

    @Test
    fun summonsOfTheGroveMakesACentaur() = scenario {
        alice.battlefield("Forest", "Plains")
        alice.hand(summonsOfTheGrove)
        alice.casts(summonsOfTheGrove)
        resolveAll()
        expect { "Centaur Token".power shouldBe 3 }
    }

    @Test
    fun centaurMenderGainsThree() = scenario {
        alice.battlefield("Forest", "Plains", "Plains")
        alice.hand(centaurMender)
        alice.casts(centaurMender)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    // ---- Chunk 03: overload and Izzet spells (overload itself: RtrMechanicsScenariosTest)

    // CR 702.96b: overloaded, "target" becomes "each"
    @Test
    fun galeburstOverloadedTapsEach() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", bear)
        alice.hand(galeburst)
        bob.battlefield(ogre, giant)
        alice.casts(galeburst, way = ownAlternative(galeburst))
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            giant.tapped shouldBe true
            bear.tapped shouldBe false
        }
    }

    @Test
    fun shrinkingSpellOverloaded() = scenario {
        alice.battlefield("Island", "Island", "Island", bear)
        alice.hand(shrinkingSpell)
        bob.battlefield(ogre, giant)
        alice.casts(shrinkingSpell, way = ownAlternative(shrinkingSpell))
        resolveAll()
        expect {
            ogre.power shouldBe -1
            giant.power shouldBe 1
            bear.power shouldBe 2
        }
    }

    @Test
    fun alloyhideWardOverloaded() = scenario {
        alice.battlefield("Island", "Island", bear, human)
        alice.hand(alloyhideWard)
        alice.casts(alloyhideWard, way = ownAlternative(alloyhideWard))
        resolveAll()
        expect {
            bear hasKeyword Keyword.Hexproof
            human.toughness shouldBe 2
        }
    }

    @Test
    fun overchargePulseOneTarget() = scenario {
        alice.battlefield("Mountain", bear)
        alice.hand(overchargePulse)
        alice.casts(overchargePulse, target = bear)
        resolveAll()
        expect { bear.power shouldBe 4 }
    }

    @Test
    fun arcLatticeOverloadedPingsEach() = scenario {
        alice.battlefield("Mountain", "Mountain", human)
        alice.hand(arcLattice)
        bob.battlefield(human, human)
        alice.casts(arcLattice, way = ownAlternative(arcLattice))
        resolveAll()
        expect {
            bob.graveyard.size shouldBe 2
            alice.battlefield has human
        }
    }

    // CR 107.3: X in the overload cost
    @Test
    fun cobblestoneQuakeOverloadedSparesFlyers() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(cobblestoneQuake)
        bob.battlefield(bear, flyer)
        alice.casts(cobblestoneQuake, way = ownAlternative(cobblestoneQuake), x = 2)
        resolveAll()
        expect {
            bob.graveyard has bear
            bob.battlefield has flyer
        }
    }

    @Test
    fun scrapwreckOverloadedDestroysTheirArtifacts() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", idol)
        alice.hand(scrapwreck)
        bob.battlefield(idol)
        alice.casts(scrapwreck, way = ownAlternative(scrapwreck))
        resolveAll()
        expect {
            bob.graveyard has idol
            alice.battlefield has idol
        }
    }

    // CR 508.1d: attacks this turn if able
    @Test
    fun alchemistsFeintWeakensAndForcesAnAttack() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand(alchemistsFeint)
        bob.battlefield(ogre)
        bob.library(bear)
        turn(active = bob, step = TurnStep.Upkeep, priority = alice)
        alice.casts(alchemistsFeint, target = ogre)
        resolveAll()
        expect { ogre.power shouldBe 1 }
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { bob.attacks() }
    }

    @Test
    fun reversalCurrentCountersEachSpellYouDontControl() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        alice.battlefield("Island", "Island", "Mountain")
        alice.hand(reversalCurrent)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(reversalCurrent, target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun blinkgateMakesOneUnblockable() = scenario {
        alice.battlefield("Island", "Mountain", bear)
        alice.hand(blinkgate)
        bob.battlefield(giant)
        alice.casts(blinkgate, target = bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
    }

    @Test
    fun lifeforceRecoilCountersAndBurnsTheCaster() = scenario {
        bob.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        bob.hand(giant)
        alice.battlefield("Island", "Island", "Mountain", "Mountain")
        alice.hand(lifeforceRecoil)
        turn(active = bob)
        bob.casts(giant)
        bob.passes()
        alice.casts(lifeforceRecoil, target = giant)
        resolveAll()
        expect {
            bob.graveyard has giant
            bob.life shouldBe 15
        }
    }

    @Test
    fun laboratorySigilBurnsACreature() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand(laboratorySigil)
        bob.battlefield(bear)
        alice.casts(laboratorySigil, modes = listOf(1), target = bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun laboratorySigilLoots() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand(laboratorySigil, ogre, giant)
        alice.library(bear, human)
        alice.casts(laboratorySigil, modes = listOf(2))
        resolveAll()
        alice.chooses(ogre, giant)
        resolveAll()
        expect { alice.hand.names shouldBe listOf(bear, human) }
    }

    // CR 118.12: unless its controller pays {X}; exiled instead
    @Test
    fun offbeatDenialExilesAnUnpaidSpell() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        alice.battlefield("Island", "Island", "Island")
        alice.hand(offbeatDenial)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(offbeatDenial, target = ogre, x = 2)
        resolveAll()
        if (pending is Decision.May) bob.answers(Answer.YesNo(false))
        resolveAll()
        expect { bob.exile has ogre }
    }

    // CR 107.3a, 118.12a (set check): the {X} to pay is the X announced for the counterspell; paid, the spell stays
    @Test
    fun offbeatDenialIsPaidWithItsX() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        alice.battlefield("Island", "Island", "Island")
        alice.hand(offbeatDenial)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(offbeatDenial, target = ogre, x = 2)
        resolveAll()
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        val payment = decision<Decision.ManaPayment>()
        answer(Answer.ManaPayment(payment.suggestion))
        resolveAll()
        expect {
            (0..5).count { card("Mountain", it).tapped } shouldBe 5
            bob.battlefield has ogre
        }
    }
}
