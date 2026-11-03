package arcanum.engine.sets.sos

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.sos.Sos.bear
import arcanum.engine.sets.sos.Sos.doom
import arcanum.engine.sets.sos.Sos.elf
import arcanum.engine.sets.sos.Sos.giant
import arcanum.engine.sets.sos.Sos.heal
import arcanum.engine.sets.sos.Sos.ogre
import arcanum.engine.sets.sos.Sos.p1p1
import arcanum.engine.sets.sos.Sos.shock
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Secrets of Strixhaven's life, repartee, Inkling and preparation cards in play (parser chunks 05–09): each prepare
 * card's creature and the copy of its spell (CR 722). */
class SosPrepareAndReparteeCardsScenariosTest {
    private val smirkingDuskscribe = Cards.invent("Smirking Duskscribe", "{4}{B}", "Creature — Vampire Warlock", """
        Flying
        When this creature enters, each opponent loses 2 life and you gain 2 life.
    """, "3/3")
    private val unleashTheVermin = Cards.invent("Unleash the Vermin", "{1}{B}", "Sorcery", "Each opponent discards a card. You create a 1/1 black and green Pest creature token with \"Whenever this token attacks, you gain 1 life.\"")
    private val grubnestSloth = Cards.invent("Grubnest Sloth", "{3}{G}", "Creature — Plant Sloth", """
        Reach
        When this creature dies, create two 1/1 black and green Pest creature tokens with "Whenever this token attacks, you gain 1 life."
    """, "4/4")
    private val commencementMorning = Cards.invent("Commencement Morning", "{W}", "Enchantment", "Repartee — Whenever you cast an instant or sorcery spell that targets a creature, put a +1/+1 counter on target creature you control.")
    private val wellReadInkscribe = Cards.invent("Well-Read Inkscribe", "{1}{W}", "Creature — Human Wizard", """
        Vigilance
        Repartee — Whenever you cast an instant or sorcery spell that targets a creature, create a 1/1 white and black Inkling creature token with flying.
    """, "2/2")
    private val inkformExhibitor = Cards.invent("Inkform Exhibitor", "{3}{W}", "Creature — Elephant Cleric", """
        Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
        Repartee — Whenever you cast an instant or sorcery spell that targets a creature, this creature gets +1/+0 and gains lifelink until end of turn.
    """, "3/4")
    private val practicedDisputant = Cards.invent("Practiced Disputant", "{2}{W}", "Creature — Djinn Bard", """
        Vigilance
        Repartee — Whenever you cast an instant or sorcery spell that targets a creature, this creature gets +1/+1 until end of turn.
    """, "3/3")
    private val rousingHopecaller = Cards.invent("Rousing Hopecaller", "{2}{W}", "Creature — Bird Bard", """
        Flying, lifelink
        Repartee — Whenever you cast an instant or sorcery spell that targets a creature, put a +1/+1 counter on each creature you control.
    """, "1/3")
    private val agoraGravescribe = Cards.invent("Agora Gravescribe", "{5}{B}", "Creature — Troll Warlock", """
        Ward—Discard a card.
        Repartee — Whenever you cast an instant or sorcery spell that targets a creature, return target creature card from your graveyard to the battlefield.
    """, "5/4")
    private val sermonizingSneermage = Cards.invent("Sermonizing Sneermage", "{B}", "Creature — Human Warlock", "Repartee — Whenever you cast an instant or sorcery spell that targets a creature, put a +1/+1 counter on this creature.", "1/1")
    private val mediatorsFencer = Cards.invent("Mediator's Fencer", "{W}{W}{B}{B}", "Creature — Kor Warlock", """
        When this creature enters, draw a card. Each player loses 1 life.
        Repartee — Whenever you cast an instant or sorcery spell that targets a creature, exile up to one target creature. Return that card to the battlefield under its owner's control at the beginning of the next end step.
    """, "4/3")
    private val inklingPennantBearer = Cards.invent("Inkling Pennant-Bearer", "{W}{B}", "Creature — Inkling Cat", "Repartee — Whenever you cast an instant or sorcery spell that targets a creature, this creature gains flying until end of turn. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)", "2/2")
    private val chidingRegistrar = Cards.invent("Chiding Registrar", "{W}{B}", "Creature — Dwarf Cleric", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Repartee — Whenever you cast an instant or sorcery spell that targets a creature, put a +1/+1 counter on this creature.
        When this creature dies, if it had counters on it, put those counters on up to one target creature.
    """, "2/2")
    private val eavesdroppingAide = Cards.invent("Eavesdropping Aide", "{1}{W}{B}", "Creature — Human Cleric", """
        Repartee — Whenever you cast an instant or sorcery spell that targets a creature, this creature can't be blocked this turn.
        Whenever this creature deals combat damage to a player, you draw a card and lose 1 life.
    """, "2/3")
    private val keenRunecaster = Cards.invent("Keen Runecaster", "{3}{W}", "Creature — Cat Cleric", "When this creature enters, create a 1/1 white and black Inkling creature token with flying.", "3/3")
    private val scathingMarginalia = Cards.invent("Scathing Marginalia", "{1}{W}", "Instant", "Destroy target creature. Its controller creates a 1/1 white and black Inkling creature token with flying.")
    private val crackWaylayer = Cards.inventFaces("Prepare",
        face("Crack Waylayer", "{W}", "Creature — Human Wizard", "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "1/2", null, null),
        face("Retort in Kind", "{1}{W}", "Sorcery", """
            You may tap or untap target creature.
            Draw a card.
        """, null, null, null),
    )
    private val emeritusOfConcord = Cards.inventFaces("Prepare",
        face("Emeritus of Concord", "{1}{W}{W}", "Creature — Cat Cleric", "When this creature enters, target player creates a 1/1 white and black Inkling creature token with flying. Then if an opponent controls more creatures than you, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "3/3", null, null),
        face("Blades into Ploughs", "{W}", "Instant", "Exile target creature. Its controller gains life equal to its power.", null, null, null),
    )
    private val oathswornSquire = Cards.inventFaces("Prepare",
        face("Oathsworn Squire", "{3}{W}", "Creature — Cat Cleric", """
            First strike
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "3/3", null, null),
        face("Agora's Boon", "{W}", "Sorcery", "Target creature gets +1/+0 and gains flying until end of turn.", null, null, null),
    )
    private val pairedScholars = Cards.inventFaces("Prepare",
        face("Paired Scholars", "{1}{W}", "Creature — Human Cleric Wizard", """
            First strike
            At the beginning of each end step, if an opponent has more cards in hand than you, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "2/2", null, null),
        face("Hidden Assignation", "{1}{W}{W}", "Sorcery", "You and target opponent each draw three cards.", null, null, null),
    )
    private val penknifeLaureate = Cards.inventFaces("Prepare",
        face("Penknife Laureate", "{1}{W}", "Creature — Human Cleric", """
            Double strike
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "1/1", null, null),
        face("Double Purpose", "{1}{W}", "Sorcery", "Target creature gets +1/+0 and gains double strike until end of turn.", null, null, null),
    )
    private val quadComposer = Cards.inventFaces("Prepare",
        face("Quad Composer", "{3}{U}", "Creature — Merfolk Bard", """
            Ward {2}
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "3/4", null, null),
        face("Watery Aria", "{4}{U}", "Sorcery", "Create a 3/3 blue and red Elemental creature token with flying.", null, null, null),
    )
    private val emeritusOfNotions = Cards.inventFaces("Prepare",
        face("Emeritus of Notions", "{3}{U}{U}", "Creature — Human Wizard", """
            Flying, ward {2}
            This creature enters prepared.
            Whenever this creature attacks, you may exile eight cards from your graveyard. If you do, this creature becomes prepared.
        """, "5/5", null, null),
        face("Forebears' Recollection", "{U}", "Instant", "Target player draws three cards.", null, null, null),
    )
    private val hearteningFlier = Cards.inventFaces("Prepare",
        face("Heartening Flier", "{2}{U}", "Creature — Bird Wizard", """
            Flying
            Whenever this creature attacks, it becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "2/3", null, null),
        face("Leap Aloft", "{U}", "Instant", "Target creature gains flying until end of turn.", null, null, null),
    )
    private val attunedTrio = Cards.inventFaces("Prepare",
        face("Attuned Trio", "{U}", "Creature — Merfolk Bard Wizard", "{T}, Tap two untapped creatures you control: This creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "1/1", null, null),
        face("Thoughtsquall", "{U}", "Instant", "Draw three cards, then put two cards from your hand on top of your library in any order.", null, null, null),
    )
    private val ilsaWardenOfFortune = Cards.inventFaces("Prepare",
        face("Ilsa, Warden of Fortune", "{2}{U}", "Legendary Creature — Human Wizard", """
            Ilsa enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            When Ilsa enters, draw two cards, then discard two cards.
        """, "2/4", null, null),
        face("Seer's Bounty", "{X}{X}{U}", "Sorcery", "Create X 0/0 green and blue Fractal creature tokens, then put X +1/+1 counters on each Fractal you control.", null, null, null),
    )
    private val vistaPainter = Cards.inventFaces("Prepare",
        face("Vista Painter", "{1}{U}", "Creature — Merfolk Wizard", "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "2/1", null, null),
        face("Vivid Notion", "{4}{U}", "Sorcery", "Draw two cards.", null, null, null),
    )
    private val aircoachDriver = Cards.inventFaces("Prepare",
        face("Aircoach Driver", "{2}{U}", "Creature — Bird Pilot", """
            Flash
            Flying, vigilance
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "2/3", null, null),
        face("Board the Coach", "{U}", "Instant", "Exile target non-Pilot creature you control, then return that card to the battlefield under its owner's control.", null, null, null),
    )
    private val grimoireSearcher = Cards.inventFaces("Prepare",
        face("Grimoire Searcher", "{3}{U}", "Creature — Bird Wizard", """
            Flying
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "3/3", null, null),
        face("Studious Review", "{U}", "Sorcery", "Draw two cards, then discard two cards.", null, null, null),
    )
    private val daringGourmand = Cards.inventFaces("Prepare",
        face("Daring Gourmand", "{2}{B}", "Creature — Human Warlock", "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "3/2", null, null),
        face("Take a Nibble", "{B}", "Sorcery", "Put a +1/+1 counter on target creature. You gain 1 life.", null, null, null),
    )
    private val jovialBonecaller = Cards.inventFaces("Prepare",
        face("Jovial Bonecaller", "{3}{B}", "Creature — Orc Warlock", "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "4/2", null, null),
        face("Rouse the Fallen", "{B}", "Sorcery", "Return target creature card from your graveyard to your hand.", null, null, null),
    )
    private val emeritusOfSorrow = Cards.inventFaces("Prepare",
        face("Emeritus of Sorrow", "{3}{B}", "Creature — Vampire Warlock", """
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            At the beginning of your end step, if two or more creatures died this turn, this creature becomes prepared.
        """, "5/4", null, null),
        face("Fiendish Mentor", "{1}{B}", "Sorcery", "Search your library for a card, put that card into your hand, then shuffle.", null, null, null),
    )
    private val cryptScholar = Cards.inventFaces("Prepare",
        face("Crypt Scholar", "{2}{B}", "Creature — Troll Warlock", "At the beginning of your upkeep, surveil 1. Then if there are three or more creature cards in your graveyard, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "3/3", null, null),
        face("Raise the Buried", "{B}", "Sorcery", "Put target creature card from a graveyard onto the battlefield under your control. You lose life equal to that card's mana value.", null, null, null),
    )
    private val leechkeeper = Cards.inventFaces("Prepare",
        face("Leechkeeper", "{1}{B}", "Creature — Human Warlock", "Whenever you gain life for the first time each turn, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "2/2", null, null),
        face("Vein Draw", "{B}", "Sorcery", "Each opponent loses 2 life.", null, null, null),
    )
    private val searingShadecoil = Cards.inventFaces("Prepare",
        face("Searing Shadecoil", "{4}{B}", "Creature — Snake Warlock", "At the beginning of your first main phase, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "4/6", null, null),
        face("Toxic Retort", "{B}", "Sorcery", "Target creature you control gets +2/+0 and gains deathtouch until end of turn.", null, null, null),
    )
    private val plottingGlibtongue = Cards.inventFaces("Prepare",
        face("Plotting Glibtongue", "{1}{B}", "Creature — Vampire Warlock", """
            Flying, lifelink
            At the beginning of your second main phase, if you gained 2 or more life this turn, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "1/3", null, null),
        face("Blood Signature", "{B}{B}", "Sorcery", "Target player draws two cards and loses 2 life.", null, null, null),
    )
    private val flaringFirechanter = Cards.inventFaces("Prepare",
        face("Flaring Firechanter", "{2}{R}", "Creature — Dwarf Bard", "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "2/3", null, null),
        face("Boiling Chant", "{2}{R}", "Instant", "Add {R}{R}{R}{R}{R}.", null, null, null),
    )
    private val emeritusOfStrife = Cards.inventFaces("Prepare",
        face("Emeritus of Strife", "{1}{R}", "Creature — Human Wizard", """
            First strike
            Whenever you cast your third spell each turn, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "2/2", null, null),
        face("Skyfire Jolt", "{R}", "Instant", "Skyfire Jolt deals 3 damage to any target.", null, null, null),
    )
    private val goblinGlassblower = Cards.inventFaces("Prepare",
        face("Goblin Glassblower", "{1}{R}", "Creature — Goblin Sorcerer", "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "2/2", null, null),
        face("Proud Handiwork", "{R}", "Sorcery", "Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")", null, null, null),
    )
    private val vortexArtisan = Cards.inventFaces("Prepare",
        face("Vortex Artisan", "{1}{R}{R}", "Creature — Minotaur Sorcerer", """
            Haste
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "3/2", null, null),
        face("Flare Salvo", "{1}{R}", "Sorcery", "Destroy target nonbasic land.", null, null, null),
    )
    private val pigmentHerder = Cards.inventFaces("Prepare",
        face("Pigment Herder", "{4}{R}", "Creature — Orc Sorcerer", """
            Flying
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "4/4", null, null),
        face("Vivid Stroke", "{R}", "Sorcery", "When you next cast an instant or sorcery spell this turn, copy that spell. You may choose new targets for the copy.", null, null, null),
    )
    private val discordScholar = Cards.inventFaces("Prepare",
        face("Discord Scholar", "{2}{R}", "Creature — Orc Sorcerer", """
            Ward—Pay 2 life.
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "3/2", null, null),
        face("Rouse the Eras", "{5}{R}", "Sorcery", "Create two 2/2 red and white Spirit creature tokens.", null, null, null),
    )
    private val emeritusOfPlenty = Cards.inventFaces("Prepare",
        face("Emeritus of Plenty", "{2}{G}", "Creature — Elf Druid", """
            Vigilance
            This creature enters prepared.
            Whenever this creature attacks, if you control eight or more lands, this creature becomes prepared.
        """, "3/4", null, null),
        face("Regather", "{1}{G}", "Sorcery", "Return target card from your graveyard to your hand.", null, null, null),
    )
    private val sickbayMender = Cards.inventFaces("Prepare",
        face("Sickbay Mender", "{1}{G}", "Creature — Cat Cleric", "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "2/3", null, null),
        face("Rivulet of Vigor", "{X}{G}", "Sorcery", "Target player gains X life.", null, null, null),
    )
    private val diligentFreshman = Cards.inventFaces("Prepare",
        face("Diligent Freshman", "{G}", "Creature — Bear Wizard", "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "1/1", null, null),
        face("Sprawling Sprout", "{1}{G}", "Sorcery", "Search your library for a basic land card, put that card onto the battlefield tapped, then shuffle.", null, null, null),
    )
    private val wildlandsForager = Cards.inventFaces("Prepare",
        face("Wildlands Forager", "{1}{G}{G}", "Creature — Bear Druid", """
            Deathtouch
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "4/4", null, null),
        face("Tether to Life", "{4}{G}", "Instant", "Mill seven cards. Then put a creature card from among them onto the battlefield.", null, null, null),
    )
    private val ceridwenPoetLaureate = Cards.inventFaces("Prepare",
        face("Ceridwen, Poet Laureate", "{1}{W}{B}", "Legendary Creature — Bird Bard", """
            Flying
            Whenever you cast a creature spell, Ceridwen becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "2/3", null, null),
        face("Valiant Verse", "{1}{W/B}", "Sorcery", "Put a +1/+1 counter on target creature.", null, null, null),
    )
    private val rhoskLoreEnthusiast = Cards.inventFaces("Prepare",
        face("Rhosk, Lore Enthusiast", "{R}{W}", "Legendary Creature — Vampire Cleric", "Whenever one or more cards leave your graveyard, Rhosk becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "2/3", null, null),
        face("Throw a Haymaker", "{1}{R}{W}", "Sorcery", "Mill a card. Put two +1/+1 counters on target creature. It gains trample until end of turn.", null, null, null),
    )
    private val taviVisitingScholar = Cards.inventFaces("Prepare",
        face("Tavi, Visiting Scholar", "{2}{B}{G}", "Legendary Creature — Elf Druid", """
            Tavi enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            Exile a creature card from your graveyard: Tavi becomes prepared. Activate only as a sorcery.
        """, "3/4", null, null),
        face("Vermin Companion", "{B/G}", "Sorcery", "Create a 1/1 black and green Pest creature token with \"Whenever this token attacks, you gain 1 life.\"", null, null, null),
    )

    private val tapper = Cards.invent("Quad Hex", "{W}", "Instant", "Tap target creature.")
    private val purge = Cards.invent("Quad Purge", "{R}", "Instant", "Exile target card from a graveyard.")
    private val pest = Cards.invent("Quad Pest", "{B}", "Creature — Pest", "", "1/1")

    // ---- Chunk 05: life

    @Test
    fun smirkingDuskscribeDrains() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(smirkingDuskscribe)
        alice.casts(smirkingDuskscribe)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    @Test
    fun unleashTheVerminStripsAndBreeds() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(unleashTheVermin)
        bob.hand(bear)
        alice.casts(unleashTheVermin)
        resolveAll()
        chooseIfOffered(bear)
        resolveAll()
        expect {
            bob.graveyard has bear
            alice.battlefield has "Pest Token"
        }
    }

    @Test
    fun grubnestSlothLeavesPests() = scenario {
        alice.battlefield(grubnestSloth, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = grubnestSloth)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Pest Token" } shouldBe 2 }
    }

    // ---- Chunk 06: repartee (a spell that targets a creature) and Inklings

    @Test
    fun commencementMorningCounters() = scenario {
        alice.battlefield(commencementMorning, bear, "Plains")
        alice.hand(tapper)
        bob.battlefield(ogre)
        alice.casts(tapper, target = ogre)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun wellReadInkscribeMakesInklings() = scenario {
        alice.battlefield(wellReadInkscribe, "Plains")
        alice.hand(tapper)
        bob.battlefield(ogre)
        alice.casts(tapper, target = ogre)
        resolveAll()
        expect { "Inkling Token" hasKeyword Keyword.Flying }
    }

    @Test
    fun inkformExhibitorGainsLifelink() = scenario {
        alice.battlefield(inkformExhibitor, "Plains")
        alice.hand(tapper)
        bob.battlefield(ogre)
        alice.casts(tapper, target = ogre)
        resolveAll()
        expect {
            inkformExhibitor.power shouldBe 4
            inkformExhibitor hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun practicedDisputantGrows() = scenario {
        alice.battlefield(practicedDisputant, "Plains")
        alice.hand(tapper)
        bob.battlefield(ogre)
        alice.casts(tapper, target = ogre)
        resolveAll()
        expect { practicedDisputant.power shouldBe 4 }
    }

    @Test
    fun rousingHopecallerCountersAll() = scenario {
        alice.battlefield(rousingHopecaller, bear, "Plains")
        alice.hand(tapper)
        bob.battlefield(ogre)
        alice.casts(tapper, target = ogre)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            rousingHopecaller.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun agoraGravescribeReanimates() = scenario {
        alice.battlefield(agoraGravescribe, "Plains")
        alice.hand(tapper)
        alice.graveyard(giant)
        bob.battlefield(ogre)
        alice.casts(tapper, target = ogre)
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        expect { alice.battlefield has giant }
    }

    @Test
    fun sermonizingSneermageGrows() = scenario {
        alice.battlefield(sermonizingSneermage, "Plains")
        alice.hand(tapper)
        bob.battlefield(ogre)
        alice.casts(tapper, target = ogre)
        resolveAll()
        expect { sermonizingSneermage.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun mediatorsFencerDrawsAndBlinks() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", "Plains")
        alice.hand(mediatorsFencer, tapper)
        alice.library(bear)
        bob.battlefield(ogre)
        alice.casts(mediatorsFencer, paying = listOf("Plains", "Plains", "Swamp", "Swamp"))
        resolveAll()
        expect {
            alice.hand has bear
            alice.life shouldBe 19
            bob.life shouldBe 19
        }
        alice.casts(tapper, target = ogre)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.exile has ogre }
    }

    @Test
    fun inklingPennantBearerFliesAndSurveils() = scenario {
        alice.battlefield(inklingPennantBearer, "Plains")
        alice.hand(tapper)
        alice.library(bear)
        bob.battlefield(ogre)
        alice.casts(tapper, target = ogre)
        resolveAll()
        chooseNone()
        resolveAll()
        expect { inklingPennantBearer hasKeyword Keyword.Flying }
    }

    @Test
    fun chidingRegistrarPassesItsCounters() = scenario {
        alice.battlefield("Swamp", bear)
        alice.battlefield(chidingRegistrar, counters = listOf(p1p1 to 2))
        alice.hand(doom)
        alice.casts(doom, target = chidingRegistrar)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        // the Doom targets a creature: repartee's counter comes first, so three move (CR 603.10a)
        expect { bear.counters(p1p1) shouldBe 3 }
    }

    @Test
    fun eavesdroppingAideSneaksAndDraws() = scenario {
        alice.battlefield(eavesdroppingAide, "Plains")
        alice.hand(tapper)
        alice.library(bear)
        bob.battlefield(ogre, elf)
        alice.casts(tapper, target = ogre)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(eavesdroppingAide)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(elf to eavesdroppingAide) }
    }

    @Test
    fun keenRunecasterMakesAnInkling() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(keenRunecaster)
        alice.casts(keenRunecaster)
        resolveAll()
        expect { alice.battlefield has "Inkling Token" }
    }

    @Test
    fun scathingMarginaliaLeavesThemAnInkling() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(scathingMarginalia)
        bob.battlefield(giant)
        alice.casts(scathingMarginalia, target = giant)
        resolveAll()
        expect {
            bob.graveyard has giant
            bob.battlefield has "Inkling Token"
        }
    }

    // ---- Chunks 07–09: preparation (CR 722)

    @Test
    fun crackWaylayerRetortsAndDraws() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(crackWaylayer)
        alice.library(bear)
        bob.battlefield(ogre)
        alice.casts("Crack Waylayer")
        resolveAll()
        alice.casts("Retort in Kind", target = ogre)
        resolveAll()
        if (pending is Decision.May) may(true)
        if (pending is Decision.ChooseOption) option("tap")
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            alice.hand has bear
        }
    }

    @Test
    fun emeritusOfConcordPreparesWhenOutnumbered() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(emeritusOfConcord)
        bob.battlefield(bear, elf, ogre)
        alice.casts("Emeritus of Concord")
        resolveAll()
        targetsIfAsked("alice")
        resolveAll()
        assertTrue(prepared("Emeritus of Concord"))
        alice.casts("Blades into Ploughs", target = ogre)
        resolveAll()
        expect {
            bob.exile has ogre
            bob.life shouldBe 23
        }
    }

    @Test
    fun oathswornSquireBoonLifts() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(oathswornSquire)
        alice.casts("Oathsworn Squire")
        resolveAll()
        alice.casts("Agora's Boon", target = "Oathsworn Squire")
        resolveAll()
        expect { "Oathsworn Squire" hasKeyword Keyword.Flying }
    }

    @Test
    fun pairedScholarsPrepareWhenBehindOnCards() = scenario {
        alice.battlefield(pairedScholars, "Plains", "Plains", "Plains")
        alice.library(bear, bear, bear, bear)
        bob.hand(elf)
        bob.library(elf, elf, elf)
        advanceTo(TurnStep.End)
        resolveAll()
        assertTrue(prepared(pairedScholars))
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts("Hidden Assignation", target = "bob")
        resolveAll()
        expect { alice.hand.size shouldBe 4 }
    }

    @Test
    fun penknifeLaureateDoublePurpose() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(penknifeLaureate)
        alice.casts("Penknife Laureate")
        resolveAll()
        alice.casts("Double Purpose", target = bear)
        resolveAll()
        expect { bear hasKeyword Keyword.DoubleStrike }
    }

    @Test
    fun quadComposerWateryAria() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(quadComposer)
        alice.casts("Quad Composer")
        resolveAll()
        alice.casts("Watery Aria")
        resolveAll()
        expect { "Elemental Token" hasKeyword Keyword.Flying }
    }

    @Test
    fun emeritusOfNotionsRepreparesFromTheGraveyard() = scenario {
        alice.battlefield(emeritusOfNotions, "Island")
        alice.graveyard(elf, elf, elf, elf, elf, elf, elf, elf)
        alice.library(bear, bear, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(emeritusOfNotions)
        resolveAll()
        may(true)
        resolveAll()
        assertTrue(prepared(emeritusOfNotions))
        alice.casts("Forebears' Recollection", target = "alice")
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
    }

    @Test
    fun hearteningFlierPreparesOnAttack() = scenario {
        alice.battlefield(hearteningFlier, bear, "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hearteningFlier)
        resolveAll()
        alice.casts("Leap Aloft", target = bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Flying }
    }

    @Test
    fun attunedTrioPreparesByTappingTwo() = scenario {
        alice.battlefield(attunedTrio, bear, elf, "Island")
        alice.library(ogre, giant, bear)
        alice.activates(attunedTrio, choosing = listOf(bear, elf))
        resolveAll()
        alice.casts("Thoughtsquall")
        resolveAll()
        alice.chooses(ogre, giant)
        resolveAll()
        // their order on top (CR 401.4)
        (pending as? Decision.ChooseObjects)?.let { answer(Answer.Objects(it.options)) }
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.library.size shouldBe 2
        }
    }

    @Test
    fun ilsaWardenOfFortuneLootsAndMakesFractals() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(ilsaWardenOfFortune, ogre, giant)
        alice.library(bear, elf)
        alice.casts("Ilsa, Warden of Fortune")
        resolveAll()
        alice.chooses(ogre, giant)
        resolveAll()
        alice.casts("Seer's Bounty", x = 1)
        resolveAll()
        expect { "Fractal Token".counters(p1p1) shouldBe 1 }
    }

    @Test
    fun vistaPainterVividNotion() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(vistaPainter)
        alice.library(bear, bear)
        alice.casts("Vista Painter")
        resolveAll()
        alice.casts("Vivid Notion")
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun aircoachDriverBlinksAnother() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.hand(aircoachDriver)
        alice.casts("Aircoach Driver")
        resolveAll()
        alice.casts("Board the Coach", target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 0 }
    }

    @Test
    fun grimoireSearcherStudiousReview() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(grimoireSearcher)
        alice.library(bear, ogre)
        alice.casts("Grimoire Searcher")
        resolveAll()
        alice.casts("Studious Review")
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear, ogre)
        resolveAll()
        expect { alice.graveyard.size shouldBe 2 }
    }

    @Test
    fun daringGourmandTakesANibble() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(daringGourmand)
        alice.casts("Daring Gourmand")
        resolveAll()
        alice.casts("Take a Nibble", target = "Daring Gourmand")
        resolveAll()
        expect {
            "Daring Gourmand".counters(p1p1) shouldBe 1
            alice.life shouldBe 21
        }
    }

    @Test
    fun jovialBonecallerRousesTheFallen() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(jovialBonecaller)
        alice.graveyard(ogre)
        alice.casts("Jovial Bonecaller")
        resolveAll()
        alice.casts("Rouse the Fallen", target = ogre)
        resolveAll()
        expect { alice.hand has ogre }
    }

    @Test
    fun emeritusOfSorrowTutors() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(emeritusOfSorrow)
        alice.library(bear, giant)
        alice.casts("Emeritus of Sorrow")
        resolveAll()
        alice.casts("Fiendish Mentor")
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect { alice.hand has giant }
    }

    @Test
    fun cryptScholarPreparesWithThreeCreatureCards() = scenario {
        alice.battlefield(cryptScholar, "Swamp")
        alice.graveyard(bear, elf, ogre)
        alice.library(elf, elf, elf)
        bob.library(elf, elf)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        chooseNone()
        resolveAll()
        assertTrue(prepared(cryptScholar))
        advanceTo(TurnStep.PrecombatMain)
        alice.casts("Raise the Buried", target = ogre)
        resolveAll()
        expect {
            alice.battlefield has ogre
            alice.life shouldBe 17
        }
    }

    @Test
    fun leechkeeperPreparesOnLifeGain() = scenario {
        alice.battlefield(leechkeeper, "Plains", "Swamp")
        alice.hand(heal)
        alice.casts(heal)
        resolveAll()
        alice.casts("Vein Draw")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun searingShadecoilPreparesEachFirstMain() = scenario {
        alice.battlefield(searingShadecoil, bear, "Swamp")
        alice.library(elf, elf)
        bob.library(elf, elf)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        alice.casts("Toxic Retort", target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Deathtouch
        }
    }

    @Test
    fun plottingGlibtonguePreparesAfterTwoLife() = scenario {
        alice.battlefield(plottingGlibtongue, "Plains", "Swamp", "Swamp")
        alice.hand(heal)
        alice.library(bear, bear)
        alice.casts(heal)
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        resolveAll()
        alice.casts("Blood Signature", target = "alice")
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun flaringFirechanterBoilsMana() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(flaringFirechanter)
        alice.casts("Flaring Firechanter")
        resolveAll()
        alice.casts("Boiling Chant")
        resolveAll()
        expect { alice.pool.size shouldBe 5 }
    }

    @Test
    fun emeritusOfStrifePreparesOnTheThirdSpell() = scenario {
        alice.battlefield(emeritusOfStrife, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(shock, shock, shock)
        repeat(3) {
            alice.casts(shock, target = "bob")
            resolveAll()
        }
        alice.casts("Skyfire Jolt", target = "bob")
        resolveAll()
        expect { bob.life shouldBe 11 }
    }

    @Test
    fun goblinGlassblowerMakesATreasure() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(goblinGlassblower)
        alice.casts("Goblin Glassblower")
        resolveAll()
        alice.casts("Proud Handiwork")
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
    }

    @Test
    fun vortexArtisanFlaresANonbasic() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(vortexArtisan)
        bob.battlefield(Cards.invent("Quad Ruins", null, "Land", "{T}: Add {C}."))
        alice.casts("Vortex Artisan")
        resolveAll()
        alice.casts("Flare Salvo", target = "Quad Ruins")
        resolveAll()
        expect { bob.graveyard has "Quad Ruins" }
    }

    @Test
    fun pigmentHerderCopiesTheNextSpell() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(pigmentHerder, shock)
        alice.casts("Pigment Herder")
        resolveAll()
        alice.casts("Vivid Stroke")
        resolveAll()
        alice.casts(shock, target = "bob")
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun discordScholarRousesTheEras() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(discordScholar)
        alice.casts("Discord Scholar")
        resolveAll()
        alice.casts("Rouse the Eras")
        resolveAll()
        expect { alice.battlefield.names.count { it == "Spirit Token" } shouldBe 2 }
    }

    @Test
    fun emeritusOfPlentyRegathers() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(emeritusOfPlenty)
        alice.graveyard(shock)
        alice.casts("Emeritus of Plenty")
        resolveAll()
        alice.casts("Regather", target = shock)
        resolveAll()
        expect { alice.hand has shock }
    }

    @Test
    fun sickbayMenderHealsByX() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(sickbayMender)
        alice.casts("Sickbay Mender")
        resolveAll()
        alice.casts("Rivulet of Vigor", x = 2, target = "alice")
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    @Test
    fun diligentFreshmanSprouts() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(diligentFreshman)
        alice.library(bear, "Island")
        alice.casts("Diligent Freshman")
        resolveAll()
        alice.casts("Sprawling Sprout")
        resolveAll()
        chooseIfOffered("Island")
        resolveAll()
        expect { "Island".tapped shouldBe true }
    }

    @Test
    fun wildlandsForagerTethersLife() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(wildlandsForager)
        alice.library(elf, elf, giant, elf, elf, elf, elf)
        alice.casts("Wildlands Forager")
        resolveAll()
        alice.casts("Tether to Life")
        resolveAll()
        chooseIfOffered(giant)
        resolveAll()
        expect { alice.battlefield has giant }
    }

    @Test
    fun ceridwenPreparesOnCreatureSpells() = scenario {
        alice.battlefield(ceridwenPoetLaureate, "Forest", "Plains", "Plains")
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        alice.casts("Valiant Verse", target = elf)
        resolveAll()
        expect { elf.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun rhoskPreparesWhenCardsLeaveTheGraveyard() = scenario {
        alice.battlefield(rhoskLoreEnthusiast, "Plains", "Mountain", "Mountain", "Mountain", bear)
        alice.graveyard(shock)
        alice.hand(purge)
        alice.library(elf)
        alice.casts(purge, target = shock, paying = listOf("Mountain"))
        resolveAll()
        assertTrue(prepared(rhoskLoreEnthusiast))
        alice.casts("Throw a Haymaker", target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 2
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun taviBreedsPests() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Forest", "Swamp")
        alice.hand(taviVisitingScholar)
        alice.casts("Tavi, Visiting Scholar", paying = listOf("Swamp", "Swamp", "Forest", "Forest"))
        resolveAll()
        alice.casts("Vermin Companion")
        resolveAll()
        expect { alice.battlefield has "Pest Token" }
    }

    @Test
    fun taviRepreparesFromTheGraveyard() = scenario {
        alice.battlefield(taviVisitingScholar)
        alice.graveyard(bear)
        alice.activates(taviVisitingScholar, choosing = listOf(bear))
        resolveAll()
        assertTrue(prepared(taviVisitingScholar))
    }
}
