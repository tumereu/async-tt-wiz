package arcanum.engine.sets.inr

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** INR chunk 14: graveyard abilities that make or return creatures, once-a-turn death triggers with decayed tokens, delayed returns,
 * replacement of an opponent's death, optional discards, Blood sacrifices, modal removal and damage to each opponent.
 * Invented names carry the oracle text. */
class InrChunk14TokensDamageDelayedDestroyScenariosTest {
    private val cathar = Cards.invent("Dauntless Warden", "{2}{W}", "Creature — Human Soldier", """
        {1}{W}, Exile this card from your graveyard: Create a 1/1 white Spirit creature token with flying. Activate only as a sorcery.
    """, "3/2")
    private val procession = Cards.invent("Ghoulish Cortege", "{1}{B}", "Enchantment", """
        Whenever one or more nontoken creatures die, create a 2/2 black Zombie creature token with decayed. This ability triggers only once each turn.
    """)
    private val deadMan = Cards.invent("Haunted Corpse", "{3}{B}", "Creature — Zombie", """
        When this creature enters, create a 1/1 white Spirit creature token with flying.
        {1}{B}, Discard two cards: Return this card from your graveyard to the battlefield tapped.
    """, "2/2")
    private val guardian = Cards.invent("Pack Warden", "{2}{G}{G}", "Creature — Wolf Spirit", """
        Flash
        When this creature enters, you may discard a land card. If you do, create a 2/2 green Wolf creature token.
    """, "4/3")
    private val guest = Cards.invent("Gluttonous Visitor", "{2}{B}", "Creature — Vampire", """
        When this creature enters, create a Blood token.
        Whenever you sacrifice a Blood token, you gain 1 life.
    """, "1/4")
    private val abrade = Cards.invent("Scour", "{1}{R}", "Instant", """
        Choose one —
        • Scour deals 3 damage to target creature.
        • Destroy target artifact.
    """)
    private val thermo = Cards.invent("Thermo-Mixer", "{1}{R}", "Creature — Human Shaman", """
        Defender
        {T}: This creature deals 1 damage to each opponent.
        Whenever you cast an instant or sorcery spell, untap this creature.
    """, "0/3")
    private val epicure = Cards.invent("Voldaren Gourmand", "{R}", "Creature — Vampire", """
        When this creature enters, it deals 1 damage to each opponent. Create a Blood token.
    """, "1/1")
    private val liesa = Cards.invent("Liesa, Lost Archangel", "{2}{W}{W}{B}", "Legendary Creature — Angel", """
        Flying, lifelink
        Whenever another nontoken creature you control dies, return that card to its owner's hand at the beginning of the next end step.
        If a creature an opponent controls would die, exile it instead.
    """, "4/5")
    private val sweep = Cards.invent("Grim Sweep", "{2}{B}{B}", "Sorcery", "Destroy all creatures.")
    private val hex = Cards.invent("Bonecrack Snare", "{B}", "Instant", "Destroy target creature.")
    private val bolt = Cards.invent("Pebble Jab", "{R}", "Instant", "Pebble Jab deals 1 damage to any target.")
    private val ox = Cards.invent("Tarnished Steer", "{2}", "Creature — Ox", "", "2/2")
    private val cub = Cards.invent("Bramble Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val relic = Cards.invent("Mossy Relic", "{1}", "Artifact", "")

    private fun Scenario.count(name: String) = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == name }

    // CR 602.2, 602.5d: the card works from the graveyard, only at sorcery speed; CR 118.8: it exiles itself as a cost
    @Test
    fun cathar_exilesItselfForASpirit() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.graveyard(cathar)
        alice.activates(cathar)
        resolveAll()
        expect {
            alice.battlefield has "Spirit Token"
            cathar.zone shouldBe Zone.Exile
        }
    }

    // CR 602.5d: not in the opponent's turn
    @Test
    fun cathar_notAtInstantSpeed() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.graveyard(cathar)
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = alice)
        assertFailsWith<Throwable> { alice.activates(cathar) }
    }

    // CR 603.2c: two nontoken creatures dying together are one trigger for one Zombie; CR 603.4 once each turn; tokens don't count
    @Test
    fun procession_onceEachTurnAndNotForTokens() = scenario(players = 3) {
        alice.battlefield(procession, ox, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        bob.battlefield(cub)
        alice.hand(sweep, hex)
        alice.casts(sweep)
        resolveAll()
        expect {
            alice.graveyard has ox
            bob.graveyard has cub
        }
        check(count("Zombie Token") == 1)
        // the decayed Zombie dies later this turn: it is a token (CR 111.7, it ceases to exist), and the ability already triggered
        alice.casts(hex, target = "Zombie Token")
        resolveAll()
        check(count("Zombie Token") == 0)
    }

    // CR 603.4: the second death in the same turn makes no second Zombie
    @Test
    fun procession_secondDeathSameTurnMakesNoZombie() = scenario {
        alice.battlefield(procession, ox, cub, "Swamp", "Swamp", "Swamp")
        alice.hand(hex, hex)
        alice.casts(hex, target = ox)
        resolveAll()
        check(count("Zombie Token") == 1)
        alice.casts(hex, target = cub)
        resolveAll()
        expect { alice.graveyard has cub }
        check(count("Zombie Token") == 1)
    }

    // CR 702.147a: the token has decayed
    @Test
    fun procession_tokenCantBlock() = scenario {
        alice.battlefield(procession, ox, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(hex)
        alice.casts(hex, target = ox)
        resolveAll()
        expect { "Zombie Token" hasKeyword arcanum.api.Keyword.Decayed.kind }
    }

    // CR 602.2, 118.8: discarding two cards pays; the card returns from the graveyard tapped
    @Test
    fun deadMan_returnsTappedFromTheGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(deadMan)
        alice.hand(ox, cub)
        alice.activates(deadMan, choosing = listOf(ox, cub))
        resolveAll()
        expect {
            alice.battlefield has deadMan
            deadMan.tapped shouldBe true
            alice.graveyard has ox
            alice.graveyard has cub
        }
    }

    // CR 608.2c: "if you do" follows the discard of a land card
    @Test
    fun guardian_landDiscardMakesAWolf() = scenario {
        alice.battlefield(*Array(4) { "Forest" })
        alice.hand(guardian, "Forest")
        alice.casts(guardian)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses("Forest")
        resolveAll()
        expect {
            alice.battlefield has "Wolf Token"
            alice.graveyard has "Forest"
        }
    }

    // CR 608.2c: declining the discard makes no Wolf
    @Test
    fun guardian_decliningMakesNothing() = scenario {
        alice.battlefield(*Array(4) { "Forest" })
        alice.hand(guardian, "Forest")
        alice.casts(guardian)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        resolveAll()
        check(count("Wolf Token") == 0)
        expect { alice.hand has "Forest" }
    }

    // CR 701.21a: sacrificing the Blood token gains 1 life; CR 111.10g
    @Test
    fun guest_bloodSacrificeGainsLife() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.library("Swamp", "Swamp")
        alice.hand(guest, ox)
        alice.casts(guest)
        resolveAll()
        expect { alice.battlefield has "Blood Token" }
        val before = alice.life
        alice.activates("Blood Token", choosing = listOf(ox))
        resolveAll()
        expect {
            alice.life shouldBe before + 1
            alice.graveyard has ox
        }
    }

    // CR 700.2: the first mode's damage kills a 2/2
    @Test
    fun abrade_damageMode() = scenario {
        alice.battlefield("Mountain", "Mountain")
        bob.battlefield(ox, relic)
        alice.hand(abrade)
        alice.casts(abrade, modes = listOf(0), target = ox)
        resolveAll()
        expect {
            bob.graveyard has ox
            bob.battlefield has relic
        }
    }

    // CR 700.2: the second mode destroys an artifact, and the creature is untouched
    @Test
    fun abrade_destroyMode() = scenario {
        alice.battlefield("Mountain", "Mountain")
        bob.battlefield(ox, relic)
        alice.hand(abrade)
        alice.casts(abrade, modes = listOf(1), target = relic)
        resolveAll()
        expect {
            bob.graveyard has relic
            bob.battlefield has ox
        }
    }

    // CR 120.3: the ping reaches each of two opponents; CR 701.26b: casting an instant untaps it for a second ping
    @Test
    fun thermo_pingsEachOpponentAndUntapsOnInstants() = scenario(players = 3) {
        alice.battlefield(thermo, "Mountain")
        alice.hand(bolt)
        alice.activates(thermo)
        resolveAll()
        expect {
            bob.life shouldBe 19
            players[2].life shouldBe 19
            thermo.tapped shouldBe true
        }
        alice.casts(bolt, target = "bob")
        resolveAll()
        expect { thermo.tapped shouldBe false }
        alice.activates(thermo)
        resolveAll()
        expect {
            bob.life shouldBe 17
            players[2].life shouldBe 18
        }
    }

    // CR 120.3, 111.10g: each opponent takes 1, then the Blood token
    @Test
    fun epicure_hitsEveryOpponent() = scenario(players = 3) {
        alice.battlefield("Mountain")
        alice.hand(epicure)
        alice.casts(epicure)
        resolveAll()
        expect {
            bob.life shouldBe 19
            players[2].life shouldBe 19
            alice.life shouldBe 20
            alice.battlefield has "Blood Token"
        }
    }

    // CR 603.7: the creature returns at the next end step; CR 614.6: an opponent's creature is exiled instead of dying; tokens don't return
    @Test
    fun liesa_returnsOwnNontokenAndExilesOpponents() = scenario(players = 3) {
        alice.battlefield(liesa, ox, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        bob.battlefield(cub)
        alice.hand(sweep)
        alice.casts(sweep)
        resolveAll()
        expect {
            alice.graveyard has ox
            bob.graveyard lacks cub
            cub.zone shouldBe Zone.Exile
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.hand has ox
            ox.zone shouldBe Zone.Hand
        }
    }

    // CR 603.7: "another": Liesa herself dying doesn't return her
    @Test
    fun liesa_herselfStaysInTheGraveyard() = scenario {
        alice.battlefield(liesa, "Swamp")
        alice.hand(hex)
        alice.casts(hex, target = liesa)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.graveyard has liesa }
    }
}
