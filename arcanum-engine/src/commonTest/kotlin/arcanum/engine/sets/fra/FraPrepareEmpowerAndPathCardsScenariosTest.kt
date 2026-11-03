package arcanum.engine.sets.fra

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fra.Fra.bear
import arcanum.engine.sets.fra.Fra.doom
import arcanum.engine.sets.fra.Fra.elf
import arcanum.engine.sets.fra.Fra.giant
import arcanum.engine.sets.fra.Fra.heal
import arcanum.engine.sets.fra.Fra.jace
import arcanum.engine.sets.fra.Fra.ogre
import arcanum.engine.sets.fra.Fra.p1p1
import arcanum.engine.sets.fra.Fra.ponder
import arcanum.engine.sets.fra.Fra.shock
import arcanum.engine.sets.fra.Fra.walker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Reality Fracture's preparation, empower Jace, Path and planeswalker cards in play (parser chunks 01–05): each card's
 * own ability on top of the mechanics [FraPrepareScenariosTest] and [FraEmpowerJaceScenariosTest] cover. */
class FraPrepareEmpowerAndPathCardsScenariosTest {
    private val augurOfTriumphs = Cards.inventFaces("Prepare",
        face("Augur of Triumphs", "{U}", "Creature — Dwarf Wizard", """
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            Whenever you scry or surveil, this creature gets +1/+1 until end of turn.
        """, "1/1", null, null),
        face("Rewind the Annals", "{1}{U}", "Sorcery", "Return target creature an opponent controls with mana value 3 or less to its owner's hand. Surveil 1.", null, null, null),
    )
    private val termSeer = Cards.inventFaces("Prepare",
        face("Term Seer", "{3}{U}", "Creature — Human Wizard", """
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            When this creature enters, surveil 1.
        """, "3/4", null, null),
        face("Faculty Review", "{2}{W/U}", "Sorcery", "Create a 2/2 colorless Wizard Soldier creature token named Cadet. Surveil 1.", null, null, null),
    )
    private val vectorPursuer = Cards.inventFaces("Prepare",
        face("Vector Pursuer", "{2}{U}", "Creature — Human Wizard", """
            Flying, prowess
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "2/3", null, null),
        face("Curve of Chance", "{2}{U}", "Sorcery", "Each player may discard their hand and draw seven cards.", null, null, null),
    )
    private val fatekeepAnnalist = Cards.inventFaces("Prepare",
        face("Fatekeep Annalist", "{1}{W/U}", "Creature — Bird Wizard", """
            Flying
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "1/2", null, null),
        face("Faculty Review", "{2}{W/U}", "Sorcery", "Create a 2/2 colorless Wizard Soldier creature token named Cadet. Surveil 1.", null, null, null),
    )
    private val carefulForeteller = Cards.inventFaces("Prepare",
        face("Careful Foreteller", "{1}{W/U}{W/U}", "Creature — Dwarf Wizard", """
            This creature enters prepared.
            Whenever you scry or surveil, creatures you control get +1/+0 until end of turn. This ability triggers only once each turn.
        """, "1/4", null, null),
        face("Faculty Review", "{2}{W/U}", "Sorcery", "Create a 2/2 colorless Wizard Soldier creature token named Cadet. Surveil 1.", null, null, null),
    )
    private val bloomvigorOutrider = Cards.inventFaces("Prepare",
        face("Bloomvigor Outrider", "{1}{G/W}", "Creature — Troll Druid", """
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            Each creature you control with a +1/+1 counter on it has vigilance.
        """, "2/2", null, null),
        face("Graft Mending", "{G/W}", "Sorcery", "Put a +1/+1 counter on target creature. You gain 1 life.", null, null, null),
    )
    private val hollowProjector = Cards.inventFaces("Prepare",
        face("Hollow Projector", "{1}{B}", "Creature — Aetherborn Warlock", """
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            Threshold — This creature gets +1/+1 as long as there are seven or more cards in your graveyard.
        """, "2/2", null, null),
        face("Drop the Terms", "{U/B}", "Sorcery", "Mill three cards. (Put the top three cards of your library into your graveyard.)", null, null, null),
    )
    private val theoremMetashade = Cards.inventFaces("Prepare",
        face("Theorem Metashade", "{2}{U/B}", "Creature — Shade Wizard", """
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            Threshold — This creature gets +1/+0 and has flying as long as there are seven or more cards in your graveyard.
        """, "2/3", null, null),
        face("Drop the Terms", "{U/B}", "Sorcery", "Mill three cards. (Put the top three cards of your library into your graveyard.)", null, null, null),
    )
    private val lineageRememberer = Cards.inventFaces("Prepare",
        face("Lineage Rememberer", "{1}{B}", "Creature — Vampire Warlock", "At the beginning of each end step, if three or more creatures died this turn, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "2/2", null, null),
        face("Ancestor's Hunger", "{B}", "Instant", "Target player draws three cards and loses 3 life.", null, null, null),
    )
    private val corridorJeerer = Cards.inventFaces("Prepare",
        face("Corridor Jeerer", "{2}{R}", "Creature — Elemental Sorcerer", """
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            {T}, Discard a card: Draw a card.
        """, "2/3", null, null),
        face("Spiteful Stanza", "{B/R}", "Sorcery", "Spiteful Stanza deals 1 damage to target opponent.", null, null, null),
    )
    private val vaingloriousSpellslinger = Cards.inventFaces("Prepare",
        face("Vainglorious Spellslinger", "{R}", "Creature — Goblin Sorcerer", """
            Prowess
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
        """, "1/1", null, null),
        face("Unscripted Scene", "{R}", "Sorcery", "You may discard a card. If you do, draw a card.", null, null, null),
    )
    private val maneaterGardener = Cards.inventFaces("Prepare",
        face("Maneater Gardener", "{1}{G}", "Creature — Elf Warlock", """
            Deathtouch
            This creature enters prepared.
            Whenever this creature deals combat damage to a player, return target land card from your graveyard to your hand.
        """, "2/3", null, null),
        face("Take Root", "{G}", "Sorcery", "Search your library for a land card, put it into your graveyard, then shuffle.", null, null, null),
    )
    private val heartgrainWhittler = Cards.inventFaces("Prepare",
        face("Heartgrain Whittler", "{G}", "Creature — Elf Artificer", """
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            {T}: Add {C}. This mana can't be spent to cast spells from your hand.
        """, "1/1", null, null),
        face("Spirit Binding", "{2}{R/G}", "Sorcery", "Create a Heartwood token. (It's a red and green artifact with \"{T}: Add {R} or {G}.\")", null, null, null),
    )
    private val lashingLyricist = Cards.inventFaces("Prepare",
        face("Lashing Lyricist", "{3}{B/R}", "Creature — Vampire Sorcerer", """
            This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            As long as an opponent was dealt noncombat damage this turn, this creature has flying and haste.
        """, "3/3", null, null),
        face("Spiteful Stanza", "{B/R}", "Sorcery", "Spiteful Stanza deals 1 damage to target opponent.", null, null, null),
    )
    private val scholasticRise = Cards.invent("Scholastic Rise", "{1}{W}", "Instant", """
        Target creature gets +2/+2 and gains flying until end of turn.
        Empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
    """)
    private val quadHerald = Cards.invent("Quad Herald", "{1}{W}", "Creature — Human Advisor", "{1}, Exile this card from your graveyard: Empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with \"[−1]: Surveil 1\" and \"[−3]: Draw a card.\")", "3/1")
    private val hexwardRegiment = Cards.invent("Hexward Regiment", "{4}{W}{W}", "Sorcery", """
        Create three 2/2 colorless Wizard Soldier creature tokens named Cadet. Empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
    """)
    private val retooledWarden = Cards.invent("Retooled Warden", "{1}{W}", "Creature — Human Soldier", "Whenever this creature attacks, empower Jace X, where X is the number of creatures you control. (Put that many loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with \"[−1]: Surveil 1\" and \"[−3]: Draw a card.\")", "3/2")
    private val contingencyPlanning = Cards.invent("Contingency Planning", "{3}{U}", "Enchantment", """
        When this enchantment enters, the owner of up to one other target nonland permanent puts it on their choice of the top or bottom of their library.
        Whenever you cast your first noncreature spell each turn, empower Jace 1. (Put a loyalty counter on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
    """)
    private val apprenticesEpiphany = Cards.invent("Apprentice's Epiphany", "{3}{U}", "Sorcery", """
        Empower Jace 6. (Put six loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        Draw a card.
    """)
    private val amendOldSorrows = Cards.invent("Amend Old Sorrows", "{3}{B}", "Sorcery", """
        Return target creature or planeswalker card with mana value 6 or less from your graveyard to the battlefield.
        Empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
    """)
    private val resolveToDismay = Cards.invent("Resolve to Dismay", "{1}{B}", "Sorcery", """
        Target opponent reveals their hand. You choose a nonland permanent card from it. That player discards that card.
        Empower Jace 1. (Put a loyalty counter on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
    """)
    private val gorgonsLastClemency = Cards.invent("Gorgon's Last Clemency", "{B}{B}", "Sorcery", """
        Choose one —
        • You lose 2 life. Destroy target creature or planeswalker.
        • You lose 2 life. Empower Jace 6. (Put six loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
    """)
    private val admissionRefused = Cards.invent("Admission Refused", "{1}{R}", "Sorcery", """
        Admission Refused deals 3 damage to any target.
        Empower Jace 1. (Put a loyalty counter on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
    """)
    private val twinmawAdder = Cards.invent("Twinmaw Adder", "{1}{G}", "Creature — Snake", """
        Deathtouch
        When this creature enters, empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
    """, "1/1")
    private val heraldOfSwellingEchoes = Cards.invent("Herald of Swelling Echoes", "{G}{U}", "Creature — Avatar", """
        Landfall — Whenever a land you control enters, empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        Planeswalkers you control have "[−10]: Put a +1/+1 counter on target creature for each land you control."
    """, "2/3")
    private val gorgonsDefiance = Cards.invent("Gorgon's Defiance", "{1}{G/U}", "Sorcery", """
        Put a +1/+1 counter on up to one target creature. It gains vigilance until end of turn.
        Empower Jace 4. (Put four loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
    """)
    private val fatekeepCharm = Cards.invent("Fatekeep Charm", "{W}{U}", "Instant", """
        Choose one —
        • Draw a card. Empower Jace 2.
        • Return target spell or creature to its owner's hand.
        • Creatures you control get +1/+2 until end of turn.
    """)
    private val pathOfTheMender = Cards.invent("Path of the Mender", "{3}{W}", "Legendary Enchantment", """
        When Path of the Mender enters, empower Jace 5. (Put five loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        Planeswalkers you control have "[−2]: Create a 2/2 colorless Wizard Soldier creature token named Cadet. Surveil 1."
    """)
    private val pathOfTheTutor = Cards.invent("Path of the Tutor", "{2}{W}", "Legendary Enchantment", """
        When Path of the Tutor enters, empower Jace 5. (Put five loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        Whenever you gain life, put a loyalty counter on each planeswalker you control.
    """)
    private val pathOfTheDoomcaller = Cards.invent("Path of the Doomcaller", "{2}{B}", "Legendary Enchantment", """
        When Path of the Doomcaller enters, empower Jace 5. (Put five loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        Planeswalkers you control have "[−2]: You may sacrifice a creature. If you do, create a 4/4 green Beast creature token with trample."
    """)
    private val pathOfTheGravecaller = Cards.invent("Path of the Gravecaller", "{1}{B}", "Legendary Enchantment", """
        When Path of the Gravecaller enters, empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        Whenever a creature you control dies, put a loyalty counter on each planeswalker you control.
    """)
    private val pathOfTheFlamecaller = Cards.invent("Path of the Flamecaller", "{1}{R}", "Legendary Enchantment", """
        When Path of the Flamecaller enters, empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        Planeswalkers you control have "[+1]: Add {R}."
    """)
    private val pathOfTheBattlelord = Cards.invent("Path of the Battlelord", "{2}{R}", "Legendary Enchantment", """
        When Path of the Battlelord enters, empower Jace 5. (Put five loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        Planeswalkers you control have "[−4]: This planeswalker deals 2 damage to up to one target creature or planeswalker and 2 damage to target player."
    """)
    private val pathOfTheBeastcaller = Cards.invent("Path of the Beastcaller", "{4}{G}", "Legendary Enchantment", """
        When Path of the Beastcaller enters, empower Jace 7. (Put seven loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        Planeswalkers you control have "[−4]: Create a 4/4 green Beast creature token with trample."
    """)
    private val orvaneSteadfast = Cards.invent("Orvane Steadfast", "{1}{W}", "Legendary Planeswalker — Ajani", """
        Whenever you gain life, put a loyalty counter on Orvane.
        [0]: You gain 1 life.
        [−4]: Create a 2/2 white Cat Soldier creature token named Orvane's Pridemate with "Whenever you gain life, put a +1/+1 counter on this token."
        [−10]: You get an emblem with "Creatures you control get +2/+2."
    """, loyalty = "2")
    private val orvaneRelentless = Cards.invent("Orvane Relentless", "{4}{R}{R}", "Legendary Planeswalker — Ajani", """
        Whenever you activate a loyalty ability, create a 2/2 colorless Wizard Soldier creature token named Cadet.
        [+1]: Creatures you control get +1/+0 and gain haste until end of turn.
        [−2]: Discard your hand, then draw a card for each creature you control.
        [−3]: Orvane deals 4 damage to each creature except for tokens you control.
    """, loyalty = "5")
    private val dovanSyndicateJurist = Cards.invent("Dovan, Syndicate Jurist", "{1}{W}", "Legendary Creature — Human Advisor", """
        Flying
        Planeswalkers you control have "No more than one creature can attack this planeswalker each combat."
        {T}: Target creature with a +1/+1 counter on it gains flying until end of turn.
    """, "2/1")
    private val bequeathTheEmber = Cards.invent("Bequeath the Ember", "{3}{G}{U}", "Sorcery", "You may sacrifice a planeswalker. If you do, search your library for a planeswalker card, put it onto the battlefield, then shuffle.")
    private val faithfulMentorsCall = Cards.invent("Faithful Mentor's Call", "{W}", "Instant", "Search your library for a planeswalker card, reveal it, then shuffle and put that card on top.")

    // ---- Chunk 01: preparation, white and blue

    // CR 722.3c, 701.25a: the copy's surveil triggers the creature's own scry-or-surveil ability
    @Test
    fun augurOfTriumphsBouncesAndGrows() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(augurOfTriumphs)
        alice.library(ogre)
        bob.battlefield(bear)
        alice.casts("Augur of Triumphs")
        resolveAll()
        assertTrue(prepared("Augur of Triumphs"))
        alice.casts("Rewind the Annals", target = bear)
        resolveAll()
        chooseNone()
        resolveAll()
        expect {
            bob.hand has bear
            "Augur of Triumphs".power shouldBe 2
        }
    }

    // CR 722.3c, 111.4: a token named Cadet
    @Test
    fun termSeerSurveilsAndMakesACadet() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Plains", "Plains", "Plains")
        alice.hand(termSeer)
        alice.library(ogre, giant, bear)
        alice.casts("Term Seer", paying = listOf("Island", "Island", "Island", "Island"))
        resolveAll()
        chooseNone()
        resolveAll()
        alice.casts("Faculty Review")
        resolveAll()
        chooseNone()
        resolveAll()
        expect {
            alice.battlefield has "Cadet"
            "Cadet".power shouldBe 2
        }
    }

    // CR 722.3c: "each player may discard their hand and draw seven cards"
    @Test
    fun vectorPursuerRefillsWhoWants() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(vectorPursuer, ogre)
        alice.library(bear, bear, bear, bear, bear, bear, bear, bear)
        bob.hand(giant)
        alice.casts("Vector Pursuer")
        resolveAll()
        alice.casts("Curve of Chance")
        repeat(4) {
            resolveAll()
            (pending as? Decision.May)?.let { answer(Answer.YesNo(it.player == alice.id)) }
        }
        expect {
            alice.hand.size shouldBe 7
            alice.graveyard has ogre
            bob.hand has giant
            "Vector Pursuer".power shouldBe 3
        }
    }

    @Test
    fun fatekeepAnnalistGivesAFacultyReview() = scenario {
        alice.battlefield("Plains", "Island")
        alice.hand(fatekeepAnnalist)
        alice.casts("Fatekeep Annalist")
        resolveAll()
        expect {
            alice.exile has "Faculty Review"
            "Fatekeep Annalist" hasKeyword Keyword.Flying
        }
    }

    // CR 603.2h: "This ability triggers only once each turn"
    @Test
    fun carefulForetellerPumpsOnSurveil() = scenario {
        alice.battlefield("Plains", "Plains", "Island", "Island", "Island", "Island", bear)
        alice.hand(carefulForeteller)
        alice.library(ogre, giant)
        alice.casts("Careful Foreteller", paying = listOf("Plains", "Plains", "Island"))
        resolveAll()
        alice.casts("Faculty Review")
        resolveAll()
        chooseNone()
        resolveAll()
        expect {
            bear.power shouldBe 3
            "Careful Foreteller".power shouldBe 2
        }
    }

    @Test
    fun bloomvigorOutriderGivesCounteredCreaturesVigilance() = scenario {
        alice.battlefield("Forest", "Forest", "Plains")
        alice.hand(bloomvigorOutrider)
        alice.casts("Bloomvigor Outrider")
        resolveAll()
        expect { "Bloomvigor Outrider" lacksKeyword Keyword.Vigilance }
        alice.casts("Graft Mending", target = "Bloomvigor Outrider")
        resolveAll()
        expect {
            "Bloomvigor Outrider" hasKeyword Keyword.Vigilance
            alice.life shouldBe 21
        }
    }

    // CR 702.120 (ability word): threshold counts seven cards
    @Test
    fun hollowProjectorGrowsAtThreshold() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(hollowProjector)
        alice.graveyard(bear, bear, bear, bear)
        alice.library(ogre, ogre, ogre, giant)
        alice.casts("Hollow Projector")
        resolveAll()
        expect { "Hollow Projector".power shouldBe 2 }
        alice.casts("Drop the Terms")
        resolveAll()
        expect {
            "Hollow Projector".power shouldBe 3
            "Hollow Projector".toughness shouldBe 3
        }
    }

    @Test
    fun theoremMetashadeFliesAtThreshold() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Island")
        alice.hand(theoremMetashade)
        alice.graveyard(bear, bear, bear, bear)
        alice.library(ogre, ogre, ogre, giant)
        alice.casts("Theorem Metashade")
        resolveAll()
        alice.casts("Drop the Terms")
        resolveAll()
        expect {
            "Theorem Metashade".power shouldBe 3
            "Theorem Metashade" hasKeyword Keyword.Flying
        }
    }

    // ---- Chunk 02: preparation, black, red and green

    // CR 603.4: the intervening "if" counts creatures that died this turn
    @Test
    fun lineageRemembererPreparesAfterThreeDeaths() = scenario {
        alice.battlefield(lineageRememberer, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(doom, doom, doom)
        alice.library(bear, bear, bear, bear)
        bob.battlefield(elf, elf, elf)
        repeat(3) {
            alice.casts(doom, target = elf)
            resolveAll()
        }
        advanceTo(TurnStep.End)
        resolveAll()
        assertTrue(prepared("Lineage Rememberer"))
        alice.casts("Ancestor's Hunger", target = "alice")
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            alice.life shouldBe 17
        }
    }

    @Test
    fun corridorJeererRummages() = scenario {
        alice.battlefield(corridorJeerer)
        alice.hand(bear)
        alice.library(ogre)
        alice.activates(corridorJeerer, choosing = listOf(bear))
        resolveAll()
        expect {
            alice.hand has ogre
            alice.graveyard has bear
        }
    }

    @Test
    fun corridorJeererStanzaPingsAnOpponent() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Swamp")
        alice.hand(corridorJeerer)
        alice.casts("Corridor Jeerer", paying = listOf("Mountain", "Mountain", "Mountain"))
        resolveAll()
        alice.casts("Spiteful Stanza", target = "bob")
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun vaingloriousSpellslingerLoots() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(vaingloriousSpellslinger, bear)
        alice.library(ogre)
        alice.casts("Vainglorious Spellslinger")
        resolveAll()
        alice.casts("Unscripted Scene")
        resolveAll()
        if (pending is Decision.May) may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect {
            alice.hand has ogre
            alice.graveyard has bear
        }
    }

    @Test
    fun maneaterGardenerReturnsALandOnDamage() = scenario {
        alice.battlefield(maneaterGardener)
        alice.graveyard("Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(maneaterGardener)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        targetsIfAsked("Forest")
        resolveAll()
        expect {
            alice.hand has "Forest"
            bob.life shouldBe 18
        }
    }

    @Test
    fun maneaterGardenerTakeRootBinsALand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(maneaterGardener)
        alice.library(bear, "Swamp", ogre)
        alice.casts("Maneater Gardener")
        resolveAll()
        alice.casts("Take Root")
        resolveAll()
        alice.chooses("Swamp")
        resolveAll()
        expect { alice.graveyard has "Swamp" }
    }

    // CR 111.10 (predefined Heartwood token per the reminder text)
    @Test
    fun heartgrainWhittlerBindsAHeartwood() = scenario {
        alice.battlefield("Forest", "Mountain", "Mountain", "Forest")
        alice.hand(heartgrainWhittler)
        alice.casts("Heartgrain Whittler", paying = listOf("Forest"))
        resolveAll()
        alice.casts("Spirit Binding")
        resolveAll()
        expect { alice.battlefield has "Heartwood Token" }
    }

    // CR 611.3a: flying and haste while an opponent has been dealt noncombat damage this turn
    @Test
    fun lashingLyricistFliesAfterNoncombatDamage() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(lashingLyricist)
        alice.casts("Lashing Lyricist", paying = listOf("Swamp", "Swamp", "Swamp", "Swamp"))
        resolveAll()
        expect { "Lashing Lyricist" lacksKeyword Keyword.Flying }
        alice.casts("Spiteful Stanza", target = "bob")
        resolveAll()
        expect {
            "Lashing Lyricist" hasKeyword Keyword.Flying
            "Lashing Lyricist" hasKeyword Keyword.Haste
        }
    }

    // ---- Chunk 03: empower Jace (CR 701.71a)

    @Test
    fun scholasticRisePumpsAndEmpowers() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(scholasticRise)
        alice.casts(scholasticRise, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Flying
        }
        assertEquals(2, loyalty(jace))
    }

    @Test
    fun quadHeraldEmpowersFromTheGraveyard() = scenario {
        alice.battlefield("Plains")
        alice.graveyard(quadHerald)
        alice.activates(quadHerald)
        resolveAll()
        assertEquals(2, loyalty(jace))
        expect { alice.exile has quadHerald }
    }

    @Test
    fun hexwardRegimentMakesThreeCadets() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(hexwardRegiment)
        alice.casts(hexwardRegiment)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Cadet" } shouldBe 3 }
        assertEquals(2, loyalty(jace))
    }

    @Test
    fun retooledWardenEmpowersByCreatures() = scenario {
        alice.battlefield(retooledWarden, bear, elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(retooledWarden)
        resolveAll()
        assertEquals(3, loyalty(jace))
    }

    // CR 401.4: the owner chooses top or bottom
    @Test
    fun contingencyPlanningPutsAPermanentAway() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(contingencyPlanning)
        bob.battlefield(ogre)
        bob.library(bear)
        alice.casts(contingencyPlanning)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        if (pending is Decision.ChooseOption) option("top")
        resolveAll()
        expect { bob.library.names.first() shouldBe ogre }
    }

    @Test
    fun contingencyPlanningEmpowersOnTheFirstNoncreatureSpell() = scenario {
        alice.battlefield(contingencyPlanning, "Island", "Island")
        alice.hand(ponder, ponder)
        alice.library(bear, bear)
        alice.casts(ponder)
        resolveAll()
        alice.casts(ponder)
        resolveAll()
        assertEquals(1, loyalty(jace))
    }

    @Test
    fun apprenticesEpiphanyEmpowersSix() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(apprenticesEpiphany)
        alice.library(bear)
        alice.casts(apprenticesEpiphany)
        resolveAll()
        assertEquals(6, loyalty(jace))
        expect { alice.hand has bear }
    }

    @Test
    fun amendOldSorrowsReanimates() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(amendOldSorrows)
        alice.graveyard(giant)
        alice.casts(amendOldSorrows, target = giant)
        resolveAll()
        expect { alice.battlefield has giant }
        assertEquals(2, loyalty(jace))
    }

    @Test
    fun resolveToDismayTakesAPermanentCard() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(resolveToDismay)
        bob.hand(bear, "Island", shock)
        alice.casts(resolveToDismay, target = "bob")
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect {
            bob.graveyard has bear
            bob.hand has shock
        }
        assertEquals(1, loyalty(jace))
    }

    @Test
    fun gorgonsLastClemencyDestroys() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(gorgonsLastClemency)
        bob.battlefield(ogre)
        alice.casts(gorgonsLastClemency, modes = listOf(0), target = ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.life shouldBe 18
        }
    }

    @Test
    fun gorgonsLastClemencyEmpowersSix() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(gorgonsLastClemency)
        alice.casts(gorgonsLastClemency, modes = listOf(1))
        resolveAll()
        assertEquals(6, loyalty(jace))
        expect { alice.life shouldBe 18 }
    }

    @Test
    fun admissionRefusedBurnsAndEmpowers() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(admissionRefused)
        alice.casts(admissionRefused, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
        assertEquals(1, loyalty(jace))
    }

    @Test
    fun twinmawAdderEmpowersOnEntering() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(twinmawAdder)
        alice.casts(twinmawAdder)
        resolveAll()
        assertEquals(2, loyalty(jace))
    }

    // CR 207.2c (landfall), 606.3: a granted loyalty ability
    @Test
    fun heraldOfSwellingEchoesEmpowersOnLandfallAndGrantsMinusTen() = scenario {
        alice.battlefield(heraldOfSwellingEchoes, "Forest", bear)
        alice.battlefield(walker, counters = listOf(CounterKind.Loyalty to 10))
        alice.hand("Island")
        alice.plays("Island")
        resolveAll()
        assertEquals(2, loyalty(jace))
        alice.activates(walker, ability = 1, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun gorgonsDefianceCountersAndEmpowersFour() = scenario {
        alice.battlefield("Forest", "Island", bear)
        alice.hand(gorgonsDefiance)
        alice.casts(gorgonsDefiance, target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Vigilance
        }
        assertEquals(4, loyalty(jace))
    }

    @Test
    fun fatekeepCharmDrawsAndEmpowers() = scenario {
        alice.battlefield("Plains", "Island")
        alice.hand(fatekeepCharm)
        alice.library(bear)
        alice.casts(fatekeepCharm, modes = listOf(0))
        resolveAll()
        expect { alice.hand has bear }
        assertEquals(2, loyalty(jace))
    }

    @Test
    fun fatekeepCharmBouncesASpell() = scenario {
        alice.battlefield("Plains", "Island")
        alice.hand(fatekeepCharm)
        bob.battlefield("Mountain")
        bob.hand(shock)
        alice.passes()
        bob.casts(shock, target = "alice")
        bob.passes()
        alice.casts(fatekeepCharm, modes = listOf(1), target = shock)
        resolveAll()
        expect {
            bob.hand has shock
            alice.life shouldBe 20
        }
    }

    // ---- Chunk 04: Paths (Ways) — empower on entering and a granted loyalty ability or trigger

    @Test
    fun pathOfTheMenderGrantsACadetAbility() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(pathOfTheMender)
        alice.library(bear, ogre)
        alice.casts(pathOfTheMender)
        resolveAll()
        assertEquals(5, loyalty(jace))
        alice.activates(jace, ability = 2)
        resolveAll()
        chooseNone()
        resolveAll()
        expect { alice.battlefield has "Cadet" }
        assertEquals(3, loyalty(jace))
    }

    @Test
    fun pathOfTheTutorLoyaltyOnLifeGain() = scenario {
        alice.battlefield(pathOfTheTutor, "Plains")
        alice.battlefield(walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.hand(heal)
        alice.casts(heal)
        resolveAll()
        assertEquals(4, loyalty(walker))
    }

    @Test
    fun pathOfTheDoomcallerTradesACreatureForABeast() = scenario {
        alice.battlefield(pathOfTheDoomcaller, bear)
        alice.battlefield(walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.activates(walker, ability = 1)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect {
            alice.graveyard has bear
            "Beast Token".power shouldBe 4
            "Beast Token" hasKeyword Keyword.Trample
        }
    }

    @Test
    fun pathOfTheGravecallerLoyaltyOnDeaths() = scenario {
        alice.battlefield(pathOfTheGravecaller, bear, "Swamp")
        alice.battlefield(walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.hand(doom)
        alice.casts(doom, target = bear)
        resolveAll()
        assertEquals(4, loyalty(walker))
    }

    @Test
    fun pathOfTheFlamecallerGrantsMana() = scenario {
        alice.battlefield(pathOfTheFlamecaller)
        alice.battlefield(walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.activates(walker, ability = 1)
        resolveAll()
        expect { alice.pool.size shouldBe 1 }
        assertEquals(4, loyalty(walker))
    }

    @Test
    fun pathOfTheBattlelordSplitsDamage() = scenario {
        alice.battlefield(pathOfTheBattlelord)
        alice.battlefield(walker, counters = listOf(CounterKind.Loyalty to 5))
        bob.battlefield(bear)
        alice.activates(walker, ability = 1, targets = listOf(bear, "bob"))
        resolveAll()
        expect {
            bob.graveyard has bear
            bob.life shouldBe 18
        }
    }

    @Test
    fun pathOfTheBeastcallerEmpowersSevenAndMakesBeasts() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(pathOfTheBeastcaller)
        alice.casts(pathOfTheBeastcaller)
        resolveAll()
        assertEquals(7, loyalty(jace))
        alice.activates(jace, ability = 2)
        resolveAll()
        expect { alice.battlefield has "Beast Token" }
        assertEquals(3, loyalty(jace))
    }

    // ---- Chunk 05: planeswalkers

    @Test
    fun orvaneSteadfastGrowsOnLifeGain() = scenario {
        alice.battlefield(orvaneSteadfast, counters = listOf(CounterKind.Loyalty to 2))
        alice.activates(orvaneSteadfast, ability = 0)
        resolveAll()
        expect { alice.life shouldBe 21 }
        assertEquals(3, loyalty(orvaneSteadfast))
    }

    @Test
    fun orvaneSteadfastMakesAPridemate() = scenario {
        alice.battlefield(orvaneSteadfast, counters = listOf(CounterKind.Loyalty to 5))
        alice.battlefield("Plains")
        alice.hand(heal)
        alice.activates(orvaneSteadfast, ability = 1)
        resolveAll()
        alice.casts(heal)
        resolveAll()
        expect { "Orvane's Pridemate".counters(p1p1) shouldBe 1 }
        assertEquals(2, loyalty(orvaneSteadfast))
    }

    // CR 606.4: the trigger resolves first, so the new Cadet gets the bonus
    @Test
    fun orvaneRelentlessMakesACadetPerLoyaltyAbility() = scenario {
        alice.battlefield(orvaneRelentless, counters = listOf(CounterKind.Loyalty to 5))
        alice.activates(orvaneRelentless, ability = 0)
        resolveAll()
        expect {
            "Cadet".power shouldBe 3
            "Cadet" hasKeyword Keyword.Haste
        }
    }

    @Test
    fun orvaneRelentlessSparesItsTokens() = scenario {
        alice.battlefield(orvaneRelentless, counters = listOf(CounterKind.Loyalty to 5))
        alice.battlefield(bear)
        bob.battlefield(ogre)
        alice.activates(orvaneRelentless, ability = 2)
        resolveAll()
        expect {
            alice.battlefield has "Cadet"
            alice.graveyard has bear
            bob.graveyard has ogre
        }
    }

    @Test
    fun orvaneRelentlessRedrawsByCreatures() = scenario {
        alice.battlefield(orvaneRelentless, counters = listOf(CounterKind.Loyalty to 5))
        alice.battlefield(bear)
        alice.hand(ogre, giant)
        alice.library(elf, elf, elf)
        alice.activates(orvaneRelentless, ability = 1)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.graveyard has giant
        }
    }

    @Test
    fun dovanSyndicateJuristGivesFlying() = scenario {
        alice.battlefield(dovanSyndicateJurist)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.activates(dovanSyndicateJurist, target = bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Flying }
    }

    // CR 506.5: a restriction on how many creatures attack a planeswalker
    @Test
    fun dovanSyndicateJuristLetsOneAttackAPlaneswalker() = scenario {
        alice.battlefield(dovanSyndicateJurist)
        alice.battlefield(walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.library(elf)
        bob.battlefield(bear, ogre)
        bob.library(elf)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        assertFailsWith<IllegalArgumentException> { bob.attacks(bear, ogre, defender = walker) }
        bob.attacks(bear, defender = walker)
    }

    @Test
    fun bequeathTheEmberSwapsAPlaneswalker() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Island", "Island")
        alice.battlefield(walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.hand(bequeathTheEmber)
        alice.library(bear, orvaneSteadfast)
        alice.casts(bequeathTheEmber)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects && (pending as Decision.ChooseObjects).options.contains(card(walker))) alice.chooses(walker)
        resolveAll()
        alice.chooses(orvaneSteadfast)
        resolveAll()
        expect {
            alice.graveyard has walker
            alice.battlefield has orvaneSteadfast
        }
    }

    @Test
    fun faithfulMentorsCallTopsAPlaneswalker() = scenario {
        alice.battlefield("Plains")
        alice.hand(faithfulMentorsCall)
        alice.library(bear, ogre, orvaneSteadfast)
        alice.casts(faithfulMentorsCall)
        resolveAll()
        alice.chooses(orvaneSteadfast)
        resolveAll()
        expect { alice.library.names.first() shouldBe orvaneSteadfast }
    }
}
