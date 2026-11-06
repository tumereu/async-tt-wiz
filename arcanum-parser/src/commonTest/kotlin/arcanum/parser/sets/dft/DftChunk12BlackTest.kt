package arcanum.parser.sets.dft

import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Aetherdrift chunk 12: black cards: returning from the graveyard on landfall (CR 113.6c), granting embalm with the card's own mana cost
 * (CR 702.128a), spells cast by players who don't own them, exiling face down to be played (CR 406.3), a delayed trigger on a creature dying
 * this turn (CR 603.7c), a sacrificed permanent read as it last existed (CR 608.2h), a chosen target compared with the graveyard after a mill,
 * and casting from the graveyard with additional costs (CR 601.2f).
 */
class DftChunk12BlackTest {
    @Test
    fun gravehauntSpecter() = card(
        name = "Gravehaunt Specter",
        cost = "{B}{B}",
        type = "Creature — Vampire Spirit",
        text = """
            This creature can't block.
            This creature has haste as long as an opponent has 10 or less life.
            Landfall — Whenever a land you control enters, you may return this card from your graveyard to the battlefield.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        static(Mechanic.CantBlock(ObjectFilter.Any)),
        static(has(Keyword.Haste), condition = Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Life(Comparison.AtMost, n(10)))), atLeast = 1)),
        triggered(landfall, Step.May(listOf(move(Objects.Self, Zone.Battlefield, postBind = "returned")), player = Players.You), abilityWord = "Landfall").copy(functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 702.128a: embalm with the card's own mana cost, until end of turn
    @Test
    fun shroudclothWrappings() = card(
        name = "Shroudcloth Wrappings",
        cost = "{2}{B}{B}",
        type = "Artifact",
        text = """
            Zombies you control get +1/+1.
            {T}: Target creature card in your graveyard gains embalm until end of turn. The embalm cost is equal to its mana cost. (Exile that card and pay its embalm cost: Create a token that's a copy of it, except it's a white Zombie in addition to its other types and has no mana cost. Embalm only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(and(sub("Zombie"), you))),
        activated(cost(tap), gains(target("card"), Keyword.Embalm(Cost(listOf(CostPart.ItsManaCost)))), targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    @Test
    fun motorRat() = card(
        name = "Motor Rat",
        cost = "{B}",
        type = "Creature — Zombie Rat",
        text = """
            Deathtouch
            {5}{B}: Each opponent loses 2 life.
        """,
        pt = "1/1",
    ) parsesTo abilities(Keyword.Deathtouch, activated(cost(mana("{5}{B}")), loseLife(2, Players.Each(PlayerFilter.Opponent))))

    // CR 406.3: exiled face down, played by the damaging creature's controller
    @Test
    fun gontraNightEnvoy() = card(
        name = "Gontra, Night Envoy",
        cost = "{2}{B}{B}",
        type = "Legendary Creature — Aetherborn Rogue",
        text = """
            Whenever a player casts a spell they don't own, that player creates a Treasure token.
            Whenever a creature deals combat damage to one of your opponents, its controller looks at the top card of that opponent's library and exiles it face down. They may play that card for as long as it remains exiled. Mana of any type can be spent to cast a spell this way.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(not(ObjectFilter.OwnedBy(PlayerFilter.Is(Players.ControllerOf(Objects.Tested)))), PlayerFilter.Any)),
            doing(Effect.CreateToken(treasure, n(1), Players.EventPlayer))),
        run {
            val controller = Players.ControllerOf(Objects.EventObject(EventRole.Source))
            triggered(on(EventPattern.Damage(creature, RecipientFilter(players = PlayerFilter.Opponent), combat = true)),
                doing(Effect.LookAt(Objects.LibraryCards(Players.EventPlayer, n(1), fromTop = true), controller, bind = "looked")),
                doing(Effect.Move(Objects.Bound("looked"), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.Is(controller), manaAsAnyColor = true)),
                    Affected.Objects(Objects.Bound("exiled")), Duration.Indefinitely)))
        },
    )

    @Test
    fun grimTrinket() = card(
        name = "Grim Trinket",
        cost = "{B}",
        type = "Artifact",
        text = """
            When this artifact enters, target creature an opponent controls gets -2/-2 until end of turn.
            {2}{B}, {T}, Sacrifice this artifact: Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, pump(target("creature"), -2, -2), targets = listOf(slot("creature", creatureOpponentControls))),
        activated(cost(mana("{2}{B}"), tap, sacrificeSelf), surveil(2)),
    )

    // CR 603.7c: the delayed trigger is about that creature only, this turn
    @Test
    fun grimSpearthrower() = card(
        name = "Grim Spearthrower",
        cost = "{2}{B}",
        type = "Creature — Human Warrior",
        text = "Whenever you attack, target attacking creature gets +1/+0 until end of turn. When that creature dies this turn, surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(youAttack, pump(target("creature"), 1, 0),
            doing(Effect.CreateDelayedTrigger(dies(ObjectFilter.Is(target("creature"))), Instructions(steps = listOf(surveil(1))), duration = Duration.EndOfTurn)),
            targets = listOf(slot("creature", and(attacking, creature)))),
    )

    // CR 608.2h: the sacrificed permanent as it last existed
    @Test
    fun infernalSideswipe() = card(
        name = "Infernal Sideswipe",
        cost = "{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact or creature.
            Destroy target creature or Vehicle. If the sacrificed permanent was a Vehicle, draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.Sacrifice(CostObjects.Choose(or(artifact, creature), count = n(1)), preBind = "sacrificed")))),
        spell(destroy(target("permanent")), Step.If(Condition.ObjectMatches(Objects.Bound("sacrificed"), vehicle), then = listOf(draw(1))),
            targets = listOf(slot("permanent", creatureOrVehicle))),
    )

    @Test
    fun kalaksionMawTyrant() = card(
        name = "Kalaksion, Maw Tyrant",
        cost = "{1}{B}{B}",
        type = "Legendary Creature — Crocodile",
        pt = "7/2",
    ) parsesTo abilities()

    @Test
    fun maximumThrottle() = card(
        name = "Maximum Throttle",
        cost = "{1}{B}",
        type = "Instant",
        text = "Put a +1/+1 counter on target creature. It gains deathtouch and indestructible until end of turn.",
    ) parsesTo abilities(
        spell(counters(target("creature")), gains(target("creature"), Keyword.Deathtouch, Keyword.Indestructible), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun pactdollHorror() = card(
        name = "Pactdoll Horror",
        cost = "{3}{B}",
        type = "Artifact Creature — Toy",
        text = "Whenever this creature or another artifact you control enters, each opponent loses 1 life and you gain 1 life.",
        pt = "3/4",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(or(isSelf, and(another, artifact, you)), to = Zone.Battlefield)),
            loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
    )

    // CR 608.2b: the chosen permanent is destroyed only if the comparison holds as the spell resolves
    @Test
    fun bogFeast() = card(
        name = "Bog Feast",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Choose target creature, planeswalker, or Vehicle. Mill two cards, then destroy the chosen permanent if its mana value is less than or equal to the number of cards in your graveyard.",
    ) parsesTo abilities(
        spell(mill(2), Step.If(Condition.ObjectMatches(target("permanent"), ObjectFilter.ManaValue(Comparison.AtMost,
            count(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))), then = listOf(destroy(target("permanent")))),
            targets = listOf(slot("permanent", or(creature, planeswalker, vehicle)))),
    )

    @Test
    fun recklessShortcut() = card(
        name = "Reckless Shortcut",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Draw two cards. Each player loses 2 life.",
    ) parsesTo abilities(spell(draw(2), loseLife(2, Players.Each(PlayerFilter.Any))))

    @Test
    fun spinOff() = card(
        name = "Spin Off",
        cost = "{1}{B}{B}",
        type = "Instant",
        text = "Destroy target creature or Vehicle.",
    ) parsesTo abilities(spell(destroy(target("permanent")), targets = listOf(slot("permanent", creatureOrVehicle))))

    @Test
    fun siphonFuel() = card(
        name = "Siphon Fuel",
        cost = "{4}{B}",
        type = "Instant",
        text = "Target creature gets -6/-6 until end of turn. You gain 2 life.",
    ) parsesTo abilities(spell(pump(target("creature"), -6, -6), gainLife(2), targets = listOf(slot("creature", creature))))

    // CR 601.2f: the additional costs of casting it from the graveyard
    @Test
    fun wickerkinIndomitable() = card(
        name = "Wickerkin Indomitable",
        cost = "{3}{B}",
        type = "Artifact Creature — Scarecrow",
        text = "You may cast this card from your graveyard by paying 2 life and sacrificing an artifact or creature in addition to paying its other costs.",
        pt = "4/3",
    ) parsesTo abilities(
        static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, holder = PlayerFilter.You,
            additionalCost = cost(CostPart.PayLife(n(2)), sacrificeA(or(artifact, creature))))),
    )

    @Test
    fun wreckageWickerkin() = card(
        name = "Wreckage Wickerkin",
        cost = "{1}{B}",
        type = "Artifact Creature — Scarecrow",
        text = """
            Flying
            When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
        pt = "1/3",
    ) parsesTo abilities(Keyword.Flying, triggered(entersSelf, surveil(2)))

    @Test
    fun wretchedDolly() = card(
        name = "Wretched Dolly",
        cost = "{1}{B}",
        type = "Artifact Creature — Toy",
        text = "{B}, {T}: Surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)",
        pt = "3/1",
    ) parsesTo abilities(activated(cost(mana("{B}"), tap), surveil(1)))
}
