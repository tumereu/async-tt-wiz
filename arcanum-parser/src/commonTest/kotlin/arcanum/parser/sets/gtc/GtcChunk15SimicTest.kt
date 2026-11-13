package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Gatecrash chunk 15: Simic (green and blue). Winning the game with four of a name (CR 104.2b, 201.2), a land becoming a
 * creature until end of turn (CR 205.1b), a token sized by the countered spell's mana value (CR 608.2h), entering with X
 * counters (CR 107.3m, 614.1c), a modal charm (CR 700.2), casting the revealed card for free (CR 608.2g), an additional
 * land play (CR 305.2), and setting the base power and toughness of your creatures (CR 613.4b).
 */
class GtcChunk15SimicTest {
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList(), announced: List<String> = emptyList()) =
        Ability.Spell(Instructions(targets = targets, steps = steps.toList(), announced = announced))

    // CR 104.2b, 603.4: checked as it triggers and resolves; CR 201.2: by name
    @Test
    fun geneVisionary() = card(
        name = "Gene Visionary",
        cost = "{1}{G}{U}",
        type = "Creature — Human Wizard",
        pt = "2/3",
        text = "At the beginning of the end step, if you control four or more creatures named Gene Visionary, you win the game.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
            steps(Step.Do(Effect.WinGame(Players.You))),
            interveningIf = Condition.Exists(ObjectFilter.And(listOf(creature, ObjectFilter.Named("Gene Visionary"), you)), atLeast = 4),
        ),
    )

    // CR 205.1b: still a land; no colour given, so its colour doesn't change
    @Test
    fun hydroshape() = card(
        name = "Hydroshape",
        cost = "{G}{U}",
        type = "Instant",
        text = "Target land becomes a 3/3 Elemental creature with flying until end of turn. It's still a land.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(Objects.Target("land"),
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Elemental")))),  // CR 205.1b: "still a [type]" keeps all prior subtypes
                Mechanic.SetPowerToughness(n(3), n(3)),
                Mechanic.GainAbilities(listOf(Keyword.Flying))),
            targets = listOf(slot("land", land)),
        ),
    )

    // CR 608.2h: the countered spell's mana value as it last existed
    @Test
    fun arcaneGenesis() {
        val mv = Value.Of(NumericProperty.ManaValue, Objects.Target("spell"), Combine.Single)
        card(
            name = "Arcane Genesis",
            cost = "{2}{G}{U}{U}",
            type = "Instant",
            text = "Counter target spell. Create an X/X green Ooze creature token, where X is that spell's mana value.",
        ) parsesTo abilities(
            spell(
                Step.Do(Effect.Counter(Objects.Target("spell"))),
                Step.Do(Effect.CreateToken(TokenSpec.Defined(arcanum.api.FaceDefinition("Ooze Token", colorIndicator = listOf(Color.Green),
                    types = listOf(CardType.Creature), subtypes = listOf(Subtype("Ooze")), power = PrintedNumber.X, toughness = PrintedNumber.X),
                    power = mv, toughness = mv), n(1), Players.You)),
                targets = listOf(slot("spell", ObjectFilter.Category(ObjectCategory.Spell))),
            ),
        )
    }

    // CR 107.3m: the X it was cast with
    @Test
    fun cloudSwimmer() = card(
        name = "Cloud Swimmer",
        cost = "{X}{G}{U}",
        type = "Creature — Leviathan",
        pt = "0/0",
        text = """
            Flying
            This creature enters with X +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, Value.CastX(Objects.Self))))), affects = Affected.Objects(Objects.Self)),
    )

    // CR 614.1c: counted as it enters; its power then, counters included
    @Test
    fun firstSpeakerZelana() = card(
        name = "First Speaker Zelana",
        cost = "{2}{G}{G}{U}{U}",
        type = "Legendary Creature — Merfolk Wizard",
        pt = "1/1",
        text = """
            First Speaker Zelana enters with X +1/+1 counters on it, where X is the greatest power among other creatures you control.
            When First Speaker Zelana enters, draw cards equal to its power.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, Value.Of(NumericProperty.Power,
                Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, you))), Combine.Greatest))))),
            affects = Affected.Objects(Objects.Self)),
        whenThisEnters(Step.Do(Effect.Draw(Players.You, power(Objects.Self)))),
    )

    @Test
    fun hybridCharm() = card(
        name = "Hybrid Charm",
        cost = "{G}{U}",
        type = "Instant",
        text = """
            Choose one —
            • Target creature gets +3/+3 until end of turn.
            • Permanents you control gain hexproof until end of turn.
            • Return target creature to its owner's hand.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(pump(Objects.Target("creature"), 3, 3)))),
            Mode(steps(gains(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Permanent), you))), Keyword.Hexproof))),
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Hand)))))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    // CR 608.2g: cast while it resolves; a land is put onto the battlefield (not played) and this spell returns to hand
    @Test
    fun startlingResults() {
        val revealed = Objects.Bound("revealed")
        card(
            name = "Startling Results",
            cost = "{2}{G}{U}",
            type = "Sorcery",
            text = "Shuffle your library, then reveal the top card. If it's a nonland card, you may cast it without paying its mana cost. If it's a land card, you may put it onto the battlefield and return Startling Results to its owner's hand.",
        ) parsesTo abilities(
            spell(
                Step.Do(Effect.Shuffle(Players.You)),
                Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed")),
                Step.If(Condition.ObjectMatches(revealed, ObjectFilter.Not(land)), then = listOf(Step.May(listOf(
                    Step.Do(Effect.Cast(revealed, PlayKind.Cast, withoutPaying = true, caster = Players.You))), Players.You))),
                Step.If(Condition.ObjectMatches(revealed, land), then = listOf(Step.May(listOf(
                    Step.Do(Effect.Move(revealed, Destination(Zone.Battlefield))),
                    Step.Do(Effect.Move(Objects.Self, Destination(Zone.Hand)))), Players.You))),
            ),
        )
    }

    // CR 305.2: one more land this turn
    @Test
    fun civicEvolution() = card(
        name = "Civic Evolution",
        cost = "{3}{G}{U}",
        type = "Sorcery",
        text = "Draw three cards. You may play an additional land this turn.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Draw(Players.You, n(3))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ExtraLandPlays(1)), Affected.Players(Players.You), Duration.EndOfTurn)),
        ),
    )

    // CR 613.4b: base power and toughness X/X, the creatures fixed as it resolves (CR 611.2c)
    @Test
    fun biomassShift() = card(
        name = "Biomass Shift",
        cost = "{X}{G/U}{G/U}",
        type = "Instant",
        text = "Creatures you control have base power and toughness X/X until end of turn.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(Objects.Each(creatureYouControl), Mechanic.SetPowerToughness(Value.Announced("X"), Value.Announced("X"))), announced = listOf("X")),
    )
}
