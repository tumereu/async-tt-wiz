package arcanum.parser.sets.tmt

import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Teenage Mutant Ninja Turtles chunk 12: green cards. A look at the top cards for one of several subtypes or a land, a triggered mana
 * ability on tapping lands (CR 605.1b, 106.12a), a hexproof counter it enters with (CR 122.1b), removing a counter for a reflexive
 * destroy of what that player controls (CR 603.12), mana by power, "can't be blocked by more than one creature" (CR 509.1b), a spell
 * cheaper with a Turtle (CR 601.2f), one creature dealing damage equal to its power to another (CR 120.3), an anthem with flash, and
 * counters for damage dealt to it (CR 120.3) and a payment on combat damage.
 */
class TmtChunk12GreenTest {
    @Test
    fun radicalDiscovery() = card(
        name = "Radical Discovery!",
        cost = "{G}",
        type = "Sorcery",
        text = "Look at the top four cards of your library. You may reveal a Mutant, Ninja, Turtle, or land card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(or(mutant, ninja, turtle, land), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    @Test
    fun toadButler() = card(
        name = "Toad Butler",
        cost = "{1}{G}",
        type = "Creature — Frog Spirit",
        text = """
            Deathtouch
            {T}: Add one mana of any color.
            {2}: This creature gains reach until end of turn.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        activated(manaCost("{2}"), gains(Objects.Self, Keyword.Reach)),
    )

    // CR 605.1b: a triggered mana ability, resolving at once (CR 605.4a)
    @Test
    fun mudskipperAndBurrow() = card(
        name = "Mudskipper & Burrow",
        cost = "{4}{G}{G}",
        type = "Legendary Creature — Ox Mole Mutant",
        text = """
            Trample
            Whenever you tap a land for mana, add {G}.
        """,
        pt = "8/8",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(on(EventPattern.Tap(and(land, you), forMana = true)), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))),
    )

    // CR 122.1b: a hexproof counter; CR 603.12: the reflexive destroy targets what the damaged player controls
    @Test
    fun hidebackSwampStalker() = card(
        name = "Hideback, Swamp Stalker",
        cost = "{2}{G}{G}",
        type = "Legendary Creature — Crocodile Mutant Rogue",
        text = """
            Trample
            Hideback enters with a hexproof counter on her.
            Whenever Hideback deals combat damage to a player, you may remove a counter from her. When you do, destroy target artifact or enchantment that player controls.
        """,
        pt = "5/4",
    ) parsesTo abilities(
        Keyword.Trample,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(CounterKind.Keyword(Keyword.Hexproof), n(1))))),
        triggered(combatDamageToAPlayer,
            Step.Pay(cost(CostPart.RemoveCounters(null, n(1), CostObjects.Self)), payer = Players.You, bind = "removed"),
            Step.If(Condition.Did("removed"), then = listOf(reflexive(destroy(target("permanent")), targets = listOf(slot("permanent",
                and(or(artifact, enchantment), ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.Is(Players.EventPlayer))))))))),
    )

    // CR 106.1b: all the mana of one colour; the power as the ability resolves
    @Test
    fun monaVerdeScienceGeek() = card(
        name = "Mona Verde, Science Geek",
        cost = "{2}{G}",
        type = "Legendary Creature — Lizard Mutant",
        text = """
            Reach
            {T}: Add X mana of any one color, where X is Mona Verde's power.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Reach,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(power(Objects.Self))), Players.You))),
    )

    @Test
    fun ancientPachyderm() = card(
        name = "Ancient Pachyderm",
        cost = "{3}{G}",
        type = "Creature — Elephant Avatar",
        text = """
            Reach, trample
            When this creature enters, you gain 2 life.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        triggered(entersSelf, gainLife(2)),
    )

    @Test
    fun scruffyRaptor() = card(
        name = "Scruffy Raptor",
        cost = "{4}{G}",
        type = "Creature — Dinosaur",
        text = "When this creature enters, return up to one target creature or Food card from your graveyard to your hand.",
        pt = "4/3",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("cards"), Zone.Hand), targets = listOf(slot("cards", inYourGraveyard(or(creature, sub("Food"))), min = 0))),
    )

    // CR 509.1b: at most one blocker
    @Test
    fun hornbackCrashCourser() = card(
        name = "Hornback, Crash Courser",
        cost = "{4}{G}{G}",
        type = "Legendary Creature — Rhino Mutant",
        text = """
            Hornback can't be blocked by more than one creature.
            Boars you control can't be blocked by more than one creature.
            Forestcycling {2} ({2}, Discard this card: Search your library for a Forest card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "7/7",
    ) parsesTo abilities(
        static(Mechanic.MaximumBlockers(1)),
        static(Mechanic.MaximumBlockers(1), affects = Objects.Each(and(sub("Boar"), you))),
        landcycling("Forest"),
    )

    // CR 601.2f: the cost reduction applies while you control a Turtle
    @Test
    fun hiddenInTheShell() = card(
        name = "Hidden in the Shell",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if you control a Turtle.
            Put a +1/+1 counter on target creature you control. It gains trample, hexproof, and indestructible until end of turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, n(1))),
            condition = Condition.Exists(and(turtle, you), atLeast = 1)),
        spell(counters(target("creature")), gains(target("creature"), Keyword.Trample, Keyword.Hexproof, Keyword.Indestructible),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 120.3: damage equal to its power, dealt by your creature
    @Test
    fun pound() = card(
        name = "Pound",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target creature you control deals damage equal to its power to target creature an opponent controls.",
    ) parsesTo abilities(
        spell(damage(power(target("creature")), toObjects(target("creature an opponent controls")), source = target("creature")),
            targets = listOf(slot("creature", creatureYouControl), slot("creature an opponent controls", creatureOpponentControls))),
    )

    @Test
    fun interdimensionalBovine() = card(
        name = "Interdimensional Bovine",
        cost = "{2}{G}",
        type = "Creature — Ox Avatar",
        text = """
            Flying
            {T}: Add two mana of any one color.
        """,
        pt = "0/4",
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(anyOneColor(2)), Players.You))),
    )

    @Test
    fun shellPower() = card(
        name = "Shell Power!",
        cost = "{2}{G}",
        type = "Enchantment",
        text = """
            Flash
            Turtles you control get +2/+2.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        static(pt(2, 2), affects = Objects.Each(and(turtle, you))),
    )

    // CR 120.3: as many counters as damage dealt; CR 118.12: "you may pay {U}. If you do"
    @Test
    fun froggaTornBetweenWorlds() = card(
        name = "Frogga, Torn Between Worlds",
        cost = "{4}{G}",
        type = "Legendary Creature — Mutant Frog Turtle",
        text = """
            Whenever Frogga is dealt damage, put that many +1/+1 counters on her. (She must survive the damage to get the counters.)
            Whenever a creature you control with a counter on it deals combat damage to a player, you may pay {U}. If you do, draw a card.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        triggered(on(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)))),
            counters(Objects.EventObject(arcanum.api.EventRole.Object), Value.EventAmount)),
        triggered(on(EventPattern.Damage(and(creature, you, ObjectFilter.HasCounter(null, atLeast = 1)), RecipientFilter(players = arcanum.api.PlayerFilter.Any), combat = true)),
            Step.Pay(manaCost("{U}"), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(draw(1)))),
    )
}
