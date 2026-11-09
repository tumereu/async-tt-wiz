package arcanum.parser.sets.fdn

import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
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
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 11c of FDN: counter, damage-dealt, draw-step, chosen-colour cast and mana-spent triggers of six cards. Invented names, the real oracle text. */
class FdnChunk11cTriggersTest {
    private val eventObject = Objects.EventObject(EventRole.Object)

    // CR 614.12 (as it enters), 607.2d (the choice is remembered), 603.2 (cast trigger), 202.2 (the colour a spell is)
    @Test
    fun diamondMare() = card(
        name = "Jadeglint Mare",
        cost = "{2}",
        type = "Artifact Creature — Horse",
        pt = "1/3",
        text = """
            As this creature enters, choose a color.
            Whenever you cast a spell of the chosen color, you gain 1 life.
        """,
    ) parsesTo abilities(
        Ability_enterChoice(),
        triggered(youCast(and(spellFilter, ObjectFilter.OfColor(ColorRef.Remembered("color")))), gainLife(1)),
    )

    private fun Ability_enterChoice() = arcanum.api.Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.Color(), bind = "color", chooser = Players.You, remember = true)))))),
        affects = arcanum.api.Affected.Objects(Objects.Self))

    // CR 106.6 (mana with a delayed trigger), 603.7a, 707.10 (copy a spell), 707.10c (new targets), 202.2 (red), 108.1
    @Test
    fun pyromancersGoggles() = card(
        name = "Cinderlens Spectacles",
        cost = "{5}",
        type = "Legendary Artifact",
        text = "{T}: Add {R}. When that mana is spent to cast a red instant or sorcery spell, copy that spell and you may choose new targets for the copy.",
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!),
            whenSpent = Instructions(steps = listOf(Step.If(
                Condition.ObjectMatches(eventObject, or(and(ObjectFilter.OfColor(ColorRef.Literal(Color.Red)), instant, spellFilter), and(ObjectFilter.OfColor(ColorRef.Literal(Color.Red)), sorcery, spellFilter))),
                listOf(doing(Effect.CopySpell(eventObject, Players.You, newTargets = true))))))), Players.You))),
    )

    // CR 122.6 (counters put on), 603.2c (one or more: once for the batch), 205.3 (non-Hydra), 109.1
    @Test
    fun wildwoodScourge() = card(
        name = "Thornbrake Hydra",
        cost = "{X}{G}",
        type = "Creature — Hydra",
        pt = "0/0",
        text = """
            This creature enters with X +1/+1 counters on it.
            Whenever one or more +1/+1 counters are put on another non-Hydra creature you control, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        enterWithX(),
        Trigger_batch(EventPattern.CountersPut(and(another, not(sub("Hydra")), creature, you), p1p1), counters(Objects.Self, 1)),
    )

    private fun enterWithX() = arcanum.api.Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersWithCounters(p1p1, arcanum.api.Value.CastX(Objects.Self))))), affects = arcanum.api.Affected.Objects(Objects.Self))

    private fun Trigger_batch(event: EventPattern, vararg steps: Step) =
        triggered(arcanum.api.Trigger.On(listOf(event), oneOrMore = true), *steps)

    // CR 702.12 (indestructible), 508.1 (attacks), 603.10a (the creature as it died), 120.1 (damage dealt by this creature this turn)
    @Test
    fun predatorOoze() = card(
        name = "Gullet Ooze",
        cost = "{G}{G}{G}",
        type = "Creature — Ooze",
        pt = "1/1",
        text = """
            Indestructible
            Whenever this creature attacks, put a +1/+1 counter on it.
            Whenever a creature dealt damage by this creature this turn dies, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Indestructible,
        triggered(attacksSelf, counters(Objects.Self, 1)),
        triggered(on(EventPattern.ZoneChange(and(creature, ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter()), role = EventRole.Object)),
            from = Zone.Battlefield, to = Zone.Graveyard)), counters(Objects.Self, 1)),
    )

    // CR 504.1 (draw step), 120 / 121.1 (drawing), 603.2 (each player's step), 109.5 (that player)
    @Test
    fun dictateOfKruphix() = card(
        name = "Decree of Plenty",
        cost = "{1}{U}{U}",
        type = "Enchantment",
        text = """
            Flash
            At the beginning of each player's draw step, that player draws an additional card.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(stepBegins(TurnStep.Draw, PlayerFilter.Any), draw(1, Players.EventPlayer)),
    )

    // CR 122.6, 603.2c, 603.4 (once each turn is the ability's own limit); "you put" = your spells and abilities
    @Test
    fun exemplarOfLight() = card(
        name = "Paragon of Dawn",
        cost = "{2}{W}{W}",
        type = "Creature — Angel",
        pt = "3/3",
        text = """
            Flying
            Whenever you gain life, put a +1/+1 counter on this creature.
            Whenever you put one or more +1/+1 counters on this creature, draw a card. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(EventPattern.GainLife(PlayerFilter.You)), counters(Objects.Self, 1)),
        triggered(arcanum.api.Trigger.On(listOf(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), p1p1, causedBy = you)), oneOrMore = true), draw(1), perTurn = 1),
    )
}
