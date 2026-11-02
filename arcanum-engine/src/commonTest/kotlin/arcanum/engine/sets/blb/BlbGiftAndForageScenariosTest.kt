package arcanum.engine.sets.blb

import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** Bloomburrow's gift (CR 702.174) and forage (CR 701.61), played with invented cards of the parser's chunks 02 and 03. */
class BlbGiftAndForageScenariosTest {
    private val craving = Cards.invent("Midnight Craving", "{2}{B}", "Instant", """
        Gift a Food (You may promise an opponent a gift as you cast this spell. If you do, they create a Food token before its other effects. It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        Destroy target creature. If the gift wasn't promised, you lose 2 life.
    """)
    private val whirlpool = Cards.invent("Down the Whirlpool", "{U}", "Instant", """
        Gift a tapped Fish (You may promise an opponent a gift as you cast this spell. If you do, they create a tapped 1/1 blue Fish creature token before its other effects.)
        Return target creature an opponent controls to its owner's hand. If the gift was promised, instead return target nonland permanent an opponent controls to its owner's hand.
    """)
    private val slinger = Cards.invent("Junkyard Slinger", "{1}{G}{G}", "Creature — Raccoon Archer", """
        Gift a card (You may promise an opponent a gift as you cast this spell. If you do, when it enters, they draw a card.)
        Reach
        When this creature enters, if the gift was promised, destroy target artifact or enchantment an opponent controls.
    """, "4/4")
    private val hamsters = Cards.invent("Merry Hamsters", "{1}{W}", "Creature — Hamster Citizen", "Whenever you give a gift, draw a card.", "2/3")
    private val nourish = Cards.invent("Nourish the Loop", "{1}{B}", "Instant", """
        As an additional cost to cast this spell, forage or pay {B}. (To forage, exile three cards from your graveyard or sacrifice a Food.)
        Destroy target creature or planeswalker.
    """)
    private val lookouts = Cards.invent("Canopy Lookouts", "{3}{G}", "Creature — Squirrel Archer", """
        Reach
        When this creature enters, you may forage. If you do, draw a card. (To forage, exile three cards from your graveyard or sacrifice a Food.)
    """, "2/4")
    private val tender = Cards.invent("Gravefruit Tender", "{1}{B/G}{B/G}", "Creature — Squirrel Warlock", """
        At the beginning of combat on your turn, you may forage. (Exile three cards from your graveyard or sacrifice a Food.)
        Whenever you forage, put a +1/+1 counter on this creature.
    """, "2/3")
    private val bonegrafter = Cards.invent("Bonegrafter Apprentice", "{1}{B}", "Creature — Squirrel Warlock", """
        Deathtouch
        {T}: Until end of turn, you may cast creature spells from your graveyard by foraging in addition to paying their other costs. If you cast a spell this way, that creature enters with a finality counter on it. (To forage, exile three cards from your graveyard or sacrifice a Food. If a creature with a finality counter on it would die, exile it instead.)
    """, "2/2")
    private val bear = Cards.invent("Mossback Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val idol = Cards.invent("Brass Idol", "{2}", "Artifact")
    private val food = Cards.invent("Pantry Pie", "{1}", "Artifact — Food", "{2}, {T}, Sacrifice this artifact: You gain 3 life.")

    private fun Scenario.yes() = answer(Answer.YesNo(true))

    // ---- Gift

    /** Promised: the opponent gets the Food before the creature is destroyed, and the caster loses no life. */
    // CR 702.174a, j: the gift is an optional additional cost; its effect happens before the spell's other effects
    @Test
    fun giftPromisedComesFirst() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(craving)
        bob.battlefield(bear)

        alice.casts(craving, target = bear, kicked = 1)
        allPass()
        expect {
            bob.battlefield.names shouldBe listOf("Food Token")
            bob.graveyard has bear
            alice.life shouldBe 20
        }
    }

    /** Not promised: no gift, and the "if the gift wasn't promised" part happens. */
    // CR 702.174k
    @Test
    fun giftNotPromised() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(craving)
        bob.battlefield(bear)

        alice.casts(craving, target = bear, kicked = 0)
        allPass()
        expect {
            bob.battlefield.isEmpty()
            alice.life shouldBe 18
        }
    }

    /** Promised, the spell has only the promised version's target: a nonland permanent, here an artifact. */
    // CR 702.174m: targets of a part that happens only if the gift was promised are chosen only then
    @Test
    fun giftInsteadHasItsOwnTarget() = scenario {
        alice.battlefield("Island")
        alice.hand(whirlpool)
        bob.battlefield(idol, bear)

        alice.casts(whirlpool, target = idol, kicked = 1)
        allPass()
        expect {
            bob.hand.names shouldBe listOf(idol)
            bob.battlefield.names shouldBe listOf(bear, "Fish Token")
            card("Fish Token").tapped shouldBe true
        }
    }

    /** Not promised, the artifact isn't a legal target: only a creature is. */
    // CR 601.2c, 702.174m
    @Test
    fun giftNotPromisedTargetsTheOtherVersion() = scenario {
        alice.battlefield("Island")
        alice.hand(whirlpool)
        bob.battlefield(idol, bear)

        alice.casts(whirlpool, kicked = 0)
        val d = decision<Decision.Targets>()
        assertEquals(listOf(target(bear)), d.options)
        answer(Answer.Targets(listOf(target(bear))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        expect { bob.hand.names shouldBe listOf(bear) }
    }

    /** A permanent's gift is an enters trigger; with "whenever you give a gift", the giver draws too. */
    // CR 702.174b, c
    @Test
    fun giftOfAPermanent() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", hamsters)
        alice.hand(slinger)
        alice.library("Island")
        bob.battlefield(idol)
        bob.library("Plains")

        alice.casts(slinger, kicked = 1)
        allPass()
        // Both enters triggers wait: the gift and the destruction; alice orders them
        if (pending is Decision.OrderTriggers) answer(Answer.Order(decision<Decision.OrderTriggers>().triggers.map { t -> t.index }))
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(idol))))
        allPass()
        allPass()
        allPass()
        expect {
            bob.hand.names shouldBe listOf("Plains")
            bob.graveyard has idol
            alice.hand.names shouldBe listOf("Island")
        }
    }

    /** "Whenever you give a gift" triggers as a promised gift spell resolves, not when it isn't promised. */
    // CR 702.174c
    @Test
    fun givingAGiftSpell() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", hamsters)
        alice.hand(craving, craving)
        alice.library("Island", "Island")
        bob.battlefield(bear, "Mossback Cub")

        alice.casts(craving, target = bear, kicked = 0)
        allPass()
        expect { stack.isEmpty() }
        alice.casts(craving, target = bear, kicked = 1)
        allPass()
        expect { stack.size shouldBe 1 }
        allPass()
        expect { alice.hand.names shouldBe listOf("Island") }
    }

    // ---- Forage

    /** Foraging as an additional cost: the three graveyard cards are exiled; "whenever you forage" triggers. */
    // CR 701.61a, 601.2f
    @Test
    fun forageAsAdditionalCost() = scenario {
        alice.battlefield("Swamp", "Swamp", tender)
        alice.graveyard("Island", "Island", "Island")
        alice.hand(nourish)
        bob.battlefield(bear)

        alice.casts(nourish, target = bear, option = "Forage")
        expect {
            alice.exile.size shouldBe 3
            stack.size shouldBe 2
        }
        allPass()
        expect { card(tender).counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
        allPass()
        expect { bob.graveyard has bear }
    }

    /** "You may forage. If you do": with only a Food, it is sacrificed. */
    // CR 118.12, 701.61a
    @Test
    fun mayForageSacrificingAFood() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", food)
        alice.hand(lookouts)
        alice.library("Island")

        alice.casts(lookouts)
        allPass()
        allPass()
        yes()
        expect {
            alice.graveyard has food
            alice.hand.names shouldBe listOf("Island")
        }
    }

    /** Can't forage (no Food, too few cards in the graveyard): nothing happens. */
    // CR 118.3
    @Test
    fun cantForage() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.graveyard("Island")
        alice.hand(lookouts)
        alice.library("Island")

        alice.casts(lookouts)
        allPass()
        allPass()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        expect {
            alice.hand.isEmpty()
            alice.graveyard.names shouldBe listOf("Island")
        }
    }

    /** A creature cast from the graveyard by foraging enters with a finality counter. */
    // CR 601.2f, 400.7d, 122.1h
    @Test
    fun castFromGraveyardByForaging() = scenario {
        alice.battlefield("Forest", "Forest", bonegrafter, food)
        alice.graveyard(bear)

        alice.activates(bonegrafter)
        allPass()
        alice.casts(bear)
        expect { alice.graveyard has food }
        allPass()
        expect {
            alice.battlefield has bear
            card(bear).counters(CounterKind.Finality) shouldBe 1
        }
        assertEquals(Zone.Battlefield, state.objOrThrow(card(bear)).zone)
    }
}
