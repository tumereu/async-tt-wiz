package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Chunk 27 played: the invented names carry the oracle text of Exsanguinate, Kalastria Highborn, Pulse Tracker, Sanguine Syphoner,
 * Vampire Neonate, Vengeful Bloodwitch, Flamewake Phoenix, Juggernaut, Niv-Mizzet Visionary, Fynn the Fangbearer, Wildborn Preserver,
 * Burnished Hart, Circuitous Route, Expedition Map, Fierce Empath, Micromancer, Rune-Scarred Demon, Solemn Simulacrum, Springbloom Druid,
 * Consuming Aberration, Crusader of Odric, Enigma Drake and Aurelia the Warleader.
 */
class FdnChunk27LifeAttackSearchSetPtScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val tithe = Cards.invent("Draining Tithe", "{X}{B}{B}", "Sorcery", "Each opponent loses X life. You gain life equal to the life lost this way.")
    private val heir = Cards.invent("Crimson Heir", "{B}{B}", "Creature — Vampire Shaman", """
        Whenever this creature or another Vampire you control dies, you may pay {B}. If you do, target player loses 2 life and you gain 2 life.
    """, "2/2")
    private val tracker = Cards.invent("Vein Tracker", "{B}", "Creature — Vampire Rogue", "Whenever this creature attacks, each opponent loses 1 life.", "1/1")
    private val syphoner = Cards.invent("Blood Syphoner", "{1}{B}", "Creature — Vampire Warlock", """
        Whenever this creature attacks, each opponent loses 1 life and you gain 1 life.
    """, "1/3")
    private val infant = Cards.invent("Vampire Infant", "{B}", "Creature — Vampire", "{2}, {T}: Each opponent loses 1 life and you gain 1 life.", "0/3")
    private val leech = Cards.invent("Vengeful Leech", "{1}{B}", "Creature — Vampire Warlock", """
        Whenever this creature or another creature you control dies, target opponent loses 1 life and you gain 1 life.
    """, "1/1")
    private val phoenix = Cards.invent("Ember Phoenix", "{1}{R}{R}", "Creature — Phoenix", """
        Flying, haste
        This creature attacks each combat if able.
        Ferocious — At the beginning of combat on your turn, if you control a creature with power 4 or greater, you may pay {R}. If you do, return this card from your graveyard to the battlefield.
    """, "2/2")
    private val ram = Cards.invent("Iron Ram", "{4}", "Artifact Creature — Juggernaut", """
        This creature attacks each combat if able.
        This creature can't be blocked by Walls.
    """, "5/3")
    private val sage = Cards.invent("Dragon Sage", "{4}{U}{R}", "Legendary Creature — Dragon Wizard", """
        Flying
        You have no maximum hand size.
        Whenever a source you control deals noncombat damage to an opponent, you draw that many cards.
    """, "5/5")
    private val venom = Cards.invent("Venom Warrior", "{1}{G}", "Legendary Creature — Human Warrior", """
        Deathtouch
        Whenever a creature you control with deathtouch deals combat damage to a player, that player gets two poison counters.
    """, "1/3")
    private val keeper = Cards.invent("Woodland Keeper", "{1}{G}", "Creature — Elf Archer", """
        Flash
        Reach
        Whenever another non-Human creature you control enters, you may pay {X}. When you do, put X +1/+1 counters on this creature.
    """, "2/2")
    private val deer = Cards.invent("Copper Deer", "{3}", "Artifact Creature — Elk", """
        {3}, Sacrifice this creature: Search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.
    """, "2/2")
    private val path = Cards.invent("Wandering Path", "{3}{G}", "Sorcery", """
        Search your library for up to two basic land cards and/or Gate cards, put them onto the battlefield tapped, then shuffle.
    """)
    private val map = Cards.invent("Surveyor's Map", "{1}", "Artifact", """
        {2}, {T}, Sacrifice this artifact: Search your library for a land card, reveal it, put it into your hand, then shuffle.
    """)
    private val seeker = Cards.invent("Giant Seeker", "{2}{G}", "Creature — Elf", """
        When this creature enters, you may search your library for a creature card with mana value 6 or greater, reveal it, put it into your hand, then shuffle.
    """, "1/1")
    private val finder = Cards.invent("Cheap Spell Finder", "{3}{U}", "Creature — Human Wizard", """
        When this creature enters, you may search your library for an instant or sorcery card with mana value 1, reveal it, put it into your hand, then shuffle.
    """, "3/3")
    private val tutor = Cards.invent("Hellish Tutor", "{5}{B}{B}", "Creature — Demon", """
        Flying
        When this creature enters, search your library for a card, put it into your hand, then shuffle.
    """, "6/6")
    private val golem = Cards.invent("Pewter Golem", "{4}", "Artifact Creature — Golem", """
        When this creature enters, you may search your library for a basic land card, put that card onto the battlefield tapped, then shuffle.
        When this creature dies, you may draw a card.
    """, "2/2")
    private val druid = Cards.invent("Mossback Druid", "{2}{G}", "Creature — Elf Druid", """
        When this creature enters, you may sacrifice a land. If you do, search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.
    """, "1/1")
    private val horror = Cards.invent("Hungry Horror", "{3}{U}{B}", "Creature — Horror", """
        Hungry Horror's power and toughness are each equal to the number of cards in your opponents' graveyards.
        Whenever you cast a spell, each opponent reveals cards from the top of their library until they reveal a land card, then puts those cards into their graveyard.
    """, "*/*")
    private val legion = Cards.invent("Legion of Light", "{2}{W}", "Creature — Human Soldier", """
        Legion of Light's power and toughness are each equal to the number of creatures you control.
    """, "*/*")
    private val drake = Cards.invent("Storm Drake", "{1}{U}{R}", "Creature — Drake", """
        Flying
        Storm Drake's power is equal to the number of instant and sorcery cards in your graveyard.
    """, "*/4")
    private val aurora = Cards.invent("Aurora, the Warleader", "{2}{R}{R}{W}{W}", "Legendary Creature — Angel", """
        Flying, vigilance, haste
        Whenever Aurora attacks for the first time each turn, untap all creatures you control. After this phase, there is an additional combat phase.
    """, "3/4")

    private val jab = Cards.invent("Spark Prick", "{R}", "Instant", "Spark Prick deals 9 damage to target creature.")
    private val dusk = Cards.invent("Dusk Vampire", "{1}{B}", "Creature — Vampire", "", "2/2")
    private val moss = Cards.invent("Moss Hound", "{1}{G}", "Creature — Hound", "", "2/2")
    private val bigHound = Cards.invent("Moss Mastiff", "{4}{G}", "Creature — Hound", "", "5/5")
    private val reedWall = Cards.invent("Reed Barrier", "{1}{G}", "Creature — Wall", "", "0/5")
    private val giant = Cards.invent("Hill Giant Elder", "{5}{G}{G}", "Creature — Giant", "", "7/7")
    private val gate = Cards.invent("Lowmoor Gate", null, "Land — Gate", "")
    private val idol = Cards.invent("Tin Idol", "{1}", "Artifact", "")
    private val medal = Cards.invent("Tin Medal", "{1}", "Artifact", "")
    private val oneDrop = Cards.invent("Spark Flick", "{R}", "Instant", "Spark Flick deals 1 damage to target creature.")
    private val oneSorcery = Cards.invent("Quiet Study", "{U}", "Sorcery", "Draw a card.")
    private val spell = Cards.invent("Brisk Sketch", "{U}", "Sorcery", "Draw a card.")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.pay(player: arcanum.engine.scenario.ScenarioPlayer) {
        player.answers(Answer.ManaPayment(decision<arcanum.engine.decision.Decision.ManaPayment>().suggestion))
    }

    private fun Scenario.stock() {
        alice.library("Island", "Forest", "Plains", "Swamp", "Mountain", "Island", "Forest", "Plains")
        bob.library("Island", "Forest", "Plains", "Swamp", "Mountain", "Island", "Forest", "Plains")
    }

    // CR 107.3, 119.3, 119.4: each opponent loses X, you gain the life actually lost
    @Test
    fun titheDrainsXAndGainsTheSame() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(tithe)
        alice.casts(tithe, x = 3)
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    // CR 119.4, 603.12: paying {B} makes the target player lose 2 and you gain 2
    @Test
    fun heirPaidDrainsTwo() = scenario {
        stock()
        alice.battlefield(heir, dusk, "Mountain", "Swamp")
        alice.hand(jab)
        alice.casts(jab, target = dusk)
        resolveAll()
        alice.answers(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolveAll()
        alice.answers(Answer.YesNo(true))
        pay(alice)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    // Audit: declining the payment loses and gains nothing (the gain is inside the "If you do")
    @Test
    fun heirDecliningDrainsNothing() = scenario {
        stock()
        alice.battlefield(heir, dusk, "Mountain", "Swamp")
        alice.hand(jab)
        alice.casts(jab, target = dusk)
        resolveAll()
        alice.answers(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolveAll()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            bob.life shouldBe 20
            alice.life shouldBe 20
        }
    }

    // CR 508.1m, 119.3: attacking makes each opponent lose 1 life
    @Test
    fun trackerDrainsOnAttack() = scenario {
        alice.battlefield(tracker)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(tracker)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 20
        }
    }

    @Test
    fun syphonerDrainsOnAttack() = scenario {
        alice.battlefield(syphoner)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(syphoner)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    // CR 602.2, 119.3
    @Test
    fun infantDrainsForTwoAndTap() = scenario {
        alice.battlefield(infant, "Swamp", "Swamp")
        alice.activates(infant)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
            infant.tapped shouldBe true
        }
    }

    // CR 700.4, 603.10a: another creature dying drains the target opponent
    @Test
    fun leechDrainsWhenAnotherCreatureDies() = scenario {
        stock()
        alice.battlefield(leech, moss, "Mountain")
        alice.hand(jab)
        alice.casts(jab, target = moss)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    // CR 508.1d: a creature that attacks each combat if able must attack
    @Test
    fun ramMustAttackEachCombat() = scenario {
        alice.battlefield(ram)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks() }
    }

    // CR 509.1b: a Wall can't block it, another creature can
    @Test
    fun ramCantBeBlockedByWalls() = scenario {
        alice.battlefield(ram)
        bob.battlefield(reedWall, moss)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ram)
        allPass()
        assertFailsWith<IllegalArgumentException> { bob.blocks(reedWall to ram) }
        bob.blocks(moss to ram)
    }

    // CR 402.2: no maximum hand size, no discard at the cleanup step
    @Test
    fun sageKeepsAllCards() = scenario {
        alice.battlefield(sage)
        alice.hand("Forest", "Forest", "Forest", "Island", "Island", "Island", "Mountain", "Mountain", "Swamp")
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { alice.hand.size shouldBe 9 }
    }

    // CR 120.2b, 120.3: noncombat damage to an opponent draws that many cards; combat damage doesn't
    @Test
    fun sageDrawsForNoncombatDamageToAnOpponent() = scenario {
        stock()
        val burn = Cards.invent("Quick Singe", "{R}", "Instant", "Quick Singe deals 2 damage to target opponent.")
        alice.battlefield(sage, "Mountain")
        alice.hand(burn)
        alice.casts(burn)
        alice.answers(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        pay(alice)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.hand.size shouldBe 2
        }
    }

    // CR 122.1f, 120.3: a deathtouch creature's combat damage to a player gives two poison counters, a plain creature's none
    @Test
    fun venomWarriorGivesPoison() = scenario {
        alice.battlefield(venom)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(venom)
        allPass()
        allPass()
        allPass()
        expect { bob.counters(CounterKind.Poison) shouldBe 2 }
    }

    // CR 603.12, 107.3: pay X, then put X counters on the Keeper
    @Test
    fun keeperPaysXForCounters() = scenario {
        stock()
        alice.battlefield(keeper, "Forest", "Forest", "Forest", "Forest")
        alice.hand(moss)
        alice.casts(moss)
        resolveAll()
        alice.answers(Answer.Number(2))
        alice.answers(Answer.YesNo(true))
        pay(alice)
        resolveAll()
        expect { keeper.counters(p1p1) shouldBe 2 }
    }

    // Audit: declining the payment puts no counters
    @Test
    fun keeperDecliningPutsNoCounters() = scenario {
        stock()
        alice.battlefield(keeper, "Forest", "Forest", "Forest", "Forest")
        alice.hand(moss)
        alice.casts(moss)
        resolveAll()
        alice.answers(Answer.Number(2))
        resolveAll()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect { keeper.counters(p1p1) shouldBe 0 }
    }

    // Human creatures don't trigger it
    @Test
    fun keeperIgnoresHumans() = scenario {
        stock()
        alice.battlefield(keeper, "Forest", "Forest", "Forest", "Forest")
        alice.hand(legion)
        alice.battlefield("Plains")
        alice.casts(legion)
        resolveAll()
        expect {
            legion.zone shouldBe Zone.Battlefield
            keeper.counters(p1p1) shouldBe 0
        }
    }

    // CR 701.23a, 701.24: two basic lands come onto the battlefield tapped
    @Test
    fun deerFetchesTwoBasicsTapped() = scenario {
        alice.battlefield(deer, "Swamp", "Swamp", "Swamp")
        alice.library("Forest", "Island", "Mountain", idol)
        alice.activates(deer)
        resolveAll()
        alice.chooses("Forest", "Island")
        resolveAll()
        expect {
            "Forest".zone shouldBe Zone.Battlefield
            "Forest".tapped shouldBe true
            "Island".tapped shouldBe true
            deer.zone shouldBe Zone.Graveyard
            alice.library has "Mountain"
        }
    }

    // CR 701.23a: basic lands and/or Gates
    @Test
    fun pathFetchesABasicAndAGate() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(path)
        alice.library("Island", gate, "Mountain")
        alice.casts(path)
        resolveAll()
        alice.chooses("Island", gate)
        resolveAll()
        expect {
            "Island".zone shouldBe Zone.Battlefield
            gate.zone shouldBe Zone.Battlefield
            gate.tapped shouldBe true
            alice.library has "Mountain"
        }
    }

    // CR 701.23a, 701.20a: the Map finds any land and reveals it; the Map sacrifices itself
    @Test
    fun mapFindsALandIntoHand() = scenario {
        alice.battlefield(map, "Island", "Island")
        alice.library(idol, "Mountain", "Forest")
        alice.activates(map)
        resolveAll()
        alice.chooses("Mountain")
        resolveAll()
        expect {
            alice.hand has "Mountain"
            map.zone shouldBe Zone.Graveyard
        }
    }

    // CR 701.23a, 202.3: only a creature with mana value 6 or more qualifies
    @Test
    fun seekerFindsABigCreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(seeker)
        alice.library(moss, giant, "Forest")
        alice.casts(seeker)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        alice.chooses(giant)
        resolveAll()
        expect {
            alice.hand has giant
            alice.library has moss
        }
    }

    // CR 202.3: an instant or sorcery with mana value exactly 1
    @Test
    fun finderFindsAOneManaSpell() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(finder)
        alice.library(jab, tithe, oneSorcery, "Island")
        alice.casts(finder)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        alice.chooses(oneSorcery)
        resolveAll()
        expect {
            alice.hand has oneSorcery
            alice.library has tithe
        }
    }

    // CR 701.23a: any card, not optional
    @Test
    fun tutorFindsAnyCard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(tutor)
        alice.library(moss, giant, "Forest")
        alice.casts(tutor)
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect { alice.hand has giant }
    }

    // CR 701.23a, 614.1: a basic land onto the battlefield tapped; a death trigger may draw
    @Test
    fun golemFetchesALand() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(golem)
        alice.library("Forest", moss)
        alice.casts(golem)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        alice.chooses("Forest")
        resolveAll()
        expect {
            "Forest".zone shouldBe Zone.Battlefield
            "Forest".tapped shouldBe true
        }
    }

    @Test
    fun golemMayDrawWhenItDies() = scenario {
        stock()
        alice.battlefield(golem, "Mountain")
        alice.hand(jab)
        alice.casts(jab, target = golem)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 701.21a, 118.12: sacrificing a land is optional; the search only follows when it was done
    @Test
    fun druidSacrificesALandToFetchTwo() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Island")
        alice.hand(druid)
        alice.library("Mountain", "Swamp", "Plains")
        alice.casts(druid)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        alice.chooses("Island")
        resolveAll()
        alice.chooses("Mountain", "Swamp")
        resolveAll()
        expect {
            "Island".zone shouldBe Zone.Graveyard
            "Mountain".zone shouldBe Zone.Battlefield
            "Swamp".zone shouldBe Zone.Battlefield
            "Mountain".tapped shouldBe true
        }
    }

    // Audit: declining the sacrifice keeps the land and fetches nothing
    @Test
    fun druidDecliningFetchesNothing() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Island")
        alice.hand(druid)
        alice.library("Mountain", "Swamp", "Plains")
        alice.casts(druid)
        resolveAll()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            "Island".zone shouldBe Zone.Battlefield
            alice.library has "Mountain"
            alice.library has "Swamp"
        }
    }

    // CR 604.3: the Horror's size is the cards in the opponents' graveyards only
    @Test
    fun horrorCountsOpponentsGraveyards() = scenario {
        alice.battlefield(horror)
        alice.graveyard(moss, idol)
        bob.graveyard(bigHound, medal, reedWall)
        expect {
            horror.power shouldBe 3
            horror.toughness shouldBe 3
        }
    }

    // CR 701.20a, 404.1: casting a spell mills each opponent through the first land (the land included)
    @Test
    fun horrorMillsUntilALand() = scenario {
        alice.battlefield(horror)
        alice.hand(spell)
        alice.battlefield("Island")
        bob.library(medal, bigHound, "Forest", "Island")
        alice.casts(spell)
        resolveAll()
        expect {
            bob.graveyard has medal
            bob.graveyard has bigHound
            bob.graveyard has "Forest"
            bob.library has "Island"
            horror.power shouldBe 3
        }
    }

    // CR 604.3: counts every creature you control, itself included
    @Test
    fun legionCountsYourCreatures() = scenario {
        alice.battlefield(legion)
        bob.battlefield(moss)
        expect { legion.power shouldBe 1 }
        alice.battlefield(bigHound, reedWall)
        expect {
            legion.power shouldBe 3
            legion.toughness shouldBe 3
        }
    }

    // CR 604.3: only the power is defined; the toughness stays 4
    @Test
    fun drakePowerCountsInstantsAndSorceries() = scenario {
        alice.battlefield(drake)
        alice.graveyard(jab, tithe, moss)
        bob.graveyard(oneDrop)
        expect {
            drake.power shouldBe 2
            drake.toughness shouldBe 4
        }
    }

    // CR 603.2c, 502.3, 702.20b: the first attack untaps your creatures, vigilance keeps Aurora untapped
    @Test
    fun auroraUntapsCreaturesWhenAttacking() = scenario {
        alice.battlefield(aurora, moss)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(aurora, moss)
        resolveAll()
        expect {
            moss.tapped shouldBe false
            aurora.tapped shouldBe false
        }
    }

    // CR 702.20b: without the trigger a plain attacker taps, vigilance doesn't
    @Test
    fun attackingTapsAPlainCreatureButNotAVigilantOne() = scenario {
        val vigil = Cards.invent("Dawn Sentry", "{2}{W}", "Creature — Human Soldier", "Vigilance", "2/2")
        alice.battlefield(vigil, moss)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vigil, moss)
        expect {
            vigil.tapped shouldBe false
            moss.tapped shouldBe true
        }
    }

    // CR 500.8: Aurora's trigger adds a combat phase
    @Test
    fun auroraAddsACombatPhase() = scenario {
        stock()
        alice.battlefield(aurora)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(aurora)
        resolveAll()
        advanceTo(TurnStep.EndOfCombat)
        allPass()
        assertEquals(TurnStep.BeginningOfCombat, state.turn.step)
    }

    // CR 603.2c: "for the first time each turn": the second attack declaration doesn't trigger it again
    @Test
    fun auroraTriggersOnlyOnTheFirstAttack() = scenario {
        stock()
        alice.battlefield(aurora)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(aurora)
        resolveAll()
        advanceTo(TurnStep.EndOfCombat)
        allPass()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(aurora)
        expect { state.stack.size shouldBe 0 }
    }
}
