package arcanum.engine.sets.dsk

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dsk.Dsk.bear
import arcanum.engine.sets.dsk.Dsk.giant
import arcanum.engine.sets.dsk.Dsk.ogre
import arcanum.engine.sets.dsk.Dsk.p1p1
import arcanum.engine.sets.dsk.Dsk.shock
import kotlin.test.Test

/** Duskmourn's survival, manifest dread and face-down cards in play (parser chunks 04–06), beyond the mechanics played in [DskMechanicsScenariosTest]. */
class DskSurvivalAndDreadCardsScenariosTest {
    private val tumblingPepSquad = Cards.invent("Tumbling Pep Squad", "{1}{W}", "Creature — Human Survivor", "Survival — At the beginning of your second main phase, if this creature is tapped, put a flying counter on it. This ability triggers only once.", "2/2")
    private val glimmerHunter = Cards.invent("Glimmer Hunter", "{2}{W}", "Creature — Human Survivor", "Survival — At the beginning of your second main phase, if this creature is tapped, draw a card if you control a Glimmer creature. If you don't control a Glimmer creature, create a 1/1 white Glimmer enchantment creature token.", "3/3")
    private val hesitantMentor = Cards.invent("Hesitant Mentor", "{1}{W}", "Creature — Human Survivor", """
        Survival — At the beginning of your second main phase, if this creature is tapped, put a flying, lifelink, or +1/+1 counter on it.
        Whenever this creature or another creature you control dies, if it had counters on it, put those counters on up to one target creature.
    """, "2/2")
    private val guardianOfTheMeek = Cards.invent("Guardian of the Meek", "{3}{W}", "Creature — Kor Survivor", "Survival — At the beginning of your second main phase, if this creature is tapped, return target creature card with mana value 3 or less from your graveyard to your hand.", "3/4")
    private val hardenedSurvivor = Cards.invent("Hardened Survivor", "{W}", "Creature — Human Survivor", """
        Survival — At the beginning of your second main phase, if this creature is tapped, exile up to one target card from a graveyard.
        As long as there are three or more cards exiled with this creature, it gets +3/+3 and has hexproof. (It can't be the target of spells or abilities your opponents control.)
    """, "2/1")
    private val jadedDrifter = Cards.invent("Jaded Drifter", "{1}{B}", "Creature — Human Survivor", """
        This creature can't be blocked by Glimmers.
        Survival — At the beginning of your second main phase, if this creature is tapped, you may search your library for a card, put it into your graveyard, then shuffle.
    """, "3/1")
    private val warySurvivor = Cards.invent("Wary Survivor", "{3}{G}", "Creature — Elf Survivor", "Survival — At the beginning of your second main phase, if this creature is tapped, you gain 2 life.", "4/4")
    private val stubbornSurvivor = Cards.invent("Stubborn Survivor", "{2}{G}", "Creature — Human Survivor", "Survival — At the beginning of your second main phase, if this creature is tapped, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)", "3/2")
    private val manorMapmaker = Cards.invent("Manor Mapmaker", "{1}{G}", "Creature — Human Scout Survivor", "Survival — At the beginning of your second main phase, if this creature is tapped, reveal cards from the top of your library until you reveal a land card. Put that card into your hand and the rest on the bottom of your library in a random order.", "2/2")
    private val koroRescuedBeastie = Cards.invent("Koro, Rescued Beastie", "{3}{G}", "Legendary Creature — Beast Survivor", "Survival — At the beginning of your second main phase, if Koro is tapped, you may put a permanent card from your hand onto the battlefield.", "4/3")
    private val deeprootSurvivor = Cards.invent("Deeproot Survivor", "{3}{G}{G}", "Creature — Human Survivor", """
        Haste
        Survival — At the beginning of your second main phase, if this creature is tapped, put three +1/+1 counters on up to one target land you control. That land becomes a 0/0 Elemental creature in addition to its other types. It gains haste until your next turn.
    """, "3/4")
    private val rookBroodStalker = Cards.invent("Rook, Brood Stalker", "{2}{G}{W}", "Legendary Creature — Human Survivor", "Survival — At the beginning of your second main phase, if Rook is tapped, reveal the top X cards of your library, where X is its power. Put any number of creature and/or Vehicle cards with different powers from among them into your hand. Put the rest on the bottom of your library in a random order.", "4/4")
    private val cannyFabulist = Cards.invent("Canny Fabulist", "{1}{G}{W}", "Creature — Human Survivor", "Survival — At the beginning of your second main phase, if this creature is tapped, put a +1/+1 counter on target creature.", "3/3")
    private val unknownSkimmer = Cards.invent("Unknown Skimmer", "{1}{W}{W}", "Artifact — Vehicle", """
        Flying
        When this Vehicle enters, exile up to one target creature with toughness 5 or less.
        When this Vehicle leaves the battlefield, the exiled card's owner manifests dread.
        Crew 1
    """, "2/2")
    private val uncannyTwins = Cards.invent("Uncanny Twins", "{3}{W}", "Creature — Human", "When this creature enters, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)", "2/2")
    private val unwelcomeRedo = Cards.invent("Unwelcome Redo", "{W}", "Instant", "Destroy target creature. Its controller manifests dread. (That player looks at the top two cards of their library, then puts one onto the battlefield face down as a 2/2 creature and the other into their graveyard. If it's a creature card, it can be turned face up any time for its mana cost.)")
    private val loathsomeEye = Cards.invent("Loathsome Eye", "{2}{U}", "Creature — Eye", """
        As an additional cost to cast this spell, exile six cards from your graveyard.
        Flying
        At the beginning of each opponent's upkeep, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
    """, "5/5")
    private val hexedWindcoat = Cards.invent("Hexed Windcoat", "{2}{U}", "Artifact — Equipment", """
        When this Equipment enters, manifest dread, then attach this Equipment to that creature. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
        Equipped creature has flying.
        Equip {3}
    """)
    private val dreadOfDoubles = Cards.invent("Dread of Doubles", "{1}{U}{U}", "Enchantment Creature — Nightmare", """
        Flash
        When this creature enters, counter target spell. Its controller manifests dread. (That player looks at the top two cards of their library, then puts one onto the battlefield face down as a 2/2 creature and the other into their graveyard. If it's a creature card, it can be turned face up any time for its mana cost.)
    """, "3/2")
    private val occultAnalyst = Cards.invent("Occult Analyst", "{1}{U}", "Creature — Human Detective", "Whenever you manifest dread, put a card you put into your graveyard this way into your hand.", "1/3")
    private val keepQuietKeepHidden = Cards.invent("Keep Quiet, Keep Hidden", "{1}{U}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, tap enchanted creature.
        Enchanted creature doesn't untap during its controller's untap step.
        {4}{U}{U}: Shuffle enchanted creature into its owner's library, then manifest dread. Activate only as a sorcery.
    """)
    private val wrenchedReality = Cards.invent("Wrenched Reality", "{1}{U}{U}", "Instant", """
        Choose one —
        • Counter target spell.
        • Manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
    """)
    private val tooScaredToScream = Cards.invent("Too Scared to Scream", "{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature loses all abilities and is a Toy artifact creature with base power and toughness 0/2 in addition to its other types.
        As long as enchanted creature is face down, it can't be turned face up.
    """)
    private val unnervingClutch = Cards.invent("Unnerving Clutch", "{2}{U}", "Sorcery", "Return up to one target nonland permanent to its owner's hand. Manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)")
    private val harmlessRat = Cards.invent("Harmless Rat", "{1}{B}", "Creature — Rat", "When this creature dies, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)", "1/1")
    private val slayersVisage = Cards.invent("Slayer's Visage", "{2}{B}", "Artifact — Equipment", """
        When this Equipment enters, manifest dread, then attach this Equipment to that creature. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
        Equipped creature has menace.
        Equip {2}
    """)
    private val flipInsideOut = Cards.invent("Flip Inside Out", "{R}", "Instant", "Target creature gets +3/+0 until end of turn. When it dies this turn, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)")
    private val bashfulBehemoth = Cards.invent("Bashful Behemoth", "{4}{G}", "Creature — Beast", "When this creature dies, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)", "5/4")
    private val smashThroughTheDoor = Cards.invent("Smash Through the Door", "{2}{G}", "Instant", """
        Choose one —
        • Exile target artifact.
        • Exile target enchantment.
        • Manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
    """)
    private val summonTheDread = Cards.invent("Summon the Dread", "{1}{G}", "Sorcery", "Manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)")
    private val dangersBehindEveryDoor = Cards.invent("Dangers Behind Every Door", "{3}{G}", "Enchantment", """
        When this enchantment enters, manifest dread.
        Whenever a face-down permanent you control enters, search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
    """)
    private val beneathTheFlesh = Cards.invent("Beneath the Flesh", "{2}{G}", "Sorcery", """
        Manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
        You may return a permanent card from your graveyard to your hand.
    """)
    private val valgavothsOnrush = Cards.invent("Valgavoth's Onrush", "{X}{X}{G}", "Sorcery", "Manifest dread X times, then put X +1/+1 counters on each of those creatures. (To manifest dread, look at the top two cards of your library, then put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)")
    private val unsettlingGlee = Cards.invent("Unsettling Glee", "{B}{R}", "Enchantment", """
        When this enchantment enters, you may sacrifice another enchantment or creature. If you do, draw two cards.
        When you sacrifice this enchantment, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
    """)
    private val swellingDread = Cards.invent("Swelling Dread", "{G}{U}", "Enchantment", """
        Flash
        When this enchantment enters, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
        Whenever you turn a permanent face up, put a +1/+1 counter on it.
    """)
    private val cryptidExaminer = Cards.invent("Cryptid Examiner", "{2}{G}", "Creature — Elf Warrior", """
        Vigilance
        Whenever a face-down permanent you control enters and whenever this creature or another permanent you control is turned face up, put a +1/+1 counter on this creature.
    """, "2/3")
    private val hauntwoodsHowler = Cards.invent("Hauntwoods Howler", "{1}{G}{G}", "Creature — Beast Mutant", """
        Whenever this creature attacks, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
        {1}{G}: Reveal target face-down permanent. If it's a creature card, you may turn it face up.
    """, "3/3")
    private val conductiveCleaver = Cards.invent("Conductive Cleaver", "{4}", "Artifact — Equipment", """
        When this Equipment enters, manifest dread, then attach this Equipment to that creature. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
        Equipped creature gets +2/+1.
        Equip {4}
    """)
    private val autopsyKit = Cards.invent("Autopsy Kit", "{5}", "Artifact — Equipment", """
        When this Equipment enters, manifest dread, then attach this Equipment to that creature.
        Equipped creature gets +2/+2 and has deathtouch and lifelink.
        Equip—Sacrifice a creature.
    """)
    private val lostFootage = Cards.invent("Lost Footage", "{1}", "Artifact — Clue", """
        You may look at face-down creatures your opponents control any time.
        {2}, Sacrifice this artifact: Surveil 2, then draw a card. (To surveil 2, look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """)
    private val overgrownDevotee = Cards.invent("Overgrown Devotee", "{1}{G}", "Creature — Elf Druid", """
        {T}: Add one mana of any color.
        {T}: Add two mana of any one color. Spend this mana only to turn permanents face up.
    """, "0/4")
    private val absentMindedBookworm = Cards.invent("Absent-Minded Bookworm", "{G}{U}", "Creature — Human Wizard", "At the beginning of your end step, you may draw a card. If you do, discard a card unless a permanent entered the battlefield face down under your control this turn or you turned a permanent face up this turn.", "2/3")

    /** [name] starts tapped and the turn moves to the second main phase, where survival triggers (CR 207.2c). */
    private fun Scenario.survive() {
        advanceTo(TurnStep.PostcombatMain)
        resolveAll()
    }

    // ---- Chunk 04: survival (the ability word itself: DskMechanicsScenariosTest)

    // CR 603.4: "if this creature is tapped"; "triggers only once"
    @Test
    fun tumblingPepSquadGetsAFlyingCounter() = scenario {
        alice.battlefield(tumblingPepSquad, tapped = true)
        survive()
        expect { tumblingPepSquad hasKeyword Keyword.Flying }
    }

    @Test
    fun glimmerHunterMakesAGlimmerWithoutOne() = scenario {
        alice.battlefield(glimmerHunter, tapped = true)
        survive()
        expect { alice.battlefield has "Glimmer Token" }
    }

    @Test
    fun hesitantMentorChoosesACounter() = scenario {
        alice.battlefield(hesitantMentor, tapped = true)
        survive()
        option("+1/+1")
        resolveAll()
        expect { hesitantMentor.counters(p1p1) shouldBe 1 }
    }

    // CR 603.10a: the counters it had as it died
    @Test
    fun hesitantMentorPassesCountersOn() = scenario {
        alice.battlefield("Mountain", bear)
        alice.battlefield(hesitantMentor, counters = listOf(p1p1 to 1))
        alice.hand(shock, shock)
        alice.battlefield("Mountain")
        alice.casts(shock, target = hesitantMentor)
        resolveAll()
        alice.casts(shock, target = hesitantMentor)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun guardianOfTheMeekReturnsACheapCreature() = scenario {
        alice.battlefield(guardianOfTheMeek, tapped = true)
        alice.graveyard(ogre, giant)
        survive()
        targetsIfAsked(ogre)
        resolveAll()
        expect { alice.hand has ogre }
    }

    @Test
    fun hardenedSurvivorExilesFromAGraveyard() = scenario {
        alice.battlefield(hardenedSurvivor, tapped = true)
        bob.graveyard(ogre)
        survive()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            bob.exile has ogre
            hardenedSurvivor.power shouldBe 2
        }
    }

    // CR 607.2a: "cards exiled with this creature"
    @Test
    fun hardenedSurvivorGrowsWithThreeExiled() = scenario {
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.battlefield(hardenedSurvivor, tapped = true)
        bob.graveyard(ogre, giant, Dsk.human)
        survive()
        targetsIfAsked(ogre)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(hardenedSurvivor)
        advanceTo(TurnStep.PostcombatMain)
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers, turn = 5)
        alice.attacks(hardenedSurvivor)
        advanceTo(TurnStep.PostcombatMain)
        resolveAll()
        targetsIfAsked(Dsk.human)
        resolveAll()
        expect {
            hardenedSurvivor.power shouldBe 5
            hardenedSurvivor hasKeyword Keyword.Hexproof
        }
    }

    @Test
    fun jadedDrifterPutsACardIntoTheGraveyard() = scenario {
        alice.battlefield(jadedDrifter, tapped = true)
        alice.library(bear, giant)
        survive()
        may(true)
        alice.chooses(giant)
        resolveAll()
        expect { alice.graveyard has giant }
    }

    @Test
    fun warySurvivorGainsLife() = scenario {
        alice.battlefield(warySurvivor, tapped = true)
        survive()
        expect { alice.life shouldBe 22 }
    }

    @Test
    fun stubbornSurvivorManifestsDread() = scenario {
        alice.battlefield(stubbornSurvivor, tapped = true)
        alice.library(ogre, "Island")
        survive()
        alice.chooses(ogre)
        resolveAll()
        expect {
            card(ogre).power shouldBe 2
            alice.graveyard has "Island"
        }
    }

    @Test
    fun manorMapmakerFindsALand() = scenario {
        alice.battlefield(manorMapmaker, tapped = true)
        alice.library(bear, ogre, "Plains", giant)
        survive()
        expect {
            alice.hand has "Plains"
            alice.library.names.first() shouldBe giant
        }
    }

    @Test
    fun koroPutsAPermanentFromTheHand() = scenario {
        alice.battlefield(koroRescuedBeastie, tapped = true)
        alice.hand(giant)
        survive()
        if (pending is Decision.May) may(true)
        alice.chooses(giant)
        resolveAll()
        expect { alice.battlefield has giant }
    }

    // CR 205.1b: the land becomes a 0/0 Elemental creature in addition to its other types
    @Test
    fun deeprootSurvivorAnimatesALand() = scenario {
        alice.battlefield("Forest")
        alice.battlefield(deeprootSurvivor, tapped = true)
        survive()
        targetsIfAsked("Forest")
        resolveAll()
        expect {
            "Forest".power shouldBe 3
            "Forest".types.toSet() shouldBe setOf(CardType.Land, CardType.Creature)
            "Forest" hasKeyword Keyword.Haste
        }
    }

    @Test
    fun rookTakesCreaturesWithDifferentPowers() = scenario {
        alice.battlefield(rookBroodStalker, tapped = true)
        alice.library(bear, ogre, "Island", Dsk.flyer, giant)
        survive()
        alice.chooses(bear, ogre)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(bear, ogre)
            alice.library.size shouldBe 3
        }
    }

    @Test
    fun cannyFabulistGivesACounter() = scenario {
        alice.battlefield(cannyFabulist, tapped = true)
        alice.battlefield(bear)
        survive()
        targets(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    // ---- Chunks 05–06: manifest dread and face-down permanents (the keyword action: DskMechanicsScenariosTest)

    // CR 610.3-like: the leaves trigger makes the exiled card's owner manifest dread
    @Test
    fun unknownSkimmerExilesThenItsOwnerManifests() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Mountain")
        alice.hand(unknownSkimmer, Dsk.shatter)
        bob.battlefield(ogre)
        bob.library(giant, "Forest")
        alice.casts(unknownSkimmer, paying = listOf("Plains", "Plains#1", "Plains#2"))
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.exile has ogre }
        alice.casts(Dsk.shatter, target = unknownSkimmer)
        resolveAll()
        bob.chooses(giant)
        resolveAll()
        expect { card(giant).power shouldBe 2 }
    }

    @Test
    fun uncannyTwinsManifestOnEntering() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(uncannyTwins)
        alice.library(ogre, "Island")
        alice.casts(uncannyTwins)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { card(ogre).power shouldBe 2 }
    }

    @Test
    fun unwelcomeRedoLeavesTheControllerADread() = scenario {
        alice.battlefield("Plains")
        alice.hand(unwelcomeRedo)
        bob.battlefield(giant)
        bob.library(ogre, "Forest")
        alice.casts(unwelcomeRedo, target = giant)
        resolveAll()
        bob.chooses(ogre)
        resolveAll()
        expect {
            bob.graveyard has giant
            card(ogre).power shouldBe 2
            card(ogre).controller shouldBe bob.id
        }
    }

    // CR 601.2b, 601.2h: exiling six cards as an additional cost
    @Test
    fun loathsomeEyeManifestsInEachOpponentsUpkeep() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(loathsomeEye)
        alice.graveyard("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.library(ogre, "Island")
        bob.library(bear)
        alice.casts(loathsomeEye)
        resolveAll()
        expect { alice.exile.size shouldBe 6 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { card(ogre).power shouldBe 2 }
    }

    @Test
    fun hexedWindcoatEquipsTheDread() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(hexedWindcoat)
        alice.library(ogre, "Island")
        alice.casts(hexedWindcoat)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { ogre hasKeyword Keyword.Flying }
    }

    @Test
    fun dreadOfDoublesCountersAndTheCasterManifests() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        bob.library(giant, "Forest")
        alice.battlefield("Island", "Island", "Island")
        alice.hand(dreadOfDoubles)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(dreadOfDoubles)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        bob.chooses(giant)
        resolveAll()
        expect {
            bob.graveyard has ogre
            card(giant).power shouldBe 2
        }
    }

    @Test
    fun occultAnalystKeepsTheOtherCard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", occultAnalyst)
        alice.hand(beneathTheFlesh)
        alice.library(ogre, "Island")
        alice.casts(beneathTheFlesh)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Island")
        resolveAll()
        expect { alice.hand has "Island" }
    }

    // CR 701.24a: shuffled into the library
    @Test
    fun keepQuietKeepHiddenShufflesAwayAndManifests() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(keepQuietKeepHidden)
        alice.library(bear, "Island")
        bob.battlefield(ogre)
        alice.casts(keepQuietKeepHidden, target = ogre)
        resolveAll()
        expect { ogre.tapped shouldBe true }
        alice.activates(keepQuietKeepHidden)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect {
            bob.library has ogre
            card(bear).power shouldBe 2
        }
    }

    @Test
    fun wrenchedRealityManifests() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(wrenchedReality)
        alice.library(ogre, "Island")
        alice.casts(wrenchedReality, modes = listOf(1))
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { card(ogre).power shouldBe 2 }
    }

    // CR 613.1d, f, 613.4b: a Toy artifact creature that loses all abilities, base 0/2
    @Test
    fun tooScaredToScreamMakesAToy() = scenario {
        alice.battlefield("Island")
        alice.hand(tooScaredToScream)
        bob.battlefield(Dsk.flyer)
        alice.casts(tooScaredToScream, target = Dsk.flyer)
        resolveAll()
        expect {
            Dsk.flyer.power shouldBe 0
            Dsk.flyer.toughness shouldBe 2
            Dsk.flyer lacksKeyword Keyword.Flying
            Dsk.flyer.types.toSet() shouldBe setOf(CardType.Artifact, CardType.Creature)
        }
    }

    @Test
    fun unnervingClutchBouncesAndManifests() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(unnervingClutch)
        alice.library(bear, "Island")
        bob.battlefield(ogre)
        alice.casts(unnervingClutch, target = ogre)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        expect {
            bob.hand has ogre
            card(bear).power shouldBe 2
        }
    }

    @Test
    fun harmlessRatManifestsAsItDies() = scenario {
        alice.battlefield("Mountain", harmlessRat)
        alice.hand(shock)
        alice.library(ogre, "Island")
        alice.casts(shock, target = harmlessRat)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { card(ogre).power shouldBe 2 }
    }

    @Test
    fun slayersVisageGivesTheDreadMenace() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(slayersVisage)
        alice.library(ogre, "Island")
        alice.casts(slayersVisage)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { ogre hasKeyword Keyword.Menace }
    }

    // CR 603.7c: "when it dies this turn"
    @Test
    fun flipInsideOutManifestsWhenItDies() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(flipInsideOut, shock)
        alice.library(ogre, "Island")
        alice.casts(flipInsideOut, target = bear)
        resolveAll()
        expect { bear.power shouldBe 5 }
        alice.casts(shock, target = bear)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { card(ogre).power shouldBe 2 }
    }

    @Test
    fun bashfulBehemothManifestsAsItDies() = scenario {
        alice.battlefield("Swamp", bashfulBehemoth)
        alice.hand(Dsk.doom)
        alice.library(ogre, "Island")
        alice.casts(Dsk.doom, target = bashfulBehemoth)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { card(ogre).power shouldBe 2 }
    }

    @Test
    fun smashThroughTheDoorExilesAnArtifact() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(smashThroughTheDoor)
        bob.battlefield(Dsk.idol)
        alice.casts(smashThroughTheDoor, modes = listOf(0), target = Dsk.idol)
        resolveAll()
        expect { bob.exile has Dsk.idol }
    }

    @Test
    fun dangersBehindEveryDoorFetchesForAFaceDownArrival() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(dangersBehindEveryDoor)
        alice.library(ogre, "Island", "Plains")
        alice.casts(dangersBehindEveryDoor)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Plains")
        resolveAll()
        expect {
            alice.battlefield has "Plains"
            card(ogre).power shouldBe 2
        }
    }

    @Test
    fun beneathTheFleshReturnsAPermanentCard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(beneathTheFlesh)
        alice.graveyard(Dsk.idol)
        alice.library(ogre, "Island")
        alice.casts(beneathTheFlesh)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        if (pending is Decision.May) may(true)
        alice.chooses(Dsk.idol)
        resolveAll()
        expect { alice.hand has Dsk.idol }
    }

    @Test
    fun unsettlingGleeSacrificesForCardsAndManifestsWhenSacrificed() = scenario {
        alice.battlefield("Swamp", "Mountain", Dsk.charm)
        alice.hand(unsettlingGlee)
        alice.library(bear, ogre)
        alice.casts(unsettlingGlee)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(Dsk.charm)
        resolveAll()
        expect {
            alice.graveyard has Dsk.charm
            alice.hand.names shouldBe listOf(bear, ogre)
        }
    }

    // CR 701.40b: turning it face up; "whenever you turn a permanent face up"
    @Test
    fun swellingDreadGrowsWhatTurnsFaceUp() = scenario {
        alice.battlefield("Forest", "Island", "Forest", "Forest")
        alice.hand(swellingDread)
        alice.library(bear, "Island")
        alice.casts(swellingDread, paying = listOf("Forest", "Island"))
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        alice.takesSpecialAction(bear, "manifested", paying = listOf("Forest#1", "Forest#2"))
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear.power shouldBe 3
        }
    }

    @Test
    fun cryptidExaminerGrowsOnFaceDownAndFaceUp() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", cryptidExaminer)
        alice.hand(summonTheDread)
        alice.library(bear, "Island")
        alice.casts(summonTheDread, paying = listOf("Forest", "Forest#1"))
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        expect { cryptidExaminer.counters(p1p1) shouldBe 1 }
        alice.takesSpecialAction(bear, "manifested", paying = listOf("Forest#2", "Forest#3"))
        resolveAll()
        expect { cryptidExaminer.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun hauntwoodsHowlerManifestsWhenAttacking() = scenario {
        alice.battlefield(hauntwoodsHowler)
        alice.library(ogre, "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hauntwoodsHowler)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { card(ogre).power shouldBe 2 }
    }

    @Test
    fun conductiveCleaverEquipsTheDread() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(conductiveCleaver)
        alice.library(ogre, "Island")
        alice.casts(conductiveCleaver)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            card(ogre).power shouldBe 4
            card(ogre).toughness shouldBe 3
        }
    }

    // CR 702.6a: equip with a sacrifice as its cost
    @Test
    fun autopsyKitEquipsAndMovesBySacrifice() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", bear, Dsk.human)
        alice.hand(autopsyKit)
        alice.library(ogre, "Island")
        alice.casts(autopsyKit)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            card(ogre).power shouldBe 4
            ogre hasKeyword Keyword.Deathtouch
        }
        alice.activates(autopsyKit, target = bear, choosing = listOf(Dsk.human))
        resolveAll()
        expect {
            alice.graveyard has Dsk.human
            bear.power shouldBe 4
            bear hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun lostFootageSurveilsAndDraws() = scenario {
        alice.battlefield(lostFootage, "Plains", "Plains")
        alice.library(ogre, bear, giant)
        alice.activates(lostFootage)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.hand has bear
        }
    }

    // CR 106.6: mana only for turning permanents face up
    @Test
    fun overgrownDevoteeTurnsADreadFaceUp() = scenario {
        alice.battlefield("Forest", "Forest", overgrownDevotee)
        alice.hand(summonTheDread)
        alice.library(bear, "Island")
        alice.casts(summonTheDread, paying = listOf("Forest", "Forest#1"))
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        alice.activates(overgrownDevotee, ability = 1, color = Color.Green)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        alice.takesSpecialAction(bear, "manifested")
        expect { card(bear).view.characteristics!!.name shouldBe bear }
    }

    @Test
    fun absentMindedBookwormDrawsWithoutDiscardAfterAFaceDownArrival() = scenario {
        alice.battlefield("Forest", "Forest", absentMindedBookworm)
        alice.hand(summonTheDread)
        alice.library(bear, "Island", ogre)
        alice.casts(summonTheDread)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.hand.names shouldBe listOf(ogre) }
    }

    @Test
    fun absentMindedBookwormLootsOtherwise() = scenario {
        alice.battlefield(absentMindedBookworm)
        alice.hand(giant)
        alice.library(ogre)
        advanceTo(TurnStep.End)
        resolveAll()
        may(true)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(giant)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(ogre)
            alice.graveyard has giant
        }
    }
}
