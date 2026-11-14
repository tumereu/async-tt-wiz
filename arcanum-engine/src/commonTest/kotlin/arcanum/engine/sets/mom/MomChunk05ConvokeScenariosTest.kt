package arcanum.engine.sets.mom

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.sets.ltr.permitted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** MOM chunk 05: convoke: counters for each creature that convoked a permanent, a flying counter on each of them and a look-and-reveal whose X
 * is their number; tripled damage; sorceries cast as though they had flash and the next one copied twice; a planeswalker target only once
 * activated this turn; a tutored card played from exile face down with convoke; stun counters on either of two targets; a permanent put second
 * from the top. */
class MomChunk05ConvokeScenariosTest {
    private val convoke = "Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)"
    private val saurian = Cards.invent("Thronging Saurian", "{5}{G}{G}", "Creature — Dinosaur", """
        $convoke
        Trample, ward {2}
        This creature enters with two +1/+1 counters on it for each creature that convoked it.
    """.trimIndent(), "6/6")
    private val city = Cards.invent("Metropolis Ablaze", "{5}{R}{R}{R}", "Enchantment", """
        $convoke
        If a source you control would deal damage to a permanent or player, it deals triple that damage instead.
    """.trimIndent())
    private val circuit = Cards.invent("Closed Loop Surge", "{5}{U}", "Instant", """
        $convoke
        You may cast sorcery spells this turn as though they had flash.
        When you next cast an instant or sorcery spell this turn, copy that spell twice. You may choose new targets for the copies.
    """.trimIndent())
    private val curtail = Cards.invent("Abrupt Curtail", "{2}{W}", "Instant", """
        $convoke
        Destroy target planeswalker that was activated this turn or tapped creature.
    """.trimIndent())
    private val hoardmother = Cards.invent("Brood Hoardmother", "{5}{B}{B}{B}", "Creature — Dragon", """
        Convoke
        Flying
        When this creature enters, search your library for a card, exile it face down, then shuffle. For as long as that card remains exiled, you may play it.
        Spells you cast from exile have convoke.
    """.trimIndent(), "7/6")
    private val sunlance = Cards.invent("Errant Sunlance", "{4}{W}", "Creature — Human Knight", """
        Convoke
        When this creature enters, look at the top six cards of your library. You may reveal up to two creature cards with mana value X or less from among them, where X is the number of creatures that convoked this creature. Put the revealed cards into your hand, then shuffle.
    """.trimIndent(), "4/4")
    private val doze = Cards.invent("Wicked Doze", "{3}{U}", "Instant", """
        $convoke
        Tap up to two target creatures. Put a stun counter on either of them. Then put a stun counter on either of them. (If a permanent with a stun counter would become untapped, remove one from it instead.)
    """.trimIndent())
    private val chanteuse = Cards.invent("Gale Chanteuse", "{2}{U}{U}", "Creature — Siren Pirate", """
        $convoke
        Flying, vigilance
        When this creature enters, put a flying counter on each creature that convoked it.
    """.trimIndent(), "3/4")
    private val scour = Cards.invent("Chrono Scour", "{3}{U}", "Sorcery", """
        $convoke
        The owner of target nonland permanent puts it into their library second from the top or on the bottom.
    """.trimIndent())

    private val page = Cards.invent("Musterfield Page", "{W}", "Creature — Human Soldier", "", "1/1")
    private val scout = Cards.invent("Musterfield Scout", "{G}", "Creature — Elf Scout", "", "1/1")
    private val adept = Cards.invent("Musterfield Adept", "{U}", "Creature — Human Wizard", "", "1/1")
    private val ghoul = Cards.invent("Musterfield Ghoul", "{1}{B}", "Creature — Zombie", "", "2/2")
    private val ogre = Cards.invent("Musterfield Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val sage = Cards.invent("Musterfield Sage", "{1}{W}", "Creature — Human Cleric", "", "2/2")
    private val knight = Cards.invent("Musterfield Knight", "{2}{W}", "Creature — Human Knight", "", "3/3")
    private val jab = Cards.invent("Musterfield Jab", "{R}", "Instant", "Musterfield Jab deals 2 damage to any target.")
    private val lash = Cards.invent("Musterfield Lash", "{R}", "Sorcery", "Musterfield Lash deals 1 damage to any target.")
    private val oracle = Cards.invent("Musterfield Oracle", "{2}{W}", "Legendary Planeswalker — Ajani", "[+1]: You gain 1 life.", loyalty = "3")

    private val p1p1 = CounterKind.PowerToughness(1, 1)

    // CR 702.51c: two creatures convoked it, so it enters with four +1/+1 counters (CR 614.1c)
    @Test
    fun imperiosaurEntersWithTwoCountersForEachConvoker() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", scout, page)
        alice.hand(saurian)
        alice.casts(saurian, paying = listOf("Forest", "Forest", "Forest", "Forest", "Forest", scout, page))
        resolveAll()
        expect {
            saurian.counters(p1p1) shouldBe 4
            saurian.power shouldBe 10
            scout.tapped shouldBe true
            page.tapped shouldBe true
        }
    }

    // CR 702.51c: cast with mana alone, nothing convoked it
    @Test
    fun imperiosaurWithoutConvokeHasNoCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", scout)
        alice.hand(saurian)
        alice.casts(saurian, paying = listOf("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest"))
        resolveAll()
        expect {
            saurian.counters(p1p1) shouldBe 0
            scout.tapped shouldBe false
        }
    }

    // CR 614.1a: 2 damage from a source Alice controls becomes 6; Bob's source isn't hers (CR 109.4)
    @Test
    fun cityOnFireTriplesDamageFromYourSources() = scenario {
        alice.battlefield(city, "Mountain")
        bob.battlefield("Mountain")
        alice.hand(jab)
        bob.hand(jab)
        alice.casts(jab, target = "bob")
        resolveAll()
        alice.passes()
        bob.casts(jab, target = "alice")
        resolveAll()
        expect {
            assertEquals(14, bob.life)
            assertEquals(18, alice.life)
        }
    }

    // CR 702.8a: a sorcery cast during Bob's turn; CR 603.7b, 707.10: the next spell is copied twice, then the delayed trigger is gone
    @Test
    fun completeTheCircuitFlashesASorceryAndCopiesItTwice() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Mountain", "Mountain")
        alice.hand(circuit, lash, lash)
        turn(number = 2, active = bob, priority = alice)
        alice.casts(circuit, paying = listOf("Island", "Island", "Island", "Island", "Island", "Island"))
        resolveAll()
        // CR 117.3b: after a resolution the active player gets priority
        bob.passes()
        alice.casts(lash, target = "bob", paying = listOf("Mountain"))
        resolveAll()
        while (pending is Decision.May) { alice.answers(Answer.YesNo(false)); resolveAll() }
        expect { assertEquals(17, bob.life) }
        bob.passes()
        alice.casts(lash, target = "bob", paying = listOf("Mountain#1"))
        resolveAll()
        expect { assertEquals(16, bob.life) }
    }

    // CR 606.3: a planeswalker whose loyalty ability was activated this turn is a legal target, and is destroyed (CR 701.8a)
    @Test
    fun cutShortDestroysAnActivatedPlaneswalker() = scenario {
        bob.battlefield(oracle, counters = listOf(CounterKind.Loyalty to 3))
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(curtail)
        turn(number = 2, active = bob)
        bob.activates(oracle)
        resolveAll()
        bob.passes()
        alice.casts(curtail, target = oracle)
        resolveAll()
        expect { bob.graveyard has oracle }
    }

    // CR 115.1, 601.2c: an untapped creature and a planeswalker not activated this turn aren't legal targets
    @Test
    fun cutShortCantTargetAnUnactivatedPlaneswalker() = scenario {
        bob.battlefield(oracle, counters = listOf(CounterKind.Loyalty to 3))
        bob.battlefield(ogre)
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(curtail)
        assertFailsWith<IllegalArgumentException> { alice.casts(curtail, target = oracle) }
    }

    // CR 701.23a, 406.3: the card found is exiled face down and Alice may cast it (CR 601.2a); cast from exile it has convoke, so two
    // creatures pay all of it (CR 702.51a)
    @Test
    fun hoardingBroodlordPlaysTheExiledCardWithConvoke() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", page)
        alice.hand(hoardmother)
        alice.library("Plains", ghoul, "Plains")
        alice.casts(hoardmother, paying = listOf("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp"))
        resolveAll()
        alice.chooses(ghoul)
        resolveAll()
        expect { alice.exile has ghoul }
        assertTrue(state.obj(card(ghoul))!!.faceDown)
        alice.casts(ghoul, way = permitted(ghoul), paying = listOf(hoardmother, page))
        resolveAll()
        expect {
            alice.battlefield has ghoul
            hoardmother.tapped shouldBe true
        }
    }

    // CR 702.51c, 608.2h: two creatures convoked it, so X is 2: the creature cards of mana value 1 and 2 go to hand, the one of 3 can't
    @Test
    fun knightErrantRevealsCreaturesUpToTheConvokeCount() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", page, scout)
        alice.hand(sunlance)
        alice.library(knight, sage, adept, "Island", "Island", "Island", "Island")
        alice.casts(sunlance, paying = listOf("Plains", "Plains", "Plains", page, scout))
        resolveAll()
        val choice = pending as Decision.ChooseObjects
        assertTrue(choice.options.none { it == card(knight) })
        alice.chooses(sage, adept)
        resolveAll()
        expect {
            alice.hand has sage
            alice.hand has adept
            alice.library has knight
        }
    }

    // CR 122.1d, 608.2c: both stun counters may go on the same target
    @Test
    fun wickedSlumberPutsBothStunCountersOnOneCreature() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        bob.battlefield(ogre, sage)
        alice.hand(doze)
        alice.casts(doze, targets = listOf(ogre, sage))
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            ogre.counters(CounterKind.Stun) shouldBe 2
            sage.counters(CounterKind.Stun) shouldBe 0
            ogre.tapped shouldBe true
            sage.tapped shouldBe true
        }
    }

    // CR 702.51c, 122.1b: the two creatures that convoked it get a flying counter, the third creature doesn't
    @Test
    fun zephyrSingerGivesFlyingCountersToItsConvokers() = scenario {
        alice.battlefield("Island", "Island", page, scout, adept)
        alice.hand(chanteuse)
        alice.casts(chanteuse, paying = listOf("Island", "Island", page, scout))
        resolveAll()
        expect {
            page.counters(CounterKind.Keyword(Keyword.Flying.kind)) shouldBe 1
            page hasKeyword Keyword.Flying.kind
            scout hasKeyword Keyword.Flying.kind
            adept lacksKeyword Keyword.Flying.kind
        }
    }

    // CR 401.4: "second from the top" puts it under exactly one card (the audit's question: position 1 counted from the top is right)
    @Test
    fun temporalCleansingPutsItSecondFromTheTop() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        bob.battlefield(ogre)
        bob.library("Mountain", "Forest", "Swamp")
        alice.hand(scour)
        alice.casts(scour, target = ogre)
        resolveAll()
        bob.answers(Answer.Option("second from the top"))
        resolveAll()
        expect { assertEquals(listOf("Mountain", ogre, "Forest", "Swamp"), bob.library.names) }
    }
}
