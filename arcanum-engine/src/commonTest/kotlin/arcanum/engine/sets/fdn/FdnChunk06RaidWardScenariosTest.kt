package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The FDN raid and ward cards, played: the invented names carry the oracle text of Alesha, Who Laughs at Fate, Goblin Boarders,
 * Gorehorn Raider, Gutless Plunderer, Perforating Artist, Searslicer Goblin, Skyship Buccaneer, Storm Fleet Spy, Strongbox Raider,
 * Ovika, Enigma Goliath, Koma, World-Eater, Sire of Seven Deaths, Tolarian Terror, Zul Ashur, Lich Lord and Skyknight Squire.
 */
class FdnChunk06RaidWardScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val bear = Cards.invent("Raidmark Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Raidmark Ox", "{2}{G}", "Creature — Ox", "", "2/4")
    private val giant = Cards.invent("Raidmark Giant", "{3}{G}", "Creature — Giant", "", "4/4")
    private val zombie = Cards.invent("Raidmark Zombie", "{1}{B}", "Creature — Zombie", "", "2/2")
    private val hex = Cards.invent("Raidmark Hex", "{B}", "Instant", "Target creature gets -1/-1 until end of turn.")
    private val insight = Cards.invent("Raidmark Insight", "{1}{U}", "Sorcery", "Draw a card.")
    private val denial = Cards.invent("Raidmark Denial", "{U}{U}", "Instant", "Counter target spell.")

    private val vorna = Cards.invent("Vorna, Who Mocks Doom", "{1}{B}{R}", "Legendary Creature — Human Warrior", """
        First strike
        Whenever Vorna attacks, put a +1/+1 counter on it.
        Raid — At the beginning of your end step, if you attacked this turn, return target creature card with mana value less than or equal to Vorna's power from your graveyard to the battlefield.
    """, "2/2")
    private val gangplank = Cards.invent("Gangplank Goblin", "{2}{R}", "Creature — Goblin Pirate",
        "Raid — This creature enters with a +1/+1 counter on it if you attacked this turn.", "3/2")
    private val gorehide = Cards.invent("Gorehide Marauder", "{4}{R}", "Creature — Minotaur Pirate",
        "Raid — When this creature enters, if you attacked this turn, this creature deals 2 damage to any target.", "4/4")
    private val ribcage = Cards.invent("Ribcage Plunderer", "{2}{B}", "Creature — Skeleton Pirate", """
        Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)
        Raid — When this creature enters, if you attacked this turn, look at the top three cards of your library. You may put one of those cards back on top of your library. Put the rest into your graveyard.
    """, "2/2")
    private val virtuoso = Cards.invent("Puncturing Virtuoso", "{1}{B}{R}", "Creature — Devil", """
        Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)
        Raid — At the beginning of your end step, if you attacked this turn, each opponent loses 3 life unless that player sacrifices a nonland permanent of their choice or discards a card.
    """, "3/2")
    private val scorchcutter = Cards.invent("Scorchcutter Goblin", "{1}{R}", "Creature — Goblin Warrior",
        "Raid — At the beginning of your end step, if you attacked this turn, create a 1/1 red Goblin creature token.", "2/1")
    private val corsair = Cards.invent("Cloudhull Corsair", "{3}{U}{U}", "Creature — Human Pirate", """
        Flying
        Raid — When this creature enters, if you attacked this turn, draw a card.
    """, "4/3")
    private val informant = Cards.invent("Galefleet Informant", "{2}{U}", "Creature — Human Pirate",
        "Raid — When this creature enters, if you attacked this turn, draw a card.", "2/2")
    private val reaver = Cards.invent("Lockbox Reaver", "{2}{R}{R}", "Creature — Orc Pirate",
        "Raid — When this creature enters, if you attacked this turn, exile the top two cards of your library. Choose one of them. Until the end of your next turn, you may play that card.", "5/2")
    private val zeruvo = Cards.invent("Zeruvo, Riddle Colossus", "{5}{U}{R}", "Legendary Creature — Phyrexian Nightmare", """
        Flying
        Ward—{3}, Pay 3 life.
        Whenever you cast a noncreature spell, create X 1/1 red Phyrexian Goblin creature tokens, where X is the mana value of that spell. They gain haste until end of turn.
    """, "6/6")
    private val vorga = Cards.invent("Vorga, Deep Devourer", "{3}{G}{G}{U}{U}", "Legendary Creature — Serpent", """
        This spell can't be countered.
        Trample, ward {4}
        Whenever Vorga deals combat damage to a player, create four 3/3 blue Serpent creature tokens named Vorga's Coil.
    """, "8/12")
    private val progenitor = Cards.invent("Progenitor of Seven Ends", "{7}", "Creature — Eldrazi", """
        Reach, first strike
        Vigilance, menace
        Trample, lifelink
        Ward—Pay 7 life.
    """, "7/7")
    private val leviathan = Cards.invent("Archive Leviathan", "{6}{U}", "Creature — Serpent", """
        This spell costs {1} less to cast for each instant and sorcery card in your graveyard.
        Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
    """, "5/5")
    private val kezmar = Cards.invent("Kezmar Ashgrave, Crypt Lord", "{1}{B}", "Legendary Creature — Zombie Warlock", """
        Ward—Pay 2 life. (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays 2 life.)
        {T}: You may cast target Zombie creature card from your graveyard this turn.
    """, "2/2")
    private val page = Cards.invent("Cloudrider Page", "{1}{W}", "Creature — Cat Scout", """
        Whenever another creature you control enters, put a +1/+1 counter on this creature.
        As long as this creature has three or more +1/+1 counters on it, it has flying and is a Knight in addition to its other types.
    """, "1/1")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** Alice attacks with [attacker] on turn 1 and moves on to her second main phase. */
    private fun Scenario.attackWith(attacker: String) {
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(attacker)
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
    }

    // CR 508.1, CR 603.4, CR 608.2h: her power is 3 after the attack counter, so the 3-mana-value card is the only legal target
    @Test
    fun vornaReturnsACardUpToHerPower() = scenario {
        alice.battlefield(vorna)
        alice.graveyard(ox, giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vorna)
        resolveAll()
        expect { vorna.power shouldBe 3 }
        advanceTo(TurnStep.End)
        (pending as? Decision.Targets)?.let { d ->
            assertEquals(listOf(target(ox)), d.options)
            alice.answers(Answer.Targets(listOf(target(ox))))
        }
        resolveAll()
        expect {
            alice.battlefield has ox
            alice.graveyard has giant
        }
    }

    // CR 603.4: without an attack the raid trigger doesn't trigger
    @Test
    fun vornaReturnsNothingWithoutAnAttack() = scenario {
        alice.battlefield(vorna)
        alice.graveyard(bear)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.graveyard has bear }
    }

    // CR 614.1c
    @Test
    fun gangplankGoblinEntersWithACounterAfterAnAttack() = scenario {
        alice.battlefield(bear, "Mountain", "Mountain", "Mountain")
        alice.hand(gangplank)
        attackWith(bear)
        alice.casts(gangplank)
        resolveAll()
        expect { gangplank.counters(p1p1) shouldBe 1 }
    }

    // CR 614.1c
    @Test
    fun gangplankGoblinEntersWithoutACounterBeforeAnAttack() = scenario {
        alice.battlefield(bear, "Mountain", "Mountain", "Mountain")
        alice.hand(gangplank)
        alice.casts(gangplank)
        resolveAll()
        expect { gangplank.counters(p1p1) shouldBe 0 }
    }

    // CR 603.4, CR 120.3a
    @Test
    fun gorehideDealsTwoAfterAnAttack() = scenario {
        alice.battlefield(bear, *Array(5) { "Mountain" })
        alice.hand(gorehide)
        attackWith(bear)
        alice.casts(gorehide)
        allPass()
        alice.answers(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    // CR 603.4, CR 401.5
    @Test
    fun ribcagePlundererKeepsOneOnTopAndMillsTheRest() = scenario {
        alice.battlefield(bear, "Swamp", "Swamp", "Swamp")
        alice.hand(ribcage)
        alice.library("Island", "Forest", "Plains", "Mountain")
        attackWith(bear)
        alice.casts(ribcage)
        resolveAll()
        alice.chooses("Forest")
        resolveAll()
        expect {
            alice.library.names.first() shouldBe "Forest"
            alice.graveyard has "Island"
            alice.graveyard has "Plains"
            alice.library has "Mountain"
        }
    }

    // CR 118.12: Bob discards rather than lose 3 life
    @Test
    fun virtuosoOpponentDiscardsInstead() = scenario {
        alice.battlefield(virtuoso)
        bob.hand(insight)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(virtuoso)
        advanceTo(TurnStep.End)
        resolveAll()
        // his one card is the only way to pay, so taking the "unless" pays it
        bob.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.life shouldBe 17
            bob.graveyard has insight
        }
    }

    // CR 701.9a: a discard is from the hand, so a card in Bob's graveyard can't pay it, and with no nonland permanent he loses 3
    @Test
    fun virtuosoOpponentWithAnEmptyHandLosesThreeLife() = scenario {
        alice.battlefield(virtuoso)
        bob.battlefield("Island")
        bob.graveyard(insight)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(virtuoso)
        advanceTo(TurnStep.End)
        resolveAll()
        // no way to pay, so nothing is asked
        expect { pending shouldBe null }
        expect {
            bob.life shouldBe 14
            bob.battlefield has "Island"
        }
    }

    // CR 603.4, CR 111.4
    @Test
    fun scorchcutterMakesAGoblinAfterAnAttack() = scenario {
        alice.battlefield(scorchcutter)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(scorchcutter)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield has "Goblin Token" }
    }

    // CR 603.4
    @Test
    fun informantDrawsAfterAnAttack() = scenario {
        alice.battlefield(bear, "Island", "Island", "Island")
        alice.hand(informant)
        alice.library("Plains")
        attackWith(bear)
        alice.casts(informant)
        resolveAll()
        expect { alice.hand has "Plains" }
    }

    // CR 603.4
    @Test
    fun corsairDrawsNothingWithoutAnAttack() = scenario {
        alice.battlefield(*Array(5) { "Island" })
        alice.hand(corsair)
        alice.library("Plains")
        alice.casts(corsair)
        resolveAll()
        expect {
            alice.battlefield has corsair
            alice.library has "Plains"
        }
    }

    // CR 603.4, CR 305.1: the chosen exiled land can be played this turn
    @Test
    fun reaverLetsAliceThePlayChosenCard() = scenario {
        alice.battlefield(bear, *Array(4) { "Mountain" })
        alice.hand(reaver)
        alice.library("Forest", insight, "Plains")
        attackWith(bear)
        alice.casts(reaver)
        resolveAll()
        alice.chooses("Forest")
        resolveAll()
        alice.plays("Forest")
        expect {
            alice.battlefield has "Forest"
            alice.exile has insight
            alice.library has "Plains"
        }
    }

    // CR 107.3c, CR 112.7a: a 2-mana-value sorcery makes two Goblins, with haste until end of turn
    @Test
    fun zeruvoMakesGoblinsForTheSpellsManaValue() = scenario {
        alice.battlefield(zeruvo, "Island", "Island")
        alice.hand(insight)
        alice.library("Plains")
        alice.casts(insight)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Phyrexian Goblin Token" } shouldBe 2
            card("Phyrexian Goblin Token") hasKeyword Keyword.Haste
            alice.hand has "Plains"
        }
    }

    // CR 702.21a: Bob pays both {3} and 3 life, so his spell resolves
    @Test
    fun zeruvoWardTakesManaAndLife() = scenario {
        alice.battlefield(zeruvo)
        bob.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        bob.hand(hex)
        alice.passes()
        bob.casts(hex, target = zeruvo)
        allPass()
        payWard()
        resolveAll()
        expect {
            bob.life shouldBe 17
            zeruvo.toughness shouldBe 5
        }
    }

    /** The opponent agrees to pay the ward cost, mana as suggested. */
    private fun Scenario.payWard() {
        bob.answers(Answer.YesNo(true))
        (pending as? Decision.ManaPayment)?.let { d -> bob.answers(Answer.ManaPayment(d.suggestion)) }
    }

    // CR 702.21a: Bob declines the 7 life, so his spell is countered
    @Test
    fun progenitorWardCountersUnlessSevenLife() = scenario {
        alice.battlefield(progenitor)
        bob.battlefield("Swamp")
        bob.hand(hex)
        alice.passes()
        bob.casts(hex, target = progenitor)
        allPass()
        bob.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            bob.life shouldBe 20
            progenitor.toughness shouldBe 7
            bob.graveyard has hex
        }
    }

    // CR 702.21a: paid with 7 life
    @Test
    fun progenitorWardPaidWithSevenLife() = scenario {
        alice.battlefield(progenitor)
        bob.battlefield("Swamp")
        bob.hand(hex)
        alice.passes()
        bob.casts(hex, target = progenitor)
        allPass()
        payWard()
        resolveAll()
        expect {
            bob.life shouldBe 13
            progenitor.toughness shouldBe 6
        }
    }

    // CR 101.2: Bob's counterspell resolves, and Vorga stays on the Stack and resolves
    @Test
    fun vorgaCantBeCountered() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Island", "Island")
        alice.hand(vorga)
        bob.battlefield("Island", "Island")
        bob.hand(denial)
        alice.casts(vorga)
        alice.passes()
        bob.casts(denial, target = vorga)
        resolveAll()
        expect {
            alice.battlefield has vorga
            bob.graveyard has denial
        }
    }

    // CR 510.2, CR 111.4: four 3/3 blue Serpents named Vorga's Coil
    @Test
    fun vorgaMakesFourCoils() = scenario {
        alice.battlefield(vorga)
        attackWith(vorga)
        expect {
            bob.life shouldBe 12
            alice.battlefield.names.count { it == "Vorga's Coil" } shouldBe 4
            "Vorga's Coil".power shouldBe 3
        }
    }

    // CR 601.2f: two instant and sorcery cards make it cost {4}{U}
    @Test
    fun leviathanCostsLessForEachInstantAndSorcery() = scenario {
        alice.battlefield(*Array(5) { "Island" })
        alice.graveyard(hex, insight, bear)
        alice.hand(leviathan)
        alice.casts(leviathan)
        resolveAll()
        expect { alice.battlefield has leviathan }
    }

    // CR 601.2a: only a Zombie creature card is a legal target, and it can then be cast from the graveyard
    @Test
    fun kezmarLetsAliceCastAZombieFromHerGraveyard() = scenario {
        alice.battlefield(kezmar, "Swamp", "Swamp")
        alice.graveyard(zombie, bear)
        alice.activates(kezmar)
        assertEquals(listOf(target(zombie)), decision<Decision.Targets>().options)
        alice.answers(Answer.Targets(listOf(target(zombie))))
        resolveAll()
        alice.casts(zombie)
        resolveAll()
        expect { alice.battlefield has zombie }
    }

    // CR 613.1d, 613.1f: at three counters it has flying and is a Knight too
    @Test
    fun pageFliesAsAKnightAtThreeCounters() = scenario {
        alice.battlefield(page, "Forest", "Forest", counters = listOf(p1p1 to 2))
        alice.hand(bear)
        expect { page lacksKeyword Keyword.Flying }
        alice.casts(bear)
        resolveAll()
        expect {
            page.counters(p1p1) shouldBe 3
            page hasKeyword Keyword.Flying
            page.subtypes shouldBe listOf(Subtype("Cat"), Subtype("Scout"), Subtype("Knight"))
        }
    }
}
