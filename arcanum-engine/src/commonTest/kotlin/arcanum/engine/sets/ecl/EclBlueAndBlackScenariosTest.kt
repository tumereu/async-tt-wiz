package arcanum.engine.sets.ecl

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ecl.Ecl.bear
import arcanum.engine.sets.ecl.Ecl.bigElemental
import arcanum.engine.sets.ecl.Ecl.bigSpell
import arcanum.engine.sets.ecl.Ecl.elf
import arcanum.engine.sets.ecl.Ecl.elfMatron
import arcanum.engine.sets.ecl.Ecl.faerie
import arcanum.engine.sets.ecl.Ecl.goblin
import arcanum.engine.sets.ecl.Ecl.jolt
import arcanum.engine.sets.ecl.Ecl.m1m1
import arcanum.engine.sets.ecl.Ecl.merfolk
import arcanum.engine.sets.ecl.Ecl.ogre
import arcanum.engine.sets.ecl.Ecl.ponder
import arcanum.engine.sets.ecl.Ecl.redBear
import arcanum.engine.sets.ecl.Ecl.totem
import kotlin.test.Test
import kotlin.test.assertEquals

/** Lorwyn Eclipsed's blue and black cards in play (chunks 11–12). */
class EclBlueAndBlackScenariosTest {
    private val tidewrightsBulwark = Cards.invent("Tidewright's Bulwark", "{1}{U}", "Enchantment — Aura", """
        Flash
        Enchant creature you control
        When this Aura enters, enchanted creature gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)
        Enchanted creature gets +1/+2.
    """)
    private val upsetterOfTides = Cards.invent("Upsetter of Tides", "{3}{U}{U}", "Creature — Merfolk Wizard", """
        Flash
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        When this creature enters, return up to one other target nonland permanent to its owner's hand.
    """, "3/3")
    private val glamourmite = Cards.invent("Glamourmite", "{2}{U}", "Creature — Faerie Rogue", """
        Flash
        Flying
        When this creature enters, choose one —
        • Tap target creature.
        • Untap target creature.
    """, "2/2")
    private val valeSylvanesRebuke = Cards.invent("Vale Sylvane's Rebuke", "{2}{U}{U}", "Instant", """
        This spell can't be countered.
        Counter all spells your opponents control and all abilities your opponents control. Create a 1/1 blue and black Faerie creature token with flying for each spell and ability countered this way.
    """)
    private val phantasmWeavers = Cards.invent("Phantasm Weavers", "{4}{U}", "Creature — Faerie Wizard", """
        You may cast this spell as though it had flash if you control a Faerie.
        Flying
        This creature has hexproof as long as it's untapped. (It can't be the target of spells or abilities your opponents control.)
    """, "4/3")
    private val emberkinSeer = Cards.invent("Emberkin Seer", "{2}{U}", "Creature — Elemental Wizard", "Whenever you cast a spell with mana value 4 or greater, this creature gets +2/+0 and gains vigilance until end of turn.", "2/4")
    private val soaringVisions = Cards.invent("Soaring Visions", "{3}{U}{U}", "Enchantment — Aura", """
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        Enchant creature
        When this Aura enters, draw a card.
        Enchanted creature gets +2/+2 and has flying.
    """)
    private val mirrorshape = Cards.invent("Mirrorshape", "{4}{U}{U}", "Instant", "Each nonland permanent you control becomes a copy of target non-Aura permanent.")
    private val fleeAsOne = Cards.invent("Flee As One", "{1}{U}", "Instant", "Choose two target creatures controlled by different players. Return those creatures to their owners' hands.")
    private val highcloudGlider = Cards.invent("Highcloud Glider", "{4}{U}", "Creature — Elemental", """
        Flying
        When this creature enters, target creature gains flying until end of turn.
        Basic landcycling {1}{U} ({1}{U}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
    """, "3/5")
    private val splitflock = Cards.invent("Splitflock", "{7}{U}{U}", "Creature — Elemental", """
        This spell costs {X} less to cast, where X is the greatest mana value among Elementals you control.
        Flying
        When this creature enters, if you cast it, return all non-Elemental creatures to their owners' hands.
    """, "5/5")
    private val brushAside = Cards.invent("Brush Aside", "{2}{U}{U}", "Instant", """
        This spell costs {2} less to cast if a creature is attacking you.
        The owner of target spell or creature puts it on their choice of the top or bottom of their library.
    """)
    private val velmorFrostcaller = Cards.invent("Velmor Frostcaller", "{3}{U}", "Creature — Elemental Wizard", "Whenever you cast a spell with mana value 4 or greater, draw a card.", "2/4")
    private val chronoRinse = Cards.invent("Chrono Rinse", "{3}{U}", "Sorcery", """
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        The owner of target nonland permanent puts it into their library second from the top or on the bottom.
    """)
    private val cravingForSelf = Cards.invent("Craving for Self", "{2}{U}", "Instant", "Draw three cards. Then discard two cards unless you discard a creature card.")
    private val suddenSuccor = Cards.invent("Sudden Succor", "{3}{U}{U}", "Instant", """
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        Draw three cards, then discard a card.
    """)
    private val uninvitedPixie = Cards.invent("Uninvited Pixie", "{1}{U}", "Creature — Faerie Rogue", """
        Flying
        Whenever you cast a spell during an opponent's turn, surveil 2. (Look at the top two cards of your library. You may put any number of them into your graveyard and the rest on top of your library in any order.)
    """, "2/1")
    private val driftwineParting = Cards.invent("Driftwine Parting", "{5}{U}{U}", "Kindred Sorcery — Merfolk", """
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        Return one or two target nonland permanents to their owners' hands. Then if you control a Merfolk, create a 1/1 white and blue Merfolk creature token for each permanent returned to its owner's hand this way.
    """)
    private val grannysVerdict = Cards.invent("Granny's Verdict", "{1}{B}", "Sorcery", """
        Choose one —
        • Target opponent reveals their hand. You choose a nonland permanent card from it. That player discards that card.
        • Target creature gets -2/-2 until end of turn.
    """)
    private val hookedLancet = Cards.invent("Hooked Lancet", "{1}{B}", "Artifact — Equipment", """
        Flash
        When this Equipment enters, attach it to target creature you control. That creature gains wither until end of turn. (It deals damage to creatures in the form of -1/-1 counters.)
        Equipped creature gets +1/+2.
        Equip {2}
    """)
    private val gallFlaskHobgoblin = Cards.invent("Gall-Flask Hobgoblin", "{B}", "Creature — Goblin Assassin", "When this creature dies, put a -1/-1 counter on up to one target creature.", "1/1")
    private val hobgoblinTrickster = Cards.invent("Hobgoblin Trickster", "{1}{B}", "Creature — Goblin Warrior", "Whenever you attack, target attacking Goblin you control gets +1/+0 until end of turn.", "1/3")
    private val gloomFalls = Cards.invent("Gloom Falls", "{2}{B}{B}", "Sorcery", "Put two -1/-1 counters on each creature.")
    private val duskpalmMourner = Cards.invent("Duskpalm Mourner", "{3}{B}", "Creature — Elf Warlock", """
        Menace (This creature can't be blocked except by two or more creatures.)
        When this creature enters, mill three cards. Then if there is an Elf card in your graveyard, each opponent loses 2 life and you gain 2 life. (To mill three cards, put the top three cards of your library into your graveyard.)
    """, "3/3")
    private val murkRender = Cards.invent("Murk Render", "{3}{B}{B}", "Creature — Elf Assassin", "When this creature enters, target creature you control gets +X/+0 until end of turn and up to one target creature an opponent controls gets -0/-X until end of turn, where X is the number of Elves you control plus the number of Elf cards in your graveyard.", "4/4")
    private val steelGuardElf = Cards.invent("Steel-Guard Elf", "{1}{B}", "Creature — Elf Warrior", """
        Discard a card: This creature gains indestructible until end of turn. Tap it. (Damage and effects that say "destroy" don't destroy it. If its toughness is 0 or less, it still dies.)
    """, "3/1")
    private val nightbloomDrainer = Cards.invent("Nightbloom Drainer", "{2}{B}", "Creature — Elf Warlock", "Whenever this creature attacks, you draw a card and lose 1 life.", "2/1")
    private val duskhymnRefrain = Cards.invent("Duskhymn Refrain", "{1}{B}{B}", "Legendary Enchantment", """
        Players can't draw cards or gain life.
        At the beginning of each player's draw step, that player loses 3 life, searches their library for a card, puts it into their hand, then shuffles.
    """)
    private val nightTerrorSeeder = Cards.invent("Night Terror Seeder", "{3}{B}", "Creature — Faerie Assassin", """
        Flying, lifelink
        Whenever you cast a spell during an opponent's turn, put a -1/-1 counter on up to one target creature.
    """, "2/3")
    private val scarknifesSpite = Cards.invent("Scarknife's Spite", "{B}", "Instant", "Target creature you control gains deathtouch and lifelink until end of turn. When that creature dies this turn, create a 2/2 black and green Elf creature token.")
    private val samplerOfGoods = Cards.invent("Sampler of Goods", "{2}{B}", "Creature — Goblin Warlock", "When this creature enters, target opponent reveals X cards from their hand, where X is the number of Goblins you control. You choose one of those cards. That player exiles it. If an instant or sorcery card is exiled this way, you may cast it for as long as you control this creature, and mana of any type can be spent to cast that spell.", "3/2")

    private fun Scenario.targets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    /** It's bob's turn, in his first main phase, and he passes priority to alice. */
    private fun Scenario.inBobsTurn() {
        turn(active = bob)
    }

    @Test
    fun tidewrightsBulwarkProtects() = scenario {
        alice.battlefield("Island", "Island", bear)
        alice.hand(tidewrightsBulwark)
        alice.casts(tidewrightsBulwark, target = bear)
        resolveAll()
        expect {
            bear.toughness shouldBe 4
            bear hasKeyword Keyword.Hexproof
        }
    }

    @Test
    fun upsetterOfTidesBounces() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(upsetterOfTides)
        bob.battlefield(totem)
        alice.casts(upsetterOfTides)
        resolveAll()
        targets(totem)
        resolveAll()
        expect { bob.hand has totem }
    }

    // CR 700.2b: a modal triggered ability: the mode is chosen as it's put on the Stack
    @Test
    fun glamourmiteTapsACreature() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(glamourmite)
        bob.battlefield(ogre)
        alice.casts(glamourmite)
        resolveAll()
        if (pending is Decision.Modes) alice.answers(Answer.Modes(listOf(0)))
        targets(ogre)
        resolveAll()
        expect { ogre.tapped shouldBe true }
    }

    // CR 701.6a: every spell of the opponents countered, a Faerie for each
    @Test
    fun valeSylvanesRebukeCountersAndMakesFaeries() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(valeSylvanesRebuke)
        bob.battlefield("Mountain")
        bob.hand(jolt)
        inBobsTurn()
        bob.casts(jolt, target = "alice")
        bob.passes()
        alice.casts(valeSylvanesRebuke)
        resolveAll()
        expect {
            bob.graveyard has jolt
            alice.battlefield has "Faerie Token"
        }
        assertEquals(20, alice.life)
    }

    // CR 702.8a: as though it had flash with a Faerie; hexproof while untapped
    @Test
    fun phantasmWeaversAtInstantSpeed() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", faerie)
        alice.hand(phantasmWeavers)
        inBobsTurn()
        bob.passes()
        alice.casts(phantasmWeavers)
        resolveAll()
        expect {
            alice.battlefield has phantasmWeavers
            phantasmWeavers hasKeyword Keyword.Hexproof
        }
    }

    @Test
    fun emberkinSeerAndVelmorFrostcallerOnABigSpell() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", emberkinSeer, velmorFrostcaller)
        alice.hand(bigSpell)
        alice.library(bear, elf, ogre)
        alice.casts(bigSpell)
        resolveAll()
        expect {
            emberkinSeer.power shouldBe 4
            emberkinSeer hasKeyword Keyword.Vigilance
            alice.hand.size shouldBe 3
        }
    }

    @Test
    fun soaringVisionsDrawsAndLifts() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", bear)
        alice.hand(soaringVisions)
        alice.library(elf)
        alice.casts(soaringVisions, target = bear, paying = listOf("Island", "Island", "Island", "Island", "Island"))
        resolveAll()
        expect {
            alice.hand has elf
            bear.power shouldBe 4
            bear hasKeyword Keyword.Flying
        }
    }

    // CR 707.2: each of your nonland permanents becomes a copy
    @Test
    fun mirrorshapeCopiesOntoEverything() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", bear, elf)
        alice.hand(mirrorshape)
        bob.battlefield(ogre)
        alice.casts(mirrorshape, target = ogre)
        resolveAll()
        expect {
            card(bear).view.name shouldBe ogre
            card(elf).view.characteristics?.power shouldBe 3
            card("Island").view.name shouldBe "Island"
        }
    }

    // CR 601.2c: the two targets have different controllers
    @Test
    fun fleeAsOneReturnsOneOfEach() = scenario {
        alice.battlefield("Island", "Island", bear)
        alice.hand(fleeAsOne)
        bob.battlefield(ogre)
        alice.casts(fleeAsOne, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            alice.hand has bear
            bob.hand has ogre
        }
    }

    @Test
    fun fleeAsOneNeedsDifferentControllers() = scenario {
        alice.battlefield("Island", "Island", bear, elf)
        alice.hand(fleeAsOne)
        kotlin.test.assertFailsWith<IllegalArgumentException> { alice.casts(fleeAsOne, targets = listOf(bear, elf)) }
    }

    // CR 702.29e: basic landcycling
    @Test
    fun highcloudGliderLandcycles() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(highcloudGlider)
        alice.library(bear, "Plains")
        alice.activates(highcloudGlider)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Plains")
        resolveAll()
        expect {
            alice.hand has "Plains"
            alice.graveyard has highcloudGlider
        }
    }

    @Test
    fun highcloudGliderGivesFlying() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", bear)
        alice.hand(highcloudGlider)
        alice.casts(highcloudGlider)
        resolveAll()
        targets(bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Flying }
    }

    // CR 601.2f: {5} less for an Elemental of mana value 5; non-Elementals return
    @Test
    fun splitflockCostsLessAndBouncesNonElementals() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", bigElemental, bear)
        alice.hand(splitflock)
        bob.battlefield(ogre)
        alice.casts(splitflock)
        resolveAll()
        expect {
            alice.battlefield has splitflock
            alice.battlefield has bigElemental
            alice.hand has bear
            bob.hand has ogre
        }
    }

    // CR 601.2f: {2} less while a creature attacks you; its owner puts it on top or bottom
    @Test
    fun brushAsideAnAttacker() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(brushAside)
        alice.library(bear)
        bob.battlefield(ogre)
        bob.library(bear)
        inBobsTurn()
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(ogre)
        bob.passes()
        alice.casts(brushAside, target = ogre)
        resolveAll()
        if (pending is Decision.ChooseOption) bob.answers(Answer.Option((pending as Decision.ChooseOption).options.first()))
        resolveAll()
        expect { bob.library has ogre }
    }

    // CR 401.4: second from the top or the bottom
    @Test
    fun chronoRinsePutsAPermanentIntoTheLibrary() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(chronoRinse)
        bob.battlefield(ogre)
        bob.library(bear, bear)
        alice.casts(chronoRinse, target = ogre, paying = listOf("Island", "Island", "Island", "Island"))
        resolveAll()
        if (pending is Decision.ChooseOption) bob.answers(Answer.Option((pending as Decision.ChooseOption).options.first()))
        resolveAll()
        expect { bob.library has ogre }
    }

    // CR 608.2c: discarding a creature card is enough
    @Test
    fun cravingForSelfDiscardsACreature() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(cravingForSelf)
        alice.library(bear, ponder, totem)
        alice.casts(cravingForSelf)
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun cravingForSelfDiscardsTwo() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(cravingForSelf)
        alice.library(bear, ponder, totem)
        alice.casts(cravingForSelf)
        resolveAll()
        say(false)
        if (pending is Decision.ChooseObjects) alice.chooses(ponder, totem)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.hand has bear
        }
    }

    @Test
    fun suddenSuccorDrawsThreeDiscardsOne() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(suddenSuccor)
        alice.library(bear, ponder, totem)
        alice.casts(suddenSuccor, paying = listOf("Island", "Island", "Island", "Island", "Island"))
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(totem)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.graveyard has totem
        }
    }

    // CR 701.25a: a spell cast in an opponent's turn surveils 2
    @Test
    fun uninvitedPixieSurveilsOnTheirTurn() = scenario {
        alice.battlefield("Mountain", uninvitedPixie)
        alice.hand(jolt)
        alice.library(ogre, bear)
        inBobsTurn()
        bob.passes()
        alice.casts(jolt, target = "bob")
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.library has bear
        }
    }

    // a Merfolk token for each permanent returned
    @Test
    fun driftwinePartingReturnsAndMakesMerfolk() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", merfolk)
        alice.hand(driftwineParting)
        bob.battlefield(ogre, totem)
        alice.casts(driftwineParting, targets = listOf(ogre, totem), paying = listOf("Island", "Island", "Island", "Island", "Island", "Island", merfolk))
        resolveAll()
        expect {
            bob.hand has ogre
            bob.hand has totem
            alice.battlefield.names.count { it == "Merfolk Token" } shouldBe 2
        }
    }

    // CR 701.9b: a nonland permanent card chosen from the revealed hand
    @Test
    fun grannysVerdictTakesAPermanentCard() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(grannysVerdict)
        bob.hand(ogre, jolt, "Forest")
        alice.casts(grannysVerdict, modes = listOf(0), target = "bob")
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            bob.hand has jolt
        }
    }

    @Test
    fun grannysVerdictShrinks() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(grannysVerdict)
        bob.battlefield(redBear)
        alice.casts(grannysVerdict, modes = listOf(1), target = redBear)
        resolveAll()
        expect { bob.graveyard has redBear }
    }

    // CR 702.80a: wither: damage to creatures as -1/-1 counters
    @Test
    fun hookedLancetAttachesWithWither() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand(hookedLancet)
        alice.casts(hookedLancet)
        resolveAll()
        targets(bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear.toughness shouldBe 4
            bear hasKeyword Keyword.Wither
        }
    }

    @Test
    fun gallFlaskHobgoblinLeavesACounter() = scenario {
        alice.battlefield("Mountain", gallFlaskHobgoblin)
        alice.hand(jolt)
        bob.battlefield(elf)
        alice.casts(jolt, target = gallFlaskHobgoblin)
        resolveAll()
        targets(elf)
        resolveAll()
        expect { bob.graveyard has elf }
    }

    // CR 508.1m: "whenever you attack"
    @Test
    fun hobgoblinTricksterPumpsAnAttackingGoblin() = scenario {
        alice.battlefield(hobgoblinTrickster, goblin)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(goblin)
        targets(goblin)
        resolveAll()
        expect { goblin.power shouldBe 2 }
    }

    @Test
    fun gloomFallsShrinksEveryCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(gloomFalls)
        bob.battlefield(ogre)
        alice.casts(gloomFalls)
        resolveAll()
        expect {
            alice.graveyard has bear
            ogre.counters(m1m1) shouldBe 2
        }
    }

    @Test
    fun duskpalmMournerDrainsWithAnElf() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(duskpalmMourner)
        alice.library(bear, ogre, elf)
        alice.casts(duskpalmMourner)
        resolveAll()
        assertEquals(18, bob.life)
        assertEquals(22, alice.life)
    }

    // X: two Elves on the battlefield and one Elf card in the graveyard
    @Test
    fun murkRenderCountsElves() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", elf)
        alice.graveyard(elfMatron)
        alice.hand(murkRender)
        bob.battlefield(ogre)
        alice.casts(murkRender)
        resolveAll()
        targets(elf)
        targets(ogre)
        resolveAll()
        expect {
            elf.power shouldBe 4
            bob.graveyard has ogre
        }
    }

    @Test
    fun steelGuardElfBecomesIndestructible() = scenario {
        alice.battlefield(steelGuardElf)
        alice.hand(ogre)
        alice.activates(steelGuardElf, choosing = listOf(ogre))
        resolveAll()
        expect {
            steelGuardElf hasKeyword Keyword.Indestructible
            steelGuardElf.tapped shouldBe true
            alice.graveyard has ogre
        }
    }

    @Test
    fun nightbloomDrainerDrawsOnAttack() = scenario {
        alice.battlefield(nightbloomDrainer)
        alice.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(nightbloomDrainer)
        resolveAll()
        expect { alice.hand has bear }
        assertEquals(19, alice.life)
    }

    // CR 101.2: no draw in the draw step; 3 life and a tutor instead
    @Test
    fun duskhymnRefrainReplacesDrawing() = scenario {
        alice.battlefield(duskhymnRefrain)
        alice.library(bear)
        bob.library(bear, ogre)
        turn(active = bob, step = TurnStep.Upkeep)
        advanceTo(TurnStep.Draw)
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses(ogre)
        resolveAll()
        expect {
            bob.hand.size shouldBe 1
            bob.hand has ogre
        }
        assertEquals(17, bob.life)
    }

    @Test
    fun nightTerrorSeederShrinksOnTheirTurn() = scenario {
        alice.battlefield("Mountain", nightTerrorSeeder)
        alice.hand(jolt)
        bob.battlefield(elf, ogre)
        inBobsTurn()
        bob.passes()
        alice.casts(jolt, target = ogre)
        // the trigger resolves first: the ogre becomes 2/2, then the 2 damage kills it
        targets(ogre)
        resolveAll()
        expect {
            bob.battlefield has elf
            bob.graveyard has ogre
        }
    }

    // CR 603.7c: a delayed trigger for that creature's death this turn
    @Test
    fun scarknifesSpiteLeavesAnElf() = scenario {
        alice.battlefield("Swamp", "Mountain", bear)
        alice.hand(scarknifesSpite, jolt)
        alice.casts(scarknifesSpite, target = bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Deathtouch }
        alice.casts(jolt, target = bear)
        resolveAll()
        expect { alice.battlefield has "Elf Token" }
    }

    // CR 609.4b: mana of any type may be spent to cast the exiled instant
    @Test
    fun samplerOfGoodsStealsASpell() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(samplerOfGoods)
        bob.hand(jolt)
        alice.casts(samplerOfGoods, paying = listOf("Swamp", "Swamp", "Swamp"))
        resolveAll()
        targets("bob")
        resolveAll()
        if (pending is Decision.ChooseObjects && (pending as Decision.ChooseObjects).player == bob.id) bob.chooses(jolt)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(jolt)
        resolveAll()
        expect { bob.exile has jolt }
        alice.casts(jolt, target = "bob", paying = listOf("Swamp#3"))
        resolveAll()
        assertEquals(18, bob.life)
    }
}
