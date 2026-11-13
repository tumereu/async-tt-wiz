package arcanum.engine.sets.mom

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import kotlin.test.Test
import kotlin.test.assertEquals

/** MOM chunk 10: a modal combat damage trigger whose modes may all be chosen (each player's Treasure, each player's sacrifice, +2 damage to
 * players and battles this turn), a granted Treasure trigger on a stolen creature, an attack trigger that counters a creature attacking a
 * battle instead of pumping it, indestructible after attacking a battle, and an ability that weakens a battle an opponent protects or
 * strengthens one you protect. */
class MomChunk10SurveilTreasureAndConditionsScenariosTest {
    private val siege = Cards.inventFaces("Transform",
        OracleCard("Siege of the Chronicle Gate", "{2}{R}", "Battle — Siege", "", defense = "3"),
        OracleCard("Chronicle Gate Keeper", null, "Creature — Elemental", "", power = "4", toughness = "4", colorIndicator = "R"),
    )
    private val grisk = Cards.invent("Grisk and Volmar", "{1}{B}{B}{R}{R}", "Legendary Creature — Faerie Dwarf", """
        Flying, first strike, haste
        Whenever Grisk and Volmar deals combat damage to a player or battle, choose any number —
        • Each player creates a Treasure token.
        • Each player sacrifices a creature of their choice.
        • If a source would deal damage to a player or battle this turn, it deals that much damage plus 2 instead.
    """.trimIndent(), "3/4")
    private val bridle = Cards.invent("Forge Bridle", "{2}{R}", "Sorcery",
        "Gain control of target creature until end of turn. Untap that creature. Until end of turn, it gains haste and \"Whenever this creature deals combat damage to a player or battle, create a Treasure token.\" (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")")
    private val terrashell = Cards.invent("Stampeding Terrashell", "{2}{R}{G}", "Creature — Dinosaur Beast", """
        Trample, haste
        Whenever you attack, target attacking creature gets +1/+1 until end of turn. If it's attacking a battle, put a +1/+1 counter on it instead.
    """.trimIndent(), "3/3")
    private val chronicler = Cards.invent("Battle Chronicler", "{2}{G}", "Creature — Human Monk", """
        Reach
        This creature has indestructible as long as it attacked a battle this turn.
    """.trimIndent(), "3/3")
    private val stalker = Cards.invent("Omen Stalker", "{1}{G}", "Creature — Satyr Scout", """
        {T}: Untap target land.
        {T}: Choose target battle. If an opponent protects it, remove a defense counter from it. Otherwise, put a defense counter on it. Activate only as a sorcery.
    """.trimIndent(), "1/1")
    private val spark = Cards.invent("Cinder Flick", "{R}", "Instant", "This spell deals 1 damage to any target.")
    private val ox = Cards.invent("Chronicle Plain Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val bear = Cards.invent("Chronicle Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val boar = Cards.invent("Chronicle Plain Boar", "{1}{G}", "Creature — Boar", "", "2/2")

    private val defense = listOf(CounterKind.Defense to 3)
    private fun Scenario.libraries() = players.forEach { it.library("Island", "Island", "Island") }

    // CR 700.2b, 700.2d: every mode chosen; CR 111.10a: each player creates a Treasure; CR 701.21a, 101.4: each player sacrifices a creature of
    // their choice; CR 614.1a: afterwards this turn, damage to a player is 2 more
    @Test
    fun griskAndVolmarChoosesEveryMode() = scenario(players = 3) {
        alice.battlefield(grisk, bear, "Mountain")
        bob.battlefield(ox)
        carol.battlefield(boar)
        alice.hand(spark)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(grisk, defender = "bob")
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        val choice = decision<Decision.Modes>()
        assertEquals(0, choice.min)
        assertEquals(3, choice.max)
        answer(Answer.Modes(listOf(0, 1, 2)))
        resolveAll()
        // alice chooses which of her two creatures to sacrifice; bob and carol have one each
        answer(Answer.Objects(listOf(card(bear))))
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.battlefield has grisk
            alice.graveyard has bear
            bob.graveyard has ox
            carol.graveyard has boar
            alice.battlefield has "Treasure Token"
            bob.battlefield has "Treasure Token"
            carol.battlefield has "Treasure Token"
        }
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(spark, target = "carol", paying = listOf("Mountain"))
        resolveAll()
        expect { carol.life shouldBe 17 }
    }

    // CR 700.2b: with no mode chosen, nothing happens
    @Test
    fun griskAndVolmarMayChooseNoMode() = scenario {
        alice.battlefield(grisk)
        bob.battlefield(ox)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(grisk)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        modes()
        resolveAll()
        expect {
            bob.life shouldBe 17
            bob.battlefield has ox
            alice.battlefield lacks "Treasure Token"
        }
    }

    // CR 613.1b, 113.2c: the stolen creature has the granted trigger; it creates the Treasure for its controller, alice
    @Test
    fun forgeBridleStealsAnAttackerThatMakesTreasure() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        bob.battlefield(ox, tapped = true)
        alice.hand(bridle)
        libraries()
        alice.casts(bridle, target = ox)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.battlefield has "Treasure Token"
            bob.battlefield lacks "Treasure Token"
        }
    }

    private fun Scenario.attack(vararg attacks: Pair<String, TargetRef>) =
        perform(Input.DeclareAttackers(alice.id, attacks.map { (name, target) -> Attack(card(name), target) }))

    // CR 506.3: the target attacks a battle as the trigger resolves, so it gets a +1/+1 counter instead of +1/+1 until end of turn
    @Test
    fun terrashellCountersACreatureAttackingABattle() = scenario {
        alice.battlefield(terrashell, ox)
        alice.battlefield(siege, counters = defense)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        attack(terrashell to TargetRef.Player(bob.id), ox to TargetRef.Object(card(siege)))
        resolveAll()
        targets(ox)
        resolveAll()
        expect {
            ox.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            ox.power shouldBe 4
            terrashell.power shouldBe 3
        }
    }

    // CR 506.3, 611.2a: a creature attacking a player gets +1/+1 until end of turn and no counter
    @Test
    fun terrashellPumpsACreatureAttackingAPlayer() = scenario {
        alice.battlefield(terrashell, ox)
        alice.battlefield(siege, counters = defense)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        attack(terrashell to TargetRef.Player(bob.id), ox to TargetRef.Object(card(siege)))
        resolveAll()
        targets(terrashell)
        resolveAll()
        expect {
            terrashell.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            terrashell.power shouldBe 4
            ox.power shouldBe 3
        }
    }

    // CR 702.12, 506.3: indestructible for the rest of the turn once it attacked a battle, not after attacking a player
    @Test
    fun chroniclerIsIndestructibleAfterAttackingABattle() = scenario {
        alice.battlefield(chronicler, ox)
        alice.battlefield(siege, counters = listOf(CounterKind.Defense to 5))
        libraries()
        expect { chronicler lacksKeyword Keyword.Indestructible.kind }
        advanceTo(TurnStep.DeclareAttackers)
        attack(chronicler to TargetRef.Object(card(siege)), ox to TargetRef.Player(bob.id))
        resolveAll()
        expect {
            chronicler hasKeyword Keyword.Indestructible.kind
        }
        advanceTo(TurnStep.PostcombatMain)
        expect {
            chronicler hasKeyword Keyword.Indestructible.kind
            ox lacksKeyword Keyword.Indestructible.kind
        }
    }

    // CR 310.8, 310.4: alice's Siege is protected by bob, an opponent: a defense counter is removed
    @Test
    fun stalkerWeakensABattleAnOpponentProtects() = scenario {
        alice.battlefield(stalker)
        alice.battlefield(siege, counters = defense)
        settle()
        alice.activates(stalker, ability = 1, target = siege)
        resolveAll()
        expect { siege.counters(CounterKind.Defense) shouldBe 2 }
    }

    // CR 310.8: bob's Siege is protected by alice, not an opponent of hers: a defense counter is put on it
    @Test
    fun stalkerStrengthensABattleYouProtect() = scenario {
        alice.battlefield(stalker)
        bob.battlefield(siege, counters = defense)
        settle()
        alice.activates(stalker, ability = 1, target = siege)
        resolveAll()
        expect { siege.counters(CounterKind.Defense) shouldBe 4 }
    }
}
