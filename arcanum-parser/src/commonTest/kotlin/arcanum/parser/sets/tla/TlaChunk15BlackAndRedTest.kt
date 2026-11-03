package arcanum.parser.sets.tla

import arcanum.api.Ability
import arcanum.api.AbilityLabel
import arcanum.api.CardType
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 15: black and red cards: one ability with an enters and an upkeep event (CR 603.2), X from permanents
 * sacrificed this turn with a threshold (CR 107.3), a Vehicle that copies itself and becomes a creature by its name count, sacrifice
 * triggers, dealt-damage-this-turn, an Aura's cost reduced by its target (CR 601.2f, 303.4a), a return with a finality counter, a kicked
 * return to the battlefield, "unless its controller has it deal damage" (a choice of that player), damage increased by fire counters
 * (CR 614.1a), a keyword counter (CR 122.1b).
 */
class TlaChunk15BlackAndRedTest {
    // CR 603.2 (two trigger events), 107.3 (X defined by this turn's sacrifices)
    @Test
    fun relentlessHunt() = card(
        name = "Relentless Hunt",
        cost = "{1}{B}",
        type = "Enchantment",
        text = """
            When this enchantment enters and at the beginning of your upkeep, you lose 1 life and create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Whenever you attack, put X +1/+1 counters on target attacking creature, where X is the number of permanents you've sacrificed this turn. If X is three or more, that creature gains lifelink until end of turn.
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), EventPattern.StepBegins(arcanum.api.TurnStep.Upkeep,
            PlayerFilter.You))), loseLife(1), createClue),
        triggered(youAttack,
            counters(target("creature"), Value.EventsThisTurn(EventPattern.Sacrifice(you), Measure.Objects)),
            Step.If(Condition.HappenedThisTurn(EventPattern.Sacrifice(you), atLeast = 3, measure = Measure.Objects),
                then = listOf(gains(target("creature"), Keyword.Lifelink))),
            targets = listOf(slot("creature", and(attacking, creature)))),
    )

    // CR 603.4, 707.2 (a token copy of itself), 205.1 (an artifact creature while eight share its name)
    @Test
    fun cinderbirdFleetAirship() = card(
        name = "Cinderbird Fleet Airship",
        cost = "{2}{B}{B}",
        type = "Artifact — Vehicle",
        text = """
            Flying
            At the beginning of your end step, if you sacrificed a permanent this turn, create a token that's a copy of this Vehicle.
            As long as you control eight or more permanents named Cinderbird Fleet Airship, this Vehicle is an artifact creature.
            Crew 1
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(yourEndStep, doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self), n(1), Players.You)),
            interveningIf = Condition.HappenedThisTurn(EventPattern.Sacrifice(you), atLeast = 1, measure = Measure.Objects)),
        static(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)),
            condition = Condition.Exists(and(permanentFilter, ObjectFilter.Named("Cinderbird Fleet Airship"), you), atLeast = 8)),
        Keyword.Crew(1),
    )

    @Test
    fun corsairHawkers() = card(
        name = "Corsair Hawkers",
        cost = "{2}{B}",
        type = "Creature — Human Pirate",
        text = """
            Deathtouch
            Whenever you sacrifice another permanent, put a +1/+1 counter on this creature.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(youSacrificeAnother, counters(Objects.Self)),
    )

    @Test
    fun crowHawk() = card(
        name = "Crow Hawk",
        cost = "{2}{B}",
        type = "Creature — Bird Assassin",
        text = """
            Flying
            Whenever this creature enters or attacks, exile up to one target card from a graveyard. If a creature card is exiled this way, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Whenever you draw your second card each turn, each opponent loses 1 life and you gain 1 life.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersOrAttacksSelf, move(target("card"), Zone.Exile, postBind = "exiled"),
            Step.If(Condition.ObjectMatches(Objects.Bound("exiled"), creature), then = listOf(createClue)),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)), min = 0))),
        triggered(drawSecondCard, loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
    )

    // CR 608.2h: whether it was dealt damage this turn, as it last existed
    @Test
    fun betrayedForCoin() = card(
        name = "Betrayed for Coin",
        cost = "{3}{B}",
        type = "Instant",
        text = """
            Exile target creature. If it was dealt damage this turn, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        spell(doing(Effect.Move(target("creature"), Destination(Zone.Exile), preBind = "exiled")),
            Step.If(Condition.ObjectMatches(Objects.Bound("exiled"), ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Any)),
                role = EventRole.Object)), then = listOf(createClue)),
            targets = listOf(slot("creature", creature))),
    )

    // CR 601.2f, 303.4a: an Aura spell's cost reduced by what it targets (its enchant slot)
    @Test
    fun bogsnareTrap() = card(
        name = "Bogsnare Trap",
        cost = "{2}{B}",
        type = "Enchantment — Aura",
        text = """
            This spell costs {1} less to cast if it targets a creature with flying.
            Enchant creature
            Enchanted creature gets -5/-3.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, n(1))),
            condition = Condition.ObjectMatches(target(Keyword.Enchant.SLOT), and(creature, kw(Keyword.Flying)))),
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(pt(-5, -3), affects = Objects.AttachedTo(Objects.Self)),
    )

    // CR 113.6c (a triggered ability that works in the graveyard), 122.1h (finality)
    @Test
    fun houndbat() = card(
        name = "Houndbat",
        cost = "{2}{B}",
        type = "Creature — Wolf Bat",
        text = """
            Flying
            Whenever you draw your second card each turn, you may pay {B}. If you do, return this card from your graveyard to the battlefield with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(drawSecondCard, Instructions(steps = listOf(
            Step.Pay(manaCost("{B}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.Move(Objects.Self, Destination(Zone.Battlefield,
                counters = listOf(CountersToPut(CounterKind.Finality, n(1)))))))))), functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 702.33d
    @Test
    fun banishedHeirsResolve() = card(
        name = "Banished Heir's Resolve",
        cost = "{B}",
        type = "Instant",
        text = """
            Kicker {4} (You may pay an additional {4} as you cast this spell.)
            Return target creature card from your graveyard to your hand. If this spell was kicked, instead put that card onto the battlefield tapped.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{4}"))),
        spell(Step.If(kicked, then = listOf(move(target("card"), Zone.Battlefield, tapped = true)), otherwise = listOf(move(target("card"), Zone.Hand))),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    @Test
    fun hogUQuill() = card(
        name = "Hog-u-quill",
        cost = "{2}{R}",
        type = "Creature — Boar Porcupine",
        text = "Whenever you cast a noncreature spell, put a +1/+1 counter on this creature.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(youCast(noncreature), counters(Objects.Self)),
    )

    @Test
    fun oldKingsBash() = card(
        name = "Old King's Bash",
        cost = "{3}{R}",
        type = "Sorcery",
        text = """
            Choose one —
            • Old King's Bash deals damage equal to the number of lands you control to target creature.
            • Destroy target land creature or nonbasic land.
        """,
    ) parsesTo abilities(
        modal(
            mode(damage(count(and(land, you)), toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
            mode(destroy(target("permanent")), targets = listOf(slot("permanent", or(and(land, creature), and(not(basic), land))))),
        ),
    )

    // CR 118.12a: not a cost: its controller chooses to be dealt the damage or let the permanent be destroyed
    @Test
    fun blastmindAssassin() = card(
        name = "Blastmind Assassin",
        cost = "{3}{R}{R}",
        type = "Legendary Creature — Human Assassin",
        text = "Whenever Blastmind Assassin attacks, destroy target permanent unless its controller has Blastmind Assassin deal damage to them equal to his power.",
        pt = "4/6",
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Choose(arcanum.api.Choice.Option(listOf("damage", "destroy")), bind = "choice", chooser = Players.ControllerOf(target("permanent"))),
            Step.If(Condition.Chose("choice", "damage"),
                then = listOf(damage(powerOf(Objects.Self), arcanum.api.Recipients(players = Players.ControllerOf(target("permanent"))))),
                otherwise = listOf(destroy(target("permanent")))),
            targets = listOf(slot("permanent", permanentFilter))),
    )

    @Test
    fun craftyGambit() = card(
        name = "Crafty Gambit",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Target creature gets +3/+1 until end of turn.
            Create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), 3, 1), createClue, targets = listOf(slot("creature", creature))),
    )

    @Test
    fun defectorsPupil() = card(
        name = "Defector's Pupil",
        cost = "{1}{R}",
        type = "Creature — Human Rebel Ally",
        text = "{T}: Another target creature you control with power 2 or less can't be blocked this turn.",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(tap), untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked),
            targets = listOf(slot("creature", and(another, creature, you, power(Comparison.AtMost, 2))))),
    )

    // CR 107.3m (X of its spell), 614.1a (more damage)
    @Test
    fun destinedBlaze() = card(
        name = "Destined Blaze",
        cost = "{X}{R}{R}{R}",
        type = "Enchantment",
        text = """
            Flash
            This enchantment enters with X fire counters on it.
            If a source you control would deal damage to an opponent or a permanent an opponent controls, it deals that much damage plus an amount of damage equal to the number of fire counters on this enchantment instead.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(CounterKind.Named("fire"), Value.CastX(of = Objects.Self))))),
        static(Mechanic.Replacement(EventPattern.Damage(you, RecipientFilter(objects = and(permanentFilter, opponents), players = PlayerFilter.Opponent)),
            modify = listOf(Modification.AddAmount(Value.Of(NumericProperty.Counters(CounterKind.Named("fire")), Objects.Self, arcanum.api.Combine.Single))))),
    )

    // CR 207.2c (raid), 603.4
    @Test
    fun emberLegionRaider() = card(
        name = "Ember Legion Raider",
        cost = "{3}{R}",
        type = "Creature — Human Soldier",
        text = """
            Raid — When this creature enters, if you attacked this turn, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
        pt = "4/2",
    ) parsesTo abilities(
        triggered(entersSelf, createClue, interveningIf = Condition.HappenedThisTurn(EventPattern.Attack(you), atLeast = 1, measure = Measure.Events),
            abilityWord = "Raid"),
    )

    // CR 702.33d, 613.1b (control until end of turn)
    @Test
    fun rebelsMindBending() = card(
        name = "Rebel's Mind Bending",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Kicker {3} (You may pay an additional {3} as you cast this spell.)
            Target creature can't block this turn. If this spell was kicked, gain control of that creature until end of turn, untap it, and it gains haste until end of turn.
            Create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{3}"))),
        spell(untilEndOfTurn(target("creature"), Mechanic.CantBlock(ObjectFilter.Any)),
            Step.If(kicked, then = listOf(untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)), untapIt(target("creature")),
                gains(target("creature"), Keyword.Haste))),
            createClue,
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun thunderboltJab() = card(
        name = "Thunderbolt Jab",
        cost = "{1}{R}",
        type = "Instant",
        text = "Thunderbolt Jab deals 3 damage to any target.",
    ) parsesTo abilities(
        spell(damage(3, toAny), targets = listOf(anyTarget)),
    )

    // CR 122.1b: a double strike counter
    @Test
    fun ilsaJadedEdge() = card(
        name = "Ilsa, Jaded Edge",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Noble",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Exhaust — {3}: Put a double strike counter on Ilsa. (Activate each exhaust ability only once.)
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Prowess,
        activated(cost(mana("{3}")), counters(Objects.Self, 1, CounterKind.Keyword(Keyword.DoubleStrike)), label = AbilityLabel.Exhaust),
    )

    @Test
    fun weaselGecko() = card(
        name = "Weasel Gecko",
        cost = "{4}{R}{R}",
        type = "Creature — Mongoose Lizard",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature enters, it deals 1 damage to any target.
            Mountaincycling {2} ({2}, Discard this card: Search your library for a Mountain card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "5/6",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersSelf, damage(1, toAny), targets = listOf(anyTarget)),
        Keyword.Typecycling(sub("Mountain"), "Mountaincycling", manaCost("{2}")),
    )
}
