package arcanum.engine.sets.dmu

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
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The DMU kicker cards of chunk 01, played: the invented names carry the oracle text of Archangel of Wrath, Balduvian Atrocity, In Thrall to
 * the Pit, Monstrous War-Leech, Pixie Illusionist, Protect the Negotiators, Sheoldred's Restoration, Stronghold Arena, Temporal Firestorm,
 * Urborg Lhurgoyf, Vodalian Mindsinger, Aggressive Sabotage, Battlewing Mystic, Benalish Sleeper, Choking Miasma, Cleaving Skyrider and
 * Colossal Growth.
 */
class DmuChunk01KickerScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val cub = Cards.invent("Thornwood Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Thornwood Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val fawn = Cards.invent("Thornwood Fawn", "{1}{G}", "Creature — Elk", "", "2/2")
    private val titan = Cards.invent("Thornwood Titan", "{3}{G}{G}", "Creature — Giant", "", "5/5")
    private val lesson = Cards.invent("Thornwood Lesson", "{1}{U}", "Sorcery", "Draw a card.")

    private val archangel = Cards.invent("Seraph of Twin Furies", "{2}{W}{W}", "Creature — Angel", """
        Kicker {B} and/or {R} (You may pay an additional {B} and/or {R} as you cast this spell.)
        Flying, lifelink
        When this creature enters, if it was kicked, it deals 2 damage to any target.
        When this creature enters, if it was kicked twice, it deals 2 damage to any target.
    """, "3/4")
    private val atrocity = Cards.invent("Grimfrost Ravager", "{2}{B}", "Creature — Phyrexian Berserker", """
        Kicker {R} (You may pay an additional {R} as you cast this spell.)
        Menace
        When this creature enters, if it was kicked, return target creature card with mana value 3 or less from your graveyard to the battlefield. It gains haste. Sacrifice it at the beginning of the next end step.
    """, "2/3")
    private val thrall = Cards.invent("Shackled by the Abyss", "{3}{R}", "Sorcery", """
        Kicker {2}{B} (You may pay an additional {2}{B} as you cast this spell.)
        Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn. If this spell was kicked, sacrifice that creature at the beginning of the next end step.
    """)
    private val leech = Cards.invent("Ravenous Mire-Leech", "{3}{B}", "Creature — Leech Horror", """
        Kicker {U} (You may pay an additional {U} as you cast this spell.)
        As this creature enters, if it was kicked, mill four cards. (To mill a card, put the top card of your library into your graveyard.)
        Ravenous Mire-Leech's power and toughness are each equal to the greatest mana value among cards in your graveyard.
    """, "*/*")
    private val pixie = Cards.invent("Sprite Glamourweaver", "{U}", "Creature — Faerie Wizard", """
        Kicker {3}{G} (You may pay an additional {3}{G} as you cast this spell.)
        Flying
        If this creature was kicked, it enters with two +1/+1 counters on it.
        {T}: Target land you control becomes the basic land type of your choice until end of turn.
    """, "1/1")
    private val negotiators = Cards.invent("Guard the Envoys", "{1}{U}", "Instant", """
        Kicker {W} (You may pay an additional {W} as you cast this spell.)
        If this spell was kicked, create a 1/1 white Soldier creature token.
        Counter target spell unless its controller pays {1} for each creature you control.
    """)
    private val restoration = Cards.invent("Grim Restoration Rite", "{3}{B}", "Sorcery", """
        Kicker {2}{W} (You may pay an additional {2}{W} as you cast this spell.)
        Return target creature card from your graveyard to the battlefield. If this spell was kicked, you gain life equal to that card's mana value. Otherwise, you lose that much life.
        Exile Grim Restoration Rite.
    """)
    private val arena = Cards.invent("Citadel Proving Pit", "{1}{B}", "Enchantment", """
        Kicker {G} and/or {W} (You may pay an additional {G} and/or {W} as you cast this spell.)
        When this enchantment enters, you gain 3 life for each time it was kicked.
        Whenever one or more creatures you control deal combat damage to a player, you may reveal the top card of your library and put it into your hand. If you do, you lose life equal to its mana value.
    """)
    private val firestorm = Cards.invent("Chronal Blazestorm", "{3}{R}{R}", "Sorcery", """
        Kicker {1}{W} and/or {1}{U} (You may pay an additional {1}{W} and/or {1}{U} as you cast this spell.)
        Choose up to X creatures and/or planeswalkers you control, where X is the number of times this spell was kicked. Those permanents phase out.
        Chronal Blazestorm deals 5 damage to each creature and each planeswalker.
    """)
    private val lhurgoyf = Cards.invent("Fenmire Lhurgoyf", "{1}{G}", "Creature — Lhurgoyf", """
        Kicker {U} and/or {B} (You may pay an additional {U} and/or {B} as you cast this spell.)
        As this creature enters, mill three cards for each time it was kicked.
        Fenmire Lhurgoyf's power is equal to the number of creature cards in your graveyard and its toughness is equal to that number plus 1.
    """, "*/1+*")
    private val mindsinger = Cards.invent("Merrow Thoughtcaller", "{1}{U}{U}", "Creature — Merfolk Wizard", """
        Kicker {1}{R} and/or {1}{G}
        This creature enters with two +1/+1 counters on it for each time it was kicked.
        When this creature enters, gain control of target creature with power less than this creature's power for as long as you control this creature.
    """, "2/2")
    private val sabotage = Cards.invent("Hostile Subversion", "{2}{B}", "Sorcery", """
        Kicker {R} (You may pay an additional {R} as you cast this spell.)
        Target player discards two cards. If this spell was kicked, it deals 3 damage to that player.
    """)
    private val mystic = Cards.invent("Stormwing Seer", "{1}{U}", "Creature — Bird Wizard", """
        Kicker {R} (You may pay an additional {R} as you cast this spell.)
        Flying
        When this creature enters, if it was kicked, discard your hand, then draw two cards.
    """, "2/1")
    private val sleeper = Cards.invent("Dormant Infiltrator", "{1}{W}", "Creature — Phyrexian Human Soldier", """
        Kicker {B} (You may pay an additional {B} as you cast this spell.)
        When this creature enters, if it was kicked, each player sacrifices a creature of their choice.
    """, "3/1")
    private val miasma = Cards.invent("Strangling Vapors", "{1}{B}{B}", "Sorcery", """
        Kicker {G} (You may pay an additional {G} as you cast this spell.)
        If this spell was kicked, put a +1/+1 counter on a creature you control.
        All creatures get -2/-2 until end of turn.
    """)
    private val skyrider = Cards.invent("Splitting Skyknight", "{2}{W}", "Creature — Human Warrior", """
        Flash
        Kicker {2}{R} (You may pay an additional {2}{R} as you cast this spell.)
        Flying
        When this creature enters, if it was kicked, it deals X damage to any target, where X is the number of attacking creatures.
    """, "2/2")
    private val growth = Cards.invent("Titanic Surge", "{1}{G}", "Instant", """
        Kicker {R} (You may pay an additional {R} as you cast this spell.)
        Target creature gets +3/+3 until end of turn. If this spell was kicked, instead that creature gets +4/+4 and gains trample and haste until end of turn.
    """)

    /** Answers the cast's two kicker questions in order, paying the engine's suggestion for each kicker paid. */
    private fun Scenario.kicks(first: Boolean, second: Boolean) {
        for (yes in listOf(first, second)) {
            assertTrue(pending is Decision.May, "a kicker question, not $pending")
            answer(Answer.YesNo(yes))
        }
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
    }

    // CR 702.33b, d: both kicker costs paid, so both triggers (CR 603.4)
    @Test
    fun archangelKickedTwiceDealsDamageTwice() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Swamp", "Mountain")
        alice.hand(archangel)
        alice.casts(archangel, kicked = 2)
        resolveAll()
        while (pending is Decision.Targets) { answer(Answer.Targets(listOf(target("bob")))); resolveAll() }
        expect {
            alice.battlefield has archangel
            bob.life shouldBe 16
        }
    }

    // CR 702.33d: one of the two kicker costs paid is kicked, not kicked twice
    @Test
    fun archangelKickedOnceDealsDamageOnce() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Swamp", "Mountain")
        alice.hand(archangel)
        alice.casts(archangel)
        kicks(first = true, second = false)
        resolveAll()
        while (pending is Decision.Targets) { answer(Answer.Targets(listOf(target("bob")))); resolveAll() }
        expect {
            alice.battlefield has archangel
            bob.life shouldBe 18
            alice.battlefield.names.count { it == "Mountain" } shouldBe 1
            card("Mountain").tapped shouldBe false
        }
    }

    // CR 702.33b, d: 3 life for each kicker cost paid
    @Test
    fun arenaGainsThreeLifeForEachKicker() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Plains")
        alice.hand(arena)
        alice.casts(arena, kicked = 2)
        resolveAll()
        expect {
            alice.battlefield has arena
            alice.life shouldBe 26
        }
    }

    // CR 400.7: the returned permanent gains haste and is sacrificed at the next end step, not this creature (CR 603.7)
    @Test
    fun atrocityReturnsAHastyCreatureForTheTurn() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(atrocity)
        alice.graveyard(ox)
        alice.casts(atrocity, kicked = 1)
        resolveAll()
        expect {
            alice.battlefield has ox
            ox hasKeyword Keyword.Haste.kind
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.graveyard has ox
            alice.battlefield has atrocity
        }
    }

    // CR 613.1b: control until end of turn; kicked, its new controller sacrifices it at the next end step (CR 603.7, 701.21a)
    @Test
    fun thrallKickedSacrificesTheStolenCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Swamp", "Swamp", "Swamp")
        alice.hand(thrall)
        bob.battlefield(ox, tapped = true)
        alice.casts(thrall, target = ox, kicked = 1)
        resolveAll()
        expect {
            ox.controller shouldBe alice.id
            ox.tapped shouldBe false
            ox hasKeyword Keyword.Haste.kind
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { bob.graveyard has ox }
    }

    // CR 614.1c: kicked, it mills four as it enters, and its power and toughness are the greatest mana value there (CR 604.3)
    @Test
    fun leechKickedMillsAsItEnters() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Island")
        alice.hand(leech)
        alice.library("Forest", titan, "Forest", cub, "Island")
        alice.casts(leech, kicked = 1)
        resolveAll()
        expect {
            alice.graveyard has titan
            alice.library.size shouldBe 1
            leech.power shouldBe 5
            leech.toughness shouldBe 5
        }
    }

    // CR 614.1c, 704.5f: unkicked, nothing is milled; with an empty graveyard it is 0/0 and dies
    @Test
    fun leechUnkickedMillsNothing() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Island")
        alice.hand(leech)
        alice.library("Forest", titan)
        alice.casts(leech, kicked = 0)
        resolveAll()
        expect {
            alice.library.size shouldBe 2
            alice.graveyard has leech
        }
    }

    // CR 305.7: the land becomes the chosen basic land type in place of its own until end of turn
    @Test
    fun pixieChangesALandsType() = scenario {
        alice.battlefield(pixie, "Forest")
        alice.library("Island")
        bob.library("Island")
        alice.activates(pixie, target = "Forest")
        resolveAll()
        answer(Answer.SubtypeChoice(Subtype("Island")))
        resolveAll()
        expect {
            assertTrue(card("Forest").view.characteristics!!.hasSubtype(Subtype("Island")), "an Island")
            assertTrue(!card("Forest").view.characteristics!!.hasSubtype(Subtype("Forest")), "no longer a Forest")
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { assertTrue(card("Forest").view.characteristics!!.hasSubtype(Subtype("Forest")), "a Forest again") }
    }

    // CR 702.33d, CR 614.1c: kicked, it enters with two +1/+1 counters
    @Test
    fun pixieKickedEntersWithCounters() = scenario {
        alice.battlefield("Island", "Forest", "Forest", "Forest", "Forest")
        alice.hand(pixie)
        alice.casts(pixie, kicked = 1)
        resolveAll()
        expect { pixie.counters(p1p1) shouldBe 2 }
    }

    // CR 118.12, 608.2c: kicked, the Soldier comes first, so the controller must pay {3} for three creatures; two lands can't
    @Test
    fun negotiatorsKickedCountsTheNewSoldier() = scenario {
        turn(active = bob)
        bob.battlefield("Forest", "Forest", "Island", "Island")
        bob.hand(lesson)
        alice.battlefield(cub, ox, "Island", "Island", "Plains")
        alice.hand(negotiators)
        bob.casts(lesson)
        bob.passes()
        alice.casts(negotiators, target = lesson, kicked = 1)
        resolve()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        expect {
            stack.size shouldBe 0
            bob.graveyard has lesson
            alice.battlefield has "Soldier Token"
        }
    }

    // CR 118.12: unkicked, two creatures ask {2}, which the controller pays, and the spell isn't countered
    @Test
    fun negotiatorsUnkickedCanBePaid() = scenario {
        turn(active = bob)
        bob.battlefield("Forest", "Forest", "Island", "Island")
        bob.hand(lesson)
        alice.battlefield(cub, ox, "Island", "Island")
        alice.hand(negotiators)
        bob.library("Forest")
        bob.casts(lesson)
        bob.passes()
        alice.casts(negotiators, target = lesson, kicked = 0)
        resolve()
        assertTrue(pending is Decision.May, "bob is asked to pay, not $pending")
        answer(Answer.YesNo(true))
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
        resolveAll()
        expect {
            bob.graveyard has lesson
            bob.hand has "Forest"
        }
    }

    // CR 702.33d, 608.2h: unkicked, you lose life equal to the returned card's mana value; the spell exiles itself
    @Test
    fun restorationUnkickedLosesLife() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(restoration)
        alice.graveyard(titan)
        alice.casts(restoration, target = titan, kicked = 0)
        resolveAll()
        expect {
            alice.battlefield has titan
            alice.life shouldBe 15
            alice.exile has restoration
        }
    }

    // CR 702.33d: kicked, you gain that much life instead
    @Test
    fun restorationKickedGainsLife() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Plains", "Plains", "Plains")
        alice.hand(restoration)
        alice.graveyard(titan)
        alice.casts(restoration, target = titan, kicked = 1)
        resolveAll()
        expect {
            alice.battlefield has titan
            alice.life shouldBe 25
        }
    }

    // CR 702.26b: kicked once, one chosen creature phases out and isn't dealt damage; the rest are (CR 120.3)
    @Test
    fun firestormKickedPhasesOutTheChosenCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Plains", "Plains", cub, titan)
        alice.hand(firestorm)
        bob.battlefield(ox)
        alice.casts(firestorm)
        kicks(first = true, second = false)
        resolveAll()
        alice.chooses(titan)
        resolveAll()
        expect {
            assertTrue(card(titan).view.obj?.phasedOut == true, "phased out")
            alice.graveyard has cub
            bob.graveyard has ox
        }
    }

    // CR 614.1c: kicked twice, it mills six as it enters; CR 604.3: two creature cards make it 2/3
    @Test
    fun lhurgoyfKickedTwiceMillsSix() = scenario {
        alice.battlefield("Forest", "Forest", "Island", "Swamp")
        alice.hand(lhurgoyf)
        alice.library(cub, "Forest", ox, "Island", "Forest", "Island", "Forest")
        alice.casts(lhurgoyf, kicked = 2)
        resolveAll()
        expect {
            alice.library.size shouldBe 1
            lhurgoyf.power shouldBe 2
            lhurgoyf.toughness shouldBe 3
        }
    }

    // CR 614.1c: four counters for two kicks; CR 611.2b: it takes a creature with less power
    @Test
    fun mindsingerKickedTwiceStealsASmallerCreature() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain", "Mountain", "Forest", "Forest")
        alice.hand(mindsinger)
        bob.battlefield(titan)
        alice.casts(mindsinger, kicked = 2)
        resolveAll()
        expect {
            mindsinger.counters(p1p1) shouldBe 4
            titan.controller shouldBe alice.id
        }
    }

    // CR 115.1: unkicked it is 2/2, so a 5/5 isn't a legal target and the trigger is removed (CR 603.3d)
    @Test
    fun mindsingerUnkickedCantTakeABiggerCreature() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain", "Mountain", "Forest", "Forest")
        alice.hand(mindsinger)
        bob.battlefield(titan)
        alice.casts(mindsinger, kicked = 0)
        resolveAll()
        expect {
            mindsinger.counters(p1p1) shouldBe 0
            titan.controller shouldBe bob.id
        }
    }

    // CR 702.33d: kicked, the targeted player also takes 3 damage
    @Test
    fun sabotageKickedDealsDamageToThatPlayer() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(sabotage)
        bob.hand(cub, ox, titan)
        alice.casts(sabotage, target = "bob", kicked = 1)
        resolveAll()
        bob.chooses(cub, ox)
        resolveAll()
        expect {
            bob.graveyard has cub
            bob.graveyard has ox
            bob.hand has titan
            bob.life shouldBe 17
        }
    }

    // CR 701.9a: "discard your hand" discards every card in it, then two are drawn (audit: the count doesn't limit it)
    @Test
    fun mysticKickedDiscardsTheWholeHand() = scenario {
        alice.battlefield("Island", "Island", "Mountain")
        alice.hand(mystic, cub, ox, titan)
        alice.library("Forest", "Island")
        alice.casts(mystic, kicked = 1)
        resolveAll()
        expect {
            alice.graveyard has cub
            alice.graveyard has ox
            alice.graveyard has titan
            alice.hand.size shouldBe 2
        }
    }

    // CR 101.4, 701.21a: with three players each one sacrifices a creature of their own choice
    @Test
    fun sleeperKickedMakesEachPlayerSacrifice() = scenario(players = 3) {
        alice.battlefield("Plains", "Swamp", "Swamp", cub)
        alice.hand(sleeper)
        bob.battlefield(ox)
        carol.battlefield(titan)
        alice.casts(sleeper, kicked = 1)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(cub)
        resolveAll()
        expect {
            alice.graveyard has cub
            alice.battlefield has sleeper
            bob.graveyard has ox
            carol.graveyard has titan
        }
    }

    // CR 613.4c: kicked, the counter saves a 2/2 from -2/-2; the others die
    @Test
    fun miasmaKickedSavesOneCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Forest", cub)
        alice.hand(miasma)
        bob.battlefield(ox, fawn)
        alice.casts(miasma, kicked = 1)
        resolveAll()
        expect {
            alice.battlefield has cub
            cub.power shouldBe 1
            bob.graveyard has fawn
            bob.battlefield has ox
        }
    }

    // CR 608.2h: kicked during combat, X is the number of attacking creatures as the trigger resolves
    @Test
    fun skyriderKickedCountsAttackers() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Mountain", "Mountain", "Mountain")
        alice.battlefield(cub, ox)
        alice.hand(skyrider)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cub, ox)
        alice.casts(skyrider, kicked = 1)
        resolveAll()
        if (pending is Decision.Targets) { answer(Answer.Targets(listOf(target("bob")))); resolveAll() }
        expect { bob.life shouldBe 18 }
    }

    // CR 601.2c: kicked, the target still exists and gets +4/+4, trample and haste instead (audit: the target isn't dropped)
    @Test
    fun growthKickedGivesTheBiggerBonus() = scenario {
        alice.battlefield("Forest", "Forest", "Mountain", cub)
        alice.hand(growth)
        alice.casts(growth, target = cub, kicked = 1)
        resolveAll()
        expect {
            cub.power shouldBe 6
            cub.toughness shouldBe 6
            cub hasKeyword Keyword.Trample.kind
            cub hasKeyword Keyword.Haste.kind
        }
    }
}
