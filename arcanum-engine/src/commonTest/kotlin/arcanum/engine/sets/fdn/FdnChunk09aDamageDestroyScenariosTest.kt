package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/**
 * Chunk 09a played: the invented names carry the oracle text of Drakuseth, Felling Blow, Maelstrom Pulse, Steel Hellkite, Myojin of
 * Night's Reach, Lunar Insight, Abyssal Harvester, Etali and Fiery Annihilation.
 */
class FdnChunk09aDamageDestroyScenariosTest {
    private val tyrant = Cards.invent("Cinderhorn Tyrant", "{4}{R}{R}{R}", "Legendary Creature — Dragon", """
        Flying
        Whenever this creature attacks, this creature deals 4 damage to any target and 3 damage to each of up to two other targets.
    """, "7/7")
    private val blow = Cards.invent("Thornwake Blow", "{2}{G}", "Sorcery",
        "Put a +1/+1 counter on target creature you control. Then that creature deals damage equal to its power to target creature an opponent controls.")
    private val pulse = Cards.invent("Gorgebrand Pulse", "{1}{B}{G}", "Sorcery",
        "Destroy target nonland permanent and all other permanents with the same name as that permanent.")
    private val wyrm = Cards.invent("Cindersteel Wyrm", "{6}", "Artifact Creature — Dragon", """
        Flying
        {2}: This creature gets +1/+0 until end of turn.
        {X}: Destroy each nonland permanent with mana value X whose controller was dealt combat damage by this creature this turn. Activate only once each turn.
    """, "5/5")
    private val sentinel = Cards.invent("Nightfold Sentinel", "{5}{B}{B}{B}", "Legendary Creature — Spirit", """
        Nightfold Sentinel enters with a divinity counter on it if you cast it from your hand.
        Nightfold Sentinel has indestructible as long as it has a divinity counter on it.
        Remove a divinity counter from Nightfold Sentinel: Each opponent discards their hand.
    """, "5/2")
    private val insight = Cards.invent("Prism Insight", "{2}{U}", "Sorcery", "Draw a card for each different mana value among nonland permanents you control.")
    private val reaper = Cards.invent("Gravetithe Reaper", "{1}{B}{B}", "Creature — Demon Warlock",
        "{T}: Exile target creature card from a graveyard that was put there this turn. Create a token that's a copy of it, except it's a Nightmare in addition to its other types. Then exile all other Nightmare tokens you control.", "3/2")
    private val elder = Cards.invent("Stormcaller Elder", "{4}{R}{R}", "Legendary Creature — Elder Dinosaur",
        "Whenever this creature attacks, exile the top card of each player's library, then you may cast any number of spells from among those cards without paying their mana costs.", "6/6")
    private val annihilation = Cards.invent("Searing Dissolution", "{2}{R}", "Instant",
        "This spell deals 5 damage to target creature. Exile up to one target Equipment attached to that creature. If that creature would die this turn, exile it instead.")

    private val cub = Cards.invent("Duskpelt Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val colt = Cards.invent("Duskpelt Colt", "{G}", "Creature — Horse", "", "1/1")
    private val pup = Cards.invent("Duskpelt Pup", "{G}", "Creature — Dog", "", "1/1")
    private val brute = Cards.invent("Duskpelt Brute", "{1}{G}{G}", "Creature — Ogre", "", "3/3")
    private val blade = Cards.invent("Duskpelt Blade", "{1}", "Artifact — Equipment", "Equipped creature gets +1/+1.\nEquip {1}")
    private val hex = Cards.invent("Duskpelt Hex", "{B}", "Instant", "Destroy target creature.")
    private val gift = Cards.invent("Duskpelt Gift", "{R}", "Sorcery", "Draw a card.")
    private val charm = Cards.invent("Duskpelt Charm", "{2}", "Artifact", "")
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val divinity = CounterKind.Named("divinity")

    private fun Scenario.payAsSuggested() {
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    // CR 120.3, 608.2c: the counter is put first, so the creature's power (3) is what it deals
    @Test
    fun felling_blow_counter_comes_first() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", cub)
        alice.hand(blow)
        bob.battlefield(brute)
        alice.casts(blow, targets = listOf(cub, brute))
        allPass()
        expect {
            cub.counters(p1p1) shouldBe 1
            brute.zone shouldBe Zone.Graveyard
        }
    }

    // CR 201.2, 608.2b: all permanents with the same name go, whoever controls them; other names stay
    @Test
    fun maelstrom_pulse_destroys_every_permanent_with_that_name() = scenario {
        alice.battlefield("Swamp", "Forest", "Forest", cub)
        alice.hand(pulse)
        bob.battlefield(cub, cub, colt)
        alice.casts(pulse, target = cub)
        allPass()
        expect {
            cub.zone shouldBe Zone.Graveyard
            colt.zone shouldBe Zone.Battlefield
        }
    }

    // CR 701.9a, 602.2: removing the counter is the cost; the opponent discards everything, the controller nothing
    @Test
    fun myojin_makes_the_opponent_discard_their_hand() = scenario {
        alice.battlefield(sentinel, counters = listOf(divinity to 1))
        alice.hand(cub)
        bob.hand(colt, pup, brute)
        alice.activates(sentinel)
        allPass()
        expect {
            bob.hand.isEmpty()
            bob.graveyard has brute
            alice.hand has cub
        }
    }

    // CR 202.3, 608.2c: mana values 2, 1 and 1 among the nonland permanents are two different values; the land doesn't count
    @Test
    fun lunar_insight_counts_different_mana_values() = scenario {
        alice.battlefield("Island", "Island", "Island", cub, colt, pup, "Forest")
        alice.hand(insight)
        alice.library(gift, gift, gift, gift)
        alice.casts(insight)
        allPass()
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 115.3, 120.3: 4 damage to the first target and 3 to each of two others, all different
    @Test
    fun drakuseth_splits_its_damage_over_three_targets() = scenario {
        turn(number = 1, active = alice, step = TurnStep.PrecombatMain)
        alice.battlefield(tyrant)
        bob.battlefield(brute, cub, colt)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(tyrant)
        alice.answers(Answer.Targets(listOf(target(brute))))
        alice.answers(Answer.Targets(listOf(target(cub), target(colt))))
        allPass()
        expect {
            brute.zone shouldBe Zone.Graveyard
            cub.zone shouldBe Zone.Graveyard
            colt.zone shouldBe Zone.Graveyard
        }
    }

    // CR 118.9, 305.1: each player's top card is exiled, the spells may be cast free, a land stays exiled
    @Test
    fun elder_casts_spells_from_both_libraries() = scenario {
        alice.battlefield(elder)
        alice.library("Forest", gift)
        bob.library(brute, "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(elder)
        allPass()
        alice.chooses(brute)
        allPass()
        expect {
            alice.exile has "Forest"
            alice.battlefield has brute
        }
    }

    // CR 120.3, 614.6: the Equipment is exiled, the creature takes 5 and is exiled instead of dying
    @Test
    fun annihilation_exiles_the_creature_and_its_equipment() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(annihilation)
        bob.battlefield(brute, blade)
        attach(blade, brute)
        alice.casts(annihilation, targets = listOf(brute, blade))
        allPass()
        expect {
            brute.zone shouldBe Zone.Exile
            blade.zone shouldBe Zone.Exile
        }
    }

    // CR 120.2a: only the permanents of a player this creature dealt combat damage to are destroyed
    @Test
    fun wyrm_destroys_permanents_of_the_damaged_player_with_that_mana_value() = scenario {
        alice.battlefield(wyrm, "Forest", "Forest", "Forest", cub, charm)
        bob.battlefield(cub, brute)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wyrm)
        allPass()
        allPass()
        allPass()
        alice.activates(wyrm, ability = 1, x = 2)
        allPass()
        expect {
            alice.battlefield has cub
            bob.graveyard has cub
            bob.battlefield has brute
        }
    }

    // CR 707.2: the token is a copy of the exiled card plus the type; the older Nightmare token is exiled, the new one stays
    @Test
    fun harvester_copies_a_creature_that_just_died() = scenario {
        alice.battlefield(reaper, "Swamp")
        alice.hand(hex)
        bob.battlefield(brute)
        alice.casts(hex, target = brute)
        allPass()
        alice.activates(reaper, target = brute)
        allPass()
        expect {
            bob.exile has brute
            alice.battlefield has brute
        }
    }
}
