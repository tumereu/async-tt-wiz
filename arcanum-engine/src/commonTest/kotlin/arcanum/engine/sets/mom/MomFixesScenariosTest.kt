package arcanum.engine.sets.mom

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Share
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** MOM fix chunk: rules fixes found while working the set. */
class MomFixesScenariosTest {
    private val renderLifeless = Cards.invent("Render Lifeless", "{2}{B}", "Sorcery", """
        Remove up to five counters from target permanent.
        Draw a card.
    """.trimIndent())
    private val drudge = Cards.invent("Momentide Drudge", "{2}{G}", "Creature — Zombie", "", "1/1")
    private val exhumer = Cards.invent("Momentide Exhumer", "{3}{B}", "Creature — Zombie",
        "When this creature enters, put up to two creature cards from your graveyard on top of your library.", "3/3")
    private val bear = Cards.invent("Momentide Grave Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    private val prism = Cards.inventFaces("Transform",
        OracleCard("Invasion of Momentide Prism", "{W}{U}{B}{R}{G}", "Battle — Siege",
            "When this Siege enters, exile cards from the top of your library until you exile two nonland cards with mana value 4 or less. You may cast one of those two cards without paying its mana cost. Put one of them into your hand. Then put the other cards exiled this way on the bottom of your library in a random order.",
            defense = "7"),
        OracleCard("Awaken the Momentide", null, "Sorcery", """
            Awaken the Momentide is all colors.
            Target player draws two cards. You may put an artifact card from your hand onto the battlefield. Create a token that's a copy of a permanent you control. Distribute three +1/+1 counters among one, two, or three creatures you control. Destroy target permanent an opponent controls.
        """.trimIndent()))
    private val shardBear = Cards.invent("Prism Shard Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val heavyOx = Cards.invent("Prism Heavy Ox", "{4}{G}", "Creature — Ox", "", "5/5")
    private val scout = Cards.invent("Prism Scout", "{2}{W}", "Creature — Human Scout", "", "2/2")
    private val trinket = Cards.invent("Prism Trinket", "{1}", "Artifact", "")
    private val charger = Cards.invent("Prism Charger", "{2}{G}", "Creature — Beast", "", "4/4")
    private val victim = Cards.invent("Prism Victim", "{1}{W}", "Creature — Human", "", "2/2")

    // CR 608.2d: "remove up to N counters" names no kind: as it resolves, its controller chooses which counters (here the stun
    // counter and one of the three +1/+1 counters added before it), not the first ones added
    @Test
    fun removingCountersOfAnyKindAsksWhichCounters() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(renderLifeless)
        alice.library("Swamp")
        bob.battlefield(drudge, counters = listOf(CounterKind.PowerToughness(1, 1) to 3, CounterKind.Stun to 1))
        alice.casts(renderLifeless, target = drudge)
        resolveAll()
        answer(Answer.Number(2))
        val choice = pending as Decision.ChooseOption
        assertEquals(alice.id, choice.player)
        assertEquals(setOf("+1/+1", "stun"), choice.options.toSet())
        answer(Answer.Option("stun"))
        // only +1/+1 counters left: the second needs no question
        resolveAll()
        expect {
            drudge.counters(CounterKind.Stun) shouldBe 0
            drudge.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            alice.hand.size shouldBe 1
        }
    }

    // CR 609.3: with every counter going, nothing is left to choose
    @Test
    fun removingAtLeastAllCountersAsksNothing() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(renderLifeless)
        alice.library("Swamp")
        bob.battlefield(drudge, counters = listOf(CounterKind.PowerToughness(1, 1) to 1, CounterKind.Stun to 1))
        alice.casts(renderLifeless, target = drudge)
        resolveAll()
        answer(Answer.Number(5))
        resolveAll()
        expect {
            drudge.counters(CounterKind.Stun) shouldBe 0
            drudge.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 608.2c: two cards chosen as it resolves; CR 401.4: put on top at once, their owner orders them (first: topmost)
    @Test
    fun twoCardsPutOnTopAreOrderedByTheirOwner() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(exhumer)
        alice.graveyard(drudge, bear)
        alice.library("Swamp")
        alice.casts(exhumer)
        resolveAll()
        alice.chooses(drudge, bear)
        val order = pending as Decision.ChooseObjects
        assertEquals(alice.id, order.player)
        answer(Answer.Objects(listOf(card(bear), card(drudge))))
        resolveAll()
        assertEquals(listOf(card(bear), card(drudge)), state.player(alice.id).library.take(2))
    }

    // CR 701.20a-b: exiled until two nonland cards with mana value 4 or less (the 5-mana Ox doesn't count); CR 608.2g: one is cast while it
    // resolves without paying; the other goes to the hand; CR 401.4: the rest to the bottom
    @Test
    fun theInvasionCastsOneFoundCardAndKeepsTheOther() = scenario {
        alice.battlefield("Plains", "Island", "Swamp", "Mountain", "Forest")
        alice.library("Plains", shardBear, heavyOx, "Island", scout, trinket)
        bob.library("Plains")
        alice.hand(prism)
        alice.casts(prism)
        resolveAll()
        alice.chooses(shardBear)
        resolveAll()
        expect {
            shardBear.zone shouldBe Zone.Battlefield
            scout.zone shouldBe Zone.Hand
            alice.exile.size shouldBe 0
        }
        // the Ox and the two lands turned over go below the Trinket that wasn't reached
        val library = state.player(alice.id).library
        assertEquals(listOf(card(trinket)), library.take(1))
        assertEquals(4, library.size)
        assertEquals(Zone.Library, state.objOrThrow(card(heavyOx)).zone)
    }

    // CR 608.2c: casting neither, one of the two is put into the hand and the other goes to the bottom with the rest
    @Test
    fun theInvasionCastingNothingKeepsOneAndBottomsTheOther() = scenario {
        alice.battlefield("Plains", "Island", "Swamp", "Mountain", "Forest")
        alice.library("Plains", shardBear, scout, trinket)
        bob.library("Plains")
        alice.hand(prism)
        alice.casts(prism)
        resolveAll()
        alice.chooses()
        alice.chooses(scout)
        resolveAll()
        expect {
            scout.zone shouldBe Zone.Hand
            shardBear.zone shouldBe Zone.Library
            alice.exile.size shouldBe 0
        }
        assertEquals(listOf(card(trinket)), state.player(alice.id).library.take(1))
    }

    // CR 310.12b, 712.11a: the defeated Siege is cast transformed; CR 604.3, 105.2: the spell is all colours; CR 608.2c-d: the copied
    // permanent and the creatures sharing the counters are chosen as it resolves; CR 707.2: the token copies the Charger
    @Test
    fun awakenTheMomentideDoesEverythingInOrder() = scenario {
        alice.battlefield(prism, counters = listOf(CounterKind.Defense to 2))
        alice.battlefield(charger)
        alice.hand(trinket)
        bob.battlefield(victim)
        alice.library("Plains", "Plains", "Plains")
        bob.library("Plains", "Plains")
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(charger), TargetRef.Object(card(prism))))))
        advanceTo(TurnStep.CombatDamage)
        allPass()
        answer(Answer.YesNo(true))
        answer(Answer.Targets(listOf(TargetRef.Player(alice.id))))
        // the Victim, Bob's only permanent, is the lone legal target of the second slot
        val spell = state.stack.last()
        assertEquals(Color.all.toSet(), view()[spell]!!.characteristics!!.colors.toSet())
        allPass()
        alice.chooses(trinket)
        alice.chooses(charger)
        val token = state.battlefield.single { it != card(charger) && view()[it]?.characteristics?.name == charger }
        answer(Answer.Objects(listOf(card(charger), token)))
        answer(Answer.Division(listOf(Share(TargetRef.Object(card(charger)), 2), Share(TargetRef.Object(token), 1))))
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            trinket.zone shouldBe Zone.Battlefield
            victim.zone shouldBe Zone.Graveyard
            charger.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
        }
        assertEquals(1, state.objOrThrow(token).counters(CounterKind.PowerToughness(1, 1)))
    }
}
