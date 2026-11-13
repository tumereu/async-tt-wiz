package arcanum.parser.sets.dft

import arcanum.api.DamageOccurrence
import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Aetherdrift chunk 16: colourless artifacts: a planeswalker Equipment that can't be attacked while attached and grows from combat damage
 * (CR 301.5, 306), counters by the X paid (CR 107.3m), draws by its counters as it last existed (CR 603.10a), modes not chosen this turn
 * (CR 700.2), mana for a target player from sacrificed artifacts (CR 605.1a: targeted, so not a mana ability), and a condition comparing land
 * counts.
 */
class DftChunk16ArtifactsTest {
    @Test
    fun aetherhullJacket() = card(
        name = "Aetherhull Jacket",
        cost = "{3}",
        type = "Artifact Creature — Thopter",
        text = """
            Flying, vigilance
            {2}, {T}, Sacrifice this creature: Destroy another target artifact. Activate only as a sorcery.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        activated(cost(mana("{2}"), tap, sacrificeSelf), destroy(target("artifact")), targets = listOf(slot("artifact", and(another, artifact))), rules = asSorcery),
    )

    // CR 306.5d, 301.5: a planeswalker that is an Equipment; the ability it has only while attached
    @Test
    fun theAetherflare() = card(
        name = "The Aetherflare",
        cost = "{4}",
        type = "Legendary Artifact Planeswalker — Equipment",
        text = """
            As long as The Aetherflare is attached to a creature, The Aetherflare can't be attacked and has "Whenever equipped creature deals combat damage during your turn, put that many loyalty counters on The Aetherflare."
            [+1]: Attach The Aetherflare to up to one target creature you control. Put a +1/+1 counter on that creature.
            [−5]: Draw two cards.
            [−10]: Add ten mana of any one color.
        """,
        loyalty = "4",
    ) parsesTo abilities(
        static(Mechanic.CantBeAttacked, has(triggered(on(EventPattern.Damage(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), RecipientFilter(), combat = true, per = DamageOccurrence.Source)),
                counters(Objects.Self, Value.EventAmount, CounterKind.Loyalty), triggersOnlyIf = Condition.YourTurn)),
            condition = Condition.ObjectMatches(Objects.AttachedTo(Objects.Self), creature)),
        activated(cost(CostPart.Loyalty(n(1))), doing(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), counters(target("creature")),
            targets = listOf(slot("creature", creatureYouControl, min = 0))),
        activated(cost(CostPart.Loyalty(n(-5))), draw(2)),
        activated(cost(CostPart.Loyalty(n(-10))), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(10))), Players.You))),
    )

    // CR 107.3m: X is the X it was cast with; CR 603.10a: the counters it had as it died
    @Test
    fun marketbackStrider() = card(
        name = "Marketback Strider",
        cost = "{X}{X}",
        type = "Artifact Creature — Construct",
        text = """
            This creature enters with X +1/+1 counters on it.
            {4}: Put a +1/+1 counter on this creature.
            When this creature dies, draw a card for each +1/+1 counter on it.
        """,
        pt = "0/0",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.EntersWithCounters(p1p1, Value.CastX(Objects.Self))))),
        activated(cost(mana("{4}")), counters(Objects.Self)),
        triggered(diesSelf, doing(Effect.Draw(Players.You, Value.Of(NumericProperty.Counters(p1p1), Objects.Self, Combine.Single)))),
    )

    // CR 700.2: a mode chosen earlier this turn can't be chosen again
    @Test
    fun monumentToPersistence() = card(
        name = "Monument to Persistence",
        cost = "{3}",
        type = "Artifact",
        text = """
            Whenever you discard a card, choose one that hasn't been chosen this turn —
            • Draw a card.
            • Create a Treasure token.
            • Each opponent loses 3 life.
        """,
    ) parsesTo abilities(
        Ability.Triggered(on(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You)), Instructions(modes = Modes(listOf(
            mode(draw(1)), mode(createTreasure), mode(loseLife(3, Players.Each(PlayerFilter.Opponent)))), min = 1, max = 1, onlyUnchosenThisTurn = true, chooser = Players.You))),
    )

    // CR 605.1a: it has a target, so it isn't a mana ability; it resolves only at instant speed timing
    @Test
    fun radiantLotusBloom() = card(
        name = "Radiant Lotus Bloom",
        cost = "{6}",
        type = "Artifact",
        text = "{T}, Sacrifice one or more artifacts: Choose a color. Target player adds three mana of the chosen color for each artifact sacrificed this way. (Activate only as an instant.)",
    ) parsesTo abilities(
        activated(cost(tap, CostPart.Sacrifice(CostObjects.Choose(artifact, count = x), preBind = "sacrificed")),
            Step.Choose(Choice.Color(), bind = "color", chooser = Players.You),
            doing(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Times(Value.Count(Objects.Bound("sacrificed")), 3), colors = listOf(ColorRef.Chosen("color")))),
                Players.Target("player"))),
            targets = listOf(targetPlayer), rules = anyTime.copy(minimumX = 1)).let { it.copy(instructions = it.instructions.copy(announced = listOf("X"))) },
    )

    @Test
    fun scrapCrusher() = card(
        name = "Scrap Crusher",
        cost = "{1}",
        type = "Artifact",
        text = """
            {3}, {T}, Sacrifice this artifact: It deals 3 damage to target creature.
            {6}, {T}, Sacrifice this artifact: Destroy target creature or Vehicle.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{3}"), tap, sacrificeSelf), damage(3, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
        activated(cost(mana("{6}"), tap, sacrificeSelf), destroy(target("permanent")), targets = listOf(slot("permanent", creatureOrVehicle))),
    )

    // CR 603.4: checked as it triggers and as it resolves
    @Test
    fun turnstileTortoise() = card(
        name = "Turnstile Tortoise",
        cost = "{2}",
        type = "Artifact Creature — Turtle",
        text = """
            Defender
            When this creature enters, if an opponent controls more lands than you, you create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
        pt = "3/1",
    ) parsesTo abilities(
        Keyword.Defender,
        triggered(entersSelf, createTreasure, interveningIf = Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.Compare(
            count(and(land, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Tested)))), Comparison.Greater, count(and(land, you)))))), atLeast = 1)),
    )
}
