package arcanum.parser.sets.mh2

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
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
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 chunk 07: red cards, first part: modular with riot (CR 702.43, 702.136), "a spell other than your first spell each
 * turn", a spell that has cascade while delirium holds (CR 702.85), an activated ability with modes (CR 700.2), revealing until a nonland
 * card with a reflexive trigger (CR 603.12), the third spell each turn, gaining control of a spell and reselecting its targets at random
 * (CR 115.7), a split card, multikicker counters (CR 702.33g), damage divided among any number of targets (CR 601.2d), playing a card only
 * during your next turn, and suspend with haste (CR 702.62a).
 */
class Mh2Chunk07RedTest {
    // CR 702.136a riot
    @Test
    fun arcwiredSlasher() = card(
        name = "Arcwired Slasher",
        cost = "{4}{R}",
        type = "Artifact Creature — Cat",
        text = """
            Modular 4 (This creature enters with four +1/+1 counters on it. When it dies, you may put its +1/+1 counters on target artifact creature.)
            Riot (This creature enters with your choice of an additional +1/+1 counter or haste.)
        """,
        pt = "0/0",
    ) parsesTo abilities(Keyword.Modular(4), Keyword.Riot)

    // CR 601.2: each spell after the first cast that turn
    @Test
    fun arcwiredTracker() = card(
        name = "Arcwired Tracker",
        cost = "{2}{R}",
        type = "Artifact Creature — Dog",
        text = """
            Menace
            Modular 2 (This creature enters with two +1/+1 counters on it. When it dies, you may put its +1/+1 counters on target artifact creature.)
            Whenever you cast a spell other than your first spell each turn, put a +1/+1 counter on this creature.
        """,
        pt = "0/0",
    ) parsesTo abilities(
        Keyword.Menace,
        Keyword.Modular(2),
        triggered(youCast(not(ObjectFilter.NthSpellThisTurn(ObjectFilter.Any, 1))), counters(Objects.Self)),
    )

    @Test
    fun arcwiredWhelp() = card(
        name = "Arcwired Whelp",
        cost = "{3}{R}",
        type = "Artifact Creature — Dragon",
        text = """
            Flying
            {R}: This creature gets +1/+0 until end of turn.
            Modular 2 (This creature enters with two +1/+1 counters on it. When it dies, you may put its +1/+1 counters on target artifact creature.)
        """,
        pt = "0/0",
    ) parsesTo abilities(Keyword.Flying, activated(cost(mana("{R}")), pump(Objects.Self, 1, 0)), Keyword.Modular(2))

    @Test
    fun battleScheme() = card(
        name = "Battle Scheme",
        cost = "{3}{R}",
        type = "Enchantment",
        text = """
            At the beginning of combat on your turn, target creature you control gets +2/+0 until end of turn.
            Basic landcycling {1}{R} ({1}{R}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, pump(target("creature"), 2, 0), targets = listOf(slot("creature", creatureYouControl))),
        basicLandcycling("{1}{R}"),
    )

    @Test
    fun flaringRootwalla() = card(
        name = "Flaring Rootwalla",
        cost = "{R}",
        type = "Creature — Lizard",
        text = """
            {R}: This creature gets +2/+0 until end of turn. Activate only once each turn.
            Madness {0} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        activated(cost(mana("{R}")), pump(Objects.Self, 2, 0), rules = anyTime.copy(perTurn = 1)),
        Keyword.Madness(manaCost("{0}")),
    )

    // CR 702.85a: cascade triggers as the spell is cast; the spell's own rule works on the Stack (CR 113.6a)
    @Test
    fun bloodbraidRaider() = card(
        name = "Bloodbraid Raider",
        cost = "{1}{R}",
        type = "Creature — Human Berserker",
        text = """
            This creature can't block.
            Delirium — This spell has cascade as long as there are four or more card types among cards in your graveyard. (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)
        """,
        pt = "3/1",
    ) parsesTo abilities(
        static(Mechanic.CantBlock(ObjectFilter.Any)),
        static(has(Keyword.Cascade), condition = delirium).copy(abilityWord = "Delirium", functionsIn = listOf(Zone.Stack)),
    )

    // CR 700.2: an activated ability with modes
    @Test
    fun breyasPupil() = card(
        name = "Breya's Pupil",
        cost = "{2}{R}",
        type = "Artifact Creature — Human Artificer",
        text = """
            When this creature enters, create a 1/1 colorless Thopter artifact creature token with flying.
            {T}, Sacrifice an artifact: Choose one —
            • Exile the top card of your library. Until the end of your next turn, you may play that card.
            • Target creature gets +2/+0 until end of turn.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        triggered(entersSelf, create(thopter)),
        Ability.Activated(cost(tap, sacrificeA(artifact)), Instructions(modes = modes(
            mode(
                doing(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                    Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn)),
            ),
            mode(pump(target("creature"), 2, 0), targets = listOf(slot("creature", creature))),
        )), anyTime),
    )

    // CR 603.12: the reflexive trigger only if a nonland card was revealed
    @Test
    fun measuredBlast() = card(
        name = "Measured Blast",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Reveal cards from the top of your library until you reveal a nonland card. Put the revealed cards on the bottom of your library in a random order. When you reveal a nonland card this way, Measured Blast deals damage equal to that card's mana value to any target.
            Flashback {3}{R}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.RevealUntil(nonland, Players.You, n(1), reveal = true, foundBind = "found", restBind = "rest")),
            doing(Effect.Move(Objects.Union(listOf(Objects.Bound("found"), Objects.Bound("rest"))), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            Step.If(Condition.NonEmpty("found"), then = listOf(reflexive(
                damage(Value.Of(NumericProperty.ManaValue, Objects.Bound("found"), Combine.Single), toAny), targets = listOf(anyTarget)))),
        ),
        Keyword.Flashback(manaCost("{3}{R}{R}")),
    )

    // CR 601.2: the third spell that player cast this turn
    @Test
    fun captainRillaVane() = card(
        name = "Captain Rilla Vane",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Pirate",
        text = "Whenever you cast your third spell each turn, put a +1/+1 counter on Captain Rilla Vane, then it deals damage equal to its power to any target.",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)), nthThisTurn = 3),
            counters(Objects.Self), damage(powerOf(Objects.Self), toAny), targets = listOf(anyTarget)),
    )

    // CR 115.7: the spell and its copy get new targets at random, none of them you or a permanent you control
    @Test
    fun cooksKiss() = card(
        name = "Cook's Kiss",
        cost = "{1}{R}{R}",
        type = "Instant",
        text = "Gain control of target spell that targets only a single permanent or player. Copy it, then reselect the targets at random for the spell and the copy. The new targets can't be you or a permanent you control.",
    ) parsesTo abilities(
        spell(
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("spell")), Duration.Indefinitely)),
            doing(Effect.CopySpell(target("spell"), Players.You, newTargets = false, bind = "copy")),
            doing(Effect.ChangeTargets(Objects.Union(listOf(target("spell"), Objects.Bound("copy"))), Players.You, optional = false, atRandom = true,
                notTo = RecipientFilter(objects = and(permanentFilter, you), players = PlayerFilter.You))),
            targets = listOf(slot("spell", and(spellFilter, ObjectFilter.SingleTarget(permanentFilter, players = PlayerFilter.Any))))),
    )

    @Test
    fun dragonsWrathConduit() = card(
        name = "Dragon's Wrath Conduit",
        cost = "{R}",
        type = "Creature — Human Shaman",
        text = """
            Whenever you cast a noncreature spell, surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)
            Delirium — As long as there are four or more card types among cards in your graveyard, this creature gets +2/+2, has flying, and attacks each combat if able.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(noncreature, PlayerFilter.You)), surveil(1)),
        static(pt(2, 2), has(Keyword.Flying), Mechanic.MustAttack(), condition = delirium).copy(abilityWord = "Delirium"),
    )

    // CR 702.88a rebound
    @Test
    fun godlessScavenging() = card(
        name = "Godless Scavenging",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Discard a card, then draw a card.
            Rebound (If you cast this spell from your hand, exile it as it resolves. At the beginning of your next upkeep, you may cast this card from exile without paying its mana cost.)
        """,
    ) parsesTo abilities(spell(discard(1), draw(1)), Keyword.Rebound)

    @Test
    fun swiftFeral() = cardFaces(
        Layout.Split,
        card(name = "Swift", cost = "{2}{R}", type = "Instant", text = "Discard a card, then draw two cards.") to abilities(spell(discard(1), draw(2))),
        card(name = "Feral", cost = "{3}{R}{R}", type = "Sorcery", text = "Feral deals 3 damage to each creature without flying.") to abilities(
            spell(damage(3, toObjects(Objects.Each(and(creature, not(kw(Keyword.Flying)))))))),
    )

    @Test
    fun fireRush() = card(
        name = "Fire Rush",
        cost = "{R}",
        type = "Enchantment",
        text = """
            At the beginning of your end step, this enchantment deals 5 damage to each planeswalker.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(triggered(yourEndStep, damage(5, toObjects(Objects.Each(planeswalker)))), Keyword.Cycling(manaCost("{2}")))

    // CR 702.33g: the number of times it was kicked; CR 608.2h: its power as the trigger resolves
    @Test
    fun flametongueCub() = card(
        name = "Flametongue Cub",
        cost = "{R}{R}",
        type = "Creature — Kavu",
        text = """
            Multikicker {2} (You may pay an additional {2} any number of times as you cast this spell.)
            This creature enters with a +1/+1 counter on it for each time it was kicked.
            When this creature enters, it deals damage equal to its power to target creature.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Multikicker(manaCost("{2}")),
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(
            Modification.EntersWithCounters(p1p1, Value.TimesPaid(Keyword.Kicker.Kind, Objects.Self))))),
        triggered(entersSelf, damage(powerOf(Objects.Self), toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    // CR 601.2d: 4 damage divided, at least 1 to each target
    @Test
    fun choler() = card(
        name = "Choler",
        cost = "{3}{R}{R}",
        type = "Creature — Elemental Incarnation",
        text = """
            Double strike
            When this creature enters, it deals 4 damage divided as you choose among any number of target creatures and/or planeswalkers.
            Evoke—Exile a red card from your hand.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        triggered(entersSelf, damage(Value.DividedShare, toObjects(target("planeswalkers"))),
            targets = listOf(TargetSlot("planeswalkers", TargetKind.Objects(or(creature, planeswalker)), min = n(1), max = null, divided = n(4)))),
        Keyword.Evoke(Cost(listOf(CostPart.Exile(CostObjects.Choose(ObjectFilter.OfColor(arcanum.api.Color.Red), count = n(1)), from = Zone.Hand)))),
    )

    // CR 702.40a storm; the permission starts with your next turn (a delayed trigger at its first upkeep, CR 603.7)
    @Test
    fun galvanicHandoff() = card(
        name = "Galvanic Handoff",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            Exile the top card of your library. During your next turn, you may play that card.
            Storm (When you cast this spell, copy it for each spell cast before it this turn.)
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.Upkeep, PlayerFilter.You), Instructions(steps = listOf(
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                    Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn)))), laterTurn = true)),
        ),
        Keyword.Storm,
    )

    // CR 702.62a: a suspended creature cast this way has haste
    @Test
    fun greaterBehemoth() = card(
        name = "Greater Behemoth",
        cost = "{5}{R}{R}",
        type = "Creature — Beast",
        text = """
            Trample
            Suspend 4—{1}{R} (Rather than cast this card from your hand, you may pay {1}{R} and exile it with four time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost. It has haste.)
        """,
        pt = "7/5",
    ) parsesTo abilities(Keyword.Trample, Keyword.Suspend(4, manaCost("{1}{R}")))
}
