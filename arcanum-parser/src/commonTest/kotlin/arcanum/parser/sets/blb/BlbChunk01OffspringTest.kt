package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.AttackTarget
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.thisEnters
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Bloomburrow chunk 01: offspring creatures. "Offspring [cost]" is an optional additional cost and an enters trigger
 * that creates a 1/1 token copy (CR 702.175a); the keyword line is read as the keyword, its reminder text ignored. With
 * them, the creatures' other abilities: "whenever you attack, choose one" with a token "that's tapped and attacking"
 * (CR 508.4), tapping with a stun counter (CR 122.1d), landfall damage to a target opponent, "whenever you gain life",
 * "loses 3 life unless they sacrifice … or discard" (a cost paid on resolution, CR 118.12a), "your choice of double
 * strike or trample", a dies trigger dealing damage, a "becomes the target of a spell or ability an opponent controls"
 * trigger, and "can't be blocked by creatures with power 2 or less".
 */
class BlbChunk01OffspringTest {
    private fun offspring(cost: String) = Keyword.Offspring(manaCost(cost))

    @Test
    fun pluckyHareguard() = card(
        name = "Plucky Hareguard",
        cost = "{2}{W}",
        type = "Creature — Rabbit Soldier",
        pt = "3/2",
        text = """
            Offspring {1} (You may pay an additional {1} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            When this creature enters, target creature you control gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        offspring("{1}"),
        whenThisEnters(pump(Objects.Target("creature"), 1, 1), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 508.4: a token put onto the battlefield attacking was never declared as an attacker; its controller chooses
    // what it attacks. CR 611.2c: "attacking creatures you control" is fixed as the effect begins.
    @Test
    fun burrowBattlelord() = card(
        name = "Burrow Battlelord",
        cost = "{2}{W}{W}",
        type = "Creature — Rabbit Knight",
        pt = "4/4",
        text = """
            Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            Whenever you attack, choose one —
            • Create a 1/1 white Rabbit creature token that's tapped and attacking.
            • Attacking creatures you control get +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        offspring("{2}"),
        Ability.Triggered(
            trigger = youAttack,
            instructions = Instructions(modes = Modes(
                options = listOf(
                    Mode(steps(Step.Do(Effect.CreateToken(
                        rabbitToken,
                        count = one(),
                        controller = Players.You,
                        tapped = true,
                        attacking = AttackTarget.Chosen,
                    )))),
                    Mode(steps(pump(
                        Objects.Each(ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Attacking), creature, you))),
                        1,
                        1,
                    ))),
                ),
                min = 1,
                max = 1,
                chooser = Players.You,
            )),
        ),
    )

    @Test
    fun sparrowSquadron() = card(
        name = "Sparrow Squadron",
        cost = "{2}{U}",
        type = "Creature — Bird Scout",
        pt = "2/2",
        text = """
            Offspring {3} (You may pay an additional {3} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            Flying
            When this creature enters, target creature you control gains flying until end of turn.
        """,
    ) parsesTo abilities(
        offspring("{3}"),
        Keyword.Flying,
        whenThisEnters(gains(Objects.Target("creature"), Keyword.Flying), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 122.1d: a stun counter stops its next untap; the creature is tapped first, then gets the counter.
    @Test
    fun puddleWhipper() = card(
        name = "Puddle Whipper",
        cost = "{3}{U}",
        type = "Creature — Frog Wizard",
        pt = "3/3",
        text = """
            Offspring {1}{U} (You may pay an additional {1}{U} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            When this creature enters, tap up to one target creature and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(
        offspring("{1}{U}"),
        whenThisEnters(
            Step.Do(Effect.Tap(Objects.Target("creature"))),
            counters(Objects.Target("creature"), kind = CounterKind.Stun),
            targets = listOf(slot("creature", creature, min = 0)),
        ),
    )

    @Test
    fun stormnetTutor() = card(
        name = "Stormnet Tutor",
        cost = "{1}{U}",
        type = "Creature — Otter Wizard",
        pt = "1/2",
        text = """
            Offspring {4} (You may pay an additional {4} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            When this creature enters, look at the top four cards of your library. You may reveal a noncreature, nonland card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        offspring("{4}"),
        whenThisEnters(
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, one(4), fromTop = true), players = Players.You, bind = "seen")),
            Step.Choose(
                arcanum.api.Choice.Objects(
                    ObjectFilter.And(listOf(ObjectFilter.Not(creature), ObjectFilter.Not(land))),
                    min = one(0),
                    max = one(),
                    from = Objects.Bound("seen"),
                ),
                bind = "chosen",
                chooser = Players.You,
            ),
            Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
            Step.Do(Effect.Move(Objects.Bound("chosen"), arcanum.api.Destination(Zone.Hand))),
            Step.Do(Effect.Move(
                Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")),
                arcanum.api.Destination(Zone.Library, libraryPosition = -1, randomOrder = true),
            )),
        ),
    )

    @Test
    fun nightskySeer() = card(
        name = "Nightsky Seer",
        cost = "{2}{B}",
        type = "Creature — Bat Warlock",
        pt = "2/3",
        text = """
            Offspring {B} (You may pay an additional {B} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            Flying
            At the beginning of your upkeep, reveal the top card of your library and put that card into your hand. You lose life equal to its mana value.
        """,
    ) parsesTo abilities(
        offspring("{B}"),
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.Upkeep, PlayerFilter.You)),
            instructions = steps(
                Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, one(), fromTop = true), bind = "revealed")),
                Step.Do(Effect.Move(Objects.Bound("revealed"), arcanum.api.Destination(Zone.Hand))),
                Step.Do(Effect.LoseLife(
                    Players.You,
                    Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.Bound("revealed"), arcanum.api.Combine.Single),
                )),
            ),
        ),
    )

    // CR 207.2c: landfall is an ability word
    @Test
    fun shimmerscaleStalker() = card(
        name = "Shimmerscale Stalker",
        cost = "{B}",
        type = "Creature — Lizard Assassin",
        pt = "1/2",
        text = """
            Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            Landfall — Whenever a land you control enters, this creature deals 1 damage to target opponent.
        """,
    ) parsesTo abilities(
        offspring("{2}"),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(land, you)), to = Zone.Battlefield)),
            instructions = Instructions(
                targets = listOf(targetOpponent),
                steps = listOf(Step.Do(Effect.DealDamage(Recipients(players = Players.Target("opponent")), one(), source = Objects.Self))),
            ),
            abilityWord = "Landfall",
        ),
    )

    @Test
    fun skyveilAcolyte() = card(
        name = "Skyveil Acolyte",
        cost = "{1}{B}",
        type = "Creature — Bat Cleric",
        pt = "2/1",
        text = """
            Offspring {2}{B} (You may pay an additional {2}{B} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            Flying
            This creature can't block.
            Whenever you gain life, each opponent loses 1 life.
        """,
    ) parsesTo abilities(
        offspring("{2}{B}"),
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.CantBlock(attackers = ObjectFilter.Any)), affects = Affected.Objects(Objects.Self)),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.GainLife(PlayerFilter.You)),
            instructions = steps(Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), one()))),
        ),
    )

    // CR 118.12a: the opponent may pay either cost; if they don't, they lose 3 life
    @Test
    fun spikehideBully() = card(
        name = "Spikehide Bully",
        cost = "{3}{B}",
        type = "Creature — Rat Rogue",
        pt = "4/3",
        text = """
            Offspring {3} (You may pay an additional {3} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            When this creature enters, target opponent loses 3 life unless they sacrifice a nonland permanent of their choice or discard a card.
        """,
    ) parsesTo abilities(
        offspring("{3}"),
        Ability.Triggered(
            trigger = thisEnters,
            instructions = Instructions(
                targets = listOf(targetOpponent),
                steps = losesUnlessSacrificesOrDiscards(Players.Target("opponent"), 3),
            ),
        ),
    )

    @Test
    fun sparkleConjurer() = card(
        name = "Sparkle Conjurer",
        cost = "{1}{R}",
        type = "Creature — Otter Wizard",
        pt = "2/2",
        text = """
            Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            Whenever you cast a noncreature spell, this creature deals 1 damage to each opponent.
        """,
    ) parsesTo abilities(
        offspring("{2}"),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Cast(ObjectFilter.Not(creature), PlayerFilter.You)),
            instructions = steps(Step.Do(Effect.DealDamage(
                Recipients(players = Players.Each(PlayerFilter.Opponent)),
                one(),
                source = Objects.Self,
            ))),
        ),
    )

    // The choice between the keywords is made as the ability resolves (CR 608.2d)
    @Test
    fun manyTailedMouse() = card(
        name = "Many-Tailed Mouse",
        cost = "{1}{R}",
        type = "Creature — Mouse Soldier",
        pt = "1/2",
        text = """
            Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            At the beginning of combat on your turn, target Mouse you control gains your choice of double strike or trample until end of turn.
        """,
    ) parsesTo abilities(
        offspring("{2}"),
        Ability.Triggered(
            trigger = yourCombat,
            instructions = Instructions(
                targets = listOf(slot("Mouse", ObjectFilter.And(listOf(sub("Mouse"), you)))),
                steps = listOf(
                    Step.Choose(arcanum.api.Choice.Option(listOf("double strike", "trample")), bind = "keyword", chooser = Players.You),
                    Step.If(
                        Condition.Chose("keyword", "double strike"),
                        then = listOf(gains(Objects.Target("Mouse"), Keyword.DoubleStrike)),
                        otherwise = listOf(gains(Objects.Target("Mouse"), Keyword.Trample)),
                    ),
                ),
            ),
        ),
    )

    // CR 608.2h: the creature that died deals the damage as it last existed
    @Test
    fun ashtrailRunner() = card(
        name = "Ashtrail Runner",
        cost = "{1}{R}",
        type = "Creature — Lizard Warlock",
        pt = "2/1",
        text = """
            Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            When this creature dies, it deals 1 damage to target player.
        """,
    ) parsesTo abilities(
        offspring("{2}"),
        Ability.Triggered(
            trigger = thisDies,
            instructions = Instructions(
                targets = listOf(targetPlayer),
                steps = listOf(Step.Do(Effect.DealDamage(Recipients(players = Players.Target("player")), one(), source = Objects.Self))),
            ),
        ),
    )

    // CR 115.10: a spell or ability "becomes the target" as it is put on the Stack; "that creature" is the event's object
    @Test
    fun cloverfieldCadet() = card(
        name = "Cloverfield Cadet",
        cost = "{G}",
        type = "Creature — Rabbit Warrior",
        pt = "2/1",
        text = """
            Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            Trample
            Whenever a creature you control becomes the target of a spell or ability an opponent controls, put a +1/+1 counter on target creature you control other than that creature.
        """,
    ) parsesTo abilities(
        offspring("{2}"),
        Keyword.Trample,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.BecomesTarget(creatureYouControl, by = opponents)),
            instructions = Instructions(
                targets = listOf(slot("creature", ObjectFilter.And(listOf(
                    creature,
                    you,
                    ObjectFilter.Not(ObjectFilter.Is(Objects.EventObject(EventRole.Object))),
                )))),
                steps = listOf(counters(Objects.Target("creature"))),
            ),
        ),
    )

    @Test
    fun tinshieldBruiser() = card(
        name = "Tinshield Bruiser",
        cost = "{3}{G}",
        type = "Creature — Raccoon Warrior",
        pt = "4/4",
        text = """
            Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            This creature can't be blocked by creatures with power 2 or less.
        """,
    ) parsesTo abilities(
        offspring("{2}"),
        Ability.Static(
            listOf(Mechanic.CantBeBlockedBy(ObjectFilter.And(listOf(
                creature,
                ObjectFilter.Power(arcanum.api.Comparison.AtMost, one(2)),
            )))),
            affects = Affected.Objects(Objects.Self),
        ),
    )

    @Test
    fun gentleThicketkeeper() = card(
        name = "Gentle Thicketkeeper",
        cost = "{1}{G}",
        type = "Creature — Possum Druid",
        pt = "2/2",
        text = """
            Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            {T}: Add one mana of any color.
            {T}: Put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        offspring("{2}"),
        Ability.Activated(
            cost = arcanum.api.Cost(listOf(arcanum.api.CostPart.TapSelf)),
            instructions = steps(Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))),
            rules = arcanum.api.ActivationRules(timing = arcanum.api.Timing.Any, activatedBy = PlayerFilter.You),
        ),
        Ability.Activated(
            cost = arcanum.api.Cost(listOf(arcanum.api.CostPart.TapSelf)),
            instructions = steps(counters(Objects.Self)),
            rules = arcanum.api.ActivationRules(timing = arcanum.api.Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )
}
