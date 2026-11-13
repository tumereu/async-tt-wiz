package arcanum.parser.sets.one

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Phyrexia: All Will Be One chunk 07: mill and scry (each opponent may scry, CR 701.22c), oil counters, conditions about what was cast or
 * controlled, damage prevention with a reflexive trigger (CR 615.5, 603.12), a dying trigger doubled (CR 603.2d) and a poison replacement
 * (CR 614.1a).
 */
class OneChunk07MillScryConditionsTest {
    private val oil = CounterKind.Named("oil")
    private val oilOnSelf = Value.Of(NumericProperty.Counters(oil), Objects.Self, Combine.Single)

    /** "This permanent enters with [n] oil counter(s) on it." (CR 614.1c) */
    private fun entersWithOil(count: Int) = Ability.Static(listOf(Mechanic.Replacement(
        EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersWithCounters(oil, n(count))))), affects = Affected.Objects(Objects.Self))

    /** "Remove [n] oil counter(s) from this permanent" as a cost (CR 118.3). */
    private fun removeOil(count: Int) = CostPart.RemoveCounters(oil, amount = n(count), from = CostObjects.Self)

    /** "Then each opponent may scry [count]." Each decides in turn order (CR 101.4); those who do scry at once (CR 701.22c). */
    private fun eachOpponentMayScry(count: Int) = Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "player", together = true,
        steps = listOf(Step.May(listOf(doing(Effect.Scry(Players.Bound("player"), n(count)))), player = Players.Bound("player"))))

    private val dies: (ObjectFilter) -> Trigger = { on(EventPattern.ZoneChange(it, from = Zone.Battlefield, to = Zone.Graveyard)) }

    // CR 302.6, 702.10: activated abilities with {T} ignore summoning sickness; CR 115.10a: the returned card is no target, chosen as it resolves
    @Test
    fun vorrikRootboundBrawler() = card(
        name = "Vorrik, Rootbound Brawler",
        cost = "{1}{B}{G}",
        type = "Legendary Planeswalker — Vorrik",
        loyalty = "3",
        text = """
            You may activate abilities of creatures you control as though those creatures had haste.
            [+1]: Untap up to one target creature.
            [−2]: Mill three cards, then you may return a creature card with mana value 2 or less from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        static(Mechanic.AbilitiesIgnoreSummoningSickness, affects = Objects.Each(creatureYouControl)),
        loyalty(1, untapIt(target("creature")), targets = listOf(slot("creature", creature, min = 0))),
        loyalty(-2,
            mill(3),
            Step.Choose(Choice.Objects(inYourGraveyard(creature, manaValue(Comparison.AtMost, 2)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield)))),
    )

    // CR 614.1c; CR 608.2h: X counted as it resolves
    @Test
    fun oilwellFountain() = card(
        name = "Oilwell Fountain",
        cost = "{U}",
        type = "Artifact",
        text = """
            This artifact enters with two oil counters on it.
            {3}, {T}: Target player mills X cards, where X is the number of oil counters on this artifact.
        """,
    ) parsesTo abilities(
        entersWithOil(2),
        activated(cost(mana("{3}"), tap), doing(Effect.Mill(Players.Target("player"), oilOnSelf)), targets = listOf(targetPlayer)),
    )

    // CR 701.22c: each opponent decides in turn order; CR 707.9b: the copy's exceptions (its colour replaced, its types added); CR 608.2h: "wasn't an
    // Aura" as it last existed
    @Test
    fun seraphineVeiledReaper() = card(
        name = "Seraphine, Veiled Reaper",
        cost = "{3}{W}{W}{B}{B}",
        type = "Legendary Planeswalker — Seraphine",
        loyalty = "6",
        text = """
            Hexproof
            [+2]: Each opponent loses 3 life and you gain 3 life.
            [0]: You draw two cards. Then each opponent may scry 1.
            [−3]: Exile target creature or enchantment. If it wasn't an Aura, create a token that's a copy of it, except it's a 1/1 white Spirit creature with flying in addition to its other types.
        """,
    ) parsesTo abilities(
        Keyword.Hexproof,
        loyalty(2, loseLife(3, Players.Each(PlayerFilter.Opponent)), gainLife(3)),
        loyalty(0, draw(2), eachOpponentMayScry(1)),
        loyalty(-3,
            move(target("permanent"), Zone.Exile, postBind = "exiled"),
            Step.If(Condition.ObjectMatches(Objects.Bound("exiled"), not(sub("Aura"))), then = listOf(
                doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("exiled"), listOf(
                    CopyException.PowerToughness(1, 1),
                    CopyException.Colors(listOf(ColorRef.Literal(Color.White))),
                    CopyException.Types(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Spirit")))),
                    CopyException.AddAbilities(listOf(Keyword.Flying)))), n(1), Players.You)))),
            targets = listOf(slot("permanent", or(creature, enchantment)))),
    )

    // CR 701.22c
    @Test
    fun myrArchkeeper() = card(
        name = "Myr Archkeeper",
        cost = "{3}",
        type = "Artifact Creature — Myr",
        pt = "2/3",
        text = "When this creature enters, scry 2. Then each opponent may scry 1. (To scry X, a player looks at the top X cards of their library, then puts any number of them on the bottom and the rest on top in any order.)",
    ) parsesTo abilities(
        triggered(entersSelf, scry(2), eachOpponentMayScry(1)),
    )

    // layer 4 and 7b (CR 613.1d, 613.4b)
    @Test
    fun gazeOfTheFurnace() = card(
        name = "Gaze of the Furnace",
        cost = "{2}{U}",
        type = "Artifact",
        text = """
            When this artifact enters, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)
            Whenever another artifact you control enters, this artifact becomes a 4/4 Phyrexian Eye artifact creature until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, scry(2)),
        triggered(enters(and(another, artifact, you)), untilEndOfTurn(Objects.Self,
            Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Phyrexian")), SubtypeRef.Literal(Subtype("Eye")))),
            Mechanic.SetPowerToughness(n(4), n(4)))),
    )

    // CR 614.1c; CR 118.3: removing a counter is a cost
    @Test
    fun oilglassAugur() = card(
        name = "Oilglass Augur",
        cost = "{U}",
        type = "Creature — Phyrexian Advisor",
        pt = "0/3",
        text = """
            This creature enters with three oil counters on it.
            {T}, Remove an oil counter from this creature: Scry 1. (Look at the top card of your library. You may put that card on the bottom.)
        """,
    ) parsesTo abilities(
        entersWithOil(3),
        activated(cost(tap, removeOil(1)), scry(1)),
    )

    // CR 700.4: dies
    @Test
    fun ichorWellhead() = card(
        name = "Ichor Wellhead",
        cost = "{1}{W}",
        type = "Artifact",
        text = """
            Whenever a creature you control dies, scry 1 and put an oil counter on this artifact.
            {1}, {T}, Remove two oil counters from this artifact: Draw a card.
        """,
    ) parsesTo abilities(
        triggered(dies(creatureYouControl), scry(1), counters(Objects.Self, 1, oil)),
        activated(cost(mana("{1}"), tap, removeOil(2)), draw(1)),
    )

    // CR 601.2i: cast triggers
    @Test
    fun serumMonarch() = card(
        name = "Serum Monarch",
        cost = "{4}{U}",
        type = "Creature — Phyrexian Sphinx",
        pt = "4/4",
        text = """
            Flying
            Whenever you cast a noncreature spell, put an oil counter on this creature.
            {U}, Remove an oil counter from this creature: Draw a card, then scry 2.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCast(noncreatureSpell), counters(Objects.Self, 1, oil)),
        activated(cost(mana("{U}"), removeOil(1)), draw(1), scry(2)),
    )

    // CR 305.1: playing a land from the graveyard; CR 608.2g: cast while the ability resolves; CR 101.2: "can't" for the rest of the turn
    @Test
    fun gatewayOfStrata() = card(
        name = "Gateway of Strata",
        cost = "{2}{G}{G}",
        type = "Artifact",
        text = """
            You may play lands from your graveyard.
            {T}: Choose target nonland permanent card in your graveyard. If you haven't cast a spell this turn, you may cast that card. If you do, you can't cast additional spells this turn. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, holder = PlayerFilter.You)), affects = Affected.Objects(Objects.Each(and(land, inZone(Zone.Graveyard),
            ObjectFilter.OwnedBy(PlayerFilter.You))))),
        activated(cost(tap),
            Step.If(Condition.Not(Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You), atLeast = 1, measure = Measure.Events)), then = listOf(
                Step.Pay(steps = listOf(doing(Effect.Cast(target("card"), PlayKind.Cast, caster = Players.You))), payer = Players.You, bind = "cast"),
                Step.If(Condition.Did("cast"), then = listOf(
                    doing(Effect.ApplyMechanics(listOf(Mechanic.CantCast(ObjectFilter.Any)), Affected.Players(Players.You), Duration.EndOfTurn)))))),
            targets = listOf(slot("card", inYourGraveyard(nonland, permanentCard))),
            rules = asSorcery),
    )

    // CR 608.2h: "you controlled" as it last existed on the battlefield
    @Test
    fun joyousWrecking() = card(
        name = "Joyous Wrecking",
        cost = "{R}",
        type = "Sorcery",
        text = "Destroy target artifact. If you controlled that artifact, create three 1/1 red Phyrexian Goblin creature tokens.",
    ) parsesTo abilities(
        spell(
            destroy(target("artifact")),
            Step.If(Condition.ObjectMatches(target("artifact"), ObjectFilter.ControlledBy(PlayerFilter.You)), then = listOf(
                create(creatureToken("Phyrexian Goblin", listOf(Color.Red), 1, 1, name = "Phyrexian Goblin Token"), n(3)))),
            targets = listOf(slot("artifact", artifact)),
        ),
    )

    // CR 611.3a: statics checked continuously; CR 603.4 no: "Then if" is checked as the ability resolves
    @Test
    fun mutatedQuillback() = card(
        name = "Mutated Quillback",
        cost = "{2}{G}{G}",
        type = "Creature — Phyrexian Beast",
        pt = "5/5",
        text = """
            This creature enters with four oil counters on it.
            This creature has trample as long as it has two or fewer oil counters on it. Otherwise, it has hexproof.
            At the beginning of your upkeep, remove an oil counter from this creature. Then if it has no oil counters on it, sacrifice it.
        """,
    ) parsesTo abilities(
        entersWithOil(4),
        static(has(Keyword.Trample), condition = Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(oil, atLeast = 3)))),
        static(has(Keyword.Hexproof), condition = Condition.Not(Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(oil, atLeast = 3))))),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
            doing(Effect.RemoveCounters(Objects.Self, oil, n(1))),
            Step.If(Condition.ObjectMatches(Objects.Self, not(ObjectFilter.HasCounter(oil, atLeast = 1))), then = listOf(doing(Effect.Sacrifice(Objects.Self))))),
    )

    // CR 615.5: the prevention's additional effect; CR 603.12: a reflexive trigger, its target chosen as it is put on the stack
    @Test
    fun phyrexianRetaliator() = card(
        name = "Phyrexian Retaliator",
        cost = "{W}{W}{W}{W}",
        type = "Creature — Phyrexian Horror",
        pt = "5/5",
        text = """
            Flying
            If damage would be dealt to this creature, prevent that damage. When damage is prevented this way, this creature deals that much damage to any other target.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self))),
            additional = Instructions(steps = listOf(doing(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(TargetSlot("any", TargetKind.Either(and(another, or(creature, planeswalker, ObjectFilter.Type(CardType.Battle))), PlayerFilter.Any),
                    min = n(1), max = n(1))),
                steps = listOf(doing(Effect.DealDamage(Recipients(target = "any"), Value.EventAmount, Objects.Self, bind = "dealt")))))))))),
    )

    // CR 118.12: "If you do"; CR 608.2h: the sacrificed creature's mana value as it last existed; CR 701.20: reveal until
    @Test
    fun morvathCrucibleBrute() = card(
        name = "Morvath, Crucible Brute",
        cost = "{2}{B}{R}",
        type = "Legendary Creature — Phyrexian Beast",
        pt = "4/4",
        text = "At the beginning of your end step, you may sacrifice another creature. If you do, reveal cards from the top of your library until you reveal a nonlegendary creature card with lesser mana value, put it onto the battlefield, then put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(and(another, creature), count = n(1)), postBind = "sacrificed"))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(
                doing(Effect.RevealUntil(and(not(legendary), creature, ObjectFilter.ManaValue(Comparison.Less,
                    Value.Of(NumericProperty.ManaValue, Objects.Bound("sacrificed"), Combine.Single))), Players.You, n(1), reveal = true, foundBind = "found", restBind = "rest")),
                move(Objects.Bound("found"), Zone.Battlefield),
                doing(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))))),
    )

    // CR 603.2d: a creature dying (CR 700.4) causing a trigger makes it trigger an additional time; CR 107.4f: Phyrexian mana
    @Test
    fun skelvarSlaughterLord() = card(
        name = "Skelvar, Slaughter Lord",
        cost = "{3}{B}{B}",
        type = "Legendary Creature — Phyrexian Horror",
        pt = "8/3",
        text = """
            If a creature dying causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.
            {B/P}{B/P}, Exile three creature cards from your graveyard: Put an indestructible counter on Skelvar. ({B/P} can be paid with either {B} or 2 life.)
        """,
    ) parsesTo abilities(
        static(Mechanic.TriggersAdditionalTime(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), and(permanentFilter, you))),
        activated(cost(mana("{B/P}{B/P}"), CostPart.Exile(CostObjects.Choose(creature, count = n(3)), from = Zone.Graveyard)),
            counters(Objects.Self, 1, CounterKind.Keyword(Keyword.Indestructible.kind))),
    )

    // CR 614.1a, 614.5 (not applied to its own counter), 101.2 ("can't" for the turn, even if this leaves); CR 603.7c: a delayed trigger about the
    // target; CR 400.7: "that card" is the card put into the graveyard
    @Test
    fun ysoldeTheMendingHand() = card(
        name = "Ysolde, the Mending Hand",
        cost = "{G}{W}",
        type = "Legendary Creature — Human Scout",
        pt = "3/3",
        text = """
            If you would get one or more poison counters, instead you get one poison counter and you can't get additional poison counters this turn.
            Exile Ysolde: Choose another target creature or artifact. When it's put into a graveyard this turn, return that card to the battlefield under its owner's control.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.PlayerCountersPut(PlayerFilter.You, CounterKind.Poison), instead = Instructions(steps = listOf(
            doing(Effect.GivePlayerCounters(Players.You, CounterKind.Poison, n(1))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.CantGetCounters(CounterKind.Poison)), Affected.Players(Players.You), Duration.EndOfTurn)))))),
        activated(cost(CostPart.Exile(CostObjects.Self, from = Zone.Battlefield)),
            doing(Effect.CreateDelayedTrigger(on(EventPattern.ZoneChange(ObjectFilter.Is(target("permanent")), from = Zone.Battlefield, to = Zone.Graveyard)),
                Instructions(steps = listOf(move(Objects.EventObject(EventRole.After), Zone.Battlefield, controller = Players.OwnerOf(Objects.EventObject(EventRole.After)), postBind = "returned"))),
                duration = Duration.EndOfTurn)),
            targets = listOf(slot("permanent", and(another, or(creature, artifact))))),
    )
}
