package arcanum.parser.sets.tmt

import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Doubled
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Teenage Mutant Ninja Turtles chunk 14: colourless artifacts and lands. A token copy that gains haste and is sacrificed at the next end
 * step (CR 603.7), exile until it leaves, a lord granting keywords to other artifact creatures, "can't attack or block unless its power is
 * 6 or greater", Vehicles (CR 301.7) with a counter put on a creature that crewed it this turn (CR 702.122c) and doubled (CR 701.10e),
 * charge counters as costs, a tapped dual land with life, a land sacrificed for a basic land or evasion, cards exiled with a land returned
 * (CR 607.2a), and mana spendable only on Ninja or Turtle spells (CR 106.6).
 */
class TmtChunk14ArtifactsAndLandsTest {
    private val charge = CounterKind.Named("charge")

    // CR 603.7: the copy is sacrificed at the beginning of the next end step
    @Test
    fun gleamingDomeSentinel() = card(
        name = "Gleaming Dome Sentinel",
        cost = "{2}",
        type = "Artifact Creature — Robot Ninja",
        text = """
            Other artifact creatures you control get +1/+0.
            {5}: Create a token that's a copy of another target artifact you control. That token gains haste. Sacrifice it at the beginning of the next end step.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        static(pt(1, 0), affects = Objects.Each(and(another, artifact, creature, you))),
        activated(manaCost("{5}"),
            doing(Effect.CreateToken(TokenSpec.CopyOf(target("artifact")), n(1), Players.You, bind = "token")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), arcanum.api.Affected.Objects(Objects.Bound("token")), Duration.Indefinitely)),
            delayed(nextEndStep, doing(Effect.Sacrifice(Objects.Bound("token")))),
            targets = listOf(slot("artifact", and(another, artifact, you)))),
    )

    @Test
    fun henchbotSquad() = card(
        name = "Henchbot Squad",
        cost = "{4}",
        type = "Artifact Creature — Robot",
        text = "When this creature enters, exile target tapped creature an opponent controls until this creature leaves the battlefield.",
        pt = "2/3",
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Move(target("creature"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("creature", and(tapped, creature, opponents)))),
    )

    @Test
    fun overlordBrainWarlord() = card(
        name = "Overlord Brain, Warlord",
        cost = "{9}",
        type = "Legendary Artifact Creature — Utrom Robot",
        text = """
            Flying, trample, indestructible, haste
            Other artifact creatures you control have flying, trample, indestructible, and haste.
        """,
        pt = "9/9",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        Keyword.Indestructible,
        Keyword.Haste,
        static(has(Keyword.Flying, Keyword.Trample, Keyword.Indestructible, Keyword.Haste), affects = Objects.Each(and(another, artifact, creature, you))),
    )

    // CR 508.1c, 509.1b: checked as attackers and blockers are declared
    @Test
    fun rollingFortress() = card(
        name = "Rolling Fortress",
        cost = "{2}",
        type = "Artifact Creature — Construct",
        text = """
            Reach, trample
            This creature can't attack or block unless its power is 6 or greater.
            {T}, Sacrifice another artifact: Draw a card. Put a +1/+1 counter on this creature.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), condition = Condition.Not(Condition.ObjectMatches(Objects.Self, power(Comparison.AtLeast, 6)))),
        activated(cost(tap, sacrificeA(and(another, artifact))), draw(1), counters(Objects.Self)),
    )

    // CR 702.122a: crew
    @Test
    fun shellBlimp() = card(
        name = "Shell Blimp",
        cost = "{5}",
        type = "Artifact — Vehicle",
        text = """
            Flying
            When this Vehicle enters, create a 2/2 red Mutant creature token.
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, create(mutantToken)),
        Keyword.Crew(2),
    )

    // CR 702.122c: a creature that crewed it; CR 701.10e: doubling counters
    @Test
    fun shellVan() = card(
        name = "Shell Van",
        cost = "{3}",
        type = "Artifact — Vehicle",
        text = """
            Whenever this Vehicle attacks, put a +1/+1 counter on target creature that crewed it this turn. Then if that creature is a Mutant, Ninja, or Turtle, double the number of +1/+1 counters on it.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(attacksSelf,
            counters(target("creature")),
            Step.If(Condition.ObjectMatches(target("creature"), or(mutant, ninja, turtle)),
                then = listOf(doing(Effect.Action(KeywordAction.Double(Doubled.Counters(target("creature"), p1p1)))))),
            targets = listOf(slot("creature", and(creature, ObjectFilter.CrewedThisTurn(Objects.Self))))),
        Keyword.Crew(1),
    )

    // CR 122.1: charge counters removed as a cost
    @Test
    fun stormMachine() = card(
        name = "Storm Machine",
        cost = "{3}",
        type = "Artifact",
        text = """
            Landfall — Whenever a land you control enters, put a charge counter on this artifact.
            {T}: Add one mana of any color.
            {T}, Remove two charge counters from this artifact: Add {C}{C}.
            {T}, Remove three charge counters from this artifact: It deals 3 damage to any target.
        """,
    ) parsesTo abilities(
        triggered(landfall, counters(Objects.Self, 1, charge), abilityWord = "Landfall"),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        activated(cost(tap, CostPart.RemoveCounters(charge, n(2), arcanum.api.CostObjects.Self)),
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}{C}")!!)), Players.You))),
        activated(cost(tap, CostPart.RemoveCounters(charge, n(3), arcanum.api.CostObjects.Self)), damage(3, toAny), targets = listOf(anyTarget)),
    )

    @Test
    fun portalDimension() = card(
        name = "Portal Dimension",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, you gain 1 life.
            {T}: Add {R} or {W}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, gainLife(1)),
        addEither("R", "W"),
    )

    @Test
    fun drainageTunnel() = card(
        name = "Drainage Tunnel",
        cost = null,
        type = "Land",
        text = """
            {T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
            {T}, Sacrifice this land: Target creature with power 2 or less can't be blocked this turn.
        """,
    ) parsesTo abilities(
        activated(cost(tap, sacrificeSelf), search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
        activated(cost(tap, sacrificeSelf), untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked),
            targets = listOf(slot("creature", and(creature, power(Comparison.AtMost, 2))))),
    )

    // CR 607.2a: the cards exiled with this land
    @Test
    fun farmhouseRetreat() = card(
        name = "Farmhouse Retreat",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {1}, {T}: Exile target creature you own.
            {2}, {T}, Sacrifice this land: Return a creature card exiled with this land to the battlefield under your control. Return each other card exiled with this land to its owner's hand.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(mana("{1}"), tap), move(target("creature"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(slot("creature", and(creature, ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You))))),
        activated(cost(mana("{2}"), tap, sacrificeSelf),
            Step.Choose(Choice.Objects(creature, n(1), n(1), from = Objects.Where(Objects.Remembered("exiled"), inZone(Zone.Exile))), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, controller = Players.You),
            move(Objects.Except(Objects.Where(Objects.Remembered("exiled"), inZone(Zone.Exile)), Objects.Bound("chosen")), Zone.Hand)),
    )

    // CR 106.6: the mana may be spent only on Ninja or Turtle spells
    @Test
    fun shellLair() = card(
        name = "Shell Lair",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a Ninja or Turtle spell.
            {3}, {T}: Target Ninja or Turtle can't be blocked this turn.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)), restriction = and(spellFilter, or(ninja, turtle))), Players.You))),
        activated(cost(mana("{3}"), tap), untilEndOfTurn(target("permanent"), Mechanic.CantBeBlocked), targets = listOf(slot("permanent", or(ninja, turtle)))),
    )
}
