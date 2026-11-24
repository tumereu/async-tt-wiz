package arcanum.engine.sets.vow

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** VOW chunk 08: an attack trigger that checks the creatures attacked with this combat, it becoming night, a cost reduction by card types shared
 * with exiled cards, a chosen number of sacrifices and each player's kept permanent with three players, modes that may all be skipped, a land
 * gaining a mana ability, counters by the greatest number of a player's creatures, a token copy of the card a cost exiled, keywords counted
 * among creatures, a reflexive modal trigger reading the exiled card's mana value, and all graveyards exiled for tokens. */
class VowChunk08EffectsScenariosTest {
    private val puppeteer = Cards.invent("Grinning Marionettist", "{3}{R}", "Creature — Human Rogue", """
        Haste
        Whenever this creature attacks, if you attacked with exactly one other creature this combat, you may have that creature's base power and toughness become 4/3 until end of turn.
    """.trimIndent(), "4/3")
    private val dusk = Cards.invent("Dusk Descends Early", "{3}{R}", "Sorcery", "It becomes night. Discard any number of cards, then draw that many cards plus one.")
    private val prowler = Cards.invent("Tombyard Stalker", "{1}{G}{G}", "Creature — Wolf", """
        Vigilance
        Whenever this creature enters or attacks, exile a card from a graveyard.
        Spells you cast cost {1} less to cast for each card type they share with cards exiled with this creature.
    """.trimIndent(), "3/4")
    private val purge = Cards.invent("Guest List Purge", "{3}{W}{W}", "Sorcery",
        "Choose a number between 0 and 13. Each player sacrifices that many creatures of their choice.")
    private val undertow = Cards.invent("Swallowing Undertow", "{2}{U}{U}", "Sorcery",
        "Each player chooses a nonland permanent they control. Return all nonland permanents not chosen this way to their owners' hands. Then you draw a card for each opponent who has more cards in their hand than you.")
    private val shade = Cards.invent("Sleepbinder Shade", "{1}{U}{U}", "Creature — Spirit", """
        Flying
        At the beginning of combat on your turn, choose up to one —
        • Tap target creature.
        • Target creature doesn't untap during its controller's next untap step.
    """.trimIndent(), "3/1")
    private val rite = Cards.invent("Radiant Daybreak Rite", "{3}{G}{G}", "Enchantment", """
        At the beginning of combat on your turn, choose one —
        • Creatures you control get +1/+1 and gain trample until end of turn.
        • Target land gains "{T}: Add {G}{G}{G}" until end of turn.
        • Draw a card if you control a creature with power 3 or greater.
        • You gain 3 life.
    """.trimIndent())
    private val casebook = Cards.invent("Sleuth's Casebook", "{2}", "Artifact — Book Clue", """
        This artifact enters with a number of suspect counters on it equal to the greatest number of creatures a player controls.
        {2}, {T}, Remove a suspect counter from this artifact: Draw a card.
        {2}, Sacrifice this artifact: Draw a card.
    """.trimIndent())
    private val cabinet = Cards.invent("Puppet Cabinet of Dread", "{5}", "Artifact",
        "{1}, {T}, Exile a creature card from your graveyard: Create a token that's a copy of the exiled card, except it's a 0/0 Construct artifact in addition to its other types and it has \"This token gets +1/+1 for each Construct you control.\" It gains haste until end of turn. Activate only as a sorcery.")
    private val corvan = Cards.invent("Corvan, Blight-Sworn", "{1}{R}{W}", "Legendary Creature — Vampire Soldier",
        "When Corvan enters, create X Blood tokens, where X is the number of abilities from among flying, first strike, double strike, deathtouch, haste, hexproof, indestructible, lifelink, menace, reach, trample, and vigilance found among creatures you control. (Count abilities, not creatures.)",
        "3/3")
    private val defiler = Cards.invent("Gravefield Defiler", "{4}{B}{B}", "Creature — Zombie", """
        Menace
        When this creature enters or dies, exile another card from a graveyard. When you do, choose one —
        • Remove X counters from target permanent, where X is the mana value of the exiled card.
        • Target creature an opponent controls gets -X/-X until end of turn, where X is the mana value of the exiled card.
    """.trimIndent(), "4/4")
    private val mirela = Cards.invent("Mirela, Shade Warden", "{1}{W}{B}", "Legendary Planeswalker — Kaya", """
        [+1]: Creatures you control gain deathtouch until end of turn. Put a +1/+1 counter on up to one target creature token you control.
        [−2]: Until end of turn, if one or more tokens would be created under your control, twice that many of those tokens are created instead.
        [−6]: Exile all cards from all graveyards, then create a 1/1 white Spirit creature token with flying for each card exiled this way.
    """.trimIndent())

    private val badger = Cards.invent("Gloam Badger", "{1}{G}", "Creature — Badger", "", "2/2")
    private val sentinel = Cards.invent("Hedge Sentinel", "{1}{W}", "Creature — Human Soldier", "", "1/3")
    private val ogre = Cards.invent("Ashcap Ogre", "{4}{R}", "Creature — Ogre", "", "5/5")
    private val familiar = Cards.invent("Tin Familiar", "{2}", "Artifact", "")
    private val pup = Cards.invent("Brass Pup", "{3}", "Artifact Creature — Dog Construct", "", "2/2")
    private val kestrel = Cards.invent("Skyreach Kestrel", "{1}{W}", "Creature — Bird", "Flying, vigilance", "1/1")
    private val lancer = Cards.invent("Gale Lancer", "{2}{W}", "Creature — Human Knight", "Flying", "2/2")
    private val cinders = Cards.invent("Sudden Cinders", "{R}", "Instant", "Sudden Cinders deals 5 damage to any target.")
    private val youth = Cards.inventFaces("Transform",
        face("Dusk-Pelt Youth", "{1}{G}", "Creature — Human Werewolf",
            "Daybound (If a player casts no spells during their own turn, it becomes night next turn.)", "2/2", null, null),
        face("Dusk-Pelt Ravager", null, "Creature — Werewolf",
            "Nightbound (If a player casts at least two spells during their own turn, it becomes day next turn.)", "4/4", null, "G"))

    // CR 506.4, 508.1 (exactly one other creature attacked), 603.4, 613.4b: that creature's base power and toughness become 4/3
    @Test
    fun oneOtherAttackerBecomesFourThree() = scenario {
        alice.battlefield(puppeteer, badger)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(puppeteer, badger)
        playOut()
        expect {
            badger.power shouldBe 4
            badger.toughness shouldBe 3
        }
    }

    // CR 603.4: with two other attackers it doesn't trigger
    @Test
    fun twoOtherAttackersDontTrigger() = scenario {
        alice.battlefield(puppeteer, badger, sentinel)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(puppeteer, badger, sentinel)
        playOut()
        expect {
            stack.size shouldBe 0
            badger.power shouldBe 2
            sentinel.toughness shouldBe 3
        }
    }

    // CR 732.1 (it becomes night: day becomes night, CR 732.1a), 702.145c (a daybound permanent transforms); CR 701.9 (discard one, draw two)
    @Test
    fun itBecomesNightAndDayboundTransforms() = scenario {
        alice.battlefield(youth, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(dusk, "Island")
        alice.library("Forest", "Forest")
        alice.casts(dusk)
        allPass()
        alice.chooses("Island")
        resolveAll()
        expect {
            alice.graveyard has "Island"
            alice.hand.size shouldBe 2
            card(youth).view.obj?.face shouldBe 1
        }
    }

    // CR 607.2a, 601.2f, 205.2a: an exiled artifact creature card shares two card types with an artifact creature spell: {2} less
    @Test
    fun spellCostsLessForEachSharedCardType() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(prowler, pup)
        bob.graveyard(pup)
        alice.casts(prowler)
        resolveAll()
        playOut("Brass Pup")
        expect { alice.exile.size + bob.exile.size shouldBe 1 }
        alice.casts(pup)
        resolveAll()
        expect { alice.battlefield has pup }
    }

    // CR 601.2f: a card type the spell doesn't share takes nothing off (an artifact spell, an exiled creature card)
    @Test
    fun unsharedTypeDoesntReduce() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(prowler, familiar)
        bob.graveyard(badger)
        alice.casts(prowler)
        resolveAll()
        playOut("Gloam Badger")
        perform(arcanum.engine.input.Input.Cast(alice.id, card(familiar)))
        expect { decision<Decision.ManaPayment>().suggestion.isEmpty() shouldBe true }
    }

    // CR 608.2d (the number chosen as it resolves), 101.4, 701.21a: each of three players sacrifices that many
    @Test
    fun eachPlayerSacrificesTheChosenNumber() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.battlefield(badger, sentinel)
        bob.battlefield(ogre, kestrel)
        carol.battlefield(lancer)
        alice.hand(purge)
        alice.casts(purge)
        allPass()
        answer(Answer.Number(1))
        playOut("Gloam Badger", "Ashcap Ogre", "Gale Lancer")
        expect {
            alice.graveyard has badger
            alice.battlefield has sentinel
            bob.graveyard has ogre
            bob.battlefield has kestrel
            carol.graveyard has lancer
        }
    }

    // CR 101.4: each player keeps one nonland permanent, the rest return; CR 402.1: one opponent has more cards in hand than you
    @Test
    fun eachKeepsOneAndYouDrawForOpponentsWithMoreCards() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island", badger, sentinel)
        bob.battlefield(ogre, familiar)
        carol.battlefield(lancer)
        alice.hand(undertow)
        bob.hand("Swamp", "Swamp")
        alice.library("Island", "Island")
        alice.casts(undertow)
        allPass()
        playOut("Gloam Badger", "Ashcap Ogre", "Gale Lancer")
        expect {
            alice.battlefield has badger
            alice.battlefield lacks sentinel
            bob.battlefield has ogre
            bob.hand has familiar
            carol.battlefield has lancer
            // Hedge Sentinel and one card drawn: Bob has three cards, Carol none
            alice.hand.size shouldBe 2
        }
    }

    // CR 700.2b: no mode chosen, the ability is removed from the Stack and nothing happens
    @Test
    fun upToOneModeAllowsNone() = scenario {
        alice.battlefield(shade)
        bob.battlefield(ogre)
        advanceTo(TurnStep.BeginningOfCombat)
        val modes = decision<Decision.Modes>()
        assertEquals(0, modes.min)
        answer(Answer.Modes(emptyList()))
        resolveAll()
        expect {
            ogre.tapped shouldBe false
            stack.size shouldBe 0
        }
    }

    // CR 700.2: one mode, tapping the target
    @Test
    fun upToOneModeTaps() = scenario {
        alice.battlefield(shade)
        bob.battlefield(ogre)
        advanceTo(TurnStep.BeginningOfCombat)
        answer(Answer.Modes(listOf(0)))
        playOut("Ashcap Ogre")
        expect { ogre.tapped shouldBe true }
    }

    // CR 605.1a, 611.2a: the land has the mana ability until end of turn and adds three mana
    @Test
    fun landGainsAManaAbility() = scenario {
        alice.battlefield(rite, "Forest")
        advanceTo(TurnStep.BeginningOfCombat)
        answer(Answer.Modes(listOf(1)))
        playOut("Forest")
        alice.activates("Forest", ability = 1)
        assertEquals(3, state.player(alice.id).manaPool.size)
    }

    // CR 614.1c: counted as it enters, the greatest number among three players' creatures
    @Test
    fun entersWithCountersForTheGreatestCreatureCount() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", badger)
        bob.battlefield(ogre, kestrel, sentinel)
        carol.battlefield(lancer)
        alice.hand(casebook)
        alice.casts(casebook)
        resolveAll()
        expect { casebook.counters(CounterKind.Named("suspect")) shouldBe 3 }
    }

    // CR 707.9b, 601.2h: a 0/0 Construct artifact copy of the exiled card, +1/+1 for each Construct (itself and another), with haste
    @Test
    fun tokenCopyOfTheExiledCard() = scenario {
        alice.battlefield(cabinet, pup, "Plains")
        alice.graveyard(ogre)
        alice.activates(cabinet, choosing = listOf("Ashcap Ogre"))
        resolveAll()
        expect {
            alice.exile has ogre
            ogre.power shouldBe 2
            ogre.toughness shouldBe 2
            ogre hasKeyword Keyword.Haste.kind
            assertTrue(arcanum.api.CardType.Artifact in ogre.types)
        }
    }

    // CR 702.9, 702.20: flying (on two creatures) and vigilance count once each: two Blood tokens
    @Test
    fun countsEachListedKeywordOnce() = scenario {
        alice.battlefield("Mountain", "Plains", "Plains", kestrel, lancer)
        alice.hand(corvan)
        alice.casts(corvan)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Blood Token" } shouldBe 2 }
    }

    // CR 603.12, 608.2h: dying, it exiles another card (not itself, CR 400.7) and the creature gets -X/-X for that card's mana value
    @Test
    fun diesExilesAnotherCardAndShrinksACreature() = scenario {
        alice.battlefield(defiler, "Mountain")
        alice.hand(cinders)
        alice.graveyard(ogre)
        bob.battlefield(kestrel, lancer)
        alice.casts(cinders, target = defiler)
        resolveAll()
        val choice = decision<Decision.ChooseObjects>()
        assertTrue(card(defiler) !in choice.options)
        alice.chooses("Ashcap Ogre")
        resolveAll()
        answer(Answer.Modes(listOf(1)))
        playOut("Gale Lancer")
        expect {
            alice.exile has ogre
            bob.graveyard has lancer
            bob.battlefield has kestrel
        }
    }

    // CR 603.12, 122.8: the other mode removes that many counters (here all of them)
    @Test
    fun entersExilesACardAndRemovesCounters() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(defiler)
        bob.graveyard(badger)
        bob.battlefield(sentinel, counters = listOf(CounterKind.PowerToughness(1, 1) to 3))
        alice.casts(defiler)
        resolveAll()
        playOut("Gloam Badger")
        answer(Answer.Modes(listOf(0)))
        playOut("Hedge Sentinel")
        expect { sentinel.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    // CR 400.7: a Spirit for each card exiled from every graveyard (from 7 loyalty: at 0 it would be in the graveyard by then too, CR 704.5i)
    @Test
    fun exilesAllGraveyardsForSpirits() = scenario {
        alice.battlefield(mirela, counters = listOf(CounterKind.Loyalty to 7))
        alice.graveyard(ogre, badger)
        bob.graveyard(lancer)
        alice.activates(mirela, ability = 2)
        resolveAll()
        expect {
            alice.graveyard.size shouldBe 0
            bob.graveyard.size shouldBe 0
            alice.battlefield.names.count { it == "Spirit Token" } shouldBe 3
        }
    }
}
