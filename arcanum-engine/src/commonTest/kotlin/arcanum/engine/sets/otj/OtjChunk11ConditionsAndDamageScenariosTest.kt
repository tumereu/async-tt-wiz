package arcanum.engine.sets.otj

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.permitted
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test

/** OTJ chunk 11: types of creatures you control but don't own, casting an opponent's exiled cards, a revealed hand's other choice, a copy with
 * hexproof, damage by power to two other targets, spells cast this turn, "as you cast this spell", per-opponent discards. */
class OtjChunk11ConditionsAndDamageScenariosTest {
    private val spark = Cards.invent("Grinning Jade Spark", "{1}{B}{R}", "Legendary Creature — Lizard Rogue",
        "Creatures you control but don't own are Mercenaries in addition to their other types.\n" +
            "At the beginning of your upkeep, exile the top X cards of target opponent's library, where X is the number of outlaws you control. " +
            "Until end of turn, you may cast spells from among those cards, and mana of any type can be spent to cast those spells.", "4/3")
    private val bargain = Cards.invent("Shackled Bargain", "{1}{B}", "Sorcery",
        "Target opponent reveals their hand. You may choose a nonland card from it. If you do, they discard it. Otherwise, you may put a face-up exiled card they own into their graveyard.")
    private val vesk = Cards.invent("Vesk, the Mimic Boss", "{2}{G}{U}", "Legendary Planeswalker — Vesk", """
        At the beginning of combat on your turn, Vesk becomes a copy of up to one target creature you control until end of turn, except he has hexproof.
        [+1]: Draw two cards. If you've committed a crime this turn, discard a card. Otherwise, discard two cards.
        [−1]: Create a 3/3 green Elk creature token.
        [−5]: For each other nonland permanent you control, create a token that's a copy of that permanent.
    """.trimIndent(), loyalty = "3")
    private val treachery = Cards.invent("Treachery at the Depot", "{4}{G}{G}", "Instant",
        "Target creature you control deals damage equal to its power to each of two other target creatures.")
    private val gecko = Cards.invent("Spellbiter Gecko", "{1}{R}", "Creature — Lizard",
        "Whenever a player casts a noncreature spell, this creature deals damage to that player equal to the number of noncreature spells they've cast this turn.", "1/4")
    private val veer = Cards.invent("Veer Aside", "{W}", "Instant",
        "Veer Aside deals 2 damage to target attacking or blocking creature. Veer Aside deals 4 damage to that creature instead if you controlled a Mount as you cast this spell.")
    private val volley = Cards.invent("Rumbling Volley", "{1}{R}", "Instant",
        "Rumbling Volley deals X damage to target creature, where X is 2 plus the number of other spells you've cast this turn.")
    private val raider = Cards.invent("Hollowed Raider", "{6}{B}", "Creature — Specter Rogue",
        "This spell costs {1} less to cast for each creature card in your graveyard.\nFlying\n" +
            "When this creature enters, any number of target opponents each discard a card. For each of those opponents who didn't discard a card with mana value 4 or greater, draw a card.",
        "4/2")

    private val lasso = Cards.invent("Lasso the Herd", "{2}{R}", "Sorcery", "Gain control of target creature until end of turn.")
    private val insight = Cards.invent("Tidal Insight", "{1}{U}", "Instant", "Draw a card.")
    private val mare = Cards.invent("Dusty Mare", "{1}{W}", "Creature — Horse Mount", "", "2/2")
    private val flick = Cards.invent("Pebble Flick", "{R}", "Instant", "Pebble Flick deals 1 damage to any target.")
    private val ponder = Cards.invent("Idle Ponder", "{U}", "Sorcery", "Draw a card.")
    private val musing = Cards.invent("Lazy Musing", "{U}", "Sorcery", "Draw a card.")
    private val hulk = Cards.invent("Lumbering Hulk", "{3}{G}", "Creature — Beast", "", "4/4")
    private val imp = Cards.invent("Scrap Imp", "{B}", "Creature — Imp", "", "1/1")
    private val bear = Cards.invent("Ridge Bear", "{2}{G}", "Creature — Bear", "", "3/3")
    private val ogre = Cards.invent("Canyon Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val stalker = Cards.invent("Mesa Stalker", "{2}{G}", "Creature — Cat", "", "2/4")
    private val bandit = Cards.invent("Gulch Bandit", "{1}{B}", "Creature — Human Rogue", "", "2/1")
    private val sentry = Cards.invent("Stone Sentry", "{4}", "Artifact Creature — Golem", "", "5/5")

    // CR 108.3, 108.4, 205.1b: only the creature alice controls but bob owns is a Mercenary
    @Test
    fun sparkMakesBorrowedCreaturesMercenaries() = scenario {
        alice.battlefield(spark, bear)
        alice.hand(lasso)
        bob.battlefield(ogre)
        alice.castsWithoutCosts(lasso, target = ogre)
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            ogre.subtypes.contains(Subtype("Mercenary")) shouldBe true
            bear.subtypes.contains(Subtype("Mercenary")) shouldBe false
        }
    }

    // CR 700.12: two outlaws exile two cards; CR 601.2h: one is cast with mana of another colour; CR 400.3: it goes to its owner's graveyard
    @Test
    fun sparkExilesTopXAndCastsWithAnyMana() = scenario {
        alice.battlefield(spark, bandit, "Mountain", "Mountain")
        alice.library("Swamp", "Swamp", "Swamp")
        bob.library(insight, ogre, "Island", "Island")
        turn(number = 3, step = TurnStep.Upkeep)
        beginStep(TurnStep.Upkeep)
        playOut("bob")
        expect {
            bob.exile has insight
            bob.exile has ogre
            bob.library.size shouldBe 2
        }
        alice.casts(insight, way = permitted(insight))
        resolveAll()
        expect {
            insight.zone shouldBe Zone.Graveyard
            bob.graveyard has insight
            alice.hand.size shouldBe 1
        }
    }

    // CR 608.2c: with no nonland card to choose, the face-up exiled card bob owns can be put into his graveyard
    @Test
    fun bargainOtherwiseMovesAnExiledCard() = scenario {
        alice.hand(bargain)
        bob.hand("Island")
        bob.exile(hulk)
        alice.castsWithoutCosts(bargain, target = "bob")
        playOut(hulk)
        expect {
            bob.graveyard has hulk
            bob.hand has "Island"
        }
    }

    // CR 701.9b: the chosen card is discarded and the exiled card stays
    @Test
    fun bargainDiscardsTheChosenCard() = scenario {
        alice.hand(bargain)
        bob.hand("Island", imp)
        bob.exile(hulk)
        alice.castsWithoutCosts(bargain, target = "bob")
        playOut(imp, hulk)
        expect {
            bob.graveyard has imp
            bob.graveyard lacks hulk
            bob.exile has hulk
        }
    }

    // CR 707.2, 707.9b: Vesk is a copy of the creature with hexproof until end of turn
    @Test
    fun veskBecomesACopyWithHexproof() = scenario {
        alice.battlefield(vesk, counters = listOf(CounterKind.Loyalty to 3))
        alice.battlefield(sentry)
        advanceTo(TurnStep.BeginningOfCombat)
        playOut(sentry)
        expect {
            card(vesk).power shouldBe 5
            card(vesk).types.contains(CardType.Creature) shouldBe true
            card(vesk).types.contains(CardType.Planeswalker) shouldBe false
            card(vesk) hasKeyword Keyword.Hexproof.kind
            sentry lacksKeyword Keyword.Hexproof.kind
        }
    }

    // CR 120.3: the creature deals damage equal to its power to each of the two other targets
    @Test
    fun treacheryDealsPowerToTwoOthers() = scenario {
        alice.battlefield(bear)
        alice.hand(treachery)
        bob.battlefield(ogre, stalker)
        alice.castsWithoutCosts(treachery, targets = listOf(bear, ogre, stalker))
        resolveAll()
        expect {
            bob.graveyard has ogre
            stalker.view.obj?.damage shouldBe 3
            bear.view.obj?.damage shouldBe 0
        }
    }

    // CR 601.2i: each noncreature spell bob casts counts the noncreature spells he cast this turn; a creature spell doesn't (3 players)
    @Test
    fun geckoCountsThatPlayersNoncreatureSpells() = scenario(players = 3) {
        alice.battlefield(gecko)
        bob.battlefield("Island", "Island", "Forest", "Forest")
        bob.hand(imp, ponder, musing)
        bob.library("Island", "Island", "Island")
        turn(number = 2, active = bob)
        bob.castsWithoutCosts(imp)
        resolveAll()
        bob.casts(ponder)
        resolveAll()
        expect { bob.life shouldBe 19 }
        bob.casts(musing)
        resolveAll()
        expect {
            bob.life shouldBe 17
            carol.life shouldBe 20
            alice.life shouldBe 20
        }
    }

    // CR 601.2i: a Mount controlled as the spell was cast makes it 4 damage, even after the Mount is gone
    @Test
    fun veerChecksTheMountAsCast() = scenario {
        alice.battlefield(mare, "Plains")
        alice.hand(veer)
        bob.battlefield(sentry)
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(sentry)
        bob.passes()
        alice.casts(veer, target = sentry)
        move(mare, Zone.Graveyard)
        resolveAll()
        expect { sentry.view.obj?.damage shouldBe 4 }
    }

    // CR 601.2i: a Mount that arrives after the cast doesn't count
    @Test
    fun veerWithoutAMountAsCastDealsTwo() = scenario {
        alice.battlefield("Plains")
        alice.hand(veer, mare)
        bob.battlefield(sentry)
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(sentry)
        bob.passes()
        alice.casts(veer, target = sentry)
        move(mare, Zone.Battlefield)
        resolveAll()
        expect { sentry.view.obj?.damage shouldBe 2 }
    }

    // CR 107.3c: X is 2 plus the other spells cast this turn: the flick counts, the volley itself doesn't
    @Test
    fun volleyCountsOtherSpells() = scenario {
        alice.hand(flick, volley)
        bob.battlefield(sentry)
        alice.castsWithoutCosts(flick, target = "bob")
        resolveAll()
        alice.castsWithoutCosts(volley, target = sentry)
        resolveAll()
        expect { sentry.view.obj?.damage shouldBe 3 }
    }

    // CR 101.4: bob and carol discard at once; only carol's card has mana value less than 4, so alice draws one card (3 players)
    @Test
    fun raiderDrawsForOpponentsWithoutBigDiscards() = scenario(players = 3) {
        alice.hand(raider)
        alice.library("Swamp", "Swamp")
        bob.hand(hulk)
        carol.hand(imp)
        alice.castsWithoutCosts(raider)
        resolveAll()
        playOut("bob", "carol")
        expect {
            bob.graveyard has hulk
            carol.graveyard has imp
            alice.hand.size shouldBe 1
        }
    }
}
