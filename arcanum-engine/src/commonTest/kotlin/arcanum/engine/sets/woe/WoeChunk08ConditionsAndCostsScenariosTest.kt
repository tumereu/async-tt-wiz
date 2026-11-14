package arcanum.engine.sets.woe

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.ecl.paySuggested
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WOE chunk 08, conditions, cost changes and chosen numbers: the invented names carry the oracle text of Break the Spell, Charging Hooligan,
 * Virtue of Strength, Food Fight, Hylda's Crown of Winter, Agatha of the Vile Cauldron, Mosswood Dreadknight, Rowan, Scion of War, The End,
 * Expel the Interlopers, Sentinel of Lost Lore, Talion, the Kindly Lord and Three Bowls of Porridge.
 */
class WoeChunk08ConditionsAndCostsScenariosTest {
    private val bear = Cards.invent("Hollow Bruin", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Craghorn Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val rat = Cards.invent("Gutter Ratling", "{B}", "Creature — Rat", "", "1/1")
    private val charm = Cards.invent("Hearth Charm", "{1}{W}", "Enchantment", "")
    private val crowning = Cards.invent("Crowning Rite", "{W}", "Sorcery",
        "Create a Royal Role token attached to target creature you control. (Enchanted creature gets +1/+1 and has ward {1}.)")
    private val bauble = Cards.invent("Brass Bauble", "{1}", "Artifact", "")
    private val hollow = Cards.invent("Mossy Hollow", null, "Land", "{T}: Add {G}.")
    private val jab = Cards.invent("Searing Jab", "{R}", "Instant", "Searing Jab deals 3 damage to target creature.")
    private val toll = Cards.invent("Bitter Toll", "{0}", "Sorcery", "You lose 2 life.")
    private val tithe = Cards.invent("Bitter Tithe", "{0}", "Sorcery", "You lose 2 life.")
    private val harvest = Cards.invent("Grim Harvest Call", "{3}{B}", "Sorcery", "Draw a card.")
    private val stalker = Cards.invent("Gloomvale Stalker", "{2}{B}", "Creature — Horror", "", "3/3")
    private val imp = Cards.invent("Cauldron Imp", "{1}{R}", "Creature — Imp", """
        {2}: This creature gets +1/+0 until end of turn.
        {1}{R}: This creature gets +0/+1 until end of turn.
    """, "1/1")
    private val courier = Cards.inventFaces("Adventure",
        face("Lantern Courier", "{2}{G}", "Creature — Human Scout", "", "2/2", null, null),
        face("Quick Errand", "{G}", "Sorcery — Adventure", "Draw a card. (Then exile this card. You may cast the creature later from exile.)", null, null, null))
    private val peddler = Cards.inventFaces("Adventure",
        face("Roadside Peddler", "{1}{U}", "Creature — Human Peasant", "", "1/2", null, null),
        face("Haggle Away", "{U}", "Instant — Adventure", "Draw a card. (Then exile this card. You may cast the creature later from exile.)", null, null, null))

    private val shatter = Cards.invent("Shatter the Hex", "{W}", "Instant",
        "Destroy target enchantment. If a permanent you controlled or a token was destroyed this way, draw a card.")
    private val ruffian = Cards.invent("Rushing Ruffian", "{3}{R}", "Creature — Human Peasant",
        "Whenever this creature attacks, it gets +1/+0 until end of turn for each attacking creature. If a Rat is attacking, this creature gains trample until end of turn.",
        "3/3")
    private val might = Cards.inventFaces("Adventure",
        face("Virtue of Might", "{5}{G}{G}", "Enchantment", "If you tap a basic land for mana, it produces three times as much of that mana instead.", null, null, null),
        face("Hedgerow Growth", "{G}", "Sorcery — Adventure",
            "Return target creature or land card from your graveyard to your hand. (Then exile this card. You may cast the enchantment later from exile.)", null, null, null))
    private val brawl = Cards.invent("Pantry Brawl", "{1}{R}", "Enchantment",
        "Artifacts you control have \"{2}, Sacrifice this artifact: It deals damage to any target equal to 1 plus the number of permanents named Pantry Brawl you control.\"")
    private val diadem = Cards.invent("Frostqueen's Diadem", "{3}", "Legendary Artifact", """
        {1}, {T}: Tap target creature. This ability costs {1} less to activate during your turn.
        {3}, Sacrifice Frostqueen's Diadem: Draw a card for each tapped creature your opponents control.
    """)
    private val morwen = Cards.invent("Morwen of the Bitter Pot", "{R}{G}", "Legendary Creature — Human Warlock", """
        Activated abilities of creatures you control cost {X} less to activate, where X is Morwen's power. This effect can't reduce the mana in that cost to less than one mana.
        {4}{R}{G}: Other creatures you control get +1/+1 and gain trample and haste until end of turn.
    """, "1/1")
    private val darkknight = Cards.inventFaces("Adventure",
        face("Mossgrove Darkknight", "{1}{G}", "Creature — Human Knight",
            "Trample\nWhen this creature dies, you may cast it from your graveyard as an Adventure until the end of your next turn.", "3/2", null, null),
        face("Grim Murmurs", "{1}{B}", "Sorcery — Adventure",
            "You draw a card and you lose 1 life. (Then exile this card. You may cast the creature later from exile.)", null, null, null))
    private val rhiannon = Cards.invent("Rhiannon, Scion of Ash", "{1}{B}{R}", "Legendary Creature — Human Wizard", """
        Menace
        {T}: Spells you cast this turn that are black and/or red cost {X} less to cast, where X is the amount of life you lost this turn. Activate only as a sorcery.
    """, "4/2")
    private val curtain = Cards.invent("Final Curtain", "{2}{B}{B}", "Instant", """
        This spell costs {2} less to cast if your life total is 5 or less.
        Exile target creature or planeswalker. Search its controller's graveyard, hand, and library for any number of cards with the same name as that permanent and exile them. That player shuffles, then draws a card for each card exiled from their hand this way.
    """)
    private val banish = Cards.invent("Banish the Trespassers", "{3}{W}{W}", "Sorcery",
        "Choose a number between 0 and 10. Destroy all creatures with power greater than or equal to the chosen number.")
    private val warden = Cards.invent("Warden of Lost Tales", "{1}{G}{G}", "Creature — Elf Knight", """
        When this creature enters, choose one or more —
        • Return target card you own in exile that has an Adventure to your hand.
        • Put target card you don't own in exile that has an Adventure on the bottom of its owner's library.
        • Exile target player's graveyard.
    """, "3/4")
    private val talwyn = Cards.invent("Talwyn, the Gentle Lord", "{2}{U}{B}", "Legendary Creature — Faerie Noble", """
        Flying
        As Talwyn enters, choose a number between 1 and 10.
        Whenever an opponent casts a spell with mana value, power, or toughness equal to the chosen number, that player loses 2 life and you draw a card.
    """, "3/4")
    private val broth = Cards.invent("Three Cups of Broth", "{2}", "Artifact — Food", """
        {2}, {T}: Choose one that hasn't been chosen —
        • This artifact deals 2 damage to target creature.
        • Tap target creature.
        • Sacrifice this artifact. You gain 3 life.
    """)

    // CR 608.2h: Bob's own enchantment, not a token: nothing drawn
    @Test
    fun breakTheSpellDrawsNothingForAnOpponentsCard() = scenario {
        alice.battlefield("Plains")
        alice.hand(shatter)
        alice.library("Island")
        bob.battlefield(charm)
        alice.casts(shatter, target = charm)
        resolveAll()
        expect {
            charm.zone shouldBe Zone.Graveyard
            alice.hand.size shouldBe 0
        }
    }

    // CR 608.2h: a permanent Alice controlled, as it last existed
    @Test
    fun breakTheSpellDrawsForItsControllersOwnEnchantment() = scenario {
        alice.battlefield("Plains", charm)
        alice.hand(shatter)
        alice.library("Island")
        alice.casts(shatter, target = charm)
        resolveAll()
        expect { alice.hand has "Island" }
    }

    // CR 111.1, 608.2h: Bob's Role token (a token Bob controlled) was destroyed: Alice draws
    @Test
    fun breakTheSpellDrawsForAnOpponentsToken() = scenario {
        turn(number = 2, active = bob)
        bob.battlefield("Plains", bear)
        bob.hand(crowning)
        alice.battlefield("Plains")
        alice.hand(shatter)
        alice.library("Island")
        bob.casts(crowning, target = bear)
        resolveAll()
        expect { bob.battlefield has "Royal" }
        bob.passes()
        alice.casts(shatter, target = "Royal")
        resolveAll()
        expect {
            bob.battlefield lacks "Royal"
            alice.hand has "Island"
        }
    }

    // CR 508.1k, 608.2c: +1/+0 for each of the two attackers; a Rat is attacking, so trample
    @Test
    fun chargingHooliganGainsTrampleWithARatAttacking() = scenario {
        alice.battlefield(ruffian, rat)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ruffian, rat)
        resolveAll()
        expect {
            ruffian.power shouldBe 5
            ruffian hasKeyword Keyword.Trample.kind
        }
    }

    // CR 608.2c: no Rat among the attackers, no trample
    @Test
    fun chargingHooliganWithoutARatHasNoTrample() = scenario {
        alice.battlefield(ruffian, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ruffian, bear)
        resolveAll()
        expect {
            ruffian.power shouldBe 5
            ruffian lacksKeyword Keyword.Trample.kind
        }
    }

    // CR 106.12b: a basic land tapped for mana makes three times as much; a nonbasic land doesn't
    @Test
    fun virtueOfStrengthTriplesABasicLandsMana() = scenario {
        alice.battlefield(might, "Forest", hollow)
        alice.activates("Forest")
        expect { alice.pool.map { it.color } shouldBe List(3) { Color.Green } }
        alice.activates(hollow)
        expect { alice.pool.size shouldBe 4 }
    }

    // CR 106.12b: the tripled mana counts when paying: one Forest pays for a {2}{G} creature
    @Test
    fun virtueOfStrengthPaysAThreeManaSpellWithOneForest() = scenario {
        alice.battlefield(might, "Forest")
        alice.hand(courier)
        alice.casts(courier)
        resolveAll()
        expect { courier.zone shouldBe Zone.Battlefield }
    }

    // CR 106.12b: "if you tap": an opponent's basic land isn't affected
    @Test
    fun virtueOfStrengthDoesntTripleAnOpponentsLand() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield(might)
        bob.battlefield("Forest")
        bob.activates("Forest")
        expect { bob.pool.size shouldBe 1 }
    }

    // CR 113.10, 608.2h: the granted ability of the sacrificed artifact deals 1 plus the two Pantry Brawls Alice controls
    @Test
    fun foodFightDealsOnePlusTheNumberOfFoodFights() = scenario {
        alice.battlefield(brawl, brawl, bauble, "Mountain", "Mountain")
        alice.activates(bauble, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 17
            bauble.zone shouldBe Zone.Graveyard
        }
    }

    // CR 601.2f: during Alice's turn the {1} is reduced to nothing
    @Test
    fun hyldasCrownCostsNothingDuringYourTurn() = scenario {
        alice.battlefield(diadem)
        bob.battlefield(bear)
        alice.activates(diadem, target = bear)
        resolveAll()
        expect { bear.tapped shouldBe true }
    }

    // CR 601.2f: during Bob's turn it costs the full {1}; Alice has no mana
    @Test
    fun hyldasCrownCostsOneDuringAnOpponentsTurn() = scenario {
        turn(number = 2, active = bob, priority = alice)
        alice.battlefield(diadem)
        bob.battlefield(bear)
        alice.activates(diadem, target = bear)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 602.2b, 601.2f: Morwen's power 1: the Imp's {2} costs {1}
    @Test
    fun agathaReducesAnAbilityByItsPower() = scenario {
        alice.battlefield(morwen, imp, "Mountain")
        alice.activates(imp, ability = 0)
        resolveAll()
        expect { imp.power shouldBe 2 }
    }

    // the card's own floor: power 3 still leaves {1} of the {2}; {1}{R} becomes {R}, the {R} counting toward the one mana
    @Test
    fun agathaCantReduceACostBelowOneMana() = scenario {
        alice.battlefield(morwen, counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        alice.battlefield(imp)
        alice.activates(imp, ability = 0)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    @Test
    fun agathaLeavesAColoredCostAtOneMana() = scenario {
        alice.battlefield(morwen, counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        alice.battlefield(imp, "Mountain")
        alice.activates(imp, ability = 1)
        resolveAll()
        expect { imp.toughness shouldBe 2 }
    }

    // CR 715.3: from the graveyard only as the Adventure; CR 715.4: the Adventure resolves into exile, and the creature may then be cast from there
    @Test
    fun mosswoodDreadknightCastsItsAdventureFromTheGraveyard() = scenario {
        alice.battlefield(darkknight, "Mountain", "Swamp", "Swamp", "Forest", "Forest")
        alice.hand(jab)
        alice.library("Island", "Island")
        alice.casts(jab, target = darkknight)
        resolveAll()
        expect { darkknight.zone shouldBe Zone.Graveyard }
        val casts = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().filter { it.card == card(darkknight) }
        assertTrue(casts.none { it.way.face == 0 })
        alice.casts(darkknight, way = casts.first { it.way.face == 1 }.way)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.life shouldBe 19
            darkknight.zone shouldBe Zone.Exile
        }
        val fromExile = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().first { it.card == card(darkknight) && it.way.face == 0 }
        alice.casts(darkknight, way = fromExile.way)
        resolveAll()
        expect { darkknight.zone shouldBe Zone.Battlefield }
    }

    // CR 608.2h: X is the 2 life lost when the ability resolves; losing 2 more later doesn't raise it: {3}{B} costs {1}{B}, one Swamp isn't enough
    @Test
    fun rowanFixesXAsTheAbilityResolves() = scenario {
        alice.battlefield(rhiannon, "Swamp")
        alice.hand(toll, tithe, harvest)
        alice.casts(toll)
        resolveAll()
        alice.activates(rhiannon)
        resolveAll()
        alice.casts(tithe)
        resolveAll()
        expect { alice.life shouldBe 16 }
        alice.casts(harvest)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 105.2, 601.2f: a black spell costs 2 less after Alice lost 2 life
    @Test
    fun rowanReducesABlackSpell() = scenario {
        alice.battlefield(rhiannon, "Swamp", "Swamp")
        alice.hand(toll, harvest)
        alice.library("Island")
        alice.casts(toll)
        resolveAll()
        alice.activates(rhiannon)
        resolveAll()
        alice.casts(harvest)
        resolveAll()
        expect { alice.hand has "Island" }
    }

    // CR 608.2h, 701.23: the permanent and every card with its name in Bob's graveyard, hand and library are exiled; Bob draws one for the hand's
    @Test
    fun theEndExilesEveryCopyAndReplacesTheHandOnes() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(curtain)
        bob.battlefield(stalker)
        bob.hand(stalker, "Island")
        bob.graveyard(stalker)
        bob.library("Plains", stalker, "Plains")
        alice.casts(curtain)
        // the copies in other zones share the name: the one on the battlefield is the only option
        answer(Answer.Targets(decision<Decision.Targets>().options))
        paySuggested()
        resolveAll()
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(decision<Decision.ChooseObjects>().options))
        resolveAll()
        expect {
            bob.exile.names.count { it == stalker } shouldBe 4
            bob.hand.size shouldBe 2
            bob.hand has "Island"
            bob.library.size shouldBe 1
        }
    }

    // CR 601.2f: at 5 life it costs {B}{B}
    @Test
    fun theEndCostsLessAtFiveLife() = scenario {
        alice.life = 5
        alice.battlefield("Swamp", "Swamp")
        alice.hand(curtain)
        bob.battlefield(stalker)
        alice.casts(curtain, target = stalker)
        resolveAll()
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(decision<Decision.ChooseObjects>().options))
        resolveAll()
        expect { stalker.zone shouldBe Zone.Exile }
    }

    // CR 608.2d: 3 chosen as it resolves: the 4/4 Darkknight and the 3/3 Ogre are destroyed, the 2/2 Bruin stays
    @Test
    fun expelTheInterlopersDestroysAtOrAboveTheChosenNumber() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", ruffian)
        alice.hand(banish)
        bob.battlefield(ogre, bear)
        alice.casts(banish)
        resolveAll()
        val d = decision<Decision.ChooseNumber>()
        assertEquals(0 to 10, d.min to d.max)
        answer(Answer.Number(3))
        resolveAll()
        expect {
            ruffian.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Graveyard
            bear.zone shouldBe Zone.Battlefield
        }
    }

    // CR 700.2d: two of the three modes; CR 108.3: Alice's adventurer card in exile to her hand, Bob's graveyard exiled
    @Test
    fun sentinelOfLostLoreChoosesOneOrMore() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(warden)
        alice.exile(courier)
        bob.exile(peddler)
        bob.graveyard(ogre)
        alice.casts(warden)
        resolveAll()
        modes(0, 2)
        // the lone card Alice owns in exile is chosen without asking; the player is asked
        targets("bob")
        resolveAll()
        expect {
            alice.hand has courier
            ogre.zone shouldBe Zone.Exile
            peddler.zone shouldBe Zone.Exile
        }
    }

    // CR 108.3: a card Bob owns in exile goes to the bottom of Bob's library
    @Test
    fun sentinelOfLostLorePutsAnOpponentsAdventurerOnTheBottom() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(warden)
        bob.exile(peddler)
        bob.library("Island")
        alice.casts(warden)
        resolveAll()
        modes(1)
        resolveAll()
        expect { bob.library.names shouldBe listOf("Island", peddler) }
    }

    // CR 614.12, 607.2d: 2 chosen as it enters; CR 202.3: Bob's spell with mana value 2 makes Bob (not Carol) lose 2 and Alice draw
    @Test
    fun talionPunishesASpellWithTheChosenNumber() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Swamp", "Swamp")
        alice.hand(talwyn)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        carol.library("Island", "Island")
        bob.hand(bear)
        bob.battlefield("Forest", "Forest")
        alice.casts(talwyn)
        resolveAll()
        val d = decision<Decision.ChooseNumber>()
        assertEquals(1 to 10, d.min to d.max)
        answer(Answer.Number(2))
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(bear)
        resolveAll()
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 20
            alice.hand.size shouldBe 1
        }
    }

    // CR 700.2: a mode chosen once for this artifact can't be chosen again
    @Test
    fun threeBowlsOfPorridgeChoosesEachModeOnce() = scenario {
        alice.battlefield(broth, "Plains", "Plains", "Plains", "Plains")
        alice.library("Plains", "Plains")
        bob.library("Island", "Island")
        bob.battlefield(bear, ogre)
        alice.activates(broth, modes = listOf(0), target = bear)
        resolveAll()
        expect { bear.zone shouldBe Zone.Graveyard }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates(broth)
        val options = decision<Decision.Modes>().options
        assertEquals(listOf(false, true, true), options.map { it.available })
        answer(Answer.Modes(listOf(2)))
        if (pending is Decision.ManaPayment) paySuggested()
        resolveAll()
        expect {
            alice.life shouldBe 23
            broth.zone shouldBe Zone.Graveyard
        }
    }
}
