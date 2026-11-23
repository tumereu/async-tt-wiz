package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** SNC chunk 02: casualty N (CR 702.153a) on the spells that carry it. */
class SncChunk02CasualtyTest {
    private val bottomAnyOrder = Destination(Zone.Library, libraryPosition = -1)
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)

    // CR 702.153a, 701.20a (look at, then put one into hand and the other on the bottom)
    @Test
    fun aLittleChat() = card(
        name = "A Brief Natter",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Casualty 1 (As you cast this spell, you may sacrifice a creature with power 1 or greater. When you do, copy this spell.)
            Look at the top two cards of your library. Put one of them into your hand and the other on the bottom of your library.
        """,
    ) parsesTo abilities(
        Keyword.Casualty(1),
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(2), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), bottomAnyOrder)),
        ),
    )

    // CR 702.153a, 701.17? mill half the library rounded down (CR 107.1a)
    @Test
    fun cutYourLosses() = card(
        name = "Trim the Stacks",
        cost = "{4}{U}{U}",
        type = "Sorcery",
        text = """
            Casualty 2 (As you cast this spell, you may sacrifice a creature with power 2 or greater. When you do, copy this spell and you may choose a new target for the copy.)
            Target player mills half their library, rounded down.
        """,
    ) parsesTo abilities(
        Keyword.Casualty(2),
        spell(doing(Effect.Mill(Players.Target("player"), Value.Half(Value.Count(Objects.Each(and(inZone(Zone.Library),
            ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player")))))), roundUp = false))), targets = listOf(targetPlayer)),
    )

    // CR 702.153a, 107.3a (X announced as it is cast)
    @Test
    fun cutOfTheProfits() = card(
        name = "Share of the Take",
        cost = "{X}{B}{B}",
        type = "Sorcery",
        text = """
            Casualty 3 (As you cast this spell, you may sacrifice a creature with power 3 or greater. When you do, copy this spell.)
            You draw X cards and you lose X life.
        """,
    ) parsesTo abilities(
        Keyword.Casualty(3),
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.Draw(Players.You, Value.Announced("X"))),
            Step.Do(Effect.LoseLife(Players.You, Value.Announced("X"))),
        ), announced = listOf("X"))),
    )

    // CR 702.153a, 701.17a (mill), 701.17? an optional return afterwards
    @Test
    fun digUpTheBody() = card(
        name = "Unearth the Corpse",
        cost = "{2}{B}",
        type = "Instant",
        text = """
            Casualty 1 (As you cast this spell, you may sacrifice a creature with power 1 or greater. When you do, copy this spell.)
            Mill two cards, then you may return a creature card from your graveyard to your hand. (To mill a card, put the top card of your library into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Casualty(1),
        spell(mill(2),
            Step.Choose(Choice.Objects(inYourGraveyard(creature), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
    )

    // CR 702.153a, 701.23a (search)
    @Test
    fun illicitShipment() = card(
        name = "Contraband Run",
        cost = "{3}{B}{B}",
        type = "Sorcery",
        text = """
            Casualty 3 (As you cast this spell, you may sacrifice a creature with power 3 or greater. When you do, copy this spell.)
            Search your library for a card, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Casualty(3),
        spell(search(cardFilter), move(Objects.Bound("found"), Zone.Hand), shuffle),
    )

    // CR 702.153a, 111.1 (a token from the effect)
    @Test
    fun joinTheMaestros() = card(
        name = "Rally the Brutes",
        cost = "{4}{B}",
        type = "Sorcery",
        text = """
            Casualty 2 (As you cast this spell, you may sacrifice a creature with power 2 or greater. When you do, copy this spell.)
            Create a 4/3 black Ogre Warrior creature token.
        """,
    ) parsesTo abilities(
        Keyword.Casualty(2),
        spell(create(creatureToken("Ogre Warrior", listOf(Color.Black), 4, 3))),
    )

    // CR 702.153a, 120.3 (damage), a new target for the copy
    @Test
    fun lightEmUp() = card(
        name = "Flare 'Em Out",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Casualty 2 (As you cast this spell, you may sacrifice a creature with power 2 or greater. When you do, copy this spell and you may choose a new target for the copy.)
            Flare 'Em Out deals 2 damage to target creature or planeswalker.
        """,
    ) parsesTo abilities(
        Keyword.Casualty(2),
        spell(damage(2, toObjects(target("permanent"))), targets = listOf(slot("permanent", or(creature, planeswalker)))),
    )

    // CR 702.153a, 701.6a, 118.12 (counter unless its controller pays)
    @Test
    fun makeDisappear() = card(
        name = "Make Vanish",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Casualty 1 (As you cast this spell, you may sacrifice a creature with power 1 or greater. When you do, copy this spell and you may choose a new target for the copy.)
            Counter target spell unless its controller pays {2}.
        """,
    ) parsesTo abilities(
        Keyword.Casualty(1),
        spell(
            Step.Pay(manaCost("{2}"), payer = Players.ControllerOf(target("spell")), bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Counter(target("spell"))))),
            targets = listOf(slot("spell", spellFilter)),
        ),
    )

    // CR 702.153a, 406.1 (exile), 604.2 permission to play them this turn
    @Test
    fun robTheArchives() = card(
        name = "Raid the Shelves",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Casualty 1 (As you cast this spell, you may sacrifice a creature with power 1 or greater. When you do, copy this spell.)
            Exile the top two cards of your library. You may play those cards this turn.
        """,
    ) parsesTo abilities(
        Keyword.Casualty(1),
        spell(
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(2), fromTop = true), to = Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.MayPlay(Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                affects = Affected.Objects(Objects.Bound("exiled")),
                duration = Duration.EndOfTurn,
            )),
        ),
    )

    // CR 702.153a, 701.26a (tap), 502.3 (doesn't untap during its controller's next untap step)
    @Test
    fun rooftopNuisance() = card(
        name = "Gutter Prankster",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            Casualty 1 (As you cast this spell, you may sacrifice a creature with power 1 or greater. When you do, copy this spell and you may choose a new target for the copy.)
            Tap target creature. It doesn't untap during its controller's next untap step.
            Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Casualty(1),
        spell(
            Step.Do(Effect.Tap(Objects.Target("creature"))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.DoesntUntap), Affected.Objects(Objects.Target("creature")),
                Duration.ThroughNextStep(TurnStep.Untap, Players.ControllerOf(Objects.Target("creature"))))),
            draw(1),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 702.153a, 120.2b (noncombat damage), 608.2h (the target's history this turn)
    @Test
    fun grislySigil() = card(
        name = "Gruesome Mark",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Casualty 1 (As you cast this spell, you may sacrifice a creature with power 1 or greater. When you do, copy this spell and you may choose a new target for the copy.)
            Choose target creature or planeswalker. If it was dealt noncombat damage this turn, Gruesome Mark deals 3 damage to it and you gain 3 life. Otherwise, Gruesome Mark deals 1 damage to it and you gain 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Casualty(1),
        spell(
            Step.If(
                Condition.ObjectMatches(Objects.Target("permanent"), ObjectFilter.ThisTurn(
                    EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Any), combat = false), role = EventRole.Object)),
                then = listOf(damage(3, toObjects(target("permanent"))), gainLife(3)),
                otherwise = listOf(damage(1, toObjects(target("permanent"))), gainLife(1)),
            ),
            targets = listOf(slot("permanent", or(creature, planeswalker))),
        ),
    )
}
