package arcanum.parser.sets.hob

import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Hobbit chunk 04: Sagas (CR 714): chapter abilities, one per listed chapter (714.2b, c); cards exiled with the Saga as its
 * card memory (CR 607.2a); a delayed trigger for the rest of the turn (CR 603.7b); effects lasting for as long as the Saga stays
 * (CR 611.2b); a return at the next end step (CR 603.7); sacrificing the Saga on resolution (CR 118.12); an ability gained for
 * good (CR 611.2a).
 */
class HobChunk04SagasTest {
    // CR 714.2b, 701.70a
    @Test
    fun theHillLordComesHome() = card(
        name = "The Hill-Lord Comes Home",
        cost = "{2}{W}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)
            II — Return target creature card with mana value 3 or less from your graveyard to the battlefield.
            III — Put a +1/+1 counter on up to one target creature.
        """,
    ) parsesTo abilities(
        chapter(1, recruit),
        chapter(2, move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 3))))),
        chapter(3, counters(target("creature")), targets = listOf(slot("creature", creature, min = 0))),
    )

    /** The exiled lands are this Saga's memory for its later chapters (CR 607.2a); chapter IV makes a trigger for the rest of the
     * turn (CR 603.7b, 508.3d). */
    // CR 714.2c
    @Test
    fun pathsWindOnAndOn() = card(
        name = "Paths Wind On and On",
        cost = "{1}{W}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I — Search your library for up to two basic Plains cards, exile them, then shuffle. You gain 2 life.
            II, III — Put a card exiled with this Saga into its owner's hand.
            IV — Whenever you attack this turn, target creature you control gets +1/+1 until end of turn for each Plains you control.
        """,
    ) parsesTo abilities(
        chapter(1, search(and(ObjectFilter.Super(Supertype.Basic), sub("Plains")), max = 2),
            move(Objects.Bound("found"), Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
            shuffle, gainLife(2)),
        chapters(listOf(2, 3),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Remembered("exiled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
        chapter(4, Step.Do(Effect.CreateDelayedTrigger(youAttack, Instructions(targets = listOf(slot("creature", creatureYouControl)),
            steps = listOf(untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(count(and(sub("Plains"), you)), count(and(sub("Plains"), you)))))),
            duration = Duration.EndOfTurn, repeats = true))),
    )

    // CR 611.2b, 615: for as long as the Saga remains on the battlefield
    @Test
    fun lazyLobCantCatchMe() = card(
        name = "Lazy Lob Can't Catch Me",
        cost = "{2}{U}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I — Target creature you control gains hexproof for as long as this Saga remains on the battlefield.
            II — Prevent all damage that would be dealt by up to one target creature for as long as this Saga remains on the battlefield.
            III, IV — Draw a card.
        """,
    ) parsesTo abilities(
        chapter(1, Step.Do(Effect.ApplyMechanics(listOf(has(Keyword.Hexproof)), Affected.Objects(target("creature")), Duration.UntilLeaves(Objects.Self))),
            targets = listOf(slot("creature", creatureYouControl))),
        chapter(2, Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Is(target("creature")), RecipientFilter()))), null,
            Duration.UntilLeaves(Objects.Self))), targets = listOf(slot("creature", creature, min = 0))),
        chapters(listOf(3, 4), draw(1)),
    )

    // CR 603.7, 400.7: "if you do" (a permanent was exiled); it returns as a new object
    @Test
    fun bumpBumpBumpBump() = card(
        name = "Bump-Bump-Bump-Bump",
        cost = "{2}{U}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I, II, III, IV — Exile up to one target creature or land you control. If you do, return it to the battlefield under its owner's control at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2, 3, 4),
            move(target("permanent"), Zone.Exile, postBind = "exiled"),
            Step.If(Condition.NonEmpty("exiled"), then = listOf(Step.Do(Effect.CreateDelayedTrigger(stepBegins(arcanum.api.TurnStep.End, PlayerFilter.Any),
                Instructions(steps = listOf(move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))))))),
            targets = listOf(slot("permanent", and(or(creature, land), you), min = 0))),
    )

    // CR 701.9b: you choose, that player discards
    @Test
    fun deepDeepToTheGoblinHalls() = card(
        name = "Deep, Deep to the Goblin Halls",
        cost = "{2}{B}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I — Target opponent reveals their hand. You choose a nonland card from it. That player discards that card.
            II — Amass Goblins 1. (Put a +1/+1 counter on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
            III, IV — Target opponent loses 1 life and you gain 1 life.
        """,
    ) parsesTo abilities(
        chapter(1,
            Step.Do(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            targets = listOf(targetOpponent)),
        chapter(2, amassGoblins(1)),
        chapters(listOf(3, 4), loseLife(1, Players.Target("opponent")), gainLife(1), targets = listOf(targetOpponent)),
    )

    // CR 714.2b: the Saga is the source
    @Test
    fun blazeBlazePineAndMoss() = card(
        name = "Blaze, Blaze, Pine and Moss",
        cost = "{3}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I — This Saga deals 6 damage to target creature an opponent controls.
            II — Destroy target artifact an opponent controls.
            III, IV — Add {R}.
        """,
    ) parsesTo abilities(
        chapter(1, damage(6, toObjects(target("creature"))), targets = listOf(slot("creature", creatureOpponentControls))),
        chapter(2, destroy(target("artifact")), targets = listOf(slot("artifact", and(artifact, opponents)))),
        chapters(listOf(3, 4), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), Players.You))),
    )

    // CR 118.12: the Saga is sacrificed on resolution; "if you do" reads that
    @Test
    fun theFrozenPeaksFar() = card(
        name = "The Frozen Peaks Far",
        cost = "{2}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I, II, III, IV — Create a Treasure token. Then if you control four or more Treasures, sacrifice this Saga. If you do, create a 6/6 red Dragon creature token with flying. (A Treasure token is an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2, 3, 4),
            createTreasure(),
            Step.If(Condition.Exists(and(sub("Treasure"), you), atLeast = 4), then = listOf(
                Step.Pay(cost(sacrificeSelf), payer = Players.You, mandatory = true, bind = "sacrificed"),
                Step.If(Condition.Did("sacrificed"), then = listOf(create(creatureToken("Dragon", listOf(Color.Red), 6, 6, abilities = listOf(Keyword.Flying)))))))),
    )

    // CR 611.2a: the gained landfall ability lasts while the Saga stays
    @Test
    fun deepInTheDell() = card(
        name = "Deep in the Dell",
        cost = "{2}{G}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I — Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            II — This Saga gains "Landfall — Whenever a land you control enters, create a 1/1 green Elf creature token."
            III, IV — Elves you control get +1/+0 and gain vigilance until end of turn.
        """,
    ) parsesTo abilities(
        chapter(1, search(and(ObjectFilter.Super(Supertype.Basic), land), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        chapter(2, Step.Do(Effect.ApplyMechanics(listOf(has(triggered(landfall, create(elf), abilityWord = "Landfall"))), Affected.Objects(Objects.Self),
            Duration.Indefinitely))),
        chapters(listOf(3, 4), pump(Objects.Each(and(sub("Elf"), you)), 1, 0, has(Keyword.Vigilance))),
    )
}
