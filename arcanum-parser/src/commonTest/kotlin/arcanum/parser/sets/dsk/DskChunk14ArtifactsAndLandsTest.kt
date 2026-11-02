package arcanum.parser.sets.dsk

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.dsk.Dsk.creature
import arcanum.parser.sets.dsk.Dsk.youControl
import kotlin.test.Test

/**
 * Duskmourn chunk 14: the colourless artifacts and the lands. "You may have it get +4/+0. If you do, …" (one choice for
 * both); cards exiled with an artifact returning as 1/1 Spirits (CR 607.2a, layers 4 and 7b); an artifact becoming a
 * creature; the bottom of a library; activated abilities of other creatures without its name (layer 6); a sacrifice
 * excluding two objects; "enters tapped unless a player has 13 or less life"; "as it enters, choose a color" (CR 614.12).
 */
class DskChunk14ArtifactsAndLandsTest {
    private fun slot(name: String, filter: ObjectFilter) = TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(1), max = Value.Fixed(1))
    private val any = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
    private val sorcery = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)
    private val thisEntersTapped = Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped))
    private val cardInAGraveyard = ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.InZone(Zone.Graveyard)))
    private fun tapFor(mana: ManaKind, vararg extra: CostPart) = Ability.Activated(Cost(listOf(CostPart.TapSelf) + extra),
        Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(mana), Players.You)))), rules = any)
    private fun oneOf(vararg colors: Color) = ManaKind.Choose(Value.Fixed(1), colors.map { ColorRef.Literal(it) })

    /** "You may have it get …. If you do, …": an optional action the rest depends on (CR 608.2d). */
    @Test
    fun pounceInTheBox() = card(
        name = "Pounce-in-the-Box",
        cost = "{3}",
        type = "Artifact Creature — Toy",
        pt = "2/4",
        text = "Whenever this creature attacks, you may have it get +4/+0 until end of turn. If you do, sacrifice it at the beginning of the next end step.",
    ) parsesTo abilities(
        Ability.Triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))), Instructions(steps = listOf(
            Step.Pay(steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(4), Value.Fixed(0))),
                Affected.Objects(Objects.Self), Duration.EndOfTurn))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, whose = PlayerFilter.Any)),
                Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Self)))))))),
        ))),
    )

    /** The creature cards exiled with it (CR 607.2a) return under your control as 1/1 Spirits for good (layers 4, 7b). */
    @Test
    fun spiritVacuum() = card(
        name = "Spirit Vacuum",
        cost = "{1}",
        type = "Artifact",
        text = """
            {T}: Exile target card from a graveyard.
            {6}, {T}, Sacrifice this artifact: Put each creature card exiled with this artifact onto the battlefield under your control with a flying counter on it. Each of them is a 1/1 Spirit in addition to its other types. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Activated(Cost(listOf(CostPart.TapSelf)), Instructions(
            targets = listOf(slot("card", cardInAGraveyard)),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
            ),
        ), rules = any),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{6}")!!), CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self))),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.Move(Objects.Where(Objects.Remembered("exiled"), creature), Destination(Zone.Battlefield, controller = Players.You,
                    counters = listOf(CountersToPut(CounterKind.Keyword(Keyword.Flying), Value.Fixed(1)))), postBind = "returned")),
                Step.Do(Effect.ApplyMechanics(listOf(
                    Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Spirit")))),
                    Mechanic.SetPowerToughness(Value.Fixed(1), Value.Fixed(1)),
                ), Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
            )),
            rules = sorcery,
        ),
    )

    /** "It becomes a 0/0 Spirit creature": this artifact, for good (layers 4 and 7b); "Activate only once." */
    @Test
    fun hauntedFoldingScreen() = card(
        name = "Haunted Folding Screen",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add {W} or {B}.
            {T}, Pay 1 life: Add {G}, {U}, or {R}.
            {7}: Put seven +1/+1 counters on this artifact. It becomes a 0/0 Spirit creature in addition to its other types. Activate only once.
        """,
    ) parsesTo abilities(
        tapFor(oneOf(Color.White, Color.Black)),
        tapFor(oneOf(Color.Green, Color.Blue, Color.Red), CostPart.PayLife(Value.Fixed(1))),
        Ability.Activated(
            cost = manaCost("{7}"),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.PutCounters(Objects.Self, CounterKind.PowerToughness(1, 1), Value.Fixed(7))),
                Step.Do(Effect.ApplyMechanics(listOf(
                    Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Spirit")))),
                    Mechanic.SetPowerToughness(Value.Fixed(0), Value.Fixed(0)),
                ), Affected.Objects(Objects.Self), Duration.Indefinitely)),
            )),
            rules = any.copy(once = true),
        ),
    )

    @Test
    fun maliciousChandelier() = card(
        name = "Malicious Chandelier",
        cost = "{6}",
        type = "Artifact Creature — Construct",
        pt = "4/4",
        text = """
            Flying
            {2}: Put target card from a graveyard on the bottom of its owner's library. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Activated(manaCost("{2}"), Instructions(
            targets = listOf(slot("card", cardInAGraveyard)),
            steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Library, libraryPosition = -1)))),
        ), rules = sorcery),
    )

    /** Activated abilities of other creatures without its name (layer 6, CR 613.1f). */
    @Test
    fun morvinMimickingMenace() = card(
        name = "Morvin, Mimicking Menace",
        cost = "{2}",
        type = "Legendary Artifact Creature — Toy",
        pt = "2/2",
        text = "Morvin has all activated abilities of creatures you control that don't have the same name as this creature.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.GainAbilitiesOf(Objects.Each(ObjectFilter.And(listOf(creature, youControl,
            ObjectFilter.Not(ObjectFilter.SameNameAs(Objects.Self))))))), affects = Affected.Objects(Objects.Self)),
    )

    /** A sacrifice of a permanent other than the attacking creature and this Equipment; "if you do" (CR 608.2d). */
    @Test
    fun hacksaw() = card(
        name = "Hacksaw",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+0.
            Whenever equipped creature attacks, you may sacrifice a permanent other than that creature or this Equipment. If you do, draw a card.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(0))), affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
        Ability.Triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)))), Instructions(steps = listOf(
            Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.And(listOf(
                ObjectFilter.Category(ObjectCategory.Permanent),
                ObjectFilter.Not(ObjectFilter.Is(Objects.EventObject(EventRole.Object))),
                ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
            )), count = Value.Fixed(1))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(1))))),
        ))),
        Keyword.Equip(manaCost("{2}")),
    )

    /** "Unless a player has 13 or less life": checked as it enters (CR 614.1c). */
    @Test
    fun forsakenCampsite() = card(
        name = "Forsaken Campsite",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless a player has 13 or less life.
            {T}: Add {W} or {U}.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(thisEntersTapped), affects = Affected.Objects(Objects.Self),
            condition = Condition.Not(Condition.PlayerExists(PlayerFilter.Life(Comparison.AtMost, Value.Fixed(13)), atLeast = 1))),
        tapFor(oneOf(Color.White, Color.Blue)),
    )

    /** "As it enters, choose a color" kept on the land (CR 614.12, 607.2d) and "one mana of the chosen color". */
    @Test
    fun vulgarothsDen() = card(
        name = "Vulgaroth's Den",
        cost = null,
        type = "Enchantment Land",
        text = """
            Hexproof
            This land enters tapped. As it enters, choose a color.
            {T}: Add one mana of the chosen color.
        """,
    ) parsesTo abilities(
        Keyword.Hexproof,
        Ability.Static(listOf(thisEntersTapped), affects = Affected.Objects(Objects.Self)),
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.Color(), bind = "color", chooser = Players.You, remember = true)))))),
            affects = Affected.Objects(Objects.Self)),
        tapFor(ManaKind.Choose(Value.Fixed(1), listOf(ColorRef.Remembered("color")))),
    )
}
