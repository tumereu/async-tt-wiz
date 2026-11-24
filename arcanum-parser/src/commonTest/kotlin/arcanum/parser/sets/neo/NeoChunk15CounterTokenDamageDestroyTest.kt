package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 15: counter, create-token, damage, destroy, discard, draw and gain-abilities wordings: a creature-spell counter with a counter on up to one
 * creature, "you may pay {1}. If you do, create tokens" end-step triggers, conditional second damage, destroy-plus-rider spells, a modal death trigger,
 * "attacks alone" and "creature died this turn" conditions, and conditional keyword statics. */
class NeoChunk15CounterTokenDamageDestroyTest {
    private val spirit = creatureToken("Spirit", emptyList(), 1, 1)
    private val modifiedYouControl = and(creature, you, ObjectFilter.Modified)
    private val samuraiOrWarrior = and(or(sub("Samurai"), sub("Warrior")), you)
    private val attacksAlone = on(EventPattern.Attack(samuraiOrWarrior, alone = true))
    private fun payOne(vararg then: Step) = arrayOf(
        Step.Pay(manaCost("{1}"), payer = Players.You, bind = "paid"),
        ifThen(Condition.Did("paid"), *then),
    )

    // CR 701.6a (counter), 122.1 (counters), 115.1 ("up to one" target may be none)
    @Test
    fun essenceSnare() = card(
        name = "Essence Snare",
        cost = "{U}{U}",
        type = "Instant",
        text = "Counter target creature spell. Put a +1/+1 counter on up to one target creature you control.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Counter(target("spell"))),
            counters(target("creature")),
            targets = listOf(slot("spell", and(creature, spellFilter)), slot("creature", and(creature, you), min = 0)),
        ),
    )

    // CR 603.12 is not involved: "you may pay {1}. If you do" is one resolution (CR 608.2c); the count is of Shrines when the token is created
    @Test
    fun shintaiOfSharedCauses() = card(
        name = "Shintai of Shared Causes",
        cost = "{3}{W}",
        type = "Legendary Enchantment Creature — Shrine",
        pt = "1/3",
        text = """
            Vigilance
            At the beginning of your end step, you may pay {1}. If you do, create a 1/1 colorless Spirit creature token for each Shrine you control.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(yourEndStep, *payOne(create(spirit, count(and(sub("Shrine"), you))))),
    )

    @Test
    fun searchlightHelper() = card(
        name = "Searchlight Helper",
        cost = "{3}",
        type = "Artifact Creature — Drone",
        pt = "1/1",
        text = """
            Flying
            When this creature enters, create a 1/1 colorless Spirit creature token.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, create(spirit)),
    )

    @Test
    fun forgeSmelter() = card(
        name = "Forge Smelter",
        cost = "{1}{R}",
        type = "Creature — Goblin Artificer",
        pt = "2/2",
        text = "At the beginning of combat on your turn, you may pay {1} and sacrifice an artifact. If you do, create a 3/1 red Construct artifact creature token with haste.",
    ) parsesTo abilities(
        triggered(
            beginningOfCombatOnYourTurn,
            Step.Pay(cost(mana("{1}"), sacrificeA(artifact)), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), create(creatureToken("Construct", listOf(Color.Red), 3, 1, listOf(Keyword.Haste),
                types = listOf(CardType.Artifact, CardType.Creature)))),
        ),
    )

    // CR 700.9 (modified): Equipment, Auras you control and counters; the second damage is a step of the same spell (CR 608.2c)
    @Test
    fun spiritFlare() = card(
        name = "Ember Flare",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Ember Flare deals 3 damage to target creature or planeswalker. Ember Flare also deals 2 damage to that permanent's controller if you control a modified creature. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        spell(
            damage(3, toObjects(target("permanent"))),
            ifThen(Condition.Exists(modifiedYouControl, atLeast = 1), damage(2, Recipients(players = Players.ControllerOf(target("permanent"))))),
            targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker)))),
        ),
    )

    @Test
    fun blastingEntry() = card(
        name = "Blasting Entry",
        cost = "{1}{R}",
        type = "Sorcery",
        text = "Destroy up to one target artifact. Put a +1/+1 counter on up to one target creature.",
    ) parsesTo abilities(
        spell(
            destroy(target("artifact")),
            counters(target("creature")),
            targets = listOf(slot("artifact", artifact, min = 0), slot("creature", creature, min = 0)),
        ),
    )

    @Test
    fun fortunateTribute() = card(
        name = "Fortunate Tribute",
        cost = "{W}",
        type = "Sorcery",
        text = "Destroy target artifact with mana value 3 or less. You gain 3 life.",
    ) parsesTo abilities(
        spell(
            destroy(target("artifact")),
            gainLife(3),
            targets = listOf(slot("artifact", and(artifact, manaValue(Comparison.AtMost, 3)))),
        ),
    )

    // CR 120.1 / 510.2: "was dealt damage this turn" is the creature as the recipient of any damage this turn
    @Test
    fun youAreAlreadyBroken() = card(
        name = "You Are Already Broken",
        cost = "{B}",
        type = "Instant",
        text = """
            Destroy target creature that was dealt damage this turn.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            destroy(target("creature")),
            draw(1),
            targets = listOf(slot("creature", and(creature,
                ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()), role = EventRole.Object)))),
        ),
    )

    // CR 700.2 (modes), 603.10a (dies looks back), 701.9 (discard), 110.2 (under your control)
    @Test
    fun jinjiTheMidnightMoon() = card(
        name = "Jinji, the Midnight Moon",
        cost = "{3}{B}{B}",
        type = "Legendary Creature — Dragon Spirit",
        pt = "5/5",
        text = """
            Flying, menace
            When Jinji dies, choose one —
            • Each opponent discards two cards and loses 2 life.
            • Put target non-Dragon creature card from a graveyard onto the battlefield under your control. You lose 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Menace,
        Ability.Triggered(diesSelf, Instructions(modes = modes(
                mode(
                    Step.Do(Effect.Discard(Players.Each(PlayerFilter.Opponent), n(2), ObjectFilter.Any)),
                    loseLife(2, Players.Each(PlayerFilter.Opponent)),
                ),
                mode(
                    move(target("card"), Zone.Battlefield, controller = Players.You),
                    loseLife(2),
                    targets = listOf(slot("card", and(not(sub("Dragon")), creature, inZone(Zone.Graveyard)))),
                ),
            ),
        )),
    )

    // CR 702.110b (menace), 608.2c
    @Test
    fun kaitosPursuitLookalike() = card(
        name = "Shadow Pursuit",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Target player discards two cards. Ninjas and Rogues you control gain menace until end of turn.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Discard(Players.Target("player"), n(2), ObjectFilter.Any)),
            gains(Objects.Each(and(or(sub("Ninja"), sub("Rogue")), you)), Keyword.Menace),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 508.1 ("attacks alone": it is the only creature declared as an attacker), 608.2c
    @Test
    fun akkiMonk() = card(
        name = "Akki Monk",
        cost = "{1}{R}",
        type = "Creature — Goblin Samurai",
        pt = "1/3",
        text = "Whenever a Samurai or Warrior you control attacks alone, you may discard a card. If you do, draw a card.",
    ) parsesTo abilities(
        triggered(
            attacksAlone,
            Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1)),
        ),
    )

    // CR 608.2c (if/otherwise), 701.21a (each player sacrifices a creature of their choice), 603.4 is not involved: no intervening "if"
    @Test
    fun graveGleaner() = card(
        name = "Grave Gleaner",
        cost = "{2}{B}",
        type = "Creature — Spirit",
        pt = "2/2",
        text = """
            Flying
            When this creature enters, draw a card if a creature died this turn. Otherwise, each player sacrifices a creature of their choice.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            entersSelf,
            ifThen(
                Condition.HappenedThisTurn(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events),
                draw(1),
                otherwise = listOf(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                    Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)),
                        bind = "sacrificed", chooser = Players.Bound("player")),
                    doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
                ))),
            ),
        ),
    )

    @Test
    fun paperFoldDecoy() = card(
        name = "Paperfold Decoy",
        cost = "{2}",
        type = "Artifact Creature — Frog",
        pt = "2/1",
        text = "When this creature leaves the battlefield, you may pay {2}. If you do, draw a card.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
            Step.Pay(manaCost("{2}"), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1)),
        ),
    )

    // CR 608.2c: "you may draw a card. If you do, discard a card" (the draw is the optional step)
    @Test
    fun skyswimmerCarp() = card(
        name = "Skyswimmer Carp",
        cost = "{3}{U}",
        type = "Creature — Fish",
        pt = "3/3",
        text = """
            Flying
            Whenever an artifact you control enters, you may draw a card. If you do, discard a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            enters(and(artifact, you)),
            Step.Pay(steps = listOf(draw(1)), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), discard(1)),
        ),
    )

    // CR 700.9 (modified), 613.1f (ability-adding statics)
    @Test
    fun kodamaOfTheEastTree() = card(
        name = "Kodama of the Far Tree",
        cost = "{2}{G}",
        type = "Legendary Creature — Spirit",
        pt = "3/3",
        text = """
            Reach
            Modified creatures you control have trample. (Equipment, Auras you control, and counters are modifications.)
            Whenever a modified creature you control deals combat damage to a player, search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        static(has(Keyword.Trample), affects = Objects.Each(modifiedYouControl)),
        triggered(
            on(EventPattern.Damage(modifiedYouControl, RecipientFilter(players = PlayerFilter.Any), combat = true)),
            search(basicLand()),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            shuffle,
        ),
    )

    // CR 611.3a (a static ability's condition is checked continuously), one ability per line
    @Test
    fun rodentBladeblesser() = card(
        name = "Rodent Bladeblesser",
        cost = "{2}{B}",
        type = "Creature — Rat Samurai",
        pt = "3/2",
        text = """
            This creature has deathtouch as long as you control an artifact.
            This creature has menace as long as you control an enchantment. (It can't be blocked except by two or more creatures.)
        """,
    ) parsesTo abilities(
        static(has(Keyword.Deathtouch), condition = Condition.Exists(and(artifact, you), atLeast = 1)),
        static(has(Keyword.Menace), condition = Condition.Exists(and(enchantment, you), atLeast = 1)),
    )
}
