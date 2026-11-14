package arcanum.engine.sets.one

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phyrexia: All Will Be One chunk 11 in play: proliferate (CR 701.34a) after an optional tap, as a loyalty-less life-paid ability and
 * after a spell's other steps; triggers on proliferating (CR 701.34e) from the battlefield and the graveyard, with a mode that
 * creates a token only when no creature of that name is controlled; a mana ability of any color (CR 106.1b).
 */
class OneChunk11ProliferateTwoScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val proliferateText = "(Choose any number of permanents and/or players, then give each another counter of each kind already there.)"

    private val anatomist = Cards.invent("Gilded Anatomist", "{3}{U}", "Creature — Phyrexian Wizard",
        "When this creature enters, you may tap it. If you do, proliferate. $proliferateText", "2/5")
    private val adept = Cards.invent("Scheming Adept", "{1}{B}", "Creature — Phyrexian Advisor",
        "Whenever you proliferate, each opponent loses 2 life and you gain 2 life.", "1/3")
    private val lesson = Cards.invent("Vivisurgeon's Lesson", "{3}{U}{U}", "Sorcery", "Draw three cards. Proliferate. $proliferateText")
    private val hatchling = Cards.invent("Voidwing Hatchling", "{U}{B}", "Creature — Phyrexian Bat", """
        Flying
        Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
        When you proliferate, return this card from your graveyard to your hand.
    """, "2/1")
    private val vesner = Cards.invent("Vesner, Corpse Handler", "{U}{B}", "Legendary Creature — Phyrexian Zombie Wizard", """
        Lifelink, toxic 1
        Whenever you proliferate, choose one —
        • If you don't control a creature named The Hollow Warden, create The Hollow Warden, a legendary 3/3 colorless Phyrexian Golem artifact creature token.
        • Target artifact creature you control gains flying and lifelink until end of turn.
    """, "1/3")
    private val surge = Cards.invent("Volt Surge", "{2}{R}", "Instant", "Volt Surge deals 3 damage to any target. Proliferate. $proliferateText")
    private val staff = Cards.invent("Staff of Fulfillment", "{3}", "Artifact", """
        {T}, Pay 1 life: Destroy target permanent you own.
        {T}, Pay 2 life: Add one mana of any color.
        {T}, Pay 3 life: Proliferate.
        {T}, Pay 4 life: Draw a card.
        {5}: Untap this artifact.
    """)
    private val grunt = Cards.invent("Ichorvat Grunt", "{1}{G}", "Creature — Phyrexian Warrior", "", "2/2")
    private val scout = Cards.invent("Ichorvat Scout", "{1}{G}", "Creature — Phyrexian Scout", "", "2/2")
    private val golem = Cards.invent("Tarnished Automaton", "{3}", "Artifact Creature — Golem", "", "3/3")
    private val greenSpell = Cards.invent("Mossy Boon", "{G}", "Instant", "Put a +1/+1 counter on target creature.")

    private fun Scenario.proliferating(vararg names: String) {
        assertEquals("proliferate", decision<Decision.Targets>().slot)
        targets(*names)
    }

    // CR 701.34a, 118.12: tapping it is the optional action; proliferate happens only if it was done
    @Test
    fun anatomistTapsThenProliferates() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        alice.hand(anatomist)
        alice.casts(anatomist)
        resolveAll()
        answer(Answer.YesNo(true))
        proliferating(grunt)
        resolveAll()
        expect {
            anatomist.tapped shouldBe true
            grunt.counters(p1p1) shouldBe 2
        }
    }

    // CR 701.34a: declining to tap it: it stays untapped and nothing proliferates
    @Test
    fun anatomistDecliningStaysUntappedAndDoesntProliferate() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        alice.hand(anatomist)
        alice.casts(anatomist)
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            anatomist.tapped shouldBe false
            grunt.counters(p1p1) shouldBe 1
        }
    }

    // CR 701.34e, 102.2, 3+ players: each opponent loses 2 life and you gain 2, once per proliferate
    @Test
    fun adeptDrainsEachOpponentWhenYouProliferate() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.battlefield(adept)
        alice.hand(surge)
        bob.battlefield(grunt)
        alice.casts(surge, target = grunt)
        resolveAll()
        // nothing has a counter: proliferate has nothing to choose, the trigger asks for its mode
        expect {
            grunt.zone shouldBe Zone.Graveyard
            alice.life shouldBe 22
            bob.life shouldBe 18
            carol.life shouldBe 18
        }
    }

    // CR 701.34e: only the proliferating player's trigger: an opponent's proliferate doesn't drain
    @Test
    fun adeptIgnoresAnOpponentsProliferate() = scenario {
        alice.battlefield(adept)
        bob.battlefield("Island", "Island", "Island", "Island", "Island")
        bob.hand(lesson)
        bob.library("Island", "Island", "Island")
        turn(number = 2, active = bob)
        bob.casts(lesson)
        resolveAll()
        // nothing has a counter: proliferate has nothing to choose, the trigger asks for its mode
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 20
        }
    }

    // CR 603.6c, 701.34e: the graveyard trigger returns the card when you proliferate
    @Test
    fun hatchlingReturnsFromTheGraveyardWhenYouProliferate() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.graveyard(hatchling)
        alice.hand(lesson)
        alice.library("Island", "Island", "Island")
        alice.casts(lesson)
        resolveAll()
        // nothing has a counter: proliferate has nothing to choose, the trigger asks for its mode
        expect {
            hatchling.zone shouldBe Zone.Hand
            alice.hand.size shouldBe 4
        }
    }

    // CR 700.2b, 111.1, 614.1a: the token is made when you control no creature of that name; the second proliferate
    // finds it and creates none, and the other mode gives the artifact creature flying and lifelink
    @Test
    fun vesnerMakesTheTokenOnceThenGrantsFlyingAndLifelink() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.battlefield(vesner)
        alice.hand(surge, surge2)
        bob.battlefield(grunt, scout)
        alice.casts(surge, target = grunt)
        resolveAll()
        // nothing has a counter: proliferate has nothing to choose, the trigger asks for its mode
        answer(Answer.Modes(listOf(0)))
        resolveAll()
        val first = tokens("The Hollow Warden")
        assertEquals(1, first.size)
        alice.casts(surge2, target = scout)
        resolveAll()
        // nothing has a counter: proliferate has nothing to choose, the trigger asks for its mode
        answer(Answer.Modes(listOf(0)))
        resolveAll()
        assertEquals(1, tokens("The Hollow Warden").size, "already controls The Hollow Warden: no second token")
    }

    // CR 700.2, 611.2: the other mode gives an artifact creature flying and lifelink
    @Test
    fun vesnerGrantsFlyingAndLifelinkToAnArtifactCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.battlefield(vesner, golem)
        alice.hand(surge)
        bob.battlefield(grunt)
        alice.casts(surge, target = grunt)
        resolveAll()
        // nothing has a counter: proliferate has nothing to choose, the trigger asks for its mode
        answer(Answer.Modes(listOf(1)))
        resolveAll()
        expect {
            golem hasKeyword Keyword.Flying.kind
            golem hasKeyword Keyword.Lifelink.kind
        }
    }

    // CR 106.1b: "one mana of any color": all five colors are offered; the paid {G} spell resolves
    @Test
    fun staffAddsOneManaOfAnyColorForALife() = scenario {
        alice.battlefield(staff)
        alice.battlefield(grunt)
        alice.hand(greenSpell)
        alice.activates(staff, ability = 1)
        val offered = (pending as Decision.ChooseColor).among
        assertEquals(setOf(Color.White, Color.Blue, Color.Black, Color.Red, Color.Green), offered.toSet())
        answer(Answer.ColorChoice(Color.Green))
        resolveAll()
        alice.casts(greenSpell, target = grunt)
        resolveAll()
        expect {
            alice.life shouldBe 18
            staff.tapped shouldBe true
            grunt.counters(p1p1) shouldBe 1
        }
    }

    // CR 701.34a, 118.4: paying 3 life as a cost proliferates; the permanent tapped as part of the cost
    @Test
    fun staffPaysThreeLifeToProliferate() = scenario {
        alice.battlefield(staff)
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        alice.activates(staff, ability = 2)
        resolveAll()
        proliferating(grunt)
        resolveAll()
        expect {
            alice.life shouldBe 17
            grunt.counters(p1p1) shouldBe 2
        }
    }

    // CR 701.26a: {5} untaps it, after it was tapped for its life-paying draw
    @Test
    fun staffUntapsForFiveMana() = scenario {
        alice.battlefield(staff, "Island", "Island", "Island", "Island", "Island")
        alice.library("Island", "Island")
        alice.activates(staff, ability = 3)
        resolveAll()
        expect { staff.tapped shouldBe true; alice.life shouldBe 16; alice.hand.size shouldBe 1 }
        alice.activates(staff, ability = 4)
        resolveAll()
        expect { staff.tapped shouldBe false }
    }

    private val surge2 = Cards.invent("Volt Surge Again", "{2}{R}", "Instant", "Volt Surge Again deals 3 damage to any target. Proliferate. $proliferateText")
}
