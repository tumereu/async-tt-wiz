package arcanum.engine.sets.woe

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Subtype
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targetsIfAsked
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import arcanum.engine.decision.Decision

/**
 * Wilds of Eldraine sweep: siblings of the engine and parser fixes the set's work found, each probing a path the fix's own card didn't
 * take (another kind of ability, another zone change, another payment, three players).
 */
class WoeSweepScenariosTest {
    private val stonepawBear = Cards.invent("Stonepaw Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val thornpeltStag = Cards.invent("Thornpelt Stag", "{3}{G}", "Creature — Elk", "", "3/3")
    private val ridgeGoblinPest = Cards.invent("Ridge Goblin Pest", "{R}", "Creature — Goblin", "", "1/1")

    // ---- Auras entering with nothing to enchant (sibling of the Role token fix, CR 303.4i)

    private val gleamingMantlet = Cards.invent("Gleaming Mantlet", "{W}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +1/+1.")
    private val rekindledBond = Cards.invent("Rekindled Bond", "{W}", "Sorcery", "Return target enchantment card from your graveyard to the battlefield.")

    // CR 303.4g: an Aura card put onto the battlefield with nothing it could enchant stays in its zone
    @Test
    fun auraCardReturnedWithNothingToEnchantStaysInTheGraveyard() = scenario {
        alice.battlefield("Plains")
        alice.graveyard(gleamingMantlet)
        alice.hand(rekindledBond)
        alice.casts(rekindledBond, target = gleamingMantlet)
        resolveAll()
        expect { gleamingMantlet.zone shouldBe Zone.Graveyard }
    }

    // ---- "it" after a zone change is the new object (sibling of the Role "attached to it" fix, CR 400.7)

    private val gravecallRite = Cards.invent("Gravecall Rite", "{2}{B}", "Sorcery",
        "Return target creature card from your graveyard to the battlefield. Put a +1/+1 counter on it.")
    private val flickeringVigil = Cards.invent("Flickering Vigil", "{W}", "Instant",
        "Exile target creature you control, then return it to the battlefield under its owner's control. Put a +1/+1 counter on it.")
    private val barrowmawShovel = Cards.invent("Barrowmaw Shovel", "{1}", "Artifact",
        "{1}, {T}: Return target creature card with mana value 2 or less from your graveyard to the battlefield. It gains haste until end of turn.")
    private val graveAnnexation = Cards.invent("Grave Annexation", "{3}{B}", "Sorcery",
        "Put target creature card from a graveyard onto the battlefield under your control. Put a +1/+1 counter on it.")

    // CR 400.7, 608.2c: the counter goes on the permanent the card became
    @Test
    fun spellPutsACounterOnTheCreatureItReturned() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.graveyard(stonepawBear)
        alice.hand(gravecallRite)
        alice.casts(gravecallRite, target = stonepawBear)
        resolveAll()
        expect {
            stonepawBear.zone shouldBe Zone.Battlefield
            stonepawBear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            stonepawBear.power shouldBe 3
        }
    }

    // CR 400.7: exiled and returned, the creature is a new object; "it" is that new permanent
    @Test
    fun flickerPutsACounterOnTheReturnedCreature() = scenario {
        alice.battlefield("Plains", stonepawBear)
        alice.hand(flickeringVigil)
        alice.casts(flickeringVigil, target = stonepawBear)
        resolveAll()
        expect {
            stonepawBear.zone shouldBe Zone.Battlefield
            stonepawBear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 400.7, 611.2c: the activated ability's haste applies to the returned permanent
    @Test
    fun activatedReturnGivesTheReturnedCreatureHaste() = scenario {
        alice.battlefield("Forest", barrowmawShovel)
        alice.graveyard(stonepawBear)
        alice.activates(barrowmawShovel, target = stonepawBear)
        resolveAll()
        expect {
            stonepawBear.zone shouldBe Zone.Battlefield
            stonepawBear hasKeyword Keyword.Haste
        }
    }

    // CR 400.7, 108.3: three players; Carol's card comes under Alice's control and gets the counter
    @Test
    fun reanimatingAnOpponentsCreaturePutsTheCounterOnIt() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        carol.graveyard(thornpeltStag)
        alice.hand(graveAnnexation)
        alice.casts(graveAnnexation, target = thornpeltStag)
        resolveAll()
        expect {
            thornpeltStag.zone shouldBe Zone.Battlefield
            thornpeltStag.controller shouldBe alice.id
            thornpeltStag.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // ---- A spell's cost reduction on a condition its own announced choices answer (sibling of the bargain reduction, CR 601.2c before 601.2f)

    private val downdraftSnare = Cards.invent("Downdraft Snare", "{2}{B}", "Instant",
        "This spell costs {1} less to cast if it targets a tapped creature.\nDestroy target creature.")

    // CR 601.2c, 601.2f: the target is chosen before the total cost is determined, so a tapped target makes it cost {1}{B}
    @Test
    fun spellCostsLessWhenItTargetsATappedCreature() = scenario {
        alice.battlefield("Swamp", "Swamp")
        bob.battlefield(thornpeltStag, tapped = true)
        alice.hand(downdraftSnare)
        alice.casts(downdraftSnare, target = thornpeltStag)
        resolveAll()
        expect { thornpeltStag.zone shouldBe Zone.Graveyard }
    }

    // CR 601.2f: targeting an untapped creature, the full {2}{B} can't be paid with two lands
    @Test
    fun spellCostsFullWhenItsTargetIsUntapped() = scenario {
        alice.battlefield("Swamp", "Swamp")
        bob.battlefield(thornpeltStag)
        alice.hand(downdraftSnare)
        alice.casts(downdraftSnare, target = thornpeltStag)
        assertEquals(true, decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // ---- A card exiled face down is played with its own characteristics (sibling of the face-down land and mana value fixes)

    private val glimmerthiefRaid = Cards.invent("Glimmerthief Raid", "{U}", "Sorcery",
        "Exile the top two cards of target opponent's library face down. You may look at and play those cards for as long as they remain exiled.")
    private val sparkjolt = Cards.invent("Sparkjolt", "{R}", "Instant", "Sparkjolt deals 3 damage to any target.")

    // CR 406.3a, 601.2a, 601.2f, 307.1: turned face up as it's cast, the exiled instant has its own type and mana cost: castable in the end step,
    // and it costs {R}
    @Test
    fun faceDownExiledInstantIsCastAtInstantSpeedForItsManaCost() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand(glimmerthiefRaid)
        bob.library(sparkjolt, "Plains", "Swamp")
        alice.casts(glimmerthiefRaid, target = "bob", paying = listOf("Island"))
        resolveAll()
        advanceTo(TurnStep.End)
        alice.casts(sparkjolt, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 17
            "Mountain".tapped shouldBe true
        }
    }

    // CR 601.2f, 601.2h: with no mana left, the face-down instant can't be cast for free
    @Test
    fun faceDownExiledInstantStillCostsItsManaCost() = scenario {
        alice.battlefield("Island")
        alice.hand(glimmerthiefRaid)
        bob.library(sparkjolt, "Plains", "Swamp")
        alice.casts(glimmerthiefRaid, target = "bob")
        resolveAll()
        alice.casts(sparkjolt, target = "bob")
        assertEquals(true, decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // ---- Mana multiplying replacements on any permanent tapped for mana (sibling of the land multiplier, CR 106.12b)

    private val wellspringMirror = Cards.invent("Wellspring Mirror", "{4}{G}", "Enchantment",
        "If you tap a permanent for mana, it produces twice as much of that mana instead.")
    private val copperLantern = Cards.invent("Copper Lantern", "{1}", "Artifact", "{T}: Add {C}.")
    private val bronzeEffigy = Cards.invent("Bronze Effigy", "{2}", "Artifact Creature — Golem", "", "2/2")

    // CR 106.12b, 614.1a: the artifact tapped for {C} produces {C}{C}, enough for a {2} spell
    @Test
    fun artifactTappedForManaProducesTwiceAsMuch() = scenario {
        alice.battlefield(wellspringMirror, copperLantern)
        alice.hand(bronzeEffigy)
        alice.casts(bronzeEffigy)
        resolveAll()
        expect { bronzeEffigy.zone shouldBe Zone.Battlefield }
    }

    // ---- "Choose one that hasn't been chosen" on a triggered ability (sibling of the activated fix, CR 700.2)

    private val wheelOfSeasonsPast = Cards.invent("Wheel of Seasons Past", "{2}", "Artifact", """
        At the beginning of your upkeep, choose one that hasn't been chosen —
        • You gain 2 life.
        • Draw a card.
        • Each opponent loses 1 life.
    """.trimIndent())

    // CR 700.2: the first upkeep's mode can't be chosen again on the next upkeep
    @Test
    fun triggeredChooseOneThatHasNotBeenChosenRemembersItsModes() = scenario {
        alice.battlefield(wheelOfSeasonsPast)
        alice.library("Island", "Island", "Island", "Island")
        bob.library("Island", "Island", "Island", "Island")
        turn(1, step = TurnStep.Untap)
        advanceTo(TurnStep.Upkeep)
        answer(arcanum.engine.decision.Answer.Modes(listOf(0)))
        resolveAll()
        expect { alice.life shouldBe 22 }
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertFails { answer(arcanum.engine.decision.Answer.Modes(listOf(0))) }
    }

    private val lanternwrightsToolkit = Cards.invent("Lanternwright's Toolkit", "{1}", "Artifact", """
        {T}: Choose one that hasn't been chosen —
        • You gain 2 life.
        • Draw a card.
    """.trimIndent())
    private val blinkforgeRite = Cards.invent("Blinkforge Rite", "{W}", "Instant",
        "Exile target artifact you control, then return it to the battlefield under its owner's control.")

    // CR 400.7, 700.2: exiled and returned, the artifact is a new object that has chosen no modes yet
    @Test
    fun aReturnedPermanentMayChooseAModeAgain() = scenario {
        alice.battlefield("Plains", lanternwrightsToolkit)
        alice.hand(blinkforgeRite)
        alice.activates(lanternwrightsToolkit, modes = listOf(0))
        resolveAll()
        alice.casts(blinkforgeRite, target = lanternwrightsToolkit)
        resolveAll()
        alice.activates(lanternwrightsToolkit, modes = listOf(0))
        resolveAll()
        expect { alice.life shouldBe 24 }
    }

    // ---- Information about an object gone "this way" is its last known information (sibling of "destroyed this way", CR 608.2h)

    private val kindledAnthem = Cards.invent("Kindled Anthem", "{2}{W}", "Enchantment", "Creatures you control get +1/+1.")
    private val hurlTheFallen = Cards.invent("Hurl the Fallen", "{1}{R}", "Instant",
        "As an additional cost to cast this spell, sacrifice a creature.\nDraw cards equal to the sacrificed creature's power.")
    private val bloodAltar = Cards.invent("Barrowgate Altar", "{2}", "Artifact", "Sacrifice a creature: You gain life equal to the sacrificed creature's toughness.")
    private val tollOfTheFallen = Cards.invent("Toll of the Fallen", "{1}{B}", "Instant", "Destroy target creature. Its controller gains life equal to its power.")

    // CR 608.2h, 113.7a: the sacrificed creature's power as it last existed, the anthem's +1/+1 included
    @Test
    fun sacrificedCreaturesPowerIsItsLastKnownPower() = scenario {
        alice.battlefield("Mountain", "Mountain", kindledAnthem, stonepawBear)
        alice.hand(hurlTheFallen)
        alice.library("Island", "Island", "Island", "Island")
        alice.casts(hurlTheFallen, choosing = listOf(stonepawBear))
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
    }

    // CR 608.2h, 113.7a: a cost's sacrificed creature, as it last existed (toughness 3 with the anthem)
    @Test
    fun costSacrificedCreaturesToughnessIsItsLastKnownToughness() = scenario {
        alice.battlefield(bloodAltar, kindledAnthem, stonepawBear)
        alice.activates(bloodAltar, choosing = listOf(stonepawBear))
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    // CR 608.2h: three players; Carol controlled the destroyed creature, with her anthem it had power 4
    @Test
    fun destroyedCreaturesControllerGainsItsLastKnownPower() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp")
        carol.battlefield(kindledAnthem, thornpeltStag)
        alice.hand(tollOfTheFallen)
        alice.casts(tollOfTheFallen, target = thornpeltStag)
        resolveAll()
        expect {
            carol.life shouldBe 24
            bob.life shouldBe 20
            alice.life shouldBe 20
        }
    }

    // ---- "any other target" means other than the first target, not other than the source (sibling of the "other target" fix, CR 115.4)

    private val twinfireKindler = Cards.invent("Twinfire Kindler", "{2}{R}", "Creature — Elemental",
        "When this creature enters, it deals 2 damage to any target and 1 damage to any other target.", "3/3")

    // CR 115.4: the creature may be its own ability's first target
    @Test
    fun enteringCreatureMayTargetItselfAndAnotherTarget() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(twinfireKindler)
        alice.casts(twinfireKindler)
        resolveAll()
        targetsIfAsked(twinfireKindler)
        targetsIfAsked("bob")
        resolveAll()
        expect {
            twinfireKindler.view.obj?.damage shouldBe 2
            bob.life shouldBe 19
        }
    }

    // ---- "that many" is the amount the ability itself defined first, not the trigger event's (sibling of the "draw that many" fix)

    private val restlessTinkerer = Cards.invent("Restless Tinkerer", "{1}{U}", "Creature — Human Artificer",
        "When this creature enters, discard any number of cards, then draw that many cards.", "1/1")

    // CR 608.2c: two cards discarded, two drawn (the entering event has no amount of its own)
    @Test
    fun enteringTriggerDrawsAsManyAsItDiscarded() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(restlessTinkerer, "Plains", "Swamp", "Forest")
        alice.library("Island", "Island", "Island")
        alice.casts(restlessTinkerer)
        resolveAll()
        alice.chooses("Plains", "Swamp")
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            alice.graveyard.size shouldBe 2
        }
    }

    // ---- "Those creatures" after a counter effect are the creatures it put counters on (sibling of the "Untap those creatures" fix)

    private val rallyingGraft = Cards.invent("Rallying Graft", "{1}{G}", "Sorcery",
        "Put a +1/+1 counter on each creature you control. Those creatures gain trample until end of turn.")

    // CR 608.2c: both of Alice's creatures gain trample, Bob's doesn't
    @Test
    fun thoseCreaturesAreTheOnesThatGotCounters() = scenario {
        alice.battlefield("Forest", "Forest", stonepawBear, thornpeltStag)
        bob.battlefield(ridgeGoblinPest)
        alice.hand(rallyingGraft)
        alice.casts(rallyingGraft)
        resolveAll()
        expect {
            stonepawBear hasKeyword Keyword.Trample
            thornpeltStag hasKeyword Keyword.Trample
            ridgeGoblinPest lacksKeyword Keyword.Trample
            stonepawBear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // ---- Paying life replaced in every way life is paid (sibling of the pay-life replacement on an ability's cost, CR 119.4, 614.1a)

    private val vashkaEcho = Cards.invent("Ivrel, Hoarder of Pages", "{3}{B}{B}", "Legendary Enchantment",
        "If you would pay life while your library has at least that many cards in it, exile that many cards from the top of your library instead.")
    private val bloodpriceInsight = Cards.invent("Bloodprice Insight", "{B}", "Sorcery",
        "As an additional cost to cast this spell, pay 2 life.\nDraw two cards.")
    private val bloodpriceGamble = Cards.invent("Bloodprice Gamble", "{B}", "Sorcery", "You may pay 2 life. If you do, draw two cards.")

    // CR 118.3b, 614.1a: a spell's additional life cost is a life payment too
    @Test
    fun additionalLifeCostOfASpellIsReplaced() = scenario {
        alice.battlefield("Swamp", vashkaEcho)
        alice.hand(bloodpriceInsight)
        alice.library("Plains", "Island", "Swamp", "Forest")
        alice.casts(bloodpriceInsight)
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.exile.names shouldBe listOf("Plains", "Island")
            alice.hand.names shouldBe listOf("Swamp", "Forest")
        }
    }

    // CR 119.4, 614.1a: paying life as the spell resolves is replaced the same way
    @Test
    fun lifePaidAsASpellResolvesIsReplaced() = scenario {
        alice.battlefield("Swamp", vashkaEcho)
        alice.hand(bloodpriceGamble)
        alice.library("Plains", "Island", "Swamp", "Forest")
        alice.casts(bloodpriceGamble)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.exile.names shouldBe listOf("Plains", "Island")
            alice.hand.names shouldBe listOf("Swamp", "Forest")
        }
    }

    // ---- "Dealt damage this way" exile replacements on damage to many creatures (sibling of the single-target fix, CR 614.1a, 615.1a)

    private val searingSweep = Cards.invent("Searing Sweep", "{2}{R}", "Sorcery",
        "Searing Sweep deals 2 damage to each creature. If a creature dealt damage this way would die this turn, exile it instead.")
    private val wardlightVeil = Cards.invent("Wardlight Veil", "{W}", "Instant", "Prevent all damage that would be dealt to target creature this turn.")
    
    // CR 614.1a, 615.1a: the Goblin dealt damage dies and is exiled; the Stag's damage was prevented, so destroyed later it goes to the graveyard
    @Test
    fun sweepExilesOnlyTheCreaturesItDealtDamageTo() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Plains", "Swamp")
        bob.battlefield(ridgeGoblinPest, thornpeltStag)
        alice.hand(wardlightVeil, searingSweep, graveknellVerdict)
        alice.casts(wardlightVeil, target = thornpeltStag)
        resolveAll()
        alice.casts(searingSweep)
        resolveAll()
        expect { ridgeGoblinPest.zone shouldBe Zone.Exile }
        alice.casts(graveknellVerdict, target = thornpeltStag)
        resolveAll()
        expect { thornpeltStag.zone shouldBe Zone.Graveyard }
    }

    // ---- More of "it" after a zone change: a dies trigger returning the card (CR 400.7, 603.10a)

    private val cinderhuskReturner = Cards.invent("Cinderhusk Returner", "{2}{B}", "Creature — Zombie",
        "When this creature dies, return it to the battlefield tapped under its owner's control. Put a +1/+1 counter on it.", "2/2")
    private val graveknellVerdict = Cards.invent("Graveknell Verdict", "{B}", "Instant", "Destroy target creature.")

    // CR 400.7: the counter goes on the permanent the returned card became
    @Test
    fun diesTriggerPutsACounterOnTheReturnedCreature() = scenario {
        alice.battlefield("Swamp", cinderhuskReturner)
        alice.hand(graveknellVerdict)
        alice.casts(graveknellVerdict, target = cinderhuskReturner)
        resolveAll()
        expect {
            cinderhuskReturner.zone shouldBe Zone.Battlefield
            cinderhuskReturner.tapped shouldBe true
            cinderhuskReturner.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // ---- Type-setting in other abilities (siblings of the CR 205.1a/b fixes)

    private val galvanicQuickening = Cards.invent("Galvanic Quickening", "{1}{U}", "Instant",
        "Target artifact becomes an artifact creature with base power and toughness 4/4 until end of turn.")
    private val goblinTotem = Cards.invent("Hearthbound Goblin Totem", "{2}", "Kindred Artifact — Goblin", "")

    // CR 205.1b: becoming an "artifact creature" keeps all prior card types and subtypes (Kindred, Goblin)
    @Test
    fun becomingAnArtifactCreatureKeepsItsTypes() = scenario {
        alice.battlefield("Island", "Island", goblinTotem)
        alice.hand(galvanicQuickening)
        alice.casts(galvanicQuickening, target = goblinTotem)
        resolveAll()
        expect {
            goblinTotem.power shouldBe 4
            goblinTotem.types.toSet() shouldBe setOf(CardType.Kindred, CardType.Artifact, CardType.Creature)
            goblinTotem.subtypes.toSet() shouldBe setOf(Subtype("Goblin"))
        }
    }
}
