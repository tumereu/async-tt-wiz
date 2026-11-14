package arcanum.engine.sets.otj

import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test

/** OTJ chunk 12r: control of a permanent while an Aura stays attached to it, up to X modes for a modal spell, a draw for damage equal to
 * toughness and "plus X" damage for the turn, and a draw for an enters batch none of which had mana spent to cast it. */
class OtjChunk12rLegendsScenariosTest {
    private val veyra = Cards.invent("Veyra, the Enthraller", "{1}{W}{U}{B}", "Legendary Creature — Human Warlock",
        "Lifelink\nWhenever an Aura you control becomes attached to a nonland permanent an opponent controls with mana value less than or equal to " +
            "that Aura's mana value, gain control of that permanent for as long as that Aura is attached to it.", "4/4")
    private val charm = Cards.invent("Glass Charm", "{1}{U}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +1/+1.")
    private val brute = Cards.invent("Moss Brute", "{1}{G}", "Creature — Beast", "", "2/2")
    private val mule = Cards.invent("Copper Mule", "{2}{W}", "Creature — Ox", "", "2/3")
    private val torvin = Cards.invent("Torvin of Many Roads", "{G}{U}{R}", "Legendary Creature — Human Wizard",
        "Whenever you cast a modal spell, choose up to X, where X is the number of times you chose a mode for that spell —\n" +
            "• Exile the top card of your library. Until the end of your next turn, you may play it.\n" +
            "• Put a +1/+1 counter on Torvin. It gains trample until end of turn.\n" +
            "• Create a 1/1 blue Bird creature token with flying.", "3/3")
    private val remedy = Cards.invent("Dual Remedy", "{1}{G}", "Sorcery", "Choose one or both —\n• You gain 2 life.\n• Draw a card.")
    private val marsa = Cards.invent("Marsa Quickdraw, Deadeye", "{R}{W}", "Legendary Creature — Human Mercenary",
        "Whenever a source you control deals noncombat damage to a creature equal to that creature's toughness, draw a card.\n" +
            "{X}, {T}: If a source you control would deal noncombat damage to a permanent or player this turn, it deals that much damage plus X instead.", "2/3")
    private val flick = Cards.invent("Cinder Flick", "{R}", "Instant", "Cinder Flick deals 2 damage to any target.")
    private val kaimon = Cards.invent("Kaimon, the Unseen", "{U}{B}", "Legendary Creature — Human Ninja Rogue",
        "Menace\nWhenever Kaimon and/or one or more other nontoken creatures you control enter, if none of them were cast or no mana was spent " +
            "to cast them, draw a card.", "2/3")
    private val summons = Cards.invent("Grave Summons", "{1}{B}", "Sorcery", "Return target creature card from your graveyard to the battlefield.")

    // CR 303.4a: an Aura spell enters attached to its target, so it becomes attached to it (CR 603.2e); CR 202.3: mana value 2 at most the
    // Aura's 2; CR 613.1b: its controller changes; CR 611.2b: control ends once the Aura is no longer attached to it
    @Test
    fun anAuraOnAnOpponentsPermanentTakesItWhileAttached() = scenario(players = 3) {
        alice.battlefield(veyra, "Island", "Island")
        alice.hand(charm)
        bob.battlefield(brute)
        alice.casts(charm, target = brute)
        resolveAll()
        expect { brute.controller shouldBe alice.id }
        move(charm, Zone.Graveyard)
        expect { brute.controller shouldBe bob.id }
    }

    // CR 202.3: a permanent with mana value 3 is greater than the Aura's 2: it doesn't trigger
    @Test
    fun aPermanentWithGreaterManaValueStaysWithItsController() = scenario(players = 3) {
        alice.battlefield(veyra, "Island", "Island")
        alice.hand(charm)
        carol.battlefield(mule)
        alice.casts(charm, target = mule)
        resolveAll()
        expect {
            mule.controller shouldBe carol.id
            charm.zone shouldBe Zone.Battlefield
        }
    }

    /** Casts Dual Remedy choosing [spellModes] by hand, so the trigger's own mode choice stays open. */
    private fun Scenario.castChoosing(spellModes: List<Int>) {
        alice.casts(remedy)
        answer(Answer.Modes(spellModes))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    // CR 700.2: two modes chosen for the modal spell let the trigger choose up to two (CR 603.3c: as it is put on the Stack)
    @Test
    fun aModalSpellWithTwoModesGivesUpToTwo() = scenario {
        alice.battlefield(torvin, "Forest", "Forest")
        alice.hand(remedy)
        alice.library("Island", "Island")
        castChoosing(listOf(0, 1))
        expect { decision<Decision.Modes>().max shouldBe 2 }
        modes(1, 2)
        resolveAll()
        expect {
            torvin.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            alice.battlefield has "Bird Token"
            alice.life shouldBe 22
            alice.hand has "Island"
        }
    }

    // CR 700.2: one mode chosen: up to one
    @Test
    fun aModalSpellWithOneModeGivesUpToOne() = scenario {
        alice.battlefield(torvin, "Forest", "Forest")
        alice.hand(remedy)
        castChoosing(listOf(0))
        expect { decision<Decision.Modes>().max shouldBe 1 }
    }

    // CR 700.2: a spell without modes isn't modal: nothing triggers
    @Test
    fun aSpellWithoutModesDoesntTrigger() = scenario {
        alice.battlefield(torvin, "Mountain")
        alice.hand(flick)
        bob.battlefield(mule)
        alice.casts(flick, target = mule)
        expect { stack.size shouldBe 1 }
    }

    // CR 120.3: 2 damage to a creature with toughness 2 draws a card; CR 603.2: compared as it is dealt (the creature then dies)
    @Test
    fun damageEqualToToughnessDrawsACard() = scenario {
        alice.battlefield(marsa, "Mountain")
        alice.hand(flick)
        alice.library("Island")
        bob.battlefield(brute)
        alice.casts(flick, target = brute)
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Graveyard
            alice.hand has "Island"
        }
    }

    // CR 120.3: 2 damage to a creature with toughness 3 isn't equal to its toughness
    @Test
    fun damageBelowToughnessDrawsNothing() = scenario {
        alice.battlefield(marsa, "Mountain")
        alice.hand(flick)
        alice.library("Island")
        bob.battlefield(mule)
        alice.casts(flick, target = mule)
        resolveAll()
        expect { alice.hand lacks "Island" }
    }

    // CR 614.1a, 611.2a: for the rest of the turn noncombat damage from alice's sources is 2 more (X fixed as it resolved, CR 608.2h);
    // 2 + 2 to a 2/3 is more than its toughness: no draw
    @Test
    fun plusXForTheRestOfTheTurn() = scenario {
        alice.battlefield(marsa, "Mountain", "Mountain", "Mountain")
        alice.hand(flick)
        alice.library("Island")
        bob.battlefield(mule)
        alice.activates(marsa, x = 2)
        resolveAll()
        alice.casts(flick, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 16
            alice.hand lacks "Island"
        }
    }

    // CR 601.2h: Kaimon cast with mana: nothing to draw
    @Test
    fun castWithManaDrawsNothing() = scenario {
        alice.battlefield("Island", "Swamp")
        alice.hand(kaimon)
        alice.library("Plains")
        alice.casts(kaimon)
        resolveAll()
        expect {
            kaimon.zone shouldBe Zone.Battlefield
            alice.hand lacks "Plains"
        }
    }

    // CR 603.4: a creature put onto the battlefield, not cast: draw a card
    @Test
    fun aCreatureReturnedNotCastDrawsACard() = scenario {
        alice.battlefield(kaimon, "Swamp", "Swamp")
        alice.hand(summons)
        alice.graveyard(brute)
        alice.library("Plains")
        alice.casts(summons, target = brute)
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Battlefield
            alice.hand has "Plains"
        }
    }

    // CR 601.2h, 118.9: a creature cast without paying its mana cost had no mana spent: draw a card
    @Test
    fun aCreatureCastWithoutManaDrawsACard() = scenario {
        alice.battlefield(kaimon)
        alice.hand(brute)
        alice.library("Plains")
        alice.castsWithoutCosts(brute)
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Battlefield
            alice.hand has "Plains"
        }
    }

    // CR 601.2h: another creature cast with mana: nothing to draw
    @Test
    fun anotherCreatureCastWithManaDrawsNothing() = scenario {
        alice.battlefield(kaimon, "Forest", "Forest")
        alice.hand(brute)
        alice.library("Plains")
        alice.casts(brute)
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Battlefield
            alice.hand lacks "Plains"
        }
    }
}
